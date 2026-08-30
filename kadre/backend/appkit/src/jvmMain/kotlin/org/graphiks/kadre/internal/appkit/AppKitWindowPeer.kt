package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeDesktopNativeWindowHandle
import org.graphiks.kadre.internal.runtime.SurfaceMetrics
import org.graphiks.kadre.internal.runtime.WindowPeerOwner
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.window.WindowSpec
import java.util.concurrent.atomic.AtomicBoolean

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
}

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
    private val surfaceObserver: AppKitNativeSurfaceObserverOwner?,
    private val inputObserver: AppKitNativeInputObserverOwner?,
    private val callbackGate: AppKitWindowCallbackGate,
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
        callbackGate.revoke()
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
            reportCallbackFailure: (Throwable) -> Unit = {},
        ): AppKitWindowPeer {
            val callbackGate = AppKitWindowCallbackGate(
                id,
                acceptStimulus,
                acceptSurfaceStimulus,
                reportCallbackFailure,
            )
            return port.onMainThread {
                var window: AppKitNativeWindowOwner? = null
                var contentView: AppKitNativeViewOwner? = null
                var delegate: AppKitNativeDelegateOwner? = null
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
                        ),
                    )
                    port.attachContentView(window, contentView)
                    contentAttached = true
                    delegateMayBeAttached = true
                    port.attachDelegate(window, delegate)
                    port.present(window)
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
                        AppKitInputCallbacks(callbackGate::input),
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
                        surfaceObserver,
                        inputObserver,
                        callbackGate,
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
) {
    private val lock = Any()
    private var accepting = true
    private var surfaceAccepting = false
    private var nativeCloseDelivered = false
    private var lastMetrics: SurfaceMetrics? = null
    private var lastFocus: SurfaceFocus? = null
    private var lastVisibility: Pair<SurfaceVisibility, SurfaceOcclusion>? = null
    private var lastTheme: SurfaceTheme? = null
    private var lastRedrawGeneration = -1L

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

    fun revoke() {
        synchronized(lock) {
            accepting = false
            surfaceAccepting = false
        }
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
