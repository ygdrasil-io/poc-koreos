package org.graphiks.kadre.application

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

class LifecycleValuesTest {
    @Test
    fun terminatedLifecycleMustBeBackgroundAndInactive() {
        assertFailsWith<IllegalArgumentException> {
            LifecycleState(SessionPhase.Terminated, VisibilityState.Foreground, ActivationState.Active)
        }
        assertFailsWith<IllegalArgumentException> {
            LifecycleState(SessionPhase.Terminated, VisibilityState.Foreground, ActivationState.Inactive)
        }
        assertFailsWith<IllegalArgumentException> {
            LifecycleState(SessionPhase.Terminated, VisibilityState.Background, ActivationState.Active)
        }
    }

    @Test
    fun activeRequiresRunningAndForeground() {
        assertFailsWith<IllegalArgumentException> {
            LifecycleState(SessionPhase.NotStarted, VisibilityState.Foreground, ActivationState.Active)
        }
        assertFailsWith<IllegalArgumentException> {
            LifecycleState(SessionPhase.Running, VisibilityState.Background, ActivationState.Active)
        }
    }

    @Test
    fun validConstructionIsAccepted() {
        assertEquals(
            LifecycleState(SessionPhase.Running, VisibilityState.Foreground, ActivationState.Active),
            LifecycleState(
                phase = SessionPhase.Running,
                visibility = VisibilityState.Foreground,
                activation = ActivationState.Active,
            ),
        )
    }
}
