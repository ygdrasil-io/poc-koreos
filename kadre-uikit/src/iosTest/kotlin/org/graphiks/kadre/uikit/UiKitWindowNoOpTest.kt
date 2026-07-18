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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        assertNotNull(UiKitWindow::class.simpleName, "UiKitWindow must be resolvable at compile time")
        assertEquals("UiKitWindow", UiKitWindow::class.simpleName)
    }

    // ── WindowRequestResult core types ─────────────────────────────────────

    @Test
    fun `WindowRequestResult Success is a data object`() {
        assertTrue(WindowRequestResult.Success is WindowRequestResult)
    }

    @Test
    fun `WindowRequestResult Failure wraps RequestError`() {
        val failure = WindowRequestResult.Failure(RequestError.Unsupported("test"))
        assertTrue(failure is WindowRequestResult)
        assertTrue(failure.error is RequestError.Unsupported)
    }

    // ── Method signature checks ────────────────────────────────────────────

    @Test
    fun `setCursor exists with CursorIcon parameter`() {
        val method: (UiKitWindow, CursorIcon) -> Unit = UiKitWindow::setCursor
        assertNotNull(method)
    }

    @Test
    fun `setCursorVisible exists with Boolean parameter`() {
        val method: (UiKitWindow, Boolean) -> Unit = UiKitWindow::setCursorVisible
        assertNotNull(method)
    }

    @Test
    fun `setCursorGrab returns WindowRequestResult`() {
        val method: (UiKitWindow, CursorGrabMode) -> WindowRequestResult = UiKitWindow::setCursorGrab
        assertNotNull(method)
    }

    @Test
    fun `setCursorPosition returns WindowRequestResult`() {
        val method: (UiKitWindow, PhysicalPosition<Int>) -> WindowRequestResult =
            UiKitWindow::setCursorPosition
        assertNotNull(method)
    }

    @Test
    fun `setCursorHittest returns WindowRequestResult`() {
        val method: (UiKitWindow, Boolean) -> WindowRequestResult = UiKitWindow::setCursorHittest
        assertNotNull(method)
    }

    @Test
    fun `setMinimized exists`() {
        val method: (UiKitWindow, Boolean) -> Unit = UiKitWindow::setMinimized
        assertNotNull(method)
    }

    @Test
    fun `setMaximized exists`() {
        val method: (UiKitWindow, Boolean) -> Unit = UiKitWindow::setMaximized
        assertNotNull(method)
    }

    @Test
    fun `setDecorations exists`() {
        val method: (UiKitWindow, Boolean) -> Unit = UiKitWindow::setDecorations
        assertNotNull(method)
    }

    @Test
    fun `setResizable exists`() {
        val method: (UiKitWindow, Boolean) -> Unit = UiKitWindow::setResizable
        assertNotNull(method)
    }

    @Test
    fun `setWindowLevel exists`() {
        val method: (UiKitWindow, WindowLevel) -> Unit = UiKitWindow::setWindowLevel
        assertNotNull(method)
    }

    @Test
    fun `setFullscreen exists`() {
        val method: (UiKitWindow, Fullscreen?) -> Unit = UiKitWindow::setFullscreen
        assertNotNull(method)
    }

    @Test
    fun `setTheme exists`() {
        val method: (UiKitWindow, Theme?) -> Unit = UiKitWindow::setTheme
        assertNotNull(method)
    }

    @Test
    fun `setTransparent exists`() {
        val method: (UiKitWindow, Boolean) -> Unit = UiKitWindow::setTransparent
        assertNotNull(method)
    }

    @Test
    fun `setBlur exists`() {
        val method: (UiKitWindow, Boolean) -> Unit = UiKitWindow::setBlur
        assertNotNull(method)
    }

    @Test
    fun `setWindowIcon exists`() {
        val method: (UiKitWindow, Icon?) -> Unit = UiKitWindow::setWindowIcon
        assertNotNull(method)
    }

    @Test
    fun `requestUserAttention returns WindowRequestResult`() {
        val method: (UiKitWindow, UserAttentionType?) -> WindowRequestResult =
            UiKitWindow::requestUserAttention
        assertNotNull(method)
    }

    @Test
    fun `setContentProtected returns WindowRequestResult`() {
        val method: (UiKitWindow, Boolean) -> WindowRequestResult = UiKitWindow::setContentProtected
        assertNotNull(method)
    }

    @Test
    fun `showWindowMenu returns WindowRequestResult`() {
        val method: (UiKitWindow, PhysicalPosition<Int>) -> WindowRequestResult =
            UiKitWindow::showWindowMenu
        assertNotNull(method)
    }

    @Test
    fun `dragWindow returns WindowRequestResult`() {
        val method: (UiKitWindow) -> WindowRequestResult = UiKitWindow::dragWindow
        assertNotNull(method)
    }

    @Test
    fun `dragResizeWindow returns WindowRequestResult`() {
        val method: (UiKitWindow, ResizeDirection) -> WindowRequestResult = UiKitWindow::dragResizeWindow
        assertNotNull(method)
    }

    @Test
    fun `setCustomCursor exists as no-op`() {
        val method: (UiKitWindow, CustomCursor) -> Unit = UiKitWindow::setCustomCursor
        assertNotNull(method)
    }

    @Test
    fun `all cursor no-op methods exist`() {
        val cursorMethods = listOf<Any>(
            UiKitWindow::setCursor,
            UiKitWindow::setCursorVisible,
            UiKitWindow::setCursorGrab,
            UiKitWindow::setCursorPosition,
            UiKitWindow::setCursorHittest,
        )
        assertEquals(5, cursorMethods.size)
    }

    @Test
    fun `all window management no-op methods exist`() {
        val mgmtMethods = listOf<Any>(
            UiKitWindow::dragWindow,
            UiKitWindow::dragResizeWindow,
            UiKitWindow::showWindowMenu,
        )
        assertEquals(3, mgmtMethods.size)
    }
}
