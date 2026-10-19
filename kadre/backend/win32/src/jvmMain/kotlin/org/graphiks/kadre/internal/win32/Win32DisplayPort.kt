package org.graphiks.kadre.internal.win32

import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.internal.runtime.DisplayPort
import org.graphiks.kadre.internal.runtime.DisplayPortDisplay
import org.graphiks.kadre.internal.runtime.DisplayPortMode
import org.graphiks.kadre.internal.runtime.DisplayPortSnapshot
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kffi.win32.managed.Win32DisplaySnapshotValue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DisplayPort over the win32 multi-monitor inventory. The snapshot is
 * re-enumerated on demand and on system display changes ([onDisplayChanged]
 * fired by the driver on WM_DISPLAYCHANGE); assembly is all-or-nothing and
 * never partial. A snapshot whose scale or modes cannot be read is a typed
 * failure that withdraws the prior inventory (no stale displays).
 */
internal class Win32DisplayPort(
    private val inventoryProvider: () -> List<Win32DisplaySnapshotValue>,
) : DisplayPort {
    private val closed = AtomicBoolean(false)
    private val observers = CopyOnWriteArrayList<(KadreResult<DisplayPortSnapshot>) -> Unit>()

    override val enumerationCapability: Capability<Unit>
        get() = Capability.Supported(Unit, FeatureAvailability.Available)

    override suspend fun requestSnapshot(): KadreResult<DisplayPortSnapshot> {
        if (closed.get()) return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Display))
        return publish(inventoryProvider())
    }

    override fun installSnapshotObserver(
        observer: (KadreResult<DisplayPortSnapshot>) -> Unit,
    ): AutoCloseable {
        observers += observer
        return AutoCloseable { observers -= observer }
    }

    /** Re-enumerates and republishes the snapshot after a system display change. */
    internal fun onDisplayChanged() {
        if (closed.get()) return
        when (val result = publish(inventoryProvider())) {
            is KadreResult.Success -> observers.forEach { it(result) }
            is KadreResult.Failure -> observers.forEach { it(result) }
        }
    }

    override fun close() {
        closed.set(true)
        observers.clear()
    }

    private fun publish(source: List<Win32DisplaySnapshotValue>): KadreResult<DisplayPortSnapshot> {
        val snapshot = assembleDisplaySnapshot(source) ?: return KadreResult.Failure(
            KadreFailure.PlatformFailure(KadrePlatform.Win32, "display-enumeration", "no-usable-display"),
        )
        return KadreResult.Success(snapshot)
    }
}

/**
 * Assembles the all-or-nothing display snapshot from copied monitor values.
 * Assembly is pure: it reads copied scalars, never native state. A display
 * with a non-positive scale, degenerate bounds or no current mode is skipped;
 * if nothing usable remains the snapshot is null (the caller withdraws the
 * inventory instead of publishing a partial one).
 */
internal fun assembleDisplaySnapshot(
    monitors: List<Win32DisplaySnapshotValue>,
): DisplayPortSnapshot? {
    val displays = ArrayList<DisplayPortDisplay>(monitors.size)
    for (monitor in monitors) {
        val current = monitor.current ?: continue
        if (current.width <= 0 || current.height <= 0 || monitor.scale <= 0) continue
        if (monitor.workRight < monitor.workLeft || monitor.workBottom < monitor.workTop) continue
        val bounds = PhysicalRect(
            PhysicalPoint(monitor.left, monitor.top),
            PhysicalSize(current.width, current.height),
        )
        val workArea = PhysicalRect(
            PhysicalPoint(monitor.workLeft, monitor.workTop),
            PhysicalSize(monitor.workRight - monitor.workLeft, monitor.workBottom - monitor.workTop),
        )
        displays.add(
            DisplayPortDisplay(
                key = monitor.monitor,
                type = DisplayType.Physical,
                name = "DISPLAY${displays.size + 1}",
                bounds = bounds,
                workArea = workArea,
                scaleFactor = monitor.scale,
                currentModeKey = current.key,
                modes = monitor.modes.map { mode ->
                    DisplayPortMode(
                        key = mode.key,
                        physicalSize = PhysicalSize(mode.width, mode.height),
                        refreshRateHz = if (mode.hz > 0) mode.hz.toDouble() else null,
                        bitDepth = if (mode.bits > 0) mode.bits else null,
                    )
                },
            ),
        )
    }
    if (monitors.isNotEmpty() && displays.isEmpty()) return null
    return DisplayPortSnapshot(primaryKey = null, displays = displays)
}
