package org.graphiks.kadre.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun refreshRateOverflowHasNoMillihertzValue() {
        val threshold = (Int.MAX_VALUE.toDouble() / 1_000.0).toFloat()
        val belowThreshold = Math.nextDown(threshold)
        val aboveThreshold = Math.nextUp(threshold)

        assertEquals(
            (belowThreshold.toDouble() * 1_000.0).toInt(),
            refreshRateMillihertz(belowThreshold),
        )
        assertNull(refreshRateMillihertz(aboveThreshold))
        assertNull(refreshRateMillihertz(Float.MAX_VALUE))
    }

    @Test
    fun availableMonitorsDoesNotCallApi30ContextDisplay() {
        val classLoader = checkNotNull(AndroidEventLoop::class.java.classLoader)
        val classBytes = listOf(
            "org/graphiks/kadre/android/AndroidEventLoop.class",
            "org/graphiks/kadre/android/AndroidEventLoop\$availableMonitors\$1.class",
        ).joinToString(separator = "") { resource ->
            checkNotNull(classLoader.getResourceAsStream(resource)).use {
                it.readBytes().toString(Charsets.ISO_8859_1)
            }
        }

        assertTrue(classBytes.contains("getDefaultDisplay"))
        assertFalse(
            classBytes.contains("getDisplay"),
            "availableMonitors must not call Context.getDisplay(), which requires API 30",
        )
    }
}
