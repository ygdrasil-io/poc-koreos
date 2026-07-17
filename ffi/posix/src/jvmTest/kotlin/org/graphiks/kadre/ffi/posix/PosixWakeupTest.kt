package org.graphiks.kadre.ffi.posix

import java.lang.foreign.MemorySegment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PosixWakeupTest {
    @Test
    fun threeSignalPollDrainCyclesRearmOnLinux() {
        if (System.getProperty("os.name") != "Linux") return

        PosixWakeup.open().use { wakeup ->
            repeat(3) {
                assertTrue(wakeup.signal())
                assertTrue(PollFd.isReadable(wakeup.readFd, timeoutMillis = 1_000))
                assertTrue(wakeup.drain())
            }
        }
    }

    @Test
    fun concurrentSignalsCoalesceAndRemainUsableAfterDrainOnLinux() {
        if (System.getProperty("os.name") != "Linux") return

        PosixWakeup.open().use { wakeup ->
            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(10)
            val futures = List(10) {
                pool.submit<Boolean> {
                    start.await()
                    wakeup.signal()
                }
            }
            start.countDown()
            assertTrue(futures.all { it.get(2, TimeUnit.SECONDS) })
            pool.shutdown()
            assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS))

            assertTrue(PollFd.isReadable(wakeup.readFd, timeoutMillis = 1_000))
            assertTrue(wakeup.drain())
            assertTrue(wakeup.signal())
            assertTrue(PollFd.isReadable(wakeup.readFd, timeoutMillis = 1_000))
            assertTrue(wakeup.drain())
        }
    }

    @Test
    fun closeIsIdempotentAndSignalAfterCloseIsTypedFailure() {
        val syscalls = FakePosixSyscalls()
        val wakeup = PosixWakeup.open(allSymbols, syscalls)

        wakeup.close()
        wakeup.close()

        assertFalse(wakeup.signal())
        assertEquals(listOf(syscalls.eventReadFd), syscalls.closedFds)
    }

    @Test
    fun missingEventFdSelectsPipe2Fallback() {
        val syscalls = FakePosixSyscalls()
        val symbols = PosixSymbolLookup { name ->
            if (name == "eventfd") null else symbol(name)
        }

        PosixWakeup.open(symbols, syscalls).use { wakeup ->
            assertEquals(syscalls.pipeReadFd, wakeup.readFd)
            assertEquals(O_NONBLOCK or O_CLOEXEC, syscalls.pipe2Flags)
            assertTrue(wakeup.signal())
            assertTrue(wakeup.drain())
        }

        assertEquals(listOf(syscalls.pipeReadFd, syscalls.pipeWriteFd), syscalls.closedFds)
    }

    @Test
    fun plainPipeFallbackMarksBothDescriptorsNonBlockingAndCloseOnExec() {
        val syscalls = FakePosixSyscalls()
        val symbols = PosixSymbolLookup { name ->
            if (name == "eventfd" || name == "pipe2") null else symbol(name)
        }

        PosixWakeup.open(symbols, syscalls).use { wakeup ->
            assertEquals(syscalls.pipeReadFd, wakeup.readFd)
            for (fd in listOf(syscalls.pipeReadFd, syscalls.pipeWriteFd)) {
                assertEquals(O_NONBLOCK, syscalls.statusFlags.getValue(fd) and O_NONBLOCK)
                assertEquals(FD_CLOEXEC, syscalls.descriptorFlags.getValue(fd) and FD_CLOEXEC)
            }
            assertTrue(wakeup.signal())
            assertTrue(wakeup.drain())
        }
    }

    private companion object {
        fun symbol(name: String): MemorySegment =
            MemorySegment.ofAddress(name.hashCode().toLong().let { if (it == 0L) 1L else it })

        val allSymbols = PosixSymbolLookup(::symbol)
    }
}

private class FakePosixSyscalls : PosixSyscalls {
    val eventReadFd = 30
    val pipeReadFd = 40
    val pipeWriteFd = 41
    var pipe2Flags: Int? = null
    val statusFlags = mutableMapOf(pipeReadFd to 0, pipeWriteFd to 0)
    val descriptorFlags = mutableMapOf(pipeReadFd to 0, pipeWriteFd to 0)
    val closedFds = mutableListOf<Int>()
    private val readable = mutableSetOf<Int>()

    override fun eventFd(symbol: MemorySegment, initialValue: Int, flags: Int): PosixCall<Int> =
        PosixCall.success(eventReadFd)

    override fun pipe2(symbol: MemorySegment, flags: Int): PosixCall<FdPair> {
        pipe2Flags = flags
        return PosixCall.success(FdPair(pipeReadFd, pipeWriteFd))
    }

    override fun pipe(symbol: MemorySegment): PosixCall<FdPair> =
        PosixCall.success(FdPair(pipeReadFd, pipeWriteFd))

    override fun fcntl(
        symbol: MemorySegment,
        fd: Int,
        command: Int,
        argument: Int,
    ): PosixCall<Int> = when (command) {
        F_GETFL -> PosixCall.success(statusFlags.getValue(fd))
        F_SETFL -> {
            statusFlags[fd] = argument
            PosixCall.success(0)
        }
        F_GETFD -> PosixCall.success(descriptorFlags.getValue(fd))
        F_SETFD -> {
            descriptorFlags[fd] = argument
            PosixCall.success(0)
        }
        else -> error("unexpected fcntl command $command")
    }

    override fun write(symbol: MemorySegment, fd: Int, bytes: ByteArray): PosixCall<Long> {
        readable += fd
        return PosixCall.success(bytes.size.toLong())
    }

    override fun read(symbol: MemorySegment, fd: Int, byteCount: Int): PosixCall<Long> {
        val sourceFd = if (fd == eventReadFd) eventReadFd else pipeWriteFd
        return if (readable.remove(sourceFd)) PosixCall.success(byteCount.toLong()) else PosixCall.failure(EAGAIN)
    }

    override fun close(symbol: MemorySegment, fd: Int): PosixCall<Int> {
        closedFds += fd
        return PosixCall.success(0)
    }

    override fun poll(symbol: MemorySegment, fd: Int, timeoutMillis: Int): PosixCall<Boolean> =
        PosixCall.success(fd in readable)
}
