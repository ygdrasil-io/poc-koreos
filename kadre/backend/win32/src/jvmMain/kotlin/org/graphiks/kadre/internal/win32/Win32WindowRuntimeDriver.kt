package org.graphiks.kadre.internal.win32

import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.internal.runtime.*
import org.graphiks.kadre.interaction.InteractionAction
import org.graphiks.kadre.interaction.InteractionKind
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.surface.*
import org.graphiks.kadre.window.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private fun win32HandlerInteractions(dropTargetsAvailable: Boolean): Set<InteractionKind> = buildSet {
    add(InteractionKind.OpenWindow)
    add(InteractionKind.BeginWindowMove)
    add(InteractionKind.BeginWindowResize)
    if (dropTargetsAvailable) add(InteractionKind.AcceptDrop)
}

private fun win32SurfaceCapabilities(dropTargetsAvailable: Boolean): SurfaceCapabilities {
    val unsupportedUpdate = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface))
    return SurfaceCapabilities(
        cursor = Capability.Supported(WIN32_CURSOR_RESOURCES.keys, FeatureAvailability.Available),
        customCursor = Capability.Supported(ImageConstraints(PhysicalSize(128, 128), setOf(ImageFormat.Rgba8)), FeatureAvailability.Available),
        pointerCapture = Capability.Supported(setOf(PointerCaptureMode.None, PointerCaptureMode.Confined), FeatureAvailability.Available),
        hitTesting = Capability.Supported(setOf(HitTestingMode.Enabled), FeatureAvailability.Available),
        inputDefaultBehavior = Capability.Supported(InputDefaultBehavior.entries.toSet(), FeatureAvailability.Available),
        handlerInteractions = Capability.Supported(win32HandlerInteractions(dropTargetsAvailable), FeatureAvailability.Available),
        armedInteractions = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.ArmInteraction)),
        platformAccess = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.PlatformSurfaceAccess)),
    )
}

internal const val WM_CLOSE: Int = 0x0010
internal const val WM_DESTROY: Int = 0x0002
internal const val WM_SIZE: Int = 0x0005
internal const val WM_PAINT: Int = 0x000F
internal const val WM_SHOWWINDOW: Int = 0x0018
internal const val WM_ACTIVATE: Int = 0x0006
internal const val WM_DPICHANGED: Int = 0x02E0
internal const val WM_MOVE: Int = 0x0003
internal const val WM_GETMINMAXINFO: Int = 0x0024

