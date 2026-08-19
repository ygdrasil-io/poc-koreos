/**
 * WaylandEventLoop — Wayland event loop for kadre.
 *
 * Implements the canonical Wayland prepare_read / poll / read_events sequence
 * with a portable POSIX wake descriptor for inter-thread wakeup (`pipe2` or `eventfd`).
 *
 * Pump sequence:
 *  1. while (wl_display_prepare_read != 0) → wl_display_dispatch_pending
 *  2. wl_display_flush
 *  3. poll([displayFd, wakeFd], timeout)
 *  4. If displayFd ready → wl_display_read_events + dispatch_pending
 *     Otherwise          → wl_display_cancel_read
 *  5. If wakeFd ready → drain the portable wake descriptor
 *
 * WaylandEventLoop.
 */
package org.graphiks.kadre.wayland
import org.graphiks.kadre.ffi.wayland.*

import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.DeviceEvent
import org.graphiks.kadre.core.DeviceId
import org.graphiks.kadre.core.CursorImage
import org.graphiks.kadre.core.CustomCursor
import org.graphiks.kadre.core.DeviceEvents
import org.graphiks.kadre.core.EventLoopProxy
import org.graphiks.kadre.core.MonitorHandle
import org.graphiks.kadre.core.OwnedDisplayHandle
import org.graphiks.kadre.core.RawDisplayHandle
import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.Theme
import org.graphiks.kadre.core.VideoMode
import org.graphiks.kadre.core.Window
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import org.graphiks.kadre.ffi.posix.PosixWakeup
import org.graphiks.kadre.ffi.posix.PosixException
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.util.Queue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

// ── Singleton guard ───────────────────────────────────────────────────────────

/**
 * Guarantees that only a single Wayland loop runs at a time.
 * compareAndSet(false, true) before starting, set(false) in finally.
 */
internal val waylandRunning = AtomicBoolean(false)

internal sealed interface WaylandQueueItem

internal class WaylandWindowOwner(
    val window: WaylandWindow,
) {
    val redrawTransactionLock = Any()
    val tombstoned = AtomicBoolean(false)
    val nativeClosed = AtomicBoolean(false)
    val destroyedDelivered = AtomicBoolean(false)
}

internal data class WaylandQueuedWindowEvent(
    val owner: WaylandWindowOwner,
    val event: WindowEvent,
    val redrawToken: Any? = null,
) : WaylandQueueItem {
    val windowId: WindowId get() = owner.window.id
}

internal data class WaylandQueuedDeviceEvent(
    val event: DeviceEvent,
) : WaylandQueueItem

internal data class WaylandQueuedCloseCommand(
    val owner: WaylandWindowOwner,
    val token: Any,
) : WaylandQueueItem {
    val wakeFailures = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
    val windowId: WindowId get() = owner.window.id
}

internal data class WaylandCloseResult(
    val owner: WaylandWindowOwner,
    val closed: Boolean,
    val failure: Throwable?,
)

private class WaylandDispatchBoundary : WaylandQueueItem

// ── WaylandEventLoop ──────────────────────────────────────────────────────────

/**
 * Active Wayland event loop, passed to [ApplicationHandler] callbacks.
 *
 * Created and controlled by [runApp]. Windows created here are stored in
 * [windows] to enable event dispatch.
 *
 * @param displayPtr  Address of the wl_display* (Long, never 0).
 * @param compositorPtr Address of the wl_compositor* (Long, 0 if unavailable).
 * @param xdgWmBasePtr  Address of the xdg_wm_base* (Long, 0 if unavailable).
 * @param wakeup      owned POSIX descriptor used for inter-thread wakeup.
 */
