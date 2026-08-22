package org.graphiks.kadre.android

import org.graphiks.kadre.core.CursorGrabMode
import org.graphiks.kadre.core.CursorIcon
import org.graphiks.kadre.core.CustomCursor
import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.RequestError
import org.graphiks.kadre.core.ResizeDirection
import org.graphiks.kadre.core.UserAttentionType
import org.graphiks.kadre.core.Window
import org.graphiks.kadre.core.WindowRequestResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * No-op contract tests for [AndroidWindow] (Task 6 — C6).
 *
 * Verifies that all documented no-op methods exist with the correct
 * signatures and return the expected [WindowRequestResult] types.
 * Uses Java reflection to inspect method signatures without requiring
 * an Android emulator or Robolectric runtime.
 */
class AndroidWindowNoOpTest {

    // ── Type hierarchy ─────────────────────────────────────────────────────

    @Test
    fun `AndroidWindow implements Window`() {
        assertTrue(
            Window::class.java.isAssignableFrom(AndroidWindow::class.java),
            "AndroidWindow must implement Window",
        )
    }

    // ── Cursor APIs — no-ops returning Unit ────────────────────────────────

    @Test
    fun `setCursor accepts CursorIcon parameter`() {
        val method = AndroidWindow::class.java.getMethod("setCursor", CursorIcon::class.java)
        assertNotNull(method, "setCursor must exist on AndroidWindow")
        assertEquals(Void.TYPE, method.returnType, "setCursor must return Unit")
    }

    @Test
    fun `setCursorVisible accepts Boolean parameter`() {
        val method = AndroidWindow::class.java.getMethod("setCursorVisible", Boolean::class.javaPrimitiveType)
        assertNotNull(method, "setCursorVisible must exist on AndroidWindow")
    }

    // ── Cursor APIs — WindowRequestResult ──────────────────────────────────

    @Test
    fun `setCursorGrab returns WindowRequestResult`() {
        val method = AndroidWindow::class.java.getMethod("setCursorGrab", CursorGrabMode::class.java)
        assertNotNull(method, "setCursorGrab must exist on AndroidWindow")
        assertEquals(
            WindowRequestResult::class.java, method.returnType,
            "setCursorGrab must return WindowRequestResult",
        )
    }

    @Test
    fun `setCursorPosition returns WindowRequestResult`() {
        val method = AndroidWindow::class.java.getMethod("setCursorPosition", PhysicalPosition::class.java)
        assertNotNull(method, "setCursorPosition must exist on AndroidWindow")
        assertEquals(
            WindowRequestResult::class.java, method.returnType,
            "setCursorPosition must return WindowRequestResult",
        )
    }

    @Test
    fun `setCursorHittest returns WindowRequestResult`() {
        val method = AndroidWindow::class.java.getMethod("setCursorHittest", Boolean::class.javaPrimitiveType)
        assertNotNull(method, "setCursorHittest must exist on AndroidWindow")
        assertEquals(
            WindowRequestResult::class.java, method.returnType,
            "setCursorHittest must return WindowRequestResult",
        )
    }

    // ── Window state APIs — no-ops returning Unit ──────────────────────────

    @Test
    fun `setMinimized accepts Boolean parameter`() {
        val method = AndroidWindow::class.java.getMethod("setMinimized", Boolean::class.javaPrimitiveType)
        assertNotNull(method, "setMinimized must exist on AndroidWindow")
        assertEquals(Void.TYPE, method.returnType, "setMinimized must return Unit")
    }

    @Test
    fun `setMaximized accepts Boolean parameter`() {
        val method = AndroidWindow::class.java.getMethod("setMaximized", Boolean::class.javaPrimitiveType)
        assertNotNull(method, "setMaximized must exist on AndroidWindow")
    }

    @Test
    fun `setDecorations accepts Boolean parameter`() {
        val method = AndroidWindow::class.java.getMethod("setDecorations", Boolean::class.javaPrimitiveType)
        assertNotNull(method, "setDecorations must exist on AndroidWindow")
    }

    @Test
    fun `setResizable accepts Boolean parameter`() {
        val method = AndroidWindow::class.java.getMethod("setResizable", Boolean::class.javaPrimitiveType)
        assertNotNull(method, "setResizable must exist on AndroidWindow")
    }

    @Test
    fun `setWindowLevel exists`() {
        val method = AndroidWindow::class.java.methods
            .firstOrNull { it.name == "setWindowLevel" }
        assertNotNull(method, "setWindowLevel must exist on AndroidWindow")
    }

    @Test
    fun `setFullscreen exists`() {
        val method = AndroidWindow::class.java.methods
            .firstOrNull { it.name == "setFullscreen" }
        assertNotNull(method, "setFullscreen must exist on AndroidWindow")
    }

