package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DeviceId
import org.graphiks.kadre.input.DropOfferId
import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.GestureKind
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.PenState
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.input.ScrollDelta
import org.graphiks.kadre.input.TouchPhase
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceVisibility

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

/** Complete immutable native snapshot used to initialise one portable runtime surface. */
public data class SurfaceInitialSnapshot(
    public val metrics: SurfaceMetrics,
    public val focus: SurfaceFocus,
    public val visibility: SurfaceVisibility,
    public val occlusion: SurfaceOcclusion,
    public val appearance: SurfaceAppearance,
)

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
