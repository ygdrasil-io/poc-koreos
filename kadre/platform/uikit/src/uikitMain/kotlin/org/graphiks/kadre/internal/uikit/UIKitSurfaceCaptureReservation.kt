package org.graphiks.kadre.internal.uikit

import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.graphiks.kadre.capture.AlphaMode
import org.graphiks.kadre.capture.CaptureCadence
import org.graphiks.kadre.capture.CaptureConfiguration
import org.graphiks.kadre.capture.CaptureConfigurationRevision
import org.graphiks.kadre.capture.CaptureOrientation
import org.graphiks.kadre.capture.CaptureOutcome
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureStopReason
import org.graphiks.kadre.capture.ColorEncoding
import org.graphiks.kadre.capture.ColorPrimaries
import org.graphiks.kadre.capture.ColorRange
import org.graphiks.kadre.capture.HdrMetadata
import org.graphiks.kadre.capture.MatrixCoefficients
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.capture.PixelPlaneLayout
import org.graphiks.kadre.capture.TransferFunction
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.CapturePortFrame
import org.graphiks.kadre.internal.runtime.CapturePortPlane
import org.graphiks.kadre.internal.runtime.CapturePortReservation
import org.graphiks.kadre.internal.runtime.CapturePortSource
import org.graphiks.kadre.internal.runtime.CapturePortStream
import org.graphiks.kadre.internal.runtime.CapturePortStreamListener
import org.graphiks.kadre.internal.runtime.CapturePortStreamStart
import org.graphiks.kadre.internal.runtime.CapturePortTermination
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.surface.PhysicalSize
import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.reinterpret
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreGraphics.CGDataProviderCopyData
import platform.CoreGraphics.CGImageGetBitmapInfo
import platform.CoreGraphics.CGImageGetBytesPerRow
import platform.CoreGraphics.CGImageGetDataProvider
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.UIKit.UIView
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import platform.posix.memcpy

/** Le domaine des refus du flux surface — le MÊME que le bras ReplayKit (le miroir T3). */
private const val SURFACE_STREAM_DOMAIN = "capture-stream"

/**
 * L'encodage couleur du chemin renderer — LE pin du spike § 7 (le MÊME que le kernel des
 * fourcc packed 8-bit RGB : Bt709/Srgb/Identity/Full/None) : le contenu d'écran est
 * composé en sRGB, les primaires sRGB sont numériquement celles du BT.709, des données RGB
 * ne portent aucune matrice, le 8-bit RGB est full-range.
 */
private val SURFACE_COLOR_ENCODING = ColorEncoding(
    primaries = ColorPrimaries.Bt709,
    transfer = TransferFunction.Srgb,
    matrix = MatrixCoefficients.Identity,
    range = ColorRange.Full,
    hdr = HdrMetadata.None,
)

/**
 * La période du ticker quand la requête ne porte AUCUN `minimumFrameInterval` — un fait de
 * CONFIGURATION du pump (~30 fps, sobre par défaut), JAMAIS une vérité d'écran prétendue :
 * la cadence annoncée dans la configuration reste `Unknown` (le miroir Android), et le
 * `duration` des frames porte la période réellement armée (décision 5 du spike).
 */
internal val UIKIT_SURFACE_DEFAULT_TICK_INTERVAL: Duration = 1.0.seconds / 30.0

/**
 * LE DÉCODEUR du bitmap info d'UN `CGImage` renderer vers le format portable — la forme
 * pure (scalaires seuls) de la lecture que le kernel `uikitSurfaceLayoutOf` EXIGE de sa
 * couture (« la couture passe un format portable ou n'appelle pas ») : la table est celle
 * du header `CGImage.h` RELUE À LA SOURCE (spike fix round 2 — byteOrder 16Little=0x1000,
 * 32Little=0x2000, 16Big=0x3000, 32Big=0x4000) et de la MESURE du spike (décision 4) :
 *
 * - `premultipliedLast` + 32Big/default → Rgba8 (l'unité MSB→LSB R,G,B,A est `[R][G][B][A]`
 *   en mémoire — LE fait mesuré du renderer, bitmapInfo `0x1`) ;
 * - `premultipliedFirst` + 32Little → Bgra8 (`[B][G][R][A]` — le BGRA canonique iOS 0x2002) ;
 * - `noneSkipFirst` + 32Little → Bgrx8 (`[B][G][R][X]`) ;
 * - TOUT le reste → `null`, JAMAIS un relabel : `premultipliedLast+32Little` est
 *   `[A][B][G][R]` (ABGR), `noneSkipLast` est `[R][G][B][X]` (RGBX — hors table portable),
 *   les unités 16-bit (mesuré : `drawHierarchy` produit 0x1101/0x3001) et les composantes
 *   flottantes ne sont pas du packed 8-bit. Un `null` fabrique un refus honnête, jamais
 *   une couleur échangée.
 */
internal fun uikitSurfaceBitmapInfoFormatOf(bitmapInfo: Long): PixelFormat? {
    val byteOrder = bitmapInfo and 0xF000L
    if (byteOrder == 0x1000L || byteOrder == 0x3000L) return null // unités 16-bit
    if (bitmapInfo and 0x100L != 0x0L) return null // composantes flottantes
    val alpha = bitmapInfo and 0xFL
    return when {
        alpha == 0x2L && byteOrder == 0x2000L -> PixelFormat.Bgra8
        alpha == 0x1L && (byteOrder == 0x0000L || byteOrder == 0x4000L) -> PixelFormat.Rgba8
        alpha == 0x6L && byteOrder == 0x2000L -> PixelFormat.Bgrx8
        else -> null
    }
}

