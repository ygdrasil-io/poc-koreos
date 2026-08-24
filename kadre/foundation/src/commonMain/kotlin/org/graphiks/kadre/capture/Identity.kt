package org.graphiks.kadre.capture

import kotlin.time.Duration

public class CaptureSourceId internal constructor(private val value: Long) {
    init { require(value >= 0) }
    override fun equals(other: Any?): Boolean = other is CaptureSourceId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "CaptureSourceId(<redacted>)"
}

@JvmInline public value class CaptureManagerRevision internal constructor(public val value: Long) {
    init { require(value >= 0) }
}

@JvmInline public value class CaptureConfigurationRevision internal constructor(public val value: Long) {
    init { require(value >= 0) }
}

@JvmInline public value class CaptureSourceInstant internal constructor(public val sinceCaptureStart: Duration) {
    init {
        require(sinceCaptureStart.isFinite() && !sinceCaptureStart.isNegative()) {
            "sinceCaptureStart must be finite and non-negative"
        }
    }
}
