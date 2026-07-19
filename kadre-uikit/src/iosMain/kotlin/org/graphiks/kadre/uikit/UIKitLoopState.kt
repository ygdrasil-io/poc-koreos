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

/** Result of consuming a native deadline callback. */
internal sealed interface UIKitDeadlineSignal {
    data object Stale : UIKitDeadlineSignal

    data class Early(val replacement: UIKitTimerDecision.Arm) : UIKitDeadlineSignal

    data object Reached : UIKitDeadlineSignal
}

/** Pure synchronous state owned by the UIKit event-loop scheduler. */
internal class UIKitLoopState(
    private val nowMillis: () -> Long,
) {
    private val liveWindows = mutableSetOf<WindowId>()
    private val pendingRedraws = linkedSetOf<WindowId>()
    private var pendingCause: StartCause? = null
    private var requestedResume: Long? = null
    private var nextDeadlineGeneration = 0L
    private var armedDeadline: UIKitTimerDecision.Arm? = null
    private var handledDeadline: Long? = null
    private var terminal = false

    fun registerWindow(windowId: WindowId): Boolean {
        check(!terminal) { "UIKit event loop is terminal" }
        val wasEmpty = liveWindows.isEmpty()
        check(liveWindows.add(windowId)) {
            "WindowId ${windowId.value} has already been registered"
        }
        return wasEmpty
    }

    fun rollbackWindowRegistration(windowId: WindowId) {
        liveWindows.remove(windowId)
        pendingRedraws.remove(windowId)
        armedDeadline = null
        if (liveWindows.isEmpty()) {
            pendingCause = null
            requestedResume = null
            handledDeadline = null
        }
    }

    fun requestRedraw(
        windowId: WindowId,
        controlFlow: ControlFlow,
        wakeIteration: Boolean,
    ): Boolean {
        if (terminal || windowId !in liveWindows) return false
        if (wakeIteration && controlFlow != ControlFlow.Poll) {
            signalEarlierEvent()
        }
        return pendingRedraws.add(windowId)
    }

    fun wakeExternal(controlFlow: ControlFlow): Boolean {
        if (terminal) return false
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

    fun signalDeadline(generation: Long): UIKitDeadlineSignal {
        val armed = armedDeadline?.takeIf { it.generation == generation }
            ?: return UIKitDeadlineSignal.Stale
        val observedAt = nowMillis()
        armedDeadline = null
        if (observedAt < armed.deadlineMillis) {
            val replacement = UIKitTimerDecision.Arm(
                deadlineMillis = armed.deadlineMillis,
                generation = ++nextDeadlineGeneration,
            )
            armedDeadline = replacement
            return UIKitDeadlineSignal.Early(replacement)
        }
        requestedResume = armed.deadlineMillis
        handledDeadline = armed.deadlineMillis
        pendingCause = StartCause.ResumeTimeReached(
            requestedResume = armed.deadlineMillis,
            start = observedAt,
        )
        return UIKitDeadlineSignal.Reached
    }

    fun beginIteration(controlFlow: ControlFlow): StartCause? = if (terminal) {
        null
    } else {
        pendingCause?.also { pendingCause = null }
            ?: if (controlFlow == ControlFlow.Poll) StartCause.Poll else null
    }

    fun takeRedraws(): List<WindowId> = pendingRedraws.toList().also {
        pendingRedraws.clear()
    }

    fun hasPendingRedraws(): Boolean = pendingRedraws.isNotEmpty()

    fun hasPendingIteration(): Boolean = pendingCause != null

    fun hasLiveWindows(): Boolean = liveWindows.isNotEmpty()

    fun isTerminal(): Boolean = terminal

    fun isLive(windowId: WindowId): Boolean = windowId in liveWindows

    fun cancelArmedDeadline() {
        armedDeadline = null
    }

    fun closeWindow(windowId: WindowId, controlFlow: ControlFlow): Boolean {
        if (!liveWindows.remove(windowId)) return false
        pendingRedraws.remove(windowId)
        armedDeadline = null
        if (controlFlow != ControlFlow.Poll) {
            signalEarlierEvent()
        }
        return true
    }

    fun exit(): Boolean {
        if (terminal) return false
        terminal = true
        liveWindows.clear()
        abortIteration()
        return true
    }

    fun abortIteration() {
        pendingRedraws.clear()
        pendingCause = null
        requestedResume = null
        armedDeadline = null
        handledDeadline = null
    }

    private fun signalEarlierEvent() {
        val deadline = requestedResume
        val observedAt = nowMillis()
        val deadlineReached = deadline != null && observedAt >= deadline
        if (pendingCause == null || pendingCause is StartCause.WaitCancelled && deadlineReached) {
            pendingCause = if (deadlineReached) {
                handledDeadline = deadline
                StartCause.ResumeTimeReached(
                    requestedResume = checkNotNull(deadline),
                    start = observedAt,
                )
            } else {
                StartCause.WaitCancelled(deadline)
            }
        }
        armedDeadline = null
    }
}
