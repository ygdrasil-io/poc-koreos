package org.graphiks.kadre.internal.uikit

import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.graphiks.kadre.capture.AlphaMode
import org.graphiks.kadre.capture.CaptureCadence
import org.graphiks.kadre.capture.CaptureConfiguration
import org.graphiks.kadre.capture.CaptureConfigurationRevision
import org.graphiks.kadre.capture.CaptureOutcome
import org.graphiks.kadre.capture.CaptureOrientation
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureStopReason
import org.graphiks.kadre.capture.ColorEncoding
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.capture.PixelPlaneLayout
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.KadrePermission
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

/** Le domaine ReplayKit des échecs du recorder — le mapping du spike § 6. */
private const val REPLAYKIT_DOMAIN = "replaykit"

/** Le domaine de la pompe de frames — chaque refus de lecture/copie du pixel buffer. */
private const val STREAM_DOMAIN = "capture-stream"

/**
 * La réservation HostChoice du port capture UIKit/tvOS (phase 9, Task 3) — le miroir
 * structurel d'[AndroidHostChoiceReservation] porté à ReplayKit : réserver ne produit
 * AUCUNE frame et n'ALLUME rien ; [start] lit la disponibilité VIVANTE (ruling 6 — un
 * recorder indisponible répond l'échec honnête du seam, `TemporarilyUnavailable`, jamais
 * une raison fabriquée), démarre le recorder (`startCaptureWithHandler`, la forme klib
 * épinglée par le spike), attend la PREMIÈRE frame pour dériver la configuration complète
 * (révision 0, avant tout callback listener — la discipline du miroir Android), puis remet
 * la pompe au collector unique du runtime.
 *
 * **Le confinement** (la règle que la Task 2 réservait à cette task) : le start et le stop
 * tournent sur le fil de l'appelant (aucune exigence de file documentée — ces appels ne
 * présentent aucune UI tant que micro/camera restent désactivés) ; le handler ReplayKit,
 * lui, arrive sur la file interne du recorder — chaque transition d'état du pump est sous
 * [RuntimeLock], la consommation d'un sample est strictement SYNCHRONE dans l'invocation du
 * handler (la fenêtre de validité du buffer natif ne dépasse jamais le handler), et seule
 * la copie Kotlin des octets franchit cette frontière.
 *
 * [close] est idempotent et termine la pompe (`Stopped(Requested)` au listener si personne
 * d'autre ne l'a portée — un runtime déjà en fin de session absorbe le terminal, jamais un
 * double) ; l'arrêt du recorder est EXACTEMENT UNE FOIS quoi qu'il arrive.
 *
 * ATTENTION aux appelants (drivers, harnesses) : [start] attend INDEFINIMENT la complétion
 * ReplayKit ET la première frame — aucun timeout n'est inventé ici (ces faits ne portent
 * aucun pourquoi) ; tout appelant qui ne peut pas se permettre l'attente doit borner la
 * sienne (le pattern des tests de mesure, § mesure du rapport Task 3).
 *
 * @param source le descripteur source détaché (l'affichage entier, nommé par le port).
 * @param streams le seam ReplayKit possédé par le port — jamais fermé par la réservation.
 * @param request la requête admise (la région fut refusée avant la construction).
 * @param closedGate le fait « le port est fermé » — consulté au start (jamais un recorder
 *   vivant derrière un port fermé).
 * @param onEnded la déposition du slot de réservation vivante du port.
 * @param loopContext le contexte de L'UNIQUE boucle de livraison (le miroir Android, fix
 *   round 1) — CHAQUE frame y est livrée, la première garée comme les suivantes.
 */
