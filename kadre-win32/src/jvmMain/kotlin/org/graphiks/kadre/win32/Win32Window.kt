/**
 * Win32 implementation of the [Window] interface for Windows Desktop.
 *
 * Uses the Foreign Function & Memory API (JEP 454, JDK 25) to interact
 * with user32.dll and kernel32.dll without JNA or any other intermediate layer.
 *
 * Creation flow:
 *  1. [companion.registerClassOnce] → RegisterClassExW (run only once)
 *  2. [createWindow]                → CreateWindowExW
 *  3. ShowWindow / UpdateWindow     → initial display
 *
 * GRA-141: Win32Window — complete implementation of the Window interface.
 */
package org.graphiks.kadre.win32

import org.graphiks.kadre.core.CursorGrabMode
import org.graphiks.kadre.core.CursorIcon
import org.graphiks.kadre.core.CursorImage
import org.graphiks.kadre.core.CustomCursor
import org.graphiks.kadre.core.Fullscreen
import org.graphiks.kadre.core.Icon
import org.graphiks.kadre.core.ImeCapabilities
import org.graphiks.kadre.core.ImeCapability
import org.graphiks.kadre.core.ImePurpose
import org.graphiks.kadre.core.MonitorHandle
import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.core.InputCapabilities
import org.graphiks.kadre.core.Insets
import org.graphiks.kadre.core.RawDisplayHandle
import org.graphiks.kadre.core.RawWindowHandle
import org.graphiks.kadre.core.RequestError
import org.graphiks.kadre.core.ResizeDirection
import org.graphiks.kadre.core.Theme
import org.graphiks.kadre.core.UserAttentionType
import org.graphiks.kadre.core.Window
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowButtons
import org.graphiks.kadre.core.WindowId
import org.graphiks.kadre.core.WindowLevel
import org.graphiks.kadre.core.WindowRequestResult
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.graphiks.kadre.ffi.win32.*
import org.graphiks.kadre.ffi.win32.generated.*

private fun lookupDowncall(libName: String, symbol: String, desc: FunctionDescriptor): MethodHandle? {
    return try {
        val lookup = SymbolLookup.libraryLookup(libName, Arena.global())
        lookup.find(symbol).map { Linker.nativeLinker().downcallHandle(it, desc) }.orElse(null)
    } catch (_: Throwable) { null }
}

private val dwmExtendFrameIntoClientArea: MethodHandle? by lazy {
    lookupDowncall("dwmapi.dll", "DwmExtendFrameIntoClientArea",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS))
}

private val dwmEnableBlurBehindWindow: MethodHandle? by lazy {
    lookupDowncall("dwmapi.dll", "DwmEnableBlurBehindWindow",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS))
}

private val setWindowDisplayAffinity: MethodHandle? by lazy {
    lookupDowncall("user32.dll", "SetWindowDisplayAffinity",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT))
}

private val flashWindowEx: MethodHandle? by lazy {
    lookupDowncall("user32.dll", "FlashWindowEx",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
}

private val getActiveWindow: MethodHandle? by lazy {
    lookupDowncall("user32.dll", "GetActiveWindow",
        FunctionDescriptor.of(ValueLayout.ADDRESS))
}

private val setLayeredWindowAttributes: MethodHandle? by lazy {
    lookupDowncall("user32.dll", "SetLayeredWindowAttributes",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_BYTE, ValueLayout.JAVA_INT))
}

private val dragAcceptFiles: MethodHandle? by lazy {
    lookupDowncall("shell32.dll", "DragAcceptFiles",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT))
}

internal val toUnicode: MethodHandle? by lazy {
    lookupDowncall("user32.dll", "ToUnicode",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
}

internal val getKeyboardState: MethodHandle? by lazy {
    lookupDowncall("user32.dll", "GetKeyboardState",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
}

private val immAssociateContextEx: MethodHandle? by lazy {
    lookupDowncall("imm32.dll", "ImmAssociateContextEx",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT))
}

internal val immGetContext: MethodHandle? by lazy {
    lookupDowncall("imm32.dll", "ImmGetContext",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS))
}

internal val immReleaseContext: MethodHandle? by lazy {
    lookupDowncall("imm32.dll", "ImmReleaseContext",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS))
}

private val immSetCompositionWindow: MethodHandle? by lazy {
    lookupDowncall("imm32.dll", "ImmSetCompositionWindow",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS))
}

private val immSetCandidateWindow: MethodHandle? by lazy {
    lookupDowncall("imm32.dll", "ImmSetCandidateWindow",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS))
}

