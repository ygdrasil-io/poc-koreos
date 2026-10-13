package org.graphiks.kadre.platform.android

import android.graphics.Bitmap
import android.view.PixelCopy
import android.view.SurfaceView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.graphiks.kadre.capture.AlphaMode
import org.graphiks.kadre.capture.CaptureCadence
import org.graphiks.kadre.capture.CaptureConfiguration
import org.graphiks.kadre.capture.CaptureConfigurationRevision
import org.graphiks.kadre.capture.CaptureOrientation
import org.graphiks.kadre.capture.CaptureOutcome
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSourceKind
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
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.CapturePortFrame
import org.graphiks.kadre.internal.runtime.CapturePortPlane
import org.graphiks.kadre.internal.runtime.CapturePortReservation
import org.graphiks.kadre.internal.runtime.CapturePortSource
import org.graphiks.kadre.internal.runtime.CapturePortSourceKey
import org.graphiks.kadre.internal.runtime.CapturePortStream
import org.graphiks.kadre.internal.runtime.CapturePortStreamListener
import org.graphiks.kadre.internal.runtime.CapturePortStreamStart
import org.graphiks.kadre.internal.runtime.CapturePortTermination
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.surface.PhysicalSize
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

/** Le domaine des refus du flux PixelCopy — le même que le bras MediaProjection. */
private const val SURFACE_STREAM_DOMAIN = "capture-stream"

/** Le namespace des réservations Surface — le buffer composé du propre SurfaceView de l'app. */
private const val SURFACE_SOURCE_NAMESPACE = "android-pixel-copy"

/** `PixelCopy.SUCCESS` — le literal, le stub android.jar du test hôte n'est jamais nommé. */
private const val PIXEL_COPY_SUCCESS = 0

/**
 * Le seam SDK du PixelCopy — la couture du bras Surface (API 26+). Le port ne nomme JAMAIS
 * un type SDK de la copie : les bitmaps voyagent en jetons `Any` (le même parti pris que les
 * autres seams du module — le stub android.jar du test hôte lance au moindre appel, le test
 * passe ses propres jetons et pilote le completed-callback à la main).
 */
internal interface AndroidPixelCopyNative {
    /**
     * La taille composée courante de la surface attachée — `null` tant que la surface n'a
     * pas de buffer (avant le premier layout) ; la vérité du MOMENT, jamais une taille
     * mémorisée.
     */
    public fun composedSize(surfaceView: Any): PhysicalSize?

    /** Alloue le bitmap de copie ARGB_8888 — un jeton opaque pour le flux. */
    public fun createBitmap(width: Int, height: Int): Any

    /**
     * La copie ASYNC — `PixelCopy.request(SurfaceView, bitmap, listener, handler)` en
     * production. Le verdict SDK (`PixelCopy.SUCCESS` ou un code `ERROR_*`) est délivré à
     * [onCompleted] exactement une fois.
     */
    public fun requestCopy(surfaceView: Any, bitmap: Any, onCompleted: (result: Int) -> Unit)

    /** Les octets RGBA de la copie — le bitmap ARGB_8888 a le stride serré `width * 4`. */
    public fun readPixels(bitmap: Any): ByteArray

    /** Libère exactement le bitmap donné (`Bitmap.recycle`). */
    public fun recycle(bitmap: Any)
}

/**
 * La réalisation de production du seam PixelCopy — `PixelCopy.request(SurfaceView, ...)` sur
 * le looper main (le callback SDK arrive sur le handler donné), les octets lus par
 * `copyPixelsToBuffer` (RGBA_8888, stride serré).
 */
internal class AndroidLivePixelCopy : AndroidPixelCopyNative {

    override fun composedSize(surfaceView: Any): PhysicalSize? {
        val view = surfaceView as SurfaceView
        val width = view.width
        val height = view.height
        return if (width <= 0 || height <= 0) null else PhysicalSize(width, height)
    }

    override fun createBitmap(width: Int, height: Int): Any =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    override fun requestCopy(surfaceView: Any, bitmap: Any, onCompleted: (result: Int) -> Unit) {
        PixelCopy.request(
            surfaceView as SurfaceView,
            bitmap as Bitmap,
            { result: Int -> onCompleted(result) },
            MAIN_HANDLER,
        )
    }

    override fun readPixels(bitmap: Any): ByteArray {
        val owned = bitmap as Bitmap
        val bytes = ByteArray(owned.rowBytes * owned.height)
        owned.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(bytes))
        return bytes
    }

    override fun recycle(bitmap: Any) {
        (bitmap as Bitmap).recycle()
    }

    private companion object {
        val MAIN_HANDLER = android.os.Handler(android.os.Looper.getMainLooper())
    }
}

