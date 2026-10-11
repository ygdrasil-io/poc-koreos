package org.graphiks.kadre.samples.desktour.appkit

import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class OwnerThreadExecutorTest {
    @Test
    fun `barrier waits for async child enqueued during synchronous close to unwind`() {
        val owner = Executors.newSingleThreadExecutor()
        val caller = Executors.newSingleThreadExecutor()
        val childBodyFinished = CountDownLatch(1)
        val closeReturned = CountDownLatch(1)
        val allowChildUnwind = CountDownLatch(1)
        val childUnwound = AtomicBoolean()
        try {
            val executor = OwnerThreadExecutor(
                isOwnerThread = { false },
                enqueue = { task -> owner.execute {
                    task.run()
                    childBodyFinished.countDown()
                    allowChildUnwind.await()
                    childUnwound.set(true)
                } },
                invokeAndWait = { task -> owner.submit(task).get() },
            )
            val result = caller.submit<Unit> {
                executor.call { executor.dispatch(EmptyCoroutineContext, Runnable {}) }
                closeReturned.countDown()
                executor.awaitQueued()
                assertTrue(childUnwound.get())
            }
            check(closeReturned.await(5, TimeUnit.SECONDS))
            check(childBodyFinished.await(5, TimeUnit.SECONDS))
            assertFailsWith<TimeoutException> { result.get(100, TimeUnit.MILLISECONDS) }
            allowChildUnwind.countDown()
            result.get(5, TimeUnit.SECONDS)
        } finally {
            allowChildUnwind.countDown()
            caller.shutdownNow()
            owner.shutdownNow()
        }
    }

    @Test
    fun `call waits for the scheduler wrapper to unwind after the task completes`() {
        val owner = Executors.newSingleThreadExecutor()
        val caller = Executors.newSingleThreadExecutor()
        val bodyFinished = CountDownLatch(1)
        val allowUnwind = CountDownLatch(1)
        try {
            val executor = OwnerThreadExecutor(
                isOwnerThread = { false },
                enqueue = { task -> owner.execute {
                    task.run()
                    bodyFinished.countDown()
                    allowUnwind.await()
                } },
                invokeAndWait = { task -> owner.submit {
                    task.run()
                    bodyFinished.countDown()
                    allowUnwind.await()
                }.get() },
            )
            val result = caller.submit<Int> { executor.call { 42 } }
            check(bodyFinished.await(5, TimeUnit.SECONDS))
            // The action is complete, but the scheduler still owns its callback frame.
            assertFailsWith<TimeoutException> { result.get(100, TimeUnit.MILLISECONDS) }
            allowUnwind.countDown()
            assertEquals(42, result.get(5, TimeUnit.SECONDS))
        } finally {
            allowUnwind.countDown()
            caller.shutdownNow()
            owner.shutdownNow()
        }
    }

    @Test
    fun `call runs on owner and returns its value`() = withOwner { executor, owner ->
        val value = executor.call { Thread.currentThread() to 42 }
        assertSame(owner.get(), value.first)
        assertEquals(42, value.second)
    }

    @Test
    fun `nested owner calls complete inline`() = withOwner { executor, owner ->
        val caller = Executors.newSingleThreadExecutor { Thread(it).apply { isDaemon = true } }
        try {
            val result = caller.submit<Thread> {
                executor.call { executor.call { Thread.currentThread() } }
            }
            val executingThread = result.get(5, TimeUnit.SECONDS)
            assertSame(owner.get(), executingThread)
        } finally {
            caller.shutdownNow()
        }
    }

    @Test
    fun `call propagates the original failure`() = withOwner { executor, _ ->
        val failure = IllegalStateException("native operation failed")
        val thrown = assertFailsWith<IllegalStateException> { executor.call { throw failure } }
        assertSame(failure, thrown)
    }

    @Test
    fun `coroutine resumes on owner after suspension`() = withOwner { executor, owner ->
        runBlocking {
            withContext(executor) {
                assertSame(owner.get(), Thread.currentThread())
                delay(1)
                assertSame(owner.get(), Thread.currentThread())
            }
        }
    }

    @Test
    fun `concurrent callers are serialized and finish before returning`() = withOwner { executor, _ ->
        val callers = Executors.newFixedThreadPool(4)
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val completed = AtomicInteger()
        try {
            val tasks = (1..32).map {
                callers.submit<Unit> {
                    executor.call {
                        maximum.accumulateAndGet(active.incrementAndGet(), ::maxOf)
                        Thread.yield()
                        completed.incrementAndGet()
                        active.decrementAndGet()
                    }
                }
            }
            tasks.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, maximum.get())
            assertEquals(32, completed.get())
            assertEquals(0, active.get())
        } finally {
            callers.shutdownNow()
        }
    }

    private fun withOwner(block: (OwnerThreadExecutor, AtomicReference<Thread>) -> Unit) {
        val owner = AtomicReference<Thread>()
        val queue = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "desk-tour-test-owner").apply {
                isDaemon = true
                owner.set(this)
            }
        }
        try {
            val executor = OwnerThreadExecutor(
                isOwnerThread = { Thread.currentThread() === owner.get() },
                enqueue = queue::execute,
                invokeAndWait = { task -> queue.submit(task).get() },
            )
            block(executor, owner)
        } finally {
            queue.shutdownNow()
        }
    }
}
