package org.graphiks.kadre.internal.appkit

import kotlinx.coroutines.suspendCancellableCoroutine
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.internal.runtime.OpenedWindowCloseCommand
import org.graphiks.kadre.internal.runtime.OpenedWindowCloseOutcome
import org.graphiks.kadre.internal.runtime.CloseRequestRejectionOutcome
import org.graphiks.kadre.internal.runtime.PendingWindowCancellationCommand
import org.graphiks.kadre.internal.runtime.PendingWindowCancellationIntent
import org.graphiks.kadre.internal.runtime.PendingWindowCancellationOutcome
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeDesktopNativeWindowHandle
import org.graphiks.kadre.internal.runtime.RuntimeDesktopWindowHandleAccess
import org.graphiks.kadre.internal.runtime.RuntimeWindowManager
import org.graphiks.kadre.internal.runtime.SurfaceCommandPort
import org.graphiks.kadre.internal.runtime.SurfaceInitialSnapshot
import org.graphiks.kadre.internal.runtime.SurfaceRedrawCommand
import org.graphiks.kadre.internal.runtime.SurfaceRedrawGeneration
import org.graphiks.kadre.internal.runtime.SurfaceStimulus
import org.graphiks.kadre.internal.runtime.SurfaceUpdateCommand
import org.graphiks.kadre.internal.runtime.SurfaceUpdateCommandOutcome
import org.graphiks.kadre.internal.runtime.WindowCommandPort
import org.graphiks.kadre.internal.runtime.WindowOpenCommand
import org.graphiks.kadre.internal.runtime.WindowPeerOwner
import org.graphiks.kadre.internal.runtime.WindowUpdateCancellationCommand
import org.graphiks.kadre.internal.runtime.WindowUpdateCancellationOutcome
import org.graphiks.kadre.internal.runtime.WindowUpdateCommand
import org.graphiks.kadre.internal.runtime.RuntimeFullscreenObservation
import org.graphiks.kadre.internal.runtime.RuntimeFullscreenObservationSink
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.window.FullscreenMode
import org.graphiks.kadre.window.RejectedWindowField
import org.graphiks.kadre.window.WindowDecorations
import org.graphiks.kadre.window.WindowLevel
import org.graphiks.kadre.window.WindowId
import org.graphiks.kadre.window.WindowRequestId
import org.graphiks.kadre.window.WindowState
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kadre.window.WindowSystemButtons
import org.graphiks.kadre.window.WindowProperty
import org.graphiks.kadre.surface.SurfaceId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Private AppKit composition root for one deterministic window-runtime session. */
internal class AppKitWindowRuntimeDriver internal constructor(
    resources: ResourceBudgetPolicy,
    nativePort: AppKitNativeWindowPort,
    failureReporter: RuntimeFailureReporter,
    publicAppKitCapabilities: Boolean,
    private val enabledWindowUpdateCapabilities: Set<WindowProperty>,
    fullscreenAvailabilityFailure: KadreFailure.PlatformFailure?,
    publicSurfaceCapabilities: Boolean,
    onLastWindowClosed: (() -> Unit)?,
    beforeCommitDelivery: (WindowSpec) -> Unit,
    beforeFullscreenFollowUpEnqueue: (AppKitFullscreenCallback) -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val fullscreenObservationSink = DeferredRuntimeFullscreenObservationSink()
    private val commandPort = AppKitWindowCommandPort(
        nativePort,
        failureReporter,
        beforeCommitDelivery,
        enabledWindowUpdateCapabilities,
        surfaceStimulusSink = { stimulus -> manager.acceptSurfaceStimulus(stimulus) },
        geometryStimulusSink = geometry@{ windowId, snapshot ->
            val state = manager.state.value.windows.firstOrNull { it.id == windowId }?.state?.value
                ?: return@geometry false
            manager.acceptWindowGeometryObservation(windowId, snapshot.withGeometryFrom(state))
        },
        windowState = { windowId ->
            manager.state.value.windows.firstOrNull { it.id == windowId }?.state?.value
        },
        fullscreenObservationSink = fullscreenObservationSink,
        beforeFullscreenFollowUpEnqueue = beforeFullscreenFollowUpEnqueue,
    )

    internal val manager: RuntimeWindowManager = RuntimeWindowManager(
        resources = resources,
        commandPort = commandPort,
        surfaceCommandPort = commandPort,
        platform = KadrePlatform.AppKit,
        failureReporter = failureReporter,
        publicWindowCapabilities = publicAppKitCapabilities,
        enabledWindowUpdateCapabilities = enabledWindowUpdateCapabilities,
        fullscreenAvailabilityFailure = fullscreenAvailabilityFailure,
        publicSurfaceCapabilities = publicSurfaceCapabilities,
        onLastWindowClosed = onLastWindowClosed,
    ).also(fullscreenObservationSink::install)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val drainMode = commandPort.beginClose()
        try {
            manager.close()
        } finally {
            commandPort.finishClose(drainMode)
        }
    }
}