private val immSetConversionStatus: MethodHandle? by lazy {
    lookupDowncall("imm32.dll", "ImmSetConversionStatus",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
}

/**
 * Native Win32 window implementing [Window].
 *
 * The constructor is private: use [Win32Window.create] to instantiate.
 *
 * @param hwnd      Native window handle (HWND), represented by a MemorySegment.
 * @param hInstance Handle of the current module (HINSTANCE), represented by a MemorySegment.
 * @param attrs     Window creation attributes.
 */
class Win32Window private constructor(
    private val hwnd: MemorySegment,
    private val hInstance: MemorySegment,
    private val attrs: WindowAttributes,
    private val ownerThreadId: Int,
) : Window {

    // -- R2 fullscreen state --

    @Volatile private var _fullscreen: Fullscreen? = attrs.fullscreen
    @Volatile private var _savedStyle: Long? = null
    @Volatile private var _savedRect: IntArray? = null
    @Volatile private var _enabledButtons: WindowButtons = attrs.enabledButtons

    // -- Cursor visibility counter for ShowCursor balance --

    internal val cursorVisibleCounter: AtomicInteger = AtomicInteger(0)

    private val iconLock = Any()
    private var ownedWindowIconHandle: MemorySegment = MemorySegment.NULL

    override val id: WindowId = WindowId(hwnd.address())

    override val rawWindowHandle: RawWindowHandle
        get() = RawWindowHandle.Win32(
            hwnd = hwnd.address(),
            hinstance = hInstance.address(),
        )

    override val rawDisplayHandle: RawDisplayHandle
        get() = RawDisplayHandle.Win32(hinstance = hInstance.address())

    override fun inputCapabilities(): InputCapabilities =
        InputCapabilities(touch = true)

    override fun imeCapabilities(): ImeCapabilities =
        ImeCapabilities(
            enabled = true,
            purposes = listOf(ImePurpose.Normal, ImePurpose.Password, ImePurpose.Terminal),
            capabilities = setOf(ImeCapability.Composition, ImeCapability.Password),
        )

    @Volatile
    private var needsRedraw: Boolean = false

    override fun requestRedraw() {
        needsRedraw = true
    }

    override fun setTitle(title: String) {
        Arena.ofConfined().use { arena ->
            val titleW = arena.allocateWString(title)
            SetWindowTextW(hwnd, titleW)
        }
    }

    override val innerSize: PhysicalSize<Int>
        get() = rectToSize { GetClientRect(hwnd, it) } ?: attrs.size ?: PhysicalSize(800, 600)

    override val outerSize: PhysicalSize<Int>
        get() = rectToSize { GetWindowRect(hwnd, it) } ?: attrs.size ?: PhysicalSize(800, 600)

    private fun rectToSize(fillRect: (MemorySegment) -> Int): PhysicalSize<Int>? {
        return try {
            Arena.ofConfined().use { arena ->
                val rect = arena.allocate(RECT_SIZE, RECT_ALIGN)
                val ok = fillRect(rect)
                if (ok == 0) return@use null
                val width  = rect.get(ValueLayout.JAVA_INT, RECT_OFFSET_RIGHT)  -
                             rect.get(ValueLayout.JAVA_INT, RECT_OFFSET_LEFT)
                val height = rect.get(ValueLayout.JAVA_INT, RECT_OFFSET_BOTTOM) -
                             rect.get(ValueLayout.JAVA_INT, RECT_OFFSET_TOP)
                PhysicalSize(width, height)
            }
        } catch (_: Throwable) {
            null
        }
    }

    override val scaleFactor: Double
        get() = try {
            val dpi = GetDpiForWindow(hwnd)
            if (dpi > 0) dpi.toDouble() / 96.0 else 1.0
        } catch (_: Throwable) {
            1.0
        }

    override val safeArea: Insets<Int>
        get() {
            return try {
                Arena.ofConfined().use { arena ->
                    val windowRect = arena.allocate(RECT_SIZE, RECT_ALIGN)
                    val wrOk = GetWindowRect(hwnd, windowRect)
                    if (wrOk == 0) return@use Insets(0, 0, 0, 0)

                    val clientRect = arena.allocate(RECT_SIZE, RECT_ALIGN)
                    val crOk = GetClientRect(hwnd, clientRect)
                    if (crOk == 0) return@use Insets(0, 0, 0, 0)

                    val clientTopLeft = arena.allocate(POINT_SIZE, POINT_ALIGN)
                    val ctsOk = ClientToScreen(hwnd, clientTopLeft)
                    if (ctsOk == 0) return@use Insets(0, 0, 0, 0)

                    val clientLeft = clientTopLeft.get(ValueLayout.JAVA_INT, POINT_OFFSET_X)
                    val clientTop = clientTopLeft.get(ValueLayout.JAVA_INT, POINT_OFFSET_Y)
                    val winLeft = windowRect.get(ValueLayout.JAVA_INT, RECT_OFFSET_LEFT)
                    val winTop = windowRect.get(ValueLayout.JAVA_INT, RECT_OFFSET_TOP)
                    val winRight = windowRect.get(ValueLayout.JAVA_INT, RECT_OFFSET_RIGHT)
                    val winBottom = windowRect.get(ValueLayout.JAVA_INT, RECT_OFFSET_BOTTOM)
                    val clientWidth = clientRect.get(ValueLayout.JAVA_INT, RECT_OFFSET_RIGHT) -
                        clientRect.get(ValueLayout.JAVA_INT, RECT_OFFSET_LEFT)
                    val clientHeight = clientRect.get(ValueLayout.JAVA_INT, RECT_OFFSET_BOTTOM) -
                        clientRect.get(ValueLayout.JAVA_INT, RECT_OFFSET_TOP)

                    Insets(
                        top = maxOf(0, clientTop - winTop),
                        bottom = maxOf(0, winBottom - (clientTop + clientHeight)),
                        left = maxOf(0, clientLeft - winLeft),
                        right = maxOf(0, winRight - (clientLeft + clientWidth)),
                    )
                }
            } catch (_: Throwable) {
                Insets(0, 0, 0, 0)
            }
        }

    override fun setVisible(visible: Boolean) {
        val nCmdShow = if (visible) SW_SHOW else SW_HIDE
        ShowWindow(hwnd, nCmdShow)
    }

    override fun close() {
        // Reset cursor visibility counter to ensure balanced ShowCursor calls.
        // We use getAndSet(0) to atomically read and reset the counter in one operation.
        val currentCount = cursorVisibleCounter.getAndSet(0)
        if (currentCount > 0) {
            // Hide cursor to balance any remaining ShowCursor(1) calls
            repeat(currentCount) { ShowCursor(0) }
        } else if (currentCount < 0) {
            // Show cursor to balance any remaining ShowCursor(0) calls
            // Note: Negative values should not occur in normal usage, but we handle them
            // defensively to ensure the system ShowCursor counter remains balanced.
            repeat(-currentCount) { ShowCursor(1) }
        }
        setWindowIcon(null)
        KadreWndProc.unregisterConstraints(hwnd.address())
        DestroyWindow(hwnd)
    }

    // -- R1: window state & geometry --

    override val title: String
        get() {
            return try {
                Arena.ofConfined().use { arena ->
                    val buf = arena.allocate(512L, 2L)
                    val len = GetWindowTextW(hwnd, buf, 256)
                    if (len <= 0) return@use ""
                    val chars = CharArray(len) {
                        buf.getAtIndex(ValueLayout.JAVA_SHORT, it.toLong()).toInt().toChar()
                    }
                    String(chars)
                }
            } catch (_: Throwable) { attrs.title }
        }

    override val isVisible: Boolean?
        get() = try {
            IsWindowVisible(hwnd) != 0
        } catch (_: Throwable) { null }

    private fun getWindowStyle(): Long = try {
        GetWindowLongPtrW(hwnd, GWL_STYLE)
    } catch (_: Throwable) { 0L }

    private fun setWindowStyle(style: Long) {
        try {
            SetWindowLongPtrW(hwnd, GWL_STYLE, style)
            SetWindowPos(hwnd, MemorySegment.NULL, 0, 0, 0, 0, WIN32_STYLE_UPDATE_FLAGS)
        } catch (_: Throwable) {}
    }

    override fun setResizable(resizable: Boolean) {
        val style = getWindowStyle()
        val newStyle = if (resizable) style or WS_THICKFRAME.toLong()
                       else style and WS_THICKFRAME.toLong().inv()
        setWindowStyle(newStyle)
    }

    override val isResizable: Boolean
        get() = (getWindowStyle() and WS_THICKFRAME.toLong()) != 0L

    override fun setMinimized(minimized: Boolean) {
        try {
            val nCmd = if (minimized) SW_MINIMIZE else SW_RESTORE
            ShowWindow(hwnd, nCmd)
        } catch (_: Throwable) {}
    }

    override val isMinimized: Boolean?
        get() = try {
            IsIconic(hwnd) != 0
        } catch (_: Throwable) { null }

    override fun setMaximized(maximized: Boolean) {
        try {
            val nCmd = if (maximized) SW_MAXIMIZE else SW_RESTORE
            ShowWindow(hwnd, nCmd)
        } catch (_: Throwable) {}
    }

    override val isMaximized: Boolean
        get() = try {
            IsZoomed(hwnd) != 0
        } catch (_: Throwable) { false }

    override fun setDecorations(decorated: Boolean) {
        val style = getWindowStyle()
        val newStyle = if (decorated) {
            style or WS_CAPTION.toLong() or WS_SYSMENU.toLong() or
            WS_MINIMIZEBOX.toLong() or WS_MAXIMIZEBOX.toLong()
        } else {
            style and (WS_CAPTION.toLong() or WS_SYSMENU.toLong() or
            WS_MINIMIZEBOX.toLong() or WS_MAXIMIZEBOX.toLong()).inv()
        }
        setWindowStyle(newStyle)
        applyEnabledButtons(enabledButtons)
    }

    override val isDecorated: Boolean
        get() = (getWindowStyle() and WS_CAPTION.toLong()) != 0L

    override fun setEnabledButtons(buttons: WindowButtons) {
        _enabledButtons = buttons
        applyEnabledButtons(buttons)
    }

    override val enabledButtons: WindowButtons
        get() = _enabledButtons

    private fun applyEnabledButtons(buttons: WindowButtons) {
        val style = getWindowStyle()
        val decorated = (style and WS_CAPTION.toLong()) != 0L
        val newStyle = style.withEnabledWindowButtonStyles(buttons, decorated)
        if (newStyle != style) {
            setWindowStyle(newStyle)
        }
        updateCloseMenuItem(buttons.contains(WindowButtons.CLOSE))
    }

    private fun updateCloseMenuItem(enabled: Boolean) {
        try {
            val menu = GetSystemMenu(hwnd, 0)
            if (menu == MemorySegment.NULL) return
            EnableMenuItem(menu, SC_CLOSE, win32CloseMenuState(enabled))
        } catch (_: Throwable) {}
    }

    override fun setMinSurfaceSize(size: PhysicalSize<Int>?) {
        _minSurfaceSize = size
        syncConstraints()
    }

    override fun setMaxSurfaceSize(size: PhysicalSize<Int>?) {
        _maxSurfaceSize = size
        syncConstraints()
    }

    override val surfaceResizeIncrements: PhysicalSize<Int>?
        get() = _surfaceResizeIncrements

    override fun setSurfaceResizeIncrements(increments: PhysicalSize<Int>?) {
        _surfaceResizeIncrements = increments
        syncConstraints()
    }

    private fun syncConstraints() {
        KadreWndProc.registerConstraints(
            hwnd = hwnd.address(),
            constraints = KadreWndProc.WindowConstraints(
                minSize = _minSurfaceSize,
                maxSize = _maxSurfaceSize,
                resizeIncrements = _surfaceResizeIncrements,
            ),
        )
    }

    @Volatile internal var _minSurfaceSize: PhysicalSize<Int>? = attrs.minSize
    @Volatile internal var _maxSurfaceSize: PhysicalSize<Int>? = attrs.maxSize
    @Volatile internal var _surfaceResizeIncrements: PhysicalSize<Int>? = attrs.resizeIncrements

    override val outerPosition: PhysicalPosition<Int>
        get() = try {
            Arena.ofConfined().use { arena ->
                val rect = arena.allocate(RECT_SIZE, RECT_ALIGN)
                val ok = GetWindowRect(hwnd, rect)
                if (ok == 0) return@use PhysicalPosition(0, 0)
                val x = rect.get(ValueLayout.JAVA_INT, RECT_OFFSET_LEFT)
                val y = rect.get(ValueLayout.JAVA_INT, RECT_OFFSET_TOP)
                PhysicalPosition(x, y)
            }
        } catch (_: Throwable) { PhysicalPosition(0, 0) }

    override fun setOuterPosition(position: PhysicalPosition<Int>) {
        try {
            SetWindowPos(
                hwnd, MemorySegment.NULL,
                position.x, position.y, 0, 0,
                SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE,
            )
        } catch (_: Throwable) {}
    }

    override fun prePresentNotify() { /* no-op on Win32 */ }

    // -- R2: monitor & fullscreen --

    override fun currentMonitor(): MonitorHandle? = win32MonitorFromHwnd(hwnd)

    override fun availableMonitors(): List<MonitorHandle> =
        enumerateWin32Monitors()

    override fun primaryMonitor(): MonitorHandle? =
        enumerateWin32Monitors().firstOrNull { it.isPrimary }

    override val fullscreen: Fullscreen?
        get() = _fullscreen

    override fun focusWindow() {
        try {
            val visible = isVisible == true
            val minimized = isMinimized == true
            if (win32ShouldFocusWindow(visible, minimized, isForegroundWindow())) {
                forceWindowActive(hwnd)
            }
        } catch (_: Throwable) {}
    }

    override val hasFocus: Boolean
        get() = Win32FocusState.hasActiveFocus(hwnd.address())

    private fun isForegroundWindow(): Boolean =
        try {
            val foreground = GetForegroundWindow()
            foreground.address() == hwnd.address()
        } catch (_: Throwable) {
            false
        }

    override fun setFullscreen(fullscreen: Fullscreen?) {
        try {
            when (fullscreen) {
                null -> exitFullscreen()
                is Fullscreen.Borderless -> enterBorderless(fullscreen.monitor)
                is Fullscreen.Exclusive  -> {
                    enterBorderless(fullscreen.monitor)
                    _fullscreen = fullscreen
                }
            }
        } catch (_: Throwable) {}
    }

    private fun enterBorderless(monitor: MonitorHandle?) {
        if (_savedStyle == null) {
            _savedStyle = getWindowStyle()
            _savedRect = try {
                Arena.ofConfined().use { arena ->
                    val rect = arena.allocate(16L, 4L)
                    val ok = GetWindowRect(hwnd, rect)
                    if (ok != 0) intArrayOf(
                        rect.get(ValueLayout.JAVA_INT, RECT_OFFSET_LEFT),
                        rect.get(ValueLayout.JAVA_INT, RECT_OFFSET_TOP),
                        rect.get(ValueLayout.JAVA_INT, RECT_OFFSET_RIGHT),
                        rect.get(ValueLayout.JAVA_INT, RECT_OFFSET_BOTTOM),
                    ) else null
                }
            } catch (_: Throwable) { null }
        }

        val targetMonitor: Win32MonitorHandle? = when (monitor) {
            is Win32MonitorHandle -> monitor
            null -> win32MonitorFromHwnd(hwnd)
            else -> null
        }

        val (mx, my, mw, mh) = if (targetMonitor != null) {
            val pos = targetMonitor.position
            val cm = targetMonitor.currentVideoMode
            intArrayOf(pos.x, pos.y, cm?.size?.width ?: 1920, cm?.size?.height ?: 1080)
        } else {
            intArrayOf(0, 0, 1920, 1080)
        }

        setWindowStyle(0x80000000L.toLong() or WS_VISIBLE.toLong())
        SetWindowPos(
            hwnd, HWND_TOP,
            mx, my, mw, mh,
            SWP_NOACTIVATE or SWP_FRAMECHANGED,
        )

        _fullscreen = Fullscreen.Borderless(monitor)
    }

    private fun exitFullscreen() {
        val savedStyle = _savedStyle
        val savedRect  = _savedRect
        if (savedStyle != null) {
            setWindowStyle(savedStyle)
            if (savedRect != null) {
                val w = savedRect[2] - savedRect[0]
                val h = savedRect[3] - savedRect[1]
                SetWindowPos(
                    hwnd, HWND_TOP,
                    savedRect[0], savedRect[1], w, h,
                    SWP_NOACTIVATE or SWP_FRAMECHANGED,
                )
            }
            _savedStyle = null
            _savedRect  = null
        }
        _fullscreen = null
    }

    // -- R3: cursor, theme & appearance --

    @Volatile private var _cursorHandle: MemorySegment? = null

    /**
     * Sets the cursor shape by loading the matching system IDC_* resource via
     * LoadCursorW and applying it with SetCursor.
     *
     * Win32-specific: SetCursor only takes effect while the pointer is over the
     * window; the cached handle is re-applied from the WM_SETCURSOR handler.
     * Silently no-ops if the cursor resource cannot be loaded.
     */
    override fun setCursor(cursor: CursorIcon) {
        try {
            val id = cursorIdcResource(cursor)
            val hCursor = LoadCursorW(MemorySegment.NULL, MemorySegment.ofAddress(id))
            if (hCursor == MemorySegment.NULL) return
            _cursorHandle = hCursor
            SetCursor(hCursor)
        } catch (_: Throwable) {}
    }

    /**
     * Applies a previously created custom cursor by treating [CustomCursor.id] as
     * a native HCURSOR handle and passing it to SetCursor.
     *
     * Win32-specific: the caller owns the HCURSOR lifetime; this only references
     * it. As with [setCursor], the handle is re-applied from WM_SETCURSOR.
     */
    override fun setCustomCursor(cursor: CustomCursor) {
        try {
            val hCursor = MemorySegment.ofAddress(cursor.id)
            _cursorHandle = hCursor
            SetCursor(hCursor)
        } catch (_: Throwable) {}
    }

    /**
     * Shows or hides the cursor via ShowCursor.
     *
     * Win32-specific: ShowCursor maintains a process-wide internal display
     * counter rather than a boolean, so visibility is shared across all windows
     * in the process and balanced show/hide calls are expected.
     *
     * This implementation maintains an atomic counter to ensure proper balancing:
     * - Increment when showing (ShowCursor(1))
     * - Decrement when hiding (ShowCursor(0))
     * - Reset to 0 when counter goes negative to prevent underflow
     *   (defensive programming: ensures system counter stays balanced even with
     *   unmatched hide/show calls)
     */
    override fun setCursorVisible(visible: Boolean) {
        try {
            if (visible) {
                cursorVisibleCounter.incrementAndGet()
            } else {
                val prev = cursorVisibleCounter.decrementAndGet()
                // Ensure counter doesn't go negative; reset to 0 if it does
                if (prev < 0) cursorVisibleCounter.set(0)
            }
            // Call ShowCursor to update the system counter
            ShowCursor(if (visible) 1 else 0)
        } catch (_: Throwable) {}
    }

    /**
     * Confines or releases the cursor via ClipCursor.
     *
     * Win32-specific: [CursorGrabMode.None] removes any clip rectangle, while
     * [CursorGrabMode.Confined] clips the cursor to the window's screen rect.
     * Win32 has no true cursor-lock primitive, so [CursorGrabMode.Locked] is
     * treated the same as [CursorGrabMode.Confined]. Returns
     * [WindowRequestResult.Failure] with [RequestError.OsError] if the Win32
     * call fails.
     */
    override fun setCursorGrab(mode: CursorGrabMode): WindowRequestResult =
        try {
            when (mode) {
                CursorGrabMode.None -> {
                    val ok = ClipCursor(MemorySegment.NULL)
                    if (ok == 0) return WindowRequestResult.Failure(RequestError.OsError("ClipCursor(NULL) failed"))
                }
                CursorGrabMode.Confined, CursorGrabMode.Locked -> {
                    Arena.ofConfined().use { arena ->
                        val rect = arena.allocate(RECT_SIZE, RECT_ALIGN)
                        val rectOk = GetWindowRect(hwnd, rect)
                        if (rectOk == 0) return WindowRequestResult.Failure(RequestError.OsError("GetWindowRect failed for cursor grab"))
                        val clipOk = ClipCursor(rect)
                        if (clipOk == 0) return WindowRequestResult.Failure(RequestError.OsError("ClipCursor failed"))
                    }
                }
            }
            WindowRequestResult.Success
        } catch (t: Throwable) {
            WindowRequestResult.Failure(RequestError.OsError(t.message ?: t::class.simpleName ?: "Win32 cursor grab failed"))
        }

    /**
     * Moves the cursor to a window-client-relative [position] via SetCursorPos.
     *
     * Win32-specific: SetCursorPos operates in screen coordinates, so the
     * client-relative position is translated using the window's screen rect
     * (GetWindowRect). Returns [WindowRequestResult.Failure] with
     * [RequestError.OsError] if the coordinate lookup or move fails.
     */
    override fun setCursorPosition(position: PhysicalPosition<Int>): WindowRequestResult =
        try {
            val (screenX, screenY) = Arena.ofConfined().use { arena ->
                val rect = arena.allocate(RECT_SIZE, RECT_ALIGN)
                val ok = GetWindowRect(hwnd, rect)
                if (ok == 0) return WindowRequestResult.Failure(RequestError.OsError("GetWindowRect failed for cursor position"))
                rect.get(ValueLayout.JAVA_INT, RECT_OFFSET_LEFT) + position.x to
                    rect.get(ValueLayout.JAVA_INT, RECT_OFFSET_TOP) + position.y
            }
            val ok = SetCursorPos(screenX, screenY)
            if (ok == 0) {
                WindowRequestResult.Failure(RequestError.OsError("SetCursorPos failed"))
            } else {
                WindowRequestResult.Success
            }
        } catch (t: Throwable) {
            WindowRequestResult.Failure(RequestError.OsError(t.message ?: t::class.simpleName ?: "Win32 cursor position failed"))
        }

    /**
     * Enables or disables cursor hit-testing by toggling the WS_EX_TRANSPARENT
     * extended window style via SetWindowLongPtrW.
     *
     * Win32-specific: when [hittest] is false the WS_EX_TRANSPARENT flag is set
     * so pointer events pass through to windows underneath; when true the flag is
     * cleared so the window receives hit-testing again. Returns
     * [WindowRequestResult.Failure] with [RequestError.OsError] if reading or
     * updating the extended style fails.
     */
    override fun setCursorHittest(hittest: Boolean): WindowRequestResult =
        try {
            SetLastError(0L)
            val exStyle = GetWindowLongPtrW(hwnd, GWL_EXSTYLE)
            if (exStyle == 0L) {
                val error = GetLastError().toInt()
                if (error != 0) {
                    return WindowRequestResult.Failure(RequestError.OsError("GetWindowLongPtrW failed: $error"))
                }
            }
            val transparentFlag = 0x00000020L
            val newStyle = if (!hittest) exStyle or transparentFlag
                           else exStyle and transparentFlag.inv()
            SetLastError(0L)
            val previous = SetWindowLongPtrW(hwnd, GWL_EXSTYLE, newStyle)
            if (previous == 0L) {
                val error = GetLastError().toInt()
                if (error != 0) {
                    return WindowRequestResult.Failure(RequestError.OsError("SetWindowLongPtrW failed: $error"))
                }
            }
            WindowRequestResult.Success
        } catch (t: Throwable) {
            WindowRequestResult.Failure(RequestError.OsError(t.message ?: t::class.simpleName ?: "Win32 cursor hit-testing failed"))
        }

    override fun showWindowMenu(position: PhysicalPosition<Int>): WindowRequestResult =
        try {
            val menu = GetSystemMenu(hwnd, 0)
            if (menu == MemorySegment.NULL) {
                return WindowRequestResult.Success
            }
            syncSystemMenuState(menu)
            val (screenX, screenY) = Arena.ofConfined().use { arena ->
                val point = arena.allocate(POINT_SIZE, POINT_ALIGN)
                point.set(ValueLayout.JAVA_INT, POINT_OFFSET_X, position.x)
                point.set(ValueLayout.JAVA_INT, POINT_OFFSET_Y, position.y)
                val ok = ClientToScreen(hwnd, point)
                if (ok == 0) return WindowRequestResult.Failure(RequestError.OsError("ClientToScreen failed for window menu"))
                point.get(ValueLayout.JAVA_INT, POINT_OFFSET_X) to
                    point.get(ValueLayout.JAVA_INT, POINT_OFFSET_Y)
            }
            val command = TrackPopupMenu(
                menu,
                TPM_RETURNCMD or TPM_LEFTALIGN,
                screenX,
                screenY,
                0,
                hwnd,
                MemorySegment.NULL,
            )
            if (command != 0) {
                val ok = PostMessageW(hwnd, WM_SYSCOMMAND, command.toLong(), 0L)
                if (ok == 0) return WindowRequestResult.Failure(RequestError.OsError("PostMessageW(WM_SYSCOMMAND) failed"))
            }
            WindowRequestResult.Success
        } catch (t: Throwable) {
            WindowRequestResult.Failure(RequestError.OsError(t.message ?: t::class.simpleName ?: "Win32 window menu failed"))
        }

    override fun dragWindow(): WindowRequestResult =
        sendNonClientDrag(HTCAPTION, "Win32 window drag failed")

    override fun dragResizeWindow(direction: ResizeDirection): WindowRequestResult =
        sendNonClientDrag(direction.toWin32HitTest(), "Win32 resize drag failed")

    private fun sendNonClientDrag(hitTest: Long, failureMessage: String): WindowRequestResult =
        try {
            if (!isOwnerThread()) {
                val ok = PostMessageW(hwnd, WM_KADRE_NON_CLIENT_DRAG, hitTest, 0L)
                return if (ok == 0) {
                    WindowRequestResult.Failure(RequestError.OsError("PostMessageW(WM_KADRE_NON_CLIENT_DRAG) failed"))
                } else {
                    WindowRequestResult.Success
                }
            }
            performNonClientDrag(hwnd, hitTest)
        } catch (t: Throwable) {
            WindowRequestResult.Failure(RequestError.OsError(t.message ?: t::class.simpleName ?: failureMessage))
        }

    private fun isOwnerThread(): Boolean {
        val current = currentWin32ThreadId()
        return current == 0 || ownerThreadId == 0 || current == ownerThreadId
    }

    private fun syncSystemMenuState(menu: MemorySegment) {
        try {
            fun state(enabled: Boolean): Int = if (enabled) MFS_ENABLED else MFS_DISABLED
            val maximized = isMaximized
            val resizable = isResizable
            EnableMenuItem(menu, SC_RESTORE, MF_BYCOMMAND or state(maximized && resizable))
            EnableMenuItem(menu, SC_MOVE, MF_BYCOMMAND or state(!maximized))
            EnableMenuItem(menu, SC_SIZE, MF_BYCOMMAND or state(!maximized && resizable))
            EnableMenuItem(menu, SC_MINIMIZE, MF_BYCOMMAND or MFS_ENABLED)
            EnableMenuItem(menu, SC_MAXIMIZE, MF_BYCOMMAND or state(!maximized && resizable))
            EnableMenuItem(menu, SC_CLOSE, MF_BYCOMMAND or MFS_ENABLED)
            SetMenuDefaultItem(menu, SC_CLOSE, 0)
        } catch (_: Throwable) {}
    }

    private fun ResizeDirection.toWin32HitTest(): Long =
        when (this) {
            ResizeDirection.East -> HTRIGHT
            ResizeDirection.North -> HTTOP
            ResizeDirection.NorthEast -> HTTOPRIGHT
            ResizeDirection.NorthWest -> HTTOPLEFT
            ResizeDirection.South -> HTBOTTOM
            ResizeDirection.SouthEast -> HTBOTTOMRIGHT
            ResizeDirection.SouthWest -> HTBOTTOMLEFT
            ResizeDirection.West -> HTLEFT
        }

    @Volatile private var _theme: Theme? = attrs.preferredTheme

    override val theme: Theme?
        get() = Win32ThemeHelper.systemThemeFromRegistry() ?: _theme

    override fun setTheme(theme: Theme?) {
        _theme = theme
        try {
            Win32ThemeHelper.setWindowDarkMode(hwnd, theme ?: Win32ThemeHelper.systemThemeFromRegistry())
        } catch (_: Throwable) {}
    }

    override fun setWindowLevel(level: WindowLevel) {
        try {
            SetWindowPos(
                hwnd, win32WindowLevelInsertAfter(level),
                0, 0, 0, 0,
                SWP_NOSIZE or SWP_NOMOVE or SWP_NOACTIVATE,
            )
        } catch (_: Throwable) {}
    }

    override fun setTransparent(transparent: Boolean) {
        try {
            val exStyle = try {
                GetWindowLongPtrW(hwnd, GWL_EXSTYLE)
            } catch (_: Throwable) { 0L }
            val newStyle = if (transparent) exStyle or WS_EX_LAYERED.toLong()
                           else exStyle and WS_EX_LAYERED.toLong().inv()
            SetWindowLongPtrW(hwnd, GWL_EXSTYLE, newStyle)
            if (transparent) {
                setLayeredWindowAttributes?.invokeExact(hwnd, 0, 255.toByte(), 0x2) as? Int
            }
        } catch (_: Throwable) {}
    }

    /**
     * No-op on Win32.
     *
     * DWM window blur (DwmEnableBlurBehindWindow) is deprecated since Windows 8
     * and has no visual effect on Windows 10/11. winit treats this as a no-op.
     */
    override fun setBlur(blur: Boolean) {
        if (!win32RuntimeBlurRequiresNativeUpdate(blur)) return
    }

    // -- Platform extensions --

    internal fun setSystemBackdrop(backdrop: SystemBackdrop): WindowRequestResult =
        win32ApplyDwmAttribute(hwnd, DWMWA_SYSTEMBACKDROP_TYPE, backdrop.toDwmValue())

    internal fun setCornerPreference(preference: CornerPreference): WindowRequestResult =
        win32ApplyDwmAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, preference.toDwmValue())

    internal fun setBorderColor(color: Long?): WindowRequestResult =
        win32ApplyDwmAttribute(hwnd, DWMWA_BORDER_COLOR, (color ?: -1L).toInt())

    internal fun setTitleBackgroundColor(color: Long?): WindowRequestResult =
        win32ApplyDwmAttribute(hwnd, DWMWA_CAPTION_COLOR, (color ?: -1L).toInt())

    internal fun setTitleTextColor(color: Long?): WindowRequestResult =
        win32ApplyDwmAttribute(hwnd, DWMWA_TEXT_COLOR, (color ?: -1L).toInt())

    internal fun setSkipTaskbar(skip: Boolean): WindowRequestResult = try {
        val exStyle = GetWindowLongPtrW(hwnd, GWL_EXSTYLE)
        val newStyle = if (skip) {
            (exStyle and WS_EX_APPWINDOW.toLong().inv()) or WS_EX_TOOLWINDOW.toLong()
        } else {
            (exStyle and WS_EX_TOOLWINDOW.toLong().inv()) or WS_EX_APPWINDOW.toLong()
        }
        SetWindowLongPtrW(hwnd, GWL_EXSTYLE, newStyle)
        SetWindowPos(
            hwnd, MemorySegment.NULL, 0, 0, 0, 0,
            SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_FRAMECHANGED or SWP_NOACTIVATE,
        )
        WindowRequestResult.Success
    } catch (t: Throwable) {
        WindowRequestResult.Failure(
            RequestError.OsError(t.message ?: t::class.simpleName ?: "Win32 skip taskbar failed")
        )
    }

    internal fun setUndecoratedShadow(show: Boolean): WindowRequestResult = try {
        val handle = dwmExtendFrameIntoClientArea ?: return WindowRequestResult.Failure(
            RequestError.Unsupported("DwmExtendFrameIntoClientArea is unavailable")
        )
        Arena.ofConfined().use { arena ->
            val margins = arena.allocate(16L, 4L)
            if (show) {
                margins.set(ValueLayout.JAVA_INT, 0L, -1)
                margins.set(ValueLayout.JAVA_INT, 4L, -1)
                margins.set(ValueLayout.JAVA_INT, 8L, -1)
                margins.set(ValueLayout.JAVA_INT, 12L, -1)
            } else {
                margins.set(ValueLayout.JAVA_INT, 0L, 0)
                margins.set(ValueLayout.JAVA_INT, 4L, 0)
                margins.set(ValueLayout.JAVA_INT, 8L, 0)
                margins.set(ValueLayout.JAVA_INT, 12L, 0)
            }
            val hr = handle.invokeExact(hwnd, margins) as Int
            if (hr >= 0) WindowRequestResult.Success
            else WindowRequestResult.Failure(
                RequestError.OsError("DwmExtendFrameIntoClientArea returned HRESULT=$hr")
            )
        }
    } catch (t: Throwable) {
        WindowRequestResult.Failure(
            RequestError.OsError(t.message ?: t::class.simpleName ?: "Win32 undecorated shadow failed")
        )
    }

    internal fun setEnabled(enabled: Boolean): WindowRequestResult = try {
        val ok = EnableWindow(hwnd, if (enabled) 1 else 0)
        if (ok != 0) WindowRequestResult.Success
        else WindowRequestResult.Failure(RequestError.OsError("EnableWindow failed"))
    } catch (t: Throwable) {
        WindowRequestResult.Failure(
            RequestError.OsError(t.message ?: t::class.simpleName ?: "Win32 enable window failed")
        )
    }

    override fun setWindowIcon(icon: Icon?) {
        var newHandle = MemorySegment.NULL
        try {
            if (icon != null) {
                newHandle = win32CreateIcon(hInstance, icon) ?: return
            }
            synchronized(iconLock) {
                SendMessageW(hwnd, WM_SETICON, ICON_SMALL, newHandle.address())
                val oldHandle = ownedWindowIconHandle
                ownedWindowIconHandle = newHandle
                win32DestroyIcon(oldHandle)
                newHandle = MemorySegment.NULL
            }
        } catch (_: Throwable) {}
        finally {
            win32DestroyIcon(newHandle)
        }
    }

    override fun setContentProtected(protected: Boolean): WindowRequestResult =
        try {
            val setAffinity = setWindowDisplayAffinity ?: return WindowRequestResult.Failure(
                RequestError.Unsupported("Win32 SetWindowDisplayAffinity is unavailable"),
            )
            val affinity = if (protected) WDA_EXCLUDEFROMCAPTURE else WDA_NONE
            val ok = setAffinity.invokeExact(hwnd, affinity) as Int
            if (ok == 0) {
                WindowRequestResult.Failure(RequestError.OsError("SetWindowDisplayAffinity failed"))
            } else {
                WindowRequestResult.Success
            }
        } catch (t: Throwable) {
            WindowRequestResult.Failure(RequestError.OsError(t.message ?: t::class.simpleName ?: "Win32 content protection failed"))
        }

    override fun requestUserAttention(requestType: UserAttentionType?): WindowRequestResult =
        try {
            val flash = flashWindowEx ?: return WindowRequestResult.Failure(
                RequestError.Unsupported("Win32 FlashWindowEx is unavailable"),
            )
            val active = getActiveWindow?.invokeExact() as? MemorySegment
            if (active != null && active.address() == hwnd.address()) {
                return WindowRequestResult.Success
            }
            val (flags, count) = when (requestType) {
                UserAttentionType.Critical -> (FLASHW_ALL or FLASHW_TIMERNOFG) to -1
                UserAttentionType.Informational -> (FLASHW_TRAY or FLASHW_TIMERNOFG) to 0
                null -> FLASHW_STOP to 0
            }
            Arena.ofConfined().use { arena ->
                val info = arena.allocate(FLASHWINFO_SIZE, FLASHWINFO_ALIGN)
                info.set(ValueLayout.JAVA_INT, FLASHWINFO_CB_SIZE_OFFSET, FLASHWINFO_SIZE.toInt())
                info.set(ValueLayout.ADDRESS, FLASHWINFO_HWND_OFFSET, hwnd)
                info.set(ValueLayout.JAVA_INT, FLASHWINFO_FLAGS_OFFSET, flags)
                info.set(ValueLayout.JAVA_INT, FLASHWINFO_COUNT_OFFSET, count)
                info.set(ValueLayout.JAVA_INT, FLASHWINFO_TIMEOUT_OFFSET, 0)
                flash.invokeExact(info) as Int
                WindowRequestResult.Success
            }
        } catch (t: Throwable) {
            WindowRequestResult.Failure(RequestError.OsError(t.message ?: t::class.simpleName ?: "Win32 user attention failed"))
        }

    // -- R4: keyboard --

    override fun resetDeadKeys() {
        try {
            val handle = toUnicode ?: return
            Arena.ofConfined().use { arena ->
                val keyState = arena.allocate(256L, 1L)
                getKeyboardState?.invoke(keyState)
                val buf = arena.allocate(16L, 2L)
                handle.invokeExact(0x20, 0x39, keyState, buf, 8, 0) as Int
            }
        } catch (_: Throwable) {}
    }

    // -- IME --

    override fun setImeAllowed(allowed: Boolean) {
        val associate = immAssociateContextEx ?: return
        try {
            if (allowed) {
                associate.invokeExact(hwnd, MemorySegment.NULL, IACE_DEFAULT) as Int
            } else {
                associate.invokeExact(hwnd, MemorySegment.NULL, IACE_CHILDREN) as Int
            }
        } catch (_: Throwable) {}
    }

    override fun setImeCursorArea(position: PhysicalPosition<Int>, size: PhysicalSize<Int>) {
        val getCtx = immGetContext ?: return
        val relCtx = immReleaseContext ?: return
        val setComp = immSetCompositionWindow ?: return
        val setCand = immSetCandidateWindow ?: return
        val hwndSeg = hwnd
        val himc: MemorySegment = try {
            getCtx.invokeExact(hwndSeg) as MemorySegment
        } catch (_: Throwable) { MemorySegment.NULL }
        if (himc == MemorySegment.NULL) return
        try {
            java.lang.foreign.Arena.ofConfined().use { arena ->
                val cf = arena.allocate(COMPOSITIONFORM_SIZE, COMPOSITIONFORM_ALIGN)
                cf.set(ValueLayout.JAVA_INT, COMPOSITIONFORM_OFFSET_DWSTYLE, CFS_POINT)
                cf.set(ValueLayout.JAVA_INT, COMPOSITIONFORM_OFFSET_PT_X, position.x)
                cf.set(ValueLayout.JAVA_INT, COMPOSITIONFORM_OFFSET_PT_Y, position.y + size.height)
                cf.set(ValueLayout.JAVA_INT, COMPOSITIONFORM_OFFSET_RC_LEFT, position.x)
                cf.set(ValueLayout.JAVA_INT, COMPOSITIONFORM_OFFSET_RC_TOP, position.y)
                cf.set(ValueLayout.JAVA_INT, COMPOSITIONFORM_OFFSET_RC_RIGHT, position.x + size.width)
                cf.set(ValueLayout.JAVA_INT, COMPOSITIONFORM_OFFSET_RC_BOTTOM, position.y + size.height)
                setComp.invokeExact(himc, cf) as Int

                val cand = arena.allocate(CANDIDATEFORM_SIZE, CANDIDATEFORM_ALIGN)
                cand.set(ValueLayout.JAVA_INT, CANDIDATEFORM_OFFSET_DWINDEX, 0)
                cand.set(ValueLayout.JAVA_INT, CANDIDATEFORM_OFFSET_DWSTYLE, CFS_EXCLUDE)
                cand.set(ValueLayout.JAVA_INT, CANDIDATEFORM_OFFSET_PT_X, position.x)
                cand.set(ValueLayout.JAVA_INT, CANDIDATEFORM_OFFSET_PT_Y, position.y)
                cand.set(ValueLayout.JAVA_INT, CANDIDATEFORM_OFFSET_RC_LEFT, position.x)
                cand.set(ValueLayout.JAVA_INT, CANDIDATEFORM_OFFSET_RC_TOP, position.y)
                cand.set(ValueLayout.JAVA_INT, CANDIDATEFORM_OFFSET_RC_RIGHT, position.x + size.width)
                cand.set(ValueLayout.JAVA_INT, CANDIDATEFORM_OFFSET_RC_BOTTOM, position.y + size.height)
                setCand.invokeExact(himc, cand) as Int
            }
        } catch (_: Throwable) {
        } finally {
            try { relCtx.invokeExact(hwndSeg, himc) as Int } catch (_: Throwable) {}
        }
    }

    override fun setImePurpose(purpose: ImePurpose) {
        val setConversion = immSetConversionStatus ?: return
        val getCtx = immGetContext ?: return
        val relCtx = immReleaseContext ?: return
        val hwndSeg = hwnd
        val himc: MemorySegment = try {
            getCtx.invokeExact(hwndSeg) as MemorySegment
        } catch (_: Throwable) { MemorySegment.NULL }
        if (himc == MemorySegment.NULL) return
        try {
            val (conversion, sentence) = when (purpose) {
                ImePurpose.Normal    -> IME_CMODE_NATIVE to IME_SMODE_NONE
                ImePurpose.Password  -> IME_CMODE_ALPHANUMERIC to IME_SMODE_NONE
                ImePurpose.Terminal  -> IME_CMODE_ALPHANUMERIC to IME_SMODE_NONE
            }
            setConversion.invokeExact(himc, conversion, sentence) as Int
        } catch (_: Throwable) {
        } finally {
            try { relCtx.invokeExact(hwndSeg, himc) as Int } catch (_: Throwable) {}
        }
    }

    // -- Companion --

    companion object {

        private const val CLASS_NAME = "KadreWin32Window"

        internal fun performNonClientDrag(hwnd: MemorySegment, hitTest: Long): WindowRequestResult =
            try {
                ReleaseCapture()
                val ok = PostMessageW(hwnd, WM_NCLBUTTONDOWN, hitTest, currentCursorLParam())
                if (ok == 0) {
                    WindowRequestResult.Failure(RequestError.OsError("PostMessageW(WM_NCLBUTTONDOWN) failed"))
                } else {
                    WindowRequestResult.Success
                }
            } catch (t: Throwable) {
                WindowRequestResult.Failure(
                    RequestError.OsError(t.message ?: t::class.simpleName ?: "Win32 native move/resize drag failed"),
                )
            }

        private fun currentCursorLParam(): Long =
            try {
                Arena.ofConfined().use { arena ->
                    val point = arena.allocate(POINT_SIZE, POINT_ALIGN)
                    val ok = GetCursorPos(point)
                    if (ok == 0) return@use 0L
                    val x = point.get(ValueLayout.JAVA_INT, POINT_OFFSET_X)
                    val y = point.get(ValueLayout.JAVA_INT, POINT_OFFSET_Y)
                    ((y.toLong() and 0xffffL) shl 16) or (x.toLong() and 0xffffL)
                }
            } catch (_: Throwable) {
                0L
            }

        private val classRegistered = AtomicBoolean(false)

        @Volatile
        private var wndProcStub: MemorySegment? = null

        private fun registerClassOnce(hInstance: MemorySegment, classNamePtr: MemorySegment) {
            if (!classRegistered.compareAndSet(false, true)) return

            val wndProcMH = MethodHandles.lookup().findStatic(
                Win32Window::class.java,
                "wndProc",
                MethodType.methodType(Long::class.java, MemorySegment::class.java, Int::class.java, Long::class.java, Long::class.java)
            )

            val wndProcDesc = FunctionDescriptor.of(
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_LONG,
            )

            val linker = Linker.nativeLinker()
            val stub = linker.upcallStub(wndProcMH, wndProcDesc, Win32WndProcArena.arena)
            wndProcStub = stub

            Arena.ofConfined().use { arena ->
                val wndClass = WndClassExW(arena)
                wndClass.cbSize = WndClassExW.SIZEOF
                wndClass.style = CS_HREDRAW_VREDRAW
                wndClass.lpfnWndProc = stub
                wndClass.cbClsExtra = 0
                wndClass.cbWndExtra = 0
                wndClass.hInstance = hInstance
                wndClass.hIcon = MemorySegment.NULL
                wndClass.hCursor = LoadCursorW(MemorySegment.NULL, MemorySegment.ofAddress(IDC_ARROW))
                wndClass.hbrBackground = MemorySegment.NULL
                wndClass.lpszMenuName = MemorySegment.NULL
                wndClass.lpszClassName = classNamePtr
                wndClass.hIconSm = MemorySegment.NULL

                val atom = RegisterClassExW(wndClass.segment)
                if (atom.toInt() == 0) {
                    classRegistered.set(false)
                    wndProcStub = null
                    error("RegisterClassExW failed (atom = 0)")
                }
            }
        }

        @JvmStatic
        fun wndProc(hwnd: MemorySegment, msg: Int, wParam: Long, lParam: Long): Long {
            return KadreWndProc.dispatch(hwnd.address(), msg, wParam, lParam)
        }

        fun create(attrs: WindowAttributes): Win32Window? {
            init()
            val hInstance = GetModuleHandleW(MemorySegment.NULL)
            if (hInstance == MemorySegment.NULL) return null

            val classArena = Win32WndProcArena.arena
            val classNamePtr = classArena.allocateWString(CLASS_NAME)

            registerClassOnce(hInstance, classNamePtr)

            val width = attrs.size?.width ?: 800
            val height = attrs.size?.height ?: 600
            val posX = attrs.position?.x ?: 100
            val posY = attrs.position?.y ?: 100

            val baseStyle = if (attrs.decorations) {
                WS_OVERLAPPEDWINDOW and (if (attrs.resizable) Int.MAX_VALUE else WS_THICKFRAME.inv())
            } else {
                0x80000000.toInt()
            }
            val buttonStyle = win32StyleWithEnabledButtons(baseStyle, attrs.enabledButtons, attrs.decorations)

            val hwnd: MemorySegment = Arena.ofConfined().use { arena ->
                val titlePtr = arena.allocateWString(attrs.title)
                CreateWindowExW(
                    win32InitialExtendedStyle(attrs.transparent).toLong(),
                    classNamePtr,
                    titlePtr,
                    buttonStyle.toLong(),
                    posX,
                    posY,
                    width,
                    height,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    hInstance,
                    MemorySegment.NULL,
                )
            }

            if (hwnd == MemorySegment.NULL) return null

            val window = Win32Window(hwnd, hInstance, attrs, currentWin32ThreadId())
            Win32FocusState.register(hwnd.address())
            window.syncConstraints()
            window.applyEnabledButtons(attrs.enabledButtons)
            window.setWindowLevel(attrs.windowLevel)
            attrs.windowIcon?.let(window::setWindowIcon)
            if (attrs.transparent) {
                window.setTransparent(true)
                enableWin32TransparentBlurBehind(hwnd)
            }

            RegisterTouchWindow(hwnd, 0L)

            dragAcceptFiles?.invoke(hwnd, 1)

            if (attrs.visible) {
                val showCmd = if (attrs.maximized) SW_MAXIMIZE else SW_SHOW
                ShowWindow(hwnd, showCmd)
                UpdateWindow(hwnd)
            }

            return window
        }
    }
}

