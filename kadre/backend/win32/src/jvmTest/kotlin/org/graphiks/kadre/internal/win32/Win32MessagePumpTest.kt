package org.graphiks.kadre.internal.win32

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Win32MessagePumpTest {
    @Test
    fun dispatchedMessageReturnsDispatchedStep() {
        val port = DeterministicWin32HostPort()
        port.enqueueMessage(100, wParam = 5L, lParam = 6L)
        val pump = Win32MessagePump(port, hwnd = 1L)
        assertEquals(PumpStep.Dispatched, pump.runOnce(50))
        assertEquals(1, port.dispatchCount.get())
        assertNull(pump.failureReason())
    }

    @Test
    fun idleQueueRearms() {
        val port = DeterministicWin32HostPort()
        val pump = Win32MessagePump(port, hwnd = 1L)
        assertEquals(PumpStep.Rearm, pump.runOnce(20))
    }

    @Test
    fun postedStopYieldsStoppedExactlyOnce() {
        val port = DeterministicWin32HostPort()
        val pump = Win32MessagePump(port, hwnd = 1L)
        port.requestStopFromAnyone(1L)
        assertEquals(PumpStep.Stopped, pump.runOnce(50))
        assertEquals(PumpStep.Stopped, pump.runOnce(50))
    }

    @Test
    fun ownerRequestStopPostsWakeAndYieldsStopped() {
        val port = DeterministicWin32HostPort()
        val pump = Win32MessagePump(port, hwnd = 1L)
        pump.requestStop()
        assertEquals(1, port.wakeCount.get())
        assertEquals(PumpStep.Stopped, pump.runOnce(50))
    }

    @Test
    fun getErrorMessageYieldsPumpFailedWithReason() {
        val port = DeterministicWin32HostPort()
        port.forcedWaitResult.set(Win32MessageWait.Error(-1))
        val pump = Win32MessagePump(port, hwnd = 1L)
        assertEquals(PumpStep.PumpFailed, pump.runOnce(50))
        assertEquals(-1, pump.failureReason())
        // Une pompe en échec reste en échec.
        assertEquals(PumpStep.PumpFailed, pump.runOnce(50))
    }

    @Test
    fun requestStopWakesABlockedWaitFromAnotherThread() {
        val port = DeterministicWin32HostPort()
        val pump = Win32MessagePump(port, hwnd = 1L)
        val entered = CountDownLatch(1)
        val released = thread(isDaemon = true) {
            entered.countDown()
            assertEquals(PumpStep.Stopped, pump.runOnce(5_000))
        }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        Thread.sleep(100) // laisse le wait entrer en bloqué
        pump.requestStop()
        released.join(3_000)
        assertTrue(!released.isAlive, "blocked runOnce must be released by the stop wake")
    }
}
