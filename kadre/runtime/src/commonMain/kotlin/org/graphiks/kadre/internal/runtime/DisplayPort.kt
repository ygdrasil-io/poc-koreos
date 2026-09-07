package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalSize

/** One detached native display mode before the runtime assigns its opaque public identity. */
public data class DisplayPortMode(
    public val key: Long,
    public val physicalSize: PhysicalSize,
    public val refreshRateHz: Double?,
    public val bitDepth: Int?,
)

/** One detached native display snapshot before the runtime assigns its opaque public identity. */
public data class DisplayPortDisplay(
    public val key: Long,
    public val type: DisplayType,
    public val name: String?,
    public val bounds: PhysicalRect,
    public val workArea: PhysicalRect?,
    public val scaleFactor: Double,
    public val currentModeKey: Long?,
    public val modes: List<DisplayPortMode>,
)

/** An all-or-nothing display snapshot from one backend enumeration. */
public data class DisplayPortSnapshot(
    public val primaryKey: Long?,
    public val displays: List<DisplayPortDisplay>,
)

/**
 * Unstable backend SPI for one session's display inventory observations.
 *
 * Native identifiers are only correlation keys inside the backend/runtime boundary. The runtime
 * assigns fresh public [org.graphiks.kadre.display.DisplayId] and `DisplayModeId` values.
 */
public interface DisplayPort : AutoCloseable {
    public val enumerationCapability: Capability<Unit>

    /** Requests a complete snapshot. A successful result must never contain a partial inventory. */
    public suspend fun requestSnapshot(): KadreResult<DisplayPortSnapshot>

    /**
     * Installs the session-runtime observer for later complete snapshots or enumeration failures.
     * A failure withdraws the prior inventory: a backend must never leave stale displays exposed
     * as though its failed native re-enumeration were still authoritative.
     */
    public fun installSnapshotObserver(observer: (KadreResult<DisplayPortSnapshot>) -> Unit): AutoCloseable

    override public fun close()
}
