package org.graphiks.kadre.consumer

import kotlinx.coroutines.awaitCancellation
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreLaunchContext
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.surface.HostSurface

/**
 * Le registre des faits applicatifs du consumer — le même patron que les fixtures du driver
 * (phase 1 : `DriverSceneStore`/`DriverFixture`) : TOUT ce que les XCTest du consumer lisent
 * sur la session vivante, capturé ICI au run de l'application (l'application du consumer est
 * KOTLIN — le scope Kadre n'arrive qu'à son `run`, la façade Swift ne peut pas le lire).
 *
 * Threading : écriture au run de l'application (le dispatcher Main de la session — le scope
 * de session est `SupervisorJob() + Dispatchers.Main`, `KadreIos.attach`), lectures depuis
 * les XCTest sur le thread principal : LE MÊME fil — aucun verbe atomique nécessaire.
 *
 * Fraîcheur : `runCount` s'incrémente À CHAQUE run — les faits d'une session précédente ne
 * peuvent pas passer pour ceux de la session courante (le test note le compteur AVANT son
 * attach et n'accepte qu'un compteur strictement supérieur, le patron
 * `waitUntilApplicationFacts(minimumRuns:)` du driver).
 */
public object ConsumerFixture {
    /** Le nombre d'applications démarrées — la sentinelle de fraîcheur des faits capturés. */
    public var runCount: Int = 0
        private set

    /**
     * La surface primaire capturée au DERNIER run — celle de la session vivante. `null`
     * seulement si un run a tourné sans surface primaire (jamais observé : l'admission de la
     * fenêtre primaire précède Running, donc précède le run).
     */
    public var primarySurface: HostSurface? = null
        private set

    /** Appelé au début de chaque run applicatif — capture les faits de LA session vivante. */
    public fun record(surface: HostSurface?) {
        runCount += 1
        primarySurface = surface
    }
}

/**
 * La factory TENUE (held) du consumer : l'application suspend son run indéfiniment — la
 * session ne s'arrête que lorsque le HOST la ferme (`requestStop`/`close` → cancellation du
 * job applicatif par `SessionRuntime.requestTermination` ; `applicationCompleted` voit alors
 * un outcome déjà sélectionné et ne le remplace pas : `Stopped(HostRequested)`). C'est la
 * factory des tests du `KadreHostViewController` (Task 5) : la self-stop de
 * `ConsumerApplication` (~500 ms) arrêterait la session PENDANT les assertions (retrait
 * temporaire, re-add, preuve de surface).
 *
 * Même posture que `ConsumerApplicationFactory` : type public du module applicatif, exporté
 * au header (préfixe `KadreUikit`), instancié en Swift et passé à l'attach.
 */
public class HeldApplicationFactory : KadreApplicationFactory {
    public override fun create(context: KadreLaunchContext): KadreApplication = HeldApplication
}

/**
 * L'application tenue du consumer. La capture des faits (miroir `SceneFixture.capture` du
 * driver) est faite AU DÉBUT du run : la surface primaire de CETTE session — le
 * `primarySurfaceProvider` de la session lit `runtime.attachedPrimarySurface`, admis avant
 * Running donc présent au run (finding phase 1 : `hasApplicationFacts` exige
 * `primarySurfaceId != nil` au run).
 */
private object HeldApplication : KadreApplication {
    public override suspend fun KadreScope.run() {
        ConsumerFixture.record(primarySurface.value)
        awaitCancellation()
    }
}
