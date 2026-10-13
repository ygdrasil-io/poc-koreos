@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.graphiks.kadre.internal.uikit

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ExportObjCClass
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import org.graphiks.kadre.input.DropOfferId
import org.graphiks.kadre.interaction.InteractionKind
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.surface.LogicalPoint
import platform.Foundation.NSItemProvider
import platform.darwin.NSObject
import platform.UIKit.UIDragItem
import platform.UIKit.UIDropInteraction
import platform.UIKit.UIDropInteractionDelegateProtocol
import platform.UIKit.UIDropOperationCancel
import platform.UIKit.UIDropOperationCopy
import platform.UIKit.UIDropProposal
import platform.UIKit.UIDropSessionProtocol
import platform.UIKit.UIView
import platform.UIKit.addInteraction
import platform.UIKit.removeInteraction

/**
 * Le delegate drop natif d'une vue (iOS seul) — le patron de conformance prouvé par la SPIKE
 * Task 1 (compilée iosSimulatorArm64 ET rejouée au gate) :
 *
 * - `@ExportObjCClass ... : NSObject(), UIDropInteractionDelegateProtocol` compile (le précédent
 *   `UIKitTextInputReceiver`, mais sur NSObject : `UIDropInteraction` n'est PAS une base
 *   Kotlin-sous-classable pour un delegate — le delegate est un objet à part, la classe du
 *   spike l'établit) ;
 * - les cinq hooks du protocole partagent la base Kotlin `dropInteraction` (l'étiquette du
 *   premier paramètre du sélecteur Obj-C est absorbée) — Kotlin les voit en surcharges
 *   CONFLICTUELLES et chaque override exige [ObjCSignatureOverride]
 *   (`dropInteraction:canHandleSession:` vs `dropInteraction:sessionDidEnter:` …) ;
 * - le paramètre `session` est `UIDropSessionProtocol` — il n'existe PAS de classe
 *   `platform.UIKit.UIDropSession` dans le klib (le protocole Obj-C arrive en interface Kotlin).
 *
 * LE PONT VERS LE SEAM PUR ([UIKitDropHooks]) : le delegate ne connaît ni le port ni le
 * runtime. Il convertit la position (`session.locationInView` → [uikitLogicalPoint] — l'espace
 * de la vue EST l'espace logique, aucune multiplication d'échelle), snapshotte les items
 * ([ukitDropSnapshot], Task 1) et pose SES questions aux hooks :
 *
 * - `canHandle` — la sonde à types enregistrés : AU MOINS UN item provider dont le snapshot
 *   Task 1 est non nul (au moins une UTI que la table fermée porte). Sonde BON MARCHÉ : les
 *   `registeredTypeIdentifiers` et les métadonnées sont disponibles à tout moment (header
 *   UIDragSession.h), aucun payload n'y est attaché ;
 * - `sessionDidEnter` — la construction de la source : chaque provider dont le snapshot est
 *   non nul devient un [UIKitDropItem] avec son payload LAZY attaché ([ukitPayloadFromProvider]
 *   — le handle natif vit dans la closure, la livraison entière mise en cache à la première
 *   lecture) ; les providers SANS type mappable sont SAUTÉS (un item que le modèle ne peut pas
 *   décrire n'est pas un item — un descripteur à mimes vides serait un mensonge). La source
 *   part aux hooks ; l'offerId rendu est RETENU ICI (le retenu des hooks et le nôtre vivent en
 *   lockstep — entrée, sortie, perform et fin de session le consomment chacun à leur tour) ;
 * - `sessionDidUpdate` — la table des opérations du plan : proposition `Copy` SI ET SEULEMENT
 *   si une offre vit encore (`hooks.moved`, dont le rendu porte la réponse), `Cancel` sinon —
 *   JAMAIS `Move` (le plan épinge la copie, sans égard à `allowsMoveOperation`) ;
 * - `sessionDidExit` — l'offre est terminée aux hooks (`DropExited` publié une fois) et
 *   oubliée ici ;
 * - `performDrop` — le drop : `hooks.performed` décide (le stimulus `DropPerformed` ne part
 *   que pour l'offre vivante). COURBE DE SIGNATURE (header UIDropInteraction.h:164 vérifié) :
 *   le sélecteur Obj-C rend `void` — le Bool de [UIKitDropHooks.performed] ne peut pas remonter
 *   à UIKit (c'est la proposition de `sessionDidUpdate` qui a déjà accepté le drop) ; il porte
 *   le nettoyage LOCAL : l'offerId retenu n'est oublié que sur un drop réellement complété ;
 * - `concludeDrop` — le point « état final post-drop » d'UIKit : Kadre ne dessine rien, aucun
 *   état natif ne survit — no-op (les livraisons continuent d'être comptées) ;
 * - `sessionDidEnd` (header l.176-179 : « you may now forget it », appelé à la fin du drag pour
 *   toute interaction ayant vu enter/update/exit) — le balayage de sûreté : une offre encore
 *   tenue ICI (un drag annulé en plein survol, la sortie jamais livrée) est terminée aux hooks
 *   comme une sortie — le registre des offres ne retient jamais un drag mort.
 *
 * GARDE DE TEARDOWN (pattern Stale de la famille) : [detach] éteint toute livraison déjà en
 * file au moment du retrait de l'interaction — `canHandle` répond faux, la proposition vire à
 * l'annulation, les observations deviennent des no-ops comptés zéro. La construction des
 * entries à l'entrée ne touche AUCUN objet hors la session (les snapshots sont des copies), et
 * la source refusée par les hooks est fermée par le runtime, jamais retenue ici.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@ExportObjCClass
internal class UIKitDropDelegate(
    private val view: UIView,
    private val hooks: UIKitDropHooks,
) : NSObject(), UIDropInteractionDelegateProtocol {
    private val lock = RuntimeLock()
    private var detached: Boolean = false
    private var retainedOffer: DropOfferId? = null

    /** Éteint le delegate ([UIKitDropBindingIos.teardown]) : toute livraison en file est rejetée ici. */
    fun detach() {
        lock.withLock { detached = true }
    }

    // MARK: - le protocole (chaque override porte @ObjCSignatureOverride — fait du spike)

    @ObjCSignatureOverride
    override fun dropInteraction(interaction: UIDropInteraction, canHandleSession: UIDropSessionProtocol): Boolean {
        if (lock.withLock { detached }) return false
        UIKitDropCount.recordEvent()
        return hasMappableProvider(canHandleSession)
    }

    @ObjCSignatureOverride
    override fun dropInteraction(interaction: UIDropInteraction, sessionDidEnter: UIDropSessionProtocol) {
        if (lock.withLock { detached }) return
        UIKitDropCount.recordEvent()
        val position = positionOf(sessionDidEnter) ?: return
        val entries = entriesOf(sessionDidEnter)
        // Une session sans item descriptible n'ouvre rien — les hooks ne voient pas de source vide.
        if (entries.isEmpty()) return
        val offer = hooks.entered(UIKitDropTransferSource(entries), position)
        lock.withLock { retainedOffer = offer }
    }

    @ObjCSignatureOverride
    override fun dropInteraction(interaction: UIDropInteraction, sessionDidUpdate: UIDropSessionProtocol): UIDropProposal {
        if (lock.withLock { detached }) return UIDropProposal(dropOperation = UIDropOperationCancel)
        UIKitDropCount.recordEvent()
        val offer = lock.withLock { retainedOffer }
        val held = offer != null && positionOf(sessionDidUpdate)?.let { hooks.moved(offer, it) } == true
        // La table du plan : copie si une offre vit (le rendu de moved EST la réponse), annulation
        // sinon — jamais Move.
        return UIDropProposal(dropOperation = if (held) UIDropOperationCopy else UIDropOperationCancel)
    }

    @ObjCSignatureOverride
    override fun dropInteraction(interaction: UIDropInteraction, sessionDidExit: UIDropSessionProtocol) {
        if (lock.withLock { detached }) return
        UIKitDropCount.recordEvent()
        val offer = lock.withLock { retainedOffer } ?: return
        hooks.exited(offer)
        lock.withLock { retainedOffer = null }
    }

    @ObjCSignatureOverride
    override fun dropInteraction(interaction: UIDropInteraction, performDrop: UIDropSessionProtocol) {
        if (lock.withLock { detached }) return
        UIKitDropCount.recordEvent()
        val offer = lock.withLock { retainedOffer } ?: return
        val position = positionOf(performDrop)
        // Un drop sans position lisible ne complète rien (jamais de position inventée — la
        // règle de la famille) ; l'offre reste tenue jusqu'au balayage de sessionDidEnd.
        val completed = position != null && hooks.performed(offer, position)
        if (completed) {
            lock.withLock { retainedOffer = null }
        }
    }

    @ObjCSignatureOverride
    override fun dropInteraction(interaction: UIDropInteraction, concludeDrop: UIDropSessionProtocol) {
        if (lock.withLock { detached }) return
        // Le point « dessine ton état final » d'UIKit : aucune vue Kadre, aucun état natif —
        // la livraison est comptée, rien d'autre.
        UIKitDropCount.recordEvent()
    }

    /**
     * Le « you may now forget it » du header (l.176-179) : le balayage de sûreté — une offre
     * encore tenue après la fin du drag (sortie jamais livrée : drag annulé en plein survol) est
     * terminée aux hooks comme une sortie. Une offre déjà consommée (exit, perform) rend le
     * no-op attendu — idempotent par construction de la machine.
     */
    @ObjCSignatureOverride
    override fun dropInteraction(interaction: UIDropInteraction, sessionDidEnd: UIDropSessionProtocol) {
        if (lock.withLock { detached }) return
        UIKitDropCount.recordEvent()
        val offer = lock.withLock { retainedOffer } ?: return
        hooks.exited(offer)
        lock.withLock { retainedOffer = null }
    }

    // MARK: - les lectures de session (le protocole porte les membres en forme fonction)

    /** Les items de la session sont des `UIDragItem` ; le provider vit sur l'item. */
    private fun providersOf(session: UIDropSessionProtocol): List<NSItemProvider> =
        session.items.filterIsInstance<UIDragItem>().map { it.itemProvider }

    /** La sonde BON MARCHÉ de `canHandle` : au moins un provider que la table Task 1 sait décrire. */
    private fun hasMappableProvider(session: UIDropSessionProtocol): Boolean =
        providersOf(session).any { provider ->
            ukitDropSnapshot(
                provider.registeredTypeIdentifiers.filterIsInstance<String>(),
                provider.suggestedName,
                null,
            ) != null
        }

    /** La position du drag dans l'espace de la vue — points logiques, `null` si non finie. */
    private fun positionOf(session: UIDropSessionProtocol): LogicalPoint? =
        session.locationInView(view).useContents { uikitLogicalPoint(x, y) }

    /**
     * Les entries descriptibles de la session : chaque provider au snapshot non nul devient un
     * item (descripteur = le snapshot Task 1, payload LAZY attaché — le handle ne quitte jamais
     * la closure) ; les autres sont SAUTÉS, jamais approximés. La taille est honnêtement
     * inconnue (`null`) : aucun entier de provider n'est lisible avant la livraison.
     *
     * Le type préféré du payload : le type que le GENRE du descripteur nomme
     * ([ukitDropPreferredTypeFor]) — la lecture charge exactement un type du mime que le
     * descripteur annonce, jamais l'un pour l'autre genre.
     */
    private fun entriesOf(session: UIDropSessionProtocol): List<UIKitDropItem> =
        providersOf(session).mapNotNull { provider ->
            val types = provider.registeredTypeIdentifiers.filterIsInstance<String>()
            val snapshot = ukitDropSnapshot(types, provider.suggestedName, null) ?: return@mapNotNull null
            val item = UIKitDropItem(snapshot)
            item.attach(ukitPayloadFromProvider(provider, ukitDropPreferredTypeFor(types, snapshot.kind)))
            item
        }
}

