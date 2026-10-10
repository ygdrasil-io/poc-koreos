package org.graphiks.kadre.internal.runtime

import kotlin.concurrent.AtomicInt
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.interaction.InteractionAction
import org.graphiks.kadre.interaction.InteractionHandler
import org.graphiks.kadre.interaction.InteractionKind
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceCapabilities
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility

/**
 * Native tests for the ported `RuntimeWindowSurface` synchronous-interaction dispatch.
 *
 * The JVM original serialised the dispatch slot with a `ReentrantLock` + `Condition` wait; the
 * common version holds one recursive `RuntimeLock` across the whole dispatch and defers
 * same-thread re-entry. These tests exercise both paths on real native threads: a cross-worker
 * dispatch must wait for the active dispatch without losing its outcome, and a nested dispatch
 * on the dispatching thread must be deferred, not re-enter the handler.
 */
@OptIn(DelicateKadreApi::class)
class RuntimeWindowSurfaceConcurrencyTest {

    private val stamps = AtomicInt(0)

    private fun nextStamp(): EventStamp = EventStamp(
        SessionSequence(stamps.addAndGet(1).toLong()),
        SessionInstant(Duration.ZERO),
        null,
    )

    private fun surface(): RuntimeWindowSurface = RuntimeWindowSurface(
        id = RuntimeProcessIds.nextSurfaceId(),
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
            appearance = SurfaceAppearance(SurfaceTheme.Unknown, SurfaceContrast.Unknown),
        ),
        commandPort = UnsupportedSurfaceCommandPort,
        commandsEnabled = true,
        enabledCapabilities = SurfaceCapabilities(
            cursor = Capability.Supported(CursorIcon.entries.toSet(), FeatureAvailability.Available),
            customCursor = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface)),
            pointerCapture = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface)),
            hitTesting = Capability.Supported(HitTestingMode.entries.toSet(), FeatureAvailability.Available),
            inputDefaultBehavior = Capability.Supported(
                InputDefaultBehavior.entries.toSet(),
                FeatureAvailability.Available,
            ),
            handlerInteractions = Capability.Supported(
                setOf(InteractionKind.BeginWindowMove),
                FeatureAvailability.Available,
            ),
            armedInteractions = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.ArmInteraction)),
            platformAccess = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.PlatformSurfaceAccess)),
        ),
        eventStampSource = ::nextStamp,
        failureReporter = RuntimeFailureReporter { },
    )

    private fun dispatch(
        surface: RuntimeWindowSurface,
        button: PointerButton,
        nativeCalls: AtomicInt,
    ) {
        surface.dispatchSynchronousInteraction(
            RuntimeSynchronousInteraction.PointerPressed(button, LogicalPoint(1.0, 2.0), null),
            setOf(InteractionKind.BeginWindowMove),
        ) { _ ->
            nativeCalls.addAndGet(1)
            KadreResult.Success(Unit)
        }
    }

    @Test
    fun interactionDispatchedFromAnotherWorkerWaitsAndKeepsItsOutcome() {
        val surface = surface()
        val handlerEntered = AtomicInt(0)
        val handlerDone = AtomicInt(0)
        val nativeCalls = AtomicInt(0)
        val releaseOuter = AtomicInt(0)
        val registration = surface.installInteractionHandler(InteractionHandler { context, _ ->
            handlerEntered.addAndGet(1)
            context.request(InteractionAction.BeginWindowMove)
            // Only the first (outer) dispatch parks here: the second runs after the gate opens,
            // because it cannot claim the slot while the outer dispatch still holds it.
            if (handlerEntered.value == 1) {
                while (releaseOuter.value == 0) {
                    // park while holding the dispatch slot
                }
            }
            handlerDone.addAndGet(1)
        })
        assertIs<KadreResult.Success<*>>(registration)

        val outer = Worker.start()
        val outerJob = { dispatch(surface, PointerButton.Primary, nativeCalls) }
        val outerDone = outer.execute(TransferMode.SAFE, { outerJob }) { it() }
        while (handlerEntered.value == 0) {
            // the outer dispatch claimed the slot and its handler is parked
        }

        val inner = Worker.start()
        val innerJob = { dispatch(surface, PointerButton.Secondary, nativeCalls) }
        val innerDone = inner.execute(TransferMode.SAFE, { innerJob }) { it() }

        releaseOuter.value = 1
        outerDone.consume { }
        innerDone.consume { }
        outer.requestTermination(processScheduledJobs = false).consume { }
        inner.requestTermination(processScheduledJobs = false).consume { }

        // Both dispatches became the claimant (the second waited for the slot instead of being
        // deferred), both native outcomes ran, and both input deliveries landed.
        assertEquals(2, handlerEntered.value)
        assertEquals(2, handlerDone.value)
        assertEquals(2, nativeCalls.value)
        assertEquals(
            setOf(PointerButton.Primary, PointerButton.Secondary),
            surface.input.state.value.pointers.single().pressedButtons,
        )
    }

    @Test
    fun nestedDispatchOnTheDispatchingThreadIsDeferredWithoutReEnteringTheHandler() {
        val surface = surface()
        val handlerEntered = AtomicInt(0)
        val nesting = AtomicInt(0)
        val nativeCalls = AtomicInt(0)
        val registration = surface.installInteractionHandler(InteractionHandler { context, _ ->
            handlerEntered.addAndGet(1)
            context.request(InteractionAction.BeginWindowMove)
            if (nesting.addAndGet(1) == 1) {
                dispatch(surface, PointerButton.Secondary, nativeCalls)
            }
        })
        assertIs<KadreResult.Success<*>>(registration)

        dispatch(surface, PointerButton.Primary, nativeCalls)

        // The nested dispatch was deferred: the handler ran exactly once, the nested native
        // lambda never ran (a deferred interaction skips the handler dispatch), and the outer
        // dispatch drained the nested input delivery.
        assertEquals(1, handlerEntered.value)
        assertEquals(1, nativeCalls.value)
        assertEquals(
            setOf(PointerButton.Primary, PointerButton.Secondary),
            surface.input.state.value.pointers.single().pressedButtons,
        )
    }
}