internal object Win32FocusState {
    private data class State(
        @Volatile var active: Boolean = false,
        @Volatile var focused: Boolean = false,
    )

    private val states = ConcurrentHashMap<Long, State>()

    fun register(hwnd: Long) {
        states.putIfAbsent(hwnd, State())
    }

    fun unregister(hwnd: Long) {
        states.remove(hwnd)
    }

    @Synchronized
    fun setActive(hwnd: Long, active: Boolean): Boolean? {
        val state = state(hwnd)
        val previous = state.hasActiveFocus
        state.active = active
        val current = state.hasActiveFocus
        return current.takeIf { previous != current }
    }

    @Synchronized
    fun setFocused(hwnd: Long, focused: Boolean): Boolean? {
        val state = state(hwnd)
        val previous = state.hasActiveFocus
        state.focused = focused
        val current = state.hasActiveFocus
        return current.takeIf { previous != current }
    }

    @Synchronized
    fun hasActiveFocus(hwnd: Long): Boolean {
        val state = states[hwnd] ?: return false
        return state.hasActiveFocus
    }

    private fun state(hwnd: Long): State =
        states.computeIfAbsent(hwnd) { State() }

    private val State.hasActiveFocus: Boolean
        get() = active && focused
}

internal fun cursorIdcResource(cursor: CursorIcon): Long = when (cursor) {
    CursorIcon.Default        -> IDC_ARROW
    CursorIcon.Pointer        -> IDC_HAND
    CursorIcon.Text           -> IDC_IBEAM
    CursorIcon.Crosshair      -> IDC_CROSS
    CursorIcon.Move           -> IDC_SIZEALL
    CursorIcon.ResizeNorth, CursorIcon.ResizeSouth, CursorIcon.NsResize, CursorIcon.RowResize -> IDC_SIZENS
    CursorIcon.ResizeEast, CursorIcon.ResizeWest, CursorIcon.EwResize, CursorIcon.ColResize -> IDC_SIZEWE
    CursorIcon.ResizeNorthEast, CursorIcon.ResizeSouthWest, CursorIcon.NeswResize -> IDC_SIZENESW
    CursorIcon.ResizeNorthWest, CursorIcon.ResizeSouthEast, CursorIcon.NwseResize -> IDC_SIZENWSE
    CursorIcon.NotAllowed     -> IDC_NO
    CursorIcon.Grab, CursorIcon.Grabbing -> IDC_SIZEALL
    CursorIcon.Wait           -> IDC_WAIT
    CursorIcon.Progress       -> IDC_APPSTARTING
    CursorIcon.AllScroll      -> IDC_SIZEALL
    CursorIcon.ZoomIn, CursorIcon.ZoomOut -> IDC_CROSS
    CursorIcon.Copy, CursorIcon.Alias -> IDC_ARROW
    CursorIcon.ContextMenu    -> IDC_ARROW
    CursorIcon.Cell           -> IDC_CROSS
    CursorIcon.NoDrop         -> IDC_NO
    CursorIcon.Help           -> IDC_HELP
    CursorIcon.Hidden         -> IDC_ARROW
    CursorIcon.NoneReset      -> IDC_ARROW
    CursorIcon.WaitCursor     -> IDC_WAIT
    CursorIcon.VerticalText   -> IDC_IBEAM
}