/**
 * Le binding drop iOS ([UIKitDropBinding]) : une `UIDropInteraction(delegate:)` Kadre-owned
 * posée ADDITIVEMENT sur la vue du host (la vue garde toute sa propre gestion — l'interaction
 * ne fait que recevoir les drags), retirée au teardown. Même cycle de garde que la famille
 * (touch, gestes) : la garde de binding mort et le tripwire de double-installation AVANT toute
 * écriture sur la vue, la re-lecture finale absorbe un teardown glissé en pleine pose. Le
 * teardown détache le delegate (livraisons en file rejetées), retire l'interaction (zéro
 * résiduel) et ÉTEINT la machine à états ([UIKitDropHooks.close] — plus aucun stimulus).
 */
internal class UIKitDropBindingIos(
    private val view: UIView,
    private val hooks: UIKitDropHooks,
) : UIKitDropBinding() {
    private val lock = RuntimeLock()
    private var tornDown: Boolean = false
    private var delegate: UIKitDropDelegate? = null
    private var interaction: UIDropInteraction? = null

    override fun install() {
        lock.withLock {
            if (tornDown) return
            check(interaction == null) { "duplicate UIKit drop interaction install" }
        }
        val createdDelegate = UIKitDropDelegate(view, hooks)
        val created = UIDropInteraction(delegate = createdDelegate)
        view.addInteraction(created)
        val live = lock.withLock {
            if (tornDown) {
                false
            } else {
                delegate = createdDelegate
                interaction = created
                true
            }
        }
        if (!live) {
            // Un teardown a passé le sien entre l'enregistrement et la pose : son propre remove
            // est tombé sur une interaction pas encore posée (no-op), le nôtre achève le retrait.
            view.removeInteraction(created)
            return
        }
        UIKitDropCount.recordInstall()
    }

    override fun teardown() {
        val stale = lock.withLock {
            if (tornDown) {
                null
            } else {
                tornDown = true
                val staleDelegate = delegate
                val staleInteraction = interaction
                delegate = null
                interaction = null
                // Une interaction jamais posée (install interrompu avant sa pose) n'a rien à
                // retirer : le teardown est déjà son propre point fixe.
                staleInteraction?.let { staleDelegate to it }
            }
        } ?: return
        stale.first?.detach()
        view.removeInteraction(stale.second)
        UIKitDropCount.recordTeardown()
        hooks.close()
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun createDropBinding(view: UIView, hooks: UIKitDropHooks, scale: Double): UIKitDropBinding? {
    // Le klib iOS porte la couture : le binding réel est construit. Le scale ne sert qu'à la
    // symétrie de signature (la conversion n'en multiplie JAMAIS — cf. KDoc du expect).
    @Suppress("UNUSED_EXPRESSION")
    scale
    return UIKitDropBindingIos(view, hooks)
}

internal actual fun uikitHandlerInteractionKinds(): Set<InteractionKind> =
    // EXACTEMENT AcceptDrop : aucun primitif fullscreen/pointer-lock/window-move n'existe sur
    // UIKit, et le drag SORTANT n'est pas annoncé (sortie roadmap).
    setOf(InteractionKind.AcceptDrop)
