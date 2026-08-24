package org.graphiks.kadre.surface

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeometryTest {
    @Test
    fun canonicalizesNegativeZero() {
        val negative = LogicalPoint(-0.0, -0.0)
        val positive = LogicalPoint(0.0, 0.0)

        assertEquals(positive, negative)
        assertEquals(positive.hashCode(), negative.hashCode())
        assertEquals(0.0.toBits(), negative.x.toBits())
        assertEquals(0.0.toBits(), negative.y.toBits())
    }

    @Test
    fun rejectsNonFiniteGeometryAndInvalidSizes() {
        assertFailsWith<IllegalArgumentException> { LogicalPoint(Double.NaN, 0.0) }
        assertFailsWith<IllegalArgumentException> { LogicalSize(0.0, 1.0) }
        assertFailsWith<IllegalArgumentException> { LogicalInsets(-1.0, 0.0, 0.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { PhysicalSize(1, 0) }
    }

    @Test
    fun appliesTheDocumentedRoundingModes() {
        assertEquals(
            PhysicalPoint(2, 4),
            LogicalPoint(1.25, 1.75).toPhysical(scaleFactor = 2.0),
        )
        assertEquals(
            PhysicalPoint(-1, 1),
            LogicalPoint(-1.9, 1.9).toPhysical(1.0, PixelRounding.TowardZero),
        )
        assertEquals(
            PhysicalSize(3, 4),
            LogicalSize(1.1, 1.6).toPhysical(scaleFactor = 2.0),
        )
    }

    @Test
    fun rejectsInvalidScaleAndIntegerOverflow() {
        assertFailsWith<IllegalArgumentException> { LogicalPoint(1.0, 1.0).toPhysical(0.0) }
        assertFailsWith<IllegalArgumentException> {
            LogicalPoint(Int.MAX_VALUE.toDouble(), 1.0).toPhysical(2.0)
        }
    }

    @Test
    fun binaryImageOwnsItsBytes() {
        val bytes = ByteArray(4) { it.toByte() }
        val image = BinaryImage(bytes, ImageFormat.Rgba8, PhysicalSize(1, 1))

        bytes[0] = 99
        assertEquals(0, image.bytes[0])
        val read = image.bytes
        read[1] = 99
        assertContentEquals(byteArrayOf(0, 1, 2, 3), image.bytes)
    }

    @Test
    fun validatesRgbaLengthAndCursorHotspot() {
        assertFailsWith<IllegalArgumentException> {
            BinaryImage(ByteArray(3), ImageFormat.Rgba8, PhysicalSize(1, 1))
        }
        val image = BinaryImage(ByteArray(4), ImageFormat.Rgba8, PhysicalSize(1, 1))
        assertFailsWith<IllegalArgumentException> {
            CursorImage(image, PhysicalPoint(1, 0))
        }
    }
}
