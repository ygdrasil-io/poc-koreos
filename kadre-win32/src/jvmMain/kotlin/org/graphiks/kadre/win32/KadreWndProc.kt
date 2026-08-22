/**
 * Centralized Win32 window procedure (WndProc) for kadre.
 *
 * This file defines [KadreWndProc], a singleton object that dispatches Win32 messages
 * to kadre-core [WindowEvent]s, and forwards them to the associated [Win32Window] instance
 * via a [WindowEventHandler] installed by the caller.
 *
 * ## Architecture
 *
 * ```
 *  Win32 OS  ─[WM_*]→  KadreWndProc.dispatch()
 *                           │
 *                           ├─ translates the message into a WindowEvent
 *                           │
 *                           └─ resolves Win32Window via windowResolver(hwnd)
 *                                   │
 *                                   └─ calls WindowEventHandler.onEvent(window, event)
 * ```
 *
 * ## Usage
 *
 * ```kotlin
 * KadreWndProc.install { hwnd -> win32WindowMap[hwnd] }
 * // …
 * // The FFM stub calls KadreWndProc.dispatch(hwnd, msg, wParam, lParam)
 * ```
 *
 * ## Platform constraints
 *
 * On macOS/Linux, no real FFM call is performed. The [dispatch] method
 * can be called in tests with synthetic values.
 *
 * @see WindowEvent
 * @see Win32KeyMapper
 * @see Win32Constants
 */
package org.graphiks.kadre.win32

import org.graphiks.kffi.win32.generated.*
import org.graphiks.kadre.core.ButtonSource
import org.graphiks.kadre.core.FingerId
import org.graphiks.kadre.core.KeyCode
import org.graphiks.kadre.core.KeyEvent
import org.graphiks.kadre.core.KeyPlatform
import org.graphiks.kadre.core.KeyState
import org.graphiks.kadre.core.KeyboardModifierState
import org.graphiks.kadre.core.KeyboardModifiers
import org.graphiks.kadre.core.LogicalKey
import org.graphiks.kadre.core.ModifierKeyState
import org.graphiks.kadre.core.ModifierKeys
import org.graphiks.kadre.core.MouseButton
import org.graphiks.kadre.core.NativeKeyInfo
import org.graphiks.kadre.core.NativeKeyCode
import org.graphiks.kadre.core.NativeLogicalKey
import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.core.PointerKind
import org.graphiks.kadre.core.PointerSource
import org.graphiks.kadre.core.TouchPhase
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

// ── Non-generated function bindings ────────────────────────────────────────────

private fun lookupDowncall(libName: String, symbol: String, desc: FunctionDescriptor): MethodHandle? {
    return try {
        val lookup = SymbolLookup.libraryLookup(libName, Arena.global())
        lookup.find(symbol).map { Linker.nativeLinker().downcallHandle(it, desc) }.orElse(null)
    } catch (_: Throwable) { null }
}

private val immGetCompositionStringW: MethodHandle? by lazy {
    lookupDowncall("imm32.dll", "ImmGetCompositionStringW",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT))
}

private val dragQueryFileW: MethodHandle? by lazy {
    lookupDowncall("shell32.dll", "DragQueryFileW",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT))
}

private val dragQueryPoint: MethodHandle? by lazy {
    lookupDowncall("shell32.dll", "DragQueryPoint",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS))
}

private val dragFinish: MethodHandle? by lazy {
    lookupDowncall("shell32.dll", "DragFinish",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS))
}

// ── Handler interface ─────────────────────────────────────────────────────────

/**
 * Receiver for Win32 window events.
 *
 * Implemented by the application layer (Win32 EventLoop) to receive the [WindowEvent]s
 * translated by [KadreWndProc] and dispatch them to the [org.graphiks.kadre.core.ApplicationHandler].
 */
fun interface WindowEventHandler {
    /**
     * Called for each window event translated from a Win32 message.
     *
     * @param hwnd  Native handle of the source window (64-bit integer address).
     * @param event Translated kadre event.
     */
    fun onEvent(hwnd: Long, event: WindowEvent)
}

// ── KadreWndProc ─────────────────────────────────────────────────────────────

/**
 * Central dispatcher for Win32 messages → kadre [WindowEvent]s.
 *
 * ### Installation
 * Before the message loop starts, call [install] to register
 * the handler that will receive the events:
 *
 * ```kotlin
 * KadreWndProc.install { hwnd, event ->
 *     win32WindowMap[hwnd]?.let { appHandler.onWindowEvent(it.id, event) }
 * }
 * ```
 *
 * ### Call from Win32Window
 * [Win32Window] must route its static `wndProc` to [dispatch]:
 *
 * ```kotlin
 * @JvmStatic
 * fun wndProc(hwnd: MemorySegment, msg: Int, wParam: Long, lParam: Long): Long =
 *     KadreWndProc.dispatch(hwnd.address(), msg, wParam, lParam)
 * ```
 */
object KadreWndProc {
    init { init() }

    /**
     * Handler installed by the caller via [install].
     *
     * Null until [install] is called — events are silently
     * ignored (but DefWindowProcW is always called for unhandled messages).
     */
    @Volatile
    private var handler: WindowEventHandler? = null

    /**
     * Window size constraints keyed by HWND address.
     *
     * Registered by [Win32Window] so that [WM_GETMINMAXINFO] can apply min/max size
     * and resize increments during interactive window sizing.
     */
    private val windowConstraints = java.util.concurrent.ConcurrentHashMap<Long, WindowConstraints>()

    /**
     * Per-window size constraint values for WM_GETMINMAXINFO.
     */
    data class WindowConstraints(
        val minSize: PhysicalSize<Int>? = null,
        val maxSize: PhysicalSize<Int>? = null,
        val resizeIncrements: PhysicalSize<Int>? = null,
    )

    /**
     * Registers or updates size constraints for the given window.
     */
    fun registerConstraints(hwnd: Long, constraints: WindowConstraints) {
        if (constraints.minSize != null || constraints.maxSize != null || constraints.resizeIncrements != null) {
            windowConstraints[hwnd] = constraints
        } else {
            windowConstraints.remove(hwnd)
        }
    }

    /**
     * Removes size constraints for the given window (called when the window is destroyed).
     */
    fun unregisterConstraints(hwnd: Long) {
        windowConstraints.remove(hwnd)
    }

    /**
     * Removes physical modifier state for the given window (called when the window is destroyed).
     */
    fun unregisterPhysicalModifiers(hwnd: Long) {
        physicalModifierStates.remove(hwnd)
    }

