@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.web

import org.graphiks.kadre.core.ButtonSource
import org.graphiks.kadre.core.MouseButton
import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.core.PointerSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@JsFun("() => Object.getOwnPropertyDescriptor(window, 'devicePixelRatio') || null")
private external fun readDevicePixelRatioDescriptor(): JsAny?

@JsFun("() => globalThis.ResizeObserver")
private external fun readResizeObserver(): JsAny

@JsFun("""(id) => {
    Object.defineProperty(window, 'devicePixelRatio', { configurable: true, value: 2.0 });
    const canvas = document.createElement('canvas');
    canvas.id = id;
    canvas.getBoundingClientRect = () => ({
        left: 10.0, top: 20.0, width: 300.0, height: 150.0,
        right: 310.0, bottom: 170.0
    });
    document.body.appendChild(canvas);
}""")
private external fun installDom(id: JsString)

@JsFun("""(id, originalDpr) => {
    const canvas = document.getElementById(id);
    if (canvas) canvas.remove();
    if (originalDpr === null) delete window.devicePixelRatio;
    else Object.defineProperty(window, 'devicePixelRatio', originalDpr);
}""")
private external fun restoreDom(id: JsString, originalDpr: JsAny?)

@JsFun("""(id, type, clientX, clientY, pointerId, button) => {
    const canvas = document.getElementById(id);
    let event = null;
    if (typeof window.PointerEvent === 'function') {
        try {
            event = new PointerEvent(type, {
                bubbles: true,
                clientX: clientX,
                clientY: clientY,
                pointerId: pointerId,
                pointerType: 'mouse',
                isPrimary: true,
                button: button
            });
        } catch (_) {}
    }
    if (!event) {
        event = new Event(type, { bubbles: true });
        Object.defineProperty(event, 'clientX', { configurable: true, value: clientX });
        Object.defineProperty(event, 'clientY', { configurable: true, value: clientY });
        Object.defineProperty(event, 'pointerId', { configurable: true, value: pointerId });
        Object.defineProperty(event, 'pointerType', { configurable: true, value: 'mouse' });
        Object.defineProperty(event, 'isPrimary', { configurable: true, value: true });
        Object.defineProperty(event, 'button', { configurable: true, value: button });
    }
    canvas.dispatchEvent(event);
}""")
private external fun dispatchPointer(
    id: JsString,
    type: JsString,
    clientX: Double,
    clientY: Double,
    pointerId: Double,
    button: Int,
)

@JsFun("""(id) => {
    Object.defineProperty(window, 'devicePixelRatio', { configurable: true, value: 2.0 });
    const canvas = document.createElement('canvas');
    canvas.id = id;
    canvas.style.width = '800px';
    canvas.style.height = '600px';
    canvas.style.border = '2px solid black';
    document.body.appendChild(canvas);
    globalThis.ResizeObserver = class {
        constructor(callback) { this.callback = callback; }
        observe() { for (let i = 0; i < 5; i += 1) this.callback([]); }
        disconnect() {}
    };
}""")
private external fun installBorderedDom(id: JsString)

@JsFun("""(id, originalDpr, originalResizeObserver) => {
    const canvas = document.getElementById(id);
    if (canvas) canvas.remove();
    if (originalDpr === null) delete window.devicePixelRatio;
    else Object.defineProperty(window, 'devicePixelRatio', originalDpr);
    globalThis.ResizeObserver = originalResizeObserver;
}""")
private external fun restoreBorderedDom(
    id: JsString,
    originalDpr: JsAny?,
    originalResizeObserver: JsAny,
)

@JsFun("""(id) => {
    const canvas = document.getElementById(id);
    const rect = canvas.getBoundingClientRect();
    let event = null;
    const options = {
        bubbles: true,
        clientX: rect.left + canvas.clientLeft,
        clientY: rect.top + canvas.clientTop,
        pointerId: 11,
        pointerType: 'mouse',
        isPrimary: true,
        button: 0
    };
    if (typeof window.PointerEvent === 'function') {
        try { event = new PointerEvent('pointermove', options); } catch (_) {}
    }
    if (!event) {
        event = new Event('pointermove', { bubbles: true });
        for (const [name, value] of Object.entries(options)) {
            Object.defineProperty(event, name, { configurable: true, value: value });
        }
    }
    canvas.dispatchEvent(event);
}""")
private external fun dispatchPointerAtContentOrigin(id: JsString)

class WasmJsWebDomBridgeIntegrationTest {

    @Test
    fun `real Wasm bridge excludes canvas border from physical metrics and pointer origin`() {
        val canvasId = "kadre-wasm-bordered-canvas"
        val originalDpr = readDevicePixelRatioDescriptor()
        val originalResizeObserver = readResizeObserver()
        val bridge = WasmJsWebDomBridge()
        val events = mutableListOf<WebWindowEvent>()
        bridge.onWindowEvent = { event -> events.add(event) }

        try {
            installBorderedDom(canvasId.toJsString())
            bridge.attach(canvasId)

            val sizes = List(5) { bridge.readCanvasPhysicalSize(canvasId) }
            assertEquals(List(5) { 1600 to 1200 }, sizes)
            assertEquals(
                List(5) { WebWindowEvent.Resized(1600, 1200) },
                events.filterIsInstance<WebWindowEvent.Resized>(),
            )

            dispatchPointerAtContentOrigin(canvasId.toJsString())
            val moved = assertIs<WebWindowEvent.PointerMoved>(events.last())
            assertEquals(0.0, moved.x)
            assertEquals(0.0, moved.y)
        } finally {
            bridge.detach()
            restoreBorderedDom(canvasId.toJsString(), originalDpr, originalResizeObserver)
        }
    }

    @Test
    fun `real Wasm bridge maps pointer events and reactivates only its suspended route`() {
        val canvasId = "kadre-wasm-bridge-integration"
        val originalDpr = readDevicePixelRatioDescriptor()
        val baseline = WebMetricsTransactions.connectionCount
        val bridge = WasmJsWebDomBridge()
        val events = mutableListOf<WebWindowEvent>()
        var metricsDeliveries = 0
        bridge.onWindowEvent = { event -> events.add(event) }
        var connection: WebMetricsConnection? = null

        try {
            installDom(canvasId.toJsString())
            connection = WebMetricsTransactions.connect(bridge) { metricsDeliveries += 1 }
            bridge.attach(canvasId)

            dispatchPointer(canvasId.toJsString(), "pointermove".toJsString(), 9.75, 19.5, 7.0, 0)
            dispatchPointer(canvasId.toJsString(), "pointerdown".toJsString(), 20.5, 30.25, 7.0, 0)

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
            connection?.let { activeConnection -> WebMetricsTransactions.disconnect(activeConnection) }
            restoreDom(canvasId.toJsString(), originalDpr)
        }
    }
}
