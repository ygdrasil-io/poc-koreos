package org.graphiks.kadre.internal.win32

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.input.RawInputUnit
import org.graphiks.kadre.internal.runtime.RawInputPort
import org.graphiks.kadre.internal.runtime.RawInputPortInput
import org.graphiks.kadre.internal.runtime.RawInputPortLease
import org.graphiks.kadre.internal.runtime.RawInputPortLeaseEvent
import java.util.concurrent.atomic.AtomicBoolean

/** Native registration frontier behind an injectable seam, provable without Windows. */
internal interface Win32RawInputNative {
    /** True when this process already holds a raw-input registration (foreign ownership). */
    public fun hasForeignRegistration(): Boolean

    /** Installs the one mouse registration; the returned owner only clears the admission state. */
    public fun register(
        targetHwnd: Long,
        onMotion: (deltaX: Int, deltaY: Int, device: Long) -> Unit,
        onFailure: (Throwable) -> Unit,
    ): AutoCloseable
}

/** Broker-owned session port; mirrors the AppKit raw-input port one for one. */
internal class Win32RawInputPort internal constructor(
    private val broker: Win32RawInputBroker,
    initialCapability: Capability<Unit>,
) : RawInputPort {
    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private var capability = initialCapability
    private var capabilityObserver: ((Capability<Unit>) -> Unit)? = null

    override val rawInputCapability: Capability<Unit>
        get() = synchronized(lock) { capability }

    override fun installRawInputCapabilityObserver(observer: (Capability<Unit>) -> Unit): AutoCloseable {
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
        if (closed.get()) return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.InputSource))
        return broker.requestAccess(this)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        broker.closePort(this)
    }

    internal fun publishCapability(value: Capability<Unit>) {
        val observer = synchronized(lock) {
            capability = value
            capabilityObserver
        }
        observer?.invoke(value)
    }

    internal fun isOpen(): Boolean = !closed.get()
}

/** One admitted raw-input access; events flow only to this lease. */
internal class Win32RawInputPortLease internal constructor(
    private val onClose: (Win32RawInputPortLease) -> Unit,
) : RawInputPortLease {
    private val closed = AtomicBoolean(false)
    private val channel = Channel<RawInputPortLeaseEvent>(Channel.UNLIMITED)

    override val events: Flow<RawInputPortLeaseEvent> = channel.receiveAsFlow()

    internal fun publish(event: RawInputPortLeaseEvent) {
        channel.trySend(event)
    }

    internal fun closeFromBroker() {
        if (!closed.compareAndSet(false, true)) return
        channel.close()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        onClose(this)
    }
}

/**
 * The process raw-input broker: one mouse registration for the whole process,
 * fanned out to every admitted lease. A foreign preexisting registration is
 * never stolen — the typed refusal keeps raw input honestly unavailable. The
 * native registration is process-lifetime: the OS refuses removal for the
 * mouse usage, so close only drops the broker's own admission state.
 */
