/**
 * X11 implementation of [ActiveEventLoop] and the [runApp] entry point.
 *
 * [X11EventLoop] implements [ActiveEventLoop] and is passed to each
 * [ApplicationHandler] callback. The [runApp] function orchestrates
 * the X11 initialization (XOpenDisplay) and the event loop
 * with dynamic switching according to [ControlFlow]:
 *
 * All [ControlFlow] modes use the same POSIX poll pump over the X connection
 * descriptor and a shared wake descriptor.
 *
 * Lazy FFM pattern (tryCreate): all MethodHandles are null on macOS/Windows,
 * which lets the build pass on all platforms.
 *
 * X11EventLoop — X11 event loop with ControlFlow switching.
 */
package org.graphiks.kadre.x11

import org.graphiks.kadre.x11.binding.*
import org.graphiks.kadre.ffi.posix.PollFd
import org.graphiks.kadre.ffi.posix.PosixException
import org.graphiks.kadre.ffi.posix.PosixWakeup
import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ButtonSource
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.CursorImage
import org.graphiks.kadre.core.CustomCursor
import org.graphiks.kadre.core.DeviceEvents
import org.graphiks.kadre.core.EventLoopProxy
import org.graphiks.kadre.core.KeyCode
import org.graphiks.kadre.core.OwnedDisplayHandle
import org.graphiks.kadre.core.RawDisplayHandle
import org.graphiks.kadre.core.KeyEvent
import org.graphiks.kadre.core.KeyPlatform
import org.graphiks.kadre.core.KeyState
import org.graphiks.kadre.core.KeyboardModifierState
import org.graphiks.kadre.core.KeyboardModifiers
import org.graphiks.kadre.core.LogicalKey
import org.graphiks.kadre.core.MonitorHandle
import org.graphiks.kadre.core.MouseButton
import org.graphiks.kadre.core.NativeKeyInfo
import org.graphiks.kadre.core.NativeKeyCode
import org.graphiks.kadre.core.NativeLogicalKey
import org.graphiks.kadre.core.PhysicalKey
import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.core.PointerKind
import org.graphiks.kadre.core.PointerSource
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.TouchPhase
import org.graphiks.kadre.core.Theme
import org.graphiks.kadre.core.Window
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import org.graphiks.kadre.core.defaultLogicalKey
import org.graphiks.kadre.core.defaultText
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

// ── XEvent constants ──────────────────────────────────────────────────────────

/** Size of XEvent in bytes on LP64 systems (`long pad[24]` = 192 bytes). */
private const val XEVENT_SIZE: Long = 192L

/** Alignment of XEvent (8 bytes for 64-bit pointers). */
private const val XEVENT_ALIGN: Long = 8L

// Offsets in XEvent for Xlib LP64 structs.
private const val XEVENT_TYPE_OFFSET: Long = 0L     // int type
internal const val XANY_WINDOW_OFFSET: Long = 32L   // XAnyEvent.window

// XKeyEvent offsets (type=KeyPress or KeyRelease)
private const val XKEY_STATE_OFFSET: Long = 80L     // unsigned int state (modifiers)
private const val XKEY_KEYCODE_OFFSET: Long = 84L   // unsigned int keycode

// XButtonEvent offsets (type=ButtonPress or ButtonRelease)
private const val XBUTTON_X_OFFSET: Long = 64L      // int x
private const val XBUTTON_Y_OFFSET: Long = 68L      // int y
private const val XBUTTON_BUTTON_OFFSET: Long = 84L // unsigned int button

// XMotionEvent offsets (type=MotionNotify)
private const val XMOTION_X_OFFSET: Long = 64L      // int x
private const val XMOTION_Y_OFFSET: Long = 68L      // int y

// XConfigureEvent offsets (type=ConfigureNotify)
private const val XCONFIGURE_SEND_EVENT_OFFSET: Long = 16L // Bool send_event
private const val XCONFIGURE_WINDOW_OFFSET: Long = 40L  // Window window
private const val XCONFIGURE_X_OFFSET: Long = 48L       // int x
private const val XCONFIGURE_Y_OFFSET: Long = 52L       // int y
private const val XCONFIGURE_WIDTH_OFFSET: Long = 56L   // int width
private const val XCONFIGURE_HEIGHT_OFFSET: Long = 60L  // int height

// XDestroyWindowEvent offsets (type=DestroyNotify)
private const val XDESTROY_WINDOW_OFFSET: Long = 40L // Window window

// XVisibilityEvent offsets (type=VisibilityNotify)
private const val XVISIBILITY_STATE_OFFSET: Long = 40L // int state

// X11 modifiers
private const val X11_SHIFT_MASK: Int = 0x0001
private const val X11_CONTROL_MASK: Int = 0x0004
private const val X11_MOD1_MASK: Int = 0x0008  // Alt
private const val X11_MOD4_MASK: Int = 0x0040  // Super / Meta
private const val X11_KEYMAP_SIZE: Long = 32L
private const val X11_KEYMAP_BITS: Int = 256

private const val X11_VISIBILITY_FULLY_OBSCURED: Int = 2

// X11 buttons
private const val X11_BUTTON1: Int = 1
private const val X11_BUTTON2: Int = 2
private const val X11_BUTTON3: Int = 3
private const val X11_BUTTON4: Int = 4  // scroll up
private const val X11_BUTTON5: Int = 5  // scroll down

// Keysym → Key mapping (common keysyms)
// Reference: /usr/include/X11/keysymdef.h
private const val XK_BackSpace: Int = 0xFF08
private const val XK_Tab: Int = 0xFF09
private const val XK_Return: Int = 0xFF0D
private const val XK_Escape: Int = 0xFF1B
private const val XK_space: Int = 0x0020
private const val XK_F1: Int = 0xFFBE
private const val XK_F12: Int = 0xFFC9
private const val XK_Left: Int = 0xFF51
private const val XK_Up: Int = 0xFF52
private const val XK_Right: Int = 0xFF53
private const val XK_Down: Int = 0xFF54
private const val XK_Shift_L: Int = 0xFFE1
private const val XK_Shift_R: Int = 0xFFE2
private const val XK_Control_L: Int = 0xFFE3
private const val XK_Control_R: Int = 0xFFE4
private const val XK_Alt_L: Int = 0xFFE9
private const val XK_Alt_R: Int = 0xFFEA
private const val XK_Meta_L: Int = 0xFFE7
private const val XK_Meta_R: Int = 0xFFE8
private const val XK_Super_L: Int = 0xFFEB
private const val XK_Super_R: Int = 0xFFEC

// ── Single-instance lock ──────────────────────────────────────────────────────

/**
 * Global lock guaranteeing that only a single X11 event loop is active
 * at a time in the process.
 */
internal val x11Running = AtomicBoolean(false)

/** Native window/display boundary used by the loop and deterministic contract tests. */
internal interface X11NativeAdapter {
    fun createWindow(loop: X11EventLoop, attributes: WindowAttributes): X11Window?
    fun destroyWindow(displayPtr: Long, windowId: Long)
    fun flush(displayPtr: Long)
    fun closeDisplay(displayPtr: Long)
}

private object NativeX11Adapter : X11NativeAdapter {
    override fun createWindow(loop: X11EventLoop, attributes: WindowAttributes): X11Window? =
        X11Window.create(loop.displayPtr, loop.screen, attributes, owner = loop)

    override fun destroyWindow(displayPtr: Long, windowId: Long) {
        val destroy = xDestroyWindow ?: error("XDestroyWindow is unavailable")
        destroy.invokeExact(MemorySegment.ofAddress(displayPtr), windowId) as Int
    }

    override fun flush(displayPtr: Long) {
        val flush = xFlush ?: error("XFlush is unavailable")
        flush.invokeExact(MemorySegment.ofAddress(displayPtr)) as Int
    }

    override fun closeDisplay(displayPtr: Long) {
        xCloseDisplay?.invokeExact(MemorySegment.ofAddress(displayPtr)) as? Int
    }
}

// ── X11EventLoop ──────────────────────────────────────────────────────────────

