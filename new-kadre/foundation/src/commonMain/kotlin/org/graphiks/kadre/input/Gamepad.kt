package org.graphiks.kadre.input

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import kotlin.time.Duration

public interface Gamepad {
    public val id: GamepadId
    public val state: StateFlow<GamepadSnapshot>
    public val events: Flow<GamepadEvent>

    public suspend fun playEffect(effect: GamepadEffect): KadreResult<GamepadEffectSession>
    public suspend fun stopEffects(): KadreResult<Unit>
}

public data class GamepadSnapshot(
    public val descriptor: GamepadDescriptor,
    public val connection: DeviceConnectionState,
    public val routing: GamepadRoutingState,
    public val controls: GamepadState,
    public val capabilities: GamepadCapabilities,
    public val revision: GamepadRevision,
) {
    init {
        if (connection == DeviceConnectionState.Disconnected) {
            require(routing == GamepadRoutingState.Suspended) { "disconnected gamepad must be suspended" }
        }
    }
}

public enum class GamepadRoutingState { Routed, Suspended }
public enum class GamepadMapping { Standard, Native }

public sealed interface GamepadButton {
    public data object South : GamepadButton
    public data object East : GamepadButton
    public data object West : GamepadButton
    public data object North : GamepadButton
    public data object LeftShoulder : GamepadButton
    public data object RightShoulder : GamepadButton
    public data object LeftTrigger : GamepadButton
    public data object RightTrigger : GamepadButton
    public data object Select : GamepadButton
    public data object Start : GamepadButton
    public data object Mode : GamepadButton
    public data object LeftStick : GamepadButton
    public data object RightStick : GamepadButton
    public data object DpadUp : GamepadButton
    public data object DpadDown : GamepadButton
    public data object DpadLeft : GamepadButton
    public data object DpadRight : GamepadButton

    public data class Other(public val nativeCode: String) : GamepadButton {
        init { validateGamepadCode(nativeCode) }
    }
}

public sealed interface GamepadAxis {
    public data object LeftX : GamepadAxis
    public data object LeftY : GamepadAxis
    public data object RightX : GamepadAxis
    public data object RightY : GamepadAxis
    public data object LeftTrigger : GamepadAxis
    public data object RightTrigger : GamepadAxis
    public data object DpadX : GamepadAxis
    public data object DpadY : GamepadAxis

    public data class Other(public val nativeCode: String) : GamepadAxis {
        init { validateGamepadCode(nativeCode) }
    }
}

public data class GamepadDescriptor(
    public val name: String?,
    public val mapping: GamepadMapping,
    public val buttons: List<GamepadButton>,
    public val axes: List<GamepadAxis>,
) {
    init {
        require(buttons.distinct().size == buttons.size) { "buttons must not contain duplicates" }
        require(axes.distinct().size == axes.size) { "axes must not contain duplicates" }
    }
}

public class GamepadButtonValue(
    public val button: GamepadButton,
    value: Double,
    public val pressed: Boolean,
) {
    public val value: Double = canonicalRange(value, 0.0, 1.0, "button value")
    public operator fun component1(): GamepadButton = button
    public operator fun component2(): Double = value
    public operator fun component3(): Boolean = pressed
    public fun copy(
        button: GamepadButton = this.button,
        value: Double = this.value,
        pressed: Boolean = this.pressed,
    ): GamepadButtonValue = GamepadButtonValue(button, value, pressed)
    override fun equals(other: Any?): Boolean =
        other is GamepadButtonValue && button == other.button && value == other.value && pressed == other.pressed
    override fun hashCode(): Int = 31 * (31 * button.hashCode() + value.hashCode()) + pressed.hashCode()
    override fun toString(): String = "GamepadButtonValue(button=$button, value=$value, pressed=$pressed)"
}

public class GamepadAxisValue(public val axis: GamepadAxis, value: Double) {
    public val value: Double = canonicalRange(value, -1.0, 1.0, "axis value")
    public operator fun component1(): GamepadAxis = axis
    public operator fun component2(): Double = value
    public fun copy(axis: GamepadAxis = this.axis, value: Double = this.value): GamepadAxisValue =
        GamepadAxisValue(axis, value)
    override fun equals(other: Any?): Boolean = other is GamepadAxisValue && axis == other.axis && value == other.value
    override fun hashCode(): Int = 31 * axis.hashCode() + value.hashCode()
    override fun toString(): String = "GamepadAxisValue(axis=$axis, value=$value)"
}

