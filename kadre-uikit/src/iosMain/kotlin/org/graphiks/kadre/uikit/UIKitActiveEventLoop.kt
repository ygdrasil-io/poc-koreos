package org.graphiks.kadre.uikit

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import org.graphiks.kadre.core.*
import platform.UIKit.UIScreen


/**
 * UIKit ActiveEventLoop — lightweight proxy to ApplicationHandler.
 *
 * On iOS, UIKit owns the event loop (UIApplicationMain).
 * This implementation exposes the ActiveEventLoop contract to the
 * KadreAppDelegate callbacks without duplicating the loop.
 */
internal class UIKitActiveEventLoop(internal val handler: ApplicationHandler) : ActiveEventLoop {

    private var _controlFlow: ControlFlow = ControlFlow.Wait
    private var _isExiting = false

    /** Windows created by this loop, used to scope app-level lifecycle events. */
    private val windows = mutableListOf<UiKitWindow>()

    /** Next live window to return while surfaces are being recreated. */
    private var recreationCursor: Int? = null

    /** Whether the current surface generation still needs destruction. */
    private var surfacesActive = false

    /** Last observed system theme, used to detect changes across app activation. */
    internal var lastTheme: Theme? = null

    override fun createWindow(attributes: WindowAttributes): Window {
        recreationCursor?.let { cursor ->
            windows.getOrNull(cursor)?.let { existing ->
                recreationCursor = cursor + 1
                existing.applyMutableAttributes(attributes)
                return existing
            }
            // Newly created windows never become reusable in the same session.
            recreationCursor = null
        }
        return UiKitWindow(attributes, this).also { windows.add(it) }
    }

    /**
     * Reuses live windows, in creation order, for one surface-creation callback.
     * Calls beyond the number of existing windows create and register new ones.
     */
    internal fun recreateSurfaces(block: UIKitActiveEventLoop.() -> Unit) {
        val previousCursor = recreationCursor
        recreationCursor = 0
        try {
            block()
            surfacesActive = true
        } finally {
            recreationCursor = previousCursor
        }
    }

    /** Destroys the current surface generation at most once. */
    internal fun destroySurfaces() {
        if (!surfacesActive) return
        surfacesActive = false
        handler.destroySurfaces(this)
    }

    fun createWindow(attrs: UiKitWindowAttributes): Window {
        val window = createWindow(attrs.core) as UiKitWindow
        attrs.scaleFactor?.let { /* scale override would be applied here */ }
        // validOrientations: Kotlin/Native bindings do not expose
        // UIViewController.supportedInterfaceOrientations override.
        // TODO: implement via UIViewController subclass if bindings are extended.
        if (attrs.prefersHomeIndicatorHidden) window.setPrefersHomeIndicatorHidden(true)
        if (attrs.prefersStatusBarHidden) window.setPrefersStatusBarHidden(true)
        attrs.preferredStatusBarStyle?.let { window.setPreferredStatusBarStyle(it) }
        if (attrs.recognizePinchGesture) window.recognizePinchGesture(true)
        if (attrs.recognizePanGesture) window.recognizePanGesture(true, 1, 2)
        if (attrs.recognizeDoubleTapGesture) window.recognizeDoubleTapGesture(true)
        if (attrs.recognizeRotationGesture) window.recognizeRotationGesture(true)
        return window
    }

    /**
     * Emits [WindowEvent.Focused] for every window.
     *
     * Driven by [KadreAppDelegate] from the app activation callbacks. This is the
     * per-window counterpart of the app-level [ApplicationHandler.resumed] /
     * [ApplicationHandler.suspended] lifecycle: on iOS, app focus *is* window
     * focus (single-window AppDelegate model), but both are emitted so consumers
     * that switch on [WindowEvent] (desktop/winit parity) also receive focus.
     */
    internal fun dispatchWindowFocused(gained: Boolean) {
        forEachLiveWindow {
            if (!gained) it.resetKeyboardModifiersIfNeeded()
            handler.windowEvent(this, it.id, WindowEvent.Focused(gained))
        }
    }

    /**
     * Emits [WindowEvent.Occluded] for every window.
     *
     * Driven by [KadreAppDelegate] from the app background/foreground callbacks.
     * On iOS, when the app enters the background it is fully occluded by another
     * app; when it returns to the foreground it is no longer occluded.
     */
    internal fun dispatchOccluded(occluded: Boolean) {
        val event = WindowEvent.Occluded(occluded)
        forEachLiveWindow { handler.windowEvent(this, it.id, event) }
    }

