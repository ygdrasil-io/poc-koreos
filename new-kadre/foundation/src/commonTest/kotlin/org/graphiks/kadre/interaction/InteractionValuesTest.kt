package org.graphiks.kadre.interaction

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class InteractionValuesTest {
    @Test
    fun armedConstraintsMustHaveActionsAndTriggers() {
        assertFailsWith<IllegalArgumentException> {
            ArmedInteractionConstraints(emptySet(), setOf(InteractionTriggerKind.AnyActivation))
        }
        assertFailsWith<IllegalArgumentException> {
            ArmedInteractionConstraints(setOf(InteractionKind.OpenWindow), emptySet())
        }
    }

    @Test
    fun expirationMustBeFiniteAndPositive() {
        assertFailsWith<IllegalArgumentException> {
            InteractionArmOptions(Duration.ZERO)
        }
        assertFailsWith<IllegalArgumentException> {
            InteractionArmOptions(Duration.INFINITE)
        }
        InteractionArmOptions(1.seconds)
    }
}
