package org.graphiks.kadre.core

/**
 * Result of a platform windowing request.
 *
 * This mirrors winit's fallible window operations without using exceptions for
 * expected platform limitations.
 */
sealed interface WindowRequestResult {
    data object Success : WindowRequestResult
    data class Failure(val error: RequestError) : WindowRequestResult
}

sealed interface SurfaceSizeRequestResult {
    data class Applied(val size: PhysicalSize<Int>) : SurfaceSizeRequestResult
    data object Pending : SurfaceSizeRequestResult
    data class Failure(val error: RequestError) : SurfaceSizeRequestResult
}

sealed interface RequestError {
    val message: String

    data class Unsupported(override val message: String = "Unsupported on this platform") : RequestError
    data class PermissionDenied(override val message: String = "Permission denied") : RequestError
    data class OsError(override val message: String) : RequestError
}

data class Insets<T : Number>(
    val top: T,
    val right: T,
    val bottom: T,
    val left: T,
)

data class WindowButtons(val bits: Int) {
    fun contains(buttons: WindowButtons): Boolean = bits and buttons.bits == buttons.bits
    operator fun plus(buttons: WindowButtons): WindowButtons = WindowButtons(bits or buttons.bits)

    companion object {
        val NONE = WindowButtons(0)
        val CLOSE = WindowButtons(1 shl 0)
        val MINIMIZE = WindowButtons(1 shl 1)
        val MAXIMIZE = WindowButtons(1 shl 2)
        val ALL = CLOSE + MINIMIZE + MAXIMIZE
    }
}

enum class WindowLevel {
    AlwaysOnBottom,
    Normal,
    AlwaysOnTop,
}

enum class CursorIcon {
    Default,
    Pointer,
    Text,
    Crosshair,
    Move,
    ResizeNorth,
    ResizeSouth,
    ResizeEast,
    ResizeWest,
    ResizeNorthEast,
    ResizeNorthWest,
    ResizeSouthEast,
    ResizeSouthWest,
    NotAllowed,
    Grab,
    Grabbing,
    Wait,
    Progress,
    ColResize,
    RowResize,
}

sealed interface Cursor {
    data class Icon(val icon: CursorIcon) : Cursor
    data class Custom(val cursor: CustomCursor) : Cursor

    companion object {
        val Default: Cursor = Icon(CursorIcon.Default)
    }
}

enum class CursorGrabMode {
    None,
    Confined,
    Locked,
}

data class CursorImage(
    val rgba: ByteArray,
    val width: Int,
    val height: Int,
    val hotspotX: Int = 0,
    val hotspotY: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CursorImage) return false
        return width == other.width &&
            height == other.height &&
            hotspotX == other.hotspotX &&
            hotspotY == other.hotspotY &&
            rgba.contentEquals(other.rgba)
    }

    override fun hashCode(): Int {
        var result = rgba.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + hotspotX
        result = 31 * result + hotspotY
        return result
    }
}

data class CustomCursor(val id: Long)

data class Icon(
    val rgba: ByteArray,
    val width: Int,
    val height: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Icon) return false
        return width == other.width && height == other.height && rgba.contentEquals(other.rgba)
    }

    override fun hashCode(): Int {
        var result = rgba.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}

enum class Theme {
    Light,
    Dark,
}

enum class UserAttentionType {
    Critical,
    Informational,
}

enum class ResizeDirection {
    East,
    North,
    NorthEast,
    NorthWest,
    South,
    SouthEast,
    SouthWest,
    West,
}

interface MonitorHandle {
    val id: Long
    val name: String?
    val position: PhysicalPosition<Int>
    val scaleFactor: Double
    val currentVideoMode: VideoMode?
    val videoModes: List<VideoMode>
}

data class VideoMode(
    val size: PhysicalSize<Int>,
    val bitDepth: Int?,
    val refreshRateMilliHz: Int?,
)

sealed interface Fullscreen {
    data class Borderless(val monitor: MonitorHandle? = null) : Fullscreen
    data class Exclusive(val monitor: MonitorHandle, val videoMode: VideoMode) : Fullscreen
}

data class OwnedDisplayHandle(val rawDisplayHandle: RawDisplayHandle)
