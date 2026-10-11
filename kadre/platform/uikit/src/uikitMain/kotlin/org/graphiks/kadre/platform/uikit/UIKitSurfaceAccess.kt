package org.graphiks.kadre.platform.uikit

import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatformApi
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.uikit.UIKitViewLeaseRegistry
import org.graphiks.kadre.surface.HostSurface
import platform.UIKit.UIView

/**
 * Prête la UIView du host UIKit/tvOS pour la durée de [block], et pas plus.
 *
 * Le modèle est `withAndroidView`/`withWebElement`, et le contrat avec eux (INTEROP §7 l.394,
 * verbatim) : la callback s'exécute sur le thread host (le thread principal UIKit — le même
 * que l'attach), n'est pas réentrante pour la même ressource — un bail commencé pendant qu'un
 * autre est en vol répond `TemporarilyUnavailable(retryable = true)` — et doit retourner avant
 * une recreation/detach native committée (une surface fermée répond `Closed(Surface)` à tout
 * bail suivant ; un bail admis dont la relecture de la vue suit le teardown répond pareil sans
 * jamais invoquer la callback). Kadre ne garantit plus la validité de la UIView après le
 * callback. La callback ne peut pas suspendre, bien que l'opération englobante soit suspendue
 * pour marshaller vers le host. Toute exception de la callback est propagée telle quelle —
 * jamais enveloppée en `KadreFailure` — après libération du bail. Une cancellation avant le
 * callback garantit qu'il n'est pas appelé ; après son début, il finit non-cancellable et son
 * résultat n'est remis que si le waiter reste actif.
 *
 * Échoue sans invoquer [block] quand la surface ne loue rien : surface non UIKit
 * (`Unsupported(PlatformSurfaceAccess)`), autre bail en vol (`TemporarilyUnavailable`,
 * retryable), ou surface terminale (`Closed(Surface)`).
 *
 * Le double opt-in est le contrat de la frontière : [KadrePlatformApi] parce que le type SDK
 * traverse, [DelicateKadreApi] parce que tenir une UIView hors de [block] est exactement le
 * danger que la surface promet de ne jamais faire courir ailleurs. Kotlin-only (INTEROP §7 :
 * aucun pointer n'apparaît dans les exports Swift).
 */
@KadrePlatformApi
@DelicateKadreApi
public suspend fun <R> HostSurface.withUIKitView(block: (UIView) -> R): KadreResult<R> {
    val port = UIKitViewLeaseRegistry.shared.portFor(id)
        ?: return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.PlatformSurfaceAccess))
    return port.lease { view -> block(view as UIView) }
}
