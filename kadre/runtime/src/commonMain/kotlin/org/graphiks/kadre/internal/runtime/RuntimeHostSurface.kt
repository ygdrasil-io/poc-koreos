package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.InputCapabilities
import org.graphiks.kadre.input.InputStateRevision
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.KeyboardState
import org.graphiks.kadre.input.RawInputAccess
import org.graphiks.kadre.input.SurfaceInput
import org.graphiks.kadre.input.SurfaceInputState
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextInputSession
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceCapabilities
import org.graphiks.kadre.surface.SurfaceEvent
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceUpdate
import org.graphiks.kadre.surface.SurfaceUpdateOutcome

/** A host-owned portable surface whose unsupported subsystems remain explicitly unavailable. */
internal class RuntimeHostSurface(
    override val id: SurfaceId,
    initialState: SurfaceState,
) : HostSurface, AutoCloseable {
    private val lock = RuntimeLock()
    private val mutableState = MutableStateFlow(initialState)
    private val mutableCapabilities = MutableStateFlow(unsupportedHostSurfaceCapabilities())

    override val state: StateFlow<SurfaceState> = mutableState.asStateFlow()
    override val capabilities: StateFlow<SurfaceCapabilities> = mutableCapabilities.asStateFlow()
    override val events: Flow<SurfaceEvent> = emptyFlow()
    override val input: SurfaceInput = UnsupportedHostSurfaceInput

    override fun requestRedraw(): KadreResult<Unit> = lock.withLock {
        if (mutableState.value.attachment == SurfaceAttachmentState.Detached) {
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface))
        } else {
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RequestRedraw))
        }
    }

    override suspend fun apply(update: SurfaceUpdate): KadreResult<SurfaceUpdateOutcome> = lock.withLock {
        if (mutableState.value.attachment == SurfaceAttachmentState.Detached) {
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface))
        } else {
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.UpdateSurface))
        }
    }

    override fun close() {
        lock.withLock {
            val current = mutableState.value
            if (current.attachment == SurfaceAttachmentState.Detached) return
            check(current.revision.value < Long.MAX_VALUE) { "surface revision space exhausted" }
            mutableState.value = current.copy(
                attachment = SurfaceAttachmentState.Detached,
                revision = SurfaceRevision(current.revision.value + 1L),
            )
        }
    }
}

private object UnsupportedHostSurfaceInput : SurfaceInput {
    private val unsupportedTextInput = KadreFailure.Unsupported(KadreOperation.TextInput)
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
                textInput = Capability.Unsupported(unsupportedTextInput),
                rawInput = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RawInputAccess)),
            ),
            revision = InputStateRevision(0L),
        ),
    )

    override val events: Flow<org.graphiks.kadre.input.InputEvent> = emptyFlow()
    override val state: StateFlow<SurfaceInputState> = mutableState.asStateFlow()

    override suspend fun openTextInput(config: TextInputConfig): KadreResult<TextInputSession> =
        KadreResult.Failure(unsupportedTextInput)

    @OptIn(DelicateKadreApi::class)
    override suspend fun requestRawInput(): KadreResult<RawInputAccess> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RawInputAccess))
}

private fun unsupportedHostSurfaceCapabilities(): SurfaceCapabilities = SurfaceCapabilities(
    cursor = unsupportedHostSurfaceCapability(KadreOperation.UpdateSurface),
    customCursor = unsupportedHostSurfaceCapability(KadreOperation.UpdateSurface),
    pointerCapture = unsupportedHostSurfaceCapability(KadreOperation.UpdateSurface),
    hitTesting = unsupportedHostSurfaceCapability(KadreOperation.UpdateSurface),
    inputDefaultBehavior = unsupportedHostSurfaceCapability(KadreOperation.UpdateSurface),
    handlerInteractions = unsupportedHostSurfaceCapability(KadreOperation.InstallInteractionHandler),
    armedInteractions = unsupportedHostSurfaceCapability(KadreOperation.ArmInteraction),
    platformAccess = unsupportedHostSurfaceCapability(KadreOperation.PlatformSurfaceAccess),
)

private fun <T> unsupportedHostSurfaceCapability(operation: KadreOperation): Capability<T> =
    Capability.Unsupported(KadreFailure.Unsupported(operation))
