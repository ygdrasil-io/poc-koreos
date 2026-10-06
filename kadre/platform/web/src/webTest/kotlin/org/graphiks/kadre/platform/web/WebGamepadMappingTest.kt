package org.graphiks.kadre.platform.web

import org.graphiks.kadre.input.GamepadAxis
import org.graphiks.kadre.input.GamepadAxisValue
import org.graphiks.kadre.input.GamepadButton
import org.graphiks.kadre.input.GamepadButtonValue
import org.graphiks.kadre.input.GamepadMapping
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The mapping is the whole story a browser gamepad tells Kadre: which controls exist and which value
 * each holds. These tests pin the exact DOM standard layout (the one `mapping == "standard"` names,
 * in DOM order) and the native fallback's arbitrary shapes, plus the state pairing rule the runtime's
 * exact-match validation (`RuntimeGamepadManager`) will enforce against the descriptor.
 *
 * The pad double is structural: exactly what a browser reports, nothing it does not.
 */
class WebGamepadMappingTest {
    private class FakeDomGamepad(
        override val index: Int = 0,
        override val domId: String = "test-pad",
        override val connected: Boolean = true,
        override val mapping: String? = "standard",
        override val buttonValues: List<Double> = List(STANDARD_BUTTON_COUNT) { 0.0 },
        override val axisValues: List<Double> = List(STANDARD_AXIS_COUNT) { 0.0 },
        override val hapticActuator: WebDomHapticActuator? = null,
    ) : WebDomGamepad

    private fun pad(
        index: Int = 0,
        domId: String = "test-pad",
        connected: Boolean = true,
        mapping: String? = "standard",
        buttonValues: List<Double> = List(STANDARD_BUTTON_COUNT) { 0.0 },
        axisValues: List<Double> = List(STANDARD_AXIS_COUNT) { 0.0 },
    ): WebDomGamepad = FakeDomGamepad(index, domId, connected, mapping, buttonValues, axisValues)

    @Test
    fun `standard-mapping-exact-layout`() {
        val pad = pad(
            domId = "Wireless Controller",
            buttonValues = List(STANDARD_BUTTON_COUNT) { it / 32.0 },
            axisValues = listOf(0.5, -0.25, 1.0, -1.0),
        )

        val descriptor = WebGamepadMapping.descriptor(pad)
        assertEquals("Wireless Controller", descriptor.name, "the DOM id is honest metadata, verbatim")
        assertEquals(GamepadMapping.Standard, descriptor.mapping)
        assertEquals(standardButtons, descriptor.buttons, "the 17 standard buttons, in DOM order")
        assertEquals(standardAxes, descriptor.axes, "the 4 standard axes, in DOM order")

        val state = WebGamepadMapping.state(pad, descriptor)
        assertEquals(standardButtons, state.buttons.map(GamepadButtonValue::button))
        assertEquals(standardAxes, state.axes.map(GamepadAxisValue::axis))
        List(STANDARD_BUTTON_COUNT) { it / 32.0 }.forEachIndexed { index, value ->
            assertEquals(value, state.buttons[index].value, "button $index pairs its DOM value in order")
            assertEquals(value >= 0.5, state.buttons[index].pressed, "button $index presses at the DOM threshold")
        }
        listOf(0.5, -0.25, 1.0, -1.0).forEachIndexed { index, value ->
            assertEquals(value, state.axes[index].value, "axis $index pairs its DOM value in order")
        }
    }

    @Test
    fun `canonicalization-holds-for-what-the-dom-reports`() {
        // Out-of-range and non-finite readings flow into the seam (nothing is dropped, nothing
        // crashes) and what reaches the model is canonical: in range, +0.0, pressed at the threshold.
        val pad = pad(
            buttonValues = List(STANDARD_BUTTON_COUNT) { index ->
                when (index) {
                    3 -> 1.5 // above the canonical [0, 1] window
                    7 -> -0.3 // below it
                    11 -> Double.NaN // not a reading at all
                    else -> 0.0
                }
            },
            axisValues = listOf(-0.0, 1.5, -1.5, Double.NaN),
        )

        val state = WebGamepadMapping.state(pad, WebGamepadMapping.descriptor(pad))
        assertEquals(1.0, state.buttons[3].value)
        assertEquals(true, state.buttons[3].pressed, "an over-range reading is a press, not a drop")
        assertEquals(0.0, state.buttons[7].value)
        assertEquals(false, state.buttons[7].pressed)
        assertEquals(0.0, state.buttons[11].value)
        assertEquals(false, state.buttons[11].pressed)
        // The foundation's canonicalization of a negative zero is a positive one: `Double.equals`
        // distinguishes them, so this holds only if the canonical +0.0 really is stored.
        assertTrue(state.axes[0].value.equals(0.0), "the canonical zero is +0.0, never -0.0")
        assertEquals(1.0, state.axes[1].value)
        assertEquals(-1.0, state.axes[2].value)
        assertEquals(0.0, state.axes[3].value)
    }

