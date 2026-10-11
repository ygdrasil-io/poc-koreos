package org.graphiks.kadre.consumer

import kotlinx.coroutines.delay
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreLaunchContext
import org.graphiks.kadre.application.KadreScope

/**
 * La factory du module applicatif Kotlin du consumer — le contre-exemple du driver :
 * là où le driver définit sa factory EN SWIFT, celle-ci est produite par le module
 * applicatif Kotlin du consumer (framework statique `KadreUikit` du build
 * consumer-kmp, préfixe Obj-C identique à la distribution), exportée au header Obj-C,
 * instanciée en Swift et passée à `KadreIos.shared.attach(applicationFactory:)`. Le
 * compilateur Swift n'accepte le passage que si les DEUX fragments liés exposent le
 * même type Obj-C (`KadreApplicationFactory`) — l'identité de types par préfixe
 * exporté + lien statique.
 *
 * Forme RÉELLE du `fun interface` foundation (Application.kt) :
 * `fun create(context: KadreLaunchContext): KadreApplication`.
 */
public class ConsumerApplicationFactory : KadreApplicationFactory {
    public override fun create(context: KadreLaunchContext): KadreApplication =
        ConsumerApplication
}

/**
 * L'application du consumer. Forme RÉELLE du `fun interface` foundation :
 * `suspend fun KadreScope.run()` — le scope est le RECEIVER de la fonction membre
 * étendue (PAS un paramètre `run(receiver: ...)`), `requestStop()` s'y lit directement.
 *
 * `run` est suspend sur le dispatcher principal de la scène : JAMAIS de runBlocking ici
 * (deadlock — le pattern phase 1 utilisait un timer pour la même raison). L'application
 * demande l'arrêt depuis sa propre coroutine après un court délai : le test observe
 * Starting → Running, puis Stopping → Terminated avec l'outcome `Stopped(ApplicationRequested)`
 * — la preuve que l'arrêt vient de l'application consumer, pas du host.
 */
internal object ConsumerApplication : KadreApplication {
    private const val SELF_STOP_DELAY_MS = 500L

    public override suspend fun KadreScope.run() {
        delay(SELF_STOP_DELAY_MS)
        requestStop()
    }
}
