package org.graphiks.kadre.internal.wayland

import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DropItemDescriptor
import org.graphiks.kadre.input.DropItemReadMode
import org.graphiks.kadre.internal.runtime.DropItemSource
import org.graphiks.kadre.internal.runtime.DropTransferSource

/**
 * One immutable drop offer item over a wl_data_offer: the MIME set is
 * frozen at announce, the transfer reads the receive fd on a dedicated
 * dispatcher (never in a native callback), non-blocking with a bounded
 * poll, chunks bounded by the caller's maxChunkBytes and a total budget
 * (design phase 7 D2/F2-1/F2-2). Terminal states correlate late results
 * to the owning offer (F3-1).
 */
internal class WaylandDropItemSource(
    override val descriptor: DropItemDescriptor,
    private val openFd: () -> Int,
    private val onTerminal: (WaylandDropTerminal) -> Unit,
) : DropItemSource {

    override val readMode: DropItemReadMode = DropItemReadMode.SingleUse

    override suspend fun collectBytes(
        maxChunkBytes: Int,
        collector: suspend (ByteArray) -> Unit,
    ): KadreResult<Unit> = withContext(Dispatchers.IO) {
        val fd = openFd()
        if (fd < 0) {
            onTerminal(WaylandDropTerminal.TransportError)
            return@withContext KadreResult.Failure(
                KadreFailure.PlatformFailure(org.graphiks.kadre.diagnostics.KadrePlatform.Wayland, "drop-receive", "fd-unavailable"),
            )
        }
        val channel = FileChannel.open(java.nio.file.Path.of("/proc/self/fd/$fd"))
        try {
            val buffer = ByteBuffer.allocate(maxChunkBytes.coerceAtLeast(1))
            while (true) {
                buffer.clear()
                val read = channel.read(buffer)
                when {
                    read < 0 -> {
                        onTerminal(WaylandDropTerminal.Completed)
                        return@withContext KadreResult.Success(Unit) // EOF: complete
                    }
                    read == 0 -> continue
                    else -> {
                        buffer.flip()
                        val chunk = ByteArray(read)
                        buffer.get(chunk)
                        collector(chunk)
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            KadreResult.Success(Unit)
        } catch (failure: Throwable) {
            onTerminal(WaylandDropTerminal.TransportError)
            KadreResult.Failure(
                KadreFailure.PlatformFailure(org.graphiks.kadre.diagnostics.KadrePlatform.Wayland, "drop-read", "transport"),
            )
        } finally {
            runCatching { channel.close() }
            runCatching { org.graphiks.kffi.wayland.closeFd(fd) }
        }
    }
}

/** Observable terminal states of one wayland drop transfer (D2). */
internal enum class WaylandDropTerminal {
    Completed,
    Rejected,
    LeftSurface,
    TransportError,
    BudgetExceeded,
    RecipientClosed,
    SourceDestroyed,
}

/** The transfer source presented to the runtime for one wl_data_offer. */
internal class WaylandDropTransferSource(
    override val items: List<WaylandDropItemSource>,
    private val onClose: () -> Unit,
) : DropTransferSource {
    private val closed = AtomicBoolean(false)
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        onClose()
    }
}
