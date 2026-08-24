package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
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

class UnsupportedManagersTest {
    @Test
    fun managersExistBeforeApplicationAndRejectUnsupportedOperationsHonestly() = runTest {
        val host = RuntimeHostController(KadrePlatform.Fake)
        lateinit var scope: KadreScope
        val session = attach(host) {
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
    fun rejectedWindowRequestsReceiveProcessUniqueIds() = runTest {
        val firstHost = RuntimeHostController(KadrePlatform.Fake)
        val secondHost = RuntimeHostController(KadrePlatform.Fake)
        lateinit var firstScope: KadreScope
        lateinit var secondScope: KadreScope
        val firstSession = attach(firstHost) { firstScope = this; awaitCancellation() }
        val secondSession = attach(secondHost) { secondScope = this; awaitCancellation() }
        testScheduler.runCurrent()

        val first = assertIs<KadreResult.Success<org.graphiks.kadre.window.WindowRequest>>(
            firstScope.windows.requestWindow(),
        ).value
        val second = assertIs<KadreResult.Success<org.graphiks.kadre.window.WindowRequest>>(
            secondScope.windows.requestWindow(),
        ).value
        assertEquals(false, first.id == second.id)

        firstSession.close()
        secondSession.close()
        testScheduler.runCurrent()
    }

    private fun kotlinx.coroutines.test.TestScope.attach(
        host: RuntimeHostController,
        application: suspend KadreScope.() -> Unit,
    ): KadreSession = assertIs<KadreResult.Success<KadreSession>>(
        host.attach(this, KadreApplicationFactory { KadreApplication(application) }),
    ).value
}
