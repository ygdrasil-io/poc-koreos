package org.graphiks.kadre.application

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.graphiks.kadre.diagnostics.FeatureAvailability

public interface KadreLifecycle {
    public val state: StateFlow<LifecycleState>
    public val capabilities: StateFlow<LifecycleCapabilities>
    public val events: Flow<LifecycleEvent>
    public val signals: Flow<HostSignal>
}

public data class LifecycleCapabilities(public val memoryPressure: FeatureAvailability)

public data class LifecycleState(
    public val attachment: AttachmentState,
    public val visibility: VisibilityState,
    public val activation: ActivationState,
) {
    init {
        if (attachment == AttachmentState.Detached) {
            require(visibility == VisibilityState.Background && activation == ActivationState.Inactive) {
                "detached lifecycle must be background and inactive"
            }
        }
        if (visibility == VisibilityState.Background) {
            require(activation == ActivationState.Inactive) { "background lifecycle must be inactive" }
        }
        if (activation == ActivationState.Active) {
            require(attachment == AttachmentState.Attached && visibility == VisibilityState.Foreground) {
                "active lifecycle must be attached and foreground"
            }
        }
    }
}

public enum class AttachmentState { Attached, Detached }
public enum class VisibilityState { Foreground, Background }
public enum class ActivationState { Active, Inactive }

public data class LifecycleEvent(
    public val previous: LifecycleState,
    public val current: LifecycleState,
    public val stamp: EventStamp,
)

public sealed interface HostSignal {
    public val stamp: EventStamp

    public data class MemoryPressure(
        public val level: MemoryPressureLevel,
        override val stamp: EventStamp,
    ) : HostSignal
}

public enum class MemoryPressureLevel { Moderate, Critical }
