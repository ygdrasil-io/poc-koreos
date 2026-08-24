package org.graphiks.kadre.application

import kotlin.time.Duration

public class SessionId internal constructor(private val value: Long) {
    init {
        require(value >= 0) { "value must be non-negative" }
    }

    override fun equals(other: Any?): Boolean = other is SessionId && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "SessionId(<redacted>)"
}

@JvmInline
public value class SessionSequence internal constructor(public val value: Long) {
    init {
        require(value >= 0) { "value must be non-negative" }
    }
}

@JvmInline
public value class SessionInstant internal constructor(public val sinceStart: Duration) {
    init {
        require(sinceStart.isFinite() && !sinceStart.isNegative()) {
            "sinceStart must be finite and non-negative"
        }
    }
}

public data class EventDeliverySpan(
    public val firstSequence: SessionSequence,
    public val lastSequence: SessionSequence,
    public val eventCount: Long,
) {
    init {
        require(firstSequence.value < lastSequence.value) {
            "firstSequence must precede lastSequence"
        }
        require(eventCount > 1) { "eventCount must be greater than one" }
        val difference = lastSequence.value - firstSequence.value
        require(eventCount - 1L <= difference) { "eventCount exceeds the sequence span" }
    }
}

public data class EventStamp(
    public val sequence: SessionSequence,
    public val timestamp: SessionInstant,
    public val deliverySpan: EventDeliverySpan?,
) {
    init {
        require(deliverySpan == null || deliverySpan.lastSequence == sequence) {
            "deliverySpan must end at sequence"
        }
    }
}