internal class UIKitHostCaptureReservation(
    override val source: CapturePortSource,
    private val streams: UIKitCaptureSource,
    private val request: CaptureRequest,
    private val closedGate: () -> Boolean,
    private val onEnded: () -> Unit,
    private val loopContext: CoroutineContext = Dispatchers.Default,
) : CapturePortReservation {
    private val lock = RuntimeLock()
    private var closed = false
    private var started = false
    private var pump: UIKitReplayKitPump? = null

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
        // Le port a pu fermer depuis la réservation : jamais un recorder vivant derrière un
        // port fermé (le miroir Android).
        if (closedGate()) return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host))
        // Ruling 6 — la disponibilité est RELUE VIVANTE au start : l'échec honnête du fait
        // du seam (la MÊME forme que la table — isAvailable ne porte aucun pourquoi).
        if (!streams.recorderAvailable()) {
            return KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true))
        }
        val ownedPump = UIKitReplayKitPump(streams, request, closedGate, loopContext)
        lock.withLock { pump = ownedPump }
        // La course du close : la réservation a pu être fermée pendant la construction — la
        // pompe n'a encore RIEN démarré, elle est scellée sans arrêt de recorder.
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
     * Le compteur de diagnostics des samples tombés en silence (gardes refusées, samples
     * non vidéo) — le pattern des compteurs de drops des phases précédentes ; délégation
     * au pump vivant, `0` tant que rien n'a démarré.
     */
    internal fun droppedSampleCount(): Long =
        lock.withLock { pump }?.droppedSampleCount() ?: 0L
}

/**
 * La pompe de frames ReplayKit — le miroir du `AndroidCapturePump` porté au handler
 * ReplayKit : le recorder y est démarré UNE FOIS (`startCaptureWithHandler`), la première
 * frame dérive la configuration complète (révision 0) AVANT tout callback listener, le
 * budget `maxFrameBytes` est vérifié AVANT la copie (la frame surdimensionnée répond un
 * terminal `ResourceLimitExceeded` — zéro octet copié), chaque sample est consommé dans la
 * fenêtre fact+copy (lock read-only → faits → kernel fourcc → budget → LE copie bloc →
 * unlock) et fermé EXACTEMENT UNE FOIS, la reconfiguration est publiée AVANT la frame qui
 * la porte, et les terminaux tombent EXACTEMENT UNE FOIS — `Stopped(Requested)` de l'arrêt
 * propre, `Failed(PlatformFailure("replaykit", …))` des échecs recorder (le spike § 6 : le
 * recorder EST le chemin, pas la source — JAMAIS `SourceLost`, réservé à la surface
 * détachée de la Task 4). Les invocations TARDIVES du handler (après le terminal, ou les
 * extras avant l'installation) tombent en silence et sont COMPTEES ([droppedSampleCount] —
 * le pattern des compteurs de drops des phases précédentes).
 *
 * **UNE SEULE file de livraison** (le moule Android, fix round 1) : le handler ReplayKit ne
 * fait que CONSOMMER (synchronement, dans la fenêtre de validité du buffer natif) et
 * ENFILER la copie Kotlin dans [deliveries] ; la boucle `run` sur [loopContext] est
 * l'UNIQUE chemin vers le listener — l'ordre d'arrivée des samples EST l'ordre de
 * livraison, jamais une paire de frames inversée ni un flip-flop de révision par course
 * entre deux fils. Un silence de garde à la LIVRAISON (le terminal a gagné la course
 * pendant l'attente en file) est absorbé sans compte — le compteur porte les refus de
 * GARDE du handler, pas les courses perdues de la livraison.
 *
 * La mapping d'erreurs du spike § 6 : l'erreur de COMPLETION fait échouer le start
 * (`PlatformFailure(replaykit, start:<code>)`) ; l'erreur du flux VIVANT est un terminal
 * `Outcome(Failed(…))` ; l'erreur d'ARRÊT est un fait diagnostic, jamais un terminal.
 *
 * ATTENTION aux appelants (drivers, harnesses) : le start attend INDEFINIMENT la
 * complétion ReplayKit ET la première frame — aucun timeout n'est inventé ici (le spike :
 * ces faits ne portent aucun pourquoi) ; tout appelant qui ne peut pas se permettre
 * l'attente doit borner la sienne (le pattern des tests de mesure).
 */
