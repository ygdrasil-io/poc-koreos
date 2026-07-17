package org.graphiks.kadre.win32

/** Preserves the first failure and attaches later, distinct failures in call order. */
internal fun appendWin32Failure(primary: Throwable?, later: Throwable): Throwable {
    if (primary == null) return later
    if (primary !== later && primary.suppressed.none { it === later }) {
        primary.addSuppressed(later)
    }
    return primary
}

internal fun captureWin32Failure(
    primary: Throwable?,
    action: () -> Unit,
): Throwable? = try {
    action()
    primary
} catch (later: Throwable) {
    appendWin32Failure(primary, later)
}

/**
 * Failures thrown by a Win32 FFM upcall cannot cross the native boundary on JDK 25.
 * They are retained here until execution is safely back in the Java message loop.
 */
internal object Win32WndProcFailures {
    private val lock = Any()
    private var pending: Throwable? = null

    fun record(failure: Throwable) {
        synchronized(lock) {
            pending = appendWin32Failure(pending, failure)
        }
    }

    fun take(): Throwable? = synchronized(lock) {
        val failure = pending
        pending = null
        failure
    }

    fun clear() {
        synchronized(lock) {
            pending = null
        }
    }

    fun throwPending() {
        take()?.let { throw it }
    }
}

/** Runs Java-side work with pending-upcall checks on both sides of the boundary. */
internal fun <T> withWndProcFailureCheck(action: () -> T): T {
    Win32WndProcFailures.throwPending()
    return try {
        val result = action()
        Win32WndProcFailures.throwPending()
        result
    } catch (caught: Throwable) {
        val pending = Win32WndProcFailures.take()
        if (pending != null) {
            throw appendWin32Failure(pending, caught)
        }
        throw caught
    }
}
