package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionId
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.diagnostics.InteractionFailureReason
import org.graphiks.kadre.diagnostics.KadreFailure as FoundationFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadrePolicyComponent
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.input.KadrePermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The value model of `@kadre/host` is a closed mapping: every Kotlin value of
 * `kadre/INTEROP-EXPORTS.md` section 6 has exactly one published name and one set of fields.
 *
 * These assertions live in the shared web test source set, so the JS and the Wasm compilation prove
 * the same mapping and the same handle contract.
 */
class WebHostInteropTest {
    @Test
    fun everyOperationMemberKeepsItsPublishedName() {
        assertPublishedNames(
            entries = KadreOperation.entries,
            expected = listOf(
                KadreOperation.HostAttach to "hostAttach",
                KadreOperation.RequestRedraw to "requestRedraw",
                KadreOperation.DisplayAccess to "displayAccess",
                KadreOperation.RequestWindow to "requestWindow",
                KadreOperation.UpdateWindow to "updateWindow",
                KadreOperation.RequestWindowAttention to "requestWindowAttention",
                KadreOperation.CloseWindow to "closeWindow",
                KadreOperation.RespondToCloseRequest to "respondToCloseRequest",
                KadreOperation.UpdateSurface to "updateSurface",
                KadreOperation.InstallInteractionHandler to "installInteractionHandler",
                KadreOperation.ArmInteraction to "armInteraction",
                KadreOperation.Interaction to "interaction",
                KadreOperation.GamepadEffect to "gamepadEffect",
                KadreOperation.StopGamepadEffects to "stopGamepadEffects",
                KadreOperation.TextInput to "textInput",
                KadreOperation.UpdateTextInput to "updateTextInput",
                KadreOperation.ClaimDropTransfer to "claimDropTransfer",
                KadreOperation.ReadDropItem to "readDropItem",
                KadreOperation.CapturePermission to "capturePermission",
                KadreOperation.CaptureRefreshSources to "captureRefreshSources",
                KadreOperation.CaptureOpen to "captureOpen",
                KadreOperation.CaptureCollectFrames to "captureCollectFrames",
                KadreOperation.RawInputAccess to "rawInputAccess",
                KadreOperation.GestureInput to "gestureInput",
                KadreOperation.PlatformSurfaceAccess to "platformSurfaceAccess",
                KadreOperation.PlatformWindowAccess to "platformWindowAccess",
            ),
            publishedName = KadreOperation::interopName,
        )
    }

    @Test
    fun everyPermissionMemberKeepsItsPublishedName() {
        assertPublishedNames(
            entries = KadrePermission.entries,
            expected = listOf(
                KadrePermission.DisplayEnumeration to "displayEnumeration",
                KadrePermission.InputMonitoring to "inputMonitoring",
                KadrePermission.RawInput to "rawInput",
                KadrePermission.CaptureScreen to "captureScreen",
                KadrePermission.CaptureWindow to "captureWindow",
            ),
            publishedName = KadrePermission::interopName,
        )
    }

    @Test
    fun everyPolicyComponentMemberKeepsItsPublishedName() {
        assertPublishedNames(
            entries = KadrePolicyComponent.entries,
            expected = listOf(
                KadrePolicyComponent.Execution to "execution",
                KadrePolicyComponent.LifecycleEvents to "lifecycleEvents",
                KadrePolicyComponent.HostSignals to "hostSignals",
                KadrePolicyComponent.WindowEvents to "windowEvents",
                KadrePolicyComponent.DeviceEvents to "deviceEvents",
                KadrePolicyComponent.InputEvents to "inputEvents",
                KadrePolicyComponent.DevicePolicy to "devicePolicy",
                KadrePolicyComponent.CaptureEvents to "captureEvents",
                KadrePolicyComponent.CaptureFrames to "captureFrames",
                KadrePolicyComponent.Diagnostics to "diagnostics",
                KadrePolicyComponent.Resources to "resources",
            ),
            publishedName = KadrePolicyComponent::interopName,
        )
    }

