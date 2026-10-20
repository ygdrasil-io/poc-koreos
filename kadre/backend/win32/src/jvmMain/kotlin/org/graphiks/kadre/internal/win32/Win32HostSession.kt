package org.graphiks.kadre.internal.win32

internal class Win32SessionStartException(
    internal val reason: String,
    cause: Throwable? = null,
) : IllegalStateException("win32 host start failed: $reason", cause)

internal object Win32HostLease {
    private val lock = Object()

    /** Process-wide standalone host exclusivity (spec D1), exclusive against embedded sessions. */
    fun acquire(): Boolean = Win32HostOwnership.tryAcquireStandalone()

    fun release() = Win32HostOwnership.releaseStandalone()
}

/**
 * Process-wide ownership registry (phase 12): one standalone host XOR N
 * embedded sessions. The gate requires concurrent embedded sessions to
 * coexist, so embedded uses a counted registry while standalone keeps its
 * exclusive acquisition.
 */
internal object Win32HostOwnership {
    private val lock = Object()
    private var standaloneHeld = false
    private var embedded = 0

    internal fun embeddedSessions(): Int = synchronized(lock) { embedded }

    internal fun tryAcquireStandalone(): Boolean = synchronized(lock) {
        if (standaloneHeld || embedded > 0) false else {
            standaloneHeld = true
            true
        }
    }

    internal fun releaseStandalone() = synchronized(lock) { standaloneHeld = false }

    internal fun tryRegisterEmbedded(): Boolean = synchronized(lock) {
        if (standaloneHeld) false else {
            embedded++
            true
        }
    }

    internal fun releaseEmbedded() = synchronized(lock) {
        check(embedded > 0) { "no embedded session is registered" }
        embedded--
    }

    /** Test isolation only: tests share the process-wide registry. */
    internal fun resetForTesting() = synchronized(lock) {
        standaloneHeld = false
        embedded = 0
    }
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
    private val closed = java.util.concurrent.atomic.AtomicBoolean(false)
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
        if (!closed.compareAndSet(false, true)) return
        port.onMainThread {
            port.destroyControlWindow(controlWindow.hwnd)
        }
    }

    override fun close() = teardown()
}
