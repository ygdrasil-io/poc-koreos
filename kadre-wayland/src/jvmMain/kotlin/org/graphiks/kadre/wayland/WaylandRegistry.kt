/**
 * Wayland globals discovery.
 *
 * `WaylandEventLoop` left `compositorPtr = 0` (stub #66): no `wl_surface` could
 * therefore be created. This file implements the missing negotiation via FFM:
 *
 *   wl_display.get_registry → wl_registry.add_listener(global) → wl_display.roundtrip
 *   → wl_registry.bind(wl_compositor) + wl_registry.bind(xdg_wm_base)
 *
 * `wl_display_get_registry` and `wl_registry_bind` are `static inline` functions in the
 * header (not exported): we perform them via `wl_proxy_marshal_flags`. The
 * `wl_registry_interface` / `wl_compositor_interface` structures, however, are exported;
 * `xdg_wm_base_interface` comes from the kextract-generated bindings ([generated]).
 */
package org.graphiks.kadre.wayland
import org.graphiks.kadre.ffi.wayland.*

import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.ffi.wayland.generated.xdg_wm_base_interface
import org.graphiks.kadre.ffi.wayland.generated.zxdg_decoration_manager_v1_interface
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** Opcode wl_display.get_registry. */
private const val WL_DISPLAY_GET_REGISTRY: Int = 1
/** Opcode wl_registry.bind. */
private const val WL_REGISTRY_BIND: Int = 0

/** Bound Wayland globals. Addresses are 0 when the global is unavailable. */
internal data class WaylandGlobals(
    val compositorPtr: Long,
    val xdgWmBasePtr: Long,
    val decorationManagerPtr: Long = 0L,
    val seatPtr: Long = 0L,
    val seatVersion: Int = 0,
    val registryOwner: WaylandRegistryOwner? = null,
    val textInputManagerPtr: Long = 0L,
    val shmPtr: Long = 0L,
    val shmVersion: Int = 0,
    val pointerConstraintsPtr: Long = 0L,
    val iconManagerPtr: Long = 0L,
    val activationManagerPtr: Long = 0L,
    val extBackgroundEffectManagerPtr: Long = 0L,
    val kwinBlurManagerPtr: Long = 0L,
    val dataDeviceManagerPtr: Long = 0L,
) {
    /** All protocol interface names announced by the compositor during wl_registry.global events. */
    val availableProtocols: Set<String>
        get() = registryOwner?.collector?.allProtocolNames?.toSet() ?: _availableProtocols
    internal var _availableProtocols: Set<String> = emptySet()

    /** Returns true if the compositor announced the given protocol interface name. */
    fun hasProtocol(interfaceName: String): Boolean = interfaceName in availableProtocols
}

/**
 * `wl_registry.global` event collector: retains the `name` and `version` of the globals
 * Kadre needs (`wl_compositor`, `xdg_wm_base`, `wl_seat`, `wl_output`) as they are announced,
 * and records ALL announced protocol interface names for dynamic detection.
 */
internal class GlobalsCollector {
    var compositorName: Int = -1
    var compositorVersion: Int = 0
    var xdgWmBaseName: Int = -1
    var xdgWmBaseVersion: Int = 0
    var decorationManagerName: Int = -1
    var decorationManagerVersion: Int = 0
    var seatName: Int = -1
    var seatVersion: Int = 0
    var textInputManagerName: Int = -1
    var textInputManagerVersion: Int = 0
    var shmName: Int = -1
    var shmVersion: Int = 0
    var pointerConstraintsName: Int = -1
    var pointerConstraintsVersion: Int = 0
    var iconManagerName: Int = -1
    var iconManagerVersion: Int = 0
    var activationManagerName: Int = -1
    var activationManagerVersion: Int = 0
    var extBackgroundEffectManagerName: Int = -1
    var extBackgroundEffectManagerVersion: Int = 0
    var kwinBlurManagerName: Int = -1
    var kwinBlurManagerVersion: Int = 0
    var dataDeviceManagerName: Int = -1
    var dataDeviceManagerVersion: Int = 0

    /** All live protocol interface names announced by the compositor. */
    val allProtocolNames = mutableSetOf<String>()
    private val protocolByRegistryName = mutableMapOf<Int, String>()

