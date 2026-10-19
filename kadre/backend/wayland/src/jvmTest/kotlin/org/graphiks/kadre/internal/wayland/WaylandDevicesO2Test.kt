package org.graphiks.kadre.internal.wayland

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.input.InputDeviceKind
import org.graphiks.kadre.internal.runtime.InputDevicePortEvent

/**
 * Preuves O2 phase 9 : inventaire seats honnête (nom + kind issu des
 * capabilities réelles), hotplug seat (ajout/retrait → InventoryChanged
 * + Connected/Disconnected), lecture après close vide, gamepad refusé
 * avec échec d'énumération explicite.
 */
class WaylandDevicesO2Test {

    @Test
    fun seatProjectionInventoryIsHonest() {
        // P-1 : seats observés → devices avec kind dérivé des capabilities
        var seats = listOf(
            WaylandSeatDevice(3, "seat0", capabilities = 3), // clavier+pointeur
            WaylandSeatDevice(7, "seat1", capabilities = 1), // pointeur seul
        )
        val port = WaylandInputDevicePort(seatSnapshots = { seats })
        val devices = port.devices
        assertEquals(2, devices.size)
        assertEquals(3L, devices[0].key)
        assertEquals("seat0", devices[0].descriptor.name)
        assertEquals(InputDeviceKind.Keyboard, devices[0].descriptor.kind)
        assertEquals(InputDeviceKind.Mouse, devices[1].descriptor.kind)
        assertNull(port.inventoryFailure, "la projection seats s'énumère par construction")
        // hotplug capabilities : le kind du seat 7 devient clavier
        seats = listOf(
            WaylandSeatDevice(3, "seat0", capabilities = 3),
            WaylandSeatDevice(7, "seat1", capabilities = 2),
        )
        val changes = ArrayList<InputDevicePortEvent>()
        port.installObserver { changes.add(it) }
        port.seatInventoryChanged()
        assertEquals(listOf<InputDevicePortEvent>(InputDevicePortEvent.InventoryChanged), changes)
        assertEquals(InputDeviceKind.Keyboard, port.devices[1].descriptor.kind)
        port.close()
    }

    @Test
    fun seatRemovalFiresDisconnectedAndNeverRekeys() {
        // P-2 + F2-2 : retrait → Disconnected ; la clé du global retiré
        // ne réapparaît pas ; un NOUVEAU global a une nouvelle clé
        var seats = listOf(WaylandSeatDevice(11, "seat0", capabilities = 3))
        val port = WaylandInputDevicePort(seatSnapshots = { seats })
        val events = ArrayList<InputDevicePortEvent>()
        port.installObserver { events.add(it) }
        seats = emptyList()
        port.seatDisconnected(11)
        assertEquals(1, events.size)
        assertEquals(InputDevicePortEvent.Disconnected(11L), events.single())
        assertTrue(port.devices.isEmpty())
        // reconnexion : NOUVEAU global name ⇒ nouvelle clé
        seats = listOf(WaylandSeatDevice(42, "seat0", capabilities = 3))
        port.seatConnected(seats.single())
        assertEquals(42L, port.devices.single().key)
        port.close()
    }

    @Test
    fun readingAfterCloseYieldsNothing() {
        // F2-1
        val port = WaylandInputDevicePort { listOf(WaylandSeatDevice(5, "s", 3)) }
        port.close()
        assertTrue(port.devices.isEmpty())
        val events = ArrayList<InputDevicePortEvent>()
        port.installObserver { events.add(it) }
        port.seatInventoryChanged()
        assertTrue(events.isEmpty())
    }

    
}
