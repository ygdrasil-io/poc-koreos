package org.graphiks.kadre.platform.uikit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.cValue
import kotlinx.cinterop.useContents
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatformApi
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.TextCapitalization
import org.graphiks.kadre.input.TextDocumentRevision
import org.graphiks.kadre.input.TextInputAction
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextInputEvent
import org.graphiks.kadre.input.TextInputPurpose
import org.graphiks.kadre.input.TextInputSession
import org.graphiks.kadre.input.TextRange
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.internal.uikit.UIKitDropCount
import org.graphiks.kadre.internal.uikit.UIKitGestureCount
import org.graphiks.kadre.internal.uikit.UIKitKeyboardCount
import org.graphiks.kadre.internal.uikit.UIKitObservationCount
import org.graphiks.kadre.internal.uikit.UIKitTextInputCount
import org.graphiks.kadre.internal.uikit.UIKitTextInputReceiver
import org.graphiks.kadre.internal.uikit.UIKitTouchCount
import org.graphiks.kadre.internal.uikit.UIKitTouchRecognizer
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.SurfaceEvent
import kotlinx.cinterop.ObjCClass
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.toKString
import platform.Foundation.NSRange
import platform.Foundation.NSSelectorFromString
import platform.objc.class_getMethodImplementation
import platform.objc.class_getName
import platform.objc.objc_getClass
import platform.objc.object_getClass
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIView

/** Le sélecteur exact de la livraison du premier contact (le hook que la base implémente vide). */
private const val TOUCH_DELIVERY_SELECTOR = "touchesBegan:withEvent:"

/**
 * Résultat du probe de bail ([KadreUikitSurfaceDriver.leaseProbe]) — les faits copiés, jamais
 * les objets : le verdict nominal, l'égalité Objective-C de la vue remise (`isEqual` — le `===`
 * ne pont pas l'identité Obj-C, preuve O3 phase 0), le verdict du SECOND bail tenté PENDANT le
 * vol du premier (le block du premier EST le vol : l'admission du second y répond) et sa
 * retryabilité, plus l'exception éventuelle du chemin (propagée telle quelle par la façade).
 */
public class KadreUikitLeaseProbe(
    public val granted: Boolean,
    public val grantedViewMatches: Boolean,
    public val grantedFailureDescription: String?,
    public val concurrentGranted: Boolean,
    public val concurrentFailureDescription: String?,
    public val concurrentRetryable: Boolean,
    public val threw: String?,
)

/**
 * Résultat du probe de bail POST-CLOSE ([KadreUikitSurfaceDriver.closedLeaseProbe]) : le ruled
 * steady outcome est `Unsupported(PlatformSurfaceAccess)` (la route du registre est retirée au
 * close — la façade ne résout plus de port) ; [outcomeDescription] embarque la forme exacte de
 * la réponse pour l'échec CI.
 */
public class KadreUikitClosedLeaseProbe(
    public val unsupportedPlatformSurfaceAccess: Boolean,
    public val outcomeDescription: String,
)

/**
 * La capture d'événements d'une surface, tenue côté Kotlin (le `Flow` exporté n'est pas
 * consommable depuis Swift sans collector) : un collecteur sur [Dispatchers.Default] compte les
 * événements publiés par type et garde les dernières descriptions bornées. Le drain côté test
 * est le pumping du run loop principal (les publications vivent sur le dispatcher main de la
 * session) — aucun sommeil nu, le pattern fixture. [stop] arrête la collecte ; après la
 * terminaison de la surface, le flow se referme seul (inscription `Closed`) et les compteurs
 * gèlent — le gel EST l'assertion « plus d'événement ».
 */
public class KadreUikitSurfaceEventCapture private constructor(surface: HostSurface) {
    private val lock = RuntimeLock()

    // Compteurs par type (index = kind de `record`) — un IntArray unique : l'écriture capturée
    // par le collecteur vise UN champ (le tableau), pas six (voir le rapport Task 5 : les
    // écritures par champs individuels dans la lambda collectrice se sont révélées non
    // persistantes dans un cas non déterministe — K/N captured-object ; le tableau, lui, a un
    // comportement observé uniforme).
    private val counts = IntArray(6)
    private var descriptions: List<String> = emptyList()
    private var collectAlive = false
    private var collectError: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            collectAlive = true
            try {
                surface.events.collect { event -> record(event) }
            } catch (t: Throwable) {
                collectError = t.toString()
            } finally {
                collectAlive = false
            }
        }
    }

    private fun record(event: SurfaceEvent) {
        val kind = when (event) {
            is SurfaceEvent.RedrawRequested -> 1
            is SurfaceEvent.MetricsChanged -> 2
            is SurfaceEvent.AppearanceChanged -> 3
            is SurfaceEvent.VisibilityChanged -> 4
            is SurfaceEvent.FocusChanged -> 5
        }
        lock.withLock {
            counts[0] += 1
            counts[kind] += 1
            descriptions = (descriptions + "$kind:" + event.toString()).takeLast(DESCRIPTION_CAPACITY)
        }
    }

    public val totalCount: Int get() = count(0)

    public val redrawCount: Int get() = count(1)

    public val metricsCount: Int get() = count(2)

    public val appearanceCount: Int get() = count(3)

    public val visibilityCount: Int get() = count(4)

    public val focusCount: Int get() = count(5)

    private fun count(index: Int): Int = lock.withLock { counts[index] }

    public val recentDescriptions: List<String> get() = lock.withLock { descriptions }

    /** Le collecteur tourne-t-il encore (faux = flow terminé ou jeté — le « pourquoi » suit). */
    public val isCollectAlive: Boolean get() = lock.withLock { collectAlive }

    /** L'exception qui a tué la collecte, s'il y en eut une (diagnostic d'échec CI). */
    public val collectErrorDescription: String? get() = lock.withLock { collectError }

    /** Arrête la collecte (idempotent) — les compteurs restent lus tels quels. */
    public fun stop() {
        scope.cancel()
    }

    internal companion object {
        internal fun start(surface: HostSurface): KadreUikitSurfaceEventCapture =
            KadreUikitSurfaceEventCapture(surface)

        private const val DESCRIPTION_CAPACITY = 16
    }
}

