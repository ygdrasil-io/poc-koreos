package org.graphiks.kadre.wayland

import kotlin.test.Test

class XdgShellSmokeTest {
    @Test
    fun `xdg_shell constants are defined`() {
        // Validate key opcode values from Wayland xdg-shell protocol XML
        assert(XDG_WM_BASE_GET_XDG_SURFACE == 2)
        assert(XDG_SURFACE_GET_TOPLEVEL == 1)
        assert(XDG_TOPLEVEL_SET_TITLE == 2)
        assert(XDG_TOPLEVEL_SHOW_WINDOW_MENU == 4)
        assert(XDG_TOPLEVEL_MOVE == 5)
        assert(XDG_TOPLEVEL_RESIZE == 6)
    }
}
