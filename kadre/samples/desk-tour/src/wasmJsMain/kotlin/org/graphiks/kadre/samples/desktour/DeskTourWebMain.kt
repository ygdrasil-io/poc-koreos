package org.graphiks.kadre.samples.desktour

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.platform.web.attachKadre
import org.graphiks.kadre.samples.desktour.core.ActionDispatcher
import org.graphiks.kadre.samples.desktour.core.NoteKey
import org.graphiks.kadre.samples.desktour.core.TourStore
import org.graphiks.kadre.samples.desktour.ui.DeskTourApp
import org.graphiks.kadre.samples.desktour.ui.NoteContent
import org.w3c.dom.HTMLElement

/** L'id du conteneur hôte de l'application (la page du host web le déclare). */
private const val CONTAINER_ID = "kadre-desk-tour"

/** L'id de la couche à notes : au-dessus du conteneur, elle ne bloque pas ses clics. */
private const val NOTE_LAYER_ID = "kadre-desk-tour-notes"

/**
 * Le host web de la visite — le point d'entrée du binaire `wasmJsBrowserDistribution`.
 *
 * La recette est celle du host bureau ([DeskTourMain]), transportée sur la page : un
 * attachement Kadre sur le conteneur hôte, puis le même assemblage store / gateway /
 * dispatcher / publications de disponibilité, puis l'application Compose et ses
 * callbacks. Deux différences de matière, tranchées par la sonde (PROBE-NOTES.md) et la
 * décision T2 :
 * - pas de `requestWindow` pour la surface principale : le conteneur EST la fenêtre de
 *   la démo, `attachKadre` s'y attache directement (sans windowProvider — Kadre refuse
 *   une fenêtre enfant du même document) ;
 * - les notes sont des divs de la démo ([DemoNoteWindows]) : chaque note reçoit son
 *   propre viewport Compose, monté par la boucle ci-dessous dans l'élément du support,
 *   exactement comme le bureau monte la sienne dans la fenêtre du support.
 *
 * La taille suit la fenêtre (PROBE-NOTES.md, Q2-d) : le conteneur remplit la fenêtre
 * (page CSS) et Compose ne relit sa boîte qu'aux resizes de la FENÊTRE — c'est donc le
 * chemin de resize du navigateur qui redessine les deux piles ; la couche à notes est
 * une surcouche fixe qui ne déplace jamais la boîte du conteneur.
 */
