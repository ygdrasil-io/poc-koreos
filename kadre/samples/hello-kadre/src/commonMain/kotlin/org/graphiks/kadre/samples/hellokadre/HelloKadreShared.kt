package org.graphiks.kadre.samples.hellokadre

import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.display.DisplayInventory
import org.graphiks.kadre.surface.SurfaceEvent
import org.graphiks.kadre.window.WindowEvent

/**
 * Le partage entre les deux runtimes du sample (bureau JVM et web wasmJs) : les
 * libellés courts des événements et l'impression des écrans. Seuls les bootstraps
 * diffèrent — [org.graphiks.kadre.platform.desktop.runKadreApplication] côté bureau,
 * `attachKadre` côté web.
 */

/** Étape 0 — sortie console : les écrans connus au démarrage de la session. */
internal fun KadreScope.printDisplays() {
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
internal fun describe(event: WindowEvent): String = when (event) {
    is WindowEvent.GeometryChanged -> "géométrie : contenu=${event.state.contentSize}"
    is WindowEvent.PropertiesChanged -> "propriétés modifiées : ${event.changed}"
    is WindowEvent.CloseRequested -> "demande de fermeture (raison=${event.reason}, rejet possible=${event.canReject})"
    is WindowEvent.Closing -> "en fermeture (raison=${event.reason})"
}

/** Un libellé court par événement de surface, au lieu du `toString()` complet. */
internal fun describe(event: SurfaceEvent): String = when (event) {
    is SurfaceEvent.MetricsChanged -> "métriques : ${event.state.logicalSize} @ ×${event.state.scaleFactor}"
    is SurfaceEvent.FocusChanged -> "focus : ${event.state.focus}"
    is SurfaceEvent.VisibilityChanged -> "visibilité : ${event.state.visibility}, occlusion : ${event.state.occlusion}"
    is SurfaceEvent.AppearanceChanged -> "apparence : ${event.state.appearance}"
    is SurfaceEvent.RedrawRequested -> "redessin demandé"
}
