package org.graphiks.kadre.x11

import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import org.graphiks.kadre.ffi.posix.PosixWakeup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class X11LoopContractTest {
    @Test
    fun `startup lifecycle reaches about to wait before the first native pump`() {
        val loop = testLoop(RecordingX11Wakeup(), FakeX11NativeAdapter())
        val trace = mutableListOf<String>()

        startX11Lifecycle(loop, recordingHandler(trace = trace))

        assertEquals(
            listOf(
                "resumed",
                "newEvents-${StartCause.Init}",
                "canCreateSurfaces",
                "aboutToWait",
            ),
            trace,
        )
    }

    @Test
    fun `iteration starts after pump then dispatches queued events before about to wait and poll`() {
        val loop = testLoop(RecordingX11Wakeup(), FakeX11NativeAdapter())
        val window = loop.createWindow(WindowAttributes(title = "iteration"))
        val trace = mutableListOf<String>()

        trace += "pump-${StartCause.WaitCancelled()}"
        assertTrue(loop.enqueueWindowEvent(window.id, WindowEvent.Focused(true)))
        dispatchX11Iteration(loop, recordingHandler(trace = trace), StartCause.WaitCancelled())
        trace += "poll"

        assertEquals(
            listOf(
                "pump-${StartCause.WaitCancelled()}",
                "newEvents-${StartCause.WaitCancelled()}",
                "event-Focused",
                "aboutToWait",
                "poll",
            ),
            trace,
        )
    }

    @Test
    fun `ten redraw requests coalesce to one event and one idle wake`() {
        val wakeup = RecordingX11Wakeup()
        val loop = testLoop(wakeup, FakeX11NativeAdapter())
        val window = loop.createWindow(WindowAttributes(title = "redraw"))
        val events = mutableListOf<WindowEvent>()

        repeat(10) { window.requestRedraw() }

        assertEquals(1, wakeup.signalCalls)
        dispatchX11Iteration(loop, recordingHandler(events = events), StartCause.WaitCancelled())
        assertEquals(listOf<WindowEvent>(WindowEvent.RedrawRequested), events)
    }

    @Test
    fun `native expose coalesces a pending requested redraw`() {
        val wakeup = RecordingX11Wakeup()
        val loop = testLoop(wakeup, FakeX11NativeAdapter())
        val window = loop.createWindow(WindowAttributes(title = "expose"))
        val events = mutableListOf<WindowEvent>()

        window.requestRedraw()
        assertTrue(loop.enqueueExpose(window.id))
        dispatchX11Iteration(loop, recordingHandler(events = events), StartCause.WaitCancelled())

        assertEquals(1, wakeup.signalCalls)
        assertEquals(listOf<WindowEvent>(WindowEvent.RedrawRequested), events)
    }

    @Test
    fun `pending redraw IDs retain first insertion order`() {
        val loop = testLoop(RecordingX11Wakeup(), FakeX11NativeAdapter())
        val first = loop.createWindow(WindowAttributes(title = "first"))
        val second = loop.createWindow(WindowAttributes(title = "second"))
        val redrawIds = mutableListOf<WindowId>()
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event == WindowEvent.RedrawRequested) redrawIds += windowId
            }
        }

        second.requestRedraw()
        first.requestRedraw()
        second.requestRedraw()
        dispatchX11Iteration(loop, handler, StartCause.WaitCancelled())

        assertEquals(listOf(second.id, first.id), redrawIds)
    }

    @Test
    fun `close removes before native destroy clears queued work and emits one terminal event`() {
        val wakeup = RecordingX11Wakeup()
        lateinit var loop: X11EventLoop
        val loopThread = Thread.currentThread()
        val native = FakeX11NativeAdapter(
            onDestroy = { xid ->
                assertEquals(41L, xid)
                assertFalse(loop.windows.containsKey(xid))
                assertEquals(loopThread, Thread.currentThread())
            },
        )
        loop = testLoop(wakeup, native)
        val window = loop.createWindow(WindowAttributes(title = "close"))
        val events = mutableListOf<WindowEvent>()

        val caller = Thread({
            window.requestRedraw()
            window.close()
            window.close()
        }, "x11-window-caller")
        caller.start()
        caller.join()
        assertTrue(loop.enqueueWindowEvent(window.id, WindowEvent.Focused(true)))
        loop.dragSourceWindows[window.id.value] = 900L
        loop.pendingDropRequests[window.id.value] = PhysicalPosition(1.0, 2.0)
        loop.pendingXdndDrops.add(
            PendingXdndDrop(
                targetWindow = window.id.value,
                sourceWindow = 900L,
                position = PhysicalPosition(1.0, 2.0),
            ),
        )

        assertTrue(loop.windows.containsKey(window.id.value))
        assertTrue(native.trace.isEmpty())
        assertEquals(2, wakeup.signalCalls)
        dispatchX11Iteration(loop, recordingHandler(events = events), StartCause.WaitCancelled())

        assertEquals(listOf("destroy-41", "flush"), native.trace)
        assertEquals(listOf<WindowEvent>(WindowEvent.Destroyed), events)
        assertFalse(loop.windows.containsKey(window.id.value))
        assertFalse(loop.dragSourceWindows.containsKey(window.id.value))
        assertFalse(loop.pendingDropRequests.containsKey(window.id.value))
        assertTrue(loop.pendingXdndDrops.none { it.targetWindow == window.id.value })

        events.clear()
        assertFalse(loop.nativeWindowDestroyed(window.id))
        dispatchX11Iteration(loop, recordingHandler(events = events), StartCause.Poll)
        assertTrue(events.isEmpty())
        assertEquals(listOf("destroy-41", "flush"), native.trace)
    }

    @Test
    fun `shutdown destroys surfaces closes every window suspends then closes display`() {
        val native = FakeX11NativeAdapter()
        val loop = testLoop(RecordingX11Wakeup(), native)
        val first = loop.createWindow(WindowAttributes(title = "first"))
        loop.createWindow(WindowAttributes(title = "second"))
        val trace = mutableListOf<String>()
        native.traceSink = trace
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) = Unit

            override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                trace += "destroySurfaces"
                first.close()
            }

            override fun suspended(eventLoop: ActiveEventLoop) {
                assertTrue(loop.windows.isEmpty())
                trace += "suspended"
            }
        }

        shutdownX11Lifecycle(loop, handler)

        assertEquals(
            listOf(
                "destroySurfaces",
                "destroy-41", "flush",
                "destroy-42", "flush",
                "suspended",
                "closeDisplay",
            ),
            trace,
        )
    }

    @Test
    fun `shutdown still closes windows suspends and closes display after callback failure`() {
        val native = FakeX11NativeAdapter()
        val loop = testLoop(RecordingX11Wakeup(), native)
        loop.createWindow(WindowAttributes(title = "remaining"))
        val trace = mutableListOf<String>()
        val callbackFailure = IllegalStateException("destroy surfaces")
        native.traceSink = trace
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) = Unit

            override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                trace += "destroySurfaces"
                throw callbackFailure
            }

            override fun suspended(eventLoop: ActiveEventLoop) {
                trace += "suspended"
            }
        }

        val thrown = assertFailsWith<IllegalStateException> {
            shutdownX11Lifecycle(loop, handler)
        }

        assertSame(callbackFailure, thrown)
        assertEquals(
            listOf("destroySurfaces", "destroy-41", "flush", "suspended", "closeDisplay"),
            trace,
        )
        assertTrue(loop.windows.isEmpty())
    }

    @Test
    fun `events for unknown and closed XIDs are discarded`() {
        val loop = testLoop(RecordingX11Wakeup(), FakeX11NativeAdapter())
        val window = loop.createWindow(WindowAttributes(title = "known"))
        val events = mutableListOf<WindowEvent>()

        assertFalse(loop.enqueueWindowEvent(WindowId(999L), WindowEvent.Focused(true)))
        window.close()
        dispatchX11Iteration(loop, recordingHandler(events = events), StartCause.WaitCancelled())
        assertFalse(loop.enqueueWindowEvent(window.id, WindowEvent.Focused(false)))
        dispatchX11Iteration(loop, recordingHandler(events = events), StartCause.Poll)

        assertEquals(listOf<WindowEvent>(WindowEvent.Destroyed), events)
    }

    private fun recordingHandler(
        events: MutableList<WindowEvent> = mutableListOf(),
        trace: MutableList<String> = mutableListOf(),
    ): ApplicationHandler = object : ApplicationHandler {
        override fun resumed(eventLoop: ActiveEventLoop) {
            trace += "resumed"
        }

        override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
            trace += "newEvents-$startCause"
        }

        override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
            trace += "canCreateSurfaces"
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

    private fun testLoop(
        wakeup: PosixWakeup,
        native: X11NativeAdapter,
    ): X11EventLoop = X11EventLoop(
        displayPtr = 1L,
        screen = 0,
        wakeup = wakeup,
        nativeAdapter = native,
    )
}

private class RecordingX11Wakeup : PosixWakeup {
    override val readFd: Int = 7
    var signalCalls: Int = 0
        private set

    override fun signal(): Boolean {
        signalCalls += 1
        return true
    }

    override fun drain(): Boolean = true

    override fun close() = Unit
}

private class FakeX11NativeAdapter(
    private val onDestroy: (Long) -> Unit = {},
) : X11NativeAdapter {
    private var nextXid = 41L
    val trace = mutableListOf<String>()
    var traceSink: MutableList<String> = trace

    override fun createWindow(
        loop: X11EventLoop,
        attributes: WindowAttributes,
    ): X11Window = X11Window(
        displayPtr = loop.displayPtr,
        screen = loop.screen,
        xWindowId = nextXid++,
        attrs = attributes,
        owner = loop,
        initialScaleFactor = 1.0,
    )

    override fun destroyWindow(displayPtr: Long, windowId: Long) {
        onDestroy(windowId)
        traceSink += "destroy-$windowId"
    }

    override fun flush(displayPtr: Long) {
        traceSink += "flush"
    }

    override fun closeDisplay(displayPtr: Long) {
        traceSink += "closeDisplay"
    }
}
