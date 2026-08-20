package org.graphiks.kadre.web

import org.graphiks.kadre.core.ButtonSource
import org.graphiks.kadre.core.FingerId
import org.graphiks.kadre.core.PointerKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebInputPolicyTest {
    @Test
    fun `Pointer Events support selects only the Pointer Events family`() {
        val registration = selectWebInputRegistration(pointerEventsSupported = true)

        assertEquals(WebInputFamily.PointerEvents, registration.family)
        assertEquals(
            listOf(
                "pointermove",
                "pointerdown",
                "pointerup",
                "pointerenter",
                "pointerleave",
                "pointercancel",
            ),
            registration.eventTypes,
        )
    }

    @Test
    fun `missing Pointer Events support selects only the legacy Touch Events family`() {
        val registration = selectWebInputRegistration(pointerEventsSupported = false)

        assertEquals(WebInputFamily.LegacyTouchEvents, registration.family)
        assertEquals(
            listOf("touchstart", "touchmove", "touchend", "touchcancel"),
            registration.eventTypes,
        )
    }

    @Test
    fun `touch PointerEvent primary remains true from enter through leave after up`() {
        val actual = listOf(
            domPointerEvent("pointerenter", 10.0, 20.0, 42L, "touch", domPrimary = true, button = (-1).toShort()),
            domPointerEvent("pointerdown", 11.0, 21.0, 42L, "touch", domPrimary = true, button = 0),
            domPointerEvent("pointerup", 12.0, 22.0, 42L, "touch", domPrimary = true, button = 0),
            domPointerEvent("pointerleave", 13.0, 23.0, 42L, "touch", domPrimary = true, button = (-1).toShort()),
        )

        assertEquals(
            listOf(
                WebWindowEvent.PointerEntered(10.0, 20.0, 42L, true, PointerKind.Touch),
                WebWindowEvent.PointerButton(
                    11.0,
                    21.0,
                    42L,
                    true,
                    ButtonSource.Touch(FingerId(42L)),
                    WebKeyState.Pressed,
                ),
                WebWindowEvent.PointerButton(
                    12.0,
                    22.0,
                    42L,
                    true,
                    ButtonSource.Touch(FingerId(42L)),
                    WebKeyState.Released,
                ),
                WebWindowEvent.PointerLeft(13.0, 23.0, 42L, true, PointerKind.Touch),
            ),
            actual,
        )
    }

    @Test
    fun `pointercancel alone emits no core event`() {
        assertNull(
            domPointerEvent(
                "pointercancel",
                18.5,
                19.75,
                42L,
                "touch",
                domPrimary = true,
                button = 0,
            ),
        )
    }

    @Test
    fun `pointercancel then leave and re-entry emits one left followed by entered`() {
        val actual = listOf(
            "pointercancel" to true,
            "pointerleave" to true,
            "pointerenter" to false,
        ).mapIndexedNotNull { index, (eventType, domPrimary) ->
            domPointerEvent(
                eventType = eventType,
                x = 18.5 + index,
                y = 19.75 + index,
                pointerId = 42L,
                pointerType = "touch",
                domPrimary = domPrimary,
                button = (-1).toShort(),
            )
        }

        assertEquals(
            listOf(
                WebWindowEvent.PointerLeft(19.5, 20.75, 42L, true, PointerKind.Touch),
                WebWindowEvent.PointerEntered(20.5, 21.75, 42L, false, PointerKind.Touch),
            ),
            actual,
        )
    }
}