internal const val WIN32_STYLE_UPDATE_FLAGS: Int =
    SWP_NOSIZE or SWP_NOMOVE or SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED

internal fun win32CloseMenuState(enabled: Boolean): Int =
    MF_BYCOMMAND or if (enabled) MFS_ENABLED else MFS_DISABLED

internal fun win32ShouldFocusWindow(
    isVisible: Boolean,
    isMinimized: Boolean,
    isForeground: Boolean,
): Boolean =
    isVisible && !isMinimized && !isForeground

internal fun win32InitialExtendedStyle(transparent: Boolean): Int =
    if (transparent) {
        WS_EX_APPWINDOW or WS_EX_LAYERED
    } else {
        WS_EX_APPWINDOW
    }

internal fun win32TransparentBlurBehindFlags(): Int =
    DWM_BB_ENABLE or DWM_BB_BLURREGION

@Suppress("UNUSED_PARAMETER")
internal fun win32RuntimeBlurRequiresNativeUpdate(blur: Boolean): Boolean = false

internal fun enableWin32TransparentBlurBehind(hwnd: MemorySegment): Boolean {
    val enableBlur = dwmEnableBlurBehindWindow ?: return false
    var region = MemorySegment.NULL
    return try {
        region = CreateRectRgn(0, 0, -1, -1)
        if (region == MemorySegment.NULL) return false
        Arena.ofConfined().use { arena ->
            val blurBehind = arena.allocate(DWM_BLURBEHIND_SIZE, DWM_BLURBEHIND_ALIGN)
            blurBehind.set(ValueLayout.JAVA_INT, DWM_BLURBEHIND_OFFSET_DW_FLAGS, win32TransparentBlurBehindFlags())
            blurBehind.set(ValueLayout.JAVA_INT, DWM_BLURBEHIND_OFFSET_F_ENABLE, 1)
            blurBehind.set(ValueLayout.ADDRESS, DWM_BLURBEHIND_OFFSET_H_RGN_BLUR, region)
            blurBehind.set(ValueLayout.JAVA_INT, DWM_BLURBEHIND_OFFSET_F_TRANSITION_ON_MAXIMIZED, 0)
            val hr = enableBlur.invokeExact(hwnd, blurBehind) as Int
            hr >= 0
        }
    } catch (_: Throwable) {
        false
    } finally {
        if (region != MemorySegment.NULL) {
            try {
                DeleteObject(region)
            } catch (_: Throwable) {}
        }
    }
}

