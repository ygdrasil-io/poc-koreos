package org.graphiks.kadre.consumer

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.host.android.KadreAndroid
import org.graphiks.kadre.host.android.KadreSessionHandle
import org.graphiks.kadre.host.android.KadreSessionPhase
import org.graphiks.kadre.platform.android.attachKadre
import org.junit.runner.RunWith

/**
 * Le compagnon Kotlin des consumers de la façade Java (la même ligne §9 « Java Android »,
 * vue du langage hôte) : les DEUX formes Kotlin `attachKadre` (Activity + View), la facade
 * `KadreAndroid` rappelée depuis Kotlin, et le `when` EXHAUSTIF sur `SessionOutcome` —
 * la preuve consumateur que la surface Kotlin que la façade transpose est complète (zéro
 * `Continuation` nommé, aucune coroutine exigée du caller).
 *
 * Règle de thread : attach et arrêt sur le thread principal (l'admission la dresse),
 * attentes par observation bornée sur le thread d'instrumentation — le looper main reste
 * libre de publier (le parti pris de [ConsumerTestSupport]).
 */
@RunWith(AndroidJUnit4::class)
class KotlinFacadeConsumerTest {

    /**
     * Les deux formes `attachKadre` sur deux hôtes vivants en parallèle (forme Activity sur
     * le decor de l'Activity scénario, forme View sur la vue d'une seconde Activity), puis
     * l'arrêt coopératif de la première et le `when` EXHAUSTIF sur l'outcome terminal —
     * `Stopped(HostRequested)` exact, sans branche `else`.
     */
    @Test
    fun kotlinAttachKadreBothFormsExhaustiveOutcome() {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val firstSession = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                // Forme ACTIVITY : (surfaceView, factory[, policy, parentScope]) → succès.
                firstSession.set(
                    ConsumerTestSupport.success(
                        activity.attachKadre(
                            activity.consumerView,
                            ConsumerTestSupport.applicationFactory,
                        ),
                    ),
                )
            }

            // Forme VIEW : (lifecycleOwner, factory[, policy, parentScope]) — sur la vue
            // d'une seconde Activity (la même vue serait un conflit d'ownership, AND-002).
            val second = ConsumerTestSupport.launchConsumerActivity()
            try {
                ConsumerTestSupport.awaitResumed(second)
                val secondSession = AtomicReference<KadreSession?>(null)
                ConsumerTestSupport.onMain {
                    secondSession.set(
                        ConsumerTestSupport.success(
                            second.consumerView.attachKadre(
                                lifecycleOwner = second,
                                applicationFactory = ConsumerTestSupport.applicationFactory,
                            ),
                        ),
                    )
                }

                val sessionA = ConsumerTestSupport.notNull(firstSession)
                val sessionB = ConsumerTestSupport.notNull(secondSession)
                try {
                    // Les deux formes vivent en parallèle, identités indépendantes.
                    assertNotEquals(sessionA.id, sessionB.id)
                    ConsumerTestSupport.awaitRunning(sessionA)
                    ConsumerTestSupport.awaitRunning(sessionB)

                    // L'arrêt coopératif de la première : terminaison attendue par
                    // observation bornée, puis le when exhaustif — zéro else.
                    ConsumerTestSupport.onMain { sessionA.requestStop() }
                    val state = ConsumerTestSupport.awaitTerminal(
                        sessionA,
                        SessionOutcome.Stopped(SessionStopReason.HostRequested),
                    )
                    val outcome = (state as SessionState.Terminated).outcome
                    val verdict = when (outcome) {
                        is SessionOutcome.Completed -> "completed"
                        is SessionOutcome.Stopped -> "stopped:${outcome.reason}"
                        is SessionOutcome.Failed -> "failed:${outcome.failure}"
                    }
                    assertEquals("stopped:${SessionStopReason.HostRequested}", verdict)
                } finally {
                    ConsumerTestSupport.onMain { sessionB.close() }
                }
            } finally {
                ConsumerTestSupport.onMain { second.finish() }
            }
        }
    }

    /**
     * La façade `KadreAndroid` rappelée DEPUIS Kotlin (la politique par défaut omise —
     * l'argument Kotlin par défaut) : attach, émission immédiate de l'observation, stage
     * de terminaison stable, stop, await, et le `when` exhaustif sur la phase transposée
     * Java-safe — la surface complète est vivante depuis le langage hôte.
     */
    @Test
    fun kotlinFacadePathFromKotlin() {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val handleBox = AtomicReference<KadreSessionHandle?>(null)
            scenario.onActivity { activity ->
                val handle = KadreAndroid.attach(
                    activity,
                    activity.consumerView,
                    ConsumerTestSupport.applicationFactory,
                )
                handleBox.set(handle)
                assertNotEquals("SessionId(<redacted>)", handle.id())
                assertTrue(handle.id().isNotEmpty())

                // L'émission immédiate du snapshot courant — au retour de l'appel.
                val before = handle.state().phase
                val phases = mutableListOf<KadreSessionPhase>()
                handle.observeState { phases += it.phase }
                assertEquals(listOf(before), phases)
                // LE stage stable de §4 — le même objet à chaque appel.
                assertSame(handle.termination(), handle.termination())

                handle.requestStop()
            }

            val handle = ConsumerTestSupport.notNull(handleBox)
            val outcome = handle.termination().toCompletableFuture().get(5, TimeUnit.SECONDS)
            assertEquals(SessionOutcome.Stopped(SessionStopReason.HostRequested), outcome)

            // Le snapshot terminal vu du billet : phase Terminated, outcome présent —
            // le when exhaustif sur la transposition Java-safe de SessionState.
            val terminal = handle.state()
            val label = when (terminal.phase) {
                KadreSessionPhase.Starting -> "starting"
                KadreSessionPhase.Running -> "running"
                KadreSessionPhase.Stopping -> "stopping"
                KadreSessionPhase.Terminated -> "terminated"
            }
            assertEquals("terminated", label)
            val terminalOutcome = assertNotNull(terminal.outcome)
            assertEquals(
                SessionStopReason.HostRequested,
                (terminalOutcome as SessionOutcome.Stopped).reason,
            )
        }
    }
}
