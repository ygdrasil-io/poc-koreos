package org.graphiks.kadre.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class GamepadValuesTest {
    @Test
    fun descriptorAndControlValuesAreClosed() {
        assertFailsWith<IllegalArgumentException> {
            GamepadDescriptor(null, GamepadMapping.Standard, listOf(GamepadButton.South, GamepadButton.South), emptyList())
        }
        assertFailsWith<IllegalArgumentException> { GamepadButtonValue(GamepadButton.South, 1.1, true) }
        assertFailsWith<IllegalArgumentException> { GamepadAxisValue(GamepadAxis.LeftX, -1.1) }
    }

    @Test
    fun effectsRequirePositiveDurationAndNormalizedIntensity() {
        assertFailsWith<IllegalArgumentException> { GamepadEffect.DualRumble(1.0, 1.0, Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { GamepadEffect.TriggerRumble(1.1, 0.0, 1.seconds) }
    }

    @Test
    fun analogValuesCanonicalizeNegativeZero() {
        val negative = GamepadAxisValue(GamepadAxis.LeftX, -0.0)
        val positive = GamepadAxisValue(GamepadAxis.LeftX, 0.0)

        assertEquals(positive, negative)
        assertEquals(positive.hashCode(), negative.hashCode())
    }
}
