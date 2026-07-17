package org.graphiks.kadre.android

import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal const val MAIN_HANDOFF_TIMEOUT_MILLIS = 5_000L

internal fun <T> boundedMainHandoff(
    timeoutMillis: Long,
    post: (Runnable) -> Boolean,
    action: () -> T,
): T {
    val task = FutureTask<T> { action() }
    if (!post(task)) {
        task.cancel(false)
        throw IllegalStateException("Main-thread handoff was rejected by Handler")
    }

    return try {
        task.get(timeoutMillis, TimeUnit.MILLISECONDS)
    } catch (failure: TimeoutException) {
        task.cancel(false)
        throw IllegalStateException(
            "Main-thread handoff timed out after $timeoutMillis ms",
            failure,
        )
    } catch (failure: InterruptedException) {
        task.cancel(false)
        Thread.currentThread().interrupt()
        throw IllegalStateException("Main-thread handoff was interrupted", failure)
    } catch (failure: ExecutionException) {
        when (val cause = failure.cause ?: failure) {
            is RuntimeException -> throw cause
            is Error -> throw cause
            else -> throw IllegalStateException("Main-thread handoff action failed", cause)
        }
    }
}
