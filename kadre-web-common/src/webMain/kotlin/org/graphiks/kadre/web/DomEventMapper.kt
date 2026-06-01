/**
 * DOM → Kadre web types mapping (pure functions, no DOM imports).
 *
 * This file is in webMain. It must contain NO DOM import
 * (no kotlinx.browser, org.w3c.dom.*, nor Wasm JS interop).
 * The parameters receive pure Kotlin types (String, Short, Boolean)
 * extracted by the implementations in jsMain / wasmJsMain.
 *
 * The functions are `internal` to avoid polluting the public API.
 *
 * @since 1.0.0
 */
package org.graphiks.kadre.web

import org.graphiks.kadre.core.KeyCode
import org.graphiks.kadre.core.KeyEvent
import org.graphiks.kadre.core.KeyPlatform
import org.graphiks.kadre.core.KeyState
import org.graphiks.kadre.core.KeyboardModifiers
import org.graphiks.kadre.core.LogicalKey
import org.graphiks.kadre.core.NativeKeyInfo
import org.graphiks.kadre.core.PhysicalKey
import org.graphiks.kadre.core.defaultLogicalKey
import org.graphiks.kadre.core.defaultText

/**
 * Converts a DOM code (`KeyboardEvent.code`) into a Kadre [WebKey].
 *
 * DOM codes are strings of the form `"KeyA"`, `"Digit1"`, `"ArrowUp"`, etc.
 * See: https://developer.mozilla.org/en-US/docs/Web/API/UI_Events/Keyboard_event_code_values
 *
 * @param code Value of `KeyboardEvent.code` (e.g. `"KeyA"`, `"Space"`, `"F1"`).
 * @return The corresponding [WebKey], or [WebKey.Unknown] if the code is not recognized.
 */
internal fun domCodeToKey(code: String): WebKey = when (code) {
    // Letters
    "KeyA" -> WebKey.A
    "KeyB" -> WebKey.B
    "KeyC" -> WebKey.C
    "KeyD" -> WebKey.D
    "KeyE" -> WebKey.E
    "KeyF" -> WebKey.F
    "KeyG" -> WebKey.G
    "KeyH" -> WebKey.H
    "KeyI" -> WebKey.I
    "KeyJ" -> WebKey.J
    "KeyK" -> WebKey.K
    "KeyL" -> WebKey.L
    "KeyM" -> WebKey.M
    "KeyN" -> WebKey.N
    "KeyO" -> WebKey.O
    "KeyP" -> WebKey.P
    "KeyQ" -> WebKey.Q
    "KeyR" -> WebKey.R
    "KeyS" -> WebKey.S
    "KeyT" -> WebKey.T
    "KeyU" -> WebKey.U
    "KeyV" -> WebKey.V
    "KeyW" -> WebKey.W
    "KeyX" -> WebKey.X
    "KeyY" -> WebKey.Y
    "KeyZ" -> WebKey.Z

    // Digits
    "Digit0" -> WebKey.Digit0
    "Digit1" -> WebKey.Digit1
    "Digit2" -> WebKey.Digit2
    "Digit3" -> WebKey.Digit3
    "Digit4" -> WebKey.Digit4
    "Digit5" -> WebKey.Digit5
    "Digit6" -> WebKey.Digit6
    "Digit7" -> WebKey.Digit7
    "Digit8" -> WebKey.Digit8
    "Digit9" -> WebKey.Digit9

    // Function keys
    "F1"  -> WebKey.F1
    "F2"  -> WebKey.F2
    "F3"  -> WebKey.F3
    "F4"  -> WebKey.F4
    "F5"  -> WebKey.F5
    "F6"  -> WebKey.F6
    "F7"  -> WebKey.F7
    "F8"  -> WebKey.F8
    "F9"  -> WebKey.F9
    "F10" -> WebKey.F10
    "F11" -> WebKey.F11
    "F12" -> WebKey.F12

    // Special keys
    "Space"     -> WebKey.Space
    "Enter"     -> WebKey.Enter
    "Escape"    -> WebKey.Escape
    "Backspace" -> WebKey.Backspace
    "Tab"       -> WebKey.Tab

    // Navigation
    "ArrowUp"    -> WebKey.ArrowUp
    "ArrowDown"  -> WebKey.ArrowDown
    "ArrowLeft"  -> WebKey.ArrowLeft
    "ArrowRight" -> WebKey.ArrowRight

    // Modifiers
    "ShiftLeft"    -> WebKey.ShiftLeft
    "ShiftRight"   -> WebKey.ShiftRight
    "ControlLeft"  -> WebKey.ControlLeft
    "ControlRight" -> WebKey.ControlRight
    "AltLeft"      -> WebKey.AltLeft
    "AltRight"     -> WebKey.AltRight
    "MetaLeft"     -> WebKey.MetaLeft
    "MetaRight"    -> WebKey.MetaRight

    else -> WebKey.Unknown
}

