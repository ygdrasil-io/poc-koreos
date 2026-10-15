package org.graphiks.kadre.internal.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.graphiks.kadre.capture.CaptureConfiguration
import org.graphiks.kadre.capture.CaptureOutcome
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSourceKind
import org.graphiks.kadre.capture.CaptureStopReason
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.CapturePortFrame
import org.graphiks.kadre.internal.runtime.CapturePortSource
import org.graphiks.kadre.internal.runtime.CapturePortSourceKey
import org.graphiks.kadre.internal.runtime.CapturePortStreamListener
import org.graphiks.kadre.internal.runtime.CapturePortTermination

/**
 * LA MESURE du chemin ReplayKit réel sur l'hôte Simulator (le devoir du spike § 9.1 et du
 * brief Task 3) : `isAvailable` y répond TRUE, mais la disponibilité n'est pas la preuve
 * par frames — ce test démarre le recorder RÉEL, compte les frames sous un timeout borné,
 * puis s'arrête proprement. Le résultat est ENREGISTRÉ verbatim (`SPIKE-FACT host-frame`),
 * jamais fabriqué : zéro frame livrée est un résultat honnête (les tests structurels du
 * seam portent la preuve, cette mesure documente le fait).
 *
 * Le pin structurel de la mesure : le close du stream délivre le terminal
 * `Stopped(Requested)` — le contrat de fin propre tient SUR LE CHEMIN RÉEL.
 */
class UIKitReplayKitRealFrameMeasurementTest {

    /**
     * La sonde BRUTE du seam (sans réservation) : elle sépare les DEUX faits que la mesure
     * complète ne peut pas distinguer — la complétion du démarrage a-t-elle eu lieu, et des
     * frames sont-elles arrivées. Chaque fait est enregistré verbatim, jamais fabriqué.
     */
    @Test
    fun rawReplayKitSeamCompletionAndFirstFrameAreMeasuredSeparately(): Unit = runBlocking {
        val source = UIKitReplayKitCaptureSource()
        val available = source.recorderAvailable()
        println("SPIKE-FACT host-frame: RPScreenRecorder.isAvailable=$available")
        if (!available) {
            println("SPIKE-FACT host-frame: recorder indisponible sur cet hôte — mesure absentée, rien n'est fabriqué")
            return@runBlocking
        }

        var samples = 0
        val startVerdict = kotlinx.coroutines.CompletableDeferred<String>()
        source.startCapture(object : UIKitCaptureStreamHandler {
            override fun onSample(sample: UIKitCaptureSample?) {
                if (sample == null) {
                    println("SPIKE-FACT host-frame: sample NON vidéo (audio) reçu et tombé")
                    return
                }
                samples += 1
                if (samples == 1) {
                    val locked = sample.lock()
                    val facts = locked?.let { "w=${it.width} h=${it.height} bpr=${it.bytesPerRow} fourcc=${it.cvFormat}" }
                    if (locked != null) sample.unlock()
                    sample.close()
                    println("SPIKE-FACT host-frame: PREMIÈRE FRAME — $facts")
                }
            }

            override fun onStreamError(code: String) {
                println("SPIKE-FACT host-frame: erreur du flux vivant code=$code")
            }

            override fun onStarted() {
                startVerdict.complete("onStarted")
            }

            override fun onStartFailed(code: String) {
                startVerdict.complete("onStartFailed:$code")
            }
        })
        // L'attente bornée : la complétion OU la première frame, la première des deux.
        val verdict = withTimeoutOrNull(10.seconds) { startVerdict.await() }
        println("SPIKE-FACT host-frame: complétion du démarrage = ${verdict ?: "ABSENTE sous 10s"}")
        if (verdict != null) {
            // La complétion est passée : est-ce que des frames SUIVENT ?
            val deadline = TimeSource.Monotonic.markNow() + 8.seconds
            while (TimeSource.Monotonic.markNow() < deadline && samples == 0) delay(100)
            println("SPIKE-FACT host-frame: frames après complétion = $samples")
        }
        source.stopCapture { code ->
            println("SPIKE-FACT host-frame: arrêt du recorder (diagnostic code=$code)")
        }
    }

    @Test
    fun realRecorderStartFrameCountAndCleanStopAreMeasuredOnThisHost(): Unit = runBlocking {
        val source = UIKitReplayKitCaptureSource()
        val available = source.recorderAvailable()
        println("SPIKE-FACT host-frame: RPScreenRecorder.isAvailable=$available")
        if (!available) {
            println("SPIKE-FACT host-frame: recorder indisponible sur cet hôte — mesure absentée, rien n'est fabriqué")
            return@runBlocking
        }

        val reservation = UIKitHostCaptureReservation(
            source = CapturePortSource(
                key = CapturePortSourceKey("uikit-host-measure", 0L),
                kind = CaptureSourceKind.Display,
                name = null,
                size = null,
            ),
            streams = source,
            request = CaptureRequest(),
            closedGate = { false },
            onEnded = {},
        )
        var frames = 0
        var firstFrameAt = TimeSource.Monotonic.markNow()
        var terminal: CapturePortTermination? = null
        val listener = object : CapturePortStreamListener {
            override fun onFrame(frame: CapturePortFrame) {
                if (frames == 0) firstFrameAt = TimeSource.Monotonic.markNow()
                frames += 1
            }

            override fun onReconfigured(configuration: CaptureConfiguration) = Unit

            override fun onTerminated(termination: CapturePortTermination) {
                terminal = termination
            }
        }

        val started = withTimeoutOrNull(15.seconds) {
            reservation.start(listener, maxFrameBytes = 64L * 1024L * 1024L)
        }
        println("SPIKE-FACT host-frame: start=$started")
        val stream = (started as? KadreResult.Success)?.value?.stream
        if (stream != null) {
            // Le comptage borné : 8 secondes suffisent à un recorder qui délivre (60/s attendus).
            val deadline = TimeSource.Monotonic.markNow() + 8.seconds
            while (TimeSource.Monotonic.markNow() < deadline && frames == 0) delay(100)
            val waited = 8.seconds - (deadline - TimeSource.Monotonic.markNow())
            println(
                "SPIKE-FACT host-frame: frames=$frames (première après ${firstFrameAt.elapsedNow()}, " +
                    "attente bornée $waited)",
            )
            // L'arrêt propre sur le chemin réel : le close du stream reporte le terminal.
            stream.close()
            val stopDeadline = TimeSource.Monotonic.markNow() + 5.seconds
            while (TimeSource.Monotonic.markNow() < stopDeadline && terminal == null) delay(50)
            println("SPIKE-FACT host-frame: terminal=$terminal")
            assertEquals(
                CapturePortTermination.Outcome(CaptureOutcome.Stopped(CaptureStopReason.Requested)),
                terminal,
            )
        } else {
            println("SPIKE-FACT host-frame: LE START n'a pas abouti sur cet hôte — mesuré, non fabriqué")
        }
        reservation.close()
        println("SPIKE-FACT host-frame: arrêt propre (la réservation est close, le recorder arrêté une fois)")
    }
}
