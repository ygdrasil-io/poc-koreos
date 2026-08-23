package org.graphiks.kadre.input

import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
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
}