/**
 * Résultat du probe STRUCTUREL du touch ([KadreUikitSurfaceDriver.touchRecognitionProbe]) :
 * les faits copiés de la vue, jamais les objets. [recognizerAttached] — la vue porte bien LE
 * recognizer Kadre (vérifié par le type Kotlin côté module, jamais par un nom) ;
 * [cancelsTouchesInView] — la politique additive tracée (la vue du host garde ses touches,
 * `false` attendu) ; [recognizerOverridesTouchesBeganImp] — la preuve DISCRIMINANTE du SPIKE
 * (revue I2) : l'IMP du sélecteur de livraison `touchesBegan:withEvent:` sur LA CLASSE
 * PROPRISSAIRE de l'instance diffère de l'IMP de la classe de base — ce qui n'est VRAI que si
 * l'export `@ExportObjCClass` + `@ObjCAction` est vivant (le trampoline K/N remplace le hook
 * vide hérité). Un `respondsToSelector:` ne discriminait RIEN (TOUT recognizer y répond, la
 * base implémente les hooks) — un recognizer qui hériterait le hook vide sans l'exporter
 * ÉCHOUE ce prédicat, la régression silencieuse devient rouge. [recognizerObjCClassName] — le
 * nom Obj-C de la classe propriétaire (l'enregistrement `@ExportObjCClass` attendu) ;
 * [recognizerEnabled] — le recognizer est actif ; [totalGestureRecognizers] — le compte TOTAL
 * des recognizers de la vue (l'additivité se lit ici : le host garde les siens, Kadre
 * n'ajoute que le sien).
 */
public class KadreUikitTouchRecognitionProbe(
    public val recognizerAttached: Boolean,
    public val cancelsTouchesInView: Boolean,
    public val recognizerOverridesTouchesBeganImp: Boolean,
    public val recognizerObjCClassName: String,
    public val recognizerEnabled: Boolean,
    public val totalGestureRecognizers: Int,
)

/**
 * Résultat du probe STRUCTUREL des gestes et du survol ([KadreUikitSurfaceDriver.gestureRecognitionProbe])
 * — les faits copiés de la vue, jamais les objets : le NOM Obj-C de CHAQUE recognizer posé
 * (`class_getName` — les recognizers Kadre de Task 3 sont des recognizers STANDARD, le nom est
 * celui de la classe Apple : le test distingue par le compte et par l'ensemble exact) et la
 * politique additive de TOUS ([cancelsTouchesInViewAll] — chaque recognizer posé par Kadre porte
 * `false` : le host garde ses touches).
 */
public class KadreUikitGestureRecognitionProbe(
    public val recognizerClassNames: List<String>,
    public val cancelsTouchesInViewAll: Boolean,
)

/**
 * Résultat du probe STRUCTUREL du texte ([KadreUikitSurfaceDriver.textInputProbe], phase 6
 * Task 3) — les faits copiés du receiver découvert sous [org.graphiks.kadre.platform.uikit]
 * la vue du host, jamais les objets (le receiver [UIKitTextInputReceiver] est interne au
 * module — Swift ne peut pas le nommer, la sonde copie, même discipline que le touch) :
 *
 * - [receiverAttached] — la vue du host porte bien LE receiver Kadre (vérifié par le TYPE
 *   Kotlin côté module) ; [receiverObjCClassName] — le nom Obj-C de sa classe propriétaire
 *   (l'enregistrement `@ExportObjCClass` attendu) ;
 * - [receiverInHierarchy]/[receiverWindowed] — le champ vit dans la hiérarchie du host et une
 *   fenêtre (`isAddressable` du receiver — l'analogue D-X2 du seam élément web) ;
 *   [receiverRemovedFromSuperview] est le négatif post-close du scénario close ;
 * - [receiverFirstResponder]/[receiverAlpha] — le focus programmatique (le spike Task 1 l'a
 *   prouvé déterministe) et l'invisibilité SANS `isHidden` (alpha 0, fait Task 2) ;
 * - [fieldText]/[fieldSelectionStart]/[fieldSelectionEnd] — le texte VIVANT du champ et sa
 *   sélection en offsets UTF-16 (la vérité du write-back et des édits programmatiques) ;
 * - [fieldMarkedStart]/[fieldMarkedLength] — la plage marquée (composition IME VIVANTE) en
 *   offsets UTF-16, `-1/-1` quand le champ n'en tient PAS, `-2` quand l'offset est illisible
 *   (le champ sans document) ;
 * - [fieldUtf16Count] — le compte UTF-16 du texte du champ (la preuve graphème honnête des
 *   paires surrogates) ;
 * - [keyboardTypeRaw]/[returnKeyTypeRaw]/[secureTextEntry] — les traits de saisie LUS SUR LE
 *   CHAMP (le mapping de config Task 2 vérifié à l'exécution sur les deux familles).
 */
