package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.input.GamepadAxis
import org.graphiks.kadre.input.GamepadAxisValue
import org.graphiks.kadre.input.GamepadButton
import org.graphiks.kadre.input.GamepadButtonValue
import org.graphiks.kadre.input.GamepadDescriptor
import org.graphiks.kadre.input.GamepadMapping
import org.graphiks.kadre.input.GamepadState

/**
 * Les kernels PURS de mapping GAMEPAD GameController → modèle kadre (phase 8, Task 1) — les
 * tables fermées portées du kadre-old (`GamepadController.iosArm64.kt`, les tables SEULEMENT,
 * pas l'architecture), élargies aux éléments que le klib 2.4.20 nomme. RIEN ici n'importe un
 * type GameController : les noms d'éléments NATIFS entrent en `String` (le chemin de propriété
 * du profil — `buttonA`, `dpad.up`, `leftThumbstick.xAxis`…), les valeurs du modèle sortent —
 * le pattern jeton du module (miroir [UIKitDropMapping]), testable dans `uikitTest` sans
 * manette.
 *
 * Deux propriétés font partie du contrat :
 *
 * - **Totalité.** Les fonctions sont définies pour toute entrée, y compris hostile. Un mapping
 *   ne jette jamais : un nom hors table répond `null` — l'élément est PERDU (omis), jamais
 *   approximé ni transmué.
 * - **L'absence reste une absence.** Le descripteur ([uikitGamepadDescriptorOf]) ne porte QUE
 *   les éléments que le profil annonce présents — un bouton absent du profil est OMIS, jamais
 *   fabriqué en toujours-haut (ruling descripteur de la phase 8).
 */

/**
 * La table fermée des BOUTONS — les 17 kinds bouton du modèle, un à un depuis l'élément
 * `GCExtendedGamepad` qui les porte (le kadre-old portait 14 éléments ; `buttonMenu`,
 * `buttonOptions`, `buttonHome` complètent les 17, tous présents au klib 2.4.20) :
 *
 * - la croix : `buttonA` → South, `buttonB` → East, `buttonX` → West, `buttonY` → North (le
 *   kadre-old, `buttonValue` codes 0-3 — le naming GC suit la grille Xbox A/B/X/Y) ;
 * - les épaules et gâchettes : `leftShoulder`/`rightShoulder`/`leftTrigger`/`rightTrigger`
 *   (codes 4-7) ;
 * - les boutons système : `buttonMenu` → Start (le menu MFi EST le bouton pause/start-position),
 *   `buttonOptions` → Select (le second bouton système, select-position), `buttonHome` → Mode
 *   (le bouton guide/mode) ;
 * - la croix directionnelle : `dpad.up/down/left/right` (codes 8-11) ;
 * - les clics de sticks : `leftThumbstickButton`/`rightThumbstickButton` → LeftStick/RightStick
 *   (codes 12-13, les LeftThumb/RightThumb du kadre-old).
 */
internal val UIKIT_GAMEPAD_BUTTON_TABLE: List<Pair<String, GamepadButton>> = listOf(
    "buttonA" to GamepadButton.South,
    "buttonB" to GamepadButton.East,
    "buttonX" to GamepadButton.West,
    "buttonY" to GamepadButton.North,
    "leftShoulder" to GamepadButton.LeftShoulder,
    "rightShoulder" to GamepadButton.RightShoulder,
    "leftTrigger" to GamepadButton.LeftTrigger,
    "rightTrigger" to GamepadButton.RightTrigger,
    "buttonMenu" to GamepadButton.Start,
    "buttonOptions" to GamepadButton.Select,
    "buttonHome" to GamepadButton.Mode,
    "dpad.up" to GamepadButton.DpadUp,
    "dpad.down" to GamepadButton.DpadDown,
    "dpad.left" to GamepadButton.DpadLeft,
    "dpad.right" to GamepadButton.DpadRight,
    "leftThumbstickButton" to GamepadButton.LeftStick,
    "rightThumbstickButton" to GamepadButton.RightStick,
)

