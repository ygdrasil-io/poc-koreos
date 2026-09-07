package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeDesktopNativeWindowHandle
import org.graphiks.kadre.internal.runtime.SurfaceMetrics
import org.graphiks.kadre.internal.runtime.TextInputCursorCommand
import org.graphiks.kadre.internal.runtime.TextInputDocumentCommand
import org.graphiks.kadre.internal.runtime.TextInputObservation
import org.graphiks.kadre.internal.runtime.TextInputOwner
import org.graphiks.kadre.internal.runtime.DropTransferSource
import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.window.WindowDecorations
import org.graphiks.kadre.window.FullscreenMode
import org.graphiks.kadre.window.WindowLevel
import org.graphiks.kadre.window.WindowProperty
import org.graphiks.kadre.window.WindowSystemButtons
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

    /**
     * Returns the text-input port owned by [view]. The default keeps backends that have not
     * installed a revocable `NSTextInputClient` receiver explicitly unsupported.
     */
    fun textInputPort(view: AppKitNativeViewOwner): AppKitNativeTextInputPort = AppKitUnsupportedTextInputPort

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
     * Applies one private window mutation and returns the values AppKit made effective.
     *
     * Implementations keep `NSWindow`, style masks, and all native defaults behind this seam.
     * [commit] is consulted immediately before the first native setter; `null` means that setter
     * was withdrawn and no native mutation occurred.
     */
    fun updateWindow(
        window: AppKitNativeWindowOwner,
        target: AppKitWindowMutationTarget,
        commit: AppKitWindowMutationCommit,
    ): AppKitWindowMutationSnapshot? = error("AppKit window mutations are not installed")

    /** Reads one authoritative effective snapshot after a setter began, including after failure. */
    fun readWindow(window: AppKitNativeWindowOwner): AppKitWindowMutationSnapshot =
        error("AppKit window readback is not installed")

    /** Begins one native fullscreen transition without completing the runtime mutation. */
    fun toggleFullscreen(
        window: AppKitNativeWindowOwner,
        target: AppKitWindowFullscreenTarget,
        commit: AppKitWindowMutationCommit,
    ): Boolean = error("AppKit fullscreen mutations are not installed")

    /** Restores the persistent level after a terminal fullscreen callback. */
    fun restoreWindowLevel(window: AppKitNativeWindowOwner, desiredLevel: WindowLevel) {
        error("AppKit fullscreen level restoration is not installed")
    }

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

    /** Installs one `NSDraggingDestination` bridge after the regular input observer is ready. */
    fun observeDrop(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
        callbacks: AppKitDropCallbacks,
    ): AppKitNativeDropObserverOwner? = null

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

/** Identifies the native mutation field whose setter or readback actually failed. */
internal class AppKitWindowMutationFailure(
    val failedFields: Set<WindowProperty>,
    cause: Throwable,
) : RuntimeException(cause)

/** Peer-to-port token whose transition is the exact first native window setter boundary. */
internal interface AppKitWindowMutationCommit {
    val started: Boolean

    fun beforeFirstSetter(): Boolean
}

internal interface AppKitNativeViewOwner : AutoCloseable {
    override fun close()
}

/**
 * Synchronous, AppKit-main-thread side of a text-input receiver.
 *
 * [TextInputPort] deliberately remains suspending at the portable runtime boundary. AppKit owns
 * a synchronous native run-loop instead, so [AppKitPeerTextInputPort] is the sole adapter that
 * marshals this contract onto the main thread without blocking it in a nested coroutine.
 */
internal interface AppKitNativeTextInputPort {
    val capability: Capability<Unit>

    fun open(command: AppKitNativeTextInputOpenCommand): KadreResult<TextInputOwner>

    fun updateCursor(command: TextInputCursorCommand): KadreResult<Unit>

    fun updateDocument(command: TextInputDocumentCommand): KadreResult<Unit>
}

/** Immutable pointer-free values copied from the runtime into the synchronous AppKit receiver. */
internal data class AppKitNativeTextInputOpenCommand(
    val config: TextInputConfig,
    val onObservation: (TextInputObservation) -> Boolean,
)

private object AppKitUnsupportedTextInputPort : AppKitNativeTextInputPort {
    override val capability: Capability<Unit> = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.TextInput))

    override fun open(command: AppKitNativeTextInputOpenCommand): KadreResult<TextInputOwner> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.TextInput))

    override fun updateCursor(command: TextInputCursorCommand): KadreResult<Unit> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.TextInput))

    override fun updateDocument(command: TextInputDocumentCommand): KadreResult<Unit> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.TextInput))
}