/** One UI owner for native peers, input normalization and synchronous interactions. */
internal class Win32WindowRuntimeDriver internal constructor(
    private val resources: ResourceBudgetPolicy,
    private val port: Win32NativeHostPort,
    private val failureReporter: RuntimeFailureReporter,
    onLastWindowClosed: (() -> Unit)?,
    enabledWindowUpdateCapabilities: Set<WindowProperty>,
) : AutoCloseable, WindowCommandPort, SurfaceCommandPort {
    private val closed = AtomicBoolean(false)
    private val handlerInteractions = win32HandlerInteractions(port.dropTargetsAvailable)
    private val entries = ConcurrentHashMap<WindowRequestId, Win32WindowEntry>()
    private val queuedOpens = ConcurrentHashMap<WindowRequestId, WindowOpenCommand>()
    private val queuedUpdates = ConcurrentHashMap<WindowOperationId, WindowUpdateCommand>()
    private val pendingRedraws = mutableMapOf<SurfaceId, java.util.ArrayDeque<SurfaceRedrawGeneration>>()
    private val attention = object : WindowAttentionPort {
        override suspend fun request(windowId: WindowId, attention: WindowAttention): KadreResult<Unit> = port.onMainThread {
            val entry = entries.values.firstOrNull { it.command.windowId == windowId && isLive(it) }
                ?: return@onMainThread KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
            try { port.requestAttention(entry.window.hwnd, attention); KadreResult.Success(Unit) }
            catch (_: Throwable) { KadreResult.Failure(platformFailure("attention", "native-request-failed")) }
        }
        override fun release(windowId: WindowId) {
            // Native peer teardown stops flashing before the HWND is destroyed.
        }
        override fun close() = Unit
    }

    internal val manager: RuntimeWindowManager = RuntimeWindowManager(
        resources = resources,
        commandPort = this,
        surfaceCommandPort = this,
        platform = KadrePlatform.Win32,
        failureReporter = failureReporter,
        publicWindowCapabilities = true,
        enabledWindowUpdateCapabilities = enabledWindowUpdateCapabilities,
        acceptedWindowLevels = setOf(WindowLevel.Normal, WindowLevel.Floating),
        windowIconConstraints = ImageConstraints(PhysicalSize(256, 256), setOf(ImageFormat.Rgba8)),
        attentionPort = attention,
        acceptedAttention = WindowAttention.entries.toSet(),
        publicSurfaceCapabilities = true,
        enabledSurfaceCapabilities = win32SurfaceCapabilities(port.dropTargetsAvailable),
        textInputPortFactory = TextInputPortFactory { surfaceId ->
            val entry = entries.values.firstOrNull { it.command.surfaceId == surfaceId }
            if (entry == null) Win32UnavailableTextInputPort(KadreFailure.Closed(KadreResourceKind.Surface))
            else try { port.createTextInputPort(entry.window.hwnd) }
            catch (cause: Throwable) { Win32UnavailableTextInputPort(win32TextFailure("initialization", cause)) }
        },
        onLastWindowClosed = onLastWindowClosed,
    )

    override fun requestOpen(command: WindowOpenCommand) {
        if (!port.isMainThread()) {
            // Runtime calls this SPI while admitting the request under its manager lock.
            // Never wait here for a UI commit that needs that same lock.
            queuedOpens[command.requestId] = command
            val reject: (Throwable) -> Unit = {
                if (queuedOpens.remove(command.requestId) != null) command.fail(platformFailure("open-window", "ui-command-failed"))
            }
            try { port.postOnMainThread({ if (queuedOpens.remove(command.requestId) != null) requestOpen(command) }, reject) }
            catch (failure: Throwable) { reject(failure) }
            return
        }
        if (closed.get()) { command.fail(KadreFailure.Closed(KadreResourceKind.Host)); return }
        var router: Win32InputRouter? = null
        var dropRouter: Win32DropRouter? = null
        var window: Win32TopLevelWindowHandle? = null
        try {
            val created = port.createTopLevelWindow(
                title = command.spec.title,
                decorations = Win32WindowDecorations.System,
                width = command.spec.contentSize.width.toInt().coerceAtLeast(1),
                height = command.spec.contentSize.height.toInt().coerceAtLeast(1),
                messageHandler = { hwnd, message, wParam, lParam ->
                    try {
                        router?.handle(message, wParam, lParam)
                            ?: handleMessage(command, hwnd, message, wParam, lParam)
                    } catch (failure: Throwable) {
                        // The FFI callback cannot throw across WndProc. Fail the session explicitly.
                        failureReporter.report(failure)
                        0L
                    }
                },
            )
            window = created
            val entry = Win32WindowEntry(created, command)
            check(entries.putIfAbsent(command.requestId, entry) == null) { "duplicate window request" }
            port.observeConfinementLoss(created.hwnd) { publishCaptureLoss(entry) }
            entry.mutations = Win32WindowMutations(port, created.hwnd) { isLive(entry) }
            entry.mutating = true
            val effectiveSpec = entry.mutations.initialize(command.spec)
            entry.mutating = false
            router = Win32InputRouter(created.hwnd, command.surfaceId, port, manager::acceptSurfaceStimulus,
                { event -> dispatchInteraction(entry, event) },
                { !closed.get() && !entry.closeAdmitted && entries[command.requestId] === entry },
                { revokeInteractionAuthority(entry); releaseConfinement(entry) },
                { revokeInteractionAuthority(entry) },
                Win32ContactRouter(created.hwnd, command.surfaceId, port, resources, manager.nativeInputPayloadBudget(),
                    manager.nativeInputIngressCapacity(), manager::acceptSurfaceStimulus,
                    { event -> dispatchInteraction(entry, event) }, { isLive(entry) }, { cause ->
                        val gestureFailure = cause is Win32GestureFailure
                        val nativeCause = (cause as? Win32GestureFailure)?.nativeCause ?: cause
                        val reason = (nativeCause as? KadreException)?.failure
                        if (!gestureFailure && (reason is KadreFailure.SourceOverflow || reason is KadreFailure.ResourceLimitExceeded))
                            manager.nativeInputFailure(command.surfaceId, checkNotNull(reason))
                        else {
                            val failure = platformFailure("rich-input", when (nativeCause) {
                                is org.graphiks.kffi.win32.managed.Win32PointerException -> "pointer-error-${nativeCause.error}"
                                is org.graphiks.kffi.win32.managed.Win32InteractionException -> "interaction-hresult-${nativeCause.hresult.toUInt().toString(16)}"
                                else -> "native-failed"
                            })
                            manager.acceptSurfaceStimulus(SurfaceStimulus.InputObservationChanged(command.surfaceId, true, true,
                                touch=if (gestureFailure) FeatureAvailability.Available else FeatureAvailability.Unavailable(failure),
                                gestures=Capability.Supported(setOf(org.graphiks.kadre.input.GestureKind.Pan, org.graphiks.kadre.input.GestureKind.Pinch,
                                    org.graphiks.kadre.input.GestureKind.Rotation, org.graphiks.kadre.input.GestureKind.DoubleTap), FeatureAvailability.Unavailable(failure))))
                            failureReporter.report(KadreException(failure))
                        }
                    }, { revokeInteractionAuthority(entry) }))
            entry.drop = Win32DropRouter(command.surfaceId,
                { source, position -> dispatchSynchronousDrop(entry, source, position) },
                manager::acceptSurfaceStimulus,
                { !closed.get() && !entry.closeAdmitted && entries[command.requestId] === entry }).also { dropRouter = it }
            entry.input = router
            val snapshot = initialSnapshot(created.hwnd)
            command.commit(
                owner = Win32PeerOwner(entry),
                effectiveSpec = effectiveSpec,
                initialSurfaceSnapshot = snapshot,
                onSurfaceReady = {
                    // Runtime may finish admission on a worker under its manager lock.
                    // Return before native activation can synchronously call that lock.
                    port.postOnMainThread({
                        if (!entry.closeAdmitted && entries[command.requestId] === entry) {
                            router.activate()
                            entry.dropObserver = try {
                                port.observeDrop(created.hwnd,
                                    Win32DropBudgets(resources.maxCollectionElementsPerValue, resources.maxMetadataCodeUnitsPerValue,
                                        resources.maxRetainedPayloadBytesPerSession.toInt()),
                                    failureReporter::report,
                                    Win32DropCallbacks(
                                        entered = { source, position -> checkNotNull(entry.drop).entered(source, position) },
                                        moved = { position -> entry.drop?.moved(position) },
                                        exited = { entry.drop?.exited() },
                                        performed = { position -> entry.drop?.performed(position) },
                                    ))
                            } catch (failure: Throwable) {
                                failureReporter.report(failure)
                                null
                            }
                            port.showWindow(created.hwnd)
                            observeWindow(entry)
                        }
                    }, failureReporter::report)
                },
            )
        } catch (failure: Throwable) {
            runCatching { dropRouter?.close() }
            router?.close()
            entries.remove(command.requestId)
            window?.let { port.destroyWindow(it.hwnd) }
            command.fail(platformFailure("open-window", "create-failed"))
        }
    }

    private fun handleMessage(command: WindowOpenCommand, hwnd: Long, message: Int, wParam: Long, lParam: Long): Long =
        when (message) {
            WM_CLOSE -> { command.closeRequested(); 0L }
            WM_DESTROY -> { entries[command.requestId]?.let { releaseConfinement(it); it.closeAdmitted = true; it.closeDropFrontier(); it.input?.close() }; command.nativeClosed(); 0L }
            WM_SIZE, WM_MOVE, WM_DPICHANGED -> {
                entries[command.requestId]?.let { if (message == WM_SIZE && wParam == 1L) { revokeInteractionAuthority(it); releaseConfinement(it) } else port.refreshPointerConfinement(hwnd) }
                if (message == WM_SIZE && entries.containsKey(command.requestId)) {
                    val visible = wParam != 1L && port.readWindow(hwnd).style and 0x10000000 != 0
                    manager.acceptSurfaceStimulus(SurfaceStimulus.VisibilityChanged(command.surfaceId,
                        if (visible) SurfaceVisibility.Visible else SurfaceVisibility.Hidden, SurfaceOcclusion.Unknown))
                }
                entries[command.requestId]?.takeIf { !it.mutating }?.let(::observeWindow)
                0L
            }
            WM_GETMINMAXINFO -> {
                val result = port.defWindowProc(hwnd, message, wParam, lParam)
                entries[command.requestId]?.let { if (isLive(it)) it.mutations.trackLimits(lParam) }
                result
            }
            WM_SHOWWINDOW -> {
                if (wParam == 0L) entries[command.requestId]?.let { revokeInteractionAuthority(it); releaseConfinement(it) }
                manager.acceptSurfaceStimulus(SurfaceStimulus.VisibilityChanged(command.surfaceId,
                    if (wParam != 0L) SurfaceVisibility.Visible else SurfaceVisibility.Hidden, SurfaceOcclusion.Unknown))
                port.defWindowProc(hwnd, message, wParam, lParam)
            }
            0x001f -> { entries[command.requestId]?.let { revokeInteractionAuthority(it); releaseConfinement(it) }; port.defWindowProc(hwnd, message, wParam, lParam) }
            0x0211, 0x0231 -> { entries[command.requestId]?.let(::revokeInteractionAuthority); port.defWindowProc(hwnd, message, wParam, lParam) }
            0x0020 -> {
                if (lParam and 0xffffL == 1L && entries[command.requestId]?.let(::isLive) == true) { port.applyClientCursor(hwnd); 1L }
                else port.defWindowProc(hwnd, message, wParam, lParam)
            }
            WM_PAINT -> {
                port.validateRect(hwnd)
                pendingRedraws[command.surfaceId]?.pollFirst()?.let {
                    manager.acceptSurfaceStimulus(SurfaceStimulus.RedrawConsumed(command.surfaceId, it))
                }
                0L
            }
            else -> port.defWindowProc(hwnd, message, wParam, lParam)
        }

    private fun initialSnapshot(hwnd: Long): SurfaceInitialSnapshot {
        val (width, height) = port.readClientPhysicalSize(hwnd)
        val scale = port.readScaleFactor(hwnd)
        return SurfaceInitialSnapshot(
            SurfaceMetrics(LogicalSize(width / scale, height / scale), PhysicalSize(width, height), scale,
                LogicalInsets(0.0, 0.0, 0.0, 0.0)),
            SurfaceFocus.Unfocused, SurfaceVisibility.Hidden, SurfaceOcclusion.Unknown,
            SurfaceAppearance(SurfaceTheme.Light, SurfaceContrast.Normal),
        )
    }

    private fun isLive(entry: Win32WindowEntry): Boolean = !closed.get() && !entry.closeAdmitted && entries[entry.command.requestId] === entry

    private fun currentState(entry: Win32WindowEntry): WindowState? = manager.state.value.windows
        .firstOrNull { it.id == entry.command.windowId }?.state?.value

    private fun publishCaptureLoss(entry: Win32WindowEntry) {
        manager.acceptSurfaceStimulus(SurfaceStimulus.PointerCaptureChanged(entry.command.surfaceId, PointerCaptureMode.None))
    }
    private fun revokeInteractionAuthority(entry: Win32WindowEntry) {
        entry.interactionGeneration++
        manager.invalidateDeferredInteractionInput(entry.command.surfaceId)
    }
    private fun releaseConfinement(entry: Win32WindowEntry) {
        port.setPointerConfinement(entry.window.hwnd, false)
        publishCaptureLoss(entry)
    }

    /** Synchronous drop admission on the UI owner; the runtime closes the source on every refused path. */
    private fun dispatchSynchronousDrop(entry: Win32WindowEntry, source: org.graphiks.kadre.internal.runtime.DropTransferSource,
        position: org.graphiks.kadre.surface.LogicalPoint): org.graphiks.kadre.input.DropOfferId? {
        if (!isLive(entry)) {
            source.close()
            return null
        }
        return manager.dispatchSynchronousDrop(entry.command.surfaceId, source, position) { KadreResult.Success(Unit) }
    }

    private fun observeWindow(entry: Win32WindowEntry) {
        if (!isLive(entry)) return
        val current = currentState(entry) ?: return
        val actual = entry.mutations.readState(current)
        manager.acceptWindowGeometryObservation(entry.command.windowId, actual)
        publishMetrics(entry)
    }

    private fun publishMetrics(entry: Win32WindowEntry) {
        if (!isLive(entry)) return
        val size = port.readClientPhysicalSize(entry.window.hwnd)
        if (size.first <= 0 || size.second <= 0) return
        val scale = port.readScaleFactor(entry.window.hwnd)
        manager.acceptSurfaceStimulus(SurfaceStimulus.MetricsChanged(entry.command.surfaceId,
            SurfaceMetrics(LogicalSize(size.first / scale, size.second / scale), PhysicalSize(size.first, size.second),
                scale, LogicalInsets(0.0, 0.0, 0.0, 0.0))))
    }

    private fun dispatchInteraction(entry: Win32WindowEntry, event: RuntimeSynchronousInteraction) {
        val authority = entry.interactionGeneration
        manager.dispatchSynchronousInteraction(entry.command.surfaceId, event, handlerInteractions) { action ->
            if (action is InteractionAction.OpenWindow) {
                // Native creation commits inline on this UI owner; no borrowed event leaves the callback.
                runBlocking {
                    when (val requested = manager.requestWindow(action.spec)) {
                        is KadreResult.Failure -> requested
                        is KadreResult.Success -> when (val outcome = requested.value.await()) {
                            is WindowRequestOutcome.OpenedHere -> KadreResult.Success(Unit)
                            is WindowRequestOutcome.Rejected -> KadreResult.Failure(outcome.failure)
                            else -> KadreResult.Failure(platformFailure("interaction", "open-not-committed"))
                        }
                    }
                }
            } else if (action == InteractionAction.BeginWindowMove || action is InteractionAction.BeginWindowResize) {
                val press = event as? RuntimeSynchronousInteraction.PointerPressed
                val state = currentState(entry)
                if (press?.button != PointerButton.Primary || authority != entry.interactionGeneration || !isLive(entry) || state == null || state.fullscreen != FullscreenMode.Windowed ||
                    (action is InteractionAction.BeginWindowResize && !state.resizable)) {
                    KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true))
                } else try {
                    val hitTest = if (action is InteractionAction.BeginWindowResize) when (action.edge) {
                        ResizeEdge.West -> 10; ResizeEdge.East -> 11; ResizeEdge.North -> 12; ResizeEdge.NorthWest -> 13
                        ResizeEdge.NorthEast -> 14; ResizeEdge.South -> 15; ResizeEdge.SouthWest -> 16; ResizeEdge.SouthEast -> 17
                    } else 2
                    val physical = press.position.toPhysical(port.readScaleFactor(entry.window.hwnd))
                    releaseConfinement(entry)
                    port.beginMoveResize(entry.window.hwnd, hitTest, physical.x, physical.y)
                    // The press belongs to the completed native drag. Its deferred ordinary
                    // event must not resurrect a held button after the modal loop consumed it.
                    manager.invalidateDeferredInteractionInput(entry.command.surfaceId)
                    if (isLive(entry)) KadreResult.Success(Unit) else KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
                } catch (_: Throwable) { KadreResult.Failure(platformFailure("interaction", "native-drag-failed")) }
            } else KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.Interaction))
        }
    }

    override fun requestOpenedClose(command: OpenedWindowCloseCommand): OpenedWindowCloseOutcome {
        if (!port.isMainThread()) {
            if (!entries.containsKey(command.requestId)) return OpenedWindowCloseOutcome.PlatformFailure(
                platformFailure("close-window", "missing-peer"))
            // Completion is the nativeClosed stimulus. Blocking the application here prevents
            // its cancellation when closing the last window starts the session shutdown.
            port.postOnMainThread({ requestOpenedClose(command) }, failureReporter::report)
            return OpenedWindowCloseOutcome.Accepted
        }
        val entry = entries[command.requestId] ?: return OpenedWindowCloseOutcome.PlatformFailure(
            platformFailure("close-window", "missing-peer"))
        if (entry.closeAdmitted) return OpenedWindowCloseOutcome.NativeCloseAlreadyCommitted
        entry.closeAdmitted = true
        entry.closeDropFrontier()
        entry.input?.close()
        port.destroyWindow(entry.window.hwnd)
        entry.command.nativeClosed()
        return OpenedWindowCloseOutcome.Accepted
    }

    override fun closeRequestRejected(command: OpenedWindowCloseCommand): CloseRequestRejectionOutcome = CloseRequestRejectionOutcome.Rejected

    override fun requestPendingCancellation(command: PendingWindowCancellationCommand): PendingWindowCancellationOutcome {
        if (queuedOpens.remove(command.requestId) != null) return PendingWindowCancellationOutcome.CancelledBeforeCommit
        if (!port.isMainThread()) {
            port.postOnMainThread({
                val entry = entries[command.requestId]
                if (requestPendingCancellation(command) == PendingWindowCancellationOutcome.CancelledBeforeCommit) entry?.command?.nativeClosed()
            }, failureReporter::report)
            return PendingWindowCancellationOutcome.CancellationRequested
        }
        val entry = entries[command.requestId] ?: return PendingWindowCancellationOutcome.TooLate
        if (entry.closeAdmitted) return PendingWindowCancellationOutcome.TooLate
        entries.remove(command.requestId)
        entry.closeDropFrontier()
        entry.input?.close()
        port.destroyWindow(entry.window.hwnd)
        return PendingWindowCancellationOutcome.CancelledBeforeCommit
    }

    override fun requestUpdate(command: WindowUpdateCommand) {
        queuedUpdates[command.operationId] = command
        val reject: (Throwable) -> Unit = { failure ->
            if (queuedUpdates.remove(command.operationId) != null) command.failed(platformFailure("update-window", "ui-command-failed"), failure)
        }
        try {
            port.deferWindowMutation({ if (queuedUpdates.remove(command.operationId) != null) applyWindowUpdate(command) }, reject)
        } catch (failure: Throwable) { reject(failure) }
    }
    override fun requestUpdateCancellation(command: WindowUpdateCancellationCommand): WindowUpdateCancellationOutcome =
        if (queuedUpdates.remove(command.operationId) != null) WindowUpdateCancellationOutcome.CancelledBeforeCommit else WindowUpdateCancellationOutcome.TooLate

    private fun applyWindowUpdate(command: WindowUpdateCommand) {
        val entry = entries.values.firstOrNull { it.command.windowId == command.windowId }
        if (entry == null || !isLive(entry)) { command.failed(KadreFailure.Closed(KadreResourceKind.Window)); return }
        val current = currentState(entry) ?: run { command.failed(KadreFailure.Closed(KadreResourceKind.Window)); return }
        entry.mutating = true
        try {
            val blocked = if (current.fullscreen == FullscreenMode.Borderless && command.update.fullscreen == PropertyChange.Unchanged)
                fullscreenBlockedFields(command.update) else emptyList()
            val update = if (blocked.isEmpty()) command.update else command.update.copy(
                outerPosition = PropertyChange.Unchanged, contentSize = PropertyChange.Unchanged,
                minimumSize = PropertyChange.Unchanged, maximumSize = PropertyChange.Unchanged,
                resizable = PropertyChange.Unchanged, decorations = PropertyChange.Unchanged, systemButtons = PropertyChange.Unchanged)
            val actual = entry.mutations.apply(update, current)
            if (!isLive(entry)) { command.failed(KadreFailure.Closed(KadreResourceKind.Window)); return }
            publishMetrics(entry)
            if (blocked.isEmpty()) command.applied(actual) else command.partiallyApplied(actual,
                blocked.map { RejectedWindowField(it, KadreFailure.TemporarilyUnavailable(retryable = true)) })
        } catch (failure: Throwable) {
            if (!isLive(entry)) command.failed(KadreFailure.Closed(KadreResourceKind.Window))
            else if (failure is Win32FullscreenRecoveryFailure) {
                command.failed(platformFailure("update-window", "fullscreen-restore-failed"), failure)
                failureReporter.report(failure)
            }
            else if (entry.mutations.nativeCommitStarted) {
                try {
                    val actual = entry.mutations.readState(current)
                    publishMetrics(entry)
                    command.committedFailure(actual, command.operationId, platformFailure("update-window", "native-mutation-failed"), diagnosticCause = failure)
                } catch (readFailure: Throwable) {
                    command.failed(platformFailure("update-window", "native-readback-failed"), readFailure)
                    failureReporter.report(readFailure)
                }
            } else command.failed(if (failure is IllegalArgumentException) KadreFailure.InvalidRequest("windowUpdate")
                else platformFailure("update-window", "native-preflight-failed"), failure)
        } finally { entry.mutating = false }
    }

    private fun fullscreenBlockedFields(update: WindowUpdate): List<WindowProperty> = buildList {
        if (update.outerPosition != PropertyChange.Unchanged) add(WindowProperty.OuterPosition)
        if (update.contentSize != PropertyChange.Unchanged) add(WindowProperty.ContentSize)
        if (update.minimumSize != PropertyChange.Unchanged) add(WindowProperty.MinimumSize)
        if (update.maximumSize != PropertyChange.Unchanged) add(WindowProperty.MaximumSize)
        if (update.resizable != PropertyChange.Unchanged) add(WindowProperty.Resizable)
        if (update.decorations != PropertyChange.Unchanged) add(WindowProperty.Decorations)
        if (update.systemButtons != PropertyChange.Unchanged) add(WindowProperty.SystemButtons)
    }

    override fun requestRedraw(command: SurfaceRedrawCommand): KadreResult<Unit> {
        if (!port.isMainThread()) return port.onMainThread { requestRedraw(command) }
        val entry = entries.values.firstOrNull { it.command.surfaceId == command.surfaceId }
        if (entry == null || entry.closeAdmitted || closed.get()) return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
        pendingRedraws.getOrPut(command.surfaceId) { java.util.ArrayDeque() }.addLast(command.generation)
        port.invalidateRect(entry.window.hwnd)
        return KadreResult.Success(Unit)
    }

    override suspend fun apply(command: SurfaceUpdateCommand): KadreResult<SurfaceUpdateCommandOutcome> = port.onMainThread {
        val entry = entries.values.firstOrNull { it.command.surfaceId == command.surfaceId }
        if (entry == null || entry.closeAdmitted || closed.get()) return@onMainThread KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface))
        val outcome = when (val change = command.inputDefaultBehavior) {
            PropertyChange.Unchanged -> SurfaceFieldOutcome.Unchanged
            PropertyChange.Clear -> SurfaceFieldOutcome.Rejected(KadreFailure.InvalidRequest("inputDefaultBehavior"))
            is PropertyChange.Set -> {
                checkNotNull(entry.input).defaultBehavior = change.value
                SurfaceFieldOutcome.Applied(change.value)
            }
        }
        fun <T> applyField(change: PropertyChange<T>, apply: (T) -> Boolean): SurfaceFieldOutcome<T> = when (change) {
            PropertyChange.Unchanged -> SurfaceFieldOutcome.Unchanged
            PropertyChange.Clear -> SurfaceFieldOutcome.Rejected(KadreFailure.InvalidRequest("surfaceUpdate"))
            is PropertyChange.Set -> try {
                if (apply(change.value)) SurfaceFieldOutcome.Applied(change.value)
                else SurfaceFieldOutcome.Rejected(KadreFailure.TemporarilyUnavailable(retryable = true))
            } catch (_: Throwable) { SurfaceFieldOutcome.Rejected(platformFailure("update-surface", "native-field-failed")) }
        }
        val cursor = applyField(command.cursor) { port.setCursorStyle(entry.window.hwnd, it); true }
        val capture = applyField(command.pointerCapture) { port.setPointerConfinement(entry.window.hwnd, it == PointerCaptureMode.Confined) }
        val hitTesting = applyField(command.hitTesting) { it == HitTestingMode.Enabled }
        KadreResult.Success(SurfaceUpdateCommandOutcome(cursor, capture, hitTesting, outcome))
    }

    override fun close() {
        if (!port.isMainThread()) return port.onMainThread { close() }
        if (!closed.compareAndSet(false, true)) return
        queuedOpens.values.toList().forEach { it.fail(KadreFailure.Closed(KadreResourceKind.Host)) }
        queuedOpens.clear()
        queuedUpdates.values.toList().forEach { it.failed(KadreFailure.Closed(KadreResourceKind.Host)) }
        queuedUpdates.clear()
        entries.values.toList().forEach { entry ->
            runCatching { entry.closeDropFrontier() }
            runCatching { entry.input?.close() }
            runCatching { port.destroyWindow(entry.window.hwnd) }
        }
        entries.clear()
        pendingRedraws.clear()
    }

    private inner class Win32PeerOwner(private val entry: Win32WindowEntry) : WindowPeerOwner, RuntimeDesktopWindowHandleAccess {
        override fun close() {
            if (!port.isMainThread()) {
                port.postOnMainThread({ close() }, failureReporter::report)
                return
            }
            port.onMainThread {
                if (entries.remove(entry.command.requestId) != null) {
                    entry.closeAdmitted = true
                    entry.closeDropFrontier()
                    entry.input?.close()
                    pendingRedraws.remove(entry.command.surfaceId)
                    port.destroyWindow(entry.window.hwnd)
                    entry.command.nativeClosed()
                }
            }
        }

        override suspend fun <R> withDesktopHandle(block: (RuntimeDesktopNativeWindowHandle) -> R): KadreResult<R> = port.onMainThread {
            if (entry.closeAdmitted || entries[entry.command.requestId] !== entry)
                KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
            else KadreResult.Success(block(RuntimeDesktopNativeWindowHandle.Win32(entry.window.hwnd.toULong())))
        }
    }
}

