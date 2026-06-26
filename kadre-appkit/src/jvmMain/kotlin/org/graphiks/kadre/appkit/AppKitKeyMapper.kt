/**
 * Mapping of macOS key codes (NSEvent.keyCode) to Kadre keyboard types.
 *
 * NSEvent.keyCode is a **physical / positional** hardware code: it identifies a
 * key by its location on the keyboard and is **independent of the active layout**
 * (e.g. key 0 is always the bottom-left letter key — "A" on QWERTY, "Q" on
 * AZERTY). The table below therefore maps physical positions, not the produced
 * characters, which is correct for [PhysicalKey] across all keyboard layouts.
 *
 * Layout-dependent *text* (the logical character a key produces) is sourced from
 * `[NSEvent characters]` in [KadreApplication], so non-QWERTY layouts emit the
 * right `KeyEvent.text` without a separate translation table here.
 *
 * TODO(appkit-logical-key): for layout-aware *named* logical keys (rather than
 *  text), use `UCKeyTranslate` with the current `TISCopyCurrentKeyboardLayoutInputSource`
 *  layout data. Not required for text input, which already works via NSEvent.characters.
 */
package org.graphiks.kadre.appkit

import org.graphiks.kadre.core.KeyCode
import org.graphiks.kadre.core.KeyboardModifiers
import org.graphiks.kadre.core.ModifierKeyState
import org.graphiks.kadre.core.ModifierKeys
import org.graphiks.kadre.core.NativeKeyCode
import org.graphiks.kadre.core.PhysicalKey

/**
 * Maps macOS virtual key codes (NSEvent.keyCode) to physical key codes.
 *
 * The codes are **positional** (layout-independent); QWERTY US labels are used
 * only as human-readable references for the physical positions.
 */
internal object AppKitKeyMapper {

    fun physicalKey(code: Short): PhysicalKey = keyCode(code)?.let(PhysicalKey::Code)
        ?: PhysicalKey.Native(NativeKeyCode.AppKit(code.toLong()))

    fun keyCode(code: Short): KeyCode? = when (code.toInt()) {
        // Letters (physical positions, labelled with QWERTY US for reference)
        0  -> KeyCode.KeyA
        11 -> KeyCode.KeyB
        8  -> KeyCode.KeyC
        2  -> KeyCode.KeyD
        14 -> KeyCode.KeyE
        3  -> KeyCode.KeyF
        5  -> KeyCode.KeyG
        4  -> KeyCode.KeyH
        34 -> KeyCode.KeyI
        38 -> KeyCode.KeyJ
        40 -> KeyCode.KeyK
        37 -> KeyCode.KeyL
        46 -> KeyCode.KeyM
        45 -> KeyCode.KeyN
        31 -> KeyCode.KeyO
        35 -> KeyCode.KeyP
        12 -> KeyCode.KeyQ
        15 -> KeyCode.KeyR
        1  -> KeyCode.KeyS
        17 -> KeyCode.KeyT
        32 -> KeyCode.KeyU
        9  -> KeyCode.KeyV
        13 -> KeyCode.KeyW
        7  -> KeyCode.KeyX
        16 -> KeyCode.KeyY
        6  -> KeyCode.KeyZ
        // Digits
        29 -> KeyCode.Digit0
        18 -> KeyCode.Digit1
        19 -> KeyCode.Digit2
        20 -> KeyCode.Digit3
        21 -> KeyCode.Digit4
        23 -> KeyCode.Digit5
        22 -> KeyCode.Digit6
        26 -> KeyCode.Digit7
        28 -> KeyCode.Digit8
        25 -> KeyCode.Digit9
        // Navigation
        123 -> KeyCode.ArrowLeft
        124 -> KeyCode.ArrowRight
        125 -> KeyCode.ArrowDown
        126 -> KeyCode.ArrowUp
        // Special keys
        36  -> KeyCode.Enter
        49  -> KeyCode.Space
        48  -> KeyCode.Tab
        51  -> KeyCode.Backspace
        53  -> KeyCode.Escape
        // Function keys
        122 -> KeyCode.F1
        120 -> KeyCode.F2
        99  -> KeyCode.F3
        118 -> KeyCode.F4
        96  -> KeyCode.F5
        97  -> KeyCode.F6
        98  -> KeyCode.F7
        100 -> KeyCode.F8
        101 -> KeyCode.F9
        109 -> KeyCode.F10
        103 -> KeyCode.F11
        111 -> KeyCode.F12
        else -> null
    }

    /**
     * Maps NSEventModifierFlags bitmask to Kadre [KeyboardModifiers].
     *
     * NSEventModifierFlagShift   = 0x20000
     * NSEventModifierFlagControl = 0x40000
     * NSEventModifierFlagOption  = 0x80000  (Alt)
     * NSEventModifierFlagCommand = 0x100000 (Meta)
     */
    fun modifierFlags(flags: Long): KeyboardModifiers {
        var mods = KeyboardModifiers.NONE
        if (flags and 0x20000L  != 0L) mods += KeyboardModifiers.Shift
        if (flags and 0x40000L  != 0L) mods += KeyboardModifiers.Ctrl
        if (flags and 0x80000L  != 0L) mods += KeyboardModifiers.Alt
        if (flags and 0x100000L != 0L) mods += KeyboardModifiers.Meta
        return mods
    }

    /**
     * Maps the device-dependent bits of NSEventModifierFlags to a [ModifierKeys]
     * with per-side Pressed/Released state.
     *
     * AppKit encodes left/right modifier sides in the low bits of
     * `modifierFlags` (device-dependent flags):
     * - Shift:   left 0x0002, right 0x0004
     * - Control: left 0x0001, right 0x2000
     * - Option:  left 0x0020, right 0x0040  (Alt)
     * - Command: left 0x0008, right 0x0010  (Meta)
     *
     * A side that is not set is reported as [ModifierKeyState.Released] (rather
     * than Unknown), since `modifierFlags` reflects the complete current state.
     */
    fun modifierKeys(flags: Long): ModifierKeys {
        fun state(mask: Long): ModifierKeyState =
            if (flags and mask != 0L) ModifierKeyState.Pressed else ModifierKeyState.Released
        return ModifierKeys(
            leftShift = state(NS_DEVICE_LSHIFT), rightShift = state(NS_DEVICE_RSHIFT),
            leftCtrl = state(NS_DEVICE_LCTRL), rightCtrl = state(NS_DEVICE_RCTRL),
            leftAlt = state(NS_DEVICE_LALT), rightAlt = state(NS_DEVICE_RALT),
            leftMeta = state(NS_DEVICE_LCMD), rightMeta = state(NS_DEVICE_RCMD),
        )
    }

    // Device-dependent modifier masks (low bits of NSEvent.modifierFlags).
    private const val NS_DEVICE_LSHIFT = 0x0002L
    private const val NS_DEVICE_RSHIFT = 0x0004L
    private const val NS_DEVICE_LCTRL = 0x0001L
    private const val NS_DEVICE_RCTRL = 0x2000L
    private const val NS_DEVICE_LALT = 0x0020L
    private const val NS_DEVICE_RALT = 0x0040L
    private const val NS_DEVICE_LCMD = 0x0008L
    private const val NS_DEVICE_RCMD = 0x0010L
}
