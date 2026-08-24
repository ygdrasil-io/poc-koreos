package org.graphiks.kadre.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class CaptureValuesTest {
    @Test
    fun cadenceAndOpaqueFormatsRejectInvalidValues() {
        assertFailsWith<IllegalArgumentException> { CaptureCadence.Fixed(Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { CaptureCadence.Variable(null, null) }
        assertFailsWith<IllegalArgumentException> { CaptureCadence.Variable(2.seconds, 1.seconds) }
        assertFailsWith<IllegalArgumentException> { PixelFormat.Opaque("", 1) }
        assertFailsWith<IllegalArgumentException> { PixelFormat.Opaque("native", 0) }
    }

    @Test
    fun hdrAndPlaneLayoutsRejectContradictions() {
        assertFailsWith<IllegalArgumentException> { HdrMetadata.Static(null, null, null) }
        assertFailsWith<IllegalArgumentException> {
            HdrMetadata.Static(null, maximumContentLightLevelNits = 100.0, maximumFrameAverageLightLevelNits = 101.0)
        }
        assertFailsWith<IllegalArgumentException> {
            PixelPlaneLayout(10, 10, rowStride = 5, pixelStride = 1, byteCount = 100, 1, 1)
        }
    }

    @Test
    fun chromaticityCanonicalizesNegativeZero() {
        val negative = Chromaticity(-0.0, -0.0)
        val positive = Chromaticity(0.0, 0.0)

        assertEquals(positive, negative)
        assertEquals(positive.hashCode(), negative.hashCode())
    }

    @Test
    fun luminanceValuesCanonicalizeNegativeZero() {
        val metadata = MasteringDisplayMetadata(
            red = Chromaticity(0.1, 0.1),
            green = Chromaticity(0.2, 0.2),
            blue = Chromaticity(0.3, 0.3),
            whitePoint = Chromaticity(0.4, 0.4),
            minimumLuminanceNits = -0.0,
            maximumLuminanceNits = 1.0,
        )
        val hdr = HdrMetadata.Static(
            masteringDisplay = null,
            maximumContentLightLevelNits = -0.0,
            maximumFrameAverageLightLevelNits = -0.0,
        )

        assertEquals(0.0.toBits(), metadata.minimumLuminanceNits.toBits())
        assertEquals(0.0.toBits(), hdr.maximumContentLightLevelNits?.toBits())
        assertEquals(0.0.toBits(), hdr.maximumFrameAverageLightLevelNits?.toBits())
    }
}
