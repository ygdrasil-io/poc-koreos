package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * supprimé par la Task 8 — chemins embedded.
 *
 * The relocated embedded host controller's revocation seams: the ordering invariants the
 * embedded owners (target-owned bridges released on revocation) depend on. They live here —
 * not in the process-controller suite — because [RuntimeProcessController] owns its scope and
 * revokes nothing, while the embedded controller still hands embedder scopes to sessions.
 */
class RuntimeEmbeddedHostControllerTest {
    @OptIn(InternalCoroutinesApi::class)
    @Test
    fun ordinaryTerminationRevokesOnceBeforeApplicationCancellation() = runTest {
        val events = mutableListOf<String>()
        val blocker = CompletableDeferred<Unit>()
        val controller = controllerWithRevocation(
            onRevocation = { events += "revoked" },
            onStop = { events += "host-stopping" },
        )
        val session = attach(controller) {
            coroutineContext[Job]!!.invokeOnCompletion(
                onCancelling = true,
                invokeImmediately = true,
            ) { cause ->
                if (cause != null) events += "application-cancelled"
            }
            withContext(NonCancellable) { blocker.await() }
        }
        testScheduler.runCurrent()

        session.requestStop()
        session.requestStop()
        controller.detach()

        try {
            assertEquals(SessionState.Stopping, session.state.value)
            assertEquals(listOf("revoked", "application-cancelled", "host-stopping"), events)
        } finally {
            blocker.complete(Unit)
            testScheduler.runCurrent()
        }
    }

    @Test
    fun revocationFailureIsDiagnosticOnly() = runTest {
        val failure = IllegalStateException("revoke")
        val reported = mutableListOf<Throwable>()
        val controller = controllerWithRevocation(
            onRevocation = { throw failure },
            failureReporter = RuntimeFailureReporter(reported::add),
        )
        val session = attach(controller) { awaitCancellation() }
        testScheduler.runCurrent()

        session.requestStop()
        testScheduler.runCurrent()

        assertEquals(
            SessionOutcome.Stopped(SessionStopReason.SystemTermination),
            session.awaitTermination(),
        )
        assertEquals(listOf<Throwable>(failure), reported)
    }

    private fun controllerWithRevocation(
        onRevocation: () -> Unit,
        onStop: () -> Unit = {},
        failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { },
    ): RuntimeHostController = RuntimeHostController.withPrimarySurface(
        platform = KadrePlatform.Web,
        failureReporter = failureReporter,
        sessionRevocationHandler = RuntimeSessionRevocationHandler { onRevocation() },
        sessionStopHandler = RuntimeSessionStopHandler {
            onStop()
            null
        },
        primarySurfaceFactory = { id ->
            val surface = RuntimeHostSurface(id, initialSurfaceState())
            RuntimePrimarySurface(surface, surface::close)
        },
    )

    private suspend fun kotlinx.coroutines.test.TestScope.attach(
        controller: RuntimeHostController,
        application: suspend KadreScope.() -> Unit,
    ): KadreSession = assertIs<KadreResult.Success<KadreSession>>(
        controller.attach(
            this,
            KadreApplicationFactory { KadreApplication(application) },
            KadrePolicies.Default,
        ),
    ).value

    private fun initialSurfaceState(): SurfaceState = SurfaceState(
        attachment = SurfaceAttachmentState.Attached,
        logicalSize = LogicalSize(1.0, 1.0),
        physicalSize = PhysicalSize(1, 1),
        scaleFactor = 1.0,
        safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
        visibility = SurfaceVisibility.Visible,
        occlusion = SurfaceOcclusion.Visible,
        focus = SurfaceFocus.Focused,
        appearance = SurfaceAppearance(SurfaceTheme.Unknown, SurfaceContrast.Unknown),
        cursor = CursorStyle.System(CursorIcon.Default),
        pointerCapture = PointerCaptureMode.None,
        hitTesting = HitTestingMode.Enabled,
        inputDefaultBehavior = InputDefaultBehavior.HostDefault,
        revision = SurfaceRevision(0),
    )
}
