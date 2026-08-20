package org.graphiks.kadre.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import org.graphiks.kadre.core.Insets

class UIKitSafeAreaTest {
    @Test
    fun physicalInsetScalesPointsAndRoundsToTheNearestPhysicalPixel() {
        assertEquals(31, physicalInset(points = 10.25, scale = 3.0))
        assertEquals(2, physicalInset(points = 0.5, scale = 3.0))
        assertEquals(1, physicalInset(points = 0.49, scale = 3.0))
    }

    @Test
    fun everySafeAreaEdgeUsesTheSamePhysicalPixelRounding() {
        assertEquals(
            Insets(top = 1, bottom = 2, left = 3, right = 4),
            physicalSafeArea(
                topPoints = 0.25,
                bottomPoints = 0.5,
                leftPoints = 0.9,
                rightPoints = 1.25,
                scale = 3.0,
            ),
        )
    }

    @Test
    fun currentScaleIsAppliedEveryTimeWithoutRecreatingTheWindow() {
        val points = 10.25

        assertEquals(21, physicalInset(points, scale = 2.0))
        assertEquals(31, physicalInset(points, scale = 3.0))
    }

    @Test
    fun invalidOrNegativePointValuesProduceZero() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.01).forEach { points ->
            assertEquals(0, physicalInset(points, scale = 3.0), "points=$points")
        }
        assertEquals(0, physicalInset(points = 0.0, scale = 3.0))
    }

    @Test
    fun invalidOrNegativeScalesProduceZero() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.01).forEach { scale ->
            assertEquals(0, physicalInset(points = 10.25, scale = scale), "scale=$scale")
        }
        assertEquals(0, physicalInset(points = 10.25, scale = 0.0))
    }

    @Test
    fun positiveProductOverflowAndFiniteOversizeSaturateWhileNormalValuesRound() {
        assertEquals(Int.MAX_VALUE, physicalInset(points = Double.MAX_VALUE, scale = 2.0))
        assertEquals(
            Int.MAX_VALUE,
            physicalInset(points = Int.MAX_VALUE.toDouble() + 1_024.0, scale = 1.0),
        )
        assertEquals(31, physicalInset(points = 10.25, scale = 3.0))
    }
}
