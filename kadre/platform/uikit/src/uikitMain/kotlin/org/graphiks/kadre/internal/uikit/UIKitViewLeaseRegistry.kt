package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.surface.SurfaceId

/**
 * Le port interne du bail d'accès natif UIKit — le miroir du port Android
 * (`AndroidViewLeasePort`, `AndroidViewLease.kt` l.22-25) et du `WebElementLeasePort` web.
 * La UIView circule en `Any` : le port ne nomme jamais le type SDK, la façade publique seule
 * (`withUIKitView`, `UIKitSurfaceAccess.kt`) le fait réapparaître au bord. Le block peut
 * suspendre — la callback de la façade, elle, ne suspend pas — et la vue qu'il reçoit n'est
 * valide que jusqu'à sa fin : retour, exception ou cancellation, rien ne la retient au-delà.
 */
internal interface UIKitViewLeasePort {
    /** Exécute [block] avec la vue louée, ou échoue sans l'invoquer. */
    public suspend fun <R> lease(block: suspend (Any) -> R): KadreResult<R>
}

/**
 * Le registre par surface des ports de bail d'accès natif (Task 4) : [SurfaceId] →
 * [UIKitViewLeasePort], la table que la façade publique `withUIKitView` consulte pour
 * retrouver le port d'une surface. Miroir d'[UIKitSceneRegistry] — instanciable pour la
 * testabilité, le câblage de production passe par [shared] — et du patron web
 * (`WebHostRegistry`) : l'identité est fournie par la cible, le retrait est idempotent.
 *
 * Tout se joue sous [RuntimeLock] : l'enregistrement (au commit du peer, par
 * `UIKitWindowRuntime`) et le retrait (par `close`, AVANT la publication de l'état détaché —
 * l'ordre Android l.415-416) consultent la même carte que la résolution de la façade, qui peut
 * arriver de n'importe quelle coroutine.
 *
 * La vue n'est JAMAIS ici — le registre ne porte que la route vers le port ; la validité de la
 * vue reste jugée par le port au moment du bail (INTEROP §7 l.394 : bornée au callback).
 */
internal class UIKitViewLeaseRegistry {
    private val lock = RuntimeLock()
    private val ports = linkedMapOf<SurfaceId, UIKitViewLeasePort>()

    /**
     * Enregistre le port d'une surface. Un doublon est une erreur de programmation (une surface
     * n'a qu'un port, le surfaceId du runtime est unique — même discipline que les maps du port
     * de fenêtre) ; le retrait, lui, est idempotent.
     */
    fun register(surfaceId: SurfaceId, port: UIKitViewLeasePort) {
        lock.withLock {
            check(ports.put(surfaceId, port) == null) { "duplicate UIKit view lease surface" }
        }
    }

    /** Retire la route d'une surface ; retirer une surface absente est absorbé. */
    fun unregister(surfaceId: SurfaceId) {
        lock.withLock { ports.remove(surfaceId) }
    }

    /** Le port d'une surface, ou `null` — la résolution de la façade (`withUIKitView`). */
    fun portFor(surfaceId: SurfaceId): UIKitViewLeasePort? = lock.withLock { ports[surfaceId] }

    internal companion object {
        /** Le registre de production — le process-wide du câblage `UIKitWindowRuntime`. */
        val shared: UIKitViewLeaseRegistry = UIKitViewLeaseRegistry()
    }
}
