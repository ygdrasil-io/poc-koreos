package org.graphiks.kadre.web

import kotlinx.browser.document
import org.graphiks.kadre.core.ButtonSource
import org.graphiks.kadre.core.MouseButton
import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.core.PointerSource
import org.w3c.dom.Element
import org.w3c.dom.events.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JsWebDomBridgeIntegrationTest {

    @Test
    fun `real JS bridge maps pointer events and reactivates only its suspended route`() {
        val canvasId = "kadre-js-bridge-integration"
        val canvas = document.createElement("canvas")
        canvas.id = canvasId
        val originalDpr = readDevicePixelRatioDescriptor()
        var originalRect: dynamic = null
        var rectInstalled = false
        val baseline = WebMetricsTransactions.connectionCount
        val bridge = JsWebDomBridge()
        val events = mutableListOf<WebWindowEvent>()
        var metricsDeliveries = 0
        bridge.onWindowEvent = events::add
        var connection: WebMetricsConnection? = null

        try {
            document.body?.appendChild(canvas) ?: error("document.body is required by the browser test")
            installDevicePixelRatio()
            originalRect = installCanvasRect(canvas)
            rectInstalled = true
            connection = WebMetricsTransactions.connect(bridge) { metricsDeliveries += 1 }
            bridge.attach(canvasId)

            canvas.dispatchEvent(pointerEvent("pointermove", 9.75, 19.5, 7L, 0))
            canvas.dispatchEvent(pointerEvent("pointerdown", 20.5, 30.25, 7L, 0))

            val moved = assertIs<WebWindowEvent.PointerMoved>(events[0])
            assertEquals(-0.5, moved.x)
            assertEquals(-1.0, moved.y)
            assertEquals(7L, moved.pointerId)
            assertTrue(moved.primary)
            assertEquals(PointerSource.Mouse, moved.source)

            val button = assertIs<WebWindowEvent.PointerButton>(events[1])
            assertEquals(21.0, button.x)
            assertEquals(20.5, button.y)
            assertEquals(7L, button.pointerId)
            assertTrue(button.primary)
            assertEquals(ButtonSource.Mouse(MouseButton.Left), button.button)

            bridge.detach()
            assertEquals(baseline, WebMetricsTransactions.connectionCount)
            assertEquals(WebMetricsConnection.State.Suspended, connection.state)
            assertFalse(
                WebMetricsTransactions.dispatch(
                    bridge,
                    WebMetricsTransaction(3.0, PhysicalSize(900, 450)),
                ),
            )

            bridge.onWindowEvent = { event -> events.add(event) }
            bridge.attach("missing-$canvasId")
            assertEquals(WebMetricsConnection.State.Suspended, connection.state)
            assertEquals(baseline, WebMetricsTransactions.connectionCount)

            bridge.attach(canvasId)
            assertEquals(WebMetricsConnection.State.Active, connection.state)
            assertEquals(baseline + 1, WebMetricsTransactions.connectionCount)
            assertTrue(
                WebMetricsTransactions.dispatch(
                    bridge,
                    WebMetricsTransaction(3.0, PhysicalSize(900, 450)),
                ),
            )
            assertEquals(1, metricsDeliveries)

            bridge.detach()
            assertTrue(WebMetricsTransactions.disconnect(connection))
            assertEquals(WebMetricsConnection.State.Cancelled, connection.state)
            assertFalse(WebMetricsTransactions.disconnect(connection))
            assertEquals(baseline, WebMetricsTransactions.connectionCount)
        } finally {
            bridge.detach()
            connection?.let(WebMetricsTransactions::disconnect)
            if (rectInstalled) restoreCanvasRect(canvas, originalRect)
            restoreDevicePixelRatio(originalDpr)
            canvas.parentNode?.removeChild(canvas)
        }
    }

    private fun readDevicePixelRatioDescriptor(): dynamic =
        js("Object.getOwnPropertyDescriptor(window, 'devicePixelRatio')")

    private fun installDevicePixelRatio() {
        js("Object.defineProperty(window, 'devicePixelRatio', { configurable: true, value: 2.0 })")
    }

    private fun restoreDevicePixelRatio(descriptor: dynamic) {
        js("if (descriptor === undefined) delete window.devicePixelRatio; else Object.defineProperty(window, 'devicePixelRatio', descriptor)")
    }

    private fun installCanvasRect(canvas: Element): dynamic {
        val target = canvas.asDynamic()
        val original = target.getBoundingClientRect
        target.getBoundingClientRect = {
            js("({ left: 10.0, top: 20.0, width: 300.0, height: 150.0, right: 310.0, bottom: 170.0 })")
        }
        return original
    }

    private fun restoreCanvasRect(canvas: Element, original: dynamic) {
        canvas.asDynamic().getBoundingClientRect = original
    }

    private fun pointerEvent(
        type: String,
        clientX: Double,
        clientY: Double,
        pointerId: Long,
        button: Int,
    ): Event {
        val event: dynamic = if (js("typeof window.PointerEvent === 'function'") as Boolean) {
            try {
                js("new PointerEvent(type, { bubbles: true, clientX: clientX, clientY: clientY, pointerId: pointerId, pointerType: 'mouse', isPrimary: true, button: button })")
            } catch (_: Throwable) {
                fallbackPointerEvent(type, clientX, clientY, pointerId, button)
            }
        } else {
            fallbackPointerEvent(type, clientX, clientY, pointerId, button)
        }
        return event.unsafeCast<Event>()
    }

    private fun fallbackPointerEvent(
        type: String,
        clientX: Double,
        clientY: Double,
        pointerId: Long,
        button: Int,
    ): dynamic {
        val fallback = js("new Event(type, { bubbles: true })")
        defineEventField(fallback, "clientX", clientX)
        defineEventField(fallback, "clientY", clientY)
        defineEventField(fallback, "pointerId", pointerId)
        defineEventField(fallback, "pointerType", "mouse")
        defineEventField(fallback, "isPrimary", true)
        defineEventField(fallback, "button", button)
        return fallback
    }

    private fun defineEventField(event: dynamic, name: String, value: Any?) {
        js("Object.defineProperty(event, name, { configurable: true, value: value })")
    }
}