private class AppKitWindowCommandPort(
    private val nativePort: AppKitNativeWindowPort,
    private val failureReporter: RuntimeFailureReporter,
    private val beforeCommitDelivery: (WindowSpec) -> Unit,
    private val enabledWindowUpdateCapabilities: Set<WindowProperty>,
    private val surfaceStimulusSink: (SurfaceStimulus) -> Boolean,
    private val geometryStimulusSink: (WindowId, AppKitWindowGeometrySnapshot) -> Boolean,
    private val windowState: (WindowId) -> WindowState?,
    private val fullscreenObservationSink: RuntimeFullscreenObservationSink,
    private val beforeFullscreenFollowUpEnqueue: (AppKitFullscreenCallback) -> Unit,
) : WindowCommandPort, SurfaceCommandPort {
    private val lock = Any()
    private val nextPeerId = AtomicLong(0L)
    private val byRequest = linkedMapOf<WindowRequestId, PeerEntry>()
    private val byPeer = linkedMapOf<AppKitWindowPeerId, PeerEntry>()
    private val byWindow = linkedMapOf<WindowId, PeerEntry>()
    private val bySurface = linkedMapOf<SurfaceId, PeerEntry>()
    private val mutationCommands = linkedMapOf<org.graphiks.kadre.window.WindowOperationId, PendingWindowMutationCommand>()
    private val commands = AppKitWindowCommandQueue(::reportFailure)
    private var closed = false

    override fun requestOpen(command: WindowOpenCommand) {
        val entry = PeerEntry(command, AppKitWindowPeerId(nextPeerId.getAndIncrement()))
        val admitted = synchronized(lock) {
            if (closed) {
                false
            } else {
                check(byRequest.put(command.requestId, entry) == null) { "duplicate AppKit window request" }
                check(byPeer.put(entry.peerId, entry) == null) { "duplicate AppKit window peer" }
                check(byWindow.put(command.windowId, entry) == null) { "duplicate AppKit window" }
                check(bySurface.put(entry.surfaceId, entry) == null) { "duplicate AppKit window surface" }
                true
            }
        }
        if (!admitted) {
            command.fail(KadreFailure.Closed(KadreResourceKind.Host))
            return
        }
        if (!commands.submit { prepare(entry) }) {
            synchronized(lock) { removeEntryLocked(entry) }
            command.fail(KadreFailure.Closed(KadreResourceKind.Host))
        }
    }

    override fun requestUpdate(command: WindowUpdateCommand) {
        val pending = synchronized(lock) {
            val entry = byWindow[command.windowId]
            if (
                closed ||
                entry == null ||
                entry.removed ||
                entry.closeAdmitted ||
                entry.peer == null
            ) {
                null
            } else {
                PendingWindowMutationCommand(entry, command).also { pending ->
                    check(mutationCommands.put(command.operationId, pending) == null) {
                        "duplicate AppKit window mutation operation"
                    }
                    if (pending.isFullscreenMutation()) {
                        check(entry.fullscreenPending == null) { "duplicate AppKit fullscreen mutation" }
                        entry.fullscreenPending = pending
                        if (entry.pendingExternalFullscreenWills > 0) {
                            pending.claimExternalFullscreenBeforeCommitLocked()
                        }
                    }
                }
            }
        }
        if (pending == null) {
            command.rejected(IllegalStateException("AppKit window geometry peer is unavailable"))
        } else if (!commands.submit { applyWindowMutation(pending) }) {
            synchronized(lock) {
                mutationCommands.remove(command.operationId, pending)
                if (pending.entry.fullscreenPending === pending) pending.entry.fullscreenPending = null
            }
            command.rejected(IllegalStateException("AppKit window mutation queue is closed"))
        }
    }

    override fun requestUpdateCancellation(
        command: WindowUpdateCancellationCommand,
    ): WindowUpdateCancellationOutcome = synchronized(lock) {
        val pending = mutationCommands[command.operationId] ?: return@synchronized WindowUpdateCancellationOutcome.TooLate
        if (!pending.claimCallerCancellationBeforeCommitLocked()) {
            WindowUpdateCancellationOutcome.TooLate
        } else {
            if (mutationCommands[command.operationId] === pending) {
                mutationCommands.remove(command.operationId)
            }
            pending.removeFullscreenDeferralLocked()
            if (pending.entry.fullscreenPending === pending) pending.entry.fullscreenPending = null
            WindowUpdateCancellationOutcome.CancelledBeforeCommit
        }
    }

    override fun requestPendingCancellation(
        command: PendingWindowCancellationCommand,
    ): PendingWindowCancellationOutcome {
        var outcome: PendingWindowCancellationOutcome =
            PendingWindowCancellationOutcome.CancellationRequested
        val entry = synchronized(lock) {
            val entry = byRequest[command.requestId]
                ?: return PendingWindowCancellationOutcome.TooLate
            if (entry.nativeTerminalIssued) {
                return PendingWindowCancellationOutcome.TooLate
            }
            if (
                entry.commitIssued &&
                command.intent == PendingWindowCancellationIntent.RequesterCancellation
            ) {
                return PendingWindowCancellationOutcome.TooLate
            }
            entry.cancellationRequested = true
            entry.cleanupCompletion = CleanupCompletion.PendingCancellation
            if (entry.commitIssued) outcome = PendingWindowCancellationOutcome.TooLate
            entry
        }
        scheduleCleanup(entry)
        return outcome
    }

    override fun requestOpenedClose(command: OpenedWindowCloseCommand): OpenedWindowCloseOutcome {
        val entry = synchronized(lock) {
            val entry = byRequest[command.requestId]
                ?: return OpenedWindowCloseOutcome.PlatformFailure(platformFailure("missing-peer"))
            if (entry.closeAdmitted || entry.nativeTerminalIssued) {
                return OpenedWindowCloseOutcome.NativeCloseAlreadyCommitted
            }
            entry.also {
                it.closeRequestPending = false
                it.closeAdmitted = true
                it.cleanupCompletion = CleanupCompletion.ProgrammaticClose
            }
        }
        scheduleNativeClose(entry)
        return OpenedWindowCloseOutcome.Accepted
    }

    override fun closeRequestRejected(command: OpenedWindowCloseCommand): CloseRequestRejectionOutcome {
        return synchronized(lock) {
            val entry = byRequest[command.requestId]
                ?: return@synchronized CloseRequestRejectionOutcome.TooLate
            if (entry.removed || entry.closeAdmitted || entry.nativeTerminalIssued) {
                CloseRequestRejectionOutcome.TooLate
            } else {
                entry.closeRequestPending = false
                CloseRequestRejectionOutcome.Rejected
            }
        }
    }

    override fun requestRedraw(command: SurfaceRedrawCommand): org.graphiks.kadre.diagnostics.KadreResult<Unit> {
        val entry = synchronized(lock) {
            bySurface[command.surfaceId]?.takeIf {
                !closed &&
                    !it.removed &&
                    !it.surfaceCleanupReserved &&
                    !it.closeAdmitted &&
                    it.peer != null
            }
        } ?: return closedSurfaceFailure()
        return if (commands.submitFollowUp { entry.peer?.requestRedraw(command.generation.value) }) {
            org.graphiks.kadre.diagnostics.KadreResult.Success(Unit)
        } else {
            closedSurfaceFailure()
        }
    }

    override suspend fun apply(
        command: SurfaceUpdateCommand,
    ): org.graphiks.kadre.diagnostics.KadreResult<SurfaceUpdateCommandOutcome> =
        org.graphiks.kadre.diagnostics.KadreResult.Failure(
            KadreFailure.TemporarilyUnavailable(retryable = false),
        )

    fun beginClose(): CloseDrainMode {
        synchronized(lock) { closed = true }
        if (!nativePort.isMainThread()) return CloseDrainMode.Blocking
        return if (commands.beginMainThreadDrain()) {
            CloseDrainMode.Inline
        } else {
            CloseDrainMode.Asynchronous
        }
    }

    fun finishClose(mode: CloseDrainMode) {
        val remaining = synchronized(lock) {
            byRequest.values.toList().asReversed()
        }
        remaining.forEach(::scheduleCleanup)
        when (mode) {
            CloseDrainMode.Inline -> commands.drainInline()
            CloseDrainMode.Asynchronous -> commands.finishAsynchronousDrain()
            CloseDrainMode.Blocking -> commands.closeAndDrain()
        }
    }

    private fun prepare(entry: PeerEntry) {
        val skipPreparation = synchronized(lock) {
            entry.removed || closed || entry.cancellationRequested
        }
        if (skipPreparation) {
            finishWithoutPreparedPeer(entry)
            return
        }

        val effectiveSpec = appKitEffectiveSpec(entry.command.spec, enabledWindowUpdateCapabilities)
        val initialLevelReadbackRequired = WindowProperty.Level in enabledWindowUpdateCapabilities
        val peer = try {
            AppKitWindowPeer.prepare(
                id = entry.peerId,
                spec = effectiveSpec,
                port = nativePort,
                acceptStimulus = ::enqueueStimulus,
                acceptSurfaceStimulus = ::enqueueSurfaceStimulus,
                reportCallbackFailure = ::reportFailure,
                readInitialWindowSnapshot = initialLevelReadbackRequired,
            )
        } catch (cause: Exception) {
            rejectPreparation(entry, cause)
            return
        } catch (cause: LinkageError) {
            rejectPreparation(entry, cause)
            return
        }
        val openingSpec = if (initialLevelReadbackRequired) {
            effectiveSpec.copy(level = checkNotNull(peer.initialWindowSnapshot).level)
        } else {
            effectiveSpec
        }

        val action = synchronized(lock) {
            entry.peer = peer
            when {
                entry.removed || closed -> PreparationAction.Cleanup
                entry.cancellationRequested -> PreparationAction.Cancel
                else -> {
                    entry.commitIssued = true
                    PreparationAction.Commit
                }
            }
        }
        when (action) {
            PreparationAction.Commit -> {
                beforeCommitDelivery(openingSpec)
                entry.command.commit(
                    entry.owner,
                    openingSpec,
                    peer.initialSurfaceSnapshot?.toRuntimeSnapshot(),
                ) {
                    commands.submitFollowUp { markRuntimeSurfaceReady(entry) }
                    commands.submitFollowUp { markRuntimeGeometryReady(entry) }
                    commands.submitFollowUp { markRuntimeWindowReady(entry) }
                }
            }
            PreparationAction.Cancel -> scheduleCleanup(entry)
            PreparationAction.Cleanup -> scheduleCleanup(entry)
        }
    }

    private fun finishWithoutPreparedPeer(entry: PeerEntry) {
        val completion = synchronized(lock) {
            if (entry.removed) return
            entry.cleanupFinished = true
            entry.cleanupCompletion
        }
        when (completion) {
            CleanupCompletion.PendingCancellation -> entry.command.fail(CANCELLATION_COMPLETION)
            CleanupCompletion.ProgrammaticClose -> issueNativeTerminal(entry)
            CleanupCompletion.None -> Unit
        }
        val heldMutations = synchronized(lock) { removeEntryLocked(entry) }
        heldMutations.forEach { pending ->
            pending.command.failed(KadreFailure.Closed(KadreResourceKind.Window))
        }
    }

    private fun enqueueStimulus(stimulus: AppKitWindowStimulus) {
        var deliverGeometryThroughSerializer = false
        val accepted = synchronized(lock) {
            val entry = byPeer[stimulus.peerId]
            when {
                closed || entry == null || entry.removed -> false
                stimulus is AppKitWindowStimulus.CloseRequested -> {
                    if (entry.closeAdmitted || entry.closeRequestPending) {
                        false
                    } else {
                        entry.closeRequestPending = true
                        true
                    }
                }

                stimulus is AppKitWindowStimulus.NativeClosed -> {
                    entry.closeRequestPending = false
                    entry.closeAdmitted = true
                    true
                }

                stimulus is AppKitWindowStimulus.GeometryChanged -> if (
                    entry.surfaceCleanupReserved || entry.closeAdmitted
                ) {
                    false
                } else {
                    if (entry.runtimeGeometryReady) {
                        deliverGeometryThroughSerializer = true
                    } else {
                        entry.bufferedGeometryStimuli.addLast(stimulus)
                    }
                    true
                }

                stimulus is AppKitWindowStimulus.FullscreenCallback -> if (
                    entry.surfaceCleanupReserved || entry.closeAdmitted
                ) {
                    false
                } else {
                    if (
                        stimulus.callback == AppKitFullscreenCallback.WillEnter ||
                        stimulus.callback == AppKitFullscreenCallback.WillExit
                    ) {
                        entry.fullscreenTransitionGateActive = true
                        entry.pendingExternalFullscreenWills += 1
                        entry.fullscreenPending?.claimExternalFullscreenBeforeCommitLocked()
                    }
                    if (entry.runtimeWindowReady) {
                        true
                    } else {
                        entry.bufferedFullscreenStimuli.addLast(stimulus)
                        false
                    }
                }

                else -> false
            }
        }
        if (accepted && (stimulus !is AppKitWindowStimulus.GeometryChanged || deliverGeometryThroughSerializer)) {
            if (stimulus is AppKitWindowStimulus.FullscreenCallback) {
                beforeFullscreenFollowUpEnqueue(stimulus.callback)
            }
            commands.submitFollowUp { acceptStimulus(stimulus) }
        }
    }

    private fun enqueueSurfaceStimulus(stimulus: AppKitSurfaceStimulus) {
        var deliverThroughSerializer = false
        val accepted = synchronized(lock) {
            val entry = byPeer[stimulus.peerId]
            if (
                entry != null &&
                !closed &&
                !entry.removed &&
                !entry.surfaceCleanupReserved &&
                !entry.closeAdmitted
            ) {
                if (entry.runtimeSurfaceReady) {
                    deliverThroughSerializer = true
                } else {
                    entry.bufferedSurfaceStimuli.addLast(stimulus)
                }
                true
            } else {
                false
            }
        }
        if (accepted && deliverThroughSerializer) {
            commands.submitFollowUp { acceptSurfaceStimulus(stimulus) }
        }
    }

    private fun markRuntimeSurfaceReady(entry: PeerEntry) {
        val bufferedSurfaceStimuli = synchronized(lock) {
            if (
                entry.removed ||
                entry.surfaceCleanupReserved ||
                entry.closeAdmitted ||
                closed
            ) {
                entry.bufferedSurfaceStimuli.clear()
                emptyList()
            } else {
                entry.runtimeSurfaceReady = true
                entry.bufferedSurfaceStimuli.toList().also {
                    entry.bufferedSurfaceStimuli.clear()
                }
            }
        }
        // This callback itself runs on the command queue, so the FIFO drain is serialized with
        // every later surface callback admitted through submitFollowUp().
        bufferedSurfaceStimuli.forEach(::acceptSurfaceStimulus)
    }

    private fun markRuntimeGeometryReady(entry: PeerEntry) {
        val bufferedGeometryStimuli = synchronized(lock) {
            if (entry.removed || entry.surfaceCleanupReserved || entry.closeAdmitted || closed) {
                entry.bufferedGeometryStimuli.clear()
                emptyList()
            } else {
                entry.runtimeGeometryReady = true
                entry.bufferedGeometryStimuli.toList().also { entry.bufferedGeometryStimuli.clear() }
            }
        }
        bufferedGeometryStimuli.forEach(::acceptStimulus)
    }

    private fun markRuntimeWindowReady(entry: PeerEntry) {
        val buffered = synchronized(lock) {
            if (entry.removed || entry.surfaceCleanupReserved || entry.closeAdmitted || closed) {
                entry.bufferedFullscreenStimuli.clear()
                emptyList()
            } else {
                entry.runtimeWindowReady = true
                entry.bufferedFullscreenStimuli.toList().also { entry.bufferedFullscreenStimuli.clear() }
            }
        }
        buffered.forEach(::acceptStimulus)
    }

    private fun acceptSurfaceStimulus(stimulus: AppKitSurfaceStimulus) {
        val surfaceId = synchronized(lock) {
            byPeer[stimulus.peerId]?.takeIf {
                !it.removed && !it.surfaceCleanupReserved && it.commitIssued
            }?.surfaceId
        } ?: return
        surfaceStimulusSink(stimulus.toRuntime(surfaceId))
    }

    private fun acceptStimulus(stimulus: AppKitWindowStimulus) {
        val entry = synchronized(lock) {
            byPeer[stimulus.peerId]?.takeUnless(PeerEntry::removed)
        } ?: return
        when (stimulus) {
            is AppKitWindowStimulus.CloseRequested -> {
                val pending = synchronized(lock) {
                    entry.closeRequestPending && !entry.closeAdmitted
                }
                if (pending) entry.command.closeRequested()
            }

            is AppKitWindowStimulus.NativeClosed -> {
                synchronized(lock) { entry.surfaceCleanupReserved = true }
                entry.peer?.markNativeClosed()
                issueNativeTerminal(entry)
                scheduleCleanup(entry)
            }

            is AppKitWindowStimulus.GeometryChanged -> {
                val fresh = synchronized(lock) {
                    stimulus.generation > entry.managedGeometryGeneration
                }
                if (fresh) geometryStimulusSink(entry.command.windowId, stimulus.snapshot)
            }

            is AppKitWindowStimulus.FullscreenCallback -> acceptFullscreenCallback(entry, stimulus.callback)
        }
    }

    private fun applyWindowMutation(pending: PendingWindowMutationCommand) {
        val admission = synchronized(lock) {
            when {
                pending.nativeCommitPrevented || mutationCommands[pending.command.operationId] !== pending ->
                    WindowMutationCommandAdmission.Cancelled

                closed || pending.entry.removed || pending.entry.closeAdmitted -> {
                    mutationCommands.remove(pending.command.operationId, pending)
                    if (pending.entry.fullscreenPending === pending) pending.entry.fullscreenPending = null
                    WindowMutationCommandAdmission.Rejected
                }

                pending.entry.peer == null -> {
                    mutationCommands.remove(pending.command.operationId, pending)
                    if (pending.entry.fullscreenPending === pending) pending.entry.fullscreenPending = null
                    WindowMutationCommandAdmission.Rejected
                }

                pending.deferOrdinaryBehindFullscreenTransitionLocked() ->
                    WindowMutationCommandAdmission.Deferred

                else -> WindowMutationCommandAdmission.Ready(checkNotNull(pending.entry.peer))
            }
        }
        when (admission) {
            WindowMutationCommandAdmission.Cancelled -> {
                synchronized(lock) {
                    if (pending.entry.fullscreenPending === pending) pending.entry.fullscreenPending = null
                    if (mutationCommands[pending.command.operationId] === pending) {
                        mutationCommands.remove(pending.command.operationId)
                    }
                }
                return
            }
            WindowMutationCommandAdmission.Deferred -> return
            WindowMutationCommandAdmission.Rejected -> {
                pending.command.rejected(IllegalStateException("AppKit window mutation command closed before native commit"))
                return
            }

            is WindowMutationCommandAdmission.Ready -> Unit
        }
        val fullscreen = (pending.command.update.fullscreen as? PropertyChange.Set)?.value
        if (fullscreen == FullscreenMode.Borderless || fullscreen == FullscreenMode.Windowed) {
            applyFullscreenMutation(pending, admission.peer, fullscreen)
            return
        }
        val expectedRevision = pending.command.expectedRevision
        val currentRevision = expectedRevision?.let {
            windowState(pending.command.windowId)?.revision
        }
        if (expectedRevision != null && currentRevision != null && expectedRevision != currentRevision) {
            val fail = synchronized(lock) {
                pending.claimExpectedRevisionFailureLocked()
            }
            if (fail) {
                pending.command.failed(
                    KadreFailure.StaleRevision(expectedRevision.value, currentRevision.value),
                )
            }
            return
        }
        val mutation = try {
            admission.peer.updateWindow(pending.command.toMutationTarget(), pending)
        } catch (cause: Throwable) {
            val reject = synchronized(lock) {
                mutationCommands.remove(pending.command.operationId, pending) && !pending.nativeCommitPrevented
            }
            if (reject) pending.command.rejected(cause)
            return
        }
        if (mutation == null) {
            val reject = synchronized(lock) {
                if (pending.isDeferredBehindFullscreenTransitionLocked()) {
                    false
                } else {
                    mutationCommands.remove(pending.command.operationId, pending) && !pending.nativeCommitPrevented
                }
            }
            if (reject) {
                pending.command.rejected(IllegalStateException("AppKit window mutation peer closed before commit"))
            }
            return
        }
        synchronized(lock) {
            mutationCommands.remove(pending.command.operationId, pending)
            pending.entry.managedGeometryGeneration = maxOf(
                pending.entry.managedGeometryGeneration,
                mutation.generation,
            )
        }
        val current = windowState(pending.command.windowId)
        if (current == null) {
            pending.command.rejected(IllegalStateException("AppKit window runtime is unavailable"))
            return
        }
        val effective = mutation.snapshot.withMutationFrom(current)
        val failure = mutation.failure
        if (failure == null) {
            pending.command.applied(effective)
            return
        }
        reportFailure(failure)
        val rejected = pending.command.rejectedMutationFields(
            mutation.snapshot,
            platformFailure("window-update-rejected"),
        )
        if (rejected.isEmpty()) {
            pending.command.applied(effective)
        } else {
            pending.command.partiallyApplied(effective, rejected)
        }
    }

    private fun applyFullscreenMutation(
        pending: PendingWindowMutationCommand,
        peer: AppKitWindowPeer,
        target: FullscreenMode,
    ) {
        peer.beginFullscreenToggleArbitration()
        if (!pending.beginFullscreenCommitArbitration()) {
            synchronized(lock) {
                if (pending.entry.fullscreenPending === pending) pending.entry.fullscreenPending = null
                if (mutationCommands[pending.command.operationId] === pending) {
                    mutationCommands.remove(pending.command.operationId)
                }
            }
            return
        }
        val toggled = try {
            peer.toggleFullscreen(
                AppKitWindowFullscreenTarget(target),
                pending,
                arbitrationAlreadyStarted = true,
            )
        } catch (failure: Throwable) {
            commands.submitFollowUp {
                finishFullscreenSelectorInvocation(
                    pending.entry,
                    pending,
                    peer,
                    target,
                    fullscreenFailure("selector-threw"),
                )
            }
            return
        }
        if (toggled == true) {
            commands.submitFollowUp {
                finishFullscreenSelectorInvocation(pending.entry, pending, peer, target, selectorFailure = null)
            }
            return
        }
        if (pending.nativeCommitPrevented || peer.fullscreenWillObservedSinceToggle()) return
        val reject = synchronized(lock) {
            if (pending.entry.fullscreenPending === pending) pending.entry.fullscreenPending = null
            if (mutationCommands[pending.command.operationId] === pending) {
                mutationCommands.remove(pending.command.operationId)
                true
            } else {
                false
            }
        }
        if (reject) {
            pending.command.rejected(IllegalStateException("AppKit fullscreen peer closed before native commit"))
        }
    }

    private fun finishFullscreenSelectorInvocation(
        entry: PeerEntry,
        pending: PendingWindowMutationCommand,
        peer: AppKitWindowPeer,
        target: FullscreenMode,
        selectorFailure: KadreFailure?,
    ) {
        val stillPending = synchronized(lock) {
            entry.fullscreenPending === pending &&
                mutationCommands[pending.command.operationId] === pending
        }
        val terminalSubmitted = if (
            selectorFailure != null &&
            stillPending &&
            !peer.fullscreenWillObservedSinceToggle()
        ) {
            completeSelectorFailure(entry, pending, target)
        } else {
            false
        }
        pending.command.fullscreenSelectorReturned(
            if (terminalSubmitted) null else selectorFailure,
        )
    }

    private fun acceptFullscreenCallback(entry: PeerEntry, callback: AppKitFullscreenCallback) {
        val target = when (callback) {
            AppKitFullscreenCallback.WillEnter,
            AppKitFullscreenCallback.DidEnter,
            AppKitFullscreenCallback.DidFailEnter,
            -> FullscreenMode.Borderless
            AppKitFullscreenCallback.WillExit,
            AppKitFullscreenCallback.DidExit,
            AppKitFullscreenCallback.DidFailExit,
            -> FullscreenMode.Windowed
        }
        val correlated = synchronized(lock) {
            if (
                callback == AppKitFullscreenCallback.WillEnter ||
                callback == AppKitFullscreenCallback.WillExit
            ) {
                entry.fullscreenDidTombstone = null
                check(entry.pendingExternalFullscreenWills > 0) {
                    "AppKit fullscreen Will claim is missing"
                }
                entry.pendingExternalFullscreenWills -= 1
            }
            val pending = entry.fullscreenPending ?: return@synchronized null
            if (pending.nativeCommitStarted) {
                pending
            } else {
                if (
                    callback != AppKitFullscreenCallback.DidFailEnter &&
                    callback != AppKitFullscreenCallback.DidFailExit
                ) {
                    pending.claimExternalFullscreenBeforeCommitLocked()
                    if (mutationCommands[pending.command.operationId] === pending) {
                        mutationCommands.remove(pending.command.operationId)
                    }
                    if (entry.fullscreenPending === pending) entry.fullscreenPending = null
                }
                null
            }
        }
        when (callback) {
            AppKitFullscreenCallback.WillEnter,
            AppKitFullscreenCallback.WillExit,
            -> if (correlated != null) {
                correlated.command.fullscreenWill(target)
            } else {
                fullscreenObservationSink.accept(
                    entry.command.windowId,
                    RuntimeFullscreenObservation.Will(target),
                )
            }

            AppKitFullscreenCallback.DidEnter,
            AppKitFullscreenCallback.DidExit,
            -> completeFullscreen(entry, correlated, target)

            AppKitFullscreenCallback.DidFailEnter,
            AppKitFullscreenCallback.DidFailExit,
            -> {
                if (correlated != null) {
                    completeFullscreenFailure(entry, correlated, target)
                } else {
                    fullscreenObservationSink.accept(
                        entry.command.windowId,
                        RuntimeFullscreenObservation.DidFail(target),
                    )
                }
            }
        }
        if (
            callback == AppKitFullscreenCallback.DidEnter ||
            callback == AppKitFullscreenCallback.DidExit ||
            callback == AppKitFullscreenCallback.DidFailEnter ||
            callback == AppKitFullscreenCallback.DidFailExit
        ) {
            releaseMutationsHeldBehindFullscreenTransition(entry)
        }
    }

    private fun releaseMutationsHeldBehindFullscreenTransition(entry: PeerEntry) {
        val held = synchronized(lock) {
            if (entry.pendingExternalFullscreenWills > 0 || entry.nativeCloseScheduled) {
                return@synchronized emptyList()
            }
            entry.fullscreenTransitionGateActive = false
            entry.mutationsHeldBehindFullscreenTransition.toList().also { commands ->
                entry.mutationsHeldBehindFullscreenTransition.clear()
                commands.forEach(PendingWindowMutationCommand::releaseFullscreenDeferralLocked)
            }
        }
        held.forEach { pending ->
            if (!commands.submitFollowUp { applyWindowMutation(pending) }) {
                val reject = synchronized(lock) {
                    pending.removeFullscreenDeferralLocked()
                    mutationCommands.remove(pending.command.operationId, pending) &&
                        !pending.nativeCommitPrevented
                }
                if (reject) {
                    pending.command.rejected(
                        IllegalStateException("AppKit window mutation queue closed during fullscreen deferral"),
                    )
                }
            }
        }
    }

    private fun completeFullscreen(
        entry: PeerEntry,
        pending: PendingWindowMutationCommand?,
        target: FullscreenMode,
    ) {
        val peer = synchronized(lock) {
            if (pending == null && entry.fullscreenDidTombstone == target) return@synchronized null
            entry.peer?.also { entry.fullscreenDidTombstone = target }
        } ?: return
        val desiredLevel = pending?.fullscreenDesiredLevel
            ?: fullscreenObservationSink.desiredLevel(entry.command.windowId)
            ?: return
        val completion = try {
            peer.completeFullscreen(desiredLevel)
        } catch (failure: Throwable) {
            if (pending != null) {
                finishFullscreenPending(entry, pending)
                pending.command.failed(fullscreenFailure("level-readback-failed"))
            } else {
                reportFullscreenTerminalFailure("level-readback-failed", failure)
            }
            scheduleNativeClose(entry)
            return
        }
        val snapshot = completion.snapshot
        val current = windowState(entry.command.windowId) ?: return
        val effective = snapshot.withMutationFrom(current).copy(fullscreen = target)
        if (pending != null) {
            finishFullscreenPending(entry, pending)
            val requestedTarget = (pending.command.update.fullscreen as? PropertyChange.Set)?.value
            if (target != requestedTarget) {
                pending.command.fullscreenDid(effective)
                if (completion.restoreFailure != null || snapshot.level != desiredLevel) {
                    reportFailure(KadreException(fullscreenFailure("level-restore-failed")))
                }
            } else if (completion.restoreFailure == null && snapshot.level == desiredLevel) {
                pending.command.fullscreenDid(effective)
            } else {
                pending.command.committedFailure(
                    effectiveState = effective,
                    publicationOperationId = pending.command.operationId,
                    failure = fullscreenFailure("level-restore-failed"),
                )
            }
        } else {
            fullscreenObservationSink.accept(
                entry.command.windowId,
                RuntimeFullscreenObservation.Did(effective),
            )
            if (completion.restoreFailure != null || snapshot.level != desiredLevel) {
                reportFullscreenTerminalFailure("level-restore-failed", completion.restoreFailure)
            }
        }
    }

    private fun completeFullscreenFailure(
        entry: PeerEntry,
        pending: PendingWindowMutationCommand,
        target: FullscreenMode,
    ) {
        val effective = readFullscreenFailureState(entry, pending) ?: return
        finishFullscreenPending(entry, pending)
        pending.command.fullscreenDidFail(target, effective)
    }

    private fun completeSelectorFailure(
        entry: PeerEntry,
        pending: PendingWindowMutationCommand,
        target: FullscreenMode,
    ): Boolean {
        val effective = readFullscreenFailureState(entry, pending) ?: return false
        finishFullscreenPending(entry, pending)
        pending.command.fullscreenDidFail(
            target = target,
            effectiveState = effective,
            terminalFailure = fullscreenFailure("selector-threw"),
        )
        return true
    }

    private fun readFullscreenFailureState(
        entry: PeerEntry,
        pending: PendingWindowMutationCommand,
    ): WindowState? {
        val peer = synchronized(lock) { entry.peer } ?: return null
        val desiredLevel = pending.fullscreenDesiredLevel
        val completion = try {
            peer.completeFullscreen(desiredLevel)
        } catch (failure: Throwable) {
            finishFullscreenPending(entry, pending)
            pending.command.failed(fullscreenFailure("level-readback-failed"))
            scheduleNativeClose(entry)
            return null
        }
        val snapshot = completion.snapshot
        val current = windowState(entry.command.windowId) ?: return null
        return snapshot.withMutationFrom(current)
    }

    private fun finishFullscreenPending(entry: PeerEntry, pending: PendingWindowMutationCommand) {
        synchronized(lock) {
            if (entry.fullscreenPending === pending) entry.fullscreenPending = null
            mutationCommands.remove(pending.command.operationId, pending)
        }
    }

    private fun scheduleNativeClose(entry: PeerEntry) {
        val submit = synchronized(lock) {
            if (entry.removed || entry.nativeCloseScheduled || entry.nativeTerminalIssued) {
                false
            } else {
                entry.nativeCloseScheduled = true
                true
            }
        }
        if (submit) commands.submitFollowUp { performNativeClose(entry) }
    }

    private fun performNativeClose(entry: PeerEntry) {
        val peer = synchronized(lock) {
            if (entry.removed || entry.nativeTerminalIssued) return
            entry.peer
        }
        val failure = try {
            peer?.commitNativeClose()
            null
        } catch (cause: Exception) {
            cause
        } catch (cause: LinkageError) {
            cause
        }
        failure?.let(::reportFailure)
        issueNativeTerminal(entry)
        scheduleCleanup(entry)
    }

    private suspend fun <R> withDesktopHandle(
        entry: PeerEntry,
        block: (RuntimeDesktopNativeWindowHandle) -> R,
    ): org.graphiks.kadre.diagnostics.KadreResult<R> = suspendCancellableCoroutine { continuation ->
        val admission = HandleLeaseAdmission()
        continuation.invokeOnCancellation { admission.cancel() }
        val submitted = synchronized(lock) {
            if (closed || entry.removed || entry.closeAdmitted || entry.peer == null) {
                false
            } else {
                commands.submit {
                    val peer = synchronized(lock) { entry.peer }
                    if (peer == null) {
                        if (continuation.isActive) {
                            continuation.resume(closedHandleFailure()) { _, _, _ -> }
                        }
                        return@submit
                    }
                    try {
                        val result = peer.withDesktopHandle(
                            admitCallback = {
                                synchronized(lock) {
                                    !closed && !entry.removed && !entry.closeAdmitted && admission.admit()
                                }
                            },
                            block = block,
                        )
                        if (result == null) {
                            if (continuation.isActive) {
                                continuation.resume(closedHandleFailure()) { _, _, _ -> }
                            }
                        } else {
                            if (continuation.isActive) continuation.resume(result) { _, _, _ -> }
                        }
                    } catch (cause: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(cause)
                    }
                }
            }
        }
        if (!submitted) {
            continuation.resume(closedHandleFailure()) { _, _, _ -> }
        }
    }

    private fun <R> closedHandleFailure(): org.graphiks.kadre.diagnostics.KadreResult<R> =
        org.graphiks.kadre.diagnostics.KadreResult.Failure(
            KadreFailure.Closed(KadreResourceKind.Window),
        )

    private fun rejectPreparation(
        entry: PeerEntry,
        cause: Throwable,
    ) {
        val cancelled = synchronized(lock) {
            entry.cleanupFinished = true
            entry.cancellationRequested
        }
        reportFailure(cause)
        if (cancelled) {
            issueNativeTerminal(entry)
        } else {
            entry.command.fail(platformFailure("open-exception"))
        }
        synchronized(lock) { removeEntryLocked(entry) }
    }

    private fun scheduleCleanup(entry: PeerEntry) {
        val submit = synchronized(lock) {
            if (entry.removed || entry.cleanupScheduled || entry.cleanupFinished) {
                false
            } else {
                entry.surfaceCleanupReserved = true
                entry.cleanupScheduled = true
                true
            }
        }
        if (submit) commands.submitFollowUp { performCleanup(entry) }
    }

    private fun performCleanup(entry: PeerEntry) {
        val peer = synchronized(lock) {
            if (entry.removed && entry.cleanupFinished) return
            entry.peer
        }
        val failure = try {
            peer?.close()
            null
        } catch (cause: Exception) {
            cause
        } catch (cause: LinkageError) {
            cause
        }
        failure?.let(::reportFailure)
        val completion = synchronized(lock) {
            entry.peer = null
            entry.cleanupFinished = true
            entry.cleanupCompletion
        }
        when (completion) {
            CleanupCompletion.PendingCancellation -> if (failure == null) {
                entry.command.fail(CANCELLATION_COMPLETION)
            } else {
                issueNativeTerminal(entry)
            }

            CleanupCompletion.ProgrammaticClose -> issueNativeTerminal(entry)
            CleanupCompletion.None -> Unit
        }
        val heldMutations = synchronized(lock) { removeEntryLocked(entry) }
        heldMutations.forEach { pending ->
            pending.command.failed(KadreFailure.Closed(KadreResourceKind.Window))
        }
    }

    private fun issueNativeTerminal(entry: PeerEntry) {
        val issue = synchronized(lock) {
            if (entry.nativeTerminalIssued) {
                false
            } else {
                entry.nativeTerminalIssued = true
                true
            }
        }
        if (issue) entry.command.nativeClosed()
    }

    private fun removeEntryLocked(entry: PeerEntry): List<PendingWindowMutationCommand> {
        entry.removed = true
        entry.bufferedSurfaceStimuli.clear()
        entry.bufferedGeometryStimuli.clear()
        entry.bufferedFullscreenStimuli.clear()
        entry.fullscreenTransitionGateActive = false
        entry.pendingExternalFullscreenWills = 0
        val heldMutations = entry.mutationsHeldBehindFullscreenTransition.toList()
        mutationCommands.values.filter { pending -> pending.entry === entry }.forEach { pending ->
            pending.cancelFullscreenDeferralForTeardownLocked()
            mutationCommands.remove(pending.command.operationId, pending)
        }
        entry.mutationsHeldBehindFullscreenTransition.clear()
        entry.fullscreenPending = null
        if (byRequest[entry.command.requestId] === entry) byRequest.remove(entry.command.requestId)
        if (byPeer[entry.peerId] === entry) byPeer.remove(entry.peerId)
        if (byWindow[entry.command.windowId] === entry) byWindow.remove(entry.command.windowId)
        if (bySurface[entry.surfaceId] === entry) bySurface.remove(entry.surfaceId)
        return heldMutations
    }

    private fun reportFailure(cause: Throwable) {
        try {
            failureReporter.report(cause)
        } catch (_: Exception) {
            // Diagnostics cannot destabilise AppKit ownership cleanup.
        } catch (_: LinkageError) {
            // Diagnostics cannot destabilise AppKit ownership cleanup.
        }
    }

    private fun reportFullscreenTerminalFailure(code: String, cause: Throwable?) {
        val diagnostic = KadreException(fullscreenFailure(code))
        cause?.let(diagnostic::addSuppressed)
        reportFailure(diagnostic)
    }

    private fun platformFailure(code: String): KadreFailure.PlatformFailure =
        KadreFailure.PlatformFailure(KadrePlatform.AppKit, "appkit-window", code)

    private fun closedSurfaceFailure(): org.graphiks.kadre.diagnostics.KadreResult<Unit> =
        org.graphiks.kadre.diagnostics.KadreResult.Failure(
            KadreFailure.Closed(KadreResourceKind.Surface),
        )

    private inner class PeerEntry(
        val command: WindowOpenCommand,
        val peerId: AppKitWindowPeerId,
    ) {
        val surfaceId: SurfaceId = command.surfaceId
        var peer: AppKitWindowPeer? = null
        var cancellationRequested: Boolean = false
        var commitIssued: Boolean = false
        var runtimeSurfaceReady: Boolean = false
        val bufferedSurfaceStimuli = ArrayDeque<AppKitSurfaceStimulus>()
        var runtimeGeometryReady: Boolean = false
        var managedGeometryGeneration: Long = 0L
        val bufferedGeometryStimuli = ArrayDeque<AppKitWindowStimulus.GeometryChanged>()
        var runtimeWindowReady: Boolean = false
        val bufferedFullscreenStimuli = ArrayDeque<AppKitWindowStimulus.FullscreenCallback>()
        var pendingExternalFullscreenWills: Int = 0
        var fullscreenTransitionGateActive: Boolean = false
        val mutationsHeldBehindFullscreenTransition = ArrayDeque<PendingWindowMutationCommand>()
        var fullscreenPending: PendingWindowMutationCommand? = null
        var fullscreenDidTombstone: FullscreenMode? = null
        var cleanupScheduled: Boolean = false
        var cleanupFinished: Boolean = false
        var cleanupCompletion: CleanupCompletion = CleanupCompletion.None
        var nativeTerminalIssued: Boolean = false
        var nativeCloseScheduled: Boolean = false
        var closeRequestPending: Boolean = false
        var closeAdmitted: Boolean = false
        var surfaceCleanupReserved: Boolean = false
        var removed: Boolean = false
        val owner: WindowPeerOwner = object : WindowPeerOwner, RuntimeDesktopWindowHandleAccess {
            override fun close() {
                scheduleCleanup(this@PeerEntry)
            }

            override suspend fun <R> withDesktopHandle(
                block: (RuntimeDesktopNativeWindowHandle) -> R,
            ): org.graphiks.kadre.diagnostics.KadreResult<R> =
                this@AppKitWindowCommandPort.withDesktopHandle(this@PeerEntry, block)
        }
    }

    private enum class PreparationAction {
        Commit,
        Cancel,
        Cleanup,
    }

    private enum class CleanupCompletion {
        None,
        PendingCancellation,
        ProgrammaticClose,
    }

    private class HandleLeaseAdmission {
        private val state = AtomicReference(HandleLeaseState.Queued)

        fun cancel() {
            state.compareAndSet(HandleLeaseState.Queued, HandleLeaseState.Cancelled)
        }

        fun admit(): Boolean = state.compareAndSet(HandleLeaseState.Queued, HandleLeaseState.Admitted)
    }

    private inner class PendingWindowMutationCommand(
        val entry: PeerEntry,
        val command: WindowUpdateCommand,
        val fullscreenDesiredLevel: WindowLevel = command.desiredLevel,
    ) : AppKitWindowMutationCommit {
        private var commitState: WindowMutationCommitState = WindowMutationCommitState.Queued
        private var deferredBehindFullscreenTransition: Boolean = false

        val nativeCommitPrevented: Boolean
            get() = synchronized(lock) {
                commitState == WindowMutationCommitState.Cancelled ||
                    commitState == WindowMutationCommitState.ExternalClaimed
            }

        val nativeCommitStarted: Boolean
            get() = synchronized(lock) { commitState == WindowMutationCommitState.Committed }

        override val started: Boolean
            get() = nativeCommitStarted

        fun beginFullscreenCommitArbitration(): Boolean = synchronized(lock) {
            when {
                commitState == WindowMutationCommitState.Committed -> true
                commitState == WindowMutationCommitState.Cancelled ||
                    commitState == WindowMutationCommitState.ExternalClaimed -> false
                mutationCommands[command.operationId] !== this || entry.fullscreenPending !== this -> {
                    commitState = WindowMutationCommitState.Cancelled
                    false
                }
                closed || entry.removed || entry.closeAdmitted || entry.peer == null -> {
                    commitState = WindowMutationCommitState.Cancelled
                    false
                }
                else -> {
                    check(commitState == WindowMutationCommitState.Queued) {
                        "AppKit fullscreen commit arbitration is already in progress"
                    }
                    commitState = WindowMutationCommitState.Arbitrating
                    true
                }
            }
        }

        fun claimCallerCancellationBeforeCommitLocked(): Boolean = when (commitState) {
            WindowMutationCommitState.Committed,
            WindowMutationCommitState.ExternalClaimed,
            -> false
            WindowMutationCommitState.Cancelled -> true
            WindowMutationCommitState.Queued,
            WindowMutationCommitState.Arbitrating,
            -> {
                commitState = WindowMutationCommitState.Cancelled
                true
            }
        }

        fun claimExternalFullscreenBeforeCommitLocked(): Boolean = when (commitState) {
            WindowMutationCommitState.Committed,
            WindowMutationCommitState.Cancelled,
            -> false
            WindowMutationCommitState.ExternalClaimed -> true
            WindowMutationCommitState.Queued,
            WindowMutationCommitState.Arbitrating,
            -> {
                commitState = WindowMutationCommitState.ExternalClaimed
                true
            }
        }

        fun claimExpectedRevisionFailureLocked(): Boolean {
            if (
                commitState != WindowMutationCommitState.Queued ||
                mutationCommands[command.operationId] !== this
            ) {
                return false
            }
            commitState = WindowMutationCommitState.Cancelled
            removeFullscreenDeferralLocked()
            return mutationCommands.remove(command.operationId, this)
        }

        fun deferOrdinaryBehindFullscreenTransitionLocked(): Boolean {
            if (isFullscreenMutation() || !entry.fullscreenTransitionGateActive) return false
            return when (commitState) {
                WindowMutationCommitState.Queued -> {
                    if (!deferredBehindFullscreenTransition) {
                        deferredBehindFullscreenTransition = true
                        entry.mutationsHeldBehindFullscreenTransition.addLast(this)
                    }
                    true
                }
                WindowMutationCommitState.Arbitrating,
                WindowMutationCommitState.Committed,
                WindowMutationCommitState.ExternalClaimed,
                WindowMutationCommitState.Cancelled,
                -> false
            }
        }

        fun isDeferredBehindFullscreenTransitionLocked(): Boolean = deferredBehindFullscreenTransition

        fun releaseFullscreenDeferralLocked() {
            deferredBehindFullscreenTransition = false
        }

        fun removeFullscreenDeferralLocked() {
            if (!deferredBehindFullscreenTransition) return
            deferredBehindFullscreenTransition = false
            entry.mutationsHeldBehindFullscreenTransition.remove(this)
        }

        fun cancelFullscreenDeferralForTeardownLocked() {
            deferredBehindFullscreenTransition = false
            if (commitState != WindowMutationCommitState.Committed) {
                commitState = WindowMutationCommitState.Cancelled
            }
        }

        override fun beforeFirstSetter(): Boolean {
            if (!isFullscreenMutation()) {
                return synchronized(lock) {
                    when {
                        commitState == WindowMutationCommitState.Committed -> true
                        commitState == WindowMutationCommitState.Cancelled ||
                            commitState == WindowMutationCommitState.ExternalClaimed -> false
                        mutationCommands[command.operationId] !== this -> false
                        closed || entry.removed || entry.closeAdmitted || entry.peer == null -> false
                        deferOrdinaryBehindFullscreenTransitionLocked() -> false
                        else -> {
                            commitState = WindowMutationCommitState.Committed
                            true
                        }
                    }
                }
            }
            val arbitrate = synchronized(lock) {
                when {
                    commitState == WindowMutationCommitState.Committed -> return true
                    commitState == WindowMutationCommitState.Cancelled ||
                        commitState == WindowMutationCommitState.ExternalClaimed -> false
                    mutationCommands[command.operationId] !== this -> false
                    closed || entry.removed || entry.closeAdmitted || entry.peer == null -> false
                    else -> {
                        check(commitState == WindowMutationCommitState.Arbitrating) {
                            "AppKit fullscreen setter reached before commit arbitration"
                        }
                        true
                    }
                }
            }
            if (!arbitrate) return false
            val admitted = command.fullscreenSelectorInvoking()
            return synchronized(lock) {
                if (admitted) {
                    commitState = WindowMutationCommitState.Committed
                    entry.fullscreenDidTombstone = null
                    true
                } else {
                    if (commitState == WindowMutationCommitState.Arbitrating) {
                        commitState = WindowMutationCommitState.Cancelled
                    }
                    false
                }
            }
        }

        fun isFullscreenMutation(): Boolean =
            (command.update.fullscreen as? PropertyChange.Set)?.value.let { target ->
                target == FullscreenMode.Borderless || target == FullscreenMode.Windowed
            }
    }

    private enum class WindowMutationCommitState {
        Queued,
        Arbitrating,
        Committed,
        ExternalClaimed,
        Cancelled,
    }

    private sealed interface WindowMutationCommandAdmission {
        data object Cancelled : WindowMutationCommandAdmission
        data object Deferred : WindowMutationCommandAdmission
        data object Rejected : WindowMutationCommandAdmission
        data class Ready(val peer: AppKitWindowPeer) : WindowMutationCommandAdmission
    }

    private enum class HandleLeaseState {
        Queued,
        Admitted,
        Cancelled,
    }

    enum class CloseDrainMode {
        Inline,
        Asynchronous,
        Blocking,
    }

    private companion object {
        val CANCELLATION_COMPLETION: KadreFailure = KadreFailure.TemporarilyUnavailable(retryable = false)
    }
}

