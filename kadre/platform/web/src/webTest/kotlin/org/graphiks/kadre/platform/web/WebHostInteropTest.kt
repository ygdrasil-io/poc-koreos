package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionId
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.diagnostics.InteractionFailureReason
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadrePolicyComponent
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayId
import org.graphiks.kadre.display.DisplayMode
import org.graphiks.kadre.display.DisplayModeId
import org.graphiks.kadre.input.KadrePermission
import org.graphiks.kadre.surface.BinaryImage
import org.graphiks.kadre.surface.ImageFormat
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.window.FullscreenMode
import org.graphiks.kadre.window.WindowSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The value model of `@kadre/host` is a closed mapping: every Kotlin value of
 * `kadre/INTEROP-EXPORTS.md` section 6 has exactly one published name and one encoding.
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
            KadreFailure.Unsupported(KadreOperation.PlatformSurfaceAccess) to
                "{\"kind\":\"unsupported\",\"operation\":\"platformSurfaceAccess\"}",
            KadreFailure.PermissionDenied(KadrePermission.RawInput) to
                "{\"kind\":\"permissionDenied\",\"permission\":\"rawInput\"}",
            KadreFailure.UserCancelled(KadreOperation.CaptureOpen) to
                "{\"kind\":\"userCancelled\",\"operation\":\"captureOpen\"}",
            KadreFailure.TemporarilyUnavailable(retryable = true) to
                "{\"kind\":\"temporarilyUnavailable\",\"retryable\":true}",
            KadreFailure.InvalidRequest(null) to "{\"kind\":\"invalidRequest\",\"field\":null}",
            KadreFailure.InvalidRequest("element") to "{\"kind\":\"invalidRequest\",\"field\":\"element\"}",
            KadreFailure.AlreadyInUse(KadreResourceKind.Host) to
                "{\"kind\":\"alreadyInUse\",\"resource\":\"host\"}",
            KadreFailure.Closed(KadreResourceKind.Surface) to
                "{\"kind\":\"closed\",\"resource\":\"surface\"}",
            KadreFailure.ResourceLimitExceeded(KadreResourceKind.Surface, 4L) to
                "{\"kind\":\"resourceLimitExceeded\",\"resource\":\"surface\",\"limit\":\"4\"}",
            KadreFailure.SourceOverflow(KadreResourceKind.EventSequence) to
                "{\"kind\":\"sourceOverflow\",\"resource\":\"eventSequence\"}",
            KadreFailure.StaleRevision(expected = 2L, received = 3L) to
                "{\"kind\":\"staleRevision\",\"expected\":\"2\",\"received\":\"3\"}",
            KadreFailure.InteractionRequired(InteractionFailureReason.Expired) to
                "{\"kind\":\"interactionRequired\",\"reason\":\"expired\"}",
            KadreFailure.UnsupportedPolicy(KadrePolicyComponent.WindowEvents) to
                "{\"kind\":\"unsupportedPolicy\",\"component\":\"windowEvents\"}",
            KadreFailure.ParentScopeCancelled to "{\"kind\":\"parentScopeCancelled\"}",
            KadreFailure.ShutdownTimedOut(5.seconds) to
                "{\"kind\":\"shutdownTimedOut\",\"timeoutNanoseconds\":\"5000000000\"}",
            KadreFailure.ApplicationFailure to "{\"kind\":\"applicationFailure\"}",
            KadreFailure.PlatformFailure(KadrePlatform.Web, "WebWindowProvider", "callback-exception") to
                "{\"kind\":\"platformFailure\",\"platform\":\"web\"," +
                "\"domain\":\"WebWindowProvider\",\"code\":\"callback-exception\"}",
        )

        mapped.forEach { (failure, encoded) -> assertEquals(encoded, WebInteropJson.encode(failure)) }
    }

    @Test
    fun snapshotAndOutcomeEncodingsCarryTheirDiscriminant() {
        assertEquals("{\"kind\":\"starting\"}", WebInteropJson.encode(SessionState.Starting))
        assertEquals("{\"kind\":\"running\"}", WebInteropJson.encode(SessionState.Running))
        assertEquals("{\"kind\":\"stopping\"}", WebInteropJson.encode(SessionState.Stopping))
        assertEquals(
            "{\"kind\":\"terminated\",\"outcome\":{\"kind\":\"stopped\",\"reason\":\"hostDetached\"}}",
            WebInteropJson.encode(SessionState.Terminated(SessionOutcome.Stopped(SessionStopReason.HostDetached))),
        )
        assertEquals("{\"kind\":\"completed\"}", WebInteropJson.encode(SessionOutcome.Completed))
        assertEquals(
            "{\"kind\":\"failed\",\"failure\":{\"kind\":\"parentScopeCancelled\"}}",
            WebInteropJson.encode(SessionOutcome.Failed(KadreFailure.ParentScopeCancelled)),
        )
    }

    @Test
    fun anEncodedFailureEscapesItsStringPayloads() {
        val encoded = WebInteropJson.encode(
            KadreFailure.PlatformFailure(KadrePlatform.Web, "WebWindowProvider", "quote\"and\\slash"),
        )

        assertEquals(
            "{\"kind\":\"platformFailure\",\"platform\":\"web\",\"domain\":\"WebWindowProvider\"," +
                "\"code\":\"quote\\\"and\\\\slash\"}",
            encoded,
        )
    }

    @Test
    fun theHandleCallsTheObserverSynchronouslyAndThenOncePerChange() = runTest {
        val session = StubSession()
        val scope = MainScope()
        val handle = KadreWebHandle(7, session, scope, "kadre-host-session-test")
        try {
            val observed = mutableListOf<String>()
            val subscription = handle.subscribe { observed += it }

            assertEquals(listOf("{\"kind\":\"starting\"}"), observed, "the first call is synchronous")
            assertEquals("{\"kind\":\"starting\"}", handle.state)

            session.transitionTo(SessionState.Running)
            awaitReal(1.seconds) { observed.size == 2 }
            assertEquals(listOf("{\"kind\":\"starting\"}", "{\"kind\":\"running\"}"), observed)

            KadreWebInterop.unsubscribe(subscription)
            session.transitionTo(SessionState.Stopping)
            awaitReal(100.milliseconds) { false }
            assertEquals(
                listOf("{\"kind\":\"starting\"}", "{\"kind\":\"running\"}"),
                observed,
                "an unsubscribed observer hears nothing more",
            )
            assertEquals("{\"kind\":\"stopping\"}", handle.state, "the handle keeps reading the live state")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aThrowingObserverIsUnsubscribedAndReportedOutOfBand() = runTest {
        val session = StubSession()
        val scope = MainScope()
        val reported = mutableListOf<String>()
        val handle = KadreWebHandle(7, session, scope, "kadre-host-session-test") { error ->
            reported += error.message.orEmpty()
        }
        try {
            val observed = mutableListOf<String>()
            handle.subscribe {
                observed += it
                error("observer failure")
            }

            assertEquals(listOf("{\"kind\":\"starting\"}"), observed, "the failing observer ran once, synchronously")
            assertEquals(listOf("observer failure"), reported, "the failure is reported out of band")

            session.transitionTo(SessionState.Running)
            awaitReal(100.milliseconds) { false }
            assertEquals(listOf("{\"kind\":\"starting\"}"), observed, "a throwing observer is unsubscribed")
            assertEquals(SessionState.Running, session.state.value, "the session kept running")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun stopAndCloseAreForwardedToTheSession() = runTest {
        val session = StubSession()
        val scope = MainScope()
        val handle = KadreWebHandle(7, session, scope, "kadre-host-session-test")
        try {
            handle.requestStop()
            assertEquals(1, session.stopRequests)
            handle.close()
            assertEquals(1, session.closeRequests)
            assertEquals("{\"kind\":\"starting\"}", handle.state, "a non-terminated session has no outcome")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aTerminationSubscriberHearsTheOutcomeExactlyOnce() = runTest {
        val session = StubSession()
        val scope = MainScope()
        val handle = KadreWebHandle(7, session, scope, "kadre-host-session-test")
        try {
            val delivered = mutableListOf<String>()
            handle.subscribeTermination { delivered += it }

            session.terminate(SessionOutcome.Stopped(SessionStopReason.HostRequested))
            awaitReal(1.seconds) { delivered.size == 1 }
            assertEquals(listOf("{\"kind\":\"stopped\",\"reason\":\"hostRequested\"}"), delivered)

            awaitReal(100.milliseconds) { false }
            assertEquals(1, delivered.size, "the registration is one-shot")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun repeatedAsHostRefCallsShareTheFactoryKey() {
        val factory = KadreApplicationFactory { KadreApplication { } }

        val first = factory.asHostRef()
        val second = factory.asHostRef()

        assertEquals(
            first.hostKey,
            second.hostKey,
            "the key belongs to the factory instance: the table must not grow per wrapper",
        )
        assertTrue(first !== second, "each call still creates a light wrapper")
    }

    @Test
    fun aTerminatedSessionDeliversTheTerminalSnapshotAndReleasesTheHandle() = runTest {
        val session = StubSession()
        val scope = MainScope()
        val handle = KadreWebHandle(7, session, scope, "kadre-host-session-test")
        KadreWebInterop.registerHandle(7, handle)
        try {
            val observed = mutableListOf<String>()
            handle.subscribe { observed += it }
            assertTrue(KadreWebInterop.isLiveHandle(7), "a live session is retained while it runs")
            assertEquals(
                1,
                KadreWebInterop.registrationCount(7),
                "an open subscription is registered against the handle it observes",
            )

            session.terminate(SessionOutcome.Stopped(SessionStopReason.HostRequested))
            awaitReal(
                1.seconds,
            ) { observed.lastOrNull() == TERMINAL_STOPPED }

            assertEquals(
                TERMINAL_STOPPED,
                observed.lastOrNull(),
                "the observer hears the terminal snapshot",
            )
            assertTrue(observed.contains("{\"kind\":\"starting\"}"), "the observer heard the first snapshot")
            assertEquals(false, KadreWebInterop.isLiveHandle(7), "the terminated handle is released")
            assertEquals(
                0,
                KadreWebInterop.registrationCount(7),
                "the released handle keeps no subscription: one the consumer never unsubscribed must not " +
                    "retain its observer closure, and what it captured, for the lifetime of the page",
            )

            val released = KadreWebInterop.session(7)
            assertEquals(TERMINAL_STOPPED, released.state, "the released record still answers the state")
            assertEquals("kadre-host-session-test", released.id)

            val lateState = mutableListOf<String>()
            released.subscribe { lateState += it }
            assertEquals(listOf(TERMINAL_STOPPED), lateState, "a late state observer hears the terminal snapshot")
            assertEquals(
                1,
                KadreWebInterop.registrationCount(7),
                "a subscription opened after the release is the released record's own, not the session's",
            )

            val lateTermination = mutableListOf<String>()
            released.subscribeTermination { lateTermination += it }
            assertEquals(
                listOf("{\"kind\":\"stopped\",\"reason\":\"hostRequested\"}"),
                lateTermination,
                "a late termination observer hears the outcome immediately",
            )

            released.requestStop()
            released.close()
            assertEquals(0, session.stopRequests, "a released session forwards no stop request")
            assertEquals(0, session.closeRequests, "a released session forwards no close request")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun theTerminalSnapshotIsDeliveredEvenWhenTheStateFlowDoesNotPublishIt() = runTest {
        val session = StubSession()
        val scope = MainScope()
        val handle = KadreWebHandle(7, session, scope, "kadre-host-session-test")
        try {
            val observed = mutableListOf<String>()
            handle.subscribe { observed += it }

            // The state flow's terminal publication and the session scope's cancellation are
            // independent tasks: this double models a collector that never got to run, so the
            // terminal snapshot can only come from the interop layer's own termination path.
            session.terminateWithoutPublishing(SessionOutcome.Stopped(SessionStopReason.HostRequested))
            awaitReal(1.seconds) { observed.lastOrNull() == TERMINAL_STOPPED }

            assertEquals(
                TERMINAL_STOPPED,
                observed.lastOrNull(),
                "a state observer waiting for terminated must not depend on the state collector",
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aTerminationObserverHearsTheOutcomeWithoutWaitingForTheScope() = runTest {
        val session = StubSession()
        val scope = MainScope()
        val handle = KadreWebHandle(7, session, scope, "kadre-host-session-test")
        try {
            val delivered = mutableListOf<String>()
            handle.subscribeTermination { delivered += it }

            session.terminate(SessionOutcome.Completed)
            awaitReal(1.seconds) { delivered.isNotEmpty() }

            assertEquals(listOf("{\"kind\":\"completed\"}"), delivered)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun everySessionGetsItsOwnOpaqueIdentifier() {
        val first = KadreWebInterop.nextSessionIdentity()
        val second = KadreWebInterop.nextSessionIdentity()

        assertTrue(first.isNotEmpty() && second.isNotEmpty())
        assertTrue(first != second, "two sessions must not share an opaque identifier")
        assertTrue(
            first.startsWith("kadre-host-session-"),
            "the identifier is allocated by the interop layer, not taken from the Kotlin SessionId",
        )
    }

    @Test
    fun everyWindowRequestCarriesItsOwnOpaqueCorrelationToken() {
        val first = KadreWebInterop.nextWindowRequestIdentity()
        val second = KadreWebInterop.nextWindowRequestIdentity()

        assertTrue(first != second, "two window requests must not share a correlation token")
        assertTrue(
            first.startsWith("kadre-window-request-"),
            "the token is allocated by the interop layer, not taken from the Kotlin WindowRequestId",
        )
    }

    @Test
    fun aDisplayTokenIsStablePerDisplayIdentityAndDistinctBetweenDisplays() {
        val first = DisplayId(7)
        val second = DisplayId(8)

        assertEquals(KadreWebInterop.displayKey(first), KadreWebInterop.displayKey(first))
        assertTrue(
            KadreWebInterop.displayKey(first) != KadreWebInterop.displayKey(second),
            "two displays must not share a token",
        )
    }

    @Test
    fun everyAdmittedWindowRequestFailureDecodesFromItsPublishedFields() {
        val none: String? = null
        val mapped = listOf(
            WebWindowFailureFields("unsupported", "requestWindow", none, none, none, null, none, none, none, none) to
                KadreFailure.Unsupported(KadreOperation.RequestWindow),
            WebWindowFailureFields("invalidRequest", none, "element", none, none, null, none, none, none, none) to
                KadreFailure.InvalidRequest("element"),
            WebWindowFailureFields("invalidRequest", none, none, none, none, null, none, none, none, none) to
                KadreFailure.InvalidRequest(null),
            WebWindowFailureFields("interactionRequired", none, none, "missing", none, null, none, none, none, none) to
                KadreFailure.InteractionRequired(InteractionFailureReason.Missing),
            WebWindowFailureFields("interactionRequired", none, none, "expired", none, null, none, none, none, none) to
                KadreFailure.InteractionRequired(InteractionFailureReason.Expired),
            WebWindowFailureFields("interactionRequired", none, none, "consumed", none, null, none, none, none, none) to
                KadreFailure.InteractionRequired(InteractionFailureReason.Consumed),
            WebWindowFailureFields("interactionRequired", none, none, "wrongSurface", none, null, none, none, none, none) to
                KadreFailure.InteractionRequired(InteractionFailureReason.WrongSurface),
            WebWindowFailureFields("alreadyInUse", none, none, none, "host", null, none, none, none, none) to
                KadreFailure.AlreadyInUse(KadreResourceKind.Host),
            WebWindowFailureFields("closed", none, none, none, "host", null, none, none, none, none) to
                KadreFailure.Closed(KadreResourceKind.Host),
            WebWindowFailureFields("parentScopeCancelled", none, none, none, none, null, none, none, none, none) to
                KadreFailure.ParentScopeCancelled,
            WebWindowFailureFields("resourceLimitExceeded", none, none, none, "window", null, "4", none, none, none) to
                KadreFailure.ResourceLimitExceeded(KadreResourceKind.Window, 4L),
            WebWindowFailureFields("temporarilyUnavailable", none, none, none, none, true, none, none, none, none) to
                KadreFailure.TemporarilyUnavailable(retryable = true),
            WebWindowFailureFields("temporarilyUnavailable", none, none, none, none, false, none, none, none, none) to
                KadreFailure.TemporarilyUnavailable(retryable = false),
            WebWindowFailureFields("platformFailure", none, none, none, none, null, none, "web", "WebWindowProvider", "callback-exception") to
                KadreFailure.PlatformFailure(KadrePlatform.Web, "WebWindowProvider", "callback-exception"),
        )

        mapped.forEach { (fields, expected) -> assertEquals(expected, decodeWindowRequestFailure(fields)) }
    }

    @Test
    fun aFailureOutsideTheClosedWindowRequestSetDoesNotDecode() {
        val none: String? = null
        val outside = listOf(
            // An `unsupported` naming another operation is not the closed set's row.
            WebWindowFailureFields("unsupported", "updateSurface", none, none, none, null, none, none, none, none),
            // A reason, a resource or a platform the unions do not name is out of the set.
            WebWindowFailureFields("interactionRequired", none, none, "bogus", none, null, none, none, none, none),
            WebWindowFailureFields("alreadyInUse", none, none, none, "surface", null, none, none, none, none),
            WebWindowFailureFields("closed", none, none, none, "window", null, none, none, none, none),
            WebWindowFailureFields("resourceLimitExceeded", none, none, none, "windowRequest", null, "2", none, none, none),
            // A limit that is absent or not a number payload does not decode either.
            WebWindowFailureFields("resourceLimitExceeded", none, none, none, "window", null, none, none, none, none),
            WebWindowFailureFields("resourceLimitExceeded", none, none, none, "window", null, "many", none, none, none),
            // A `temporary` without its retryable payload is not a member.
            WebWindowFailureFields("temporarilyUnavailable", none, none, none, none, null, none, none, none, none),
            // A platform failure needs all three payloads from the closed unions.
            WebWindowFailureFields("platformFailure", none, none, none, none, null, none, "natron", "WebWindowProvider", "x"),
            WebWindowFailureFields("platformFailure", none, none, none, none, null, none, "web", none, "x"),
            WebWindowFailureFields("platformFailure", none, none, none, none, null, none, "web", "WebWindowProvider", none),
            // Anything else — an unknown kind or no kind at all — is undecodable.
            WebWindowFailureFields("bogus", none, none, none, none, null, none, none, none, none),
            WebWindowFailureFields(null, none, none, none, none, null, none, none, none, none),
        )

        outside.forEach { fields -> assertNull(decodeWindowRequestFailure(fields), "no failure for $fields") }
    }

    @Test
    fun aPresentWindowProviderOptionWithoutCallableOpenIsRefusedInsteadOfIgnored() {
        val key = KadreApplicationFactory { KadreApplication { awaitCancellation() } }.asHostRef().hostKey
        var attachReached = false

        val refused = attachSession(
            "element",
            key,
            "default",
            "manual",
            WebWindowProviderOption.Invalid,
        ) { _, _, _, _, _, _ ->
            attachReached = true
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host))
        }

        assertEquals(
            "{\"kind\":\"invalidRequest\",\"field\":\"options.windowProvider\"}",
            refused.removePrefix("failed|"),
        )
        assertFalse(attachReached, "a refused option never reaches the attach")
    }

    @Test
    fun anAbsentWindowProviderOptionAttachesWithoutAProvider() {
        val key = KadreApplicationFactory { KadreApplication { awaitCancellation() } }.asHostRef().hostKey
        var received: Any? = "unset"

        val refused = attachSession(
            "element",
            key,
            "default",
            "manual",
            WebWindowProviderOption.Absent,
        ) { _, _, _, _, _, provider ->
            received = provider
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host))
        }

        assertNull(received, "an absent option attaches with no provider")
        assertEquals("{\"kind\":\"closed\",\"resource\":\"host\"}", refused.removePrefix("failed|"))
    }

    @Test
    fun aProvidedWindowProviderOptionReachesTheAttachUnchanged() {
        val key = KadreApplicationFactory { KadreApplication { awaitCancellation() } }.asHostRef().hostKey
        var received: Any? = "unset"

        attachSession(
            "element",
            key,
            "default",
            "manual",
            WebWindowProviderOption.Provided("the-target-provider"),
        ) { _, _, _, _, _, provider ->
            received = provider
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host))
        }

        assertEquals("the-target-provider", received, "the target's own provider value crosses untouched")
    }

    @Test
    fun theWindowProviderOptionIsRefusedAfterTheOtherOptions() {
        val key = KadreApplicationFactory { KadreApplication { awaitCancellation() } }.asHostRef().hostKey

        assertEquals(
            "options.policy",
            (attachSession(
                "element",
                key,
                "turbo",
                "manual",
                WebWindowProviderOption.Invalid,
            ) { _, _, _, _, _, _ -> KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host)) })
                .removePrefix("failed|")
                .removePrefix("{\"kind\":\"invalidRequest\",\"field\":\"")
                .removeSuffix("\"}"),
            "the policy is resolved before the provider option",
        )
        assertEquals(
            "options.attachmentPolicy",
            (attachSession(
                "element",
                key,
                "default",
                "sometimes",
                WebWindowProviderOption.Invalid,
            ) { _, _, _, _, _, _ -> KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host)) })
                .removePrefix("failed|")
                .removePrefix("{\"kind\":\"invalidRequest\",\"field\":\"")
                .removeSuffix("\"}"),
            "the attachment policy is resolved before the provider option",
        )
        assertEquals(
            "factoryKey",
            (attachSession(
                "element",
                "kadre-factory-none",
                "default",
                "manual",
                WebWindowProviderOption.Invalid,
            ) { _, _, _, _, _, _ -> KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host)) })
                .removePrefix("failed|")
                .removePrefix("{\"kind\":\"invalidRequest\",\"field\":\"")
                .removeSuffix("\"}"),
            "the factory key is resolved before the provider option",
        )
    }

    @Test
    fun theSpecDtoJsonCarriesThePublishedShapes() {
        val spec = WindowSpec(
            title = "fac\"ade",
            icon = BinaryImage(byteArrayOf(1, 2, 3), ImageFormat.Png, PhysicalSize(4, 5)),
            minimumSize = LogicalSize(320.0, 240.0),
            outerPosition = PhysicalPoint(3, 4),
            fullscreen = FullscreenMode.Exclusive(
                DisplayId(9),
                DisplayMode(DisplayModeId(1), PhysicalSize(3840, 2160), 120.0, 10),
            ),
        )

        val json = webWindowSpecDtoJson(spec, "kadre-display-0")

        // Every field of the section 6 shape is present, quoted where the union quotes it, with the
        // escaped strings and the base64 icon payload — the exact `Double` rendering is the target's.
        assertTrue(json.startsWith("{\"title\":\"fac\\\"ade\",\"contentSize\":{"), json)
        assertTrue(json.contains("\"minimumSize\":{\"width\":"), json)
        assertTrue(json.contains("\"maximumSize\":null"), json)
        assertTrue(json.contains("\"outerPosition\":{\"x\":3,\"y\":4}"), json)
        assertTrue(json.contains("\"resizable\":true"), json)
        assertTrue(
            json.contains(
                "\"fullscreen\":{\"kind\":\"exclusive\",\"displayId\":\"kadre-display-0\"," +
                    "\"physicalWidth\":3840,\"physicalHeight\":2160,\"refreshRateHz\":",
            ),
            json,
        )
        assertTrue(json.contains("\"bitDepth\":10}"), json)
        assertTrue(json.contains("\"decorations\":\"system\",\"systemButtons\":\"all\",\"level\":\"normal\""), json)
        assertTrue(json.contains("\"transparent\":false,\"blurBehind\":false"), json)
        assertTrue(
            json.contains("\"icon\":{\"format\":\"png\",\"bytes\":\"AQID\",\"pixelSize\":{\"width\":4,\"height\":5}}"),
            json,
        )
        assertTrue(json.contains("\"contentProtection\":false}"), json)
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
        if (timeout > 200.milliseconds) assertTrue(condition(), "the observer never delivered what the test waited for")
    }
}

/** The terminal snapshot of a session stopped by the host, as the shim receives it. */
private const val TERMINAL_STOPPED = "{\"kind\":\"terminated\",\"outcome\":{\"kind\":\"stopped\",\"reason\":\"hostRequested\"}}"

/**
 * A session the interop layer can observe without a browser.
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

    /** Ends the session without publishing the terminal state, as a cancelled collector would see it. */
    fun terminateWithoutPublishing(outcome: SessionOutcome): Unit {
        termination.complete(outcome)
    }
}
