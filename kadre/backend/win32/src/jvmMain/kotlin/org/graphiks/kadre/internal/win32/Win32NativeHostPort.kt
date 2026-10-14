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
}
