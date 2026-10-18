package org.graphiks.kadre.internal.win32

import org.graphiks.kffi.win32.generated.DefWindowProcW
import org.graphiks.kffi.win32.generated.DestroyWindow
import org.graphiks.kffi.win32.generated.GetClientRect
import org.graphiks.kffi.win32.generated.GetDpiForWindow
import org.graphiks.kffi.win32.generated.GetWindowRect
import org.graphiks.kffi.win32.generated.IsIconic
import org.graphiks.kffi.win32.generated.GetLastError
import org.graphiks.kffi.win32.generated.InvalidateRect
import org.graphiks.kffi.win32.generated.IsWindow
import org.graphiks.kffi.win32.generated.MsgWaitForMultipleObjectsEx
import org.graphiks.kffi.win32.generated.PeekMessageW
import org.graphiks.kffi.win32.generated.PostMessageW
import org.graphiks.kffi.win32.generated.SetProcessDpiAwarenessContext
import org.graphiks.kffi.win32.generated.DispatchMessageW
import org.graphiks.kffi.win32.generated.TranslateMessage
import org.graphiks.kffi.win32.generated.ValidateRect
import org.graphiks.kffi.win32.generated.init
import org.graphiks.kffi.win32.managed.Win32ControlWindow
import org.graphiks.kffi.win32.managed.Win32ManagedWindow
import org.graphiks.kffi.win32.managed.Win32Structs
import org.graphiks.kffi.win32.managed.Win32Keyboard
import org.graphiks.kffi.win32.managed.Win32Pointer
import org.graphiks.kffi.win32.managed.Win32WindowProperties
import org.graphiks.kffi.win32.managed.Win32WindowOperations
import org.graphiks.kffi.win32.managed.Win32TextServices
import org.graphiks.kffi.win32.managed.Win32TextStore
import org.graphiks.kadre.internal.runtime.TextInputPort
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.ModifierKey
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The real KFFI host port. `waitForMessage` bounds the blocking wait with
 * `MsgWaitForMultipleObjectsEx` and drains with `PeekMessageW(PM_REMOVE)`;
 * the canonical `GetMessageW == -1` discipline is proven on the controlled
 * port (spec SN-4), while `WAIT_FAILED` surfaces through the same `Error`
 * result here. The retrieved MSG is retained and dispatched as-is by
 * [translateAndDispatch]. The Per-Monitor V2 DPI context is applied once at
 * first window creation (spec D2).
 */
internal open class KffiWin32HostPort : Win32NativeHostPort {
    private val createdThread = Thread.currentThread()
    @Volatile private var controlWindow: Win32ControlWindow? = null
    private var msgArena: Arena? = null
    private var pendingMessage: MemorySegment? = null
    private val dpiContextApplied = AtomicBoolean(false)
    private val topLevelWindows = ConcurrentHashMap<Win32TopLevelWindowHandle, Win32ManagedWindow>()
    private val features = mutableMapOf<Long, Win32WindowFeatures>()
    private val textPorts = mutableMapOf<Long, TextInputPort>()
    private val nativeTextPorts = mutableMapOf<Long, KffiWin32TextPort>()
    private var textServices: Win32TextServices? = null
    private var textInitializationFailure: KadreFailure? = null
    private val contactServices = KffiWin32ContactServices()
    override val pointerPacketsAvailable: Boolean get() = true
    override fun readPointerBatch(pointerId: Int, maxPointers: Int, maxFrames: Int,
        budget: org.graphiks.kadre.internal.runtime.RuntimePayloadBudget): Win32ContactBatch = contactServices.read(pointerId, maxPointers, maxFrames, budget)
    override fun isPromotedMouseMessage(): Boolean = org.graphiks.kffi.win32.managed.Win32PointerPackets.isPromotedMouseMessage()
    override fun pointerClientPosition(hwnd: Long, x: Int, y: Int): org.graphiks.kadre.surface.LogicalPoint {
        val point = Win32Pointer.screenToClient(hwnd, x, y)
        val scale = readScaleFactor(hwnd)
        return org.graphiks.kadre.surface.LogicalPoint(point.x / scale, point.y / scale)
    }
    override fun createGesturePort(hwnd: Long, maxPointers: Int, onOutput: (Win32GestureOutput) -> Unit): Win32GesturePort = contactServices.open(hwnd, maxPointers, onOutput)
    private val closing = AtomicBoolean(false)
    private val commands = Win32UiCommandQueue(
        ::isMainThread,
        { controlWindow?.let { postWake(it.hwnd) } ?: false },
    )