/**
 * Le seam TICKER de la pompe surface (ruling cadence, décision 5 du spike — le miroir
 * d'`UIKitRedrawScheduler` : « le scheduling est injecté ») : la réservation ARM le ticker
 * à [startTicking] (une fois par pump) et le DÉSAMORCE à [stopTicking] (exactement une
 * fois, la matière du pin « l'arrêt exactement une fois »). Le tick est le producteur de
 * la pompe : chaque invocation fait UN snapshot, consommé SYNCHRONEMENT dans l'invocation
 * (la fenêtre de validité du raster), et la copie Kotlin seule franchit la frontière.
 *
 * Le confinement PRODUCTION est le fil principal : le dessin d'une vue est main-confiné
 * (fait SDK) — la réalisation de production y schedule ; le test hôte passe le sien et
 * tire les ticks à la main (déterministe, sans run loop ni vsync).
 */
internal interface UIKitSurfaceTickSource {
    public fun startTicking(interval: Duration, onTick: () -> Unit)

    public fun stopTicking()
}

/**
 * Le jeton d'UN snapshot de vue — zéro type SDK (pattern jeton) : les faits sont
 * pré-extraits, la copie est UNE opération bloc ([copyInto], le memcpy honnête — JAMAIS la
 * boucle octet-par-octet du kadre-old) vers une destination que le PUMP a allouée après
 * son budget : les octets livrés ne peuvent jamais aliaser le raster source. La fenêtre de
 * validité est exactement l'invocation du tick — [close] met fin au jeton (le raster n'est
 * JAMAIS retenu au-delà).
 */
internal interface UIKitSurfaceSnapshot : AutoCloseable {
    public val width: Int
    public val height: Int
    public val bytesPerRow: Int

    /** Le format LU du bitmap info de CE snapshot — JAMAIS supposé (ruling 1). */
    public val format: PixelFormat

    /** Le copie bloc unique de [byteCount] octets — le budget a été vérifié AVANT. */
    public fun copyInto(destination: ByteArray, byteCount: Int)

    /** La fin du jeton — le raster est relâché, aucune lecture ne peut plus suivre. */
    override fun close()
}

/** L'issue d'UN snapshot de vue — les trois faits que le seam peut porter honnêtement. */
internal sealed interface UIKitSurfaceSnapshotOutcome {
    /**
     * La vue est SORTIE de sa fenêtre : la source admise — le contenu rendu de la vue dans
     * sa fenêtre — a disparu. Le pump répond `CapturePortTermination.SourceLost` (le ruling
     * du spike : SourceLost EST la surface détachée).
     */
    public data object SourceGone : UIKitSurfaceSnapshotOutcome

    /** Un snapshot consommable — le jeton porte les faits et le raster. */
    public data class Frame(public val snapshot: UIKitSurfaceSnapshot) : UIKitSurfaceSnapshotOutcome

    /**
     * Le renderer n'a produit AUCUN fait consommable : la raison est bornée ASCII et
     * devient LE code du terminal `Failed(PlatformFailure(UIKit, "capture-stream", …))` —
     * ni un skip silencieux (il fabriquerait une continuité), ni une reconfiguration
     * qu'aucune frame ne pourrait porter.
     */
    public data class Refused(public val reason: String) : UIKitSurfaceSnapshotOutcome
}

/**
 * Le seam SNAPSHOT du port capture — la lecture renderer d'UNE frame (le port ne nomme
 * JAMAIS un type UIKit de dessin ; le pattern jeton du module). La réalisation production
 * ([UIKitLiveSurfaceSnapshotSource]) lit bitmapInfo+bytesPerRow À CHAQUE snapshot (le pin
 * du spike : les voies de rendu peuvent sortir de la table — JAMAIS de format supposé).
 */
internal interface UIKitSurfaceSnapshotSource {
    public fun snapshot(view: Any): UIKitSurfaceSnapshotOutcome
}

/**
 * La réalisation production du ticker — UN `dispatch_after` auto-reschedulé sur la file
 * principale (le pattern maison `dispatch_async(dispatch_get_main_queue())`, miroir
 * `UIKitSurfaceObservationBinding`) : le tick N court, PUIS le N+1 est schedulé — jamais
 * deux snapshots en vol, jamais un tick hors main. `stopTicking` désamorce : le bloc en
 * vol se voit désarmé et ne re-schedule pas. Le ticker est démarré UNE FOIS
 * (le check lève au second start — la propriété du pump).
 */
internal class UIKitSurfaceMainQueueTickSource : UIKitSurfaceTickSource {
    private val lock = RuntimeLock()
    private var armed = false
    private var scheduled = false

    override fun startTicking(interval: Duration, onTick: () -> Unit) {
        require(interval.isPositive()) { "the surface tick interval must be positive" }
        lock.withLock {
            check(!armed) { "the surface tick source is started once" }
            armed = true
        }
        scheduleTick(interval, onTick)
    }

    override fun stopTicking() {
        lock.withLock { armed = false }
    }

    private fun scheduleTick(interval: Duration, onTick: () -> Unit) {
        lock.withLock {
            if (!armed || scheduled) return
            scheduled = true
        }
        dispatch_after(
            dispatch_time(DISPATCH_TIME_NOW, interval.inWholeNanoseconds),
            dispatch_get_main_queue(),
        ) {
            val live = lock.withLock {
                if (armed && scheduled) {
                    true
                } else {
                    scheduled = false
                    false
                }
            }
            if (live) {
                try {
                    onTick()
                } finally {
                    lock.withLock { scheduled = false }
                    scheduleTick(interval, onTick)
                }
            }
        }
    }
}

