package org.graphiks.kadre.consumer

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.MainScope
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.platform.android.attachKadre
import org.junit.runner.RunWith

/**
 * Smoke consumateur sur l'API publique résolue depuis l'umbrella PUBLIÉ
 * (`org.graphiks.kadre:kadre`, repository contractuel — jamais le projet inclus).
 *
 * Phase 1 : les trois scénarios AND-001 consomment les overloads publics `attachKadre`
 * (l'API jetante de la phase 0 n'existe plus). Les noms de méthodes sont LES MÊMES —
 * ce sont les `testName` contractuels de la ligne AND-001 du registre, le mapping survit.
 *
 * Assertions alignées sur l'API `KadreSession` réelle : `state : StateFlow<SessionState>`
 * avec `Starting`/`Running`/`Stopping`/`Terminated(outcome)`. L'attach retourne un
 * `KadreResult<KadreSession>` : un succès a démarré (le passage `Starting` → `Running`
 * est publié par le looper main, donc `Starting` est l'état déterministe observé ici) ;
 * `close()` (arrêt coopératif demandé par l'hôte) publie
 * `Terminated(SessionOutcome.Stopped(SessionStopReason.HostRequested))` — la terminaison
 * est attendue par observation bornée, jamais lue en course avec le looper main.
 * Un second attach sur une View déjà revendiquée est un
 * `KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host))` — plus jamais
 * d'exception sur ce chemin.
 */
@RunWith(AndroidJUnit4::class)
class KadreConsumerSessionTest {

    @Test
    fun androidConsumerSessionStart() {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val scope = MainScope()
                val handle = ConsumerTestSupport.success(
                    activity.consumerView.attachKadre(
                        lifecycleOwner = activity,
                        applicationFactory = ConsumerTestSupport.applicationFactory,
                        parentScope = scope,
                    ),
                )
                // Une session retournée a démarré : Starting ou déjà Running — jamais terminée.
                assertTrue(
                    handle.state.value in setOf(SessionState.Starting, SessionState.Running),
                )
                // Le nettoyage ferme la session et libère l'ownership (prouvé par le scénario stop).
                handle.close()
            }
        }
    }

    @Test
    fun androidConsumerSessionStopCleanup() {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val scope = MainScope()
                val handle = ConsumerTestSupport.success(
                    activity.consumerView.attachKadre(
                        lifecycleOwner = activity,
                        applicationFactory = ConsumerTestSupport.applicationFactory,
                        parentScope = scope,
                    ),
                )
                // La session Kadre est terminée (arrêt coopératif demandé par l'hôte) ;
                // un second close est idempotent.
                handle.close()
                handle.close()
                val state = ConsumerTestSupport.awaitTerminal(
                    handle,
                    SessionOutcome.Stopped(SessionStopReason.HostRequested),
                )
                assertEquals(
                    SessionOutcome.Stopped(SessionStopReason.HostRequested),
                    (state as SessionState.Terminated).outcome,
                )
            }
        }
    }

    @Test
    fun androidConsumerDoubleAttachRejected() {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val scope = MainScope()
                val first = ConsumerTestSupport.success(
                    activity.consumerView.attachKadre(
                        lifecycleOwner = activity,
                        applicationFactory = ConsumerTestSupport.applicationFactory,
                        parentScope = scope,
                    ),
                )
                val secondAttempt = activity.consumerView.attachKadre(
                    lifecycleOwner = activity,
                    applicationFactory = ConsumerTestSupport.applicationFactory,
                    parentScope = scope,
                )
                assertTrue(
                    secondAttempt is KadreResult.Failure,
                    "expected a Failure, got $secondAttempt",
                )
                assertEquals(
                    KadreFailure.AlreadyInUse(KadreResourceKind.Host),
                    (secondAttempt as KadreResult.Failure).reason,
                )
                first.close()
                // Ownership libéré : un re-attach sur la même View réussit.
                val second = ConsumerTestSupport.success(
                    activity.consumerView.attachKadre(
                        lifecycleOwner = activity,
                        applicationFactory = ConsumerTestSupport.applicationFactory,
                        parentScope = scope,
                    ),
                )
                second.close()
            }
        }
    }
}
