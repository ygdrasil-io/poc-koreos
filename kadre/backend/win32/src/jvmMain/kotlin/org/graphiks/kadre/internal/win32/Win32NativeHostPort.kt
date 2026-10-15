package org.graphiks.kadre.internal.win32

internal enum class Win32PumpMessage { Wake, StopRequest }

internal sealed interface Win32MessageWait {
    data class Message(val id: Int, val wParam: Long, val lParam: Long) : Win32MessageWait
    data object Quit : Win32MessageWait
    data class Error(val lastError: Int) : Win32MessageWait
    data object Idle : Win32MessageWait
}

internal class Win32ControlWindowHandle(val hwnd: Long)

internal interface Win32NativeHostPort : AutoCloseable {
    fun isMainThread(): Boolean
    fun <T> onMainThread(block: () -> T): T
    fun postOnMainThread(block: () -> Unit, onFailure: (Throwable) -> Unit)
    fun deferWindowMutation(block: () -> Unit, onFailure: (Throwable) -> Unit)
    fun stopWorkerCommands()
    fun createControlWindow(messageHandler: (id: Int, wParam: Long, lParam: Long) -> Long): Win32ControlWindowHandle
    fun postWake(hwnd: Long): Boolean
    fun requestStopFromAnyone(hwnd: Long)
    fun waitForMessage(timeoutMillis: Long): Win32MessageWait
    fun translateAndDispatch(wait: Win32MessageWait.Message)
    fun destroyControlWindow(hwnd: Long)

    /** Phase 2: real top-level windows. Messages (WM_CLOSE et al.) reach the handler; wake/stop privates stay pump-owned. */
    fun createTopLevelWindow(
        title: String,
        decorations: Win32WindowDecorations,
        width: Int,
        height: Int,
        messageHandler: (hwnd: Long, id: Int, wParam: Long, lParam: Long) -> Long,
    ): Win32TopLevelWindowHandle

    fun requestCloseWindow(hwnd: Long)
    fun destroyWindow(hwnd: Long)

    /** Native default behavior for unhandled messages (real: DefWindowProcW; fake: 0L). */
    fun defWindowProc(hwnd: Long, message: Int, wParam: Long, lParam: Long): Long

    /** Phase 3 surface: invalidation requests and effective-metrics readback. */
    fun invalidateRect(hwnd: Long)
    fun validateRect(hwnd: Long)
    fun readClientPhysicalSize(hwnd: Long): Pair<Int, Int>
    fun readScaleFactor(hwnd: Long): Double

    /** Copied property snapshots; all layouts and native buffers belong to KFFI. */
    fun readWindow(hwnd: Long): Win32WindowReadback
    fun setWindowTitle(hwnd: Long, title: String)
    fun setWindowStyle(hwnd: Long, style: Int)
    fun setWindowBounds(hwnd: Long, bounds: org.graphiks.kadre.surface.PhysicalRect, frameChanged: Boolean)
    fun outerSizeForClient(hwnd: Long, width: Int, height: Int): Pair<Int, Int>
    fun monitorBounds(hwnd: Long): org.graphiks.kadre.surface.PhysicalRect
    fun applyTrackLimits(hwnd: Long, lParam: Long, minimum: org.graphiks.kadre.surface.PhysicalSize?, maximum: org.graphiks.kadre.surface.PhysicalSize?)

    fun showWindow(hwnd: Long)
    fun readKeyboardInput(hwnd: Long, message: Int, wParam: Long, lParam: Long): Win32KeyInput?
    fun trackMouseLeave(hwnd: Long)
    fun cancelMouseLeave(hwnd: Long)
    fun readWheelSettings(): Win32WheelSettings
}

internal data class Win32WindowReadback(
    val title: String,
    val outerBounds: org.graphiks.kadre.surface.PhysicalRect,
    val clientSize: Pair<Int, Int>,
    val style: Int,
    val scale: Double,
    val minimized: Boolean = false,
    val maximized: Boolean = false,
)
