package org.graphiks.kadre.internal.linux.capture

import org.graphiks.kadre.capture.CaptureCapabilities
import org.graphiks.kadre.capture.CapturePermissionScope
import org.graphiks.kadre.capture.CapturePermissionState
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSourceKind
import org.graphiks.kadre.capture.CaptureTargetConstraints
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.internal.linux.pipewire.KffiPipeWireNativeSession
import org.graphiks.kadre.internal.linux.pipewire.PipeWireStreamClient
import org.graphiks.kadre.internal.runtime.CapturePortFrame
import org.graphiks.kadre.internal.runtime.CapturePortSource
import org.graphiks.kadre.internal.runtime.CapturePortSourceKey
import org.graphiks.kadre.internal.runtime.CapturePortStreamListener
import org.graphiks.kadre.internal.runtime.CapturePortStreamStart
import org.graphiks.kadre.diagnostics.KadreFailure.Unsupported as UnsupportedCapability
import org.graphiks.kffi.posix.LinuxPosix
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
 * LINUX-CAPTURE-PORTAL-DESIGN D1/D4 + LINUX-PIPEWIRE-STREAM-DESIGN D9):
 * availability is OBSERVED — the session bus answers AND the portal owns
 * its name — never assumed from environment markers. The inventory stays
 * HostPickerOnly (the portal owns the source list); a HostChoice
 * reservation drives the whole ScreenCast chain — CreateSession,
 * SelectSources (the picker), Start, OpenPipeWireRemote — and hands the
 * fd plus node id to the PipeWire stream client. Source and Surface
 * targets stay refused until an exact path to them is proven (D4).
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
        synchronized(lock) {
            if (closed) {
                return KadreResult.Failure(KadreFailure.Closed(org.graphiks.kadre.diagnostics.KadreResourceKind.CaptureSource))
            }
        }
        val connection = connectionFactory()
            ?: return KadreResult.Failure(portalFailure("bus-unreachable"))
        // ownership: the connection leaves this function alive exactly when
        // the reservation takes it over; every failure path closes it
        var handoff = false
        try {
            if (!connection.performHandshake(2_000)) {
                return KadreResult.Failure(portalFailure("bus-auth-failed"))
            }
            val hello = hello(connection) ?: return KadreResult.Failure(portalFailure("bus-hello-failed"))
            val portal = ScreenCastPortal(connection, uniqueNameProvider = { hello })

            val session = portal.createSession()
            val sessionPath = session.sessionPath
                ?: return when (session.outcome) {
                    is ScreenCastPortal.StepOutcome.Refused ->
                        KadreResult.Failure(KadreFailure.PermissionDenied(org.graphiks.kadre.input.KadrePermission.CaptureScreen))
                    else -> KadreResult.Failure(portalFailure("session-create-failed"))
                }

            when (val select = portal.selectSources(sessionPath)) {
                is ScreenCastPortal.StepOutcome.Refused ->
                    return KadreResult.Failure(KadreFailure.PermissionDenied(org.graphiks.kadre.input.KadrePermission.CaptureScreen))
                ScreenCastPortal.StepOutcome.Failed ->
                    return KadreResult.Failure(portalFailure("source-selection-failed"))
                is ScreenCastPortal.StepOutcome.Success -> Unit
            }

            val started = portal.start(sessionPath)
            val results = (started as? ScreenCastPortal.StepOutcome.Success)?.results
                ?: return when (started) {
                    is ScreenCastPortal.StepOutcome.Refused ->
                        KadreResult.Failure(KadreFailure.PermissionDenied(org.graphiks.kadre.input.KadrePermission.CaptureScreen))
                    else -> KadreResult.Failure(portalFailure("start-failed"))
                }
            val stream = firstStream(results)
                ?: return KadreResult.Failure(portalFailure("stream-absent"))
            val nodeId = stream.nodeId
                ?: return KadreResult.Failure(portalFailure("node-id-absent"))

            val fd = portal.openPipeWireRemote(sessionPath)
                ?: return KadreResult.Failure(portalFailure("pipewire-remote-refused"))

            handoff = true
            return KadreResult.Success(
                LinuxCaptureReservation(
                    connection = connection,
                    portal = portal,
                    sessionPath = sessionPath,
                    pipewireFd = fd,
                    nodeId = nodeId,
                    source = CapturePortSource(
                        key = CapturePortSourceKey(NAMESPACE, nodeId.toLong()),
                        kind = stream.kind,
                        name = null,
                        size = stream.size,
                    ),
                ),
            )
        } finally {
            if (!handoff) connection.close()
        }
    }

    private fun firstStream(results: Map<String, DbusValue>): PortalStream? {
        val streams = (results["streams"] as? DbusValue.ArrayValue)?.elements ?: return null
        val stream = streams.firstOrNull() as? DbusValue.ArrayValue ?: return null
        var nodeId: Long? = null
        var kind = CaptureSourceKind.Display
        var width: Int? = null
        var height: Int? = null
        stream.elements.forEach { element ->
            val entry = element as? DbusValue.DictEntryValue ?: return@forEach
            val key = (entry.key as? DbusValue.StringValue)?.value ?: return@forEach
            when (key) {
                "source_id" -> nodeId = (entry.value as? DbusValue.VariantValue)
                    ?.let { it.value as? DbusValue.Uint32Value }?.value
                "source_type" -> {
                    val type = (entry.value as? DbusValue.VariantValue)
                        ?.let { it.value as? DbusValue.Uint32Value }?.value?.toInt()
                    if (type == 2) kind = CaptureSourceKind.Window
                }
                "size" -> {
                    val size = (entry.value as? DbusValue.VariantValue)?.value as? DbusValue.StructValue
                    val members = size?.members
                    if (members != null && members.size == 2) {
                        width = (members[0] as? DbusValue.Int32Value)?.value
                        height = (members[1] as? DbusValue.Int32Value)?.value
                    }
                }
            }
        }
        if (nodeId == null) return null
        val size = if (width != null && height != null && width!! > 0 && height!! > 0) {
            org.graphiks.kadre.surface.PhysicalSize(width!!, height!!)
        } else {
            null
        }
        return PortalStream(nodeId!!, kind, size)
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

    private fun reservationFailure(code: String): KadreResult<CapturePortReservation> =
        KadreResult.Failure(portalFailure(code))

    private fun portalFailure(code: String): KadreFailure =
        KadreFailure.PlatformFailure(KadrePlatform.Linux, "capture-portal", code)

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

    private data class PortalStream(
        val nodeId: Long,
        val kind: CaptureSourceKind,
        val size: org.graphiks.kadre.surface.PhysicalSize?,
    )

    /**
     * A reserved portal session: owns the D-Bus connection, the portal
     * session, the PipeWire fd and (after [start]) one stream client.
     */
    private inner class LinuxCaptureReservation(
        @Volatile private var connection: DbusConnection?,
        @Volatile private var portal: ScreenCastPortal?,
        private val sessionPath: String,
        private val pipewireFd: Int,
        private val nodeId: Long,
        override val source: CapturePortSource,
    ) : CapturePortReservation {

        private val closeLock = Any()
        private var streamClient: PipeWireStreamClient? = null
        private var closed = false

        override suspend fun start(
            listener: CapturePortStreamListener,
            maxFrameBytes: Long,
        ): org.graphiks.kadre.diagnostics.KadreResult<CapturePortStreamStart> {
            synchronized(closeLock) {
                if (closed) {
                    return org.graphiks.kadre.diagnostics.KadreResult.Failure(
                        KadreFailure.Closed(org.graphiks.kadre.diagnostics.KadreResourceKind.CaptureSession),
                    )
                }
                if (streamClient != null) {
                    return org.graphiks.kadre.diagnostics.KadreResult.Failure(
                        KadreFailure.InvalidRequest("already-started"),
                    )
                }
                val client = PipeWireStreamClient { events ->
                    KffiPipeWireNativeSession(pipewireFd, nodeId.toInt(), events)
                }
                streamClient = client
                val started = client.start(listener, maxFrameBytes, NEGOTIATION_TIMEOUT_MILLIS)
                if (started is org.graphiks.kadre.diagnostics.KadreResult.Failure) {
                    client.close()
                    streamClient = null
                }
                return started
            }
        }

        override fun close() {
            val client: PipeWireStreamClient?
            val portalRef: ScreenCastPortal?
            val connectionRef: DbusConnection?
            synchronized(closeLock) {
                if (closed) return
                closed = true
                client = streamClient
                streamClient = null
                portalRef = portal
                portal = null
                connectionRef = connection
                connection = null
            }
            runCatching { client?.close() }
            runCatching { portalRef?.closeSession(sessionPath) }
            runCatching { LinuxPosix.close(pipewireFd) }
            runCatching { connectionRef?.close() }
        }
    }

    companion object {
        private const val NAMESPACE = "portal-screencast"
        private const val NEGOTIATION_TIMEOUT_MILLIS = 10_000L

        // HostPickerOnly stays published: the portal owns the source list.
        // Screen and window targets deliver through the host picker; the
        // exact-source enumeration and surfaces remain refused (design D4).
        private val DeliveredFormats = setOf(
            PixelFormat.Bgra8, PixelFormat.Bgrx8, PixelFormat.Rgba8, PixelFormat.Nv12, PixelFormat.I420,
        )
        private val Capabilities = CaptureCapabilities(
            screen = Capability.Supported(
                constraints = CaptureTargetConstraints(
                    formats = DeliveredFormats,
                    cursorModes = setOf(org.graphiks.kadre.capture.CaptureCursorMode.Hidden),
                    region = FeatureAvailability.Unsupported,
                ),
                availability = FeatureAvailability.RequiresPermission(org.graphiks.kadre.input.KadrePermission.CaptureScreen),
            ),
            window = Capability.Supported(
                constraints = CaptureTargetConstraints(
                    formats = DeliveredFormats,
                    cursorModes = setOf(org.graphiks.kadre.capture.CaptureCursorMode.Hidden),
                    region = FeatureAvailability.Unsupported,
                ),
                availability = FeatureAvailability.RequiresPermission(org.graphiks.kadre.input.KadrePermission.CaptureWindow),
            ),
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
