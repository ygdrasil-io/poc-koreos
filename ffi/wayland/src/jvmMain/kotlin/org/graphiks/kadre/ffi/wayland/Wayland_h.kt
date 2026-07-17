package org.graphiks.kadre.ffi.wayland

import org.graphiks.kadre.ffi.posix.PosixSymbols
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout.PathElement.groupElement
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

fun waylandNativeDisabled(): Boolean =
    System.getenv("KADRE_WAYLAND_DISABLE_NATIVE") == "1" ||
        System.getProperty("kadre.wayland.disableNative") == "true"

val libWaylandClient: SymbolLookup? by lazy {
    if (waylandNativeDisabled()) return@lazy null
    try {
        SymbolLookup.libraryLookup("libwayland-client.so.0", Arena.global())
    } catch (e: Throwable) {
        null
    }
}

val libXkbCommon: SymbolLookup? by lazy {
    if (waylandNativeDisabled()) return@lazy null
    try {
        SymbolLookup.libraryLookup("libxkbcommon.so.0", Arena.global())
    } catch (e: Throwable) {
        null
    }
}

private val linker: Linker = Linker.nativeLinker()

private fun SymbolLookup?.downcall(name: String, desc: FunctionDescriptor): MethodHandle? {
    this ?: return null
    return this.find(name).map { linker.downcallHandle(it, desc) }.orElse(null)
}

private fun SymbolLookup?.symbol(name: String): MemorySegment? =
    this?.find(name)?.orElse(null)

fun posixDowncall(name: String, desc: FunctionDescriptor): MethodHandle? =
    PosixSymbols.find(name)?.let { linker.downcallHandle(it, desc) }

fun upcallStub(
    handle: MethodHandle,
    descriptor: FunctionDescriptor,
    arena: java.lang.foreign.Arena,
): MemorySegment = linker.upcallStub(handle, descriptor, arena)

val wlRegistryInterface: MemorySegment? by lazy { libWaylandClient.symbol("wl_registry_interface") }

val wlCompositorInterface: MemorySegment? by lazy { libWaylandClient.symbol("wl_compositor_interface") }

val wlProxyGetVersion: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_get_version",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
}

val wlDisplayRoundtrip: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_display_roundtrip",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
}

val wlProxyMarshalNewId: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        ))
}

val wlProxyMarshalBind: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        ))
}

val wlDisplayConnect: MethodHandle? by lazy {
    libWaylandClient.downcall(
        "wl_display_connect",
        FunctionDescriptor.of(
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
        )
    )
}

val wlDisplayDisconnect: MethodHandle? by lazy {
    libWaylandClient.downcall(
        "wl_display_disconnect",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
        )
    )
}

val wlDisplayGetFd: MethodHandle? by lazy {
    libWaylandClient.downcall(
        "wl_display_get_fd",
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        )
    )
}

val wlDisplayDispatch: MethodHandle? by lazy {
    libWaylandClient.downcall(
        "wl_display_dispatch",
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        )
    )
}

val wlDisplayDispatchPending: MethodHandle? by lazy {
    libWaylandClient.downcall(
        "wl_display_dispatch_pending",
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        )
    )
}

val wlDisplayPrepareRead: MethodHandle? by lazy {
    libWaylandClient.downcall(
        "wl_display_prepare_read",
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        )
    )
}

val wlDisplayReadEvents: MethodHandle? by lazy {
    libWaylandClient.downcall(
        "wl_display_read_events",
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        )
    )
}

val wlDisplayCancelRead: MethodHandle? by lazy {
    libWaylandClient.downcall(
        "wl_display_cancel_read",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
        )
    )
}

val wlDisplayFlush: MethodHandle? by lazy {
    libWaylandClient.downcall(
        "wl_display_flush",
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        )
    )
}

val wlDisplayGetRegistry: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_display_get_registry",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS))
}

val wlRegistryAddListener: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_registry_add_listener",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS))
}

val wlRegistryBind: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_registry_bind",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT))
}

val wlProxyDestroy: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_destroy",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS))
}

val wlProxyAddListener: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_add_listener",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS))
}

val wlProxyMarshalFlagsGetXdgSurface: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
        ))
}

val wlSurfaceInterface: MemorySegment? by lazy {
    libWaylandClient?.find("wl_surface_interface")?.orElse(null)
}

val wlRegionInterface: MemorySegment? by lazy {
    libWaylandClient?.find("wl_region_interface")?.orElse(null)
}

val wlCompositorCreateSurface: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        ))
}

val wlCompositorCreateRegion: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        ))
}

val wlProxyMarshalFlagsVoid: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        ))
}

val wlProxyMarshalFlagsUint: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        ))
}

val wlProxyMarshalFlagsString: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        ))
}

