package org.graphiks.kadre.samples.desktour.core

import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.surface.LogicalPoint

/** Ce qu'un événement d'entrée observé dit à l'utilisateur, sans jargon de type. */
internal data class InputEventSummary(val kind: String, val detail: String?)

internal fun renderEventSummary(summary: InputEventSummary): String {
    val detail = summary.detail?.takeIf { it.isNotBlank() } ?: return summary.kind
    return "${summary.kind} — $detail"
}

/**
 * Extraction par variante. Elle vit à côté des libellés parce qu'un `InputEvent` n'est pas
 * constructible dans le sample — `EventStamp` et `InputStateRevision` ont des constructeurs
 * internes — donc seule la mise en forme est testée ; le `when` exhaustif garantit qu'aucune
 * variante n'est oubliée, et la compilation le dirait.
 */
internal fun describeInputEvent(event: InputEvent): InputEventSummary = when (event) {
    is InputEvent.Key -> InputEventSummary(
        kind = if (event.keyState == KeyState.Pressed) "Touche enfoncée" else "Touche relâchée",
        detail = when (val key = event.logicalKey) {
            is LogicalKey.Character -> key.value
            is LogicalKey.Named -> namedKeyLabel(key.value)
            is LogicalKey.Unidentified -> "touche non identifiée"
        },
    )
    is InputEvent.PointerMoved -> InputEventSummary("Pointeur déplacé", coordinates(event.position))
    is InputEvent.PointerEntered -> InputEventSummary("Pointeur entré", coordinates(event.position))
    is InputEvent.PointerLeft -> InputEventSummary("Pointeur sorti", event.lastPosition?.let(::coordinates))
    is InputEvent.PointerButtonChanged -> InputEventSummary(
        kind = "Bouton de pointeur",
        detail = "${pointerButtonLabel(event.button)} ${pointerButtonStateLabel(event.buttonState)}",
    )
    is InputEvent.Scrolled -> InputEventSummary("Défilement", null)
    is InputEvent.TouchChanged -> InputEventSummary("Tactile", touchPhaseLabel(event.phase))
    is InputEvent.Gesture -> InputEventSummary("Geste", gestureKindLabel(event.kind))
    is InputEvent.DropEntered -> InputEventSummary("Dépôt proposé", null)
    is InputEvent.DropMoved -> InputEventSummary("Dépôt déplacé", null)
    is InputEvent.DropExited -> InputEventSummary("Dépôt retiré", null)
    is InputEvent.Dropped -> InputEventSummary("Dépôt accepté", null)
    is InputEvent.StateReset -> InputEventSummary("Entrée réinitialisée", resetReasonLabel(event.reason))
}

/** Coordonnées logiques du host, dans son origine — en pixels logiques, sans décimale trompeuse. */
private fun coordinates(position: LogicalPoint): String =
    "${position.x.roundToPixels()} × ${position.y.roundToPixels()}"

private fun Double.roundToPixels(): String {
    val rounded = kotlin.math.round(this).toLong()
    return if (this == rounded.toDouble()) rounded.toString() else this.toString()
}