/**
 * La résolution du bras Surface : la surfaceView enregistrée par l'attach doit ÊTRE une
 * `SurfaceView` (le seul type que `PixelCopy.request` copie — les overloads Window sont hors
 * scope de la phase 8) ; tout autre view enregistré est un refus honnête
 * `Unsupported(CaptureOpen)`, jamais une copie d'autre chose. C'est LA fabrique par défaut
 * du port (le bras Task 3, à la place du stub jetant de la Task 1).
 */
internal fun androidPixelCopyReservation(
    surfaceView: Any,
    request: CaptureRequest,
): KadreResult<CapturePortReservation> {
    val view = surfaceView as? SurfaceView
        ?: return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
    return KadreResult.Success(
        AndroidPixelCopyStream(view, request, AndroidLivePixelCopy()),
    )
}

/**
 * Le flux PixelCopy d'une surface hôte attachée — le miroir du bras MediaProjection
 * ([AndroidCapturePump], la discipline du web Pump :111-196) porté à la copie composée :
 * la configuration complète (révision 0) dérivée de la PREMIÈRE copie avant tout callback
 * listener, `maxFrameBytes` vérifié AVANT toute allocation et toute lecture (l'invariant
 * never-copy — rien n'est créé du tout quand la frame entière dépasse le budget), une
 * reconfiguration publiée `onReconfigured` avant la frame qui la porte, et des terminations
 * exactement une fois — `Stopped(Requested)` pour l'arrêt propre, `Failed(PlatformFailure)`
 * pour un refus `PixelCopy`. La cadence : `minimumFrameInterval` de la requête → `Fixed`,
 * une copie par tick ; sinon `Unknown`, une copie par completed-callback (la plus simple
 * honnête, la décision documentée de la Task 3).
 */
internal class AndroidPixelCopyStream(
    private val surfaceView: Any,
    private val request: CaptureRequest,
    private val native: AndroidPixelCopyNative,
    private val loopContext: CoroutineContext = Dispatchers.Default,
) : CapturePortReservation {

    /** La source admise : HostSurface (le SurfaceView enregistré), la taille connue au start seulement. */
    override val source: CapturePortSource = CapturePortSource(
        key = CapturePortSourceKey(SURFACE_SOURCE_NAMESPACE, nextSourceKeyValue()),
        kind = CaptureSourceKind.HostSurface,
        name = null,
        size = null,
    )

    private val lock = RuntimeLock()
    private var closed = false
    private var started = false
    private var pump: AndroidPixelCopyPump? = null

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
        val ownedPump = AndroidPixelCopyPump(surfaceView, request, native, loopContext)
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
        // Le bitmap n'existe qu'à partir du start (l'invariant never-copy vaut pour la vie
        // entière) — une réservation jamais lancée n'a RIEN à libérer.
        lock.withLock { pump }?.close()
    }

    private companion object {
        /** La séquence des réservations Surface — chaque réservation en mint une fraîche. */
        val sourceKeyValues = AtomicLong(0L)

        fun nextSourceKeyValue(): Long = sourceKeyValues.incrementAndGet()
    }
}

/**
 * La copie prête : la frame et la configuration effective qui la porte (le retour interne
 * de [AndroidPixelCopyPump.copyFrame] — `null` y signifie refus-du-start enregistré ou
 * terminal déjà délivré).
 */
/** La lecture d'une copie sous verrou : les octets, le refus SDK, ou le terminal (`null`). */
private sealed interface PixelRead {
    data class Ok(val bytes: ByteArray) : PixelRead
    data class Refused(val reason: Throwable) : PixelRead
}

private class FirstCopyReady(
    val frame: CapturePortFrame,
    val configuration: CaptureConfiguration,
)

/**
 * La pompe PixelCopy — le miroir exact d'[AndroidCapturePump] : la configuration dérivée de
 * la PREMIÈRE copie (révision 0) avant tout callback, `maxFrameBytes` avant allocation ET
 * avant lecture, la reconfiguration avant la frame qui la porte, les terminations exactement
 * une fois, le bitmap libéré exactement une fois (resize compris — la copie périmée d'une
 * surface qui a changé de taille est JETÉE, jamais délivrée, la suivante porte le resize).
 * Un terminal pendant le `start` revient PAR SA failure — le listener n'entend rien.
 */
