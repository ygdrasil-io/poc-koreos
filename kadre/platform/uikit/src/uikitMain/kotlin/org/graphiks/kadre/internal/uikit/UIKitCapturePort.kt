package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.capture.CaptureCapabilities
import org.graphiks.kadre.capture.CaptureCursorMode
import org.graphiks.kadre.capture.CapturePermissionScope
import org.graphiks.kadre.capture.CapturePermissionState
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSourceKind
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
import org.graphiks.kadre.internal.runtime.CapturePortSource
import org.graphiks.kadre.internal.runtime.CapturePortSourceKey
import org.graphiks.kadre.internal.runtime.CapturePortSources
import org.graphiks.kadre.internal.runtime.CapturePortTarget
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.surface.SurfaceId
import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferGetPresentationTimeStamp
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetPixelFormatType
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.Foundation.NSError
import platform.ReplayKit.RPScreenRecorder
import platform.posix.memcpy

/**
 * Le seam SDK du port capture UIKit/tvOS (phase 9, Task 2 pour le plan de contrôle, Task 3
 * pour le flux) — la lecture ReplayKit de disponibilité en production, le démarrage/arrêt du
 * recorder et la consommation de ses samples, une source factice pilotable en test hôte (le
 * pattern jeton du module, miroir [UIKitDisplaySource]) : le port ne nomme JAMAIS un type
 * ReplayKit — c'est la traduction de ce seam en formes port qui est prouvée dans
 * `uikitTest`, pas la lecture SDK elle-même (charterée sur device).
 *
 * Task 2 portait le fait contrôle-plan (la disponibilité) ; Task 3 y ajoute LE FLUX :
 * `startCapture` (la forme klib `startCaptureWithHandler` — handler
 * `(CMSampleBufferRef?, Long, NSError?)`, complétion `(NSError?)`), `stopCapture` (la forme
 * `stopCaptureWithHandler`) et les jetons [UIKitCaptureSample] — le buffer natif ne
 * franchit JAMAIS le seam : chaque sample y devient un jeton dont les faits et la copie
 * sont lus DANS l'invocation du handler (la fenêtre de validité du buffer natif).
 */
internal interface UIKitCaptureSource : AutoCloseable {
    /**
     * La lecture LIVE de `RPScreenRecorder.isAvailable` (la FONCTION klib — pas une propriété)
     * au moment de l'appel. Le pin du spike (décision 2) : la valeur appartient au runtime de
     * l'hôte — JAMAIS « Simulator ⇒ indisponible » codé en dur ; un Simulator qui répond `false`
     * (runtimes anciens) reste honnête par la même lecture.
     */
    public fun recorderAvailable(): Boolean

    /**
     * Le démarrage du flux ReplayKit — le handler reçoit les samples vidéo (les jetons),
     * les erreurs du flux vivant (`onStreamError`), et la complétion du démarrage
     * (`onStarted` / `onStartFailed` avec le code numérique de l'NSError).
     */
    public fun startCapture(handler: UIKitCaptureStreamHandler)

    /** L'arrêt du flux — l'erreur éventuelle est un fait diagnostic, jamais un terminal. */
    public fun stopCapture(onStopped: (String?) -> Unit)
}

/**
 * Les faits verrouillés d'UN pixel buffer ReplayKit — zéro type SDK (pattern jeton). La
 * copie est UNE opération bloc ([copyInto], le memcpy honnête du spike — JAMAIS la boucle
 * octet-par-octet du kadre-old), vers une destination que le PUMP a allouée après son
 * budget : les octets livrés ne peuvent jamais aliaser la source.
 */
internal interface UIKitCaptureLockedBuffer {
    public val width: Int
    public val height: Int
    public val bytesPerRow: Int
    public val cvFormat: Long

    /** Le copie bloc unique de [byteCount] octets — le budget a été vérifié AVANT. */
    public fun copyInto(destination: ByteArray, byteCount: Int)
}

