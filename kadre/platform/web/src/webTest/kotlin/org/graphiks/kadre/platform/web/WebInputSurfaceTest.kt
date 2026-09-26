package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.InputStateResetReason
import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.ModifierKey
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.input.ScrollDelta
import org.graphiks.kadre.input.SurfaceInput
import org.graphiks.kadre.input.SurfaceInputState
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.SurfaceAttachmentState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The input seam of the web surface: the target's DOM-free stimuli, the one shared reducer they feed,
 * and the capabilities and terminalisation that reducer owns.
 *
 * Every case drives the shared [RecordingWebHostPort], which is the only target this suite has, so
 * what is proven here is the surface's own contract: one reducer per surface, buffered stimuli
 * replayed in order, structural capabilities declared only by the surface's own observation, a focus
 * loss neutralised exactly once, and nothing admitted after the close.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WebInputSurfaceTest {
    @Test
    fun stimuliObservedBeforeTheConfigurationReplayInOrderAndWithoutLoss() = runTest {
        val harness = InputHarness(
            this,
            stimuliBeforeInstall = listOf(
                keyChanged(KEY_A, modifiers = SHIFT),
                keyChanged(KEY_B, modifiers = SHIFT),
                keyChanged(KEY_A, KeyState.Released),
            ),
        )
        harness.start()
        val state = harness.surface().input.state.value

        assertEquals(
            setOf(physicalKey(KEY_B)),
            state.keyboard.pressedKeys,
            "the release of A must be reduced after the press of B, so replay is ordered",
        )
        assertEquals(
            KeyboardModifiers(emptySet()),
            state.modifiers,
            "the last replayed stimulus carries the last modifiers, so none is lost or reordered",
        )
        assertEquals(
            4L,
            state.revision.value,
            "three replayed stimuli (1..3) and the structural observation (4) are all admitted",
        )
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun everyStimulusPublishesItsStateBeforeItsEventAndRevisionsNeverGoBackwards() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val input = harness.surface().input
        val events = mutableListOf<InputEvent>()
        val revisionsAtDelivery = mutableListOf<Long>()
        val collector = collectInput(input, events, revisionsAtDelivery)
        testScheduler.runCurrent()

        harness.port.deliverInput(keyChanged(KEY_A, modifiers = SHIFT))
        harness.port.deliverInput(WebInputStimulus.PointerEntered(LogicalPoint(11.0, 12.0)))
        harness.port.deliverInput(
            WebInputStimulus.PointerMoved(LogicalPoint(13.0, 14.0), LogicalDelta(2.0, 2.0), pressure = 0.5),
        )
        harness.port.deliverInput(
            WebInputStimulus.PointerButtonChanged(
                button = PointerButton.Primary,
                buttonState = PointerButtonState.Pressed,
                position = LogicalPoint(13.0, 14.0),
                pressure = 0.5,
            ),
        )
        harness.port.deliverInput(WebInputStimulus.Scrolled(ScrollDelta.Lines(0.0, 3.0), coalescingBoundary = 0L))
        harness.port.deliverInput(WebInputStimulus.PointerLeft)
        harness.port.deliverInput(keyChanged(KEY_A, KeyState.Released))
        testScheduler.runCurrent()

        assertIs<InputEvent.Key>(events[0])
        assertIs<InputEvent.PointerEntered>(events[1])
        assertIs<InputEvent.PointerMoved>(events[2])
        assertIs<InputEvent.PointerButtonChanged>(events[3])
        assertIs<InputEvent.Scrolled>(events[4])
        assertIs<InputEvent.PointerLeft>(events[5])
        assertIs<InputEvent.Key>(events[6])

        assertEquals(
            listOf(2L, 3L, 4L, 5L, 5L, 5L, 7L),
            events.map { it.stateRevision.value },
            "a scroll and a pointer exit carry the current revision, the other stimuli the one they moved",
        )
        assertEquals(
            events.map { it.stateRevision.value },
            revisionsAtDelivery,
            "the snapshot is committed before its event: the revision read at delivery is already the " +
                "one the event announces",
        )
        assertEquals(
            events.map { it.stateRevision.value }.sorted(),
            events.map { it.stateRevision.value },
            "revisions never go backwards",
        )
        val stamps = events.map { it.stamp.sequence.value }
        assertEquals(stamps.sorted(), stamps, "the lane is ordered by the session's own stamp source")
        assertTrue(stamps.zipWithNext().all { (previous, next) -> previous < next })

        val state = input.state.value
        assertEquals(7L, state.revision.value)
        assertEquals(emptySet(), state.keyboard.pressedKeys)
        assertTrue(state.pointers.isEmpty(), "PointerLeft removes the mouse after publishing its last position")

        collector.cancel()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun keyboardAndPointerAreDeclaredOnlyByTheSurfaceOwnStructuralObservation() = runTest {
        val harness = InputHarness(this, stimuliBeforeInstall = listOf(keyChanged(KEY_A)))
        harness.start()
        val input = harness.surface().input
        val capabilities = input.state.value.capabilities

        assertEquals(FeatureAvailability.Available, capabilities.keyboard)
        assertEquals(FeatureAvailability.Available, capabilities.pointer)
        assertEquals(FeatureAvailability.Unsupported, capabilities.touch)
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.GestureInput)),
            capabilities.gestures,
        )
        assertEquals(FeatureAvailability.Unsupported, capabilities.dragAndDrop)
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.TextInput)),
            capabilities.textInput,
        )
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RawInputAccess)),
            capabilities.rawInput,
        )
        // The structural observation is the transition that declared them: the stimulus that waited
        // for the configuration occupied the previous revision, so nothing declared keyboard or
        // pointer available before the observation.
        assertEquals(
            2L,
            input.state.value.revision.value,
            "the pre-configuration stimulus (1) and the observation's capability change (2)",
        )

        // No target stimulus can declare a capability: the union has no way of saying so.
        harness.port.deliverInput(keyChanged(KEY_B))
        harness.port.deliverInput(WebInputStimulus.PointerEntered(LogicalPoint(1.0, 1.0)))
        testScheduler.runCurrent()

        assertEquals(capabilities, input.state.value.capabilities, "a stimulus never declares a capability")
        assertEquals(setOf(physicalKey(KEY_A), physicalKey(KEY_B)), input.state.value.keyboard.pressedKeys)

        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun losingFocusNeutralisesTheSnapshotExactlyOncePerLoss() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val port = harness.port
        val input = harness.surface().input
        val events = mutableListOf<InputEvent>()
        val collector = launch { input.events.collect { events += it } }
        testScheduler.runCurrent()

        port.deliverInput(keyChanged(KEY_A, modifiers = SHIFT))
        port.deliverInput(WebInputStimulus.PointerEntered(LogicalPoint(4.0, 5.0)))
        testScheduler.runCurrent()
        val pressed = input.state.value
        assertEquals(1, pressed.pointers.size)
        assertEquals(setOf(physicalKey(KEY_A)), pressed.keyboard.pressedKeys)

        // The element's subtree lost focus: the browsing context is still focused, the page visible.
        port.deliverLifecycle(subtreeBlurredSnapshot())
        testScheduler.runCurrent()

        val neutral = input.state.value
        val resets = events.filterIsInstance<InputEvent.StateReset>()
        assertEquals(1, resets.size, "one loss of activation publishes exactly one reset")
        assertEquals(InputStateResetReason.FocusLost, resets.single().reason)
        assertEquals(resets.single().stateRevision, neutral.revision)
        assertEquals(
            pressed.revision.value + 1L,
            neutral.revision.value,
            "the reset publishes one new revision, not one per cleared field",
        )
        assertEquals(emptySet(), neutral.keyboard.pressedKeys)
        assertEquals(KeyboardModifiers(emptySet()), neutral.modifiers)
        assertTrue(neutral.pointers.isEmpty())
        assertTrue(neutral.touches.isEmpty())
        // A capability describes the structural installation, not the transient eligibility of the
        // element: the loss neutralises the snapshot and leaves keyboard and pointer available.
        assertEquals(FeatureAvailability.Available, neutral.capabilities.keyboard)
        assertEquals(FeatureAvailability.Available, neutral.capabilities.pointer)

        // The port may repeat a snapshot it already delivered; the same loss is never announced twice.
        port.deliverLifecycle(subtreeBlurredSnapshot())
        testScheduler.runCurrent()
        assertEquals(1, events.filterIsInstance<InputEvent.StateReset>().size)
        assertEquals(neutral.revision, input.state.value.revision)

        // Regaining focus does not restore the old snapshot and does not reset anything.
        port.deliverLifecycle(refocusedSnapshot())
        testScheduler.runCurrent()
        assertEquals(1, events.filterIsInstance<InputEvent.StateReset>().size)
        assertEquals(neutral.revision, input.state.value.revision)

        // A later loss is a new loss, and it is announced exactly once too.
        port.deliverLifecycle(subtreeBlurredSnapshot())
        testScheduler.runCurrent()
        assertEquals(2, events.filterIsInstance<InputEvent.StateReset>().size)
        assertEquals(neutral.revision.value + 1L, input.state.value.revision.value)

        collector.cancel()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aHiddenDocumentNeutralisesTheSnapshotOnce() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val port = harness.port
        val input = harness.surface().input
        val events = mutableListOf<InputEvent>()
        val collector = launch { input.events.collect { events += it } }
        testScheduler.runCurrent()

        port.deliverInput(keyChanged(KEY_A))
        testScheduler.runCurrent()

        port.deliverLifecycle(hiddenDocumentSnapshot())
        testScheduler.runCurrent()
        port.deliverLifecycle(hiddenDocumentSnapshot())
        testScheduler.runCurrent()

        val resets = events.filterIsInstance<InputEvent.StateReset>()
        assertEquals(1, resets.size, "a document that goes hidden loses activation once")
        assertEquals(InputStateResetReason.FocusLost, resets.single().reason)
        assertEquals(emptySet(), input.state.value.keyboard.pressedKeys)

        collector.cancel()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aPageHideNeutralisesTheSnapshotAndThenClosesTheFlow() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val port = harness.port
        val surface = harness.surface()
        val input = surface.input
        val events = mutableListOf<InputEvent>()
        val collector = launch { input.events.collect { events += it } }
        testScheduler.runCurrent()

        port.deliverInput(keyChanged(KEY_A))
        testScheduler.runCurrent()

        port.deliverLifecycle(port.pageHiddenSnapshot())
        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf(InputStateResetReason.FocusLost),
            events.filterIsInstance<InputEvent.StateReset>().map { it.reason },
            "a pagehide loses activation once before the surface closes",
        )
        assertEquals(SurfaceAttachmentState.Detached, surface.state.value.attachment)
        assertEquals(emptySet(), input.state.value.keyboard.pressedKeys)
        assertTrue(collector.isCompleted, "the input events flow closes with the surface")
        assertEquals(1, port.releaseCount)
        assertEquals(SessionOutcome.Stopped(SessionStopReason.HostDetached), harness.outcome())
    }

    /**
     * The port owns the callbacks it already queued, so one stimulus can still be delivered while the
     * surface is letting the element go. It must reach neither the state nor the event stream: the
     * terminal transition closed admission first and the reducer with it.
     */
    @Test
    fun aStimulusRacingTheReleaseProducesNoStateAndNoEvent() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val port = harness.port
        val surface = harness.surface()
        val input = surface.input
        val events = mutableListOf<InputEvent>()
        val collector = launch { input.events.collect { events += it } }
        testScheduler.runCurrent()

        port.deliverInput(keyChanged(KEY_A))
        testScheduler.runCurrent()

        var stateAtRelease: SurfaceInputState? = null
        port.onRelease = {
            port.deliverInput(keyChanged(KEY_B))
            port.deliverInput(WebInputStimulus.Scrolled(ScrollDelta.Lines(0.0, 1.0), coalescingBoundary = 1L))
            stateAtRelease = input.state.value
        }

        port.deliverLifecycle(port.disconnectedSnapshot())
        testScheduler.advanceUntilIdle()

        val closed = input.state.value
        assertEquals(SurfaceAttachmentState.Detached, surface.state.value.attachment)
        assertEquals(closed, stateAtRelease, "the racing stimulus may not mutate a closed input")
        assertEquals(closed, input.state.value, "and the closed input stays frozen")
        assertTrue(closed.keyboard.pressedKeys.isEmpty(), "the terminal transition neutralised it")
        assertEquals(
            listOf(InputStateResetReason.FocusLost),
            events.filterIsInstance<InputEvent.StateReset>().map { it.reason },
            "the terminal transition publishes its own reset and nothing else",
        )
        assertTrue(
            events.none { it is InputEvent.Key && it.physicalKey == physicalKey(KEY_B) },
            "the racing key never reaches the event stream",
        )
        assertTrue(events.none { it is InputEvent.Scrolled })
        assertTrue(collector.isCompleted, "the input events flow is closed exactly once")
        assertEquals(1, port.releaseCount)

        collector.cancel()
        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * The same window from the revocation side: a cooperative stop lets the element go while the
     * runtime still has to close the surface, so input delivered in between is refused before the
     * reducer is even closed.
     */
    @Test
    fun aStimulusDeliveredWhileTheOwnerRevokesIsNotAdmitted() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val port = harness.port
        val surface = harness.surface()
        val input = surface.input
        val events = mutableListOf<InputEvent>()
        val collector = launch { input.events.collect { events += it } }
        testScheduler.runCurrent()

        port.deliverInput(keyChanged(KEY_A))
        testScheduler.runCurrent()
        val pressed = input.state.value

        var stateDuringRevocation: SurfaceInputState? = null
        port.onRelease = {
            port.deliverInput(keyChanged(KEY_B))
            stateDuringRevocation = input.state.value
        }

        // The window under test: the stop releases the port before the runtime closes the surface.
        harness.stop()
        assertEquals(1, port.releaseCount, "a cooperative stop releases the port itself")
        assertEquals(pressed, stateDuringRevocation, "a stimulus admitted after the revocation changes nothing")

        testScheduler.advanceUntilIdle()

        assertEquals(pressed.revision, input.state.value.revision)
        assertTrue(
            events.none { it is InputEvent.Key && it.physicalKey == physicalKey(KEY_B) },
            "no stimulus may be admitted after the owner let the element go",
        )
        assertEquals(1, events.size, "only the stimulus admitted before the revocation is published")
        assertEquals(SurfaceAttachmentState.Detached, surface.state.value.attachment)
        assertTrue(collector.isCompleted)

        collector.cancel()
        testScheduler.runCurrent()
    }

    private fun TestScope.collectInput(
        input: SurfaceInput,
        events: MutableList<InputEvent>,
        revisionsAtDelivery: MutableList<Long> = mutableListOf(),
    ): Job = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
        input.events.collect { event ->
            events += event
            revisionsAtDelivery += input.state.value.revision.value
        }
    }

    /** The element's subtree lost focus while the browsing context and the document stayed active. */
    private fun subtreeBlurredSnapshot(): WebLifecycleSnapshot = WebLifecycleSnapshot(
        connected = true,
        inOriginDocument = true,
        documentVisible = true,
        browsingContextFocused = true,
        subtreeFocused = false,
    )

    /** The same element with its subtree focused again, and nothing else changed. */
    private fun refocusedSnapshot(): WebLifecycleSnapshot = WebLifecycleSnapshot(
        connected = true,
        inOriginDocument = true,
        documentVisible = true,
        browsingContextFocused = true,
        subtreeFocused = true,
    )

    /** A visible, focused element whose document went hidden instead. */
    private fun hiddenDocumentSnapshot(): WebLifecycleSnapshot = WebLifecycleSnapshot(
        connected = true,
        inOriginDocument = true,
        documentVisible = false,
        browsingContextFocused = true,
        subtreeFocused = false,
    )

    private companion object {
        const val KEY_A: String = "native-key-a"
        const val KEY_B: String = "native-key-b"
        val SHIFT: KeyboardModifiers = KeyboardModifiers(setOf(ModifierKey.Shift))

        fun physicalKey(nativeCode: String): PhysicalKey = PhysicalKey.Unidentified(nativeCode)

        fun keyChanged(
            nativeCode: String,
            keyState: KeyState = KeyState.Pressed,
            repeat: Boolean = false,
            modifiers: KeyboardModifiers = KeyboardModifiers(emptySet()),
        ): WebInputStimulus = WebInputStimulus.KeyChanged(
            physicalKey = physicalKey(nativeCode),
            logicalKey = LogicalKey.Unidentified(nativeCode),
            location = KeyLocation.Standard,
            keyState = keyState,
            repeat = repeat,
            modifiers = modifiers,
        )
    }
}

