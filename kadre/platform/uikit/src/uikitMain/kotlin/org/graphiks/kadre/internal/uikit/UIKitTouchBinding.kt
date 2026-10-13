@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.graphiks.kadre.internal.uikit

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ExportObjCClass
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.SurfaceStimulus
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.input.TouchPhase
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.SurfaceId
import platform.Foundation.NSSet
import platform.UIKit.UIEvent
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UITouch
import platform.UIKit.UIView

/**
 * Le recognizer touch Kadre-owned : une sous-classe de `UIGestureRecognizer` qui copie chaque
 * contact UIKit (`UITouch`) en primitive du kernel ([UIKitTouchPrimitive]) et n'interprète rien
 * d'autre. C'est le SPIKE du plan (Task 2, Step 1) : les quatre handlers propres d'un
 * recognizer (`touchesBegan(touches: Set<*>, withEvent: UIEvent?)` et ses trois voisins) sont
 * bien overrideables depuis Kotlin/Native — le klib porte les méthodes protégées d'
 * `UIGestureRecognizer` comme membres ouvrables de la classe (contrat KDoc : l'attach est
 * prouvé structurellement par le driver, le fire réel sur vraies touches reste le charter
 * manuel — aucun touch n'est synthétisable in-process honnêtement).
 *
 * POSTURE ADDITIVE (décision tracée du plan) : `cancelsTouchesInView = false` est posé par le
 * binding ([UIKitTouchBinding.install]) — la vue du host GARDE ses touches quoi qu'il arrive ;
 * le recognizer ne fait que lire. Il ne reconnaît aucun geste : il ne transitionne jamais
 * d'état (le klib n'expose d'ailleurs pas le setter `state`, propriété en lecture seule du
 * mapping — la livraison d'un contact n'exige pas de transition, UIKit forward les quatre
 * handlers à tout recognizer attaché et activé), il ne pose pas d'action target/action, et il
 * ne retient AUCUN objet natif : chaque `UITouch` est copiée dans la primitive avant retour.
 *
 * IDENTITÉ (leçon phase 2, miroir `AppKitTouchAdapter`) : un jeton entier est alloué UNE fois
 * par contact natif, dans une table indexée par la référence `UITouch` elle-même (l'égalité
 * des wrappers Kotlin d'objets Obj-C délègue à `isEqual` — donc à l'identité de pointeur pour
 * un `UITouch` ; preuve O3 phase 0 : deux wrappers d'une même instance, `===` faux, `hashCode`
 * et `isEqual` égaux). L'instance `UITouch` est stable et vivante pour la durée du contact
 * (contrat UIKit). La vie de la table ([UIKitTouchTokenTable]) est celle des contacts, pas de
 * la session : le jeton est alloué au `Began`, stable pendant la vie du contact, et RÉVOQUÉ à
 * l'event terminal — `Ended`/`Cancelled` portent le même jeton une DERNIÈRE fois (l'identité
 * est stable DANS la livraison), puis l'entrée disparaît et le wrapper `UITouch` retenu est
 * relâché : la table ne grossit plus avec les contacts. Un `Began` ultérieur pour la même
 * référence est un NOUVEAU contact et reçoit un NOUVEAU jeton (jamais le jeton d'un contact
 * révolué) ; un `Moved`/`Ended`/`Cancelled` pour une identité inconnue (recognizer attaché en
 * pleine séquence, ou event tardif après la fin) rendent `null` — le contact est perdu, jamais
 * inventé. [detach] vide ce qui resterait (contacts vivants au moment du teardown).
 *
 * LE FAIT DU SPIKE (Task 2, Step 1, preuve compile + binaire) : le klib K/N 2.4.20 ne porte
 * PAS les quatre handlers protégés d'`UIGestureRecognizer` (`touchesBegan:withEvent:` et
 * voisins — catégorie `UIGestureRecognizerProtected` du header UIKitCore) comme MEMBRES de la
 * classe — ils arrivent en extensions, donc non-overrideables (`overrides nothing`, erreur du
 * compilateur relevée avant repli). Les quatre méthodes CI-DESSOUS sont donc des MEMBRES
 * propres de la sous-classe, SANS `override`, EXPORTÉES par [ObjCAction] sous la classe
 * enregistrée par [ExportObjCClass] : K/N y ajoute son trampoline sous les sélecteurs standard
 * `touchesBegan:withEvent:` etc. — les mêmes sélecteurs que dispatche UIKit à tout recognizer
 * attaché, la livraison passe par `objc_msgSend` (preuve exécutable : la sonde du driver
 * compare l'IMP de la classe PROPRE de l'instance à celui de la base — différent seulement
 * quand l'export est vivant, un `respondsToSelector:` ne discriminant rien puisque la base
 * implémente les hooks ; preuve binaire complémentaire dans le rapport Task 2 ; le compteur
 * d'events du charter manuel Task 4 incrémentera sur vraies touches).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@ExportObjCClass
internal class UIKitTouchRecognizer(
    private val observationView: UIView,
    private val onObservation: (List<UIKitTouchPrimitive>) -> Unit,
) : UIGestureRecognizer(target = null, action = null) {
    private val lock = RuntimeLock()
    private var observing: Boolean = true
    private val tokenTable = UIKitTouchTokenTable<UITouch>()

    init {
        // Nom de debug lisible dans tout dump Xcode des recognizers de la vue (iOS 11+) —
        // la preuve Swift d'appartenance passe par la sonde Kotlin, pas par ce nom.
        name = TOUCH_RECOGNIZER_NAME
    }

    /**
     * Ferme le robinet des livraisons ([UIKitTouchBinding.teardown]) : un handler encore
     * appelé par un event déjà en file au moment du retrait est rejeté ICI (pattern Stale du
     * binding lifecycle), et la table des jetons est vidée (les contacts vivants au moment du
     * teardown n'ont plus de destinataire).
     */
    fun detach() {
        lock.withLock {
            observing = false
            tokenTable.clear()
        }
    }

    /**
     * Les quatre handlers propres — membres de la sous-classe EXPORTÉS comme méthodes Obj-C
     * par [ObjCAction] (le klib ne porte pas ces méthodes comme membres ouvrables — cf. le
     * fait du SPIKE en KDoc de classe ; sans l'annotation, un membre Kotlin nouveau n'émet
     * AUCUN sélecteur dans le binaire, fait relevé par inspection du framework lié).
     * L'annotation [ObjCAction] est la même route que le target/action d'`UIKitGestureRecognizerProxy`
     * (kadre-old) ; la règle de nommage K/N (le nom du premier paramètre est absorbé par le
     * nom de méthode, les suivants le suivent) produit ici exactement les sélecteurs UIKit
     * `touchesBegan:withEvent:` et voisins, que la livraison dispatche à tout recognizer
     * attaché. La super-implémentation n'est pas appelable depuis Kotlin (méthodes
     * extension-only du klib) — la base d'`UIGestureRecognizer` pour ces hooks est vide, les
     * sous-classes reconnaissantes la remplacent de la même façon.
     */
    @ObjCAction
    fun touchesBegan(touches: NSSet, withEvent: UIEvent?) {
        report(touches, UIKitTouchPhase.Began)
    }

    @ObjCAction
    fun touchesMoved(touches: NSSet, withEvent: UIEvent?) {
        report(touches, UIKitTouchPhase.Moved)
    }

    @ObjCAction
    fun touchesEnded(touches: NSSet, withEvent: UIEvent?) {
        report(touches, UIKitTouchPhase.Ended)
    }

    @ObjCAction
    fun touchesCancelled(touches: NSSet, withEvent: UIEvent?) {
        report(touches, UIKitTouchPhase.Cancelled)
    }

    /**
     * La copie d'une livraison : chaque `UITouch` du set devient une primitive (jeton stable,
     * position dans l'espace de la vue par `locationInView` — des points LOGIQUES déjà, la
     * vue ne change pas l'espace, `LogicalPoint` les porte tels quels), puis la liste est
     * remise à la callback du binding. Un handler silencieux (set vide, contact perdu) ne
     * publie rien. Les quatre phases viennent du NOM du handler —UIKit les redelivre à
     * l'identique dans `UITouch.phase`, la source de vérité est la livraison elle-même.
     *
     * Le set arrive en [NSSet] (la forme que l'annotation [ObjCAction] exporte — le mapping
     * `Set<*>` générique est refusé par elle) : l'itération indexée copie chaque élément
     * avant tout usage, aucune rétention du set lui-même.
     */
    private fun report(touches: NSSet, phase: UIKitTouchPhase) {
        // La garde AVANT tout : un handler déjà en file au moment du [detach] est rejeté ICI
        // (pattern Stale) et ne compte PAS — le compteur d'events ne porte que les livraisons
        // acceptées, ce qui rend « zéro résidu après teardown » prouvable par le driver.
        if (lock.withLock { !observing }) return
        UIKitTouchCount.recordEvent()
        val primitives = buildList {
            val enumerator = touches.objectEnumerator()
            while (true) {
                val touch = enumerator.nextObject() as? UITouch ?: break
                val token = tokenFor(touch, phase) ?: continue
                val location = touch.locationInView(observationView).useContents { x to y }
                add(
                    UIKitTouchPrimitive(
                        identityToken = token,
                        phase = phase,
                        x = location.first,
                        y = location.second,
                        pressure = touch.force,
                        // `touch.type` arrive en Long brut du klib (NS_ENUM(NSInteger)) : les
                        // valeurs sont celles d'UITouch.h vérifiées par le kernel (Pencil = 2).
                        isStylus = uikitIsStylus(touch.type.toInt()),
                    ),
                )
            }
        }
        if (primitives.isNotEmpty()) onObservation(primitives)
    }

    /**
     * Le jeton du contact — alloué au `Began`, réutilisé à l'identique pendant la vie du
     * contact, RÉVOQUÉ à l'event terminal (le `Ended`/`Cancelled` le porte une dernière fois,
     * l'entrée disparaît ensuite) et perdu (`null`) pour une identité inconnue hors `Began`.
     * Sous [lock] : le teardown (file principale) et les handlers (file principale également)
     * ne se croisent que par cette garde.
     */
    private fun tokenFor(touch: UITouch, phase: UIKitTouchPhase): Int? = lock.withLock {
        if (!observing) return null
        tokenTable.tokenFor(touch, phase)
    }
}

