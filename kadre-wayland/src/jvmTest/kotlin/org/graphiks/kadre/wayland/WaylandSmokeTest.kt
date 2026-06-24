package org.graphiks.kadre.wayland

import org.graphiks.kadre.core.RawWindowHandle
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.ffi.wayland.*
import java.lang.foreign.MemorySegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WaylandSmokeTest {
    @Test
    fun `libwayland-client binding loads safely on non-Wayland`() {
        // On Wayland Linux: loads; on macOS/Windows/X11: null — safe skip
        val lib = libWaylandClient
        if (lib == null) return // Not Wayland — skip silently
    }

    @Test
    fun `wl_display_connect succeeds when WAYLAND_DISPLAY is configured`() {
        if (!shouldRunWaylandRuntimeTest()) return

        val connect = wlDisplayConnect ?: error("wl_display_connect binding is not available")
        val disconnect = wlDisplayDisconnect ?: error("wl_display_disconnect binding is not available")
        val display = connect.invokeExact(MemorySegment.NULL) as MemorySegment
        try {
            assertFalse(display == MemorySegment.NULL)
        } finally {
            if (display != MemorySegment.NULL) {
                disconnect.invokeExact(display)
            }
        }
    }

    @Test
    fun `Wayland runtime discovers compositor and creates wl_surface`() {
        if (!shouldRunWaylandRuntimeTest()) return

        val connect = wlDisplayConnect ?: error("wl_display_connect binding is not available")
        val disconnect = wlDisplayDisconnect ?: error("wl_display_disconnect binding is not available")
        val roundtrip = wlDisplayRoundtrip ?: error("wl_display_roundtrip binding is not available")
        val display = connect.invokeExact(MemorySegment.NULL) as MemorySegment
        assertFalse(display == MemorySegment.NULL)

        try {
            val displayPtr = display.address()
            val globals = discoverGlobals(displayPtr, protocolExtensions = emptySet())
            assertTrue(globals.compositorPtr != 0L, "wl_compositor must be discovered and bound")

            val window = assertNotNull(
                WaylandWindow.create(
                    display = displayPtr,
                    compositor = globals.compositorPtr,
                    xdgWmBase = 0L,
                    shmPtr = 0L,
                    attrs = WindowAttributes(title = "Kadre Wayland smoke", visible = false),
                ),
                "WaylandWindow.create must create a wl_surface from wl_compositor",
            )
            val rawHandle = window.rawWindowHandle
            assertTrue(rawHandle is RawWindowHandle.Wayland)
            assertEquals(displayPtr, rawHandle.display)
            assertTrue(rawHandle.surface != 0L, "wl_surface pointer must be non-null")

            window.close()
            roundtrip.invokeExact(display) as Int
        } finally {
            disconnect.invokeExact(display)
        }
    }

    @Test
    fun `Wayland runtime creates configured xdg toplevel`() {
        if (!shouldRunWaylandRuntimeTest()) return

        val connect = wlDisplayConnect ?: error("wl_display_connect binding is not available")
        val disconnect = wlDisplayDisconnect ?: error("wl_display_disconnect binding is not available")
        val roundtrip = wlDisplayRoundtrip ?: error("wl_display_roundtrip binding is not available")
        val display = connect.invokeExact(MemorySegment.NULL) as MemorySegment
        assertFalse(display == MemorySegment.NULL)

        try {
            val displayPtr = display.address()
            val globals = discoverGlobals(displayPtr, protocolExtensions = emptySet())
            assertTrue(globals.compositorPtr != 0L, "wl_compositor must be discovered and bound")
            assertTrue(globals.xdgWmBasePtr != 0L, "xdg_wm_base must be discovered and bound")

            val window = assertNotNull(
                WaylandWindow.create(
                    display = displayPtr,
                    compositor = globals.compositorPtr,
                    xdgWmBase = globals.xdgWmBasePtr,
                    shmPtr = 0L,
                    attrs = WindowAttributes(title = "Kadre Wayland xdg smoke", visible = false),
                ),
                "WaylandWindow.create must complete the xdg_shell handshake",
            )
            try {
                val rawHandle = window.rawWindowHandle
                assertTrue(rawHandle is RawWindowHandle.Wayland)
                assertEquals(displayPtr, rawHandle.display)
                assertTrue(rawHandle.surface != 0L, "wl_surface pointer must be non-null")
                assertTrue(window.xdgToplevelPtr() != 0L, "xdg_toplevel pointer must be non-null")
                assertTrue(
                    window.hasReceivedInitialXdgConfigure(),
                    "initial xdg_surface.configure must be received and acked",
                )

                window.setTitle("Kadre Wayland xdg smoke updated")
                roundtrip.invokeExact(display) as Int
            } finally {
                window.close()
                roundtrip.invokeExact(display) as Int
            }
        } finally {
            disconnect.invokeExact(display)
        }
    }
}

private fun shouldRunWaylandRuntimeTest(): Boolean {
    val os = System.getProperty("os.name", "").lowercase()
    if (!os.contains("linux")) return false

    val waylandDisplay = System.getenv("WAYLAND_DISPLAY")
    if (waylandDisplay.isNullOrBlank()) return false

    if (waylandNativeDisabled()) return false

    return true
}
