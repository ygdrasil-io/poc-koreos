package org.graphiks.kadre.platform.uikit

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
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
 * une recreation/detach native committée. Après le close, toute demande de bail suivante répond
 * de façon déterministe `Unsupported(PlatformSurfaceAccess)` : la route du registre
 * ([UIKitViewLeaseRegistry]) est retirée à la fermeture, la façade ne résout plus de port.
 * `Closed(Surface)` n'est que l'issue de course en vol : un bail ADMIS dont la relecture de la
 * vue suit le teardown répond pareil, sans jamais invoquer la callback. Kadre ne garantit plus la validité de la UIView après le
 * callback. La callback ne peut pas suspendre, bien que l'opération englobante soit suspendue
 * pour marshaller vers le host. Toute exception de la callback est propagée telle quelle —
 * jamais enveloppée en `KadreFailure` — après libération du bail. Une cancellation avant le
 * callback garantit qu'il n'est pas appelé ; après son début, il finit non-cancellable et son
 * résultat n'est remis que si le waiter reste actif.
 *
 * Échoue sans invoquer [block] quand la surface ne loue rien : surface non UIKit
 * (`Unsupported(PlatformSurfaceAccess)`), autre bail en vol (`TemporarilyUnavailable`,
 * retryable), ou surface terminale. Après le close, la route du registre est retirée et la
 * réponse EST `Unsupported(PlatformSurfaceAccess)` ; `Closed(Surface)` reste l'issue de course
 * d'un bail déjà admis dont la relecture de la vue suit le teardown.
 *
 * Le double opt-in est le contrat de la frontière : [KadrePlatformApi] parce que le type SDK
 * traverse, [DelicateKadreApi] parce que tenir une UIView hors de [block] est exactement le
 * danger que la surface promet de ne jamais faire courir ailleurs. La règle Kotlin-only d'INTEROP
 * §7 vaut pour la façade ET pour l'en-tête framework : [withUIKitView] est masqué des exports
 * Obj-C/Swift ([HiddenFromObjC], audit façade phase 3 — INTEROP §2 pré-autorisait l'annotation
 * sur les actuals « dès qu'un mécanisme stable existe »). La divergence F1 enregistrée en
 * phase 2 (l'en-tête exportait la fonction, et la sonde leaseProbe du driver, porteurs de
 * `UIView*`) est RÉSOLUE PAR SUPPRESSION : l'extension est strictement Kotlin-only — aucune
 * route Swift n'existe, la surface promise §5 (`KadreIos.attach`, factory, session) ne
 * contient aucun bail natif — et la preuve phase 2 (`KadreUikitSurfaceTests`) passe par la
 * sonde Kotlin [KadreUikitSurfaceDriver], qui reste visible.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
@KadrePlatformApi
@DelicateKadreApi
public suspend fun <R> HostSurface.withUIKitView(block: (UIView) -> R): KadreResult<R> {
    val port = UIKitViewLeaseRegistry.shared.portFor(id)
        ?: return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.PlatformSurfaceAccess))
    return port.lease { view -> block(view as UIView) }
}
