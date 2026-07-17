package org.graphiks.kadre.android

import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal const val MAIN_HANDOFF_TIMEOUT_MILLIS = 5_000L

/**
 * Linearizes one retryable close operation across all callers.
 *
 * Concurrent callers wait for the owner to complete. A failure resets the operation so a
 * handoff rejected or cancelled before start can be retried. Reentrant calls from the owner
 * join the in-flight operation without waiting on themselves.
 */
internal class LinearizedCloseOperation {
    private val lock = ReentrantLock()
    private val completion = lock.newCondition()
    private var phase = Phase.Idle
    private var owner: Thread? = null

    fun run(action: () -> Unit) {
        val caller = Thread.currentThread()
        lock.withLock {
            while (phase == Phase.Running) {
                if (owner === caller) return
                completion.awaitUninterruptibly()
            }
            if (phase == Phase.Completed) return
            phase = Phase.Running
            owner = caller
        }

        var completed = false
        try {
            action()
            completed = true
        } finally {
            lock.withLock {
                phase = if (completed) Phase.Completed else Phase.Idle
                owner = null
                completion.signalAll()
            }
        }
    }

    private enum class Phase {
        Idle,
        Running,
        Completed,
    }
}

internal fun <T> boundedMainHandoff(
    timeoutMillis: Long,
    post: (Runnable) -> Boolean,
    action: () -> T,
): T {
    val task = StartAwareFutureTask(action)
    if (!post(task)) {
        task.cancel(false)
        throw IllegalStateException("Main-thread handoff was rejected by Handler")
    }

    return try {
        task.get(timeoutMillis, TimeUnit.MILLISECONDS)
    } catch (failure: TimeoutException) {
        if (task.cancel(false)) {
            throw IllegalStateException(
                "Main-thread handoff timed out after $timeoutMillis ms",
                failure,
            )
        }
        awaitTerminalOutcome(task)
    } catch (failure: InterruptedException) {
        if (task.cancel(false)) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Main-thread handoff was interrupted", failure)
        }
        awaitTerminalOutcome(task, restoreInterrupt = true)
    } catch (failure: ExecutionException) {
        rethrowExecutionFailure(failure)
    }
}

private fun <T> awaitTerminalOutcome(
    task: FutureTask<T>,
    restoreInterrupt: Boolean = false,
): T {
    var interrupted = restoreInterrupt
    try {
        while (true) {
            try {
                return task.get()
            } catch (_: InterruptedException) {
                interrupted = true
            } catch (failure: ExecutionException) {
                rethrowExecutionFailure(failure)
            }
        }
    } finally {
        if (interrupted) Thread.currentThread().interrupt()
    }
}

private fun rethrowExecutionFailure(failure: ExecutionException): Nothing {
    when (val cause = failure.cause ?: failure) {
        is RuntimeException -> throw cause
        is Error -> throw cause
        else -> throw IllegalStateException("Main-thread handoff action failed", cause)
    }
}

private class StartAwareFutureTask<T>(action: () -> T) : FutureTask<T>({ action() }) {
    private val state = MainHandoffTaskState()

    override fun run() {
        if (!state.tryStart()) return
        super.run()
    }

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        if (!state.tryCancelBeforeStart()) return false
        return super.cancel(mayInterruptIfRunning)
    }
}

internal class MainHandoffTaskState {
    private val phase = AtomicReference(Phase.Pending)

    fun tryStart(): Boolean = phase.compareAndSet(Phase.Pending, Phase.Running)

    fun tryCancelBeforeStart(): Boolean = phase.compareAndSet(Phase.Pending, Phase.Cancelled)

    private enum class Phase {
        Pending,
        Running,
        Cancelled,
    }
}
