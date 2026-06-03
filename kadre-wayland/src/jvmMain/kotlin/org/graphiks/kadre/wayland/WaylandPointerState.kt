package org.graphiks.kadre.wayland

import java.util.concurrent.atomic.AtomicLong

/**
 * Minimal shared pointer state needed by xdg_toplevel interactive requests.
 *
 * Wayland requires the `wl_seat` and a recent button serial for
 * `xdg_toplevel.show_window_menu` / `move` / `resize`. The pointer listener
 * mirrors winit by remembering the latest button serial from pointer button events.
 */
internal object WaylandPointerState {
    private val seatPtr = AtomicLong(0L)
    private val focusedSurfacePtr = AtomicLong(0L)
    private val latestButtonSerial = AtomicLong(0L)

    fun updateSeat(ptr: Long) {
        seatPtr.set(ptr)
    }

    fun enterSurface(ptr: Long) {
        focusedSurfacePtr.set(ptr)
    }

    fun leaveSurface(ptr: Long) {
        focusedSurfacePtr.compareAndSet(ptr, 0L)
    }

    fun recordButton(serial: Int, button: Int, state: Int) {
        if (state == WL_POINTER_BUTTON_STATE_PRESSED || state == WL_POINTER_BUTTON_STATE_RELEASED) {
            latestButtonSerial.set(serial.toLong() and 0xFFFF_FFFFL)
        }
    }

    fun current(surfacePtr: Long): PointerRequestContext? {
        val seat = seatPtr.get()
        val serial = latestButtonSerial.get()
        val focusedSurface = focusedSurfacePtr.get()
        if (seat == 0L || serial == 0L || focusedSurface != surfacePtr) return null
        return PointerRequestContext(seat, serial.toInt())
    }

    data class PointerRequestContext(
        val seatPtr: Long,
        val serial: Int,
    )
}
