package org.graphiks.kadre.internal.appkit

import kotlinx.coroutines.suspendCancellableCoroutine
import org.graphiks.kadre.diagnostics.KadreFailure
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
import org.graphiks.kadre.internal.runtime.SurfaceRedrawCommand
import org.graphiks.kadre.internal.runtime.SurfaceRedrawGeneration
import org.graphiks.kadre.internal.runtime.SurfaceStimulus
import org.graphiks.kadre.internal.runtime.SurfaceUpdateCommand
import org.graphiks.kadre.internal.runtime.SurfaceUpdateCommandOutcome
import org.graphiks.kadre.internal.runtime.WindowCommandPort
import org.graphiks.kadre.internal.runtime.WindowOpenCommand
import org.graphiks.kadre.internal.runtime.WindowPeerOwner
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.window.FullscreenMode
import org.graphiks.kadre.window.WindowLevel
import org.graphiks.kadre.window.WindowRequestId
import org.graphiks.kadre.window.WindowSpec
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
    publicSurfaceCapabilities: Boolean,
    onLastWindowClosed: (() -> Unit)?,
    beforeCommitDelivery: (WindowSpec) -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val commandPort = AppKitWindowCommandPort(
        nativePort,
        failureReporter,
        beforeCommitDelivery,
        surfaceStimulusSink = { stimulus -> manager.acceptSurfaceStimulus(stimulus) },
    )

    internal val manager: RuntimeWindowManager = RuntimeWindowManager(
        resources = resources,
        commandPort = commandPort,
        surfaceCommandPort = commandPort,
        platform = KadrePlatform.AppKit,
        failureReporter = failureReporter,
        publicWindowCapabilities = publicAppKitCapabilities,
        publicSurfaceCapabilities = publicSurfaceCapabilities,
        onLastWindowClosed = onLastWindowClosed,
    )

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
    private val surfaceStimulusSink: (SurfaceStimulus) -> Boolean,
) : WindowCommandPort, SurfaceCommandPort {
    private val lock = Any()
    private val nextPeerId = AtomicLong(0L)
    private val byRequest = linkedMapOf<WindowRequestId, PeerEntry>()
    private val byPeer = linkedMapOf<AppKitWindowPeerId, PeerEntry>()
    private val bySurface = linkedMapOf<SurfaceId, PeerEntry>()
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

        val peer = try {
            AppKitWindowPeer.prepare(
                id = entry.peerId,
                spec = entry.command.spec,
                port = nativePort,
                acceptStimulus = ::enqueueStimulus,
                acceptSurfaceStimulus = ::enqueueSurfaceStimulus,
                reportCallbackFailure = ::reportFailure,
            )
        } catch (cause: Exception) {
            rejectPreparation(entry, cause)
            return
        } catch (cause: LinkageError) {
            rejectPreparation(entry, cause)
            return
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
                beforeCommitDelivery(entry.command.spec)
                entry.command.commit(
                    entry.owner,
                    appKitEffectiveSpec(entry.command.spec),
                    peer.initialSurfaceSnapshot?.metrics,
                )
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
        synchronized(lock) { removeEntryLocked(entry) }
    }

    private fun enqueueStimulus(stimulus: AppKitWindowStimulus) {
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

                else -> {
                    entry.closeRequestPending = false
                    entry.closeAdmitted = true
                    true
                }
            }
        }
        if (accepted) commands.submitFollowUp { acceptStimulus(stimulus) }
    }

    private fun enqueueSurfaceStimulus(stimulus: AppKitSurfaceStimulus) {
        val accepted = synchronized(lock) {
            val entry = byPeer[stimulus.peerId]
            entry != null &&
                !closed &&
                !entry.removed &&
                !entry.surfaceCleanupReserved &&
                !entry.closeAdmitted &&
                entry.commitIssued
        }
        if (accepted) commands.submitFollowUp { acceptSurfaceStimulus(stimulus) }
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
        synchronized(lock) { removeEntryLocked(entry) }
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

    private fun removeEntryLocked(entry: PeerEntry) {
        entry.removed = true
        if (byRequest[entry.command.requestId] === entry) byRequest.remove(entry.command.requestId)
        if (byPeer[entry.peerId] === entry) byPeer.remove(entry.peerId)
        if (bySurface[entry.surfaceId] === entry) bySurface.remove(entry.surfaceId)
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

private fun AppKitSurfaceStimulus.toRuntime(surfaceId: SurfaceId): SurfaceStimulus = when (this) {
    is AppKitSurfaceStimulus.MetricsChanged -> SurfaceStimulus.MetricsChanged(surfaceId, metrics)
    is AppKitSurfaceStimulus.FocusChanged -> SurfaceStimulus.FocusChanged(surfaceId, focus)
    is AppKitSurfaceStimulus.VisibilityChanged -> SurfaceStimulus.VisibilityChanged(surfaceId, visibility, occlusion)
    is AppKitSurfaceStimulus.ThemeChanged -> SurfaceStimulus.ThemeChanged(surfaceId, theme)
    is AppKitSurfaceStimulus.RedrawConsumed -> SurfaceStimulus.RedrawConsumed(
        surfaceId,
        SurfaceRedrawGeneration.fromNative(generation),
    )
}

private fun appKitEffectiveSpec(requested: WindowSpec): WindowSpec = requested.copy(
    minimumSize = null,
    maximumSize = null,
    outerPosition = null,
    fullscreen = FullscreenMode.Windowed,
    level = WindowLevel.Normal,
    transparent = false,
    blurBehind = false,
    icon = null,
    contentProtection = false,
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
