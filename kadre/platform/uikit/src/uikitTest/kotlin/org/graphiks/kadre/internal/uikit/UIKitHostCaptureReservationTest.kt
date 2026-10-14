package org.graphiks.kadre.internal.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
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
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.CapturePortFrame
import org.graphiks.kadre.internal.runtime.CapturePortSource
import org.graphiks.kadre.internal.runtime.CapturePortSourceKey
import org.graphiks.kadre.internal.runtime.CapturePortStreamListener
import org.graphiks.kadre.internal.runtime.CapturePortStreamStart
import org.graphiks.kadre.internal.runtime.CapturePortTermination

/**
 * La réservation HostChoice ReplayKit (phase 9, Task 3) prouvée PAR JETONS de seam — aucune
 * ReplayKit réelle dans les tests unitaires : la source factice journalise CHAQUE appel de
 * seam, compte ses arrêts, et livre des samples FACTICES dont le « buffer natif » est un
 * ByteArray contrôlé. Ce qui est prouvé est la TRADUCTION du handler ReplayKit en formes
 * port, avec les disciplines du contrôleur :
 *
 * - **le budget AVANT la copie** (ruling 1) : faits sous lock → kernel fourcc → budget →
 *   SEULEMENT ENSUITE le copie bloc — une frame surdimensionnée répond un terminal
 *   `ResourceLimitExceeded`, zéro octet livré, le buffer fermé, la pompe arrêtée ;
 * - **la fenêtre fact+copy** : le lock read-only couvre exactement faits+copie, l'unlock en
 *   finally, et la fermeture du sample EXACTEMENT UNE FOIS (le fake LÈVE au second close —
 *   la mutation « double-close » crasherait nativement, le test qui passe le prouve) ;
 * - **les octets transférés** (ruling 3) : le ByteArray livré à `onFrame` est une copie
 *   fraîche — muter le « natif » du fake après la livraison ne touche pas les octets livrés
 *   (une implémentation à référence retenue échoue) ;
 * - **rien après le terminal** (ruling 4/5) : les invocations tardives du handler sont
 *   tombées en silence et COMPTEES (le compteur de drops, le pattern phase-7) ;
 * - **l'arrêt exactement une fois** (ruling 4) : `stopCapture` exactement une fois sous
 *   courses stop/close, le terminal exactement une fois ;
 * - **le close pendant le start** (ruling 4) : le start répond immédiatement l'échec,
 *   sans aucun callback ;
 * - **la disponibilité lue VIVANTE au start** (ruling 6) : un recorder indisponible répond
 *   l'échec honnête du seam (`TemporarilyUnavailable`), terminal, sans callback ;
 * - **la mapping du spike § 6** : l'erreur du flux vivant est un terminal
 *   `Outcome(Failed(PlatformFailure))` — JAMAIS `SourceLost` (réservé à la surface détachée
 *   de la Task 4) ; l'erreur d'arrêt est un fait diagnostic, non un terminal.
 */
class UIKitHostCaptureReservationTest {

    // --------------------------------------------------------------------------
    // Les jetons de seam — la source factice et le sample factice.
    // --------------------------------------------------------------------------

    /**
     * Le seam factice : le journal de chaque appel (start/stop/lecture de disponibilité), le
     * compteur d'arrêts, et le handler posé par le pump que les tests pilotent à la main.
     */
    private class FakeCaptureSource : UIKitCaptureSource {
        var available: Boolean = true
        val calls = ArrayList<String>()
        var stopCount: Int = 0
            private set
        var closedCount: Int = 0
            private set
        var handler: UIKitCaptureStreamHandler? = null
            private set

        /** Le signal du test : le pump a passé son admission et posé le handler ReplayKit. */
        val handlerAdmitted = CompletableDeferred<Unit>()

        override fun recorderAvailable(): Boolean {
            calls += "recorderAvailable"
            return available
        }

        override fun startCapture(handler: UIKitCaptureStreamHandler) {
            calls += "startCapture"
            this.handler = handler
            handlerAdmitted.complete(Unit)
        }

