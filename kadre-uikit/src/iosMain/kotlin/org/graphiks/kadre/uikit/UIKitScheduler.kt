package org.graphiks.kadre.uikit

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
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

/** Production UIKit operations; one reusable display link is owned per scheduler. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class UIKitNativeSchedulerOperations : UIKitSchedulerOperations {
    private var displayLink: CADisplayLink? = null
    private var displayLinkTarget: UIKitSchedulerDisplayLinkTarget? = null
    private var displayLinkActive = false

    override fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1_000.0).toLong()

    override fun startDisplayLink(onFrame: () -> Unit) {
        if (displayLinkActive) return
        val link = displayLink ?: run {
            val target = UIKitSchedulerDisplayLinkTarget(onFrame)
            displayLinkTarget = target
            CADisplayLink.displayLinkWithTarget(
                target = target,
                selector = NSSelectorFromString("handleDisplayLink"),
            ).also { displayLink = it }
        }
        link.addToRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)
        displayLinkActive = true
    }

    override fun stopDisplayLink() {
        if (!displayLinkActive) return
        displayLink?.removeFromRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)
        displayLinkActive = false
    }

    override fun disposeDisplayLink() {
        displayLink?.invalidate()
        displayLink = null
        displayLinkTarget = null
        displayLinkActive = false
    }

    override fun scheduleDeadline(deadlineMillis: Long, onDeadline: () -> Unit) {
        val delayMillis = (deadlineMillis - nowMillis()).coerceAtLeast(0L)
        val delayNanos = if (delayMillis > Long.MAX_VALUE / NANOS_PER_MILLISECOND) {
            Long.MAX_VALUE
        } else {
            delayMillis * NANOS_PER_MILLISECOND
        }
        dispatch_after(
            dispatch_time(DISPATCH_TIME_NOW, delayNanos),
            dispatch_get_main_queue(),
        ) {
            onDeadline()
        }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class UIKitSchedulerDisplayLinkTarget(
    private val onFrame: () -> Unit,
) : NSObject() {
    @ObjCAction
    fun handleDisplayLink() = onFrame()
}

private const val NANOS_PER_MILLISECOND = 1_000_000L

/** Demand-driven, loop-level scheduler for UIKit event-loop iterations. */
internal class UIKitScheduler(
    private val operations: UIKitSchedulerOperations,
    private val controlFlow: () -> ControlFlow,
    private val newEvents: (StartCause) -> Unit,
    private val redraw: (WindowId) -> Unit,
    private val aboutToWait: () -> Unit,
) {
    private val state = UIKitLoopState(operations::nowMillis)
    private val frameCallback: () -> Unit = ::onFrame
    private var iterationRunning = false
    private var currentIterationAcceptsRedraws = false

    fun registerWindow(windowId: WindowId) {
        state.registerWindow(windowId)
        controlFlowChanged()
    }

    fun requestRedraw(windowId: WindowId): Boolean {
        val wakeIteration = !currentIterationAcceptsRedraws
        val queued = state.requestRedraw(windowId, controlFlow(), wakeIteration)
        if (queued && wakeIteration) operations.startDisplayLink(frameCallback)
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
        state.exit()
        operations.disposeDisplayLink()
    }

    fun controlFlowChanged() {
        if (!iterationRunning) armOrStop()
    }

    private fun onFrame() = runIteration()

    private fun runIteration() {
        val cause = state.beginIteration(controlFlow()) ?: run {
            operations.stopDisplayLink()
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
            operations.stopDisplayLink()
            throw failure
        } finally {
            currentIterationAcceptsRedraws = false
            iterationRunning = false
        }

        armOrStop()
    }

    private fun armOrStop() {
        if (!state.hasLiveWindows()) {
            state.arm(ControlFlow.Wait)
            operations.stopDisplayLink()
            return
        }

        val currentControlFlow = controlFlow()
        if (currentControlFlow == ControlFlow.Poll) {
            state.arm(currentControlFlow)
            operations.startDisplayLink(frameCallback)
        } else if (state.hasPendingRedraws() || state.hasPendingIteration()) {
            state.cancelArmedDeadline()
            operations.startDisplayLink(frameCallback)
        } else {
            operations.stopDisplayLink()
            when (val decision = state.arm(currentControlFlow)) {
                UIKitTimerDecision.Cancel -> Unit
                UIKitTimerDecision.Keep -> Unit
                is UIKitTimerDecision.Arm -> operations.scheduleDeadline(decision.deadlineMillis) {
                    if (state.signalDeadline(decision.generation)) runIteration()
                }
            }
        }
    }
}
