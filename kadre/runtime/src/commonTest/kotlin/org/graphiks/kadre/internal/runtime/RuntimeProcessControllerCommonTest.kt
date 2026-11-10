package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreLaunchContext
import org.graphiks.kadre.application.KadreLaunchReason
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.MemoryPressureLevel
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreDiagnostic
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.InputDeliveryPolicy
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
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.window.FullscreenMode
import org.graphiks.kadre.window.Window
import org.graphiks.kadre.window.WindowAttention
import org.graphiks.kadre.window.WindowCapabilities
import org.graphiks.kadre.window.WindowCloseDecision
import org.graphiks.kadre.window.WindowCloseOutcome
import org.graphiks.kadre.window.WindowCloseRequestId
import org.graphiks.kadre.window.WindowCloseResponseOutcome
import org.graphiks.kadre.window.WindowDecorations
import org.graphiks.kadre.window.WindowEvent
import org.graphiks.kadre.window.WindowId
import org.graphiks.kadre.window.WindowLevel
import org.graphiks.kadre.window.WindowManager
import org.graphiks.kadre.window.WindowManagerCapabilities
import org.graphiks.kadre.window.WindowManagerRevision
import org.graphiks.kadre.window.WindowManagerState
import org.graphiks.kadre.window.WindowPhase
import org.graphiks.kadre.window.WindowRevision
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kadre.window.WindowState
import org.graphiks.kadre.window.WindowSystemButtons
import org.graphiks.kadre.window.WindowUpdate
import org.graphiks.kadre.window.WindowUpdateOutcome
import kotlin.coroutines.resume
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class RuntimeProcessControllerCommonTest {
    @Test
    fun bootAdmitsAndReachesRunning() = runProcessTest {
        lateinit var suppliedSurface: RuntimeHostSurface
        val controller = RuntimeProcessController.withPrimarySurface(KadrePlatform.Web) { id ->
            val surface = RuntimeHostSurface(id, initialSurfaceState()).also { suppliedSurface = it }
            RuntimePrimarySurface(surface, surface::close)
        }
        val scopeReady = CompletableDeferred<KadreScope>()
        val launchContext = CompletableDeferred<KadreLaunchContext>()
        val factory = KadreApplicationFactory { context ->
            launchContext.complete(context)
            KadreApplication {
                scopeReady.complete(this)
                awaitCancellation()
            }
        }

        val session = assertIs<KadreResult.Success<KadreSession>>(
            controller.boot(RuntimeBootRequest(platform = KadrePlatform.Web, applicationFactory = factory)),
        ).value
        testScheduler.advanceUntilIdle()

        assertEquals(SessionState.Running, session.state.value)
        val scope = scopeReady.await()
        assertSame(suppliedSurface, scope.primarySurface.value)
        val context = launchContext.await()
        assertEquals(KadreLaunchReason.ProcessLaunch, context.reason)
        assertEquals(session.id, context.sessionId)

        session.requestStop()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun secondConcurrentBootFailsWithAlreadyInUse() = runProcessTest {
        val controller = RuntimeProcessController.withPrimarySurface(KadrePlatform.Web) { id ->
            val surface = RuntimeHostSurface(id, initialSurfaceState())
            RuntimePrimarySurface(surface, surface::close)
        }
        val holdingFactory = KadreApplicationFactory { KadreApplication { awaitCancellation() } }
        val first = assertIs<KadreResult.Success<KadreSession>>(
            controller.boot(RuntimeBootRequest(platform = KadrePlatform.Web, applicationFactory = holdingFactory)),
        ).value
        testScheduler.advanceUntilIdle()
        assertEquals(SessionState.Running, first.state.value)

        var secondFactoryInvocations = 0
        val second = controller.boot(
            RuntimeBootRequest(
                platform = KadrePlatform.Web,
                applicationFactory = KadreApplicationFactory {
                    secondFactoryInvocations += 1
                    KadreApplication { awaitCancellation() }
                },
            ),
        )

        assertEquals(KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host)), second)
        assertEquals(0, secondFactoryInvocations)
        assertEquals(SessionState.Running, first.state.value)

        first.requestStop()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun bootRejectsInvalidFirstWindowWithoutStarting() = runProcessTest {
        var surfaceFactoryInvocations = 0
        val controller = RuntimeProcessController.withPrimarySurface(KadrePlatform.Web) { id ->
            surfaceFactoryInvocations += 1
            val surface = RuntimeHostSurface(id, initialSurfaceState())
            RuntimePrimarySurface(surface, surface::close)
        }
        var applicationFactoryInvocations = 0
        val factory = KadreApplicationFactory {
            applicationFactoryInvocations += 1
            KadreApplication { awaitCancellation() }
        }

        // A withPrimarySurface controller's sessions receive one host-provided primary surface
        // WITHOUT a runtime-managed window, so a firstWindow spec can never be applied: the boot
        // request is invalid before anything is created. (A WindowSpec's own constructor already
        // guarantees its size coherence — the remaining boot-time invalidity is this shape.)
        val rejected = controller.boot(
            RuntimeBootRequest(
                platform = KadrePlatform.Web,
                applicationFactory = factory,
                firstWindow = WindowSpec(),
            ),
        )

        assertEquals(KadreResult.Failure(KadreFailure.InvalidRequest("firstWindow")), rejected)
        assertEquals(0, surfaceFactoryInvocations)
        assertEquals(0, applicationFactoryInvocations)
        assertNull(controller.ownedRootScope)

        // The rejection reserved nothing: the very next boot on the same controller is admitted.
        val admitted = assertIs<KadreResult.Success<KadreSession>>(
            controller.boot(RuntimeBootRequest(platform = KadrePlatform.Web, applicationFactory = factory)),
        ).value
        testScheduler.advanceUntilIdle()
        assertEquals(SessionState.Running, admitted.state.value)

        admitted.requestStop()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun sessionCompletesWhenLastWindowCloses() = runProcessTest {
        val windows = LastContainerRuleWindows()
        val controller = RuntimeProcessController.withComponents(
            platform = KadrePlatform.Web,
            componentsFactory = { _, _ -> RuntimeSessionComponents(windows = windows) },
        )
        val session = assertIs<KadreResult.Success<KadreSession>>(
            controller.boot(
                RuntimeBootRequest(
                    platform = KadrePlatform.Web,
                    applicationFactory = KadreApplicationFactory { KadreApplication { awaitCancellation() } },
                ),
            ),
        ).value
        testScheduler.advanceUntilIdle()
        assertEquals(SessionState.Running, session.state.value)

        windows.presentOneWindow()
        testScheduler.advanceUntilIdle()
        assertEquals(SessionState.Running, session.state.value)

        windows.presentNoWindows()
        testScheduler.advanceUntilIdle()

        assertEquals(SessionState.Terminated(SessionOutcome.Completed), session.state.value)
    }

    /**
     * The last-container rule must never terminate a session other than the watcher's own.
     *
     * The scripted manager is the deterministic clock for the production race: a watcher
     * resumption dispatched while its session was alive can run its fire path after that
     * session terminated (the fire path never suspends, so cancelling the watcher's job cannot
     * stop it — the uncontended fast path), at a moment when a later boot is already live. Here
     * [ScriptedLastContainerWindows.deliver] dispatches that resumption on the test's clock:
     * session A's dying watcher runs with session B live, and only B's own state may end B.
     */
    @Test
    fun lastContainerRuleNeverTerminatesALaterSession() = runProcessTest {
        val aWindows = ScriptedLastContainerWindows()
        val bWindows = LastContainerRuleWindows()
        var servedFirstSession = false
        val controller = RuntimeProcessController.withComponents(
            platform = KadrePlatform.Web,
            componentsFactory = { _, _ ->
                if (!servedFirstSession) {
                    servedFirstSession = true
                    RuntimeSessionComponents(windows = aWindows)
                } else {
                    RuntimeSessionComponents(windows = bWindows)
                }
            },
        )
        val sessionA = assertIs<KadreResult.Success<KadreSession>>(
            controller.boot(
                RuntimeBootRequest(
                    platform = KadrePlatform.Web,
                    applicationFactory = KadreApplicationFactory { KadreApplication { awaitCancellation() } },
                ),
            ),
        ).value
        testScheduler.advanceUntilIdle()
        assertEquals(SessionState.Running, sessionA.state.value)

        aWindows.presentOneWindow()
        aWindows.deliver()
        testScheduler.runCurrent()
        assertEquals(SessionState.Running, sessionA.state.value)

        // A's dying publication is recorded but not yet dispatched to the watcher.
        aWindows.presentNoWindows()
        controller.terminate(SessionStopReason.SystemTermination)
        testScheduler.advanceUntilIdle()
        assertEquals(
            SessionState.Terminated(SessionOutcome.Stopped(SessionStopReason.SystemTermination)),
            sessionA.state.value,
        )

        val sessionB = assertIs<KadreResult.Success<KadreSession>>(
            controller.boot(
                RuntimeBootRequest(
                    platform = KadrePlatform.Web,
                    applicationFactory = KadreApplicationFactory { KadreApplication { awaitCancellation() } },
                ),
            ),
        ).value
        testScheduler.advanceUntilIdle()
        assertEquals(SessionState.Running, sessionB.state.value)

        // A's watcher runs its fire path now, with B live: it must not terminate B.
        aWindows.deliver()
        testScheduler.advanceUntilIdle()

        assertEquals(SessionState.Running, sessionB.state.value)

        sessionB.requestStop()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun platformFailureFailsTheLiveSession() = runProcessTest {
        val controller = RuntimeProcessController.withPrimarySurface(KadrePlatform.Web) { id ->
            val surface = RuntimeHostSurface(id, initialSurfaceState())
            RuntimePrimarySurface(surface, surface::close)
        }
        val session = assertIs<KadreResult.Success<KadreSession>>(
            controller.boot(
                RuntimeBootRequest(
                    platform = KadrePlatform.Web,
                    applicationFactory = KadreApplicationFactory { KadreApplication { awaitCancellation() } },
                ),
            ),
        ).value
        testScheduler.advanceUntilIdle()
        assertEquals(SessionState.Running, session.state.value)

        val failure = KadreFailure.PlatformFailure(KadrePlatform.Web, "pump", "get-message-failed")
        controller.fail(failure)
        testScheduler.advanceUntilIdle()

        assertEquals(SessionState.Terminated(SessionOutcome.Failed(failure)), session.state.value)

        // Idempotent without a live session: a second fail (or a memory-pressure level) is a no-op.
        controller.fail(failure)
        controller.emitMemoryPressure(MemoryPressureLevel.Critical)
    }

    @Test
    fun systemTerminationProducesStoppedSystemTermination() = runProcessTest {
        val controller = RuntimeProcessController.withPrimarySurface(KadrePlatform.Web) { id ->
            val surface = RuntimeHostSurface(id, initialSurfaceState())
            RuntimePrimarySurface(surface, surface::close)
        }
        val session = assertIs<KadreResult.Success<KadreSession>>(
            controller.boot(
                RuntimeBootRequest(
                    platform = KadrePlatform.Web,
                    applicationFactory = KadreApplicationFactory { KadreApplication { awaitCancellation() } },
                ),
            ),
        ).value
        testScheduler.advanceUntilIdle()
        assertEquals(SessionState.Running, session.state.value)
        val ownedScope = assertNotNull(controller.ownedRootScope)

        controller.terminate(SessionStopReason.SystemTermination)
        testScheduler.advanceUntilIdle()

        assertEquals(
            SessionState.Terminated(SessionOutcome.Stopped(SessionStopReason.SystemTermination)),
            session.state.value,
        )
        val ownedJob = assertNotNull(ownedScope.coroutineContext[Job])
        assertFalse(ownedJob.isActive)
    }

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

/**
 * A scripted [WindowManager] `state` for the cross-session interleaving: subscriptions park
 * non-cancellably (`suspendCoroutine`, not tied to the watcher's job), a publication is only
 * recorded, and [deliver] dispatches it to the parked watcher on the test's clock. This is the
 * deterministic stand-in for the production race — a resumption dispatched while the session
 * was alive whose fire path runs after that session terminated and a later boot is live: the
 * fire path never suspends, so cancelling the watcher's job cannot stop it.
 */
private class ScriptedLastContainerWindows :
    WindowManager by UnsupportedWindowManager(RuntimeProcessIds::nextWindowRequestId),
    RuntimeSessionWindowManager,
    StateFlow<WindowManagerState> {
    private val window = OneSessionWindow()
    private var current: WindowManagerState = noWindows()
    private var parked: kotlin.coroutines.Continuation<WindowManagerState>? = null

    override val state: StateFlow<WindowManagerState> get() = this
    override val value: WindowManagerState get() = current
    override val replayCache: List<WindowManagerState> get() = listOf(current)

    fun presentOneWindow() {
        current = oneWindow(current.revision.value + 1L)
    }

    fun presentNoWindows() {
        current = noWindows(current.revision.value + 1L)
    }

    /** Dispatches the recorded publication to the parked watcher — the in-flight resumption. */
    fun deliver() {
        val continuation = parked ?: return
        parked = null
        continuation.resume(current)
    }

    override suspend fun collect(collector: FlowCollector<WindowManagerState>): Nothing {
        collector.emit(current)
        while (true) {
            val next = kotlin.coroutines.suspendCoroutine { continuation -> parked = continuation }
            collector.emit(next)
        }
    }

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
        // The rule under test observes `state` only; the configuration seam is not exercised.
    }

    private fun oneWindow(revision: Long): WindowManagerState = WindowManagerState(
        primary = window,
        windows = listOf(window),
        capabilities = unsupportedWindowManagerCapabilities(),
        revision = WindowManagerRevision(revision),
    )

    private fun noWindows(revision: Long = 0L): WindowManagerState = WindowManagerState(
        primary = null,
        windows = emptyList(),
        capabilities = unsupportedWindowManagerCapabilities(),
        revision = WindowManagerRevision(revision),
    )
}

/** The window-manager seam the last-container rule is tested through (ledger ruling 2). */
private class LastContainerRuleWindows :
    WindowManager by UnsupportedWindowManager(RuntimeProcessIds::nextWindowRequestId),
    RuntimeSessionWindowManager {
    private val window = OneSessionWindow()
    private val mutableState = MutableStateFlow(noWindows())

    override val state: StateFlow<WindowManagerState> = mutableState.asStateFlow()

    fun presentOneWindow() {
        mutableState.value = WindowManagerState(
            primary = window,
            windows = listOf(window),
            capabilities = unsupportedWindowManagerCapabilities(),
            revision = WindowManagerRevision(mutableState.value.revision.value + 1L),
        )
    }

    fun presentNoWindows() {
        mutableState.value = noWindows(mutableState.value.revision.value + 1L)
    }

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
        // The rule under test observes `state` only; the configuration seam is not exercised.
    }

    private fun noWindows(revision: Long = 0L): WindowManagerState = WindowManagerState(
        primary = null,
        windows = emptyList(),
        capabilities = unsupportedWindowManagerCapabilities(),
        revision = WindowManagerRevision(revision),
    )
}

private class OneSessionWindow : Window {
    override val id: WindowId = WindowId(1L)
    override val surface: HostSurface = RuntimeHostSurface(SurfaceId(1L), initialSurfaceState())
    override val state: StateFlow<WindowState> = MutableStateFlow(initialWindowState()).asStateFlow()
    override val capabilities: StateFlow<WindowCapabilities> =
        MutableStateFlow(initialWindowCapabilities()).asStateFlow()
    override val events: Flow<WindowEvent> = emptyFlow()

    override suspend fun apply(update: WindowUpdate): KadreResult<WindowUpdateOutcome> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.UpdateWindow))

    override suspend fun requestAttention(attention: WindowAttention): KadreResult<Unit> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RequestWindowAttention))

    override suspend fun close(): KadreResult<WindowCloseOutcome> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CloseWindow))

    override suspend fun respondToCloseRequest(
        requestId: WindowCloseRequestId,
        decision: WindowCloseDecision,
    ): KadreResult<WindowCloseResponseOutcome> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RespondToCloseRequest))
}

