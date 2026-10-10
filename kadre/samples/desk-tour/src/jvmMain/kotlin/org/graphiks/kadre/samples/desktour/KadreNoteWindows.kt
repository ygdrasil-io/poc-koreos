package org.graphiks.kadre.samples.desktour

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.samples.desktour.core.DeskTourNote
import org.graphiks.kadre.samples.desktour.core.NoteControls
import org.graphiks.kadre.samples.desktour.core.NoteKey
import org.graphiks.kadre.samples.desktour.core.NoteOpenOutcome
import org.graphiks.kadre.samples.desktour.core.NoteUpdateOutcome
import org.graphiks.kadre.samples.desktour.core.NoteWindowHost
import org.graphiks.kadre.samples.desktour.core.RejectedFieldPresentation
import org.graphiks.kadre.samples.desktour.core.noteOpenOutcomeFor
import org.graphiks.kadre.samples.desktour.core.present
import org.graphiks.kadre.samples.desktour.core.readableName
import org.graphiks.kadre.samples.desktour.core.userMotif
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.window.Window
import org.graphiks.kadre.window.WindowAttention
import org.graphiks.kadre.window.WindowCloseDecision
import org.graphiks.kadre.window.WindowCloseOutcome
import org.graphiks.kadre.window.WindowDecorations
import org.graphiks.kadre.window.WindowEvent
import org.graphiks.kadre.window.WindowProperty
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowUpdate
import org.graphiks.kadre.window.WindowUpdateOutcome
import org.graphiks.kadre.window.requestWindow

/** Taille d'une note : assez large pour le champ et son bouton, assez petite pour rester une note. */
private val noteContentSize = LogicalSize(420.0, 240.0)

/**
 * Le support de notes du host bureau : chaque note est une fenêtre Kadre ouverte par
 * `windows.requestWindow` (PROBE-NOTES.md, correction 2 : sur le web ce n'est pas possible,
 * la note y appartiendra à la démo). Le code est celui de l'ancien gateway, extrait tel quel
 * — le comportement desktop ne change pas.
 */
internal class KadreNoteWindows(private val scope: KadreScope) : NoteWindowHost {
    private val notes = mutableMapOf<NoteKey, Window>()
    private val nextKey = AtomicLong(0L)
    private val noteCloseRequests = MutableSharedFlow<NoteKey>(extraBufferCapacity = 8)

    override suspend fun openNote(): NoteOpenOutcome {
        // Une note est une petite fenêtre : avec la taille par défaut du host (800 × 600), elle
        // recouvrait entièrement l'espace de travail et l'opérateur ne voyait plus rien d'autre.
        val request = when (val result = scope.windows.requestWindow {
            title = "Notes"
            contentSize = noteContentSize
        }) {
            is KadreResult.Success -> result.value
            is KadreResult.Failure -> return noteOpenOutcomeFor(result.reason)
        }
        return when (val outcome = request.await()) {
            is WindowRequestOutcome.OpenedHere -> {
                val opened = outcome.window
                val key = NoteKey(nextKey.incrementAndGet())
                notes[key] = opened
                // Le bouton rouge du système passe par le même chemin que l'action de la
                // démo : on accepte, et on ne publie la clé qu'après une acceptation réussie.
                scope.launch {
                    opened.events.filterIsInstance<WindowEvent.CloseRequested>().collect { closeRequest ->
                        val answered = opened.respondToCloseRequest(closeRequest.requestId, WindowCloseDecision.Accept)
                        if (answered is KadreResult.Success) {
                            notes.remove(key)
                            noteCloseRequests.emit(key)
                        }
                    }
                }
                NoteOpenOutcome.Opened(DeskTourNote(key, opened.state.value.title, noteControls(key)))
            }
            is WindowRequestOutcome.Rejected -> noteOpenOutcomeFor(outcome.failure)
            is WindowRequestOutcome.OpenedInNewSession -> NoteOpenOutcome.OpenedElsewhere
            WindowRequestOutcome.Cancelled -> NoteOpenOutcome.Cancelled
            WindowRequestOutcome.RequesterDetached -> NoteOpenOutcome.Cancelled
        }
    }

    override fun noteWindow(key: NoteKey): Window? = notes[key]