/**
 * Le jeton d'UN sample vidéo ReplayKit — consommé SYNCHRONEMENT dans l'invocation du
 * handler (la fenêtre de validité du buffer natif ne dépasse jamais le handler). La
 * fenêtre fact+copy est exactement la paire [lock]/[unlock] ; [close] referme le jeton
 * EXACTEMENT UNE FOIS (un double close natif est un crash — la discipline est épinglée par
 * les jetons de seam, qui LÈVENT au second close).
 */
internal interface UIKitCaptureSample : AutoCloseable {
    /** Le timestamp de présentation du sample en nanoseconds, ou null si non lisible honnêtement. */
    public val sourceTimestampNanos: Long?

    /**
     * Le lock read-only ET les faits — le début de la fenêtre fact+copy. `null` signifie
     * « illisible et NON verrouillé » (le pump referme le sample, sans unlock).
     */
    public fun lock(): UIKitCaptureLockedBuffer?

    /** La fin de la fenêtre fact+copy — l'unlock du lock pris par [lock]. */
    public fun unlock()

    /** La fermeture du jeton EXACTEMENT UNE FOIS. */
    override fun close()
}

/**
 * Le handler du flux ReplayKit — la forme klib (`(CMSampleBufferRef?, Long, NSError?)` +
 * complétion) réduite à ses quatre faits, sans aucun type SDK :
 *
 * - [onSample] : UN sample vidéo (le jeton), ou `null` pour un sample non vidéo (l'audio
 *   ReplayKit n'a pas d'image buffer — un drop silencieux, compté côté pump) ;
 * - [onStreamError] : l'erreur du flux VIVANT (le `error` du captureHandler) — le code
 *   numérique de l'NSError, borné ASCII ;
 * - [onStarted] / [onStartFailed] : la complétion du DÉMARRAGE (le spike § 6 : l'erreur de
 *   complétion fait échouer l'ouverture).
 */
internal interface UIKitCaptureStreamHandler {
    public fun onSample(sample: UIKitCaptureSample?)
    public fun onStreamError(code: String)
    public fun onStarted()
    public fun onStartFailed(code: String)
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

    @OptIn(ExperimentalForeignApi::class)
    override fun startCapture(handler: UIKitCaptureStreamHandler) {
        RPScreenRecorder.sharedRecorder().startCaptureWithHandler(
            captureHandler = { sampleBuffer: CMSampleBufferRef?, _: Long, error: NSError? ->
                when {
                    error != null -> handler.onStreamError(error.code.toString())

                    sampleBuffer != null -> {
                        // Seuls les samples VIDÉO deviennent des jetons — l'audio ReplayKit
                        // (app/mic) n'a pas d'image buffer et tombe en silence (compté côté
                        // pump). Le jeton est consommé SYNCHRONEMENT dans cette invocation.
                        val pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer)
                        handler.onSample(
                            if (pixelBuffer != null) {
                                UIKitReplayKitCaptureSample(sampleBuffer, pixelBuffer)
                            } else {
                                null
                            },
                        )
                    }

                    else -> handler.onSample(null)
                }
            },
            completionHandler = { error: NSError? ->
                if (error != null) handler.onStartFailed(error.code.toString()) else handler.onStarted()
            },
        )
    }

    override fun stopCapture(onStopped: (String?) -> Unit) {
        RPScreenRecorder.sharedRecorder().stopCaptureWithHandler { error: NSError? ->
            onStopped(error?.code?.toString())
        }
    }

    override fun close() = Unit
}

/**
 * Le jeton production d'UN sample vidéo ReplayKit : le lock read-only couvre la lecture
 * des faits ET la copie bloc (memcpy vers la destination du pump), l'unlock referme la
 * fenêtre, et le jeton ne retient RIEN au-delà de l'invocation du handler — le buffer
 * natif est consommé strictement dedans (sa fenêtre de validité), rien n'est retenu ni
 * libéré nativement par [close] : la discipline « exactement une fois » est portée par le
 * pump et prouvée par les jetons de seam.
 */
