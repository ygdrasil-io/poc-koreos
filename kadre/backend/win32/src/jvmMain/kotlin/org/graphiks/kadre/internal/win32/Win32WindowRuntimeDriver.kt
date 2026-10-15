package org.graphiks.kadre.internal.win32

import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.internal.runtime.*
import org.graphiks.kadre.interaction.InteractionAction
import org.graphiks.kadre.interaction.InteractionKind
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.surface.*
import org.graphiks.kadre.window.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private fun win32SurfaceCapabilities(): SurfaceCapabilities {
    val unsupportedUpdate = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface))
    return SurfaceCapabilities(
        cursor = unsupportedUpdate,
        customCursor = unsupportedUpdate,
        pointerCapture = unsupportedUpdate,
        hitTesting = unsupportedUpdate,
        inputDefaultBehavior = Capability.Supported(InputDefaultBehavior.entries.toSet(), FeatureAvailability.Available),
        handlerInteractions = Capability.Supported(setOf(InteractionKind.OpenWindow), FeatureAvailability.Available),
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

/** One UI owner for native peers, input normalization and synchronous interactions. */
internal class Win32WindowRuntimeDriver internal constructor(
    resources: ResourceBudgetPolicy,
    private val port: Win32NativeHostPort,
    private val failureReporter: RuntimeFailureReporter,
    onLastWindowClosed: (() -> Unit)?,
    enabledWindowUpdateCapabilities: Set<WindowProperty>,
) : AutoCloseable, WindowCommandPort, SurfaceCommandPort {
    private val closed = AtomicBoolean(false)
    private val entries = ConcurrentHashMap<WindowRequestId, Win32WindowEntry>()
    private val queuedOpens = ConcurrentHashMap<WindowRequestId, WindowOpenCommand>()
    private val pendingRedraws = mutableMapOf<SurfaceId, java.util.ArrayDeque<SurfaceRedrawGeneration>>()

    internal val manager: RuntimeWindowManager = RuntimeWindowManager(
        resources = resources,
        commandPort = this,
        surfaceCommandPort = this,
        platform = KadrePlatform.Win32,
        failureReporter = failureReporter,
        publicWindowCapabilities = true,
        enabledWindowUpdateCapabilities = enabledWindowUpdateCapabilities,
        publicSurfaceCapabilities = true,
        enabledSurfaceCapabilities = win32SurfaceCapabilities(),
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
            router = Win32InputRouter(created.hwnd, command.surfaceId, port, manager::acceptSurfaceStimulus,
                { event -> dispatchInteraction(entry, event) },
                { !closed.get() && !entry.closeAdmitted && entries[command.requestId] === entry })
            entry.input = router
            val snapshot = initialSnapshot(created.hwnd)
            command.commit(
                owner = Win32PeerOwner(entry),
                effectiveSpec = command.spec.copy(contentSize = snapshot.metrics.logicalSize),
                initialSurfaceSnapshot = snapshot,
                onSurfaceReady = {
                    if (!entry.closeAdmitted && entries[command.requestId] === entry) {
                        router.activate()
                        port.showWindow(created.hwnd)
                    }
                },
            )
        } catch (failure: Throwable) {
            router?.close()
            entries.remove(command.requestId)
            window?.let { port.destroyWindow(it.hwnd) }
            command.fail(platformFailure("open-window", "create-failed"))
        }
    }

    private fun handleMessage(command: WindowOpenCommand, hwnd: Long, message: Int, wParam: Long, lParam: Long): Long =
        when (message) {
            WM_CLOSE -> { command.closeRequested(); 0L }
            WM_DESTROY -> { entries[command.requestId]?.input?.close(); command.nativeClosed(); 0L }
            WM_SIZE, WM_DPICHANGED -> {
                // Minimized windows can report a zero client size. There is no positive geometry to admit.
                val size = port.readClientPhysicalSize(hwnd)
                if (size.first > 0 && size.second > 0) {
                    val scale = port.readScaleFactor(hwnd)
                    val current = manager.state.value.windows.firstOrNull { it.id == command.windowId }?.state?.value
                    if (current != null) manager.acceptWindowGeometryObservation(command.windowId,
                        current.copy(contentSize = LogicalSize(size.first / scale, size.second / scale)))
                    manager.acceptSurfaceStimulus(SurfaceStimulus.MetricsChanged(command.surfaceId,
                        SurfaceMetrics(LogicalSize(size.first / scale, size.second / scale), PhysicalSize(size.first, size.second),
                            scale, LogicalInsets(0.0, 0.0, 0.0, 0.0))))
                }
                0L
            }
            WM_SHOWWINDOW -> {
                manager.acceptSurfaceStimulus(SurfaceStimulus.VisibilityChanged(command.surfaceId,
                    if (wParam != 0L) SurfaceVisibility.Visible else SurfaceVisibility.Hidden, SurfaceOcclusion.Unknown))
                port.defWindowProc(hwnd, message, wParam, lParam)
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

    private fun dispatchInteraction(entry: Win32WindowEntry, event: RuntimeSynchronousInteraction) {
        manager.dispatchSynchronousInteraction(entry.command.surfaceId, event, setOf(InteractionKind.OpenWindow)) { action ->
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
        entry.input?.close()
        port.destroyWindow(entry.window.hwnd)
        return PendingWindowCancellationOutcome.CancelledBeforeCommit
    }

    override fun requestUpdate(command: WindowUpdateCommand) {
        command.rejected(IllegalStateException("Win32 window mutations arrive in phase 5"))
    }
    override fun requestUpdateCancellation(command: WindowUpdateCancellationCommand): WindowUpdateCancellationOutcome = WindowUpdateCancellationOutcome.TooLate

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
        fun reject(change: PropertyChange<*>): SurfaceFieldOutcome<Nothing> = if (change == PropertyChange.Unchanged)
            SurfaceFieldOutcome.Unchanged else SurfaceFieldOutcome.Rejected(KadreFailure.Unsupported(KadreOperation.UpdateSurface))
        KadreResult.Success(SurfaceUpdateCommandOutcome(reject(command.cursor), reject(command.pointerCapture), reject(command.hitTesting), outcome))
    }

    override fun close() {
        if (!port.isMainThread()) return port.onMainThread { close() }
        if (!closed.compareAndSet(false, true)) return
        queuedOpens.values.toList().forEach { it.fail(KadreFailure.Closed(KadreResourceKind.Host)) }
        queuedOpens.clear()
        entries.values.toList().forEach { entry ->
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
}

internal object Win32WindowRuntimeDriverFactory {
    val supportedWindowUpdateCapabilities: Set<WindowProperty> = setOf(WindowProperty.Title, WindowProperty.ContentSize, WindowProperty.Decorations)
    fun create(
        port: Win32NativeHostPort,
        resources: ResourceBudgetPolicy,
        onLastWindowClosed: (() -> Unit)? = null,
        failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { },
        enabledWindowUpdateCapabilities: Set<WindowProperty> = supportedWindowUpdateCapabilities,
    ): Win32WindowRuntimeDriver = Win32WindowRuntimeDriver(resources, port, failureReporter, onLastWindowClosed, enabledWindowUpdateCapabilities)
}
