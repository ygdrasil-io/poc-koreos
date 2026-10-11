package org.graphiks.kadre.consumer

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.awaitCancellation
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.surface.SurfaceState

/**
 * Support commun des deux smokes instrumentés consumateurs (AND-001, AND-002).
 *
 * Règle de thread : les méthodes d'attente tournent sur le thread d'instrumentation et
 * laissent le looper main vivre — toutes les publications du runtime (Starting → Running,
 * Stopping → Terminated, publications lifecycle de la surface) arrivent sur ce looper ;
 * l'observation est un polling borné, jamais une lecture en course.
 */
internal object ConsumerTestSupport {

    /** L'application minimale : suspendue pour toujours, zéro effet observable. */
    val applicationFactory: KadreApplicationFactory =
        KadreApplicationFactory { KadreApplication { awaitCancellation() } }

    /** Un scope applicatif indépendant du lifecycle de l'Activity (le modèle de la phase 0). */
    fun independentScope(): CoroutineScope = MainScope()

    /** Déballe l'unique chemin nominal — un échec est une erreur de scénario, nommée. */
    fun success(result: KadreResult<KadreSession>): KadreSession = when (result) {
        is KadreResult.Success -> result.value
        is KadreResult.Failure -> error("expected attach success, got ${result.reason}")
    }

    /** La valeur capturée depuis le thread main — absente : le scénario est en erreur. */
    fun <T : Any> notNull(reference: AtomicReference<T?>): T =
        requireNotNull(reference.get()) { "the reference was never set" }

    /**
     * Lance une [ConsumerActivity] SANS ActivityScenario. Deux ActivityScenario simultanés
     * ne coexistent pas (le second `launch` finit l'Activity du premier via son
     * EmptyActivity) : les scénarios multi-Activities n'en gardent qu'un et lancent les
     * autres à la main.
     */
    fun launchConsumerActivity(): ConsumerActivity {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, ConsumerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        @Suppress("UNCHECKED_CAST")
        return instrumentation.startActivitySync(intent) as ConsumerActivity
    }

    /** Le block sur le thread main, bloquant pour le caller (thread d'instrumentation). */
    fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    /** Attend l'état RESUMED de l'Activity lancée à la main. */
    fun awaitResumed(activity: ConsumerActivity): ConsumerActivity {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        var last = activity.lifecycle.currentState
        while (System.currentTimeMillis() < deadline) {
            last = activity.lifecycle.currentState
            if (last == Lifecycle.State.RESUMED) return activity
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error("the activity never resumed within ${DEFAULT_TIMEOUT_MILLIS}ms; last=$last")
    }

    /** La terminaison attendue — [SessionOutcome] exact, observé par polling borné. */
    fun awaitTerminal(session: KadreSession, expected: SessionOutcome): SessionState =
        awaitState(session) { state -> state is SessionState.Terminated && state.outcome == expected }

    /** L'état `Running` publié par le looper main après le démarrage. */
    fun awaitRunning(session: KadreSession): SessionState =
        awaitState(session) { it == SessionState.Running }

    /** Le [KadreScope] capturé par l'application au démarrage de la session. */
    fun awaitKadreScope(captured: AtomicReference<KadreScope?>): KadreScope {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            captured.get()?.let { return it }
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error("the session's KadreScope was never captured within ${DEFAULT_TIMEOUT_MILLIS}ms")
    }

    /**
     * L'état de la surface primaire de la session — le canal public qui rend les
     * publications lifecycle du host observables (attachment/visibility/focus).
     */
    fun awaitSurfaceState(
        captured: AtomicReference<KadreScope?>,
        predicate: (SurfaceState) -> Boolean,
    ): SurfaceState {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        var last = "no primary surface yet"
        while (System.currentTimeMillis() < deadline) {
            val surface = captured.get()?.primarySurface?.value
            if (surface != null) {
                val state = surface.state.value
                if (predicate(state)) return state
                last = "$state"
            }
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error("surface state never satisfied the predicate within ${DEFAULT_TIMEOUT_MILLIS}ms; last=$last")
    }

    fun awaitState(
        session: KadreSession,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        predicate: (SessionState) -> Boolean,
    ): SessionState {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var current = session.state.value
        while (System.currentTimeMillis() < deadline) {
            current = session.state.value
            if (predicate(current)) return current
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error("session state never satisfied the predicate within ${timeoutMillis}ms; last=$current")
    }

    private const val DEFAULT_TIMEOUT_MILLIS = 10_000L
    private const val POLL_INTERVAL_MILLIS = 25L
}
