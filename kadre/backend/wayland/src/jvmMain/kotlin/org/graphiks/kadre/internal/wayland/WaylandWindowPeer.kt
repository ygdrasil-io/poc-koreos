package org.graphiks.kadre.internal.wayland

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.OpenedWindowCloseOutcome
import org.graphiks.kadre.internal.runtime.RuntimeDesktopNativeWindowHandle
import org.graphiks.kadre.internal.runtime.RuntimeDesktopWindowHandleAccess
import org.graphiks.kadre.internal.runtime.WindowPeerOwner
import org.graphiks.kadre.window.WindowId

/**
 * Pair d'une fenêtre Wayland réelle : état minimal côté backend, lease
 * `withDesktopHandle` (adresses valides uniquement pendant le callback,
 * refusées dès la fermeture admise — D2) et destruction ordonnée
 * (toplevel → xdg_surface → surface → buffer). Aucun second snapshot
 * public : le runtime reste l'autorité d'état.
 */
internal class WaylandWindowPeer(
    val windowId: WindowId,
    val window: WaylandNativeWindow,
    private val onNativeClosed: (WindowId) -> Unit,
) : WindowPeerOwner, RuntimeDesktopWindowHandleAccess, AutoCloseable {

    private val closed = AtomicBoolean(false)
    private val commitAcknowledged = AtomicBoolean(false)
    private val activeHandleLeases = AtomicInteger(0)

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

    /**
     * Lease des adresses natives (display + surface) pour un renderer
     * externe: les adresses ne sont valides que pendant l'exécution de
     * [block]; toute nouvelle lease est refusée dès que la fenêtre est
     * fermée (D2 — course lease/close: le lease est invalidé avant la
     * destruction des proxies).
     */
    override suspend fun <R> withDesktopHandle(
        block: (RuntimeDesktopNativeWindowHandle) -> R,
    ): KadreResult<R> {
        val previous = activeHandleLeases.incrementAndGet()
        try {
            if (closed.get() || previous <= 0) {
                return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
            }
            val (display, surface) = window.nativeAddresses
            return KadreResult.Success(
                block(RuntimeDesktopNativeWindowHandle.Wayland(display.toULong(), surface.toULong())),
            )
        } finally {
            activeHandleLeases.decrementAndGet()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        // D2/F3: les nouvelles leases sont refusées dès l'entrée (closed=true
        // lu par withDesktopHandle) — les leases actives sont courtes et
        // bornées au thread pompe; le destroy suit immédiatement.
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
