package org.graphiks.kadre.internal.wayland

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform

class WaylandNegotiationTest {

    @Test
    fun bindingIsCappedAtTheKffiMaximum() {
        assertEquals(4, WaylandNegotiation.resolveBinding(advertised = 6, kffiMax = 4))
    }

    @Test
    fun bindingKeepsTheAdvertisedVersionWhenBelowTheMaximum() {
        assertEquals(3, WaylandNegotiation.resolveBinding(advertised = 3, kffiMax = 4))
    }

    @Test
    fun completeGlobalsSnapshotIsAdmitted() {
        assertNull(
            WaylandNegotiation.checkGlobals(
                listOf(WaylandGlobal("wl_compositor", 6), WaylandGlobal("xdg_wm_base", 2)),
            ),
        )
    }

    @Test
    fun missingRequiredGlobalFailsBeforeSession() {
        assertEquals(
            KadreFailure.PlatformFailure(KadrePlatform.Wayland, "negotiation", "missing-xdg_wm_base"),
            WaylandNegotiation.checkGlobals(listOf(WaylandGlobal("wl_compositor", 6))),
        )
    }

    @Test
    fun versionBelowMinimumFailsBeforeSession() {
        assertEquals(
            KadreFailure.PlatformFailure(KadrePlatform.Wayland, "negotiation", "wl_compositor-advertised-3-below-4"),
            WaylandNegotiation.checkGlobals(listOf(WaylandGlobal("wl_compositor", 3), WaylandGlobal("xdg_wm_base", 2))),
        )
    }

    @Test
    fun kffiMaximumsMatchTheAuditBaseline() {
        assertEquals(4, WaylandNegotiation.KFFI_MAX_VERSIONS["wl_compositor"])
        assertEquals(2, WaylandNegotiation.KFFI_MAX_VERSIONS["xdg_wm_base"])
        assertTrue(WaylandNegotiation.REQUIRED_GLOBALS["wl_compositor"]!! <= 4)
    }
}
