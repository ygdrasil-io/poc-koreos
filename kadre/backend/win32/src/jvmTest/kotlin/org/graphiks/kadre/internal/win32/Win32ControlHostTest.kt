package org.graphiks.kadre.internal.win32

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Win32ControlHostTest {
    private class FakeWindow : AutoCloseable {
        var closeCount = 0
        override fun close() {
            closeCount += 1
        }
    }

    @Test
    fun unsupportedOsFailsActionablyBeforeNative() {
        val host = Win32ControlHost(osGate = { false })
        assertEquals(false, host.isSupported)
        val failure = runCatching { host.open { _, _, _, _ -> 0L } }.exceptionOrNull()
        assertTrue(failure is IllegalStateException, "got $failure")
        assertTrue(failure?.message?.contains("Windows") == true)
        assertTrue(host.dispatches.isEmpty())
        assertTrue(host.hwnd == 0L)
    }

    @Test
    fun openRecordsCopiedDispatchesAndCloseIsIdempotent() {
        val window = FakeWindow()
        var factoryHandler: ((Long, Int, Long, Long) -> Long)? = null
        val host = Win32ControlHost(
            osGate = { true },
            nativeFactory = { handler ->
                factoryHandler = handler
                window
            },
        )
        host.open { _, _, _, _ -> 0L }
        // Simule une livraison copiée par la factory injectée (chemin Kadre uniquement).
        factoryHandler!!.invoke(9L, 0x8001, 5L, 6L)
        assertEquals(1, host.dispatches.size)
        val dispatch = host.dispatches.single()
        assertEquals(9L, dispatch.hwnd)
        assertEquals(0x8001, dispatch.message)
        assertEquals(5L, dispatch.wParam)
        assertEquals(6L, dispatch.lParam)
        host.close()
        host.close()
        assertTrue(host.isClosed)
        assertEquals(1, window.closeCount, "close must be idempotent: one native close")
    }

    @Test
    fun pollAfterCloseReturnsFalseWithoutError() {
        val host = Win32ControlHost(osGate = { true }, nativeFactory = { FakeWindow() })
        host.open { _, _, _, _ -> 0L }
        host.close()
        assertEquals(false, host.pollOne(0))
    }
}
