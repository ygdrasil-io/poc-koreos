package org.graphiks.kadre.samples.desktour.appkit

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Uses the host's existing scheduler; it creates no thread or event loop. */
internal class OwnerThreadExecutor(
    private val isOwnerThread: () -> Boolean,
    private val enqueue: (Runnable) -> Unit,
    // Must return after the scheduler's callback frame unwinds, not merely Runnable.run().
    private val invokeAndWait: (Runnable) -> Unit,
) : CoroutineDispatcher() {
    private val admissions = Any()
    private val shutdownLock = Mutex()
    private var accepting = true
    private var shutdownResult: Result<Unit>? = null

    // Cancellation finalizers initiated by owner-thread cleanup must still complete inline.
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !isOwnerThread()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val accepted = synchronized(admissions) {
            if (!accepting) false else {
                // enqueue is non-blocking. Admission ends only after its native post is made.
                enqueue(block)
                true
            }
        }
        // Owned work is joined before admission closes. Remaining detached scene flushes
        // cannot resume on a foreign fallback or post a selector after native teardown.
        if (!accepted) context[Job]?.cancel(CancellationException("Compose mount executor closed"))
    }

    /** Joins cancelled owned work before closing admission and waiting for native return. */
    suspend fun shutdown(work: Job, cleanup: () -> Unit) {
        val result = withContext(NonCancellable) {
            check(!isOwnerThread()) { "Cannot shut down the owner queue from its own thread" }
            shutdownLock.withLock {
                shutdownResult?.let { return@withLock it }
                // Neither admission nor any lock used by dispatch is held across a native wait.
                val outcome = runCatching {
                    var primary: Throwable? = null
                    try { call(cleanup) } catch (failure: Throwable) { primary = failure }
                    // Keep dispatch open: a foreign resume may already own a cancelled child's
                    // continuation. Dropping it would strand its finally and the parent Job.
                    try { work.join() } catch (failure: Throwable) {
                        if (primary == null) primary = failure else if (primary !== failure) primary.addSuppressed(failure)
                    }
                    synchronized(admissions) { accepting = false }
                    try { awaitQueued() } catch (failure: Throwable) {
                        if (primary == null) primary = failure else if (primary !== failure) primary.addSuppressed(failure)
                    }
                    primary?.let { throw it }
                    Unit
                }
                shutdownResult = outcome
                outcome
            }
        }
        // Carry the original Throwable as a value across the coroutine context boundary.
        result.getOrThrow()
    }

    /** FIFO fence for work already enqueued, including callback frames, not future producers. */
    fun awaitQueued() {
        check(!isOwnerThread()) { "Cannot await the owner queue from its own thread" }
        call { Unit }
    }

    /** Returns only after the owner operation AND its enclosing scheduler callback complete. */
    fun <T> call(action: () -> T): T {
        if (isOwnerThread()) return action()
        val task = FutureTask(Callable(action))
        invokeAndWait(task)
        var interrupted = false
        try {
            while (true) {
                try {
                    return task.get()
                } catch (_: InterruptedException) {
                    // Once admitted, do not let teardown return before native work completes.
                    interrupted = true
                } catch (failure: ExecutionException) {
                    throw checkNotNull(failure.cause)
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }
}
