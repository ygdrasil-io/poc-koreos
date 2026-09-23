package org.graphiks.kadre.samples.desktour.core

/** Identifiant de note propre à la démo : constructible dans les tests, contrairement à un WindowId. */
@JvmInline
internal value class NoteKey(val value: Long)

internal sealed interface NoteOpenOutcome {
    data class Opened(val key: NoteKey) : NoteOpenOutcome
    data class Refused(val motif: String) : NoteOpenOutcome
    data object Cancelled : NoteOpenOutcome
    data object OpenedElsewhere : NoteOpenOutcome
}

internal fun reduceNoteOutcome(outcome: NoteOpenOutcome): Pair<ActivityStatus, String?> = when (outcome) {
    is NoteOpenOutcome.Opened -> ActivityStatus.Succeeded to null
    is NoteOpenOutcome.Refused -> ActivityStatus.Rejected to outcome.motif
    NoteOpenOutcome.Cancelled -> ActivityStatus.Cancelled to null
    NoteOpenOutcome.OpenedElsewhere ->
        ActivityStatus.Unavailable to "La fenêtre a été ouverte dans une autre session."
}
