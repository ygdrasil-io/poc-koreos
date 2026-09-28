package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.InteractionFailureReason
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.interaction.InteractionAction
import org.graphiks.kadre.interaction.InteractionActionOutcome
import org.graphiks.kadre.interaction.InteractionContext
import org.graphiks.kadre.interaction.InteractionEvent
import org.graphiks.kadre.interaction.InteractionHandler
import org.graphiks.kadre.interaction.InteractionKind
import org.graphiks.kadre.interaction.InteractionRegistration
import org.graphiks.kadre.interaction.InteractionRequestId
import org.graphiks.kadre.policy.CollectorOverflowAction
import org.graphiks.kadre.policy.EventDeliveryPolicy
import org.graphiks.kadre.policy.IngressOverflowAction
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.WindowDeliveryPolicy
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.window.FullscreenMode
import org.graphiks.kadre.window.ResizeEdge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.nanoseconds

@OptIn(DelicateKadreApi::class)
class RuntimeInteractionHandlerCommonTest {
    @Test
    fun installSucceedsOnceAndAlreadyInUseUntilTheRegistrationCloses() = runTest {
        val handler = handler()
        val first = handler.install(InteractionHandler { _, _ -> }).successValue()

        assertEquals(
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Interaction)),
            handler.install(InteractionHandler { _, _ -> }),
        )

        first.close()
        assertIs<KadreResult.Success<*>>(handler.install(InteractionHandler { _, _ -> }))
    }

    @Test
    fun nativeInvokesOnceInsideTheRequestAndPublishesCommittedAfterTheHandlerReturns() = runTest {
        val handler = handler()
        val trace = mutableListOf<String>()
        val registration = handler.install(InteractionHandler { context, _ ->
            assertIs<KadreResult.Success<*>>(context.request(InteractionAction.BeginWindowMove))
            trace += "handler-return"
        }).successValue()
        val outcome = async(start = CoroutineStart.UNDISPATCHED) { registration.outcomes.first() }

        handler.dispatch(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
            trace += "native"
            NativeInteractionOutcome.Now(KadreResult.Success(Unit))
        }

        val committed = assertIs<InteractionActionOutcome.Committed>(outcome.await())
        assertEquals(pointerEvent().stamp, committed.stamp)
        assertEquals(listOf("native", "handler-return"), trace)
    }

    @Test
    fun duplicateRetainedExpiredAndUnsupportedRequestsFailWithoutCallingNativeCode() = runTest {
        val handler = handler()
        var retained: InteractionContext? = null
        var nativeCalls = 0
        val requests = mutableListOf<KadreResult<InteractionRequestId>>()
        val failures = mutableListOf<KadreFailure>()
        var duplicateChecked = false
        handler.install(InteractionHandler { context, _ ->
            retained = context
            when (val result = context.request(InteractionAction.BeginWindowMove)) {
                is KadreResult.Success -> requests += result
                is KadreResult.Failure -> failures += result.reason
            }
            if (!duplicateChecked) {
                duplicateChecked = true
                failures += context.request(InteractionAction.BeginWindowMove).failureValue()
            }
        })

        handler.dispatch(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
            nativeCalls += 1
            NativeInteractionOutcome.Now(KadreResult.Success(Unit))
        }
        failures += retained!!.request(InteractionAction.BeginWindowMove).failureValue()
        handler.dispatch(pointerEvent(), emptySet()) {
            NativeInteractionOutcome.Now(KadreResult.Success(Unit))
        }

        assertEquals(1, nativeCalls)
        assertIs<KadreResult.Success<InteractionRequestId>>(requests.single())
        assertEquals(
            listOf(
                KadreFailure.InteractionRequired(InteractionFailureReason.Consumed),
                KadreFailure.InteractionRequired(InteractionFailureReason.Expired),
                KadreFailure.Unsupported(KadreOperation.Interaction),
            ),
            failures,
        )
    }

    @Test
    fun retainedContextIsWrongSurfaceDuringAnotherSurfaceCallback() = runTest {
        val first = handler(surfaceId = SurfaceId(5L))
        val second = handler(surfaceId = SurfaceId(6L))
        var retained: InteractionContext? = null
        var request: KadreResult<InteractionRequestId>? = null
        first.install(InteractionHandler { context, _ ->
            retained = context
            second.dispatch(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
                NativeInteractionOutcome.Now(KadreResult.Success(Unit))
            }
        })
        second.install(InteractionHandler { _, _ ->
            request = retained!!.request(InteractionAction.BeginWindowMove)
        })

        first.dispatch(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
            NativeInteractionOutcome.Now(KadreResult.Success(Unit))
        }

        assertEquals(
            KadreResult.Failure(KadreFailure.InteractionRequired(InteractionFailureReason.WrongSurface)),
            request,
        )
    }

    @Test
    fun backendSubsetCannotExpandTheAdvertisedHandlerActions() = runTest {
        val handler = handler(advertised = setOf(InteractionKind.BeginWindowMove))
        var nativeCalls = 0
        var request: KadreResult<InteractionRequestId>? = null
        handler.install(InteractionHandler { context, _ ->
            request = context.request(InteractionAction.BeginWindowResize(ResizeEdge.North))
        })

        handler.dispatch(pointerEvent(), setOf(InteractionKind.BeginWindowResize)) {
            nativeCalls += 1
            NativeInteractionOutcome.Now(KadreResult.Success(Unit))
        }

        assertEquals(0, nativeCalls)
        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.Interaction)),
            request,
        )
    }

    @Test
    fun closingRegistrationStopsFutureCallbacksWithoutRollingBackTheCommittedAction() = runTest {
        val handler = handler()
        var callbacks = 0
        var nativeCalls = 0
        val registration = handler.install(InteractionHandler { context, _ ->
            callbacks += 1
            context.request(InteractionAction.BeginWindowMove)
        }).successValue()

        handler.dispatch(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
            nativeCalls += 1
            NativeInteractionOutcome.Now(KadreResult.Success(Unit))
        }
        registration.close()
        handler.dispatch(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
            nativeCalls += 1
            NativeInteractionOutcome.Now(KadreResult.Success(Unit))
        }

        assertEquals(1, callbacks)
        assertEquals(1, nativeCalls)
    }

    @Test
    fun closeBeforeRequestRejectsTheNativeAction() = runTest {
        val handler = handler()
        var registration: InteractionRegistration? = null
        var request: KadreResult<InteractionRequestId>? = null
        var nativeCalls = 0
        registration = handler.install(InteractionHandler { context, _ ->
            registration!!.close()
            request = context.request(InteractionAction.BeginWindowMove)
        }).successValue()

        handler.dispatch(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
            nativeCalls += 1
            NativeInteractionOutcome.Now(KadreResult.Success(Unit))
        }

        assertEquals(0, nativeCalls)
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Interaction)),
            request,
        )
    }

    @Test
    fun closingFromHandlerAfterACommitDrainsOutcomeThenCompletes() = runTest {
        val handler = handler()
        lateinit var registration: InteractionRegistration
        registration = handler.install(InteractionHandler { context, _ ->
            assertIs<KadreResult.Success<*>>(context.request(InteractionAction.BeginWindowMove))
            registration.close()
        }).successValue()
        val collected = async(start = CoroutineStart.UNDISPATCHED) { registration.outcomes.toList() }

        handler.dispatch(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
            NativeInteractionOutcome.Now(KadreResult.Success(Unit))
        }

        assertIs<InteractionActionOutcome.Committed>(collected.await().single())
    }

    @Test
    fun closeSourceOutcomeOverflowFailsCurrentAndFutureCollectorsAndReleasesRegistration() = runTest {
        val handler = handler(deliveryPolicy = outcomePolicy(CollectorOverflowAction.CloseSource))
        val registration = handler.install(moveHandler()).successValue()
        val release = CompletableDeferred<Unit>()
        val terminal = async(start = CoroutineStart.UNDISPATCHED) {
            try {
                registration.outcomes.collect { release.await() }
                null
            } catch (error: KadreException) {
                error
            }
        }

        // The first offer is handed to the suspended receiver, the second fills the single
        // collector slot, and the third one overflows the close-source policy.
        handler.dispatch(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
            NativeInteractionOutcome.Now(KadreResult.Success(Unit))
        }
        handler.dispatch(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
            NativeInteractionOutcome.Now(KadreResult.Success(Unit))
        }
        handler.dispatch(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
            NativeInteractionOutcome.Now(KadreResult.Success(Unit))
        }
        release.complete(Unit)

        assertEquals(
            KadreFailure.SourceOverflow(KadreResourceKind.Interaction),
            terminal.await()!!.failure,
        )
        val future = async {
            try {
                registration.outcomes.first()
                null
            } catch (error: KadreException) {
                error
            }
        }
        assertEquals(
            KadreFailure.SourceOverflow(KadreResourceKind.Interaction),
            future.await()!!.failure,
        )
        assertIs<KadreResult.Success<*>>(handler.install(InteractionHandler { _, _ -> }))
    }

    @Test
    fun failSessionOutcomeOverflowReportsOnceAndReleasesRegistration() = runTest {
        val sessionFailures = mutableListOf<KadreFailure>()
        val handler = handler(
            deliveryPolicy = outcomePolicy(CollectorOverflowAction.FailSession),
            sessionFailureHandler = sessionFailures::add,
        )
        val registration = handler.install(moveHandler()).successValue()
        val release = CompletableDeferred<Unit>()
        val terminal = async(start = CoroutineStart.UNDISPATCHED) {
            try {
                registration.outcomes.collect { release.await() }
                null
            } catch (error: KadreException) {
                error
            }
        }

        // The first offer is handed to the suspended receiver, the second fills the single
        // collector slot, and the third one overflows the fail-session policy.
        handler.dispatch(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
            NativeInteractionOutcome.Now(KadreResult.Success(Unit))
        }
        handler.dispatch(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
            NativeInteractionOutcome.Now(KadreResult.Success(Unit))
        }
        handler.dispatch(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
            NativeInteractionOutcome.Now(KadreResult.Success(Unit))
        }
        release.complete(Unit)

        assertEquals(
            KadreFailure.SourceOverflow(KadreResourceKind.Interaction),
            terminal.await()!!.failure,
        )
        assertEquals<List<KadreFailure>>(
            listOf(KadreFailure.SourceOverflow(KadreResourceKind.Interaction)),
            sessionFailures,
        )
        assertIs<KadreResult.Success<*>>(handler.install(InteractionHandler { _, _ -> }))
    }

    @Test
    fun handlerExceptionsAreReportedToTheSessionAndCannotEscapeDispatch() = runTest {
        val sessionFailures = mutableListOf<KadreFailure>()
        val reports = mutableListOf<Throwable>()
        val handler = handler(
            failureReporter = RuntimeFailureReporter(reports::add),
            sessionFailureHandler = sessionFailures::add,
        )
        handler.install(InteractionHandler { _, _ -> error("boom") })

        handler.dispatch(pointerEvent(), emptySet()) {
            NativeInteractionOutcome.Now(KadreResult.Success(Unit))
        }

        assertEquals<List<KadreFailure>>(listOf(KadreFailure.ApplicationFailure), sessionFailures)
        assertEquals("boom", reports.single().message)
    }

    @Test
    fun deferredOutcomeCompletesWhenTheTerminalCallbackFires() = runTest {
        val handler = handler(advertised = setOf(InteractionKind.EnterFullscreen))
        val requests = mutableListOf<KadreResult<InteractionRequestId>>()
        val registration = handler.install(InteractionHandler { context, _ ->
            requests += context.request(InteractionAction.EnterFullscreen(FullscreenMode.Borderless))
        }).successValue()
        val outcome = async(start = CoroutineStart.UNDISPATCHED) { registration.outcomes.first() }
        var completed: Pair<Boolean, KadreFailure?>? = null

        handler.dispatch(pointerEvent(), setOf(InteractionKind.EnterFullscreen)) { _ ->
            NativeInteractionOutcome.Deferred { committed, failure -> completed = committed to failure }
        }
        yield()
        assertTrue(outcome.isActive)

        handler.completePending(InteractionRequestId(0L), committed = true, failure = null)

        val committed = assertIs<InteractionActionOutcome.Committed>(outcome.await())
        assertEquals(InteractionRequestId(0L), committed.requestId)
        assertEquals(pointerEvent().stamp, committed.stamp)
        assertEquals(true to null, completed)

        // The terminal callback released the budget slot, so a later deferred request registers.
        handler.dispatch(pointerEvent(), setOf(InteractionKind.EnterFullscreen)) { _ ->
            NativeInteractionOutcome.Deferred { _, _ -> }
        }
        assertIs<KadreResult.Success<InteractionRequestId>>(requests[1])
    }

    @Test
    fun abandonCompletesDeferredRequestsWithTheClosedFailure() = runTest {
        val handler = handler(advertised = setOf(InteractionKind.EnterFullscreen))
        val requests = mutableListOf<KadreResult<InteractionRequestId>>()
        val registration = handler.install(InteractionHandler { context, _ ->
            requests += context.request(InteractionAction.EnterFullscreen(FullscreenMode.Borderless))
        }).successValue()
        val outcome = async(start = CoroutineStart.UNDISPATCHED) { registration.outcomes.first() }
        var completed: Pair<Boolean, KadreFailure?>? = null

        handler.dispatch(pointerEvent(), setOf(InteractionKind.EnterFullscreen)) { _ ->
            NativeInteractionOutcome.Deferred { committed, failure -> completed = committed to failure }
        }

        handler.close()

        val rejected = assertIs<InteractionActionOutcome.Rejected>(outcome.await())
        assertEquals(InteractionRequestId(0L), rejected.requestId)
        assertEquals(KadreFailure.Closed(KadreResourceKind.Interaction), rejected.failure)
        assertEquals(false to KadreFailure.Closed(KadreResourceKind.Interaction), completed)

        // A late terminal callback for an abandoned request is ignored: the recorded completion
        // must still be the abandonment's, not the late terminal's.
        handler.completePending(InteractionRequestId(0L), committed = true, failure = null)
        assertEquals(false to KadreFailure.Closed(KadreResourceKind.Interaction), completed)
    }

    @Test
    fun pendingBudgetExceededRefusesWithResourceLimit() = runTest {
        val handler = handler(
            advertised = setOf(InteractionKind.EnterFullscreen),
            maxPendingInteractionRequests = 1,
        )
        val requests = mutableListOf<KadreResult<InteractionRequestId>>()
        handler.install(InteractionHandler { context, _ ->
            requests += context.request(InteractionAction.EnterFullscreen(FullscreenMode.Borderless))
        })

        handler.dispatch(pointerEvent(), setOf(InteractionKind.EnterFullscreen)) { _ ->
            NativeInteractionOutcome.Deferred { _, _ -> }
        }
        handler.dispatch(pointerEvent(), setOf(InteractionKind.EnterFullscreen)) { _ ->
            NativeInteractionOutcome.Deferred { _, _ -> }
        }

        val first = assertIs<KadreResult.Success<InteractionRequestId>>(requests[0])
        assertEquals(InteractionRequestId(0L), first.value)
        assertEquals(
            KadreResult.Failure(KadreFailure.ResourceLimitExceeded(KadreResourceKind.Interaction, 1L)),
            requests[1],
        )
    }

    private fun handler(
        surfaceId: SurfaceId = SurfaceId(5L),
        advertised: Set<InteractionKind> = setOf(InteractionKind.BeginWindowMove),
        deliveryPolicy: WindowDeliveryPolicy = KadrePolicies.Default.window,
        maxPendingInteractionRequests: Int = KadrePolicies.Default.resources.maxPendingInteractionRequests,
        failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { },
        sessionFailureHandler: (KadreFailure) -> Unit = {},
    ): RuntimeInteractionHandler = RuntimeInteractionHandler(
        surfaceId = surfaceId,
        advertised = advertised,
        deliveryPolicy = deliveryPolicy,
        eventCollectorGate = RuntimeEventCollectorAllocator(4).newGate(4),
        failureReporter = failureReporter,
        sessionFailureHandler = sessionFailureHandler,
        maxPendingInteractionRequests = maxPendingInteractionRequests,
    )

    private fun moveHandler(): InteractionHandler = InteractionHandler { context, _ ->
        assertIs<KadreResult.Success<*>>(context.request(InteractionAction.BeginWindowMove))
    }

    private fun outcomePolicy(overflow: CollectorOverflowAction): WindowDeliveryPolicy =
        KadrePolicies.Default.window.copy(
            discreteEvents = EventDeliveryPolicy(
                ingressCapacity = 1,
                collectorCapacity = 1,
                ingressOverflow = IngressOverflowAction.CloseSource,
                collectorOverflow = overflow,
            ),
        )

    private fun pointerEvent(): InteractionEvent.PointerPressed = InteractionEvent.PointerPressed(
        PointerButton.Primary,
        LogicalPoint(10.0, 20.0),
        EventStamp(SessionSequence(99), SessionInstant(99.nanoseconds), null),
    )

    private fun <T> KadreResult<T>.successValue(): T = when (this) {
        is KadreResult.Success -> value
        is KadreResult.Failure -> error("expected success, got $reason")
    }

    private fun <T> KadreResult<T>.failureValue(): KadreFailure = when (this) {
        is KadreResult.Success -> error("expected failure, got $value")
        is KadreResult.Failure -> reason
    }
}
