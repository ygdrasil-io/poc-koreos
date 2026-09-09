package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.KadrePermission
import org.graphiks.kadre.input.RawInputUnit
import org.graphiks.kadre.internal.runtime.RawInputPortInput
import org.graphiks.kadre.internal.runtime.RawInputPortLease
import org.graphiks.kadre.internal.runtime.RawInputPortLeaseEvent

/** Immutable event copied at the AppKit/CoreGraphics boundary before process-wide fan-out. */
internal sealed interface AppKitRawInputNativeEvent {
    data class Motion(val deltaX: Long, val deltaY: Long) : AppKitRawInputNativeEvent
    data object DisabledByTimeout : AppKitRawInputNativeEvent
    data object DisabledByUserInput : AppKitRawInputNativeEvent
}

/** Native ownership isolated behind KFFI; no raw pointer reaches the Kadre runtime. */
internal interface AppKitRawInputNative {
    fun preflightPermission(): Boolean
    suspend fun requestPermission(): Boolean
    fun installTap(listener: (AppKitRawInputNativeEvent) -> Unit): AppKitRawInputNativeTap
}

internal interface AppKitRawInputNativeTap : AutoCloseable {
    fun reenable()
}

/**
 * Process-wide fan-out owner for one listen-only CoreGraphics event tap.
 *
 * Ports and registrations remain session-owned; this broker owns neither a session nor a public
 * collector. A native event is copied once here and distributed to every registration active at
 * that instant.
 */
