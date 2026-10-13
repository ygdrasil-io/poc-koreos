package org.graphiks.kadre.internal.win32

/**
 * The Win32 message loop's three-result discipline (spec D3): a dispatched
 * message, a stop (queued stop request, owner stop or WM_QUIT), or a pump
 * error (`GetMessageW == -1` via the port's `Error`). Idle re-arms the wait.
 */
internal enum class PumpStep { Dispatched, Rearm, Stopped, PumpFailed }

internal class Win32MessagePump(
    private val port: Win32NativeHostPort,
    private val hwnd: Long,
) {
    private val lock = Object()
    private var stopRequested = false
    private var stopped = false
    private var pumpError: Int? = null

    /** Stop is owner-only: called by the session's stop handler on the UI thread; the posted wake releases a blocked wait. */
    public fun requestStop() {
        synchronized(lock) { stopRequested = true }
        port.postWake(hwnd)
    }

    public fun failureReason(): Int? = synchronized(lock) { pumpError }

    public fun runOnce(timeoutMillis: Long = 250): PumpStep {
        synchronized(lock) {
            if (stopped || pumpError != null) {
                return if (pumpError != null) PumpStep.PumpFailed else PumpStep.Stopped
            }
            if (stopRequested) {
                stopped = true
                return PumpStep.Stopped
            }
        }
        return when (val wait = port.waitForMessage(timeoutMillis)) {
            is Win32MessageWait.Message -> {
                when (wait.id) {
                    Win32PumpMessage.StopRequest.ordinal -> {
                        synchronized(lock) { stopped = true }
                        PumpStep.Stopped
                    }

                    // A wake exists only to release a blocked wait so the loop can
                    // re-evaluate its state; it never dispatches on its own.
                    Win32PumpMessage.Wake.ordinal -> synchronized(lock) {
                        if (stopRequested) {
                            stopped = true
                            PumpStep.Stopped
                        } else {
                            PumpStep.Rearm
                        }
                    }

                    else -> {
                        port.translateAndDispatch(wait)
                        PumpStep.Dispatched
                    }
                }
            }

            Win32MessageWait.Quit -> {
                synchronized(lock) { stopped = true }
                PumpStep.Stopped
            }

            is Win32MessageWait.Error -> {
                synchronized(lock) {
                    if (pumpError == null) pumpError = wait.lastError
                }
                PumpStep.PumpFailed
            }

            Win32MessageWait.Idle -> PumpStep.Rearm
        }
    }
}
