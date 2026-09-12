@file:OptIn(org.graphiks.kffi.objc.PlatformAvailability::class)

package org.graphiks.kadre.internal.appkit

import kotlinx.coroutines.suspendCancellableCoroutine
import org.graphiks.kadre.capture.AlphaMode
import org.graphiks.kadre.capture.CaptureCadence
import org.graphiks.kadre.capture.CaptureCapabilities
import org.graphiks.kadre.capture.CaptureConfiguration
import org.graphiks.kadre.capture.CaptureConfigurationRevision
import org.graphiks.kadre.capture.CaptureCursorMode
import org.graphiks.kadre.capture.CaptureOrientation
import org.graphiks.kadre.capture.CaptureOutcome
import org.graphiks.kadre.capture.CapturePermissionScope
import org.graphiks.kadre.capture.CapturePermissionState
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSourceKind
import org.graphiks.kadre.capture.CaptureStopReason
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
import org.graphiks.kadre.input.KadrePermission
import org.graphiks.kadre.input.PermissionState
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
import org.graphiks.kadre.internal.runtime.CapturePortTermination
import org.graphiks.kadre.internal.runtime.CapturePortTarget
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kffi.objc.appkit.ScreenCaptureCapability
import org.graphiks.kffi.objc.appkit.ScreenCaptureFrameLease
import org.graphiks.kffi.objc.appkit.ScreenCaptureKitCaptures
import org.graphiks.kffi.objc.appkit.ScreenCaptureKitFailure
import org.graphiks.kffi.objc.appkit.ScreenCaptureOpenResult
import org.graphiks.kffi.objc.appkit.ScreenCapturePermissionRequestResult
import org.graphiks.kffi.objc.appkit.ScreenCapturePlane
import org.graphiks.kffi.objc.appkit.ScreenCaptureReservation
import org.graphiks.kffi.objc.appkit.ScreenCaptureReservationResult
import org.graphiks.kffi.objc.appkit.ScreenCaptureReservationSource
import org.graphiks.kffi.objc.appkit.ScreenCaptureSourceCatalog
import org.graphiks.kffi.objc.appkit.ScreenCaptureSourceEnumerationResult
import org.graphiks.kffi.objc.appkit.ScreenCaptureStopResult
import org.graphiks.kffi.objc.appkit.ScreenCaptureStreamConfiguration
import org.graphiks.kffi.objc.appkit.ScreenCaptureStreamSession
import org.graphiks.kffi.objc.appkit.ScreenCaptureTarget
import org.graphiks.kffi.objc.appkit.ScreenCaptureControlPlanes
import org.graphiks.kffi.objc.SCStreamErrorCode
import org.graphiks.kffi.objc.SCStreamErrorDomain
import org.graphiks.kffi.objc.ObjCRuntime
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.min

/**
 * Session-owned ScreenCaptureKit adapter.
 *
 * The port retains only detached identifiers and Kotlin-owned frame bytes across the
 * backend/runtime boundary. It deliberately advertises neither region nor same-session Surface
 * capture: ScreenCaptureKit's physical-pixel to logical-point conversion is not available yet,
 * and approximating a Surface by its enclosing window would violate the public contract.
 */
