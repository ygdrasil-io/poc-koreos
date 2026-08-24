package org.graphiks.kadre.window

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionId
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayId
import org.graphiks.kadre.display.DisplayMode
import org.graphiks.kadre.surface.BinaryImage
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.ImageConstraints
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PropertyChange

public interface WindowManager {
    public val state: StateFlow<WindowManagerState>

    public suspend fun requestWindow(spec: WindowSpec = WindowSpec()): KadreResult<WindowRequest>
}

public data class WindowManagerState(
    public val primary: Window?,
    public val windows: List<Window>,
    public val capabilities: WindowManagerCapabilities,
    public val revision: WindowManagerRevision,
) {
    init {
        require(windows.map(Window::id).distinct().size == windows.size) { "window IDs must be unique" }
        require(primary == null || windows.any { it === primary }) { "primary must be one of windows" }
    }
}

public suspend fun WindowManager.requestWindow(
    configure: WindowSpecBuilder.() -> Unit,
): KadreResult<WindowRequest> {
    val builder = WindowSpecBuilder()
    builder.configure()
    val spec = try {
        builder.build()
    } catch (_: IllegalArgumentException) {
        return KadreResult.Failure(KadreFailure.InvalidRequest("sizeConstraints"))
    }
    return requestWindow(spec)
}

public interface WindowRequest : AutoCloseable {
    public val id: WindowRequestId
    public val state: StateFlow<WindowRequestState>

    override fun close()
    public suspend fun cancel(): WindowCancellationOutcome
    public suspend fun await(): WindowRequestOutcome
}

public sealed interface WindowRequestState {
    public data object Pending : WindowRequestState
    public data class Terminated(public val outcome: WindowRequestOutcome) : WindowRequestState
}

public sealed interface WindowRequestOutcome {
    public data class OpenedHere(public val window: Window) : WindowRequestOutcome
    public data class OpenedInNewSession(public val sessionId: SessionId) : WindowRequestOutcome
    public data class Rejected(public val failure: KadreFailure) : WindowRequestOutcome
    public data object Cancelled : WindowRequestOutcome
    public data object RequesterDetached : WindowRequestOutcome
}

public sealed interface WindowCancellationOutcome {
    public data object CancelledBeforeCommit : WindowCancellationOutcome
    public data object CancellationRequested : WindowCancellationOutcome
    public data class AlreadyTerminated(public val outcome: WindowRequestOutcome) : WindowCancellationOutcome
    public data object TooLate : WindowCancellationOutcome
}

public data class WindowSpec(
    public val title: String = "",
    public val contentSize: LogicalSize = LogicalSize(800.0, 600.0),
    public val minimumSize: LogicalSize? = null,
    public val maximumSize: LogicalSize? = null,
    public val outerPosition: PhysicalPoint? = null,
    public val resizable: Boolean = true,
    public val fullscreen: FullscreenMode = FullscreenMode.Windowed,
    public val decorations: WindowDecorations = WindowDecorations.System,
    public val systemButtons: WindowSystemButtons = WindowSystemButtons.All,
    public val level: WindowLevel = WindowLevel.Normal,
    public val transparent: Boolean = false,
    public val blurBehind: Boolean = false,
    public val icon: BinaryImage? = null,
    public val contentProtection: Boolean = false,
) {
    init {
        validateSizeConstraints(contentSize, minimumSize, maximumSize)
    }
}

public class WindowSpecBuilder internal constructor() {
    public var title: String = ""
    public var contentSize: LogicalSize = LogicalSize(800.0, 600.0)
    public var minimumSize: LogicalSize? = null
    public var maximumSize: LogicalSize? = null
    public var outerPosition: PhysicalPoint? = null
    public var resizable: Boolean = true
    public var fullscreen: FullscreenMode = FullscreenMode.Windowed
    public var decorations: WindowDecorations = WindowDecorations.System
    public var systemButtons: WindowSystemButtons = WindowSystemButtons.All
    public var level: WindowLevel = WindowLevel.Normal
    public var transparent: Boolean = false
    public var blurBehind: Boolean = false
    public var icon: BinaryImage? = null
    public var contentProtection: Boolean = false

    internal fun build(): WindowSpec = WindowSpec(
        title,
        contentSize,
        minimumSize,
        maximumSize,
        outerPosition,
        resizable,
        fullscreen,
        decorations,
        systemButtons,
        level,
        transparent,
        blurBehind,
        icon,
        contentProtection,
    )
}

