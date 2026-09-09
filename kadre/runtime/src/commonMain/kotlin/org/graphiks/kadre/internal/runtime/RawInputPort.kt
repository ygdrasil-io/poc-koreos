package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.flow.Flow
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DeviceId
import org.graphiks.kadre.input.RawInputUnit
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreOperation

/** Immutable native-boundary raw motion observation before one session assigns its event stamp. */
public data class RawInputPortInput(
    public val deltaX: Double,
    public val deltaY: Double,
    public val unit: RawInputUnit,
    public val deviceId: DeviceId?,
)

/**
 * Unstable backend SPI for one session's registrations with a raw-input source.
 *
 * This type is public only because backend modules are separate Gradle modules. It is not part of
 * Kadre's supported public API and may change without compatibility guarantees.
 */
public interface RawInputPort : AutoCloseable {
    /** Current backend capability before a public raw-input access is admitted. */
    public val rawInputCapability: Capability<Unit>
        get() = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RawInputAccess))

    /** Installs the one session-runtime observer for dynamic raw-input capability changes. */
    public fun installRawInputCapabilityObserver(observer: (Capability<Unit>) -> Unit): AutoCloseable =
        AutoCloseable { }

    /** Creates one independent backend registration for one public raw-input access. */
    public suspend fun requestAccess(): KadreResult<RawInputPortLease>

    /** Stops every outstanding registration. This operation is idempotent. */
    override public fun close()
}

/** Immutable observation emitted only for the registration that owns this lease. */
public sealed interface RawInputPortLeaseEvent {
    public data class Input(public val input: RawInputPortInput) : RawInputPortLeaseEvent
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
