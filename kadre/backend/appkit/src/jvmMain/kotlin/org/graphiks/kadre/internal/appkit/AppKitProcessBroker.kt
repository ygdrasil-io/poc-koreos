package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.application.ActivationState
import org.graphiks.kadre.application.AttachmentState
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.VisibilityState
import org.graphiks.kadre.internal.runtime.RuntimeHostController
import java.util.concurrent.atomic.AtomicBoolean

internal interface AppKitLifecycleTarget {
    fun updateLifecycle(state: LifecycleState)

    fun detach()
}

internal class AppKitRuntimeHost(
    val controller: RuntimeHostController,
) : AppKitLifecycleTarget {
    override fun updateLifecycle(state: LifecycleState) {
        controller.updateLifecycle(state)
    }

    override fun detach() {
        controller.detach()
    }
}

/** Coordinates process-wide AppKit ownership without retaining a current Kadre session. */
internal class AppKitProcessBroker {
    private val lock = Any()
    private val deliveryLock = Any()
    private val embeddedHosts = linkedSetOf<AppKitLifecycleTarget>()
    private var standaloneOwned = false
    private var terminated = false
    private var lifecycleState: LifecycleState = EMBEDDED_INITIAL_LIFECYCLE

    fun tryAcquireStandalone(): StandaloneLease? = synchronized(lock) {
        if (terminated || standaloneOwned || embeddedHosts.isNotEmpty()) return@synchronized null
        standaloneOwned = true
        StandaloneLease(this)
    }

    fun <T : AppKitLifecycleTarget> createEmbeddedHost(
        factory: (LifecycleState) -> T,
    ): EmbeddedRegistration<T>? = synchronized(deliveryLock) delivery@{
        synchronized(lock) {
            if (terminated || standaloneOwned) {
                null
            } else {
                val host = factory(lifecycleState)
                check(embeddedHosts.add(host)) { "AppKit host is already registered" }
                EmbeddedRegistration(this, host)
            }
        }
    }

    fun accept(signal: AppKitLifecycleSignal) {
        synchronized(deliveryLock) delivery@{
            if (signal == AppKitLifecycleSignal.HostTerminated) {
                val targets = synchronized(lock) {
                    if (terminated) {
                        null
                    } else {
                        terminated = true
                        lifecycleState = DETACHED_LIFECYCLE
                        embeddedHosts.toList().also { embeddedHosts.clear() }
                    }
                }
                if (targets == null) return@delivery
                targets.forEach(AppKitLifecycleTarget::detach)
                return@delivery
            }

            val delivery = synchronized(lock) {
                if (terminated) {
                    null
                } else {
                    val next = lifecycleState.after(signal)
                    if (next == lifecycleState) {
                        null
                    } else {
                        lifecycleState = next
                        next to embeddedHosts.toList()
                    }
                }
            }
            if (delivery == null) return@delivery
            delivery.second.forEach { host -> host.updateLifecycle(delivery.first) }
        }
    }

    private fun releaseStandalone() {
        synchronized(lock) {
            check(standaloneOwned) { "AppKit standalone ownership is not held" }
            standaloneOwned = false
        }
    }

    private fun releaseEmbedded(host: AppKitLifecycleTarget) {
        synchronized(deliveryLock) {
            synchronized(lock) { embeddedHosts.remove(host) }
        }
    }

    internal class StandaloneLease internal constructor(
        private val broker: AppKitProcessBroker,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) broker.releaseStandalone()
        }
    }

    internal class EmbeddedRegistration<T : AppKitLifecycleTarget> internal constructor(
        private val broker: AppKitProcessBroker,
        val host: T,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) broker.releaseEmbedded(host)
        }
    }

    private fun LifecycleState.after(signal: AppKitLifecycleSignal): LifecycleState = when (signal) {
        AppKitLifecycleSignal.BecameActive -> LifecycleState(
            AttachmentState.Attached,
            VisibilityState.Foreground,
            ActivationState.Active,
        )
        AppKitLifecycleSignal.BecameInactive -> LifecycleState(
            AttachmentState.Attached,
            visibility,
            ActivationState.Inactive,
        )
        AppKitLifecycleSignal.DidHide -> LifecycleState(
            AttachmentState.Attached,
            VisibilityState.Background,
            ActivationState.Inactive,
        )
        AppKitLifecycleSignal.DidUnhide -> LifecycleState(
            AttachmentState.Attached,
            VisibilityState.Foreground,
            ActivationState.Inactive,
        )
        AppKitLifecycleSignal.HostTerminated -> error("terminal lifecycle is handled separately")
    }

    private companion object {
        val EMBEDDED_INITIAL_LIFECYCLE: LifecycleState = LifecycleState(
            AttachmentState.Attached,
            VisibilityState.Foreground,
            ActivationState.Active,
        )
        val DETACHED_LIFECYCLE: LifecycleState = LifecycleState(
            AttachmentState.Detached,
            VisibilityState.Background,
            ActivationState.Inactive,
        )
    }
}

internal object ProcessAppKitProcessBroker {
    val value: AppKitProcessBroker = AppKitProcessBroker()
}
