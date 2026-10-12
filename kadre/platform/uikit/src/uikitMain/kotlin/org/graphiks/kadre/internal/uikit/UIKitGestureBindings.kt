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
import org.graphiks.kadre.input.GestureKind
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.SurfaceId
import platform.darwin.NSObject
import platform.UIKit.UIPanGestureRecognizer
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIView

/**
 * La fabrique plateforme des recognizers de GESTE — la couture iOS/tvOS du binding, dictée par
 * la surface RÉELLE du klib de chaque famille (vérifiée au compile ET dans le linkdata du klib
 * tvOS 2.4.20) :
 *
 * - **iOS** : `UITapGestureRecognizer` (double), `UIPanGestureRecognizer`,
 *   `UIPinchGestureRecognizer`, `UIRotationGestureRecognizer` — les quatre.
 * - **tvOS** : tap et pan seulement. `UIPinchGestureRecognizer` et
 *   `UIRotationGestureRecognizer` sont `API_UNAVAILABLE(tvos)` (UIPinchGestureRecognizer.h:19
 *   et UIRotationGestureRecognizer.h:19 du SDK AppleTVOS27.0 — Apple lui-même déclare ces
 *   deux recognizers indisponibles sur tvOS, même famille que le hover) : leur absence du
 *   klib tvOS est cette frontière de plateforme TELLE QUELLE, PAS un trou de mapping K/N.
 *   Les installer est structurellement impossible — le binding n'existe pas et n'existera
 *   pas sur tvOS tant qu'Apple ne portera pas ces classes — et les simuler serait un
 *   mensonge.
 *
 * La liste rendue EST la vérité de ce qui sera installé — l'annonce `gestureKinds` la porte
 * telle quelle (la règle de l'ensemble exact : exactement les recognizers posés sur la vue,
 * jamais un de plus). Chaque recognizer est construit en target/action sur le proxy du
 * binding ([UIKitGestureProxy]), SANS subclassement — le pattern canonique des recognizers
 * standard.
 */
internal expect fun createGestureRecognizers(target: UIKitGestureProxy): List<Pair<UIGestureRecognizer, GestureKind>>

/**
 * La fabrique plateforme du recognizer de survol — la SEULE couture iOS/tvOS du binding. La
 * classe `UIHoverGestureRecognizer` est `API_UNAVAILABLE(tvos)` (UIGestureRecognizer.h vérifié ;
 * le klib tvOS ne porte pas la classe) : l'actual iOS construit le recognizer standard en
 * target/action sur le proxy du binding, l'actual tvOS répond `null` — PAS de route pointeur
 * tvOS en v1 (décision tracée du plan), et `pointerInstalled` reste à faux.
 *
 * La construction en target/action (et non par sous-classement) est le pattern canonique des
 * recognizers standard : l'action arrive au proxy exporté ([UIKitGestureProxy]), la livraison
 * est celle de tout recognizer attaché. `null` est la réponse honnête d'une plateforme sans la
 * classe — jamais un recognizer de substitution qui ne survolerait rien.
 */
internal expect fun createHoverGestureRecognizer(target: UIKitGestureProxy, action: String): UIGestureRecognizer?

