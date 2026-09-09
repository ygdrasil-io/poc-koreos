package org.graphiks.kadre.display

import org.graphiks.kadre.surface.PhysicalSize
import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayValuesTest {
    @Test
    fun displayStateKeepsDistinctNativeModesThatShareTheSameMetrics() {
        val first = DisplayMode(
            id = DisplayModeId(1),
            physicalSize = PhysicalSize(1920, 1080),
            refreshRateHz = 60.0,
            bitDepth = 24,
        )
        val second = DisplayMode(
            id = DisplayModeId(2),
            physicalSize = PhysicalSize(1920, 1080),
            refreshRateHz = 60.0,
            bitDepth = 24,
        )

        val state = DisplayState(
            type = DisplayType.Physical,
            connection = DisplayConnectionState.Connected,
            name = null,
            bounds = org.graphiks.kadre.surface.PhysicalRect(
                origin = org.graphiks.kadre.surface.PhysicalPoint(0, 0),
                size = PhysicalSize(1920, 1080),
            ),
            workArea = null,
            scaleFactor = 1.0,
            currentMode = first,
            modes = listOf(first, second),
            revision = DisplayRevision(0),
        )

        assertEquals(listOf(first, second), state.modes)
    }
}
