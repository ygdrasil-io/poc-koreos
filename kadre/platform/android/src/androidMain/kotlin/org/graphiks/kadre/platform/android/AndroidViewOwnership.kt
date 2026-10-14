package org.graphiks.kadre.platform.android

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Registre d'ownership process-wide à double identité (roadmap §3.1 : « Réserver
 * atomiquement les identités Activity/View »). L'Activity et la View sont reservées
 * ensemble ou pas du tout ; la libération n'intervient qu'à la terminaison effective.
 * Les clés sont les identités natives (Activity instance, View instance) — jamais un
 * « handler courant ».
 *
 * La forme présentation (`attachKadreSecondaryDisplay`, Task 2 phase 11) y ajoute la clé
 * displayId PAR VALEUR : elle revendique le contentView (identité, la même table que les
 * formes existantes — une View ne peut pas servir deux attaches) ET l'écran cible, mais
 * JAMAIS l'identité de l'Activity — c'est ce qui autorise la coexistence session principale
 * + session présentation sur une même Activity.
 */
internal class AndroidViewOwnership {
    private val viewOwners = ConcurrentHashMap<Any, Unit>()
    private val activityOwners = ConcurrentHashMap<Any, Unit>()
    private val displayOwners = ConcurrentHashMap<Long, Unit>()

    fun isViewClaimed(viewIdentity: Any): Boolean = viewOwners.containsKey(viewIdentity)
    fun isActivityClaimed(activityIdentity: Any): Boolean = activityOwners.containsKey(activityIdentity)
    fun isDisplayClaimed(displayKey: Long): Boolean = displayOwners.containsKey(displayKey)

    /** Réservation atomique : les deux identités, ou rien (rollback intégral en cas d'échec partiel). */
    fun reserve(activityIdentity: Any?, viewIdentity: Any): Boolean {
        if (viewOwners.putIfAbsent(viewIdentity, Unit) != null) return false
        if (activityIdentity != null && activityOwners.putIfAbsent(activityIdentity, Unit) != null) {
            viewOwners.remove(viewIdentity)
            return false
        }
        return true
    }

    /** Libération idempotente à la terminaison effective. */
    fun release(activityIdentity: Any?, viewIdentity: Any) {
        viewOwners.remove(viewIdentity)
        if (activityIdentity != null) activityOwners.remove(activityIdentity)
    }

    /**
     * Réservation atomique de la forme présentation : le contentView (identité) ET le
     * displayId (valeur) — l'un ou l'autre déjà pris, RIEN n'est réservé. L'Activity n'est
     * jamais touchée ici, quand bien même la demande passerait pour la même.
     */
    fun reservePresentation(viewIdentity: Any, displayKey: Long): Boolean {
        if (viewOwners.putIfAbsent(viewIdentity, Unit) != null) return false
        if (displayOwners.putIfAbsent(displayKey, Unit) != null) {
            viewOwners.remove(viewIdentity)
            return false
        }
        return true
    }

    /** Libération présentation — idempotente, à la terminaison effective et au rollback. */
    fun releasePresentation(viewIdentity: Any, displayKey: Long) {
        viewOwners.remove(viewIdentity)
        displayOwners.remove(displayKey)
    }

    /** Le registre est-il vide — la vérification zéro-résidu des tests hôtes (AND-001). */
    fun isEmpty(): Boolean =
        viewOwners.isEmpty() && activityOwners.isEmpty() && displayOwners.isEmpty()
}

/**
 * Réservation vivante de la forme présentation. Le rollback intégral est le seul chemin
 * d'échec ; [release] n'est appelé qu'à la terminaison effective de la session (tous chemins,
 * via le sessionObserver du runtime — idempotent).
 */
internal class AndroidPresentationReservation private constructor(
    private val ownership: AndroidViewOwnership,
    val viewIdentity: Any,
    val displayKey: Long,
) {
    private val open = AtomicBoolean(true)

    /** Relectures post-installation (course validation/retrait). */
    fun isStillHeld(): Boolean = open.get() &&
        ownership.isViewClaimed(viewIdentity) &&
        ownership.isDisplayClaimed(displayKey)

    /** À la terminaison effective — idempotent. */
    fun release() {
        if (!open.getAndSet(false)) return
        ownership.releasePresentation(viewIdentity, displayKey)
    }

    /** Échec avant session vivante : restaure exactement l'état d'avant la demande. */
    fun rollback() = release()

    companion object {
        /** `null` si l'une des deux clés est déjà prise (la denial est déjà calculée). */
        fun acquire(
            ownership: AndroidViewOwnership,
            viewIdentity: Any,
            displayKey: Long,
        ): AndroidPresentationReservation? =
            if (ownership.reservePresentation(viewIdentity, displayKey)) {
                AndroidPresentationReservation(ownership, viewIdentity, displayKey)
            } else {
                null
            }
    }
}
