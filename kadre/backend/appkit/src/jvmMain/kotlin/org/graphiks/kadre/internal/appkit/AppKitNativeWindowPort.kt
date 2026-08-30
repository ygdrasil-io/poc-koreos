package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.internal.runtime.RuntimeDesktopNativeWindowHandle
import org.graphiks.kadre.internal.runtime.SurfaceMetrics
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.window.WindowSpec

/**
 * AppKit-only native seam used to prepare and release one complete window peer.
 *
 * Implementations run [onMainThread] synchronously. Resource owners represent explicit native
 * ownership and must release idempotently; they do not expose native addresses outside this
 * backend package.
 */
internal interface AppKitNativeWindowPort {
    /** True only when the caller may execute AppKit operations without marshalling. */
    fun isMainThread(): Boolean

    fun <T> onMainThread(block: () -> T): T

    fun createWindow(spec: WindowSpec): AppKitNativeWindowOwner

    fun createContentView(spec: WindowSpec): AppKitNativeViewOwner

    fun createDelegate(
        peerId: AppKitWindowPeerId,
        callbacks: AppKitWindowDelegateCallbacks,
    ): AppKitNativeDelegateOwner

    fun attachContentView(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
    )

    /** A failure may occur after the native window has stored the delegate reference. */
    fun attachDelegate(
        window: AppKitNativeWindowOwner,
        delegate: AppKitNativeDelegateOwner,
    )

    fun present(window: AppKitNativeWindowOwner)

    /** Installs one peer-local observer after the complete native window has been presented. */
    fun observeSurface(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
        callbacks: AppKitSurfaceCallbacks,
    ): AppKitNativeSurfaceObserverOwner? = null

    /** Returns only when the native window is known not to reference its delegate. */
    fun detachDelegate(window: AppKitNativeWindowOwner)

    fun detachContentView(window: AppKitNativeWindowOwner)

    fun closeWindow(window: AppKitNativeWindowOwner)

    fun desktopHandle(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
    ): RuntimeDesktopNativeWindowHandle.AppKit
}

internal interface AppKitNativeWindowOwner : AutoCloseable {
    override fun close()
}

internal interface AppKitNativeViewOwner : AutoCloseable {
    override fun close()
}

/** Immutable effective values captured together on the AppKit owner thread. */
internal data class AppKitSurfaceSnapshot(
    val metrics: SurfaceMetrics,
    val focus: SurfaceFocus,
    val visibility: SurfaceVisibility,
    val occlusion: SurfaceOcclusion,
    val theme: SurfaceTheme,
)

/** Callback boundary used only with already-frozen, native-address-free values. */
internal class AppKitSurfaceCallbacks(
    val metricsChanged: (SurfaceMetrics) -> Unit,
    val focusChanged: (SurfaceFocus) -> Unit,
    val visibilityChanged: (SurfaceVisibility, SurfaceOcclusion) -> Unit,
    val themeChanged: (SurfaceTheme) -> Unit,
    val redrawConsumed: (Long) -> Unit,
)

/** Owns all notification receivers and redraw callback admission for one native surface. */
internal interface AppKitNativeSurfaceObserverOwner : AutoCloseable {
    val initialSnapshot: AppKitSurfaceSnapshot

    fun requestRedraw(generation: Long)

    fun revokeCallbacks()

    override fun close()
}

/**
 * Owns one native delegate receiver.
 *
 * [revokeCallbacks] only closes callback admission. The receiver remains alive so the native
 * delegate can be detached before [close] releases it. Calling [close] is valid only when native
 * attachment was never attempted or detachment completed successfully. When detachment cannot be
 * proven, [retainAfterFailedDetachment] must preserve the receiver for the rest of the process;
 * leaking is safer than leaving an `NSWindow` with a dangling delegate. Retention is idempotent
 * and permanently prevents a later [close] from releasing that receiver.
 */
internal interface AppKitNativeDelegateOwner : AutoCloseable {
    fun revokeCallbacks()

    fun retainAfterFailedDetachment()

    override fun close()
}

internal class AppKitWindowDelegateCallbacks(
    val windowShouldClose: () -> Boolean,
    val windowWillClose: () -> Unit,
)
