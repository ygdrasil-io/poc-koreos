package org.graphiks.kadre.samples.desktour.core

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.window.Window
import org.graphiks.kadre.window.WindowProperty
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowUpdate
import org.graphiks.kadre.window.WindowUpdateOutcome
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

    override fun noteWindow(key: NoteKey): Window? = notes[key]

    override fun noteControls(key: NoteKey): NoteControls = notes[key]?.let { window ->
        val capabilities = window.capabilities.value
        NoteControls(
            canRename = capabilities.title is Capability.Supported,
            canRequestAttention = capabilities.attention is Capability.Supported,
            canChangeDecorations = capabilities.decorations is Capability.Supported,
            canClose = true,
        )
    } ?: NoteControls(false, false, false, false)

    override fun observeNote(key: NoteKey): Flow<DeskTourNote> = notes[key]?.let { window ->
        combine(window.state, window.surface.state) { state, _ ->
            DeskTourNote(key, state.title, noteControls(key))
        }
    } ?: flowOf()

    override suspend fun renameNote(key: NoteKey, title: String): NoteUpdateOutcome {
        val window = notes[key] ?: return NoteUpdateOutcome.Refused("Cette note n'existe plus.")
        return when (val result = window.apply(WindowUpdate(title = PropertyChange.Set(title)))) {
            is KadreResult.Failure -> NoteUpdateOutcome.Refused(result.reason.userMotif())
            is KadreResult.Success -> when (val outcome = result.value) {
                is WindowUpdateOutcome.Applied, is WindowUpdateOutcome.Accepted -> NoteUpdateOutcome.Applied
                is WindowUpdateOutcome.PartiallyApplied -> NoteUpdateOutcome.PartiallyApplied(
                    applied = WindowProperty.entries
                        .filterNot { property -> outcome.rejected.any { it.field == property } }
                        .map { it.readableName() },
                    rejected = outcome.rejected.map {
                        RejectedFieldPresentation(it.field.readableName(), it.failure.userMotif())
                    },
                )
            }
        }
    }
}
