package org.graphiks.kadre.win32

import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import org.graphiks.kadre.ffi.win32.WM_DESTROY
import org.graphiks.kadre.ffi.win32.allocateMsg
import org.graphiks.kadre.ffi.win32.generated.PeekMessageW
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class Win32LastWindowPolicyTest {

    @AfterTest
    fun cleanupWndProcAndQuitMessages() {
        KadreWndProc.uninstall()
        if (isWindowsHost()) {
            Arena.ofConfined().use { arena ->
                val message = arena.allocateMsg()
                while (
                    PeekMessageW(
                        message,
                        MemorySegment.NULL,
                        WM_QUIT_MESSAGE,
                        WM_QUIT_MESSAGE,
                        PM_REMOVE,
                    ) != 0
                ) {
                    // Drain thread-local WM_QUIT messages so tests stay isolated.
                }
            }
        }
    }

    @Test
    fun `destroying first of two windows keeps loop alive`() {
        val fixture = PolicyFixture(1L, 2L)

        fixture.deliver(1L, WindowEvent.Destroyed)

        assertEquals(setOf(2L), fixture.trackedWindows)
        assertFalse(fixture.eventLoop.isExiting)
        assertEquals(emptyList(), fixture.quitCodes)
    }

    @Test
    fun `remaining window still receives redraw after first is destroyed`() {
        val fixture = PolicyFixture(1L, 2L)

        fixture.deliver(1L, WindowEvent.Destroyed)
        fixture.deliver(2L, WindowEvent.RedrawRequested)

        assertEquals(
            listOf(1L to WindowEvent.Destroyed, 2L to WindowEvent.RedrawRequested),
            fixture.events,
        )
    }

    @Test
    fun `destroying final window exits and posts quit`() {
        val fixture = PolicyFixture(1L)

        fixture.deliver(1L, WindowEvent.Destroyed)

        assertTrue(fixture.eventLoop.isExiting)
        assertEquals(listOf(0), fixture.quitCodes)
    }

    @Test
    fun `explicit exit posts quit while windows remain`() {
        val fixture = PolicyFixture(1L, 2L)

        fixture.eventLoop.exit()

        assertTrue(fixture.eventLoop.isExiting)
        assertEquals(setOf(1L, 2L), fixture.trackedWindows)
        assertEquals(listOf(0), fixture.quitCodes)
    }

    @Test
    fun `replacement created synchronously from Destroyed prevents exit`() {
        val replacementHwnd = 2L
        val fixture = PolicyFixture(1L) { _, event ->
            if (event is WindowEvent.Destroyed) {
                trackedWindows += replacementHwnd
            }
        }

        fixture.deliver(1L, WindowEvent.Destroyed)

        assertEquals(setOf(replacementHwnd), fixture.trackedWindows)
        assertFalse(fixture.eventLoop.isExiting)
        assertEquals(emptyList(), fixture.quitCodes)
    }

    @Test
    fun `destroyed delivery failure still removes final window and exits`() {
        val deliveryFailure = IllegalStateException("destroyed delivery failed")
        val fixture = PolicyFixture(1L) { _, event ->
            if (event is WindowEvent.Destroyed) throw deliveryFailure
        }

        val thrown = assertFailsWith<IllegalStateException> {
            fixture.deliver(1L, WindowEvent.Destroyed)
        }

        assertSame(deliveryFailure, thrown)
        assertEquals(emptySet(), fixture.trackedWindows)
        assertTrue(fixture.eventLoop.isExiting)
        assertEquals(listOf(0), fixture.quitCodes)
    }

    @Test
    fun `WndProc destroy failure still clears every per-window registry`() {
        val deliveryFailure = IllegalStateException("destroyed delivery failed")
        val calls = mutableListOf<String>()

        val thrown = assertFailsWith<IllegalStateException> {
            KadreWndProc.dispatchDestroy(
                hwnd = 1L,
                emitEvent = { _, event ->
                    assertEquals(WindowEvent.Destroyed, event)
                    calls += "emit"
                    throw deliveryFailure
                },
                unregisterFocus = { calls += "focus" },
                unregisterConstraints = { calls += "constraints" },
                unregisterModifiers = { calls += "modifiers" },
            )
        }

        assertSame(deliveryFailure, thrown)
        assertEquals(listOf("emit", "focus", "constraints", "modifiers"), calls)
    }

    @Test
    fun `WndProc destroy does not post quit directly`() {
        if (!isWindowsHost()) return
        KadreWndProc.install { _, _ -> }

        KadreWndProc.dispatch(0x1234L, WM_DESTROY, 0L, 0L)

        Arena.ofConfined().use { arena ->
            val message = arena.allocateMsg()
            val foundQuit = PeekMessageW(
                message,
                MemorySegment.NULL,
                WM_QUIT_MESSAGE,
                WM_QUIT_MESSAGE,
                PM_REMOVE,
            )
            assertEquals(0, foundQuit, "WM_DESTROY must not post WM_QUIT itself")
        }
    }
}

private const val WM_QUIT_MESSAGE = 0x0012

private fun isWindowsHost(): Boolean =
    System.getProperty("os.name", "").contains("Windows", ignoreCase = true)

private class PolicyFixture(
    vararg hwnds: Long,
    private val afterEvent: PolicyFixture.(Long, WindowEvent) -> Unit = { _, _ -> },
) {
    val quitCodes = mutableListOf<Int>()
    val eventLoop = Win32EventLoop(postQuitMessage = { code: Int -> quitCodes += code })
    val trackedWindows = hwnds.toMutableSet()
    val events = mutableListOf<Pair<Long, WindowEvent>>()

    private val handler = object : ApplicationHandler {
        override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

        override fun windowEvent(eventLoop: ActiveEventLoop, windowId: WindowId, event: WindowEvent) {
            events += windowId.value to event
            afterEvent(windowId.value, event)
        }
    }

    fun deliver(hwnd: Long, event: WindowEvent) {
        eventLoop.deliverWindowEvent(
            handler = handler,
            hwnd = hwnd,
            event = event,
            removeWindow = { hwndToRemove: Long -> trackedWindows.remove(hwndToRemove) },
            windowsEmpty = trackedWindows::isEmpty,
        )
    }
}
