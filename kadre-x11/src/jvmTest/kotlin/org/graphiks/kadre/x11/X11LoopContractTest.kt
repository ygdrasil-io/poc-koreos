package org.graphiks.kadre.x11

import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import org.graphiks.kadre.ffi.posix.PosixWakeup
import org.graphiks.kadre.test.RecordingApplicationHandler
import org.graphiks.kadre.test.assertIterationOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class X11LoopContractTest {
    @Test
    fun `startup lifecycle satisfies the shared iteration contract`() {
        val loop = testLoop(RecordingX11Wakeup(), FakeX11NativeAdapter())
        val handler = RecordingApplicationHandler()

        startX11Lifecycle(loop, handler)

        assertIterationOrder(handler.trace)
    }

    @Test
    fun `startup lifecycle reaches about to wait before the first native pump`() {
        val loop = testLoop(RecordingX11Wakeup(), FakeX11NativeAdapter())
        val trace = mutableListOf<String>()

        startX11Lifecycle(loop, recordingHandler(trace = trace))

        assertEquals(
            listOf(
                "resumed",
                "newEvents-${StartCause.Init}",
                "canCreateSurfaces",
                "aboutToWait",
            ),
            trace,
        )
    }

    @Test
    fun `iteration starts after pump then dispatches queued events before about to wait and poll`() {
        val loop = testLoop(RecordingX11Wakeup(), FakeX11NativeAdapter())
        val window = loop.createWindow(WindowAttributes(title = "iteration"))
        val trace = mutableListOf<String>()
        val operations = ContractPumpOperations(trace) {
            loop.enqueueWindowEvent(window.id, WindowEvent.Focused(true))
        }
        var pollCount = 0
        val poller = X11Poller { _, _, _ ->
            pollCount += 1
            trace += if (pollCount == 1) "pump-poll" else "next-poll"
            if (pollCount == 1) operations.enqueueEvent()
            X11PollResult(xReadable = pollCount == 1, wakeReadable = false)
        }

        val cause = dispatchX11Once(
            controlFlow = ControlFlow.Wait,
            operations = operations,
            poller = poller,
            wakeup = loop.wakeup,
            xConnectionFd = 41,
        )
        trace += "cause-$cause"
        dispatchX11Iteration(loop, recordingHandler(trace = trace), cause)
        dispatchX11Once(
            controlFlow = ControlFlow.Poll,
            operations = operations,
            poller = poller,
            wakeup = loop.wakeup,
            xConnectionFd = 41,
        )

        assertEquals(
            listOf(
                "pump-poll",
                "native-dispatch",
                "cause-${StartCause.WaitCancelled()}",
                "newEvents-${StartCause.WaitCancelled()}",
                "event-Focused",
                "aboutToWait",
                "next-poll",
            ),
            trace,
        )
    }

    @Test
    fun `ten redraw requests coalesce to one event and one idle wake`() {
        val wakeup = RecordingX11Wakeup()
        val loop = testLoop(wakeup, FakeX11NativeAdapter())
        val window = loop.createWindow(WindowAttributes(title = "redraw"))
        val events = mutableListOf<WindowEvent>()

        repeat(10) { window.requestRedraw() }

        assertEquals(1, wakeup.signalCalls)
        dispatchX11Iteration(loop, recordingHandler(events = events), StartCause.WaitCancelled())
        assertEquals(listOf<WindowEvent>(WindowEvent.RedrawRequested), events)
    }

    @Test
    fun `native expose coalesces a pending requested redraw`() {
        val wakeup = RecordingX11Wakeup()
        val loop = testLoop(wakeup, FakeX11NativeAdapter())
        val window = loop.createWindow(WindowAttributes(title = "expose"))
        val events = mutableListOf<WindowEvent>()

        window.requestRedraw()
        assertTrue(loop.enqueueExpose(window.id))
        dispatchX11Iteration(loop, recordingHandler(events = events), StartCause.WaitCancelled())

        assertEquals(1, wakeup.signalCalls)
        assertEquals(listOf<WindowEvent>(WindowEvent.RedrawRequested), events)
    }

    @Test
    fun `requested redraw coalesces with an already queued native expose`() {
        val wakeup = RecordingX11Wakeup()
        val loop = testLoop(wakeup, FakeX11NativeAdapter())
        val window = loop.createWindow(WindowAttributes(title = "expose-first"))
        val events = mutableListOf<WindowEvent>()

        assertTrue(loop.enqueueExpose(window.id))
        window.requestRedraw()
        dispatchX11Iteration(loop, recordingHandler(events = events), StartCause.WaitCancelled())

        assertEquals(0, wakeup.signalCalls)
        assertEquals(listOf<WindowEvent>(WindowEvent.RedrawRequested), events)
    }

    @Test
    fun `redraw requested from redraw callback is deferred to the next boundary`() {
        val loop = testLoop(RecordingX11Wakeup(), FakeX11NativeAdapter())
        val window = loop.createWindow(WindowAttributes(title = "rearm"))
        val events = mutableListOf<WindowEvent>()
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                events += event
                if (events.size == 1) window.requestRedraw()
            }
        }

        loop.enqueueExpose(window.id)
        dispatchX11Iteration(loop, handler, StartCause.Poll)
        assertEquals(listOf<WindowEvent>(WindowEvent.RedrawRequested), events)

        dispatchX11Iteration(loop, handler, StartCause.Poll)
        assertEquals(
            listOf<WindowEvent>(WindowEvent.RedrawRequested, WindowEvent.RedrawRequested),
            events,
        )
    }

    @Test
    fun `pending redraw IDs retain first insertion order`() {
        val loop = testLoop(RecordingX11Wakeup(), FakeX11NativeAdapter())
        val first = loop.createWindow(WindowAttributes(title = "first"))
        val second = loop.createWindow(WindowAttributes(title = "second"))
        val redrawIds = mutableListOf<WindowId>()
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event == WindowEvent.RedrawRequested) redrawIds += windowId
            }
        }

        second.requestRedraw()
        first.requestRedraw()
        second.requestRedraw()
        dispatchX11Iteration(loop, handler, StartCause.WaitCancelled())

        assertEquals(listOf(second.id, first.id), redrawIds)
    }

    @Test
    fun `close removes before native destroy clears queued work and emits one terminal event`() {
        val wakeup = RecordingX11Wakeup()
        lateinit var loop: X11EventLoop
        val loopThread = Thread.currentThread()
        val native = FakeX11NativeAdapter(
            onDestroy = { xid ->
                assertEquals(41L, xid)
                assertFalse(loop.windows.containsKey(xid))
                assertEquals(loopThread, Thread.currentThread())
            },
        )
        loop = testLoop(wakeup, native)
        val window = loop.createWindow(WindowAttributes(title = "close"))
        val events = mutableListOf<WindowEvent>()
        loop.dragSourceWindows[window.id.value] = 900L
        loop.pendingDropRequests[window.id.value] = PhysicalPosition(1.0, 2.0)
        loop.pendingXdndDrops.add(
            PendingXdndDrop(
                targetWindow = window.id.value,
                sourceWindow = 900L,
                position = PhysicalPosition(1.0, 2.0),
            ),
        )

        val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "x11-window-caller") }
        try {
            executor.submit {
                window.requestRedraw()
                window.close()
                window.close()
            }.get()
        } finally {
            executor.shutdownNow()
        }
        assertFalse(loop.enqueueWindowEvent(window.id, WindowEvent.Focused(true)))

        assertFalse(loop.windows.containsKey(window.id.value))
        assertTrue(native.trace.isEmpty())
        assertEquals(2, wakeup.signalCalls)
        dispatchX11Iteration(loop, recordingHandler(events = events), StartCause.WaitCancelled())

        assertEquals(listOf("destroy-41", "flush"), native.trace)
        assertEquals(listOf<WindowEvent>(WindowEvent.Destroyed), events)
        assertFalse(loop.windows.containsKey(window.id.value))
        assertFalse(loop.dragSourceWindows.containsKey(window.id.value))
        assertFalse(loop.pendingDropRequests.containsKey(window.id.value))
        assertTrue(loop.pendingXdndDrops.none { it.targetWindow == window.id.value })

        events.clear()
        assertFalse(loop.nativeWindowDestroyed(window.id))
        dispatchX11Iteration(loop, recordingHandler(events = events), StartCause.Poll)
        assertTrue(events.isEmpty())
        assertEquals(listOf("destroy-41", "flush"), native.trace)
    }

    @Test
    fun `public close tombstones an event already captured by the boundary while other windows remain live`() {
        val loop = testLoop(RecordingX11Wakeup(), FakeX11NativeAdapter())
        val closing = loop.createWindow(WindowAttributes(title = "closing"))
        val other = loop.createWindow(WindowAttributes(title = "other"))
        val delivered = mutableListOf<Pair<WindowId, WindowEvent>>()
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                delivered += windowId to event
                if (windowId == other.id && event == WindowEvent.Focused(true)) closing.close()
            }
        }

        loop.enqueueWindowEvent(other.id, WindowEvent.Focused(true))
        loop.enqueueWindowEvent(closing.id, WindowEvent.Focused(true))

        dispatchX11Iteration(loop, handler, StartCause.Poll)
        dispatchX11Iteration(loop, handler, StartCause.Poll)

        assertEquals(
            listOf(
                other.id to WindowEvent.Focused(true),
                closing.id to WindowEvent.Destroyed,
            ),
            delivered,
        )
    }

    @Test
    fun `close waits for an already claimed non terminal callback`() {
        val loop = testLoop(RecordingX11Wakeup(), FakeX11NativeAdapter())
        val window = loop.createWindow(WindowAttributes(title = "claimed-callback"))
        val callbackClaimed = CountDownLatch(1)
        val closeBlockedAtAdmission = CountDownLatch(1)
        val closeReturned = CountDownLatch(1)
        val events = mutableListOf<WindowEvent>()
        loop.onCloseAdmissionBlockedForTest = closeBlockedAtAdmission::countDown
        val closer = Thread({
            assertTrue(callbackClaimed.await(CONCURRENCY_GUARD_SECONDS, TimeUnit.SECONDS))
            window.close()
            closeReturned.countDown()
        }, "x11-close-after-claim")
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                events += event
                if (event == WindowEvent.Focused(true)) {
                    callbackClaimed.countDown()
                    assertTrue(closeBlockedAtAdmission.await(CONCURRENCY_GUARD_SECONDS, TimeUnit.SECONDS))
                    assertEquals(1L, closeReturned.count)
                }
            }
        }

        loop.enqueueWindowEvent(window.id, WindowEvent.Focused(true))
        closer.start()
        dispatchX11Iteration(loop, handler, StartCause.Poll)
        closer.join(TimeUnit.SECONDS.toMillis(CONCURRENCY_GUARD_SECONDS))
        assertFalse(closer.isAlive)
        assertEquals(0L, closeReturned.count)
        dispatchX11Iteration(loop, handler, StartCause.Poll)

        assertEquals(listOf(WindowEvent.Focused(true), WindowEvent.Destroyed), events)
    }

    @Test
    fun `destroy notify after public tombstone cancels native close and delivers destroyed once`() {
        val native = FakeX11NativeAdapter()
        val loop = testLoop(RecordingX11Wakeup(), native)
        val window = loop.createWindow(WindowAttributes(title = "destroy-notify"))
        val events = mutableListOf<WindowEvent>()

        window.close()
        assertTrue(loop.nativeWindowDestroyed(window.id))
        dispatchX11Iteration(loop, recordingHandler(events = events), StartCause.Poll)
        dispatchX11Iteration(loop, recordingHandler(events = events), StartCause.Poll)

        assertTrue(native.trace.isEmpty())
        assertEquals(listOf<WindowEvent>(WindowEvent.Destroyed), events)
    }

    @Test
    fun `failed close wake retains the tombstone and reports it at the loop boundary`() {
        val wakeup = ScriptedX11Wakeup(results = ArrayDeque(listOf(false, true)))
        val native = FakeX11NativeAdapter()
        val loop = testLoop(wakeup, native)
        val window = loop.createWindow(WindowAttributes(title = "retry-false"))
        val events = mutableListOf<WindowEvent>()

        window.close()
        window.close()
        assertFalse(loop.windows.containsKey(window.id.value))
        assertFalse(loop.enqueueWindowEvent(window.id, WindowEvent.Focused(true)))
        val thrown = assertFailsWith<IllegalStateException> {
            dispatchX11Iteration(loop, recordingHandler(events = events), StartCause.WaitCancelled())
        }

        assertTrue(thrown.message.orEmpty().contains("X11 close wake failed"))
        assertEquals(1, wakeup.signalCalls)
        assertEquals(listOf("destroy-41", "flush"), native.trace)
        assertEquals(listOf<WindowEvent>(WindowEvent.Destroyed), events)
    }

    @Test
    fun `throwing close wake retains the tombstone and reports its cause at the loop boundary`() {
        val wakeFailure = IllegalStateException("signal")
        val wakeup = ScriptedX11Wakeup(
            failures = ArrayDeque(listOf(wakeFailure)),
            results = ArrayDeque(listOf(true)),
        )
        val native = FakeX11NativeAdapter()
        val loop = testLoop(wakeup, native)
        val window = loop.createWindow(WindowAttributes(title = "retry-throw"))
        val events = mutableListOf<WindowEvent>()

        window.close()
        window.close()
        assertFalse(loop.windows.containsKey(window.id.value))
        val thrown = assertFailsWith<IllegalStateException> {
            dispatchX11Iteration(loop, recordingHandler(events = events), StartCause.WaitCancelled())
        }

        assertSame(wakeFailure, thrown.cause)
        assertEquals(1, wakeup.signalCalls)
        assertEquals(listOf("destroy-41", "flush"), native.trace)
        assertEquals(listOf<WindowEvent>(WindowEvent.Destroyed), events)
    }

    @Test
    fun `old tombstone is delivered when XID is reused before close batch drain completes`() {
        lateinit var loop: X11EventLoop
        var replacement: org.graphiks.kadre.core.Window? = null
        val native = FakeX11NativeAdapter(
            windowIds = ArrayDeque(listOf(41L, 41L)),
            onDestroy = { replacement = loop.createWindow(WindowAttributes(title = "replacement")) },
        )
        loop = testLoop(RecordingX11Wakeup(), native)
        val old = loop.createWindow(WindowAttributes(title = "old"))
        val delivered = mutableListOf<Pair<WindowId, WindowEvent>>()
        old.close()

        dispatchX11Iteration(loop, object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                delivered += windowId to event
            }
        }, StartCause.WaitCancelled())

        assertEquals(old.id, replacement?.id)
        assertTrue(loop.windows[old.id.value] === replacement)
        assertEquals<List<Pair<WindowId, WindowEvent>>>(
            listOf(old.id to WindowEvent.Destroyed),
            delivered,
        )
    }

    @Test
    fun `old window handle cannot publish to replacement that reuses its XID`() {
        lateinit var loop: X11EventLoop
        var replacement: org.graphiks.kadre.core.Window? = null
        val wakeup = RecordingX11Wakeup()
        val native = FakeX11NativeAdapter(
            windowIds = ArrayDeque(listOf(41L, 41L)),
            onDestroy = {
                if (replacement == null) {
                    replacement = loop.createWindow(WindowAttributes(title = "replacement"))
                }
            },
        )
        loop = testLoop(wakeup, native)
        val old = loop.createWindow(WindowAttributes(title = "old"))
        val initialEvents = mutableListOf<WindowEvent>()
        old.close()
        dispatchX11Iteration(loop, recordingHandler(events = initialEvents), StartCause.WaitCancelled())
        val replacementWindow = requireNotNull(replacement)
        val baselineSignals = wakeup.signalCalls
        val baselineTrace = native.trace.toList()
        val staleEvents = mutableListOf<WindowEvent>()

        old.close()
        old.requestRedraw()
        dispatchX11Iteration(loop, recordingHandler(events = staleEvents), StartCause.Poll)

        assertEquals(
            Triple(baselineSignals, baselineTrace, true) to emptyList<WindowEvent>(),
            Triple(
                wakeup.signalCalls,
                native.trace.toList(),
                loop.windows[old.id.value] === replacementWindow,
            ) to staleEvents,
        )
    }

    @Test
    fun `close batch delivers every tombstone and aggregates native failures`() {
        val primary = IllegalStateException("destroy first")
        val secondary = IllegalArgumentException("flush second")
        val native = FakeX11NativeAdapter(
            destroyFailures = mapOf(41L to primary),
            flushFailures = mapOf(2 to secondary),
        )
        val loop = testLoop(RecordingX11Wakeup(), native)
        val first = loop.createWindow(WindowAttributes(title = "first"))
        val second = loop.createWindow(WindowAttributes(title = "second"))
        val delivered = mutableListOf<Pair<WindowId, WindowEvent>>()
        first.close()
        second.close()

        val thrown = assertFailsWith<IllegalStateException> {
            dispatchX11Iteration(loop, object : ApplicationHandler {
                override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
                override fun windowEvent(
                    eventLoop: ActiveEventLoop,
                    windowId: WindowId,
                    event: WindowEvent,
                ) {
                    delivered += windowId to event
                }
            }, StartCause.WaitCancelled())
        }

        assertSame(primary, thrown)
        assertEquals(listOf(secondary), thrown.suppressed.toList())
        assertEquals<List<Pair<WindowId, WindowEvent>>>(
            listOf(first.id to WindowEvent.Destroyed, second.id to WindowEvent.Destroyed),
            delivered,
        )
        assertEquals(listOf("destroy-41", "flush", "destroy-42", "flush"), native.trace)
    }

    @Test
    fun `resource release failure still destroys flushes and emits tombstone before propagation`() {
        val releaseFailure = IllegalStateException("release")
        val lease = X11ImeLease { throw releaseFailure }.also(X11ImeLease::markAcquired)
        val native = FakeX11NativeAdapter(imeLeases = ArrayDeque(listOf(lease)))
        val loop = testLoop(RecordingX11Wakeup(), native)
        val window = loop.createWindow(WindowAttributes(title = "release-failure"))
        val events = mutableListOf<WindowEvent>()
        window.close()

        val thrown = assertFailsWith<IllegalStateException> {
            dispatchX11Iteration(
                loop,
                recordingHandler(events = events),
                StartCause.WaitCancelled(),
            )
        }

        assertSame(releaseFailure, thrown)
        assertEquals(listOf("destroy-41", "flush"), native.trace)
        assertEquals(listOf<WindowEvent>(WindowEvent.Destroyed), events)
    }

    @Test
    fun `native destroy defers resource failure until after tombstone callback`() {
        val releaseFailure = IllegalStateException("native release")
        val lease = X11ImeLease { throw releaseFailure }.also(X11ImeLease::markAcquired)
        val loop = testLoop(
            RecordingX11Wakeup(),
            FakeX11NativeAdapter(imeLeases = ArrayDeque(listOf(lease))),
        )
        val window = loop.createWindow(WindowAttributes(title = "native-release-failure"))
        val events = mutableListOf<WindowEvent>()

        assertTrue(loop.nativeWindowDestroyed(window.id))
        val thrown = assertFailsWith<IllegalStateException> {
            dispatchX11Iteration(loop, recordingHandler(events = events), StartCause.Poll)
        }

        assertSame(releaseFailure, thrown)
        assertEquals(listOf<WindowEvent>(WindowEvent.Destroyed), events)
    }

    @Test
    fun `close racing after detach publishes no stale command`() {
        val destroyEntered = CountDownLatch(1)
        val allowDestroy = CountDownLatch(1)
        val loopExecutor = Executors.newSingleThreadExecutor { task -> Thread(task, "x11-loop-owner") }
        val callerExecutor = Executors.newSingleThreadExecutor { task -> Thread(task, "x11-close-racer") }
        try {
            val context = loopExecutor.submit(java.util.concurrent.Callable {
                val wakeup = RecordingX11Wakeup()
                val native = FakeX11NativeAdapter(
                    onDestroy = {
                        destroyEntered.countDown()
                        assertTrue(
                            allowDestroy.await(CONCURRENCY_GUARD_SECONDS, TimeUnit.SECONDS),
                            "timed out waiting to release native destroy",
                        )
                    },
                )
                val loop = testLoop(wakeup, native)
                val window = loop.createWindow(WindowAttributes(title = "race"))
                Triple(loop, window, wakeup)
            }).get(CONCURRENCY_GUARD_SECONDS, TimeUnit.SECONDS)
            val (loop, window, wakeup) = context
            window.close()
            val destroy = loopExecutor.submit(java.util.concurrent.Callable {
                val events = mutableListOf<WindowEvent>()
                dispatchX11Iteration(loop, recordingHandler(events = events), StartCause.WaitCancelled())
                events
            })
            assertTrue(
                destroyEntered.await(CONCURRENCY_GUARD_SECONDS, TimeUnit.SECONDS),
                "timed out waiting for native destroy to enter",
            )

            callerExecutor.submit { window.close() }.get(CONCURRENCY_GUARD_SECONDS, TimeUnit.SECONDS)
            assertEquals(1, wakeup.signalCalls)

            allowDestroy.countDown()
            assertEquals(
                listOf<WindowEvent>(WindowEvent.Destroyed),
                destroy.get(CONCURRENCY_GUARD_SECONDS, TimeUnit.SECONDS),
            )
        } finally {
            allowDestroy.countDown()
            callerExecutor.shutdownNow()
            loopExecutor.shutdownNow()
        }
    }

    @Test
    fun `shutdown destroys surfaces closes every window suspends then closes display`() {
        val native = FakeX11NativeAdapter()
        val loop = testLoop(RecordingX11Wakeup(), native)
        val first = loop.createWindow(WindowAttributes(title = "first"))
        loop.createWindow(WindowAttributes(title = "second"))
        val trace = mutableListOf<String>()
        native.traceSink = trace
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) = Unit

            override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                trace += "destroySurfaces"
                first.close()
            }

            override fun suspended(eventLoop: ActiveEventLoop) {
                assertTrue(loop.windows.isEmpty())
                trace += "suspended"
            }
        }

        shutdownX11Lifecycle(loop, handler)

        assertEquals(
            listOf(
                "destroySurfaces",
                "destroy-41", "flush",
                "destroy-42", "flush",
                "suspended",
                "closeDisplay",
            ),
            trace,
        )
    }

    @Test
    fun `shutdown still closes windows suspends and closes display after callback failure`() {
        val native = FakeX11NativeAdapter()
        val loop = testLoop(RecordingX11Wakeup(), native)
        loop.createWindow(WindowAttributes(title = "remaining"))
        val trace = mutableListOf<String>()
        val callbackFailure = IllegalStateException("destroy surfaces")
        native.traceSink = trace
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) = Unit

            override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                trace += "destroySurfaces"
                throw callbackFailure
            }

            override fun suspended(eventLoop: ActiveEventLoop) {
                trace += "suspended"
            }
        }

        val thrown = assertFailsWith<IllegalStateException> {
            shutdownX11Lifecycle(loop, handler)
        }

        assertSame(callbackFailure, thrown)
        assertEquals(
            listOf("destroySurfaces", "destroy-41", "flush", "suspended", "closeDisplay"),
            trace,
        )
        assertTrue(loop.windows.isEmpty())
    }

    @Test
    fun `events for unknown and closed XIDs are discarded`() {
        val loop = testLoop(RecordingX11Wakeup(), FakeX11NativeAdapter())
        val window = loop.createWindow(WindowAttributes(title = "known"))
        val events = mutableListOf<WindowEvent>()

        assertFalse(loop.enqueueWindowEvent(WindowId(999L), WindowEvent.Focused(true)))
        window.close()
        dispatchX11Iteration(loop, recordingHandler(events = events), StartCause.WaitCancelled())
        assertFalse(loop.enqueueWindowEvent(window.id, WindowEvent.Focused(false)))
        dispatchX11Iteration(loop, recordingHandler(events = events), StartCause.Poll)

        assertEquals(listOf<WindowEvent>(WindowEvent.Destroyed), events)
    }

    private fun recordingHandler(
        events: MutableList<WindowEvent> = mutableListOf(),
        trace: MutableList<String> = mutableListOf(),
    ): ApplicationHandler = object : ApplicationHandler {
        override fun resumed(eventLoop: ActiveEventLoop) {
            trace += "resumed"
        }

        override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
            trace += "newEvents-$startCause"
        }

        override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
            trace += "canCreateSurfaces"
        }

        override fun windowEvent(
            eventLoop: ActiveEventLoop,
            windowId: WindowId,
            event: WindowEvent,
        ) {
            events += event
            trace += "event-${event::class.simpleName}"
        }

        override fun aboutToWait(eventLoop: ActiveEventLoop) {
            trace += "aboutToWait"
        }
    }

    private fun testLoop(
        wakeup: PosixWakeup,
        native: X11NativeAdapter,
    ): X11EventLoop = X11EventLoop(
        displayPtr = 1L,
        screen = 0,
        wakeup = wakeup,
        nativeAdapter = native,
    )
}

