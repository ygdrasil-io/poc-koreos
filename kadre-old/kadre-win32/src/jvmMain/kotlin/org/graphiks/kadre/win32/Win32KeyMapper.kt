/**
 * Mapping of Win32 virtual key codes (VK_*) to kadre physical key codes.
 *
 * Reference: https://learn.microsoft.com/en-us/windows/win32/inputdev/virtual-key-codes
 *
 * The VK codes are passed in the wParam of the WM_KEYDOWN / WM_KEYUP /
 * WM_SYSKEYDOWN / WM_SYSKEYUP messages. This mapper provides the complete mapping
 * for the A–Z, 0–9, F1–F12, arrow, modifier and special keys.
 */
package org.graphiks.kadre.win32

import org.graphiks.kadre.core.KeyCode
import org.graphiks.kadre.core.NativeKeyCode
import org.graphiks.kadre.core.PhysicalKey
import org.graphiks.kffi.win32.generated.MapVirtualKeyW

/**
 * `MapVirtualKeyW` translation type: scan code → virtual key, distinguishing
 * left/right variants (VK_LSHIFT/VK_RSHIFT, etc.). Not defined in the generated
 * Win32 constants, so declared here.
 */
private const val MAPVK_VSC_TO_VK_EX: Int = 3

/** Hardware scan code of the right Shift key (used as a fallback heuristic). */
private const val SCANCODE_RIGHT_SHIFT: Int = 0x36

/**
 * Converts a Win32 virtual key code (VK_*) into a kadre [KeyCode].
 *
 * @receiver VK code passed in the wParam of a Win32 keyboard message.
 * @return Corresponding kadre key code.
 */
internal object Win32KeyMapper {

    /**
     * VK code → [KeyCode] mapping table.
     *
     * Built only once (lazy via companion) to avoid repeated allocations.
     */
    private val table: Map<Int, KeyCode> = buildMap {
        // Letters A–Z (VK codes = uppercase ASCII codes 0x41–0x5A)
        put(VK_A, KeyCode.KeyA)
        put(VK_B, KeyCode.KeyB)
        put(VK_C, KeyCode.KeyC)
        put(VK_D, KeyCode.KeyD)
        put(VK_E, KeyCode.KeyE)
        put(VK_F, KeyCode.KeyF)
        put(VK_G, KeyCode.KeyG)
        put(VK_H, KeyCode.KeyH)
        put(VK_I, KeyCode.KeyI)
        put(VK_J, KeyCode.KeyJ)
        put(VK_K, KeyCode.KeyK)
        put(VK_L, KeyCode.KeyL)
        put(VK_M, KeyCode.KeyM)
        put(VK_N, KeyCode.KeyN)
        put(VK_O, KeyCode.KeyO)
        put(VK_P, KeyCode.KeyP)
        put(VK_Q, KeyCode.KeyQ)
        put(VK_R, KeyCode.KeyR)
        put(VK_S, KeyCode.KeyS)
        put(VK_T, KeyCode.KeyT)
        put(VK_U, KeyCode.KeyU)
        put(VK_V, KeyCode.KeyV)
        put(VK_W, KeyCode.KeyW)
        put(VK_X, KeyCode.KeyX)
        put(VK_Y, KeyCode.KeyY)
        put(VK_Z, KeyCode.KeyZ)

        // Digits 0–9 (top row, VK codes = ASCII codes 0x30–0x39)
        put(VK_0, KeyCode.Digit0)
        put(VK_1, KeyCode.Digit1)
        put(VK_2, KeyCode.Digit2)
        put(VK_3, KeyCode.Digit3)
        put(VK_4, KeyCode.Digit4)
        put(VK_5, KeyCode.Digit5)
        put(VK_6, KeyCode.Digit6)
        put(VK_7, KeyCode.Digit7)
        put(VK_8, KeyCode.Digit8)
        put(VK_9, KeyCode.Digit9)

        // Function keys F1–F12
        put(VK_F1, KeyCode.F1)
        put(VK_F2, KeyCode.F2)
        put(VK_F3, KeyCode.F3)
        put(VK_F4, KeyCode.F4)
        put(VK_F5, KeyCode.F5)
        put(VK_F6, KeyCode.F6)
        put(VK_F7, KeyCode.F7)
        put(VK_F8, KeyCode.F8)
        put(VK_F9, KeyCode.F9)
        put(VK_F10, KeyCode.F10)
        put(VK_F11, KeyCode.F11)
        put(VK_F12, KeyCode.F12)

        // Navigation keys
        put(VK_LEFT,  KeyCode.ArrowLeft)
        put(VK_RIGHT, KeyCode.ArrowRight)
        put(VK_UP,    KeyCode.ArrowUp)
        put(VK_DOWN,  KeyCode.ArrowDown)

        // Special keys
        put(VK_SPACE,  KeyCode.Space)
        put(VK_RETURN, KeyCode.Enter)
        put(VK_ESCAPE, KeyCode.Escape)
        put(VK_BACK,   KeyCode.Backspace)
        put(VK_TAB,    KeyCode.Tab)

        // Modifiers (left/right versions + generic)
        put(VK_LSHIFT,   KeyCode.ShiftLeft)
        put(VK_RSHIFT,   KeyCode.ShiftRight)
        put(VK_SHIFT,    KeyCode.ShiftLeft)   // generic -> left by default
        put(VK_LCONTROL, KeyCode.ControlLeft)
        put(VK_RCONTROL, KeyCode.ControlRight)
        put(VK_CONTROL,  KeyCode.ControlLeft) // generic -> left by default
        put(VK_LMENU,    KeyCode.AltLeft)
        put(VK_RMENU,    KeyCode.AltRight)
        put(VK_MENU,     KeyCode.AltLeft)     // generic -> left by default
        put(VK_LWIN,     KeyCode.MetaLeft)
        put(VK_RWIN,     KeyCode.MetaRight)
    }

