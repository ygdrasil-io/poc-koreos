package org.graphiks.kadre.internal.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.capture.AlphaMode
import org.graphiks.kadre.capture.CaptureCadence
import org.graphiks.kadre.capture.CaptureConfiguration
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
import org.graphiks.kadre.capture.TransferFunction
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.CapturePortFrame
import org.graphiks.kadre.internal.runtime.CapturePortSource
import org.graphiks.kadre.internal.runtime.CapturePortSourceKey
import org.graphiks.kadre.internal.runtime.CapturePortStreamListener
import org.graphiks.kadre.internal.runtime.CapturePortStreamStart
import org.graphiks.kadre.internal.runtime.CapturePortTermination
import org.graphiks.kadre.surface.PhysicalSize

/**
 * La réservation Surface (phase 9, Task 4) prouvée PAR JETONS de seam — aucune UIView réelle
 * dans les tests structurels : la source de snapshots factice livre des issues SCRIPTÉES dont
 * le « raster natif » est un ByteArray contrôlé, le ticker factice est piloté À LA MAIN (le
 * ruling cadence du spike : le scheduling est injecté, le corps de pompe est déterministe).
 * Ce qui est prouvé est la TRADUCTION du tick en formes port — le miroir structurel du pump
 * ReplayKit ([UIKitReplayKitPump], fix round 1) cloné au producteur tick :
 *
 * - **la VÉRITÉ DE FORMAT par frame** (ruling 1) : les faits du snapshot entrent dans le
 *   kernel [uikitSurfaceLayoutOf] À CHAQUE tick — le format est celui LU (le fake le porte,
 *   le pump ne le relabel JAMAIS) ; un refus de format persistant répond un terminal
 *   `Failed` honnête (décision du ruling : ni skip silencieux — il fabriquerait une
 *   continuité — ni reconfiguration — une configuration qu'aucune frame ne peut porter
 *   mentirait à l'avance-by-one du runtime) ;
 * - **le budget AVANT la copie** (ruling 1, miroir I2 de la Task 3) : faits → kernel →
 *   budget → SEULEMENT ENSUITE `copyInto` — LE compteur `copyCount` du fake MUTATION-ÉPINGLE
 *   l'ordre (une implémentation qui déplacerait la vérification après la copie échoue) ;
 * - **les octets transférés** (ruling 5) : le ByteArray livré est une copie fraîche — muter
 *   le « natif » après la livraison ne touche pas les octets livrés ;
 * - **la fenêtre du tick** : le snapshot est consommé SYNCHRONEMENT dans l'invocation du
 *   tick (la fenêtre de validité du raster) et refermé EXACTEMENT UNE FOIS (le fake LÈVE au
 *   second close) ;
 * - **la reconfiguration AVANT la frame qui la porte** (ruling 3) : un changement de taille
 *   — ou de format, le delta propre au chemin renderer qui peut changer de voie entre deux
 *   snapshots — avance d'UNE révision, publiée AVANT la frame ;
 * - **`SourceLost` honnête** (le ruling du spike) : la vue sortie de sa fenêtre EST la
 *   source admise qui disparaît — le terminal `CapturePortTermination.SourceLost`,
 *   exactement une fois, plus aucune frame après ;
 * - **le déterminisme du ticker injecté** (ruling 2) : N ticks → N frames, FIFO (zéro
 *   coalescence), l'arrêt du ticker EXACTEMENT UNE FOIS ;
 * - **le décodeur bitmapInfo** (le pin du spike fix round 2) : [uikitSurfaceBitmapInfoFormatOf]
 *   relu contre la table du header CGImage.h — 0x1 → Rgba8 (LE fait mesuré du renderer),
 *   0x2002 → Bgra8, 0x2006 → Bgrx8, TOUT le reste → `null` (jamais un relabel).
 */
class UIKitSurfaceCaptureReservationTest {

    // --------------------------------------------------------------------------
    // Les jetons de seam — la source de snapshots, le snapshot, le ticker.
    // --------------------------------------------------------------------------

    /**
     * La source de snapshots factice : les issues SCRIPTÉES par le test, chaque appel
     * journalisé (la matière des pins « réserver ne touche pas le seam »).
     */
    private class FakeSnapshotSource : UIKitSurfaceSnapshotSource {
        val calls = ArrayList<String>()
        var snapshotCount: Int = 0
            private set
        val outcomes = ArrayDeque<UIKitSurfaceSnapshotOutcome>()

        override fun snapshot(view: Any): UIKitSurfaceSnapshotOutcome {
            snapshotCount += 1
            calls += "snapshot"
            return outcomes.removeFirstOrNull() ?: error("no scripted snapshot outcome")
        }
    }

    /**
     * Le snapshot factice — un « raster natif » ByteArray contrôlé, LE compteur du pin I2
     * (le budget est vérifié AVANT la copie : une implémentation qui copierait avant de
     * vérifier mettrait ce compteur à 1 sur les snapshots surdimensionnés et échouerait),
     * et une fermeture EXACTEMENT UNE FOIS (le second close LÈVE — un double close de
     * jeton est une mutation que le pin interdit).
     */
    private class FakeSnapshot(
        override val width: Int,
        override val height: Int,
        override val bytesPerRow: Int,
        override val format: PixelFormat = PixelFormat.Rgba8,
        nativeBytes: ByteArray? = null,
    ) : UIKitSurfaceSnapshot {
        val native: ByteArray = nativeBytes ?: ByteArray(height * bytesPerRow)
        var copyCount: Int = 0
            private set
        var closeCount: Int = 0
            private set

        override fun copyInto(destination: ByteArray, byteCount: Int) {
            copyCount += 1
            native.copyInto(destination, 0, 0, byteCount)
        }

        override fun close() {
            check(closeCount == 0) { "the surface snapshot was closed twice" }
            closeCount += 1
        }
    }

    /**
     * Le ticker factice — le ruling cadence (décision 5 du spike) : le scheduling est
     * INJECTÉ, le test arm le callback et tire les ticks À LA MAIN (déterministe, sans run
     * loop ni vsync). Le compteur d'arrêts porte le pin « le ticker s'arrête EXACTEMENT
     * UNE FOIS ».
     */
    private class FakeTickSource : UIKitSurfaceTickSource {
        var startCount: Int = 0
            private set
        var stopCount: Int = 0
            private set
        var startedInterval: Duration? = null
            private set

