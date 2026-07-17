package org.graphiks.kadre.wayland

import org.graphiks.kadre.core.DeviceEvent
import org.graphiks.kadre.core.DeviceEvents

/** Mutable device-event policy shared by the loop and already-installed listeners. */
internal class WaylandDeviceFilter(
    initial: DeviceEvents = DeviceEvents.WhenFocused,
) {
    @Volatile
    var current: DeviceEvents = initial
        private set

    fun update(mode: DeviceEvents) {
        current = mode
    }

    /**
     * Creates a stable native-listener sink that reads [current] for every dispatch.
     * Wayland only delivers keyboard events to focused surfaces, so `WhenFocused`
     * and `Always` both dispatch while `Never` suppresses the raw event.
     */
    fun listener(sink: (DeviceEvent) -> Unit): (DeviceEvent) -> Unit = { event ->
        if (current != DeviceEvents.Never) sink(event)
    }
}
