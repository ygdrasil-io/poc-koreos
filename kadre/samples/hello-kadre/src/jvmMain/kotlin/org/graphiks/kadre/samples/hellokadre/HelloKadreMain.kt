package org.graphiks.kadre.samples.hellokadre

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.platform.desktop.DesktopBackend
import org.graphiks.kadre.platform.desktop.DesktopHostOptions
import org.graphiks.kadre.platform.desktop.runKadreApplication
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.window.WindowCloseDecision
import org.graphiks.kadre.window.WindowCloseResponseOutcome
import org.graphiks.kadre.window.WindowEvent
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec

/**
 * Hello Kadre sur le bureau : le plus petit programme fenêtré du dépôt.
 *
 * Aucun rendu, aucun Compose : une fenêtre native s'ouvre et tout ce qui lui
 * arrive — géométrie, focus, touches, souris, scroll — est imprimé dans la
 * console. Fermer la fenêtre (bouton rouge du système) achève la session.
 *
 * Le fil conducteur suit six étapes numérotées dans [main] ; les libellés et
 * l'impression des écrans sont partagés avec la variante web ([HelloKadreWebMain]).
 */
public fun main() {
    // Étape 1 — le runtime démarre, choisit le backend natif de la plateforme
    // (AppKit sur macOS, Wayland sous Linux…) et exécute le bloc `application`
    // dans le scope de la session. `stopWhenLastWindowClosed` : la dernière
    // fenêtre fermée arrête tout.
    val outcome = runKadreApplication(
        options = DesktopHostOptions.Standalone(
            backend = DesktopBackend.Auto,
            stopWhenLastWindowClosed = true,
        ),
        application = KadreApplication {
            printDisplays()

            // Étape 2 — une fenêtre se demande avec une spécification (titre,
            // taille...), puis la demande se résout en attendant son ouverture.
            val request = when (
                val result = windows.requestWindow(
                    WindowSpec(title = "Hello Kadre", contentSize = LogicalSize(640.0, 480.0)),
                )
            ) {
                is KadreResult.Success -> result.value
                is KadreResult.Failure -> error("Demande de fenêtre refusée : ${result.reason}")
            }
            val window = when (val opened = request.await()) {
                is WindowRequestOutcome.OpenedHere -> opened.window
                else -> error("La fenêtre ne s'est pas ouverte : $opened")
            }
            println("[fenêtre] ouverte — interagissez avec elle, la console suit")

            // Étape 3 — les événements de fenêtre : géométrie (déplacer,
            // redimensionner), propriétés modifiées, demandes de fermeture.
            launch(start = CoroutineStart.UNDISPATCHED) {
                window.events.collect { event -> println("[fenêtre] ${describe(event)}") }
            }

            // Étape 4 — les événements de surface : focus, visibilité, dimensions.
            launch(start = CoroutineStart.UNDISPATCHED) {
                window.surface.events.collect { event -> println("[surface] ${describe(event)}") }
            }

            // Étape 5 — les entrées : clavier, souris, scroll. Elles arrivent sans
            // aucun rendu ; le `toString()` de chaque événement est déjà lisible.
            launch(start = CoroutineStart.UNDISPATCHED) {
                window.surface.input.events.collect { event -> println("[entrée] $event") }
            }

            // Étape 6 — la fermeture est un protocole en deux temps : le système
            // demande (bouton rouge), l'application répond. Accepter ferme la
            // fenêtre ; `stopWhenLastWindowClosed` achève alors la session et
            // `runKadreApplication` retourne.
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
            // La session vit encore le temps du démontage ; cette suspension ne
            // reprend qu'à l'annulation du scope, quand la dernière fenêtre a fermé.
            awaitCancellation()
        },
    )
    if (outcome is SessionOutcome.Failed) error("Hello Kadre a échoué : ${outcome.failure}")
}
