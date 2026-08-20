/**
 * Conditional native integration tests for the Wayland backend.
 *
 * These tests only run on Linux where libwayland-client.so.0 is available.
 * On macOS/Windows they verify that the FFM bindings gracefully return null.
 *
 * Based on the pattern established by [Win32WindowTest]
 * (kadre-win32/.../Win32WindowTest.kt) for OS-conditional native tests.
 */
package org.graphiks.kadre.wayland

import org.graphiks.kffi.wayland.*
import org.graphiks.kffi.wayland.generated.xdg_activation_v1_interface
import org.graphiks.kffi.wayland.generated.xdg_toplevel_icon_manager_v1_interface
import org.graphiks.kffi.wayland.generated.zwp_pointer_constraints_v1_interface
import org.graphiks.kffi.wayland.generated.zwp_text_input_manager_v3_interface
import org.graphiks.kffi.wayland.generated.zwp_text_input_v3_interface
import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun isLinux(): Boolean =
    System.getProperty("os.name", "").contains("Linux", ignoreCase = true)

private fun nativeEnabled(): Boolean =
    isLinux() && !waylandNativeDisabled()

/** Reads the `const char *name` from a minimal `wl_interface` struct. */
private fun interfaceName(segment: MemorySegment): String {
    val nameSeg = segment.get(ValueLayout.ADDRESS, 0L)
    return nameSeg.reinterpret(64).getString(0)
}

class WaylandNativeIntegrationTest {

    @Test
    fun `missing Wayland display fails descriptively`() {
        if (!nativeEnabled() || System.getenv("WAYLAND_DISPLAY") != "definitely-missing") return

        val failure = assertFailsWith<IllegalStateException> {
            runApp(object : ApplicationHandler {
                override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
                override fun windowEvent(
                    eventLoop: ActiveEventLoop,
                    windowId: WindowId,
                    event: WindowEvent,
                ) = Unit
            })
        }

        assertTrue(failure.message.orEmpty().contains("backend=Wayland"))
        assertTrue(failure.message.orEmpty().contains("WAYLAND_DISPLAY=definitely-missing"))
        assertTrue(failure.message.orEmpty().contains("operation=wl_display_connect"))
        assertNotNull(failure.cause)
    }

    // ── FFM library loading (Linux only) ──────────────────────────────────────

    @Test
    fun `libWaylandClient resolves on Linux`() {
        if (!nativeEnabled()) return
        assertNotNull(libWaylandClient, "libwayland-client.so.0 must load on Linux")
    }

    @Test
    fun `core wayland method handles resolve on Linux`() {
        if (!nativeEnabled()) return
        assertNotNull(wlDisplayConnect)
        assertNotNull(wlCompositorCreateSurface)
        assertNotNull(wlProxyMarshalNewId)
        assertNotNull(wlProxyMarshalBind)
    }

    @Test
    fun `zwp pointer constraints v1 method handles resolve on Linux`() {
        if (!nativeEnabled()) return
        assertNotNull(wlPointerConstraintsLockPointer)
        assertNotNull(wlPointerConstraintsConfinePointer)
    }

    // ── Binding constants (cross-platform) ────────────────────────────────────

    @Test
    fun `kffi provides the Wayland marshal destroy flag`() {
        assertEquals(1, WL_MARSHAL_FLAG_DESTROY)
    }

    // ── Protocol interface names (Linux with libwayland) ───────────────────────

    @Test
    fun `protocol extension interface name constants are defined`() {
        assertEquals("xdg_toplevel_icon_manager_v1", XDG_TOPLEVEL_ICON_MANAGER_INTERFACE_NAME)
        assertEquals("ext_background_effect_v1", EXT_BACKGROUND_EFFECT_V1_INTERFACE_NAME)
        assertEquals("org_kde_kwin_blur_manager", ORG_KDE_KWIN_BLUR_MANAGER_INTERFACE_NAME)
    }

    @Test
    fun `zwp_pointer_constraints_v1 interface symbol name is correct`() {
        if (!nativeEnabled()) return
        assertEquals("zwp_pointer_constraints_v1", interfaceName(zwp_pointer_constraints_v1_interface))
    }

    @Test
    fun `xdg_toplevel_icon_manager_v1 interface symbol name is correct`() {
        if (!nativeEnabled()) return
        assertEquals("xdg_toplevel_icon_manager_v1", interfaceName(xdg_toplevel_icon_manager_v1_interface))
    }

    @Test
    fun `ext_background_effect_v1 interface symbol name is correct`() {
        assertEquals("ext_background_effect_v1", interfaceName(extBackgroundEffectV1Interface))
    }

    @Test
    fun `org_kde_kwin_blur_manager interface symbol name is correct`() {
        assertEquals("org_kde_kwin_blur_manager", interfaceName(orgKdeKwinBlurManagerInterface))
    }

    @Test
    fun `xdg_activation_v1 interface symbol name is correct`() {
        if (!nativeEnabled()) return
        assertEquals("xdg_activation_v1", interfaceName(xdg_activation_v1_interface))
    }

    @Test
    fun `zwp_text_input_manager_v3 interface symbol name is correct`() {
        if (!nativeEnabled()) return
        assertEquals("zwp_text_input_manager_v3", interfaceName(zwp_text_input_manager_v3_interface))
    }

    @Test
    fun `zwp_text_input_v3 interface symbol name is correct`() {
        if (!nativeEnabled()) return
        assertEquals("zwp_text_input_v3", interfaceName(zwp_text_input_v3_interface))
    }

    // ── WaylandRegistry.discoverGlobals (non-Linux only — no compositor) ────────

    @Test
    fun `discoverGlobals returns empty globals when library is unavailable`() {
        if (nativeEnabled()) return // on Linux with native libs this would invoke FFM with null display
        val globals = discoverGlobals(displayPtr = 0L)
        assertEquals(0L, globals.compositorPtr)
        assertEquals(0L, globals.xdgWmBasePtr)
    }

    @Test
    fun `discoverGlobals accepts empty protocolExtensions set`() {
        if (nativeEnabled()) return // on Linux with native libs this would invoke FFM with null display
        val globals = discoverGlobals(displayPtr = 0L, protocolExtensions = emptySet())
        assertEquals(0L, globals.compositorPtr)
    }

    // ── ZWP_POINTER_CONSTRAINTS_V1 lifetime constants (cross-platform) ─────────

    @Test
    fun `zwp pointer constraints v1 lifetime constants are defined`() {
        assertEquals(1, POINTER_CONSTRAINTS_LIFETIME_ONESHOT)
        assertEquals(2, POINTER_CONSTRAINTS_LIFETIME_PERSISTENT)
    }
}