private fun fullscreenFailure(code: String): KadreFailure.PlatformFailure =
    KadreFailure.PlatformFailure(KadrePlatform.AppKit, "fullscreen", code)

private class DeferredRuntimeFullscreenObservationSink : RuntimeFullscreenObservationSink {
    private val delegate = AtomicReference<RuntimeFullscreenObservationSink?>()

    fun install(sink: RuntimeFullscreenObservationSink) {
        check(delegate.compareAndSet(null, sink)) { "AppKit fullscreen observation sink is already installed" }
    }

    override fun accept(windowId: WindowId, observation: RuntimeFullscreenObservation): Boolean =
        checkNotNull(delegate.get()) { "AppKit fullscreen observation sink is not installed" }
            .accept(windowId, observation)

    override fun desiredLevel(windowId: WindowId): WindowLevel? =
        checkNotNull(delegate.get()) { "AppKit fullscreen observation sink is not installed" }
            .desiredLevel(windowId)
}

private fun AppKitSurfaceStimulus.toRuntime(surfaceId: SurfaceId): SurfaceStimulus = when (this) {
    is AppKitSurfaceStimulus.MetricsChanged -> SurfaceStimulus.MetricsChanged(surfaceId, metrics)
    is AppKitSurfaceStimulus.FocusChanged -> SurfaceStimulus.FocusChanged(surfaceId, focus)
    is AppKitSurfaceStimulus.VisibilityChanged -> SurfaceStimulus.VisibilityChanged(surfaceId, visibility, occlusion)
    is AppKitSurfaceStimulus.ThemeChanged -> SurfaceStimulus.ThemeChanged(surfaceId, theme)
    is AppKitSurfaceStimulus.RedrawConsumed -> SurfaceStimulus.RedrawConsumed(
        surfaceId,
        SurfaceRedrawGeneration.fromNative(generation),
    )
    is AppKitSurfaceStimulus.InputObservationChanged -> SurfaceStimulus.InputObservationChanged(
        surfaceId,
        keyboardInstalled,
        pointerInstalled,
    )
    is AppKitSurfaceStimulus.KeyChanged -> SurfaceStimulus.KeyChanged(
        surfaceId,
        input.physicalKey,
        input.logicalKey,
        input.location,
        input.keyState,
        input.repeat,
        input.modifiers,
    )
    is AppKitSurfaceStimulus.PointerInput -> when (val input = input) {
        is AppKitInput.PointerEntered -> SurfaceStimulus.PointerEntered(
            surfaceId,
            org.graphiks.kadre.input.PointerKind.Mouse,
            input.position,
        )
        is AppKitInput.PointerMoved -> SurfaceStimulus.PointerMoved(
            surfaceId,
            org.graphiks.kadre.input.PointerKind.Mouse,
            input.position,
            input.delta,
            input.pressure,
            null,
        )
        is AppKitInput.PointerButtonChanged -> SurfaceStimulus.PointerButtonChanged(
            surfaceId,
            org.graphiks.kadre.input.PointerKind.Mouse,
            input.button,
            input.buttonState,
            input.position,
            input.pressure,
            null,
        )
        AppKitInput.PointerLeft -> SurfaceStimulus.PointerLeft(
            surfaceId,
            org.graphiks.kadre.input.PointerKind.Mouse,
        )
        is AppKitInput.KeyChanged -> error("key input must not use a pointer stimulus")
    }
}

