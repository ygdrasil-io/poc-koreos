package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CancellationException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DropItemDescriptor
import org.graphiks.kadre.input.DropItemKind
import org.graphiks.kadre.input.DropItemReadMode
import org.graphiks.kadre.internal.runtime.DropItemSource
import org.graphiks.kadre.internal.runtime.DropTransferSource
import kotlin.math.min

/**
 * The DOM-free half of the web drop snapshot: the classification of what the element was handed, the
 * source that carries the snapshot through the common drop pipeline, and the lazy byte reads behind
 * it (plan decisions D-D1).
 *
 * Nothing here names a DOM type. The per-target glue reads the browser's `DataTransfer` inside the
 * drag callback that borrowed it, builds the [WebDropItem] entries from the pure rules of this file,
 * and keeps the `File`/`Blob` handles of a drag inside the closures of the payloads it attaches —
 * the interface the runtime sees is Kotlin-only, and the bytes it can ask for are fresh copies
 * bounded by the chunk size the read names. What a payload can resolve depends on when the browser
 * lets it: the drag data store is protected outside `drop` on some engines, so a descriptor is
 * snapshotted at the entry from whatever is readable then, and the payload a read resolves may only
 * arrive with the drop itself. A read of a payload nobody could attach is the closed platform
 * failure of this seam, never an exception.
 */

/** The `kind` the DOM reports for an item whose payload is a file. */
internal const val WEB_DROP_FILE_KIND: String = "file"

/** The `kind` the DOM reports for an item whose payload is a string of the item's own type. */
internal const val WEB_DROP_STRING_KIND: String = "string"

/** The one plain-text format the model names `Text`, exactly as the browser must spell it. */
internal const val WEB_DROP_TEXT_TYPE: String = "text/plain"

/** The one URI-list format the model names `Uri`, exactly as the browser must spell it. */
internal const val WEB_DROP_URI_TYPE: String = "text/uri-list"

/**
 * The kind of one dropped item, from the DOM's own `kind` and `type` of it.
 *
 * The vocabulary is closed and the browser's own spelling is the whole rule: a file is a [DropItemKind.File]
 * whatever format it carries, the exact plain-text format is [DropItemKind.Text], the exact URI list
 * is [DropItemKind.Uri], and everything else — including a format that only looks like one of the
 * two but is not spelled that way — is [DropItemKind.Binary], named for what it is rather than
 * translated into something more familiar.
 */
internal fun webDropItemKind(isFile: Boolean, type: String): DropItemKind = when {
    isFile -> DropItemKind.File
    type == WEB_DROP_TEXT_TYPE -> DropItemKind.Text
    type == WEB_DROP_URI_TYPE -> DropItemKind.Uri
    else -> DropItemKind.Binary
}

/**
 * Whether [value] is a media type the foundation's own rule accepts as canonical.
 *
 * The rule is not restated here. The single owner of "canonical media type" is the
 * [DropItemDescriptor] constructor's requirement (`TextDropRaw.kt`), so this asks it: a probe
 * descriptor either carries the value or refuses it. A morsel of the rule copied here would drift
 * the day the rule moved, and a mime this probe refuses can never be fabricated into a descriptor
 * by the glue that consumes it.
 */
internal fun isCanonicalWebDropMediaType(value: String): Boolean = runCatching {
    DropItemDescriptor(displayName = null, sizeBytes = null, mimeTypes = listOf(value), kind = DropItemKind.Binary)
}.isSuccess

/**
 * The mime of one dropped item as a canonical media type, or `null` when the browser reported none
 * the model can carry. A non-canonical spelling is dropped, never normalised and never fabricated:
 * a descriptor built through [webDropItemDescriptor] provably carries only mimes the foundation's
 * own rule accepted.
 */
internal fun canonicalWebDropMimeTypeOrNull(value: String): String? =
    value.takeIf(::isCanonicalWebDropMediaType)

/**
 * The snapshot descriptor of one dropped item, from the facts the element reported about it.
 *
 * The kind comes from [webDropItemKind] and the mime list is the one canonical media type the item
 * named — an empty list when it named none the model can carry, which every member of
 * [DropItemDescriptor] accepts. The name and the size are the facts the callback could read, and
 * they stay `null` when the browser protected them, exactly as they are: the snapshot says what the
 * entry knew, never what it guessed.
 */