internal class AppKitCapturePort(
    private val native: AppKitCaptureNative = KffiAppKitCaptureNative,
) : CapturePort {
    private val lock = Any()
    private val sourcesByKey = linkedMapOf<CapturePortSourceKey, CapturePortSource>()
    private var closed = false
    private var observer: ((KadreResult<CapturePortSnapshot>) -> Unit)? = null

    override val initialSnapshot: CapturePortSnapshot = snapshotFor(native.capability())

    override suspend fun requestPermission(scope: CapturePermissionScope): KadreResult<CapturePortSnapshot> {
        if (isClosed()) return closedFailure()
        val beforeRequest = try {
            native.capability()
        } catch (_: Exception) {
            return KadreResult.Failure(platformFailure("capability-read-failed"))
        } catch (_: LinkageError) {
            return KadreResult.Failure(platformFailure("capability-read-failed"))
        }
        if (!beforeRequest.supportsScreenCapture) {
            return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CapturePermission))
        }
        val requested = try {
            native.requestPermission()
        } catch (_: Exception) {
            return KadreResult.Failure(platformFailure("permission-request-failed"))
        } catch (_: LinkageError) {
            return KadreResult.Failure(platformFailure("permission-request-failed"))
        }
        val capability = try {
            native.capability()
        } catch (_: Exception) {
            return KadreResult.Failure(platformFailure("capability-read-failed"))
        } catch (_: LinkageError) {
            return KadreResult.Failure(platformFailure("capability-read-failed"))
        }
        val snapshot = when (requested) {
            AppKitCaptureNativePermissionResult.Granted -> snapshotFor(
                capability = capability.copy(preflightAccessGranted = true),
                permissions = CapturePermissionState(PermissionState.Granted, PermissionState.Granted),
            )

            AppKitCaptureNativePermissionResult.Denied -> snapshotFor(
                capability = capability.copy(preflightAccessGranted = false),
                permissions = CapturePermissionState(
                    PermissionState.Denied(canRequestAgain = true),
                    PermissionState.Denied(canRequestAgain = true),
                ),
            )

            is AppKitCaptureNativePermissionResult.Failed -> {
                return KadreResult.Failure(platformFailure("permission-request-failed"))
            }
        }
        return KadreResult.Success(snapshot)
    }

    override suspend fun refreshSources(): KadreResult<CapturePortSnapshot> {
        if (isClosed()) return closedFailure()
        val capability = try {
            native.capability()
        } catch (_: Exception) {
            return KadreResult.Failure(platformFailure("capability-read-failed"))
        } catch (_: LinkageError) {
            return KadreResult.Failure(platformFailure("capability-read-failed"))
        }
        if (!capability.supportsScreenCapture) {
            return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureRefreshSources))
        }
        if (!capability.preflightAccessGranted) {
            return KadreResult.Failure(KadreFailure.PermissionDenied(KadrePermission.CaptureScreen))
        }
        return when (val enumerated = awaitSourceEnumeration()) {
            is AppKitCaptureNativeSourceEnumerationResult.Enumerated -> {
                val sources = try {
                    enumerated.catalog.toPortSources()
                } catch (_: Exception) {
                    return KadreResult.Failure(platformFailure("invalid-source-inventory"))
                } catch (_: LinkageError) {
                    return KadreResult.Failure(platformFailure("invalid-source-inventory"))
                }
                synchronized(lock) {
                    sourcesByKey.clear()
                    sources.forEach { source -> sourcesByKey[source.key] = source }
                }
                KadreResult.Success(
                    snapshotFor(
                        capability = capability,
                        permissions = CapturePermissionState(PermissionState.Granted, PermissionState.Granted),
                        sources = CapturePortSources.Enumerated(sources),
                    ),
                )
            }

            is AppKitCaptureNativeSourceEnumerationResult.Failed ->
                KadreResult.Failure(platformFailure("source-enumeration-failed"))
        }
    }

    override suspend fun reserve(
        target: CapturePortTarget,
        request: CaptureRequest,
    ): KadreResult<CapturePortReservation> {
        if (isClosed()) return closedFailure()
        validateRequest(request)?.let { return KadreResult.Failure(it) }
        val capability = try {
            native.capability()
        } catch (_: Exception) {
            return KadreResult.Failure(platformFailure("capability-read-failed"))
        } catch (_: LinkageError) {
            return KadreResult.Failure(platformFailure("capability-read-failed"))
        }
        if (!capability.supportsScreenCapture) {
            return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
        }
        if (!capability.preflightAccessGranted) {
            return KadreResult.Failure(KadreFailure.PermissionDenied(KadrePermission.CaptureScreen))
        }
        val nativeTarget = target.toNativeTarget(capability)
            ?: return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
        val selected = (target as? CapturePortTarget.Source)?.key?.let { key ->
            synchronized(lock) { sourcesByKey[key] }
        }
        return when (val reserved = awaitReservation(nativeTarget)) {
            is AppKitCaptureNativeReservationResult.Reserved -> {
                try {
                    val source = reserved.reservation.source.toPortSource(selected)
                    KadreResult.Success(AppKitCaptureReservation(reserved.reservation, source, request))
                } catch (_: Exception) {
                    runCatching(reserved.reservation::close)
                    KadreResult.Failure(platformFailure("invalid-reserved-source"))
                } catch (_: LinkageError) {
                    runCatching(reserved.reservation::close)
                    KadreResult.Failure(platformFailure("invalid-reserved-source"))
                }
            }

            AppKitCaptureNativeReservationResult.Cancelled ->
                KadreResult.Failure(KadreFailure.UserCancelled(KadreOperation.CaptureOpen))

            is AppKitCaptureNativeReservationResult.Failed ->
                KadreResult.Failure(platformFailure("source-reservation-failed"))
        }
    }

    override fun installObserver(observer: (KadreResult<CapturePortSnapshot>) -> Unit): AutoCloseable {
        synchronized(lock) {
            check(!closed) { "capture port is closed" }
            check(this.observer == null) { "capture port observer is already installed" }
            this.observer = observer
        }
        return AutoCloseable {
            synchronized(lock) {
                if (this.observer === observer) this.observer = null
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            observer = null
            sourcesByKey.clear()
        }
    }

    private suspend fun awaitSourceEnumeration(): AppKitCaptureNativeSourceEnumerationResult =
        suspendCancellableCoroutine { continuation ->
            val owner = DeferredNativeOwner()
            continuation.invokeOnCancellation { owner.close() }
            try {
                owner.install(native.enumerateSources { result ->
                    owner.close()
                    if (continuation.isActive) continuation.resume(result)
                })
            } catch (cause: Throwable) {
                owner.close()
                if (continuation.isActive) continuation.resume(
                    AppKitCaptureNativeSourceEnumerationResult.Failed(cause),
                )
            }
        }

    private suspend fun awaitReservation(
        target: AppKitCaptureNativeTarget,
    ): AppKitCaptureNativeReservationResult = suspendCancellableCoroutine { continuation ->
        val owner = DeferredNativeOwner()
        continuation.invokeOnCancellation { owner.close() }
        try {
            owner.install(native.reserve(target) { result ->
                owner.close()
                if (continuation.isActive) continuation.resume(result) { _, delivered, _ ->
                    (delivered as? AppKitCaptureNativeReservationResult.Reserved)?.reservation?.close()
                }
                else (result as? AppKitCaptureNativeReservationResult.Reserved)?.reservation?.close()
            })
        } catch (cause: Throwable) {
            owner.close()
            if (continuation.isActive) continuation.resume(AppKitCaptureNativeReservationResult.Failed(cause))
        }
    }

    private fun snapshotFor(
        capability: AppKitCaptureNativeCapability,
        permissions: CapturePermissionState = capability.permissions(),
        sources: CapturePortSources = capability.initialSources(),
    ): CapturePortSnapshot = CapturePortSnapshot(
        permissions = permissions,
        capabilities = capability.toCapabilities(),
        sources = sources,
    )

    private fun isClosed(): Boolean = synchronized(lock) { closed }

    private fun closedFailure(): KadreResult.Failure =
        KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host))
}

