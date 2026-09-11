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
        assertFailsWith<IllegalArgumentException> {
            GamepadEffect.TriggerRumble(1.1, 0.0, 0.0, 0.0, 1.seconds)
        }
        assertFailsWith<IllegalArgumentException> {
            GamepadEffect.LocalizedHaptic(GamepadHapticLocality.Default, 0.0, Duration.ZERO)
        }
    }

    @Test
    fun analogValuesCanonicalizeNegativeZero() {
        val negative = GamepadAxisValue(GamepadAxis.LeftX, -0.0)
        val positive = GamepadAxisValue(GamepadAxis.LeftX, 0.0)

        assertEquals(positive, negative)
        assertEquals(positive.hashCode(), negative.hashCode())
    }

    @Test
    fun effectIntensitiesCanonicalizeNegativeZero() {
        val effect = GamepadEffect.DualRumble(-0.0, -0.0, 1.seconds)
        val trigger = GamepadEffect.TriggerRumble(-0.0, -0.0, -0.0, -0.0, 1.seconds)
        val localized = GamepadEffect.LocalizedHaptic(GamepadHapticLocality.Default, -0.0, 1.seconds)

        assertEquals(0.0.toBits(), effect.strong.toBits())
        assertEquals(0.0.toBits(), effect.weak.toBits())
        assertEquals(0.0.toBits(), trigger.strong.toBits())
        assertEquals(0.0.toBits(), trigger.weak.toBits())
        assertEquals(0.0.toBits(), trigger.leftTrigger.toBits())
        assertEquals(0.0.toBits(), trigger.rightTrigger.toBits())
        assertEquals(0.0.toBits(), localized.intensity.toBits())
    }

    @Test
    fun localizedHapticCapabilitiesRequireAndDescribeTheirLocalities() {
        val localities = GamepadLocalizedHapticConstraints(
            setOf(GamepadHapticLocality.Default, GamepadHapticLocality.LeftHandle),
        )
        val constraints = GamepadEffectConstraints(
            kinds = setOf(GamepadEffectKind.LocalizedHaptic),
            localizedHaptics = localities,
            maximumDuration = 1.seconds,
        )

        assertEquals(localities, constraints.localizedHaptics)
        assertFailsWith<IllegalArgumentException> {
            GamepadEffectConstraints(
                kinds = setOf(GamepadEffectKind.LocalizedHaptic),
                localizedHaptics = null,
                maximumDuration = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GamepadEffectConstraints(
                kinds = setOf(GamepadEffectKind.DualRumble),
                localizedHaptics = localities,
                maximumDuration = null,
            )
        }
    }

    @Test
    fun effectConstraintsSnapshotCallerOwnedSets() {
        val localities = mutableSetOf(GamepadHapticLocality.Default)
        val kinds = mutableSetOf(GamepadEffectKind.LocalizedHaptic)
        val constraints = GamepadEffectConstraints(
            kinds = kinds,
            localizedHaptics = GamepadLocalizedHapticConstraints(localities),
            maximumDuration = null,
        )

        localities += GamepadHapticLocality.LeftHandle
        kinds += GamepadEffectKind.DualRumble

        assertEquals(setOf(GamepadHapticLocality.Default), constraints.localizedHaptics?.localities)
        assertEquals(setOf(GamepadEffectKind.LocalizedHaptic), constraints.kinds)
    }
}