/**
 * La réservation Surface du port capture UIKit/tvOS (phase 9, Task 4) — le miroir
 * structurel de la réservation HostChoice ([UIKitHostCaptureReservation]) porté au
 * producteur TICK : réserver ne produit AUCUNE frame et n'allume RIEN ; [start] arme le
 * ticker injecté et attend la PREMIÈRE frame stagée pour dériver la configuration complète
 * (révision 0, avant tout callback listener — la discipline du moule), puis remet la pompe
 * au collector unique du runtime.
 *
 * **UNE SEULE file de livraison** (le moule T3, cloné — jamais une seconde forme) : le
 * tick ne fait que CONSOMMER (synchronement, dans la fenêtre de validité du raster — le
 * dessin de vue est main-confiné, le ticker de production schedule sur la file principale)
 * et ENFILER la copie Kotlin ; la boucle `run` sur [loopContext] est l'UNIQUE chemin vers
 * le listener — l'ordre des ticks EST l'ordre de livraison, jamais une paire de frames
 * inversée ni un flip-flop de révision par course entre deux fils.
 *
 * **La vérité de format par frame** (ruling 1) : les faits du snapshot entrent dans le
 * kernel [uikitSurfaceLayoutOf] À CHAQUE tick ; un refus de format persistant (le renderer
 * réellement sorti de la table portable sur TOUTES ses voies) répond un terminal
 * `Failed(PlatformFailure(UIKit, "capture-stream", <raison du seam>))` honnête — la
 * décision du ruling : ni un skip silencieux (il fabriquerait une continuité), ni une
 * `onReconfigured` vers un état qu'aucune frame ne peut porter (elle mentirait à la
 * discipline advance-by-one du runtime, dont la configuration se dérive des faits de
 * frame). Les faits malhonnêtes (une rangée qui ne tient pas pixel complet) répondent le
 * code miroir T3 `frame-format-out-of-table`.
 *
 * **`SourceLost` honnête** (le ruling du spike) : la vue sortie de sa fenêtre EST « the
 * admitted source disappeared » — le terminal `CapturePortTermination.SourceLost`,
 * exactement une fois ; au premier tick (pre-settle), sa verdict devient LA failure du
 * start (`Closed(CaptureSession)`, le miroir T3) et le listener n'entend rien.
 *
 * [close] est idempotent et termine la pompe (`Stopped(Requested)` au listener si personne
 * d'autre ne l'a portée — un runtime déjà en fin de session absorbe le terminal, jamais un
 * double) ; le désamorçage du ticker est EXACTEMENT UNE FOIS quoi qu'il arrive.
 *
 * ATTENTION aux appelants (drivers, harnesses) : [start] attend INDEFINIMENT la première
 * frame stagée du ticker — aucun timeout n'est inventé ici (le miroir T3 : ces faits ne
 * portent aucun pourquoi) ; tout appelant qui ne peut pas se permettre l'attente doit
 * borner la sienne. La production arme une période finie (le premier tick vient toujours).
 *
 * @param source le descripteur source détaché (la surface hôte, nommée par le port).
 * @param view la vue enregistrée par le registre de CETTE session — consommée comme un
 *   jeton `Any` par le seam (le port ne voit jamais le type UIKit).
 * @param request la requête admise (la région fut refusée avant la construction).
 * @param snapshots le seam snapshot possédé par le port — jamais fermé par la réservation.
 * @param tickSources la fabrique du ticker INJECTÉ (ruling cadence) — consultée UNE FOIS au
 *   start (le pump construit SON ticker) ; réserver ne consulte RIEN, n'allume RIEN.
 * @param tickInterval la période armée (le `minimumFrameInterval` de la requête, sinon le
 *   repli documenté [UIKIT_SURFACE_DEFAULT_TICK_INTERVAL]) — porte le `duration` des frames.
 * @param closedGate le fait « le port est fermé » — consulté au start (jamais un ticker
 *   vivant derrière un port fermé).
 * @param onEnded la déposition du slot de réservation vivante du port.
 * @param loopContext le contexte de L'UNIQUE boucle de livraison (le miroir Android, fix
 *   round 1) — CHAQUE frame y est livrée, la première stagée comme les suivantes.
 */
internal class UIKitSurfaceCaptureReservation(
    override val source: CapturePortSource,
    private val view: Any,
    private val request: CaptureRequest,
    private val snapshots: UIKitSurfaceSnapshotSource,
    private val tickSources: () -> UIKitSurfaceTickSource,
    private val tickInterval: Duration,
    private val closedGate: () -> Boolean,
    private val onEnded: () -> Unit,
    private val loopContext: CoroutineContext = Dispatchers.Default,
) : CapturePortReservation {
    private val lock = RuntimeLock()
    private var closed = false
    private var started = false
    private var pump: UIKitSurfaceCapturePump? = null

    init {
        require(tickInterval.isPositive()) { "the surface tick interval must be positive" }
    }

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
        // Le port a pu fermer depuis la réservation : jamais un ticker vivant derrière un
        // port fermé (le miroir T3).
        if (closedGate()) return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host))
        val ownedPump = UIKitSurfaceCapturePump(
            view = view,
            request = request,
            snapshots = snapshots,
            tickSources = tickSources,
            tickInterval = tickInterval,
            loopContext = loopContext,
        )
        lock.withLock { pump = ownedPump }
        // La course du close : la réservation a pu être fermée pendant la construction —
        // la pompe n'a encore RIEN armé, elle est scellée sans désamorçage de ticker.
        val reservationClosed = lock.withLock { closed }
        if (reservationClosed) {
            ownedPump.close()
            return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.CaptureSession))
        }
        return ownedPump.start(listener, maxFrameBytes)
    }

    override fun close() {
        val first = lock.withLock {
            if (closed) {
                false
            } else {
                closed = true
                true
            }
        }
        if (!first) return
        lock.withLock { pump }?.close()
        onEnded()
    }

    /**
     * Le compteur de diagnostics des ticks tombés en silence (gardes refusées, décisions
     * tardives) — le pattern des compteurs de drops des phases précédentes ; délégation au
     * pump vivant, `0` tant que rien n'a démarré.
     */
    internal fun droppedSnapshotCount(): Long =
        lock.withLock { pump }?.droppedSnapshotCount() ?: 0L
}

