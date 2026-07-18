package org.graphiks.kadre.uikit

import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.WindowId

/** Timer work selected by the pure UIKit loop state. */
internal sealed interface UIKitTimerDecision {
    data object Cancel : UIKitTimerDecision

    data object Keep : UIKitTimerDecision

    data class Arm(
        val deadlineMillis: Long,
        val generation: Long,
    ) : UIKitTimerDecision
}

/** Pure synchronous state owned by the UIKit event-loop scheduler. */
internal class UIKitLoopState(
    private val nowMillis: () -> Long,
) {
    private val claimedWindows = mutableSetOf<WindowId>()
    private val liveWindows = mutableSetOf<WindowId>()
    private val pendingRedraws = linkedSetOf<WindowId>()
    private var pendingCause: StartCause? = null
    private var requestedResume: Long? = null
    private var nextDeadlineGeneration = 0L
    private var armedDeadline: UIKitTimerDecision.Arm? = null
    private var handledDeadline: Long? = null

    fun registerWindow(windowId: WindowId) {
        check(claimedWindows.add(windowId)) {
            "WindowId ${windowId.value} has already been registered"
        }
        liveWindows.add(windowId)
    }

    fun requestRedraw(
        windowId: WindowId,
        controlFlow: ControlFlow,
        wakeIteration: Boolean,
    ): Boolean {
        if (windowId !in liveWindows) return false
        if (wakeIteration && controlFlow != ControlFlow.Poll) {
            signalEarlierEvent()
        }
        return pendingRedraws.add(windowId)
    }

    fun wakeExternal(controlFlow: ControlFlow): Boolean {
        if (liveWindows.isEmpty()) return false
        if (controlFlow != ControlFlow.Poll) signalEarlierEvent()
        return true
    }

    fun arm(controlFlow: ControlFlow): UIKitTimerDecision {
        return when (controlFlow) {
            ControlFlow.Wait -> {
                armedDeadline = null
                requestedResume = null
                handledDeadline = null
                UIKitTimerDecision.Cancel
            }

            ControlFlow.Poll -> {
                armedDeadline = null
                requestedResume = null
                handledDeadline = null
                UIKitTimerDecision.Cancel
            }

            is ControlFlow.WaitUntil -> {
                requestedResume = controlFlow.instant
                if (armedDeadline?.deadlineMillis == controlFlow.instant) {
                    return UIKitTimerDecision.Keep
                }
                armedDeadline = null
                if (handledDeadline == controlFlow.instant) return UIKitTimerDecision.Cancel
                UIKitTimerDecision.Arm(
                    deadlineMillis = controlFlow.instant,
                    generation = ++nextDeadlineGeneration,
                ).also { armedDeadline = it }
            }
        }
    }

    fun signalDeadline(generation: Long): Boolean {
        val armed = armedDeadline?.takeIf { it.generation == generation } ?: return false
        val observedAt = nowMillis()
        if (observedAt < armed.deadlineMillis) return false
        armedDeadline = null
        requestedResume = armed.deadlineMillis
        handledDeadline = armed.deadlineMillis
        pendingCause = StartCause.ResumeTimeReached(
            requestedResume = armed.deadlineMillis,
            start = observedAt,
        )
        return true
    }

    fun beginIteration(controlFlow: ControlFlow): StartCause? =
        pendingCause?.also { pendingCause = null }
            ?: if (controlFlow == ControlFlow.Poll) StartCause.Poll else null

    fun takeRedraws(): List<WindowId> = pendingRedraws.toList().also {
        pendingRedraws.clear()
    }

    fun hasPendingRedraws(): Boolean = pendingRedraws.isNotEmpty()

    fun hasPendingIteration(): Boolean = pendingCause != null

    fun hasLiveWindows(): Boolean = liveWindows.isNotEmpty()

    fun isLive(windowId: WindowId): Boolean = windowId in liveWindows

    fun cancelArmedDeadline() {
        armedDeadline = null
    }

    fun closeWindow(windowId: WindowId, controlFlow: ControlFlow): Boolean {
        if (!liveWindows.remove(windowId)) return false
        pendingRedraws.remove(windowId)
        armedDeadline = null
        if (liveWindows.isEmpty()) {
            pendingCause = null
        } else if (controlFlow != ControlFlow.Poll) {
            signalEarlierEvent()
        }
        return true
    }

    fun exit() {
        liveWindows.clear()
        abortIteration()
    }

    fun abortIteration() {
        pendingRedraws.clear()
        pendingCause = null
        requestedResume = null
        armedDeadline = null
        handledDeadline = null
    }

    private fun signalEarlierEvent() {
        if (pendingCause == null) {
            pendingCause = StartCause.WaitCancelled(requestedResume)
        }
        armedDeadline = null
    }
}
