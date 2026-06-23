package org.graphiks.kadre.wayland

import org.graphiks.kadre.ffi.wayland.*
import java.lang.foreign.MemorySegment
import kotlin.test.Test
import kotlin.test.assertFalse

class WaylandSmokeTest {
    @Test
    fun `libwayland-client binding loads safely on non-Wayland`() {
        // On Wayland Linux: loads; on macOS/Windows/X11: null — safe skip
        val lib = libWaylandClient
        if (lib == null) return // Not Wayland — skip silently
    }

    @Test
    fun `wl_display_connect succeeds when WAYLAND_DISPLAY is configured`() {
        val os = System.getProperty("os.name", "").lowercase()
        if (!os.contains("linux")) return

        val waylandDisplay = System.getenv("WAYLAND_DISPLAY")
        if (waylandDisplay.isNullOrBlank()) return

        if (waylandNativeDisabled()) return

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
}