internal fun domCodeToKeyCode(code: String): KeyCode? = when (code) {
    "KeyA" -> KeyCode.KeyA
    "KeyB" -> KeyCode.KeyB
    "KeyC" -> KeyCode.KeyC
    "KeyD" -> KeyCode.KeyD
    "KeyE" -> KeyCode.KeyE
    "KeyF" -> KeyCode.KeyF
    "KeyG" -> KeyCode.KeyG
    "KeyH" -> KeyCode.KeyH
    "KeyI" -> KeyCode.KeyI
    "KeyJ" -> KeyCode.KeyJ
    "KeyK" -> KeyCode.KeyK
    "KeyL" -> KeyCode.KeyL
    "KeyM" -> KeyCode.KeyM
    "KeyN" -> KeyCode.KeyN
    "KeyO" -> KeyCode.KeyO
    "KeyP" -> KeyCode.KeyP
    "KeyQ" -> KeyCode.KeyQ
    "KeyR" -> KeyCode.KeyR
    "KeyS" -> KeyCode.KeyS
    "KeyT" -> KeyCode.KeyT
    "KeyU" -> KeyCode.KeyU
    "KeyV" -> KeyCode.KeyV
    "KeyW" -> KeyCode.KeyW
    "KeyX" -> KeyCode.KeyX
    "KeyY" -> KeyCode.KeyY
    "KeyZ" -> KeyCode.KeyZ
    "Digit0" -> KeyCode.Digit0
    "Digit1" -> KeyCode.Digit1
    "Digit2" -> KeyCode.Digit2
    "Digit3" -> KeyCode.Digit3
    "Digit4" -> KeyCode.Digit4
    "Digit5" -> KeyCode.Digit5
    "Digit6" -> KeyCode.Digit6
    "Digit7" -> KeyCode.Digit7
    "Digit8" -> KeyCode.Digit8
    "Digit9" -> KeyCode.Digit9
    "F1" -> KeyCode.F1
    "F2" -> KeyCode.F2
    "F3" -> KeyCode.F3
    "F4" -> KeyCode.F4
    "F5" -> KeyCode.F5
    "F6" -> KeyCode.F6
    "F7" -> KeyCode.F7
    "F8" -> KeyCode.F8
    "F9" -> KeyCode.F9
    "F10" -> KeyCode.F10
    "F11" -> KeyCode.F11
    "F12" -> KeyCode.F12
    "Space" -> KeyCode.Space
    "Enter" -> KeyCode.Enter
    "Escape" -> KeyCode.Escape
    "Backspace" -> KeyCode.Backspace
    "Tab" -> KeyCode.Tab
    "ArrowUp" -> KeyCode.ArrowUp
    "ArrowDown" -> KeyCode.ArrowDown
    "ArrowLeft" -> KeyCode.ArrowLeft
    "ArrowRight" -> KeyCode.ArrowRight
    "ShiftLeft" -> KeyCode.ShiftLeft
    "ShiftRight" -> KeyCode.ShiftRight
    "ControlLeft" -> KeyCode.ControlLeft
    "ControlRight" -> KeyCode.ControlRight
    "AltLeft" -> KeyCode.AltLeft
    "AltRight" -> KeyCode.AltRight
    "MetaLeft" -> KeyCode.MetaLeft
    "MetaRight" -> KeyCode.MetaRight
    else -> null
}

/**
 * Builds a [WebModifiers] from the boolean fields of a DOM `KeyboardEvent` or `MouseEvent`.
 *
 * @param shiftKey  Value of `event.shiftKey`.
 * @param ctrlKey   Value of `event.ctrlKey`.
 * @param altKey    Value of `event.altKey`.
 * @param metaKey   Value of `event.metaKey`.
 * @return [WebModifiers] with the corresponding bits set.
 */
internal fun domModifiers(
    shiftKey: Boolean,
    ctrlKey: Boolean,
    altKey: Boolean,
    metaKey: Boolean,
): WebModifiers {
    var mods = WebModifiers.NONE
    if (shiftKey) mods = mods + WebModifiers.SHIFT
    if (ctrlKey)  mods = mods + WebModifiers.CTRL
    if (altKey)   mods = mods + WebModifiers.ALT
    if (metaKey)  mods = mods + WebModifiers.META
    return mods
}

