package org.graphiks.kadre.wayland

import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.DeviceEvent
import org.graphiks.kadre.core.DeviceId
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import org.graphiks.kadre.ffi.posix.PosixWakeup
import java.lang.foreign.MemorySegment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WaylandLoopContractTest {
    @Test
    fun `device event is queued until after new events and handler failure stays on Kotlin loop`() {
        val loop = testLoop(RecordingWakeup())
        val trace = mutableListOf<String>()
        val handlerFailure = IllegalStateException("device handler")
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) = Unit
            override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                trace += "newEvents"
            }
            override fun deviceEvent(
                eventLoop: ActiveEventLoop,
                deviceId: DeviceId,
                event: DeviceEvent,
            ) {
                trace += "device"
                throw handlerFailure
            }
        }

        loop.enqueueDeviceEvent(DeviceEvent.PointerMotion(1.0, 2.0))
        assertTrue(trace.isEmpty())

        val thrown = assertFailsWith<IllegalStateException> {
            dispatchWaylandIteration(loop, handler, StartCause.Poll)
        }

        assertSame(handlerFailure, thrown)
        assertEquals(listOf("newEvents", "device"), trace)
    }

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
    fun `concurrent redraw survives first publisher wake rollback`() {
        val wakeup = BlockingFirstWakeup()
        val loop = testLoop(wakeup)
        val window = WaylandWindow.createForTest(surface = 56L)
        loop.registerWindow(window)
        var firstFailure: Throwable? = null
        var secondResult = false
        val secondStarted = CountDownLatch(1)

        val first = Thread({
            try {
                loop.requestRedraw(window.id)
            } catch (failure: Throwable) {
                firstFailure = failure
            }
        }, "redraw-first")
        first.start()
        wakeup.firstSignalEntered.await()

        val second = Thread({
            secondStarted.countDown()
            secondResult = loop.requestRedraw(window.id)
        }, "redraw-second")
        second.start()
        secondStarted.await()
        wakeup.releaseFirstSignal.countDown()
        first.join()
        second.join()

        assertTrue(firstFailure?.message.orEmpty().contains("redraw wake failed"))
        assertTrue(secondResult)
        assertEquals(2, wakeup.signalCalls.get())
        val events = mutableListOf<WindowEvent>()
        dispatchWaylandIteration(loop, recordingHandler(events = events), StartCause.Poll)
        assertEquals(listOf<WindowEvent>(WindowEvent.RedrawRequested), events)
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
    fun `surface id reuse in new events rejects stale events and destroyed from old owner`() {
        val loop = testLoop(RecordingWakeup())
        val oldWindow = WaylandWindow.createForTest(
            surface = 57L,
            surfaceProxyDestroyer = {},
            surfaceFlusher = { 0 },
        )
        loop.registerWindow(oldWindow)
        loop.enqueueWindowEvent(oldWindow.id, WindowEvent.Focused(true))
        assertTrue(loop.closeWindow(oldWindow.id))
        val newWindow = WaylandWindow.createForTest(surface = 57L)
        val events = mutableListOf<WindowEvent>()
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
            override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                loop.registerWindow(newWindow)
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
        assertSame(newWindow, loop.windows[57L])
    }

    @Test
    fun `initial xdg resize buffered before registration publishes under the new owner`() {
        val loop = testLoop(RecordingWakeup())
        val window = WaylandWindow.createForTest(xdgWmBase = 1L, surface = 59L)

        window.handleXdgResize(width = 640, height = 480, applyResizeIncrements = true)
        loop.registerWindow(window)
        val events = mutableListOf<WindowEvent>()

        dispatchWaylandIteration(loop, recordingHandler(events = events), StartCause.Poll)

        assertEquals(
            listOf<WindowEvent>(WindowEvent.Resized(org.graphiks.kadre.core.PhysicalSize(640, 480))),
            events,
        )
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
    fun `unreturned window rolls back registration and native resources when initial redraw fails`() {
        val loop = testLoop(RecordingWakeup(signalResult = false))
        val nativeFailure = IllegalArgumentException("rollback surface")
        var destroyCalls = 0
        val window = WaylandWindow.createForTest(
            surface = 58L,
            surfaceProxyDestroyer = {
                destroyCalls += 1
                throw nativeFailure
            },
        )

        val thrown = assertFailsWith<IllegalStateException> {
            loop.adoptCreatedWindow(window)
        }

        assertEquals("Wayland redraw wake failed", thrown.message)
        assertEquals(listOf(nativeFailure), thrown.suppressed.toList())
        assertEquals(1, destroyCalls)
        assertFalse(loop.windows.containsKey(window.id.value))
        val events = mutableListOf<WindowEvent>()
        dispatchWaylandIteration(loop, recordingHandler(events = events), StartCause.Poll)
        assertTrue(events.isEmpty())
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
        val events = mutableListOf<WindowEvent>()
        dispatchWaylandIteration(loop, recordingHandler(events = events), StartCause.Poll)

        assertEquals(2, wakeup.signalCalls)
        assertEquals(1, nativeDestroyCalls)
        assertFalse(loop.windows.containsKey(window.id.value))
        assertEquals(listOf<WindowEvent>(WindowEvent.Destroyed), events)
        events.clear()
        dispatchWaylandIteration(loop, recordingHandler(events = events), StartCause.Poll)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `xdg close wake failure propagates only after close and destroyed`() {
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

        assertTrue(failures.isEmpty())
        assertTrue(loop.windows.containsKey(window.id.value))
        assertEquals(0, nativeDestroyCalls)

        val events = mutableListOf<WindowEvent>()
        val failure = assertFailsWith<IllegalStateException> {
            dispatchWaylandIteration(loop, recordingHandler(events = events), StartCause.Poll)
        }

        assertEquals("Wayland close wake failed", failure.message)
        assertEquals(1, nativeDestroyCalls)
        assertFalse(loop.windows.containsKey(window.id.value))
        assertEquals(listOf<WindowEvent>(WindowEvent.Destroyed), events)
        events.clear()
        dispatchWaylandIteration(loop, recordingHandler(events = events), StartCause.Poll)
        assertTrue(events.isEmpty())
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
        assertEquals(listOf<WindowEvent>(WindowEvent.Destroyed), events)
        events.clear()
        dispatchWaylandIteration(loop, recordingHandler(events = events), StartCause.Poll)
        assertTrue(events.isEmpty())
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
        assertEquals(listOf<WindowEvent>(WindowEvent.Destroyed), events)
        events.clear()
        dispatchWaylandIteration(loop, recordingHandler(events = events), StartCause.Poll)
        assertTrue(events.isEmpty())
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

        val events = mutableListOf<WindowEvent>()
        val thrown = assertFailsWith<IllegalStateException> {
            dispatchWaylandIteration(loop, recordingHandler(events = events), StartCause.Poll)
        }
        assertSame(nativeFailure, thrown)
        assertEquals(listOf<WindowEvent>(WindowEvent.Destroyed), events)
        events.clear()
        dispatchWaylandIteration(loop, recordingHandler(events = events), StartCause.Poll)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `close batch destroys every window delivers destroyed then throws aggregated cleanup`() {
        val loop = testLoop(RecordingWakeup())
        val firstFailure = IllegalStateException("first cleanup")
        val secondFailure = IllegalArgumentException("second cleanup")
        val first = WaylandWindow.createForTest(
            surface = 60L,
            surfaceProxyDestroyer = { throw firstFailure },
        )
        val second = WaylandWindow.createForTest(
            surface = 61L,
            surfaceProxyDestroyer = { throw secondFailure },
        )
        loop.registerWindow(first)
        loop.registerWindow(second)
        val terminal = mutableListOf<Pair<WindowId, WindowEvent>>()
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                terminal += windowId to event
            }
        }

        first.close()
        second.close()
        val thrown = assertFailsWith<IllegalStateException> {
            dispatchWaylandIteration(loop, handler, StartCause.Poll)
        }

        assertSame(firstFailure, thrown)
        assertEquals(listOf(secondFailure), thrown.suppressed.toList())
        assertEquals(
            listOf<Pair<WindowId, WindowEvent>>(
                first.id to WindowEvent.Destroyed,
                second.id to WindowEvent.Destroyed,
            ),
            terminal,
        )
        assertTrue(loop.windows.isEmpty())

        dispatchWaylandIteration(loop, handler, StartCause.Poll)
        assertEquals(2, terminal.size)
    }

    @Test
    fun `shutdown closes queued and live windows directly before connection cleanup`() {
        val loop = testLoop(RecordingWakeup())
        val firstFailure = IllegalStateException("shutdown first")
        val secondFailure = IllegalArgumentException("shutdown second")
        var firstDestroyCalls = 0
        var secondDestroyCalls = 0
        val first = WaylandWindow.createForTest(
            surface = 62L,
            surfaceProxyDestroyer = {
                firstDestroyCalls += 1
                throw firstFailure
            },
        )
        val second = WaylandWindow.createForTest(
            surface = 63L,
            surfaceProxyDestroyer = {
                secondDestroyCalls += 1
                throw secondFailure
            },
        )
        loop.registerWindow(first)
        loop.registerWindow(second)
        first.close()

        val thrown = assertFailsWith<IllegalStateException> {
            loop.closeAllWindowsDirect()
        }

        assertSame(firstFailure, thrown)
        assertEquals(listOf(secondFailure), thrown.suppressed.toList())
        assertEquals(1, firstDestroyCalls)
        assertEquals(1, secondDestroyCalls)
        assertTrue(loop.windows.isEmpty())
        assertFalse(loop.eventQueue.any { it is WaylandQueuedCloseCommand })
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
    fun `startup native steps are contextualized while application callbacks stay outside wrapper`() {
        val nativeFailure = IllegalArgumentException("seat listener rc=-1")
        val wrapped = assertFailsWith<IllegalStateException> {
            runWaylandStartupOperation("install seat listeners", "wayland-test-10") {
                throw nativeFailure
            }
        }

        assertTrue(wrapped.message.orEmpty().contains("operation=install seat listeners"))
        assertSame(nativeFailure, wrapped.cause)

        val callbackFailure = UnsupportedOperationException("application callback")
        val callback = runWaylandStartupOperation("create text input", "wayland-test-10") {
            { throw callbackFailure }
        }
        assertSame(callbackFailure, assertFailsWith { callback() })
    }

    @Test
    fun `strict display disconnect and wake cleanup preserve every failure`() {
        val disconnectFailure = IllegalArgumentException("disconnect")
        assertSame(
            disconnectFailure,
            assertFailsWith {
                disconnectWaylandDisplay(MemorySegment.NULL) { throw disconnectFailure }
            },
        )

        val wakeFailure = IllegalStateException("wake close")
        val cleanupFailure = assertFailsWith<IllegalStateException> {
            closeWaylandResources(
                closeWakeup = { throw wakeFailure },
                disconnectDisplay = { throw disconnectFailure },
            )
        }
        assertSame(wakeFailure, cleanupFailure)
        assertEquals(listOf(disconnectFailure), cleanupFailure.suppressed.toList())
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

    @Test
    fun `startup lifecycle reaches about to wait before the first native pump`() {
        val loop = testLoop(RecordingWakeup())
        val trace = mutableListOf<String>()
        val handler = object : ApplicationHandler {
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
            ) = Unit
            override fun aboutToWait(eventLoop: ActiveEventLoop) {
                trace += "aboutToWait"
            }
        }

        startWaylandLifecycle(loop, handler)
        trace += "pump"

        assertEquals(
            listOf(
                "resumed",
                "newEvents-${StartCause.Init}",
                "canCreateSurfaces",
                "aboutToWait",
                "pump",
            ),
            trace,
        )
    }

    @Test
    fun `shutdown closes windows after destroy surfaces failure before suspended`() {
        val loop = testLoop(RecordingWakeup())
        val trace = mutableListOf<String>()
        val destroySurfacesFailure = IllegalStateException("destroy surfaces")
        val nativeFailure = IllegalArgumentException("native close")
        val window = WaylandWindow.createForTest(
            surface = 64L,
            surfaceProxyDestroyer = {
                trace += "native-close"
                throw nativeFailure
            },
        )
        loop.registerWindow(window)
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) = Unit
            override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                trace += "destroySurfaces"
                window.close()
                throw destroySurfacesFailure
            }
            override fun suspended(eventLoop: ActiveEventLoop) {
                assertTrue(loop.windows.isEmpty())
                trace += "suspended"
            }
        }

        val thrown = assertFailsWith<IllegalStateException> {
            shutdownWaylandLifecycle(loop, handler)
        }

        assertSame(destroySurfacesFailure, thrown)
        assertEquals(listOf(nativeFailure), thrown.suppressed.toList())
        assertEquals(listOf("destroySurfaces", "native-close", "suspended"), trace)
        assertTrue(loop.windows.isEmpty())
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

private class BlockingFirstWakeup : PosixWakeup {
    override val readFd: Int = 8
    val firstSignalEntered = CountDownLatch(1)
    val releaseFirstSignal = CountDownLatch(1)
    val signalCalls = AtomicInteger()

    override fun signal(): Boolean {
        val call = signalCalls.incrementAndGet()
        if (call == 1) {
            firstSignalEntered.countDown()
            releaseFirstSignal.await()
            return false
        }
        return true
    }

    override fun drain(): Boolean = true
    override fun close() = Unit
}
