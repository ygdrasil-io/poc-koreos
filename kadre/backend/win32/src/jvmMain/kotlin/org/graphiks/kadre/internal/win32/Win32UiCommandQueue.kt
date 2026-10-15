package org.graphiks.kadre.internal.win32

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.CancellationException

/** Bounded worker-to-UI admission. Native waits never hold the queue's lock. */
internal class Win32UiCommandQueue(
    private val isUiThread: () -> Boolean,
    private val wake: () -> Boolean,
    private val capacity: Int = 256,
) : AutoCloseable {
    private class Command(val run: () -> Unit, val fail: (Throwable) -> Unit)
    private val lock = Any()
    private val commands = ArrayDeque<Command>()
    private var closed = false
    private var workerAdmissionStopped = false

    init { require(capacity > 0) }

    fun post(block: () -> Unit, onFailure: (Throwable) -> Unit) {
        if (isUiThread()) {
            synchronized(lock) { check(!closed) { "Win32 UI command queue is closed" } }
            try { block() } catch (failure: Throwable) { onFailure(failure) }
        } else enqueue(Command({ try { block() } catch (failure: Throwable) { onFailure(failure) } }, onFailure))
    }

    /** Always enqueue: mutation admission must return before native callbacks enter the runtime. */
    fun defer(block: () -> Unit, onFailure: (Throwable) -> Unit) {
        enqueue(Command({ try { block() } catch (failure: Throwable) { onFailure(failure) } }, onFailure))
    }

    fun <T> invoke(block: () -> T): T {
        if (isUiThread()) {
            synchronized(lock) { check(!closed) { "Win32 UI command queue is closed" } }
            return block()
        }
        val result = CompletableFuture<T>()
        val command = Command(
            run = { try { result.complete(block()) } catch (failure: Throwable) { result.completeExceptionally(failure) } },
            fail = { result.completeExceptionally(it) },
        )
        enqueue(command)
        return try {
            result.get()
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        } catch (failure: InterruptedException) {
            if (synchronized(lock) { commands.remove(command) }) command.fail(failure)
            Thread.currentThread().interrupt()
            throw failure
        }
    }

    private fun enqueue(command: Command) {
        synchronized(lock) {
            check(!closed) { "Win32 UI command queue is closed" }
            if (workerAdmissionStopped) throw CancellationException("Win32 UI owner is stopping")
            if (commands.size >= capacity) throw RejectedExecutionException("Win32 UI command queue is full")
            commands.addLast(command)
        }
        val wakeFailure = try {
            if (wake()) null else IllegalStateException("Win32 UI command endpoint is not ready")
        } catch (failure: Throwable) { failure }
        if (wakeFailure != null && synchronized(lock) { commands.remove(command) }) command.fail(wakeFailure)
    }

    /** Stops worker admission before the owner awaits runtime termination; UI cleanup stays legal. */
    fun stopWorkerAdmission() {
        val abandoned = synchronized(lock) {
            if (workerAdmissionStopped) return
            workerAdmissionStopped = true
            commands.toList().also { commands.clear() }
        }
        val reason = CancellationException("Win32 UI owner is stopping")
        abandoned.forEach { runCatching { it.fail(reason) } }
    }

    fun drain() {
        check(isUiThread()) { "Win32 UI commands must drain on their owner thread" }
        // A finite batch lets native input progress even under a sustained producer.
        val batch = synchronized(lock) { commands.size }
        repeat(batch) {
            val command = synchronized(lock) { commands.removeFirstOrNull() } ?: return
            command.run()
        }
    }

    override fun close() {
        val abandoned = synchronized(lock) {
            if (closed) return
            closed = true
            workerAdmissionStopped = true
            commands.toList().also { commands.clear() }
        }
        abandoned.forEach { runCatching { it.fail(IllegalStateException("Win32 UI command queue is closed")) } }
    }
}
