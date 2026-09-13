@file:OptIn(org.graphiks.kffi.objc.PlatformAvailability::class)

package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.input.GamepadHapticLocality
import org.graphiks.kffi.objc.managed.GameControllerDescriptor
import org.graphiks.kffi.objc.managed.GameControllerHapticLocality
import org.graphiks.kffi.objc.managed.GameControllerPhysicalInput
import org.graphiks.kffi.objc.managed.GameControllerProfile
import kotlin.test.Test
import kotlin.test.assertEquals

class KffiAppKitGameControllerNativeTest {
    @Test
    fun detachedKffiSnapshotRetainsProfileNamesAndAdvertisedHapticLocalities() {
        val mapped = GameControllerDescriptor(
            vendorName = "Controller vendor",
            productCategory = "Controller category",
            profile = GameControllerProfile.Standard,
            hapticLocalities = setOf(
                GameControllerHapticLocality.Default,
                GameControllerHapticLocality.LeftHandle,
            ),
        ).toAppKitGameControllerNativeController(
            key = 7L,
            initialInputs = listOf(
                GameControllerPhysicalInput.Axis(setOf("GCInputLeftThumbstickX"), 0.5f),
            ),
        )

        assertEquals("Controller vendor", mapped.name)
        assertEquals(AppKitGameControllerProfile.Standard, mapped.profile)
        assertEquals(
            setOf(GamepadHapticLocality.Default, GamepadHapticLocality.LeftHandle),
            mapped.hapticLocalities,
        )
        assertEquals(
            AppKitGameControllerPhysicalInput.Axis(setOf("GCInputLeftThumbstickX"), 0.5),
            mapped.initialInputs.single(),
        )
    }

    @Test
    fun managedGameControllerMonitorOpensAndReadsWithoutSynthesizingInputOnMacOs() {
        if (!isMacOsGameControllerHost()) return

        val native = KffiAppKitGameControllerNativeFactory.open { }
        try {
            native.controllers.forEach { controller ->
                kotlin.test.assertTrue(controller.key >= 0L)
            }
        } finally {
            native.close()
        }
    }
}

private fun isMacOsGameControllerHost(): Boolean = System.getProperty("os.name", "").let { name ->
    name.contains("Mac", ignoreCase = true) || name.contains("Darwin", ignoreCase = true)
}
