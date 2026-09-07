package org.graphiks.kadre.internal.appkit

import kotlinx.coroutines.CancellationException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DropItemDescriptor
import org.graphiks.kadre.input.DropItemKind
import org.graphiks.kadre.input.DropItemReadMode
import org.graphiks.kadre.internal.runtime.DropItemSource
import org.graphiks.kadre.internal.runtime.DropTransferSource
import org.graphiks.kffi.objc.NSArray
import org.graphiks.kffi.objc.NSData
import org.graphiks.kffi.objc.NSDraggingInfo
import org.graphiks.kffi.objc.NSPasteboard
import org.graphiks.kffi.objc.NSPasteboardItem
import org.graphiks.kffi.objc.NSURL
import org.graphiks.kffi.objc.ObjCRuntime
import org.graphiks.kffi.objc.appkit.SecurityScopedUrlAccess
import org.graphiks.kffi.objc.managed.retainStrong
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * Snapshots a callback-borrowed AppKit pasteboard into only retained Objective-C values or owned
 * Kotlin data. Native pasteboard items, URLs and data objects never cross the backend SPI.
 */
internal fun NSDraggingInfo.toKffiDropTransferSourceOrNull(): DropTransferSource? {
    val pasteboardPointer = draggingPasteboard()
    if (pasteboardPointer == MemorySegment.NULL) return null
    val itemPointers = NSPasteboard(pasteboardPointer).pasteboardItems()
    if (itemPointers == MemorySegment.NULL) return null

    val retained = mutableListOf<AutoCloseable>()
    return try {
        val array = NSArray(itemPointers)
        val itemSources = buildList {
            var index = 0L
            val count = array.count()
            while (index < count) {
                val itemPointer = array.objectAtIndex(index++)
                if (itemPointer != MemorySegment.NULL) {
                    NSPasteboardItem(itemPointer).toKffiDropItemSourceOrNull(retained)?.let(::add)
                }
            }
        }
        if (itemSources.isEmpty()) {
            retained.closeAll()
            null
        } else {
            KffiDropTransferSource(itemSources, retained)
        }
    } catch (failure: Throwable) {
        retained.closeAll(failure)
        throw failure
    }
}

private fun NSPasteboardItem.toKffiDropItemSourceOrNull(
    retained: MutableList<AutoCloseable>,
): DropItemSource? {
    val types = availableTypes()
    fileUrlItemSource(types, retained)?.let { return it }
    uriItemSource(types)?.let { return it }
    textItemSource(types)?.let { return it }
    return binaryItemSource(types, retained)
}

private fun NSPasteboardItem.fileUrlItemSource(
    types: List<String>,
    retained: MutableList<AutoCloseable>,
): DropItemSource? {
    if (APPKIT_FILE_URL_TYPE !in types) return null
    val urlValue = stringForTypeOrNull(APPKIT_FILE_URL_TYPE) ?: return null
    val owner = ObjCRuntime.autoreleasePool {
        val pointer = NSURL.URLWithString(urlValue)
        if (pointer == MemorySegment.NULL) {
            null
        } else {
            NSURL(pointer).retainStrong()
        }
    } ?: return null
    val url = owner.value
    val isFileUrl = try {
        url.isFileURL()
    } catch (failure: Throwable) {
        try {
            owner.close()
        } catch (closeFailure: Throwable) {
            if (closeFailure !== failure) failure.addSuppressed(closeFailure)
        }
        throw failure
    }
    if (!isFileUrl) {
        owner.close()
        return null
    }
    retained += owner
    return KffiFileDropItemSource(url)
}

private fun NSPasteboardItem.uriItemSource(types: List<String>): DropItemSource? {
    if (APPKIT_URL_TYPE !in types) return null
    val value = stringForTypeOrNull(APPKIT_URL_TYPE) ?: return null
    val bytes = value.encodeToByteArray()
    return KffiByteArrayDropItemSource(
        descriptor = DropItemDescriptor(
            displayName = null,
            sizeBytes = bytes.size.toLong(),
            mimeTypes = listOf("text/uri-list"),
            kind = DropItemKind.Uri,
        ),
        bytes = bytes,
    )
}

