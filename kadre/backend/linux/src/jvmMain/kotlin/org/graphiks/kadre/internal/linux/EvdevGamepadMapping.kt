package org.graphiks.kadre.internal.linux

import org.graphiks.kadre.input.GamepadAxis
import org.graphiks.kadre.input.GamepadAxisValue
import org.graphiks.kadre.input.GamepadButton
import org.graphiks.kadre.input.GamepadButtonValue
import org.graphiks.kadre.input.GamepadDescriptor
import org.graphiks.kadre.input.GamepadMapping
import org.graphiks.kadre.input.GamepadState

// Kernel gamepad-API codes (linux/input-event-codes.h) behind the standard
// mapping — the analog triggers and the dpad surface as buttons on some
// devices and as abs axes on others; both shapes map onto the model.
internal object EvdevCodes {
    const val BTN_SOUTH = 0x130
    const val BTN_EAST = 0x131
    const val BTN_NORTH = 0x133
    const val BTN_WEST = 0x134
    const val BTN_TL = 0x136
    const val BTN_TR = 0x137
    const val BTN_TL2 = 0x138
    const val BTN_TR2 = 0x139
    const val BTN_SELECT = 0x13a
    const val BTN_START = 0x13b
    const val BTN_MODE = 0x13c
    const val BTN_THUMBL = 0x13d
    const val BTN_THUMBR = 0x13e

    const val BTN_DPAD_UP = 0x220
    const val BTN_DPAD_DOWN = 0x221
    const val BTN_DPAD_LEFT = 0x222
    const val BTN_DPAD_RIGHT = 0x223

    const val ABS_X = 0x00
    const val ABS_Y = 0x01
    const val ABS_Z = 0x02
    const val ABS_RX = 0x03
    const val ABS_RY = 0x04
    const val ABS_RZ = 0x05
    const val ABS_HAT0X = 0x10
    const val ABS_HAT0Y = 0x11

    fun mapButton(code: Int): GamepadButton? = when (code) {
        BTN_SOUTH -> GamepadButton.South
        BTN_EAST -> GamepadButton.East
        BTN_NORTH -> GamepadButton.North
        BTN_WEST -> GamepadButton.West
        BTN_TL -> GamepadButton.LeftShoulder
        BTN_TR -> GamepadButton.RightShoulder
        BTN_SELECT -> GamepadButton.Select
        BTN_START -> GamepadButton.Start
        BTN_MODE -> GamepadButton.Mode
        BTN_THUMBL -> GamepadButton.LeftStick
        BTN_THUMBR -> GamepadButton.RightStick
        // analog-trigger buttons feed the trigger axes, not the button set
        BTN_TL2, BTN_TR2, BTN_DPAD_UP, BTN_DPAD_DOWN, BTN_DPAD_LEFT, BTN_DPAD_RIGHT -> null
        else -> null
    }

    fun mapAxis(code: Int): GamepadAxis? = when (code) {
        ABS_X -> GamepadAxis.LeftX
        ABS_Y -> GamepadAxis.LeftY
        ABS_RX -> GamepadAxis.RightX
        ABS_RY -> GamepadAxis.RightY
        ABS_Z -> GamepadAxis.LeftTrigger
        ABS_RZ -> GamepadAxis.RightTrigger
        ABS_HAT0X -> GamepadAxis.DpadX
        ABS_HAT0Y -> GamepadAxis.DpadY
        else -> null
    }

    /** Button codes that synthesize axis state (triggers, dpad). */
    fun syntheticAxis(code: Int): GamepadAxis? = when (code) {
        BTN_TL2 -> GamepadAxis.LeftTrigger
        BTN_TR2 -> GamepadAxis.RightTrigger
        else -> null
    }
}

/**
 * One live device tracked by the broker: the standard-map projection of
 * its capability codes plus its current state. Snapshots are pure
 * functions of the state — the runtime validates them against the
 * descriptor (exact order, no duplicates) on every event.
 */
