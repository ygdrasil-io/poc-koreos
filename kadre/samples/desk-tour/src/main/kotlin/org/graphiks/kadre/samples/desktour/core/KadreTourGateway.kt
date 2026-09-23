package org.graphiks.kadre.samples.desktour.core

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.window.Window
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.requestWindow

internal class KadreTourGateway(private val scope: KadreScope) : TourGateway {
    private val notes = mutableMapOf<NoteKey, Window>()
    private val nextKey = AtomicLong(0L)

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

    override suspend fun openNote(): NoteOpenOutcome {
        val request = when (val result = scope.windows.requestWindow { title = "Notes" }) {
            is KadreResult.Success -> result.value
            is KadreResult.Failure -> return noteOpenOutcomeFor(result.reason)
        }
        return when (val outcome = request.await()) {
            is WindowRequestOutcome.OpenedHere -> NoteOpenOutcome.Opened(
                NoteKey(nextKey.incrementAndGet()).also { notes[it] = outcome.window },
            )
            is WindowRequestOutcome.Rejected -> noteOpenOutcomeFor(outcome.failure)
            is WindowRequestOutcome.OpenedInNewSession -> NoteOpenOutcome.OpenedElsewhere
            WindowRequestOutcome.Cancelled -> NoteOpenOutcome.Cancelled
            WindowRequestOutcome.RequesterDetached -> NoteOpenOutcome.Cancelled
        }
    }
}
