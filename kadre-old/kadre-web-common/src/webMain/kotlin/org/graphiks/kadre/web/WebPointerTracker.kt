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

/** Maps one PointerEvent from its real DOM identity fields without retaining state. */
internal fun domPointerSnapshot(
    pointerId: Long,
    pointerType: String,
    domPrimary: Boolean,
): WebPointerSnapshot = WebPointerSnapshot(
    pointerId = pointerId,
    primary = when (pointerType) {
        "mouse" -> true
        else -> domPrimary
    },
    source = when (pointerType) {
        "mouse" -> PointerSource.Mouse
        "touch" -> PointerSource.Touch(FingerId(pointerId))
        "pen" -> PointerSource.TabletTool(TabletToolKind.Pen)
        else -> PointerSource.Unknown
    },
    kind = when (pointerType) {
        "mouse" -> PointerKind.Mouse
        "touch" -> PointerKind.Touch
        "pen" -> PointerKind.TabletTool
        else -> PointerKind.Unknown
    },
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
    ): WebPointerSnapshot {
        val pointer = domPointerSnapshot(pointerId, pointerType, domPrimary)
        return if (pointerType == TOUCH) {
            pointer.copy(primary = activeTouchIds.firstOrNull() == pointerId)
        } else {
            pointer
        }
    }

    private companion object {
        const val TOUCH = "touch"
    }
}
