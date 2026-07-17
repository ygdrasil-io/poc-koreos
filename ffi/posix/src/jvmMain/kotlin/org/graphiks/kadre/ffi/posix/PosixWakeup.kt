package org.graphiks.kadre.ffi.posix

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout.PathElement.groupElement
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

internal const val O_NONBLOCK: Int = 0x800
internal const val O_CLOEXEC: Int = 0x80000
internal const val FD_CLOEXEC: Int = 1
internal const val F_GETFD: Int = 1
internal const val F_SETFD: Int = 2
internal const val F_GETFL: Int = 3
internal const val F_SETFL: Int = 4
internal const val EINTR: Int = 4
internal const val EAGAIN: Int = 11
private const val ENOSYS: Int = 38

private const val EFD_FLAGS: Int = O_NONBLOCK or O_CLOEXEC
private const val PIPE_FLAGS: Int = O_NONBLOCK or O_CLOEXEC

/** A non-blocking, coalescing wake descriptor with idempotent ownership. */
interface PosixWakeup : AutoCloseable {
    val readFd: Int

    /** Returns false only when this owner has already been closed. */
    fun signal(): Boolean

    /** Returns false only when this owner has already been closed. */
    fun drain(): Boolean

    override fun close()

    companion object {
        fun open(): PosixWakeup =
            open(PosixSymbolLookup(PosixSymbols::find), NativePosixSyscalls)

        internal fun open(
            symbols: PosixSymbolLookup,
            syscalls: PosixSyscalls,
        ): PosixWakeup {
            symbols.find("eventfd")?.let { eventFd ->
                when (val fd = retryEintr("eventfd") { syscalls.eventFd(eventFd, 0, EFD_FLAGS) }) {
                    is CompletedCall.Success -> return OwnedPosixWakeup(
                        readFd = fd.value,
                        writeFd = fd.value,
                        eventFd = true,
                        symbols = symbols,
                        syscalls = syscalls,
                    )
                    is CompletedCall.Failure -> if (fd.errno != ENOSYS) {
                        throw PosixException("eventfd", fd.errno)
                    }
                }
            }

            symbols.find("pipe2")?.let { pipe2 ->
                when (val pair = retryEintr("pipe2") { syscalls.pipe2(pipe2, PIPE_FLAGS) }) {
                    is CompletedCall.Success -> return OwnedPosixWakeup(
                        readFd = pair.value.readFd,
                        writeFd = pair.value.writeFd,
                        eventFd = false,
                        symbols = symbols,
                        syscalls = syscalls,
                    )
                    is CompletedCall.Failure -> if (pair.errno != ENOSYS) {
                        throw PosixException("pipe2", pair.errno)
                    }
                }
            }

            val pipe = symbols.require("pipe")
            val pair = retryEintrOrThrow("pipe") { syscalls.pipe(pipe) }
            try {
                val fcntl = symbols.require("fcntl")
                configureDescriptor(pair.readFd, fcntl, syscalls)
                configureDescriptor(pair.writeFd, fcntl, syscalls)
            } catch (failure: Throwable) {
                closePairAfterOpenFailure(pair, symbols, syscalls, failure)
                throw failure
            }
            return OwnedPosixWakeup(
                readFd = pair.readFd,
                writeFd = pair.writeFd,
                eventFd = false,
                symbols = symbols,
                syscalls = syscalls,
            )
        }
    }
}

class PosixException(
    val operation: String,
    val errno: Int,
) : IllegalStateException("$operation failed (errno=$errno)")