    @Test
    fun everyResourceKindMemberKeepsItsPublishedName() {
        assertPublishedNames(
            entries = KadreResourceKind.entries,
            expected = listOf(
                KadreResourceKind.Host to "host",
                KadreResourceKind.Surface to "surface",
                KadreResourceKind.Window to "window",
                KadreResourceKind.WindowRequest to "windowRequest",
                KadreResourceKind.Display to "display",
                KadreResourceKind.InputSource to "inputSource",
                KadreResourceKind.RawInputAccess to "rawInputAccess",
                KadreResourceKind.InputDevice to "inputDevice",
                KadreResourceKind.Gamepad to "gamepad",
                KadreResourceKind.EventCollector to "eventCollector",
                KadreResourceKind.Interaction to "interaction",
                KadreResourceKind.DropTransfer to "dropTransfer",
                KadreResourceKind.DropItem to "dropItem",
                KadreResourceKind.CursorImage to "cursorImage",
                KadreResourceKind.GamepadEffect to "gamepadEffect",
                KadreResourceKind.TextInputSession to "textInputSession",
                KadreResourceKind.CaptureSource to "captureSource",
                KadreResourceKind.CaptureSession to "captureSession",
                KadreResourceKind.CaptureCollector to "captureCollector",
                KadreResourceKind.CaptureBuffer to "captureBuffer",
                KadreResourceKind.RetainedPayload to "retainedPayload",
                KadreResourceKind.ImageResource to "imageResource",
                KadreResourceKind.EventSequence to "eventSequence",
            ),
            publishedName = KadreResourceKind::interopName,
        )
    }

    @Test
    fun everyPlatformMemberKeepsItsPublishedName() {
        assertPublishedNames(
            entries = KadrePlatform.entries,
            expected = listOf(
                KadrePlatform.Android to "android",
                KadrePlatform.UIKit to "uikit",
                KadrePlatform.Web to "web",
                KadrePlatform.AppKit to "appKit",
                KadrePlatform.Win32 to "win32",
                KadrePlatform.X11 to "x11",
                KadrePlatform.Wayland to "wayland",
                KadrePlatform.Fake to "fake",
            ),
            publishedName = KadrePlatform::interopName,
        )
    }

    @Test
    fun everyInteractionFailureReasonKeepsItsPublishedName() {
        assertPublishedNames(
            entries = InteractionFailureReason.entries,
            expected = listOf(
                InteractionFailureReason.Missing to "missing",
                InteractionFailureReason.Expired to "expired",
                InteractionFailureReason.Consumed to "consumed",
                InteractionFailureReason.WrongSurface to "wrongSurface",
            ),
            publishedName = InteractionFailureReason::interopName,
        )
    }

    @Test
    fun everyStopReasonKeepsItsPublishedName() {
        assertPublishedNames(
            entries = SessionStopReason.entries,
            expected = listOf(
                SessionStopReason.HostRequested to "hostRequested",
                SessionStopReason.ApplicationRequested to "applicationRequested",
                SessionStopReason.ApplicationCancelled to "applicationCancelled",
                SessionStopReason.ParentCancelled to "parentCancelled",
                SessionStopReason.HostDetached to "hostDetached",
            ),
            publishedName = SessionStopReason::interopName,
        )
    }

