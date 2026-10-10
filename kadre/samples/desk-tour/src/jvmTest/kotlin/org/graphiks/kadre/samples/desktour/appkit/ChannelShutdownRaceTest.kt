package org.graphiks.kadre.samples.desktour.appkit

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ChannelShutdownRaceTest {
    @Test
    fun `foreign channel resume before owner cancellation completes its finally before admission closes`() {
        val ownerThread = AtomicReference<Thread>()
        val owner = Executors.newSingleThreadExecutor { task ->
            Thread(task).apply { isDaemon = true; ownerThread.set(this) }
        }
        val caller = Executors.newSingleThreadExecutor { Thread(it).apply { isDaemon = true } }
        val ownerBlocked = CountDownLatch(1)
        val releaseOwner = CountDownLatch(1)
        val cleanupSubmitted = CountDownLatch(1)
        val finalized = AtomicBoolean()
        val posts = AtomicInteger()
        val channel = Channel<Unit>()
        val work = Job()
        try {
            val executor = OwnerThreadExecutor(
                isOwnerThread = { Thread.currentThread() === ownerThread.get() },
                enqueue = { posts.incrementAndGet(); owner.execute(it) },
                invokeAndWait = { task ->
                    val invocation = owner.submit(task)
                    cleanupSubmitted.countDown()
                    invocation.get()
                },
            )
            val child = CoroutineScope(work + executor).launch {
                try { channel.receive() } finally { finalized.set(true) }
            }
            owner.submit {}.get(5, TimeUnit.SECONDS) // receive is now suspended
            owner.execute { ownerBlocked.countDown(); releaseOwner.await() }
            check(ownerBlocked.await(5, TimeUnit.SECONDS))
            val close = caller.submit<Unit> {
                runBlocking { executor.shutdown(work) { work.cancel(); channel.close() } }
            }
            check(cleanupSubmitted.await(5, TimeUnit.SECONDS))
            // A real foreign send resumes the receiver after close starts, before owner cancel.
            assertTrue(channel.trySend(Unit).isSuccess)
            releaseOwner.countDown()
            close.get(5, TimeUnit.SECONDS)
            assertTrue(finalized.get())
            assertTrue(child.isCompleted)
            assertTrue(work.isCompleted)
            val beforeLate = posts.get()
            executor.dispatch(EmptyCoroutineContext, Runnable { error("late detached flush ran") })
            assertEquals(beforeLate, posts.get())
        } finally {
            releaseOwner.countDown()
            caller.shutdownNow()
            owner.shutdownNow()
        }
    }
}
