package org.graphiks.kadre.samples.desktour.core

/**
 * Quels contrôles la fenêtre de cette note accepte, d'après ses propres capabilities,
 * et pourquoi les autres sont indisponibles (spec §5 ligne 115).
 */
internal data class NoteControls(
    val canRename: Boolean,
    val canRequestAttention: Boolean,
    val canChangeDecorations: Boolean,
    val canClose: Boolean,
    val motifs: List<String> = emptyList(),
)

internal data class DeskTourNote(
    val key: NoteKey,
    val title: String,
    val capabilities: NoteControls,
    val mountFailed: Boolean = false,
) {
    fun withMountFailure(): DeskTourNote = copy(mountFailed = true)
}
