package org.graphiks.kadre.uikit

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.WindowId

class UIKitSchedulerTest {
    @OptIn(ExperimentalNativeApi::class, NativeRuntimeApi::class)
    @Test
    fun retainedDeadlineCallbackDoesNotKeepItsSchedulerOwnerAlive() {
        val (weakScheduler, retainedTimer) = schedulerRetainedOnlyByDeadlineCallback()

        GC.collect()

        assertNull(weakScheduler.get())
        retainedTimer.fire()
    }

    @Test
    fun nativeDeadlineDelaySaturatesPastAndFarFutureBoundsWithoutOverflow() {
        assertEquals(0L, deadlineDelayNanos(deadlineMillis = Long.MIN_VALUE, nowMillis = 0L))
        assertEquals(Long.MAX_VALUE, deadlineDelayNanos(deadlineMillis = Long.MAX_VALUE, nowMillis = 0L))
    }

    @Test
    fun proxyWakeWithNoWindowsRunsAnIterationThatCanCreateAndObserveQueuedWork() {
        val harness = SchedulerHarness()
        val mainQueue = FakeMainQueue()
        val proxy = UIKitEventLoopProxy(
            scheduler = harness.scheduler,
            dispatchMain = mainQueue::dispatch,
        )
        harness.onNewEvents = {
            harness.registerWindow()
            assertTrue(harness.scheduler.requestRedraw(harness.windowId))
        }

        proxy.wakeUp()
        mainQueue.runNext()

        assertEquals(
            listOf(
                "newEvents:${StartCause.WaitCancelled()}",
                "redraw:${harness.windowId.value}",
                "aboutToWait",
            ),
            harness.trace,
        )
        assertFalse(harness.operations.displayLinkActive)
    }

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
    fun frameCapturedBeforeStopCannotConsumeWorkFromAReactivatedGeneration() {
        val harness = SchedulerHarness()
        harness.registerWindow()
        harness.scheduler.requestRedraw(harness.windowId)
        val oldFrame = harness.operations.capturedFrames.single()
        harness.operations.fireFrame()
        harness.trace.clear()

        harness.scheduler.requestRedraw(harness.windowId)
        oldFrame()

        assertEquals(emptyList(), harness.trace)
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
    }

