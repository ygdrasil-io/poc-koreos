package org.graphiks.kadre.samples.composeshowcase

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionId
import org.graphiks.kadre.integration.compose.KadreComposeHost
import org.graphiks.kadre.input.InputEvent

/**
 * La démo Compose migrée (kadre-old `samples/compose/android`) : le composable
 * [KadreComposeHost] de la liaison publiée — UNE session kadre par host compose, attachée
 * quand la [SurfaceView] rejoint réellement la fenêtre, fermée à la sortie de composition,
 * recréée après un détachement terminal.
 *
 * Les crochets `internal` ([tick], [showHost], [seenSessions], [liveSession]) sont les
 * poignées des scénarios instrumentés du contrat AND-010 (le source set `androidTest` est
 * l'ami de cette compilation) : recomposition ×3 → UNE session, dispose → fermée,
 * re-compose → recréée.
 *
 * Review T3 (M-2), démontré ici : les captures de création (policy, factory, la vue choisie
 * par [onSurface]) vivent dans des `remember` SANS clé de la liaison — une session recréée
 * après détachement est construite avec la policy et l'usine de la PREMIÈRE composition, pas
 * avec les valeurs de la dernière recomposition. (La recréation après une sortie de
 * composition complète est une NOUVELLE composition : ses captures sont à elle.)
 */
class MainActivity : ComponentActivity() {

    /**
     * Le compteur de recomposition de la démo — lu DANS le slot content : chaque
     * incrémentation recompose le content sans toucher au `remember` de la liaison.
     */
    internal var tick by mutableStateOf(0)

    /** Le host compose est dans la composition — basculé à faux pour prouver le dispose. */
    internal var showHost by mutableStateOf(true)

    /** Les identités de session vues par le content, une entrée par (re)composition. */
    internal val seenSessions = mutableListOf<SessionId>()

    /** La session vivante la plus récente passée au content — la poignée du test. */
    internal var liveSession: KadreSession? = null

    /** Les lignes de console (fenêtre + touch) publiées par l'application kadre. */
    internal val consoleLines = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            if (showHost) {
                ShowcaseScreen(this)
            } else {
                BasicText(
                    "kadre compose showcase — host disposed (the session was closed)",
                    Modifier.fillMaxSize().padding(24.dp),
                )
            }
        }
    }
}

/** Le host compose de la démo : la SurfaceView choisie par le callback P1, l'overlay au-dessus. */
@Composable
private fun ShowcaseScreen(activity: MainActivity) {
    KadreComposeHost(
        onSurface = { hostActivity, container ->
            // P1 : kadre ne crée jamais de vue — le sample fournit LA vue de surface (une
            // SurfaceView réelle, remplie dans le conteneur identitaire de la liaison).
            SurfaceView(hostActivity).also(container::addView)
        },
        applicationFactory = showcaseApplication(activity.consoleLines),
        modifier = Modifier.fillMaxSize(),
    ) { session ->
        // Chaque (re)composition du slot enregistre la session VIVANTE — l'ensemble des
        // identités vues est LA preuve « une session par host » du scénario instrumenté.
        SideEffect {
            activity.seenSessions += session.id
            activity.liveSession = session
        }
        Overlay(activity, session)
    }
}

/** L'overlay non consommant de la démo : l'état du host, le tick, la console touch/fenêtre. */
@Composable
private fun Overlay(activity: MainActivity, session: KadreSession) {
    Box(Modifier.fillMaxSize()) {
        // Les lignes vivent sur un fond transparent-non-consommant : les touches traversent
        // vers la surface kadre (l'observation vedette de la démo), seules les LETTRES
        // dessinent.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(16.dp),
        ) {
            BasicText(
                "kadre compose showcase — tick=${activity.tick}",
                style = TextStyle(color = Color.White),
            )
            BasicText(
                "session ${session.id} — touch the surface below",
                style = TextStyle(color = Color.White.copy(alpha = 0.7f)),
            )
            BasicText(
                activity.consoleLines.takeLast(8).joinToString(separator = "\n"),
                style = TextStyle(color = Color.White.copy(alpha = 0.7f)),
            )
        }
    }
}

/**
 * L'application kadre de la démo : la surface primaire, puis l'observation de ses événements
 * d'entrée et de son état de fenêtre, publiés dans les lignes de console de l'overlay.
 * Les `collect` suspendent pour toujours — l'application vit jusqu'à l'arrêt de la session
 * (l'annulation structurée du runtime libère tout).
 */
private fun showcaseApplication(lines: MutableList<String>): KadreApplicationFactory =
    KadreApplicationFactory {
        // Le récepteur de `KadreApplication.run` EST le KadreScope — l'application vit dans
        // la coroutine structurée de la session.
        KadreApplication {
            observeSurface(lines)
        }
    }

private suspend fun KadreScope.observeSurface(lines: MutableList<String>): Unit = coroutineScope {
    val surface = primarySurface.filterNotNull().first()
    postLine(lines, "surface acquired")
    launch {
        // Le touch : L'observation vedette de la démo — chaque contact réduit par le runtime.
        surface.input.events.collect { event -> postLine(lines, describe(event)) }
    }
    surface.state.collect { state ->
        postLine(lines, "window ${state.visibility} ${state.focus}")
    }
}

private fun describe(event: InputEvent): String = when (event) {
    is InputEvent.TouchChanged -> "touch ${event.phase} #${event.touchId}"
    is InputEvent.Key -> "key ${event.logicalKey} ${event.keyState}"
    is InputEvent.Scrolled -> "scroll ${event.delta}"
    else -> "input ${event::class.simpleName}"
}

/** Les lignes de console vivent sur le thread principal — les collecteurs, non. */
private fun postLine(lines: MutableList<String>, message: String) {
    Handler(Looper.getMainLooper()).post {
        lines.add(message)
        while (lines.size > 8) lines.removeAt(0)
    }
}
