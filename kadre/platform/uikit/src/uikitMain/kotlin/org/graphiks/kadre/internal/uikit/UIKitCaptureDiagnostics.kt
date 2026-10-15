package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock

/**
 * Le canal de diagnostic CAPTURE de la couture uikit/tvOS (phase 9, Task 6) — le miroir
 * capture du canal d'attach ([org.graphiks.kadre.platform.uikit.UIKitAttachDiagnostics],
 * le pattern des compteurs process-wide des phases précédentes) : les comptes que le
 * driver Swift lit en DELTAS pour prouver le retour au repos des cycles capture
 * (scénario `uikit-capture-frames-release-bounded`, l'arrêt exactement une fois, le
 * gel des livraisons après le terminal).
 *
 * Les trois faits comptés, chacun à SON site de production :
 * - **réservations surface ouvertes** — le port, au succès de `surfaceReservation`
 *   (l'admission réelle d'un bras renderer) ;
 * - **réservations surface terminées** — la déposition `onEnded` du slot du port
 *   (exactement une fois par réservation, le close du port compris — ruling 4) ;
 *   `ouvertes − terminées` = les réservations EN VOL, le retour à zéro au repos ;
 * - **frames surface livrées** — la boucle de livraison de la pompe, au SEUL appel
 *   listener (`onFrame`) — le gel du compteur après le terminal est l'assertion
 *   « zéro frame après l'arrêt » du driver.
 *
 * Thread-safe (les sites comptent depuis les dispatchers Kotlin et le fil principal),
 * interne au module — la surface lisible du driver passe par
 * [org.graphiks.kadre.platform.uikit.KadreUikitSurfaceDriver].
 */
internal object UIKitCaptureDiagnostics {
    private val lock = RuntimeLock()
    private var surfaceReservationsOpened = 0L
    private var surfaceReservationsEnded = 0L
    private var surfaceFramesDelivered = 0L

    /** Le port a ADMIS une réservation surface (l'ouverture réelle d'un bras renderer). */
    fun surfaceReservationOpened() {
        lock.withLock { surfaceReservationsOpened += 1 }
    }

    /** La déposition `onEnded` d'UNE réservation surface (exactement une fois par vie). */
    fun surfaceReservationEnded() {
        lock.withLock { surfaceReservationsEnded += 1 }
    }

    /** La pompe a livré UNE frame au listener (le seul chemin listener de la pompe). */
    fun surfaceFrameDelivered() {
        lock.withLock { surfaceFramesDelivered += 1 }
    }

    /** L'instantané des trois comptes — les deltas de test comparent des instantanés. */
    fun snapshot(): Snapshot = lock.withLock {
        Snapshot(
            surfaceReservationsOpened = surfaceReservationsOpened,
            surfaceReservationsEnded = surfaceReservationsEnded,
            surfaceFramesDelivered = surfaceFramesDelivered,
        )
    }

    /** Les trois comptes d'UN instant — une donnée immuable, comparable champ à champ. */
    data class Snapshot(
        val surfaceReservationsOpened: Long,
        val surfaceReservationsEnded: Long,
        val surfaceFramesDelivered: Long,
    ) {
        /** Les réservations en vol — zéro attendu au repos de tout cycle propre. */
        val surfaceReservationsInFlight: Long
            get() = surfaceReservationsOpened - surfaceReservationsEnded
    }
}