    override fun isMainThread(): Boolean = Thread.currentThread() == createdThread

    override fun createTextInputPort(hwnd: Long): TextInputPort = onMainThread {
        textPorts.getOrPut(hwnd) {
            if (textServices == null && textInitializationFailure == null) {
                try { textServices = Win32TextServices.open() }
                catch (cause: Throwable) { textInitializationFailure = win32TextFailure("sta-initialization", cause) }
            }
            val services = textServices
            if (services == null) Win32UnavailableTextInputPort(checkNotNull(textInitializationFailure))
            else KffiWin32TextPort(hwnd, this, services).let {
                nativeTextPorts[hwnd] = it
                Win32TextInputPort(it)
            }
        }
    }
    override fun filterTextKey(hwnd: Long, message: Int, wParam: Long, lParam: Long): Boolean =
        textServices?.filterKey(hwnd, message, wParam, lParam) ?: false
    override fun handleTextCharacter(hwnd: Long, message: Int, wParam: Long, lParam: Long): Long? =
        (textPorts[hwnd] as? Win32TextInputPort)?.handleCharacter(message, wParam, lParam)
    override fun setTextFocus(hwnd: Long, focused: Boolean) {
        (textPorts[hwnd] as? Win32TextInputPort)?.setFocused(focused)
    }
    override fun closeTextInput(hwnd: Long) {
        (textPorts.remove(hwnd) as? AutoCloseable)?.close()
        nativeTextPorts.remove(hwnd)
    }
    internal fun textStoreForTesting(hwnd: Long): Win32TextStore? = nativeTextPorts[hwnd]?.lastStore
    internal fun textServicesForTesting(): Win32TextServices = checkNotNull(textServices)

    override fun showWindow(hwnd: Long) { onMainThread { topLevelWindows.entries.firstOrNull { it.key.hwnd == hwnd }?.value?.setVisible(true) } }
    private class PreparedKey(val hwnd: Long, val message: Int, val wParam: Long, val lParam: Long, val input: Win32KeyInput?) {
        var consumed = false
    }
    private var preparedKey: PreparedKey? = null
    internal fun preparedKeyForTesting(): Win32KeyInput? = preparedKey?.input

    override fun readKeyboardInput(hwnd: Long, message: Int, wParam: Long, lParam: Long): Win32KeyInput? {
        check(isMainThread())
        val prepared = preparedKey
        if (prepared != null && !prepared.consumed && prepared.hwnd == hwnd && prepared.message == message &&
            prepared.wParam == wParam && prepared.lParam == lParam) {
            prepared.consumed = true
            return prepared.input
        }
        // SendMessage callbacks have no TranslateMessage step to observe.
        return readKey(wParam, lParam)
    }

    private fun readKey(wParam: Long, lParam: Long): Win32KeyInput? {
        if (wParam !in 0L..255L) return null
        val snapshot = Win32Keyboard.read(wParam.toInt(), lParam) ?: return null
        val modifiers = buildSet {
            if (snapshot.shift) add(ModifierKey.Shift)
            if (snapshot.control) add(ModifierKey.Control)
            if (snapshot.alt) add(ModifierKey.Alt)
            if (snapshot.meta) add(ModifierKey.Meta)
            if (snapshot.capsLock) add(ModifierKey.CapsLock)
            if (snapshot.numLock) add(ModifierKey.NumLock)
        }
        val packet = snapshot.packet
        return Win32KeyInput(packet.virtualKey, packet.scanCode, packet.extended, packet.pressed,
            packet.repeat, packet.repeatCount, KeyboardModifiers(modifiers), snapshot.translation.characters,
            snapshot.translation.deadKey, snapshot.layoutId)
    }

