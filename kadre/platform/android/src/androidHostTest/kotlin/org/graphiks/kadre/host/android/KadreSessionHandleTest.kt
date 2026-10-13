package org.graphiks.kadre.host.android

import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionId
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.application.SessionStopReason

/**
 * Les sémantiques exactes du billet Java (INTEROP-EXPORTS §4, ruling P9-0) : l'observation
 * immédiate puis séquentielle, la désinscription sur exception du consumer SANS tuer la
 * session, le stage stable et annulable par waiter, la fermeture idempotente.
 *
 * Les sessions Android ne sont pas attachables hors émulateur (aucune View constructible,
 * cf. `AndroidViewLeaseTest`) : le handle est construit directement sur un faux
 * `KadreSession` piloté par un `MutableStateFlow` — le miroir des fakes P1. Le dispatcher
 * injecté est celui du `TestScope` : chaque tour du collecteur et du waiter de terminaison
 * est explicite (`advanceUntilIdle`), le même parti pris que les tests de flux web.
 */
class KadreSessionHandleTest {

    @Test
    fun observersImmediateThenSequential() {
        val harness = Harness(SessionState.Starting)
        val events = mutableListOf<String>()
        val observerA = Consumer<KadreSessionSnapshot> { events += "A:${it.phase}" }
        val observerB = Consumer<KadreSessionSnapshot> { events += "B:${it.phase}" }

        val tokenA = harness.handle.observeState(observerA)
        val tokenB = harness.handle.observeState(observerB)

        // Chacun reçoit le snapshot COURANT, immédiatement, au retour de l'appel.
        assertEquals(listOf("A:Starting", "B:Starting"), events)

        // Un changement : les DEUX reçoivent, séquentiellement, dans l'ordre d'inscription.
        harness.session.flow.value = SessionState.Stopping
        harness.pump()
        assertEquals(listOf("A:Starting", "B:Starting", "A:Stopping", "B:Stopping"), events)

        // Fermer le token retire l'observer SANS arrêter la session.
        tokenA.close()
        harness.session.flow.value = SessionState.Terminated(
            SessionOutcome.Stopped(SessionStopReason.HostRequested),
        )
        harness.pump()
        assertEquals(
            listOf("A:Starting", "B:Starting", "A:Stopping", "B:Stopping", "B:Terminated"),
            events,
        )
        assertEquals(0, harness.session.closeCount)
        assertEquals(0, harness.session.stopCount)
        harness.shutdown()
    }

    @Test
    fun observerExceptionUnregistersWithoutKillingSession() {
        val harness = Harness(SessionState.Running)
        val phases = mutableListOf<KadreSessionPhase>()
        val boom = IllegalStateException("observer boom")
        val failing = Consumer<KadreSessionSnapshot> { throw boom }
        val healthy = Consumer<KadreSessionSnapshot> { phases += it.phase }

        // L'observer lançant à l'émission immédiate : désinscrit, l'exception remonte au
        // handler installé (capturé par la couture) — JAMAIS à la session ni au caller.
        harness.handle.observeState(failing)
        assertEquals(listOf<Throwable>(boom), harness.failures)

        // L'observer sain continue : émission immédiate puis le changement suivant.
        harness.handle.observeState(healthy)
        assertEquals(listOf(KadreSessionPhase.Running), phases)
        harness.session.flow.value = SessionState.Stopping
        harness.pump()
        assertEquals(listOf(KadreSessionPhase.Running, KadreSessionPhase.Stopping), phases)

        // Exactement UN rapport — le lançant ne reçoit plus rien ; la session vit : aucun
        // stop ni close demandé, l'observation ordinaire continue d'alimenter le billet.
        assertEquals(listOf<Throwable>(boom), harness.failures)
        assertEquals(0, harness.session.stopCount)
        assertEquals(0, harness.session.closeCount)
        assertEquals(KadreSessionPhase.Stopping, harness.handle.state().phase)
        harness.shutdown()
    }

