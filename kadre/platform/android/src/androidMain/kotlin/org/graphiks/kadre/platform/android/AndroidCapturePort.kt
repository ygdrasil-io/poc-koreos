package org.graphiks.kadre.platform.android

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.graphiks.kadre.capture.AlphaMode
import org.graphiks.kadre.capture.CaptureCadence
import org.graphiks.kadre.capture.CaptureCapabilities
import org.graphiks.kadre.capture.CaptureConfiguration
import org.graphiks.kadre.capture.CaptureConfigurationRevision
import org.graphiks.kadre.capture.CaptureCursorMode
import org.graphiks.kadre.capture.CaptureOrientation
import org.graphiks.kadre.capture.CaptureOutcome
import org.graphiks.kadre.capture.CapturePermissionScope
import org.graphiks.kadre.capture.CapturePermissionState
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSourceKind
import org.graphiks.kadre.capture.CaptureStopReason
import org.graphiks.kadre.capture.CaptureTargetConstraints
import org.graphiks.kadre.capture.ColorEncoding
import org.graphiks.kadre.capture.ColorPrimaries
import org.graphiks.kadre.capture.ColorRange
import org.graphiks.kadre.capture.HdrMetadata
import org.graphiks.kadre.capture.MatrixCoefficients
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.capture.PixelPlaneLayout
import org.graphiks.kadre.capture.TransferFunction
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.InteractionFailureReason
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.KadrePermission
import org.graphiks.kadre.input.PermissionState
import org.graphiks.kadre.internal.runtime.CapturePort
import org.graphiks.kadre.internal.runtime.CapturePortFrame
import org.graphiks.kadre.internal.runtime.CapturePortPlane
import org.graphiks.kadre.internal.runtime.CapturePortReservation
import org.graphiks.kadre.internal.runtime.CapturePortSnapshot
import org.graphiks.kadre.internal.runtime.CapturePortSource
import org.graphiks.kadre.internal.runtime.CapturePortSources
import org.graphiks.kadre.internal.runtime.CapturePortSourceKey
import org.graphiks.kadre.internal.runtime.CapturePortStream
import org.graphiks.kadre.internal.runtime.CapturePortStreamListener
import org.graphiks.kadre.internal.runtime.CapturePortStreamStart
import org.graphiks.kadre.internal.runtime.CapturePortTarget
import org.graphiks.kadre.internal.runtime.CapturePortTermination
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.SurfaceId
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.nanoseconds

/** Le domaine des refus du flux de consentement (le miroir du `capture-permission` web). */
private const val PERMISSION_DOMAIN = "capture-permission"

/** Le domaine de la pompe de frames — chaque refus de lecture/copie de l'ImageReader. */
private const val STREAM_DOMAIN = "capture-stream"

/** Le namespace des réservations HostChoice — le système a consenti à UN affichage entier. */
private const val HOST_CHOICE_SOURCE_NAMESPACE = "android-host-choice"

/** `Activity.RESULT_OK` — le literal, le stub android.jar du test hôte n'est jamais nommé. */
private const val ACTIVITY_RESULT_OK = -1

/**
 * La couture du bras Surface : la fabrique PixelCopy derrière un id résolu par le registre.
 * Le défaut est la réalisation de production ([androidPixelCopyReservation] — le
 * `PixelCopy.request` réel du SurfaceView attaché, le bras Task 3) ; les tests hôtes
 * injectent leurs fabriques (le stub android.jar lance à la construction, l'argument `Any`
 * est le même parti pris que les seams du module).
 */
internal fun interface AndroidPixelCopyStreamFactory {
    public fun create(surfaceView: Any, request: CaptureRequest): KadreResult<CapturePortReservation>
}

/**
 * Le seam SDK du jeton MediaProjection — la consommation du consentement UNE FOIS par
 * demande. `createPending` est `MediaProjectionManager.getMediaProjection(resultCode, data)`
 * suivi de l'enregistrement du callback de révocation : le grant devient UN pending pour
 * CETTE session, jamais stocké au-delà (le jeton est single-use — la réutilisation d'un
 * grant stocké est un échec SDK, le port ne le tente jamais). L'appel vit APRÈS le signal
 * FGS-ready du `start` (l'ordre API 34+ : dialogue → startForeground → getMediaProjection,
 * l'item I-1 de la review Task 2) — jamais au moment du consentement.
 */
internal interface AndroidCaptureProjectionSource : AutoCloseable {
    /** Consomme le grant en un pending — la réalisation réelle enregistre aussi le callback. */
    public fun createPending(resultCode: Int, data: Any?): AndroidCapturePendingProjection

    override public fun close()
}

/**
 * Le MediaProjection consommé du grant, vivant dans la pompe du `start` qui l'a créé.
 * La pompe le garde jusqu'au terminal ; `close` (le `stop()` SDK) le libère sur tout
 * chemin qui ne démarre jamais — jamais de capture vivante derrière un port fermé ou un
 * grant périmé.
 */
internal interface AndroidCapturePendingProjection : AutoCloseable {
    /**
     * Le fait de révocation — `MediaProjection.Callback.onStop` : la permission a été retirée
     * (par l'utilisateur ou le système), le jeton est mort quoi qu'il arrive d'autre.
     */
    public val revoked: Boolean

    /** Pose le gestionnaire de révocation ; appelé immédiatement si déjà révoqué. */
    public fun onRevoked(handler: (() -> Unit)?)

    /** `MediaProjection.createVirtualDisplay` — l'affichage miroir qui nourrit la surface. */
    public fun createVirtualDisplay(name: String, width: Int, height: Int, dpi: Int, surface: Any): Any

    /** Libère exactement le display virtuel que [createVirtualDisplay] a rendu. */
    public fun releaseVirtualDisplay(display: Any)

    public fun stop()

    override public fun close()
}

/** Le seam SDK de l'ImageReader — RGBA_8888, le pool borné au budget de session. */
internal interface AndroidCaptureImageReaderSource : AutoCloseable {
    public fun createReader(width: Int, height: Int, maxImages: Int): AndroidCaptureImageReader

    override public fun close()
}

/** Un ImageReader posé : sa surface nourrit le display virtuel, ses images sont coalescées. */
internal interface AndroidCaptureImageReader : AutoCloseable {
    public val surface: Any

    /** Le signal SDK « une image est disponible » — le port coalesce par acquireLatest. */
    public fun setOnImageAvailable(listener: () -> Unit)

    /** La DERNIÈRE image non acquise (`acquireLatestImage`) — null quand rien n'attend. */
    public fun acquireLatest(): AndroidCaptureImage?

    override public fun close()
}

/** Les faits bruts d'un plan d'image SDK — les strides honests, lus avant tout octet. */
internal data class AndroidCaptureImagePlane(
    public val rowStride: Int,
    public val pixelStride: Int,
)

/** Une image acquise — les dimensions et le stride d'abord, les octets sur demande. */
internal interface AndroidCaptureImage : AutoCloseable {
    public val width: Int
    public val height: Int
    public val timestampNanos: Long
    public val planes: List<AndroidCaptureImagePlane>

    /** Copie les [byteCount] premiers octets du plan — le port a vérifié le budget AVANT. */
    public fun readPlane(index: Int, byteCount: Int): ByteArray

    override public fun close()
}

/** Les faits d'affichage de la session — la taille du buffer ImageReader et sa densité. */
internal data class AndroidCaptureDisplayFacts(
    public val size: PhysicalSize,
    public val densityDpi: Int,
)