class WaylandEventLoop internal constructor(
    internal val displayPtr: Long,
    internal val compositorPtr: Long,
    internal val xdgWmBasePtr: Long,
    internal val shmPtr: Long,
    internal val wakeup: PosixWakeup,
    internal val decorationManagerPtr: Long = 0L,
    internal val pointerConstraintsPtr: Long = 0L,
    internal val iconManagerPtr: Long = 0L,
    internal val activationManagerPtr: Long = 0L,
    internal val seatPtr: Long = 0L,
    internal val extBackgroundEffectManagerPtr: Long = 0L,
    internal val kwinBlurManagerPtr: Long = 0L,
    private val nativeListenerLifetime: WaylandNativeListenerLifetime = WaylandNativeListenerLifetime(),
) : ActiveEventLoop {

    /** The [WaylandGlobals] discovered during startup. Used by [protocols] and [hasProtocol]. */
    @PublishedApi
    internal var _globals: WaylandGlobals? = null

    /** Returns the set of protocol interface names announced by the compositor. */
    internal fun protocols(): Set<String> = _globals?.availableProtocols ?: emptySet()

    /** Active windows indexed by the address of their wl_surface*. */
    internal val windows = ConcurrentHashMap<Long, WaylandWindow>()
    private val windowOwners = ConcurrentHashMap<Long, WaylandWindowOwner>()

    /**
     * Window events produced by native upcalls (xdg configure/close), queued here and drained
     * into [ApplicationHandler.windowEvent] from the loop thread after each pump.
     */
    internal val eventQueue = java.util.concurrent.ConcurrentLinkedQueue<WaylandQueueItem>()

    /** Window IDs with one redraw already queued for the next Kotlin dispatch. */
    private val pendingRedraws = ConcurrentHashMap<WaylandWindowOwner, Any>()
    private val pendingCloseCommands = ConcurrentHashMap<WaylandWindowOwner, WaylandQueuedCloseCommand>()

    internal fun registerWindow(window: WaylandWindow): WaylandWindowOwner {
        val owner = WaylandWindowOwner(window)
        windowOwners[window.id.value] = owner
        windows[window.id.value] = window
        window.attachWindowEventSink { event -> enqueueWindowEvent(owner, event) }
        window.onRedrawRequested = { requestRedraw(owner) }
        window.onCloseRequested = { enqueueCloseWindow(owner) }
        window.onCompositorCloseRequested = { enqueueCloseWindow(owner) }
        window.registryOwner = _globals?.registryOwner
        if (window.takePendingCompositorClose()) {
            enqueueCloseWindow(owner)
        }
        return owner
    }

    internal fun adoptCreatedWindow(
        window: WaylandWindow,
        configure: (WaylandWindow) -> Unit = {},
    ): WaylandWindow {
        var owner: WaylandWindowOwner? = null
        try {
            owner = registerWindow(window)
            configure(window)
            requestRedraw(owner)
            return window
        } catch (failure: Throwable) {
            val registeredOwner = owner ?: windowOwners[window.id.value]?.takeIf { it.window === window }
            if (registeredOwner != null) {
                val closeResult = closeWindow(registeredOwner)
                closeResult.failure?.let {
                    if (it !== failure) failure.addSuppressed(it)
                }
                eventQueue.removeIf { item ->
                    (item is WaylandQueuedWindowEvent && item.owner === registeredOwner) ||
                        (item is WaylandQueuedCloseCommand && item.owner === registeredOwner)
                }
            } else {
                window.detachFromEventLoop()
                try {
                    window.closeNativeResources()
                } catch (closeFailure: Throwable) {
                    if (closeFailure !== failure) failure.addSuppressed(closeFailure)
                }
            }
            throw failure
        }
    }

    /**
     * Queues at most one redraw per window until it is dispatched. A successful first
     * insertion always signals the portable POSIX wake owner so an idle poll returns.
     */
    internal fun requestRedraw(windowId: WindowId): Boolean {
        val owner = windowOwners[windowId.value] ?: return false
        return requestRedraw(owner)
    }

    private fun requestRedraw(owner: WaylandWindowOwner): Boolean {
        synchronized(owner.redrawTransactionLock) {
            if (!isCurrentOwner(owner)) return false
            val token = Any()
            if (pendingRedraws.putIfAbsent(owner, token) != null) return true
            val queued = WaylandQueuedWindowEvent(owner, WindowEvent.RedrawRequested, token)
            eventQueue.add(queued)
            val wakeFailure = try {
                if (wakeup.signal()) return true
                IllegalStateException("portable POSIX wake owner is closed")
            } catch (failure: Throwable) {
                failure
            }
            rollbackRedraw(owner, token, queued)
            throw IllegalStateException("Wayland redraw wake failed", wakeFailure)
        }
    }

    private fun rollbackRedraw(owner: WaylandWindowOwner, token: Any, queued: WaylandQueuedWindowEvent) {
        if (pendingRedraws.remove(owner, token)) {
            eventQueue.remove(queued)
        }
    }

    internal fun consumeRedraw(owner: WaylandWindowOwner, token: Any?): Boolean {
        if (token == null) return false
        return pendingRedraws.remove(owner, token)
    }

    internal fun enqueueWindowEvent(windowId: WindowId, event: WindowEvent) {
        val owner = windowOwners[windowId.value] ?: return
        enqueueWindowEvent(owner, event)
    }

    private fun enqueueWindowEvent(owner: WaylandWindowOwner, event: WindowEvent) {
        eventQueue.add(WaylandQueuedWindowEvent(owner, event))
    }

    internal fun enqueueDeviceEvent(event: DeviceEvent) {
        eventQueue.add(WaylandQueuedDeviceEvent(event))
    }

    /** Tombstones ownership before publishing the loop-thread native close command. */
    private fun enqueueCloseWindow(owner: WaylandWindowOwner) {
        if (!tombstoneWindow(owner)) return
        val command = WaylandQueuedCloseCommand(owner, Any())
        pendingCloseCommands[owner] = command
        eventQueue.add(command)
        signalCloseWake()?.let(command.wakeFailures::add)
    }

    private fun signalCloseWake(): Throwable? = try {
        if (wakeup.signal()) {
            null
        } else {
            IllegalStateException("portable POSIX wake owner is closed")
        }
    } catch (failure: Throwable) {
        failure
    }.let { wakeFailure ->
        wakeFailure?.let { IllegalStateException("Wayland close wake failed", it) }
    }

    internal fun consumeCloseCommand(command: WaylandQueuedCloseCommand): Boolean =
        pendingCloseCommands.remove(command.owner, command)

    /** Test seam that closes the current owner and queues its terminal notification. */
    internal fun closeWindow(windowId: WindowId): Boolean {
        val owner = windowOwners[windowId.value] ?: return false
        val result = closeWindow(owner)
        if (result.closed) eventQueue.add(WaylandQueuedWindowEvent(owner, WindowEvent.Destroyed))
        result.failure?.let { throw it }
        return result.closed
    }

    /** Removes loop ownership before releasing any child or surface proxy. */
    internal fun closeWindow(owner: WaylandWindowOwner): WaylandCloseResult {
        tombstoneWindow(owner)
        if (!owner.nativeClosed.compareAndSet(false, true)) {
            return WaylandCloseResult(owner, closed = false, failure = null)
        }

        var failure: Throwable? = null
        try {
            owner.window.detachFromEventLoop()
            owner.window.closeNativeResources()
        } catch (thrown: Throwable) {
            failure = thrown
        }
        return WaylandCloseResult(owner, closed = true, failure = failure)
    }

    /** Removes logical ownership and all non-terminal work without touching native resources. */
    private fun tombstoneWindow(owner: WaylandWindowOwner): Boolean {
        if (!owner.tombstoned.compareAndSet(false, true)) return false
        windowOwners.remove(owner.window.id.value, owner)
        windows.remove(owner.window.id.value, owner.window)
        pendingRedraws.remove(owner)
        pendingCloseCommands.remove(owner)
        eventQueue.removeIf { item ->
            (item is WaylandQueuedWindowEvent && item.owner === owner) ||
                (item is WaylandQueuedCloseCommand && item.owner === owner)
        }
        return true
    }

    internal fun deliverDestroyed(
        owner: WaylandWindowOwner,
        handler: ApplicationHandler,
    ) {
        if (!owner.destroyedDelivered.compareAndSet(false, true)) return
        val current = windowOwners[owner.window.id.value]
        if (current != null && current !== owner) return
        handler.windowEvent(this, owner.window.id, WindowEvent.Destroyed)
    }

    internal fun isCurrentOwner(owner: WaylandWindowOwner): Boolean =
        windowOwners[owner.window.id.value] === owner && windows[owner.window.id.value] === owner.window

    /** Closes every remaining native window synchronously while the display is still valid. */
    internal fun closeAllWindowsDirect() {
        val commandsByOwner = pendingCloseCommands.values.associateBy { it.owner }
        val owners = (windowOwners.values + commandsByOwner.keys)
            .distinct()
            .sortedBy { it.window.id.value }
        var failure: Throwable? = null
        for (owner in owners) {
            val result = closeWindow(owner)
            result.failure?.let { failure = appendWaylandFailure(failure, it) }
            val command = commandsByOwner[owner]
            if (command != null) {
                while (true) {
                    val wakeFailure = command.wakeFailures.poll() ?: break
                    failure = appendWaylandFailure(failure, wakeFailure)
                }
            }
        }
        eventQueue.removeIf {
            it is WaylandQueuedWindowEvent || it is WaylandQueuedCloseCommand
        }
        failure?.let { throw it }
    }

    /**
     * Default CSD preference for newly created windows.
     * Set via [setPreferCsd] extension.
     */
    @Volatile
    internal var _preferCsd: Boolean = false

    /**
     * Activation token for xdg_activation_v1, set via [setActivationToken] extension.
     */
    internal var _activationToken: String? = null

    /**
     * Returns an [OwnedDisplayHandle] wrapping [RawDisplayHandle.Wayland].
     */
    override fun ownedDisplayHandle(): OwnedDisplayHandle? =
        OwnedDisplayHandle(RawDisplayHandle.Wayland(display = displayPtr))

    @Volatile private var _isExiting = false
    override val isExiting: Boolean get() = _isExiting

    @Volatile private var _controlFlow: ControlFlow = ControlFlow.Wait
    override val controlFlow: ControlFlow get() = _controlFlow

    private var _systemTheme: Theme? = null

    override fun setControlFlow(controlFlow: ControlFlow) {
        _controlFlow = controlFlow
    }

    override fun exit() {
        _isExiting = true
    }

    /**
     * Creates a native Wayland window and registers it in [windows].
     *
     * @param attributes Window configuration parameters.
     * @return The created window, or throws IllegalStateException if libwayland is absent.
     */
    override fun createWindow(attributes: WindowAttributes): Window {
        val window = WaylandWindow.createOwned(
            display = displayPtr,
            compositor = compositorPtr,
            xdgWmBase = xdgWmBasePtr,
            shmPtr = shmPtr,
            attrs = attributes,
            decorationManager = decorationManagerPtr,
            pointerConstraintsPtr = pointerConstraintsPtr,
            iconManagerPtr = iconManagerPtr,
            activationManagerPtr = activationManagerPtr,
            seatPtr = seatPtr,
            extBackgroundEffectManagerPtr = extBackgroundEffectManagerPtr,
            kwinBlurManagerPtr = kwinBlurManagerPtr,
            nativeListenerLifetime = nativeListenerLifetime,
            ownsNativeListenerLifetime = false,
        ) ?: error("WaylandWindow.create failed — libwayland-client.so.0 absent or display invalid")
        return adoptCreatedWindow(window)
    }

    /**
     * Creates a window with Wayland-specific attributes.
     *
     * Merges [WaylandWindowAttributes] fields into the core [WindowAttributes]
     * and applies platform-specific settings at creation time.
     */
    fun createWindow(attrs: WaylandWindowAttributes): Window {
        val window = WaylandWindow.createOwned(
            display = displayPtr,
            compositor = compositorPtr,
            xdgWmBase = xdgWmBasePtr,
            shmPtr = shmPtr,
            attrs = attrs.core,
            decorationManager = decorationManagerPtr,
            pointerConstraintsPtr = pointerConstraintsPtr,
            iconManagerPtr = iconManagerPtr,
            activationManagerPtr = activationManagerPtr,
            seatPtr = seatPtr,
            extBackgroundEffectManagerPtr = extBackgroundEffectManagerPtr,
            kwinBlurManagerPtr = kwinBlurManagerPtr,
            nativeListenerLifetime = nativeListenerLifetime,
            ownsNativeListenerLifetime = false,
        ) ?: error("WaylandWindow.create failed — libwayland-client.so.0 absent")
        return adoptCreatedWindow(window) { ownedWindow ->
            attrs.preferCsd?.let { ownedWindow.setPreferCsd(it) }
            attrs.activationToken?.let { ownedWindow.setActivationToken(it) }
            attrs.name?.let { name -> ownedWindow.setAppId(name) }
        }
    }

    /**
     * Creates a thread-safe proxy to this event loop.
     *
     * The proxy uses the portable POSIX wake descriptor to wake the loop from any thread.
     */
    override fun createProxy(): EventLoopProxy = WaylandEventLoopProxy(wakeup)

    // ── R2: monitor enumeration ───────────────────────────────────────────────

    /**
     * Returns the list of monitors detected via wl_output geometry/mode/scale events.
     *
     * ### Sprint 3 (#272)
     * Prior to Sprint 3, this returned a synthetic monitor derived from the first
     * window's size. Now it uses real [WaylandOutputInfo] data collected from the
     * wl_output listener. If no output info is available yet (e.g. during early
     * startup), falls back to a synthetic monitor. Once global discovery has completed, this
     * returns the live output map verbatim; removing the final `wl_output` therefore returns an
     * empty list.
     */
    override fun availableMonitors(): List<MonitorHandle> {
        val realOutputs = _globals?.registryOwner?.outputs?.map { it.info.toMonitorHandle() }.orEmpty()
        if (_globals != null) return realOutputs

        // Before startup discovery completes, retain the historical synthetic fallback.
        val win = windows.values.firstOrNull()
        val scale = win?._scaleFactor ?: 1.0
        val size = win?.innerSize ?: PhysicalSize(1920, 1080)
        return listOf(syntheticWaylandMonitor(displayPtr, scale, size))
    }

    /**
     * Returns null because Wayland has no global primary-monitor concept.
     *
     * This matches winit's Wayland backend. [availableMonitors] may still expose
     * synthetic output data, but that must not be promoted to a primary monitor.
     */
    override fun primaryMonitor(): MonitorHandle? = null

    // ── R3: system theme ──────────────────────────────────────────────────────

    override fun systemTheme(): Theme? {
        if (_systemTheme == null) {
            _systemTheme = WaylandThemePortal.queryColorScheme()
        }
        return _systemTheme
    }

    fun refreshTheme() {
        WaylandThemePortal.resetCache()
        val newTheme = WaylandThemePortal.queryColorScheme()
        val oldTheme = _systemTheme
        _systemTheme = newTheme
        if (newTheme != null && newTheme != oldTheme) {
            for (win in windows.values) {
                enqueueWindowEvent(win.id, WindowEvent.ThemeChanged(newTheme))
            }
        }
    }

    // ── R3b: occlusion ───────────────────────────────────────────────────────

    /**
     * [WindowEvent.Occluded] is **not emitted on Wayland**.
     *
     * The xdg-shell protocol does not expose surface occlusion information.
     * The compositor is a separate process and does not inform clients when
     * their windows are fully obscured by other surfaces. This is an
     * intentional non-emission — there is no standard Wayland protocol to
     * query or subscribe to occlusion state.
     */

    // ── R4: device event filter ───────────────────────────────────────────────

    /**
     * Device event filter controlling raw [DeviceEvent] dispatch.
     *
     * Defaults to [DeviceEvents.WhenFocused], which is the natural behavior on
     * Wayland since keyboard/pointer events are already delivered per-surface.
     * [DeviceEvents.Never] suppresses raw `DeviceEvent.Key` dispatch.
     */
    internal val liveDeviceFilter = WaylandDeviceFilter()
    internal val deviceEventFilter: DeviceEvents get() = liveDeviceFilter.current

    private val nativeFailureQueue = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()

    internal fun queueNativeFailure(failure: Throwable) {
        nativeFailureQueue.add(failure)
    }

    internal fun throwPendingNativeFailure() {
        val primary = nativeFailureQueue.poll() ?: return
        while (true) {
            val additional = nativeFailureQueue.poll() ?: break
            if (additional !== primary) primary.addSuppressed(additional)
        }
        throw primary
    }

    override fun listenDeviceEvents(mode: DeviceEvents) {
        liveDeviceFilter.update(mode)
    }

    // ── R6: gestures ──────────────────────────────────────────────────────────

    /**
     * Gesture events ([WindowEvent.PinchGesture], [WindowEvent.PanGesture],
     * [WindowEvent.RotationGesture], [WindowEvent.DoubleTapGesture]) are
     * **not emitted on Wayland**.
     *
     * The wl_pointer / wl_touch protocols expose raw pointer coordinates and
     * touch points but do NOT include gesture recognition. Gesture recognition
     * is typically performed by the compositor (e.g. libinput) and not forwarded
     * to individual clients. There is no standard Wayland protocol for gesture
     * events.
     */

    // ── R5-CustomCursor ─────────────────────────────────────────────────────────

    /**
     * Stores the [CursorImage] data and returns a [CustomCursor] handle.
     *
     * The returned handle can be passed to [Window.setCustomCursor], which will
     * look up the stored image data and apply it through a `wl_shm` cursor
     * surface when the window has a current `wl_pointer` enter serial.
     */
    override fun createCustomCursor(image: CursorImage): CustomCursor? {
        if (image.width <= 0 || image.height <= 0 || image.rgba.isEmpty()) return null
        val id = WaylandCustomCursorStore.store(image)
        return CustomCursor(id)
    }
}

