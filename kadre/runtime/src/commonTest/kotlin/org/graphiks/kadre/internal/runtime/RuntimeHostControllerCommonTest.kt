package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.KadrePolicies
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
import org.graphiks.kadre.window.WindowManagerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RuntimeHostControllerCommonTest {
    @OptIn(DelicateKadreApi::class)
    @Test
    fun hostSurfaceRawInputIsExplicitlyUnsupported() = runTest {
        val surface = RuntimeHostSurface(SurfaceId(1L), initialSurfaceState())

        val result = surface.input.requestRawInput()

        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RawInputAccess)),
            result,
        )
    }

    @Test
    fun attachedHostExposesItsPrimarySurfaceWithoutCreatingAWindow() = runTest {
        lateinit var suppliedSurface: RuntimeHostSurface
        val controller = RuntimeHostController.withPrimarySurface(KadrePlatform.Web) { id ->
            val surface = RuntimeHostSurface(id, initialSurfaceState()).also { suppliedSurface = it }
            RuntimePrimarySurface(surface, surface::close)
        }
        val scopeReady = CompletableDeferred<KadreScope>()
        val factory = KadreApplicationFactory {
            KadreApplication {
                scopeReady.complete(this)
                awaitCancellation()
            }
        }

        val session = assertIs<KadreResult.Success<org.graphiks.kadre.application.KadreSession>>(
            controller.attach(this, factory, KadrePolicies.Default),
        ).value
        testScheduler.runCurrent()
        val scope = scopeReady.await()

        assertSame(suppliedSurface, scope.primarySurface.value)
        val windows: WindowManagerState = scope.windows.state.value
        assertNull(windows.primary)
        assertTrue(windows.windows.isEmpty())

        session.requestStop()
        testScheduler.runCurrent()
        assertEquals(SurfaceAttachmentState.Detached, suppliedSurface.state.value.attachment)
    }

    @Test
    fun hostAttachmentsReceiveDistinctSessionIds() = runTest {
        val controller = RuntimeHostController.withPrimarySurface(KadrePlatform.Web) { id ->
            val surface = RuntimeHostSurface(id, initialSurfaceState())
            RuntimePrimarySurface(surface, surface::close)
        }
        val factory = KadreApplicationFactory { KadreApplication { awaitCancellation() } }

        val first = assertIs<KadreResult.Success<org.graphiks.kadre.application.KadreSession>>(
            controller.attach(this, factory, KadrePolicies.Default),
        ).value
        val second = assertIs<KadreResult.Success<org.graphiks.kadre.application.KadreSession>>(
            controller.attach(this, factory, KadrePolicies.Default),
        ).value

        assertNotEquals(first.id, second.id)

        first.requestStop()
        second.requestStop()
        testScheduler.runCurrent()
    }

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

    @OptIn(InternalCoroutinesApi::class)
    @Test
    fun parentCancellationRevokesBeforeApplicationCancellation() = runTest {
        val events = mutableListOf<String>()
        val blocker = CompletableDeferred<Unit>()
        val parentJob = SupervisorJob()
        val parentScope = CoroutineScope(parentJob + StandardTestDispatcher(testScheduler))
        val controller = controllerWithRevocation(onRevocation = { events += "revoked" })
        val session = attach(controller, parentScope) {
            coroutineContext[Job]!!.invokeOnCompletion(
                onCancelling = true,
                invokeImmediately = true,
            ) { cause ->
                if (cause != null) events += "application-cancelled"
            }
            withContext(NonCancellable) { blocker.await() }
        }
        testScheduler.runCurrent()

        parentScope.cancel()

        try {
            assertEquals(listOf("revoked", "application-cancelled"), events)
        } finally {
            blocker.complete(Unit)
            testScheduler.runCurrent()
        }
        assertEquals(
            SessionOutcome.Stopped(SessionStopReason.ParentCancelled),
            session.awaitTermination(),
        )
    }

    @OptIn(InternalCoroutinesApi::class)
    @Test
    fun immediateHostDetachRevokesOnceBeforeApplicationCancellation() = runTest {
        val events = mutableListOf<String>()
        val blocker = CompletableDeferred<Unit>()
        val controller = controllerWithRevocation(onRevocation = { events += "revoked" })
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

        controller.detachImmediately()
        controller.detachImmediately()

        try {
            assertEquals(listOf("revoked", "application-cancelled"), events)
            assertEquals(
                SessionState.Terminated(SessionOutcome.Stopped(SessionStopReason.HostDetached)),
                session.state.value,
            )
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
            SessionOutcome.Stopped(SessionStopReason.HostRequested),
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
        parentScope: CoroutineScope = this,
        application: suspend KadreScope.() -> Unit,
    ): KadreSession = assertIs<KadreResult.Success<KadreSession>>(
        controller.attach(
            parentScope,
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
        cursor = CursorStyle.System(org.graphiks.kadre.surface.CursorIcon.Default),
        pointerCapture = PointerCaptureMode.None,
        hitTesting = HitTestingMode.Enabled,
        inputDefaultBehavior = InputDefaultBehavior.HostDefault,
        revision = SurfaceRevision(0),
    )
}