/** Le nom de debug du recognizer (iOS 11+), visible dans les dumps Xcode des recognizers. */
private const val TOUCH_RECOGNIZER_NAME = "KadreTouch"

/**
 * La table de jetons de contact — pure, sans aucun type UIKit, testée sans appareil
 * ([UIKitTouchTokenTableTest]) : le recognizer UIKit-typed ([UIKitTouchRecognizer]) la tient et
 * la consulte sous son verrou (la table N'EST PAS thread-safe, son propriétaire en répond).
 *
 * La sémantique est celle de la vie d'un contact (miroir `AppKitTouchAdapter`, et la contrepartie
 * identitaire de la table de vie d'[UIKitTouchAdapter]) :
 *
 * - **`Began` alloue, une fois.** Une clé inconnue reçoit un jeton neuf ; une clé déjà connue
 *   réutilise SON jeton (un `Began` redelivré ne réalloue jamais — l'identité du contact ne
 *   change pas sous ses pieds) ;
 * - **`Moved` lit.** Le jeton du contact vivant, ou `null` pour une clé inconnue ;
 * - **`Ended`/`Cancelled` lisent puis RÉVOQUENT.** L'event terminal porte encore le jeton du
 *   contact (l'identité est stable DANS la livraison qui la clôt), puis l'entrée disparaît —
 *   la clé retenu dans la table (une référence native pour le recognizer) est relâchée avec
 *   elle. La table contient donc au plus UN état par contact VIVANT ; elle ne grossit pas avec
 *   les contacts de la session ;
 * - **Un contact révolué est révolué.** Tout handler ultérieur pour la même clé rend `null`
 *   jusqu'à un nouveau `Began`, qui reçoit un jeton neuf (jamais la valeur d'un contact
 *   terminé — les jetons ne sont jamais réutilisés).
 *
 * [clear] est le teardown du propriétaire : tout ce qui survivrait (contacts vivants au moment
 * du retrait) est jeté.
 */
