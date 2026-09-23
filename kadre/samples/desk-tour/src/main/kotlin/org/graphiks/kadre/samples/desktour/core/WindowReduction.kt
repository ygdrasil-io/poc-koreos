package org.graphiks.kadre.samples.desktour.core

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.message
import org.graphiks.kadre.window.WindowRequestOutcome

internal fun TourStore.recordWindowRequestOutcome(id: ActionCorrelationId, outcome: WindowRequestOutcome) {
    when (outcome) {
        is WindowRequestOutcome.OpenedHere -> resolve(id, ActivityStatus.Succeeded)
        is WindowRequestOutcome.OpenedInNewSession ->
            resolve(id, ActivityStatus.Unavailable, motif = "La fenêtre a été ouverte dans une autre session.")
        is WindowRequestOutcome.Rejected ->
            resolve(id, ActivityStatus.Unavailable, motif = outcome.failure.userMotif())
        WindowRequestOutcome.Cancelled -> resolve(id, ActivityStatus.Cancelled)
        WindowRequestOutcome.RequesterDetached -> resolve(id, ActivityStatus.Cancelled)
    }
}

internal fun KadreFailure.userMotif(): String = when (this) {
    is KadreFailure.Unsupported -> "Non pris en charge par le host courant."
    is KadreFailure.PermissionDenied -> "Cette action nécessite une permission non accordée."
    is KadreFailure.TemporarilyUnavailable -> "Temporairement indisponible${if (retryable) ", réessayable" else ""}."
    is KadreFailure.InvalidRequest -> "Requête invalide${field?.let { " ($it)" } ?: ""}."
    else -> message
}