internal fun routeWaylandInputEvent(
    surfacePtr: Long,
    event: WindowEvent,
    windows: Map<Long, WaylandWindow>,
    eventQueue: Queue<Pair<WindowId, WindowEvent>>,
): Boolean {
    val win = windows[surfacePtr] ?: return false
    eventQueue.add(win.id to event)
    return true
}

private fun routeWaylandInputEvent(
    surfacePtr: Long,
    event: WindowEvent,
    windows: Map<Long, WaylandWindow>,
    enqueue: (WindowId, WindowEvent) -> Unit,
): Boolean {
    val win = windows[surfacePtr] ?: return false
    enqueue(win.id, event)
    return true
}

/** Dispatches the Kotlin-visible part of one iteration after the native pump completed. */
internal fun dispatchWaylandIteration(
    eventLoop: WaylandEventLoop,
    handler: ApplicationHandler,
    startCause: StartCause,
) {
    val boundary = WaylandDispatchBoundary()
    eventLoop.eventQueue.add(boundary)
    try {
        handler.newEvents(eventLoop, startCause)
        val batch = mutableListOf<WaylandQueueItem>()
        while (true) {
            val item = eventLoop.eventQueue.poll() ?: break
            if (item === boundary) break
            batch += item
        }
        // Terminal commands own the batch: closing first clears pending redraw tokens and makes
        // every ordinary event already queued for that window invalid before callbacks run.
        var terminalFailure: Throwable? = null
        batch.filterIsInstance<WaylandQueuedCloseCommand>().forEach { command ->
            if (eventLoop.consumeCloseCommand(command)) {
                val result = eventLoop.closeWindow(command.owner)
                result.failure?.let { failure ->
                    terminalFailure = appendWaylandFailure(terminalFailure, failure)
                }
                if (result.closed) {
                    try {
                        eventLoop.deliverDestroyed(command.owner, handler)
                    } catch (failure: Throwable) {
                        terminalFailure = appendWaylandFailure(terminalFailure, failure)
                    }
                }
                while (true) {
                    val wakeFailure = command.wakeFailures.poll() ?: break
                    terminalFailure = appendWaylandFailure(terminalFailure, wakeFailure)
                }
            }
        }
        terminalFailure?.let { throw it }
        for (item in batch) {
            if (item is WaylandQueuedCloseCommand) continue
            if (item is WaylandQueuedDeviceEvent) {
                handler.deviceEvent(eventLoop, DeviceId(0L), item.event)
                continue
            }
            val queued = item as WaylandQueuedWindowEvent
            val windowId = queued.windowId
            val event = queued.event
            if (event == WindowEvent.Destroyed) {
                eventLoop.deliverDestroyed(queued.owner, handler)
                continue
            }
            if (event == WindowEvent.RedrawRequested) {
                if (!eventLoop.consumeRedraw(queued.owner, queued.redrawToken)) continue
            }
            if (!eventLoop.isCurrentOwner(queued.owner)) continue
            handler.windowEvent(eventLoop, windowId, event)
        }
        handler.aboutToWait(eventLoop)
    } finally {
        eventLoop.eventQueue.remove(boundary)
    }
}

