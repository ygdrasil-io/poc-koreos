package org.graphiks.kadre.samples.desktour.appkit

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class OwnerThreadShutdownTest {
    @Test
    fun `owner cleanup can finish cancellation finalizers without async admission`() {
        val ownerThread = AtomicReference<Thread>()
        val owner = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable).also { ownerThread.set(it) }
        }
        val started = CompletableDeferred<Unit>()
        val finalized = AtomicBoolean()
        val work = Job()
        try {
            val executor = OwnerThreadExecutor(
                { Thread.currentThread() === ownerThread.get() }, owner::execute,
                { owner.submit(it).get() },
            )
            val child = CoroutineScope(work + executor).launch {
                try {
                    started.complete(Unit)
                    awaitCancellation()
                } finally {
                    finalized.set(true)
                }
            }
            runBlocking { started.await() }
            runBlocking { executor.shutdown(work) { work.cancel() } }
            assertTrue(finalized.get())
            assertTrue(child.isCompleted)
            assertTrue(work.isCompleted)
        } finally {
            owner.shutdownNow()
        }
    }

    @Test
    fun `shutdown rejects a delayed producer and waits for every admitted wrapper`() {
        val owner = Executors.newSingleThreadExecutor()
        val callers = Executors.newFixedThreadPool(2)
        val priorStarted = CountDownLatch(1)
        val releasePrior = CountDownLatch(1)
        val shutdownSubmitted = CountDownLatch(1)
        val priorUnwound = AtomicBoolean()
        val lateRan = AtomicBoolean()
        val posted = AtomicInteger()
        val syncCalls = AtomicInteger()
        val lateJob = Job()
        val reentrantJob = Job()
        val work = Job()
        try {
            val executor = OwnerThreadExecutor(
                isOwnerThread = { false },
                enqueue = { task ->
                    posted.incrementAndGet()
                    owner.execute {
                        task.run()
                        releasePrior.await()
                        priorUnwound.set(true)
                    }
                },
                invokeAndWait = { task ->
                    val invocation = syncCalls.incrementAndGet()
                    val result = owner.submit(task)
                    if (invocation == 2) shutdownSubmitted.countDown()
                    result.get()
                },
            )
            val shutdown = callers.submit<Unit> {
                runBlocking {
                    executor.shutdown(work) {
                        work.cancel()
                        // Detached scene flush admitted during cleanup; work itself is done.
                        executor.dispatch(EmptyCoroutineContext, Runnable {
                            priorStarted.countDown()
                            check(shutdownSubmitted.await(5, TimeUnit.SECONDS))
                            executor.dispatch(reentrantJob, Runnable { lateRan.set(true) })
                        })
                    }
                }
            }
            check(priorStarted.await(5, TimeUnit.SECONDS))
            check(shutdownSubmitted.await(5, TimeUnit.SECONDS))
            // This foreign producer arrives only after shutdown has closed admission.
            callers.submit { executor.dispatch(lateJob, Runnable { lateRan.set(true) }) }.get(5, TimeUnit.SECONDS)
            assertEquals(1, posted.get())
            assertTrue(lateJob.isCancelled)
            assertFailsWith<TimeoutException> { shutdown.get(100, TimeUnit.MILLISECONDS) }
            releasePrior.countDown()
            shutdown.get(5, TimeUnit.SECONDS)
            assertTrue(priorUnwound.get())
            assertFalse(lateRan.get())
            assertTrue(reentrantJob.isCancelled)
            assertEquals(2, syncCalls.get()) // cleanup and native-return barrier
            runBlocking { executor.shutdown(work) { error("cleanup must not run twice") } }
            assertEquals(2, syncCalls.get())
            val nextMount = OwnerThreadExecutor({ false }, owner::execute) { owner.submit(it).get() }
            val nextRan = AtomicBoolean()
            nextMount.dispatch(EmptyCoroutineContext, Runnable { nextRan.set(true) })
            owner.submit {}.get(5, TimeUnit.SECONDS)
            assertTrue(nextRan.get())
        } finally {
            releasePrior.countDown()
            callers.shutdownNow()
            owner.shutdownNow()
        }
    }

    @Test
    fun `cleanup failure still drains and retains a barrier failure as suppressed`() {
        val owner = Executors.newSingleThreadExecutor()
        val syncCalls = AtomicInteger()
        val primary = IllegalStateException("scene close failed")
        val barrierFailure = IllegalArgumentException("barrier cleanup failed")
        val barrierReturned = AtomicBoolean()
        val work = Job()
        try {
            val executor = OwnerThreadExecutor(
                isOwnerThread = { false },
                enqueue = owner::execute,
                invokeAndWait = { task ->
                    val invocation = syncCalls.incrementAndGet()
                    owner.submit(task).get()
                    if (invocation == 2) {
                        barrierReturned.set(true)
                        throw barrierFailure
                    }
                },
            )
            val thrown = runBlocking {
                assertFailsWith<IllegalStateException> { executor.shutdown(work) { work.cancel(); throw primary } }
            }
            assertSame(primary, thrown)
            assertTrue(barrierReturned.get())
            assertEquals(2, syncCalls.get())
            assertEquals(listOf(barrierFailure), thrown.suppressed.toList())
        } finally {
            owner.shutdownNow()
        }
    }
}
