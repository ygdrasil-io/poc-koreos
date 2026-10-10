package org.graphiks.kadre.platform.android

import android.os.Looper
import android.view.View
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatformApi
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.surface.HostSurface

/**
 * Le port interne du bail d'accès natif — le miroir Android du `WebElementLeasePort` web.
 *
 * La View circule en `Any` : le port ne nomme jamais le type SDK, la façade publique seule
 * ([withAndroidView]) le fait réapparaître au bord. Le block peut suspendre — la callback de la
 * façade, elle, ne suspend pas — et la View qu'il reçoit n'est valide que jusqu'à sa fin :
 * retour, exception ou cancellation, rien ne la retient au-delà.
 */
internal interface AndroidViewLeasePort {
    /** Exécute [block] avec la View louée, ou échoue sans l'invoquer. */
    public suspend fun <R> lease(block: suspend (Any) -> R): KadreResult<R>
}

/**
 * Le transfert vers le thread host — le thread principal Android, celui qui possède la View.
 * La décision y est entière : inline si l'appelant y est déjà, sinon le transfert dispatché.
 * La couture SDK seule est injectable (le miroir du `FrameClock` du scheduler) : la logique du
 * bail vit dans la surface, la façon d'atteindre le thread host vit ici.
 */
internal interface AndroidHostThreadDispatch {
    /** Exécute [block] sur le thread host, inline ou transféré. */
    public suspend fun <R> dispatch(block: suspend () -> R): R
}

/**
 * La couture de production du thread host : inline si le Looper courant est le Looper main,
 * sinon `withContext(Dispatchers.Main.immediate)` — l'attente du transfert reste annulable
 * (un appelant annulé avant le départ n'invoque rien ; la surface enveloppe elle-même
 * l'invocation démarrée de `NonCancellable`). Aucun appel SDK à la construction : les host
 * tests qui injectent leur propre couture ne touchent jamais le Looper.
 */
internal object AndroidMainThreadDispatch : AndroidHostThreadDispatch {
    override suspend fun <R> dispatch(block: suspend () -> R): R =
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            withContext(Dispatchers.Main.immediate) { block() }
        }
}

/**
 * Prête la View du host Android pour la durée de [block], et pas plus.
 *
 * Le modèle est `withWebElement` (web), et le contrat avec lui : l'objet n'est valide que
 * pendant [block] — le retenir, ou l'utiliser après son retour, est hors contrat. Un bail n'est
 * pas réentrant pour une même surface : un bail commencé pendant qu'un autre est en vol répond
 * `TemporarilyUnavailable(retryable = true)`, et une surface fermée répond `Closed(Surface)` à
 * tout bail suivant. Le block court sur le thread principal Android ; une invocation démarrée
 * s'achève même si la coroutine appelante est annulée pendant lui — la cancellation se manifeste
 * chez l'appelant après — et l'exception applicative du block se propage telle quelle.
 *
 * Échoue sans invoquer [block] quand la surface ne loue rien : surface non Android
 * (`Unsupported(PlatformSurfaceAccess)`), autre bail en vol (`TemporarilyUnavailable`,
 * retryable), ou surface terminale (`Closed(Surface)`).
 *
 * Le double opt-in est le contrat de la frontière : [KadrePlatformApi] parce que le type SDK
 * traverse, [DelicateKadreApi] parce que tenir une View hors de [block] est exactement le danger
 * que la surface promet de ne jamais faire courir ailleurs.
 */
@KadrePlatformApi
@DelicateKadreApi
public suspend fun <R> HostSurface.withAndroidView(block: (View) -> R): KadreResult<R> {
    val lease = this as? AndroidViewLeasePort
        ?: return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.PlatformSurfaceAccess))
    return lease.lease { view -> block(view as View) }
}
