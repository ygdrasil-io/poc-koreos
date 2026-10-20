package org.graphiks.kadre.internal.win32

import kotlin.time.Duration.Companion.nanoseconds
import org.graphiks.kadre.capture.AlphaMode
import org.graphiks.kadre.capture.CaptureOutcome
import org.graphiks.kadre.capture.CaptureCadence
import org.graphiks.kadre.capture.CaptureConfigurationRevision
import org.graphiks.kadre.capture.CaptureOrientation
import org.graphiks.kadre.capture.CaptureCapabilities
import org.graphiks.kadre.capture.CaptureConfiguration
import org.graphiks.kadre.capture.CaptureCursorMode
import org.graphiks.kadre.capture.CapturePermissionScope
import org.graphiks.kadre.capture.CapturePermissionState
import org.graphiks.kadre.internal.runtime.CapturePort
import org.graphiks.kadre.internal.runtime.CapturePortFrame
import org.graphiks.kadre.internal.runtime.CapturePortPlane
import org.graphiks.kadre.internal.runtime.CapturePortReservation
import org.graphiks.kadre.internal.runtime.CapturePortSnapshot
import org.graphiks.kadre.internal.runtime.CapturePortSource
import org.graphiks.kadre.internal.runtime.CapturePortSourceKey
import org.graphiks.kadre.internal.runtime.CapturePortSources
import org.graphiks.kadre.internal.runtime.CapturePortStream
import org.graphiks.kadre.internal.runtime.CapturePortStreamListener
import org.graphiks.kadre.internal.runtime.CapturePortStreamStart
import org.graphiks.kadre.internal.runtime.CapturePortTarget
import org.graphiks.kadre.internal.runtime.CapturePortTermination
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSourceKind
import org.graphiks.kadre.capture.CaptureTargetConstraints
import org.graphiks.kadre.capture.ColorEncoding
import org.graphiks.kadre.capture.ColorPrimaries
import org.graphiks.kadre.capture.ColorRange
import org.graphiks.kadre.capture.HdrMetadata
import org.graphiks.kadre.capture.MatrixCoefficients
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.capture.PixelPlaneLayout
import org.graphiks.kadre.capture.TransferFunction
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.PermissionState
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kffi.win32.managed.Win32CaptureFrameValue
import org.graphiks.kffi.win32.managed.Win32DisplaySnapshotValue
import org.graphiks.kffi.win32.managed.Win32Displays
import org.graphiks.kffi.win32.managed.Win32GraphicsCapture
import org.graphiks.kffi.win32.managed.Win32GraphicsCaptureGeneratedPort
import org.graphiks.kffi.win32.managed.Win32GraphicsCaptureNativePort

/**
 * Windows Graphics Capture control plane for one runtime session. Honest
 * scope for this delivery: monitors only — `Surface` waits for the
 * same-session window registry wiring and the desktop picker does not exist,
 * so `HostChoice` is refused instead of satisfied by an arbitrary window.
 * Windows desktop apps carry no OS capture permission gate, so permissions
 * publish as granted; WGC availability is probed at runtime, never assumed.
 * Frames arrive as BGRA8 through the KFFI poller and a frame that cannot fit
 * the session budget terminates the stream before it reaches the runtime.
 */
