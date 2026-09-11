package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kffi.objc.appkit.WindowOuterBoundsSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KffiAppKitWindowGeometryTest {
    @Test
    fun exactWindowServerBoundsBecomeKadrePhysicalBounds() {
        val bounds = WindowOuterBoundsSnapshot(
            x = -1_312.0,
            y = 348.0,
            width = 1_600.0,
            height = 900.0,
        )

        assertEquals(
            PhysicalRect(PhysicalPoint(-1_312, 348), PhysicalSize(1_600, 900)),
            bounds.toKadrePhysicalRectOrNull(),
        )
    }

    @Test
    fun fractionalWindowServerBoundsRemainUnknown() {
        val bounds = WindowOuterBoundsSnapshot(
            x = -1_312.5,
            y = 348.0,
            width = 1_600.0,
            height = 900.0,
        )

        assertNull(bounds.toKadrePhysicalRectOrNull())
    }

    @Test
    fun emptyWindowServerBoundsRemainUnknown() {
        val bounds = WindowOuterBoundsSnapshot(
            x = 0.0,
            y = 0.0,
            width = 0.0,
            height = 900.0,
        )

        assertNull(bounds.toKadrePhysicalRectOrNull())
    }
}
