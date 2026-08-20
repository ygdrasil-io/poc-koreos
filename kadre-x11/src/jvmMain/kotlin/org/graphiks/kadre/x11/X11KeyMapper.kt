/**
 * X11 mapper for keyboard events (XKeyEvent).
 *
 * Converts raw XKeyEvent events (KeyPress / KeyRelease) into
 * [org.graphiks.kadre.core.WindowEvent.KeyInput].
 */
package org.graphiks.kadre.x11

import org.graphiks.kadre.x11.binding.*
import org.graphiks.kadre.core.KeyCode
import org.graphiks.kadre.core.KeyEvent
import org.graphiks.kadre.core.KeyPlatform
import org.graphiks.kadre.core.KeyState
import org.graphiks.kadre.core.KeyboardModifierState
import org.graphiks.kadre.core.KeyboardModifiers
import org.graphiks.kadre.core.LogicalKey
import org.graphiks.kadre.core.ModifierKeyState
import org.graphiks.kadre.core.ModifierKeys
import org.graphiks.kadre.core.NativeKeyInfo
import org.graphiks.kadre.core.NativeKeyCode
import org.graphiks.kadre.core.NativeLogicalKey
import org.graphiks.kadre.core.PhysicalKey
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.defaultLogicalKey
import org.graphiks.kadre.core.defaultText
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

private const val OFFSET_STATE: Long = 64L
private const val OFFSET_KEYCODE: Long = 68L

/**
 * Offset of the `Display *display` field inside XAnyEvent (LP64 layout):
 * `type(4) + pad(4) + serial(8) + send_event(4) + pad(4) = 24`.
 * Used to skip [lookupX11Text] for synthetic events whose display is NULL
 * (e.g. unit-test segments), where XLookupString would dereference NULL.
 */
private const val OFFSET_DISPLAY: Long = 24L

private const val SHIFT_MASK: Int = 0x01
private const val CONTROL_MASK: Int = 0x04
private const val MOD1_MASK: Int = 0x08
private const val MOD4_MASK: Int = 0x40

