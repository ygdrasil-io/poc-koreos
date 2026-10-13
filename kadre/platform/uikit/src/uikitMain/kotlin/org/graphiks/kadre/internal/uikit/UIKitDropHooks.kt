package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.input.DropOfferId
import org.graphiks.kadre.internal.runtime.DropTransferSource
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.SurfaceStimulus
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.SurfaceId

/**
 * Le seam PUR entre le delegate drop natif (iOS seul, iosMain) et le pipeline commun — la
 * contrepartie drop des hooks d'input des autres bindings : le delegate ne connaît NI le port NI
 * le runtime, chaque callback du drag lui pose SA question et le rendu décide. Aucun type UIKit
 * ne traverse : les positions sont des [LogicalPoint] déjà converties, les sources sont les
 * sources Kotlin de la couture ([DropTransferSource]), l'identité de l'offre est celle que le
 * runtime a allouée ([DropOfferId]).
 *
 * La vie d'un drag, portée par [UIKitDropSessionHooks] :
 *
 * - `entered` présente la source au dispatch synchrone du port et RETIENT l'offre rendue —
 *   `null` quand le runtime/consumer a refusé (surface inconnue, consumer sans `AcceptDrop`) ;
 *   dans ce cas le runtime A DÉJÀ fermé la source et le binding ne doit PAS la retenir ;
 * - `moved` observe le déplacement POUR L'OFFRE RETENUE seule — le rendu dit si une offre vit
 *   encore, ce qui porte la réponse native (copie proposée ou annulation) ;
 * - `exited`/`performed` terminent l'offre — la publication du stimulus correspondant n'arrive
 *   que pour l'offre active, une seule fois ;
 * - `close` (le teardown du binding) éteint la machine : plus aucun stimulus ne part après lui.
 */
internal interface UIKitDropHooks : AutoCloseable {
    /**
     * Présente l'offre ; retourne l'offerId retenu, ou `null` quand le runtime/consumer a refusé
     * (le binding ne doit alors PAS retenir la source — le runtime l'a déjà fermée sur le chemin
     * non-admis).
     */
    fun entered(source: DropTransferSource, position: LogicalPoint): DropOfferId?

    /** L'observation de mouvement ; retourne si une offre est encore tenue (porte la réponse native). */
    fun moved(offerId: DropOfferId, position: LogicalPoint): Boolean

    fun exited(offerId: DropOfferId)

    /** Retourne si le drop s'est complété dans une offre vivante. */
    fun performed(offerId: DropOfferId, position: LogicalPoint): Boolean

    fun holdsActiveOffer(): Boolean

    override fun close()
}

/**
 * La machine à états d'UNE session drop (l'implémentation du port) : les hooks du delegate
 * ferment sur les DEUX sinks du port — le sink de dispatch synchrone (l'admission) et le sink
 * de publication (les stimuli de suivi). État : AU PLUS UNE offre active (le contrat UIKit —
 * une session de drag est une entrée, un mouvement, une sortie ou un drop ; le runtime de son
 * côté termine toute offre précédente à la présentation de la suivante).
 *
 * Les règles (testées pur, [UIKitDropHooksTest]) :
 *
 * - **`entered` ne publie AUCUN stimulus** — le `DropEntered` du runtime vit dans l'événement
 *   d'interaction du dispatch, pas dans le canal de stimuli ; le rendu `null` ne retient rien
 *   et ne referme pas la source (le runtime l'a déjà fait sur le chemin refusant) ;
 * - **un offerId étranger est un no-op silencieux** — `moved` répond faux, `exited`/`performed`
 *   ne publient rien ; l'offre active publie UNE fois puis disparaît (le stimulus
 *   `DropExited`/`DropPerformed` part exactement une fois par offre) ;
 * - **après [close], plus rien** — `holdsActiveOffer` répond faux (la réponse native vire à
 *   l'annulation), les observations sont des no-ops, et un `entered` tardif est refusé : le
 *   dispatch n'a plus de destinataire, la source portée serait sinon perdue aux handles — les
 *   hooks la referment EUX-MÊMES (le runtime ne l'a jamais vue).
 *
 * Tout vit sur la file principale (les livraisons d'[UIKitDropDelegate] comme le teardown du
 * port) ; le verrou porte la discipline de la famille, jamais un croisement réel.
 */
internal class UIKitDropSessionHooks(
    private val surfaceId: SurfaceId,
    private val dropSink: (SurfaceId, DropTransferSource, LogicalPoint) -> DropOfferId?,
    private val publish: (SurfaceStimulus) -> Boolean,
) : UIKitDropHooks {
    private val lock = RuntimeLock()
    private var activeOffer: DropOfferId? = null
    private var closed: Boolean = false

    override fun entered(source: DropTransferSource, position: LogicalPoint): DropOfferId? {
        val refused = lock.withLock { closed }
        if (refused) {
            // Le dispatch n'aura jamais lieu : le runtime ne refermera jamais cette source.
            source.close()
            return null
        }
        val offer = dropSink(surfaceId, source, position)
        lock.withLock {
            // null → rien retenu (le runtime a fermé la source) ; une offre remplace
            // exactement la précédente (le runtime termine la sienne à la présentation).
            activeOffer = offer
        }
        return offer
    }

    override fun moved(offerId: DropOfferId, position: LogicalPoint): Boolean {
        val live = lock.withLock { !closed && activeOffer == offerId }
        if (!live) return false
        publish(SurfaceStimulus.DropMoved(surfaceId, offerId, position))
        return true
    }

    override fun exited(offerId: DropOfferId) {
        val live = lock.withLock {
            if (closed || activeOffer != offerId) {
                false
            } else {
                activeOffer = null
                true
            }
        }
        if (live) publish(SurfaceStimulus.DropExited(surfaceId, offerId))
    }

    override fun performed(offerId: DropOfferId, position: LogicalPoint): Boolean {
        val live = lock.withLock {
            if (closed || activeOffer != offerId) {
                false
            } else {
                activeOffer = null
                true
            }
        }
        if (!live) return false
        publish(SurfaceStimulus.DropPerformed(surfaceId, offerId, position))
        return true
    }

    override fun holdsActiveOffer(): Boolean = lock.withLock { !closed && activeOffer != null }

    override fun close() {
        lock.withLock {
            activeOffer = null
            closed = true
        }
    }
}
