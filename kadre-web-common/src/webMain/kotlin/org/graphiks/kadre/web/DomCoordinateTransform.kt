package org.graphiks.kadre.web

import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.PhysicalSize

internal data class CanvasMetrics(
    val leftCss: Double,
    val topCss: Double,
    val widthCss: Double,
    val heightCss: Double,
    val devicePixelRatio: Double,
)

internal fun CanvasMetrics.toPhysical(
    clientX: Double,
    clientY: Double,
): PhysicalPosition<Double> {
    val scale = normalizedDevicePixelRatio()
    return PhysicalPosition(
        x = (clientX - leftCss) * scale,
        y = (clientY - topCss) * scale,
    )
}

internal fun CanvasMetrics.physicalSize(): PhysicalSize<Int> {
    val scale = normalizedDevicePixelRatio()
    return PhysicalSize(
        width = physicalDimension(widthCss, scale),
        height = physicalDimension(heightCss, scale),
    )
}

private fun CanvasMetrics.normalizedDevicePixelRatio(): Double =
    devicePixelRatio.takeIf { it.isFinite() && it > 0.0 } ?: 1.0

private fun physicalDimension(cssDimension: Double, scale: Double): Int {
    val scaled = cssDimension * scale
    return when {
        scaled.isNaN() || scaled <= 0.0 -> 0
        scaled >= Int.MAX_VALUE.toDouble() - 0.5 -> Int.MAX_VALUE
        else -> (scaled + 0.5).toInt()
    }
}