internal fun domKeyboardModifiers(
    shiftKey: Boolean,
    ctrlKey: Boolean,
    altKey: Boolean,
    metaKey: Boolean,
): KeyboardModifiers {
    var mods = KeyboardModifiers.NONE
    if (shiftKey) mods += KeyboardModifiers.Shift
    if (ctrlKey) mods += KeyboardModifiers.Ctrl
    if (altKey) mods += KeyboardModifiers.Alt
    if (metaKey) mods += KeyboardModifiers.Meta
    return mods
}

internal fun domKeyEvent(
    code: String,
    key: String,
    eventType: String,
    shiftKey: Boolean,
    ctrlKey: Boolean,
    altKey: Boolean,
    metaKey: Boolean,
    repeat: Boolean,
): KeyEvent {
    val mappedCode = domCodeToKeyCode(code)
    val native = NativeKeyInfo(
        platform = KeyPlatform.Web,
        keyCode = code,
        keyValue = key,
    )
    val logicalKey = when {
        key.length == 1 -> LogicalKey.Character(key)
        mappedCode != null -> mappedCode.defaultLogicalKey()
        else -> LogicalKey.Unidentified(native)
    }
    return KeyEvent(
        physicalKey = mappedCode?.let(PhysicalKey::Code) ?: PhysicalKey.Native(KeyPlatform.Web, code.hashCode().toLong()),
        logicalKey = logicalKey,
        state = domCoreKeyStateFromEventType(eventType),
        modifiers = domKeyboardModifiers(shiftKey, ctrlKey, altKey, metaKey),
        repeat = repeat,
        text = key.takeIf { it.length == 1 } ?: mappedCode?.defaultText(),
        keyWithoutModifiers = mappedCode?.defaultLogicalKey(),
        native = native,
    )
}

/**
 * Converts a DOM button index (`MouseEvent.button`) into a Kadre [WebMouseButton].
 *
 * Standard DOM mapping:
 * - `0` → left button
 * - `1` → middle button (wheel)
 * - `2` → right button
 * - `≥3` → additional buttons ([WebMouseButton.Other])
 *
 * @param button Value of `MouseEvent.button` (Short on the DOM side).
 * @return The corresponding [WebMouseButton].
 */
internal fun domButtonToMouseButton(button: Short): WebMouseButton = when (button.toInt()) {
    0    -> WebMouseButton.Left
    1    -> WebMouseButton.Middle
    2    -> WebMouseButton.Right
    else -> WebMouseButton.Other(button.toInt())
}

/**
 * Derives the [WebKeyState] from the DOM event type (`"keydown"` or `"keyup"`).
 *
 * @param eventType Value of `event.type` (`"keydown"` or `"keyup"`).
 * @return [WebKeyState.Pressed] for `"keydown"`, [WebKeyState.Released] otherwise.
 */
internal fun domKeyStateFromEventType(eventType: String): WebKeyState = when (eventType) {
    "keydown"     -> WebKeyState.Pressed
    "pointerdown" -> WebKeyState.Pressed
    else          -> WebKeyState.Released
}

internal fun domCoreKeyStateFromEventType(eventType: String): KeyState = when (eventType) {
    "keydown" -> KeyState.Pressed
    else -> KeyState.Released
}

/**
 * Maps a DOM touch event type into a [WebTouchPhase].
 *
 * @param eventType Value of `event.type` (`"touchstart"`, `"touchmove"`,
 *   `"touchend"` or `"touchcancel"`).
 * @return The corresponding [WebTouchPhase]; unrecognized types fall back to
 *   [WebTouchPhase.Cancelled] (the safest "release" semantics).
 */
internal fun domTouchTypeToPhase(eventType: String): WebTouchPhase = when (eventType) {
    "touchstart"  -> WebTouchPhase.Started
    "touchmove"   -> WebTouchPhase.Moved
    "touchend"    -> WebTouchPhase.Ended
    "touchcancel" -> WebTouchPhase.Cancelled
    else          -> WebTouchPhase.Cancelled
}

/**
 * Normalizes a DOM wheel delta into logical pixels.
 *
 * The DOM exposes three scroll modes:
 * - `0` (DOM_DELTA_PIXEL): the delta is already in pixels — no transformation.
 * - `1` (DOM_DELTA_LINE) : the delta is in lines — multiply by 16 px.
 * - `2` (DOM_DELTA_PAGE) : the delta is in pages — multiply by 600 px.
 *
 * @param delta     Raw value of `WheelEvent.deltaX` or `deltaY`.
 * @param deltaMode Value of `WheelEvent.deltaMode` (0, 1 or 2).
 * @return Delta normalized into logical pixels.
 */
internal fun normalizeWheelDelta(delta: Double, deltaMode: Int): Double {
    val scale = when (deltaMode) {
        1 -> 16.0
        2 -> 600.0
        else -> 1.0
    }
    return delta * scale
}
