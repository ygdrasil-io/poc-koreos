package org.graphiks.kadre.internal.appkit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.DisplayPort
import org.graphiks.kadre.internal.runtime.DisplayPortSnapshot
import java.util.concurrent.atomic.AtomicBoolean

/** Native display enumeration and reconfiguration observation, with no session ownership. */
internal interface AppKitDisplayNative : AutoCloseable {
    val enumerationCapability: Capability<Unit>
    fun snapshot(): DisplayPortSnapshot
    fun observeReconfiguration(listener: () -> Unit): AutoCloseable
}

/** Dispatches CoreGraphics callback work away from the native callback stack. */
internal interface AppKitDisplayReconfigurationDispatcher : AutoCloseable {
    fun dispatch(task: () -> Unit)
}

private class CoroutineAppKitDisplayReconfigurationDispatcher : AppKitDisplayReconfigurationDispatcher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun dispatch(task: () -> Unit) {
        scope.launch { task() }
    }

    override fun close() {
        scope.cancel()
    }
}

/** Process-wide AppKit display subscription fan-out. */
internal class AppKitDisplayBroker(
    private val native: AppKitDisplayNative,
    private val reconfigurationDispatcher: AppKitDisplayReconfigurationDispatcher =
        CoroutineAppKitDisplayReconfigurationDispatcher(),
) : AutoCloseable {
    private val lock = Any()
    private val ports = linkedSetOf<AppKitDisplayPort>()
    private val exclusiveObservers = linkedSetOf<ExclusiveObserver>()
    private var observation: AutoCloseable? = null
    private var reconfigurationPending = false
    private var reconfigurationQueued = false
    private var closed = false

    fun openPort(): AppKitDisplayPort = synchronized(lock) {
        check(!closed) { "AppKit display broker is closed" }
        AppKitDisplayPort(this, native.enumerationCapability).also { port ->
            check(ports.add(port)) { "AppKit display port is already registered" }
            if (observation == null) observation = native.observeReconfiguration(::acceptNativeReconfiguration)
        }
    }

    /** Retains the process observer independently from session-owned [DisplayPort] instances. */
    internal fun retainExclusiveObservation(
        observer: (KadreResult<DisplayPortSnapshot>) -> Unit,
    ): KadreResult<AutoCloseable> = synchronized(lock) {
        if (closed) {
            return@synchronized KadreResult.Failure(
                KadreFailure.Closed(org.graphiks.kadre.diagnostics.KadreResourceKind.Display),
            )
        }
        val registration = ExclusiveObserver(observer)
        check(exclusiveObservers.add(registration)) { "exclusive display observer is already registered" }
        try {
            if (observation == null) observation = native.observeReconfiguration(::acceptNativeReconfiguration)
        } catch (_: Exception) {
            exclusiveObservers.remove(registration)
            return@synchronized KadreResult.Failure(displayFailure("observation-exception"))
        } catch (_: LinkageError) {
            exclusiveObservers.remove(registration)
            return@synchronized KadreResult.Failure(displayFailure("observation-exception"))
        }
        val released = AtomicBoolean(false)
        KadreResult.Success(
            AutoCloseable {
                if (released.compareAndSet(false, true)) releaseExclusiveObservation(registration)
            },
        )
    }

    internal fun requestExclusiveSnapshot(): KadreResult<DisplayPortSnapshot> = snapshotResult()

    fun requestSnapshot(port: AppKitDisplayPort): KadreResult<DisplayPortSnapshot> {
        if (!synchronized(lock) { !closed && port in ports && port.isOpen() }) {
            return KadreResult.Failure(KadreFailure.Closed(org.graphiks.kadre.diagnostics.KadreResourceKind.Display))
        }
        return snapshotResult()
    }

    fun closePort(port: AppKitDisplayPort) {
        val toClose = synchronized(lock) {
            ports.remove(port)
            if (ports.isEmpty() && exclusiveObservers.isEmpty()) observation.also { observation = null } else null
        }
        toClose?.close()
    }

    override fun close() {
        val toClose = synchronized(lock) {
            if (closed) return
            closed = true
            exclusiveObservers.clear()
            ports.toList().also { ports.clear() } to observation.also { observation = null }
        }
        toClose.first.forEach(AppKitDisplayPort::closeFromBroker)
        try {
            toClose.second?.close()
        } finally {
            try {
                reconfigurationDispatcher.close()
            } finally {
                native.close()
            }
        }
    }

    private fun acceptNativeReconfiguration() {
        val dispatch = synchronized(lock) {
            if (closed || consumersEmptyLocked()) {
                false
            } else {
                reconfigurationPending = true
                if (reconfigurationQueued) {
                    false
                } else {
                    reconfigurationQueued = true
                    true
                }
            }
        }
        if (dispatch) reconfigurationDispatcher.dispatch(::drainReconfigurations)
    }

    private fun drainReconfigurations() {
        while (true) {
            val shouldReconfigure = synchronized(lock) {
                when {
                    closed || consumersEmptyLocked() -> {
                        reconfigurationPending = false
                        reconfigurationQueued = false
                        false
                    }
                    !reconfigurationPending -> {
                        reconfigurationQueued = false
                        false
                    }
                    else -> {
                        reconfigurationPending = false
                        true
                    }
                }
            }
            if (!shouldReconfigure) return
            reconfigure()
        }
    }

    private fun reconfigure() {
        val result = snapshotResult()
        val targets = synchronized(lock) { ports.toList() to exclusiveObservers.toList() }
        targets.first.forEach { port -> port.publish(result) }
        targets.second.forEach { observer -> observer.listener(result) }
    }

    private fun snapshotResult(): KadreResult<DisplayPortSnapshot> = try {
        KadreResult.Success(native.snapshot())
    } catch (_: Exception) {
        KadreResult.Failure(displayFailure())
    } catch (_: LinkageError) {
        KadreResult.Failure(displayFailure())
    }

    private fun releaseExclusiveObservation(observer: ExclusiveObserver) {
        val toClose = synchronized(lock) {
            exclusiveObservers.remove(observer)
            if (ports.isEmpty() && exclusiveObservers.isEmpty()) observation.also { observation = null } else null
        }
        toClose?.close()
    }

    private fun consumersEmptyLocked(): Boolean = ports.isEmpty() && exclusiveObservers.isEmpty()

    private class ExclusiveObserver(
        val listener: (KadreResult<DisplayPortSnapshot>) -> Unit,
    )

    private fun displayFailure(code: String = "enumeration-exception"): KadreFailure.PlatformFailure = KadreFailure.PlatformFailure(
        KadrePlatform.AppKit,
        "display",
        code,
    )
}

