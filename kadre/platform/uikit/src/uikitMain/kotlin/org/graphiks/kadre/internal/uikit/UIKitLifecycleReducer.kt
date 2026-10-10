package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.application.ActivationState
import org.graphiks.kadre.application.VisibilityState
import platform.UIKit.UISceneActivationStateBackground
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UISceneActivationStateForegroundInactive
import platform.UIKit.UISceneActivationStateUnattached
import platform.UIKit.UISceneDidActivateNotification
import platform.UIKit.UISceneDidEnterBackgroundNotification
import platform.UIKit.UISceneWillDeactivateNotification
import platform.UIKit.UISceneWillEnterForegroundNotification
import platform.UIKit.UIWindowScene

/**
 * Axes de cycle de vie d'une scène, exprimés dans les types foundation réels :
 * [VisibilityState] (le « LifecycleForeground » de la roadmap) et [ActivationState]
 * (le « LifecycleActivation »). Immutable, réduit d'une notification à la suivante.
 */
internal data class UIKitLifecycleSnapshot(
    val visibility: VisibilityState,
    val activation: ActivationState,
)

/**
 * Table normative de décision notification → snapshot (phase 1, attach lifecycle).
 * Retourne `null` pour toute notification sans effet de snapshot — en particulier
 * `UISceneDidDisconnectNotification`, qui est une terminaison routée à part, jamais un état.
 *
 * Interop K/N 2.4.20 : `UISceneActivationState` est un typedef strict de `Long` (pas un enum
 * Kotlin) ; les constantes sont des vals top-level platform.UIKit, et `scene.activationState`
 * est un `Long` comparé à ces constantes.
 */
internal object UIKitLifecycleReducer {
    /** État de départ lu sur la scène, ou `null` tant que la scène est unattached. */
    fun initial(scene: UIWindowScene): UIKitLifecycleSnapshot? = when (scene.activationState) {
        UISceneActivationStateForegroundInactive ->
            UIKitLifecycleSnapshot(VisibilityState.Foreground, ActivationState.Inactive)
        UISceneActivationStateForegroundActive ->
            UIKitLifecycleSnapshot(VisibilityState.Foreground, ActivationState.Active)
        UISceneActivationStateBackground ->
            UIKitLifecycleSnapshot(VisibilityState.Background, ActivationState.Inactive)
        UISceneActivationStateUnattached -> null
        else -> null
    }

    fun reduce(notificationName: String, snapshot: UIKitLifecycleSnapshot): UIKitLifecycleSnapshot? =
        when (notificationName) {
            UISceneWillEnterForegroundNotification -> snapshot.copy(visibility = VisibilityState.Foreground)
            UISceneDidActivateNotification -> snapshot.copy(activation = ActivationState.Active)
            UISceneWillDeactivateNotification -> snapshot.copy(activation = ActivationState.Inactive)
            UISceneDidEnterBackgroundNotification ->
                UIKitLifecycleSnapshot(VisibilityState.Background, ActivationState.Inactive)
            else -> null
        }
}
