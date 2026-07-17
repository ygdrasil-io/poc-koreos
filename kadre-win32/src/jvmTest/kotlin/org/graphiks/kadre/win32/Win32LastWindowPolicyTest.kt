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
    fun `destroyed stages preserve first failure and suppress later distinct failures`() {
        val deliveryFailure = IllegalStateException("delivery")
        val removalFailure = IllegalStateException("removal")
        val exitFailure = IllegalStateException("exit")
        val calls = mutableListOf<String>()
        val eventLoop = Win32EventLoop(postQuitMessage = {
            calls += "exit"
            throw exitFailure
        })
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                calls += "delivery"
                throw deliveryFailure
            }
        }

        val thrown = assertFailsWith<IllegalStateException> {
            eventLoop.deliverWindowEvent(
                handler = handler,
                hwnd = 1L,
                event = WindowEvent.Destroyed,
                removeWindow = {
                    calls += "removal"
                    throw removalFailure
                },
                windowsEmpty = {
                    calls += "empty"
                    true
                },
            )
        }

        assertSame(deliveryFailure, thrown)
        assertEquals(listOf(removalFailure, exitFailure), thrown.suppressed.toList())
        assertEquals(listOf("delivery", "removal", "empty", "exit"), calls)
        assertTrue(eventLoop.isExiting)
    }

    @Test
    fun `destroyed stages tolerate the same Throwable instance`() {
        val shared = IllegalStateException("shared")
        val calls = mutableListOf<String>()
        val eventLoop = Win32EventLoop(postQuitMessage = {
            calls += "exit"
            throw shared
        })
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                calls += "delivery"
                throw shared
            }
        }

        val thrown = assertFailsWith<IllegalStateException> {
            eventLoop.deliverWindowEvent(
                handler = handler,
                hwnd = 1L,
                event = WindowEvent.Destroyed,
                removeWindow = {
                    calls += "removal"
                    throw shared
                },
                windowsEmpty = {
                    calls += "empty"
                    true
                },
            )
        }

        assertSame(shared, thrown)
        assertEquals(emptyList(), thrown.suppressed.toList())
        assertEquals(listOf("delivery", "removal", "empty", "exit"), calls)
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
                unregisterInside = { calls += "inside" },
            )
        }

        assertSame(deliveryFailure, thrown)
        assertEquals(listOf("emit", "focus", "constraints", "modifiers", "inside"), calls)
    }

    @Test
    fun `WndProc registries preserve distinct failures in cleanup order`() {
        val deliveryFailure = IllegalStateException("delivery")
        val focusFailure = IllegalStateException("focus")
        val constraintsFailure = IllegalStateException("constraints")
        val modifiersFailure = IllegalStateException("modifiers")
        val insideFailure = IllegalStateException("inside")
        val calls = mutableListOf<String>()

        val thrown = assertFailsWith<IllegalStateException> {
            KadreWndProc.dispatchDestroy(
                hwnd = 1L,
                emitEvent = { _, _ ->
                    calls += "emit"
                    throw deliveryFailure
                },
                unregisterFocus = {
                    calls += "focus"
                    throw focusFailure
                },
                unregisterConstraints = {
                    calls += "constraints"
                    throw constraintsFailure
                },
                unregisterModifiers = {
                    calls += "modifiers"
                    throw modifiersFailure
                },
                unregisterInside = {
                    calls += "inside"
                    throw insideFailure
                },
            )
        }

        assertSame(deliveryFailure, thrown)
        assertEquals(
            listOf(focusFailure, constraintsFailure, modifiersFailure, insideFailure),
            thrown.suppressed.toList(),
        )
        assertEquals(listOf("emit", "focus", "constraints", "modifiers", "inside"), calls)
    }

    @Test
    fun `WndProc registries tolerate the same Throwable instance`() {
        val shared = IllegalStateException("shared")
        val calls = mutableListOf<String>()

        val thrown = assertFailsWith<IllegalStateException> {
            KadreWndProc.dispatchDestroy(
                hwnd = 1L,
                emitEvent = { _, _ ->
                    calls += "emit"
                    throw shared
                },
                unregisterFocus = {
                    calls += "focus"
                    throw shared
                },
                unregisterConstraints = {
                    calls += "constraints"
                    throw shared
                },
                unregisterModifiers = {
                    calls += "modifiers"
                    throw shared
                },
                unregisterInside = {
                    calls += "inside"
                    throw shared
                },
            )
        }

        assertSame(shared, thrown)
        assertEquals(emptyList(), thrown.suppressed.toList())
        assertEquals(listOf("emit", "focus", "constraints", "modifiers", "inside"), calls)
    }

    @Test
    fun `destroyed HWND reused by Windows emits PointerEntered again`() {
        val hwnd = 0x7654_3210L
        val events = mutableListOf<WindowEvent>()
        KadreWndProc.install { _, event -> events += event }

        try {
            KadreWndProc.dispatch(hwnd, WM_MOUSEMOVE, 0L, 0L)
            assertTrue(events.first() is WindowEvent.PointerEntered)

            KadreWndProc.dispatch(hwnd, WM_DESTROY, 0L, 0L)
            events.clear()

            KadreWndProc.dispatch(hwnd, WM_MOUSEMOVE, 0L, 0L)
            assertTrue(
                events.first() is WindowEvent.PointerEntered,
                "a recycled HWND must not inherit the destroyed window's inside state",
            )
        } finally {
            KadreWndProc.dispatch(hwnd, WM_MOUSELEAVE, 0L, 0L)
        }
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