    fun hasProtocol(interfaceName: String): Boolean = interfaceName in allProtocolNames

    fun recordGlobal(name: Int, ifaceName: String, version: Int) {
        protocolByRegistryName[name] = ifaceName
        allProtocolNames.add(ifaceName)

        when (ifaceName) {
            "wl_compositor" -> if (compositorName < 0) { compositorName = name; compositorVersion = version }
            "xdg_wm_base" -> if (xdgWmBaseName < 0) { xdgWmBaseName = name; xdgWmBaseVersion = version }
            "zxdg_decoration_manager_v1" ->
                if (decorationManagerName < 0) { decorationManagerName = name; decorationManagerVersion = version }
            "wl_seat" -> if (seatName < 0) { seatName = name; seatVersion = version }
            "zwp_text_input_manager_v3" ->
                if (textInputManagerName < 0) { textInputManagerName = name; textInputManagerVersion = version }
            "wl_shm" -> if (shmName < 0) { shmName = name; shmVersion = version }
            "zwp_pointer_constraints_v1" ->
                if (pointerConstraintsName < 0) { pointerConstraintsName = name; pointerConstraintsVersion = version }
            "xdg_toplevel_icon_manager_v1" ->
                if (iconManagerName < 0) { iconManagerName = name; iconManagerVersion = version }
            "xdg_activation_v1" ->
                if (activationManagerName < 0) { activationManagerName = name; activationManagerVersion = version }
            "ext_background_effect_v1" ->
                if (extBackgroundEffectManagerName < 0) { extBackgroundEffectManagerName = name; extBackgroundEffectManagerVersion = version }
            "org_kde_kwin_blur_manager" ->
                if (kwinBlurManagerName < 0) { kwinBlurManagerName = name; kwinBlurManagerVersion = version }
            "wl_data_device_manager" ->
                if (dataDeviceManagerName < 0) { dataDeviceManagerName = name; dataDeviceManagerVersion = version }
        }
    }

    fun recordGlobalRemove(name: Int) {
        val removedProtocol = protocolByRegistryName.remove(name) ?: return
        if (removedProtocol !in protocolByRegistryName.values) {
            allProtocolNames.remove(removedProtocol)
        }
    }

    /** C callback: void global(data, wl_registry*, uint32 name, const char* interface, uint32 version). */
    @Suppress("UNUSED_PARAMETER")
    fun onGlobal(data: MemorySegment, registry: MemorySegment, name: Int, iface: MemorySegment, version: Int) {
        val ifaceName = try {
            iface.reinterpret(128).getString(0)
        } catch (_: Throwable) {
            return
        }
        recordGlobal(name, ifaceName, version)
    }

    /** C callback: void global_remove(data, wl_registry*, uint32 name). */
    @Suppress("UNUSED_PARAMETER")
    fun onGlobalRemove(data: MemorySegment, registry: MemorySegment, name: Int) {
        recordGlobalRemove(name)
    }
}

internal data class BoundOutput(
    val registryName: Int,
    val proxy: Long,
    val version: Int,
    val info: WaylandOutputInfo,
)

/**
 * Owns the live wl_registry listener and every wl_output bound through it.
 * Output globals remain dynamic for the whole display connection lifetime.
 */