@OptIn(ExperimentalForeignApi::class)
private class UIKitReplayKitCaptureSample(
    private val sampleBuffer: CMSampleBufferRef,
    private val pixelBuffer: CVPixelBufferRef,
) : UIKitCaptureSample {
    override val sourceTimestampNanos: Long? = replayKitPresentationTimestampNanos(sampleBuffer)
    private var locked = false

    override fun lock(): UIKitCaptureLockedBuffer? {
        check(!locked) { "the replaykit sample buffer is locked twice" }
        CVPixelBufferLockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly)
        locked = true
        val base = CVPixelBufferGetBaseAddress(pixelBuffer)
        if (base == null) {
            // Un pixel buffer sans base address ne se lit pas : le lock est refermé ICI —
            // `null` signifie « illisible et NON verrouillé » (le pump ferme, sans unlock).
            CVPixelBufferUnlockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly)
            locked = false
            return null
        }
        return UIKitReplayKitLockedBuffer(
            source = base.reinterpret<ByteVarOf<Byte>>(),
            width = CVPixelBufferGetWidth(pixelBuffer).toInt(),
            height = CVPixelBufferGetHeight(pixelBuffer).toInt(),
            bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer).toInt(),
            cvFormat = CVPixelBufferGetPixelFormatType(pixelBuffer).toLong(),
        )
    }

    override fun unlock() {
        if (!locked) return
        locked = false
        CVPixelBufferUnlockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly)
    }

    /**
     * CAVEAT : le header `RPScreenRecorder.h` est MUET sur la propriété du buffer du
     * handler (ni retain ni release documentés) — ce no-op est une convention DOCUMENTÉE
     * (consommation strictement synchrone, rien retenu, donc rien à libérer), pas un fait
     * de header ; si une task prouvait un jour une possession (+1) explicite, c'est ICI
     * que la libération exactement-une-fois devrait naître.
     */
    override fun close() = Unit
}

/** La source verrouillée production — le memcpy bloc unique vers la destination du pump. */
@OptIn(ExperimentalForeignApi::class)
private class UIKitReplayKitLockedBuffer(
    private val source: CPointer<ByteVarOf<Byte>>,
    override val width: Int,
    override val height: Int,
    override val bytesPerRow: Int,
    override val cvFormat: Long,
) : UIKitCaptureLockedBuffer {
    override fun copyInto(destination: ByteArray, byteCount: Int) {
        destination.usePinned { pinned ->
            memcpy(pinned.addressOf(0), source, byteCount.toULong())
        }
    }
}

/**
 * Le timestamp de présentation du sample, en nanoseconds — l'honnêteté du brief : une
 * lecture à bon marché (`CMSampleBufferGetPresentationTimeStamp`), `null` quand le CMTime
 * n'est pas un instant honnête (invalide, négatif, epoch non nul, débordement) — JAMAIS un
 * timestamp inventé.
 */
