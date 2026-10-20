package org.graphiks.kadre.internal.linux.dbus

import java.lang.foreign.Arena
import java.lang.foreign.MemoryLayout.PathElement.groupElement
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import org.graphiks.kffi.posix.LinuxPosix
import org.graphiks.kffi.posix.generated.cmsghdr
import org.graphiks.kffi.posix.generated.iovec
import org.graphiks.kffi.posix.generated.msghdr
import org.graphiks.kffi.posix.generated.sockaddr_un

/**
 * The kffi-backed transport: one blocking AF_UNIX stream socket to the
 * session bus. Blocking reads are fine — the connection pumps from the
 * capture worker, never a host thread (design D5).
 */
internal class KffiDbusTransport(
    private val fd: Int,
) : DbusTransport {

    override fun send(bytes: ByteArray): Boolean {
        Arena.ofConfined().use { arena ->
            var offset = 0
            while (offset < bytes.size) {
                val chunk = arena.allocate((bytes.size - offset).toLong())
                for (index in chunk.byteSize() - 1 downTo 0) {
                    chunk.set(ValueLayout.JAVA_BYTE, index, bytes[offset + index.toInt()])
                }
                val written = runCatching { LinuxPosix.send(fd, chunk, LinuxPosix.MSG_NOSIGNAL) }.getOrDefault(-1)
                if (written <= 0) return false
                offset += written.toInt()
            }
            return true
        }
    }

    override fun receive(timeoutMillis: Long): DbusChunk? {
        val scratch = ByteArray(RECEIVE_BUFFER)
        val deadline = System.currentTimeMillis() + timeoutMillis.coerceAtLeast(0)
        Arena.ofConfined().use { arena ->
            while (true) {
                val buffer = arena.allocate(RECEIVE_BUFFER.toLong())
                val control = arena.allocate(CONTROL_BUFFER.toLong())
                val vector = iovec.allocate(arena)
                vector.set(ValueLayout.ADDRESS, iovec.layout.byteOffset(groupElement("iov_base")), buffer)
                vector.set(ValueLayout.JAVA_LONG, iovec.layout.byteOffset(groupElement("iov_len")), RECEIVE_BUFFER.toLong())
                val message = msghdr.allocate(arena)
                message.set(ValueLayout.ADDRESS, msghdr.layout.byteOffset(groupElement("msg_iov")), vector)
                message.set(ValueLayout.JAVA_LONG, msghdr.layout.byteOffset(groupElement("msg_iovlen")), 1L)
                message.set(ValueLayout.ADDRESS, msghdr.layout.byteOffset(groupElement("msg_control")), control)
                message.set(ValueLayout.JAVA_LONG, msghdr.layout.byteOffset(groupElement("msg_controllen")), CONTROL_BUFFER.toLong())
                val read = runCatching { LinuxPosix.recvmsg(fd, message, LinuxPosix.MSG_DONTWAIT) }.getOrDefault(-1)
                if (read > 0) {
                    for (index in 0 until read) {
                        scratch[index.toInt()] = buffer.get(ValueLayout.JAVA_BYTE, index)
                    }
                    val controlLength = message.get(ValueLayout.JAVA_LONG, msghdr.layout.byteOffset(groupElement("msg_controllen"))).toInt()
                    return DbusChunk(scratch.copyOf(read.toInt()), parseScmRights(control, controlLength))
                }
                if (System.currentTimeMillis() >= deadline) return null
                Thread.sleep(2)
            }
        }
    }

    /**
     * Sends raw message bytes carrying one SCM_RIGHTS descriptor — the
     * peer-side mirror of [receive], used by the oracle to answer
     * OpenPipeWireRemote the way a real portal does. The receiver gets a
     * kernel duplicate; our copy of [fd] stays owned by the caller.
     */
    internal fun sendWithUnixFd(bytes: ByteArray, fd: Int): Boolean {
        Arena.ofConfined().use { arena ->
            var offset = 0
            while (offset < bytes.size) {
                val chunkSize = bytes.size - offset
                val payload = arena.allocate(chunkSize.toLong())
                for (index in 0 until chunkSize) {
                    payload.set(ValueLayout.JAVA_BYTE, index.toLong(), bytes[offset + index])
                }
                val control = arena.allocate(CONTROL_BUFFER.toLong())
                val controlLength = (cmsghdr.layout.byteSize() + Int.SIZE_BYTES).toInt()
                control.set(ValueLayout.JAVA_LONG, cmsghdr.layout.byteOffset(groupElement("cmsg_len")), controlLength.toLong())
                control.set(ValueLayout.JAVA_INT, cmsghdr.layout.byteOffset(groupElement("cmsg_level")), LinuxPosix.SOL_SOCKET)
                control.set(ValueLayout.JAVA_INT, cmsghdr.layout.byteOffset(groupElement("cmsg_type")), LinuxPosix.SCM_RIGHTS)
                control.set(ValueLayout.JAVA_INT, cmsghdr.layout.byteSize(), fd)

                val vector = iovec.allocate(arena)
                vector.set(ValueLayout.ADDRESS, iovec.layout.byteOffset(groupElement("iov_base")), payload)
                vector.set(ValueLayout.JAVA_LONG, iovec.layout.byteOffset(groupElement("iov_len")), chunkSize.toLong())
                val message = msghdr.allocate(arena)
                message.set(ValueLayout.ADDRESS, msghdr.layout.byteOffset(groupElement("msg_iov")), vector)
                message.set(ValueLayout.JAVA_LONG, msghdr.layout.byteOffset(groupElement("msg_iovlen")), 1L)
                message.set(ValueLayout.ADDRESS, msghdr.layout.byteOffset(groupElement("msg_control")), control)
                message.set(ValueLayout.JAVA_LONG, msghdr.layout.byteOffset(groupElement("msg_controllen")), controlLength.toLong())

                val written = runCatching { LinuxPosix.sendmsg(this.fd, message, 0) }.getOrDefault(-1)
                if (written <= 0) return false
                offset += written.toInt()
            }
            return true
        }
    }

    /** Walks the cmsghdr chain and collects SCM_RIGHTS descriptors. */
    private fun parseScmRights(control: MemorySegment, controlLength: Int): IntArray {
        val headerSize = cmsghdr.layout.byteSize().toInt()
        val levelOffset = cmsghdr.layout.byteOffset(groupElement("cmsg_level"))
        val typeOffset = cmsghdr.layout.byteOffset(groupElement("cmsg_type"))
        val lengthOffset = cmsghdr.layout.byteOffset(groupElement("cmsg_len"))
        val fds = ArrayList<Int>()
        var offset = 0
        while (offset + headerSize <= controlLength) {
            val length = control.get(ValueLayout.JAVA_LONG, (offset + lengthOffset).toLong()).toInt()
            if (length < headerSize || offset + length > controlLength) break
            val level = control.get(ValueLayout.JAVA_INT, (offset + levelOffset).toLong())
            val type = control.get(ValueLayout.JAVA_INT, (offset + typeOffset).toLong())
            if (level == LinuxPosix.SOL_SOCKET && type == LinuxPosix.SCM_RIGHTS) {
                val dataStart = offset + headerSize
                val count = (length - headerSize) / Int.SIZE_BYTES
                for (index in 0 until count) {
                    fds += control.get(ValueLayout.JAVA_INT, (dataStart + index * Int.SIZE_BYTES).toLong())
                }
            }
            // CMSG_ALIGN: headers sit at sizeof(size_t) boundaries
            offset += (length + 7) and 7.inv()
        }
        return fds.toIntArray()
    }

    override fun close() {
        runCatching { LinuxPosix.close(fd) }
    }

    companion object {
        private const val RECEIVE_BUFFER = 4096
        private const val CONTROL_BUFFER = 256

        /**
         * Connects to the session bus. Address forms: `unix:path=…`
         * (the `$XDG_RUNTIME_DIR/bus` default) — abstract sockets are a
         * documented residual.
         */
        fun connectToSessionBus(address: String? = System.getenv("DBUS_SESSION_BUS_ADDRESS")): KffiDbusTransport? {
            val path = parsePath(address) ?: return null
            val fd = runCatching {
                LinuxPosix.socket(LinuxPosix.AF_UNIX, LinuxPosix.SOCK_STREAM)
            }.getOrDefault(-1)
            if (fd < 0) return null
            return Arena.ofConfined().use { arena ->
                val addressSegment = sockaddr_un.allocate(arena)
                addressSegment.set(
                    ValueLayout.JAVA_SHORT,
                    sockaddr_un.layout.byteOffset(groupElement("sun_family")),
                    LinuxPosix.AF_UNIX.toShort(),
                )
                val pathOffset = sockaddr_un.layout.byteOffset(groupElement("sun_path"))
                val bytes = path.toByteArray()
                for (index in bytes.indices) {
                    addressSegment.set(ValueLayout.JAVA_BYTE, pathOffset + index, bytes[index])
                }
                addressSegment.set(ValueLayout.JAVA_BYTE, pathOffset + bytes.size, 0)
                val length = (pathOffset + bytes.size + 1).toInt()
                // connect returns Unit: success is the absence of PosixException
                val connected = runCatching { LinuxPosix.connect(fd, addressSegment, length) }.isSuccess
                if (connected) KffiDbusTransport(fd) else run {
                    LinuxPosix.close(fd)
                    null
                }
            }
        }

        internal fun parsePath(address: String?): String? {
            if (address == null) {
                val runtimeDir = System.getenv("XDG_RUNTIME_DIR") ?: return null
                val default = "$runtimeDir/bus"
                return if (java.io.File(default).exists()) default else null
            }
            if (!address.startsWith("unix:")) return null
            return address.removePrefix("unix:")
                .split(",")
                .firstOrNull { it.startsWith("path=") }
                ?.removePrefix("path=")
        }
    }
}
