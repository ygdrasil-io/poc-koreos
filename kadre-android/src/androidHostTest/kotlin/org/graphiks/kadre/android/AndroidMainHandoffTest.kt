package org.graphiks.kadre.android

import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AndroidMainHandoffTest {
    @Test
    fun concurrentCloseCallersWaitForOneSharedCompletion() {
        val operation = LinearizedCloseOperation()
        val actionStarted = CountDownLatch(1)
        val releaseAction = CountDownLatch(1)
        val secondReturned = CountDownLatch(1)
        val actionCalls = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit {
                operation.run {
                    actionCalls.incrementAndGet()
                    actionStarted.countDown()
                    check(releaseAction.await(5L, TimeUnit.SECONDS))
                }
            }
            assertTrue(actionStarted.await(5L, TimeUnit.SECONDS))
            val second = executor.submit {
                operation.run {
                    actionCalls.incrementAndGet()
                }
                secondReturned.countDown()
            }

            assertFalse(
                secondReturned.await(250L, TimeUnit.MILLISECONDS),
                "a concurrent close returned before the shared operation completed",
            )
            releaseAction.countDown()
            first.get(5L, TimeUnit.SECONDS)
            second.get(5L, TimeUnit.SECONDS)
            assertEquals(1, actionCalls.get())
        } finally {
            releaseAction.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun rejectedCloseHandoffCanBeRetried() {
        val operation = LinearizedCloseOperation()
        val attempts = AtomicInteger(0)

        assertFailsWith<IllegalStateException> {
            operation.run {
                attempts.incrementAndGet()
                boundedMainHandoff(
                    timeoutMillis = 50L,
                    post = { false },
                ) {
                    error("rejected handoff must not run")
                }
            }
        }
        operation.run {
            attempts.incrementAndGet()
        }

        assertEquals(2, attempts.get())
    }

    @Test
    fun cancelledBeforeStartCloseHandoffCanBeRetried() {
        val operation = LinearizedCloseOperation()
        val attempts = AtomicInteger(0)
        var postedTask: Runnable? = null

        assertFailsWith<IllegalStateException> {
            operation.run {
                attempts.incrementAndGet()
                boundedMainHandoff(
                    timeoutMillis = 10L,
                    post = { task ->
                        postedTask = task
                        true
                    },
                ) {
                    error("cancelled handoff must not run")
                }
            }
        }
        assertTrue(assertIs<Future<*>>(postedTask).isCancelled)
        operation.run {
            attempts.incrementAndGet()
        }

        assertEquals(2, attempts.get())
    }

    @Test
    fun handoffStartAndCancellationTransitionsAreMutuallyExclusive() {
        val startFirst = MainHandoffTaskState()
        assertTrue(startFirst.tryStart())
        assertFalse(startFirst.tryCancelBeforeStart())

        val cancelFirst = MainHandoffTaskState()
        assertTrue(cancelFirst.tryCancelBeforeStart())
        assertFalse(cancelFirst.tryStart())

        val executor = Executors.newFixedThreadPool(2)
        try {
            repeat(100) {
                val state = MainHandoffTaskState()
                val ready = CountDownLatch(2)
                val start = CountDownLatch(1)
                val startWon = AtomicBoolean(false)
                val cancelWon = AtomicBoolean(false)
                val startFuture = executor.submit {
                    ready.countDown()
                    start.await()
                    startWon.set(state.tryStart())
                }
                val cancelFuture = executor.submit {
                    ready.countDown()
                    start.await()
                    cancelWon.set(state.tryCancelBeforeStart())
                }

                assertTrue(ready.await(5L, TimeUnit.SECONDS))
                start.countDown()
                startFuture.get(5L, TimeUnit.SECONDS)
                cancelFuture.get(5L, TimeUnit.SECONDS)
                assertTrue(startWon.get() != cancelWon.get())
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun timedOutRunningHandoffWaitsForFinalResult() {
        val actionStarted = CountDownLatch(1)
        val releaseAction = CountDownLatch(1)
        val allowPostReturn = Semaphore(0)
        val callerReturned = CountDownLatch(1)
        val outcome = CompletableFuture<Int>()
        val caller = Thread {
            try {
                outcome.complete(
                    boundedMainHandoff(
                        timeoutMillis = 0L,
                        post = { task ->
                            Thread(task, "running-timeout-action").start()
                            check(actionStarted.await(5L, TimeUnit.SECONDS))
                            allowPostReturn.acquireUninterruptibly()
                            true
                        },
                    ) {
                        actionStarted.countDown()
                        check(releaseAction.await(5L, TimeUnit.SECONDS))
                        42
                    },
                )
            } catch (failure: Throwable) {
                outcome.completeExceptionally(failure)
            } finally {
                callerReturned.countDown()
            }
        }

        caller.start()
        try {
            assertTrue(actionStarted.await(5L, TimeUnit.SECONDS))
            allowPostReturn.release()
            assertFalse(
                callerReturned.await(250L, TimeUnit.MILLISECONDS),
                "running action was reported as timed out before its terminal result",
            )

            releaseAction.countDown()
            assertEquals(42, outcome.get(5L, TimeUnit.SECONDS))
            assertTrue(callerReturned.await(5L, TimeUnit.SECONDS))
        } finally {
            allowPostReturn.release()
            releaseAction.countDown()
            caller.join(5_000L)
        }
    }

    @Test
    fun interruptedRunningHandoffWaitsForFinalResultAndRestoresInterrupt() {
        val actionStarted = CountDownLatch(1)
        val callerReadyForInterrupt = CountDownLatch(1)
        val releaseAction = CountDownLatch(1)
        val allowPostReturn = Semaphore(0)
        val callerReturned = CountDownLatch(1)
        val interruptRestored = AtomicBoolean(false)
        val outcome = CompletableFuture<Int>()
        val caller = Thread {
            try {
                val result = boundedMainHandoff(
                    timeoutMillis = 5_000L,
                    post = { task ->
                        Thread(task, "running-interrupted-action").start()
                        check(actionStarted.await(5L, TimeUnit.SECONDS))
                        callerReadyForInterrupt.countDown()
                        allowPostReturn.acquireUninterruptibly()
                        true
                    },
                ) {
                    actionStarted.countDown()
                    check(releaseAction.await(5L, TimeUnit.SECONDS))
                    42
                }
                interruptRestored.set(Thread.currentThread().isInterrupted)
                outcome.complete(result)
            } catch (failure: Throwable) {
                outcome.completeExceptionally(failure)
            } finally {
                callerReturned.countDown()
            }
        }

        caller.start()
        try {
            assertTrue(callerReadyForInterrupt.await(5L, TimeUnit.SECONDS))
            caller.interrupt()
            allowPostReturn.release()
            assertFalse(
                callerReturned.await(250L, TimeUnit.MILLISECONDS),
                "running action was reported as interrupted before its terminal result",
            )

            releaseAction.countDown()
            assertEquals(42, outcome.get(5L, TimeUnit.SECONDS))
            assertTrue(interruptRestored.get())
            assertTrue(callerReturned.await(5L, TimeUnit.SECONDS))
        } finally {
            allowPostReturn.release()
            releaseAction.countDown()
            caller.join(5_000L)
        }
    }

    @Test
    fun acceptedHandoffReturnsActionResult() {
        val result = boundedMainHandoff(
            timeoutMillis = 50L,
            post = { task ->
                task.run()
                true
            },
        ) {
            42
        }

        assertEquals(42, result)
    }

    @Test
    fun rejectedHandoffFailsWithoutRunningAction() {
        var actionRan = false

        val failure = assertFailsWith<IllegalStateException> {
            boundedMainHandoff(
                timeoutMillis = 50L,
                post = { false },
            ) {
                actionRan = true
            }
        }

        assertFalse(actionRan)
        assertTrue(failure.message.orEmpty().contains("rejected", ignoreCase = true))
    }

    @Test
    fun timedOutHandoffCancelsTaskAndReportsTimeout() {
        var postedTask: Runnable? = null

        val failure = assertFailsWith<IllegalStateException> {
            boundedMainHandoff(
                timeoutMillis = 10L,
                post = { task ->
                    postedTask = task
                    true
                },
            ) {
                42
            }
        }

        assertIs<TimeoutException>(failure.cause)
        assertTrue(failure.message.orEmpty().contains("10 ms"))
        assertTrue(assertIs<Future<*>>(postedTask).isCancelled)
    }

    @Test
    fun interruptedHandoffRestoresInterruptAndReportsFailure() {
        try {
            Thread.currentThread().interrupt()

            val failure = assertFailsWith<IllegalStateException> {
                boundedMainHandoff(
                    timeoutMillis = 50L,
                    post = { true },
                ) {
                    42
                }
            }

            assertIs<InterruptedException>(failure.cause)
            assertTrue(Thread.currentThread().isInterrupted)
            assertTrue(failure.message.orEmpty().contains("interrupted", ignoreCase = true))
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun runtimeExceptionFromActionIsRethrownUnchanged() {
        val expected = IllegalArgumentException("boom")

        val actual = assertFailsWith<IllegalArgumentException> {
            boundedMainHandoff(
                timeoutMillis = 50L,
                post = { task ->
                    task.run()
                    true
                },
            ) {
                throw expected
            }
        }

        assertSame(expected, actual)
    }

    @Test
    fun errorFromActionIsRethrownUnchanged() {
        val expected = AssertionError("boom")

        val actual = assertFailsWith<AssertionError> {
            boundedMainHandoff(
                timeoutMillis = 50L,
                post = { task ->
                    task.run()
                    true
                },
            ) {
                throw expected
            }
        }

        assertSame(expected, actual)
    }

    @Test
    fun checkedFailureFromActionIsWrappedWithItsCause() {
        val expected = IOException("boom")

        val actual = assertFailsWith<IllegalStateException> {
            boundedMainHandoff(
                timeoutMillis = 50L,
                post = { task ->
                    task.run()
                    true
                },
            ) {
                throw expected
            }
        }

        assertSame(expected, actual.cause)
        assertTrue(actual.message.orEmpty().contains("failed", ignoreCase = true))
    }
}
