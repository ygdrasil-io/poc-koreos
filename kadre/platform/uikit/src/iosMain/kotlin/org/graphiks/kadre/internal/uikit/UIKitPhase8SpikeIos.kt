@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.graphiks.kadre.internal.uikit

import platform.GameController.GCController
import platform.GameController.GCControllerButtonInput
import platform.GameController.GCControllerDidConnectNotification
import platform.GameController.GCControllerDidDisconnectNotification
import platform.GameController.GCControllerDirectionPad
import platform.GameController.GCDeviceHaptics
import platform.GameController.GCExtendedGamepad
import platform.GameController.GCHapticsLocalityDefault
import platform.GameController.GCKeyboardDidConnectNotification
import platform.GameController.GCMicroGamepad
import platform.GameController.GCMouseDidConnectNotification
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification

/**
 * Le probe de COMPILE de la spike phase 8 côté iOS (Task 1) — ce fichier ne SERVE à rien en
 * runtime : il prouve que les symboles klib dont les Tasks 3-4 dépendent compilent depuis
 * Kotlin (2.4.20, iOS simulator + device), et il est DESTINÉ à être consommé (ou supprimé) par
 * les ports réels. La PIÈCE DISPLAY (inventaire `UIScreen` + les trois notifications + les
 * modes iOS-only) a été CONSOMMÉE par la Task 2 — le port réel [UIKitDisplayPort] et sa
 * réalisation [UIKitScreenDisplaySource] portent ces symboles désormais (verdicts : spike
 * `task1-spike-report.md` §2 — `UIScreen.name` 0-hit, `localizedName` iOS-seul, d'où le nom
 * null honnête du port ; `preferredMode`/`availableModes` iOS-seuls, et le kernel T1 n'en porte
 * qu'un mode unique, donc aucun port ne les lira).
 *
 * Les décisions de la spike ENCORE À CONSOMMER ICI côté iOS :
 * 1. [hapticsReachable] — le chemin `controller.haptics` → `createEngineWithLocality` (la
 *    création `createLocally*` du header est 0-hit au klib ; `createEngineWithLocality` est le
 *    SEUL chemin de `GCDeviceHaptics` exposé, vers la `CHHapticEngine` de CoreHaptics) ;
 * 2. [memoryWarningConstant] — la constante memory-warning (ruling Task 5 : le mapping mémoire
 *    Moderate-only s'abonne à elle) ;
 * 3. [controllerInventory] — `GCController.controllers()` + les notifications connect/disconnect
 *    de contrôleur, clavier et souris (le klib nomme GCMouse/GCKeyboard sur les DEUX cibles) ;
 * 4. [extendedProfileShape] — les éléments du profil étendu que la table du kernel
 *    ([UIKitGamepadMapping]) traduit : boutons, croix, sticks, gâchettes, micro-gamepad, et
 *    les handlers de l'élément bouton (`pressedChangedHandler`/`valueChangedHandler`).
 */
internal object UIKitPhase8SpikeIos {

    /**
     * Décision 2bis (le RESTE display de la spike, pour la Task 3) : la constante
     * memory-warning — le retour n'a pas de sémantique, seuls les SYMBOLES référencés comptent
     * (la compile EST le fait).
     */
    fun memoryWarningConstant(): String = UIApplicationDidReceiveMemoryWarningNotification!!

    /**
     * Décision 1 (iOS) : le chemin haptique RÉELLEMENT EXPOSÉ au klib — `controller.haptics`
     * (le `GCDeviceHaptics?` du contrôleur) puis `createEngineWithLocality` (le klib ne porte
     * AUCUN `createLocally*` ; le paramètre de localité est une constante du klib). La
     * `CHHapticEngine` rendue est l'affaire de CoreHaptics — ici seul le chemin COMPTE.
     */
    fun hapticsReachable(controller: GCController): Boolean {
        val haptics: GCDeviceHaptics? = controller.haptics
        val engine = haptics?.createEngineWithLocality(GCHapticsLocalityDefault)
        return engine != null
    }

    /**
     * Décision 3 (les DEUX cibles via leurs probes) : l'énumération des contrôleurs et les
     * constantes de connexion/déconnexion — contrôleur, clavier et souris (le klib 2.4.20
     * nomme GCMouse/GCKeyboard et leurs notifications sur iOS ET tvOS).
     */
    fun controllerInventory(): Int {
        val controllers: List<*> = GCController.controllers().orEmpty()
        val didConnect: String = GCControllerDidConnectNotification!!
        val didDisconnect: String = GCControllerDidDisconnectNotification!!
        val keyboardConnect: String = GCKeyboardDidConnectNotification!!
        val mouseConnect: String = GCMouseDidConnectNotification!!
        return controllers.size + didConnect.length + didDisconnect.length +
            keyboardConnect.length + mouseConnect.length
    }

    /**
     * Décision 4 (iOS) : la forme du profil que la table du kernel traduit — chaque élément
     * référencé ici a sa ligne dans [UIKIT_GAMEPAD_BUTTON_TABLE]/[UIKIT_GAMEPAD_AXIS_TABLE].
     * Les handlers de bouton sont posés (forme lambda inférée) : c'est le canal des Tasks 3-4
     * pour les événements pression/valeur.
     */
    fun extendedProfileShape(controller: GCController): Int {
        val extended: GCExtendedGamepad? = controller.extendedGamepad
        val micro: GCMicroGamepad? = controller.microGamepad
        val button: GCControllerButtonInput? = extended?.buttonA
        val pressed: Boolean = button?.isPressed() == true
        button?.pressedChangedHandler = { _, _, _ -> }
        button?.valueChangedHandler = { _, _, _ -> }
        val dpad: GCControllerDirectionPad? = extended?.dpad
        val x: Float = dpad?.xAxis?.value ?: 0f
        val y: Float = dpad?.yAxis?.value ?: 0f
        val up: GCControllerButtonInput? = dpad?.up
        return listOfNotNull(extended, micro, button, dpad, up).size +
            if (pressed) 1 else 0 + x.toInt() + y.toInt()
    }
}
