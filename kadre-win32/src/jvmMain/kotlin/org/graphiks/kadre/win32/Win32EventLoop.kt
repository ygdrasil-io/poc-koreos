/**
 * Win32 implementation of [ActiveEventLoop] and the [runApp] entry point.
 *
 * [Win32EventLoop] implements [ActiveEventLoop] and is passed to every
 * [ApplicationHandler] callback. The [runApp] method orchestrates
 * Win32 initialization (KadreWndProc registration) and the message
 * loop with dynamic switching based on [ControlFlow]:
 *
 * - [ControlFlow.Poll]      → PeekMessageW (PM_REMOVE) — non-blocking
 * - [ControlFlow.Wait]      → GetMessageW — blocks until the next message
 * - [ControlFlow.WaitUntil] → MsgWaitForMultipleObjectsEx with a timeout in ms
 *
 * Lazy FFM pattern (tryCreate): all MethodHandles are null on macOS/Linux,
 * which lets the build pass on all platforms.
 *
 * GRA-11: Win32EventLoop — Win32 message loop with ControlFlow switching.
 */
package org.graphiks.kadre.win32

import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.CursorImage
import org.graphiks.kadre.core.CustomCursor
import org.graphiks.kadre.core.DeviceEvents
import org.graphiks.kadre.core.EventLoopProxy
import org.graphiks.kadre.core.MonitorHandle
import org.graphiks.kadre.core.OwnedDisplayHandle
import org.graphiks.kadre.core.RawDisplayHandle
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.Theme
import org.graphiks.kadre.core.Window
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.graphiks.kadre.ffi.win32.*
import org.graphiks.kadre.ffi.win32.generated.*

// ── Single-instance lock ──────────────────────────────────────────────────────

/**
 * Global lock guaranteeing that only one Win32 event loop is active
 * at a time in the process.
 *
 * Uses AtomicBoolean for thread-safety: [runApp] performs an atomic CAS
 * false→true at startup and throws [IllegalStateException] if already true.
 */
internal val win32Running = AtomicBoolean(false)

// ── Win32EventLoop ────────────────────────────────────────────────────────────

/**
 * Internal implementation of [ActiveEventLoop] for the Win32 platform (Windows).
 *
 * One instance is created per call to [runApp] and passed as the receiver
 * to all [ApplicationHandler] callbacks.
 *
 * ### Lifecycle
 * ```
 * runApp(handler)
 *   └─ handler.resumed(this)
 *   └─ handler.canCreateSurfaces(this)
 *   └─ message loop
 *        ├─ handler.newEvents(this, cause)
 *        ├─ pump messages according to ControlFlow
 *        └─ handler.aboutToWait(this)
 *   └─ handler.suspended(this)
 *   └─ handler.destroySurfaces(this)
 * ```
 *
 * ### Thread-safety
 * - [_controlFlow] is @Volatile: readable from any thread.
 * - [_isExiting] is @Volatile: readable from any thread.
 * - [windows] is a ConcurrentHashMap.
 * - The message loop itself runs on the calling thread.
 */
