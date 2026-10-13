package org.graphiks.kadre.integration.compose

/**
 * La machine à états PURE du binding compose (roadmap §9) — create/keep/close/recreate,
 * pilotée par les seuls événements de cycle de vie de la vue hôte. Aucun type Android ni
 * Compose : la classe est testable sans compose runtime (les tests hôtes la conduisent avec
 * des billets factices), et la wiring [KadreComposeHost] n'est que sa transcription.
 *
 * Les quatre transitions normatives :
 *  — **create** — [onSurfaceAttached] sur un créneau vide : la fabrique [create] rend le
 *    billet (la session) ; un refus (`null` — l'échelle P1 rend un `KadreResult.Failure`,
 *    jamais d'exception ordinaire) laisse le créneau ouvert et l'événement suivant retente.
 *  — **keep** — tout le reste ne touche pas au billet : la recomposition ne transporte
 *    AUCUN événement (la wiring garde le billet dans `remember`), et même un événement
 *    d'attach répété sur un créneau vivant ne recrée pas — le capture l'emporte.
 *  — **close** — [onHostDisposed] (la sortie de composition) : [close] reçoit le billet
 *    vivant (l'arrêt host demandé à la session) ; un créneau vide ne ferme rien.
 *  — **recreate** — [onSurfaceDetached] : le détachement de la vue est un terminal dont
 *    l'arrêt appartient au RUNTIME (le binding P1 propose `Stopped(HostDetached)`) — la
 *    machine ne double pas cet arrêt, elle rouvre seulement le créneau ; l'attach suivant
 *    recrée une session sur la même vue (la réservation P1 est libérée à la terminaison).
 *
 * La machine est volontairement aveugle à la policy et au content lambda : ce sont des
 * paramètres de CREATION capturés par la wiring — les changer au fil des recompositions ne
 * peut pas atteindre un billet vivant.
 *
 * @param T le billet du host — la [org.graphiks.kadre.application.KadreSession] en wiring
 *   réelle, un factice dans les tests.
 */
internal class KadreComposeBinding<T : Any>(
    private val create: () -> T?,
    private val close: (T) -> Unit,
) {

    /** Le billet vivant, s'il existe — le capture que la recomposition ne remplace pas. */
    internal var current: T? = null
        private set

    /** L'événement « la vue hôte est attachée à la fenêtre » — create sur créneau vide, keep sinon. */
    internal fun onSurfaceAttached() {
        if (current == null) current = create()
    }

    /** L'événement « détachement terminal » — le créneau rouvre, sans fermer (le runtime arrête). */
    internal fun onSurfaceDetached() {
        current = null
    }

    /** L'événement « sortie de composition » — fermeture explicite du billet vivant, s'il existe. */
    internal fun onHostDisposed() {
        current?.let(close)
        current = null
    }
}