internal class UIKitTouchTokenTable<Key : Any> {
    private val tokensByKey = mutableMapOf<Key, Int>()
    private var nextToken: Int = 0

    /** Le jeton du contact [key] pour la phase [phase], ou `null` quand l'observation est perdue. */
    fun tokenFor(key: Key, phase: UIKitTouchPhase): Int? {
        val existing = tokensByKey[key]
        return when (phase) {
            UIKitTouchPhase.Began -> existing ?: nextToken++.also { tokensByKey[key] = it }
            UIKitTouchPhase.Moved -> existing
            UIKitTouchPhase.Ended,
            UIKitTouchPhase.Cancelled,
            -> existing.also { if (it != null) tokensByKey.remove(key) }
        }
    }

    /** Teardown du propriétaire : aucune vie de contact ne survit. */
    fun clear() = tokensByKey.clear()
}

/**
 * L'adaptateur de contacts du binding touch — le miroir exact de `AppKitTouchAdapter`
 * (KffiAppKitWindowPort.kt l.843-899), en forme UIKit : il reçoit des primitives déjà copiées
 * hors d'UIKit ([UIKitTouchPrimitive], produites par [UIKitTouchRecognizer]) et applique les
 * règles de VIE d'un contact, en amont de la publication :
 *
 * - **Un contact, une identité.** Le jeton du `Started` est retenu ; un `Started` redelivré
 *   pour le même jeton est perdu (jamais de double identité pour un même doigt) ;
 * - **Position invalide.** Une composante non finie n'est pas une position : un `Started`
 *   hors espace n'admet PAS le contact (rien n'est publié, le jeton reste inconnu) ; un
 *   `Moved` hors espace n'est pas publié (la dernière position valide est conservée) ; un
 *   `Ended`/`Cancelled` hors espace devient un `Cancelled` publié À LA DERNIÈRE POSITION
 *   VALIDE — le contact doit se terminer proprement aux yeux du runtime, même quand la source
 *   n'a plus de position à dire ;
 * - **Fin propre.** `Ended`/`Cancelled` révoquent le jeton — un `Moved` tardif pour un
 *   contact terminé est perdu, jamais résurrecté.
 *
 * La classe est pure (aucun type UIKit) et testée dans `uikitTest` sans appareil : les
 * invariants de payload (pression porteuse du stylet seul, phases 4/4 par le kernel
 * [uikitTouchPhase]) s'y prouvent. La politique de pression est celle du plan : la force
 * d'`UITouch` n'est porteuse QUE pour un stylet (`UITouchTypePencil` — la force d'un doigt
 * rapporte une base sans information utile) et seulement si la valeur validée par le kernel
 * [uikitPressure] est strictement positive — un contact de stylet qui ne rapporte pas encore
 * de force ne publie PAS de pression (`null`), jamais un zéro inventé.
 */
