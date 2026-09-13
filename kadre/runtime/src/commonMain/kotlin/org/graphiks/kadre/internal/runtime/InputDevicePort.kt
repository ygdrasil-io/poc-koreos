package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.input.InputDeviceDescriptor

/** One detached native input-device projection before the runtime allocates its public identity. */
public data class InputDevicePortDevice(
    public val key: Long,
    public val descriptor: InputDeviceDescriptor,
) {
    init {
        require(key >= 0L) { "input-device key must be non-negative" }
    }
}

/** One per-session generic input-device observation from a backend. */
public sealed interface InputDevicePortEvent {
    public data class Connected(public val device: InputDevicePortDevice) : InputDevicePortEvent
    public data class Disconnected(public val key: Long) : InputDevicePortEvent {
        init {
            require(key >= 0L) { "input-device key must be non-negative" }
        }
    }
}

/**
 * Unstable backend SPI for detached generic input-device inventory.
 *
 * Keys are usable only across this backend/runtime boundary. The runtime allocates public
 * `DeviceId`s, owns lifecycle flows, and never receives native handles or raw HID reports.
 */
public interface InputDevicePort : AutoCloseable {
    /** Complete current generic-device projection visible to this session. */
    public val devices: List<InputDevicePortDevice>

    /** Installs a listener for changes that occur after installation begins. */
    public fun installObserver(observer: (InputDevicePortEvent) -> Unit): AutoCloseable

    override public fun close()
}
