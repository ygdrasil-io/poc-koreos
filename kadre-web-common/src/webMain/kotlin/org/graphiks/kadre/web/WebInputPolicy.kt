package org.graphiks.kadre.web

internal enum class WebInputFamily {
    PointerEvents,
    LegacyTouchEvents,
}

internal data class WebInputRegistration(
    val family: WebInputFamily,
    val eventTypes: List<String>,
)

internal fun selectWebInputRegistration(pointerEventsSupported: Boolean): WebInputRegistration =
    if (pointerEventsSupported) {
        WebInputRegistration(
            family = WebInputFamily.PointerEvents,
            eventTypes = listOf(
                "pointermove",
                "pointerdown",
                "pointerup",
                "pointerenter",
                "pointerleave",
                "pointercancel",
            ),
        )
    } else {
        WebInputRegistration(
            family = WebInputFamily.LegacyTouchEvents,
            eventTypes = listOf("touchstart", "touchmove", "touchend", "touchcancel"),
        )
    }

internal fun domPointerEvent(
    eventType: String,
    x: Double,
    y: Double,
    pointerId: Long,
    pointerType: String,
    domPrimary: Boolean,
    button: Short,
): WebWindowEvent {
    val pointer = domPointerSnapshot(pointerId, pointerType, domPrimary)
    return when (eventType) {
        "pointermove" -> WebWindowEvent.PointerMoved(x, y, pointer.pointerId, pointer.primary, pointer.source)
        "pointerdown" -> WebWindowEvent.PointerButton(
            x,
            y,
            pointer.pointerId,
            pointer.primary,
            domPointerButtonSource(button, pointer),
            WebKeyState.Pressed,
        )
        "pointerup" -> WebWindowEvent.PointerButton(
            x,
            y,
            pointer.pointerId,
            pointer.primary,
            domPointerButtonSource(button, pointer),
            WebKeyState.Released,
        )
        "pointerenter" -> WebWindowEvent.PointerEntered(x, y, pointer.pointerId, pointer.primary, pointer.kind)
        "pointerleave", "pointercancel" ->
            WebWindowEvent.PointerLeft(x, y, pointer.pointerId, pointer.primary, pointer.kind)
        else -> error("Unsupported DOM pointer event type: $eventType")
    }
}
