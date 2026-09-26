package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.ContinuousDelivery
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.WindowDeliveryPolicy
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.HostSurface
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RuntimePrimarySurfaceConfigurationTest {
    @Test
    fun hostProvidedPrimarySurfaceReceivesSessionStampSourceAndPolicy() = runTest {
        val recorded = RecordingSurface()
        val controller = RuntimeHostController.withPrimarySurface(
            platform = KadrePlatform.Web,
            primarySurfaceFactory = { id -> RuntimePrimarySurface(recorded.surfaceFor(id)) { } },
        )
        val scope = CoroutineScope(SupervisorJob() + coroutineContext)
        val session = assertIs<KadreResult.Success<KadreSession>>(
            controller.attach(
                scope,
                KadreApplicationFactory { KadreApplication { awaitCancellation() } },
                KadrePolicies.Default,
            ),
        ).value

        val configuration = assertNotNull(
            recorded.configuration,
            "the host surface must receive session configuration",
        )
        assertEquals(1, recorded.installCount, "session configuration must be installed exactly once")
        assertEquals(KadrePolicies.Default.window, configuration.deliveryPolicy)
        assertEquals(KadrePolicies.Default.window.redrawRequests, configuration.deliveryPolicy.redrawRequests)
        assertEquals(KadrePolicies.Default.resources.maxEventCollectorsPerFlow, configuration.maxCollectorsPerFlow)

        val first = configuration.stampSource()
        val second = configuration.stampSource()
        assertTrue(
            second.sequence.value > first.sequence.value,
            "the session stamp source must allocate strictly increasing sequences",
        )

        session.requestStop()
        testScheduler.runCurrent()
    }

    @Test
    fun policyRedrawDeliveryIsForwardedForBufferedProfiles() = runTest {
        val recorded = RecordingSurface()
        val controller = RuntimeHostController.withPrimarySurface(
            platform = KadrePlatform.Web,
            primarySurfaceFactory = { id -> RuntimePrimarySurface(recorded.surfaceFor(id)) { } },
        )
        val scope = CoroutineScope(SupervisorJob() + coroutineContext)
        val session = assertIs<KadreResult.Success<KadreSession>>(
            controller.attach(
                scope,
                KadreApplicationFactory { KadreApplication { awaitCancellation() } },
                KadrePolicies.Recording,
            ),
        ).value

        val configuration = assertNotNull(recorded.configuration)
        assertEquals(KadrePolicies.Recording.window, configuration.deliveryPolicy)
        val declaredRedraw = assertIs<ContinuousDelivery.Buffered>(KadrePolicies.Recording.window.redrawRequests)
        assertEquals(
            ContinuousDelivery.Buffered(declaredRedraw.capacity, declaredRedraw.onOverflow),
            configuration.deliveryPolicy.redrawRequests,
            "a buffered redraw policy must reach the host surface with its overflow shape intact",
        )

        session.requestStop()
        testScheduler.runCurrent()
    }

    private class RecordingSurface {
        var configuration: RecordedConfiguration? = null
            private set
        var installCount: Int = 0
            private set

        fun surfaceFor(surfaceId: SurfaceId): HostSurface =
            object : HostSurface by RuntimeHostSurface(surfaceId, initialState()), RuntimePrimarySurfaceConfiguration {
                override fun installSessionConfiguration(
                    deliveryPolicy: WindowDeliveryPolicy,
                    source: () -> EventStamp,
                    sessionFailureHandler: (KadreFailure) -> Unit,
                    collectorAllocator: Any,
                    maxCollectorsPerFlow: Int,
                ) {
                    this@RecordingSurface.installCount += 1
                    this@RecordingSurface.configuration = RecordedConfiguration(
                        deliveryPolicy = deliveryPolicy,
                        stampSource = source,
                        sessionFailureHandler = sessionFailureHandler,
                        maxCollectorsPerFlow = maxCollectorsPerFlow,
                    )
                }
            }
    }

    private data class RecordedConfiguration(
        val deliveryPolicy: WindowDeliveryPolicy,
        val stampSource: () -> EventStamp,
        val sessionFailureHandler: (KadreFailure) -> Unit,
        val maxCollectorsPerFlow: Int,
    )
}

private fun initialState(): SurfaceState = SurfaceState(
    attachment = SurfaceAttachmentState.Attached,
    logicalSize = LogicalSize(16.0, 16.0),
    physicalSize = PhysicalSize(16, 16),
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
    revision = SurfaceRevision(0L),
)
