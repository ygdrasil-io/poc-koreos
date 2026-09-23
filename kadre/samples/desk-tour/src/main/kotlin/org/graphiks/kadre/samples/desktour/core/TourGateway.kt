package org.graphiks.kadre.samples.desktour.core

import kotlinx.coroutines.flow.Flow
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.window.Window
import org.graphiks.kadre.window.WindowRequest

/**
 * Seule frontière autorisée à appeler l'API publique Kadre. Le reste de `core` ne
 * connaît que ce contrat, ce qui permet aux tests d'injecter un faux (spec §7, §8.6).
 */
internal interface TourGateway {
    fun lifecycleSummary(): Flow<String>
    fun observeWindow(window: Window): Flow<DeskTourWindow>
    suspend fun requestNoteWindow(): KadreResult<WindowRequest>
}
