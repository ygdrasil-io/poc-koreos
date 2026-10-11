package org.graphiks.kadre.samples.desktour.core

import kotlinx.coroutines.flow.Flow
import org.graphiks.kadre.window.Window

/**
 * Le support de notes du host courant : qui crée les fenêtres de notes, qui les manipule,
 * qui les ferme. Sur le bureau, une note est une fenêtre Kadre ([Window]) ; sur le web, la
 * sonde l'a tranché (PROBE-NOTES.md, correction 2) : la validation de Kadre refuse une
 * fenêtre enfant dans le même document, donc la note web appartient à la démo — un div du
 * document avec son propre viewport Compose. Le gateway ne connaît que ce contrat, et le
 * reste de `core` ne connaît ni l'un ni l'autre : seul le host décide ce qu'est une note.
 *
 * L'implémentation bureau vit en jvmMain ([org.graphiks.kadre.samples.desktour.KadreNoteWindows],
 * fenêtres Kadre, code inchangé de l'ancien gateway) ; l'implémentation web (divs + viewports)
 * viendra avec le host web.
 */
internal interface NoteWindowHost {
    suspend fun openNote(): NoteOpenOutcome

    /** La fenêtre Kadre d'une note ouverte — point de montage du host bureau. */
    fun noteWindow(key: NoteKey): Window?

    fun noteControls(key: NoteKey): NoteControls
    fun observeNote(key: NoteKey): Flow<DeskTourNote>

    suspend fun renameNote(key: NoteKey, title: String): NoteUpdateOutcome
    suspend fun requestNoteAttention(key: NoteKey): NoteUpdateOutcome
    suspend fun toggleNoteDecorations(key: NoteKey): NoteUpdateOutcome
    suspend fun closeNote(key: NoteKey): NoteUpdateOutcome

    /** Les notes dont la fenêtre a demandé à se fermer et dont la fermeture a été acceptée. */
    fun observeNoteCloseRequests(): Flow<NoteKey>
}
