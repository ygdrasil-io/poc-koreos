package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import platform.UIKit.UISceneDidActivateNotification
import platform.UIKit.UISceneDidEnterBackgroundNotification
import platform.UIKit.UISceneDidDisconnectNotification
import platform.UIKit.UISceneWillDeactivateNotification
import platform.UIKit.UISceneWillEnterForegroundNotification

/**
 * Détenteur de la réservation de scène et des observers de cycle de vie qui y sont attachés.
 * Ordre de libération non négociable : observers d'abord ([UIKitLifecycleSource.close] — plus
 * aucune livraison vers les handlers), réservation ensuite.
 */
internal class UIKitOwnership(private val reservation: UIKitSceneReservation) {
    private var observedSource: UIKitLifecycleSource? = null

    fun observeLifecycle(
        source: UIKitLifecycleSource,
        onSnapshot: (UIKitLifecycleSnapshot) -> Unit,
        onDisconnect: () -> Unit,
        onLateCallback: () -> Unit,
    ): KadreResult<Unit> {
        // Assigné AVANT l'installation : même sur une installation partielle qui lève, la
        // libération (releaseAfterAttachFailure) peut fermer la source et retirer les tokens posés.
        observedSource = source
        return try {
            var current: UIKitLifecycleSnapshot? = null
            val deliver: (String) -> Unit = { notificationName ->
                if (notificationName == UISceneDidDisconnectNotification) {
                    onDisconnect()
                } else {
                    val before = current ?: UIKitLifecycleReducer.initial(source.scene)
                    before
                        ?.let { UIKitLifecycleReducer.reduce(notificationName, it) }
                        ?.let { next ->
                            current = next
                            onSnapshot(next)
                        }
                }
            }
            observedNotifications.forEach { name ->
                source.observe(name, deliver, onLateCallback)
            }
            KadreResult.Success(Unit)
        } catch (failure: Throwable) {
            KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.UIKit, "uikit-host", "lifecycle-install-failed"),
            )
        }
    }

    fun releaseAfterAttachFailure() {
        release()
    }

    fun release() {
        observedSource?.close()
        reservation.release()
    }

    private companion object {
        // Interop K/N 2.4.20 : les constantes NSNotificationName sont des `String?` externes ;
        // elles ne sont jamais nulles à l'exécution, le `!!` documente l'engagement non-nullable
        // du contrat du reducer (List<String>).
        val observedNotifications: List<String> = listOf(
            UISceneWillEnterForegroundNotification!!,
            UISceneDidActivateNotification!!,
            UISceneWillDeactivateNotification!!,
            UISceneDidEnterBackgroundNotification!!,
            UISceneDidDisconnectNotification!!,
        )
    }
}