        override fun stopCapture(onStopped: (String?) -> Unit) {
            calls += "stopCapture"
            stopCount += 1
            onStopped(null)
        }

        override fun close() {
            closedCount += 1
        }

        fun deliverVideoSample(sample: UIKitCaptureSample) {
            handler!!.onSample(sample)
        }

        fun deliverNonVideoSample() {
            handler!!.onSample(null)
        }

        fun failStream(code: String) {
            handler!!.onStreamError(code)
        }

        fun completeStart() {
            handler!!.onStarted()
        }

        fun failStart(code: String) {
            handler!!.onStartFailed(code)
        }
    }

    /**
     * Le sample factice — un « buffer natif » ByteArray contrôlé, une PAIRE lock/unlock
     * comptée, et une fermeture EXACTEMENT UNE FOIS : le second close LÈVE (la mutation que
     * le pin exige — un double close natif est un crash, le test qui passe prouve que le
     * pump ne le fait jamais).
     */
    private class FakeSample(
        val width: Int,
        val height: Int,
        val bytesPerRow: Int,
        val cvFormat: Long = UIKIT_CVFORMAT_32BGRA,
        nativeBytes: ByteArray? = null,
        override val sourceTimestampNanos: Long? = null,
    ) : UIKitCaptureSample {
        val native: ByteArray = nativeBytes ?: ByteArray(height * bytesPerRow)
        var lockCount: Int = 0
            private set
        var unlockCount: Int = 0
            private set
        var closeCount: Int = 0
            private set

        override fun lock(): UIKitCaptureLockedBuffer? {
            check(lockCount == 0) { "the fake sample was locked twice" }
            lockCount += 1
            return LockedBuffer()
        }

        override fun unlock() {
            check(lockCount > unlockCount) { "the fake sample was unlocked without a lock" }
            unlockCount += 1
        }

        override fun close() {
            check(closeCount == 0) { "the capture sample was closed twice — a native double release would crash" }
            closeCount += 1
        }

        private inner class LockedBuffer : UIKitCaptureLockedBuffer {
            override val width: Int = this@FakeSample.width
            override val height: Int = this@FakeSample.height
            override val bytesPerRow: Int = this@FakeSample.bytesPerRow
            override val cvFormat: Long = this@FakeSample.cvFormat

            override fun copyInto(destination: ByteArray, byteCount: Int) {
                // La simulation du memcpy : le « natif » du fake est copié bloc vers la
                // destination fraîche du pump — JAMAIS l'array du fake elle-même.
                native.copyInto(destination, 0, 0, byteCount)
            }
        }
    }

    /** Le listener d'enregistrement — tout callback du port est visible au test. */
    private class RecordingListener : CapturePortStreamListener {
        val frames = ArrayList<CapturePortFrame>()
        val reconfigurations = ArrayList<CaptureConfiguration>()
        val terminations = ArrayList<CapturePortTermination>()

        override fun onFrame(frame: CapturePortFrame) {
            frames += frame
        }

        override fun onReconfigured(configuration: CaptureConfiguration) {
            reconfigurations += configuration
        }

        override fun onTerminated(termination: CapturePortTermination) {
            terminations += termination
        }
    }

    private fun newReservation(
        streams: FakeCaptureSource = FakeCaptureSource(),
        request: CaptureRequest = CaptureRequest(),
    ): Pair<UIKitHostCaptureReservation, FakeCaptureSource> = UIKitHostCaptureReservation(
        source = CapturePortSource(
            key = CapturePortSourceKey("uikit-test-host", 0L),
            kind = CaptureSourceKind.Display,
            name = null,
            size = null,
        ),
        streams = streams,
        request = request,
        closedGate = { false },
        onEnded = {},
    ) to streams

