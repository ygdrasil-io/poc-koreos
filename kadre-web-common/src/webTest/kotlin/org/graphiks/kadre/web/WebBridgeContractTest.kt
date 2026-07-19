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
            metricsSink = transactions::add,
        )

        adapter.attach()
        adapter.pointer("pointermove", 9.75, 19.5, 5L, "mouse", true, 0)
        adapter.pointer("pointerenter", 11.0, 21.0, 5L, "mouse", true, 0)
        adapter.pointer("pointerleave", 12.0, 22.0, 5L, "mouse", true, 0)
        adapter.pointer("pointerdown", 20.5, 30.25, 5L, "mouse", true, 0)
        adapter.pointer("pointerup", 30.25, 40.5, 5L, "mouse", true, 0)
        adapter.touches(
            WebTouchPhase.Started,
            listOf(
                WebTouchContact(id = 42L, clientX = 10.25, clientY = 20.5),
                WebTouchContact(id = 7L, clientX = 14.0, clientY = 25.0),
            ),
        )
        adapter.wheel(
            deltaX = 0.0,
            deltaY = -25.0,
            deltaMode = 0,
            ctrlKey = true,
            clientX = 15.25,
            clientY = 27.5,
        )
        adapter.dragEntered(12.5, 24.5, listOf("image/png"))
        adapter.dragMoved(13.5, 25.5)
        adapter.dragDropped(14.5, 26.5, listOf("sprite.png"))
        adapter.resized()

        metrics = metrics.copy(devicePixelRatio = 3.0)
        adapter.devicePixelRatioChanged()

        val readsBeforeDetach = metricsReads
        val eventCountBeforeDetach = events.size
        val transactionCountBeforeDetach = transactions.size
        adapter.detach()
        adapter.pointer("pointermove", 100.0, 100.0, 5L, "mouse", true, 0)
        adapter.resized()
        adapter.devicePixelRatioChanged()

        adapter.attach()
        adapter.touches(
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
        bridge.adapter.devicePixelRatioChanged()
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

    private class ScriptedBridge : WebDomBridge {
        override var onWindowEvent: ((WebWindowEvent) -> Unit)? = null
        var metrics = CanvasMetrics(10.0, 20.0, 300.0, 150.0, 2.0)
        val adapter = WebBridgeEventAdapter(
            metricsProvider = { metrics },
            eventSink = { onWindowEvent?.invoke(it) },
            metricsSink = { WebMetricsTransactions.dispatch(this, it) },
        )

        override fun attach(targetElementId: String) = adapter.attach()
        override fun detach() = adapter.detach()
        override fun readDevicePixelRatio(): Double = metrics.devicePixelRatio
        override fun readCanvasPhysicalSize(canvasId: String): Pair<Int, Int> =
            metrics.physicalSize().let { it.width to it.height }
    }

    private class TestWebEventLoop(
        private val bridge: WebDomBridge,
    ) : WebEventLoop() {
        override fun createDomBridge(): WebDomBridge = bridge
        fun pump(handler: ApplicationHandler) = tick(handler)
    }
}