/**
 * La table fermée des AXES — les 8 kinds axe du modèle : les sticks (`leftThumbstick`/
 * `rightThumbstick` × `xAxis`/`yAxis`, codes 0-3 du kadre-old), les gâchettes analogiques
 * (`leftTrigger`/`rightTrigger`, codes 4-5 — le MÊME élément porte le bouton ET l'axe : la
 * valeur analogique du `GCControllerButtonInput`) et la croix directionnelle analogique
 * (`dpad.xAxis`/`dpad.yAxis` — le `GCControllerDirectionPad` porte ses axes, absents du
 * kadre-old, présents au klib).
 */
internal val UIKIT_GAMEPAD_AXIS_TABLE: List<Pair<String, GamepadAxis>> = listOf(
    "leftThumbstick.xAxis" to GamepadAxis.LeftX,
    "leftThumbstick.yAxis" to GamepadAxis.LeftY,
    "rightThumbstick.xAxis" to GamepadAxis.RightX,
    "rightThumbstick.yAxis" to GamepadAxis.RightY,
    "leftTrigger" to GamepadAxis.LeftTrigger,
    "rightTrigger" to GamepadAxis.RightTrigger,
    "dpad.xAxis" to GamepadAxis.DpadX,
    "dpad.yAxis" to GamepadAxis.DpadY,
)

/**
 * Le bouton du modèle porté par l'élément natif [elementNativeName], ou `null` quand la table
 * fermée ne le nomme pas — l'élément est perdu, jamais transmué en bouton familier.
 */
internal fun uikitGamepadButtonOf(elementNativeName: String): GamepadButton? =
    UIKIT_GAMEPAD_BUTTON_TABLE.firstOrNull { it.first == elementNativeName }?.second

/**
 * L'axe du modèle porté par l'élément natif [elementNativeName], ou `null` quand la table
 * fermée ne le nomme pas — même discipline : perdu, jamais approximé.
 */
internal fun uikitGamepadAxisOf(elementNativeName: String): GamepadAxis? =
    UIKIT_GAMEPAD_AXIS_TABLE.firstOrNull { it.first == elementNativeName }?.second

/**
 * Le descripteur d'UNE manette GameController — le mapping est `GamepadMapping.Native`
 * (ruling : le SDK GameController DÉFINIT le layout, la table ci-dessus est SA traduction vers
 * le vocabulaire du modèle — jamais `Standard`), et le profil annoncé ([buttonNames]/
 * [axisNames], les noms d'éléments que le port a lus présents sur le profil physique) est
 * FILTRÉ par les tables fermées : les noms hors table sont OMIS, les noms absents du profil
 * ne deviennent JAMAIS des boutons toujours-hauts fabriqués.
 *
 * L'ordre du descripteur est celui des TABLES (l'ordre canonique ci-dessus), jamais celui du
 * `Set` entrant — déterministe quel que soit l'ordre d'énumération du port. Les tables portent
 * des valeurs toutes distinctes (une table = un kind par nom, un kind par entrée), donc le
 * descripteur satisfait l'init du modèle (pas de doublon) par construction.
 *
 * Un profil sans AUCUN élément mappable reste un descripteur HONNÊTE à listes vides — la
 * manette existe, le modèle la porte (miroir Android : une manette réelle sans axe mappable
 * reste connectée), il n'est pas sauté.
 */
internal fun uikitGamepadDescriptorOf(
    buttonNames: Set<String>,
    axisNames: Set<String>,
    controllerName: String?,
): GamepadDescriptor = GamepadDescriptor(
    name = controllerName,
    mapping = GamepadMapping.Native,
    buttons = UIKIT_GAMEPAD_BUTTON_TABLE.mapNotNull { (name, button) -> button.takeIf { name in buttonNames } },
    axes = UIKIT_GAMEPAD_AXIS_TABLE.mapNotNull { (name, axis) -> axis.takeIf { name in axisNames } },
)

