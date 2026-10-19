package org.graphiks.kadre.internal.win32

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.input.DropItemDescriptor
import org.graphiks.kadre.input.DropItemKind
import org.graphiks.kadre.input.DropItemReadMode
import org.graphiks.kadre.input.DropOfferId
import org.graphiks.kadre.internal.runtime.DropItemSource
import org.graphiks.kadre.internal.runtime.DropTransferSource
import org.graphiks.kadre.internal.runtime.SurfaceStimulus
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kffi.win32.managed.Win32DropData
import org.graphiks.kffi.win32.managed.Win32DropFormats
import org.graphiks.kffi.win32.managed.Win32DropStream

/**
 * Per-window drop admission. At most one offer is alive per surface; the
 * runtime-issued id from the synchronous accept routes every later move, exit
 * and perform observation, and callbacks of a terminated offer are ignored.
 */
internal class Win32DropRouter(
    private val surfaceId: org.graphiks.kadre.surface.SurfaceId,
    private val dispatchDrop: (DropTransferSource, LogicalPoint) -> DropOfferId?,
    private val emit: (SurfaceStimulus) -> Boolean,
    private val isAlive: () -> Boolean,
) : AutoCloseable {
    internal var activeOffer: DropOfferId? = null
        private set

    /** Admits the adapter-snapshotted source; a refused dispatch answers DROPEFFECT_NONE. */
    fun entered(source: DropTransferSource, position: LogicalPoint): Boolean {
        if (!isAlive()) {
            source.close()
            return false
        }
        val offer = dispatchDrop(source, position)
        if (offer == null) {
            source.close()
            return false
        }
        activeOffer = offer
        return true
    }

    fun moved(position: LogicalPoint) {
        val offer = activeOffer ?: return
        emit(SurfaceStimulus.DropMoved(surfaceId, offer, position))
    }

    fun exited() {
        val offer = activeOffer ?: return
        activeOffer = null
        emit(SurfaceStimulus.DropExited(surfaceId, offer))
    }

    fun performed(position: LogicalPoint) {
        val offer = activeOffer ?: return
        activeOffer = null
        emit(SurfaceStimulus.DropPerformed(surfaceId, offer, position))
    }

    override fun close() {
        // The runtime terminates a still-active offer OwnerClosed with its surface.
        activeOffer = null
    }
}

/**
 * Snapshots one borrowed [Win32DropData] into a portable, runtime-owned source.
 * Returns null when the offer is inadmissible (no supported payload, or an
 * over-budget essential item). Every retained stream reference is released on
 * the owner thread when the returned source closes; a failure releases them
 * before the exception leaves the native callback frame.
 */
internal fun win32SnapshotDropSource(
    data: Win32DropData,
    maxElements: Int,
    maxCodeUnits: Int,
    maxRetainedBytes: Int,
    urlFormat: Int,
    pump: ((() -> Unit) -> Unit),
): DropTransferSource? {
    val items = ArrayList<DropItemSource>()
    val streams = ArrayList<Win32DropStream>()
    try {
        val formats = data.formats(minOf(maxElements, 64))

        val paths = if (formats.any { it.clipboardFormat == Win32DropFormats.CF_HDROP && it.tymed and Win32DropFormats.TYMED_HGLOBAL != 0 }) {
            data.filePaths(minOf(maxElements, 1024), maxCodeUnits)
        } else emptyList()
        if (paths.size > maxElements) return null
        for (path in paths) {
            if (path.length > maxCodeUnits) return null
            items += Win32FileDropItemSource(path)
        }

        if (formats.any { it.clipboardFormat == urlFormat && it.tymed and Win32DropFormats.TYMED_HGLOBAL != 0 }) {
            val bytes = data.readHGlobal(urlFormat, maxRetainedBytes.toLong()).decodeUTF16ToBytes()
            items += Win32BytesDropItemSource(Win32DropMapping.uriItem(bytes), bytes)
        }
        if (formats.any { it.clipboardFormat == Win32DropFormats.CF_UNICODETEXT && it.tymed and Win32DropFormats.TYMED_HGLOBAL != 0 }) {
            val bytes = data.readHGlobal(Win32DropFormats.CF_UNICODETEXT, maxRetainedBytes.toLong()).decodeUTF16ToBytes()
            items += Win32BytesDropItemSource(Win32DropMapping.textItem(bytes), bytes)
        }

        // TYMED_ISTREAM media stay stream-backed: single-use chunked reads on the owner thread.
        for (format in formats) {
            if (format.tymed and Win32DropFormats.TYMED_ISTREAM == 0) continue
            if (items.size >= maxElements) return null
            val stream = data.retainStream(format.clipboardFormat)
            streams += stream
            items += Win32StreamDropItemSource(Win32DropMapping.streamItem(), stream, pump)
        }
        if (items.isEmpty()) return null
        return Win32DropTransferSource(items) {
            for (stream in streams) pump { runCatching { stream.close() } }
        }
    } catch (failure: Throwable) {
        for (stream in streams) pump { runCatching { stream.close() } }
        throw failure
    } finally {
        data.close()
    }
}

/** UTF-16LE payload to UTF-8 bytes without platform code-page guessing. */
private fun ByteArray.decodeUTF16ToBytes(): ByteArray {
    val builder = StringBuilder(size / 2)
    var index = 0
    while (index + 1 < size) {
        builder.append(((this[index].toInt() and 0xFF) or ((this[index + 1].toInt() and 0xFF) shl 8)).toChar())
        index += 2
    }
    return builder.toString().toByteArray(Charsets.UTF_8)
}
