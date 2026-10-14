package org.graphiks.kadre.internal.win32

import org.graphiks.kffi.win32.generated.DispatchMessageW
import org.graphiks.kffi.win32.generated.GetLastError
import org.graphiks.kffi.win32.generated.DefWindowProcW
import org.graphiks.kffi.win32.generated.DestroyWindow
import org.graphiks.kffi.win32.generated.IsWindow
import org.graphiks.kffi.win32.generated.MsgWaitForMultipleObjectsEx
import org.graphiks.kffi.win32.generated.PeekMessageW
import org.graphiks.kffi.win32.generated.PostMessageW
import org.graphiks.kffi.win32.generated.TranslateMessage
import org.graphiks.kffi.win32.generated.init
import org.graphiks.kffi.win32.managed.Win32ControlWindow
import org.graphiks.kffi.win32.managed.Win32ManagedWindow
import org.graphiks.kffi.win32.managed.Win32Structs
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment

/**
 * The real KFFI host port. `waitForMessage` bounds the blocking wait with
 * `MsgWaitForMultipleObjectsEx` and drains with `PeekMessageW(PM_REMOVE)`;
 * the canonical `GetMessageW == -1` discipline is proven on the controlled
 * port (spec SN-4), while `WAIT_FAILED` surfaces through the same `Error`
 * result here. The retrieved MSG is retained and dispatched as-is by
 * [translateAndDispatch].
 */
internal class KffiWin32HostPort : Win32NativeHostPort {
    private val createdThread = Thread.currentThread()
    private var controlWindow: Win32ControlWindow? = null
    private var msgArena: Arena? = null
    private var pendingMessage: MemorySegment? = null
    private val topLevelWindows = java.util.concurrent.ConcurrentHashMap<Win32TopLevelWindowHandle, Win32ManagedWindow>()

    override fun isMainThread(): Boolean = Thread.currentThread() == createdThread

    override fun <T> onMainThread(block: () -> T): T {
        check(isMainThread()) {
            "Win32 host main-thread violation: ${Thread.currentThread().name} != ${createdThread.name}"
        }
        return block()
    }

    override fun createControlWindow(
        messageHandler: (id: Int, wParam: Long, lParam: Long) -> Long,
    ): Win32ControlWindowHandle {
        init()
        // Phase 1: the pump consumes wake/stop private messages through
        // waitForMessage before any dispatch, so the wndproc sink defaults
        // every dispatched message to the native default behavior.
        val window = Win32ControlWindow.open { _, _, _, _ -> 0L }
        controlWindow = window
        msgArena = Arena.ofShared()
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
        TranslateMessage(message)
        DispatchMessageW(message)
    }

    override fun destroyControlWindow(hwnd: Long) {
        controlWindow?.close()
        controlWindow = null
        if (IsWindow(MemorySegment.ofAddress(hwnd)) != 0) {
            DestroyWindow(MemorySegment.ofAddress(hwnd))
        }
    }

    override fun createTopLevelWindow(
        title: String,
        decorations: Win32WindowDecorations,
        width: Int,
        height: Int,
        messageHandler: (hwnd: Long, id: Int, wParam: Long, lParam: Long) -> Long,
    ): Win32TopLevelWindowHandle {
        init()
        val window = Win32ManagedWindow.open(title) { hwnd, message, wParam, lParam ->
            messageHandler(hwnd, message, wParam, lParam)
        }
        topLevelWindows[Win32TopLevelWindowHandle(window.hwnd)] = window
        return Win32TopLevelWindowHandle(window.hwnd)
    }

    override fun defWindowProc(hwnd: Long, message: Int, wParam: Long, lParam: Long): Long =
        org.graphiks.kffi.win32.generated.DefWindowProcW(MemorySegment.ofAddress(hwnd), message, wParam, lParam)

    override fun requestCloseWindow(hwnd: Long) {
        PostMessageW(MemorySegment.ofAddress(hwnd), WM_CLOSE, 0L, 0L)
    }

    override fun destroyWindow(hwnd: Long) {
        val entry = topLevelWindows.entries.firstOrNull { it.key.hwnd == hwnd }
        entry?.value?.close()
        topLevelWindows.remove(entry?.key)
    }

    override fun close() {
        controlWindow?.close()
        controlWindow = null
        topLevelWindows.values.forEach { runCatching(it::close) }
        topLevelWindows.clear()
        msgArena?.close()
        msgArena = null
        pendingMessage = null
    }

    private companion object {
        const val WAKE_MESSAGE: Int = 0x8002 // WM_APP + 2, spec D2
        const val STOP_WPARAM: Long = 0x57494E // "WIN" marker distinguishing stop from wake
        const val QS_ALLINPUT: Int = 0x04FF
        const val MWMO_INPUTAVAILABLE: Int = 0x0004
        const val PM_REMOVE: Int = 0x1
        val WAIT_FAILED: Int = -1 // MsgWaitForMultipleObjectsEx failure return
        const val WM_QUIT: Int = 0x0012
        const val WM_CLOSE: Int = 0x0010
    }
}
