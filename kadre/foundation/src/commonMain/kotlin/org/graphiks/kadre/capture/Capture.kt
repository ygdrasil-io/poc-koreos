package org.graphiks.kadre.capture

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.KadrePermission
import org.graphiks.kadre.input.PermissionState
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.SurfaceId
import kotlin.time.Duration

public interface CaptureManager {
    public val state: StateFlow<CaptureManagerState>

    public suspend fun requestPermission(scope: CapturePermissionScope): KadreResult<CaptureManagerState>
    public suspend fun refreshSources(): KadreResult<CaptureManagerState>
    public suspend fun open(request: CaptureRequest): KadreResult<CaptureSession>
}

public data class CaptureManagerState(
    public val permissions: CapturePermissionState,
    public val capabilities: CaptureCapabilities,
    public val sources: CaptureSources,
    public val revision: CaptureManagerRevision,
)

public sealed interface CaptureSources {
    public data class Enumerated(public val values: List<CaptureSource>) : CaptureSources {
        init { require(values.map(CaptureSource::id).distinct().size == values.size) { "source IDs must be unique" } }
    }

    public data object HostPickerOnly : CaptureSources

    public data class PermissionRequired(public val required: Set<KadrePermission>) : CaptureSources {
        init {
            require(required.isNotEmpty()) { "required permissions must not be empty" }
            require(required.all { it == KadrePermission.CaptureScreen || it == KadrePermission.CaptureWindow }) {
                "capture source inventory accepts only capture permissions"
            }
        }
    }

    public data class Unavailable(public val failure: KadreFailure) : CaptureSources
}

public enum class CaptureSourceKind { Display, Window, HostSurface }

public data class CaptureSource(
    public val id: CaptureSourceId,
    public val kind: CaptureSourceKind,
    public val name: String?,
    public val size: PhysicalSize?,
    public val managerRevision: CaptureManagerRevision,
)

public sealed interface CaptureTarget {
    public data object HostChoice : CaptureTarget
    public data class Source(public val id: CaptureSourceId, public val managerRevision: CaptureManagerRevision) : CaptureTarget
    public data class Surface(public val id: SurfaceId) : CaptureTarget
}

public data class CaptureRequest(
    public val target: CaptureTarget = CaptureTarget.HostChoice,
    public val preferredSize: PhysicalSize? = null,
    public val preferredFormats: List<PixelFormat> = emptyList(),
    public val region: CaptureRegion? = null,
    public val cursorMode: CaptureCursorMode = CaptureCursorMode.EmbeddedWhenAvailable,
    public val minimumFrameInterval: Duration? = null,
) {
    init {
        require(preferredFormats.distinct().size == preferredFormats.size) {
            "preferredFormats must not contain duplicates"
        }
        require(minimumFrameInterval == null || minimumFrameInterval.isFinite() && minimumFrameInterval.isPositive()) {
            "minimumFrameInterval must be finite and positive"
        }
    }
}

public enum class CapturePermissionScope { Screen, Window }

public data class CapturePermissionState(public val screen: PermissionState, public val window: PermissionState)

public data class CaptureTargetConstraints(
    public val formats: Set<PixelFormat>,
    public val cursorModes: Set<CaptureCursorMode>,
    public val region: FeatureAvailability,
) {
    init {
        require(formats.isNotEmpty()) { "formats must not be empty" }
        require(cursorModes.isNotEmpty()) { "cursorModes must not be empty" }
    }
}

public data class CaptureCapabilities(
    public val screen: Capability<CaptureTargetConstraints>,
    public val window: Capability<CaptureTargetConstraints>,
    public val surface: Capability<CaptureTargetConstraints>,
    public val sourceEnumeration: Capability<Unit>,
    public val hostPicker: FeatureAvailability,
)

public data class CaptureRegion(public val rect: PhysicalRect) {
    init {
        require(rect.origin.x >= 0 && rect.origin.y >= 0) { "capture region origin must be non-negative" }
    }
}

public enum class CaptureCursorMode { Hidden, Embedded, EmbeddedWhenAvailable }

public enum class CaptureOrientation {
    Upright,
    Rotated90,
    Rotated180,
    Rotated270,
    MirroredUpright,
    Mirrored90,
    Mirrored180,
    Mirrored270,
}

