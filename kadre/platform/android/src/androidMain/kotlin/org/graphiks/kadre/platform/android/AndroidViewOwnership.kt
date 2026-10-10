package org.graphiks.kadre.platform.android

import java.util.concurrent.ConcurrentHashMap

/**
 * Registre d'ownership process-wide à double identité (roadmap §3.1 : « Réserver
 * atomiquement les identités Activity/View »). L'Activity et la View sont reservées
 * ensemble ou pas du tout ; la libération n'intervient qu'à la terminaison effective.
 * Les clés sont les identités natives (Activity instance, View instance) — jamais un
 * « handler courant ».
 */
internal class AndroidViewOwnership {
    private val viewOwners = ConcurrentHashMap<Any, Unit>()
    private val activityOwners = ConcurrentHashMap<Any, Unit>()

    fun isViewClaimed(viewIdentity: Any): Boolean = viewOwners.containsKey(viewIdentity)
    fun isActivityClaimed(activityIdentity: Any): Boolean = activityOwners.containsKey(activityIdentity)

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
}
