package org.graphiks.kadre.android

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.Surface
import androidx.activity.ComponentActivity
import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ControlFlow
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
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android implementation of [ActiveEventLoop].
 *
 * ## Window lifecycle — "pending window" pattern
 *
 * On Android, the [android.view.Surface] is only available after the
 * [android.view.SurfaceHolder.Callback.surfaceCreated] callback; it can be released
 * and recreated (e.g. rotation, onPause/onResume). The "pending window" pattern
 * decouples the creation of the [AndroidWindow] object from the surface availability:
 *
 * 1. **[onSurfaceCreated]** — called by [KadreActivity] before
 *    [org.graphiks.kadre.core.ApplicationHandler.canCreateSurfaces]: remembers the current surface
 *    independently of whether a window exists yet.
 *
 * 2. **[createWindow]** — called from
 *    [org.graphiks.kadre.core.ApplicationHandler.canCreateSurfaces]: immediately creates an
 *    [AndroidWindow], attaches the remembered surface, and stores it in [pendingWindow].
 *
 * 3. **[onSurfaceDestroyed]** — called by [KadreActivity] on `surfaceDestroyed`:
 *    invalidates the surface via [AndroidWindow.onSurfaceReleased].
 *
 * ## Timing contract for [AndroidWindow.rawWindowHandle]
 *
 * [AndroidWindow.rawWindowHandle] throws [IllegalStateException] if no surface is active.
 * A window created from [ApplicationHandler.canCreateSurfaces] receives the current surface
 * before [createWindow] returns, so renderers can read the handle inside that callback.
 *
 * ## Frame scheduling
 *
 * Frame timing is handled by one main-thread [Handler] and [Choreographer].
 * [AndroidLoopState] owns wake and redraw coalescing; the Android adapter only
 * turns the next state-machine cause into one ordered application iteration.
 *
 * [exit] terminates the parent Activity via [ComponentActivity.finish].
 */