internal fun webDropItemDescriptor(
    isFile: Boolean,
    mimeType: String,
    displayName: String?,
    sizeBytes: Long?,
): DropItemDescriptor = DropItemDescriptor(
    displayName = displayName,
    sizeBytes = sizeBytes,
    mimeTypes = listOfNotNull(canonicalWebDropMimeTypeOrNull(mimeType)),
    kind = webDropItemKind(isFile, mimeType),
)

/**
 * One lazy byte read behind a dropped item, as the closure that resolves it.
 *
 * [totalBytes] is the size the read knows about, or `null` when it must discover the end from a
 * short read. [readChunk] delivers one fresh, self-owned chunk of at most [WebDropPayload] caller's
 * `count` bytes from [readChunk]'s own handle — `null` when there is nothing more — and may suspend:
 * the DOM answers a slice of a file through a promise. The handle itself never leaves the closure.
 */
internal class WebDropPayload(
    val totalBytes: Long?,
    internal val readChunk: suspend (offset: Long, count: Int) -> ByteArray?,
) {
    internal companion object {
        /** Builds a payload out of an already-owned byte array, delivered chunk by chunk. */
        internal fun ofBytes(bytes: ByteArray): WebDropPayload =
            WebDropPayload(bytes.size.toLong()) { offset, count ->
                when {
                    offset >= bytes.size.toLong() -> null
                    else -> bytes.copyOfRange(offset.toInt(), min(bytes.size.toLong(), offset + count).toInt())
                }
            }
    }
}

/**
 * One dropped item as the common pipeline sees it: the snapshot descriptor of the entry, the read
 * mode it honours, and the payload a read resolves — attached when the browser let the glue read it,
 * and absent when it did not.
 *
 * The payload slot is deliberately the only mutable thing here, and it is this class's own: the
 * glue fills it from the drag's own handles, the runtime never sees one, and a close empties it so
 * a handle of a drag that ended cannot be read by a transfer that outlived it. A read with no
 * payload is the closed platform failure of this seam — the honest "the browser never let this
 * port read that item", not an exception thrown across a boundary.
 */
internal class WebDropItem(
    override val descriptor: DropItemDescriptor,
    override val readMode: DropItemReadMode = DropItemReadMode.Replayable,
) : DropItemSource {
    internal var payload: WebDropPayload? = null

    /** Attaches the payload a read resolves; the last attach wins, exactly as the drop re-reads it. */
    internal fun attach(payload: WebDropPayload) {
        this.payload = payload
    }

    /** Forgets the payload, with the drag it belonged to. */
    internal fun detach() {
        payload = null
    }

    override suspend fun collectBytes(
        maxChunkBytes: Int,
        collector: suspend (ByteArray) -> Unit,
    ): KadreResult<Unit> {
        if (maxChunkBytes <= 0) return KadreResult.Failure(KadreFailure.InvalidRequest("maxChunkBytes"))
        val payload = payload
            ?: return KadreResult.Failure(KadreFailure.PlatformFailure(KadrePlatform.Web, "drop", "payload-unavailable"))
        val total = payload.totalBytes
        var offset = 0L
        return try {
            while (total == null || offset < total) {
                val count = if (total == null) maxChunkBytes else min(maxChunkBytes.toLong(), total - offset).toInt()
                val chunk = payload.readChunk(offset, count) ?: break
                if (chunk.isEmpty()) break
                collector(chunk)
                offset += chunk.size
                if (total == null && chunk.size < count) break
            }
            KadreResult.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            KadreResult.Failure(KadreFailure.PlatformFailure(KadrePlatform.Web, "drop", "byte-read"))
        }
    }
}

/**
 * The source one drag entry presents to the common drop pipeline: the items the element reported,
 * and nothing else.
 *
 * The runtime retains it for the offer it built — through an accept and a performed drop, or until
 * the offer ends — and closes it on every terminal path that never handed a transfer over. The
 * close detaches the payloads, so the handles of a drag that ended are dropped with it; the
 * descriptors stay, as snapshots do. Like every [DropTransferSource], a close arrives at most once
 * from the runtime, and this one answers a repeated one harmlessly anyway.
 */
internal class WebDropTransferSource(
    internal val entries: List<WebDropItem>,
) : DropTransferSource {
    override val items: List<DropItemSource> get() = entries

    override fun close() {
        entries.forEach { it.detach() }
    }
}
