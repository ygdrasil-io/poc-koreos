package org.graphiks.kadre.web

import org.graphiks.kadre.core.ButtonSource
import org.graphiks.kadre.core.FingerId
import org.graphiks.kadre.core.PointerKind
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `pointercancel emits exact left semantics without retaining pointer state`() {
        assertEquals(
            WebWindowEvent.PointerLeft(
                x = 18.5,
                y = 19.75,
                pointerId = 42L,
                primary = true,
                kind = PointerKind.Touch,
            ),
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

        assertEquals(
            WebWindowEvent.PointerEntered(20.0, 21.0, 42L, false, PointerKind.Touch),
            domPointerEvent(
                "pointerenter",
                20.0,
                21.0,
                42L,
                "touch",
                domPrimary = false,
                button = (-1).toShort(),
            ),
        )
    }
}
