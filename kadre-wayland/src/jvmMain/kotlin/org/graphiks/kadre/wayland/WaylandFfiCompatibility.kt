package org.graphiks.kadre.wayland

import java.lang.foreign.MemorySegment
import org.graphiks.kffi.wayland.generated.xdg_activation_token_v1_interface
import org.graphiks.kffi.wayland.generated.xdg_activation_v1_interface
import org.graphiks.kffi.wayland.generated.xdg_toplevel_icon_manager_v1_interface
import org.graphiks.kffi.wayland.generated.xdg_toplevel_icon_v1_interface
import org.graphiks.kffi.wayland.generated.zwp_confined_pointer_v1_interface
import org.graphiks.kffi.wayland.generated.zwp_locked_pointer_v1_interface
import org.graphiks.kffi.wayland.generated.zwp_pointer_constraints_v1_interface
import org.graphiks.kffi.wayland.generated.zwp_pointer_constraints_v1_lifetime
import org.graphiks.kffi.wayland.generated.zwp_text_input_manager_v3_interface
import org.graphiks.kffi.wayland.generated.zwp_text_input_v3_interface
import org.graphiks.kffi.wayland.generated.zwlr_screencopy_frame_v1_interface
import org.graphiks.kffi.wayland.generated.zwlr_screencopy_manager_v1_interface

/**
 * Bridges the internal names used by Kadre to the generated API exposed by kffi-wayland.
 *
 * kffi-wayland keeps protocol interface descriptors in its generated package and exposes
 * the pointer-constraint enum with the values from the Wayland protocol XML.
 */
internal val xdgActivationTokenV1Interface: MemorySegment
    get() = xdg_activation_token_v1_interface

internal val xdgActivationV1Interface: MemorySegment
    get() = xdg_activation_v1_interface

internal val xdgToplevelIconManagerV1Interface: MemorySegment
    get() = xdg_toplevel_icon_manager_v1_interface

internal val xdgToplevelIconV1Interface: MemorySegment
    get() = xdg_toplevel_icon_v1_interface

internal val zwpConfinedPointerV1Interface: MemorySegment
    get() = zwp_confined_pointer_v1_interface

internal val zwpLockedPointerV1Interface: MemorySegment
    get() = zwp_locked_pointer_v1_interface

internal val zwpPointerConstraintsV1Interface: MemorySegment
    get() = zwp_pointer_constraints_v1_interface

internal val zwpTextInputManagerV3Interface: MemorySegment
    get() = zwp_text_input_manager_v3_interface

internal val zwpTextInputV3Interface: MemorySegment
    get() = zwp_text_input_v3_interface

internal val zwlrScreencopyFrameV1Interface: MemorySegment
    get() = zwlr_screencopy_frame_v1_interface

internal val zwlrScreencopyManagerV1Interface: MemorySegment
    get() = zwlr_screencopy_manager_v1_interface

/** `wl_proxy_marshal_flags` flag used to destroy the proxy after the request. */
internal const val WL_MARSHAL_FLAG_DESTROY: Int = 1

internal val ZWP_POINTER_CONSTRAINTS_V1_LIFETIME_ONESHOT: Int =
    zwp_pointer_constraints_v1_lifetime.ZWP_POINTER_CONSTRAINTS_V1_LIFETIME_ONESHOT.value.toInt()

internal val ZWP_POINTER_CONSTRAINTS_V1_LIFETIME_PERSISTENT: Int =
    zwp_pointer_constraints_v1_lifetime.ZWP_POINTER_CONSTRAINTS_V1_LIFETIME_PERSISTENT.value.toInt()
