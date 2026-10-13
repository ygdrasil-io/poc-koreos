@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.graphiks.kadre.internal.uikit

import kotlinx.cinterop.useContents
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
import platform.UIKit.UIScreen
import platform.UIKit.UIScreenDidConnectNotification
import platform.UIKit.UIScreenDidDisconnectNotification
import platform.UIKit.UIScreenModeDidChangeNotification

/**
 * Le probe de COMPILE de la spike phase 8 côté tvOS (Task 1) — le miroir iOS
 * ([UIKitPhase8SpikeIos]), AMPUTÉ des symboles que le klib tvOS ne porte pas
 * (`preferredMode`/`availableModes` : 0-hit, le brief l'épingle ; l'écran unique tvOS n'a
 * qu'un mode, le kernel [uikitDisplayPortDisplay] en porte un seul). Il prouve que les
 * symboles dont les Tasks 2-4 dépendent compilent depuis Kotlin tvOS (2.4.20), et il est
 * destiné à être consommé (ou supprimé) par les ports réels — le rapport de spike
 * (`task1-spike-report.md`) porte chaque verdict.
 *
 * L'ABSENCE de discipline (tvOS absence discipline, gate Step 4) : aucun symbole référencé
 * ici ne doit manquer au klib tvOS — c'est le sens même du probe, la compile EST le fait.
 */
internal object UIKitPhase8SpikeTvos {

    /**
     * Décision 2 (tvOS) : l'inventaire UIScreen et les constantes de notification —
     * `screens`/`mainScreen`/`bounds` + les TROIS notifications (DidConnect/DidDisconnect/
     * ModeDidChange — les trois getters existent au klib tvOS, contrairement à
     * `preferredMode`/`availableModes`) + la constante memory-warning (ruling Task 5 : mapping
     * mémoire Moderate-only, même constant sur tvOS).
     */
    fun uiscreenFacts(): Int {
        val screens: List<*> = UIScreen.screens
        val main: UIScreen = UIScreen.mainScreen
        val didConnect: String = UIScreenDidConnectNotification!!
        val didDisconnect: String = UIScreenDidDisconnectNotification!!
        val modeDidChange: String = UIScreenModeDidChangeNotification!!
        val memoryWarning: String = UIApplicationDidReceiveMemoryWarningNotification!!
        val bounds = main.bounds.useContents { size.width to size.height }
        return screens.size + bounds.first.toInt() + bounds.second.toInt() +
            didConnect.length + didDisconnect.length + modeDidChange.length + memoryWarning.length
    }

    /**
     * Décision 1 (tvOS) : le chemin haptique exposé — le klib tvOS porte `controller.haptics`
     * ET `createEngineWithLocality` (mêmes faits d'interop qu'iOS, aucun `createLocally*`).
     */
    fun hapticsReachable(controller: GCController): Boolean {
        val haptics: GCDeviceHaptics? = controller.haptics
        val engine = haptics?.createEngineWithLocality(GCHapticsLocalityDefault)
        return engine != null
    }

    /**
     * Décision 3 (tvOS) : l'énumération des contrôleurs et les constantes — le Siri Remote
     * arrive comme contrôleur `GCMicroGamepad` SUR DEVICE ; sur Simulator l'inventaire est
     * vide (le probe de test `UIKitPhase8KernelsTest` l'atteste sur les deux familles).
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
     * Décision 4 (tvOS) : le profil étendu (une manette MFi sur Apple TV) ET le micro-gamepad
     * (le Siri Remote) — mêmes éléments que la table du kernel traduit.
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
