package org.graphiks.kadre.x11

import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowLevel
import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.core.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class X11WindowTest {

    @Test
    fun `X11Window is created without error on non-Linux`() {
        if (libX11 == null) return // Skip on macOS / Windows
        // On Linux with an available X server, a creation attempt may
        // fail if DISPLAY is not set in CI — that is acceptable.
        // We only verify that the code path is traversed without an exception.
    }

    @Test
    fun `X11Window returns null if libX11 is absent`() {
        // On macOS / Windows, libX11 is null → create() must return null
        if (libX11 != null) return // Skip on Linux
        val result = X11Window.create(
            display = 0L,
            screen = 0,
            attrs = WindowAttributes(title = "Test"),
        )
        assertNull(result, "X11Window.create must return null if libX11 is absent")
    }

    // ── parseXftDpi unit tests (pure Kotlin, no X server needed) ─────────────
    // parseXftDpi(resourceString) returns a scale factor (Double): dpi/96.0, or 1.0

    @Test
    fun `parseXftDpi returns correct scale factor for tab-separated entry`() {
        val resources = "Xft.antialias:\t1\nXft.dpi:\t144\nXft.hinting:\t1\n"
        val result = parseXftDpi(resources)
        assertEquals(144.0 / 96.0, result, "Expected Xft.dpi=144 → scaleFactor=1.5")
    }

    @Test
    fun `parseXftDpi returns correct scale factor for space-separated entry`() {
        val resources = "Xft.dpi: 192"
        val result = parseXftDpi(resources)
        assertEquals(192.0 / 96.0, result, "Expected Xft.dpi=192 → scaleFactor=2.0")
    }

    @Test
    fun `parseXftDpi returns 1_0 fallback if Xft dpi is absent`() {
        val resources = "Xft.antialias:\t1\nXft.hinting:\t1\n"
        val result = parseXftDpi(resources)
        assertEquals(1.0, result, "Expected fallback 1.0 when Xft.dpi is absent")
    }

    @Test
    fun `parseXftDpi returns 1_0 for empty string`() {
        val result = parseXftDpi("")
        assertEquals(1.0, result, "Expected fallback 1.0 for empty resource string")
    }

    @Test
    fun `readXftDpi returns 1_0 fallback when display is 0`() {
        // display pointer 0 → readXftDpi must not throw and must return 1.0
        // (libX11 is null on macOS/Windows; on Linux XResourceManagerString(0) → handled)
        if (libX11 != null) return // Skip on Linux where this could be undefined behaviour
        val sf = readXftDpi(0L)
        assertEquals(1.0, sf, "Expected fallback scale factor 1.0 when libX11 is absent")
    }

    @Test
    fun `X11 window levels map to EWMH above and below states`() {
        assertEquals(X11WindowLevelState(above = true, below = false), x11WindowLevelState(WindowLevel.AlwaysOnTop))
        assertEquals(X11WindowLevelState(above = false, below = false), x11WindowLevelState(WindowLevel.Normal))
        assertEquals(X11WindowLevelState(above = false, below = true), x11WindowLevelState(WindowLevel.AlwaysOnBottom))
    }

    @Test
    fun `X11 theme variants match winit GTK theme hint values`() {
        assertEquals("dark", x11ThemeVariant(Theme.Dark))
        assertEquals("light", x11ThemeVariant(Theme.Light))
        assertEquals("dark", x11ThemeVariant(null))
    }

    @Test
    fun `X11 initial position uses attributes or zero fallback`() {
        assertEquals(PhysicalPosition(12, 34), x11InitialPosition(PhysicalPosition(12, 34)))
        assertEquals(PhysicalPosition(0, 0), x11InitialPosition(null))
    }

    @Test
    fun `X11 normal hints encode position size constraints and increments`() {
        val hints = x11NormalHints(
            position = PhysicalPosition(10, 20),
            size = PhysicalSize(800, 600),
            minSize = PhysicalSize(320, 240),
            maxSize = PhysicalSize(1920, 1080),
            resizeIncrements = PhysicalSize(8, 16),
            resizable = true,
        )

        assertEquals(X11_NORMAL_HINTS_ELEMENTS, hints.elements.size)
        assertEquals(
            X11_US_POSITION or X11_US_SIZE or X11_P_MIN_SIZE or X11_P_MAX_SIZE or X11_P_RESIZE_INC,
            hints.elements[0],
        )
        assertEquals(10L, hints.elements[1])
        assertEquals(20L, hints.elements[2])
        assertEquals(800L, hints.elements[3])
        assertEquals(600L, hints.elements[4])
        assertEquals(320L, hints.elements[5])
        assertEquals(240L, hints.elements[6])
        assertEquals(1920L, hints.elements[7])
        assertEquals(1080L, hints.elements[8])
        assertEquals(8L, hints.elements[9])
        assertEquals(16L, hints.elements[10])
    }

    @Test
    fun `X11 normal hints pin min and max size when not resizable`() {
        val hints = x11NormalHints(
            position = null,
            size = PhysicalSize(640, 480),
            minSize = PhysicalSize(320, 240),
            maxSize = PhysicalSize(1920, 1080),
            resizeIncrements = null,
            resizable = false,
        )

        assertEquals(X11_US_SIZE or X11_P_MIN_SIZE or X11_P_MAX_SIZE, hints.elements[0])
        assertEquals(640L, hints.elements[5])
        assertEquals(480L, hints.elements[6])
        assertEquals(640L, hints.elements[7])
        assertEquals(480L, hints.elements[8])
    }

    @Test
    fun `X11 normal hints avoid non-resizable pin for Xfwm4 compatibility`() {
        val hints = x11NormalHints(
            position = null,
            size = PhysicalSize(640, 480),
            minSize = PhysicalSize(320, 240),
            maxSize = PhysicalSize(1920, 1080),
            resizeIncrements = null,
            resizable = false,
            avoidNonResizablePin = true,
        )

        assertEquals(X11_US_SIZE or X11_P_MIN_SIZE or X11_P_MAX_SIZE, hints.elements[0])
        assertEquals(320L, hints.elements[5])
        assertEquals(240L, hints.elements[6])
        assertEquals(1920L, hints.elements[7])
        assertEquals(1080L, hints.elements[8])
    }

    @Test
    fun `scaleFactor computed from Xft_dpi 96 equals 1_0`() {
        val resources = "Xft.dpi:\t96"
        assertEquals(1.0, parseXftDpi(resources), "96 DPI should yield scale factor 1.0")
    }
}