public class KadreUikitTextInputProbe(
    public val receiverAttached: Boolean,
    public val receiverObjCClassName: String,
    public val receiverInHierarchy: Boolean,
    public val receiverWindowed: Boolean,
    public val receiverRemovedFromSuperview: Boolean,
    public val receiverFirstResponder: Boolean,
    public val receiverAlpha: Double,
    public val fieldText: String,
    public val fieldUtf16Count: Int,
    public val fieldSelectionStart: Int,
    public val fieldSelectionEnd: Int,
    public val fieldMarkedStart: Int,
    public val fieldMarkedLength: Int,
    public val keyboardTypeRaw: Long,
    public val returnKeyTypeRaw: Long,
    public val secureTextEntry: Boolean,
)

/**
 * La capture d'événements d'UNE session texte ([KadreUikitSurfaceDriver.captureTextInputEvents])
 * — le miroir exact de [KadreUikitSurfaceEventCapture] pour le `Flow<TextInputEvent>` de la
 * session (les faits copiés en primitives : Swift ne peut pas collecter un Flow Kotlin). Le
 * drain côté test est le pumping du run loop principal — la publication des événements vit sur
 * le main, la collecte sur [Dispatchers.Default] ; aucun sommeil nu. [stop] annule la collecte
 * (idempotent) et désinscrit l'abonné (le lease du collecteur rendu) ; les compteurs restent
 * lus tels quels après l'arrêt.
 *
 * AMORÇAGE (le même problème que `primeCapture` phase 5) : le flow est FROID (aucun replay) —
 * un événement publié avant l'inscription de l'abonné est perdu. L'inscription suit le départ
 * du collecteur SANS point de suspension intermédiaire (le `registerSubscriber` est synchrone
 * dans le corps du flow) : le test attend [isCollectAlive] PUIS pompe une fenêtre de
 * quiescence avant le premier stimulus — la même rigueur que l'amorce de phase 5.
 */
public class KadreUikitTextInputEventCapture private constructor(session: TextInputSession) {
    private val lock = RuntimeLock()

    // Compteurs par type (index = kind de `record`) — un IntArray unique, la leçon du rapport
    // Task 5 (les écritures capturées par champs individuels ne sont pas fiables en K/N, le
    // tableau l'est).
    private val counts = IntArray(5)
    private var descriptions: List<String> = emptyList()
    private var collectAlive = false
    private var collectError: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            collectAlive = true
            try {
                session.events.collect { event -> record(event) }
            } catch (t: Throwable) {
                collectError = t.toString()
            } finally {
                collectAlive = false
            }
        }
    }

    private fun record(event: TextInputEvent) {
        val kind = when (event) {
            is TextInputEvent.Replace -> 1
            is TextInputEvent.SelectionChanged -> 2
            is TextInputEvent.CompositionChanged -> 3
            is TextInputEvent.Action -> 4
        }
        lock.withLock {
            counts[0] += 1
            counts[kind] += 1
            when (event) {
                is TextInputEvent.Replace -> {
                    lastReplaceRangeStart_ = event.range.startUtf16
                    lastReplaceRangeEnd_ = event.range.endExclusiveUtf16
                    lastReplaceText_ = event.text
                    lastReplaceBaseRevision_ = event.baseRevision.value
                }

                is TextInputEvent.Action -> {
                    lastActionName_ = event.action.name
                    lastActionBaseRevision_ = event.baseRevision.value
                }

                else -> Unit
            }
            descriptions = (descriptions + "$kind:" + event.toString()).takeLast(DESCRIPTION_CAPACITY)
        }
    }

    public val totalCount: Int get() = count(0)

    public val replaceCount: Int get() = count(1)

    public val selectionCount: Int get() = count(2)

    public val compositionCount: Int get() = count(3)

    public val actionCount: Int get() = count(4)

    private fun count(index: Int): Int = lock.withLock { counts[index] }

    /** Les faits du DERNIER Replace drainé (`-1`/null quand aucun). */
    public val lastReplaceRangeStart: Int get() = lock.withLock { lastReplaceRangeStart_ }

    public val lastReplaceRangeEndExclusive: Int get() = lock.withLock { lastReplaceRangeEnd_ }

    public val lastReplaceText: String? get() = lock.withLock { lastReplaceText_ }

    public val lastReplaceBaseRevision: Long get() = lock.withLock { lastReplaceBaseRevision_ }

    /** Le DERNIER nom d'action drainé + sa révision (null quand aucune). */
    public val lastActionName: String? get() = lock.withLock { lastActionName_ }

    public val lastActionBaseRevision: Long get() = lock.withLock { lastActionBaseRevision_ }

    private var lastReplaceRangeStart_: Int = -1
    private var lastReplaceRangeEnd_: Int = -1
    private var lastReplaceText_: String? = null
    private var lastReplaceBaseRevision_: Long = -1
    private var lastActionName_: String? = null
    private var lastActionBaseRevision_: Long = -1

    public val recentDescriptions: List<String> get() = lock.withLock { descriptions }

    /** Le collecteur tourne-t-il encore (faux = flow terminé ou jeté — le « pourquoi » suit). */
    public val isCollectAlive: Boolean get() = lock.withLock { collectAlive }

    /** L'exception qui a tué la collecte, s'il y en eut une (diagnostic d'échec CI). */
    public val collectErrorDescription: String? get() = lock.withLock { collectError }

    /** Arrête la collecte (idempotent) — désinscrit l'abonné, les compteurs restent lus tels quels. */
    public fun stop() {
        scope.cancel()
    }

    internal companion object {
        internal fun start(session: TextInputSession): KadreUikitTextInputEventCapture =
            KadreUikitTextInputEventCapture(session)

        private const val DESCRIPTION_CAPACITY = 16
    }
}

