package org.graphiks.kadre.samples.hellokadre

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayInventory
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.SurfaceEvent
import org.graphiks.kadre.window.Window
import org.graphiks.kadre.window.WindowCloseDecision
import org.graphiks.kadre.window.WindowCloseResponseOutcome
import org.graphiks.kadre.window.WindowEvent

/** Ce que le support fournit à la session : sa fenêtre (bureau) et la surface observée. */
internal data class HelloKadreSurface(
    val window: Window?,
    val surface: HostSurface,
)

/**
 * Le corps du sample, identique sur tous les supports : l'observation des écrans,
 * de la fenêtre (quand le support en fournit une), de la surface et des entrées,
 * puis le protocole de fermeture en deux temps. Tout est écrit contre l'API
 * commune de `:kadre` — rien ici ne connaît AppKit ni le DOM.
 *
 * La seule partie propre au support est [openSurface] : comment obtenir la
 * fenêtre et la surface (requestWindow côté bureau, primarySurface côté web —
 * le web passe `window = null` et les parties fenêtre ne tournent pas).
 */
internal fun helloKadreApplication(
    openSurface: suspend KadreScope.() -> HelloKadreSurface,
): KadreApplication = KadreApplication {
    val (window, surface) = openSurface()
    printDisplays()
    println(
        if (window != null) "[fenêtre] ouverte — interagissez avec elle, la console suit"
        else "[surface] attachée au conteneur — interagissez avec la page, la console suit",
    )
    // Les événements de fenêtre : géométrie (déplacer, redimensionner),
    // propriétés modifiées, demandes de fermeture.
    window?.let { w ->
        launch(start = CoroutineStart.UNDISPATCHED) {
            w.events.collect { event -> println("[fenêtre] ${describe(event)}") }
        }
    }
    // Les événements de surface : focus, visibilité, dimensions.
    launch(start = CoroutineStart.UNDISPATCHED) {
        surface.events.collect { event -> println("[surface] ${describe(event)}") }
    }
    // Les entrées : clavier, souris, scroll. Elles arrivent sans aucun rendu ;
    // le `toString()` de chaque événement est déjà lisible.
    launch(start = CoroutineStart.UNDISPATCHED) {
        surface.input.events.collect { event -> println("[entrée] $event") }
    }
    // La fermeture est un protocole en deux temps : le système demande (bouton
    // rouge), l'application répond. Accepter ferme la fenêtre ; côté bureau,
    // `stopWhenLastWindowClosed` achève alors la session.
    if (window != null) {
        val closeRequest = window.events.filterIsInstance<WindowEvent.CloseRequested>().first()
        println("[fenêtre] demande de fermeture reçue — raison=${closeRequest.reason}")
        when (val result = window.respondToCloseRequest(closeRequest.requestId, WindowCloseDecision.Accept)) {
            is KadreResult.Failure -> error("Réponse à la fermeture refusée : ${result.reason}")
            is KadreResult.Success -> when (val response = result.value) {
                is WindowCloseResponseOutcome.Closing -> println("[fenêtre] fermeture acceptée — au revoir !")
                WindowCloseResponseOutcome.KeptOpen -> error("Fenêtre restée ouverte : $response")
                WindowCloseResponseOutcome.TooLate -> error("Demande de fermeture expirée")
                WindowCloseResponseOutcome.AlreadyResolved -> error("Demande de fermeture déjà résolue")
            }
        }
    }
    // La session vit encore le temps du démontage ; côté web, une page ne se
    // ferme pas : cette suspension ne reprend qu'à l'annulation du scope.
    awaitCancellation()
}

/** Les écrans connus au démarrage de la session. */
private fun KadreScope.printDisplays() {
    when (val inventory = displays.state.value.inventory) {
        is DisplayInventory.Enumerated -> inventory.displays.forEach { display ->
            val state = display.state.value
            val role = if (display.id == inventory.primary?.id) " (principale)" else ""
            println(
                "[écran] ${state.name ?: "sans nom"}$role : " +
                    "${state.bounds.size.width}×${state.bounds.size.height} px, échelle ×${state.scaleFactor}",
            )
        }
        // Sur macOS, l'énumération des écrans peut attendre une permission ;
        // le sample reste observateur et ne la demande pas de lui-même.
        DisplayInventory.PermissionRequired ->
            println("[écran] énumération bloquée par une permission (voir displays.requestAccess())")
        is DisplayInventory.PermissionDenied ->
            println("[écran] énumération refusée par le système")
        is DisplayInventory.Unavailable ->
            println("[écran] énumération indisponible : ${inventory.failure}")
    }
}

/** Un libellé court par événement de fenêtre, au lieu du `toString()` complet. */
private fun describe(event: WindowEvent): String = when (event) {
    is WindowEvent.GeometryChanged -> "géométrie : contenu=${event.state.contentSize}"
    is WindowEvent.PropertiesChanged -> "propriétés modifiées : ${event.changed}"
    is WindowEvent.CloseRequested -> "demande de fermeture (raison=${event.reason}, rejet possible=${event.canReject})"
    is WindowEvent.Closing -> "en fermeture (raison=${event.reason})"
}

/** Un libellé court par événement de surface, au lieu du `toString()` complet. */
private fun describe(event: SurfaceEvent): String = when (event) {
    is SurfaceEvent.MetricsChanged -> "métriques : ${event.state.logicalSize} @ ×${event.state.scaleFactor}"
    is SurfaceEvent.FocusChanged -> "focus : ${event.state.focus}"
    is SurfaceEvent.VisibilityChanged -> "visibilité : ${event.state.visibility}, occlusion : ${event.state.occlusion}"
    is SurfaceEvent.AppearanceChanged -> "apparence : ${event.state.appearance}"
    is SurfaceEvent.RedrawRequested -> "redessin demandé"
}
