package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.graphiks.kadre.capture.CaptureCapabilities
import org.graphiks.kadre.capture.CaptureManager
import org.graphiks.kadre.capture.CaptureManagerRevision
import org.graphiks.kadre.capture.CaptureManagerState
import org.graphiks.kadre.capture.CapturePermissionScope
import org.graphiks.kadre.capture.CapturePermissionState
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSession
import org.graphiks.kadre.capture.CaptureSources
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayCapabilities
import org.graphiks.kadre.display.DisplayEvent
import org.graphiks.kadre.display.DisplayInventory
import org.graphiks.kadre.display.DisplayManager
import org.graphiks.kadre.display.DisplayManagerRevision
import org.graphiks.kadre.display.DisplayManagerState
import org.graphiks.kadre.input.DeviceId
import org.graphiks.kadre.input.DeviceInventory
import org.graphiks.kadre.input.DeviceLifecycleEvent
import org.graphiks.kadre.input.DeviceManager
import org.graphiks.kadre.input.DeviceManagerRevision
import org.graphiks.kadre.input.DeviceManagerState
import org.graphiks.kadre.input.Gamepad
import org.graphiks.kadre.input.GamepadId
import org.graphiks.kadre.input.InputDevice
import org.graphiks.kadre.input.PermissionState
import org.graphiks.kadre.window.WindowCancellationOutcome
import org.graphiks.kadre.window.WindowManager
import org.graphiks.kadre.window.WindowManagerCapabilities
import org.graphiks.kadre.window.WindowManagerRevision
import org.graphiks.kadre.window.WindowManagerState
import org.graphiks.kadre.window.WindowRequest
import org.graphiks.kadre.window.WindowRequestId
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowRequestState
import org.graphiks.kadre.window.WindowSpec

internal class UnsupportedWindowManager(
    private val nextRequestId: () -> WindowRequestId,
) : WindowManager {
    private val mutableState = MutableStateFlow(
        WindowManagerState(
            primary = null,
            windows = emptyList(),
            capabilities = WindowManagerCapabilities(unsupportedCapability(KadreOperation.RequestWindow)),
            revision = WindowManagerRevision(0),
        ),
    )

    override val state: StateFlow<WindowManagerState> = mutableState.asStateFlow()

    override suspend fun requestWindow(spec: WindowSpec): KadreResult<WindowRequest> =
        KadreResult.Success(UnsupportedWindowRequest(nextRequestId()))
}

private class UnsupportedWindowRequest(
    override val id: WindowRequestId,
) : WindowRequest {
    private val outcome = WindowRequestOutcome.Rejected(KadreFailure.Unsupported(KadreOperation.RequestWindow))
    private val mutableState = MutableStateFlow<WindowRequestState>(WindowRequestState.Terminated(outcome))

    override val state: StateFlow<WindowRequestState> = mutableState.asStateFlow()

    override fun close() = Unit

    override suspend fun cancel(): WindowCancellationOutcome =
        WindowCancellationOutcome.AlreadyTerminated(outcome)

    override suspend fun await(): WindowRequestOutcome = outcome
}

internal class UnsupportedDisplayManager(
    collectorAllocator: RuntimeEventCollectorAllocator,
    maxCollectorsPerFlow: Int,
) : DisplayManager {
    private val unavailable = KadreFailure.Unsupported(KadreOperation.DisplayAccess)
    private val mutableState = MutableStateFlow(
        DisplayManagerState(
            inventory = DisplayInventory.Unavailable(unavailable),
            capabilities = DisplayCapabilities(unsupportedCapability(KadreOperation.DisplayAccess)),
            revision = DisplayManagerRevision(0),
        ),
    )
    private val mutableEvents = MutableSharedFlow<DisplayEvent>()

    override val state: StateFlow<DisplayManagerState> = mutableState.asStateFlow()
    override val events: Flow<DisplayEvent> = mutableEvents.asSharedFlow().withEventCollectorAdmission(
        collectorAllocator.newGate(maxCollectorsPerFlow),
    )

    override suspend fun requestAccess(): KadreResult<DisplayManagerState> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.DisplayAccess))
}

internal class UnsupportedDeviceManager(
    collectorAllocator: RuntimeEventCollectorAllocator,
    maxCollectorsPerFlow: Int,
) : DeviceManager {
    private val mutableState = MutableStateFlow(
        DeviceManagerState(
            inventory = DeviceInventory.Unsupported,
            revision = DeviceManagerRevision(0),
        ),
    )
    private val mutableEvents = MutableSharedFlow<DeviceLifecycleEvent>()

    override val state: StateFlow<DeviceManagerState> = mutableState.asStateFlow()
    override val events: Flow<DeviceLifecycleEvent> = mutableEvents.asSharedFlow().withEventCollectorAdmission(
        collectorAllocator.newGate(maxCollectorsPerFlow),
    )

    override fun device(id: DeviceId): InputDevice? = null
    override fun gamepad(id: GamepadId): Gamepad? = null
}

internal class UnsupportedCaptureManager : CaptureManager {
    private val permissionUnavailable = KadreFailure.Unsupported(KadreOperation.CapturePermission)
    private val sourcesUnavailable = KadreFailure.Unsupported(KadreOperation.CaptureRefreshSources)
    private val mutableState = MutableStateFlow(
        CaptureManagerState(
            permissions = CapturePermissionState(
                PermissionState.Unavailable(permissionUnavailable),
                PermissionState.Unavailable(permissionUnavailable),
            ),
            capabilities = CaptureCapabilities(
                screen = unsupportedCapability(KadreOperation.CaptureOpen),
                window = unsupportedCapability(KadreOperation.CaptureOpen),
                surface = unsupportedCapability(KadreOperation.CaptureOpen),
                sourceEnumeration = unsupportedCapability(KadreOperation.CaptureRefreshSources),
                hostPicker = FeatureAvailability.Unsupported,
            ),
            sources = CaptureSources.Unavailable(sourcesUnavailable),
            revision = CaptureManagerRevision(0),
        ),
    )

    override val state: StateFlow<CaptureManagerState> = mutableState.asStateFlow()

    override suspend fun requestPermission(scope: CapturePermissionScope): KadreResult<CaptureManagerState> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CapturePermission))

    override suspend fun refreshSources(): KadreResult<CaptureManagerState> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureRefreshSources))

    override suspend fun open(request: CaptureRequest): KadreResult<CaptureSession> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
}

private fun <T> unsupportedCapability(operation: KadreOperation): Capability<T> =
    Capability.Unsupported(KadreFailure.Unsupported(operation))
