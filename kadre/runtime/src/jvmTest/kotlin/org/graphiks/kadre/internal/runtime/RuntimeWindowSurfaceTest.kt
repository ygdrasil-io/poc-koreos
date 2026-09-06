package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.EventDeliverySpan
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DeviceId
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
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.input.ScrollDelta
import org.graphiks.kadre.input.TextDocumentRevision
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextInputEvent
import org.graphiks.kadre.input.TextInputState
import org.graphiks.kadre.input.TextRange
import org.graphiks.kadre.interaction.InteractionHandler
import org.graphiks.kadre.policy.CollectorOverflowAction
import org.graphiks.kadre.policy.ContinuousDelivery
import org.graphiks.kadre.policy.IngressOverflowAction
import org.graphiks.kadre.policy.InputDeliveryPolicy
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.SlowCollectorCancellationException
import org.graphiks.kadre.policy.WindowDeliveryPolicy
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.LogicalRect
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceCapabilities
import org.graphiks.kadre.surface.SurfaceEvent
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceProperty
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceUpdate
import org.graphiks.kadre.surface.SurfaceUpdateOutcome
import org.graphiks.kadre.surface.SurfaceVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.nanoseconds

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeWindowSurfaceTest {
    @Test
    fun textInputAllowsOneSessionAndSerializesDocumentRevisions() = runTest {
        val port = RecordingTextInputPort()
        val surface = surface(textInputPort = port)

        val session = assertIs<KadreResult.Success<org.graphiks.kadre.input.TextInputSession>>(
            surface.input.openTextInput(TextInputConfig(surroundingText = "a", selection = TextRange(1, 1))),
        ).value

        assertEquals(TextInputState.Active(TextDocumentRevision(0), null), session.state.value)
        assertEquals(1, port.opened.size)
        assertEquals(
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.TextInputSession)),
            surface.input.openTextInput(TextInputConfig()),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.StaleRevision(expected = 0, received = 1)),
            session.updateCursor(LogicalRect(LogicalPoint(0.0, 0.0), LogicalSize(1.0, 1.0)), TextDocumentRevision(1)),
        )
        assertEquals(
            KadreResult.Success(Unit),
            session.updateCursor(LogicalRect(LogicalPoint(1.0, 2.0), LogicalSize(3.0, 4.0)), TextDocumentRevision(0)),
        )
        assertEquals(1, port.cursorCommands.size)
        assertEquals(
            KadreResult.Success(Unit),
            session.updateSurroundingText("ab", TextRange(2, 2), TextDocumentRevision(1)),
        )
        assertEquals(TextInputState.Active(TextDocumentRevision(1), null), session.state.value)
        assertEquals(
            KadreResult.Success(Unit),
            session.updateSurroundingText("ab", TextRange(2, 2), TextDocumentRevision(1)),
        )
        assertEquals(1, port.documentCommands.size)
        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("text")),
            session.updateSurroundingText("different", TextRange(2, 2), TextDocumentRevision(1)),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.StaleRevision(expected = 1, received = 0)),
            session.updateSurroundingText("a", TextRange(1, 1), TextDocumentRevision(0)),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("selection")),
            session.updateSurroundingText("a", TextRange(0, 2), TextDocumentRevision(2)),
        )

        session.close()

        assertTrue(port.owner.closed)
        assertIs<KadreResult.Success<org.graphiks.kadre.input.TextInputSession>>(
            surface.input.openTextInput(TextInputConfig()),
        )
    }

    @Test
    fun textInputValidatesNativeRangesSuspendsOnFocusLossAndClosesWithTheSurface() = runTest {
        val port = RecordingTextInputPort()
        val surface = surface(textInputPort = port)
        val session = assertIs<KadreResult.Success<org.graphiks.kadre.input.TextInputSession>>(
            surface.input.openTextInput(TextInputConfig(surroundingText = "ab", selection = TextRange(2, 2))),
        ).value
        val events = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            session.events.toList()
        }

        assertTrue(
            port.emit(
                TextInputObservation.Replace(
                    range = TextRange(1, 2),
                    text = "x",
                    baseRevision = TextDocumentRevision(0),
                ),
            ),
        )
        assertTrue(
            port.emit(
                TextInputObservation.CompositionChanged(
                    range = TextRange(1, 2),
                    text = "xyz",
                    selection = TextRange(1, 2),
                    baseRevision = TextDocumentRevision(0),
                ),
            ),
        )
        assertEquals(TextInputState.Active(TextDocumentRevision(0), TextRange(1, 2)), session.state.value)
        assertTrue(
            port.emit(
                TextInputObservation.CompositionChanged(
                    range = null,
                    text = "",
                    selection = null,
                    baseRevision = TextDocumentRevision(0),
                ),
            ),
        )
        assertFalse(
            port.emit(
                TextInputObservation.SelectionChanged(
                    selection = TextRange(0, 3),
                    baseRevision = TextDocumentRevision(0),
                ),
            ),
        )
        assertTrue(surface.accept(SurfaceStimulus.FocusChanged(surface.id, SurfaceFocus.Focused)))
        assertTrue(surface.accept(SurfaceStimulus.FocusChanged(surface.id, SurfaceFocus.Unfocused)))
        assertEquals(TextInputState.Suspended(TextDocumentRevision(0), null), session.state.value)

        assertTrue(surface.detach())

        val delivered = events.await()
        val replacement = assertIs<TextInputEvent.Replace>(delivered.first())
        assertEquals(TextRange(1, 2), replacement.range)
        assertEquals("x", replacement.text)
        assertEquals(
            listOf<TextInputEvent>(
                replacement,
                TextInputEvent.CompositionChanged(
                    range = TextRange(1, 2),
                    text = "xyz",
                    selection = TextRange(1, 2),
                    baseRevision = TextDocumentRevision(0),
                    stamp = delivered[1].stamp,
                ),
                TextInputEvent.CompositionChanged(
                    range = null,
                    text = "",
                    selection = null,
                    baseRevision = TextDocumentRevision(0),
                    stamp = delivered[2].stamp,
                ),
            ),
            delivered,
        )
        assertEquals(TextInputState.Closed, session.state.value)
        assertTrue(port.owner.closed)
        assertFalse(
            port.emit(
                TextInputObservation.Action(
                    action = org.graphiks.kadre.input.TextInputAction.Done,
                    baseRevision = TextDocumentRevision(0),
                ),
            ),
        )
    }

    @Test
    fun textInputRebasesTheComposingRangeWhenItsSnapshotIsAccepted() = runTest {
        val port = RecordingTextInputPort()
        val surface = surface(textInputPort = port)
        val session = assertIs<KadreResult.Success<org.graphiks.kadre.input.TextInputSession>>(
            surface.input.openTextInput(
                TextInputConfig(surroundingText = "abcd", selection = TextRange(4, 4)),
            ),
        ).value

        assertTrue(
            port.emit(
                TextInputObservation.CompositionChanged(
                    range = TextRange(2, 4),
                    text = "x",
                    selection = TextRange(1, 1),
                    baseRevision = TextDocumentRevision(0),
                ),
            ),
        )
        assertEquals(TextInputState.Active(TextDocumentRevision(0), TextRange(2, 4)), session.state.value)

        assertEquals(
            KadreResult.Success(Unit),
            session.updateSurroundingText("abx", TextRange(3, 3), TextDocumentRevision(1)),
        )
        assertEquals(TextInputState.Active(TextDocumentRevision(1), TextRange(2, 3)), session.state.value)
    }

    @Test
    fun textInputClosesWhenAnObservationRacesWithAnAcceptedDocumentSnapshot() = runTest {
        val port = RecordingTextInputPort()
        val documentUpdateStarted = CompletableDeferred<Unit>()
        val allowDocumentUpdateToReturn = CompletableDeferred<Unit>()
        port.beforeDocumentSuccess = {
            documentUpdateStarted.complete(Unit)
            allowDocumentUpdateToReturn.await()
        }
        val surface = surface(textInputPort = port)
        val session = assertIs<KadreResult.Success<org.graphiks.kadre.input.TextInputSession>>(
            surface.input.openTextInput(
                TextInputConfig(surroundingText = "abcd", selection = TextRange(4, 4)),
            ),
        ).value
        assertTrue(
            port.emit(
                TextInputObservation.CompositionChanged(
                    range = TextRange(2, 4),
                    text = "x",
                    selection = TextRange(1, 1),
                    baseRevision = TextDocumentRevision(0),
                ),
            ),
        )

        val update = async(start = CoroutineStart.UNDISPATCHED) {
            session.updateSurroundingText("abx", TextRange(3, 3), TextDocumentRevision(1))
        }
        documentUpdateStarted.await()
        assertTrue(
            port.emit(
                TextInputObservation.CompositionChanged(
                    range = TextRange(0, 1),
                    text = "z",
                    selection = TextRange(1, 1),
                    baseRevision = TextDocumentRevision(0),
                ),
            ),
        )
        allowDocumentUpdateToReturn.complete(Unit)

        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession)),
            update.await(),
        )
        assertEquals(TextInputState.Closed, session.state.value)
        assertTrue(port.owner.closed)
    }

    @Test
    @OptIn(DelicateKadreApi::class)
    fun interactionHandlerRemainsUnsupportedWithoutBackendInteractionCapabilities() = runTest {
        val surface = surface()

        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.InstallInteractionHandler)),
            surface.installInteractionHandler(InteractionHandler { _, _ -> }),
        )
    }

    @Test
    fun keyAndPointerPublishReducedSnapshotsBeforeEventsAndKeepUnknownRepeatAndReleaseAtOneRevision() = runTest {
        val surface = surface()
        val observedRevisions = mutableListOf<Long>()
        val events = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            surface.input.events
                .onEach { observedRevisions += surface.input.state.value.revision.value }
                .take(4)
                .toList()
        }
        val physical = PhysicalKey.Unidentified("native-key-91")
        val logical = LogicalKey.Unidentified("native-key-91")
        val neverPressedPhysical = PhysicalKey.Unidentified("native-key-never-pressed")
        val neverPressedLogical = LogicalKey.Unidentified("native-key-never-pressed")
        val modifiers = KeyboardModifiers(setOf(ModifierKey.Shift))

        assertTrue(
            surface.accept(
                SurfaceStimulus.KeyChanged(
                    surface.id,
                    physical,
                    logical,
                    KeyLocation.Standard,
                    KeyState.Pressed,
                    repeat = false,
                    modifiers,
                ),
            ),
        )
        assertTrue(
            surface.accept(
                SurfaceStimulus.KeyChanged(
                    surface.id,
                    physical,
                    logical,
                    KeyLocation.Standard,
                    KeyState.Pressed,
                    repeat = true,
                    modifiers,
                ),
            ),
        )
        assertTrue(
            surface.accept(
                SurfaceStimulus.PointerEntered(
                    surface.id,
                    PointerKind.Mouse,
                    LogicalPoint(19.0, 23.0),
                ),
            ),
        )
        assertTrue(
            surface.accept(
                SurfaceStimulus.KeyChanged(
                    surface.id,
                    neverPressedPhysical,
                    neverPressedLogical,
                    KeyLocation.Standard,
                    KeyState.Released,
                    repeat = false,
                    modifiers,
                ),
            ),
        )

        val received = events.await()
        val keys = received.take(2).map { assertIs<InputEvent.Key>(it) }
        val pointer = assertIs<InputEvent.PointerEntered>(received[2])
        val released = assertIs<InputEvent.Key>(received[3])
        assertEquals(listOf(1L, 1L, 2L, 2L), received.map { it.stateRevision.value })
        assertEquals(listOf(1L, 1L, 2L, 2L), observedRevisions)
        assertEquals(physical, keys.first().physicalKey)
        assertEquals(logical, keys.first().logicalKey)
        assertEquals(KeyState.Pressed, keys.last().keyState)
        assertTrue(keys.last().repeat)
        assertEquals(neverPressedPhysical, released.physicalKey)
        assertEquals(neverPressedLogical, released.logicalKey)
        assertEquals(KeyState.Released, released.keyState)
        assertFalse(released.repeat)
        assertEquals(LogicalPoint(19.0, 23.0), pointer.position)
        assertEquals(setOf(physical), surface.input.state.value.keyboard.pressedKeys)
        assertEquals(modifiers, surface.input.state.value.modifiers)
        assertEquals(pointer.pointerId, surface.input.state.value.pointers.single().id)
    }

    @Test
    fun mousePointerKeepsOneRuntimeIdentityAndRemovesItOnlyAfterTheExitEventCarriesItsLastPosition() = runTest {
        val surface = surface()
        val observedPointers = mutableListOf<List<org.graphiks.kadre.input.PointerState>>()
        val events = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            surface.input.events
                .onEach { observedPointers += surface.input.state.value.pointers }
                .take(6)
                .toList()
        }
        val enteredAt = LogicalPoint(3.0, 5.0)
        val movedAt = LogicalPoint(9.0, 11.0)
        val reenteredAt = LogicalPoint(13.0, 17.0)

        assertTrue(surface.accept(SurfaceStimulus.PointerEntered(surface.id, PointerKind.Mouse, enteredAt)))
        assertTrue(
            surface.accept(
                SurfaceStimulus.PointerMoved(
                    surface.id,
                    PointerKind.Mouse,
                    movedAt,
                    LogicalDelta(6.0, 6.0),
                    pressure = null,
                    pen = null,
                ),
            ),
        )
        assertTrue(
            surface.accept(
                SurfaceStimulus.PointerButtonChanged(
                    surface.id,
                    PointerKind.Mouse,
                    PointerButton.Primary,
                    PointerButtonState.Pressed,
                    movedAt,
                    pressure = null,
                    pen = null,
                ),
            ),
        )
        assertTrue(surface.accept(SurfaceStimulus.PointerLeft(surface.id, PointerKind.Mouse)))
        assertTrue(surface.accept(SurfaceStimulus.PointerEntered(surface.id, PointerKind.Mouse, reenteredAt)))
        assertTrue(surface.accept(SurfaceStimulus.PointerLeft(surface.id, PointerKind.Mouse)))

        val received = events.await()
        val entered = assertIs<InputEvent.PointerEntered>(received[0])
        val moved = assertIs<InputEvent.PointerMoved>(received[1])
        val button = assertIs<InputEvent.PointerButtonChanged>(received[2])
        val left = assertIs<InputEvent.PointerLeft>(received[3])
        val reentered = assertIs<InputEvent.PointerEntered>(received[4])
        val finalLeft = assertIs<InputEvent.PointerLeft>(received[5])
        assertEquals(entered.pointerId, moved.pointerId)
        assertEquals(entered.pointerId, button.pointerId)
        assertEquals(entered.pointerId, left.pointerId)
        assertEquals(entered.pointerId, reentered.pointerId)
        assertEquals(entered.pointerId, finalLeft.pointerId)
        assertEquals(enteredAt, entered.position)
        assertEquals(movedAt, left.lastPosition)
        assertEquals(reenteredAt, finalLeft.lastPosition)
        assertEquals(listOf(1L, 2L, 3L, 3L, 5L, 5L), received.map { it.stateRevision.value })
        assertEquals(movedAt, observedPointers[3].single().position)
        assertEquals(setOf(PointerButton.Primary), observedPointers[3].single().pressedButtons)
        assertEquals(reenteredAt, observedPointers[5].single().position)
        assertEquals(6L, surface.input.state.value.revision.value)
        assertEquals(emptyList(), surface.input.state.value.pointers)
    }

    @Test
    fun scrollCoalescesAdditivelyOnlyInsideItsNativeBoundaryAndDoesNotReviseSnapshot() = runTest {
        val surface = surface()
        val firstDevice = DeviceId(1L)
        val secondDevice = DeviceId(2L)
        var injected = false
        val events = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            surface.input.events
                .onEach { event ->
                    if (!injected && event is InputEvent.Key) {
                        injected = true
                        surface.accept(
                            SurfaceStimulus.Scroll(
                                surface.id,
                                ScrollDelta.Lines(1.0, -2.0),
                                7L,
                                firstDevice,
                            ),
                        )
                        surface.accept(
                            SurfaceStimulus.Scroll(
                                surface.id,
                                ScrollDelta.Lines(3.0, 5.0),
                                7L,
                                firstDevice,
                            ),
                        )
                        surface.accept(
                            SurfaceStimulus.Scroll(
                                surface.id,
                                ScrollDelta.Logical(7.0, 9.0),
                                7L,
                                firstDevice,
                            ),
                        )
                        surface.accept(
                            SurfaceStimulus.Scroll(
                                surface.id,
                                ScrollDelta.Lines(-4.0, 6.0),
                                7L,
                                secondDevice,
                            ),
                        )
                    }
                }
                .take(4)
                .toList()
        }

        surface.accept(
            SurfaceStimulus.KeyChanged(
                surface.id,
                PhysicalKey.Unidentified("native-scroll-barrier"),
                LogicalKey.Unidentified("native-scroll-barrier"),
                KeyLocation.Standard,
                KeyState.Pressed,
                repeat = false,
                KeyboardModifiers(emptySet()),
            ),
        )

        val received = events.await()
        val first = assertIs<InputEvent.Scrolled>(received[1])
        val second = assertIs<InputEvent.Scrolled>(received[2])
        val third = assertIs<InputEvent.Scrolled>(received[3])
        assertEquals(ScrollDelta.Lines(4.0, 3.0), first.delta)
        assertEquals(ScrollDelta.Logical(7.0, 9.0), second.delta)
        assertEquals(ScrollDelta.Lines(-4.0, 6.0), third.delta)
        assertEquals(EventDeliverySpan(SessionSequence(1), SessionSequence(2), 2), first.stamp.deliverySpan)
        assertEquals(null, second.stamp.deliverySpan)
        assertEquals(null, third.stamp.deliverySpan)
        assertEquals(firstDevice, first.deviceId)
        assertEquals(firstDevice, second.deviceId)
        assertEquals(secondDevice, third.deviceId)
        assertEquals(listOf(1L, 1L, 1L, 1L), received.map { it.stateRevision.value })
        assertEquals(1L, surface.input.state.value.revision.value)
    }

    @Test
    fun focusLossPublishesOneNeutralSnapshotAndResetWithoutSyntheticKeyRelease() = runTest {
        val surface = surface()
        val events = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            surface.input.events.take(3).toList()
        }
        val physical = PhysicalKey.Unidentified("native-focus-key")

        assertTrue(surface.accept(SurfaceStimulus.FocusChanged(surface.id, SurfaceFocus.Focused)))
        assertTrue(
            surface.accept(
                SurfaceStimulus.KeyChanged(
                    surface.id,
                    physical,
                    LogicalKey.Unidentified("native-focus-key"),
                    KeyLocation.Standard,
                    KeyState.Pressed,
                    repeat = false,
                    KeyboardModifiers(setOf(ModifierKey.Alt)),
                ),
            ),
        )
        assertTrue(
            surface.accept(
                SurfaceStimulus.PointerButtonChanged(
                    surface.id,
                    PointerKind.Mouse,
                    PointerButton.Primary,
                    PointerButtonState.Pressed,
                    LogicalPoint(31.0, 37.0),
                    pressure = null,
                    pen = null,
                ),
            ),
        )
        assertTrue(surface.accept(SurfaceStimulus.FocusChanged(surface.id, SurfaceFocus.Unfocused)))

        val received = events.await()
        assertIs<InputEvent.Key>(received.first())
        assertIs<InputEvent.PointerButtonChanged>(received[1])
        val reset = assertIs<InputEvent.StateReset>(received.last())
        assertEquals(InputStateResetReason.FocusLost, reset.reason)
        assertEquals(3L, reset.stateRevision.value)
        assertFalse(received.any { it is InputEvent.Key && it.keyState == KeyState.Released })
        assertFalse(
            received.any {
                it is InputEvent.PointerButtonChanged && it.buttonState == PointerButtonState.Released
            },
        )
        assertEquals(emptySet(), surface.input.state.value.keyboard.pressedKeys)
        assertEquals(KeyboardModifiers(emptySet()), surface.input.state.value.modifiers)
        assertEquals(emptyList(), surface.input.state.value.pointers)
    }

    @Test
    fun inputCapabilitiesBecomeAvailableOnlyAfterTheirNativeObservationIsStructurallyInstalled() {
        val surface = surface()

        assertAllInputObservationCapabilitiesUnsupported(surface)
        assertTrue(
            surface.accept(
                SurfaceStimulus.InputObservationChanged(
                    surface.id,
                    keyboardInstalled = true,
                    pointerInstalled = false,
                ),
            ),
        )
        assertEquals(FeatureAvailability.Available, surface.input.state.value.capabilities.keyboard)
        assertEquals(FeatureAvailability.Unsupported, surface.input.state.value.capabilities.pointer)
        assertAllOtherInputCapabilitiesUnsupported(surface)
        assertEquals(1L, surface.input.state.value.revision.value)
        assertFalse(
            surface.accept(
                SurfaceStimulus.InputObservationChanged(
                    surface.id,
                    keyboardInstalled = true,
                    pointerInstalled = false,
                ),
            ),
        )

        assertTrue(
            surface.accept(
                SurfaceStimulus.InputObservationChanged(
                    surface.id,
                    keyboardInstalled = true,
                    pointerInstalled = true,
                ),
            ),
        )
        assertEquals(FeatureAvailability.Available, surface.input.state.value.capabilities.keyboard)
        assertEquals(FeatureAvailability.Available, surface.input.state.value.capabilities.pointer)
        assertAllOtherInputCapabilitiesUnsupported(surface)
        assertEquals(2L, surface.input.state.value.revision.value)

        assertTrue(
            surface.accept(
                SurfaceStimulus.InputObservationChanged(
                    surface.id,
                    keyboardInstalled = false,
                    pointerInstalled = false,
                ),
            ),
        )
        assertAllInputObservationCapabilitiesUnsupported(surface)
        assertEquals(3L, surface.input.state.value.revision.value)
    }

    @Test
    fun inputIngressOverflowNeutralisesTheSourceAndRejectsEveryLateStimulus() = runTest {
        val inputPolicy = KadrePolicies.Default.input.copy(
            discreteEvents = KadrePolicies.Default.input.discreteEvents.copy(
                ingressCapacity = 1,
                ingressOverflow = IngressOverflowAction.CloseSource,
            ),
        )
        val surface = surface(inputDeliveryPolicy = inputPolicy)
        var injected = false
        val terminal = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                surface.input.events
                    .onEach { event ->
                        if (!injected && event is InputEvent.Key) {
                            injected = true
                            surface.accept(keyStimulus(surface, "overflow-one"))
                            surface.accept(keyStimulus(surface, "overflow-two"))
                        }
                    }
                    .toList()
            }.exceptionOrNull()
        }

        assertTrue(surface.accept(keyStimulus(surface, "overflow-root")))

        val expected = KadreFailure.SourceOverflow(KadreResourceKind.InputSource)
        assertEquals(expected, assertIs<KadreException>(terminal.await()).failure)
        assertEquals(emptySet(), surface.input.state.value.keyboard.pressedKeys)
        assertEquals(expected, assertIs<FeatureAvailability.Unavailable>(surface.input.state.value.capabilities.keyboard).failure)
        assertFalse(surface.accept(keyStimulus(surface, "late-after-overflow")))
    }

    @Test
    fun slowInputCollectorCanCloseTheWholeInputSourceAccordingToItsDedicatedPolicy() = runTest {
        val inputPolicy = KadrePolicies.Default.input.copy(
            discreteEvents = KadrePolicies.Default.input.discreteEvents.copy(
                collectorCapacity = 1,
                collectorOverflow = CollectorOverflowAction.CloseSource,
            ),
        )
        val surface = surface(inputDeliveryPolicy = inputPolicy)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val terminal = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                surface.input.events
                    .onEach { event ->
                        if (event is InputEvent.Key && event.logicalKey == LogicalKey.Unidentified("collector-root")) {
                            entered.complete(Unit)
                            release.await()
                        }
                    }
                    .toList()
            }.exceptionOrNull()
        }

        assertTrue(surface.accept(keyStimulus(surface, "collector-root")))
        entered.await()
        assertTrue(surface.accept(keyStimulus(surface, "collector-one")))
        assertTrue(surface.accept(keyStimulus(surface, "collector-two")))
        release.complete(Unit)

        val expected = KadreFailure.SourceOverflow(KadreResourceKind.InputSource)
        assertEquals(expected, assertIs<KadreException>(terminal.await()).failure)
        assertEquals(emptySet(), surface.input.state.value.keyboard.pressedKeys)
        assertEquals(expected, assertIs<FeatureAvailability.Unavailable>(surface.input.state.value.capabilities.pointer).failure)
    }

    @Test
    fun slowInputCollectorCancellationDropsItsQueuedEventsBeforeReportingThePolicyFailure() = runTest {
        val inputPolicy = KadrePolicies.Default.input.copy(
            discreteEvents = KadrePolicies.Default.input.discreteEvents.copy(
                collectorCapacity = 1,
                collectorOverflow = CollectorOverflowAction.CancelSlowCollector,
            ),
        )
        val surface = surface(inputDeliveryPolicy = inputPolicy)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val delivered = mutableListOf<InputEvent.Key>()
        val terminal = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                surface.input.events
                    .onEach { event ->
                        val key = event as? InputEvent.Key ?: return@onEach
                        delivered += key
                        if (key.logicalKey == LogicalKey.Unidentified("cancel-root")) {
                            entered.complete(Unit)
                            release.await()
                        }
                    }
                    .toList()
            }.exceptionOrNull()
        }

        assertTrue(surface.accept(keyStimulus(surface, "cancel-root")))
        entered.await()
        assertTrue(surface.accept(keyStimulus(surface, "cancel-one")))
        assertTrue(surface.accept(keyStimulus(surface, "cancel-two")))
        release.complete(Unit)

        assertIs<SlowCollectorCancellationException>(terminal.await())
        assertEquals(
            listOf(LogicalKey.Unidentified("cancel-root")),
            delivered.map(InputEvent.Key::logicalKey),
        )
    }

    @Test
    fun initialSnapshotUsesAllEffectiveMetricsAtRevisionZero() {
        val surface = surface(
            metrics = SurfaceMetrics(
                logicalSize = LogicalSize(640.5, 360.25),
                physicalSize = PhysicalSize(1281, 721),
                scaleFactor = 2.0,
                safeAreaInsets = LogicalInsets(12.0, 3.0, 7.0, 4.0),
            ),
        )

        assertEquals(SurfaceAttachmentState.Attached, surface.state.value.attachment)
        assertEquals(LogicalSize(640.5, 360.25), surface.state.value.logicalSize)
        assertEquals(PhysicalSize(1281, 721), surface.state.value.physicalSize)
        assertEquals(2.0, surface.state.value.scaleFactor)
        assertEquals(LogicalInsets(12.0, 3.0, 7.0, 4.0), surface.state.value.safeAreaInsets)
        assertEquals(SurfaceRevision(0), surface.state.value.revision)
    }

    @Test
    fun surfaceMetricsRejectAPhysicalSizeThatWasNotDerivedFromTheAtomicLogicalSnapshot() {
        assertFailsWith<IllegalArgumentException> {
            SurfaceMetrics(
                logicalSize = LogicalSize(640.5, 360.25),
                physicalSize = PhysicalSize(1280, 720),
                scaleFactor = 2.0,
                safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
            )
        }
    }

    @Test
    fun oneMetricsStimulusPublishesOneAtomicRevisionBeforeItsEvent() = runTest {
        val surface = surface()
        var stateObservedByCollector = surface.state.value
        val event = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            surface.events.first().also { stateObservedByCollector = surface.state.value }
        }

        val accepted = surface.accept(
            SurfaceStimulus.MetricsChanged(
                surface.id,
                SurfaceMetrics(
                    logicalSize = LogicalSize(800.25, 450.5),
                    physicalSize = PhysicalSize(1601, 901),
                    scaleFactor = 2.0,
                    safeAreaInsets = LogicalInsets(5.0, 4.0, 3.0, 2.0),
                ),
            ),
        )

        val metricsEvent = assertIs<SurfaceEvent.MetricsChanged>(event.await())
        assertTrue(accepted)
        assertEquals(SurfaceRevision(1), metricsEvent.state.revision)
        assertEquals(metricsEvent.state, stateObservedByCollector)
        assertEquals(metricsEvent.state, surface.state.value)
        assertEquals(LogicalSize(800.25, 450.5), metricsEvent.state.logicalSize)
        assertEquals(PhysicalSize(1601, 901), metricsEvent.state.physicalSize)
        assertEquals(2.0, metricsEvent.state.scaleFactor)
        assertEquals(LogicalInsets(5.0, 4.0, 3.0, 2.0), metricsEvent.state.safeAreaInsets)
    }

    @Test
    fun equalStimuliAreIgnoredWithoutRevisionOrEvent() = runTest {
        val surface = surface()
        val events = mutableListOf<SurfaceEvent>()
        val collection = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            surface.events.toList(events)
        }
        assertFalse(surface.accept(SurfaceStimulus.MetricsChanged(surface.id, DEFAULT_METRICS)))
        assertFalse(surface.accept(SurfaceStimulus.FocusChanged(surface.id, SurfaceFocus.Unfocused)))
        assertFalse(
            surface.accept(
                SurfaceStimulus.VisibilityChanged(
                    surface.id,
                    SurfaceVisibility.Visible,
                    SurfaceOcclusion.Unknown,
                ),
            ),
        )
        assertFalse(surface.accept(SurfaceStimulus.ThemeChanged(surface.id, SurfaceTheme.Unknown)))
        surface.detach()
        collection.await()

        assertEquals(SurfaceRevision(1), surface.state.value.revision)
        assertEquals(emptyList(), events)
    }

    @Test
    fun eachTypedObservationChangesOnlyItsOwnedSnapshotFields() = runTest {
        val surface = surface()
        val events = mutableListOf<SurfaceEvent>()
        val collection = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            surface.events.toList(events)
        }

        assertTrue(surface.accept(SurfaceStimulus.FocusChanged(surface.id, SurfaceFocus.Focused)))
        assertTrue(
            surface.accept(
                SurfaceStimulus.VisibilityChanged(
                    surface.id,
                    SurfaceVisibility.Hidden,
                    SurfaceOcclusion.Occluded,
                ),
            ),
        )
        assertTrue(surface.accept(SurfaceStimulus.ThemeChanged(surface.id, SurfaceTheme.Dark)))
        surface.detach()
        collection.await()

        assertEquals(SurfaceFocus.Focused, surface.state.value.focus)
        assertEquals(SurfaceVisibility.Hidden, surface.state.value.visibility)
        assertEquals(SurfaceOcclusion.Occluded, surface.state.value.occlusion)
        assertEquals(SurfaceTheme.Dark, surface.state.value.theme)
        assertEquals(SurfaceRevision(4), surface.state.value.revision)
        assertIs<SurfaceEvent.FocusChanged>(events[0])
        assertIs<SurfaceEvent.VisibilityChanged>(events[1])
        assertIs<SurfaceEvent.ThemeChanged>(events[2])
        assertEquals(listOf(1L, 2L, 3L), events.map { it.stateRevision.value })
    }

    @Test
    fun reentrantStimulusIsQueuedAfterTheEventThatTriggeredIt() = runTest {
        val surface = surface()
        val events = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            surface.events
                .onEach { event ->
                    if (event is SurfaceEvent.FocusChanged) {
                        surface.accept(SurfaceStimulus.ThemeChanged(surface.id, SurfaceTheme.Dark))
                    }
                }
                .take(2)
                .toList()
        }

        surface.accept(SurfaceStimulus.FocusChanged(surface.id, SurfaceFocus.Focused))

        assertEquals(
            listOf(SurfaceEvent.FocusChanged::class, SurfaceEvent.ThemeChanged::class),
            events.await().map { it::class },
        )
        assertEquals(SurfaceRevision(2), surface.state.value.revision)
        assertEquals(SurfaceTheme.Dark, surface.state.value.theme)
    }

    @Test
    fun ingressCoalescesGeometryWithinDiscreteBarriersAndFlattensDeliverySpans() = runTest {
        val surface = surface()
        var injected = false
        val events = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            surface.events
                .onEach { event ->
                    if (!injected && event is SurfaceEvent.FocusChanged) {
                        injected = true
                        surface.accept(SurfaceStimulus.MetricsChanged(surface.id, metrics(650.0, 370.0)))
                        surface.accept(SurfaceStimulus.MetricsChanged(surface.id, metrics(660.0, 380.0)))
                        surface.accept(SurfaceStimulus.ThemeChanged(surface.id, SurfaceTheme.Dark))
                        surface.accept(SurfaceStimulus.MetricsChanged(surface.id, metrics(670.0, 390.0)))
                        surface.accept(SurfaceStimulus.MetricsChanged(surface.id, metrics(680.0, 400.0)))
                    }
                }
                .take(4)
                .toList()
        }

        surface.accept(SurfaceStimulus.FocusChanged(surface.id, SurfaceFocus.Focused))

        val delivered = events.await()
        assertEquals(
            listOf(
                SurfaceEvent.FocusChanged::class,
                SurfaceEvent.MetricsChanged::class,
                SurfaceEvent.ThemeChanged::class,
                SurfaceEvent.MetricsChanged::class,
            ),
            delivered.map { it::class },
        )
        val geometry = delivered.filterIsInstance<SurfaceEvent.MetricsChanged>()
        assertEquals(listOf(660.0, 680.0), geometry.map { it.state.logicalSize.width })
        assertEquals(
            listOf(
                EventDeliverySpan(SessionSequence(1), SessionSequence(2), 2),
                EventDeliverySpan(SessionSequence(4), SessionSequence(5), 2),
            ),
            geometry.map { it.stamp.deliverySpan },
        )
    }

    @Test
    fun slowCollectorHasItsOwnBoundedGeometrySchedulerAndDrainsAdmittedSegmentsOnClose() = runTest {
        val surface = surface()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val collection = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            surface.events
                .onEach { event ->
                    if (event is SurfaceEvent.FocusChanged) {
                        entered.complete(Unit)
                        release.await()
                    }
                }
                .toList()
        }

        surface.accept(SurfaceStimulus.FocusChanged(surface.id, SurfaceFocus.Focused))
        entered.await()
        surface.accept(SurfaceStimulus.MetricsChanged(surface.id, metrics(650.0, 370.0)))
        surface.accept(SurfaceStimulus.MetricsChanged(surface.id, metrics(660.0, 380.0)))
        surface.accept(SurfaceStimulus.ThemeChanged(surface.id, SurfaceTheme.Dark))
        surface.accept(SurfaceStimulus.MetricsChanged(surface.id, metrics(670.0, 390.0)))
        surface.accept(SurfaceStimulus.MetricsChanged(surface.id, metrics(680.0, 400.0)))
        surface.detach()
        release.complete(Unit)

        val delivered = collection.await()
        assertEquals(
            listOf(
                SurfaceEvent.FocusChanged::class,
                SurfaceEvent.MetricsChanged::class,
                SurfaceEvent.ThemeChanged::class,
                SurfaceEvent.MetricsChanged::class,
            ),
            delivered.map { it::class },
        )
        val geometry = delivered.filterIsInstance<SurfaceEvent.MetricsChanged>()
        assertEquals(listOf(660.0, 680.0), geometry.map { it.state.logicalSize.width })
        assertEquals(listOf(2L, 2L), geometry.map { it.stamp.deliverySpan?.eventCount })
    }

    @Test
    fun slowCollectorKeepsOnlyTheLatestRedrawMarkerWithAnExactSpan() = runTest {
        val port = RecordingSurfaceCommandPort()
        val surface = surface(port = port, commandsEnabled = true)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val collection = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            surface.events
                .onEach { event ->
                    if (event is SurfaceEvent.FocusChanged) {
                        entered.complete(Unit)
                        release.await()
                    }
                }
                .toList()
        }

        surface.accept(SurfaceStimulus.FocusChanged(surface.id, SurfaceFocus.Focused))
        entered.await()
        repeat(3) {
            surface.requestRedraw()
            val command = port.redrawCommands.last()
            surface.accept(SurfaceStimulus.RedrawConsumed(surface.id, command.generation))
        }
        surface.detach()
        release.complete(Unit)

        val delivered = collection.await()
        assertEquals(2, delivered.size)
        val redraw = assertIs<SurfaceEvent.RedrawRequested>(delivered.last())
        assertEquals(
            EventDeliverySpan(SessionSequence(1), SessionSequence(3), 3),
            redraw.stamp.deliverySpan,
        )
    }

    @Test
    fun mixedContinuousLanesDeliverTheSmallestRetainedSequenceFirst() = runTest {
        val port = RecordingSurfaceCommandPort()
        val surface = surface(port = port, commandsEnabled = true)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val collection = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            surface.events
                .onEach { event ->
                    if (event is SurfaceEvent.FocusChanged) {
                        entered.complete(Unit)
                        release.await()
                    }
                }
                .toList()
        }

        surface.accept(SurfaceStimulus.FocusChanged(surface.id, SurfaceFocus.Focused))
        entered.await()
        surface.accept(SurfaceStimulus.MetricsChanged(surface.id, metrics(650.0, 370.0)))
        repeat(2) {
            surface.requestRedraw()
            val command = port.redrawCommands.last()
            surface.accept(SurfaceStimulus.RedrawConsumed(surface.id, command.generation))
        }
        surface.accept(SurfaceStimulus.MetricsChanged(surface.id, metrics(660.0, 380.0)))
        surface.detach()
        release.complete(Unit)

        val delivered = collection.await()
        assertEquals(
            listOf(
                SurfaceEvent.FocusChanged::class,
                SurfaceEvent.RedrawRequested::class,
                SurfaceEvent.MetricsChanged::class,
            ),
            delivered.map { it::class },
        )
        assertEquals(
            EventDeliverySpan(SessionSequence(2), SessionSequence(3), 2),
            delivered[1].stamp.deliverySpan,
        )
        assertEquals(
            EventDeliverySpan(SessionSequence(1), SessionSequence(4), 2),
            delivered[2].stamp.deliverySpan,
        )
    }

    @Test
    fun eventCollectorsAreLimitedPerFlowAndAcrossTheSharedSessionBudget() = runTest {
        val perFlow = surface(
            maxCollectorsPerFlow = 1,
            collectorAllocator = RuntimeEventCollectorAllocator(2),
        )
        val first = launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            perFlow.events.collect()
        }
        val flowFailure = assertFailsWith<KadreException> { perFlow.events.collect() }
        assertEquals(
            KadreFailure.ResourceLimitExceeded(KadreResourceKind.EventCollector, 1),
            flowFailure.failure,
        )
        first.cancelAndJoin()

        val sharedAllocator = RuntimeEventCollectorAllocator(1)
        val firstSurface = surface(
            id = SurfaceId(41),
            maxCollectorsPerFlow = 2,
            collectorAllocator = sharedAllocator,
        )
        val secondSurface = surface(
            id = SurfaceId(42),
            maxCollectorsPerFlow = 2,
            collectorAllocator = sharedAllocator,
        )
        val sessionCollector = launch(
            UnconfinedTestDispatcher(testScheduler),
            start = CoroutineStart.UNDISPATCHED,
        ) {
            firstSurface.events.collect()
        }
        val sessionFailure = assertFailsWith<KadreException> { secondSurface.events.collect() }
        assertEquals(
            KadreFailure.ResourceLimitExceeded(KadreResourceKind.EventCollector, 1),
            sessionFailure.failure,
        )
        sessionCollector.cancelAndJoin()
    }

    @Test
    fun lateInputCollectorObservesNormalTerminalBeforeASaturatedSessionBudget() = runTest {
        val sharedAllocator = RuntimeEventCollectorAllocator(1)
        val target = surface(
            id = SurfaceId(43),
            collectorAllocator = sharedAllocator,
        )
        val blocker = surface(
            id = SurfaceId(44),
            collectorAllocator = sharedAllocator,
        )
        val activeInput = async(
            UnconfinedTestDispatcher(testScheduler),
            start = CoroutineStart.UNDISPATCHED,
        ) {
            target.input.events.toList()
        }

        val whileOpen = assertFailsWith<KadreException> { blocker.events.collect() }
        assertEquals(
            KadreFailure.ResourceLimitExceeded(KadreResourceKind.EventCollector, 1),
            whileOpen.failure,
        )
        target.detach()
        assertTrue(activeInput.await().isEmpty())

        val blockerCollection = launch(
            UnconfinedTestDispatcher(testScheduler),
            start = CoroutineStart.UNDISPATCHED,
        ) {
            blocker.events.collect()
        }
        try {
            assertTrue(blockerCollection.isActive)
            assertTrue(target.input.events.toList().isEmpty())
        } finally {
            blockerCollection.cancelAndJoin()
        }
    }

    @Test
    fun lateInputCollectorObservesTerminalFailureBeforeASaturatedSessionBudget() = runTest {
        val sharedAllocator = RuntimeEventCollectorAllocator(1)
        val policy = KadrePolicies.Default.window.copy(
            discreteEvents = KadrePolicies.Default.window.discreteEvents.copy(
                ingressCapacity = 1,
                ingressOverflow = IngressOverflowAction.CloseSource,
            ),
        )
        val target = surface(
            id = SurfaceId(45),
            deliveryPolicy = policy,
            collectorAllocator = sharedAllocator,
        )
        val blocker = surface(
            id = SurfaceId(46),
            collectorAllocator = sharedAllocator,
        )
        var injected = false
        val terminalCollector = async(
            UnconfinedTestDispatcher(testScheduler),
            start = CoroutineStart.UNDISPATCHED,
        ) {
            runCatching {
                target.events
                    .onEach { event ->
                        if (!injected && event is SurfaceEvent.FocusChanged) {
                            injected = true
                            target.accept(SurfaceStimulus.ThemeChanged(target.id, SurfaceTheme.Dark))
                            target.accept(
                                SurfaceStimulus.VisibilityChanged(
                                    target.id,
                                    SurfaceVisibility.Hidden,
                                    SurfaceOcclusion.Occluded,
                                ),
                            )
                        }
                    }
                    .toList()
            }.exceptionOrNull()
        }
        target.accept(SurfaceStimulus.FocusChanged(target.id, SurfaceFocus.Focused))
        val expected = KadreFailure.SourceOverflow(KadreResourceKind.Surface)
        assertEquals(expected, assertIs<KadreException>(terminalCollector.await()).failure)

        val blockerCollection = launch(
            UnconfinedTestDispatcher(testScheduler),
            start = CoroutineStart.UNDISPATCHED,
        ) {
            blocker.events.collect()
        }
        try {
            assertTrue(blockerCollection.isActive)
            assertEquals(
                expected,
                assertFailsWith<KadreException> { target.input.events.collect() }.failure,
            )
        } finally {
            blockerCollection.cancelAndJoin()
        }
    }

    @Test
    fun collectorDiscreteOverflowCancelsOnlyTheSlowCollector() = runTest {
        val surface = surface(
            deliveryPolicy = KadrePolicies.Default.window.copy(
                discreteEvents = KadrePolicies.Default.window.discreteEvents.copy(
                    collectorCapacity = 1,
                    collectorOverflow = CollectorOverflowAction.CancelSlowCollector,
                ),
            ),
        )
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val collection = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                surface.events
                    .onEach { event ->
                        if (event is SurfaceEvent.FocusChanged) {
                            entered.complete(Unit)
                            release.await()
                        }
                    }
                    .toList()
            }.exceptionOrNull()
        }

        surface.accept(SurfaceStimulus.FocusChanged(surface.id, SurfaceFocus.Focused))
        entered.await()
        surface.accept(SurfaceStimulus.ThemeChanged(surface.id, SurfaceTheme.Dark))
        surface.accept(
            SurfaceStimulus.VisibilityChanged(
                surface.id,
                SurfaceVisibility.Hidden,
                SurfaceOcclusion.Occluded,
            ),
        )
        assertEquals(SurfaceAttachmentState.Attached, surface.state.value.attachment)
        surface.detach()
        release.complete(Unit)

        assertIs<SlowCollectorCancellationException>(collection.await())
    }

    @Test
    fun ingressDiscreteOverflowClosesTheSurfaceWithTheStableOverflowFailure() = runTest {
        val policy = KadrePolicies.Default.window.copy(
            discreteEvents = KadrePolicies.Default.window.discreteEvents.copy(
                ingressCapacity = 1,
                ingressOverflow = IngressOverflowAction.CloseSource,
            ),
        )
        val surface = surface(deliveryPolicy = policy)
        var injected = false
        val collection = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                surface.events
                    .onEach { event ->
                        if (!injected && event is SurfaceEvent.FocusChanged) {
                            injected = true
                            surface.accept(SurfaceStimulus.ThemeChanged(surface.id, SurfaceTheme.Dark))
                            surface.accept(
                                SurfaceStimulus.VisibilityChanged(
                                    surface.id,
                                    SurfaceVisibility.Hidden,
                                    SurfaceOcclusion.Occluded,
                                ),
                            )
                            if (surface.state.value.attachment != SurfaceAttachmentState.Detached) {
                                surface.detach()
                            }
                        }
                    }
                    .toList()
            }.exceptionOrNull()
        }

        surface.accept(SurfaceStimulus.FocusChanged(surface.id, SurfaceFocus.Focused))

        val failure = assertIs<KadreException>(collection.await())
        assertEquals(KadreFailure.SourceOverflow(KadreResourceKind.Surface), failure.failure)
        assertEquals(SurfaceAttachmentState.Detached, surface.state.value.attachment)
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface)),
            surface.requestRedraw(),
        )
        assertEquals(
            failure.failure,
            assertFailsWith<KadreException> { surface.events.collect() }.failure,
        )
    }

    @Test
    fun failSessionOverflowUsesTheSameStableFailureForSourceAndSessionHandler() = runTest {
        val policy = KadrePolicies.Default.window.copy(
            discreteEvents = KadrePolicies.Default.window.discreteEvents.copy(
                ingressCapacity = 1,
                ingressOverflow = IngressOverflowAction.FailSession,
            ),
        )
        val sessionFailures = mutableListOf<KadreFailure>()
        val surface = surface(
            deliveryPolicy = policy,
            sessionFailureHandler = sessionFailures::add,
        )
        var injected = false
        val collection = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                surface.events
                    .onEach { event ->
                        if (!injected && event is SurfaceEvent.FocusChanged) {
                            injected = true
                            surface.accept(SurfaceStimulus.ThemeChanged(surface.id, SurfaceTheme.Dark))
                            surface.accept(
                                SurfaceStimulus.VisibilityChanged(
                                    surface.id,
                                    SurfaceVisibility.Hidden,
                                    SurfaceOcclusion.Occluded,
                                ),
                            )
                        }
                    }
                    .toList()
            }.exceptionOrNull()
        }

        surface.accept(SurfaceStimulus.FocusChanged(surface.id, SurfaceFocus.Focused))

        val expected: KadreFailure = KadreFailure.SourceOverflow(KadreResourceKind.Surface)
        assertEquals(listOf(expected), sessionFailures)
        assertEquals(expected, assertIs<KadreException>(collection.await()).failure)
    }

    @Test
    fun disabledCommandAccessKeepsAllPhaseThreeOperationsPrivate() = runTest {
        val port = RecordingSurfaceCommandPort()
        val surface = surface(
            port = port,
            commandsEnabled = false,
            capabilities = phaseThreeCapabilities(),
        )

        assertEquals(
            KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = false)),
            surface.requestRedraw(),
        )
        val update = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
            surface.apply(SurfaceUpdate(cursor = PropertyChange.Set(CursorStyle.Hidden))).successValue(),
        )

        assertEquals(SurfaceProperty.Cursor, update.rejected.single().field)
        assertEquals(KadreFailure.Unsupported(KadreOperation.UpdateSurface), update.rejected.single().failure)
        assertEquals(emptyList(), port.redrawCommands)
        assertEquals(emptyList(), port.updateCommands)
        assertTrue(surface.capabilities.value.allUnsupported())
    }

    @Test
    fun redrawRequestsCoalesceUntilOneTypedAcknowledgement() = runTest {
        val port = RecordingSurfaceCommandPort()
        val surface = surface(port = port, commandsEnabled = true)
        val firstEvent = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            surface.events.first()
        }

        assertEquals(KadreResult.Success(Unit), surface.requestRedraw())
        assertEquals(KadreResult.Success(Unit), surface.requestRedraw())
        assertEquals(listOf(surface.id), port.redrawCommands.map { it.surfaceId })
        assertTrue(
            surface.accept(
                SurfaceStimulus.RedrawConsumed(surface.id, port.redrawCommands.single().generation),
            ),
        )

        val redraw = assertIs<SurfaceEvent.RedrawRequested>(firstEvent.await())
        assertEquals(SurfaceRevision(0), redraw.stateRevision)
        assertEquals(KadreResult.Success(Unit), surface.requestRedraw())
        assertEquals(listOf(surface.id, surface.id), port.redrawCommands.map { it.surfaceId })
    }

    @Test
    fun staleRedrawAcknowledgementCannotConsumeANewerTicket() {
        val port = RecordingSurfaceCommandPort()
        val surface = surface(port = port, commandsEnabled = true)

        assertEquals(KadreResult.Success(Unit), surface.requestRedraw())
        val first = port.redrawCommands.single()
        assertTrue(surface.accept(SurfaceStimulus.RedrawConsumed(surface.id, first.generation)))
        assertEquals(KadreResult.Success(Unit), surface.requestRedraw())
        val second = port.redrawCommands.last()

        assertFalse(surface.accept(SurfaceStimulus.RedrawConsumed(surface.id, first.generation)))
        assertEquals(KadreResult.Success(Unit), surface.requestRedraw())
        assertEquals(2, port.redrawCommands.size)
        assertTrue(surface.accept(SurfaceStimulus.RedrawConsumed(surface.id, second.generation)))
        assertEquals(KadreResult.Success(Unit), surface.requestRedraw())
        assertEquals(3, port.redrawCommands.size)
    }

    @Test
    fun redrawPortReentrancyCannotClearANewerTicket() {
        val port = RecordingSurfaceCommandPort()
        val surface = surface(port = port, commandsEnabled = true)
        var first = true
        port.onRedraw = { command ->
            if (first) {
                first = false
                surface.accept(SurfaceStimulus.RedrawConsumed(surface.id, command.generation))
                assertEquals(KadreResult.Success(Unit), surface.requestRedraw())
                port.redrawResult = KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true))
            }
        }

        assertEquals(
            KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true)),
            surface.requestRedraw(),
        )
        assertEquals(KadreResult.Success(Unit), surface.requestRedraw())
        assertEquals(2, port.redrawCommands.size)
    }

    @Test
    fun redrawPortExceptionBecomesAPlatformFailureAndReleasesTheTicket() {
        val port = RecordingSurfaceCommandPort()
        val surface = surface(port = port, commandsEnabled = true)
        var shouldThrow = true
        port.onRedraw = {
            if (shouldThrow) {
                shouldThrow = false
                throw IllegalStateException("redraw boom")
            }
        }

        assertEquals(
            KadreResult.Failure(
                KadreFailure.PlatformFailure(
                    KadrePlatform.Fake,
                    "surface-command-port",
                    "redraw-exception",
                ),
            ),
            surface.requestRedraw(),
        )
        assertEquals(KadreResult.Success(Unit), surface.requestRedraw())
        assertEquals(2, port.redrawCommands.size)
    }

    @Test
    fun fieldsRemainUnchangedUntilThePortReportsTheirEffectiveValues() = runTest {
        val port = RecordingSurfaceCommandPort()
        val surface = surface(
            port = port,
            commandsEnabled = true,
            capabilities = phaseThreeCapabilities(),
        )
        val update = SurfaceUpdate(
            cursor = PropertyChange.Set(CursorStyle.Hidden),
            hitTesting = PropertyChange.Set(HitTestingMode.Disabled),
            inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.SuppressWhenPossible),
        )

        val rejected = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(surface.apply(update).successValue())
        assertEquals(
            listOf(SurfaceProperty.Cursor, SurfaceProperty.HitTesting, SurfaceProperty.InputDefaultBehavior),
            rejected.rejected.map { it.field },
        )
        assertEquals(CursorStyle.System(CursorIcon.Default), surface.state.value.cursor)
        assertEquals(HitTestingMode.Enabled, surface.state.value.hitTesting)
        assertEquals(InputDefaultBehavior.HostDefault, surface.state.value.inputDefaultBehavior)
        assertEquals(SurfaceRevision(0), surface.state.value.revision)

        port.updateOutcome = KadreResult.Success(
            SurfaceUpdateCommandOutcome(
                cursor = SurfaceFieldOutcome.Applied(CursorStyle.Hidden),
                hitTesting = SurfaceFieldOutcome.Applied(HitTestingMode.Disabled),
                inputDefaultBehavior = SurfaceFieldOutcome.Applied(InputDefaultBehavior.SuppressWhenPossible),
            ),
        )
        val applied = assertIs<SurfaceUpdateOutcome.Applied>(surface.apply(update).successValue())

        assertEquals(CursorStyle.Hidden, applied.state.cursor)
        assertEquals(HitTestingMode.Disabled, applied.state.hitTesting)
        assertEquals(InputDefaultBehavior.SuppressWhenPossible, applied.state.inputDefaultBehavior)
        assertEquals(SurfaceRevision(1), applied.state.revision)
    }

    @Test
    fun unsupportedPointerCaptureIsRejectedWithoutBeingSentToThePort() = runTest {
        val port = RecordingSurfaceCommandPort()
        val surface = surface(
            port = port,
            commandsEnabled = true,
            capabilities = phaseThreeCapabilities(),
        )

        val outcome = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
            surface.apply(
                SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Locked)),
            ).successValue(),
        )

        assertEquals(SurfaceProperty.PointerCapture, outcome.rejected.single().field)
        assertEquals(KadreFailure.Unsupported(KadreOperation.UpdateSurface), outcome.rejected.single().failure)
        assertEquals(emptyList(), port.updateCommands)
    }

    @Test
    fun clearIsInvalidForEverySurfaceUpdateFieldBeforePortAdmission() = runTest {
        val port = RecordingSurfaceCommandPort()
        val surface = surface(
            port = port,
            commandsEnabled = true,
            capabilities = phaseThreeCapabilities().copy(
                pointerCapture = Capability.Supported(
                    PointerCaptureMode.entries.toSet(),
                    FeatureAvailability.Available,
                ),
            ),
        )
        val cases = listOf(
            "cursor" to SurfaceUpdate(cursor = PropertyChange.Clear),
            "pointerCapture" to SurfaceUpdate(pointerCapture = PropertyChange.Clear),
            "hitTesting" to SurfaceUpdate(hitTesting = PropertyChange.Clear),
            "inputDefaultBehavior" to SurfaceUpdate(inputDefaultBehavior = PropertyChange.Clear),
        )

        cases.forEach { (field, update) ->
            assertEquals(
                KadreResult.Failure(KadreFailure.InvalidRequest(field)),
                surface.apply(update),
            )
        }

        assertEquals(emptyList(), port.updateCommands)
        assertEquals(SurfaceRevision(0), surface.state.value.revision)
    }

    @Test
    fun invalidPortFailuresAreNormalisedToTheClosedOperationDomains() = runTest {
        val reported = mutableListOf<Throwable>()
        val port = RecordingSurfaceCommandPort()
        val surface = surface(
            port = port,
            commandsEnabled = true,
            capabilities = phaseThreeCapabilities(),
            reported = reported,
        )
        port.redrawResult = KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))

        assertEquals(
            KadreResult.Failure(invalidPortFailure("invalid-redraw-failure")),
            surface.requestRedraw(),
        )

        port.updateOutcome = KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.UpdateSurface))
        assertEquals(
            KadreResult.Failure(invalidPortFailure("invalid-update-failure")),
            surface.apply(SurfaceUpdate(cursor = PropertyChange.Set(CursorStyle.Hidden))),
        )

        port.updateOutcome = KadreResult.Success(
            SurfaceUpdateCommandOutcome(
                cursor = SurfaceFieldOutcome.Rejected(KadreFailure.Closed(KadreResourceKind.Surface)),
            ),
        )
        val partial = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
            surface.apply(SurfaceUpdate(cursor = PropertyChange.Set(CursorStyle.Hidden))).successValue(),
        )
        assertEquals(invalidPortFailure("invalid-field-failure"), partial.rejected.single().failure)
        assertEquals(3, reported.size)
        reported.forEach { assertIs<KadreException>(it) }
    }

    @Test
    fun invalidFieldFailureIsReportedAfterCommitSoReentrantDetachCannotResurrectTheSurface() = runTest {
        val port = RecordingSurfaceCommandPort()
        val reported = mutableListOf<Throwable>()
        var stateObservedByReporter: org.graphiks.kadre.surface.SurfaceState? = null
        lateinit var surface: RuntimeWindowSurface
        surface = surface(
            port = port,
            commandsEnabled = true,
            capabilities = phaseThreeCapabilities(),
            failureReporter = RuntimeFailureReporter { cause ->
                stateObservedByReporter = surface.state.value
                reported += cause
                surface.detach()
            },
        )
        port.updateOutcome = KadreResult.Success(
            SurfaceUpdateCommandOutcome(
                cursor = SurfaceFieldOutcome.Applied(CursorStyle.Hidden),
                hitTesting = SurfaceFieldOutcome.Rejected(KadreFailure.Closed(KadreResourceKind.Surface)),
            ),
        )

        val outcome = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
            surface.apply(
                SurfaceUpdate(
                    cursor = PropertyChange.Set(CursorStyle.Hidden),
                    hitTesting = PropertyChange.Set(HitTestingMode.Disabled),
                ),
            ).successValue(),
        )

        assertEquals(SurfaceAttachmentState.Attached, outcome.state.attachment)
        assertEquals(CursorStyle.Hidden, outcome.state.cursor)
        assertEquals(SurfaceRevision(1), outcome.state.revision)
        assertEquals(outcome.state, stateObservedByReporter)
        assertEquals(invalidPortFailure("invalid-field-failure"), outcome.rejected.single().failure)
        assertEquals(SurfaceAttachmentState.Detached, surface.state.value.attachment)
        assertEquals(CursorStyle.Hidden, surface.state.value.cursor)
        assertEquals(SurfaceRevision(2), surface.state.value.revision)
        assertEquals(1, reported.size)
    }

    @Test
    fun updatePortExceptionBecomesAPlatformFailureWithoutChangingState() = runTest {
        val port = RecordingSurfaceCommandPort()
        val surface = surface(
            port = port,
            commandsEnabled = true,
            capabilities = phaseThreeCapabilities(),
        )
        port.onApply = { throw IllegalStateException("update boom") }

        assertEquals(
            KadreResult.Failure(
                KadreFailure.PlatformFailure(
                    KadrePlatform.Fake,
                    "surface-command-port",
                    "update-exception",
                ),
            ),
            surface.apply(SurfaceUpdate(cursor = PropertyChange.Set(CursorStyle.Hidden))),
        )
        assertEquals(CursorStyle.System(CursorIcon.Default), surface.state.value.cursor)
        assertEquals(SurfaceRevision(0), surface.state.value.revision)
    }

    @Test
    fun cancellingAnUpdateWaiterCommitsNothingAndDoesNotBlockTheNextUpdate() = runTest {
        val port = RecordingSurfaceCommandPort()
        val surface = surface(
            port = port,
            commandsEnabled = true,
            capabilities = phaseThreeCapabilities(),
        )
        val firstStarted = CompletableDeferred<Unit>()
        val neverCompletes = CompletableDeferred<KadreResult<SurfaceUpdateCommandOutcome>>()
        port.onApply = {
            firstStarted.complete(Unit)
            neverCompletes.await()
        }
        val cancelled = launch {
            surface.apply(SurfaceUpdate(cursor = PropertyChange.Set(CursorStyle.Hidden)))
        }
        firstStarted.await()

        cancelled.cancelAndJoin()
        port.onApply = { port.updateOutcome }
        port.updateOutcome = KadreResult.Success(
            SurfaceUpdateCommandOutcome(
                cursor = SurfaceFieldOutcome.Applied(CursorStyle.Hidden),
            ),
        )

        val applied = assertIs<SurfaceUpdateOutcome.Applied>(
            surface.apply(SurfaceUpdate(cursor = PropertyChange.Set(CursorStyle.Hidden))).successValue(),
        )
        assertEquals(CursorStyle.Hidden, applied.state.cursor)
        assertEquals(SurfaceRevision(1), applied.state.revision)
        assertEquals(2, port.updateCommands.size)
    }

    @Test
    fun collectorReentrantApplyPublishesItsStateBeforeOutcomeAndRemainsMonotoneThroughDetach() = runTest {
        val port = RecordingSurfaceCommandPort()
        val surface = surface(
            port = port,
            commandsEnabled = true,
            capabilities = phaseThreeCapabilities(),
        )
        port.updateOutcome = KadreResult.Success(
            SurfaceUpdateCommandOutcome(
                cursor = SurfaceFieldOutcome.Applied(CursorStyle.Hidden),
            ),
        )
        lateinit var outcome: SurfaceUpdateOutcome.Applied
        lateinit var stateObservedBeforeOutcomeEscaped: org.graphiks.kadre.surface.SurfaceState
        val collection = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            surface.events
                .onEach {
                    outcome = assertIs<SurfaceUpdateOutcome.Applied>(
                        surface.apply(
                            SurfaceUpdate(cursor = PropertyChange.Set(CursorStyle.Hidden)),
                        ).successValue(),
                    )
                    stateObservedBeforeOutcomeEscaped = surface.state.value
                    surface.detach()
                }
                .take(1)
                .toList()
        }

        assertTrue(surface.accept(SurfaceStimulus.FocusChanged(surface.id, SurfaceFocus.Focused)))
        collection.await()

        assertEquals(outcome.state, stateObservedBeforeOutcomeEscaped)
        assertEquals(CursorStyle.Hidden, outcome.state.cursor)
        assertEquals(SurfaceRevision(2), outcome.state.revision)
        assertEquals(SurfaceAttachmentState.Detached, surface.state.value.attachment)
        assertEquals(CursorStyle.Hidden, surface.state.value.cursor)
        assertEquals(SurfaceRevision(3), surface.state.value.revision)
    }

    @Test
    fun detachWinsOverAnUpdateAcknowledgementAlreadyInFlight() = runTest {
        val port = RecordingSurfaceCommandPort()
        val surface = surface(
            port = port,
            commandsEnabled = true,
            capabilities = phaseThreeCapabilities(),
        )
        val started = CompletableDeferred<Unit>()
        val acknowledgement = CompletableDeferred<KadreResult<SurfaceUpdateCommandOutcome>>()
        port.onApply = {
            started.complete(Unit)
            acknowledgement.await()
        }
        val update = async {
            surface.apply(SurfaceUpdate(cursor = PropertyChange.Set(CursorStyle.Hidden)))
        }
        started.await()

        surface.detach()
        acknowledgement.complete(
            KadreResult.Success(
                SurfaceUpdateCommandOutcome(
                    cursor = SurfaceFieldOutcome.Applied(CursorStyle.Hidden),
                ),
            ),
        )

        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface)),
            update.await(),
        )
        assertEquals(CursorStyle.System(CursorIcon.Default), surface.state.value.cursor)
        assertEquals(SurfaceRevision(1), surface.state.value.revision)
    }

    @Test
    fun detachPreservesTheTerminalSnapshotClosesEventsAndRejectsEveryLateOperation() = runTest {
        val port = RecordingSurfaceCommandPort()
        val surface = surface(
            port = port,
            commandsEnabled = true,
            capabilities = phaseThreeCapabilities(),
        )
        val events = mutableListOf<SurfaceEvent>()
        val collection = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            surface.events.toList(events)
        }
        val inputCollection = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            surface.input.events.toList()
        }
        surface.accept(SurfaceStimulus.FocusChanged(surface.id, SurfaceFocus.Focused))
        surface.requestRedraw()

        assertTrue(surface.detach())
        assertFalse(surface.detach())
        collection.await()
        assertTrue(inputCollection.isCompleted)
        inputCollection.await()
        val terminal = surface.state.value

        assertEquals(SurfaceAttachmentState.Detached, terminal.attachment)
        assertEquals(SurfaceFocus.Focused, terminal.focus)
        assertEquals(SurfaceRevision(2), terminal.revision)
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface)),
            surface.requestRedraw(),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface)),
            surface.apply(SurfaceUpdate(cursor = PropertyChange.Set(CursorStyle.Hidden))),
        )
        assertFalse(surface.accept(SurfaceStimulus.ThemeChanged(surface.id, SurfaceTheme.Dark)))
        assertFalse(
            surface.accept(
                SurfaceStimulus.RedrawConsumed(surface.id, port.redrawCommands.single().generation),
            ),
        )
        assertEquals(terminal, surface.state.value)
        assertEquals(1, events.size)
        assertIs<SurfaceEvent.FocusChanged>(events.single())
        assertTrue(surface.capabilities.value.allUnsupported())
    }

    private fun keyStimulus(surface: RuntimeWindowSurface, nativeCode: String): SurfaceStimulus.KeyChanged =
        SurfaceStimulus.KeyChanged(
            surface.id,
            PhysicalKey.Unidentified(nativeCode),
            LogicalKey.Unidentified(nativeCode),
            KeyLocation.Standard,
            KeyState.Pressed,
            repeat = false,
            KeyboardModifiers(emptySet()),
        )

    private fun surface(
        id: SurfaceId = SurfaceId(37),
        metrics: SurfaceMetrics = DEFAULT_METRICS,
        port: SurfaceCommandPort = RecordingSurfaceCommandPort(),
        textInputPort: TextInputPort = UnsupportedTextInputPort,
        commandsEnabled: Boolean = false,
        capabilities: SurfaceCapabilities = phaseThreeCapabilities(),
        reported: MutableList<Throwable> = mutableListOf(),
        failureReporter: RuntimeFailureReporter = RuntimeFailureReporter(reported::add),
        deliveryPolicy: WindowDeliveryPolicy = KadrePolicies.Default.window,
        inputDeliveryPolicy: InputDeliveryPolicy = KadrePolicies.Default.input,
        maxCollectorsPerFlow: Int = KadrePolicies.Default.resources.maxEventCollectorsPerFlow,
        collectorAllocator: RuntimeEventCollectorAllocator = RuntimeEventCollectorAllocator(
            KadrePolicies.Default.resources.maxEventCollectorsPerSession,
        ),
        sessionFailureHandler: (KadreFailure) -> Unit = {},
    ): RuntimeWindowSurface = RuntimeWindowSurface(
        id = id,
        initialSnapshot = SurfaceInitialSnapshot(
            metrics = metrics,
            focus = SurfaceFocus.Unfocused,
            visibility = SurfaceVisibility.Visible,
            occlusion = SurfaceOcclusion.Unknown,
            theme = SurfaceTheme.Unknown,
        ),
        commandPort = port,
        textInputPort = textInputPort,
        commandsEnabled = commandsEnabled,
        enabledCapabilities = capabilities,
        eventStampSource = StampSource()::next,
        failureReporter = failureReporter,
        deliveryPolicy = deliveryPolicy,
        inputDeliveryPolicy = inputDeliveryPolicy,
        maxCollectorsPerFlow = maxCollectorsPerFlow,
        collectorAllocator = collectorAllocator,
        sessionFailureHandler = sessionFailureHandler,
    )

    private class RecordingSurfaceCommandPort : SurfaceCommandPort {
        val redrawCommands = mutableListOf<SurfaceRedrawCommand>()
        val updateCommands = mutableListOf<SurfaceUpdateCommand>()
        var redrawResult: KadreResult<Unit> = KadreResult.Success(Unit)
        var updateOutcome: KadreResult<SurfaceUpdateCommandOutcome> = KadreResult.Success(
            SurfaceUpdateCommandOutcome(
                cursor = SurfaceFieldOutcome.Rejected(KadreFailure.Unsupported(KadreOperation.UpdateSurface)),
                hitTesting = SurfaceFieldOutcome.Rejected(KadreFailure.Unsupported(KadreOperation.UpdateSurface)),
                inputDefaultBehavior = SurfaceFieldOutcome.Rejected(
                    KadreFailure.Unsupported(KadreOperation.UpdateSurface),
                ),
            ),
        )
        var onRedraw: (SurfaceRedrawCommand) -> Unit = {}
        var onApply: suspend (SurfaceUpdateCommand) -> KadreResult<SurfaceUpdateCommandOutcome> = {
            updateOutcome
        }

        override fun requestRedraw(command: SurfaceRedrawCommand): KadreResult<Unit> {
            redrawCommands += command
            onRedraw(command)
            return redrawResult
        }

        override suspend fun apply(command: SurfaceUpdateCommand): KadreResult<SurfaceUpdateCommandOutcome> {
            updateCommands += command
            return onApply(command)
        }
    }

    private class RecordingTextInputPort : TextInputPort {
        override val capability: Capability<Unit> = Capability.Supported(Unit, FeatureAvailability.Available)
        val opened = mutableListOf<TextInputOpenCommand>()
        val cursorCommands = mutableListOf<TextInputCursorCommand>()
        val documentCommands = mutableListOf<TextInputDocumentCommand>()
        var beforeDocumentSuccess: suspend (TextInputDocumentCommand) -> Unit = { }
        private val owners = mutableListOf<RecordingTextInputOwner>()
        val owner: RecordingTextInputOwner
            get() = owners.last()

        override fun open(command: TextInputOpenCommand): KadreResult<TextInputOwner> {
            opened += command
            return KadreResult.Success(RecordingTextInputOwner().also(owners::add))
        }

        override suspend fun updateCursor(command: TextInputCursorCommand): KadreResult<Unit> {
            cursorCommands += command
            return KadreResult.Success(Unit)
        }

        override suspend fun updateDocument(command: TextInputDocumentCommand): KadreResult<Unit> {
            documentCommands += command
            beforeDocumentSuccess(command)
            return KadreResult.Success(Unit)
        }

        fun emit(observation: TextInputObservation): Boolean = opened.single().onObservation(observation)
    }

    private class RecordingTextInputOwner : TextInputOwner {
        var closed = false

        override fun close() {
            closed = true
        }
    }

    private class StampSource {
        private var sequence = 0L

        fun next(): EventStamp {
            val current = sequence++
            return EventStamp(SessionSequence(current), SessionInstant(current.nanoseconds), null)
        }
    }

    private fun phaseThreeCapabilities(): SurfaceCapabilities = SurfaceCapabilities(
        cursor = Capability.Supported(CursorIcon.entries.toSet(), FeatureAvailability.Available),
        customCursor = unsupported(KadreOperation.UpdateSurface),
        pointerCapture = unsupported(KadreOperation.UpdateSurface),
        hitTesting = Capability.Supported(HitTestingMode.entries.toSet(), FeatureAvailability.Available),
        inputDefaultBehavior = Capability.Supported(
            InputDefaultBehavior.entries.toSet(),
            FeatureAvailability.Available,
        ),
        handlerInteractions = unsupported(KadreOperation.InstallInteractionHandler),
        armedInteractions = unsupported(KadreOperation.ArmInteraction),
        platformAccess = unsupported(KadreOperation.PlatformSurfaceAccess),
    )

    private fun SurfaceCapabilities.allUnsupported(): Boolean = listOf<Capability<*>>(
        cursor,
        customCursor,
        pointerCapture,
        hitTesting,
        inputDefaultBehavior,
        handlerInteractions,
        armedInteractions,
        platformAccess,
    ).all { it is Capability.Unsupported }

    private fun assertAllInputObservationCapabilitiesUnsupported(surface: RuntimeWindowSurface) {
        val capabilities = surface.input.state.value.capabilities
        assertEquals(FeatureAvailability.Unsupported, capabilities.keyboard)
        assertEquals(FeatureAvailability.Unsupported, capabilities.pointer)
        assertAllOtherInputCapabilitiesUnsupported(surface)
    }

    private fun assertAllOtherInputCapabilitiesUnsupported(surface: RuntimeWindowSurface) {
        val capabilities = surface.input.state.value.capabilities
        assertEquals(FeatureAvailability.Unsupported, capabilities.touch)
        assertEquals(FeatureAvailability.Unsupported, capabilities.gestures)
        assertEquals(FeatureAvailability.Unsupported, capabilities.dragAndDrop)
        assertIs<Capability.Unsupported>(capabilities.textInput)
        assertIs<Capability.Unsupported>(capabilities.rawInput)
    }

    private fun <T> unsupported(operation: KadreOperation): Capability<T> =
        Capability.Unsupported(KadreFailure.Unsupported(operation))

    private fun invalidPortFailure(code: String): KadreFailure.PlatformFailure =
        KadreFailure.PlatformFailure(KadrePlatform.Fake, "surface-command-port", code)

    private fun metrics(width: Double, height: Double): SurfaceMetrics = SurfaceMetrics(
        logicalSize = LogicalSize(width, height),
        physicalSize = PhysicalSize(width.toInt(), height.toInt()),
        scaleFactor = 1.0,
        safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
    )

    private fun <T> KadreResult<T>.successValue(): T = when (this) {
        is KadreResult.Success -> value
        is KadreResult.Failure -> error("expected success, got $reason")
    }

    private companion object {
        val DEFAULT_METRICS = SurfaceMetrics(
            logicalSize = LogicalSize(640.0, 360.0),
            physicalSize = PhysicalSize(640, 360),
            scaleFactor = 1.0,
            safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
        )
    }
}
