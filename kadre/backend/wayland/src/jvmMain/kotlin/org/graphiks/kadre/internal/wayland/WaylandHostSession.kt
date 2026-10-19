package org.graphiks.kadre.internal.wayland

import org.graphiks.kffi.posix.PosixWakeup
import org.graphiks.kadre.diagnostics.KadreFailure

/** Échec avant session (tables F1 du design de tranche) — [code] est un identifiant stable ASCII. */
internal class WaylandSessionStartException(val code: String, cause: Throwable? = null) : Exception(cause)

/**
 * Session Wayland tranche 1 : connexion par nom de socket, négociation B-4,
 * pompe conforme et teardown ordonné (F3 : quiescence → proxies → flush final
 * → fermeture des fds, chaque owner idempotent).
 */
internal class WaylandHostSession private constructor(
    val bridge: WaylandNativeBridge,
    val wake: PosixWakeup,
    val pump: WaylandEventPump,
) : WindowActionQueue {

    private val windowActions = java.util.concurrent.ConcurrentLinkedQueue<() -> Unit>()

    /** Marshal d'une action vers le thread pompe (créations fenêtres). */
    override fun enqueue(action: () -> Unit) {
        windowActions.add(action)
        wake.signal()
    }

    internal fun asActionQueue(): WindowActionQueue = this

    /** Crée une fenêtre native (surface+xdg+toplevel+buffer) via la facade kffi. */
    fun createWindowing(width: Int, height: Int): WaylandNativeWindow =
        bridge.createWindowing(width, height)

    /** À appeler par la boucle de pompe avant chaque itération. */
    fun drainWindowActions() {
        while (true) {
            val action = windowActions.poll() ?: return
            runCatching { action() }.onFailure { failure -> println("wayland action failed: $failure") }
        }
    }

    internal companion object {
        fun start(
            bridgeFactory: (String?) -> WaylandNativeBridge,
            socketName: String?,
            env: WaylandProbeEnvironment,
            wakeFactory: () -> PosixWakeup = { PosixWakeup.open() },
        ): WaylandHostSession {
            // F1-4 : tant que KFFI-WAYLAND-001 est ouvert, un run avec fd
            // transmis échoue avant session, sans repli par nom de socket.
            if (!env.waylandSocket.isNullOrEmpty()) {
                throw WaylandSessionStartException("wayland-socket-unsupported")
            }
            val wake = try {
                wakeFactory()
            } catch (failure: Throwable) {
                throw WaylandSessionStartException("wakeup-unavailable", failure)
            }
            val bridge = try {
                bridgeFactory(socketName)
            } catch (failure: Throwable) {
                wake.close()
                if (failure is WaylandConnectException) {
                    throw WaylandSessionStartException(failure.code, failure)
                }
                throw WaylandSessionStartException("connect-unexpected", failure)
            }
            val negotiation = WaylandNegotiation.checkGlobals(bridge.globals())
            if (negotiation != null) {
                bridge.close()
                wake.close()
                val code = (negotiation as KadreFailure.PlatformFailure).code
                throw WaylandSessionStartException(code)
            }
            val pump = WaylandEventPump(BridgePumpOperations(bridge), wake)
            return WaylandHostSession(bridge, wake, pump)
        }
    }

    /** Teardown F3 : quiescence puis flush final (si la connexion vit) puis fermeture. */
    fun teardown() {
        if (!bridge.connectionLost()) {
            bridge.flush()
        }
        bridge.close()
        wake.close()
    }
}

/** Pont → pompe : les opérations réelles, sans état propre. */
internal class BridgePumpOperations(
    private val bridge: WaylandNativeBridge,
) : PumpOperations {
    override fun dispatchPending(): Boolean = bridge.dispatchPending()
    override fun prepareRead(): Int = bridge.prepareRead()
    override fun readEvents(): Boolean = bridge.readEvents()
    override fun cancelRead() = bridge.cancelRead()
    override fun flush(): WaylandFlushOutcome = bridge.flush()
    override fun poll(wakeFd: Int, watchWrite: Boolean): PollOutcome = bridge.pollEvents(wakeFd, watchWrite)
}
