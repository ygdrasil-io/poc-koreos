package org.graphiks.kadre.test

import kotlin.test.Test
import kotlin.test.assertFailsWith

private class FakeDriver(
    private val oneShotWake: Boolean = false,
    private val dispatchAfterClose: Boolean = false,
) : EventLoopConformanceDriver {
    override val trace = mutableListOf<ObservedCallback>()
    private var wakes = 0
    private var pendingWake = false
    private var pendingRedraw = false
    private var closed = false

    override fun start() = Unit
    override fun wakeUp() {
        if ((!closed || dispatchAfterClose) && (!oneShotWake || wakes++ == 0)) pendingWake = true
    }

    override fun requestRedraw() {
        if (!closed || dispatchAfterClose) pendingRedraw = true
    }

    override fun waitForIdle() {
        if (!pendingWake && !pendingRedraw) return
        trace += ObservedCallback.NewEvents
        if (pendingRedraw) trace += ObservedCallback.RedrawRequested
        trace += ObservedCallback.AboutToWait
        pendingWake = false
        pendingRedraw = false
    }

    override fun closeWindow() {
        if (closed) return
        closed = true
        pendingWake = false
        pendingRedraw = false
        trace += ObservedCallback.Destroyed
        trace += ObservedCallback.Closed
    }

    override fun shutdown() = Unit
}

class EventLoopConformanceTest {
    @Test
    fun validIterationIsAccepted() {
        assertIterationOrder(
            listOf(
                ObservedCallback.NewEvents,
                ObservedCallback.WindowEvent,
                ObservedCallback.AboutToWait,
            )
        )
    }

    @Test
    fun eventBeforeNewEventsIsRejected() {
        assertFailsWith<AssertionError> {
            assertIterationOrder(
                listOf(
                    ObservedCallback.WindowEvent,
                    ObservedCallback.NewEvents,
                    ObservedCallback.AboutToWait,
                )
            )
        }
    }

    @Test
    fun secondNewEventsIsRejected() {
        assertFailsWith<AssertionError> {
            assertIterationOrder(
                listOf(
                    ObservedCallback.NewEvents,
                    ObservedCallback.NewEvents,
                    ObservedCallback.AboutToWait,
                )
            )
        }
    }

    @Test
    fun missingAboutToWaitIsRejected() {
        assertFailsWith<AssertionError> {
            assertIterationOrder(listOf(ObservedCallback.NewEvents))
        }
    }

    @Test
    fun dispatchBeforeNewEventsIsRejected() {
        assertFailsWith<AssertionError> {
            assertIterationOrder(
                listOf(
                    ObservedCallback.WindowEvent,
                    ObservedCallback.NewEvents,
                    ObservedCallback.AboutToWait,
                )
            )
        }
    }

    @Test
    fun callbackAfterAboutToWaitIsRejected() {
        assertFailsWith<AssertionError> {
            assertIterationOrder(
                listOf(
                    ObservedCallback.NewEvents,
                    ObservedCallback.AboutToWait,
                    ObservedCallback.WindowEvent,
                )
            )
        }
    }

    @Test
    fun callbackAfterClosedMarkerIsRejected() {
        assertFailsWith<AssertionError> {
            assertNoCallbacksAfter(
                listOf(ObservedCallback.Closed, ObservedCallback.WindowEvent),
                ObservedCallback.Closed,
            )
        }
    }

    @Test
    fun oneShotWakeDriverFailsConformance() {
        assertFailsWith<AssertionError> { assertWakeUpRearms { FakeDriver(oneShotWake = true) } }
    }

    @Test
    fun reusableWakeDriverPassesConformance() {
        assertWakeUpRearms { FakeDriver() }
    }

    @Test
    fun redrawAfterIdleCoalesces() {
        assertRedrawAfterIdle { FakeDriver() }
    }

    @Test
    fun terminalCloseDriverPassesConformance() {
        assertCloseIsTerminal { FakeDriver() }
    }

    @Test
    fun callbackAfterCloseFailsConformance() {
        assertFailsWith<AssertionError> {
            assertCloseIsTerminal { FakeDriver(dispatchAfterClose = true) }
        }
    }
}
