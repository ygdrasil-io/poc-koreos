package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.internal.runtime.RuntimeDesktopNativeWindowHandle
import org.graphiks.kadre.internal.runtime.RuntimeSynchronousInteraction
import org.graphiks.kadre.interaction.InteractionAction
import org.graphiks.kadre.internal.runtime.SurfaceMetrics
import org.graphiks.kadre.internal.runtime.WindowPeerOwner
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kadre.window.WindowLevel
import org.graphiks.kadre.window.WindowProperty
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@JvmInline
internal value class AppKitWindowPeerId(internal val value: Long)

internal sealed interface AppKitWindowStimulus {
    val peerId: AppKitWindowPeerId

    data class CloseRequested(
        override val peerId: AppKitWindowPeerId,
    ) : AppKitWindowStimulus

    data class NativeClosed(
        override val peerId: AppKitWindowPeerId,
    ) : AppKitWindowStimulus

    /** An uncorrelated native observation, after peer-local managed-callback filtering. */
    data class GeometryChanged(
        override val peerId: AppKitWindowPeerId,
        val snapshot: AppKitWindowGeometrySnapshot,
        val generation: Long,
    ) : AppKitWindowStimulus

    data class FullscreenCallback(
        override val peerId: AppKitWindowPeerId,
        val callback: AppKitFullscreenCallback,
    ) : AppKitWindowStimulus
}

internal data class AppKitWindowMutation(
    val snapshot: AppKitWindowMutationSnapshot,
    val generation: Long,
    val failure: Throwable?,
    val failureFields: Set<WindowProperty>,
)

internal data class AppKitFullscreenCompletion(
    val snapshot: AppKitWindowMutationSnapshot,
    val restoreFailure: Throwable?,
)

private data class NativeWindowMutationResult(
    val snapshot: AppKitWindowMutationSnapshot,
    val failure: Throwable?,
    val failureFields: Set<WindowProperty>,
)

/** Native-address-free surface observation emitted by one AppKit window peer. */
internal sealed interface AppKitSurfaceStimulus {
    val peerId: AppKitWindowPeerId

    data class MetricsChanged(
        override val peerId: AppKitWindowPeerId,
        val metrics: SurfaceMetrics,
    ) : AppKitSurfaceStimulus

    data class FocusChanged(
        override val peerId: AppKitWindowPeerId,
        val focus: SurfaceFocus,
    ) : AppKitSurfaceStimulus

    data class VisibilityChanged(
        override val peerId: AppKitWindowPeerId,
        val visibility: SurfaceVisibility,
        val occlusion: SurfaceOcclusion,
    ) : AppKitSurfaceStimulus

    data class ThemeChanged(
        override val peerId: AppKitWindowPeerId,
        val theme: SurfaceTheme,
    ) : AppKitSurfaceStimulus

    data class RedrawConsumed(
        override val peerId: AppKitWindowPeerId,
        val generation: Long,
    ) : AppKitSurfaceStimulus {
        init {
            require(generation >= 0L) { "generation must be non-negative" }
        }
    }

    data class InputObservationChanged(
        override val peerId: AppKitWindowPeerId,
        val keyboardInstalled: Boolean,
        val pointerInstalled: Boolean,
    ) : AppKitSurfaceStimulus

    data class KeyChanged(
        override val peerId: AppKitWindowPeerId,
        val input: AppKitInput.KeyChanged,
    ) : AppKitSurfaceStimulus

    data class PointerInput(
        override val peerId: AppKitWindowPeerId,
        val input: AppKitInput,
    ) : AppKitSurfaceStimulus
}