/** Detached native seam. The production implementation below is the only code that imports KFFI. */
internal interface AppKitCaptureNative {
    fun capability(): AppKitCaptureNativeCapability
    fun requestPermission(): AppKitCaptureNativePermissionResult
    fun enumerateSources(callback: (AppKitCaptureNativeSourceEnumerationResult) -> Unit): AutoCloseable
    fun reserve(
        target: AppKitCaptureNativeTarget,
        callback: (AppKitCaptureNativeReservationResult) -> Unit,
    ): AutoCloseable
}

internal data class AppKitCaptureNativeCapability(
    val supportsScreenCapture: Boolean,
    val preflightAccessGranted: Boolean,
    val supportsHostPicker: Boolean,
)

internal sealed interface AppKitCaptureNativePermissionResult {
    data object Granted : AppKitCaptureNativePermissionResult
    data object Denied : AppKitCaptureNativePermissionResult
    data class Failed(val cause: Throwable) : AppKitCaptureNativePermissionResult
}

internal sealed interface AppKitCaptureNativeTarget {
    data object HostPicker : AppKitCaptureNativeTarget
    data class Display(val id: Long) : AppKitCaptureNativeTarget
    data class Window(val id: Long) : AppKitCaptureNativeTarget
}

internal data class AppKitCaptureNativeSourceCatalog(
    val displays: List<AppKitCaptureNativeDisplaySource>,
    val windows: List<AppKitCaptureNativeWindowSource>,
)

internal data class AppKitCaptureNativeDisplaySource(
    val id: Long,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val name: String?,
)

internal data class AppKitCaptureNativeWindowSource(
    val id: Long,
    val title: String?,
)

internal sealed interface AppKitCaptureNativeSourceEnumerationResult {
    data class Enumerated(val catalog: AppKitCaptureNativeSourceCatalog) : AppKitCaptureNativeSourceEnumerationResult
    data class Failed(val cause: Throwable) : AppKitCaptureNativeSourceEnumerationResult
}

internal sealed interface AppKitCaptureNativeReservationResult {
    data class Reserved(val reservation: AppKitCaptureNativeReservation) : AppKitCaptureNativeReservationResult
    data object Cancelled : AppKitCaptureNativeReservationResult
    data class Failed(val cause: Throwable) : AppKitCaptureNativeReservationResult
}

internal sealed interface AppKitCaptureNativeReservationSource {
    data object Unknown : AppKitCaptureNativeReservationSource
    data class Display(val id: Long) : AppKitCaptureNativeReservationSource
    data class Window(val id: Long, val title: String?) : AppKitCaptureNativeReservationSource
}

