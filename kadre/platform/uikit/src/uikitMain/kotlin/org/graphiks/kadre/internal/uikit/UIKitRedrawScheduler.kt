package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.SurfaceRedrawGeneration
import org.graphiks.kadre.internal.runtime.withLock

/**
 * Coalescing du redraw : au plus UN drain programmé ; les requêtes en rafale rejoignent le
 * drain en vol et seule la DERNIÈRE génération est consommée. `cancel()` (fermeture du port)
 * arrête tout pour toujours — aucun drain après fermeture. Pas de boucle permanente : le
 * scheduling est injecté (production : un `DispatchQueue.main.async` par drain).
 *
 * Miroir d'`AndroidRedrawScheduler` (l.21-64) : le moniteur interne ne sert qu'à rendre le
 * trio request/cancel/drain inoffensif si une course de threads survenait quand même (tout le
 * port UIKit vit sur le thread principal) ; `onDrain` est invoqué HORS du moniteur — il ne
 * tient jamais le verrou du scheduler. Le consommateur du drain (le port) doit consommer la
 * génération exacte du ticket pendant, exactement une fois — un ticket jamais consommé ferme
 * le robinet de redraw de la surface pour toujours (le runtime ne réalloue qu'après
 * consommation, `RuntimeWindowSurface.requestRedraw`).
 */
internal class UIKitRedrawScheduler(
    private val onDrain: (SurfaceRedrawGeneration) -> Unit,
    private val schedule: (() -> Unit) -> Unit,
) {
    private val lock = RuntimeLock()
    private var pending: SurfaceRedrawGeneration? = null
    private var scheduled = false
    private var cancelled = false

    fun request(generation: SurfaceRedrawGeneration) {
        // Style expression (pas de return non local) : le withLock inline du klib runtime
        // l'interdit côté natif — même posture que le reste du port UIKit.
        val scheduleDrain = lock.withLock {
            if (cancelled) {
                false
            } else {
                pending = generation
                val wasScheduled = scheduled
                scheduled = true
                !wasScheduled
            }
        }
        if (scheduleDrain) schedule { drainScheduled() }
    }

    fun cancel() {
        lock.withLock {
            cancelled = true
            pending = null
            scheduled = false
        }
    }

    private fun drainScheduled() {
        // Le type du `val` est explicite : à travers le `withLock<T>` générique, une value
        // class nullable (SurfaceRedrawGeneration) s'efface en `Any?` sans lui.
        val generation: SurfaceRedrawGeneration? = lock.withLock {
            if (cancelled) {
                null
            } else {
                scheduled = false
                val current = pending
                pending = null
                current
            }
        }
        if (generation != null) onDrain(generation)
    }
}
