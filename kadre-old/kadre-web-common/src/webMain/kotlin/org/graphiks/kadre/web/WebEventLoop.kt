/**
 * Web event loop via requestAnimationFrame.
 *
 * ## Behavior per [ControlFlow] mode
 * - [ControlFlow.Wait]      : the loop waits for a DOM event before triggering a frame.
 *                             The next RAF is scheduled only when an event
 *                             arrives via [WebDomBridge.onWindowEvent].
 * - [ControlFlow.Poll]      : continuous RAF — a new frame is scheduled on each tick.
 * - [ControlFlow.WaitUntil] : setTimeout until the target instant, then a single RAF.
 *
 * ## webMain constraint
 * This file resides in `webMain` — NO DOM import is allowed here.
 * The actual RAF scheduling is delegated to the subclasses in
 * `jsMain` ([JsWebEventLoop]) and `wasmJsMain` ([WasmJsWebEventLoop]).
 *
 * ## Lifecycle
 * ```
 * runApp(handler)
 *   └─► handler.resumed(this)
 *   └─► handler.newEvents(this, StartCause.Init)
 *   └─► handler.canCreateSurfaces(this)
 *   └─► handler.aboutToWait(this)
 *   └─► BrowserScheduler.arm(controlFlow)
 *         └─► scheduled iteration
 *               ├─ handler.newEvents(exact stored cause)
 *               ├─ dispatch of the accumulated DOM events
 *               ├─ handler.aboutToWait(this)
 *               └─ BrowserScheduler.arm(controlFlow)  ← if !isExiting
 * ```
 *
 * @since 1.0.0
 */
package org.graphiks.kadre.web

import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.CursorImage
import org.graphiks.kadre.core.CustomCursor
import org.graphiks.kadre.core.DeviceEvents
import org.graphiks.kadre.core.EventLoopProxy
import org.graphiks.kadre.core.MonitorHandle
import org.graphiks.kadre.core.OwnedDisplayHandle
import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.core.RawDisplayHandle
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.Theme
import org.graphiks.kadre.core.VideoMode
import org.graphiks.kadre.core.Window
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId

/**
 * Web event loop shared between the JS and wasmJs targets.
 *
 * [JsWebEventLoop] and [WasmJsWebEventLoop] inject five target-specific browser
 * operations. Scheduling policy, deadline epoch sampling and cancellation live
 * entirely in the shared [BrowserScheduler]. RAF-relative timestamps never
 * enter this common event loop.
 *
 * ## Thread safety
 * JavaScript is single-threaded; the calls from `wakeUp()` are synchronous.
 */
