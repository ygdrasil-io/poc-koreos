package org.graphiks.kadre.interaction

public class InteractionToken internal constructor(private val value: Long) {
    init { require(value >= 0) }
    override fun equals(other: Any?): Boolean = other is InteractionToken && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "InteractionToken(<redacted>)"
}

public class InteractionRequestId internal constructor(private val value: Long) {
    init { require(value >= 0) }
    override fun equals(other: Any?): Boolean = other is InteractionRequestId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "InteractionRequestId(<redacted>)"
}
