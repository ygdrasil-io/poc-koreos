package org.graphiks.kadre.web

/**
 * Validates cursor metadata before either web backend allocates browser data.
 *
 * The pixel product is computed as [Long] and bounded before multiplication by
 * four, so malformed dimensions cannot overflow into an apparently valid RGBA
 * byte count.
 */
internal fun isValidCursorRgba(
    rgbaSize: Int,
    width: Int,
    height: Int,
    hotspotX: Int,
    hotspotY: Int,
): Boolean {
    if (width <= 0 || height <= 0) return false
    if (hotspotX !in 0 until width || hotspotY !in 0 until height) return false
    if (rgbaSize > Int.MAX_VALUE / CURSOR_ENCODING_CHARS_PER_BYTE) return false

    val pixelCount = width.toLong() * height.toLong()
    if (pixelCount > Int.MAX_VALUE.toLong() / RGBA_COMPONENT_COUNT) return false

    return rgbaSize.toLong() == pixelCount * RGBA_COMPONENT_COUNT
}

private const val RGBA_COMPONENT_COUNT = 4L
private const val CURSOR_ENCODING_CHARS_PER_BYTE = 2
