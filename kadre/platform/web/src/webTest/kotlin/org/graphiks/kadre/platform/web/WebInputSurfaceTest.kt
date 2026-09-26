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
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.InputStateResetReason
import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.ModifierKey
import org.graphiks.kadre.input.NamedKey
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.input.ScrollDelta
import org.graphiks.kadre.input.SurfaceInput
import org.graphiks.kadre.input.SurfaceInputState
import org.graphiks.kadre.policy.ContinuousDelivery
import org.graphiks.kadre.policy.ContinuousOverflowAction
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.RejectedSurfaceField
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceProperty
import org.graphiks.kadre.surface.SurfaceUpdate
import org.graphiks.kadre.surface.SurfaceUpdateOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The input seam of the web surface: the target's DOM-free stimuli, the one shared reducer they feed,
 * and the capabilities and terminalisation that reducer owns.
 *
 * Every case drives the shared [RecordingWebHostPort], which is the only target this suite has, so
 * what is proven here is the surface's own contract: one reducer per surface, buffered stimuli
 * replayed in order, structural capabilities declared only by the surface's own observation, a loss
 * of activation neutralised exactly once, a terminating transition that closes the input lane instead
 * of resetting it, and nothing admitted after the close.
 *
 * The last group covers `SurfaceUpdate.inputDefaultBehavior`, the one surface-update field this phase
 * activates: the capability that publishes it, the admission and commit of a value through the shared
 * helper, and the suppression decision the port asks the surface for at the point of dispatch —
 * including the phase's exit gate, which is that no category is suppressed under the default.
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
        harness.port.deliverInput(WebInputStimulus.PointerEntered(LogicalPoint(11.0, 12.0), kind = PointerKind.Mouse))
        harness.port.deliverInput(
            WebInputStimulus.PointerMoved(
                LogicalPoint(13.0, 14.0),
                LogicalDelta(2.0, 2.0),
                pressure = 0.5,
                kind = PointerKind.Mouse,
                pen = null,
            ),
        )
        harness.port.deliverInput(
            WebInputStimulus.PointerButtonChanged(
                button = PointerButton.Primary,
                buttonState = PointerButtonState.Pressed,
                position = LogicalPoint(13.0, 14.0),
                pressure = 0.5,
                kind = PointerKind.Mouse,
                pen = null,
            ),
        )
        harness.port.deliverInput(WebInputStimulus.Scrolled(ScrollDelta.Lines(0.0, 3.0), coalescingBoundary = 0L))
        harness.port.deliverInput(WebInputStimulus.PointerLeft(kind = PointerKind.Mouse))
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
        // pointer available before the observation. This is a *transition* proof, not a timeline one:
        // the reducer owns a `StateFlow`, which carries no history, so no collector can read the
        // capability value that existed before the install. The revision ordering is what is
        // observable — had anything declared them earlier, the observation would have been a no-op
        // (`updateObservationCapabilitiesLocked` returns without moving the revision) and the lane
        // would still be at revision 1.
        assertEquals(
            2L,
            input.state.value.revision.value,
            "the pre-configuration stimulus (1) and the observation's capability change (2)",
        )

        // No target stimulus can declare a capability: the union has no way of saying so.
        harness.port.deliverInput(keyChanged(KEY_B))
        harness.port.deliverInput(WebInputStimulus.PointerEntered(LogicalPoint(1.0, 1.0), kind = PointerKind.Mouse))
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
        port.deliverInput(WebInputStimulus.PointerEntered(LogicalPoint(4.0, 5.0), kind = PointerKind.Mouse))
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

    /**
     * A terminating transition closes the input lane, it never resets it.
     *
     * The reset belongs to the activation-loss branch alone (`MinimalWindowSurface.kt:326` routes its
     * own teardown straight to the terminal path, `:369` calls `focusLost()` only for a `FocusChanged`
     * observed while the lane still admits, and `:760-766` closes the input on the terminal
     * publication), which is also what `APPKIT-PHASE-4-INPUT-DESIGN.md` states: after a detach or a
     * native revocation, every late stimulus is ignored and the input flow is closed.
     *
     * The lane was live — one key was pressed before the page went away — so a synthetic reset would
     * have been observable here.
     */
    @Test
    fun aPageHideClosesTheInputFlowWithoutAReset() = runTest {
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
        val beforeThePageHide = input.state.value
        assertEquals(1, events.size, "the lane is live: the key was published before the page went away")

        port.deliverLifecycle(port.pageHiddenSnapshot())
        testScheduler.advanceUntilIdle()

        assertEquals(SurfaceAttachmentState.Detached, surface.state.value.attachment)
        assertTrue(
            events.none { it is InputEvent.StateReset },
            "a terminating transition publishes no StateReset",
        )
        assertEquals(1, events.size, "and no input event of its own either")
        assertEquals(
            beforeThePageHide,
            input.state.value,
            "the closed lane keeps the snapshot it had: closing does not neutralise it (RuntimeSurfaceInput.close)",
        )
        assertTrue(collector.isCompleted, "the input events flow closes with the surface")
        assertEquals(1, port.releaseCount)
        assertEquals(SessionOutcome.Stopped(SessionStopReason.HostDetached), harness.outcome())
    }

    /**
     * The same terminating transition on a lane that had nothing in flight: the closed snapshot is the
     * neutral one, and it is reached without a reset.
     */
    @Test
    fun aDisconnectClosesTheInputFlowWithANeutralSnapshot() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val port = harness.port
        val surface = harness.surface()
        val input = surface.input
        val events = mutableListOf<InputEvent>()
        val collector = launch { input.events.collect { events += it } }
        testScheduler.runCurrent()

        port.deliverLifecycle(port.disconnectedSnapshot())
        testScheduler.advanceUntilIdle()

        assertEquals(SurfaceAttachmentState.Detached, surface.state.value.attachment)
        assertEquals(emptySet(), input.state.value.keyboard.pressedKeys)
        assertTrue(input.state.value.pointers.isEmpty())
        assertEquals(KeyboardModifiers(emptySet()), input.state.value.modifiers)
        assertTrue(events.isEmpty(), "no input event is published by a terminating transition")
        assertTrue(collector.isCompleted, "the input events flow closes with the surface")
        assertEquals(1, port.releaseCount)
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
        assertTrue(
            events.none { it is InputEvent.StateReset },
            "a terminating transition publishes no reset, not even one racing stimulus later",
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

    /**
     * A surface's own redraw overflow closes the input lane with the surface failure.
     *
     * On the reference surface the redraw request enters the surface's own publication scheduler
     * (`MinimalWindowSurface.kt:720-750`); a `Buffered` policy whose action is `CloseSource` or
     * `FailSession` terminalises the surface with `KadreFailure.SourceOverflow(KadreResourceKind.Surface)`
     * (`:738-747`); the terminal publication is drained last (`:760-766`) and the drain closes the
     * input lane with that same failure (`surfaceInput.close(publication.failure)`). The input lane
     * keeps its own ingress policy for its own overflow — a surface-event overflow is a surface
     * failure that also ends the input source, it does not replace the input policy.
     */
    @Test
    fun aRedrawOverflowClosesTheInputLaneWithTheSurfaceFailure() = runTest {
        val bufferedCapacity = 2
        val policy = KadrePolicies.Default.copy(
            window = KadrePolicies.Default.window.copy(
                redrawRequests = ContinuousDelivery.Buffered(
                    capacity = bufferedCapacity,
                    onOverflow = ContinuousOverflowAction.FailSession,
                ),
            ),
        )
        val harness = InputHarness(this, policy = policy)
        harness.start()
        val surface = harness.surface()
        val input = surface.input
        var closedWith: Throwable? = null
        val collector = launch {
            try {
                input.events.collect { }
            } catch (cause: Throwable) {
                closedWith = cause
            }
        }
        testScheduler.runCurrent()

        repeat(bufferedCapacity + 1) { assertEquals(KadreResult.Success(Unit), surface.requestRedraw()) }
        testScheduler.advanceUntilIdle()

        assertEquals(SurfaceAttachmentState.Detached, surface.state.value.attachment)
        assertTrue(collector.isCompleted, "the input events flow is closed by the overflow")
        assertEquals(
            KadreFailure.SourceOverflow(KadreResourceKind.Surface),
            assertIs<KadreException>(closedWith).failure,
            "the input lane reports the surface failure it was closed with",
        )
        assertEquals(
            SessionOutcome.Failed(KadreFailure.SourceOverflow(KadreResourceKind.Surface)),
            harness.outcome(),
        )
        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * The field is published as `Supported` over the whole enum, `Available`, and nothing else moved.
     *
     * The constraint set is written out member by member in the production code, and pinned here
     * against `InputDefaultBehavior.entries` in both directions: a third member of the enum fails this
     * case instead of being claimed silently, and so does a shape that stops covering one of the two
     * members the port can honour.
     *
     * The same case closes the other end of the promise: once the surface stopped admitting, the whole
     * snapshot is unavailable (`DESIGN.md:703`), so the field is not claimed by a surface that can no
     * longer drop any default.
     */
    @Test
    fun theInputDefaultBehaviorCapabilityIsTheWholeEnumAndTheOtherFieldsStayUnsupported() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val capabilities = harness.surface().capabilities.value

        val inputDefaultBehavior = assertIs<Capability.Supported<Set<InputDefaultBehavior>>>(
            capabilities.inputDefaultBehavior,
        )
        assertEquals(
            setOf(InputDefaultBehavior.HostDefault, InputDefaultBehavior.SuppressWhenPossible),
            inputDefaultBehavior.constraints,
            "the two members this backend can honour are named one by one, not derived from the enum",
        )
        assertEquals(
            InputDefaultBehavior.entries.toSet(),
            inputDefaultBehavior.constraints,
            "the enum has exactly two members: a future one must fail here rather than be claimed silently",
        )
        assertEquals(FeatureAvailability.Available, inputDefaultBehavior.availability)

        // Scope non-regression: this task activates one field and only one. The three fields it does
        // not touch keep the blanket rejection of the previous phase, byte for byte.
        assertEquals(unsupportedUpdateSurface(), capabilities.cursor)
        assertEquals(unsupportedUpdateSurface(), capabilities.customCursor)
        assertEquals(unsupportedUpdateSurface(), capabilities.hitTesting)

        // A detached surface claims nothing at all, this field included: the capability is the
        // statement of what can still be asked of the surface, and a surface that stopped admitting
        // answers `Closed` to everything.
        harness.port.deliverLifecycle(harness.port.disconnectedSnapshot())
        testScheduler.advanceUntilIdle()

        assertEquals(SurfaceAttachmentState.Detached, harness.surface().state.value.attachment)
        assertEquals(
            unsupportedUpdateSurface(),
            harness.surface().capabilities.value.inputDefaultBehavior,
            "the terminal snapshot is the all-unsupported one, as the reference publishes it",
        )

        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * The admission path of the one field this phase activates: requested, admitted, committed,
     * revisioned, and reported as `Applied` with no rejected field.
     *
     * The revision is asserted as a *step*: it moves by exactly one for the state change the admitted
     * field causes, which is the rule the reference surface applies when it commits the fields its
     * backend acknowledged, and it is asserted to move for nothing else in the cases below.
     */
    @Test
    fun settingSuppressWhenPossibleIsAdmittedCommittedAndRevisioned() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val surface = harness.surface()
        val before = surface.state.value
        assertEquals(InputDefaultBehavior.HostDefault, before.inputDefaultBehavior, "the state starts at the default")

        val applied = assertIs<SurfaceUpdateOutcome.Applied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(
                    SurfaceUpdate(
                        inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.SuppressWhenPossible),
                    ),
                ),
            ).value,
        )

        assertEquals(InputDefaultBehavior.SuppressWhenPossible, applied.state.inputDefaultBehavior)
        assertEquals(
            before.revision.value + 1L,
            applied.state.revision.value,
            "the effective value is committed with one new revision",
        )
        assertEquals(
            applied.state,
            surface.state.value,
            "the outcome carries the state the surface publishes, not a copy that could drift from it",
        )
        assertEquals(InputDefaultBehavior.SuppressWhenPossible, surface.state.value.inputDefaultBehavior)

        harness.stop()
        testScheduler.runCurrent()
    }

    /** The way back: the default is a value like the other, admitted and committed without error. */
    @Test
    fun restoringHostDefaultIsAdmittedWithoutError() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val surface = harness.surface()

        surface.apply(SurfaceUpdate(inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.SuppressWhenPossible)))
        val suppressed = surface.state.value
        val restored = assertIs<SurfaceUpdateOutcome.Applied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(SurfaceUpdate(inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.HostDefault))),
            ).value,
        )

        assertEquals(InputDefaultBehavior.HostDefault, restored.state.inputDefaultBehavior)
        assertEquals(InputDefaultBehavior.HostDefault, surface.state.value.inputDefaultBehavior)
        assertEquals(suppressed.revision.value + 1L, restored.state.revision.value)

        // Requesting the value already in effect is admitted and answered with the state it already
        // has — and the revision does not move, because the revision is the marker of a state change
        // rather than of a call: the reference commits the same way (`commitUpdateLocked` bumps only
        // when the committed state differs from the previous one).
        val again = assertIs<SurfaceUpdateOutcome.Applied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(SurfaceUpdate(inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.HostDefault))),
            ).value,
        )
        assertEquals(InputDefaultBehavior.HostDefault, again.state.inputDefaultBehavior)
        assertEquals(restored.state.revision.value, again.state.revision.value)

        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * A field the surface cannot honour is rejected with the shared helper's own outcome, and it does
     * not block the field that can be honoured in the same update.
     *
     * This is the reference behaviour proven on the reference surface
     * (`RuntimeWindowSurfaceTest.fieldsRemainUnchangedUntilThePortReportsTheirEffectiveValues`): every
     * unsupported field of the update is collected as a [RejectedSurfaceField] carrying
     * `Unsupported(UpdateSurface)`, the state of the rejected fields does not move, and the update as
     * a whole is a `PartiallyApplied`.
     *
     * The constraint branch of that same helper cannot be reached from here today: the capability
     * claims the whole enum, which the case above pins, so no `InputDefaultBehavior` exists that it
     * would refuse. The branch is the runtime's own `admitField`, covered by its own suite; what is
     * proven here is that this surface is wired to it rather than to a second admission mechanism of
     * its own.
     */
    @Test
    fun aRejectedFieldKeepsTheSharedOutcomeAndDoesNotBlockAnAdmittedOne() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val surface = harness.surface()
        val before = surface.state.value

        val partiallyApplied = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(
                    SurfaceUpdate(
                        cursor = PropertyChange.Set(CursorStyle.Hidden),
                        inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.SuppressWhenPossible),
                    ),
                ),
            ).value,
        )

        assertEquals(
            listOf(
                RejectedSurfaceField(
                    SurfaceProperty.Cursor,
                    KadreFailure.Unsupported(KadreOperation.UpdateSurface),
                ),
            ),
            partiallyApplied.rejected,
        )
        assertEquals(CursorStyle.System(CursorIcon.Default), partiallyApplied.state.cursor, "a rejected field is not committed")
        assertEquals(
            InputDefaultBehavior.SuppressWhenPossible,
            partiallyApplied.state.inputDefaultBehavior,
            "the admitted field is committed even though another one was rejected",
        )
        assertEquals(
            before.revision.value + 1L,
            partiallyApplied.state.revision.value,
            "one commit, one revision: the rejection moves nothing",
        )

        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * The two requests the reference refuses before admission, refused here with the same failures.
     *
     * A `Clear` has no meaning for these fields — none of them has an "unset" value — so it is an
     * `InvalidRequest` naming the field, and the call has no effect at all. An expected revision that
     * is not the current one is a `StaleRevision`, the failure the contract reserves for it; without
     * that check a caller doing optimistic concurrency would be told `Applied` for an update it
     * computed against a state that no longer exists.
     */
    @Test
    fun aClearAndAStaleRevisionAreRefusedBeforeAdmission() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val surface = harness.surface()
        val before = surface.state.value

        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("inputDefaultBehavior")),
            surface.apply(SurfaceUpdate(inputDefaultBehavior = PropertyChange.Clear)),
            "a field with no unset value refuses `Clear` by name, as the reference surface does",
        )
        assertEquals(before, surface.state.value, "a request refused before admission changes nothing")

        val stale = before.revision
        surface.apply(SurfaceUpdate(inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.SuppressWhenPossible)))
        val committed = surface.state.value
        assertEquals(
            KadreResult.Failure(
                KadreFailure.StaleRevision(stale.value, committed.revision.value),
            ),
            surface.apply(
                SurfaceUpdate(
                    expectedRevision = stale,
                    inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.HostDefault),
                ),
            ),
        )
        assertEquals(committed, surface.state.value, "a stale update is refused whole, fields included")

        val accepted = assertIs<SurfaceUpdateOutcome.Applied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(
                    SurfaceUpdate(
                        expectedRevision = committed.revision,
                        inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.HostDefault),
                    ),
                ),
            ).value,
        )
        assertEquals(InputDefaultBehavior.HostDefault, accepted.state.inputDefaultBehavior)

        // An update that requests nothing is admitted and commits nothing: `Unchanged` is not a
        // source of revisions.
        val current = surface.state.value
        assertEquals(
            SurfaceUpdateOutcome.Applied(current),
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(surface.apply(SurfaceUpdate())).value,
        )
        assertEquals(current, surface.state.value)

        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * The phase's exit gate, in its pure form: under `HostDefault` the decision is `false` for
     * *every* category, without exception and without a single category being read.
     *
     * The loop runs over the enum rather than over a list written here, so a category added later is
     * covered by this case the moment it exists.
     */
    @Test
    fun hostDefaultSuppressesNoCategoryAtAll() {
        for (category in WebInputCategory.entries) {
            assertFalse(
                shouldSuppress(category, InputDefaultBehavior.HostDefault),
                "the browser keeps its own default for $category while the policy does not ask for " +
                    "its inhibition: suppressing it would be an implicit subtraction from the page",
            )
        }
    }

    /**
     * The same purity for the other behaviour: `SuppressWhenPossible` suppresses exactly the closed
     * set, and the set is enumerated here as well as in the production code.
     *
     * Both sides are pinned: the categories the decision answers `true` for are collected from the
     * enum and compared with the closed set the production code uses, *and* that set is compared with
     * the literal members. Adding a category to one side only — a new member to the set, or a new
     * member to the enum — fails this case.
     */
    @Test
    fun suppressWhenPossibleSuppressesExactlyTheClosedSet() {
        val suppressed = WebInputCategory.entries
            .filter { shouldSuppress(it, InputDefaultBehavior.SuppressWhenPossible) }
            .toSet()

        assertEquals(
            setOf(WebInputCategory.Wheel, WebInputCategory.ScrollingKey),
            SUPPRESSED_INPUT_DEFAULTS,
            "the suppressed set is closed and enumerated: extending it is a deliberate act that fails here",
        )
        assertEquals(SUPPRESSED_INPUT_DEFAULTS, suppressed, "the decision suppresses the closed set and nothing else")
        assertEquals(
            setOf(WebInputCategory.Wheel, WebInputCategory.ScrollingKey, WebInputCategory.Key, WebInputCategory.Pointer, WebInputCategory.Focus),
            WebInputCategory.entries.toSet(),
            "every category this phase observes is enumerated: a new one must be classified against the decision",
        )
        for (category in WebInputCategory.entries - SUPPRESSED_INPUT_DEFAULTS) {
            assertFalse(
                shouldSuppress(category, InputDefaultBehavior.SuppressWhenPossible),
                "$category is outside the closed set: its default is the browser's, not Kadre's to take",
            )
        }
    }

    /**
     * The closed list of keys behind the `ScrollingKey` category, read through the derivation the port
     * feeds.
     *
     * This is the other half of the enumeration: the categories are decided in one place, and which
     * observations *are* the suppressing category is decided in another, so both are pinned against
     * the whole of `NamedKey`. A key added to the list without a line here, or a list silently
     * widened, fails.
     */
    @Test
    fun aKeyPressIsAScrollingKeyOnlyForTheClosedListOfDocumentScrollKeys() {
        val expected = setOf(
            NamedKey.Space,
            NamedKey.ArrowUp,
            NamedKey.ArrowDown,
            NamedKey.ArrowLeft,
            NamedKey.ArrowRight,
            NamedKey.PageUp,
            NamedKey.PageDown,
            NamedKey.Home,
            NamedKey.End,
        )

        val scrolling = NamedKey.entries
            .filter { key -> webInputCategory(namedKeyChanged(key)) == WebInputCategory.ScrollingKey }
            .toSet()

        assertEquals(
            expected,
            scrolling,
            "the keys whose browser default scrolls the document are the arrows, the page keys, Home, " +
                "End and Space — the closed list the code enumerates, in this single place",
        )
        for (key in NamedKey.entries - expected) {
            assertEquals(
                WebInputCategory.Key,
                webInputCategory(namedKeyChanged(key)),
                "$key has no document-scroll default: its browser default is not Kadre's to take",
            )
        }

        // Only the press carries that default: a release of the same key is an ordinary key
        // observation, and a wheel is the reference case of its own category.
        assertEquals(
            WebInputCategory.Key,
            webInputCategory(namedKeyChanged(NamedKey.ArrowDown, KeyState.Released)),
            "a key release scrolls nothing: the document moves on the press alone",
        )
        assertEquals(WebInputCategory.Wheel, webInputCategory(scrolled()))
        assertEquals(WebInputCategory.Pointer, webInputCategory(pointerMoved()))
        assertEquals(WebInputCategory.Focus, webInputCategory(WebInputStimulus.FocusLost))
    }

    /**
     * The whole chain without a browser: an event is delivered, and the answer the port would apply to
     * that event is read from the surface's own effective state.
     *
     * This is the seam a real port uses — ask about the event just handed over, then prevent the
     * default of that event if the answer is yes — driven through the shared port double so the two
     * targets prove it once, in a suite both of them run.
     */
    @Test
    fun theSurfaceAnswersSuppressionForTheEventItWasJustHanded() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val surface = harness.surface()
        val input = surface.input
        val events = mutableListOf<InputEvent>()
        val collector = launch { input.events.collect { events += it } }
        testScheduler.runCurrent()

        assertFalse(
            harness.port.deliverInputAndAskSuppression(scrolled()),
            "under HostDefault the reference category is delivered and its default is left alone",
        )
        assertFalse(
            harness.port.deliverInputAndAskSuppression(namedKeyChanged(NamedKey.ArrowDown)),
            "and so is the key whose default scrolls the document",
        )
        testScheduler.runCurrent()
        assertEquals(
            1,
            events.filterIsInstance<InputEvent.Scrolled>().size,
            "the answer is about the default, never about the delivery: the observation is reduced either way",
        )
        assertEquals(
            setOf(physicalKey("native-ArrowDown")),
            input.state.value.keyboard.pressedKeys,
            "the key was delivered and is pressed: asking did not replace delivering",
        )

        surface.apply(SurfaceUpdate(inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.SuppressWhenPossible)))

        assertTrue(harness.port.deliverInputAndAskSuppression(scrolled()), "the wheel loses its scroll and zoom")
        assertTrue(
            harness.port.deliverInputAndAskSuppression(namedKeyChanged(NamedKey.ArrowUp)),
            "the document-scroll keys lose the scroll of the document",
        )
        assertFalse(
            harness.port.deliverInputAndAskSuppression(namedKeyChanged(NamedKey.ArrowUp, KeyState.Released)),
            "the release of a suppressing key still carries no default of its own",
        )
        assertFalse(
            harness.port.deliverInputAndAskSuppression(namedKeyChanged(NamedKey.Enter)),
            "a key outside the closed list keeps its browser default",
        )
        assertFalse(
            harness.port.deliverInputAndAskSuppression(namedKeyChanged(NamedKey.Tab)),
            "Tab keeps the focus traversal it owns: Kadre does not trap the keyboard",
        )
        assertFalse(
            harness.port.deliverInputAndAskSuppression(pointerMoved()),
            "no pointer observation is ever suppressed by this phase",
        )

        surface.apply(SurfaceUpdate(inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.HostDefault)))
        assertFalse(
            harness.port.deliverInputAndAskSuppression(scrolled()),
            "restoring the default stops the suppression immediately, from the same event on",
        )
        assertFalse(harness.port.deliverInputAndAskSuppression(namedKeyChanged(NamedKey.ArrowDown)))

        collector.cancel()
        harness.stop()
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

        /** One key press of a key the model names, which is what a category is derived from. */
        fun namedKeyChanged(
            key: NamedKey,
            keyState: KeyState = KeyState.Pressed,
        ): WebInputStimulus.KeyChanged = WebInputStimulus.KeyChanged(
            physicalKey = physicalKey("native-${key.name}"),
            logicalKey = LogicalKey.Named(key),
            location = KeyLocation.Standard,
            keyState = keyState,
            repeat = false,
            modifiers = KeyboardModifiers(emptySet()),
        )

        /** One deliverable wheel: the reference event of the suppression decision. */
        fun scrolled(deltaY: Double = 3.0): WebInputStimulus.Scrolled =
            WebInputStimulus.Scrolled(ScrollDelta.Lines(0.0, deltaY), coalescingBoundary = 0L)

        /** One mouse motion, of a category that is never suppressed. */
        fun pointerMoved(): WebInputStimulus.PointerMoved = WebInputStimulus.PointerMoved(
            position = LogicalPoint(2.0, 4.0),
            delta = LogicalDelta(1.0, 1.0),
            pressure = null,
            kind = PointerKind.Mouse,
            pen = null,
        )
    }
}

/** The blanket rejection the three fields this task does not activate still answer with. */
private fun unsupportedUpdateSurface(): Capability.Unsupported =
    Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface))

/** One web session whose primary surface receives the target's input through the shared double. */
private class InputHarness(
    scope: TestScope,
    policy: KadrePolicy = KadrePolicies.Default,
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
                policy = policy,
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
