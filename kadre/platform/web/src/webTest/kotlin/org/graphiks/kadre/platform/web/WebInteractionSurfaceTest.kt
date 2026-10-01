package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.InteractionFailureReason
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.interaction.InteractionAction
import org.graphiks.kadre.interaction.InteractionActionOutcome
import org.graphiks.kadre.interaction.InteractionContext
import org.graphiks.kadre.interaction.InteractionEvent
import org.graphiks.kadre.interaction.InteractionHandler
import org.graphiks.kadre.interaction.InteractionKind
import org.graphiks.kadre.interaction.InteractionRegistration
import org.graphiks.kadre.interaction.InteractionRequestId
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeSynchronousInteraction
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.window.FullscreenMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The interaction seam of the web surface: the common token engine the runtime lifted, installed on
 * `WebHostSurface`, dispatched from the target's listeners before the ordinary input, and completed
 * at the browser's terminal answers.
 *
 * Every case drives the shared [RecordingWebHostPort] and no browser: the dispatcher the surface
 * registers is what a DOM port will invoke inside its own `pointerdown`/`keydown` listeners, so the
 * tests invoke that same seam and read back the journal the port records. What is proven here is the
 * surface's own contract — the capability flip at the structural install only, one registration, the
 * admission-before-native-call ordering for actions outside the supported set, the deferred outcomes
 * a browser primitive needs, the refusal of a `LockPointer` mode this target does not take, the
 * containment of a throwing handler, and the teardown that abandons the deferred pendings.
 */
