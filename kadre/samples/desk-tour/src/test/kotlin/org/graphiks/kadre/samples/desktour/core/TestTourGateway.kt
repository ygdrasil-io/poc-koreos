package org.graphiks.kadre.samples.desktour.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.window.Window

/**
 * Base des faux de test : chaque tâche n'implémente que ce qu'elle exerce, et l'interface
 * peut grandir sans que les tests des tâches antérieures cessent de compiler.
 */
internal open class TestTourGateway : TourGateway {
    override fun lifecycleSummary(): Flow<String> = flowOf()
    override fun observeWindow(window: Window): Flow<DeskTourWindow> = flowOf()
    override fun createNoteAvailability(): CapabilityPresentation = CapabilityPresentation(enabled = true)
    override suspend fun openNote(): NoteOpenOutcome = NoteOpenOutcome.Cancelled
    override fun noteWindow(key: NoteKey): Window? = null

    // Permissif par défaut : un faux laisse passer l'appel pour que le test exerce le code
    // visé ; les tests qui portent sur une capability absente écrasent cette valeur.
    override fun noteControls(key: NoteKey): NoteControls = NoteControls(true, true, true, true)
    override fun observeNote(key: NoteKey): Flow<DeskTourNote> = flowOf()
    override suspend fun renameNote(key: NoteKey, title: String): NoteUpdateOutcome = NoteUpdateOutcome.Applied
    override suspend fun requestNoteAttention(key: NoteKey): NoteUpdateOutcome = NoteUpdateOutcome.Applied
    override suspend fun toggleNoteDecorations(key: NoteKey): NoteUpdateOutcome = NoteUpdateOutcome.Applied
    override suspend fun closeNote(key: NoteKey): NoteUpdateOutcome = NoteUpdateOutcome.Applied
    override fun observeNoteCloseRequests(): Flow<NoteKey> = flowOf()

    // Permissive par défaut, comme `noteControls` : un faux laisse passer l'appel.
    override fun observeDisplays(): Flow<DisplayPresentation> = flowOf(DisplayPresentation.NeedsPermission)
    override fun displayAccessAvailability(): CapabilityPresentation =
        CapabilityPresentation(enabled = true, requestable = true)
    override suspend fun requestDisplayAccess(): DisplayPresentation = DisplayPresentation.NeedsPermission

    override fun observeInput(surface: HostSurface): Flow<InputPresentation> = flowOf()

    override fun observeDevices(): Flow<DevicePresentation> = flowOf(DevicePresentation.Unsupported)
    override fun observeCapture(): Flow<CapturePresentation> =
        flowOf(CapturePresentation("pas encore demandée", "pas encore demandée", canRequest = true))

    override fun textInputAvailability(): CapabilityPresentation =
        CapabilityPresentation(enabled = true, requestable = true)
    override fun observeTextInput(): Flow<TextInputPresentation> =
        flowOf(TextInputPresentation(open = false, stateLabel = "fermée", lastEvent = null))
    override suspend fun openTextInput(): TextInputOutcome = TextInputOutcome.Opened
    override suspend fun closeTextInput() = Unit
}
