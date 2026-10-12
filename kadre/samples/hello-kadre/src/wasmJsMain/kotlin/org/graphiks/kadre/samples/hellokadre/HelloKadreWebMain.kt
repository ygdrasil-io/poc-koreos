package org.graphiks.kadre.samples.hellokadre

import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.platform.web.attachKadre
import org.w3c.dom.HTMLElement

/** L'id du conteneur hôte : la page (index.html) doit le déclarer. */
private const val CONTAINER_ID = "hello-kadre"

/**
 * Hello Kadre sur le web : le même sample que le bureau, transporté sur la page.
 *
 * Le web n'a pas de fenêtres OS : `attachKadre` attache la session au conteneur,
 * qui EST la fenêtre du sample. Pas de `requestWindow` (Kadre refuse une fenêtre
 * enfant du même document), pas de fermeture système — ce que montre cette
 * variante, c'est la surface (focus, visibilité, métriques), les entrées (souris,
 * clavier) et les écrans, imprimés dans la console du navigateur (DevTools).
 */
public fun main() {
    val container = document.getElementById(CONTAINER_ID) as? HTMLElement
        ?: error("La page du host web doit déclarer #$CONTAINER_ID (conteneur hôte du sample).")
    // Le scope parent vit en dehors de Kadre : c'est lui qui possède l'attachement.
    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val attached = container.attachKadre(
        parentScope,
        application = KadreApplication {
            // La surface primaire est publiée avant que ce bloc ne tourne : le
            // conteneur la porte, l'attachement est l'équivalent web de l'étape 2
            // du bureau (requestWindow).
            val surface = checkNotNull(primarySurface.value)
            printDisplays()
            println("[surface] attachée au conteneur — interagissez avec la page, la console suit")

            // Les mêmes relais que le bureau (étapes 4 et 5), sur la surface primaire.
            launch(start = CoroutineStart.UNDISPATCHED) {
                surface.events.collect { event -> println("[surface] ${describe(event)}") }
            }
            launch(start = CoroutineStart.UNDISPATCHED) {
                surface.input.events.collect { event -> println("[entrée] $event") }
            }
            // Une page ne se ferme pas : le bloc application vit tant que la
            // session vit, et l'annulation du scope emporte ses collecteurs.
            awaitCancellation()
        },
    )
    when (attached) {
        is KadreResult.Success -> Unit
        is KadreResult.Failure -> error("Impossible d'attacher Kadre au conteneur : ${attached.reason}")
    }
}
