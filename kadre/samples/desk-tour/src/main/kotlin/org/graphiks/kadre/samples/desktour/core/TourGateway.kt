package org.graphiks.kadre.samples.desktour.core

import kotlinx.coroutines.flow.Flow
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
}