/**
 * Les branchements de gestes ET de survol (phase 5, Task 3) : des recognizers STANDARD ajoutés
 * ADDITIVEMENT à la vue du host — jamais une modification de sa propre gestion des touches —
 * avec `cancelsTouchesInView = false` partout (la décision tracée du plan : le host garde ses
 * touches ; les recognizers ne font que lire). Un SEUL proxy target/action Kadre-owned
 * ([UIKitGestureProxy], l'export `@ExportObjCClass` + `@ObjCAction` prouvé par la SPIKE de la
 * Task 2) sert les cinq recognizers :
 *
 * - `UITapGestureRecognizer` (`numberOfTapsRequired = 2`) → `Gesture(DoubleTap)`. Un tap est un
 *   geste DISCRET : le header UIKit le documente — les recognizers discrets ne passent JAMAIS
 *   par Began/Changed et ne peuvent ni échouer ni être annulés, la livraison arrive `Ended`
 *   (`Recognized == Ended`). UN seul stimulus par reconnaissance, payload tout-`null` (les
 *   invariants du kernel pour DoubleTap), phase `Ended` — jamais un Began fabriqué pour un fait
 *   discret.
 * - `UIPanGestureRecognizer` → `Gesture(Pan)` avec la TRANSLATION CUMULÉE
 *   (`translationInView`, points logiques de l'espace de la vue — la mesure propre de la
 *   source, portée telle quelle dans la `LogicalDelta`) à chaque phase `Began`/`Moved`/`Ended`.
 * - `UIPinchGestureRecognizer` → `Gesture(Pinch)` avec l'ÉCHELLE relative (`scale`).
 * - `UIRotationGestureRecognizer` → `Gesture(Rotation)` avec la rotation en RADIANS
 *   (`rotation`), cumulée depuis le début du geste.
 * - `UIHoverGestureRecognizer` (iOS seul, via [createHoverGestureRecognizer]) →
 *   `PointerEntered`/`PointerMoved`/`PointerLeft` de kind [org.graphiks.kadre.input.PointerKind.Mouse]
 *   ([uikitPointerKind] sur (stylus=faux, hover=vrai)) à la position du survol
 *   (`locationInView`, points logiques — miroir de la règle M4 du touch : aucune multiplication
 *   d'échelle). La delta du `PointerMoved` est l'arithmétique des deux dernières observations
 *   ([uikitHoverDelta] — le recognizer ne rapporte pas de delta propre) ; un mouvement sans
 *   précédent est perdu.
 *
 * PHASES ET PERTES (décision tracée) : l'état du recognizer arrive en `Long` brut du klib, la
 * route [uikitGesturePhaseForState] ne passe que `Began`/`Moved`/`Ended` — `Possible` n'est pas
 * une observation, et `Cancelled`/`Failed` n'ont PAS de chemin pour les gestes : l'observation
 * terminale est perdue explicitement (KDoc du mapper ; le runtime ne retient aucun état de
 * geste actif — la perte est documentée, jamais suspendue).
 *
 * ANNONCE (l'agrégat du port, [UIKitInputAnnouncement]) : le binding écrit le fait pointeur
 * (le survol installé — iOS) puis l'ensemble EXACT des genres installés
 * (`gestureKinds` == exactement les recognizers posés sur la vue, jamais un de plus), et publie
 * UN snapshot complet par point de cycle (install / teardown). APRÈS [teardown], les
 * recognizers sont retirés de la vue (zéro résiduel — le compte de la vue revient au baseline)
 * et toute action déjà en file est rejetée par la garde commune. Idempotent.
 */
