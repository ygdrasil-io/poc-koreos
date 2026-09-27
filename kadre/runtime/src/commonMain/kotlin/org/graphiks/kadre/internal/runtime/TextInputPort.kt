package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.TextDocumentRevision
import org.graphiks.kadre.input.TextInputAction
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextRange
import org.graphiks.kadre.surface.SurfaceId

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