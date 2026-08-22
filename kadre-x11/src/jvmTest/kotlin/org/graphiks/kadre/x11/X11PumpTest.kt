package org.graphiks.kadre.x11

import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.StartCause
import org.graphiks.kffi.posix.PosixWakeup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class X11PumpTest {
    @Test
    fun `pump drains poll-time events after wake consumption in strict order`() {
        val trace = mutableListOf<String>()
        val operations = FakeX11PumpOperations(trace = trace)
        val wakeup = FakePosixWakeup(trace = trace)
        wakeup.signal()

        val result = pumpX11Once(
            operations = operations,
            poller = X11Poller { _, _, _ ->
                trace += "poll"
                operations.enqueueEvents(2)
                X11PollResult(xReadable = true, wakeReadable = true)
            },
            wakeup = wakeup,
            xConnectionFd = 41,
            timeoutMillis = -1,
        )

        assertEquals(2, result.eventsDispatched)
        assertEquals(
            listOf(
                "wake-signal", "pending", "flush", "poll", "wake-drain",
                "pending", "next", "pending", "next", "pending",
            ),
            trace,
        )
    }

    @Test
    fun `EINTR retry uses only the remaining monotonic timeout budget`() {
        val clock = ArrayDeque(listOf(0L, 90_000_000L))
        val attemptedTimeouts = mutableListOf<Int>()

        val result = retryX11Poll(
            timeoutMillis = 100,
            nowNanos = { clock.removeFirst() },
        ) { timeoutMillis ->
            attemptedTimeouts += timeoutMillis
            if (attemptedTimeouts.size == 1) {
                X11PollAttempt.Failure(errno = 4)
            } else {
                X11PollAttempt.Ready(X11PollResult(xReadable = false, wakeReadable = false))
            }
        }

        assertEquals(listOf(100, 10), attemptedTimeouts)
        assertEquals(X11PollResult(xReadable = false, wakeReadable = false), result)
    }

    @Test
    fun `EINTR retry stops when the monotonic timeout budget is exhausted`() {
        val clock = ArrayDeque(listOf(0L, 101_000_000L))
        var attempts = 0

        val result = retryX11Poll(
            timeoutMillis = 100,
            nowNanos = { clock.removeFirst() },
        ) {
            attempts += 1
            X11PollAttempt.Failure(errno = 4)
        }

        assertEquals(1, attempts)
        assertEquals(X11PollResult(xReadable = false, wakeReadable = false), result)
    }

    @Test
    fun `three proxy wake consume cycles rearm the shared fd`() {
        val wakeup = FakePosixWakeup(readFd = 73)
        val proxy = X11EventLoopProxy(wakeup)
        val poller = X11Poller { xConnectionFd, wakeFd, timeoutMillis ->
            assertEquals(41, xConnectionFd)
            assertEquals(73, wakeFd)
            assertEquals(-1, timeoutMillis)
            X11PollResult(xReadable = false, wakeReadable = wakeup.pending)
        }

        repeat(3) {
            proxy.wakeUp()
            val result = pumpX11Once(
                operations = FakeX11PumpOperations(),
                poller = poller,
                wakeup = wakeup,
                xConnectionFd = 41,
                timeoutMillis = -1,
            )
            assertTrue(result.pollResult.wakeReadable)
            assertFalse(wakeup.pending)
        }

        assertEquals(3, wakeup.signalCount)
        assertEquals(3, wakeup.drainCount)
    }

    @Test
    fun `one pump drains all already-pending X events without blocking`() {
        val operations = FakeX11PumpOperations(pendingEvents = 3)
        val pollCalls = mutableListOf<Triple<Int, Int, Int>>()
        val result = pumpX11Once(
            operations = operations,
            poller = X11Poller { xConnectionFd, wakeFd, timeoutMillis ->
                operations.recordPoll()
                pollCalls += Triple(xConnectionFd, wakeFd, timeoutMillis)
                X11PollResult(xReadable = false, wakeReadable = false)
            },
            wakeup = FakePosixWakeup(readFd = 73),
            xConnectionFd = 41,
            timeoutMillis = -1,
        )

        assertEquals(3, result.eventsDispatched)
        assertEquals(3, operations.dispatchedEvents)
        assertEquals(listOf(Triple(41, 73, 0)), pollCalls)
        assertEquals(
            listOf(
                "pending", "next", "pending", "next", "pending", "next", "pending",
                "flush", "poll", "pending",
            ),
            operations.traceWithPoll,
        )
    }

    @Test
    fun `poll watches the X connection and wake descriptors`() {
        val calls = mutableListOf<Triple<Int, Int, Int>>()

        pumpX11Once(
            operations = FakeX11PumpOperations(),
            poller = X11Poller { xConnectionFd, wakeFd, timeoutMillis ->
                calls += Triple(xConnectionFd, wakeFd, timeoutMillis)
                X11PollResult(xReadable = false, wakeReadable = false)
            },
            wakeup = FakePosixWakeup(readFd = 29),
            xConnectionFd = 17,
            timeoutMillis = 250,
        )

        assertEquals(listOf(Triple(17, 29, 250)), calls)
    }

    @Test
    fun `WaitUntil is cancelled by either descriptor`() {
        val deadline = 1_000L

        val xReady = dispatchX11Once(
            controlFlow = ControlFlow.WaitUntil(deadline),
            operations = FakeX11PumpOperations(),
            poller = fixedPoller(X11PollResult(xReadable = true, wakeReadable = false)),
            wakeup = FakePosixWakeup(),
            xConnectionFd = 41,
            nowMillis = { 900L },
        )
        val wakeReady = dispatchX11Once(
            controlFlow = ControlFlow.WaitUntil(deadline),
            operations = FakeX11PumpOperations(),
            poller = fixedPoller(X11PollResult(xReadable = false, wakeReadable = true)),
            wakeup = FakePosixWakeup(),
            xConnectionFd = 41,
            nowMillis = { 900L },
        )

        assertEquals(StartCause.WaitCancelled(deadline), xReady)
        assertEquals(StartCause.WaitCancelled(deadline), wakeReady)
    }

    @Test
    fun `WaitUntil reaches its deadline only at or after the requested epoch`() {
        val deadline = 1_000L

        val beforeDeadline = dispatchX11Once(
            controlFlow = ControlFlow.WaitUntil(deadline),
            operations = FakeX11PumpOperations(),
            poller = fixedPoller(X11PollResult(xReadable = false, wakeReadable = false)),
            wakeup = FakePosixWakeup(),
            xConnectionFd = 41,
            nowMillis = { 999L },
        )
        val atDeadline = dispatchX11Once(
            controlFlow = ControlFlow.WaitUntil(deadline),
            operations = FakeX11PumpOperations(),
            poller = fixedPoller(X11PollResult(xReadable = false, wakeReadable = false)),
            wakeup = FakePosixWakeup(),
            xConnectionFd = 41,
            nowMillis = { 1_000L },
        )
        val afterDeadline = dispatchX11Once(
            controlFlow = ControlFlow.WaitUntil(deadline),
            operations = FakeX11PumpOperations(),
            poller = fixedPoller(X11PollResult(xReadable = false, wakeReadable = false)),
            wakeup = FakePosixWakeup(),
            xConnectionFd = 41,
            nowMillis = { 1_001L },
        )

        assertFalse(beforeDeadline is StartCause.ResumeTimeReached)
        assertEquals(StartCause.ResumeTimeReached(deadline, deadline), atDeadline)
        assertEquals(StartCause.ResumeTimeReached(deadline, 1_001L), afterDeadline)
    }

    private fun fixedPoller(result: X11PollResult): X11Poller = X11Poller { _, _, _ -> result }
}

