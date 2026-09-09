package org.graphiks.kadre.internal.appkit

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RawInputPort
import org.graphiks.kadre.internal.runtime.RawInputPortLease
import org.graphiks.kadre.internal.runtime.RawInputPortLeaseEvent
import java.util.concurrent.atomic.AtomicBoolean

/** One session-owned port into the process-wide AppKit raw-input broker. */
internal class AppKitRawInputPort internal constructor(
    private val broker: AppKitRawInputBroker,
    initialCapability: Capability<Unit>,
) : RawInputPort {
    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private var capability = initialCapability
    private var capabilityObserver: ((Capability<Unit>) -> Unit)? = null

    override val rawInputCapability: Capability<Unit>
        get() = synchronized(lock) { capability }

    override fun installRawInputCapabilityObserver(
        observer: (Capability<Unit>) -> Unit,
    ): AutoCloseable {
        val initial = synchronized(lock) {
            check(capabilityObserver == null) { "raw-input capability observer is already installed" }
            capabilityObserver = observer
            capability
        }
        observer(initial)
        return AutoCloseable {
            synchronized(lock) {
                if (capabilityObserver === observer) capabilityObserver = null
            }
        }
    }

    override suspend fun requestAccess(): KadreResult<RawInputPortLease> {
        if (closed.get()) return KadreResult.Failure(KadreFailure.Closed(org.graphiks.kadre.diagnostics.KadreResourceKind.InputSource))
        return broker.requestAccess(this)
    }

    internal fun publishCapability(value: Capability<Unit>) {
        val observer = synchronized(lock) {
            capability = value
            capabilityObserver
        }
        observer?.invoke(value)
    }

    internal fun isOpen(): Boolean = !closed.get()

    override fun close() {
        if (closed.compareAndSet(false, true)) broker.closePort(this)
    }
}

/** One private broker registration exposed to exactly one portable runtime access. */
internal class AppKitRawInputPortLease internal constructor(
    private val onClose: (AppKitRawInputPortLease) -> Unit,
) : RawInputPortLease {
    private val closed = AtomicBoolean(false)
    private val channel = Channel<RawInputPortLeaseEvent>(Channel.UNLIMITED)

    override val events: Flow<RawInputPortLeaseEvent> = channel.receiveAsFlow()

    internal fun publish(event: RawInputPortLeaseEvent) {
        if (!closed.get()) channel.trySend(event)
    }

    internal fun closeFromBroker() {
        if (closed.compareAndSet(false, true)) channel.close()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                onClose(this)
            } finally {
                channel.close()
            }
        }
    }
}
