package org.graphiks.kadre.internal.win32

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DeterministicWin32WindowPortTest {
    @Test
    fun createTopLevelWindowBookkeepsAndRoutesByHwnd() {
        val port = DeterministicWin32HostPort()
        val seen = mutableListOf<Int>()
        val window = port.createTopLevelWindow(
            title = "kadre",
            decorations = Win32WindowDecorations.System,
            width = 800,
            height = 600,
        ) { _, id, _, _ ->
            seen.add(id)
            0L
        }

        assertTrue(window.hwnd != 0L)
        assertEquals(1, port.createdTopLevelWindows.get())
        port.dispatchWindowMessage(window.hwnd, 0x0010) // WM_CLOSE
        assertEquals(listOf(0x0010), seen)
        port.destroyWindow(window.hwnd)
        assertEquals(1, port.destroyedTopLevelWindows.get())
    }

    @Test
    fun destroyedWindowStopsRouting() {
        val port = DeterministicWin32HostPort()
        val seen = mutableListOf<Int>()
        val window = port.createTopLevelWindow(
            title = "kadre",
            decorations = Win32WindowDecorations.Borderless,
            width = 640,
            height = 480,
        ) { _, id, _, _ ->
            seen.add(id)
            0L
        }
        port.destroyWindow(window.hwnd)
        port.dispatchWindowMessage(window.hwnd, 0x0010)
        assertTrue(seen.isEmpty(), "a destroyed window must not route messages")
    }

    @Test
    fun requestClosePostsWmClose() {
        val port = DeterministicWin32HostPort()
        val seen = mutableListOf<Int>()
        val window = port.createTopLevelWindow(
            title = "kadre",
            decorations = Win32WindowDecorations.System,
            width = 100,
            height = 100,
        ) { _, id, _, _ ->
            seen.add(id)
            0L
        }
        port.requestCloseWindow(window.hwnd)
        port.dispatchWindowMessage(window.hwnd, 0x0010)
        assertEquals(listOf(0x0010), seen)
        assertEquals(1, port.postedCloses.get())
    }

    @Test
    fun duplicateHwndRegistrationIsImpossibleByConstruction() {
        val port = DeterministicWin32HostPort()
        val first = port.createTopLevelWindow("a", Win32WindowDecorations.System, 10, 10) { _, _, _, _ -> 0L }
        port.destroyWindow(first.hwnd)
        val second = port.createTopLevelWindow("b", Win32WindowDecorations.System, 10, 10) { _, _, _, _ -> 0L }
        // La réutilisation d'adresse ne doit pas héberger l'ancien handler.
        assertFailsWith<IllegalStateException> {
            port.errorIfWindowUnknown(first.hwnd)
        }
        assertEquals(second.hwnd != 0L, true)
    }
}

class DeterministicWin32SurfacePortTest {
    @Test
    fun scriptedClientSizeIsReadBack() {
        val port = DeterministicWin32HostPort()
        val window = port.createTopLevelWindow("s", Win32WindowDecorations.System, 320, 240) { _, _, _, _ -> 0L }
        port.scriptedClientSize[window.hwnd] = 320 to 240
        assertEquals(320 to 240, port.readClientPhysicalSize(window.hwnd))
    }

    @Test
    fun invalidateAndValidateAreCounted() {
        val port = DeterministicWin32HostPort()
        val window = port.createTopLevelWindow("s", Win32WindowDecorations.System, 100, 100) { _, _, _, _ -> 0L }
        port.invalidateRect(window.hwnd)
        port.invalidateRect(window.hwnd)
        port.validateRect(window.hwnd)
        assertEquals(2, port.invalidateCount.get())
        assertEquals(1, port.validateCount.get())
    }

    @Test
    fun paintDispatchRequiresPriorInvalidation() {
        val port = DeterministicWin32HostPort()
        val window = port.createTopLevelWindow("s", Win32WindowDecorations.System, 100, 100) { _, _, _, _ -> 0L }
        port.dispatchWindowMessage(window.hwnd, 0x000F) // WM_PAINT sans invalidation
        assertEquals(0, port.paintCount.get())
        port.invalidateRect(window.hwnd)
        port.dispatchWindowMessage(window.hwnd, 0x000F)
        assertEquals(1, port.paintCount.get())
    }

    @Test
    fun scaleFactorIsScripted() {
        val port = DeterministicWin32HostPort()
        val window = port.createTopLevelWindow("s", Win32WindowDecorations.System, 100, 100) { _, _, _, _ -> 0L }
        port.scriptedScaleFactor[window.hwnd] = 1.5
        assertEquals(1.5, port.readScaleFactor(window.hwnd))
    }
}
