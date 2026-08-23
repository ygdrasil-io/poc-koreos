package org.graphiks.kadre.surface

public class SurfaceId internal constructor(private val value: Long) {
    init {
        require(value >= 0) { "value must be non-negative" }
    }

    override fun equals(other: Any?): Boolean = other is SurfaceId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "SurfaceId(<redacted>)"
}

@JvmInline
public value class SurfaceRevision internal constructor(public val value: Long) {
    init {
        require(value >= 0) { "value must be non-negative" }
    }
}