@OptIn(ExperimentalForeignApi::class)
private fun replayKitPresentationTimestampNanos(sampleBuffer: CMSampleBufferRef): Long? {
    val time = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
    val (value, timescale, epoch) = time.useContents {
        Triple(this.value, this.timescale, this.epoch)
    }
    if (value < 0L || timescale <= 0 || epoch != 0L) return null
    if (value != 0L && Long.MAX_VALUE / value < 1_000_000_000L) return null
    return value * 1_000_000_000L / timescale
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
 * AVANT tout seam** (ruling 6, précédent AND-009). **La réservation HostChoice est le bras
 * ReplayKit réel** (Task 3 — la disponibilité y est RELUE à la réservation puis au start,
 * la pompe vit dans [UIKitHostCaptureReservation]) ; **la réservation Surface est le bras
 * renderer réel** (Task 4 — la pompe surface vit dans [UIKitSurfaceCaptureReservation] : le
 * ticker injecté, la vérité de format lue par snapshot, `SourceLost` de la vue détachée) ;
 * la route Surface résout d'abord l'id dans le registre de CETTE session
 * ([UIKitCaptureSurfaceRegistry], nourri par la couture d'attach), un id
 * inconnu répond `InvalidRequest("request.target")` (le miroir Android), et la région est
 * refusée STRUCTURELLEMENT avant même la résolution (le miroir HostChoice).
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
 * @param surfaceSnapshots le seam snapshot du bras Surface (Task 4) — la réalisation
 *   production lit le renderer réel ; le test hôte passe le sien (pattern jeton).
 * @param surfaceTickSources la fabrique des tickers du bras Surface (ruling cadence — le
 *   scheduling est injecté) : UNE instance fraîche par réservation, démarrée au start et
 *   désamorcée à la libération.
 */
internal class UIKitCapturePort(
    private val source: UIKitCaptureSource,
    private val surfaces: UIKitCaptureSurfaceRegistry = UIKitHostCaptureSurfaceRegistry(),
    private val surfaceSnapshots: UIKitSurfaceSnapshotSource = UIKitLiveSurfaceSnapshotSource(),
    private val surfaceTickSources: () -> UIKitSurfaceTickSource = { UIKitSurfaceMainQueueTickSource() },
) : CapturePort {
    private val lock = RuntimeLock()
    private var closed = false
    private var observer: ((KadreResult<CapturePortSnapshot>) -> Unit)? = null

    /** La séquence des clés de réservation de CE port (le miroir Android). */
    private var nextReservationKeyValue = 0L

    /**
     * La réservation HostChoice vivante de CE port (le slot unique, le miroir Android) — le
     * close du port la termine (ruling 4 : un port qui ferme pendant une réservation vivante
     * la fait terminer immédiatement, la pompe arrêtée, plus aucun callback après).
     */
    private var liveReservation: UIKitHostCaptureReservation? = null

    /**
     * La réservation Surface vivante de CE port (le slot unique, le miroir du slot
     * HostChoice) — le close du port la termine pareillement (ruling 4).
     */
    private var liveSurfaceReservation: UIKitSurfaceCaptureReservation? = null

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
     * un `CapturePortSourceKey` ne peut rien désigner) ; la cible Surface résout l'id dans
     * le registre de CETTE session puis construit la réservation renderer réelle (Task 4 —
     * la région est refusée STRUCTURELLEMENT avant même la résolution, le miroir
     * HostChoice) ; le choix hôte construit la réservation ReplayKit réelle (Task 3 — le
     * squelette Task 2 est remplacé).
     */
    override suspend fun reserve(
        target: CapturePortTarget,
        request: CaptureRequest,
    ): KadreResult<CapturePortReservation> {
        if (isClosed()) return closedFailure()
        return when (target) {
            is CapturePortTarget.Source ->
                KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))

            is CapturePortTarget.Surface -> surfaceReservation(target.id, request)

            CapturePortTarget.HostChoice -> hostChoiceReservation(request)
        }
    }

    /**
     * La réservation HostChoice — le bras ReplayKit réel (Task 3, remplaçant le squelette
     * Task 2). La région est refusée STRUCTURELLEMENT avant tout fait de seam (ReplayKit
     * capte l'affichage ENTIER — la capability l'annonce déjà `region Unsupported`, le
     * miroir Android). LE FAIT DE SEAM DU BRAS (le pin delta != 0) : la disponibilité du
     * recorder est RELUE à la réservation — un recorder indisponible refuse honnêtement la
     * MÊME forme que la table (`TemporarilyUnavailable`, retryable — `isAvailable` ne porte
     * aucun pourquoi, jamais une raison fabriquée) au lieu d'admettre une réservation qui
     * ne pourra jamais démarrer. La disponibilité est RELUE ENCORE au start (ruling 6) —
     * la propriété est vivante, le fait peut basculer entre les deux.
     */
    private fun hostChoiceReservation(request: CaptureRequest): KadreResult<CapturePortReservation> {
        if (request.region != null) {
            return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
        }
        if (!source.recorderAvailable()) {
            return KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true))
        }
        val keyValue = lock.withLock {
            val value = nextReservationKeyValue
            nextReservationKeyValue += 1L
            value
        }
        val reservation = UIKitHostCaptureReservation(
            source = CapturePortSource(
                key = CapturePortSourceKey(HOST_CHOICE_SOURCE_NAMESPACE, keyValue),
                kind = CaptureSourceKind.Display,
                name = null,
                size = null,
            ),
            streams = source,
            request = request,
            closedGate = ::isClosed,
            onEnded = { lock.withLock { liveReservation = null } },
        )
        lock.withLock { liveReservation = reservation }
        return KadreResult.Success(reservation)
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
     * livraison après le close) et toute voie ultérieure répond `Closed(Host)`. Une
     * réservation HostChoice ou Surface vivante est terminée d'ABORD (ruling 4 — le close du
     * port pendant une réservation active est un terminal immédiat, la pompe arrêtée, le
     * ticker désamorcé exactement une fois, plus aucun callback après). Le registre meurt
     * avec le port : la session entière le possède, aucun autre détenteur n'existe — rien à
     * déposer ici (le miroir Android).
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
        if (firstClose) {
            lock.withLock { liveReservation }?.close()
            lock.withLock { liveSurfaceReservation }?.close()
            source.close()
        }
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
     * La réservation de la cible Surface — le bras renderer réel (Task 4, remplaçant le
     * squelette T2), dans l'ordre :
     *
     * 1. **La région est refusée STRUCTURELLEMENT, avant même la résolution du registre**
     *    (le miroir du bras HostChoice : la région est une propriété de la REQUÊTE, le
     *    refus ne dépend d'aucun état de session). La forme est celle que la table annonce
     *    déjà (surface : `region Unsupported` → `Unsupported(CaptureOpen)`) — PAS
     *    `InvalidRequest`, qui reste la forme de la cible IRRÉSOLVABLE : le snapshot
     *    capture la vue ENTIÈRE, aucune primitive de sous-rect n'existe dans le pump.
     * 2. **L'id doit être la surface hôte enregistrée par CETTE session** (l'admission du
     *    runtime ne vérifie que la capability et transfère l'id tel quel) — sinon
     *    `InvalidRequest("request.target")`, la forme du refus de cible irrésoluble
     *    (miroir Android).
     * 3. **La réservation est construite** contre la vue résolue (le jeton `Any` du
     *    registre — le port ne voit jamais le type UIKit), le seam snapshot et LA fabrique
     *    de tickers. RÉSERVER ne touche PAS le seam snapshot et ne consulte PAS la
     *    fabrique (l'admission ne fait qu'admettre — les pins delta zéro ; le ticker
     *    n'est construit et armé qu'au START) ; le slot vivant est déposé pour le ruling 4
     *    (le close du port termine la réservation vivante).
     */
    private fun surfaceReservation(
        id: SurfaceId,
        request: CaptureRequest,
    ): KadreResult<CapturePortReservation> {
        if (request.region != null) {
            return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
        }
        val view = lock.withLock { surfaces.resolve(id) }
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("request.target"))
        val keyValue = lock.withLock {
            val value = nextReservationKeyValue
            nextReservationKeyValue += 1L
            value
        }
        val reservation = UIKitSurfaceCaptureReservation(
            source = CapturePortSource(
                key = CapturePortSourceKey(SURFACE_SOURCE_NAMESPACE, keyValue),
                kind = CaptureSourceKind.HostSurface,
                name = null,
                size = null,
            ),
            view = view,
            request = request,
            snapshots = surfaceSnapshots,
            tickSources = surfaceTickSources,
            tickInterval = request.minimumFrameInterval ?: UIKIT_SURFACE_DEFAULT_TICK_INTERVAL,
            closedGate = ::isClosed,
            onEnded = { lock.withLock { liveSurfaceReservation = null } },
        )
        lock.withLock { liveSurfaceReservation = reservation }
        return KadreResult.Success(reservation)
    }

    private fun isClosed(): Boolean = lock.withLock { closed }

    private fun closedFailure(): KadreResult.Failure =
        KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host))

    private companion object {
        /** Le namespace des réservations HostChoice — UN affichage entier (le miroir Android). */
        private const val HOST_CHOICE_SOURCE_NAMESPACE = "uikit-host-choice"

        /**
         * Le namespace des réservations Surface — le contenu RENDU du propre view de
         * l'app (le miroir Android `android-pixel-copy`).
         */
        private const val SURFACE_SOURCE_NAMESPACE = "uikit-surface"

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
