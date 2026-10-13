package org.graphiks.kadre.samples.hellokadre

import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.platform.web.attachKadre
import org.w3c.dom.HTMLElement

/** L'id du conteneur hôte : la page (index.html) doit le déclarer. */
private const val CONTAINER_ID = "hello-kadre"

/**
 * Hello Kadre sur le web : le bootstrap tient en quelques lignes. La session
 * s'attache au conteneur de la page, qui EST la fenêtre du sample (pas de
 * `requestWindow` — Kadre refuse une fenêtre enfant du même document —, pas de
 * fermeture système), puis le corps partagé [helloKadreApplication] (commonMain)
 * observe tout et imprime dans la console du navigateur (DevTools).
 */
public fun main() {
    val container = document.getElementById(CONTAINER_ID) as? HTMLElement
        ?: error("La page du host web doit déclarer #$CONTAINER_ID (conteneur hôte du sample).")
    // Le scope parent vit en dehors de Kadre : c'est lui qui possède l'attachement.
    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val attached = container.attachKadre(
        parentScope,
        // Étape 2 (web) — la surface primaire est publiée avant que le bloc ne
        // tourne : le conteneur la porte ; pas de fenêtre, `window = null`.
        application = helloKadreApplication {
            HelloKadreSurface(window = null, surface = checkNotNull(primarySurface.value))
        },
    )
    when (attached) {
        is KadreResult.Success -> Unit
        is KadreResult.Failure -> error("Impossible d'attacher Kadre au conteneur : ${attached.reason}")
    }
}