private fun NSPasteboardItem.textItemSource(types: List<String>): DropItemSource? {
    val type = types.firstOrNull(::isAppKitTextType) ?: return null
    val value = stringForTypeOrNull(type) ?: return null
    val bytes = value.encodeToByteArray()
    return KffiByteArrayDropItemSource(
        descriptor = DropItemDescriptor(
            displayName = null,
            sizeBytes = bytes.size.toLong(),
            mimeTypes = listOf("text/plain"),
            kind = DropItemKind.Text,
        ),
        bytes = bytes,
    )
}

private fun NSPasteboardItem.binaryItemSource(
    types: List<String>,
    retained: MutableList<AutoCloseable>,
): DropItemSource? {
    val type = types.firstOrNull() ?: return null
    val owner = retainedDataForTypeOrNull(type) ?: return null
    val byteCount = try {
        owner.value.length()
    } catch (failure: Throwable) {
        try {
            owner.close()
        } catch (closeFailure: Throwable) {
            if (closeFailure !== failure) failure.addSuppressed(closeFailure)
        }
        throw failure
    }
    if (byteCount < 0L) {
        owner.close()
        return null
    }
    retained += owner
    return KffiDataDropItemSource(owner.value, byteCount)
}

private fun NSPasteboardItem.availableTypes(): List<String> {
    val pointer = types()
    if (pointer == MemorySegment.NULL) return emptyList()
    val array = NSArray(pointer)
    return buildList {
        var index = 0L
        val count = array.count()
        while (index < count) {
            val value = array.objectAtIndex(index++)
            if (value != MemorySegment.NULL) add(ObjCRuntime.toJavaString(value))
        }
    }
}

private fun NSPasteboardItem.stringForTypeOrNull(type: String): String? = ObjCRuntime.autoreleasePool {
    Arena.ofConfined().use { arena ->
        val value = stringForType(ObjCRuntime.newNSString(arena, type))
        if (value == MemorySegment.NULL) null else ObjCRuntime.toJavaString(value)
    }
}

private fun NSPasteboardItem.retainedDataForTypeOrNull(type: String) = ObjCRuntime.autoreleasePool {
    Arena.ofConfined().use { arena ->
        val value = dataForType(ObjCRuntime.newNSString(arena, type))
        if (value == MemorySegment.NULL) null else NSData(value).retainStrong()
    }
}

private fun isAppKitTextType(type: String): Boolean =
    type == APPKIT_UTF8_TEXT_TYPE || type == APPKIT_TEXT_TYPE || type.startsWith("public.text")

private class KffiDropTransferSource(
    override val items: List<DropItemSource>,
    private val retained: List<AutoCloseable>,
) : DropTransferSource {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) retained.asReversed().closeAll()
    }
}

private class KffiByteArrayDropItemSource(
    override val descriptor: DropItemDescriptor,
    private val bytes: ByteArray,
) : DropItemSource {
    override val readMode: DropItemReadMode = DropItemReadMode.Replayable

    override suspend fun collectBytes(
        maxChunkBytes: Int,
        collector: suspend (ByteArray) -> Unit,
    ): KadreResult<Unit> = collectChunks(bytes.size.toLong(), maxChunkBytes, collector) { offset, count ->
        bytes.copyOfRange(offset.toInt(), (offset + count).toInt())
    }
}