/**
 * La sonde de CONDUITE de surface du driver UIKit/tvOS (phase 2) — le prolongement naturel de
 * [KadreUikitProbe] (phase 0) : les chemins Kotlin-only du module (le bail natif de la
 * surface — l'extension d'accès plateforme d'`UIKitSurfaceAccess` — et le `Flow`
 * d'événements) et les compteurs internes (diagnostics d'attach, re-lectures) rendus
 * lisibles du fixture Swift. Scaffolding du driver, même posture que la sonde :
 * VOLONTAIREMENT VISIBLE dans l'en-tête framework (aucun masquage Obj-C — les suites
 * XCTest phase 2 le pilotent depuis Swift), hors de la surface promise §5, listé comme tel
 * dans l'inventaire d'audit de l'en-tête (`capabilities/uikit.md` §3) ; opt-in
 * explicite du consumer ([KadrePlatformApi]), aucune entrée dans l'API contractuelle.
 *
 * Le bail est conduit en `runBlocking` depuis le thread appelant (le principal UIKit, contrat
 * de l'attach — le port court le block en inline sur ce même thread, aucun dispatch) : le
 * pattern `runBlocking`-sur-main est celui d'[KadreIos] lui-même (`admitPrimaryWindow`).
 */
@KadrePlatformApi
@OptIn(DelicateKadreApi::class)
public object KadreUikitSurfaceDriver {

    /**
     * Le probe de bail NOMINAL + la garde de réentrance : le premier bail est admis (sa callback
     * reçoit la UIView du host, comparée par `isEqual` à la vue attendue du fixture) ; PENDANT
     * ce vol — exactement dans la callback — un second bail est tenté et doit répondre
     * `TemporarilyUnavailable(retryable = true)` sans que sa callback soit invoquée.
     */
    public fun leaseProbe(surface: HostSurface, expectedView: UIView): KadreUikitLeaseProbe {
        var granted = false
        var viewMatches = false
        var grantedFailure: String? = null
        var concurrentGranted = false
        var concurrentFailure: String? = null
        var concurrentRetryable = false
        var threw: String? = null
        try {
            when (val outer = runBlocking {
                surface.withUIKitView { view ->
                    viewMatches = view.isEqual(expectedView)
                    when (val nested = runBlocking { surface.withUIKitView { true } }) {
                        is KadreResult.Failure -> {
                            concurrentFailure = nested.reason.toString()
                            concurrentRetryable =
                                (nested.reason as? KadreFailure.TemporarilyUnavailable)?.retryable == true
                        }

                        is KadreResult.Success -> concurrentGranted = true
                    }
                    true
                }
            }) {
                is KadreResult.Success -> granted = true
                is KadreResult.Failure -> grantedFailure = outer.reason.toString()
            }
        } catch (t: Throwable) {
            threw = t.toString()
        }
        return KadreUikitLeaseProbe(
            granted = granted,
            grantedViewMatches = viewMatches,
            grantedFailureDescription = grantedFailure,
            concurrentGranted = concurrentGranted,
            concurrentFailureDescription = concurrentFailure,
            concurrentRetryable = concurrentRetryable,
            threw = threw,
        )
    }

    /**
     * Le probe de bail POST-TERMINAISON : le close a retiré la route du registre — la réponse
     * steady est `Unsupported(PlatformSurfaceAccess)` (`Closed(Surface)` n'est que l'issue de
     * course en vol, jamais observable de façon déterministe après coup).
     */
    public fun closedLeaseProbe(surface: HostSurface): KadreUikitClosedLeaseProbe {
        val outcome = runBlocking { surface.withUIKitView { true } }
        val failure = (outcome as? KadreResult.Failure)?.reason
        val unsupported = failure as? KadreFailure.Unsupported
        return KadreUikitClosedLeaseProbe(
            unsupportedPlatformSurfaceAccess = unsupported?.operation == KadreOperation.PlatformSurfaceAccess,
            outcomeDescription = outcome.toString(),
        )
    }

    /** Ouvre une capture d'événements de [surface] (le fixture la draine en pompant le run loop). */
    public fun captureEvents(surface: HostSurface): KadreUikitSurfaceEventCapture =
        KadreUikitSurfaceEventCapture.start(surface)

    /** Le nombre TOTAL de reports de l'attach — zéro attendu sur toute suite verte. */
    public val reportedFailureCount: Int
        get() = UIKitAttachDiagnostics.totalReported()

    /** Les derniers reports (journal borné, descriptions rédigées) — le « où » d'un échec CI. */
    public val recentFailureDescriptions: List<String>
        get() = UIKitAttachDiagnostics.failures().map { it.toString() }