    @Test
    fun everyFailureVariantPublishesItsKindAndItsStableFields() {
        val mapped = listOf(
            FoundationFailure.Unsupported(KadreOperation.PlatformSurfaceAccess),
            FoundationFailure.PermissionDenied(KadrePermission.RawInput),
            FoundationFailure.UserCancelled(KadreOperation.CaptureOpen),
            FoundationFailure.TemporarilyUnavailable(retryable = true),
            FoundationFailure.InvalidRequest(null),
            FoundationFailure.InvalidRequest("element"),
            FoundationFailure.AlreadyInUse(KadreResourceKind.Host),
            FoundationFailure.Closed(KadreResourceKind.Surface),
            FoundationFailure.ResourceLimitExceeded(KadreResourceKind.Surface, 4L),
            FoundationFailure.SourceOverflow(KadreResourceKind.EventSequence),
            FoundationFailure.StaleRevision(expected = 2L, received = 3L),
            FoundationFailure.InteractionRequired(InteractionFailureReason.Expired),
            FoundationFailure.UnsupportedPolicy(KadrePolicyComponent.WindowEvents),
            FoundationFailure.ParentScopeCancelled,
            FoundationFailure.ShutdownTimedOut(5.seconds),
            FoundationFailure.ApplicationFailure,
            FoundationFailure.PlatformFailure(KadrePlatform.Web, "WebWindowProvider", "callback-exception"),
        )

        assertEquals(
            listOf(
                "unsupported|operation=platformSurfaceAccess",
                "permissionDenied|permission=rawInput",
                "userCancelled|operation=captureOpen",
                "temporarilyUnavailable|retryable=true",
                "invalidRequest",
                "invalidRequest|field=element",
                "alreadyInUse|resource=host",
                "closed|resource=surface",
                "resourceLimitExceeded|resource=surface|limit=4",
                "sourceOverflow|resource=eventSequence",
                "staleRevision|expected=2|received=3",
                "interactionRequired|reason=expired",
                "unsupportedPolicy|component=windowEvents",
                "parentScopeCancelled",
                "shutdownTimedOut|timeoutNanoseconds=5000000000",
                "applicationFailure",
                "platformFailure|platform=web|domain=WebWindowProvider|code=callback-exception",
            ),
            mapped.map { it.toInterop().describe() },
        )
    }

    @Test
    fun snapshotsAndOutcomesPublishTheirDiscriminant() {
        assertEquals("starting", SessionState.Starting.toInterop().describe())
        assertEquals("running", SessionState.Running.toInterop().describe())
        assertEquals("stopping", SessionState.Stopping.toInterop().describe())
        assertEquals(
            "terminated|outcome=stopped|reason=hostDetached",
            SessionState.Terminated(SessionOutcome.Stopped(SessionStopReason.HostDetached)).toInterop().describe(),
        )
        assertEquals("completed", SessionOutcome.Completed.toInterop().describe())
        assertEquals(
            "failed|failure=parentScopeCancelled",
            SessionOutcome.Failed(FoundationFailure.ParentScopeCancelled).toInterop().describe(),
        )
    }

