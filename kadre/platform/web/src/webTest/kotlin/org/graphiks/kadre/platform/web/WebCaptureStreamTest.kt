package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.capture.AlphaMode
import org.graphiks.kadre.capture.CaptureCadence
import org.graphiks.kadre.capture.CaptureConfiguration
import org.graphiks.kadre.capture.CaptureCursorMode
import org.graphiks.kadre.capture.CaptureDiagnostic
import org.graphiks.kadre.capture.CaptureOrientation
import org.graphiks.kadre.capture.CaptureOutcome
import org.graphiks.kadre.capture.CaptureRegion
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
import org.graphiks.kadre.internal.runtime.CapturePortReservation
import org.graphiks.kadre.internal.runtime.CapturePortSource
import org.graphiks.kadre.internal.runtime.CapturePortSourceKey
import org.graphiks.kadre.internal.runtime.CapturePortStreamListener
import org.graphiks.kadre.internal.runtime.CapturePortStreamStart
import org.graphiks.kadre.internal.runtime.CapturePortTarget
import org.graphiks.kadre.internal.runtime.CapturePortTermination
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalSize
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The `HostChoice` streaming vertical, driven entirely over scripted seam doubles: every frame the
 * browser would deliver is staged in a [ScriptedReadable], every release effect is counted, and the
 * listener records the exact callback order — which is what the configuration-before-frame and
 * no-frames-after-stop invariants are mutations against. The five review-focus classes are pinned
 * by name; the mapping tier tables sit next to them as pure unit tests.
 */
class WebCaptureStreamTest {

    // -- the seam doubles --------------------------------------------------------------------------------

    /** A track double: the two release effects are counted, the external end is under test control. */
    private class ScriptedTrack : WebDomVideoTrack {
        var stopCount = 0
            private set
        var closeCount = 0
            private set
        private val endedListeners = mutableListOf<() -> Unit>()

        override fun stop() {
            stopCount += 1
        }

        override fun close() {
            closeCount += 1
        }

        override fun addEndedListener(listener: () -> Unit) {
            endedListeners += listener
        }

        /** The browser's own revocation: the track ends, not by the caller's stop. */
        fun endExternally() = endedListeners.forEach { it() }
    }

    /**
     * A frame double: the shape is fixed, the copy result is scripted byte-exact, and both the copy
     * and the browser's frame release are counted — the oversized-frame invariant is an assertion
     * over the copy count staying zero.
     */
    private class ScriptedFrame(
        override val shape: WebVideoFrameShape,
        private val allocationSizeBytes: Long,
        private val copyResult: List<WebPlaneBytes>,
    ) : WebVideoFrame {
        var copyCount = 0
            private set
        val allocationFormats = mutableListOf<String?>()
        val copyFormats = mutableListOf<String?>()
        var closeCount = 0
            private set

        override fun allocationSize(format: String?): Long {
            allocationFormats += format
            return allocationSizeBytes
        }

        override suspend fun copyTo(format: String?): List<WebPlaneBytes> {
            copyCount += 1
            copyFormats += format
            return copyResult
        }

        override fun close() {
            closeCount += 1
        }
    }

    /**
     * A readable double: reads answer from a queue while it has entries and suspend otherwise, so a
     * test decides exactly when the browser's next frame lands. The close count is the reader
     * release the exactly-once invariants are asserted over.
     */
    private class ScriptedReadable : WebFrameReadable {
        var closeCount = 0
            private set
        var readRequests = 0
            private set
        private val queued = ArrayDeque<WebFrameRead>()
        private var pending: CancellableContinuation<WebFrameRead>? = null

        fun queue(read: WebFrameRead) {
            queued += read
        }

        /**
         * Answers a read that is already suspended in [read] — the browser's next event, late. A
         * waiter that is already gone (the pump stopped or was cancelled mid-delivery) never sees
         * the chunk: the seam's documented discard closes the frame right here, the way the real
         * realizations close it.
         */
        fun offer(read: WebFrameRead) {
            val waiting = pending
            pending = null
            if (waiting != null && waiting.isActive) {
                waiting.resume(read) { _, _, _ -> }
            } else {
                // No live waiter: the reader is closed or its task cancelled — the chunk is
                // discarded at the seam, its frame closed, exactly as the realizations do.
                (read as? WebFrameRead.Frame)?.frame?.close()
            }
        }

        override suspend fun read(): WebFrameRead = suspendCancellableCoroutine { continuation ->
            readRequests += 1
            val staged = queued.removeFirstOrNull()
            if (staged != null) {
                continuation.resume(staged) { _, _, _ -> }
                return@suspendCancellableCoroutine
            }
            pending = continuation
            continuation.invokeOnCancellation { if (pending === continuation) pending = null }
        }

        override fun close() {
            closeCount += 1
        }
    }

