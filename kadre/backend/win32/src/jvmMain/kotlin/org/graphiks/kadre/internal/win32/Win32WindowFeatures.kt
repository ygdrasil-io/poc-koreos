package org.graphiks.kadre.internal.win32

import org.graphiks.kadre.surface.*
import org.graphiks.kffi.win32.generated.IsWindow
import org.graphiks.kffi.win32.managed.Win32OwnedImage
import org.graphiks.kffi.win32.managed.Win32PointerConfinement
import org.graphiks.kffi.win32.managed.Win32WindowOperations
import java.lang.foreign.MemorySegment

internal val WIN32_CURSOR_RESOURCES: Map<CursorIcon, Int> = mapOf(
    CursorIcon.Default to 32512, CursorIcon.Pointer to 32649, CursorIcon.Text to 32513,
    CursorIcon.Crosshair to 32515, CursorIcon.Move to 32646, CursorIcon.NotAllowed to 32648,
    CursorIcon.Wait to 32514, CursorIcon.Progress to 32650, CursorIcon.Help to 32651,
    CursorIcon.ResizeHorizontal to 32644, CursorIcon.ResizeVertical to 32645,
    CursorIcon.ResizeDiagonalNorthWestSouthEast to 32642, CursorIcon.ResizeDiagonalNorthEastSouthWest to 32643,
)

/** UI-owned handles. Icon storage outlives the HWND, and shared cursors are never destroyed. */
internal class Win32WindowFeatures(private val hwnd: Long) : AutoCloseable {
    private data class Icon(val native: Win32OwnedImage, val image: BinaryImage)
    private val icons = mutableMapOf<Long, Icon>()
    private var cursor: Win32OwnedImage? = null
    private var cursorHandle: Long = Win32WindowOperations.systemCursor(32512)
    private val confinement = Win32PointerConfinement(hwnd)
    private var confined = false
    private var onConfinementLost: () -> Unit = {}

    fun icon(): BinaryImage? {
        val handle = Win32WindowOperations.readIcon(hwnd)
        return if (handle == 0L) null else checkNotNull(icons[handle]) { "unowned native icon readback" }.image
    }
    fun setIcon(image: BinaryImage?) {
        val next = image?.let { value ->
            val size = checkNotNull(value.pixelSize)
            require(value.format == ImageFormat.Rgba8)
            Icon(Win32OwnedImage.icon(size.width, size.height, value.bytes), value).also { icons[it.native.handle] = it }
        }
        try { Win32WindowOperations.setIcon(hwnd, next?.native?.handle ?: 0L) }
        finally {
            // A reentrant destroy or a failed second WM_SETICON must not free a still-installed icon.
            val installed = if (IsWindow(MemorySegment.ofAddress(hwnd)) != 0) Win32WindowOperations.iconSlots(hwnd) else emptySet()
            icons.keys.toList().filter { it !in installed }.forEach { icons.remove(it)?.native?.close() }
        }
    }
    fun setCursor(style: CursorStyle) {
        var next: Win32OwnedImage? = null
        val handle = when (style) {
            CursorStyle.Hidden -> 0L
            is CursorStyle.System -> Win32WindowOperations.systemCursor(checkNotNull(WIN32_CURSOR_RESOURCES[style.icon]))
            is CursorStyle.Custom -> {
                val value = style.image
                val size = checkNotNull(value.image.pixelSize)
                require(value.image.format == ImageFormat.Rgba8)
                Win32OwnedImage.cursor(size.width, size.height, value.image.bytes, value.hotspot.x, value.hotspot.y).also { next = it }.handle
            }
        }
        val old = cursor
        val wasCurrent = old != null && Win32WindowOperations.currentCursor() == old.handle
        cursor = next; cursorHandle = handle
        if (wasCurrent) Win32WindowOperations.setCursor(handle)
        old?.close()
    }
    fun applyCursor() { Win32WindowOperations.setCursor(cursorHandle) }
    fun observeLoss(observer: () -> Unit) { onConfinementLost = observer }
    fun confine(enabled: Boolean): Boolean {
        if (!enabled) { confinement.release(); confined = false; return true }
        confined = confinement.acquire()
        return confined
    }
    fun refreshConfinement(): Boolean {
        if (!confined) return false
        if (!confinement.update()) { confined = false; onConfinementLost(); return false }
        return true
    }
    fun pollConfinement() {
        if (confined && !confinement.isActive()) { confined = false; onConfinementLost() }
    }
    fun beforeDestroy() {
        confine(false)
        if (IsWindow(MemorySegment.ofAddress(hwnd)) != 0) Win32WindowOperations.requestAttention(hwnd, 0)
    }
    override fun close() {
        confinement.close()
        cursor?.close(); cursor = null
        icons.values.forEach { it.native.close() }; icons.clear()
    }
}
