/**
 * Window configuration attributes.
 *
 * Scope: pure Kotlin types, no native reference.
 */
package org.graphiks.kadre.core

/**
 * Window creation parameters.
 *
 * @property title Title shown in the window's title bar.
 * @property size Initial surface size in physical pixels, or null to use the default size.
 * @property visible Indicates whether the window is visible at the time of its creation.
 * @property resizable Indicates whether the user can resize the window.
 */
data class WindowAttributes(
    val title: String = "Kadre",
    val size: PhysicalSize<Int>? = null,
    val minSize: PhysicalSize<Int>? = null,
    val maxSize: PhysicalSize<Int>? = null,
    val resizeIncrements: PhysicalSize<Int>? = null,
    val position: PhysicalPosition<Int>? = null,
    val visible: Boolean = true,
    val resizable: Boolean = true,
    val enabledButtons: WindowButtons = WindowButtons.ALL,
    val maximized: Boolean = false,
    val transparent: Boolean = false,
    val blur: Boolean = false,
    val decorations: Boolean = true,
    val windowIcon: Icon? = null,
    val preferredTheme: Theme? = null,
    val contentProtected: Boolean = false,
    val windowLevel: WindowLevel = WindowLevel.Normal,
    val active: Boolean = true,
    val cursor: Cursor = Cursor.Default,
    val parentWindow: RawWindowHandle? = null,
    val fullscreen: Fullscreen? = null,
)