internal class UIKitTouchAdapter(
    private val surfaceId: SurfaceId,
) {
    private data class ActiveContact(val lastValid: LogicalPoint)

    private val activeContacts = mutableMapOf<Int, ActiveContact>()

    /** Le lot d'un handler : les primitives valides deviennent des stimuli, les autres rien. */
    fun convert(primitives: List<UIKitTouchPrimitive>): List<SurfaceStimulus.TouchChanged> =
        primitives.mapNotNull(::convert)

    /** Un contact, un jugement — `null` quand l'observation est perdue (jamais approximée). */
    fun convert(primitive: UIKitTouchPrimitive): SurfaceStimulus.TouchChanged? {
        val phase = uikitTouchPhase(primitive.phase)
        return when (phase) {
            TouchPhase.Started -> {
                if (activeContacts.containsKey(primitive.identityToken)) return null
                val position = primitive.validPosition() ?: return null
                activeContacts[primitive.identityToken] = ActiveContact(position)
                stimulus(primitive, phase, position)
            }

            TouchPhase.Moved -> {
                if (!activeContacts.containsKey(primitive.identityToken)) return null
                val position = primitive.validPosition() ?: return null
                activeContacts[primitive.identityToken] = ActiveContact(position)
                stimulus(primitive, phase, position)
            }

            TouchPhase.Ended,
            TouchPhase.Cancelled,
            -> {
                val contact = activeContacts.remove(primitive.identityToken) ?: return null
                val position = primitive.validPosition()
                if (position == null) {
                    // Fin sans position : le contact se termine À LA DERNIÈRE POSITION VALIDE,
                    // en `Cancelled` (miroir AppKitTouchAdapter — jamais une position inventée).
                    stimulus(primitive, TouchPhase.Cancelled, contact.lastValid)
                } else {
                    stimulus(primitive, phase, position)
                }
            }
        }
    }

    /** Retrait du binding : aucune vie de contact ne survit au teardown. */
    fun clear() = activeContacts.clear()

    /**
     * Le stimulus porteur : identité = le jeton stable du contact (`nativeIdentity` est opaque
     * pour le runtime — l'entier tient par égalité, jamais réalloué pendant la vie du
     * contact), pression = la politique stylet-seul du KDoc de classe.
     */
    private fun stimulus(
        primitive: UIKitTouchPrimitive,
        phase: TouchPhase,
        position: LogicalPoint,
    ): SurfaceStimulus.TouchChanged = SurfaceStimulus.TouchChanged(
        surfaceId = surfaceId,
        nativeIdentity = primitive.identityToken,
        phase = phase,
        position = position,
        pressure = primitive.pressure
            ?.let(::uikitPressure)
            ?.takeIf { primitive.isStylus && it > 0.0 },
    )

    /** La position est une position : une composante non finie n'en est pas une. */
    private fun UIKitTouchPrimitive.validPosition(): LogicalPoint? =
        if (x.isFinite() && y.isFinite()) LogicalPoint(x, y) else null
}

