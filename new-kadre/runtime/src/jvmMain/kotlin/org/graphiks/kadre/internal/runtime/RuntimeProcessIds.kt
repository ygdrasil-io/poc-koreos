package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.application.SessionId
import org.graphiks.kadre.window.WindowRequestId
import java.util.concurrent.atomic.AtomicLong

internal object RuntimeProcessIds {
    private val sessionIds = AtomicLong(0L)
    private val windowRequestIds = AtomicLong(0L)

    fun nextSessionId(): SessionId = SessionId(nextValue(sessionIds, "session ID"))

    fun nextWindowRequestId(): WindowRequestId =
        WindowRequestId(nextValue(windowRequestIds, "window request ID"))

    private fun nextValue(source: AtomicLong, name: String): Long {
        while (true) {
            val current = source.get()
            check(current >= 0L && current < Long.MAX_VALUE) { "$name space exhausted" }
            if (source.compareAndSet(current, current + 1L)) return current
        }
    }
}
