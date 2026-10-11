package org.graphiks.kadre.platform.uikit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatformApi
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.internal.uikit.UIKitObservationCount
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.SurfaceEvent
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
 * La sonde de CONDUITE de surface du driver UIKit/tvOS (phase 2) — le prolongement naturel de
 * [KadreUikitProbe] (phase 0) : les chemins Kotlin-only du module (`withUIKitView`, le `Flow`
 * d'événements) et les compteurs internes (diagnostics d'attach, re-lectures) rendus
 * lisibles du fixture Swift. Scaffolding du driver, même posture que la sonde : opt-in
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
}