    /**
     * Le start « avec première frame » — la recette déterministe : le start part en
     * coroutine, l'admission est attendue (le handler posé), la première frame est garée
     * AVANT la complétion du démarrage, puis la complétion est livrée.
     */
    private suspend fun startWithFirstFrame(
        reservation: UIKitHostCaptureReservation,
        streams: FakeCaptureSource,
        listener: RecordingListener,
        maxFrameBytes: Long,
        firstSample: FakeSample,
    ): KadreResult<CapturePortStreamStart> = coroutineScope {
        val started = async(Dispatchers.Default) { reservation.start(listener, maxFrameBytes) }
        streams.handlerAdmitted.await()
        streams.deliverVideoSample(firstSample)
        streams.completeStart()
        started.await()
    }

    private suspend fun awaitCondition(description: String, condition: () -> Boolean) {
        val deadline = TimeSource.Monotonic.markNow() + 5.seconds
        while (!condition()) {
            check(TimeSource.Monotonic.markNow() < deadline) { "timeout: $description" }
            delay(10)
        }
    }

    private fun videoSample(
        width: Int = 4,
        height: Int = 2,
        bytesPerRow: Int = 16,
        fill: Byte = 0x07,
        timestampNanos: Long? = null,
    ): FakeSample {
        val sample = FakeSample(width, height, bytesPerRow, sourceTimestampNanos = timestampNanos)
        sample.native.fill(fill)
        return sample
    }

    // --------------------------------------------------------------------------
    // Le budget AVANT la copie (ruling 1).
    // --------------------------------------------------------------------------

