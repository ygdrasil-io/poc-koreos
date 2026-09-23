package org.graphiks.kadre.samples.desktour.core

/** Quels contrôles la fenêtre de cette note accepte, d'après ses propres capabilities. */
internal data class NoteControls(
    val canRename: Boolean,
    val canRequestAttention: Boolean,
    val canChangeDecorations: Boolean,
    val canClose: Boolean,
)

internal data class DeskTourNote(
    val key: NoteKey,
    val title: String,
    val capabilities: NoteControls,
)
