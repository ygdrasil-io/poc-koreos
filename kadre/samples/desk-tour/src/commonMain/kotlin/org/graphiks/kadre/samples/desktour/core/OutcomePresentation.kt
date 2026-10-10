package org.graphiks.kadre.samples.desktour.core

import org.graphiks.kadre.window.RejectedWindowField
import org.graphiks.kadre.window.WindowProperty

internal data class RejectedFieldPresentation(val fieldLabel: String, val motif: String)

internal data class OutcomePresentation(
    val applied: List<String>,
    val rejected: List<RejectedFieldPresentation>,
)

internal fun presentWindowUpdate(
    applied: List<WindowProperty>,
    rejected: List<RejectedWindowField>,
): OutcomePresentation = OutcomePresentation(
    applied = applied.map { it.readableName() },
    rejected = rejected.map { RejectedFieldPresentation(it.field.readableName(), it.failure.userMotif()) },
)

internal fun WindowProperty.readableName(): String = when (this) {
    WindowProperty.Title -> "Titre"
    WindowProperty.OuterPosition -> "Position"
    WindowProperty.ContentSize -> "Taille du contenu"
    WindowProperty.MinimumSize -> "Taille minimale"
    WindowProperty.MaximumSize -> "Taille maximale"
    WindowProperty.Resizable -> "Redimensionnable"
    WindowProperty.Fullscreen -> "Plein écran"
    WindowProperty.Decorations -> "Décoration"
    WindowProperty.SystemButtons -> "Boutons système"
    WindowProperty.Level -> "Niveau"
    WindowProperty.Transparency -> "Transparence"
    WindowProperty.Blur -> "Flou d'arrière-plan"
    WindowProperty.Icon -> "Icône"
    WindowProperty.ContentProtection -> "Protection du contenu"
}