internal interface AppKitCaptureNativeReservation : AutoCloseable {
    val source: AppKitCaptureNativeReservationSource

    fun start(
        configuration: AppKitCaptureNativeStreamConfiguration,
        onFrame: (AppKitCaptureNativeFrame) -> Unit,
        onOpened: (AppKitCaptureNativeOpenResult) -> Unit,
        onStopped: (AppKitCaptureNativeStopResult) -> Unit,
    ): AutoCloseable

    override fun close()
}

internal data class AppKitCaptureNativeStreamConfiguration(
    val width: Int,
    val height: Int,
    val showsCursor: Boolean,
    val minimumFrameInterval: kotlin.time.Duration?,
)

internal sealed interface AppKitCaptureNativeOpenResult {
    data class Opened(val stream: AppKitCaptureNativeStream) : AppKitCaptureNativeOpenResult
    data class Failed(val cause: Throwable) : AppKitCaptureNativeOpenResult
}

internal sealed interface AppKitCaptureNativeStopResult {
    data object Stopped : AppKitCaptureNativeStopResult
    data object PermissionRevoked : AppKitCaptureNativeStopResult
    data object SourceLost : AppKitCaptureNativeStopResult
    data class Failed(val cause: Throwable) : AppKitCaptureNativeStopResult
}

internal fun interface AppKitCaptureNativeStream {
    fun requestStop()
}

internal interface AppKitCaptureNativeFrame {
    val width: Int
    fun copyPlanes(maxBytes: Int): List<AppKitCaptureNativePlane>
}

internal data class AppKitCaptureNativePlane(
    val bytes: ByteArray,
    val bytesPerRow: Int,
    val height: Int,
)

private class AppKitCaptureReservation(
    private val native: AppKitCaptureNativeReservation,
    override val source: CapturePortSource,
    private val request: CaptureRequest,
) : CapturePortReservation {
    private val lock = Any()
    private var closed = false
    private var started = false

    override suspend fun start(
        listener: CapturePortStreamListener,
        maxFrameBytes: Long,
    ): KadreResult<CapturePortStreamStart> {
        require(maxFrameBytes > 0L) { "maxFrameBytes must be positive" }
        val size = request.preferredSize ?: source.size ?: DEFAULT_STREAM_SIZE
        val requiredBytes = try {
            Math.multiplyExact(Math.multiplyExact(size.width.toLong(), size.height.toLong()), BGRA_BYTES_PER_PIXEL)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
        if (requiredBytes > maxFrameBytes) {
            return KadreResult.Failure(
                KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, maxFrameBytes),
            )
        }
        synchronized(lock) {
            if (closed) return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.CaptureSession))
            if (started) return KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.CaptureCollector))
            started = true
        }
        val configuration = request.toNativeConfiguration(size)
        val effective = configuration.toCaptureConfiguration()
        val copyLimit = min(maxFrameBytes, Int.MAX_VALUE.toLong()).toInt()
        return suspendCancellableCoroutine { continuation ->
            val owner = DeferredNativeOwner()
            val terminal = AtomicBoolean(false)
            continuation.invokeOnCancellation { owner.close() }
            fun terminate(termination: CapturePortTermination) {
                if (terminal.compareAndSet(false, true)) listener.onTerminated(termination)
            }
            fun terminate(outcome: CaptureOutcome) {
                terminate(CapturePortTermination.Outcome(outcome))
            }
            try {
                owner.install(native.start(
                    configuration = configuration,
                    onFrame = { frame ->
                        if (terminal.get()) return@start
                        when (val copied = frame.toPortFrame(effective, copyLimit, maxFrameBytes)) {
                            is KadreResult.Success -> listener.onFrame(copied.value)
                            is KadreResult.Failure -> {
                                owner.close()
                                terminate(CaptureOutcome.Failed(copied.reason))
                            }
                        }
                    },
                    onOpened = { opened ->
                        when (opened) {
                            is AppKitCaptureNativeOpenResult.Opened -> {
                                val stream = AppKitCapturePortStream(owner, opened.stream)
                                if (continuation.isActive) {
                                    continuation.resume(
                                        KadreResult.Success(CapturePortStreamStart(stream, effective)),
                                    )
                                } else {
                                    stream.close()
                                }
                            }

                            is AppKitCaptureNativeOpenResult.Failed -> {
                                owner.close()
                                if (continuation.isActive) {
                                    continuation.resume(
                                        KadreResult.Failure(platformFailure("stream-start-failed")),
                                    )
                                }
                            }
                        }
                    },
                    onStopped = { stopped ->
                        owner.close()
                        terminate(
                            when (stopped) {
                                AppKitCaptureNativeStopResult.Stopped ->
                                    CaptureOutcome.Stopped(CaptureStopReason.Requested)

                                AppKitCaptureNativeStopResult.PermissionRevoked ->
                                    CaptureOutcome.Stopped(CaptureStopReason.PermissionRevoked)

                                AppKitCaptureNativeStopResult.SourceLost -> {
                                    terminate(CapturePortTermination.SourceLost)
                                    return@start
                                }

                                is AppKitCaptureNativeStopResult.Failed ->
                                    CaptureOutcome.Failed(platformFailure("stream-stop-failed"))
                            },
                        )
                    },
                ))
            } catch (_: Exception) {
                owner.close()
                if (continuation.isActive) {
                    continuation.resume(KadreResult.Failure(platformFailure("stream-start-failed")))
                }
            } catch (_: LinkageError) {
                owner.close()
                if (continuation.isActive) {
                    continuation.resume(KadreResult.Failure(platformFailure("stream-start-failed")))
                }
            }
        }
    }

    override fun close() {
        val shouldClose = synchronized(lock) {
            if (closed) false else {
                closed = true
                true
            }
        }
        if (shouldClose) native.close()
    }
}

