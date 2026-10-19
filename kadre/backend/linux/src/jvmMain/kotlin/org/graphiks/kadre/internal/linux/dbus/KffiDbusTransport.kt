package org.graphiks.kadre.internal.linux.dbus

import java.lang.foreign.Arena
import java.lang.foreign.MemoryLayout.PathElement.groupElement
import java.lang.foreign.ValueLayout
import org.graphiks.kffi.posix.LinuxPosix
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

    override fun receive(timeoutMillis: Long): ByteArray? {
        val scratch = ByteArray(4096)
        val deadline = System.currentTimeMillis() + timeoutMillis.coerceAtLeast(0)
        Arena.ofConfined().use { arena ->
            while (true) {
                val buffer = arena.allocate(scratch.size.toLong())
                val read = runCatching { LinuxPosix.recv(fd, buffer, LinuxPosix.MSG_DONTWAIT) }.getOrDefault(-1)
                if (read > 0) {
                    for (index in 0 until read) {
                        scratch[index.toInt()] = buffer.get(ValueLayout.JAVA_BYTE, index)
                    }
                    return scratch.copyOf(read.toInt())
                }
                if (System.currentTimeMillis() >= deadline) return null
                Thread.sleep(2)
            }
        }
    }

    override fun close() {
        runCatching { LinuxPosix.close(fd) }
    }

    companion object {
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