internal class UIKitGestureBindings(
    private val view: UIView,
    private val surfaceId: SurfaceId,
    private val announcement: UIKitInputAnnouncement,
    private val publish: (SurfaceStimulus) -> Boolean,
) {
    private val lock = RuntimeLock()
    private var tornDown: Boolean = false
    private var proxy: UIKitGestureProxy? = null

    /** Les recognizers de GESTE seuls — le compte [UIKitGestureCount] gestes les porte (le survol a SES compteurs, l'équilibre install/teardown de chaque famille de compteur reste exact). */
    private var gestureRecognizers: List<UIGestureRecognizer> = emptyList()

    /** Le recognizer de survol (iOS), retenu à part : retiré comme les autres, compté à part. */
    private var hoverRecognizer: UIGestureRecognizer? = null
    private var hoverLastPosition: LogicalPoint? = null

    /**
     * Crée le proxy, les quatre recognizers de geste et (iOS) le recognizer de survol, les pose
     * `cancelsTouchesInView = false` sur la vue, puis publie l'annonce. Ordre de garde (miroir
     * touch-binding, revue M1) : garde `tornDown` et tripwire AVANT toute écriture sur la vue ;
     * un teardown glissé en pleine pose est absorbé par la re-lecture finale (les recognizers
     * déjà posés sont retirés dans la foulée). L'exception d'installation remonte à l'appelant
     * (le port enveloppe dans `runCatching` → rapport).
     */
    fun install() {
        lock.withLock {
            if (tornDown) return
            check(proxy == null) { "duplicate UIKit gesture bindings install" }
        }
        val created = UIKitGestureProxy(
            onTap = { tapRecognized(it) },
            onPan = { panRecognized(it) },
            onPinch = { pinchRecognized(it) },
            onRotation = { rotationRecognized(it) },
            onHover = { hoverRecognized(it) },
        )
        // La vérité par famille : la fabrique plateforme construit CE QUE LE KLIB PORTE
        // (les quatre en iOS, tap+pan en tvOS — cf. KDoc de [createGestureRecognizers]).
        val gestures = createGestureRecognizers(created)
        gestures.forEach { (recognizer, _) -> recognizer.cancelsTouchesInView = false }
        gestures.forEach { (recognizer, _) -> view.addGestureRecognizer(recognizer) }
        val hover = createHoverGestureRecognizer(target = created, action = GESTURE_HOVER_ACTION)
        if (hover != null) {
            hover.cancelsTouchesInView = false
            view.addGestureRecognizer(hover)
        }
        val live = lock.withLock {
            if (tornDown) {
                false
            } else {
                proxy = created
                gestureRecognizers = gestures.map { it.first }
                hoverRecognizer = hover
                true
            }
        }
        if (!live) {
            // Un teardown a passé le sien entre l'enregistrement et la pose : le sien est tombé
            // sur une liste pas encore enregistrée (no-op), le nôtre achève le retrait.
            (gestures.map { it.first } + listOfNotNull(hover)).forEach { view.removeGestureRecognizer(it) }
            return
        }
        UIKitGestureCount.recordInstalls(gestures.size.toLong())
        if (hover != null) {
            UIKitGestureCount.recordHoverInstall()
            announcement.pointer(true)
        }
        UIKitGestureCount.recordAnnouncement()
        publish(announcement.gestures(gestures.map { it.second }.toSet()))
    }

    /**
     * Retrait : recognizers retirés de la vue (zéro résiduel), fait pointeur révoqué, ensemble
     * des gestes REMPLACÉ par vide (la règle de l'ensemble exact), annonce de révocation
     * publiée. Idempotent.
     */
    fun teardown() {
        val stale = lock.withLock {
            if (tornDown) {
                null
            } else {
                tornDown = true
                Pair(gestureRecognizers, hoverRecognizer).also {
                    gestureRecognizers = emptyList()
                    hoverRecognizer = null
                    hoverLastPosition = null
                    proxy = null
                }
            }
        } ?: return
        (stale.first + listOfNotNull(stale.second)).forEach { view.removeGestureRecognizer(it) }
        // Le MÊME compte que l'install (les gestes seuls — le survol a le sien) : un cycle
        // complet install/teardown laisse chaque compteur équilibré.
        UIKitGestureCount.recordTeardowns(stale.first.size.toLong())
        if (stale.second != null) {
            UIKitGestureCount.recordHoverTeardown()
            announcement.pointer(false)
        }
        UIKitGestureCount.recordAnnouncement()
        publish(announcement.gestures(emptySet()))
    }

    // MARK: - Les actions du proxy (la garde de teardown passe AVANT toute publication)

    /**
     * Le tap discret : la livraison `Ended` seule (le header UIKit documente que les gestes
     * discrets ne passent jamais Began/Changed), payload tout-`null` — le kernel
     * [uikitGesture] valide l'invariant DoubleTap.
     */
    internal fun tapRecognized(recognizer: UIGestureRecognizer) {
        val phase = uikitGesturePhaseForState(recognizer.state) ?: return
        val stimulus =
            uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.DoubleTap, phase)) ?: return
        if (lock.withLock { tornDown }) return
        UIKitGestureCount.recordStimulus()
        publish(stimulus)
    }

    /** Le pan : la translation cumulée de la source, portée telle quelle. */
    internal fun panRecognized(recognizer: UIGestureRecognizer) {
        val phase = uikitGesturePhaseForState(recognizer.state) ?: return
        val pan = recognizer as? UIPanGestureRecognizer ?: return
        val translation = pan.translationInView(view).useContents { x to y }
        val stimulus = uikitGesture(
            surfaceId,
            UIKitGesturePrimitive(
                kind = UIKitGestureKind.Pan,
                phase = phase,
                translationX = translation.first,
                translationY = translation.second,
            ),
        ) ?: return
        if (lock.withLock { tornDown }) return
        UIKitGestureCount.recordStimulus()
        publish(stimulus)
    }

    /** Le pinch : l'échelle relative de la source. */
    internal fun pinchRecognized(recognizer: UIGestureRecognizer) {
        val phase = uikitGesturePhaseForState(recognizer.state) ?: return
        val scale = pinchScaleOf(recognizer) ?: return
        val stimulus = uikitGesture(
            surfaceId,
            UIKitGesturePrimitive(kind = UIKitGestureKind.Pinch, phase = phase, scale = scale),
        ) ?: return
        if (lock.withLock { tornDown }) return
        UIKitGestureCount.recordStimulus()
        publish(stimulus)
    }

    /** La rotation : les radians de la source. */
    internal fun rotationRecognized(recognizer: UIGestureRecognizer) {
        val phase = uikitGesturePhaseForState(recognizer.state) ?: return
        val rotation = rotationRadiansOf(recognizer) ?: return
        val stimulus = uikitGesture(
            surfaceId,
            UIKitGesturePrimitive(
                kind = UIKitGestureKind.Rotation,
                phase = phase,
                rotation = rotation,
            ),
        ) ?: return
        if (lock.withLock { tornDown }) return
        UIKitGestureCount.recordStimulus()
        publish(stimulus)
    }

    /**
     * Le survol (iOS) : l'entrée publie `PointerEntered` à la position observée, le mouvement
     * `PointerMoved` avec la delta ARITHMÉTIQUE des deux dernières observations, la sortie
     * `PointerLeft`. `Cancelled`/`Failed` n'ont pas de chemin (KDoc de classe — la perte est
     * documentée) ; un mouvement sans précédent (l'entrée perdue) est perdu aussi, jamais une
     * zéro fabriquée.
     */
    internal fun hoverRecognized(recognizer: UIGestureRecognizer) {
        val phase = uikitGesturePhaseForState(recognizer.state) ?: return
        when (phase) {
            UIKitTouchPhase.Began -> {
                val position = locationOf(recognizer) ?: return
                val live = lock.withLock {
                    if (tornDown) {
                        null
                    } else {
                        hoverLastPosition = position
                        true
                    }
                } ?: return
                if (!live) return
                UIKitGestureCount.recordStimulus()
                publish(
                    SurfaceStimulus.PointerEntered(
                        surfaceId = surfaceId,
                        kind = uikitPointerKind(isStylus = false, isHover = true),
                        position = position,
                    ),
                )
            }

            UIKitTouchPhase.Moved -> {
                val position = locationOf(recognizer) ?: return
                val delta = lock.withLock {
                    if (tornDown) {
                        null
                    } else {
                        val delta = uikitHoverDelta(hoverLastPosition, position) ?: return
                        hoverLastPosition = position
                        delta
                    }
                } ?: return
                UIKitGestureCount.recordStimulus()
                publish(
                    SurfaceStimulus.PointerMoved(
                        surfaceId = surfaceId,
                        kind = uikitPointerKind(isStylus = false, isHover = true),
                        position = position,
                        delta = delta,
                        pressure = null,
                        pen = null,
                    ),
                )
            }

            else -> {
                // `Ended` : la sortie — la dernière position retombe (le runtime la retient,
                // `PointerLeft` n'en porte pas). Began/Moved sont traités ci-dessus.
                val live = lock.withLock {
                    if (tornDown) {
                        false
                    } else {
                        hoverLastPosition = null
                        true
                    }
                }
                if (!live) return
                UIKitGestureCount.recordStimulus()
                publish(
                    SurfaceStimulus.PointerLeft(
                        surfaceId = surfaceId,
                        kind = uikitPointerKind(isStylus = false, isHover = true),
                    ),
                )
            }
        }
    }

    /** La position du recognizer dans l'espace de la vue (points logiques, portés tels quels). */
    private fun locationOf(recognizer: UIGestureRecognizer): LogicalPoint? {
        val (x, y) = recognizer.locationInView(view).useContents { x to y }
        return if (x.isFinite() && y.isFinite()) LogicalPoint(x, y) else null
    }
}

