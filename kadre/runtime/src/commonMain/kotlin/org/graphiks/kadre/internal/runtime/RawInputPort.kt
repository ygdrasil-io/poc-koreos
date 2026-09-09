package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.flow.Flow
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.RawInputEvent

/**
 * Unstable backend SPI for one session's registrations with a raw-input source.
 *
 * This type is public only because backend modules are separate Gradle modules. It is not part of
 * Kadre's supported public API and may change without compatibility guarantees.
 */
public interface RawInputPort : AutoCloseable {
    /** Creates one independent backend registration for one public raw-input access. */
    public suspend fun requestAccess(): KadreResult<RawInputPortLease>

    /** Stops every outstanding registration. This operation is idempotent. */
    override public fun close()
}

/** Immutable observation emitted only for the registration that owns this lease. */
public sealed interface RawInputPortLeaseEvent {
    public data class Input(public val event: RawInputEvent) : RawInputPortLeaseEvent
    public data class Availability(public val availability: FeatureAvailability) : RawInputPortLeaseEvent
    public data class Terminal(public val failure: KadreFailure) : RawInputPortLeaseEvent
}

/**
 * Unstable backend SPI for one independently closeable raw-input registration.
 *
 * A port must not multiplex several registrations through this flow: process-wide fan-out belongs
 * at the native broker boundary and each lease is owned by exactly one runtime access.
 */
public interface RawInputPortLease : AutoCloseable {
    public val events: Flow<RawInputPortLeaseEvent>

    /** Stops this registration only. This operation is idempotent. */
    override public fun close()
}
