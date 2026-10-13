package org.graphiks.kadre.internal.win32

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Win32HostSessionTest {
    @Test
    fun startCreatesControlWindowAndRecordsCallingThread() {
        val port = DeterministicWin32HostPort()
        val session = Win32HostSession.start(port)
        try {
            assertEquals(1, port.createdWindows.get())
            assertEquals(Thread.currentThread(), session.uiThread)
            assertTrue(session.controlWindow.hwnd != 0L)
        } finally {
            session.teardown()
        }
        assertEquals(1, port.destroyedWindows.get())
    }

    @Test
    fun controlWindowCreationFailureThrowsBeforeAnythingElse() {
        val port = DeterministicWin32HostPort()
        port.close() // le port fermé refuse createControlWindow
        val failure = assertFailsWith<Win32SessionStartException> {
            Win32HostSession.start(port)
        }
        assertEquals("control-window", failure.reason)
        assertEquals(0, port.createdWindows.get())
    }

    @Test
    fun teardownIsIdempotentAndUnwiresTheHandler() {
        val port = DeterministicWin32HostPort()
        val session = Win32HostSession.start(port)
        session.teardown()
        session.teardown()
        assertEquals(1, port.createdWindows.get())
        assertEquals(1, port.destroyedWindows.get())
        assertEquals(null, port.createdHandlers.get())
    }

    @Test
    fun twoSuccessiveSessionsShareNothing() {
        val port = DeterministicWin32HostPort()
        val first = Win32HostSession.start(port)
        val firstHwnd = first.controlWindow.hwnd
        first.teardown()
        val second = Win32HostSession.start(port)
        val secondHwnd = second.controlWindow.hwnd
        second.teardown()
        assertEquals(2, port.createdWindows.get())
        assertEquals(2, port.destroyedWindows.get())
        assertTrue(firstHwnd != secondHwnd)
        assertEquals(Thread.currentThread(), second.uiThread)
    }
}