    /** Le compteur des re-lectures d'observation réellement exécutées (le compteur du fixture). */
    public val observationCount: Long
        get() = UIKitObservationCount.total()

    /**
     * Le probe STRUCTUREL du touch (phase 5, Task 2) : l'état des recognizers de [view] lu du
     * côté Kotlin, où le recognizer Kadre est identifiable par son TYPE (interne au module —
     * Swift ne peut pas le nommer, la sonde copie les faits, même discipline que les autres
     * probes). La preuve du SPIKE ([KadreUikitTouchRecognitionProbe.recognizerOverridesTouchesBeganImp])
     * est la vérification d'exécution DISCRIMINANTE du sous-classement : l'IMP propre de la
     * classe de l'instance diffère de celui de la base pour le sélecteur de livraison.
     */
    @OptIn(ExperimentalForeignApi::class)
    public fun touchRecognitionProbe(view: UIView): KadreUikitTouchRecognitionProbe {
        val recognizers: List<*> = view.gestureRecognizers.orEmpty()
        val kadre = recognizers.firstOrNull { it is UIKitTouchRecognizer }
        val recognizer = kadre as? UIKitTouchRecognizer
        return KadreUikitTouchRecognitionProbe(
            recognizerAttached = recognizer != null,
            cancelsTouchesInView = recognizer?.cancelsTouchesInView ?: false,
            recognizerOverridesTouchesBeganImp = recognizer?.let(::overridesBaseTouchHook) == true,
            recognizerObjCClassName = recognizer?.let(::objcClassNameOf).orEmpty(),
            recognizerEnabled = recognizer?.isEnabled() ?: false,
            totalGestureRecognizers = recognizers.size,
        )
    }

    /**
     * Le probe STRUCTUREL des gestes et du survol (phase 5, Task 3) : le NOM Obj-C de chaque
     * recognizer posé sur [view] (`class_getName` — les recognizers de Task 3 sont des
     * recognizers STANDARD, le test lit l'ensemble exact des noms et la politique additive de
     * tous). Le compte par famille est la vérité du klib : iOS = tap/pan/pinch/rotation +
     * survol ; tvOS = tap/pan seuls (pinch/rotation absents du klib tvOS, survol
     * `API_UNAVAILABLE` — cf. le KDoc de `UIKitGestureBindings`).
     */
    @OptIn(ExperimentalForeignApi::class)
    public fun gestureRecognitionProbe(view: UIView): KadreUikitGestureRecognitionProbe {
        val recognizers: List<*> = view.gestureRecognizers.orEmpty()
        val names = recognizers.mapNotNull { recognizer ->
            recognizer?.let(::objcClassNameOf)?.takeIf { it.isNotEmpty() }
        }
        val cancellations = recognizers.mapNotNull { recognizer ->
            (recognizer as? UIGestureRecognizer)?.cancelsTouchesInView
        }
        return KadreUikitGestureRecognitionProbe(
            recognizerClassNames = names,
            cancelsTouchesInViewAll = cancellations.isNotEmpty() && cancellations.all { !it },
        )
    }

    /**
     * Le prédicat discriminant de la revue I2 : l'IMP du sélecteur de livraison sur la classe
     * PROPRE de l'instance (`object_getClass` — pas la hiérarchie) diffère de l'IMP de la
     * classe de base `UIGestureRecognizer`. VRAI uniquement quand l'export Kotlin
     * (`@ExportObjCClass` + `@ObjCAction`) a posé son trampoline sur la sous-classe ; FAUX
     * pour tout recognizer qui hériterait le hook vide de la base — exactement la régression
     * silencieuse qu'un `respondsToSelector:` (toujours vrai, la base implémente les hooks)
     * ne voyait pas.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun overridesBaseTouchHook(recognizer: UIKitTouchRecognizer): Boolean {
        val selector = NSSelectorFromString(TOUCH_DELIVERY_SELECTOR)
        val ownClass = object_getClass(recognizer) ?: return false
        val baseClass = objc_getClass("UIGestureRecognizer") as? ObjCClass ?: return false
        val ownImp = class_getMethodImplementation(ownClass, selector) ?: return false
        val baseImp = class_getMethodImplementation(baseClass, selector) ?: return false
        return ownImp.rawValue != baseImp.rawValue
    }

    /** Le nom Obj-C de la classe propriétaire de [instance] (`class_getName`). */
    @OptIn(ExperimentalForeignApi::class)
    private fun objcClassNameOf(instance: Any): String =
        object_getClass(instance)?.let { cls -> class_getName(cls)?.toKString() }.orEmpty()

    /** Les installations de recognizer touch réellement exécutées (le compteur du fixture). */
    public val touchInstallCount: Long
        get() = UIKitTouchCount.totalInstalls()

    /** Les retraits de recognizer touch réellement exécutés (le compteur du fixture). */
    public val touchTeardownCount: Long
        get() = UIKitTouchCount.totalTeardowns()

    /**
     * Les livraisons d'`UITouch` réellement reçues par le recognizer (le compteur du SPIKE en
     * vraies touches : zéro attendu sur toute suite automatisée — aucun touch n'est
     * synthétisable in-process honnêtement ; son incrémentation sur le charter manuel est la
     * preuve Task 4 de la livraison réelle).
     */
    public val touchEventCount: Long
        get() = UIKitTouchCount.totalEvents()

