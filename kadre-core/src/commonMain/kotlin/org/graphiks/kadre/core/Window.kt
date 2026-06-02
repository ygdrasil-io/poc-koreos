/**
 * Interface representing a native window managed by kadre.
 *
 * Scope: pure Kotlin interface, no native reference.
 */
package org.graphiks.kadre.core

/**
 * Abstraction of a native window created by the event loop.
 *
 * The concrete implementations are provided by the platform modules
 * (kadre-appkit, etc.).
 */
interface Window {

    /** Unique identifier of the window. */
    val id: WindowId

    /**
     * Returns the native handle of the rendering surface.
     */
    val rawWindowHandle: RawWindowHandle

    /**
     * Returns the native handle of the display.
     */
    val rawDisplayHandle: RawDisplayHandle

    /**
     * Requests a redraw of the window at the next iteration.
     */
    fun requestRedraw()

    /**
     * Sets the title shown in the window's title bar.
     *
     * @param title New title of the window.
     */
    fun setTitle(title: String)

    /**
     * Returns the current title when the backend can query or cache it.
     */
    val title: String get() = ""

    /**
     * Returns the inner size of the window in physical pixels
     * (rendering surface, without the decorations).
     */
    val innerSize: PhysicalSize<Int>

    /**
     * Returns the outer size of the window in physical pixels
     * (rendering surface plus the platform decorations).
     */
    val outerSize: PhysicalSize<Int>

    /**
     * Returns the size of the renderable surface.
     */
    val surfaceSize: PhysicalSize<Int> get() = innerSize

    /**
     * Requests a new renderable surface size.
     */
    fun requestSurfaceSize(size: PhysicalSize<Int>): SurfaceSizeRequestResult =
        SurfaceSizeRequestResult.Failure(RequestError.Unsupported("Surface resizing is unsupported by this window"))

    /**
     * Returns the surface position relative to the outer window.
     */
    val surfacePosition: PhysicalPosition<Int> get() = PhysicalPosition(0, 0)

    /**
     * Returns the outer position in desktop coordinates, if known.
     */
    val outerPosition: PhysicalPosition<Int>? get() = null

    /**
     * Sets the outer position in desktop coordinates.
     */
    fun setOuterPosition(position: PhysicalPosition<Int>): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Window positioning is unsupported by this window"))

    /**
     * Insets of the unobstructed area inside [surfaceSize].
     */
    val safeArea: Insets<Int> get() = Insets(0, 0, 0, 0)

    fun setMinSurfaceSize(size: PhysicalSize<Int>?): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Minimum surface size is unsupported by this window"))

    fun setMaxSurfaceSize(size: PhysicalSize<Int>?): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Maximum surface size is unsupported by this window"))

    val surfaceResizeIncrements: PhysicalSize<Int>? get() = null

    fun setSurfaceResizeIncrements(increments: PhysicalSize<Int>?): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Surface resize increments are unsupported by this window"))

    /**
     * Returns the scale factor between logical and physical pixels
     * for this window.
     */
    val scaleFactor: Double

    /**
     * Makes the window visible or invisible.
     *
     * @param visible true to show the window, false to hide it.
     */
    fun setVisible(visible: Boolean)

    /**
     * Returns visibility if the platform can determine it; null means unknown.
     */
    val isVisible: Boolean? get() = null

    fun setResizable(resizable: Boolean): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Resizable state is unsupported by this window"))

    val isResizable: Boolean get() = true

    fun setEnabledButtons(buttons: WindowButtons): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Window buttons are unsupported by this window"))

    val enabledButtons: WindowButtons get() = WindowButtons.ALL

    fun setMinimized(minimized: Boolean): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Minimized state is unsupported by this window"))

    val isMinimized: Boolean? get() = null

    fun setMaximized(maximized: Boolean): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Maximized state is unsupported by this window"))

    val isMaximized: Boolean get() = false

    fun setFullscreen(fullscreen: Fullscreen?): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Fullscreen is unsupported by this window"))

    val fullscreen: Fullscreen? get() = null

    fun setDecorations(decorated: Boolean): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Decorations are unsupported by this window"))

    val isDecorated: Boolean get() = true

    fun setWindowLevel(level: WindowLevel): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Window level is unsupported by this window"))

    fun setWindowIcon(icon: Icon?): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Window icons are unsupported by this window"))

    fun focusWindow(): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Focusing windows is unsupported by this window"))

    val hasFocus: Boolean get() = false

    fun requestUserAttention(requestType: UserAttentionType?): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("User attention requests are unsupported by this window"))

    fun setContentProtected(protected: Boolean): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Content protection is unsupported by this window"))

    fun setCursor(cursor: Cursor): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Cursor changes are unsupported by this window"))

    fun setCursorPosition(position: PhysicalPosition<Int>): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Cursor positioning is unsupported by this window"))

    fun setCursorGrab(mode: CursorGrabMode): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Cursor grab is unsupported by this window"))

    fun setCursorVisible(visible: Boolean): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Cursor visibility is unsupported by this window"))

    fun setCursorHittest(hittest: Boolean): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Cursor hittest is unsupported by this window"))

    fun dragWindow(): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Window dragging is unsupported by this window"))

    fun dragResizeWindow(direction: ResizeDirection): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Window drag-resize is unsupported by this window"))

    fun showWindowMenu(position: PhysicalPosition<Int>): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Window menus are unsupported by this window"))

    /**
     * Closes the window.
     *
     * Once closed, the window no longer emits events and its identifier
     * becomes invalid.
     */
    fun close()
}
