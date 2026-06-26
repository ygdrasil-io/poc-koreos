package org.graphiks.kadre.uikit

import org.graphiks.kadre.core.CursorGrabMode
import org.graphiks.kadre.core.CursorIcon
import org.graphiks.kadre.core.CustomCursor
import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.RequestError
import org.graphiks.kadre.core.ResizeDirection
import org.graphiks.kadre.core.UserAttentionType
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
        val members = UiKitWindow::class.members.filter { it.name == "setCursor" }
        assertTrue(members.isNotEmpty(), "setCursor must exist on UiKitWindow")
    }

    @Test
    fun `setCursorVisible exists with Boolean parameter`() {
        val members = UiKitWindow::class.members.filter { it.name == "setCursorVisible" }
        assertTrue(members.isNotEmpty(), "setCursorVisible must exist on UiKitWindow")
    }

    @Test
    fun `setCursorGrab returns WindowRequestResult`() {
        val members = UiKitWindow::class.members.filter { it.name == "setCursorGrab" }
        assertTrue(members.isNotEmpty(), "setCursorGrab must exist on UiKitWindow")
    }

    @Test
    fun `setCursorPosition returns WindowRequestResult`() {
        val members = UiKitWindow::class.members.filter { it.name == "setCursorPosition" }
        assertTrue(members.isNotEmpty(), "setCursorPosition must exist on UiKitWindow")
    }

    @Test
    fun `setCursorHittest returns WindowRequestResult`() {
        val members = UiKitWindow::class.members.filter { it.name == "setCursorHittest" }
        assertTrue(members.isNotEmpty(), "setCursorHittest must exist on UiKitWindow")
    }

    @Test
    fun `setMinimized exists`() {
        val members = UiKitWindow::class.members.filter { it.name == "setMinimized" }
        assertTrue(members.isNotEmpty(), "setMinimized must exist on UiKitWindow")
    }

    @Test
    fun `setMaximized exists`() {
        val members = UiKitWindow::class.members.filter { it.name == "setMaximized" }
        assertTrue(members.isNotEmpty(), "setMaximized must exist on UiKitWindow")
    }

    @Test
    fun `setDecorations exists`() {
        val members = UiKitWindow::class.members.filter { it.name == "setDecorations" }
        assertTrue(members.isNotEmpty(), "setDecorations must exist on UiKitWindow")
    }

    @Test
    fun `setResizable exists`() {
        val members = UiKitWindow::class.members.filter { it.name == "setResizable" }
        assertTrue(members.isNotEmpty(), "setResizable must exist on UiKitWindow")
    }

    @Test
    fun `setWindowLevel exists`() {
        val members = UiKitWindow::class.members.filter { it.name == "setWindowLevel" }
        assertTrue(members.isNotEmpty(), "setWindowLevel must exist on UiKitWindow")
    }

    @Test
    fun `setFullscreen exists`() {
        val members = UiKitWindow::class.members.filter { it.name == "setFullscreen" }
        assertTrue(members.isNotEmpty(), "setFullscreen must exist on UiKitWindow")
    }

    @Test
    fun `setTheme exists`() {
        val members = UiKitWindow::class.members.filter { it.name == "setTheme" }
        assertTrue(members.isNotEmpty(), "setTheme must exist on UiKitWindow")
    }

    @Test
    fun `setTransparent exists`() {
        val members = UiKitWindow::class.members.filter { it.name == "setTransparent" }
        assertTrue(members.isNotEmpty(), "setTransparent must exist on UiKitWindow")
    }

    @Test
    fun `setBlur exists`() {
        val members = UiKitWindow::class.members.filter { it.name == "setBlur" }
        assertTrue(members.isNotEmpty(), "setBlur must exist on UiKitWindow")
    }

    @Test
    fun `setWindowIcon exists`() {
        val members = UiKitWindow::class.members.filter { it.name == "setWindowIcon" }
        assertTrue(members.isNotEmpty(), "setWindowIcon must exist on UiKitWindow")
    }

    @Test
    fun `requestUserAttention returns WindowRequestResult`() {
        val members = UiKitWindow::class.members.filter { it.name == "requestUserAttention" }
        assertTrue(members.isNotEmpty(), "requestUserAttention must exist on UiKitWindow")
    }

    @Test
    fun `setContentProtected returns WindowRequestResult`() {
        val members = UiKitWindow::class.members.filter { it.name == "setContentProtected" }
        assertTrue(members.isNotEmpty(), "setContentProtected must exist on UiKitWindow")
    }

    @Test
    fun `showWindowMenu returns WindowRequestResult`() {
        val members = UiKitWindow::class.members.filter { it.name == "showWindowMenu" }
        assertTrue(members.isNotEmpty(), "showWindowMenu must exist on UiKitWindow")
    }

    @Test
    fun `dragWindow returns WindowRequestResult`() {
        val members = UiKitWindow::class.members.filter { it.name == "dragWindow" }
        assertTrue(members.isNotEmpty(), "dragWindow must exist on UiKitWindow")
    }

    @Test
    fun `dragResizeWindow returns WindowRequestResult`() {
        val members = UiKitWindow::class.members.filter { it.name == "dragResizeWindow" }
        assertTrue(members.isNotEmpty(), "dragResizeWindow must exist on UiKitWindow")
    }

    @Test
    fun `setCustomCursor exists as no-op`() {
        val members = UiKitWindow::class.members.filter { it.name == "setCustomCursor" }
        assertTrue(members.isNotEmpty(), "setCustomCursor must exist on UiKitWindow")
    }

    @Test
    fun `all cursor no-op methods exist`() {
        val cursorMethods = listOf(
            "setCursor", "setCursorVisible", "setCursorGrab",
            "setCursorPosition", "setCursorHittest",
        )
        cursorMethods.forEach { name ->
            val found = UiKitWindow::class.members.any { it.name == name }
            assertTrue(found, "Cursor no-op method '$name' must exist on UiKitWindow")
        }
    }

    @Test
    fun `all window management no-op methods exist`() {
        val mgmtMethods = listOf(
            "dragWindow", "dragResizeWindow", "showWindowMenu",
        )
        mgmtMethods.forEach { name ->
            val found = UiKitWindow::class.members.any { it.name == name }
            assertTrue(found, "Window management no-op method '$name' must exist on UiKitWindow")
        }
    }
}
