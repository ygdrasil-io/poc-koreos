package org.graphiks.kadre.input

import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration

class InputValuesTest {
    @Test
    fun validatesPortableKeysAndPressureDomains() {
        assertFailsWith<IllegalArgumentException> { PhysicalKey.Code(-1, 1) }
        assertFailsWith<IllegalArgumentException> { PhysicalKey.Code(1, 65_536) }
        assertFailsWith<IllegalArgumentException> { LogicalKey.Character("") }
        assertFailsWith<IllegalArgumentException> { PenState(91.0, null, null, null) }
        assertFailsWith<IllegalArgumentException> { TouchState(TouchId(0), org.graphiks.kadre.surface.LogicalPoint(0.0, 0.0), 1.1) }
    }

    @Test
    fun gesturePayloadMustMatchItsKind() {
        assertFailsWith<IllegalArgumentException> {
            InputEvent.Gesture(
                kind = GestureKind.Pinch,
                phase = TouchPhase.Moved,
                delta = org.graphiks.kadre.surface.LogicalDelta(1.0, 1.0),
                scale = 2.0,
                rotationRadians = null,
                pressure = null,
                stamp = EventStamp(SessionSequence(0), SessionInstant(Duration.ZERO), null),
                deviceId = null,
                stateRevision = InputStateRevision(0),
            )
        }
    }

    @Test
    fun penValuesCanonicalizeNegativeZero() {
        val negative = PenState(-0.0, -0.0, -0.0, -0.0)
        val positive = PenState(0.0, 0.0, 0.0, 0.0)

        assertEquals(positive, negative)
        assertEquals(positive.hashCode(), negative.hashCode())
    }

    @Test
    fun inputValuesCanonicalizeNegativeZero() {
        val stamp = EventStamp(SessionSequence(0), SessionInstant(Duration.ZERO), null)
        val pointer = PointerState(PointerId(0), PointerKind.Mouse, null, emptySet(), -0.0, null)
        val touch = TouchState(TouchId(0), LogicalPoint(0.0, 0.0), -0.0)
        val scroll = ScrollDelta.Logical(-0.0, -0.0)
        val lineScroll = ScrollDelta.Lines(-0.0, -0.0)
        val moved = InputEvent.PointerMoved(
            PointerId(0),
            PointerKind.Mouse,
            LogicalPoint(0.0, 0.0),
            LogicalDelta(0.0, 0.0),
            -0.0,
            null,
            stamp,
            null,
            InputStateRevision(0),
        )
        val button = InputEvent.PointerButtonChanged(
            PointerId(0),
            PointerKind.Mouse,
            PointerButton.Primary,
            PointerButtonState.Pressed,
            LogicalPoint(0.0, 0.0),
            -0.0,
            null,
            stamp,
            null,
            InputStateRevision(0),
        )
        val touchChanged = InputEvent.TouchChanged(
            TouchId(0),
            TouchPhase.Moved,
            LogicalPoint(0.0, 0.0),
            -0.0,
            stamp,
            null,
            InputStateRevision(0),
        )
        val rotation = InputEvent.Gesture(
            GestureKind.Rotation,
            TouchPhase.Moved,
            null,
            null,
            -0.0,
            null,
            stamp,
            null,
            InputStateRevision(0),
        )
        val touchpadPressure = InputEvent.Gesture(
            GestureKind.TouchpadPressure,
            TouchPhase.Moved,
            null,
            null,
            null,
            -0.0,
            stamp,
            null,
            InputStateRevision(0),
        )
        val raw = RawInputEvent(-0.0, -0.0, RawInputUnit.DeviceCount, null, stamp)

        assertEquals(0.0.toBits(), pointer.pressure?.toBits())
        assertEquals(0.0.toBits(), touch.pressure?.toBits())
        assertEquals(0.0.toBits(), scroll.x.toBits())
        assertEquals(0.0.toBits(), scroll.y.toBits())
        assertEquals(0.0.toBits(), lineScroll.x.toBits())
        assertEquals(0.0.toBits(), lineScroll.y.toBits())
        assertEquals(0.0.toBits(), moved.pressure?.toBits())
        assertEquals(0.0.toBits(), button.pressure?.toBits())
        assertEquals(0.0.toBits(), touchChanged.pressure?.toBits())
        assertEquals(0.0.toBits(), rotation.rotationRadians?.toBits())
        assertEquals(0.0.toBits(), touchpadPressure.pressure?.toBits())
        assertEquals(0.0.toBits(), raw.deltaX.toBits())
        assertEquals(0.0.toBits(), raw.deltaY.toBits())
    }
}
