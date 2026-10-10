package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIWindowScene
import platform.darwin.NSObjectProtocol

/**
 * Wrapper des observer tokens NSNotificationCenter d'une scène : un observer par notification
 * (filtre `object = scene`, plus une double vérification `isEqual` dans le block — défense en
 * profondeur, gratuite), tous installés sur la file principale. [close] retire TOUS les tokens.
 */
internal class UIKitLifecycleSource(internal val scene: UIWindowScene) {
    private val lock = RuntimeLock()
    private var closed: Boolean = false
    private var tokens: List<NSObjectProtocol> = emptyList()

    val isClosed: Boolean
        get() = lock.withLock { closed }

    /**
     * Installe un observer de scène pour [notificationName]. Toute exception d'installation
     * remonte à l'appelant (UIKitOwnership la convertit en failure de plateforme) ; les tokens
     * déjà posés restent retirables par [close].
     */
    fun observe(
        notificationName: String,
        onEvent: (String) -> Unit,
        onLateCallback: () -> Unit,
    ) {
        val token = NSNotificationCenter.defaultCenter.addObserverForName(
            notificationName,
            scene,
            NSOperationQueue.mainQueue(),
        ) { notification: NSNotification? ->
            if (lock.withLock { closed }) {
                // Livraison déjà en file au moment du close : routée comme callback tardif,
                // jamais vers les handlers d'un attach terminé.
                onLateCallback()
                return@addObserverForName
            }
            // `===` ne pont pas l'identité Obj-C (preuve O3, phase 0) : la vérification passe par
            // NSObjectProtocol.isEqual. Le `object` de NSNotification est typé Any? — le cast
            // sûr est la formulation exacte de la vérification de scène.
            val fromScene = (notification?.`object` as? NSObjectProtocol)?.isEqual(scene) == true
            if (fromScene) onEvent(notificationName)
        }
        lock.withLock { tokens = tokens + token }
    }

    fun close() {
        val stale: List<NSObjectProtocol> = lock.withLock {
            if (closed) {
                emptyList()
            } else {
                closed = true
                val taken = tokens
                tokens = emptyList()
                taken
            }
        }
        stale.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
    }
}
