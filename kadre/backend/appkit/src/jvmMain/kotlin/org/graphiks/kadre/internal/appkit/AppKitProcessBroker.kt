package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.application.ActivationState
import org.graphiks.kadre.application.AttachmentState
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.VisibilityState
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeHostController
import org.graphiks.kadre.window.WindowAttention
import org.graphiks.kadre.window.WindowId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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
    private val embeddedHosts = linkedMapOf<AppKitLifecycleTarget, AppKitUserAttentionOwner?>()
    private val attentionTokens = linkedMapOf<WindowId, AppKitUserAttentionToken>()
    private val nextAttentionOwnerId = AtomicLong(0L)
    private var standaloneOwned = false
    private var terminated = false
    private var lifecycleState: LifecycleState = EMBEDDED_INITIAL_LIFECYCLE

    fun tryAcquireStandalone(
        attentionOwner: AppKitUserAttentionOwner? = null,
    ): StandaloneLease? = synchronized(lock) {
        if (terminated || standaloneOwned || embeddedHosts.isNotEmpty()) return@synchronized null
        standaloneOwned = true
        StandaloneLease(this, attentionOwner)
    }

    fun newUserAttentionOwner(nativeApplication: AppKitNativeApplication): AppKitUserAttentionOwner =
        AppKitUserAttentionOwner(this, nativeApplication, nextAttentionOwnerId.getAndIncrement())

    fun <T : AppKitLifecycleTarget> createEmbeddedHost(
        factory: (LifecycleState) -> T,
    ): EmbeddedRegistration<T>? = registerEmbeddedHost(null, factory)

    fun <T : AppKitLifecycleTarget> createEmbeddedHost(
        attentionOwner: AppKitUserAttentionOwner,
        factory: (LifecycleState) -> T,
    ): EmbeddedRegistration<T>? = registerEmbeddedHost(attentionOwner, factory)

    private fun <T : AppKitLifecycleTarget> registerEmbeddedHost(
        attentionOwner: AppKitUserAttentionOwner?,
        factory: (LifecycleState) -> T,
    ): EmbeddedRegistration<T>? = synchronized(deliveryLock) delivery@{
        synchronized(lock) {
            if (terminated || standaloneOwned) {
                null
            } else {
                val host = factory(lifecycleState)
                check(embeddedHosts.put(host, attentionOwner) == null) { "AppKit host is already registered" }
                EmbeddedRegistration(this, host, attentionOwner)
            }
        }
    }

    fun requestUserAttention(
        owner: AppKitUserAttentionOwner,
        windowId: WindowId,
        attention: WindowAttention,
    ): KadreResult<Unit> {
        if (attention == WindowAttention.None) {
            releaseUserAttention(owner, windowId)
            return KadreResult.Success(Unit)
        }
        val replaced = synchronized(lock) {
            val previous = attentionTokens[windowId]
            when {
                previous == null -> null
                previous.ownerId == owner.id -> attentionTokens.remove(windowId)
                else -> return KadreResult.Failure(KadreFailure.InvalidRequest("windowId"))
            }
        }
        if (replaced != null && !cancelUserAttention(owner.nativeApplication, replaced.token)) {
            return KadreResult.Failure(userAttentionFailure("cancel-exception"))
        }
        val token = try {
            owner.nativeApplication.requestUserAttention(attention)
        } catch (_: Exception) {
            return KadreResult.Failure(userAttentionFailure("request-exception"))
        } catch (_: LinkageError) {
            return KadreResult.Failure(userAttentionFailure("request-exception"))
        }
        synchronized(lock) {
            attentionTokens[windowId] = AppKitUserAttentionToken(owner.id, token)
        }
        return KadreResult.Success(Unit)
    }

    fun releaseUserAttention(owner: AppKitUserAttentionOwner, windowId: WindowId) {
        val token = synchronized(lock) {
            attentionTokens[windowId]?.takeIf { it.ownerId == owner.id }?.also { attentionTokens.remove(windowId) }
        } ?: return
        cancelUserAttention(owner.nativeApplication, token.token)
    }

    fun releaseAllUserAttention(owner: AppKitUserAttentionOwner) {
        releaseUserAttentionOwner(owner)
    }

    fun hasUserAttention(owner: AppKitUserAttentionOwner, windowId: WindowId? = null): Boolean =
        synchronized(lock) {
            if (windowId == null) {
                attentionTokens.values.any { it.ownerId == owner.id }
            } else {
                attentionTokens[windowId]?.ownerId == owner.id
            }
        }

    private fun releaseUserAttentionOwner(owner: AppKitUserAttentionOwner) {
        val tokens = synchronized(lock) {
            attentionTokens.entries
                .filter { (_, token) -> token.ownerId == owner.id }
                .map { (windowId, token) ->
                    attentionTokens.remove(windowId)
                    token.token
                }
        }
        tokens.forEach { token -> cancelUserAttention(owner.nativeApplication, token) }
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
                targets.forEach { (target, owner) ->
                    try {
                        target.detach()
                    } finally {
                        owner?.close()
                    }
                }
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
                        next to embeddedHosts.keys.toList()
                    }
                }
            }
            if (delivery == null) return@delivery
            delivery.second.forEach { host -> host.updateLifecycle(delivery.first) }
        }
    }

    private fun releaseStandalone(attentionOwner: AppKitUserAttentionOwner?) {
        synchronized(lock) {
            check(standaloneOwned) { "AppKit standalone ownership is not held" }
            standaloneOwned = false
        }
        attentionOwner?.close()
    }

    private fun releaseEmbedded(host: AppKitLifecycleTarget, attentionOwner: AppKitUserAttentionOwner?) {
        synchronized(deliveryLock) {
            synchronized(lock) { embeddedHosts.remove(host) }
        }
        attentionOwner?.close()
    }

    internal class StandaloneLease internal constructor(
        private val broker: AppKitProcessBroker,
        private val attentionOwner: AppKitUserAttentionOwner?,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) broker.releaseStandalone(attentionOwner)
        }
    }

    internal class EmbeddedRegistration<T : AppKitLifecycleTarget> internal constructor(
        private val broker: AppKitProcessBroker,
        val host: T,
        private val attentionOwner: AppKitUserAttentionOwner?,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) broker.releaseEmbedded(host, attentionOwner)
        }
    }

    internal class AppKitUserAttentionOwner internal constructor(
        private val broker: AppKitProcessBroker,
        internal val nativeApplication: AppKitNativeApplication,
        internal val id: Long,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) broker.releaseUserAttentionOwner(this)
        }
    }

    private data class AppKitUserAttentionToken(
        val ownerId: Long,
        val token: Long,
    )

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

        fun userAttentionFailure(code: String): KadreFailure.PlatformFailure = KadreFailure.PlatformFailure(
            KadrePlatform.AppKit,
            "user-attention",
            code,
        )
    }

    private fun cancelUserAttention(nativeApplication: AppKitNativeApplication, token: Long): Boolean = try {
        nativeApplication.cancelUserAttentionRequest(token)
        true
    } catch (_: Exception) {
        false
    } catch (_: LinkageError) {
        false
    }
}

internal object ProcessAppKitProcessBroker {
    val value: AppKitProcessBroker = AppKitProcessBroker()
}
