package org.graphiks.kadre.platform.web

import kotlinx.coroutines.await
import org.graphiks.kadre.surface.LogicalPoint
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.dom.DataTransfer
import org.w3c.dom.DragEvent
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.files.Blob
import org.w3c.files.File
import kotlin.js.Promise
import kotlin.js.unsafeCast

/**
 * The DOM-reading half of the JS drop port: it reads the browser's own `DataTransfer` inside the drag
 * callback that borrowed it, builds the snapshot the common drop pipeline presents from it, and keeps
 * the `File` handles of the drag behind the payload closures that read them (plan decision D-D1).
 *
 * This is the JS twin of `WasmWebDropEvents.kt`, function for function and with the same readings.
 * Nothing here holds a policy: the classification and the canonicality are the pure rules of
 * `WebDropStimulus.kt`, and the accept/reject of the offer is the surface's. What is target-specific
 * here is only the reading — a dragged file crosses as a `File` whose bytes are a promised
 * `ArrayBuffer` per slice, and the one member the DOM declarations of this target do not carry
 * (`Blob.arrayBuffer`) is declared as the external interface the value is read through, the same kind
 * of gap `JsPointerLockRequester` declares for the pointer-lock seam.
 *
 * A borrowed `DataTransfer` is read within its own callback and never stored beyond the handles the
 * snapshot's payloads keep, and no returned value names a DOM type: the surface sees the Kotlin
 * source, and the source's `collectBytes` is the only read it can ask for.
 */

/**
 * The position of one drag observation, in the surface's own logical space.
 *
 * The same measurement the pointer observations make (`jsPointerPosition`): a `DragEvent` is a
 * mouse-positioned event, its client coordinate minus the element's own origin is where the drag is
 * on this surface.
 */
internal fun jsDropPosition(element: HTMLElement, event: DragEvent): LogicalPoint {
    val box = element.getBoundingClientRect()
    return LogicalPoint(
        event.clientX.toDouble() - box.left - element.clientLeft,
        event.clientY.toDouble() - box.top - element.clientTop,
    )
}

/**
 * The drop snapshot of one drag entry: the source the surface may present an offer from, and the
 * drop-time re-read the entries behind it perform.
 *
 * The re-read is the glue's own memory — the index-by-index entries of what the browser reported at
 * the entry, kind and type per item, the two members the drag data store exposes even while it
 * protects the payload — closed over here, where the DOM types of the entries stay. The drop event's
 * own `DataTransfer` is re-read against it in [attachDropData]: the store is the drag's, so the same
 * indices name the same items, and a mismatched one is skipped rather than guessed.
 */
internal class JsWebDropSnapshot(
    val source: WebDropTransferSource,
    private val attach: (DataTransfer) -> Unit,
) {
    /**
     * Attaches the payloads this drop's own data makes readable, item by item.
     *
     * A file becomes its handle — the `File` the store releases with the drop, kept inside the
     * payload's closure — and a string becomes the bytes of the value the store returns for the
     * format it named. Whatever the browser refuses to read here (a read that throws, an index that
     * is gone) is contained: that item's payload stays unattached, and a read of it is the closed
     * platform failure of the seam, never an exception. Nothing here reads a payload the entry did
     * not name.
     */
    fun attachDropData(dataTransfer: DataTransfer) {
        attach(dataTransfer)
    }
}

/**
 * Snapshots the drag's data store into the source one drag entry presents, or `null` when the store
 * read as nothing at all.
 *
 * Every read is contained — a store that throws or reports fewer facts than it holds contributes
 * exactly the items it could describe, with the descriptor each one's own facts support. A file whose
 * handle the engine already releases at the entry (a constructed store does; a dragged one may not)
 * carries its name and size in the snapshot and its payload from that handle; the others carry the
 * kind and format the store exposed, and their bytes wait for the drop ([JsWebDropSnapshot.attachDropData]).
 */
