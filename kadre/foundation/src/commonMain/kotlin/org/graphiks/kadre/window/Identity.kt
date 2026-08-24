package org.graphiks.kadre.window

public class WindowId internal constructor(private val value: Long) {
    init { require(value >= 0) }
    override fun equals(other: Any?): Boolean = other is WindowId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "WindowId(<redacted>)"
}

public class WindowOperationId internal constructor(private val value: Long) {
    init { require(value >= 0) }
    override fun equals(other: Any?): Boolean = other is WindowOperationId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "WindowOperationId(<redacted>)"
}

public class WindowCloseRequestId internal constructor(private val value: Long) {
    init { require(value >= 0) }
    override fun equals(other: Any?): Boolean = other is WindowCloseRequestId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "WindowCloseRequestId(<redacted>)"
}

public class WindowRequestId internal constructor(private val value: Long) {
    init { require(value >= 0) }
    override fun equals(other: Any?): Boolean = other is WindowRequestId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "WindowRequestId(<redacted>)"
}

@JvmInline public value class WindowManagerRevision internal constructor(public val value: Long) {
    init { require(value >= 0) }
}

@JvmInline public value class WindowRevision internal constructor(public val value: Long) {
    init { require(value >= 0) }
}
