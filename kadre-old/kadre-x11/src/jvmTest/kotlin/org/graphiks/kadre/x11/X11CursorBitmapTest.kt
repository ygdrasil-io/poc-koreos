package org.graphiks.kadre.x11

import org.graphiks.kadre.core.CursorImage
import org.graphiks.kadre.core.MAX_CURSOR_SIZE
import java.lang.foreign.Arena
import java.lang.foreign.ValueLayout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class X11CursorBitmapTest {

    @Test
    fun `width eight uses one XBM byte per row in LSB-first order`() {
        val packed = packMonochromeCursor(
            cursorImage(
                width = 8,
                height = 2,
                opaqueWhitePixels = setOf(0 to 0, 7 to 1),
            ),
        )

        assertContentEquals(byteArrayOf(0x01, 0x80.toByte()), packed.source)
        assertContentEquals(byteArrayOf(0x01, 0x80.toByte()), packed.mask)
    }

    @Test
    fun `width nine uses two XBM bytes per row without row spill`() {
        val packed = packMonochromeCursor(
            cursorImage(
                width = 9,
                height = 2,
                opaqueWhitePixels = setOf(8 to 0, 0 to 1),
            ),
        )

        assertContentEquals(byteArrayOf(0x00, 0x01, 0x01, 0x00), packed.source)
        assertContentEquals(byteArrayOf(0x00, 0x01, 0x01, 0x00), packed.mask)
    }

    @Test
    fun `alpha controls mask and masked luminance controls source`() {
        val packed = packMonochromeCursor(
            rgbaCursor(
                width = 4,
                height = 1,
                pixels = listOf(
                    intArrayOf(255, 255, 255, 0),
                    intArrayOf(255, 255, 255, 255),
                    intArrayOf(0, 0, 0, 255),
                    intArrayOf(0, 0, 0, 0),
                ),
            ),
        )

        assertContentEquals(byteArrayOf(0b0000_0010), packed.source)
        assertContentEquals(byteArrayOf(0b0000_0110), packed.mask)
    }

    @Test
    fun `packed size is row stride times height`() {
        val packed = packMonochromeCursor(cursorImage(width = 10, height = 3))

        assertEquals(6, packed.source.size)
        assertEquals(6, packed.mask.size)
    }

    @Test
    fun `XColor white uses exact LP64 layout and RGB flags`() {
        Arena.ofConfined().use { arena ->
            val color = arena.allocate(16L, 8L)
            color.fill(0xA5.toByte())

            writeXColor(color, UShort.MAX_VALUE, UShort.MAX_VALUE, UShort.MAX_VALUE)

            assertEquals(0L, color.get(ValueLayout.JAVA_LONG, 0L))
            assertEquals(UShort.MAX_VALUE, color.get(ValueLayout.JAVA_SHORT, 8L).toUShort())
            assertEquals(UShort.MAX_VALUE, color.get(ValueLayout.JAVA_SHORT, 10L).toUShort())
            assertEquals(UShort.MAX_VALUE, color.get(ValueLayout.JAVA_SHORT, 12L).toUShort())
            assertEquals(0x07, color.get(ValueLayout.JAVA_BYTE, 14L).toInt() and 0xFF)
            assertEquals(0, color.get(ValueLayout.JAVA_BYTE, 15L).toInt() and 0xFF)
        }
    }

    @Test
    fun `XColor black uses zero channels and exact LP64 layout`() {
        Arena.ofConfined().use { arena ->
            val color = arena.allocate(16L, 8L)
            color.fill(0xA5.toByte())

            writeXColor(color, 0u, 0u, 0u)

            assertEquals(0L, color.get(ValueLayout.JAVA_LONG, 0L))
            assertEquals(0u, color.get(ValueLayout.JAVA_SHORT, 8L).toUShort())
            assertEquals(0u, color.get(ValueLayout.JAVA_SHORT, 10L).toUShort())
            assertEquals(0u, color.get(ValueLayout.JAVA_SHORT, 12L).toUShort())
            assertEquals(0x07, color.get(ValueLayout.JAVA_BYTE, 14L).toInt() and 0xFF)
            assertEquals(0, color.get(ValueLayout.JAVA_BYTE, 15L).toInt() and 0xFF)
        }
    }

    @Test
    fun `cursor geometry rejects invalid hotspots`() {
        assertFalse(validateCursorGeometry(cursorImage(2, 2, hotspotX = -1), 2, 2))
        assertFalse(validateCursorGeometry(cursorImage(2, 2, hotspotX = 2), 2, 2))
        assertFalse(validateCursorGeometry(cursorImage(2, 2, hotspotY = -1), 2, 2))
        assertFalse(validateCursorGeometry(cursorImage(2, 2, hotspotY = 2), 2, 2))
        assertTrue(validateCursorGeometry(cursorImage(2, 2, hotspotX = 1, hotspotY = 1), 2, 2))
    }

    @Test
    fun `cursor geometry rejects Int wraparound with checked Long arithmetic`() {
        val overflowing = CursorImage(
            rgba = ByteArray(0),
            width = 1 shl 30,
            height = 4,
        )

        assertFalse(validateCursorGeometry(overflowing, Int.MAX_VALUE, Int.MAX_VALUE))
        assertFailsWith<IllegalArgumentException> { packMonochromeCursor(overflowing) }
    }

    @Test
    fun `client cursor cap rejects oversized geometry before native work`() {
        assertEquals(minOf(MAX_CURSOR_SIZE, 0xFFFF), X11_CURSOR_DIMENSION_LIMIT)
        val oversizedImages = listOf(
            cursorImage(width = MAX_CURSOR_SIZE + 1, height = 1),
            cursorImage(width = 65_536, height = 1),
        )
        var nativeCalls = 0

        oversizedImages.forEach { image ->
            val result = withValidX11CursorGeometry(image) {
                nativeCalls += 1
                "native cursor"
            }

            assertNull(result)
        }
        assertEquals(0, nativeCalls)
    }

    @Test
    fun `server cursor limits can reduce but never enlarge client cap`() {
        assertEquals(64, capServerCursorLimit(64u))
        assertEquals(X11_CURSOR_DIMENSION_LIMIT, capServerCursorLimit(UInt.MAX_VALUE))
    }

    @Test
    fun `cursor geometry rejects dimensions beyond server maximum`() {
        val image = cursorImage(width = 9, height = 7)

        assertFalse(validateCursorGeometry(image, maxWidth = 8, maxHeight = 7))
        assertFalse(validateCursorGeometry(image, maxWidth = 9, maxHeight = 6))
        assertTrue(validateCursorGeometry(image, maxWidth = 9, maxHeight = 7))
    }

    @Test
    fun `successful cursor wrapping transfers ownership without freeing`() {
        var frees = 0

        val wrapped = wrapOwnedX11Cursor(
            cursor = 42L,
            wrap = { cursor -> "cursor-$cursor" },
            free = { frees += 1 },
        )

        assertEquals("cursor-42", wrapped)
        assertEquals(0, frees)
    }

    @Test
    fun `failed cursor wrapping frees native cursor exactly once`() {
        val wrapperFailure = IllegalStateException("wrapper failed")
        var frees = 0

        val thrown = assertFailsWith<IllegalStateException> {
            wrapOwnedX11Cursor(
                cursor = 42L,
                wrap = { throw wrapperFailure },
                free = { cursor ->
                    assertEquals(42L, cursor)
                    frees += 1
                },
            )
        }

        assertSame(wrapperFailure, thrown)
        assertEquals(1, frees)
    }

    @Test
    fun `cursor cleanup failure is suppressed on wrapper failure`() {
        val wrapperFailure = IllegalStateException("wrapper failed")
        val cleanupFailure = IllegalArgumentException("cleanup failed")
        var frees = 0

        val thrown = assertFailsWith<IllegalStateException> {
            wrapOwnedX11Cursor(
                cursor = 42L,
                wrap = { throw wrapperFailure },
                free = {
                    frees += 1
                    throw cleanupFailure
                },
            )
        }

        assertSame(wrapperFailure, thrown)
        assertEquals(1, frees)
        assertEquals(listOf(cleanupFailure), thrown.suppressed.toList())
    }

    private fun cursorImage(
        width: Int,
        height: Int,
        opaqueWhitePixels: Set<Pair<Int, Int>> = emptySet(),
        hotspotX: Int = 0,
        hotspotY: Int = 0,
    ): CursorImage {
        val rgba = ByteArray(width * height * 4)
        for ((x, y) in opaqueWhitePixels) {
            val offset = (y * width + x) * 4
            rgba[offset] = 0xFF.toByte()
            rgba[offset + 1] = 0xFF.toByte()
            rgba[offset + 2] = 0xFF.toByte()
            rgba[offset + 3] = 0xFF.toByte()
        }
        return CursorImage(rgba, width, height, hotspotX, hotspotY)
    }

    private fun rgbaCursor(width: Int, height: Int, pixels: List<IntArray>): CursorImage {
        require(pixels.size == width * height)
        val rgba = ByteArray(pixels.size * 4)
        pixels.forEachIndexed { index, pixel ->
            require(pixel.size == 4)
            pixel.forEachIndexed { channel, value -> rgba[index * 4 + channel] = value.toByte() }
        }
        return CursorImage(rgba, width, height)
    }
}
