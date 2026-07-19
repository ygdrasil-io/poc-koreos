package org.graphiks.kadre.web

import org.graphiks.kadre.core.PhysicalSize

internal data class WebTouchContact(
    val id: Long,
    val clientX: Double,
    val clientY: Double,
)

internal data class WebMetricsTransaction(
    val scaleFactor: Double,
    val physicalSize: PhysicalSize<Int>,
)

internal object WebMetricsTransactions {
    private val sinks = mutableMapOf<WebDomBridge, (WebMetricsTransaction) -> Unit>()

    fun connect(bridge: WebDomBridge, sink: (WebMetricsTransaction) -> Unit) {
        sinks[bridge] = sink
    }

    fun dispatch(bridge: WebDomBridge, transaction: WebMetricsTransaction): Boolean {
        val sink = sinks[bridge] ?: return false
        sink(transaction)
        return true
    }

    fun disconnect(bridge: WebDomBridge) {
        sinks.remove(bridge)
    }
}

/**
 * Target-neutral event adapter used by both browser bridges.
 *
 * Target wrappers only extract DOM fields. Lifecycle, touch identity and event
 * production stay here so JS and Wasm cannot drift semantically.
 */
internal class WebBridgeEventAdapter(
    private val metricsProvider: () -> CanvasMetrics,
    private val eventSink: (WebWindowEvent) -> Unit,
    private val metricsSink: (WebMetricsTransaction) -> Unit,
) {
    private val touchTracker = WebPointerTracker()
    private var attached = false

    fun attach() {
        touchTracker.close()
        attached = true
    }

    fun detach() {
        attached = false
        touchTracker.close()
    }

    fun pointer(
        eventType: String,
        clientX: Double,
        clientY: Double,
        pointerId: Long,
        pointerType: String,
        domPrimary: Boolean,
        button: Int,
    ) {
        if (!attached) return
        val position = metricsProvider().toPhysical(clientX, clientY)
        domPointerEvent(
            eventType = eventType,
            x = position.x,
            y = position.y,
            pointerId = pointerId,
            pointerType = pointerType,
            domPrimary = domPrimary,
            button = button.toShort(),
        )?.let(eventSink)
    }

    fun touches(phase: WebTouchPhase, contacts: List<WebTouchContact>) {
        if (!attached) return
        val metrics = metricsProvider()
        contacts.forEach { contact ->
            val pointer = when (phase) {
                WebTouchPhase.Started -> touchTracker.onStart(contact.id, "touch", domPrimary = false)
                WebTouchPhase.Moved -> touchTracker.onMove(contact.id, "touch", domPrimary = false)
                WebTouchPhase.Ended -> touchTracker.onEnd(contact.id, "touch", domPrimary = false)
                WebTouchPhase.Cancelled -> touchTracker.onCancel(contact.id, "touch", domPrimary = false)
            }
            val position = metrics.toPhysical(contact.clientX, contact.clientY)
            eventSink(
                WebWindowEvent.Touch(
                    phase = phase,
                    x = position.x,
                    y = position.y,
                    id = contact.id,
                    primary = pointer.primary,
                ),
            )
        }
    }

    fun wheel(
        deltaX: Double,
        deltaY: Double,
        deltaMode: Int,
        ctrlKey: Boolean,
        clientX: Double,
        clientY: Double,
    ) {
        if (!attached) return
        val metrics = metricsProvider()
        eventSink(
            if (ctrlKey) {
                val center = metrics.toPhysical(clientX, clientY)
                WebWindowEvent.WebPinchZoom(
                    delta = (-deltaY / 100.0).toFloat(),
                    centerX = center.x,
                    centerY = center.y,
                )
            } else {
                WebWindowEvent.MouseWheel(
                    deltaX = normalizeWheelDelta(deltaX, deltaMode),
                    deltaY = normalizeWheelDelta(deltaY, deltaMode),
                )
            },
        )
    }

    fun dragEntered(clientX: Double, clientY: Double, files: List<String>) {
        positional(clientX, clientY) { x, y -> WebWindowEvent.DragEntered(x, y, files) }
    }

    fun dragMoved(clientX: Double, clientY: Double) {
        positional(clientX, clientY) { x, y -> WebWindowEvent.DragMoved(x, y) }
    }

    fun dragDropped(clientX: Double, clientY: Double, files: List<String>) {
        positional(clientX, clientY) { x, y -> WebWindowEvent.DragDropped(x, y, files) }
    }

    fun resized() {
        if (!attached) return
        val size = metricsProvider().physicalSize()
        eventSink(WebWindowEvent.Resized(size.width, size.height))
    }

    fun devicePixelRatioChanged() {
        if (!attached) return
        val metrics = metricsProvider()
        metricsSink(
            WebMetricsTransaction(
                scaleFactor = metrics.normalizedDevicePixelRatio(),
                physicalSize = metrics.physicalSize(),
            ),
        )
    }

    private fun positional(
        clientX: Double,
        clientY: Double,
        event: (Double, Double) -> WebWindowEvent,
    ) {
        if (!attached) return
        val position = metricsProvider().toPhysical(clientX, clientY)
        eventSink(event(position.x, position.y))
    }
}
