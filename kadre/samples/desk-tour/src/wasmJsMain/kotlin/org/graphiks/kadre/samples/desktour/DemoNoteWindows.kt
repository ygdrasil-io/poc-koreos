package org.graphiks.kadre.samples.desktour

import kotlinx.browser.document
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.incrementAndFetch
import org.graphiks.kadre.samples.desktour.core.DeskTourNote
import org.graphiks.kadre.samples.desktour.core.NoteControls
import org.graphiks.kadre.samples.desktour.core.NoteKey
import org.graphiks.kadre.samples.desktour.core.NoteOpenOutcome
import org.graphiks.kadre.samples.desktour.core.NoteUpdateOutcome
import org.graphiks.kadre.samples.desktour.core.NoteWindowHost
import org.graphiks.kadre.window.Window
import org.w3c.dom.HTMLElement

/**
 * Le support de notes du host web : chaque note est un div de la démo, créé dans la couche
 * à notes de la page (PROBE-NOTES.md, correction 2 — Kadre refuse une fenêtre enfant du
 * même document, donc la note web appartient à la démo ; décision T2). Le code suit la
 * forme du support bureau ([KadreNoteWindows]) : mêmes clés atomiques, mêmes outcomes,
 * mêmes refus. Ce qui change est la matière : pas de fenêtre Kadre, un élément hôte.
 *
 * Le viewport Compose d'une note n'est pas booté ici : le host web le monte, depuis la
 * boucle de montage, dans l'élément que [element] rend accessible — exactement comme le
 * host bureau monte la sienne dans la fenêtre que [noteWindow] rendait accessible. Ce
 * fichier reste ainsi étranger à Compose, comme son miroir jvmMain.
 */
internal class DemoNoteWindows(private val noteLayer: HTMLElement) : NoteWindowHost {
    private class WebNote(val element: HTMLElement, val title: MutableStateFlow<String>)

    // Les notes partent chacune dans sa propre coroutine (Dispatchers.Default du scope
    // applicatif) : même grammaire que KadreNoteWindows, mêmes réserves (les mutations de
    // la carte restent non sérialisées — limites héritées du sample d'origine).
    private val notes = mutableMapOf<NoteKey, WebNote>()
    private val nextKey = AtomicLong(0L)

    // Aucun bouton système ne ferme une note web (pas de fenêtre, pas de bouton rouge) :
    // le flux n'émet jamais sur cette cible, le membre reste le même contrat que le bureau.
    private val noteCloseRequests = MutableSharedFlow<NoteKey>(extraBufferCapacity = 8)

    override suspend fun openNote(): NoteOpenOutcome {
        val key = NoteKey(nextKey.incrementAndFetch())
        val element = (document.createElement("div") as HTMLElement).also { note ->
            note.className = NOTE_CLASS
            // Cascade : la place suit la CLÉ monotone de la note, pas le nombre de notes
            // vivantes — fermer puis rouvrir ne rejoue jamais la place d'une note encore
            // ouverte (un décompte vivant renotait la même place après chaque fermeture).
            // Huit places cyclent ; une neuvième note VIVANTE réutiliserait la première —
            // borne honnête d'une démo, jamais atteinte par le scénario de la visite.
            val offset = ((key.value - 1) % CASCADE_SLOTS) * NOTE_CASCADE_STEP
            note.style.left = "${NOTE_MARGIN + offset}px"
            note.style.top = "${NOTE_MARGIN + offset}px"
        }
        noteLayer.appendChild(element)
        val note = WebNote(element, MutableStateFlow(NOTE_TITLE))
        notes[key] = note
        return NoteOpenOutcome.Opened(DeskTourNote(key, note.title.value, noteControls(key)))
    }

    /** L'élément hôte d'une note ouverte — le point de montage du host web. */
    fun element(key: NoteKey): HTMLElement? = notes[key]?.element

    /** Décision T2 : aucun windowProvider n'est armé sur le web, aucune fenêtre Kadre n'existe. */
    override fun noteWindow(key: NoteKey): Window? = null

    override fun noteControls(key: NoteKey): NoteControls = NoteControls(
        // Le renommage vit chez la démo (le titre est un état du support) : il est offert.
        canRename = true,
        // Une note web n'est pas une fenêtre : pas d'attention ni de décoration à demander,
        // et le motif de l'hôte porte la raison — les contrôles restent affichés, désactivés.
        canRequestAttention = false,
        canChangeDecorations = false,
        canClose = true,
        motifs = listOf(
            "Attention — une note web n'est pas une fenêtre à signaler.",
            "Décoration — une note web n'est pas une fenêtre à décorer.",
        ),
    )

    override fun observeNote(key: NoteKey): Flow<DeskTourNote> = notes[key]?.let { note ->
        note.title.map { title -> DeskTourNote(key, title, noteControls(key)) }
    } ?: flowOf()

    override fun observeNoteCloseRequests(): Flow<NoteKey> = noteCloseRequests

    override suspend fun renameNote(key: NoteKey, title: String): NoteUpdateOutcome {
        val note = notes[key] ?: return NoteUpdateOutcome.Refused("Cette note n'existe plus.")
        note.title.value = title
        return NoteUpdateOutcome.Applied
    }

    override suspend fun requestNoteAttention(key: NoteKey): NoteUpdateOutcome =
        NoteUpdateOutcome.Refused("Une note web n'a pas de demande d'attention.")

    override suspend fun toggleNoteDecorations(key: NoteKey): NoteUpdateOutcome =
        NoteUpdateOutcome.Refused("Une note web n'a pas de décoration à changer.")

    override suspend fun closeNote(key: NoteKey): NoteUpdateOutcome {
        val note = notes[key] ?: return NoteUpdateOutcome.Refused("Cette note n'existe plus.")
        notes.remove(key)
        // Pas d'API publique de dispose pour un viewport Compose (PROBE-NOTES.md, Q3) : le
        // retrait de l'élément est la fermeture honnête et idempotente. Le résidu — scène de
        // composition, couche Skia, écouteur de resize — est borné, invisible, et documenté
        // comme limite de la démo.
        note.element.remove()
        return NoteUpdateOutcome.Applied
    }

    private companion object {
        const val NOTE_CLASS = "desk-tour-note"
        const val NOTE_TITLE = "Notes"
        const val NOTE_MARGIN = 48
        const val NOTE_CASCADE_STEP = 32
        const val CASCADE_SLOTS = 8
    }
}