private fun appendWaylandFailure(primary: Throwable?, additional: Throwable): Throwable {
    if (primary == null) return additional
    if (additional !== primary) primary.addSuppressed(additional)
    return primary
}

internal fun startWaylandLifecycle(
    eventLoop: WaylandEventLoop,
    handler: ApplicationHandler,
) {
    handler.resumed(eventLoop)
    handler.newEvents(eventLoop, StartCause.Init)
    handler.canCreateSurfaces(eventLoop)
    handler.aboutToWait(eventLoop)
}

internal fun shutdownWaylandLifecycle(
    eventLoop: WaylandEventLoop,
    handler: ApplicationHandler,
) {
    var failure: Throwable? = null
    try {
        handler.destroySurfaces(eventLoop)
    } catch (thrown: Throwable) {
        failure = appendWaylandFailure(failure, thrown)
    }
    try {
        eventLoop.closeAllWindowsDirect()
    } catch (thrown: Throwable) {
        failure = appendWaylandFailure(failure, thrown)
    }
    try {
        handler.suspended(eventLoop)
    } catch (thrown: Throwable) {
        failure = appendWaylandFailure(failure, thrown)
    }
    failure?.let { throw it }
}

/** Creates a synthetic [MonitorHandle] for a Wayland output. */
private fun syntheticWaylandMonitor(
    outputPtr: Long,
    scale: Double,
    size: PhysicalSize<Int>,
): MonitorHandle = object : MonitorHandle {
    override val id: Long = outputPtr
    override val name: String? = null
    override val position: PhysicalPosition<Int> = PhysicalPosition(0, 0)
    override val scaleFactor: Double = scale
    override val currentVideoMode: VideoMode = VideoMode(
        size = size,
        bitDepth = null,
        refreshRateMilliHz = null,
    )
    override val videoModes: List<VideoMode> = listOf(currentVideoMode)
}

