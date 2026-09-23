package org.graphiks.kadre.samples.desktour.core

import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.input.GestureKind
import org.graphiks.kadre.input.InputCapabilities
import org.graphiks.kadre.input.InputStateResetReason
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.ModifierKey
import org.graphiks.kadre.input.NamedKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.input.TouchPhase

internal data class InputFeature(val label: String, val presentation: CapabilityPresentation)

internal data class PointerSummary(val kind: String, val x: Double?, val y: Double?, val buttons: List<String>)

internal data class InputPresentation(
    /** Nul quand le host ne publie pas le clavier : « aucun modificateur » serait une affirmation. */
    val modifiers: List<String>?,
    val pointers: List<PointerSummary>,
    val pressedKeyCount: Int?,
    val lastEvent: String?,
    val features: List<InputFeature>,
)

/** Forme constructible d'un pointeur : `PointerState` exige un `PointerId` à constructeur interne. */
internal data class PointerSnapshot(
    val kind: PointerKind,
    val x: Double?,
    val y: Double?,
    val buttons: List<PointerButton>,
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

internal fun pointerSummaries(pointers: List<PointerSnapshot>): List<PointerSummary> =
    pointers.map { pointer ->
        PointerSummary(
            kind = pointerKindLabel(pointer.kind),
            x = pointer.x,
            y = pointer.y,
            buttons = pointer.buttons.map(::pointerButtonLabel),
        )
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
    // Le motif d'indisponibilité (permission, panne) n'est jamais écrasé : il est la raison.
    if (!presented.enabled) return presented
    val kinds = (capability as? Capability.Supported)?.constraints.orEmpty()
    // Un « oui » global ne dirait pas lesquels : les gestes réellement supportés sont nommés.
    return if (kinds.isEmpty()) {
        presented
    } else {
        presented.copy(motif = kinds.sortedBy { it.name }.joinToString(", ") { gestureKindLabel(it) })
    }
}

// Les libellés ci-dessous existent pour qu'aucun nom d'énumération de Kadre — anglais, et du
// vocabulaire de développeur — n'atteigne l'écran français (spec §8.4).

internal fun pointerKindLabel(kind: PointerKind): String = when (kind) {
    PointerKind.Mouse -> "Souris"
    PointerKind.Touchpad -> "Pavé tactile"
    PointerKind.Pen -> "Stylet"
    PointerKind.Eraser -> "Gomme"
    PointerKind.Unknown -> "Pointeur inconnu"
}

internal fun pointerButtonLabel(button: PointerButton): String = when (button) {
    PointerButton.Primary -> "principal"
    PointerButton.Secondary -> "secondaire"
    PointerButton.Auxiliary -> "auxiliaire"
    PointerButton.Back -> "précédent"
    PointerButton.Forward -> "suivant"
    PointerButton.Barrel -> "barillet"
    PointerButton.Eraser -> "gomme"
    is PointerButton.Other -> "autre bouton"
}

internal fun pointerButtonStateLabel(state: PointerButtonState): String = when (state) {
    PointerButtonState.Pressed -> "enfoncé"
    PointerButtonState.Released -> "relâché"
}

internal fun touchPhaseLabel(phase: TouchPhase): String = when (phase) {
    TouchPhase.Started -> "commencé"
    TouchPhase.Moved -> "déplacé"
    TouchPhase.Ended -> "terminé"
    TouchPhase.Cancelled -> "annulé"
}

internal fun gestureKindLabel(kind: GestureKind): String = when (kind) {
    GestureKind.Pan -> "glissement"
    GestureKind.Pinch -> "pincement"
    GestureKind.Rotation -> "rotation"
    GestureKind.DoubleTap -> "double appui"
    GestureKind.TouchpadPressure -> "pression du pavé"
}

internal fun resetReasonLabel(reason: InputStateResetReason): String = when (reason) {
    InputStateResetReason.FocusLost -> "perte du focus"
    InputStateResetReason.DeviceDisconnected -> "appareil déconnecté"
    InputStateResetReason.PermissionRevoked -> "permission révoquée"
}

internal fun namedKeyLabel(key: NamedKey): String = when (key) {
    NamedKey.Enter -> "Entrée"; NamedKey.Tab -> "Tabulation"; NamedKey.Space -> "Espace"
    NamedKey.Backspace -> "Retour arrière"; NamedKey.Escape -> "Échap"; NamedKey.Delete -> "Suppr"
    NamedKey.Insert -> "Inser"; NamedKey.Home -> "Début"; NamedKey.End -> "Fin"
    NamedKey.PageUp -> "Page précédente"; NamedKey.PageDown -> "Page suivante"
    NamedKey.ArrowLeft -> "Flèche gauche"; NamedKey.ArrowRight -> "Flèche droite"
    NamedKey.ArrowUp -> "Flèche haut"; NamedKey.ArrowDown -> "Flèche bas"
    NamedKey.Shift -> "Maj"; NamedKey.Control -> "Contrôle"; NamedKey.Alt -> "Alt"; NamedKey.Meta -> "Cmd"
    NamedKey.CapsLock -> "Verr. maj"; NamedKey.NumLock -> "Verr. num"; NamedKey.ContextMenu -> "Menu contextuel"
    NamedKey.F1 -> "F1"; NamedKey.F2 -> "F2"; NamedKey.F3 -> "F3"; NamedKey.F4 -> "F4"
    NamedKey.F5 -> "F5"; NamedKey.F6 -> "F6"; NamedKey.F7 -> "F7"; NamedKey.F8 -> "F8"
    NamedKey.F9 -> "F9"; NamedKey.F10 -> "F10"; NamedKey.F11 -> "F11"; NamedKey.F12 -> "F12"
    NamedKey.MediaPlayPause -> "Lecture/pause"; NamedKey.MediaStop -> "Arrêt"
    NamedKey.MediaNext -> "Piste suivante"; NamedKey.MediaPrevious -> "Piste précédente"
    NamedKey.VolumeUp -> "Volume +"; NamedKey.VolumeDown -> "Volume −"; NamedKey.VolumeMute -> "Volume coupé"
}