private class AndroidPixelCopyPump(
    private val surfaceView: Any,
    private val request: CaptureRequest,
    private val native: AndroidPixelCopyNative,
    private val loopContext: CoroutineContext,
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
    private var bitmap: Any? = null
    private var bitmapSize: PhysicalSize? = null

    /**
     * Le start n'est pas encore retourné : un terminal qui tombe ICI ne parle PAS au
     * listener — sa verdict devient la failure du start, le signal reste unique.
     */
    private var startSettled = false
    private var startFailure: KadreFailure? = null

    /** La cadence posée au start : Fixed = une copie par tick, sinon une copie par complétion. */
    private val cadence: CaptureCadence = request.minimumFrameInterval
        ?.let(CaptureCadence::Fixed)
        ?: CaptureCadence.Unknown

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
            // La PREMIÈRE copie dérive la configuration complète (révision 0) — le budget
            // est vérifié AVANT toute allocation : rien n'est créé, rien n'est copié.
            val first = copyFrame()
            if (first == null) {
                val failure = lock.withLock { startFailure }
                    ?: if (lock.withLock { closed }) {
                        KadreFailure.Closed(KadreResourceKind.CaptureSession)
                    } else {
                        pipeFailure("surface-copy-ended", null)
                    }
                return failedStart(failure)
            }
            // Le start s'installe : à partir d'ICI les terminaux parlent au listener.
            lock.withLock { startSettled = true }
            val pumpScope = CoroutineScope(loopContext + SupervisorJob())
            lock.withLock { scope = pumpScope }
            pumpScope.launch { run(first.frame) }
            return KadreResult.Success(
                CapturePortStreamStart(AndroidPixelCopyPortStream(this), first.configuration),
            )
        } catch (cancellation: CancellationException) {
            // Le contrat d'annulation du SPI : une opération suspendante annulée avant son
            // retour libère son état exactement une fois, puis l'annulation surface.
            release()
            throw cancellation
        }
    }

    /** L'arrêt propre de l'application : le listener entend `Stopped(Requested)`. */
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
                // La cadence : Fixed attend son tick entre deux copies ; Unknown/Variable
                // copie dès que la précédente se complète (la décision Task 3 documentée).
                val interval = (cadence as? CaptureCadence.Fixed)?.frameInterval
                if (interval != null) delay(interval)
                val ready = copyFrame() ?: return // le terminal a déjà été délivré
                deliver(ready.frame)
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
     * UNE copie : la taille composée lue à la demande ET re-lue à la complétion (le resize
     * se découvre là — une copie périmée est jetée, jamais délivrée), le budget vérifié
     * AVANT l'allocation du bitmap ET avant la lecture des octets, le verdict SDK traduit.
     * `null` = le terminal a déjà été délivré (ou attend le retour du start).
     */
    private suspend fun copyFrame(): FirstCopyReady? {
        var size = validSize() ?: return refusedOrTerminal(pipeFailure("surface-size-unavailable", null))
        var attempts = 0
        while (true) {
            if (lock.withLock { terminated }) return null
            if (frameBytesOf(size) > maxFrameBytes) {
                // La borne AVANT allocation — le pixel array n'est jamais créé (le
                // never-copy invariant, prouvé par le fake qui compte ses allocations).
                return refusedOrTerminal(
                    KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, maxFrameBytes),
                )
            }
            ensureBitmap(size)
            val ownedBitmap = lock.withLock { bitmap } ?: return null
            if (lock.withLock { terminated }) return null
            val result = suspendCancellableCoroutine { continuation ->
                native.requestCopy(surfaceView, ownedBitmap) { copyResult -> continuation.resume(copyResult) }
            }
            if (lock.withLock { terminated }) return null
            // La vérité du MOMENT de la complétion : la surface a pu changer de taille (ou
            // d'état) pendant la copie — le périmé est jeté, la boucle re-copie à la taille
            // fraîche ; un verdict d'erreur à taille INCHANGÉE est un refus genuine.
            val fresh = validSize()
            if (result != PIXEL_COPY_SUCCESS) {
                // Le refus transient (`ERROR_SOURCE_INVALID` : la surface se recrée) et le
                // changement de taille se traitent pareil — une RE-COPIE bornée, pas un
                // échec du flux ; le refus genuine n'arrive qu'après l'épuisement des
                // tentatives.
                if (++attempts > MAX_COPY_ATTEMPTS) {
                    return refusedOrTerminal(pipeFailure("pixel-copy-refused", null, result.toString()))
                }
                delay(COPY_RETRY_MILLIS)
                size = fresh ?: return refusedOrTerminal(pipeFailure("surface-size-unavailable", null))
                continue
            }
            if (fresh != size) {
                // Copie RÉUSSIE mais périmée (la taille a bougé pendant la copie) : jetée,
                // jamais délivrée — la boucle re-copie à la taille fraîche.
                if (++attempts > MAX_COPY_ATTEMPTS) {
                    return refusedOrTerminal(pipeFailure("stale-copy-abandoned", null))
                }
                size = fresh ?: return refusedOrTerminal(pipeFailure("surface-size-unavailable", null))
                continue
            }
            // La lecture SOUS LE VERROU de la pompe : le recycle du release prend le même
            // verrou — copie et recycle ne se croisent JAMAIS (un bitmap recyclé relu est
            // un crash natif).
            val read = lock.withLock {
                if (terminated) null else {
                    try {
                        PixelRead.Ok(native.readPixels(ownedBitmap))
                    } catch (refused: Throwable) {
                        PixelRead.Refused(refused)
                    }
                }
            }
            var bytes: ByteArray
            when (read) {
                null -> return null // le terminal a gagné la course
                is PixelRead.Refused ->
                    return refusedOrTerminal(pipeFailure("pixel-read-refused", read.reason))
                is PixelRead.Ok -> bytes = read.bytes
            }
            return frameFrom(size, bytes)
        }
    }

    /** La taille composée du moment, validée — `null` quand la surface n'a pas de buffer. */
    private fun validSize(): PhysicalSize? {
        val size = native.composedSize(surfaceView) ?: return null
        return if (size.width <= 0 || size.height <= 0) null else size
    }

    private fun frameBytesOf(size: PhysicalSize): Long = size.width.toLong() * size.height * 4L

    /** Réutilise le bitmap à la bonne taille, ou recycle l'ancien et en alloue un neuf. */
    private fun ensureBitmap(size: PhysicalSize) {
        val reusable = lock.withLock { bitmap != null && bitmapSize == size }
        if (reusable) return
        val stale = lock.withLock {
            val owned = bitmap
            bitmap = null
            bitmapSize = null
            owned
        }
        stale?.let(native::recycle)
        val created = native.createBitmap(size.width, size.height)
        lock.withLock {
            bitmap = created
            bitmapSize = size
        }
    }

    /**
     * La copie devient une frame port : la configuration complète (révision 0) stockée à la
     * PREMIÈRE frame, la reconfiguration publiée AVANT la frame qui la porte.
     */
    private fun frameFrom(size: PhysicalSize, bytes: ByteArray): FirstCopyReady {
        var effective = lock.withLock { configuration }
        if (effective == null) {
            val initial = configurationFor(size, revisionValue = 0L)
            lock.withLock {
                if (configuration == null) configuration = initial
            }
            effective = initial
        } else if (effective.size != size) {
            val next = configurationFor(size, revisionValue = nextRevision())
            lock.withLock { configuration = next }
            // La reconfiguration complète — publiée au listener AVANT la frame qui la porte
            // (la discipline advance-by-one du runtime).
            listener?.onReconfigured(next)
            effective = next
        }
        val layout = PixelPlaneLayout(
            width = size.width,
            height = size.height,
            rowStride = size.width * 4,
            pixelStride = 4,
            byteCount = size.width * size.height * 4,
            horizontalSubsampling = 1,
            verticalSubsampling = 1,
        )
        val frame = CapturePortFrame(
            size = size,
            format = PixelFormat.Rgba8,
            planes = listOf(CapturePortPlane(layout, bytes)),
            configurationRevision = effective.revision.value,
            sourceTimestamp = null,
            duration = null,
            discontinuity = null,
            colorEncoding = effective.colorEncoding,
            alphaMode = effective.alphaMode,
            orientation = CaptureOrientation.Upright,
        )
        return FirstCopyReady(frame, effective)
    }

    private fun configurationFor(
        size: PhysicalSize,
        revisionValue: Long,
    ): CaptureConfiguration = CaptureConfiguration(
        revision = CaptureConfigurationRevision(revisionValue),
        size = size,
        format = PixelFormat.Rgba8,
        // PixelCopy ne rapporte aucun espace couleur : les Unknown honests — le miroir exact
        // du branchement null du web.
        colorEncoding = ColorEncoding(
            primaries = ColorPrimaries.Unknown,
            transfer = TransferFunction.Unknown,
            matrix = MatrixCoefficients.Unknown,
            range = ColorRange.Unknown,
            hdr = HdrMetadata.Unknown,
        ),
        // Le miroir de la capability du port : le propre buffer de la SurfaceView ne compose
        // jamais le pointeur système, et l'alpha composé n'est pas une promesse nommable.
        alphaMode = AlphaMode.Unknown,
        orientation = CaptureOrientation.Upright,
        cadence = cadence,
        region = null,
        cursorMode = request.cursorMode,
    )

    private fun nextRevision(): Long = lock.withLock {
        check(revision < Long.MAX_VALUE) { "capture configuration revision space exhausted" }
        revision += 1
        revision
    }

    /** Le terminal, exactement une fois : libérer d'abord, puis dire, puis arrêter la boucle. */
    private fun terminate(outcome: CaptureOutcome) {
        terminateTerminal(CapturePortTermination.Outcome(outcome))
    }

    private fun terminateTerminal(termination: CapturePortTermination) {
        val first = lock.withLock { if (terminated) false else { terminated = true; true } }
        if (!first) return
        // Un terminal pendant le start : sa verdict devient LA FAILURE du start — le
        // listener n'entend rien, le signal reste unique (le miroir du bras MediaProjection).
        val unsettled = lock.withLock {
            if (startSettled) false else {
                startFailure = failureFor(termination)
                true
            }
        }
        release()
        if (!unsettled) listener?.onTerminated(termination)
        scope?.cancel()
    }

    private fun failureFor(termination: CapturePortTermination): KadreFailure = when (termination) {
        is CapturePortTermination.Outcome -> when (val outcome = termination.outcome) {
            is CaptureOutcome.Failed -> outcome.failure
            else -> KadreFailure.Closed(KadreResourceKind.CaptureSession)
        }

        CapturePortTermination.SourceLost -> KadreFailure.Closed(KadreResourceKind.CaptureSession)
    }

    /**
     * Le one release : le bitmap recyclé exactement une fois. Le champ est NULLÉ sous le
     * même verrou — un bitmap recyclé qui serait relu (la boucle réveillée après le release)
     * est un crash natif, jamais ré-atteint quand le champ est null.
     */
    private fun release() {
        val owned = lock.withLock {
            if (released) null else {
                released = true
                val owned = bitmap
                bitmap = null
                bitmapSize = null
                owned?.let(native::recycle)
                owned
            }
        }
        // le recycle a tourné SOUS le verrou (la lecture de frame ne le croise jamais)
        owned
    }

    /**
     * Un refus : pendant le start, sa verdict est ENREGISTRÉE et le start échouera avec ;
     * après le start, le terminal est délivré ICI et la boucle s'arrête. Les deux retournent
     * `null` — l'appelant s'arrête sans rien dire au listener.
     */
    private fun refusedOrTerminal(failure: KadreFailure): FirstCopyReady? {
        if (lock.withLock { startSettled }) {
            terminate(CaptureOutcome.Failed(failure))
            return null
        }
        lock.withLock { startFailure = failure }
        return null
    }

    /** Échoue le start et scelle la pompe : un start raté ne dit rien au listener. */
    private fun failedStart(failure: KadreFailure): KadreResult.Failure {
        lock.withLock { terminated = true }
        release()
        return KadreResult.Failure(failure)
    }

    private fun pipeFailure(code: String, cause: Throwable?, suffix: String? = null): KadreFailure =
        KadreFailure.PlatformFailure(
            KadrePlatform.Android,
            SURFACE_STREAM_DOMAIN,
            when {
                suffix != null -> "$code:$suffix"
                cause != null -> "$code:${cause::class.java.simpleName}"
                else -> code
            },
        )

    private companion object {
        /** Le plafond des re-copies (surface qui change de taille, ou transientement absente). */
        const val MAX_COPY_ATTEMPTS = 8

        /** L'attente entre deux re-copies — laisse la surface se recréer. */
        const val COPY_RETRY_MILLIS = 50L
    }
}

/**
 * Le flux vivant d'une réservation Surface : le close est l'arrêt propre de l'application —
 * non-bloquant, idempotent, et le chemin exact qui reporte `Stopped(Requested)`.
 */
private class AndroidPixelCopyPortStream(
    private val pump: AndroidPixelCopyPump,
) : CapturePortStream {
    private val lock = RuntimeLock()
    private var closed = false

    override fun close() {
        val first = lock.withLock { if (closed) false else { closed = true; true } }
        if (first) pump.requestStop()
    }
}
