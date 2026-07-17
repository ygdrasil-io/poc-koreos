package org.graphiks.kadre.android

import java.io.IOException
import java.util.concurrent.Future
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AndroidMainHandoffTest {
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