    private class ScriptedProcessorFactory(private val readable: WebFrameReadable) : WebTrackProcessorFactory {
        var builds = 0
            private set

        override fun processorFor(track: WebDomVideoTrack): WebFrameReadable {
            builds += 1
            return readable
        }
    }

    /**
     * The stream listener double: the event order IS the evidence — configuration-before-frame and
     * frames-after-stop are assertions over this list, and each frame records how many diagnostics
     * the pump had already published when the frame arrived.
     */
    private class RecordingListener(
        private val diagnostics: () -> List<CaptureDiagnostic> = { emptyList() },
    ) : CapturePortStreamListener {
        val events = mutableListOf<String>()
        val configurations = mutableListOf<CaptureConfiguration>()
        val frames = mutableListOf<CapturePortFrame>()
        val terminations = mutableListOf<CapturePortTermination>()
        val diagnosticsAtFrame = mutableListOf<Int>()

        override fun onFrame(frame: CapturePortFrame) {
            events += "frame"
            diagnosticsAtFrame += diagnostics().size
            frames += frame
        }

        override fun onReconfigured(configuration: CaptureConfiguration) {
            events += "reconfigured"
            configurations += configuration
        }

        override fun onTerminated(termination: CapturePortTermination) {
            events += "terminated"
            terminations += termination
        }
    }

    /** The capture seam double for the port-level tests: staged picks, a scripted factory. */
    private class StreamCaptureDom(
        var processor: WebTrackProcessorFactory? = null,
    ) : WebCaptureDom {
        val pickResults = ArrayDeque<WebDisplayMediaPick>()
        var pickCalls = 0
            private set
        var processorCalls = 0
            private set
        val recordedHints = mutableListOf<Pair<String?, Double?>>()

        override fun isSecureContext(): Boolean = true

        override fun queryDisplayCapturePermission(): WebCapturePermissionQueryResult? =
            WebCapturePermissionQueryResult.NotDetermined

        override fun readDisplayCapturePermission(listener: (WebCapturePermissionQueryResult?) -> Unit) = Unit

        override fun hasDisplayMedia(): Boolean = true

        override suspend fun pickDisplayMedia(cursorHint: String?, frameRateHint: Double?): WebDisplayMediaPick {
            pickCalls += 1
            recordedHints += cursorHint to frameRateHint
            return pickResults.removeFirstOrNull() ?: error("no staged pick result")
        }

        override fun processorFactory(): WebTrackProcessorFactory? {
            processorCalls += 1
            return processor
        }

        override fun canvasForSurface(): WebDomCanvas? = null

        override fun close() = Unit
    }

    // -- scripted frame stock -----------------------------------------------------------------------------

    private fun shape(
        format: String? = "RGBA",
        width: Int = 4,
        height: Int = 4,
        timestampUs: Long? = null,
        durationUs: Long? = null,
        colorSpace: WebColorSpaceShape? = null,
    ) = WebVideoFrameShape(format, width, height, timestampUs, durationUs, colorSpace)

    /** One 4x4 RGBA plane, byte-exact and independent of the implementation under test. */
    private fun rgbaPlanes(): List<WebPlaneBytes> =
        listOf(WebPlaneBytes(rowStride = 16, bytes = ByteArray(64) { it.toByte() }))

    /** Three 4x4 I420 planes — luma 16 bytes, then U and V at the 2x2 chroma of a 4x4 frame. */
    private fun i420Planes(): List<WebPlaneBytes> = listOf(
        WebPlaneBytes(rowStride = 4, bytes = ByteArray(16) { it.toByte() }),
        WebPlaneBytes(rowStride = 2, bytes = ByteArray(4) { (it + 16).toByte() }),
        WebPlaneBytes(rowStride = 2, bytes = ByteArray(4) { (it + 20).toByte() }),
    )

    /** Stages one 4x4 RGBA frame as the readable's next answer. */
    private fun ScriptedReadable.frame(
        frameShape: WebVideoFrameShape = shape(),
        allocationSizeBytes: Long = 64L,
        planes: List<WebPlaneBytes> = rgbaPlanes(),
    ): ScriptedFrame {
        val frame = ScriptedFrame(frameShape, allocationSizeBytes, planes)
        queue(WebFrameRead.Frame(frame))
        return frame
    }

    private fun TestScope.dispatcher(): CoroutineContext = StandardTestDispatcher(testScheduler)

    /** The port under test, its pump loop bound to this test's scheduler so runs are deterministic. */
    private fun TestScope.capturePort(dom: StreamCaptureDom): WebCapturePort =
        WebCapturePort(dom, primarySurfaceElementIsCanvas = true, captureLoopContext = dispatcher())

    private fun KadreResult<CapturePortStreamStart>.started(): CapturePortStreamStart =
        assertIs<KadreResult.Success<CapturePortStreamStart>>(this).value

