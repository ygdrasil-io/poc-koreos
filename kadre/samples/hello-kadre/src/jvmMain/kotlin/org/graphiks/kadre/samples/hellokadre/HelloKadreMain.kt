package org.graphiks.kadre.samples.hellokadre

import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.platform.desktop.DesktopBackend
import org.graphiks.kadre.platform.desktop.DesktopHostOptions
import org.graphiks.kadre.platform.desktop.runKadreApplication
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec

/**
 * Hello Kadre sur le bureau : le bootstrap tient en quelques lignes. La façade
 * desktop démarre le runtime, le bloc ci-dessous demande la fenêtre, puis le
 * corps partagé [helloKadreApplication] (commonMain) observe tout et imprime
 * en console.
 */
public fun main() {
    // Étape 1 — le runtime démarre, choisit le backend natif de la plateforme
    // (AppKit sur macOS, Wayland sous Linux…) ; `stopWhenLastWindowClosed` :
    // la dernière fenêtre fermée arrête tout.
    val outcome = runKadreApplication(
        options = DesktopHostOptions.Standalone(
            backend = DesktopBackend.Auto,
            stopWhenLastWindowClosed = true,
        ),
        // Étape 2 (bureau) — demander la fenêtre et attendre son ouverture ;
        // le reste (écrans, événements, fermeture) vit dans commonMain.
        application = helloKadreApplication {
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
            HelloKadreSurface(window, window.surface)
        },
    )
    if (outcome is SessionOutcome.Failed) error("Hello Kadre a échoué : ${outcome.failure}")
}
