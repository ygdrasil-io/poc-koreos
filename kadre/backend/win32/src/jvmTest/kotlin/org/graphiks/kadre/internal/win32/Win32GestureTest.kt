package org.graphiks.kadre.internal.win32

import org.graphiks.kadre.input.*
import org.graphiks.kadre.internal.runtime.SurfaceStimulus
import org.graphiks.kadre.surface.*
import kotlin.test.*

class Win32GestureTest {
    @Test fun gestureReadbackAdvertisesOnlyInstalledKinds() {
        val events = mutableListOf<SurfaceStimulus.Gesture>()
        val router = Win32GestureRouter(SurfaceId(1), setOf(GestureKind.Pan), { 2.0 }, { events += it; true }, { true })
        router.accept(output(scale=1.2, rotation=0.5))
        assertEquals(setOf(GestureKind.Pan), events.map { it.kind }.toSet())
        assertFalse(GestureKind.TouchpadPressure in router.kinds)
    }
    @Test fun combinedManipulationUsesIncrementalDeltas() {
        val events = mutableListOf<SurfaceStimulus.Gesture>(); val router = router(events)
        router.accept(output(flags=1, scale=1.0, rotation=0.0))
        assertEquals(listOf(GestureKind.Pan), events.map { it.kind })
        router.accept(output(scale=1.1, rotation=0.2))
        router.accept(output(scale=1.2, rotation=0.3))
        val scales = events.filter { it.kind == GestureKind.Pinch }
        assertEquals(listOf(1.1, 1.2), scales.map { it.scale })
        assertEquals(listOf(TouchPhase.Started, TouchPhase.Moved), scales.map { it.phase })
        assertTrue(events.filter { it.kind == GestureKind.Pan }.all { it.delta == LogicalDelta(2.0, 3.0) })
        router.accept(output(flags=2, scale=1.0, rotation=0.0))
        assertEquals(3, events.count { it.phase == TouchPhase.Ended })
    }
    @Test fun resetEndsTrackedGesturesBeforeCapabilityWithdrawal() {
        val events = mutableListOf<SurfaceStimulus.Gesture>(); val router = router(events)
        router.accept(output(scale=1.1, rotation=0.3)); router.reset(); router.reset()
        assertEquals(setOf(GestureKind.Pan, GestureKind.Pinch, GestureKind.Rotation), events.filter { it.phase == TouchPhase.Cancelled }.map { it.kind }.toSet())
        assertEquals(3, events.count { it.phase == TouchPhase.Cancelled })
    }
    @Test fun doubleTapUsesOnlyTheNativeTapCount() {
        val events = mutableListOf<SurfaceStimulus.Gesture>(); val router = router(events)
        router.accept(output().copy(interactionId=2, tapCount=1)); assertTrue(events.isEmpty())
        router.accept(output().copy(interactionId=2, tapCount=2))
        assertEquals(GestureKind.DoubleTap, events.single().kind); assertEquals(TouchPhase.Ended, events.single().phase)
    }
    private fun router(events: MutableList<SurfaceStimulus.Gesture>) = Win32GestureRouter(SurfaceId(1),
        setOf(GestureKind.Pan, GestureKind.Pinch, GestureKind.Rotation, GestureKind.DoubleTap), { 2.0 }, { events += it; true }, { true })
    private fun output(flags: Int=0, scale: Double=1.0, rotation: Double=0.0) = Win32GestureOutput(1, flags, 2, 4.0, 6.0, scale, rotation, null)
}
