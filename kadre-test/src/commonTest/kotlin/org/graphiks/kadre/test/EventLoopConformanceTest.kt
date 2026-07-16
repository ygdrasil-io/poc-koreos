package org.graphiks.kadre.test

import kotlin.test.Test
import kotlin.test.assertFailsWith

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
    fun callbackAfterClosedMarkerIsRejected() {
        assertFailsWith<AssertionError> {
            assertNoCallbacksAfter(
                listOf(ObservedCallback.Closed, ObservedCallback.WindowEvent),
                ObservedCallback.Closed,
            )
        }
    }
}
