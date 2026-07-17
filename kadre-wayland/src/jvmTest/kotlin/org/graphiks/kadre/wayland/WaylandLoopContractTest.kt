package org.graphiks.kadre.wayland

import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import org.graphiks.kadre.ffi.posix.PosixWakeup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WaylandLoopContractTest {
    @Test
    fun `redraw is published before wake can release the polling thread`() {
        lateinit var loop: WaylandEventLoop
        val events = mutableListOf<WindowEvent>()
        val handler = recordingHandler(events = events)
        val wakeup = RecordingWakeup(
            onSignal = {
                dispatchWaylandIteration(loop, handler, StartCause.WaitCancelled())
            },
        )
        loop = testLoop(wakeup)
        val window = WaylandWindow.createForTest(surface = 41L)
        loop.registerWindow(window)

        assertTrue(loop.requestRedraw(window.id))

        assertEquals(listOf<WindowEvent>(WindowEvent.RedrawRequested), events)
    }

    @Test
    fun `redraw coalesces until dispatch then rearms and wakes the loop`() {
        val wakeup = RecordingWakeup()
        val loop = testLoop(wakeup)
        val window = WaylandWindow.createForTest(surface = 42L)
        loop.registerWindow(window)
        val events = mutableListOf<WindowEvent>()
        val handler = recordingHandler(events = events)

        assertTrue(loop.requestRedraw(window.id))
        assertTrue(loop.requestRedraw(window.id))
        assertEquals(1, wakeup.signalCalls)

        dispatchWaylandIteration(loop, handler, StartCause.Poll)
        assertEquals(listOf<WindowEvent>(WindowEvent.RedrawRequested), events)

        assertTrue(loop.requestRedraw(window.id))
        assertEquals(2, wakeup.signalCalls)
        dispatchWaylandIteration(loop, handler, StartCause.Poll)
        assertEquals(
            listOf<WindowEvent>(WindowEvent.RedrawRequested, WindowEvent.RedrawRequested),
            events,
        )
    }

    @Test
    fun `redraw requested from its callback waits for the next iteration`() {
        val wakeup = RecordingWakeup()
        val loop = testLoop(wakeup)
        val window = WaylandWindow.createForTest(surface = 46L)
        loop.registerWindow(window)
        var redraws = 0
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event == WindowEvent.RedrawRequested) {
                    redraws += 1
                    if (redraws == 1) window.requestRedraw()
                }
            }
        }

        loop.requestRedraw(window.id)
        dispatchWaylandIteration(loop, handler, StartCause.Poll)
        assertEquals(1, redraws)
        assertEquals(2, wakeup.signalCalls)

        dispatchWaylandIteration(loop, handler, StartCause.Poll)
        assertEquals(2, redraws)
    }

    @Test
    fun `events enqueued after the iteration boundary wait for the next iteration`() {
        val loop = testLoop(RecordingWakeup())
        val window = WaylandWindow.createForTest(surface = 49L)
        loop.registerWindow(window)
        val events = mutableListOf<WindowEvent>()
        var firstIteration = true
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
            override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                if (firstIteration) loop.enqueueWindowEvent(window.id, WindowEvent.Focused(true))
            }
            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                events += event
            }
        }

        dispatchWaylandIteration(loop, handler, StartCause.Poll)
        assertTrue(events.isEmpty())
        firstIteration = false
        dispatchWaylandIteration(loop, handler, StartCause.Poll)
        assertEquals(listOf<WindowEvent>(WindowEvent.Focused(true)), events)
    }

    @Test
    fun `failed redraw wake rolls back atomically and allows retry`() {
        val wakeup = RecordingWakeup(signalResult = false)
        val loop = testLoop(wakeup)
        val window = WaylandWindow.createForTest(surface = 47L)
        loop.registerWindow(window)

        val failure = assertFailsWith<IllegalStateException> {
            loop.requestRedraw(window.id)
        }
        wakeup.signalResult = true
        assertTrue(loop.requestRedraw(window.id))
        val events = mutableListOf<WindowEvent>()
        dispatchWaylandIteration(loop, recordingHandler(events = events), StartCause.Poll)

        assertTrue(failure.message.orEmpty().contains("redraw"))
        assertEquals(listOf<WindowEvent>(WindowEvent.RedrawRequested), events)
    }

    @Test
    fun `throwing redraw wake rolls back and reports its native cause`() {
        val nativeFailure = IllegalArgumentException("write errno=9")
        val wakeup = RecordingWakeup(signalFailure = nativeFailure)
        val loop = testLoop(wakeup)
        val window = WaylandWindow.createForTest(surface = 50L)
        loop.registerWindow(window)

        val failure = assertFailsWith<IllegalStateException> {
            loop.requestRedraw(window.id)
        }
        wakeup.signalFailure = null
        assertTrue(loop.requestRedraw(window.id))

        assertTrue(failure.message.orEmpty().contains("redraw"))
        assertSame(nativeFailure, failure.cause)
    }

    @Test
    fun `failed close wake preserves the published terminal command`() {
        val wakeup = RecordingWakeup(signalResult = false)
        val loop = testLoop(wakeup)
        var nativeDestroyCalls = 0
        val window = WaylandWindow.createForTest(
            surface = 48L,
            surfaceProxyDestroyer = { nativeDestroyCalls += 1 },
            surfaceFlusher = { 0 },
        )
        loop.registerWindow(window)

        val thrown = assertFailsWith<IllegalStateException> { window.close() }

        assertEquals("Wayland close wake failed", thrown.message)
        assertTrue(thrown.cause?.message.orEmpty().contains("wake owner is closed"))
        assertEquals(0, nativeDestroyCalls)
        assertTrue(loop.windows.containsKey(window.id.value))

        wakeup.signalResult = true
        window.close()
        dispatchWaylandIteration(loop, recordingHandler(), StartCause.Poll)

        assertEquals(2, wakeup.signalCalls)
        assertEquals(1, nativeDestroyCalls)
        assertFalse(loop.windows.containsKey(window.id.value))
        val events = mutableListOf<WindowEvent>()
        dispatchWaylandIteration(loop, recordingHandler(events = events), StartCause.Poll)
        assertEquals(listOf<WindowEvent>(WindowEvent.Destroyed), events)
    }

    @Test
    fun `xdg close keeps its command when wake fails and reports the failure`() {
        val wakeup = RecordingWakeup(signalResult = false)
        val loop = testLoop(wakeup)
        var nativeDestroyCalls = 0
        val window = WaylandWindow.createForTest(
            surface = 54L,
            surfaceProxyDestroyer = { nativeDestroyCalls += 1 },
            surfaceFlusher = { 0 },
        )
        loop.registerWindow(window)
        val failures = mutableListOf<Throwable>()
        val callbacks = XdgUpcallCallbacks(
            onResized = { _, _, _ -> },
            onStateConfigured = {},
            onClose = window::handleXdgToplevelClose,
            onFailure = failures::add,
        )

        callbacks.close()

        assertEquals("Wayland close wake failed", failures.single().message)
        assertTrue(loop.windows.containsKey(window.id.value))
        assertEquals(0, nativeDestroyCalls)

        dispatchWaylandIteration(loop, recordingHandler(), StartCause.Poll)

        assertEquals(1, nativeDestroyCalls)
        assertFalse(loop.windows.containsKey(window.id.value))
        val events = mutableListOf<WindowEvent>()
        dispatchWaylandIteration(loop, recordingHandler(events = events), StartCause.Poll)
        assertEquals(listOf<WindowEvent>(WindowEvent.Destroyed), events)
    }

    @Test
    fun `iteration starts after pump then dispatches valid events before about to wait`() {
        val loop = testLoop(RecordingWakeup())
        val window = WaylandWindow.createForTest(surface = 43L)
        loop.registerWindow(window)
        val trace = mutableListOf<String>()
        val handler = recordingHandler(trace = trace)
        loop.enqueueWindowEvent(window.id, WindowEvent.Focused(true))
        loop.enqueueWindowEvent(WindowId(999L), WindowEvent.Focused(false))

        trace += "pump"
        dispatchWaylandIteration(loop, handler, StartCause.Poll)

        assertEquals(
            listOf("pump", "newEvents", "event-Focused", "aboutToWait"),
            trace,
        )
    }

    @Test
    fun `close removes before native destruction clears redraw and emits one terminal event`() {
        val wakeup = RecordingWakeup()
        val loop = testLoop(wakeup)
        var nativeDestroyCalls = 0
        val window = WaylandWindow.createForTest(
            surface = 44L,
            surfaceProxyDestroyer = {
                assertFalse(loop.windows.containsKey(44L))
                nativeDestroyCalls += 1
            },
            surfaceFlusher = { 0 },
        )
        loop.registerWindow(window)
        val events = mutableListOf<WindowEvent>()

        loop.requestRedraw(window.id)
        window.close()
        window.close()
        loop.enqueueWindowEvent(window.id, WindowEvent.Focused(true))

        assertEquals(0, nativeDestroyCalls)
        assertTrue(loop.windows.containsKey(window.id.value))
        assertEquals(3, wakeup.signalCalls)
        dispatchWaylandIteration(loop, recordingHandler(events = events), StartCause.Poll)

        assertEquals(1, nativeDestroyCalls)
        assertTrue(events.isEmpty())
        dispatchWaylandIteration(loop, recordingHandler(events = events), StartCause.Poll)
        assertEquals(listOf<WindowEvent>(WindowEvent.Destroyed), events)
        assertFalse(loop.requestRedraw(window.id))
    }

    @Test
    fun `public close called from another thread only destroys on loop dispatch`() {
        val wakeup = RecordingWakeup()
        val loop = testLoop(wakeup)
        val trace = mutableListOf<String>()
        val window = WaylandWindow.createForTest(
            surface = 53L,
            surfaceProxyDestroyer = {
                assertFalse(loop.windows.containsKey(53L))
                trace += Thread.currentThread().name
            },
            surfaceFlusher = { 0 },
        )
        loop.registerWindow(window)

        val caller = Thread({ window.close() }, "wayland-close-caller")
        caller.start()
        caller.join()

        assertTrue(trace.isEmpty())
        assertTrue(loop.windows.containsKey(window.id.value))
        assertEquals(1, wakeup.signalCalls)

        dispatchWaylandIteration(loop, recordingHandler(), StartCause.Poll)

        assertEquals(listOf(Thread.currentThread().name), trace)
        assertFalse(loop.windows.containsKey(window.id.value))
    }

    @Test
    fun `compositor close uses the same terminal close path`() {
        val loop = testLoop(RecordingWakeup())
        var nativeDestroyCalls = 0
        val window = WaylandWindow.createForTest(
            surface = 45L,
            surfaceProxyDestroyer = { nativeDestroyCalls += 1 },
            surfaceFlusher = { 0 },
        )
        loop.registerWindow(window)
        val events = mutableListOf<WindowEvent>()

        window.handleXdgToplevelClose()
        assertEquals(0, nativeDestroyCalls)
        dispatchWaylandIteration(loop, recordingHandler(events = events), StartCause.Poll)

        assertEquals(1, nativeDestroyCalls)
        assertTrue(events.isEmpty())
        dispatchWaylandIteration(loop, recordingHandler(events = events), StartCause.Poll)
        assertEquals(listOf<WindowEvent>(WindowEvent.Destroyed), events)
    }

    @Test
    fun `xdg close cleanup failure is queued without crossing the native callback`() {
        val loop = testLoop(RecordingWakeup())
        val nativeFailure = IllegalStateException("surface cleanup")
        val window = WaylandWindow.createForTest(
            surface = 51L,
            surfaceProxyDestroyer = { throw nativeFailure },
        )
        loop.registerWindow(window)
        val callbacks = XdgUpcallCallbacks(
            onResized = { _, _, _ -> },
            onStateConfigured = {},
            onClose = window::handleXdgToplevelClose,
            onFailure = { error("close enqueue must not fail") },
        )

        callbacks.close()
        assertTrue(loop.windows.containsKey(window.id.value))

        val thrown = assertFailsWith<IllegalStateException> {
            dispatchWaylandIteration(loop, recordingHandler(), StartCause.Poll)
        }
        assertSame(nativeFailure, thrown)
        val events = mutableListOf<WindowEvent>()
        dispatchWaylandIteration(loop, recordingHandler(events = events), StartCause.Poll)
        assertEquals(listOf<WindowEvent>(WindowEvent.Destroyed), events)
    }

    @Test
    fun `close clears frame callback before destroying the surface`() {
        val loop = testLoop(RecordingWakeup())
        val trace = mutableListOf<String>()
        val window = WaylandWindow.createForTest(
            surface = 52L,
            surfaceProxyDestroyer = { trace += "surface-$it" },
            surfaceFlusher = { 0 },
        )
        window.replaceFrameCallback(520L) { trace += "frame-$it" }
        loop.registerWindow(window)

        window.close()
        window.close()

        assertTrue(trace.isEmpty())
        dispatchWaylandIteration(loop, recordingHandler(), StartCause.Poll)
        assertEquals(listOf("frame-520", "surface-52"), trace)
    }

    @Test
    fun `startup failure reports only Wayland display context operation and native cause`() {
        val cause = IllegalArgumentException("native errno=2")

        val absent = waylandStartupFailure(
            operation = "wl_display_connect",
            display = null,
            cause = cause,
        )
        val named = waylandStartupFailure(
            operation = "discover globals",
            display = "wayland-test-9",
            cause = cause,
        )

        assertTrue(absent.message.orEmpty().contains("backend=Wayland"))
        assertTrue(absent.message.orEmpty().contains("WAYLAND_DISPLAY=<absent>"))
        assertTrue(absent.message.orEmpty().contains("operation=wl_display_connect"))
        assertTrue(absent.message.orEmpty().contains("native errno=2"))
        assertFalse(absent.message.orEmpty().contains("HOME="))
        assertFalse(absent.message.orEmpty().contains("PATH="))
        assertSame(cause, absent.cause)
        assertTrue(named.message.orEmpty().contains("WAYLAND_DISPLAY=wayland-test-9"))
    }

    @Test
    fun `required compositor and xdg wm base are validated before lifecycle`() {
        val compositor = assertFailsWith<IllegalStateException> {
            requireWaylandGlobals(WaylandGlobals(compositorPtr = 0L, xdgWmBasePtr = 2L))
        }
        val shell = assertFailsWith<IllegalStateException> {
            requireWaylandGlobals(WaylandGlobals(compositorPtr = 1L, xdgWmBasePtr = 0L))
        }

        assertTrue(compositor.message.orEmpty().contains("wl_compositor"))
        assertTrue(shell.message.orEmpty().contains("xdg_wm_base"))
    }

    private fun recordingHandler(
        events: MutableList<WindowEvent> = mutableListOf(),
        trace: MutableList<String> = mutableListOf(),
    ): ApplicationHandler = object : ApplicationHandler {
        override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

        override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
            trace += "newEvents"
        }

        override fun windowEvent(
            eventLoop: ActiveEventLoop,
            windowId: WindowId,
            event: WindowEvent,
        ) {
            events += event
            trace += "event-${event::class.simpleName}"
        }

        override fun aboutToWait(eventLoop: ActiveEventLoop) {
            trace += "aboutToWait"
        }
    }

    private fun testLoop(wakeup: PosixWakeup): WaylandEventLoop = WaylandEventLoop(
        displayPtr = 1L,
        compositorPtr = 2L,
        xdgWmBasePtr = 3L,
        shmPtr = 4L,
        wakeup = wakeup,
    )
}

private class RecordingWakeup(
    private val onSignal: () -> Unit = {},
    var signalResult: Boolean = true,
    var signalFailure: Throwable? = null,
) : PosixWakeup {
    override val readFd: Int = 7
    var signalCalls: Int = 0
        private set

    override fun signal(): Boolean {
        signalCalls += 1
        onSignal()
        signalFailure?.let { throw it }
        return signalResult
    }

    override fun drain(): Boolean = true

    override fun close() = Unit
}