/**
 * La pompe de frames surface — le CLONE du pump ReplayKit ([UIKitReplayKitPump], fix round
 * 1) porté au producteur tick : le ticker n'y fait que CONSOMMER (synchronement, dans la
 * fenêtre de validité du raster) et ENFILER la copie Kotlin dans [deliveries] ; la boucle
 * `run` sur [loopContext] est l'UNIQUE chemin vers le listener. La configuration complète
 * (révision 0) est dérivée de la PREMIÈRE frame AVANT tout callback listener, le budget
 * `maxFrameBytes` est vérifié AVANT la copie À CHAQUE frame (zéro octet copié sur un refus
 * — le pin I2), la reconfiguration est publiée AVANT la frame qui la porte (taille OU
 * format — le delta propre au chemin renderer, dont la voie peut changer entre deux
 * snapshots), et les terminaux tombent EXACTEMENT UNE FOIS — `Stopped(Requested)` de
 * l'arrêt propre, `Failed(PlatformFailure)` des refus du seam, `SourceLost` de la vue
 * sortie de sa fenêtre. Les ticks TARDIFS (après le terminal, ou les extras avant
 * l'installation) sont REFUSÉS AVANT tout snapshot et COMPTEÉS ([droppedSnapshotCount]).
 *
 * La borne PRATIQUE de la file UNLIMITED : le collector du runtime draine la file à chaque
 * livraison (un tick par frame, zéro coalescence — N ticks → N frames), et le budget par
 * frame est vérifié à la CONSOMMATION (avant copie), jamais en file.
 */
