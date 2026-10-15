package org.graphiks.kadre.internal.win32

import java.util.concurrent.CompletableFuture
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class Win32UiCommandQueueTest {
    @Test fun aMutationAdmittedByTheOwnerStillWaitsForTheNextPumpTurn() {
        var calls = 0
        val queue = Win32UiCommandQueue({ true }, { true })
        queue.defer({ calls++ }, { throw it })
        assertEquals(0, calls, "WindowCommandPort is called under the runtime lock; native callbacks must wait until it returns")
        queue.drain()
        assertEquals(1, calls)
        queue.close()
    }
    @Test
    fun stoppingWorkersReleasesEveryWaiterEvenWhenAFailureCallbackThrowsAndAllowsOwnerCleanup() {
        val owner = Thread.currentThread()
        val admitted = Semaphore(0)
        val queue = Win32UiCommandQueue({ Thread.currentThread() === owner }, { admitted.release(); true })
        worker { queue.post({}, { error("broken failure reporter") }) }.get(2, TimeUnit.SECONDS)
        assertTrue(admitted.tryAcquire(2, TimeUnit.SECONDS))
        val waiting = worker { runCatching { queue.invoke { error("must not run") } }.exceptionOrNull() }
        assertTrue(admitted.tryAcquire(2, TimeUnit.SECONDS))
        queue.stopWorkerAdmission()
        assertTrue(waiting.get(2, TimeUnit.SECONDS) is java.util.concurrent.CancellationException)
        assertEquals(42, queue.invoke { 42 }, "native owner cleanup remains legal before port close")
        val rejected = worker { runCatching { queue.invoke { 1 } }.exceptionOrNull() }.get(2, TimeUnit.SECONDS)
        assertTrue(rejected is java.util.concurrent.CancellationException)
        queue.close()
    }

    @Test
    fun aQueuedWindowCanBeCancelledBeforeTheUiClaimsIt() {
        val owner = Thread.currentThread()
        val queue = Win32UiCommandQueue({ Thread.currentThread() === owner }, { true })
        val port = object : DeterministicWin32HostPort() {
            override fun isMainThread() = Thread.currentThread() === owner
            override fun postOnMainThread(block: () -> Unit, onFailure: (Throwable) -> Unit) = queue.post(block, onFailure)
            override fun <T> onMainThread(block: () -> T): T = queue.invoke(block)
        }
        val driver = Win32WindowRuntimeDriverFactory.create(port, org.graphiks.kadre.policy.KadrePolicies.Default.resources)
        try {
            val cancellation = worker {
                kotlinx.coroutines.runBlocking {
                    val request = kotlin.test.assertIs<org.graphiks.kadre.diagnostics.KadreResult.Success<org.graphiks.kadre.window.WindowRequest>>(
                        driver.manager.requestWindow(org.graphiks.kadre.window.WindowSpec())).value
                    request.cancel()
                }
            }.get(2, TimeUnit.SECONDS)
            assertEquals(org.graphiks.kadre.window.WindowCancellationOutcome.CancelledBeforeCommit, cancellation)
            queue.drain()
            assertEquals(0, port.createdTopLevelWindows.get())
        } finally { driver.close(); queue.close(); port.close() }
    }

    @Test
    fun asynchronousAdmissionReturnsBeforeTheUiPublishesAndCloseRejectsPendingWork() {
        val owner = Thread.currentThread()
        val queue = Win32UiCommandQueue({ Thread.currentThread() === owner }, { true }, 2)
        var ran = false
        var rejected = false
        worker { queue.post({ ran = true }, { rejected = true }) }.get(2, TimeUnit.SECONDS)
        assertFalse(ran)
        queue.drain()
        assertTrue(ran)
        worker { queue.post({ error("closed work must not run") }, { rejected = true }) }.get(2, TimeUnit.SECONDS)
        queue.close()
        assertTrue(rejected)
    }

    private fun <T> worker(block: () -> T): CompletableFuture<T> = CompletableFuture<T>().also { result ->
        thread(isDaemon = true) {
            try { result.complete(block()) } catch (failure: Throwable) { result.completeExceptionally(failure) }
        }
    }

    @Test
    fun workerCommandsWakeAndRunInOrderOnTheirOwnerThread() {
        val owner = Thread.currentThread()
        val wakes = Semaphore(0)
        val queue = Win32UiCommandQueue({ Thread.currentThread() === owner }, { wakes.release(); true }, 2)
        val seen = mutableListOf<Int>()
        val first = worker { queue.invoke { seen.add(1); Thread.currentThread() } }
        assertTrue(wakes.tryAcquire(2, TimeUnit.SECONDS))
        val second = worker { queue.invoke { seen.add(2); 22 } }
        assertTrue(wakes.tryAcquire(2, TimeUnit.SECONDS))
        assertFalse(first.isDone)
        assertFalse(second.isDone)
        queue.drain()
        assertSame(owner, first.get(2, TimeUnit.SECONDS))
        assertEquals(22, second.get(2, TimeUnit.SECONDS))
        assertEquals(listOf(1, 2), seen)
        queue.close()
    }

    @Test
    fun queueCapacityAndNativeWakeFailureAreExplicit() {
        val owner = Thread.currentThread()
        val wakes = Semaphore(0)
        val queue = Win32UiCommandQueue({ Thread.currentThread() === owner }, { wakes.release(); true }, 1)
        val first = worker { queue.invoke { 1 } }
        assertTrue(wakes.tryAcquire(2, TimeUnit.SECONDS))
        val rejection = worker { runCatching { queue.invoke { 2 } }.exceptionOrNull() }.get(2, TimeUnit.SECONDS)
        assertTrue(rejection is RejectedExecutionException)
        queue.drain()
        assertEquals(1, first.get(2, TimeUnit.SECONDS))
        queue.close()
        val notReady = Win32UiCommandQueue({ false }, { false }, 1)
        assertFailsWith<IllegalStateException> { notReady.invoke { error("must not run") } }
        notReady.close()
    }

    @Test
    fun closingTheQueueReleasesWaitingWorkersAndRejectsNewCommands() {
        val owner = Thread.currentThread()
        val wakes = Semaphore(0)
        val queue = Win32UiCommandQueue({ Thread.currentThread() === owner }, { wakes.release(); true }, 1)
        var ran = false
        val waiting = worker { runCatching { queue.invoke { ran = true } }.exceptionOrNull() }
        assertTrue(wakes.tryAcquire(2, TimeUnit.SECONDS))
        queue.close()
        assertTrue(waiting.get(2, TimeUnit.SECONDS) is IllegalStateException)
        assertFalse(ran)
        assertFailsWith<IllegalStateException> { queue.invoke { ran = true } }
        queue.close()
    }

    @Test
    fun nativeOperationFailureReachesItsCallerWithoutBreakingTheDrain() {
        val owner = Thread.currentThread()
        val wakes = Semaphore(0)
        val queue = Win32UiCommandQueue({ Thread.currentThread() === owner }, { wakes.release(); true }, 2)
        val cause = IllegalArgumentException("native-failure")
        val failure = worker { runCatching { queue.invoke<Unit> { throw cause } }.exceptionOrNull() }
        assertTrue(wakes.tryAcquire(2, TimeUnit.SECONDS))
        queue.drain()
        assertSame(cause, failure.get(2, TimeUnit.SECONDS))
        assertEquals(7, queue.invoke { 7 })
        queue.close()
    }
}
