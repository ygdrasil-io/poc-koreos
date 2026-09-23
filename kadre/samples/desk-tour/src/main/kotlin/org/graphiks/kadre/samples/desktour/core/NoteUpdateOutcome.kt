package org.graphiks.kadre.samples.desktour.core

internal sealed interface NoteUpdateOutcome {
    data object Applied : NoteUpdateOutcome
    data class PartiallyApplied(
        val applied: List<String>,
        val rejected: List<RejectedFieldPresentation>,
    ) : NoteUpdateOutcome
    data class Refused(val motif: String) : NoteUpdateOutcome
}

internal fun NoteUpdateOutcome.toPresentation(): OutcomePresentation = when (this) {
    NoteUpdateOutcome.Applied -> OutcomePresentation(applied = listOf("Titre"), rejected = emptyList())
    is NoteUpdateOutcome.PartiallyApplied -> OutcomePresentation(applied, rejected)
    is NoteUpdateOutcome.Refused ->
        OutcomePresentation(emptyList(), listOf(RejectedFieldPresentation("Titre", motif)))
}