private class KffiDataDropItemSource(
    private val data: NSData,
    private val byteCount: Long,
) : DropItemSource {
    override val descriptor: DropItemDescriptor = DropItemDescriptor(
        displayName = null,
        sizeBytes = byteCount,
        mimeTypes = listOf("application/octet-stream"),
        kind = DropItemKind.Binary,
    )
    override val readMode: DropItemReadMode = DropItemReadMode.Replayable

    override suspend fun collectBytes(
        maxChunkBytes: Int,
        collector: suspend (ByteArray) -> Unit,
    ): KadreResult<Unit> {
        if (byteCount == 0L) return KadreResult.Success(Unit)
        val source = try {
            data.bytes()
        } catch (failure: Throwable) {
            return platformDropFailure("data-bytes", failure)
        }
        if (source == MemorySegment.NULL) return platformDropFailure("nil-data-bytes")
        return collectChunks(byteCount, maxChunkBytes, collector) { offset, count ->
            ByteArray(count.toInt()).also { chunk ->
                MemorySegment.copy(
                    source.reinterpret(byteCount),
                    ValueLayout.JAVA_BYTE,
                    offset,
                    chunk,
                    0,
                    chunk.size,
                )
            }
        }
    }
}

private class KffiFileDropItemSource(
    private val url: NSURL,
) : DropItemSource {
    override val descriptor: DropItemDescriptor = DropItemDescriptor(
        displayName = null,
        sizeBytes = null,
        mimeTypes = listOf("application/octet-stream"),
        kind = DropItemKind.File,
    )
    override val readMode: DropItemReadMode = DropItemReadMode.Replayable

    override suspend fun collectBytes(
        maxChunkBytes: Int,
        collector: suspend (ByteArray) -> Unit,
    ): KadreResult<Unit> {
        if (!url.isFileURL()) return platformDropFailure("not-file-url")
        val access = try {
            SecurityScopedUrlAccess.acquire(url)
        } catch (failure: Throwable) {
            return platformDropFailure("security-scoped-access", failure)
        } ?: return platformDropFailure("security-scoped-access-denied")

        return try {
            Files.newInputStream(Path.of(url.pathAsString())).use { stream ->
                val buffer = ByteArray(requireDropChunkSize(maxChunkBytes))
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (count > 0) collector(buffer.copyOf(count))
                }
            }
            KadreResult.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            platformDropFailure("file-read", failure)
        } finally {
            access.close()
        }
    }
}

private suspend fun collectChunks(
    byteCount: Long,
    maxChunkBytes: Int,
    collector: suspend (ByteArray) -> Unit,
    copyChunk: (offset: Long, count: Long) -> ByteArray,
): KadreResult<Unit> = try {
    val chunkSize = requireDropChunkSize(maxChunkBytes)
    var offset = 0L
    while (offset < byteCount) {
        val count = min(chunkSize.toLong(), byteCount - offset)
        collector(copyChunk(offset, count))
        offset += count
    }
    KadreResult.Success(Unit)
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    platformDropFailure("byte-read", failure)
}

private fun requireDropChunkSize(maxChunkBytes: Int): Int {
    require(maxChunkBytes > 0) { "maxChunkBytes must be positive" }
    return maxChunkBytes
}

private fun platformDropFailure(code: String, @Suppress("UNUSED_PARAMETER") cause: Throwable? = null): KadreResult.Failure =
    KadreResult.Failure(KadreFailure.PlatformFailure(KadrePlatform.AppKit, "drop", code))

private fun Iterable<AutoCloseable>.closeAll(primary: Throwable? = null) {
    var failure = primary
    forEach { closeable ->
        try {
            closeable.close()
        } catch (closeFailure: Throwable) {
            if (failure != null && failure !== closeFailure) failure.addSuppressed(closeFailure)
            else failure = closeFailure
        }
    }
    if (primary == null) failure?.let { throw it }
}

private const val APPKIT_FILE_URL_TYPE = "public.file-url"
private const val APPKIT_URL_TYPE = "public.url"
private const val APPKIT_UTF8_TEXT_TYPE = "public.utf8-plain-text"
private const val APPKIT_TEXT_TYPE = "public.text"
