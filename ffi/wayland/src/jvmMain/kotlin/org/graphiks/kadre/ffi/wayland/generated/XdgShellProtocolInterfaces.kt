package org.graphiks.kadre.ffi.wayland.generated

import java.lang.foreign.*
import java.lang.foreign.ValueLayout.*
import java.lang.foreign.MemoryLayout.PathElement.*
import org.graphiks.kadre.ffi.wayland.libWaylandClient

// Arena.global() — wl_interface structs live for the process lifetime;
// libwayland holds pointers to them. A scoped/auto arena would risk
// use-after-free when the GC reclaims the arena.
private val ARENA = Arena.global()

val xdg_wm_base_interface: MemorySegment by lazy { build_xdg_wm_base() }
val xdg_positioner_interface: MemorySegment by lazy { build_xdg_positioner() }
val xdg_surface_interface: MemorySegment by lazy { build_xdg_surface() }
val xdg_toplevel_interface: MemorySegment by lazy { build_xdg_toplevel() }
val xdg_popup_interface: MemorySegment by lazy { build_xdg_popup() }
val zxdg_decoration_manager_v1_interface: MemorySegment by lazy { build_zxdg_decoration_manager_v1() }
val zxdg_toplevel_decoration_v1_interface: MemorySegment by lazy { build_zxdg_toplevel_decoration_v1() }

private val wl_output_interface: MemorySegment by lazy {
    val lib = libWaylandClient ?: error("libwayland-client.so.0 not available")
    lib.find("wl_output_interface").orElseThrow()
}
private val wl_seat_interface: MemorySegment by lazy {
    val lib = libWaylandClient ?: error("libwayland-client.so.0 not available")
    lib.find("wl_seat_interface").orElseThrow()
}
private val wl_surface_interface: MemorySegment by lazy {
    val lib = libWaylandClient ?: error("libwayland-client.so.0 not available")
    lib.find("wl_surface_interface").orElseThrow()
}

private val MSG_LAYOUT = MemoryLayout.structLayout(
    ADDRESS.withName("name"), ADDRESS.withName("signature"), ADDRESS.withName("types"))
    .withByteAlignment(8)
private val IFACE_LAYOUT = MemoryLayout.structLayout(
    ADDRESS.withName("name"),
    JAVA_INT.withName("version"),
    JAVA_INT.withName("method_count"),
    ADDRESS.withName("methods").withByteAlignment(8),
    JAVA_INT.withName("event_count"),
    MemoryLayout.paddingLayout(4),
    ADDRESS.withName("events").withByteAlignment(8))
    .withByteAlignment(8)

private fun build_xdg_wm_base(): MemorySegment = iface("xdg_wm_base", 7, arrayOf(
    msg("destroy", ""),
    msg("create_positioner", "n", xdg_positioner_interface),
    msg("get_xdg_surface", "no", xdg_surface_interface, wl_surface_interface),
    msg("pong", "u", MemorySegment.NULL)
), arrayOf(
    msg("ping", "u", MemorySegment.NULL)
))

private fun build_xdg_positioner(): MemorySegment = iface("xdg_positioner", 7, arrayOf(
    msg("destroy", ""),
    msg("set_size", "ii", MemorySegment.NULL, MemorySegment.NULL),
    msg("set_anchor_rect", "iiii", MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL),
    msg("set_anchor", "u", MemorySegment.NULL),
    msg("set_gravity", "u", MemorySegment.NULL),
    msg("set_constraint_adjustment", "u", MemorySegment.NULL),
    msg("set_offset", "ii", MemorySegment.NULL, MemorySegment.NULL),
    msg("set_reactive", ""),
    msg("set_parent_size", "ii", MemorySegment.NULL, MemorySegment.NULL),
    msg("set_parent_configure", "u", MemorySegment.NULL)
), arrayOf(
))

private fun build_xdg_surface(): MemorySegment = iface("xdg_surface", 7, arrayOf(
    msg("destroy", ""),
    msg("get_toplevel", "n", xdg_toplevel_interface),
    msg("get_popup", "noo", xdg_popup_interface, MemorySegment.NULL, xdg_positioner_interface),
    msg("set_window_geometry", "iiii", MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL),
    msg("ack_configure", "u", MemorySegment.NULL)
), arrayOf(
    msg("configure", "u", MemorySegment.NULL)
))

