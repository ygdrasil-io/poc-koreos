package org.graphiks.kadre.x11.binding.capture

const val XIMAGE_DATA_OFFSET: Long = 16L
const val XIMAGE_BYTES_PER_LINE_OFFSET: Long = 44L
const val XWINDOWATTR_MAP_STATE_OFFSET: Long = 84L

const val XSHM_ZPIXMAP: Int = 2
const val IsViewable: Int = 2

fun bgraToRgba(data: ByteArray): ByteArray {
    val result = data.copyOf()
    var i = 0
    while (i + 4 <= result.size) {
        val b = result[i]
        val r = result[i + 2]
        result[i] = r
        result[i + 2] = b
        i += 4
    }
    return result
}
