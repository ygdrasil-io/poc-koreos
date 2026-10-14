package org.graphiks.kadre.internal.wayland

import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.internal.runtime.DisplayPort
import org.graphiks.kadre.internal.runtime.DisplayPortDisplay
import org.graphiks.kadre.internal.runtime.DisplayPortMode
import org.graphiks.kadre.internal.runtime.DisplayPortSnapshot

/** One cached output observation with its registry correlation key. */
internal data class WaylandOutputSnapshot(
    val key: Long,
    val state: org.graphiks.kffi.wayland.managed.WaylandOutputState,
)

/**
 * Assembles the all-or-nothing display snapshot from the cached output
 * observations (rebuilt at every wl_output.done by the managed outputs).
 * Assembly is pure: it reads cached copies, never the native connection.
 */
internal fun assembleDisplaySnapshot(
    outputs: List<WaylandOutputSnapshot>,
    primaryKey: Long?,
): DisplayPortSnapshot? {
    val displays = ArrayList<DisplayPortDisplay>(outputs.size)
    for (output in outputs) {
        val state = output.state
        val currentIndex = state.currentModeIndex
        if (currentIndex < 0 || currentIndex >= state.modes.size) {
            continue
        }
        val mode = state.modes[currentIndex]
        if (mode.width <= 0 || mode.height <= 0 || state.scale <= 0) {
            continue
        }
        val modes = state.modes.mapIndexed { index, m ->
            DisplayPortMode(
                key = (output.key.toLong() shl 8) or index.toLong(),
                physicalSize = PhysicalSize(m.width, m.height),
                refreshRateHz = if (m.refreshMillihertz > 0) m.refreshMillihertz / 1000.0 else null,
                bitDepth = null,
            )
        }
        displays.add(
            DisplayPortDisplay(
                key = output.key,
                type = DisplayType.Physical,
                name = state.name,
                bounds = PhysicalRect(
                    org.graphiks.kadre.surface.PhysicalPoint(state.geometryX, state.geometryY),
                    PhysicalSize(mode.width, mode.height),
                ),
                workArea = null,
                scaleFactor = state.scale.toDouble(),
                currentModeKey = (output.key.toLong() shl 8) or currentIndex.toLong(),
                modes = modes,
            ),
        )
    }
    if (outputs.isNotEmpty() && displays.isEmpty()) {
        return null
    }
    return DisplayPortSnapshot(
        primaryKey = primaryKey?.takeIf { candidate -> displays.any { it.key == candidate } },
        displays = displays,
    )
}

/** DisplayPort over the managed wl_output set of one wayland connection. */
internal class WaylandDisplayPort(
    private val outputsProvider: () -> List<WaylandOutputSnapshot>,
    private val rescan: ((() -> Unit) -> AutoCloseable),
) : DisplayPort {

    override val enumerationCapability: Capability<Unit>
        get() = Capability.Supported(Unit, FeatureAvailability.Available)

    override suspend fun requestSnapshot(): KadreResult<DisplayPortSnapshot> {
        val snapshot = assembleDisplaySnapshot(outputsProvider(), primaryKey = null)
            ?: return KadreResult.Failure(
                KadreFailure.PlatformFailure(
                    org.graphiks.kadre.diagnostics.KadrePlatform.Wayland,
                    "display-enumeration",
                    "no-usable-output",
                ),
            )
        return KadreResult.Success(snapshot)
    }

    override fun installSnapshotObserver(
        observer: (KadreResult<DisplayPortSnapshot>) -> Unit,
    ): AutoCloseable = rescan {
        val snapshot = assembleDisplaySnapshot(outputsProvider(), primaryKey = null)
        if (snapshot != null) {
            observer(KadreResult.Success(snapshot))
        }
    }

    override fun close() {}
}