private fun build_xdg_toplevel(): MemorySegment = iface("xdg_toplevel", 7, arrayOf(
    msg("destroy", ""),
    msg("set_parent", "o", MemorySegment.NULL),
    msg("set_title", "s", MemorySegment.NULL),
    msg("set_app_id", "s", MemorySegment.NULL),
    msg("show_window_menu", "ouii", wl_seat_interface, MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL),
    msg("move", "ou", wl_seat_interface, MemorySegment.NULL),
    msg("resize", "ouu", wl_seat_interface, MemorySegment.NULL, MemorySegment.NULL),
    msg("set_max_size", "ii", MemorySegment.NULL, MemorySegment.NULL),
    msg("set_min_size", "ii", MemorySegment.NULL, MemorySegment.NULL),
    msg("set_maximized", ""),
    msg("unset_maximized", ""),
    msg("set_fullscreen", "o", wl_output_interface),
    msg("unset_fullscreen", ""),
    msg("set_minimized", "")
), arrayOf(
    msg("configure", "iia", MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL),
    msg("close", ""),
    msg("configure_bounds", "ii", MemorySegment.NULL, MemorySegment.NULL),
    msg("wm_capabilities", "a", MemorySegment.NULL)
))

private fun build_xdg_popup(): MemorySegment = iface("xdg_popup", 7, arrayOf(
    msg("destroy", ""),
    msg("grab", "ou", wl_seat_interface, MemorySegment.NULL),
    msg("reposition", "ou", xdg_positioner_interface, MemorySegment.NULL)
), arrayOf(
    msg("configure", "iiii", MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL),
    msg("popup_done", ""),
    msg("repositioned", "u", MemorySegment.NULL)
))

private fun build_zxdg_decoration_manager_v1(): MemorySegment = iface("zxdg_decoration_manager_v1", 1, arrayOf(
    msg("destroy", ""),
    msg("get_toplevel_decoration", "no", zxdg_toplevel_decoration_v1_interface, xdg_toplevel_interface)
), arrayOf(
))

private fun build_zxdg_toplevel_decoration_v1(): MemorySegment = iface("zxdg_toplevel_decoration_v1", 1, arrayOf(
    msg("destroy", ""),
    msg("set_mode", "u", MemorySegment.NULL),
    msg("unset_mode", "")
), arrayOf(
    msg("configure", "u", MemorySegment.NULL)
))

private fun msg(name: String, signature: String, vararg types: MemorySegment): MemorySegment {
    val seg = ARENA.allocate(MSG_LAYOUT)
    seg.set(ADDRESS, 0L, ARENA.allocateFrom(name))
    seg.set(ADDRESS, 8L, ARENA.allocateFrom(signature))
    if (types.isEmpty()) {
        seg.set(ADDRESS, 16L, MemorySegment.NULL)
    } else {
        val arr = ARENA.allocate(ADDRESS, (types.size + 1).toLong())
        for (i in types.indices) arr.set(ADDRESS, (i * 8).toLong(), types[i])
        arr.set(ADDRESS, (types.size * 8).toLong(), MemorySegment.NULL)
        seg.set(ADDRESS, 16L, arr)
    }
    return seg
}

private fun iface(
    name: String, version: Int,
    methods: Array<MemorySegment>,
    events: Array<MemorySegment>
): MemorySegment {
    val seg = ARENA.allocate(IFACE_LAYOUT)
    seg.set(ADDRESS, 0L, ARENA.allocateFrom(name))
    seg.set(JAVA_INT, 8L, version)
    seg.set(JAVA_INT, 12L, methods.size)
    if (methods.isNotEmpty()) {
        val arr = ARENA.allocate(MSG_LAYOUT, methods.size.toLong())
        for (i in methods.indices) arr.asSlice(i * 24L).copyFrom(methods[i])
        seg.set(ADDRESS, 16L, arr)
    } else {
        seg.set(ADDRESS, 16L, MemorySegment.NULL)
    }
    seg.set(JAVA_INT, 24L, events.size)
    if (events.isNotEmpty()) {
        val arr = ARENA.allocate(MSG_LAYOUT, events.size.toLong())
        for (i in events.indices) arr.asSlice(i * 24L).copyFrom(events[i])
        seg.set(ADDRESS, 32L, arr)
    } else {
        seg.set(ADDRESS, 32L, MemorySegment.NULL)
    }
    return seg
}
