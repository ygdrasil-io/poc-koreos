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

/** Ordinaux androidx Lifecycle.State partagés avec la couture d'attach présentation. */
internal const val LIFECYCLE_STARTED = 3
internal const val LIFECYCLE_RESUMED = 4

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

/**
 * Entrée pure de l'échelle d'admission de la forme PRÉSENTATION (`attachKadreSecondaryDisplay`).
 * Toutes les valeurs sont observables avant toute mutation ; les faits SDK y arrivent déjà
 * traduits (le contentView par son drapeau d'attachement, l'écran par sa clé et sa
 * résolvabilité — la vérification `displayManager.getDisplay(displayId) != null` vit dans
 * l'overload public, qui la calcule AVANT l'échelle).
 */
internal class AndroidPresentationAttachRequest(
    val isMainThread: Boolean,
    val contentView: Any, // identité native du contentView — clé de réservation
    val isContentViewAttachedToWindow: Boolean, // doit être FAUX : Kadre monte la vue dans la présentation
    val displayKey: Long, // displayId SDK — clé de réservation PAR VALEUR
    val isDisplayResolvable: Boolean, // displayManager.getDisplay(displayKey) != null
    val ownerState: Int, // androidx Lifecycle.State ordinal de l'Activity hôte
    val parentScopeJob: Any?, // le Job du scope, ou null s'il n'y en a pas
    val parentScopeJobIsActive: Boolean,
)

/**
 * Échelle normative de la forme présentation (Task 2, phase 11) : thread → scope →
 * contentView LIBRE → écran résolvable → owner détruit → conflit (contentView identité OU
 * displayId valeur). Pure : aucune mutation, le refus éventuel porte le `KadreFailure` exact
 * d'`attachKadreSecondaryDisplay` — les mêmes familles que l'échelle des formes Activity/View.
 *
 * Deux inversions par rapport au rung « view » des formes existantes : le contentView doit
 * être DÉTACHÉ (Kadre le pose dans la présentation — un contentView déjà monté serait volé à
 * sa fenêtre), et la validité de l'écran est un fait lu avant l'échelle. La forme présentation
 * ne réserve JAMAIS l'identité de l'Activity : c'est ce qui autorise la coexistence session
 * principale + session présentation sur une même Activity — le conflit ne porte que sur le
 * contentView et le displayId.
 */
internal fun evaluateAndroidPresentationAttach(
    request: AndroidPresentationAttachRequest,
    ownership: AndroidViewOwnership,
): AndroidAttachDenial? {
    if (!request.isMainThread) {
        // Le rung existant : la violation de thread est une erreur de programmation.
        throw IllegalStateException("attachKadreSecondaryDisplay must be called on the Android main thread")
    }
    if (request.parentScopeJob == null) return AndroidAttachDenial(KadreFailure.InvalidRequest("parentScope"))
    if (!request.parentScopeJobIsActive) return AndroidAttachDenial(KadreFailure.ParentScopeCancelled)
    if (request.isContentViewAttachedToWindow) {
        return AndroidAttachDenial(KadreFailure.InvalidRequest("contentView"))
    }
    if (!request.isDisplayResolvable) {
        return AndroidAttachDenial(KadreFailure.InvalidRequest("presentationDisplay"))
    }
    if (request.ownerState == LIFECYCLE_DESTROYED) {
        return AndroidAttachDenial(KadreFailure.Closed(KadreResourceKind.Host))
    }
    if (ownership.isViewClaimed(request.contentView) || ownership.isDisplayClaimed(request.displayKey)) {
        return AndroidAttachDenial(KadreFailure.AlreadyInUse(KadreResourceKind.Host))
    }
    return null
}
