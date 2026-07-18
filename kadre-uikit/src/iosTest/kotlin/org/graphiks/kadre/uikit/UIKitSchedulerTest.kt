package org.graphiks.kadre.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.WindowId

class UIKitSchedulerTest {
    @Test
    fun waitWithoutWorkKeepsTheLoopDisplayLinkStopped() {
        val harness = SchedulerHarness()

        harness.registerWindow()

        assertFalse(harness.operations.displayLinkActive)
        assertEquals(0, harness.operations.displayLinkStarts)
    }

    @Test
    fun oneRedrawStartsOneFrameDispatchesOneIterationAndStops() {
        val harness = SchedulerHarness()
        harness.registerWindow()

        assertTrue(harness.scheduler.requestRedraw(harness.windowId))
        assertFalse(harness.scheduler.requestRedraw(harness.windowId))
        assertTrue(harness.operations.displayLinkActive)

        harness.operations.fireFrame()

        assertEquals(
            listOf(
                "newEvents:${StartCause.WaitCancelled()}",
                "redraw:${harness.windowId.value}",
                "aboutToWait",
            ),
            harness.trace,
        )
        assertFalse(harness.operations.displayLinkActive)
        assertEquals(1, harness.operations.displayLinkStarts)
        assertEquals(1, harness.operations.displayLinkStops)
    }

    @Test
    fun pollKeepsTheDisplayLinkActiveUntilControlFlowSwitchesToWait() {
        val harness = SchedulerHarness(controlFlow = ControlFlow.Poll)
        harness.registerWindow()

        harness.scheduler.controlFlowChanged()
        assertTrue(harness.operations.displayLinkActive)

        harness.operations.fireFrame()

        assertEquals(
            listOf(
                "newEvents:${StartCause.Poll}",
                "aboutToWait",
            ),
            harness.trace,
        )
        assertTrue(harness.operations.displayLinkActive)

        harness.controlFlow = ControlFlow.Wait
        harness.scheduler.controlFlowChanged()

        assertFalse(harness.operations.displayLinkActive)
        assertEquals(1, harness.operations.displayLinkStarts)
        assertEquals(1, harness.operations.displayLinkStops)
    }

    @Test
    fun threeProxyWakeCyclesRunNewEventsQueuedWorkAndAboutToWaitInOrder() {
        val harness = SchedulerHarness()
        val mainQueue = FakeMainQueue()
        harness.registerWindow()
        harness.scheduler.requestRedraw(harness.windowId)
        harness.operations.fireFrame()
        harness.trace.clear()
        harness.onNewEvents = {
            harness.scheduler.requestRedraw(harness.windowId)
        }
        val proxy = UIKitEventLoopProxy(
            scheduler = harness.scheduler,
            dispatchMain = mainQueue::dispatch,
        )

        repeat(3) { cycle ->
            proxy.wakeUp()
            assertEquals(1, mainQueue.pendingCount, "cycle=${cycle + 1}")

            mainQueue.runNext()

            assertEquals(0, mainQueue.pendingCount, "cycle=${cycle + 1}")
        }

        val expectedTrace = List(3) {
            listOf(
                "newEvents:${StartCause.WaitCancelled()}",
                "redraw:${harness.windowId.value}",
                "aboutToWait",
            )
        }.flatten()
        assertEquals(expectedTrace, harness.trace)

        harness.operations.fireCapturedFrame()

        assertEquals(expectedTrace, harness.trace)
    }

    @Test
    fun earlierEventCancelsWaitUntilAndItsStaleDeadlineCallbackDoesNothing() {
        val deadline = 2_000L
        val harness = SchedulerHarness(controlFlow = ControlFlow.WaitUntil(deadline))

        harness.registerWindow()

        val staleTimer = harness.operations.timers.single()
        assertEquals(deadline, staleTimer.deadlineMillis)

        harness.scheduler.wakeExternal()

        assertEquals(
            listOf(
                "newEvents:${StartCause.WaitCancelled(deadline)}",
                "aboutToWait",
            ),
            harness.trace,
        )
        val currentTimer = harness.operations.timers.last()
        assertEquals(2, harness.operations.timers.size)
        assertEquals(deadline, currentTimer.deadlineMillis)

        staleTimer.fire()

        assertEquals(2, harness.operations.timers.size)
        assertEquals(
            listOf(
                "newEvents:${StartCause.WaitCancelled(deadline)}",
                "aboutToWait",
            ),
            harness.trace,
        )
    }

