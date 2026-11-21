package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSources
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayInventory
import org.graphiks.kadre.input.DeviceInventory
import org.graphiks.kadre.input.PermissionState
import org.graphiks.kadre.window.WindowCancellationOutcome
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowRequestState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The unsupported managers of a booted session, driven through [RuntimeProcessController.boot]
 * since the embedded host controller died with the attach world: the managers must exist
 * before the application runs and reject unsupported operations honestly.
 */
class UnsupportedManagersTest {
    @Test
    fun managersExistBeforeApplicationAndRejectUnsupportedOperationsHonestly() = runProcessTest {
        lateinit var scope: KadreScope
        val session = boot {
            scope = this
            awaitCancellation()
        }
        testScheduler.runCurrent()

        assertNull(scope.primarySurface.value)
        assertIs<Capability.Unsupported>(scope.windows.state.value.capabilities.requestWindow)
        assertEquals(
            DisplayInventory.Unavailable(KadreFailure.Unsupported(KadreOperation.DisplayAccess)),
            scope.displays.state.value.inventory,
        )
        assertIs<DeviceInventory.Unsupported>(scope.devices.state.value.inventory)
        assertEquals(
            CaptureSources.Unavailable(KadreFailure.Unsupported(KadreOperation.CaptureRefreshSources)),
            scope.capture.state.value.sources,
        )
        assertEquals(
            PermissionState.Unavailable(KadreFailure.Unsupported(KadreOperation.CapturePermission)),
            scope.capture.state.value.permissions.screen,
        )
        assertEquals(scope.capture.state.value.permissions.screen, scope.capture.state.value.permissions.window)
        assertEquals(0L, scope.diagnostics.counters.value.eventLosses)

        val request = assertIs<KadreResult.Success<org.graphiks.kadre.window.WindowRequest>>(
            scope.windows.requestWindow(),
        ).value
        val rejected = WindowRequestOutcome.Rejected(KadreFailure.Unsupported(KadreOperation.RequestWindow))
        assertEquals(WindowRequestState.Terminated(rejected), request.state.value)
        assertEquals(rejected, request.await())
        assertEquals(WindowCancellationOutcome.AlreadyTerminated(rejected), request.cancel())
        request.close()
        request.close()

        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.DisplayAccess)),
            scope.displays.requestAccess(),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureRefreshSources)),
            scope.capture.refreshSources(),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            scope.capture.open(CaptureRequest()),
        )

        session.close()
        testScheduler.runCurrent()
    }

    @Test
    fun rejectedWindowRequestsReceiveProcessUniqueIds() = runProcessTest {
        val controller = controller()
        lateinit var firstScope: KadreScope
        lateinit var secondScope: KadreScope
        val first = boot(controller) { firstScope = this; awaitCancellation() }
        testScheduler.runCurrent()
        first.close()
        testScheduler.advanceUntilIdle()
        val second = boot(controller) { secondScope = this; awaitCancellation() }
        testScheduler.runCurrent()

        val firstRequest = assertIs<KadreResult.Success<org.graphiks.kadre.window.WindowRequest>>(
            firstScope.windows.requestWindow(),
        ).value
        val secondRequest = assertIs<KadreResult.Success<org.graphiks.kadre.window.WindowRequest>>(
            secondScope.windows.requestWindow(),
        ).value
        assertEquals(false, firstRequest.id == secondRequest.id)

        second.close()
        testScheduler.runCurrent()
    }

    private fun controller(): RuntimeProcessController = RuntimeProcessController.withComponents(
        platform = KadrePlatform.Fake,
        componentsFactory = UnsupportedRuntimeSessionComponentsFactory,
    )

    private suspend fun TestScope.boot(
        controller: RuntimeProcessController = controller(),
        application: suspend KadreScope.() -> Unit,
    ): KadreSession = assertIs<KadreResult.Success<KadreSession>>(
        controller.boot(
            RuntimeBootRequest(
                platform = KadrePlatform.Fake,
                applicationFactory = KadreApplicationFactory { KadreApplication(application) },
            ),
        ),
    ).value

    /**
     * The controller's owned root scope lives on `Dispatchers.Main.immediate`; a process test
     * installs the test scheduler as Main so every owned coroutine is driven by virtual time.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun runProcessTest(body: suspend TestScope.() -> Unit): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            body()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
