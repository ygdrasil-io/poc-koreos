package org.graphiks.kadre.uikit

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.WindowId
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.Foundation.NSSelectorFromString
import platform.Foundation.timeIntervalSince1970
import platform.QuartzCore.CADisplayLink
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSObject
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

/** Native operations used by [UIKitScheduler] and replaceable by deterministic fakes. */
internal interface UIKitSchedulerOperations {
    fun nowMillis(): Long

    fun startDisplayLink(onFrame: () -> Unit)

    fun stopDisplayLink()

    fun disposeDisplayLink()

    fun scheduleDeadline(deadlineMillis: Long, onDeadline: () -> Unit)
}

/** Production UIKit operations; one fresh display link is owned per active generation. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class UIKitNativeSchedulerOperations : UIKitSchedulerOperations {
    private var displayLink: CADisplayLink? = null
    private var displayLinkTarget: UIKitSchedulerDisplayLinkTarget? = null

    override fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1_000.0).toLong()

    override fun startDisplayLink(onFrame: () -> Unit) {
        if (displayLink != null) return
        val target = UIKitSchedulerDisplayLinkTarget(onFrame)
        val link = CADisplayLink.displayLinkWithTarget(
            target = target,
            selector = NSSelectorFromString("handleDisplayLink"),
        )
        displayLinkTarget = target
        displayLink = link
        try {
            link.addToRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)
        } catch (failure: Throwable) {
            displayLink = null
            displayLinkTarget = null
            link.invalidate()
            throw failure
        }
    }

    override fun stopDisplayLink() {
        val link = displayLink ?: return
        displayLink = null
        displayLinkTarget = null
        link.invalidate()
    }

    override fun disposeDisplayLink() = stopDisplayLink()

    override fun scheduleDeadline(deadlineMillis: Long, onDeadline: () -> Unit) {
        dispatch_after(
            dispatch_time(DISPATCH_TIME_NOW, deadlineDelayNanos(deadlineMillis, nowMillis())),
            dispatch_get_main_queue(),
        ) {
            onDeadline()
        }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class UIKitSchedulerDisplayLinkTarget(
    private val onFrame: () -> Unit,
) : NSObject() {
    @ObjCAction
    fun handleDisplayLink() = onFrame()
}

private const val NANOS_PER_MILLISECOND = 1_000_000L

/** Converts an epoch deadline to a non-negative saturated relative delay. */
internal fun deadlineDelayNanos(deadlineMillis: Long, nowMillis: Long): Long {
    if (deadlineMillis <= nowMillis) return 0L
    val delayMillis = if (
        nowMillis < 0L && deadlineMillis > Long.MAX_VALUE + nowMillis
    ) {
        Long.MAX_VALUE
    } else {
        deadlineMillis - nowMillis
    }
    return if (delayMillis > Long.MAX_VALUE / NANOS_PER_MILLISECOND) {
        Long.MAX_VALUE
    } else {
        delayMillis * NANOS_PER_MILLISECOND
    }
}

/** Demand-driven, loop-level scheduler for UIKit event-loop iterations. */
internal class UIKitScheduler(
    private val operations: UIKitSchedulerOperations,
    private val controlFlow: () -> ControlFlow,
    private val newEvents: (StartCause) -> Unit,
    private val redraw: (WindowId) -> Unit,
    private val aboutToWait: () -> Unit,
) {
    private val state = UIKitLoopState(operations::nowMillis)
    private var iterationRunning = false
    private var currentIterationAcceptsRedraws = false
    private var nextFrameGeneration = 0L
    private var activeFrameGeneration: Long? = null

    fun registerWindow(windowId: WindowId) {
        val needsScheduling = state.registerWindow(windowId)
        if (!needsScheduling) return
        try {
            controlFlowChanged()
        } catch (failure: Throwable) {
            state.rollbackWindowRegistration(windowId)
            throw failure
        }
    }

    fun requestRedraw(windowId: WindowId): Boolean {
        val wakeIteration = !currentIterationAcceptsRedraws
        val queued = state.requestRedraw(windowId, controlFlow(), wakeIteration)
        if (queued && wakeIteration) startDisplayLink()
        return queued
    }

    fun wakeExternal() {
        if (state.wakeExternal(controlFlow())) runIteration()
    }

    fun closeWindow(windowId: WindowId): Boolean {
        if (!state.closeWindow(windowId, controlFlow())) return false
        if (!iterationRunning) armOrStop()
        return true
    }

    fun exit() {
        if (!state.exit()) return
        activeFrameGeneration = null
        operations.disposeDisplayLink()
    }

    fun controlFlowChanged() {
        if (!state.isTerminal() && !iterationRunning) armOrStop()
    }

    private fun onFrame(generation: Long) {
        if (generation == activeFrameGeneration) runIteration()
    }

    private fun runIteration() {
        val cause = state.beginIteration(controlFlow()) ?: run {
            stopDisplayLink()
            return
        }

        iterationRunning = true
        currentIterationAcceptsRedraws = true
        try {
            newEvents(cause)
            currentIterationAcceptsRedraws = false
            state.takeRedraws().forEach { windowId ->
                if (state.isLive(windowId)) redraw(windowId)
            }
            aboutToWait()
        } catch (failure: Throwable) {
            state.abortIteration()
            try {
                stopDisplayLink()
            } catch (stopFailure: Throwable) {
                if (stopFailure !== failure) failure.addSuppressed(stopFailure)
            }
            throw failure
        } finally {
            currentIterationAcceptsRedraws = false
            iterationRunning = false
        }

        armOrStop()
    }

    private fun armOrStop() {
        if (state.isTerminal()) return
        if (!state.hasLiveWindows()) {
            state.cancelArmedDeadline()
            stopDisplayLink()
            return
        }

        val currentControlFlow = controlFlow()
        if (currentControlFlow == ControlFlow.Poll) {
            state.arm(currentControlFlow)
            startDisplayLink()
        } else if (state.hasPendingRedraws() || state.hasPendingIteration()) {
            state.cancelArmedDeadline()
            startDisplayLink()
        } else {
            stopDisplayLink()
            when (val decision = state.arm(currentControlFlow)) {
                UIKitTimerDecision.Cancel -> Unit
                UIKitTimerDecision.Keep -> Unit
                is UIKitTimerDecision.Arm -> scheduleDeadline(decision)
            }
        }
    }

    @OptIn(ExperimentalNativeApi::class)
    private fun scheduleDeadline(decision: UIKitTimerDecision.Arm) {
        val weakScheduler = WeakReference(this)
        operations.scheduleDeadline(decision.deadlineMillis) {
            weakScheduler.get()?.onDeadline(decision.generation)
        }
    }

    private fun onDeadline(generation: Long) {
        when (val signal = state.signalDeadline(generation)) {
            UIKitDeadlineSignal.Stale -> Unit
            UIKitDeadlineSignal.Reached -> runIteration()
            is UIKitDeadlineSignal.Early -> scheduleDeadline(signal.replacement)
        }
    }

    private fun startDisplayLink() {
        if (activeFrameGeneration != null) return
        val generation = ++nextFrameGeneration
        activeFrameGeneration = generation
        try {
            operations.startDisplayLink { onFrame(generation) }
        } catch (failure: Throwable) {
            activeFrameGeneration = null
            throw failure
        }
    }

    private fun stopDisplayLink() {
        activeFrameGeneration = null
        operations.stopDisplayLink()
    }
}
