package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.capture.CaptureCapabilities
import org.graphiks.kadre.capture.CaptureCursorMode
import org.graphiks.kadre.capture.CapturePermissionScope
import org.graphiks.kadre.capture.CapturePermissionState
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureTargetConstraints
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.PermissionState
import org.graphiks.kadre.internal.runtime.CapturePort
import org.graphiks.kadre.internal.runtime.CapturePortReservation
import org.graphiks.kadre.internal.runtime.CapturePortSnapshot
import org.graphiks.kadre.internal.runtime.CapturePortSources
import org.graphiks.kadre.internal.runtime.CapturePortTarget
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.surface.SurfaceId
import platform.ReplayKit.RPScreenRecorder

/**
 * Le seam SDK du port capture UIKit/tvOS (phase 9, Task 2) — la lecture ReplayKit de
 * disponibilité en production, une source factice pilotable en test hôte (le pattern jeton du
 * module, miroir [UIKitDisplaySource]) : le port ne nomme JAMAIS un type ReplayKit — c'est la
 * traduction de ce seam en formes port qui est prouvée dans `uikitTest`, pas la lecture SDK
 * elle-même (charterée sur device).
 *
 * Task 2 ne porte que le fait contrôle-plan : la disponibilité du recorder. Les formes
 * `startCaptureWithHandler`/`stopCaptureWithHandler` (spike § 1 — `isAvailable` est une
 * FONCTION klib, `sharedRecorder` la propriété de classe) rejoindront ce seam aux Tasks 3-4
 * quand les réservations vivront ; le port reste SDK-FREE par construction.
 */
internal interface UIKitCaptureSource : AutoCloseable {
    /**
     * La lecture LIVE de `RPScreenRecorder.isAvailable` (la FONCTION klib — pas une propriété)
     * au moment de l'appel. Le pin du spike (décision 2) : la valeur appartient au runtime de
     * l'hôte — JAMAIS « Simulator ⇒ indisponible » codé en dur ; un Simulator qui répond `false`
     * (runtimes anciens) reste honnête par la même lecture.
     */
    public fun recorderAvailable(): Boolean
}

/**
 * La réalisation production du seam : le recorder PARTAGÉ du process (`RPScreenRecorder
 * .sharedRecorder()`, la forme klib épinglée par le spike). `isAvailable` n'alloue rien et ne
 * présente aucune interface — la lecture est un fait d'état, jamais un geste utilisateur ; le
 * recorder étant un singleton process-wide, `close` n'a RIEN à relâcher (le port le ferme
 * exactement une fois par propriété, la réalisation absorbe).
 */
internal class UIKitReplayKitCaptureSource : UIKitCaptureSource {
    override fun recorderAvailable(): Boolean = RPScreenRecorder.sharedRecorder().isAvailable()

    override fun close() = Unit
}

/**
 * Le registre des surfaces hôtes attachées de CETTE session — le fait contre lequel la route
 * `Surface` du port résout un id. L'argument vue est `Any` (stub-safe en test hôte : le port ne
 * voit jamais une vraie UIView — pattern jeton ; la Task 4 y lira le vrai view).
 *
 * Miroir exact d'[AndroidHostCaptureSurfaceRegistry] : l'instance vit dans la même couture que
 * le port (la couture d'attach construit le registre ET le port ensemble, le registre ne survit
 * pas à la session), donc enregistrer-une-fois-par-instance EST enregistrer-une-fois-par-port.
 * Le `check` refuse de réenregistrer le même id avec une AUTRE vue ; réinscrire la MÊME vue
 * sous le même id est absorbé.
 */
internal interface UIKitCaptureSurfaceRegistry {
    public fun register(id: SurfaceId, view: Any)

    public fun unregister(id: SurfaceId)

    public fun resolve(id: SurfaceId): Any?
}

internal class UIKitHostCaptureSurfaceRegistry : UIKitCaptureSurfaceRegistry {
    private val lock = RuntimeLock()
    private val surfaces = HashMap<SurfaceId, Any>()

    override fun register(id: SurfaceId, view: Any) {
        lock.withLock {
            val existing = surfaces[id]
            check(existing == null || existing === view) {
                "the host capture surface is registered once per capture port"
            }
            surfaces[id] = view
        }
    }

    override fun unregister(id: SurfaceId) {
        lock.withLock { surfaces.remove(id) }
    }

    override fun resolve(id: SurfaceId): Any? = lock.withLock { surfaces[id] }
}