/**
 * Le port de capture Android — miroir de WebCapturePort : capabilities sondées UNE FOIS et
 * gelées à la construction, sources HostPickerOnly (MediaProjection n'énumère rien avant le
 * consentement), CaptureTarget.Source refusé avant tout seam (le precedent WEB-ROADMAP
 * :495-530), Surface résolu via le registre des surfaces hôtes attachées, et depuis la
 * Task 2 le bras HostChoice complet : le consentement par le registre DIRECT
 * ([AndroidCaptureConsentSource] — la correction de l'anti-pattern kadre-old), le service
 * foreground `mediaProjection` Kadre ([AndroidCaptureForeground] — le ruling P8-0) et la
 * pompe ImageReader/`VirtualDisplay` à strides honests (le plumbing kadre-old :43-75 porté).
 *
 * **Le consentement vit à `requestPermission`** (l'unique chemin de dialogue, le miroir
 * exact du web) : un jeton par demande (`"kadre-capture-<n>"`, frais par DEMANDE — un
 * `AtomicLong` process-wide), enregistré sur le registre direct SANS LifecycleOwner —
 * légal après RESUMED — puis le dialogue `createScreenCaptureIntent()`. `RESULT_OK` +
 * données non nulles → **le grant est stocké BRUT (resultCode + data), PAS consommé** :
 * sur l'API 34+, `getMediaProjection` exige le service foreground `mediaProjection`
 * démarré (SecurityException sinon) — l'ordre officiel de la plateforme est dialogue →
 * `startForeground(type = mediaProjection)` → `getMediaProjection` (l'item I-1 de la
 * review Task 2), la consommation unique vit donc au `start` du flux, juste après le
 * signal FGS-ready. Un port fermé pendant le dialogue fait repartir la demande
 * `Closed(Host)` IMMÉDIATEMENT (jamais suspendue derrière un port fermé, jamais un
 * `Success` postérieur au close — l'item I-2) ; refus (`RESULT_CANCELED`/sans données) →
 * `Success(Denied(canRequestAgain = true))` — JAMAIS une failure (CONTRACTS :64) ; un port
 * fermé → `Closed(Host)` d'abord (web :130). Une demande pendant qu'un dialogue attend, un
 * grant pèse ou une session vit → `InteractionRequired(Consumed)`, jamais le grant stocké
 * ni un second dialogue empilé.
 *
 * **`reserve(HostChoice)` ne lance jamais de dialogue** : il prend le grant BRUT en une
 * [CapturePortReservation] dont `start` (a) démarre le FGS et attend `startForeground(type
 * = mediaProjection)`, (a') re-vérifie le port ouvert PUIS consomme le grant en UN pending
 * MediaProjection — `getMediaProjection`, le SEUL appel, jamais plus tôt —, (b) pose la
 * révocation `MediaProjection.Callback.onStop` → `Stopped(PermissionRevoked)`, (c) crée
 * l'ImageReader RGBA_8888 (pool borné au budget) puis le `VirtualDisplay`, (d) lit la
 * PREMIÈRE image pour dériver la configuration complète (révision 0) AVANT tout callback
 * listener — la discipline du web Pump :111-196. Sans grant (jamais consenti, ou déjà
 * consommé) → `PermissionDenied(CaptureScreen)`, un refus honnête qui ne re-prompt
 * jamais — le launcher ne part que de `requestPermission`.
 *
 * **Les frames copient les strides honests** : `rowStride` est le device-aligné du SDK, le
 * `byteCount` du layout est `(height-1)*rowStride + width*pixelStride` (la dernière ligne
 * est serrée), `maxFrameBytes` est vérifié AVANT toute copie (l'invariant never-copy du
 * web), chaque Image est fermée exactement une fois, et le terminal (`Stopped(Requested)`
 * de l'arrêt propre, `Stopped(PermissionRevoked)` de la révocation, `Failed(PlatformFailure)`
 * d'un refus de lecture) libère display → reader → projection → FGS exactement une fois —
 * l'ordre de release kadre-old :92-99, le FGS arrêté au terminal comme il fut démarré avant
 * le display.
 *
 * **Les capabilities sont la forme exacte du web (:100, :387-450), la sonde est présente et
 * jamais devinée.** `screen` et `window` sont le MÊME `Supported` — un seul consentement
 * MediaProjection gouverne ce que le picker système offre, la fenêtre le reflète mot pour mot
 * (la limite documentée du web, la même ici) : les frames RGBA_8888 d'un ImageReader, le
 * pointeur composé par le système quand un device existe (`EmbeddedWhenAvailable` —
 * MediaProjection ne commande pas le pointeur, promettre `Hidden` promettrait un effacement
 * impossible), la région non annoncée du tout (l'affichage entier est capté, la forme de refus
 * AppKit). `surface` est le PixelCopy du propre buffer de la SurfaceView : `Supported` à
 * l'API 26+ — le pointeur système n'y est jamais composé (`Hidden`, le miroir exact du canvas
 * web) — et refusé champ par champ en dessous (`Unsupported(CaptureOpen)`, la garde P4 du
 * pointerCapture ; `PixelCopy.request(SurfaceView, ...)` n'existe pas avant 26).
 * `sourceEnumeration` et `hostPicker` reprennent la forme web sans condition : l'inventaire
 * EST le picker, la capability d'énumération le dit quoi qu'il arrive d'aucune autre primitive.
 *
 * **Les sources sont toujours [CapturePortSources.HostPickerOnly]** : aucun inventaire Android
 * n'existe avant le consentement, donc `refreshSources` est le no-op honnête du web — le même
 * objet snapshot, jamais un appel de plus, JAMAIS un launcher (aucune lecture d'état ne
 * re-prompt : le test `tokenConsumedOnceNoPromptOnReadback` épinglé).
 *
 * Le port est session-scoped : les DEUX branches d'attach enregistrent leur surface hôte
 * (le miroir `registerPrimarySurface` du web), le runtime installe exactement un observateur
 * et ferme le port avec les composants de la session. Les seams SDK sont injectés (null par
 * défaut : un port sans coutures répond les refus honnêtes des opérations correspondantes
 * sans jamais toucher le SDK — les tests hôtes passent leurs fakes, l'attach câble la
 * réalisation de production).
 */
