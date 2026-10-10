package org.graphiks.kadre.samples.desktour.appkit

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import org.graphiks.kadre.surface.LogicalPoint

class PointerCoordinatesTest {
    @Test
    fun `native bottom-left points map to Compose pixels using the current logical height`() {
        val point = LogicalPoint(25.0, 100.0)

        assertEquals(Offset(50f, 1000f), appKitPointToCompose(point, logicalHeight = 600.0, scaleFactor = 2.0))
        // The same raw point after a resize / density change must use the new surface metrics.
        assertEquals(Offset(37.5f, 1200f), appKitPointToCompose(point, logicalHeight = 900.0, scaleFactor = 1.5))
    }

    @Test
    fun `conversion preserves edges and positions outside the surface without clamping`() {
        assertEquals(Offset(20f, 0f), appKitPointToCompose(LogicalPoint(10.0, 600.0), 600.0, 2.0))
        assertEquals(Offset(20f, 1200f), appKitPointToCompose(LogicalPoint(10.0, 0.0), 600.0, 2.0))
        assertEquals(Offset(-10f, -100f), appKitPointToCompose(LogicalPoint(-5.0, 650.0), 600.0, 2.0))
    }
}
