package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.internal.runtime.OpenedWindowCloseCommand
import org.graphiks.kadre.internal.runtime.OpenedWindowCloseOutcome
import org.graphiks.kadre.internal.runtime.PendingWindowCancellationCommand
import org.graphiks.kadre.internal.runtime.PendingWindowCancellationOutcome
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeWindowManager
import org.graphiks.kadre.internal.runtime.WindowCommandPort
import org.graphiks.kadre.internal.runtime.WindowOpenCommand
import org.graphiks.kadre.internal.runtime.WindowPeerOwner
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.window.WindowRequestId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Private AppKit composition root for one deterministic window-runtime session. */
internal class AppKitWindowRuntimeDriver internal constructor(
    resources: ResourceBudgetPolicy,
    nativePort: AppKitNativeWindowPort,
    failureReporter: RuntimeFailureReporter,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val commandPort = AppKitWindowCommandPort(nativePort, failureReporter)

    internal val manager: RuntimeWindowManager = RuntimeWindowManager(
        resources = resources,
        commandPort = commandPort,
        platform = KadrePlatform.AppKit,
        failureReporter = failureReporter,
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
) : WindowCommandPort {
    private val lock = Any()
    private val nextPeerId = AtomicLong(0L)
    private val byRequest = linkedMapOf<WindowRequestId, PeerEntry>()
    private val byPeer = linkedMapOf<AppKitWindowPeerId, PeerEntry>()
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
        val entry = synchronized(lock) {
            val entry = byRequest[command.requestId]
                ?: return PendingWindowCancellationOutcome.TooLate
            if (entry.commitIssued || entry.nativeTerminalIssued) {
                return PendingWindowCancellationOutcome.TooLate
            }
            entry.cancellationRequested = true
            entry.cleanupCompletion = CleanupCompletion.PendingCancellation
            entry
        }
        scheduleCleanup(entry)
        return PendingWindowCancellationOutcome.CancellationRequested
    }

    override fun requestOpenedClose(command: OpenedWindowCloseCommand): OpenedWindowCloseOutcome {
        val entry = synchronized(lock) {
            byRequest[command.requestId]?.also {
                it.cleanupCompletion = CleanupCompletion.ProgrammaticClose
            }
        } ?: return OpenedWindowCloseOutcome.PlatformFailure(platformFailure("missing-peer"))
        scheduleCleanup(entry)
        return OpenedWindowCloseOutcome.Accepted
    }

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
            PreparationAction.Commit -> entry.command.commit(entry.owner)
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
            !closed && byPeer.containsKey(stimulus.peerId)
        }
        if (accepted) commands.submitFollowUp { acceptStimulus(stimulus) }
    }

    private fun acceptStimulus(stimulus: AppKitWindowStimulus) {
        val entry = synchronized(lock) {
            byPeer[stimulus.peerId]?.takeUnless(PeerEntry::removed)
        } ?: return
        when (stimulus) {
            is AppKitWindowStimulus.CloseRequested -> Unit
            is AppKitWindowStimulus.NativeClosed -> {
                issueNativeTerminal(entry)
                scheduleCleanup(entry)
            }
        }
    }

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

    private inner class PeerEntry(
        val command: WindowOpenCommand,
        val peerId: AppKitWindowPeerId,
    ) {
        var peer: AppKitWindowPeer? = null
        var cancellationRequested: Boolean = false
        var commitIssued: Boolean = false
        var cleanupScheduled: Boolean = false
        var cleanupFinished: Boolean = false
        var cleanupCompletion: CleanupCompletion = CleanupCompletion.None
        var nativeTerminalIssued: Boolean = false
        var removed: Boolean = false
        val owner: WindowPeerOwner = WindowPeerOwner { scheduleCleanup(this) }
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

    enum class CloseDrainMode {
        Inline,
        Asynchronous,
        Blocking,
    }

    private companion object {
        val CANCELLATION_COMPLETION: KadreFailure = KadreFailure.TemporarilyUnavailable(retryable = false)
    }
}

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
