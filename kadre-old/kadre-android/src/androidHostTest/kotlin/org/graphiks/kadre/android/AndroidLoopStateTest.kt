package org.graphiks.kadre.android

import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import org.graphiks.kadre.test.RecordingApplicationHandler
import org.graphiks.kadre.test.ScriptedEventLoop
import org.graphiks.kadre.test.assertIterationOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidLoopStateTest {
    private val windowId = WindowId(1L)

    @Test
    fun `state adapter records a complete shared iteration`() {
        val state = AndroidLoopState(nowMillis = { 0L })
        val eventLoop = ScriptedEventLoop(emptyList())
        val handler = RecordingApplicationHandler()
        state.register(windowId)
        state.requestRedraw(windowId)

        handler.newEvents(eventLoop, checkNotNull(state.takeStartCause(ControlFlow.Poll)))
        state.takeRedraws().forEach { handler.windowEvent(eventLoop, it, WindowEvent.RedrawRequested) }
        handler.aboutToWait(eventLoop)

        assertIterationOrder(handler.trace)
    }

    @Test
    fun `register rejects an already open window id`() {
        val state = AndroidLoopState(nowMillis = { 0L })
        state.register(windowId)

        assertFailsWith<IllegalStateException> {
            state.register(windowId)
        }
        assertTrue(state.isOpen(windowId))
    }

    @Test
    fun `register after close is rejected and close remains terminal`() {
        val state = AndroidLoopState(nowMillis = { 0L })
        state.register(windowId)
        assertTrue(state.requestRedraw(windowId))
        assertTrue(state.wakeUp())
        assertTrue(state.close(windowId))

        assertFailsWith<IllegalStateException> {
            state.register(windowId)
        }
        assertFalse(state.isOpen(windowId))
        assertFalse(state.requestRedraw(windowId))
        assertFalse(state.wakeUp())
    }

    @Test
    fun `wake can be consumed and rearmed for three wait cycles`() {
        val state = AndroidLoopState(nowMillis = { 0L })
        state.register(windowId)

        repeat(3) {
            assertTrue(state.wakeUp())
            assertEquals(StartCause.WaitCancelled(), state.takeStartCause(ControlFlow.Wait))
            assertNull(state.takeStartCause(ControlFlow.Wait))
        }
    }

    @Test
    fun `pending wakes coalesce until consumed`() {
        val state = AndroidLoopState(nowMillis = { 0L })
        state.register(windowId)

        assertTrue(state.wakeUp())
        assertFalse(state.wakeUp())
        assertEquals(StartCause.WaitCancelled(), state.takeStartCause(ControlFlow.Wait))
        assertNull(state.takeStartCause(ControlFlow.Wait))
    }

    @Test
    fun `poll starts immediately and consumes a pending wake`() {
        val state = AndroidLoopState(nowMillis = { 0L })
        state.register(windowId)

        assertEquals(StartCause.Poll, state.takeStartCause(ControlFlow.Poll))
        assertTrue(state.wakeUp())
        assertEquals(StartCause.Poll, state.takeStartCause(ControlFlow.Poll))
        assertNull(state.takeStartCause(ControlFlow.Wait))
    }

    @Test
    fun `ten redraw requests coalesce to one window id`() {
        val state = AndroidLoopState(nowMillis = { 0L })
        state.register(windowId)

        assertTrue(state.requestRedraw(windowId))
        repeat(9) {
            assertFalse(state.requestRedraw(windowId))
        }

        assertEquals(listOf(windowId), state.takeRedraws())
        assertEquals(emptyList(), state.takeRedraws())
    }

    @Test
    fun `redraws keep first-request insertion order`() {
        val secondWindowId = WindowId(2L)
        val thirdWindowId = WindowId(3L)
        val state = AndroidLoopState(nowMillis = { 0L })
        state.register(windowId)
        state.register(secondWindowId)
        state.register(thirdWindowId)

        assertTrue(state.requestRedraw(secondWindowId))
        assertTrue(state.requestRedraw(windowId))
        assertFalse(state.requestRedraw(secondWindowId))
        assertTrue(state.requestRedraw(thirdWindowId))

        assertEquals(listOf(secondWindowId, windowId, thirdWindowId), state.takeRedraws())
    }

    @Test
    fun `consuming a redraw permits a later redraw`() {
        val state = AndroidLoopState(nowMillis = { 0L })
        state.register(windowId)

        assertTrue(state.requestRedraw(windowId))
        assertEquals(listOf(windowId), state.takeRedraws())
        assertTrue(state.requestRedraw(windowId))
        assertEquals(listOf(windowId), state.takeRedraws())
    }

    @Test
    fun `wake before wait-until deadline cancels the wait with requested resume`() {
        var now = 999L
        val deadline = 1_000L
        val state = AndroidLoopState(nowMillis = { now })
        state.register(windowId)

        assertNull(state.takeStartCause(ControlFlow.WaitUntil(deadline)))
        assertTrue(state.wakeUp())
        assertEquals(
            StartCause.WaitCancelled(requestedResume = deadline),
            state.takeStartCause(ControlFlow.WaitUntil(deadline)),
        )
        assertNull(state.takeStartCause(ControlFlow.WaitUntil(deadline)))
    }

    @Test
    fun `wait-until deadline resumes only at or after epoch millisecond deadline`() {
        var now = 999L
        val deadline = 1_000L
        val state = AndroidLoopState(nowMillis = { now })
        state.register(windowId)

        assertNull(state.takeStartCause(ControlFlow.WaitUntil(deadline)))

        now = deadline
        assertEquals(
            StartCause.ResumeTimeReached(requestedResume = deadline, start = deadline),
            state.takeStartCause(ControlFlow.WaitUntil(deadline)),
        )

        now = 1_007L
        assertEquals(
            StartCause.ResumeTimeReached(requestedResume = deadline, start = now),
            state.takeStartCause(ControlFlow.WaitUntil(deadline)),
        )
    }

    @Test
    fun `closing one window removes only its queued redraw`() {
        val otherWindowId = WindowId(2L)
        val state = AndroidLoopState(nowMillis = { 0L })
        state.register(windowId)
        state.register(otherWindowId)
        assertTrue(state.requestRedraw(windowId))
        assertTrue(state.requestRedraw(otherWindowId))
        assertTrue(state.wakeUp())

        assertTrue(state.close(windowId))

        assertFalse(state.isOpen(windowId))
        assertTrue(state.isOpen(otherWindowId))
        assertEquals(listOf(otherWindowId), state.takeRedraws())
        assertEquals(StartCause.WaitCancelled(), state.takeStartCause(ControlFlow.Wait))
    }

    @Test
    fun `closing last window clears redraw and wake and rejects later enqueue`() {
        val state = AndroidLoopState(nowMillis = { 0L })
        state.register(windowId)
        assertTrue(state.requestRedraw(windowId))
        assertTrue(state.wakeUp())

        assertTrue(state.close(windowId))

        assertFalse(state.isOpen(windowId))
        assertEquals(emptyList(), state.takeRedraws())
        assertNull(state.takeStartCause(ControlFlow.Wait))
        assertFalse(state.requestRedraw(windowId))
        assertFalse(state.wakeUp())
        assertFalse(state.close(windowId))
    }
}