/** One completely prepared AppKit window and the full reverse-order ownership chain it requires. */
internal class AppKitWindowPeer private constructor(
    internal val id: AppKitWindowPeerId,
    private val port: AppKitNativeWindowPort,
    private val window: AppKitNativeWindowOwner,
    private val contentView: AppKitNativeViewOwner,
    private val delegate: AppKitNativeDelegateOwner,
    private val geometryObserver: AppKitNativeGeometryObserverOwner?,
    private val surfaceObserver: AppKitNativeSurfaceObserverOwner?,
    private val inputObserver: AppKitNativeInputObserverOwner?,
    private val callbackGate: AppKitWindowCallbackGate,
    internal val initialWindowSnapshot: AppKitWindowMutationSnapshot?,
) : WindowPeerOwner {
    internal val initialSurfaceSnapshot: AppKitSurfaceSnapshot?
        get() = surfaceObserver?.initialSnapshot

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val lifetimeLock = Object()
    private val closed = AtomicBoolean(false)
    private var activeHandleLeases = 0
    private var nativeCloseCommitted = false

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        awaitHandleLeases()
        callbackGate.revokeAndRunAfterPointerCallbacks(::releaseNativeResources)
    }

    private fun releaseNativeResources() {
        port.onMainThread {
            var failure: Throwable? = null
            inputObserver?.let { observer ->
                failure = runSuppressing(failure, observer::revokeCallbacks)
                failure = closeSuppressing(failure, observer)
            }
            surfaceObserver?.let { observer ->
                failure = runSuppressing(failure, observer::revokeCallbacks)
                failure = closeSuppressing(failure, observer)
            }
            geometryObserver?.let { observer ->
                failure = runSuppressing(failure, observer::revokeCallbacks)
                failure = closeSuppressing(failure, observer)
            }
            failure = runSuppressing(failure, delegate::revokeCallbacks)
            failure = resolveDelegateAfterPossibleAttachment(failure, delegate) {
                port.detachDelegate(window)
            }
            failure = runSuppressing(failure) { port.detachContentView(window) }
            failure = closeSuppressing(failure, contentView)
            val closeNative = synchronized(lifetimeLock) { !nativeCloseCommitted }
            if (closeNative) failure = runSuppressing(failure) { port.closeWindow(window) }
            failure = closeSuppressing(failure, window)
            failure?.let { throw it }
        }
    }

    internal fun commitNativeClose() {
        port.onMainThread {
            try {
                port.closeWindow(window)
            } finally {
                synchronized(lifetimeLock) { nativeCloseCommitted = true }
            }
        }
    }

    internal fun markNativeClosed() {
        synchronized(lifetimeLock) { nativeCloseCommitted = true }
    }

    internal fun requestRedraw(generation: Long) {
        require(generation >= 0L) { "generation must be non-negative" }
        if (closed.get()) return
        port.onMainThread {
            if (!closed.get()) surfaceObserver?.requestRedraw(generation)
        }
    }

    /** Convenience path for direct peer tests and setup that cannot be cancelled. */
    internal fun updateGeometry(target: AppKitWindowGeometryTarget): AppKitWindowGeometrySnapshot? =
        updateWindow(
            AppKitWindowMutationTarget(
                title = PropertyChange.Unchanged,
                geometry = target,
            ),
            ImmediateWindowMutationCommit(),
        )?.snapshot?.geometry

    /** Convenience path for direct peer tests and setup that cannot be cancelled. */
    internal fun updateWindow(target: AppKitWindowMutationTarget): AppKitWindowMutationSnapshot? =
        updateWindow(target, ImmediateWindowMutationCommit())?.snapshot

    /** Runs one native window mutation while ordering geometry callbacks against its readback. */
    internal fun updateWindow(
        target: AppKitWindowMutationTarget,
        commit: AppKitWindowMutationCommit,
    ): AppKitWindowMutation? {
        if (closed.get()) return null
        return port.onMainThread {
            if (closed.get()) {
                null
            } else {
                callbackGate.duringManagedGeometryMutation {
                    try {
                        port.updateWindow(window, target, commit)?.let { snapshot ->
                            NativeWindowMutationResult(snapshot, failure = null, failureFields = emptySet())
                        }
                    } catch (failure: Throwable) {
                        if (!commit.started) throw failure
                        val attributedFailure = failure as? AppKitWindowMutationFailure
                        val nativeFailure = attributedFailure?.cause ?: failure
                        val snapshot = try {
                            port.readWindow(window)
                        } catch (readbackFailure: Throwable) {
                            if (readbackFailure !== nativeFailure) nativeFailure.addSuppressed(readbackFailure)
                            throw nativeFailure
                        }
                        NativeWindowMutationResult(
                            snapshot = snapshot,
                            failure = nativeFailure,
                            failureFields = attributedFailure?.failedFields.orEmpty(),
                        )
                    }
                }
            }
        }
    }

    internal fun beginFullscreenToggleArbitration() {
        callbackGate.beginFullscreenToggle()
    }

    /** Invokes the generated fullscreen selector but leaves completion to delegate callbacks. */
    internal fun toggleFullscreen(
        target: AppKitWindowFullscreenTarget,
        commit: AppKitWindowMutationCommit,
        arbitrationAlreadyStarted: Boolean = false,
    ): Boolean? {
        if (closed.get()) return null
        return port.onMainThread {
            if (closed.get()) {
                null
            } else {
                if (!arbitrationAlreadyStarted) callbackGate.beginFullscreenToggle()
                port.toggleFullscreen(
                    window,
                    target,
                    object : AppKitWindowMutationCommit {
                        override val started: Boolean
                            get() = commit.started

                        override fun beforeFirstSetter(): Boolean =
                            callbackGate.beforeFullscreenSelectorInvocation(commit::beforeFirstSetter)
                    },
                )
            }
        }
    }

    internal fun fullscreenWillObservedSinceToggle(): Boolean =
        callbackGate.fullscreenWillObservedSinceToggle()

    /** Restores the persistent level and returns a fresh authoritative native snapshot. */
    internal fun completeFullscreen(desiredLevel: WindowLevel): AppKitFullscreenCompletion =
        port.onMainThread {
            check(!closed.get()) { "AppKit fullscreen peer closed before terminal readback" }
            val restoreFailure = try {
                port.restoreWindowLevel(window, desiredLevel)
                null
            } catch (failure: Throwable) {
                failure
            }
            val snapshot = try {
                port.readWindow(window)
            } catch (readbackFailure: Throwable) {
                if (restoreFailure != null && restoreFailure !== readbackFailure) {
                    readbackFailure.addSuppressed(restoreFailure)
                }
                throw readbackFailure
            }
            AppKitFullscreenCompletion(snapshot, restoreFailure)
        }

    private class ImmediateWindowMutationCommit : AppKitWindowMutationCommit {
        private var committed = false

        override val started: Boolean
            get() = committed

        override fun beforeFirstSetter(): Boolean {
            committed = true
            return true
        }
    }

    internal fun <R> withDesktopHandle(
        admitCallback: () -> Boolean,
        block: (RuntimeDesktopNativeWindowHandle) -> R,
    ): KadreResult<R>? = port.onMainThread {
        val admitted = synchronized(lifetimeLock) {
            if (closed.get() || !admitCallback()) {
                false
            } else {
                activeHandleLeases += 1
                true
            }
        }
        if (!admitted) {
            return@onMainThread if (closed.get()) {
                KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
            } else {
                null
            }
        }
        try {
            KadreResult.Success(block(port.desktopHandle(window, contentView)))
        } finally {
            synchronized(lifetimeLock) {
                activeHandleLeases -= 1
                check(activeHandleLeases >= 0) { "AppKit window handle lease underflow" }
                lifetimeLock.notifyAll()
            }
        }
    }

    private fun awaitHandleLeases() {
        var interrupted = false
        synchronized(lifetimeLock) {
            while (activeHandleLeases > 0) {
                try {
                    lifetimeLock.wait()
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }

    internal companion object {
        fun prepare(
            id: AppKitWindowPeerId,
            spec: WindowSpec,
            port: AppKitNativeWindowPort,
            acceptStimulus: (AppKitWindowStimulus) -> Unit = {},
            acceptSurfaceStimulus: (AppKitSurfaceStimulus) -> Unit = {},
            dispatchSynchronousInteraction: (
                RuntimeSynchronousInteraction,
                (InteractionAction) -> KadreResult<Unit>,
            ) -> Boolean = { _, _ -> false },
            readInitialWindowSnapshot: Boolean = false,
            reportCallbackFailure: (Throwable) -> Unit = {},
        ): AppKitWindowPeer {
            val callbackGate = AppKitWindowCallbackGate(
                id,
                acceptStimulus,
                acceptSurfaceStimulus,
                reportCallbackFailure,
                dispatchSynchronousInteraction,
            )
            return port.onMainThread {
                var window: AppKitNativeWindowOwner? = null
                var contentView: AppKitNativeViewOwner? = null
                var delegate: AppKitNativeDelegateOwner? = null
                var geometryObserver: AppKitNativeGeometryObserverOwner? = null
                var surfaceObserver: AppKitNativeSurfaceObserverOwner? = null
                var inputObserver: AppKitNativeInputObserverOwner? = null
                var contentAttached = false
                var delegateMayBeAttached = false
                try {
                    window = port.createWindow(spec)
                    contentView = port.createContentView(spec)
                    delegate = port.createDelegate(
                        id,
                        AppKitWindowDelegateCallbacks(
                            windowShouldClose = callbackGate::windowShouldClose,
                            windowWillClose = callbackGate::windowWillClose,
                            windowWillEnterFullscreen = callbackGate::windowWillEnterFullscreen,
                            windowDidEnterFullscreen = callbackGate::windowDidEnterFullscreen,
                            windowDidFailEnterFullscreen = callbackGate::windowDidFailEnterFullscreen,
                            windowWillExitFullscreen = callbackGate::windowWillExitFullscreen,
                            windowDidExitFullscreen = callbackGate::windowDidExitFullscreen,
                            windowDidFailExitFullscreen = callbackGate::windowDidFailExitFullscreen,
                        ),
                    )
                    port.attachContentView(window, contentView)
                    contentAttached = true
                    delegateMayBeAttached = true
                    port.attachDelegate(window, delegate)
                    val initialWindowSnapshot = if (readInitialWindowSnapshot) {
                        port.readWindow(window).also { snapshot ->
                            check(snapshot.level == spec.level) {
                                "AppKit initial window level readback diverged: " +
                                    "requested=${spec.level}, effective=${snapshot.level}"
                            }
                            check(snapshot.appearance.transparency == spec.transparent) {
                                "AppKit initial window transparency readback diverged: " +
                                    "requested=${spec.transparent}, " +
                                    "effective=${snapshot.appearance.transparency}"
                            }
                        }
                    } else {
                        null
                    }
                    port.present(window)
                    geometryObserver = port.observeGeometry(
                        window,
                        AppKitWindowGeometryCallbacks(callbackGate::geometryChanged),
                    )
                    surfaceObserver = port.observeSurface(
                        window,
                        contentView,
                        AppKitSurfaceCallbacks(
                            metricsChanged = callbackGate::metricsChanged,
                            focusChanged = callbackGate::focusChanged,
                            visibilityChanged = callbackGate::visibilityChanged,
                            themeChanged = callbackGate::themeChanged,
                            redrawConsumed = callbackGate::redrawConsumed,
                        ),
                    )
                    surfaceObserver?.let { callbackGate.activateSurface(it.initialSnapshot) }
                    inputObserver = port.observeInput(
                        window,
                        contentView,
                        AppKitInputCallbacks(
                            input = callbackGate::input,
                            pointerDown = callbackGate::pointerDown,
                        ),
                    )
                    inputObserver?.let { observer ->
                        callbackGate.inputObservationChanged(
                            keyboardInstalled = observer.keyboardInstalled,
                            pointerInstalled = observer.pointerInstalled,
                        )
                    }
                    AppKitWindowPeer(
                        id,
                        port,
                        window,
                        contentView,
                        delegate,
                        geometryObserver,
                        surfaceObserver,
                        inputObserver,
                        callbackGate,
                        initialWindowSnapshot,
                    )
                } catch (failure: Throwable) {
                    callbackGate.revoke()
                    var cleanupFailure: Throwable? = failure
                    inputObserver?.let { observer ->
                        cleanupFailure = runSuppressing(cleanupFailure, observer::revokeCallbacks)
                        cleanupFailure = closeSuppressing(cleanupFailure, observer)
                    }
                    surfaceObserver?.let { observer ->
                        cleanupFailure = runSuppressing(cleanupFailure, observer::revokeCallbacks)
                        cleanupFailure = closeSuppressing(cleanupFailure, observer)
                    }
                    geometryObserver?.let { observer ->
                        cleanupFailure = runSuppressing(cleanupFailure, observer::revokeCallbacks)
                        cleanupFailure = closeSuppressing(cleanupFailure, observer)
                    }
                    delegate?.let { nativeDelegate ->
                        cleanupFailure = runSuppressing(cleanupFailure, nativeDelegate::revokeCallbacks)
                        cleanupFailure = if (delegateMayBeAttached) {
                            resolveDelegateAfterPossibleAttachment(cleanupFailure, nativeDelegate) {
                                port.detachDelegate(checkNotNull(window))
                            }
                        } else {
                            closeSuppressing(cleanupFailure, nativeDelegate)
                        }
                    }
                    if (contentAttached) {
                        cleanupFailure = runSuppressing(cleanupFailure) {
                            port.detachContentView(checkNotNull(window))
                        }
                    }
                    contentView?.let { cleanupFailure = closeSuppressing(cleanupFailure, it) }
                    window?.let { nativeWindow ->
                        cleanupFailure = runSuppressing(cleanupFailure) { port.closeWindow(nativeWindow) }
                        cleanupFailure = closeSuppressing(cleanupFailure, nativeWindow)
                    }
                    throw failure
                }
            }
        }
    }
}

private class AppKitWindowCallbackGate(
    private val peerId: AppKitWindowPeerId,
    private val acceptWindowStimulus: (AppKitWindowStimulus) -> Unit,
    private val acceptSurfaceStimulus: (AppKitSurfaceStimulus) -> Unit,
    private val reportFailure: (Throwable) -> Unit,
    private val dispatchSynchronousInteraction: (
        RuntimeSynchronousInteraction,
        (InteractionAction) -> KadreResult<Unit>,
    ) -> Boolean,
) {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val lock = Object()
    private var accepting = true
    private var surfaceAccepting = false
    private var nativeCloseDelivered = false
    private var lastMetrics: SurfaceMetrics? = null
    private var lastFocus: SurfaceFocus? = null
    private var lastVisibility: Pair<SurfaceVisibility, SurfaceOcclusion>? = null
    private var lastTheme: SurfaceTheme? = null
    private var lastRedrawGeneration = -1L
    private var geometryGeneration = 0L
    private var managedGeometryMutationDepth = 0
    private var fullscreenWillObservedSinceToggle = false
    private val bufferedManagedGeometryCallbacks = ArrayDeque<AppKitWindowStimulus.GeometryChanged>()
    private var activePointerCallbacks = 0
    private val activePointerCallbackThreads = linkedMapOf<Thread, Int>()
    private val deferredPointerCallbackCleanup = ArrayDeque<() -> Unit>()

    fun duringManagedGeometryMutation(
        block: () -> NativeWindowMutationResult?,
    ): AppKitWindowMutation? {
        synchronized(lock) {
            check(managedGeometryMutationDepth == 0) { "AppKit managed geometry mutations must be serialized" }
            managedGeometryMutationDepth = 1
        }
        val result = try {
            block()
        } catch (failure: Throwable) {
            flushManagedGeometryCallbacks(effectiveSnapshot = null)
            throw failure
        }
        val generation = flushManagedGeometryCallbacks(result?.snapshot?.geometry)
        return result?.let { mutation ->
            AppKitWindowMutation(
                snapshot = mutation.snapshot,
                generation = checkNotNull(generation),
                failure = mutation.failure,
                failureFields = mutation.failureFields,
            )
        }
    }

    fun activateSurface(snapshot: AppKitSurfaceSnapshot) {
        synchronized(lock) {
            if (!accepting) return
            lastMetrics = snapshot.metrics
            lastFocus = snapshot.focus
            lastVisibility = snapshot.visibility to snapshot.occlusion
            lastTheme = snapshot.theme
            surfaceAccepting = true
        }
    }

    fun metricsChanged(metrics: SurfaceMetrics) {
        val stimulus = synchronized(lock) {
            if (!surfaceAccepting || metrics == lastMetrics) {
                null
            } else {
                lastMetrics = metrics
                AppKitSurfaceStimulus.MetricsChanged(peerId, metrics)
            }
        }
        stimulus?.let(::publishSurface)
    }

    fun focusChanged(focus: SurfaceFocus) {
        val stimulus = synchronized(lock) {
            if (!surfaceAccepting || focus == lastFocus) {
                null
            } else {
                lastFocus = focus
                AppKitSurfaceStimulus.FocusChanged(peerId, focus)
            }
        }
        stimulus?.let(::publishSurface)
    }

    fun visibilityChanged(visibility: SurfaceVisibility, occlusion: SurfaceOcclusion) {
        val effective = visibility to occlusion
        val stimulus = synchronized(lock) {
            if (!surfaceAccepting || effective == lastVisibility) {
                null
            } else {
                lastVisibility = effective
                AppKitSurfaceStimulus.VisibilityChanged(peerId, visibility, occlusion)
            }
        }
        stimulus?.let(::publishSurface)
    }

    fun themeChanged(theme: SurfaceTheme) {
        val stimulus = synchronized(lock) {
            if (!surfaceAccepting || theme == lastTheme) {
                null
            } else {
                lastTheme = theme
                AppKitSurfaceStimulus.ThemeChanged(peerId, theme)
            }
        }
        stimulus?.let(::publishSurface)
    }

    fun redrawConsumed(generation: Long) {
        require(generation >= 0L) { "generation must be non-negative" }
        val stimulus = synchronized(lock) {
            if (!surfaceAccepting || generation <= lastRedrawGeneration) {
                null
            } else {
                lastRedrawGeneration = generation
                AppKitSurfaceStimulus.RedrawConsumed(peerId, generation)
            }
        }
        stimulus?.let(::publishSurface)
    }

    fun input(input: AppKitInput) {
        val stimulus = synchronized(lock) {
            if (!surfaceAccepting) {
                null
            } else when (input) {
                is AppKitInput.KeyChanged -> AppKitSurfaceStimulus.KeyChanged(peerId, input)
                else -> AppKitSurfaceStimulus.PointerInput(peerId, input)
            }
        }
        stimulus?.let(::publishSurface)
    }

    /**
     * Keeps the borrowed AppKit event callback synchronous: the runtime decides whether the
     * native move is admitted, then it publishes the one ordinary immutable pointer input.
     */
    fun pointerDown(
        input: AppKitInput.PointerButtonChanged,
        invokeNativeMove: () -> KadreResult<Unit>,
    ) {
        val admitted = synchronized(lock) {
            if (!surfaceAccepting) {
                false
            } else {
                activePointerCallbacks += 1
                val thread = Thread.currentThread()
                activePointerCallbackThreads[thread] = (activePointerCallbackThreads[thread] ?: 0) + 1
                true
            }
        }
        if (!admitted) return
        val borrowedMove = BorrowedPointerMove(invokeNativeMove)
        try {
            val dispatched = dispatchSynchronousInteraction(
                RuntimeSynchronousInteraction.PointerPressed(input.button, input.position, input.pressure),
            ) { action ->
                if (action == InteractionAction.BeginWindowMove) {
                    borrowedMove.invoke()
                } else {
                    KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.Interaction))
                }
            }
            // Before public surface commit, no runtime surface exists yet. Preserve the regular
            // immutable path without retaining the native event; live surfaces always dispatch above.
            if (!dispatched) input(input)
        } finally {
            // A handler may retain its callback context. The runtime's native lambda can therefore
            // outlive this stack frame, but its AppKit event callback cannot.
            borrowedMove.revoke()
            completePointerCallback()
        }
    }

    fun inputObservationChanged(keyboardInstalled: Boolean, pointerInstalled: Boolean) {
        val stimulus = synchronized(lock) {
            if (surfaceAccepting) {
                AppKitSurfaceStimulus.InputObservationChanged(peerId, keyboardInstalled, pointerInstalled)
            } else {
                null
            }
        }
        stimulus?.let(::publishSurface)
    }

    fun geometryChanged(snapshot: AppKitWindowGeometrySnapshot) {
        val stimulus = synchronized(lock) {
            if (!accepting) {
                null
            } else {
                val observed = AppKitWindowStimulus.GeometryChanged(
                    peerId,
                    snapshot,
                    generation = ++geometryGeneration,
                )
                if (managedGeometryMutationDepth > 0) {
                    bufferedManagedGeometryCallbacks.addLast(observed)
                    null
                } else {
                    observed
                }
            }
        }
        stimulus?.let(::publishWindow)
    }

    private fun flushManagedGeometryCallbacks(effectiveSnapshot: AppKitWindowGeometrySnapshot?): Long? {
        val (generation, stimuli) = synchronized(lock) {
            managedGeometryMutationDepth -= 1
            check(managedGeometryMutationDepth >= 0) { "AppKit managed geometry callback depth underflow" }
            val managedGeneration = effectiveSnapshot?.let { ++geometryGeneration }
            if (managedGeometryMutationDepth > 0 || !accepting) {
                if (!accepting) bufferedManagedGeometryCallbacks.clear()
                managedGeneration to emptyList()
            } else {
                bufferedManagedGeometryCallbacks.removeAll { it.snapshot == effectiveSnapshot }
                managedGeneration to bufferedManagedGeometryCallbacks.toList().also {
                    bufferedManagedGeometryCallbacks.clear()
                }
            }
        }
        stimuli.forEach(::publishWindow)
        return generation
    }

    fun windowShouldClose(): Boolean {
        val stimulus = synchronized(lock) {
            if (accepting) AppKitWindowStimulus.CloseRequested(peerId) else null
        }
        stimulus?.let(::publishWindow)
        return false
    }

    fun windowWillClose() {
        val stimulus = synchronized(lock) {
            if (!accepting || nativeCloseDelivered) {
                null
            } else {
                nativeCloseDelivered = true
                AppKitWindowStimulus.NativeClosed(peerId)
            }
        }
        stimulus?.let(::publishWindow)
    }

    fun beginFullscreenToggle() {
        synchronized(lock) { fullscreenWillObservedSinceToggle = false }
    }

    fun fullscreenWillObservedSinceToggle(): Boolean =
        synchronized(lock) { fullscreenWillObservedSinceToggle }

    fun beforeFullscreenSelectorInvocation(invoke: () -> Boolean): Boolean = synchronized(lock) {
        if (fullscreenWillObservedSinceToggle) false else invoke()
    }

    fun windowWillEnterFullscreen() = fullscreen(AppKitFullscreenCallback.WillEnter)

    fun windowDidEnterFullscreen() = fullscreen(AppKitFullscreenCallback.DidEnter)

    fun windowDidFailEnterFullscreen() = fullscreen(AppKitFullscreenCallback.DidFailEnter)

    fun windowWillExitFullscreen() = fullscreen(AppKitFullscreenCallback.WillExit)

    fun windowDidExitFullscreen() = fullscreen(AppKitFullscreenCallback.DidExit)

    fun windowDidFailExitFullscreen() = fullscreen(AppKitFullscreenCallback.DidFailExit)

    private fun fullscreen(callback: AppKitFullscreenCallback) {
        val stimulus = synchronized(lock) {
            if (!accepting) {
                null
            } else {
                if (
                    callback == AppKitFullscreenCallback.WillEnter ||
                    callback == AppKitFullscreenCallback.WillExit
                ) {
                    fullscreenWillObservedSinceToggle = true
                }
                AppKitWindowStimulus.FullscreenCallback(peerId, callback)
            }
        }
        stimulus?.let(::publishWindow)
    }

    fun revoke() {
        synchronized(lock) {
            accepting = false
            surfaceAccepting = false
        }
    }

    /**
     * Linearizes teardown with pressed-pointer callbacks. A different closer waits until the
     * borrowed event callback is finished; a close requested by that callback is drained from its
     * finally block, after the borrowed move closure was revoked.
     */
    fun revokeAndRunAfterPointerCallbacks(cleanup: () -> Unit) {
        val runNow = synchronized(lock) {
            accepting = false
            surfaceAccepting = false
            if (activePointerCallbacks == 0) {
                true
            } else if ((activePointerCallbackThreads[Thread.currentThread()] ?: 0) > 0) {
                deferredPointerCallbackCleanup += cleanup
                false
            } else {
                while (activePointerCallbacks > 0) lock.wait()
                true
            }
        }
        if (runNow) cleanup()
    }

    private fun completePointerCallback() {
        val deferredCleanup = synchronized(lock) {
            activePointerCallbacks -= 1
            check(activePointerCallbacks >= 0) { "AppKit pointer callback depth underflow" }
            val thread = Thread.currentThread()
            val activeOnThread = checkNotNull(activePointerCallbackThreads[thread]) - 1
            if (activeOnThread == 0) activePointerCallbackThreads.remove(thread)
            else activePointerCallbackThreads[thread] = activeOnThread
            if (activePointerCallbacks == 0) {
                lock.notifyAll()
                deferredPointerCallbackCleanup.toList().also { deferredPointerCallbackCleanup.clear() }
            } else {
                emptyList()
            }
        }
        deferredCleanup.forEach { cleanup -> cleanup() }
    }

    private fun publishWindow(stimulus: AppKitWindowStimulus) {
        publish(stimulus, acceptWindowStimulus)
    }

    private fun publishSurface(stimulus: AppKitSurfaceStimulus) {
        publish(stimulus, acceptSurfaceStimulus)
    }

    private fun <T> publish(stimulus: T, consumer: (T) -> Unit) {
        try {
            consumer(stimulus)
        } catch (failure: Throwable) {
            try {
                reportFailure(failure)
            } catch (_: Throwable) {
                // Both failures are contained at the Objective-C callback boundary.
            }
        }
    }
}

private class BorrowedPointerMove(callback: () -> KadreResult<Unit>) {
    private val callback = AtomicReference<(() -> KadreResult<Unit>)?>(callback)

    fun invoke(): KadreResult<Unit> = callback.get()?.invoke()
        ?: KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Interaction))

    fun revoke() {
        callback.set(null)
    }
}

private fun closeSuppressing(primary: Throwable?, closeable: AutoCloseable): Throwable? =
    runSuppressing(primary, closeable::close)

private fun resolveDelegateAfterPossibleAttachment(
    primary: Throwable?,
    delegate: AppKitNativeDelegateOwner,
    detach: () -> Unit,
): Throwable? {
    var detached = false
    var failure = runSuppressing(primary) {
        detach()
        detached = true
    }
    failure = if (detached) {
        closeSuppressing(failure, delegate)
    } else {
        runSuppressing(failure, delegate::retainAfterFailedDetachment)
    }
    return failure
}

private fun runSuppressing(primary: Throwable?, action: () -> Unit): Throwable? {
    return try {
        action()
        primary
    } catch (failure: Throwable) {
        if (primary == null) {
            failure
        } else {
            primary.also {
                if (it !== failure) it.addSuppressed(failure)
            }
        }
    }
}