// ── runApp ────────────────────────────────────────────────────────────────────

/**
 * Starts the Wayland event loop and delegates the lifecycle to [handler].
 *
 * Blocking: only returns when the loop ends via [ActiveEventLoop.exit] or an exception.
 *
 * @throws IllegalStateException if a Wayland loop is already running.
 * @throws IllegalStateException if wl_display_connect fails.
 */
fun runApp(handler: ApplicationHandler) {
    if (!waylandRunning.compareAndSet(false, true)) {
        error("WaylandEventLoop already running")
    }
    try {
        runAppInternal(handler)
    } finally {
        waylandRunning.set(false)
    }
}

internal fun waylandStartupFailure(
    operation: String,
    display: String?,
    cause: Throwable,
): IllegalStateException {
    val displayContext = display ?: "<absent>"
    val nativeCause = buildString {
        append(cause::class.simpleName ?: "Throwable")
        cause.message?.takeIf(String::isNotBlank)?.let {
            append(": ")
            append(it)
        }
    }
    return IllegalStateException(
        "backend=Wayland WAYLAND_DISPLAY=$displayContext operation=$operation cause=$nativeCause",
        cause,
    )
}

internal inline fun <T> runWaylandStartupOperation(
    operation: String,
    display: String?,
    action: () -> T,
): T = try {
    action()
} catch (failure: Throwable) {
    throw waylandStartupFailure(operation, display, failure)
}

internal inline fun <T> installWaylandSeatForStartup(
    display: String?,
    install: () -> T,
    throwPendingNativeFailure: () -> Unit,
): T = runWaylandStartupOperation("install seat listeners", display) {
    val binding = install()
    throwPendingNativeFailure()
    binding
}

internal fun requireWaylandGlobals(globals: WaylandGlobals) {
    check(globals.compositorPtr != 0L) {
        "required Wayland global wl_compositor was not announced"
    }
    check(globals.xdgWmBasePtr != 0L) {
        "required Wayland global xdg_wm_base was not announced"
    }
}

// ── Internal implementation ───────────────────────────────────────────────────