    /**
     * Returns the [KeyCode] corresponding to the given VK code.
     *
     * @param vkCode Win32 virtual key code (wParam of a WM_KEY* message).
     * @return kadre key code, or null if the code is not in the table.
     */
    fun keyCode(vkCode: Int): KeyCode? = table[vkCode]

    /**
     * Resolves a [KeyCode] preferring scan code / extended-key information over
     * the raw virtual key, which improves accuracy on non-US layouts and lets
     * left/right modifier variants be distinguished.
     *
     * Windows reports the generic `VK_SHIFT` / `VK_CONTROL` / `VK_MENU` for both
     * sides in WM_KEY* messages. The side is recovered from:
     * - the **extended-key flag** (bit 24 of lParam) for Ctrl (→ Right) and
     *   Alt/AltGr (→ Right);
     * - `MapVirtualKeyW(scanCode, MAPVK_VSC_TO_VK_EX)` for Shift (falling back to
     *   the right-Shift scan code `0x36` when the native call is unavailable).
     *
     * Non-modifier keys fall through to the regular VK table.
     *
     * @param vkCode   Virtual key code (wParam of a WM_KEY* message).
     * @param scanCode Hardware scan code (bits 16-23 of lParam).
     * @param extended Extended-key flag (bit 24 of lParam).
     */
    fun keyCode(vkCode: Int, scanCode: Int, extended: Boolean): KeyCode? = when (vkCode) {
        VK_SHIFT -> resolveShift(scanCode)
        VK_CONTROL -> if (extended) KeyCode.ControlRight else KeyCode.ControlLeft
        VK_MENU -> if (extended) KeyCode.AltRight else KeyCode.AltLeft
        else -> table[vkCode]
    }

    fun physicalKey(vkCode: Int): PhysicalKey = keyCode(vkCode)?.let(PhysicalKey::Code)
        ?: PhysicalKey.Native(NativeKeyCode.Win32(scanCode = null, virtualKey = vkCode.toLong()))

    /**
     * Scan-code / extended-key aware variant of [physicalKey]. See
     * [keyCode] (3-arg) for the resolution rules.
     */
    fun physicalKey(vkCode: Int, scanCode: Int, extended: Boolean): PhysicalKey =
        keyCode(vkCode, scanCode, extended)?.let(PhysicalKey::Code)
            ?: PhysicalKey.Native(
                NativeKeyCode.Win32(
                    scanCode = scanCode.takeIf { it != 0 }?.toLong(),
                    virtualKey = vkCode.toLong(),
                )
            )

    /**
     * Distinguishes left vs. right Shift. Shift is not an extended key, so the
     * side is derived from the scan code via `MapVirtualKeyW(.., MAPVK_VSC_TO_VK_EX)`,
     * with a scan-code heuristic fallback for non-Windows / uninitialised cases.
     */
    private fun resolveShift(scanCode: Int): KeyCode {
        return when (mapScanCodeToVkEx(scanCode)) {
            VK_RSHIFT -> KeyCode.ShiftRight
            VK_LSHIFT -> KeyCode.ShiftLeft
            else -> if (scanCode == SCANCODE_RIGHT_SHIFT) KeyCode.ShiftRight else KeyCode.ShiftLeft
        }
    }

    /**
     * Calls `MapVirtualKeyW(scanCode, MAPVK_VSC_TO_VK_EX)`; returns 0 when the
     * native binding is unavailable (non-Windows) or not yet initialised.
     */
    private fun mapScanCodeToVkEx(scanCode: Int): Int = try {
        if (scanCode == 0) 0 else MapVirtualKeyW(scanCode, MAPVK_VSC_TO_VK_EX)
    } catch (_: Throwable) {
        0
    }
}