private fun AppKitSurfaceSnapshot.toRuntimeSnapshot(): SurfaceInitialSnapshot = SurfaceInitialSnapshot(
    metrics = metrics,
    focus = focus,
    visibility = visibility,
    occlusion = occlusion,
    theme = theme,
)

private fun appKitEffectiveSpec(
    requested: WindowSpec,
    enabledWindowUpdateCapabilities: Set<WindowProperty>,
): WindowSpec = requested.copy(
    minimumSize = requested.minimumSize.takeIf {
        WindowProperty.MinimumSize in enabledWindowUpdateCapabilities
    },
    maximumSize = requested.maximumSize.takeIf {
        WindowProperty.MaximumSize in enabledWindowUpdateCapabilities
    },
    outerPosition = null,
    fullscreen = FullscreenMode.Windowed,
    level = requested.level.takeIf { WindowProperty.Level in enabledWindowUpdateCapabilities }
        ?: WindowLevel.Normal,
    transparent = false,
    blurBehind = false,
    icon = null,
    contentProtection = false,
).let { effective ->
    if (effective.decorations == WindowDecorations.Borderless) {
        effective.copy(systemButtons = WindowSystemButtons.None)
    } else {
        effective
    }
}

private fun WindowUpdateCommand.toMutationTarget(): AppKitWindowMutationTarget = AppKitWindowMutationTarget(
    title = update.title,
    geometry = AppKitWindowGeometryTarget(
        contentSize = update.contentSize,
        minimumSize = update.minimumSize,
        maximumSize = update.maximumSize,
        resizable = update.resizable,
    ),
    chrome = AppKitWindowChromeTarget(
        decorations = update.decorations,
        systemButtons = update.systemButtons,
    ),
    level = AppKitWindowLevelTarget(update.level),
)

