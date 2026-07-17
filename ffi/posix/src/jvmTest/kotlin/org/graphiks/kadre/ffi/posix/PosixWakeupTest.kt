package org.graphiks.kadre.ffi.posix

import java.lang.foreign.MemorySegment
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

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
    fun tenConcurrentSignalsPerformOneWriteAndRearmAfterDrain() {
        val syscalls = FakePosixSyscalls()
        val wakeup = PosixWakeup.open(allSymbols, syscalls)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(10)
        try {
            val futures = List(10) {
                executor.submit<Boolean> {
                    start.await()
                    wakeup.signal()
                }
            }

            start.countDown()
            assertTrue(futures.all { it.get(2, TimeUnit.SECONDS) })
            assertEquals(1, syscalls.writeCalls.get(), "coalesced signals must issue one native write")

            assertTrue(wakeup.drain())
            assertTrue(wakeup.signal())
            assertEquals(2, syscalls.writeCalls.get(), "a post-drain signal must rearm the descriptor")
            assertTrue(wakeup.drain())
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
            wakeup.close()
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
    fun missingRequiredIoOrCloseSymbolCreatesNoDescriptor() {
        for (missing in listOf("read", "write", "close")) {
            val syscalls = FakePosixSyscalls()
            val symbols = PosixSymbolLookup { name -> if (name == missing) null else symbol(name) }

            val failure = assertFailsWith<IllegalStateException> {
                PosixWakeup.open(symbols, syscalls)
            }

            assertTrue(failure.message.orEmpty().contains(missing))
            assertEquals(0, syscalls.eventFdCalls.get(), "$missing must be resolved before eventfd")
            assertEquals(0, syscalls.pipe2Calls.get(), "$missing must be resolved before pipe2")
            assertEquals(0, syscalls.pipeCalls.get(), "$missing must be resolved before pipe")
            assertTrue(syscalls.closedFds.isEmpty())
        }
    }

    @Test
    fun missingFcntlCreatesNoPlainPipe() {
        val syscalls = FakePosixSyscalls()
        val symbols = PosixSymbolLookup { name ->
            if (name == "eventfd" || name == "pipe2" || name == "fcntl") null else symbol(name)
        }

        val failure = assertFailsWith<IllegalStateException> {
            PosixWakeup.open(symbols, syscalls)
        }

        assertTrue(failure.message.orEmpty().contains("fcntl"))
        assertEquals(0, syscalls.pipeCalls.get(), "fcntl must be resolved before pipe allocates fds")
        assertTrue(syscalls.closedFds.isEmpty())
    }

    @Test
    fun throwingWriteDoesNotLeaveTheWakeupPermanentlyPending() {
        val syscalls = FakePosixSyscalls().apply {
            writeThrows += IllegalStateException("injected write failure")
        }
        PosixWakeup.open(allSymbols, syscalls).use { wakeup ->
            assertFailsWith<IllegalStateException> { wakeup.signal() }

            assertTrue(wakeup.signal())
            assertEquals(2, syscalls.writeCalls.get())
            assertTrue(wakeup.drain())
        }
    }

    @Test
    fun signalRetriesEintrTreatsEagainAsCoalescedAndCanRearm() {
        val syscalls = FakePosixSyscalls().apply {
            writeResults += PosixCall.failure(EINTR)
            writeResults += PosixCall.failure(EAGAIN)
        }
        PosixWakeup.open(allSymbols, syscalls).use { wakeup ->
            assertTrue(wakeup.signal())
            assertEquals(2, syscalls.writeCalls.get())
            assertTrue(wakeup.drain(), "an empty EAGAIN drain is successful")

            assertTrue(wakeup.signal())
            assertEquals(3, syscalls.writeCalls.get())
        }
    }

    @Test
    fun drainRetriesEintrAndFailurePreservesRetryability() {
        val syscalls = FakePosixSyscalls().apply {
            readResults += PosixCall.failure(EINTR)
            readResults += PosixCall.failure(5)
            readResults += PosixCall.success(Long.SIZE_BYTES.toLong())
        }
        PosixWakeup.open(allSymbols, syscalls).use { wakeup ->
            assertTrue(wakeup.signal())
            val failure = assertFailsWith<PosixException> { wakeup.drain() }
            assertEquals("read", failure.operation)
            assertEquals(2, syscalls.readCalls.get(), "EINTR must be retried before reporting EIO")

            assertTrue(wakeup.drain())
            assertEquals(3, syscalls.readCalls.get())
        }
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
    fun eventFdEnosysSelectsPipe2Fallback() {
        val syscalls = FakePosixSyscalls().apply {
            eventFdResult = PosixCall.failure(38)
        }

        PosixWakeup.open(allSymbols, syscalls).use { wakeup ->
            assertEquals(syscalls.pipeReadFd, wakeup.readFd)
        }

        assertEquals(1, syscalls.eventFdCalls.get())
        assertEquals(1, syscalls.pipe2Calls.get())
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

    @Test
    fun fcntlFailureRollsBackBothFdsEvenWhenFirstCloseThrows() {
        val syscalls = FakePosixSyscalls().apply {
            fcntlFailure = { fd, command ->
                if (fd == pipeWriteFd && command == F_SETFL) PosixCall.failure(22) else null
            }
            closeThrows[pipeReadFd] = IllegalStateException("injected close failure")
        }
        val symbols = PosixSymbolLookup { name ->
            if (name == "eventfd" || name == "pipe2") null else symbol(name)
        }

        val failure = assertFailsWith<PosixException> {
            PosixWakeup.open(symbols, syscalls)
        }

        assertEquals("fcntl(F_SETFL)", failure.operation)
        assertEquals(listOf(syscalls.pipeReadFd, syscalls.pipeWriteFd), syscalls.closedFds)
        assertEquals(1, failure.suppressed.size)
        assertEquals("injected close failure", failure.suppressed.single().message)
    }

    @Test
    fun closeAttemptsEveryDescriptorAndAggregatesFailures() {
        val syscalls = FakePosixSyscalls().apply {
            closeThrows[pipeReadFd] = IllegalStateException("first close failed")
            closeResults[pipeWriteFd] = PosixCall.failure(9)
        }
        val symbols = PosixSymbolLookup { name -> if (name == "eventfd") null else symbol(name) }
        val wakeup = PosixWakeup.open(symbols, syscalls)

        val failure = assertFailsWith<IllegalStateException> { wakeup.close() }

        assertEquals("first close failed", failure.message)
        assertEquals(listOf(syscalls.pipeReadFd, syscalls.pipeWriteFd), syscalls.closedFds)
        val suppressed = assertIs<PosixException>(failure.suppressed.single())
        assertEquals("close", suppressed.operation)
        assertEquals(9, suppressed.errno)
        wakeup.close()
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
    val eventFdCalls = AtomicInteger()
    val pipe2Calls = AtomicInteger()
    val pipeCalls = AtomicInteger()
    val writeCalls = AtomicInteger()
    val readCalls = AtomicInteger()
    var eventFdResult: PosixCall<Int> = PosixCall.success(eventReadFd)
    var pipe2Result: PosixCall<FdPair> = PosixCall.success(FdPair(pipeReadFd, pipeWriteFd))
    var pipeResult: PosixCall<FdPair> = PosixCall.success(FdPair(pipeReadFd, pipeWriteFd))
    var pipe2Flags: Int? = null
    var fcntlFailure: ((fd: Int, command: Int) -> PosixCall<Int>?)? = null
    val statusFlags = ConcurrentHashMap(mapOf(pipeReadFd to 0, pipeWriteFd to 0))
    val descriptorFlags = ConcurrentHashMap(mapOf(pipeReadFd to 0, pipeWriteFd to 0))
    val closedFds = CopyOnWriteArrayList<Int>()
    val writeResults = ConcurrentLinkedQueue<PosixCall<Long>>()
    val readResults = ConcurrentLinkedQueue<PosixCall<Long>>()
    val writeThrows = ConcurrentLinkedQueue<Throwable>()
    val closeThrows = ConcurrentHashMap<Int, Throwable>()
    val closeResults = ConcurrentHashMap<Int, PosixCall<Int>>()
    private val readable = ConcurrentHashMap.newKeySet<Int>()

    override fun eventFd(symbol: MemorySegment, initialValue: Int, flags: Int): PosixCall<Int> {
        eventFdCalls.incrementAndGet()
        return eventFdResult
    }

    override fun pipe2(symbol: MemorySegment, flags: Int): PosixCall<FdPair> {
        pipe2Calls.incrementAndGet()
        pipe2Flags = flags
        return pipe2Result
    }

    override fun pipe(symbol: MemorySegment): PosixCall<FdPair> {
        pipeCalls.incrementAndGet()
        return pipeResult
    }

    override fun fcntl(
        symbol: MemorySegment,
        fd: Int,
        command: Int,
        argument: Int,
    ): PosixCall<Int> {
        fcntlFailure?.invoke(fd, command)?.let { return it }
        return when (command) {
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
    }

    override fun write(symbol: MemorySegment, fd: Int, bytes: ByteArray): PosixCall<Long> {
        writeCalls.incrementAndGet()
        writeThrows.poll()?.let { throw it }
        val result = writeResults.poll() ?: PosixCall.success(bytes.size.toLong())
        if (result is PosixCall.Success) readable += fd
        return result
    }

    override fun read(symbol: MemorySegment, fd: Int, byteCount: Int): PosixCall<Long> {
        readCalls.incrementAndGet()
        val scripted = readResults.poll()
        if (scripted != null) {
            if (scripted is PosixCall.Success) readable.remove(sourceFd(fd))
            return scripted
        }
        return if (readable.remove(sourceFd(fd))) {
            PosixCall.success(byteCount.toLong())
        } else {
            PosixCall.failure(EAGAIN)
        }
    }

    override fun close(symbol: MemorySegment, fd: Int): PosixCall<Int> {
        closedFds += fd
        closeThrows.remove(fd)?.let { throw it }
        return closeResults.remove(fd) ?: PosixCall.success(0)
    }

    override fun poll(symbol: MemorySegment, fd: Int, timeoutMillis: Int): PosixCall<Boolean> =
        PosixCall.success(sourceFd(fd) in readable)

    private fun sourceFd(fd: Int): Int = if (fd == eventReadFd) eventReadFd else pipeWriteFd
}
