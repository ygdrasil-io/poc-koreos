package org.graphiks.kadre.internal.appkit

import org.graphiks.kffi.objc.NSEventPhase
import org.graphiks.kadre.input.ScrollDelta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O2 proof for the scroll mapper and its coalescing frontier.
 *
 * The borrowed `scrollWheel:` callback itself belongs to the manual Phase 4 harness: AppKit
 * only delivers a scroll to a responder when a real pointing device produces one, so these tests
 * pin the conversion and the phase frontier that the native callback feeds.
 */
class AppKitScrollInputTest {
    @Test
    fun preciseTrackpadDeltasBecomeLogicalDeltas() {
        assertEquals(
            ScrollDelta.Logical(2.5, -7.25),
            sample(deltaX = -2.5, deltaY = 7.25, precise = true).toScrollDeltaOrNull(),
        )
    }

    @Test
    fun discreteWheelDeltasBecomeLineDeltas() {
        assertEquals(
            ScrollDelta.Lines(-1.0, 3.0),
            sample(deltaX = 1.0, deltaY = -3.0, precise = false).toScrollDeltaOrNull(),
        )
    }

    @Test
    fun everyAxisIsExposedInThePortableDirectionConvention() {
        val mapped = sample(deltaX = 4.0, deltaY = 6.0, precise = true).toScrollDeltaOrNull()
        assertEquals(ScrollDelta.Logical(-4.0, -6.0), mapped)
    }

    @Test
    fun anEventWithoutMovementOrWithANonFiniteDeltaProducesNoStimulus() {
        assertNull(sample(deltaX = 0.0, deltaY = -0.0).toScrollDeltaOrNull())
        assertNull(sample(deltaX = Double.NaN, deltaY = 1.0).toScrollDeltaOrNull())
        assertNull(sample(deltaX = 1.0, deltaY = Double.POSITIVE_INFINITY).toScrollDeltaOrNull())
    }

    @Test
    fun oneNativePhaseKeepsOneFrontierAndANewPhaseTakesAnother() {
        val boundary = AppKitScrollBoundary()
        val began = boundary.advance(sample(phase = NSEventPhase.NSEventPhaseBegan))
        val stillBegan = boundary.advance(
            sample(deltaY = 9.0, phase = NSEventPhase.NSEventPhaseBegan),
        )
        val changed = boundary.advance(sample(phase = NSEventPhase.NSEventPhaseChanged))

        assertEquals(began, stillBegan)
        assertTrue(changed > stillBegan, "a changed native phase must open a new frontier")
    }

    @Test
    fun momentumNeverSharesAFrontierWithANonMomentumScroll() {
        val boundary = AppKitScrollBoundary()
        val ended = boundary.advance(
            sample(phase = NSEventPhase.NSEventPhaseEnded, momentumPhase = NSEventPhase.NSEventPhaseNone),
        )
        val momentumBegan = boundary.advance(
            sample(phase = NSEventPhase.NSEventPhaseEnded, momentumPhase = NSEventPhase.NSEventPhaseBegan),
        )

        assertTrue(momentumBegan > ended, "momentum must not merge into the phase that started it")
    }

    @Test
    fun aClearedObserverNeverReusesAnAlreadyPublishedFrontier() {
        val boundary = AppKitScrollBoundary()
        val before = boundary.advance(sample(phase = NSEventPhase.NSEventPhaseBegan))

        boundary.clear()

        assertTrue(boundary.advance(sample(phase = NSEventPhase.NSEventPhaseBegan)) > before)
    }

    private fun sample(
        deltaX: Double = 0.0,
        deltaY: Double = 1.0,
        precise: Boolean = true,
        phase: NSEventPhase = NSEventPhase.NSEventPhaseNone,
        momentumPhase: NSEventPhase = NSEventPhase.NSEventPhaseNone,
    ): AppKitScrollSample = AppKitScrollSample(
        deltaX = deltaX,
        deltaY = deltaY,
        precise = precise,
        phase = phase,
        momentumPhase = momentumPhase,
    )
}