private const val CONCURRENCY_GUARD_SECONDS = 10L

private class RecordingX11Wakeup : PosixWakeup {
    override val readFd: Int = 7
    var signalCalls: Int = 0
        private set

    override fun signal(): Boolean {
        signalCalls += 1
        return true
    }

    override fun drain(): Boolean = true

    override fun close() = Unit
}

private class ScriptedX11Wakeup(
    private val results: ArrayDeque<Boolean> = ArrayDeque(),
    private val failures: ArrayDeque<Throwable> = ArrayDeque(),
) : PosixWakeup {
    override val readFd: Int = 8
    var signalCalls = 0
        private set

    override fun signal(): Boolean {
        signalCalls += 1
        failures.removeFirstOrNull()?.let { throw it }
        return results.removeFirstOrNull() ?: true
    }

    override fun drain(): Boolean = true
    override fun close() = Unit
}

private class ContractPumpOperations(
    private val trace: MutableList<String>,
    private val onDispatch: () -> Unit,
) : X11PumpOperations {
    private var pending = 0

    fun enqueueEvent() {
        pending += 1
    }

    override fun pendingCount(): Int = pending

    override fun dispatchNext() {
        pending -= 1
        trace += "native-dispatch"
        onDispatch()
    }

    override fun flush() = Unit
}

