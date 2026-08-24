package org.graphiks.kadre.internal.runtime

import kotlin.time.Duration
import kotlin.time.TimeSource

internal fun interface RuntimeClock {
    fun elapsedNow(): Duration
}

internal fun interface RuntimeClockFactory {
    fun start(): RuntimeClock
}

internal object MonotonicRuntimeClockFactory : RuntimeClockFactory {
    override fun start(): RuntimeClock {
        val origin = TimeSource.Monotonic.markNow()
        return RuntimeClock(origin::elapsedNow)
    }
}
