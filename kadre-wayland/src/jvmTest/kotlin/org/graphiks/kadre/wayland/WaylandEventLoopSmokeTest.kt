/**
 * Smoke tests for WaylandEventLoop and WaylandEventLoopProxy.
 *
 * These tests do not require a running Wayland server:
 * they only verify the static invariants and the null-safe
 * behavior of the bindings.
 *
 * WaylandEventLoop.
 */
package org.graphiks.kadre.wayland

import org.graphiks.kffi.wayland.*
import org.graphiks.kadre.core.DeviceEvents
import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.ffi.posix.PosixWakeup
import org.graphiks.kadre.ffi.posix.PosixException
import org.graphiks.kadre.test.EventLoopConformanceDriver
import org.graphiks.kadre.test.ObservedCallback
import org.graphiks.kadre.test.assertWakeUpRearms
import java.lang.foreign.Arena
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WaylandEventLoopSmokeTest {

    @Test
    fun `native poll uses Linux nfds ABI and captures errno`() {
        if (System.getProperty("os.name") != "Linux") return

        Arena.ofConfined().use { arena ->
            val pollFds = allocPollFd(arena)
            setPollFd(pollFds, 0, -1, POLLIN)
            setPollFd(pollFds, 1, -1, POLLIN)

            val success = invokeNativePoll(pollFds, 2L, 0)
            assertEquals(0, success.value)
            assertNull(success.errno)

            val failure = invokeNativePoll(pollFds, Long.MAX_VALUE, 0)
            assertTrue(failure.value < 0)
            assertTrue((failure.errno ?: 0) > 0)
        }
    }

    @Test
    fun `infinite poll EINTR cancels prepared read then retries infinitely`() {
        val trace = mutableListOf<String>()
        val operations = FakeWaylandPumpOperations(trace)
        val wakeup = FakePosixWakeup(externalTrace = trace)
        val poller = FakeWaylandPoller(
            trace,
            WaylandPollResult.Failure(errno = 4),
            WaylandPollResult.Ready(displayReadable = true, wakeReadable = true),
        )

        val readiness = pumpWaylandOnce(
            operations = operations,
            poller = poller,
            wakeup = wakeup,
            displayFd = 41,
            timeoutMs = -1,
        )

        assertEquals(WaylandPollResult.Ready(displayReadable = true, wakeReadable = true), readiness)
        assertEquals(
            listOf(
                "prepare", "flush", "poll(41,73,-1)", "cancel",
                "prepare", "flush", "poll(41,73,-1)", "read", "dispatch", "drain",
            ),
            trace,
        )
    }

    @Test
    fun `positive timeout budget decreases after EINTR and expires without rearming`() {
        val trace = mutableListOf<String>()
        val clock = FakeWaylandMonotonicClock(
            0L,
            40_000_000L,
            100_000_000L,
        )

        val readiness = pumpWaylandOnce(
            operations = FakeWaylandPumpOperations(trace),
            poller = FakeWaylandPoller(
                trace,
                WaylandPollResult.Failure(errno = 4),
                WaylandPollResult.Failure(errno = 4),
            ),
            wakeup = FakePosixWakeup(externalTrace = trace),
            displayFd = 45,
            timeoutMs = 100,
            clock = clock,
        )

        assertEquals(WaylandPollResult.Ready(displayReadable = false, wakeReadable = false), readiness)
        assertEquals(
            listOf(
                "prepare", "flush", "poll(45,73,100)", "cancel",
                "prepare", "flush", "poll(45,73,60)", "cancel",
            ),
            trace,
        )
        assertEquals(3, clock.calls)
    }

    @Test
    fun `timeout budget survives nanoTime wraparound`() {
        val trace = mutableListOf<String>()
        val clock = FakeWaylandMonotonicClock(
            Long.MAX_VALUE - 4L,
            Long.MIN_VALUE + 1_000_005L,
        )

        pumpWaylandOnce(
            operations = FakeWaylandPumpOperations(trace),
            poller = FakeWaylandPoller(
                trace,
                WaylandPollResult.Failure(errno = 4),
                WaylandPollResult.Ready(displayReadable = false, wakeReadable = false),
            ),
            wakeup = FakePosixWakeup(externalTrace = trace),
            displayFd = 49,
            timeoutMs = 2,
            clock = clock,
        )

        assertEquals(
            listOf(
                "prepare", "flush", "poll(49,73,2)", "cancel",
                "prepare", "flush", "poll(49,73,1)", "cancel",
            ),
            trace,
        )
    }

    @Test
    fun `sub millisecond remaining budget rounds up to one millisecond`() {
        val trace = mutableListOf<String>()
        val clock = FakeWaylandMonotonicClock(0L, 1_999_999L)

        pumpWaylandOnce(
            operations = FakeWaylandPumpOperations(trace),
            poller = FakeWaylandPoller(
                trace,
                WaylandPollResult.Failure(errno = 4),
                WaylandPollResult.Ready(displayReadable = false, wakeReadable = false),
            ),
            wakeup = FakePosixWakeup(externalTrace = trace),
            displayFd = 50,
            timeoutMs = 2,
            clock = clock,
        )

        assertEquals(
            listOf(
                "prepare", "flush", "poll(50,73,2)", "cancel",
                "prepare", "flush", "poll(50,73,1)", "cancel",
            ),
            trace,
        )
    }

    @Test
    fun `zero timeout stays nonblocking across EINTR`() {
        val trace = mutableListOf<String>()

        pumpWaylandOnce(
            operations = FakeWaylandPumpOperations(trace),
            poller = FakeWaylandPoller(
                trace,
                WaylandPollResult.Failure(errno = 4),
                WaylandPollResult.Ready(displayReadable = false, wakeReadable = false),
            ),
            wakeup = FakePosixWakeup(externalTrace = trace),
            displayFd = 46,
            timeoutMs = 0,
            clock = WaylandMonotonicClock { error("zero timeout must not consult the clock") },
        )

        assertEquals(
            listOf(
                "prepare", "flush", "poll(46,73,0)", "cancel",
                "prepare", "flush", "poll(46,73,0)", "cancel",
            ),
            trace,
        )
    }

    @Test
    fun `native poll decoder reports every descriptor error flag for display and wake`() {
        val cases = listOf(
            Triple(POLLERR, 0.toShort(), "display POLLERR"),
            Triple(POLLHUP, 0.toShort(), "display POLLHUP"),
            Triple(POLLNVAL, 0.toShort(), "display POLLNVAL"),
            Triple(0.toShort(), POLLERR, "wake POLLERR"),
            Triple(0.toShort(), POLLHUP, "wake POLLHUP"),
            Triple(0.toShort(), POLLNVAL, "wake POLLNVAL"),
        )

        for ((displayRevents, wakeRevents, label) in cases) {
            val result = assertIs<WaylandPollResult.DescriptorFailure>(
                decodeWaylandPollResult(
                    pollCount = 1,
                    displayRevents = displayRevents,
                    wakeRevents = wakeRevents,
                ),
                label,
            )
            assertEquals(displayRevents, result.displayRevents, label)
            assertEquals(wakeRevents, result.wakeRevents, label)
        }
    }

    @Test
    fun `positive poll count without revents is a descriptor failure`() {
        val result = decodeWaylandPollResult(
            pollCount = 1,
            displayRevents = 0,
            wakeRevents = 0,
        )

        assertEquals(
            WaylandPollResult.DescriptorFailure(displayRevents = 0, wakeRevents = 0),
            result,
        )
    }

    @Test
    fun `descriptor poll error cancels prepared read and reports both descriptors`() {
        val trace = mutableListOf<String>()

        val failure = assertFailsWith<IllegalStateException> {
            pumpWaylandOnce(
                operations = FakeWaylandPumpOperations(trace),
                poller = FakeWaylandPoller(
                    trace,
                    WaylandPollResult.DescriptorFailure(
                        displayRevents = POLLERR,
                        wakeRevents = POLLHUP,
                    ),
                ),
                wakeup = FakePosixWakeup(externalTrace = trace),
                displayFd = 47,
                timeoutMs = -1,
            )
        }

        assertTrue(failure.message.orEmpty().contains("display=POLLERR"))
        assertTrue(failure.message.orEmpty().contains("wake=POLLHUP"))
        assertEquals(listOf("prepare", "flush", "poll(47,73,-1)", "cancel"), trace)
    }

    @Test
    fun `poll error cancels prepared read before propagating errno`() {
        val trace = mutableListOf<String>()
        val operations = FakeWaylandPumpOperations(trace)
        val poller = FakeWaylandPoller(trace, WaylandPollResult.Failure(errno = 5))

        val failure = assertFailsWith<PosixException> {
            pumpWaylandOnce(
                operations = operations,
                poller = poller,
                wakeup = FakePosixWakeup(externalTrace = trace),
                displayFd = 42,
                timeoutMs = -1,
            )
        }

        assertEquals("poll", failure.operation)
        assertEquals(5, failure.errno)
        assertEquals(listOf("prepare", "flush", "poll(42,73,-1)", "cancel"), trace)
    }

    @Test
    fun `poll errno stays primary when cancel read also fails`() {
        val trace = mutableListOf<String>()

        val failure = assertFailsWith<PosixException> {
            pumpWaylandOnce(
                operations = FakeWaylandPumpOperations(trace, failCancel = true),
                poller = FakeWaylandPoller(trace, WaylandPollResult.Failure(errno = 5)),
                wakeup = FakePosixWakeup(externalTrace = trace),
                displayFd = 48,
                timeoutMs = -1,
            )
        }

        assertEquals(5, failure.errno)
        assertEquals("injected cancel failure", failure.suppressed.single().message)
        assertEquals(listOf("prepare", "flush", "poll(48,73,-1)", "cancel"), trace)
    }

    @Test
    fun `flush error cancels the prepared read before propagating`() {
        val trace = mutableListOf<String>()
        val failure = assertFailsWith<IllegalStateException> {
            pumpWaylandOnce(
                operations = FakeWaylandPumpOperations(trace, failFlush = true),
                poller = FakeWaylandPoller(
                    trace,
                    WaylandPollResult.Ready(displayReadable = false, wakeReadable = false),
                ),
                wakeup = FakePosixWakeup(externalTrace = trace),
                displayFd = 44,
                timeoutMs = 10,
            )
        }

        assertEquals("injected flush failure", failure.message)
        assertEquals(listOf("prepare", "flush", "cancel"), trace)
    }

    @Test
    fun `timeout cancels prepared read without reading or draining`() {
        val trace = mutableListOf<String>()

        val readiness = pumpWaylandOnce(
            operations = FakeWaylandPumpOperations(trace),
            poller = FakeWaylandPoller(
                trace,
                WaylandPollResult.Ready(displayReadable = false, wakeReadable = false),
            ),
            wakeup = FakePosixWakeup(externalTrace = trace),
            displayFd = 43,
            timeoutMs = 0,
        )

        assertEquals(WaylandPollResult.Ready(displayReadable = false, wakeReadable = false), readiness)
        assertEquals(listOf("prepare", "flush", "poll(43,73,0)", "cancel"), trace)
    }

    @Test
    fun `cleanup closes wakeup before disconnecting display`() {
        val trace = mutableListOf<String>()
        val wakeup = FakePosixWakeup(externalTrace = trace)

        closeWaylandResources(wakeup) { trace += "disconnect" }

        assertEquals(listOf("close", "disconnect"), trace)
    }

    @Test
    fun `cleanup disconnects display even when wakeup close fails`() {
        val trace = mutableListOf<String>()
        val wakeup = FakePosixWakeup(externalTrace = trace, failClose = true)

        val failure = assertFailsWith<IllegalStateException> {
            closeWaylandResources(wakeup) { trace += "disconnect" }
        }

        assertEquals("injected close failure", failure.message)
        assertEquals(listOf("close", "disconnect"), trace)
    }

    @Test
    fun `cleanup keeps wakeup failure primary and suppresses disconnect failure`() {
        val trace = mutableListOf<String>()
        val wakeupFailure = IllegalStateException("wakeup-close")
        val disconnectFailure = IllegalArgumentException("display-disconnect")
        val wakeup = object : PosixWakeup {
            override val readFd: Int = 73
            override fun signal(): Boolean = true
            override fun drain(): Boolean = true
            override fun close() {
                trace += "close"
                throw wakeupFailure
            }
        }

        val failure = assertFailsWith<IllegalStateException> {
            closeWaylandResources(wakeup) {
                trace += "disconnect"
                throw disconnectFailure
            }
        }

        assertSame(wakeupFailure, failure)
        assertEquals(listOf("close", "disconnect"), trace)
        assertEquals(listOf(disconnectFailure), failure.suppressed.toList())
    }

    @Test
    fun `run cleanup keeps body failure primary and suppresses every cleanup failure in order`() {
        val trace = mutableListOf<String>()
        val bodyFailure = IllegalStateException("handler")
        val wakeupFailure = IllegalArgumentException("wakeup")
        val bindingFailure = UnsupportedOperationException("binding")

        val failure = assertFailsWith<IllegalStateException> {
            preservingWaylandCleanup(
                cleanupActions = listOf(
                    {
                        trace += "wakeup"
                        throw wakeupFailure
                    },
                    {
                        trace += "binding"
                        throw bindingFailure
                    },
                ),
            ) {
                trace += "body"
                throw bodyFailure
            }
        }

        assertSame(bodyFailure, failure)
        assertEquals(listOf("body", "wakeup", "binding"), trace)
        assertEquals(listOf(wakeupFailure, bindingFailure), failure.suppressed.toList())
    }

    /**
     * Verifies that waylandRunning is false at JVM startup.
     *
     * If this test fails, it means a Wayland loop is already running — which
     * would be unexpected in an isolated unit-test context.
     */
    @Test
    fun `waylandRunning starts false`() {
        assertFalse(waylandRunning.get(), "waylandRunning must be false at startup")
    }

    @Test
    fun `wakeUp proxy rearms for every conformance cycle`() {
        val wakeup = FakePosixWakeup()

        assertWakeUpRearms { WaylandProxyConformanceDriver(wakeup) }

        assertEquals(3, wakeup.successfulSignals)
        assertEquals(3, wakeup.successfulDrains)
        assertEquals(
            listOf("signal", "drain", "signal", "drain", "signal", "drain", "close"),
            wakeup.trace,
        )
    }

    @Test
    fun `drain failure preserves wake retryability`() {
        val wakeup = FakePosixWakeup(failNextDrain = true)
        val proxy = WaylandEventLoopProxy(wakeup)

        proxy.wakeUp()
        assertFailsWith<IllegalStateException> { wakeup.drain() }
        proxy.wakeUp()
        assertTrue(wakeup.drain())
        proxy.wakeUp()
        assertTrue(wakeup.drain())

        assertEquals(3, wakeup.signalAttempts)
        assertEquals(2, wakeup.successfulSignals)
        assertEquals(2, wakeup.successfulDrains)
    }

    @Test
    fun `close prevents later wake signals`() {
        val wakeup = FakePosixWakeup()
        val proxy = WaylandEventLoopProxy(wakeup)

        proxy.wakeUp()
        wakeup.close()
        proxy.wakeUp()

        assertEquals(2, wakeup.signalAttempts)
        assertEquals(1, wakeup.successfulSignals)
        assertEquals(listOf("signal", "close", "signal-closed"), wakeup.trace)
    }

    @Test
    fun `primaryMonitor stays null even when synthetic monitors are available`() {
        val loop = WaylandEventLoop(
            displayPtr = 77L,
            compositorPtr = 0L,
            xdgWmBasePtr = 0L,
            shmPtr = 0L,
            wakeup = FakePosixWakeup(),
        )
        val window = WaylandWindow.createForTest(
            display = 77L,
            surface = 1001L,
            attrs = WindowAttributes(size = PhysicalSize(640, 480)),
        )
        loop.windows[window.id.value] = window

        assertTrue(loop.availableMonitors().isNotEmpty())
        assertEquals(null, loop.primaryMonitor())
    }

    @Test
    fun `routeWaylandInputEvent enqueues event for matching surface`() {
        val window = WaylandWindow.createForTest(surface = 1001L)
        val queue = ConcurrentLinkedQueue<Pair<WindowId, WindowEvent>>()
        val event = WindowEvent.Focused(true)

        val routed = routeWaylandInputEvent(
            surfacePtr = 1001L,
            event = event,
            windows = mapOf(1001L to window),
            eventQueue = queue,
        )

        assertTrue(routed)
        val queued = queue.poll()
        assertNotNull(queued)
        assertEquals(window.id, queued.first)
        assertEquals(event, queued.second)
    }

    @Test
    fun `routeWaylandInputEvent drops unknown surface`() {
        val window = WaylandWindow.createForTest(surface = 1001L)
        val queue = ConcurrentLinkedQueue<Pair<WindowId, WindowEvent>>()

        val routed = routeWaylandInputEvent(
            surfacePtr = 2002L,
            event = WindowEvent.Focused(true),
            windows = mapOf(1001L to window),
            eventQueue = queue,
        )

        assertFalse(routed)
        assertTrue(queue.isEmpty())
    }

    @Test
    fun `systemTheme returns null when no dbus-send available`() {
        val loop = WaylandEventLoop(
            displayPtr = 77L,
            compositorPtr = 0L,
            xdgWmBasePtr = 0L,
            shmPtr = 0L,
            wakeup = FakePosixWakeup(),
        )
        WaylandThemePortal.resetCache()
        assertNull(loop.systemTheme())
    }

    @Test
    fun `systemTheme is idempotent`() {
        val loop = WaylandEventLoop(
            displayPtr = 77L,
            compositorPtr = 0L,
            xdgWmBasePtr = 0L,
            shmPtr = 0L,
            wakeup = FakePosixWakeup(),
        )
        WaylandThemePortal.resetCache()
        val first = loop.systemTheme()
        val second = loop.systemTheme()
        assertEquals(first, second)
    }

    @Test
    fun `refreshTheme does not crash with no windows`() {
        val loop = WaylandEventLoop(
            displayPtr = 77L,
            compositorPtr = 0L,
            xdgWmBasePtr = 0L,
            shmPtr = 0L,
            wakeup = FakePosixWakeup(),
        )
        WaylandThemePortal.resetCache()
        loop.refreshTheme()
    }

    @Test
    fun `refreshTheme emits ThemeChanged when theme is available`() {
        val loop = WaylandEventLoop(
            displayPtr = 77L,
            compositorPtr = 0L,
            xdgWmBasePtr = 0L,
            shmPtr = 0L,
            wakeup = FakePosixWakeup(),
        )
        WaylandThemePortal.resetCache()
        // Force cache to a known state by calling systemTheme first
        loop.systemTheme()
        // refreshTheme should not crash; on CI it may still be null
        loop.refreshTheme()
    }

    // ── R4: device event filter ───────────────────────────────────────────────

    @Test
    fun `deviceEventFilter defaults to WhenFocused`() {
        val loop = WaylandEventLoop(
            displayPtr = 77L,
            compositorPtr = 0L,
            xdgWmBasePtr = 0L,
            shmPtr = 0L,
            wakeup = FakePosixWakeup(),
        )
        assertEquals(DeviceEvents.WhenFocused, loop.deviceEventFilter)
    }

    @Test
    fun `listenDeviceEvents stores Never`() {
        val loop = WaylandEventLoop(
            displayPtr = 77L,
            compositorPtr = 0L,
            xdgWmBasePtr = 0L,
            shmPtr = 0L,
            wakeup = FakePosixWakeup(),
        )
        loop.listenDeviceEvents(DeviceEvents.Never)
        assertEquals(DeviceEvents.Never, loop.deviceEventFilter)
    }

    @Test
    fun `listenDeviceEvents stores Always`() {
        val loop = WaylandEventLoop(
            displayPtr = 77L,
            compositorPtr = 0L,
            xdgWmBasePtr = 0L,
            shmPtr = 0L,
            wakeup = FakePosixWakeup(),
        )
        loop.listenDeviceEvents(DeviceEvents.Always)
        assertEquals(DeviceEvents.Always, loop.deviceEventFilter)
    }

    @Test
    fun `listenDeviceEvents toggles between modes`() {
        val loop = WaylandEventLoop(
            displayPtr = 77L,
            compositorPtr = 0L,
            xdgWmBasePtr = 0L,
            shmPtr = 0L,
            wakeup = FakePosixWakeup(),
        )
        loop.listenDeviceEvents(DeviceEvents.Never)
        assertEquals(DeviceEvents.Never, loop.deviceEventFilter)
        loop.listenDeviceEvents(DeviceEvents.WhenFocused)
        assertEquals(DeviceEvents.WhenFocused, loop.deviceEventFilter)
        loop.listenDeviceEvents(DeviceEvents.Always)
        assertEquals(DeviceEvents.Always, loop.deviceEventFilter)
    }
}