    @Test
    fun theHandleCallsTheObserverSynchronouslyAndThenOncePerChange() = runTest {
        val session = StubSession()
        val scope = MainScope()
        val handle = KadreSessionHandle(session, scope)
        try {
            val observed = mutableListOf<String>()
            val unsubscribe = handle.subscribeState { observed += it.kind }

            assertEquals(listOf("starting"), observed, "the first call is synchronous")
            assertEquals("starting", handle.state.kind)

            session.transitionTo(SessionState.Running)
            awaitReal(1.seconds) { observed.size == 2 }
            assertEquals(listOf("starting", "running"), observed)

            unsubscribe()
            session.transitionTo(SessionState.Stopping)
            awaitReal(100.milliseconds) { false }
            assertEquals(listOf("starting", "running"), observed, "an unsubscribed observer hears nothing more")
            assertEquals("stopping", handle.state.kind, "the handle keeps reading the live state")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aThrowingObserverIsUnsubscribedAndReportedOutOfBand() = runTest {
        val session = StubSession()
        val scope = MainScope()
        val reported = mutableListOf<String>()
        val handle = KadreSessionHandle(session, scope) { error -> reported += error.message.orEmpty() }
        try {
            val observed = mutableListOf<String>()
            handle.subscribeState {
                observed += it.kind
                error("observer failure")
            }

            assertEquals(listOf("starting"), observed, "the failing observer ran once, synchronously")
            assertEquals(listOf("observer failure"), reported, "the failure is reported out of band")

            session.transitionTo(SessionState.Running)
            awaitReal(100.milliseconds) { false }
            assertEquals(listOf("starting"), observed, "a throwing observer is unsubscribed")
            assertEquals(SessionState.Running, session.state.value, "the session kept running")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun stopAndCloseAreForwardedToTheSession() = runTest {
        val session = StubSession()
        val scope = MainScope()
        val handle = KadreSessionHandle(session, scope)
        try {
            handle.requestStop()
            assertEquals(1, session.stopRequests)
            handle.close()
            assertEquals(1, session.closeRequests)
            assertNull(handle.state.outcome, "a non-terminated session publishes no outcome")
        } finally {
            scope.cancel()
        }
    }

    private fun <T> assertPublishedNames(entries: List<T>, expected: List<Pair<T, String>>, publishedName: (T) -> String) {
        assertEquals(entries, expected.map { it.first }, "the union covers every member, in order")
        assertEquals(expected.size, expected.map { it.second }.toSet().size, "two members must not share a name")
        expected.forEach { (member, name) -> assertEquals(name, publishedName(member)) }
    }

    /**
     * Waits on the real event loop for a bounded time.
     *
     * The handle owns its own `MainScope`, so observer notifications do not run on the virtual clock
     * of [runTest]; a bounded real wait is the only portable synchronisation for them.
     */
    private suspend fun awaitReal(timeout: Duration, condition: () -> Boolean) {
        withContext(Dispatchers.Default) {
            val deadline = TimeSource.Monotonic.markNow() + timeout
            while (!condition() && deadline.hasNotPassedNow()) delay(5)
        }
        if (timeout > 200.milliseconds) assertTrue(condition(), "the observer never received what the test waited for")
    }
}

private fun KadreFailure.describe(): String = buildList {
    add(kind)
    operation?.let { add("operation=$it") }
    permission?.let { add("permission=$it") }
    retryable?.let { add("retryable=$it") }
    field?.let { add("field=$it") }
    resource?.let { add("resource=$it") }
    limit?.let { add("limit=$it") }
    expected?.let { add("expected=$it") }
    received?.let { add("received=$it") }
    reason?.let { add("reason=$it") }
    component?.let { add("component=$it") }
    timeoutNanoseconds?.let { add("timeoutNanoseconds=$it") }
    sourceId?.let { add("sourceId=$it") }
    platform?.let { add("platform=$it") }
    domain?.let { add("domain=$it") }
    code?.let { add("code=$it") }
}.joinToString(separator = "|")

private fun KadreSessionOutcome.describe(): String = buildList {
    add(kind)
    reason?.let { add("reason=$it") }
    failure?.let { add("failure=${it.describe()}") }
}.joinToString(separator = "|")

private fun KadreSessionSnapshot.describe(): String = buildList {
    add(kind)
    outcome?.let { add("outcome=${it.describe()}") }
}.joinToString(separator = "|")

/**
 * A session the facade can observe without a browser.
 *
 * The real sessions come from the runtime; this double exists so the handle contract of `@kadre/host`
 * is provable on both targets without a DOM or a rendered frame.
 */
internal class StubSession : KadreSession {
    private val states = MutableStateFlow<SessionState>(SessionState.Starting)
    private val termination = CompletableDeferred<SessionOutcome>()

    var stopRequests: Int = 0
        private set
    var closeRequests: Int = 0
        private set

    override val id: SessionId get() = error("this double has no stable session identity")

    override val state: StateFlow<SessionState> get() = states

    override fun requestStop(): Unit {
        stopRequests += 1
    }

    override fun close(): Unit {
        closeRequests += 1
    }

    override suspend fun awaitTermination(): SessionOutcome = termination.await()

    fun transitionTo(state: SessionState): Unit {
        states.value = state
    }

    fun terminate(outcome: SessionOutcome): Unit {
        states.value = SessionState.Terminated(outcome)
        termination.complete(outcome)
    }
}
