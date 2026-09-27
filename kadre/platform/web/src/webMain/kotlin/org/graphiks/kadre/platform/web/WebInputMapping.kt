package org.graphiks.kadre.platform.web

import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.ModifierKey
import org.graphiks.kadre.input.NamedKey
import org.graphiks.kadre.input.PenState
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.input.ScrollDelta
import kotlin.math.PI

/**
 * The one mapping core both Web targets share: browser primitives in, common Kadre input values out.
 *
 * `DESIGN.md` §15.3 requires the `js` and `wasmJs` targets to expose the same public contract with
 * only their internal core shared. The mapping from a browser event to the common input model is the
 * substance of that core: a DOM port of either target reads its own fields and calls these functions,
 * so the two targets cannot drift on which HID usage a `code` means, which modifier a flag is, or
 * which delta mode is deliverable.
 *
 * Nothing here imports a DOM type, and every parameter is a primitive (`String`, `Int`, `Double`,
 * `Boolean`), which is what makes the core testable in `webTest` — a suite both Karma targets run —
 * without a browser.
 *
 * Two properties are part of the contract:
 *
 * - **Totality.** Every function is defined for every input a browser can hand it, including hostile
 *   and out-of-domain values. A mapping never throws: where the common model validates a domain
 *   (the opaque token of `Unidentified`, the ranges of `PenState`, the finite components of
 *   `ScrollDelta`), this core produces a value the model accepts instead of passing the model
 *   something it would reject.
 * - **No approximation.** A primitive the model cannot carry is dropped, never clamped, rescaled or
 *   wrapped into range, and a primitive the model can carry is kept as the browser reported it. An
 *   unmappable key becomes `Unidentified` with its own native token when that token satisfies the
 *   model's rule and with `Unidentified(null)` when it cannot be a token at all; an unrepresentable
 *   measurement becomes `null`, so the observation stays visible as an absence rather than as a
 *   number Kadre invented.
 */
/**
 * Maps `KeyboardEvent.code` to the physical key of the model.
 *
 * A `code` the HID table does not enumerate is not a lost observation: it becomes
 * `Unidentified` with the code as its native token, which is what that member exists for — the same
 * shape AppKit gives a key code outside its own table (`PhysicalKey.Unidentified("mac:<keyCode>")`).
 * The token is sanitised first, so a code that is not a stable ASCII identifier (an empty one, a
 * non-ASCII one, one with a space in it, one over 256 code units) is carried as no token at all
 * rather than as a value the model would reject.
 */
internal fun webPhysicalKey(code: String): PhysicalKey =
    webHidUsageForCode(code)
        ?.let { usageId -> PhysicalKey.Code(usagePage = WEB_KEYBOARD_USAGE_PAGE, usageId = usageId) }
        ?: PhysicalKey.Unidentified(webNativeToken(code))