internal class AndroidCapturePort(
    private val apiLevel: Int,
    private val surfaces: AndroidCaptureSurfaceRegistry,
    private val consent: AndroidCaptureConsentSource? = null,
    private val projections: AndroidCaptureProjectionSource? = null,
    private val imageReaders: AndroidCaptureImageReaderSource? = null,
    private val foreground: AndroidCaptureForeground? = null,
    private val displayFacts: AndroidCaptureDisplayFacts = AndroidCaptureDisplayFacts(PhysicalSize(1, 1), 160),
    private val pixelCopyStreamFactory: AndroidPixelCopyStreamFactory =
        AndroidPixelCopyStreamFactory { surfaceView, request -> androidPixelCopyReservation(surfaceView, request) },
    private val loopContext: CoroutineContext = Dispatchers.Default,
) : CapturePort {
    private val lock = RuntimeLock()
    private var closed = false
    private var observer: ((KadreResult<CapturePortSnapshot>) -> Unit)? = null
    private var nextReservationKeyValue = 0L

    /** Le grant BRUT du consentement (resultCode + data, PAS consommé), en attente de son unique réservation. */
    private var pendingGrant: AndroidCapturePendingGrant? = null

    /** Le dialogue en vol — le launcher et la continuation qui porte la demande. */
    private var awaiting: AwaitingConsent? = null

    /** Le garde d'entrée du consentement — referme la course entre le gate et l'attente. */
    private var consentInFlight = false

    /** Une réservation HostChoice vit — un second `open` ne re-consomme rien. */
    private var liveReservation = false

    /** La vérité des capabilities, sondée une fois ici et gelée pour la vie du port. */
    private val probedCapabilities: CaptureCapabilities = probeCapabilities()

    override val initialSnapshot: CapturePortSnapshot = snapshotFor()

    /** Le snapshot derrière lequel le port se tient ; tout fait ultérieur le remplace entier. */
    private var current: CapturePortSnapshot = initialSnapshot

    /**
     * Enregistre la surface hôte de CETTE session — la seule surface que ce port capture
     * honnêtement — comme `registerPrimarySurface` du web, appelé dans les DEUX branches
     * d'attach. L'admission du runtime transfère l'id sans le vérifier, donc cet enregistrement
     * est le seul fait contre lequel la route `Surface` résout ; un port dont la session n'a
     * rien enregistré refuse toute réservation de surface au lieu de deviner.
     */
    public fun registerHostCaptureSurface(id: SurfaceId, surfaceView: Any) {
        surfaces.register(id, surfaceView)
    }

    /** Retire exactement la surface enregistrée sous [id] (le retrait suit la vie de la surface). */
    public fun unregisterHostCaptureSurface(id: SurfaceId) {
        surfaces.unregister(id)
    }

    /**
     * Le consentement explicite — l'UNIQUE chemin du dialogue système. Les refus de
     * l'utilisateur sont des succès stateful (`Denied(canRequestAgain = true)`, CONTRACTS
     * :64) ; les échecs du chemin SDK sont les rows `Platform` du domaine consentement ; un
     * port fermé répond `Closed(Host)` avant tout fait (le miroir web :130, l'item de
     * review m1).
     */
    override suspend fun requestPermission(scope: CapturePermissionScope): KadreResult<CapturePortSnapshot> {
        if (isClosed()) return closedFailure()
        val consent = this.consent
            ?: return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CapturePermission))
        val projections = this.projections
            ?: return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CapturePermission))
        // Un dialogue à la fois, jamais un second prompt pendant qu'un grant pèse ou qu'une
        // session vit : la demande rejetée est une `Interaction` (la row CONTRACTS :196), pas
        // le grant stocké re-servi ni un second dialogue empilé.
        val busy = lock.withLock {
            if (consentInFlight || awaiting != null || pendingGrant != null || liveReservation) {
                true
            } else {
                consentInFlight = true
                false
            }
        }
        if (busy) return KadreResult.Failure(KadreFailure.InteractionRequired(InteractionFailureReason.Consumed))
        try {
            val token = nextConsentToken()
            val intent = try {
                consent.createScreenCaptureIntent()
            } catch (refused: Throwable) {
                return KadreResult.Failure(platformFailure(PERMISSION_DOMAIN, "consent-intent-refused", refused))
            }
            val launcher = try {
                // owner = null : LE registre direct — la forme trois-arguments ne valide aucun
                // état lifecycle, l'attach tardif resumed est légal (l'anti-pattern kadre-old
                // épinglé par `lateRegistryRegistrationIsLegal`).
                consent.register(token, owner = null, onResult = ::onConsentResult)
            } catch (refused: Throwable) {
                return KadreResult.Failure(platformFailure(PERMISSION_DOMAIN, "consent-register-refused", refused))
            }
            val outcome = try {
                suspendCancellableCoroutine<ConsentOutcome> { continuation ->
                    lock.withLock { awaiting = AwaitingConsent(launcher, continuation) }
                    // L'annulation de la demande pendant le dialogue : le launcher part avec
                    // elle, le résultat tardif n'a plus d'attente et est absorbé.
                    continuation.invokeOnCancellation {
                        val stale = lock.withLock {
                            if (awaiting?.continuation === continuation) {
                                awaiting = null
                                launcher
                            } else {
                                null
                            }
                        }
                        stale?.close()
                    }
                    try {
                        launcher.launch(intent)
                    } catch (refused: Throwable) {
                        lock.withLock { if (awaiting?.continuation === continuation) awaiting = null }
                        continuation.resumeWith(Result.failure(AndroidCaptureConsentRefused(refused)))
                    }
                }
            } catch (refused: AndroidCaptureConsentRefused) {
                launcher.close()
                return KadreResult.Failure(platformFailure(PERMISSION_DOMAIN, "consent-launch-refused", refused.reason))
            } catch (closed: AndroidCaptureConsentClosed) {
                // Le port a fermé pendant le dialogue (l'item I-2) : le close a déjà
                // désenregistré le launcher et déposé tout état — la demande repart fermée.
                return closedFailure()
            }
            // Le launcher est consommé avec le jeton : `onConsentResult` l'a désenregistré —
            // la clé n'existe plus, un second enregistrement en recrée une fraîche.
            return when (outcome.resultCode == ACTIVITY_RESULT_OK && outcome.data != null) {
                true -> {
                    // Le grant est stocké BRUT, JAMAIS consommé ici : `getMediaProjection`
                    // exige le FGS démarré sur l'API 34+ (l'ordre officiel dialogue →
                    // startForeground → getMediaProjection) — la consommation unique vit au
                    // `start`, après le signal FGS-ready (l'item I-1). Le stockage re-vérifie
                    // le port : jamais un état qui survit à un close ni un `Success` post-close.
                    val stored = lock.withLock {
                        if (closed) {
                            null
                        } else {
                            pendingGrant = AndroidCapturePendingGrant(token, outcome.resultCode, outcome.data)
                            true
                        }
                    }
                    if (stored == null) return closedFailure()
                    val snapshot = snapshotFor(PermissionState.Granted)
                    accept(snapshot)
                    KadreResult.Success(snapshot)
                }

                false -> {
                    // RESULT_CANCELED ou des données absentes : le refus utilisateur est un
                    // état, JAMAIS une failure (CONTRACTS :64), et l'utilisateur peut redemander.
                    if (isClosed()) return closedFailure()
                    val snapshot = snapshotFor(PermissionState.Denied(canRequestAgain = true))
                    accept(snapshot)
                    KadreResult.Success(snapshot)
                }
            }
        } finally {
            lock.withLock { consentInFlight = false }
        }
    }

    override suspend fun refreshSources(): KadreResult<CapturePortSnapshot> {
        if (isClosed()) return closedFailure()
        // Le no-op honnête (miroir web :161-166) : le même objet snapshot, permissions et
        // capabilities gelées incluses, et pas un seam de plus — MediaProjection n'énumère
        // rien de ce qui n'a pas été consenti, et AUCUNE lecture d'état ne re-prompt.
        return KadreResult.Success(lock.withLock { current })
    }

    override suspend fun reserve(
        target: CapturePortTarget,
        request: CaptureRequest,
    ): KadreResult<CapturePortReservation> {
        if (isClosed()) return closedFailure()
        return when (target) {
            // Le miroir web :176 — aucun inventaire Android n'existe avant le consentement, donc
            // une cible source est refusée structurellement, ICI, avant tout seam, sans rien
            // réserver et sans rien demander.
            is CapturePortTarget.Source -> KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))

            // La surface hôte attachée de CETTE session : l'id résolu dans le registre que les
            // deux branches d'attach nourrissent, puis le bras PixelCopy de la Task 3.
            is CapturePortTarget.Surface -> surfaceReservation(target.id, request)

            // Le choix hôte : le consentement PENDANT devient réservation — jamais un dialogue.
            CapturePortTarget.HostChoice -> hostChoiceReservation(request)
        }
    }

    /**
     * L'admission et la réservation de la cible `Surface`, dans l'ordre :
     *
     * 1. **L'id doit être la surface hôte enregistrée par cette session.** L'admission du
     *    runtime ne vérifie que la capability et transfère l'id tel quel, donc cette vérification
     *    est celle du port, et sa forme reflète le refus de cible irrésoluble du runtime —
     *    `InvalidRequest("request.target")`, la forme que [RuntimeCaptureManager.sourceAdmission]
     *    répond pour un id que son inventaire ne nomme pas et que le web répond (:216-219). Le
     *    refus est structurel : pas une interaction de registre de plus que la résolution.
     * 2. **La garde d'API en recul.** Quand la capability gelée a déjà dit `Unsupported(CaptureOpen)`
     *    (sous l'API 26, le miroir du backstop canvas web), une réservation qui atteindrait le
     *    port par tout autre chemin répond la MÊME failure — le verdict constant, jamais un autre.
     * 3. **La fabrique PixelCopy** (Task 3) reçoit la surfaceView attachée et la requête ; le
     *    défaut du squelette est le stub jetant des Tasks 2-3.
     */
    private fun surfaceReservation(
        id: SurfaceId,
        request: CaptureRequest,
    ): KadreResult<CapturePortReservation> {
        val surfaceView = lock.withLock { surfaces.resolve(id) }
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("request.target"))
        if (apiLevel < 26) {
            return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
        }
        return pixelCopyStreamFactory.create(surfaceView, request)
    }

    /**
     * La réservation HostChoice — SANS aucune interaction utilisateur : le consentement a déjà
     * vécu à `requestPermission`, le grant BRUT y fut stocké (la consommation
     * `getMediaProjection` vit au `start`, après le signal FGS — l'ordre API 34+, l'item
     * I-1). La région est refusée avant tout fait (MediaProjection capte l'affichage entier,
     * la forme web :278-281) ; sans grant (jamais consenti, déjà consommé, ou port fermé
     * entre-temps) le refus est `PermissionDenied(CaptureScreen)` — un état honnête, JAMAIS
     * un second dialogue ni le grant stocké re-servi.
     */
    private fun hostChoiceReservation(request: CaptureRequest): KadreResult<CapturePortReservation> {
        if (request.region != null) {
            return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
        }
        val projections = this.projections
        val readers = this.imageReaders
        val foreground = this.foreground
        if (projections == null || readers == null || foreground == null) {
            return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
        }
        val granted = lock.withLock {
            val current = pendingGrant
            if (current != null) pendingGrant = null
            current
        } ?: return KadreResult.Failure(KadreFailure.PermissionDenied(KadrePermission.CaptureScreen))
        lock.withLock { liveReservation = true }
        val keyValue = lock.withLock {
            val value = nextReservationKeyValue
            nextReservationKeyValue += 1L
            value
        }
        return KadreResult.Success(
            AndroidHostChoiceReservation(
                source = CapturePortSource(
                    key = CapturePortSourceKey(HOST_CHOICE_SOURCE_NAMESPACE, keyValue),
                    kind = CaptureSourceKind.Display,
                    name = null,
                    size = null,
                ),
                grant = granted,
                projections = projections,
                request = request,
                foreground = foreground,
                readerSource = readers,
                facts = displayFacts,
                loopContext = loopContext,
                closedGate = ::isClosed,
                onEnded = { lock.withLock { liveReservation = false } },
            ),
        )
    }

    override fun installObserver(observer: (KadreResult<CapturePortSnapshot>) -> Unit): AutoCloseable {
        val movedPastInitial = lock.withLock {
            check(!closed) { "the android capture port is closed" }
            check(this.observer == null) { "the android capture port observer is already installed" }
            this.observer = observer
            // Une installation sur un port déjà passé son snapshot initial (le consentement de
            // la Task 2 en produit) délivre cette réponse courante une fois — l'observateur voit
            // la vérité dès qu'il existe. Un port encore à son snapshot initial ne délivre rien :
            // le runtime tient déjà exactement cela de sa lecture de construction.
            if (current !== initialSnapshot) current else null
        }
        movedPastInitial?.let { observer(KadreResult.Success(it)) }
        var withdrawn = false
        return AutoCloseable {
            lock.withLock {
                if (withdrawn) return@withLock
                withdrawn = true
                if (this.observer === observer) this.observer = null
            }
        }
    }

    override fun close() {
        val awaitingNow = lock.withLock {
            if (closed) {
                null
            } else {
                closed = true
                observer = null
                val inFlight = awaiting
                awaiting = null
                // Le grant BRUT est déposé : rien de vivant à fermer — la consommation
                // `getMediaProjection` n'a jamais eu lieu sans un `start` (l'item I-1).
                pendingGrant = null
                inFlight
            }
        }
        // Le close pendant un dialogue en vol (l'item I-2) : la demande qui attend repart
        // MAINTENANT en `Closed(Host)` — jamais suspendue derrière un port fermé, jamais un
        // `Success` postérieur au close. Le launcher est désenregistré ICI (le résultat
        // tardif du dialogue n'a plus d'attente et sera absorbé par `onConsentResult`).
        awaitingNow?.let { inFlight ->
            inFlight.launcher.close()
            inFlight.continuation.resumeWith(Result.failure(AndroidCaptureConsentClosed()))
        }
        // Le registre meurt avec le port : la session entière le possède, aucun autre détenteur
        // n'existe — rien à déposer ici (le web n'a pas de déregistration non plus).
    }

    /** Le résultat du dialogue système, routé vers la demande qui attend encore. */
    private fun onConsentResult(resultCode: Int, data: Any?) {
        val delivery = lock.withLock {
            val current = awaiting ?: return
            awaiting = null
            current.launcher.close()
            current
        }
        delivery.continuation.resume(ConsentOutcome(resultCode, data))
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

    /** Un snapshot complet — la permission reflétée sur les DEUX scopes (le miroir web). */
    private fun snapshotFor(): CapturePortSnapshot = snapshotFor(PermissionState.NotDetermined)

    private fun snapshotFor(permission: PermissionState): CapturePortSnapshot = CapturePortSnapshot(
        permissions = CapturePermissionState(permission, permission),
        capabilities = probedCapabilities,
        sources = CapturePortSources.HostPickerOnly,
    )

    private fun isClosed(): Boolean = lock.withLock { closed }

    private fun closedFailure(): KadreResult.Failure =
        KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host))

    /** Le refus de chemin SDK en `PlatformFailure` du domaine donné — la classe porte dans le code. */
    private fun platformFailure(domain: String, code: String, cause: Throwable): KadreFailure =
        KadreFailure.PlatformFailure(KadrePlatform.Android, domain, "$code:${cause::class.java.simpleName}")

    /**
     * La table de vérité des capabilities, dans la forme exacte du web (:387-450). Les
     * constraints d'écran portent sur le chemin MediaProjection (l'ImageReader RGBA_8888, la
     * région refusée — l'affichage entier), la fenêtre est le miroir mot pour mot de l'écran,
     * la surface porte les constraints du PixelCopy du buffer propre de la vue, et l'énumération
     * comme le picker sont dans la forme web inconditionnelle.
     */
    private fun probeCapabilities(): CaptureCapabilities {
        val screenAndWindow = Capability.Supported(
            CaptureTargetConstraints(
                formats = setOf(PixelFormat.Rgba8),
                cursorModes = setOf(CaptureCursorMode.EmbeddedWhenAvailable),
                // La forme de refus AppKit, miroir web : MediaProjection capte l'affichage entier,
                // la région sur une cible écran/fenêtre n'est pas annoncée du tout.
                region = FeatureAvailability.Unsupported,
            ),
            FeatureAvailability.Available,
        )
        val surface = when {
            // La garde P4 (pointerCapture, champ par champ) : `PixelCopy.request(SurfaceView, ...)`
            // est API 26+, le module descend à 24 — le champ est refusé en dessous, zéro appel.
            apiLevel < 26 -> Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen))

            else -> Capability.Supported(
                CaptureTargetConstraints(
                    formats = setOf(PixelFormat.Rgba8),
                    // Le propre buffer de la SurfaceView ne compose jamais le pointeur système —
                    // le miroir exact du canvas web et son `Hidden`.
                    cursorModes = setOf(CaptureCursorMode.Hidden),
                    // Aucun bras de recadrage n'est promis dans ce squelette : la Task 3 décidera.
                    region = FeatureAvailability.Unsupported,
                ),
                FeatureAvailability.Available,
            )
        }
        return CaptureCapabilities(
            screen = screenAndWindow,
            // La limite documentée du web : un seul consentement gouverne ce que le picker
            // système offre, la fenêtre est la même capability, mot pour mot.
            window = screenAndWindow,
            surface = surface,
            // La forme web, inconditionnelle : l'inventaire est le picker, la capability
            // d'énumération le dit quoi qu'il arrive d'aucune autre primitive.
            sourceEnumeration = Capability.Supported(Unit, FeatureAvailability.Available),
            // Le mécanisme de consentement (`createScreenCaptureIntent`) existe à tout minSdk
            // du module : le picker est disponible, sans condition.
            hostPicker = FeatureAvailability.Available,
        )
    }

    /** Le dialogue en vol — la demande annulable qui attend le résultat de l'activité. */
    private class AwaitingConsent(
        val launcher: AndroidCaptureConsentLauncher,
        val continuation: kotlinx.coroutines.CancellableContinuation<ConsentOutcome>,
    )

    private data class ConsentOutcome(
        val resultCode: Int,
        val data: Any?,
    )

    private class AndroidCaptureConsentRefused(
        val reason: Throwable,
    ) : Exception(reason)

    /** Le signal du close pendant un dialogue en vol — la demande repart `Closed(Host)`. */
    private class AndroidCaptureConsentClosed : Exception("the capture port closed during the consent dialog")

    companion object {
        /** La séquence process-wide des jetons de session — chaque demande en mint un frais. */
        private val consentTokens = AtomicLong(0L)

        /** Le prochain jeton de session — la clé du registre ET l'extra du FGS. */
        private fun nextConsentToken(): String = "kadre-capture-${consentTokens.incrementAndGet()}"
    }
}