/**
 * L'échelle d'un recognizer pinch, lue du type propre de la classe — `null` là où le klib ne
 * porte pas la classe (tvOS : le recognizer n'est de toute façon jamais installé, l'action ne
 * peut pas y arriver).
 */
internal expect fun pinchScaleOf(recognizer: UIGestureRecognizer): Double?

/** La rotation en RADIANS d'un recognizer de rotation — `null` côté tvOS, même contrat. */
internal expect fun rotationRadiansOf(recognizer: UIGestureRecognizer): Double?

/**
 * Le nombre de taps qu'exige le recognizer DoubleTap (le contrat du plan : tap DOUBLE).
 */
internal const val DOUBLE_TAP_REQUIRED_TAPS: ULong = 2uL

/** Les sélecteurs d'action des membres [ObjCAction] du proxy (l'étiquette du premier paramètre est absorbée — KDoc [UIKitGestureProxy]). */
internal const val GESTURE_TAP_ACTION: String = "tapRecognized:"

internal const val GESTURE_PAN_ACTION: String = "panRecognized:"

internal const val GESTURE_PINCH_ACTION: String = "pinchRecognized:"

internal const val GESTURE_ROTATION_ACTION: String = "rotationRecognized:"

internal const val GESTURE_HOVER_ACTION: String = "hoverRecognized:"