/** The HID usage of one `code`, or `null` when the table does not enumerate that key. */
private fun webHidUsageForCode(code: String): Int? = when (code) {
    // Source: USB HID Usage Tables 1.12, usage page 0x07 (Keyboard/Keypad). The `code` of the DOM is
    // the physical key of the US layout, which is exactly what the HID usage table enumerates: the
    // letters, digits, punctuation, function, navigation, keypad and modifier blocks below are its
    // usages in order. Cross-checked against the `kHIDUsage` constants of macOS and the Linux
    // `input-event-codes` key table.
    //
    // Usages 0x04..0x1D: Keyboard a..z.
    "KeyA" -> 0x04
    "KeyB" -> 0x05
    "KeyC" -> 0x06
    "KeyD" -> 0x07
    "KeyE" -> 0x08
    "KeyF" -> 0x09
    "KeyG" -> 0x0a
    "KeyH" -> 0x0b
    "KeyI" -> 0x0c
    "KeyJ" -> 0x0d
    "KeyK" -> 0x0e
    "KeyL" -> 0x0f
    "KeyM" -> 0x10
    "KeyN" -> 0x11
    "KeyO" -> 0x12
    "KeyP" -> 0x13
    "KeyQ" -> 0x14
    "KeyR" -> 0x15
    "KeyS" -> 0x16
    "KeyT" -> 0x17
    "KeyU" -> 0x18
    "KeyV" -> 0x19
    "KeyW" -> 0x1a
    "KeyX" -> 0x1b
    "KeyY" -> 0x1c
    "KeyZ" -> 0x1d

    // Usages 0x1E..0x27: Keyboard 1..0.
    "Digit1" -> 0x1e
    "Digit2" -> 0x1f
    "Digit3" -> 0x20
    "Digit4" -> 0x21
    "Digit5" -> 0x22
    "Digit6" -> 0x23
    "Digit7" -> 0x24
    "Digit8" -> 0x25
    "Digit9" -> 0x26
    "Digit0" -> 0x27

    // Usages 0x28..0x39: Return, Escape, Delete (Backspace), Tab, Spacebar, and the punctuation run.
    "Enter" -> 0x28
    "Escape" -> 0x29
    "Backspace" -> 0x2a
    "Tab" -> 0x2b
    "Space" -> 0x2c
    "Minus" -> 0x2d
    "Equal" -> 0x2e
    "BracketLeft" -> 0x2f
    "BracketRight" -> 0x30
    "Backslash" -> 0x31
    "Semicolon" -> 0x33
    "Quote" -> 0x34
    "Backquote" -> 0x35
    "Comma" -> 0x36
    "Period" -> 0x37
    "Slash" -> 0x38
    "CapsLock" -> 0x39

    // Usages 0x3A..0x45: Keyboard F1..F12.
    "F1" -> 0x3a
    "F2" -> 0x3b
    "F3" -> 0x3c
    "F4" -> 0x3d
    "F5" -> 0x3e
    "F6" -> 0x3f
    "F7" -> 0x40
    "F8" -> 0x41
    "F9" -> 0x42
    "F10" -> 0x43
    "F11" -> 0x44
    "F12" -> 0x45

    // Usages 0x46..0x52: PrintScreen, Scroll Lock, Pause, the editing-navigation keys, and the arrows.
    "PrintScreen" -> 0x46
    "ScrollLock" -> 0x47
    "Pause" -> 0x48
    "Insert" -> 0x49
    "Home" -> 0x4a
    "PageUp" -> 0x4b
    "Delete" -> 0x4c
    "End" -> 0x4d
    "PageDown" -> 0x4e
    "ArrowRight" -> 0x4f
    "ArrowLeft" -> 0x50
    "ArrowDown" -> 0x51
    "ArrowUp" -> 0x52

    // Usages 0x53..0x63 and 0x67: the keypad, Num Lock included.
    "NumLock" -> 0x53
    "NumpadDivide" -> 0x54
    "NumpadMultiply" -> 0x55
    "NumpadSubtract" -> 0x56
    "NumpadAdd" -> 0x57
    "NumpadEnter" -> 0x58
    "Numpad1" -> 0x59
    "Numpad2" -> 0x5a
    "Numpad3" -> 0x5b
    "Numpad4" -> 0x5c
    "Numpad5" -> 0x5d
    "Numpad6" -> 0x5e
    "Numpad7" -> 0x5f
    "Numpad8" -> 0x60
    "Numpad9" -> 0x61
    "Numpad0" -> 0x62
    "NumpadDecimal" -> 0x63
    "NumpadEqual" -> 0x67

    // Usages 0x64..0x65, 0x68..0x73, 0x85, 0x87 and 0x89: the layout-specific and menu keys, the
    // tail of the function row, the keypad comma, and the two international keys the DOM names.
    "IntlBackslash" -> 0x64
    "ContextMenu" -> 0x65
    "F13" -> 0x68
    "F14" -> 0x69
    "F15" -> 0x6a
    "F16" -> 0x6b
    "F17" -> 0x6c
    "F18" -> 0x6d
    "F19" -> 0x6e
    "F20" -> 0x6f
    "F21" -> 0x70
    "F22" -> 0x71
    "F23" -> 0x72
    "F24" -> 0x73
    "NumpadComma" -> 0x85
    "IntlRo" -> 0x87
    "IntlYen" -> 0x89

    // Usages 0xE0..0xE7: the left and right Control, Shift, Alt and GUI keys. The pair is distinct by
    // usage, which is how the model distinguishes the left modifier from the right one.
    "ControlLeft" -> 0xe0
    "ShiftLeft" -> 0xe1
    "AltLeft" -> 0xe2
    "MetaLeft" -> 0xe3
    "ControlRight" -> 0xe4
    "ShiftRight" -> 0xe5
    "AltRight" -> 0xe6
    "MetaRight" -> 0xe7

    // The legacy names browsers in the field still report for the two GUI keys.
    "OSLeft" -> 0xe3
    "OSRight" -> 0xe7

    else -> null
}

