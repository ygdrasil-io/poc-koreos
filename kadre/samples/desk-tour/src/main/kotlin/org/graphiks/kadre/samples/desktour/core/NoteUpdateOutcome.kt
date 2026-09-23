package org.graphiks.kadre.samples.desktour.core

internal sealed interface NoteUpdateOutcome {
    data object Applied : NoteUpdateOutcome

    /** Le host a accepté la demande sans confirmer l'effet : ce n'est pas un succès observé. */
    data object Accepted : NoteUpdateOutcome

    data class PartiallyApplied(
        val applied: List<String>,
        val rejected: List<RejectedFieldPresentation>,
    ) : NoteUpdateOutcome

    data class Refused(val motif: String) : NoteUpdateOutcome
}
