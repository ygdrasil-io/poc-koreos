package org.graphiks.kadre.core

data class GamepadId(val value: Int)

enum class Button {
    South, East, North, West,
    C, Z,
    LeftTrigger, LeftTrigger2,
    RightTrigger, RightTrigger2,
    Select, Start, Mode,
    LeftThumb, RightThumb,
    DPadUp, DPadDown, DPadLeft, DPadRight;

    companion object {
        fun fromOrdinal(v: Int): Button = entries.getOrElse(v) { South }
    }
}

enum class Axis {
    LeftStickX, LeftStickY, LeftZ,
    RightStickX, RightStickY, RightZ,
    DPadX, DPadY;

    companion object {
        fun fromOrdinal(v: Int): Axis = entries.getOrElse(v) { LeftStickX }
    }
}

data class GamepadState(
    val buttons: Map<Button, Float> = emptyMap(),
    val axes: Map<Axis, Float> = emptyMap(),
)

sealed interface PowerInfo {
    data object Unknown : PowerInfo
    data object Wired : PowerInfo
    data class Discharging(val battery: Int) : PowerInfo
    data class Charging(val battery: Int) : PowerInfo
    data object Charged : PowerInfo
}
