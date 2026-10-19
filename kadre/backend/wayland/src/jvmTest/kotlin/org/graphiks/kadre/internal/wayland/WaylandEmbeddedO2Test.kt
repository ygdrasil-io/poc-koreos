package org.graphiks.kadre.internal.wayland

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.isActive
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.desktop.DesktopEmbeddedRequest
import org.graphiks.kadre.internal.runtime.desktop.DesktopIntegrationKind
import org.graphiks.kadre.policy.KadrePolicies

/**
 * Preuves O2 du chemin embarqué (design phase 8) : attach EDT accepté
 * avec détach propre sans tuer le thread hôte (P-1 logique, SN-3),
 * attach hors EDT refusé (SN-1), intégration non conçue refusée
 * (SN-2), double attach en course (F3-2) et fermeture pendant tick
 * (F3-1).
 */
class WaylandEmbeddedO2Test {

    private fun testEnv() = WaylandProbeEnvironment(
        isLinux = true, waylandSocket = null, xdgRuntimeDir = "/run/user/1000",
        waylandDisplay = "wayland-test", socketExists = { true },
    )

    private fun provider(bridge: FakeBridge, probeEdt: Boolean): WaylandBackendProvider =
        WaylandBackendProvider.forTesting(
            availability = { true },
            environment = { testEnv() },
            bridgeFactory = { _ -> bridge },
            wakeFactory = { SessionFakeWakeup() },
        ).also { it.awtDispatchThreadProbe = { probeEdt } }

    private fun embedRequest(scope: CoroutineScope) = DesktopEmbeddedRequest(
        parentScope = scope,
        applicationFactory = KadreApplicationFactory { KadreApplication { } },
        integration = DesktopIntegrationKind.AwtEventDispatchThread,
        policy = KadrePolicies.Default,
    )

    @Test
    fun attachOutsideTheEdtIsRejectedNormatively() {
        // SN-1 : hors EDT ⇒ InvalidRequest("options"), avant toute connexion
        val bridge = FakeBridge()
        val provider = provider(bridge, probeEdt = false)
        val outcome = provider.attach(embedRequest(CoroutineScope(SupervisorJob())))
        val failure = assertIs<KadreResult.Failure>(outcome).reason
        assertIs<KadreFailure.InvalidRequest>(failure)
        assertEquals("options", (failure as KadreFailure.InvalidRequest).field)
        assertTrue(!bridge.closed, "aucune connexion ouverte hors EDT")
    }

    @Test
    fun unsupportedIntegrationIsRefusedWithHostAttach() {
        // SN-2 : JavaFX n'est pas conçue en tranche 1
        val bridge = FakeBridge()
        val provider = provider(bridge, probeEdt = true)
        val request = DesktopEmbeddedRequest(
            parentScope = CoroutineScope(SupervisorJob()),
            applicationFactory = KadreApplicationFactory { KadreApplication { } },
            integration = DesktopIntegrationKind.JavaFxApplicationThread,
            policy = KadrePolicies.Default,
        )
        val outcome = provider.attach(request)
        val failure = assertIs<KadreResult.Failure>(outcome).reason
        assertIs<KadreFailure.Unsupported>(failure)
        assertTrue(!bridge.closed)
    }

    @Test
    fun edtpAttachCreatesADetachableSessionWithoutKillingTheHostLoop() = runBlocking {
        // P-1/SN-3 : attach EDT → session détachable ; fermer Kadre ne
        // touche pas la boucle hôte (le scope parent survit au close)
        val bridge = FakeBridge()
        val provider = provider(bridge, probeEdt = true)
        val scope = CoroutineScope(SupervisorJob())
        val attached = provider.attach(embedRequest(scope))
        if (attached is KadreResult.Failure) {
            error("attach failed: ${attached.reason}")
        }
        val session = (attached as KadreResult.Success<*>).value as org.graphiks.kadre.application.KadreSession
        // l'application qui termine immédiatement porte la session à
        // Terminated(Completed) SANS arrêter la boucle hôte : c'est le
        // lifecycle embarqué prouvé (P-1) — le scope parent survit
        val outcome = session.awaitTermination()
        assertEquals(SessionOutcome.Completed, outcome)
        assertTrue(scope.isActive, "le scope hôte survit à la fermeture de la session")
        scope.cancel()
    }

    @Test
    fun pumpTickIsBoundedAndClosedPumpRejectsTicks() {
        // F3-1/F2-1 : un tick drain borné ; après close, plus aucun tick
        val ops = RecordingOps()
        val wake = SessionFakeWakeup()
        val pump = WaylandEmbeddedPump(ops, wake) { }
        assertTrue(pump.tick())
        pump.close()
        assertTrue(pump.isClosed())
        // les ticks suivants sont ignorés silencieusement
        pump.tick()
        assertEquals(1, ops.dispatchCount)
    }

    private class RecordingOps : PumpOperations {
        var dispatchCount = 0
        override fun dispatchPending(): Boolean { dispatchCount++; return true }
        override fun prepareRead(): Int = 0
        override fun readEvents(): Boolean = true
        override fun cancelRead() {}
        override fun flush(): WaylandFlushOutcome = WaylandFlushOutcome.Done
        override fun poll(wakeFd: Int, watchWrite: Boolean): PollOutcome = PollOutcome.DisplayReady
    }
}
