package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.capture.CaptureManager
import org.graphiks.kadre.capture.CapturePermissionState
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreDiagnostic
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayInventory
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.input.PermissionState
import org.graphiks.kadre.policy.InputDeliveryPolicy
import org.graphiks.kadre.policy.WindowDeliveryPolicy
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.window.WindowManager
import org.graphiks.kadre.window.WindowManagerCapabilities
import org.graphiks.kadre.window.WindowManagerRevision
import org.graphiks.kadre.window.WindowManagerState
import org.graphiks.kadre.window.WindowRequest
import org.graphiks.kadre.window.WindowSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RuntimeSessionComponentsTest {
    @Test
    fun injectedCapturePortIsProjectedThroughItsSessionScopeAndClosedWithTheSession() = runTest {
        val port = RecordingCapturePort(
            snapshot(
                permissions = CapturePermissionState(PermissionState.Granted, PermissionState.Granted),
                sources = CapturePortSources.HostPickerOnly,
            ),
        )
        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.Fake,
            componentsFactory = RuntimeSessionComponentsFactory { _, _ ->
                RuntimeSessionComponents(RecordingWindowManager(), capturePort = port)
            },
        )
        lateinit var observed: CaptureManager

        val session = attach(host) {
            observed = capture
            awaitCancellation()
        }
        testScheduler.runCurrent()

        assertIs<org.graphiks.kadre.capture.CaptureSources.HostPickerOnly>(observed.state.value.sources)

        session.close()
        testScheduler.runCurrent()

        assertEquals(1, port.closeCount)
    }

    @Test
    fun configurationFailureReturnsAnAttachFailureAndClosesCreatedComponents() = runTest {
        var componentsClosed = 0
        var componentScopeJob: Job? = null
        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.Fake,
            componentsFactory = RuntimeSessionComponentsFactory { _, rootScope ->
                componentScopeJob = rootScope.coroutineContext[Job]
                RuntimeSessionComponents(FailingInstallWindowManager()) { componentsClosed += 1 }
            },
        )

        val result = host.attach(this, KadreApplicationFactory { KadreApplication { } })

        assertEquals(
            KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.Fake, "runtime-session-components", "create-failed"),
            ),
            result,
        )
        assertEquals(1, componentsClosed)
        assertFalse(componentScopeJob!!.isActive)
    }

    @Test
    fun injectedDisplayPortIsProjectedThroughItsSessionScopeAndClosedWithTheSession() = runTest {
        val port = RecordingDisplayPort()
        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.Fake,
            componentsFactory = RuntimeSessionComponentsFactory { _, _ ->
                RuntimeSessionComponents(RecordingWindowManager(), displayPort = port)
            },
        )
        lateinit var observed: KadreScope

        val session = attach(host) {
            observed = this
            awaitCancellation()
        }
        testScheduler.runCurrent()

        observed.displays.requestAccess()
        assertIs<DisplayInventory.Enumerated>(observed.displays.state.value.inventory)

        session.close()
        testScheduler.runCurrent()

        assertEquals(1, port.closeCount)
    }

    @Test
    fun primarySurfaceTeardownCannotSkipComponentCleanup() {
        var componentsClosed = 0
        val components = RuntimeSessionComponents(
            windows = RecordingWindowManager(),
            closeAction = { componentsClosed += 1 },
            primarySurface = RuntimePrimarySurface(ThrowingCloseSurface()) {
                error("surface close")
            },
        )

        assertFailsWith<IllegalStateException> { components.close() }

        assertEquals(1, componentsClosed)
    }

    @Test
    fun injectedWindowManagerIsVisibleThroughItsSessionScope() = runTest {
        val manager = RecordingWindowManager()
        var factoryScopeJob: Job? = null
        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.Fake,
            componentsFactory = RuntimeSessionComponentsFactory { _, rootScope ->
                factoryScopeJob = rootScope.coroutineContext[Job]
                RuntimeSessionComponents(manager)
            },
        )
        lateinit var observed: WindowManager

        val session = attach(host) {
            observed = windows
            awaitCancellation()
        }
        testScheduler.runCurrent()

        assertSame(manager, observed)
        assertTrue(factoryScopeJob!!.isActive)
        assertFalse(factoryScopeJob === coroutineContext[Job])

        session.close()
        testScheduler.runCurrent()
    }

    @Test
    fun sessionComponentsAreIsolatedAndCloseOnlyTheirSessionExactlyOnce() = runTest {
        val components = mutableListOf<RecordingComponent>()
        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.Fake,
            componentsFactory = RuntimeSessionComponentsFactory { _, _ ->
                RecordingComponent().also(components::add).asRuntimeComponent()
            },
        )
        lateinit var firstWindows: WindowManager
        lateinit var secondWindows: WindowManager

        val first = attach(host) {
            firstWindows = windows
            awaitCancellation()
        }
        val second = attach(host) {
            secondWindows = windows
            awaitCancellation()
        }
        testScheduler.runCurrent()

        assertEquals(2, components.size)
        assertSame(components[0].manager, firstWindows)
        assertSame(components[1].manager, secondWindows)
        assertFalse(firstWindows === secondWindows)

        first.close()
        first.close()
        testScheduler.runCurrent()

        assertEquals(1, components[0].closeCount)
        assertEquals(0, components[1].closeCount)

        second.close()
        testScheduler.runCurrent()

        assertEquals(1, components[0].closeCount)
        assertEquals(1, components[1].closeCount)
    }

    @Test
    fun rawInputPortIsOwnedByItsSessionComponents() {
        val rawInputPort = RecordingRawInputPort()
        val components = RuntimeSessionComponents(
            windows = RecordingWindowManager(),
            rawInputPort = rawInputPort,
        )

        components.close()
        components.close()

        assertEquals(1, rawInputPort.closeCount)
    }

    @Test
    fun sessionComponentsCloseBeforeHostTerminationIsObserved() = runTest {
        val events = mutableListOf<String>()
        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.Fake,
            componentsFactory = RuntimeSessionComponentsFactory { _, _ ->
                RuntimeSessionComponents(RecordingWindowManager()) { events += "components-closed" }
            },
            sessionObserver = RuntimeSessionObserver { _, _ -> events += "host-observed-termination" },
        )

        attach(host) { requestStop() }
        testScheduler.runCurrent()

        assertEquals(listOf("components-closed", "host-observed-termination"), events)
    }

    @Test
    fun terminationStateAndAwaitBecomeVisibleOnlyAfterComponentsClose() = runTest {
        val events = mutableListOf<String>()
        lateinit var session: KadreSession
        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.Fake,
            componentsFactory = RuntimeSessionComponentsFactory { _, _ ->
                RuntimeSessionComponents(RecordingWindowManager()) {
                    assertEquals(SessionState.Stopping, session.state.value)
                    events += "components-closed"
                }
            },
        )

        session = attach(host) { awaitCancellation() }
        testScheduler.runCurrent()
        val waiter = async {
            session.awaitTermination().also { events += "await-returned" }
        }
        testScheduler.runCurrent()

        session.close()
        testScheduler.runCurrent()

        assertIs<SessionState.Terminated>(session.state.value)
        assertEquals(SessionOutcome.Stopped(org.graphiks.kadre.application.SessionStopReason.HostRequested), waiter.await())
        assertEquals(listOf("components-closed", "await-returned"), events)
    }

    @Test
    fun componentFactoryFailureIsReportedAndDoesNotEscapeAttach() = runTest {
        val failure = IllegalStateException("components")
        val reported = mutableListOf<Throwable>()
        val componentScopeJobs = mutableListOf<Job>()
        var observedTerminations = 0
        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.AppKit,
            componentsFactory = RuntimeSessionComponentsFactory { _, rootScope ->
                componentScopeJobs += checkNotNull(rootScope.coroutineContext[Job])
                throw failure
            },
            failureReporter = RuntimeFailureReporter(reported::add),
            sessionObserver = RuntimeSessionObserver { _, _ -> observedTerminations += 1 },
        )

        val result = host.attach(this, KadreApplicationFactory { KadreApplication { } })

        assertEquals(
            KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.AppKit, "runtime-session-components", "create-failed"),
            ),
            result,
        )
        assertEquals(listOf<Throwable>(failure), reported)
        assertEquals(1, componentScopeJobs.size)
        assertFalse(componentScopeJobs.single().isActive)

        host.detach()
        assertEquals(0, observedTerminations)
    }

    @Test
    fun attachRaceWithHostDetachmentDisposesUnstartedComponentsWithoutNotification() {
        val factoryStarted = java.util.concurrent.CountDownLatch(1)
        val allowFactoryToComplete = java.util.concurrent.CountDownLatch(1)
        val componentScopeJobs = mutableListOf<Job>()
        var closedComponents = 0
        var observedTerminations = 0
        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.Fake,
            componentsFactory = RuntimeSessionComponentsFactory { _, rootScope ->
                componentScopeJobs += checkNotNull(rootScope.coroutineContext[Job])
                factoryStarted.countDown()
                check(allowFactoryToComplete.await(2, TimeUnit.SECONDS))
                RuntimeSessionComponents(RecordingWindowManager()) { closedComponents += 1 }
            },
            sessionObserver = RuntimeSessionObserver { _, _ -> observedTerminations += 1 },
        )
        val parentScope = CoroutineScope(SupervisorJob())
        val executor = Executors.newFixedThreadPool(2)

        try {
            val attach = executor.submit<KadreResult<KadreSession>> {
                host.attach(parentScope, KadreApplicationFactory { KadreApplication { } })
            }
            assertTrue(factoryStarted.await(2, TimeUnit.SECONDS))

            val detach = executor.submit { host.detach() }
            detach.get(2, TimeUnit.SECONDS)
            allowFactoryToComplete.countDown()

            assertEquals(
                KadreResult.Failure(KadreFailure.Closed(org.graphiks.kadre.diagnostics.KadreResourceKind.Host)),
                attach.get(2, TimeUnit.SECONDS),
            )
            assertEquals(1, closedComponents)
            assertFalse(componentScopeJobs.single().isActive)
            assertEquals(0, observedTerminations)
        } finally {
            allowFactoryToComplete.countDown()
            parentScope.coroutineContext[Job]?.cancel()
            executor.shutdownNow()
        }
    }

    private fun kotlinx.coroutines.test.TestScope.attach(
        host: RuntimeHostController,
        application: suspend KadreScope.() -> Unit,
    ): KadreSession = assertIsSuccess(host.attach(this, KadreApplicationFactory { KadreApplication(application) }))

    private fun assertIsSuccess(result: KadreResult<KadreSession>): KadreSession = when (result) {
        is KadreResult.Success -> result.value
        is KadreResult.Failure -> error("expected attached session, got ${result.reason}")
    }

    private class RecordingComponent {
        val manager = RecordingWindowManager()
        var closeCount = 0

        fun asRuntimeComponent(): RuntimeSessionComponents =
            RuntimeSessionComponents(manager) { closeCount += 1 }
    }

    private open class RecordingWindowManager : WindowManager {
        private val mutableState = MutableStateFlow(
            WindowManagerState(
                primary = null,
                windows = emptyList(),
                capabilities = WindowManagerCapabilities(
                    Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RequestWindow)),
                ),
                revision = WindowManagerRevision(0),
            ),
        )

        override val state: StateFlow<WindowManagerState> = mutableState

        override suspend fun requestWindow(spec: WindowSpec): KadreResult<WindowRequest> =
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RequestWindow))
    }

    private class FailingInstallWindowManager : RecordingWindowManager(), RuntimeSessionWindowManager {
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
            error("configuration")
        }
    }

    private class ThrowingCloseSurface : HostSurface by RuntimeHostSurface(
        SurfaceId(0L),
        SurfaceState(
            attachment = org.graphiks.kadre.surface.SurfaceAttachmentState.Attached,
            logicalSize = org.graphiks.kadre.surface.LogicalSize(1.0, 1.0),
            physicalSize = org.graphiks.kadre.surface.PhysicalSize(1, 1),
            scaleFactor = 1.0,
            safeAreaInsets = org.graphiks.kadre.surface.LogicalInsets(0.0, 0.0, 0.0, 0.0),
            visibility = org.graphiks.kadre.surface.SurfaceVisibility.Visible,
            occlusion = org.graphiks.kadre.surface.SurfaceOcclusion.Visible,
            focus = org.graphiks.kadre.surface.SurfaceFocus.Focused,
            appearance = org.graphiks.kadre.surface.SurfaceAppearance(
                org.graphiks.kadre.surface.SurfaceTheme.Unknown,
                org.graphiks.kadre.surface.SurfaceContrast.Unknown,
            ),
            cursor = org.graphiks.kadre.surface.CursorStyle.System(org.graphiks.kadre.surface.CursorIcon.Default),
            pointerCapture = org.graphiks.kadre.surface.PointerCaptureMode.None,
            hitTesting = org.graphiks.kadre.surface.HitTestingMode.Enabled,
            inputDefaultBehavior = org.graphiks.kadre.surface.InputDefaultBehavior.HostDefault,
            revision = org.graphiks.kadre.surface.SurfaceRevision(0L),
        ),
    )

    private class RecordingRawInputPort : RawInputPort {
        var closeCount = 0
            private set

        override suspend fun requestAccess(): KadreResult<RawInputPortLease> =
            error("this ownership test never requests raw input")

        override fun close() {
            closeCount += 1
        }
    }

    private class RecordingDisplayPort : DisplayPort {
        var closeCount = 0
            private set

        override val enumerationCapability: Capability<Unit> = Capability.Supported(
            Unit,
            org.graphiks.kadre.diagnostics.FeatureAvailability.Available,
        )

        override suspend fun requestSnapshot(): KadreResult<DisplayPortSnapshot> = KadreResult.Success(
            DisplayPortSnapshot(
                primaryKey = 1,
                displays = listOf(
                    DisplayPortDisplay(
                        key = 1,
                        type = DisplayType.Physical,
                        name = "test",
                        bounds = PhysicalRect(PhysicalPoint(0, 0), PhysicalSize(800, 600)),
                        workArea = null,
                        scaleFactor = 1.0,
                        currentModeKey = 1,
                        modes = listOf(
                            DisplayPortMode(1, PhysicalSize(800, 600), 60.0, 24),
                        ),
                    ),
                ),
            ),
        )

        override fun installSnapshotObserver(observer: (KadreResult<DisplayPortSnapshot>) -> Unit): AutoCloseable =
            AutoCloseable { }

        override fun close() {
            closeCount += 1
        }
    }
}
