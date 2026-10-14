package org.graphiks.kadre.internal.wayland

import java.util.concurrent.atomic.AtomicBoolean
import org.graphiks.kffi.posix.PosixWakeup

/** Opérations de pompe injectables : l'implémentation réelle enveloppe le pont, les tests O2 un fake. */
internal interface PumpOperations {
    fun dispatchPending(): Boolean
    fun prepareRead(): Int
    fun readEvents(): Boolean
    fun cancelRead()
    fun flush(): WaylandFlushOutcome

    /**
     * Poll bloquant sur le fd display + fd de wake (et POLLOUT si [watchWrite]).
     * Ne draine PAS le wake : c'est la pompe qui draine ([PosixWakeup.drain])
     * après [PollOutcome.WakeOnly], sinon un réveil non consommé resterait
     * lisible et la boucle tournerait à vide.
     */
    fun poll(wakeFd: Int, watchWrite: Boolean): PollOutcome
}

internal enum class PollOutcome { DisplayReady, WakeOnly, Interrupted, Lost }

internal enum class PumpStep { Dispatched, Rearm, ConnectionLost, Stopped }

/**
 * Pompe conforme au contrat `libwayland-client` (roadmap §3.3) : vider les
 * pending, préparer la lecture, flush, poll (display + wake), puis
 * `read_events` ou `cancel_read`. Un roundtrip ne s'exécute jamais pendant
 * une lecture préparée (la négociation a lieu avant la boucle) ; `EINTR`
 * conserve la lecture préparée ; l'arrêt demandé depuis un autre thread est
 * réveillé par le wake et annule proprement la lecture.
 */
internal class WaylandEventPump(
    private val ops: PumpOperations,
    private val wake: PosixWakeup,
) {
    private val stopRequested = AtomicBoolean(false)

    fun requestStop() {
        stopRequested.set(true)
        wake.signal()
    }

    fun stopRequested(): Boolean = stopRequested.get()

    fun runOnce(): PumpStep {
        if (stopRequested.get()) {
            return PumpStep.Stopped
        }
        if (!ops.dispatchPending()) {
            return PumpStep.ConnectionLost
        }
        if (stopRequested.get()) {
            return PumpStep.Stopped
        }
        if (ops.prepareRead() != 0) {
            // file de dispatch non vide : re-boucler immédiatement (sans
            // préparer une seconde lecture, interdit par libwayland)
            return PumpStep.Rearm
        }
        val watchWrite = ops.flush() == WaylandFlushOutcome.Eagain
        while (true) {
            if (stopRequested.get()) {
                ops.cancelRead()
                return PumpStep.Stopped
            }
            when (ops.poll(wake.readFd, watchWrite)) {
                PollOutcome.DisplayReady -> break
                // EINTR : la lecture préparée subsiste, on repoll — jamais un
                // second prepare_read.
                PollOutcome.Interrupted -> continue
                PollOutcome.WakeOnly -> {
                    if (stopRequested.get()) {
                        ops.cancelRead()
                        return PumpStep.Stopped
                    }
                    // Réveil applicatif hors arrêt : une action a été
                    // marshalée vers le thread pompe (création de fenêtre).
                    // La lecture préparée est annulée et le pas rendu à la
                    // boucle hôte — c'est elle qui draine la file d'actions ;
                    // repoller ici la priverait du marshall (le poll se
                    // rebloquerait sur un compositor silencieux).
                    wake.drain()
                    ops.cancelRead()
                    return PumpStep.Rearm
                }
                PollOutcome.Lost -> {
                    ops.cancelRead()
                    return PumpStep.ConnectionLost
                }
            }
        }
        if (stopRequested.get()) {
            ops.cancelRead()
            return PumpStep.Stopped
        }
        if (!ops.readEvents()) {
            return PumpStep.ConnectionLost
        }
        return PumpStep.Dispatched
    }
}