internal class UIKitSurfaceCapturePump(
    private val view: Any,
    private val request: CaptureRequest,
    private val snapshots: UIKitSurfaceSnapshotSource,
    private val tickSources: () -> UIKitSurfaceTickSource,
    private val tickInterval: Duration,
    private val loopContext: CoroutineContext,
) {
    private val lock = RuntimeLock()
    private var closed = false
    private var started = false
    private var settled = false
    private var terminated = false
    private var released = false
    private var listener: CapturePortStreamListener? = null
    private var maxFrameBytes = 0L
    private var startFailure: KadreFailure? = null
    private var configuration: CaptureConfiguration? = null
    private var revision = 0L
    private var parkedPayload: SamplePayload? = null
    private var scope: CoroutineScope? = null
    private var ticker: UIKitSurfaceTickSource? = null

    /** Le compteur des ticks tombés en silence (gardes refusées, ticks tardifs). */
    private var droppedSnapshots = 0L

    private val firstPayloadArrived = Channel<Unit>(Channel.CONFLATED)

    /**
     * L'UNIQUE file de livraison : le tick enfile les copies consommées, la boucle `run`
     * les livre en FIFO — l'ordre d'arrivée EST l'ordre de livraison (le moule T3, fix
     * round 1). La borne PRATIQUE de l'UNLIMITED : le collector du runtime draine la file
     * à chaque livraison (un tick par frame — N ticks → N frames, zéro coalescence), et le
     * budget par frame est vérifié à la CONSOMMATION (avant copie), jamais en file.
     */
    private val deliveries = Channel<SamplePayload>(Channel.UNLIMITED)

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
            // (a) Le ticker est construit ICI (le start, jamais la réservation — la
            // fabrique n'est consultée qu'une fois) puis ARMÉ — le producteur peut tirer
            // dès ici (le premier tick est le premier fait de la session).
            val ownedTicker = tickSources()
            lock.withLock { ticker = ownedTicker }
            ownedTicker.startTicking(tickInterval) { onTick() }
            // (d) La première frame dérive la configuration (révision 0) — le start
            // n'aboutit qu'une fois elle existante, avant tout callback listener. Le close
            // et le terminal réveillent l'attente (le canal CONFLATED) : un close pendant
            // le start répond IMMEDIATEMENT.
            val first = awaitParkedPayload()
                ?: return failedStart(
                    lock.withLock { startFailure } ?: KadreFailure.Closed(KadreResourceKind.CaptureSession),
                )
            val initialConfiguration = lock.withLock { configuration }
                ?: return failedStart(KadreFailure.Closed(KadreResourceKind.CaptureSession))
            // Le start s'installe : à partir d'ICI les terminaux parlent au listener.
            lock.withLock { settled = true }
            val pumpScope = CoroutineScope(loopContext + SupervisorJob())
            lock.withLock { scope = pumpScope }
            // L'UNIQUE boucle de livraison (le moule T3) : la première frame d'abord, puis
            // la file FIFO des suivantes — jamais un second fil de livraison.
            pumpScope.launch { run(first) }
            return KadreResult.Success(CapturePortStreamStart(UIKitSurfaceCaptureStream(this), initialConfiguration))
        } catch (cancellation: CancellationException) {
            // Le contrat d'annulation du SPI : le terminal est scellé D'ABORD (les ticks en
            // vol tombent en silence à la garde), puis la libération, puis l'annulation
            // surface (le miroir T3).
            lock.withLock { terminated = true }
            release()
            throw cancellation
        }
    }

    /** Le compteur de diagnostics des ticks tombés en silence (le pin « counted »). */
    internal fun droppedSnapshotCount(): Long = lock.withLock { droppedSnapshots }

    /** L'arrêt propre de l'application : le listener entend `Stopped(Requested)`. */
    internal fun requestStop() {
        terminate(CaptureOutcome.Stopped(CaptureStopReason.Requested))
    }

    /**
     * Le seal de la pompe (le close de la réservation) : le terminal `Stopped(Requested)`
     * si personne d'autre ne l'a porté — un runtime déjà en fin de session l'absorbe (le
     * flag `stopping` du runtime), jamais un double. Libère exactement une fois.
     */
    internal fun close() {
        terminate(CaptureOutcome.Stopped(CaptureStopReason.Requested))
        lock.withLock { scope }?.cancel()
    }

    // -- le chemin tick (le producteur — la file du ticker, la principale en production) -----

    /**
     * UNE invocation du ticker : la DÉCISION de garde d'abord (un tick tardif ne dessine
     * RIEN — zéro fait de seam, zéro raster), puis la consommation synchrone (la fenêtre
     * de validité du raster est exactement cette invocation), puis l'enfilement de la
     * copie Kotlin seule.
     */
    internal fun onTick() {
        val decision = lock.withLock {
            when {
                terminated -> TickDecision.Drop
                !settled -> if (parkedPayload == null) TickDecision.Park else TickDecision.Drop
                else -> TickDecision.Enqueue
            }
        }
        when (decision) {
            TickDecision.Park -> {
                val payload = consume() ?: return
                val parked = lock.withLock {
                    when {
                        terminated -> false
                        parkedPayload != null -> false
                        else -> {
                            parkedPayload = payload
                            configuration = configurationFor(payload, revisionValue = 0L)
                            firstPayloadArrived.trySend(Unit)
                            true
                        }
                    }
                }
                if (!parked) countDropped()
            }

            TickDecision.Enqueue -> {
                val payload = consume() ?: return
                // L'ENFILEMENT seul — la boucle `run` est l'unique livreur (l'ordre
                // d'arrivée EST l'ordre de livraison, le moule T3).
                deliveries.trySend(payload)
            }

            TickDecision.Drop -> {
                // Tardif (après le terminal) ou extra (avant l'installation) : RIEN n'a
                // été dessiné (la décision précède tout fait de seam), le drop est compté.
                countDropped()
            }
        }
    }

    // -- la boucle de livraison (l'UNIQUE chemin vers le listener) --------------------------

    /**
     * La boucle du moule T3 : la première frame (stagée avant l'installation), puis la
     * file FIFO — toutes les livraisons listener sérialisées sur UN fil. Un terminal qui
     * gagne la course pendant l'attente en file garde le dernier mot (silence absorbé,
     * sans compte — les refus comptés sont ceux de la GARDE du tick).
     */
    private suspend fun run(first: SamplePayload) {
        try {
            deliver(first)
            while (true) {
                val payload = deliveries.receive()
                if (lock.withLock { terminated }) return
                deliver(payload)
            }
        } catch (cancellation: CancellationException) {
            // Le cancel du scope (le terminal, le close) — rien à libérer ici.
            throw cancellation
        } catch (unexpected: Throwable) {
            terminate(CaptureOutcome.Failed(pipeFailure("frame-pump-failed")))
        }
    }

    // -- la consommation d'UN tick (la fenêtre raster+copy) ---------------------------------

    /**
     * La lecture du seam et la fenêtre raster+copy : issue du seam d'abord (SourceGone →
     * `SourceLost` ; refus → `Failed` au code du seam), puis faits → kernel
     * ([uikitSurfaceLayoutOf]) → BUDGET → LE copie bloc → close du jeton EXACTEMENT UNE
     * FOIS (en finally). `null` = le terminal (ou le refus) a déjà été porté — zéro octet
     * n'a quitté le raster.
     */
    private fun consume(): SamplePayload? = when (val outcome = snapshots.snapshot(view)) {
        is UIKitSurfaceSnapshotOutcome.SourceGone -> {
            // LE ruling du spike : la vue sortie de sa fenêtre EST la source admise qui
            // disparaît — SourceLost, pas un Failed (le chemin, lui, n'a pas failli).
            terminateTerminal(CapturePortTermination.SourceLost)
            null
        }

        is UIKitSurfaceSnapshotOutcome.Refused -> {
            refuse(
                KadreFailure.PlatformFailure(KadrePlatform.UIKit, SURFACE_STREAM_DOMAIN, outcome.reason),
            )
        }

        is UIKitSurfaceSnapshotOutcome.Frame -> consumeFrame(outcome.snapshot)
    }

    /**
     * La fenêtre raster+copy d'UN jeton : faits → kernel → BUDGET → LE copie bloc → close
     * EXACTEMENT UNE FOIS (en finally). `null` = le terminal a déjà été porté.
     */
    private fun consumeFrame(token: UIKitSurfaceSnapshot): SamplePayload? {
        try {
            val layout = uikitSurfaceLayoutOf(
                UIKitSurfaceFrameFacts(
                    width = token.width,
                    height = token.height,
                    bytesPerRow = token.bytesPerRow,
                    format = token.format,
                ),
            ) ?: return refuse(pipeFailure("frame-format-out-of-table"))
            // LE BUDGET AVANT LA COPIE (ruling 1, le miroir I2) : une frame surdimensionnée
            // répond un terminal ResourceLimitExceeded — la copie n'a jamais tourné.
            if (layout.byteCount.toLong() > maxFrameBytes) {
                return refuse(
                    KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, maxFrameBytes),
                )
            }
            val bytes = ByteArray(layout.byteCount)
            try {
                token.copyInto(bytes, layout.byteCount)
            } catch (refused: Throwable) {
                return refuse(pipeFailure("frame-copy-failed"))
            }
            return SamplePayload(
                width = token.width,
                height = token.height,
                format = token.format,
                layout = layout,
                bytes = bytes,
            )
        } finally {
            token.close()
        }
    }

    /** Un refus en cours de consommation : le terminal (exactement une fois) puis rien. */
    private fun refuse(failure: KadreFailure): SamplePayload? {
        terminate(CaptureOutcome.Failed(failure))
        return null
    }

    // -- la livraison ----------------------------------------------------------------------

    private fun deliver(payload: SamplePayload) {
        val current = lock.withLock { configuration } ?: return
        var effective = current
        if (payload.width != effective.size.width ||
            payload.height != effective.size.height ||
            payload.format != effective.format
        ) {
            // La reconfiguration avance d'UNE révision — publiée AVANT la frame qui la
            // porte (la discipline advance-by-one du runtime). Le delta propre au chemin
            // renderer : la voie de rendu peut changer entre deux snapshots (le repli
            // layer.render prenant le relais), le FORMAT fait partie du fait.
            val next = configurationFor(payload, nextRevision())
            val accepted = lock.withLock {
                if (terminated) {
                    null
                } else {
                    configuration = next
                    next
                }
            } ?: return
            listener?.onReconfigured(accepted)
            effective = accepted
        }
        // La re-vérification à la livraison : le terminal qui a gagné la course pendant la
        // copie garde le dernier mot — rien n'arrive après lui.
        if (lock.withLock { terminated }) return
        val frame = try {
            CapturePortFrame(
                size = PhysicalSize(payload.width, payload.height),
                format = payload.format,
                planes = listOf(CapturePortPlane(payload.layout, payload.bytes)),
                configurationRevision = effective.revision.value,
                // PAS de timestamp inventé : un snapshot ne porte aucun PTS (le miroir
                // T3 honnête — le fait global d'une horloge n'est pas un fait de frame).
                sourceTimestamp = null,
                // La période du ticker EST le duration du frame (décision 5 du spike —
                // un vsync injecterait le jitter de l'écran dans un fait portable).
                duration = tickInterval,
                discontinuity = null,
                colorEncoding = SURFACE_COLOR_ENCODING,
                alphaMode = AlphaMode.Unknown,
                orientation = CaptureOrientation.Upright,
            )
        } catch (invalid: IllegalArgumentException) {
            // Le SPI a rejeté le layout : la frame aurait menti au runtime.
            terminate(CaptureOutcome.Failed(pipeFailure("unexpected-plane-layout")))
            return
        }
        // LE compteur du driver : un appel listener = un compteur — le gel du compte après
        // le terminal est l'assertion « zéro frame après l'arrêt » du driver (Task 6).
        UIKitCaptureDiagnostics.surfaceFrameDelivered()
        listener?.onFrame(frame)
    }

    private fun configurationFor(
        payload: SamplePayload,
        revisionValue: Long,
    ): CaptureConfiguration = CaptureConfiguration(
        revision = CaptureConfigurationRevision(revisionValue),
        size = PhysicalSize(payload.width, payload.height),
        format = payload.format,
        // L'encodage ÉPINGLÉ du chemin renderer (le pin du spike § 7 — le même que le
        // kernel des fourcc packed 8-bit RGB).
        colorEncoding = SURFACE_COLOR_ENCODING,
        // La composition est celle du PROPRE buffer de la vue et le format peut changer de
        // voie (Bgrx8 sans alpha) : aucune sémantique d'alpha que le port peut nommer
        // uniformément — le miroir exact du HostChoice.
        alphaMode = AlphaMode.Unknown,
        orientation = CaptureOrientation.Upright,
        // La cadence annoncée : Fixed quand la requête porte un intervalle (le miroir
        // Android PixelCopy), Unknown sinon — le ticker tourne quand même à sa période.
        cadence = request.minimumFrameInterval?.let(CaptureCadence::Fixed) ?: CaptureCadence.Unknown,
        // La région fut refusée à la réservation : la vue entière est la frame.
        region = null,
        cursorMode = request.cursorMode,
    )

    private fun nextRevision(): Long = lock.withLock {
        check(revision < Long.MAX_VALUE) { "capture configuration revision space exhausted" }
        revision += 1
        revision
    }

    // -- l'attente du start ----------------------------------------------------------------

    /**
     * L'attente de la première frame stagée — le close et le terminal la réveillent aussi
     * (le canal CONFLATED), donc un close pendant le start répond IMMEDIATEMENT.
     */
    private suspend fun awaitParkedPayload(): SamplePayload? {
        while (true) {
            firstPayloadArrived.receiveCatching().getOrNull() ?: return null
            if (lock.withLock { terminated }) return null
            val payload = lock.withLock { parkedPayload }
            if (payload != null) {
                lock.withLock { parkedPayload = null }
                return payload
            }
        }
    }

    // -- les terminaux et la libération ------------------------------------------------------

    private fun terminate(outcome: CaptureOutcome) {
        terminateTerminal(CapturePortTermination.Outcome(outcome))
    }

    private fun terminateTerminal(termination: CapturePortTermination) {
        val first = lock.withLock { if (terminated) false else { terminated = true; true } }
        if (!first) return
        // Un terminal pendant le start (la première frame attend encore) : sa verdict
        // devient LA failure du start — le listener n'entend rien (le SPI n'autorise les
        // callbacks qu'après un start réussi), le signal reste unique.
        val unsettled = lock.withLock {
            if (settled) {
                false
            } else {
                startFailure = failureFor(termination)
                true
            }
        }
        release()
        firstPayloadArrived.trySend(Unit)
        if (!unsettled) listener?.onTerminated(termination)
        lock.withLock { scope }?.cancel()
    }

    /** La verdict du terminal pendant le start, en failure de start (le miroir T3). */
    private fun failureFor(termination: CapturePortTermination): KadreFailure = when (termination) {
        is CapturePortTermination.Outcome -> when (val outcome = termination.outcome) {
            is CaptureOutcome.Failed -> outcome.failure
            else -> KadreFailure.Closed(KadreResourceKind.CaptureSession)
        }

        CapturePortTermination.SourceLost -> KadreFailure.Closed(KadreResourceKind.CaptureSession)
    }

    /**
     * La libération : le désamorçage du ticker EXACTEMENT UNE FOIS — seulement si le
     * ticker fut réellement construit et armé (l'admission de la pompe). Aucune erreur de
     * désamorçage n'est un terminal : le flux s'arrêtait déjà.
     */
    private fun release() {
        val first = lock.withLock {
            if (released) {
                false
            } else {
                released = true
                true
            }
        }
        if (!first) return
        if (lock.withLock { started }) {
            lock.withLock { ticker }?.stopTicking()
        }
        lock.withLock { scope }?.cancel()
    }

    private fun failedStart(failure: KadreFailure): KadreResult.Failure {
        lock.withLock { terminated = true }
        release()
        return KadreResult.Failure(failure)
    }

    private fun countDropped() {
        lock.withLock { droppedSnapshots += 1 }
    }

    private fun pipeFailure(code: String): KadreFailure =
        KadreFailure.PlatformFailure(KadrePlatform.UIKit, SURFACE_STREAM_DOMAIN, code)

    /** La décision de garde d'UNE invocation du ticker. */
    private enum class TickDecision { Park, Enqueue, Drop }

    /** La matière Kotlin d'UNE frame consommée — le jeton raster est déjà refermé. */
    private class SamplePayload(
        val width: Int,
        val height: Int,
        val format: PixelFormat,
        val layout: PixelPlaneLayout,
        val bytes: ByteArray,
    )
}