/**
 * La réservation HostChoice : le grant BRUT du consentement devient une
 * [CapturePortReservation]. Réserver n'a produit AUCUNE frame et AUCUNE consommation SDK —
 * [start] démarre le FGS (l'ordre P8-0), re-vérifie le port ouvert, consomme le grant en UN
 * pending MediaProjection (`getMediaProjection`, APRÈS le signal FGS-ready — l'ordre API
 * 34+, l'item I-1), pose la révocation, crée reader + display, lit la première image pour
 * la configuration, puis remet la pompe au collector unique du runtime. [close] libère
 * exactement une fois, à n'importe quelle profondeur atteinte.
 */
private class AndroidHostChoiceReservation(
    override val source: CapturePortSource,
    private val grant: AndroidCapturePendingGrant,
    private val projections: AndroidCaptureProjectionSource,
    private val request: CaptureRequest,
    private val foreground: AndroidCaptureForeground,
    private val readerSource: AndroidCaptureImageReaderSource,
    private val facts: AndroidCaptureDisplayFacts,
    private val loopContext: CoroutineContext,
    private val closedGate: () -> Boolean,
    private val onEnded: () -> Unit,
) : CapturePortReservation {
    private val lock = RuntimeLock()
    private var closed = false
    private var started = false
    private var pump: AndroidCapturePump? = null

    override suspend fun start(
        listener: CapturePortStreamListener,
        maxFrameBytes: Long,
    ): KadreResult<CapturePortStreamStart> {
        val admission = lock.withLock {
            when {
                closed -> KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.CaptureSession))
                started -> KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.CaptureCollector))
                else -> {
                    started = true
                    null
                }
            }
        }
        if (admission != null) return admission
        val ownedPump = AndroidCapturePump(
            grant = grant,
            projections = projections,
            request = request,
            foreground = foreground,
            readerSource = readerSource,
            facts = facts,
            loopContext = loopContext,
            closedGate = closedGate,
        )
        lock.withLock { pump = ownedPump }
        return ownedPump.start(listener, maxFrameBytes)
    }

    override fun close() {
        val first = lock.withLock {
            if (closed) false else {
                closed = true
                true
            }
        }
        if (!first) return
        val ownedPump = lock.withLock { pump }
        if (ownedPump != null) {
            ownedPump.close()
        } else {
            // Jamais démarré : AUCUNE projection n'existe (la consommation vit au start),
            // donc rien de SDK à libérer — le FGS, jamais acquis par cette réservation,
            // n'est pas non plus arrêté (le contrôle de production ne relâche que ce
            // qu'il a acquis, l'item I-3).
            foreground.stop()
        }
        onEnded()
    }
}

