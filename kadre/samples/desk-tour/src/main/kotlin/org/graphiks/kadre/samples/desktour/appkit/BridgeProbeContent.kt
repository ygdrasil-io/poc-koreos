package org.graphiks.kadre.samples.desktour.appkit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.graphiks.kadre.surface.SurfaceState

internal data class BridgeProbeState(
    val logicalWidth: Double,
    val logicalHeight: Double,
    val physicalWidth: Int,
    val physicalHeight: Int,
    val focusLabel: String,
    val text: String,
    val appearanceLabel: String = "Unknown",
    val visibilityLabel: String = "Unknown",
    val activity: String = "Waiting for input",
) {
    fun observing(state: SurfaceState): BridgeProbeState = copy(
        logicalWidth = state.logicalSize.width,
        logicalHeight = state.logicalSize.height,
        physicalWidth = state.physicalSize.width,
        physicalHeight = state.physicalSize.height,
        focusLabel = state.focus.name,
        appearanceLabel = "${state.appearance.theme} / ${state.appearance.contrast}",
        visibilityLabel = "${state.visibility} / ${state.occlusion}",
    )

    companion object {
        fun from(state: SurfaceState): BridgeProbeState =
            BridgeProbeState(0.0, 0.0, 0, 0, "Unknown", "").observing(state)
    }
}

@Composable
internal fun BridgeProbeContent(state: BridgeProbeState, onTextChanged: (String) -> Unit) {
    MaterialTheme {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background).padding(24.dp)) {
            Text("Kadre Desk Tour")
            Text("Surface: ${state.logicalWidth} × ${state.logicalHeight}")
            Text("Pixels: ${state.physicalWidth} × ${state.physicalHeight}")
            Text("Focus: ${state.focusLabel}")
            Text("Appearance: ${state.appearanceLabel}")
            Text("Visibility: ${state.visibilityLabel}")
            OutlinedTextField(
                value = state.text,
                onValueChange = onTextChanged,
                label = { Text("Type here") },
            )
            Text("Activity: ${state.activity}")
        }
    }
}