internal val KEYSYM_TABLE: Map<Int, KeyCode> = buildMap {
    put(0x61, KeyCode.KeyA); put(0x62, KeyCode.KeyB); put(0x63, KeyCode.KeyC); put(0x64, KeyCode.KeyD)
    put(0x65, KeyCode.KeyE); put(0x66, KeyCode.KeyF); put(0x67, KeyCode.KeyG); put(0x68, KeyCode.KeyH)
    put(0x69, KeyCode.KeyI); put(0x6A, KeyCode.KeyJ); put(0x6B, KeyCode.KeyK); put(0x6C, KeyCode.KeyL)
    put(0x6D, KeyCode.KeyM); put(0x6E, KeyCode.KeyN); put(0x6F, KeyCode.KeyO); put(0x70, KeyCode.KeyP)
    put(0x71, KeyCode.KeyQ); put(0x72, KeyCode.KeyR); put(0x73, KeyCode.KeyS); put(0x74, KeyCode.KeyT)
    put(0x75, KeyCode.KeyU); put(0x76, KeyCode.KeyV); put(0x77, KeyCode.KeyW); put(0x78, KeyCode.KeyX)
    put(0x79, KeyCode.KeyY); put(0x7A, KeyCode.KeyZ)

    put(0x41, KeyCode.KeyA); put(0x42, KeyCode.KeyB); put(0x43, KeyCode.KeyC); put(0x44, KeyCode.KeyD)
    put(0x45, KeyCode.KeyE); put(0x46, KeyCode.KeyF); put(0x47, KeyCode.KeyG); put(0x48, KeyCode.KeyH)
    put(0x49, KeyCode.KeyI); put(0x4A, KeyCode.KeyJ); put(0x4B, KeyCode.KeyK); put(0x4C, KeyCode.KeyL)
    put(0x4D, KeyCode.KeyM); put(0x4E, KeyCode.KeyN); put(0x4F, KeyCode.KeyO); put(0x50, KeyCode.KeyP)
    put(0x51, KeyCode.KeyQ); put(0x52, KeyCode.KeyR); put(0x53, KeyCode.KeyS); put(0x54, KeyCode.KeyT)
    put(0x55, KeyCode.KeyU); put(0x56, KeyCode.KeyV); put(0x57, KeyCode.KeyW); put(0x58, KeyCode.KeyX)
    put(0x59, KeyCode.KeyY); put(0x5A, KeyCode.KeyZ)

    put(0x30, KeyCode.Digit0); put(0x31, KeyCode.Digit1); put(0x32, KeyCode.Digit2)
    put(0x33, KeyCode.Digit3); put(0x34, KeyCode.Digit4); put(0x35, KeyCode.Digit5)
    put(0x36, KeyCode.Digit6); put(0x37, KeyCode.Digit7); put(0x38, KeyCode.Digit8)
    put(0x39, KeyCode.Digit9)

    put(0xFF08, KeyCode.Backspace)
    put(0xFF09, KeyCode.Tab)
    put(0xFF0D, KeyCode.Enter)
    put(0xFF1B, KeyCode.Escape)
    put(0x0020, KeyCode.Space)

    put(0xFF51, KeyCode.ArrowLeft)
    put(0xFF52, KeyCode.ArrowUp)
    put(0xFF53, KeyCode.ArrowRight)
    put(0xFF54, KeyCode.ArrowDown)

    put(0xFFBE, KeyCode.F1); put(0xFFBF, KeyCode.F2); put(0xFFC0, KeyCode.F3)
    put(0xFFC1, KeyCode.F4); put(0xFFC2, KeyCode.F5); put(0xFFC3, KeyCode.F6)
    put(0xFFC4, KeyCode.F7); put(0xFFC5, KeyCode.F8); put(0xFFC6, KeyCode.F9)
    put(0xFFC7, KeyCode.F10); put(0xFFC8, KeyCode.F11); put(0xFFC9, KeyCode.F12)

    put(0xFFE1, KeyCode.ShiftLeft)
    put(0xFFE2, KeyCode.ShiftRight)
    put(0xFFE3, KeyCode.ControlLeft)
    put(0xFFE4, KeyCode.ControlRight)
    put(0xFFE9, KeyCode.AltLeft)
    put(0xFFEA, KeyCode.AltRight)
    put(0xFFEB, KeyCode.MetaLeft)
    put(0xFFEC, KeyCode.MetaRight)
}

internal fun stateToModifiers(state: Int): KeyboardModifiers {
    var bits = 0
    if (state and SHIFT_MASK != 0) bits = bits or KeyboardModifiers.SHIFT
    if (state and CONTROL_MASK != 0) bits = bits or KeyboardModifiers.CTRL
    if (state and MOD1_MASK != 0) bits = bits or KeyboardModifiers.ALT
    if (state and MOD4_MASK != 0) bits = bits or KeyboardModifiers.META
    return KeyboardModifiers(bits)
}

private val linker: Linker = Linker.nativeLinker()

private val xLookupString: MethodHandle? by lazy {
    try {
        SymbolLookup.libraryLookup("libX11.so.6", Arena.global()).find("XLookupString")
            .map { linker.downcallHandle(it, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)) }
            .orElse(null)
    } catch (_: Throwable) { null }
}

/**
 * Best-effort keyboard text for a KeyPress event via `XLookupString`.
 *
 * Returns the ISO Latin-1 text produced by the key (honouring the modifier
 * state stored in the event), or `null` when:
 * - libX11 is unavailable (non-Linux) — the handle is null,
 * - the event has no display attached (synthetic test events),
 * - the key produces no printable text (control chars, modifiers, etc.).
 *
 * Composed / multibyte input is handled separately through the XIM path
 * (XFilterEvent + XIC); this only covers the common ASCII/Latin-1 case so the
 * [org.graphiks.kadre.core.KeyEvent.text] field is no longer always null.
 *
 * @param eventSegment Native pointer to the XKeyEvent (XEvent buffer).
 */