/**
 * Le grant BRUT du consentement, en attente de son unique réservation — le jeton voyage au
 * FGS, la consommation (`getMediaProjection`) vit au `start` après le signal FGS-ready
 * (l'ordre API 34+, l'item I-1 de la review Task 2).
 */
private data class AndroidCapturePendingGrant(
    val token: String,
    val resultCode: Int,
    val data: Any?,
)

/** La première image du start : soit la frame et sa configuration, soit le refus du start. */
private sealed interface FirstFrameOutcome {
    public data class Ready(
        val frame: CapturePortFrame,
        val configuration: CaptureConfiguration,
    ) : FirstFrameOutcome

    public data class Refused(
        val failure: KadreFailure,
    ) : FirstFrameOutcome
}

/**
 * La pompe de frames MediaProjection — le miroir du WebCapturePump (:82-453) porté à
 * l'ImageReader : le grant BRUT y devient UN pending MediaProjection (`getMediaProjection`,
 * APRÈS le signal FGS-ready — l'ordre API 34+ exige le service `mediaProjection` démarré
 * AVANT la prise de projection, l'item I-1 de la review Task 2), UNE image acquise à la
 * fois (`acquireLatestImage` coalesce), chaque frame bornée par `maxFrameBytes` AVANT toute
 * copie (le never-copy invariant), les strides du SDK rapportés honests, l'Image fermée
 * exactement une fois, la configuration complète dérivée de la PREMIÈRE image (révision 0)
 * avant tout callback listener, une reconfiguration publiée `onReconfigured` avant la frame
 * qui la porte, et des terminations exactement une fois — `Stopped(Requested)` pour l'arrêt
 * propre (le close du stream), `Stopped(PermissionRevoked)` pour la révocation
 * `MediaProjection.Callback.onStop` qui n'est pas la nôtre, `Failed(PlatformFailure)` pour
 * un refus de lecture. Un terminal qui tombe PENDANT le `start` (la première image attend
 * encore) revient PAR LA FAILURE du start — le listener n'entend rien (un start raté ne
 * raconte rien), le signal reste unique. Le FGS démarre AVANT la prise de projection et le
 * display virtuel (l'attente du signal `startForeground` sérialise) et s'arrête au terminal.
 */
