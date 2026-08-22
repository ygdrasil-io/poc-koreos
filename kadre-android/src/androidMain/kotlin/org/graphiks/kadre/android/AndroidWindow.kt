package org.graphiks.kadre.android

import android.content.Context
import android.view.SurfaceView
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import org.graphiks.kadre.core.CursorGrabMode
import org.graphiks.kadre.core.CursorIcon
import org.graphiks.kadre.core.CustomCursor
import org.graphiks.kadre.core.Fullscreen
import org.graphiks.kadre.core.Icon
import org.graphiks.kadre.core.ImeCapabilities
import org.graphiks.kadre.core.ImeCapability
import org.graphiks.kadre.core.ImePurpose
import org.graphiks.kadre.core.Insets
import org.graphiks.kadre.core.InputCapabilities
import org.graphiks.kadre.core.MonitorHandle
import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.core.RawDisplayHandle
import org.graphiks.kadre.core.RawWindowHandle
import org.graphiks.kadre.core.RequestError
import org.graphiks.kadre.core.ResizeDirection
import org.graphiks.kadre.core.Theme
import org.graphiks.kadre.core.UserAttentionType
import org.graphiks.kadre.core.VideoMode
import org.graphiks.kadre.core.Window
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowId
import org.graphiks.kadre.core.WindowLevel
import org.graphiks.kadre.core.WindowRequestResult
import kotlin.math.max

/**
 * Android implementation of [Window].
 *
 * Wraps a full-screen [SurfaceView] and exposes the raw [android.view.Surface]
 * via [rawWindowHandle] (Strategy A — zero JNI).
 *
 * ## Surface lifecycle
 *
 * An [AndroidWindow] may be created **before** the Android surface is
 * available ("pending window" pattern — see [AndroidEventLoop.createWindow]).
 * The availability cycle is:
 *
 * | Event | Surface state | [rawWindowHandle] |
 * |-----------|--------------|-------------------|
 * | After [AndroidEventLoop.createWindow] before `surfaceCreated` | Unavailable | Throws [IllegalStateException] |
 * | After [AndroidEventLoop.createWindow] during `canCreateSurfaces` | Available | Returns valid [RawWindowHandle.Android] |
 * | After [onSurfaceAvailable] | Available | Returns valid [RawWindowHandle.Android] |
 * | After [onSurfaceReleased] | Unavailable | Throws [IllegalStateException] |
 *
 * Renderers (wgpu4k, etc.) must only access [rawWindowHandle] within
 * or after the [org.graphiks.kadre.core.ApplicationHandler.canCreateSurfaces] callback,
 * and must release the handle before [org.graphiks.kadre.core.ApplicationHandler.destroySurfaces].
 */
