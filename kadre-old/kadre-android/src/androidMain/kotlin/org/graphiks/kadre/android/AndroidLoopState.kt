package org.graphiks.kadre.android

import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.WindowId

/** Pure synchronous state used by the Android event-loop adapter. */
internal class AndroidLoopState(
    private val nowMillis: () -> Long,
) {
    private val claimedWindowIds = mutableSetOf<WindowId>()
    private val openWindows = mutableSetOf<WindowId>()
    private val pendingRedraws = linkedSetOf<WindowId>()
    private var pendingWake = false

    /**
     * Claims [windowId] for this state's lifetime and opens it exactly once.
     *
     * Task 4 must provide a distinct ID for each Android window before calling this method;
     * IDs derived from the shared SurfaceView collide across multiple window creations.
     */
    fun register(windowId: WindowId) {
        check(claimedWindowIds.add(windowId)) {
            "WindowId ${windowId.value} has already been registered"
        }
        openWindows.add(windowId)
    }

    fun requestRedraw(windowId: WindowId): Boolean {
        if (windowId !in openWindows) return false
        return pendingRedraws.add(windowId)
    }

    fun takeRedraws(): List<WindowId> = pendingRedraws.toList().also {
        pendingRedraws.clear()
    }

    fun wakeUp(): Boolean {
        if (openWindows.isEmpty() || pendingWake) return false
        pendingWake = true
        return true
    }

    /**
     * Takes the cause for the next iteration and consumes any pending wake.
     *
     * [ControlFlow.Wait] needs a wake, [ControlFlow.Poll] starts immediately,
     * and [ControlFlow.WaitUntil] starts on a wake or once its deadline is reached.
     */
    fun takeStartCause(controlFlow: ControlFlow): StartCause? {
        val wasWoken = pendingWake
        pendingWake = false

        return when (controlFlow) {
            ControlFlow.Wait -> {
                if (wasWoken) StartCause.WaitCancelled() else null
            }

            ControlFlow.Poll -> StartCause.Poll

            is ControlFlow.WaitUntil -> {
                when {
                    wasWoken -> StartCause.WaitCancelled(controlFlow.instant)
                    else -> nowMillis().let { now ->
                        if (now >= controlFlow.instant) {
                            StartCause.ResumeTimeReached(controlFlow.instant, now)
                        } else {
                            null
                        }
                    }
                }
            }
        }
    }

    fun close(windowId: WindowId): Boolean {
        if (!openWindows.remove(windowId)) return false

        pendingRedraws.remove(windowId)
        if (openWindows.isEmpty()) {
            pendingWake = false
        }
        return true
    }

    fun isOpen(windowId: WindowId): Boolean = windowId in openWindows
}