internal class Win32EventLoop(
    private val postQuitMessage: (Int) -> Unit = { exitCode -> PostQuitMessage(exitCode) },
) : ActiveEventLoop {

    /** Live windows: windowId (HWND address) → Win32Window. */
    internal val windows = ConcurrentHashMap<Long, Win32Window>()

    /**
     * Cached HINSTANCE for the current process, obtained from GetModuleHandleW(NULL).
     * Zero if the binding is unavailable (non-Windows).
     */
    private val _hinstance: Long by lazy {
        try {
            GetModuleHandleW(MemorySegment.NULL).address()
        } catch (_: Throwable) {
            0L
        }
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
     * Creates a new native Win32 window and registers it in the window table.
     *
     * Also installs the [KadreWndProc] handler if not already done.
     *
     * @param attributes Window configuration attributes.
     * @return The created window.
     * @throws IllegalStateException if the Win32 bindings are not available
     *         (macOS/Linux) or if window creation fails.
     */
    override fun createWindow(attributes: WindowAttributes): Window {
        val window = Win32Window.create(attributes)
            ?: error(
                "Win32Window.create() returned null — Win32 (user32.dll) bindings are not available on this platform."
            )
        windows[window.id.value] = window
        return window
    }

    /**
     * Creates a window with Win32-specific attributes.
     *
     * Merges [Win32WindowAttributes] fields into the core [WindowAttributes]
     * and applies platform-specific settings at creation time.
     */
    fun createWindow(attrs: Win32WindowAttributes): Window {
        val window = Win32Window.create(attrs.core)
            ?: error(
                "Win32Window.create() returned null — Win32 (user32.dll) bindings are not available on this platform."
            )
        windows[window.id.value] = window
        // Apply platform extension settings
        if (attrs.skipTaskbar) window.setSkipTaskbar(true)
        if (attrs.undecoratedShadow) window.setUndecoratedShadow(true)
        attrs.systemBackdrop?.let { window.setSystemBackdrop(it) }
        attrs.cornerPreference?.let { window.setCornerPreference(it) }
        attrs.borderColor?.let { window.setBorderColor(it) }
        attrs.titleBackgroundColor?.let { window.setTitleBackgroundColor(it) }
        attrs.titleTextColor?.let { window.setTitleTextColor(it) }
        if (!attrs.enabled) window.setEnabled(false)
        return window
    }

    /**
     * Requests the Win32 event loop to stop.
     *
     * Sets [_isExiting] then calls PostQuitMessage(0) to insert WM_QUIT
     * into the message queue, which causes GetMessageW to return.
     */
    override fun exit() {
        _isExiting = true
        postQuitMessage(0)
    }

    internal fun deliverWindowEvent(
        handler: ApplicationHandler,
        hwnd: Long,
        event: WindowEvent,
        removeWindow: (Long) -> Unit = { windows.remove(it) },
        windowsEmpty: () -> Boolean = windows::isEmpty,
    ) {
        if (event !is WindowEvent.Destroyed) {
            handler.windowEvent(this, WindowId(hwnd), event)
            return
        }

        var failure: Throwable? = null
        failure = captureWin32Failure(failure) {
            handler.windowEvent(this, WindowId(hwnd), event)
        }
        failure = captureWin32Failure(failure) { removeWindow(hwnd) }

        var empty = false
        failure = captureWin32Failure(failure) { empty = windowsEmpty() }
        if (empty && !isExiting) {
            failure = captureWin32Failure(failure) { exit() }
        }

        failure?.let { throw it }
    }

    /**
     * Creates a thread-safe proxy to this event loop.
     *
     * The proxy uses PostThreadMessageW(WM_NULL) to wake the loop
     * from a secondary thread.
     */
    override fun createProxy(): EventLoopProxy = Win32EventLoopProxy.create()

    // ── Task 23: ownedDisplayHandle ───────────────────────────────────────────

    override fun ownedDisplayHandle(): OwnedDisplayHandle? {
        val hinstance = _hinstance
        if (hinstance == 0L) return null
        return OwnedDisplayHandle(RawDisplayHandle.Win32(hinstance = hinstance))
    }

    // ── R2: monitor enumeration ───────────────────────────────────────────────

    /**
     * Returns all connected monitors via EnumDisplayMonitors + GetMonitorInfoW.
     *
     * Returns an empty list on non-Windows platforms (graceful no-op).
     */
    override fun availableMonitors(): List<MonitorHandle> = enumerateWin32Monitors()

    /**
     * Returns the primary monitor (dwFlags & MONITORINFOF_PRIMARY), or null.
     */
    override fun primaryMonitor(): MonitorHandle? =
        enumerateWin32Monitors().firstOrNull { it.isPrimary }

    // ── R3: system theme ──────────────────────────────────────────────────────

    /**
     * Returns the current system theme by reading the registry.
     *
     * Reads HKCU\...\Personalize\AppsUseLightTheme via Win32 RegGetValueW.
     * Returns null if the key is absent or the call fails.
     */
    override fun systemTheme(): Theme? = Win32ThemeHelper.systemThemeFromRegistry()

    // ── R4: device event filter ───────────────────────────────────────────────

    /**
     * No-op on Win32: device events are always dispatched regardless of focus.
     *
     * A proper implementation would require Raw Input (RIDEV_INPUTSINK / RIDEV_REMOVE)
     * to globally enable/disable raw input. Out of scope for R4.
     */
    override fun listenDeviceEvents(mode: DeviceEvents) {
        // no-op on Win32: device event filtering not implemented
    }

    // ── R5-CustomCursor ─────────────────────────────────────────────────────────

    /**
     * Creates a custom cursor from RGBA pixel data on Win32.
     *
     * Converts RGBA → BGRA + inverted-alpha AND mask and calls CreateIcon.
     * On Win32, HICON handles are interchangeable with HCURSOR handles.
     */
    override fun createCustomCursor(image: CursorImage): CustomCursor? {
        val hCursor = win32CreateCursorFromImage(image) ?: return null
        return CustomCursor(id = hCursor.address())
    }

    // ── Message loop ──────────────────────────────────────────────────────────

    /**
     * Starts the Win32 message loop.
     *
     * Must be called from the main Windows thread (the thread that created
     * the windows). Blocking — returns only when the application closes.
     *
     * @param handler Lifecycle and event handler.
     */
    internal fun runMessageLoop(handler: ApplicationHandler) = withWndProcFailureCheck {
        // Confined arena for the MSG segment — freed when the loop exits
        Arena.ofConfined().use { arena ->
            val msg = arena.allocateMsg()
            var startCause: StartCause = StartCause.Init

            while (!_isExiting) {
                // Notify the handler of the start of the iteration
                handler.newEvents(this, startCause)
                if (_isExiting) break

                // Dispatch the messages according to the current ControlFlow
                startCause = dispatchMessages(msg, handler)

                // Notify the handler that the loop is about to wait
                handler.aboutToWait(this)
            }
        }
    }

    /**
     * Dispatches Win32 messages according to the current [ControlFlow].
     *
     * @param msg    Pre-allocated MSG memory segment.
     * @param handler Event handler (to route window events).
     * @return The [StartCause] of the next iteration.
     */
    private fun dispatchMessages(msg: MemorySegment, handler: ApplicationHandler): StartCause {
        return when (val cf = _controlFlow) {
            is ControlFlow.Poll -> dispatchPoll(msg, handler)
            is ControlFlow.Wait -> dispatchWait(msg, handler)
            is ControlFlow.WaitUntil -> dispatchWaitUntil(msg, handler, cf.instant)
        }
    }

    /**
     * Poll mode: PeekMessageW (PM_REMOVE) — non-blocking.
     *
     * Drains the message queue in one pass (processes all available messages)
     * and returns immediately even if the queue is empty.
     */
    private fun dispatchPoll(msg: MemorySegment, handler: ApplicationHandler): StartCause {
        while (!_isExiting) {
            val hasMsg = PeekMessageW(
                msg,
                MemorySegment.NULL,  // hWnd: NULL = all thread messages
                0,                   // wMsgFilterMin: no filter
                0,                   // wMsgFilterMax: no filter
                PM_REMOVE,           // wRemoveMsg: remove from the queue
            )
            if (hasMsg == 0) break  // queue empty, exit the pump
            TranslateMessage(msg)
            DispatchMessageW(msg)
            Win32WndProcFailures.throwPending()
        }
        return StartCause.Poll
    }

    /**
     * Wait mode: GetMessageW — blocks until the next message.
     *
     * Blocks the thread until a message is received (or WM_QUIT).
     * Returns false (StartCause.WaitCancelled) if WM_QUIT is received.
     */
    private fun dispatchWait(msg: MemorySegment, handler: ApplicationHandler): StartCause {
        val result = GetMessageW(
            msg,
            MemorySegment.NULL,  // hWnd: NULL = all thread messages
            0,                   // wMsgFilterMin
            0,                   // wMsgFilterMax
        )

        when {
            result > 0 -> {
                // Normal message → translate + dispatch
                TranslateMessage(msg)
                DispatchMessageW(msg)
                Win32WndProcFailures.throwPending()
            }
            result == 0 -> {
                // WM_QUIT → clean exit
                _isExiting = true
            }
            // result < 0: error — ignore and continue
        }
        return StartCause.WaitCancelled()
    }

    /**
     * WaitUntil mode: MsgWaitForMultipleObjectsEx with a timeout.
     *
     * Computes the remaining timeout until [targetInstant] (in ms since the Unix epoch)
     * and waits for either a message or the timeout to expire.
     *
     * @param targetInstant Target instant in milliseconds since the Unix epoch.
     */
    private fun dispatchWaitUntil(
        msg: MemorySegment,
        handler: ApplicationHandler,
        targetInstant: Long,
    ): StartCause {
        val now = System.currentTimeMillis()
        val timeoutMs = (targetInstant - now).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

        val result = MsgWaitForMultipleObjectsEx(
            0L,                   // nCount: no kernel object to wait on
            MemorySegment.NULL,  // pHandles: NULL because nCount = 0
            timeoutMs.toLong(),  // dwMilliseconds: computed timeout
            QS_ALLINPUT.toLong(), // dwWakeMask: all input messages
            MWMO_INPUTAVAILABLE.toLong(), // dwFlags: wake up if messages already available
        )

        when (result) {
            WAIT_OBJECT_0.toLong() -> {
                // Messages available → pump with PeekMessageW
                while (!_isExiting) {
                    val hasMsg = PeekMessageW(
                        msg,
                        MemorySegment.NULL,
                        0, 0,
                        PM_REMOVE,
                    )
                    if (hasMsg == 0) break
                    TranslateMessage(msg)
                    DispatchMessageW(msg)
                    Win32WndProcFailures.throwPending()
                }
                return StartCause.WaitCancelled(targetInstant)
            }
            WAIT_TIMEOUT.toLong() -> {
                // Timeout expired → target instant reached
                return StartCause.ResumeTimeReached(
                    requestedResume = targetInstant,
                    start = System.currentTimeMillis(),
                )
            }
            else -> {
                // Other return code (error or unexpected signal)
                return StartCause.WaitCancelled(targetInstant)
            }
        }
    }
}

// ── Entry point ───────────────────────────────────────────────────────────────

/**
 * Entry point for the kadre event loop on Windows.
 *
 * Initializes Win32 (installs KadreWndProc), creates a [Win32EventLoop],
 * calls [ApplicationHandler.resumed], then starts the blocking message loop.
 * Returns only when the application closes.
 *
 * Must be called from the main Windows thread (the message thread).
 *
 * @param handler Lifecycle and event handler.
 * @throws IllegalStateException if a Win32 loop is already active in this process.
 */
fun runApp(handler: ApplicationHandler) {
    runApp(handler) { eventLoop, loopHandler ->
        eventLoop.runMessageLoop(loopHandler)
    }
}

internal fun runApp(
    handler: ApplicationHandler,
    messageLoop: (Win32EventLoop, ApplicationHandler) -> Unit,
) {
    check(win32Running.compareAndSet(false, true)) {
        "Win32EventLoop.runApp() can only be called once per process. A Win32 event loop is already active."
    }

    try {
        // Enable Per-Monitor-V2 before any window creation, otherwise Windows
        // virtualizes the DPI and makes content blurry on high-density displays.
        enablePerMonitorV2DpiAwareness()

        val eventLoop = Win32EventLoop()

        // Install the KadreWndProc handler to route messages to the windows.
        KadreWndProc.install { hwnd, event ->
            eventLoop.deliverWindowEvent(handler, hwnd, event)
        }

        var failure: Throwable? = null
        try {
            withWndProcFailureCheck { handler.resumed(eventLoop) }
            withWndProcFailureCheck { handler.canCreateSurfaces(eventLoop) }
            withWndProcFailureCheck { messageLoop(eventLoop, handler) }
        } catch (throwable: Throwable) {
            failure = throwable
        }

        failure = captureLifecycleFailure(failure) {
            withWndProcFailureCheck { handler.suspended(eventLoop) }
        }
        failure = captureLifecycleFailure(failure) {
            withWndProcFailureCheck { handler.destroySurfaces(eventLoop) }
        }

        failure?.let { throw it }
    } finally {
        KadreWndProc.uninstall()
        Win32WndProcFailures.clear()
        win32Running.set(false)
    }
}

private inline fun captureLifecycleFailure(
    primary: Throwable?,
    callback: () -> Unit,
): Throwable? = try {
    callback()
    primary
} catch (later: Throwable) {
    appendWin32Failure(primary, later)
}
