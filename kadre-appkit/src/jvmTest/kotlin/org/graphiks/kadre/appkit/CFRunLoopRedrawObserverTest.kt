package org.graphiks.kadre.appkit

import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.WindowId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CFRunLoopRedrawObserverTest {
    @Test
    fun `startup dispatches lifecycle callbacks in AppKit order`() {
        val loop = FakeRunLoop(nowMillis = 1_000L)

        loop.resume()

        assertEquals(
            listOf(
                "resumed",
                "newEvents(${StartCause.Init})",
                "canCreateSurfaces",
                "aboutToWait",
            ),
            loop.callbacks,
        )
    }

    @Test
    fun `coalesced redraw is dispatched between new events and about to wait`() {
        val windowId = WindowId(7L)
        val loop = FakeRunLoop(nowMillis = 1_000L)
        loop.resume()
        loop.callbacks.clear()

        assertTrue(loop.requestRedraw(windowId))
        assertFalse(loop.requestRedraw(windowId))
        loop.apply(loop.arm(ControlFlow.Poll))

        assertEquals(
            listOf(
                "newEvents(${StartCause.Poll})",
                "redraw(${windowId.value})",
                "aboutToWait",
            ),
            loop.callbacks,
        )
    }

    @Test
    fun `close suppresses window callbacks and exit suppresses every later callback`() {
        val closedWindowId = WindowId(7L)
        val openWindowId = WindowId(8L)
        val loop = FakeRunLoop(nowMillis = 1_000L)
        loop.resume()
        loop.callbacks.clear()

        assertTrue(loop.requestRedraw(closedWindowId))
        loop.closeWindow(closedWindowId)
        assertFalse(loop.requestRedraw(closedWindowId))
        assertTrue(loop.requestRedraw(openWindowId))
        loop.apply(loop.arm(ControlFlow.Poll))
        assertEquals(
            listOf(
                "newEvents(${StartCause.Poll})",
                "redraw(${openWindowId.value})",
                "aboutToWait",
            ),
            loop.callbacks,
        )

        loop.exit()
        val callbacksAtExit = loop.callbacks.toList()
        loop.signalExternalEvent()
        loop.requestRedraw(WindowId(9L))
        loop.apply(loop.arm(ControlFlow.Poll))

        assertEquals(callbacksAtExit, loop.callbacks)
    }

    private class FakeRunLoop(nowMillis: Long) {
        private var exited = false
        private var didCreateSurfaces = false
        private val state = AppKitLoopState(nowMillis = { nowMillis })
        val callbacks = mutableListOf<String>()

        fun resume() {
            if (exited) return
            callbacks += "resumed"
            dispatchIteration()
        }

        fun requestRedraw(windowId: WindowId): Boolean = state.requestRedraw(windowId)

        fun closeWindow(windowId: WindowId) {
            state.closeWindow(windowId)
        }

        fun arm(controlFlow: ControlFlow): TimerDecision = state.arm(controlFlow)

        fun apply(decision: TimerDecision) {
            if (exited || decision !== TimerDecision.FireNow) return
            dispatchIteration()
        }

        fun signalExternalEvent() {
            if (exited) return
            state.signalExternalEvent()
            dispatchIteration()
        }

        fun exit() {
            state.exit()
            exited = true
        }

        private fun dispatchIteration() {
            val cause = state.beginIteration()
            callbacks += "newEvents($cause)"
            if (!didCreateSurfaces) {
                didCreateSurfaces = true
                callbacks += "canCreateSurfaces"
            }
            state.takeRedraws().forEach { windowId ->
                callbacks += "redraw(${windowId.value})"
            }
            callbacks += "aboutToWait"
        }
    }
}
