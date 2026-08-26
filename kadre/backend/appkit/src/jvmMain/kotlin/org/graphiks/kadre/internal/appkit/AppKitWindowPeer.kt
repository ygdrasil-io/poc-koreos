package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.internal.runtime.WindowPeerOwner
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

/** One completely prepared AppKit window and the full reverse-order ownership chain it requires. */
internal class AppKitWindowPeer private constructor(
    internal val id: AppKitWindowPeerId,
    private val port: AppKitNativeWindowPort,
    private val window: AppKitNativeWindowOwner,
    private val contentView: AppKitNativeViewOwner,
    private val delegate: AppKitNativeDelegateOwner,
    private val callbackGate: AppKitWindowCallbackGate,
) : WindowPeerOwner {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        callbackGate.revoke()
        port.onMainThread {
            var failure: Throwable? = null
            failure = runSuppressing(failure, delegate::revokeCallbacks)
            failure = resolveDelegateAfterPossibleAttachment(failure, delegate) {
                port.detachDelegate(window)
            }
            failure = runSuppressing(failure) { port.detachContentView(window) }
            failure = closeSuppressing(failure, contentView)
            failure = runSuppressing(failure) { port.closeWindow(window) }
            failure = closeSuppressing(failure, window)
            failure?.let { throw it }
        }
    }

    internal companion object {
        fun prepare(
            id: AppKitWindowPeerId,
            spec: WindowSpec,
            port: AppKitNativeWindowPort,
            acceptStimulus: (AppKitWindowStimulus) -> Unit = {},
            reportCallbackFailure: (Throwable) -> Unit = {},
        ): AppKitWindowPeer {
            val callbackGate = AppKitWindowCallbackGate(
                id,
                acceptStimulus,
                reportCallbackFailure,
            )
            return port.onMainThread {
                var window: AppKitNativeWindowOwner? = null
                var contentView: AppKitNativeViewOwner? = null
                var delegate: AppKitNativeDelegateOwner? = null
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
                    AppKitWindowPeer(
                        id,
                        port,
                        window,
                        contentView,
                        delegate,
                        callbackGate,
                    )
                } catch (failure: Throwable) {
                    callbackGate.revoke()
                    var cleanupFailure: Throwable? = failure
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
    private val acceptStimulus: (AppKitWindowStimulus) -> Unit,
    private val reportFailure: (Throwable) -> Unit,
) {
    private val lock = Any()
    private var accepting = true
    private var nativeCloseDelivered = false

    fun windowShouldClose(): Boolean {
        val stimulus = synchronized(lock) {
            if (accepting) AppKitWindowStimulus.CloseRequested(peerId) else null
        }
        stimulus?.let(::publish)
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
        stimulus?.let(::publish)
    }

    fun revoke() {
        synchronized(lock) {
            accepting = false
        }
    }

    private fun publish(stimulus: AppKitWindowStimulus) {
        try {
            acceptStimulus(stimulus)
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
