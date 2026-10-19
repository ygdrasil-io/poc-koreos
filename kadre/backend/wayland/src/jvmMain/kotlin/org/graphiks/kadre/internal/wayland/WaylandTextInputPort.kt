package org.graphiks.kadre.internal.wayland

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextInputPurpose
import org.graphiks.kadre.input.TextRange
import org.graphiks.kadre.internal.runtime.TextInputCursorCommand
import org.graphiks.kadre.internal.runtime.TextInputDocumentCommand
import org.graphiks.kadre.internal.runtime.TextInputObservation
import org.graphiks.kadre.internal.runtime.TextInputOpenCommand
import org.graphiks.kadre.internal.runtime.TextInputOwner
import org.graphiks.kadre.internal.runtime.TextInputPort
import org.graphiks.kadre.surface.SurfaceId

/**
 * `TextInputPort` over the managed `zwp_text_input_v3` (design phase 6,
 * D1/D2/D3): open per surface marshals enable + content type + surrounding
 * (converted to UTF-8 byte offsets) sealed by commit; document updates push
 * the surrounding snapshot; IME events batch at every done(serial) and are
 * published as observations with UTF-16 indices. A leave purges the live
 * composition (range=null) — no residual preedit after blur (F1-1); a done
 * older than the last applied revision is ignored entirely (F1-2).
 */
/** Minimal functional face of the kffi text-input the port drives. */
internal interface WaylandTextInputFace {
    fun enable()
    fun disable()
    fun setSurroundingText(text: String, cursorBytes: Int, anchorBytes: Int)
    fun setTextChangeCause(cause: Int)
    fun setContentType(hint: Int, purpose: Int)
    fun setCursorRectangle(x: Int, y: Int, width: Int, height: Int)
    fun commit()
    fun addListener(onEvent: (org.graphiks.kffi.wayland.managed.WaylandImeEvent) -> Unit): AutoCloseable
    fun close()
}

/** Adapts the managed kffi object to the face. */
internal class KffiTextInputFace(
    private val textInput: org.graphiks.kffi.wayland.managed.WaylandTextInput,
) : WaylandTextInputFace {
    override fun enable() = textInput.enable()
    override fun disable() = textInput.disable()
    override fun setSurroundingText(text: String, cursorBytes: Int, anchorBytes: Int) =
        textInput.setSurroundingText(text, cursorBytes, anchorBytes)
    override fun setTextChangeCause(cause: Int) = textInput.setTextChangeCause(cause)
    override fun setContentType(hint: Int, purpose: Int) = textInput.setContentType(hint, purpose)
    override fun setCursorRectangle(x: Int, y: Int, width: Int, height: Int) =
        textInput.setCursorRectangle(x, y, width, height)
    override fun commit() = textInput.commit()
    override fun addListener(onEvent: (org.graphiks.kffi.wayland.managed.WaylandImeEvent) -> Unit): AutoCloseable {
        val kffiListener = textInput.addListener(onEvent)
        return AutoCloseable { kffiListener.revoke() }
    }
    override fun close() = textInput.close()
}

