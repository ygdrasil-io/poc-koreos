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
import org.graphiks.kadre.diagnostics.InteractionFailureReason
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
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
import org.graphiks.kadre.input.TouchPhase
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.policy.ContinuousDelivery
import org.graphiks.kadre.policy.ContinuousOverflowAction
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.RejectedSurfaceField
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceProperty
import org.graphiks.kadre.surface.SurfaceUpdate
import org.graphiks.kadre.surface.SurfaceUpdateOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
 * The last groups cover the two surface-update fields this phase activates.
 * `SurfaceUpdate.inputDefaultBehavior`: the capability that publishes it, the admission and commit of
 * a value through the shared helper, and the suppression decision the port asks the surface for at the
 * point of dispatch — including the phase's exit gate, which is that no category is suppressed under
 * the default. `SurfaceUpdate.pointerCapture`: the capability over the two modes the browser can
 * honour, the ownership a capture is admitted on, the one browser effect a capture decision has
 * (asked of the port and never performed here), the containment of a browser that refuses it, and the
 * reconciliation of a capture the browser ended.
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
    fun keyboardPointerAndTouchAreDeclaredOnlyByTheSurfaceOwnStructuralObservation() = runTest {
        val harness = InputHarness(this, stimuliBeforeInstall = listOf(keyChanged(KEY_A)))
        harness.start()
        val input = harness.surface().input
        val capabilities = input.state.value.capabilities

        assertEquals(FeatureAvailability.Available, capabilities.keyboard)
        assertEquals(FeatureAvailability.Available, capabilities.pointer)
        // Touch is declared by the same structural observation: the ports route the touch contacts
        // their pointer listeners already observe, so the observers exist from the install on — never
        // earlier, never by a stimulus of its own.
        assertEquals(FeatureAvailability.Available, capabilities.touch)
        // Gestures stay `Unsupported(GestureInput)`: no recognizer exists on this target (D-T2), and
        // `gestureKinds` stays empty in the very observation that declares touch. A capability nobody
        // could honour is not published alongside one the ports do honour.
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.GestureInput)),
            capabilities.gestures,
        )
        // Drag-and-drop is declared by the same structural observation: the drag listeners are part
        // of the observation this install belongs to (D-D1/D-D2), so the offers they present are the
        // reducer's to own from the install on — never earlier, never by a stimulus of its own.
        assertEquals(FeatureAvailability.Available, capabilities.dragAndDrop)
        // Text input is declared by the same structural observation now that the session installs the
        // port: the capability is structural (D-X2) — the editability of the element the target lends
        // is the host's boundary the port observes, never a promise it makes — so a surface without an
        // element, or with a non-editable one, opens sessions that simply produce no observations.
        assertEquals(
            Capability.Supported(Unit, FeatureAvailability.Available),
            capabilities.textInput,
        )
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RawInputAccess)),
            capabilities.rawInput,
        )
        // The structural observation is the transition that declared them: the stimulus that waited
        // for the configuration occupied the previous revision, so nothing declared keyboard or
        // pointer before the observation. This is a *transition* proof, not a timeline one:
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

        // No target stimulus can declare a capability: the union has no way of saying so — a touch
        // contact included, whose delivery rides on the same structural observation as the rest.
        harness.port.deliverInput(keyChanged(KEY_B))
        harness.port.deliverInput(WebInputStimulus.PointerEntered(LogicalPoint(1.0, 1.0), kind = PointerKind.Mouse))
        harness.port.deliverInput(
            WebInputStimulus.TouchChanged(
                nativeIdentity = Any(),
                phase = TouchPhase.Started,
                position = LogicalPoint(2.0, 2.0),
                pressure = null,
            ),
        )
        testScheduler.runCurrent()

        assertEquals(capabilities, input.state.value.capabilities, "a stimulus never declares a capability")
        assertEquals(setOf(physicalKey(KEY_A), physicalKey(KEY_B)), input.state.value.keyboard.pressedKeys)

        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * A touch contact becomes a touch state of its own, never a pointer state.
     *
     * The stimulus carries the contact's stable native identity, its phase, its position and its
     * pressure; the reducer allocates the public `TouchId` and keeps the contact on its own lane. What
     * must never happen is the contact reaching `pointers`: the two lists stay disjoint, and a
     * routing that delivered a contact as any pointer stimulus would fail the `pointers` assertion
     * here.
     */
    @Test
    fun aTouchContactBecomesATouchStateWithItsOwnPositionAndPressureAndNeverAPointerState() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val input = harness.surface().input
        val events = mutableListOf<InputEvent>()
        val collector = launch { input.events.collect { events += it } }
        testScheduler.runCurrent()

        val contact = Any()
        harness.port.deliverInput(touchChanged(contact, TouchPhase.Started, LogicalPoint(7.0, 9.0), 0.5))
        harness.port.deliverInput(touchChanged(contact, TouchPhase.Moved, LogicalPoint(11.0, 13.0), 0.25))
        testScheduler.runCurrent()

        val state = input.state.value
        assertEquals(1, state.touches.size, "one contact, one touch state")
        val touch = state.touches.single()
        assertEquals(LogicalPoint(11.0, 13.0), touch.position, "the contact is where the event reported it")
        assertEquals(0.25, touch.pressure, "the pressure the event reported is the pressure the state keeps")
        assertTrue(
            state.pointers.isEmpty(),
            "a contact never becomes a pointer state: `pointers` and `touches` stay disjoint",
        )

        val touchEvents = events.filterIsInstance<InputEvent.TouchChanged>()
        assertEquals(listOf(TouchPhase.Started, TouchPhase.Moved), touchEvents.map { it.phase })
        assertEquals(touch.id, touchEvents.last().touchId, "the event carries the touch identity the state holds")

        collector.cancel()
        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * Two simultaneous contacts are two touches with two distinct stable identities.
     *
     * Each native identity the port holds allocates exactly one public `TouchId` (one per contact,
     * never one per event), and a move of the first contact keeps its own identity while the second
     * contact stays where it was. An allocator that minted an identity per event — or one shared by
     * both contacts — would fail either the distinctness or the stability half.
     */
    @Test
    fun twoSimultaneousContactsProduceTwoDistinctTouchIdsStableAcrossTheirMoves() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val input = harness.surface().input
        val events = mutableListOf<InputEvent>()
        val collector = launch { input.events.collect { events += it } }
        testScheduler.runCurrent()

        val firstContact = Any()
        val secondContact = Any()
        harness.port.deliverInput(touchChanged(firstContact, TouchPhase.Started, LogicalPoint(2.0, 3.0), null))
        harness.port.deliverInput(touchChanged(secondContact, TouchPhase.Started, LogicalPoint(20.0, 30.0), null))
        harness.port.deliverInput(touchChanged(firstContact, TouchPhase.Moved, LogicalPoint(4.0, 5.0), null))
        testScheduler.runCurrent()

        assertEquals(2, input.state.value.touches.size, "two contacts, two touch states")
        val started = events.filterIsInstance<InputEvent.TouchChanged>().filter { it.phase == TouchPhase.Started }
        assertEquals(2, started.size)
        assertNotEquals(
            started[0].touchId,
            started[1].touchId,
            "two contacts are two identities of the model, never one shared between them",
        )
        val firstMoved = events.filterIsInstance<InputEvent.TouchChanged>().last()
        assertEquals(
            started[0].touchId,
            firstMoved.touchId,
            "the move of the first contact carries the identity its own start allocated: stable per contact",
        )
        assertEquals(
            LogicalPoint(20.0, 30.0),
            input.state.value.touches.first { it.id == started[1].touchId }.position,
            "the second contact is untouched by the first contact's move",
        )

        collector.cancel()
        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * The end of a contact retires it: the touch leaves the state, and later events of the same
     * native identity deliver nothing.
     *
     * An `Ended` contact whose `pointerId` then reports another move — a stray event after the
     * browser finished the gesture — must not resurrect it, and a `Cancelled` one behaves the same:
     * the retirement is the reducer's own, pinned here at the Web level.
     */
    @Test
    fun anEndedOrCancelledContactIsRetiredAndNothingIsDeliveredForItAfterwards() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val input = harness.surface().input
        val events = mutableListOf<InputEvent>()
        val collector = launch { input.events.collect { events += it } }
        testScheduler.runCurrent()

        val contact = Any()
        harness.port.deliverInput(touchChanged(contact, TouchPhase.Started, LogicalPoint(1.0, 1.0), null))
        harness.port.deliverInput(touchChanged(contact, TouchPhase.Ended, LogicalPoint(2.0, 2.0), null))
        harness.port.deliverInput(touchChanged(contact, TouchPhase.Moved, LogicalPoint(3.0, 3.0), null))
        testScheduler.runCurrent()

        assertTrue(
            input.state.value.touches.isEmpty(),
            "a contact that ended is not held by the state any more",
        )
        assertEquals(2, events.size, "the move after the end delivers nothing: the contact is retired")

        val cancelled = Any()
        harness.port.deliverInput(touchChanged(cancelled, TouchPhase.Started, LogicalPoint(5.0, 5.0), null))
        harness.port.deliverInput(touchChanged(cancelled, TouchPhase.Cancelled, LogicalPoint(6.0, 6.0), null))
        harness.port.deliverInput(touchChanged(cancelled, TouchPhase.Moved, LogicalPoint(7.0, 7.0), null))
        testScheduler.runCurrent()

        assertTrue(input.state.value.touches.isEmpty(), "a cancelled contact is gone too")
        assertEquals(4, events.size, "and its stray move after the cancellation delivers nothing")

        collector.cancel()
        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * A loss of activation empties `touches` with the rest of the snapshot.
     *
     * The shared reducer neutralises the whole input snapshot on the one transition that leaves
     * Active; the touch lane it keeps rides on that transition like every other lane, and the pin
     * here is the Web-level half of that contract: a contact held when the element's subtree lost
     * focus is gone from the state, once, with one reset published.
     */
    @Test
    fun aLossOfActivationEmptiesTheTouchContactsWithTheRestOfTheSnapshot() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val port = harness.port
        val input = harness.surface().input
        val events = mutableListOf<InputEvent>()
        val collector = launch { input.events.collect { events += it } }
        testScheduler.runCurrent()

        port.deliverInput(touchChanged(Any(), TouchPhase.Started, LogicalPoint(4.0, 6.0), 0.5))
        testScheduler.runCurrent()
        assertEquals(1, input.state.value.touches.size, "the contact is held before the loss")

        port.deliverLifecycle(subtreeBlurredSnapshot())
        testScheduler.runCurrent()

        val neutral = input.state.value
        assertTrue(neutral.touches.isEmpty(), "the loss of activation emptied the contacts with the snapshot")
        assertTrue(neutral.pointers.isEmpty())
        val resets = events.filterIsInstance<InputEvent.StateReset>()
        assertEquals(1, resets.size, "one loss, one reset: the touch lane adds no reset of its own")
        assertEquals(InputStateResetReason.FocusLost, resets.single().reason)
        // The capability describes the structural installation: still available after the loss.
        assertEquals(FeatureAvailability.Available, neutral.capabilities.touch)

        collector.cancel()
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

        // Scope non-regression: this task activates `pointerCapture` and nothing else (its own cases are
        // below). The three fields it does not touch keep the blanket rejection of the previous phase,
        // byte for byte.
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
     * Nothing disappears: every field of an update is either committed or reported, and an update that
     * asks for all four moves exactly one of them.
     *
     * The guard this case covers is inside `apply` — a field that arrives *admitted* with no commit
     * path on this surface fails loudly rather than being dropped in silence and answered with an
     * `Applied` for a state that never took it. That guard cannot be reached from a test today: the
     * two fields it protects (`cursor` and `hitTesting`) carry a blanket `Unsupported(UpdateSurface)`
     * capability, which the capability case pins, so `admitField` answers `Unchanged` for every value
     * they can carry, and reaching the guard would mean publishing a capability this surface does not
     * have. What is pinned here is its observable half, which is what a consumer sees: a field the
     * surface cannot commit is *always* reported, and the only state a mixed update moves is the one
     * it can commit.
     *
     * `pointerCapture` is the field this task gave a commit path, and it is refused here for the reason
     * of its own rule rather than for a missing path: no pointer is owned in this update, so a
     * `Confined` capture has nothing to confine (`InteractionRequired(Missing)`, the failure
     * `OPERATION-CONTRACTS.md` admits on the rejected-field row of `HostSurface.apply`). It is still a
     * *rejection*, which is what this case is about — and it is still reported rather than dropped.
     */
    @Test
    fun everyFieldOfAnUpdateIsEitherCommittedOrReported() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val surface = harness.surface()
        val before = surface.state.value

        val partiallyApplied = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(
                    SurfaceUpdate(
                        cursor = PropertyChange.Set(CursorStyle.Hidden),
                        pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined),
                        hitTesting = PropertyChange.Set(HitTestingMode.Disabled),
                        inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.SuppressWhenPossible),
                    ),
                ),
            ).value,
        )

        assertEquals(
            listOf(
                SurfaceProperty.Cursor,
                SurfaceProperty.PointerCapture,
                SurfaceProperty.HitTesting,
            ),
            partiallyApplied.rejected.map { it.field },
            "all three fields this update cannot commit are reported, in the order of the update",
        )
        assertEquals(
            listOf(
                KadreFailure.Unsupported(KadreOperation.UpdateSurface),
                KadreFailure.InteractionRequired(InteractionFailureReason.Missing),
                KadreFailure.Unsupported(KadreOperation.UpdateSurface),
            ),
            partiallyApplied.rejected.map { it.failure },
            "each rejection carries the failure of its own rule: a blank capability, or a capture no " +
                "pointer of this surface could carry",
        )
        assertEquals(before.cursor, partiallyApplied.state.cursor, "no rejected field is committed")
        assertEquals(before.pointerCapture, partiallyApplied.state.pointerCapture)
        assertEquals(before.hitTesting, partiallyApplied.state.hitTesting)
        assertEquals(CursorStyle.System(CursorIcon.Default), partiallyApplied.state.cursor)
        assertEquals(PointerCaptureMode.None, partiallyApplied.state.pointerCapture)
        assertEquals(HitTestingMode.Enabled, partiallyApplied.state.hitTesting)
        assertEquals(
            InputDefaultBehavior.SuppressWhenPossible,
            partiallyApplied.state.inputDefaultBehavior,
            "the one field with a commit path is committed",
        )
        assertEquals(
            before.revision.value + 1L,
            partiallyApplied.state.revision.value,
            "three rejections and one commit move the revision exactly once",
        )

        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * The capture capability is the two modes this backend can honour, and `Locked` is provably outside.
     *
     * `None` and `Confined` are named one by one — the capability is this backend's promise, so the
     * promise is written out rather than derived from the enum — and the set is pinned against
     * `PointerCaptureMode.entries` in both directions: the modes the capability claims are exactly the
     * ones the rule of the commit admits, and the difference between the enum and the set is exactly
     * `Locked`. A fourth member of the enum therefore fails this case instead of being claimed
     * silently, and the third one is named as the mode that is *not* promised.
     *
     * `Locked` is not a gap in this phase's work but its scope: the Pointer Lock API needs a transient
     * user activation and belongs to `InteractionAction.LockPointer` in a later phase (`DESIGN.md`
     * §9.6), which is why no part of this backend can name it — the constraint set here, the commit
     * rule pinned with it, and `apply` all refuse it, and the capture cases below pin that nothing of
     * it ever reaches the browser.
     *
     * The same case closes the other end of the promise: once the surface stopped admitting, the whole
     * snapshot is unavailable (`DESIGN.md:703`), so no mode is claimed by a surface that could not act
     * on one.
     */
    @Test
    fun thePointerCaptureCapabilityIsNoneAndConfinedAndLockedIsProvablyOutside() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val capabilities = harness.surface().capabilities.value

        val pointerCapture = assertIs<Capability.Supported<Set<PointerCaptureMode>>>(capabilities.pointerCapture)
        assertEquals(
            setOf(PointerCaptureMode.None, PointerCaptureMode.Confined),
            pointerCapture.constraints,
            "the two modes this backend can honour are named one by one, not derived from the enum",
        )
        assertEquals(FeatureAvailability.Available, pointerCapture.availability)
        assertEquals(
            pointerCapture.constraints,
            PointerCaptureMode.entries.filter { webPointerCaptureIsHonourable(it) }.toSet(),
            "the set the capability promises is exactly the set the commit rule admits: a mode the " +
                "surface cannot honour cannot be claimed here",
        )
        assertEquals(
            setOf(PointerCaptureMode.Locked),
            PointerCaptureMode.entries.toSet() - pointerCapture.constraints,
            "and the one mode outside the promise is exactly `Locked`: a fourth member would widen this " +
                "difference and fail here rather than be claimed in silence",
        )

        // Scope non-regression: this task activates `pointerCapture`, and the three fields it does not
        // touch keep the blanket rejection of the previous phase.
        assertEquals(unsupportedUpdateSurface(), capabilities.cursor)
        assertEquals(unsupportedUpdateSurface(), capabilities.customCursor)
        assertEquals(unsupportedUpdateSurface(), capabilities.hitTesting)

        // A detached surface claims nothing at all, this field included.
        harness.port.deliverLifecycle(harness.port.disconnectedSnapshot())
        testScheduler.advanceUntilIdle()

        assertEquals(SurfaceAttachmentState.Detached, harness.surface().state.value.attachment)
        assertEquals(
            unsupportedUpdateSurface(),
            harness.surface().capabilities.value.pointerCapture,
            "the terminal snapshot is the all-unsupported one, as the reference publishes it",
        )

        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * The ownership rule itself, as the rule it is: a pointer is owned from the press the element
     * observed to the release it observed, and a leave, a cancellation or a loss of activation ends it
     * with the pointer.
     *
     * Ownership is the *only* thing a capture is admitted on, so what it is worth is what it counts: the
     * surface owns a pointer while any button of it is down, a release of one button among several is
     * not a release of the pointer, and every way a pointer stops being one — leaving, being cancelled
     * (`pointercancel` is what the ports deliver for it) or losing activation — ends it.
     */
    @Test
    fun aPointerIsOwnedFromThePressToTheReleaseAndEveryOtherEndingOfIt() {
        val ownership = WebPointerOwnership()
        assertFalse(ownership.isOwned, "a surface that observed nothing owns nothing")

        ownership.observe(pointerButton(PointerButton.Primary, PointerButtonState.Pressed))
        assertTrue(ownership.isOwned, "a press of a button is what makes the pointer the surface holds")
        ownership.observe(pointerMoved())
        assertTrue(ownership.isOwned, "a motion moves a pointer the surface already holds; it does not begin or end one")

        ownership.observe(pointerButton(PointerButton.Secondary, PointerButtonState.Pressed))
        ownership.observe(pointerButton(PointerButton.Secondary, PointerButtonState.Released))
        assertTrue(
            ownership.isOwned,
            "the primary button is still down: releasing another one is not releasing the pointer",
        )

        ownership.observe(pointerButton(PointerButton.Primary, PointerButtonState.Released))
        assertFalse(ownership.isOwned, "the last pressed button released ends the ownership")

        ownership.observe(pointerButton(PointerButton.Primary, PointerButtonState.Pressed))
        ownership.observe(WebInputStimulus.PointerLeft(kind = PointerKind.Mouse))
        assertFalse(ownership.isOwned, "a pointer that left is not held, whatever its buttons said before")

        ownership.observe(pointerButton(PointerButton.Primary, PointerButtonState.Pressed))
        ownership.observe(WebInputStimulus.FocusLost)
        assertFalse(
            ownership.isOwned,
            "a loss of activation neutralises the snapshot the ownership describes, so it ends it too",
        )

        ownership.observe(pointerButton(PointerButton.Primary, PointerButtonState.Pressed))
        ownership.clear()
        assertFalse(ownership.isOwned, "a surface that stopped admitting holds nothing")
    }

    /**
     * The capture is committed for a pointer the surface owns, and the browser is asked for it through
     * the port — once, for that capture.
     *
     * The port is the only layer that can touch the DOM, so the proof that the decision reached the
     * browser is the double's own record of what it was asked for: `true` is the one request of a
     * `Confined` capture, and its absence in the cases below is the one proof that a rejected field
     * never became a browser call. The state is committed here and now — there is no backend below this
     * surface that could report a different effective value — with the one revision a state change
     * owns.
     */
    @Test
    fun takingTheCaptureOfAnOwnedPointerIsCommittedAndAsksThePortForIt() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val surface = harness.surface()
        val port = harness.port
        port.deliverInput(pointerButton(PointerButton.Primary, PointerButtonState.Pressed))
        testScheduler.runCurrent()
        val before = surface.state.value
        assertEquals(
            PointerCaptureMode.None,
            before.pointerCapture,
            "a surface starts without a capture, which is the state the request moves",
        )

        val applied = assertIs<SurfaceUpdateOutcome.Applied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined))),
            ).value,
        )

        assertEquals(PointerCaptureMode.Confined, applied.state.pointerCapture)
        assertEquals(
            before.revision.value + 1L,
            applied.state.revision.value,
            "the effective capture is committed with one new revision",
        )
        assertEquals(applied.state, surface.state.value, "the outcome carries the state the surface publishes")
        assertEquals(
            listOf(true),
            port.pointerCaptureRequests,
            "the port was asked to capture the pointer the surface holds — and asked nothing else",
        )

        // The value already in effect is admitted and answered with the state it already has: nothing
        // moves, so nothing is asked of the browser a second time either.
        val again = assertIs<SurfaceUpdateOutcome.Applied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined))),
            ).value,
        )
        assertEquals(PointerCaptureMode.Confined, again.state.pointerCapture)
        assertEquals(applied.state.revision.value, again.state.revision.value)
        assertEquals(listOf(true), port.pointerCaptureRequests)

        harness.stop()
        testScheduler.runCurrent()
    }

    /** The way back: `None` releases the capture the surface holds, and the browser is asked to release it. */
    @Test
    fun releasingTheCaptureIsCommittedAndAsksThePortToReleaseIt() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val surface = harness.surface()
        val port = harness.port
        port.deliverInput(pointerButton(PointerButton.Primary, PointerButtonState.Pressed))
        surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined)))
        val confined = surface.state.value
        assertEquals(PointerCaptureMode.Confined, confined.pointerCapture)

        val released = assertIs<SurfaceUpdateOutcome.Applied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.None))),
            ).value,
        )

        assertEquals(PointerCaptureMode.None, released.state.pointerCapture)
        assertEquals(confined.revision.value + 1L, released.state.revision.value)
        assertEquals(PointerCaptureMode.None, surface.state.value.pointerCapture)
        assertEquals(
            listOf(true, false),
            port.pointerCaptureRequests,
            "the capture was taken and then released, in that order, through the port alone",
        )

        // Releasing a capture that is not held has nothing to do in the browser: the field is admitted,
        // the state it asks for is already in effect, and the revision stays where it is.
        val again = assertIs<SurfaceUpdateOutcome.Applied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.None))),
            ).value,
        )
        assertEquals(released.state, again.state)
        assertEquals(listOf(true, false), port.pointerCaptureRequests)

        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * A capture is refused when the surface owns no pointer, and the refusal costs the browser nothing.
     *
     * This is D13's ownership rule, and the failure is the one the contract admits for it: a
     * `Confined` capture needs a pointer this surface observed pressed and has not seen released, and
     * without one there is nothing to confine — `InteractionRequired(Missing)`, the reason
     * `OPERATION-CONTRACTS.md` §3 admits on the rejected-field row of `HostSurface.apply`. The two ways
     * of not owning one are covered: a surface that observed no pointer at all, and one whose pointer
     * was released again.
     *
     * What the state does is the point of the case: the field is rejected, so the state does not move,
     * the revision does not move (a rejection is not a change), and the port is asked *nothing* — a
     * browser call for a field the surface refused would be an effect no decision ever authorised.
     */
    @Test
    fun aCaptureIsRefusedWithoutAnOwnedPointerAndAsksThePortNothing() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val surface = harness.surface()
        val port = harness.port
        val before = surface.state.value

        val withoutAPointer = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined))),
            ).value,
        )
        assertEquals(
            listOf(
                RejectedSurfaceField(
                    SurfaceProperty.PointerCapture,
                    KadreFailure.InteractionRequired(InteractionFailureReason.Missing),
                ),
            ),
            withoutAPointer.rejected,
            "nothing was pressed on this surface, so nothing can be confined to it",
        )
        assertEquals(before.pointerCapture, withoutAPointer.state.pointerCapture)
        assertEquals(before.revision.value, withoutAPointer.state.revision.value, "a rejection moves no revision")
        assertTrue(
            port.pointerCaptureRequests.isEmpty(),
            "the browser was asked nothing for a field the surface refused: ${port.pointerCaptureRequests}",
        )

        // The same field, on a pointer the surface held and then saw released: ownership ends with the
        // release, and so does the eligibility of a capture of that pointer.
        port.deliverInput(pointerButton(PointerButton.Primary, PointerButtonState.Pressed))
        port.deliverInput(pointerButton(PointerButton.Primary, PointerButtonState.Released))
        testScheduler.runCurrent()
        val afterTheRelease = surface.state.value

        val releasedPointer = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined))),
            ).value,
        )
        assertEquals(
            listOf(
                RejectedSurfaceField(
                    SurfaceProperty.PointerCapture,
                    KadreFailure.InteractionRequired(InteractionFailureReason.Missing),
                ),
            ),
            releasedPointer.rejected,
            "a pointer that was pressed and released is not held any more",
        )
        assertEquals(afterTheRelease.pointerCapture, releasedPointer.state.pointerCapture)
        assertEquals(afterTheRelease.revision.value, releasedPointer.state.revision.value)
        assertTrue(port.pointerCaptureRequests.isEmpty())

        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * `Locked` is refused as a field, and nothing of it ever reaches the browser.
     *
     * The Pointer Lock API is not this phase's and not this backend's: it needs a transient user
     * activation, it belongs to `InteractionAction.LockPointer` (`DESIGN.md` §9.6), and this port has no
     * member that could ask for it at all. So the mode is refused twice over — by the capability's
     * constraint set, which the shared admission helper turns into `Unsupported(UpdateSurface)`, and by
     * the commit rule that would refuse it even if the capability claimed it. The port answers nothing
     * here, which is the observable half of "no pointer-lock call may ever be made".
     */
    @Test
    fun lockedIsRefusedAsAFieldAndNeverReachesThePort() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val surface = harness.surface()
        val port = harness.port
        val before = surface.state.value
        // The pointer is held on purpose: had `Locked` been admitted, the ownership its own rule asks
        // for is the one thing this surface could have brought to it.
        port.deliverInput(pointerButton(PointerButton.Primary, PointerButtonState.Pressed))
        testScheduler.runCurrent()
        val held = surface.state.value

        val refused = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Locked))),
            ).value,
        )
        assertEquals(
            listOf(
                RejectedSurfaceField(
                    SurfaceProperty.PointerCapture,
                    KadreFailure.Unsupported(KadreOperation.UpdateSurface),
                ),
            ),
            refused.rejected,
            "the mode the capability does not carry is refused as a field, with the shared helper's own failure",
        )
        assertEquals(before.pointerCapture, refused.state.pointerCapture)
        assertEquals(held.revision.value, refused.state.revision.value, "the refusal moves nothing at all")
        assertTrue(
            port.pointerCaptureRequests.isEmpty(),
            "the browser was asked nothing: no pointer-lock call may ever be made, and no capture either",
        )

        // A mixed update is refused per field: `Locked` is reported while the field that can be honoured
        // is still committed, which is the reference's own field-by-field behaviour.
        val mixed = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(
                    SurfaceUpdate(
                        pointerCapture = PropertyChange.Set(PointerCaptureMode.Locked),
                        inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.SuppressWhenPossible),
                    ),
                ),
            ).value,
        )
        assertEquals(listOf(SurfaceProperty.PointerCapture), mixed.rejected.map { it.field })
        assertEquals(
            InputDefaultBehavior.SuppressWhenPossible,
            mixed.state.inputDefaultBehavior,
            "the field that can be honoured is committed even though the other one was refused",
        )
        assertTrue(port.pointerCaptureRequests.isEmpty())

        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * A browser that refuses the effect is a rejected field, never an exception and never a silent
     * success.
     *
     * `setPointerCapture` throws for a pointer the browser does not consider active, and the port asks
     * it from inside the DOM callback of the event that led to the decision — so the port contains the
     * error and reports it (`WebHostPort.applyPointerCapture`), and what this surface does with the
     * report is the rule this case pins: the field is rejected with the failure the port returned,
     * which `OPERATION-CONTRACTS.md` §3 admits on the rejected-field row, and the state stays where it
     * was. Answering `Applied` for a capture the browser never took would be the fictitious success this
     * whole admission path exists to prevent.
     */
    @Test
    fun aBrowserThatRefusesTheCaptureYieldsARejectedFieldAndMovesNothing() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val surface = harness.surface()
        val port = harness.port
        val refusal = KadreFailure.PlatformFailure(KadrePlatform.Web, "web-host", "pointer-capture-failed")
        port.pointerCaptureFailure = refusal
        port.deliverInput(pointerButton(PointerButton.Primary, PointerButtonState.Pressed))
        testScheduler.runCurrent()
        val before = surface.state.value

        val refused = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined))),
            ).value,
        )

        assertEquals(
            listOf(RejectedSurfaceField(SurfaceProperty.PointerCapture, refusal)),
            refused.rejected,
            "the browser's own refusal is what the field reports, unchanged",
        )
        assertEquals(PointerCaptureMode.None, refused.state.pointerCapture, "a capture the browser refused is not committed")
        assertEquals(before.revision.value, refused.state.revision.value, "and it moves no revision")
        assertEquals(
            listOf(true),
            port.pointerCaptureRequests,
            "the port was asked exactly once: the refusal is reported, not retried behind the caller's back",
        )

        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * A capture the browser ended returns the state to `None`, and the pointer it belonged to leaves
     * nothing pressed behind.
     *
     * Two things end a capture the surface had committed, and both are the browser's own word:
     * `lostpointercapture` — reported to the surface by the port, because it is not an input
     * observation and the shared union has no member for it — and the revocation of the contact, which
     * the ports deliver as the pointer exit that `pointercancel` is. The state must follow both: a
     * `Confined` capture the browser no longer applies is a claim about a confinement nobody holds, and
     * the revision moves once, as it does for any state change.
     *
     * The snapshot stays the reducer's: the reconciliation commits the surface's own capture and
     * touches no input state. What it may not do is leave the input claiming a button that nothing
     * presses any more, which is what the end of the case pins — after the cancellation the pointer is
     * gone from the snapshot, with every button it held, and the ownership is gone with it, so a later
     * capture of that pointer is refused.
     */
    @Test
    fun aLostCaptureAndACancelledPointerReturnTheStateToNoneWithoutAStuckButton() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val surface = harness.surface()
        val port = harness.port
        port.deliverInput(pointerButton(PointerButton.Primary, PointerButtonState.Pressed))
        surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined)))
        val confined = surface.state.value
        assertEquals(PointerCaptureMode.Confined, confined.pointerCapture)

        // `lostpointercapture`: the browser says it no longer confines the pointer to this element.
        port.deliverPointerCaptureLost()
        testScheduler.runCurrent()

        val lost = surface.state.value
        assertEquals(
            PointerCaptureMode.None,
            lost.pointerCapture,
            "the state may not keep claiming a capture the browser ended",
        )
        assertEquals(confined.revision.value + 1L, lost.revision.value, "the reconciliation is a state change")
        assertEquals(
            setOf(PointerButton.Primary),
            surface.input.state.value.pointers.flatMap { it.pressedButtons }.toSet(),
            "the loss of a capture is not a release of the button: the pointer is still down on the element",
        )

        // The pointer is still held, so the capture may be taken again for it — and it is asked for
        // again, which is what makes the reconciliation a state the consumer can react to.
        assertIs<SurfaceUpdateOutcome.Applied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined))),
            ).value,
        )
        val recaptured = surface.state.value
        assertEquals(PointerCaptureMode.Confined, recaptured.pointerCapture)
        assertEquals(listOf(true, true), port.pointerCaptureRequests)

        // `pointercancel`: the browser revoked the contact, which the ports deliver as the pointer exit
        // the reducer drops the pointer with.
        port.deliverInput(WebInputStimulus.PointerLeft(kind = PointerKind.Mouse))
        testScheduler.runCurrent()

        val cancelled = surface.state.value
        assertEquals(
            PointerCaptureMode.None,
            cancelled.pointerCapture,
            "a revoked contact releases the capture with the pointer it belonged to",
        )
        assertEquals(
            recaptured.revision.value + 1L,
            cancelled.revision.value,
            "the revocation reconciles the capture with exactly one revision, like every other state change",
        )
        assertTrue(
            surface.input.state.value.pointers.isEmpty(),
            "the cancelled pointer is dropped from the snapshot, with every button it held",
        )
        assertEquals(
            emptySet(),
            surface.input.state.value.pointers.flatMap { it.pressedButtons }.toSet(),
            "no button is left pressed by a contact the browser revoked",
        )

        // Ownership went with the pointer, so the capture that needed it is refused again.
        val afterTheCancel = surface.state.value
        val refused = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined))),
            ).value,
        )
        assertEquals(
            listOf(
                RejectedSurfaceField(
                    SurfaceProperty.PointerCapture,
                    KadreFailure.InteractionRequired(InteractionFailureReason.Missing),
                ),
            ),
            refused.rejected,
        )
        assertEquals(afterTheCancel.revision.value, refused.state.revision.value)
        assertEquals(listOf(true, true), port.pointerCaptureRequests, "a refused capture asks the browser nothing")

        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * A port that does not implement the capture mechanism cannot make this surface claim a capture.
     *
     * `WebHostPort.applyPointerCapture` is the one member of that interface whose default is *not* an
     * inert success: the surface commits `Confined` on a `Success`, so a port that performs nothing and
     * answers `Success` would have the surface publish a confinement the browser never took. The default
     * is therefore a failure — `Unsupported(UpdateSurface)`, what the capability would have said had it
     * been honest — and this case is its guard: with the mechanism unimplemented, a `Confined` request
     * on a pointer the surface holds is refused, and no state claims it.
     */
    @Test
    fun aPortThatDoesNotImplementCaptureCannotMakeTheSurfaceClaimOne() = runTest {
        val harness = InputHarness(this)
        harness.port.captureImplemented = false
        harness.start()
        val surface = harness.surface()
        val port = harness.port
        port.deliverInput(pointerButton(PointerButton.Primary, PointerButtonState.Pressed))
        testScheduler.runCurrent()
        val before = surface.state.value

        val refused = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined))),
            ).value,
        )

        assertEquals(
            listOf(
                RejectedSurfaceField(
                    SurfaceProperty.PointerCapture,
                    KadreFailure.Unsupported(KadreOperation.UpdateSurface),
                ),
            ),
            refused.rejected,
            "a port that performs no capture is refused, never believed",
        )
        assertEquals(
            PointerCaptureMode.None,
            refused.state.pointerCapture,
            "and no confinement nobody took is published",
        )
        assertEquals(before.revision.value, refused.state.revision.value)

        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * A refusal produced at commit time still reads in the order of the update's fields.
     *
     * This surface refuses fields in two passes — the capability and the ownership at admission, the
     * browser at commit — and the rejected list is a consumer-visible ordering: the reference emits it in
     * the order it walks its four fields (`MinimalWindowSurface.commitUpdateLocked`), and
     * `OPERATION-CONTRACTS.md` §1.1 names those fields in that same order. So a `PointerCapture` the
     * browser refused *after* two fields were refused at admission must sit between them, not after
     * them, which is what merging the two passes by the field order guarantees.
     */
    @Test
    fun aCommitTimeRejectionReadsInTheOrderOfTheUpdatesFields() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val surface = harness.surface()
        val port = harness.port
        val refusal = KadreFailure.PlatformFailure(KadrePlatform.Web, "web-host", "pointer-capture-failed")
        port.pointerCaptureFailure = refusal
        port.deliverInput(pointerButton(PointerButton.Primary, PointerButtonState.Pressed))
        testScheduler.runCurrent()
        val before = surface.state.value

        val partiallyApplied = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(
                    SurfaceUpdate(
                        cursor = PropertyChange.Set(CursorStyle.Hidden),
                        pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined),
                        hitTesting = PropertyChange.Set(HitTestingMode.Disabled),
                    ),
                ),
            ).value,
        )

        assertEquals(
            listOf(
                SurfaceProperty.Cursor,
                SurfaceProperty.PointerCapture,
                SurfaceProperty.HitTesting,
            ),
            partiallyApplied.rejected.map { it.field },
            "the browser refused the capture after admission had refused the two other fields, and the " +
                "list still reads in the order the update wrote them",
        )
        assertEquals(
            listOf(
                KadreFailure.Unsupported(KadreOperation.UpdateSurface),
                refusal,
                KadreFailure.Unsupported(KadreOperation.UpdateSurface),
            ),
            partiallyApplied.rejected.map { it.failure },
        )
        assertEquals(PointerCaptureMode.None, partiallyApplied.state.pointerCapture)
        assertEquals(before.revision.value, partiallyApplied.state.revision.value)
        assertEquals(listOf(true), port.pointerCaptureRequests)

        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * A port failure outside the closed set is replaced, and the misbehaviour is reported rather than
     * returned.
     *
     * `OPERATION-CONTRACTS.md` §3 admits exactly six failures for a rejected `HostSurface.apply` field,
     * and a port is below this surface, so its answer is checked against that set through the runtime's
     * own `normaliseFieldFailure` — the same guard the reference applies to a port's field outcome
     * (`MinimalWindowSurface.commitField`). A failure the set does not admit (`Closed(Surface)` here, an
     * outer failure a port has no business returning for a field) becomes the platform failure of an
     * invalid port answer, which is what the caller receives; the adapter failure that names the bug is
     * reported to the session, never returned. A port failure that *is* admissible passes through
     * unchanged, which the refusal case above pins for this task's own `PlatformFailure`.
     */
    @Test
    fun aPortFailureOutsideTheClosedSetIsReplacedAndReported() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val surface = harness.surface()
        val port = harness.port
        val adapterFailure = KadreFailure.PlatformFailure(
            KadrePlatform.Web,
            "surface-command-port",
            "invalid-field-failure",
        )
        port.pointerCaptureFailure = KadreFailure.Closed(KadreResourceKind.Surface)
        port.deliverInput(pointerButton(PointerButton.Primary, PointerButtonState.Pressed))
        testScheduler.runCurrent()
        val before = surface.state.value

        val refused = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined))),
            ).value,
        )

        assertEquals(
            listOf(RejectedSurfaceField(SurfaceProperty.PointerCapture, adapterFailure)),
            refused.rejected,
            "a port may not inject a failure its operation does not admit: what the caller reads is the " +
                "normalised one",
        )
        assertEquals(PointerCaptureMode.None, refused.state.pointerCapture)
        assertEquals(before.revision.value, refused.state.revision.value)
        assertEquals(
            listOf(adapterFailure),
            harness.reportedFailures.map { assertIs<KadreException>(it).failure },
            "the port's misbehaviour is a diagnosis of the session, not a failure of the caller's call",
        )

        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * The other ending of a pointer: the last pressed button released.
     *
     * The `lostpointercapture` and `pointercancel` arms are the browser's word that something ended; this
     * one is the surface's own reading of a button transition, and it is the arm a real mouse takes every
     * time — the DOM releases a capture implicitly when the pointer stops being pressed, so the state
     * must follow the pointer without ever asking the browser for anything. The rule is stated over the
     * buttons rather than over the pointer, which is why the release of one button of two moves nothing,
     * and the last one ends both the ownership and the capture it carried: a later `Confined` request has
     * no pointer left to be admitted on, and the input snapshot keeps the pointer with no button pressed.
     */
    @Test
    fun theLastReleasedButtonEndsTheCaptureWithThePointer() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val surface = harness.surface()
        val port = harness.port
        port.deliverInput(pointerButton(PointerButton.Primary, PointerButtonState.Pressed))
        port.deliverInput(pointerButton(PointerButton.Secondary, PointerButtonState.Pressed))
        surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined)))
        val confined = surface.state.value
        assertEquals(PointerCaptureMode.Confined, confined.pointerCapture)

        // One button released of two: the pointer is still down on the element, so it is still held and
        // its capture is untouched.
        port.deliverInput(pointerButton(PointerButton.Secondary, PointerButtonState.Released))
        testScheduler.runCurrent()
        assertEquals(
            confined,
            surface.state.value,
            "a release of one button among several ends neither the pointer nor the capture it carries",
        )

        // The last button released: the pointer is gone, and the capture goes with it.
        port.deliverInput(pointerButton(PointerButton.Primary, PointerButtonState.Released))
        testScheduler.runCurrent()

        val released = surface.state.value
        assertEquals(
            PointerCaptureMode.None,
            released.pointerCapture,
            "the browser ends the capture with the pointer, so this surface may not keep claiming it",
        )
        assertEquals(
            confined.revision.value + 1L,
            released.revision.value,
            "the last release reconciles the capture with exactly one revision",
        )
        assertEquals(
            emptySet(),
            surface.input.state.value.pointers.flatMap { it.pressedButtons }.toSet(),
            "no button is left pressed by the pointer whose last one was released",
        )
        assertEquals(
            listOf(true),
            port.pointerCaptureRequests,
            "nothing is asked of the browser: the release it performed implicitly is not released twice",
        )

        // Ownership went with the pointer, so the capture that needed it is refused exactly as if no
        // pointer had ever been observed.
        val afterTheRelease = surface.state.value
        val refused = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined))),
            ).value,
        )
        assertEquals(
            listOf(
                RejectedSurfaceField(
                    SurfaceProperty.PointerCapture,
                    KadreFailure.InteractionRequired(InteractionFailureReason.Missing),
                ),
            ),
            refused.rejected,
        )
        assertEquals(afterTheRelease.revision.value, refused.state.revision.value)
        assertEquals(listOf(true), port.pointerCaptureRequests)

        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * The revocation window claims nothing: the capabilities are already the unavailable snapshot
     * while the surface is still attached and the port is already gone.
     *
     * The window is the one a cooperative stop leaves — the owner revoked the surface, so every
     * admission site answers `Closed` and the suppression answers `false`, while the runtime still has
     * to close the surface. A capability published there would be a promise no operation could honour,
     * which is why the revocation publishes the terminal all-unsupported snapshot instead of waiting
     * for the detached state. The teardown suite pins the same order for `platformAccess` ("no later
     * than the detached state"); this case pins it for the field this task activated.
     */
    @Test
    fun theRevocationWindowWithdrawsTheFieldBeforeTheTerminalState() = runTest {
        val harness = InputHarness(this)
        harness.start()
        val surface = harness.surface()
        surface.apply(
            SurfaceUpdate(inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.SuppressWhenPossible)),
        )

        var inputDefaultBehaviorAtRelease: Capability.Unsupported? = null
        harness.port.onRelease = {
            inputDefaultBehaviorAtRelease = assertIs<Capability.Unsupported>(
                surface.capabilities.value.inputDefaultBehavior,
            )
        }

        // The window under test: the stop releases the port before the runtime closes the surface.
        harness.stop()
        assertEquals(
            SurfaceAttachmentState.Attached,
            surface.state.value.attachment,
            "the surface is still attached when the port goes, so the capability still describes it",
        )
        assertEquals(
            unsupportedUpdateSurface(),
            inputDefaultBehaviorAtRelease,
            "the revocation withdraws the field with the admission it closes",
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface)),
            surface.apply(
                SurfaceUpdate(inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.HostDefault)),
            ),
            "and no call the withdrawn capability describes can be honoured in that window",
        )
        assertFalse(
            harness.port.deliverInputAndAskSuppression(scrolled()),
            "nor does the surface answer suppression for an event observed in it",
        )

        testScheduler.advanceUntilIdle()

        assertEquals(SurfaceAttachmentState.Detached, surface.state.value.attachment)
        assertEquals(unsupportedUpdateSurface(), surface.capabilities.value.inputDefaultBehavior)
        testScheduler.runCurrent()
    }

    /**
     * The two requests the reference refuses before admission, refused here with the same failures and
     * in the same order.
     *
     * A `Clear` has no meaning for these fields — none of them has an "unset" value — so it is an
     * `InvalidRequest` naming the field, and the call has no effect at all. An expected revision that
     * is not the current one is a `StaleRevision`, the failure the contract reserves for it; without
     * that check a caller doing optimistic concurrency would be told `Applied` for an update it
     * computed against a state that no longer exists. The revision is checked first, as it is on the
     * reference surface (`MinimalWindowSurface.kt:269-278`), so a request that is both stale and
     * malformed is answered as stale.
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

        // The precedence is the reference's: the revision first, so a request that is both stale and
        // malformed is answered with the revision failure rather than with the field one.
        assertEquals(
            KadreResult.Failure(KadreFailure.StaleRevision(stale.value, committed.revision.value)),
            surface.apply(SurfaceUpdate(expectedRevision = stale, cursor = PropertyChange.Clear)),
            "a stale request is stale whatever else it asks for",
        )
        assertEquals(committed, surface.state.value)

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
            setOf(
                WebInputCategory.Wheel,
                WebInputCategory.ScrollingKey,
                WebInputCategory.Key,
                WebInputCategory.Pointer,
                WebInputCategory.Touch,
                WebInputCategory.Focus,
                WebInputCategory.Drop,
            ),
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
        assertEquals(
            WebInputCategory.Touch,
            webInputCategory(touchChanged(Any(), TouchPhase.Started, LogicalPoint(1.0, 1.0), null)),
            "a touch contact is a category of its own: it is not a pointer observation",
        )
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

        /**
         * One button transition of the mouse the runtime keeps a single identity for (D11).
         *
         * Presses and releases of the same pointer are the two facts ownership is made of, and the
         * button is carried so a release of one button among several can be told from the release of
         * the pointer.
         */
        fun pointerButton(
            button: PointerButton,
            buttonState: PointerButtonState,
        ): WebInputStimulus.PointerButtonChanged = WebInputStimulus.PointerButtonChanged(
            button = button,
            buttonState = buttonState,
            position = LogicalPoint(3.0, 6.0),
            pressure = null,
            kind = PointerKind.Mouse,
            pen = null,
        )

        /**
         * One touch contact observation of the given [phase], for the stable native identity a port
         * holds per contact.
         */
        fun touchChanged(
            nativeIdentity: Any,
            phase: TouchPhase,
            position: LogicalPoint,
            pressure: Double?,
        ): WebInputStimulus.TouchChanged = WebInputStimulus.TouchChanged(
            nativeIdentity = nativeIdentity,
            phase = phase,
            position = position,
            pressure = pressure,
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

    /**
     * Every cause the session's failure reporter was handed.
     *
     * A diagnosis is *reported*, never returned (`MinimalWindowSurface.reportAdapterFailure`), so this
     * is the only place a case can observe what a surface told the session about a port that misbehaved.
     */
    val reportedFailures: MutableList<Throwable> = mutableListOf()

    private val scopeReady = CompletableDeferred<KadreScope>()
    private val session: KadreSession

    init {
        port.preInstallInput = stimuliBeforeInstall
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
    @OptIn(ExperimentalCoroutinesApi::class)
    fun surface(): HostSurface = scopeReady.getCompleted().primarySurface.value
        ?: error("a web session exposes a primary surface")

    /** The terminal outcome the runtime published for this session. */
    suspend fun outcome(): SessionOutcome = session.awaitTermination()

    fun stop() = session.requestStop()
}