private class AndroidCapturePump(
    private val grant: AndroidCapturePendingGrant,
    private val projections: AndroidCaptureProjectionSource,
    private val request: CaptureRequest,
    private val foreground: AndroidCaptureForeground,
    private val readerSource: AndroidCaptureImageReaderSource,
    private val facts: AndroidCaptureDisplayFacts,
    private val loopContext: CoroutineContext,
    private val closedGate: () -> Boolean,
) {
    private val lock = RuntimeLock()
    private var listener: CapturePortStreamListener? = null
    private var maxFrameBytes = 0L
    private var started = false
    private var terminated = false
    private var released = false
    private var closed = false
    private var scope: CoroutineScope? = null
    private var revision = 0L
    private var configuration: CaptureConfiguration? = null
    private var reader: AndroidCaptureImageReader? = null
    private var display: Any? = null

    /** Le pending créé au `start` — null avant la consommation FGS-ready du grant. */
    private var projection: AndroidCapturePendingProjection? = null

    /**
     * Le start n'est pas encore retourné : un terminal qui tombe ICI ne parle PAS au
     * listener (le SPI n'autorise les callbacks qu'après un start réussi) — sa verdict
     * devient la failure du start ([startFailure]), le signal reste unique.
     */
    private var startSettled = false
    private var startFailure: KadreFailure? = null
    private val available = Channel<Unit>(Channel.CONFLATED)

    /**
     * Démarre le flux : le FGS d'abord (l'ordre que la plateforme exige), la consommation
     * du grant juste après le signal `startForeground` (JAMAIS plus tôt), puis reader et
     * display, puis la première image pour la configuration — retournée avant toute frame.
     */
    suspend fun start(
        listener: CapturePortStreamListener,
        maxFrameBytes: Long,
    ): KadreResult<CapturePortStreamStart> {
        require(maxFrameBytes > 0L) { "maxFrameBytes must be positive" }
        val admission = lock.withLock {
            when {
                closed -> KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.CaptureSession))
                started -> KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.CaptureCollector))
                else -> {
                    started = true
                    this.listener = listener
                    this.maxFrameBytes = maxFrameBytes
                    null
                }
            }
        }
        if (admission != null) return admission
        try {
            // (a) Le FGS Kadre, et l'attente de `startForeground(type = mediaProjection)` —
            // l'ordre P8-0 que la plateforme exige avant toute prise de projection.
            try {
                foreground.start(grant.token)
            } catch (refused: AndroidCaptureForegroundRefused) {
                return failedStart(KadreFailure.TemporarilyUnavailable(retryable = true))
            }
            // (a') L'item I-2 : le port a pu fermer pendant l'attente du FGS — jamais un
            // MediaProjection vivant derrière un port fermé, jamais un Success d'un start
            // dont le port est mort.
            if (closedGate()) {
                return failedStart(KadreFailure.Closed(KadreResourceKind.Host))
            }
            // (a'') L'item I-1 : LA consommation du grant — `getMediaProjection` ne peut
            // légalement tourner qu'APRÈS le `startForeground` du service `mediaProjection`
            // (l'API 34+ répond SecurityException sinon). Un seul appel, jamais plus tôt.
            val ownedProjection = try {
                projections.createPending(grant.resultCode, grant.data)
            } catch (refused: Throwable) {
                return failedStart(
                    KadreFailure.PlatformFailure(
                        KadrePlatform.Android,
                        PERMISSION_DOMAIN,
                        "projection-refused:${refused::class.java.simpleName}",
                    ),
                )
            }
            val proceed = lock.withLock {
                projection = ownedProjection
                !terminated && !released
            }
            if (!proceed) {
                // Un terminal a couru pendant la consommation : release() est déjà passé et
                // ne reverra jamais ce pending — il est stoppé ici, immédiatement.
                ownedProjection.stop()
                return failedStart(lock.withLock { startFailure } ?: KadreFailure.Closed(KadreResourceKind.Host))
            }
            // (b) La révocation peut avoir gagné la course entre le consentement et la prise :
            // le jeton est mort, le start échoue en refus de permission — jamais un display
            // fantôme derrière une projection arrêtée.
            if (ownedProjection.revoked) {
                return failedStart(KadreFailure.PermissionDenied(KadrePermission.CaptureScreen))
            }
            // (c) L'ImageReader RGBA_8888, le pool borné au budget : une frame entière doit
            // tenir dans le budget, et chaque emplacement du pool avec elle.
            val size = request.preferredSize ?: facts.size
            val frameBytes = size.width.toLong() * size.height * 4L
            if (frameBytes > maxFrameBytes) {
                return failedStart(KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, maxFrameBytes))
            }
            val maxImages = (maxFrameBytes / frameBytes).toInt().coerceIn(1, MAX_IMAGES)
            val ownedReader = try {
                readerSource.createReader(size.width, size.height, maxImages)
            } catch (refused: Throwable) {
                return failedStart(pipeFailure("image-reader-refused", refused))
            }
            lock.withLock { reader = ownedReader }
            ownedReader.setOnImageAvailable { available.trySend(Unit) }
            val ownedDisplay = try {
                ownedProjection.createVirtualDisplay(
                    "KadreCapture",
                    size.width,
                    size.height,
                    facts.densityDpi,
                    ownedReader.surface,
                )
            } catch (refused: Throwable) {
                return failedStart(pipeFailure("virtual-display-refused", refused))
            }
            lock.withLock { display = ownedDisplay }
            // (b') La révocation, posée après le display : tout onStop qui n'est pas le nôtre
            // termine le flux — pendant le start, PAR SA failure ; après, au listener.
            ownedProjection.onRevoked { revoke() }

            // (d) La PREMIÈRE image dérive la configuration complète (révision 0) — publiée en
            // valeur de retour du start, donc avant tout callback listener (web Pump :111-196).
            val first = acquireFirstImage()
                ?: return failedStart(
                    lock.withLock { startFailure } ?: pipeFailure("image-reader-closed", null),
                )
            val outcome = try {
                firstFrameFrom(first)
            } finally {
                first.close()
            }
            val ready = (outcome as? FirstFrameOutcome.Ready)
                ?: return failedStart((outcome as FirstFrameOutcome.Refused).failure)
            // Le start s'installe : à partir d'ICI les terminaux parlent au listener.
            lock.withLock { startSettled = true }
            // La boucle de delivery porte la pompe hors du chemin du start : le collector
            // n'entend la première frame qu'après le retour de start.
            val pumpScope = CoroutineScope(loopContext + SupervisorJob())
            lock.withLock { scope = pumpScope }
            pumpScope.launch { run(ready.frame) }
            return KadreResult.Success(CapturePortStreamStart(AndroidCapturePortStream(this), ready.configuration))
        } catch (cancellation: CancellationException) {
            // Le contrat d'annulation du SPI : une opération suspendante annulée avant son
            // retour libère son état exactement une fois, puis l'annulation surface.
            release()
            throw cancellation
        }
    }

    /** L'arrêt propre de l'application : les ressources partent, le listener entend `Stopped(Requested)`. */
    fun requestStop() {
        terminate(CaptureOutcome.Stopped(CaptureStopReason.Requested))
    }

    /** Le close du stream — non-bloquant, idempotent, scellé : une pompe close ne raconte rien. */
    fun close() {
        lock.withLock {
            closed = true
            terminated = true
        }
        release()
        scope?.cancel()
    }

    // -- la boucle de delivery ---------------------------------------------------------------------------

    private suspend fun run(firstFrame: CapturePortFrame) {
        try {
            deliver(firstFrame)
            while (true) {
                available.receive()
                val image = lock.withLock { reader }?.acquireLatest() ?: continue
                if (lock.withLock { terminated }) {
                    image.close()
                    return
                }
                // La frame suivante : reconfiguration publiée AVANT la frame qui la porte,
                // budget vérifié AVANT la copie, l'image fermée dans tous les cas.
                val frame = try {
                    frameFrom(image)
                } finally {
                    image.close()
                } ?: return // le terminal a déjà été délivré par frameFrom
                deliver(frame)
            }
        } catch (cancellation: CancellationException) {
            release()
            throw cancellation
        } catch (unexpected: Throwable) {
            terminate(CaptureOutcome.Failed(pipeFailure("frame-pump-failed", null)))
        }
    }

    /** La frame vers le collector — l'arrêt qui atterrit pendant la copie gagne la course. */
    private fun deliver(frame: CapturePortFrame) {
        if (lock.withLock { terminated }) return
        listener?.onFrame(frame)
    }

    /**
     * Attend le premier signal de l'ImageReader et acquiert la première image — le point de
     * suspension du start, annulable en libération propre par le catch du [start]. Un
     * terminal qui tombe pendant l'attente (le [terminateTerminal] réveille le canal) est
     * vu ici : `null`, et le start rendra SA failure — jamais un callback listener.
     */
    private suspend fun acquireFirstImage(): AndroidCaptureImage? {
        while (true) {
            available.receive()
            if (lock.withLock { terminated }) return null
            val image = lock.withLock { reader }?.acquireLatest() ?: continue
            if (lock.withLock { terminated }) {
                image.close()
                return null
            }
            return image
        }
    }

    /**
     * La PREMIÈRE image devient la frame initiale et la configuration complète (révision 0) —
     * SANS aucun callback listener (le SPI n'autorise les callbacks qu'après un start réussi) :
     * un refus revient en [FirstFrameOutcome.Refused] et le start l'échoue. L'image est
     * fermée par l'appelant.
     */
    private fun firstFrameFrom(image: AndroidCaptureImage): FirstFrameOutcome {
        if (image.width <= 0 || image.height <= 0) {
            return FirstFrameOutcome.Refused(pipeFailure("invalid-frame-size", null))
        }
        val plane = image.planes.singleOrNull()
            ?: return FirstFrameOutcome.Refused(pipeFailure("unexpected-plane-count", null))
        val layout = planeLayoutFor(image, plane)
            ?: return FirstFrameOutcome.Refused(pipeFailure("frame-exceeds-int32", null))
        val initial = configurationFor(image.width, image.height, revisionValue = 0L)
        if (layout.byteCount.toLong() > maxFrameBytes) {
            // La borne avant copie — la frame surdimensionnée est fermée par l'appelant, la
            // copie n'a jamais tourné.
            return FirstFrameOutcome.Refused(
                KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, maxFrameBytes),
            )
        }
        val bytes = try {
            image.readPlane(0, layout.byteCount)
        } catch (refused: Throwable) {
            return FirstFrameOutcome.Refused(pipeFailure("image-read-refused", refused))
        }
        val frame = try {
            CapturePortFrame(
                size = PhysicalSize(image.width, image.height),
                format = PixelFormat.Rgba8,
                planes = listOf(CapturePortPlane(layout, bytes)),
                configurationRevision = 0L,
                sourceTimestamp = image.timestampNanos.takeIf { it >= 0L }?.nanoseconds,
                duration = null,
                discontinuity = null,
                colorEncoding = initial.colorEncoding,
                alphaMode = initial.alphaMode,
                orientation = CaptureOrientation.Upright,
            )
        } catch (invalid: IllegalArgumentException) {
            // Le SPI a rejeté le layout : la frame aurait menti au runtime.
            return FirstFrameOutcome.Refused(pipeFailure("unexpected-plane-layout", null))
        }
        lock.withLock { configuration = initial }
        return FirstFrameOutcome.Ready(frame, initial)
    }

    /**
     * Une image SDK suivante devient une frame port : le layout au stride honest du plan, la
     * reconfiguration (la taille livrée diffère de la configuration) publiée AVANT la frame,
     * le budget vérifié AVANT la copie. `null` = le terminal a déjà été délivré. L'image est
     * fermée par l'appelant.
     */
    private fun frameFrom(image: AndroidCaptureImage): CapturePortFrame? {
        if (image.width <= 0 || image.height <= 0) {
            terminate(CaptureOutcome.Failed(pipeFailure("invalid-frame-size", null)))
            return null
        }
        val plane = image.planes.singleOrNull() ?: run {
            terminate(CaptureOutcome.Failed(pipeFailure("unexpected-plane-count", null)))
            return null
        }
        val layout = planeLayoutFor(image, plane) ?: run {
            terminate(CaptureOutcome.Failed(pipeFailure("frame-exceeds-int32", null)))
            return null
        }
        var effective = lock.withLock { configuration } ?: return null
        if (image.width != effective.size.width || image.height != effective.size.height) {
            // La configuration complète avance d'une révision — publiée au listener AVANT la
            // frame qui la porte (la discipline advance-by-one du runtime).
            val next = configurationFor(image.width, image.height, revisionValue = nextRevision())
            lock.withLock { configuration = next }
            listener?.onReconfigured(next)
            effective = next
        }
        if (layout.byteCount.toLong() > maxFrameBytes) {
            // La borne avant copie — la frame surdimensionnée est fermée, la copie n'a jamais tourné.
            terminate(
                CaptureOutcome.Failed(KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, maxFrameBytes)),
            )
            return null
        }
        val bytes = try {
            image.readPlane(0, layout.byteCount)
        } catch (refused: Throwable) {
            terminate(CaptureOutcome.Failed(pipeFailure("image-read-refused", refused)))
            return null
        }
        return try {
            CapturePortFrame(
                size = PhysicalSize(image.width, image.height),
                format = PixelFormat.Rgba8,
                planes = listOf(CapturePortPlane(layout, bytes)),
                configurationRevision = effective.revision.value,
                sourceTimestamp = image.timestampNanos.takeIf { it >= 0L }?.nanoseconds,
                duration = null,
                discontinuity = null,
                colorEncoding = effective.colorEncoding,
                alphaMode = effective.alphaMode,
                orientation = CaptureOrientation.Upright,
            )
        } catch (invalid: IllegalArgumentException) {
            // Le SPI a rejeté le layout : la frame aurait menti au runtime.
            terminate(CaptureOutcome.Failed(pipeFailure("unexpected-plane-layout", null)))
            null
        }
    }

    /** Le layout RGBA_8888 honest : rowStride du SDK, pixelStride du SDK, dernière ligne serrée. */
    private fun planeLayoutFor(image: AndroidCaptureImage, plane: AndroidCaptureImagePlane): PixelPlaneLayout? {
        val byteCount = (image.height - 1).toLong() * plane.rowStride + image.width.toLong() * plane.pixelStride
        if (byteCount > Int.MAX_VALUE) return null
        return try {
            PixelPlaneLayout(
                width = image.width,
                height = image.height,
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride,
                byteCount = byteCount.toInt(),
                horizontalSubsampling = 1,
                verticalSubsampling = 1,
            )
        } catch (invalid: IllegalArgumentException) {
            null
        }
    }

    private fun configurationFor(
        width: Int,
        height: Int,
        revisionValue: Long,
    ): CaptureConfiguration = CaptureConfiguration(
        revision = CaptureConfigurationRevision(revisionValue),
        size = PhysicalSize(width, height),
        format = PixelFormat.Rgba8,
        // MediaProjection ne rapporte aucun espace couleur par frame : les Unknown honests —
        // le miroir exact du branchement null du web.
        colorEncoding = ColorEncoding(
            primaries = ColorPrimaries.Unknown,
            transfer = TransferFunction.Unknown,
            matrix = MatrixCoefficients.Unknown,
            range = ColorRange.Unknown,
            hdr = HdrMetadata.Unknown,
        ),
        // Le miroir exact du HostChoice web (canvasSourced = false) : la composition système
        // n'est pas une promesse d'alpha que le port peut nommer.
        alphaMode = AlphaMode.Unknown,
        orientation = CaptureOrientation.Upright,
        // Les frames arrivent quand le système les produit, sans borne annoncée : Unknown.
        cadence = CaptureCadence.Unknown,
        // La région fut refusée à l'admission : l'affichage entier est la frame.
        region = null,
        cursorMode = request.cursorMode,
    )

    private fun nextRevision(): Long = lock.withLock {
        check(revision < Long.MAX_VALUE) { "capture configuration revision space exhausted" }
        revision += 1
        revision
    }

    /** La révocation système — `MediaProjection.Callback.onStop` qui n'est pas le nôtre. */
    private fun revoke() {
        terminate(CaptureOutcome.Stopped(CaptureStopReason.PermissionRevoked))
    }

    /** Le terminal, exactement une fois : libérer d'abord, puis dire, puis arrêter la boucle. */
    private fun terminate(outcome: CaptureOutcome) {
        terminateTerminal(CapturePortTermination.Outcome(outcome))
    }

    private fun terminateTerminal(termination: CapturePortTermination) {
        val first = lock.withLock { if (terminated) false else { terminated = true; true } }
        if (!first) return
        // Un terminal pendant le start (la première image attend encore) : sa verdict
        // devient LA FAILURE du start — le listener n'entend rien (le SPI n'autorise les
        // callbacks qu'après un start réussi), le signal reste unique.
        val unsettled = lock.withLock {
            if (startSettled) false else {
                startFailure = failureFor(termination)
                true
            }
        }
        release()
        available.trySend(Unit) // réveille l'attente de la première image — elle verra `terminated`
        if (!unsettled) listener?.onTerminated(termination)
        scope?.cancel()
    }

    /** Le verdict du terminal pendant le start, en failure de start. */
    private fun failureFor(termination: CapturePortTermination): KadreFailure = when (termination) {
        is CapturePortTermination.Outcome -> when (val outcome = termination.outcome) {
            is CaptureOutcome.Stopped -> when (outcome.reason) {
                CaptureStopReason.PermissionRevoked -> KadreFailure.PermissionDenied(KadrePermission.CaptureScreen)
                else -> KadreFailure.Closed(KadreResourceKind.CaptureSession)
            }

            is CaptureOutcome.Failed -> outcome.failure
            CaptureOutcome.SourceCompleted -> KadreFailure.Closed(KadreResourceKind.CaptureSession)
        }

        CapturePortTermination.SourceLost -> KadreFailure.Closed(KadreResourceKind.CaptureSession)
    }

    /** Le one release : display → reader → projection → FGS. Idempotent. */
    private fun release() {
        val targets = lock.withLock {
            if (released) null else {
                released = true
                Triple(reader, display, projection)
            }
        } ?: return
        targets.second?.let { ownedProjection -> targets.third?.releaseVirtualDisplay(ownedProjection) }
        targets.first?.close()
        // Notre propre arrêt de projection déclenche onStop : la révocation est absorbée par
        // le flag terminated — jamais un second terminal derrière notre propre stop. `null`
        // si le start n'a jamais atteint la consommation : rien de vivant n'existe (I-1).
        targets.third?.stop()
        foreground.stop()
    }

    /**
     * Échoue le start et scelle la pompe : le SPI n'autorise les callbacks listener qu'après un
     * start réussi, donc un start raté — et tout fait SDK tardif — ne dit rien au listener.
     */
    private fun failedStart(failure: KadreFailure): KadreResult.Failure {
        lock.withLock { terminated = true }
        release()
        return KadreResult.Failure(failure)
    }

    private fun pipeFailure(code: String, cause: Throwable?): KadreFailure = KadreFailure.PlatformFailure(
        KadrePlatform.Android,
        STREAM_DOMAIN,
        if (cause == null) code else "$code:${cause::class.java.simpleName}",
    )

    private companion object {
        /** Le pool ImageReader le plus large que le budget justifie, borné honnêtement. */
        const val MAX_IMAGES = 4
    }
}