/**
 * La clé runtime d'une manette depuis la graine process-monotonique de la couture — le ruling
 * des clés de la phase 8 : `GCController` ne porte AUCUNE identité stable transposable (pas de
 * deviceId ; `playerIndex` est une préférence de slot, mutable), donc la couture alloue des
 * graines CROISSANTES à chaque connexion (0, 1, 2…) et le kernel garantit le domaine des clés
 * runtime : NON NÉGATIF pour TOUTE graine (`Long.MIN_VALUE` compris — l'image du bit de signe
 * est le seul fait porté perdu, la couture n'alloue jamais de graine négative).
 *
 * La monotonie des clés suit la monotonie des graines (`seed and Long.MAX_VALUE` garde
 * l'ordre des graines non négatives) : les clés n'exigent aucune relecture SDK pour rester
 * stables à vie du contrôleur process-lifetime.
 */
internal fun uikitControllerKey(seed: Long): Long = seed and Long.MAX_VALUE

/**
 * La lecture PRÉ-EXTRAITE d'UN bouton du profil étendu — la valeur analogique (`value` 0-1)
 * et l'état pressé (`pressed`) que le port lit sur l'élément `GCControllerButtonInput`. Le
 * pattern jeton du module : les scalaires entrent, le modèle sort — rien ici n'importe un
 * type GameController.
 */
internal data class UIKitButtonReading(val value: Double, val pressed: Boolean)

/**
 * Les tables inverses (kind → nom d'élément natif) — la clef de retour des lectures : le
 * descripteur gelé porte des KINDS du modèle, l'état assemble la lecture de CHACUN depuis
 * l'élément natif qui le porte. Les tables fermées ci-dessus portent des valeurs toutes
 * distinctes (prouvé T1), donc chaque kind a exactement un nom.
 */
internal val UIKIT_GAMEPAD_BUTTON_NAMES_BY_KIND: Map<GamepadButton, String> =
    UIKIT_GAMEPAD_BUTTON_TABLE.associate { (name, button) -> button to name }

internal val UIKIT_GAMEPAD_AXIS_NAMES_BY_KIND: Map<GamepadAxis, String> =
    UIKIT_GAMEPAD_AXIS_TABLE.associate { (name, axis) -> axis to name }

/**
 * L'état d'UNE manette depuis le descripteur (GELÉ à la connexion) et les lectures VIVANTES
 * des éléments — la forme AppKit `stateFor` : l'état parle EXACTEMENT le vocabulaire du
 * descripteur (un bouton/axe par entrée du descripteur, dans son ordre), chaque kind
 * porté par sa lecture d'élément, Toute lecture absente restant NEUTRE (valeur 0, non
 * pressé — jamais un bouton fabriqué, le miroir exact de la règle descripteur : l'absence
 * reste une absence).
 *
 * La séparation des deux entrées EST la règle du gel : un élément apparu APRÈS la connexion
 * coule dans l'état s'il porte un kind du descripteur gelé, n'entre JAMAIS dans le
 * descripteur ; un élément disparu retombe au neutre. Rien ne jette : toute entrée, y
 * compris hostile, produit un état bien formé pour l'init du modèle.
 */
internal fun uikitGamepadStateOf(
    descriptor: GamepadDescriptor,
    buttonReadings: Map<String, UIKitButtonReading>,
    axisReadings: Map<String, Double>,
): GamepadState = GamepadState(
    buttons = descriptor.buttons.map { button ->
        UIKIT_GAMEPAD_BUTTON_NAMES_BY_KIND[button]
            ?.let { name -> buttonReadings[name] }
            ?.let { reading -> GamepadButtonValue(button, reading.value, reading.pressed) }
            ?: GamepadButtonValue(button, 0.0, false)
    },
    axes = descriptor.axes.map { axis ->
        UIKIT_GAMEPAD_AXIS_NAMES_BY_KIND[axis]
            ?.let { name -> axisReadings[name] }
            ?.let { value -> GamepadAxisValue(axis, value) }
            ?: GamepadAxisValue(axis, 0.0)
    },
)