private class AppKitCapturePortStream(
    private val owner: DeferredNativeOwner,
    private val native: AppKitCaptureNativeStream,
) : CapturePortStream {
    private val closed = AtomicBoolean()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            native.requestStop()
        } finally {
            owner.close()
        }
    }
}

private class DeferredNativeOwner : AutoCloseable {
    private val lock = Any()
    private var owner: AutoCloseable? = null
    private var closed = false

    fun install(value: AutoCloseable) {
        val closeImmediately = synchronized(lock) {
            if (closed) value else {
                check(owner == null) { "native owner was installed twice" }
                owner = value
                null
            }
        }
        closeImmediately?.close()
    }

    override fun close() {
        val target = synchronized(lock) {
            if (closed) return
            closed = true
            owner.also { owner = null }
        }
        target?.close()
    }
}

private fun CaptureRequest.toNativeConfiguration(size: PhysicalSize): AppKitCaptureNativeStreamConfiguration =
    AppKitCaptureNativeStreamConfiguration(
        width = size.width,
        height = size.height,
        showsCursor = cursorMode != CaptureCursorMode.Hidden,
        minimumFrameInterval = minimumFrameInterval,
    )

private fun AppKitCaptureNativeStreamConfiguration.toCaptureConfiguration(): CaptureConfiguration = CaptureConfiguration(
    revision = CaptureConfigurationRevision(0L),
    size = PhysicalSize(width, height),
    format = PixelFormat.Bgra8,
    colorEncoding = UNKNOWN_COLOR_ENCODING,
    alphaMode = AlphaMode.Unknown,
    orientation = CaptureOrientation.Upright,
    cadence = minimumFrameInterval?.let { CaptureCadence.Variable(minimumFrameInterval = it, maximumFrameInterval = null) }
        ?: CaptureCadence.Unknown,
    region = null,
    cursorMode = if (showsCursor) CaptureCursorMode.Embedded else CaptureCursorMode.Hidden,
)

private fun AppKitCaptureNativeFrame.toPortFrame(
    configuration: CaptureConfiguration,
    copyLimit: Int,
    maximumFrameBytes: Long,
): KadreResult<CapturePortFrame> = try {
    val planes = copyPlanes(copyLimit)
    val plane = planes.singleOrNull()
        ?: return KadreResult.Failure(platformFailure("unsupported-plane-layout"))
    if (width != configuration.size.width || plane.height != configuration.size.height) {
        return KadreResult.Failure(platformFailure("unexpected-frame-size"))
    }
    val layout = PixelPlaneLayout(
        width = width,
        height = plane.height,
        rowStride = plane.bytesPerRow,
        pixelStride = BGRA_BYTES_PER_PIXEL.toInt(),
        byteCount = plane.bytes.size,
        horizontalSubsampling = 1,
        verticalSubsampling = 1,
    )
    KadreResult.Success(
        CapturePortFrame(
            size = configuration.size,
            format = PixelFormat.Bgra8,
            planes = listOf(CapturePortPlane(layout, plane.bytes)),
            configurationRevision = configuration.revision.value,
            sourceTimestamp = null,
            duration = null,
            discontinuity = null,
            colorEncoding = UNKNOWN_COLOR_ENCODING,
            alphaMode = AlphaMode.Unknown,
            orientation = CaptureOrientation.Upright,
        ),
    )
} catch (_: IllegalArgumentException) {
    KadreResult.Failure(KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, maximumFrameBytes))
} catch (_: ArithmeticException) {
    KadreResult.Failure(KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, maximumFrameBytes))
} catch (_: Exception) {
    KadreResult.Failure(platformFailure("frame-copy-failed"))
} catch (_: LinkageError) {
    KadreResult.Failure(platformFailure("frame-copy-failed"))
}

