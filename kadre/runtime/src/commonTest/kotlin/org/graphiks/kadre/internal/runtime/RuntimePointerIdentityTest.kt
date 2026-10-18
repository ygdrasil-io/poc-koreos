@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.*
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.input.*
import org.graphiks.kadre.policy.*
import org.graphiks.kadre.surface.*
import kotlin.test.*
import kotlin.time.Duration.Companion.nanoseconds

class RuntimePointerIdentityTest {
    @Test fun mouseAndPenKeepDifferentButtonsAndIdentities() {
        val input = identityInput()
        // Equal native values are deliberately distinct references.
        data class Token(val nativeId: Int)
        val mouse = Token(5); val pen = Token(5)
        input.accept(button(mouse, PointerKind.Mouse, PointerButton.Primary))
        input.accept(button(pen, PointerKind.Pen, PointerButton.Barrel))
        assertEquals(2, input.state.value.pointers.size)
        val mouseId = input.state.value.pointers.first { it.kind == PointerKind.Mouse }.id
        val penId = input.state.value.pointers.first { it.kind == PointerKind.Pen }.id
        assertNotEquals(mouseId, penId)
        input.accept(button(mouse, PointerKind.Mouse, PointerButton.Primary, PointerButtonState.Released))
        assertEquals(emptySet(), input.state.value.pointers.first { it.id == mouseId }.pressedButtons)
        assertEquals(setOf(PointerButton.Barrel), input.state.value.pointers.first { it.id == penId }.pressedButtons)
    }

    @Test fun legacyPacketsStillUseTheHistoricalPointer() {
        val input = identityInput()
        input.accept(button(null, PointerKind.Mouse, PointerButton.Primary))
        val id = input.state.value.pointers.single().id
        input.accept(SurfaceStimulus.PointerMoved(SurfaceId(1), PointerKind.Mouse, LogicalPoint(3.0, 4.0),
            LogicalDelta(1.0, 1.0), null, null))
        assertEquals(id, input.state.value.pointers.single().id)
        assertEquals(setOf(PointerButton.Primary), input.state.value.pointers.single().pressedButtons)
    }

    @Test fun reusedNativeIdSurvivesOldTerminalDelivery() = runTest {
        val input = identityInput(); val token = Any(); val events = mutableListOf<InputEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            input.events.collect {
                events += it
                if (it is InputEvent.TouchChanged && it.phase == TouchPhase.Ended)
                    input.accept(touch(token, TouchPhase.Started))
            }
        }
        input.accept(touch(token, TouchPhase.Started))
        val old = input.state.value.touches.single().id
        input.accept(touch(token, TouchPhase.Ended))
        val next = input.state.value.touches.single().id
        assertNotEquals(old, next)
        assertEquals(listOf(TouchPhase.Started, TouchPhase.Ended, TouchPhase.Started),
            events.filterIsInstance<InputEvent.TouchChanged>().map { it.phase })
        input.accept(touch(token, TouchPhase.Moved))
        assertEquals(next, input.state.value.touches.single().id)
    }

    @Test fun pointerReentrySurvivesOldLeaveDelivery() = runTest {
        val input = identityInput(); val token = Any()
        val enter = SurfaceStimulus.PointerEntered(SurfaceId(1), PointerKind.Pen, LogicalPoint(1.0, 2.0), nativeIdentity=token)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { input.events.collect {
            if (it is InputEvent.PointerLeft) input.accept(enter)
        } }
        input.accept(enter); val old = input.state.value.pointers.single().id
        input.accept(SurfaceStimulus.PointerLeft(SurfaceId(1), PointerKind.Pen, nativeIdentity=token))
        assertNotEquals(old, input.state.value.pointers.single().id)
    }

    @Test fun collectionLimitAdmitsExactlyItsCapacityThenFailsTheSourceExplicitly() = runTest {
        val failures = mutableListOf<Throwable>()
        val input = identityInput(KadrePolicies.Default.resources.copy(maxCollectionElementsPerValue=2), failures)
        val events = mutableListOf<InputEvent>(); var terminal: Throwable? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            try { input.events.collect { events += it } } catch (cause: KadreException) { terminal = cause }
        }
        assertTrue(input.accept(touch(Any(), TouchPhase.Started)))
        assertTrue(input.accept(touch(Any(), TouchPhase.Started)))
        assertEquals(2, input.state.value.touches.size)
        assertFalse(input.accept(touch(Any(), TouchPhase.Started)))
        assertEquals(2, events.filterIsInstance<InputEvent.TouchChanged>().map { it.touchId }.toSet().size)
        assertIs<KadreFailure.SourceOverflow>(assertIs<KadreException>(terminal).failure)
        assertEquals(1, failures.size)
    }

    private fun button(token: Any?, kind: PointerKind, button: PointerButton, state: PointerButtonState = PointerButtonState.Pressed) =
        SurfaceStimulus.PointerButtonChanged(SurfaceId(1), kind, button, state, LogicalPoint(1.0, 2.0), null, null, nativeIdentity=token)
    private fun touch(token: Any, phase: TouchPhase) = SurfaceStimulus.TouchChanged(SurfaceId(1), token, phase, LogicalPoint(1.0, 2.0), 0.5)
}

internal fun identityInput(resources: ResourceBudgetPolicy = KadrePolicies.Default.resources,
    failures: MutableList<Throwable> = mutableListOf()): RuntimeSurfaceInput {
    var sequence = 0L
    val allocator = RuntimeEventCollectorAllocator(16)
    return RuntimeSurfaceInput(SurfaceId(1), KadrePolicies.Default.input,
        { EventStamp(SessionSequence(sequence), SessionInstant(sequence++.nanoseconds), null) }, allocator.newGate(8),
        UnsupportedTextInputPort, null, unsupported(KadreOperation.RawInputAccess), false, resources,
        RuntimeDropTransferBudget(1), null, allocator.newGate(8), { failures += it }, {})
}