/** Private, native-address-free geometry request forwarded from the runtime command. */
internal data class AppKitWindowGeometryTarget(
    val contentSize: PropertyChange<LogicalSize>,
    val minimumSize: PropertyChange<LogicalSize>,
    val maximumSize: PropertyChange<LogicalSize>,
    val resizable: PropertyChange<Boolean>,
)

/** Private, native-address-free chrome request forwarded from the runtime command. */
internal data class AppKitWindowChromeTarget(
    val decorations: PropertyChange<WindowDecorations>,
    val systemButtons: PropertyChange<WindowSystemButtons>,
)

/** Private, native-address-free window level request forwarded from the runtime command. */
internal data class AppKitWindowLevelTarget(
    val level: PropertyChange<WindowLevel>,
)

/** Private, native-address-free appearance request forwarded from the runtime command. */
internal data class AppKitWindowAppearanceTarget(
    val transparency: PropertyChange<Boolean>,
)

/** Private, native-address-free fullscreen request forwarded from the runtime command. */
internal data class AppKitWindowFullscreenTarget(val mode: FullscreenMode)

/** Directional callbacks admitted from the generated NSWindowDelegate receiver. */
internal sealed interface AppKitFullscreenCallback {
    data object WillEnter : AppKitFullscreenCallback
    data object DidEnter : AppKitFullscreenCallback
    data object DidFailEnter : AppKitFullscreenCallback
    data object WillExit : AppKitFullscreenCallback
    data object DidExit : AppKitFullscreenCallback
    data object DidFailExit : AppKitFullscreenCallback
}

/** Private, native-address-free window request forwarded from the runtime command. */
internal data class AppKitWindowMutationTarget(
    val title: PropertyChange<String>,
    val geometry: AppKitWindowGeometryTarget,
    val chrome: AppKitWindowChromeTarget = AppKitWindowChromeTarget(
        decorations = PropertyChange.Unchanged,
        systemButtons = PropertyChange.Unchanged,
    ),
    val level: AppKitWindowLevelTarget = AppKitWindowLevelTarget(PropertyChange.Unchanged),
    val appearance: AppKitWindowAppearanceTarget = AppKitWindowAppearanceTarget(
        transparency = PropertyChange.Unchanged,
    ),
)

/** Native values read together after AppKit has applied a geometry mutation or observation. */
internal data class AppKitWindowGeometrySnapshot(
    val contentSize: LogicalSize,
    val minimumSize: LogicalSize?,
    val maximumSize: LogicalSize?,
    val resizable: Boolean,
)

/** Native chrome values read together with the rest of an effective window mutation. */
internal data class AppKitWindowChromeSnapshot(
    val decorations: WindowDecorations,
    val systemButtons: WindowSystemButtons,
)

/** Native appearance values read together with the rest of an effective window mutation. */
internal data class AppKitWindowAppearanceSnapshot(
    val transparency: Boolean,
)

/** Native values read together after AppKit has applied one window mutation. */
internal data class AppKitWindowMutationSnapshot(
    val title: String,
    val geometry: AppKitWindowGeometrySnapshot,
    val chrome: AppKitWindowChromeSnapshot = AppKitWindowChromeSnapshot(
        decorations = WindowDecorations.System,
        systemButtons = WindowSystemButtons.All,
    ),
    val level: WindowLevel = WindowLevel.Normal,
    val appearance: AppKitWindowAppearanceSnapshot = AppKitWindowAppearanceSnapshot(
        transparency = false,
    ),
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
    /** Runs only within a pressed-pointer native callback while its event is still borrowed. */
    val pointerDown: (AppKitInput.PointerButtonChanged, () -> KadreResult<Unit>) -> Unit = { pointer, _ ->
        input(pointer)
    },
)

/** Owns native input callback admission and any tracking-area resource for one view. */
internal interface AppKitNativeInputObserverOwner : AutoCloseable {
    val keyboardInstalled: Boolean
    val pointerInstalled: Boolean

    fun revokeCallbacks()

    override fun close()
}

/** Callback boundary for one retained, runtime-owned drag payload. */
internal class AppKitDropCallbacks(
    val entered: (DropTransferSource, LogicalPoint) -> Boolean,
    /** Returns whether the native destination must continue advertising an admitted copy. */
    val moved: (LogicalPoint) -> Boolean,
    val exited: () -> Unit,
    val performed: (LogicalPoint) -> Boolean,
)

/** Owns native drag-destination callback admission for one content view. */
internal interface AppKitNativeDropObserverOwner : AutoCloseable {
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
    val windowWillEnterFullscreen: () -> Unit = {},
    val windowDidEnterFullscreen: () -> Unit = {},
    val windowDidFailEnterFullscreen: () -> Unit = {},
    val windowWillExitFullscreen: () -> Unit = {},
    val windowDidExitFullscreen: () -> Unit = {},
    val windowDidFailExitFullscreen: () -> Unit = {},
)