    @Test
    fun `native-mapping-arbitrary-counts`() {
        // The empty mapping string is the browser's own "not standard": every control becomes a
        // native code of the shape `validateGamepadCode` accepts, at any count the pad reports.
        val pad = pad(
            mapping = "",
            buttonValues = List(23) { (it % 3) * 0.5 },
            axisValues = List(7) { it * 0.25 - 0.75 },
        )

        val descriptor = WebGamepadMapping.descriptor(pad)
        assertEquals(GamepadMapping.Native, descriptor.mapping)
        assertEquals((0..22).map { GamepadButton.Other("button-$it") }, descriptor.buttons)
        assertEquals((0..6).map { GamepadAxis.Other("axis-$it") }, descriptor.axes)

        val state = WebGamepadMapping.state(pad, descriptor)
        assertEquals(descriptor.buttons, state.buttons.map(GamepadButtonValue::button))
        assertEquals(descriptor.axes, state.axes.map(GamepadAxisValue::axis))
        assertEquals(0.5, state.buttons[1].value)
        assertEquals(true, state.buttons[1].pressed)
        assertEquals(-0.75, state.axes[0].value)
        assertEquals(0.75, state.axes[6].value)

        // A pad with no buttons at all still builds, descriptor and state both.
        val buttonless = pad(mapping = "", buttonValues = emptyList(), axisValues = listOf(0.0, 1.0))
        val buttonlessDescriptor = WebGamepadMapping.descriptor(buttonless)
        assertEquals(emptyList(), buttonlessDescriptor.buttons)
        assertEquals(GamepadMapping.Native, buttonlessDescriptor.mapping)
        val buttonlessState = WebGamepadMapping.state(buttonless, buttonlessDescriptor)
        assertEquals(emptyList(), buttonlessState.buttons)
        assertEquals(listOf(0.0, 1.0), buttonlessState.axes.map(GamepadAxisValue::value))
    }

    @Test
    fun `null-mapping-is-native`() {
        // A browser that omits the mapping member is as little "standard" as one that denies it.
        val pad = pad(
            mapping = null,
            buttonValues = listOf(1.0, 0.0, 0.5),
            axisValues = listOf(0.0, -1.0),
        )

        val descriptor = WebGamepadMapping.descriptor(pad)
        assertEquals(GamepadMapping.Native, descriptor.mapping)
        assertEquals(
            listOf(GamepadButton.Other("button-0"), GamepadButton.Other("button-1"), GamepadButton.Other("button-2")),
            descriptor.buttons,
        )
        assertEquals(listOf(GamepadAxis.Other("axis-0"), GamepadAxis.Other("axis-1")), descriptor.axes)
    }

    @Test
    fun `state-always-matches-descriptor-order`() {
        // The runtime validates state EXACTLY against the descriptor (buttons and axes, order and
        // membership). The pairing is positional over the descriptor, so the invariant holds for
        // every shape: standard, native, and a standard-named pad whose browser reports a
        // non-standard count (missing readings read neutral, extra ones are dropped).
        listOf(
            pad(),
            pad(mapping = "", buttonValues = List(9) { 0.0 }, axisValues = List(3) { 0.0 }),
            pad(buttonValues = List(5) { 1.0 }, axisValues = emptyList()),
        ).forEach { pad ->
            val descriptor = WebGamepadMapping.descriptor(pad)
            val state = WebGamepadMapping.state(pad, descriptor)
            assertEquals(descriptor.buttons, state.buttons.map(GamepadButtonValue::button))
            assertEquals(descriptor.axes, state.axes.map(GamepadAxisValue::axis))
        }
    }

    private companion object {
        const val STANDARD_BUTTON_COUNT = 17
        const val STANDARD_AXIS_COUNT = 4

        /** The DOM standard mapping's button order, written out here independently of the mapping. */
        val standardButtons = listOf(
            GamepadButton.South, GamepadButton.East, GamepadButton.West, GamepadButton.North,
            GamepadButton.LeftShoulder, GamepadButton.RightShoulder, GamepadButton.LeftTrigger, GamepadButton.RightTrigger,
            GamepadButton.Select, GamepadButton.Start, GamepadButton.LeftStick, GamepadButton.RightStick,
            GamepadButton.DpadUp, GamepadButton.DpadDown, GamepadButton.DpadLeft, GamepadButton.DpadRight,
            GamepadButton.Mode,
        )

        /** The DOM standard mapping's axis order. */
        val standardAxes = listOf(GamepadAxis.LeftX, GamepadAxis.LeftY, GamepadAxis.RightX, GamepadAxis.RightY)
    }
}
