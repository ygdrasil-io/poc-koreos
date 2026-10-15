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
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.display.DisplayInventory
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.input.DeviceInventory
import org.graphiks.kadre.input.GamepadButton
import org.graphiks.kadre.input.GamepadButtonValue
import org.graphiks.kadre.input.GamepadCapabilities
import org.graphiks.kadre.input.GamepadDescriptor
import org.graphiks.kadre.input.GamepadEffectConstraints
import org.graphiks.kadre.input.GamepadEffectKind
import org.graphiks.kadre.input.GamepadHapticLocality
import org.graphiks.kadre.input.GamepadLocalizedHapticConstraints
import org.graphiks.kadre.input.GamepadMapping
import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.input.GamepadState
import org.graphiks.kadre.input.InputDeviceDescriptor
import org.graphiks.kadre.input.InputDeviceKind
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
    fun throwingDisplayObserverClosesEveryCreatedComponentExactlyOnce() = runTest {
        val display = RecordingDisplayPort(failObserver = true)
        val pads = RecordingGamepadPort()
        val devices = RecordingInputDevicePort()
        val raw = RecordingRawInputPort()
        var windowRuntimeCloses = 0
        var root: Job? = null
        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.Fake,
            componentsFactory = RuntimeSessionComponentsFactory { _, scope ->
                root = scope.coroutineContext[Job]
                RuntimeSessionComponents(
                    RecordingWindowManager(),
                    rawInputPort = raw,
                    displayPort = display,
                    gamepadPort = pads,
                    inputDevicePort = devices,
                ) { windowRuntimeCloses++ }
            },
        )
        assertIs<KadreResult.Failure>(host.attach(this, KadreApplicationFactory { KadreApplication { } }))
        assertEquals(1, display.closeCount)
        assertEquals(1, pads.closeCount)
        assertEquals(1, devices.closeCount)
        assertEquals(1, raw.closeCount)
        assertEquals(1, windowRuntimeCloses)
        assertFalse(checkNotNull(root).isActive)
    }

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
        val display = RecordingDisplayPort()
        var componentScopeJob: Job? = null
        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.Fake,
            componentsFactory = RuntimeSessionComponentsFactory { _, rootScope ->
                componentScopeJob = rootScope.coroutineContext[Job]
                RuntimeSessionComponents(FailingInstallWindowManager(), displayPort = display) { componentsClosed += 1 }
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
        assertEquals(1, display.closeCount)
        assertEquals(1, display.observationCloseCount, "successfully constructed manager must close on later failure")
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
    fun injectedGamepadPortIsProjectedThroughItsSessionScopeAndClosedWithTheSession() = runTest {
        val port = RecordingGamepadPort()
        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.Fake,
            componentsFactory = RuntimeSessionComponentsFactory { _, _ ->
                RuntimeSessionComponents(RecordingWindowManager(), gamepadPort = port)
            },
        )
        lateinit var observed: KadreScope

        val session = attach(host) {
            observed = this
            awaitCancellation()
        }
        testScheduler.runCurrent()

        assertIs<DeviceInventory.Enumerated>(observed.devices.state.value.inventory)

        session.close()
        testScheduler.runCurrent()

        assertEquals(1, port.closeCount)
    }

    @Test
    fun inputOnlyPortCreatesAndClosesTheSessionDeviceManager() = runTest {
        val port = RecordingInputDevicePort()
        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.Fake,
            componentsFactory = RuntimeSessionComponentsFactory { _, _ ->
                RuntimeSessionComponents(RecordingWindowManager(), inputDevicePort = port)
            },
        )
        lateinit var observed: KadreScope

        val session = attach(host) {
            observed = this
            awaitCancellation()
        }
        testScheduler.runCurrent()

        assertIs<DeviceInventory.Enumerated>(observed.devices.state.value.inventory)

        session.close()
        testScheduler.runCurrent()

        assertEquals(1, port.closeCount)
    }

    /**
     * Le pin de la revue Task 2 (Important-2) — le SEUL des deux overloads différés sanctionnés
     * pour l'uikit phase 8 qui porte le display port seul : la délégation POSITIONNELLE interne
     * de l'overload `(windows, primarySurfaceProvider, displayPort, closeAction)` place le port
     * dans LE SLOT display (jamais capture — le glissement compile vert autrement) et [close]
     * le referme exactement une fois, APRÈS le closeAction. La projection par le manager display
     * de la session est ce qui distingue un bon slot d'un slot où le port serait seulement fermé.
     */
    @Test
    fun deferredProviderDisplayPortOverloadProjectsThroughTheDisplaySlotAndClosesThePortOnce() = runTest {
        val port = RecordingDisplayPort()
        var providerRuns = 0
        var closeActionRuns = 0
        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.Fake,
            componentsFactory = RuntimeSessionComponentsFactory { _, _ ->
                RuntimeSessionComponents(
                    windows = RecordingWindowManager(),
                    primarySurfaceProvider = { providerRuns += 1; null },
                    displayPort = port,
                    closeAction = { closeActionRuns += 1 },
                )
            },
        )
        lateinit var observed: KadreScope

        val session = attach(host) {
            observed = this
            awaitCancellation()
        }
        testScheduler.runCurrent()

        assertEquals(1, providerRuns, "the deferred provider resolves exactly once, at session construction")
        observed.displays.requestAccess()
        assertIs<DisplayInventory.Enumerated>(observed.displays.state.value.inventory)

        session.close()
        testScheduler.runCurrent()

        assertEquals(1, closeActionRuns)
        assertEquals(1, port.closeCount)
    }

    /**
     * Le pin de la revue Task 2 (Important-2), l'overload complet de l'attach uikit
     * `(windows, primarySurfaceProvider, displayPort, gamepadPort, inputDevicePort, closeAction)`
     * : les TROIS ports sont projetés DANS LEUR SLOT (le display comme DisplayManager, les deux
     * device comme DeviceManager — un glissement d'argument qui compilerait vert les mettrait
     * au mauvais manager ou les fermerait zéro/deux fois) et chacun est refermé EXACTEMENT une
     * fois par la terminaison de session, après le closeAction.
     */
    @Test
    fun deferredProviderThreePortOverloadProjectsEachPortInItsSlotAndClosesEachOnce() = runTest {
        val displayPort = RecordingDisplayPort()
        val gamepadPort = RecordingGamepadPort()
        val inputDevicePort = RecordingInputDevicePort()
        var providerRuns = 0
        var closeActionRuns = 0
        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.Fake,
            componentsFactory = RuntimeSessionComponentsFactory { _, _ ->
                RuntimeSessionComponents(
                    windows = RecordingWindowManager(),
                    primarySurfaceProvider = { providerRuns += 1; null },
                    displayPort = displayPort,
                    gamepadPort = gamepadPort,
                    inputDevicePort = inputDevicePort,
                    closeAction = { closeActionRuns += 1 },
                )
            },
        )
        lateinit var observed: KadreScope

        val session = attach(host) {
            observed = this
            awaitCancellation()
        }
        testScheduler.runCurrent()

        assertEquals(1, providerRuns, "the deferred provider resolves exactly once, at session construction")
        observed.displays.requestAccess()
        val displays = assertIs<DisplayInventory.Enumerated>(observed.displays.state.value.inventory)
        assertEquals(1, displays.displays.size, "the display port is projected as the DisplayManager")

        val devices = assertIs<DeviceInventory.Enumerated>(observed.devices.state.value.inventory)
        assertEquals(1, devices.gamepads.size, "the gamepad port is projected into the DeviceManager gamepads")
        assertEquals(1, devices.devices.size, "the input-device port is projected into the DeviceManager devices")

        session.close()
        testScheduler.runCurrent()

        assertEquals(1, closeActionRuns)
        assertEquals(1, displayPort.closeCount)
        assertEquals(1, gamepadPort.closeCount)
        assertEquals(1, inputDevicePort.closeCount)
    }

    /**
     * Le pin de la phase 9 (Task 2, uikit) — l'overload différé QUATRE ports
     * `(windows, primarySurfaceProvider, displayPort, gamepadPort, inputDevicePort, capturePort,
     * closeAction)` : le port capture est projeté DANS SON SLOT (comme CaptureManager de la
     * session — le snapshot d'inventaire indisponible du port est l'état public, donc un
     * glissement d'argument qui compilerait vert projeterait ici le mauvais port ou rien), et
     * chacun des QUATRE ports est refermé EXACTEMENT une fois par la terminaison de session,
     * après le closeAction. Les TROIS autres slots (display, gamepad, device) sont réépinglés
     * dans le même test : la délégation positionnelle du nouvel overload ne doit pas les
     * déranger.
     */
    @Test
    fun deferredProviderCapturePortOverloadProjectsTheCapturePortInItsSlotAndClosesEachPortOnce() = runTest {
        val displayPort = RecordingDisplayPort()
        val gamepadPort = RecordingGamepadPort()
        val inputDevicePort = RecordingInputDevicePort()
        val capturePort = RecordingCapturePort(
            snapshot(
                permissions = CapturePermissionState(PermissionState.Granted, PermissionState.Granted),
                sources = CapturePortSources.Unavailable(
                    KadreFailure.Unsupported(KadreOperation.CaptureRefreshSources),
                ),
            ),
        )
        var providerRuns = 0
        var closeActionRuns = 0
        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.Fake,
            componentsFactory = RuntimeSessionComponentsFactory { _, _ ->
                RuntimeSessionComponents(
                    windows = RecordingWindowManager(),
                    primarySurfaceProvider = { providerRuns += 1; null },
                    displayPort = displayPort,
                    gamepadPort = gamepadPort,
                    inputDevicePort = inputDevicePort,
                    capturePort = capturePort,
                    closeAction = { closeActionRuns += 1 },
                )
            },
        )
        lateinit var observed: KadreScope

        val session = attach(host) {
            observed = this
            awaitCancellation()
        }
        testScheduler.runCurrent()

        assertEquals(1, providerRuns, "the deferred provider resolves exactly once, at session construction")

        val sources = assertIs<org.graphiks.kadre.capture.CaptureSources.Unavailable>(
            observed.capture.state.value.sources,
        )
        assertEquals(
            KadreFailure.Unsupported(KadreOperation.CaptureRefreshSources),
            sources.failure,
            "the capture port is projected as the session CaptureManager",
        )

        session.close()
        testScheduler.runCurrent()

        assertEquals(1, closeActionRuns)
        assertEquals(1, capturePort.closeCount)
        assertEquals(1, displayPort.closeCount)
        assertEquals(1, gamepadPort.closeCount)
        assertEquals(1, inputDevicePort.closeCount)
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

    private class RecordingDisplayPort(private val failObserver: Boolean = false) : DisplayPort {
        var observationCloseCount = 0
            private set
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

        override fun installSnapshotObserver(observer: (KadreResult<DisplayPortSnapshot>) -> Unit): AutoCloseable {
            check(!failObserver) { "display observer installation failed" }
            return AutoCloseable { observationCloseCount++ }
        }

        override fun close() {
            closeCount += 1
        }
    }

    /**
     * Le port manette enregistré avec UNE manette connectée (la forme uikit Task 4 : profil
     * étendu, effets LocalizedHaptic disponibles) — la projection du slot gamepadPort doit la
     * retrouver dans l'inventaire publie de la session.
     */
    private class RecordingGamepadPort : GamepadPort {
        var closeCount = 0
            private set

        override val gamepads: List<GamepadPortGamepad> = listOf(
            GamepadPortGamepad(
                key = 11L,
                descriptor = GamepadDescriptor(
                    name = "Controller 11",
                    mapping = GamepadMapping.Standard,
                    buttons = listOf(GamepadButton.South),
                    axes = emptyList(),
                ),
                state = GamepadState(
                    buttons = listOf(GamepadButtonValue(GamepadButton.South, 0.0, false)),
                    axes = emptyList(),
                ),
                routing = GamepadRoutingState.Routed,
                capabilities = GamepadCapabilities(
                    effects = Capability.Supported(
                        GamepadEffectConstraints(
                            kinds = setOf(GamepadEffectKind.LocalizedHaptic),
                            localizedHaptics = GamepadLocalizedHapticConstraints(
                                setOf(GamepadHapticLocality.Default),
                            ),
                            maximumDuration = null,
                        ),
                        FeatureAvailability.Available,
                    ),
                ),
            ),
        )

        override fun installObserver(observer: (GamepadPortEvent) -> Unit): AutoCloseable = AutoCloseable { }

        override fun updateRouting(routing: GamepadPortRouting) = Unit

        override fun startEffect(
            key: Long,
            effect: org.graphiks.kadre.input.GamepadEffect,
        ): KadreResult<GamepadPortEffect> = KadreResult.Failure(
            KadreFailure.Unsupported(KadreOperation.GamepadEffect),
        )

        override fun close() {
            closeCount += 1
        }
    }

    /** Le port device générique enregistré avec UN clavier (le fait seul, hors livraison). */
    private class RecordingInputDevicePort : InputDevicePort {
        var closeCount = 0
            private set

        override val devices: List<InputDevicePortDevice> = listOf(
            InputDevicePortDevice(21L, InputDeviceDescriptor("Keyboard", InputDeviceKind.Keyboard)),
        )

        override fun installObserver(observer: (InputDevicePortEvent) -> Unit): AutoCloseable = AutoCloseable { }

        override fun close() {
            closeCount += 1
        }
    }
}
