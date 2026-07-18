package org.graphiks.kadre.appkit

import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.WindowId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppKitLoopStateTest {
    private val windowId = WindowId(1L)

    @Test
    fun `first iteration starts with init then poll starts immediately`() {
        val state = AppKitLoopState(nowMillis = { 1_000L })

        assertEquals(StartCause.Init, state.beginIteration())
        assertEquals(TimerDecision.FireNow, state.arm(ControlFlow.Poll))
        assertEquals(StartCause.Poll, state.beginIteration())
    }

    @Test
    fun `wait event starts with wait cancelled without a deadline`() {
        val state = initializedState(nowMillis = { 1_000L })

        assertEquals(TimerDecision.Cancel, state.arm(ControlFlow.Wait))
        state.signalExternalEvent()

        assertEquals(StartCause.WaitCancelled(), state.beginIteration())
    }

    @Test
    fun `future deadline arms an epoch millisecond timer`() {
        val deadline = 2_000L
        val state = initializedState(nowMillis = { 1_000L })

        val decision = assertIs<TimerDecision.Arm>(
            state.arm(ControlFlow.WaitUntil(deadline)),
        )

        assertEquals(deadline, decision.deadline)
        assertTrue(decision.generation > 0L)
    }

    @Test
    fun `deadline callback resumes at the observed epoch time`() {
        var now = 1_000L
        val deadline = 2_000L
        val state = initializedState(nowMillis = { now })
        val decision = assertIs<TimerDecision.Arm>(state.arm(ControlFlow.WaitUntil(deadline)))

        now = 2_007L
        state.signalDeadline(decision.generation)

        assertEquals(
            StartCause.ResumeTimeReached(requestedResume = deadline, start = now),
            state.beginIteration(),
        )
    }

    @Test
    fun `timer firing before its deadline cancels the wait instead of reaching it`() {
        var now = 1_000L
        val deadline = 2_000L
        val state = initializedState(nowMillis = { now })
        val decision = assertIs<TimerDecision.Arm>(state.arm(ControlFlow.WaitUntil(deadline)))

        state.signalDeadline(decision.generation)
        now = deadline

        assertEquals(
            StartCause.WaitCancelled(requestedResume = deadline),
            state.beginIteration(),
        )
    }

    @Test
    fun `event cancels armed deadline and preserves requested resume`() {
        val deadline = 2_000L
        val state = initializedState(nowMillis = { 1_000L })
        val decision = assertIs<TimerDecision.Arm>(state.arm(ControlFlow.WaitUntil(deadline)))

        state.signalExternalEvent()
        state.signalDeadline(decision.generation)

        assertEquals(
            StartCause.WaitCancelled(requestedResume = deadline),
            state.beginIteration(),
        )
    }

    @Test
    fun `stale deadline generation cannot replace the current wait cause`() {
        var now = 1_000L
        val state = initializedState(nowMillis = { now })
        val stale = assertIs<TimerDecision.Arm>(state.arm(ControlFlow.WaitUntil(2_000L)))
        val currentDeadline = 3_000L
        val current = assertIs<TimerDecision.Arm>(
            state.arm(ControlFlow.WaitUntil(currentDeadline)),
        )

        now = 3_005L
        state.signalDeadline(stale.generation)
        state.signalDeadline(current.generation)

        assertEquals(
            StartCause.ResumeTimeReached(requestedResume = currentDeadline, start = now),
            state.beginIteration(),
        )
    }

    @Test
    fun `already expired deadline fires once without arming a timer`() {
        val deadline = 2_000L
        val now = 2_005L
        val state = initializedState(nowMillis = { now })

        assertEquals(TimerDecision.FireNow, state.arm(ControlFlow.WaitUntil(deadline)))
        assertEquals(
            StartCause.ResumeTimeReached(requestedResume = deadline, start = now),
            state.beginIteration(),
        )
        assertEquals(TimerDecision.Cancel, state.arm(ControlFlow.WaitUntil(deadline)))
    }

    @Test
    fun `redraw requests coalesce in first request order and can be requested again`() {
        val secondWindowId = WindowId(2L)
        val state = initializedState(nowMillis = { 1_000L })

        assertTrue(state.requestRedraw(secondWindowId))
        assertTrue(state.requestRedraw(windowId))
        assertFalse(state.requestRedraw(secondWindowId))
        assertEquals(listOf(secondWindowId, windowId), state.takeRedraws())

        assertTrue(state.requestRedraw(secondWindowId))
        assertEquals(listOf(secondWindowId), state.takeRedraws())
    }

    @Test
    fun `close removes queued redraw and permanently rejects that window`() {
        val state = initializedState(nowMillis = { 1_000L })
        assertTrue(state.requestRedraw(windowId))

        state.closeWindow(windowId)

        assertEquals(emptyList(), state.takeRedraws())
        assertFalse(state.requestRedraw(windowId))
    }

    @Test
    fun `exit cancels timer and suppresses pending and later redraws`() {
        val state = initializedState(nowMillis = { 1_000L })
        val timer = assertIs<TimerDecision.Arm>(state.arm(ControlFlow.WaitUntil(2_000L)))
        assertTrue(state.requestRedraw(windowId))

        state.exit()
        state.signalExternalEvent()
        state.signalDeadline(timer.generation)

        assertEquals(emptyList(), state.takeRedraws())
        assertFalse(state.requestRedraw(WindowId(2L)))
        assertEquals(TimerDecision.Cancel, state.arm(ControlFlow.Poll))
    }

    private fun initializedState(nowMillis: () -> Long): AppKitLoopState =
        AppKitLoopState(nowMillis).also { state ->
            assertEquals(StartCause.Init, state.beginIteration())
        }
}