/**
 * Le port capture UIKit/tvOS : le plan de contrôle ReplayKit, sans AUCUNE interaction
 * utilisateur — le miroir structurel d'[AndroidCapturePort] et du port web, avec les rulings
 * du contrôleur pour table de vérité.
 *
 * **La disponibilité est LUE, jamais devinée** (pin du spike, décision 2) : chaque snapshot
 * relit `recorderAvailable()` au moment de sa construction — le snapshot initial une fois à la
 * construction, chaque `requestPermission(Screen)` à sa demande. Un recorder indisponible
 * descend SEULEMENT la disponibilité de l'écran ([FeatureAvailability.Unavailable] sur des
 * contraintes qui restent annoncées) et la permission écran, avec l'échec honnête du fait du
 * seam ([KadreFailure.TemporarilyUnavailable], `retryable = true` — `isAvailable` ne porte
 * AUCUN pourquoi, la propriété VIVANTE peut revenir ; aucune raison n'est fabriquée, JAMAIS
 * « Simulator » codé en dur). La table STRUCTURELLE ne bouge pas avec la disponibilité : les
 * refus de fenêtre, d'énumération et de picker sont des faits de plateforme (aucune primitive
 * n'énumère des fenêtres capturables, les broadcast extensions vivent hors process — les
 * frames n'atteignent jamais l'app), pas des états de disponibilité.
 *
 * **Permissions issues de la disponibilité** (ruling 1/5) : l'écran utilisable sans dialogue
 * est `Granted` (RIEN ne se dresse devant la capture — ReplayKit n'a pas d'API de consentement
 * d'app) ; l'écran indisponible est `Unavailable(même échec)` ; la fenêtre n'a aucune
 * permission obtenable (`Unavailable(Unsupported(CapturePermission))` — l'état qui ne deviendra
 * jamais accordé, miroir de la refuse `requestPermission(Window)`).
 *
 * **`requestPermission` ne prompt JAMAIS** (ruling 5) : le scope Screen rend le snapshot de
 * disponibilité LUE à cet instant (répétable, idempotent, vivant), le scope Window est refusé
 * `Unsupported(CapturePermission)` avant tout fait de seam. **La cible Source est refusée
 * AVANT tout seam** (ruling 6, précédent AND-009). **Les réservations HostChoice/Surface sont
 * les refus de squelette** (`Unsupported(CaptureOpen)`) — les bras ReplayKit (Task 3) et pompe
 * surface (Task 4) les remplaceront ; la route Surface résout d'abord l'id dans le registre de
 * CETTE session ([UIKitCaptureSurfaceRegistry], nourri par la couture d'attach), un id
 * inconnu répond `InvalidRequest("request.target")` (le miroir Android).
 *
 * **Pas d'inventaire, pas de picker** (ruling 4) : `sourceEnumeration` est
 * `Unsupported(CaptureRefreshSources)`, `hostPicker` est [FeatureAvailability.Unsupported],
 * l'inventaire est `Unavailable(même échec)` — JAMAIS `HostPickerOnly`.
 *
 * L'observer est UNIQUE et remplacé (le dernier install gagne, le handle périmé ne
 * désenregistre jamais l'install vivante) avec la livraison sync-once du pattern maison : un
 * install sur un port DÉJÀ passé son snapshot initial délivre la réponse courante UNE FOIS,
 * un port encore à son snapshot initial ne délivre rien (le runtime tient déjà exactement
 * cela de sa lecture de construction). Le close est idempotent, ferme le seam possédé
 * exactement une fois, et barre toute voie derrière lui en `Closed(Host)`.
 *
 * THREADING — la lecture `isAvailable` est un fait d'état ReplayKit sans exigence de file
 * documentée (aucune UI présentée) ; Task 2 la lit sur le fil de l'appelant. La Task 3, qui
 * branchera `startCaptureWithHandler` (callbacks sur la file principale), posera là sa propre
 * règle de confinement.
 *
 * @param source le seam ReplayKit que ce port lit ; possédé par le port, fermé avec lui.
 * @param surfaces le registre des surfaces hôtes de CETTE session — nourri par la couture
 *   d'attach après l'admission différée de la fenêtre primaire (l'id n'existe qu'alors).
 */
