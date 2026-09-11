package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.application.ActivationState
import org.graphiks.kadre.application.AttachmentState
import org.graphiks.kadre.application.LifecycleCapabilities
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.MemoryPressureLevel
import org.graphiks.kadre.application.VisibilityState
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeHostController
import org.graphiks.kadre.window.WindowAttention
import org.graphiks.kadre.window.WindowId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private typealias AttentionReleaseDispatcher =
    (() -> List<KadreFailure.PlatformFailure>) -> List<KadreFailure.PlatformFailure>

internal interface AppKitLifecycleTarget {
    fun updateLifecycle(state: LifecycleState)

    fun updateLifecycleCapabilities(capabilities: LifecycleCapabilities) = Unit

    fun emitMemoryPressure(level: MemoryPressureLevel) = Unit

    fun detach()
}

internal class AppKitRuntimeHost(
    val controller: RuntimeHostController,
) : AppKitLifecycleTarget {
    override fun updateLifecycle(state: LifecycleState) {
        controller.updateLifecycle(state)
    }

    override fun updateLifecycleCapabilities(capabilities: LifecycleCapabilities) {
        controller.updateLifecycleCapabilities(capabilities)
    }

    override fun emitMemoryPressure(level: MemoryPressureLevel) {
        controller.emitMemoryPressure(level)
    }

    override fun detach() {
        controller.detach()
    }
}

