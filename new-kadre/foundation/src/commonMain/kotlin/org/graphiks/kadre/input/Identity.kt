package org.graphiks.kadre.input

public class DeviceId internal constructor(private val value: Long) {
    init { require(value >= 0) }
    override fun equals(other: Any?): Boolean = other is DeviceId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "DeviceId(<redacted>)"
}

public class GamepadId internal constructor(private val value: Long) {
    init { require(value >= 0) }
    override fun equals(other: Any?): Boolean = other is GamepadId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "GamepadId(<redacted>)"
}

public class PointerId internal constructor(private val value: Long) {
    init { require(value >= 0) }
    override fun equals(other: Any?): Boolean = other is PointerId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "PointerId(<redacted>)"
}

public class TouchId internal constructor(private val value: Long) {
    init { require(value >= 0) }
    override fun equals(other: Any?): Boolean = other is TouchId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "TouchId(<redacted>)"
}

public class DropOfferId internal constructor(private val value: Long) {
    init { require(value >= 0) }
    override fun equals(other: Any?): Boolean = other is DropOfferId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "DropOfferId(<redacted>)"
}

@JvmInline public value class DeviceManagerRevision internal constructor(public val value: Long) {
    init { require(value >= 0) }
}

@JvmInline public value class InputStateRevision internal constructor(public val value: Long) {
    init { require(value >= 0) }
}

@JvmInline public value class GamepadRevision internal constructor(public val value: Long) {
    init { require(value >= 0) }
}

@JvmInline public value class TextDocumentRevision(public val value: Long) {
    init { require(value >= 0) { "value must be non-negative" } }
}

public enum class KadrePermission {
    DisplayEnumeration,
    InputMonitoring,
    RawInput,
    CaptureScreen,
    CaptureWindow,
}
