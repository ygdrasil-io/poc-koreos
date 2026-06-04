package org.graphiks.kadre.core

sealed interface GamepadEvent {
    val id: GamepadId
    val time: Long

    data class ButtonPressed(
        override val id: GamepadId,
        val button: Button,
        override val time: Long = 0L,
    ) : GamepadEvent

    data class ButtonReleased(
        override val id: GamepadId,
        val button: Button,
        override val time: Long = 0L,
    ) : GamepadEvent

    data class AxisChanged(
        override val id: GamepadId,
        val axis: Axis,
        val value: Float,
        override val time: Long = 0L,
    ) : GamepadEvent

    data class Connected(
        override val id: GamepadId,
        val name: String,
        override val time: Long = 0L,
    ) : GamepadEvent

    data class Disconnected(
        override val id: GamepadId,
        override val time: Long = 0L,
    ) : GamepadEvent
}