/** Coordinates process-wide AppKit ownership without retaining a current Kadre session. */
internal class AppKitProcessBroker(
    private val displayBrokerFactory: () -> AppKitDisplayBroker = {
        AppKitDisplayBroker(KffiAppKitDisplayNative())
    },
    private val memoryPressureNative: AppKitMemoryPressureNative? = null,
    private val exclusiveDisplayBridge: AppKitExclusiveDisplayBridge = UnsupportedAppKitExclusiveDisplayBridge,
    private val exclusiveRecoveryExecutor: AppKitExclusiveExecutor = AppKitExclusiveExecutor { task ->
        task()
        true
    },
    private val gameControllerBrokerFactory: () -> AppKitGameControllerBroker = {
        AppKitGameControllerBroker(KffiAppKitGameControllerNativeFactory)
    },
) {
    private val lock = Any()
    private val deliveryLock = Any()
    private val embeddedHosts = linkedMapOf<AppKitLifecycleTarget, AppKitUserAttentionOwner?>()
    private val attentionTokens = linkedMapOf<WindowId, AppKitUserAttentionToken>()
    private val nextAttentionOwnerId = AtomicLong(0L)
    private var standaloneOwned = false
    private var standaloneMemoryTarget: AppKitLifecycleTarget? = null
    private var terminated = false
    private var lifecycleState: LifecycleState = EMBEDDED_INITIAL_LIFECYCLE
    private val rawInputBroker = lazy {
        AppKitRawInputBroker(
            permission = AppKitPermissionBroker(KffiAppKitRawInputNative),
            native = KffiAppKitRawInputNative,
            bridgeAvailable = AppKitRawInputAvailability().isAvailable,
        )
    }
    private val displayBroker = lazy(displayBrokerFactory)
    private val exclusiveDisplayBroker = lazy {
        AppKitExclusiveDisplayBroker(
            bridge = exclusiveDisplayBridge,
            displayBroker = displayBroker.value,
            recoveryExecutor = exclusiveRecoveryExecutor,
        )
    }
    private val gameControllerBroker = lazy(gameControllerBrokerFactory)
    private val memoryPressureBroker = lazy {
        AppKitMemoryPressureBroker(checkNotNull(memoryPressureNative), ::deliverMemoryPressure)
    }

    fun openRawInputPort(): AppKitRawInputPort = rawInputBroker.value.openPort()

    fun openDisplayPort(): AppKitDisplayPort = displayBroker.value.openPort()

    fun openGameControllerPort(): AppKitGameControllerPort? = try {
        gameControllerBroker.value.openPort()
    } catch (_: Exception) {
        null
    } catch (_: LinkageError) {
        null
    }

    fun openExclusiveFullscreenPort(
        executor: AppKitExclusiveExecutor,
        windowPort: AppKitExclusiveWindowPort,
    ): AppKitExclusiveFullscreenPort = exclusiveDisplayBroker.value.openPort(executor, windowPort)

    fun memoryPressureAvailability(): FeatureAvailability = synchronized(lock) {
        if (terminated || memoryPressureNative == null) {
            FeatureAvailability.Unsupported
        } else {
            memoryPressureBroker.value.activate()
        }
    }

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
        if (!owner.isOpen()) {
            return KadreResult.Failure(KadreFailure.Closed(org.graphiks.kadre.diagnostics.KadreResourceKind.Host))
        }
        if (attention == WindowAttention.None) {
            return releaseUserAttention(owner, windowId)
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
        val admitted = synchronized(lock) {
            if (owner.isOpen()) {
                attentionTokens[windowId] = AppKitUserAttentionToken(owner.id, token)
                true
            } else {
                false
            }
        }
        if (admitted) return KadreResult.Success(Unit)
        return if (cancelUserAttention(owner.nativeApplication, token)) {
            KadreResult.Failure(KadreFailure.Closed(org.graphiks.kadre.diagnostics.KadreResourceKind.Host))
        } else {
            KadreResult.Failure(userAttentionFailure("cancel-exception"))
        }
    }

    fun releaseUserAttention(owner: AppKitUserAttentionOwner, windowId: WindowId): KadreResult<Unit> {
        val token = synchronized(lock) {
            attentionTokens[windowId]?.takeIf { it.ownerId == owner.id }?.also { attentionTokens.remove(windowId) }
        } ?: return KadreResult.Success(Unit)
        return if (cancelUserAttention(owner.nativeApplication, token.token)) KadreResult.Success(Unit)
        else KadreResult.Failure(userAttentionFailure("cancel-exception"))
    }

    fun releaseAllUserAttention(owner: AppKitUserAttentionOwner): List<KadreFailure.PlatformFailure> {
        return releaseUserAttentionOwner(owner)
    }

    fun hasUserAttention(owner: AppKitUserAttentionOwner, windowId: WindowId? = null): Boolean =
        synchronized(lock) {
            if (windowId == null) {
                attentionTokens.values.any { it.ownerId == owner.id }
            } else {
                attentionTokens[windowId]?.ownerId == owner.id
            }
        }

    private fun releaseUserAttentionOwner(owner: AppKitUserAttentionOwner): List<KadreFailure.PlatformFailure> {
        val tokens = synchronized(lock) {
            attentionTokens.entries
                .filter { (_, token) -> token.ownerId == owner.id }
                .map { (windowId, token) ->
                    attentionTokens.remove(windowId)
                    token.token
                }
        }
        return tokens.mapNotNull { token ->
            if (cancelUserAttention(owner.nativeApplication, token)) null else userAttentionFailure("cancel-exception")
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
                targets.forEach { (target, owner) ->
                    try {
                        target.detach()
                    } finally {
                        owner?.close()
                    }
                }
                if (rawInputBroker.isInitialized()) rawInputBroker.value.close()
                if (gameControllerBroker.isInitialized()) gameControllerBroker.value.close()
                if (exclusiveDisplayBroker.isInitialized()) exclusiveDisplayBroker.value.close()
                if (displayBroker.isInitialized()) displayBroker.value.close()
                if (memoryPressureBroker.isInitialized()) memoryPressureBroker.value.close()
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
            if (signal == AppKitLifecycleSignal.BecameActive && rawInputBroker.isInitialized()) {
                rawInputBroker.value.reprobeForHostActivation()
            }
        }
    }

    private fun releaseStandalone(attentionOwner: AppKitUserAttentionOwner?) {
        synchronized(lock) {
            check(standaloneOwned) { "AppKit standalone ownership is not held" }
            standaloneOwned = false
            standaloneMemoryTarget = null
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

        fun installMemoryTarget(target: AppKitLifecycleTarget) {
            synchronized(broker.lock) {
                check(!closed.get()) { "AppKit standalone lease is closed" }
                check(broker.standaloneOwned) { "AppKit standalone ownership is not held" }
                check(broker.standaloneMemoryTarget == null) { "AppKit standalone memory target is already installed" }
                broker.standaloneMemoryTarget = target
            }
        }

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
        private val failureReporter = AtomicReference<((KadreFailure.PlatformFailure) -> Unit)?>(null)
        private val releaseDispatcher = AtomicReference<AttentionReleaseDispatcher?>(null)

        fun isOpen(): Boolean = !closed.get()

        fun installFailureReporter(reporter: (KadreFailure.PlatformFailure) -> Unit) {
            check(failureReporter.compareAndSet(null, reporter)) {
                "AppKit user-attention failure reporter is already installed"
            }
        }

        fun installReleaseDispatcher(dispatcher: AttentionReleaseDispatcher) {
            check(releaseDispatcher.compareAndSet(null, dispatcher)) {
                "AppKit user-attention release dispatcher is already installed"
            }
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            if (!broker.hasUserAttention(this)) return
            val release = { broker.releaseUserAttentionOwner(this) }
            val failures = releaseDispatcher.get()?.invoke(release) ?: release()
            failures.forEach { failure ->
                try {
                    failureReporter.get()?.invoke(failure)
                } catch (_: Exception) {
                    // Diagnostics cannot destabilise AppKit ownership cleanup.
                } catch (_: LinkageError) {
                    // Diagnostics cannot destabilise AppKit ownership cleanup.
                }
            }
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

    private fun deliverMemoryPressure(level: MemoryPressureLevel) {
        val targets = synchronized(lock) {
            if (terminated) emptyList()
            else embeddedHosts.keys.toList() + listOfNotNull(standaloneMemoryTarget)
        }
        targets.forEach { target ->
            try {
                target.emitMemoryPressure(level)
            } catch (_: Exception) {
                // A session may race its terminal teardown after source admission.
            } catch (_: LinkageError) {
                // Diagnostics must not let a failing host callback escape the source owner.
            }
        }
    }
}

internal object ProcessAppKitProcessBroker {
    private val displaySource = KffiAppKitDisplayNative()
    val value: AppKitProcessBroker = AppKitProcessBroker(
        displayBrokerFactory = { AppKitDisplayBroker(displaySource) },
        memoryPressureNative = KffiAppKitMemoryPressureNative,
        exclusiveDisplayBridge = KffiAppKitExclusiveDisplayBridge(displaySource),
    )
}
