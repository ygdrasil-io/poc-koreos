package org.graphiks.kadre.internal.win32

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertIs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.desktop.DesktopEmbeddedRequest
import org.graphiks.kadre.internal.runtime.desktop.DesktopIntegrationKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopStandaloneRequest
import org.graphiks.kadre.policy.KadrePolicies

class Win32BackendProviderTest {
    @Test
    fun stopCompletesAWorkerCommandAlreadyWaitingInTheUiQueue() {
        val owner = Thread.currentThread()
        val admitted = CountDownLatch(1)
        val released = java.util.concurrent.atomic.AtomicBoolean(false)
        val queue = Win32UiCommandQueue({ Thread.currentThread() === owner }, { admitted.countDown(); true })
        val port = object : DeterministicWin32HostPort() {
            override fun isMainThread() = Thread.currentThread() === owner
            override fun <T> onMainThread(block: () -> T): T = queue.invoke(block)
            override fun stopWorkerCommands() = queue.stopWorkerAdmission()
            override fun close() { queue.close(); super.close() }
        }
        val provider = Win32BackendProvider.forTesting(availability = { true }, portFactory = { port },
            loopStarted = { pump ->
                assertTrue(admitted.await(2, TimeUnit.SECONDS))
                pump.requestStop()
            })
        val result = provider.run(DesktopStandaloneRequest(
            applicationFactory = KadreApplicationFactory { KadreApplication {
                try { port.onMainThread { error("stopped command must not run") } }
                finally { released.set(true) }
            } }, stopWhenLastWindowClosed = true, policy = KadrePolicies.Default))
        val outcome = assertIs<KadreResult.Success<SessionOutcome>>(result).value
        assertTrue(outcome is SessionOutcome.Stopped || outcome == SessionOutcome.Completed, "shutdown outcome: $outcome")
        assertTrue(released.get(), "shutdown must release the waiting Java future before awaiting application termination")
    }

    @Test
    fun nativeInputReadFailureTerminatesTheSessionAsAPlatformFailure() {
        val provider = Win32BackendProvider.forTesting(
            availability = { true },
            portFactory = { object : DeterministicWin32HostPort() {
                override fun waitForMessage(timeoutMillis: Long): Win32MessageWait = Win32MessageWait.Message(0x100, 0, 0)
                override fun translateAndDispatch(wait: Win32MessageWait.Message) { error("native input read failed") }
            } },
        )
        val result = assertIs<KadreResult.Success<SessionOutcome>>(provider.run(blockingRequest()))
        val failed = assertIs<SessionOutcome.Failed>(result.value)
        assertEquals(KadreFailure.PlatformFailure(KadrePlatform.Win32, "input", "native-input-failed"), failed.failure)
    }

    @Test
    fun driverAndPumpShareTheSameNativePort() {
        var portsCreated = 0
        val provider = Win32BackendProvider.forTesting(
            availability = { true },
            portFactory = { portsCreated += 1; DeterministicWin32HostPort() },
        )
        assertEquals(KadreResult.Success(SessionOutcome.Completed), provider.run(standaloneRequest()))
        assertEquals(1, portsCreated, "the pump and driver must share their UI endpoint")
    }

    private fun blockingRequest(): DesktopStandaloneRequest =
        DesktopStandaloneRequest(
            applicationFactory = KadreApplicationFactory { KadreApplication { awaitCancellation() } },
            stopWhenLastWindowClosed = true,
            policy = KadrePolicies.Default,
        )

    private fun standaloneRequest(): DesktopStandaloneRequest =
        DesktopStandaloneRequest(
            applicationFactory = KadreApplicationFactory { KadreApplication { } },
            stopWhenLastWindowClosed = true,
            policy = KadrePolicies.Default,
        )

