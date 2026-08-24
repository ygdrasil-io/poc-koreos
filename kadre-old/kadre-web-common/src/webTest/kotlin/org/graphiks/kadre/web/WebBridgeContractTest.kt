package org.graphiks.kadre.web

import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebBridgeContractTest {

    @Test
    fun `one shared bridge adapter normalizes the complete DOM trace and resets after detach`() {
        var metrics = CanvasMetrics(
            leftCss = 10.0,
            topCss = 20.0,
            widthCss = 300.0,
            heightCss = 150.0,
            devicePixelRatio = 2.0,
        )
        var metricsReads = 0
        val events = mutableListOf<WebWindowEvent>()
        val transactions = mutableListOf<WebMetricsTransaction>()
        val adapter = WebBridgeEventAdapter(
            metricsProvider = {
                metricsReads += 1
                metrics
            },
            eventSink = events::add,
            metricsSink = { _, transaction -> transactions += transaction },
        )

        val token = adapter.attach()
        adapter.pointer(token, "pointermove", 9.75, 19.5, 5L, "mouse", true, 0)
        adapter.pointer(token, "pointerenter", 11.0, 21.0, 5L, "mouse", true, 0)
        adapter.pointer(token, "pointerleave", 12.0, 22.0, 5L, "mouse", true, 0)
        adapter.pointer(token, "pointerdown", 20.5, 30.25, 5L, "mouse", true, 0)
        adapter.pointer(token, "pointerup", 30.25, 40.5, 5L, "mouse", true, 0)
        adapter.touches(
            token,
            WebTouchPhase.Started,
            listOf(
                WebTouchContact(id = 42L, clientX = 10.25, clientY = 20.5),
                WebTouchContact(id = 7L, clientX = 14.0, clientY = 25.0),
            ),
        )
        adapter.wheel(
            token = token,
            deltaX = 0.0,
            deltaY = -25.0,
            deltaMode = 0,
            ctrlKey = true,
            clientX = 15.25,
            clientY = 27.5,
        )
        adapter.dragEntered(token, 12.5, 24.5, listOf("image/png"))
        adapter.dragMoved(token, 13.5, 25.5)
        adapter.dragDropped(token, 14.5, 26.5, listOf("sprite.png"))
        adapter.resized(token)

        metrics = metrics.copy(devicePixelRatio = 3.0)
        adapter.devicePixelRatioChanged(token)

        val readsBeforeDetach = metricsReads
        val eventCountBeforeDetach = events.size
        val transactionCountBeforeDetach = transactions.size
        adapter.detach()
        adapter.pointer(token, "pointermove", 100.0, 100.0, 5L, "mouse", true, 0)
        adapter.resized(token)
        adapter.devicePixelRatioChanged(token)

        val reattachedToken = adapter.attach()
        adapter.touches(
            reattachedToken,
            WebTouchPhase.Started,
            listOf(WebTouchContact(id = 7L, clientX = 11.0, clientY = 21.0)),
        )

        assertEquals(readsBeforeDetach + 1, metricsReads, "detached callbacks must not read stale DOM state")
        assertEquals(eventCountBeforeDetach + 1, events.size, "only the reattached touch may emit")
        assertEquals(transactionCountBeforeDetach, transactions.size, "detached metrics callbacks must not emit")

        assertEquals(-0.5, assertIs<WebWindowEvent.PointerMoved>(events[0]).x)
        assertEquals(-1.0, assertIs<WebWindowEvent.PointerMoved>(events[0]).y)
        assertPosition(events[1], 2.0, 2.0)
        assertPosition(events[2], 4.0, 4.0)
        assertPosition(events[3], 21.0, 20.5)
        assertPosition(events[4], 40.5, 41.0)

        val firstTouch = assertIs<WebWindowEvent.Touch>(events[5])
        assertEquals(42L, firstTouch.id)
        assertEquals(true, firstTouch.primary)
        assertEquals(0.5, firstTouch.x)
        assertEquals(1.0, firstTouch.y)
        val secondTouch = assertIs<WebWindowEvent.Touch>(events[6])
        assertEquals(7L, secondTouch.id)
        assertEquals(false, secondTouch.primary)
        assertEquals(8.0, secondTouch.x)
        assertEquals(10.0, secondTouch.y)

        val pinch = assertIs<WebWindowEvent.WebPinchZoom>(events[7])
        assertEquals(10.5, pinch.centerX)
        assertEquals(15.0, pinch.centerY)
        assertPosition(events[8], 5.0, 9.0)
        assertPosition(events[9], 7.0, 11.0)
        assertPosition(events[10], 9.0, 13.0)

        assertEquals(WebWindowEvent.Resized(600, 300), events[11])
        assertEquals(listOf(WebMetricsTransaction(3.0, PhysicalSize(900, 450))), transactions)

        val reattachedTouch = assertIs<WebWindowEvent.Touch>(events.last())
        assertEquals(7L, reattachedTouch.id)
        assertTrue(reattachedTouch.primary, "detach must clear the legacy touch primary state")

        // 5 pointer + 1 multi-touch + 1 wheel + 3 drag + 1 resize + 1 DPR + 1 reattached touch.
        assertEquals(13, metricsReads)
    }

    @Test
    fun `callbacks captured by an old attachment remain stale after reattach`() {
        var metricsReads = 0
        val events = mutableListOf<WebWindowEvent>()
        val transactions = mutableListOf<WebMetricsTransaction>()
        val adapter = WebBridgeEventAdapter(
            metricsProvider = {
                metricsReads += 1
                CanvasMetrics(10.0, 20.0, 300.0, 150.0, 2.0)
            },
            eventSink = events::add,
            metricsSink = { _, transaction -> transactions += transaction },
        )

        val oldToken = adapter.attach()
        adapter.touches(
            oldToken,
            WebTouchPhase.Started,
            listOf(WebTouchContact(id = 42L, clientX = 11.0, clientY = 21.0)),
        )
        val oldPointerCallback = {
            adapter.pointer(oldToken, "pointermove", 12.0, 22.0, 5L, "mouse", true, 0)
        }
        val oldResizeCallback = { adapter.resized(oldToken) }
        val oldDprCallback = { adapter.devicePixelRatioChanged(oldToken) }

        adapter.detach()
        val newToken = adapter.attach()
        val readsBeforeOldCallbacks = metricsReads
        val eventsBeforeOldCallbacks = events.size
        val transactionsBeforeOldCallbacks = transactions.size

        oldPointerCallback()
        oldResizeCallback()
        oldDprCallback()

        assertEquals(readsBeforeOldCallbacks, metricsReads)
        assertEquals(eventsBeforeOldCallbacks, events.size)
        assertEquals(transactionsBeforeOldCallbacks, transactions.size)

        adapter.pointer(newToken, "pointermove", 12.0, 22.0, 5L, "mouse", true, 0)
        adapter.touches(
            newToken,
            WebTouchPhase.Started,
            listOf(WebTouchContact(id = 42L, clientX = 11.0, clientY = 21.0)),
        )
        assertIs<WebWindowEvent.PointerMoved>(events[eventsBeforeOldCallbacks])
        assertTrue(assertIs<WebWindowEvent.Touch>(events.last()).primary)
    }

    @Test
    fun `DPR transaction updates both window caches before either public callback`() {
        val bridge = ScriptedBridge()
        val loop = TestWebEventLoop(bridge)
        val window = loop.createWindow(WebWindowAttributes(canvasId = "canvas")) as WebWindow
        val observations = mutableListOf<CacheObservation>()
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
            override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) = Unit

            override fun windowEvent(eventLoop: ActiveEventLoop, windowId: WindowId, event: WindowEvent) {
                if (event is WindowEvent.ScaleFactorChanged || event is WindowEvent.Resized) {
                    observations += CacheObservation(event, window.scaleFactor, window.innerSize)
                }
            }
        }

        bridge.metrics = bridge.metrics.copy(devicePixelRatio = 3.0)
        bridge.emitDevicePixelRatioChanged()
        loop.pump(handler)

        assertEquals(2, observations.size)
        assertIs<WindowEvent.ScaleFactorChanged>(observations[0].event)
        assertIs<WindowEvent.Resized>(observations[1].event)
        assertEquals(
            listOf(
                CacheObservation(observations[0].event, 3.0, PhysicalSize(900, 450)),
                CacheObservation(observations[1].event, 3.0, PhysicalSize(900, 450)),
            ),
            observations,
        )

        val cleanupProbe = WebMetricsTransaction(4.0, PhysicalSize(1200, 600))
        assertTrue(
            WebMetricsTransactions.dispatch(bridge, cleanupProbe),
            "the loop must own a metrics sink before close",
        )
        window.close()
        assertFalse(
            WebMetricsTransactions.dispatch(bridge, cleanupProbe),
            "closing the window must release its internal metrics sink",
        )
    }

    @Test
    fun `queued DPR transaction drains before a later ordinary resize without premature cache mutation`() {
        val bridge = ScriptedBridge()
        val loop = TestWebEventLoop(bridge)
        val window = loop.createWindow(WebWindowAttributes(canvasId = "canvas")) as WebWindow
        val observations = mutableListOf<CacheObservation>()
        val handler = observingHandler(window, observations)

        bridge.metrics = bridge.metrics.copy(devicePixelRatio = 3.0)
        bridge.emitDevicePixelRatioChanged()
        bridge.emit(WebWindowEvent.Resized(1000, 500))

        assertEquals(2.0, window.scaleFactor, "queued events must not mutate the cache before drain")
        assertEquals(PhysicalSize(600, 300), window.innerSize)

        loop.pump(handler)

        assertEquals(
            listOf(
                CacheObservation(WindowEvent.ScaleFactorChanged(3.0), 3.0, PhysicalSize(900, 450)),
                CacheObservation(WindowEvent.Resized(PhysicalSize(900, 450)), 3.0, PhysicalSize(900, 450)),
                CacheObservation(WindowEvent.Resized(PhysicalSize(1000, 500)), 3.0, PhysicalSize(1000, 500)),
            ),
            observations,
        )
        assertEquals(3.0, window.scaleFactor)
        assertEquals(PhysicalSize(1000, 500), window.innerSize)
        window.close()
    }

    @Test
    fun `ordinary resize enqueued reentrantly cannot split an atomic DPR transaction`() {
        val bridge = ScriptedBridge()
        val loop = TestWebEventLoop(bridge)
        val window = loop.createWindow(WebWindowAttributes(canvasId = "canvas")) as WebWindow
        val observations = mutableListOf<CacheObservation>()
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
            override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) = Unit

            override fun windowEvent(eventLoop: ActiveEventLoop, windowId: WindowId, event: WindowEvent) {
                if (event is WindowEvent.ScaleFactorChanged) {
                    bridge.emit(WebWindowEvent.Resized(1000, 500))
                }
                if (event is WindowEvent.ScaleFactorChanged || event is WindowEvent.Resized) {
                    observations += CacheObservation(event, window.scaleFactor, window.innerSize)
                }
            }
        }

        bridge.metrics = bridge.metrics.copy(devicePixelRatio = 3.0)
        bridge.emitDevicePixelRatioChanged()
        loop.pump(handler)

        assertEquals(
            listOf(
                CacheObservation(WindowEvent.ScaleFactorChanged(3.0), 3.0, PhysicalSize(900, 450)),
                CacheObservation(WindowEvent.Resized(PhysicalSize(900, 450)), 3.0, PhysicalSize(900, 450)),
            ),
            observations,
        )
        assertEquals(PhysicalSize(900, 450), window.innerSize)

        loop.pump(handler)

        assertEquals(
            CacheObservation(WindowEvent.Resized(PhysicalSize(1000, 500)), 3.0, PhysicalSize(1000, 500)),
            observations.last(),
        )
        window.close()
    }

    @Test
    fun `older DPR transaction cannot overwrite a later ordinary scale factor event`() {
        val bridge = ScriptedBridge()
        val loop = TestWebEventLoop(bridge)
        val window = loop.createWindow(WebWindowAttributes(canvasId = "canvas")) as WebWindow
        val observations = mutableListOf<CacheObservation>()

        bridge.metrics = bridge.metrics.copy(devicePixelRatio = 3.0)
        bridge.emitDevicePixelRatioChanged()
        bridge.emit(WebWindowEvent.ScaleFactorChanged(4.0))

        assertEquals(2.0, window.scaleFactor, "queued events must not mutate the cache before drain")
        loop.pump(observingHandler(window, observations))

        assertEquals(
            listOf(
                CacheObservation(WindowEvent.ScaleFactorChanged(3.0), 3.0, PhysicalSize(900, 450)),
                CacheObservation(WindowEvent.Resized(PhysicalSize(900, 450)), 3.0, PhysicalSize(900, 450)),
                CacheObservation(WindowEvent.ScaleFactorChanged(4.0), 4.0, PhysicalSize(900, 450)),
            ),
            observations,
        )
        assertEquals(4.0, window.scaleFactor)
        window.close()
    }

    @Test
    fun `custom-equal bridges retain independent metrics routes`() {
        val first = EqualBridge()
        val second = EqualBridge()
        var firstDeliveries = 0
        var secondDeliveries = 0
        val transaction = WebMetricsTransaction(3.0, PhysicalSize(900, 450))

        val firstConnection = WebMetricsTransactions.connect(first) { firstDeliveries += 1 }
        val secondConnection = WebMetricsTransactions.connect(second) { secondDeliveries += 1 }
        try {
            assertTrue(WebMetricsTransactions.dispatch(first, transaction))
            assertTrue(WebMetricsTransactions.dispatch(second, transaction))
            assertEquals(1, firstDeliveries)
            assertEquals(1, secondDeliveries)
        } finally {
            WebMetricsTransactions.disconnect(firstConnection)
            WebMetricsTransactions.disconnect(secondConnection)
        }
    }

    @Test
    fun `mismatched local owner slot cannot cancel another bridge connection`() {
        val baseline = WebMetricsTransactions.connectionCount
        val first = ScriptedBridge()
        val second = ScriptedBridge()
        val firstConnection = WebMetricsTransactions.connect(first) { }
        val secondConnection = WebMetricsTransactions.connect(second) { }
        first.metricsConnection = secondConnection

        val replacement = WebMetricsTransactions.connect(first) { }

        assertEquals(WebMetricsConnection.State.Cancelled, firstConnection.state)
        assertEquals(WebMetricsConnection.State.Active, secondConnection.state)
        assertTrue(
            WebMetricsTransactions.dispatch(
                second,
                WebMetricsTransaction(3.0, PhysicalSize(900, 450)),
            ),
        )
        assertEquals(baseline + 2, WebMetricsTransactions.connectionCount)

        WebMetricsTransactions.disconnect(replacement)
        WebMetricsTransactions.disconnect(secondConnection)
        assertEquals(baseline, WebMetricsTransactions.connectionCount)
    }

    @Test
    fun `mismatched local owner slot cannot suspend or reactivate another bridge connection`() {
        val baseline = WebMetricsTransactions.connectionCount
        val first = ScriptedBridge()
        val second = ScriptedBridge()
        val firstConnection = WebMetricsTransactions.connect(first) { }
        val secondConnection = WebMetricsTransactions.connect(second) { }
        val transaction = WebMetricsTransaction(3.0, PhysicalSize(900, 450))

        first.metricsConnection = secondConnection
        first.detach()

        assertEquals(WebMetricsConnection.State.Suspended, firstConnection.state)
        assertEquals(WebMetricsConnection.State.Active, secondConnection.state)
        assertFalse(WebMetricsTransactions.dispatch(first, transaction))
        assertTrue(WebMetricsTransactions.dispatch(second, transaction))
        assertEquals(baseline + 1, WebMetricsTransactions.connectionCount)

        first.metricsConnection = secondConnection
        first.attach("first")

        assertEquals(WebMetricsConnection.State.Suspended, firstConnection.state)
        assertEquals(WebMetricsConnection.State.Active, secondConnection.state)
        assertFalse(WebMetricsTransactions.dispatch(first, transaction))
        assertTrue(WebMetricsTransactions.dispatch(second, transaction))
        assertEquals(baseline + 1, WebMetricsTransactions.connectionCount)

        WebMetricsTransactions.disconnect(firstConnection)
        WebMetricsTransactions.disconnect(secondConnection)
        assertEquals(baseline, WebMetricsTransactions.connectionCount)
    }

    @Test
    fun `a stale window cannot disconnect or detach a newer owner of the same bridge`() {
        val baseline = WebMetricsTransactions.connectionCount
        val bridge = ScriptedBridge()
        val loop = TestWebEventLoop(bridge)
        val oldWindow = loop.createWindow(WebWindowAttributes(canvasId = "old")) as WebWindow
        val newWindow = loop.createWindow(WebWindowAttributes(canvasId = "new")) as WebWindow
        val transaction = WebMetricsTransaction(3.0, PhysicalSize(900, 450))

        oldWindow.close()
        val detachCallsAfterStaleClose = bridge.detachCalls
        val newerRouteSurvived = WebMetricsTransactions.dispatch(bridge, transaction)
        val countAfterStaleClose = WebMetricsTransactions.connectionCount
        newWindow.close()

        assertEquals(0, detachCallsAfterStaleClose)
        assertTrue(newerRouteSurvived)
        assertEquals(baseline + 1, countAfterStaleClose)
        assertEquals(1, bridge.detachCalls)
        assertEquals(baseline, WebMetricsTransactions.connectionCount)
    }

    @Test
    fun `connection tokens disconnect only their exact active owner and are idempotent`() {
        val baseline = WebMetricsTransactions.connectionCount
        val bridge = ScriptedBridge()
        val oldConnection = WebMetricsTransactions.connect(bridge) { }
        val newConnection = WebMetricsTransactions.connect(bridge) { }

        assertFalse(WebMetricsTransactions.disconnect(oldConnection))
        assertEquals(baseline + 1, WebMetricsTransactions.connectionCount)
        assertTrue(WebMetricsTransactions.disconnect(newConnection))
        assertFalse(WebMetricsTransactions.disconnect(newConnection))
        assertEquals(baseline, WebMetricsTransactions.connectionCount)
    }

    @Test
    fun `direct bridge detach releases its active metrics route exactly once`() {
        val baseline = WebMetricsTransactions.connectionCount
        val bridge = ScriptedBridge()
        val connection = WebMetricsTransactions.connect(bridge) { }
        val transaction = WebMetricsTransaction(3.0, PhysicalSize(900, 450))

        assertEquals(WebMetricsConnection.State.Active, connection.state)
        bridge.detach()
        bridge.detach()

        assertFalse(WebMetricsTransactions.dispatch(bridge, transaction))
        assertEquals(WebMetricsConnection.State.Suspended, connection.state)
        assertNotNull(connection.sink, "the opaque owner retains its dormant sink outside the active registry")
        assertTrue(WebMetricsTransactions.disconnect(connection))
        assertEquals(WebMetricsConnection.State.Cancelled, connection.state)
        assertNull(connection.sink)
        assertFalse(WebMetricsTransactions.disconnect(connection))
        assertEquals(baseline, WebMetricsTransactions.connectionCount)
        assertEquals(2, bridge.detachCalls, "the bridge implementation remains callable but registry release is idempotent")
    }

    @Test
    fun `loop owner reactivates after direct detach and closes the reattached bridge`() {
        val baseline = WebMetricsTransactions.connectionCount
        val bridge = ScriptedBridge()
        val loop = TestWebEventLoop(bridge)
        val window = loop.createWindow(WebWindowAttributes(canvasId = "canvas")) as WebWindow
        val connection = window.metricsConnection ?: error("loop-owned window must have a metrics connection")
        val observations = mutableListOf<CacheObservation>()
        val transaction = WebMetricsTransaction(3.0, PhysicalSize(900, 450))

        assertEquals(baseline + 1, WebMetricsTransactions.connectionCount)
        bridge.detach()
        assertEquals(WebMetricsConnection.State.Suspended, connection.state)
        assertFalse(WebMetricsTransactions.dispatch(bridge, transaction))
        assertEquals(baseline, WebMetricsTransactions.connectionCount)

        bridge.attach("canvas")
        assertEquals(WebMetricsConnection.State.Active, connection.state)
        bridge.metrics = bridge.metrics.copy(devicePixelRatio = 3.0)
        bridge.emitDevicePixelRatioChanged()
        loop.pump(observingHandler(window, observations))

        assertEquals(
            listOf(
                CacheObservation(WindowEvent.ScaleFactorChanged(3.0), 3.0, PhysicalSize(900, 450)),
                CacheObservation(WindowEvent.Resized(PhysicalSize(900, 450)), 3.0, PhysicalSize(900, 450)),
            ),
            observations,
        )
        assertEquals(baseline + 1, WebMetricsTransactions.connectionCount)

        window.close()
        assertEquals(WebMetricsConnection.State.Cancelled, connection.state)
        assertNull(connection.sink)
        assertFalse(WebMetricsTransactions.dispatch(bridge, transaction))
        assertEquals(2, bridge.detachCalls)
        assertEquals(baseline, WebMetricsTransactions.connectionCount)

        window.close()
        bridge.detach()
        assertFalse(WebMetricsTransactions.dispatch(bridge, transaction))
        assertEquals(baseline, WebMetricsTransactions.connectionCount)
    }

    @Test
    fun `closing an exactly suspended loop owner cancels and detaches it once`() {
        val baseline = WebMetricsTransactions.connectionCount
        val bridge = ScriptedBridge()
        val loop = TestWebEventLoop(bridge)
        val window = loop.createWindow(WebWindowAttributes(canvasId = "canvas")) as WebWindow
        val connection = window.metricsConnection ?: error("loop-owned window must have a metrics connection")

        bridge.detach()
        assertEquals(WebMetricsConnection.State.Suspended, connection.state)
        assertEquals(baseline, WebMetricsTransactions.connectionCount)

        window.close()
        assertEquals(WebMetricsConnection.State.Cancelled, connection.state)
        assertNull(connection.sink)
        assertEquals(2, bridge.detachCalls)
        assertEquals(baseline, WebMetricsTransactions.connectionCount)

        window.close()
        assertEquals(2, bridge.detachCalls)
    }

    @Test
    fun `suspended owner replaced by a newer window stays stale`() {
        val baseline = WebMetricsTransactions.connectionCount
        val bridge = ScriptedBridge()
        val loop = TestWebEventLoop(bridge)
        val oldWindow = loop.createWindow(WebWindowAttributes(canvasId = "old")) as WebWindow
        val oldConnection = oldWindow.metricsConnection ?: error("old window must own a metrics connection")

        bridge.detach()
        assertEquals(WebMetricsConnection.State.Suspended, oldConnection.state)
        assertEquals(baseline, WebMetricsTransactions.connectionCount)
        bridge.onWindowEvent = { }

        val newWindow = loop.createWindow(WebWindowAttributes(canvasId = "new")) as WebWindow
        val newConnection = newWindow.metricsConnection ?: error("new window must own a metrics connection")
        assertEquals(WebMetricsConnection.State.Cancelled, oldConnection.state)
        assertNull(oldConnection.sink)
        assertEquals(WebMetricsConnection.State.Active, newConnection.state)
        assertEquals(baseline + 1, WebMetricsTransactions.connectionCount)

        oldWindow.close()
        assertEquals(1, bridge.detachCalls, "a stale suspended owner must not detach the replacement")
        assertTrue(
            WebMetricsTransactions.dispatch(
                bridge,
                WebMetricsTransaction(3.0, PhysicalSize(900, 450)),
            ),
        )
        assertEquals(baseline + 1, WebMetricsTransactions.connectionCount)

        newWindow.close()
        assertEquals(WebMetricsConnection.State.Cancelled, newConnection.state)
        assertEquals(2, bridge.detachCalls)
        assertEquals(baseline, WebMetricsTransactions.connectionCount)
    }

    @Test
    fun `two normal windows own and release two independent registry connections`() {
        val baseline = WebMetricsTransactions.connectionCount
        val firstBridge = ScriptedBridge()
        val secondBridge = ScriptedBridge()
        val loop = SequencedWebEventLoop(firstBridge, secondBridge)
        val firstWindow = loop.createWindow(WebWindowAttributes(canvasId = "first")) as WebWindow
        val secondWindow = loop.createWindow(WebWindowAttributes(canvasId = "second")) as WebWindow

        assertEquals(baseline + 2, WebMetricsTransactions.connectionCount)
        firstWindow.close()
        assertEquals(baseline + 1, WebMetricsTransactions.connectionCount)
        secondWindow.close()
        assertEquals(baseline, WebMetricsTransactions.connectionCount)
    }

    private fun observingHandler(
        window: WebWindow,
        observations: MutableList<CacheObservation>,
    ): ApplicationHandler = object : ApplicationHandler {
        override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
        override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) = Unit

        override fun windowEvent(eventLoop: ActiveEventLoop, windowId: WindowId, event: WindowEvent) {
            if (event is WindowEvent.ScaleFactorChanged || event is WindowEvent.Resized) {
                observations += CacheObservation(event, window.scaleFactor, window.innerSize)
            }
        }
    }

    private fun assertPosition(event: WebWindowEvent, x: Double, y: Double) {
        val actual = when (event) {
            is WebWindowEvent.PointerEntered -> event.x to event.y
            is WebWindowEvent.PointerLeft -> event.x to event.y
            is WebWindowEvent.PointerButton -> event.x to event.y
            is WebWindowEvent.DragEntered -> event.x to event.y
            is WebWindowEvent.DragMoved -> event.x to event.y
            is WebWindowEvent.DragDropped -> event.x to event.y
            else -> error("Event has no tested position: $event")
        }
        assertEquals(x to y, actual)
    }

    private data class CacheObservation(
        val event: WindowEvent,
        val scaleFactor: Double,
        val size: PhysicalSize<Int>,
    )

    private class ScriptedBridge : WebDomBridge, WebMetricsConnectionOwner {
        override var onWindowEvent: ((WebWindowEvent) -> Unit)? = null
        var metrics = CanvasMetrics(10.0, 20.0, 300.0, 150.0, 2.0)
        var detachCalls = 0
        private val adapter = WebBridgeEventAdapter(
            metricsProvider = { metrics },
            eventSink = { onWindowEvent?.invoke(it) },
            metricsSink = { _, transaction -> WebMetricsTransactions.dispatch(this, transaction) },
        )
        private var attachmentToken: WebAttachmentToken? = null
        override var metricsConnection: WebMetricsConnection? = null

        override fun attach(targetElementId: String) {
            attachmentToken = adapter.attach()
            metricsConnection
                ?.takeIf { it.bridge === this }
                ?.let(WebMetricsTransactions::reactivate)
        }
        override fun detach() {
            detachCalls += 1
            val ownerConnection = metricsConnection?.takeIf { it.bridge === this }
            if (ownerConnection == null) {
                WebMetricsTransactions.suspendActive(this)
            } else {
                WebMetricsTransactions.suspend(ownerConnection)
            }
            adapter.detach()
        }
        override fun readDevicePixelRatio(): Double = metrics.devicePixelRatio
        override fun readCanvasPhysicalSize(canvasId: String): Pair<Int, Int> =
            metrics.physicalSize().let { it.width to it.height }

        fun emit(event: WebWindowEvent) {
            onWindowEvent?.invoke(event)
        }

        fun emitDevicePixelRatioChanged() {
            adapter.devicePixelRatioChanged(attachmentToken ?: error("bridge is not attached"))
        }
    }

    private class EqualBridge : WebDomBridge {
        override var onWindowEvent: ((WebWindowEvent) -> Unit)? = null
        override fun attach(targetElementId: String) = Unit
        override fun detach() = Unit
        override fun readDevicePixelRatio(): Double = 1.0
        override fun readCanvasPhysicalSize(canvasId: String): Pair<Int, Int> = 1 to 1
        override fun equals(other: Any?): Boolean = other is EqualBridge
        override fun hashCode(): Int = 0
    }

    private class TestWebEventLoop(
        private val bridge: WebDomBridge,
    ) : WebEventLoop() {
        override fun createDomBridge(): WebDomBridge = bridge
        fun pump(handler: ApplicationHandler) = tick(handler)
    }

    private class SequencedWebEventLoop(
        vararg bridges: WebDomBridge,
    ) : WebEventLoop() {
        private val remaining = bridges.toMutableList()
        override fun createDomBridge(): WebDomBridge = remaining.removeAt(0)
    }
}
