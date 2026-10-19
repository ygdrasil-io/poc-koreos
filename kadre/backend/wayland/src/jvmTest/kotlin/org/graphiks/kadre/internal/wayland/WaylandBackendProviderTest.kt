package org.graphiks.kadre.internal.wayland

import java.util.ServiceLoader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendProvider
import org.graphiks.kadre.internal.runtime.desktop.DesktopEmbeddedRequest
import org.graphiks.kadre.internal.runtime.desktop.DesktopIntegrationKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopStandaloneRequest
import org.graphiks.kadre.policy.KadrePolicies

class WaylandBackendProviderTest {

    @Test
    fun discoveryFindsExactlyOneWaylandProvider() {
        val providers = ServiceLoader.load(DesktopBackendProvider::class.java).toList()
        val provider = providers.single { it.backend == DesktopBackendKind.Wayland }
        assertEquals(emptySet(), provider.supportedIntegrations)
        assertEquals(isLinux(), provider.isAvailable())
    }

    @Test
    fun embeddedAttachOutsideTheEdtIsRejectedBeforeConnecting() {
        // phase 8 : l'attach EDT est conçu ; ce test (thread de test ≠ EDT)
        // épingle le refus normatif hors EDT, sans connexion ouverte
        val provider = WaylandBackendProvider.forTesting { true }
        val request = DesktopEmbeddedRequest(
            parentScope = CoroutineScope(Job()),
            applicationFactory = KadreApplicationFactory { KadreApplication { } },
            integration = DesktopIntegrationKind.AwtEventDispatchThread,
            policy = KadrePolicies.Default,
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("options")),
            provider.attach(request),
        )
    }

    private fun environment(waylandSocket: String? = null): WaylandProbeEnvironment =
        WaylandProbeEnvironment(
            isLinux = true,
            waylandSocket = waylandSocket,
            xdgRuntimeDir = "/run/user/1000",
            waylandDisplay = "wayland-test",
            socketExists = { true },
        )

    private fun standaloneRequest(factoryInvoked: (Boolean) -> Unit): DesktopStandaloneRequest =
        DesktopStandaloneRequest(
            applicationFactory = KadreApplicationFactory {
                factoryInvoked(true)
                KadreApplication { }
            },
            stopWhenLastWindowClosed = true,
            policy = KadrePolicies.Default,
        )

    @Test
    fun unavailableProviderRunIsUnsupportedHostAttach() {
        var factoryInvoked = false
        val provider = WaylandBackendProvider.forTesting(availability = { false })
        val result = provider.run(standaloneRequest { factoryInvoked = it })
        assertEquals(KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach)), result)
        assertFalse(factoryInvoked)
    }

    @Test
    fun runWithWaylandSocketFailsBeforeSession() {
        var factoryInvoked = false
        var bridgeCreated = false
        val provider = WaylandBackendProvider.forTesting(
            availability = { true },
            environment = { environment(waylandSocket = "7") },
            bridgeFactory = { _ -> bridgeCreated = true; FakeBridge() },
            wakeFactory = { SessionFakeWakeup() },
        )
        val result = provider.run(standaloneRequest { factoryInvoked = it })
        assertEquals(
            KadreResult.Failure(KadreFailure.PlatformFailure(KadrePlatform.Wayland, "start", "wayland-socket-unsupported")),
            result,
        )
        assertFalse(factoryInvoked)
        assertFalse(bridgeCreated)
    }

    @Test
    fun absentServerFailsBeforeSessionWithoutCreatingResources() {
        var factoryInvoked = false
        val provider = WaylandBackendProvider.forTesting(
            availability = { true },
            environment = { environment() },
            bridgeFactory = { _ -> throw WaylandConnectException("display-connect-failed") },
            wakeFactory = { SessionFakeWakeup() },
        )
        val result = provider.run(standaloneRequest { factoryInvoked = it })
        assertEquals(
            KadreResult.Failure(KadreFailure.PlatformFailure(KadrePlatform.Wayland, "start", "display-connect-failed")),
            result,
        )
        assertFalse(factoryInvoked)
    }

    @Test
    fun missingGlobalFailsBeforeSession() {
        var factoryInvoked = false
        val bridge = FakeBridge(globals = listOf(WaylandGlobal("wl_compositor", 6)))
        val provider = WaylandBackendProvider.forTesting(
            availability = { true },
            environment = { environment() },
            bridgeFactory = { _ -> bridge },
            wakeFactory = { SessionFakeWakeup() },
        )
        val result = provider.run(standaloneRequest { factoryInvoked = it })
        assertEquals(
            KadreResult.Failure(KadreFailure.PlatformFailure(KadrePlatform.Wayland, "start", "missing-xdg_wm_base")),
            result,
        )
        assertFalse(factoryInvoked)
        assertTrue(bridge.closed)
    }

    @Test
    fun concurrentRunIsAlreadyInUse() {
        val loopReady = CountDownLatch(1)
        val releaseLoop = CountDownLatch(1)
        val bridge = FakeBridge()
        bridge.beforePoll = { releaseLoop.await() }
        var capturedPump: WaylandEventPump? = null
        val provider = WaylandBackendProvider.forTesting(
            availability = { true },
            environment = { environment() },
            bridgeFactory = { _ -> bridge },
            wakeFactory = { SessionFakeWakeup() },
            loopStarted = { pump ->
                capturedPump = pump
                loopReady.countDown()
            },
        )
        val runner = Thread {
            provider.run(standaloneRequest { })
        }
        runner.start()
        assertTrue(loopReady.await(5, TimeUnit.SECONDS))
        val second = provider.run(standaloneRequest { })
        assertEquals(KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host)), second)
        capturedPump!!.requestStop()
        releaseLoop.countDown()
        runner.join(5_000)
        assertFalse(runner.isAlive)
    }

    @Test
    fun completingApplicationStopsTheLoopWithCompletedOutcome() {
        val provider = WaylandBackendProvider.forTesting(
            availability = { true },
            environment = { environment() },
            bridgeFactory = { _ -> FakeBridge() },
            wakeFactory = { SessionFakeWakeup() },
        )
        val outcome = provider.run(
            DesktopStandaloneRequest(
                applicationFactory = KadreApplicationFactory { KadreApplication { } },
                stopWhenLastWindowClosed = true,
                policy = KadrePolicies.Default,
            ),
        )
        assertEquals(KadreResult.Success(SessionOutcome.Completed), outcome)
    }

    @Test
    fun applicationRequestedStopStopsTheLoop() {
        // SN-9 : annulation côté application — `KadreScope.requestStop()`
        // passe par le stopHandler, réveille la pompe et termine la session
        // en `ApplicationRequested` (premier motif terminal conservé).
        val provider = WaylandBackendProvider.forTesting(
            availability = { true },
            environment = { environment() },
            bridgeFactory = { _ -> FakeBridge() },
            wakeFactory = { SessionFakeWakeup() },
        )
        val outcome = provider.run(
            DesktopStandaloneRequest(
                applicationFactory = KadreApplicationFactory { KadreApplication { requestStop() } },
                stopWhenLastWindowClosed = true,
                policy = KadrePolicies.Default,
            ),
        )
        assertEquals(
            KadreResult.Success(SessionOutcome.Stopped(SessionStopReason.ApplicationRequested)),
            outcome,
        )
    }

    @Test
    fun disconnectAfterAdmissionFailsTheSessionWithoutReconnecting() {
        val bridge = FakeBridge()
        var dispatchCalls = 0
        bridge.dispatchPendingResult = true
        val provider = WaylandBackendProvider.forTesting(
            availability = { true },
            environment = { environment() },
            bridgeFactory = { _ -> bridge },
            wakeFactory = { SessionFakeWakeup() },
        )
        // le 2e dispatch de la boucle constate la perte de connexion (F2)
        val watchBridge = object : WaylandNativeBridge by bridge {
            override fun dispatchPending(): Boolean {
                dispatchCalls++
                return dispatchCalls <= 1
            }
        }
        val failingProvider = WaylandBackendProvider.forTesting(
            availability = { true },
            environment = { environment() },
            bridgeFactory = { _ -> watchBridge },
            wakeFactory = { SessionFakeWakeup() },
        )
        val outcome = failingProvider.run(
            DesktopStandaloneRequest(
                applicationFactory = KadreApplicationFactory { KadreApplication { } },
                stopWhenLastWindowClosed = true,
                policy = KadrePolicies.Default,
            ),
        )
        val expected = KadreResult.Success(
            SessionOutcome.Failed(KadreFailure.PlatformFailure(KadrePlatform.Wayland, "display", "connection-lost")),
        )
        assertEquals(expected, outcome)
        assertTrue(bridge.closed)
    }
}
