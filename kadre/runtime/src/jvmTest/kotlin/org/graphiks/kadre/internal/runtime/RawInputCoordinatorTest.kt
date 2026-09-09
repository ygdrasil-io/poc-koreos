package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreDiagnostic
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.RawInputEvent
import org.graphiks.kadre.input.RawInputState
import org.graphiks.kadre.input.RawInputUnit
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.RawInputDeliveryPolicy
import org.graphiks.kadre.policy.RawInputOverflowAction
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.nanoseconds

@OptIn(DelicateKadreApi::class, ExperimentalCoroutinesApi::class)
class RawInputCoordinatorTest {
    @Test
    fun independentLeasesReceiveTheSameEventAndClosingOneDoesNotCloseTheOther() = runTest {
        val port = FakeRawInputPort()
        val coordinator = coordinator(port, scope = backgroundScope)
        val first = coordinator.requestAccess().requireValue()
        val second = coordinator.requestAccess().requireValue()
        val firstEvents = async { first.events.take(1).toList() }
        val secondEvents = async { second.events.take(2).toList() }
        runCurrent()

        val event = rawEvent(1.0)
        port.leaseAt(0).emit(RawInputPortLeaseEvent.Input(portInput(1.0)))
        port.leaseAt(1).emit(RawInputPortLeaseEvent.Input(portInput(1.0)))
        runCurrent()

        first.close()
        port.leaseAt(1).emit(RawInputPortLeaseEvent.Input(portInput(2.0)))
        runCurrent()

        assertEquals(listOf(event), firstEvents.await())
        assertEquals(listOf(event, rawEvent(2.0)), secondEvents.await())
        assertTrue(port.leaseAt(0).closed)
        assertTrue(!port.leaseAt(1).closed)
    }

    @Test
    fun accessSubscribesToItsLeaseBeforeItIsReturned() = runTest {
        val port = FakeRawInputPort()
        val access = coordinator(port, scope = backgroundScope).requestAccess().requireValue()

        assertTrue(port.leaseAt(0).collectionStarted.isCompleted)
        access.close()
    }

    @Test
    fun sessionBudgetIsRejectedBeforeAnotherNativeAdmissionAndReleasedByClose() = runTest {
        val port = FakeRawInputPort()
        val coordinator = coordinator(
            port = port,
            maxConcurrentRawInputAccesses = 1,
            scope = backgroundScope,
        )
        val first = coordinator.requestAccess().requireValue()

        val rejected = assertIs<KadreResult.Failure>(coordinator.requestAccess())

        assertEquals(
            KadreFailure.ResourceLimitExceeded(KadreResourceKind.RawInputAccess, 1),
            rejected.reason,
        )
        assertEquals(1, port.requestCount)

        first.close()

        assertIs<KadreResult.Success<*>>(coordinator.requestAccess())
        assertEquals(2, port.requestCount)
    }

    @Test
    fun dropOldestIsDiagnosedPerAccessAndKeepsTheMostRecentEvent() = runTest {
        val diagnostics = mutableListOf<KadreDiagnostic>()
        val port = FakeRawInputPort()
        val access = coordinator(
            port = port,
            rawPolicy = RawInputDeliveryPolicy(1, RawInputOverflowAction.DropOldestAndReport),
            diagnostics = diagnostics::add,
            scope = backgroundScope,
        ).requestAccess().requireValue()
        val releaseCollector = CompletableDeferred<Unit>()
        val received = mutableListOf<RawInputEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            access.events.collect { event ->
                received += event
                if (received.size == 1) releaseCollector.await()
            }
        }
        val first = rawEvent(1.0)
        val second = rawEvent(2.0)
        val third = rawEvent(3.0)

        port.leaseAt(0).emit(RawInputPortLeaseEvent.Input(portInput(1.0)))
        runCurrent()
        port.leaseAt(0).emit(RawInputPortLeaseEvent.Input(portInput(2.0)))
        port.leaseAt(0).emit(RawInputPortLeaseEvent.Input(portInput(3.0)))
        runCurrent()
        releaseCollector.complete(Unit)
        runCurrent()

