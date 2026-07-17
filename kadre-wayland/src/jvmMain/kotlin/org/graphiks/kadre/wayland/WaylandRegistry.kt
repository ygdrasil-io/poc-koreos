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

internal class BoundOutput(
    val registryName: Int,
    val proxy: Long,
    val version: Int,
    val info: WaylandOutputInfo,
) {
    private val closed = AtomicBoolean(false)
    internal var listenerLease: WaylandNativeListenerLease? = null

    fun close(destroyProxy: (Long) -> Unit) {
        if (!closed.compareAndSet(false, true)) return
        // The upcall arena is invalidated only after proxy destruction succeeds.
        destroyProxy(proxy)
        listenerLease?.releaseAfterProxyDestroyed()
        listenerLease = null
    }
}

/**
 * Owns the live wl_registry listener and every wl_output bound through it.
 * Output globals remain dynamic for the whole display connection lifetime.
 */
internal class WaylandRegistryOwner internal constructor(
    private val registryPtr: Long,
    @Suppress("unused") private val listenerArena: Arena,
    internal val collector: GlobalsCollector,
    private val bindOutput: (registryName: Int, advertisedVersion: Int) -> Pair<Long, Int>?,
    private val installOutputListener: (BoundOutput) -> AutoCloseable?,
    private val destroyProxy: (Long) -> Unit,
    private val closeListenerArena: () -> Unit = listenerArena::close,
    private val nativeListenerLifetime: WaylandNativeListenerLifetime = WaylandNativeListenerLifetime(),
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val outputsByRegistryName = linkedMapOf<Int, BoundOutput>()
    private val removalListeners = CopyOnWriteArrayList<(Long) -> Unit>()
    private val scaleListeners = CopyOnWriteArrayList<(WaylandOutputInfo, Int) -> Unit>()
    private val ownedChildren = mutableListOf<AutoCloseable>()
    private val ownedGlobalProxies = mutableListOf<Long>()
    private val registryListenerLease = nativeListenerLifetime.registerOrClose(
        AutoCloseable(closeListenerArena),
    )
    private val nativeFailureLock = Any()
    private val pendingNativeFailures = ArrayDeque<Throwable>()
    @Volatile private var nativeFailureSink: ((Throwable) -> Unit)? = null

    @Volatile
    var onOutputChanged: ((WaylandOutputInfo) -> Unit)? = null

    @Volatile
    var onOutputScaleChanged: ((WaylandOutputInfo, Int) -> Unit)? = null

    val outputs: List<BoundOutput>
        get() = synchronized(outputsByRegistryName) { outputsByRegistryName.values.toList() }

    internal val outputRemovalListenerCount: Int get() = removalListeners.size
    internal val outputScaleListenerCount: Int get() = scaleListeners.size

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
        synchronized(ownedChildren) {
            check(!closed.get()) { "Wayland registry owner is already closed" }
            ownedChildren += child
        }
    }

    fun ownChildOrClose(child: AutoCloseable) {
        try {
            ownChild(child)
        } catch (adoptionFailure: Throwable) {
            runWaylandCleanup(adoptionFailure, listOf(child::close))
            throw adoptionFailure
        }
    }

    fun ownGlobalProxy(proxy: Long) {
        if (proxy == 0L) return
        check(!closed.get()) { "Wayland registry owner is already closed" }
        synchronized(ownedGlobalProxies) { ownedGlobalProxies += proxy }
    }

    fun routeNativeFailuresTo(sink: (Throwable) -> Unit) {
        synchronized(nativeFailureLock) { nativeFailureSink = sink }
        while (true) {
            val pending = synchronized(nativeFailureLock) { pendingNativeFailures.firstOrNull() } ?: return
            try {
                sink(pending)
            } catch (sinkFailure: Throwable) {
                synchronized(nativeFailureLock) {
                    nativeFailureSink = null
                    if (sinkFailure !== pending) pendingNativeFailures.addLast(sinkFailure)
                }
                throw sinkFailure
            }
            synchronized(nativeFailureLock) {
                if (pendingNativeFailures.firstOrNull() === pending) pendingNativeFailures.removeFirst()
            }
        }
    }

    fun throwPendingNativeFailure() {
        val pending = synchronized(nativeFailureLock) {
            buildList {
                while (pendingNativeFailures.isNotEmpty()) add(pendingNativeFailures.removeFirst())
            }
        }
        val primary = pending.firstOrNull() ?: return
        pending.drop(1).forEach { additional ->
            if (additional !== primary) primary.addSuppressed(additional)
        }
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
                    if (nativeFailureSink === sink) nativeFailureSink = null
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
        var listenerBinding: AutoCloseable? = null
        val installationFailure = try {
            listenerBinding = installOutputListener(output)
            if (listenerBinding != null) null else IllegalStateException(
                "failed to install wl_output listener for registry global ${output.registryName}",
            )
        } catch (failure: Throwable) {
            failure
        }
        if (installationFailure != null) {
            try {
                destroyProxy(proxy)
            } catch (destroyFailure: Throwable) {
                if (destroyFailure !== installationFailure) installationFailure.addSuppressed(destroyFailure)
            }
            reportNativeFailure(installationFailure)
            return
        }
        output.listenerLease = try {
            nativeListenerLifetime.registerForProxyOrRollback(
                binding = checkNotNull(listenerBinding),
                proxy = output.proxy,
                destroyProxy = destroyProxy,
            )
        } catch (failure: Throwable) {
            reportNativeFailure(failure)
            return
        }
        val inserted = synchronized(outputsByRegistryName) {
            if (name in outputsByRegistryName) false
            else true.also { outputsByRegistryName[name] = output }
        }
        if (!inserted) {
            try {
                output.close(destroyProxy)
            } catch (failure: Throwable) {
                reportNativeFailure(failure)
            }
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
            removed.close(destroyProxy)
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
        val outputs = synchronized(outputsByRegistryName) {
            outputsByRegistryName.values.toList().also { outputsByRegistryName.clear() }
        }
        val otherChildren = synchronized(ownedChildren) {
            ownedChildren.toList().also { ownedChildren.clear() }
        }
        val globals = synchronized(ownedGlobalProxies) {
            ownedGlobalProxies.asReversed().toList().also { ownedGlobalProxies.clear() }
        }
        runWaylandCleanup(
            primary = null,
            cleanupActions = buildList {
                outputs.forEach { output -> add { output.close(destroyProxy) } }
                otherChildren.forEach { child -> add(child::close) }
                globals.forEach { proxy -> add { destroyProxy(proxy) } }
                add {
                    if (registryPtr != 0L) destroyProxy(registryPtr)
                    registryListenerLease.releaseAfterProxyDestroyed()
                }
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
internal class XdgWmBasePinger(
    private val pong: ((Int) -> Unit)?,
    private val flush: () -> Int,
    private val onFailure: (Throwable) -> Unit,
) {
    /** C callback: void ping(data, xdg_wm_base*, uint32 serial). */
    @Suppress("UNUSED_PARAMETER")
    fun onPing(data: MemorySegment, wmBase: MemorySegment, serial: Int) {
        try {
            checkNotNull(pong) { "xdg_wm_base pong operation unavailable" }(serial)
            val flushResult = flush()
            check(flushResult >= 0) { "wl_display_flush failed after xdg_wm_base pong: $flushResult" }
        } catch (failure: Throwable) {
            try {
                onFailure(failure)
            } catch (_: Throwable) {
                // Never let a Kotlin exception cross the native upcall boundary.
            }
        }
    }
}

internal class XdgWmBaseBinding(
    val proxy: Long,
    @Suppress("unused") private val pinger: XdgWmBasePinger,
    private val listenerLease: WaylandNativeListenerLease,
    private val destroyProxy: (Long) -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        if (proxy != 0L) destroyProxy(proxy)
        listenerLease.releaseAfterProxyDestroyed()
    }
}

internal enum class WaylandRegistryBootstrapStage {
    OpenArena,
    CreateCollector,
    CreateLookup,
    LoadOutputInterface,
    CreateOwner,
}

internal data class WaylandRegistryBootstrap<T>(
    val arena: Arena,
    val collector: GlobalsCollector,
    val lookup: MethodHandles.Lookup,
    val outputInterface: MemorySegment?,
    val owner: T,
)

/** Actual discovery bootstrap seam: the arena is transactional until owner transfer. */
internal fun <T> bootstrapWaylandRegistryOwner(
    failAt: (WaylandRegistryBootstrapStage) -> Unit = {},
    arenaFactory: () -> Arena = Arena::ofShared,
    ownerFactory: (Arena, GlobalsCollector, MethodHandles.Lookup, MemorySegment?) -> T,
): WaylandRegistryBootstrap<T> {
    failAt(WaylandRegistryBootstrapStage.OpenArena)
    val arena = arenaFactory()
    try {
        failAt(WaylandRegistryBootstrapStage.CreateCollector)
        val collector = GlobalsCollector()
        failAt(WaylandRegistryBootstrapStage.CreateLookup)
        val lookup = MethodHandles.lookup()
        failAt(WaylandRegistryBootstrapStage.LoadOutputInterface)
        val outputInterface = wlOutputInterface
        failAt(WaylandRegistryBootstrapStage.CreateOwner)
        val owner = ownerFactory(arena, collector, lookup, outputInterface)
        return WaylandRegistryBootstrap(arena, collector, lookup, outputInterface, owner)
    } catch (failure: Throwable) {
        if (arena.scope().isAlive) runWaylandCleanup(failure, listOf(arena::close))
        throw failure
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
    nativeListenerLifetime: WaylandNativeListenerLifetime = WaylandNativeListenerLifetime(),
): WaylandGlobals {
    val marshalNewId = wlProxyMarshalNewId ?: return WaylandGlobals(0L, 0L)
    val addListener = wlProxyAddListener ?: return WaylandGlobals(0L, 0L)
    val roundtrip = wlDisplayRoundtrip ?: return WaylandGlobals(0L, 0L)
    val bind = wlProxyMarshalBind ?: return WaylandGlobals(0L, 0L)
    val registryIface = wlRegistryInterface ?: return WaylandGlobals(0L, 0L)
    val compositorIface = wlCompositorInterface ?: return WaylandGlobals(0L, 0L)
    val getVersion = wlProxyGetVersion ?: return WaylandGlobals(0L, 0L)
    val destroy = wlProxyDestroy ?: return WaylandGlobals(0L, 0L)

    val display = MemorySegment.ofAddress(displayPtr)
    var registryOwnerForCleanup: WaylandRegistryOwner? = null
    val provisional = WaylandProxyTransaction { proxy ->
        destroy.invokeExact(MemorySegment.ofAddress(proxy))
    }

    return try {
        // 1. wl_display.get_registry → wl_registry*
        val displayVersion = getVersion.invokeExact(display) as Int
        val registry = marshalNewId.invokeExact(
            display, WL_DISPLAY_GET_REGISTRY, registryIface, displayVersion, 0, MemorySegment.NULL,
        ) as MemorySegment
        if (registry.address() == 0L) return WaylandGlobals(0L, 0L)
        provisional.adopt(registry.address())

        // 2. Registry listener (global/global_remove upcall) in a durable arena.
        lateinit var registryOwner: WaylandRegistryOwner
        val bootstrap = bootstrapWaylandRegistryOwner { arena, collector, lookup, outputIface ->
            WaylandRegistryOwner(
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
                        outputInfo = output.info,
                        onOutputChanged = registryOwner::notifyOutputChanged,
                        onScaleChanged = registryOwner::notifyOutputScaleChanged,
                        onFailure = registryOwner::reportNativeFailure,
                    )
                },
                destroyProxy = { proxy ->
                    destroy.invokeExact(MemorySegment.ofAddress(proxy))
                },
                nativeListenerLifetime = nativeListenerLifetime,
            )
        }
        val arena = bootstrap.arena
        val collector = bootstrap.collector
        val lookup = bootstrap.lookup
        registryOwner = bootstrap.owner
        registryOwnerForCleanup = registryOwner
        provisional.release(registry.address())

        fun bindOwnedGlobal(
            iface: MemorySegment,
            registryName: Int,
            advertisedVersion: Int,
            maximumVersion: Int = advertisedVersion,
        ): Pair<Long, Int> {
            val boundVersion = advertisedVersion.coerceAtMost(maximumVersion)
            val namePtr = iface.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0L)
            val proxy = (bind.invokeExact(
                registry, WL_REGISTRY_BIND, iface, boundVersion, 0,
                registryName, namePtr, boundVersion, MemorySegment.NULL,
            ) as MemorySegment).address()
            check(proxy != 0L) { "wl_registry.bind returned NULL for registry global $registryName" }
            provisional.adopt(proxy)
            registryOwner.ownGlobalProxy(proxy)
            provisional.release(proxy)
            return proxy to boundVersion
        }

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
        check(rc == 0) { "wl_registry listener installation failed: $rc" }

        // 3. roundtrip → triggers the global events (fills the collector).
        val roundtripResult = roundtrip.invokeExact(display) as Int
        check(roundtripResult >= 0) { "wl_display_roundtrip failed: $roundtripResult" }
        check(collector.compositorName >= 0) { "Wayland compositor did not advertise wl_compositor" }

        // 4. wl_registry.bind(wl_compositor). interface->name = 1st field (const char*).
        val compositorPtr = bindOwnedGlobal(
            compositorIface,
            collector.compositorName,
            collector.compositorVersion,
        ).first

        // 5. wl_registry.bind(xdg_wm_base) if present, then install the ping→pong listener.
        var xdgWmBasePtr = 0L
        if (collector.xdgWmBaseName >= 0) {
            val binding = bindXdgWmBase(
                registry, bind, collector, addListener, lookup, displayPtr,
                nativeListenerLifetime, registryOwner::reportNativeFailure,
                destroyProxy = { proxy -> destroy.invokeExact(MemorySegment.ofAddress(proxy)) },
            )
            xdgWmBasePtr = binding.proxy
            registryOwner.ownChildOrClose(binding)
        }

        // 6. wl_registry.bind(zxdg_decoration_manager_v1) for server-side window decorations.
        var decorationManagerPtr = 0L
        if (collector.decorationManagerName >= 0) {
            decorationManagerPtr = bindOwnedGlobal(
                zxdg_decoration_manager_v1_interface,
                collector.decorationManagerName,
                collector.decorationManagerVersion,
            ).first
        }

        // 7. wl_registry.bind(wl_seat) for keyboard/pointer/touch input.
        var seatPtr = 0L
        var seatVersion = 0
        if (collector.seatName >= 0) {
            val iface = wlSeatInterface
            if (iface != null) {
                val bound = bindOwnedGlobal(iface, collector.seatName, collector.seatVersion, 7)
                seatPtr = bound.first
                seatVersion = bound.second
            }
        }

        // 9. wl_registry.bind(zwp_text_input_manager_v3) for IME.
        var textInputManagerPtr = 0L
        if (collector.textInputManagerName >= 0) {
            val iface = zwpTextInputManagerV3Interface
            textInputManagerPtr = bindOwnedGlobal(
                iface, collector.textInputManagerName, collector.textInputManagerVersion, 1,
            ).first
        }

        // 10. wl_registry.bind(wl_shm) for cursor buffer creation.
        var shmPtr = 0L
        var shmVersion = 0
        if (collector.shmName >= 0) {
            val iface = wlShmInterface
            if (iface != null) {
                val bound = bindOwnedGlobal(iface, collector.shmName, collector.shmVersion, 1)
                shmPtr = bound.first
                shmVersion = bound.second
            }
        }

        // 11. wl_registry.bind(zwp_pointer_constraints_v1) for pointer confinement/locking.
        var pointerConstraintsPtr = 0L
        if (collector.pointerConstraintsName >= 0 && "zwp_pointer_constraints_v1" in protocolExtensions) {
            val iface = zwpPointerConstraintsV1Interface
            pointerConstraintsPtr = bindOwnedGlobal(
                iface, collector.pointerConstraintsName, collector.pointerConstraintsVersion,
            ).first
        }

        // 12. wl_registry.bind(xdg_toplevel_icon_manager_v1) for window icons.
        var iconManagerPtr = 0L
        if (collector.iconManagerName >= 0 && "xdg_toplevel_icon_manager_v1" in protocolExtensions) {
            val iface = xdgToplevelIconManagerV1Interface
            iconManagerPtr = bindOwnedGlobal(
                iface, collector.iconManagerName, collector.iconManagerVersion,
            ).first
        }

        // 13. wl_registry.bind(xdg_activation_v1) for activation tokens.
        var activationManagerPtr = 0L
        if (collector.activationManagerName >= 0 && "xdg_activation_v1" in protocolExtensions) {
            val iface = xdgActivationV1Interface
            activationManagerPtr = bindOwnedGlobal(
                iface, collector.activationManagerName, collector.activationManagerVersion,
            ).first
        }

        // 15. wl_registry.bind(ext_background_effect_v1) for Wayland blur (wlroots, KWin 6+).
        var extBackgroundEffectManagerPtr = 0L
        if (collector.extBackgroundEffectManagerName >= 0 && "ext_background_effect_v1" in protocolExtensions) {
            val iface = extBackgroundEffectV1Interface
            extBackgroundEffectManagerPtr = bindOwnedGlobal(
                iface, collector.extBackgroundEffectManagerName, collector.extBackgroundEffectManagerVersion,
            ).first
        }

        // 16. wl_registry.bind(org_kde_kwin_blur_manager) for Wayland blur (KWin 5.x).
        var kwinBlurManagerPtr = 0L
        if (collector.kwinBlurManagerName >= 0 && "org_kde_kwin_blur_manager" in protocolExtensions) {
            val iface = orgKdeKwinBlurManagerInterface
            kwinBlurManagerPtr = bindOwnedGlobal(
                iface, collector.kwinBlurManagerName, collector.kwinBlurManagerVersion,
            ).first
        }

        // 17. wl_registry.bind(wl_data_device_manager) for Drag & Drop.
        var dataDeviceManagerPtr = 0L
        if (collector.dataDeviceManagerName >= 0) {
            val iface = wlDataDeviceManagerInterface
            if (iface != null) {
                dataDeviceManagerPtr = bindOwnedGlobal(
                    iface, collector.dataDeviceManagerName, collector.dataDeviceManagerVersion,
                ).first
            }
        }
        WaylandGlobals(
            compositorPtr           = compositorPtr,
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
        provisional.rollback(failure)
        throw failure
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
    nativeListenerLifetime: WaylandNativeListenerLifetime,
    onFailure: (Throwable) -> Unit,
    destroyProxy: (Long) -> Unit,
): XdgWmBaseBinding {
    var wmBase = MemorySegment.NULL
    var listenerLease: WaylandNativeListenerLease? = null
    return try {
        val wmBaseIface = xdg_wm_base_interface
        val wmBaseNamePtr = wmBaseIface.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0L)
        wmBase = bind.invokeExact(
            registry, WL_REGISTRY_BIND, wmBaseIface, collector.xdgWmBaseVersion, 0,
            collector.xdgWmBaseName, wmBaseNamePtr, collector.xdgWmBaseVersion, MemorySegment.NULL,
        ) as MemorySegment
        check(wmBase.address() != 0L) { "xdg_wm_base bind returned NULL" }

        val arena = Arena.ofShared()
        listenerLease = nativeListenerLifetime.registerOrClose(AutoCloseable(arena::close))
        val pongHandle = wlProxyMarshalFlagsUint
        val flushHandle = wlDisplayFlush
        val pinger = XdgWmBasePinger(
            pong = pongHandle?.let { pong ->
                { serial ->
                    pong.invokeExact(
                        wmBase, XDG_WM_BASE_PONG, MemorySegment.NULL,
                        collector.xdgWmBaseVersion, 0, serial,
                    )
                }
            },
            flush = {
                val flush = checkNotNull(flushHandle) { "wl_display_flush unavailable" }
                flush.invokeExact(MemorySegment.ofAddress(displayPtr)) as Int
            },
            onFailure = onFailure,
        )
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
        val listenerResult = addListener.invokeExact(wmBase, pingListener, MemorySegment.NULL) as Int
        check(listenerResult == 0) { "xdg_wm_base listener installation failed: $listenerResult" }

        XdgWmBaseBinding(
            wmBase.address(), pinger, checkNotNull(listenerLease), destroyProxy,
        )
    } catch (failure: Throwable) {
        if (wmBase.address() != 0L) {
            try {
                destroyProxy(wmBase.address())
                listenerLease?.releaseAfterProxyDestroyed()
                listenerLease = null
            } catch (cleanupFailure: Throwable) {
                if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
            }
        } else {
            try {
                listenerLease?.releaseAfterProxyDestroyed()
                listenerLease = null
            } catch (cleanupFailure: Throwable) {
                if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
            }
        }
        throw failure
    }
}
