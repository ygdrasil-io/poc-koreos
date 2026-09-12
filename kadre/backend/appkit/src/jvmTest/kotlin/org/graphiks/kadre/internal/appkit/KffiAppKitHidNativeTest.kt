@file:OptIn(org.graphiks.kffi.objc.PlatformAvailability::class)

package org.graphiks.kadre.internal.appkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KffiAppKitHidNativeTest {
    @Test
    fun managedHidMonitorOpensAndReadsDetachedInventoryWithoutOpeningReportsOnMacOs() {
        if (!isMacOsHidHost()) return

        val native = KffiAppKitHidNativeFactory.open { }
        try {
            val devices = native.devices

            assertEquals(devices.size, devices.map(AppKitHidNativeDevice::key).toSet().size)
            assertTrue(devices.all { device -> device.key >= 0L })
        } finally {
            native.close()
        }
    }
}

private fun isMacOsHidHost(): Boolean = System.getProperty("os.name", "").let { name ->
    name.contains("Mac", ignoreCase = true) || name.contains("Darwin", ignoreCase = true)
}