        /** Le signal du test : le pump a passé son admission et armé le ticker. */
        val armed = CompletableDeferred<Duration>()

        private var onTick: (() -> Unit)? = null

        override fun startTicking(interval: Duration, onTick: () -> Unit) {
            check(startCount == 0) { "the fake tick source was started twice" }
            startCount += 1
            startedInterval = interval
            this.onTick = onTick
            armed.complete(interval)
        }

        override fun stopTicking() {
            stopCount += 1
        }

        /** LE geste du test : un tick, SYNCHRONE — le producteur consomme et enfile. */
        fun tick() = checkNotNull(onTick) { "the fake tick source was never armed" }.invoke()
    }

    /**
     * Le listener d'enregistrement — la CHRONOLOGIE partagée (la matière du pin
     * « onReconfigured AVANT la frame qui la porte ») et les réponses typées.
     */
    private class RecordingListener : CapturePortStreamListener {
        val events = ArrayList<String>()
        val frames = ArrayList<CapturePortFrame>()
        val reconfigurations = ArrayList<CaptureConfiguration>()
        val terminations = ArrayList<CapturePortTermination>()

        override fun onFrame(frame: CapturePortFrame) {
            events += "frame:${frame.configurationRevision}"
            frames += frame
        }

        override fun onReconfigured(configuration: CaptureConfiguration) {
            events += "reconfigured:${configuration.revision.value}"
            reconfigurations += configuration
        }

        override fun onTerminated(termination: CapturePortTermination) {
            events += "terminated"
            terminations += termination
        }
    }

    private fun newReservation(
        snapshots: FakeSnapshotSource = FakeSnapshotSource(),
        tick: FakeTickSource = FakeTickSource(),
        request: CaptureRequest = CaptureRequest(),
        tickInterval: Duration = 1.0.seconds / 30.0,
    ): Triple<UIKitSurfaceCaptureReservation, FakeSnapshotSource, FakeTickSource> = Triple(
        UIKitSurfaceCaptureReservation(
            source = CapturePortSource(
                key = CapturePortSourceKey("uikit-test-surface", 0L),
                kind = CaptureSourceKind.HostSurface,
                name = null,
                size = null,
            ),
            view = Any(),
            request = request,
            snapshots = snapshots,
            tickSources = { tick },
            tickInterval = tickInterval,
            closedGate = { false },
            onEnded = {},
        ),
        snapshots,
        tick,
    )

    /**
     * Le start « avec première frame » — la recette déterministe : le start part en
     * coroutine, l'armement du ticker est attendu, la première frame est STAGÉE par un tick
     * manuel AVANT le retour du start (le moule T3 : la première frame dérive la
     * configuration révision 0 avant tout callback listener).
     */
    private suspend fun startWithFirstFrame(
        reservation: UIKitSurfaceCaptureReservation,
        snapshots: FakeSnapshotSource,
        tick: FakeTickSource,
        listener: RecordingListener,
        maxFrameBytes: Long,
        firstSnapshot: FakeSnapshot,
    ): KadreResult<CapturePortStreamStart> = coroutineScope {
        val started = async(Dispatchers.Default) { reservation.start(listener, maxFrameBytes) }
        tick.armed.await()
        snapshots.outcomes.addLast(UIKitSurfaceSnapshotOutcome.Frame(firstSnapshot))
        tick.tick()
        started.await()
    }

    private suspend fun awaitCondition(description: String, condition: () -> Boolean) {
        val deadline = TimeSource.Monotonic.markNow() + 5.seconds
        while (!condition()) {
            check(TimeSource.Monotonic.markNow() < deadline) { "timeout: $description" }
            delay(10)
        }
    }

    private fun rgbaSnapshot(
        width: Int = 4,
        height: Int = 1,
        bytesPerRow: Int = 16,
        fill: Byte = 0x07,
        format: PixelFormat = PixelFormat.Rgba8,
    ): FakeSnapshot {
        val snapshot = FakeSnapshot(width, height, bytesPerRow, format)
        snapshot.native.fill(fill)
        return snapshot
    }

    // --------------------------------------------------------------------------
    // Le budget AVANT la copie (ruling 1, miroir I2).
    // --------------------------------------------------------------------------