    /** Les stimuli `TouchChanged` réellement publiés par le binding. */
    public val touchStimulusCount: Long
        get() = UIKitTouchCount.totalStimuli()

    /**
     * Les annonces `InputObservationChanged` réellement publiées par le binding (l'installation
     * ET la révocation — deux publications par cycle de session attendues). La RÉCEPTION par le
     * runtime (capabilities d'input) reste la preuve contractuelle Task 4.
     */
    public val touchAnnouncementCount: Long
        get() = UIKitTouchCount.totalAnnouncements()

    // MARK: - Compteurs clavier GCKeyboard (phase 5, Task 3) — le proxy du driver, même posture.

    /** Les installations du binding clavier réellement exécutées (le compteur du fixture). */
    public val keyboardInstallCount: Long
        get() = UIKitKeyboardCount.totalInstalls()

    /** Les retraits du binding clavier réellement exécutés (le compteur du fixture). */
    public val keyboardTeardownCount: Long
        get() = UIKitKeyboardCount.totalTeardowns()

    /**
     * Les connexions de source clavier réellement ACCEPTÉES (le handler posé sur le clavier
     * coalesqué — zéro attendu sur toute suite automatisée : aucun clavier physique ne se
     * connecte à un simulateur ; son incrémentation sur le charter manuel est la preuve Task 4).
     */
    public val keyboardConnectCount: Long
        get() = UIKitKeyboardCount.totalConnects()

    /**
     * Les livraisons de touches réellement reçues du `keyChangedHandler` (zéro attendu sur une
     * suite automatisée — cf. [keyboardConnectCount] ; l'incrémentation sur le charter manuel
     * Task 4 est la preuve de la vraie touche).
     */
    public val keyboardEventCount: Long
        get() = UIKitKeyboardCount.totalEvents()

    /** Les stimuli `KeyChanged` réellement publiés par le binding clavier. */
    public val keyboardStimulusCount: Long
        get() = UIKitKeyboardCount.totalStimuli()

    /** Les annonces `InputObservationChanged` déclenchées par le binding clavier. */
    public val keyboardAnnouncementCount: Long
        get() = UIKitKeyboardCount.totalAnnouncements()

    // MARK: - Compteurs gestes + survol (phase 5, Task 3) — même posture.

    /** Les recognizers de geste réellement posés sur la vue (un compteur par recognizer). */
    public val gestureInstallCount: Long
        get() = UIKitGestureCount.totalInstalls()

    /** Les recognizers de geste réellement retirés de la vue. */
    public val gestureTeardownCount: Long
        get() = UIKitGestureCount.totalTeardowns()

    /** Les installations du recognizer de survol (iOS ; zéro côté tvOS par construction). */
    public val hoverInstallCount: Long
        get() = UIKitGestureCount.totalHoverInstalls()

    /** Les retraits du recognizer de survol (iOS ; zéro côté tvOS par construction). */
    public val hoverTeardownCount: Long
        get() = UIKitGestureCount.totalHoverTeardowns()

    /**
     * Les stimuli `Gesture`/`Pointer*` réellement publiés par les bindings de gestes (zéro
     * attendu sur une suite automatisée — aucun geste n'est synthétisable in-process
     * honnêtement ; le charter manuel Task 4 incrémentera).
     */
    public val gestureStimulusCount: Long
        get() = UIKitGestureCount.totalStimuli()

    /** Les annonces `InputObservationChanged` déclenchées par le binding gestes/survol. */
    public val gestureAnnouncementCount: Long
        get() = UIKitGestureCount.totalAnnouncements()

    // MARK: - Compteurs drop (phase 7, Task 3) — le proxy du driver, même posture.

    /**
     * Les interactions `UIDropInteraction` réellement posées sur la vue (le compteur du
     * fixture ; zéro côté tvOS par construction — le binding n'y existe pas).
     */
    public val dropInstallCount: Long
        get() = UIKitDropCount.totalInstalls()

    /** Les interactions drop réellement retirées de la vue. */
    public val dropTeardownCount: Long
        get() = UIKitDropCount.totalTeardowns()

    /**
     * Les livraisons que le delegate drop VIVANT reçoit (`canHandle`, entrée, mouvement,
     * sortie, perform, conclude, fin de session — zéro sur toute suite automatisée SANS
     * scénarios drop delegate-driven : tvOS, où le binding n'existe pas, ou les suites des
     * autres familles d'entrée, où aucun drag n'est livré ; les scénarios drop
     * delegate-driven du driver les incrémentent — l'install structurel posé par l'attach,
     * puis chaque hook livré au vrai delegate par les fausses de session conformes).
     */
    public val dropEventCount: Long
        get() = UIKitDropCount.totalEvents()

    // MARK: - Compteurs + probes texte (phase 6, Task 3) — le proxy du driver, même posture.

    /** Les opens texte réellement installés (un owner vivant par open admis — le CAS du port). */
    public val textInstallCount: Long
        get() = UIKitTextInputCount.totalInstalls()

    /** Les retraits d'owner texte réellement exécutés (close de session, shutdown du port). */
    public val textTeardownCount: Long
        get() = UIKitTextInputCount.totalTeardowns()

    /**
     * Les observations PUBLIÉES par le port (Replace/Action — passées la garde de close, admises
     * par le runtime ou non). La publication port n'est PAS la livraison session : l'événement
     * drainé ([captureTextInputEvents]) reste la preuve bout en bout.
     */
    public val textObservationCount: Long
        get() = UIKitTextInputCount.totalObservations()