val wlProxyMarshalFlagsObject: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        ))
}

val wlProxyMarshalFlagsFourInt: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        ))
}

val wlProxyMarshalFlagsTwoUint: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        ))
}

val wlProxyMarshalFlagsObjectUint: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
        ))
}

val wlProxyMarshalFlagsObjectUintTwoInt: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        ))
}

val wlProxyMarshalFlagsUintObjectTwoInt: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        ))
}

val wlProxyMarshalFlagsObjectTwoUint: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        ))
}

val wlProxyMarshalFlagsObjectTwoInt: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        ))
}

val wlProxyMarshalFlagsStringObject: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
        ))
}

val wlProxyMarshalFlagsUintObject: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        ))
}

val wlProxyMarshalFlagsUintTwoObjects: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
        ))
}

val wlProxyMarshalFlagsTwoObjects: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
        ))
}

val wlShmCreatePool: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        ))
}

val wlShmPoolCreateBuffer: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        ))
}

val libWaylandCursor: SymbolLookup? by lazy {
    if (waylandNativeDisabled()) return@lazy null
    try { SymbolLookup.libraryLookup("libwayland-cursor.so.0", Arena.global()) }
    catch (_: Throwable) { null }
}

val wlCursorThemeLoad: MethodHandle? by lazy {
    libWaylandCursor.downcall("wl_cursor_theme_load",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        ))
}

val wlCursorThemeGetCursor: MethodHandle? by lazy {
    libWaylandCursor.downcall("wl_cursor_theme_get_cursor",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
        ))
}

val wlCursorImageGetBuffer: MethodHandle? by lazy {
    libWaylandCursor.downcall("wl_cursor_image_get_buffer",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
        ))
}

val wlCursorThemeDestroy: MethodHandle? by lazy {
    libWaylandCursor.downcall("wl_cursor_theme_destroy",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
        ))
}

private val textInputArena: Arena = Arena.ofShared()

fun buildWaylandInterface(
    name: String,
    version: Int = 1,
    methodCount: Int = 0,
    eventCount: Int = 0,
): MemorySegment {
    val nameSeg = textInputArena.allocateFrom(name)
    val ptr: Long = ValueLayout.ADDRESS.byteSize()
    val iface = textInputArena.allocate(ptr + 4 + 4 + ptr + 4 + 4 + ptr)
    iface.set(ValueLayout.ADDRESS, 0L, nameSeg)
    iface.set(ValueLayout.JAVA_INT, ptr, version)
    iface.set(ValueLayout.JAVA_INT, ptr + 4, methodCount)
    iface.set(ValueLayout.ADDRESS, ptr + 8, MemorySegment.NULL)
    iface.set(ValueLayout.JAVA_INT, ptr + 8 + ptr, eventCount)
    iface.set(ValueLayout.ADDRESS, ptr + 8 + ptr + 8, MemorySegment.NULL)
    return iface
}

val xdgActivationV1Interface: MemorySegment by lazy {
    buildWaylandInterface("xdg_activation_v1", methodCount = 3, eventCount = 1)
}

val xdgActivationTokenV1Interface: MemorySegment by lazy {
    buildWaylandInterface("xdg_activation_token_v1", methodCount = 5, eventCount = 1)
}

val zwpTextInputManagerV3Interface: MemorySegment by lazy {
    buildWaylandInterface("zwp_text_input_manager_v3", methodCount = 2, eventCount = 0)
}

val zwpTextInputV3Interface: MemorySegment by lazy {
    buildWaylandInterface("zwp_text_input_v3", methodCount = 8, eventCount = 6)
}

val zwpPointerConstraintsV1Interface: MemorySegment by lazy {
    buildWaylandInterface("zwp_pointer_constraints_v1", methodCount = 3, eventCount = 0)
}

val zwpLockedPointerV1Interface: MemorySegment by lazy {
    buildWaylandInterface("zwp_locked_pointer_v1", methodCount = 3, eventCount = 2)
}

val zwpConfinedPointerV1Interface: MemorySegment by lazy {
    buildWaylandInterface("zwp_confined_pointer_v1", methodCount = 2, eventCount = 2)
}

const val ZWP_POINTER_CONSTRAINTS_V1_LIFETIME_ONESHOT: Int = 0
const val ZWP_POINTER_CONSTRAINTS_V1_LIFETIME_PERSISTENT: Int = 1

val wlPointerConstraintsLockPointer: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        ))
}

val wlPointerConstraintsConfinePointer: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        ))
}

val xdgToplevelIconManagerV1Interface: MemorySegment by lazy {
    buildWaylandInterface("xdg_toplevel_icon_manager_v1", methodCount = 3, eventCount = 0)
}