private fun WindowUpdateCommand.rejectedMutationFields(
    snapshot: AppKitWindowMutationSnapshot,
    failure: KadreFailure,
): List<RejectedWindowField> = buildList {
    when (val change = update.title) {
        is PropertyChange.Set -> if (snapshot.title != change.value) {
            add(RejectedWindowField(WindowProperty.Title, failure))
        }
        PropertyChange.Clear -> add(RejectedWindowField(WindowProperty.Title, failure))
        PropertyChange.Unchanged -> Unit
    }
    when (val change = update.contentSize) {
        is PropertyChange.Set -> if (snapshot.geometry.contentSize != change.value) {
            add(RejectedWindowField(WindowProperty.ContentSize, failure))
        }
        PropertyChange.Clear -> add(RejectedWindowField(WindowProperty.ContentSize, failure))
        PropertyChange.Unchanged -> Unit
    }
    when (val change = update.minimumSize) {
        is PropertyChange.Set -> if (snapshot.geometry.minimumSize != change.value) {
            add(RejectedWindowField(WindowProperty.MinimumSize, failure))
        }
        PropertyChange.Clear -> if (snapshot.geometry.minimumSize != null) {
            add(RejectedWindowField(WindowProperty.MinimumSize, failure))
        }
        PropertyChange.Unchanged -> Unit
    }
    when (val change = update.maximumSize) {
        is PropertyChange.Set -> if (snapshot.geometry.maximumSize != change.value) {
            add(RejectedWindowField(WindowProperty.MaximumSize, failure))
        }
        PropertyChange.Clear -> if (snapshot.geometry.maximumSize != null) {
            add(RejectedWindowField(WindowProperty.MaximumSize, failure))
        }
        PropertyChange.Unchanged -> Unit
    }
    when (val change = update.resizable) {
        is PropertyChange.Set -> if (snapshot.geometry.resizable != change.value) {
            add(RejectedWindowField(WindowProperty.Resizable, failure))
        }
        PropertyChange.Clear -> add(RejectedWindowField(WindowProperty.Resizable, failure))
        PropertyChange.Unchanged -> Unit
    }
    when (val change = update.decorations) {
        is PropertyChange.Set -> if (snapshot.chrome.decorations != change.value) {
            add(RejectedWindowField(WindowProperty.Decorations, failure))
        }
        PropertyChange.Clear -> add(RejectedWindowField(WindowProperty.Decorations, failure))
        PropertyChange.Unchanged -> Unit
    }
    when (val change = update.systemButtons) {
        is PropertyChange.Set -> if (snapshot.chrome.systemButtons != change.value) {
            add(RejectedWindowField(WindowProperty.SystemButtons, failure))
        }
        PropertyChange.Clear -> add(RejectedWindowField(WindowProperty.SystemButtons, failure))
        PropertyChange.Unchanged -> Unit
    }
    when (val change = update.level) {
        is PropertyChange.Set -> if (snapshot.level != change.value) {
            add(RejectedWindowField(WindowProperty.Level, failure))
        }
        PropertyChange.Clear -> add(RejectedWindowField(WindowProperty.Level, failure))
        PropertyChange.Unchanged -> Unit
    }
}

