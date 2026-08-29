package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.KadrePermission
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.ContinuousDelivery
import org.graphiks.kadre.policy.ContinuousOverflowAction
import org.graphiks.kadre.policy.IngressOverflowAction
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.window.Window
import org.graphiks.kadre.window.WindowAttention
import org.graphiks.kadre.window.WindowCancellationOutcome
import org.graphiks.kadre.window.WindowCloseDecision
import org.graphiks.kadre.window.WindowCloseOutcome
import org.graphiks.kadre.window.WindowCloseRequestId
import org.graphiks.kadre.window.WindowCloseResponseOutcome
import org.graphiks.kadre.window.WindowCreationMode
import org.graphiks.kadre.window.WindowEvent
import org.graphiks.kadre.window.WindowPhase
import org.graphiks.kadre.window.WindowProperty
import org.graphiks.kadre.window.WindowRequest
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowRequestState
import org.graphiks.kadre.window.WindowRevision
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kadre.window.WindowState
import org.graphiks.kadre.window.WindowUpdate
import org.graphiks.kadre.window.WindowUpdateOutcome
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import java.util.concurrent.CountDownLatch

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeWindowManagerTest {
    @Test
    fun windowIngressSerializesConcurrentPublicationsAndEnforcesDiscreteAdmission() = runTest {
        val policy = KadrePolicies.Default.window.copy(
            geometryChanges = ContinuousDelivery.Coalesced,
            discreteEvents = KadrePolicies.Default.window.discreteEvents.copy(
                ingressCapacity = 2,
                collectorCapacity = 4,
                ingressOverflow = IngressOverflowAction.CloseSource,
            ),
        )
        val statePort = DeterministicWindowCommandPort()
        val stateManager = manager(statePort)
        val base = commit(
            stateManager.requestWindow(WindowSpec(contentSize = LogicalSize(100.0, 100.0))).successValue(),
            statePort.openCommands.single(),
        ).state.value
        val ordered = RuntimeWindowEventFlow(
            policy = policy,
            eventCollectorGate = RuntimeEventCollectorAllocator(4).newGate(4),
            failureReporter = RuntimeFailureReporter { },
            sessionFailureHandler = {},
            closeWindow = {},
        )
        val received = mutableListOf<WindowEvent>()
        val collector = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            ordered.events.onEach { event ->
                received += event
                if (event.stamp.sequence == SessionSequence(1L)) {
                    val geometryReady = CountDownLatch(1)
                    val barrierPublished = CountDownLatch(1)
                    val geometryBeforeBarrier = Thread {
                        geometryReady.countDown()
                        barrierPublished.await()
                        ordered.publish(
                            WindowEvent.GeometryChanged(
                                base.copy(
                                    contentSize = LogicalSize(120.0, 100.0),
                                    revision = WindowRevision(2L),
                                ),
                                operationId = null,
                                stamp = EventStamp(SessionSequence(2L), SessionInstant(2.nanoseconds), null),
                            ),
                        )
                    }
                    val barrierThenGeometry = Thread {
                        geometryReady.await()
                        ordered.publish(
                            WindowEvent.PropertiesChanged(
                                base.copy(revision = WindowRevision(3L)),
                                changed = setOf(WindowProperty.Resizable),
                                operationId = null,
                                stamp = EventStamp(SessionSequence(3L), SessionInstant(3.nanoseconds), null),
                            ),
                        )
                        barrierPublished.countDown()
                        ordered.publish(
                            WindowEvent.GeometryChanged(
                                base.copy(
                                    contentSize = LogicalSize(140.0, 100.0),
                                    revision = WindowRevision(4L),
                                ),
                                operationId = null,
                                stamp = EventStamp(SessionSequence(4L), SessionInstant(4.nanoseconds), null),
                            ),
                        )
                    }
                    geometryBeforeBarrier.start()
                    barrierThenGeometry.start()
                    geometryBeforeBarrier.join()
                    barrierThenGeometry.join()
                }
            }.toList()
        }

        ordered.publish(
            WindowEvent.GeometryChanged(
                base.copy(revision = WindowRevision(1L)),
                operationId = null,
                stamp = EventStamp(SessionSequence(1L), SessionInstant(1.nanoseconds), null),
            ),
        )
        advanceUntilIdle()
        ordered.close()
        collector.await()

        assertEquals(listOf(1L, 2L, 3L, 4L), received.map { it.stamp.sequence.value })
        assertEquals(
            listOf(
                LogicalSize(100.0, 100.0),
                LogicalSize(120.0, 100.0),
                null,
                LogicalSize(140.0, 100.0),
            ),
            received.map { (it as? WindowEvent.GeometryChanged)?.state?.contentSize },
        )

        var closedWindows = 0
        val overflow = RuntimeWindowEventFlow(
            policy = policy.copy(
                discreteEvents = policy.discreteEvents.copy(ingressCapacity = 1),
            ),
            eventCollectorGate = RuntimeEventCollectorAllocator(4).newGate(4),
            failureReporter = RuntimeFailureReporter { },
            sessionFailureHandler = {},
            closeWindow = { closedWindows += 1 },
        )
        val overflowCollector = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                overflow.events.onEach { event ->
                    if (event is WindowEvent.GeometryChanged) {
                        overflow.publish(
                            WindowEvent.PropertiesChanged(
                                base.copy(revision = WindowRevision(2L)),
                                changed = setOf(WindowProperty.Resizable),
                                operationId = null,
                                stamp = EventStamp(SessionSequence(2L), SessionInstant(2.nanoseconds), null),
                            ),
                        )
                        overflow.publish(
                            WindowEvent.PropertiesChanged(
                                base.copy(revision = WindowRevision(3L)),
                                changed = setOf(WindowProperty.Resizable),
                                operationId = null,
                                stamp = EventStamp(SessionSequence(3L), SessionInstant(3.nanoseconds), null),
                            ),
                        )
                    }
                }.toList()
            }.exceptionOrNull()
        }
        overflow.publish(
            WindowEvent.GeometryChanged(
                base.copy(revision = WindowRevision(1L)),
                operationId = null,
                stamp = EventStamp(SessionSequence(1L), SessionInstant(1.nanoseconds), null),
            ),
        )
        advanceUntilIdle()
        overflow.close()

        assertEquals(1, closedWindows)
        assertEquals(
            KadreFailure.SourceOverflow(KadreResourceKind.Window),
            assertIs<KadreException>(overflowCollector.await()).failure,
        )
    }

    @Test
    fun externalWindowObservationSanitizesUnsupportedFieldsAndOwnsItsRevision() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port)
        installWindowEventPolicy(manager, KadrePolicies.Default.window)
        val window = commit(
            manager.requestWindow(
                WindowSpec(
                    title = "runtime-title",
                    contentSize = LogicalSize(100.0, 100.0),
                    minimumSize = LogicalSize(50.0, 50.0),
                    maximumSize = LogicalSize(200.0, 200.0),
                ),
            ).successValue(),
            port.openCommands.single(),
        ) as RuntimeWindow
        val before = window.state.value
        val native = before.copy(
            title = "native-title",
            outerBounds = PhysicalRect(PhysicalPoint(9, 11), PhysicalSize(300, 240)),
            contentSize = LogicalSize(120.0, 100.0),
            minimumSize = LogicalSize(60.0, 50.0),
            maximumSize = LogicalSize(180.0, 200.0),
            resizable = false,
            revision = WindowRevision(900L),
        )

        assertTrue(window.observeNativeUpdate(native))

        assertEquals(
            before.copy(
                contentSize = LogicalSize(120.0, 100.0),
                minimumSize = LogicalSize(60.0, 50.0),
                maximumSize = LogicalSize(180.0, 200.0),
                resizable = false,
                revision = WindowRevision(1L),
            ),
            window.state.value,
        )
    }

    @Test
    fun repeatedExternalWindowObservationIsANoOp() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port)
        installWindowEventPolicy(manager, KadrePolicies.Default.window)
        val window = commit(
            manager.requestWindow(WindowSpec(contentSize = LogicalSize(100.0, 100.0))).successValue(),
            port.openCommands.single(),
        ) as RuntimeWindow
        val events = mutableListOf<WindowEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { window.events.collect(events::add) }
        val before = window.state.value
        val repeatedNativeSnapshot = before.copy(
            title = "ignored-native-title",
            outerBounds = PhysicalRect(PhysicalPoint(4, 6), PhysicalSize(240, 180)),
            revision = WindowRevision(700L),
        )

        assertFalse(window.observeNativeUpdate(repeatedNativeSnapshot))
        advanceUntilIdle()

        assertEquals(before, window.state.value)
        assertEquals(emptyList(), events)
        collector.cancelAndJoin()
    }

    @Test
    fun windowGeometryEventsFollowConfiguredDeliveryPolicy() = runTest {
        val coalescedPort = DeterministicWindowCommandPort()
        val coalescedManager = manager(coalescedPort)
        val coalescedPolicy = KadrePolicies.Default.window.copy(
            geometryChanges = ContinuousDelivery.Coalesced,
        )
        installWindowEventPolicy(coalescedManager, coalescedPolicy)
        val coalescedWindow = commit(
            coalescedManager.requestWindow(WindowSpec(contentSize = LogicalSize(100.0, 100.0))).successValue(),
            coalescedPort.openCommands.single(),
        )
        val firstGeometryDelivered = CompletableDeferred<Unit>()
        val releaseCollector = CompletableDeferred<Unit>()
        val coalescedEvents = mutableListOf<WindowEvent.GeometryChanged>()
        val coalescedCollector = async(start = CoroutineStart.UNDISPATCHED) {
            coalescedWindow.events
                .filterIsInstance<WindowEvent.GeometryChanged>()
                .onEach { event ->
                    coalescedEvents += event
                    if (!firstGeometryDelivered.isCompleted) {
                        firstGeometryDelivered.complete(Unit)
                        releaseCollector.await()
                    }
                }
                .toList()
        }

        applyContentSize(coalescedWindow, coalescedPort, LogicalSize(110.0, 100.0))
        firstGeometryDelivered.await()
        applyContentSize(coalescedWindow, coalescedPort, LogicalSize(120.0, 100.0))
        applyContentSize(coalescedWindow, coalescedPort, LogicalSize(130.0, 100.0))
        releaseCollector.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            listOf(LogicalSize(110.0, 100.0), LogicalSize(130.0, 100.0)),
            coalescedEvents.map { it.state.contentSize },
        )
        coalescedCollector.cancelAndJoin()

        val failWindowPort = DeterministicWindowCommandPort()
        val failWindowManager = manager(failWindowPort)
        val failWindowPolicy = KadrePolicies.Default.window.copy(
            geometryChanges = ContinuousDelivery.Buffered(1, ContinuousOverflowAction.CloseSource),
        )
        installWindowEventPolicy(failWindowManager, failWindowPolicy)
        val failWindow = commit(
            failWindowManager.requestWindow(WindowSpec(contentSize = LogicalSize(100.0, 100.0))).successValue(),
            failWindowPort.openCommands.single(),
        )
        val failWindowEntered = CompletableDeferred<Unit>()
        val failWindowRelease = CompletableDeferred<Unit>()
        val failWindowCollector = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                failWindow.events
                    .filterIsInstance<WindowEvent.GeometryChanged>()
                    .onEach {
                        if (!failWindowEntered.isCompleted) {
                            failWindowEntered.complete(Unit)
                            failWindowRelease.await()
                        }
                    }
                    .toList()
            }.exceptionOrNull()
        }

        applyContentSize(failWindow, failWindowPort, LogicalSize(110.0, 100.0))
        failWindowEntered.await()
        applyContentSize(failWindow, failWindowPort, LogicalSize(120.0, 100.0))
        applyContentSize(failWindow, failWindowPort, LogicalSize(130.0, 100.0))
        failWindowRelease.complete(Unit)

        assertEquals(WindowPhase.Closed, failWindow.state.value.phase)
        assertEquals(
            KadreFailure.SourceOverflow(KadreResourceKind.Window),
            assertIs<KadreException>(failWindowCollector.await()).failure,
        )

        val failSessionPort = DeterministicWindowCommandPort()
        val failSessionManager = manager(failSessionPort)
        val sessionFailures = mutableListOf<KadreFailure>()
        val failSessionPolicy = KadrePolicies.Default.window.copy(
            geometryChanges = ContinuousDelivery.Buffered(1, ContinuousOverflowAction.FailSession),
        )
        installWindowEventPolicy(failSessionManager, failSessionPolicy, sessionFailures::add)
        val failSessionWindow = commit(
            failSessionManager.requestWindow(WindowSpec(contentSize = LogicalSize(100.0, 100.0))).successValue(),
            failSessionPort.openCommands.single(),
        )
        val failSessionEntered = CompletableDeferred<Unit>()
        val failSessionRelease = CompletableDeferred<Unit>()
        val failSessionCollector = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                failSessionWindow.events
                    .filterIsInstance<WindowEvent.GeometryChanged>()
                    .onEach {
                        if (!failSessionEntered.isCompleted) {
                            failSessionEntered.complete(Unit)
                            failSessionRelease.await()
                        }
                    }
                    .toList()
            }.exceptionOrNull()
        }

        applyContentSize(failSessionWindow, failSessionPort, LogicalSize(110.0, 100.0))
        failSessionEntered.await()
        applyContentSize(failSessionWindow, failSessionPort, LogicalSize(120.0, 100.0))
        applyContentSize(failSessionWindow, failSessionPort, LogicalSize(130.0, 100.0))
        failSessionRelease.complete(Unit)

        val expected: KadreFailure = KadreFailure.SourceOverflow(KadreResourceKind.Window)
        assertEquals(listOf(expected), sessionFailures)
        assertEquals(expected, assertIs<KadreException>(failSessionCollector.await()).failure)
    }

    @Test
    fun windowUpdatePublishesStateBeforeCorrelatedGeometryAndPropertiesEvents() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port)
        installWindowEventPolicy(manager, KadrePolicies.Default.window)
        val window = commit(
            manager.requestWindow(WindowSpec(contentSize = LogicalSize(100.0, 100.0))).successValue(),
            port.openCommands.single(),
        )
        val observedStates = mutableListOf<WindowState>()
        val events = mutableListOf<WindowEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            window.events.onEach { event ->
                observedStates += window.state.value
                events += event
            }.collect()
        }
        val update = async(start = CoroutineStart.UNDISPATCHED) {
            window.apply(
                WindowUpdate(
                    contentSize = PropertyChange.Set(LogicalSize(120.0, 100.0)),
                    resizable = PropertyChange.Set(false),
                ),
            )
        }
        val command = port.updateCommands.single()
        val effective = window.state.value.copy(
            contentSize = LogicalSize(120.0, 100.0),
            resizable = false,
            revision = WindowRevision(1L),
        )

        command.applied(effective)

        val outcome = assertIs<WindowUpdateOutcome.Applied>(update.await().successValue())
        assertEquals(effective, outcome.state)
        assertEquals(effective, window.state.value)
        assertEquals(listOf(effective, effective), observedStates)
        val geometry = assertIs<WindowEvent.GeometryChanged>(events[0])
        val properties = assertIs<WindowEvent.PropertiesChanged>(events[1])
        assertEquals(command.operationId, geometry.operationId)
        assertEquals(command.operationId, properties.operationId)
        assertEquals(effective.revision, geometry.stateRevision)
        assertEquals(effective.revision, properties.stateRevision)
        assertTrue(geometry.stamp.sequence.value < properties.stamp.sequence.value)

        val external = effective.copy(
            contentSize = LogicalSize(140.0, 100.0),
            revision = WindowRevision(2L),
        )
        assertTrue((window as RuntimeWindow).observeNativeUpdate(external))
        advanceUntilIdle()
        val externalGeometry = assertIs<WindowEvent.GeometryChanged>(events[2])
        assertEquals(external, observedStates[2])
        assertEquals(null, externalGeometry.operationId)
        assertEquals(external, externalGeometry.state)
        collector.cancelAndJoin()
    }

    @Test
    fun windowUpdateValidatesCombinedSizeConstraintsBeforeDispatch() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port)
        val request = manager.requestWindow(
            WindowSpec(
                contentSize = LogicalSize(100.0, 100.0),
                minimumSize = LogicalSize(50.0, 50.0),
                maximumSize = LogicalSize(200.0, 200.0),
            ),
        ).successValue()
        val window = commit(request, port.openCommands.single())
        val initial = window.state.value
        val events = mutableListOf<WindowEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { window.events.collect(events::add) }

        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("sizeConstraints")),
            window.apply(WindowUpdate(contentSize = PropertyChange.Set(LogicalSize(40.0, 100.0)))),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("sizeConstraints")),
            window.apply(WindowUpdate(minimumSize = PropertyChange.Set(LogicalSize(150.0, 50.0)))),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("sizeConstraints")),
            window.apply(WindowUpdate(maximumSize = PropertyChange.Set(LogicalSize(40.0, 200.0)))),
        )

        assertEquals(emptyList<WindowUpdateCommand>(), port.updateCommands)
        assertEquals(initial, window.state.value)
        assertEquals(emptyList(), events)
        collector.cancelAndJoin()
    }

    @Test
    fun clearOfInitialSizeConstraintsDispatchesAndPublishesNullDefaults() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port)
        val request = manager.requestWindow(
            WindowSpec(
                contentSize = LogicalSize(100.0, 100.0),
                minimumSize = LogicalSize(50.0, 50.0),
                maximumSize = LogicalSize(200.0, 200.0),
            ),
        ).successValue()
        val window = commit(request, port.openCommands.single())
        val update = async(start = CoroutineStart.UNDISPATCHED) {
            window.apply(
                WindowUpdate(
                    minimumSize = PropertyChange.Clear,
                    maximumSize = PropertyChange.Clear,
                ),
            )
        }

        val command = port.updateCommands.single()
        assertEquals(PropertyChange.Clear, command.update.minimumSize)
        assertEquals(PropertyChange.Clear, command.update.maximumSize)
        command.applied(
            window.state.value.copy(
                minimumSize = null,
                maximumSize = null,
            ),
        )

        val outcome = assertIs<WindowUpdateOutcome.Applied>(update.await().successValue())
        assertEquals(null, outcome.state.minimumSize)
        assertEquals(null, outcome.state.maximumSize)
        assertEquals(1L, outcome.state.revision.value)
    }

    @Test
    fun clearIsRejectedForContentSizeAndResizableBeforeNativeDispatch() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port)
        val window = commit(
            manager.requestWindow(WindowSpec()).successValue(),
            port.openCommands.single(),
        )

        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("contentSize")),
            window.apply(WindowUpdate(contentSize = PropertyChange.Clear)),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("resizable")),
            window.apply(WindowUpdate(resizable = PropertyChange.Clear)),
        )
        assertEquals(emptyList(), port.updateCommands)
    }

    @Test
    fun windowUpdatesSerializePerWindowAndRevalidateExpectedRevisionAtDispatch() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port)
        val first = commit(
            manager.requestWindow(WindowSpec(contentSize = LogicalSize(100.0, 100.0))).successValue(),
            port.openCommands.single(),
        )
        val second = commit(
            manager.requestWindow(WindowSpec(contentSize = LogicalSize(100.0, 100.0))).successValue(),
            port.openCommands.last(),
        )

        val firstUpdate = async(start = CoroutineStart.UNDISPATCHED) {
            first.apply(WindowUpdate(contentSize = PropertyChange.Set(LogicalSize(120.0, 100.0))))
        }
        val staleUpdate = async(start = CoroutineStart.UNDISPATCHED) {
            first.apply(
                WindowUpdate(
                    contentSize = PropertyChange.Set(LogicalSize(130.0, 100.0)),
                    expectedRevision = WindowRevision(0L),
                ),
            )
        }
        val otherWindowUpdate = async(start = CoroutineStart.UNDISPATCHED) {
            second.apply(WindowUpdate(contentSize = PropertyChange.Set(LogicalSize(140.0, 100.0))))
        }

        assertEquals(2, port.updateCommands.size)
        val firstCommand = port.updateCommands.single { it.windowId == first.id }
        firstCommand.applied(first.state.value.copy(
            contentSize = LogicalSize(120.0, 100.0),
            revision = WindowRevision(1L),
        ))

        assertIs<KadreResult.Success<WindowUpdateOutcome.Applied>>(firstUpdate.await())
        assertEquals(
            KadreResult.Failure(KadreFailure.StaleRevision(expected = 0L, received = 1L)),
            staleUpdate.await(),
        )
        assertEquals(2, port.updateCommands.size)
        port.updateCommands.single { it.windowId == second.id }.applied(second.state.value.copy(
            contentSize = LogicalSize(140.0, 100.0),
            revision = WindowRevision(1L),
        ))
        assertIs<KadreResult.Success<WindowUpdateOutcome.Applied>>(otherWindowUpdate.await())
    }

    @Test
    fun queuedWindowUpdateRevalidatesCombinedConstraintsAgainstTheDispatchedState() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port)
        val request = manager.requestWindow(
            WindowSpec(
                contentSize = LogicalSize(100.0, 100.0),
                minimumSize = LogicalSize(50.0, 50.0),
                maximumSize = LogicalSize(200.0, 200.0),
            ),
        ).successValue()
        val window = commit(request, port.openCommands.single())

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            window.apply(
                WindowUpdate(
                    contentSize = PropertyChange.Set(LogicalSize(150.0, 100.0)),
                    minimumSize = PropertyChange.Set(LogicalSize(150.0, 50.0)),
                ),
            )
        }
        val laterInvalid = async(start = CoroutineStart.UNDISPATCHED) {
            window.apply(WindowUpdate(maximumSize = PropertyChange.Set(LogicalSize(120.0, 200.0))))
        }

        assertEquals(1, port.updateCommands.size)
        port.updateCommands.single().applied(window.state.value.copy(
            contentSize = LogicalSize(150.0, 100.0),
            minimumSize = LogicalSize(150.0, 50.0),
            revision = WindowRevision(1L),
        ))

        assertIs<KadreResult.Success<WindowUpdateOutcome.Applied>>(first.await())
        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("sizeConstraints")),
            laterInvalid.await(),
        )
        assertEquals(1, port.updateCommands.size)
    }

    @Test
    fun delayedNativeUpdateCannotRollbackTheWindowLifecyclePhase() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port)
        val request = manager.requestWindow(WindowSpec(contentSize = LogicalSize(100.0, 100.0))).successValue()
        val openCommand = port.openCommands.single()
        val window = commit(request, openCommand)
        val update = async(start = CoroutineStart.UNDISPATCHED) {
            window.apply(WindowUpdate(contentSize = PropertyChange.Set(LogicalSize(120.0, 100.0))))
        }
        val updateCommand = port.updateCommands.single()

        assertIs<KadreResult.Success<WindowCloseOutcome.Accepted>>(window.close())
        openCommand.nativeClosed()
        updateCommand.applied(window.state.value.copy(
            phase = WindowPhase.Open,
            contentSize = LogicalSize(120.0, 100.0),
            revision = WindowRevision(1L),
        ))

        assertEquals(WindowPhase.Closed, window.state.value.phase)
        assertEquals(LogicalSize(120.0, 100.0), window.state.value.contentSize)
        assertEquals(WindowPhase.Closed, assertIs<KadreResult.Success<WindowUpdateOutcome.Applied>>(update.await()).value.state.phase)
    }

    @Test
    fun postCommitWindowUpdatePublishesItsCorrelatedEventBeforeClosedEventsTerminate() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port)
        installWindowEventPolicy(manager, KadrePolicies.Default.window)
        val request = manager.requestWindow(WindowSpec(contentSize = LogicalSize(100.0, 100.0))).successValue()
        val openCommand = port.openCommands.single()
        val window = commit(request, openCommand)
        val events = async(start = CoroutineStart.UNDISPATCHED) { window.events.toList() }
        val update = async(start = CoroutineStart.UNDISPATCHED) {
            window.apply(WindowUpdate(contentSize = PropertyChange.Set(LogicalSize(120.0, 100.0))))
        }
        val updateCommand = port.updateCommands.single()

        assertIs<KadreResult.Success<WindowCloseOutcome.Accepted>>(window.close())
        openCommand.nativeClosed()
        advanceUntilIdle()

        assertEquals(WindowPhase.Closed, window.state.value.phase)
        assertFalse(events.isCompleted, "post-commit event delivery must outlive native close")

        updateCommand.applied(
            window.state.value.copy(
                phase = WindowPhase.Open,
                contentSize = LogicalSize(120.0, 100.0),
            ),
        )
        val outcome = assertIs<WindowUpdateOutcome.Applied>(update.await().successValue())
        val delivered = events.await()
        val geometry = assertIs<WindowEvent.GeometryChanged>(delivered.last())

        assertEquals(WindowPhase.Closed, outcome.state.phase)
        assertEquals(outcome.state, window.state.value)
        assertEquals(updateCommand.operationId, geometry.operationId)
        assertEquals(outcome.state, geometry.state)
    }

    @Test
    fun closeBetweenNativeUpdateAdmissionAndGeometryPublicationDeliversTheCorrelatedEvent() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port)
        var sequence = 0L
        var closeDuringGeometryPublication = false
        lateinit var openCommand: WindowOpenCommand
        installWindowEventPolicy(
            manager = manager,
            policy = KadrePolicies.Default.window,
            eventStampSource = {
                EventStamp(
                    SessionSequence(sequence),
                    SessionInstant((sequence++).nanoseconds),
                    deliverySpan = null,
                ).also {
                    if (closeDuringGeometryPublication) {
                        closeDuringGeometryPublication = false
                        openCommand.nativeClosed()
                    }
                }
            },
        )
        val request = manager.requestWindow(WindowSpec(contentSize = LogicalSize(100.0, 100.0))).successValue()
        openCommand = port.openCommands.single()
        val window = commit(request, openCommand)
        val events = async(start = CoroutineStart.UNDISPATCHED) { window.events.toList() }
        val update = async(start = CoroutineStart.UNDISPATCHED) {
            window.apply(WindowUpdate(contentSize = PropertyChange.Set(LogicalSize(120.0, 100.0))))
        }
        val updateCommand = port.updateCommands.single()

        assertIs<KadreResult.Success<WindowCloseOutcome.Accepted>>(window.close())
        closeDuringGeometryPublication = true
        updateCommand.applied(
            window.state.value.copy(
                phase = WindowPhase.Open,
                contentSize = LogicalSize(120.0, 100.0),
            ),
        )

        val outcome = assertIs<WindowUpdateOutcome.Applied>(update.await().successValue())
        val delivered = events.await()
        val geometry = assertIs<WindowEvent.GeometryChanged>(delivered.last())

        assertEquals(WindowPhase.Closing, outcome.state.phase)
        assertEquals(WindowPhase.Closed, window.state.value.phase)
        assertEquals(LogicalSize(120.0, 100.0), window.state.value.contentSize)
        assertEquals(updateCommand.operationId, geometry.operationId)
        assertEquals(outcome.state, geometry.state)
    }

    @Test
    fun windowUpdateCancellationAndCloseRespectTheNativeCommitBoundary() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port)
        val request = manager.requestWindow(WindowSpec(contentSize = LogicalSize(100.0, 100.0))).successValue()
        val openCommand = port.openCommands.single()
        val window = commit(request, openCommand)

        port.updateCancellationOutcome = WindowUpdateCancellationOutcome.CancelledBeforeCommit
        val preCommitCaller = async(start = CoroutineStart.UNDISPATCHED) {
            window.apply(WindowUpdate(contentSize = PropertyChange.Set(LogicalSize(110.0, 100.0))))
        }
        val preCommitCommand = port.updateCommands.single()
        preCommitCaller.cancelAndJoin()
        assertEquals(1, port.updateCancellationCommands.size)
        preCommitCommand.applied(window.state.value.copy(
            contentSize = LogicalSize(110.0, 100.0),
            revision = WindowRevision(1L),
        ))
        assertEquals(LogicalSize(100.0, 100.0), window.state.value.contentSize)

        port.updateCancellationOutcome = WindowUpdateCancellationOutcome.TooLate
        val committedCaller = async(start = CoroutineStart.UNDISPATCHED) {
            window.apply(WindowUpdate(contentSize = PropertyChange.Set(LogicalSize(120.0, 100.0))))
        }
        val committedCommand = port.updateCommands.last()
        committedCaller.cancel()
        committedCommand.applied(window.state.value.copy(
            contentSize = LogicalSize(120.0, 100.0),
            revision = WindowRevision(1L),
        ))
        assertTrue(committedCaller.isCancelled)
        assertEquals(LogicalSize(120.0, 100.0), window.state.value.contentSize)

        val blockingCaller = async(start = CoroutineStart.UNDISPATCHED) {
            window.apply(WindowUpdate(contentSize = PropertyChange.Set(LogicalSize(130.0, 100.0))))
        }
        val withdrawnCaller = async(start = CoroutineStart.UNDISPATCHED) {
            window.apply(WindowUpdate(contentSize = PropertyChange.Set(LogicalSize(140.0, 100.0))))
        }
        assertEquals(3, port.updateCommands.size)
        withdrawnCaller.cancelAndJoin()
        port.updateCommands.last().applied(window.state.value.copy(
            contentSize = LogicalSize(130.0, 100.0),
            revision = WindowRevision(2L),
        ))
        assertIs<KadreResult.Success<WindowUpdateOutcome.Applied>>(blockingCaller.await())
        assertTrue(withdrawnCaller.isCancelled)
        assertEquals(3, port.updateCommands.size)

        assertIs<KadreResult.Success<WindowCloseOutcome.Accepted>>(window.close())
        openCommand.nativeClosed()
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window)),
            window.apply(WindowUpdate(contentSize = PropertyChange.Set(LogicalSize(150.0, 100.0)))),
        )
    }

    @Test
    fun pendingRequestConsumesOnlyThePendingBudgetAndStaysOutOfTheWindowSnapshot() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port, maxWindows = 2, maxPending = 1)

        val first = manager.requestWindow(WindowSpec(title = "first")).successValue()
        val second = manager.requestWindow(WindowSpec(title = "second"))

        assertEquals(WindowRequestState.Pending, first.state.value)
        assertEquals(emptyList(), manager.state.value.windows)
        assertEquals(
            KadreResult.Failure(KadreFailure.ResourceLimitExceeded(KadreResourceKind.WindowRequest, 1)),
            second,
        )
        assertEquals(listOf("first"), port.openCommands.map { it.spec.title })
        assertIs<Capability.Unsupported>(manager.state.value.capabilities.requestWindow)
    }

    @Test
    fun cancellationBeforeCommitCreatesNoWindowAndReleasesBothReservations() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port, maxWindows = 1, maxPending = 1)
        val request = manager.requestWindow(WindowSpec(title = "cancelled")).successValue()

        assertEquals(WindowCancellationOutcome.CancelledBeforeCommit, request.cancel())

        assertEquals(WindowRequestState.Terminated(WindowRequestOutcome.Cancelled), request.state.value)
        assertEquals(WindowRequestOutcome.Cancelled, request.await())
        assertEquals(emptyList(), manager.state.value.windows)
        assertEquals(1, port.pendingCancellationCommands.size)

        val replacement = manager.requestWindow(WindowSpec(title = "replacement"))
        assertIs<KadreResult.Success<WindowRequest>>(replacement)
        assertEquals(2, port.openCommands.size)
    }

    @Test
    fun commitPublishesTheExactWindowBeforeItsOpenedHereOutcome() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port)
        val request = manager.requestWindow(WindowSpec(title = "committed")).successValue()
        val observedPublication = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            val terminal = assertIs<WindowRequestState.Terminated>(request.state.drop(1).first())
            val window = assertIs<WindowRequestOutcome.OpenedHere>(terminal.outcome).window
            manager.state.value.windows.single() === window
        }

        val owner = CountingWindowPeerOwner()
        port.openCommands.single().commit(owner)
        val outcome = assertIs<WindowRequestOutcome.OpenedHere>(request.await())

        assertTrue(observedPublication.await())
        assertSame(outcome.window, manager.state.value.windows.single())
        assertSame(outcome.window, manager.state.value.primary)
        assertEquals("committed", outcome.window.state.value.title)
        assertEquals(0, owner.closeCount)
    }

    @Test
    fun admittedSurfaceIdentityRoutesOnlyToItsLiveRuntimeSurface() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port)
        val request = manager.requestWindow(WindowSpec(title = "surface-routing")).successValue()
        val command = port.openCommands.single()
        val window = commit(request, command)

        assertEquals(command.surfaceId, window.surface.id)
        assertTrue(
            manager.acceptSurfaceStimulus(
                SurfaceStimulus.FocusChanged(command.surfaceId, SurfaceFocus.Focused),
            ),
        )
        assertEquals(SurfaceFocus.Focused, window.surface.state.value.focus)

        window.close()
        command.nativeClosed()

        assertEquals(SurfaceAttachmentState.Detached, window.surface.state.value.attachment)
        assertFalse(
            manager.acceptSurfaceStimulus(
                SurfaceStimulus.FocusChanged(command.surfaceId, SurfaceFocus.Unfocused),
            ),
        )
        assertEquals(SurfaceFocus.Focused, window.surface.state.value.focus)
    }

    @Test
    fun sessionInputPolicyControlsTheIngressOfEveryCommittedSurface() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port)
        val inputPolicy = KadrePolicies.Default.input.copy(
            discreteEvents = KadrePolicies.Default.input.discreteEvents.copy(
                ingressCapacity = 1,
                ingressOverflow = org.graphiks.kadre.policy.IngressOverflowAction.CloseSource,
            ),
        )
        var sequence = 0L
        manager.installSessionConfiguration(
            deliveryPolicy = KadrePolicies.Default.window,
            inputDeliveryPolicy = inputPolicy,
            source = {
                EventStamp(
                    SessionSequence(sequence),
                    SessionInstant((sequence++).nanoseconds),
                    deliverySpan = null,
                )
            },
            sessionFailureHandler = {},
            collectorAllocator = RuntimeEventCollectorAllocator(4),
            maxCollectorsPerFlow = 4,
        )
        val request = manager.requestWindow(WindowSpec(title = "input-policy")).successValue()
        val window = commit(request, port.openCommands.single())
        var injected = false
        val terminal = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                window.surface.input.events
                    .onEach { event ->
                        if (!injected && event is InputEvent.Key) {
                            injected = true
                            manager.acceptSurfaceStimulus(inputKey(window.surface.id, "session-policy-one"))
                            manager.acceptSurfaceStimulus(inputKey(window.surface.id, "session-policy-two"))
                        }
                    }
                    .toList()
            }.exceptionOrNull()
        }

        assertTrue(manager.acceptSurfaceStimulus(inputKey(window.surface.id, "session-policy-root")))

        assertEquals(
            KadreFailure.SourceOverflow(KadreResourceKind.InputSource),
            assertIs<KadreException>(terminal.await()).failure,
        )
    }

    @Test
    fun inputStimuliRemainIsolatedToTheCommittedSurfaceThatOwnsTheirIdentity() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port)
        val firstRequest = manager.requestWindow(WindowSpec(title = "input-first")).successValue()
        val secondRequest = manager.requestWindow(WindowSpec(title = "input-second")).successValue()
        val first = commit(firstRequest, port.openCommands[0])
        val second = commit(secondRequest, port.openCommands[1])
        val event = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            first.surface.input.events.first()
        }

        assertTrue(manager.acceptSurfaceStimulus(inputKey(first.surface.id, "surface-one")))

        assertIs<InputEvent.Key>(event.await())
        assertEquals(1L, first.surface.input.state.value.revision.value)
        assertEquals(0L, second.surface.input.state.value.revision.value)
        assertFalse(
            manager.acceptSurfaceStimulus(
                inputKey(org.graphiks.kadre.surface.SurfaceId(9_999L), "unknown-surface"),
            ),
        )
    }

    @Test
    fun windowSurfaceAndInputCollectorsShareTheInjectedSessionAllocator() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port)
        manager.installSessionConfiguration(
            deliveryPolicy = KadrePolicies.Default.window,
            inputDeliveryPolicy = KadrePolicies.Default.input,
            source = { error("event stamps are not used by this collector test") },
            sessionFailureHandler = {},
            collectorAllocator = RuntimeEventCollectorAllocator(1),
            maxCollectorsPerFlow = 1,
        )
        val request = manager.requestWindow(WindowSpec(title = "collector-budget")).successValue()
        val window = commit(request, port.openCommands.single())

        val windowCollector = launch(start = CoroutineStart.UNDISPATCHED) { window.events.collect() }
        val rejectedInput = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { window.surface.input.events.collect() }.exceptionOrNull()
        }
        try {
            assertTrue(rejectedInput.isCompleted)
            val rejection = assertIs<KadreException>(rejectedInput.await())
            assertEquals(
                KadreFailure.ResourceLimitExceeded(KadreResourceKind.EventCollector, 1),
                rejection.failure,
            )
        } finally {
            rejectedInput.cancelAndJoin()
            windowCollector.cancelAndJoin()
        }

        val inputCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            window.surface.input.events.collect()
        }
        val rejectedSurface = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { window.surface.events.collect() }.exceptionOrNull()
        }
        try {
            assertTrue(rejectedSurface.isCompleted)
            val rejection = assertIs<KadreException>(rejectedSurface.await())
            assertEquals(
                KadreFailure.ResourceLimitExceeded(KadreResourceKind.EventCollector, 1),
                rejection.failure,
            )
        } finally {
            rejectedSurface.cancelAndJoin()
            inputCollector.cancelAndJoin()
        }
    }

    @Test
    fun openCommitCarriesAtomicInitialMetricsAcrossPreAndPostCommitStimulusRaces() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port, publicSurfaceCapabilities = true)
        val request = manager.requestWindow(
            WindowSpec(title = "native-metrics", contentSize = LogicalSize(700.0, 400.0)),
        ).successValue()
        val command = port.openCommands.single()
        val preCommitMetrics = SurfaceMetrics(
            logicalSize = LogicalSize(710.0, 405.0),
            physicalSize = PhysicalSize(1420, 810),
            scaleFactor = 2.0,
            safeAreaInsets = LogicalInsets(1.0, 2.0, 3.0, 4.0),
        )
        val committedMetrics = SurfaceMetrics(
            logicalSize = LogicalSize(720.25, 410.5),
            physicalSize = PhysicalSize(1441, 821),
            scaleFactor = 2.0,
            safeAreaInsets = LogicalInsets(5.0, 6.0, 7.0, 8.0),
        )
        val postCommitMetrics = SurfaceMetrics(
            logicalSize = LogicalSize(730.25, 420.5),
            physicalSize = PhysicalSize(1461, 841),
            scaleFactor = 2.0,
            safeAreaInsets = LogicalInsets(9.0, 10.0, 11.0, 12.0),
        )

        assertFalse(
            manager.acceptSurfaceStimulus(
                SurfaceStimulus.MetricsChanged(command.surfaceId, preCommitMetrics),
            ),
        )
        command.commit(
            CountingWindowPeerOwner(),
            initialSurfaceSnapshot = SurfaceInitialSnapshot(
                metrics = committedMetrics,
                focus = SurfaceFocus.Focused,
                visibility = SurfaceVisibility.Hidden,
                occlusion = SurfaceOcclusion.Occluded,
                theme = SurfaceTheme.Dark,
            ),
        )
        val window = assertIs<WindowRequestOutcome.OpenedHere>(request.await()).window

        assertEquals(committedMetrics.logicalSize, window.surface.state.value.logicalSize)
        assertEquals(committedMetrics.physicalSize, window.surface.state.value.physicalSize)
        assertEquals(committedMetrics.scaleFactor, window.surface.state.value.scaleFactor)
        assertEquals(committedMetrics.safeAreaInsets, window.surface.state.value.safeAreaInsets)
        assertEquals(SurfaceFocus.Focused, window.surface.state.value.focus)
        assertEquals(SurfaceVisibility.Hidden, window.surface.state.value.visibility)
        assertEquals(SurfaceOcclusion.Occluded, window.surface.state.value.occlusion)
        assertEquals(SurfaceTheme.Dark, window.surface.state.value.theme)
        assertEquals(SurfaceRevision(0), window.surface.state.value.revision)

        assertTrue(
            manager.acceptSurfaceStimulus(
                SurfaceStimulus.MetricsChanged(command.surfaceId, postCommitMetrics),
            ),
        )
        assertEquals(postCommitMetrics.logicalSize, window.surface.state.value.logicalSize)
        assertEquals(postCommitMetrics.physicalSize, window.surface.state.value.physicalSize)
        assertEquals(SurfaceRevision(1), window.surface.state.value.revision)
    }

    @Test
    fun phaseThreeCommitWithoutNativeInitialSnapshotCannotExposeAnOpenedWindow() = runTest {
        val reported = mutableListOf<Throwable>()
        val port = DeterministicWindowCommandPort()
        val manager = manager(
            port,
            reported = reported,
            publicSurfaceCapabilities = true,
        )
        val request = manager.requestWindow(WindowSpec(title = "missing-metrics")).successValue()
        val owner = CountingWindowPeerOwner()

        port.openCommands.single().commit(owner)

        val rejected = assertIs<WindowRequestOutcome.Rejected>(request.await())
        assertEquals(
            KadreFailure.PlatformFailure(
                KadrePlatform.Fake,
                "window-command-port",
                "missing-initial-surface-snapshot",
            ),
            rejected.failure,
        )
        assertEquals(emptyList(), manager.state.value.windows)
        assertEquals(1, owner.closeCount)
        assertIs<KadreException>(reported.single())
    }

    @Test
    fun windowSlotsAreReservedAtAdmissionBeforeNativeCommit() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port, maxWindows = 1, maxPending = 2)

        manager.requestWindow(WindowSpec(title = "reserved")).successValue()
        val saturated = manager.requestWindow(WindowSpec(title = "must-not-open"))

        assertEquals(
            KadreResult.Failure(KadreFailure.ResourceLimitExceeded(KadreResourceKind.Window, 1)),
            saturated,
        )
        assertEquals(listOf("reserved"), port.openCommands.map { it.spec.title })
    }

    @Test
    fun cancellationAndFailureReleaseAWindowSlotExactlyOnce() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port, maxWindows = 1, maxPending = 2)
        val cancelled = manager.requestWindow(WindowSpec(title = "cancelled")).successValue()

        assertEquals(WindowCancellationOutcome.CancelledBeforeCommit, cancelled.cancel())
        assertEquals(
            WindowCancellationOutcome.AlreadyTerminated(WindowRequestOutcome.Cancelled),
            cancelled.cancel(),
        )

        val rejected = manager.requestWindow(WindowSpec(title = "rejected")).successValue()
        val nativeFailure = KadreFailure.TemporarilyUnavailable(retryable = true)
        port.openCommands.last().fail(nativeFailure)
        assertEquals(WindowRequestOutcome.Rejected(nativeFailure), rejected.await())

        val replacement = manager.requestWindow(WindowSpec(title = "replacement"))
        assertIs<KadreResult.Success<WindowRequest>>(replacement)
        assertEquals(
            KadreResult.Failure(KadreFailure.ResourceLimitExceeded(KadreResourceKind.Window, 1)),
            manager.requestWindow(WindowSpec(title = "still-saturated")),
        )
    }

    @Test
    fun committedSlotIsRetainedUntilOneTerminalNativeClose() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port, maxWindows = 1, maxPending = 2)
        val request = manager.requestWindow(WindowSpec(title = "committed")).successValue()
        val command = port.openCommands.single()
        val owner = CountingWindowPeerOwner()
        val window = commit(request, command, owner)

        assertEquals(
            KadreResult.Failure(KadreFailure.ResourceLimitExceeded(KadreResourceKind.Window, 1)),
            manager.requestWindow(WindowSpec(title = "blocked")),
        )
        assertIs<KadreResult.Success<WindowCloseOutcome>>(window.close())
        assertEquals(WindowPhase.Closing, window.state.value.phase)
        assertEquals(SurfaceAttachmentState.Detached, window.surface.state.value.attachment)

        command.nativeClosed()
        command.nativeClosed()

        assertEquals(WindowPhase.Closed, window.state.value.phase)
        assertEquals(1, owner.closeCount)
        assertEquals(emptyList(), manager.state.value.windows)
        assertIs<KadreResult.Success<WindowRequest>>(
            manager.requestWindow(WindowSpec(title = "after-close")),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.ResourceLimitExceeded(KadreResourceKind.Window, 1)),
            manager.requestWindow(WindowSpec(title = "slot-was-not-double-released")),
        )
    }

    @Test
    fun committedWindowsKeepAdmissionOrderAcrossOutOfOrderCommitsAndPrimaryClosure() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port, maxWindows = 3, maxPending = 3)
        val firstRequest = manager.requestWindow(WindowSpec(title = "first")).successValue()
        val secondRequest = manager.requestWindow(WindowSpec(title = "second")).successValue()
        val thirdRequest = manager.requestWindow(WindowSpec(title = "third")).successValue()

        val second = commit(secondRequest, port.openCommands[1])
        val third = commit(thirdRequest, port.openCommands[2])
        val first = commit(firstRequest, port.openCommands[0])

        assertEquals(listOf(first, second, third), manager.state.value.windows)
        assertSame(first, manager.state.value.primary)

        second.close()
        port.openCommands[1].nativeClosed()
        assertEquals(listOf(first, third), manager.state.value.windows)
        assertSame(first, manager.state.value.primary)

        first.close()
        port.openCommands[0].nativeClosed()
        assertEquals(listOf(third), manager.state.value.windows)
        assertSame(third, manager.state.value.primary)
    }

    @Test
    fun closingARequestDetachesPendingOwnershipButNeverClosesAnOpenedWindow() = runTest {
        val port = DeterministicWindowCommandPort().apply {
            pendingCancellationOutcome = PendingWindowCancellationOutcome.CancellationRequested
        }
        val manager = manager(port, maxWindows = 2, maxPending = 2)
        val detached = manager.requestWindow(WindowSpec(title = "detached")).successValue()
        val detachedCommand = port.openCommands.single()

        detached.close()

        assertEquals(
            WindowRequestState.Terminated(WindowRequestOutcome.RequesterDetached),
            detached.state.value,
        )
        val lateOwner = CountingWindowPeerOwner()
        detachedCommand.commit(lateOwner)
        assertEquals(1, lateOwner.closeCount)
        assertEquals(emptyList(), manager.state.value.windows)

        port.pendingCancellationOutcome = PendingWindowCancellationOutcome.CancelledBeforeCommit
        val openedRequest = manager.requestWindow(WindowSpec(title = "opened")).successValue()
        val owner = CountingWindowPeerOwner()
        val openedWindow = commit(openedRequest, port.openCommands.last(), owner)

        openedRequest.close()

        assertEquals(WindowPhase.Open, openedWindow.state.value.phase)
        assertEquals(0, owner.closeCount)
        assertSame(openedWindow, manager.state.value.windows.single())
    }

    @Test
    fun synchronousCommitDuringPendingRequestCloseIsLateAndNeverPublishesAWindow() = runTest {
        val port = DeterministicWindowCommandPort().apply {
            pendingCancellationOutcome = PendingWindowCancellationOutcome.CancellationRequested
        }
        val manager = manager(port, maxWindows = 1, maxPending = 1)
        val request = manager.requestWindow(WindowSpec(title = "close-race")).successValue()
        val command = port.openCommands.single()
        val owner = CountingWindowPeerOwner()
        port.onPendingCancellation = { command.commit(owner) }

        request.close()

        assertEquals(WindowRequestOutcome.RequesterDetached, request.await())
        assertEquals(emptyList(), manager.state.value.windows)
        assertEquals(0L, manager.state.value.revision.value)
        assertEquals(1, owner.closeCount)
        assertIs<KadreResult.Success<WindowRequest>>(
            manager.requestWindow(WindowSpec(title = "replacement")),
        )
    }

    @Test
    fun tooLateCancellationLeavesTheLaterCommitAuthoritative() = runTest {
        val port = DeterministicWindowCommandPort().apply {
            pendingCancellationOutcome = PendingWindowCancellationOutcome.TooLate
        }
        val manager = manager(port)
        val request = manager.requestWindow(WindowSpec(title = "too-late")).successValue()

        assertEquals(WindowCancellationOutcome.TooLate, request.cancel())
        assertEquals(WindowRequestState.Pending, request.state.value)

        val owner = CountingWindowPeerOwner()
        val window = commit(request, port.openCommands.single(), owner)

        assertEquals(
            WindowCancellationOutcome.AlreadyTerminated(WindowRequestOutcome.OpenedHere(window)),
            request.cancel(),
        )
        assertSame(window, manager.state.value.windows.single())
        assertEquals(0, owner.closeCount)
    }

    @Test
    fun repeatedCancellationRequestDoesNotSendDuplicateNativeCloseCommands() = runTest {
        val port = DeterministicWindowCommandPort().apply {
            pendingCancellationOutcome = PendingWindowCancellationOutcome.CancellationRequested
        }
        val manager = manager(port)
        val request = manager.requestWindow(WindowSpec(title = "cancel-once")).successValue()

        assertEquals(WindowCancellationOutcome.CancellationRequested, request.cancel())
        assertEquals(WindowCancellationOutcome.CancellationRequested, request.cancel())

        assertEquals(1, port.pendingCancellationCommands.size)
        assertEquals(WindowRequestState.Pending, request.state.value)
    }

    @Test
    fun closeAfterCancellationRequestDetachesLocallyWithoutASecondPendingCommand() = runTest {
        val port = DeterministicWindowCommandPort().apply {
            pendingCancellationOutcome = PendingWindowCancellationOutcome.CancellationRequested
        }
        val manager = manager(port, maxWindows = 1, maxPending = 1)
        val request = manager.requestWindow(WindowSpec(title = "cancel-then-close")).successValue()
        val command = port.openCommands.single()

        assertEquals(WindowCancellationOutcome.CancellationRequested, request.cancel())
        request.close()

        assertEquals(WindowRequestOutcome.RequesterDetached, request.await())
        assertEquals(1, port.pendingCancellationCommands.size)
        assertIs<KadreResult.Success<WindowRequest>>(
            manager.requestWindow(WindowSpec(title = "replacement")),
        )
        val lateOwner = CountingWindowPeerOwner()
        command.commit(lateOwner)
        assertEquals(1, lateOwner.closeCount)
        assertEquals(emptyList(), manager.state.value.windows)
    }

    @Test
    fun callerCancellationBeforeRequestHandoffReleasesTheAdmission() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port, maxWindows = 1, maxPending = 1)
        val callerJob = Job()
        port.onOpen = { callerJob.cancel() }

        val caller = CoroutineScope(coroutineContext + callerJob).async(start = CoroutineStart.UNDISPATCHED) {
            manager.requestWindow(WindowSpec(title = "unhanded"))
        }

        assertTrue(caller.isCancelled)
        assertIs<KadreResult.Success<WindowRequest>>(
            manager.requestWindow(WindowSpec(title = "replacement")),
        )
        val lateOwner = CountingWindowPeerOwner()
        port.openCommands.first().commit(lateOwner)
        assertEquals(1, lateOwner.closeCount)
        assertEquals(emptyList(), manager.state.value.windows)
    }

    @Test
    fun callerCancellationBeforeHandoffClosesASynchronouslyCommittedOwner() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port, maxWindows = 1, maxPending = 1)
        val callerJob = Job()
        val owner = CountingWindowPeerOwner()
        port.onOpen = { command ->
            command.commit(owner)
            callerJob.cancel()
        }

        val caller = CoroutineScope(coroutineContext + callerJob).async(start = CoroutineStart.UNDISPATCHED) {
            manager.requestWindow(WindowSpec(title = "committed-before-handoff"))
        }

        assertTrue(caller.isCancelled)
        assertEquals(emptyList(), manager.state.value.windows)
        assertEquals(0L, manager.state.value.revision.value)
        assertEquals(1, owner.closeCount)
        assertEquals(1, port.pendingCancellationCommands.size)
        assertEquals(emptyList(), port.openedCloseCommands)
        assertIs<KadreResult.Success<WindowRequest>>(
            manager.requestWindow(WindowSpec(title = "replacement")),
        )
    }

    @Test
    fun cancellationTriggeredByMembershipPublicationRevokesTheUndeliveredWindow() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port, maxWindows = 1, maxPending = 2)
        val callerJob = Job()
        val owner = CountingWindowPeerOwner()
        val publicationObserver = launch(
            UnconfinedTestDispatcher(testScheduler),
            start = CoroutineStart.UNDISPATCHED,
        ) {
            manager.state.drop(1).first { it.windows.isNotEmpty() }
            callerJob.cancel()
        }
        port.onOpen = { command -> command.commit(owner) }

        val caller = CoroutineScope(coroutineContext + callerJob).async(start = CoroutineStart.UNDISPATCHED) {
            manager.requestWindow(WindowSpec(title = "cancelled-after-resume"))
        }
        publicationObserver.join()
        caller.join()

        assertTrue(caller.isCancelled)
        assertEquals(emptyList(), manager.state.value.windows)
        assertEquals(1, owner.closeCount)
        assertEquals(1, port.openedCloseCommands.size)
        port.onOpen = {}
        assertIs<KadreResult.Success<WindowRequest>>(
            manager.requestWindow(WindowSpec(title = "replacement")),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.ResourceLimitExceeded(KadreResourceKind.Window, 1)),
            manager.requestWindow(WindowSpec(title = "slot-released-once")),
        )
    }

    @Test
    fun cancellationBeforeQueuedContinuationDeliveryReplacesOnlyTheUndeliveredOutcome() = runTest {
        val dispatcher = QueuedCoroutineDispatcher()
        val callerJob = Job()
        val port = DeterministicWindowCommandPort()
        val manager = manager(port, maxWindows = 1, maxPending = 2)
        val owner = CountingWindowPeerOwner()
        port.onOpen = { command -> command.commit(owner) }

        val caller = CoroutineScope(callerJob + dispatcher).async(start = CoroutineStart.UNDISPATCHED) {
            manager.requestWindow(WindowSpec(title = "queued-handoff"))
        }
        val requestId = port.openCommands.single().requestId
        val internalRequest = checkNotNull(manager.requestForTesting(requestId))

        assertEquals(WindowRequestState.Pending, internalRequest.state.value)
        assertTrue(dispatcher.runNext())
        assertIs<WindowRequestOutcome.OpenedHere>(internalRequest.terminalOutcome())
        assertEquals(1, manager.state.value.windows.size)
        assertEquals(1, dispatcher.queuedTaskCount)

        callerJob.cancel()
        assertTrue(dispatcher.runNext())

        assertEquals(WindowRequestOutcome.RequesterDetached, internalRequest.terminalOutcome())
        assertEquals(emptyList(), manager.state.value.windows)
        assertEquals(1, owner.closeCount)
        assertEquals(1, port.openedCloseCommands.size)
        caller.join()
        assertTrue(caller.isCancelled)

        port.onOpen = {}
        assertIs<KadreResult.Success<WindowRequest>>(
            manager.requestWindow(WindowSpec(title = "replacement")),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.ResourceLimitExceeded(KadreResourceKind.Window, 1)),
            manager.requestWindow(WindowSpec(title = "slot-released-once")),
        )
    }

    @Test
    fun invalidNativeRejectionIsNormalisedBeforeItReachesPublicState() = runTest {
        val port = DeterministicWindowCommandPort()
        val reported = mutableListOf<Throwable>()
        val manager = manager(port, maxWindows = 1, maxPending = 1, reported = reported)
        val request = manager.requestWindow(WindowSpec(title = "invalid-rejection")).successValue()
        val normalised = KadreFailure.PlatformFailure(
            KadrePlatform.Fake,
            "window-command-port",
            "invalid-rejection",
        )

        port.openCommands.single().fail(KadreFailure.PermissionDenied(KadrePermission.RawInput))

        assertEquals(WindowRequestOutcome.Rejected(normalised), request.await())
        assertEquals(normalised, assertIs<KadreException>(reported.single()).failure)
        assertIs<KadreResult.Success<WindowRequest>>(
            manager.requestWindow(WindowSpec(title = "replacement")),
        )
    }

    @Test
    fun invalidRequestRejectionAcceptsOnlyTheRequestWindowFieldAllowlist() = runTest {
        val port = DeterministicWindowCommandPort()
        val reported = mutableListOf<Throwable>()
        val manager = manager(port, maxWindows = 1, maxPending = 1, reported = reported)
        val unknownFieldRequest = manager.requestWindow(WindowSpec(title = "unknown-field")).successValue()
        val normalised = KadreFailure.PlatformFailure(
            KadrePlatform.Fake,
            "window-command-port",
            "invalid-rejection",
        )

        port.openCommands.single().fail(KadreFailure.InvalidRequest("notAWindowField"))

        assertEquals(WindowRequestOutcome.Rejected(normalised), unknownFieldRequest.await())
        assertEquals(normalised, assertIs<KadreException>(reported.single()).failure)

        val validFieldRequest = manager.requestWindow(WindowSpec(title = "valid-field")).successValue()
        val validFailure = KadreFailure.InvalidRequest("contentSize")
        port.openCommands.last().fail(validFailure)

        assertEquals(WindowRequestOutcome.Rejected(validFailure), validFieldRequest.await())
        assertEquals(1, reported.size)
    }

    @Test
    fun requestOpenExceptionTerminalisesTheRequestAndReleasesItsReservations() = runTest {
        val cause = IllegalStateException("request-open")
        val port = DeterministicWindowCommandPort().apply { onOpen = { throw cause } }
        val reported = mutableListOf<Throwable>()
        val manager = manager(port, maxWindows = 1, maxPending = 1, reported = reported)
        val expected = KadreFailure.PlatformFailure(
            KadrePlatform.Fake,
            "window-command-port",
            "request-open-exception",
        )

        val request = manager.requestWindow(WindowSpec(title = "throws")).successValue()

        assertEquals(WindowRequestOutcome.Rejected(expected), request.await())
        assertEquals(listOf<Throwable>(cause), reported)
        port.onOpen = {}
        assertIs<KadreResult.Success<WindowRequest>>(
            manager.requestWindow(WindowSpec(title = "replacement")),
        )
    }

    @Test
    fun pendingCloseExceptionCannotEscapeAndLeavesDetachedStateResourceSafe() = runTest {
        val cause = LinkageError("pending-close")
        val port = DeterministicWindowCommandPort().apply {
            onPendingCancellation = { throw cause }
        }
        val reported = mutableListOf<Throwable>()
        val manager = manager(port, maxWindows = 1, maxPending = 1, reported = reported)
        val request = manager.requestWindow(WindowSpec(title = "pending")).successValue()

        request.close()

        assertEquals(WindowRequestOutcome.RequesterDetached, request.await())
        assertEquals(emptyList(), manager.state.value.windows)
        assertEquals(listOf<Throwable>(cause), reported)
        port.onPendingCancellation = {}
        assertIs<KadreResult.Success<WindowRequest>>(
            manager.requestWindow(WindowSpec(title = "replacement")),
        )
    }

    @Test
    fun pendingCancellationExceptionDetachesAndMakesEveryLaterCallCommandFree() = runTest {
        val cause = IllegalStateException("pending-cancel")
        val port = DeterministicWindowCommandPort().apply {
            onPendingCancellation = { throw cause }
        }
        val reported = mutableListOf<Throwable>()
        val manager = manager(port, maxWindows = 1, maxPending = 1, reported = reported)
        val request = manager.requestWindow(WindowSpec(title = "pending")).successValue()
        val command = port.openCommands.single()

        assertEquals(
            WindowCancellationOutcome.AlreadyTerminated(WindowRequestOutcome.RequesterDetached),
            request.cancel(),
        )

        assertEquals(
            WindowRequestState.Terminated(WindowRequestOutcome.RequesterDetached),
            request.state.value,
        )
        assertEquals(listOf<Throwable>(cause), reported)
        assertEquals(1, port.pendingCancellationCommands.size)
        port.onPendingCancellation = {}
        assertEquals(
            WindowCancellationOutcome.AlreadyTerminated(WindowRequestOutcome.RequesterDetached),
            request.cancel(),
        )
        request.close()
        assertEquals(1, port.pendingCancellationCommands.size)
        assertIs<KadreResult.Success<WindowRequest>>(
            manager.requestWindow(WindowSpec(title = "replacement")),
        )
        val lateOwner = CountingWindowPeerOwner()
        command.commit(lateOwner)
        assertEquals(1, lateOwner.closeCount)
    }

    @Test
    fun reentrantCloseDuringThrowingCancellationDetachesWithoutASecondCommand() = runTest {
        val cause = IllegalStateException("reentrant-pending-cancel")
        val port = DeterministicWindowCommandPort()
        val reported = mutableListOf<Throwable>()
        val manager = manager(port, maxWindows = 1, maxPending = 1, reported = reported)
        lateinit var request: WindowRequest
        port.onPendingCancellation = {
            port.onPendingCancellation = {}
            request.close()
            throw cause
        }
        request = manager.requestWindow(WindowSpec(title = "reentrant-close")).successValue()

        assertEquals(
            WindowCancellationOutcome.AlreadyTerminated(WindowRequestOutcome.RequesterDetached),
            request.cancel(),
        )

        assertEquals(WindowRequestOutcome.RequesterDetached, request.await())
        assertEquals(1, port.pendingCancellationCommands.size)
        assertEquals(listOf<Throwable>(cause), reported)
        request.close()
        assertEquals(1, port.pendingCancellationCommands.size)
        assertIs<KadreResult.Success<WindowRequest>>(
            manager.requestWindow(WindowSpec(title = "replacement")),
        )
    }

    @Test
    fun openedCloseExceptionLogicallyRevokesTheWindowAndReturnsPlatformFailure() = runTest {
        val cause = IllegalStateException("opened-close")
        val port = DeterministicWindowCommandPort().apply { onOpenedClose = { throw cause } }
        val reported = mutableListOf<Throwable>()
        val manager = manager(port, maxWindows = 1, maxPending = 1, reported = reported)
        val request = manager.requestWindow(WindowSpec(title = "opened")).successValue()
        val owner = CountingWindowPeerOwner()
        val window = commit(request, port.openCommands.single(), owner)
        val expected = KadreFailure.PlatformFailure(
            KadrePlatform.Fake,
            "window-command-port",
            "opened-close-exception",
        )

        assertEquals(KadreResult.Failure(expected), window.close())

        assertEquals(WindowPhase.Closed, window.state.value.phase)
        assertEquals(SurfaceAttachmentState.Detached, window.surface.state.value.attachment)
        assertEquals(emptyList(), manager.state.value.windows)
        assertEquals(1, owner.closeCount)
        assertEquals(listOf<Throwable>(cause), reported)
        port.onOpenedClose = {}
        assertIs<KadreResult.Success<WindowRequest>>(
            manager.requestWindow(WindowSpec(title = "replacement")),
        )
    }

    @Test
    fun ownerCloseExceptionCannotEscapeNativeCallbackOrStrandTheWindowSlot() = runTest {
        val cause = IllegalStateException("owner-close")
        val port = DeterministicWindowCommandPort()
        val reported = mutableListOf<Throwable>()
        val manager = manager(port, maxWindows = 1, maxPending = 1, reported = reported)
        val request = manager.requestWindow(WindowSpec(title = "owner-throws")).successValue()
        val owner = ThrowingWindowPeerOwner(cause)
        val command = port.openCommands.single()
        val window = commit(request, command, owner)

        command.nativeClosed()

        assertEquals(WindowPhase.Closed, window.state.value.phase)
        assertEquals(emptyList(), manager.state.value.windows)
        assertEquals(1, owner.closeCount)
        assertEquals(listOf<Throwable>(cause), reported)
        assertIs<KadreResult.Success<WindowRequest>>(
            manager.requestWindow(WindowSpec(title = "replacement")),
        )
    }

    @Test
    fun synchronousNativeCloseWinsOverAnAcceptedOpenedCloseOutcome() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port)
        val request = manager.requestWindow(WindowSpec(title = "synchronous-close")).successValue()
        val command = port.openCommands.single()
        val window = commit(request, command)
        port.onOpenedClose = { command.nativeClosed() }

        assertEquals(KadreResult.Success(WindowCloseOutcome.Closed), window.close())
        assertEquals(WindowPhase.Closed, window.state.value.phase)
        assertEquals(emptyList(), manager.state.value.windows)
    }

    @Test
    fun validOpenedCloseFailureKeepsTheWindowOpenAndRetryable() = runTest {
        val port = DeterministicWindowCommandPort().apply {
            openedCloseOutcome = OpenedWindowCloseOutcome.TemporarilyUnavailable(retryable = true)
        }
        val manager = manager(port)
        val request = manager.requestWindow(WindowSpec(title = "retry-close")).successValue()
        val window = commit(request, port.openCommands.single())

        assertEquals(
            KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true)),
            window.close(),
        )
        assertEquals(WindowPhase.Open, window.state.value.phase)
        assertEquals(SurfaceAttachmentState.Attached, window.surface.state.value.attachment)
        assertSame(window, manager.state.value.windows.single())
    }

    @Test
    fun teardownContinuesInReverseAfterPortAndOwnerCleanupFailures() = runTest {
        val portCause = IllegalStateException("teardown-port")
        val ownerCause = IllegalStateException("teardown-owner")
        val port = DeterministicWindowCommandPort()
        val reported = mutableListOf<Throwable>()
        val manager = manager(port, maxWindows = 3, maxPending = 3, reported = reported)
        val firstRequest = manager.requestWindow(WindowSpec(title = "first")).successValue()
        val firstOwner = CountingWindowPeerOwner()
        val first = commit(firstRequest, port.openCommands[0], firstOwner)
        val secondRequest = manager.requestWindow(WindowSpec(title = "second")).successValue()
        val secondOwner = ThrowingWindowPeerOwner(ownerCause)
        val second = commit(secondRequest, port.openCommands[1], secondOwner)
        val thirdRequest = manager.requestWindow(WindowSpec(title = "third")).successValue()
        val thirdOwner = CountingWindowPeerOwner()
        val third = commit(thirdRequest, port.openCommands[2], thirdOwner)
        port.onOpenedClose = { command ->
            if (command.requestId == thirdRequest.id) throw portCause
        }

        manager.close()

        assertEquals(
            listOf(thirdRequest.id, secondRequest.id, firstRequest.id),
            port.openedCloseCommands.map(OpenedWindowCloseCommand::requestId),
        )
        assertEquals(
            listOf(WindowPhase.Closed, WindowPhase.Closed, WindowPhase.Closed),
            listOf(first, second, third).map { it.state.value.phase },
        )
        assertEquals(emptyList(), manager.state.value.windows)
        assertEquals(1, firstOwner.closeCount)
        assertEquals(1, secondOwner.closeCount)
        assertEquals(1, thirdOwner.closeCount)
        assertEquals(listOf<Throwable>(portCause, ownerCause), reported)
    }

    @Test
    fun managerTeardownReleasesPendingBeforeClosingWindowsInReverseAdmissionOrder() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port, maxWindows = 4, maxPending = 4)
        val firstRequest = manager.requestWindow(WindowSpec(title = "first")).successValue()
        val firstOwner = CountingWindowPeerOwner()
        val first = commit(firstRequest, port.openCommands[0], firstOwner)
        val pendingRequest = manager.requestWindow(WindowSpec(title = "pending")).successValue()
        val pendingCommand = port.openCommands[1]
        val thirdRequest = manager.requestWindow(WindowSpec(title = "third")).successValue()
        val thirdOwner = CountingWindowPeerOwner()
        val third = commit(thirdRequest, port.openCommands[2], thirdOwner)

        manager.close()
        manager.close()

        assertEquals(WindowRequestOutcome.Cancelled, pendingRequest.await())
        assertEquals(WindowPhase.Closed, first.state.value.phase)
        assertEquals(WindowPhase.Closed, third.state.value.phase)
        assertEquals(emptyList(), manager.state.value.windows)
        assertEquals(
            listOf(
                pendingRequest.id,
                thirdRequest.id,
                firstRequest.id,
            ),
            port.closeEvents.map(PortCloseEvent::requestId),
        )
        assertIs<PortCloseEvent.Pending>(port.closeEvents[0])
        assertIs<PortCloseEvent.Opened>(port.closeEvents[1])
        assertIs<PortCloseEvent.Opened>(port.closeEvents[2])
        assertEquals(1, firstOwner.closeCount)
        assertEquals(1, thirdOwner.closeCount)
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host)),
            manager.requestWindow(WindowSpec()),
        )

        val lateOwner = CountingWindowPeerOwner()
        pendingCommand.commit(lateOwner)
        assertEquals(1, lateOwner.closeCount)
    }

    @Test
    fun nativeCloseRequestIsPublishedAndTheFirstApplicationDecisionWins() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port, publicWindowCapabilities = true)
        val request = manager.requestWindow(WindowSpec(title = "native-close")).successValue()
        val command = port.openCommands.single()
        val window = commit(request, command)

        assertEquals(
            Capability.Supported(setOf(WindowCreationMode.OpenedHere), FeatureAvailability.Available),
            manager.state.value.capabilities.requestWindow,
        )
        assertEquals(
            Capability.Supported(Unit, FeatureAvailability.Available),
            window.capabilities.value.closeInterception,
        )
        assertEquals(
            Capability.Supported(Unit, FeatureAvailability.Available),
            window.capabilities.value.platformAccess,
        )

        val firstEvent = async(start = CoroutineStart.UNDISPATCHED) {
            window.events.filterIsInstance<WindowEvent.CloseRequested>().first()
        }
        command.closeRequested()
        val rejectedRequest = withTimeout(2.seconds) { firstEvent.await() }

        assertEquals(WindowCloseResponseOutcome.KeptOpen, window.respondToCloseRequest(
            rejectedRequest.requestId,
            WindowCloseDecision.Reject,
        ).successValue())
        assertEquals(WindowCloseResponseOutcome.KeptOpen, window.respondToCloseRequest(
            rejectedRequest.requestId,
            WindowCloseDecision.Reject,
        ).successValue())
        assertEquals(WindowCloseResponseOutcome.AlreadyResolved, window.respondToCloseRequest(
            rejectedRequest.requestId,
            WindowCloseDecision.Accept,
        ).successValue())
        assertEquals(WindowPhase.Open, window.state.value.phase)
        assertEquals(emptyList(), port.openedCloseCommands)

        val secondEvent = async(start = CoroutineStart.UNDISPATCHED) {
            window.events.filterIsInstance<WindowEvent.CloseRequested>().first()
        }
        command.closeRequested()
        val acceptedRequest = withTimeout(2.seconds) { secondEvent.await() }
        val accepted = assertIs<WindowCloseResponseOutcome.Closing>(
            window.respondToCloseRequest(acceptedRequest.requestId, WindowCloseDecision.Accept).successValue(),
        )

        assertEquals(WindowPhase.Closing, window.state.value.phase)
        assertEquals(
            WindowCloseResponseOutcome.Closing(accepted.operationId),
            window.respondToCloseRequest(acceptedRequest.requestId, WindowCloseDecision.Accept).successValue(),
        )
        assertEquals(
            WindowCloseResponseOutcome.AlreadyResolved,
            window.respondToCloseRequest(acceptedRequest.requestId, WindowCloseDecision.Reject).successValue(),
        )
        assertEquals(1, port.openedCloseCommands.size)

        command.nativeClosed()

        assertEquals(WindowPhase.Closed, window.state.value.phase)
        assertEquals(emptyList(), manager.state.value.windows)
        assertEquals(
            WindowCloseResponseOutcome.TooLate,
            window.respondToCloseRequest(acceptedRequest.requestId, WindowCloseDecision.Accept).successValue(),
        )
    }

    @Test
    fun aRejectedCloseRequestBecomesTooLateAfterAProgrammaticCloseWins() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port, publicWindowCapabilities = true)
        val request = manager.requestWindow(WindowSpec(title = "reject-then-close")).successValue()
        val command = port.openCommands.single()
        val window = commit(request, command)
        val closeRequested = async(start = CoroutineStart.UNDISPATCHED) {
            window.events.filterIsInstance<WindowEvent.CloseRequested>().first()
        }

        command.closeRequested()
        val rejected = withTimeout(2.seconds) { closeRequested.await() }
        assertEquals(
            WindowCloseResponseOutcome.KeptOpen,
            window.respondToCloseRequest(rejected.requestId, WindowCloseDecision.Reject).successValue(),
        )
        assertIs<WindowCloseOutcome.Accepted>(window.close().successValue())
        command.nativeClosed()

        assertEquals(WindowPhase.Closed, window.state.value.phase)
        assertEquals(
            WindowCloseResponseOutcome.TooLate,
            window.respondToCloseRequest(rejected.requestId, WindowCloseDecision.Reject).successValue(),
        )
    }

    @Test
    fun lastWindowPolicyArmsOnlyAfterACommitAndFiresOnTheLaterNonemptyToEmptyTransition() = runTest {
        val port = DeterministicWindowCommandPort()
        var stopProposals = 0
        val manager = manager(
            port,
            maxWindows = 3,
            maxPending = 3,
            publicWindowCapabilities = true,
            onLastWindowClosed = { stopProposals += 1 },
        )

        assertEquals(0, stopProposals)
        val pending = manager.requestWindow(WindowSpec(title = "pending")).successValue()
        assertEquals(0, stopProposals)
        assertEquals(WindowCancellationOutcome.CancelledBeforeCommit, pending.cancel())
        assertEquals(0, stopProposals)

        val firstRequest = manager.requestWindow(WindowSpec(title = "first")).successValue()
        val firstCommand = port.openCommands.last()
        val first = commit(firstRequest, firstCommand)
        val secondRequest = manager.requestWindow(WindowSpec(title = "second")).successValue()
        val secondCommand = port.openCommands.last()
        val second = commit(secondRequest, secondCommand)

        firstCommand.nativeClosed()
        assertEquals(0, stopProposals)
        assertSame(second, manager.state.value.primary)

        secondCommand.nativeClosed()
        assertEquals(1, stopProposals)
        assertEquals(emptyList(), manager.state.value.windows)

        manager.close()
        assertEquals(1, stopProposals)
        assertEquals(WindowPhase.Closed, first.state.value.phase)
        assertEquals(WindowPhase.Closed, second.state.value.phase)
    }

    @Test
    fun minimalWindowRejectsUnavailableOperationsAndKeepsTerminalSnapshots() = runTest {
        val port = DeterministicWindowCommandPort()
        val manager = manager(port)
        val request = manager.requestWindow(WindowSpec(title = "original")).successValue()
        val command = port.openCommands.single()
        val window = commit(request, command)

        val update = window.apply(WindowUpdate(title = PropertyChange.Set("changed"))).successValue()
        val partial = assertIs<WindowUpdateOutcome.PartiallyApplied>(update)
        assertEquals("original", partial.state.title)
        assertEquals(1, partial.rejected.size)
        assertEquals(KadreFailure.Unsupported(KadreOperation.UpdateWindow), partial.rejected.single().failure)
        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RequestWindowAttention)),
            window.requestAttention(WindowAttention.Informational),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("requestId")),
            window.respondToCloseRequest(WindowCloseRequestId(91), WindowCloseDecision.Reject),
        )
        assertTrue(window.capabilities.value.allPhaseThreeCapabilitiesAreUnsupported())
        assertEquals(SurfaceAttachmentState.Attached, window.surface.state.value.attachment)

        window.close()
        command.nativeClosed()

        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window)),
            window.requestAttention(WindowAttention.Critical),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window)),
            window.respondToCloseRequest(WindowCloseRequestId(92), WindowCloseDecision.Accept),
        )
        assertEquals(SurfaceAttachmentState.Detached, window.surface.state.value.attachment)
        assertEquals(WindowCloseOutcome.Closed, window.close().successValue())
    }

    private fun manager(
        port: DeterministicWindowCommandPort,
        maxWindows: Int = 4,
        maxPending: Int = 4,
        reported: MutableList<Throwable> = mutableListOf(),
        publicWindowCapabilities: Boolean = false,
        publicSurfaceCapabilities: Boolean = false,
        onLastWindowClosed: () -> Unit = {},
    ): RuntimeWindowManager = RuntimeWindowManager(
        resources = KadrePolicies.Default.resources.copy(
            maxWindowsPerSession = maxWindows,
            maxPendingWindowRequests = maxPending,
        ),
        commandPort = port,
        platform = KadrePlatform.Fake,
        failureReporter = RuntimeFailureReporter(reported::add),
        publicWindowCapabilities = publicWindowCapabilities,
        publicSurfaceCapabilities = publicSurfaceCapabilities,
        onLastWindowClosed = onLastWindowClosed,
    )

    private fun installWindowEventPolicy(
        manager: RuntimeWindowManager,
        policy: org.graphiks.kadre.policy.WindowDeliveryPolicy,
        sessionFailureHandler: (KadreFailure) -> Unit = {},
        eventStampSource: (() -> EventStamp)? = null,
    ) {
        var sequence = 0L
        manager.installSessionConfiguration(
            deliveryPolicy = policy,
            inputDeliveryPolicy = KadrePolicies.Default.input,
            source = eventStampSource ?: {
                EventStamp(
                    SessionSequence(sequence),
                    SessionInstant((sequence++).nanoseconds),
                    deliverySpan = null,
                )
            },
            sessionFailureHandler = sessionFailureHandler,
            collectorAllocator = RuntimeEventCollectorAllocator(16),
            maxCollectorsPerFlow = 16,
        )
    }

    private suspend fun kotlinx.coroutines.test.TestScope.applyContentSize(
        window: Window,
        port: DeterministicWindowCommandPort,
        contentSize: LogicalSize,
    ) {
        val update = async(start = CoroutineStart.UNDISPATCHED) {
            window.apply(WindowUpdate(contentSize = PropertyChange.Set(contentSize)))
        }
        port.updateCommands.last().applied(
            window.state.value.copy(
                contentSize = contentSize,
                revision = WindowRevision(window.state.value.revision.value + 1L),
            ),
        )
        assertIs<WindowUpdateOutcome.Applied>(update.await().successValue())
    }

    private suspend fun commit(
        request: WindowRequest,
        command: WindowOpenCommand,
        owner: WindowPeerOwner = CountingWindowPeerOwner(),
    ): Window {
        command.commit(owner)
        return assertIs<WindowRequestOutcome.OpenedHere>(request.await()).window
    }

    private fun inputKey(
        surfaceId: org.graphiks.kadre.surface.SurfaceId,
        nativeCode: String,
    ): SurfaceStimulus.KeyChanged = SurfaceStimulus.KeyChanged(
        surfaceId,
        PhysicalKey.Unidentified(nativeCode),
        LogicalKey.Unidentified(nativeCode),
        KeyLocation.Standard,
        KeyState.Pressed,
        repeat = false,
        KeyboardModifiers(emptySet()),
    )

    private fun org.graphiks.kadre.window.WindowCapabilities.allPhaseThreeCapabilitiesAreUnsupported(): Boolean =
        listOf(
            title,
            outerPosition,
            contentSize,
            minimumSize,
            maximumSize,
            resizable,
            fullscreen,
            decorations,
            systemButtons,
            level,
            transparency,
            blurBehind,
            icon,
            attention,
            contentProtection,
            closeInterception,
            platformAccess,
        ).all { it is Capability.Unsupported }

    private fun <T> KadreResult<T>.successValue(): T = when (this) {
        is KadreResult.Success -> value
        is KadreResult.Failure -> error("expected success, got $reason")
    }

    private class DeterministicWindowCommandPort : WindowCommandPort {
        val openCommands = mutableListOf<WindowOpenCommand>()
        val updateCommands = mutableListOf<WindowUpdateCommand>()
        val updateCancellationCommands = mutableListOf<WindowUpdateCancellationCommand>()
        val pendingCancellationCommands = mutableListOf<PendingWindowCancellationCommand>()
        val openedCloseCommands = mutableListOf<OpenedWindowCloseCommand>()
        val closeEvents = mutableListOf<PortCloseEvent>()
        var pendingCancellationOutcome: PendingWindowCancellationOutcome =
            PendingWindowCancellationOutcome.CancelledBeforeCommit
        var openedCloseOutcome: OpenedWindowCloseOutcome = OpenedWindowCloseOutcome.Accepted
        var updateCancellationOutcome: WindowUpdateCancellationOutcome = WindowUpdateCancellationOutcome.TooLate
        var onOpen: (WindowOpenCommand) -> Unit = {}
        var onPendingCancellation: (PendingWindowCancellationCommand) -> Unit = {}
        var onOpenedClose: (OpenedWindowCloseCommand) -> Unit = {}

        override fun requestOpen(command: WindowOpenCommand) {
            openCommands += command
            onOpen(command)
        }

        override fun requestUpdate(command: WindowUpdateCommand) {
            updateCommands += command
        }

        override fun requestUpdateCancellation(
            command: WindowUpdateCancellationCommand,
        ): WindowUpdateCancellationOutcome {
            updateCancellationCommands += command
            return updateCancellationOutcome
        }

        override fun requestPendingCancellation(
            command: PendingWindowCancellationCommand,
        ): PendingWindowCancellationOutcome {
            pendingCancellationCommands += command
            closeEvents += PortCloseEvent.Pending(command.requestId)
            onPendingCancellation(command)
            return pendingCancellationOutcome
        }

        override fun requestOpenedClose(command: OpenedWindowCloseCommand): OpenedWindowCloseOutcome {
            openedCloseCommands += command
            closeEvents += PortCloseEvent.Opened(command.requestId)
            onOpenedClose(command)
            return openedCloseOutcome
        }
    }

    private sealed interface PortCloseEvent {
        val requestId: org.graphiks.kadre.window.WindowRequestId

        data class Pending(
            override val requestId: org.graphiks.kadre.window.WindowRequestId,
        ) : PortCloseEvent

        data class Opened(
            override val requestId: org.graphiks.kadre.window.WindowRequestId,
        ) : PortCloseEvent
    }

    private class CountingWindowPeerOwner : WindowPeerOwner {
        var closeCount: Int = 0

        override fun close() {
            closeCount += 1
        }
    }

    private class ThrowingWindowPeerOwner(
        private val failure: Throwable,
    ) : WindowPeerOwner {
        var closeCount: Int = 0

        override fun close() {
            closeCount += 1
            throw failure
        }
    }

    private class QueuedCoroutineDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()

        val queuedTaskCount: Int
            get() = tasks.size

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks.addLast(block)
        }

        fun runNext(): Boolean {
            val task = tasks.removeFirstOrNull() ?: return false
            task.run()
            return true
        }
    }
}
