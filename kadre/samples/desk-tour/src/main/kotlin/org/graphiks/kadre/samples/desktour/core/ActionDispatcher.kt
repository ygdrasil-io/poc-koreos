package org.graphiks.kadre.samples.desktour.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal class ActionDispatcher(
    private val store: TourStore,
    private val gateway: TourGateway,
) {
    private val noteInFlight = AtomicBoolean(false)
    private val closingNotes = ConcurrentHashMap.newKeySet<NoteKey>()

    suspend fun createNote() {
        // Spec §5 ligne 115 : une capacité absente désactive l'action. Elle ne doit ni
        // remplir le journal ni atteindre Kadre.
        if (!gateway.createNoteAvailability().enabled) return
        // Un double clic ne doit ni doubler le journal ni ouvrir une seconde fenêtre.
        if (!noteInFlight.compareAndSet(false, true)) return
        try {
            val id = store.admit(ApiDetailKey.CreateFloatingNote.userAction, "WindowManager.requestWindow")
            try {
                when (val outcome = gateway.openNote()) {
                    is NoteOpenOutcome.Opened -> {
                        store.publishNote(outcome.note)
                        store.resolve(id, ActivityStatus.Succeeded)
                    }
                    is NoteOpenOutcome.Refused -> store.resolve(id, ActivityStatus.Rejected, outcome.motif)
                    NoteOpenOutcome.Cancelled -> store.resolve(id, ActivityStatus.Cancelled)
                    NoteOpenOutcome.OpenedElsewhere -> store.resolve(
                        id,
                        ActivityStatus.Unavailable,
                        "La fenêtre a été ouverte dans une autre session.",
                    )
                }
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
        settle(id, gateway.renameNote(key, title))
    }

    suspend fun requestNoteAttention(key: NoteKey) {
        if (!gateway.noteControls(key).canRequestAttention) return
        val id = store.admit("Demander l'attention", "Window.requestAttention")
        settle(id, gateway.requestNoteAttention(key))
    }

    suspend fun toggleNoteDecorations(key: NoteKey) {
        if (!gateway.noteControls(key).canChangeDecorations) return
        val id = store.admit("Changer la décoration", "Window.apply")
        settle(id, gateway.toggleNoteDecorations(key))
    }

    suspend fun closeNote(key: NoteKey) {
        // Une note déjà retirée du store n'existe plus : c'est ce qui empêche le bouton de
        // la démo et le bouton rouge du système de la fermer chacun de leur côté.
        if (store.state.value.notes.none { it.key == key }) return
        if (!gateway.noteControls(key).canClose) return
        if (!closingNotes.add(key)) return
        try {
            val id = store.admit("Fermer une note", "Window.close")
            when (val outcome = gateway.closeNote(key)) {
                NoteUpdateOutcome.Applied -> {
                    store.removeNote(key)
                    store.resolve(id, ActivityStatus.Succeeded)
                }
                // La note n'est pas retirée ici : la fermeture n'est pas confirmée, donc la
                // présenter comme fermée serait exactement le faux succès du §2.3.
                NoteUpdateOutcome.Accepted -> store.resolve(
                    id,
                    ActivityStatus.Succeeded,
                    "Le host a accepté la fermeture ; elle n'est pas encore confirmée.",
                )
                is NoteUpdateOutcome.PartiallyApplied -> store.resolve(
                    id,
                    ActivityStatus.Succeeded,
                    "Refusé : ${outcome.rejected.joinToString { it.fieldLabel }}.",
                )
                is NoteUpdateOutcome.Refused -> store.resolve(id, ActivityStatus.Rejected, outcome.motif)
            }
        } finally {
            closingNotes.remove(key)
        }
    }

    suspend fun requestDisplayAccess() {
        if (!gateway.displayAccessAvailability().enabled) return
        val id = store.admit("Afficher les écrans", "DisplayManager.requestAccess")
        when (val inventory = gateway.requestDisplayAccess()) {
            is DisplayPresentation.Unavailable -> {
                store.publishDisplays(inventory)
                store.resolve(id, ActivityStatus.Rejected, inventory.motif)
            }
            else -> {
                store.publishDisplays(inventory)
                store.resolve(id, ActivityStatus.Succeeded)
            }
        }
    }

    /** Spec §5 ligne 118 : un partiel montre séparément ce qui est passé et ce qui est refusé. */
    private fun settle(id: ActionCorrelationId, outcome: NoteUpdateOutcome) {
        when (outcome) {
            NoteUpdateOutcome.Applied -> store.resolve(id, ActivityStatus.Succeeded)
            NoteUpdateOutcome.Accepted -> store.resolve(
                id,
                ActivityStatus.Succeeded,
                "Le host a accepté la demande ; l'effet n'est pas encore confirmé.",
            )
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