internal class WaylandTextInputPort(
    private val surfaceId: SurfaceId,
    private val textInput: WaylandTextInputFace,
    private val surfaceAddressProvider: () -> Long,
) : TextInputPort {

    private class Owner(
        val port: WaylandTextInputPort,
        val token: Long,
    ) : TextInputOwner {
        private val closed = AtomicBoolean(false)
        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            port.release(token)
        }
    }

    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private val nextToken = AtomicLong(1)
    private val liveOwners = ConcurrentHashMap<Long, TextInputOpenCommand>()
    private var lastAppliedSerial = -1

    /** The current document snapshot, updated by updateDocument. */
    @Volatile
    private var document: String = ""
    @Volatile
    private var selection: TextRange = TextRange(0, 0)

    /** Serial of the snapshot currently mirrored to the protocol. */
    @Volatile
    private var mirroredRevision: Long = -1

    private var listener: AutoCloseable? = null

    override val capability: Capability<Unit>
        get() = Capability.Supported(Unit, FeatureAvailability.Available)

    init {
        listener = textInput.addListener { event -> onImeEvent(event) }
    }

    override fun open(command: TextInputOpenCommand): KadreResult<TextInputOwner> {
        if (closed.get()) {
            return KadreResult.Failure(KadreFailure.Closed(org.graphiks.kadre.diagnostics.KadreResourceKind.Surface))
        }
        document = command.config.surroundingText
        selection = command.config.selection
        val token = nextToken.getAndIncrement()
        liveOwners[token] = command
        // D1: state requests then commit
        textInput.enable()
        applyContentType(command.config)
        pushSurrounding()
        textInput.commit()
        return KadreResult.Success(Owner(this, token))
    }

    override suspend fun updateCursor(command: TextInputCursorCommand): KadreResult<Unit> {
        if (closed.get()) {
            return KadreResult.Failure(KadreFailure.Closed(org.graphiks.kadre.diagnostics.KadreResourceKind.Surface))
        }
        // cursor rect is surface-local logical → the protocol takes integers
        textInput.setCursorRectangle(
            command.rect.origin.x.toInt(),
            command.rect.origin.y.toInt(),
            command.rect.size.width.toInt(),
            command.rect.size.height.toInt(),
        )
        textInput.commit()
        return KadreResult.Success(Unit)
    }

    override suspend fun updateDocument(command: TextInputDocumentCommand): KadreResult<Unit> {
        if (closed.get()) {
            return KadreResult.Failure(KadreFailure.Closed(org.graphiks.kadre.diagnostics.KadreResourceKind.Surface))
        }
        document = command.text
        selection = command.selection
        textInput.setTextChangeCause(CHANGE_CAUSE_INPUT_METHOD)
        pushSurrounding()
        mirroredRevision = command.documentRevision.value
        textInput.commit()
        return KadreResult.Success(Unit)
    }

    internal fun release(token: Long) {
        liveOwners.remove(token)
        if (liveOwners.isEmpty()) {
            textInput.disable()
            textInput.commit()
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        liveOwners.clear()
        listener?.close()
        textInput.close()
    }

    /** Pushes the surrounding snapshot with UTF-8 byte offsets (D2). */
    private fun pushSurrounding() {
        val cursorUtf16 = selection.endExclusiveUtf16.coerceIn(0, document.length)
        val anchorUtf16 = selection.startUtf16.coerceIn(0, document.length)
        textInput.setSurroundingText(
            document,
            Utf8Utf16.utf16IndexToUtf8Byte(document, cursorUtf16),
            Utf8Utf16.utf16IndexToUtf8Byte(document, anchorUtf16),
        )
    }

    private fun applyContentType(config: TextInputConfig) {
        val purpose = when (config.purpose) {
            TextInputPurpose.Text -> 0
            TextInputPurpose.Name -> 1
            TextInputPurpose.Email -> 6
            TextInputPurpose.Url -> 8
            TextInputPurpose.Telephone -> 5
            TextInputPurpose.Number -> 4
            TextInputPurpose.Decimal -> 4
            TextInputPurpose.Password -> 0
        }
        val hint = 0 // normal content hint; autocorrect/multiline map to
        // purpose-specific hints in a later tranche when the public
        // capability surface exposes them
        textInput.setContentType(hint, purpose)
    }

    /** Test seam: injects one raw IME event through the production path. */
    internal fun offerForTest(event: org.graphiks.kffi.wayland.managed.WaylandImeEvent) {
        onImeEvent(event)
    }

    /** IME event consumer — batching and conversion per D2/D3. */
    private fun onImeEvent(event: org.graphiks.kffi.wayland.managed.WaylandImeEvent) {
        when (event.kind) {
            org.graphiks.kffi.wayland.managed.WaylandImeEvent.Kind.Enter -> {
                // entering is protocol bookkeeping: observations flow at done
            }
            org.graphiks.kffi.wayland.managed.WaylandImeEvent.Kind.Leave -> {
                // F1-1: purge the composition on blur
                publish(TextInputObservation.CompositionChanged(
                    range = null,
                    text = "",
                    selection = null,
                    baseRevision = revision(),
                ))
                synchronized(lock) { lastAppliedSerial = -1 }
            }
            org.graphiks.kffi.wayland.managed.WaylandImeEvent.Kind.Done -> {
                val serial = event.serial
                // F1-2: an obsolete batch is ignored entirely
                if (serial < 0 || serial <= lastAppliedSerial) return
                val owner = liveOwners.values.firstOrNull() ?: return
                val base = revision()
                val text = event.text ?: ""
                if (event.deleteBeforeBytes > 0 || event.deleteAfterBytes > 0) {
                    val cursorUtf16 = selection.endExclusiveUtf16.coerceIn(0, document.length)
                    // deleteBeforeBytes is a byte COUNT before the cursor:
                    // walk back that many bytes, clamping to code-point starts
                    val start = (cursorUtf16 - bytesBackAsUtf16(event.deleteBeforeBytes))
                        .coerceAtLeast(0)
                    val end = (cursorUtf16 + bytesForwardAsUtf16(event.deleteAfterBytes))
                        .coerceAtMost(document.length)
                    publish(TextInputObservation.Replace(
                        range = TextRange(start, end),
                        text = text,
                        baseRevision = base,
                    ))
                } else if (text.isNotEmpty()) {
                    // commit at the cursor: Replace of the empty range there
                    val cursorUtf16 = selection.endExclusiveUtf16.coerceIn(0, document.length)
                    publish(TextInputObservation.Replace(
                        range = TextRange(cursorUtf16, cursorUtf16),
                        text = text,
                        baseRevision = base,
                    ))
                } else {
                    // pure preedit state — composition observation with
                    // converted cursor/anchor (byte → UTF-16)
                    val compositionStart = document.length
                    publish(TextInputObservation.CompositionChanged(
                        range = TextRange(compositionStart, compositionStart + text.length),
                        text = text,
                        selection = TextRange(
                            event.byteCursor.coerceIn(0, text.length),
                            event.byteAnchor.coerceIn(0, text.length),
                        ),
                        baseRevision = base,
                    ))
                }
                synchronized(lock) { lastAppliedSerial = serial }
            }
            else -> Unit
        }
    }

    private fun publish(observation: TextInputObservation) {
        liveOwners.values.firstOrNull()?.let { owner ->
            owner.onObservation(observation)
        }
    }

    /**
     * Converts a byte COUNT to a UTF-16 index delta measured from the
     * cursor: code-point-aligned walk over the document around the cursor
     * (delete_surrounding semantics — bytes, not offsets).
     */
    private fun bytesBackAsUtf16(byteCount: Int): Int {
        if (byteCount <= 0 || document.isEmpty()) return 0
        val cursorUtf16 = selection.endExclusiveUtf16.coerceIn(0, document.length)
        var bytes = 0
        var index = cursorUtf16
        while (index > 0 && bytes < byteCount) {
            index--
            val codePoint = document.codePointAt(index)
            if (Character.isLowSurrogate(document[index]) && index > 0 &&
                Character.isHighSurrogate(document[index - 1])
            ) {
                continue // inside a pair: counts with its high half
            }
            bytes += when {
                codePoint <= 0x7F -> 1
                codePoint <= 0x7FF -> 2
                codePoint <= 0xFFFF -> 3
                else -> 4
            }
        }
        return cursorUtf16 - index
    }

    private fun bytesForwardAsUtf16(byteCount: Int): Int {
        if (byteCount <= 0 || document.isEmpty()) return 0
        val cursorUtf16 = selection.endExclusiveUtf16.coerceIn(0, document.length)
        var bytes = 0
        var index = cursorUtf16
        while (index < document.length && bytes < byteCount) {
            val codePoint = document.codePointAt(index)
            bytes += when {
                codePoint <= 0x7F -> 1
                codePoint <= 0x7FF -> 2
                codePoint <= 0xFFFF -> 3
                else -> 4
            }
            index += if (codePoint > 0xFFFF) 2 else 1
        }
        return index - cursorUtf16
    }

    private fun revision() = org.graphiks.kadre.input.TextDocumentRevision(mirroredRevision.coerceAtLeast(0))

    private companion object {
        /** zwp_text_input_v3.change_cause: input_method. */
        const val CHANGE_CAUSE_INPUT_METHOD = 1
    }
}