public enum class WindowPhase { Open, Closing, Closed }
public enum class WindowDecorations { System, Borderless }
public enum class WindowSystemButtons { All, CloseOnly, None }
public enum class WindowLevel { Normal, Floating, Modal }
public enum class WindowAttention { None, Informational, Critical }
public enum class ResizeEdge { North, NorthEast, East, SouthEast, South, SouthWest, West, NorthWest }

public sealed interface FullscreenMode {
    public data object Windowed : FullscreenMode
    public data object Borderless : FullscreenMode
    public data class Exclusive(public val displayId: DisplayId, public val mode: DisplayMode) : FullscreenMode
}

public data class WindowState(
    public val phase: WindowPhase,
    public val title: String,
    public val outerBounds: PhysicalRect?,
    public val contentSize: LogicalSize,
    public val minimumSize: LogicalSize?,
    public val maximumSize: LogicalSize?,
    public val resizable: Boolean,
    public val fullscreen: FullscreenMode,
    public val decorations: WindowDecorations,
    public val systemButtons: WindowSystemButtons,
    public val level: WindowLevel,
    public val transparent: Boolean,
    public val blurBehind: Boolean,
    public val icon: BinaryImage?,
    public val contentProtection: Boolean,
    public val revision: WindowRevision,
) {
    init {
        validateSizeConstraints(contentSize, minimumSize, maximumSize)
    }
}

public data class WindowCapabilities(
    public val title: Capability<Unit>,
    public val outerPosition: Capability<Unit>,
    public val contentSize: Capability<LogicalSizeRange>,
    public val minimumSize: Capability<LogicalSizeRange>,
    public val maximumSize: Capability<LogicalSizeRange>,
    public val resizable: Capability<Unit>,
    public val fullscreen: Capability<Set<FullscreenKind>>,
    public val decorations: Capability<Set<WindowDecorations>>,
    public val systemButtons: Capability<Set<WindowSystemButtons>>,
    public val level: Capability<Set<WindowLevel>>,
    public val transparency: Capability<Unit>,
    public val blurBehind: Capability<Unit>,
    public val icon: Capability<ImageConstraints>,
    public val attention: Capability<Set<WindowAttention>>,
    public val contentProtection: Capability<Unit>,
    public val closeInterception: Capability<Unit>,
    public val platformAccess: Capability<Unit>,
)

public data class LogicalSizeRange(
    public val minimum: LogicalSize?,
    public val maximum: LogicalSize?,
    public val increments: LogicalSize?,
) {
    init {
        validateMinimumMaximum(minimum, maximum)
    }
}

public enum class FullscreenKind { Borderless, Exclusive }

public data class WindowManagerCapabilities(
    public val requestWindow: Capability<Set<WindowCreationMode>>,
)

public enum class WindowCreationMode { OpenedHere, OpenedInNewSession }

public data class WindowUpdate(
    public val title: PropertyChange<String> = PropertyChange.Unchanged,
    public val outerPosition: PropertyChange<PhysicalPoint> = PropertyChange.Unchanged,
    public val contentSize: PropertyChange<LogicalSize> = PropertyChange.Unchanged,
    public val minimumSize: PropertyChange<LogicalSize> = PropertyChange.Unchanged,
    public val maximumSize: PropertyChange<LogicalSize> = PropertyChange.Unchanged,
    public val resizable: PropertyChange<Boolean> = PropertyChange.Unchanged,
    public val fullscreen: PropertyChange<FullscreenMode> = PropertyChange.Unchanged,
    public val decorations: PropertyChange<WindowDecorations> = PropertyChange.Unchanged,
    public val systemButtons: PropertyChange<WindowSystemButtons> = PropertyChange.Unchanged,
    public val level: PropertyChange<WindowLevel> = PropertyChange.Unchanged,
    public val transparency: PropertyChange<Boolean> = PropertyChange.Unchanged,
    public val blurBehind: PropertyChange<Boolean> = PropertyChange.Unchanged,
    public val icon: PropertyChange<BinaryImage> = PropertyChange.Unchanged,
    public val contentProtection: PropertyChange<Boolean> = PropertyChange.Unchanged,
    public val expectedRevision: WindowRevision? = null,
)