private fun runAppInternal(handler: ApplicationHandler) {
    val waylandDisplay = System.getenv("WAYLAND_DISPLAY")
    // ── 1. Connect to the Wayland server ──────────────────────────────────────
    if (waylandNativeDisabled()) {
        throw waylandStartupFailure(
            operation = "enable native access",
            display = waylandDisplay,
            cause = IllegalStateException("Wayland native access is disabled"),
        )
    }
    val connectHandle = wlDisplayConnect ?: throw waylandStartupFailure(
        operation = "resolve wl_display_connect",
        display = waylandDisplay,
        cause = IllegalStateException("libwayland-client.so.0 does not export wl_display_connect"),
    )

    val displaySeg: MemorySegment = try {
        val nullSeg = MemorySegment.NULL
        connectHandle.invokeExact(nullSeg) as MemorySegment
    } catch (failure: Throwable) {
        throw waylandStartupFailure("wl_display_connect", waylandDisplay, failure)
    }

    if (displaySeg == MemorySegment.NULL || displaySeg.address() == 0L) {
        throw waylandStartupFailure(
            operation = "wl_display_connect",
            display = waylandDisplay,
            cause = IllegalStateException("wl_display_connect returned NULL"),
        )
    }

    val displayPtr = displaySeg.address()

    // ── 2. Wayland socket file descriptor ─────────────────────────────────────
    val displayFd: Int = try {
        val fdHandle = wlDisplayGetFd
            ?: error("wl_display_get_fd not available")
        fdHandle.invokeExact(displaySeg) as Int
    } catch (t: Throwable) {
        // Clean disconnect before propagating
        runWaylandCleanup(t, listOf({ disconnectWaylandDisplay(displaySeg) }))
        throw waylandStartupFailure("wl_display_get_fd", waylandDisplay, t)
    }

    // ── 3. Create the portable POSIX wake owner ───────────────────────────────
    val wakeup = try {
        PosixWakeup.open()
    } catch (t: Throwable) {
        runWaylandCleanup(t, listOf({ disconnectWaylandDisplay(displaySeg) }))
        throw waylandStartupFailure("create POSIX wake", waylandDisplay, t)
    }

    var seatBinding: WaylandSeatBinding? = null
    var textInputBinding: WaylandTextInputBinding? = null
    var registryOwner: WaylandRegistryOwner? = null
    var eventLoopForCleanup: WaylandEventLoop? = null
    val nativeListenerLifetime = WaylandNativeListenerLifetime()
    preservingWaylandCleanup(
        cleanupActions = listOf(
            { eventLoopForCleanup?.closeAllWindowsDirect(); Unit },
            { textInputBinding?.close(); Unit },
            { registryOwner?.close(); Unit },
            { closeWaylandResources(wakeup) { disconnectWaylandDisplay(displaySeg) } },
            nativeListenerLifetime::closeAfterDisplayDisconnect,
            {
                val binding = seatBinding
                if (binding != null) binding.close()
            },
        ),
    ) {
        // ── 4. Discover Wayland globals (compositor, seat, output) ───────────
        // get_registry + listener(global) + roundtrip + bind(wl_compositor, wl_seat, wl_output, …).
        val globals = try {
            discoverGlobals(displayPtr, nativeListenerLifetime = nativeListenerLifetime).also {
                registryOwner = it.registryOwner
                requireWaylandGlobals(it)
            }
        } catch (failure: Throwable) {
            throw waylandStartupFailure("discover globals", waylandDisplay, failure)
        }

        val eventLoop = WaylandEventLoop(
            displayPtr, globals.compositorPtr, globals.xdgWmBasePtr, globals.shmPtr, wakeup,
            globals.decorationManagerPtr, globals.pointerConstraintsPtr, globals.iconManagerPtr,
            globals.activationManagerPtr, globals.seatPtr,
            globals.extBackgroundEffectManagerPtr, globals.kwinBlurManagerPtr,
            nativeListenerLifetime,
        ).also { it._globals = globals }
        eventLoopForCleanup = eventLoop
        globals.registryOwner?.routeNativeFailuresTo(eventLoop::queueNativeFailure)

        // ── 4b. Install seat / output listeners (keyboard, pointer, touch, scale) ─
        // Route all input events into the eventQueue by their source wl_surface.
        // The seat and output globals may be absent (0) — installSeatListeners tolerates that.
        seatBinding = installWaylandSeatForStartup(
            display = waylandDisplay,
            install = {
                installSeatListeners(
                displayPtr    = displayPtr,
                seatPtr       = globals.seatPtr,
                seatVersion   = globals.seatVersion,
                onEvent = { surfacePtr, event ->
                    routeWaylandInputEvent(
                        surfacePtr,
                        event,
                        eventLoop.windows,
                        eventLoop::enqueueWindowEvent,
                    )
                },
                onDeviceEvent = eventLoop::enqueueDeviceEvent,
                dataDeviceManagerPtr = globals.dataDeviceManagerPtr,
                deviceFilter = eventLoop.liveDeviceFilter,
                onNativeFailure = eventLoop::queueNativeFailure,
                failOnNativeError = true,
                onBindingCreated = { seatBinding = it },
                )
            },
            throwPendingNativeFailure = eventLoop::throwPendingNativeFailure,
        )

        // ── 4c. Create zwp_text_input_v3 for IME (if compositor exposes the protocol) ──
        if (globals.textInputManagerPtr != 0L && globals.seatPtr != 0L) {
            runWaylandStartupOperation(
                operation = "create text input",
                display = waylandDisplay,
            ) {
                textInputBinding = createTextInput(
                    managerPtr = globals.textInputManagerPtr,
                    seatPtr = globals.seatPtr,
                    display = displayPtr,
                    onEvent = { surfacePtr, event ->
                        routeWaylandInputEvent(
                            surfacePtr,
                            event,
                            eventLoop.windows,
                            eventLoop::enqueueWindowEvent,
                        )
                    },
                    nativeListenerLifetime = nativeListenerLifetime,
                    onNativeFailure = eventLoop::queueNativeFailure,
                    failOnNativeError = true,
                )
            }
        }

        // ── 5-7. Initial lifecycle and pre-pump wait boundary ─────────────────
        startWaylandLifecycle(eventLoop, handler)

        // ── 8. Main loop ──────────────────────────────────────────────────────
        while (!eventLoop.isExiting) {
            // Compute the timeout in milliseconds. If we already have queued window events (e.g.
            // a pending RedrawRequested), don't block in poll — drain them this iteration.
            val timeoutMs: Int = if (eventLoop.eventQueue.isNotEmpty()) {
                0
            } else when (val cf = eventLoop.controlFlow) {
                is ControlFlow.Wait -> -1
                is ControlFlow.Poll -> 0
                is ControlFlow.WaitUntil -> {
                    val delta = cf.instant - System.currentTimeMillis()
                    if (delta <= 0) 0 else delta.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                }
            }

            // Canonical Wayland prepare_read / poll / read_events sequence. This dispatches the
            // pending Wayland protocol events, whose native upcalls enqueue WindowEvents.
            val startCause = pumpOnce(displaySeg, displayFd, wakeup, timeoutMs, eventLoop)
            eventLoop.throwPendingNativeFailure()
            dispatchWaylandIteration(eventLoop, handler, startCause)
        }

        // ── 9. Shutdown ───────────────────────────────────────────────────────
        shutdownWaylandLifecycle(eventLoop, handler)
    }
}