    @Test
    fun deadlineFireResumesAtTheObservedEpochTimeAndDoesNotRearmTheSameDeadline() {
        val deadline = 2_000L
        val now = 2_007L
        val harness = SchedulerHarness(controlFlow = ControlFlow.WaitUntil(deadline))
        harness.registerWindow()
        val timer = harness.operations.timers.single()

        harness.operations.nowMillis = now
        timer.fire()

        assertEquals(
            listOf(
                "newEvents:${StartCause.ResumeTimeReached(deadline, now)}",
                "aboutToWait",
            ),
            harness.trace,
        )
        assertEquals(1, harness.operations.timers.size)
        assertFalse(harness.operations.displayLinkActive)
    }

    @Test
    fun closeCancelsPendingRedrawAndDeadlineAndStaleCallbacksNeverTargetTheWindow() {
        val deadline = 2_000L
        val harness = SchedulerHarness(controlFlow = ControlFlow.WaitUntil(deadline))
        harness.registerWindow()
        val staleTimer = harness.operations.timers.single()
        assertTrue(harness.scheduler.requestRedraw(harness.windowId))

        assertTrue(harness.scheduler.closeWindow(harness.windowId))
        assertFalse(harness.scheduler.closeWindow(harness.windowId))
        assertFalse(harness.scheduler.requestRedraw(harness.windowId))
        assertFalse(harness.operations.displayLinkActive)

        harness.operations.nowMillis = deadline + 7L
        staleTimer.fire()
        harness.operations.fireCapturedFrame()

        assertEquals(emptyList(), harness.trace)
        assertEquals(1, harness.operations.timers.size)
    }

    @Test
    fun handlerFailureStopsSchedulingAndClearsTheInterruptedIteration() {
        val harness = SchedulerHarness()
        val failure = IllegalStateException("newEvents")
        harness.registerWindow()
        assertTrue(harness.scheduler.requestRedraw(harness.windowId))
        harness.onNewEvents = { throw failure }

        val thrown = assertFailsWith<IllegalStateException> {
            harness.operations.fireFrame()
        }

        assertSame(failure, thrown)
        assertFalse(harness.operations.displayLinkActive)

        harness.onNewEvents = {}
        harness.operations.fireCapturedFrame()
        assertEquals(listOf("newEvents:${StartCause.WaitCancelled()}"), harness.trace)
    }

    @Test
    fun reentrantCloseDuringRedrawPreventsDispatchToTheClosedTarget() {
        val secondWindowId = WindowId(42L)
        val harness = SchedulerHarness()
        harness.registerWindow()
        harness.registerWindow(secondWindowId)
        harness.onRedraw = { id ->
            if (id == harness.windowId) harness.scheduler.closeWindow(secondWindowId)
        }
        assertTrue(harness.scheduler.requestRedraw(harness.windowId))
        assertTrue(harness.scheduler.requestRedraw(secondWindowId))

        harness.operations.fireFrame()

        assertEquals(
            listOf(
                "newEvents:${StartCause.WaitCancelled()}",
                "redraw:${harness.windowId.value}",
                "aboutToWait",
            ),
            harness.trace,
        )
    }

    @Test
    fun repeatedWaitUntilArmingKeepsOneCurrentTimerGeneration() {
        val deadline = 2_000L
        val harness = SchedulerHarness(controlFlow = ControlFlow.WaitUntil(deadline))

        harness.registerWindow()
        harness.registerWindow(WindowId(42L))
        repeat(3) { harness.scheduler.controlFlowChanged() }

        assertEquals(1, harness.operations.timers.size)
        assertEquals(deadline, harness.operations.timers.single().deadlineMillis)
    }

    @Test
    fun redrawQueuedFromAboutToWaitRearmsTheDeadlineAfterItsFrame() {
        val deadline = 2_000L
        val harness = SchedulerHarness(controlFlow = ControlFlow.WaitUntil(deadline))
        harness.registerWindow()
        val staleTimer = harness.operations.timers.single()
        var requestFromAboutToWait = true
        harness.onAboutToWait = {
            if (requestFromAboutToWait) {
                requestFromAboutToWait = false
                harness.scheduler.requestRedraw(harness.windowId)
            }
        }

        harness.scheduler.wakeExternal()
        assertTrue(harness.operations.displayLinkActive)
        harness.operations.fireFrame()

        assertFalse(harness.operations.displayLinkActive)
        assertEquals(2, harness.operations.timers.size)
        assertEquals(deadline, harness.operations.timers.last().deadlineMillis)

        val traceBeforeStaleTimer = harness.trace.toList()
        staleTimer.fire()
        assertEquals(traceBeforeStaleTimer, harness.trace)
    }

