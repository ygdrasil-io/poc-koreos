package org.graphiks.kadre.platform.android

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind

/** Forme d'attach : Activity (surfaceView fournie) ou View seule. */
internal enum class AndroidAttachForm { Activity, View }

/** Entrée pure de l'échelle d'admission — toutes les valeurs sont observables avant toute mutation. */
internal class AndroidAttachRequest(
    val form: AndroidAttachForm,
    val isMainThread: Boolean,
    val view: Any, // identité native de la View
    val isViewAttachedToWindow: Boolean,
    val viewRootIdentity: Any?, // rootView de la View (forme Activity : comparé au decor)
    val activityIdentity: Any?, // identité native de l'Activity (null pour la forme View)
    val decorRootIdentity: Any?, // window.decorView de l'Activity (null pour la forme View)
    val ownerState: Int, // androidx Lifecycle.State ordinal ou -1 si absent
    val parentScopeJob: Any?, // le Job du scope, ou null s'il n'y en a pas
    val parentScopeJobIsActive: Boolean,
)

/** Refus pur de l'échelle — [failure] est le KadreFailure exact retourné par attachKadre. */
internal class AndroidAttachDenial(val failure: KadreFailure)

private const val LIFECYCLE_DESTROYED = 0
private const val LIFECYCLE_RESUMED = 4

/**
 * Échelle normative : thread → scope → view/decor → owner détruit → conflit d'identité.
 * Pure : aucune mutation, le refus éventuel porte le KadreFailure exact d'`attachKadre`.
 */
internal fun evaluateAndroidAttach(
    request: AndroidAttachRequest,
    ownership: AndroidViewOwnership,
): AndroidAttachDenial? {
    if (!request.isMainThread) {
        throw IllegalStateException("attachKadre must be called on the Android main thread")
    }
    if (request.parentScopeJob == null) return AndroidAttachDenial(KadreFailure.InvalidRequest("parentScope"))
    if (!request.parentScopeJobIsActive) return AndroidAttachDenial(KadreFailure.ParentScopeCancelled)
    if (!request.isViewAttachedToWindow) {
        val field = if (request.form == AndroidAttachForm.Activity) "surfaceView" else "view"
        return AndroidAttachDenial(KadreFailure.InvalidRequest(field))
    }
    if (request.form == AndroidAttachForm.Activity) {
        if (request.viewRootIdentity !== request.decorRootIdentity) {
            return AndroidAttachDenial(KadreFailure.InvalidRequest("surfaceView"))
        }
    }
    if (request.ownerState == LIFECYCLE_DESTROYED) {
        // Aucun field "lifecycleOwner" au registre §1.1 : un owner détruit est un host fermé.
        return AndroidAttachDenial(KadreFailure.Closed(KadreResourceKind.Host))
    }
    if (request.form == AndroidAttachForm.Activity &&
        request.activityIdentity != null &&
        ownership.isActivityClaimed(request.activityIdentity)
    ) {
        return AndroidAttachDenial(KadreFailure.AlreadyInUse(KadreResourceKind.Host))
    }
    if (ownership.isViewClaimed(request.view)) {
        return AndroidAttachDenial(KadreFailure.AlreadyInUse(KadreResourceKind.Host))
    }
    return null
}
