package org.graphiks.kadre.win32

import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import java.lang.foreign.MemorySegment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class Win32LifecycleTest {

    @BeforeTest
    fun isolateGlobals() {
        KadreWndProc.uninstall()
        win32Running.set(false)
    }

    @AfterTest
    fun cleanupGlobals() {
        KadreWndProc.uninstall()
        win32Running.set(false)
    }

    @Test
    fun `resumed failure stays primary while both cleanups are attempted`() {
        val primary = LifecycleFailure("resumed")
        val suspendedFailure = LifecycleFailure("suspended")
        val destroyFailure = LifecycleFailure("destroySurfaces")
        val handler = LifecycleRecordingHandler(
            resumedFailure = primary,
            suspendedFailure = suspendedFailure,
            destroySurfacesFailure = destroyFailure,
        )

        val thrown = assertFailsWith<LifecycleFailure> {
            runApp(handler, messageLoop = { _, _ -> error("loop must not run") })
        }

        assertSame(primary, thrown)
        assertEquals(listOf(suspendedFailure, destroyFailure), thrown.suppressed.toList())
        assertEquals(listOf("resumed", "suspended", "destroySurfaces"), handler.calls)
        assertCleanAndReusable(handler)
    }

    @Test
    fun `canCreateSurfaces failure stays primary while both cleanups are attempted`() {
        val primary = LifecycleFailure("canCreateSurfaces")
        val suspendedFailure = LifecycleFailure("suspended")
        val destroyFailure = LifecycleFailure("destroySurfaces")
        val handler = LifecycleRecordingHandler(
            canCreateSurfacesFailure = primary,
            suspendedFailure = suspendedFailure,
            destroySurfacesFailure = destroyFailure,
        )

        val thrown = assertFailsWith<LifecycleFailure> {
            runApp(handler, messageLoop = { _, _ -> error("loop must not run") })
        }

        assertSame(primary, thrown)
        assertEquals(listOf(suspendedFailure, destroyFailure), thrown.suppressed.toList())
        assertEquals(
            listOf("resumed", "canCreateSurfaces", "suspended", "destroySurfaces"),
            handler.calls,
        )
        assertCleanAndReusable(handler)
    }

    @Test
    fun `message loop failure stays primary while both cleanups are attempted`() {
        val primary = LifecycleFailure("messageLoop")
        val suspendedFailure = LifecycleFailure("suspended")
        val destroyFailure = LifecycleFailure("destroySurfaces")
        val handler = LifecycleRecordingHandler(
            suspendedFailure = suspendedFailure,
            destroySurfacesFailure = destroyFailure,
        )

        val thrown = assertFailsWith<LifecycleFailure> {
            runApp(handler, messageLoop = { _, _ ->
                handler.calls += "messageLoop"
                throw primary
            })
        }

        assertSame(primary, thrown)
        assertEquals(listOf(suspendedFailure, destroyFailure), thrown.suppressed.toList())
        assertEquals(
            listOf("resumed", "canCreateSurfaces", "messageLoop", "suspended", "destroySurfaces"),
            handler.calls,
        )
        assertCleanAndReusable(handler)
    }

    @Test
    fun `destroySurfaces is attempted after suspended failure and becomes suppressed`() {
        val suspendedFailure = LifecycleFailure("suspended")
        val destroyFailure = LifecycleFailure("destroySurfaces")
        val handler = LifecycleRecordingHandler(
            suspendedFailure = suspendedFailure,
            destroySurfacesFailure = destroyFailure,
        )

        val thrown = assertFailsWith<LifecycleFailure> {
            runApp(handler, messageLoop = { _, _ -> handler.calls += "messageLoop" })
        }

        assertSame(suspendedFailure, thrown)
        assertEquals(listOf(destroyFailure), thrown.suppressed.toList())
        assertEquals(
            listOf("resumed", "canCreateSurfaces", "messageLoop", "suspended", "destroySurfaces"),
            handler.calls,
        )
        assertCleanAndReusable(handler)
    }

    @Test
    fun `destroySurfaces failure propagates after successful suspended`() {
        val destroyFailure = LifecycleFailure("destroySurfaces")
        val handler = LifecycleRecordingHandler(destroySurfacesFailure = destroyFailure)

        val thrown = assertFailsWith<LifecycleFailure> {
            runApp(handler, messageLoop = { _, _ -> handler.calls += "messageLoop" })
        }

        assertSame(destroyFailure, thrown)
        assertEquals(emptyList(), thrown.suppressed.toList())
        assertEquals(
            listOf("resumed", "canCreateSurfaces", "messageLoop", "suspended", "destroySurfaces"),
            handler.calls,
        )
        assertCleanAndReusable(handler)
    }

    @Test
    fun `WndProc upcall defers failures and runApp preserves cleanup order`() {
        val primary = LifecycleFailure("first window event")
        val second = LifecycleFailure("second window event")
        val cleanup = LifecycleFailure("destroySurfaces")
        val calls = mutableListOf<String>()
        var windowEventCalls = 0
        val handler = object : ApplicationHandler {
            override fun resumed(eventLoop: ActiveEventLoop) {
                calls += "resumed"
            }

            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
                calls += "canCreateSurfaces"
            }

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                calls += "windowEvent"
                windowEventCalls++
                throw if (windowEventCalls == 1) primary else second
            }

            override fun suspended(eventLoop: ActiveEventLoop) {
                calls += "suspended"
                throw primary
            }

            override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                calls += "destroySurfaces"
                throw cleanup
            }
        }

        val thrown = assertFailsWith<LifecycleFailure> {
            runApp(handler, messageLoop = { _, _ ->
                calls += "messageLoop"
                assertEquals(
                    0L,
                    Win32Window.wndProc(MemorySegment.ofAddress(0x1234L), WM_SIZE, 0L, 0L),
                    "the FFM upcall target must never throw",
                )
                assertEquals(
                    0L,
                    Win32Window.wndProc(MemorySegment.ofAddress(0x1234L), WM_SIZE, 0L, 0L),
                    "later upcall failures must also be deferred",
                )
            })
        }

        assertSame(primary, thrown)
        assertEquals(listOf(second, cleanup), thrown.suppressed.toList())
        assertEquals(
            listOf(
                "resumed",
                "canCreateSurfaces",
                "messageLoop",
                "windowEvent",
                "windowEvent",
                "suspended",
                "destroySurfaces",
            ),
            calls,
        )
        assertFalse(win32Running.get())

        val probe = LifecycleRecordingHandler()
        runApp(probe, messageLoop = { _, _ -> probe.calls += "messageLoop" })
        assertEquals(
            listOf("resumed", "canCreateSurfaces", "messageLoop", "suspended", "destroySurfaces"),
            probe.calls,
        )
    }

    @Test
    fun `WndProc catch path returns zero when deferred failure recorder throws`() {
        val deliveryFailure = LifecycleFailure("window event")
        val recorderFailure = LifecycleFailure("recorder")
        var recorded: Throwable? = null
        KadreWndProc.install { _, _ -> throw deliveryFailure }

        val result = Win32Window.wndProc(
            hwnd = MemorySegment.ofAddress(0x1234L),
            msg = WM_SIZE,
            wParam = 0L,
            lParam = 0L,
            recordFailure = { failure ->
                recorded = failure
                throw recorderFailure
            },
        )

        assertEquals(0L, result, "the FFM upcall target must remain no-throw")
        assertSame(deliveryFailure, recorded)
    }

    @Test
    fun `Java message loop rethrows a failure pending before its body`() {
        val primary = LifecycleFailure("pending before message loop")
        var newEventsCalls = 0
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) = Unit

            override fun newEvents(
                eventLoop: ActiveEventLoop,
                startCause: org.graphiks.kadre.core.StartCause,
            ) {
                newEventsCalls++
            }
        }
        KadreWndProc.install { _, _ -> throw primary }

        assertEquals(
            0L,
            Win32Window.wndProc(MemorySegment.ofAddress(0x1234L), WM_SIZE, 0L, 0L),
            "the upcall must return before Java code rethrows the pending failure",
        )

        val thrown = assertFailsWith<LifecycleFailure> {
            Win32EventLoop(postQuitMessage = {}).runMessageLoop(handler)
        }

        assertSame(primary, thrown)
        assertEquals(0, newEventsCalls, "a pending failure must be checked before the loop body")
    }

    private fun assertCleanAndReusable(handler: LifecycleRecordingHandler) {
        assertFalse(win32Running.get(), "win32Running must reset after every path")
        handler.assertLifecycleCallbacksAtMostOnce()

        val eventsBeforeProbe = handler.windowEventCalls
        KadreWndProc.dispatch(0x4321L, WM_SIZE, 0L, 0L)
        assertEquals(eventsBeforeProbe, handler.windowEventCalls, "WndProc handler must be uninstalled")

        val secondHandler = LifecycleRecordingHandler()
        runApp(secondHandler, messageLoop = { _, _ -> secondHandler.calls += "messageLoop" })
        assertEquals(
            listOf("resumed", "canCreateSurfaces", "messageLoop", "suspended", "destroySurfaces"),
            secondHandler.calls,
        )
        assertFalse(win32Running.get(), "second runApp must also reset the lock")
    }
}

