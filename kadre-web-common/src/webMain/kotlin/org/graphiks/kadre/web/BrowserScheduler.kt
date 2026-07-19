package org.graphiks.kadre.web

import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.StartCause

/** Browser operations required by the target-neutral event-loop scheduler. */
internal interface BrowserSchedulingApi {
    fun epochNowMillis(): Long
    fun requestAnimationFrame(callback: () -> Unit): Int
    fun cancelAnimationFrame(id: Int)
    fun setTimeout(delayMillis: Int, callback: () -> Unit): Int
    fun clearTimeout(id: Int)
}

/** Target-neutral owner of browser frame and deadline scheduling. */
internal class BrowserScheduler(
    private val api: BrowserSchedulingApi,
    private val onIteration: (StartCause) -> Unit,
) {
    private var generation = 0L
    private var animationFrameId: Int? = null
    private var timeoutId: Int? = null
    private var requestedDeadline: Long? = null
    private var pendingCause: StartCause? = null
    private var deliveringIteration = false
    private var cancelled = false

    /** Replaces all previous ownership with the scheduling required by [controlFlow]. */
    fun arm(controlFlow: ControlFlow, hasPendingWork: Boolean = false) {
        if (cancelled) return

        val deferredCause = if (deliveringIteration) pendingCause else null
        invalidateAndCancelOwnedIds()
        if (deferredCause != null) {
            requestFrame(deferredCause)
            return
        }
        if (hasPendingWork) {
            requestFrame(wakeCause(controlFlow))
            return
        }

        when (controlFlow) {
            ControlFlow.Poll -> requestFrame(StartCause.Poll)
            ControlFlow.Wait -> Unit
            is ControlFlow.WaitUntil -> requestDeadline(controlFlow.instant)
        }
    }

    /** Wakes an idle/deadline loop without duplicating an already-owned RAF. */
    fun signalEvent(controlFlow: ControlFlow) {
        if (cancelled || animationFrameId != null) return

        val ownedDeadline = requestedDeadline
        val cause = if (timeoutId != null && ownedDeadline != null) {
            StartCause.WaitCancelled(ownedDeadline)
        } else {
            wakeCause(controlFlow)
        }
        if (deliveringIteration) {
            if (pendingCause == null) {
                pendingCause = cause
            }
            return
        }
        invalidateAndCancelOwnedIds()
        requestFrame(cause)
    }

    /** Permanently invalidates this scheduler and cancels every browser ID it owns. */
    fun cancel() {
        if (cancelled) return
        cancelled = true
        invalidateAndCancelOwnedIds()
    }

    private fun wakeCause(controlFlow: ControlFlow): StartCause = when (controlFlow) {
        ControlFlow.Poll -> StartCause.Poll
        ControlFlow.Wait -> StartCause.WaitCancelled()
        is ControlFlow.WaitUntil -> StartCause.WaitCancelled(controlFlow.instant)
    }

    private fun requestDeadline(deadline: Long) {
        requestedDeadline = deadline
        val callbackGeneration = generation
        var callbackId: Int? = null
        val id = api.setTimeout(clampedDelay(deadline, api.epochNowMillis())) {
            val ownedId = callbackId ?: return@setTimeout
            if (cancelled || callbackGeneration != generation || timeoutId != ownedId) {
                return@setTimeout
            }

            timeoutId = null
            val observedEpoch = api.epochNowMillis()
            requestFrame(StartCause.ResumeTimeReached(deadline, observedEpoch))
        }
        callbackId = id
        timeoutId = id
    }

    private fun requestFrame(cause: StartCause) {
        pendingCause = cause
        val callbackGeneration = generation
        var callbackId: Int? = null
        val id = api.requestAnimationFrame {
            val ownedId = callbackId ?: return@requestAnimationFrame
            if (
                cancelled ||
                callbackGeneration != generation ||
                animationFrameId != ownedId ||
                pendingCause !== cause
            ) {
                return@requestAnimationFrame
            }

            animationFrameId = null
            pendingCause = null
            deliveringIteration = true
            try {
                onIteration(cause)
            } finally {
                deliveringIteration = false
            }
        }
        callbackId = id
        animationFrameId = id
    }

    private fun invalidateAndCancelOwnedIds() {
        generation += 1L
        animationFrameId?.let(api::cancelAnimationFrame)
        animationFrameId = null
        timeoutId?.let(api::clearTimeout)
        timeoutId = null
        requestedDeadline = null
        pendingCause = null
    }

    private fun clampedDelay(deadline: Long, now: Long): Int {
        if (deadline <= now) return 0

        val largestIntDeadline = if (now > Long.MAX_VALUE - Int.MAX_VALUE.toLong()) {
            Long.MAX_VALUE
        } else {
            now + Int.MAX_VALUE.toLong()
        }
        return if (deadline > largestIntDeadline) {
            Int.MAX_VALUE
        } else {
            (deadline - now).toInt()
        }
    }
}
