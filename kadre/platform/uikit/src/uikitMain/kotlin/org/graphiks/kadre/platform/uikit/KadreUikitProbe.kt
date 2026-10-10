package org.graphiks.kadre.platform.uikit

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import org.graphiks.kadre.diagnostics.KadrePlatformApi
import platform.UIKit.UIView
import platform.UIKit.UIWindow

/** État d'appartenance de la vue observée à la fenêtre fournie. */
public enum class KadreUikitWindowMembership {
    /** `view.window` est exactement la fenêtre fournie. */
    Attached,

    /** La vue n'est pas dans la fenêtre fournie (autre fenêtre ou aucune). */
    Detached,
}

/** Lecture immédiate et copiée d'un objet UIKit fourni par le host. Aucune rétention native. */
public data class KadreUikitObservation(
    public val windowMembership: KadreUikitWindowMembership,
    public val sceneConnected: Boolean,
    public val viewBoundsWidth: Double,
    public val viewBoundsHeight: Double,
    public val displayScale: Double,
)

/**
 * Sonde de conduite de la phase 0 : prouve que le code Kotlin/Native observe des objets UIKit
 * réels reçus du host. Scaffolding du driver — remplacé par `KadreIos.attach` en phase 1 ;
 * ne fait partie d'aucune façade promise. Marqué platform-api : opt-in explicite du consumer.
 */
@KadrePlatformApi
public object KadreUikitProbe {
    @OptIn(ExperimentalForeignApi::class)
    public fun observe(window: UIWindow, view: UIView): KadreUikitObservation {
        val membership =
            if (view.window === window) KadreUikitWindowMembership.Attached
            else KadreUikitWindowMembership.Detached
        val sceneConnected = window.windowScene != null
        // CGRect n'expose pas width/height : ce sont les champs de son CGSize (`size`). Sous
        // useContents, `size` est déjà une vue CStructVar dont les champs scalaires se lisent
        // directement (le snippet du brief lisait width/height directement sur CGRect).
        val bounds = view.bounds.useContents { size.width to size.height }
        val scale = view.traitCollection.displayScale
        return KadreUikitObservation(
            windowMembership = membership,
            sceneConnected = sceneConnected,
            viewBoundsWidth = bounds.first,
            viewBoundsHeight = bounds.second,
            displayScale = scale,
        )
    }
}
