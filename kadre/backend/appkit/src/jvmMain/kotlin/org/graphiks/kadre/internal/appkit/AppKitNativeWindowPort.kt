package org.graphiks.kadre.internal.appkit

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

    /** Returns only when the native window is known not to reference its delegate. */
    fun detachDelegate(window: AppKitNativeWindowOwner)

    fun detachContentView(window: AppKitNativeWindowOwner)

    fun closeWindow(window: AppKitNativeWindowOwner)
}

internal interface AppKitNativeWindowOwner : AutoCloseable {
    override fun close()
}

internal interface AppKitNativeViewOwner : AutoCloseable {
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
