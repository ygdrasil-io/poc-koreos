package org.graphiks.kadre.web

import org.graphiks.kadre.core.FingerId
import org.graphiks.kadre.core.PointerKind
import org.graphiks.kadre.core.PointerSource
import org.graphiks.kadre.core.TabletToolKind

internal data class WebPointerSnapshot(
    val pointerId: Long,
    val primary: Boolean,
    val source: PointerSource,
    val kind: PointerKind,
)

internal class WebPointerTracker {
    private val activeTouchIds = linkedSetOf<Long>()

    fun onStart(
        pointerId: Long,
        pointerType: String,
        domPrimary: Boolean,
    ): WebPointerSnapshot {
        if (pointerType == TOUCH) activeTouchIds += pointerId
        return snapshot(pointerId, pointerType, domPrimary)
    }

    fun onMove(
        pointerId: Long,
        pointerType: String,
        domPrimary: Boolean,
    ): WebPointerSnapshot = snapshot(pointerId, pointerType, domPrimary)

    fun onEnd(
        pointerId: Long,
        pointerType: String,
        domPrimary: Boolean,
    ): WebPointerSnapshot = removeAfterSnapshot(pointerId, pointerType, domPrimary)

    fun onCancel(
        pointerId: Long,
        pointerType: String,
        domPrimary: Boolean,
    ): WebPointerSnapshot = removeAfterSnapshot(pointerId, pointerType, domPrimary)

    fun onLeave(
        pointerId: Long,
        pointerType: String,
        domPrimary: Boolean,
    ): WebPointerSnapshot = removeAfterSnapshot(pointerId, pointerType, domPrimary)

    fun close() {
        activeTouchIds.clear()
    }

    private fun removeAfterSnapshot(
        pointerId: Long,
        pointerType: String,
        domPrimary: Boolean,
    ): WebPointerSnapshot {
        val result = snapshot(pointerId, pointerType, domPrimary)
        if (pointerType == TOUCH) activeTouchIds -= pointerId
        return result
    }

    private fun snapshot(
        pointerId: Long,
        pointerType: String,
        domPrimary: Boolean,
    ): WebPointerSnapshot = WebPointerSnapshot(
        pointerId = pointerId,
        primary = when (pointerType) {
            MOUSE -> true
            TOUCH -> activeTouchIds.firstOrNull() == pointerId
            else -> domPrimary
        },
        source = when (pointerType) {
            MOUSE -> PointerSource.Mouse
            TOUCH -> PointerSource.Touch(FingerId(pointerId))
            PEN -> PointerSource.TabletTool(TabletToolKind.Pen)
            else -> PointerSource.Unknown
        },
        kind = when (pointerType) {
            MOUSE -> PointerKind.Mouse
            TOUCH -> PointerKind.Touch
            PEN -> PointerKind.TabletTool
            else -> PointerKind.Unknown
        },
    )

    private companion object {
        const val MOUSE = "mouse"
        const val TOUCH = "touch"
        const val PEN = "pen"
    }
}