/**
 * Le branchement SDK du touch (phase 5, Task 2) : un recognizer touch Kadre-owned
 * ([UIKitTouchRecognizer]) ajouté ADDITIVEMENT à la vue du host, jamais une modification de
 * sa propre gestion des touches. `cancelsTouchesInView = false` (décision tracée du plan) :
 * le host garde ses touches, Kadre ne fait que lire — le recognizer ne reconnaît rien, la
 * vue ne peut de toute façon pas être annulée par lui.
 *
 * ANNONCE (miroir web l.1014-1036, AGRÉGÉE Task 3) : [install] publie l'annonce
 * `InputObservationChanged` APRÈS l'ajout du recognizer à la vue, [teardown] la révocation.
 * Depuis la Task 3, la publication passe par le PORTE-UNIQUE de l'agrégat
 * ([UIKitInputAnnouncement]) : le binding écrit SON fait (`touchInstalled`) et publie le
 * snapshot COMPLET des quatre faits — une annonce touch-only publiée après l'installation des
 * gestes ferait croire au runtime qu'ils n'existent pas (le runtime écrit les quatre champs en
 * absolu à chaque annonce). Le replay-before-announce du web (les stimuli en attente rejetés
 * par le sink avant l'annonce) n'a PAS d'équivalent nécessaire ici, et c'est prouvé par la
 * structure : toute la vie du binding vit sur la file principale (l'appelant d'[install] EST
 * sur le main, les handlers d'`UITouch` aussi) — AUCUN stimulus ne peut être produit entre la
 * création du recognizer (inerte tant qu'UIKit n'a pas livré un tour de run loop) et
 * l'annonce qui suit dans la même exécution. La file du web existe parce que ses listeners
 * peuvent y être invoqués pendant l'installation elle-même ; la sérialisation main d'UIKit
 * rend ce créneau vide. Aucune file mortneuse n'est donc posée : l'absence est la vérité.
 *
 * APRÈS [teardown], aucun callback résiduel n'agit : le recognizer est retiré de la vue,
 * [UIKitTouchRecognizer.detach] court-circuite tout handler déjà en file (pattern Stale), la
 * table de vie des contacts ([UIKitTouchAdapter]) est vidée. Idempotent.
 */