/**
 * Le flux vivant d'une [UIKitSurfaceCaptureReservation] : le close est l'arrêt propre de
 * l'application — non-bloquant, idempotent, et le chemin exact qui reporte
 * `Stopped(Requested)`.
 */
private class UIKitSurfaceCaptureStream(
    private val pump: UIKitSurfaceCapturePump,
) : CapturePortStream {
    private val lock = RuntimeLock()
    private var closed = false

    override fun close() {
        val first = lock.withLock { if (closed) false else { closed = true; true } }
        if (first) pump.requestStop()
    }
}

/**
 * La réalisation production du seam snapshot — LA lecture renderer mesurée par le spike
 * (décision 4) : `drawViewHierarchyInRect(afterScreenUpdates: true)` est la voie primaire
 * (le sélecteur canonique du snapshot de vue), `layer.render(in:)` le repli (la voie
 * mesurée 32bpp RGBA8 stable). Le bitmap info du `CGImage` produit est LU À CHAQUE
 * snapshot (`CGImageGetBitmapInfo` + `GetBytesPerRow` — JAMAIS un format supposé : les
 * voies de rendu peuvent sortir de la table, 16bpp et 16-bit composantes mesurés sur le
 * chemin même du pump), décodé par [uikitSurfaceBitmapInfoFormatOf], et la vue HORS
 * fenêtre répond `SourceGone` avant tout dessin (le fait `SourceLost` du ruling).
 *
 * Le jeton retenu ([UIKitLiveSurfaceSnapshot]) porte la référence du `CGImage` — zéro
 * octet de pixel n'est copié avant `copyInto` (le budget est vérifié AVANT, le miroir
 * I2) — et [close] met fin à la référence (le raster n'est jamais retenu au-delà du tick).
 */