    private fun KadreResult<CapturePortReservation>.reserved(): CapturePortReservation =
        assertIs<KadreResult.Success<CapturePortReservation>>(this).value

    // -- review focus 1: no user activation — the reserve fails honestly, no hang, no partial state -------

    @Test
    fun reserveWithoutActivationFailsHonestly() = runTest {
        val dom = StreamCaptureDom(processor = ScriptedProcessorFactory(ScriptedReadable()))
        dom.pickResults += WebDisplayMediaPick.Refused("NotAllowedError")
        val port = capturePort(dom)

        val result = port.reserve(CapturePortTarget.HostChoice, CaptureRequest())
        assertEquals(
            KadreResult.Failure(KadreFailure.UserCancelled(KadreOperation.CaptureOpen)),
            result,
            "the consent that never happened is the AppKit-mirrored user cancellation of the open",
        )
        assertEquals(1, dom.pickCalls)
        assertTrue(dom.pickResults.isEmpty(), "the refusal consumed the staged answer and nothing else")

        // No partial state: the port still admits a later, consented reserve on the same seam.
        val track = ScriptedTrack()
        dom.pickResults += WebDisplayMediaPick.Picked(track)
        val reservation = port.reserve(CapturePortTarget.HostChoice, CaptureRequest()).reserved()
        reservation.close()
        assertEquals(1, track.stopCount, "the closed reservation releases its picked track exactly once")
        assertEquals(1, track.closeCount)
    }

    // -- review focus 4: the host cancels the browser picker — reserve fails, nothing leaks ---------------

    @Test
    fun pickerCancelFailsReserve() = runTest {
        val dom = StreamCaptureDom(processor = ScriptedProcessorFactory(ScriptedReadable()))
        dom.pickResults += WebDisplayMediaPick.Refused("AbortError")
        val port = capturePort(dom)

        val result = port.reserve(CapturePortTarget.HostChoice, CaptureRequest())
        assertEquals(
            KadreResult.Failure(KadreFailure.UserCancelled(KadreOperation.CaptureOpen)),
            result,
            "the dismissed picker is the same consent refusal, mapped to the same closed failure",
        )
        assertEquals(1, dom.pickCalls)
        assertTrue(dom.pickResults.isEmpty())
        // The refusal carried no track, so there is nothing to release and nothing that could leak:
        // the result IS the failure, never a reservation object.
        assertIs<KadreResult.Failure>(result)
    }