/**
 * Le flux vivant d'une [AndroidHostChoiceReservation] : le close est l'arrêt propre de
 * l'application — non-bloquant, idempotent, et le chemin exact qui reporte `Stopped(Requested)`.
 */
private class AndroidCapturePortStream(
    private val pump: AndroidCapturePump,
) : CapturePortStream {
    private val lock = RuntimeLock()
    private var closed = false

    override fun close() {
        val first = lock.withLock { if (closed) false else { closed = true; true } }
        if (first) pump.requestStop()
    }
}

/**
 * La réalisation de production du seam projection — `getMediaProjection` consomme le grant,
 * le callback de révocation est posé ICI, avant toute réservation (la fact sheet §5a : le
 * callback avant le start). Les appels SDK vivent sur le thread appelant de la couture.
 */
internal class AndroidMediaProjectionSource(
    private val context: Context,
) : AndroidCaptureProjectionSource {

    override fun createPending(resultCode: Int, data: Any?): AndroidCapturePendingProjection {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            ?: throw IllegalStateException("the media projection service is absent on this device")
        val projection = manager.getMediaProjection(resultCode, data as Intent)
        return AndroidLiveProjection(projection)
    }

    override fun close() {}
}

private class AndroidLiveProjection(
    private val projection: MediaProjection,
) : AndroidCapturePendingProjection {
    private val lock = RuntimeLock()
    private var revokedFlag = false
    private var handler: (() -> Unit)? = null
    private var stopped = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            val fire = lock.withLock {
                revokedFlag = true
                handler
            }
            fire?.invoke()
        }
    }

    init {
        projection.registerCallback(callback, mainHandler)
    }

    override val revoked: Boolean
        get() = lock.withLock { revokedFlag }

    override fun onRevoked(handler: (() -> Unit)?) {
        val fire = lock.withLock {
            this.handler = handler
            if (revokedFlag) handler else null
        }
        fire?.invoke()
    }

    override fun createVirtualDisplay(name: String, width: Int, height: Int, dpi: Int, surface: Any): Any =
        projection.createVirtualDisplay(
            name,
            width,
            height,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface as Surface,
            null,
            mainHandler,
        )

    override fun releaseVirtualDisplay(display: Any) {
        (display as VirtualDisplay).release()
    }

    override fun stop() {
        val first = lock.withLock { if (stopped) false else { stopped = true; true } }
        if (first) projection.stop()
    }

    override fun close() = stop()
}