val xdgToplevelIconV1Interface: MemorySegment by lazy {
    buildWaylandInterface("xdg_toplevel_icon_v1", methodCount = 4, eventCount = 0)
}

val zwpInputManagerV3CreateTextInput: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
        ))
}

val extBackgroundEffectV1Interface: MemorySegment by lazy {
    buildWaylandInterface("ext_background_effect_v1", methodCount = 2, eventCount = 0)
}

val extBackgroundEffectSurfaceV1Interface: MemorySegment by lazy {
    buildWaylandInterface("ext_background_effect_surface_v1", methodCount = 1, eventCount = 0)
}

val extBackgroundEffectV1Create: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
        ))
}

val orgKdeKwinBlurManagerInterface: MemorySegment by lazy {
    buildWaylandInterface("org_kde_kwin_blur_manager", methodCount = 2, eventCount = 0)
}

val orgKdeKwinBlurInterface: MemorySegment by lazy {
    buildWaylandInterface("org_kde_kwin_blur", methodCount = 2, eventCount = 0)
}

val kwinBlurManagerCreate: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
        ))
}

fun wlSurfaceAttach(
    surfacePtr: Long,
    bufferPtr: Long,
    dx: Int,
    dy: Int,
) {
    val marshal = wlProxyMarshalFlagsObjectTwoInt ?: return
    try {
        marshal.invokeExact(
            MemorySegment.ofAddress(surfacePtr),
            1,
            MemorySegment.NULL,
            1,
            0,
            if (bufferPtr != 0L) MemorySegment.ofAddress(bufferPtr) else MemorySegment.NULL,
            dx,
            dy,
        )
    } catch (_: Throwable) { }
}

fun wlSurfaceDamage(
    surfacePtr: Long,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
) {
    val marshal = wlProxyMarshalFlagsFourInt ?: return
    try {
        marshal.invokeExact(
            MemorySegment.ofAddress(surfacePtr),
            2,
            MemorySegment.NULL,
            1,
            0,
            x,
            y,
            width,
            height,
        )
    } catch (_: Throwable) { }
}

private val pollCaptureErrno = Linker.Option.captureCallState("errno")
private val pollCaptureLayout = Linker.Option.captureStateLayout()
private val pollErrnoOffset = pollCaptureLayout.byteOffset(groupElement("errno"))

private val nativePoll: MethodHandle? by lazy {
    PosixSymbols.find("poll")?.let { symbol ->
        linker.downcallHandle(
            symbol,
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_INT,
            ),
            pollCaptureErrno,
        )
    }
}

data class WaylandNativePollResult(
    val value: Int,
    val errno: Int?,
)

/** Invokes POSIX poll(2) with the platform-sized nfds_t and captures errno atomically. */
fun invokeNativePoll(
    pollFds: MemorySegment,
    count: Long,
    timeoutMs: Int,
): WaylandNativePollResult = Arena.ofConfined().use { arena ->
    val poll = nativePoll
        ?: error("required POSIX symbol 'poll' is unavailable")
    val callState = arena.allocate(pollCaptureLayout)
    val result = poll.invokeExact(callState, pollFds, count, timeoutMs) as Int
    WaylandNativePollResult(
        value = result,
        errno = if (result < 0) {
            callState.get(ValueLayout.JAVA_INT, pollErrnoOffset)
        } else {
            null
        },
    )
}

val nativeEventfd: MethodHandle? by lazy {
    posixDowncall("eventfd", FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,
    ))
}

val nativeRead: MethodHandle? by lazy {
    posixDowncall("read", FunctionDescriptor.of(
        ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
        ValueLayout.JAVA_LONG,
    ))
}

val nativeWrite: MethodHandle? by lazy {
    posixDowncall("write", FunctionDescriptor.of(
        ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
        ValueLayout.JAVA_LONG,
    ))
}

val nativeClose: MethodHandle? by lazy {
    posixDowncall("close", FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,
    ))
}

val nativeMemfdCreate: MethodHandle? by lazy {
    posixDowncall("memfd_create", FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT,
    ))
}

val nativeFtruncate: MethodHandle? by lazy {
    posixDowncall("ftruncate", FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_LONG,
    ))
}

val nativeMmap: MethodHandle? by lazy {
    posixDowncall("mmap", FunctionDescriptor.of(
        ValueLayout.ADDRESS,
        ValueLayout.ADDRESS,
        ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_LONG,
    ))
}

val nativeMunmap: MethodHandle? by lazy {
    posixDowncall("munmap", FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
        ValueLayout.JAVA_LONG,
    ))
}

val xkbContextNew: MethodHandle? by lazy {
    libXkbCommon.downcall("xkb_context_new",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT))
}

