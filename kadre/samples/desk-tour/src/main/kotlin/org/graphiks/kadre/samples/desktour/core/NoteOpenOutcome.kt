package org.graphiks.kadre.samples.desktour.core

/** Identifiant de note propre à la démo : constructible dans les tests, contrairement à un WindowId. */
@JvmInline
internal value class NoteKey(val value: Long)

internal sealed interface NoteOpenOutcome {
    /** La note est publiée dès l'ouverture : sans cela, aucune note n'existe pour l'interface. */
    data class Opened(val note: DeskTourNote) : NoteOpenOutcome
    data class Refused(val motif: String) : NoteOpenOutcome
    data object Cancelled : NoteOpenOutcome
    data object OpenedElsewhere : NoteOpenOutcome
}