/**
 * Maps `KeyboardEvent.key` to the logical key it describes.
 *
 * A named key keeps its name — the lookup runs on the value as the browser reported it, so no named
 * key can be lost to sanitisation. A value of exactly one character (one code point, so a letter of
 * any script, a digit, a punctuation mark or an astral character) is the character the key produced
 * and keeps its case: the logical key of `"a"` is not the logical key of `"A"`, and a layout that
 * produces `"é"` or `"ß"` produces a character like any other. Everything else — a multi-character
 * value the model has no name for, an empty value — becomes `Unidentified` with the value sanitised
 * to the token the model accepts, or with no token at all when the value cannot be a token.
 */
internal fun webLogicalKey(key: String): LogicalKey {
    webNamedKeyForValue(key)?.let { named -> return LogicalKey.Named(named) }
    if (key.isSingleCharacter()) return LogicalKey.Character(key)
    return LogicalKey.Unidentified(webNativeToken(key))
}

/**
 * Whether the value is one character, counted in code points rather than in UTF-16 code units: an
 * astral character is one character reported as a surrogate pair.
 */
private fun String.isSingleCharacter(): Boolean =
    length == 1 || (length == 2 && this[0].isHighSurrogate() && this[1].isLowSurrogate())

/**
 * The `KeyboardEvent.key` values the common model names.
 *
 * Source: the key values registry of UI Events, both the current names and the legacy ones the same
 * registry lists for the same key (`OSLeft`/`OSRight` for the GUI keys, `VolumeUp`/`VolumeDown`/
 * `VolumeMute` for the audio keys): a browser may report either, and both mean that key. The media
 * names of the registry that differ from the model's own (`MediaTrackNext`, `MediaTrackPrevious`) are
 * listed next to the key they denote.
 */
private fun webNamedKeyForValue(key: String): NamedKey? = when (key) {
    "Enter" -> NamedKey.Enter
    "Tab" -> NamedKey.Tab
    " " -> NamedKey.Space
    "Backspace" -> NamedKey.Backspace
    "Escape" -> NamedKey.Escape
    "Delete" -> NamedKey.Delete
    "Insert" -> NamedKey.Insert
    "Home" -> NamedKey.Home
    "End" -> NamedKey.End
    "PageUp" -> NamedKey.PageUp
    "PageDown" -> NamedKey.PageDown
    "ArrowLeft" -> NamedKey.ArrowLeft
    "ArrowRight" -> NamedKey.ArrowRight
    "ArrowUp" -> NamedKey.ArrowUp
    "ArrowDown" -> NamedKey.ArrowDown
    "Shift" -> NamedKey.Shift
    "Control" -> NamedKey.Control
    "Alt" -> NamedKey.Alt
    "Meta" -> NamedKey.Meta
    "CapsLock" -> NamedKey.CapsLock
    "NumLock" -> NamedKey.NumLock
    "ContextMenu" -> NamedKey.ContextMenu
    "F1" -> NamedKey.F1
    "F2" -> NamedKey.F2
    "F3" -> NamedKey.F3
    "F4" -> NamedKey.F4
    "F5" -> NamedKey.F5
    "F6" -> NamedKey.F6
    "F7" -> NamedKey.F7
    "F8" -> NamedKey.F8
    "F9" -> NamedKey.F9
    "F10" -> NamedKey.F10
    "F11" -> NamedKey.F11
    "F12" -> NamedKey.F12
    "MediaPlayPause" -> NamedKey.MediaPlayPause
    "MediaStop" -> NamedKey.MediaStop
    "MediaTrackNext" -> NamedKey.MediaNext
    "MediaTrackPrevious" -> NamedKey.MediaPrevious
    "AudioVolumeUp", "VolumeUp" -> NamedKey.VolumeUp
    "AudioVolumeDown", "VolumeDown" -> NamedKey.VolumeDown
    "AudioVolumeMute", "VolumeMute" -> NamedKey.VolumeMute
    else -> null
}

/**
 * Sanitises an opaque native token to the rule the common model validates it with: a non-empty ASCII
 * identifier of at most 256 code units, each in `0x21..0x7e`.
 *
 * A value that cannot be such a token is `null` rather than a truncated or transliterated
 * approximation of itself.
 */
internal fun webNativeToken(value: String?): String? =
    value?.takeIf { token -> token.isNotEmpty() && token.length <= 256 && token.all { it.code in WEB_TOKEN_CHARACTERS } }

/**
 * Maps `KeyboardEvent.location` to where the key physically sits.
 *
 * The four values of the DOM are standard, left, right and numpad. Anything else — a value outside
 * the registry, or a negative one — is `Standard`, the location of every key a browser does not
 * further qualify: the alternative would be to invent a side of the keyboard.
 */
