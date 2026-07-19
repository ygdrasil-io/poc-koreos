package org.graphiks.kadre.web

import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.PhysicalSize
import kotlin.test.Test
import kotlin.test.assertEquals

class DomCoordinateTransformTest {
    @Test
    fun `coordinates are canvas-relative physical pixels`() {
        val metrics = CanvasMetrics(
            leftCss = 10.0,
            topCss = 20.0,
            widthCss = 300.0,
            heightCss = 150.0,
            devicePixelRatio = 2.0,
        )

        assertEquals(
            PhysicalPosition(200.0, 100.0),
            metrics.toPhysical(clientX = 110.0, clientY = 70.0),
        )
    }

    @Test
    fun `negative canvas-relative coordinates are retained`() {
        val metrics = CanvasMetrics(10.0, 20.0, 300.0, 150.0, 2.0)

        assertEquals(
            PhysicalPosition(-10.0, -8.0),
            metrics.toPhysical(clientX = 5.0, clientY = 16.0),
        )
    }

    @Test
    fun `fractional origins and DPR retain Double precision`() {
        val metrics = CanvasMetrics(0.25, 0.5, 300.0, 150.0, 1.5)

        assertEquals(
            PhysicalPosition(15.75, 30.375),
            metrics.toPhysical(clientX = 10.75, clientY = 20.75),
        )
    }

    @Test
    fun `non-finite and non-positive DPR normalize to one`() {
        val invalidRatios = listOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            0.0,
            -2.0,
        )

        invalidRatios.forEach { invalidRatio ->
            val metrics = CanvasMetrics(10.0, 20.0, 20.6, 10.4, invalidRatio)
            assertEquals(
                PhysicalPosition(5.0, 6.0),
                metrics.toPhysical(clientX = 15.0, clientY = 26.0),
                "DPR $invalidRatio",
            )
            assertEquals(PhysicalSize(21, 10), metrics.physicalSize(), "DPR $invalidRatio")
        }
    }

    @Test
    fun `physical canvas size rounds scaled CSS dimensions`() {
        val metrics = CanvasMetrics(0.0, 0.0, 300.3, 150.2, 2.0)

        assertEquals(PhysicalSize(601, 300), metrics.physicalSize())
    }

    @Test
    fun `physical canvas size rounds exactly around half and saturation boundaries`() {
        assertEquals(0, physicalWidth(0.49999999999999994))
        assertEquals(1, physicalWidth(0.5))
        assertEquals(1, physicalWidth(0.5000000000000001))

        val saturationTie = Int.MAX_VALUE.toDouble() - 0.5
        val justBelowSaturationTie = Double.fromBits(saturationTie.toBits() - 1L)
        val justAboveSaturationTie = Double.fromBits(saturationTie.toBits() + 1L)

        assertEquals(Int.MAX_VALUE - 1, physicalWidth(justBelowSaturationTie))
        assertEquals(Int.MAX_VALUE, physicalWidth(saturationTie))
        assertEquals(Int.MAX_VALUE, physicalWidth(justAboveSaturationTie))
    }

    @Test
    fun `invalid and overflowing CSS dimensions have explicit bounds`() {
        assertEquals(
            PhysicalSize(0, 0),
            CanvasMetrics(0.0, 0.0, -1.0, Double.NaN, 2.0).physicalSize(),
        )
        assertEquals(
            PhysicalSize(Int.MAX_VALUE, Int.MAX_VALUE),
            CanvasMetrics(
                leftCss = 0.0,
                topCss = 0.0,
                widthCss = Double.POSITIVE_INFINITY,
                heightCss = Int.MAX_VALUE.toDouble(),
                devicePixelRatio = 2.0,
            ).physicalSize(),
        )
    }

    private fun physicalWidth(widthCss: Double): Int = CanvasMetrics(
        leftCss = 0.0,
        topCss = 0.0,
        widthCss = widthCss,
        heightCss = 0.0,
        devicePixelRatio = 1.0,
    ).physicalSize().width
}
