package org.graphiks.kadre.samples.desktour.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import org.graphiks.kadre.samples.desktour.core.InputFeature
import org.graphiks.kadre.samples.desktour.core.InputPresentation

internal object InteractionsViewLabels {
    private const val NOT_PUBLISHED = "non publié par le host"

    fun observed(presentation: InputPresentation?): String? = when (presentation) {
        null -> "Aucune entrée n'a encore été observée."
        else -> buildString {
            val modifiers = presentation.modifiers
            append("Modificateurs : ")
            append(if (modifiers == null) NOT_PUBLISHED else modifiers.joinToString().ifEmpty { "aucun" })
            append("\nTouches enfoncées : ")
            append(presentation.pressedKeyCount?.toString() ?: NOT_PUBLISHED)
            append("\nPointeurs : ${presentation.pointers.size}")
            append("\nDernier événement : ${presentation.lastEvent ?: "aucun"}")
        }
    }

    /** Une capacité absente porte son motif ; une capacité disponible n'a rien à expliquer. */
    fun featureDetail(feature: InputFeature): String? =
        if (feature.presentation.enabled) null else feature.presentation.motif

    /** L'origine des coordonnées change d'un host à l'autre : on la nomme au lieu de la supposer. */
    fun pointerOrigin(): String = "Coordonnées du host, dans l'origine qu'il rapporte."

    /** Pixels logiques entiers : « 700.0 » suggère une précision que le host ne rapporte pas. */
    fun pointerPosition(x: Double?, y: Double?): String = if (x == null || y == null) {
        "position inconnue"
    } else {
        "${x.roundToInt()} × ${y.roundToInt()}"
    }
}

@Composable
internal fun InteractionsView(
    presentation: InputPresentation?,
    modifier: Modifier = Modifier,
) {
    // Défilant : les sections du §4.4 ne tiennent pas dans la fenêtre, et une section
    // inatteignable n'est pas une section.
    Column(modifier.verticalScroll(rememberScrollState())) {
        Text("Interactions")
        InteractionsViewLabels.observed(presentation)?.let { Text(it) }
        if (presentation != null && presentation.pointers.isNotEmpty()) {
            Text(InteractionsViewLabels.pointerOrigin())
            presentation.pointers.forEach { pointer ->
                val buttons = if (pointer.buttons.isEmpty()) "" else " · ${pointer.buttons.joinToString()}"
                Text("${pointer.kind} — ${InteractionsViewLabels.pointerPosition(pointer.x, pointer.y)}$buttons")
            }
        }
        if (presentation != null) {
            Text("Capacités d'entrée")
            presentation.features.forEach { feature ->
                val state = if (feature.presentation.enabled) "disponible" else "indisponible"
                Text("${feature.label} — $state")
                InteractionsViewLabels.featureDetail(feature)?.let { Text(it) }
            }
        }
    }
}
