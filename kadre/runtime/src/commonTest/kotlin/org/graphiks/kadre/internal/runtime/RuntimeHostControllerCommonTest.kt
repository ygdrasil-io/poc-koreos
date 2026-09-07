package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
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
import org.graphiks.kadre.surface.SurfaceFocus
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

    private fun initialSurfaceState(): SurfaceState = SurfaceState(
        attachment = SurfaceAttachmentState.Attached,
        logicalSize = LogicalSize(1.0, 1.0),
        physicalSize = PhysicalSize(1, 1),
        scaleFactor = 1.0,
        safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
        visibility = SurfaceVisibility.Visible,
        occlusion = SurfaceOcclusion.Visible,
        focus = SurfaceFocus.Focused,
        theme = SurfaceTheme.Unknown,
        cursor = CursorStyle.System(org.graphiks.kadre.surface.CursorIcon.Default),
        pointerCapture = PointerCaptureMode.None,
        hitTesting = HitTestingMode.Enabled,
        inputDefaultBehavior = InputDefaultBehavior.HostDefault,
        revision = SurfaceRevision(0),
    )
}
