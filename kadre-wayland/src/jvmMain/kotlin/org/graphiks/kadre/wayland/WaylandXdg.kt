/**
 * xdg_shell decoration of a wl_surface: turns a bare surface into a real mapped toplevel and
 * routes the compositor's configure/close events back to Kadre.
 *
 * xdg_shell is a protocol extension: its request opcodes are known ([XdgShellConstants]) but the
 * `xdg_*_interface` tables needed by `wl_proxy_marshal_flags` are not exported by
 * libwayland-client. They come from the kextract-generated bindings ([generated] package).
 *
 * Handshake (xdg-shell protocol):
 *   xdg_wm_base.get_xdg_surface(surface)  → xdg_surface
 *   xdg_surface.get_toplevel()            → xdg_toplevel
 *   add listeners (surface.configure, toplevel.configure/close)
 *   wl_surface.commit + roundtrip         → first configure → ack_configure
 *
 * Per the protocol, the surface must be acked-configured before a buffer is attached, so the
 * roundtrip here completes that handshake before any rendering.
 */
package org.graphiks.kadre.wayland
import org.graphiks.kadre.ffi.wayland.*

import org.graphiks.kadre.ffi.wayland.generated.xdg_surface_interface
import org.graphiks.kadre.ffi.wayland.generated.xdg_toplevel_interface
import org.graphiks.kadre.ffi.wayland.generated.zxdg_toplevel_decoration_v1_interface
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.atomic.AtomicBoolean

/** wl_surface.commit opcode (core protocol). */
private const val WL_SURFACE_COMMIT: Int = 6

internal class XdgUpcallCallbacks(
    private val onResized: (Int, Int, Boolean) -> Unit,
    private val onStateConfigured: (WaylandToplevelConfigureStates) -> Unit,
    private val onClose: () -> Unit,
    private val onFailure: (Throwable) -> Unit,
) {
    private inline fun guarded(action: () -> Unit) {
        try {
            action()
        } catch (failure: Throwable) {
            try {
                onFailure(failure)
            } catch (_: Throwable) {
                // No Kotlin exception may cross a native upcall boundary.
            }
        }
    }

    fun resized(width: Int, height: Int, applyResizeIncrements: Boolean) = guarded {
        onResized(width, height, applyResizeIncrements)
    }

    fun stateConfigured(states: WaylandToplevelConfigureStates) = guarded {
        onStateConfigured(states)
    }

    fun close() = guarded(onClose)

    fun failure(failure: Throwable) {
        try {
            onFailure(failure)
        } catch (_: Throwable) {
            // No Kotlin exception may cross a native upcall boundary.
        }
    }
}

internal class XdgListenerLifetime private constructor(
    private val lease: WaylandNativeListenerLease,
    hasDecoration: Boolean,
) {
    private val decorationDestroyed = AtomicBoolean(!hasDecoration)
    private val toplevelDestroyed = AtomicBoolean(false)
    private val surfaceDestroyed = AtomicBoolean(false)
    private val released = AtomicBoolean(false)

    fun markDecorationDestroyed() {
        decorationDestroyed.set(true)
        releaseIfComplete()
    }

    fun markToplevelDestroyed() {
        toplevelDestroyed.set(true)
        releaseIfComplete()
    }

    fun markSurfaceDestroyed() {
        surfaceDestroyed.set(true)
        releaseIfComplete()
    }

    private fun releaseIfComplete() {
        if (
            decorationDestroyed.get() &&
            toplevelDestroyed.get() &&
            surfaceDestroyed.get() &&
            released.compareAndSet(false, true)
        ) {
            lease.releaseAfterProxyDestroyed()
        }
    }

    companion object {
        fun register(
            binding: AutoCloseable,
            nativeListenerLifetime: WaylandNativeListenerLifetime,
            hasDecoration: Boolean,
        ): XdgListenerLifetime = XdgListenerLifetime(
            lease = nativeListenerLifetime.register(binding),
            hasDecoration = hasDecoration,
        )
    }
}