    /**
     * Aligns [value] up to the nearest multiple of [increment].
     * Returns the original value when [increment] is null, zero, or negative.
     */
    private fun alignUp(value: Int, increment: Int?): Int {
        if (increment == null || increment <= 0) return value
        val remainder = value % increment
        return if (remainder == 0) value else value + increment - remainder
    }

    /** Windows whose cursor is currently inside (to emit PointerEntered on the first move in). */
    private val insideWindows = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    // ── Installation ──────────────────────────────────────────────────────────

    /**
     * Registers the [WindowEventHandler] that will receive the translated [WindowEvent]s.
     *
     * Must be called before the Win32 message loop starts.
     * Thread-safe (volatile write).
     *
     * @param handler Handler implementation (lambda or object).
     */
    fun install(handler: WindowEventHandler) {
        this.handler = handler
    }

    /**
     * Unregisters the current handler.
     *
     * Useful at the end of the message loop to avoid object leaks.
     */
    fun uninstall() {
        handler = null
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────

    /**
     * Dispatches a Win32 message to a kadre [WindowEvent] and forwards it to the handler.
     *
     * Signature compatible with the Win32 WndProc:
     * `LRESULT CALLBACK KadreWndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam)`
     *
     * Unrecognized messages are delegated to [defWindowProcW].
     *
     * @param hwnd   Integer address of the HWND (MemorySegment.address()).
     * @param msg    Message identifier (WM_*).
     * @param wParam First message parameter.
     * @param lParam Second message parameter.
     * @return LRESULT to return to Windows.
     */
    fun dispatch(hwnd: Long, msg: Int, wParam: Long, lParam: Long): Long {
        return when (msg.toUInt()) {

            // ── Redraw ────────────────────────────────────────────────────────
            WM_PAINT.toUInt() -> {
                emit(hwnd, WindowEvent.RedrawRequested)
                // Return 0 without calling BeginPaint/EndPaint — the window will be
                // redrawn by the render engine (wgpu4k) on the next frame.
                0L
            }

            // ── Kadre private native actions ─────────────────────────────────
            WM_KADRE_NON_CLIENT_DRAG.toUInt() -> {
                Win32Window.performNonClientDrag(MemorySegment.ofAddress(hwnd), wParam)
                0L
            }

            // ── Resize ────────────────────────────────────────────────────────
            WM_SIZE.toUInt() -> {
                // lParam: LOWORD = new width, HIWORD = new height (client pixels)
                val width  = (lParam and 0xFFFF).toInt()
                val height = ((lParam ushr 16) and 0xFFFF).toInt()
                emit(hwnd, WindowEvent.Resized(PhysicalSize(width, height)))
                0L
            }

            // ── Move ──────────────────────────────────────────────────────────
            WM_MOVE.toUInt() -> {
                // lParam: LOWORD = x, HIWORD = y (client top-left, screen coords, signed)
                val x = (lParam and 0xFFFF).toShort().toInt()
                val y = ((lParam ushr 16) and 0xFFFF).toShort().toInt()
                emit(hwnd, WindowEvent.Moved(PhysicalPosition(x, y)))
                0L
            }

            // ── Focus ─────────────────────────────────────────────────────────
            WM_NCACTIVATE.toUInt() -> {
                Win32FocusState.setActive(hwnd, wParam != 0L)?.let { gained ->
                    emit(hwnd, WindowEvent.Focused(gained))
                }
                defWindowProcW(hwnd, msg, wParam, lParam)
            }
            WM_SETFOCUS.toUInt() -> {
                Win32FocusState.setFocused(hwnd, true)?.let { gained ->
                    emit(hwnd, WindowEvent.Focused(gained))
                }
                0L
            }
            WM_KILLFOCUS.toUInt() -> {
                Win32FocusState.setFocused(hwnd, false)?.let { gained ->
                    emit(hwnd, WindowEvent.Focused(gained))
                }
                0L
            }

            // ── Keyboard ──────────────────────────────────────────────────────
            WM_KEYDOWN.toUInt(),
            WM_SYSKEYDOWN.toUInt() -> {
                val vkCode   = wParam.toInt()
                val isRepeat = (lParam and KF_REPEAT) != 0L
                val mods     = currentModifiers()
                val rawScanCode = ((lParam ushr 16) and 0xFF).toInt()
                val extended = (lParam and KF_EXTENDED) != 0L
                val currentPhysical = physicalModifierStates[hwnd] ?: ModifierKeys()
                val resolvedKeyCode = if (isGenericModifierVk(vkCode) && rawScanCode == 0) {
                    resolveGenericModifierVk(vkCode, rawScanCode, KeyState.Pressed, currentPhysical)
                        ?: Win32KeyMapper.keyCode(vkCode, rawScanCode, extended)
                } else {
                    Win32KeyMapper.keyCode(vkCode, rawScanCode, extended)
                }
                if (resolvedKeyCode != null && isModifierKeyCode(resolvedKeyCode)) {
                    val newPhysical = updatePhysicalModifiers(hwnd, resolvedKeyCode, KeyState.Pressed)
                    emit(hwnd, WindowEvent.ModifiersChanged(KeyboardModifierState(logical = mods, physical = newPhysical)))
                }
                emit(hwnd, keyEvent(vkCode, KeyState.Pressed, mods, isRepeat, lParam))
                0L
            }

            WM_KEYUP.toUInt(),
            WM_SYSKEYUP.toUInt() -> {
                val vkCode   = wParam.toInt()
                val mods     = currentModifiers()
                val rawScanCode = ((lParam ushr 16) and 0xFF).toInt()
                val extended = (lParam and KF_EXTENDED) != 0L
                val currentPhysical = physicalModifierStates[hwnd] ?: ModifierKeys()
                val resolvedKeyCode = if (isGenericModifierVk(vkCode) && rawScanCode == 0) {
                    resolveGenericModifierVk(vkCode, rawScanCode, KeyState.Released, currentPhysical)
                        ?: Win32KeyMapper.keyCode(vkCode, rawScanCode, extended)
                } else {
                    Win32KeyMapper.keyCode(vkCode, rawScanCode, extended)
                }
                if (resolvedKeyCode != null && isModifierKeyCode(resolvedKeyCode)) {
                    val newPhysical = updatePhysicalModifiers(hwnd, resolvedKeyCode, KeyState.Released)
                    emit(hwnd, WindowEvent.ModifiersChanged(KeyboardModifierState(logical = mods, physical = newPhysical)))
                }
                emit(hwnd, keyEvent(vkCode, KeyState.Released, mods, isRepeat = false, lParam))
                0L
            }

            // ── Cursor movement ───────────────────────────────────────────────
            WM_MOUSEMOVE.toUInt() -> {
                // First move after the cursor entered the client area → PointerEntered.
                val position = mousePosition(lParam)
                if (insideWindows.add(hwnd)) {
                    emit(hwnd, WindowEvent.PointerEntered(null, position, primary = true, kind = PointerKind.Mouse))
                }
                emit(hwnd, WindowEvent.PointerMoved(null, position, primary = true, source = PointerSource.Mouse))
                // Arm TrackMouseEvent to detect WM_MOUSELEAVE
                armMouseLeaveTracking(hwnd)
                0L
            }

            // ── Cursor exit ───────────────────────────────────────────────────
            WM_MOUSELEAVE.toUInt() -> {
                // WM_MOUSELEAVE tracking is automatically disarmed after receipt.
                // It will be re-armed on the next WM_MOUSEMOVE if the cursor returns.
                insideWindows.remove(hwnd)
                emit(hwnd, WindowEvent.PointerLeft(null, position = null, primary = true, kind = PointerKind.Mouse))
                0L
            }

            // ── Mouse buttons ─────────────────────────────────────────────────
            WM_LBUTTONDOWN.toUInt() -> {
                emit(hwnd, pointerButton(MouseButton.Left, KeyState.Pressed, lParam))
                0L
            }
            WM_LBUTTONUP.toUInt() -> {
                emit(hwnd, pointerButton(MouseButton.Left, KeyState.Released, lParam))
                0L
            }
            WM_RBUTTONDOWN.toUInt() -> {
                emit(hwnd, pointerButton(MouseButton.Right, KeyState.Pressed, lParam))
                0L
            }
            WM_RBUTTONUP.toUInt() -> {
                emit(hwnd, pointerButton(MouseButton.Right, KeyState.Released, lParam))
                0L
            }
            WM_MBUTTONDOWN.toUInt() -> {
                emit(hwnd, pointerButton(MouseButton.Middle, KeyState.Pressed, lParam))
                0L
            }
            WM_MBUTTONUP.toUInt() -> {
                emit(hwnd, pointerButton(MouseButton.Middle, KeyState.Released, lParam))
                0L
            }

            // ── Additional buttons (X1 / X2) ─────────────────────────────────
            WM_XBUTTONDOWN.toUInt() -> {
                // HIWORD(wParam) = X button number (XBUTTON1 = 1, XBUTTON2 = 2)
                val xButton = ((wParam ushr 16) and 0xFFFF).toInt()
                val button = MouseButton.Other(xButton)
                emit(hwnd, pointerButton(button, KeyState.Pressed, lParam))
                // WM_XBUTTONDOWN must return TRUE (non-zero) per the Win32 docs
                1L
            }
            WM_XBUTTONUP.toUInt() -> {
                val xButton = ((wParam ushr 16) and 0xFFFF).toInt()
                val button = MouseButton.Other(xButton)
                emit(hwnd, pointerButton(button, KeyState.Released, lParam))
                // WM_XBUTTONUP must return TRUE (non-zero) per the Win32 docs
                1L
            }

            // ── Wheel ─────────────────────────────────────────────────────────
            WM_MOUSEWHEEL.toUInt() -> {
                // wParam: HIWORD = signed delta (multiple of WHEEL_DELTA = 120)
                val rawDelta = ((wParam ushr 16) and 0xFFFF).toShort().toInt()
                val deltaY   = rawDelta.toDouble() / WHEEL_DELTA
                emit(hwnd, WindowEvent.MouseWheel(null, deltaX = 0.0, deltaY = deltaY, phase = TouchPhase.Moved))
                0L
            }

            // ── Drag & Drop (WM_DROPFILES) ────────────────────────────────────
            WM_DROPFILES.toUInt() -> {
                handleFileDrop(hwnd, wParam)
                0L
            }

            // ── Touch ─────────────────────────────────────────────────────────
            WM_TOUCH.toUInt() -> {
                handleTouch(hwnd, wParam, lParam)
                // WM_TOUCH must be passed to DefWindowProcW when not fully handled,
                // but since we consume every contact and close the handle ourselves,
                // returning 0 (handled) is correct here.
                0L
            }

            // ── Gesture ────────────────────────────────────────────────────────
            WM_GESTURE.toUInt() -> {
                // lParam = HGESTUREINFO handle
                handleGesture(hwnd, lParam)
                0L
            }

            // ── Close ─────────────────────────────────────────────────────────
            WM_CLOSE.toUInt() -> {
                emit(hwnd, WindowEvent.CloseRequested)
                // Do not call DefWindowProcW here — the application decides whether to destroy the window.
                0L
            }

            // ── Visibility (Occluded) ───────────────────────────────────────────
            // WM_SHOWWINDOW fires when ShowWindow is called (including minimize/restore).
            // wParam = TRUE → window becoming visible, FALSE → window becoming hidden.
            WM_SHOWWINDOW.toUInt() -> {
                emit(hwnd, WindowEvent.Occluded(wParam == 0L))
                defWindowProcW(hwnd, msg, wParam, lParam)
            }

            // ── Destroy ───────────────────────────────────────────────────────
            WM_DESTROY.toUInt() -> {
                emit(hwnd, WindowEvent.Destroyed)
                Win32FocusState.unregister(hwnd)
                unregisterConstraints(hwnd)
                unregisterPhysicalModifiers(hwnd)
                // PostQuitMessage(0) — signal to end the Win32 message loop.
                postQuitMessage(0)
                0L
            }

            // ── Min/max size & resize increments ──────────────────────────────
            WM_GETMINMAXINFO.toUInt() -> {
                val c = windowConstraints[hwnd]
                if (c != null) {
                    val mmi = MemorySegment.ofAddress(lParam)
                    val inc = c.resizeIncrements
                    val incX = inc?.width
                    val incY = inc?.height
                    // ptMinTrackSize at offset 24+0/4, ptMaxTrackSize at offset 32+0/4
                    val baseMinTrack = 24L
                    val baseMaxTrack = 32L
                    val rawMinX = mmi.get(ValueLayout.JAVA_INT, baseMinTrack)
                    val rawMinY = mmi.get(ValueLayout.JAVA_INT, baseMinTrack + 4L)
                    val rawMaxX = mmi.get(ValueLayout.JAVA_INT, baseMaxTrack)
                    val rawMaxY = mmi.get(ValueLayout.JAVA_INT, baseMaxTrack + 4L)
                    val minSize = c.minSize
                    val maxSize = c.maxSize
                    val newMinX = alignUp(maxOf(rawMinX, minSize?.width ?: 0), incX)
                    val newMinY = alignUp(maxOf(rawMinY, minSize?.height ?: 0), incY)
                    val maxX = maxSize?.width?.let { minOf(rawMaxX, it) } ?: rawMaxX
                    val maxY = maxSize?.height?.let { minOf(rawMaxY, it) } ?: rawMaxY
                    // Align max track sizes down to increment boundary
                    val newMaxX = if (incX != null && incX > 0) (maxX / incX) * incX else maxX
                    val newMaxY = if (incY != null && incY > 0) (maxY / incY) * incY else maxY
                    mmi.set(ValueLayout.JAVA_INT, baseMinTrack, newMinX)
                    mmi.set(ValueLayout.JAVA_INT, baseMinTrack + 4L, newMinY)
                    mmi.set(ValueLayout.JAVA_INT, baseMaxTrack, newMaxX)
                    mmi.set(ValueLayout.JAVA_INT, baseMaxTrack + 4L, newMaxY)
                }
                0L
            }

            // ── DPI change ────────────────────────────────────────────────────
            WM_DPICHANGED.toUInt() -> {
                // wParam: LOWORD = new DPI X, HIWORD = new DPI Y
                val dpiX   = (wParam and DPI_WPARAM_MASK).toInt()
                val factor = dpiX.toDouble() / 96.0  // 96 DPI = factor 1.0 (100%)
                emit(hwnd, WindowEvent.ScaleFactorChanged(factor))
                0L
            }

            // ── IME ───────────────────────────────────────────────────────────
            WM_IME_SETCONTEXT.toUInt() -> {
                // Clear ISC_SHOWUIALL to prevent the IME from drawing its own UI
                // (composition window, candidate window). The application manages
                // positioning via setImeCursorArea.
                val lParamCleared = lParam and ISC_SHOWUIALL.inv()
                defWindowProcW(hwnd, msg, wParam, lParamCleared)
            }

            WM_IME_STARTCOMPOSITION.toUInt() -> {
                emit(hwnd, WindowEvent.Ime(WindowEvent.Ime.ImeEvent.Enabled))
                0L
            }

            WM_IME_COMPOSITION.toUInt() -> {
                handleImeComposition(hwnd, lParam)
                0L
            }

            WM_IME_ENDCOMPOSITION.toUInt() -> {
                emit(hwnd, WindowEvent.Ime(WindowEvent.Ime.ImeEvent.Disabled))
                0L
            }

            WM_INPUTLANGCHANGE.toUInt() -> {
                // Pass through — the keyboard layout changed, but kadre does
                // not currently track HKL.
                defWindowProcW(hwnd, msg, wParam, lParam)
            }

            // ── Default ───────────────────────────────────────────────────────
            else -> defWindowProcW(hwnd, msg, wParam, lParam)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Forwards a [WindowEvent] to the installed [handler], if present.
     */
    private fun emit(hwnd: Long, event: WindowEvent) {
        handler?.onEvent(hwnd, event)
    }

    /**
     * Calls DefWindowProcW via the FFM binding (lazy, null on macOS/Linux).
     *
     * @param hwnd  Integer address of the HWND.
     * @param msg   Message identifier.
     * @param wParam First parameter.
     * @param lParam Second parameter.
     * @return Return value of DefWindowProcW, or 0 if the binding is unavailable.
     */
    private fun defWindowProcW(hwnd: Long, msg: Int, wParam: Long, lParam: Long): Long {
        val hwndSeg = MemorySegment.ofAddress(hwnd)
        return DefWindowProcW(hwndSeg, msg, wParam, lParam)
    }

    /**
     * Calls PostQuitMessage(nExitCode) via the FFM binding (lazy, null on macOS/Linux).
     *
     * Triggers the exit of the GetMessage message loop when WM_DESTROY is received.
     *
     * @param nExitCode Exit code (0 = normal success).
     */
    private fun postQuitMessage(nExitCode: Int) {
        PostQuitMessage(nExitCode)
    }

    /**
     * Reads the current state of the modifier keys via GetKeyState (lazy, null on macOS/Linux).
     *
     * GetKeyState reads the key state at the moment the message is processed — consistent
     * with the Win32 message thread.
     *
     * @return [KeyboardModifiers] representing the active modifiers.
     */
    private fun currentModifiers(): KeyboardModifiers {
        var bits = 0
        // GetKeyState returns a Short: bit 15 = key down, bit 0 = toggle
        if ((GetKeyState(VK_SHIFT).toInt() and 0x8000) != 0) bits = bits or KeyboardModifiers.SHIFT
        if ((GetKeyState(VK_CONTROL).toInt() and 0x8000) != 0) bits = bits or KeyboardModifiers.CTRL
        if ((GetKeyState(VK_MENU).toInt() and 0x8000) != 0) bits = bits or KeyboardModifiers.ALT
        if ((GetKeyState(VK_LWIN).toInt() and 0x8000) != 0 ||
            (GetKeyState(VK_RWIN).toInt() and 0x8000) != 0) bits = bits or KeyboardModifiers.META
        return KeyboardModifiers(bits)
    }

    /** Per-window physical left/right modifier state tracked across key events. */
    private val physicalModifierStates = java.util.concurrent.ConcurrentHashMap<Long, ModifierKeys>()

    /**
     * Generic VK codes that do not distinguish left/right in their VK value.
     */
    private val genericModifierVkCodes = setOf(VK_SHIFT, VK_CONTROL, VK_MENU)

    private fun isGenericModifierVk(vkCode: Int): Boolean = vkCode in genericModifierVkCodes

    /**
     * Resolves a generic VK code (VK_SHIFT/VK_CONTROL/VK_MENU) to the correct
     * left/right [KeyCode] when the scan code is 0 or ambiguous.
     *
     * For VK_SHIFT with scanCode==0: uses the current [ModifierKeys] state and
     * the [KeyState] to infer which side changed:
     * - **Pressed**: the side NOT currently pressed (toggle inference)
     * - **Released**: the side currently pressed
     *
     * For VK_CONTROL/VK_MENU: the extended-key flag already distinguishes sides,
     * so scanCode==0 is harmless — returns null to let the standard dispatch handle it.
     */
    private fun resolveGenericModifierVk(
        vkCode: Int,
        scanCode: Int,
        state: KeyState,
        current: ModifierKeys,
    ): KeyCode? {
        if (vkCode != VK_SHIFT || scanCode != 0) return null
        return when (state) {
            KeyState.Pressed -> when {
                current.leftShift == ModifierKeyState.Pressed &&
                    current.rightShift != ModifierKeyState.Pressed -> KeyCode.ShiftRight
                current.rightShift == ModifierKeyState.Pressed &&
                    current.leftShift != ModifierKeyState.Pressed -> KeyCode.ShiftLeft
                else -> KeyCode.ShiftLeft
            }
            KeyState.Released -> when {
                current.leftShift == ModifierKeyState.Pressed -> KeyCode.ShiftLeft
                current.rightShift == ModifierKeyState.Pressed -> KeyCode.ShiftRight
                else -> KeyCode.ShiftLeft
            }
        }
    }

    private fun isModifierKeyCode(keyCode: KeyCode): Boolean = keyCode in setOf(
        KeyCode.ShiftLeft, KeyCode.ShiftRight,
        KeyCode.ControlLeft, KeyCode.ControlRight,
        KeyCode.AltLeft, KeyCode.AltRight,
        KeyCode.MetaLeft, KeyCode.MetaRight,
    )

    private fun updatePhysicalModifiers(hwnd: Long, keyCode: KeyCode, state: KeyState): ModifierKeys {
        val current = physicalModifierStates.getOrDefault(hwnd, ModifierKeys())
        val modifierState = when (state) {
            KeyState.Pressed -> ModifierKeyState.Pressed
            KeyState.Released -> ModifierKeyState.Released
        }
        val newState = when (keyCode) {
            KeyCode.ShiftLeft   -> current.copy(leftShift = modifierState)
            KeyCode.ShiftRight  -> current.copy(rightShift = modifierState)
            KeyCode.ControlLeft -> current.copy(leftCtrl = modifierState)
            KeyCode.ControlRight -> current.copy(rightCtrl = modifierState)
            KeyCode.AltLeft     -> current.copy(leftAlt = modifierState)
            KeyCode.AltRight    -> current.copy(rightAlt = modifierState)
            KeyCode.MetaLeft    -> current.copy(leftMeta = modifierState)
            KeyCode.MetaRight   -> current.copy(rightMeta = modifierState)
            else -> return current
        }
        physicalModifierStates[hwnd] = newState
        return newState
    }

    private fun keyEvent(
        vkCode: Int,
        state: KeyState,
        modifiers: KeyboardModifiers,
        isRepeat: Boolean,
        lParam: Long = 0L,
    ): WindowEvent.KeyInput {
        val rawScanCode = (lParam ushr 16) and 0xFF
        val extended = (lParam and KF_EXTENDED) != 0L
        val mappedCode = Win32KeyMapper.keyCode(vkCode, rawScanCode.toInt(), extended)
        val scanCode = rawScanCode
            .takeIf { it != 0L }
            ?.let { if (extended) 0xE000L or it else it }
        val native = NativeKeyInfo(
            platform = KeyPlatform.Win32,
            scanCode = scanCode,
            virtualKey = vkCode.toLong(),
            nativeCode = NativeKeyCode.Win32(scanCode = scanCode, virtualKey = vkCode.toLong()),
            nativeKey = NativeLogicalKey.Win32(vkCode.toLong()),
        )
        val logicalKey = mappedCode?.defaultLogicalKey() ?: LogicalKey.Unidentified(native)
        // Share one keyboard-state capture (arena-managed) between text variants to
        // minimise dead-key state corruption — ToUnicode mutates the internal keyboard
        // layout buffer on each call, so two separate GetKeyboardState + ToUnicode
        // sequences would double-consume dead keys on layouts like AZERTY.
        val (win32Text, win32TextNoMods) = if (!isRepeat) {
            val textHandle = toUnicodeEx ?: toUnicode
            if (textHandle != null) {
                try {
                    java.lang.foreign.Arena.ofConfined().use { arena ->
                        val keyState = arena.allocate(256L, 1L)
                        getKeyboardState?.invoke(keyState)
                        val withMods = toUnicodeText(textHandle, vkCode, rawScanCode.toInt(), keyState)
                        val noMods = toUnicodeTextWithoutModifiers(textHandle, vkCode, rawScanCode.toInt(), keyState)
                        withMods to noMods
                    }
                } catch (_: Throwable) { null to null }
            } else null to null
        } else null to null
        return WindowEvent.KeyInput(
            event = KeyEvent(
                physicalKey = Win32KeyMapper.physicalKey(vkCode, rawScanCode.toInt(), extended),
                logicalKey = logicalKey,
                state = state,
                modifiers = modifiers,
                repeat = isRepeat,
                text = win32Text ?: mappedCode?.defaultText(),
                textWithAllModifiers = win32Text ?: mappedCode?.defaultText(),
                keyWithoutModifiers = win32TextNoMods ?: mappedCode?.defaultText(),
                native = native,
            ),
            deviceId = null,
        )
    }

    /**
     * Returns true if the given VK code is a modifier key.
     */
    private fun isModifierVk(vkCode: Int): Boolean = vkCode in setOf(
        VK_SHIFT, VK_LSHIFT, VK_RSHIFT,
        VK_CONTROL, VK_LCONTROL, VK_RCONTROL,
        VK_MENU, VK_LMENU, VK_RMENU,
        VK_LWIN, VK_RWIN,
    )

    /**
     * Calls ToUnicode (or ToUnicodeEx) with the given [keyState] in the caller's
     * arena, returning the printable text. Used for both [KeyEvent.text] and
     * [KeyEvent.textWithAllModifiers] (same value on Win32).
     */
    private fun toUnicodeText(handle: java.lang.invoke.MethodHandle, vkCode: Int, scanCode: Int, keyState: MemorySegment): String? {
        return callToUnicode(handle, vkCode, scanCode, keyState)
    }

    /**
     * Calls ToUnicode(Ex) with modifier keys cleared, returning the base character
     * without modifier influence. Used for [KeyEvent.keyWithoutModifiers].
     * Operates on a copy of [keyState] so the caller's snapshot is not mutated.
     */
    private fun toUnicodeTextWithoutModifiers(handle: java.lang.invoke.MethodHandle, vkCode: Int, scanCode: Int, keyState: MemorySegment): String? {
        clearModifiers(keyState)
        return callToUnicode(handle, vkCode, scanCode, keyState)
    }

    private fun callToUnicode(handle: java.lang.invoke.MethodHandle, vkCode: Int, scanCode: Int, keyState: MemorySegment): String? {
        return try {
            java.lang.foreign.Arena.ofConfined().use { arena ->
                val buf = arena.allocate(16L, 2L)
                val result = if (handle === toUnicodeEx) {
                    val hkl = getKeyboardLayout?.invoke(0) as? MemorySegment ?: MemorySegment.NULL
                    handle.invokeExact(vkCode, scanCode, keyState, buf, 8, 0, hkl) as Int
                } else {
                    handle.invokeExact(vkCode, scanCode, keyState, buf, 8, 0) as Int
                }
                extractChars(buf, result)
            }
        } catch (_: Throwable) { null }
    }

    private fun extractChars(buf: MemorySegment, count: Int): String? {
        if (count <= 0) return null
        val sb = StringBuilder()
        for (i in 0 until count) {
            val ch = buf.getAtIndex(ValueLayout.JAVA_CHAR, i.toLong())
            if (ch >= ' ') sb.append(ch)
        }
        return if (sb.isEmpty()) null else sb.toString()
    }

    /**
     * Clears the high bit (key-down) in [keyState] for all modifier VK codes,
     * so subsequent ToUnicode calls treat modifiers as not pressed.
     * Operates in-place — the caller's snapshot is intentionally mutated because
     * text extraction is ordered: textWithAllModifiers first, then keyWithoutModifiers.
     */
    private fun clearModifiers(keyState: MemorySegment) {
        for (vk in MODIFIER_VK_CODES) {
            val current = keyState.get(ValueLayout.JAVA_BYTE, vk.toLong()).toInt() and 0xFF
            keyState.set(ValueLayout.JAVA_BYTE, vk.toLong(), (current and 0x7F).toByte())
        }
    }

    /** VK codes for modifier keys, used to clear their state for keyWithoutModifiers. */
    private val MODIFIER_VK_CODES: IntArray = intArrayOf(
        VK_SHIFT, VK_LSHIFT, VK_RSHIFT,
        VK_CONTROL, VK_LCONTROL, VK_RCONTROL,
        VK_MENU, VK_LMENU, VK_RMENU,
        VK_LWIN, VK_RWIN,
    )

    /**
     * Arms WM_MOUSELEAVE tracking for the window [hwnd] via TrackMouseEvent.
     *
     * Must be called on every WM_MOUSEMOVE to keep the tracking active,
     * because Windows automatically disarms TrackMouseEvent after sending
     * WM_MOUSELEAVE. Redundant calls (tracking already active) are gracefully
     * ignored by Windows.
     *
     * On macOS/Linux (trackMouseEvent null), this method is a no-op.
     *
     * @param hwndAddr Integer address of the HWND (MemorySegment.address()).
     */
    private fun armMouseLeaveTracking(hwndAddr: Long) {
        try {
            val hwndSeg = MemorySegment.ofAddress(hwndAddr)
            val tme = MemorySegment.ofArray(LongArray(3))
            tme.set(ValueLayout.JAVA_INT,  0L, TRACKMOUSEEVENT_SIZE)
            tme.set(ValueLayout.JAVA_INT,  4L, TME_LEAVE)
            tme.set(ValueLayout.ADDRESS,   8L, hwndSeg)
            tme.set(ValueLayout.JAVA_INT, 16L, 0)
            TrackMouseEvent(tme)
        } catch (_: Throwable) {
        }
    }

    /**
     * Handles a WM_TOUCH message: reads every contact via GetTouchInputInfo,
     * emits pointer events per contact, then releases the touch handle.
     *
     * On macOS/Linux (getTouchInputInfo null) this is a no-op — no event is
     * emitted and the message is silently consumed.
     *
     * @param hwnd   Integer address of the source HWND.
     * @param wParam WM_TOUCH wParam — LOWORD = number of contacts (cInputs).
     * @param lParam WM_TOUCH lParam — HTOUCHINPUT handle.
     */
    private fun handleTouch(hwnd: Long, wParam: Long, lParam: Long) {
        val cInputs = (wParam and 0xFFFF).toInt()
        if (cInputs <= 0) return

        try {
            val hTouchInput = MemorySegment.ofAddress(lParam)
            val arena = java.lang.foreign.Arena.ofConfined()
            arena.use {
                val buffer = arena.allocate(cInputs.toLong() * TOUCHINPUT_SIZE, 8L)
                val ok = GetTouchInputInfo(
                    hTouchInput,
                    cInputs,
                    buffer,
                    TOUCHINPUT_SIZE,
                )
                if (ok != 0) {
                    for (i in 0 until cInputs) {
                        decodeTouchInput(hwnd, buffer, i).forEach { event -> emit(hwnd, event) }
                    }
                }
            }
            CloseTouchInputHandle(hTouchInput)
        } catch (_: Throwable) {
        }
    }

    /**
     * Decodes the TOUCHINPUT at [index] in a buffer of contiguous TOUCHINPUT
     * structures into one or more pointer events.
     *
     * Pure function (no native call) so it can be unit-tested with a synthetic
     * buffer on any platform.
     *
     * Coordinates: TOUCHINPUT.x / .y are physical **screen** coordinates in
     * hundredths of a pixel. They are converted to client coordinates with
     * ScreenToClient when an HWND is available.
     *
     * Phase: TOUCHEVENTF_DOWN → [TouchPhase.Started], TOUCHEVENTF_UP →
     * [TouchPhase.Ended], otherwise (TOUCHEVENTF_MOVE) → [TouchPhase.Moved].
     * Windows touch has no dedicated "cancelled" flag, so [TouchPhase.Cancelled]
     * is never produced here.
     *
     * @param buffer Native (or heap) segment holding one or more TOUCHINPUT structs.
     * @param index  Zero-based index of the contact to decode.
     */
    internal fun decodeTouchInput(buffer: MemorySegment, index: Int): List<WindowEvent> {
        return decodeTouchInput(hwnd = 0L, buffer = buffer, index = index, screenToClient = null)
    }

    /**
     * Decodes a TOUCHINPUT using the source [hwnd] to convert Win32 screen
     * coordinates into kadre client coordinates.
     */
    internal fun decodeTouchInput(hwnd: Long, buffer: MemorySegment, index: Int): List<WindowEvent> {
        return decodeTouchInput(hwnd, buffer, index, ::screenToClientTouchPoint)
    }

    /**
     * Testable TOUCHINPUT decoder. [screenToClient] receives the integer screen
     * pixel point required by Win32; the fractional hundredths are preserved and
     * re-applied after conversion.
     */
    internal fun decodeTouchInput(
        hwnd: Long,
        buffer: MemorySegment,
        index: Int,
        screenToClient: ((hwnd: Long, screenX: Int, screenY: Int) -> PhysicalPosition<Int>?)?,
    ): List<WindowEvent> {
        val base = index.toLong() * TOUCHINPUT_SIZE
        val rawX = buffer.get(ValueLayout.JAVA_INT, base + TOUCHINPUT_OFFSET_X)
        val rawY = buffer.get(ValueLayout.JAVA_INT, base + TOUCHINPUT_OFFSET_Y)
        // dwID is a DWORD (unsigned 32-bit); widen without sign extension.
        val id = buffer.get(ValueLayout.JAVA_INT, base + TOUCHINPUT_OFFSET_ID).toLong() and 0xFFFF_FFFFL
        val flags = buffer.get(ValueLayout.JAVA_INT, base + TOUCHINPUT_OFFSET_FLAGS)

        val phase = when {
            flags and TOUCHEVENTF_DOWN != 0 -> TouchPhase.Started
            flags and TOUCHEVENTF_UP != 0   -> TouchPhase.Ended
            else                            -> TouchPhase.Moved
        }

        val location = touchClientPosition(hwnd, rawX, rawY, screenToClient)
        val fingerId = FingerId(id)
        return when (phase) {
            TouchPhase.Started -> listOf(
                WindowEvent.PointerEntered(null, location, primary = true, kind = PointerKind.Touch),
                WindowEvent.PointerButton(null, KeyState.Pressed, location, primary = true, ButtonSource.Touch(fingerId)),
            )
            TouchPhase.Moved -> listOf(
                WindowEvent.PointerMoved(null, location, primary = true, source = PointerSource.Touch(fingerId)),
            )
            TouchPhase.Ended -> listOf(
                WindowEvent.PointerButton(null, KeyState.Released, location, primary = true, ButtonSource.Touch(fingerId)),
                WindowEvent.PointerLeft(null, location, primary = true, kind = PointerKind.Touch),
            )
            TouchPhase.Cancelled -> listOf(
                WindowEvent.PointerLeft(null, location, primary = true, kind = PointerKind.Touch),
            )
        }
    }

    private fun touchClientPosition(
        hwnd: Long,
        rawX: Int,
        rawY: Int,
        screenToClient: ((hwnd: Long, screenX: Int, screenY: Int) -> PhysicalPosition<Int>?)?,
    ): PhysicalPosition<Double> {
        val scale = TOUCH_COORD_SCALE.toInt()
        // Match Win32 TOUCH_COORD_TO_PIXEL semantics: integer division truncates toward zero.
        val screenX = rawX / scale
        val screenY = rawY / scale
        val fractionX = rawX - screenX * scale
        val fractionY = rawY - screenY * scale
        val client = screenToClient?.invoke(hwnd, screenX, screenY)
        return if (client != null) {
            PhysicalPosition(
                client.x + fractionX.toDouble() / TOUCH_COORD_SCALE,
                client.y + fractionY.toDouble() / TOUCH_COORD_SCALE,
            )
        } else {
            PhysicalPosition(
                rawX.toDouble() / TOUCH_COORD_SCALE,
                rawY.toDouble() / TOUCH_COORD_SCALE,
            )
        }
    }

    private fun screenToClientTouchPoint(hwnd: Long, screenX: Int, screenY: Int): PhysicalPosition<Int>? {
        return try {
            java.lang.foreign.Arena.ofConfined().use { arena ->
                val point = arena.allocate(8L, 4L)
                point.set(ValueLayout.JAVA_INT, 0L, screenX)
                point.set(ValueLayout.JAVA_INT, 4L, screenY)
                val ok = ScreenToClient(MemorySegment.ofAddress(hwnd), point)
                if (ok == 0) return@use null
                PhysicalPosition(
                    point.get(ValueLayout.JAVA_INT, 0L),
                    point.get(ValueLayout.JAVA_INT, 4L),
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

    // ── IME helpers ───────────────────────────────────────────────────────────

    /**
     * Handles a WM_IME_COMPOSITION message: reads the composition and/or result
     * strings from the IME context and emits [WindowEvent.Ime] events.
     *
     * @param hwnd   Integer address of the source HWND.
     * @param lParam WM_IME_COMPOSITION lParam — GCS_* flags indicating which
     *               information has changed.
     */
    private fun handleImeComposition(hwnd: Long, lParam: Long) {
        val getCtx = immGetContext ?: return
        val relCtx = immReleaseContext ?: return
        val hwndSeg = MemorySegment.ofAddress(hwnd)
        val himc: MemorySegment = try {
            getCtx.invokeExact(hwndSeg) as MemorySegment
        } catch (_: Throwable) { MemorySegment.NULL }
        if (himc == MemorySegment.NULL) return
        try {
            val gcsFlags = lParam.toInt()
            if (gcsFlags and GCS_RESULTSTR != 0) {
                readImeString(himc, GCS_RESULTSTR)?.let { text ->
                    emit(hwnd, WindowEvent.Ime(WindowEvent.Ime.ImeEvent.Commit(text)))
                }
            }
            if (gcsFlags and GCS_COMPSTR != 0) {
                readImeString(himc, GCS_COMPSTR)?.let { text ->
                    val cursorPos = readImeCursor(himc)
                    val range = if (cursorPos != null) Pair(cursorPos, cursorPos) else null
                    emit(hwnd, WindowEvent.Ime(WindowEvent.Ime.ImeEvent.Preedit(text, range)))
                }
            }
        } catch (_: Throwable) {
            // IME composition handling is best-effort
        } finally {
            try { relCtx.invokeExact(hwndSeg, himc) as Int } catch (_: Throwable) {}
        }
    }

    /**
     * Reads an IME composition string (UTF-16) from the given HIMC for the
     * specified [gcsMode] (GCS_COMPSTR or GCS_RESULTSTR).
     *
     * Returns null on error, empty string if the string is empty.
     */
    private fun readImeString(himc: MemorySegment, gcsMode: Int): String? {
        val query = immGetCompositionStringW ?: return null
        val size: Int = try {
            query.invokeExact(himc, gcsMode, MemorySegment.NULL, 0) as Int
        } catch (_: Throwable) { return null }
        if (size < 0) return null
        if (size == 0) return ""
        return try {
            java.lang.foreign.Arena.ofConfined().use { arena ->
                val buf = arena.allocate(size.toLong(), 1L)
                query.invokeExact(himc, gcsMode, buf, size) as Int
                val chars = CharArray(size / 2) { i ->
                    buf.getAtIndex(ValueLayout.JAVA_SHORT, i.toLong()).toInt().toChar()
                }
                String(chars)
            }
        } catch (_: Throwable) { null }
    }

    /**
     * Reads the IME cursor position (character index within the composition
     * string) via GCS_CURSORPOS.
     *
     * Returns null if not available or on error.
     */
    private fun readImeCursor(himc: MemorySegment): Int? {
        val query = immGetCompositionStringW ?: return null
        return try {
            val cursor = query.invokeExact(himc, GCS_CURSORPOS, MemorySegment.NULL, 0) as Int
            if (cursor >= 0) cursor else null
        } catch (_: Throwable) { null }
    }

    private fun pointerButton(button: MouseButton, state: KeyState, lParam: Long): WindowEvent.PointerButton =
        WindowEvent.PointerButton(
            deviceId = null,
            state = state,
            position = mousePosition(lParam),
            primary = true,
            button = ButtonSource.Mouse(button),
        )

    private fun mousePosition(lParam: Long): PhysicalPosition<Double> =
        PhysicalPosition(
            x = (lParam and 0xFFFF).toShort().toDouble(),
            y = ((lParam ushr 16) and 0xFFFF).toShort().toDouble(),
        )

    /**
     * Handles a WM_GESTURE message: reads the GESTUREINFO via GetGestureInfo,
     * dispatches the gesture to the appropriate kadre [WindowEvent], and
     * releases the gesture handle via CloseGestureInfoHandle.
     *
     * Supported gestures:
     *  - GID_ZOOM        → [WindowEvent.PinchGesture]
     *  - GID_PAN         → [WindowEvent.PanGesture]
     *  - GID_ROTATE      → [WindowEvent.RotationGesture]
     *  - GID_TWOFINGERTAP → [WindowEvent.DoubleTapGesture]
     *
     * No-op if user32 gesture API is unavailable (macOS/Linux).
     *
     * @param hwnd   Integer address of the source HWND.
     * @param lParam WM_GESTURE lParam — HGESTUREINFO handle.
     */
    private fun handleGesture(hwnd: Long, lParam: Long) {
        if (lParam == 0L) return

        try {
            val hGestureInfo = MemorySegment.ofAddress(lParam)
            java.lang.foreign.Arena.ofConfined().use { arena ->
                val info = arena.allocate(GESTUREINFO_SIZE.toLong(), 8L)
                info.set(ValueLayout.JAVA_INT, 0L, GESTUREINFO_SIZE)

                val ok = GetGestureInfo(hGestureInfo, info)
                if (ok == 0) return@use

                val dwFlags = info.get(ValueLayout.JAVA_INT, GESTUREINFO_OFFSET_FLAGS)
                val dwCommand = info.get(ValueLayout.JAVA_INT, GESTUREINFO_OFFSET_COMMAND)
                val ullArguments = info.get(ValueLayout.JAVA_LONG, GESTUREINFO_OFFSET_ARGUMENTS)

                val phase: TouchPhase = when {
                    dwFlags and GF_BEGIN != 0 && dwFlags and GF_END == 0 -> TouchPhase.Started
                    dwFlags and GF_END != 0 -> TouchPhase.Ended
                    else -> TouchPhase.Moved
                }

                when (dwCommand) {
                    GID_ZOOM -> {
                        val zoomVal = (ullArguments and 0xFFFF_FFFFL).toInt()
                        val delta = (zoomVal.toDouble() / 100.0) - 1.0
                        emit(hwnd, WindowEvent.PinchGesture(
                            deviceId = null,
                            delta = delta,
                            phase = phase,
                        ))
                    }

                    GID_PAN -> {
                        val raw = ullArguments.toInt()
                        val deltaX = (raw and 0xFFFF).toShort().toFloat()
                        val deltaY = ((raw ushr 16) and 0xFFFF).toShort().toFloat()
                        emit(hwnd, WindowEvent.PanGesture(
                            deviceId = null,
                            delta = PhysicalPosition(deltaX, deltaY),
                            phase = phase,
                        ))
                    }

                    GID_ROTATE -> {
                        val angleVal = (ullArguments and 0xFFFF_FFFFL).toInt()
                        val radians = angleVal.toDouble() / 100.0
                        val deltaDegrees = (radians * (180.0 / Math.PI)).toFloat()
                        emit(hwnd, WindowEvent.RotationGesture(
                            deviceId = null,
                            deltaDegrees = deltaDegrees,
                            phase = phase,
                        ))
                    }

                    GID_TWOFINGERTAP -> {
                        emit(hwnd, WindowEvent.DoubleTapGesture(deviceId = null))
                    }
                }
            }
        } catch (_: Throwable) {
        } finally {
            try {
                val hGestureInfo = MemorySegment.ofAddress(lParam)
                CloseGestureInfoHandle(hGestureInfo)
            } catch (_: Throwable) {}
        }
    }

    /**
     * Handles a WM_DROPFILES message.
     *
     * Reads the dropped file paths and position from the HDROP handle,
     * then emits [WindowEvent.DragDropped]. The HDROP handle is freed
     * via DragFinish before returning.
     *
     * No-op if shell32.dll bindings are unavailable (macOS/Linux).
     */
    private fun handleFileDrop(hwnd: Long, wParam: Long) {
        val dragQueryFile = dragQueryFileW ?: return
        val dragQueryPt = dragQueryPoint ?: return
        val dragFin = dragFinish ?: return
        val hDrop = MemorySegment.ofAddress(wParam)
        try {
            java.lang.foreign.Arena.ofConfined().use { arena ->
                // Get drop point in client coordinates
                val pt = arena.allocate(POINT_SIZE, POINT_ALIGN)
                dragQueryPt.invokeExact(hDrop, pt) as Int
                val x = pt.get(ValueLayout.JAVA_INT, POINT_OFFSET_X)
                val y = pt.get(ValueLayout.JAVA_INT, POINT_OFFSET_Y)

                // Count files (iFile = 0xFFFFFFFF = -1 as signed int)
                val fileCount = dragQueryFile.invokeExact(hDrop, -1, MemorySegment.NULL, 0) as Int

                // Read each file path
                val files = mutableListOf<String>()
                for (i in 0 until fileCount) {
                    val charCount = dragQueryFile.invokeExact(hDrop, i, MemorySegment.NULL, 0) as Int
                    val buffer = arena.allocate((charCount + 1) * 2L, 2L)
                    dragQueryFile.invokeExact(hDrop, i, buffer, charCount + 1) as Int
                    val chars = CharArray(charCount) { j ->
                        buffer.getAtIndex(ValueLayout.JAVA_SHORT, j.toLong()).toInt().toChar()
                    }
                    files.add(String(chars))
                }

                dragFin.invokeExact(hDrop)
                emit(hwnd, WindowEvent.DragDropped(PhysicalPosition(x.toDouble(), y.toDouble()), files))
            }
        } catch (_: Throwable) {
            try { dragFin.invokeExact(hDrop) } catch (_: Throwable) {}
        }
    }
}