@OptIn(ExperimentalCoroutinesApi::class, DelicateKadreApi::class)
class WebInteractionSurfaceTest {
    @Test
    fun handlerInteractionsIsTheFourWebActionsOnceStructurallyInstalled() = runTest {
        val harness = InteractionHarness(this)
        harness.start()

        assertEquals(
            Capability.Supported(
                setOf(
                    InteractionKind.EnterFullscreen,
                    InteractionKind.ExitFullscreen,
                    InteractionKind.LockPointer,
                    InteractionKind.UnlockPointer,
                ),
                FeatureAvailability.Available,
            ),
            harness.surface().capabilities.value.handlerInteractions,
            "the capability is the structural snapshot: the same install that builds the engine publishes it",
        )
        assertEquals(1, harness.port.interactionDispatcherInstallations)

        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun armedInteractionsRemainsUnsupported() = runTest {
        val harness = InteractionHarness(this)
        harness.start()

        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.ArmInteraction)),
            harness.surface().capabilities.value.armedInteractions,
        )

        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun thePreInstallSnapshotClaimsNoInteractionAtAll() {
        val preInstall = preInstallSurfaceCapabilities()

        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.InstallInteractionHandler)),
            preInstall.handlerInteractions,
            "a surface whose session configuration has not installed claims no handler interactions",
        )
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.ArmInteraction)),
            preInstall.armedInteractions,
        )
    }

    @Test
    fun aSecondHandlerIsRefusedAlreadyInUse() = runTest {
        val harness = InteractionHarness(this)
        harness.start()
        val surface = harness.surface()
        val registration = harness.installHandler { _, _ -> }

        assertEquals(
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Interaction)),
            surface.installInteractionHandler { _, _ -> },
            "one registration lives at a time, whatever the second handler is",
        )

        registration.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun theHandlerIsDispatchedFromTheListenerBeforeTheOrdinaryStimulus() = runTest {
        val harness = InteractionHarness(this)
        harness.start()
        val surface = harness.surface()
        val events = mutableListOf<InteractionEvent>()
        val revisionsAtHandler = mutableListOf<Long>()
        val registration = harness.installHandler { _, event ->
            events += event
            revisionsAtHandler += surface.input.state.value.revision.value
        }

        val revisionBefore = surface.input.state.value.revision.value
        harness.port.deliverInteractionThenInput(
            RuntimeSynchronousInteraction.PointerPressed(PointerButton.Primary, POINT, PRESSURE),
            WebInputStimulus.PointerButtonChanged(
                button = PointerButton.Primary,
                buttonState = PointerButtonState.Pressed,
                position = POINT,
                pressure = PRESSURE,
                kind = PointerKind.Mouse,
                pen = null,
            ),
        )
        testScheduler.runCurrent()

        assertEquals(
            listOf("interaction", "observation"),
            harness.port.deliveryJournal,
            "the listener seam dispatches the interaction before it delivers the observation",
        )
        val pressed = assertIs<InteractionEvent.PointerPressed>(events.single())
        assertEquals(PointerButton.Primary, pressed.button)
        assertEquals(POINT, pressed.position)
        assertEquals(
            listOf(revisionBefore),
            revisionsAtHandler,
            "the handler ran before the ordinary stimulus moved the input state",
        )
        assertTrue(
            surface.input.state.value.revision.value > revisionBefore,
            "the ordinary stimulus was still admitted, after the interaction",
        )

        registration.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun anUnsupportedActionNeverTouchesTheBrowser() = runTest {
        val harness = InteractionHarness(this)
        harness.start()
        val requests = mutableListOf<KadreResult<InteractionRequestId>>()
        val registration = harness.installHandler { context, _ ->
            requests += context.request(InteractionAction.BeginWindowMove)
            requests += context.request(InteractionAction.OpenWindow())
        }
        val outcomes = collectOutcomes(registration)

        harness.port.deliverInteraction(KEY_TRIGGER)
        testScheduler.runCurrent()

        assertEquals(
            listOf<KadreResult<InteractionRequestId>>(
                KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.Interaction)),
                KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.Interaction)),
            ),
            requests,
            "both actions are outside the supported set and are refused at admission",
        )
        assertTrue(
            harness.port.primitiveCalls.isEmpty() && harness.port.pointerCaptureRequests.isEmpty(),
            "a refused action reaches no browser API: not the interaction primitives, not the capture",
        )
        assertTrue(outcomes.await().isEmpty(), "a refused request publishes no outcome")

        registration.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aRetainedContextIsRefusedExpiredAfterTheHandlerReturns() = runTest {
        val harness = InteractionHarness(this)
        harness.start()
        lateinit var retained: InteractionContext
        val requests = mutableListOf<KadreResult<InteractionRequestId>>()
        val registration = harness.installHandler { context, _ ->
            requests += context.request(InteractionAction.EnterFullscreen(FullscreenMode.Borderless))
            retained = context
        }

        harness.port.deliverInteraction(KEY_TRIGGER)
        testScheduler.runCurrent()
        assertIs<KadreResult.Success<InteractionRequestId>>(requests.single())

        assertEquals(
            KadreResult.Failure(KadreFailure.InteractionRequired(InteractionFailureReason.Expired)),
            retained.request(InteractionAction.EnterFullscreen(FullscreenMode.Borderless)),
            "the context of a returned callback is invalid: the token may not be reused",
        )

        registration.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun lockPointerRefusesEveryModeButLockedAsInvalidActionMode() = runTest {
        val harness = InteractionHarness(this)
        harness.start()
        val requests = mutableListOf<KadreResult<InteractionRequestId>>()
        val registration = harness.installHandler { context, _ ->
            // The token is single-use per callback, so the second mode arrives in its own dispatch.
            val mode = if (requests.isEmpty()) PointerCaptureMode.Confined else PointerCaptureMode.None
            requests += context.request(InteractionAction.LockPointer(mode))
        }
        val outcomes = collectOutcomes(registration)

        harness.port.deliverInteraction(KEY_TRIGGER)
        testScheduler.runCurrent()
        val firstRequest = assertIs<KadreResult.Success<InteractionRequestId>>(requests[0]).value

        harness.port.deliverInteraction(KEY_TRIGGER)
        testScheduler.runCurrent()
        val secondRequest = assertIs<KadreResult.Success<InteractionRequestId>>(requests[1]).value

        assertEquals(
            listOf(firstRequest, secondRequest),
            outcomes.await().map { it.requestId },
            "both refused modes still produced one outcome per request",
        )
        assertEquals(
            listOf(
                KadreFailure.InvalidRequest("action.mode"),
                KadreFailure.InvalidRequest("action.mode"),
            ),
            outcomes.await().map { (it as InteractionActionOutcome.Rejected).failure },
            "`Confined` and `None` are not the action: `Confined` stays the `SurfaceUpdate." +
                "pointerCapture` path of this target, and `None` contradicts the action itself",
        )
        assertTrue(harness.port.primitiveCalls.isEmpty(), "a refused mode reaches no pointer-lock call")

        registration.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun handlerExceptionBecomesApplicationFailureAndStopsTheSession() = runTest {
        val harness = InteractionHarness(this)
        harness.start()
        val registration = harness.installHandler { _, _ -> error("boom") }

        harness.port.deliverInteraction(KEY_TRIGGER)
        testScheduler.runCurrent()

        assertTrue(
            harness.reportedFailures.any { it.message == "boom" },
            "the exception is reported, never rethrown across the DOM callback boundary",
        )
        assertEquals(
            SessionOutcome.Failed(KadreFailure.ApplicationFailure),
            harness.outcome(),
            "the captured exception fails the session by the ordinary application rules",
        )

        registration.close()
        testScheduler.runCurrent()
    }

    @Test
    fun terminationAbandonsDeferredRequestsWithClosedInteraction() = runTest {
        val harness = InteractionHarness(this)
        harness.start()
        // The primitive is emitted and the browser never answers: the request stays deferred.
        harness.port.requestFullscreenEffect = { KadreResult.Success(Unit) }
        val requests = mutableListOf<KadreResult<InteractionRequestId>>()
        val registration = harness.installHandler { context, _ ->
            requests += context.request(InteractionAction.EnterFullscreen(FullscreenMode.Borderless))
        }
        val outcomes = collectOutcomes(registration)

        harness.port.deliverInteraction(KEY_TRIGGER)
        testScheduler.runCurrent()
        val requestId = assertIs<KadreResult.Success<InteractionRequestId>>(requests.single()).value

        harness.stop()
        testScheduler.runCurrent()

        val abandoned = assertIs<InteractionActionOutcome.Rejected>(outcomes.await().single())
        assertEquals(requestId, abandoned.requestId)
        assertEquals(
            KadreFailure.Closed(KadreResourceKind.Interaction),
            abandoned.failure,
            "the surface's terminalisation abandons the deferred pendings with the closed failure",
        )
    }

    @Test
    fun pendingRequestsRespectMaxPendingInteractionRequests() = runTest {
        val policy = KadrePolicies.Default.copy(
            resources = KadrePolicies.Default.resources.copy(maxPendingInteractionRequests = 1),
        )
        val harness = InteractionHarness(this, policy)
        harness.start()
        harness.port.requestFullscreenEffect = { KadreResult.Success(Unit) }
        val requests = mutableListOf<KadreResult<InteractionRequestId>>()
        val registration = harness.installHandler { context, _ ->
            requests += context.request(InteractionAction.EnterFullscreen(FullscreenMode.Borderless))
        }

        harness.port.deliverInteraction(KEY_TRIGGER)
        testScheduler.runCurrent()
        harness.port.deliverInteraction(KEY_TRIGGER)
        testScheduler.runCurrent()

        assertIs<KadreResult.Success<InteractionRequestId>>(requests[0])
        assertEquals(
            KadreResult.Failure(KadreFailure.ResourceLimitExceeded(KadreResourceKind.Interaction, 1L)),
            requests[1],
            "the second deferred request is refused for budget, and its failure is the only signal",
        )

        registration.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aRefusingDefaultNeverReachesATerminalAndIsRejectedSynchronously() = runTest {
        val harness = InteractionHarness(this)
        harness.start()
        val requests = mutableListOf<KadreResult<InteractionRequestId>>()
        val registration = harness.installHandler { context, _ ->
            requests += context.request(InteractionAction.EnterFullscreen(FullscreenMode.Borderless))
        }
        val outcomes = collectOutcomes(registration)

        harness.port.deliverInteraction(KEY_TRIGGER)
        testScheduler.runCurrent()

        val requestId = assertIs<KadreResult.Success<InteractionRequestId>>(requests.single()).value
        val rejected = assertIs<InteractionActionOutcome.Rejected>(outcomes.await().single())
        assertEquals(requestId, rejected.requestId)
        assertEquals(
            KadreFailure.PlatformFailure(KadrePlatform.Web, "fullscreen", "refused"),
            rejected.failure,
            "a port that cannot emit the primitive answers synchronously: the refusal is the outcome",
        )
        assertNull(rejected.dropOfferId)
        assertEquals(listOf("requestFullscreen"), harness.port.primitiveCalls)

        registration.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aSynchronousExitTerminalCommitsThePending() = runTest {
        val harness = InteractionHarness(this)
        harness.start()
        val requests = mutableListOf<KadreResult<InteractionRequestId>>()
        val registration = harness.installHandler { context, _ ->
            requests += context.request(InteractionAction.ExitFullscreen)
        }
        val outcomes = collectOutcomes(registration)

        harness.port.deliverInteraction(KEY_TRIGGER)
        testScheduler.runCurrent()

        val committed = assertIs<InteractionActionOutcome.Committed>(outcomes.await().single())
        assertEquals(
            assertIs<KadreResult.Success<InteractionRequestId>>(requests.single()).value,
            committed.requestId,
            "the exiting default fires its terminal synchronously, and the pending completes committed",
        )
        assertNull(committed.dropOfferId)
        assertEquals(listOf("exitFullscreen"), harness.port.primitiveCalls)

        registration.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aDeferredTerminalRefusalIsRejectedWithTheRefusalFailureAndFreesTheBudget() = runTest {
        val harness = InteractionHarness(this)
        harness.start()
        // The primitive is emitted; the browser's terminal answer has not arrived yet.
        harness.port.requestFullscreenEffect = { KadreResult.Success(Unit) }
        val requests = mutableListOf<KadreResult<InteractionRequestId>>()
        val registration = harness.installHandler { context, _ ->
            requests += context.request(InteractionAction.EnterFullscreen(FullscreenMode.Borderless))
        }
        val outcomes = collectOutcomes(registration)

        harness.port.deliverInteraction(KEY_TRIGGER)
        testScheduler.runCurrent()
        val requestId = assertIs<KadreResult.Success<InteractionRequestId>>(requests.single()).value

        // The browser refuses, after the callback returned: `fullscreenerror` fires the terminal.
        harness.port.fireLatestTerminal(committed = false)
        testScheduler.runCurrent()

        val refused = assertIs<InteractionActionOutcome.Rejected>(outcomes.await().single())
        assertEquals(requestId, refused.requestId)
        assertEquals(
            KadreFailure.PlatformFailure(KadrePlatform.Web, "fullscreen", "refused"),
            refused.failure,
            "a browser refusal is a rejected outcome carrying the one honest code",
        )

        // The budget slot went with the outcome, so a later deferred request is admitted again.
        harness.port.deliverInteraction(KEY_TRIGGER)
        testScheduler.runCurrent()
        assertIs<KadreResult.Success<InteractionRequestId>>(requests[1])

        registration.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * Collects the registration's outcomes from a coroutine that starts eagerly and suspends on the
     * first emission: the list is readable after the scheduler has been pumped, and an empty list
     * after pumping means no outcome was published at all.
     */
    private fun TestScope.collectOutcomes(registration: InteractionRegistration): OutcomeCollector {
        val outcomes = mutableListOf<InteractionActionOutcome>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            registration.outcomes.collect { outcomes += it }
        }
        return OutcomeCollector(outcomes, collector, testScheduler)
    }

    private class OutcomeCollector(
        private val outcomes: MutableList<InteractionActionOutcome>,
        private val collector: Job,
        private val scheduler: TestCoroutineScheduler,
    ) {
        fun await(): List<InteractionActionOutcome> {
            scheduler.runCurrent()
            return outcomes
        }
    }

    companion object {
        private val POINT = LogicalPoint(3.0, 4.0)
        private const val PRESSURE = 0.5
        private val KEY_TRIGGER = RuntimeSynchronousInteraction.KeyPressed(
            PhysicalKey.Code(usagePage = 0x07, usageId = 0x04),
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class, DelicateKadreApi::class)
private class InteractionHarness(
    scope: TestScope,
    policy: KadrePolicy = KadrePolicies.Default,
) {
    val port = RecordingWebHostPort(WebSurfaceMetrics(48.0, 48.0, 1.0))

    /**
     * Every cause the session's failure reporter was handed: a throwing handler is *reported*, never
     * rethrown across the DOM callback boundary, so this is where a case reads the report from.
     */
    val reportedFailures: MutableList<Throwable> = mutableListOf()

    private val scopeReady = CompletableDeferred<KadreScope>()
    private val session: KadreSession

    init {
        session = assertIs<KadreResult.Success<KadreSession>>(
            WebHostSession(
                port = port,
                registry = WebHostRegistry(),
                failureReporter = RuntimeFailureReporter { cause -> reportedFailures += cause },
            ).attach(
                parentScope = scope,
                applicationFactory = KadreApplicationFactory {
                    KadreApplication {
                        scopeReady.complete(this)
                        awaitCancellation()
                    }
                },
                policy = policy,
            ),
        ).value
    }

    /** Pumping the scheduler until the application scope has been handed to the test. */
    suspend fun start() = scopeReady.await()

    /** The surface this session owns, once its application scope exists. */
    fun surface(): HostSurface = scopeReady.getCompleted().primarySurface.value
        ?: error("a web session exposes a primary surface")

    /** Installs the interaction handler the case drives, and answers with its registration. */
    fun installHandler(
        handler: (context: InteractionContext, event: InteractionEvent) -> Unit,
    ): InteractionRegistration = assertIs<KadreResult.Success<InteractionRegistration>>(
        surface().installInteractionHandler(InteractionHandler(handler)),
    ).value

    /** The terminal outcome the runtime published for this session. */
    suspend fun outcome(): SessionOutcome = session.awaitTermination()

    fun stop() = session.requestStop()
}