internal fun rollbackXdgAcquisition(
    primary: Throwable,
    decorationPtr: Long,
    toplevelPtr: Long,
    surfacePtr: Long,
    destroyDecoration: (Long) -> Unit,
    destroyToplevel: (Long) -> Unit,
    destroySurface: (Long) -> Unit,
    closeArena: () -> Unit,
    listenerBinding: AutoCloseable? = null,
    nativeListenerLifetime: WaylandNativeListenerLifetime? = null,
) {
    var decorationDestroyed = decorationPtr == 0L
    var toplevelDestroyed = toplevelPtr == 0L
    var surfaceDestroyed = surfacePtr == 0L
    fun attempt(action: () -> Unit, onSuccess: () -> Unit) {
        try {
            action()
            onSuccess()
        } catch (failure: Throwable) {
            if (failure !== primary) primary.addSuppressed(failure)
        }
    }

    if (!decorationDestroyed) attempt(
        action = { destroyDecoration(decorationPtr) },
        onSuccess = { decorationDestroyed = true },
    )
    if (!toplevelDestroyed) attempt(
        action = { destroyToplevel(toplevelPtr) },
        onSuccess = { toplevelDestroyed = true },
    )
    if (!surfaceDestroyed) attempt(
        action = { destroySurface(surfacePtr) },
        onSuccess = { surfaceDestroyed = true },
    )

    val allProxiesDestroyed = decorationDestroyed && toplevelDestroyed && surfaceDestroyed
    attempt(
        action = {
            when {
                listenerBinding == null -> closeArena()
                allProxiesDestroyed -> listenerBinding.close()
                else -> checkNotNull(nativeListenerLifetime) {
                    "XDG listener lifetime required after rollback failure"
                }.deferUntilDisplayDisconnect(listenerBinding)
            }
        },
        onSuccess = {},
    )
}

internal fun performXdgSurfaceConfigure(
    ackConfigure: (() -> Unit)?,
    flushDisplay: (() -> Int)?,
) {
    checkNotNull(ackConfigure) { "xdg_surface.ack_configure unavailable" }.invoke()
    val flush = checkNotNull(flushDisplay) { "wl_display_flush unavailable after xdg_surface.configure" }
    val result = flush()
    check(result >= 0) { "wl_display_flush failed after xdg_surface.configure: $result" }
}