    @Test
    fun oversizeFirstFrameFailsTheStartWithResourceLimitExceededAndClosedBuffer(): Unit = runBlocking {
        val (reservation, streams) = newReservation()
        val listener = RecordingListener()
        // Une frame 4x4 @ stride 16 = 64 octets — le budget n'en admet que 32.
        val oversize = videoSample(width = 4, height = 4, bytesPerRow = 16)
        val started = startWithFirstFrame(
            reservation = reservation,
            streams = streams,
            listener = listener,
            maxFrameBytes = 32L,
            firstSample = oversize,
        )

        // Le start lui-même échoue : la PREMIÈRE frame est surdimensionnée — la pompe ne
        // démarre jamais, le budget a été vérifié AVANT la copie.
        val failure = assertIs<KadreResult.Failure>(started)
        assertEquals(
            KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, 32L),
            failure.reason,
        )
        // Zéro octet livré, aucun callback du tout.
        assertEquals(emptyList(), listener.frames)
        assertEquals(emptyList(), listener.terminations)
        // La fenêtre fact+copy a été REFERMÉE : lock, unlock, close exactement une fois.
        assertEquals(1, oversize.lockCount)
        assertEquals(1, oversize.unlockCount)
        assertEquals(1, oversize.closeCount)
        // La pompe est arrêtée (le recorder fut démarré) — exactement une fois.
        assertEquals(1, streams.stopCount)
    }

    @Test
    fun oversizeFrameAfterStartTerminalsOnceWithZeroBytesAndClosedBuffer(): Unit = runBlocking {
        val (reservation, streams) = newReservation()
        val listener = RecordingListener()
        // La première frame (4x1 @ 16 = 16 octets) passe le budget de 32 ; la deuxième (4x3
        // @ 16 = 48) le dépasse — le budget est vérifié AVANT la copie, la copie n'a jamais
        // tourné pour elle.
        val first = videoSample(height = 1)
        val started = startWithFirstFrame(reservation, streams, listener, maxFrameBytes = 32L, firstSample = first)
        assertIs<KadreResult.Success<CapturePortStreamStart>>(started)

        val oversize = videoSample(height = 3)
        streams.deliverVideoSample(oversize)

        // Le terminal exactement une fois, portant ResourceLimitExceeded.
        awaitCondition("the oversize terminal is delivered") { listener.terminations.isNotEmpty() }
        assertEquals(1, listener.terminations.size)
        val termination = assertIs<CapturePortTermination.Outcome>(listener.terminations.single())
        val outcome = assertIs<CaptureOutcome.Failed>(termination.outcome)
        assertEquals(
            KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, 32L),
            outcome.failure,
        )
        // Zéro octet de la frame surdimensionnée — au plus la première frame (livrée en
        // async depuis le garé), JAMAIS une frame 4x3 (la copie n'a jamais tourné pour elle).
        assertTrue(listener.frames.size <= 1)
        listener.frames.forEach { frame -> assertEquals(1, frame.size.height) }
        assertEquals(1, first.closeCount)
        // La pompe est arrêtée (stop exactement une fois) et le buffer surdimensionné fermé.
        assertEquals(1, streams.stopCount)
        assertEquals(1, oversize.closeCount)
        assertEquals(1, oversize.unlockCount)
    }

    // --------------------------------------------------------------------------
    // La fermeture EXACTEMENT UNE FOIS (ruling 2) — le fake lève au second close.
    // --------------------------------------------------------------------------

    @Test
    fun eachSampleIsClosedExactlyOnceAcrossTheWholeStream(): Unit = runBlocking {
        val (reservation, streams) = newReservation()
        val listener = RecordingListener()
        val first = videoSample(height = 1)
        val started = startWithFirstFrame(reservation, streams, listener, maxFrameBytes = 1 shl 20, firstSample = first)
        assertIs<KadreResult.Success<CapturePortStreamStart>>(started)

        // Trois frames supplémentaires — chacune doit être fermée EXACTEMENT une fois (le
        // check du fake lèverait au second close : le test qui passe EST le pin).
        val samples = List(3) { videoSample(height = 1) }
        samples.forEach { streams.deliverVideoSample(it) }

        awaitCondition("all three frames delivered") { listener.frames.size >= 4 }
        assertEquals(1, first.closeCount)
        assertEquals(1, first.unlockCount)
        samples.forEach { sample ->
            assertEquals(1, sample.closeCount, "every sample is closed exactly once")
            assertEquals(1, sample.unlockCount, "every lock window is closed by an unlock")
        }
    }

    // --------------------------------------------------------------------------
    // Les octets transférés (ruling 3) — la source mutée après livraison est sans effet.
    // --------------------------------------------------------------------------

    @Test
    fun deliveredBytesSurvivePostDeliveryMutationOfTheSource(): Unit = runBlocking {
        val (reservation, streams) = newReservation()
        val listener = RecordingListener()
        // Un natif rempli de 0x07 — la copie du pump doit en être une COPIE FRAÎCHE.
        val first = videoSample(width = 4, height = 1, bytesPerRow = 16, fill = 0x07)
        val started = startWithFirstFrame(reservation, streams, listener, maxFrameBytes = 1 shl 20, firstSample = first)
        assertIs<KadreResult.Success<CapturePortStreamStart>>(started)
        awaitCondition("the first frame is delivered") { listener.frames.isNotEmpty() }

        val delivered = listener.frames.single().planes.single().bytes
        // La mutation APRÈS la livraison : une implémentation à référence retenue (qui
        // livrerait l'array du sample elle-même) verrait ses octets livrés changer — le test
        // échouerait. Le pump honnête a copié : les octets livrés restent 0x07.
        first.native.fill(0x2A)
        assertTrue(delivered.isNotEmpty())
        delivered.forEach { byte -> assertEquals(0x07, byte) }
    }

    // --------------------------------------------------------------------------
    // Rien après le terminal (ruling 4/5) — drops silencieux comptés.
    // --------------------------------------------------------------------------

    @Test
    fun lateHandlerSamplesAfterTerminalAreSilentlyDroppedAndCounted(): Unit = runBlocking {
        val (reservation, streams) = newReservation()
        val listener = RecordingListener()
        val first = videoSample(height = 1)
        val started = startWithFirstFrame(reservation, streams, listener, maxFrameBytes = 1 shl 20, firstSample = first)
        val start = assertIs<KadreResult.Success<CapturePortStreamStart>>(started).value

        // L'arrêt propre : le terminal Stopped(Requested) exactement une fois.
        start.stream.close()
        assertEquals(1, listener.terminations.size)
        val termination = assertIs<CapturePortTermination.Outcome>(listener.terminations.single())
        assertEquals(CaptureOutcome.Stopped(CaptureStopReason.Requested), termination.outcome)

        // Les invocations TARDIVES du handler : tombées en silence, COMPTEES, zéro callback.
        val droppedBefore = reservation.droppedSampleCount()
        val late = videoSample(height = 1)
        streams.deliverVideoSample(late)
        streams.deliverNonVideoSample()
        assertEquals(1, listener.terminations.size, "no second terminal after the stop")
        assertEquals(droppedBefore + 2, reservation.droppedSampleCount())
        // Le buffer tardif a quand même été refermé honnêtement (fenêtre fact+copy propre).
        assertEquals(1, late.closeCount)
    }

    // --------------------------------------------------------------------------
    // L'arrêt exactement une fois sous courses (ruling 4).
    // --------------------------------------------------------------------------

    @Test
    fun stopCaptureRunsExactlyOnceUnderRacingStopAndClose(): Unit = runBlocking {
        val (reservation, streams) = newReservation()
        val listener = RecordingListener()
        val first = videoSample(height = 1)
        val started = startWithFirstFrame(reservation, streams, listener, maxFrameBytes = 1 shl 20, firstSample = first)
        val start = assertIs<KadreResult.Success<CapturePortStreamStart>>(started).value

        // La course : le close du stream (l'arrêt propre du runtime) contre le close de la
        // réservation — le terminal reste EXACTEMENT UNE FOIS, l'arrêt du recorder AUSSI.
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
        assertEquals(1, streams.stopCount, "stopCaptureWithHandler exactly once under the race")
    }

    // --------------------------------------------------------------------------
    // Le close pendant le start (ruling 4) — échec immédiat, zéro callback.
    // --------------------------------------------------------------------------

    @Test
    fun reservationCloseDuringStartFailsTheStartImmediatelyWithoutCallbacks(): Unit = runBlocking {
        val (reservation, streams) = newReservation()
        val listener = RecordingListener()
        val started = async(Dispatchers.Default) { reservation.start(listener, 1 shl 20) }
        streams.handlerAdmitted.await()

        // Le close PENDANT l'attente de la complétion : le start répond immédiatement.
        reservation.close()
        val result = started.await()

        val failure = assertIs<KadreResult.Failure>(result)
        assertEquals(KadreFailure.Closed(KadreResourceKind.CaptureSession), failure.reason)
        assertEquals(emptyList(), listener.frames)
        assertEquals(emptyList(), listener.terminations)
        // Le recorder fut démarré — l'arrêt exactement une fois quand même.
        assertEquals(1, streams.stopCount)
    }

    @Test
    fun recorderFailureDuringStartFailsTheStartWithTheReplayKitPlatformFailure(): Unit = runBlocking {
        val (reservation, streams) = newReservation()
        val listener = RecordingListener()
        val started = async(Dispatchers.Default) { reservation.start(listener, 1 shl 20) }
        streams.handlerAdmitted.await()
        streams.failStart("5705")
        val result = started.await()

        val failure = assertIs<KadreResult.Failure>(result)
        assertEquals(
            KadreFailure.PlatformFailure(KadrePlatform.UIKit, "replaykit", "start:5705"),
            failure.reason,
        )
        assertEquals(emptyList(), listener.frames)
        assertEquals(1, streams.stopCount)
    }

    // --------------------------------------------------------------------------
    // La disponibilité lue VIVANTE au start (ruling 6).
    // --------------------------------------------------------------------------

    @Test
    fun recorderUnavailableAtStartFailsHonestlyWithoutStartingAnything(): Unit = runBlocking {
        val streams = FakeCaptureSource().apply { available = false }
        val (reservation, _) = newReservation(streams)
        val listener = RecordingListener()

        val result = reservation.start(listener, 1 shl 20)

        val failure = assertIs<KadreResult.Failure>(result)
        assertEquals(KadreFailure.TemporarilyUnavailable(retryable = true), failure.reason)
        // Aucun callback, et le recorder n'a JAMAIS été démarré (ni arrêté).
        assertEquals(emptyList(), listener.frames)
        assertEquals(emptyList(), listener.terminations)
        assertEquals(listOf("recorderAvailable"), streams.calls)
        assertEquals(0, streams.stopCount)
    }

    // --------------------------------------------------------------------------
    // La mapping du spike § 6 — l'erreur du flux vivant : Failed, JAMAIS SourceLost.
    // --------------------------------------------------------------------------

    @Test
    fun liveStreamRecorderFailureTerminalsFailedNeverSourceLost(): Unit = runBlocking {
        val (reservation, streams) = newReservation()
        val listener = RecordingListener()
        val first = videoSample(height = 1)
        val started = startWithFirstFrame(reservation, streams, listener, maxFrameBytes = 1 shl 20, firstSample = first)
        assertIs<KadreResult.Success<CapturePortStreamStart>>(started)

        streams.failStream("3840")

        awaitCondition("the stream-error terminal is delivered") { listener.terminations.isNotEmpty() }
        assertEquals(1, listener.terminations.size)
        val termination = assertIs<CapturePortTermination.Outcome>(listener.terminations.single())
        val outcome = assertIs<CaptureOutcome.Failed>(termination.outcome)
        // LE pin du spike : le recorder EST le chemin, pas la source — PlatformFailure, et
        // JAMAIS SourceLost (réservé à la surface détachée de la Task 4).
        assertEquals(
            KadreFailure.PlatformFailure(KadrePlatform.UIKit, "replaykit", "handler:3840"),
            outcome.failure,
        )
        assertTrue(listener.terminations.single() != CapturePortTermination.SourceLost)
        assertEquals(1, streams.stopCount)
    }

    // --------------------------------------------------------------------------
    // Les gardes d'admission de la réservation.
    // --------------------------------------------------------------------------

    @Test
    fun secondStartIsRefusedAsAlreadyInUse(): Unit = runBlocking {
        val (reservation, streams) = newReservation()
        val listener = RecordingListener()
        val first = videoSample(height = 1)
        val started = startWithFirstFrame(reservation, streams, listener, maxFrameBytes = 1 shl 20, firstSample = first)
        assertIs<KadreResult.Success<CapturePortStreamStart>>(started)

        val second = reservation.start(RecordingListener(), 1 shl 20)

        assertEquals(
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.CaptureCollector)),
            second,
        )
    }

    @Test
    fun startAfterReservationCloseIsRefusedAsClosed(): Unit = runBlocking {
        val (reservation, streams) = newReservation()
        reservation.close()
        val second = reservation.start(RecordingListener(), 1 shl 20)

        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.CaptureSession)),
            second,
        )
        // Rien n'a été démarré : aucune lecture de disponibilité de plus, aucun arrêt.
        assertEquals(emptyList(), streams.calls)
    }

    // --------------------------------------------------------------------------
    // La frame portée — le contenu honnête du chemin kernel (fourcc, encoding, stride).
    // --------------------------------------------------------------------------

    @Test
    fun framesCarryTheKernelHonestLayoutEncodingAndTimestamp(): Unit = runBlocking {
        val (reservation, streams) = newReservation()
        val listener = RecordingListener()
        // Stride paddé (20 > 4·4) — le layout porte le stride verbatim, byteCount = h·rowStride.
        val first = videoSample(width = 4, height = 2, bytesPerRow = 20, fill = 0x5A, timestampNanos = 123456L)
        val started = startWithFirstFrame(reservation, streams, listener, maxFrameBytes = 1 shl 20, firstSample = first)
        assertIs<KadreResult.Success<CapturePortStreamStart>>(started)

        // La configuration de start (révision 0) — la taille de la première frame.
        val start = assertIs<KadreResult.Success<CapturePortStreamStart>>(started).value
        assertEquals(0L, start.configuration.revision.value)
        start.configuration.let { configuration ->
            assertEquals(PixelFormat.Bgra8, configuration.format)
            assertEquals(
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
        }

        awaitCondition("the parked first frame is delivered") { listener.frames.isNotEmpty() }
        val frame = listener.frames.single()
        assertEquals(0L, frame.configurationRevision)
        assertEquals(123456L.nanoseconds, frame.sourceTimestamp)
        assertEquals(null, frame.duration)
        val plane = frame.planes.single()
        assertEquals(20, plane.layout.rowStride)
        assertEquals(40, plane.layout.byteCount)
        assertEquals(40, plane.bytes.size)
        // Les octets livrés sont la copie du « natif » du sample.
        plane.bytes.forEach { byte -> assertEquals(0x5A, byte) }
        // Le sample de départ a été refermé honnêtement.
        assertEquals(1, first.closeCount)
    }

    @Test
    fun honestMissingTimestampStaysNullAndNonVideoSamplesAreCounted(): Unit = runBlocking {
        val (reservation, streams) = newReservation()
        val listener = RecordingListener()
        val first = videoSample(height = 1, timestampNanos = null)
        val started = startWithFirstFrame(reservation, streams, listener, maxFrameBytes = 1 shl 20, firstSample = first)
        assertIs<KadreResult.Success<CapturePortStreamStart>>(started)
        awaitCondition("the first frame is delivered") { listener.frames.isNotEmpty() }

        // L'honnêteté du timestamp : un sample sans timestamp lisible → null, jamais inventé.
        assertEquals(null, listener.frames.single().sourceTimestamp)

        // Les samples NON vidéo (l'audio ReplayKit) : tombés en silence, comptés.
        val droppedBefore = reservation.droppedSampleCount()
        streams.deliverNonVideoSample()
        assertEquals(droppedBefore + 1, reservation.droppedSampleCount())
        assertEquals(1, listener.frames.size)
    }

    // --------------------------------------------------------------------------
    // La reconfiguration — la taille livrée diffère : onReconfigured AVANT la frame.
    // --------------------------------------------------------------------------

    @Test
    fun sizeChangePublishesReconfigurationBeforeTheCarryingFrame(): Unit = runBlocking {
        val (reservation, streams) = newReservation()
        val listener = RecordingListener()
        val first = videoSample(width = 4, height = 1, bytesPerRow = 16)
        val started = startWithFirstFrame(reservation, streams, listener, maxFrameBytes = 1 shl 20, firstSample = first)
        assertIs<KadreResult.Success<CapturePortStreamStart>>(started)
        awaitCondition("the first frame is delivered") { listener.frames.isNotEmpty() }

        // Une frame d'une AUTRE taille (la rotation) : la configuration avance d'UNE révision,
        // publiée AVANT la frame qui la porte.
        val rotated = videoSample(width = 8, height = 2, bytesPerRow = 32)
        streams.deliverVideoSample(rotated)

        awaitCondition("the reconfigured frame is delivered") { listener.frames.size >= 2 }
        assertEquals(1, listener.reconfigurations.size)
        assertEquals(1L, listener.reconfigurations.single().revision.value)
        val second = listener.frames.last()
        assertEquals(1L, second.configurationRevision)
        assertEquals(8, second.size.width)
        assertEquals(2, second.size.height)
    }

    // --------------------------------------------------------------------------
    // La complétion de démarrage peut arriver APRÈS la première frame — l'ordre inverse.
    // --------------------------------------------------------------------------

    @Test
    fun firstFrameArrivingBeforeStartCompletionIsParkedNotLost(): Unit = runBlocking {
        val (reservation, streams) = newReservation()
        val listener = RecordingListener()
        val started = async(Dispatchers.Default) { reservation.start(listener, 1 shl 20) }
        streams.handlerAdmitted.await()

        // La complétion D'ABORD, la frame ENSUITE — le start attend sa première frame.
        streams.completeStart()
        delay(50)
        streams.deliverVideoSample(videoSample(height = 1))

        val result = started.await()
        assertIs<KadreResult.Success<CapturePortStreamStart>>(result)
        awaitCondition("the frame is delivered after start") { listener.frames.isNotEmpty() }
    }

}
