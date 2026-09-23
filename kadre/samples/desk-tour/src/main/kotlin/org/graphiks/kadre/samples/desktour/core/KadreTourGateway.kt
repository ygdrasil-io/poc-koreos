package org.graphiks.kadre.samples.desktour.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.window.Window
import org.graphiks.kadre.window.WindowRequest
import org.graphiks.kadre.window.requestWindow

internal class KadreTourGateway(private val scope: KadreScope) : TourGateway {
    override fun lifecycleSummary(): Flow<String> =
        scope.lifecycle.state.map { state ->
            "Session : ${state.attachment} / ${state.visibility} / ${state.activation}"
        }

    override fun observeWindow(window: Window): Flow<DeskTourWindow> =
        combine(window.state, window.surface.state) { windowState, surfaceState ->
            DeskTourWindow(
                title = windowState.title,
                focusLabel = surfaceState.focus.name,
                logicalWidth = surfaceState.logicalSize.width,
                logicalHeight = surfaceState.logicalSize.height,
                physicalWidth = surfaceState.physicalSize.width,
                physicalHeight = surfaceState.physicalSize.height,
            )
        }

    override fun createNoteAvailability(): CapabilityPresentation =
        present(scope.windows.state.value.capabilities.requestWindow)

    override suspend fun requestNoteWindow(): KadreResult<WindowRequest> =
        scope.windows.requestWindow { title = "Notes" }
}