private fun AppKitCaptureNativeCapability.permissions(): CapturePermissionState = when {
    !supportsScreenCapture -> CapturePermissionState(
        PermissionState.Unavailable(KadreFailure.Unsupported(KadreOperation.CapturePermission)),
        PermissionState.Unavailable(KadreFailure.Unsupported(KadreOperation.CapturePermission)),
    )

    preflightAccessGranted -> CapturePermissionState(PermissionState.Granted, PermissionState.Granted)
    else -> CapturePermissionState(PermissionState.NotDetermined, PermissionState.NotDetermined)
}

private fun AppKitCaptureNativeCapability.initialSources(): CapturePortSources = when {
    !supportsScreenCapture -> CapturePortSources.Unavailable(KadreFailure.Unsupported(KadreOperation.CaptureRefreshSources))
    !preflightAccessGranted -> CapturePortSources.PermissionRequired(
        setOf(KadrePermission.CaptureScreen, KadrePermission.CaptureWindow),
    )

    else -> CapturePortSources.Unavailable(KadreFailure.TemporarilyUnavailable(retryable = true))
}

private fun AppKitCaptureNativeCapability.toCapabilities(): CaptureCapabilities {
    if (!supportsScreenCapture) {
        return CaptureCapabilities(
            screen = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            window = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            surface = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            sourceEnumeration = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureRefreshSources)),
            hostPicker = FeatureAvailability.Unsupported,
        )
    }
    val screenAvailability = if (preflightAccessGranted) FeatureAvailability.Available
    else FeatureAvailability.RequiresPermission(KadrePermission.CaptureScreen)
    val windowAvailability = if (preflightAccessGranted) FeatureAvailability.Available
    else FeatureAvailability.RequiresPermission(KadrePermission.CaptureWindow)
    val constraints = CaptureTargetConstraints(
        formats = setOf(PixelFormat.Bgra8),
        cursorModes = setOf(
            CaptureCursorMode.Hidden,
            CaptureCursorMode.Embedded,
            CaptureCursorMode.EmbeddedWhenAvailable,
        ),
        region = FeatureAvailability.Unsupported,
    )
    return CaptureCapabilities(
        screen = Capability.Supported(constraints, screenAvailability),
        window = Capability.Supported(constraints, windowAvailability),
        surface = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
        sourceEnumeration = Capability.Supported(Unit, screenAvailability),
        hostPicker = if (supportsHostPicker) screenAvailability else FeatureAvailability.Unsupported,
    )
}

private fun AppKitCaptureNativeSourceCatalog.toPortSources(): List<CapturePortSource> = buildList {
    displays.forEach { display ->
        add(
            CapturePortSource(
                key = CapturePortSourceKey("appkit-display", display.id),
                kind = CaptureSourceKind.Display,
                name = display.name,
                size = PhysicalSize(display.pixelWidth, display.pixelHeight),
            ),
        )
    }
    windows.forEach { window ->
        add(
            CapturePortSource(
                key = CapturePortSourceKey("appkit-window", window.id),
                kind = CaptureSourceKind.Window,
                name = window.title,
                size = null,
            ),
        )
    }
}

private fun CapturePortTarget.toNativeTarget(
    capability: AppKitCaptureNativeCapability,
): AppKitCaptureNativeTarget? = when (this) {
    CapturePortTarget.HostChoice -> if (capability.supportsHostPicker) AppKitCaptureNativeTarget.HostPicker else null
    is CapturePortTarget.Source -> when (key.namespace) {
        "appkit-display" -> AppKitCaptureNativeTarget.Display(key.value)
        "appkit-window" -> AppKitCaptureNativeTarget.Window(key.value)
        else -> null
    }

    is CapturePortTarget.Surface -> null
}

private fun AppKitCaptureNativeReservationSource.toPortSource(
    selected: CapturePortSource?,
): CapturePortSource = when (this) {
    AppKitCaptureNativeReservationSource.Unknown -> CapturePortSource(
        key = CapturePortSourceKey("appkit-host-picker", 0L),
        kind = CaptureSourceKind.HostSurface,
        name = null,
        size = null,
    )

    is AppKitCaptureNativeReservationSource.Display -> selected
        ?.takeIf { it.key == CapturePortSourceKey("appkit-display", id) }
        ?: CapturePortSource(
            key = CapturePortSourceKey("appkit-display", id),
            kind = CaptureSourceKind.Display,
            name = null,
            size = null,
        )

    is AppKitCaptureNativeReservationSource.Window -> selected
        ?.takeIf { it.key == CapturePortSourceKey("appkit-window", id) }
        ?: CapturePortSource(
            key = CapturePortSourceKey("appkit-window", id),
            kind = CaptureSourceKind.Window,
            name = title,
            size = null,
        )
}

