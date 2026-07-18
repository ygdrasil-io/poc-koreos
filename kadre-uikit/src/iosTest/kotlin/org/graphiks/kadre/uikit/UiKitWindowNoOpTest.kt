package org.graphiks.kadre.uikit

import org.graphiks.kadre.core.CursorGrabMode
import org.graphiks.kadre.core.CursorIcon
import org.graphiks.kadre.core.CustomCursor
import org.graphiks.kadre.core.Fullscreen
import org.graphiks.kadre.core.Icon
import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.RequestError
import org.graphiks.kadre.core.ResizeDirection
import org.graphiks.kadre.core.Theme
import org.graphiks.kadre.core.UserAttentionType
import org.graphiks.kadre.core.WindowLevel
import org.graphiks.kadre.core.WindowRequestResult
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Compile-time contracts for UIKit's documented no-op methods.
 *
 * These typed references intentionally have no runtime assertions: compilation
 * itself proves that every method keeps the expected receiver, parameters, and
 * return type on Kotlin/Native.
 */
@Suppress("unused")
private object UiKitWindowCompileTimeContracts {
    val setCursor: (UiKitWindow, CursorIcon) -> Unit = UiKitWindow::setCursor
    val setCursorVisible: (UiKitWindow, Boolean) -> Unit = UiKitWindow::setCursorVisible
    val setCursorGrab: (UiKitWindow, CursorGrabMode) -> WindowRequestResult = UiKitWindow::setCursorGrab
    val setCursorPosition: (UiKitWindow, PhysicalPosition<Int>) -> WindowRequestResult =
        UiKitWindow::setCursorPosition
    val setCursorHittest: (UiKitWindow, Boolean) -> WindowRequestResult = UiKitWindow::setCursorHittest
    val setMinimized: (UiKitWindow, Boolean) -> Unit = UiKitWindow::setMinimized
    val setMaximized: (UiKitWindow, Boolean) -> Unit = UiKitWindow::setMaximized
    val setDecorations: (UiKitWindow, Boolean) -> Unit = UiKitWindow::setDecorations
    val setResizable: (UiKitWindow, Boolean) -> Unit = UiKitWindow::setResizable
    val setWindowLevel: (UiKitWindow, WindowLevel) -> Unit = UiKitWindow::setWindowLevel
    val setFullscreen: (UiKitWindow, Fullscreen?) -> Unit = UiKitWindow::setFullscreen
    val setTheme: (UiKitWindow, Theme?) -> Unit = UiKitWindow::setTheme
    val setTransparent: (UiKitWindow, Boolean) -> Unit = UiKitWindow::setTransparent
    val setBlur: (UiKitWindow, Boolean) -> Unit = UiKitWindow::setBlur
    val setWindowIcon: (UiKitWindow, Icon?) -> Unit = UiKitWindow::setWindowIcon
    val requestUserAttention: (UiKitWindow, UserAttentionType?) -> WindowRequestResult =
        UiKitWindow::requestUserAttention
    val setContentProtected: (UiKitWindow, Boolean) -> WindowRequestResult =
        UiKitWindow::setContentProtected
    val showWindowMenu: (UiKitWindow, PhysicalPosition<Int>) -> WindowRequestResult =
        UiKitWindow::showWindowMenu
    val dragWindow: (UiKitWindow) -> WindowRequestResult = UiKitWindow::dragWindow
    val dragResizeWindow: (UiKitWindow, ResizeDirection) -> WindowRequestResult =
        UiKitWindow::dragResizeWindow
    val setCustomCursor: (UiKitWindow, CustomCursor) -> Unit = UiKitWindow::setCustomCursor
}

/**
 * No-op contract tests for [UiKitWindow] (Task 6 — C6).
 *
 * Verifies that all documented no-op methods exist and return the expected
 * [WindowRequestResult] types. Runtime no-op behaviour requires a real iOS
 * device or simulator; these tests validate the Kotlin-level types at compile
 * time and the result types in the common core types.
 */
class UiKitWindowNoOpTest {

    // ── Class identity ──────────────────────────────────────────────────────

    @Test
    fun `UiKitWindow class exists`() {
        assertEquals("UiKitWindow", UiKitWindow::class.simpleName)
    }

    @Test
    fun `WindowRequestResult Failure wraps RequestError`() {
        val error = RequestError.Unsupported("test")
        val failure = WindowRequestResult.Failure(error)

        assertEquals(error, failure.error)
    }
}