internal fun lookupX11Text(eventSegment: MemorySegment): String? {
    val handle = xLookupString ?: return null
    // Guard against synthetic events with a NULL display (XLookupString would crash).
    val displayPtr = try {
        eventSegment.get(ValueLayout.JAVA_LONG, OFFSET_DISPLAY)
    } catch (_: Throwable) {
        return null
    }
    if (displayPtr == 0L) return null
    return try {
        Arena.ofConfined().use { arena ->
            val buffer = arena.allocate(32L, 1L)
            val keysymOut = arena.allocate(ValueLayout.JAVA_LONG)
            val count = handle.invokeExact(
                eventSegment, buffer, 32, keysymOut, MemorySegment.NULL,
            ) as Int
            if (count <= 0) return@use null
            val bytes = ByteArray(count) { index -> buffer.get(ValueLayout.JAVA_BYTE, index.toLong()) }
            val text = String(bytes, Charsets.ISO_8859_1)
            // Filter out control characters (NUL, DEL, etc.) that are not user-visible text.
            if (text.isNotEmpty() && text.all { it.code >= 0x20 && it.code != 0x7F }) text else null
        }
    } catch (_: Throwable) {
        null
    }
}

object X11KeyMapper {
    private val pressedKeys: MutableSet<Int> = mutableSetOf()

    fun enableDetectableAutoRepeat(displayPtr: Long) {
        val handle = xkbSetDetectableAutoRepeat ?: return
        val display = MemorySegment.ofAddress(displayPtr)
        try {
            handle.invokeExact(display, 1, MemorySegment.NULL) as Int
        } catch (_: Throwable) {
            // The Xkb extension is optional.
        }
    }

    fun resetState() {
        pressedKeys.clear()
    }

    fun isModifierKey(keyCode: KeyCode?): Boolean = keyCode in modifierKeys

    fun initialModifierState(): KeyboardModifierState =
        KeyboardModifierState(
            logical = KeyboardModifiers.NONE,
            physical = ModifierKeys(
                leftShift = ModifierKeyState.Released,
                rightShift = ModifierKeyState.Released,
                leftCtrl = ModifierKeyState.Released,
                rightCtrl = ModifierKeyState.Released,
                leftAlt = ModifierKeyState.Released,
                rightAlt = ModifierKeyState.Released,
                leftMeta = ModifierKeyState.Released,
                rightMeta = ModifierKeyState.Released,
            ),
        )

    fun modifierStateFrom(previousState: KeyboardModifierState, keyCode: KeyCode?, state: KeyState): KeyboardModifierState {
        val keyState = when (state) {
            KeyState.Pressed -> ModifierKeyState.Pressed
            KeyState.Released -> ModifierKeyState.Released
        }
        val previous = previousState.physical
        val physical = when (keyCode) {
            KeyCode.ShiftLeft -> previous.copy(leftShift = keyState)
            KeyCode.ShiftRight -> previous.copy(rightShift = keyState)
            KeyCode.ControlLeft -> previous.copy(leftCtrl = keyState)
            KeyCode.ControlRight -> previous.copy(rightCtrl = keyState)
            KeyCode.AltLeft -> previous.copy(leftAlt = keyState)
            KeyCode.AltRight -> previous.copy(rightAlt = keyState)
            KeyCode.MetaLeft -> previous.copy(leftMeta = keyState)
            KeyCode.MetaRight -> previous.copy(rightMeta = keyState)
            else -> previous
        }
        return KeyboardModifierState(logical = logicalModifiersFrom(physical), physical = physical)
    }

    private fun logicalModifiersFrom(physical: ModifierKeys): KeyboardModifiers {
        var mods = KeyboardModifiers.NONE
        if (physical.leftShift == ModifierKeyState.Pressed || physical.rightShift == ModifierKeyState.Pressed) {
            mods += KeyboardModifiers.Shift
        }
        if (physical.leftCtrl == ModifierKeyState.Pressed || physical.rightCtrl == ModifierKeyState.Pressed) {
            mods += KeyboardModifiers.Ctrl
        }
        if (physical.leftAlt == ModifierKeyState.Pressed || physical.rightAlt == ModifierKeyState.Pressed) {
            mods += KeyboardModifiers.Alt
        }
        if (physical.leftMeta == ModifierKeyState.Pressed || physical.rightMeta == ModifierKeyState.Pressed) {
            mods += KeyboardModifiers.Meta
        }
        return mods
    }