private fun AppKitCapturePort.validateRequest(request: CaptureRequest): KadreFailure? = when {
    request.preferredFormats.any { it != PixelFormat.Bgra8 } -> KadreFailure.Unsupported(KadreOperation.CaptureOpen)
    request.region != null -> KadreFailure.Unsupported(KadreOperation.CaptureOpen)
    else -> null
}

private fun platformFailure(code: String): KadreFailure.PlatformFailure =
    KadreFailure.PlatformFailure(KadrePlatform.AppKit, "screen-capture-kit", code)

private val UNKNOWN_COLOR_ENCODING = ColorEncoding(
    primaries = ColorPrimaries.Unknown,
    transfer = TransferFunction.Unknown,
    matrix = MatrixCoefficients.Unknown,
    range = ColorRange.Unknown,
    hdr = HdrMetadata.Unknown,
)

private val DEFAULT_STREAM_SIZE = PhysicalSize(1920, 1080)
private const val BGRA_BYTES_PER_PIXEL = 4L

private object KffiAppKitCaptureNative : AppKitCaptureNative {
    override fun capability(): AppKitCaptureNativeCapability =
        ScreenCaptureControlPlanes.capability().toNativeCapability()

    override fun requestPermission(): AppKitCaptureNativePermissionResult =
        try {
            when (KffiAppKitMainThread.call(ScreenCaptureKitCaptures::requestPermission)) {
                ScreenCapturePermissionRequestResult.Granted -> AppKitCaptureNativePermissionResult.Granted
                ScreenCapturePermissionRequestResult.Denied -> AppKitCaptureNativePermissionResult.Denied
                is ScreenCapturePermissionRequestResult.Failed ->
                    AppKitCaptureNativePermissionResult.Failed(IllegalStateException("ScreenCaptureKit permission request failed"))
            }
        } catch (cause: Throwable) {
            AppKitCaptureNativePermissionResult.Failed(cause)
        }

    override fun enumerateSources(
        callback: (AppKitCaptureNativeSourceEnumerationResult) -> Unit,
    ): AutoCloseable = KffiAppKitMainThread.call {
        ScreenCaptureKitCaptures.enumerateSources { result ->
            callback(result.toNativeResult())
        }
    }

    override fun reserve(
        target: AppKitCaptureNativeTarget,
        callback: (AppKitCaptureNativeReservationResult) -> Unit,
    ): AutoCloseable = KffiAppKitMainThread.call {
        ScreenCaptureKitCaptures.reserve(target.toKffiTarget()) { result ->
            callback(result.toNativeResult())
        }
    }
}

private fun ScreenCaptureCapability.toNativeCapability(): AppKitCaptureNativeCapability = AppKitCaptureNativeCapability(
    supportsScreenCapture = supportsMacOs13Baseline,
    preflightAccessGranted = preflightScreenCaptureAccess,
    supportsHostPicker = supportsContentSharingPicker,
)

private fun ScreenCaptureSourceEnumerationResult.toNativeResult(): AppKitCaptureNativeSourceEnumerationResult = when (this) {
    is ScreenCaptureSourceEnumerationResult.Enumerated ->
        AppKitCaptureNativeSourceEnumerationResult.Enumerated(catalog.toNativeCatalog())

    is ScreenCaptureSourceEnumerationResult.Failed -> AppKitCaptureNativeSourceEnumerationResult.Failed(cause)
}

private fun ScreenCaptureSourceCatalog.toNativeCatalog(): AppKitCaptureNativeSourceCatalog = AppKitCaptureNativeSourceCatalog(
    displays = displays.map { display ->
        AppKitCaptureNativeDisplaySource(display.id, display.pixelWidth, display.pixelHeight, null)
    },
    windows = windows.map { window -> AppKitCaptureNativeWindowSource(window.id, window.title) },
)

private fun AppKitCaptureNativeTarget.toKffiTarget(): ScreenCaptureTarget = when (this) {
    AppKitCaptureNativeTarget.HostPicker -> ScreenCaptureTarget.HostPicker
    is AppKitCaptureNativeTarget.Display -> ScreenCaptureTarget.Display(id)
    is AppKitCaptureNativeTarget.Window -> ScreenCaptureTarget.Window(id)
}