internal class XdgToplevel private constructor(
    private val displayPtr: Long,
    private val xdgSurfacePtr: Long,
    /** Returns the xdg_toplevel proxy pointer for platform extension use. */
    val xdgToplevelPtr: Long,
    private val xdgDecorationPtr: Long,
    private val version: Int,
    private val decorationVersion: Int,
    private val callbacks: XdgUpcallCallbacks,
    private val listenerLifetime: XdgListenerLifetime,
    private val arena: Arena,
) {
    private val toplevelDestroyStarted = AtomicBoolean(false)
    private val surfaceDestroyStarted = AtomicBoolean(false)
    @Volatile
    private var receivedInitialConfigure: Boolean = false

    // ── Native upcall targets (invoked by the compositor through the listener vtables) ──────

    /** xdg_surface.configure(serial): must ack, then the surface is considered configured. */
    @Suppress("UNUSED_PARAMETER")
    fun onSurfaceConfigure(data: MemorySegment, surface: MemorySegment, serial: Int) {
        try {
            val ack = wlProxyMarshalFlagsUint
            performXdgSurfaceConfigure(
                ackConfigure = ack?.let { handle ->
                    {
                        // invokeExact must be in statement position (not the lambda's return value).
                        handle.invokeExact(
                            MemorySegment.ofAddress(xdgSurfacePtr),
                            XDG_SURFACE_ACK_CONFIGURE,
                            MemorySegment.NULL,
                            version,
                            0,
                            serial,
                        )
                    }
                },
                flushDisplay = wlDisplayFlush?.let { flush ->
                    { flush.invokeExact(MemorySegment.ofAddress(displayPtr)) as Int }
                },
            )
            receivedInitialConfigure = true
        } catch (failure: Throwable) {
            callbacks.failure(failure)
        }
    }

    fun hasReceivedInitialConfigure(): Boolean =
        receivedInitialConfigure

    /** xdg_toplevel.configure(width, height, states): a (0,0) size means "pick your own". */
    @Suppress("UNUSED_PARAMETER")
    fun onToplevelConfigure(data: MemorySegment, tl: MemorySegment, width: Int, height: Int, states: MemorySegment) {
        try {
            val configureStates = waylandToplevelConfigureStates(states)
            callbacks.stateConfigured(configureStates)
            if (width > 0 && height > 0) {
                callbacks.resized(
                    width,
                    height,
                    waylandShouldApplyResizeIncrements(
                        isResizing = configureStates.resizing,
                        isMaximized = configureStates.maximized,
                        isFullscreen = configureStates.fullscreen,
                        isTiled = configureStates.tiled,
                    ),
                )
            }
        } catch (failure: Throwable) {
            callbacks.failure(failure)
        }
    }

    /** xdg_toplevel.close(): the user/compositor asked to close the window. */
    @Suppress("UNUSED_PARAMETER")
    fun onToplevelClose(data: MemorySegment, tl: MemorySegment) {
        callbacks.close()
    }

    /** xdg_toplevel.configure_bounds (since v4) — recommended max size; unused. */
    @Suppress("UNUSED_PARAMETER")
    fun onToplevelConfigureBounds(data: MemorySegment, tl: MemorySegment, width: Int, height: Int) { /* no-op */ }

    /** xdg_toplevel.wm_capabilities (since v5) — available compositor actions; unused. */
    @Suppress("UNUSED_PARAMETER")
    fun onToplevelWmCapabilities(data: MemorySegment, tl: MemorySegment, caps: MemorySegment) { /* no-op */ }

    /** zxdg_toplevel_decoration_v1.configure(mode): compositor chose a decoration mode. */
    @Suppress("UNUSED_PARAMETER")
    fun onDecorationConfigure(data: MemorySegment, decoration: MemorySegment, mode: Int) { /* no-op */ }

    /** Sets the minimum size via xdg_toplevel.set_min_size(width, height). */
    fun setMinSize(width: Int, height: Int) {
        val handle = wlProxyMarshalFlagsTwoUint ?: return
        runCatching {
            handle.invokeExact(
                MemorySegment.ofAddress(xdgToplevelPtr), XDG_TOPLEVEL_SET_MIN_SIZE,
                MemorySegment.NULL, version, 0, width, height,
            )
            Unit
        }
    }

    /** Sets the maximum size via xdg_toplevel.set_max_size(width, height). */
    fun setMaxSize(width: Int, height: Int) {
        val handle = wlProxyMarshalFlagsTwoUint ?: return
        runCatching {
            handle.invokeExact(
                MemorySegment.ofAddress(xdgToplevelPtr), XDG_TOPLEVEL_SET_MAX_SIZE,
                MemorySegment.NULL, version, 0, width, height,
            )
            Unit
        }
    }

    /** Starts compositor-managed interactive window movement. */
    fun move(seatPtr: Long, serial: Int): Boolean {
        val handle = wlProxyMarshalFlagsObjectUint ?: return false
        return runCatching {
            handle.invokeExact(
                MemorySegment.ofAddress(xdgToplevelPtr), XDG_TOPLEVEL_MOVE,
                MemorySegment.NULL, version, 0, MemorySegment.ofAddress(seatPtr), serial,
            )
            true
        }.getOrDefault(false)
    }

    /** Shows the compositor-managed window menu at a surface-local logical position. */
    fun showWindowMenu(seatPtr: Long, serial: Int, x: Int, y: Int): Boolean {
        val handle = wlProxyMarshalFlagsObjectUintTwoInt ?: return false
        return runCatching {
            handle.invokeExact(
                MemorySegment.ofAddress(xdgToplevelPtr), XDG_TOPLEVEL_SHOW_WINDOW_MENU,
                MemorySegment.NULL, version, 0, MemorySegment.ofAddress(seatPtr), serial, x, y,
            )
            true
        }.getOrDefault(false)
    }

    /** Starts compositor-managed interactive window resize. */
    fun resize(seatPtr: Long, serial: Int, edges: Int): Boolean {
        val handle = wlProxyMarshalFlagsObjectTwoUint ?: return false
        return runCatching {
            handle.invokeExact(
                MemorySegment.ofAddress(xdgToplevelPtr), XDG_TOPLEVEL_RESIZE,
                MemorySegment.NULL, version, 0, MemorySegment.ofAddress(seatPtr), serial, edges,
            )
            true
        }.getOrDefault(false)
    }

    /** Requests that the toplevel be maximized. */
    fun setMaximized(maximized: Boolean) {
        val opcode = if (maximized) XDG_TOPLEVEL_SET_MAXIMIZED else XDG_TOPLEVEL_UNSET_MAXIMIZED
        val handle = wlProxyMarshalFlagsVoid ?: return
        runCatching {
            handle.invokeExact(
                MemorySegment.ofAddress(xdgToplevelPtr), opcode,
                MemorySegment.NULL, version, 0,
            )
            Unit
        }
    }

    /**
     * Requests fullscreen / unfullscreen via xdg_toplevel.set_fullscreen / unset_fullscreen.
     *
     * @param fullscreen true = enter fullscreen (borderless), false = exit fullscreen.
     * @param outputPtr target wl_output proxy, or 0 to let the compositor choose.
     */
    fun setFullscreen(fullscreen: Boolean, outputPtr: Long = 0L) {
        if (fullscreen) {
            val handle = wlProxyMarshalFlagsObject ?: return
            runCatching {
                handle.invokeExact(
                    MemorySegment.ofAddress(xdgToplevelPtr), XDG_TOPLEVEL_SET_FULLSCREEN,
                    MemorySegment.NULL, version, 0,
                    outputPtr.takeIf { it != 0L }?.let(MemorySegment::ofAddress) ?: MemorySegment.NULL,
                )
                Unit
            }
        } else {
            val handle = wlProxyMarshalFlagsVoid ?: return
            runCatching {
                handle.invokeExact(
                    MemorySegment.ofAddress(xdgToplevelPtr), XDG_TOPLEVEL_UNSET_FULLSCREEN,
                    MemorySegment.NULL, version, 0,
                )
                Unit
            }
        }
    }

    /** Requests that the toplevel be minimized (set_minimized — compositor may ignore). */
    fun setMinimized() {
        val handle = wlProxyMarshalFlagsVoid ?: return
        runCatching {
            handle.invokeExact(
                MemorySegment.ofAddress(xdgToplevelPtr), XDG_TOPLEVEL_SET_MINIMIZED,
                MemorySegment.NULL, version, 0,
            )
            Unit
        }
    }

    /** Sets the toplevel title via xdg_toplevel.set_title. */
    fun setTitle(title: String) {
        val setTitle = wlProxyMarshalFlagsString ?: return
        runCatching {
            val str = arena.allocateFrom(title)
            setTitle.invokeExact(
                MemorySegment.ofAddress(xdgToplevelPtr), XDG_TOPLEVEL_SET_TITLE,
                MemorySegment.NULL, version, 0, str,
            )
            Unit
        }
    }

    /** Sets the toplevel app ID via xdg_toplevel.set_app_id (opcode 3). */
    fun setAppId(appId: String) {
        val handle = wlProxyMarshalFlagsString ?: return
        runCatching {
            val str = arena.allocateFrom(appId)
            handle.invokeExact(
                MemorySegment.ofAddress(xdgToplevelPtr), XDG_TOPLEVEL_SET_APP_ID,
                MemorySegment.NULL, version, 0, str,
            )
            Unit
        }
    }

    /** Requests server-side or client-side decoration mode when xdg-decoration is available. */
    fun setDecorations(decorated: Boolean): Boolean {
        if (xdgDecorationPtr == 0L) return false
        val setMode = wlProxyMarshalFlagsUint ?: return false
        return runCatching {
            setMode.invokeExact(
                MemorySegment.ofAddress(xdgDecorationPtr),
                XDG_TOPLEVEL_DECORATION_SET_MODE,
                MemorySegment.NULL,
                decorationVersion,
                0,
                waylandDecorationMode(decorated),
            )
            true
        }.getOrDefault(false)
    }

    /** Tears down decoration and xdg_toplevel children, preserving native failures. */
    fun destroyToplevel() {
        if (!toplevelDestroyStarted.compareAndSet(false, true)) return
        val destroy = checkNotNull(wlProxyMarshalFlagsVoid) { "xdg_toplevel destroy unavailable" }
        runWaylandCleanup(
            primary = null,
            cleanupActions = listOf(
                {
                    if (xdgDecorationPtr != 0L) {
                        destroy.invokeExact(
                            MemorySegment.ofAddress(xdgDecorationPtr),
                            XDG_TOPLEVEL_DECORATION_DESTROY,
                            MemorySegment.NULL,
                            decorationVersion,
                            WL_MARSHAL_FLAG_DESTROY,
                        )
                        listenerLifetime.markDecorationDestroyed()
                    }
                },
                {
                    destroy.invokeExact(
                        MemorySegment.ofAddress(xdgToplevelPtr),
                        XDG_TOPLEVEL_DESTROY,
                        MemorySegment.NULL,
                        version,
                        WL_MARSHAL_FLAG_DESTROY,
                    )
                    listenerLifetime.markToplevelDestroyed()
                },
            ),
        )
    }

    /** Tears down xdg_surface after its toplevel; frees upcalls only after success. */
    fun destroySurface() {
        if (!surfaceDestroyStarted.compareAndSet(false, true)) return
        val destroy = checkNotNull(wlProxyMarshalFlagsVoid) { "xdg_surface destroy unavailable" }
        destroy.invokeExact(
            MemorySegment.ofAddress(xdgSurfacePtr),
            XDG_SURFACE_DESTROY,
            MemorySegment.NULL,
            version,
            WL_MARSHAL_FLAG_DESTROY,
        )
        listenerLifetime.markSurfaceDestroyed()
    }

    fun destroy() {
        var primary: Throwable? = null
        try {
            destroyToplevel()
        } catch (failure: Throwable) {
            primary = failure
        }
        try {
            destroySurface()
        } catch (failure: Throwable) {
            if (primary == null) primary = failure else if (failure !== primary) primary.addSuppressed(failure)
        }
        primary?.let { throw it }
    }

    companion object {

        /**
         * Performs the xdg_shell handshake for [surfacePtr] under [wmBasePtr], wiring the
         * configure/close listeners to [onResized]/[onClose]. Returns null if the bindings are
         * unavailable or any step fails.
         */
        fun create(
            displayPtr: Long,
            wmBasePtr: Long,
            surfacePtr: Long,
            onResized: (Int, Int, Boolean) -> Unit,
            onStateConfigured: (WaylandToplevelConfigureStates) -> Unit = {},
            onClose: () -> Unit,
            onFailure: (Throwable) -> Unit,
            nativeListenerLifetime: WaylandNativeListenerLifetime,
            decorationManagerPtr: Long = 0L,
            decorated: Boolean = true,
        ): XdgToplevel? {
            if (wmBasePtr == 0L || surfacePtr == 0L) return null
            val getXdgSurface = wlProxyMarshalFlagsGetXdgSurface ?: return null
            // get_toplevel is a plain new_id request (6-arg marshal) — reuse the create_surface form.
            val getToplevel = wlCompositorCreateSurface ?: return null
            val addListener = wlProxyAddListener ?: return null
            val getVersion = wlProxyGetVersion ?: return null
            val commit = wlProxyMarshalFlagsVoid ?: return null
            val roundtrip = wlDisplayRoundtrip ?: return null

            var xdgSurfaceForCleanup = 0L
            var xdgToplevelForCleanup = 0L
            var decorationForCleanup = 0L
            var xdgVersionForCleanup = 1
            var decorationVersionForCleanup = 1
            var arenaForCleanup: Arena? = null
            var bridgeForCleanup: XdgToplevel? = null
            var operation = "get xdg_surface"
            return try {
                val wmBase = MemorySegment.ofAddress(wmBasePtr)
                val surface = MemorySegment.ofAddress(surfacePtr)
                val display = MemorySegment.ofAddress(displayPtr)
                // Cap the version to 5: the xdg_toplevel listener vtable below has 4 entries
                // (configure, close, configure_bounds[v4], wm_capabilities[v5]), so the compositor
                // must not be allowed to send an event index beyond that range.
                val version = (getVersion.invokeExact(wmBase) as Int).coerceIn(1, 5)
                xdgVersionForCleanup = version

                // xdg_wm_base.get_xdg_surface(surface) → xdg_surface*
                val xdgSurface = getXdgSurface.invokeExact(
                    wmBase, XDG_WM_BASE_GET_XDG_SURFACE, xdg_surface_interface, version, 0,
                    MemorySegment.NULL, surface,
                ) as MemorySegment
                check(xdgSurface.address() != 0L) { "xdg_wm_base.get_xdg_surface returned NULL" }
                xdgSurfaceForCleanup = xdgSurface.address()

                // xdg_surface.get_toplevel() → xdg_toplevel*
                operation = "get xdg_toplevel"
                val xdgToplevel = getToplevel.invokeExact(
                    xdgSurface, XDG_SURFACE_GET_TOPLEVEL, xdg_toplevel_interface, version, 0, MemorySegment.NULL,
                ) as MemorySegment
                check(xdgToplevel.address() != 0L) { "xdg_surface.get_toplevel returned NULL" }
                xdgToplevelForCleanup = xdgToplevel.address()

                // Request server-side decorations (titlebar + close/resize) when the compositor
                // supports zxdg_decoration_manager_v1. Without this, Weston leaves the toplevel
                // undecorated (clients are expected to draw their own).
                var xdgDecorationPtr = 0L
                var decorationVersion = 1
                if (decorationManagerPtr != 0L) {
                    operation = "get xdg decoration"
                    val manager = MemorySegment.ofAddress(decorationManagerPtr)
                    decorationVersion = (getVersion.invokeExact(manager) as Int).coerceAtLeast(1)
                    decorationVersionForCleanup = decorationVersion
                    val decoration = getXdgSurface.invokeExact(
                        manager,
                        XDG_DECORATION_MANAGER_GET_TOPLEVEL_DECORATION,
                        zxdg_toplevel_decoration_v1_interface,
                        decorationVersion,
                        0,
                        MemorySegment.NULL,
                        xdgToplevel,
                    ) as MemorySegment
                    if (decoration.address() != 0L) {
                        xdgDecorationPtr = decoration.address()
                        decorationForCleanup = xdgDecorationPtr
                    }
                }

                val arena = Arena.ofShared()
                val listenerBinding = AutoCloseable { arena.close() }
                val listenerLifetime = try {
                    XdgListenerLifetime.register(
                        binding = listenerBinding,
                        nativeListenerLifetime = nativeListenerLifetime,
                        hasDecoration = xdgDecorationPtr != 0L,
                    )
                } catch (registrationFailure: Throwable) {
                    rollbackXdgAcquisition(
                        primary = registrationFailure,
                        decorationPtr = decorationForCleanup,
                        toplevelPtr = xdgToplevelForCleanup,
                        surfacePtr = xdgSurfaceForCleanup,
                        destroyDecoration = { proxy ->
                            checkNotNull(wlProxyMarshalFlagsVoid) {
                                "xdg decoration rollback unavailable"
                            }.invokeExact(
                                MemorySegment.ofAddress(proxy),
                                XDG_TOPLEVEL_DECORATION_DESTROY,
                                MemorySegment.NULL,
                                decorationVersionForCleanup,
                                WL_MARSHAL_FLAG_DESTROY,
                            )
                        },
                        destroyToplevel = { proxy ->
                            checkNotNull(wlProxyMarshalFlagsVoid) {
                                "xdg_toplevel rollback unavailable"
                            }.invokeExact(
                                MemorySegment.ofAddress(proxy),
                                XDG_TOPLEVEL_DESTROY,
                                MemorySegment.NULL,
                                xdgVersionForCleanup,
                                WL_MARSHAL_FLAG_DESTROY,
                            )
                        },
                        destroySurface = { proxy ->
                            checkNotNull(wlProxyMarshalFlagsVoid) {
                                "xdg_surface rollback unavailable"
                            }.invokeExact(
                                MemorySegment.ofAddress(proxy),
                                XDG_SURFACE_DESTROY,
                                MemorySegment.NULL,
                                xdgVersionForCleanup,
                                WL_MARSHAL_FLAG_DESTROY,
                            )
                        },
                        closeArena = { arena.close() },
                        listenerBinding = listenerBinding,
                        nativeListenerLifetime = nativeListenerLifetime,
                    )
                    decorationForCleanup = 0L
                    xdgToplevelForCleanup = 0L
                    xdgSurfaceForCleanup = 0L
                    arenaForCleanup = null
                    throw registrationFailure
                }
                arenaForCleanup = arena
                val bridge = XdgToplevel(
                    displayPtr,
                    xdgSurface.address(),
                    xdgToplevel.address(),
                    xdgDecorationPtr,
                    version,
                    decorationVersion,
                    XdgUpcallCallbacks(onResized, onStateConfigured, onClose, onFailure),
                    listenerLifetime,
                    arena,
                )
                bridgeForCleanup = bridge
                val lookup = MethodHandles.lookup()
                val ptr = ValueLayout.ADDRESS.byteSize()

                if (xdgDecorationPtr != 0L) {
                    operation = "install xdg decoration listener"
                    val decorationConfigure = upcallStub(
                        lookup.findVirtual(
                            XdgToplevel::class.java, "onDecorationConfigure",
                            MethodType.methodType(
                                Void.TYPE,
                                MemorySegment::class.java,
                                MemorySegment::class.java,
                                Int::class.javaPrimitiveType,
                            ),
                        ).bindTo(bridge),
                        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
                        arena,
                    )
                    val decorationListener = arena.allocate(ptr)
                    decorationListener.set(ValueLayout.ADDRESS, 0L, decorationConfigure)
                    val result = addListener.invokeExact(
                        MemorySegment.ofAddress(xdgDecorationPtr),
                        decorationListener,
                        MemorySegment.NULL,
                    ) as Int
                    check(result == 0) { "xdg decoration listener installation failed: $result" }
                    bridge.setDecorations(decorated)
                }

                // struct xdg_surface_listener { configure } — 1 pointer.
                operation = "install xdg_surface listener"
                val surfaceConfigure = upcallStub(
                    lookup.findVirtual(
                        XdgToplevel::class.java, "onSurfaceConfigure",
                        MethodType.methodType(
                            Void.TYPE, MemorySegment::class.java, MemorySegment::class.java, Int::class.javaPrimitiveType,
                        ),
                    ).bindTo(bridge),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
                    arena,
                )
                val surfaceListener = arena.allocate(ptr * 1)
                surfaceListener.set(ValueLayout.ADDRESS, 0L, surfaceConfigure)
                val surfaceListenerResult = addListener.invokeExact(
                    xdgSurface,
                    surfaceListener,
                    MemorySegment.NULL,
                ) as Int
                check(surfaceListenerResult == 0) {
                    "xdg_surface listener installation failed: $surfaceListenerResult"
                }

                // struct xdg_toplevel_listener { configure, close, configure_bounds, wm_capabilities }.
                operation = "install xdg_toplevel listener"
                val tlConfigure = upcallStub(
                    lookup.findVirtual(
                        XdgToplevel::class.java, "onToplevelConfigure",
                        MethodType.methodType(
                            Void.TYPE, MemorySegment::class.java, MemorySegment::class.java,
                            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, MemorySegment::class.java,
                        ),
                    ).bindTo(bridge),
                    FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                    ),
                    arena,
                )
                val tlClose = upcallStub(
                    lookup.findVirtual(
                        XdgToplevel::class.java, "onToplevelClose",
                        MethodType.methodType(Void.TYPE, MemorySegment::class.java, MemorySegment::class.java),
                    ).bindTo(bridge),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS),
                    arena,
                )
                val tlBounds = upcallStub(
                    lookup.findVirtual(
                        XdgToplevel::class.java, "onToplevelConfigureBounds",
                        MethodType.methodType(
                            Void.TYPE, MemorySegment::class.java, MemorySegment::class.java,
                            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                        ),
                    ).bindTo(bridge),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
                    arena,
                )
                val tlCaps = upcallStub(
                    lookup.findVirtual(
                        XdgToplevel::class.java, "onToplevelWmCapabilities",
                        MethodType.methodType(Void.TYPE, MemorySegment::class.java, MemorySegment::class.java, MemorySegment::class.java),
                    ).bindTo(bridge),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
                    arena,
                )
                val tlListener = arena.allocate(ptr * 4)
                tlListener.set(ValueLayout.ADDRESS, 0L, tlConfigure)
                tlListener.set(ValueLayout.ADDRESS, ptr, tlClose)
                tlListener.set(ValueLayout.ADDRESS, ptr * 2, tlBounds)
                tlListener.set(ValueLayout.ADDRESS, ptr * 3, tlCaps)
                val toplevelListenerResult = addListener.invokeExact(
                    xdgToplevel,
                    tlListener,
                    MemorySegment.NULL,
                ) as Int
                check(toplevelListenerResult == 0) {
                    "xdg_toplevel listener installation failed: $toplevelListenerResult"
                }

                // Initial commit (no buffer yet) + roundtrip → triggers the first configure, which
                // the surface listener acks. Only then is it legal to attach a buffer.
                operation = "initial xdg commit"
                commit.invokeExact(surface, WL_SURFACE_COMMIT, MemorySegment.NULL, version, 0)
                operation = "initial xdg roundtrip"
                val roundtripResult = roundtrip.invokeExact(display) as Int
                check(roundtripResult >= 0) { "wl_display_roundtrip failed: $roundtripResult" }

                bridge
            } catch (failure: Throwable) {
                val bridge = bridgeForCleanup
                if (bridge != null) {
                    runWaylandCleanup(failure, listOf(bridge::destroy))
                } else {
                    val destroy = wlProxyMarshalFlagsVoid
                    rollbackXdgAcquisition(
                        primary = failure,
                        decorationPtr = decorationForCleanup,
                        toplevelPtr = xdgToplevelForCleanup,
                        surfacePtr = xdgSurfaceForCleanup,
                        destroyDecoration = { proxy ->
                            checkNotNull(destroy) { "xdg decoration rollback unavailable" }
                                .invokeExact(
                                    MemorySegment.ofAddress(proxy),
                                    XDG_TOPLEVEL_DECORATION_DESTROY,
                                    MemorySegment.NULL,
                                    decorationVersionForCleanup,
                                    WL_MARSHAL_FLAG_DESTROY,
                                )
                        },
                        destroyToplevel = { proxy ->
                            checkNotNull(destroy) { "xdg_toplevel rollback unavailable" }
                                .invokeExact(
                                    MemorySegment.ofAddress(proxy),
                                    XDG_TOPLEVEL_DESTROY,
                                    MemorySegment.NULL,
                                    xdgVersionForCleanup,
                                    WL_MARSHAL_FLAG_DESTROY,
                                )
                        },
                        destroySurface = { proxy ->
                            checkNotNull(destroy) { "xdg_surface rollback unavailable" }
                                .invokeExact(
                                    MemorySegment.ofAddress(proxy),
                                    XDG_SURFACE_DESTROY,
                                    MemorySegment.NULL,
                                    xdgVersionForCleanup,
                                    WL_MARSHAL_FLAG_DESTROY,
                                )
                        },
                        closeArena = { arenaForCleanup?.close() },
                        listenerBinding = arenaForCleanup?.let { retainedArena ->
                            AutoCloseable { retainedArena.close() }
                        },
                        nativeListenerLifetime = nativeListenerLifetime,
                    )
                }
                throw IllegalStateException(
                    "Wayland xdg-shell setup failed during $operation",
                    failure,
                )
            }
        }
    }
}