private fun platformFailure(operation: String, detail: String): KadreFailure.PlatformFailure =
    KadreFailure.PlatformFailure(KadrePlatform.Win32, operation, detail)

internal class Win32WindowEntry(val window: Win32TopLevelWindowHandle, val command: WindowOpenCommand) {
    var closeAdmitted = false
    var input: Win32InputRouter? = null
    var drop: Win32DropRouter? = null
    var dropObserver: AutoCloseable? = null
    lateinit var mutations: Win32WindowMutations
    var mutating: Boolean = false
    var interactionGeneration: Long = 0L

    /** Revokes the native drop target, then the router, before the HWND is destroyed. */
    fun closeDropFrontier() {
        runCatching { dropObserver?.close() }
        dropObserver = null
        drop?.close()
    }
}

internal object Win32WindowRuntimeDriverFactory {
    val supportedWindowUpdateCapabilities: Set<WindowProperty> = setOf(WindowProperty.Title, WindowProperty.ContentSize,
        WindowProperty.MinimumSize, WindowProperty.MaximumSize, WindowProperty.Resizable, WindowProperty.OuterPosition,
        WindowProperty.Decorations, WindowProperty.SystemButtons, WindowProperty.Fullscreen,
        WindowProperty.Level, WindowProperty.Icon, WindowProperty.ContentProtection)
    fun create(
        port: Win32NativeHostPort,
        resources: ResourceBudgetPolicy,
        onLastWindowClosed: (() -> Unit)? = null,
        failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { },
        enabledWindowUpdateCapabilities: Set<WindowProperty> = supportedWindowUpdateCapabilities,
    ): Win32WindowRuntimeDriver = Win32WindowRuntimeDriver(resources, port, failureReporter, onLastWindowClosed, enabledWindowUpdateCapabilities)
}