private fun AppKitWindowMutationSnapshot.withMutationFrom(
    current: WindowState,
): WindowState = current.copy(
    title = title,
    contentSize = geometry.contentSize,
    minimumSize = geometry.minimumSize,
    maximumSize = geometry.maximumSize,
    resizable = geometry.resizable,
    decorations = chrome.decorations,
    systemButtons = chrome.systemButtons,
    level = level,
    revision = current.revision,
)

private fun AppKitWindowGeometrySnapshot.withGeometryFrom(
    current: WindowState,
): WindowState = current.copy(
    contentSize = contentSize,
    minimumSize = minimumSize,
    maximumSize = maximumSize,
    resizable = resizable,
    revision = current.revision,
)

internal class AppKitWindowCommandQueue(
    private val reportFailure: (Throwable) -> Unit,
) {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val lock = Object()
    private val tasks = ArrayDeque<() -> Unit>()
    private val terminated = CountDownLatch(1)
    private var accepting = true
    private var draining = false
    private var inlineDrain = false
    private var workerRunningTask = false
    private var stopped = false
    private val worker = Thread.ofPlatform()
        .daemon()
        .name("kadre-appkit-window-driver")
        .unstarted(::run)

    init {
        worker.start()
    }

    fun submit(task: () -> Unit): Boolean = enqueue(task, requireAccepting = true)

    fun submitFollowUp(task: () -> Unit): Boolean = enqueue(task, requireAccepting = false)

    fun beginMainThreadDrain(): Boolean = synchronized(lock) {
        accepting = false
        if (workerRunningTask) {
            lock.notifyAll()
            false
        } else {
            inlineDrain = true
            lock.notifyAll()
            true
        }
    }

    fun finishAsynchronousDrain() {
        synchronized(lock) {
            check(!inlineDrain) { "inline drain cannot be sealed asynchronously" }
            draining = true
            lock.notifyAll()
        }
    }

    fun drainInline() {
        check(Thread.currentThread() !== worker) { "worker cannot inline-drain itself" }
        while (true) {
            val task = synchronized(lock) {
                check(inlineDrain) { "inline drain was not reserved" }
                tasks.removeFirstOrNull()?.also { return@synchronized it } ?: run {
                    inlineDrain = false
                    stopped = true
                    lock.notifyAll()
                    return
                }
            }
            runTask(task)
        }
    }

    fun closeAndDrain() {
        synchronized(lock) {
            accepting = false
            draining = true
            lock.notifyAll()
        }
        if (Thread.currentThread() === worker) return
        var interrupted = false
        while (true) {
            try {
                terminated.await()
                break
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }

    private fun enqueue(task: () -> Unit, requireAccepting: Boolean): Boolean = synchronized(lock) {
        if (stopped || (requireAccepting && !accepting)) return@synchronized false
        tasks.addLast(task)
        lock.notifyAll()
        true
    }

    private fun run() {
        try {
            while (true) {
                val task = synchronized(lock) {
                    while (!stopped && (inlineDrain || (tasks.isEmpty() && !draining))) lock.wait()
                    if (stopped) return
                    if (tasks.isEmpty()) {
                        stopped = true
                        return
                    }
                    workerRunningTask = true
                    tasks.removeFirst()
                }
                runTask(task)
                synchronized(lock) {
                    workerRunningTask = false
                    lock.notifyAll()
                }
            }
        } finally {
            synchronized(lock) {
                stopped = true
                lock.notifyAll()
            }
            terminated.countDown()
        }
    }

    private fun runTask(task: () -> Unit) {
        try {
            task()
        } catch (cause: Throwable) {
            reportFailure(cause)
        }
    }
}
