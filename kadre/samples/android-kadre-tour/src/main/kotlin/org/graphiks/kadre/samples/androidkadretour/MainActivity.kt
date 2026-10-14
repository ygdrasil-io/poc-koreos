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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import org.graphiks.kadre.samples.desktour.ui.DeskTourApp
import android.content.Context
import android.view.inputmethod.InputMethodManager
// La section Cast (phase 11) : la forme présentation consommée par le sample.
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Gravity
import android.widget.TextView
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayInventory
import org.graphiks.kadre.display.DisplayManagerState
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.TouchPhase
import org.graphiks.kadre.platform.android.attachKadreSecondaryDisplay
import org.graphiks.kadre.samples.desktour.core.TourRoute
import org.graphiks.kadre.samples.desktour.core.describeInputEvent
import org.graphiks.kadre.samples.desktour.core.renderEventSummary
import org.graphiks.kadre.samples.desktour.core.userMotif

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
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
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
            // Le bouton clavier virtuel : toggle IMM côté sample (le clavier est un feature
            // SDK Android — kadre observe les touches qui traversent le Window.Callback).
            val imm = androidx.compose.ui.platform.LocalContext.current.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            androidx.compose.material3.Button(
                onClick = { imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp),
            ) { androidx.compose.material3.Text("Clavier") }

            // La section Cast du route Écrans : la présentation de la visite sur un écran
            // secondaire (P11, `attachKadreSecondaryDisplay`). L'état ne quitte JAMAIS la
            // composition — seule la vue se cache au changement de route : une présentation
            // qui tourne pendant que la visite navigue reste contrôlable (et se termine
            // proprement, par son bouton ou par le retrait de l'écran) au retour.
            CastSection(
                visible = state.route == TourRoute.Screens,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // Le panneau flotte AU-DESSUS de la barre de navigation et du bouton
                    // « Détails API » du shell commun — il ne remplace jamais la liste des
                    // écrans observés, il la complète.
                    .padding(start = 16.dp, end = 16.dp, bottom = 140.dp),
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

/**
 * La section Cast du route Écrans — la démonstration consommateur de la forme présentation
 * (P11, AND-011) : « Présenter » monte une session Kadre SECONDAIRE sur le premier écran
 * ≠ défaut du système, coexistante avec la session principale de la liaison (la forme
 * présentation ne réserve jamais l'identité de l'Activity) ; « Arrêter » la termine ; le
 * retrait de l'écran la termine seul (surface fermée, réservations libérées — le panneau
 * revient à son état initial, prêt à re-présenter).
 *
 * L'écran est résolu par le `DisplayManager` SDK, côté SAMPLE : Kadre n'expose jamais les
 * displayIds SDK comme identités (les [org.graphiks.kadre.display.DisplayId] sont opaques,
 * alloués par le runtime) — c'est l'application qui choisit l'écran et le passe à Kadre.
 */
@Composable
private fun CastSection(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val activity = androidx.compose.ui.platform.LocalContext.current as ComponentActivity
    var secondaryDisplay by remember { mutableStateOf<Display?>(null) }
    var castSession by remember { mutableStateOf<KadreSession?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    // L'inventaire SDK des écrans, vivant : l'option développeur « Simuler un second écran »
    // ajoute et retire un écran à tout moment, le bouton « Présenter » suit. Le listener est
    // désenregistré à la sortie de composition (la section vit toute la vie de l'Activity).
    DisposableEffect(activity) {
        val manager = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        fun resolve(): Display? = manager.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { secondaryDisplay = resolve() }
            override fun onDisplayRemoved(displayId: Int) { secondaryDisplay = resolve() }
            override fun onDisplayChanged(displayId: Int) { secondaryDisplay = resolve() }
        }
        manager.registerDisplayListener(listener, null)
        secondaryDisplay = resolve()
        onDispose { manager.unregisterDisplayListener(listener) }
    }

    // L'état de la session présentation : le terminal (bouton « Arrêter », retrait de
    // l'écran, fin de l'Activity) rend le panneau à son état initial — « Présenter »
    // revient, aucune résurrection.
    LaunchedEffect(castSession) {
        val session = castSession ?: return@LaunchedEffect
        session.state.collect { state ->
            if (state is SessionState.Terminated) {
                status = "Présentation terminée (${castOutcomeLabel(state.outcome)})."
                castSession = null
            }
        }
    }

    if (!visible) return

    androidx.compose.foundation.layout.Column(modifier) {
        androidx.compose.material3.Text("Cast — écran secondaire")
        Row {
            androidx.compose.material3.Button(
                onClick = {
                    val display = secondaryDisplay ?: return@Button
                    // Le contentView LIBRE de la forme présentation : un TextView observateur,
                    // PAS un ComposeView — un ComposeView dans une Presentation exigerait des
                    // ViewTree owners posés à la main (le piège connu), hors du périmètre v1.
                    val content = TextView(activity).apply {
                        textSize = 20f
                        gravity = Gravity.CENTER
                        setPadding(32, 32, 32, 32)
                    }
                    when (
                        val outcome = activity.attachKadreSecondaryDisplay(
                            presentationDisplay = display,
                            contentView = content,
                            applicationFactory = castApplication(content),
                        )
                    ) {
                        is KadreResult.Success -> {
                            castSession = outcome.value
                            status = "Présentation en cours sur « ${display.name} »."
                        }
                        is KadreResult.Failure ->
                            status = "Le cast a été refusé : ${outcome.reason.userMotif()}"
                    }
                },
                enabled = secondaryDisplay != null && castSession == null,
            ) { androidx.compose.material3.Text("Présenter") }
            androidx.compose.material3.Button(
                onClick = { castSession?.close() },
                enabled = castSession != null,
            ) { androidx.compose.material3.Text("Arrêter") }
        }
        if (secondaryDisplay == null && castSession == null) {
            androidx.compose.material3.Text("Aucun écran secondaire — Options développeur → Simuler un second écran")
        }
        status?.let { androidx.compose.material3.Text(it) }
    }
}

/** Le motif utilisateur d'une terminaison de session présentation, sans jargon de type. */
private fun castOutcomeLabel(outcome: SessionOutcome): String = when (outcome) {
    SessionOutcome.Completed -> "application terminée"
    is SessionOutcome.Stopped -> when (outcome.reason) {
        org.graphiks.kadre.application.SessionStopReason.HostRequested -> "arrêt demandé"
        // Le retrait de l'écran ferme la surface du média : la session suit l'écran.
        org.graphiks.kadre.application.SessionStopReason.HostDetached -> "écran retiré"
        else -> "arrêt de session"
    }
    is SessionOutcome.Failed -> "échec"
}

/** L'usine d'application de la présentation : la session cast n'exécute que l'observateur. */
private fun castApplication(content: TextView): KadreApplicationFactory = KadreApplicationFactory {
    KadreApplication { runCastObserver(content) }
}

/**
 * L'observateur de la présentation, dans le scope de SA session — le miroir minimal du
 * [runTour] de la visite : l'inventaire `displays` de la session présentation (les métriques
 * réelles de l'écran simulé, rafraîchies à chaque événement du DisplayPort) et l'entrée du
 * média (le MÊME flux `surface.input.events` que la route Interactions observe) rendus dans
 * le TextView. Les collecteurs vivent sur le dispatcher de la session — celui du
 * `parentScope` par défaut, le `lifecycleScope` de l'Activity : le thread principal, où le
 * TextView s'écrit ; les `collect` ne quittent jamais ce dispatcher.
 */
private suspend fun KadreScope.runCastObserver(content: TextView) {
    val surface = primarySurface.filterNotNull().first()
    var touchCount = 0
    var lastEvent: String? = null
    var screens = "Les écrans n'ont pas encore été observés."
    fun render() {
        content.text = buildString {
            appendLine("Présentation Kadre — session de présentation")
            appendLine(screens)
            append("Touchers : $touchCount")
            lastEvent?.let {
                appendLine()
                append("Dernier événement : $it")
            }
        }
    }
    launch(start = CoroutineStart.UNDISPATCHED) {
        displays.state.collect { state ->
            screens = castScreensLabel(state)
            render()
        }
    }
    launch(start = CoroutineStart.UNDISPATCHED) {
        surface.input.events.collect { event ->
            // Un contact commencé = un toucher ; le résumé affiché est celui de la visite
            // (route Interactions), la même mise en forme pour le même fait.
            if (event is InputEvent.TouchChanged && event.phase == TouchPhase.Started) touchCount++
            lastEvent = renderEventSummary(describeInputEvent(event))
            render()
        }
    }
    render()
    awaitCancellation()
}

/** L'inventaire `displays` de la session présentation, une ligne par écran, comme le route Écrans. */
private fun castScreensLabel(state: DisplayManagerState): String =
    when (val inventory = state.inventory) {
        is DisplayInventory.Enumerated ->
            inventory.displays.joinToString(separator = "\n") { display ->
                val displayState = display.state.value
                val name = displayState.name ?: "écran sans nom"
                val primaryMark = if (display == inventory.primary) " (principal)" else ""
                "$name$primaryMark — ${displayState.bounds.size.width} × ${displayState.bounds.size.height}" +
                    " · échelle ${displayState.scaleFactor}"
            }.ifEmpty { "Aucun écran rapporté par le host." }
        else -> "Les écrans ne sont pas disponibles sur ce host."
    }