    @Test
    fun externalWakeInPollModeUsesPollStartCause() {
        val harness = SchedulerHarness(controlFlow = ControlFlow.Poll)
        harness.registerWindow()

        harness.scheduler.wakeExternal()

        assertEquals(
            listOf(
                "newEvents:${StartCause.Poll}",
                "aboutToWait",
            ),
            harness.trace,
        )
        assertTrue(harness.operations.displayLinkActive)
    }

    @Test
    fun closeIsAnEarlierEventAndRearmsWaitUntilForTheRemainingWindow() {
        val deadline = 2_000L
        val secondWindowId = WindowId(42L)
        val harness = SchedulerHarness(controlFlow = ControlFlow.WaitUntil(deadline))
        harness.registerWindow()
        harness.registerWindow(secondWindowId)
        val staleTimer = harness.operations.timers.single()

        assertTrue(harness.scheduler.closeWindow(harness.windowId))
        assertTrue(harness.operations.displayLinkActive)
        harness.operations.fireFrame()

        assertEquals(
            listOf(
                "newEvents:${StartCause.WaitCancelled(deadline)}",
                "aboutToWait",
            ),
            harness.trace,
        )
        assertFalse(harness.operations.displayLinkActive)
        assertEquals(2, harness.operations.timers.size)

        val traceBeforeStaleTimer = harness.trace.toList()
        staleTimer.fire()
        assertEquals(traceBeforeStaleTimer, harness.trace)
    }
}

private class SchedulerHarness(
    var controlFlow: ControlFlow = ControlFlow.Wait,
) {
    val windowId = WindowId(41L)
    val operations = FakeUIKitSchedulerOperations()
    val trace = mutableListOf<String>()
    var onNewEvents: (StartCause) -> Unit = {}
    var onRedraw: (WindowId) -> Unit = {}
    var onAboutToWait: () -> Unit = {}
    val scheduler = UIKitScheduler(
        operations = operations,
        controlFlow = { controlFlow },
        newEvents = { cause ->
            trace += "newEvents:$cause"
            onNewEvents(cause)
        },
        redraw = { id ->
            trace += "redraw:${id.value}"
            onRedraw(id)
        },
        aboutToWait = {
            trace += "aboutToWait"
            onAboutToWait()
        },
    )

    fun registerWindow(id: WindowId = windowId) {
        scheduler.registerWindow(id)
    }
}

private class FakeUIKitSchedulerOperations : UIKitSchedulerOperations {
    var nowMillis = 1_000L
    var displayLinkActive = false
    var displayLinkStarts = 0
    var displayLinkStops = 0
    private var frame: (() -> Unit)? = null
    val timers = mutableListOf<FakeTimer>()

    override fun nowMillis(): Long = nowMillis

    override fun startDisplayLink(onFrame: () -> Unit) {
        frame = onFrame
        if (!displayLinkActive) {
            displayLinkActive = true
            displayLinkStarts += 1
        }
    }

    override fun stopDisplayLink() {
        if (displayLinkActive) {
            displayLinkActive = false
            displayLinkStops += 1
        }
    }

    override fun disposeDisplayLink() {
        stopDisplayLink()
        frame = null
    }

    override fun scheduleDeadline(deadlineMillis: Long, onDeadline: () -> Unit) {
        timers += FakeTimer(deadlineMillis, onDeadline)
    }

    fun fireFrame() {
        check(displayLinkActive) { "display link is stopped" }
        checkNotNull(frame).invoke()
    }

    fun fireCapturedFrame() {
        checkNotNull(frame).invoke()
    }
}

private data class FakeTimer(
    val deadlineMillis: Long,
    val fire: () -> Unit,
)

private class FakeMainQueue {
    private val pending = mutableListOf<() -> Unit>()

    val pendingCount: Int get() = pending.size

    fun dispatch(block: () -> Unit) {
        pending += block
    }

    fun runNext() {
        pending.removeAt(0).invoke()
    }
}