internal class UIKitReplayKitPump(
    private val streams: UIKitCaptureSource,
    private val request: CaptureRequest,
    private val closedGate: () -> Boolean,
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
    private var startCompletionSeen = false
    private var startCompletionCode: String? = null
    private var startFailure: KadreFailure? = null
    private var configuration: CaptureConfiguration? = null
    private var revision = 0L
    private var parkedPayload: SamplePayload? = null
    private var scope: CoroutineScope? = null

    /** Le compteur des samples tombés en silence (gardes refusées, samples non vidéo). */
    private var droppedSamples = 0L

    private val startWakeup = Channel<Unit>(Channel.CONFLATED)
    private val firstPayloadArrived = Channel<Unit>(Channel.CONFLATED)

    /**
     * L'UNIQUE file de livraison : le handler enfile les copies consommées, la boucle
     * `run` les livre en FIFO — l'ordre d'arrivée EST l'ordre de livraison (fix round 1).
     * La borne PRATIQUE de l'UNLIMITED : le collector du runtime draine la file à chaque
     * livraison (pas de rétention au-delà), et le budget par frame est vérifié à la
     * CONSOMMATION (avant copie), jamais en file.
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
            // (a) Le démarrage du recorder — la complétion OU le seal d'un close réveille
            // l'attente (le close pendant le start répond par la failure du start).
            streams.startCapture(UIKitHandlerBridge())
            when (val gate = awaitStartCompletion()) {
                is StartGate.Sealed -> return failedStart(gate.failure)

                is StartGate.Completed ->
                    gate.code?.let { code ->
                        return failedStart(
                            KadreFailure.PlatformFailure(KadrePlatform.UIKit, REPLAYKIT_DOMAIN, "start:$code"),
                        )
                    }
            }
            // (a') Le port a pu fermer pendant le démarrage — jamais un flux derrière un
            // port fermé (le miroir Android).
            if (closedGate()) return failedStart(KadreFailure.Closed(KadreResourceKind.Host))
            // (d) La première frame dérive la configuration (révision 0) — le start
            // n'aboutit qu'une fois elle existante, avant tout callback listener.
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
            // L'UNIQUE boucle de livraison (le moule Android) : la première frame d'abord,
            // puis la file FIFO des suivantes — jamais un second fil de livraison.
            pumpScope.launch { run(first) }
            return KadreResult.Success(CapturePortStreamStart(UIKitHostCaptureStream(this), initialConfiguration))
        } catch (cancellation: CancellationException) {
            // Le contrat d'annulation du SPI : libération propre puis surface de
            // l'annulation. Le terminal est scellé d'abord — les samples en vol dans le
            // handler tombent en silence à la garde (le miroir de failedStart).
            lock.withLock { terminated = true }
            release()
            throw cancellation
        }
    }

    /** Le compteur de diagnostics des samples tombés en silence (le pin « counted »). */
    internal fun droppedSampleCount(): Long = lock.withLock { droppedSamples }

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

    // -- le chemin handler (la file interne du recorder) -----------------------------------

    internal fun onSample(sample: UIKitCaptureSample?) {
        if (sample == null) {
            // Un sample NON vidéo (l'audio ReplayKit) — rien à pomper, compté.
            countDropped()
            return
        }
        val decision = lock.withLock {
            when {
                terminated -> SampleDecision.Drop
                !settled -> if (parkedPayload == null) SampleDecision.Park else SampleDecision.Drop
                else -> SampleDecision.Enqueue
            }
        }
        when (decision) {
            SampleDecision.Park -> {
                // La consommation est SYNCHRONE dans l'invocation du handler — seule la
                // copie Kotlin des octets est garée (jamais le jeton natif).
                val payload = consume(sample) ?: return
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

            SampleDecision.Enqueue -> {
                val payload = consume(sample) ?: return
                // L'ENFILEMENT seul — la boucle `run` est l'unique livreur (l'ordre
                // d'arrivée EST l'ordre de livraison, fix round 1).
                deliveries.trySend(payload)
            }

            SampleDecision.Drop -> {
                // Tardif (après le terminal) ou extra (avant l'installation) : le sample est
                // quand même refermé honnêtement, le drop est compté.
                sample.close()
                countDropped()
            }
        }
    }

    // -- la boucle de livraison (l'UNIQUE chemin vers le listener) --------------------------

    /**
     * La boucle du moule Android : la première frame (garée avant l'installation), puis la
     * file FIFO — toutes les livraisons listener sérialisées sur UN fil, jamais une course
     * entre le fil du handler et un livreur parallèle. Un terminal qui gagne la course
     * pendant l'attente en file garde le dernier mot (silence absorbé, sans compte — les
     * refus comptés sont ceux de la GARDE du handler).
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

    // -- la consommation d'UN sample (la fenêtre fact+copy) --------------------------------

    /**
     * La fenêtre fact+copy complète : lock read-only → faits → kernel fourcc → BUDGET →
     * LE copie bloc → unlock (en finally) → close EXACTEMENT UNE FOIS (en finally). `null`
     * = le terminal (ou le refus) a déjà été porté — zéro octet n'a quitté le buffer.
     */
    private fun consume(sample: UIKitCaptureSample): SamplePayload? {
        try {
            val locked = sample.lock()
                ?: return refuse(pipeFailure("pixel-buffer-unreadable"))
            try {
                val layoutPair = uikitCaptureLayoutOfFourcc(
                    width = locked.width,
                    height = locked.height,
                    bytesPerRow = locked.bytesPerRow,
                    cvFormat = locked.cvFormat,
                ) ?: return refuse(pipeFailure("frame-format-out-of-table"))
                val (format, layout) = layoutPair
                // LE BUDGET AVANT LA COPIE (ruling 1) : une frame surdimensionnée répond un
                // terminal ResourceLimitExceeded — la copie n'a jamais tourné.
                if (layout.byteCount.toLong() > maxFrameBytes) {
                    return refuse(
                        KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, maxFrameBytes),
                    )
                }
                val bytes = ByteArray(layout.byteCount)
                try {
                    locked.copyInto(bytes, layout.byteCount)
                } catch (refused: Throwable) {
                    return refuse(pipeFailure("frame-copy-failed"))
                }
                return SamplePayload(
                    width = locked.width,
                    height = locked.height,
                    format = format,
                    layout = layout,
                    bytes = bytes,
                    encoding = uikitCaptureColorEncodingOf(locked.cvFormat),
                    timestampNanos = sample.sourceTimestampNanos,
                )
            } finally {
                sample.unlock()
            }
        } finally {
            sample.close()
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
        if (payload.width != effective.size.width || payload.height != effective.size.height) {
            // La reconfiguration avance d'UNE révision — publiée AVANT la frame qui la porte
            // (la discipline advance-by-one du runtime).
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
                sourceTimestamp = payload.timestampNanos?.takeIf { it >= 0L }?.nanoseconds,
                duration = null,
                discontinuity = null,
                colorEncoding = payload.encoding,
                alphaMode = AlphaMode.Unknown,
                orientation = CaptureOrientation.Upright,
            )
        } catch (invalid: IllegalArgumentException) {
            // Le SPI a rejeté le layout : la frame aurait menti au runtime.
            terminate(CaptureOutcome.Failed(pipeFailure("unexpected-plane-layout")))
            return
        }
        listener?.onFrame(frame)
    }

    private fun configurationFor(
        payload: SamplePayload,
        revisionValue: Long,
    ): CaptureConfiguration = CaptureConfiguration(
        revision = CaptureConfigurationRevision(revisionValue),
        size = PhysicalSize(payload.width, payload.height),
        format = payload.format,
        // L'encodage du KERNEL (le pin du spike § 7 : packed 8-bit RGB → Bt709/Srgb/
        // Identity/Full/None) — jamais inventé au-delà de ce que le fourcc porte.
        colorEncoding = payload.encoding,
        // La composition système n'est pas une promesse d'alpha que le port peut nommer
        // (le miroir exact du HostChoice Android).
        alphaMode = AlphaMode.Unknown,
        orientation = CaptureOrientation.Upright,
        // Les frames arrivent quand le système les produit, sans borne annoncée : Unknown.
        cadence = CaptureCadence.Unknown,
        // La région fut refusée à la réservation : l'affichage entier est la frame.
        region = null,
        cursorMode = request.cursorMode,
    )

    private fun nextRevision(): Long = lock.withLock {
        check(revision < Long.MAX_VALUE) { "capture configuration revision space exhausted" }
        revision += 1
        revision
    }

    // -- l'attente du start ----------------------------------------------------------------

    private sealed interface StartGate {
        data class Completed(val code: String?) : StartGate
        data class Sealed(val failure: KadreFailure) : StartGate
    }

    /**
     * L'attente de la complétion du démarrage — le close et le terminal la réveillent aussi
     * (le canal CONFLATED), donc un close pendant le start répond IMMEDIATEMENT.
     */
    private suspend fun awaitStartCompletion(): StartGate {
        while (true) {
            startWakeup.receiveCatching().getOrNull()
                ?: return StartGate.Sealed(KadreFailure.Closed(KadreResourceKind.CaptureSession))
            val gate = lock.withLock {
                when {
                    terminated -> StartGate.Sealed(
                        startFailure ?: KadreFailure.Closed(KadreResourceKind.CaptureSession),
                    )

                    startCompletionSeen -> StartGate.Completed(startCompletionCode)
                    else -> null
                }
            }
            if (gate != null) return gate
        }
    }

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

    private fun settleStartCompletion(code: String?) {
        val first = lock.withLock {
            if (startCompletionSeen) {
                false
            } else {
                startCompletionSeen = true
                startCompletionCode = code
                true
            }
        }
        if (first) startWakeup.trySend(Unit)
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
        startWakeup.trySend(Unit)
        firstPayloadArrived.trySend(Unit)
        if (!unsettled) listener?.onTerminated(termination)
        lock.withLock { scope }?.cancel()
    }

    /** La verdict du terminal pendant le start, en failure de start (le miroir Android). */
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

    /**
     * La libération : l'arrêt du recorder EXACTEMENT UNE FOIS — l'erreur d'arrêt est un
     * fait diagnostic (le spike § 6), jamais un terminal : le flux s'arrêtait déjà.
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
        // Seulement si le recorder fut réellement démarré (l'admission de la pompe).
        if (lock.withLock { started }) {
            streams.stopCapture { code ->
                // Le fait diagnostic de l'arrêt — le mapping du spike le refuse comme terminal.
            }
        }
        lock.withLock { scope }?.cancel()
    }

    private fun failedStart(failure: KadreFailure): KadreResult.Failure {
        lock.withLock { terminated = true }
        release()
        return KadreResult.Failure(failure)
    }

    private fun countDropped() {
        lock.withLock { droppedSamples += 1 }
    }

    private fun pipeFailure(code: String): KadreFailure =
        KadreFailure.PlatformFailure(KadrePlatform.UIKit, STREAM_DOMAIN, code)

    /** Le pont du handler ReplayKit vers le pump — la forme klib sans aucun type SDK ici. */
    private inner class UIKitHandlerBridge : UIKitCaptureStreamHandler {
        override fun onSample(sample: UIKitCaptureSample?) = this@UIKitReplayKitPump.onSample(sample)

        override fun onStreamError(code: String) {
            // LE pin du spike § 6 : le recorder EST le chemin, pas la source — Failed,
            // JAMAIS SourceLost (réservé à la surface détachée de la Task 4).
            terminate(
                CaptureOutcome.Failed(
                    KadreFailure.PlatformFailure(KadrePlatform.UIKit, REPLAYKIT_DOMAIN, "handler:$code"),
                ),
            )
        }

        override fun onStarted() = settleStartCompletion(null)

        override fun onStartFailed(code: String) = settleStartCompletion(code)
    }

    /** La décision de garde d'UNE invocation du handler. */
    private enum class SampleDecision { Park, Enqueue, Drop }

    /** La matière Kotlin d'UNE frame consommée — le jeton natif est déjà refermé. */
    private class SamplePayload(
        val width: Int,
        val height: Int,
        val format: PixelFormat,
        val layout: PixelPlaneLayout,
        val bytes: ByteArray,
        val encoding: ColorEncoding,
        val timestampNanos: Long?,
    )
}

/**
 * Le flux vivant d'une [UIKitHostCaptureReservation] : le close est l'arrêt propre de
 * l'application — non-bloquant, idempotent, et le chemin exact qui reporte
 * `Stopped(Requested)`.
 */
private class UIKitHostCaptureStream(
    private val pump: UIKitReplayKitPump,
) : CapturePortStream {
    private val lock = RuntimeLock()
    private var closed = false

    override fun close() {
        val first = lock.withLock { if (closed) false else { closed = true; true } }
        if (first) pump.requestStop()
    }
}