class AndroidWindow internal constructor(
    override val id: WindowId,
    internal val surfaceView: KadreImeSurfaceView,
    private val eventLoop: AndroidEventLoop,
    private val activity: KadreActivity,
) : Window {

    @Volatile
    private var _surface: android.view.Surface? = null

    /**
     * Makes the surface available for rendering.
     *
     * Called by [AndroidEventLoop.onSurfaceCreated] (which is itself triggered
     * by [KadreActivity]) on `surfaceCreated`. After this call,
     * [rawWindowHandle] returns a valid [RawWindowHandle.Android].
     *
     * @param surface The Android surface freshly allocated by the SurfaceHolder.
     */
    internal fun onSurfaceAvailable(surface: android.view.Surface) {
        _surface = surface
    }

    /**
     * Returns the visible content rectangle excluding system decoration areas.
     *
     * Uses [android.view.View.getWindowVisibleDisplayFrame] to compute the
     * area not covered by the status bar, navigation bar, or cutouts.
     */
    internal fun contentRect(): android.graphics.Rect {
        val rect = android.graphics.Rect()
        surfaceView.getWindowVisibleDisplayFrame(rect)
        return rect
    }

    /**
     * Returns the current Android [android.content.res.Configuration] for the
     * window's context.
     *
     * Provides orientation, night mode, screen layout, font scale, etc.
     */
    internal fun config(): android.content.res.Configuration {
        return android.content.res.Configuration(surfaceView.context.resources.configuration)
    }

    /**
     * Invalidates the rendering surface.
     *
     * Called by [AndroidEventLoop.onSurfaceDestroyed] (which is itself triggered
     * by [KadreActivity]) on `surfaceDestroyed`. After this call,
     * [rawWindowHandle] throws [IllegalStateException] until the next
     * invocation of [onSurfaceAvailable].
     */
    internal fun onSurfaceReleased() {
        _surface = null
    }

    /**
     * Returns the native handle of the Android surface.
     *
     * @return [RawWindowHandle.Android] wrapping the active [android.view.Surface].
     * @throws IllegalStateException if the surface is not yet available
     *   (before [onSurfaceAvailable]) or has been released (after [onSurfaceReleased]).
     */
    override val rawWindowHandle: RawWindowHandle
        get() = RawWindowHandle.Android(
            surface = _surface
                ?: throw IllegalStateException(
                    "Android Surface not available: rawWindowHandle can only be called " +
                    "after surfaceCreated and before surfaceDestroyed. " +
                    "Wait for the ApplicationHandler.canCreateSurfaces callback."
                )
        )

    override val rawDisplayHandle: RawDisplayHandle
        get() = RawDisplayHandle.Android

    override fun inputCapabilities(): InputCapabilities =
        InputCapabilities(deviceIds = true, touch = true, touchForce = true)

    override fun imeCapabilities(): ImeCapabilities =
        ImeCapabilities(
            enabled = true,
            purposes = listOf(ImePurpose.Normal, ImePurpose.Password, ImePurpose.Terminal),
            capabilities = setOf(ImeCapability.Composition, ImeCapability.Learning, ImeCapability.Password),
        )

    @Volatile
    internal var handleVolumeKeys: Boolean = false

    override fun requestRedraw() {
        eventLoop.requestRedraw(id)
    }

    override val innerSize: PhysicalSize<Int>
        get() = PhysicalSize(surfaceView.width, surfaceView.height)

    override val outerSize: PhysicalSize<Int>
        get() = PhysicalSize(surfaceView.width, surfaceView.height)

    override val scaleFactor: Double
        get() = surfaceView.resources.displayMetrics.density.toDouble()

    override val safeArea: Insets<Int>
        get() {
            val decorView = activity.window?.decorView ?: return Insets(0, 0, 0, 0)
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val windowInsets = decorView.rootWindowInsets ?: return Insets(0, 0, 0, 0)
                val displayCutout = windowInsets.displayCutout
                val statusBarInsets = windowInsets.getInsets(android.view.WindowInsets.Type.statusBars())
                val navBarInsets = windowInsets.getInsets(android.view.WindowInsets.Type.navigationBars())
                Insets(
                    top = max(statusBarInsets.top, displayCutout?.safeInsetTop ?: 0),
                    bottom = max(navBarInsets.bottom, displayCutout?.safeInsetBottom ?: 0),
                    left = max(statusBarInsets.left, displayCutout?.safeInsetLeft ?: 0),
                    right = max(navBarInsets.right, displayCutout?.safeInsetRight ?: 0),
                )
            } else {
                val rect = android.graphics.Rect()
                decorView.getWindowVisibleDisplayFrame(rect)
                Insets(
                    top = rect.top,
                    bottom = decorView.height - rect.bottom,
                    left = rect.left,
                    right = decorView.width - rect.right,
                )
            }
        }

    override fun setVisible(visible: Boolean) {
        surfaceView.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
    }

    override fun close() {
        eventLoop.closeWindow(this)
    }

    // ── R5-IME: Input Method Editor ────────────────────────────────────────────

    @Volatile
    private var imeAllowed = false

    @Volatile
    private var currentImePurpose: ImePurpose = ImePurpose.Normal

    override fun setImeAllowed(allowed: Boolean) {
        imeAllowed = allowed
        val ctx = surfaceView.context
        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (allowed) {
            surfaceView.requestFocus()
            surfaceView.imeConnectionFactory = { editorInfo ->
                applyImePurposeToEditorInfo(currentImePurpose, editorInfo)
                KadreInputConnection(
                    dispatchEvent = { event ->
                        eventLoop.queueWindowEvent(id, event)
                    },
                    targetView = surfaceView,
                    editorInfo = editorInfo,
                )
            }
            imm.restartInput(surfaceView)
            imm.showSoftInput(surfaceView, InputMethodManager.SHOW_IMPLICIT)
        } else {
            surfaceView.imeConnectionFactory = null
            imm.hideSoftInputFromWindow(surfaceView.windowToken, 0)
        }
    }

    override fun setImeCursorArea(position: PhysicalPosition<Int>, size: PhysicalSize<Int>) {
        // The IME positions its candidate window relative to the focused view
        // automatically. No action required at this level.
    }

    override fun setImePurpose(purpose: ImePurpose) {
        currentImePurpose = purpose
        if (imeAllowed) {
            val imm = surfaceView.context
                .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.restartInput(surfaceView)
        }
    }

    private fun applyImePurposeToEditorInfo(purpose: ImePurpose, editorInfo: EditorInfo) {
        when (purpose) {
            ImePurpose.Normal -> {
                editorInfo.inputType = EditorInfo.TYPE_CLASS_TEXT
                editorInfo.imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION
            }
            ImePurpose.Password -> {
                editorInfo.inputType = EditorInfo.TYPE_CLASS_TEXT or
                    EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
                editorInfo.imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION
            }
            ImePurpose.Terminal -> {
                editorInfo.inputType = EditorInfo.TYPE_CLASS_TEXT or
                    EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                editorInfo.imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION
            }
        }
    }

    // ── R1: window state & geometry — no-ops on Android ───────────────────────
    //
    // Android does not support programmatic window resizing, minimization,
    // maximization, or decoration changes. The Activity lifecycle and the system
    // UI control these aspects. All members below are documented no-ops.

    /** Android windows always have the full-screen Activity title; tracked for getter parity. */
    @Volatile private var _title: String = ""

    /**
     * Sets the title. On Android this is a no-op at the window level; the Activity
     * title bar is managed via Activity.setTitle() outside kadre's scope.
     */
    override fun setTitle(title: String) { _title = title }

    override val title: String get() = _title

    /**
     * Android does not expose a reliable winit-style window visibility state.
     */
    override val isVisible: Boolean? get() = null

    /**
     * Android does not support programmatic resizing.
     * This is a no-op — the system controls the window geometry.
     */
    override fun setResizable(resizable: Boolean) { /* no-op: Android does not support programmatic resizing */ }

    /** Android windows are not user-resizable. Always returns false. */
    override val isResizable: Boolean get() = false

    /**
     * Android does not support programmatic minimization.
     * This is a no-op — use Activity.moveTaskToBack() if needed.
     */
    override fun setMinimized(minimized: Boolean) { /* no-op: Android does not support programmatic minimization */ }

    /** Android does not expose a reliable minimized state. */
    override val isMinimized: Boolean? get() = null

    /**
     * Android does not support programmatic maximization.
     * This is a no-op — the window always fills the available screen area.
     */
    override fun setMaximized(maximized: Boolean) { /* no-op: Android windows always fill the screen */ }

    /** Android windows always fill the screen. Always returns false (not a maximize concept). */
    override val isMaximized: Boolean get() = false

    /**
     * Android does not support platform window decorations in the traditional sense.
     * This is a no-op — the system UI (status bar, navigation bar) is controlled by the Activity.
     */
    override fun setDecorations(decorated: Boolean) { /* no-op: Android decorations are managed by the system UI */ }

    /** Android windows have no platform decorations (title bar / resize borders). Always returns false. */
    override val isDecorated: Boolean get() = false

    /**
     * Android does not support surface size constraints.
     * This is a no-op — the surface size is determined by the screen and Activity layout.
     */
    override fun setMinSurfaceSize(size: PhysicalSize<Int>?) { /* no-op: Android does not support surface size constraints */ }

    /**
     * Android does not support surface size constraints.
     * This is a no-op — the surface size is determined by the screen and Activity layout.
     */
    override fun setMaxSurfaceSize(size: PhysicalSize<Int>?) { /* no-op: Android does not support surface size constraints */ }

    /**
     * Android does not expose a global window position.
     * Returns PhysicalPosition(0, 0) as the window always fills the screen.
     */
    override val outerPosition: PhysicalPosition<Int> get() = PhysicalPosition(0, 0)

    /**
     * Android does not support programmatic window positioning.
     * This is a no-op — the window always fills the Activity area.
     */
    override fun setOuterPosition(position: PhysicalPosition<Int>) { /* no-op: Android does not support programmatic window positioning */ }

    /**
     * No-op on Android: there is no Wayland-style pre-commit concept on this platform.
     */
    override fun prePresentNotify() { /* no-op on Android */ }

    // ── R2: monitor & fullscreen ──────────────────────────────────────────────

    /**
     * Returns a synthetic monitor based on the Android display metrics.
     *
     * Uses the SurfaceView's display to read width/height/density.
     */
    override fun currentMonitor(): MonitorHandle? {
        return try {
            val dm = android.util.DisplayMetrics()
            surfaceView.display?.getRealMetrics(dm)
                ?: (surfaceView.context.getSystemService(android.content.Context.WINDOW_SERVICE)
                    as WindowManager).defaultDisplay.getRealMetrics(dm)
            object : MonitorHandle {
                override val id: Long = 0L
                override val name: String? = null
                override val position: PhysicalPosition<Int> = PhysicalPosition(0, 0)
                override val scaleFactor: Double = dm.density.toDouble()
                override val currentVideoMode: VideoMode = VideoMode(
                    size = PhysicalSize(dm.widthPixels, dm.heightPixels),
                    bitDepth = null,
                    refreshRateMilliHz = refreshRateMillihertz(
                        surfaceView.display?.refreshRate ?: 0f,
                    ),
                )
                override val videoModes: List<VideoMode> = listOf(currentVideoMode)
            }
        } catch (_: Throwable) { null }
    }

    /** In-memory fullscreen state (R2). */
    @Volatile private var _fullscreen: Fullscreen? = null

    override val fullscreen: Fullscreen? get() = _fullscreen

    // ── R3: cursor, theme & appearance — no-ops on Android ───────────────────

    /** No-op on Android: there is no visible cursor. */
    override fun setCursor(cursor: CursorIcon) { /* no-op: Android has no cursor */ }

    /** No-op on Android: there is no visible cursor. */
    override fun setCursorVisible(visible: Boolean) { /* no-op: Android has no cursor */ }

    /**
     * Sets the cursor grab mode for this window.
     *
     * **Platform note (Android):** Unsupported — touch-first platform with no system cursor.
     * Returns [WindowRequestResult.Failure] with [RequestError.Unsupported].
     */
    override fun setCursorGrab(mode: CursorGrabMode): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Android has no system cursor"))

    /**
     * Warps the cursor to the given position.
     *
     * **Platform note (Android):** Touch-first platform with no system cursor.
     * Cursor warping is unsupported; returns [WindowRequestResult.Failure] with [RequestError.Unsupported].
     */
    override fun setCursorPosition(position: PhysicalPosition<Int>): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Android has no cursor to warp"))

    /**
     * Enables or disables cursor hit-testing for this window.
     *
     * **Platform note (Android):** Unsupported — touch-first platform with no system cursor.
     * Returns [WindowRequestResult.Failure] with [RequestError.Unsupported].
     */
    override fun setCursorHittest(hittest: Boolean): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Android has no system cursor"))

    /**
     * Returns the current UI theme via UiModeManager.
     *
     * Reads [android.app.UiModeManager.nightMode]:
     * - MODE_NIGHT_YES → [Theme.Dark]
     * - MODE_NIGHT_NO  → [Theme.Light]
     * - otherwise      → null
     */
    override val theme: Theme?
        get() = try {
            val uiModeManager = surfaceView.context
                .getSystemService(android.content.Context.UI_MODE_SERVICE)
                    as? android.app.UiModeManager
            when (uiModeManager?.nightMode) {
                android.app.UiModeManager.MODE_NIGHT_YES -> Theme.Dark
                android.app.UiModeManager.MODE_NIGHT_NO  -> Theme.Light
                else -> null
            }
        } catch (_: Throwable) { null }

    /**
     * No-op on Android.
     *
     * Per-window theme override is not standard at the Window level;
     * it can be done at the Activity level via AppCompatDelegate.setDefaultNightMode().
     */
    override fun setTheme(theme: Theme?) { /* no-op: use AppCompatDelegate for Android theme */ }

    /** No-op on Android: Z-ordering is managed by the system. */
    override fun setWindowLevel(level: WindowLevel) { /* no-op: Android Z-ordering is system-managed */ }

    /** No-op on Android: transparency is managed by the renderer and Surface format. */
    override fun setTransparent(transparent: Boolean) { /* no-op: transparency requires SurfaceHolder.setFormat */ }

    /** No-op on Android: no standard blur API for SurfaceView. */
    override fun setBlur(blur: Boolean) { /* no-op: Android has no standard window blur API */ }

    /** No-op on Android: application icon is set via the manifest, not via Window. */
    override fun setWindowIcon(icon: Icon?) { /* no-op: Android icon is set via the app manifest */ }

    /**
     * Enters or exits immersive fullscreen on Android via WindowInsetsController.
     *
     * **Exclusive fullscreen is not supported on Android** — the immersive mode is
     * always borderless. [Fullscreen.Exclusive] is treated as [Fullscreen.Borderless].
     *
     * Requires API 30+ for [android.view.WindowInsetsController]. On older APIs this is
     * a documented no-op (the status/navigation bars remain visible).
     *
     * @param fullscreen New fullscreen state, or null to exit fullscreen.
     */
    override fun setFullscreen(fullscreen: Fullscreen?) {
        try {
            val activity = surfaceView.context
            if (activity is androidx.activity.ComponentActivity) {
                val window = activity.window ?: return
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val controller = window.insetsController ?: return
                    if (fullscreen != null) {
                        controller.hide(
                            android.view.WindowInsets.Type.statusBars() or
                            android.view.WindowInsets.Type.navigationBars()
                        )
                        controller.systemBarsBehavior =
                            android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else {
                        controller.show(
                            android.view.WindowInsets.Type.statusBars() or
                            android.view.WindowInsets.Type.navigationBars()
                        )
                    }
                }
                // For API < 30, fullscreen via SYSTEM_UI_FLAG_IMMERSIVE_STICKY is intentionally
                // omitted to avoid deprecation warnings — document as out of scope.
            }
        } catch (_: Throwable) {}
        _fullscreen = fullscreen
    }

    // ── R4: keyboard ──────────────────────────────────────────────────────────

    /**
     * No-op on Android: dead-key state is managed by the InputMethodManager.
     *
     * TODO(R4-android-dead-keys): call InputMethodManager.restartInput to reset IME.
     */
    override fun resetDeadKeys() {
        // no-op: Android IME state is managed by InputMethodManager
    }

    // ── R5-CustomCursor ───────────────────────────────────────────────────────

    /**
     * Applies a previously created custom cursor to this window.
     *
     * **Platform note (Android):** No-op — touch-first platform with no system cursor.
     */
    override fun setCustomCursor(cursor: CustomCursor) { /* no-op on Android */ }

    // ── R5-MiscWindow ─────────────────────────────────────────────────────────

    /**
     * Requests the platform to attract the user's attention (taskbar / dock icon).
     *
     * **Platform note (Android):** Documented no-op — Android does not have a
     * per-window user-attention API. Applications should use
     * [android.app.Notification] for user alerts.
     * Returns [WindowRequestResult.Success] to match local winit semantics.
     */
    override fun requestUserAttention(requestType: UserAttentionType?): WindowRequestResult =
        WindowRequestResult.Success

    /**
     * Enables or disables screen-capture protection for this window.
     *
     * **Platform note (Android):** Unsupported. Android's
     * [android.view.WindowManager.LayoutParams.FLAG_SECURE] disables screenshots
     * at the Activity level, not at the SurfaceView window level.
     */
    override fun setContentProtected(protected: Boolean): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Content protection is unsupported on Android"))

    /**
     * Shows the platform window menu (system / title-bar context menu) at the given position.
     *
     * **Platform note (Android):** Unsupported — Android has no system window menu concept.
     */
    override fun showWindowMenu(position: PhysicalPosition<Int>): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Window menu is unsupported on Android"))

    /**
     * Initiates a user-driven window drag from the current cursor position.
     *
     * **Platform note (Android):** Unsupported — Android has no windowing model
     * with user-draggable windows.
     */
    override fun dragWindow(): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Window dragging is unsupported on Android"))

    /**
     * Initiates a user-driven window resize from the current cursor position.
     *
     * **Platform note (Android):** Unsupported — Android windows are not user-resizable.
     */
    override fun dragResizeWindow(direction: ResizeDirection): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Window resizing is unsupported on Android"))
}