/**
 * Internal implementation of [ActiveEventLoop] for the X11 platform (Linux).
 *
 * An instance is created by a call to [runApp] and passed as the receiver
 * to all [ApplicationHandler] callbacks.
 *
 * ### Lifecycle
 * ```
 * runApp(handler)
 *   └─ handler.resumed(this)
 *   └─ handler.newEvents(this, Init)
 *   └─ handler.canCreateSurfaces(this)
 *   └─ handler.aboutToWait(this)
 *   └─ event loop
 *        ├─ pump native events according to ControlFlow
 *        ├─ handler.newEvents(this, cause)
 *        ├─ dispatch queued window events
 *        └─ handler.aboutToWait(this)
 *   └─ handler.destroySurfaces(this)
 *   └─ close all windows
 *   └─ handler.suspended(this)
 *   └─ close display
 * ```
 *
 * ### Thread-safety
 * - [_controlFlow] is @Volatile: readable from any thread.
 * - [_isExiting] is @Volatile: readable from any thread.
 * - [windows] is a ConcurrentHashMap.
 * - The event loop itself runs in the calling thread.
 */
class X11EventLoop internal constructor(
    internal val displayPtr: Long,
    internal val screen: Int,
    internal val wakeup: PosixWakeup,
    internal val nativeAdapter: X11NativeAdapter = NativeX11Adapter,
) : ActiveEventLoop {

    private val loopThread = Thread.currentThread()
    internal val keyboardModifierTracker = X11KeyboardModifierTracker()

    /**
     * Xdnd drag source window per target window.
     * Cleared when drag leaves or finishes.
     */
    internal val dragSourceWindows = ConcurrentHashMap<Long, Long>()

    /**
     * Whether an Xdnd drop data request (XConvertSelection) is pending.
     * Key: target window XID, Value: drop position.
     */
    internal val pendingDropRequests = ConcurrentHashMap<Long, PhysicalPosition<Double>>()

    /** Queue of pending Xdnd drops awaiting selection data. */
    internal val pendingXdndDrops = ConcurrentLinkedQueue<PendingXdndDrop>()

    private val windowLifecycle = X11WindowLifecycle(
        loop = this,
        displayPtr = displayPtr,
        wakeup = wakeup,
        nativeAdapter = nativeAdapter,
        checkLoopThread = ::checkLoopThread,
        detachAuxiliaryState = { windowId ->
            dragSourceWindows.remove(windowId)
            pendingDropRequests.remove(windowId)
            pendingXdndDrops.removeAll { it.targetWindow == windowId }
        },
    )

    /** Live windows: windowId (XID) → X11Window. */
    internal val windows: ConcurrentHashMap<Long, X11Window>
        get() = windowLifecycle.windows

    internal var onCloseAdmissionBlockedForTest: (() -> Unit)?
        get() = windowLifecycle.onCloseAdmissionBlockedForTest
        set(value) {
            windowLifecycle.onCloseAdmissionBlockedForTest = value
        }

    @Volatile
    private var _isExiting = false

    override val isExiting: Boolean
        get() = _isExiting

    @Volatile
    private var _controlFlow: ControlFlow = ControlFlow.Wait

    override val controlFlow: ControlFlow
        get() = _controlFlow

    override fun setControlFlow(controlFlow: ControlFlow) {
        _controlFlow = controlFlow
    }

    /**
     * Creates a new native X11 window and registers it in the window table.
     *
     * @param attributes Window configuration attributes.
     * @return The created window.
     * @throws IllegalStateException if the libX11 bindings are not available.
     */
    override fun createWindow(attributes: WindowAttributes): Window {
        checkLoopThread()
        val window = nativeAdapter.createWindow(this, attributes)
            ?: error(
                "X11Window.create() returned null — libX11.so.6 bindings are not available on this platform."
            )
        return windowLifecycle.register(window)
    }

    /**
     * Creates a window with X11-specific attributes.
     *
     * Merges [X11WindowAttributes] fields into the core [WindowAttributes]
     * and applies platform-specific settings at creation time.
     */
    fun createWindow(attrs: X11WindowAttributes): Window {
        checkLoopThread()
        val window = nativeAdapter.createWindow(this, attrs.core)
            ?: error(
                "X11Window.create() returned null — libX11.so.6 bindings are not available on this platform."
            )
        windowLifecycle.register(window)
        // Apply platform extension settings
        if (attrs.windowType != null) window.setWindowType(attrs.windowType)
        if (attrs.overrideRedirect) window.setOverrideRedirect(true)
        return window
    }

    internal fun requestRedraw(window: X11Window): Boolean = windowLifecycle.requestRedraw(window)

    internal fun enqueueExpose(windowId: WindowId): Boolean = windowLifecycle.enqueueExpose(windowId)

    internal fun enqueueWindowEvent(windowId: WindowId, event: WindowEvent): Boolean =
        windowLifecycle.enqueueWindowEvent(windowId, event)

    internal fun closeWindow(window: X11Window): Boolean = windowLifecycle.closeWindow(window)

    internal fun nativeWindowDestroyed(windowId: WindowId): Boolean =
        windowLifecycle.nativeWindowDestroyed(windowId)

    internal fun drainOpenWindowEvents(handler: ApplicationHandler) = windowLifecycle.drain(handler)

    internal fun hasPendingWork(): Boolean = windowLifecycle.hasPendingWork()

    internal fun closeAllWindowsDirect() = windowLifecycle.closeAllWindowsDirect()

    private fun checkLoopThread() {
        check(Thread.currentThread() === loopThread) {
            "X11 native work must run on the display-owning event-loop thread"
        }
    }

    /**
     * Requests the X11 event loop to stop.
     */
    override fun exit() {
        _isExiting = true
    }

    /**
     * Creates a thread-safe proxy to this event loop.
     */
    override fun createProxy(): EventLoopProxy =
        X11EventLoopProxy(wakeup)

    /**
     * Returns a persistent X11 display handle usable independently from a window.
     */
    override fun ownedDisplayHandle(): OwnedDisplayHandle? {
        return OwnedDisplayHandle(RawDisplayHandle.Xlib(display = displayPtr))
    }

    // ── R2: monitor enumeration ───────────────────────────────────────────────

    /**
     * Returns all monitors for this X11 display.
     *
     * Tries RANDR, then Xinerama, then a synthetic single-monitor fallback.
     */
    override fun availableMonitors(): List<MonitorHandle> {
        val scale = windows.values.firstOrNull()?.scaleFactor ?: 1.0
        return enumerateX11Monitors(displayPtr, screen, scale)
    }

    /**
     * Returns the XRandR primary monitor, or the first/synthetic fallback.
     */
    override fun primaryMonitor(): MonitorHandle? {
        val scale = windows.values.firstOrNull()?.scaleFactor ?: 1.0
        return primaryX11Monitor(displayPtr, screen, scale)
    }

    // ── R3: system theme ──────────────────────────────────────────────────────

    /**
     * X11 has no standard theme API.
     *
     * The xsettings daemon exposes `Net/ThemeName` but there is no standardised
     * Light/Dark distinction. Documented null.
     *
     * **ThemeChanged not emitted on X11** — there is no standard protocol for
     * system theme change notifications. The xsettings manager could be polled,
     * but polling introduces overhead for a low-value signal.
     *
     * TODO(R3-x11-theme): query xsettings or GTK_THEME env variable.
     */
    override fun systemTheme(): Theme? = null

    // ── R6: gestures ──────────────────────────────────────────────────────────

    /**
     * Gesture events ([WindowEvent.PinchGesture], [WindowEvent.PanGesture],
     * [WindowEvent.RotationGesture], [WindowEvent.DoubleTapGesture]) are
     * **not emitted on X11**.
     *
     * X11 has no standard gesture protocol. Ctrl+scroll is dispatched as
     * [WindowEvent.PinchGesture] as a convenient software fallback (see
     * [ButtonPress] handling in [dispatchEvent]), but proper multi-touch
     * gesture recognition requires hardware-specific extensions (Xi2 touch
     * events) that are not universally available on X11 servers.
     */

    // ── R4: device event filter ───────────────────────────────────────────────

    /**
     * No-op on X11: device events are always dispatched.
     *
     * TODO(R4-x11-device-filter): use XSelectInput to selectively disable raw motion.
     */
    override fun listenDeviceEvents(mode: DeviceEvents) {
        // no-op on X11
    }

    // ── R5-CustomCursor ─────────────────────────────────────────────────────────

    /**
     * Creates a monochrome custom cursor from RGBA pixel data on X11.
     *
     * Computes 1-bit source/mask bitmaps (source = luminance>128, mask = alpha>0),
     * then calls XCreateBitmapFromData + XCreatePixmapCursor with white foreground
     * and black background. Both pixmaps are freed after cursor creation.
     *
     * This is a monochrome fallback — no XRender or ARGB cursor support yet.
     * Returns null on failure (missing symbols, invalid image, or OOM).
     */
    override fun createCustomCursor(image: CursorImage): CustomCursor? =
        withValidX11CursorGeometry(image) { createValidatedCustomCursor(image) }

    private fun createValidatedCustomCursor(image: CursorImage): CustomCursor? {
        val createBitmap = xCreateBitmapFromData ?: return null
        val createPixmapCursor = xCreatePixmapCursor ?: return null
        val freePixmap = xFreePixmap ?: return null
        val freeCursor = xFreeCursor ?: return null
        val rootHandle = xRootWindow ?: return null

        val display = MemorySegment.ofAddress(displayPtr)

        return try {
            val root = rootHandle.invokeExact(display, screen) as Long
            if (root == 0L) return null

            var maxWidth = X11_CURSOR_DIMENSION_LIMIT
            var maxHeight = X11_CURSOR_DIMENSION_LIMIT
            val queryBestCursor = xQueryBestCursor
            if (queryBestCursor != null) {
                Arena.ofConfined().use { arena ->
                    val widthReturn = arena.allocate(ValueLayout.JAVA_INT)
                    val heightReturn = arena.allocate(ValueLayout.JAVA_INT)
                    val status = queryBestCursor.invokeExact(
                        display,
                        root,
                        image.width,
                        image.height,
                        widthReturn,
                        heightReturn,
                    ) as Int
                    if (status == 0) return null
                    maxWidth = capServerCursorLimit(widthReturn.get(ValueLayout.JAVA_INT, 0L).toUInt())
                    maxHeight = capServerCursorLimit(heightReturn.get(ValueLayout.JAVA_INT, 0L).toUInt())
                }
            }
            if (!validateCursorGeometry(image, maxWidth, maxHeight)) return null
            val packed = packMonochromeCursor(image)

            Arena.ofConfined().use { arena ->
                val srcData = arena.allocate(packed.source.size.toLong(), 1L)
                val maskData = arena.allocate(packed.mask.size.toLong(), 1L)
                srcData.copyFrom(MemorySegment.ofArray(packed.source))
                maskData.copyFrom(MemorySegment.ofArray(packed.mask))

                val source = createBitmap.invokeExact(display, root, srcData, image.width, image.height) as Long
                if (source == 0L) return@use null
                var mask = 0L
                try {
                    mask = createBitmap.invokeExact(display, root, maskData, image.width, image.height) as Long
                    if (mask == 0L) return@use null

                    val foreground = arena.allocate(X11_COLOR_SIZE_BYTES, X11_COLOR_ALIGN_BYTES)
                    val background = arena.allocate(X11_COLOR_SIZE_BYTES, X11_COLOR_ALIGN_BYTES)
                    writeXColor(foreground, UShort.MAX_VALUE, UShort.MAX_VALUE, UShort.MAX_VALUE)
                    writeXColor(background, 0u, 0u, 0u)
                    val cursor = createPixmapCursor.invokeExact(
                        display, source, mask, foreground, background,
                        image.hotspotX, image.hotspotY,
                    ) as Long
                    if (cursor == 0L) return@use null
                    wrapOwnedX11Cursor(
                        cursor = cursor,
                        wrap = { ownedCursor -> CustomCursor(id = ownedCursor) },
                        free = { ownedCursor ->
                            freeCursor.invokeExact(display, ownedCursor) as Int
                        },
                    )
                } finally {
                    if (mask != 0L) {
                        try {
                            freePixmap.invokeExact(display, mask) as Int
                        } catch (_: Throwable) {}
                    }
                    try {
                        freePixmap.invokeExact(display, source) as Int
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) { null }
    }
}

/**
 * Pending Xdnd drop state used when waiting for SelectionNotify.
 */
internal data class PendingXdndDrop(
    val targetWindow: Long,
    val sourceWindow: Long,
    val position: PhysicalPosition<Double>,
)

/**
 * Xdnd atoms interned at event loop start.
 */
internal data class XdndAtoms(
    val xdndEnter: Long,
    val xdndPosition: Long,
    val xdndLeave: Long,
    val xdndDrop: Long,
    val xdndSelection: Long,
    val textUriList: Long,
)

// ── Dispatch X11 events ───────────────────────────────────────────────────────

/**
 * Translates an X11 keysym into a kadre [KeyCode].
 *
 * X11 keysym = symbolic key code (defined in keysymdef.h).
 * For letters a-z and digits 0-9, the keysyms are identical to the ASCII codes.
 *
 * @param keysym X11 keysym (INT in XKeyEvent.keycode, translated via XLookupKeysym).
 * @return The corresponding kadre key code, or null if unrecognized.
 */
internal fun keysymToKeyCode(keysym: Int): KeyCode? = when (keysym) {
    // Letters a-z (keysyms = lowercase ASCII codes 0x61–0x7A)
    in 0x61..0x7A -> KeyCode.entries[KeyCode.KeyA.ordinal + (keysym - 0x61)]

    // Letters A-Z (keysyms = uppercase ASCII codes 0x41–0x5A)
    in 0x41..0x5A -> KeyCode.entries[KeyCode.KeyA.ordinal + (keysym - 0x41)]

    // Digits 0-9 (keysyms = ASCII codes 0x30–0x39)
    0x30 -> KeyCode.Digit0
    0x31 -> KeyCode.Digit1
    0x32 -> KeyCode.Digit2
    0x33 -> KeyCode.Digit3
    0x34 -> KeyCode.Digit4
    0x35 -> KeyCode.Digit5
    0x36 -> KeyCode.Digit6
    0x37 -> KeyCode.Digit7
    0x38 -> KeyCode.Digit8
    0x39 -> KeyCode.Digit9

    // Function keys F1-F12
    in XK_F1..XK_F12 -> KeyCode.entries[KeyCode.F1.ordinal + (keysym - XK_F1)]

    // Navigation keys
    XK_Left  -> KeyCode.ArrowLeft
    XK_Right -> KeyCode.ArrowRight
    XK_Up    -> KeyCode.ArrowUp
    XK_Down  -> KeyCode.ArrowDown

    // Special keys
    XK_space     -> KeyCode.Space
    XK_Return    -> KeyCode.Enter
    XK_Escape    -> KeyCode.Escape
    XK_BackSpace -> KeyCode.Backspace
    XK_Tab       -> KeyCode.Tab

    // Modifiers
    XK_Shift_L   -> KeyCode.ShiftLeft
    XK_Shift_R   -> KeyCode.ShiftRight
    XK_Control_L -> KeyCode.ControlLeft
    XK_Control_R -> KeyCode.ControlRight
    XK_Alt_L     -> KeyCode.AltLeft
    XK_Alt_R     -> KeyCode.AltRight
    XK_Meta_L,
    XK_Super_L   -> KeyCode.MetaLeft
    XK_Meta_R,
    XK_Super_R   -> KeyCode.MetaRight

    else -> null
}

/**
 * Translates an X11 state field (modifiers) into kadre [KeyboardModifiers].
 *
 * @param state state field of XKeyEvent or XButtonEvent.
 * @return The corresponding [KeyboardModifiers].
 */
internal fun x11StateToModifiers(state: Int): KeyboardModifiers {
    var bits = 0
    if (state and X11_SHIFT_MASK != 0) bits = bits or KeyboardModifiers.SHIFT
    if (state and X11_CONTROL_MASK != 0) bits = bits or KeyboardModifiers.CTRL
    if (state and X11_MOD1_MASK != 0) bits = bits or KeyboardModifiers.ALT
    if (state and X11_MOD4_MASK != 0) bits = bits or KeyboardModifiers.META
    return KeyboardModifiers(bits)
}

internal fun x11ModifierStateFromPressedKeycodes(keycodes: Iterable<Int>): KeyboardModifierState {
    var state = X11KeyMapper.initialModifierState()
    keycodes.forEach { keycode ->
        val keyCode = x11KeycodeToKeyCode(keycode)
        if (X11KeyMapper.isModifierKey(keyCode)) {
            state = X11KeyMapper.modifierStateFrom(state, keyCode, KeyState.Pressed)
        }
    }
    return state
}

internal fun x11PressedKeycodesFromKeymap(keymap: MemorySegment): List<Int> {
    val snapshot = keymap.reinterpret(X11_KEYMAP_SIZE)
    return (0 until X11_KEYMAP_BITS).filter { keycode ->
        val bits = snapshot.get(ValueLayout.JAVA_BYTE, (keycode / 8).toLong()).toInt() and 0xFF
        bits and (1 shl (keycode % 8)) != 0
    }
}

private fun queryX11ModifierState(displayPtr: Long): KeyboardModifierState? {
    val handle = xQueryKeymap ?: return null
    if (displayPtr == 0L) return null
    return try {
        Arena.ofConfined().use { arena ->
            val keymap = arena.allocate(X11_KEYMAP_SIZE)
            val result = handle.invokeExact(MemorySegment.ofAddress(displayPtr), keymap) as Int
            if (result == 0) {
                null
            } else {
                x11ModifierStateFromPressedKeycodes(x11PressedKeycodesFromKeymap(keymap))
            }
        }
    } catch (_: Throwable) {
        null
    }
}

internal object X11LiveRepeatTracker {
    private val pressedKeycodes = mutableSetOf<Int>()

    fun update(keycode: Int, state: KeyState): Boolean = when (state) {
        KeyState.Pressed -> !pressedKeycodes.add(keycode)
        KeyState.Released -> {
            pressedKeycodes.remove(keycode)
            false
        }
    }

    fun reset() {
        pressedKeycodes.clear()
    }
}

private fun x11KeyInput(
    keycode: Int,
    keysym: Int,
    mappedCode: KeyCode?,
    state: KeyState,
    modifiers: KeyboardModifiers,
    repeat: Boolean = false,
    text: String? = null,
): WindowEvent.KeyInput {
    val native = NativeKeyInfo(
        platform = KeyPlatform.X11,
        scanCode = keycode.toLong(),
        keyValue = keysym.toString(),
        nativeCode = NativeKeyCode.X11(keycode.toLong()),
        nativeKey = NativeLogicalKey.X11(keysym.toLong()),
    )
    val logicalKey = mappedCode?.defaultLogicalKey() ?: LogicalKey.Unidentified(native)
    val resolvedText = text ?: mappedCode?.defaultText()
    return WindowEvent.KeyInput(
        event = KeyEvent(
            physicalKey = mappedCode?.let(PhysicalKey::Code) ?: PhysicalKey.Native(NativeKeyCode.X11(keycode.toLong())),
            logicalKey = logicalKey,
            state = state,
            modifiers = modifiers,
            repeat = repeat,
            text = resolvedText,
            textWithAllModifiers = resolvedText,
            keyWithoutModifiers = mappedCode?.defaultText(),
            native = native,
        ),
        deviceId = null,
    )
}

/**
 * Reads an X11 keycode from the event buffer.
 *
 * Note: a correct implementation would use XLookupKeysym or XkbKeycodeToKeysym
 * to convert a keycode into a keysym. For simplicity (no XLookupKeysym binding),
 * we map keycode → keysym directly using a heuristic based on the standard
 * PC keyboard layout (X11 adds 8 to the hardware keycode).
 *
 * For example: keycode 38 → 'a' (keysym 0x61) on a QWERTY keyboard.
 * This heuristic works for standard PC keyboards.
 */
internal fun x11KeycodeToKeyCode(keycode: Int): KeyCode? = keysymToKeyCode(keycodeToKeysym(keycode))

private fun keycodeToKeysym(keycode: Int): Int {
    // X11 keycode mapping (hardware + 8) → approximate keysym for QWERTY
    // X11 keycodes are layout-dependent — this is an approximation
    return when (keycode) {
        // Letters (standard QWERTY layout)
        38 -> 0x61  // a
        56 -> 0x62  // b
        54 -> 0x63  // c
        40 -> 0x64  // d
        26 -> 0x65  // e
        41 -> 0x66  // f
        42 -> 0x67  // g
        43 -> 0x68  // h
        31 -> 0x69  // i
        44 -> 0x6A  // j
        45 -> 0x6B  // k
        46 -> 0x6C  // l
        58 -> 0x6D  // m
        57 -> 0x6E  // n
        32 -> 0x6F  // o
        33 -> 0x70  // p
        24 -> 0x71  // q
        27 -> 0x72  // r
        39 -> 0x73  // s
        28 -> 0x74  // t
        30 -> 0x75  // u
        55 -> 0x76  // v
        25 -> 0x77  // w
        53 -> 0x78  // x
        29 -> 0x79  // y
        52 -> 0x7A  // z
        // Digits
        19 -> 0x30  // 0
        10 -> 0x31  // 1
        11 -> 0x32  // 2
        12 -> 0x33  // 3
        13 -> 0x34  // 4
        14 -> 0x35  // 5
        15 -> 0x36  // 6
        16 -> 0x37  // 7
        17 -> 0x38  // 8
        18 -> 0x39  // 9
        // Special keys
        65  -> XK_space
        36  -> XK_Return
        9   -> XK_Escape
        22  -> XK_BackSpace
        23  -> XK_Tab
        // Function keys
        67  -> XK_F1
        68  -> XK_F1 + 1   // F2
        69  -> XK_F1 + 2   // F3
        70  -> XK_F1 + 3   // F4
        71  -> XK_F1 + 4   // F5
        72  -> XK_F1 + 5   // F6
        73  -> XK_F1 + 6   // F7
        74  -> XK_F1 + 7   // F8
        75  -> XK_F1 + 8   // F9
        76  -> XK_F1 + 9   // F10
        95  -> XK_F1 + 10  // F11
        96  -> XK_F1 + 11  // F12
        // Navigation
        113 -> XK_Left
        114 -> XK_Right
        111 -> XK_Up
        116 -> XK_Down
        // Modifiers
        50  -> XK_Shift_L
        62  -> XK_Shift_R
        37  -> XK_Control_L
        105 -> XK_Control_R
        64  -> XK_Alt_L
        108 -> XK_Alt_R
        133 -> XK_Super_L
        134 -> XK_Super_R
        else -> 0
    }
}

/**
 * Translates a single XEvent into loop-owned work for later Kotlin dispatch.
 *
 * @param eventBuf Buffer containing the XEvent read by XNextEvent.
 * @param loop     Active event loop.
 * @param wmDeleteWindow WM_DELETE_WINDOW atom for detecting window close.
 */
private fun dispatchEvent(
    eventBuf: MemorySegment,
    loop: X11EventLoop,
    wmDeleteWindow: Long,
    xdnd: XdndAtoms?,
) {
    val eventType = eventBuf.get(ValueLayout.JAVA_INT, XEVENT_TYPE_OFFSET)
    val windowXid = x11EventWindowXid(eventBuf, eventType)
    val windowId = WindowId(windowXid)
    val window = loop.windows[windowXid] ?: return

    when (eventType) {

        // ── Exposure (redraw) ─────────────────────────────────────────────────
        Expose -> {
            loop.enqueueExpose(windowId)
        }

        // ── Resize ────────────────────────────────────────────────────────────
        ConfigureNotify -> {
            val isSynthetic = eventBuf.get(ValueLayout.JAVA_INT, XCONFIGURE_SEND_EVENT_OFFSET) != 0
            val x = eventBuf.get(ValueLayout.JAVA_INT, XCONFIGURE_X_OFFSET)
            val y = eventBuf.get(ValueLayout.JAVA_INT, XCONFIGURE_Y_OFFSET)
            val width  = eventBuf.get(ValueLayout.JAVA_INT, XCONFIGURE_WIDTH_OFFSET)
            val height = eventBuf.get(ValueLayout.JAVA_INT, XCONFIGURE_HEIGHT_OFFSET)
            val changes = window.onConfigureNotify(
                width = width,
                height = height,
                position = PhysicalPosition(x, y),
                positionIsRootRelative = isSynthetic,
            )
            if (changes.sizeChanged && width > 0 && height > 0) {
                loop.enqueueWindowEvent(windowId, WindowEvent.Resized(PhysicalSize(width, height)))
            }
            changes.movedPosition?.let { position ->
                loop.enqueueWindowEvent(windowId, WindowEvent.Moved(position))
            }
        }

        // ── Keyboard ──────────────────────────────────────────────────────────
        KeyPress -> {
            val filterEvent = xFilterEvent
            if (filterEvent != null) {
                try {
                    val consumed = filterEvent.invokeExact(eventBuf, windowXid) as Int
                    if (consumed != 0) {
                        window.drainImeEvents { loop.enqueueWindowEvent(windowId, it) }
                        return
                    }
                } catch (_: Throwable) {
                }
            }
            val keycode = eventBuf.get(ValueLayout.JAVA_INT, XKEY_KEYCODE_OFFSET)
            val state   = eventBuf.get(ValueLayout.JAVA_INT, XKEY_STATE_OFFSET)
            val keysym  = keycodeToKeysym(keycode)
            val mappedCode = keysymToKeyCode(keysym)
            val modifierState = loop.keyboardModifierTracker.modifierStateFor(mappedCode, KeyState.Pressed)
            loop.keyboardModifierTracker.modifiersChangedIfNeeded(modifierState)
                ?.let { loop.enqueueWindowEvent(windowId, it) }
            val mods = modifierState?.logical ?: x11StateToModifiers(state)
            val repeat = X11LiveRepeatTracker.update(keycode, KeyState.Pressed)
            val text = lookupX11Text(eventBuf)
            loop.enqueueWindowEvent(
                windowId,
                x11KeyInput(keycode, keysym, mappedCode, KeyState.Pressed, mods, repeat, text),
            )
        }

        KeyRelease -> {
            val keycode = eventBuf.get(ValueLayout.JAVA_INT, XKEY_KEYCODE_OFFSET)
            val state   = eventBuf.get(ValueLayout.JAVA_INT, XKEY_STATE_OFFSET)
            val keysym  = keycodeToKeysym(keycode)
            val mappedCode = keysymToKeyCode(keysym)
            val modifierState = loop.keyboardModifierTracker.modifierStateFor(mappedCode, KeyState.Released)
            loop.keyboardModifierTracker.modifiersChangedIfNeeded(modifierState)
                ?.let { loop.enqueueWindowEvent(windowId, it) }
            val mods = modifierState?.logical ?: x11StateToModifiers(state)
            X11LiveRepeatTracker.update(keycode, KeyState.Released)
            loop.enqueueWindowEvent(
                windowId,
                x11KeyInput(keycode, keysym, mappedCode, KeyState.Released, mods),
            )
        }

        // ── Mouse buttons ─────────────────────────────────────────────────────
        ButtonPress -> {
            val button = eventBuf.get(ValueLayout.JAVA_INT, XBUTTON_BUTTON_OFFSET)
            val position = xButtonPosition(eventBuf)
            if (button == X11_BUTTON4 || button == X11_BUTTON5) {
                val state = eventBuf.get(ValueLayout.JAVA_INT, XKEY_STATE_OFFSET)
                if (state and X11_CONTROL_MASK != 0) {
                    val delta = if (button == X11_BUTTON4) 1.0 else -1.0
                    loop.enqueueWindowEvent(
                        windowId,
                        WindowEvent.PinchGesture(null, delta, TouchPhase.Moved),
                    )
                    return
                }
            }
            when (button) {
                X11_BUTTON1 -> loop.enqueueWindowEvent(windowId,
                    pointerButton(MouseButton.Left, KeyState.Pressed, position))
                X11_BUTTON2 -> loop.enqueueWindowEvent(windowId,
                    pointerButton(MouseButton.Middle, KeyState.Pressed, position))
                X11_BUTTON3 -> loop.enqueueWindowEvent(windowId,
                    pointerButton(MouseButton.Right, KeyState.Pressed, position))
                X11_BUTTON4 -> loop.enqueueWindowEvent(windowId,
                    WindowEvent.MouseWheel(null, deltaX = 0.0, deltaY = 1.0, phase = TouchPhase.Moved))
                X11_BUTTON5 -> loop.enqueueWindowEvent(windowId,
                    WindowEvent.MouseWheel(null, deltaX = 0.0, deltaY = -1.0, phase = TouchPhase.Moved))
                else -> loop.enqueueWindowEvent(windowId,
                    pointerButton(MouseButton.Other(button), KeyState.Pressed, position))
            }
        }

        ButtonRelease -> {
            val button = eventBuf.get(ValueLayout.JAVA_INT, XBUTTON_BUTTON_OFFSET)
            val position = xButtonPosition(eventBuf)
            // Do not emit MouseInput Released for scroll wheel events (4 and 5)
            when (button) {
                X11_BUTTON1 -> loop.enqueueWindowEvent(windowId,
                    pointerButton(MouseButton.Left, KeyState.Released, position))
                X11_BUTTON2 -> loop.enqueueWindowEvent(windowId,
                    pointerButton(MouseButton.Middle, KeyState.Released, position))
                X11_BUTTON3 -> loop.enqueueWindowEvent(windowId,
                    pointerButton(MouseButton.Right, KeyState.Released, position))
                X11_BUTTON4, X11_BUTTON5 -> { /* scroll wheel — no Released */ }
                else -> loop.enqueueWindowEvent(windowId,
                    pointerButton(MouseButton.Other(button), KeyState.Released, position))
            }
        }

        // ── Mouse motion ──────────────────────────────────────────────────────
        MotionNotify -> {
            val x = eventBuf.get(ValueLayout.JAVA_INT, XMOTION_X_OFFSET).toDouble()
            val y = eventBuf.get(ValueLayout.JAVA_INT, XMOTION_Y_OFFSET).toDouble()
            loop.enqueueWindowEvent(windowId, WindowEvent.PointerMoved(null, PhysicalPosition(x, y), primary = true, source = PointerSource.Mouse))
        }

        // ── Cursor enter/leave ────────────────────────────────────────────────
        EnterNotify -> {
            loop.enqueueWindowEvent(windowId, WindowEvent.PointerEntered(null, xCrossingPosition(eventBuf), primary = true, kind = PointerKind.Mouse))
        }

        LeaveNotify -> {
            loop.enqueueWindowEvent(windowId, WindowEvent.PointerLeft(null, xCrossingPosition(eventBuf), primary = true, kind = PointerKind.Mouse))
        }

        FocusIn -> {
            queryX11ModifierState(loop.displayPtr)
                ?.let(loop.keyboardModifierTracker::initializeIfNeeded)
                ?.let { loop.enqueueWindowEvent(windowId, it) }
            if (window.onFocusChanged(true)) {
                loop.enqueueWindowEvent(windowId, WindowEvent.Focused(gained = true))
            }
        }

        FocusOut -> {
            X11LiveRepeatTracker.reset()
            loop.keyboardModifierTracker.resetIfNeeded()?.let { loop.enqueueWindowEvent(windowId, it) }
            if (window.onFocusChanged(false)) {
                loop.enqueueWindowEvent(windowId, WindowEvent.Focused(gained = false))
            }
        }

        VisibilityNotify -> {
            val state = eventBuf.get(ValueLayout.JAVA_INT, XVISIBILITY_STATE_OFFSET)
            loop.enqueueWindowEvent(windowId, WindowEvent.Occluded(state == X11_VISIBILITY_FULLY_OBSCURED))
            window.onVisibilityNotify()
        }

        // ── Window destruction ────────────────────────────────────────────────
        DestroyNotify -> {
            loop.nativeWindowDestroyed(windowId)
        }

        // ── ClientMessage (WM close + wakeUp + Xdnd) ─────────────────────────
        ClientMessage -> {
            val messageType = eventBuf.get(ValueLayout.JAVA_LONG, XCLIENT_MESSAGE_TYPE_OFFSET)
            val data0 = eventBuf.get(ValueLayout.JAVA_LONG, XCLIENT_DATA_L0_OFFSET)
            if (messageType == wmDeleteWindow || data0 == wmDeleteWindow) {
                loop.enqueueWindowEvent(windowId, WindowEvent.CloseRequested)
            } else if (xdnd != null) {
                handleXdndClientMessage(eventBuf, loop, windowXid, windowId, xdnd)
            }
        }

        // ── SelectionNotify (Xdnd drop data) ─────────────────────────────────
        SelectionNotify -> {
            if (xdnd != null) {
                handleSelectionNotify(eventBuf, loop, windowXid, windowId, xdnd)
            }
        }
    }
}

private fun handleXdndClientMessage(
    eventBuf: MemorySegment,
    loop: X11EventLoop,
    windowXid: Long,
    windowId: WindowId,
    xdnd: XdndAtoms,
) {
    val messageType = eventBuf.get(ValueLayout.JAVA_LONG, XCLIENT_MESSAGE_TYPE_OFFSET)
    val display = MemorySegment.ofAddress(loop.displayPtr)

    when (messageType) {
        xdnd.xdndEnter -> {
            val sourceWindow = eventBuf.get(ValueLayout.JAVA_LONG, XCLIENT_DATA_L0_OFFSET)
            val flags = eventBuf.get(ValueLayout.JAVA_LONG, XCLIENT_DATA_L1_OFFSET)
            loop.dragSourceWindows[windowXid] = sourceWindow
            val types = mutableListOf<Long>()
            val hasMoreTypes = (flags and 1L) != 0L
            if (hasMoreTypes) {
                readXdndTypeList(display, sourceWindow, types)
            } else {
                val t0 = eventBuf.get(ValueLayout.JAVA_LONG, XCLIENT_DATA_L2_OFFSET)
                val t1 = eventBuf.get(ValueLayout.JAVA_LONG, XCLIENT_DATA_L3_OFFSET)
                val t2 = eventBuf.get(ValueLayout.JAVA_LONG, XCLIENT_DATA_L4_OFFSET)
                if (t0 != 0L) types.add(t0)
                if (t1 != 0L) types.add(t1)
                if (t2 != 0L) types.add(t2)
            }
            loop.pendingDropRequests.remove(windowXid)
            loop.enqueueWindowEvent(windowId, WindowEvent.DragEntered(
                position = PhysicalPosition(0.0, 0.0),
                paths = if (types.contains(xdnd.textUriList)) listOf("text/uri-list") else emptyList(),
            ))
        }

        xdnd.xdndPosition -> {
            val sourceWindow = eventBuf.get(ValueLayout.JAVA_LONG, XCLIENT_DATA_L0_OFFSET)
            val packedPos = eventBuf.get(ValueLayout.JAVA_LONG, XCLIENT_DATA_L2_OFFSET)
            val rootX = (packedPos shr 16).toInt()
            val rootY = (packedPos and 0xFFFFL).toInt()
            val localPos = rootToWindowPosition(display, windowXid, rootX, rootY, loop.screen)
            loop.dragSourceWindows[windowXid] = sourceWindow
            val displayMs = MemorySegment.ofAddress(loop.displayPtr)
            X11DragAndDrop.sendXdndStatus(displayMs, windowXid, sourceWindow, accept = true, rootX, rootY)
            loop.enqueueWindowEvent(windowId, WindowEvent.DragMoved(localPos))
        }

        xdnd.xdndLeave -> {
            val sourceWindow = eventBuf.get(ValueLayout.JAVA_LONG, XCLIENT_DATA_L0_OFFSET)
            loop.dragSourceWindows.remove(windowXid)
            loop.pendingDropRequests.remove(windowXid)
            loop.enqueueWindowEvent(windowId, WindowEvent.DragLeft)
        }

        xdnd.xdndDrop -> {
            val sourceWindow = eventBuf.get(ValueLayout.JAVA_LONG, XCLIENT_DATA_L0_OFFSET)
            val packedPos = eventBuf.get(ValueLayout.JAVA_LONG, XCLIENT_DATA_L2_OFFSET)
            val rootX = (packedPos shr 16).toInt()
            val rootY = (packedPos and 0xFFFFL).toInt()
            val localPos = rootToWindowPosition(display, windowXid, rootX, rootY, loop.screen)
            val displayMs = MemorySegment.ofAddress(loop.displayPtr)
            val ok = X11DragAndDrop.requestDropData(
                display = displayMs,
                targetWindow = windowXid,
                xdndSelectionAtom = xdnd.xdndSelection,
                targetAtom = xdnd.textUriList,
                time = 0L,
            )
            if (ok) {
                loop.pendingXdndDrops.add(PendingXdndDrop(
                    targetWindow = windowXid,
                    sourceWindow = sourceWindow,
                    position = localPos,
                ))
            } else {
                loop.dragSourceWindows.remove(windowXid)
                loop.enqueueWindowEvent(windowId, WindowEvent.DragDropped(localPos, emptyList()))
            }
        }
    }
}

private fun handleSelectionNotify(
    eventBuf: MemorySegment,
    loop: X11EventLoop,
    windowXid: Long,
    windowId: WindowId,
    xdnd: XdndAtoms,
) {
    val selection = eventBuf.get(ValueLayout.JAVA_LONG, XSELECTION_SELECTION_OFFSET)
    if (selection != xdnd.xdndSelection) return
    val drop = loop.pendingXdndDrops.poll() ?: return
    val display = MemorySegment.ofAddress(loop.displayPtr)
    val property = eventBuf.get(ValueLayout.JAVA_LONG, XSELECTION_PROPERTY_OFFSET)
    val paths: List<String>
    if (property != 0L) {
        val data = X11DragAndDrop.readSelectionProperty(
            getProperty = xGetWindowProperty,
            free = xFree,
            display = display,
            window = windowXid,
            property = property,
        )
        paths = if (data != null) X11DragAndDrop.parseUriList(data) else emptyList()
    } else {
        paths = emptyList()
    }
    X11DragAndDrop.sendXdndFinished(display, windowXid, drop.sourceWindow, accept = true)
    loop.dragSourceWindows.remove(windowXid)
    loop.enqueueWindowEvent(windowId, WindowEvent.DragDropped(drop.position, paths))
}

private fun readXdndTypeList(display: MemorySegment, sourceWindow: Long, types: MutableList<Long>) {
    val getProperty = xGetWindowProperty ?: return
    val xdndTypeListAtom = x11DragAndDropAtom(display, "XdndTypeList")
    if (xdndTypeListAtom == 0L) return
    try {
        Arena.ofConfined().use { arena ->
            val actualType = arena.allocate(ValueLayout.JAVA_LONG)
            val actualFormat = arena.allocate(ValueLayout.JAVA_INT)
            val nitems = arena.allocate(ValueLayout.JAVA_LONG)
            val bytesAfter = arena.allocate(ValueLayout.JAVA_LONG)
            val propReturn = arena.allocate(ValueLayout.ADDRESS)
            val status = getProperty.invokeExact(
                display, sourceWindow,
                xdndTypeListAtom, 0L, 1024L, 0,
                0L,
                actualType, actualFormat, nitems, bytesAfter, propReturn,
            ) as Int
            if (status != 0) return
            val ptr = propReturn.get(ValueLayout.ADDRESS, 0L)
            if (ptr == MemorySegment.NULL || ptr.address() == 0L) return
            try {
                val count = nitems.get(ValueLayout.JAVA_LONG, 0L)
                for (i in 0 until count) {
                    types.add(ptr.getAtIndex(ValueLayout.JAVA_LONG, i))
                }
            } finally {
                xFree?.invokeExact(ptr) as? Int
            }
        }
    } catch (_: Throwable) {}
}

private fun rootToWindowPosition(
    display: MemorySegment,
    window: Long,
    rootX: Int,
    rootY: Int,
    screen: Int = 0,
): PhysicalPosition<Double> {
    val translate = xTranslateCoordinates ?: return PhysicalPosition(rootX.toDouble(), rootY.toDouble())
    val rootHandle = xRootWindow ?: return PhysicalPosition(rootX.toDouble(), rootY.toDouble())
    return try {
        Arena.ofConfined().use { arena ->
            val destX = arena.allocate(ValueLayout.JAVA_INT)
            val destY = arena.allocate(ValueLayout.JAVA_INT)
            val child = arena.allocate(ValueLayout.JAVA_LONG)
            val rootWindow = rootHandle.invokeExact(display, screen) as Long
            if (rootWindow == 0L) return@use PhysicalPosition(rootX.toDouble(), rootY.toDouble())
            val ok = translate.invokeExact(
                display, rootWindow, window,
                rootX, rootY,
                destX, destY, child,
            ) as Int
            if (ok != 0) {
                PhysicalPosition(
                    destX.get(ValueLayout.JAVA_INT, 0L).toDouble(),
                    destY.get(ValueLayout.JAVA_INT, 0L).toDouble(),
                )
            } else {
                PhysicalPosition(rootX.toDouble(), rootY.toDouble())
            }
        }
    } catch (_: Throwable) {
        PhysicalPosition(rootX.toDouble(), rootY.toDouble())
    }
}

private fun pointerButton(
    button: MouseButton,
    state: KeyState,
    position: PhysicalPosition<Double>,
): WindowEvent.PointerButton =
    WindowEvent.PointerButton(
        deviceId = null,
        state = state,
        position = position,
        primary = true,
        button = ButtonSource.Mouse(button),
    )

private fun xButtonPosition(eventBuf: MemorySegment): PhysicalPosition<Double> =
    PhysicalPosition(
        eventBuf.get(ValueLayout.JAVA_INT, XBUTTON_X_OFFSET).toDouble(),
        eventBuf.get(ValueLayout.JAVA_INT, XBUTTON_Y_OFFSET).toDouble(),
    )

private fun xCrossingPosition(eventBuf: MemorySegment): PhysicalPosition<Double> =
    PhysicalPosition(
        eventBuf.get(ValueLayout.JAVA_INT, XBUTTON_X_OFFSET).toDouble(),
        eventBuf.get(ValueLayout.JAVA_INT, XBUTTON_Y_OFFSET).toDouble(),
    )

internal fun x11EventWindowXid(eventBuf: MemorySegment, eventType: Int): Long {
    val offset = when (eventType) {
        ConfigureNotify -> XCONFIGURE_WINDOW_OFFSET
        DestroyNotify -> XDESTROY_WINDOW_OFFSET
        else -> XANY_WINDOW_OFFSET
    }
    return eventBuf.get(ValueLayout.JAVA_LONG, offset)
}

// ── Dispatch modes ──────────────────────────────────────────────────────────

/** Injectable XPending/XNextEvent/XFlush boundary used by deterministic pump tests. */
internal interface X11PumpOperations {
    fun pendingCount(): Int
    fun dispatchNext()
    fun flush()
}

internal fun interface X11Poller {
    fun poll(xConnectionFd: Int, wakeFd: Int, timeoutMillis: Int): X11PollResult
}

internal data class X11PollResult(
    val xReadable: Boolean,
    val wakeReadable: Boolean,
)

internal sealed interface X11PollAttempt {
    data class Ready(val result: X11PollResult) : X11PollAttempt
    data class Failure(val errno: Int) : X11PollAttempt
}

internal data class X11PumpResult(
    val pollResult: X11PollResult,
    val eventsDispatched: Int,
) {
    val interrupted: Boolean
        get() = eventsDispatched > 0 || pollResult.xReadable || pollResult.wakeReadable
}

private const val X11_POSIX_EINTR = 4
private const val X11_NANOS_PER_MILLISECOND = 1_000_000L
private const val X11_POLLERR: Int = 0x08
private const val X11_POLLHUP: Int = 0x10
private const val X11_POLLNVAL: Int = 0x20
private const val X11_POLL_ERROR_MASK: Int = X11_POLLERR or X11_POLLHUP or X11_POLLNVAL

private class NativeX11PumpOperations(
    private val display: MemorySegment,
    private val eventBuffer: MemorySegment,
    private val loop: X11EventLoop,
    private val wmDeleteWindow: Long,
    private val xdnd: XdndAtoms?,
) : X11PumpOperations {
    override fun pendingCount(): Int =
        (xPending ?: error("XPending is unavailable")).invokeExact(display) as Int

    override fun dispatchNext() {
        (xNextEvent ?: error("XNextEvent is unavailable"))
            .invokeExact(display, eventBuffer) as Int
        dispatchEvent(eventBuffer, loop, wmDeleteWindow, xdnd)
    }

    override fun flush() {
        (xFlush ?: error("XFlush is unavailable")).invokeExact(display) as Int
    }
}

private object NativeX11Poller : X11Poller {
    override fun poll(
        xConnectionFd: Int,
        wakeFd: Int,
        timeoutMillis: Int,
    ): X11PollResult = Arena.ofConfined().use { arena ->
        val pollFds = PollFd.allocate(arena, 2)
        PollFd.set(pollFds, 0, xConnectionFd, PollFd.POLLIN)
        PollFd.set(pollFds, 1, wakeFd, PollFd.POLLIN)

        retryX11Poll(timeoutMillis) { currentTimeoutMillis ->
            val result = invokeX11NativePoll(pollFds, 2L, currentTimeoutMillis)
            if (result.value < 0) {
                val errno = result.errno ?: error("poll failed without a captured errno")
                return@retryX11Poll X11PollAttempt.Failure(errno)
            }
            if (result.value == 0) {
                return@retryX11Poll X11PollAttempt.Ready(
                    X11PollResult(xReadable = false, wakeReadable = false)
                )
            }

            val xFlags = PollFd.revents(pollFds, 0).toInt() and 0xffff
            val wakeFlags = PollFd.revents(pollFds, 1).toInt() and 0xffff
            if ((xFlags and X11_POLL_ERROR_MASK) != 0 ||
                (wakeFlags and X11_POLL_ERROR_MASK) != 0
            ) {
                error(
                    "X11 poll descriptor failure: " +
                        "x=0x${xFlags.toString(16)}, wake=0x${wakeFlags.toString(16)}"
                )
            }

            val xReadable = (xFlags and PollFd.POLLIN.toInt()) != 0
            val wakeReadable = (wakeFlags and PollFd.POLLIN.toInt()) != 0
            if (!xReadable && !wakeReadable) {
                error(
                    "X11 poll returned ${result.value} without a readable descriptor: " +
                        "x=0x${xFlags.toString(16)}, wake=0x${wakeFlags.toString(16)}"
                )
            }
            X11PollAttempt.Ready(X11PollResult(xReadable, wakeReadable))
        }
    }
}

internal fun retryX11Poll(
    timeoutMillis: Int,
    nowNanos: () -> Long = System::nanoTime,
    attempt: (Int) -> X11PollAttempt,
): X11PollResult {
    require(timeoutMillis >= -1) { "timeoutMillis must be -1 or non-negative" }
    val durationNanos = timeoutMillis
        .takeIf { it > 0 }
        ?.toLong()
        ?.times(X11_NANOS_PER_MILLISECOND)
    val startedAtNanos = durationNanos?.let { nowNanos() }
    var retryingAfterInterrupt = false

    while (true) {
        val currentTimeoutMillis = if (
            durationNanos != null && startedAtNanos != null && retryingAfterInterrupt
        ) {
            val elapsedNanos = nowNanos() - startedAtNanos
            val remainingNanos = durationNanos - elapsedNanos
            if (remainingNanos <= 0L) {
                return X11PollResult(xReadable = false, wakeReadable = false)
            }
            (((remainingNanos - 1L) / X11_NANOS_PER_MILLISECOND) + 1L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        } else {
            timeoutMillis
        }

        when (val result = attempt(currentTimeoutMillis)) {
            is X11PollAttempt.Ready -> return result.result
            is X11PollAttempt.Failure -> {
                if (result.errno != X11_POSIX_EINTR) {
                    throw PosixException("poll", result.errno)
                }
                retryingAfterInterrupt = true
            }
        }
    }
}

internal fun pumpX11Once(
    operations: X11PumpOperations,
    poller: X11Poller,
    wakeup: PosixWakeup,
    xConnectionFd: Int,
    timeoutMillis: Int,
    shouldContinue: () -> Boolean = { true },
): X11PumpResult {
    var eventsDispatched = drainPendingX11Events(operations, shouldContinue)
    operations.flush()

    val effectiveTimeout = if (eventsDispatched > 0) 0 else timeoutMillis
    val pollResult = poller.poll(xConnectionFd, wakeup.readFd, effectiveTimeout)

    if (pollResult.wakeReadable && !wakeup.drain()) {
        error("X11 wake descriptor closed while the event loop is running")
    }
    eventsDispatched += drainPendingX11Events(operations, shouldContinue)

    return X11PumpResult(pollResult, eventsDispatched)
}

private fun drainPendingX11Events(
    operations: X11PumpOperations,
    shouldContinue: () -> Boolean,
): Int {
    var dispatched = 0
    while (shouldContinue() && operations.pendingCount() > 0) {
        operations.dispatchNext()
        dispatched += 1
    }
    return dispatched
}

internal fun dispatchX11Once(
    controlFlow: ControlFlow,
    operations: X11PumpOperations,
    poller: X11Poller,
    wakeup: PosixWakeup,
    xConnectionFd: Int,
    nowMillis: () -> Long = System::currentTimeMillis,
    shouldContinue: () -> Boolean = { true },
    hasPendingWork: () -> Boolean = { false },
): StartCause {
    val timeoutMillis = if (hasPendingWork()) 0 else when (controlFlow) {
        is ControlFlow.Poll -> 0
        is ControlFlow.Wait -> -1
        is ControlFlow.WaitUntil -> {
            val now = nowMillis()
            if (controlFlow.instant <= now) 0
            else (controlFlow.instant - now).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }
    val result = pumpX11Once(
        operations = operations,
        poller = poller,
        wakeup = wakeup,
        xConnectionFd = xConnectionFd,
        timeoutMillis = timeoutMillis,
        shouldContinue = shouldContinue,
    )

    return when (controlFlow) {
        is ControlFlow.Poll -> StartCause.Poll
        is ControlFlow.Wait -> StartCause.WaitCancelled()
        is ControlFlow.WaitUntil -> when {
            result.interrupted -> StartCause.WaitCancelled(controlFlow.instant)
            else -> {
                val now = nowMillis()
                if (now >= controlFlow.instant) {
                    StartCause.ResumeTimeReached(controlFlow.instant, now)
                } else {
                    StartCause.Poll
                }
            }
        }
    }
}

/** Dispatches the Kotlin-visible half of an iteration after its native pump. */
internal fun dispatchX11Iteration(
    loop: X11EventLoop,
    handler: ApplicationHandler,
    startCause: StartCause,
) {
    handler.newEvents(loop, startCause)
    loop.drainOpenWindowEvents(handler)
    handler.aboutToWait(loop)
}

internal fun startX11Lifecycle(loop: X11EventLoop, handler: ApplicationHandler) {
    handler.resumed(loop)
    handler.newEvents(loop, StartCause.Init)
    handler.canCreateSurfaces(loop)
    handler.aboutToWait(loop)
}

private fun appendX11Failure(primary: Throwable?, additional: Throwable): Throwable {
    if (primary == null) return additional
    if (additional !== primary) primary.addSuppressed(additional)
    return primary
}

/** Runs terminal callbacks/resources in contract order and always closes the display last. */
internal fun shutdownX11Lifecycle(loop: X11EventLoop, handler: ApplicationHandler) {
    var failure: Throwable? = null
    val actions = listOf<() -> Unit>(
        { handler.destroySurfaces(loop) },
        loop::closeAllWindowsDirect,
        { handler.suspended(loop) },
        loop.wakeup::close,
        { loop.nativeAdapter.closeDisplay(loop.displayPtr) },
    )
    for (action in actions) {
        try {
            action()
        } catch (thrown: Throwable) {
            failure = appendX11Failure(failure, thrown)
        }
    }
    failure?.let { throw it }
}

// ── Entry point ───────────────────────────────────────────────────────────────

private fun x11OpenDisplayFailure(cause: Throwable? = null): IllegalStateException {
    val display = System.getenv("DISPLAY") ?: "<unset>"
    return IllegalStateException(
        "backend=X11 operation=XOpenDisplay DISPLAY=$display",
        cause,
    )
}

/**
 * Opens and immediately closes an X11 connection without constructing an event
 * loop or invoking application callbacks.
 */
fun probeConnection() {
    val openHandle = xOpenDisplay ?: throw x11OpenDisplayFailure()
    val displaySeg = try {
        openHandle.invokeExact(MemorySegment.NULL) as? MemorySegment
    } catch (cause: Throwable) {
        throw x11OpenDisplayFailure(cause)
    } ?: throw x11OpenDisplayFailure()
    if (displaySeg == MemorySegment.NULL || displaySeg.address() == 0L) {
        throw x11OpenDisplayFailure()
    }

    try {
        val connectionHandle = xConnectionNumber
            ?: error("XConnectionNumber is unavailable")
        val connectionFd = connectionHandle.invokeExact(displaySeg) as Int
        check(connectionFd >= 0) { "XConnectionNumber returned an invalid fd: $connectionFd" }
    } finally {
        NativeX11Adapter.closeDisplay(displaySeg.address())
    }
}

/**
 * Entry point of the kadre event loop on Linux (X11).
 *
 * Opens the connection to the X server (XOpenDisplay), creates an [X11EventLoop],
 * calls [ApplicationHandler.resumed], then starts the blocking event loop.
 * Only returns when the application closes.
 *
 * Must be called from the main thread (the one that opened the display).
 *
 * @param handler Lifecycle and event handler.
 * @throws IllegalStateException if `XOpenDisplay` is unavailable or cannot open
 * the configured `DISPLAY`, or if an X11 loop is already active in this process.
 */
fun runApp(handler: ApplicationHandler) {
    check(x11Running.compareAndSet(false, true)) {
        "X11EventLoop.runApp() can only be called once per process. An X11 event loop is already active."
    }

    try {
        val openHandle = xOpenDisplay
        if (openHandle == null) {
            throw x11OpenDisplayFailure()
        }

        // XOpenDisplay(NULL) → uses the DISPLAY environment variable
        val displaySeg = try {
            openHandle.invokeExact(MemorySegment.NULL) as? MemorySegment
        } catch (cause: Throwable) {
            throw x11OpenDisplayFailure(cause)
        } ?: throw x11OpenDisplayFailure()
        if (displaySeg == MemorySegment.NULL || displaySeg.address() == 0L) {
            throw x11OpenDisplayFailure()
        }
        val displayPtr = displaySeg.address()
        val screen = 0  // default screen
        var displayOwnedByLifecycle = false
        try {
            val connectionHandle = xConnectionNumber
                ?: error("XConnectionNumber is unavailable")
            val xConnectionFd = connectionHandle.invokeExact(displaySeg) as Int
            check(xConnectionFd >= 0) { "XConnectionNumber returned an invalid fd: $xConnectionFd" }
            val wakeup = PosixWakeup.open()
            var lifecycleStarted = false
            try {
                // Obtain the WM_DELETE_WINDOW atom to detect a clean close.
                val wmDeleteWindow: Long = Arena.ofConfined().use { arena ->
                    val atomName = "WM_DELETE_WINDOW".toByteArray(Charsets.US_ASCII)
                    val namePtr = arena.allocate(atomName.size.toLong() + 1)
                    for (i in atomName.indices) {
                        namePtr.set(ValueLayout.JAVA_BYTE, i.toLong(), atomName[i])
                    }
                    namePtr.set(ValueLayout.JAVA_BYTE, atomName.size.toLong(), 0)
                    xInternAtom?.invokeExact(displaySeg, namePtr, 0) as? Long ?: 0L
                }

                val loop = X11EventLoop(displayPtr, screen, wakeup)
                val xdnd = x11DragAndDropAtoms(displayPtr)

                Arena.ofConfined().use { arena ->
                    val pumpOperations = NativeX11PumpOperations(
                        display = displaySeg,
                        eventBuffer = arena.allocate(XEVENT_SIZE, XEVENT_ALIGN),
                        loop = loop,
                        wmDeleteWindow = wmDeleteWindow,
                        xdnd = xdnd,
                    )
                    lifecycleStarted = true
                    displayOwnedByLifecycle = true
                    try {
                        startX11Lifecycle(loop, handler)
                        while (!loop.isExiting) {
                            val startCause = dispatchX11Once(
                                controlFlow = loop.controlFlow,
                                operations = pumpOperations,
                                poller = NativeX11Poller,
                                wakeup = wakeup,
                                xConnectionFd = xConnectionFd,
                                shouldContinue = { !loop.isExiting },
                                hasPendingWork = loop::hasPendingWork,
                            )
                            dispatchX11Iteration(loop, handler, startCause)
                        }
                    } finally {
                        shutdownX11Lifecycle(loop, handler)
                    }
                }
            } finally {
                if (!lifecycleStarted) wakeup.close()
            }
        } finally {
            if (!displayOwnedByLifecycle) NativeX11Adapter.closeDisplay(displayPtr)
        }
    } finally {
        x11Running.set(false)
    }
}

private fun x11DragAndDropAtoms(displayPtr: Long): XdndAtoms? {
    val display = MemorySegment.ofAddress(displayPtr)
    val enter = x11DragAndDropAtom(display, "XdndEnter")
    val position = x11DragAndDropAtom(display, "XdndPosition")
    val leave = x11DragAndDropAtom(display, "XdndLeave")
    val drop = x11DragAndDropAtom(display, "XdndDrop")
    val selection = x11DragAndDropAtom(display, "XdndSelection")
    val textUriList = x11DragAndDropAtom(display, "text/uri-list")
    return if (
        enter != 0L && position != 0L && leave != 0L && drop != 0L &&
        selection != 0L && textUriList != 0L
    ) {
        XdndAtoms(enter, position, leave, drop, selection, textUriList)
    } else {
        null
    }
}
