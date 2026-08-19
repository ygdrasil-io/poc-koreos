package org.graphiks.kadre.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebCursorRgbaTest {

    @Test
    fun `pure RGBA widening preserves all unsigned component values`() {
        assertEquals(
            listOf(0, 127, 128, 255),
            platformWidenCursorRgba(byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte())),
        )
    }

    @Test
    fun `real browser cursor widens signed RGBA bytes without changing bits`() {
        val result = platformCursorRgbaProbe(
            rgba = byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte()),
            width = 1,
            height = 1,
            hotspotX = 0,
            hotspotY = 0,
            blockCanvasCreation = false,
        )

        assertEquals(1, result.imageDataCreationCount)
        assertTrue(result.documentCreateElementRestored, "document.createElement descriptor was not restored")
        assertEquals(
            listOf(0, 127, 128, 255),
            result.imageDataRgba,
            "cursor URL was '${result.dataUrl}'",
        )
        assertTrue(result.dataUrl.startsWith("data:image/png"), "cursor URL was '${result.dataUrl}'")
    }

    @Test
    fun `invalid cursor metadata and byte counts are rejected before browser allocation`() {
        val invalidInputs = listOf(
            InvalidCursorInput("zero width", byteArrayOf(), 0, 1, 0, 0),
            InvalidCursorInput("negative height", byteArrayOf(), 1, -1, 0, 0),
            InvalidCursorInput("negative hotspot x", byteArrayOf(0, 0, 0, 0), 1, 1, -1, 0),
            InvalidCursorInput("hotspot x at width", byteArrayOf(0, 0, 0, 0), 1, 1, 1, 0),
            InvalidCursorInput("negative hotspot y", byteArrayOf(0, 0, 0, 0), 1, 1, 0, -1),
            InvalidCursorInput("hotspot y at height", byteArrayOf(0, 0, 0, 0), 1, 1, 0, 1),
            InvalidCursorInput("missing RGBA byte", byteArrayOf(0, 0, 0), 1, 1, 0, 0),
            InvalidCursorInput("extra RGBA byte", byteArrayOf(0, 0, 0, 0, 0), 1, 1, 0, 0),
            InvalidCursorInput("overflowing RGBA byte count", byteArrayOf(), Int.MAX_VALUE, Int.MAX_VALUE, 0, 0),
        )

        invalidInputs.forEach { input ->
            val result = platformCursorRgbaProbe(
                rgba = input.rgba,
                width = input.width,
                height = input.height,
                hotspotX = input.hotspotX,
                hotspotY = input.hotspotY,
                blockCanvasCreation = true,
            )

            assertEquals("", result.dataUrl, input.name)
            assertEquals(0, result.canvasCreationCount, "${input.name} reached canvas allocation")
            assertEquals(0, result.imageDataCreationCount, "${input.name} reached ImageData")
            assertEquals(emptyList(), result.imageDataRgba, input.name)
            assertTrue(result.documentCreateElementRestored, "${input.name} did not restore document.createElement")
        }
    }
}

internal data class CursorRgbaProbeResult(
    val dataUrl: String,
    val canvasCreationCount: Int,
    val imageDataCreationCount: Int,
    val imageDataRgba: List<Int>,
    val documentCreateElementRestored: Boolean = false,
)

internal expect fun platformCursorRgbaProbe(
    rgba: ByteArray,
    width: Int,
    height: Int,
    hotspotX: Int,
    hotspotY: Int,
    blockCanvasCreation: Boolean,
): CursorRgbaProbeResult

internal expect fun platformWidenCursorRgba(rgba: ByteArray): List<Int>

private data class InvalidCursorInput(
    val name: String,
    val rgba: ByteArray,
    val width: Int,
    val height: Int,
    val hotspotX: Int,
    val hotspotY: Int,
)
