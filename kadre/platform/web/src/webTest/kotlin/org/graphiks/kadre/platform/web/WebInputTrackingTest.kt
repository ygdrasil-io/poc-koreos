package org.graphiks.kadre.platform.web

import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two stateful helpers both Web ports share: the scroll-coalescing frontier and the pointer
 * motion.
 *
 * They are proven here, on model values and primitives alone, because that is the point of their
 * living in the shared core: the rule of when a scroll may merge and the rule of what a motion is
 * cannot be verified by a target's own reading of a DOM event, and a Wasm copy of either would be a
 * second implementation that could drift from this one. The JS port's own suite proves the reading it
 * feeds them (a real animation frame, a real element position); this suite proves the rules.
 */
class WebInputTrackingTest {
    // --- Scroll coalescing frontier ----------------------------------------------------------------

    @Test
    fun theWheelsOfOneFrameShareAFrontierAndTheFirstWheelOfTheNextOneOpensAnother() {
        val boundary = WebScrollBoundary()

        val first = boundary.advance(deltaMode = 0, buttons = 0)
        val second = boundary.advance(deltaMode = 0, buttons = 0)
        assertEquals(first, second, "two wheels of one frame may merge: one frame is one frontier")

        boundary.frameOpened()
        val afterTheFrame = boundary.advance(deltaMode = 0, buttons = 0)
        assertTrue(
            afterTheFrame > first,
            "the first wheel a new animation frame delivers opens a frontier, which is how the " +
                "browser's own delivery granularity replaces the native phase the DOM does not expose",
        )
        assertEquals(
            afterTheFrame,
            boundary.advance(deltaMode = 0, buttons = 0),
            "and only the first one does: the rest of the frame shares it",
        )
    }

    @Test
    fun aChangeOfUnitOrOfButtonStateOpensAFrontierOfItsOwn() {
        val boundary = WebScrollBoundary()
        val oneUnit = boundary.advance(deltaMode = 0, buttons = 0)

        assertEquals(oneUnit, boundary.advance(deltaMode = 0, buttons = 0), "nothing changed")
        val afterTheUnitChange = boundary.advance(deltaMode = 1, buttons = 0)
        assertTrue(afterTheUnitChange > oneUnit, "a change of the browser's unit of measurement opens one")

        val afterTheButtonChange = boundary.advance(deltaMode = 1, buttons = 1)
        assertTrue(afterTheButtonChange > afterTheUnitChange, "a change of the pointer's button state opens one")
        assertEquals(
            afterTheButtonChange,
            boundary.advance(deltaMode = 1, buttons = 1),
            "the same unit and the same button state still merge, frame after frame",
        )
    }

    @Test
    fun aFrontierIsNeverHandedOutTwiceWhateverThePortObservedInBetween() {
        val boundary = WebScrollBoundary()
        val delivered = mutableListOf<Long>()

        delivered += boundary.advance(deltaMode = 0, buttons = 0)
        boundary.frameOpened()
        delivered += boundary.advance(deltaMode = 0, buttons = 0)
        boundary.clear()
        delivered += boundary.advance(deltaMode = 1, buttons = 0)
        boundary.frameOpened()
        delivered += boundary.advance(deltaMode = 1, buttons = 0)

        assertEquals(4, delivered.size)
        assertEquals(
            delivered.sorted(),
            delivered,
            "the frontiers never go backwards",
        )
        assertEquals(
            delivered.distinct(),
            delivered,
            "a frontier is never handed out twice, not even after the port forgot its observations: a " +
                "consumer must not merge a scroll it was told to keep separate",
        )
    }

    // --- Pointer motion ----------------------------------------------------------------------------

    @Test
    fun aMotionIsTheDifferenceFromThePreviousObservation() {
        val motion = WebPointerMotion()

        assertEquals(
            LogicalDelta(0.0, 0.0),
            motion.advance(LogicalPoint(10.0, 20.0)),
            "the first observation a port ever makes has nothing to measure from: no motion was " +
                "observed, and inventing one would be an approximation",
        )
        assertEquals(LogicalDelta(5.0, -4.0), motion.advance(LogicalPoint(15.0, 16.0)))
        assertEquals(LogicalDelta(-1.5, 0.0), motion.advance(LogicalPoint(13.5, 16.0)))
    }

    @Test
    fun anObservationWhoseMotionIsNotDeliveredStillOpensTheMotionOfTheNextOne() {
        val motion = WebPointerMotion()

        // An entry, then a button transition: neither carries a motion, and a motion that follows the
        // transition measures from the transition's own position rather than repeating movement the
        // transition already reported.
        motion.record(LogicalPoint(3.0, 4.0))
        motion.record(LogicalPoint(9.0, 9.0))

        assertEquals(LogicalDelta(1.0, 6.0), motion.advance(LogicalPoint(10.0, 15.0)))
    }

    @Test
    fun forgettingThePreviousPositionMakesTheNextObservationTheFirstOneAgain() {
        val motion = WebPointerMotion()
        motion.advance(LogicalPoint(1.0, 2.0))
        assertEquals(LogicalDelta(1.0, 1.0), motion.advance(LogicalPoint(2.0, 3.0)))

        motion.clear()

        assertEquals(
            LogicalDelta(0.0, 0.0),
            motion.advance(LogicalPoint(40.0, 50.0)),
            "after an exit the next observation is the first of a re-entry, so it measures from its " +
                "own entry point rather than from wherever the pointer left",
        )
        assertEquals(LogicalDelta(2.0, 3.0), motion.advance(LogicalPoint(42.0, 53.0)))
    }
}
