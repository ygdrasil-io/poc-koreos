package org.graphiks.kadre.ffi.posix

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

/** Layout helpers for the POSIX `struct pollfd`. */
object PollFd {
    const val POLLIN: Short = 1
    const val SIZE_BYTES: Long = 8

    fun allocate(arena: Arena, count: Int): MemorySegment {
        require(count >= 0) { "count must be non-negative" }
        return arena.allocate(SIZE_BYTES * count, ValueLayout.JAVA_INT.byteAlignment())
    }

    fun set(segment: MemorySegment, index: Int, fd: Int, events: Short) {
        val offset = index * SIZE_BYTES
        segment.set(ValueLayout.JAVA_INT, offset, fd)
        segment.set(ValueLayout.JAVA_SHORT, offset + 4, events)
        segment.set(ValueLayout.JAVA_SHORT, offset + 6, 0)
    }

    fun revents(segment: MemorySegment, index: Int): Short =
        segment.get(ValueLayout.JAVA_SHORT, index * SIZE_BYTES + 6)

    fun isReadable(fd: Int, timeoutMillis: Int): Boolean =
        isReadable(fd, timeoutMillis, PosixSymbolLookup(PosixSymbols::find), NativePosixSyscalls)

    internal fun isReadable(
        fd: Int,
        timeoutMillis: Int,
        symbols: PosixSymbolLookup,
        syscalls: PosixSyscalls,
    ): Boolean {
        require(fd >= 0) { "fd must be non-negative" }
        require(timeoutMillis >= -1) { "timeoutMillis must be -1 or non-negative" }
        val poll = symbols.require("poll")
        while (true) {
            val result = syscalls.poll(poll, fd, timeoutMillis)
            if (result.succeeded) return result.value == true
            if (result.errno == EINTR) continue
            throw PosixException("poll", result.requireErrno())
        }
    }
}
