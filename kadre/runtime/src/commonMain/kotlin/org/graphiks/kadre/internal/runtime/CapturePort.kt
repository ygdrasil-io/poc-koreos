package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.capture.CaptureCapabilities
import org.graphiks.kadre.capture.CapturePermissionScope
import org.graphiks.kadre.capture.CapturePermissionState
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSourceKind
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.KadrePermission
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.SurfaceId

/**
 * Opaque backend key for a capture source.
 *
 * The namespace prevents collisions between backend-specific display and window identifiers. It
 * is confined to the backend/runtime boundary and never appears in a public [CaptureSourceId].
 */
public data class CapturePortSourceKey(
    public val namespace: String,
    public val value: Long,
) {
    init {
        require(namespace.isNotEmpty() && namespace.length <= 256 && namespace.all { it.code in 0x21..0x7e }) {
            "namespace must be a non-empty ASCII identifier of at most 256 code units"
        }
        require(value >= 0L) { "backend capture source key must be non-negative" }
    }
}

/** Detached backend descriptor before the runtime assigns its opaque public source identity. */
public data class CapturePortSource(
    public val key: CapturePortSourceKey,
    public val kind: CaptureSourceKind,
    public val name: String?,
    public val size: PhysicalSize?,
)

/** Detached source availability from one atomic backend capture-control-plane observation. */
public sealed interface CapturePortSources {
    public data class Enumerated(public val values: List<CapturePortSource>) : CapturePortSources {
        init {
            require(values.map(CapturePortSource::key).distinct().size == values.size) {
                "backend capture source keys must be unique"
            }
        }
    }

    public data object HostPickerOnly : CapturePortSources

    public data class PermissionRequired(public val required: Set<KadrePermission>) : CapturePortSources {
        init {
            require(required.isNotEmpty()) { "required permissions must not be empty" }
            require(required.all { it == KadrePermission.CaptureScreen || it == KadrePermission.CaptureWindow }) {
                "capture source inventory accepts only capture permissions"
            }
        }
    }

    public data class Unavailable(public val failure: KadreFailure) : CapturePortSources
}

/**
 * Complete detached capture-control-plane observation. The runtime projects this as exactly one
 * public [org.graphiks.kadre.capture.CaptureManagerState] revision.
 */
public data class CapturePortSnapshot(
    public val permissions: CapturePermissionState,
    public val capabilities: CaptureCapabilities,
    public val sources: CapturePortSources,
)

/** Backend-private target resolved by the common runtime before a capture reservation begins. */
public sealed interface CapturePortTarget {
    public data object HostChoice : CapturePortTarget

    public data class Source(public val key: CapturePortSourceKey) : CapturePortTarget

    public data class Surface(public val id: SurfaceId) : CapturePortTarget
}

/**
 * Backend-owned reservation admitted by [CapturePort.reserve].
 *
 * A successful reservation does not start frame production. It owns all native state required to
 * start later, and [close] releases that state exactly once from the runtime's point of view. A
 * backend must also release a reservation it created if its own suspending operation is cancelled
 * before it can return it to the runtime.
 */
public interface CapturePortReservation : AutoCloseable {
    /** Immutable source descriptor selected by the backend, including after a host picker. */
    public val source: CapturePortSource

    override public fun close()
}

/**
 * Unstable backend SPI for capture control-plane facts owned by one runtime session.
 *
 * No native pointer, SDK handle, or callback token may cross this boundary. Backends own the
 * port and must make [close] idempotent; the runtime only owns its observation registration.
 */
public interface CapturePort : AutoCloseable {
    /** Control-plane snapshot available before any permission request or source enumeration. */
    public val initialSnapshot: CapturePortSnapshot

    /** Requests the user-visible permission associated with [scope] and returns a full snapshot. */
    public suspend fun requestPermission(scope: CapturePermissionScope): KadreResult<CapturePortSnapshot>

    /** Refreshes the complete source inventory and returns a full snapshot, never a partial list. */
    public suspend fun refreshSources(): KadreResult<CapturePortSnapshot>

    /**
     * Reserves a target after common validation and session-budget admission.
     *
     * This must not start frame production. Backends return an operation-appropriate failure
     * rather than exposing a native handle or callback token through this SPI.
     */
    public suspend fun reserve(
        target: CapturePortTarget,
        request: CaptureRequest,
    ): KadreResult<CapturePortReservation> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))

    /** Installs later complete snapshots or a terminal control-plane failure. */
    public fun installObserver(observer: (KadreResult<CapturePortSnapshot>) -> Unit): AutoCloseable

    override public fun close()
}