@OptIn(ExperimentalForeignApi::class)
internal class UIKitLiveSurfaceSnapshotSource : UIKitSurfaceSnapshotSource {

    override fun snapshot(view: Any): UIKitSurfaceSnapshotOutcome {
        val uiView = view as? UIView
            ?: return UIKitSurfaceSnapshotOutcome.Refused("surface-not-a-view")
        // Le fait SourceLost AVANT tout dessin : la vue hors fenêtre n'a plus de contenu
        // rendu dont le snapshot serait le fait (le ruling du spike, la décision Task 4).
        if (uiView.window == null) return UIKitSurfaceSnapshotOutcome.SourceGone

        // La voie primaire puis le repli documenté (le pin du spike : la voie de rendu est
        // relue par snapshot, le `null` du décodeur EST le signal de repli).
        val primary = renderPath(uiView) {
            uiView.drawViewHierarchyInRect(uiView.bounds, afterScreenUpdates = true)
        }
        if (primary is RenderOutcome.Rendered) {
            return UIKitSurfaceSnapshotOutcome.Frame(
                UIKitLiveSurfaceSnapshot(
                    image = primary.snapshot.image,
                    width = primary.snapshot.width,
                    height = primary.snapshot.height,
                    bytesPerRow = primary.snapshot.bytesPerRow,
                    format = primary.snapshot.format,
                ),
            )
        }
        return when (val fallback = renderPath(uiView) {
            // Le contexte du renderer est courant dans le bloc (la forme classique) :
            // layer.render en a besoin, drawViewHierarchy l'ignore.
            uiView.layer.renderInContext(checkNotNull(UIGraphicsGetCurrentContext()))
        }) {
            is RenderOutcome.Rendered -> UIKitSurfaceSnapshotOutcome.Frame(
                UIKitLiveSurfaceSnapshot(
                    image = fallback.snapshot.image,
                    width = fallback.snapshot.width,
                    height = fallback.snapshot.height,
                    bytesPerRow = fallback.snapshot.bytesPerRow,
                    format = fallback.snapshot.format,
                ),
            )

            // Le repli a produit des faits mais HORS table : l'état du renderer est réel
            // sur au moins une voie — le refus nommé (le ruling 1 : terminal honnête).
            RenderOutcome.OutOfTable -> UIKitSurfaceSnapshotOutcome.Refused("surface-format-out-of-table")

            // Le repli n'a produit AUCUN raster : « illisible » si AUCUNE voie n'a produit
            // de fait, sinon l'état hors table de la voie primaire reste LE fait nommé.
            RenderOutcome.NoImage -> UIKitSurfaceSnapshotOutcome.Refused(
                if (primary is RenderOutcome.NoImage) "snapshot-unreadable" else "surface-format-out-of-table",
            )
        }
    }

