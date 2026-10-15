package org.graphiks.kadre.consumer

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreLaunchContext
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.surface.HostSurface

/**
 * La factory TENUE de la scène de coexistence tvOS (Task 7) — la variante RÉACTIVE de
 * `HeldApplicationFactory` : même session longue (`awaitCancellation` — le sample est
 * navigué à la télécommande, la self-stop de `ConsumerApplication` ~500 ms fermerait la
 * session pendant la navigation), mais la capture de la surface primaire est faite au
 * PREMIER MOMENT OÙ ELLE EXISTE (`primarySurface.filterNotNull().first()`), pas au début
 * du run. Pourquoi la divergence : `HeldApplication` capture `primarySurface.value` AU
 * DÉBUT du run — dans les flows driver/Task 5, l'admission de la fenêtre primaire précède
 * toujours le run (finding phase 1) ; dans la scène de coexistence POSÉE PAR UN TEST
 * (fenêtre propre, `isHidden` sans makeKey, sur la scène de l'app hôte déjà active),
 * l'attach controller-driven peut gagner la course contre l'admission et le run démarre
 * avec une surface encore null — la capture unique ratée resterait null à vie (un seul
 * run par session tenue), et `ConsumerTextInputBridge` (qui lit `ConsumerFixture.
 * primarySurface`) échouerait « no primary surface ». La capture réactive est strictement
 * plus forte : `runCount` garde sa sémantique de fraîcheur (un run = une incrémentation),
 * `primarySurface` est simplement mise à jour AU MOMENT OÙ ELLE DEVIENT VRAIE.
 *
 * Même posture que `ConsumerApplicationFactory` : type public du module applicatif,
 * exporté au header (préfixe `KadreUikit`), instancié en Swift et passé à l'attach.
 */
public class ConsumerCoexistenceApplicationFactory : KadreApplicationFactory {
    public override fun create(context: KadreLaunchContext): KadreApplication =
        ConsumerCoexistenceApplication
}

/**
 * L'application tenue RÉACTIVE du sample tvOS — la capture de la surface au premier
 * moment où elle existe (voir l'en-tête de la factory), puis la session tient.
 */
private object ConsumerCoexistenceApplication : KadreApplication {
    public override suspend fun KadreScope.run() {
        val surface: HostSurface = primarySurface.filterNotNull().first()
        ConsumerFixture.record(surface)
        awaitCancellation()
    }
}
