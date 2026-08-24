package org.graphiks.kadre.display

public class DisplayId internal constructor(private val value: Long) {
    init { require(value >= 0) }
    override fun equals(other: Any?): Boolean = other is DisplayId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "DisplayId(<redacted>)"
}

@JvmInline public value class DisplayManagerRevision internal constructor(public val value: Long) {
    init { require(value >= 0) }
}

@JvmInline public value class DisplayRevision internal constructor(public val value: Long) {
    init { require(value >= 0) }
}
