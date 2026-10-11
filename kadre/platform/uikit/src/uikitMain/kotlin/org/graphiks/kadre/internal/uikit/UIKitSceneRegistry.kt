package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import platform.UIKit.UIWindowScene

/**
 * Une réservation de host par scène connectée. Le pont Kotlin/Obj-C ne préserve pas l'identité
 * (`===`), la comparaison des scènes passe donc par `NSObject.isEqual` (preuve O3, phase 0).
 * Miroir de `WebHostRegistry` : identité fournie par la cible, réservation idempotente à la libération.
 */
internal class UIKitSceneRegistry {
    private val lock = RuntimeLock()
    private val reserved = mutableListOf<UIWindowScene>()

    fun reserve(scene: UIWindowScene): KadreResult<UIKitSceneReservation> = lock.withLock {
        if (reserved.any { it.isEqual(scene) }) {
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host))
        } else {
            reserved.add(scene)
            KadreResult.Success(UIKitSceneReservation(this, scene))
        }
    }

    internal fun release(scene: UIWindowScene) {
        lock.withLock {
            reserved.indexOfFirst { it.isEqual(scene) }.takeIf { it >= 0 }?.let(reserved::removeAt)
        }
    }

    internal companion object {
        val shared: UIKitSceneRegistry = UIKitSceneRegistry()
    }
}

internal class UIKitSceneReservation internal constructor(
    private val registry: UIKitSceneRegistry,
    private val scene: UIWindowScene,
) {
    private var released: Boolean = false

    fun release() {
        if (released) return
        released = true
        registry.release(scene)
    }
}
