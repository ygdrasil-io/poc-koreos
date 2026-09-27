package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.KadreDiagnostic
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.ContinuousDelivery
import org.graphiks.kadre.policy.InputDeliveryPolicy
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.ResourceBudgetPolicy
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
import org.graphiks.kadre.window.WindowManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
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
        assertEquals(
            KadrePolicies.Default.input,
            configuration.inputDeliveryPolicy,
            "the primary surface must receive the session input delivery policy",
        )
        assertEquals(
            KadrePolicies.Default.resources,
            configuration.resources,
            "the primary surface must receive the session resource budget policy",
        )
        assertNotNull(
            configuration.dropTransferScope,
            "the primary surface must receive the session scope a drop transfer outlives its stimulus in",
        )
        assertNotNull(configuration.diagnostics, "the primary surface must receive the diagnostic channel")
        assertNull(
            configuration.rawInputPort,
            "a session without a raw-input port must deliver none to its primary surface",
        )

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

    /**
     * The parity proof of the extended primary-surface SPI: one session hands its input
     * configuration to both branches, and a primary surface receives exactly what the
     * components-side window manager receives.
     *
     * The policy is [KadrePolicies.Recording], whose input policy and resource budgets differ from
     * the defaults, so a branch that substituted a built-in default instead of forwarding the
     * session configuration fails here.
     */
    @Test
    fun primarySurfaceReceivesTheSameSessionInputConfigurationAsTheComponentsManager() = runTest {
        val recorded = RecordingSurface()
        val components = RecordingComponentsManager()
        val rawInputPort = RecordingRawInputPort()
        val controller = RuntimeHostController.withComponents(
            platform = KadrePlatform.Web,
            componentsFactory = RuntimeSessionComponentsFactory { _, _ ->
                RuntimeSessionComponents(
                    windows = components,
                    primarySurface = RuntimePrimarySurface(recorded.surfaceFor(SurfaceId(0L))) { },
                    rawInputPort = rawInputPort,
                )
            },
        )
        val scope = CoroutineScope(SupervisorJob() + coroutineContext)
        val session = assertIs<KadreResult.Success<KadreSession>>(
            controller.attach(
                scope,
                KadreApplicationFactory { KadreApplication { awaitCancellation() } },
                KadrePolicies.Recording,
            ),
        ).value

        val surface = assertNotNull(
            recorded.configuration,
            "the host surface must receive session configuration",
        )
        val windowManager = assertNotNull(
            components.configuration,
            "the components window manager must receive session configuration",
        )

        assertEquals(
            windowManager.inputDeliveryPolicy,
            surface.inputDeliveryPolicy,
            "the primary surface must receive the very input policy the components branch receives",
        )
        assertEquals(KadrePolicies.Recording.input, surface.inputDeliveryPolicy)
        // The components branch is handed its resource budgets when its backend constructs the
        // window manager for the session, so there is no recorded counterpart to compare: the
        // parity claim is that the primary surface receives the very policy of that session.
        assertEquals(
            KadrePolicies.Recording.resources,
            surface.resources,
            "the primary surface must receive the session resource budget policy, not a built-in default",
        )
        assertEquals(KadrePolicies.Recording.resources.maxEventCollectorsPerFlow, surface.maxCollectorsPerFlow)
        assertEquals(windowManager.maxCollectorsPerFlow, surface.maxCollectorsPerFlow)
        assertSame(
            windowManager.collectorAllocator,
            surface.collectorAllocator,
            "both branches must derive their collector gates from the one session allocator",
        )
        assertSame(
            windowManager.dropTransferScope,
            surface.dropTransferScope,
            "both branches must receive the one session root scope",
        )
        assertNotNull(surface.dropTransferScope)
        assertSame(
            windowManager.diagnostics,
            surface.diagnostics,
            "both branches must report through the one session diagnostic channel",
        )
        assertSame(
            rawInputPort,
            surface.rawInputPort,
            "the primary surface must receive the session-owned raw-input port",
        )
        assertSame(windowManager.rawInputPort, surface.rawInputPort)
        assertSame(windowManager.stampSource, surface.stampSource)
        assertSame(windowManager.sessionFailureHandler, surface.sessionFailureHandler)
        assertEquals(KadrePolicies.Recording.window, surface.deliveryPolicy)

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
                    inputDeliveryPolicy: InputDeliveryPolicy,
                    source: () -> EventStamp,
                    sessionFailureHandler: (KadreFailure) -> Unit,
                    collectorAllocator: Any,
                    maxCollectorsPerFlow: Int,
                    resources: ResourceBudgetPolicy,
                    dropTransferScope: CoroutineScope?,
                    diagnostics: (KadreDiagnostic) -> Unit,
                    rawInputPort: RawInputPort?,
                ) {
                    this@RecordingSurface.installCount += 1
                    this@RecordingSurface.configuration = RecordedConfiguration(
                        deliveryPolicy = deliveryPolicy,
                        inputDeliveryPolicy = inputDeliveryPolicy,
                        stampSource = source,
                        sessionFailureHandler = sessionFailureHandler,
                        collectorAllocator = collectorAllocator,
                        maxCollectorsPerFlow = maxCollectorsPerFlow,
                        resources = resources,
                        dropTransferScope = dropTransferScope,
                        diagnostics = diagnostics,
                        rawInputPort = rawInputPort,
                    )
                }
            }
    }

    /** The components-side counterpart of [RecordingSurface], for the parity assertions. */
    private class RecordingComponentsManager : WindowManager by UnsupportedWindowManager(
        RuntimeProcessIds::nextWindowRequestId,
    ), RuntimeSessionWindowManager {
        var configuration: RecordedComponentsConfiguration? = null
            private set

        override fun installSessionConfiguration(
            deliveryPolicy: WindowDeliveryPolicy,
            inputDeliveryPolicy: InputDeliveryPolicy,
            source: () -> EventStamp,
            sessionFailureHandler: (KadreFailure) -> Unit,
            collectorAllocator: Any,
            maxCollectorsPerFlow: Int,
            dropTransferScope: CoroutineScope?,
            diagnostics: (KadreDiagnostic) -> Unit,
            rawInputPort: RawInputPort?,
        ) {
            configuration = RecordedComponentsConfiguration(
                deliveryPolicy = deliveryPolicy,
                inputDeliveryPolicy = inputDeliveryPolicy,
                stampSource = source,
                sessionFailureHandler = sessionFailureHandler,
                collectorAllocator = collectorAllocator,
                maxCollectorsPerFlow = maxCollectorsPerFlow,
                dropTransferScope = dropTransferScope,
                diagnostics = diagnostics,
                rawInputPort = rawInputPort,
            )
        }
    }

    /**
     * The raw-input port of one session, recorded by identity.
     *
     * The port is never entered: this test only proves that the runtime hands the very instance its
     * components own to the primary surface too.
     */
    private class RecordingRawInputPort : RawInputPort {
        override suspend fun requestAccess(): KadreResult<RawInputPortLease> =
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RawInputAccess))

        override fun close() = Unit
    }

    private data class RecordedConfiguration(
        val deliveryPolicy: WindowDeliveryPolicy,
        val inputDeliveryPolicy: InputDeliveryPolicy,
        val stampSource: () -> EventStamp,
        val sessionFailureHandler: (KadreFailure) -> Unit,
        val collectorAllocator: Any,
        val maxCollectorsPerFlow: Int,
        val resources: ResourceBudgetPolicy,
        val dropTransferScope: CoroutineScope?,
        val diagnostics: (KadreDiagnostic) -> Unit,
        val rawInputPort: RawInputPort?,
    )

    private data class RecordedComponentsConfiguration(
        val deliveryPolicy: WindowDeliveryPolicy,
        val inputDeliveryPolicy: InputDeliveryPolicy,
        val stampSource: () -> EventStamp,
        val sessionFailureHandler: (KadreFailure) -> Unit,
        val collectorAllocator: Any,
        val maxCollectorsPerFlow: Int,
        val dropTransferScope: CoroutineScope?,
        val diagnostics: (KadreDiagnostic) -> Unit,
        val rawInputPort: RawInputPort?,
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