private fun ScreenCaptureReservationResult.toNativeResult(): AppKitCaptureNativeReservationResult = when (this) {
    is ScreenCaptureReservationResult.Reserved ->
        AppKitCaptureNativeReservationResult.Reserved(KffiAppKitCaptureReservation(reservation))

    ScreenCaptureReservationResult.Cancelled -> AppKitCaptureNativeReservationResult.Cancelled
    is ScreenCaptureReservationResult.Failed -> AppKitCaptureNativeReservationResult.Failed(cause)
}

private class KffiAppKitCaptureReservation(
    private val reservation: ScreenCaptureReservation,
) : AppKitCaptureNativeReservation {
    override val source: AppKitCaptureNativeReservationSource = reservation.source.toNativeSource()

    override fun start(
        configuration: AppKitCaptureNativeStreamConfiguration,
        onFrame: (AppKitCaptureNativeFrame) -> Unit,
        onOpened: (AppKitCaptureNativeOpenResult) -> Unit,
        onStopped: (AppKitCaptureNativeStopResult) -> Unit,
    ): AutoCloseable = KffiAppKitMainThread.call {
        reservation.start(
            configuration = ScreenCaptureStreamConfiguration(
                width = configuration.width,
                height = configuration.height,
                showsCursor = configuration.showsCursor,
                minimumFrameInterval = configuration.minimumFrameInterval,
            ),
            onFrame = { lease -> onFrame(KffiAppKitCaptureFrame(lease)) },
            onOpened = { result -> onOpened(result.toNativeResult()) },
            onStopped = { result -> onStopped(result.toNativeResult()) },
        )
    }

    override fun close() = reservation.close()
}

private fun ScreenCaptureReservationSource.toNativeSource(): AppKitCaptureNativeReservationSource = when (this) {
    ScreenCaptureReservationSource.Unknown -> AppKitCaptureNativeReservationSource.Unknown
    is ScreenCaptureReservationSource.Display -> AppKitCaptureNativeReservationSource.Display(id)
    is ScreenCaptureReservationSource.Window -> AppKitCaptureNativeReservationSource.Window(id, title)
}

private fun ScreenCaptureOpenResult.toNativeResult(): AppKitCaptureNativeOpenResult = when (this) {
    is ScreenCaptureOpenResult.Opened -> AppKitCaptureNativeOpenResult.Opened(KffiAppKitCaptureStream(session))
    is ScreenCaptureOpenResult.Failed -> AppKitCaptureNativeOpenResult.Failed(cause)
}

private fun ScreenCaptureStopResult.toNativeResult(): AppKitCaptureNativeStopResult = when (this) {
    ScreenCaptureStopResult.Stopped -> AppKitCaptureNativeStopResult.Stopped
    is ScreenCaptureStopResult.Failed -> cause.toNativeTerminationResult()
}

private fun Throwable.toNativeTerminationResult(): AppKitCaptureNativeStopResult =
    (this as? ScreenCaptureKitFailure)?.let { failure ->
        classifyScreenCaptureKitTermination(
            domain = failure.domain,
            code = failure.code,
            preflightScreenCaptureAccess = runCatching {
                ScreenCaptureControlPlanes.capability().preflightScreenCaptureAccess
            }.getOrNull(),
            streamErrorDomain = runCatching {
                ObjCRuntime.toJavaString(SCStreamErrorDomain)
            }.getOrNull(),
        )
    } ?: AppKitCaptureNativeStopResult.Failed(this)

internal fun classifyScreenCaptureKitTermination(
    domain: String?,
    code: Long?,
    preflightScreenCaptureAccess: Boolean?,
    streamErrorDomain: String?,
): AppKitCaptureNativeStopResult? = when {
    preflightScreenCaptureAccess == false -> AppKitCaptureNativeStopResult.PermissionRevoked
    domain != null && domain == streamErrorDomain && code == SCStreamErrorCode.SCStreamErrorNoCaptureSource.value -> {
        AppKitCaptureNativeStopResult.SourceLost
    }

    else -> null
}

private class KffiAppKitCaptureStream(
    private val stream: ScreenCaptureStreamSession,
) : AppKitCaptureNativeStream {
    override fun requestStop() = KffiAppKitMainThread.call(stream::requestStop)
}

private class KffiAppKitCaptureFrame(
    private val lease: ScreenCaptureFrameLease,
) : AppKitCaptureNativeFrame {
    override val width: Int get() = lease.width

    override fun copyPlanes(maxBytes: Int): List<AppKitCaptureNativePlane> = lease.copyPlanes(maxBytes).map(ScreenCapturePlane::toNativePlane)
}

private fun ScreenCapturePlane.toNativePlane(): AppKitCaptureNativePlane =
    AppKitCaptureNativePlane(bytes, bytesPerRow, height)