internal fun webKeyLocation(location: Int): KeyLocation = when (location) {
    1 -> KeyLocation.Left
    2 -> KeyLocation.Right
    3 -> KeyLocation.Numpad
    else -> KeyLocation.Standard
}

/**
 * Maps `PointerEvent.pointerType` to the pointer kind of the model, or to `null` when this phase
 * does not deliver that kind at all.
 *
 * `mouse` and `pen` are the two kinds this phase delivers, and the kind is the one the browser
 * reported for the event at hand: a pen is never delivered as a mouse, which is what makes the
 * `PenState` of [webPenState] a real observation instead of an approximation.
 *
 * `touch` is refused — `null`, no stimulus — because D12 defers touch to the phase that installs its
 * observers: `InputCapabilities.touch` stays `Unsupported`, so nothing may claim a touch pointer
 * exists. The refusal is the same kind of declared boundary as the `DOM_DELTA_PAGE` one of
 * [webScrollDelta]: the variant is named and dropped, never converted.
 *
 * Every other value is [PointerKind.Unknown], including the empty string a browser reports when it
 * cannot name the device. The model has that member for exactly this case, and dropping a motion
 * because its device went unnamed would lose an observation the model can carry.
 */
internal fun webPointerKind(pointerType: String): PointerKind? = when (pointerType) {
    "mouse" -> PointerKind.Mouse
    "pen" -> PointerKind.Pen
    "touch" -> null
    else -> PointerKind.Unknown
}

/**
 * The pen state of one observation, for the pointer kinds the model lets carry one.
 *
 * The kind is what decides: the model validates `pen == null || kind == PointerKind.Pen ||
 * kind == PointerKind.Eraser` on every pointer payload it builds, because a pen state belongs to a
 * pen. Reading the four pen members without asking the kind first would hand the model either a
 * state it rejects or, worse, a mouse delivered as a pen lying flat and untwisted — a mouse reports
 * zeros for every pen member, and only the kind tells those zeros from a real pen's.
 *
 * This is a rule of the mapping and lives with it rather than in a port, so a second port cannot get
 * it wrong. A kind that carries no pen state, and a pen whose four members are all outside the
 * domains the model documents, both answer `null`: the caller then delivers a pointer the model
 * accepts, with no pen state at all, rather than a fabricated one.
 */
internal fun webPenStateFor(
    kind: PointerKind,
    tiltXDegrees: Double?,
    tiltYDegrees: Double?,
    twistDegrees: Double?,
    tangentialPressure: Double?,
): PenState? =
    if (kind == PointerKind.Pen || kind == PointerKind.Eraser) {
        webPenState(tiltXDegrees, tiltYDegrees, twistDegrees, tangentialPressure)
    } else {
        null
    }

/**
 * Maps the modifier primitives of a keyboard event to the model's modifier set.
 *
 * The flags are the DOM's own: `shiftKey`, `ctrlKey`, `altKey`, `metaKey`, and the toggle states
 * `getModifierState("CapsLock")` and `getModifierState("NumLock")`. A flag that is false contributes
 * no modifier: the set is exactly what the browser reported as active, so nothing is invented and
 * nothing is defaulted.
 */
internal fun webKeyboardModifiers(
    shift: Boolean,
    control: Boolean,
    alt: Boolean,
    meta: Boolean,
    capsLock: Boolean,
    numLock: Boolean,
): KeyboardModifiers = KeyboardModifiers(
    buildSet {
        if (shift) add(ModifierKey.Shift)
        if (control) add(ModifierKey.Control)
        if (alt) add(ModifierKey.Alt)
        if (meta) add(ModifierKey.Meta)
        if (capsLock) add(ModifierKey.CapsLock)
        if (numLock) add(ModifierKey.NumLock)
    },
)

/**
 * Maps a `WheelEvent` to the scroll the model can carry, or `null` when it cannot carry it.
 *
 * `deltaMode` 0 (`DOM_DELTA_PIXEL`) and 1 (`DOM_DELTA_LINE`) are the two normalisable variants of
 * this phase, delivered as [ScrollDelta.Logical] and [ScrollDelta.Lines] in the DOM's own units.
 * `deltaMode` 2 (`DOM_DELTA_PAGE`) is the declared non-normalisable variant: turning pages into
 * logical units would need a page size Kadre does not own, so it is dropped exactly like an unknown
 * mode, never converted approximately (D9 of the Web input plan).
 *
 * The sign is not touched: `WheelEvent` reports positive deltas down and right, which is the same
 * orientation the model documents, so a scroll keeps the numbers the browser reported. A component
 * that is not finite is dropped too, since `ScrollDelta` requires finite ones.
 */