/** One session-owned [DisplayPort] into [AppKitDisplayBroker]. */
internal class AppKitDisplayPort internal constructor(
    private val broker: AppKitDisplayBroker,
    override val enumerationCapability: Capability<Unit>,
) : DisplayPort {
    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private var observer: ((KadreResult<DisplayPortSnapshot>) -> Unit)? = null

    override suspend fun requestSnapshot(): KadreResult<DisplayPortSnapshot> = broker.requestSnapshot(this)

    override fun installSnapshotObserver(
        observer: (KadreResult<DisplayPortSnapshot>) -> Unit,
    ): AutoCloseable {
        synchronized(lock) {
            check(this.observer == null) { "display snapshot observer is already installed" }
            check(!closed.get()) { "display port is closed" }
            this.observer = observer
        }
        return AutoCloseable {
            synchronized(lock) {
                if (this.observer === observer) this.observer = null
            }
        }
    }

    internal fun publish(snapshot: KadreResult<DisplayPortSnapshot>) {
        synchronized(lock) { observer }?.invoke(snapshot)
    }

    internal fun isOpen(): Boolean = !closed.get()

    internal fun closeFromBroker() {
        closed.set(true)
        synchronized(lock) { observer = null }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            synchronized(lock) { observer = null }
            broker.closePort(this)
        }
    }
}