private class WaylandProxyConformanceDriver(
    private val wakeup: FakePosixWakeup,
) : EventLoopConformanceDriver {
    override val trace = mutableListOf<ObservedCallback>()
    private val proxy = WaylandEventLoopProxy(wakeup)

    override fun start() = Unit

    override fun wakeUp() {
        proxy.wakeUp()
    }

    override fun requestRedraw() = Unit

    override fun waitForIdle() {
        check(wakeup.drain())
        trace += ObservedCallback.NewEvents
        trace += ObservedCallback.AboutToWait
    }

    override fun closeWindow() = Unit

    override fun shutdown() {
        wakeup.close()
    }
}

private class FakePosixWakeup(
    private var failNextDrain: Boolean = false,
    private val externalTrace: MutableList<String>? = null,
    private val failClose: Boolean = false,
) : PosixWakeup {
    override val readFd: Int = 73
    val trace = mutableListOf<String>()
    var signalAttempts = 0
        private set
    var successfulSignals = 0
        private set
    var successfulDrains = 0
        private set
    private var pending = false
    private var closed = false

    override fun signal(): Boolean {
        signalAttempts += 1
        if (closed) {
            trace += "signal-closed"
            return false
        }
        trace += "signal"
        if (!pending) {
            pending = true
            successfulSignals += 1
        }
        return true
    }

    override fun drain(): Boolean {
        if (closed) return false
        trace += "drain"
        externalTrace?.add("drain")
        if (failNextDrain) {
            failNextDrain = false
            throw IllegalStateException("injected drain failure")
        }
        if (pending) {
            pending = false
            successfulDrains += 1
        }
        return true
    }

    override fun close() {
        if (closed) return
        closed = true
        pending = false
        trace += "close"
        externalTrace?.add("close")
        if (failClose) throw IllegalStateException("injected close failure")
    }
}

