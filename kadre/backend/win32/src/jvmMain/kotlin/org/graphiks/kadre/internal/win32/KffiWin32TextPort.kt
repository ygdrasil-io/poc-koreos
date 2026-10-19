package org.graphiks.kadre.internal.win32

import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.surface.LogicalRect
import org.graphiks.kffi.win32.managed.*
import java.util.concurrent.atomic.AtomicBoolean

/** No native layout or address crosses this adapter: all COM work belongs to KFFI. */
internal class KffiWin32TextPort(
    private val hwnd: Long,
    private val host: Win32NativeHostPort,
    private val services: Win32TextServices,
) : Win32TextNativePort {
    override val capability: Capability<Unit> = Capability.Supported(Unit, FeatureAvailability.Available)
    internal var lastStore: Win32TextStore? = null
        private set
    override fun <T> onMainThread(block: () -> T): T = host.onMainThread(block)
    override fun postOnMainThread(block: () -> Unit, onFailure: (Throwable) -> Unit) = host.postOnMainThread(block, onFailure)
    override fun createStore(initial: Win32TextSnapshot, configuration: Win32TextConfiguration, limits: Win32TextLimits,
        onChange: (Win32TextChange) -> Boolean, onFailure: (Throwable) -> Unit): Win32NativeTextOwner {
        val store = services.createStore(hwnd, initial, configuration, limits, onChange, onFailure)
        lastStore = store
        return Owner(store, initial.epoch, onFailure)
    }

    private inner class Owner(private val store: Win32TextStore, private var epoch: Long,
        private val failure: (Throwable) -> Unit) : Win32NativeTextOwner {
        private val closed = AtomicBoolean()
        private val refreshPending = AtomicBoolean()
        @Volatile private var cursor: Pair<LogicalRect, Long>? = null
        override fun activate() = store.activate()
        override fun apply(snapshot: Win32TextSnapshot) {
            store.apply(snapshot)
            epoch = snapshot.epoch
            if (cursor?.second != epoch) cursor = null
        }
        override fun updateCursor(rect: LogicalRect, epoch: Long) {
            val physical = win32CaretRect(rect, host.readScaleFactor(hwnd), Win32Pointer.clientToScreen(hwnd, 0, 0))
            store.updateCursor(physical, epoch)
            cursor = rect to epoch
        }
        override fun refreshCursor() {
            if (closed.get() || cursor == null || !refreshPending.compareAndSet(false, true)) return
            try { host.postOnMainThread({
                refreshPending.set(false)
                if (!closed.get()) cursor?.takeIf { it.second == epoch }?.let { updateCursor(it.first, it.second) }
            }, { refreshPending.set(false); if (!closed.get()) failure(it) }) }
            catch (cause: Throwable) { refreshPending.set(false); if (!closed.get()) failure(cause) }
        }
        override fun setFocused(focused: Boolean) = store.setFocused(focused)
        override fun handleCharacter(message: Int, wParam: Long, lParam: Long): Long? = store.handleCharacter(message, wParam, lParam)
        override fun revoke() = store.revoke()
        override fun close() {
            if (closed.compareAndSet(false, true)) { cursor = null; store.close() }
        }
    }
}