internal fun win32WindowLevelInsertAfter(level: WindowLevel): MemorySegment =
    when (level) {
        WindowLevel.AlwaysOnTop -> HWND_TOPMOST
        WindowLevel.Normal -> HWND_NOTOPMOST
        WindowLevel.AlwaysOnBottom -> HWND_BOTTOM
    }

internal fun win32StyleWithEnabledButtons(
    style: Int,
    buttons: WindowButtons,
    decorated: Boolean = true,
): Int =
    style
        .withStyleBit(WS_MINIMIZEBOX, decorated && buttons.contains(WindowButtons.MINIMIZE))
        .withStyleBit(WS_MAXIMIZEBOX, decorated && buttons.contains(WindowButtons.MAXIMIZE))

private fun Long.withEnabledWindowButtonStyles(buttons: WindowButtons, decorated: Boolean): Long =
    this
        .withStyleBit(WS_MINIMIZEBOX.toLong(), decorated && buttons.contains(WindowButtons.MINIMIZE))
        .withStyleBit(WS_MAXIMIZEBOX.toLong(), decorated && buttons.contains(WindowButtons.MAXIMIZE))

private fun Int.withStyleBit(bit: Int, enabled: Boolean): Int =
    if (enabled) this or bit else this and bit.inv()

private fun Long.withStyleBit(bit: Long, enabled: Boolean): Long =
    if (enabled) this or bit else this and bit.inv()

