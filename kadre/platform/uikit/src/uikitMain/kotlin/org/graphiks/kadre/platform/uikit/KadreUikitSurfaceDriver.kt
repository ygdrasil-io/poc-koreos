package org.graphiks.kadre.platform.uikit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.cinterop.ExperimentalForeignApi
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatformApi
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.internal.uikit.UIKitObservationCount
import org.graphiks.kadre.internal.uikit.UIKitTouchCount
import org.graphiks.kadre.internal.uikit.UIKitTouchRecognizer
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.SurfaceEvent
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIView

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
 * `false` attendu) ; [recognizerRespondsToTouchesBegan] — la preuve de SUBCLASSE du SPIKE :
 * l'instance répond au sélecteur exact `touchesBegan:withEvent:` qu'UIKit dispatche (la
 * livraison passe par ce sélecteur) ; [recognizerEnabled] — le recognizer est actif ;
 * [totalGestureRecognizers] — le compte TOTAL des recognizers de la vue (l'additivité se
 * lit ici : le host garde les siens, Kadre n'ajoute que le sien).
 */
public class KadreUikitTouchRecognitionProbe(
    public val recognizerAttached: Boolean,
    public val cancelsTouchesInView: Boolean,
    public val recognizerRespondsToTouchesBegan: Boolean,
    public val recognizerEnabled: Boolean,
    public val totalGestureRecognizers: Int,
)

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
     * probes). La preuve du SPIKE ([KadreUikitTouchRecognitionProbe.recognizerRespondsToTouchesBegan])
     * est une vérification D'EXÉCUTION du sous-classement : l'instance répond au sélecteur
     * exact que la livraison d'UIKit dispatche.
     */
    @OptIn(ExperimentalForeignApi::class)
    public fun touchRecognitionProbe(view: UIView): KadreUikitTouchRecognitionProbe {
        val recognizers: List<*> = view.gestureRecognizers.orEmpty()
        val kadre = recognizers.firstOrNull { it is UIKitTouchRecognizer }
        val recognizer = kadre as? UIKitTouchRecognizer
        return KadreUikitTouchRecognitionProbe(
            recognizerAttached = recognizer != null,
            cancelsTouchesInView = recognizer?.cancelsTouchesInView ?: false,
            recognizerRespondsToTouchesBegan =
                recognizer?.respondsToSelector(NSSelectorFromString("touchesBegan:withEvent:")) == true,
            recognizerEnabled = recognizer?.isEnabled() ?: false,
            totalGestureRecognizers = recognizers.size,
        )
    }

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
}
