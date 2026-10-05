package org.graphiks.kadre.platform.web

import org.graphiks.kadre.input.GamepadAxis
import org.graphiks.kadre.input.GamepadAxisValue
import org.graphiks.kadre.input.GamepadButton
import org.graphiks.kadre.input.GamepadButtonValue
import org.graphiks.kadre.input.GamepadDescriptor
import org.graphiks.kadre.input.GamepadMapping
import org.graphiks.kadre.input.GamepadState

/**
 * The pure rule that dresses one browser poll ([WebDomGamepad]) in Kadre's gamepad model: which
 * controls exist (the [descriptor]) and what they read (the [state]). No DOM type reaches here, so
 * every rule of it is unit-testable on both targets.
 *
 * Two rules only, both dictated by the model rather than invented here:
 *
 * - **The descriptor is the browser's own word.** `mapping == "standard"` names the DOM standard
 *   layout — exactly the 17 buttons and 4 axes below, in DOM order; anything else (an empty string,
 *   a vendor string, an absent member) means the browser promises nothing, so every control becomes
 *   a native code in the shape `validateGamepadCode` accepts, at whatever count the pad reports.
 *   The DOM `id` is honest metadata and passes through verbatim — the model treats a name as
 *   nullable display metadata, so there is nothing to truncate and nothing to invent.
 * - **The state pairs positionally against the descriptor**, whatever its shape: reading *i* of the
 *   pad belongs to control *i* of the descriptor. A standard-named pad whose browser reports an
 *   unusual count still builds a state the runtime's exact-match validation accepts (missing
 *   readings read neutral, extra ones are dropped). Values flow in unfiltered — the seam drops
 *   nothing and crashes never — and what reaches the model is canonical: the foundation's
 *   `GamepadButtonValue`/`GamepadAxisValue` require finite values inside their canonical windows,
 *   so an out-of-window reading is clamped to the window it belongs to and a non-finite one reads
 *   neutral, while the press threshold is answered from the reading itself, before any clamping.
 */
internal object WebGamepadMapping {
    /** The DOM standard mapping's buttons, in DOM order — the layout `mapping == "standard"` names. */
    private val standardButtons = listOf(
        GamepadButton.South, GamepadButton.East, GamepadButton.West, GamepadButton.North,
        GamepadButton.LeftShoulder, GamepadButton.RightShoulder, GamepadButton.LeftTrigger, GamepadButton.RightTrigger,
        GamepadButton.Select, GamepadButton.Start, GamepadButton.LeftStick, GamepadButton.RightStick,
        GamepadButton.DpadUp, GamepadButton.DpadDown, GamepadButton.DpadLeft, GamepadButton.DpadRight,
        GamepadButton.Mode,
    )

    /** The DOM standard mapping's axes, in DOM order. */
    private val standardAxes = listOf(GamepadAxis.LeftX, GamepadAxis.LeftY, GamepadAxis.RightX, GamepadAxis.RightY)

    /** The mapping string the Gamepad API reserves for its standard layout; anything else is native. */
    private const val STANDARD_MAPPING = "standard"

    /**
     * The descriptor the pad advertises, from the browser's own mapping word and control counts.
     *
     * Frozen by the caller at connection: a reconnect is a new connection and may report a
     * different shape, but one connection never rewrites its descriptor.
     */
    fun descriptor(pad: WebDomGamepad): GamepadDescriptor {
        val standard = pad.mapping == STANDARD_MAPPING
        return GamepadDescriptor(
            name = pad.domId,
            mapping = if (standard) GamepadMapping.Standard else GamepadMapping.Native,
            buttons = if (standard) standardButtons else pad.buttonValues.indices.map { GamepadButton.Other("button-$it") },
            axes = if (standard) standardAxes else pad.axisValues.indices.map { GamepadAxis.Other("axis-$it") },
        )
    }

    /**
     * What the pad's controls read, paired positionally against [descriptor] — the exact
     * buttons-then-axes order [org.graphiks.kadre.internal.runtime.RuntimeGamepadManager] validates
     * every state against. The DOM's press model (`value >= 0.5`) is the model's press; the axis
     * window is `[-1, 1]` and the button window `[0, 1]`.
     */
    fun state(pad: WebDomGamepad, descriptor: GamepadDescriptor): GamepadState = GamepadState(
        buttons = descriptor.buttons.mapIndexed { index, button ->
            val raw = pad.buttonValues.getOrElse(index) { 0.0 }
            GamepadButtonValue(button, canonicalButton(raw), raw >= 0.5)
        },
        axes = descriptor.axes.mapIndexed { index, axis ->
            GamepadAxisValue(axis, canonicalAxis(pad.axisValues.getOrElse(index) { 0.0 }))
        },
    )

    /**
     * The neutral reading of [descriptor]: every button unpressed at zero, every axis at zero — the
     * construction `RuntimeGamepadManager` publishes for a suspended or disconnected gamepad. A
     * suspended projection publishes exactly this while its real readings stay recorded for later.
     */
    fun neutralState(descriptor: GamepadDescriptor): GamepadState = GamepadState(
        buttons = descriptor.buttons.map { button -> GamepadButtonValue(button, 0.0, false) },
        axes = descriptor.axes.map { axis -> GamepadAxisValue(axis, 0.0) },
    )

    /** The reading as a canonical button value: in `[0, 1]` and finite, or neutral. */
    private fun canonicalButton(raw: Double): Double = if (raw.isFinite()) raw.coerceIn(0.0, 1.0) else 0.0

    /** The reading as a canonical axis value: in `[-1, 1]` and finite, or neutral. */
    private fun canonicalAxis(raw: Double): Double = if (raw.isFinite()) raw.coerceIn(-1.0, 1.0) else 0.0
}
