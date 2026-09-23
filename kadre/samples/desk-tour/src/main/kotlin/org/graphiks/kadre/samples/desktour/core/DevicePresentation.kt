package org.graphiks.kadre.samples.desktour.core

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.input.DeviceConnectionState
import org.graphiks.kadre.input.DeviceInventory
import org.graphiks.kadre.input.InputDeviceKind
import org.graphiks.kadre.input.PermissionState

internal data class DeviceEntry(val name: String, val kind: String, val connected: Boolean)

/** Les périphériques et gamepads observés — le §4.4 les veut utiles seulement si le host les déclare. */
internal sealed interface DevicePresentation {
    data class Enumerated(val devices: List<DeviceEntry>, val gamepadCount: Int) : DevicePresentation
    data object Unsupported : DevicePresentation
    data class Unavailable(val motif: String) : DevicePresentation
}

internal fun deviceKindLabel(kind: InputDeviceKind): String = when (kind) {
    InputDeviceKind.Keyboard -> "Clavier"
    InputDeviceKind.Mouse -> "Souris"
    InputDeviceKind.Touchscreen -> "Écran tactile"
    InputDeviceKind.Touchpad -> "Pavé tactile"
    InputDeviceKind.Pen -> "Stylet"
    InputDeviceKind.Other -> "Autre"
}

internal fun deviceEntryOf(name: String?, kind: InputDeviceKind, connected: Boolean): DeviceEntry =
    DeviceEntry(name?.takeIf { it.isNotBlank() } ?: "Périphérique sans nom", deviceKindLabel(kind), connected)

internal fun devicePresentationFor(failure: KadreFailure): DevicePresentation =
    DevicePresentation.Unavailable(failure.userMotif())

internal fun devicePresentationOf(
    inventory: DeviceInventory,
    devices: (DeviceInventory.Enumerated) -> List<DeviceEntry>,
): DevicePresentation = when (inventory) {
    is DeviceInventory.Enumerated -> DevicePresentation.Enumerated(devices(inventory), inventory.gamepads.size)
    DeviceInventory.Unsupported -> DevicePresentation.Unsupported
    is DeviceInventory.Unavailable -> devicePresentationFor(inventory.failure)
}

/** L'état d'accès à la capture, par portée, tel que le host le publie. */
internal data class CapturePresentation(
    val screenLabel: String,
    val windowLabel: String,
    val canRequest: Boolean,
)

internal fun permissionLabel(state: PermissionState): String = when (state) {
    PermissionState.NotDetermined -> "pas encore demandée"
    PermissionState.Granted -> "accordée"
    is PermissionState.Denied -> if (state.canRequestAgain) "refusée, réessayable" else "refusée définitivement"
    PermissionState.Restricted -> "restreinte par le système"
    is PermissionState.Unavailable -> state.failure.userMotif()
}
