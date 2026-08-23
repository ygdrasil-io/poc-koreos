package org.graphiks.kadre.surface

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.SurfaceInput
import org.graphiks.kadre.interaction.ArmedInteractionConstraints
import org.graphiks.kadre.interaction.InteractionKind

public interface HostSurface {
    public val id: SurfaceId
    public val state: StateFlow<SurfaceState>
    public val capabilities: StateFlow<SurfaceCapabilities>
    public val events: Flow<SurfaceEvent>
    public val input: SurfaceInput

    public fun requestRedraw(): KadreResult<Unit>
    public suspend fun apply(update: SurfaceUpdate): KadreResult<SurfaceUpdateOutcome>
}

public data class SurfaceState(
    public val attachment: SurfaceAttachmentState,
    public val logicalSize: LogicalSize,
    public val physicalSize: PhysicalSize,
    public val scaleFactor: Double,
    public val safeAreaInsets: LogicalInsets,
    public val visibility: SurfaceVisibility,
    public val occlusion: SurfaceOcclusion,
    public val focus: SurfaceFocus,
    public val theme: SurfaceTheme,
    public val cursor: CursorStyle,
    public val pointerCapture: PointerCaptureMode,
    public val hitTesting: HitTestingMode,
    public val inputDefaultBehavior: InputDefaultBehavior,
    public val revision: SurfaceRevision,
) {
    init {
        require(scaleFactor.isFinite() && scaleFactor > 0.0) { "scaleFactor must be finite and positive" }
    }
}

public enum class SurfaceAttachmentState { Attached, Detached }
public enum class SurfaceVisibility { Visible, Hidden }
public enum class SurfaceOcclusion { Visible, Occluded, Unknown }
public enum class SurfaceFocus { Focused, Unfocused }
public enum class SurfaceTheme { Light, Dark, Unknown }

public sealed interface SurfaceEvent {
    public val stamp: EventStamp
    public val stateRevision: SurfaceRevision

    public data class MetricsChanged(public val state: SurfaceState, override val stamp: EventStamp) : SurfaceEvent {
        override val stateRevision: SurfaceRevision get() = state.revision
    }

    public data class FocusChanged(public val state: SurfaceState, override val stamp: EventStamp) : SurfaceEvent {
        override val stateRevision: SurfaceRevision get() = state.revision
    }

    public data class VisibilityChanged(public val state: SurfaceState, override val stamp: EventStamp) : SurfaceEvent {
        override val stateRevision: SurfaceRevision get() = state.revision
    }

    public data class ThemeChanged(public val state: SurfaceState, override val stamp: EventStamp) : SurfaceEvent {
        override val stateRevision: SurfaceRevision get() = state.revision
    }

    public data class RedrawRequested(
        override val stateRevision: SurfaceRevision,
        override val stamp: EventStamp,
    ) : SurfaceEvent
}

public data class SurfaceUpdate(
    public val cursor: PropertyChange<CursorStyle> = PropertyChange.Unchanged,
    public val pointerCapture: PropertyChange<PointerCaptureMode> = PropertyChange.Unchanged,
    public val hitTesting: PropertyChange<HitTestingMode> = PropertyChange.Unchanged,
    public val inputDefaultBehavior: PropertyChange<InputDefaultBehavior> = PropertyChange.Unchanged,
    public val expectedRevision: SurfaceRevision? = null,
)

public sealed interface SurfaceUpdateOutcome {
    public data class Applied(public val state: SurfaceState) : SurfaceUpdateOutcome

    public data class PartiallyApplied(
        public val state: SurfaceState,
        public val rejected: List<RejectedSurfaceField>,
    ) : SurfaceUpdateOutcome {
        init { require(rejected.isNotEmpty()) { "rejected must not be empty" } }
    }
}

public data class RejectedSurfaceField(
    public val field: SurfaceProperty,
    public val failure: KadreFailure,
)

public enum class SurfaceProperty { Cursor, PointerCapture, HitTesting, InputDefaultBehavior }

public data class SurfaceCapabilities(
    public val cursor: Capability<Set<CursorIcon>>,
    public val customCursor: Capability<ImageConstraints>,
    public val pointerCapture: Capability<Set<PointerCaptureMode>>,
    public val hitTesting: Capability<Set<HitTestingMode>>,
    public val inputDefaultBehavior: Capability<Set<InputDefaultBehavior>>,
    public val handlerInteractions: Capability<Set<InteractionKind>>,
    public val armedInteractions: Capability<ArmedInteractionConstraints>,
    public val platformAccess: Capability<Unit>,
)

public sealed interface CursorStyle {
    public data object Hidden : CursorStyle
    public data class System(public val icon: CursorIcon) : CursorStyle
    public data class Custom(public val image: CursorImage) : CursorStyle
}

public enum class CursorIcon {
    Default,
    Pointer,
    Text,
    Crosshair,
    Move,
    Grab,
    Grabbing,
    NotAllowed,
    Wait,
    Progress,
    Help,
    ResizeHorizontal,
    ResizeVertical,
    ResizeDiagonalNorthWestSouthEast,
    ResizeDiagonalNorthEastSouthWest,
    ResizeColumn,
    ResizeRow,
    ZoomIn,
    ZoomOut,
    ContextMenu,
    Copy,
    Alias,
    Cell,
}

public enum class PointerCaptureMode { None, Confined, Locked }
public enum class HitTestingMode { Enabled, Disabled }
public enum class InputDefaultBehavior { HostDefault, SuppressWhenPossible }
