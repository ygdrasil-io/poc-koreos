package org.graphiks.kadre.samples.desktour.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

internal class ActionDispatcher(
    private val store: TourStore,
    private val gateway: TourGateway,
) {
    private val noteInFlight = AtomicBoolean(false)

    suspend fun createNote() {
        // Spec §5 ligne 115 : une capacité absente désactive l'action. Elle ne doit ni
        // remplir le journal ni atteindre Kadre.
        if (!gateway.createNoteAvailability().enabled) return
        // Un double clic ne doit ni doubler le journal ni ouvrir une seconde fenêtre.
        if (!noteInFlight.compareAndSet(false, true)) return
        try {
            val id = store.admit(ApiDetailKey.CreateFloatingNote.userAction, "WindowManager.requestWindow")
            try {
                val (status, motif) = reduceNoteOutcome(gateway.openNote())
                store.resolve(id, status, motif)
            } catch (cancellation: CancellationException) {
                withContext(NonCancellable) { store.resolve(id, ActivityStatus.Cancelled) }
                throw cancellation
            }
        } finally {
            noteInFlight.set(false)
        }
    }

    suspend fun renameNote(key: NoteKey, title: String) {
        if (!gateway.noteControls(key).canRename) return
        val id = store.admit(ApiDetailKey.ModifyWindow.userAction, "Window.apply")
        when (val outcome = gateway.renameNote(key, title)) {
            NoteUpdateOutcome.Applied -> store.resolve(id, ActivityStatus.Succeeded)
            is NoteUpdateOutcome.PartiallyApplied -> store.resolve(
                id,
                ActivityStatus.Succeeded,
                "Appliqué : ${outcome.applied.joinToString()}. " +
                    "Refusé : ${outcome.rejected.joinToString { it.fieldLabel }}.",
            )
            is NoteUpdateOutcome.Refused -> store.resolve(id, ActivityStatus.Rejected, outcome.motif)
        }
    }
}
