package org.graphiks.kadre.x11

import org.graphiks.kadre.core.CursorImage
import org.graphiks.kadre.core.MAX_CURSOR_SIZE
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

internal data class PackedCursor(
    val source: ByteArray,
    val mask: ByteArray,
)

internal val X11_CURSOR_DIMENSION_LIMIT: Int = minOf(MAX_CURSOR_SIZE, 0xFFFF)

internal inline fun <T> withValidX11CursorGeometry(image: CursorImage, block: () -> T): T? {
    if (!validateCursorGeometry(image, X11_CURSOR_DIMENSION_LIMIT, X11_CURSOR_DIMENSION_LIMIT)) return null
    return block()
}

internal fun capServerCursorLimit(serverLimit: UInt): Int =
    minOf(X11_CURSOR_DIMENSION_LIMIT.toLong(), serverLimit.toLong()).toInt()

internal fun <T : Any> wrapOwnedX11Cursor(
    cursor: Long,
    wrap: (Long) -> T,
    free: (Long) -> Unit,
): T {
    require(cursor != 0L) { "Cannot wrap a null X11 cursor" }
    return try {
        wrap(cursor)
    } catch (primary: Throwable) {
        try {
            free(cursor)
        } catch (cleanup: Throwable) {
            if (cleanup !== primary) primary.addSuppressed(cleanup)
        }
        throw primary
    }
}

internal fun packMonochromeCursor(image: CursorImage, alphaThreshold: Int = 1): PackedCursor {
    require(alphaThreshold in 1..255) { "alphaThreshold must be between 1 and 255" }
    require(validateCursorGeometry(image, Int.MAX_VALUE, Int.MAX_VALUE)) {
        "Invalid cursor image geometry or RGBA buffer"
    }

    val rowStride = ((image.width.toLong() + 7L) / 8L).toInt()
    val packedSize = Math.multiplyExact(rowStride.toLong(), image.height.toLong())
    require(packedSize <= Int.MAX_VALUE.toLong()) { "Packed cursor bitmap is too large" }

    val source = ByteArray(packedSize.toInt())
    val mask = ByteArray(packedSize.toInt())
    for (y in 0 until image.height) {
        for (x in 0 until image.width) {
            val pixelOffset = (y * image.width + x) * 4
            val alpha = image.rgba[pixelOffset + 3].toInt() and 0xFF
            if (alpha < alphaThreshold) continue

            val byteIndex = y * rowStride + x / 8
            val bit = 1 shl (x and 7)
            mask[byteIndex] = (mask[byteIndex].toInt() or bit).toByte()

            val red = image.rgba[pixelOffset].toInt() and 0xFF
            val green = image.rgba[pixelOffset + 1].toInt() and 0xFF
            val blue = image.rgba[pixelOffset + 2].toInt() and 0xFF
            if (red + green + blue > 128 * 3) {
                source[byteIndex] = (source[byteIndex].toInt() or bit).toByte()
            }
        }
    }
    return PackedCursor(source, mask)
}

internal fun validateCursorGeometry(image: CursorImage, maxWidth: Int, maxHeight: Int): Boolean {
    if (image.width <= 0 || image.height <= 0) return false
    if (maxWidth <= 0 || maxHeight <= 0) return false
    if (image.width > maxWidth || image.height > maxHeight) return false
    if (image.hotspotX !in 0 until image.width || image.hotspotY !in 0 until image.height) return false

    val rgbaSize = try {
        val pixelCount = Math.multiplyExact(image.width.toLong(), image.height.toLong())
        Math.multiplyExact(pixelCount, 4L)
    } catch (_: ArithmeticException) {
        return false
    }
    if (rgbaSize > Int.MAX_VALUE.toLong() || rgbaSize != image.rgba.size.toLong()) return false

    val rowStride = (image.width.toLong() + 7L) / 8L
    val packedSize = try {
        Math.multiplyExact(rowStride, image.height.toLong())
    } catch (_: ArithmeticException) {
        return false
    }
    return packedSize <= Int.MAX_VALUE.toLong()
}

internal fun writeXColor(segment: MemorySegment, red: UShort, green: UShort, blue: UShort) {
    require(segment.byteSize() >= X11_COLOR_SIZE_BYTES) { "XColor segment must be at least 16 bytes" }
    segment.asSlice(0L, X11_COLOR_SIZE_BYTES).fill(0)
    segment.set(ValueLayout.JAVA_SHORT, 8L, red.toShort())
    segment.set(ValueLayout.JAVA_SHORT, 10L, green.toShort())
    segment.set(ValueLayout.JAVA_SHORT, 12L, blue.toShort())
    segment.set(ValueLayout.JAVA_BYTE, 14L, X11_COLOR_RGB_FLAGS)
}

private const val X11_COLOR_RGB_FLAGS: Byte = 0x07
