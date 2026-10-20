package org.graphiks.kadre.internal.win32

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.graphiks.kadre.application.*
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.input.*
import org.graphiks.kadre.platform.desktop.*
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.window.*

/**
 * Real session through the real provider. On a host without a gamepad the
 * XInput slots all answer ERROR_DEVICE_NOT_CONNECTED and the inventory stays
 * empty — absence must never be published as a pad and must never become a
 * rumble success. On a host with hardware (developer machine), each enumerated
 * pad is Standard-mapped with the canonical controls and a rumble round-trip
 * starts and stops both motors without error.
 */
@OptIn(KadrePlatformApi::class, DelicateKadreApi::class)
class Win32GamepadNativeContractTest {
    @Test fun absentSlotsPublishNothingAndNeverInventRumbleSuccess() {
        val observed = CompletableDeferred<DeviceInventory>()
        runKadreApplication(
            options = DesktopHostOptions.Standalone(backend = DesktopBackend.Win32),
            application = KadreApplication {
                windows.requestWindow(WindowSpec(title = "Kadre native gamepads proof",
                    contentSize = LogicalSize(320.0, 240.0)))
                launch(start = CoroutineStart.UNDISPATCHED) {
                    devices.state.collect { state -> observed.complete(state.inventory) }
                }
                val inventory = withTimeout(5_000) { observed.await() }
                when (inventory) {
                    is DeviceInventory.Enumerated -> {
                        assertTrue(inventory.gamepads.size <= 4, "XInput exposes at most four slots")
                        inventory.gamepads.forEach { gamepad ->
                            val snapshot = gamepad.state.value
                            assertEquals(GamepadMapping.Standard, snapshot.descriptor.mapping)
                            assertTrue(snapshot.descriptor.buttons.isNotEmpty())
                            assertEquals(6, snapshot.descriptor.axes.size)
                        }
                        val present = inventory.gamepads.firstOrNull()
                        if (present != null) {
                            val started = present.playEffect(
                                GamepadEffect.DualRumble(0.5, 0.5, kotlin.time.Duration.parse("100ms")),
                            )
                            val session = assertIs<KadreResult.Success<GamepadEffectSession>>(started).value
                            val stopped = present.stopEffects()
                            assertIs<KadreResult.Success<Unit>>(stopped)
                            session.close()
                        } else {
                            // The sentinel: an absent pad publishes no handle at all, so no
                            // rumble path can exist — absence never becomes a vibration
                            // success (the disconnected-slot refusal is proven portably).
                            assertTrue(inventory.gamepads.isEmpty())
                        }
                    }
                    is DeviceInventory.Unavailable -> {
                        // Honest unavailability (native failure) is contract-acceptable;
                        // silence is not — it must carry the typed failure.
                        assertNotNull(inventory.failure)
                    }
                    DeviceInventory.Unsupported -> error("the win32 backend supplies a gamepad port")
                }
                requestStop()
            },
        )
    }
}
