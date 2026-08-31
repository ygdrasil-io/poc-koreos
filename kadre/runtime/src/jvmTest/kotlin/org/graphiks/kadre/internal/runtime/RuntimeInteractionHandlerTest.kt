package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.diagnostics.InteractionFailureReason
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.interaction.InteractionHandler
import org.graphiks.kadre.interaction.InteractionKind
import org.graphiks.kadre.interaction.InteractionAction
import org.graphiks.kadre.interaction.InteractionActionOutcome
import org.graphiks.kadre.interaction.InteractionEvent
import org.graphiks.kadre.interaction.InteractionContext
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.WindowDeliveryPolicy
import org.graphiks.kadre.policy.EventDeliveryPolicy
import org.graphiks.kadre.policy.CollectorOverflowAction
import org.graphiks.kadre.policy.IngressOverflowAction
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.SurfaceCapabilities
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.nanoseconds
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(DelicateKadreApi::class, ExperimentalCoroutinesApi::class)
class RuntimeInteractionHandlerTest {
    @Test
    fun installHandlerSucceedsWhenTheBackendAdvertisesSupportedInteractions() = runTest {
        val surface = surface()

        val result = surface.installInteractionHandler(InteractionHandler { _, _ -> })

        assertIs<KadreResult.Success<*>>(result)
    }

