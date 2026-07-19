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

internal class WebAttachmentToken internal constructor(
    internal val generation: Int,
)

private var nextAttachmentGeneration = 0

internal class WebMetricsConnection internal constructor()

internal object WebMetricsTransactions {
    private data class Entry(
        val bridge: WebDomBridge,
        val connection: WebMetricsConnection,
        val sink: (WebMetricsTransaction) -> Unit,
    )

    private val entries = mutableListOf<Entry>()

    internal val connectionCount: Int
        get() = entries.size

    fun connect(bridge: WebDomBridge, sink: (WebMetricsTransaction) -> Unit): WebMetricsConnection {
        entries.removeAll { it.bridge === bridge }
        val connection = WebMetricsConnection()
        entries += Entry(bridge, connection, sink)
        return connection
    }

    fun dispatch(bridge: WebDomBridge, transaction: WebMetricsTransaction): Boolean {
        val entry = entries.firstOrNull { it.bridge === bridge } ?: return false
        entry.sink(transaction)
        return true
    }

    fun disconnect(connection: WebMetricsConnection): Boolean {
        val index = entries.indexOfFirst { it.connection === connection }
        if (index < 0) return false
        entries.removeAt(index)
        return true
    }

    fun disconnectActive(bridge: WebDomBridge): Boolean {
        val index = entries.indexOfFirst { it.bridge === bridge }
        if (index < 0) return false
        entries.removeAt(index)
        return true
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
    private val metricsSink: (WebAttachmentToken, WebMetricsTransaction) -> Unit,
) {
    private val touchTracker = WebPointerTracker()
    private var currentToken: WebAttachmentToken? = null

    fun attach(): WebAttachmentToken {
        touchTracker.close()
        return WebAttachmentToken(++nextAttachmentGeneration).also { currentToken = it }
    }

    fun detach() {
        currentToken = null
        touchTracker.close()
    }

    fun isCurrent(token: WebAttachmentToken): Boolean =
        currentToken?.generation == token.generation

    fun runIfCurrent(token: WebAttachmentToken, action: () -> Unit) {
        if (isCurrent(token)) action()
    }

    fun emit(token: WebAttachmentToken, event: WebWindowEvent) {
        if (isCurrent(token)) eventSink(event)
    }

    fun pointer(
        token: WebAttachmentToken,
        eventType: String,
        clientX: Double,
        clientY: Double,
        pointerId: Long,
        pointerType: String,
        domPrimary: Boolean,
        button: Int,
    ) {
        if (!isCurrent(token)) return
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

    fun touches(token: WebAttachmentToken, phase: WebTouchPhase, contacts: List<WebTouchContact>) {
        if (!isCurrent(token)) return
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
        token: WebAttachmentToken,
        deltaX: Double,
        deltaY: Double,
        deltaMode: Int,
        ctrlKey: Boolean,
        clientX: Double,
        clientY: Double,
    ) {
        if (!isCurrent(token)) return
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

    fun dragEntered(token: WebAttachmentToken, clientX: Double, clientY: Double, files: List<String>) {
        positional(token, clientX, clientY) { x, y -> WebWindowEvent.DragEntered(x, y, files) }
    }

    fun dragMoved(token: WebAttachmentToken, clientX: Double, clientY: Double) {
        positional(token, clientX, clientY) { x, y -> WebWindowEvent.DragMoved(x, y) }
    }

    fun dragDropped(token: WebAttachmentToken, clientX: Double, clientY: Double, files: List<String>) {
        positional(token, clientX, clientY) { x, y -> WebWindowEvent.DragDropped(x, y, files) }
    }

    fun resized(token: WebAttachmentToken) {
        if (!isCurrent(token)) return
        val size = metricsProvider().physicalSize()
        eventSink(WebWindowEvent.Resized(size.width, size.height))
    }

    fun devicePixelRatioChanged(token: WebAttachmentToken) {
        if (!isCurrent(token)) return
        val metrics = metricsProvider()
        metricsSink(
            token,
            WebMetricsTransaction(
                scaleFactor = metrics.normalizedDevicePixelRatio(),
                physicalSize = metrics.physicalSize(),
            ),
        )
    }

    private fun positional(
        token: WebAttachmentToken,
        clientX: Double,
        clientY: Double,
        event: (Double, Double) -> WebWindowEvent,
    ) {
        if (!isCurrent(token)) return
        val position = metricsProvider().toPhysical(clientX, clientY)
        eventSink(event(position.x, position.y))
    }
}
