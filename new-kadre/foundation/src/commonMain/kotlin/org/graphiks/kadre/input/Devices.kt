package org.graphiks.kadre.input

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.KadreFailure

public interface DeviceManager {
    public val state: StateFlow<DeviceManagerState>
    public val events: Flow<DeviceLifecycleEvent>

    public fun device(id: DeviceId): InputDevice?
    public fun gamepad(id: GamepadId): Gamepad?
}

public data class DeviceManagerState(
    public val inventory: DeviceInventory,
    public val revision: DeviceManagerRevision,
)

public sealed interface DeviceInventory {
    public data class Enumerated(
        public val devices: List<InputDevice>,
        public val gamepads: List<Gamepad>,
    ) : DeviceInventory {
        init {
            require(devices.map(InputDevice::id).distinct().size == devices.size) { "device IDs must be unique" }
            require(gamepads.map(Gamepad::id).distinct().size == gamepads.size) { "gamepad IDs must be unique" }
        }
    }

    public data object Unsupported : DeviceInventory
    public data class Unavailable(public val failure: KadreFailure) : DeviceInventory
}

public enum class InputDeviceKind { Keyboard, Mouse, Touchscreen, Touchpad, Pen, Other }
public enum class DeviceConnectionState { Connected, Disconnected }

public data class InputDeviceDescriptor(public val name: String?, public val kind: InputDeviceKind)

public interface InputDevice {
    public val id: DeviceId
    public val descriptor: InputDeviceDescriptor
    public val connection: StateFlow<DeviceConnectionState>
}

public sealed interface DeviceLifecycleEvent {
    public val stamp: EventStamp
    public val managerRevision: DeviceManagerRevision

    public data class DeviceAdded(
        public val device: InputDevice,
        override val managerRevision: DeviceManagerRevision,
        override val stamp: EventStamp,
    ) : DeviceLifecycleEvent

    public data class DeviceRemoved(
        public val deviceId: DeviceId,
        override val managerRevision: DeviceManagerRevision,
        override val stamp: EventStamp,
    ) : DeviceLifecycleEvent

    public data class GamepadAdded(
        public val gamepad: Gamepad,
        override val managerRevision: DeviceManagerRevision,
        override val stamp: EventStamp,
    ) : DeviceLifecycleEvent

    public data class GamepadRemoved(
        public val gamepadId: GamepadId,
        override val managerRevision: DeviceManagerRevision,
        override val stamp: EventStamp,
    ) : DeviceLifecycleEvent
}

public sealed interface PermissionState {
    public data object NotDetermined : PermissionState
    public data object Granted : PermissionState
    public data class Denied(public val canRequestAgain: Boolean) : PermissionState
    public data object Restricted : PermissionState
    public data class Unavailable(public val failure: KadreFailure) : PermissionState
}
