package org.graphiks.kadre.samples.desktour.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
    override fun noteControls(key: NoteKey): NoteControls = NoteControls(false, false, false, false)
    override fun observeNote(key: NoteKey): Flow<DeskTourNote> = flowOf()
}
