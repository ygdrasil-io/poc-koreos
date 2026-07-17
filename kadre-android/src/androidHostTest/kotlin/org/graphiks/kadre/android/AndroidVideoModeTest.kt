package org.graphiks.kadre.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidVideoModeTest {
    @Test
    fun refreshRateHzIsConvertedToMillihertz() {
        assertEquals(60_000, refreshRateMillihertz(60.0f))
        assertEquals(59_940, refreshRateMillihertz(59.94f))
    }

    @Test
    fun invalidRefreshRateHasNoMillihertzValue() {
        assertNull(refreshRateMillihertz(0.0f))
        assertNull(refreshRateMillihertz(-1.0f))
        assertNull(refreshRateMillihertz(Float.NaN))
        assertNull(refreshRateMillihertz(Float.POSITIVE_INFINITY))
        assertNull(refreshRateMillihertz(Float.NEGATIVE_INFINITY))
    }
}