    fun fromXEvent(
        eventSegment: MemorySegment,
        eventType: Int,
        keysym: Int = 0,
    ): WindowEvent.KeyInput? {
        val state = eventSegment.get(ValueLayout.JAVA_INT, OFFSET_STATE)
        val keycode = eventSegment.get(ValueLayout.JAVA_INT, OFFSET_KEYCODE) and 0xFFFF
        val isPressed = eventType == KeyPress
        val isRepeat = isPressed && pressedKeys.contains(keycode)

        if (isPressed) {
            pressedKeys.add(keycode)
        } else {
            pressedKeys.remove(keycode)
        }

        val mappedCode = if (keysym != 0) KEYSYM_TABLE[keysym] else null
        val keyState = if (isPressed) KeyState.Pressed else KeyState.Released
        val modifierState = if (isModifierKey(mappedCode)) {
            modifierStateFrom(initialModifierState(), mappedCode, keyState)
        } else {
            null
        }
        val modifiers = modifierState?.logical ?: stateToModifiers(state)
        val native = NativeKeyInfo(
            platform = KeyPlatform.X11,
            scanCode = keycode.toLong(),
            keyValue = keysym.takeIf { it != 0 }?.toString(),
            nativeCode = NativeKeyCode.X11(keycode.toLong()),
            nativeKey = keysym.takeIf { it != 0 }?.let { NativeLogicalKey.X11(it.toLong()) },
        )
        val logicalKey = mappedCode?.defaultLogicalKey() ?: LogicalKey.Unidentified(native)

        val lookupText = if (isPressed) lookupX11Text(eventSegment) else null
        val resolvedText = lookupText ?: mappedCode?.defaultText()

        return WindowEvent.KeyInput(
            event = KeyEvent(
                physicalKey = mappedCode?.let(PhysicalKey::Code) ?: PhysicalKey.Native(NativeKeyCode.X11(keycode.toLong())),
                logicalKey = logicalKey,
                state = keyState,
                modifiers = modifiers,
                repeat = isRepeat,
                text = resolvedText,
                textWithAllModifiers = resolvedText,
                keyWithoutModifiers = mappedCode?.defaultText(),
                native = native,
            ),
            deviceId = null,
        )
    }

    private val modifierKeys = setOf(
        KeyCode.ShiftLeft,
        KeyCode.ShiftRight,
        KeyCode.ControlLeft,
        KeyCode.ControlRight,
        KeyCode.AltLeft,
        KeyCode.AltRight,
        KeyCode.MetaLeft,
        KeyCode.MetaRight,
    )
}

internal class X11KeyboardModifierTracker {
    private var modifierState = X11KeyMapper.initialModifierState()

    fun initializeIfNeeded(nextState: KeyboardModifierState): WindowEvent.ModifiersChanged? {
        if (nextState == modifierState) return null
        modifierState = nextState
        return WindowEvent.ModifiersChanged(nextState)
    }

    fun modifierStateFor(keyCode: KeyCode?, state: KeyState): KeyboardModifierState? =
        if (X11KeyMapper.isModifierKey(keyCode)) {
            X11KeyMapper.modifierStateFrom(modifierState, keyCode, state)
        } else {
            null
        }

    fun modifiersChangedIfNeeded(nextState: KeyboardModifierState?): WindowEvent.ModifiersChanged? {
        nextState ?: return null
        if (nextState == modifierState) return null
        modifierState = nextState
        return WindowEvent.ModifiersChanged(nextState)
    }

    fun resetIfNeeded(): WindowEvent.ModifiersChanged? {
        val initial = X11KeyMapper.initialModifierState()
        if (modifierState == initial) return null
        modifierState = initial
        return WindowEvent.ModifiersChanged(initial)
    }
}
