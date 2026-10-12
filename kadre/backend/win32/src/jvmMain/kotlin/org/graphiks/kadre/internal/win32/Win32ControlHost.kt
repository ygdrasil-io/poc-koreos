package org.graphiks.kadre.internal.win32

import org.graphiks.kffi.win32.managed.Win32ControlWindow
import org.graphiks.kffi.win32.managed.Win32WndProcHandler
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Scalar copy of one dispatched window message — no native data is retained. */
internal data class Win32Dispatch(
    val hwnd: Long,
    val message: Int,
    val wParam: Long,
    val lParam: Long,
)

/**
 * Kadre-side owner of the internal control window (spec D3). The OS gate and
 * the native factory are injectable so the actionable-failure and copy rules
 * are provable portably (O2); the real frontier is proven O3 on Windows.
 */
internal class Win32ControlHost(
    private val osGate: () -> Boolean = Win32ControlWindow::isSupported,
    private val nativeFactory: (handler: (Long, Int, Long, Long) -> Long) -> AutoCloseable = { handler ->
        Win32ControlWindow.open(Win32WndProcHandler(handler))
    },
) : AutoCloseable {
    private val lock = ReentrantLock()
    private val recorded = CopyOnWriteArrayList<Win32Dispatch>()
    private var native: AutoCloseable? = null
    private var closed = false

    @Volatile
    private var hwndValue: Long = 0L

    internal val isSupported: Boolean
        get() = osGate()

    internal val hwnd: Long
        get() = hwndValue

    internal val dispatches: List<Win32Dispatch>
        get() = recorded.toList()

    internal val isClosed: Boolean
        get() = lock.withLock { closed }

    internal fun open(handler: (hwnd: Long, message: Int, wParam: Long, lParam: Long) -> Long) {
        check(lock.withLock { !closed }) { "Win32 control host is already closed" }
        check(isSupported) {
            "Win32 control windows require Windows; this platform (" +
                System.getProperty("os.name") + ") is unsupported — no native library was loaded."
        }
        val window = nativeFactory { hwnd, message, wParam, lParam ->
            recorded.add(Win32Dispatch(hwnd, message, wParam, lParam))
            handler(hwnd, message, wParam, lParam)
        }
        lock.withLock {
            native = window
            hwndValue = (window as? Win32ControlWindow)?.hwnd ?: 0L
        }
    }

    internal fun pollOne(timeoutMillis: Long): Boolean {
        val window = lock.withLock {
            if (closed) return false
            native
        } ?: return false
        return (window as? Win32ControlWindow)?.pollOne(timeoutMillis)
            ?: error("Injected native factory does not support polling")
    }

    override fun close() {
        val window = lock.withLock {
            if (closed) return
            closed = true
            native
        } ?: return
        window.close()
    }
}