    /**
     * UNE voie de rendu : le rendu, la lecture brute des faits, le décodage — le résultat
     * nomme HONNÊTEMENT lequel des deux faits de repli s'applique (pas d'image produite,
     * ou décodage hors table).
     */
    private fun renderPath(
        uiView: UIView,
        draw: () -> Unit,
    ): RenderOutcome {
        val renderer = UIGraphicsImageRenderer(
            bounds = uiView.bounds,
            format = UIGraphicsImageRendererFormat.defaultFormat(),
        )
        val image = renderer.imageWithActions { _ -> draw() }
        val cgImage = image.CGImage ?: return RenderOutcome.NoImage
        val bitmapInfo = CGImageGetBitmapInfo(cgImage).toLong() and 0xFFFF_FFFFL
        val format = uikitSurfaceBitmapInfoFormatOf(bitmapInfo) ?: return RenderOutcome.OutOfTable
        return RenderOutcome.Rendered(
            RenderedSnapshot(
                image = cgImage,
                width = CGImageGetWidth(cgImage).toInt(),
                height = CGImageGetHeight(cgImage).toInt(),
                bytesPerRow = CGImageGetBytesPerRow(cgImage).toInt(),
                format = format,
            ),
        )
    }

    /** L'issue d'UNE voie de rendu — les deux faits de repli sont distingués. */
    private sealed interface RenderOutcome {
        data class Rendered(val snapshot: RenderedSnapshot) : RenderOutcome
        data object NoImage : RenderOutcome
        data object OutOfTable : RenderOutcome
    }

    /** Les faits consommables d'UN raster rendu — le format est DÉJÀ décodé LU. */
    private class RenderedSnapshot(
        val image: CGImageRef,
        val width: Int,
        val height: Int,
        val bytesPerRow: Int,
        val format: PixelFormat,
    )
}

/**
 * Le jeton production d'UN snapshot de vue : la référence du `CGImage` est retenue JUSQU'À
 * [copyInto] (zéro octet de pixel copié avant le budget — le miroir I2), la copie est LE
 * memcpy bloc depuis la donnée du provider (la lecture honnête du raster — jamais la
 * boucle octet-par-octet du kadre-old), et [close] met fin à la référence : le raster
 * n'est JAMAIS retenu au-delà de la fenêtre du tick. Un `copyInto` après `close` lève (un
 * bug de pump doit être BRUYANT, jamais une lecture d'un raster libéré).
 */
@OptIn(ExperimentalForeignApi::class)
private class UIKitLiveSurfaceSnapshot(
    private var image: CGImageRef?,
    override val width: Int,
    override val height: Int,
    override val bytesPerRow: Int,
    override val format: PixelFormat,
) : UIKitSurfaceSnapshot {

    override fun copyInto(destination: ByteArray, byteCount: Int) {
        val cgImage = checkNotNull(image) { "the surface snapshot was already closed" }
        val data = checkNotNull(CGDataProviderCopyData(CGImageGetDataProvider(cgImage))) {
            "the surface snapshot data provider is unreadable"
        }
        val length = CFDataGetLength(data)
        check(length >= byteCount.toLong()) { "the surface snapshot raster is shorter than its facts" }
        val source = checkNotNull(CFDataGetBytePtr(data)).reinterpret<ByteVarOf<Byte>>()
        destination.usePinned { pinned ->
            memcpy(pinned.addressOf(0), source, byteCount.toULong())
        }
    }

    override fun close() {
        image = null
    }
}
