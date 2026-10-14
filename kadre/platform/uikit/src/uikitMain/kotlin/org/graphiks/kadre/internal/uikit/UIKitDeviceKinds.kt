package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.input.InputDeviceKind

/**
 * Le kernel PUR du kind GÉNÉRIQUE d'un périphérique d'entrée iOS/tvOS (phase 8, Task 1) — le
 * miroir exact d'Android (`AndroidInputDevicePort.genericDeviceKind:260-271`) : les prédicats
 * pré-extraits du profil entrent en `Boolean` (le port de la Task suivante les lit sur le
 * `GCController` — `extendedGamepad != null`, le clavier/souris Bluetooth, `microGamepad !=
 * null`), le kind du modèle sort. RIEN ici n'importe un type SDK (le pattern jeton du module).
 *
 * L'ORDRE de la règle (ruling phase 8) :
 * 1. **le carve-out gamepad D'ABORD** — un device gamepad reste `InputDeviceKind.Other` côté
 *    générique MÊME s'il déclare aussi des traits clavier/souris : la manette est un `Gamepad`
 *    du port manette, le port générique ne vole pas l'identité que l'autre port porte
 *    (des manettes réelles déclarent aussi des sources clavier — le précédent Android) ;
 * 2. **Keyboard**, **Mouse** — les périphériques Bluetooth réels sont typés génériques ;
 * 3. **la micro-manette carve-out ensuite** — le profil `GCMicroGamepad` (le Siri Remote) est
 *    du ressort du port manette : `Other` côté générique, mais APRÈS Keyboard/Mouse, une
 *    identité clavier/souris réelle gagne (l'ordre du ruling) ;
 * 4. sinon `Other` — le fourre-tout honnête.
 */
internal fun uikitGenericDeviceKindOf(
    isGamepad: Boolean,
    isKeyboard: Boolean,
    isMouse: Boolean,
    isMicroGamepad: Boolean,
): InputDeviceKind = when {
    isGamepad -> InputDeviceKind.Other
    isKeyboard -> InputDeviceKind.Keyboard
    isMouse -> InputDeviceKind.Mouse
    isMicroGamepad -> InputDeviceKind.Other
    else -> InputDeviceKind.Other
}
