package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DeviceId
import org.graphiks.kadre.input.DropItemDescriptor
import org.graphiks.kadre.input.DropItemReadMode
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
import org.graphiks.kadre.input.TextDocumentRevision
import org.graphiks.kadre.input.TextInputAction
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextRange
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
 * Backend-owned source retained by one runtime-owned drop offer.
 *
 * The source is Kotlin-only: implementations must not allow a borrowed native pasteboard object
 * or filesystem path to escape through this interface. The runtime closes it on every terminal
 * path that has not handed the resulting transfer to the application.
 */
public interface DropTransferSource : AutoCloseable {
    public val items: List<DropItemSource>

    public override fun close()
}

/** One deferred byte source behind a portable dropped item. */
public interface DropItemSource {
    public val descriptor: DropItemDescriptor
    public val readMode: DropItemReadMode

    /** Delivers source-owned chunks no larger than [maxChunkBytes]. */
    public suspend fun collectBytes(
        maxChunkBytes: Int,
        collector: suspend (ByteArray) -> Unit,
    ): KadreResult<Unit>
}

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

/**
 * Unstable backend SPI for one surface-owned text input session.
 *
 * The boundary contains Kotlin values only. A backend owns any native receiver behind the
 * [TextInputOwner] and must revoke [TextInputOpenCommand.onObservation] before closing it.
 */
public interface TextInputPort {
    public val capability: Capability<Unit>

    public fun open(command: TextInputOpenCommand): KadreResult<TextInputOwner>

    public suspend fun updateCursor(command: TextInputCursorCommand): KadreResult<Unit>

    public suspend fun updateDocument(command: TextInputDocumentCommand): KadreResult<Unit>
}

/** One native text-input owner whose lifetime is bounded by its runtime session. */
public interface TextInputOwner : AutoCloseable {
    public override fun close()
}

/** Immutable input snapshot and callback admitted when a backend opens text input. */
public data class TextInputOpenCommand(
    public val surfaceId: SurfaceId,
    public val config: TextInputConfig,
    public val onObservation: (TextInputObservation) -> Boolean,
)

/** Immutable cursor geometry applied by the backend for one live owner. */
public data class TextInputCursorCommand(
    public val owner: TextInputOwner,
    public val rect: org.graphiks.kadre.surface.LogicalRect,
    public val documentRevision: TextDocumentRevision,
)

/** Immutable document snapshot applied by the backend for one live owner. */
public data class TextInputDocumentCommand(
    public val owner: TextInputOwner,
    public val text: String,
    public val selection: TextRange,
    public val documentRevision: TextDocumentRevision,
)

/** Immutable native IME observation. The runtime assigns its public [EventStamp]. */
public sealed interface TextInputObservation {
    public val baseRevision: TextDocumentRevision

    public data class Replace(
        public val range: TextRange,
        public val text: String,
        override val baseRevision: TextDocumentRevision,
    ) : TextInputObservation

    public data class SelectionChanged(
        public val selection: TextRange,
        override val baseRevision: TextDocumentRevision,
    ) : TextInputObservation

    public data class CompositionChanged(
        public val range: TextRange?,
        public val text: String,
        /** Selection within [text], or null when the composition has ended. */
        public val selection: TextRange?,
        override val baseRevision: TextDocumentRevision,
    ) : TextInputObservation

    public data class Action(
        public val action: TextInputAction,
        override val baseRevision: TextDocumentRevision,
    ) : TextInputObservation
}

/** Creates the text-input port bound to a runtime surface. */
public fun interface TextInputPortFactory {
    public fun create(surfaceId: SurfaceId): TextInputPort
}

internal object UnsupportedTextInputPort : TextInputPort {
    override val capability: Capability<Unit> = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.TextInput))

    override fun open(command: TextInputOpenCommand): KadreResult<TextInputOwner> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.TextInput))

    override suspend fun updateCursor(command: TextInputCursorCommand): KadreResult<Unit> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.TextInput))

    override suspend fun updateDocument(command: TextInputDocumentCommand): KadreResult<Unit> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.TextInput))
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