internal class WaylandRegistryOwner internal constructor(
    private val registryPtr: Long,
    @Suppress("unused") private val listenerArena: Arena,
    internal val collector: GlobalsCollector,
    private val bindOutput: (registryName: Int, advertisedVersion: Int) -> Pair<Long, Int>?,
    private val installOutputListener: (BoundOutput) -> Boolean,
    private val destroyProxy: (Long) -> Unit,
    private val closeListenerArena: () -> Unit = listenerArena::close,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val outputsByRegistryName = linkedMapOf<Int, BoundOutput>()
    private val removalListeners = CopyOnWriteArrayList<(Long) -> Unit>()
    private val scaleListeners = CopyOnWriteArrayList<(WaylandOutputInfo, Int) -> Unit>()
    private val ownedChildren = mutableListOf<AutoCloseable>()
    private val nativeFailureLock = Any()
    private val pendingNativeFailures = ArrayDeque<Throwable>()
    @Volatile private var nativeFailureSink: ((Throwable) -> Unit)? = null

    @Volatile
    var onOutputChanged: ((WaylandOutputInfo) -> Unit)? = null

    @Volatile
    var onOutputScaleChanged: ((WaylandOutputInfo, Int) -> Unit)? = null

    val outputs: List<BoundOutput>
        get() = synchronized(outputsByRegistryName) { outputsByRegistryName.values.toList() }

    fun outputForProxy(proxy: Long): BoundOutput? =
        synchronized(outputsByRegistryName) { outputsByRegistryName.values.firstOrNull { it.proxy == proxy } }

    fun addOutputRemovalListener(listener: (Long) -> Unit): AutoCloseable {
        removalListeners += listener
        return AutoCloseable { removalListeners -= listener }
    }

    fun addOutputScaleListener(listener: (WaylandOutputInfo, Int) -> Unit): AutoCloseable {
        scaleListeners += listener
        return AutoCloseable { scaleListeners -= listener }
    }

    fun ownChild(child: AutoCloseable) {
        check(!closed.get()) { "Wayland registry owner is already closed" }
        synchronized(ownedChildren) { ownedChildren += child }
    }

    fun routeNativeFailuresTo(sink: (Throwable) -> Unit) {
        val pending = synchronized(nativeFailureLock) {
            nativeFailureSink = sink
            buildList {
                while (pendingNativeFailures.isNotEmpty()) add(pendingNativeFailures.removeFirst())
            }
        }
        pending.forEach(sink)
    }

    fun throwPendingNativeFailure() {
        val pending = synchronized(nativeFailureLock) {
            buildList {
                while (pendingNativeFailures.isNotEmpty()) add(pendingNativeFailures.removeFirst())
            }
        }
        val primary = pending.firstOrNull() ?: return
        pending.drop(1).forEach(primary::addSuppressed)
        throw primary
    }

    internal fun reportNativeFailure(failure: Throwable) {
        val sink = synchronized(nativeFailureLock) {
            nativeFailureSink ?: run {
                pendingNativeFailures.addLast(failure)
                null
            }
        }
        if (sink != null) {
            try {
                sink(failure)
            } catch (sinkFailure: Throwable) {
                synchronized(nativeFailureLock) {
                    pendingNativeFailures.addLast(failure)
                    if (sinkFailure !== failure) pendingNativeFailures.addLast(sinkFailure)
                }
            }
        }
    }

    fun notifyOutputChanged(info: WaylandOutputInfo) {
        try {
            onOutputChanged?.invoke(info)
        } catch (failure: Throwable) {
            reportNativeFailure(failure)
        }
    }

    fun notifyOutputScaleChanged(info: WaylandOutputInfo, scale: Int) {
        try {
            onOutputScaleChanged?.invoke(info, scale)
        } catch (failure: Throwable) {
            reportNativeFailure(failure)
        }
        scaleListeners.forEach { listener ->
            try {
                listener(info, scale)
            } catch (failure: Throwable) {
                reportNativeFailure(failure)
            }
        }
    }

    fun onGlobal(name: Int, interfaceName: String, version: Int) {
        if (closed.get()) return
        collector.recordGlobal(name, interfaceName, version)
        if (interfaceName != "wl_output") return
        if (synchronized(outputsByRegistryName) { name in outputsByRegistryName }) return

        val binding = try {
            bindOutput(name, version)
        } catch (failure: Throwable) {
            reportNativeFailure(failure)
            return
        }
        if (binding == null || binding.first == 0L) {
            reportNativeFailure(
                IllegalStateException(
                    "failed to bind wl_output registry global $name at advertised version $version",
                ),
            )
            return
        }
        val (proxy, boundVersion) = binding
        val output = BoundOutput(
            registryName = name,
            proxy = proxy,
            version = boundVersion,
            info = WaylandOutputInfo(
                outputPtr = proxy,
                name = null,
                outputVersion = boundVersion,
            ),
        )
        val inserted = synchronized(outputsByRegistryName) {
            if (name in outputsByRegistryName) false
            else true.also { outputsByRegistryName[name] = output }
        }
        if (!inserted) {
            try {
                destroyProxy(proxy)
            } catch (failure: Throwable) {
                reportNativeFailure(failure)
            }
            return
        }
        val installationFailure = try {
            if (installOutputListener(output)) null else IllegalStateException(
                "failed to install wl_output listener for registry global ${output.registryName}",
            )
        } catch (failure: Throwable) {
            failure
        }
        if (installationFailure != null) {
            synchronized(outputsByRegistryName) {
                if (outputsByRegistryName[name] === output) outputsByRegistryName.remove(name)
            }
            try {
                destroyProxy(output.proxy)
            } catch (destroyFailure: Throwable) {
                if (destroyFailure !== installationFailure) installationFailure.addSuppressed(destroyFailure)
            }
            reportNativeFailure(installationFailure)
        }
    }

    fun onGlobalRemove(name: Int) {
        if (closed.get()) return
        collector.recordGlobalRemove(name)
        val removed = synchronized(outputsByRegistryName) { outputsByRegistryName.remove(name) } ?: return
        removalListeners.forEach { listener ->
            try {
                listener(removed.proxy)
            } catch (failure: Throwable) {
                reportNativeFailure(failure)
            }
        }
        try {
            destroyProxy(removed.proxy)
        } catch (failure: Throwable) {
            reportNativeFailure(failure)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onNativeGlobal(
        data: MemorySegment,
        registry: MemorySegment,
        name: Int,
        iface: MemorySegment,
        version: Int,
    ) {
        try {
            val interfaceName = iface.reinterpret(128).getString(0)
            onGlobal(name, interfaceName, version)
        } catch (failure: Throwable) {
            reportNativeFailure(IllegalStateException("wl_registry.global callback failed", failure))
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onNativeGlobalRemove(data: MemorySegment, registry: MemorySegment, name: Int) {
        try {
            onGlobalRemove(name)
        } catch (failure: Throwable) {
            reportNativeFailure(failure)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val children = synchronized(outputsByRegistryName) {
            outputsByRegistryName.values.map(BoundOutput::proxy).also { outputsByRegistryName.clear() }
        }
        val otherChildren = synchronized(ownedChildren) {
            ownedChildren.toList().also { ownedChildren.clear() }
        }
        runWaylandCleanup(
            primary = null,
            cleanupActions = buildList {
                children.forEach { proxy -> add { destroyProxy(proxy) } }
                otherChildren.forEach { child -> add(child::close) }
                add { if (registryPtr != 0L) destroyProxy(registryPtr) }
                add(closeListenerArena)
            },
        )
        removalListeners.clear()
        scaleListeners.clear()
    }
}

/**
 * Answers xdg_wm_base.ping with pong so the compositor does not consider the client unresponsive.
 * Held for the whole connection lifetime (a strong reference keeps its upcall arena alive).
 */
private class XdgWmBasePinger(private val wmBasePtr: Long, private val displayPtr: Long, private val version: Int) {
    /** C callback: void ping(data, xdg_wm_base*, uint32 serial). */
    @Suppress("UNUSED_PARAMETER")
    fun onPing(data: MemorySegment, wmBase: MemorySegment, serial: Int) {
        val pong = wlProxyMarshalFlagsUint ?: return
        runCatching {
            // invokeExact in statement position (void handle) — see onSurfaceConfigure.
            pong.invokeExact(
                MemorySegment.ofAddress(wmBasePtr), XDG_WM_BASE_PONG, MemorySegment.NULL, version, 0, serial,
            )
            wlDisplayFlush?.let { it.invokeExact(MemorySegment.ofAddress(displayPtr)) as Int }
        }
    }
}

private class XdgWmBaseBinding(
    val proxy: Long,
    @Suppress("unused") private val pinger: XdgWmBasePinger,
    private val listenerArena: Arena,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runWaylandCleanup(
            primary = null,
            cleanupActions = listOf(
                {
                    if (proxy != 0L) {
                        wlProxyDestroy?.invokeExact(MemorySegment.ofAddress(proxy))
                    }
                },
                listenerArena::close,
            ),
        )
    }
}

/**
 * Discovers and binds the `wl_compositor` and `xdg_wm_base` globals, and installs the
 * xdg_wm_base ping→pong listener.
 *
 * @param displayPtr Address of the connected `wl_display*`.
 * @return Bound global addresses (0 where unavailable).
 */
internal fun discoverGlobals(
    displayPtr: Long,
    protocolExtensions: Set<String> = setOf(
        "zwp_pointer_constraints_v1",
        "xdg_toplevel_icon_manager_v1",
        "ext_background_effect_v1",
        "org_kde_kwin_blur_manager",
        "xdg_activation_v1",
    ),
): WaylandGlobals {
    val marshalNewId = wlProxyMarshalNewId ?: return WaylandGlobals(0L, 0L)
    val addListener = wlProxyAddListener ?: return WaylandGlobals(0L, 0L)
    val roundtrip = wlDisplayRoundtrip ?: return WaylandGlobals(0L, 0L)
    val bind = wlProxyMarshalBind ?: return WaylandGlobals(0L, 0L)
    val registryIface = wlRegistryInterface ?: return WaylandGlobals(0L, 0L)
    val compositorIface = wlCompositorInterface ?: return WaylandGlobals(0L, 0L)
    val getVersion = wlProxyGetVersion ?: return WaylandGlobals(0L, 0L)

    val display = MemorySegment.ofAddress(displayPtr)
    var registryOwnerForCleanup: WaylandRegistryOwner? = null

    return try {
        // 1. wl_display.get_registry → wl_registry*
        val displayVersion = getVersion.invokeExact(display) as Int
        val registry = marshalNewId.invokeExact(
            display, WL_DISPLAY_GET_REGISTRY, registryIface, displayVersion, 0, MemorySegment.NULL,
        ) as MemorySegment
        if (registry.address() == 0L) return WaylandGlobals(0L, 0L)

        // 2. Registry listener (global/global_remove upcall) in a durable arena.
        val arena = Arena.ofShared()
        val collector = GlobalsCollector()
        val lookup = MethodHandles.lookup()
        val outputIface = wlOutputInterface
        lateinit var registryOwner: WaylandRegistryOwner
        registryOwner = WaylandRegistryOwner(
            registryPtr = registry.address(),
            listenerArena = arena,
            collector = collector,
            bindOutput = bindOutput@{ name, advertisedVersion ->
                val iface = outputIface ?: return@bindOutput null
                val boundVersion = advertisedVersion.coerceAtMost(4)
                val namePtr = iface.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0L)
                val proxy = (bind.invokeExact(
                    registry, WL_REGISTRY_BIND, iface, boundVersion, 0,
                    name, namePtr, boundVersion, MemorySegment.NULL,
                ) as MemorySegment).address()
                proxy to boundVersion
            },
            installOutputListener = { output ->
                installWaylandOutputListener(
                    output = MemorySegment.ofAddress(output.proxy),
                    addListener = addListener,
                    lookup = lookup,
                    arena = arena,
                    outputInfo = output.info,
                    onOutputChanged = registryOwner::notifyOutputChanged,
                    onScaleChanged = registryOwner::notifyOutputScaleChanged,
                )
            },
            destroyProxy = { proxy ->
                wlProxyDestroy?.invokeExact(MemorySegment.ofAddress(proxy))
            },
        )
        registryOwnerForCleanup = registryOwner

        val onGlobalHandle = lookup.findVirtual(
            WaylandRegistryOwner::class.java, "onNativeGlobal",
            MethodType.methodType(
                Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType, MemorySegment::class.java, Int::class.javaPrimitiveType,
            ),
        ).bindTo(registryOwner)
        val onGlobalRemoveHandle = lookup.findVirtual(
            WaylandRegistryOwner::class.java, "onNativeGlobalRemove",
            MethodType.methodType(
                Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java, Int::class.javaPrimitiveType,
            ),
        ).bindTo(registryOwner)

        val globalStub = upcallStub(
            onGlobalHandle,
            FunctionDescriptor.ofVoid(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ),
            arena,
        )
        val globalRemoveStub = upcallStub(
            onGlobalRemoveHandle,
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
            arena,
        )

        // struct wl_registry_listener { global; global_remove; } — 2 pointers.
        val listener = arena.allocate(ValueLayout.ADDRESS.byteSize() * 2)
        listener.set(ValueLayout.ADDRESS, 0L, globalStub)
        listener.set(ValueLayout.ADDRESS, ValueLayout.ADDRESS.byteSize(), globalRemoveStub)

        val rc = addListener.invokeExact(registry, listener, MemorySegment.NULL) as Int
        if (rc != 0) {
            registryOwner.close()
            return WaylandGlobals(0L, 0L)
        }

        // 3. roundtrip → triggers the global events (fills the collector).
        roundtrip.invokeExact(display) as Int
        if (collector.compositorName < 0) {
            registryOwner.close()
            return WaylandGlobals(0L, 0L)
        }

        // 4. wl_registry.bind(wl_compositor). interface->name = 1st field (const char*).
        val compositorNamePtr = compositorIface.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0L)
        val compositor = bind.invokeExact(
            registry, WL_REGISTRY_BIND, compositorIface, collector.compositorVersion, 0,
            collector.compositorName, compositorNamePtr, collector.compositorVersion, MemorySegment.NULL,
        ) as MemorySegment

        // 5. wl_registry.bind(xdg_wm_base) if present, then install the ping→pong listener.
        var xdgWmBasePtr = 0L
        if (collector.xdgWmBaseName >= 0) {
            bindXdgWmBase(registry, bind, collector, addListener, lookup, displayPtr)?.let { binding ->
                xdgWmBasePtr = binding.proxy
                registryOwner.ownChild(binding)
            }
        }

        // 6. wl_registry.bind(zxdg_decoration_manager_v1) for server-side window decorations.
        var decorationManagerPtr = 0L
        if (collector.decorationManagerName >= 0) {
            decorationManagerPtr = runCatching {
                val iface = zxdg_decoration_manager_v1_interface
                val namePtr = iface.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0L)
                (bind.invokeExact(
                    registry, WL_REGISTRY_BIND, iface, collector.decorationManagerVersion, 0,
                    collector.decorationManagerName, namePtr, collector.decorationManagerVersion, MemorySegment.NULL,
                ) as MemorySegment).address()
            }.getOrDefault(0L)
        }

        // 7. wl_registry.bind(wl_seat) for keyboard/pointer/touch input.
        var seatPtr = 0L
        var seatVersion = 0
        if (collector.seatName >= 0) {
            val iface = wlSeatInterface
            if (iface != null) {
                val namePtr = iface.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0L)
                val boundVersion = collector.seatVersion.coerceAtMost(7) // cap at v7
                seatPtr = runCatching {
                    (bind.invokeExact(
                        registry, WL_REGISTRY_BIND, iface, boundVersion, 0,
                        collector.seatName, namePtr, boundVersion, MemorySegment.NULL,
                    ) as MemorySegment).address()
                }.getOrDefault(0L)
                seatVersion = boundVersion
            }
        }

        // 9. wl_registry.bind(zwp_text_input_manager_v3) for IME.
        var textInputManagerPtr = 0L
        if (collector.textInputManagerName >= 0) {
            val iface = zwpTextInputManagerV3Interface
            val namePtr = iface.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0L)
            val boundVersion = collector.textInputManagerVersion.coerceAtMost(1)
            textInputManagerPtr = runCatching {
                (bind.invokeExact(
                    registry, WL_REGISTRY_BIND, iface, boundVersion, 0,
                    collector.textInputManagerName, namePtr, boundVersion, MemorySegment.NULL,
                ) as MemorySegment).address()
            }.getOrDefault(0L)
        }

        // 10. wl_registry.bind(wl_shm) for cursor buffer creation.
        var shmPtr = 0L
        var shmVersion = 0
        if (collector.shmName >= 0) {
            val iface = wlShmInterface
            if (iface != null) {
                val namePtr = iface.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0L)
                val boundVersion = collector.shmVersion.coerceAtMost(1)
                shmPtr = runCatching {
                    (bind.invokeExact(
                        registry, WL_REGISTRY_BIND, iface, boundVersion, 0,
                        collector.shmName, namePtr, boundVersion, MemorySegment.NULL,
                    ) as MemorySegment).address()
                }.getOrDefault(0L)
                shmVersion = boundVersion
            }
        }

        // 11. wl_registry.bind(zwp_pointer_constraints_v1) for pointer confinement/locking.
        var pointerConstraintsPtr = 0L
        if (collector.pointerConstraintsName >= 0 && "zwp_pointer_constraints_v1" in protocolExtensions) {
            val iface = zwpPointerConstraintsV1Interface
            val namePtr = iface.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0L)
            pointerConstraintsPtr = runCatching {
                (bind.invokeExact(
                    registry, WL_REGISTRY_BIND, iface, collector.pointerConstraintsVersion, 0,
                    collector.pointerConstraintsName, namePtr, collector.pointerConstraintsVersion, MemorySegment.NULL,
                ) as MemorySegment).address()
            }.getOrDefault(0L)
        }

        // 12. wl_registry.bind(xdg_toplevel_icon_manager_v1) for window icons.
        var iconManagerPtr = 0L
        if (collector.iconManagerName >= 0 && "xdg_toplevel_icon_manager_v1" in protocolExtensions) {
            val iface = xdgToplevelIconManagerV1Interface
            val namePtr = iface.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0L)
            iconManagerPtr = runCatching {
                (bind.invokeExact(
                    registry, WL_REGISTRY_BIND, iface, collector.iconManagerVersion, 0,
                    collector.iconManagerName, namePtr, collector.iconManagerVersion, MemorySegment.NULL,
                ) as MemorySegment).address()
            }.getOrDefault(0L)
        }

        // 13. wl_registry.bind(xdg_activation_v1) for activation tokens.
        var activationManagerPtr = 0L
        if (collector.activationManagerName >= 0 && "xdg_activation_v1" in protocolExtensions) {
            val iface = xdgActivationV1Interface
            val namePtr = iface.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0L)
            activationManagerPtr = runCatching {
                (bind.invokeExact(
                    registry, WL_REGISTRY_BIND, iface, collector.activationManagerVersion, 0,
                    collector.activationManagerName, namePtr, collector.activationManagerVersion, MemorySegment.NULL,
                ) as MemorySegment).address()
            }.getOrDefault(0L)
        }

        // 15. wl_registry.bind(ext_background_effect_v1) for Wayland blur (wlroots, KWin 6+).
        var extBackgroundEffectManagerPtr = 0L
        if (collector.extBackgroundEffectManagerName >= 0 && "ext_background_effect_v1" in protocolExtensions) {
            val iface = extBackgroundEffectV1Interface
            val namePtr = iface.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0L)
            extBackgroundEffectManagerPtr = runCatching {
                (bind.invokeExact(
                    registry, WL_REGISTRY_BIND, iface, collector.extBackgroundEffectManagerVersion, 0,
                    collector.extBackgroundEffectManagerName, namePtr, collector.extBackgroundEffectManagerVersion, MemorySegment.NULL,
                ) as MemorySegment).address()
            }.getOrDefault(0L)
        }

        // 16. wl_registry.bind(org_kde_kwin_blur_manager) for Wayland blur (KWin 5.x).
        var kwinBlurManagerPtr = 0L
        if (collector.kwinBlurManagerName >= 0 && "org_kde_kwin_blur_manager" in protocolExtensions) {
            val iface = orgKdeKwinBlurManagerInterface
            val namePtr = iface.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0L)
            kwinBlurManagerPtr = runCatching {
                (bind.invokeExact(
                    registry, WL_REGISTRY_BIND, iface, collector.kwinBlurManagerVersion, 0,
                    collector.kwinBlurManagerName, namePtr, collector.kwinBlurManagerVersion, MemorySegment.NULL,
                ) as MemorySegment).address()
            }.getOrDefault(0L)
        }

        // 17. wl_registry.bind(wl_data_device_manager) for Drag & Drop.
        var dataDeviceManagerPtr = 0L
        if (collector.dataDeviceManagerName >= 0) {
            val iface = wlDataDeviceManagerInterface
            if (iface != null) {
                val namePtr = iface.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0L)
                dataDeviceManagerPtr = runCatching {
                    (bind.invokeExact(
                        registry, WL_REGISTRY_BIND, iface, collector.dataDeviceManagerVersion, 0,
                        collector.dataDeviceManagerName, namePtr, collector.dataDeviceManagerVersion, MemorySegment.NULL,
                    ) as MemorySegment).address()
                }.getOrDefault(0L)
            }
        }
        WaylandGlobals(
            compositorPtr           = compositor.address(),
            xdgWmBasePtr            = xdgWmBasePtr,
            decorationManagerPtr    = decorationManagerPtr,
            seatPtr                 = seatPtr,
            seatVersion             = seatVersion,
            registryOwner           = registryOwner,
            textInputManagerPtr     = textInputManagerPtr,
            shmPtr                  = shmPtr,
            shmVersion              = shmVersion,
            pointerConstraintsPtr   = pointerConstraintsPtr,
            iconManagerPtr          = iconManagerPtr,
            activationManagerPtr    = activationManagerPtr,
            extBackgroundEffectManagerPtr = extBackgroundEffectManagerPtr,
            kwinBlurManagerPtr      = kwinBlurManagerPtr,
            dataDeviceManagerPtr    = dataDeviceManagerPtr,
        ).also { it._availableProtocols = collector.allProtocolNames.toSet() }
    } catch (failure: Throwable) {
        try {
            registryOwnerForCleanup?.close()
        } catch (cleanupFailure: Throwable) {
            if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
        }
        WaylandGlobals(0L, 0L)
    }
}