/**
 * Runs cleanup actions in order without losing the failure that left the loop body.
 * If [primary] is null, the first cleanup failure becomes primary and later failures
 * are suppressed on it. Otherwise every cleanup failure is suppressed directly on
 * [primary], which is already propagating from the body.
 */
internal fun runWaylandCleanup(
    primary: Throwable?,
    cleanupActions: List<() -> Unit>,
) {
    var cleanupPrimary: Throwable? = null
    for (cleanup in cleanupActions) {
        try {
            cleanup()
        } catch (failure: Throwable) {
            if (primary != null) {
                if (failure !== primary) primary.addSuppressed(failure)
            } else if (cleanupPrimary == null) {
                cleanupPrimary = failure
            } else if (failure !== cleanupPrimary) {
                cleanupPrimary.addSuppressed(failure)
            }
        }
    }
    if (primary == null) cleanupPrimary?.let { throw it }
}

internal fun <T> preservingWaylandCleanup(
    cleanupActions: List<() -> Unit>,
    body: () -> T,
): T {
    var bodyFailure: Throwable? = null
    try {
        return body()
    } catch (failure: Throwable) {
        bodyFailure = failure
        throw failure
    } finally {
        runWaylandCleanup(bodyFailure, cleanupActions)
    }
}

internal fun closeWaylandResources(
    wakeup: PosixWakeup,
    disconnectDisplay: () -> Unit,
) = closeWaylandResources(wakeup::close, disconnectDisplay)

internal fun closeWaylandResources(
    closeWakeup: () -> Unit,
    disconnectDisplay: () -> Unit,
) {
    // Stop every proxy before invalidating the display they wake.
    runWaylandCleanup(
        primary = null,
        cleanupActions = listOf(closeWakeup, disconnectDisplay),
    )
}

internal interface WaylandPumpOperations {
    fun prepareRead(): Int
    fun dispatchPending()
    fun flush()
    fun readEvents()
    fun cancelRead()
}

internal fun interface WaylandPoller {
    fun poll(displayFd: Int, wakeFd: Int, timeoutMs: Int): WaylandPollResult
}

internal sealed interface WaylandPollResult {
    data class Ready(
        val displayReadable: Boolean,
        val wakeReadable: Boolean,
    ) : WaylandPollResult

    data class Failure(val errno: Int) : WaylandPollResult

    data class DescriptorFailure(
        val displayRevents: Short,
        val wakeRevents: Short,
    ) : WaylandPollResult
}

internal fun interface WaylandMonotonicClock {
    fun nowNanos(): Long
}

private const val POSIX_EINTR = 4
private const val NANOS_PER_MILLISECOND = 1_000_000L
private const val POLL_ERROR_MASK =
    POLLERR.toInt() or POLLHUP.toInt() or POLLNVAL.toInt()

private val systemWaylandMonotonicClock = WaylandMonotonicClock(System::nanoTime)

private class NativeWaylandPumpOperations(
    private val display: MemorySegment,
) : WaylandPumpOperations {
    override fun prepareRead(): Int =
        (wlDisplayPrepareRead ?: error("wl_display_prepare_read not available"))
            .invokeExact(display) as Int

    override fun dispatchPending() {
        (wlDisplayDispatchPending ?: error("wl_display_dispatch_pending not available"))
            .invokeExact(display) as Int
    }

    override fun flush() {
        (wlDisplayFlush ?: error("wl_display_flush not available"))
            .invokeExact(display) as Int
    }

    override fun readEvents() {
        (wlDisplayReadEvents ?: error("wl_display_read_events not available"))
            .invokeExact(display) as Int
    }

    override fun cancelRead() {
        (wlDisplayCancelRead ?: error("wl_display_cancel_read not available"))
            .invokeExact(display)
    }
}

private object NativeWaylandPoller : WaylandPoller {
    override fun poll(
        displayFd: Int,
        wakeFd: Int,
        timeoutMs: Int,
    ): WaylandPollResult = Arena.ofConfined().use { arena ->
        val fds = allocPollFd(arena)
        setPollFd(fds, 0, displayFd, POLLIN)
        setPollFd(fds, 1, wakeFd, POLLIN)

        val result = invokeNativePoll(fds, 2L, timeoutMs)
        if (result.value < 0) {
            WaylandPollResult.Failure(
                result.errno ?: error("poll failed without a captured errno"),
            )
        } else if (result.value == 0) {
            WaylandPollResult.Ready(displayReadable = false, wakeReadable = false)
        } else {
            decodeWaylandPollResult(
                pollCount = result.value,
                displayRevents = getPollRevents(fds, 0),
                wakeRevents = getPollRevents(fds, 1),
            )
        }
    }
}

internal fun decodeWaylandPollResult(
    pollCount: Int,
    displayRevents: Short,
    wakeRevents: Short,
): WaylandPollResult {
    if (pollCount == 0) {
        return WaylandPollResult.Ready(displayReadable = false, wakeReadable = false)
    }

    val displayFlags = displayRevents.toInt() and 0xffff
    val wakeFlags = wakeRevents.toInt() and 0xffff
    val hasDescriptorError =
        (displayFlags and POLL_ERROR_MASK) != 0 || (wakeFlags and POLL_ERROR_MASK) != 0
    val displayReadable = (displayFlags and POLLIN.toInt()) != 0
    val wakeReadable = (wakeFlags and POLLIN.toInt()) != 0

    return if (hasDescriptorError || (!displayReadable && !wakeReadable)) {
        WaylandPollResult.DescriptorFailure(displayRevents, wakeRevents)
    } else {
        WaylandPollResult.Ready(displayReadable, wakeReadable)
    }
}

