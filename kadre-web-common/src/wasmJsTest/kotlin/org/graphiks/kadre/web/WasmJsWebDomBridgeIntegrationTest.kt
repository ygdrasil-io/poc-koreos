@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.web

import org.graphiks.kadre.core.ButtonSource
import org.graphiks.kadre.core.MouseButton
import org.graphiks.kadre.core.PointerSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@JsFun("() => Object.getOwnPropertyDescriptor(window, 'devicePixelRatio') || null")
private external fun readDevicePixelRatioDescriptor(): JsAny?

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

class WasmJsWebDomBridgeIntegrationTest {

    @Test
    fun `real Wasm bridge maps scripted canvas pointer events and releases its route on detach`() {
        val canvasId = "kadre-wasm-bridge-integration"
        val originalDpr = readDevicePixelRatioDescriptor()
        val baseline = WebMetricsTransactions.connectionCount
        val bridge = WasmJsWebDomBridge()
        val events = mutableListOf<WebWindowEvent>()
        bridge.onWindowEvent = { event -> events.add(event) }
        var connection: WebMetricsConnection? = null

        try {
            installDom(canvasId.toJsString())
            bridge.attach(canvasId)
            connection = WebMetricsTransactions.connect(bridge) { }

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
            assertFalse(WebMetricsTransactions.disconnect(connection))
        } finally {
            bridge.detach()
            connection?.let { activeConnection -> WebMetricsTransactions.disconnect(activeConnection) }
            restoreDom(canvasId.toJsString(), originalDpr)
        }
    }
}