internal class UIKitCapturePort(
    private val source: UIKitCaptureSource,
    private val surfaces: UIKitCaptureSurfaceRegistry = UIKitHostCaptureSurfaceRegistry(),
) : CapturePort {
    private val lock = RuntimeLock()
    private var closed = false
    private var observer: ((KadreResult<CapturePortSnapshot>) -> Unit)? = null

    /** Le snapshot de construction — relu UNE fois, gelé comme référence initiale du runtime. */
    override val initialSnapshot: CapturePortSnapshot = snapshotOf()

    /** Le snapshot derrière lequel le port se tient ; tout fait ultérieur le remplace entier. */
    private var current: CapturePortSnapshot = initialSnapshot

    /**
     * Enregistre la surface hôte de CETTE session — la seule surface que ce port capture
     * honnêtement, appelé par la couture d'attach APRÈS l'admission différée (le miroir exact
     * d'`AndroidCapturePort.registerHostCaptureSurface` : l'admission du runtime transfère
     * l'id sans le vérifier, donc cet enregistrement est le seul fait contre lequel la route
     * `Surface` résout ; un port dont la session n'a rien enregistré refuse toute réservation
     * de surface au lieu de deviner).
     */
    public fun registerHostCaptureSurface(id: SurfaceId, view: Any) {
        surfaces.register(id, view)
    }

    /** Retire exactement la surface enregistrée sous [id] (le retrait suit la vie de la surface). */
    public fun unregisterHostCaptureSurface(id: SurfaceId) {
        surfaces.unregister(id)
    }

    /**
     * La demande de permission qui ne présente JAMAIS d'interface (ruling 5) : le scope Screen
     * relit la disponibilité et rend le snapshot complet (répétable, idempotent — deux
     * demandes de suite relisent le MÊME état) ; le scope Window est refusé STRUCTURELLEMENT,
     * avant tout fait de seam.
     */
    override suspend fun requestPermission(scope: CapturePermissionScope): KadreResult<CapturePortSnapshot> {
        if (isClosed()) return closedFailure()
        return when (scope) {
            CapturePermissionScope.Screen -> {
                val snapshot = snapshotOf()
                accept(snapshot)
                KadreResult.Success(snapshot)
            }

            CapturePermissionScope.Window ->
                KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CapturePermission))
        }
    }

    /** Aucune primitive n'énumère des sources capturables : le refus structurel (ruling 4). */
    override suspend fun refreshSources(): KadreResult<CapturePortSnapshot> {
        if (isClosed()) return closedFailure()
        return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureRefreshSources))
    }

    /**
     * La garde de cible, dans l'ordre du miroir Android : la cible Source est refusée
     * STRUCTURELLEMENT avant tout fait de seam (ruling 6 — aucune source énumérée n'existe,
     * un `CapturePortSourceKey` ne peut rien désigner) ; la cible Surface résout d'abord l'id
     * dans le registre de CETTE session puis retombe sur le refus de squelette (la pompe
     * surface vit en Task 4) ; le choix hôte retombe sur le refus de squelette (la réservation
     * ReplayKit vit en Task 3).
     */
    override suspend fun reserve(
        target: CapturePortTarget,
        request: CaptureRequest,
    ): KadreResult<CapturePortReservation> {
        if (isClosed()) return closedFailure()
        return when (target) {
            is CapturePortTarget.Source ->
                KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))

            is CapturePortTarget.Surface -> surfaceReservation(target.id)

            CapturePortTarget.HostChoice ->
                KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
        }
    }

    /**
     * Installe l'observer session-runtime (le dernier install REMPLACE le précédent — le
     * handle périmé reste appelable sans effet et ne désenregistre jamais l'install vivante)
     * et lui délivre la réponse courante EXACTEMENT UNE FOIS si le port a déjà quitté son
     * snapshot initial ; un port encore à son snapshot initial ne délivre rien — le runtime
     * tient déjà exactement cela de sa lecture de construction.
     */
    override fun installObserver(observer: (KadreResult<CapturePortSnapshot>) -> Unit): AutoCloseable {
        val movedPastInitial = lock.withLock {
            check(!closed) { "the uikit capture port is closed" }
            this.observer = observer
            if (current !== initialSnapshot) current else null
        }
        movedPastInitial?.let { observer(KadreResult.Success(it)) }
        var withdrawn = false
        return AutoCloseable {
            lock.withLock {
                if (withdrawn || closed) return@withLock
                withdrawn = true
                if (this.observer === observer) this.observer = null
            }
        }
    }

    /**
     * Ferme le port : idempotent, le seam possédé est fermé EXACTEMENT UNE FOIS (la propriété
     * du port — sans compter sur l'idempotence du seam), l'observer est oublié (plus aucune
     * livraison après le close) et toute voie ultérieure répond `Closed(Host)`. Le registre
     * meurt avec le port : la session entière le possède, aucun autre détenteur n'existe —
     * rien à déposer ici (le miroir Android).
     */
    override fun close() {
        val firstClose = lock.withLock {
            if (closed) {
                false
            } else {
                closed = true
                observer = null
                true
            }
        }
        if (firstClose) source.close()
    }

    /** Remplace le snapshot courant et le remet à l'observateur installé, s'il existe et si ouvert. */
    private fun accept(snapshot: CapturePortSnapshot) {
        val installed = lock.withLock {
            if (closed) return
            current = snapshot
            observer
        }
        installed?.invoke(KadreResult.Success(snapshot))
    }

    /**
     * Un snapshot complet relu VIVANT : la permission écran et la disponibilité de la
     * capability écran portent le MÊME fait du seam, la table structurelle ne bouge pas.
     */
    private fun snapshotOf(): CapturePortSnapshot {
        val available = source.recorderAvailable()
        return CapturePortSnapshot(
            permissions = CapturePermissionState(
                screen = when (available) {
                    true -> PermissionState.Granted
                    false -> PermissionState.Unavailable(RECORDER_UNAVAILABLE)
                },
                window = PermissionState.Unavailable(KadreFailure.Unsupported(KadreOperation.CapturePermission)),
            ),
            capabilities = captureCapabilities(available),
            sources = SOURCE_INVENTORY,
        )
    }

    /**
     * La table de vérité des capabilities, rulings 1-4 : l'écran porte les contraintes
     * ReplayKit documentées (sortie 32BGRA, curseur système composé, affichage entier) avec la
     * disponibilité LUE ; la fenêtre est le refus structurel ; la surface porte les contraintes
     * de la voie renderer mesurée (Rgba8, le propre buffer de la vue ne compose jamais de
     * pointeur, région non promise dans ce squelette) ; énumération et picker sont les refus
     * structurels — l'inventaire est `Unavailable(même échec)`, JAMAIS `HostPickerOnly`.
     */
    private fun captureCapabilities(recorderAvailable: Boolean): CaptureCapabilities = CaptureCapabilities(
        screen = Capability.Supported(
            CaptureTargetConstraints(
                formats = setOf(PixelFormat.Bgra8),
                cursorModes = setOf(CaptureCursorMode.EmbeddedWhenAvailable),
                region = FeatureAvailability.Unsupported,
            ),
            when (recorderAvailable) {
                true -> FeatureAvailability.Available
                false -> FeatureAvailability.Unavailable(RECORDER_UNAVAILABLE)
            },
        ),
        window = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
        surface = Capability.Supported(
            CaptureTargetConstraints(
                formats = setOf(PixelFormat.Rgba8),
                cursorModes = setOf(CaptureCursorMode.Hidden),
                region = FeatureAvailability.Unsupported,
            ),
            FeatureAvailability.Available,
        ),
        sourceEnumeration = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureRefreshSources)),
        hostPicker = FeatureAvailability.Unsupported,
    )

    /**
     * La réservation de la cible Surface : l'id doit être la surface hôte enregistrée par
     * CETTE session (l'admission du runtime ne vérifie que la capability et transfère l'id tel
     * quel) — sinon `InvalidRequest("request.target")`, la forme du refus de cible
     * irrésoluble (miroir Android). L'id résolu atteint le refus de squelette : la pompe
     * surface de la Task 4 remplacera ce refus par la réservation réelle.
     */
    private fun surfaceReservation(id: SurfaceId): KadreResult<CapturePortReservation> {
        lock.withLock { surfaces.resolve(id) }
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("request.target"))
        return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
    }

    private fun isClosed(): Boolean = lock.withLock { closed }

    private fun closedFailure(): KadreResult.Failure =
        KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host))

    private companion object {
        /**
         * L'échec honnête du fait « le recorder se déclare indisponible » : `isAvailable` ne
         * porte AUCUN pourquoi — la forme ne fabrique aucune raison (pas de « Simulator », pas
         * de cause inventée) et ne prétend pas à une panne dure (`retryable = true` : la
         * propriété vivante peut revenir).
         */
        private val RECORDER_UNAVAILABLE = KadreFailure.TemporarilyUnavailable(retryable = true)

        /** L'inventaire indisponible — l'échec de la MÊME opération que l'énumération (ruling 4). */
        private val SOURCE_INVENTORY =
            CapturePortSources.Unavailable(KadreFailure.Unsupported(KadreOperation.CaptureRefreshSources))
    }
}