internal fun webScrollDelta(deltaMode: Int, deltaX: Double, deltaY: Double): ScrollDelta? {
    if (!deltaX.isFinite() || !deltaY.isFinite()) return null
    return when (deltaMode) {
        0 -> ScrollDelta.Logical(deltaX, deltaY)
        1 -> ScrollDelta.Lines(deltaX, deltaY)
        else -> null
    }
}

/**
 * Maps the `MouseEvent.button` index to the model's button.
 *
 * The five standard indices are the primary, auxiliary (the wheel), secondary, back and forward
 * buttons. Any other index — including `-1`, which a move event reports for "no button related to
 * this event" — stays `Other` with the index it came with: it is never reported as the primary
 * button, because a press Kadre cannot name must not read as a left click.
 */
internal fun webPointerButton(button: Int): PointerButton = when (button) {
    0 -> PointerButton.Primary
    1 -> PointerButton.Auxiliary
    2 -> PointerButton.Secondary
    3 -> PointerButton.Back
    4 -> PointerButton.Forward
    else -> PointerButton.Other(button)
}

/**
 * Maps `PointerEvent.pressure` to the pointer pressure of the model, or to `null` when the model
 * cannot carry it.
 *
 * The DOM reports pressure in `0.0..1.0`; a value outside that domain is not a pressure, so it is
 * dropped rather than clamped into one. The same rule applies to a non-finite value or to an absent
 * one, which is what `PointerState` requires (its `pressure` is optional, but never out of range).
 */
internal fun webPointerPressure(pressure: Double?): Double? =
    pressure?.takeIf { it.isFinite() && it in 0.0..1.0 }

/**
 * Maps the pen primitives of a pointer event to a pen state, or to `null` when there is nothing to
 * carry.
 *
 * `tiltX`/`tiltY` are degrees, `twist` is degrees, and `tangentialPressure` is the barrel pressure a
 * pen reports. The model keeps angles in degrees but the twist in radians, so the twist is the one
 * converted value here (exactly, by the definition of the degree), while the tilt stays in degrees.
 *
 * Every field is optional and independently checked: a primitive outside the domain the model
 * documents for it is dropped — a tilt beyond ±90 degrees, a twist outside a turn, a tangential
 * pressure outside ±1, a non-finite value — and the fields that are in domain are still carried, so a
 * partly reported pen loses only what it could not report. When no field survives, the result is
 * `null`: a pointer that observed nothing pen-like carries no pen state rather than an empty record.
 */
internal fun webPenState(
    tiltXDegrees: Double?,
    tiltYDegrees: Double?,
    twistDegrees: Double?,
    tangentialPressure: Double?,
): PenState? {
    val tiltX = tiltXDegrees.takeIfInDomain(-90.0, 90.0)
    val tiltY = tiltYDegrees.takeIfInDomain(-90.0, 90.0)
    val twist = webTwistRadians(twistDegrees)
    val tangential = tangentialPressure.takeIfInDomain(-1.0, 1.0)
    if (tiltX == null && tiltY == null && twist == null && tangential == null) return null
    return PenState(tiltX, tiltY, twist, tangential)
}

/**
 * The DOM's pen twist, in degrees, as the radians the model keeps.
 *
 * A value outside `[0, 360)` is not a twist the browser can report, so it is dropped instead of being
 * wrapped: a full turn is not silently folded onto zero. Inside that range the conversion cannot
 * leave the model's `[0, 2π)` domain, since the largest double below 360 is 359.99999999999994 and
 * its radians are one unit in the last place below 2π; the guard keeps that structurally true.
 */
private fun webTwistRadians(twistDegrees: Double?): Double? {
    val bounded = twistDegrees?.takeIf { it.isFinite() && it >= 0.0 && it < 360.0 } ?: return null
    val radians = bounded * DEGREES_TO_RADIANS
    return if (radians.isFinite() && radians < TWO_PI) radians else null
}

/** The value itself when it is finite and inside `[minimum, maximum]`, and `null` otherwise. */
private fun Double?.takeIfInDomain(minimum: Double, maximum: Double): Double? =
    this?.takeIf { it.isFinite() && it in minimum..maximum }

/** The usage page of the Keyboard/Keypad block of the USB HID Usage Tables. */
private const val WEB_KEYBOARD_USAGE_PAGE: Int = 0x07

/** The code units the common model accepts in an opaque native token. */
private val WEB_TOKEN_CHARACTERS: IntRange = 0x21..0x7e

private const val DEGREES_TO_RADIANS: Double = PI / 180.0
private const val TWO_PI: Double = 2.0 * PI
