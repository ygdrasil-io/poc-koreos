package org.graphiks.kadre.samples.desktour.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.graphiks.kadre.samples.desktour.core.CapturePresentation
import org.graphiks.kadre.samples.desktour.core.DevicePresentation

/**
 * Les capacités conditionnelles du §4.4 — périphériques, gamepads et capture — dans leur
 * propre espace : le host ne les déclare pas toutes, et l'utilisateur doit pouvoir lire
 * chacune sans faire défiler un panneau que ce bridge ne fait pas défiler.
 */
@Composable
internal fun DevicesView(
    devices: DevicePresentation?,
    capture: CapturePresentation?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text("Périphériques")
        when (devices) {
            null -> Text("Les périphériques n'ont pas encore été observés.")
            is DevicePresentation.Enumerated -> {
                if (devices.devices.isEmpty()) Text("Aucun périphérique n'est rapporté par le host.")
                devices.devices.forEach { device ->
                    Text("${device.name} — ${device.kind}" + if (device.connected) "" else " (déconnecté)")
                }
                Text("Gamepads : ${devices.gamepadCount}")
            }
            DevicePresentation.Unsupported -> Text("Le host ne publie pas de périphériques.")
            is DevicePresentation.Unavailable -> Text(devices.motif)
        }
        Text("Capture")
        when (capture) {
            null -> Text("L'état d'accès à la capture n'a pas encore été observé.")
            else -> {
                Text("Permission d'écran : ${capture.screenLabel}")
                Text("Permission de fenêtre : ${capture.windowLabel}")
                if (capture.canRequest) Text("Une demande d'accès est possible depuis l'application.")
            }
        }
    }
}