internal class Win32RawInputBroker internal constructor(
    private val native: Win32RawInputNative,
    private val bridgeAvailable: Boolean = true,
) : AutoCloseable {
    private val lock = Any()
    private val ports = linkedSetOf<Win32RawInputPort>()
    private val registrations = linkedMapOf<Win32RawInputPortLease, Win32RawInputPort>()
    private var registration: AutoCloseable? = null
    private var registrationFailed: KadreFailure? = null
    private var closed = false
    private var capability: Capability<Unit> = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RawInputAccess))

    internal fun openPort(): Win32RawInputPort = synchronized(lock) {
        check(!closed) { "Win32 raw-input broker is closed" }
        Win32RawInputPort(this, capability).also { port ->
            check(ports.add(port)) { "Win32 raw-input port is already registered" }
        }
    }

    internal suspend fun requestAccess(port: Win32RawInputPort): KadreResult<RawInputPortLease> {
        if (!isOpen(port)) return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.InputSource))
        if (!bridgeAvailable) return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RawInputAccess))

        val failure = synchronized(lock) { registrationFailed }
        if (failure != null) {
            publishCapability(Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RawInputAccess)))
            publishRegistrationAvailability(FeatureAvailability.Unavailable(failure))
            return KadreResult.Failure(failure)
        }

        val lease = synchronized(lock) {
            if (closed || port !in ports || !port.isOpen()) return@synchronized null
            Win32RawInputPortLease { candidate -> closeRegistration(candidate) }.also { registration ->
                check(registrations.put(registration, port) == null) { "duplicate raw-input registration" }
            }
        }
        if (lease == null) return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.InputSource))
        publishCapability(Capability.Supported(Unit, FeatureAvailability.Available))
        return KadreResult.Success(lease)
    }

    /** Fans one copied native motion out to every admitted lease; never ordinary input. */
    internal fun onNativeMotion(deltaX: Int, deltaY: Int, device: Long) {
        val targets = synchronized(lock) { registrations.keys.toList() }
        val input = RawInputPortInput(deltaX.toDouble(), deltaY.toDouble(), RawInputUnit.DeviceCount, null)
        targets.forEach { lease -> lease.publish(RawInputPortLeaseEvent.Input(input)) }
    }

    /**
     * Moves (or installs) the process registration onto the activated window.
     * Before the very first install a foreign registration is refused once and
     * remembered; afterwards re-targeting is owner arbitration, not stealing.
     */
    internal fun activateOn(hwnd: Long) {
        if (hwnd == 0L) return
        val failure = synchronized(lock) {
            if (closed) return
            if (registration != null) {
                if (currentHwnd == hwnd) return
                registrationFailed
            } else {
                if (native.hasForeignRegistration()) {
                    KadreFailure.PlatformFailure(KadrePlatform.Win32, "raw-input", "foreign-registration")
                        .also { registrationFailed = it }
                } else null
            }
        }
        if (failure != null) {
            publishCapability(Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RawInputAccess)))
            publishRegistrationAvailability(FeatureAvailability.Unavailable(failure))
            return
        }
        try {
            val owner = native.register(hwnd, ::onNativeMotion) { }
            synchronized(lock) {
                registration = owner
                currentHwnd = hwnd
            }
            publishCapability(Capability.Supported(Unit, FeatureAvailability.Available))
        } catch (ignored: Throwable) {
            synchronized(lock) {
                if (registrationFailed == null) {
                    registrationFailed = KadreFailure.PlatformFailure(KadrePlatform.Win32, "raw-input", "registration-failed")
                }
            }
        }
    }

    private var currentHwnd: Long = 0L

    internal fun closePort(port: Win32RawInputPort) {
        val stale = synchronized(lock) {
            ports.remove(port)
            registrations.filterValues { it === port }.keys.toList().also { entries ->
                entries.forEach(registrations::remove)
            }
        }
        stale.forEach(Win32RawInputPortLease::closeFromBroker)
    }

    private fun closeRegistration(lease: Win32RawInputPortLease) {
        synchronized(lock) { registrations.remove(lease) }
        lease.closeFromBroker()
    }

    override fun close() {
        val stale = synchronized(lock) {
            closed = true
            val leases = registrations.keys.toList()
            registrations.clear()
            ports.clear()
            leases
        }
        stale.forEach(Win32RawInputPortLease::closeFromBroker)
    }

    private fun publishCapability(value: Capability<Unit>) {
        synchronized(lock) { capability = value }
        synchronized(lock) { ports.toList() }.forEach { it.publishCapability(value) }
    }

    private fun publishRegistrationAvailability(availability: FeatureAvailability) {
        synchronized(lock) { registrations.keys.toList() }
            .forEach { it.publish(RawInputPortLeaseEvent.Availability(availability)) }
    }

    private fun isOpen(port: Win32RawInputPort): Boolean = synchronized(lock) { !closed && port in ports }
}
