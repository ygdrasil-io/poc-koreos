package org.graphiks.kadre.samples.desktour.core

import kotlinx.coroutines.flow.Flow
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.window.Window

/**
 * Seule frontière autorisée à appeler l'API publique Kadre. Le reste de `core` ne
 * connaît que ce contrat, ce qui permet aux tests d'injecter un faux (spec §7, §8.6).
 * En particulier, `core` ne manipule jamais un `Window` : le gateway les détient.
 */
internal interface TourGateway {
    fun lifecycleSummary(): Flow<String>
    fun observeWindow(window: Window): Flow<DeskTourWindow>
    fun createNoteAvailability(): CapabilityPresentation
    suspend fun openNote(): NoteOpenOutcome

    /** Rendu à l'hôte, seul composant autorisé à monter du Compose (spec §2.5). */
    fun noteWindow(key: NoteKey): Window?
    fun noteControls(key: NoteKey): NoteControls
    fun observeNote(key: NoteKey): Flow<DeskTourNote>

    suspend fun renameNote(key: NoteKey, title: String): NoteUpdateOutcome
    suspend fun requestNoteAttention(key: NoteKey): NoteUpdateOutcome
    suspend fun toggleNoteDecorations(key: NoteKey): NoteUpdateOutcome
    suspend fun closeNote(key: NoteKey): NoteUpdateOutcome

    /** Les notes dont la fenêtre a demandé à se fermer et dont la fermeture a été acceptée. */
    fun observeNoteCloseRequests(): Flow<NoteKey>

    fun observeDisplays(): Flow<DisplayPresentation>
    fun displayAccessAvailability(): CapabilityPresentation
    suspend fun requestDisplayAccess(): DisplayPresentation

    fun observeInput(surface: HostSurface): Flow<InputPresentation>

    fun observeDevices(): Flow<DevicePresentation>
    fun observeCapture(): Flow<CapturePresentation>

    /** La saisie de texte par le contrat public de Kadre, et non par la voie manuelle du bridge. */
    fun textInputAvailability(): CapabilityPresentation
    fun observeTextInput(): Flow<TextInputPresentation>
    suspend fun openTextInput(): TextInputOutcome
    suspend fun closeTextInput()
}

internal sealed interface TextInputOutcome {
    data object Opened : TextInputOutcome
    data class Refused(val motif: String) : TextInputOutcome
}
