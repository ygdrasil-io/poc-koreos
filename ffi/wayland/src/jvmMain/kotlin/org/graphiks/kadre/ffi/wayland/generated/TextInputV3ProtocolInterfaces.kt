package org.graphiks.kadre.ffi.wayland.generated

import org.graphiks.kadre.ffi.wayland.libWaylandClient
import java.lang.foreign.Arena
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT

// libwayland retains these protocol descriptors, so every segment must remain
// valid for the lifetime of the process.
private val ARENA = Arena.global()

val zwp_text_input_manager_v3_interface: MemorySegment by lazy {
    interfaceDescriptor(
        name = "zwp_text_input_manager_v3",
        version = 1,
        methods = arrayOf(
            message("destroy", ""),
            message("get_text_input", "no", zwp_text_input_v3_interface, wlSeatInterface),
        ),
        events = emptyArray(),
    )
}

val zwp_text_input_v3_interface: MemorySegment by lazy {
    interfaceDescriptor(
        name = "zwp_text_input_v3",
        version = 1,
        methods = arrayOf(
            message("destroy", ""),
            message("enable", ""),
            message("disable", ""),
            message("set_surrounding_text", "sii", NULL_TYPE, NULL_TYPE, NULL_TYPE),
            message("set_text_change_cause", "u", NULL_TYPE),
            message("set_content_type", "uu", NULL_TYPE, NULL_TYPE),
            message("set_cursor_rectangle", "iiii", NULL_TYPE, NULL_TYPE, NULL_TYPE, NULL_TYPE),
            message("commit", ""),
        ),
        events = arrayOf(
            message("enter", "o", wlSurfaceInterface),
            message("leave", "o", wlSurfaceInterface),
            message("preedit_string", "?sii", NULL_TYPE, NULL_TYPE, NULL_TYPE),
            message("commit_string", "?s", NULL_TYPE),
            message("delete_surrounding_text", "uu", NULL_TYPE, NULL_TYPE),
            message("done", "u", NULL_TYPE),
        ),
    )
}

private val wlSeatInterface: MemorySegment by lazy { waylandCoreInterface("wl_seat_interface", "wl_seat") }
private val wlSurfaceInterface: MemorySegment by lazy { waylandCoreInterface("wl_surface_interface", "wl_surface") }

private fun waylandCoreInterface(symbol: String, protocolName: String): MemorySegment =
    libWaylandClient?.find(symbol)?.orElse(null)
        ?: interfaceDescriptor(protocolName, version = 1, methods = emptyArray(), events = emptyArray())

private val NULL_TYPE = MemorySegment.NULL

private val MESSAGE_LAYOUT = MemoryLayout.structLayout(
    ADDRESS.withName("name"),
    ADDRESS.withName("signature"),
    ADDRESS.withName("types"),
).withByteAlignment(ADDRESS.byteAlignment())

private val INTERFACE_LAYOUT = MemoryLayout.structLayout(
    ADDRESS.withName("name"),
    JAVA_INT.withName("version"),
    JAVA_INT.withName("method_count"),
    ADDRESS.withName("methods").withByteAlignment(ADDRESS.byteAlignment()),
    JAVA_INT.withName("event_count"),
    MemoryLayout.paddingLayout(4),
    ADDRESS.withName("events").withByteAlignment(ADDRESS.byteAlignment()),
).withByteAlignment(ADDRESS.byteAlignment())

private fun message(
    name: String,
    signature: String,
    vararg types: MemorySegment,
): MemorySegment {
    val descriptor = ARENA.allocate(MESSAGE_LAYOUT)
    descriptor.set(ADDRESS, 0L, ARENA.allocateFrom(name))
    descriptor.set(ADDRESS, 8L, ARENA.allocateFrom(signature))
    descriptor.set(
        ADDRESS,
        16L,
        if (types.isEmpty()) {
            MemorySegment.NULL
        } else {
            ARENA.allocate(ADDRESS, (types.size + 1).toLong()).also { typeArray ->
                types.forEachIndexed { index, type ->
                    typeArray.set(ADDRESS, index * ADDRESS.byteSize(), type)
                }
                typeArray.set(ADDRESS, types.size * ADDRESS.byteSize(), MemorySegment.NULL)
            }
        },
    )
    return descriptor
}

private fun interfaceDescriptor(
    name: String,
    version: Int,
    methods: Array<MemorySegment>,
    events: Array<MemorySegment>,
): MemorySegment = ARENA.allocate(INTERFACE_LAYOUT).also { descriptor ->
    descriptor.set(ADDRESS, 0L, ARENA.allocateFrom(name))
    descriptor.set(JAVA_INT, 8L, version)
    descriptor.set(JAVA_INT, 12L, methods.size)
    descriptor.set(ADDRESS, 16L, messageTable(methods))
    descriptor.set(JAVA_INT, 24L, events.size)
    descriptor.set(ADDRESS, 32L, messageTable(events))
}

private fun messageTable(messages: Array<MemorySegment>): MemorySegment {
    if (messages.isEmpty()) return MemorySegment.NULL
    return ARENA.allocate(MESSAGE_LAYOUT, messages.size.toLong()).also { table ->
        messages.forEachIndexed { index, message ->
            table.asSlice(index * MESSAGE_LAYOUT.byteSize(), MESSAGE_LAYOUT.byteSize()).copyFrom(message)
        }
    }
}
