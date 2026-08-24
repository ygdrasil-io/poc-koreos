package org.graphiks.kadre.internal.appkit

import java.util.concurrent.atomic.AtomicBoolean

internal class AppKitStandaloneOwnership {
    private val owned = AtomicBoolean(false)

    fun tryAcquire(): Lease? = if (owned.compareAndSet(false, true)) Lease(this) else null

    private fun release() {
        check(owned.compareAndSet(true, false)) { "AppKit standalone ownership is not held" }
    }

    internal class Lease internal constructor(
        private val owner: AppKitStandaloneOwnership,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) owner.release()
        }
    }
}

internal object ProcessAppKitStandaloneOwnership {
    val value: AppKitStandaloneOwnership = AppKitStandaloneOwnership()
}
