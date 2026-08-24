package org.graphiks.kadre.application

import org.graphiks.kadre.input.TextDocumentRevision
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds

class EventStampTest {
    @Test
    fun rejectsInvalidDeliverySpans() {
        assertFailsWith<IllegalArgumentException> {
            EventDeliverySpan(SessionSequence(2), SessionSequence(1), 2)
        }
        assertFailsWith<IllegalArgumentException> {
            EventDeliverySpan(SessionSequence(1), SessionSequence(2), 1)
        }
        assertFailsWith<IllegalArgumentException> {
            EventDeliverySpan(SessionSequence(1), SessionSequence(2), 3)
        }
    }

    @Test
    fun stampSequenceMustMatchSpanEnd() {
        val span = EventDeliverySpan(SessionSequence(1), SessionSequence(2), 2)

        assertFailsWith<IllegalArgumentException> {
            EventStamp(SessionSequence(3), SessionInstant(1.milliseconds), span)
        }
    }

    @Test
    fun rejectsNegativeTimeAndDocumentRevision() {
        assertFailsWith<IllegalArgumentException> { SessionInstant((-1).milliseconds) }
        assertFailsWith<IllegalArgumentException> { TextDocumentRevision(-1) }
    }
}
