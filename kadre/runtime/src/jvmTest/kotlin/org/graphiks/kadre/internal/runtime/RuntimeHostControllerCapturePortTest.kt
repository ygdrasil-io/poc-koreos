package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.capture.CaptureCapabilities
import org.graphiks.kadre.capture.CaptureCursorMode
import org.graphiks.kadre.capture.CapturePermissionScope
import org.graphiks.kadre.capture.CapturePermissionState
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSources
import org.graphiks.kadre.capture.CaptureTargetConstraints
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.PermissionState
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalPoint
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

class RuntimeHostControllerCapturePortTest {
    @Test
    fun `withPrimarySurface forwards the capture port to the session`() = runTest {
        val capturePort = StubCapturePort(
            CapturePortSnapshot(
                permissions = CapturePermissionState(PermissionState.NotDetermined, PermissionState.NotDetermined),
                capabilities = CaptureCapabilities(
                    screen = supportedConstraints(),
                    window = supportedConstraints(),
                    surface = supportedConstraints(),
                    sourceEnumeration = Capability.Supported(Unit, FeatureAvailability.Available),
                    hostPicker = FeatureAvailability.Available,
                ),
                sources = CapturePortSources.HostPickerOnly,
            ),
        )
        val host = RuntimeHostController.withPrimarySurface(
            platform = KadrePlatform.Fake,
            sessionRevocationHandler = RuntimeSessionRevocationHandler { },
            primarySurfaceFactory = ::stubRuntimePrimarySurface,
            capturePort = capturePort,
        )
        lateinit var observed: KadreScope

        val session = attach(host) {
            observed = this
            awaitCancellation()
        }
        testScheduler.runCurrent()

        val state = observed.capture.state.value
        assertIs<CaptureSources.HostPickerOnly>(state.sources)
        assertEquals(PermissionState.NotDetermined, state.permissions.screen)
        assertEquals(PermissionState.NotDetermined, state.permissions.window)

        session.close()
        testScheduler.runCurrent()
    }

    @Test
    fun `withPrimarySurface without a capture port keeps the unsupported capture manager`() = runTest {
        val host = RuntimeHostController.withPrimarySurface(
            platform = KadrePlatform.Fake,
            sessionRevocationHandler = RuntimeSessionRevocationHandler { },
            primarySurfaceFactory = ::stubRuntimePrimarySurface,
        )
        lateinit var observed: KadreScope

        val session = attach(host) {
            observed = this
            awaitCancellation()
        }
        testScheduler.runCurrent()

        val unavailable = assertIs<CaptureSources.Unavailable>(observed.capture.state.value.sources)
        assertEquals(KadreFailure.Unsupported(KadreOperation.CaptureRefreshSources), unavailable.failure)
        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            observed.capture.open(CaptureRequest()),
        )

        session.close()
        testScheduler.runCurrent()
    }

    private fun kotlinx.coroutines.test.TestScope.attach(
        host: RuntimeHostController,
        application: suspend KadreScope.() -> Unit,
    ): KadreSession = assertIs<KadreResult.Success<KadreSession>>(
        host.attach(this, KadreApplicationFactory { KadreApplication(application) }, KadrePolicies.Default),
    ).value

    private fun stubRuntimePrimarySurface(id: SurfaceId): RuntimePrimarySurface {
        val surface = RuntimeHostSurface(id, initialSurfaceState())
        return RuntimePrimarySurface(surface, surface::close)
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
        appearance = SurfaceAppearance(SurfaceTheme.Unknown, SurfaceContrast.Unknown),
        cursor = org.graphiks.kadre.surface.CursorStyle.System(org.graphiks.kadre.surface.CursorIcon.Default),
        pointerCapture = PointerCaptureMode.None,
        hitTesting = HitTestingMode.Enabled,
        inputDefaultBehavior = InputDefaultBehavior.HostDefault,
        revision = SurfaceRevision(0),
    )

    private fun supportedConstraints(): Capability<CaptureTargetConstraints> = Capability.Supported(
        CaptureTargetConstraints(
            formats = setOf(PixelFormat.Bgra8),
            cursorModes = setOf(CaptureCursorMode.EmbeddedWhenAvailable),
            region = FeatureAvailability.Available,
        ),
        FeatureAvailability.Available,
    )

    private class StubCapturePort(
        override val initialSnapshot: CapturePortSnapshot,
    ) : CapturePort {
        override suspend fun requestPermission(scope: CapturePermissionScope): KadreResult<CapturePortSnapshot> =
            KadreResult.Success(initialSnapshot)

        override suspend fun refreshSources(): KadreResult<CapturePortSnapshot> =
            KadreResult.Success(initialSnapshot)

        override fun installObserver(observer: (KadreResult<CapturePortSnapshot>) -> Unit): AutoCloseable =
            AutoCloseable { }

        override fun close() = Unit
    }
}
