package org.graphiks.kadre.input

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.surface.LogicalRect

public suspend fun SurfaceInput.openTextInput(config: TextInputConfig): KadreResult<TextInputSession> =
    KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.TextInput))

public enum class TextInputPurpose { Text, Name, Email, Url, Telephone, Number, Decimal, Password }
public enum class TextInputAction { Default, Done, Go, Next, Search, Send }
public enum class TextCapitalization { None, Sentences, Words, Characters }

public data class TextRange(public val startUtf16: Int, public val endExclusiveUtf16: Int) {
    init {
        require(startUtf16 >= 0 && endExclusiveUtf16 >= startUtf16) { "text range is invalid" }
    }
}

public data class TextInputConfig(
    public val purpose: TextInputPurpose = TextInputPurpose.Text,
    public val action: TextInputAction = TextInputAction.Default,
    public val capitalization: TextCapitalization = TextCapitalization.None,
    public val autocorrect: Boolean = true,
    public val multiline: Boolean = false,
    public val surroundingText: String = "",
    public val selection: TextRange = TextRange(0, 0),
    public val documentRevision: TextDocumentRevision = TextDocumentRevision(0),
) {
    init {
        require(selection.endExclusiveUtf16 <= surroundingText.length) {
            "selection must be contained in surroundingText"
        }
    }
}

public interface TextInputSession : AutoCloseable {
    public val events: Flow<TextInputEvent>
    public val state: StateFlow<TextInputState>
    override fun close()
    public suspend fun updateCursor(rect: LogicalRect, documentRevision: TextDocumentRevision): KadreResult<Unit>
    public suspend fun updateSurroundingText(
        text: String,
        selection: TextRange,
        documentRevision: TextDocumentRevision,
    ): KadreResult<Unit>
}

public sealed interface TextInputState {
    public data class Active(
        public val documentRevision: TextDocumentRevision,
        public val composingRange: TextRange?,
    ) : TextInputState

    public data class Suspended(
        public val documentRevision: TextDocumentRevision,
        public val composingRange: TextRange?,
    ) : TextInputState

    public data object Closed : TextInputState
}

public sealed interface TextInputEvent {
    public val stamp: EventStamp
    public val baseRevision: TextDocumentRevision

    public data class Replace(
        public val range: TextRange,
        public val text: String,
        override val baseRevision: TextDocumentRevision,
        override val stamp: EventStamp,
    ) : TextInputEvent

    public data class SelectionChanged(
        public val selection: TextRange,
        override val baseRevision: TextDocumentRevision,
        override val stamp: EventStamp,
    ) : TextInputEvent

    public data class CompositionChanged(
        public val range: TextRange?,
        public val text: String,
        override val baseRevision: TextDocumentRevision,
        override val stamp: EventStamp,
    ) : TextInputEvent

    public data class Action(
        public val action: TextInputAction,
        override val baseRevision: TextDocumentRevision,
        override val stamp: EventStamp,
    ) : TextInputEvent
}

public enum class DropItemKind { Text, File, Uri, Binary }

public data class DropItemDescriptor(
    public val displayName: String?,
    public val sizeBytes: Long?,
    public val mimeTypes: List<String>,
    public val kind: DropItemKind,
) {
    init {
        require(sizeBytes == null || sizeBytes >= 0) { "sizeBytes must be non-negative" }
        require(mimeTypes.distinct().size == mimeTypes.size) { "mimeTypes must not contain duplicates" }
        require(mimeTypes.all(::isCanonicalMediaType)) { "mimeTypes must be canonical lowercase media types" }
    }
}

public interface DropOffer {
    public val id: DropOfferId
    public val items: List<DropItemDescriptor>
    public val state: StateFlow<DropOfferState>
    public suspend fun claimTransfer(): KadreResult<DropTransfer>
}

public sealed interface DropOfferState {
    public data object Presented : DropOfferState
    public data object Accepted : DropOfferState
    public data object TransferAvailable : DropOfferState
    public data object Claimed : DropOfferState
    public data class Terminated(public val reason: DropOfferTerminationReason) : DropOfferState
}

public sealed interface DropOfferTerminationReason {
    public data object Rejected : DropOfferTerminationReason
    public data object LeftSurface : DropOfferTerminationReason
    public data object OfferExpired : DropOfferTerminationReason
    public data object ClaimTimedOut : DropOfferTerminationReason
    public data object OwnerClosed : DropOfferTerminationReason
    public data class Failed(public val failure: KadreFailure) : DropOfferTerminationReason
}

public interface DropTransfer : AutoCloseable {
    public val items: List<DroppedItem>
    override fun close()
}

public interface DroppedItem {
    public val descriptor: DropItemDescriptor
    public val readMode: DropItemReadMode
    public suspend fun collectBytes(
        maxBytes: Long,
        collector: suspend (ByteArray) -> Unit,
    ): KadreResult<Unit>
}

public enum class DropItemReadMode { Replayable, SingleUse }

@DelicateKadreApi
public suspend fun SurfaceInput.requestRawInput(): KadreResult<RawInputAccess> =
    KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RawInputAccess))

@DelicateKadreApi
public interface RawInputAccess : AutoCloseable {
    public val state: StateFlow<RawInputState>
    public val events: Flow<RawInputEvent>
    override fun close()
}

public sealed interface RawInputState {
    public data object Active : RawInputState
    public data class Suspended(public val reason: KadreFailure) : RawInputState
    public data object Closed : RawInputState
}

public enum class RawInputUnit { DeviceCount, LogicalPixel, PhysicalPixel }

public data class RawInputEvent private constructor(
    public val deltaX: Double,
    public val deltaY: Double,
    public val unit: RawInputUnit,
    public val deviceId: DeviceId?,
    public val stamp: EventStamp,
    private val canonicalized: Unit,
) {
    public constructor(
        deltaX: Double,
        deltaY: Double,
        unit: RawInputUnit,
        deviceId: DeviceId?,
        stamp: EventStamp,
    ) : this(
        canonicalRawDelta(deltaX, "deltaX"),
        canonicalRawDelta(deltaY, "deltaY"),
        unit,
        deviceId,
        stamp,
        Unit,
    )

    public fun copy(
        deltaX: Double = this.deltaX,
        deltaY: Double = this.deltaY,
        unit: RawInputUnit = this.unit,
        deviceId: DeviceId? = this.deviceId,
        stamp: EventStamp = this.stamp,
    ): RawInputEvent = RawInputEvent(deltaX, deltaY, unit, deviceId, stamp)

    override fun toString(): String =
        "RawInputEvent(deltaX=$deltaX, deltaY=$deltaY, unit=$unit, deviceId=$deviceId, stamp=$stamp)"
}

private fun canonicalRawDelta(value: Double, name: String): Double {
    require(value.isFinite()) { "$name must be finite" }
    return if (value == 0.0) 0.0 else value
}

private fun isCanonicalMediaType(value: String): Boolean {
    if (value.isEmpty() || value != value.lowercase() || ';' in value || value.any { it.code !in 0x21..0x7e }) return false
    val slash = value.indexOf('/')
    return slash > 0 && slash == value.lastIndexOf('/') && slash < value.lastIndex
}
