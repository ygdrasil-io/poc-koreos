package org.graphiks.kadre.samples.desktour.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.graphiks.kadre.samples.desktour.core.InputFeature
import org.graphiks.kadre.samples.desktour.core.InputPresentation

internal object InteractionsViewLabels {
    fun observed(presentation: InputPresentation?): String? = when (presentation) {
        null -> "Aucune entrée n'a encore été observée."
        else -> buildString {
            append("Modificateurs : ")
            append(presentation.modifiers.joinToString().ifEmpty { "aucun" })
            append("\nTouches enfoncées : ${presentation.pressedKeyCount}")
            append("\nPointeurs : ${presentation.pointers.size}")
            append("\nDernier événement : ${presentation.lastEvent ?: "aucun"}")
        }
    }

    /** Une capacité absente porte son motif ; une capacité disponible n'a rien à expliquer. */
    fun featureDetail(feature: InputFeature): String? =
        if (feature.presentation.enabled) null else feature.presentation.motif
}

@Composable
internal fun InteractionsView(presentation: InputPresentation?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Entrées observées")
        InteractionsViewLabels.observed(presentation)?.let { Text(it) }
        if (presentation != null && presentation.pointers.isNotEmpty()) {
            LazyColumn {
                items(presentation.pointers) { pointer ->
                    val position = if (pointer.x == null || pointer.y == null) {
                        "position inconnue"
                    } else {
                        "${pointer.x} × ${pointer.y}"
                    }
                    val buttons = if (pointer.buttons.isEmpty()) "" else " · ${pointer.buttons.joinToString()}"
                    Text("${pointer.kind} — $position$buttons")
                }
            }
        }
        if (presentation != null) {
            Text("Capacités d'entrée")
            presentation.features.forEach { feature ->
                Column {
                    val state = if (feature.presentation.enabled) "disponible" else "indisponible"
                    Text("${feature.label} — $state")
                    InteractionsViewLabels.featureDetail(feature)?.let { Text(it) }
                }
            }
        }
    }
}