open class WebEventLoop internal constructor(
    private val schedulingApi: BrowserSchedulingApi?,
) : ActiveEventLoop {

    /** Public compatibility constructor retained for source and binary callers. */
    constructor() : this(null)

    private sealed interface PendingWebEvent {
        data class Window(
            val window: WebWindow,
            val event: WindowEvent,
            val terminal: Boolean = false,
        ) : PendingWebEvent

        data class Metrics(
            val window: WebWindow,
            val transaction: WebMetricsTransaction,
        ) : PendingWebEvent
    }

    // -------------------------------------------------------------------------
    // Internal state
    // -------------------------------------------------------------------------

    private var _controlFlow: ControlFlow = ControlFlow.Wait
    private var _isExiting = false
    private var handler: ApplicationHandler? = null
    private var suspendedDelivered = false
    private var schedulerPausedAfterLastWindowClose = false
    private val scheduler = schedulingApi?.let { api ->
        BrowserScheduler(api, ::runScheduledIteration)
    }

    /** Current polling strategy (defaults to [PollStrategy.IdleCallback]). */
    internal var pollStrategy: PollStrategy = PollStrategy.IdleCallback

    /** Current wait-until strategy (defaults to [WaitUntilStrategy.Scheduler]). */
    internal var waitUntilStrategy: WaitUntilStrategy = WaitUntilStrategy.Scheduler

    /** List of active windows created by this loop. */
    private val windows = mutableListOf<WebWindow>()

    /** Primary DOM bridge (the first one created); used for system-level queries. */
    private var primaryBridge: WebDomBridge? = null

    /** Next internal window id. Canvas ids are DOM handles, not identity. */
    private var nextWindowId: Long = 1L

    /** Queue of DOM events received between two frames. */
    private val pendingEvents = mutableListOf<PendingWebEvent>()

    /** Windows whose redraw request is queued or awaiting snapshot consumption. */
    private val queuedRedrawWindows = mutableSetOf<WebWindow>()

    // -------------------------------------------------------------------------
    // ActiveEventLoop
    // -------------------------------------------------------------------------

    override val controlFlow: ControlFlow get() = _controlFlow

    override fun setControlFlow(controlFlow: ControlFlow) {
        _controlFlow = controlFlow
    }

    override val isExiting: Boolean get() = _isExiting

    override fun exit() {
        if (_isExiting) return
        _isExiting = true
        scheduler?.cancel()
    }

    /**
     * Creates a web window from the core [WindowAttributes] contract.
     *
     * **Legacy**: uses `attributes.title` as the canvas CSS `id` (a non-idiomatic
     * convention, kept for backward compatibility). Prefer the
     * `createWindow(WebWindowAttributes)` overload, which explicitly exposes the canvas id
     * and the auto-creation mode (equivalent of winit's `WindowAttributesExtWebSys`
     * trait).
     */
    override fun createWindow(attributes: WindowAttributes): Window {
        return createWindow(
            WebWindowAttributes(
                canvasId = attributes.title.ifEmpty { null },
                core = attributes,
            )
        )
    }

    /**
     * Creates a web window from the web-only extension [WebWindowAttributes].
     *
     * Inspired by winit's `WindowAttributesExtWebSys` trait:
     *
     *  - Targets an existing DOM `<canvas>` via [WebWindowAttributes.canvasId], OR
     *  - Lets Kadre create a `<canvas>` ([WebWindowAttributes.appendToBody] = true).
     *
     * The DOM bridge is instantiated by [createDomBridge] then:
     *  1. [WebDomBridge.ensureCanvas] resolves (or creates) the canvas.
     *  2. [WebDomBridge.attach] wires the DOM listeners (keydown, pointer, resize…).
     *
     * @param attrs Web-specific configuration of the window.
     * @return The created and attached [WebWindow].
     */
    fun createWindow(attrs: WebWindowAttributes): Window {
        val bridge = createDomBridge()
        if (primaryBridge == null) {
            installPrimaryBridge(bridge)
        }
        val canvasId = bridge.ensureCanvas(attrs)
        val window = WebWindow(
            id = WindowId(nextWindowId++),
            canvasElementId = canvasId,
            bridge = bridge,
        )
        window.closeHandler = ::closeWindow

        // Initialise synchronously from the current DOM state (before any event fires).
        val (initW, initH) = bridge.readCanvasPhysicalSize(canvasId)
        window.updatePhysicalSize(initW, initH)
        window.updateScaleFactor(bridge.readDevicePixelRatio())

        val wasPausedAfterLastClose = schedulerPausedAfterLastWindowClose
        schedulerPausedAfterLastWindowClose = false
        windows.add(window)
        installWindowBridge(window)
        bridge.attach(canvasId)
        if (wasPausedAfterLastClose) {
            signalScheduling()
        }
        return window
    }

    /**
     * Creates a thread-safe proxy to this event loop.
     *
     * In JavaScript (single-threaded), the proxy simply calls [scheduleWakeUp].
     */
    override fun createProxy(): EventLoopProxy = object : EventLoopProxy {
        override fun wakeUp() = signalScheduling()
    }

    // ── Task 14: ownedDisplayHandle ──────────────────────────────────────────

    /**
     * Returns an [OwnedDisplayHandle] wrapping [RawDisplayHandle.Web].
     */
    override fun ownedDisplayHandle(): OwnedDisplayHandle? {
        return OwnedDisplayHandle(RawDisplayHandle.Web)
    }

    // ── R2: monitor enumeration ───────────────────────────────────────────────

    /**
     * Returns a synthetic monitor representing the browser window.
     *
     * Uses the current canvas size and device pixel ratio from the first window.
     * The Fullscreen API cannot expose physical monitor properties.
     */
    override fun availableMonitors(): List<MonitorHandle> {
        val win = windows.firstOrNull()
        val scale = win?._scaleFactor ?: 1.0
        val size = win?._physicalSize ?: PhysicalSize(1920, 1080)
        return listOf(syntheticWebMonitor(scale, size))
    }

    /**
     * Returns the single synthetic web monitor.
     */
    override fun primaryMonitor(): MonitorHandle? = availableMonitors().firstOrNull()

    // ── R4: device event filter ───────────────────────────────────────────────

    /**
     * No-op on Web: device events are not emitted (no raw input API in the browser).
     */
    override fun listenDeviceEvents(mode: DeviceEvents) {
        // no-op on Web: raw device events are not dispatched
    }

    // ── R5-CustomCursor ─────────────────────────────────────────────────────────

    /**
     * Creates a custom cursor from RGBA pixel data on the Web.
     *
     * Delegates to [WebDomBridge.createCursorDataUrl] to produce a data URL,
     * caches it by sequential id in [WebCustomCursorCache], and returns
     * a [CustomCursor] with that id.
     */
    override fun createCustomCursor(image: CursorImage): CustomCursor? {
        val bridge = primaryBridge ?: return null
        val dataUrl = bridge.createCursorDataUrl(
            rgba = image.rgba,
            width = image.width,
            height = image.height,
            hotspotX = image.hotspotX,
            hotspotY = image.hotspotY,
        )
        if (dataUrl.isEmpty()) return null
        return WebCustomCursorCache.register(dataUrl)
    }

    // ── R3: system theme ──────────────────────────────────────────────────────

    /**
     * Returns the current system theme via `prefers-color-scheme`.
     *
     * Delegates to [WebDomBridge.prefersDarkColorScheme] on the primary bridge.
     * Returns null if no window has been created yet.
     */
    override fun systemTheme(): Theme? {
        val b = primaryBridge ?: return null
        return if (b.prefersDarkColorScheme()) Theme.Dark else Theme.Light
    }

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------



    /**
     * Starts the event loop and notifies the handler.
     *
     * Calls [ApplicationHandler.resumed], then [ApplicationHandler.newEvents]
     * with [StartCause.Init], then [ApplicationHandler.canCreateSurfaces]
     * (the browser allows surface creation right from startup), then
     * [ApplicationHandler.aboutToWait], and finally arms the shared browser
     * scheduler.
     *
     * @param handler Handler for the application's lifecycle.
     */
    open fun runApp(handler: ApplicationHandler) {
        this.handler = handler
        handler.resumed(this)
        handler.newEvents(this, StartCause.Init)
        // On the web, the canvas is available immediately: we allow surface
        // creation right away (parity with the AppKit/Win32 desktop loops).
        handler.canCreateSurfaces(this)
        handler.aboutToWait(this)
        if (
            !_isExiting &&
            (!schedulerPausedAfterLastWindowClose || pendingEvents.isNotEmpty())
        ) {
            if (scheduler != null) {
                scheduler.arm(_controlFlow, pendingEvents.isNotEmpty())
            } else {
                scheduleNextFrame(handler)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal tick — called by RAF
    // -------------------------------------------------------------------------

    /**
     * Runs one iteration of the event loop.
     *
     * Compatibility hook for subclasses without an injected browser API.
     * Target loops use [BrowserScheduler], whose RAF callback carries no timestamp.
     *
     * @param handler Lifecycle handler.
     * @param now Deprecated RAF-relative timestamp retained only for compatibility;
     *            it is deliberately ignored.
     */
    protected fun tick(
        handler: ApplicationHandler,
        @Suppress("UNUSED_PARAMETER") now: Double = 0.0,
    ) {
        this.handler = handler
        val cause = when (val controlFlow = _controlFlow) {
            ControlFlow.Poll -> StartCause.Poll
            ControlFlow.Wait -> StartCause.WaitCancelled()
            is ControlFlow.WaitUntil -> StartCause.ResumeTimeReached(
                requestedResume = controlFlow.instant,
                start = schedulingApi?.epochNowMillis() ?: controlFlow.instant,
            )
        }
        runScheduledIteration(cause)
    }

    private fun runScheduledIteration(cause: StartCause) {
        if (_isExiting) return
        val currentHandler = handler ?: return
        currentHandler.newEvents(this, cause)

        // Dispatch the accumulated DOM events
        val snapshot = pendingEvents.toList()
        pendingEvents.clear()
        for (pending in snapshot) {
            when (pending) {
                is PendingWebEvent.Window -> {
                    if (pending.window.isClosed && !pending.terminal) continue
                    if (pending.event == WindowEvent.RedrawRequested) {
                        queuedRedrawWindows.remove(pending.window)
                    }
                    when (val event = pending.event) {
                        is WindowEvent.Resized ->
                            pending.window.updatePhysicalSize(event.size.width, event.size.height)
                        is WindowEvent.ScaleFactorChanged ->
                            pending.window.updateScaleFactor(event.factor)
                        else -> Unit
                    }
                    currentHandler.windowEvent(this, pending.window.id, pending.event)
                }
                is PendingWebEvent.Metrics -> {
                    val window = pending.window
                    if (window.isClosed) continue
                    val metrics = pending.transaction
                    window.updateScaleFactor(metrics.scaleFactor)
                    window.updatePhysicalSize(metrics.physicalSize.width, metrics.physicalSize.height)
                    currentHandler.windowEvent(
                        this,
                        window.id,
                        WebWindowEvent.ScaleFactorChanged(metrics.scaleFactor).toWindowEvent(),
                    )
                    if (!window.isClosed) {
                        currentHandler.windowEvent(
                            this,
                            window.id,
                            WebWindowEvent.Resized(
                                metrics.physicalSize.width,
                                metrics.physicalSize.height,
                            ).toWindowEvent(),
                        )
                    }
                }
            }
        }

        currentHandler.aboutToWait(this)
        // Schedule the next frame according to the current mode
        if (
            !_isExiting &&
            (!schedulerPausedAfterLastWindowClose || pendingEvents.isNotEmpty())
        ) {
            if (scheduler != null) {
                scheduler.arm(_controlFlow, pendingEvents.isNotEmpty())
            } else {
                scheduleNextFrame(currentHandler)
            }
        } else if (_isExiting && !suspendedDelivered) {
            // Notify the handler of the imminent end
            suspendedDelivered = true
            currentHandler.suspended(this)
        }
    }

    private fun enqueueWindowEvent(window: WebWindow, event: WindowEvent) {
        if (window.isClosed || window !in windows) return
        if (event == WindowEvent.RedrawRequested) {
            if (!queuedRedrawWindows.add(window)) return
        }

        pendingEvents.add(PendingWebEvent.Window(window, event))
        signalScheduling()
    }

    private fun closeWindow(window: WebWindow) {
        if (!window.markClosed()) return
        if (!windows.remove(window)) return

        window.closeHandler = null
        val bridge = window.bridge
        val bridgeStillOwned = windows.any { it.bridge === bridge }
        if (!bridgeStillOwned) {
            bridge.onWindowEvent = null
        }
        val connection = window.metricsConnection
        window.metricsConnection = null
        val ownsDetachment = connection == null || WebMetricsTransactions.disconnect(connection)

        pendingEvents.removeAll { pending ->
            when (pending) {
                is PendingWebEvent.Window -> pending.window === window
                is PendingWebEvent.Metrics -> pending.window === window
            }
        }
        queuedRedrawWindows.remove(window)

        val wasPrimary = primaryBridge === bridge && !bridgeStillOwned
        if (wasPrimary) {
            bridge.onThemeChange = null
            primaryBridge = null
        }
        if (!bridgeStillOwned && ownsDetachment) {
            bridge.detach()
        } else if (bridgeStillOwned && ownsDetachment) {
            windows.lastOrNull { it.bridge === bridge }?.let(::installWindowBridge)
        }
        if (wasPrimary) {
            windows.firstOrNull()?.bridge?.let(::installPrimaryBridge)
        }

        try {
            val currentHandler = handler
            if (currentHandler != null) {
                currentHandler.windowEvent(this, window.id, WindowEvent.Destroyed)
            } else {
                pendingEvents.add(
                    PendingWebEvent.Window(
                        window = window,
                        event = WindowEvent.Destroyed,
                        terminal = true,
                    ),
                )
            }
        } finally {
            if (windows.isEmpty()) {
                schedulerPausedAfterLastWindowClose = true
                scheduler?.cancelPending()
            }
        }
    }

    private fun installPrimaryBridge(bridge: WebDomBridge) {
        primaryBridge = bridge
        bridge.onThemeChange = { dark ->
            val theme = if (dark) Theme.Dark else Theme.Light
            for (window in windows.toList()) {
                enqueueWindowEvent(window, WindowEvent.ThemeChanged(theme))
            }
        }
    }

    private fun installWindowBridge(window: WebWindow) {
        val bridge = window.bridge
        bridge.onWindowEvent = callback@{ event ->
            if (window.isClosed || window !in windows) return@callback
            if (event == WebWindowEvent.Destroyed) {
                closeWindow(window)
                return@callback
            }
            enqueueWindowEvent(window, event.toWindowEvent())
        }
        window.metricsConnection = WebMetricsTransactions.connect(bridge) { transaction ->
            if (window.isClosed || window !in windows) return@connect
            pendingEvents.add(PendingWebEvent.Metrics(window, transaction))
            signalScheduling()
        }
    }

    private fun signalScheduling() {
        if (_isExiting || handler == null) return
        if (scheduler != null) {
            scheduler.signalEvent(_controlFlow)
        } else {
            scheduleWakeUp()
        }
    }

    // -------------------------------------------------------------------------
    // Methods extensible by subclasses
    // -------------------------------------------------------------------------

    /**
     * Schedules the next frame according to the current [ControlFlow].
     *
     * - [ControlFlow.Poll]      → immediate `requestAnimationFrame`
     * - [ControlFlow.Wait]      → waits for a DOM event ([scheduleWakeUp] will schedule the RAF)
     * - [ControlFlow.WaitUntil] → `setTimeout` until the target instant, then RAF
     *
     * New target loops inject [BrowserSchedulingApi] instead. This method remains
     * as a source-compatible hook for existing subclasses built around the public
     * zero-argument constructor.
     *
     * This method is `open` to allow overriding in tests.
     */
    protected open fun scheduleNextFrame(handler: ApplicationHandler) {
        // Stub: the concrete implementation is provided by JsWebEventLoop / WasmJsWebEventLoop
        // via requestAnimationFrame in jsMain / wasmJsMain.
    }

    /**
     * Schedules an immediate wake-up of the loop (single RAF).
     *
     * Used in [ControlFlow.Wait] mode when a DOM event arrives,
     * and by [createProxy] to wake the loop from another context.
     *
     * New target loops inject [BrowserSchedulingApi] instead. This compatibility
     * hook is used only by subclasses built around the zero-argument constructor.
     */
    protected open fun scheduleWakeUp() {
        // Stub: overridden in JsWebEventLoop / WasmJsWebEventLoop
    }

    /**
     * Creates the DOM bridge appropriate to the compilation target.
     *
     * Overridden in [JsWebEventLoop] to return [JsWebDomBridge],
     * and in [WasmJsWebEventLoop] to return [WasmJsWebDomBridge].
     *
     * The default implementation returns a no-op bridge (useful for tests).
     */
    protected open fun createDomBridge(): WebDomBridge = object : WebDomBridge {
        override var onWindowEvent: ((WebWindowEvent) -> Unit)? = null
        override fun attach(targetElementId: String) {}
        override fun detach() {}
    }
}

/**
 * Thread-safe cache of CSS cursor data URLs keyed by sequential [Long] ids.
 *
 * Used by [WebEventLoop.createCustomCursor] and [WebWindow.setCustomCursor]
 * to decouple cursor creation from cursor application across the DOM bridge.
 */
internal object WebCustomCursorCache {

    private var nextId: Long = 0L
    private val map = mutableMapOf<Long, String>()

    /**
     * Registers [dataUrl] and returns a [CustomCursor] with a new unique id.
     */
    fun register(dataUrl: String): CustomCursor {
        val id = nextId++
        map[id] = dataUrl
        return CustomCursor(id)
    }

    /**
     * Resolves [cursorId] to its cached data URL, or null if unknown.
     */
    fun resolve(cursorId: Long): String? = map[cursorId]
}

/** Creates a synthetic [MonitorHandle] representing the browser window. */
internal fun syntheticWebMonitor(scale: Double, size: PhysicalSize<Int>): MonitorHandle =
    object : MonitorHandle {
        override val id: Long = 0L
        override val name: String? = null
        override val position: PhysicalPosition<Int> = PhysicalPosition(0, 0)
        override val scaleFactor: Double = scale
        override val currentVideoMode: VideoMode = VideoMode(size, null, null)
        override val videoModes: List<VideoMode> = listOf(currentVideoMode)
    }