public interface CaptureSession : AutoCloseable {
    public val source: CaptureSource
    public val state: StateFlow<CaptureSessionState>
    public val events: Flow<CaptureEvent>
    public val diagnostics: Flow<CaptureDiagnostic>

    override fun close()
    public fun requestStop()
    public suspend fun awaitTermination(): CaptureOutcome
    public suspend fun collectFrames(collector: suspend (CaptureFrame) -> Unit): KadreResult<Unit>
}

public sealed interface CaptureSessionState {
    public data object Ready : CaptureSessionState
    public data class Streaming(public val configuration: CaptureConfiguration) : CaptureSessionState
    public data object Stopping : CaptureSessionState
    public data class Terminated(public val outcome: CaptureOutcome) : CaptureSessionState
}

public data class CaptureConfiguration(
    public val revision: CaptureConfigurationRevision,
    public val size: PhysicalSize,
    public val format: PixelFormat,
    public val colorEncoding: ColorEncoding,
    public val alphaMode: AlphaMode,
    public val orientation: CaptureOrientation,
    public val cadence: CaptureCadence,
    public val region: CaptureRegion?,
    public val cursorMode: CaptureCursorMode,
)

public sealed interface CaptureCadence {
    public data class Fixed(public val frameInterval: Duration) : CaptureCadence {
        init { requirePositiveDuration(frameInterval, "frameInterval") }
    }

    public data class Variable(
        public val minimumFrameInterval: Duration?,
        public val maximumFrameInterval: Duration?,
    ) : CaptureCadence {
        init {
            require(minimumFrameInterval != null || maximumFrameInterval != null) {
                "variable cadence requires at least one bound"
            }
            if (minimumFrameInterval != null) requirePositiveDuration(minimumFrameInterval, "minimumFrameInterval")
            if (maximumFrameInterval != null) requirePositiveDuration(maximumFrameInterval, "maximumFrameInterval")
            require(
                minimumFrameInterval == null ||
                    maximumFrameInterval == null ||
                    minimumFrameInterval <= maximumFrameInterval,
            ) { "minimumFrameInterval must not exceed maximumFrameInterval" }
        }
    }

    public data object Unknown : CaptureCadence
}

public sealed interface CaptureOutcome {
    public data object SourceCompleted : CaptureOutcome
    public data class Stopped(public val reason: CaptureStopReason) : CaptureOutcome
    public data class Failed(public val failure: KadreFailure) : CaptureOutcome
}

public enum class CaptureStopReason {
    Requested,
    CollectorCancelled,
    CollectorFailed,
    ParentSessionStopping,
    PermissionRevoked,
}

public sealed interface CaptureEvent {
    public val stamp: EventStamp
    public data class StreamingStarted(public val configuration: CaptureConfiguration, override val stamp: EventStamp) : CaptureEvent
    public data class Reconfigured(public val configuration: CaptureConfiguration, override val stamp: EventStamp) : CaptureEvent
    public data class Paused(public val reason: KadreFailure?, override val stamp: EventStamp) : CaptureEvent
    public data class Resumed(public val configuration: CaptureConfiguration, override val stamp: EventStamp) : CaptureEvent
}

public enum class CaptureDiscontinuity {
    PauseResume,
    TimestampReset,
    DroppedFrames,
    DuplicateFrame,
    SourceReconfigured,
}

public sealed interface CaptureDiagnostic {
    public val stamp: EventStamp

    public data class FrameDropped(public val count: Long, override val stamp: EventStamp) : CaptureDiagnostic {
        init { require(count > 0) { "count must be positive" } }
    }

    public data class TimestampDiscontinuity(
        public val discontinuity: CaptureDiscontinuity,
        override val stamp: EventStamp,
    ) : CaptureDiagnostic

    public data class BackendFallback(
        public val requested: PixelFormat?,
        public val effective: PixelFormat,
        override val stamp: EventStamp,
    ) : CaptureDiagnostic
}

private fun requirePositiveDuration(value: Duration, name: String) {
    require(value.isFinite() && value.isPositive()) { "$name must be finite and positive" }
}