/** One web session whose primary surface receives the target's input through the shared double. */
private class InputHarness(
    scope: TestScope,
    stimuliBeforeInstall: List<WebInputStimulus> = emptyList(),
) {
    val port = RecordingWebHostPort(WebSurfaceMetrics(48.0, 48.0, 1.0))
    private val scopeReady = CompletableDeferred<KadreScope>()
    private val session: KadreSession

    init {
        port.preInstallInput = stimuliBeforeInstall
        session = assertIs<KadreResult.Success<KadreSession>>(
            WebHostSession(port = port, registry = WebHostRegistry()).attach(
                parentScope = scope,
                applicationFactory = KadreApplicationFactory {
                    KadreApplication {
                        scopeReady.complete(this)
                        awaitCancellation()
                    }
                },
                policy = KadrePolicies.Default,
            ),
        ).value
    }

    /** Pumping the scheduler until the application scope has been handed to the test. */
    suspend fun start() = scopeReady.await()

    /** The surface this session owns, once its application scope exists. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun surface(): HostSurface = scopeReady.getCompleted().primarySurface.value
        ?: error("a web session exposes a primary surface")

    /** The terminal outcome the runtime published for this session. */
    suspend fun outcome(): SessionOutcome = session.awaitTermination()

    fun stop() = session.requestStop()
}