    @Test
    fun selectorTargetRetainsItsActivationGenerationAcrossReactivation() {
        val harness = SchedulerHarness()
        harness.registerWindow()
        harness.scheduler.requestRedraw(harness.windowId)
        val oldTarget = harness.operations.capturedFrameTargets.single()
        harness.operations.fireFrame()
        harness.trace.clear()

        harness.scheduler.requestRedraw(harness.windowId)
        val currentTarget = harness.operations.capturedFrameTargets.last()
        assertNotSame(oldTarget, currentTarget)

        oldTarget.handleDisplayLink()
        assertEquals(emptyList(), harness.trace)
        assertTrue(harness.operations.displayLinkActive)

        currentTarget.handleDisplayLink()
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
    fun exitIsPermanentIdempotentAndPreventsEverySchedulingPathFromRestarting() {
        val harness = SchedulerHarness(controlFlow = ControlFlow.Poll)
        harness.registerWindow()
        assertTrue(harness.operations.displayLinkActive)

        harness.scheduler.exit()
        harness.scheduler.exit()

        assertEquals(1, harness.operations.displayLinkDisposals)
        assertFalse(harness.operations.displayLinkActive)
        assertFailsWith<IllegalStateException> {
            harness.scheduler.registerWindow(WindowId(42L))
        }
        assertFalse(harness.scheduler.requestRedraw(harness.windowId))
        harness.scheduler.wakeExternal()
        harness.scheduler.controlFlowChanged()
        assertEquals(emptyList(), harness.trace)
        assertEquals(1, harness.operations.displayLinkStarts)
    }

    @Test
    fun registrationSchedulingFailureRollsBackStateSoTheSameIdCanBeRegisteredLater() {
        val harness = SchedulerHarness(controlFlow = ControlFlow.Poll)
        val schedulingFailure = IllegalStateException("start display link")
        harness.operations.startDisplayLinkFailure = schedulingFailure

        val thrown = assertFailsWith<IllegalStateException> {
            harness.registerWindow()
        }

        assertSame(schedulingFailure, thrown)
        harness.operations.startDisplayLinkFailure = null
        harness.registerWindow()
        assertTrue(harness.operations.displayLinkActive)
    }

    @Test
    fun loopStateRejectsConcurrentDuplicateIdsButAllowsReuseAfterClose() {
        val state = UIKitLoopState { 1_000L }
        val id = WindowId(41L)
        state.registerWindow(id)

        assertFailsWith<IllegalStateException> {
            state.registerWindow(id)
        }
        assertTrue(state.closeWindow(id, ControlFlow.Wait))

        state.registerWindow(id)
        assertTrue(state.isLive(id))
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
    fun proxyRestoresItsCasFlagWhenMainQueueDispatchThrows() {
        val harness = SchedulerHarness()
        val mainQueue = FakeMainQueue()
        val dispatchFailure = IllegalStateException("dispatch")
        var failDispatch = true
        val proxy = UIKitEventLoopProxy(
            scheduler = harness.scheduler,
            dispatchMain = { block ->
                if (failDispatch) throw dispatchFailure
                mainQueue.dispatch(block)
            },
        )

        val thrown = assertFailsWith<IllegalStateException> {
            proxy.wakeUp()
        }

        assertSame(dispatchFailure, thrown)
        failDispatch = false
        proxy.wakeUp()
        assertEquals(1, mainQueue.pendingCount)
        mainQueue.runNext()
        assertEquals(
            listOf(
                "newEvents:${StartCause.WaitCancelled()}",
                "aboutToWait",
            ),
            harness.trace,
        )
    }

    @Test
    fun proxyCoalescesPendingWakesAndQueuesExactlyOneReentrantFollowUpInCallbackOrder() {
        val harness = SchedulerHarness()
        val mainQueue = FakeMainQueue()
        lateinit var proxy: UIKitEventLoopProxy
        var issueReentrantWake = true
        proxy = UIKitEventLoopProxy(
            scheduler = harness.scheduler,
            dispatchMain = mainQueue::dispatch,
        )
        harness.onNewEvents = {
            if (issueReentrantWake) {
                issueReentrantWake = false
                proxy.wakeUp()
            }
        }

        repeat(4) { proxy.wakeUp() }
        assertEquals(1, mainQueue.pendingCount)
        mainQueue.runNext()
        assertEquals(1, mainQueue.pendingCount)
        repeat(4) { proxy.wakeUp() }
        assertEquals(1, mainQueue.pendingCount)
        mainQueue.runNext()

        assertEquals(0, mainQueue.pendingCount)
        assertEquals(
            List(2) {
                listOf(
                    "newEvents:${StartCause.WaitCancelled()}",
                    "aboutToWait",
                )
            }.flatten(),
            harness.trace,
        )
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
    fun proxyRedrawAndCloseAtOrAfterDeadlineReportResumeTimeReached() {
        val deadline = 2_000L
        val observedAt = deadline + 7L

        val proxyHarness = SchedulerHarness(controlFlow = ControlFlow.WaitUntil(deadline))
        proxyHarness.registerWindow()
        proxyHarness.operations.nowMillis = observedAt
        proxyHarness.scheduler.wakeExternal()

        val redrawHarness = SchedulerHarness(controlFlow = ControlFlow.WaitUntil(deadline))
        redrawHarness.registerWindow()
        redrawHarness.operations.nowMillis = observedAt
        redrawHarness.scheduler.requestRedraw(redrawHarness.windowId)
        redrawHarness.operations.fireFrame()

        val closeHarness = SchedulerHarness(controlFlow = ControlFlow.WaitUntil(deadline))
        closeHarness.registerWindow()
        closeHarness.registerWindow(WindowId(42L))
        closeHarness.operations.nowMillis = observedAt
        closeHarness.scheduler.closeWindow(closeHarness.windowId)
        closeHarness.operations.fireFrame()

        val expectedCause = "newEvents:${StartCause.ResumeTimeReached(deadline, observedAt)}"
        assertEquals(expectedCause, proxyHarness.trace.first())
        assertEquals(expectedCause, redrawHarness.trace.first())
        assertEquals(expectedCause, closeHarness.trace.first())
    }

    @Test
    fun closeLastAtDeadlinePreservesOneResumeAcrossZeroWindowReplacement() {
        val deadline = 2_000L
        val observedAt = deadline + 7L
        val harness = SchedulerHarness(controlFlow = ControlFlow.WaitUntil(deadline))
        val mainQueue = FakeMainQueue()
        val proxy = UIKitEventLoopProxy(
            scheduler = harness.scheduler,
            dispatchMain = mainQueue::dispatch,
        )
        harness.registerWindow()
        val staleTimer = harness.operations.timers.single()
        harness.operations.nowMillis = observedAt
        assertTrue(harness.scheduler.closeWindow(harness.windowId))
        harness.onNewEvents = { harness.registerWindow(WindowId(42L)) }

        proxy.wakeUp()
        mainQueue.runNext()

        assertEquals(
            listOf(
                "newEvents:${StartCause.ResumeTimeReached(deadline, observedAt)}",
                "aboutToWait",
            ),
            harness.trace,
        )
        assertEquals(1, harness.operations.timers.size)
        assertFalse(harness.operations.displayLinkActive)

        staleTimer.fire()
        assertEquals(1, harness.operations.timers.size)
        assertEquals(1, harness.trace.count { it.startsWith("newEvents:") })
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
    fun earlyDeadlineCallbackAfterClockMovesBackwardArmsAReplacementGeneration() {
        val deadline = 2_000L
        val harness = SchedulerHarness(controlFlow = ControlFlow.WaitUntil(deadline))
        harness.registerWindow()
        val earlyTimer = harness.operations.timers.single()

        harness.operations.nowMillis = deadline - 500L
        earlyTimer.fire()

        assertEquals(2, harness.operations.timers.size)
        assertEquals(deadline, harness.operations.timers.last().deadlineMillis)
        assertEquals(emptyList(), harness.trace)

        harness.operations.nowMillis = deadline
        harness.operations.timers.last().fire()
        assertEquals(
            "newEvents:${StartCause.ResumeTimeReached(deadline, deadline)}",
            harness.trace.first(),
        )
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
    fun redrawFailurePropagatesExactlyStopsItsFrameAndPurgesReentrantWork() {
        val harness = SchedulerHarness()
        val failure = IllegalStateException("redraw")
        val stopFailure = IllegalArgumentException("stop display link")
        harness.registerWindow()
        harness.onRedraw = {
            harness.scheduler.requestRedraw(harness.windowId)
            throw failure
        }
        harness.scheduler.requestRedraw(harness.windowId)
        val interruptedFrame = harness.operations.capturedFrames.single()
        harness.operations.stopDisplayLinkFailure = stopFailure

        val thrown = assertFailsWith<IllegalStateException> {
            harness.operations.fireFrame()
        }

        assertSame(failure, thrown)
        assertEquals(listOf(stopFailure), thrown.suppressedExceptions)
        assertFalse(harness.operations.displayLinkActive)
        val traceAfterFailure = harness.trace.toList()
        interruptedFrame()
        assertEquals(traceAfterFailure, harness.trace)

        harness.operations.stopDisplayLinkFailure = null
        harness.onRedraw = {}
        harness.scheduler.requestRedraw(harness.windowId)
        harness.operations.fireFrame()
        assertEquals(2, harness.trace.count { it.startsWith("redraw:") })
    }

    @Test
    fun aboutToWaitFailurePropagatesExactlyStopsItsFrameAndPurgesReentrantWork() {
        val harness = SchedulerHarness()
        val failure = IllegalStateException("aboutToWait")
        harness.registerWindow()
        harness.onAboutToWait = {
            harness.scheduler.requestRedraw(harness.windowId)
            throw failure
        }
        harness.scheduler.requestRedraw(harness.windowId)
        val interruptedFrame = harness.operations.capturedFrames.single()

        val thrown = assertFailsWith<IllegalStateException> {
            harness.operations.fireFrame()
        }

        assertSame(failure, thrown)
        assertFalse(harness.operations.displayLinkActive)
        val traceAfterFailure = harness.trace.toList()
        interruptedFrame()
        assertEquals(traceAfterFailure, harness.trace)

        harness.onAboutToWait = {}
        harness.scheduler.wakeExternal()
        assertEquals(traceAfterFailure.size + 2, harness.trace.size)
        assertEquals("aboutToWait", harness.trace.last())
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

@OptIn(ExperimentalNativeApi::class)
private fun schedulerRetainedOnlyByDeadlineCallback(): Pair<WeakReference<UIKitScheduler>, FakeTimer> {
    val operations = FakeUIKitSchedulerOperations()
    val scheduler = UIKitScheduler(
        operations = operations,
        controlFlow = { ControlFlow.WaitUntil(2_000L) },
        newEvents = {},
        redraw = {},
        aboutToWait = {},
    )
    val weakScheduler = WeakReference(scheduler)
    scheduler.registerWindow(WindowId(41L))
    return weakScheduler to operations.timers.single()
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
    var displayLinkDisposals = 0
    var startDisplayLinkFailure: Throwable? = null
    var stopDisplayLinkFailure: Throwable? = null
    private var frame: UIKitSchedulerDisplayLinkTarget? = null
    val capturedFrames = mutableListOf<() -> Unit>()
    val capturedFrameTargets = mutableListOf<UIKitSchedulerDisplayLinkTarget>()
    val timers = mutableListOf<FakeTimer>()

    override fun nowMillis(): Long = nowMillis

    override fun startDisplayLink(onFrame: () -> Unit) {
        startDisplayLinkFailure?.let { throw it }
        if (!displayLinkActive) {
            val target = UIKitSchedulerDisplayLinkTarget(onFrame)
            frame = target
            displayLinkActive = true
            displayLinkStarts += 1
            capturedFrameTargets += target
            capturedFrames += target::handleDisplayLink
        }
    }

    override fun stopDisplayLink() {
        if (displayLinkActive) {
            displayLinkActive = false
            displayLinkStops += 1
        }
        stopDisplayLinkFailure?.let { throw it }
    }

    override fun disposeDisplayLink() {
        displayLinkDisposals += 1
        stopDisplayLink()
        frame = null
    }

    override fun scheduleDeadline(deadlineMillis: Long, onDeadline: () -> Unit) {
        timers += FakeTimer(deadlineMillis, onDeadline)
    }

    fun fireFrame() {
        check(displayLinkActive) { "display link is stopped" }
        checkNotNull(frame).handleDisplayLink()
    }

    fun fireCapturedFrame() {
        checkNotNull(frame).handleDisplayLink()
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
