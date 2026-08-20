package org.graphiks.kadre.appkit

import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.WindowId

/** Native timer work required after an AppKit event-loop iteration. */
internal sealed interface TimerDecision {
    data object Cancel : TimerDecision

    data object FireNow : TimerDecision

    data class Arm(
        val deadline: Long,
        val generation: Long,
    ) : TimerDecision
}

/** Admission result for one redraw request. */
internal enum class RedrawRequestResult {
    Queued,
    Coalesced,
    Rejected,
}

/** Pure synchronous state owned by the AppKit event-loop adapter. */
internal class AppKitLoopState(private val nowMillis: () -> Long) {
    private data class ArmedDeadline(
        val deadline: Long,
        val generation: Long,
    )

    private val pendingRedraws = linkedSetOf<WindowId>()
    private val closedWindows = mutableSetOf<WindowId>()
    private var firstIteration = true
    private var exited = false
    private var nextGeneration = 0L
    private var armedDeadline: ArmedDeadline? = null
    private var requestedResume: Long? = null
    private var handledExpiredDeadline: Long? = null
    private var pendingCause: StartCause? = null

    @Synchronized
    fun requestRedraw(windowId: WindowId): RedrawRequestResult {
        if (exited || windowId in closedWindows) return RedrawRequestResult.Rejected
        return if (pendingRedraws.add(windowId)) {
            RedrawRequestResult.Queued
        } else {
            RedrawRequestResult.Coalesced
        }
    }

    @Synchronized
    fun arm(controlFlow: ControlFlow): TimerDecision {
        if (exited) return TimerDecision.Cancel
        if (pendingCause != null) return TimerDecision.FireNow

        armedDeadline = null
        return when (controlFlow) {
            ControlFlow.Wait -> {
                requestedResume = null
                handledExpiredDeadline = null
                if (pendingRedraws.isEmpty()) {
                    TimerDecision.Cancel
                } else {
                    signalExternalEvent()
                    TimerDecision.FireNow
                }
            }

            ControlFlow.Poll -> {
                requestedResume = null
                handledExpiredDeadline = null
                pendingCause = pendingCause ?: StartCause.Poll
                TimerDecision.FireNow
            }

            is ControlFlow.WaitUntil -> {
                if (pendingRedraws.isEmpty()) {
                    armDeadline(controlFlow.instant)
                } else {
                    requestedResume = controlFlow.instant
                    handledExpiredDeadline = null
                    signalExternalEvent()
                    TimerDecision.FireNow
                }
            }
        }
    }

    fun signalExternalEvent() {
        if (exited || pendingCause != null) return

        val deadline = requestedResume
        pendingCause = if (deadline == null) {
            StartCause.WaitCancelled()
        } else {
            deadlineCause(deadline, nowMillis())
        }
        armedDeadline = null
    }

    fun signalDeadline(generation: Long) {
        if (exited || pendingCause != null) return
        val armed = armedDeadline?.takeIf { it.generation == generation } ?: return
        armedDeadline = null

        val observedAt = nowMillis()
        pendingCause = deadlineCause(armed.deadline, observedAt)
    }

    /** Classifies the Core Foundation wake before its iteration is consumed. */
    fun classifyWake(timerGeneration: Long?) {
        if (exited || firstIteration || pendingCause != null) return

        val armed = timerGeneration?.let { generation ->
            armedDeadline?.takeIf { it.generation == generation }
        }
        if (armed == null) {
            signalExternalEvent()
        } else {
            armedDeadline = null
            pendingCause = deadlineCause(armed.deadline, nowMillis())
        }
    }

    fun beginPendingIterationOrNull(): StartCause? {
        if (firstIteration) {
            firstIteration = false
            return StartCause.Init
        }

        return pendingCause?.also {
            pendingCause = null
        }
    }

    fun beginIteration(): StartCause =
        checkNotNull(beginPendingIterationOrNull()) {
            "AppKit iteration began without a run-loop signal"
        }

    @Synchronized
    fun takeRedraws(): List<WindowId> {
        if (exited) return emptyList()
        return pendingRedraws.toList().also {
            pendingRedraws.clear()
        }
    }

    @Synchronized
    fun closeWindow(windowId: WindowId) {
        closedWindows.add(windowId)
        pendingRedraws.remove(windowId)
    }

    @Synchronized
    fun exit() {
        exited = true
        pendingRedraws.clear()
        armedDeadline = null
        requestedResume = null
        pendingCause = null
    }

    private fun armDeadline(deadline: Long): TimerDecision {
        requestedResume = deadline
        val observedAt = nowMillis()
        if (observedAt >= deadline) {
            if (handledExpiredDeadline == deadline) return TimerDecision.Cancel

            handledExpiredDeadline = deadline
            pendingCause = pendingCause ?: StartCause.ResumeTimeReached(
                requestedResume = deadline,
                start = observedAt,
            )
            return TimerDecision.FireNow
        }

        handledExpiredDeadline = null
        val generation = ++nextGeneration
        armedDeadline = ArmedDeadline(deadline, generation)
        return TimerDecision.Arm(deadline, generation)
    }

    private fun deadlineCause(deadline: Long, observedAt: Long): StartCause =
        if (observedAt >= deadline) {
            handledExpiredDeadline = deadline
            StartCause.ResumeTimeReached(
                requestedResume = deadline,
                start = observedAt,
            )
        } else {
            StartCause.WaitCancelled(requestedResume = deadline)
        }
}