private fun forceWindowActive(hwnd: MemorySegment) {
    try {
        val scanCode = MapVirtualKeyW(VK_MENU, MAPVK_VK_TO_VSC)
        if (scanCode != 0) {
            Arena.ofConfined().use { arena ->
                val inputs = arena.allocate(INPUT_SIZE * 2, INPUT_ALIGN)
                fillKeyboardInput(inputs, index = 0, scanCode = scanCode, flags = KEYEVENTF_EXTENDEDKEY)
                fillKeyboardInput(
                    inputs,
                    index = 1,
                    scanCode = scanCode,
                    flags = KEYEVENTF_EXTENDEDKEY or KEYEVENTF_KEYUP,
                )
                SendInput(2, inputs, INPUT_SIZE.toInt())
            }
        }
    } catch (_: Throwable) {
    }
    SetForegroundWindow(hwnd)
}

private fun fillKeyboardInput(inputs: MemorySegment, index: Int, scanCode: Int, flags: Int) {
    val offset = INPUT_SIZE * index
    inputs.set(ValueLayout.JAVA_INT, offset + INPUT_OFFSET_TYPE, INPUT_KEYBOARD)
    inputs.set(ValueLayout.JAVA_SHORT, offset + INPUT_OFFSET_KI_WVK, VK_LMENU.toShort())
    inputs.set(ValueLayout.JAVA_SHORT, offset + INPUT_OFFSET_KI_WSCAN, scanCode.toShort())
    inputs.set(ValueLayout.JAVA_INT, offset + INPUT_OFFSET_KI_DWFLAGS, flags)
    inputs.set(ValueLayout.JAVA_INT, offset + INPUT_OFFSET_KI_TIME, 0)
    inputs.set(ValueLayout.JAVA_LONG, offset + INPUT_OFFSET_KI_DWEXTRAINFO, 0L)
}

