package org.graphiks.kadre.samples.desktour.core

/** Ce qu'un événement d'entrée observé dit à l'utilisateur, sans jargon de type. */
internal data class InputEventSummary(val kind: String, val detail: String?)

internal fun renderEventSummary(summary: InputEventSummary): String {
    val detail = summary.detail?.takeIf { it.isNotBlank() } ?: return summary.kind
    return "${summary.kind} — $detail"
}
