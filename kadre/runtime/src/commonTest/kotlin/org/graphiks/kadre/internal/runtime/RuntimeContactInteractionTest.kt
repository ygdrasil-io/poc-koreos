@file:OptIn(org.graphiks.kadre.diagnostics.DelicateKadreApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.*
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.input.*
import org.graphiks.kadre.interaction.*
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.surface.*
import kotlin.test.*
import kotlin.time.Duration.Companion.nanoseconds

class RuntimeContactInteractionTest {
    @Test fun touchInteractionAndInputShareIdPressureAndStamp() = runTest {
        val surface = surface(); val token = Any(); var interaction: InteractionEvent.TouchStarted? = null
        surface.installInteractionHandler(InteractionHandler { _, event ->
            interaction = assertIs<InteractionEvent.TouchStarted>(event)
            assertTrue(surface.input.state.value.touches.isEmpty(), "reservation must not publish before the handler")
        })
        val events = mutableListOf<InputEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { surface.input.events.collect { events += it } }
        dispatch(surface, RuntimeSynchronousInteraction.TouchContactStarted(token, LogicalPoint(2.0, 3.0), 0.5, DeviceId(4)))
        val input = assertIs<InputEvent.TouchChanged>(events.single())
        assertEquals(checkNotNull(interaction).touchId, input.touchId)
        assertEquals(checkNotNull(interaction).stamp, input.stamp)
        assertEquals(0.5, input.pressure); assertEquals(DeviceId(4), input.deviceId)
        surface.accept(SurfaceStimulus.TouchChanged(surface.id, token, TouchPhase.Moved, LogicalPoint(4.0, 5.0), 0.6))
        assertEquals(input.touchId, surface.input.state.value.touches.single().id)
    }

    @Test fun nestedContactKeepsItsMetadataWithoutReenteringTheHandler() = runTest {
        val surface = surface(); var callbacks = 0
        val pen = PenState(10.0, -20.0, 0.1, null); val penToken = Any()
        surface.installInteractionHandler(InteractionHandler { _, _ ->
            callbacks++
            dispatch(surface, RuntimeSynchronousInteraction.PointerPressed(PointerButton.Barrel, LogicalPoint(6.0, 7.0),
                0.75, PointerKind.Pen, pen, penToken, DeviceId(9)))
        })
        val events = mutableListOf<InputEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { surface.input.events.collect { events += it } }
        dispatch(surface, RuntimeSynchronousInteraction.TouchContactStarted(Any(), LogicalPoint(2.0, 3.0), 0.5))
        assertEquals(1, callbacks); assertEquals(2, events.size)
        val pointer = assertIs<InputEvent.PointerButtonChanged>(events[1])
        assertEquals(PointerKind.Pen, pointer.kind); assertEquals(pen, pointer.pen)
        assertEquals(0.75, pointer.pressure); assertEquals(DeviceId(9), pointer.deviceId)
        assertTrue(events[0].stamp.sequence.value < pointer.stamp.sequence.value)
    }

    @Test fun focusChangeAbandonsTheUnpublishedReservation() {
        val surface = surface(); val token = Any()
        surface.accept(SurfaceStimulus.FocusChanged(surface.id, SurfaceFocus.Focused))
        val registration = assertIs<KadreResult.Success<InteractionRegistration>>(surface.installInteractionHandler(InteractionHandler { _, _ ->
            surface.accept(SurfaceStimulus.FocusChanged(surface.id, SurfaceFocus.Unfocused))
        })).value
        dispatch(surface, RuntimeSynchronousInteraction.TouchContactStarted(token, LogicalPoint(2.0, 3.0), 0.5))
        assertTrue(surface.input.state.value.touches.isEmpty())
        registration.close()
        dispatch(surface, RuntimeSynchronousInteraction.TouchContactStarted(token, LogicalPoint(2.0, 3.0), 0.5))
        assertEquals(1, surface.input.state.value.touches.size)
    }

    @Test fun nestedPointerAdmissionIsBoundedBeforeTheHandlerReturns() {
        val surface = surface(KadrePolicies.Default.resources.copy(maxCollectionElementsPerValue=2))
        val token = Any(); var unavailableDuringHandler = false; var callbacks = 0
        surface.installInteractionHandler(InteractionHandler { _, _ ->
            callbacks++
            repeat(3) { dispatch(surface, RuntimeSynchronousInteraction.PointerPressed(
                PointerButton.Primary, LogicalPoint(2.0, 3.0), null, nativeIdentity=token)) }
            unavailableDuringHandler = surface.input.state.value.capabilities.pointer is FeatureAvailability.Unavailable
        })
        dispatch(surface, RuntimeSynchronousInteraction.TouchContactStarted(Any(), LogicalPoint(2.0, 3.0), 0.5))
        assertTrue(unavailableDuringHandler, "nested input must consume bounded ingress before publication")
        assertTrue(surface.input.state.value.pointers.isEmpty())
        dispatch(surface, RuntimeSynchronousInteraction.PointerPressed(PointerButton.Primary, LogicalPoint(2.0, 3.0), null))
        assertEquals(1, callbacks, "a failed input source must revoke later handler admission too")
    }

    private fun dispatch(surface: RuntimeWindowSurface, input: RuntimeSynchronousInteraction) =
        surface.dispatchSynchronousInteraction(input, emptySet()) { KadreResult.Success(Unit) }

    private fun surface(resources: ResourceBudgetPolicy = KadrePolicies.Default.resources): RuntimeWindowSurface {
        var sequence = 0L
        return RuntimeWindowSurface(SurfaceId(1), SurfaceInitialSnapshot(
            SurfaceMetrics(LogicalSize(640.0, 360.0), PhysicalSize(640, 360), 1.0, LogicalInsets(0.0, 0.0, 0.0, 0.0)),
            SurfaceFocus.Unfocused, SurfaceVisibility.Visible, SurfaceOcclusion.Unknown,
            SurfaceAppearance(SurfaceTheme.Unknown, SurfaceContrast.Unknown)), UnsupportedSurfaceCommandPort,
            commandsEnabled=true, enabledCapabilities=SurfaceCapabilities(
                cursor=unsupported(KadreOperation.UpdateSurface), customCursor=unsupported(KadreOperation.UpdateSurface),
                pointerCapture=unsupported(KadreOperation.UpdateSurface), hitTesting=unsupported(KadreOperation.UpdateSurface),
                inputDefaultBehavior=unsupported(KadreOperation.UpdateSurface),
                handlerInteractions=Capability.Supported(setOf(InteractionKind.BeginWindowMove), FeatureAvailability.Available),
                armedInteractions=unsupported(KadreOperation.ArmInteraction), platformAccess=unsupported(KadreOperation.PlatformSurfaceAccess)),
            eventStampSource={ EventStamp(SessionSequence(sequence), SessionInstant(sequence++.nanoseconds), null) }, resources=resources)
    }
}
