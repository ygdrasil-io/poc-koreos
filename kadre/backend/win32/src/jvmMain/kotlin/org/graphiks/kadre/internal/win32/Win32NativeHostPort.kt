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
}
