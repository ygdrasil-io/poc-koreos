package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.EventDeliverySpan
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.input.DeviceId
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.InputStateResetReason
import org.graphiks.kadre.input.InputStateRevision
import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.ModifierKey
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.input.ScrollDelta
import org.graphiks.kadre.policy.IngressOverflowAction
import org.graphiks.kadre.policy.InputDeliveryPolicy
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.SurfaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Multi-target execution evidence for the ordinary-input reducer.
 *
 * The scenarios are ported from the JVM window-surface suite
 * (`RuntimeWindowSurfaceTest`, which has no `java.*` dependency): they drive
 * [RuntimeSurfaceInput] directly, in its minimal configuration, and assert the same
 * observable behaviour — event content, event order, revisions and terminal rejection.
 * Because this is `commonTest`, the Karma tasks run these assertions on `js` and `wasmJs`
 * as well as on the JVM.
 *
 * Reducer reentrancy (a stimulus admitted from inside the delivery of an earlier event) is
 * load bearing for coalescing and for ingress overflow: the ingress queue only holds more
 * than one publication while a drain is in progress, exactly as in the JVM suite.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RuntimeSurfaceInputCommonTest {
    @Test
    fun keyChangedPublishesTheReducedSnapshotBeforeItsEventAndKeepsNeutralRepeatAndReleaseAtTheSameRevision() =
        runTest {
            val input = minimalInput(eventStampSource = SequentialStampSource()::next)
            val collected = mutableListOf<InputEvent>()
            val revisionsObservedAtDelivery = mutableListOf<Long>()
            val collection = collectInput(input, collected, revisionsObservedAtDelivery)
            val physical = PhysicalKey.Unidentified("native-key-91")
            val logical = LogicalKey.Unidentified("native-key-91")
            val neverPressed = "native-key-never-pressed"
            val modifiers = KeyboardModifiers(setOf(ModifierKey.Shift))

            assertTrue(input.accept(keyStimulus("native-key-91", modifiers = modifiers)))
            assertTrue(
                input.accept(keyStimulus("native-key-91", repeat = true, modifiers = modifiers)),
            )
            assertTrue(
                input.accept(
                    SurfaceStimulus.PointerEntered(
                        surfaceId = SURFACE_ID,
                        kind = PointerKind.Mouse,
                        position = LogicalPoint(19.0, 23.0),
                    ),
                ),
            )
            assertTrue(
                input.accept(
                    keyStimulus(
                        neverPressed,
                        keyState = KeyState.Released,
                        modifiers = modifiers,
                    ),
                ),
            )
            advanceUntilIdle()

            assertEquals(4, collected.size)
            val first = assertIs<InputEvent.Key>(collected[0])
            val repeated = assertIs<InputEvent.Key>(collected[1])
            val pointer = assertIs<InputEvent.PointerEntered>(collected[2])
            val released = assertIs<InputEvent.Key>(collected[3])

            // The snapshot is committed before its event: the revision read while the event is
            // delivered already contains the stimulus that produced it.
            assertEquals(
                listOf(1L, 1L, 2L, 2L),
                collected.map { it.stateRevision.value },
            )
            assertEquals(collected.map { it.stateRevision.value }, revisionsObservedAtDelivery)
            // The lane is ordered by the runtime-owned stamp, not by delivery chance.
            assertEquals(listOf(0L, 1L, 2L, 3L), collected.map { it.stamp.sequence.value })
            assertEquals(physical, first.physicalKey)
            assertEquals(logical, first.logicalKey)
            assertEquals(KeyState.Pressed, first.keyState)
            assertFalse(first.repeat)
            // A repeat that changes neither keys nor modifiers is still an ordered event.
            assertEquals(physical, repeated.physicalKey)
            assertEquals(KeyState.Pressed, repeated.keyState)
            assertTrue(repeated.repeat)
            assertEquals(InputStateRevision(1L), repeated.stateRevision)
            // A release of a key that was never pressed keeps the current revision.
            assertEquals(PhysicalKey.Unidentified(neverPressed), released.physicalKey)
            assertEquals(LogicalKey.Unidentified(neverPressed), released.logicalKey)
            assertEquals(KeyState.Released, released.keyState)
            assertFalse(released.repeat)
            assertEquals(InputStateRevision(2L), released.stateRevision)
            assertEquals(LogicalPoint(19.0, 23.0), pointer.position)

            val state = input.state.value
            assertEquals(InputStateRevision(2L), state.revision)
            assertEquals(setOf(physical), state.keyboard.pressedKeys)
            assertEquals(modifiers, state.modifiers)
            assertEquals(pointer.pointerId, state.pointers.single().id)
            collection.cancelAndJoin()
        }

    @Test
    fun scrollCoalescesAdditivelyInsideOneCoalescingBoundaryWithoutRevisingTheSnapshot() = runTest {
        val input = minimalInput(eventStampSource = SequentialStampSource()::next)
        val collected = mutableListOf<InputEvent>()
        val injectedAdmissions = mutableListOf<Boolean>()
        val firstDevice = DeviceId(1L)
        val secondDevice = DeviceId(2L)
        val collection = collectInput(input, collected) { event ->
            if (event is InputEvent.Key) {
                // Two scrolls share one boundary, device and unit: they merge additively. The
                // third changes the unit and the fourth the device: neither may merge.
                injectedAdmissions += input.accept(
                    scrollStimulus(ScrollDelta.Lines(1.0, -2.0), boundary = BOUNDARY, deviceId = firstDevice),
                )
                injectedAdmissions += input.accept(
                    scrollStimulus(ScrollDelta.Lines(3.0, 5.0), boundary = BOUNDARY, deviceId = firstDevice),
                )
                injectedAdmissions += input.accept(
                    scrollStimulus(ScrollDelta.Logical(7.0, 9.0), boundary = BOUNDARY, deviceId = firstDevice),
                )
                injectedAdmissions += input.accept(
                    scrollStimulus(ScrollDelta.Lines(-4.0, 6.0), boundary = BOUNDARY, deviceId = secondDevice),
                )
            }
        }

        assertTrue(input.accept(keyStimulus("native-scroll-barrier")))
        advanceUntilIdle()

        assertEquals(listOf(true, true, true, true), injectedAdmissions)
        assertEquals(4, collected.size)
        assertIs<InputEvent.Key>(collected[0])
        val merged = assertIs<InputEvent.Scrolled>(collected[1])
        val otherUnit = assertIs<InputEvent.Scrolled>(collected[2])
        val otherDevice = assertIs<InputEvent.Scrolled>(collected[3])
        assertEquals(ScrollDelta.Lines(4.0, 3.0), merged.delta)
        assertEquals(
            EventDeliverySpan(SessionSequence(1L), SessionSequence(2L), 2L),
            merged.stamp.deliverySpan,
        )
        assertEquals(firstDevice, merged.deviceId)
        assertEquals(ScrollDelta.Logical(7.0, 9.0), otherUnit.delta)
        assertNull(otherUnit.stamp.deliverySpan)
        assertEquals(firstDevice, otherUnit.deviceId)
        assertEquals(ScrollDelta.Lines(-4.0, 6.0), otherDevice.delta)
        assertNull(otherDevice.stamp.deliverySpan)
        assertEquals(secondDevice, otherDevice.deviceId)
        // A scroll never revises the snapshot: every scroll carries the revision of the barrier.
        assertEquals(listOf(1L, 1L, 1L, 1L), collected.map { it.stateRevision.value })
        assertEquals(InputStateRevision(1L), input.state.value.revision)
        collection.cancelAndJoin()
    }

    @Test
    fun scrollNeverCoalescesAcrossTwoCoalescingBoundaries() = runTest {
        val input = minimalInput(eventStampSource = SequentialStampSource()::next)
        val collected = mutableListOf<InputEvent>()
        val injectedAdmissions = mutableListOf<Boolean>()
        val device = DeviceId(1L)
        val collection = collectInput(input, collected) { event ->
            if (event is InputEvent.Key) {
                injectedAdmissions += input.accept(
                    scrollStimulus(ScrollDelta.Lines(2.0, 3.0), boundary = BOUNDARY, deviceId = device),
                )
                injectedAdmissions += input.accept(
                    scrollStimulus(ScrollDelta.Lines(5.0, 7.0), boundary = BOUNDARY + 1L, deviceId = device),
                )
            }
        }

        assertTrue(input.accept(keyStimulus("native-two-boundary-barrier")))
        advanceUntilIdle()

        assertEquals(listOf(true, true), injectedAdmissions)
        assertEquals(3, collected.size)
        val first = assertIs<InputEvent.Scrolled>(collected[1])
        val second = assertIs<InputEvent.Scrolled>(collected[2])
        assertEquals(ScrollDelta.Lines(2.0, 3.0), first.delta)
        assertEquals(ScrollDelta.Lines(5.0, 7.0), second.delta)
        assertNull(first.stamp.deliverySpan)
        assertNull(second.stamp.deliverySpan)
        assertEquals(InputStateRevision(1L), input.state.value.revision)
        collection.cancelAndJoin()
    }

    @Test
    fun focusLossPublishesOneNeutralSnapshotAndOneResetWithoutASyntheticKeyOrButtonRelease() = runTest {
        val input = minimalInput(eventStampSource = SequentialStampSource()::next)
        val collected = mutableListOf<InputEvent>()
        val revisionsObservedAtDelivery = mutableListOf<Long>()
        val collection = collectInput(input, collected, revisionsObservedAtDelivery)
        val physical = PhysicalKey.Unidentified("native-focus-key")
        val modifiers = KeyboardModifiers(setOf(ModifierKey.Alt))

        assertTrue(input.accept(keyStimulus("native-focus-key", modifiers = modifiers)))
        assertTrue(
            input.accept(
                SurfaceStimulus.PointerButtonChanged(
                    surfaceId = SURFACE_ID,
                    kind = PointerKind.Mouse,
                    button = PointerButton.Primary,
                    buttonState = PointerButtonState.Pressed,
                    position = LogicalPoint(31.0, 37.0),
                    pressure = null,
                    pen = null,
                ),
            ),
        )
        // The neutralisation is asserted against a snapshot that really holds input to drop.
        val held = input.state.value
        assertEquals(InputStateRevision(2L), held.revision)
        assertEquals(setOf(physical), held.keyboard.pressedKeys)
        assertEquals(modifiers, held.modifiers)
        assertEquals(setOf(PointerButton.Primary), held.pointers.single().pressedButtons)

        // The window surface routes FocusChanged(Unfocused) to exactly this entry point.
        assertTrue(input.focusLost())
        advanceUntilIdle()

        assertEquals(3, collected.size)
        assertIs<InputEvent.Key>(collected[0])
        assertIs<InputEvent.PointerButtonChanged>(collected[1])
        val reset = assertIs<InputEvent.StateReset>(collected[2])
        assertEquals(InputStateResetReason.FocusLost, reset.reason)
        assertEquals(InputStateRevision(3L), reset.stateRevision)
        assertEquals(1, collected.count { it is InputEvent.StateReset })
        // No synthetic key or button release is fabricated to explain the neutral snapshot.
        assertFalse(collected.any { it is InputEvent.Key && it.keyState == KeyState.Released })
        assertFalse(
            collected.any { it is InputEvent.PointerButtonChanged && it.buttonState == PointerButtonState.Released },
        )
        // The neutral snapshot is committed before the reset that announces it.
        assertEquals(collected.map { it.stateRevision.value }, revisionsObservedAtDelivery)
        val state = input.state.value
        assertEquals(InputStateRevision(3L), state.revision)
        assertEquals(emptySet(), state.keyboard.pressedKeys)
        assertEquals(KeyboardModifiers(emptySet()), state.modifiers)
        assertEquals(emptyList(), state.pointers)
        collection.cancelAndJoin()
    }

    @Test
    fun inputIngressOverflowIsTerminalAndRejectsEveryLaterStimulusWithoutStateOrEvents() = runTest {
        val inputPolicy = KadrePolicies.Default.input.copy(
            discreteEvents = KadrePolicies.Default.input.discreteEvents.copy(
                ingressCapacity = 1,
                ingressOverflow = IngressOverflowAction.CloseSource,
            ),
        )
        val reported = mutableListOf<Throwable>()
        val input = minimalInput(
            deliveryPolicy = inputPolicy,
            eventStampSource = SequentialStampSource()::next,
            reported = reported,
        )
        val collected = mutableListOf<InputEvent>()
        val injectedAdmissions = mutableListOf<Boolean>()
        var injected = false
        val terminal = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                input.events.collect { event ->
                    collected += event
                    if (event is InputEvent.Key && !injected) {
                        injected = true
                        injectedAdmissions += input.accept(keyStimulus("native-overflow-one"))
                        injectedAdmissions += input.accept(keyStimulus("native-overflow-two"))
                    }
                }
            }.exceptionOrNull()
        }
        val expected: KadreFailure = KadreFailure.SourceOverflow(KadreResourceKind.InputSource)

        assertTrue(input.accept(keyStimulus("native-overflow-root")))
        // The second reentrant stimulus exceeds capacity 1 and terminalises the source. This
        // revision also proves the two stimuli were admitted from inside the first delivery.
        assertEquals(InputStateRevision(4L), input.state.value.revision)
        advanceUntilIdle()

        assertEquals(listOf(true, true), injectedAdmissions)
        assertEquals(expected, assertIs<KadreException>(terminal.await()).failure)
        assertEquals(expected, assertIs<KadreException>(reported.single()).failure)
        // The overflowing stimulus is never published: only the two admitted keys reach the flow.
        assertEquals(
            listOf(LogicalKey.Unidentified("native-overflow-root"), LogicalKey.Unidentified("native-overflow-one")),
            collected.map { assertIs<InputEvent.Key>(it).logicalKey },
        )

        val neutral = input.state.value
        assertEquals(InputStateRevision(4L), neutral.revision)
        assertEquals(emptySet(), neutral.keyboard.pressedKeys)
        assertEquals(emptyList(), neutral.pointers)
        assertEquals(KeyboardModifiers(emptySet()), neutral.modifiers)
        assertEquals(
            expected,
            assertIs<FeatureAvailability.Unavailable>(neutral.capabilities.keyboard).failure,
        )

        // Every late stimulus is rejected without producing state or events.
        val revisionsBefore = neutral.revision
        val eventsBefore = collected.size
        assertFalse(input.accept(keyStimulus("native-late-after-overflow")))
        assertFalse(
            input.accept(
                scrollStimulus(ScrollDelta.Lines(1.0, 1.0), boundary = BOUNDARY, deviceId = DeviceId(1L)),
            ),
        )
        assertFalse(input.focusLost())
        advanceUntilIdle()
        assertEquals(revisionsBefore, input.state.value.revision)
        assertEquals(neutral.revision, input.state.value.revision)
        assertEquals(eventsBefore, collected.size)
        assertFalse(collected.any { it is InputEvent.Scrolled })
        assertFalse(
            collected.any { it is InputEvent.Key && it.logicalKey == LogicalKey.Unidentified("native-late-after-overflow") },
        )
    }

    private fun TestScope.collectInput(
        input: RuntimeSurfaceInput,
        collected: MutableList<InputEvent>,
        revisionsObservedAtDelivery: MutableList<Long> = mutableListOf(),
        onEvent: (InputEvent) -> Unit = {},
    ): Job = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
        input.events.collect { event ->
            collected += event
            revisionsObservedAtDelivery += input.state.value.revision.value
            onEvent(event)
        }
    }

    private fun keyStimulus(
        nativeCode: String,
        keyState: KeyState = KeyState.Pressed,
        repeat: Boolean = false,
        modifiers: KeyboardModifiers = KeyboardModifiers(emptySet()),
    ): SurfaceStimulus.KeyChanged = SurfaceStimulus.KeyChanged(
        surfaceId = SURFACE_ID,
        physicalKey = PhysicalKey.Unidentified(nativeCode),
        logicalKey = LogicalKey.Unidentified(nativeCode),
        location = KeyLocation.Standard,
        keyState = keyState,
        repeat = repeat,
        modifiers = modifiers,
    )

    private fun scrollStimulus(
        delta: ScrollDelta,
        boundary: Long,
        deviceId: DeviceId?,
    ): SurfaceStimulus.Scroll = SurfaceStimulus.Scroll(
        surfaceId = SURFACE_ID,
        delta = delta,
        coalescingBoundary = boundary,
        deviceId = deviceId,
    )

    private class SequentialStampSource {
        private var sequence = 0L

        fun next(): EventStamp {
            val current = sequence++
            return EventStamp(SessionSequence(current), SessionInstant(current.nanoseconds), null)
        }
    }

    private fun minimalInput(
        deliveryPolicy: InputDeliveryPolicy = KadrePolicies.Default.input,
        eventStampSource: () -> EventStamp = {
            EventStamp(SessionSequence(0L), SessionInstant(0L.nanoseconds), null)
        },
        reported: MutableList<Throwable> = mutableListOf(),
    ): RuntimeSurfaceInput = RuntimeSurfaceInput(
        surfaceId = SURFACE_ID,
        deliveryPolicy = deliveryPolicy,
        eventStampSource = eventStampSource,
        eventCollectorGate = eventCollectorGate(),
        textInputPort = UnsupportedTextInputPort,
        rawInputCoordinator = null,
        rawInputCapability = unsupported(KadreOperation.RawInputAccess),
        dragAndDropAvailable = false,
        resources = KadrePolicies.Default.resources,
        dropTransferBudget = RuntimeDropTransferBudget(
            KadrePolicies.Default.resources.maxConcurrentDropTransfers,
        ),
        dropTransferScope = null,
        textInputEventCollectorGate = eventCollectorGate(),
        failureReporter = { reported += it },
        sessionFailureHandler = { },
    )

    private fun eventCollectorGate(): RuntimeEventCollectorGate = RuntimeEventCollectorAllocator(
        KadrePolicies.Default.resources.maxEventCollectorsPerSession,
    ).newGate(KadrePolicies.Default.resources.maxEventCollectorsPerFlow)

    private companion object {
        val SURFACE_ID = SurfaceId(1L)
        const val BOUNDARY = 7L
    }
}
