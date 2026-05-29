/**
 * Mapping DOM → types Koreos web (fonctions pures, sans imports DOM).
 *
 * Ce fichier est dans webMain. Il ne doit contenir AUCUN import DOM
 * (pas de kotlinx.browser, org.w3c.dom.*, ni interop JS Wasm).
 * Les paramètres reçoivent des types Kotlin purs (String, Short, Boolean)
 * extraits par les implémentations dans jsMain / wasmJsMain.
 *
 * Les fonctions sont `internal` pour éviter de polluer l'API publique.
 *
 * @since 0.1.0
 */
package io.ygdrasil.koreos.web

/**
 * Convertit un code DOM (`KeyboardEvent.code`) en [WebKey] Koreos.
 *
 * Les codes DOM sont des chaînes de la forme `"KeyA"`, `"Digit1"`, `"ArrowUp"`, etc.
 * Voir : https://developer.mozilla.org/en-US/docs/Web/API/UI_Events/Keyboard_event_code_values
 *
 * @param code Valeur de `KeyboardEvent.code` (ex. `"KeyA"`, `"Space"`, `"F1"`).
 * @return [WebKey] correspondant, ou [WebKey.Unknown] si le code n'est pas reconnu.
 */
internal fun domCodeToKey(code: String): WebKey = when (code) {
    // Lettres
    "KeyA" -> WebKey.A
    "KeyB" -> WebKey.B
    "KeyC" -> WebKey.C
    "KeyD" -> WebKey.D
    "KeyE" -> WebKey.E
    "KeyF" -> WebKey.F
    "KeyG" -> WebKey.G
    "KeyH" -> WebKey.H
    "KeyI" -> WebKey.I
    "KeyJ" -> WebKey.J
    "KeyK" -> WebKey.K
    "KeyL" -> WebKey.L
    "KeyM" -> WebKey.M
    "KeyN" -> WebKey.N
    "KeyO" -> WebKey.O
    "KeyP" -> WebKey.P
    "KeyQ" -> WebKey.Q
    "KeyR" -> WebKey.R
    "KeyS" -> WebKey.S
    "KeyT" -> WebKey.T
    "KeyU" -> WebKey.U
    "KeyV" -> WebKey.V
    "KeyW" -> WebKey.W
    "KeyX" -> WebKey.X
    "KeyY" -> WebKey.Y
    "KeyZ" -> WebKey.Z

    // Chiffres
    "Digit0" -> WebKey.Digit0
    "Digit1" -> WebKey.Digit1
    "Digit2" -> WebKey.Digit2
    "Digit3" -> WebKey.Digit3
    "Digit4" -> WebKey.Digit4
    "Digit5" -> WebKey.Digit5
    "Digit6" -> WebKey.Digit6
    "Digit7" -> WebKey.Digit7
    "Digit8" -> WebKey.Digit8
    "Digit9" -> WebKey.Digit9

    // Touches de fonction
    "F1"  -> WebKey.F1
    "F2"  -> WebKey.F2
    "F3"  -> WebKey.F3
    "F4"  -> WebKey.F4
    "F5"  -> WebKey.F5
    "F6"  -> WebKey.F6
    "F7"  -> WebKey.F7
    "F8"  -> WebKey.F8
    "F9"  -> WebKey.F9
    "F10" -> WebKey.F10
    "F11" -> WebKey.F11
    "F12" -> WebKey.F12

    // Touches spéciales
    "Space"     -> WebKey.Space
    "Enter"     -> WebKey.Enter
    "Escape"    -> WebKey.Escape
    "Backspace" -> WebKey.Backspace
    "Tab"       -> WebKey.Tab

    // Navigation
    "ArrowUp"    -> WebKey.ArrowUp
    "ArrowDown"  -> WebKey.ArrowDown
    "ArrowLeft"  -> WebKey.ArrowLeft
    "ArrowRight" -> WebKey.ArrowRight

    // Modificateurs
    "ShiftLeft"    -> WebKey.ShiftLeft
    "ShiftRight"   -> WebKey.ShiftRight
    "ControlLeft"  -> WebKey.ControlLeft
    "ControlRight" -> WebKey.ControlRight
    "AltLeft"      -> WebKey.AltLeft
    "AltRight"     -> WebKey.AltRight
    "MetaLeft"     -> WebKey.MetaLeft
    "MetaRight"    -> WebKey.MetaRight

    else -> WebKey.Unknown
}

/**
 * Construit un [WebModifiers] à partir des champs booléens d'un `KeyboardEvent` ou `MouseEvent` DOM.
 *
 * @param shiftKey  Valeur de `event.shiftKey`.
 * @param ctrlKey   Valeur de `event.ctrlKey`.
 * @param altKey    Valeur de `event.altKey`.
 * @param metaKey   Valeur de `event.metaKey`.
 * @return [WebModifiers] avec les bits correspondants positionnés.
 */
internal fun domModifiers(
    shiftKey: Boolean,
    ctrlKey: Boolean,
    altKey: Boolean,
    metaKey: Boolean,
): WebModifiers {
    var mods = WebModifiers.NONE
    if (shiftKey) mods = mods + WebModifiers.SHIFT
    if (ctrlKey)  mods = mods + WebModifiers.CTRL
    if (altKey)   mods = mods + WebModifiers.ALT
    if (metaKey)  mods = mods + WebModifiers.META
    return mods
}

/**
 * Convertit un index de bouton DOM (`MouseEvent.button`) en [WebMouseButton] Koreos.
 *
 * Mapping standard DOM :
 * - `0` → bouton gauche
 * - `1` → bouton milieu (molette)
 * - `2` → bouton droit
 * - `≥3` → boutons supplémentaires ([WebMouseButton.Other])
 *
 * @param button Valeur de `MouseEvent.button` (Short côté DOM).
 * @return [WebMouseButton] correspondant.
 */
internal fun domButtonToMouseButton(button: Short): WebMouseButton = when (button.toInt()) {
    0    -> WebMouseButton.Left
    1    -> WebMouseButton.Middle
    2    -> WebMouseButton.Right
    else -> WebMouseButton.Other(button.toInt())
}