/**
 * Le proxy target/action des gestes — la recette de la SPIKE Task 2 (`@ExportObjCClass` +
 * [ObjCAction]) : K/N exporte chaque membre sous le sélecteur `nomDeBase:` (l'étiquette du
 * premier paramètre est absorbée), exactement le sélecteur que la livraison d'un recognizer
 * dispatche à son target. Un SEUL objet sert les cinq recognizers ; il ne retient AUCUN état
 * (chaque action est routée vers les handlers du binding par les closures constructeur).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@ExportObjCClass
internal class UIKitGestureProxy(
    private val onTap: (UIGestureRecognizer) -> Unit,
    private val onPan: (UIGestureRecognizer) -> Unit,
    private val onPinch: (UIGestureRecognizer) -> Unit,
    private val onRotation: (UIGestureRecognizer) -> Unit,
    private val onHover: (UIGestureRecognizer) -> Unit,
) : NSObject() {
    @ObjCAction
    fun tapRecognized(sender: UIGestureRecognizer) {
        onTap(sender)
    }

    @ObjCAction
    fun panRecognized(sender: UIGestureRecognizer) {
        onPan(sender)
    }

    @ObjCAction
    fun pinchRecognized(sender: UIGestureRecognizer) {
        onPinch(sender)
    }

    @ObjCAction
    fun rotationRecognized(sender: UIGestureRecognizer) {
        onRotation(sender)
    }

    @ObjCAction
    fun hoverRecognized(sender: UIGestureRecognizer) {
        onHover(sender)
    }
}

/**
 * Le compteur process-wide des gestes et du survol (le proxy du driver, pattern
 * [UIKitTouchCount]) : les recognizers de geste réellement posés/retirés sur la vue
 * ([recordInstalls]/[recordTeardowns] — un par recognizer), la vie du survol
 * ([recordHoverInstall]/[recordHoverTeardown] — faux côté tvOS par construction), les
 * publications de stimuli ([recordStimulus]) et les annonces ([recordAnnouncement]).
 */
internal object UIKitGestureCount {
    private val lock = RuntimeLock()
    private var installs: Long = 0L
    private var teardowns: Long = 0L
    private var hoverInstalls: Long = 0L
    private var hoverTeardowns: Long = 0L
    private var stimuli: Long = 0L
    private var announcements: Long = 0L

    fun recordInstalls(count: Long) {
        lock.withLock { installs += count }
    }

    fun recordTeardowns(count: Long) {
        lock.withLock { teardowns += count }
    }

    fun recordHoverInstall() {
        lock.withLock { hoverInstalls += 1L }
    }

    fun recordHoverTeardown() {
        lock.withLock { hoverTeardowns += 1L }
    }

    fun recordStimulus() {
        lock.withLock { stimuli += 1L }
    }

    /** Une publication `InputObservationChanged` déclenchée par CE binding. */
    fun recordAnnouncement() {
        lock.withLock { announcements += 1L }
    }

    fun totalInstalls(): Long = lock.withLock { installs }

    fun totalTeardowns(): Long = lock.withLock { teardowns }

    fun totalHoverInstalls(): Long = lock.withLock { hoverInstalls }

    fun totalHoverTeardowns(): Long = lock.withLock { hoverTeardowns }

    fun totalStimuli(): Long = lock.withLock { stimuli }

    fun totalAnnouncements(): Long = lock.withLock { announcements }
}
