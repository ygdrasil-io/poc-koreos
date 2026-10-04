@file:OptIn(ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.platform.web

import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise
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

/**
 * The DOM-reading half of the Wasm drop port: it reads the browser's own `DataTransfer` inside the
 * drag callback that borrowed it, builds the snapshot the common drop pipeline presents from it, and
 * keeps the `File` handles of the drag behind the payload closures that read them (plan decision
 * D-D1).
 *
 * This is the Wasm twin of `JsWebDropEvents.kt`, function for function and with the same readings:
 * nothing here holds a policy, and nothing here decides how a browser fact becomes a model value.
 * What is target-specific here is only the way the reading is expressed — a browser value crosses
 * into Kotlin/Wasm as `JsAny`, so the two calls the byte reading needs beyond the declared DOM
 * bindings (`Blob.arrayBuffer`, and the view of the buffer it answers) are declared as `@JsFun`
 * externals next to the values they read, the way `wasmApplyPointerCapture` is declared for the
 * capture seam.
 *
 * A borrowed `DataTransfer` is read within its own callback and never stored beyond the handles the
 * snapshot's payloads keep, and no returned value names a DOM type: the surface sees the Kotlin
 * source, and the source's `collectBytes` is the only read it can ask for.
 */

/**
 * The position of one drag observation, in the surface's own logical space.
 *
 * The same measurement the pointer observations make (`wasmPointerPosition`): a `DragEvent` is a
 * mouse-positioned event, its client coordinate minus the element's own origin is where the drag is
 * on this surface.
 */
internal fun wasmDropPosition(element: HTMLElement, event: DragEvent): LogicalPoint {
    val box = element.getBoundingClientRect()
    return LogicalPoint(
        event.clientX.toDouble() - box.left - element.clientLeft.toDouble(),
        event.clientY.toDouble() - box.top - element.clientTop.toDouble(),
    )
}

/**
 * The drop snapshot of one drag entry: the source the surface may present an offer from, and the
 * drop-time re-read the entries behind it perform.
 *
 * The Wasm twin of `JsWebDropSnapshot`, with the same index-by-index memory of what the store
 * reported at the entry and the same containment: a read the browser refuses is contained, and that
 * item's payload stays what it was, its read the closed platform failure of the seam.
 */
internal class WasmWebDropSnapshot(
    val source: WebDropTransferSource,
    private val attach: (DataTransfer) -> Unit,
) {
    /**
     * Attaches the payloads this drop's own data makes readable, item by item.
     *
     * A file becomes its handle — the `File` the store releases with the drop, kept inside the
     * payload's closure — and a string becomes the bytes of the value the store returns for the
     * format it named. Nothing here reads a payload the entry did not name.
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
 * exactly the items it could describe, with the descriptor each one's own facts support, and a file
 * whose handle the engine already releases at the entry carries its name and size in the snapshot
 * and its payload from that handle; the others wait for the drop ([WasmWebDropSnapshot.attachDropData]).
 */
internal fun wasmDropSnapshot(dataTransfer: DataTransfer): WasmWebDropSnapshot? {
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
        val size = file?.let { runCatching { it.size.toDouble().toLong() }.getOrNull() }
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
        file?.let { webItem.attach(wasmFilePayload(it)) }
        entries += webItem
        kinds += kind
        types += type
    }
    if (entries.isEmpty()) return null
    return WasmWebDropSnapshot(WebDropTransferSource(entries)) { store ->
        attachWasmDropData(entries, kinds, types, store)
    }
}

/**
 * The drop-time half of the Wasm snapshot: the store re-read against the entry snapshot, item by
 * item.
 *
 * The indices are the drag's own, so the same index names the same item it named at the entry; a
 * mismatched kind, a read that throws or an index that is gone is contained per item, and that
 * item's payload simply stays what it was.
 */
private fun attachWasmDropData(
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
                entry.attach(wasmFilePayload(file))
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
private fun wasmFilePayload(file: File): WebDropPayload {
    val total = runCatching { file.size.toDouble().toLong() }.getOrNull()
    return WebDropPayload(totalBytes = total) { offset, count ->
        if (offset > Int.MAX_VALUE.toLong() - count) error("drop payload chunk out of range")
        val start = offset.toInt()
        val buffer: ArrayBuffer = wasmDropSliceArrayBuffer(file, start, start + count).await()
        val view = wasmDropInt8View(buffer)
        ByteArray(view.length) { index -> view[index] }
    }
}

/** The promised buffer behind one slice of the blob, the member the bindings of this target omit. */
@JsFun("(blob, start, end) => blob.slice(start, end).arrayBuffer()")
internal external fun wasmDropSliceArrayBuffer(blob: Blob, start: Int, end: Int): Promise<JsAny?>

/** The byte view of one promised buffer, the way its contents are read byte by byte. */
@JsFun("(buffer) => new Int8Array(buffer)")
internal external fun wasmDropInt8View(buffer: JsAny): Int8Array