internal class Win32CapturePort(
    private val native: Win32GraphicsCaptureNativePort = Win32GraphicsCaptureGeneratedPort,
    private val monitors: () -> List<Win32DisplaySnapshotValue> = { Win32Displays.snapshot() },
) : CapturePort {
    private val lock = Object()
    private var closed = false
    private var observer: ((KadreResult<CapturePortSnapshot>) -> Unit)? = null
    private val available: Boolean = Win32GraphicsCapture.probe(native)
    @Volatile private var cachedMonitors: List<Win32DisplaySnapshotValue> =
        if (available) runCatching { monitors() }.getOrDefault(emptyList()) else emptyList()

    override val initialSnapshot: CapturePortSnapshot = buildSnapshot()

    override suspend fun requestPermission(scope: CapturePermissionScope): KadreResult<CapturePortSnapshot> =
        KadreResult.Success(buildSnapshot())

    override suspend fun refreshSources(): KadreResult<CapturePortSnapshot> {
        if (available) {
            runCatching { monitors() }.getOrNull()?.let { refreshed ->
                synchronized(lock) { if (!closed) cachedMonitors = refreshed }
            }
        }
        return KadreResult.Success(buildSnapshot())
    }

    override suspend fun reserve(
        target: CapturePortTarget,
        request: CaptureRequest,
    ): KadreResult<CapturePortReservation> {
        if (synchronized(lock) { closed }) {
            return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
        }
        validateRequest(request)?.let { return KadreResult.Failure(it) }
        val resolved = when (target) {
            is CapturePortTarget.HostChoice ->
                return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
            is CapturePortTarget.Surface ->
                return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
            is CapturePortTarget.Source -> {
                val monitor = synchronized(lock) {
                    cachedMonitors.firstOrNull {
                        target.key.namespace == DISPLAY_NAMESPACE && it.monitor == target.key.value
                    }
                } ?: return KadreResult.Failure(KadreFailure.InvalidRequest("target"))
                CaptureTarget(monitor.monitor, window = false, sourceFor(monitor))
            }
        }
        return KadreResult.Success(Win32CaptureReservation(native, resolved))
    }

    override fun installObserver(observer: (KadreResult<CapturePortSnapshot>) -> Unit): AutoCloseable {
        synchronized(lock) {
            check(this.observer == null) { "capture observer is already installed" }
            this.observer = observer
        }
        return AutoCloseable {
            synchronized(lock) {
                if (this.observer === observer) this.observer = null
            }
        }
    }

    override fun close() {
        synchronized(lock) { closed = true }
    }

    private fun buildSnapshot(): CapturePortSnapshot {
        val constraints = CaptureTargetConstraints(
            formats = setOf(PixelFormat.Bgra8),
            cursorModes = setOf(CaptureCursorMode.Hidden, CaptureCursorMode.Embedded, CaptureCursorMode.EmbeddedWhenAvailable),
            region = FeatureAvailability.Unsupported,
        )
        val supported = Capability.Supported(constraints, FeatureAvailability.Available)
        val unavailable = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
        val sources = when {
            !available -> CapturePortSources.Unavailable(platformFailure("graphics-capture-unavailable"))
            else -> CapturePortSources.Enumerated(cachedMonitors.map(::sourceFor))
        }
        return CapturePortSnapshot(
            permissions = CapturePermissionState(screen = PermissionState.Granted, window = PermissionState.Granted),
            capabilities = CaptureCapabilities(
                screen = if (available) supported else unavailable,
                window = if (available) supported else unavailable,
                surface = unavailable,
                sourceEnumeration = if (available) Capability.Supported(Unit, FeatureAvailability.Available) else unavailable,
                hostPicker = FeatureAvailability.Unsupported,
            ),
            sources = sources,
        )
    }

    private fun sourceFor(monitor: Win32DisplaySnapshotValue): CapturePortSource = CapturePortSource(
        key = CapturePortSourceKey(DISPLAY_NAMESPACE, monitor.monitor),
        kind = CaptureSourceKind.Display,
        name = null,
        size = PhysicalSize(monitor.right - monitor.left, monitor.bottom - monitor.top),
    )

    private companion object {
        const val DISPLAY_NAMESPACE = "win32-display"
    }
}

/** One admitted capture target kept natively inert until [start]. */
private data class CaptureTarget(
    val handle: Long,
    val window: Boolean,
    val source: CapturePortSource,
)

private fun validateRequest(request: CaptureRequest): KadreFailure? = when {
    request.region != null -> KadreFailure.Unsupported(KadreOperation.CaptureOpen)
    request.preferredFormats.isNotEmpty() && request.preferredFormats.all { it != PixelFormat.Bgra8 } ->
        KadreFailure.Unsupported(KadreOperation.CaptureOpen)
    else -> null
}