    override fun trackMouseLeave(hwnd: Long) { check(isMainThread()); Win32Pointer.trackLeave(hwnd) }
    override fun cancelMouseLeave(hwnd: Long) { check(isMainThread()); Win32Pointer.cancelLeave(hwnd) }
    override fun readWheelSettings(): Win32WheelSettings {
        check(isMainThread())
        val settings = Win32Pointer.wheelSettings()
        return Win32WheelSettings(settings.verticalLines, settings.horizontalCharacters)
    }

    override fun <T> onMainThread(block: () -> T): T = commands.invoke(block)
    override fun deferWindowMutation(block: () -> Unit, onFailure: (Throwable) -> Unit) = commands.defer(block, onFailure)
    override fun postOnMainThread(block: () -> Unit, onFailure: (Throwable) -> Unit) = commands.post(block, onFailure)
    override fun stopWorkerCommands() = commands.stopWorkerAdmission()

    /**
     * Pose le contexte Per-Monitor V2 une seule fois si le processus n'en a
     * pas déjà un (décision D2 de la phase 3). L'échec n'est pas bloquant :
     * un processus hôte peut avoir posé son propre contexte.
     */
    private fun applyPerMonitorV2Once() {
        init()
        if (dpiContextApplied.compareAndSet(false, true)) {
            val context = MemorySegment.ofAddress(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2)
            if (SetProcessDpiAwarenessContext(context) == 0) {
                GetLastError() // consomme l'erreur : contexte hôte déjà posé ou refusé
            }
        }
    }

    override fun createControlWindow(
        messageHandler: (id: Int, wParam: Long, lParam: Long) -> Long,
    ): Win32ControlWindowHandle {
        check(isMainThread()) { "the control window must be created on the UI owner" }
        init()
        val window = Win32ControlWindow.open { _, message, wParam, lParam ->
            // Native modal loops dispatch the endpoint directly, without
            // returning through waitForMessage. Commands must progress there too.
            if (message == WAKE_MESSAGE) commands.drain()
            messageHandler(message, wParam, lParam)
        }
        controlWindow = window
        return Win32ControlWindowHandle(window.hwnd)
    }

    override fun postWake(hwnd: Long): Boolean =
        PostMessageW(
            MemorySegment.ofAddress(hwnd),
            WAKE_MESSAGE,
            0L,
            0L,
        ) != 0

    override fun requestStopFromAnyone(hwnd: Long) {
        PostMessageW(
            MemorySegment.ofAddress(hwnd),
            Win32ControlWindow.TEST_MESSAGE,
            STOP_WPARAM,
            0L,
        )
    }

    override fun waitForMessage(timeoutMillis: Long): Win32MessageWait {
        check(isMainThread()) { "the Win32 pump must run on its UI owner" }
        commands.drain()
        features.values.toList().forEach { it.pollConfinement() }
        if (closing.get()) return Win32MessageWait.Quit
        init()
        val waitResult = MsgWaitForMultipleObjectsEx(
            0,
            MemorySegment.NULL,
            timeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            QS_ALLINPUT,
            MWMO_INPUTAVAILABLE,
        )
        if (waitResult == WAIT_FAILED) {
            return Win32MessageWait.Error(GetLastError())
        }
        commands.drain()
        if (closing.get()) return Win32MessageWait.Quit
        if (msgArena == null) msgArena = Arena.ofShared()
        if (pendingMessage == null) {
            pendingMessage = checkNotNull(msgArena).allocate(Win32Structs.msg.layout)
        }
        val message = checkNotNull(pendingMessage)
        if (PeekMessageW(message, MemorySegment.NULL, 0, 0, PM_REMOVE) == 0) {
            return Win32MessageWait.Idle
        }
        val id = Win32Structs.msg.getMessage(message)
        val wParam = Win32Structs.msg.getWParam(message)
        return when {
            id == WM_QUIT -> Win32MessageWait.Quit
            id == WAKE_MESSAGE -> Win32MessageWait.Message(Win32PumpMessage.Wake.ordinal, wParam, 0L)
            id == Win32ControlWindow.TEST_MESSAGE && wParam == STOP_WPARAM ->
                Win32MessageWait.Message(Win32PumpMessage.StopRequest.ordinal, wParam, 0L)

            else -> Win32MessageWait.Message(id, wParam, Win32Structs.msg.getLParam(message))
        }
    }