internal data class WaylandToplevelConfigureStates(
    val maximized: Boolean = false,
    val fullscreen: Boolean = false,
    val resizing: Boolean = false,
    val tiled: Boolean = false,
) {
    fun isStateless(): Boolean =
        !maximized && !fullscreen && !tiled
}

internal fun waylandShouldApplyResizeIncrements(
    isResizing: Boolean,
    isMaximized: Boolean,
    isFullscreen: Boolean,
    isTiled: Boolean,
    constrain: Boolean = false,
): Boolean =
    (constrain || isResizing) && !isMaximized && !isFullscreen && !isTiled

internal fun waylandDecorationMode(decorated: Boolean): Int =
    if (decorated) {
        XDG_TOPLEVEL_DECORATION_MODE_SERVER_SIDE
    } else {
        XDG_TOPLEVEL_DECORATION_MODE_CLIENT_SIDE
    }

private const val WAYLAND_WL_ARRAY_SIZE_OFFSET: Long = 0L
private const val WAYLAND_WL_ARRAY_DATA_OFFSET: Long = 16L
private const val XDG_TOPLEVEL_STATE_MAXIMIZED_VALUE: Int = 1
private const val XDG_TOPLEVEL_STATE_FULLSCREEN_VALUE: Int = 2
private const val XDG_TOPLEVEL_STATE_RESIZING_VALUE: Int = 3
private const val XDG_TOPLEVEL_STATE_TILED_LEFT_VALUE: Int = 5
private const val XDG_TOPLEVEL_STATE_TILED_RIGHT_VALUE: Int = 6
private const val XDG_TOPLEVEL_STATE_TILED_TOP_VALUE: Int = 7
private const val XDG_TOPLEVEL_STATE_TILED_BOTTOM_VALUE: Int = 8