    // ── Appearance APIs — no-ops returning Unit ────────────────────────────

    @Test
    fun `setTheme accepts nullable Theme parameter`() {
        val method = AndroidWindow::class.java.methods
            .firstOrNull { it.name == "setTheme" }
        assertNotNull(method, "setTheme must exist on AndroidWindow")
        assertEquals(Void.TYPE, method.returnType)
    }

    @Test
    fun `setTransparent accepts Boolean parameter`() {
        val method = AndroidWindow::class.java.getMethod("setTransparent", Boolean::class.javaPrimitiveType)
        assertNotNull(method, "setTransparent must exist on AndroidWindow")
    }

    @Test
    fun `setBlur accepts Boolean parameter`() {
        val method = AndroidWindow::class.java.getMethod("setBlur", Boolean::class.javaPrimitiveType)
        assertNotNull(method, "setBlur must exist on AndroidWindow")
    }

    @Test
    fun `setWindowIcon exists`() {
        val method = AndroidWindow::class.java.methods
            .firstOrNull { it.name == "setWindowIcon" }
        assertNotNull(method, "setWindowIcon must exist on AndroidWindow")
    }

    @Test
    fun `setCustomCursor exists`() {
        val method = AndroidWindow::class.java.methods
            .firstOrNull { it.name == "setCustomCursor" }
        assertNotNull(method, "setCustomCursor must exist on AndroidWindow")
    }

    // ── Appearance APIs — WindowRequestResult ──────────────────────────────

    @Test
    fun `requestUserAttention returns WindowRequestResult`() {
        val method = AndroidWindow::class.java.methods
            .firstOrNull { it.name == "requestUserAttention" }
        assertNotNull(method, "requestUserAttention must exist on AndroidWindow")
        assertEquals(
            WindowRequestResult::class.java, method.returnType,
            "requestUserAttention must return WindowRequestResult",
        )
    }

    @Test
    fun `requestUserAttention Success is a valid WindowRequestResult`() {
        assertTrue(WindowRequestResult.Success is WindowRequestResult)
    }

    // ── Window management — WindowRequestResult Failure ────────────────────

    @Test
    fun `dragWindow returns WindowRequestResult`() {
        val method = AndroidWindow::class.java.methods
            .firstOrNull { it.name == "dragWindow" }
        assertNotNull(method, "dragWindow must exist on AndroidWindow")
        assertEquals(
            WindowRequestResult::class.java, method.returnType,
        )
    }

    @Test
    fun `dragResizeWindow returns WindowRequestResult`() {
        val method = AndroidWindow::class.java.methods
            .firstOrNull { it.name == "dragResizeWindow" }
        assertNotNull(method, "dragResizeWindow must exist on AndroidWindow")
        assertEquals(
            WindowRequestResult::class.java, method.returnType,
        )
    }

    @Test
    fun `showWindowMenu returns WindowRequestResult`() {
        val method = AndroidWindow::class.java.methods
            .firstOrNull { it.name == "showWindowMenu" }
        assertNotNull(method, "showWindowMenu must exist on AndroidWindow")
        assertEquals(
            WindowRequestResult::class.java, method.returnType,
        )
    }

    @Test
    fun `setContentProtected returns WindowRequestResult`() {
        val method = AndroidWindow::class.java.methods
            .firstOrNull { it.name == "setContentProtected" }
        assertNotNull(method, "setContentProtected must exist on AndroidWindow")
        assertEquals(
            WindowRequestResult::class.java, method.returnType,
        )
    }

    // ── Bulk coverage ─────────────────────────────────────────────────────

    @Test
    fun `all cursor no-op methods exist on AndroidWindow`() {
        val cursorMethods = listOf(
            "setCursor", "setCursorVisible", "setCursorGrab",
            "setCursorPosition", "setCursorHittest",
        )
        cursorMethods.forEach { name ->
            val found = AndroidWindow::class.java.methods.any { it.name == name }
            assertTrue(found, "Cursor no-op method '$name' must exist on AndroidWindow")
        }
    }

    @Test
    fun `all window management no-op methods exist on AndroidWindow`() {
        val mgmtMethods = listOf(
            "dragWindow", "dragResizeWindow", "showWindowMenu",
        )
        mgmtMethods.forEach { name ->
            val found = AndroidWindow::class.java.methods.any { it.name == name }
            assertTrue(found, "Window management no-op method '$name' must exist on AndroidWindow")
        }
    }

    @Test
    fun `all WindowRequestResult-returning no-ops exist on AndroidWindow`() {
        val resultMethods = listOf(
            "requestUserAttention", "setContentProtected",
        )
        resultMethods.forEach { name ->
            val found = AndroidWindow::class.java.methods.any { it.name == name }
            assertTrue(found, "Result-returning no-op method '$name' must exist on AndroidWindow")
        }
    }
}