internal class AndroidEventLoop(
    internal val activity: ComponentActivity,
) : ActiveEventLoop {

    @Volatile
    override var controlFlow: ControlFlow = ControlFlow.Wait
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val choreographer = Choreographer.getInstance()
    private val state = AndroidLoopState(nowMillis = System::currentTimeMillis)
    private val windows = mutableMapOf<WindowId, AndroidWindow>()
    private val pendingWindowEvents = ArrayDeque<QueuedWindowEvent>()

    private var nextWindowId = 1L
    private var initialIterationPending = true
    private var inIteration = false

    private var frameCallbackScheduled = false
    private var scheduledStartCause: StartCause? = null

    private var waitGeneration = 0L
    private var armedWaitToken: Any? = null
    private val proxyWakeQueued = AtomicBoolean(false)
    private val surfaceDestroyedCallback = object : Runnable {
        override fun run() {
            onSurfaceDestroyedOnMain()
        }
    }

    /**
     * Current window created via [createWindow].
     *
     * Null before the first call to [createWindow], non-null afterwards.
     * The surface itself is only available after [onSurfaceCreated].
     */
    @Volatile
    internal var pendingWindow: AndroidWindow? = null
        private set

    private var currentSurface: Surface? = null

    /**
     * Creates an [AndroidWindow] bound to the Activity's [SurfaceView].
     *
     * Immediately returns an [AndroidWindow] with the current surface attached when one is
     * active ("pending window" pattern). [AndroidWindow.rawWindowHandle] is inaccessible only
     * when Android has not published a surface yet or has already destroyed it.
     *
     * May be called multiple times: each call releases the previous window's surface before
     * replacing the [pendingWindow] reference (rare case — a single window per Activity is
     * the norm).
     *
     * @param attributes Window attributes (title, size, etc.).
     *                   On Android, title and resizing are ignored.
     * @return An [AndroidWindow] whose surface matches the current Android lifecycle state.
     */
    override fun createWindow(attributes: WindowAttributes): Window = callOnMain {
        createWindowOnMain(attributes)
    }

    private fun createWindowOnMain(attributes: WindowAttributes): AndroidWindow {
        val kadreActivity = activity as KadreActivity
        check(nextWindowId < Long.MAX_VALUE) { "Android WindowId space exhausted" }
        val windowId = WindowId(nextWindowId++)
        val window = AndroidWindow(windowId, kadreActivity.surfaceView, this, kadreActivity)

        pendingWindow?.let { previousWindow ->
            state.close(previousWindow.id)
            windows.remove(previousWindow.id)
            previousWindow.onSurfaceReleased()
        }

        state.register(windowId)
        windows[windowId] = window
        return window.also {
            currentSurface?.let(window::onSurfaceAvailable)
            pendingWindow = window
        }
    }

    /**
     * Creates a window with Android-specific attributes.
     *
     * Merges [AndroidWindowAttributes] fields into the core [WindowAttributes]
     * and applies platform-specific settings at creation time.
     */
    internal fun createWindow(attrs: AndroidWindowAttributes): Window = callOnMain {
        val window = createWindowOnMain(attrs.core)
        window.handleVolumeKeys = attrs.handleVolumeKeys
        window
    }

    /**
     * Transfers the [android.view.Surface] to the pending window.
     *
     * Called by [KadreActivity] on `surfaceCreated`. After this call,
     * [AndroidWindow.rawWindowHandle] returns a valid
     * [org.graphiks.kadre.core.RawWindowHandle.Android]. If no window has yet been
     * created via [createWindow], the surface is retained and attached by the next
     * [createWindow] call.
     *
     * @param surface The Android surface freshly created by the SurfaceHolder.
     */
    internal fun onSurfaceCreated(surface: Surface) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onSurfaceCreatedOnMain(surface)
        } else {
            mainHandler.post(SurfaceCreatedCallback(this, surface))
        }
    }

    private fun onSurfaceCreatedOnMain(surface: Surface) {
        currentSurface = surface
        pendingWindow?.onSurfaceAvailable(surface)
    }

    /**
     * Invalidates the active window's surface.
     *
     * Called by [KadreActivity] on `surfaceDestroyed`. After this call,
     * [AndroidWindow.rawWindowHandle] throws [IllegalStateException] until the
     * next invocation of [onSurfaceCreated].
     */
    internal fun onSurfaceDestroyed() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onSurfaceDestroyedOnMain()
        } else {
            mainHandler.post(surfaceDestroyedCallback)
        }
    }

    private fun onSurfaceDestroyedOnMain() {
        pendingWindow?.onSurfaceReleased()
        currentSurface = null
    }

    override fun setControlFlow(controlFlow: ControlFlow) {
        runOnMain {
            this.controlFlow = controlFlow
            if (!inIteration) {
                armNextWait()
            }
        }
    }

    override fun exit() {
        runOnMain(activity::finish)
    }

    override val isExiting: Boolean
        get() = callOnMain { activity.isFinishing }

    override fun createProxy(): EventLoopProxy = object : EventLoopProxy {
        override fun wakeUp() {
            if (proxyWakeQueued.compareAndSet(false, true)) {
                val posted = mainHandler.post { signalProxyWake() }
                if (!posted) {
                    proxyWakeQueued.compareAndSet(true, false)
                }
            }
        }
    }

    // ── Task 18: ownedDisplayHandle ────────────────────────────────────────────

    /**
     * Returns an [OwnedDisplayHandle] wrapping [RawDisplayHandle.Android].
     */
    override fun ownedDisplayHandle(): OwnedDisplayHandle? {
        return OwnedDisplayHandle(RawDisplayHandle.Android)
    }

    // ── R2: monitor enumeration ───────────────────────────────────────────────

    /**
     * Returns a synthetic monitor based on the Android display metrics.
     *
     * Android exposes the physical screen size via DisplayMetrics. We use the
     * window manager's default display to build a single synthetic MonitorHandle.
     */
    override fun availableMonitors(): List<MonitorHandle> {
        return try {
            val dm = android.util.DisplayMetrics()
            (activity.getSystemService(android.content.Context.WINDOW_SERVICE)
                as android.view.WindowManager).defaultDisplay.getRealMetrics(dm)
            listOf(object : MonitorHandle {
                override val id: Long = 0L
                override val name: String? = null
                override val position: PhysicalPosition<Int> = PhysicalPosition(0, 0)
                override val scaleFactor: Double = dm.density.toDouble()
                override val currentVideoMode: VideoMode = VideoMode(
                    PhysicalSize(dm.widthPixels, dm.heightPixels), null,
                    dm.xdpi.toInt().let { if (it > 0) it else null }
                )
                override val videoModes: List<VideoMode> = listOf(currentVideoMode)
            })
        } catch (_: Throwable) { emptyList() }
    }

    /**
     * Returns the primary monitor (the single Android screen).
     */
    override fun primaryMonitor(): MonitorHandle? = availableMonitors().firstOrNull()

    // ── R3: system theme ──────────────────────────────────────────────────────

    /**
     * Returns the current system UI theme via UiModeManager.nightMode.
     *
     * - MODE_NIGHT_YES → [Theme.Dark]
     * - MODE_NIGHT_NO  → [Theme.Light]
     * - otherwise      → null (FOLLOW_SYSTEM or unknown)
     */
    override fun systemTheme(): Theme? = try {
        val uiModeManager = activity
            .getSystemService(android.content.Context.UI_MODE_SERVICE)
                as? android.app.UiModeManager
        when (uiModeManager?.nightMode) {
            android.app.UiModeManager.MODE_NIGHT_YES -> Theme.Dark
            android.app.UiModeManager.MODE_NIGHT_NO  -> Theme.Light
            else -> null
        }
    } catch (_: Throwable) { null }

    // ── R4: device event filter ───────────────────────────────────────────────

    /**
     * No-op on Android: raw device events are not dispatched at the Android level.
     */
    override fun listenDeviceEvents(mode: DeviceEvents) {
        // no-op on Android
    }

    /** Starts the initial iteration, or preserves an already pending state iteration. */
    internal fun scheduleFrameIfNeeded(window: AndroidWindow) {
        runOnMain {
            if (state.isOpen(window.id)) {
                scheduleStateIteration()
            }
        }
    }

    /** Queues a redraw and explicitly wakes any idle wait on the main Looper. */
    internal fun requestRedraw(windowId: WindowId) {
        runOnMain {
            if (state.requestRedraw(windowId)) {
                state.wakeUp()
                invalidateArmedWait()
                scheduleStateIteration()
            }
        }
    }

    /** Queues a platform window event for the next ordered loop iteration. */
    internal fun queueWindowEvent(windowId: WindowId, event: WindowEvent) {
        runOnMain {
            if (!state.isOpen(windowId)) return@runOnMain

            val firstPendingEvent = pendingWindowEvents.isEmpty()
            pendingWindowEvents.addLast(QueuedWindowEvent(windowId, event))
            if (firstPendingEvent) {
                state.wakeUp()
                invalidateArmedWait()
                scheduleStateIteration()
            }
        }
    }

    private fun signalWake() {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (state.wakeUp()) {
            invalidateArmedWait()
            scheduleStateIteration()
        }
    }

    private fun signalProxyWake() {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (proxyWakeQueued.compareAndSet(true, false)) {
            signalWake()
        }
    }

    private fun scheduleStateIteration(): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (frameCallbackScheduled || !isActivityActive()) return false

        val cause = if (initialIterationPending) {
            initialIterationPending = false
            StartCause.Init
        } else {
            state.takeStartCause(controlFlow) ?: return false
        }

        scheduledStartCause = cause
        frameCallbackScheduled = true
        choreographer.postFrameCallback {
            onFrame()
        }
        return true
    }

    private fun onFrame() {
        frameCallbackScheduled = false
        val cause = scheduledStartCause ?: return
        scheduledStartCause = null
        if (!isActivityActive()) return

        val kadreActivity = activity as KadreActivity
        if (cause == StartCause.Init) {
            // Init processes all work queued before the first frame. Consume the
            // coalesced wake at frame entry so proxy calls made before Init cannot
            // leak into a later empty WaitCancelled iteration even when their main
            // Handler callback is delayed behind Choreographer's sync barrier.
            proxyWakeQueued.set(false)
            state.takeStartCause(controlFlow)
        }
        inIteration = true
        try {
            kadreActivity.handler.newEvents(this, cause)
            takeWindowEvents().forEach { queuedEvent ->
                openWindow(queuedEvent.windowId)?.let {
                    kadreActivity.handler.windowEvent(
                        this,
                        queuedEvent.windowId,
                        queuedEvent.event,
                    )
                }
            }
            state.takeRedraws().forEach { windowId ->
                openWindow(windowId)?.let {
                    kadreActivity.handler.windowEvent(
                        this,
                        windowId,
                        WindowEvent.RedrawRequested,
                    )
                }
            }
            kadreActivity.handler.aboutToWait(this)
        } finally {
            inIteration = false
        }
        armNextWait()
    }

    private fun takeWindowEvents(): List<QueuedWindowEvent> {
        if (pendingWindowEvents.isEmpty()) return emptyList()
        return pendingWindowEvents.toList().also {
            pendingWindowEvents.clear()
        }
    }

    private fun armNextWait() {
        check(Looper.myLooper() == Looper.getMainLooper())
        invalidateArmedWait()
        if (!isActivityActive() || scheduleStateIteration()) return

        val waitUntil = controlFlow as? ControlFlow.WaitUntil ?: return
        val token = Any()
        val generation = waitGeneration
        armedWaitToken = token
        val delayMillis = (waitUntil.instant - System.currentTimeMillis()).coerceAtLeast(0L)
        val uptimeMillis = SystemClock.uptimeMillis() + delayMillis
        mainHandler.postAtTime(
            {
                if (generation != waitGeneration || armedWaitToken !== token) {
                    return@postAtTime
                }
                armedWaitToken = null
                if (!scheduleStateIteration()) {
                    armNextWait()
                }
            },
            token,
            uptimeMillis,
        )
    }

    private fun invalidateArmedWait() {
        waitGeneration += 1L
        armedWaitToken?.let { token ->
            mainHandler.removeCallbacksAndMessages(token)
        }
        armedWaitToken = null
    }

    private fun openWindow(windowId: WindowId): AndroidWindow? {
        if (!state.isOpen(windowId)) return null
        return windows[windowId]
    }

    private fun isActivityActive(): Boolean {
        return !activity.isDestroyed && !(activity as KadreActivity).destroyed
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun <T> callOnMain(action: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return action()
        return boundedMainHandoff(
            timeoutMillis = MAIN_HANDOFF_TIMEOUT_MILLIS,
            post = { task -> mainHandler.post(task) },
            action = action,
        )
    }

    private class SurfaceCreatedCallback(
        private val eventLoop: AndroidEventLoop,
        private val surface: Surface,
    ) : Runnable {
        override fun run() {
            eventLoop.onSurfaceCreatedOnMain(surface)
        }
    }

    private data class QueuedWindowEvent(
        val windowId: WindowId,
        val event: WindowEvent,
    )
}
