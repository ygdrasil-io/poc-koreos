package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.internal.runtime.RuntimeDesktopNativeWindowHandle
import org.graphiks.kadre.internal.runtime.SurfaceMetrics
import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PropertyChange
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

    /**
     * Applies one private geometry request and returns the values AppKit made effective.
     *
     * Implementations keep `NSWindow`, style masks, and all native defaults behind this seam.
     */
    fun updateGeometry(
        window: AppKitNativeWindowOwner,
        target: AppKitWindowGeometryTarget,
    ): AppKitWindowGeometrySnapshot = error("AppKit geometry updates are not installed")

    /** Installs the native geometry observer for one peer, when the port supports it. */
    fun observeGeometry(
        window: AppKitNativeWindowOwner,
        callbacks: AppKitWindowGeometryCallbacks,
    ): AppKitNativeGeometryObserverOwner? = null

    /** Installs one peer-local observer after the complete native window has been presented. */
    fun observeSurface(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
        callbacks: AppKitSurfaceCallbacks,
    ): AppKitNativeSurfaceObserverOwner? = null

    /** Installs one native keyboard/pointer observer after the surface observer is active. */
    fun observeInput(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
        callbacks: AppKitInputCallbacks,
    ): AppKitNativeInputObserverOwner? = null

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

/** Private, native-address-free geometry request forwarded from the runtime command. */
internal data class AppKitWindowGeometryTarget(
    val contentSize: PropertyChange<LogicalSize>,
    val minimumSize: PropertyChange<LogicalSize>,
    val maximumSize: PropertyChange<LogicalSize>,
    val resizable: PropertyChange<Boolean>,
)

/** Native values read together after AppKit has applied a geometry mutation or observation. */
internal data class AppKitWindowGeometrySnapshot(
    val contentSize: LogicalSize,
    val minimumSize: LogicalSize?,
    val maximumSize: LogicalSize?,
    val resizable: Boolean,
)

/** Callback boundary for native-address-free geometry observations. */
internal class AppKitWindowGeometryCallbacks(
    val geometryChanged: (AppKitWindowGeometrySnapshot) -> Unit,
)

/** Owns the peer-local native geometry observation registration. */
internal interface AppKitNativeGeometryObserverOwner : AutoCloseable {
    fun revokeCallbacks()

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

/** Immutable native-address-free input captured synchronously from one borrowed AppKit event. */
internal sealed interface AppKitInput {
    data class KeyChanged(
        val physicalKey: PhysicalKey,
        val logicalKey: LogicalKey,
        val location: KeyLocation,
        val keyState: KeyState,
        val repeat: Boolean,
        val modifiers: KeyboardModifiers,
    ) : AppKitInput {
        init {
            require(keyState == KeyState.Pressed || !repeat) { "a key release cannot repeat" }
        }
    }

    data class PointerEntered(val position: LogicalPoint) : AppKitInput

    data class PointerMoved(
        val position: LogicalPoint,
        val delta: LogicalDelta,
        val pressure: Double?,
    ) : AppKitInput

    data class PointerButtonChanged(
        val button: PointerButton,
        val buttonState: PointerButtonState,
        val position: LogicalPoint,
        val pressure: Double?,
    ) : AppKitInput

    data object PointerLeft : AppKitInput
}

/** Callback boundary that admits only immutable input values, never a borrowed native event. */
internal class AppKitInputCallbacks(
    val input: (AppKitInput) -> Unit,
)

/** Owns native input callback admission and any tracking-area resource for one view. */
internal interface AppKitNativeInputObserverOwner : AutoCloseable {
    val keyboardInstalled: Boolean
    val pointerInstalled: Boolean

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