    @Test
    fun onlyOneHandlerMayBeRegisteredUntilTheActiveRegistrationCloses() = runTest {
        val surface = surface()
        val first = surface.installInteractionHandler(InteractionHandler { _, _ -> }).successValue()

        assertEquals(
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Interaction)),
            surface.installInteractionHandler(InteractionHandler { _, _ -> }),
        )

        first.close()
        assertIs<KadreResult.Success<*>>(surface.installInteractionHandler(InteractionHandler { _, _ -> }))
    }

    @Test
    fun pointerInteractionInvokesHandlerBeforePublishingTheSameStampedInput() = runTest {
        val surface = surface()
        val order = mutableListOf<String>()
        var handlerEvent: InteractionEvent? = null
        surface.installInteractionHandler(InteractionHandler { _, event ->
            order += "handler"
            handlerEvent = event
        })
        val input = async(start = CoroutineStart.UNDISPATCHED) {
            surface.input.events.first().also { order += "input" }
        }

        surface.dispatchSynchronousInteraction(pointerEvent(), emptySet()) { KadreResult.Success(Unit) }

        val ordinary = assertIs<InputEvent.PointerButtonChanged>(input.await())
        assertEquals(listOf("handler", "input"), order)
        assertEquals(handlerEvent?.stamp, ordinary.stamp)
    }

    @Test
    fun nestedCallbackInputWaitsUntilTheOuterCallbackInputHasBeenAdmitted() = runTest {
        val surface = surface()
        val trace = mutableListOf<String>()
        surface.installInteractionHandler(InteractionHandler { _, _ ->
            trace += "outer-start"
            surface.dispatchSynchronousInteraction(pointerEvent(), emptySet()) { KadreResult.Success(Unit) }
            trace += "outer-return"
        })
        val ordinary = async(start = CoroutineStart.UNDISPATCHED) {
            surface.input.events
                .onEach { trace += "input-${it.stamp.sequence.value}" }
                .take(2)
                .toList()
        }

        surface.dispatchSynchronousInteraction(pointerEvent(), emptySet()) { KadreResult.Success(Unit) }

        assertEquals(
            listOf(0L, 1L),
            ordinary.await().map { it.stamp.sequence.value },
        )
        assertEquals(listOf("outer-start", "outer-return", "input-0", "input-1"), trace)
    }

    @Test
    fun beginWindowMoveInvokesNativeOnceBeforeHandlerReturnsAndPublishesCommittedOutcomeAfterward() = runTest {
        val surface = surface()
        val trace = mutableListOf<String>()
        val registration = surface.installInteractionHandler(InteractionHandler { context, _ ->
            assertIs<KadreResult.Success<*>>(context.request(InteractionAction.BeginWindowMove))
            trace += "handler-return"
        }).successValue()
        val outcome = async(start = CoroutineStart.UNDISPATCHED) { registration.outcomes.first() }

        surface.dispatchSynchronousInteraction(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
            trace += "native"
            KadreResult.Success(Unit)
        }

        assertIs<InteractionActionOutcome.Committed>(outcome.await())
        assertEquals(listOf("native", "handler-return"), trace)
    }

    @Test
    fun duplicateRetainedAndUnsupportedRequestsFailWithoutCallingNativeCode() = runTest {
        val surface = surface()
        var retained: InteractionContext? = null
        var nativeCalls = 0
        val failures = mutableListOf<KadreFailure>()
        surface.installInteractionHandler(InteractionHandler { context, _ ->
            retained = context
            assertIs<KadreResult.Success<*>>(context.request(InteractionAction.BeginWindowMove))
            failures += context.request(InteractionAction.BeginWindowMove).failureValue()
        })

        surface.dispatchSynchronousInteraction(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
            nativeCalls += 1
            KadreResult.Success(Unit)
        }
        failures += retained!!.request(InteractionAction.BeginWindowMove).failureValue()
        val unsupportedSurface = surface()
        unsupportedSurface.installInteractionHandler(InteractionHandler { context, _ ->
            failures += context.request(InteractionAction.BeginWindowMove).failureValue()
        })
        unsupportedSurface.dispatchSynchronousInteraction(pointerEvent(), emptySet()) { nativeCalls += 1; KadreResult.Success(Unit) }

        assertEquals(1, nativeCalls)
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
    fun backendSubsetCannotExpandTheAdvertisedHandlerActions() = runTest {
        val surface = surface()
        var nativeCalls = 0
        var result: KadreResult<*>? = null
        surface.installInteractionHandler(InteractionHandler { context, _ ->
            result = context.request(
                InteractionAction.BeginWindowResize(org.graphiks.kadre.window.ResizeEdge.North),
            )
        })

        surface.dispatchSynchronousInteraction(pointerEvent(), setOf(InteractionKind.BeginWindowResize)) {
            nativeCalls += 1
            KadreResult.Success(Unit)
        }

        assertEquals(0, nativeCalls)
        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.Interaction)),
            result,
        )
    }

    @Test
    fun closingRegistrationStopsFutureCallbacksWithoutRollingBackTheCommittedAction() = runTest {
        val surface = surface()
        var callbacks = 0
        var nativeCalls = 0
        val registration = surface.installInteractionHandler(InteractionHandler { context, _ ->
            callbacks += 1
            context.request(InteractionAction.BeginWindowMove)
        }).successValue()

        surface.dispatchSynchronousInteraction(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
            nativeCalls += 1
            KadreResult.Success(Unit)
        }
        registration.close()
        surface.dispatchSynchronousInteraction(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
            nativeCalls += 1
            KadreResult.Success(Unit)
        }

        assertEquals(1, callbacks)
        assertEquals(1, nativeCalls)
    }

    @Test
    fun closingRegistrationDoesNotWaitForAnAlreadyAdmittedHandler() = runTest {
        val surface = surface()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val registration = surface.installInteractionHandler(InteractionHandler { _, _ ->
            entered.countDown()
            release.await()
        }).successValue()
        val callback = Thread {
            surface.dispatchSynchronousInteraction(pointerEvent(), emptySet()) { KadreResult.Success(Unit) }
        }.apply { isDaemon = true }.also(Thread::start)
        assertTrue(entered.await(1, TimeUnit.SECONDS))

        val close = Thread(registration::close).apply { isDaemon = true }.also(Thread::start)
        assertTrue(close.joinedWithin(200))

        release.countDown()
        callback.join()
    }

    @Test
    fun closeBeforeRequestRejectsTheNativeAction() = runTest {
        val surface = surface()
        var registration: org.graphiks.kadre.interaction.InteractionRegistration? = null
        var request: KadreResult<*>? = null
        var nativeCalls = 0
        registration = surface.installInteractionHandler(InteractionHandler { context, _ ->
            registration!!.close()
            request = context.request(InteractionAction.BeginWindowMove)
        }).successValue()

        surface.dispatchSynchronousInteraction(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
            nativeCalls += 1
            KadreResult.Success(Unit)
        }

        assertEquals(0, nativeCalls)
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Interaction)),
            request,
        )
    }

    @Test
    fun competingCallbacksKeepHandlerOutcomeAndInputStampsMonotonic() = runTest {
        val surface = surface()
        val handlerStamps = mutableListOf<Long>()
        val registration = surface.installInteractionHandler(InteractionHandler { context, event ->
            handlerStamps += event.stamp.sequence.value
            assertIs<KadreResult.Success<*>>(context.request(InteractionAction.BeginWindowMove))
        }).successValue()
        val outcomes = async(start = CoroutineStart.UNDISPATCHED) {
            registration.outcomes.take(2).toList().map { it.stamp.sequence.value }
        }
        val inputs = async(start = CoroutineStart.UNDISPATCHED) {
            surface.input.events.take(2).toList().map { it.stamp.sequence.value }
        }
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val first = Thread {
            surface.dispatchSynchronousInteraction(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
                firstEntered.countDown()
                releaseFirst.await()
                KadreResult.Success(Unit)
            }
        }.apply { isDaemon = true }
        val second = Thread {
            surface.dispatchSynchronousInteraction(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
                KadreResult.Success(Unit)
            }
        }.apply { isDaemon = true }

        first.start()
        assertTrue(firstEntered.await(1, TimeUnit.SECONDS))
        second.start()
        releaseFirst.countDown()
        first.join()
        second.join()

        assertEquals(listOf(0L, 1L), handlerStamps)
        assertEquals(listOf(0L, 1L), outcomes.await())
        assertEquals(listOf(0L, 1L), inputs.await())
    }

    @Test
    fun detachReturnsWhileHandlerCanRequestRedrawAndRejectsLaterCallbacks() = runTest {
        val surface = surface()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var callbacks = 0
        val redrawResults = mutableListOf<KadreResult<Unit>>()
        surface.installInteractionHandler(InteractionHandler { _, _ ->
            callbacks += 1
            entered.countDown()
            release.await()
            redrawResults += surface.requestRedraw()
        })
        val callback = Thread {
            surface.dispatchSynchronousInteraction(pointerEvent(), emptySet()) { KadreResult.Success(Unit) }
        }.apply { isDaemon = true }
        callback.start()
        assertTrue(entered.await(1, TimeUnit.SECONDS))

        val detach = Thread(surface::detach).apply { isDaemon = true }
        detach.start()
        assertTrue(detach.joinedWithin(200))
        release.countDown()
        callback.join()
        surface.dispatchSynchronousInteraction(pointerEvent(), emptySet()) { KadreResult.Success(Unit) }

        assertEquals(1, callbacks)
        assertEquals(KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface)), redrawResults.single())
    }

    @Test
    fun closingFromHandlerAfterACommitDrainsOutcomeThenCompletes() = runTest {
        val surface = surface()
        lateinit var registration: org.graphiks.kadre.interaction.InteractionRegistration
        registration = surface.installInteractionHandler(InteractionHandler { context, _ ->
            assertIs<KadreResult.Success<*>>(context.request(InteractionAction.BeginWindowMove))
            registration.close()
        }).successValue()
        val collected = async(start = CoroutineStart.UNDISPATCHED) { registration.outcomes.toList() }

        surface.dispatchSynchronousInteraction(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
            KadreResult.Success(Unit)
        }

        assertIs<InteractionActionOutcome.Committed>(collected.await().single())
    }

    @Test
    fun retainedContextIsWrongSurfaceDuringAnotherSurfaceCallback() = runTest {
        val first = surface()
        val second = surface(id = SurfaceId(6L))
        var retained: InteractionContext? = null
        var result: KadreResult<*>? = null
        first.installInteractionHandler(InteractionHandler { context, _ ->
            retained = context
            second.dispatchSynchronousInteraction(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
                KadreResult.Success(Unit)
            }
        })
        second.installInteractionHandler(InteractionHandler { _, _ ->
            result = retained!!.request(InteractionAction.BeginWindowMove)
        })

        first.dispatchSynchronousInteraction(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
            KadreResult.Success(Unit)
        }

        assertEquals(
            KadreResult.Failure(KadreFailure.InteractionRequired(InteractionFailureReason.WrongSurface)),
            result,
        )
    }

    @Test
    fun emptyHandlerCapabilityIsNormalisedToUnsupported() = runTest {
        val surface = surface(interactions = EmptyAfterValidationSet())

        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.InstallInteractionHandler)),
            surface.installInteractionHandler(InteractionHandler { _, _ -> }),
        )
    }

    @Test
    fun runtimePointerPayloadPublishesPressureOnceInOrdinaryInput() = runTest {
        val surface = surface()
        var callbacks = 0
        surface.installInteractionHandler(InteractionHandler { _, _ -> callbacks += 1 })
        val input = async(start = CoroutineStart.UNDISPATCHED) { surface.input.events.take(1).toList() }

        surface.dispatchSynchronousInteraction(
            RuntimeSynchronousInteraction.PointerPressed(
                button = PointerButton.Primary,
                position = LogicalPoint(10.0, 20.0),
                pressure = 0.75,
            ),
            emptySet(),
        ) { KadreResult.Success(Unit) }

        val ordinary = assertIs<InputEvent.PointerButtonChanged>(input.await().single())
        assertEquals(1, callbacks)
        assertEquals(0.75, ordinary.pressure)
    }

    @Test
    fun closeSourceOutcomeOverflowFailsCurrentAndFutureCollectorsAndReleasesRegistration() = runTest {
        val surface = surface(deliveryPolicy = outcomePolicy(CollectorOverflowAction.CloseSource))
        val registration = surface.installInteractionHandler(moveHandler()).successValue()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val terminal = async<KadreException?>(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            try {
                registration.outcomes.collect {
                    entered.countDown()
                    release.await()
                }
                null
            } catch (error: KadreException) {
                error
            }
        }

        repeat(3) {
            surface.dispatchSynchronousInteraction(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
                KadreResult.Success(Unit)
            }
            if (it == 0) assertTrue(entered.await(1, TimeUnit.SECONDS))
        }
        release.countDown()

        assertEquals(
            KadreFailure.SourceOverflow(KadreResourceKind.Interaction),
            terminal.await()!!.failure,
        )
        val future = async<KadreException?> {
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
        assertIs<KadreResult.Success<*>>(surface.installInteractionHandler(InteractionHandler { _, _ -> }))
    }

    @Test
    fun failSessionOutcomeOverflowReportsOnceAndReleasesRegistration() = runTest {
        val sessionFailures = mutableListOf<KadreFailure>()
        val surface = surface(
            deliveryPolicy = outcomePolicy(CollectorOverflowAction.FailSession),
            sessionFailureHandler = sessionFailures::add,
        )
        val registration = surface.installInteractionHandler(moveHandler()).successValue()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val terminal = async<KadreException?>(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            try {
                registration.outcomes.collect {
                    entered.countDown()
                    release.await()
                }
                null
            } catch (error: KadreException) {
                error
            }
        }

        repeat(3) {
            surface.dispatchSynchronousInteraction(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
                KadreResult.Success(Unit)
            }
            if (it == 0) assertTrue(entered.await(1, TimeUnit.SECONDS))
        }
        release.countDown()

        assertEquals(
            KadreFailure.SourceOverflow(KadreResourceKind.Interaction),
            terminal.await()!!.failure,
        )
        val future = async<KadreException?> {
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
        assertEquals<List<KadreFailure>>(
            listOf(KadreFailure.SourceOverflow(KadreResourceKind.Interaction)),
            sessionFailures,
        )
        assertIs<KadreResult.Success<*>>(surface.installInteractionHandler(InteractionHandler { _, _ -> }))
    }

    @Test
    fun collectorDisposedAfterTheOutcomeSnapshotCannotOverflowTheRegistration() = runTest {
        val snapshot = CountDownLatch(1)
        val releaseOffer = CountDownLatch(1)
        val handler = RuntimeInteractionHandler(
            surfaceId = SurfaceId(5L),
            advertised = setOf(InteractionKind.BeginWindowMove),
            deliveryPolicy = outcomePolicy(CollectorOverflowAction.CloseSource),
            eventCollectorGate = RuntimeEventCollectorAllocator(4).newGate(4),
            failureReporter = RuntimeFailureReporter { },
            sessionFailureHandler = {},
            afterOutcomeSubscriberSnapshot = {
                snapshot.countDown()
                releaseOffer.await()
            },
        )
        val registration = handler.install(moveHandler()).successValue()
        val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            registration.outcomes.collect { }
        }
        val callback = Thread {
            handler.dispatch(pointerEvent(), setOf(InteractionKind.BeginWindowMove)) {
                KadreResult.Success(Unit)
            }
        }.apply { isDaemon = true }

        callback.start()
        assertTrue(snapshot.await(1, TimeUnit.SECONDS))
        collector.cancelAndJoin()
        releaseOffer.countDown()
        callback.join()

        assertEquals(
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Interaction)),
            handler.install(InteractionHandler { _, _ -> }),
        )
    }

    @Test
    fun handlerExceptionsAreReportedToTheSessionAndCannotEscapeDispatch() = runTest {
        val sessionFailures = mutableListOf<KadreFailure>()
        val reports = mutableListOf<Throwable>()
        val surface = surface(
            failureReporter = RuntimeFailureReporter(reports::add),
            sessionFailureHandler = sessionFailures::add,
        )
        surface.installInteractionHandler(InteractionHandler { _, _ -> error("boom") })

        surface.dispatchSynchronousInteraction(pointerEvent(), emptySet()) { KadreResult.Success(Unit) }

        assertEquals<List<KadreFailure>>(listOf(KadreFailure.ApplicationFailure), sessionFailures)
        assertEquals("boom", reports.single().message)
    }

    @Test
    fun armInteractionRemainsUnsupported() = runTest {
        val result = surface().armInteraction(
            InteractionAction.BeginWindowMove,
            org.graphiks.kadre.interaction.InteractionArmOptions(1.nanoseconds),
        )

        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.ArmInteraction)),
            result,
        )
    }

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

    private fun surface(
        id: SurfaceId = SurfaceId(5L),
        interactions: Set<InteractionKind> = setOf(InteractionKind.BeginWindowMove),
        deliveryPolicy: WindowDeliveryPolicy = KadrePolicies.Default.window,
        failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { },
        sessionFailureHandler: (KadreFailure) -> Unit = {},
    ): RuntimeWindowSurface = RuntimeWindowSurface(
        id = id,
        initialSnapshot = SurfaceInitialSnapshot(
            metrics = SurfaceMetrics(
                logicalSize = LogicalSize(640.0, 360.0),
                physicalSize = PhysicalSize(640, 360),
                scaleFactor = 1.0,
                safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
            ),
            focus = SurfaceFocus.Unfocused,
            visibility = SurfaceVisibility.Visible,
            occlusion = SurfaceOcclusion.Unknown,
            theme = SurfaceTheme.Unknown,
        ),
        commandPort = UnsupportedSurfaceCommandPort,
        commandsEnabled = true,
        enabledCapabilities = SurfaceCapabilities(
            cursor = supported(CursorIcon.entries.toSet()),
            customCursor = unsupported(KadreOperation.UpdateSurface),
            pointerCapture = unsupported(KadreOperation.UpdateSurface),
            hitTesting = supported(HitTestingMode.entries.toSet()),
            inputDefaultBehavior = supported(InputDefaultBehavior.entries.toSet()),
            handlerInteractions = supported(interactions),
            armedInteractions = unsupported(KadreOperation.ArmInteraction),
            platformAccess = unsupported(KadreOperation.PlatformSurfaceAccess),
        ),
        eventStampSource = StampSource()::next,
        deliveryPolicy = deliveryPolicy,
        failureReporter = failureReporter,
        sessionFailureHandler = sessionFailureHandler,
    )

    private fun pointerEvent(): InteractionEvent.PointerPressed = InteractionEvent.PointerPressed(
        PointerButton.Primary,
        LogicalPoint(10.0, 20.0),
        EventStamp(SessionSequence(99), SessionInstant(99.nanoseconds), null),
    )

    private fun <T> supported(constraints: T): Capability<T> =
        Capability.Supported(constraints, FeatureAvailability.Available)

    private fun <T> unsupported(operation: KadreOperation): Capability<T> =
        Capability.Unsupported(KadreFailure.Unsupported(operation))

    private fun <T> KadreResult<T>.successValue(): T = when (this) {
        is KadreResult.Success -> value
        is KadreResult.Failure -> error("expected success, got $reason")
    }

    private fun <T> KadreResult<T>.failureValue(): KadreFailure = when (this) {
        is KadreResult.Success -> error("expected failure, got $value")
        is KadreResult.Failure -> reason
    }

    private fun Thread.joinedWithin(timeoutMillis: Long): Boolean {
        join(timeoutMillis)
        return !isAlive
    }

    private class StampSource {
        private var sequence = 0L

        fun next(): EventStamp = EventStamp(
            SessionSequence(sequence),
            SessionInstant(sequence++.nanoseconds),
            null,
        )
    }

    private class EmptyAfterValidationSet : Set<InteractionKind> {
        private var initiallyValid = true

        override val size: Int
            get() = if (initiallyValid) 1 else 0

        override fun contains(element: InteractionKind): Boolean = initiallyValid && element == InteractionKind.BeginWindowMove

        override fun containsAll(elements: Collection<InteractionKind>): Boolean = elements.all(::contains)

        override fun isEmpty(): Boolean = if (initiallyValid) {
            initiallyValid = false
            false
        } else {
            true
        }

        override fun iterator(): Iterator<InteractionKind> =
            if (initiallyValid) listOf(InteractionKind.BeginWindowMove).iterator() else emptySet<InteractionKind>().iterator()
    }
}
