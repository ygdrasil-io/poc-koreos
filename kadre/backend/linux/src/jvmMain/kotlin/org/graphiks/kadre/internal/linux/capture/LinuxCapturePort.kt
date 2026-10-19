package org.graphiks.kadre.internal.linux.capture

import org.graphiks.kadre.capture.CaptureCapabilities
import org.graphiks.kadre.capture.CapturePermissionScope
import org.graphiks.kadre.capture.CapturePermissionState
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure.Unsupported as UnsupportedCapability
import org.graphiks.kadre.input.PermissionState
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.linux.dbus.DbusConnection
import org.graphiks.kadre.internal.linux.dbus.DbusMessage
import org.graphiks.kadre.internal.linux.dbus.DbusValue
import org.graphiks.kadre.internal.linux.dbus.KffiDbusTransport
import org.graphiks.kadre.internal.linux.portal.ScreenCastPortal
import org.graphiks.kadre.internal.runtime.CapturePort
import org.graphiks.kadre.internal.runtime.CapturePortSnapshot
import org.graphiks.kadre.internal.runtime.CapturePortReservation
import org.graphiks.kadre.internal.runtime.CapturePortSources
import org.graphiks.kadre.internal.runtime.CapturePortTarget

/**
 * The capture control plane for Linux sessions (design
 * LINUX-CAPTURE-PORTAL-DESIGN D1/D4): availability is OBSERVED — the
 * session bus answers AND the portal owns its name — never assumed from
 * environment markers. The inventory publishes HostPickerOnly until the
 * PipeWire stream path is proven; every reservation is refused with the
 * documented pending-work failure rather than a silent empty pipeline.
 */
internal class LinuxCapturePort(
    private val connectionFactory: () -> DbusConnection?,
) : CapturePort {

    private val lock = Any()
    private var closed = false
    private var cachedSnapshot: CapturePortSnapshot = probe()

    override val initialSnapshot: CapturePortSnapshot
        get() = synchronized(lock) { cachedSnapshot }

    override suspend fun requestPermission(scope: CapturePermissionScope): KadreResult<CapturePortSnapshot> {
        // the portal asks the user per session — there is no standing
        // permission to request; refreshing the observation is the honest
        // answer for both scopes
        return refreshSources()
    }

    override suspend fun refreshSources(): KadreResult<CapturePortSnapshot> = synchronized(lock) {
        if (closed) {
            return KadreResult.Failure(KadreFailure.Closed(org.graphiks.kadre.diagnostics.KadreResourceKind.CaptureSource))
        }
        cachedSnapshot = probe()
        KadreResult.Success(cachedSnapshot)
    }

    override suspend fun reserve(target: CapturePortTarget, request: CaptureRequest): KadreResult<CapturePortReservation> {
        // D4: Source and Surface targets stay refused until the exact
        // portal→PipeWire path is proven for them
        if (target !is CapturePortTarget.HostChoice) {
            return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
        }
        return KadreResult.Failure(
            KadreFailure.PlatformFailure(
                KadrePlatform.Linux,
                "capture",
                "pipewire-stream-pending",
            ),
        )
    }

    override fun installObserver(observer: (KadreResult<CapturePortSnapshot>) -> Unit): AutoCloseable {
        // the control plane is poll-based; no spontaneous transitions
        return AutoCloseable { }
    }

    override fun close() {
        synchronized(lock) {
            closed = true
        }
    }

    /** D1: connect, Hello (unique name), then check the portal name owner. */
    private fun probe(): CapturePortSnapshot = runCatching {
        val connection = connectionFactory() ?: return unavailable("bus-unreachable")
        connection.use {
            if (!it.performHandshake(2_000)) return unavailable("bus-auth-failed")
            val hello = hello(it) ?: return unavailable("bus-hello-failed")
            if (!ScreenCastPortal.portalAvailable(it)) return unavailable("portal-absent")
            available(hello)
        }
    }.getOrElse { unavailable("bus-error") }

    private fun hello(connection: DbusConnection): String? {
        val call = DbusMessage(
            type = DbusMessage.TYPE_METHOD_CALL,
            flags = 0,
            serial = 0,
            fields = mapOf(
                DbusMessage.FIELD_DESTINATION to DbusValue.StringValue("org.freedesktop.DBus"),
                DbusMessage.FIELD_PATH to DbusValue.ObjectPathValue("/org/freedesktop/DBus"),
                DbusMessage.FIELD_INTERFACE to DbusValue.StringValue("org.freedesktop.DBus"),
                DbusMessage.FIELD_MEMBER to DbusValue.StringValue("Hello"),
            ),
            bodySignature = null,
            body = emptyList(),
        )
        val replied = connection.call(call, 2_000)
        return (replied.reply?.body?.firstOrNull() as? DbusValue.StringValue)?.value
    }

    private fun available(uniqueName: String): CapturePortSnapshot = CapturePortSnapshot(
        permissions = CapturePermissionState(
            screen = PermissionState.Granted,
            window = PermissionState.Granted,
        ),
        capabilities = Capabilities,
        sources = CapturePortSources.HostPickerOnly,
    )

    private fun unavailable(code: String): CapturePortSnapshot = CapturePortSnapshot(
        permissions = CapturePermissionState(
            screen = PermissionState.Granted,
            window = PermissionState.Granted,
        ),
        capabilities = Capabilities,
        sources = CapturePortSources.Unavailable(
            KadreFailure.PlatformFailure(KadrePlatform.Linux, "capture-portal", code),
        ),
    )

    companion object {
        // HostPickerOnly is published; source/window/surface enumeration
        // and streams follow the PipeWire tranche (design D4)
        private val Capabilities = CaptureCapabilities(
            screen = Capability.Unsupported(UnsupportedCapability(KadreOperation.CaptureOpen)),
            window = Capability.Unsupported(UnsupportedCapability(KadreOperation.CaptureOpen)),
            surface = Capability.Unsupported(UnsupportedCapability(KadreOperation.CaptureOpen)),
            sourceEnumeration = Capability.Unsupported(UnsupportedCapability(KadreOperation.CaptureRefreshSources)),
            hostPicker = FeatureAvailability.Available,
        )

        /**
         * The effective uid for SASL EXTERNAL — from /proc/self/status,
         * the kernel's own view (Euid line), no binding needed.
         */
        internal fun effectiveUid(): Int = runCatching {
            // both lines carry real/effective/saved/fs; effective is the
            // second value — prefer Euid, fall back to Uid
            val status = java.io.File("/proc/self/status").readLines()
            val uidLine = status.firstOrNull { it.startsWith("Euid:") }
                ?: status.firstOrNull { it.startsWith("Uid:") }
            uidLine
                ?.split(Regex("\\s+"))
                ?.getOrNull(2)
                ?.toIntOrNull()
        }.getOrNull() ?: -1

        /** The production factory: session bus via kffi-posix sockets. */
        fun create(): LinuxCapturePort = LinuxCapturePort(
            connectionFactory = {
                KffiDbusTransport.connectToSessionBus()?.let { transport ->
                    DbusConnection(transport, uidProvider = ::effectiveUid)
                }
            },
        )
    }
}