public data class GamepadState(
    public val buttons: List<GamepadButtonValue>,
    public val axes: List<GamepadAxisValue>,
) {
    init {
        require(buttons.map(GamepadButtonValue::button).distinct().size == buttons.size) {
            "button state must not contain duplicates"
        }
        require(axes.map(GamepadAxisValue::axis).distinct().size == axes.size) {
            "axis state must not contain duplicates"
        }
    }
}

public data class GamepadCapabilities(public val effects: Capability<GamepadEffectConstraints>)
public enum class GamepadEffectKind { DualRumble, TriggerRumble }

public data class GamepadEffectConstraints(
    public val kinds: Set<GamepadEffectKind>,
    public val maximumDuration: Duration?,
) {
    init {
        require(kinds.isNotEmpty()) { "kinds must not be empty" }
        require(maximumDuration == null || maximumDuration.isFinite() && maximumDuration.isPositive()) {
            "maximumDuration must be finite and positive"
        }
    }
}

public sealed interface GamepadEvent {
    public val stamp: EventStamp
    public val revision: GamepadRevision

    public data class ButtonChanged(
        public val value: GamepadButtonValue,
        override val revision: GamepadRevision,
        override val stamp: EventStamp,
    ) : GamepadEvent

    public data class AxisChanged(
        public val value: GamepadAxisValue,
        override val revision: GamepadRevision,
        override val stamp: EventStamp,
    ) : GamepadEvent

    public data class RoutingSuspended(
        override val revision: GamepadRevision,
        override val stamp: EventStamp,
    ) : GamepadEvent

    public data class RoutingResumed(
        override val revision: GamepadRevision,
        override val stamp: EventStamp,
    ) : GamepadEvent
}

public sealed interface GamepadEffect {
    public val duration: Duration

    public data class DualRumble(
        public val strong: Double,
        public val weak: Double,
        override val duration: Duration,
    ) : GamepadEffect {
        init {
            validateEffectValue(strong, "strong")
            validateEffectValue(weak, "weak")
            validateEffectDuration(duration)
        }
    }

    public data class TriggerRumble(
        public val left: Double,
        public val right: Double,
        override val duration: Duration,
    ) : GamepadEffect {
        init {
            validateEffectValue(left, "left")
            validateEffectValue(right, "right")
            validateEffectDuration(duration)
        }
    }
}

public interface GamepadEffectSession : AutoCloseable {
    public val state: StateFlow<GamepadEffectState>
    override fun close()
    public fun requestStop()
    public suspend fun awaitTermination(): GamepadEffectOutcome
}

public sealed interface GamepadEffectState {
    public data object Starting : GamepadEffectState
    public data object Playing : GamepadEffectState
    public data object Stopping : GamepadEffectState
    public data class Terminated(public val outcome: GamepadEffectOutcome) : GamepadEffectState
}

public sealed interface GamepadEffectOutcome {
    public data object Completed : GamepadEffectOutcome
    public data class Stopped(public val reason: GamepadEffectStopReason) : GamepadEffectOutcome
    public data class Failed(public val failure: KadreFailure) : GamepadEffectOutcome
}

public enum class GamepadEffectStopReason {
    Requested,
    DeviceDisconnected,
    OwnershipLost,
    ParentSessionStopping,
}

private fun validateGamepadCode(value: String) {
    require(value.isNotEmpty() && value.all { it.code in 0x21..0x7e }) {
        "nativeCode must be a non-empty ASCII identifier"
    }
}

private fun validateEffectValue(value: Double, name: String) {
    require(value.isFinite() && value in 0.0..1.0) { "$name must be in [0, 1]" }
}

private fun validateEffectDuration(value: Duration) {
    require(value.isFinite() && value.isPositive()) { "duration must be finite and positive" }
}

private fun canonicalRange(value: Double, minimum: Double, maximum: Double, name: String): Double {
    require(value.isFinite() && value in minimum..maximum) { "$name must be in [$minimum, $maximum]" }
    return if (value == 0.0) 0.0 else value
}