private class LifecycleFailure(message: String) : RuntimeException(message)

private class LifecycleRecordingHandler(
    private val resumedFailure: Throwable? = null,
    private val canCreateSurfacesFailure: Throwable? = null,
    private val suspendedFailure: Throwable? = null,
    private val destroySurfacesFailure: Throwable? = null,
) : ApplicationHandler {
    val calls = mutableListOf<String>()
    var windowEventCalls = 0
        private set

    override fun resumed(eventLoop: ActiveEventLoop) {
        calls += "resumed"
        resumedFailure?.let { throw it }
    }

    override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
        calls += "canCreateSurfaces"
        canCreateSurfacesFailure?.let { throw it }
    }

    override fun suspended(eventLoop: ActiveEventLoop) {
        calls += "suspended"
        suspendedFailure?.let { throw it }
    }

    override fun destroySurfaces(eventLoop: ActiveEventLoop) {
        calls += "destroySurfaces"
        destroySurfacesFailure?.let { throw it }
    }

    override fun windowEvent(eventLoop: ActiveEventLoop, windowId: WindowId, event: WindowEvent) {
        windowEventCalls++
    }

    fun assertLifecycleCallbacksAtMostOnce() {
        listOf("resumed", "canCreateSurfaces", "suspended", "destroySurfaces").forEach { callback ->
            assertTrue(calls.count { it == callback } <= 1, "$callback must be invoked at most once")
        }
    }
}
