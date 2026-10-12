package org.graphiks.kadre.internal.wayland

import java.util.concurrent.atomic.AtomicBoolean
import org.graphiks.kadre.internal.runtime.OpenedWindowCloseOutcome
import org.graphiks.kadre.internal.runtime.WindowPeerOwner
import org.graphiks.kadre.window.WindowId

/**
 * Pair d'une fenêtre Wayland réelle : état minimal côté backend, destruction
 * ordonnée (toplevel → xdg_surface → surface → buffer) et tracking du cycle
 * de vie pour les stimuli natifs. Aucun second snapshot public : le runtime
 * reste l'autorité d'état.
 */
internal class WaylandWindowPeer(
    val windowId: WindowId,
    val window: WaylandNativeWindow,
    private val onNativeClosed: (WindowId) -> Unit,
) : WindowPeerOwner, AutoCloseable {

    private val closed = AtomicBoolean(false)
    private val commitAcknowledged = AtomicBoolean(false)

    fun markCommitted() {
        commitAcknowledged.set(true)
    }

    fun isCommitted(): Boolean = commitAcknowledged.get()

    /**
     * Fermeture native (xdg_toplevel.close) : non-interceptable et sans
     * CloseRequested (DESIGN L914) — la déconnexion compositor est signalée
     * au runtime comme fermeture native.
     */
    fun nativeClosed() {
        onNativeClosed(windowId)
    }

    /**
     * Fermeture programmatique demandée par le runtime: destruction ordonnée
     * de la fenêtre native puis signal de fermeture native au runtime.
     */
    fun requestProgrammaticClose(): OpenedWindowCloseOutcome {
        close()
        onNativeClosed(windowId)
        return OpenedWindowCloseOutcome.Accepted
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        window.close()
    }

    internal companion object {
        /**
         * Crée la fenêtre native et déroule le handshake xdg sur le thread
         * pompe: commit initial → configure → ack → buffer attaché → mappée.
         * La taille proposée est celle du spec (configure non-imposé = F1-4).
         */
        fun create(
            windowId: WindowId,
            factory: (width: Int, height: Int) -> WaylandNativeWindow,
            contentWidth: Int,
            contentHeight: Int,
            title: String,
            appId: String,
            onNativeClosed: (WindowId) -> Unit,
        ): WaylandWindowPeer {
            val window = factory(contentWidth, contentHeight)
            return WaylandWindowPeer(windowId, window, onNativeClosed).also { peer ->
                val configured = java.util.concurrent.CountDownLatch(1)
                window.onConfigure { _ -> configured.countDown() }
                window.setTitle(title)
                window.setAppId(appId)
                window.commitInitial()
                check(configured.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    "no xdg configure within 10s for $windowId"
                }
                window.ackLatestConfigure()
            }
        }
    }
}