internal fun waylandToplevelConfigureStates(states: MemorySegment): WaylandToplevelConfigureStates {
    if (states == MemorySegment.NULL) return WaylandToplevelConfigureStates()
    return try {
        val size = states.get(ValueLayout.JAVA_LONG, WAYLAND_WL_ARRAY_SIZE_OFFSET)
        if (size <= 0L) return WaylandToplevelConfigureStates()
        val data = states.get(ValueLayout.ADDRESS, WAYLAND_WL_ARRAY_DATA_OFFSET)
        if (data == MemorySegment.NULL) return WaylandToplevelConfigureStates()
        val items = data.reinterpret(size)
        var maximized = false
        var fullscreen = false
        var resizing = false
        var tiled = false
        val count = (size / 4L).toInt()
        for (index in 0 until count) {
            when (items.getAtIndex(ValueLayout.JAVA_INT, index.toLong())) {
                XDG_TOPLEVEL_STATE_MAXIMIZED_VALUE -> maximized = true
                XDG_TOPLEVEL_STATE_FULLSCREEN_VALUE -> fullscreen = true
                XDG_TOPLEVEL_STATE_RESIZING_VALUE -> resizing = true
                XDG_TOPLEVEL_STATE_TILED_LEFT_VALUE,
                XDG_TOPLEVEL_STATE_TILED_RIGHT_VALUE,
                XDG_TOPLEVEL_STATE_TILED_TOP_VALUE,
                XDG_TOPLEVEL_STATE_TILED_BOTTOM_VALUE -> tiled = true
            }
        }
        WaylandToplevelConfigureStates(maximized, fullscreen, resizing, tiled)
    } catch (_: Throwable) {
        WaylandToplevelConfigureStates()
    }
}