    @Test
    fun terminationStageIsStableAndCancellable() {
        val harness = Harness(SessionState.Running)
        val stage = harness.handle.termination()

        // LE MÊME CompletionStage à chaque appel.
        assertSame(stage, harness.handle.termination())

        // Chaque waiter arme SON future dérivé : annuler l'un n'arrête PAS la session.
        val activeWaiter = stage.toCompletableFuture()
        val cancelledWaiter = stage.toCompletableFuture()
        cancelledWaiter.cancel(false)
        assertEquals(0, harness.session.stopCount)
        assertEquals(0, harness.session.closeCount)

        // Un waiter actif posé sur le stage lui-même (whenComplete).
        var stageOutcome: SessionOutcome? = null
        stage.whenComplete { outcome, _ -> stageOutcome = outcome }

        // requestStop délègue, puis la terminaison réelle complète les waiters actifs
        // avec `Stopped(HostRequested)` ; le waiter annulé, lui, reste annulé.
        harness.handle.requestStop()
        assertEquals(1, harness.session.stopCount)
        harness.session.flow.value = SessionState.Terminated(
            SessionOutcome.Stopped(SessionStopReason.HostRequested),
        )
        harness.pump()

        assertTrue(cancelledWaiter.isCancelled)
        assertTrue(activeWaiter.isDone && !activeWaiter.isCompletedExceptionally)
        assertEquals(
            SessionOutcome.Stopped(SessionStopReason.HostRequested),
            activeWaiter.get(5, TimeUnit.SECONDS),
        )
        assertEquals(SessionOutcome.Stopped(SessionStopReason.HostRequested), stageOutcome)
        assertEquals(KadreSessionPhase.Terminated, harness.handle.state().phase)
        assertEquals(
            SessionOutcome.Stopped(SessionStopReason.HostRequested),
            harness.handle.state().outcome,
        )
        harness.shutdown()
    }

    @Test
    fun closeStopsTheSessionAndIsIdempotent() {
        val harness = Harness(SessionState.Running)
        val phases = mutableListOf<KadreSessionPhase>()
        harness.handle.observeState(Consumer { phases += it.phase })
        assertEquals(listOf(KadreSessionPhase.Running), phases)

        // close() = l'arrêt host demandé à la session + la libération du billet.
        harness.handle.close()
        assertEquals(1, harness.session.closeCount)

        // Les observers ne reçoivent plus rien après la fermeture.
        harness.session.flow.value = SessionState.Terminated(
            SessionOutcome.Stopped(SessionStopReason.HostRequested),
        )
        harness.pump()
        assertEquals(listOf(KadreSessionPhase.Running), phases)

        // La terminaison réelle complète quand même le stage — le waiter survit au close.
        assertEquals(
            SessionOutcome.Stopped(SessionStopReason.HostRequested),
            harness.handle.termination().toCompletableFuture().get(5, TimeUnit.SECONDS),
        )

        // Double close : sans effet (pas de second arrêt).
        harness.handle.close()
        assertEquals(1, harness.session.closeCount)

        // Une observation post-close est muette d'office (le modèle Swift `alreadyClosed`).
        harness.handle.observeState(Consumer { phases += it.phase })
        assertEquals(listOf(KadreSessionPhase.Running), phases)
        harness.shutdown()
    }

    /** Le harnais d'un test : scope de test injecté, faux session, échecs d'observer capturés. */
    private class Harness(initial: SessionState) {
        val scope = TestScope()
        val session = FakeKadreSession(initial)
        val failures = mutableListOf<Throwable>()
        val handle = KadreSessionHandle(
            session,
            CoroutineContextHandleDispatcher(scope.coroutineContext),
        ) { cause -> failures += cause }

        fun pump() {
            scope.advanceUntilIdle()
        }

        fun shutdown() {
            scope.cancel()
        }
    }
}

/**
 * Le faux session des host tests : un `MutableStateFlow` piloté par le test, les compteurs
 * d'arrêt, et un `awaitTermination` fidèle au contrat Kotlin (l'outcome du terminal).
 * Le `SessionId` est constructible ici — `androidHostTest` est friend de foundation.
 */
internal class FakeKadreSession(initial: SessionState = SessionState.Running) : KadreSession {
    val flow = MutableStateFlow(initial)
    var closeCount = 0
    var stopCount = 0

    override val id: SessionId = SessionId(1)
    override val state: StateFlow<SessionState> get() = flow

    override fun close() {
        closeCount += 1
    }

    override fun requestStop() {
        stopCount += 1
    }

    override suspend fun awaitTermination(): SessionOutcome {
        val terminal = flow.first { it is SessionState.Terminated } as SessionState.Terminated
        return terminal.outcome
    }
}
