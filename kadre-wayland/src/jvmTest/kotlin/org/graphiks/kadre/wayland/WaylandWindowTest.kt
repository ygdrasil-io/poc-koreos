/**
 * Smoke tests for [WaylandWindow].
 *
 * These tests verify that [WaylandWindow] can be constructed with mock
 * pointers without causing a crash, and that the returned handles are correct.
 * They run on all platforms (macOS, Windows, Linux) without requiring
 * libwayland-client.so.0.
 */
package org.graphiks.kadre.wayland

import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.core.RawDisplayHandle
import org.graphiks.kadre.core.RawWindowHandle
import org.graphiks.kadre.core.WindowAttributes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class WaylandWindowTest {

    @Test
    fun `WaylandWindow can be constructed with mock pointers without crashing`() {
        // createForTest bypasses the FFM calls — works on all platforms
        val window = WaylandWindow.createForTest(
            display = 0L,
            compositor = 0L,
            xdgWmBase = 0L,
            surface = 0L,
        )
        assertNotNull(window)
    }

    @Test
    fun `rawWindowHandle returns Wayland type with correct pointers`() {
        val window = WaylandWindow.createForTest(
            display = 42L,
            compositor = 0L,
            surface = 99L,
        )
        val handle = window.rawWindowHandle
        assertIs<RawWindowHandle.Wayland>(handle)
        assertEquals(99L, handle.surface)
        assertEquals(42L, handle.display)
    }

    @Test
    fun `rawDisplayHandle returns Wayland type with correct display pointer`() {
        val window = WaylandWindow.createForTest(display = 123L)
        val handle = window.rawDisplayHandle
        assertIs<RawDisplayHandle.Wayland>(handle)
        assertEquals(123L, handle.display)
    }

    @Test
    fun `innerSize returns attrs size when provided`() {
        val attrs = WindowAttributes(size = PhysicalSize(1280, 720))
        val window = WaylandWindow.createForTest(attrs = attrs)
        assertEquals(PhysicalSize(1280, 720), window.innerSize)
    }

    @Test
    fun `innerSize returns default 800x600 when attrs size is null`() {
        val attrs = WindowAttributes(size = null)
        val window = WaylandWindow.createForTest(attrs = attrs)
        assertEquals(PhysicalSize(800, 600), window.innerSize)
    }

    @Test
    fun `outerSize equals innerSize`() {
        val window = WaylandWindow.createForTest()
        assertEquals(window.innerSize, window.outerSize)
    }

    @Test
    fun `scaleFactor is 1 0`() {
        val window = WaylandWindow.createForTest()
        assertEquals(1.0, window.scaleFactor)
    }

    @Test
    fun `onConfigure updates innerSize when dimensions are positive`() {
        val window = WaylandWindow.createForTest()
        window.onConfigure(1920, 1080)
        assertEquals(PhysicalSize(1920, 1080), window.innerSize)
    }

    @Test
    fun `surface resize increments are initialized from attrs and mutable`() {
        val window = WaylandWindow.createForTest(
            attrs = WindowAttributes(resizeIncrements = PhysicalSize(8, 16)),
        )
        assertEquals(PhysicalSize(8, 16), window.surfaceResizeIncrements)

        window.setSurfaceResizeIncrements(PhysicalSize(4, 6))
        assertEquals(PhysicalSize(4, 6), window.surfaceResizeIncrements)

        window.setSurfaceResizeIncrements(null)
        assertEquals(null, window.surfaceResizeIncrements)
    }

    @Test
    fun `onConfigure applies resize increments from minimum size base`() {
        val attrs = WindowAttributes(
            minSize = PhysicalSize(100, 50),
            resizeIncrements = PhysicalSize(30, 20),
        )
        val window = WaylandWindow.createForTest(attrs = attrs)

        window.onConfigure(176, 99)

        assertEquals(PhysicalSize(160, 90), window.innerSize)
    }

    @Test
    fun `onConfigure can skip resize increments for compositor enforced sizes`() {
        val attrs = WindowAttributes(
            minSize = PhysicalSize(100, 50),
            resizeIncrements = PhysicalSize(30, 20),
        )
        val window = WaylandWindow.createForTest(attrs = attrs)

        window.onConfigure(176, 99, applyResizeIncrements = false)

        assertEquals(PhysicalSize(176, 99), window.innerSize)
    }

    @Test
    fun `Wayland resize increments apply only for resizing unconstrained states`() {
        assertEquals(
            true,
            waylandShouldApplyResizeIncrements(
                isResizing = true,
                isMaximized = false,
                isFullscreen = false,
                isTiled = false,
            ),
        )
        assertEquals(
            false,
            waylandShouldApplyResizeIncrements(
                isResizing = true,
                isMaximized = true,
                isFullscreen = false,
                isTiled = false,
            ),
        )
        assertEquals(
            false,
            waylandShouldApplyResizeIncrements(
                isResizing = false,
                isMaximized = false,
                isFullscreen = false,
                isTiled = false,
            ),
        )
    }

    @Test
    fun `wayland resize increments ignore invalid increments`() {
        val size = PhysicalSize(176, 99)
        assertEquals(size, waylandApplyResizeIncrements(size, PhysicalSize(100, 50), PhysicalSize(0, 20)))
        assertEquals(size, waylandApplyResizeIncrements(size, PhysicalSize(100, 50), PhysicalSize(30, -1)))
        assertEquals(size, waylandApplyResizeIncrements(size, PhysicalSize(100, 50), null))
    }

    @Test
    fun `onConfigure ignores zero dimensions`() {
        val attrs = WindowAttributes(size = PhysicalSize(800, 600))
        val window = WaylandWindow.createForTest(attrs = attrs)
        window.onConfigure(0, 0)
        assertEquals(PhysicalSize(800, 600), window.innerSize)
    }

    @Test
    fun `id is based on surface pointer`() {
        val window = WaylandWindow.createForTest(surface = 7777L)
        assertEquals(7777L, window.id.value)
    }

    @Test
    fun `close does not crash with null surface`() {
        // surface = 0 → close() must return without an FFM call
        val window = WaylandWindow.createForTest(surface = 0L)
        window.close() // Must not throw an exception
    }

    @Test
    fun `requestRedraw does not crash with null surface`() {
        val window = WaylandWindow.createForTest(surface = 0L)
        window.requestRedraw() // Must not throw an exception
    }

    @Test
    fun `setTitle does not crash`() {
        val window = WaylandWindow.createForTest()
        window.setTitle("Test Window") // Stub — must not throw an exception
    }

    @Test
    fun `setVisible does not crash`() {
        val window = WaylandWindow.createForTest(surface = 0L)
        window.setVisible(true)
        window.setVisible(false)
    }

    @Test
    fun `WaylandWindow create returns null when libwayland is not available`() {
        // On macOS/Windows, wlCompositorCreateSurface is null
        // → create() returns null gracefully
        if (libWaylandClient != null) return // Skip on Wayland Linux

        val result = WaylandWindow.create(
            display = 0L,
            compositor = 0L,
            xdgWmBase = 0L,
            attrs = WindowAttributes(),
        )
        // On non-Wayland, the binding is null and create() returns null
        assertEquals(null, result)
    }
}
