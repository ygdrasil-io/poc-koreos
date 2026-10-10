package org.graphiks.kadre.platform.android

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Réservation vivante d'un attach. Le rollback intégral est le seul chemin d'échec :
 * [release] n'est appelé qu'à la terminaison effective de la session (tous chemins).
 */
internal class AndroidAttachReservation private constructor(
    private val ownership: AndroidViewOwnership,
    val activityIdentity: Any?,
    val viewIdentity: Any,
) {
    private val open = AtomicBoolean(true)

    /** Relectures post-installation (course validation/détachement). */
    fun isStillHeld(): Boolean = open.get() &&
        ownership.isViewClaimed(viewIdentity) &&
        (activityIdentity == null || ownership.isActivityClaimed(activityIdentity))

    /** À la terminaison effective — idempotent. */
    fun release() {
        if (!open.getAndSet(false)) return
        ownership.release(activityIdentity, viewIdentity)
    }

    /** Échec avant session vivante : restaure exactement l'état d'avant la demande. */
    fun rollback() = release()

    companion object {
        /** `null` si l'échelle d'admission ou la réservation refuse (la denial est déjà calculée). */
        fun acquire(
            ownership: AndroidViewOwnership,
            activityIdentity: Any?,
            viewIdentity: Any,
        ): AndroidAttachReservation? =
            if (ownership.reserve(activityIdentity, viewIdentity)) {
                AndroidAttachReservation(ownership, activityIdentity, viewIdentity)
            } else {
                null
            }
    }
}