    override fun translateAndDispatch(wait: Win32MessageWait.Message) {
        val message = checkNotNull(pendingMessage) {
            "translateAndDispatch requires a message retrieved by waitForMessage"
        }
        val id = Win32Structs.msg.getMessage(message)
        val previous = preparedKey
        try {
            preparedKey = if (id in 0x100..0x101 || id in 0x104..0x105) PreparedKey(
                Win32Structs.msg.getHwnd(message), id, Win32Structs.msg.getWParam(message),
                Win32Structs.msg.getLParam(message), readKey(Win32Structs.msg.getWParam(message), Win32Structs.msg.getLParam(message)),
            ) else null
            // Capture layout-dependent physical-key values before TSF can change native state.
            if (preparedKey != null && filterTextKey(Win32Structs.msg.getHwnd(message), id,
                    Win32Structs.msg.getWParam(message), Win32Structs.msg.getLParam(message))) return
            TranslateMessage(message)
            DispatchMessageW(message)
        } finally { preparedKey = previous }
    }

    override fun createTopLevelWindow(
        title: String,
        decorations: Win32WindowDecorations,
        width: Int,
        height: Int,
        messageHandler: (hwnd: Long, id: Int, wParam: Long, lParam: Long) -> Long,
    ): Win32TopLevelWindowHandle {
        if (!isMainThread()) return onMainThread {
            createTopLevelWindow(title, decorations, width, height, messageHandler)
        }
        check(!closing.get()) { "Win32 host port is closed" }
        applyPerMonitorV2Once()
        val window = Win32ManagedWindow.open(title) { hwnd, message, wParam, lParam ->
            if (message == WM_MOVE || message == WM_DPICHANGED) (textPorts[hwnd] as? Win32TextInputPort)?.refreshCursor()
            messageHandler(hwnd, message, wParam, lParam)
        }
        topLevelWindows[Win32TopLevelWindowHandle(window.hwnd)] = window
        features[window.hwnd] = Win32WindowFeatures(window.hwnd)
        return Win32TopLevelWindowHandle(window.hwnd)
    }


    override fun requestCloseWindow(hwnd: Long) {
        PostMessageW(MemorySegment.ofAddress(hwnd), WM_CLOSE, 0L, 0L)
    }

    override fun destroyWindow(hwnd: Long) {
        if (!isMainThread()) return onMainThread { destroyWindow(hwnd) }
        closeTextInput(hwnd)
        val entry = topLevelWindows.entries.firstOrNull { it.key.hwnd == hwnd } ?: return
        val owned = features[hwnd]
        try { owned?.beforeDestroy() }
        finally {
            try { entry.value.close() }
            finally { topLevelWindows.remove(entry.key); features.remove(hwnd)?.close() }
        }
    }

    override fun destroyControlWindow(hwnd: Long) {
        if (!isMainThread()) return onMainThread { destroyControlWindow(hwnd) }
        controlWindow?.close()
        controlWindow = null
        if (IsWindow(MemorySegment.ofAddress(hwnd)) != 0) {
            DestroyWindow(MemorySegment.ofAddress(hwnd))
        }
    }

    override fun defWindowProc(hwnd: Long, message: Int, wParam: Long, lParam: Long): Long =
        DefWindowProcW(MemorySegment.ofAddress(hwnd), message, wParam, lParam)

    override fun invalidateRect(hwnd: Long) {
        if (!isMainThread()) return onMainThread { invalidateRect(hwnd) }
        InvalidateRect(MemorySegment.ofAddress(hwnd), MemorySegment.NULL, 0)
    }