public interface Window {
    public val id: WindowId
    public val surface: HostSurface
    public val state: StateFlow<WindowState>
    public val capabilities: StateFlow<WindowCapabilities>
    public val events: Flow<WindowEvent>

    public suspend fun apply(update: WindowUpdate): KadreResult<WindowUpdateOutcome>
    public suspend fun requestAttention(attention: WindowAttention): KadreResult<Unit>
    public suspend fun close(): KadreResult<WindowCloseOutcome>
    public suspend fun respondToCloseRequest(
        requestId: WindowCloseRequestId,
        decision: WindowCloseDecision,
    ): KadreResult<WindowCloseResponseOutcome>
}

public sealed interface WindowUpdateOutcome {
    public data class Applied(public val operationId: WindowOperationId, public val state: WindowState) : WindowUpdateOutcome

    public data class PartiallyApplied(
        public val operationId: WindowOperationId,
        public val state: WindowState,
        public val rejected: List<RejectedWindowField>,
    ) : WindowUpdateOutcome {
        init { require(rejected.isNotEmpty()) { "rejected must not be empty" } }
    }

    public data class Accepted(public val operationId: WindowOperationId) : WindowUpdateOutcome
}

public data class RejectedWindowField(public val field: WindowProperty, public val failure: KadreFailure)

public enum class WindowProperty {
    Title,
    OuterPosition,
    ContentSize,
    MinimumSize,
    MaximumSize,
    Resizable,
    Fullscreen,
    Decorations,
    SystemButtons,
    Level,
    Transparency,
    Blur,
    Icon,
    ContentProtection,
}

public sealed interface WindowCloseOutcome {
    public data object Closed : WindowCloseOutcome
    public data class Accepted(public val operationId: WindowOperationId) : WindowCloseOutcome
}

public enum class WindowCloseDecision { Accept, Reject }

public sealed interface WindowCloseResponseOutcome {
    public data object KeptOpen : WindowCloseResponseOutcome
    public data class Closing(public val operationId: WindowOperationId) : WindowCloseResponseOutcome
    public data object TooLate : WindowCloseResponseOutcome
    public data object AlreadyResolved : WindowCloseResponseOutcome
}

public enum class WindowCloseReason { User, System, ParentHost, SessionStopping }

public sealed interface WindowEvent {
    public val stamp: EventStamp
    public val stateRevision: WindowRevision
    public val operationId: WindowOperationId?

    public data class GeometryChanged(
        public val state: WindowState,
        override val operationId: WindowOperationId?,
        override val stamp: EventStamp,
    ) : WindowEvent {
        override val stateRevision: WindowRevision get() = state.revision
    }

    public data class PropertiesChanged(
        public val state: WindowState,
        public val changed: Set<WindowProperty>,
        override val operationId: WindowOperationId?,
        override val stamp: EventStamp,
    ) : WindowEvent {
        init { require(changed.isNotEmpty()) { "changed must not be empty" } }
        override val stateRevision: WindowRevision get() = state.revision
    }

    public data class CloseRequested(
        public val requestId: WindowCloseRequestId,
        public val reason: WindowCloseReason,
        public val canReject: Boolean,
        public val deadline: SessionInstant?,
        override val stateRevision: WindowRevision,
        override val stamp: EventStamp,
    ) : WindowEvent {
        override val operationId: WindowOperationId? = null
    }

    public data class Closing(
        public val reason: WindowCloseReason,
        override val stateRevision: WindowRevision,
        override val operationId: WindowOperationId?,
        override val stamp: EventStamp,
    ) : WindowEvent
}

private fun validateSizeConstraints(
    contentSize: LogicalSize,
    minimumSize: LogicalSize?,
    maximumSize: LogicalSize?,
) {
    validateMinimumMaximum(minimumSize, maximumSize)
    require(minimumSize == null || contentSize.width >= minimumSize.width && contentSize.height >= minimumSize.height) {
        "contentSize must not be smaller than minimumSize"
    }
    require(maximumSize == null || contentSize.width <= maximumSize.width && contentSize.height <= maximumSize.height) {
        "contentSize must not be larger than maximumSize"
    }
}

private fun validateMinimumMaximum(minimum: LogicalSize?, maximum: LogicalSize?) {
    require(minimum == null || maximum == null || minimum.width <= maximum.width && minimum.height <= maximum.height) {
        "minimum must not exceed maximum"
    }
}