internal data class Win32IconBuffers(
    val andMask: ByteArray,
    val bgra: ByteArray,
)

internal fun win32IconBuffers(icon: Icon): Win32IconBuffers? {
    if (icon.width <= 0 || icon.height <= 0) return null
    val pixelCount = icon.width.toLong() * icon.height.toLong()
    val byteCount = pixelCount * 4L
    if (byteCount > Int.MAX_VALUE || icon.rgba.size != byteCount.toInt()) return null

    val andMask = ByteArray(pixelCount.toInt())
    val bgra = ByteArray(byteCount.toInt())
    var source = 0
    var target = 0
    var pixel = 0
    while (source < icon.rgba.size) {
        val red = icon.rgba[source]
        val green = icon.rgba[source + 1]
        val blue = icon.rgba[source + 2]
        val alpha = icon.rgba[source + 3]

        bgra[target] = blue
        bgra[target + 1] = green
        bgra[target + 2] = red
        bgra[target + 3] = alpha
        andMask[pixel] = ((alpha.toInt() and 0xFF) - 255).toByte()

        source += 4
        target += 4
        pixel += 1
    }
    return Win32IconBuffers(andMask = andMask, bgra = bgra)
}

private fun win32CreateIcon(hInstance: MemorySegment, icon: Icon): MemorySegment? {
    val buffers = win32IconBuffers(icon) ?: return null
    return Arena.ofConfined().use { arena ->
        val andMask = arena.allocate(buffers.andMask.size.toLong(), 1L)
        val bgra = arena.allocate(buffers.bgra.size.toLong(), 1L)
        for (index in buffers.andMask.indices) {
            andMask.setAtIndex(ValueLayout.JAVA_BYTE, index.toLong(), buffers.andMask[index])
        }
        for (index in buffers.bgra.indices) {
            bgra.setAtIndex(ValueLayout.JAVA_BYTE, index.toLong(), buffers.bgra[index])
        }
        val handle = CreateIcon(
            hInstance,
            icon.width,
            icon.height,
            1.toByte(),
            32.toByte(),
            andMask,
            bgra,
        )
        handle.takeUnless { it == MemorySegment.NULL }
    }
}

