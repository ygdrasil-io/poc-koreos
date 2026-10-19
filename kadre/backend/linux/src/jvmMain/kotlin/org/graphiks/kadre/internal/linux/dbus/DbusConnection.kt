package org.graphiks.kadre.internal.linux.dbus

import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** One byte-direction of the transport — the O2 seam. */
internal interface DbusTransport : AutoCloseable {
    /** Sends raw bytes; false when the peer is gone. */
    fun send(bytes: ByteArray): Boolean

    /**
     * Receives the next chunk within the budget; null on timeout or when
     * the peer is gone. Implementations must NEVER block past the budget.
     */
    fun receive(timeoutMillis: Long): ByteArray?
}

/**
 * A D-Bus connection over a [DbusTransport]: SASL EXTERNAL handshake with
 * fd negotiation, monotone serials, request→reply correlation by serial
 * and signal dispatch by sender/interface/member (design D2, D5).
 *
 * The connection is single-threaded per call site: [call] sends one
 * method call and pumps incoming traffic until its reply, an error, or
 * the [timeoutMillis] budget expires. Signal observers fire during
 * pumping.
 */
internal class DbusConnection(
    private val transport: DbusTransport,
    private val uidProvider: () -> Int,
) : AutoCloseable {

    private val serialCounter = AtomicInteger(1)
    private val lock = ReentrantLock()
    private var closed = false
    private var unixFdSupport = false
    private val signalObservers = ArrayList<(DbusMessage) -> Unit>()
    private var buffer = ByteArray(0)

    /** The last SCM_RIGHTS fds received alongside a message. */
    @Volatile
    var receivedUnixFds: List<Int> = emptyList()
        private set

    val negotiatesUnixFds: Boolean get() = unixFdSupport

    fun connect(timeoutMillis: Long = 5_000): Boolean {
        if (!performHandshake(timeoutMillis)) return false
        // the bus assigns our unique name after Hello; the portal does not
        // need it, but the peer does — send it as the first call
        return true
    }

    /** The SASL EXTERNAL exchange; \0-terminated per the transport spec. */
    internal fun performHandshake(timeoutMillis: Long): Boolean {
        // the EXTERNAL initial response hex-encodes the ASCII decimal uid
        val uidHex = uidProvider().toString().toByteArray(Charsets.US_ASCII).joinToString("") { "%02x".format(it) }
        if (!transport.send(byteArrayOf(0))) return false
        if (!sendLine("AUTH EXTERNAL $uidHex")) return false
        val auth = receiveLine(timeoutMillis) ?: return false
        if (!auth.startsWith("OK ")) return false
        if (sendLine("NEGOTIATE_UNIX_FD")) {
            val negotiate = receiveLine(timeoutMillis)
            if (negotiate == "AGREE_UNIX_FD") {
                unixFdSupport = true
            }
            // a refusal is not fatal — fd passing only matters for the
            // PipeWire remote
        }
        return sendLine("BEGIN")
    }

    private fun sendLine(line: String): Boolean =
        transport.send((line + "\r\n").toByteArray(Charsets.US_ASCII))

    private fun receiveLine(timeoutMillis: Long): String? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (true) {
            val newline = buffer.indexOf('\n'.code.toByte())
            if (newline >= 0) {
                var end = newline
                if (end > 0 && buffer[end - 1] == '\r'.code.toByte()) end--
                val line = String(buffer, 0, end, Charsets.US_ASCII)
                buffer = buffer.copyOfRange(newline + 1, buffer.size)
                return line
            }
            if (System.currentTimeMillis() > deadline) return null
            val chunk = transport.receive(deadline - System.currentTimeMillis()) ?: continue
            buffer += chunk
        }
    }

    class DbusCallResult(
        val reply: DbusMessage?,
        val error: DbusMessage?,
    )

    /**
     * Sends [message] (serial assigned here when zero) and pumps until its
     * reply arrives. Errors surface as TYPE_ERROR replies, never throws
     * across the boundary.
     */
    fun call(message: DbusMessage, timeoutMillis: Long = 5_000): DbusCallResult {
        lock.withLock {
            if (closed) return DbusCallResult(null, null)
            val serial = if (message.serial == 0) serialCounter.getAndIncrement() else message.serial
            val withSerial = DbusMessage(
                type = message.type,
                flags = message.flags,
                serial = serial,
                fields = message.fields,
                bodySignature = message.bodySignature,
                body = message.body,
            )
            val encodedCall = withSerial.encode()
            val sent = transport.send(encodedCall)
            if (!sent) return DbusCallResult(null, null)
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (true) {
                val incoming = nextMessage(deadline)
                    ?: return DbusCallResult(null, null)
                when {
                    incoming.type == DbusMessage.TYPE_METHOD_RETURN && incoming.replySerial() == serial ->
                        return DbusCallResult(incoming, null)
                    incoming.type == DbusMessage.TYPE_ERROR && incoming.replySerial() == serial ->
                        return DbusCallResult(null, incoming)
                    else -> dispatchSignal(incoming)
                }
            }
        }
    }

    /** Sends a message expecting no reply (signals, fire-and-forget). */
    fun send(message: DbusMessage): Boolean {
        lock.withLock {
            if (closed) return false
            val serial = if (message.serial == 0) serialCounter.getAndIncrement() else message.serial
            val withSerial = DbusMessage(
                type = message.type,
                flags = message.flags,
                serial = serial,
                fields = message.fields,
                bodySignature = message.bodySignature,
                body = message.body,
            )
            val encoded = withSerial.encode()
            return transport.send(encoded)
        }
    }

    /** Polls one message with the remaining budget; null on timeout/close. */
    fun pollMessage(timeoutMillis: Long): DbusMessage? {
        lock.withLock {
            if (closed) return null
            return nextMessage(System.currentTimeMillis() + timeoutMillis)
        }
    }

    fun installSignalObserver(observer: (DbusMessage) -> Unit): AutoCloseable {
        lock.withLock { signalObservers.add(observer) }
        return AutoCloseable { lock.withLock { signalObservers.remove(observer) } }
    }

    private fun dispatchSignal(message: DbusMessage) {
        val observers = lock.withLock { signalObservers.toList() }
        observers.forEach { runCatching { it(message) } }
    }

    private fun nextMessage(deadline: Long): DbusMessage? {
        while (true) {
            val parsed = tryParseMessage() ?: run {
                if (System.currentTimeMillis() > deadline) return null
                val chunk = transport.receive(deadline - System.currentTimeMillis()) ?: return null
                buffer += chunk
                null
            }
            if (parsed != null) return parsed
        }
    }

    private fun tryParseMessage(): DbusMessage? {
        if (buffer.size < 16) return null
        // endianness + fixed lengths are enough to size the frame
        val endianness = when (buffer[0].toInt().toChar()) {
            'l' -> ByteOrder.LITTLE_ENDIAN
            'B' -> ByteOrder.BIG_ENDIAN
            else -> return null
        }
        val bodyLength = readUint32(buffer, 4, endianness).toInt()
        val fieldsLength = readUint32(buffer, 12, endianness).toInt()
        val paddedFields = (fieldsLength + 7) and (7.inv())
        val total = 16 + paddedFields + bodyLength
        if (buffer.size < total) return null
        val frame = buffer.copyOf(total)
        buffer = buffer.copyOfRange(total, buffer.size)
        return runCatching { DbusMessage.decode(frame) }.getOrNull()
    }

    private fun readUint32(bytes: ByteArray, offset: Int, endianness: ByteOrder): Long =
        java.nio.ByteBuffer.wrap(bytes, offset, 4).order(endianness).int.toLong() and 0xFFFFFFFFL

    override fun close() {
        lock.withLock {
            if (closed) return
            closed = true
            runCatching(transport::close)
        }
    }
}
