package org.graphiks.kadre.consumer

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.MainScope
import org.junit.runner.RunWith
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.platform.android.attachKadre

/**
 * Smoke consumateur sur l'API publique résolue depuis l'umbrella PUBLIÉ
 * (`org.graphiks.kadre:kadre`, repository contractuel — jamais le projet inclus).
 *
 * Les noms de méthodes sont les `evidenceId` contractuels de la ligne AND-001.
 * Assertions alignées sur l'API `KadreSession` réelle : `state : StateFlow<SessionState>`
 * avec `Starting`/`Running`/`Stopping`/`Terminated(outcome)` — la session retournée par
 * `attachKadre` a démarré (le passage `Starting` → `Running` est publié par le looper
 * main, donc `Starting` est l'état déterministe observé ici) ; `stop()` publie
 * `Stopping` puis `Terminated(SessionOutcome.Stopped(SessionStopReason.HostRequested))`
 * de façon synchrone quand il est appelé avant le premier tick du looper.
 */
@RunWith(AndroidJUnit4::class)
class KadreConsumerSessionTest {

    @Test
    fun androidConsumerSessionStart() {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val scope = MainScope()
                val handle = attachKadre(activity.consumerView, scope)
                // Une session retournée a démarré : Starting ou déjà Running — jamais terminée.
                assertTrue(
                    handle.session.state.value in setOf(SessionState.Starting, SessionState.Running),
                )
                // Le nettoyage ferme la session et libère l'ownership (prouvé par le scénario stop).
                handle.stop()
            }
        }
    }

    @Test
    fun androidConsumerSessionStopCleanup() {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val scope = MainScope()
                val handle = attachKadre(activity.consumerView, scope)
                handle.stop()
                // La session Kadre est terminée (arrêt coopératif demandé par l'hôte) ;
                // un second stop est idempotent.
                handle.stop()
                val state = handle.session.state.value
                assertTrue(state is SessionState.Terminated, "expected Terminated, got $state")
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
                val first = attachKadre(activity.consumerView, scope)
                assertFailsWith<IllegalStateException> {
                    attachKadre(activity.consumerView, scope)
                }
                first.stop()
                // Ownership libéré : un re-attach sur la même View réussit.
                val second = attachKadre(activity.consumerView, scope)
                second.stop()
            }
        }
    }
}