internal class EvdevGamepad(
    val key: Long,
    val identity: String,
    val name: String?,
    /** Mapped button codes in ascending kernel order. */
    val buttonCodes: List<Int>,
    /** Mapped abs-axis codes in ascending kernel order. */
    val axisCodes: List<Int>,
    /** Abs bounds per axis code, (minimum, maximum). */
    val axisBounds: Map<Int, Pair<Int, Int>>,
    /** Button codes that synthesize axis values (dpad pairs, triggers). */
    val syntheticButtonCodes: List<Int>,
    val forceFeedback: Boolean,
) {
    private val pressedButtons = HashMap<Int, Boolean>()
    private val absValues = HashMap<Int, Int>()

    /** Dpad presses resolve through both members of the pair. */
    private val dpadUp get() = pressedButtons[EvdevCodes.BTN_DPAD_UP] == true
    private val dpadDown get() = pressedButtons[EvdevCodes.BTN_DPAD_DOWN] == true
    private val dpadLeft get() = pressedButtons[EvdevCodes.BTN_DPAD_LEFT] == true
    private val dpadRight get() = pressedButtons[EvdevCodes.BTN_DPAD_RIGHT] == true

    val descriptor: GamepadDescriptor by lazy {
        GamepadDescriptor(
            name = name,
            mapping = GamepadMapping.Standard,
            buttons = buttonCodes.mapNotNull(EvdevCodes::mapButton),
            // a device exposing both the abs axis and its button twin (e.g.
            // ABS_Z and BTN_TL2) keeps the abs form only — no duplicates
            axes = buildList {
                val absAxes = axisCodes.mapNotNull(EvdevCodes::mapAxis)
                addAll(absAxes)
                addAll(syntheticAxes().keys.filterNot { it in absAxes })
            },
        )
    }

    /**
     * Axis state synthesized from button codes: always present once the
     * capability exists (idle = 0), so the state list matches the
     * descriptor list exactly.
     */
    private fun syntheticAxes(): LinkedHashMap<GamepadAxis, Int> {
        val axes = LinkedHashMap<GamepadAxis, Int>()
        syntheticButtonCodes.forEach { code ->
            when (code) {
                EvdevCodes.BTN_TL2 -> axes[GamepadAxis.LeftTrigger] = if (pressedButtons[code] == true) 1 else 0
                EvdevCodes.BTN_TR2 -> axes[GamepadAxis.RightTrigger] = if (pressedButtons[code] == true) 1 else 0
                EvdevCodes.BTN_DPAD_UP, EvdevCodes.BTN_DPAD_DOWN ->
                    axes[GamepadAxis.DpadY] = (if (dpadDown) 1 else 0) - (if (dpadUp) 1 else 0)
                EvdevCodes.BTN_DPAD_LEFT, EvdevCodes.BTN_DPAD_RIGHT ->
                    axes[GamepadAxis.DpadX] = (if (dpadRight) 1 else 0) - (if (dpadLeft) 1 else 0)
            }
        }
        return axes
    }

    /** Applies one decoded kernel event; true when the projection changed. */
    fun apply(type: Int, code: Int, value: Int): Boolean {
        return when (type) {
            EV_TYPE_KEY -> {
                if (code !in buttonCodes && code !in syntheticButtonCodes) return false
                val next = value != 0
                if (pressedButtons[code] == next) return false
                pressedButtons[code] = next
                true
            }
            EV_TYPE_ABS -> {
                if (code !in axisCodes) return false
                if (absValues[code] == value) return false
                absValues[code] = value
                true
            }
            else -> false
        }
    }

    fun snapshotState(): GamepadState = GamepadState(
        buttons = buttonCodes.mapNotNull { code ->
            EvdevCodes.mapButton(code)?.let { button ->
                val pressed = pressedButtons[code] == true
                GamepadButtonValue(button, if (pressed) 1.0 else 0.0, pressed)
            }
        },
        axes = buildList {
            val absAxes = axisCodes.mapNotNull { code ->
                EvdevCodes.mapAxis(code)?.let { axis -> axis to normalizeAxis(code) }
            }
            absAxes.forEach { (axis, value) -> add(GamepadAxisValue(axis, value)) }
            val absAxisKinds = absAxes.map { it.first }
            syntheticAxes().forEach { (axis, value) ->
                if (axis !in absAxisKinds) add(GamepadAxisValue(axis, value.toDouble()))
            }
        },
    )

    /** Normalizes the raw kernel value to [-1, 1] against the abs bounds. */
    private fun normalizeAxis(code: Int): Double {
        val raw = absValues[code] ?: axisBounds[code]?.first ?: 0
        val (minimum, maximum) = axisBounds[code] ?: (0 to 0)
        if (maximum == minimum) return 0.0
        val clamped = raw.coerceIn(minimum, maximum)
        return ((clamped - minimum).toDouble() / (maximum - minimum).toDouble()) * 2.0 - 1.0
    }

    private companion object {
        const val EV_TYPE_KEY = 0x01
        const val EV_TYPE_ABS = 0x03
    }
}