    @Test
    fun unsupportedAvailabilityFailsBeforeSessionWithoutCreatingAnything() {
        val port = DeterministicWin32HostPort()
        val provider = Win32BackendProvider.forTesting(
            availability = { false },
            portFactory = { port },
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach)),
            provider.run(standaloneRequest()),
        )
        assertEquals(0, port.createdWindows.get())
    }

    @Test
    fun concurrentRunIsAlreadyInUse() {
        val blocked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val port = object : DeterministicWin32HostPort() {
            override fun waitForMessage(timeoutMillis: Long): Win32MessageWait {
                blocked.countDown()
                release.await(5, TimeUnit.SECONDS)
                return super.waitForMessage(timeoutMillis)
            }
        }
        val provider = Win32BackendProvider.forTesting(
            availability = { true },
            portFactory = { port },
        )
        val first = Thread {
            provider.run(blockingRequest())
        }.apply { isDaemon = true }
        first.start()
        assert(blocked.await(5, TimeUnit.SECONDS))

        val failure = assertIs<KadreResult.Failure>(provider.run(standaloneRequest()))
        assertEquals(
            KadreFailure.AlreadyInUse(KadreResourceKind.Host),
            failure.reason,
        )

        // Le stop traverse le port pendant que le premier run pompe ; le join
        // attend la sortie complète (teardown + lease libéré) — SN-6.
        release.countDown()
        port.enqueueMessage(Win32PumpMessage.StopRequest.ordinal)
        first.join(5_000)
        assertFalse(first.isAlive, "the first standalone run must terminate after its stop")

        // Le lease libéré, une exécution séquentielle repart de zéro (SN-6).
        val nextProvider = Win32BackendProvider.forTesting(
            availability = { true },
            portFactory = {
                DeterministicWin32HostPort().also { it.enqueueMessage(Win32PumpMessage.StopRequest.ordinal) }
            },
        )
        val third = nextProvider.run(blockingRequest())
        val stopped = assertIs<KadreResult.Success<SessionOutcome>>(third)
        assertIs<SessionOutcome.Stopped>(stopped.value)
    }

    @Test
    fun controlWindowStartFailureBecomesPlatformFailureBeforeSession() {
        val provider = Win32BackendProvider.forTesting(
            availability = { true },
            portFactory = {
                val port = DeterministicWin32HostPort()
                port.close()
                port
            },
        )
        val failure = assertIs<KadreResult.Failure>(provider.run(standaloneRequest()))
        assertEquals(
            KadreFailure.PlatformFailure(KadrePlatform.Win32, "start", "control-window"),
            failure.reason,
        )
    }

    @Test
    fun pumpFailureBecomesFailedOutcomeThroughControlledPort() {
        val port = DeterministicWin32HostPort()
        val provider = Win32BackendProvider.forTesting(
            availability = { true },
            portFactory = { port },
        )
        val loopStarted = CountDownLatch(1)
        val providerWithHook = Win32BackendProvider.forTesting(
            availability = { true },
            portFactory = { port },
            loopStarted = { pump ->
                port.forcedWaitResult.set(Win32MessageWait.Error(-1))
                loopStarted.countDown()
            },
        )
        val outcome = providerWithHook.run(blockingRequest())
        // Un outcome de session Failed est un run() réussi qui rapporte l'échec.
        val success = assertIs<KadreResult.Success<SessionOutcome>>(outcome)
        assertEquals(
            SessionOutcome.Failed(
                KadreFailure.PlatformFailure(KadrePlatform.Win32, "pump", "get-message-failed"),
            ),
            success.value,
        )
    }

    @Test
    fun applicationRequestedStopStopsThePumpThroughTheWake() {
        val port = DeterministicWin32HostPort()
        val provider = Win32BackendProvider.forTesting(
            availability = { true },
            portFactory = { port },
        )
        val request = DesktopStandaloneRequest(
            applicationFactory = KadreApplicationFactory {
                KadreApplication { requestStop() }
            },
            stopWhenLastWindowClosed = true,
            policy = KadrePolicies.Default,
        )
        val outcome = provider.run(request)
        val success = assertIs<KadreResult.Success<SessionOutcome>>(outcome)
        assertEquals(SessionOutcome.Stopped(SessionStopReason.ApplicationRequested), success.value)
        assertTrue(port.wakeCount.get() >= 1, "the stop handler must wake the pump")
    }

    @Test
    fun emptyApplicationCompletesThroughThePump() {
        val provider = Win32BackendProvider.forTesting(
            availability = { true },
            portFactory = { DeterministicWin32HostPort() },
        )
        assertEquals(
            KadreResult.Success(SessionOutcome.Completed),
            provider.run(standaloneRequest()),
        )
    }

    @Test
    fun embeddedAttachWithUnbuiltIntegrationIsRefusedNormatively() {
        val provider = Win32BackendProvider()
        val request = DesktopEmbeddedRequest(
            parentScope = CoroutineScope(Job() + EmptyCoroutineContext),
            applicationFactory = KadreApplicationFactory { KadreApplication { } },
            integration = DesktopIntegrationKind.AppKitMainLoop, // jamais construite pour win32
            policy = KadrePolicies.Default,
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach)),
            runBlocking { provider.attach(request) },
        )
    }

    @Test
    fun discoveryOnNonWindowsIsUnavailableWithoutNativeLoad() {
        // Portée portable : hors Windows, la gate réelle est consultée ; sur
        // Windows, la gate injectée reproduit le refus — les deux prouvent
        // que le refus précède toute création et tout chargement natif.
        val realGate = Win32BackendProvider()
        assertEquals(Win32BackendProvider.isWindows(), realGate.isAvailable())
        val provider = Win32BackendProvider.forTesting(
            availability = { false },
            portFactory = { DeterministicWin32HostPort() },
        )
        assertFalse(provider.isAvailable())
        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach)),
            provider.run(standaloneRequest()),
        )
    }
}