    /** Les gains de focus du receiver (`textFieldDidBeginEditing` — comptés tant que l'owner vit). */
    public val textFocusGainedCount: Long
        get() = UIKitTextInputCount.totalFocusGained()

    /** Les pertes de focus du receiver (`textFieldDidEndEditing` — comptés tant que l'owner vit). */
    public val textFocusLostCount: Long
        get() = UIKitTextInputCount.totalFocusLost()

    /**
     * Le probe STRUCTUREL du texte : le receiver Kadre découvert sous [surfaceView] (par le
     * TYPE Kotlin côté module — Swift ne peut pas nommer la classe interne), ses faits copiés
     * ([KadreUikitTextInputProbe]). Aucun receiver posé → [KadreUikitTextInputProbe.
     * receiverAttached] faux et les faits de champ aux valeurs « aucun » (texte vide, offsets
     * -1, brutes -1, sécurisé faux).
     */
    @OptIn(ExperimentalForeignApi::class)
    public fun textInputProbe(surfaceView: UIView): KadreUikitTextInputProbe {
        val receiver = receiverIn(surfaceView)
        if (receiver == null) {
            return KadreUikitTextInputProbe(
                receiverAttached = false,
                receiverObjCClassName = "",
                receiverInHierarchy = false,
                receiverWindowed = false,
                receiverRemovedFromSuperview = false,
                receiverFirstResponder = false,
                receiverAlpha = 0.0,
                fieldText = "",
                fieldUtf16Count = 0,
                fieldSelectionStart = -1,
                fieldSelectionEnd = -1,
                fieldMarkedStart = -1,
                fieldMarkedLength = -1,
                keyboardTypeRaw = -1,
                returnKeyTypeRaw = -1,
                secureTextEntry = false,
            )
        }
        val marked = markedRangeUtf16(receiver)
        val selection = receiver.currentSelectionUtf16()
        val text = receiver.text ?: ""
        return KadreUikitTextInputProbe(
            receiverAttached = true,
            receiverObjCClassName = objcClassNameOf(receiver),
            receiverInHierarchy = receiver.superview != null,
            receiverWindowed = receiver.window != null,
            receiverRemovedFromSuperview = receiver.superview == null,
            receiverFirstResponder = receiver.isFirstResponder,
            receiverAlpha = receiver.alpha,
            fieldText = text,
            fieldUtf16Count = text.length,
            fieldSelectionStart = selection?.startUtf16 ?: -1,
            fieldSelectionEnd = selection?.endExclusiveUtf16 ?: -1,
            fieldMarkedStart = marked?.first ?: -1,
            fieldMarkedLength = marked?.second ?: -1,
            keyboardTypeRaw = receiver.keyboardType,
            returnKeyTypeRaw = receiver.returnKeyType.value,
            secureTextEntry = receiver.secureTextEntry,
        )
    }

    /**
     * L'open texte conduit côté Kotlin ([runBlocking] sur le thread appelant — le pattern du
     * bail ; l'appelant du fixture est le main) : `surface.input.openTextInput(config)` avec la
     * config reconstruite paramètre à paramètre (les enums publics passent la frontière, la
     * config est assemblée ICI pour ne pas exposer la fabrique). Le [KadreResult] brut revient
     * — Swift y lit la session (`Success`) ou la failure typée (`AlreadyInUse`…).
     */
    public fun openTextInputProbe(
        surface: HostSurface,
        purpose: TextInputPurpose,
        action: TextInputAction,
        capitalization: TextCapitalization,
        autocorrect: Boolean,
        multiline: Boolean,
        surroundingText: String,
        selectionStartUtf16: Int,
        selectionEndExclusiveUtf16: Int,
        documentRevision: Long,
    ): KadreResult<TextInputSession> {
        val config = TextInputConfig(
            purpose = purpose,
            action = action,
            capitalization = capitalization,
            autocorrect = autocorrect,
            multiline = multiline,
            surroundingText = surroundingText,
            selection = TextRange(selectionStartUtf16, selectionEndExclusiveUtf16),
            documentRevision = TextDocumentRevision(documentRevision),
        )
        return runBlocking { surface.input.openTextInput(config) }
    }

    /**
     * Le write-back conduit côté Kotlin ([runBlocking], suspend `session.updateSurroundingText`)
     * — la DIRECTION Kotlin → champ de la preuve driver : le document du runtime est écrit dans
     * le champ sous la garde d'écho, l'ombre reconciliée, la révision du runtime avancée. Le
     * [KadreResult] brut revient (`StaleRevision` lisible par Swift).
     */
    public fun updateSurroundingTextProbe(
        session: TextInputSession,
        text: String,
        selectionStartUtf16: Int,
        selectionEndExclusiveUtf16: Int,
        documentRevision: Long,
    ): KadreResult<Unit> = runBlocking {
        session.updateSurroundingText(
            text,
            TextRange(selectionStartUtf16, selectionEndExclusiveUtf16),
            TextDocumentRevision(documentRevision),
        )
    }

    /** Ouvre une capture d'événements de la session texte (le fixture la draine en pompant). */
    public fun captureTextInputEvents(session: TextInputSession): KadreUikitTextInputEventCapture =
        KadreUikitTextInputEventCapture.start(session)

