package org.graphiks.kadre.platform.android

import android.view.InputDevice
import android.view.MotionEvent
import org.graphiks.kadre.input.InputDeviceKind
import org.graphiks.kadre.internal.runtime.InputDevicePortEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Le port des périphériques d'entrée GÉNÉRIQUES Android (Task 2, phase 7) contre un fake du
 * seam [AndroidInputDeviceSource] — JAMAIS le SDK (le stub android.jar lance à la construction,
 * la couture `Any`/primitives existe pour cela, cf. `AndroidDisplayPortTest`). Les cas du plan :
 *
 * 1. `initialEnumerationAndListenerEvents` — l'énumération initiale `getDeviceIds()` (clavier +
 *    manette du fake) → l'inventaire ; added/removed du listener multi-cast → les événements ;
 *    AUCUN abonnement avant l'install, AUCUN événement pour l'énumération initiale (la lecture
 *    de `devices` EST la connexion — le runtime dédup, miroir du hub web) ;
 * 2. `kindMappingFromSources` — le kind GÉNÉRIQUE depuis le bitmask `sources` : Keyboard, Mouse,
 *    Touchscreen, Touchpad, Pen, et le carve-out de la règle : un device PORTEUR de sources
 *    gamepad/joystick est `Other` ici (la manette est un Gamepad côté [AndroidGamepadPort],
 *    jamais un device générique typé) ;
 * 3. `closeUnregistersTheListenerAndStopsDelivering` — le miroir display : le close
 *    désenregistre exactement une fois et un fire tardif ne publie rien (le seam est PARTAGÉ
 *    avec le port gamepad : le port ne le ferme JAMAIS, c'est le tour du propriétaire).
 */
class AndroidInputDevicePortTest {

    /** Le double du seam SDK : des devices pilotables, des fires manuels, des vibreurs enregistrés. */
    private class FakeInputDeviceSource : AndroidInputDeviceSource {
        val listeners = mutableListOf<(Int) -> Unit>()
        var unregisterCount = 0
        var closeCount = 0
        val devices = linkedMapOf<Int, AndroidInputDeviceInfo>()
        val rumbleStarts = mutableListOf<Triple<Int, Long, Pair<Float, Float>>>()
        val oneShots = mutableListOf<Triple<Int, Long, Float>>()
        val cancels = mutableListOf<Int>()
        var vibrationAnswer = true

        override fun deviceIds(): List<Int> = devices.keys.toList()

        override fun deviceInfo(deviceId: Int): AndroidInputDeviceInfo? = devices[deviceId]

        override fun registerListener(listener: (Int) -> Unit) {
            listeners += listener
        }

        override fun unregisterListener(listener: (Int) -> Unit) {
            unregisterCount += 1
            listeners.remove(listener)
        }

        override fun startManagedRumble(deviceId: Int, durationMs: Long, strongMagnitude: Float, weakMagnitude: Float): Boolean {
            rumbleStarts += Triple(deviceId, durationMs, strongMagnitude to weakMagnitude)
            return vibrationAnswer
        }

        override fun startOneShot(deviceId: Int, durationMs: Long, amplitude: Float): Boolean {
            oneShots += Triple(deviceId, durationMs, amplitude)
            return vibrationAnswer
        }

        override fun cancelVibration(deviceId: Int) {
            cancels += deviceId
        }

        override fun close() {
            closeCount += 1
        }

        /** Le fire du listener multi-cast — le même id pour added/removed/changed. */
        fun fire(deviceId: Int) = listeners.toList().forEach { it(deviceId) }
    }

    private fun keyboard(id: Int = 1): AndroidInputDeviceInfo = AndroidInputDeviceInfo(
        deviceId = id,
        name = "AT Translated Set 2 keyboard",
        sources = InputDevice.SOURCE_KEYBOARD,
        motionRangeAxes = emptyList(),
        vibratorIds = emptyList(),
        hasVibrator = false,
    )

    private fun gamepad(
        id: Int = 2,
        name: String? = "Fake Pad",
        axes: List<Int> = listOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y),
        vibratorIds: List<Int> = emptyList(),
        hasVibrator: Boolean = false,
    ): AndroidInputDeviceInfo = AndroidInputDeviceInfo(
        deviceId = id,
        name = name,
        sources = InputDevice.SOURCE_GAMEPAD,
        motionRangeAxes = axes,
        vibratorIds = vibratorIds,
        hasVibrator = hasVibrator,
    )

    // -----------------------------------------------------------------------
    // Cas 1 — l'énumération initiale, puis les événements du listener multi-cast
    // -----------------------------------------------------------------------

    @Test
    fun initialEnumerationAndListenerEvents() {
        val source = FakeInputDeviceSource()
        source.devices[1] = keyboard(1)
        source.devices[2] = gamepad(2)
        val port = AndroidInputDevicePort(source)

        // L'inventaire initial vient de getDeviceIds() — et le port ne s'est JAMAIS abonné
        // avant l'install : les événements ne précèdent jamais leur observateur.
        assertTrue(source.listeners.isEmpty(), "the port never subscribes before the install")
        assertEquals(listOf(1L, 2L), port.devices.map { it.key })
        assertEquals("AT Translated Set 2 keyboard", port.devices.first().descriptor.name)

        val events = mutableListOf<InputDevicePortEvent>()
        port.installObserver { events += it }
        assertEquals(1, source.listeners.size, "the install registers exactly one listener")
        assertEquals(0, events.size, "the initial enumeration is the inventory read, never an event")

        // Added — l'id ajouté produit UN Connected portant le descripteur traduit.
        source.devices[3] = keyboard(3)
        source.fire(3)
        val added = assertIs<InputDevicePortEvent.Connected>(events.single())
        assertEquals(3L, added.device.key)
        assertEquals(InputDeviceKind.Keyboard, added.device.descriptor.kind)
        assertEquals(listOf(1L, 2L, 3L), port.devices.map { it.key })

        // Removed — l'id disparu produit UN Disconnected ; l'inventaire suit.
        source.devices.remove(1)
        source.fire(1)
        assertEquals(1L, assertIs<InputDevicePortEvent.Disconnected>(events[1]).key)
        assertEquals(listOf(2L, 3L), port.devices.map { it.key })

        // Changed — le listener multi-cast porte le même fire : la table se resynchronise
        // (descripteur mis à jour) sans événement — le port n'a que Connected/Disconnected.
        source.devices[2] = gamepad(2, name = "Fake Pad (mode clavier)")
        source.fire(2)
        assertEquals(2, events.size, "a changed device is not a connection event")
        assertEquals(
            "Fake Pad (mode clavier)",
            port.devices.single { it.key == 2L }.descriptor.name,
            "the re-read inventory carries the changed facts",
        )
    }

    // -----------------------------------------------------------------------
    // Cas 2 — le kind générique depuis le bitmask sources
    // -----------------------------------------------------------------------

    @Test
    fun kindMappingFromSources() {
        val source = FakeInputDeviceSource()
        fun add(id: Int, sources: Int) {
            source.devices[id] = AndroidInputDeviceInfo(id, "device-$id", sources, emptyList(), emptyList(), false)
        }
        add(1, InputDevice.SOURCE_KEYBOARD)
        add(2, InputDevice.SOURCE_MOUSE)
        add(3, InputDevice.SOURCE_TOUCHSCREEN)
        add(4, InputDevice.SOURCE_TOUCHPAD)
        add(5, InputDevice.SOURCE_STYLUS)
        add(6, InputDevice.SOURCE_GAMEPAD)
        add(7, InputDevice.SOURCE_JOYSTICK)
        add(8, InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_KEYBOARD)
        add(9, 0)
        val port = AndroidInputDevicePort(source)

        val kinds = port.devices.associate { it.key to it.descriptor.kind }
        assertEquals(InputDeviceKind.Keyboard, kinds[1L])
        assertEquals(InputDeviceKind.Mouse, kinds[2L])
        assertEquals(InputDeviceKind.Touchscreen, kinds[3L])
        assertEquals(InputDeviceKind.Touchpad, kinds[4L])
        assertEquals(InputDeviceKind.Pen, kinds[5L])
        assertEquals(
            InputDeviceKind.Other,
            kinds[6L],
            "a gamepad-source device is Other in the GENERIC port — gamepads are Gamepads in the gamepad port",
        )
        assertEquals(InputDeviceKind.Other, kinds[7L])
        assertEquals(
            InputDeviceKind.Other,
            kinds[8L],
            "the gamepad carve-out wins over the other source bits — the gamepad identity is the gamepad port's business",
        )
        assertEquals(InputDeviceKind.Other, kinds[9L], "a sourceless device is Other, never invented into a class")
    }

    // -----------------------------------------------------------------------
    // Miroir display — le close désenregistre exactement une fois, ne ferme JAMAIS
    // le seam partagé, et un fire tardif ne publie rien
    // -----------------------------------------------------------------------

    @Test
    fun closeUnregistersTheListenerAndStopsDelivering() {
        val source = FakeInputDeviceSource()
        source.devices[1] = keyboard(1)
        source.devices[2] = gamepad(2)
        val port = AndroidInputDevicePort(source)
        val events = mutableListOf<InputDevicePortEvent>()
        port.installObserver { events += it }

        port.close()
        port.close()

        assertEquals(1, source.unregisterCount, "the SDK unregistration happens exactly once")
        assertTrue(source.listeners.isEmpty(), "the port's listener left the source")
        assertEquals(0, source.closeCount, "the seam is SHARED with the gamepad port — the port never closes it")

        source.devices[4] = keyboard(4)
        source.fire(4)
        assertEquals(0, events.size, "a late fire after close publishes nothing")
        assertEquals(listOf(1L, 2L, 4L), port.devices.map { it.key }, "the inventory read stays honest after close")
    }
}
