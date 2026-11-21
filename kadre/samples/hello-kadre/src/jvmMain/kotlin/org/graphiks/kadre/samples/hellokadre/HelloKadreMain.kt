package org.graphiks.kadre.samples.hellokadre

import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.runKadreApplication
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec

/**
 * Hello Kadre sur le bureau : le bootstrap tient en quelques lignes. L'entrée
 * ownership démarre le runtime, le bloc ci-dessous demande la fenêtre, puis le
 * corps partagé [helloKadreApplication] (commonMain) observe tout et imprime
 * en console.
 */
public fun main() {
    // Étape 1 — Kadre possède le process : l'entrée ownership démarre le runtime
    // et choisit le backend natif de la plateforme (AppKit sur macOS, Wayland
    // sous Linux…) ; la dernière fenêtre fermée arrête tout (règle fixe).
    val result = runBlocking {
        runKadreApplication {
            // Étape 2 (bureau) — demander la fenêtre et attendre son ouverture ;
            // le reste (écrans, événements, fermeture) vit dans commonMain.
            helloKadreApplication {
                val request = when (
                    val outcome = windows.requestWindow(
                        WindowSpec(title = "Hello Kadre", contentSize = LogicalSize(640.0, 480.0)),
                    )
                ) {
                    is KadreResult.Success -> outcome.value
                    is KadreResult.Failure -> error("Demande de fenêtre refusée : ${outcome.reason}")
                }
                val window = when (val opened = request.await()) {
                    is WindowRequestOutcome.OpenedHere -> opened.window
                    else -> error("La fenêtre ne s'est pas ouverte : $opened")
                }
                HelloKadreSurface(window, window.surface)
            }
        }
    }
    when (result) {
        is KadreResult.Failure -> error("Hello Kadre a échoué : ${result.reason}")
        is KadreResult.Success -> when (val outcome = result.value) {
            is SessionOutcome.Failed -> error("Hello Kadre a échoué : ${outcome.failure}")
            else -> Unit
        }
    }
}