    /**
     * Emits [WindowEvent.Destroyed] for every window, then forgets them.
     *
     * Driven by [KadreAppDelegate] on termination — the per-window counterpart of
     * the app-level [ApplicationHandler.destroySurfaces].
     */
    internal fun dispatchWindowsDestroyed() {
        windows.toList().forEach { closeWindow(it.id) }
    }

    /** Removes a window from the live set before performing terminal cleanup. */
    internal fun closeWindow(id: WindowId): Boolean {
        val index = windows.indexOfFirst { it.id == id }
        if (index < 0) return false
        val window = windows.removeAt(index)
        recreationCursor = recreationCursor?.let { cursor ->
            if (index < cursor) cursor - 1 else cursor
        }
        try {
            window.invalidateResources()
        } finally {
            try {
                handler.windowEvent(this, id, WindowEvent.Destroyed)
            } finally {
                window.hideAndResign()
            }
        }
        return true
    }

    private inline fun forEachLiveWindow(block: (UiKitWindow) -> Unit) {
        windows.toList().forEach { window ->
            if (window in windows) block(window)
        }
    }

    override fun setControlFlow(controlFlow: ControlFlow) { _controlFlow = controlFlow }
    override val controlFlow: ControlFlow get() = _controlFlow
    override fun exit() { _isExiting = true }
    override val isExiting: Boolean get() = _isExiting

    override fun createProxy(): EventLoopProxy = UIKitEventLoopProxy(this)

    override fun ownedDisplayHandle(): OwnedDisplayHandle? =
        OwnedDisplayHandle(RawDisplayHandle.UiKit)

    // ── R2: monitor enumeration ───────────────────────────────────────────────

    /**
     * Returns a synthetic monitor based on UIScreen.mainScreen.
     *
     * iOS does not expose physical monitor information; we synthesize a single
     * monitor from the main screen's bounds and scale factor.
     */
    override fun availableMonitors(): List<MonitorHandle> = listOf(syntheticUiKitMonitor())

    /**
     * Returns the primary monitor (the single UIKit screen).
     */
    override fun primaryMonitor(): MonitorHandle? = syntheticUiKitMonitor()

    // ── R3: system theme ──────────────────────────────────────────────────────

    /**
     * Returns the current system theme via the first available window's trait collection.
     *
     * Uses the view controller's `traitCollection.userInterfaceStyle` (replacing
     * deprecated `UIScreen.mainScreen.traitCollection`):
     * - UIUserInterfaceStyleLight (1) → [Theme.Light]
     * - UIUserInterfaceStyleDark  (2) → [Theme.Dark]
     * - otherwise                     → null
     */
    @OptIn(ExperimentalForeignApi::class)
    override fun systemTheme(): Theme? = try {
        windows.firstOrNull()?.let { it.theme }
    } catch (_: Throwable) { null }

    /**
     * Dispatches [WindowEvent.ThemeChanged] to all windows if the current
     * system theme differs from the last cached value.
     *
     * Called by [KadreAppDelegate] on app activation to catch theme changes
     * that may have occurred while the app was in the background.
     */
    internal fun dispatchThemeChangedIfNeeded() {
        val current = systemTheme()
        if (current != null && current != lastTheme) {
            lastTheme = current
            forEachLiveWindow {
                handler.windowEvent(this, it.id, WindowEvent.ThemeChanged(current))
            }
        }
    }

    // ── R4: device event filter ───────────────────────────────────────────────

    /**
     * No-op on UIKit: device events are not dispatched on iOS.
     */
    override fun listenDeviceEvents(mode: DeviceEvents) {
        // no-op on UIKit
    }
}

/** Creates a synthetic monitor from UIScreen.mainScreen. */
@OptIn(ExperimentalForeignApi::class)
internal fun syntheticUiKitMonitor(): MonitorHandle {
    val screen = UIScreen.mainScreen
    val scale = screen.scale
    val (w, h) = screen.bounds.useContents {
        Pair(
            (size.width * scale).toInt(),
            (size.height * scale).toInt(),
        )
    }
    return object : MonitorHandle {
        override val id: Long = 0L
        override val name: String? = null
        override val position: PhysicalPosition<Int> = PhysicalPosition(0, 0)
        override val scaleFactor: Double = scale
        override val currentVideoMode: VideoMode = VideoMode(PhysicalSize(w, h), null, null)
        override val videoModes: List<VideoMode> = listOf(currentVideoMode)
    }
}
