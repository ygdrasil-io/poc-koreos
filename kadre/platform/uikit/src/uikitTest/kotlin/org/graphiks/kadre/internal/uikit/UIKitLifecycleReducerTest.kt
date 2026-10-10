package org.graphiks.kadre.internal.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.graphiks.kadre.application.ActivationState
import org.graphiks.kadre.application.VisibilityState

class UIKitLifecycleReducerTest {
    // Les constantes exactes sont celles de platform.UIKit (NSNotificationName importées comme String) ;
    // les retrouver par référence (platform.UIKit.UISceneDidActivateNotification) dans le code du reducer,
    // pas en copiant des littéraux ici.
    //
    // Types foundation réels (kadre/foundation application/Lifecycle.kt) : les axes du snapshot sont
    // VisibilityState (Foreground/Background — le vocabulaire roadmap « LifecycleForeground ») et
    // ActivationState (Active/Inactive — « LifecycleActivation »). Sémantique inchangée.
    //
    // Interop K/N 2.4.20 : les constantes NSNotificationName de platform.UIKit sont des `String?`
    // externes (jamais nulles à l'exécution) — le `!!` est la seule adaptation de type imposée.

    @Test
    fun activationSequenceFollowsTheNormativeMapping() {
        var s = UIKitLifecycleReducer.reduce(
            platform.UIKit.UISceneWillEnterForegroundNotification!!,
            UIKitLifecycleSnapshot(visibility = VisibilityState.Background, activation = ActivationState.Inactive),
        )!!
        assertEquals(VisibilityState.Foreground, s.visibility)
        assertEquals(ActivationState.Inactive, s.activation)
        s = UIKitLifecycleReducer.reduce(platform.UIKit.UISceneDidActivateNotification!!, s)!!
        assertEquals(ActivationState.Active, s.activation)
        s = UIKitLifecycleReducer.reduce(platform.UIKit.UISceneWillDeactivateNotification!!, s)!!
        assertEquals(ActivationState.Inactive, s.activation)
        assertEquals(VisibilityState.Foreground, s.visibility)
        s = UIKitLifecycleReducer.reduce(platform.UIKit.UISceneDidEnterBackgroundNotification!!, s)!!
        assertEquals(VisibilityState.Background, s.visibility)
        assertEquals(ActivationState.Inactive, s.activation)
    }

    @Test
    fun unknownNotificationIsIgnored() {
        assertNull(
            UIKitLifecycleReducer.reduce(
                "inconnue",
                UIKitLifecycleSnapshot(VisibilityState.Foreground, ActivationState.Active),
            ),
        )
    }

    @Test
    fun disconnectedNotificationIsNotAReducibleState() {
        // DidDisconnect n'est PAS un snapshot : c'est une terminaison routée à part.
        assertNull(
            UIKitLifecycleReducer.reduce(
                platform.UIKit.UISceneDidDisconnectNotification!!,
                UIKitLifecycleSnapshot(VisibilityState.Foreground, ActivationState.Active),
            ),
        )
    }
}
