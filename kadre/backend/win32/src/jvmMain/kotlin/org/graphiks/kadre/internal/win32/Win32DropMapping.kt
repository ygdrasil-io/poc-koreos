package org.graphiks.kadre.internal.win32

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DropItemDescriptor
import org.graphiks.kadre.input.DropItemKind
import org.graphiks.kadre.input.DropItemReadMode
import org.graphiks.kadre.internal.runtime.DropItemSource
import org.graphiks.kadre.internal.runtime.DropTransferSource
import org.graphiks.kffi.win32.managed.Win32DropStream
import org.graphiks.kadre.surface.LogicalPoint
import java.nio.file.Files
import java.nio.file.Path

/**
 * Synchronous drop admission seam between the native target callbacks and the
 * window peer. `entered` snapshots the borrowed payload and must answer before
 * the native callback returns; the boolean result is the returned drop effect.
 */
internal class Win32DropCallbacks(
    val entered: (source: DropTransferSource, position: LogicalPoint) -> Boolean,
    val moved: (position: LogicalPoint) -> Unit,
    val exited: () -> Unit,
    val performed: (position: LogicalPoint) -> Unit,
)

/** Copied snapshot bounds for one drop offer, derived from the session budget policy. */
internal class Win32DropBudgets(
    val maxElements: Int,
    val maxCodeUnits: Int,
    val maxRetainedBytes: Int,
)

internal fun win32DropFailure(cause: Throwable): KadreFailure = when (cause) {
    is org.graphiks.kffi.win32.managed.Win32DropException ->
        KadreFailure.PlatformFailure(KadrePlatform.Win32, "drop", "hresult-${cause.hresult.toUInt().toString(16)}")
    else -> KadreFailure.PlatformFailure(KadrePlatform.Win32, "drop", "initialization-failed")
}

/** Runtime-owned drop payload built once per drag enter; close releases every retained native reference. */
internal class Win32DropTransferSource(
    override val items: List<DropItemSource>,
    private val onClosed: (() -> Unit)? = null,
) : DropTransferSource {
    private var closed = false
    override fun close() {
        if (closed) return
        closed = true
        onClosed?.invoke()
    }
}

internal fun win32DropFailure(detail: String): KadreFailure.PlatformFailure =
    KadreFailure.PlatformFailure(KadrePlatform.Win32, "drop", detail)

/** One dragged file: the path is copied at enter, the content is re-read per collection. */
internal class Win32FileDropItemSource(private val path: String) : DropItemSource {
    override val descriptor = DropItemDescriptor(
        displayName = path.substringAfterLast('\\').substringAfterLast('/'),
        sizeBytes = null,
        mimeTypes = listOf("application/octet-stream"),
        kind = DropItemKind.File,
    )
    override val readMode = DropItemReadMode.Replayable

    override suspend fun collectBytes(maxChunkBytes: Int, collector: suspend (ByteArray) -> Unit): KadreResult<Unit> {
        require(maxChunkBytes > 0)
        return try {
            Files.newInputStream(Path.of(path)).use { input ->
                val buffer = ByteArray(maxChunkBytes)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    collector(buffer.copyOf(read))
                }
            }
            KadreResult.Success(Unit)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Throwable) {
            // Paths and contents never reach diagnostics.
            KadreResult.Failure(win32DropFailure("file-read-failed"))
        }
    }
}

/** A small payload copied once at enter (URL, text): replayable from owned memory. */
internal class Win32BytesDropItemSource(
    override val descriptor: DropItemDescriptor,
    private val bytes: ByteArray,
) : DropItemSource {
    override val readMode = DropItemReadMode.Replayable

    override suspend fun collectBytes(maxChunkBytes: Int, collector: suspend (ByteArray) -> Unit): KadreResult<Unit> {
        require(maxChunkBytes > 0)
        Win32DropMapping.chunk(bytes, maxChunkBytes, collector)
        return KadreResult.Success(Unit)
    }
}

/**
 * A retained TYMED_ISTREAM medium: single-use chunks read on the UI owner
 * thread through [pump], because COM media never leave their apartment.
 */
internal class Win32StreamDropItemSource(
    override val descriptor: DropItemDescriptor,
    private val stream: Win32DropStream,
    private val pump: ((() -> Unit) -> Unit),
) : DropItemSource {
    override val readMode = DropItemReadMode.SingleUse

    override suspend fun collectBytes(maxChunkBytes: Int, collector: suspend (ByteArray) -> Unit): KadreResult<Unit> {
        require(maxChunkBytes > 0)
        return try {
            while (true) {
                val buffer = ByteArray(maxChunkBytes)
                val read = suspendCancellableCoroutine { continuation ->
                    pump {
                        try { continuation.resumeWith(Result.success(stream.read(buffer))) }
                        catch (failure: Throwable) { continuation.resumeWith(Result.failure(failure)) }
                    }
                }
                if (read <= 0) break
                collector(buffer.copyOf(read))
            }
            KadreResult.Success(Unit)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Throwable) {
            KadreResult.Failure(win32DropFailure("stream-read-failed"))
        }
    }
}

/** Shared conversion helpers for the format keys this backend declares. */
internal object Win32DropMapping {
    const val FILE_MIME = "application/octet-stream"
    const val TEXT_MIME = "text/plain"
    const val URI_MIME = "text/uri-list"

    fun textItem(bytes: ByteArray) = DropItemDescriptor(null, bytes.size.toLong(), listOf(TEXT_MIME), DropItemKind.Text)
    fun uriItem(bytes: ByteArray) = DropItemDescriptor(null, bytes.size.toLong(), listOf(URI_MIME), DropItemKind.Uri)
    fun streamItem() = DropItemDescriptor(null, null, listOf(FILE_MIME), DropItemKind.Binary)

    /** Chunked replay of an owned byte array, bounded by the runtime read limits. */
    suspend fun chunk(bytes: ByteArray, maxChunkBytes: Int, collector: suspend (ByteArray) -> Unit) {
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + maxChunkBytes, bytes.size)
            collector(bytes.copyOfRange(offset, end))
            offset = end
        }
    }
}
