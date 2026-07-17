/**
 * WaylandEventLoop — Wayland event loop for kadre.
 *
 * Implements the canonical Wayland prepare_read / poll / read_events sequence
 * with an eventfd for inter-thread wakeup (wakeUp).
 *
 * Pump sequence:
 *  1. while (wl_display_prepare_read != 0) → wl_display_dispatch_pending
 *  2. wl_display_flush
 *  3. poll([displayFd, eventfdFd], timeout)
 *  4. If displayFd ready → wl_display_read_events + dispatch_pending
 *     Otherwise          → wl_display_cancel_read
 *  5. If eventfdFd ready → read(eventfd) to drain the counter
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

    /**
     * Window events produced by native upcalls (xdg configure/close), queued here and drained
     * into [ApplicationHandler.windowEvent] from the loop thread after each pump.
     */
    internal val eventQueue = java.util.concurrent.ConcurrentLinkedQueue<Pair<WindowId, WindowEvent>>()

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
        ) ?: error("WaylandWindow.create failed — libwayland-client.so.0 absent or display invalid")
        // Route this window's compositor-driven events into the loop's queue for dispatch.
        window.onWindowEvent = { event -> eventQueue.add(window.id to event) }
        window.registryOwner = _globals?.registryOwner
        windows[window.id.value] = window
        // Initial paint so the surface attaches a buffer and becomes visible. Subsequent repaints
        // are driven on demand (e.g. after a resize), not continuously — see the main loop.
        eventQueue.add(window.id to org.graphiks.kadre.core.WindowEvent.RedrawRequested)
        return window
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
        ) ?: error("WaylandWindow.create failed — libwayland-client.so.0 absent")
        window.onWindowEvent = { event -> eventQueue.add(window.id to event) }
        window.registryOwner = _globals?.registryOwner
        windows[window.id.value] = window
        // Apply platform extension settings
        attrs.preferCsd?.let { window.setPreferCsd(it) }
        attrs.activationToken?.let { window.setActivationToken(it) }
        attrs.name?.let { name -> window.setAppId(name) }
        eventQueue.add(window.id to org.graphiks.kadre.core.WindowEvent.RedrawRequested)
        return window
    }

    /**
     * Creates a thread-safe proxy to this event loop.
     *
     * The proxy uses the eventfd to wake up the loop from any thread.
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
     * startup), falls back to a synthetic monitor.
     */
    override fun availableMonitors(): List<MonitorHandle> {
        val realOutputs = _globals?.registryOwner?.outputs?.map { it.info.toMonitorHandle() }.orEmpty()
        if (realOutputs.isNotEmpty()) return realOutputs

        // Fallback: synthetic monitor from first window
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
                eventQueue.add(win.id to WindowEvent.ThemeChanged(newTheme))
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
 * Blocking: only returns when the loop ends (via [ActiveEventLoop.exit]
 * or when all windows are closed).
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

// ── Internal implementation ───────────────────────────────────────────────────

private fun runAppInternal(handler: ApplicationHandler) {
    // ── 1. Connect to the Wayland server ──────────────────────────────────────
    if (waylandNativeDisabled()) {
        error("Wayland native access disabled via KADRE_WAYLAND_DISABLE_NATIVE")
    }
    val connectHandle = wlDisplayConnect
        ?: error("wl_display_connect not available — libwayland-client.so.0 missing")

    val displaySeg: MemorySegment = Arena.ofConfined().use { arena ->
        val nullSeg = MemorySegment.NULL
        try {
            connectHandle.invokeExact(nullSeg) as MemorySegment
        } catch (t: Throwable) {
            error("wl_display_connect threw an exception: $t")
        }
    }

    if (displaySeg == MemorySegment.NULL || displaySeg.address() == 0L) {
        error("wl_display_connect returned NULL — Wayland server not available (WAYLAND_DISPLAY ?)")
    }

    val displayPtr = displaySeg.address()

    // ── 2. Wayland socket file descriptor ─────────────────────────────────────
    val displayFd: Int = try {
        val fdHandle = wlDisplayGetFd
            ?: error("wl_display_get_fd not available")
        fdHandle.invokeExact(displaySeg) as Int
    } catch (t: Throwable) {
        // Clean disconnect before propagating
        disconnectDisplay(displaySeg)
        throw t
    }

    // ── 3. Create the portable POSIX wake owner ───────────────────────────────
    val wakeup = try {
        PosixWakeup.open()
    } catch (t: Throwable) {
        disconnectDisplay(displaySeg)
        throw t
    }

    var seatBinding: WaylandSeatBinding? = null
    var registryOwner: WaylandRegistryOwner? = null
    val nativeListenerLifetime = WaylandNativeListenerLifetime()
    preservingWaylandCleanup(
        cleanupActions = listOf(
            { registryOwner?.close(); Unit },
            { closeWaylandResources(wakeup) { disconnectDisplay(displaySeg) } },
            nativeListenerLifetime::closeAfterDisplayDisconnect,
            {
                val binding = seatBinding
                if (binding != null) binding.close()
            },
        ),
    ) {
        // ── 4. Discover Wayland globals (compositor, seat, output) ───────────
        // get_registry + listener(global) + roundtrip + bind(wl_compositor, wl_seat, wl_output, …).
        val globals = discoverGlobals(displayPtr, nativeListenerLifetime = nativeListenerLifetime)
        registryOwner = globals.registryOwner

        val eventLoop = WaylandEventLoop(
            displayPtr, globals.compositorPtr, globals.xdgWmBasePtr, globals.shmPtr, wakeup,
            globals.decorationManagerPtr, globals.pointerConstraintsPtr, globals.iconManagerPtr,
            globals.activationManagerPtr, globals.seatPtr,
            globals.extBackgroundEffectManagerPtr, globals.kwinBlurManagerPtr,
            nativeListenerLifetime,
        ).also { it._globals = globals }
        globals.registryOwner?.routeNativeFailuresTo(eventLoop::queueNativeFailure)

        // ── 4b. Install seat / output listeners (keyboard, pointer, touch, scale) ─
        // Route all input events into the eventQueue by their source wl_surface.
        // The seat and output globals may be absent (0) — installSeatListeners tolerates that.
        // DeviceEvent.Key is dispatched directly to the handler for raw key events.
        seatBinding = installSeatListeners(
            displayPtr    = displayPtr,
            seatPtr       = globals.seatPtr,
            seatVersion   = globals.seatVersion,
            onEvent = { surfacePtr, event ->
                routeWaylandInputEvent(surfacePtr, event, eventLoop.windows, eventLoop.eventQueue)
            },
            onDeviceEvent = { event ->
                handler.deviceEvent(eventLoop, DeviceId(0L), event)
            },
            dataDeviceManagerPtr = globals.dataDeviceManagerPtr,
            deviceFilter = eventLoop.liveDeviceFilter,
            onNativeFailure = eventLoop::queueNativeFailure,
        )
        eventLoop.throwPendingNativeFailure()

        // ── 4c. Create zwp_text_input_v3 for IME (if compositor exposes the protocol) ──
        if (globals.textInputManagerPtr != 0L && globals.seatPtr != 0L) {
            createTextInput(
                managerPtr = globals.textInputManagerPtr,
                display = displayPtr,
                onEvent = { surfacePtr, event ->
                    routeWaylandInputEvent(surfacePtr, event, eventLoop.windows, eventLoop.eventQueue)
                },
            )
        }

        // ── 5. Lifecycle: resumed ─────────────────────────────────────────────
        handler.resumed(eventLoop)

        // ── 6. First newEvents (Init) ─────────────────────────────────────────
        handler.newEvents(eventLoop, StartCause.Init)

        // ── 7. canCreateSurfaces ──────────────────────────────────────────────
        handler.canCreateSurfaces(eventLoop)

        // ── 8. Main loop ──────────────────────────────────────────────────────
        while (!eventLoop.isExiting) {
            // aboutToWait — the handler can change controlFlow here
            handler.aboutToWait(eventLoop)

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

            // Drain queued window events (initial/resize RedrawRequested, xdg configure/close)
            // into the handler. Rendering happens here, on demand — NOT on a continuous pump:
            // eglSwapBuffers blocks the loop thread (no frame-callback pacing yet), so a steady
            // pump deadlocks the loop. On-demand keeps the loop responsive to resize/close.
            while (true) {
                val (windowId, event) = eventLoop.eventQueue.poll() ?: break
                handler.windowEvent(eventLoop, windowId, event)
            }

            handler.newEvents(eventLoop, startCause)
        }

        // ── 9. Shutdown ───────────────────────────────────────────────────────
        handler.destroySurfaces(eventLoop)
        handler.suspended(eventLoop)
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
) {
    // Stop every proxy before invalidating the display they wake.
    runWaylandCleanup(
        primary = null,
        cleanupActions = listOf(wakeup::close, disconnectDisplay),
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

/** Closes the connection to the Wayland server cleanly. */
private fun disconnectDisplay(displaySeg: MemorySegment) {
    try {
        wlDisplayDisconnect?.invokeExact(displaySeg)
    } catch (_: Throwable) {}
}