private fun win32DestroyIcon(handle: MemorySegment) {
    if (handle == MemorySegment.NULL) return
    try {
        DestroyIcon(handle)
    } catch (_: Throwable) {}
}

internal fun win32CreateCursorFromImage(image: CursorImage): MemorySegment? {
    if (image.width <= 0 || image.height <= 0) return null
    val pixelCount = image.width.toLong() * image.height.toLong()
    val byteCount = pixelCount * 4L
    if (byteCount > Int.MAX_VALUE || image.rgba.size.toLong() != byteCount) return null

    val andMask = ByteArray(pixelCount.toInt())
    val bgra = ByteArray(byteCount.toInt())
    var source = 0
    var target = 0
    var pixel = 0
    while (source < image.rgba.size) {
        val red = image.rgba[source]
        val green = image.rgba[source + 1]
        val blue = image.rgba[source + 2]
        val alpha = image.rgba[source + 3]
        bgra[target] = blue
        bgra[target + 1] = green
        bgra[target + 2] = red
        bgra[target + 3] = alpha
        andMask[pixel] = ((alpha.toInt() and 0xFF) - 255).toByte()
        source += 4
        target += 4
        pixel += 1
    }

    return Arena.ofConfined().use { arena ->
        val andSeg = arena.allocate(andMask.size.toLong(), 1L)
        val bgraSeg = arena.allocate(bgra.size.toLong(), 1L)
        for (index in andMask.indices) {
            andSeg.setAtIndex(ValueLayout.JAVA_BYTE, index.toLong(), andMask[index])
        }
        for (index in bgra.indices) {
            bgraSeg.setAtIndex(ValueLayout.JAVA_BYTE, index.toLong(), bgra[index])
        }
        val hInstance = try {
            GetModuleHandleW(MemorySegment.NULL)
        } catch (_: Throwable) { return@use null }
        val handle = CreateIcon(
            hInstance,
            image.width,
            image.height,
            1.toByte(),
            32.toByte(),
            andSeg,
            bgraSeg,
        )
        handle.takeUnless { it == MemorySegment.NULL }
    }
}
