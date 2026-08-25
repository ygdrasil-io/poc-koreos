package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.InputCapabilities
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.InputStateRevision
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.KeyboardState
import org.graphiks.kadre.input.SurfaceInput
import org.graphiks.kadre.input.SurfaceInputState
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.RejectedSurfaceField
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceCapabilities
import org.graphiks.kadre.surface.SurfaceEvent
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceProperty
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceUpdate
import org.graphiks.kadre.surface.SurfaceUpdateOutcome
import org.graphiks.kadre.surface.SurfaceVisibility
import kotlin.math.ceil

internal class MinimalWindowSurface(
    override val id: SurfaceId,
    logicalSize: LogicalSize,
) : HostSurface {
    private val mutableState = MutableStateFlow(initialState(logicalSize))
    private val mutableCapabilities = MutableStateFlow(unsupportedSurfaceCapabilities())
    private val mutableEvents = MutableSharedFlow<SurfaceEvent>()

    override val state: StateFlow<SurfaceState> = mutableState.asStateFlow()
    override val capabilities: StateFlow<SurfaceCapabilities> = mutableCapabilities.asStateFlow()
    override val events: Flow<SurfaceEvent> = mutableEvents.asSharedFlow()
    override val input: SurfaceInput = MinimalSurfaceInput()

    override fun requestRedraw(): KadreResult<Unit> = if (isDetached()) {
        KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface))
    } else {
        KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = false))
    }

    override suspend fun apply(update: SurfaceUpdate): KadreResult<SurfaceUpdateOutcome> {
        val current = mutableState.value
        if (current.attachment == SurfaceAttachmentState.Detached) {
            return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface))
        }
        update.expectedRevision?.let { expected ->
            if (expected != current.revision) {
                return KadreResult.Failure(
                    KadreFailure.StaleRevision(expected.value, current.revision.value),
                )
            }
        }
        val rejected = changedProperties(update).map { property ->
            RejectedSurfaceField(property, KadreFailure.Unsupported(KadreOperation.UpdateSurface))
        }
        return KadreResult.Success(
            if (rejected.isEmpty()) {
                SurfaceUpdateOutcome.Applied(current)
            } else {
                SurfaceUpdateOutcome.PartiallyApplied(current, rejected)
            },
        )
    }

    fun detach() {
        val current = mutableState.value
        if (current.attachment == SurfaceAttachmentState.Detached) return
        mutableState.value = current.copy(
            attachment = SurfaceAttachmentState.Detached,
            revision = SurfaceRevision(current.revision.value + 1L),
        )
    }

    private fun isDetached(): Boolean = mutableState.value.attachment == SurfaceAttachmentState.Detached
}

private class MinimalSurfaceInput : SurfaceInput {
    private val mutableEvents = MutableSharedFlow<InputEvent>()
    private val mutableState = MutableStateFlow(
        SurfaceInputState(
            keyboard = KeyboardState(emptySet()),
            pointers = emptyList(),
            touches = emptyList(),
            modifiers = KeyboardModifiers(emptySet()),
            capabilities = InputCapabilities(
                keyboard = FeatureAvailability.Unsupported,
                pointer = FeatureAvailability.Unsupported,
                touch = FeatureAvailability.Unsupported,
                gestures = FeatureAvailability.Unsupported,
                dragAndDrop = FeatureAvailability.Unsupported,
                textInput = unsupported(KadreOperation.TextInput),
                rawInput = unsupported(KadreOperation.RawInputAccess),
            ),
            revision = InputStateRevision(0L),
        ),
    )

    override val events: Flow<InputEvent> = mutableEvents.asSharedFlow()
    override val state: StateFlow<SurfaceInputState> = mutableState.asStateFlow()
}

private fun initialState(logicalSize: LogicalSize): SurfaceState = SurfaceState(
    attachment = SurfaceAttachmentState.Attached,
    logicalSize = logicalSize,
    physicalSize = PhysicalSize(
        ceil(logicalSize.width).toInt().coerceAtLeast(1),
        ceil(logicalSize.height).toInt().coerceAtLeast(1),
    ),
    scaleFactor = 1.0,
    safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
    visibility = SurfaceVisibility.Visible,
    occlusion = SurfaceOcclusion.Unknown,
    focus = SurfaceFocus.Unfocused,
    theme = SurfaceTheme.Unknown,
    cursor = CursorStyle.System(CursorIcon.Default),
    pointerCapture = PointerCaptureMode.None,
    hitTesting = HitTestingMode.Enabled,
    inputDefaultBehavior = InputDefaultBehavior.HostDefault,
    revision = SurfaceRevision(0L),
)

private fun unsupportedSurfaceCapabilities(): SurfaceCapabilities = SurfaceCapabilities(
    cursor = unsupported(KadreOperation.UpdateSurface),
    customCursor = unsupported(KadreOperation.UpdateSurface),
    pointerCapture = unsupported(KadreOperation.UpdateSurface),
    hitTesting = unsupported(KadreOperation.UpdateSurface),
    inputDefaultBehavior = unsupported(KadreOperation.UpdateSurface),
    handlerInteractions = unsupported(KadreOperation.InstallInteractionHandler),
    armedInteractions = unsupported(KadreOperation.ArmInteraction),
    platformAccess = unsupported(KadreOperation.PlatformSurfaceAccess),
)

private fun changedProperties(update: SurfaceUpdate): List<SurfaceProperty> = buildList {
    if (update.cursor !is PropertyChange.Unchanged) add(SurfaceProperty.Cursor)
    if (update.pointerCapture !is PropertyChange.Unchanged) add(SurfaceProperty.PointerCapture)
    if (update.hitTesting !is PropertyChange.Unchanged) add(SurfaceProperty.HitTesting)
    if (update.inputDefaultBehavior !is PropertyChange.Unchanged) add(SurfaceProperty.InputDefaultBehavior)
}

private fun <T> unsupported(operation: KadreOperation): Capability<T> =
    Capability.Unsupported(KadreFailure.Unsupported(operation))