    override fun validateRect(hwnd: Long) {
        if (!isMainThread()) return onMainThread { validateRect(hwnd) }
        ValidateRect(MemorySegment.ofAddress(hwnd), MemorySegment.NULL)
    }

    override fun readClientPhysicalSize(hwnd: Long): Pair<Int, Int> {
        if (!isMainThread()) return onMainThread { readClientPhysicalSize(hwnd) }
        init()
        Arena.ofConfined().use { arena ->
            // Windows LONG is signed 32-bit, including on Win64 (LLP64).
            // RECT: left(0), top(4), right(8), bottom(12), total 16 bytes.
            val rect = arena.allocate(16, 4)
            check(
                GetClientRect(MemorySegment.ofAddress(hwnd), rect) != 0,
            ) { "GetClientRect failed for hwnd $hwnd: GetLastError=${GetLastError()}" }
            val left = rect.get(ValueLayout.JAVA_INT, 0)
            val top = rect.get(ValueLayout.JAVA_INT, 4)
            val right = rect.get(ValueLayout.JAVA_INT, 8)
            val bottom = rect.get(ValueLayout.JAVA_INT, 12)
            return Pair(right - left, bottom - top)
        }
    }

    override fun readScaleFactor(hwnd: Long): Double {
        if (!isMainThread()) return onMainThread { readScaleFactor(hwnd) }
        init()
        return GetDpiForWindow(MemorySegment.ofAddress(hwnd)) / 96.0
    }

    override fun readWindow(hwnd: Long): Win32WindowReadback = onMainThread {
        val value = Win32WindowProperties.read(hwnd)
        Win32WindowReadback(value.title,
            PhysicalRect(PhysicalPoint(value.outer.x, value.outer.y), PhysicalSize(value.outer.width, value.outer.height)),
            value.clientWidth to value.clientHeight, value.style, value.dpi / 96.0, value.minimized, value.maximized,
            Win32WindowOperations.readTopmost(hwnd), checkNotNull(features[hwnd]).icon(), Win32WindowOperations.readContentProtection(hwnd))
    }
    override fun setWindowTitle(hwnd: Long, title: String) { check(isMainThread()); Win32WindowProperties.setTitle(hwnd, title) }
    override fun setWindowStyle(hwnd: Long, style: Int) { check(isMainThread()); Win32WindowProperties.setStyle(hwnd, style) }
    override fun setWindowBounds(hwnd: Long, bounds: PhysicalRect, frameChanged: Boolean) {
        check(isMainThread())
        Win32WindowProperties.setBounds(hwnd, bounds.origin.x, bounds.origin.y, bounds.size.width, bounds.size.height, frameChanged)
    }
    override fun outerSizeForClient(hwnd: Long, width: Int, height: Int, style: Int?): Pair<Int, Int> {
        check(isMainThread())
        val size = if (style == null) Win32WindowProperties.outerSize(hwnd, width, height)
            else Win32WindowProperties.outerSize(hwnd, width, height, style)
        return size.width to size.height
    }
    override fun monitorBounds(hwnd: Long): PhysicalRect {
        check(isMainThread())
        val rect = Win32WindowProperties.monitorBounds(hwnd)
        return PhysicalRect(PhysicalPoint(rect.x, rect.y), PhysicalSize(rect.width, rect.height))
    }
    override fun applyTrackLimits(hwnd: Long, lParam: Long, minimum: PhysicalSize?, maximum: PhysicalSize?) {
        check(isMainThread())
        Win32WindowProperties.applyTrackLimits(hwnd, lParam, minimum?.width, minimum?.height, maximum?.width, maximum?.height)
    }