private fun unsupportedWindowManagerCapabilities(): WindowManagerCapabilities =
    WindowManagerCapabilities(
        requestWindow = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RequestWindow)),
    )

private fun initialWindowState(): WindowState = WindowState(
    phase = WindowPhase.Open,
    title = "",
    outerBounds = null,
    contentSize = LogicalSize(1.0, 1.0),
    minimumSize = null,
    maximumSize = null,
    resizable = true,
    fullscreen = FullscreenMode.Windowed,
    decorations = WindowDecorations.System,
    systemButtons = WindowSystemButtons.All,
    level = WindowLevel.Normal,
    transparent = false,
    blurBehind = false,
    icon = null,
    contentProtection = false,
    revision = WindowRevision(0L),
)

private fun initialWindowCapabilities(): WindowCapabilities = WindowCapabilities(
    title = unsupportedWindowCapability(),
    outerPosition = unsupportedWindowCapability(),
    contentSize = unsupportedWindowCapability(),
    minimumSize = unsupportedWindowCapability(),
    maximumSize = unsupportedWindowCapability(),
    resizable = unsupportedWindowCapability(),
    fullscreen = unsupportedWindowCapability(),
    decorations = unsupportedWindowCapability(),
    systemButtons = unsupportedWindowCapability(),
    level = unsupportedWindowCapability(),
    transparency = unsupportedWindowCapability(),
    blurBehind = unsupportedWindowCapability(),
    icon = unsupportedWindowCapability(),
    attention = unsupportedWindowCapability(),
    contentProtection = unsupportedWindowCapability(),
    closeInterception = unsupportedWindowCapability(),
    platformAccess = unsupportedWindowCapability(),
)

private fun <T> unsupportedWindowCapability(): Capability<T> =
    Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateWindow))

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
    revision = SurfaceRevision(0L),
)
