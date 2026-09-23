package org.graphiks.kadre.samples.desktour.core

import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.input.GestureKind
import org.graphiks.kadre.input.InputCapabilities
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.ModifierKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerKind

internal data class InputFeature(val label: String, val presentation: CapabilityPresentation)

internal data class PointerSummary(val kind: String, val x: Double?, val y: Double?, val buttons: List<String>)

internal data class InputPresentation(
    val modifiers: List<String>,
    val pointers: List<PointerSummary>,
    val pressedKeyCount: Int,
    val lastEvent: String?,
    val features: List<InputFeature>,
)

internal fun modifierLabels(modifiers: KeyboardModifiers): List<String> =
    listOf(
        ModifierKey.Shift to "Maj",
        ModifierKey.Control to "Contrôle",
        ModifierKey.Alt to "Alt",
        ModifierKey.Meta to "Cmd",
        ModifierKey.CapsLock to "Verr. maj",
        ModifierKey.NumLock to "Verr. num",
    ).filter { (key, _) -> key in modifiers.pressed }.map { it.second }

/** Forme constructible d'un pointeur : `PointerState` exige un `PointerId` à constructeur interne. */
internal data class PointerSnapshot(
    val kind: PointerKind,
    val x: Double?,
    val y: Double?,
    val buttons: List<PointerButton>,
)

internal fun pointerSummaries(pointers: List<PointerSnapshot>): List<PointerSummary> =
    pointers.map { pointer ->
        PointerSummary(
            kind = pointer.kind.name,
            x = pointer.x,
            y = pointer.y,
            buttons = pointer.buttons.map { it.label() },
        )
    }

private fun PointerButton.label(): String = when (this) {
    PointerButton.Primary -> "Principal"
    PointerButton.Secondary -> "Secondaire"
    PointerButton.Auxiliary -> "Auxiliaire"
    PointerButton.Back -> "Précédent"
    PointerButton.Forward -> "Suivant"
    PointerButton.Barrel -> "Barillet"
    PointerButton.Eraser -> "Gomme"
    is PointerButton.Other -> "Autre"
}

/**
 * Les sept capacités d'entrée du §4.4, toutes listées — une capacité absente est une
 * information, pas une ligne manquante (§4.4, §5 ligne 115).
 */
internal fun inputFeatures(capabilities: InputCapabilities): List<InputFeature> = listOf(
    InputFeature("Clavier", present(capabilities.keyboard)),
    InputFeature("Pointeur", present(capabilities.pointer)),
    InputFeature("Tactile", present(capabilities.touch)),
    InputFeature("Gestes", gesturesFeature(capabilities.gestures)),
    InputFeature("Glisser-déposer", present(capabilities.dragAndDrop)),
    InputFeature("Saisie de texte", present(capabilities.textInput)),
    InputFeature("Entrées brutes", present(capabilities.rawInput)),
)

private fun gesturesFeature(capability: Capability<Set<GestureKind>>): CapabilityPresentation {
    val presented = present(capability)
    val kinds = (capability as? Capability.Supported)?.constraints.orEmpty()
    // Un « oui » global ne dirait pas lesquels : les gestes réellement supportés sont nommés.
    return if (kinds.isEmpty()) presented else presented.copy(motif = kinds.sortedBy { it.name }.joinToString(", ") { it.name })
}