internal fun jsDropSnapshot(dataTransfer: DataTransfer): JsWebDropSnapshot? {
    val items = runCatching { dataTransfer.items }.getOrNull() ?: return null
    val count = runCatching { items.length }.getOrNull() ?: return null
    val entries = mutableListOf<WebDropItem>()
    val kinds = mutableListOf<String>()
    val types = mutableListOf<String>()
    for (index in 0 until count) {
        val item = runCatching { items[index] }.getOrNull() ?: continue
        val kind = runCatching { item.kind }.getOrNull() ?: continue
        val type = runCatching { item.type }.getOrNull() ?: continue
        val isFile = kind == WEB_DROP_FILE_KIND
        val file = if (isFile) runCatching { item.getAsFile() }.getOrNull() else null
        val name = file?.let { runCatching { it.name }.getOrNull() }
        val size = file?.let { runCatching { it.size.toLong() }.getOrNull() }
        val mimeType = file?.let { runCatching { it.type }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: type
        val webItem = WebDropItem(
            descriptor = webDropItemDescriptor(
                isFile = isFile,
                mimeType = mimeType,
                displayName = name,
                sizeBytes = size,
            ),
        )
        file?.let { webItem.attach(jsFilePayload(it)) }
        entries += webItem
        kinds += kind
        types += type
    }
    if (entries.isEmpty()) return null
    return JsWebDropSnapshot(WebDropTransferSource(entries)) { store ->
        attachJsDropData(entries, kinds, types, store)
    }
}

/**
 * The drop-time half of the JS snapshot: the store re-read against the entry snapshot, item by item.
 *
 * The indices are the drag's own, so the same index names the same item it named at the entry; a
 * mismatched kind, a read that throws or an index that is gone is contained per item, and that
 * item's payload simply stays what it was.
 */
private fun attachJsDropData(
    entries: List<WebDropItem>,
    kinds: List<String>,
    types: List<String>,
    dataTransfer: DataTransfer,
) {
    val items = runCatching { dataTransfer.items }.getOrNull() ?: return
    val count = runCatching { items.length }.getOrNull() ?: return
    entries.forEachIndexed { index, entry ->
        if (index >= count || index >= kinds.size) return@forEachIndexed
        val domItem = runCatching { items[index] }.getOrNull() ?: return@forEachIndexed
        if (runCatching { domItem.kind }.getOrNull() != kinds[index]) return@forEachIndexed
        when (kinds[index]) {
            WEB_DROP_FILE_KIND -> {
                val file = runCatching { domItem.getAsFile() }.getOrNull() ?: return@forEachIndexed
                entry.attach(jsFilePayload(file))
            }

            else -> {
                val value = runCatching { dataTransfer.getData(types[index]) }.getOrNull() ?: return@forEachIndexed
                entry.attach(WebDropPayload.ofBytes(value.encodeToByteArray()))
            }
        }
    }
}

/**
 * The payload of one dragged file: slices of the `File`, resolved lazily at read.
 *
 * The handle stays in this closure and nowhere else. A read asks for the slice the chunk needs, waits
 * for the promised buffer, and copies it into a fresh `ByteArray` the collector may keep — one chunk
 * at a time, so a file of any size is delivered through a bounded window. A slice the browser refuses
 * to give throws into the reader's own containment.
 */
private fun jsFilePayload(file: File): WebDropPayload {
    val total = runCatching { file.size.toLong() }.getOrNull()
    return WebDropPayload(totalBytes = total) { offset, count ->
        if (offset > Int.MAX_VALUE.toLong() - count) error("drop payload chunk out of range")
        val start = offset.toInt()
        val buffer = file.slice(start, start + count).jsArrayBuffer().await()
        val view = Int8Array(buffer, 0, buffer.byteLength)
        ByteArray(view.length) { index -> view[index] }
    }
}

/**
 * The one DOM member this seam needs that the declarations of this target do not carry: the
 * promised `ArrayBuffer` behind a `Blob` — the same kind of gap `JsPointerLockRequester` declares
 * for the pointer-lock seam, and the only declaration this file adds.
 */
private external interface JsBlobArrayBufferSource {
    fun arrayBuffer(): Promise<ArrayBuffer>
}

private fun Blob.jsArrayBuffer(): Promise<ArrayBuffer> = unsafeCast<JsBlobArrayBufferSource>().arrayBuffer()
