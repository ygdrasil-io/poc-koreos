package org.graphiks.kadre.samples.desktour.core

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.window.WindowRequestOutcome

internal fun TourStore.recordWindowRequestOutcome(id: ActionCorrelationId, outcome: WindowRequestOutcome) {
    when (outcome) {
        is WindowRequestOutcome.OpenedHere -> resolve(id, ActivityStatus.Succeeded)
        is WindowRequestOutcome.OpenedInNewSession ->
            resolve(id, ActivityStatus.Unavailable, motif = "La fenêtre a été ouverte dans une autre session.")
        is WindowRequestOutcome.Rejected ->
            resolve(id, ActivityStatus.Rejected, motif = outcome.failure.userMotif())
        WindowRequestOutcome.Cancelled -> resolve(id, ActivityStatus.Cancelled)
        WindowRequestOutcome.RequesterDetached -> resolve(id, ActivityStatus.Cancelled)
    }
}

/**
 * Phrases destinées à l'utilisateur final. Aucune ne réutilise `KadreFailure.message`, qui est
 * du vocabulaire de développeur en anglais (spec §8.4 : les états indisponibles et les erreurs
 * doivent être compréhensibles sans vocabulaire de développeur).
 */
internal fun KadreFailure.userMotif(): String = when (this) {
    is KadreFailure.Unsupported -> "Non pris en charge par le host courant."
    is KadreFailure.PermissionDenied -> "Cette action nécessite une permission non accordée."
    is KadreFailure.UserCancelled -> "Action annulée."
    is KadreFailure.TemporarilyUnavailable -> if (retryable) {
        "Action temporairement indisponible, réessayable."
    } else {
        "Action temporairement indisponible."
    }
    is KadreFailure.InvalidRequest -> field?.let { "Requête invalide ($it)." } ?: "Requête invalide."
    is KadreFailure.AlreadyInUse -> "Cette ressource est déjà utilisée."
    is KadreFailure.Closed -> "Cette ressource est fermée."
    is KadreFailure.ResourceLimitExceeded -> "La limite de cette ressource est atteinte."
    is KadreFailure.SourceOverflow -> "Trop d'événements en attente pour cette ressource."
    is KadreFailure.StaleRevision -> "L'état a changé entre-temps ; l'action n'a pas été appliquée."
    is KadreFailure.InteractionRequired -> "Cette action nécessite une interaction préalable."
    is KadreFailure.UnsupportedPolicy -> "La politique de session refuse cette action."
    KadreFailure.ParentScopeCancelled -> "L'action a été interrompue par la fin de la session."
    is KadreFailure.ShutdownTimedOut -> "L'arrêt de la session a dépassé le délai imparti."
    is KadreFailure.SourceLost -> "La source observée a été perdue."
    KadreFailure.ApplicationFailure -> "Une erreur interne est survenue."
    is KadreFailure.PlatformFailure -> "Une erreur interne est survenue."
}
