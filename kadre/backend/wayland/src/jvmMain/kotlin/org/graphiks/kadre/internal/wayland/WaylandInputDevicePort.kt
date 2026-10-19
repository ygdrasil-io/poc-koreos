package org.graphiks.kadre.internal.wayland

import java.util.concurrent.atomic.AtomicBoolean
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.input.InputDeviceDescriptor
import org.graphiks.kadre.input.InputDeviceKind
import org.graphiks.kadre.internal.runtime.InputDevicePort
import org.graphiks.kadre.internal.runtime.InputDevicePortDevice
import org.graphiks.kadre.internal.runtime.InputDevicePortEvent

/** One observed seat with its live capability projection. */
internal data class WaylandSeatDevice(
    val globalName: Int,
    val name: String?,
    val capabilities: Int,
)

/**
 * Honest seat-projection inventory (design phase 9, D2): the devices are
 * the OBSERVABLE wl_seat globals with their live capability projection —
 * explicitly not a complete physical-device inventory (libinput/udev needs
 * dedicated bindings and permissions; kffi gap open). Capability changes
 * republish the device with an InventoryChanged; a removed global is never
 * re-keyed. Reading after close yields an empty list and no events.
 */
internal class WaylandInputDevicePort(
    private val seatSnapshots: () -> List<WaylandSeatDevice>,
) : InputDevicePort {

    private val closed = AtomicBoolean(false)
    private val observers = ArrayList<(InputDevicePortEvent) -> Unit>()
    private val lock = Any()

    override val inventoryFailure: KadreFailure?
        get() = null // seat projection enumeration succeeds by construction

    override val devices: List<InputDevicePortDevice>
        get() {
            if (closed.get()) return emptyList()
            return seatSnapshots().map { seat ->
                InputDevicePortDevice(
                    key = seat.globalName.toLong(),
                    descriptor = InputDeviceDescriptor(
                        name = seat.name,
                        kind = seatKind(seat.capabilities),
                    ),
                )
            }
        }

    override fun installObserver(
        observer: (InputDevicePortEvent) -> Unit,
    ): AutoCloseable {
        if (closed.get()) return AutoCloseable { }
        synchronized(lock) { observers.add(observer) }
        return AutoCloseable {
            synchronized(lock) { observers.remove(observer) }
        }
    }

    /** The driver calls this on seat add/remove/capability change. */
    internal fun seatInventoryChanged() {
        if (closed.get()) return
        val snapshot = synchronized(lock) { ArrayList(observers) }
        snapshot.forEach { it(InputDevicePortEvent.InventoryChanged) }
    }

    internal fun seatConnected(seat: WaylandSeatDevice) {
        if (closed.get()) return
        val snapshot = synchronized(lock) { ArrayList(observers) }
        snapshot.forEach { observer ->
            observer(
                InputDevicePortEvent.Connected(
                    InputDevicePortDevice(
                        key = seat.globalName.toLong(),
                        descriptor = InputDeviceDescriptor(seat.name, seatKind(seat.capabilities)),
                    ),
                ),
            )
        }
    }

    internal fun seatDisconnected(globalName: Int) {
        if (closed.get()) return
        val snapshot = synchronized(lock) { ArrayList(observers) }
        snapshot.forEach { it(InputDevicePortEvent.Disconnected(globalName.toLong())) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(lock) { observers.clear() }
    }

    private fun seatKind(capabilities: Int): InputDeviceKind = when {
        capabilities and org.graphiks.kffi.wayland.managed.WaylandSeatCapabilities.KEYBOARD != 0 &&
            capabilities and org.graphiks.kffi.wayland.managed.WaylandSeatCapabilities.POINTER != 0 ->
            InputDeviceKind.Keyboard // keyboard wins for combo seats: the
            // projection reports the primary interaction kind
        capabilities and org.graphiks.kffi.wayland.managed.WaylandSeatCapabilities.KEYBOARD != 0 ->
            InputDeviceKind.Keyboard
        capabilities and org.graphiks.kffi.wayland.managed.WaylandSeatCapabilities.POINTER != 0 ->
            InputDeviceKind.Mouse
        else -> InputDeviceKind.Other
    }
}

