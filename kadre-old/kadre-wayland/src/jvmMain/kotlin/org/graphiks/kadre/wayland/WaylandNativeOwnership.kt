package org.graphiks.kadre.wayland

import java.util.concurrent.atomic.AtomicBoolean

/** Prevents any Kotlin throwable from crossing an FFM upcall boundary. */
internal inline fun guardWaylandNativeUpcall(
    onNativeFailure: (Throwable) -> Unit,
    action: () -> Unit,
) {
    try {
        action()
    } catch (failure: Throwable) {
        try {
            onNativeFailure(failure)
        } catch (_: Throwable) {
            // The native caller must always regain control, even if routing fails.
        }
    }
}

/**
 * Keeps native listener storage alive until its proxy is known to be destroyed.
 * Any still-live leases are released only after wl_display_disconnect.
 */
internal open class WaylandNativeListenerLifetime {
    private val closed = AtomicBoolean(false)
    private val bindings = linkedSetOf<AutoCloseable>()

    internal open fun register(binding: AutoCloseable): WaylandNativeListenerLease {
        synchronized(bindings) {
            check(!closed.get()) { "Wayland display listener lifetime is already closed" }
            bindings += binding
        }
        return WaylandNativeListenerLease(this, binding)
    }

    internal fun releaseAfterProxyDestroyed(binding: AutoCloseable) {
        val removed = synchronized(bindings) { bindings.remove(binding) }
        if (removed) binding.close()
    }

    internal fun deferUntilDisplayDisconnect(binding: AutoCloseable) {
        val closeNow = synchronized(bindings) {
            if (closed.get()) true else {
                bindings += binding
                false
            }
        }
        if (closeNow) binding.close()
    }

    internal fun closeAfterDisplayDisconnect() {
        if (!closed.compareAndSet(false, true)) return
        val remaining = synchronized(bindings) {
            bindings.toList().also { bindings.clear() }
        }
        runWaylandCleanup(
            primary = null,
            cleanupActions = remaining.map { binding -> binding::close },
        )
    }
}

internal class WaylandNativeListenerLease internal constructor(
    private val lifetime: WaylandNativeListenerLifetime,
    private val binding: AutoCloseable,
) {
    private val released = AtomicBoolean(false)

    fun releaseAfterProxyDestroyed() {
        if (released.compareAndSet(false, true)) {
            lifetime.releaseAfterProxyDestroyed(binding)
        }
    }
}

internal fun WaylandNativeListenerLifetime.registerOrClose(
    binding: AutoCloseable,
): WaylandNativeListenerLease = try {
    register(binding)
} catch (failure: Throwable) {
    runWaylandCleanup(failure, listOf(binding::close))
    throw failure
}

internal fun WaylandNativeListenerLifetime.registerForProxyOrRollback(
    binding: AutoCloseable,
    proxy: Long,
    destroyProxy: (Long) -> Unit,
): WaylandNativeListenerLease = try {
    register(binding)
} catch (registrationFailure: Throwable) {
    var proxyDestroyed = proxy == 0L
    if (!proxyDestroyed) {
        try {
            destroyProxy(proxy)
            proxyDestroyed = true
        } catch (destroyFailure: Throwable) {
            if (destroyFailure !== registrationFailure) {
                registrationFailure.addSuppressed(destroyFailure)
            }
        }
    }
    try {
        if (proxyDestroyed) binding.close()
        else deferUntilDisplayDisconnect(binding)
    } catch (cleanupFailure: Throwable) {
        if (cleanupFailure !== registrationFailure) {
            registrationFailure.addSuppressed(cleanupFailure)
        }
    }
    throw registrationFailure
}

/** Transactional owner used while native discovery has not reached a durable owner. */
internal class WaylandProxyTransaction(
    private val destroyProxy: (Long) -> Unit,
) {
    private val proxies = mutableListOf<Long>()

    fun adopt(proxy: Long): Long {
        if (proxy != 0L) proxies += proxy
        return proxy
    }

    fun release(proxy: Long) {
        proxies.remove(proxy)
    }

    fun rollback(primary: Throwable) {
        val acquired = proxies.asReversed().toList()
        proxies.clear()
        runWaylandCleanup(
            primary = primary,
            cleanupActions = acquired.map { proxy -> { destroyProxy(proxy) } },
        )
    }
}

/**
 * Finalizes a native listener installation without losing either the install
 * failure or a failure while disposing its just-created listener storage.
 */
internal inline fun <T : AutoCloseable> finalizeWaylandListenerInstallation(
    binding: T,
    install: () -> Unit,
): T {
    try {
        install()
        return binding
    } catch (failure: Throwable) {
        runWaylandCleanup(failure, listOf(binding::close))
        throw failure
    }
}