/**
 * One reserved monitor capture. [start] owns the KFFI session, converts each
 * polled frame to a detached BGRA8 value, applies the session budget before
 * the frame reaches the runtime, and publishes the effective configuration
 * (sized from the reserved source, corrected once if the pool reports
 * otherwise) before the first frame that uses it.
 */
private class Win32CaptureReservation(
    private val native: Win32GraphicsCaptureNativePort,
    private val target: CaptureTarget,
) : CapturePortReservation {
    override val source: CapturePortSource = target.source

    override suspend fun start(
        listener: CapturePortStreamListener,
        maxFrameBytes: Long,
    ): KadreResult<CapturePortStreamStart> {
        if (!Win32GraphicsCapture.probe(native)) {
            return KadreResult.Failure(platformFailure("graphics-capture-unavailable"))
        }
        var revision = 0L
        var terminated = false
        val startSize = requireNotNull(source.size) { "reserved capture source carries its bounds" }
        val capture = Win32GraphicsCapture.openCapture(target.handle, target.window, native) { value ->
            if (terminated) return@openCapture
            if (value.bytes.size.toLong() > maxFrameBytes) {
                terminated = true
                listener.onTerminated(
                    CapturePortTermination.Outcome(
                        CaptureOutcome.Failed(
                            KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, maxFrameBytes),
                        ),
                    ),
                )
                return@openCapture
            }
            if (revision == 0L && (value.width != startSize.width || value.height != startSize.height)) {
                listener.onReconfigured(configuration(revision = 1L, size = PhysicalSize(value.width, value.height)))
                revision = 1L
            }
            listener.onFrame(frameFrom(value, revision))
        }
        return KadreResult.Success(
            CapturePortStreamStart(
                stream = Win32CaptureStream(capture),
                configuration = configuration(revision = 0L, size = startSize),
            ),
        )
    }

    override fun close() {
        // Nothing native runs before start; closing a never-started
        // reservation touches no capture frontier.
    }

    private fun frameFrom(value: Win32CaptureFrameValue, revision: Long): CapturePortFrame = CapturePortFrame(
        size = PhysicalSize(value.width, value.height),
        format = PixelFormat.Bgra8,
        planes = listOf(
            CapturePortPlane(
                layout = PixelPlaneLayout(
                    width = value.width,
                    height = value.height,
                    rowStride = value.width * 4,
                    pixelStride = 4,
                    byteCount = value.bytes.size,
                    horizontalSubsampling = 1,
                    verticalSubsampling = 1,
                ),
                bytes = value.bytes,
            ),
        ),
        configurationRevision = revision,
        sourceTimestamp = (value.systemRelativeTimeTicks * 100).nanoseconds,
        duration = null,
        discontinuity = null,
        colorEncoding = BGRA_COLOR,
        alphaMode = AlphaMode.Premultiplied,
        orientation = CaptureOrientation.Upright,
    )

    private fun configuration(revision: Long, size: PhysicalSize): CaptureConfiguration = CaptureConfiguration(
        revision = CaptureConfigurationRevision(revision),
        size = size,
        format = PixelFormat.Bgra8,
        colorEncoding = BGRA_COLOR,
        alphaMode = AlphaMode.Premultiplied,
        orientation = CaptureOrientation.Upright,
        cadence = CaptureCadence.Variable(minimumFrameInterval = null, maximumFrameInterval = null),
        region = null,
        cursorMode = CaptureCursorMode.Embedded,
    )

    private companion object {
        val BGRA_COLOR = ColorEncoding(
            primaries = ColorPrimaries.Bt709,
            transfer = TransferFunction.Srgb,
            matrix = MatrixCoefficients.Bt709,
            range = ColorRange.Full,
            hdr = HdrMetadata.Unknown,
        )
    }
}

/** Non-blocking idempotent stream handle over the live KFFI session. */
private class Win32CaptureStream(
    private val capture: Win32GraphicsCapture,
) : CapturePortStream {
    override fun close() {
        capture.close()
    }
}

private fun platformFailure(code: String): KadreFailure.PlatformFailure =
    KadreFailure.PlatformFailure(KadrePlatform.Win32, "graphics-capture", code)
