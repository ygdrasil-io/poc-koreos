package org.graphiks.kadre.samples.androidkadretour

import android.os.Bundle
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.integration.compose.KadreComposeHost
import org.graphiks.kadre.samples.desktour.AndroidTourGateway
import org.graphiks.kadre.samples.desktour.AndroidTourHost
import org.graphiks.kadre.samples.desktour.core.ActionDispatcher
import org.graphiks.kadre.samples.desktour.core.TourStore
import org.graphiks.kadre.samples.desktour.ui.DeskTourApp

/**
 * Le host Android de la visite — le point d'entrée du sample `android-kadre-tour`.
 *
 * La recette est celle du host web ([org.graphiks.kadre.samples.desktour.DeskTourWebMainKt],
 * elle-même transportée du host bureau), posée sur la liaison Compose P9 : une session
 * kadre par host ([KadreComposeHost] sur une [SurfaceView] réelle), puis le même
 * assemblage store / gateway / dispatcher / publications de disponibilité, puis
 * l'application Compose de la visite dans le slot content de la liaison.
 *
 * Trois différences de matière, tranchées par la plateforme (BACKEND-CAPABILITIES §3) :
 * - la surface principale est la SurfaceView du sample, fournie par le callback P1 —
 *   le content de la liaison reste NON consommant : l'observation vedette de l'entrée
 *   (route Interactions) lit les événements qui traversent vers la surface kadre ;
 * - les notes ne sont PAS créées ici : Android ne crée pas de fenêtre, le host
 *   [AndroidTourHost] est capability-off et le gateway présente la capability telle
 *   quelle — l'affordance du route Desk reste visible, porteuse de son motif ;
 * - la saisie de texte est refusée par la plateforme (P5, limite structurelle) :
 *   la capability `textInput` est présentée désactivée avec son motif, le route
 *   Interactions la montre au lieu de la cacher.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TourScreen()
        }
    }
}

@Composable
private fun TourScreen() {
    // L'état de la visite vit HORS de la session (un store par Activity) : la liaison peut
    // recréer la session après un détachement terminal, la visite continue avec le même
    // store — la fenêtre de la démo ne disparaît jamais avec une session.
    val store = remember { TourStore() }
    val noteWindows = remember { AndroidTourHost() }
    // Le dispatcher naît DANS la session (il parle au gateway, qui parle au KadreScope) :
    // le slot content le lit ici, la session le publie là-bas — jusqu'à ce qu'une session
    // vive, l'application de la visite n'est pas encore là.
    var dispatcher by remember { mutableStateOf<ActionDispatcher?>(null) }
    val actions = rememberCoroutineScope()

    KadreComposeHost(
        onSurface = { hostActivity, container ->
            // P1 : kadre ne crée jamais de vue — le sample fournit LA vue de surface (une
            // SurfaceView réelle, remplie dans le conteneur identitaire de la liaison).
            SurfaceView(hostActivity).also(container::addView)
        },
        applicationFactory = tourApplication(store, noteWindows) { dispatcher = it },
        modifier = Modifier.fillMaxSize(),
    ) { _ ->
        val state by store.state.collectAsState()
        dispatcher?.let { live ->
            DeskTourApp(
                state = state,
                onCreateNote = { actions.launch { live.createNote() } },
                onRenameNote = { key, title -> actions.launch { live.renameNote(key, title) } },
                onRequestAttention = { key -> actions.launch { live.requestNoteAttention(key) } },
                onToggleDecorations = { key -> actions.launch { live.toggleNoteDecorations(key) } },
                onCloseNote = { key -> actions.launch { live.closeNote(key) } },
                onRequestDisplayAccess = { actions.launch { live.requestDisplayAccess() } },
                onOpenTextInput = { actions.launch { live.openTextInput() } },
                onCloseTextInput = { actions.launch { live.closeTextInput() } },
                onSelectRoute = { store.setRoute(it) },
                onToggleApiDetails = { store.toggleApiDetails() },
            )
        }
    }
}

/** L'usine d'application de la visite : la session Kadre exécute [runTour]. */
private fun tourApplication(
    store: TourStore,
    noteWindows: AndroidTourHost,
    onDispatcher: (ActionDispatcher) -> Unit,
): KadreApplicationFactory = KadreApplicationFactory {
    KadreApplication {
        runTour(store, noteWindows, onDispatcher)
    }
}

/**
 * L'application kadre de la visite, dans le scope de la session — le même assemblage que
 * les hosts bureau et web : les disponibilités sont publiées d'abord, puis les observations
 * (écrans, entrée, périphériques, capture, saisie) relayent leurs états vers le store.
 * Les `collect` suspendent pour toujours — la visite vit tant que la session vit, et
 * l'annulation structurée du runtime libère tout.
 */
private suspend fun KadreScope.runTour(
    store: TourStore,
    noteWindows: AndroidTourHost,
    onDispatcher: (ActionDispatcher) -> Unit,
) {
    val gateway = AndroidTourGateway(this, noteWindows)
    val dispatcher = ActionDispatcher(store, gateway)
    onDispatcher(dispatcher)
    store.publishCreateNoteAvailability(gateway.createNoteAvailability())
    store.publishDisplayAccessAvailability(gateway.displayAccessAvailability())
    // La surface primaire : la SurfaceView de la liaison, publiée à son attach.
    val surface = primarySurface.filterNotNull().first()
    // L'observation de l'entrée enregistre la surface primaire chez le gateway (la
    // disponibilité de la saisie de texte lit sa capability) — elle part donc AVANT la
    // publication correspondante, puis les autres observations suivent. Chaque flux est
    // froid : aucun collecteur ne tourne tant que personne ne collecte.
    launch(start = CoroutineStart.UNDISPATCHED) {
        gateway.observeInput(surface).collect { store.publishInput(it) }
    }
    launch(start = CoroutineStart.UNDISPATCHED) {
        gateway.observeDisplays().collect { store.publishDisplays(it) }
    }
    launch(start = CoroutineStart.UNDISPATCHED) {
        gateway.observeDevices().collect { store.publishDevices(it) }
    }
    launch(start = CoroutineStart.UNDISPATCHED) {
        gateway.observeCapture().collect { store.publishCapture(it) }
    }
    store.publishTextInputAvailability(gateway.textInputAvailability())
    launch(start = CoroutineStart.UNDISPATCHED) {
        gateway.observeTextInput().collect { store.publishTextInput(it) }
    }
    awaitCancellation()
}