private class FakeWaylandPumpOperations(
    private val trace: MutableList<String>,
    private val failFlush: Boolean = false,
    private val failCancel: Boolean = false,
) : WaylandPumpOperations {
    override fun prepareRead(): Int {
        trace += "prepare"
        return 0
    }

    override fun dispatchPending() {
        trace += "dispatch"
    }

    override fun flush() {
        trace += "flush"
        if (failFlush) throw IllegalStateException("injected flush failure")
    }

    override fun readEvents() {
        trace += "read"
    }

    override fun cancelRead() {
        trace += "cancel"
        if (failCancel) throw IllegalStateException("injected cancel failure")
    }
}

private class FakeWaylandMonotonicClock(
    vararg readings: Long,
) : WaylandMonotonicClock {
    private val readings = ArrayDeque(readings.toList())
    var calls: Int = 0
        private set

    override fun nowNanos(): Long {
        calls += 1
        return readings.removeFirst()
    }
}

private class FakeWaylandPoller(
    private val trace: MutableList<String>,
    vararg results: WaylandPollResult,
) : WaylandPoller {
    private val results = ArrayDeque(results.toList())

    override fun poll(
        displayFd: Int,
        wakeFd: Int,
        timeoutMs: Int,
    ): WaylandPollResult {
        trace += "poll($displayFd,$wakeFd,$timeoutMs)"
        return results.removeFirst()
    }
}