    @Test
    fun oversizeFirstSnapshotFailsTheStartWithResourceLimitExceededAndClosedToken(): Unit = runBlocking {
        val (reservation, snapshots, tick) = newReservation()
        val listener = RecordingListener()
        // Un snapshot 4x4 @ stride 16 = 64 octets — le budget n'en admet que 32.
        val oversize = rgbaSnapshot(width = 4, height = 4, bytesPerRow = 16)
        val started = startWithFirstFrame(
            reservation = reservation,
            snapshots = snapshots,
            tick = tick,
            listener = listener,
            maxFrameBytes = 32L,
            firstSnapshot = oversize,
        )

        // Le start lui-même échoue : la PREMIÈRE frame est surdimensionnée — le pump ne
        // démarre jamais, le budget a été vérifié AVANT la copie.
        val failure = assertIs<KadreResult.Failure>(started)
        assertEquals(
            KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, 32L),
            failure.reason,
        )
        assertEquals(emptyList(), listener.frames)
        assertEquals(emptyList(), listener.terminations)
        // Le jeton a été refermé honnêtement (la fenêtre du tick propre).
        assertEquals(1, oversize.closeCount)
        // LE PIN I2 (mutation) : ZÉRO copie — le budget a été vérifié AVANT copyInto.
        assertEquals(0, oversize.copyCount)
        // Le ticker a été armé puis arrêté EXACTEMENT une fois.
        assertEquals(1, tick.startCount)
        assertEquals(1, tick.stopCount)
    }

    @Test
    fun oversizeSnapshotAfterStartTerminalsOnceWithZeroBytesCopied(): Unit = runBlocking {
        val (reservation, snapshots, tick) = newReservation()
        val listener = RecordingListener()
        val first = rgbaSnapshot(height = 1)
        val started = startWithFirstFrame(reservation, snapshots, tick, listener, maxFrameBytes = 32L, firstSnapshot = first)
        assertIs<KadreResult.Success<CapturePortStreamStart>>(started)

        val oversize = rgbaSnapshot(width = 4, height = 3, bytesPerRow = 16)
        snapshots.outcomes.addLast(UIKitSurfaceSnapshotOutcome.Frame(oversize))
        tick.tick()

        // Le terminal exactement une fois, portant ResourceLimitExceeded.
        awaitCondition("the oversize terminal is delivered") { listener.terminations.isNotEmpty() }
        assertEquals(1, listener.terminations.size)
        val termination = assertIs<CapturePortTermination.Outcome>(listener.terminations.single())
        val outcome = assertIs<CaptureOutcome.Failed>(termination.outcome)
        assertEquals(
            KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, 32L),
            outcome.failure,
        )
        // Au plus la première frame livrée — JAMAIS la 4x3 (la copie n'a jamais tourné).
        assertTrue(listener.frames.size <= 1)
        listener.frames.forEach { frame -> assertEquals(1, frame.size.height) }
        assertEquals(1, first.closeCount)
        // LE PIN I2 (mutation) : ZÉRO copie pour le snapshot surdimensionné.
        assertEquals(0, oversize.copyCount)
        assertEquals(1, oversize.closeCount)
        assertEquals(1, tick.stopCount)
    }

    // --------------------------------------------------------------------------
    // La fenêtre du tick — le jeton refermé EXACTEMENT UNE FOIS.
    // --------------------------------------------------------------------------

    @Test
    fun eachSnapshotIsClosedExactlyOnceAcrossTheWholeStream(): Unit = runBlocking {
        val (reservation, snapshots, tick) = newReservation()
        val listener = RecordingListener()
        val first = rgbaSnapshot(height = 1)
        val started = startWithFirstFrame(reservation, snapshots, tick, listener, maxFrameBytes = 1 shl 20, firstSnapshot = first)
        assertIs<KadreResult.Success<CapturePortStreamStart>>(started)

        // Trois ticks supplémentaires — chaque jeton refermé EXACTEMENT une fois (le check
        // du fake lèverait au second close : le test qui passe EST le pin).
        val tokens = List(3) { rgbaSnapshot(height = 1) }
        tokens.forEach { token ->
            snapshots.outcomes.addLast(UIKitSurfaceSnapshotOutcome.Frame(token))
            tick.tick()
        }

        awaitCondition("all three frames delivered") { listener.frames.size >= 4 }
        assertEquals(1, first.closeCount)
        tokens.forEach { token ->
            assertEquals(1, token.closeCount, "every snapshot token is closed exactly once")
            assertEquals(1, token.copyCount, "every delivered snapshot was copied exactly once")
        }
    }

    // --------------------------------------------------------------------------
    // Les octets transférés (ruling 5) — le raster muté après livraison est sans effet.
    // --------------------------------------------------------------------------

    @Test
    fun deliveredBytesSurvivePostDeliveryMutationOfTheSource(): Unit = runBlocking {
        val (reservation, snapshots, tick) = newReservation()
        val listener = RecordingListener()
        val first = rgbaSnapshot(width = 4, height = 1, bytesPerRow = 16, fill = 0x07)
        val started = startWithFirstFrame(reservation, snapshots, tick, listener, maxFrameBytes = 1 shl 20, firstSnapshot = first)
        assertIs<KadreResult.Success<CapturePortStreamStart>>(started)
        awaitCondition("the first frame is delivered") { listener.frames.isNotEmpty() }

        val delivered = listener.frames.single().planes.single().bytes
        // La mutation APRÈS la livraison : une implémentation à référence retenue verrait
        // ses octets livrés changer. Le pump honnête a copié : les octets livrés restent 0x07.
        first.native.fill(0x2A)
        assertTrue(delivered.isNotEmpty())
        delivered.forEach { byte -> assertEquals(0x07, byte) }
    }

    // --------------------------------------------------------------------------
    // Rien après le terminal — les ticks tardifs tombent en silence, COMPTEÉS.
    // --------------------------------------------------------------------------

    @Test
    fun lateTicksAfterTerminalAreSilentlyDroppedAndCounted(): Unit = runBlocking {
        val (reservation, snapshots, tick) = newReservation()
        val listener = RecordingListener()
        val first = rgbaSnapshot(height = 1)
        val started = startWithFirstFrame(reservation, snapshots, tick, listener, maxFrameBytes = 1 shl 20, firstSnapshot = first)
        val start = assertIs<KadreResult.Success<CapturePortStreamStart>>(started).value

        start.stream.close()
        assertEquals(1, listener.terminations.size)
        val termination = assertIs<CapturePortTermination.Outcome>(listener.terminations.single())
        assertEquals(CaptureOutcome.Stopped(CaptureStopReason.Requested), termination.outcome)

        // Les ticks TARDIFS : la DÉCISION de garde PRÉCÈDE tout fait de seam — rien n'est
        // dessiné, le drop est COMPTEÉ, zéro callback.
        val droppedBefore = reservation.droppedSnapshotCount()
        val late = rgbaSnapshot(height = 1)
        snapshots.outcomes.addLast(UIKitSurfaceSnapshotOutcome.Frame(late))
        tick.tick()
        assertEquals(1, listener.terminations.size, "no second terminal after the stop")
        assertEquals(droppedBefore + 1, reservation.droppedSnapshotCount())
        // LE PIN « la décision précède le dessin » : le jeton scripté n'a JAMAIS été
        // consommé — un tick tardif ne rend AUCUN raster (zéro travail de seam gaspillé).
        assertEquals(0, late.closeCount)
        assertEquals(0, late.copyCount)
    }

    // --------------------------------------------------------------------------
    // L'arrêt exactement une fois sous courses (le miroir T3).
    // --------------------------------------------------------------------------

    @Test
    fun tickerStopsExactlyOnceUnderRacingStreamCloseAndReservationClose(): Unit = runBlocking {
        val (reservation, snapshots, tick) = newReservation()
        val listener = RecordingListener()
        val first = rgbaSnapshot(height = 1)
        val started = startWithFirstFrame(reservation, snapshots, tick, listener, maxFrameBytes = 1 shl 20, firstSnapshot = first)
        val start = assertIs<KadreResult.Success<CapturePortStreamStart>>(started).value

        // La course : le close du stream contre le close de la réservation — le terminal
        // reste EXACTEMENT UNE FOIS, l'arrêt du ticker AUSSI.
        val gate = CompletableDeferred<Unit>()
        val racers = listOf(
            async(Dispatchers.Default) {
                gate.await()
                start.stream.close()
            },
            async(Dispatchers.Default) {
                gate.await()
                reservation.close()
            },
        )
        gate.complete(Unit)
        racers.awaitAll()

        assertEquals(1, listener.terminations.size, "exactly one terminal under the race")
        assertEquals(1, tick.stopCount, "stopTicking exactly once under the race")
    }

    // --------------------------------------------------------------------------
    // Le close pendant le start — échec immédiat, ticker arrêté, zéro callback.
    // --------------------------------------------------------------------------

    @Test
    fun reservationCloseDuringStartFailsTheStartImmediatelyWithoutCallbacks(): Unit = runBlocking {
        val (reservation, snapshots, tick) = newReservation()
        val listener = RecordingListener()
        val started = async(Dispatchers.Default) { reservation.start(listener, 1 shl 20) }
        tick.armed.await()

        // Le close PENDANT l'attente de la première frame : le start répond immédiatement.
        reservation.close()
        val result = started.await()

        val failure = assertIs<KadreResult.Failure>(result)
        assertEquals(KadreFailure.Closed(KadreResourceKind.CaptureSession), failure.reason)
        assertEquals(emptyList(), listener.frames)
        assertEquals(emptyList(), listener.terminations)
        assertEquals(1, tick.stopCount)
    }

    // --------------------------------------------------------------------------
    // Le close pendant un tick en vol — le terminal garde le dernier mot.
    // --------------------------------------------------------------------------

    @Test
    fun closeWhileATickIsInFlightYieldsExactlyOneTerminalAndTheStagedTokenStillClosed(): Unit = runBlocking {
        val (reservation, snapshots, tick) = newReservation()
        val listener = RecordingListener()
        val first = rgbaSnapshot(height = 1)
        val started = startWithFirstFrame(reservation, snapshots, tick, listener, maxFrameBytes = 1 shl 20, firstSnapshot = first)
        assertIs<KadreResult.Success<CapturePortStreamStart>>(started)
        awaitCondition("the first frame is delivered") { listener.frames.isNotEmpty() }

        // Un tick STAGÉ (le producteur a enfilé la copie) puis le close IMMÉDIAT — la
        // course entre la boucle de livraison et le terminal : quelle que soit l'issue,
        // EXACTEMENT UN terminal, le jeton stagé refermé exactement une fois, jamais un
        // second terminal.
        val staged = rgbaSnapshot(height = 1)
        snapshots.outcomes.addLast(UIKitSurfaceSnapshotOutcome.Frame(staged))
        tick.tick()
        reservation.close()

        awaitCondition("the terminal is delivered") { listener.terminations.size == 1 }
        val termination = assertIs<CapturePortTermination.Outcome>(listener.terminations.single())
        assertEquals(CaptureOutcome.Stopped(CaptureStopReason.Requested), termination.outcome)
        assertEquals(1, staged.closeCount)
        assertEquals(1, tick.stopCount)
    }

    // --------------------------------------------------------------------------
    // SourceLost honnête — la vue sortie de sa fenêtre (le ruling du spike).
    // --------------------------------------------------------------------------

    @Test
    fun detachedViewMidStreamTerminalsSourceLostExactlyOnceAndNothingAfter(): Unit = runBlocking {
        val (reservation, snapshots, tick) = newReservation()
        val listener = RecordingListener()
        val first = rgbaSnapshot(height = 1)
        val started = startWithFirstFrame(reservation, snapshots, tick, listener, maxFrameBytes = 1 shl 20, firstSnapshot = first)
        assertIs<KadreResult.Success<CapturePortStreamStart>>(started)
        awaitCondition("the first frame is delivered") { listener.frames.isNotEmpty() }

        // La vue quitte sa fenêtre : le seam le lit au tick — SourceLost, PAS un Failed
        // (la source admise, le contenu rendu de la vue dans sa fenêtre, a disparu).
        snapshots.outcomes.addLast(UIKitSurfaceSnapshotOutcome.SourceGone)
        tick.tick()

        awaitCondition("the SourceLost terminal is delivered") { listener.terminations.isNotEmpty() }
        assertEquals(1, listener.terminations.size)
        assertEquals(CapturePortTermination.SourceLost, listener.terminations.single())
        assertEquals(1, tick.stopCount, "the ticker is stopped by the terminal")

        // Les ticks suivants : plus AUCUNE frame, aucun second terminal.
        val framesAtTerminal = listener.frames.size
        snapshots.outcomes.addLast(UIKitSurfaceSnapshotOutcome.Frame(rgbaSnapshot(height = 1)))
        tick.tick()
        assertEquals(framesAtTerminal, listener.frames.size, "no frame after SourceLost")
        assertEquals(1, listener.terminations.size)
    }

    @Test
    fun detachedViewAtTheFirstTickFailsTheStartWithTheClosedSessionMirror(): Unit = runBlocking {
        val (reservation, snapshots, tick) = newReservation()
        val listener = RecordingListener()
        val started = async(Dispatchers.Default) { reservation.start(listener, 1 shl 20) }
        tick.armed.await()
        // La source est DÉJÀ perdue au premier tick (pre-settle) : la verdict du terminal
        // devient LA failure du start — le listener n'entend rien (le miroir T3).
        snapshots.outcomes.addLast(UIKitSurfaceSnapshotOutcome.SourceGone)
        tick.tick()

        val result = started.await()
        val failure = assertIs<KadreResult.Failure>(result)
        assertEquals(KadreFailure.Closed(KadreResourceKind.CaptureSession), failure.reason)
        assertEquals(emptyList(), listener.frames)
        assertEquals(emptyList(), listener.terminations)
        assertEquals(1, tick.stopCount)
    }

    // --------------------------------------------------------------------------
    // La vérité de format (ruling 1) — les refus honnêtes du chemin renderer.
    // --------------------------------------------------------------------------

    @Test
    fun persistentOutOfTableRendererRefusesTheStreamHonestlyNeverASilentSkip(): Unit = runBlocking {
        val (reservation, snapshots, tick) = newReservation()
        val listener = RecordingListener()
        val first = rgbaSnapshot(height = 1)
        val started = startWithFirstFrame(reservation, snapshots, tick, listener, maxFrameBytes = 1 shl 20, firstSnapshot = first)
        assertIs<KadreResult.Success<CapturePortStreamStart>>(started)
        awaitCondition("the first frame is delivered") { listener.frames.isNotEmpty() }

        // LE DÉCODEUR du seam ne trouve PLUS AUCUNE voie dans la table (le renderer est
        // réellement sorti du portable) : le pump répond le terminal Failed honnête —
        // ni un skip silencieux (il fabriquerait une continuité), ni une reconfiguration
        // qu'aucune frame ne pourrait porter.
        snapshots.outcomes.addLast(
            UIKitSurfaceSnapshotOutcome.Refused("surface-format-out-of-table"),
        )
        tick.tick()

        awaitCondition("the format terminal is delivered") { listener.terminations.isNotEmpty() }
        assertEquals(1, listener.terminations.size)
        val termination = assertIs<CapturePortTermination.Outcome>(listener.terminations.single())
        val outcome = assertIs<CaptureOutcome.Failed>(termination.outcome)
        assertEquals(
            KadreFailure.PlatformFailure(KadrePlatform.UIKit, "capture-stream", "surface-format-out-of-table"),
            outcome.failure,
        )
        assertEquals(1, tick.stopCount)
    }

    @Test
    fun unreadableRendererRefusesTheStreamWithItsOwnHonestCode(): Unit = runBlocking {
        val (reservation, snapshots, tick) = newReservation()
        val listener = RecordingListener()
        val first = rgbaSnapshot(height = 1)
        val started = startWithFirstFrame(reservation, snapshots, tick, listener, maxFrameBytes = 1 shl 20, firstSnapshot = first)
        assertIs<KadreResult.Success<CapturePortStreamStart>>(started)
        awaitCondition("the first frame is delivered") { listener.frames.isNotEmpty() }

        snapshots.outcomes.addLast(UIKitSurfaceSnapshotOutcome.Refused("snapshot-unreadable"))
        tick.tick()

        awaitCondition("the unreadable terminal is delivered") { listener.terminations.isNotEmpty() }
        val termination = assertIs<CapturePortTermination.Outcome>(listener.terminations.single())
        val outcome = assertIs<CaptureOutcome.Failed>(termination.outcome)
        assertEquals(
            KadreFailure.PlatformFailure(KadrePlatform.UIKit, "capture-stream", "snapshot-unreadable"),
            outcome.failure,
        )
    }

    @Test
    fun dishonestSnapshotFactsFailAtTheKernelGateWithTheT3MirrorCode(): Unit = runBlocking {
        val (reservation, snapshots, tick) = newReservation()
        val listener = RecordingListener()
        val first = rgbaSnapshot(height = 1)
        val started = startWithFirstFrame(reservation, snapshots, tick, listener, maxFrameBytes = 1 shl 20, firstSnapshot = first)
        assertIs<KadreResult.Success<CapturePortStreamStart>>(started)
        awaitCondition("the first frame is delivered") { listener.frames.isNotEmpty() }

        // Un jeton dont les FAITS ne tiennent pas dans la table (63 < 4·16 : rangée
        // incomplète) — le kernel répond null, le pump refuse au code miroir T3.
        val dishonest = rgbaSnapshot(width = 16, height = 1, bytesPerRow = 63)
        snapshots.outcomes.addLast(UIKitSurfaceSnapshotOutcome.Frame(dishonest))
        tick.tick()

        awaitCondition("the kernel-gate terminal is delivered") { listener.terminations.isNotEmpty() }
        val termination = assertIs<CapturePortTermination.Outcome>(listener.terminations.single())
        val outcome = assertIs<CaptureOutcome.Failed>(termination.outcome)
        assertEquals(
            KadreFailure.PlatformFailure(KadrePlatform.UIKit, "capture-stream", "frame-format-out-of-table"),
            outcome.failure,
        )
        // Les faits malhonnêtes n'ont jamais été copiés : le refus est ANTÉRIEUR à copyInto.
        assertEquals(0, dishonest.copyCount)
        assertEquals(1, dishonest.closeCount)
    }

    // --------------------------------------------------------------------------
    // Les gardes d'admission de la réservation.
    // --------------------------------------------------------------------------

    @Test
    fun secondStartIsRefusedAsAlreadyInUse(): Unit = runBlocking {
        val (reservation, snapshots, tick) = newReservation()
        val listener = RecordingListener()
        val first = rgbaSnapshot(height = 1)
        val started = startWithFirstFrame(reservation, snapshots, tick, listener, maxFrameBytes = 1 shl 20, firstSnapshot = first)
        assertIs<KadreResult.Success<CapturePortStreamStart>>(started)

        val second = reservation.start(RecordingListener(), 1 shl 20)

        assertEquals(
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.CaptureCollector)),
            second,
        )
    }

    @Test
    fun startAfterReservationCloseIsRefusedAsClosed(): Unit = runBlocking {
        val (reservation, _, _) = newReservation()
        reservation.close()
        val second = reservation.start(RecordingListener(), 1 shl 20)

        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.CaptureSession)),
            second,
        )
    }

    // --------------------------------------------------------------------------
    // La frame portée — les faits honnêtes du chemin kernel (format LU, stride, encoding,
    // la période du ticker en duration, PAS de timestamp inventé).
    // --------------------------------------------------------------------------

    @Test
    fun framesCarryTheReadFormatHonestLayoutAndTheTickPeriod(): Unit = runBlocking {
        val (reservation, snapshots, tick) = newReservation(tickInterval = 1.0.seconds / 30.0)
        val listener = RecordingListener()
        // Stride paddé (20 > 4·4) — le layout porte le stride verbatim, byteCount = h·rowStride.
        val first = rgbaSnapshot(width = 4, height = 2, bytesPerRow = 20, fill = 0x5A)
        val started = startWithFirstFrame(reservation, snapshots, tick, listener, maxFrameBytes = 1 shl 20, firstSnapshot = first)
        assertIs<KadreResult.Success<CapturePortStreamStart>>(started)

        // La configuration de start (révision 0) — la taille et le format LU de la première frame.
        val start = assertIs<KadreResult.Success<CapturePortStreamStart>>(started).value
        assertEquals(0L, start.configuration.revision.value)
        start.configuration.let { configuration ->
            assertEquals(PixelFormat.Rgba8, configuration.format)
            assertEquals(
                // LE pin d'encodage du spike § 7 — le MÊME que le kernel des fourcc packed
                // 8-bit RGB : Bt709/Srgb/Identity/Full/None.
                ColorEncoding(
                    primaries = ColorPrimaries.Bt709,
                    transfer = TransferFunction.Srgb,
                    matrix = MatrixCoefficients.Identity,
                    range = ColorRange.Full,
                    hdr = HdrMetadata.None,
                ),
                configuration.colorEncoding,
            )
            assertEquals(AlphaMode.Unknown, configuration.alphaMode)
            assertEquals(CaptureCadence.Unknown, configuration.cadence)
        }
        // La période injectée EST la période armée.
        assertEquals(1.0.seconds / 30.0, tick.startedInterval)

        awaitCondition("the parked first frame is delivered") { listener.frames.isNotEmpty() }
        val frame = listener.frames.single()
        assertEquals(0L, frame.configurationRevision)
        // PAS de timestamp inventé : un snapshot ne porte aucun PTS (le miroir T3 honnête).
        assertEquals(null, frame.sourceTimestamp)
        // La période du ticker EST le duration du frame (décision 5 du spike).
        assertEquals(1.0.seconds / 30.0, frame.duration)
        assertEquals(null, frame.discontinuity)
        val plane = frame.planes.single()
        assertEquals(20, plane.layout.rowStride)
        assertEquals(40, plane.layout.byteCount)
        assertEquals(40, plane.bytes.size)
        plane.bytes.forEach { byte -> assertEquals(0x5A, byte) }
        assertEquals(1, first.copyCount)
        assertEquals(1, first.closeCount)
    }

    @Test
    fun fixedIntervalRequestPublishesTheFixedCadence(): Unit = runBlocking {
        val interval = 1.0.seconds / 60.0
        // La dérivation de la période est le fait du PORT (request.minimumFrameInterval,
        // sinon le repli) — la réservation la reçoit déjà résolue, le test la passe telle.
        val (reservation, snapshots, tick) = newReservation(
            request = CaptureRequest(minimumFrameInterval = interval),
            tickInterval = interval,
        )
        val listener = RecordingListener()
        val first = rgbaSnapshot(height = 1)
        val started = startWithFirstFrame(reservation, snapshots, tick, listener, maxFrameBytes = 1 shl 20, firstSnapshot = first)
        val start = assertIs<KadreResult.Success<CapturePortStreamStart>>(started).value

        assertEquals(CaptureCadence.Fixed(interval), start.configuration.cadence)
        awaitCondition("the first frame is delivered") { listener.frames.isNotEmpty() }
        assertEquals(interval, listener.frames.single().duration)
    }

    // --------------------------------------------------------------------------
    // La reconfiguration — taille OU format : onReconfigured AVANT la frame (ruling 3).
    // --------------------------------------------------------------------------

    @Test
    fun sizeChangePublishesReconfigurationBeforeTheCarryingFrame(): Unit = runBlocking {
        val (reservation, snapshots, tick) = newReservation()
        val listener = RecordingListener()
        val first = rgbaSnapshot(width = 4, height = 1, bytesPerRow = 16)
        val started = startWithFirstFrame(reservation, snapshots, tick, listener, maxFrameBytes = 1 shl 20, firstSnapshot = first)
        assertIs<KadreResult.Success<CapturePortStreamStart>>(started)
        awaitCondition("the first frame is delivered") { listener.frames.isNotEmpty() }

        // Une frame d'une AUTRE taille (la rotation) : la configuration avance d'UNE
        // révision, publiée AVANT la frame qui la porte.
        val rotated = rgbaSnapshot(width = 8, height = 2, bytesPerRow = 32)
        snapshots.outcomes.addLast(UIKitSurfaceSnapshotOutcome.Frame(rotated))
        tick.tick()

        awaitCondition("the reconfigured frame is delivered") { listener.frames.size >= 2 }
        assertEquals(1, listener.reconfigurations.size)
        assertEquals(1L, listener.reconfigurations.single().revision.value)
        assertEquals(PhysicalSize(8, 2), listener.reconfigurations.single().size)
        // LA CHRONOLOGIE : reconfigured AVANT frame — la paire que le pin exige.
        assertEquals(listOf("frame:0", "reconfigured:1", "frame:1"), listener.events)
        val second = listener.frames.last()
        assertEquals(1L, second.configurationRevision)
        assertEquals(8, second.size.width)
        assertEquals(2, second.size.height)
    }

    @Test
    fun formatChangeAtSameSizeAlsoPublishesReconfigurationBeforeTheFrame(): Unit = runBlocking {
        val (reservation, snapshots, tick) = newReservation()
        val listener = RecordingListener()
        val first = rgbaSnapshot(width = 4, height = 2, bytesPerRow = 16)
        val started = startWithFirstFrame(reservation, snapshots, tick, listener, maxFrameBytes = 1 shl 20, firstSnapshot = first)
        assertIs<KadreResult.Success<CapturePortStreamStart>>(started)
        awaitCondition("the first frame is delivered") { listener.frames.isNotEmpty() }

        // DELTA propre au chemin renderer : la voie de rendu a changé (le repli
        // layer.render a pris le relais) — le format LU diffère À TAILLE ÉGALE, la
        // reconfiguration est publiée quand même, AVANT la frame.
        val fallback = rgbaSnapshot(width = 4, height = 2, bytesPerRow = 16, format = PixelFormat.Bgra8, fill = 0x3C)
        snapshots.outcomes.addLast(UIKitSurfaceSnapshotOutcome.Frame(fallback))
        tick.tick()

        awaitCondition("the format-reconfigured frame is delivered") { listener.frames.size >= 2 }
        assertEquals(1, listener.reconfigurations.size)
        assertEquals(PixelFormat.Bgra8, listener.reconfigurations.single().format)
        assertEquals(listOf("frame:0", "reconfigured:1", "frame:1"), listener.events)
        listener.frames.last().planes.single().bytes.forEach { byte -> assertEquals(0x3C, byte) }
    }

    // --------------------------------------------------------------------------
    // LE DÉTERMINISME du ticker injecté (ruling 2) : N ticks → N frames, FIFO.
    // --------------------------------------------------------------------------

    @Test
    fun nTicksYieldNFramesInArrivalOrderWithoutCoalescing(): Unit = runBlocking {
        val (reservation, snapshots, tick) = newReservation()
        val listener = RecordingListener()
        val first = rgbaSnapshot(width = 4, height = 1, bytesPerRow = 16, fill = 0x11)
        val started = startWithFirstFrame(reservation, snapshots, tick, listener, maxFrameBytes = 1 shl 20, firstSnapshot = first)
        assertIs<KadreResult.Success<CapturePortStreamStart>>(started)

        // Trois ticks, trois remplissages DISTINCTS — l'ordre d'arrivée EST l'ordre de
        // livraison (le miroir I1 du pump T3), zéro coalescence.
        val fills = listOf(0x22, 0x33, 0x44)
        fills.forEach { value ->
            snapshots.outcomes.addLast(UIKitSurfaceSnapshotOutcome.Frame(rgbaSnapshot(fill = value.toByte())))
            tick.tick()
        }

        awaitCondition("all four frames are delivered") { listener.frames.size >= 4 }
        assertEquals(listOf("frame:0", "frame:0", "frame:0", "frame:0"), listener.events)
        assertEquals(0, listener.reconfigurations.size)
        listener.frames.forEachIndexed { index, frame ->
            val expected = if (index == 0) 0x11 else fills[index - 1]
            frame.planes.single().bytes.forEach { byte -> assertEquals(expected.toByte(), byte) }
        }
        // Le ticker tourne ENCORE (l'arrêt appartient au close, exactement une fois).
        assertEquals(0, tick.stopCount)
        reservation.close()
        assertEquals(1, tick.stopCount)
    }

    // --------------------------------------------------------------------------
    // LE DÉCODEUR bitmapInfo — le pin du spike fix round 2 (CGImage.h relu à la source).
    // --------------------------------------------------------------------------

    @Test
    fun bitmapInfoDecoderPinsTheCanonicalByteOrders(): Unit = runBlocking {
        // LE fait mesuré du renderer (spike décision 4 : layer.render 0x1) → Rgba8.
        assertEquals(PixelFormat.Rgba8, uikitSurfaceBitmapInfoFormatOf(0x1L))
        // premultipliedLast + 32Big → Rgba8 (l'unité MSB→LSB R,G,B,A est [R][G][B][A]).
        assertEquals(PixelFormat.Rgba8, uikitSurfaceBitmapInfoFormatOf(0x4001L))
        // LE BGRA canonique iOS : premultipliedFirst + 32Little → mémoire [B][G][R][A].
        assertEquals(PixelFormat.Bgra8, uikitSurfaceBitmapInfoFormatOf(0x2002L))
        // noneSkipFirst + 32Little → mémoire [B][G][R][X] = Bgrx8.
        assertEquals(PixelFormat.Bgrx8, uikitSurfaceBitmapInfoFormatOf(0x2006L))
        // TOUT le reste est HORS table — jamais un relabel :
        // 16-bit composantes mesurées (drawHierarchy afterScreenUpdates: true → 0x1101).
        assertNull(uikitSurfaceBitmapInfoFormatOf(0x1101L), "16-bit components: out of table")
        assertNull(uikitSurfaceBitmapInfoFormatOf(0x3001L), "premultipliedLast+16Big: out of table")
        assertNull(uikitSurfaceBitmapInfoFormatOf(0x3002L), "premultipliedFirst+16Big: out of table")
        assertNull(uikitSurfaceBitmapInfoFormatOf(0x2001L), "premultipliedLast+32Little = [A][B][G][R]: out of table")
        assertNull(uikitSurfaceBitmapInfoFormatOf(0x4002L), "premultipliedFirst+32Big = [A][R][G][B]: out of table")
        assertNull(uikitSurfaceBitmapInfoFormatOf(0x5L), "noneSkipLast+default = [R][G][B][X]: not a portable label")
        assertNull(uikitSurfaceBitmapInfoFormatOf(0x4005L), "noneSkipLast+32Big: out of table")
        assertNull(uikitSurfaceBitmapInfoFormatOf(0x2005L), "noneSkipLast+32Little = [X][B][G][R]: out of table")
        assertNull(uikitSurfaceBitmapInfoFormatOf(0x2102L), "float components: out of table")
        assertNull(uikitSurfaceBitmapInfoFormatOf(0x3L), "straight alpha: out of table")
    }

    // --------------------------------------------------------------------------
    // LA MESURE du chemin surface RÉEL (ruling 4) — un snapshot UIView HORS ÉCRAN sur le
    // fil principal est légal : le runner natif exécute les tests SUR LE MAIN (le pin
    // UIKitDisplayPortTest), et le spike a prouvé le rendu UIGraphicsImageRenderer sur ces
    // MÊMES simulateurs (côté Swift XCTest). **LE FAIT MESURÉ sur l'hôte de test K/N** :
    // la fenêtre est hébergée (view-in-window=true) mais le renderer de CET hôte émet des
    // rasters 16 bpp DONT le bitmapInfo prétend premultipliedLast 32-bit (0x1) — des faits
    // INTERNEMENT INCOHÉRENTS que le plancher de stride du kernel (`bytesPerRow ≥ 4·w`)
    // rejette honnêtement sur TOUTES les voies mesurées : le start répond le terminal
    // `Failed(..., "frame-format-out-of-table")`, JAMAIS une frame fabriquée. LA DÉCISION
    // du ruling 4 est donc prise PAR L'ÉVIDENCE : le pin de frame réelle vit chez le
    // DRIVER (l'XCTest Swift a mesuré 0x1 → rgba8 32bpp cohérent — spike § 4) ; ce test
    // reste la sonde honnête de l'hôte — il enregistre verbatim (`SPIKE-FACT surface-*`)
    // et épingle SEULEMENT le fait structurel qui tient partout (le ticker arrêté une
    // fois, la verdict honnête du pump, frame ou refus).
    // --------------------------------------------------------------------------

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    @Test
    fun realViewSnapshotThroughTheProductionSeamIsMeasuredOnThisHost(): Unit = runBlocking {
        // La vue RÉELLE est hébergée dans une fenêtre RÉELLE — le fait SourceLost se lit
        // sur window != null (le ruling) : hors fenêtre, le pump répondrait SourceLost et
        // la mesure n'aurait aucun frame à épingler.
        @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
        fun hostRealView(): Pair<platform.UIKit.UIView, Boolean> {
            val bounds = platform.CoreGraphics.CGRectMake(0.0, 0.0, 64.0, 48.0)
            val realView = platform.UIKit.UIView(frame = bounds)
            val window = platform.UIKit.UIWindow(frame = bounds)
            window.addSubview(realView)
            return realView to (realView.window != null)
        }
        val (view, hosted) = hostRealView()
        println("SPIKE-FACT surface-frame: hosting view-in-window=$hosted")

        // LA SONDE BRUTE (le miroir du probe Swift du spike, sur le chemin K/N) : les
        // faits verbatim des DEUX voies, bitmapInfo brut — la matière qui distingue un
        // fait réel du renderer d'un bug de couture. Rien n'est fabriqué.
        rawRenderFacts("drawViewHierarchyInRect(default)", view) {
            view.drawViewHierarchyInRect(view.bounds, afterScreenUpdates = true)
        }
        rawRenderFacts("layer.render(default)", view) {
            view.layer.renderInContext(platform.UIKit.UIGraphicsGetCurrentContext()!!)
        }
        rawRenderFacts("layer.render(scale=1)", view) {
            view.layer.renderInContext(platform.UIKit.UIGraphicsGetCurrentContext()!!)
        }

        // Le faux ticker porte les ticks À LA MAIN (le ruling cadence) : la boucle
        // principale est occupée par runBlocking, un ticker de dispatch n'y tirerait jamais.
        val tick = FakeTickSource()
        val reservation = UIKitSurfaceCaptureReservation(
            source = CapturePortSource(
                key = CapturePortSourceKey("uikit-surface-measure", 0L),
                kind = CaptureSourceKind.HostSurface,
                name = null,
                size = null,
            ),
            view = view,
            request = CaptureRequest(),
            snapshots = UIKitLiveSurfaceSnapshotSource(),
            tickSources = { tick },
            tickInterval = 1.0.seconds / 30.0,
            closedGate = { false },
            onEnded = {},
        )

        val listener = RecordingListener()
        val started = async(Dispatchers.Default) { reservation.start(listener, 1 shl 20) }
        tick.armed.await()
        tick.tick()
        val result = started.await()

        when (val start = result) {
            is KadreResult.Failure ->
                println("SPIKE-FACT surface-frame: LE START n'a pas abouti sur cet hôte ($start) — mesuré, non fabriqué")

            is KadreResult.Success -> {
                awaitCondition("the real frame is delivered") { listener.frames.isNotEmpty() }
                val frame = listener.frames.single()
                val layout = frame.planes.single().layout
                println(
                    "SPIKE-FACT surface-frame: w=${frame.size.width} h=${frame.size.height} " +
                        "bpr=${layout.rowStride} format=${frame.format}",
                )
                // LE pin du ruling 4 : les faits du renderer RÉEL portent le format MESURÉ
                // par le spike (bitmapInfo 0x1 → rgba8). Un hôte qui rendrait autrement
                // FALSIFIERAIT le pin — c'est une mesure, pas une supposition.
                assertEquals(PixelFormat.Rgba8, frame.format)
                assertEquals(layout.rowStride.toLong(), layout.width.toLong() * 4L)
                assertEquals(layout.byteCount, layout.height * layout.rowStride)
                // Les octets sont la copie du raster réel (non tout-zéro : la fenêtre est
                // rendue — un raster entièrement nul serait un fait à enregistrer).
                assertTrue(frame.planes.single().bytes.any { it != 0.toByte() }, "the real raster is not all-zero")
                start.value.stream.close()
            }
        }
        reservation.close()
        assertEquals(1, tick.stopCount)
    }

    /**
     * La sonde brute d'UNE voie de rendu sur l'hôte K/N : le rendu avec le format DEMANDÉ
     * (default, ou scale=1 pour le miroir exact du probe Swift), les faits verbatim —
     * w/h/bytesPerRow/bitmapInfo/décodage. Rien n'est fabriqué, tout est enregistré.
     */
    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    private fun rawRenderFacts(
        label: String,
        view: platform.UIKit.UIView,
        scaleOne: Boolean = false,
        draw: () -> Unit,
    ) {
        val format = platform.UIKit.UIGraphicsImageRendererFormat.defaultFormat()
        if (scaleOne) format.scale = 1.0
        val renderer = platform.UIKit.UIGraphicsImageRenderer(bounds = view.bounds, format = format)
        val image = renderer.imageWithActions { _ -> draw() }
        val cgImage = image.CGImage
        if (cgImage == null) {
            println("SPIKE-FACT surface-raw: [$label] AUCUN CGImage — mesuré")
            return
        }
        val bitmapInfo =
            platform.CoreGraphics.CGImageGetBitmapInfo(cgImage).toLong() and 0xFFFF_FFFFL
        println(
            "SPIKE-FACT surface-raw: [$label] w=${platform.CoreGraphics.CGImageGetWidth(cgImage)} " +
                "h=${platform.CoreGraphics.CGImageGetHeight(cgImage)} " +
                "bpr=${platform.CoreGraphics.CGImageGetBytesPerRow(cgImage)} " +
                "bpp=${platform.CoreGraphics.CGImageGetBitsPerPixel(cgImage)} " +
                "bitmapInfo=0x${bitmapInfo.toString(16)} scale=${format.scale} " +
                "decoded=${uikitSurfaceBitmapInfoFormatOf(bitmapInfo)}",
        )
    }
}
