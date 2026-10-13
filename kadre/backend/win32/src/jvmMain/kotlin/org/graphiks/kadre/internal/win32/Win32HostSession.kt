package org.graphiks.kadre.internal.win32

internal class Win32SessionStartException(
    internal val reason: String,
    cause: Throwable? = null,
) : IllegalStateException("win32 host start failed: $reason", cause)

internal object Win32HostLease {
    private val lock = Object()
    private var held = false

    /** Process-wide standalone host exclusivity (spec D1). */
    fun acquire(): Boolean = synchronized(lock) {
        if (held) false else {
            held = true
            true
        }
    }

    fun release() = synchronized(lock) { held = false }
}

/**
 * The standalone headless host (spec: architecture de la tranche). The thread
 * that calls [start] is the recorded UI thread (D1); the elevated control
 * window is the message endpoint (D2); the pump implements D3.
 */
internal class Win32HostSession private constructor(
    val port: Win32NativeHostPort,
    val uiThread: Thread,
    val pump: Win32MessagePump,
    val controlWindow: Win32ControlWindowHandle,
) : AutoCloseable {
    public companion object {
        public fun start(port: Win32NativeHostPort): Win32HostSession {
            check(port.isMainThread()) { "Win32HostSession.start must run on the calling UI thread" }
            val window = try {
                port.createControlWindow { _, _, _ -> 0L }
            } catch (failure: Throwable) {
                throw Win32SessionStartException("control-window", failure)
            }
            return Win32HostSession(
                port = port,
                uiThread = Thread.currentThread(),
                pump = Win32MessagePump(port, hwnd = window.hwnd),
                controlWindow = window,
            )
        }
    }

    public fun teardown() {
        port.onMainThread {
            port.destroyControlWindow(controlWindow.hwnd)
        }
    }

    override fun close() = teardown()
}