/**
 * La réalisation de production du seam ImageReader — RGBA_8888, le HandlerThread dédié
 * "KadreCapture" (le plumbing kadre-old :50-51), `acquireLatestImage` pour coalescer.
 */
internal class AndroidImageReaderSource : AndroidCaptureImageReaderSource {

    override fun createReader(width: Int, height: Int, maxImages: Int): AndroidCaptureImageReader =
        AndroidLiveImageReader(
            // `android.graphics.PixelFormat.RGBA_8888` — l'alias évite la collision avec le
            // `PixelFormat` du modèle de capture.
            ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, maxImages),
        )

    override fun close() {}
}

private class AndroidLiveImageReader(
    private val reader: ImageReader,
) : AndroidCaptureImageReader {
    private val thread = HandlerThread("KadreCapture").apply { start() }
    private val handler = Handler(thread.looper)

    override val surface: Any
        get() = reader.surface

    override fun setOnImageAvailable(listener: () -> Unit) {
        reader.setOnImageAvailableListener({ listener() }, handler)
    }

    override fun acquireLatest(): AndroidCaptureImage? = reader.acquireLatestImage()?.let(::AndroidLiveImage)

    override fun close() {
        reader.close()
        thread.quitSafely()
    }
}

private class AndroidLiveImage(
    private val image: Image,
) : AndroidCaptureImage {
    override val width: Int = image.width
    override val height: Int = image.height
    override val timestampNanos: Long = image.timestamp
    override val planes: List<AndroidCaptureImagePlane> =
        image.planes.map { AndroidCaptureImagePlane(rowStride = it.rowStride, pixelStride = it.pixelStride) }

    override fun readPlane(index: Int, byteCount: Int): ByteArray {
        val buffer = image.planes[index].buffer
        check(buffer.remaining() >= byteCount) { "the image plane holds fewer bytes than its honest layout" }
        val bytes = ByteArray(byteCount)
        buffer.position(0)
        buffer.get(bytes)
        return bytes
    }

    override fun close() {
        image.close()
    }
}

/**
 * Le registre des surfaces hôtes attachées de CETTE session — le fait contre lequel la route
 * `Surface` du port résout un id. L'argument surfaceView est `Any` (stub-safe en test hôte :
 * le stub android.jar lance à la construction, la Task 3 y lira le vrai SurfaceView).
 *
 * L'invariant « enregistrée UNE fois » est PORT-SCOPÉ par construction : l'instance vit dans
 * la même couture que le port (chaque branche d'attach construit le registre ET le port
 * ensemble, le registre ne survit pas à la session), donc enregistrer-une-fois-par-instance
 * EST enregistrer-une-fois-par-port — l'item de review m2 épinglé ici plutôt qu'un garde
 * global de plus. Le `check` refuse de réenregistrer le même id avec une AUTRE vue (le même
 * `check` que le `registerPrimarySurface` du web) ; réinscrire la MÊME vue sous le même id
 * est absorbé (le miroir web ne sait pas non plus distinguer mieux).
 */
internal interface AndroidCaptureSurfaceRegistry {
    public fun register(id: SurfaceId, surfaceView: Any)

    public fun unregister(id: SurfaceId)

    public fun resolve(id: SurfaceId): Any?
}

internal class AndroidHostCaptureSurfaceRegistry : AndroidCaptureSurfaceRegistry {
    private val lock = RuntimeLock()
    private val surfaces = HashMap<SurfaceId, Any>()

    override fun register(id: SurfaceId, surfaceView: Any) {
        lock.withLock {
            val existing = surfaces[id]
            check(existing == null || existing === surfaceView) {
                "the host capture surface is registered once per capture port"
            }
            surfaces[id] = surfaceView
        }
    }

    override fun unregister(id: SurfaceId) {
        lock.withLock { surfaces.remove(id) }
    }

    override fun resolve(id: SurfaceId): Any? = lock.withLock { surfaces[id] }
}