internal fun pumpWaylandOnce(
    operations: WaylandPumpOperations,
    poller: WaylandPoller,
    wakeup: PosixWakeup,
    displayFd: Int,
    timeoutMs: Int,
    clock: WaylandMonotonicClock = systemWaylandMonotonicClock,
): WaylandPollResult.Ready {
    val durationNanos = timeoutMs
        .takeIf { it > 0 }
        ?.toLong()
        ?.times(NANOS_PER_MILLISECOND)
    val startedAtNanos = durationNanos?.let { clock.nowNanos() }
    var retryingAfterInterrupt = false

    while (true) {
        val currentTimeoutMs = if (
            durationNanos != null && startedAtNanos != null && retryingAfterInterrupt
        ) {
            // System.nanoTime values are only meaningful through subtraction. Long
            // overflow here intentionally implements the documented wraparound arithmetic.
            val elapsedNanos = clock.nowNanos() - startedAtNanos
            val remainingNanos = durationNanos - elapsedNanos
            if (remainingNanos <= 0L) {
                return WaylandPollResult.Ready(
                    displayReadable = false,
                    wakeReadable = false,
                )
            }
            // Ceiling division without `remaining + unit - 1`, which could overflow.
            (((remainingNanos - 1L) / NANOS_PER_MILLISECOND) + 1L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        } else {
            timeoutMs
        }

        while (operations.prepareRead() != 0) {
            operations.dispatchPending()
        }

        try {
            operations.flush()
        } catch (failure: Throwable) {
            cancelPreparedRead(operations, failure)
            throw failure
        }

        val pollResult = try {
            poller.poll(displayFd, wakeup.readFd, currentTimeoutMs)
        } catch (failure: Throwable) {
            cancelPreparedRead(operations, failure)
            throw failure
        }

        when (pollResult) {
            is WaylandPollResult.Failure -> {
                if (pollResult.errno == POSIX_EINTR) {
                    cancelPreparedRead(operations)
                    retryingAfterInterrupt = true
                    continue
                }
                val failure = PosixException("poll", pollResult.errno)
                cancelPreparedRead(operations, failure)
                throw failure
            }
            is WaylandPollResult.DescriptorFailure -> {
                val failure = IllegalStateException(
                    "Wayland poll descriptor failure: " +
                        "display=${formatPollRevents(pollResult.displayRevents)}, " +
                        "wake=${formatPollRevents(pollResult.wakeRevents)}",
                )
                cancelPreparedRead(operations, failure)
                throw failure
            }
            is WaylandPollResult.Ready -> {
                if (pollResult.displayReadable) {
                    operations.readEvents()
                    operations.dispatchPending()
                } else {
                    cancelPreparedRead(operations)
                }

                if (pollResult.wakeReadable && !wakeup.drain()) {
                    error("Wayland wake descriptor closed while the event loop is running")
                }
                return pollResult
            }
        }
    }
}

private fun formatPollRevents(revents: Short): String {
    val flags = revents.toInt() and 0xffff
    if (flags == 0) return "none"

    val names = buildList {
        if ((flags and POLLIN.toInt()) != 0) add("POLLIN")
        if ((flags and POLLERR.toInt()) != 0) add("POLLERR")
        if ((flags and POLLHUP.toInt()) != 0) add("POLLHUP")
        if ((flags and POLLNVAL.toInt()) != 0) add("POLLNVAL")
        val known = POLLIN.toInt() or POLL_ERROR_MASK
        val unknown = flags and known.inv()
        if (unknown != 0) add("0x${unknown.toString(16)}")
    }
    return names.joinToString("|")
}

private fun cancelPreparedRead(
    operations: WaylandPumpOperations,
    primaryFailure: Throwable? = null,
) {
    try {
        operations.cancelRead()
    } catch (cancelFailure: Throwable) {
        if (primaryFailure == null) throw cancelFailure
        if (primaryFailure !== cancelFailure) primaryFailure.addSuppressed(cancelFailure)
    }
}

/**
 * Performs one iteration of the canonical Wayland pump.
 *
 * Sequence:
 *  1. Drain the queue (prepare_read retry)
 *  2. Flush
 *  3. poll([displayFd, wakeup.readFd], timeoutMs)
 *  4. Conditional processing of the results
 *
 * @return [StartCause] describing the cause of the wakeup.
 */
private fun pumpOnce(
    displaySeg: MemorySegment,
    displayFd: Int,
    wakeup: PosixWakeup,
    timeoutMs: Int,
    eventLoop: WaylandEventLoop,
): StartCause {
    val readiness = pumpWaylandOnce(
        operations = NativeWaylandPumpOperations(displaySeg),
        poller = NativeWaylandPoller,
        wakeup = wakeup,
        displayFd = displayFd,
        timeoutMs = timeoutMs,
    )
    val displayReady = readiness.displayReadable
    val wakeReady = readiness.wakeReadable

    // ── Determine the StartCause ──────────────────────────────────────────────
    return when {
        wakeReady -> StartCause.WaitCancelled()
        displayReady -> StartCause.Poll
        else -> when (val cf = eventLoop.controlFlow) {
            is ControlFlow.WaitUntil -> {
                val now = System.currentTimeMillis()
                if (now >= cf.instant) StartCause.ResumeTimeReached(cf.instant, now)
                else StartCause.Poll
            }
            else -> StartCause.Poll
        }
    }
}

/** Closes the connection and propagates native cleanup failures to the cleanup aggregator. */
internal fun disconnectWaylandDisplay(
    displaySeg: MemorySegment,
    disconnect: (MemorySegment) -> Unit = { display ->
        checkNotNull(wlDisplayDisconnect) { "wl_display_disconnect not available" }
            .invokeExact(display)
    },
) = disconnect(displaySeg)