    override fun setWindowLevel(hwnd: Long, floating: Boolean) { check(isMainThread()); Win32WindowOperations.setTopmost(hwnd, floating) }
    override fun setWindowIcon(hwnd: Long, image: org.graphiks.kadre.surface.BinaryImage?) { check(isMainThread()); checkNotNull(features[hwnd]).setIcon(image) }
    override fun setContentProtection(hwnd: Long, enabled: Boolean) { check(isMainThread()); Win32WindowOperations.setContentProtection(hwnd, enabled) }
    override fun requestAttention(hwnd: Long, attention: org.graphiks.kadre.window.WindowAttention) { check(isMainThread()); Win32WindowOperations.requestAttention(hwnd, attention.ordinal) }
    override fun setCursorStyle(hwnd: Long, style: org.graphiks.kadre.surface.CursorStyle) { check(isMainThread()); checkNotNull(features[hwnd]).setCursor(style) }
    override fun applyClientCursor(hwnd: Long) { check(isMainThread()); checkNotNull(features[hwnd]).applyCursor() }
    override fun setPointerConfinement(hwnd: Long, enabled: Boolean): Boolean { check(isMainThread()); return checkNotNull(features[hwnd]).confine(enabled) }
    override fun refreshPointerConfinement(hwnd: Long): Boolean { check(isMainThread()); return checkNotNull(features[hwnd]).refreshConfinement() }
    override fun observeConfinementLoss(hwnd: Long, onLost: () -> Unit) { check(isMainThread()); checkNotNull(features[hwnd]).observeLoss(onLost) }
    override fun beginMoveResize(hwnd: Long, hitTest: Int, clientX: Int, clientY: Int) { check(isMainThread()); Win32WindowOperations.beginMoveResize(hwnd, hitTest, clientX, clientY) }

    /** Diagnostic : GetWindowRect + IsIconic pour le placement réel du natif. */
    internal fun readWindowRectForDiagnostics(hwnd: Long): String {
        init()
        Arena.ofConfined().use { arena ->
            val rect = arena.allocate(16, 4)
            val ok = GetWindowRect(MemorySegment.ofAddress(hwnd), rect)
            val err = if (ok == 0) GetLastError() else 0
            val iconic = IsIconic(MemorySegment.ofAddress(hwnd))
            return "GetWindowRect(ok=$ok err=$err iconic=$iconic) " +
                "l=${rect.get(ValueLayout.JAVA_INT, 0)} " +
                "t=${rect.get(ValueLayout.JAVA_INT, 4)} " +
                "r=${rect.get(ValueLayout.JAVA_INT, 8)} " +
                "b=${rect.get(ValueLayout.JAVA_INT, 12)}"
        }
    }

    /** Drains one message from the thread queue (translate + dispatch). Test/diagnostic helper. */
    internal fun drainOneMessage(timeoutMillis: Long) {
        val wait = waitForMessage(timeoutMillis)
        if (wait is Win32MessageWait.Message) translateAndDispatch(wait)
    }

    override fun close() {
        if (!closing.compareAndSet(false, true)) return
        try {
            onMainThread {
                try {
                    topLevelWindows.keys.toList().forEach { runCatching { destroyWindow(it.hwnd) } }
                    contactServices.close()
                    textServices?.close()
                    textServices = null
                    textPorts.clear(); nativeTextPorts.clear()
                    controlWindow?.close()
                    controlWindow = null
                    msgArena?.close()
                    msgArena = null
                    pendingMessage = null
                } finally { commands.close() }
            }
        } catch (failure: Throwable) {
            commands.close()
            throw failure
        }
    }

    private companion object {
        // DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2 = ((DPI_CONTEXT_HANDLE)-4)
        const val DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2: Long = -4
        const val WAKE_MESSAGE: Int = Win32ControlWindow.COMMAND_MESSAGE
        const val STOP_WPARAM: Long = 0x57494E // "WIN" marker distinguishing stop from wake
        const val QS_ALLINPUT: Int = 0x00FF
        const val MWMO_INPUTAVAILABLE: Int = 0x0004
        const val PM_REMOVE: Int = 0x1
        val WAIT_FAILED: Int = -1 // MsgWaitForMultipleObjectsEx failure return
        const val WM_QUIT: Int = 0x0012
        const val WM_CLOSE: Int = 0x0010
    }
}
