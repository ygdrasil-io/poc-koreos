package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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

    private fun surface(
        failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { },
        sessionFailureHandler: (KadreFailure) -> Unit = {},
    ): RuntimeWindowSurface = RuntimeWindowSurface(
        id = SurfaceId(5L),
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
            handlerInteractions = supported(setOf(InteractionKind.BeginWindowMove)),
            armedInteractions = unsupported(KadreOperation.ArmInteraction),
            platformAccess = unsupported(KadreOperation.PlatformSurfaceAccess),
        ),
        eventStampSource = StampSource()::next,
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
}