        assertEquals(listOf(first, third), received)
        assertEquals(
            listOf(KadreResourceKind.RawInputAccess),
            diagnostics.filterIsInstance<KadreDiagnostic.EventLoss>().map(KadreDiagnostic.EventLoss::resource),
        )
        collector.cancel()
    }

    @Test
    fun closeAccessOverflowFailsOnlyThatAccessAndPreservesAnotherLease() = runTest {
        val port = FakeRawInputPort()
        val coordinator = coordinator(
            port = port,
            rawPolicy = RawInputDeliveryPolicy(1, RawInputOverflowAction.CloseAccess),
            scope = backgroundScope,
        )
        val closing = coordinator.requestAccess().requireValue()
        val surviving = coordinator.requestAccess().requireValue()
        val releaseCollector = CompletableDeferred<Unit>()
        val failed = async {
            runCatching { closing.events.first { releaseCollector.await(); false } }.exceptionOrNull()
        }
        runCurrent()

        port.leaseAt(0).emit(RawInputPortLeaseEvent.Input(portInput(1.0)))
        runCurrent()
        port.leaseAt(0).emit(RawInputPortLeaseEvent.Input(portInput(2.0)))
        port.leaseAt(0).emit(RawInputPortLeaseEvent.Input(portInput(3.0)))
        runCurrent()

        assertEquals(RawInputState.Closed, closing.state.value)
        releaseCollector.complete(Unit)
        assertEquals(
            KadreFailure.SourceOverflow(KadreResourceKind.RawInputAccess),
            assertIs<KadreException>(failed.await()).failure,
        )
        assertEquals(RawInputState.Active, surviving.state.value)
        assertTrue(port.leaseAt(0).closed)
        assertTrue(!port.leaseAt(1).closed)
    }

    @Test
    fun recoverableAvailabilitySuspendsAndReactivatesAnExistingAccess() = runTest {
        val port = FakeRawInputPort()
        val access = coordinator(port, scope = backgroundScope).requestAccess().requireValue()
        val interrupted = KadreFailure.TemporarilyUnavailable(retryable = true)

        port.leaseAt(0).emit(RawInputPortLeaseEvent.Availability(FeatureAvailability.Unavailable(interrupted)))
        runCurrent()

        assertEquals(RawInputState.Suspended(interrupted), access.state.value)

        port.leaseAt(0).emit(RawInputPortLeaseEvent.Availability(FeatureAvailability.Available))
        runCurrent()

        assertEquals(RawInputState.Active, access.state.value)
    }

    @Test
    fun availabilityIsPublishedBeforeTheAccessObservesItsSuspension() = runTest {
        val port = FakeRawInputPort()
        lateinit var access: org.graphiks.kadre.input.RawInputAccess
        var stateAtAvailabilityPublication: RawInputState? = null
        val coordinator = coordinator(
            port = port,
            scope = backgroundScope,
            onAvailability = { stateAtAvailabilityPublication = access.state.value },
        )
        access = coordinator.requestAccess().requireValue()
        val interrupted = KadreFailure.TemporarilyUnavailable(retryable = true)

        port.leaseAt(0).emit(RawInputPortLeaseEvent.Availability(FeatureAvailability.Unavailable(interrupted)))
        runCurrent()

        assertEquals(RawInputState.Active, stateAtAvailabilityPublication)
        assertEquals(RawInputState.Suspended(interrupted), access.state.value)
    }

    @Test
    fun cancellationBeforeHandoffReleasesTheBudgetWithoutClosingTheSharedPort() = runTest {
        val port = FakeRawInputPort(blockFirstRequest = true)
        val coordinator = coordinator(port, maxConcurrentRawInputAccesses = 1, scope = backgroundScope)
        val pending = async { coordinator.requestAccess() }
        port.firstRequestStarted.await()

        pending.cancel()
        advanceUntilIdle()
        port.unblockFirstRequest()
        advanceUntilIdle()

        assertTrue(pending.isCancelled)
        assertIs<KadreResult.Success<*>>(coordinator.requestAccess())
        assertEquals(2, port.requestCount)
        assertTrue(!port.closed)
    }

    @Test
    fun cancellationAfterNativeRegistrationClosesOnlyThatLease() = runTest {
        val port = FakeRawInputPort(blockFirstRequest = true, ignoreFirstCancellation = true)
        val coordinator = coordinator(port, maxConcurrentRawInputAccesses = 1, scope = backgroundScope)
        val pending = async { coordinator.requestAccess() }
        port.firstRequestStarted.await()

        pending.cancel()
        port.unblockFirstRequest()
        advanceUntilIdle()

        assertTrue(pending.isCancelled)
        assertTrue(port.leaseAt(0).closed)
        assertTrue(!port.closed)
        assertIs<KadreResult.Success<*>>(coordinator.requestAccess())
    }

    @Test
    fun coordinatorCloseClosesEveryDescendantNormallyBeforeThePort() = runTest {
        val port = FakeRawInputPort()
        val coordinator = coordinator(port, scope = backgroundScope)
        val first = coordinator.requestAccess().requireValue()
        val second = coordinator.requestAccess().requireValue()

        coordinator.close()

        assertEquals(RawInputState.Closed, first.state.value)
        assertEquals(RawInputState.Closed, second.state.value)
        assertTrue(port.leaseAt(0).closed)
        assertTrue(port.leaseAt(1).closed)
        assertTrue(port.closed)
    }

    @Test
    fun closingOneSurfaceClosesOnlyItsRawAccesses() = runTest {
        val port = FakeRawInputPort()
        val coordinator = coordinator(port, scope = backgroundScope)
        val first = coordinator.requestAccess(SurfaceId(1)).requireValue()
        val second = coordinator.requestAccess(SurfaceId(2)).requireValue()

        coordinator.closeOwner(SurfaceId(1))
        port.leaseAt(0).emit(RawInputPortLeaseEvent.Input(portInput(7.0)))
        runCurrent()

        assertEquals(RawInputState.Closed, first.state.value)
        assertEquals(RawInputState.Active, second.state.value)
        assertTrue(port.leaseAt(0).closed)
        assertTrue(!port.leaseAt(1).closed)
    }

    @Test
    fun closedSurfaceOwnerIsRejectedBeforeNativeAdmission() = runTest {
        val port = FakeRawInputPort()
        val coordinator = coordinator(port, scope = backgroundScope)

        coordinator.closeOwner(SurfaceId(1))
        val result = assertIs<KadreResult.Failure>(coordinator.requestAccess(SurfaceId(1)))

        assertEquals(KadreFailure.Closed(KadreResourceKind.InputSource), result.reason)
        assertEquals(0, port.requestCount)
    }

    @Test
    fun surfaceOwnsItsRawAccessAndRawEventsDoNotEnterOrdinaryInputState() = runTest {
        val port = FakeRawInputPort()
        val coordinator = coordinator(port, scope = backgroundScope)
        val surface = RuntimeWindowSurface(
            id = SurfaceId(99),
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
            rawInputCoordinator = coordinator,
            commandsEnabled = false,
            enabledCapabilities = unsupportedSurfaceCapabilities(),
            eventStampSource = { rawEvent(0.0).stamp },
        )
        val ordinaryState = surface.input.state.value
        val access = surface.input.requestRawInput().requireValue()

        port.leaseAt(0).emit(RawInputPortLeaseEvent.Input(portInput(5.0)))
        runCurrent()

        assertEquals(ordinaryState, surface.input.state.value)
        surface.detach()
        assertEquals(RawInputState.Closed, access.state.value)
        assertTrue(port.leaseAt(0).closed)
        val afterDetach = assertIs<KadreResult.Failure>(surface.input.requestRawInput())
        assertEquals(KadreFailure.Closed(KadreResourceKind.InputSource), afterDetach.reason)
        assertEquals(1, port.requestCount)
    }

    private fun coordinator(
        port: FakeRawInputPort,
        rawPolicy: RawInputDeliveryPolicy = KadrePolicies.Default.input.rawInput,
        maxConcurrentRawInputAccesses: Int = KadrePolicies.Default.resources.maxConcurrentRawInputAccesses,
        diagnostics: (KadreDiagnostic) -> Unit = {},
        onAvailability: (FeatureAvailability) -> Unit = {},
        scope: CoroutineScope,
    ): RawInputCoordinator = RawInputCoordinator(
        port = port,
        rawPolicy = rawPolicy,
        maxConcurrentRawInputAccesses = maxConcurrentRawInputAccesses,
        scope = scope,
        diagnostics = diagnostics,
        onAvailability = onAvailability,
        diagnosticStampSource = { rawEvent(0.0).stamp },
        collectorAllocator = RuntimeEventCollectorAllocator(32),
        maxCollectorsPerFlow = 8,
    )

    private fun portInput(delta: Double): RawInputPortInput = RawInputPortInput(
        deltaX = delta,
        deltaY = -delta,
        unit = RawInputUnit.DeviceCount,
        deviceId = null,
    )

    private fun rawEvent(delta: Double): RawInputEvent = RawInputEvent(
        deltaX = delta,
        deltaY = -delta,
        unit = RawInputUnit.DeviceCount,
        deviceId = null,
        stamp = EventStamp(SessionSequence(0L), SessionInstant(0L.nanoseconds), null),
    )

    private class FakeRawInputPort(
        private val blockFirstRequest: Boolean = false,
        private val ignoreFirstCancellation: Boolean = false,
    ) : RawInputPort {
        private val firstRequestRelease = CompletableDeferred<Unit>()
        val firstRequestStarted = CompletableDeferred<Unit>()
        val leases = mutableListOf<FakeRawInputPortLease>()
        var requestCount = 0
            private set
        var closed = false
            private set

        override suspend fun requestAccess(): KadreResult<RawInputPortLease> {
            requestCount += 1
            if (blockFirstRequest && requestCount == 1) {
                firstRequestStarted.complete(Unit)
                if (ignoreFirstCancellation) {
                    withContext(NonCancellable) { firstRequestRelease.await() }
                } else {
                    firstRequestRelease.await()
                }
            }
            return KadreResult.Success(FakeRawInputPortLease().also(leases::add))
        }

        fun unblockFirstRequest() {
            firstRequestRelease.complete(Unit)
        }

        fun leaseAt(index: Int): FakeRawInputPortLease = leases[index]

        override fun close() {
            closed = true
        }
    }

    private class FakeRawInputPortLease : RawInputPortLease {
        private val eventChannel = Channel<RawInputPortLeaseEvent>(capacity = 16)
        val collectionStarted = CompletableDeferred<Unit>()
        var closed = false
            private set

        override val events = kotlinx.coroutines.flow.flow {
            collectionStarted.complete(Unit)
            eventChannel.receiveAsFlow().collect(::emit)
        }

        fun emit(event: RawInputPortLeaseEvent) {
            check(eventChannel.trySend(event).isSuccess)
        }

        override fun close() {
            closed = true
        }
    }

    private fun <T> KadreResult<T>.requireValue(): T = assertIs<KadreResult.Success<T>>(this).value
}