internal class UIKitTouchBinding(
    private val view: UIView,
    private val surfaceId: SurfaceId,
    private val publish: (SurfaceStimulus) -> Boolean,
    private val announcement: UIKitInputAnnouncement,
) {
    private val lock = RuntimeLock()
    private var tornDown: Boolean = false
    private var recognizer: UIKitTouchRecognizer? = null
    private val adapter = UIKitTouchAdapter(surfaceId)

    /**
     * Crée le recognizer, le pose sur la vue (`cancelsTouchesInView = false`), puis publie
     * l'annonce d'installation. Idempotent. Toute exception d'installation remonte à
     * l'appelant (le port l'enveloppe dans `runCatching` → rapport, sans jamais casser
     * l'attach — même posture que le binding d'observation).
     *
     * ORDRE DE GARDE (revue I1/M1) : la garde de binding mort (`tornDown`) et le tripwire de
     * double-installation passent AVANT TOUTE écriture sur la vue — un second install ne peut
     * jamais laisser un recognizer orphelin posé puis jeté par un check tardif. La pose sur la
     * vue suit l'enregistrement sous verrou, et un teardown glissé entre les deux est absorbé
     * par la re-lecture finale (le recognizer déjà posé est retiré dans la foulée).
     */
    fun install() {
        lock.withLock {
            if (tornDown) return
            check(recognizer == null) { "duplicate UIKit touch recognizer install" }
        }
        val binding = UIKitTouchRecognizer(view, onObservation = { primitives -> emit(primitives) })
        // La décision tracée du plan : le host garde ses touches — Kadre n'annule rien.
        binding.cancelsTouchesInView = false
        lock.withLock { recognizer = binding }
        view.addGestureRecognizer(binding)
        val live = lock.withLock {
            if (tornDown) {
                recognizer = null
                false
            } else {
                true
            }
        }
        if (!live) {
            // Un teardown a passé la sienne entre l'enregistrement et la pose : son propre
            // remove est tombé sur un recognizer pas encore posé (no-op), le nôtre achève le
            // retrait ; aucun compteur ni annonce — le binding est mort.
            view.removeGestureRecognizer(binding)
            return
        }
        UIKitTouchCount.recordInstall()
        UIKitTouchCount.recordAnnouncement()
        publish(announcement.touch(true))
    }

    /**
     * Retrait : recognizer détaché (les handlers déjà en file sont rejetés par lui-même) puis
     * retiré de la vue, table de vie vidée, annonce all-false + `gestureKinds` vide.
     * Idempotent. La référence est remise à `null` sous verrou : un install tardif sur un
     * binding mort suit la garde `tornDown`, jamais le tripwire de double-installation.
     */
    fun teardown() {
        val stale = lock.withLock {
            if (tornDown) {
                null
            } else {
                tornDown = true
                val stale = recognizer
                recognizer = null
                stale
            }
        } ?: return
        stale.detach()
        view.removeGestureRecognizer(stale)
        adapter.clear()
        UIKitTouchCount.recordTeardown()
        UIKitTouchCount.recordAnnouncement()
        publish(announcement.touch(false))
    }

    /**
     * Le chemin de publication des contacts observés : la garde de teardown d'abord (un lot
     * déjà en file au moment du retrait est rejeté ICI aussi — double garde avec
     * [UIKitTouchRecognizer.detach]), puis le jugement de vie ([UIKitTouchAdapter.convert]),
     * puis le sink. Chaque stimulus parti qui DEVANT l'annonce d'installation par
     * construction (l'annonce est publiée dans le même tour main que l'install, avant tout
     * tour de run loop — cf. KDoc de classe), jamais derrière.
     */
    private fun emit(primitives: List<UIKitTouchPrimitive>) {
        if (lock.withLock { tornDown }) return
        UIKitTouchCount.recordStimuli(primitives.size.toLong())
        primitives.forEach { primitive ->
            adapter.convert(primitive)?.let { stimulus -> publish(stimulus) }
        }
    }
}

/**
 * Le compteur process-wide du touch (le proxy du driver, pattern [UIKitObservationCount]) :
 * les livraisons d'`UITouch` réellement ACCEPTÉES par le recognizer ([recordEvent] — le fait
 * du SPIKE en vraies touches, incrémenté à chaque livraison passée la garde de teardown ; les
 * handlers déjà en file au moment du retrait ne comptent pas), les stimuli de contacts
 * remis au sink ([recordStimuli]), les annonces `InputObservationChanged`
 * ([recordAnnouncement] — installation ET révocation), et les installations/retraits
 * ([recordInstall]/[recordTeardown]). Interne au module ; la lecture du driver passe par la
 * sonde [org.graphiks.kadre.platform.uikit.KadreUikitSurfaceDriver] — Task 4 prouvera le
 * touch réel par l'incrémentation du compteur d'events sur un charter manuel.
 */
internal object UIKitTouchCount {
    private val lock = RuntimeLock()
    private var installs: Long = 0L
    private var teardowns: Long = 0L
    private var events: Long = 0L
    private var stimuli: Long = 0L
    private var announcements: Long = 0L

    fun recordInstall() {
        lock.withLock { installs += 1L }
    }

    fun recordTeardown() {
        lock.withLock { teardowns += 1L }
    }

    fun recordEvent() {
        lock.withLock { events += 1L }
    }

    fun recordStimuli(count: Long) {
        lock.withLock { stimuli += count }
    }

    /** Une publication `InputObservationChanged` DÉCLENCHÉE par ce binding (l'installation OU la révocation — le snapshot complet est celui de l'agrégat du port, [UIKitInputAnnouncement]). */
    fun recordAnnouncement() {
        lock.withLock { announcements += 1L }
    }

    fun totalInstalls(): Long = lock.withLock { installs }

    fun totalTeardowns(): Long = lock.withLock { teardowns }

    fun totalEvents(): Long = lock.withLock { events }

    fun totalStimuli(): Long = lock.withLock { stimuli }

    fun totalAnnouncements(): Long = lock.withLock { announcements }
}
