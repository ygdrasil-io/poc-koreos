package org.graphiks.kadre.internal.win32

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DeterministicWin32HostPortTest {
    @Test
    fun createRecordsHandlerAndWindowThenDestroyRemovesIt() {
        val port = DeterministicWin32HostPort()
        var dispatched: Triple<Int, Long, Long>? = null
        val window = port.createControlWindow { id, wParam, lParam ->
            dispatched = Triple(id, wParam, lParam)
            0L
        }
        assertEquals(1, port.createdWindows.get())
        assertEquals(0, port.destroyedWindows.get())
        assertTrue(port.hwnds.contains(window.hwnd))
        port.destroyControlWindow(window.hwnd)
        assertEquals(1, port.destroyedWindows.get())
        port.close()
        val failure = assertFailsWith<IllegalStateException> {
            port.createControlWindow { _, _, _ -> 0L }
        }
        assertTrue(failure.message?.contains("closed") == true)
    }

    @Test
    fun messageQueueIsFifoAndWakeIsCounted() {
        val port = DeterministicWin32HostPort()
        port.enqueueMessage(100, wParam = 1L)
        port.enqueueMessage(101, wParam = 2L)
        val first = port.waitForMessage(0)
        val second = port.waitForMessage(0)
        assertEquals(100, (first as Win32MessageWait.Message).id)
        assertEquals(1L, first.wParam)
        assertEquals(101, (second as Win32MessageWait.Message).id)
        assertTrue(port.postWake(7L))
        assertEquals(1, port.wakeCount.get())
    }

    @Test
    fun idleWhenQueueEmptyWithinTimeout() {
        val port = DeterministicWin32HostPort()
        assertEquals(Win32MessageWait.Idle, port.waitForMessage(30))
    }

    @Test
    fun forcedErrorIsReturnedOnceThenCleared() {
        val port = DeterministicWin32HostPort()
        port.forcedWaitResult.set(Win32MessageWait.Error(-1))
        assertEquals(Win32MessageWait.Error(-1), port.waitForMessage(0))
        assertEquals(Win32MessageWait.Idle, port.waitForMessage(0))
    }

    @Test
    fun stopRequestIsQueuedByRequestStopFromAnyone() {
        val port = DeterministicWin32HostPort()
        port.requestStopFromAnyone(5L)
        val wait = port.waitForMessage(0)
        assertEquals(Win32PumpMessage.StopRequest.ordinal, (wait as Win32MessageWait.Message).id)
        assertEquals(1, port.postedStops.get())
    }
}