    @Test
    fun hostChoicePickerRefusalsMapAtReserve() = runTest {
        // NotFoundError — no source to offer — is retryable temporary unavailability.
        val noSource = StreamCaptureDom()
        noSource.pickResults += WebDisplayMediaPick.Refused("NotFoundError")
        val noSourcePort = capturePort(noSource)
        assertEquals(
            KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true)),
            noSourcePort.reserve(CapturePortTarget.HostChoice, CaptureRequest()),
        )

        // Any other code is a platform failure in the consent domain, carrying the browser's word.
        val other = StreamCaptureDom()
        other.pickResults += WebDisplayMediaPick.Refused("UnknownError")
        val otherPort = capturePort(other)
        assertEquals(
            KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.Web, "capture-permission", "UnknownError"),
            ),
            otherPort.reserve(CapturePortTarget.HostChoice, CaptureRequest()),
        )
    }

    // -- review focus 2: the browser's "Stop sharing" — the source completed, resources freed once --------

    @Test
    fun trackEndedExternallyCompletesSource() = runTest {
        val track = ScriptedTrack()
        val readable = ScriptedReadable()
        val frame = readable.frame()
        val pump = WebCapturePump(readable, track, CaptureRequest(), dispatcher())
        val listener = RecordingListener(diagnostics = { pump.diagnostics })

        pump.start(listener, maxFrameBytes = 4096L).started()
        testScheduler.runCurrent()
        assertEquals(listOf("frame"), listener.events, "the first frame flows after start returned")

        // The browser revokes the capture — the track ends, not by our stop.
        track.endExternally()
        testScheduler.runCurrent()

        assertEquals(listOf("frame", "terminated"), listener.events)
        assertEquals(
            CapturePortTermination.Outcome(CaptureOutcome.SourceCompleted),
            listener.terminations.single(),
        )
        // The terminal fact releases the stream's resources exactly once, from the backend side.
        assertEquals(1, readable.closeCount, "the reader is released exactly once at the external end")
        assertEquals(1, track.stopCount)
        assertEquals(1, track.closeCount)
        assertEquals(1, frame.closeCount, "the delivered frame's handle was released after its copy")

        // A browser that keeps delivering after its own revocation delivers nothing more here.
        readable.queue(WebFrameRead.Frame(ScriptedFrame(shape(), 64L, rgbaPlanes())))
        testScheduler.runCurrent()
        assertEquals(listOf("frame", "terminated"), listener.events, "no frame follows the termination")
    }

    // -- review focus 3: the oversized frame — terminal ResourceLimitExceeded before any byte copy --------

    @Test
    fun oversizedFrameFailsBeforeCopy() = runTest {
        val track = ScriptedTrack()
        val readable = ScriptedReadable()
        readable.frame() // the first frame fits and is delivered
        val pump = WebCapturePump(readable, track, CaptureRequest(), dispatcher())
        val listener = RecordingListener(diagnostics = { pump.diagnostics })

        pump.start(listener, maxFrameBytes = 4096L).started()
        testScheduler.runCurrent()
        assertEquals(listOf("frame"), listener.events)

        val oversized = ScriptedFrame(shape(), allocationSizeBytes = 10_000L, copyResult = rgbaPlanes())
        readable.offer(WebFrameRead.Frame(oversized))
        testScheduler.runCurrent()

        assertEquals(listOf("frame", "terminated"), listener.events)
        assertEquals(
            CapturePortTermination.Outcome(
                CaptureOutcome.Failed(KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, 4096L)),
            ),
            listener.terminations.single(),
        )
        assertEquals(0, oversized.copyCount, "the copy was never invoked: the bound was applied first")
        assertEquals(listOf<String?>(null), oversized.allocationFormats, "the bound is read before the copy that never ran")
        assertEquals(1, oversized.closeCount, "the browser frame is released, not leaked")
        assertEquals(1, listener.frames.size, "the oversized frame was never delivered")
        assertEquals(1, readable.closeCount, "the terminal failure releases the reader exactly once")
        assertEquals(1, track.stopCount)
    }

    @Test
    fun oversizedFirstFrameFailsStartBeforeCopy() = runTest {
        val track = ScriptedTrack()
        val readable = ScriptedReadable()
        val oversized = readable.frame(allocationSizeBytes = 10_000L)
        val pump = WebCapturePump(readable, track, CaptureRequest(), dispatcher())
        val listener = RecordingListener(diagnostics = { pump.diagnostics })

        val result = pump.start(listener, maxFrameBytes = 4096L)
        assertEquals(
            KadreResult.Failure(KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, 4096L)),
            result,
            "a first frame that cannot fit fails the start — the listener is told nothing before it returns",
        )
        assertTrue(listener.events.isEmpty(), "no callback escapes a failed start")
        assertEquals(0, oversized.copyCount)
        assertEquals(1, oversized.closeCount)
        assertEquals(1, readable.closeCount, "the failed start releases the reservation's stream state")
        assertEquals(1, track.stopCount)
        assertEquals(1, track.closeCount)
    }

    // -- review focus 5: a cancelled start releases the reservation's stream exactly once -----------------

    @Test
    fun startCancelledReleasesReservationOnce() = runTest {
        val track = ScriptedTrack()
        val readable = ScriptedReadable()
        val dom = StreamCaptureDom(processor = ScriptedProcessorFactory(readable))
        dom.pickResults += WebDisplayMediaPick.Picked(track)
        val port = capturePort(dom)
        val reservation = port.reserve(CapturePortTarget.HostChoice, CaptureRequest()).reserved()

        val listener = RecordingListener()
        var failure: Throwable? = null
        val startJob = launch {
            try {
                reservation.start(listener, maxFrameBytes = 4096L)
            } catch (cause: Throwable) {
                failure = cause
            }
        }
        testScheduler.runCurrent()
        assertEquals(1, readable.readRequests, "the start is suspended in the first frame read")
        assertTrue(listener.events.isEmpty(), "no listener callback before start returned")

        startJob.cancel()
        testScheduler.runCurrent()

        assertIs<CancellationException>(failure, "the cancelled start surfaces as cancellation")
        assertEquals(1, readable.closeCount, "the reader is released exactly once by the cancellation")
        assertEquals(1, track.stopCount)
        assertEquals(1, track.closeCount)

        // Every later close path — the reservation's included — adds no second release.
        reservation.close()
        reservation.close()
        testScheduler.runCurrent()
        assertEquals(1, readable.closeCount)
        assertEquals(1, track.stopCount)
        assertEquals(1, track.closeCount)
        assertTrue(listener.events.isEmpty(), "a cancelled start delivers no listener callback at all")
    }

    // -- configuration strictly precedes the first frame; a reconfiguration precedes its own first frame --

    @Test
    fun configurationPrecedesFramesAndReconfigurationPrecedesItsFrame() = runTest {
        val track = ScriptedTrack()
        val readable = ScriptedReadable()
        readable.frame() // 4x4 RGBA
        val pump = WebCapturePump(readable, track, CaptureRequest(), dispatcher())
        val listener = RecordingListener(diagnostics = { pump.diagnostics })

        val start = pump.start(listener, maxFrameBytes = 4096L).started()
        assertEquals(0L, start.configuration.revision.value)
        assertEquals(PhysicalSize(4, 4), start.configuration.size)
        assertEquals(PixelFormat.Rgba8, start.configuration.format)
        assertEquals(CaptureOrientation.Upright, start.configuration.orientation)
        assertEquals(CaptureCadence.Unknown, start.configuration.cadence, "no bound is enforced — the honest word")
        assertTrue(listener.events.isEmpty(), "the configuration was published by start, before any callback")

        testScheduler.runCurrent()
        assertEquals(listOf("frame"), listener.events)
        assertEquals(0L, listener.frames[0].configurationRevision)

        // The browser reconfigures: the next frame is taller, and its copy is 4x8-sized.
        val grown = ScriptedFrame(
            shape(width = 4, height = 8),
            allocationSizeBytes = 128L,
            copyResult = listOf(WebPlaneBytes(rowStride = 16, bytes = ByteArray(128) { it.toByte() })),
        )
        readable.offer(WebFrameRead.Frame(grown))
        testScheduler.runCurrent()

        // The new size is published as a revision advance BEFORE the frame that carries it.
        assertEquals(listOf("frame", "reconfigured", "frame"), listener.events)
        val reconfigured = listener.configurations.single()
        assertEquals(1L, reconfigured.revision.value, "revisions advance exactly one step")
        assertEquals(PhysicalSize(4, 8), reconfigured.size)
        assertEquals(1L, listener.frames[1].configurationRevision)
    }

    // -- the frame pipeline: native I420 with exact planes; conversion with the fallback diagnostic -------

    @Test
    fun nativeI420FrameYieldsThreeExactPlanes() = runTest {
        val track = ScriptedTrack()
        val readable = ScriptedReadable()
        val frame = readable.frame(
            frameShape = shape(format = "I420", timestampUs = 1_000_000L, durationUs = 33_333L),
            allocationSizeBytes = 24L,
            planes = i420Planes(),
        )
        val pump = WebCapturePump(readable, track, CaptureRequest(), dispatcher())
        val listener = RecordingListener(diagnostics = { pump.diagnostics })

        val start = pump.start(listener, maxFrameBytes = 4096L).started()
        assertEquals(PixelFormat.I420, start.configuration.format)
        assertEquals(AlphaMode.Opaque, start.configuration.alphaMode, "planar chroma formats carry no alpha")
        testScheduler.runCurrent()

        val delivered = listener.frames.single()
        assertEquals(PixelFormat.I420, delivered.format)
        assertEquals(3, delivered.planes.size, "the SPI's own layout validation ran at construction")
        val planes = delivered.planes.map { it.layout to it.bytes }
        assertEquals(PixelPlaneLayout(4, 4, 4, 1, 16, 1, 1), planes[0].first)
        assertEquals(PixelPlaneLayout(2, 2, 2, 1, 4, 2, 2), planes[1].first)
        assertEquals(PixelPlaneLayout(2, 2, 2, 1, 4, 2, 2), planes[2].first)
        assertEquals(16, planes[0].second.size)
        assertEquals(listOf<Byte>(16, 17, 18, 19), planes[1].second.toList(), "the plane bytes are the browser's, transferred")
        assertEquals(1_000_000L.microseconds, delivered.sourceTimestamp)
        assertEquals(33_333L.microseconds, delivered.duration)
        assertEquals(ColorPrimaries.Unknown, delivered.colorEncoding.primaries, "a frame with no colorSpace maps to the unknown form")
        // The frame handle was read once and closed after its copy: no browser object is retained.
        assertEquals(1, frame.closeCount)
        assertEquals(listOf<String?>(null, null, null), frame.allocationFormats + frame.copyFormats, "a native frame is bounded (start, copy time) and copied in its own format")
    }

    @Test
    fun conversionRequestRecordsBackendFallbackBeforeTheFrame() = runTest {
        val track = ScriptedTrack()
        val readable = ScriptedReadable()
        // A "BGRA" native — not one of the promised three — is converted to Rgba8 via copyTo.
        val frame = readable.frame(
            frameShape = shape(format = "BGRA"),
            allocationSizeBytes = 64L,
            planes = rgbaPlanes(),
        )
        val pump = WebCapturePump(readable, track, CaptureRequest(), dispatcher())
        val listener = RecordingListener(diagnostics = { pump.diagnostics })

        val start = pump.start(listener, maxFrameBytes = 4096L).started()
        assertEquals(PixelFormat.Rgba8, start.configuration.format, "the effective configuration names the conversion")
        testScheduler.runCurrent()

        assertEquals(1, frame.copyCount)
        assertEquals("RGBA", frame.copyFormats.single(), "the conversion travels as copyTo's format option")
        assertEquals(listOf<String?>("RGBA", "RGBA"), frame.allocationFormats, "the bound is read for the converted size, at start and at copy time")
        assertEquals(PixelFormat.Rgba8, listener.frames.single().format)

        val fallback = pump.diagnostics.single()
        assertEquals(
            CaptureDiagnostic.BackendFallback(requested = null, effective = PixelFormat.Rgba8, stamp = fallback.stamp),
            fallback,
            "the request named no format; the backend fell back from the native word to Rgba8",
        )
        // Ordering: the diagnostic existed before the frame it concerns was delivered.
        assertEquals(1, listener.diagnosticsAtFrame.single(), "the fallback is recorded before its frame")
    }

    @Test
    fun timestampsMapFromMicrosecondsAndHonestNulls() = runTest {
        val track = ScriptedTrack()
        val readable = ScriptedReadable()
        readable.frame(
            frameShape = shape(timestampUs = 1_234_567L, durationUs = 41_666L),
        )
        val pump = WebCapturePump(readable, track, CaptureRequest(), dispatcher())
        val listener = RecordingListener(diagnostics = { pump.diagnostics })

        pump.start(listener, maxFrameBytes = 4096L).started()
        testScheduler.runCurrent()
        assertEquals(listOf("frame"), listener.events)

        val awkward = ScriptedFrame(
            shape(timestampUs = -5L, durationUs = 0L),
            allocationSizeBytes = 64L,
            copyResult = rgbaPlanes(),
        )
        readable.offer(WebFrameRead.Frame(awkward))
        testScheduler.runCurrent()

        assertEquals(2, listener.frames.size)
        assertEquals(1_234_567L.microseconds, listener.frames[0].sourceTimestamp)
        assertEquals(41_666L.microseconds, listener.frames[0].duration)
        assertNull(listener.frames[1].sourceTimestamp, "a negative browser timestamp is no source instant")
        assertNull(listener.frames[1].duration, "a non-positive browser duration is no duration")
    }

    // -- stop, after-stop, and the exactly-once close ------------------------------------------------------

    @Test
    fun requestStopTerminatesRequestedAndReleasesExactlyOnce() = runTest {
        val track = ScriptedTrack()
        val readable = ScriptedReadable()
        readable.frame()
        val pump = WebCapturePump(readable, track, CaptureRequest(), dispatcher())
        val listener = RecordingListener(diagnostics = { pump.diagnostics })

        val start = pump.start(listener, maxFrameBytes = 4096L).started()
        testScheduler.runCurrent()
        assertEquals(listOf("frame"), listener.events)

        pump.requestStop()
        testScheduler.runCurrent()

        assertEquals(listOf("frame", "terminated"), listener.events)
        assertEquals(
            CapturePortTermination.Outcome(CaptureOutcome.Stopped(CaptureStopReason.Requested)),
            listener.terminations.single(),
        )
        assertEquals(1, track.stopCount, "the app's stop performs the browser's own track stop")
        assertEquals(1, readable.closeCount)

        // Idempotent: further stops, closes and the stream close add no second termination or release.
        pump.requestStop()
        start.stream.close()
        start.stream.close()
        pump.close()
        testScheduler.runCurrent()
        assertEquals(1, listener.terminations.size)
        assertEquals(1, track.stopCount)
        assertEquals(1, readable.closeCount)
    }

    @Test
    fun framesAfterStopAreNeverDelivered() = runTest {
        val track = ScriptedTrack()
        val readable = ScriptedReadable()
        readable.frame()
        val pump = WebCapturePump(readable, track, CaptureRequest(), dispatcher())
        val listener = RecordingListener(diagnostics = { pump.diagnostics })

        pump.start(listener, maxFrameBytes = 4096L).started()
        testScheduler.runCurrent()
        pump.requestStop()
        testScheduler.runCurrent()
        assertEquals(listOf("frame", "terminated"), listener.events)

        // The browser's read was already in flight: it resolves after the stop. The frame is released,
        // never delivered — the mutation (dropping the post-read stop check) makes this fail.
        val late = ScriptedFrame(shape(), allocationSizeBytes = 64L, copyResult = rgbaPlanes())
        readable.offer(WebFrameRead.Frame(late))
        testScheduler.runCurrent()

        assertEquals(1, listener.frames.size, "no frame is delivered after the termination")
        assertEquals(1, late.closeCount, "the late frame's handle is released instead")
        assertEquals(1, listener.terminations.size, "the stop stays the single terminal fact")
    }

    @Test
    fun closeIsIdempotentAcrossReservationStreamAndStop() = runTest {
        val track = ScriptedTrack()
        val readable = ScriptedReadable()
        val dom = StreamCaptureDom(processor = ScriptedProcessorFactory(readable))
        dom.pickResults += WebDisplayMediaPick.Picked(track)
        val port = capturePort(dom)
        val reservation = port.reserve(CapturePortTarget.HostChoice, CaptureRequest()).reserved()
        val listener = RecordingListener()

        readable.frame()
        val start = reservation.start(listener, maxFrameBytes = 4096L).started()
        testScheduler.runCurrent()
        assertEquals(listOf("frame"), listener.events, "the reservation streams through the port")

        // Every close path fires; the release is still exactly once and the termination still single.
        start.stream.close()
        reservation.close()
        reservation.close()
        track.endExternally() // even a late browser revocation cannot double the terminal fact
        testScheduler.runCurrent()

        assertEquals(1, track.stopCount)
        assertEquals(1, track.closeCount)
        assertEquals(1, readable.closeCount)
        assertEquals<List<CapturePortTermination>>(
            listOf(CapturePortTermination.Outcome(CaptureOutcome.Stopped(CaptureStopReason.Requested))),
            listener.terminations,
        )
        assertEquals(listOf("frame", "terminated"), listener.events)
    }

    // -- the port's HostChoice route: source description, admission, hints ---------------------------------

    @Test
    fun hostChoiceReserveRoutesToThePickedSourceAndStreams() = runTest {
        val track = ScriptedTrack()
        val readable = ScriptedReadable()
        val factory = ScriptedProcessorFactory(readable)
        val dom = StreamCaptureDom(processor = factory)
        dom.pickResults += WebDisplayMediaPick.Picked(track)
        val port = capturePort(dom)

        val reservation = port.reserve(CapturePortTarget.HostChoice, CaptureRequest()).reserved()
        assertEquals(
            CapturePortSource(CapturePortSourceKey("web-host-picker", 0L), CaptureSourceKind.Display, null, null),
            reservation.source,
            "the picked source is detached, display-kinded, and honest about knowing nothing else",
        )
        assertEquals(1, dom.pickCalls)
        assertEquals(2, dom.processorCalls, "probed once at construction (the capability) and once at reserve")

        val listener = RecordingListener()
        readable.frame()
        val start = reservation.start(listener, maxFrameBytes = 4096L).started()
        assertEquals(1, factory.builds, "the processor is built once, at start")
        testScheduler.runCurrent()
        assertEquals(listOf("frame"), listener.events)
        assertEquals(PhysicalSize(4, 4), start.configuration.size)
    }

    @Test
    fun hostChoiceRegionIsRefusedBeforeAnyPicker() = runTest {
        val dom = StreamCaptureDom(processor = ScriptedProcessorFactory(ScriptedReadable()))
        // A pick is staged and MUST still be staged afterwards: the refusal precedes the browser.
        dom.pickResults += WebDisplayMediaPick.Picked(ScriptedTrack())
        val port = capturePort(dom)

        val result = port.reserve(
            CapturePortTarget.HostChoice,
            CaptureRequest(region = CaptureRegion(PhysicalRect(PhysicalPoint(0, 0), PhysicalSize(4, 4)))),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            result,
            "the browser picks its own bounds — the AppKit admission refusal for region on a picker target",
        )
        assertEquals(0, dom.pickCalls, "the region refusal precedes every picker call")
        assertEquals(1, dom.pickResults.size, "the staged pick was never consumed")
    }

    @Test
    fun hostChoicePassesTheRequestHintsToThePicker() = runTest {
        val dom = StreamCaptureDom(processor = ScriptedProcessorFactory(ScriptedReadable()))
        dom.pickResults += WebDisplayMediaPick.Picked(ScriptedTrack())
        val port = capturePort(dom)

        port.reserve(
            CapturePortTarget.HostChoice,
            CaptureRequest(cursorMode = CaptureCursorMode.Hidden, minimumFrameInterval = 0.5.seconds),
        ).reserved()
        assertEquals(listOf<Pair<String?, Double?>>("never" to 2.0), dom.recordedHints)

        val embedded = StreamCaptureDom(processor = ScriptedProcessorFactory(ScriptedReadable()))
        embedded.pickResults += WebDisplayMediaPick.Picked(ScriptedTrack())
        val secondPort = capturePort(embedded)
        secondPort.reserve(
            CapturePortTarget.HostChoice,
            CaptureRequest(cursorMode = CaptureCursorMode.EmbeddedWhenAvailable),
        ).reserved()
        assertEquals(listOf<Pair<String?, Double?>>("motion" to null), embedded.recordedHints)
    }

    @Test
    fun hostChoiceReserveWithoutAProcessorRefusesAndReleasesThePick() = runTest {
        val track = ScriptedTrack()
        val dom = StreamCaptureDom(processor = null)
        dom.pickResults += WebDisplayMediaPick.Picked(track)
        val port = capturePort(dom)

        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureCollectFrames)),
            port.reserve(CapturePortTarget.HostChoice, CaptureRequest()),
            "a port that cannot pump refuses the reservation, not the frames",
        )
        assertEquals(1, track.closeCount, "the picked stream is released: never a live capture behind a lost pick")
        assertEquals(1, track.stopCount)
    }

    // -- the pure mapping: decision 6's tier tables ----------------------------------------------------------

    @Test
    fun mappingTargetFormatFollowsTheDecisionSixTiers() {
        // A portable native word is delivered as itself, whatever the preference said.
        assertEquals(WebFormatPlan.Native(PixelFormat.I420), WebCaptureMapping.targetFormat(emptyList(), "I420"))
        assertEquals(WebFormatPlan.Native(PixelFormat.Rgba8), WebCaptureMapping.targetFormat(listOf(PixelFormat.I420), "RGBA"))
        assertEquals(WebFormatPlan.Native(PixelFormat.Nv12), WebCaptureMapping.targetFormat(listOf(PixelFormat.Nv12), "NV12"))

        // A non-portable native converts: to the first promised preference, else to Rgba8.
        assertEquals(
            WebFormatPlan.Converted(PixelFormat.Rgba8, requested = null),
            WebCaptureMapping.targetFormat(emptyList(), "BGRA"),
        )
        assertEquals(
            WebFormatPlan.Converted(PixelFormat.I420, requested = PixelFormat.I420),
            WebCaptureMapping.targetFormat(listOf(PixelFormat.I420), "BGRA"),
        )
        assertEquals(
            WebFormatPlan.Converted(PixelFormat.Rgba8, requested = PixelFormat.Bgra8),
            WebCaptureMapping.targetFormat(listOf(PixelFormat.Bgra8), "BGRA"),
            "a preference outside the promised three falls back, and the diagnostic says so",
        )
        assertEquals(
            WebFormatPlan.Converted(PixelFormat.Rgba8, requested = null),
            WebCaptureMapping.targetFormat(emptyList(), "I420A"),
        )
    }

    @Test
    fun mappingColorEncodingMapsTheBrowserWordsUnknownSafe() {
        val unknown = ColorEncoding(
            ColorPrimaries.Unknown,
            TransferFunction.Unknown,
            MatrixCoefficients.Unknown,
            ColorRange.Unknown,
            HdrMetadata.Unknown,
        )
        assertEquals(unknown, WebCaptureMapping.colorEncoding(shape(colorSpace = null)))

        assertEquals(
            ColorEncoding(
                ColorPrimaries.Bt709,
                TransferFunction.Srgb,
                MatrixCoefficients.Identity,
                ColorRange.Full,
                HdrMetadata.None,
            ),
            WebCaptureMapping.colorEncoding(
                shape(colorSpace = WebColorSpaceShape("bt709", "iec61966-2-1", "rgb", true)),
            ),
        )
        assertEquals(
            ColorEncoding(
                ColorPrimaries.Bt601,
                TransferFunction.Bt1886,
                MatrixCoefficients.Bt601,
                ColorRange.Limited,
                HdrMetadata.None,
            ),
            WebCaptureMapping.colorEncoding(
                shape(colorSpace = WebColorSpaceShape("smpte170m", "smpte170m", "smpte170m", false)),
            ),
        )
        assertEquals(
            ColorEncoding(
                ColorPrimaries.Bt2020,
                TransferFunction.Pq,
                MatrixCoefficients.Bt2020NonConstant,
                ColorRange.Unknown,
                HdrMetadata.None,
            ),
            WebCaptureMapping.colorEncoding(
                shape(colorSpace = WebColorSpaceShape("bt2020", "smpte2084", "bt2020-ncl", null)),
            ),
        )
        assertEquals(
            ColorEncoding(
                ColorPrimaries.Unknown,
                TransferFunction.Hlg,
                MatrixCoefficients.Bt601,
                ColorRange.Full,
                HdrMetadata.None,
            ),
            WebCaptureMapping.colorEncoding(
                shape(colorSpace = WebColorSpaceShape("anything-else", "arib-std-b67", "bt470bg", true)),
            ),
        )
    }

    @Test
    fun mappingAlphaModePerFormatAndSource() {
        assertEquals(AlphaMode.Opaque, WebCaptureMapping.alphaMode(PixelFormat.I420, canvasSourced = false))
        assertEquals(AlphaMode.Opaque, WebCaptureMapping.alphaMode(PixelFormat.Nv12, canvasSourced = false))
        assertEquals(
            AlphaMode.Unknown,
            WebCaptureMapping.alphaMode(PixelFormat.Rgba8, canvasSourced = false),
            "a display frame's alpha is the browser's silence",
        )
        assertEquals(AlphaMode.Premultiplied, WebCaptureMapping.alphaMode(PixelFormat.Rgba8, canvasSourced = true))
        assertEquals(AlphaMode.Unknown, WebCaptureMapping.alphaMode(PixelFormat.Bgra8, canvasSourced = false))
    }
}