/** Binds xdg_wm_base and registers the ping→pong listener. Returns its address, or 0 on failure. */
private fun bindXdgWmBase(
    registry: MemorySegment,
    bind: java.lang.invoke.MethodHandle,
    collector: GlobalsCollector,
    addListener: java.lang.invoke.MethodHandle,
    lookup: MethodHandles.Lookup,
    displayPtr: Long,
): XdgWmBaseBinding? {
    var wmBase = MemorySegment.NULL
    var listenerArena: Arena? = null
    return try {
        val wmBaseIface = xdg_wm_base_interface
        val wmBaseNamePtr = wmBaseIface.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0L)
        wmBase = bind.invokeExact(
            registry, WL_REGISTRY_BIND, wmBaseIface, collector.xdgWmBaseVersion, 0,
            collector.xdgWmBaseName, wmBaseNamePtr, collector.xdgWmBaseVersion, MemorySegment.NULL,
        ) as MemorySegment
        check(wmBase.address() != 0L) { "xdg_wm_base bind returned NULL" }

        val arena = Arena.ofShared().also { listenerArena = it }
        val pinger = XdgWmBasePinger(wmBase.address(), displayPtr, collector.xdgWmBaseVersion)
        val pingStub = upcallStub(
            lookup.findVirtual(
                XdgWmBasePinger::class.java, "onPing",
                MethodType.methodType(Void.TYPE, MemorySegment::class.java, MemorySegment::class.java, Int::class.javaPrimitiveType),
            ).bindTo(pinger),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
            arena,
        )
        // struct xdg_wm_base_listener { ping } — 1 pointer.
        val pingListener = arena.allocate(ValueLayout.ADDRESS.byteSize())
        pingListener.set(ValueLayout.ADDRESS, 0L, pingStub)
        val listener = checkNotNull(wlProxyAddListener) { "wl_proxy_add_listener unavailable" }
        val listenerResult = listener.invokeExact(wmBase, pingListener, MemorySegment.NULL) as Int
        check(listenerResult == 0) { "xdg_wm_base listener installation failed: $listenerResult" }

        XdgWmBaseBinding(wmBase.address(), pinger, arena).also { listenerArena = null }
    } catch (failure: Throwable) {
        if (wmBase.address() != 0L) {
            try {
                wlProxyDestroy?.invokeExact(wmBase)
            } catch (cleanupFailure: Throwable) {
                if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
            }
        }
        try {
            listenerArena?.close()
        } catch (cleanupFailure: Throwable) {
            if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
        }
        System.err.println("[kadre-wayland] bindXdgWmBase failed: $failure")
        null
    }
}