internal class AppKitRawInputBroker(
    private val permission: AppKitPermissionBroker,
    private val native: AppKitRawInputNative,
    private val bridgeAvailable: Boolean = true,
) : AutoCloseable {
    private val lock = Any()
    private val ports = linkedSetOf<AppKitRawInputPort>()
    private val registrations = linkedMapOf<AppKitRawInputPortLease, AppKitRawInputPort>()
    private var tap: AppKitRawInputNativeTap? = null
    private var closed = false
    private var capability: Capability<Unit> = initialCapability()

    fun openPort(): AppKitRawInputPort = synchronized(lock) {
        AppKitRawInputPort(this, capability).also { port ->
            check(!closed) { "AppKit raw-input broker is closed" }
            check(ports.add(port)) { "AppKit raw-input port is already registered" }
        }
    }

    suspend fun requestAccess(port: AppKitRawInputPort): KadreResult<RawInputPortLease> {
        if (!isOpen(port)) return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.InputSource))
        if (!bridgeAvailable) return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RawInputAccess))

        if (!permission.preflightGranted() && !permission.requestPermission()) {
            val liveRegistrations = synchronized(lock) { registrations.isNotEmpty() }
            publishAvailability(
                capability = deniedCapability(),
                registrationAvailability = if (liveRegistrations) {
                    FeatureAvailability.RequiresPermission(KadrePermission.InputMonitoring)
                } else {
                    FeatureAvailability.Unavailable(KadreFailure.PermissionDenied(KadrePermission.RawInput))
                },
            )
            closeTapIfUnused(force = true)
            return KadreResult.Failure(KadreFailure.PermissionDenied(KadrePermission.RawInput))
        }

        val lease = synchronized(lock) {
            if (closed || port !in ports || !port.isOpen()) return@synchronized null
            if (!ensureTapLocked()) return@synchronized false
            AppKitRawInputPortLease(::closeRegistration).also { registration ->
                check(registrations.put(registration, port) == null) { "duplicate raw-input registration" }
            }
        }
        return when (lease) {
            null -> KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.InputSource))
            false -> {
                publishAvailability(
                    capability = temporaryCapability(),
                    registrationAvailability = FeatureAvailability.Unavailable(
                        KadreFailure.TemporarilyUnavailable(retryable = true),
                    ),
                )
                KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true))
            }

            is AppKitRawInputPortLease -> {
                publishAvailability(Capability.Supported(Unit, FeatureAvailability.Available), FeatureAvailability.Available)
                KadreResult.Success(lease)
            }

            else -> error("unexpected raw-input registration outcome")
        }
    }

    fun closePort(port: AppKitRawInputPort) {
        val stale = synchronized(lock) {
            ports.remove(port)
            registrations.filterValues { it === port }.keys.toList().also { entries ->
                entries.forEach(registrations::remove)
            }
        }
        stale.forEach(AppKitRawInputPortLease::closeFromBroker)
        closeTapIfUnused()
    }

    /** Re-probes observed Input Monitoring state when AppKit returns to the foreground. */
    fun reprobeForHostActivation() {
        if (!bridgeAvailable) return
        if (!runCatching(permission::preflightGranted).getOrDefault(false)) {
            publishAvailability(
                capability = deniedCapability(),
                registrationAvailability = FeatureAvailability.RequiresPermission(KadrePermission.InputMonitoring),
            )
            closeTapIfUnused(force = true)
            return
        }
        val recovered = synchronized(lock) {
            if (closed || registrations.isEmpty()) true else ensureTapLocked()
        }
        if (recovered) {
            publishAvailability(Capability.Supported(Unit, FeatureAvailability.Available), FeatureAvailability.Available)
        } else {
            publishAvailability(
                capability = temporaryCapability(),
                registrationAvailability = FeatureAvailability.Unavailable(
                    KadreFailure.TemporarilyUnavailable(retryable = true),
                ),
            )
        }
    }

    override fun close() {
        val toClose = synchronized(lock) {
            if (closed) return
            closed = true
            ports.clear()
            registrations.keys.toList().also { registrations.clear() }
        }
        toClose.forEach(AppKitRawInputPortLease::closeFromBroker)
        closeTapIfUnused(force = true)
    }

    private fun closeRegistration(registration: AppKitRawInputPortLease) {
        synchronized(lock) { registrations.remove(registration) }
        closeTapIfUnused()
    }

    private fun isOpen(port: AppKitRawInputPort): Boolean = synchronized(lock) {
        !closed && port in ports && port.isOpen()
    }

    private fun ensureTapLocked(): Boolean {
        if (tap != null) return true
        return runCatching {
            native.installTap(::acceptNativeEvent).also { owner -> tap = owner }
        }.isSuccess
    }

    private fun acceptNativeEvent(event: AppKitRawInputNativeEvent) {
        when (event) {
            is AppKitRawInputNativeEvent.Motion -> {
                val input = RawInputPortInput(
                    deltaX = event.deltaX.toDouble(),
                    deltaY = event.deltaY.toDouble(),
                    unit = RawInputUnit.DeviceCount,
                    deviceId = null,
                )
                val targets = synchronized(lock) { registrations.keys.toList() }
                targets.forEach { registration -> registration.publish(RawInputPortLeaseEvent.Input(input)) }
            }

            AppKitRawInputNativeEvent.DisabledByTimeout -> recoverFromTimeout()
            AppKitRawInputNativeEvent.DisabledByUserInput -> reprobeAfterSourceLoss()
        }
    }

    private fun recoverFromTimeout() {
        val owner = synchronized(lock) { tap } ?: return
        publishAvailability(
            capability = temporaryCapability(),
            registrationAvailability = FeatureAvailability.Unavailable(
                KadreFailure.TemporarilyUnavailable(retryable = true),
            ),
        )
        val recovered = runCatching { owner.reenable() }.isSuccess
        if (recovered) {
            publishAvailability(Capability.Supported(Unit, FeatureAvailability.Available), FeatureAvailability.Available)
        } else {
            reprobeAfterSourceLoss()
        }
    }

    private fun reprobeAfterSourceLoss() {
        val granted = runCatching(permission::preflightGranted).getOrDefault(false)
        if (!granted) {
            publishAvailability(
                capability = deniedCapability(),
                registrationAvailability = FeatureAvailability.RequiresPermission(KadrePermission.InputMonitoring),
            )
            closeTapIfUnused(force = true)
            return
        }

        publishAvailability(
            capability = temporaryCapability(),
            registrationAvailability = FeatureAvailability.Unavailable(
                KadreFailure.TemporarilyUnavailable(retryable = true),
            ),
        )
        closeTapIfUnused(force = true)
        val recovered = synchronized(lock) {
            if (closed || registrations.isEmpty()) false else ensureTapLocked()
        }
        if (recovered) {
            publishAvailability(Capability.Supported(Unit, FeatureAvailability.Available), FeatureAvailability.Available)
        }
    }

    /** Publishes capability before every registration is suspended or reactivated. */
    private fun publishAvailability(
        capability: Capability<Unit>,
        registrationAvailability: FeatureAvailability,
    ) {
        val targets = synchronized(lock) {
            this.capability = capability
            ports.toList() to registrations.keys.toList()
        }
        targets.first.forEach { port -> port.publishCapability(capability) }
        targets.second.forEach { registration ->
            registration.publish(RawInputPortLeaseEvent.Availability(registrationAvailability))
        }
    }

    private fun closeTapIfUnused(force: Boolean = false) {
        val owner = synchronized(lock) {
            if (!force && registrations.isNotEmpty()) return
            tap.also { tap = null }
        }
        runCatching { owner?.close() }
    }

    private fun initialCapability(): Capability<Unit> = when {
        !bridgeAvailable -> Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RawInputAccess))
        permission.preflightGranted() -> Capability.Supported(Unit, FeatureAvailability.Available)
        else -> Capability.Supported(Unit, FeatureAvailability.RequiresPermission(KadrePermission.InputMonitoring))
    }

    private fun deniedCapability(): Capability<Unit> = Capability.Supported(
        Unit,
        FeatureAvailability.Unavailable(KadreFailure.PermissionDenied(KadrePermission.RawInput)),
    )

    private fun temporaryCapability(): Capability<Unit> = Capability.Supported(
        Unit,
        FeatureAvailability.Unavailable(KadreFailure.TemporarilyUnavailable(retryable = true)),
    )
}
