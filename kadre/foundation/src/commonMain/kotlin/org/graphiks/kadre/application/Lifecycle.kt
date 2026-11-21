package org.graphiks.kadre.application

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.graphiks.kadre.diagnostics.FeatureAvailability

public interface KadreLifecycle {
    public val state: StateFlow<LifecycleState>
    public val capabilities: StateFlow<LifecycleCapabilities>
    public val events: Flow<LifecycleEvent>
    public val signals: Flow<SystemSignal>
}

public data class LifecycleCapabilities(public val memoryPressure: FeatureAvailability)

/**
 * The lifecycle of the session's process, observed by the application: the session phase
 * mirrors [SessionState], and the visibility/activation axes carry what the OS observes of
 * the process's containers.
 */
public data class LifecycleState(
    public val phase: SessionPhase,
    public val visibility: VisibilityState,
    public val activation: ActivationState,
) {
    init {
        if (phase == SessionPhase.Terminated) {
            require(visibility == VisibilityState.Background && activation == ActivationState.Inactive) {
                "terminated lifecycle must be background and inactive"
            }
        }
        if (visibility == VisibilityState.Background) {
            require(activation == ActivationState.Inactive) { "background lifecycle must be inactive" }
        }
        if (activation == ActivationState.Active) {
            require(phase == SessionPhase.Running && visibility == VisibilityState.Foreground) {
                "active lifecycle must be running and foreground"
            }
        }
    }
}

public enum class VisibilityState { Foreground, Background }
public enum class ActivationState { Active, Inactive }

public enum class SessionPhase { NotStarted, Running, Terminating, Terminated }

public data class LifecycleEvent(
    public val previous: LifecycleState,
    public val current: LifecycleState,
    public val stamp: EventStamp,
)

public sealed interface SystemSignal {
    public val stamp: EventStamp

    public data class MemoryPressure(
        public val level: MemoryPressureLevel,
        override val stamp: EventStamp,
    ) : SystemSignal
}

public enum class MemoryPressureLevel { Moderate, Critical }