private class FakeX11NativeAdapter(
    private val onDestroy: (Long) -> Unit = {},
    private val windowIds: ArrayDeque<Long> = ArrayDeque(),
    private val destroyFailures: Map<Long, Throwable> = emptyMap(),
    private val flushFailures: Map<Int, Throwable> = emptyMap(),
    private val imeLeases: ArrayDeque<X11ImeLease> = ArrayDeque(),
) : X11NativeAdapter {
    private var nextXid = 41L
    private var flushCalls = 0
    val trace = mutableListOf<String>()
    var traceSink: MutableList<String> = trace

    override fun createWindow(
        loop: X11EventLoop,
        attributes: WindowAttributes,
    ): X11Window = X11Window(
        displayPtr = loop.displayPtr,
        screen = loop.screen,
        xWindowId = windowIds.removeFirstOrNull() ?: nextXid++,
        attrs = attributes,
        owner = loop,
        initialScaleFactor = 1.0,
        imeLease = imeLeases.removeFirstOrNull() ?: X11ImeLease(X11Window::releaseXIM),
    )

    override fun destroyWindow(displayPtr: Long, windowId: Long) {
        onDestroy(windowId)
        traceSink += "destroy-$windowId"
        destroyFailures[windowId]?.let { throw it }
    }

    override fun flush(displayPtr: Long) {
        flushCalls += 1
        traceSink += "flush"
        flushFailures[flushCalls]?.let { throw it }
    }

    override fun closeDisplay(displayPtr: Long) {
        traceSink += "closeDisplay"
    }
}
