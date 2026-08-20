package org.graphiks.kadre.wayland

import org.graphiks.kadre.core.DeviceEvent
import org.graphiks.kadre.core.DeviceEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class WaylandDeviceFilterTest {
    @Test
    fun `an installed listener observes live WhenFocused Never and Always transitions`() {
        val observed = mutableListOf<DeviceEvent>()
        val filter = WaylandDeviceFilter(DeviceEvents.WhenFocused)
        val listener = filter.listener(observed::add)
        val first = DeviceEvent.PointerMotion(1.0, 2.0)
        val suppressed = DeviceEvent.PointerMotion(3.0, 4.0)
        val last = DeviceEvent.PointerMotion(5.0, 6.0)

        listener(first)
        filter.update(DeviceEvents.Never)
        listener(suppressed)
        filter.update(DeviceEvents.Always)
        listener(last)

        assertEquals(listOf<DeviceEvent>(first, last), observed)
        assertEquals(DeviceEvents.Always, filter.current)
    }
}
