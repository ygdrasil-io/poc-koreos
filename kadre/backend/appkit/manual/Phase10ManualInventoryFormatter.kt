package org.graphiks.kadre.internal.appkit.manual

import org.graphiks.kadre.input.DeviceId
import org.graphiks.kadre.input.DeviceInventory
import org.graphiks.kadre.input.DeviceLifecycleEvent
import org.graphiks.kadre.input.Gamepad
import org.graphiks.kadre.input.GamepadId
import org.graphiks.kadre.input.InputDevice

/** Formats Phase 10 public inventory without relying on implementation `toString()` or exposing IDs. */
internal class Phase10ManualInventoryFormatter {
    private val deviceTokens = linkedMapOf<DeviceId, String>()
    private val gamepadTokens = linkedMapOf<GamepadId, String>()

    @Synchronized
    fun formatInventory(inventory: DeviceInventory): String = when (inventory) {
        DeviceInventory.Unsupported -> "unsupported"
        is DeviceInventory.Unavailable -> "unavailable failure=${inventory.failure}"
        is DeviceInventory.Enumerated -> buildString {
            append("enumerated devices=")
            append(inventory.devices.joinToString(prefix = "[", postfix = "]", transform = ::formatDevice))
            append(" gamepads=")
            append(inventory.gamepads.joinToString(prefix = "[", postfix = "]", transform = ::formatGamepad))
        }
    }

    @Synchronized
    fun formatEvent(event: DeviceLifecycleEvent): String = when (event) {
        is DeviceLifecycleEvent.DeviceAdded ->
            "DeviceAdded ${formatDevice(event.device)} revision=${event.managerRevision.value} sequence=${event.stamp.sequence.value}"

        is DeviceLifecycleEvent.DeviceRemoved ->
            "DeviceRemoved ${deviceToken(event.deviceId)} revision=${event.managerRevision.value} sequence=${event.stamp.sequence.value}"

        is DeviceLifecycleEvent.GamepadAdded ->
            "GamepadAdded ${formatGamepad(event.gamepad)} revision=${event.managerRevision.value} sequence=${event.stamp.sequence.value}"

        is DeviceLifecycleEvent.GamepadRemoved ->
            "GamepadRemoved ${gamepadToken(event.gamepadId)} revision=${event.managerRevision.value} sequence=${event.stamp.sequence.value}"
    }

    private fun formatDevice(device: InputDevice): String = with(device) {
        "${deviceToken(id)}{name=${quoted(descriptor.name)},kind=${descriptor.kind},connection=${connection.value}}"
    }

    private fun formatGamepad(gamepad: Gamepad): String = with(gamepad) {
        val state = state.value
        "${gamepadToken(id)}{name=${quoted(state.descriptor.name)},mapping=${state.descriptor.mapping}," +
            "connection=${state.connection},routing=${state.routing}}"
    }

    private fun deviceToken(id: DeviceId): String = deviceTokens.getOrPut(id) { "d${deviceTokens.size + 1}" }

    private fun gamepadToken(id: GamepadId): String = gamepadTokens.getOrPut(id) { "g${gamepadTokens.size + 1}" }

    private fun quoted(value: String?): String = value
        ?.replace("\\", "\\\\")
        ?.replace("\"", "\\\"")
        ?.replace("\t", "\\t")
        ?.replace("\n", "\\n")
        ?.replace("\r", "\\r")
        ?.let { escaped -> "\"$escaped\"" }
        ?: "null"
}