    /**
     * Le stimulus UIKeyInput programmatique `insertText:` sur le receiver découvert — un appel
     * RESPONDER réel (le spike Task 1 l'a prouvé vivant : le champ applique, `.editingChanged`
     * part, `shouldChange` NE part JAMAIS). Faux si aucun receiver n'est posé.
     */
    public fun receiverInsertText(surfaceView: UIView, text: String): Boolean {
        val receiver = receiverIn(surfaceView) ?: return false
        receiver.insertText(text)
        return true
    }

    /** Le stimulus UIKeyInput programmatique `deleteBackward:` (même canal que [receiverInsertText]). */
    public fun receiverDeleteBackward(surfaceView: UIView): Boolean {
        val receiver = receiverIn(surfaceView) ?: return false
        receiver.deleteBackward()
        return true
    }

    /**
     * Le stimulus programmatique `setMarkedText:selectedRange:` (la composition IME du spike —
     * un vrai état marqué, fait Task 1 (a)). [selectedLocation]/[selectedLength] sont la plage
     * sélectionnée DANS le marqué posé, en offsets UTF-16.
     */
    @OptIn(ExperimentalForeignApi::class)
    public fun receiverSetMarkedText(
        surfaceView: UIView,
        markedText: String,
        selectedLocation: Int,
        selectedLength: Int,
    ): Boolean {
        val receiver = receiverIn(surfaceView) ?: return false
        val selected: CValue<NSRange> = cValue {
            location = selectedLocation.toULong()
            length = selectedLength.toULong()
        }
        receiver.setMarkedText(markedText, selectedRange = selected)
        return true
    }

    /** Le stimulus programmatique `unmarkText` (le commit IME du spike — fait Task 1 (a)). */
    public fun receiverUnmarkText(surfaceView: UIView): Boolean {
        val receiver = receiverIn(surfaceView) ?: return false
        receiver.unmarkText()
        return true
    }

    /** Le stimulus de focus programmatique `resignFirstResponder` (le spike l'a prouvé déterministe). */
    public fun receiverResignFocus(surfaceView: UIView): Boolean {
        val receiver = receiverIn(surfaceView) ?: return false
        return receiver.resignFirstResponder()
    }

    /** Le stimulus de focus programmatique `becomeFirstResponder` (le spike l'a prouvé déterministe). */
    public fun receiverRefocus(surfaceView: UIView): Boolean {
        val receiver = receiverIn(surfaceView) ?: return false
        return receiver.becomeFirstResponder()
    }

    /**
     * La CONDUITE du hook delegate `shouldChangeCharactersInRange` du receiver (le port EST le
     * delegate — `delegate = self`) : un appel UNITAIRE du routage propre de l'adaptateur, le
     * canal utilisateur SEUL (jamais déclenché par les appels programmatiques, fait Task 1 (d)).
     * C'est la route honnête du driver vers l'observation Replace : le range passé est celui
     * qu'UIKit nomme pour l'édition correspondante (frontière de graphème prouvée au spike),
     * la mutation du champ est appliquée par le stimulus programmatique qui suit — exactement
     * l'ordre UIKit (shouldChange AVANT la modification). Documenté au rapport Task 3 : appel
     * unitaire de code propre, pas un événement système synthétisé.
     */
    @OptIn(ExperimentalForeignApi::class)
    public fun receiverDriveShouldChange(
        surfaceView: UIView,
        location: Long,
        length: Long,
        replacement: String,
    ): Boolean {
        val receiver = receiverIn(surfaceView) ?: return false
        val range: CValue<NSRange> = cValue {
            this.location = location.toULong()
            this.length = length.toULong()
        }
        return receiver.textField(
            receiver,
            shouldChangeCharactersInRange = range,
            replacementString = replacement,
        )
    }

    /**
     * La CONDUITE du hook delegate `textFieldShouldReturn` du receiver (le port EST le
     * delegate) : un appel unitaire du routage propre de l'adaptateur — l'action de la config
     * part en observation. Documenté au rapport Task 3, même posture que
     * [receiverDriveShouldChange].
     */
    public fun receiverDriveShouldReturn(surfaceView: UIView): Boolean {
        val receiver = receiverIn(surfaceView) ?: return false
        return receiver.textFieldShouldReturn(receiver)
    }

    /** Le receiver Kadre sous [surfaceView] — par le TYPE Kotlin (interne au module), jamais par un nom. */
    private fun receiverIn(surfaceView: UIView): UIKitTextInputReceiver? =
        surfaceView.subviews.orEmpty().firstOrNull { it is UIKitTextInputReceiver } as? UIKitTextInputReceiver

    /**
     * La plage marquée du champ en offsets UTF-16 `(start, length)` — `null` quand le champ
     * n'en tient pas ; `(-2, -2)` quand le marqué existe mais que ses offsets sont illisibles
     * (un champ sans document).
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun markedRangeUtf16(receiver: UIKitTextInputReceiver): Pair<Int, Int>? {
        val marked = receiver.markedTextRange ?: return null
        val begin = receiver.beginningOfDocument ?: return Pair(-2, -2)
        val start = receiver.offsetFromPosition(begin, toPosition = marked.start)
        val end = receiver.offsetFromPosition(begin, toPosition = marked.end)
        if (start < 0L || end < start) return Pair(-2, -2)
        return Pair(start.toInt(), (end - start).toInt())
    }
}
