package org.graphiks.kadre.samples.desktour.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.AtomicBoolean

internal class ActionDispatcher(
    private val store: TourStore,
    private val gateway: TourGateway,
) {
    // Les actions partent chacune dans sa propre coroutine sur le contexte applicatif Kadre
    // (« possiblement Default », cf. le host : parallèle, aucune sérialisation entre les
    // actions). Le drapeau doit donc être atomique entre coroutines ET survivre aux
    // suspensions de `openNote()`. Le CAS préserve la sémantique « un double clic est
    // ignoré » : un `Mutex.lock()` mettrait le second appel en file et ouvrirait une
    // seconde note après le premier — un changement de comportement, pas un portage.
    // Sur JVM, `kotlin.concurrent.atomics.AtomicBoolean` est un alias exact de
    // `java.util.concurrent.atomic.AtomicBoolean`.
    private val noteInFlight = AtomicBoolean(false)

    // Garde par clé : deux notes différentes doivent pouvoir se fermer en parallèle, la
    // même note revient immédiatement. Le Mutex ne couvre donc que la comptabilité
    // (jamais un appel gateway, jamais suspendu dans la section critique).
    private val closingMutex = Mutex()
    private val closingNotes = mutableSetOf<NoteKey>()

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
            noteInFlight.store(false)
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
        if (!closingMutex.withLock { closingNotes.add(key) }) return
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
            closingMutex.withLock { closingNotes.remove(key) }
        }
    }

    suspend fun requestDisplayAccess() {
        // `requestable`, pas `enabled` : une permission requise désactive l'affordance tout en
        // restant demandable, sinon l'utilisateur serait bloqué sans recours (§5 ligne 116).
        if (!gateway.displayAccessAvailability().requestable) return
        val id = store.admit(ApiDetailKey.TrackDisplays.userAction, "DisplayManager.requestAccess")
        try {
            val inventory = gateway.requestDisplayAccess()
            store.publishDisplays(inventory)
            val (status, motif) = when (inventory) {
                is DisplayPresentation.Enumerated -> ActivityStatus.Succeeded to null
                DisplayPresentation.NeedsPermission ->
                    ActivityStatus.Unavailable to "Demande transmise ; le host n'a pas publié de changement."
                is DisplayPresentation.Denied -> ActivityStatus.Rejected to "L'accès aux écrans a été refusé."
                // Un échec temporaire n'est pas un refus : il a son propre statut.
                is DisplayPresentation.Unavailable -> if (inventory.retryable) {
                    ActivityStatus.Unavailable to inventory.motif
                } else {
                    ActivityStatus.Rejected to inventory.motif
                }
            }
            store.resolve(id, status, motif)
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) { store.resolve(id, ActivityStatus.Cancelled) }
            throw cancellation
        }
    }

    suspend fun openTextInput() {
        // `requestable` : une capacité sous permission reste demandable (spec §5 ligne 116).
        if (!gateway.textInputAvailability().requestable) return
        val id = store.admit("Ouvrir une session de saisie", "SurfaceInput.openTextInput")
        when (val outcome = gateway.openTextInput()) {
            TextInputOutcome.Opened -> store.resolve(id, ActivityStatus.Succeeded)
            is TextInputOutcome.Refused -> store.resolve(id, ActivityStatus.Rejected, outcome.motif)
        }
    }

    suspend fun closeTextInput() {
        val id = store.admit("Fermer la session de saisie", "TextInputSession.close")
        gateway.closeTextInput()
        store.resolve(id, ActivityStatus.Succeeded)
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