    override fun noteControls(key: NoteKey): NoteControls = notes[key]?.let { window ->
        val capabilities = window.capabilities.value
        val rename = present(capabilities.title)
        val attention = present(capabilities.attention)
        val decorations = present(capabilities.decorations)
        NoteControls(
            canRename = rename.enabled,
            canRequestAttention = attention.enabled,
            canChangeDecorations = decorations.enabled,
            // Aucune capability publique ne conditionne la fermeture : elle est donc toujours
            // offerte, et c'est le refus éventuel du host qui portera le motif.
            canClose = true,
            motifs = listOfNotNull(
                rename.motif?.let { "Renommer — $it" },
                attention.motif?.let { "Attention — $it" },
                decorations.motif?.let { "Décoration — $it" },
            ),
        )
    } ?: NoteControls(false, false, false, false, emptyList())

    override fun observeNote(key: NoteKey): Flow<DeskTourNote> = notes[key]?.let { window ->
        combine(window.state, window.surface.state) { state, _ ->
            DeskTourNote(key, state.title, noteControls(key))
        }
    } ?: flowOf()

    override fun observeNoteCloseRequests(): Flow<NoteKey> = noteCloseRequests

    override suspend fun renameNote(key: NoteKey, title: String): NoteUpdateOutcome {
        val window = notes[key] ?: return NoteUpdateOutcome.Refused("Cette note n'existe plus.")
        return when (val result = window.apply(WindowUpdate(title = PropertyChange.Set(title)))) {
            is KadreResult.Failure -> NoteUpdateOutcome.Refused(result.reason.userMotif())
            is KadreResult.Success -> when (val outcome = result.value) {
                is WindowUpdateOutcome.Applied -> NoteUpdateOutcome.Applied
                is WindowUpdateOutcome.Accepted -> NoteUpdateOutcome.Accepted
                is WindowUpdateOutcome.PartiallyApplied -> NoteUpdateOutcome.PartiallyApplied(
                    // Un renommage n'envoie que le titre : `applied` ne doit donc jamais
                    // annoncer les treize autres propriétés, qu'on n'a pas demandées.
                    applied = if (outcome.rejected.any { it.field == WindowProperty.Title }) {
                        emptyList()
                    } else {
                        listOf(WindowProperty.Title.readableName())
                    },
                    rejected = outcome.rejected.map {
                        RejectedFieldPresentation(it.field.readableName(), it.failure.userMotif())
                    },
                )
            }
        }
    }

    override suspend fun requestNoteAttention(key: NoteKey): NoteUpdateOutcome {
        val window = notes[key] ?: return NoteUpdateOutcome.Refused("Cette note n'existe plus.")
        return when (val result = window.requestAttention(WindowAttention.Informational)) {
            is KadreResult.Failure -> NoteUpdateOutcome.Refused(result.reason.userMotif())
            is KadreResult.Success -> NoteUpdateOutcome.Applied
        }
    }

    override suspend fun toggleNoteDecorations(key: NoteKey): NoteUpdateOutcome {
        val window = notes[key] ?: return NoteUpdateOutcome.Refused("Cette note n'existe plus.")
        val next = if (window.state.value.decorations == WindowDecorations.System) {
            WindowDecorations.Borderless
        } else {
            WindowDecorations.System
        }
        return when (val result = window.apply(WindowUpdate(decorations = PropertyChange.Set(next)))) {
            is KadreResult.Failure -> NoteUpdateOutcome.Refused(result.reason.userMotif())
            is KadreResult.Success -> when (val outcome = result.value) {
                is WindowUpdateOutcome.Applied -> NoteUpdateOutcome.Applied
                is WindowUpdateOutcome.Accepted -> NoteUpdateOutcome.Accepted
                is WindowUpdateOutcome.PartiallyApplied -> NoteUpdateOutcome.PartiallyApplied(
                    applied = emptyList(),
                    rejected = outcome.rejected.map {
                        RejectedFieldPresentation(it.field.readableName(), it.failure.userMotif())
                    },
                )
            }
        }
    }

    override suspend fun closeNote(key: NoteKey): NoteUpdateOutcome {
        val window = notes[key] ?: return NoteUpdateOutcome.Refused("Cette note n'existe plus.")
        return when (val result = window.close()) {
            is KadreResult.Failure -> NoteUpdateOutcome.Refused(result.reason.userMotif())
            is KadreResult.Success -> when (result.value) {
                WindowCloseOutcome.Closed -> {
                    notes.remove(key)
                    NoteUpdateOutcome.Applied
                }
                is WindowCloseOutcome.Accepted -> NoteUpdateOutcome.Accepted
            }
        }
    }
}