public fun main() {
    val container = requireHostElement(CONTAINER_ID)
    val noteLayer = requireHostElement(NOTE_LAYER_ID)
    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val attached = container.attachKadre(parentScope, application = KadreApplication {
        // Le store, le dispatcher et le gateway ne connaissent rien du DOM ni de Compose ;
        // seul ce host les lie au moteur de rendu courant — comme dans DeskTourMain.
        val store = TourStore()
        val noteWindows = DemoNoteWindows(noteLayer)
        val gateway = WebTourGateway(this, noteWindows)
        val dispatcher = ActionDispatcher(store, gateway)
        store.publishCreateNoteAvailability(gateway.createNoteAvailability())
        store.publishDisplayAccessAvailability(gateway.displayAccessAvailability())
        // La surface primaire est publiée avant que ce bloc ne tourne (la forme même des
        // scénarios du fixture) ; c'est la page — le conteneur — qui la porte.
        val surface = checkNotNull(primarySurface.value)
        val collectors = mutableListOf<Job>()
        val mounted = mutableSetOf<NoteKey>()
        val mountAttempted = mutableSetOf<NoteKey>()
        // L'inventaire des écrans est reconstruit à chaque émission, l'entrée est observée
        // (la démo la montre, elle ne la synthétise jamais) : les mêmes relais que le bureau.
        collectors += launch(start = CoroutineStart.UNDISPATCHED) {
            gateway.observeDisplays().collect { store.publishDisplays(it) }
        }
        collectors += launch(start = CoroutineStart.UNDISPATCHED) {
            gateway.observeInput(surface).collect { store.publishInput(it) }
        }
        collectors += launch(start = CoroutineStart.UNDISPATCHED) {
            gateway.observeDevices().collect { store.publishDevices(it) }
        }
        collectors += launch(start = CoroutineStart.UNDISPATCHED) {
            gateway.observeCapture().collect { store.publishCapture(it) }
        }
        collectors += launch(start = CoroutineStart.UNDISPATCHED) {
            store.publishTextInputAvailability(gateway.textInputAvailability())
            gateway.observeTextInput().collect { store.publishTextInput(it) }
        }
        // Chaque note reçoit son propre viewport Compose, monté une seule fois. L'hôte
        // observe le store plutôt que de s'accrocher à l'ouverture : c'est le seul endroit
        // qui connaît Compose, `core` reste renderer-agnostique.
        collectors += launch(start = CoroutineStart.UNDISPATCHED) {
            store.state.map { state -> state.notes }.distinctUntilChanged().collect { notes ->
                notes.forEach { note ->
                    if (note.key in mounted || !mountAttempted.add(note.key)) return@forEach
                    val noteElement = noteWindows.element(note.key) ?: return@forEach
                    mounted.add(note.key)
                    ComposeViewport(noteElement) {
                        // La note est relue du store à chaque composition : un renommage
                        // depuis la page principale se voit aussi dans sa propre note.
                        val liveState by store.state.collectAsState()
                        val liveNote = liveState.notes.firstOrNull { it.key == note.key } ?: note
                        NoteContent(liveNote, onRename = { title ->
                            launch { dispatcher.renameNote(note.key, title) }
                        })
                    }
                    // Le titre d'une note web vit dans le support : chaque changement se
                    // republie vers le store, comme le bureau republiait l'état fenêtre.
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        gateway.observeNote(note.key).collect { store.publishNote(it) }
                    }
                }
            }
        }
        // La fenêtre principale de la démo est la page : le viewport Compose prend le
        // conteneur. `ComposeViewport` vide le conteneur à l'amorçage (PROBE-NOTES.md,
        // Q1) — l'attachement Kadre, posé avant, survit (écouteurs et observateur de
        // taille vivent sur le conteneur lui-même, pas dans ses enfants).
        ComposeViewport(container) {
            val state by store.state.collectAsState()
            DeskTourApp(
                state = state,
                onCreateNote = { launch { dispatcher.createNote() } },
                onRenameNote = { key, title -> launch { dispatcher.renameNote(key, title) } },
                onRequestAttention = { key -> launch { dispatcher.requestNoteAttention(key) } },
                onToggleDecorations = { key -> launch { dispatcher.toggleNoteDecorations(key) } },
                onCloseNote = { key -> launch { dispatcher.closeNote(key) } },
                onRequestDisplayAccess = { launch { dispatcher.requestDisplayAccess() } },
                onOpenTextInput = { launch { dispatcher.openTextInput() } },
                onCloseTextInput = { launch { dispatcher.closeTextInput() } },
                onSelectRoute = { store.setRoute(it) },
                onToggleApiDetails = { store.toggleApiDetails() },
            )
        }
        // Aucune fermeture ne termine une page : le bloc application vit tant que la
        // session vit, et l'annulation du scope emporte ses collecteurs (la grammaire du
        // fixture). Les notes se ferment par l'action de la démo, jamais par la page.
        awaitCancellation()
    })
    when (attached) {
        is KadreResult.Success -> Unit
        is KadreResult.Failure -> error("Impossible d'attacher Kadre au conteneur : ${attached.reason}")
    }
}

/** Le conteneur que la page doit déclarer : sans lui, le host n'a pas de matière. */
private fun requireHostElement(id: String): HTMLElement =
    document.getElementById(id) as? HTMLElement
        ?: error("La page du host web doit déclarer #$id (élément hôte de la démo).")