private class OwnedPosixWakeup(
    override val readFd: Int,
    private val writeFd: Int,
    private val eventFd: Boolean,
    private val symbols: PosixSymbolLookup,
    private val syscalls: PosixSyscalls,
) : PosixWakeup {
    private val closed = AtomicBoolean(false)
    private val pending = AtomicBoolean(false)
    private val lock = Any()
    private val writeBytes = if (eventFd) {
        ByteBuffer.allocate(Long.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .putLong(1L)
            .array()
    } else {
        byteArrayOf(1)
    }

    override fun signal(): Boolean = synchronized(lock) {
        if (closed.get()) return false
        if (!pending.compareAndSet(false, true)) return true

        val write = symbols.require("write")
        while (true) {
            val result = syscalls.write(write, writeFd, writeBytes)
            when {
                result.succeeded && result.value == writeBytes.size.toLong() -> return true
                result.succeeded -> {
                    pending.set(false)
                    throw PosixException("write", 0)
                }
                result.errno == EINTR -> continue
                result.errno == EAGAIN -> return true
                else -> {
                    pending.set(false)
                    throw PosixException("write", result.requireErrno())
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        false
    }

    override fun drain(): Boolean = synchronized(lock) {
        if (closed.get()) return false

        val read = symbols.require("read")
        val byteCount = if (eventFd) Long.SIZE_BYTES else 1
        while (true) {
            val result = syscalls.read(read, readFd, byteCount)
            when {
                result.succeeded && (result.value ?: 0L) > 0L -> {
                    pending.set(false)
                    return true
                }
                result.succeeded -> throw PosixException("read", 0)
                result.errno == EINTR -> continue
                result.errno == EAGAIN -> {
                    pending.set(false)
                    return true
                }
                else -> throw PosixException("read", result.requireErrno())
            }
        }
        @Suppress("UNREACHABLE_CODE")
        false
    }

    override fun close() {
        synchronized(lock) {
            if (!closed.compareAndSet(false, true)) return
            pending.set(false)

            val close = symbols.find("close")
                ?: throw IllegalStateException("required POSIX symbol 'close' is unavailable")
            var failure: Throwable? = null
            for (fd in listOf(readFd, writeFd).distinct()) {
                val result = syscalls.close(close, fd)
                if (!result.succeeded) {
                    val next = PosixException("close", result.requireErrno())
                    if (failure == null) failure = next else failure.addSuppressed(next)
                }
            }
            failure?.let { throw it }
        }
    }
}

private fun configureDescriptor(
    fd: Int,
    fcntl: MemorySegment,
    syscalls: PosixSyscalls,
) {
    val status = retryEintrOrThrow("fcntl(F_GETFL)") {
        syscalls.fcntl(fcntl, fd, F_GETFL, 0)
    }
    retryEintrOrThrow("fcntl(F_SETFL)") {
        syscalls.fcntl(fcntl, fd, F_SETFL, status or O_NONBLOCK)
    }
    val descriptor = retryEintrOrThrow("fcntl(F_GETFD)") {
        syscalls.fcntl(fcntl, fd, F_GETFD, 0)
    }
    retryEintrOrThrow("fcntl(F_SETFD)") {
        syscalls.fcntl(fcntl, fd, F_SETFD, descriptor or FD_CLOEXEC)
    }
}

private fun closePairAfterOpenFailure(
    pair: FdPair,
    symbols: PosixSymbolLookup,
    syscalls: PosixSyscalls,
    failure: Throwable,
) {
    val close = symbols.find("close") ?: return
    for (fd in listOf(pair.readFd, pair.writeFd).distinct()) {
        val result = syscalls.close(close, fd)
        if (!result.succeeded) failure.addSuppressed(PosixException("close", result.requireErrno()))
    }
}

private sealed interface CompletedCall<out T> {
    data class Success<T>(val value: T) : CompletedCall<T>
    data class Failure(val operation: String, val errno: Int) : CompletedCall<Nothing>
}

private inline fun <T> retryEintr(
    operation: String,
    call: () -> PosixCall<T>,
): CompletedCall<T> {
    while (true) {
        val result = call()
        when (result) {
            is PosixCall.Success -> return CompletedCall.Success(result.result)
            is PosixCall.Failure -> {
                if (result.code == EINTR) continue
                return CompletedCall.Failure(operation, result.code)
            }
        }
    }
}

private inline fun <T> retryEintrOrThrow(
    operation: String,
    call: () -> PosixCall<T>,
): T = when (val result = retryEintr(operation, call)) {
    is CompletedCall.Success -> result.value
    is CompletedCall.Failure -> throw PosixException(operation, result.errno)
}

internal fun PosixSymbolLookup.require(name: String): MemorySegment =
    find(name) ?: throw IllegalStateException("required POSIX symbol '$name' is unavailable")

internal data class FdPair(val readFd: Int, val writeFd: Int)

internal sealed interface PosixCall<out T> {
    data class Success<T>(val result: T) : PosixCall<T>
    data class Failure(val code: Int) : PosixCall<Nothing>

    companion object {
        fun <T> success(value: T): PosixCall<T> = Success(value)
        fun failure(errno: Int): PosixCall<Nothing> = Failure(errno)
    }
}

internal val PosixCall<*>.succeeded: Boolean get() = this is PosixCall.Success
internal val <T> PosixCall<T>.value: T?
    get() = when (this) {
        is PosixCall.Success -> result
        is PosixCall.Failure -> null
    }
internal val PosixCall<*>.errno: Int?
    get() = (this as? PosixCall.Failure)?.code

internal fun PosixCall<*>.requireErrno(): Int =
    (this as? PosixCall.Failure)?.code
        ?: error("successful call has no errno")

internal interface PosixSyscalls {
    fun eventFd(symbol: MemorySegment, initialValue: Int, flags: Int): PosixCall<Int>
    fun pipe2(symbol: MemorySegment, flags: Int): PosixCall<FdPair>
    fun pipe(symbol: MemorySegment): PosixCall<FdPair>
    fun fcntl(symbol: MemorySegment, fd: Int, command: Int, argument: Int): PosixCall<Int>
    fun write(symbol: MemorySegment, fd: Int, bytes: ByteArray): PosixCall<Long>
    fun read(symbol: MemorySegment, fd: Int, byteCount: Int): PosixCall<Long>
    fun close(symbol: MemorySegment, fd: Int): PosixCall<Int>
    fun poll(symbol: MemorySegment, fd: Int, timeoutMillis: Int): PosixCall<Boolean>
}

internal object NativePosixSyscalls : PosixSyscalls {
    private val linker = Linker.nativeLinker()
    private val captureErrno = Linker.Option.captureCallState("errno")
    private val captureLayout = Linker.Option.captureStateLayout()
    private val errnoOffset = captureLayout.byteOffset(groupElement("errno"))

    override fun eventFd(
        symbol: MemorySegment,
        initialValue: Int,
        flags: Int,
    ): PosixCall<Int> = call(
        symbol,
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        listOf(initialValue, flags),
    )

    override fun pipe2(symbol: MemorySegment, flags: Int): PosixCall<FdPair> =
        Arena.ofConfined().use { arena ->
            val descriptors = arena.allocate(ValueLayout.JAVA_INT, 2)
            call<Int>(
                symbol,
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                ),
                listOf(descriptors, flags),
            ).mapSuccess {
                FdPair(
                    descriptors.getAtIndex(ValueLayout.JAVA_INT, 0),
                    descriptors.getAtIndex(ValueLayout.JAVA_INT, 1),
                )
            }
        }

    override fun pipe(symbol: MemorySegment): PosixCall<FdPair> =
        Arena.ofConfined().use { arena ->
            val descriptors = arena.allocate(ValueLayout.JAVA_INT, 2)
            call<Int>(
                symbol,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
                listOf(descriptors),
            ).mapSuccess {
                FdPair(
                    descriptors.getAtIndex(ValueLayout.JAVA_INT, 0),
                    descriptors.getAtIndex(ValueLayout.JAVA_INT, 1),
                )
            }
        }

    override fun fcntl(
        symbol: MemorySegment,
        fd: Int,
        command: Int,
        argument: Int,
    ): PosixCall<Int> = call(
        symbol,
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        ),
        listOf(fd, command, argument),
        Linker.Option.firstVariadicArg(2),
    )

    override fun write(symbol: MemorySegment, fd: Int, bytes: ByteArray): PosixCall<Long> =
        Arena.ofConfined().use { arena ->
            val buffer = arena.allocate(bytes.size.toLong())
            MemorySegment.copy(bytes, 0, buffer, ValueLayout.JAVA_BYTE, 0, bytes.size)
            call(
                symbol,
                FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG,
                ),
                listOf(fd, buffer, bytes.size.toLong()),
            )
        }

    override fun read(symbol: MemorySegment, fd: Int, byteCount: Int): PosixCall<Long> =
        Arena.ofConfined().use { arena ->
            val buffer = arena.allocate(byteCount.toLong())
            call(
                symbol,
                FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG,
                ),
                listOf(fd, buffer, byteCount.toLong()),
            )
        }

    override fun close(symbol: MemorySegment, fd: Int): PosixCall<Int> = call(
        symbol,
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        listOf(fd),
    )

    override fun poll(
        symbol: MemorySegment,
        fd: Int,
        timeoutMillis: Int,
    ): PosixCall<Boolean> = Arena.ofConfined().use { arena ->
        val descriptor = PollFd.allocate(arena, 1)
        PollFd.set(descriptor, 0, fd, PollFd.POLLIN)
        call<Int>(
            symbol,
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_INT,
            ),
            listOf(descriptor, 1L, timeoutMillis),
        ).mapSuccess { count ->
            count > 0 && (PollFd.revents(descriptor, 0).toInt() and PollFd.POLLIN.toInt()) != 0
        }
    }

    private fun <T> call(
        symbol: MemorySegment,
        descriptor: FunctionDescriptor,
        arguments: List<Any>,
        vararg options: Linker.Option,
    ): PosixCall<T> = Arena.ofConfined().use { arena ->
        val state = arena.allocate(captureLayout)
        val handle = linker.downcallHandle(symbol, descriptor, captureErrno, *options)
        @Suppress("UNCHECKED_CAST")
        val value = handle.invokeWithArguments(listOf(state) + arguments) as T
        when {
            value is Int && value < 0 -> PosixCall.failure(state.get(ValueLayout.JAVA_INT, errnoOffset))
            value is Long && value < 0L -> PosixCall.failure(state.get(ValueLayout.JAVA_INT, errnoOffset))
            else -> PosixCall.success(value)
        }
    }
}

private inline fun <T, R> PosixCall<T>.mapSuccess(transform: (T) -> R): PosixCall<R> =
    when (this) {
        is PosixCall.Success -> PosixCall.success(transform(result))
        is PosixCall.Failure -> PosixCall.failure(code)
    }
