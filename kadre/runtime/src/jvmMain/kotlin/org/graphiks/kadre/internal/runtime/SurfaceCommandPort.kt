package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DeviceId
import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.PenState
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.input.ScrollDelta
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.surface.toPhysical

/**
 * Unstable backend SPI used by the portable surface state machine.
 *
 * A backend applies commands on its native owner thread and reports only effective values. It
 * must not maintain a second public surface snapshot. Redraw completion and native observations
 * return through [SurfaceStimulus].
 */
public interface SurfaceCommandPort {
    /** Admits one coalesced native invalidation request. */
    public fun requestRedraw(command: SurfaceRedrawCommand): KadreResult<Unit>

    /** Applies the requested fields and returns one explicit outcome for every admitted field. */
    public suspend fun apply(command: SurfaceUpdateCommand): KadreResult<SurfaceUpdateCommandOutcome>
}

/** One generation-safe native invalidation command. */
public data class SurfaceRedrawCommand(
    public val surfaceId: SurfaceId,
    public val generation: SurfaceRedrawGeneration,
)

/** Opaque generation echoed by the backend when it consumes a redraw command. */
@JvmInline
public value class SurfaceRedrawGeneration internal constructor(public val value: Long) {
    init {
        require(value >= 0L) { "value must be non-negative" }
    }

    public companion object {
        /** Reconstructs the opaque generation echoed by a native backend. */
        public fun fromNative(value: Long): SurfaceRedrawGeneration = SurfaceRedrawGeneration(value)
    }
}

/** Immutable effective metrics captured together by the native backend. */
public data class SurfaceMetrics(
    public val logicalSize: LogicalSize,
    public val physicalSize: PhysicalSize,
    public val scaleFactor: Double,
    public val safeAreaInsets: LogicalInsets,
) {
    init {
        require(scaleFactor.isFinite() && scaleFactor > 0.0) {
            "scaleFactor must be finite and positive"
        }
        require(physicalSize == logicalSize.toPhysical(scaleFactor)) {
            "physicalSize must be derived from logicalSize and scaleFactor"
        }
    }
}

/** Complete immutable native snapshot used to initialise one portable runtime surface. */
public data class SurfaceInitialSnapshot(
    public val metrics: SurfaceMetrics,
    public val focus: SurfaceFocus,
    public val visibility: SurfaceVisibility,
    public val occlusion: SurfaceOcclusion,
    public val theme: SurfaceTheme,
)

/**
 * Complete immutable ingress understood by the runtime.
 *
 * Backends may safely submit duplicate and late values. The runtime performs deduplication,
 * revision allocation and terminal rejection.
 */
public sealed interface SurfaceStimulus {
    public val surfaceId: SurfaceId

    public data class MetricsChanged(
        override val surfaceId: SurfaceId,
        public val metrics: SurfaceMetrics,
    ) : SurfaceStimulus

    public data class FocusChanged(
        override val surfaceId: SurfaceId,
        public val focus: SurfaceFocus,
    ) : SurfaceStimulus

    public data class VisibilityChanged(
        override val surfaceId: SurfaceId,
        public val visibility: SurfaceVisibility,
        public val occlusion: SurfaceOcclusion,
    ) : SurfaceStimulus

    public data class ThemeChanged(
        override val surfaceId: SurfaceId,
        public val theme: SurfaceTheme,
    ) : SurfaceStimulus

    /** Acknowledges the exact native redraw generation that was consumed. */
    public data class RedrawConsumed(
        override val surfaceId: SurfaceId,
        public val generation: SurfaceRedrawGeneration,
    ) : SurfaceStimulus

    /** One immutable keyboard observation. Identity, stamps and revisions stay runtime-owned. */
    public data class KeyChanged(
        override val surfaceId: SurfaceId,
        public val physicalKey: PhysicalKey,
        public val logicalKey: LogicalKey,
        public val location: KeyLocation,
        public val keyState: KeyState,
        public val repeat: Boolean,
        public val modifiers: KeyboardModifiers,
        public val deviceId: DeviceId? = null,
    ) : SurfaceStimulus {
        init {
            require(keyState == KeyState.Pressed || !repeat) { "a key release cannot repeat" }
        }
    }

    /**
     * Reports which native input observers have been structurally installed for this surface.
     *
     * This is a capability observation, not an input packet: it neither fabricates an input
     * event nor asserts that a callback is currently delivering native input.
     */
    public data class InputObservationChanged(
        override val surfaceId: SurfaceId,
        public val keyboardInstalled: Boolean,
        public val pointerInstalled: Boolean,
    ) : SurfaceStimulus

