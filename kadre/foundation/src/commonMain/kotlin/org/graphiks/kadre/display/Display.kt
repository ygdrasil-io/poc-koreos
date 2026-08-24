package org.graphiks.kadre.display

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalSize

public interface DisplayManager {
    public val state: StateFlow<DisplayManagerState>
    public val events: Flow<DisplayEvent>

    public suspend fun requestAccess(): KadreResult<DisplayManagerState>
}

public data class DisplayManagerState(
    public val inventory: DisplayInventory,
    public val capabilities: DisplayCapabilities,
    public val revision: DisplayManagerRevision,
)

public sealed interface DisplayInventory {
    public data class Enumerated(
        public val primary: Display?,
        public val displays: List<Display>,
    ) : DisplayInventory {
        init {
            require(displays.map(Display::id).distinct().size == displays.size) { "display IDs must be unique" }
            require(primary == null || displays.any { it === primary }) { "primary must be one of displays" }
        }
    }

    public data object PermissionRequired : DisplayInventory
    public data class PermissionDenied(public val canRequestAgain: Boolean) : DisplayInventory
    public data class Unavailable(public val failure: KadreFailure) : DisplayInventory
}

public interface Display {
    public val id: DisplayId
    public val state: StateFlow<DisplayState>
}

public enum class DisplayType { Physical, Virtual, HostViewport }
public enum class DisplayConnectionState { Connected, Disconnected }

public data class DisplayMode(
    public val physicalSize: PhysicalSize,
    public val refreshRateHz: Double?,
    public val bitDepth: Int?,
) {
    init {
        require(refreshRateHz == null || refreshRateHz.isFinite() && refreshRateHz > 0.0) {
            "refreshRateHz must be finite and positive"
        }
        require(bitDepth == null || bitDepth > 0) { "bitDepth must be positive" }
    }
}

public data class DisplayState(
    public val type: DisplayType,
    public val connection: DisplayConnectionState,
    public val name: String?,
    public val bounds: PhysicalRect,
    public val workArea: PhysicalRect?,
    public val scaleFactor: Double,
    public val currentMode: DisplayMode?,
    public val modes: List<DisplayMode>,
    public val revision: DisplayRevision,
) {
    init {
        require(scaleFactor.isFinite() && scaleFactor > 0.0) { "scaleFactor must be finite and positive" }
        require(modes.distinct().size == modes.size) { "modes must not contain duplicates" }
        require(currentMode == null || modes.isEmpty() || currentMode in modes) {
            "currentMode must belong to modes"
        }
        require(workArea == null || bounds.contains(workArea)) { "workArea must be contained in bounds" }
    }
}

public data class DisplayCapabilities(public val enumeration: Capability<Unit>)

public sealed interface DisplayEvent {
    public val stamp: EventStamp
    public val managerRevision: DisplayManagerRevision

    public data class Added(
        public val display: Display,
        public val state: DisplayState,
        override val managerRevision: DisplayManagerRevision,
        override val stamp: EventStamp,
    ) : DisplayEvent

    public data class Changed(
        public val display: Display,
        public val state: DisplayState,
        override val managerRevision: DisplayManagerRevision,
        override val stamp: EventStamp,
    ) : DisplayEvent

    public data class Removed(
        public val displayId: DisplayId,
        public val lastState: DisplayState,
        override val managerRevision: DisplayManagerRevision,
        override val stamp: EventStamp,
    ) : DisplayEvent {
        init {
            require(lastState.connection == DisplayConnectionState.Disconnected) {
                "removed display state must be disconnected"
            }
        }
    }
}

private fun PhysicalRect.contains(other: PhysicalRect): Boolean {
    val left = origin.x.toLong()
    val top = origin.y.toLong()
    val right = left + size.width
    val bottom = top + size.height
    val otherLeft = other.origin.x.toLong()
    val otherTop = other.origin.y.toLong()
    val otherRight = otherLeft + other.size.width
    val otherBottom = otherTop + other.size.height
    return otherLeft >= left && otherTop >= top && otherRight <= right && otherBottom <= bottom
}