val xkbKeymapNewFromString: MethodHandle? by lazy {
    libXkbCommon.downcall("xkb_keymap_new_from_string",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        ))
}

val xkbStateNew: MethodHandle? by lazy {
    libXkbCommon.downcall("xkb_state_new",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS))
}

val xkbKeymapUnref: MethodHandle? by lazy {
    libXkbCommon.downcall("xkb_keymap_unref",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS))
}

val xkbStateUnref: MethodHandle? by lazy {
    libXkbCommon.downcall("xkb_state_unref",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS))
}

val xkbComposeTableNewFromLocale: MethodHandle? by lazy {
    libXkbCommon.downcall("xkb_compose_table_new_from_locale",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
        ))
}

val xkbComposeStateNew: MethodHandle? by lazy {
    libXkbCommon.downcall("xkb_compose_state_new",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
        ))
}

val xkbComposeStateReset: MethodHandle? by lazy {
    libXkbCommon.downcall("xkb_compose_state_reset",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS))
}

val xkbComposeStateUnref: MethodHandle? by lazy {
    libXkbCommon.downcall("xkb_compose_state_unref",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS))
}

val xkbComposeTableUnref: MethodHandle? by lazy {
    libXkbCommon.downcall("xkb_compose_table_unref",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS))
}

val xkbContextUnref: MethodHandle? by lazy {
    libXkbCommon.downcall("xkb_context_unref",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS))
}

val wlDataDeviceManagerGetDataDevice: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
        ))
}

val wlDataOfferAccept: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        ))
}

val wlDataOfferReceive: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
        ))
}

val wlDataOfferFinish: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        ))
}

val nativePipe2: MethodHandle? by lazy {
    posixDowncall("pipe2", FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT,
    ))
}

const val O_CLOEXEC: Int = 0x80000

val nativeShmOpen: MethodHandle? by lazy {
    posixDowncall("shm_open", FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,
    ))
}

val nativeShmUnlink: MethodHandle? by lazy {
    posixDowncall("shm_unlink", FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
    ))
}

const val PROT_READ: Int = 1
const val PROT_WRITE: Int = 2
const val MAP_SHARED: Int = 1
const val MAP_FAILED_PTR: Long = -1L

const val O_RDWR: Int = 2
const val O_CREAT: Int = 64
const val O_EXCL: Int = 128

val wlSeatInterface: MemorySegment? by lazy { libWaylandClient.symbol("wl_seat_interface") }

val wlKeyboardInterface: MemorySegment? by lazy { libWaylandClient.symbol("wl_keyboard_interface") }

val wlPointerInterface: MemorySegment? by lazy { libWaylandClient.symbol("wl_pointer_interface") }

val wlTouchInterface: MemorySegment? by lazy { libWaylandClient.symbol("wl_touch_interface") }

val wlOutputInterface: MemorySegment? by lazy { libWaylandClient.symbol("wl_output_interface") }

val wlShmInterface: MemorySegment? by lazy { libWaylandClient.symbol("wl_shm_interface") }

val wlShmPoolInterface: MemorySegment? by lazy { libWaylandClient.symbol("wl_shm_pool_interface") }

val wlBufferInterface: MemorySegment? by lazy { libWaylandClient.symbol("wl_buffer_interface") }

val wlDataDeviceManagerInterface: MemorySegment? by lazy {
    libWaylandClient.symbol("wl_data_device_manager_interface")
}

val wlDataDeviceInterface: MemorySegment? by lazy {
    libWaylandClient.symbol("wl_data_device_interface")
}

val wlDataOfferInterface: MemorySegment? by lazy {
    libWaylandClient.symbol("wl_data_offer_interface")
}

val wlDataSourceInterface: MemorySegment? by lazy {
    libWaylandClient.symbol("wl_data_source_interface")
}

val wlSeatGetKeyboard: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        ))
}

val wlSeatGetPointer: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        ))
}

val wlSeatGetTouch: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        ))
}

const val POLLIN: Short = 1
const val POLLERR: Short = 0x08
const val POLLHUP: Short = 0x10
const val POLLNVAL: Short = 0x20

fun allocPollFd(arena: java.lang.foreign.Arena): java.lang.foreign.MemorySegment =
    arena.allocate(8L * 2, 4L)

fun setPollFd(seg: java.lang.foreign.MemorySegment, idx: Int, fd: Int, events: Short) {
    seg.set(ValueLayout.JAVA_INT, idx * 8L, fd)
    seg.set(ValueLayout.JAVA_SHORT, idx * 8L + 4, events)
}

fun getPollRevents(seg: java.lang.foreign.MemorySegment, idx: Int): Short =
    seg.get(ValueLayout.JAVA_SHORT, idx * 8L + 6)
