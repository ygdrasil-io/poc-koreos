package org.graphiks.kadre.samples.desktour.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ApiDetailsTest {
    @Test
    fun `the api details matrix matches the public surface named by the spec`() {
        assertEquals(
            listOf("WindowManager.requestWindow", "WindowRequest", "WindowRequestOutcome"),
            ApiDetailKey.CreateFloatingNote.publicSurface,
        )
        assertEquals(
            listOf("Window.apply", "WindowUpdate", "WindowUpdateOutcome"),
            ApiDetailKey.ModifyWindow.publicSurface,
        )
        assertEquals(
            listOf("DisplayManager.state", "DisplayManager.events"),
            ApiDetailKey.TrackDisplays.publicSurface,
        )
        assertEquals(
            listOf("HostSurface.state", "HostSurface.events", "HostSurface.apply"),
            ApiDetailKey.TrackSurface.publicSurface,
        )
        assertEquals(
            listOf("HostSurface.input", "SurfaceInput.events", "DropOffer"),
            ApiDetailKey.TypeOrDrop.publicSurface,
        )
        assertEquals(
            listOf("Capability", "KadreResult", "KadreDiagnostics"),
            ApiDetailKey.DiagnoseOption.publicSurface,
        )
    }
}