private class FakeX11PumpOperations(
    pendingEvents: Int = 0,
    private val trace: MutableList<String> = mutableListOf(),
) : X11PumpOperations {
    private var remainingEvents = pendingEvents
    var dispatchedEvents: Int = 0
        private set
    val traceWithPoll: List<String>
        get() = trace

    override fun pendingCount(): Int {
        trace += "pending"
        return remainingEvents
    }

    override fun dispatchNext() {
        check(remainingEvents > 0)
        trace += "next"
        remainingEvents -= 1
        dispatchedEvents += 1
    }

    override fun flush() {
        trace += "flush"
    }

    fun recordPoll() {
        trace += "poll"
    }

    fun enqueueEvents(count: Int) {
        require(count >= 0)
        remainingEvents += count
    }
}

private class FakePosixWakeup(
    override val readFd: Int = 73,
    private val trace: MutableList<String>? = null,
) : PosixWakeup {
    var pending: Boolean = false
        private set
    var signalCount: Int = 0
        private set
    var drainCount: Int = 0
        private set

    override fun signal(): Boolean {
        signalCount += 1
        pending = true
        trace?.add("wake-signal")
        return true
    }

    override fun drain(): Boolean {
        drainCount += 1
        pending = false
        trace?.add("wake-drain")
        return true
    }

    override fun close() = Unit
}