    /** One pointer-entry observation; the runtime assigns the public [PointerId]. */
    public data class PointerEntered(
        override val surfaceId: SurfaceId,
        public val kind: PointerKind,
        public val position: LogicalPoint,
        public val deviceId: DeviceId? = null,
    ) : SurfaceStimulus

    /** One pointer-motion observation; the runtime retains pointer identity and state. */
    public data class PointerMoved(
        override val surfaceId: SurfaceId,
        public val kind: PointerKind,
        public val position: LogicalPoint,
        public val delta: LogicalDelta,
        public val pressure: Double?,
        public val pen: PenState?,
        public val deviceId: DeviceId? = null,
    ) : SurfaceStimulus

    /** One pointer-button observation. */
    public data class PointerButtonChanged(
        override val surfaceId: SurfaceId,
        public val kind: PointerKind,
        public val button: PointerButton,
        public val buttonState: PointerButtonState,
        public val position: LogicalPoint,
        public val pressure: Double?,
        public val pen: PenState?,
        public val deviceId: DeviceId? = null,
    ) : SurfaceStimulus

    /** One pointer-exit observation; its last position comes from runtime state. */
    public data class PointerLeft(
        override val surfaceId: SurfaceId,
        public val kind: PointerKind,
        public val deviceId: DeviceId? = null,
    ) : SurfaceStimulus

    /**
     * One scroll observation. Equal [coalescingBoundary] values may merge; a backend changes it
     * whenever its native phase or momentum boundary changes. The boundary itself is not public
     * input state.
     */
    public data class Scroll(
        override val surfaceId: SurfaceId,
        public val delta: ScrollDelta,
        public val coalescingBoundary: Long,
        public val deviceId: DeviceId? = null,
    ) : SurfaceStimulus {
        init {
            require(coalescingBoundary >= 0L) { "coalescingBoundary must be non-negative" }
        }
    }

    /** Closes all ingress while preserving the last effective snapshot. */
    public data class Detached(override val surfaceId: SurfaceId) : SurfaceStimulus
}

/** Only fields admitted by runtime capabilities are present in this backend command. */
public data class SurfaceUpdateCommand(
    public val surfaceId: SurfaceId,
    public val cursor: PropertyChange<CursorStyle> = PropertyChange.Unchanged,
    public val pointerCapture: PropertyChange<PointerCaptureMode> = PropertyChange.Unchanged,
    public val hitTesting: PropertyChange<HitTestingMode> = PropertyChange.Unchanged,
    public val inputDefaultBehavior: PropertyChange<InputDefaultBehavior> = PropertyChange.Unchanged,
)

/** Effective backend result for one surface update. */
public data class SurfaceUpdateCommandOutcome(
    public val cursor: SurfaceFieldOutcome<CursorStyle> = SurfaceFieldOutcome.Unchanged,
    public val pointerCapture: SurfaceFieldOutcome<PointerCaptureMode> = SurfaceFieldOutcome.Unchanged,
    public val hitTesting: SurfaceFieldOutcome<HitTestingMode> = SurfaceFieldOutcome.Unchanged,
    public val inputDefaultBehavior: SurfaceFieldOutcome<InputDefaultBehavior> = SurfaceFieldOutcome.Unchanged,
)

/** One typed field acknowledgement; [Applied] always carries the effective native value. */
public sealed interface SurfaceFieldOutcome<out T> {
    public data object Unchanged : SurfaceFieldOutcome<Nothing>
    public data class Applied<T>(public val value: T) : SurfaceFieldOutcome<T>
    public data class Rejected(public val failure: KadreFailure) : SurfaceFieldOutcome<Nothing>
}

internal object UnsupportedSurfaceCommandPort : SurfaceCommandPort {
    override fun requestRedraw(command: SurfaceRedrawCommand): KadreResult<Unit> =
        KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = false))

    override suspend fun apply(command: SurfaceUpdateCommand): KadreResult<SurfaceUpdateCommandOutcome> =
        KadreResult.Success(
            SurfaceUpdateCommandOutcome(
                cursor = command.cursor.rejectedWhenChanged(),
                pointerCapture = command.pointerCapture.rejectedWhenChanged(),
                hitTesting = command.hitTesting.rejectedWhenChanged(),
                inputDefaultBehavior = command.inputDefaultBehavior.rejectedWhenChanged(),
            ),
        )
}

private fun PropertyChange<*>.rejectedWhenChanged(): SurfaceFieldOutcome<Nothing> =
    if (this is PropertyChange.Unchanged) {
        SurfaceFieldOutcome.Unchanged
    } else {
        SurfaceFieldOutcome.Rejected(KadreFailure.Unsupported(KadreOperation.UpdateSurface))
    }
