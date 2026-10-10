package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.capture.AlphaMode
import org.graphiks.kadre.capture.CaptureConfiguration
import org.graphiks.kadre.capture.CaptureOutcome
import org.graphiks.kadre.capture.CaptureRegion
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSourceKind
import org.graphiks.kadre.capture.CaptureStopReason
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.CapturePortFrame
import org.graphiks.kadre.internal.runtime.CapturePortReservation
import org.graphiks.kadre.internal.runtime.CapturePortSource
import org.graphiks.kadre.internal.runtime.CapturePortSourceKey
import org.graphiks.kadre.internal.runtime.CapturePortStreamListener
import org.graphiks.kadre.internal.runtime.CapturePortStreamStart
import org.graphiks.kadre.internal.runtime.CapturePortTarget
import org.graphiks.kadre.internal.runtime.CapturePortTermination
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalSize
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The `Surface` streaming vertical, driven over scripted seam doubles the way
 * [WebCaptureStreamTest] drives the `HostChoice` one: the primary canvas is a double whose
 * `captureStream` is counted, every frame is staged, and every release effect is counted.
 *
 * The invariants pinned here are the task's test list: the surface id must be the session's own
 * registered primary surface (the runtime's admission checks only the capability — the refusal of
 * a foreign id is the port's, in the runtime's own unresolvable-target form); the canvas stream
 * pumps with the canvas-sourced alpha answer (`Premultiplied` for Rgba8); a region request stages
 * decision 8's `visibleRect` crop with the crop frame closed exactly once after its copy; a
 * non-canvas element refuses consistently with the frozen capability's cause; the close paths
 * release the stream exactly once; and the track's external end completes the source.
 */
class WebCaptureSurfaceTest {

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

    /** The attach element as the double holds it: every stream start is counted, per staged track. */
    private class ScriptedCanvas(vararg tracks: ScriptedTrack) : WebDomCanvas {
        private val staged = ArrayDeque(tracks.toList())
        var captureStreamCalls = 0
            private set

        override fun captureStream(): WebDomVideoTrack {
            captureStreamCalls += 1
            return staged.removeFirstOrNull() ?: error("no staged canvas track")
        }
    }

    /**
     * A frame double with decision 8's crop under test control: the staged [cropTo] rect is
     * recorded, the answer is a staged frame, and both the crop's copy and each handle's release
     * are counted — the exactly-once crop invariants are assertions over these counts.
     */
    private class ScriptedFrame(
        override val shape: WebVideoFrameShape,
        private val allocationSizeBytes: Long,
        private val copyResult: List<WebPlaneBytes>,
    ) : WebVideoFrame {
        var copyCount = 0
            private set
        var closeCount = 0
            private set
        val stagedRects = mutableListOf<WebVisibleRect>()
        var cropResult: WebVideoFrame? = null

        override fun allocationSize(format: String?): Long = allocationSizeBytes

        override suspend fun copyTo(format: String?): List<WebPlaneBytes> {
            copyCount += 1
            return copyResult
        }

        override fun cropTo(rect: WebVisibleRect): WebVideoFrame {
            stagedRects += rect
            return cropResult ?: error("no crop frame staged for the browser's crop")
        }

        override fun close() {
            closeCount += 1
        }
    }

    /** A readable double: staged reads answer immediately, later offers need a live waiter. */
    private class ScriptedReadable : WebFrameReadable {
        var closeCount = 0
            private set
        private val queued = ArrayDeque<WebFrameRead>()
        private var pending: CancellableContinuation<WebFrameRead>? = null

        fun queue(read: WebFrameRead) {
            queued += read
        }

        fun offer(read: WebFrameRead) {
            val waiting = pending
            pending = null
            if (waiting != null && waiting.isActive) {
                waiting.resume(read) { _, _, _ -> }
            } else {
                // No live waiter: the chunk is discarded at the seam, its frame closed — the way
                // the real realizations discard a frame nobody will pump.
                (read as? WebFrameRead.Frame)?.frame?.close()
            }
        }

        override suspend fun read(): WebFrameRead = suspendCancellableCoroutine { continuation ->
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
     * The stream listener double: the event order IS the evidence — terminations and frames are
     * assertions over this list.
     */
    private class RecordingListener : CapturePortStreamListener {
        val events = mutableListOf<String>()
        val configurations = mutableListOf<CaptureConfiguration>()
        val frames = mutableListOf<CapturePortFrame>()
        val terminations = mutableListOf<CapturePortTermination>()

        override fun onFrame(frame: CapturePortFrame) {
            events += "frame"
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

    /**
     * The capture seam double for the surface tests: the attach element's canvas (or none) and the
     * pump primitive, with every probe recorded so the zero-interaction invariants are assertions
     * over this list.
     */
    private class SurfaceCaptureDom(
        val canvas: WebDomCanvas?,
        private val processor: WebTrackProcessorFactory?,
    ) : WebCaptureDom {
        val interactions = mutableListOf<String>()

        override fun isSecureContext(): Boolean {
            interactions += "isSecureContext"
            return true
        }

        override fun queryDisplayCapturePermission(): WebCapturePermissionQueryResult? =
            WebCapturePermissionQueryResult.NotDetermined

        override fun readDisplayCapturePermission(listener: (WebCapturePermissionQueryResult?) -> Unit) {
            interactions += "readDisplayCapturePermission"
        }

        override fun hasDisplayMedia(): Boolean {
            interactions += "hasDisplayMedia"
            return true
        }

        override suspend fun pickDisplayMedia(cursorHint: String?, frameRateHint: Double?): WebDisplayMediaPick =
            error("the surface path never reaches the consent machinery")

        override fun processorFactory(): WebTrackProcessorFactory? {
            interactions += "processorFactory"
            return processor
        }

        override fun canvasForSurface(): WebDomCanvas? {
            interactions += "canvasForSurface"
            return canvas
        }

        override fun close() = Unit
    }

    // -- scripted frame stock -----------------------------------------------------------------------------

    private fun shape(
        format: String? = "RGBA",
        width: Int = 4,
        height: Int = 4,
        timestampUs: Long? = null,
        durationUs: Long? = null,
    ) = WebVideoFrameShape(format, width, height, timestampUs, durationUs, colorSpace = null)

    /** One whole-frame 4x4 RGBA plane, byte-exact and independent of the implementation under test. */
    private fun rgbaPlanes(): List<WebPlaneBytes> =
        listOf(WebPlaneBytes(rowStride = 16, bytes = ByteArray(64) { it.toByte() }))

    /** Stages one 4x4 RGBA frame as the readable's next answer. */
    private fun ScriptedReadable.frame(): ScriptedFrame {
        val frame = ScriptedFrame(shape(), allocationSizeBytes = 64L, copyResult = rgbaPlanes())
        queue(WebFrameRead.Frame(frame))
        return frame
    }

    private fun TestScope.dispatcher(): CoroutineContext = StandardTestDispatcher(testScheduler)

    /** The port under test, its pump loop bound to this test's scheduler so runs are deterministic. */
    private fun TestScope.capturePort(dom: SurfaceCaptureDom): WebCapturePort =
        WebCapturePort(dom, primarySurfaceElementIsCanvas = dom.canvas != null, captureLoopContext = dispatcher())

    private fun KadreResult<CapturePortStreamStart>.started(): CapturePortStreamStart =
        assertIs<KadreResult.Success<CapturePortStreamStart>>(this).value

    private fun KadreResult<CapturePortReservation>.reserved(): CapturePortReservation =
        assertIs<KadreResult.Success<CapturePortReservation>>(this).value

    // -- the surface id must be the session's own primary surface -------------------------------------------

    @Test
    fun foreignSurfaceIdIsRefusedInTheRuntimeOwnUnresolvableTargetFormWithoutTouchingTheSeam() = runTest {
        // The runtime's admission (capabilityAdmission) checks only the surface capability and
        // forwards the id — a foreign id is the port's refusal to make. Its form mirrors the
        // runtime's own unresolvable-target refusal (sourceAdmission's InvalidRequest field path)
        // and the AppKit reference's Unknown-surface resolution: `request.target`.
        val track = ScriptedTrack()
        val dom = SurfaceCaptureDom(canvas = ScriptedCanvas(track), processor = ScriptedProcessorFactory(ScriptedReadable()))
        val port = capturePort(dom)
        val registered = RuntimeProcessIds.nextSurfaceId()
        port.registerPrimarySurface(registered)
        val atRegistration = dom.interactions.toList()

        val result = port.reserve(CapturePortTarget.Surface(RuntimeProcessIds.nextSurfaceId()), CaptureRequest())

        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("request.target")),
            result,
            "an id the session never registered names no surface this port can honestly capture",
        )
        assertEquals(atRegistration, dom.interactions, "the refusal adds not one seam interaction")
        assertEquals(0, track.stopCount)
        // Mutation: dropping the id check lets the route reach captureStream — the browser effect
        // would start behind a refusal. The canvas was never asked to stream.
        assertEquals(0, (dom.canvas as ScriptedCanvas).captureStreamCalls)

        // A port whose session registered nothing refuses every id the same way.
        val unwired = capturePort(SurfaceCaptureDom(canvas = ScriptedCanvas(track), processor = null))
        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("request.target")),
            unwired.reserve(CapturePortTarget.Surface(RuntimeProcessIds.nextSurfaceId()), CaptureRequest()),
        )
    }

    // -- the canvas present: reservation, start, and the canvas-sourced alpha answer ------------------------

    @Test
    fun canvasSurfaceReservesStreamsAndAnswersPremultipliedAlphaOnTheRgba8Path() = runTest {
        val track = ScriptedTrack()
        val readable = ScriptedReadable()
        val canvas = ScriptedCanvas(track)
        val factory = ScriptedProcessorFactory(readable)
        val dom = SurfaceCaptureDom(canvas = canvas, processor = factory)
        val port = capturePort(dom)
        val primary = RuntimeProcessIds.nextSurfaceId()
        port.registerPrimarySurface(primary)

        val reservation = port.reserve(CapturePortTarget.Surface(primary), CaptureRequest()).reserved()
        assertEquals(
            CapturePortSource(CapturePortSourceKey("web-surface", 0L), CaptureSourceKind.HostSurface, null, null),
            reservation.source,
            "the surface source is detached, host-surface-kinded, and honest about knowing nothing else",
        )
        assertEquals(1, canvas.captureStreamCalls, "the browser effect runs once, at reserve")
        assertEquals(0, factory.builds, "the processor is built at start, never at reserve")

        val frame = readable.frame()
        val listener = RecordingListener()
        val start = reservation.start(listener, maxFrameBytes = 4096L).started()
        assertEquals(1, factory.builds)
        assertEquals(PixelFormat.Rgba8, start.configuration.format)
        assertEquals(
            AlphaMode.Premultiplied,
            start.configuration.alphaMode,
            "a canvas-sourced Rgba8 frame is the canvas's own premultiplied compositing (decision 6)",
        )
        testScheduler.runCurrent()

        assertEquals(listOf("frame"), listener.events, "the first frame flows after start returned")
        val delivered = listener.frames.single()
        assertEquals(PhysicalSize(4, 4), delivered.size)
        assertEquals(PixelFormat.Rgba8, delivered.format)
        assertEquals(AlphaMode.Premultiplied, delivered.alphaMode)
        // The SPI's own Rgba8 layout validation ran at construction; the layout is asserted too.
        assertEquals(1, delivered.planes.size)
        assertEquals(16, delivered.planes.single().layout.rowStride)
        assertEquals(64, delivered.planes.single().bytes.size)
        assertEquals(1, frame.closeCount, "the frame handle was released after its copy")
        // Mutation: a pump without the canvas-sourced flag answers Unknown — every alpha assertion
        // above fails.
    }

    // -- decision 8: the region request stages the visibleRect crop ------------------------------------------

    @Test
    fun regionRequestStagesTheVisibleRectAndClosesTheCropFrameExactlyOnceAfterItsCopy() = runTest {
        val track = ScriptedTrack()
        val readable = ScriptedReadable()
        val dom = SurfaceCaptureDom(canvas = ScriptedCanvas(track), processor = ScriptedProcessorFactory(readable))
        val port = capturePort(dom)
        val primary = RuntimeProcessIds.nextSurfaceId()
        port.registerPrimarySurface(primary)
        val region = CaptureRegion(PhysicalRect(PhysicalPoint(2, 1), PhysicalSize(2, 2)))

        val reservation = port
            .reserve(CapturePortTarget.Surface(primary), CaptureRequest(region = region))
            .reserved()

        // The browser's crop answer: a 2x2 frame whose copy is byte-exact for the cropped size.
        val original = ScriptedFrame(shape(), allocationSizeBytes = 64L, copyResult = rgbaPlanes())
        val cropped = ScriptedFrame(
            shape(width = 2, height = 2),
            allocationSizeBytes = 16L,
            copyResult = listOf(WebPlaneBytes(rowStride = 8, bytes = ByteArray(16) { it.toByte() })),
        )
        original.cropResult = cropped
        readable.queue(WebFrameRead.Frame(original))

        val listener = RecordingListener()
        val start = reservation.start(listener, maxFrameBytes = 4096L).started()
        testScheduler.runCurrent()

        // The crop was staged on the double, exactly the request's visibleRect.
        assertEquals(listOf(WebVisibleRect(2, 1, 2, 2)), original.stagedRects)
        // The original handle ended when the crop was staged: never copied, never leaked.
        assertEquals(0, original.copyCount, "the copy reads the crop frame, not the whole frame")
        assertEquals(1, original.closeCount, "the crop constructor clones — the original is released at staging")
        // The crop frame: copied once, closed exactly once after its copy.
        assertEquals(1, cropped.copyCount)
        assertEquals(1, cropped.closeCount, "the crop frame is closed exactly once, after its copy")

        assertEquals(PhysicalSize(2, 2), start.configuration.size, "the effective size is the cropped size")
        assertEquals(region, start.configuration.region, "the effective configuration names the crop")
        assertEquals(listOf("frame"), listener.events)
        val delivered = listener.frames.single()
        assertEquals(PhysicalSize(2, 2), delivered.size)
        assertEquals(AlphaMode.Premultiplied, delivered.alphaMode)
        assertEquals(8, delivered.planes.single().layout.rowStride)
        assertEquals(16, delivered.planes.single().bytes.size)
        // Mutations: skipping the crop fails the staged rect and size; closing the crop frame before
        // (or twice after) its copy fails the close counts; closing the original after the copy
        // instead fails the original's counts.
    }

    // -- the non-canvas backstop: consistent with the frozen capability's cause -------------------------------

    @Test
    fun nonCanvasSurfaceRefusesWithTheFrozenCapabilityCauseAsTheConsistentBackstop() = runTest {
        // The capability probe froze `Unsupported(CaptureOpen)` for a non-canvas attach element
        // (cause: surface-not-a-canvas), so the runtime's own admission refuses a Surface target
        // before the port is ever reached. A reserve that reaches the port through any other path
        // must refuse with the SAME failure form — never invent a different verdict.
        val dom = SurfaceCaptureDom(canvas = null, processor = ScriptedProcessorFactory(ScriptedReadable()))
        val port = capturePort(dom)
        val primary = RuntimeProcessIds.nextSurfaceId()
        port.registerPrimarySurface(primary)
        val atConstruction = dom.interactions.toList()

        val result = port.reserve(CapturePortTarget.Surface(primary), CaptureRequest())

        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            result,
            "the same frozen cause the capability carries — the consistent backstop",
        )
        // The backstop probe ran (the canvas was lent, and there was none), so the refusal is the
        // port's own verdict, not a skipped check. Mutation: dropping the backstop either skips the
        // canvas lend entirely (this probe assertion fails) or proceeds to stream a surface the
        // frozen capability said was not there.
        assertEquals(
            listOf("canvasForSurface"),
            dom.interactions.drop(atConstruction.size),
            "the reserve lends the canvas exactly once, as the backstop probe",
        )
    }

    // -- the close paths: the stream is stopped exactly once ---------------------------------------------------

    @Test
    fun sessionCloseStopsTheCanvasStreamExactlyOnceThroughTheSurfaceReservationClosePaths() = runTest {
        // A reservation closed before it ever started: never a live canvas capture behind a closed
        // handle. Mutation: a missing release on the never-started path leaks the browser effect —
        // the stop count stays zero.
        val neverStartedTrack = ScriptedTrack()
        val neverStartedDom = SurfaceCaptureDom(
            canvas = ScriptedCanvas(neverStartedTrack),
            processor = ScriptedProcessorFactory(ScriptedReadable()),
        )
        val firstPort = capturePort(neverStartedDom)
        val firstPrimary = RuntimeProcessIds.nextSurfaceId()
        firstPort.registerPrimarySurface(firstPrimary)
        val unstarted = firstPort.reserve(CapturePortTarget.Surface(firstPrimary), CaptureRequest()).reserved()
        unstarted.close()
        unstarted.close()
        assertEquals(1, neverStartedTrack.stopCount, "the browser's own track stop, exactly once")
        assertEquals(1, neverStartedTrack.closeCount, "the stream handle released exactly once")

        // A started reservation: the stream's own close is the app's stop — one termination, one
        // release, however many close paths fire afterwards.
        val track = ScriptedTrack()
        val readable = ScriptedReadable()
        val dom = SurfaceCaptureDom(canvas = ScriptedCanvas(track), processor = ScriptedProcessorFactory(readable))
        val port = capturePort(dom)
        val primary = RuntimeProcessIds.nextSurfaceId()
        port.registerPrimarySurface(primary)
        val reservation = port.reserve(CapturePortTarget.Surface(primary), CaptureRequest()).reserved()
        readable.frame()
        val listener = RecordingListener()
        val start = reservation.start(listener, maxFrameBytes = 4096L).started()
        testScheduler.runCurrent()
        assertEquals(listOf("frame"), listener.events)

        start.stream.close()
        reservation.close()
        reservation.close()
        testScheduler.runCurrent()

        assertEquals<List<CapturePortTermination>>(
            listOf(CapturePortTermination.Outcome(CaptureOutcome.Stopped(CaptureStopReason.Requested))),
            listener.terminations,
            "the surface stream stops exactly once, as the requested stop decision 7 maps it",
        )
        assertEquals(1, track.stopCount)
        assertEquals(1, track.closeCount)
        assertEquals(1, readable.closeCount, "the reader is released exactly once across every close path")

        // A browser frame still in flight after the stop is released, never delivered.
        val late = ScriptedFrame(shape(), allocationSizeBytes = 64L, copyResult = rgbaPlanes())
        readable.offer(WebFrameRead.Frame(late))
        testScheduler.runCurrent()
        assertEquals(1, listener.frames.size, "no frame follows the stop")
        assertEquals(1, late.closeCount, "the late frame's handle is released instead")
    }

    // -- the track's external end: the same ruling as HostChoice ------------------------------------------------

    @Test
    fun trackEndedExternallyCompletesTheSurfaceSource() = runTest {
        val track = ScriptedTrack()
        val readable = ScriptedReadable()
        val dom = SurfaceCaptureDom(canvas = ScriptedCanvas(track), processor = ScriptedProcessorFactory(readable))
        val port = capturePort(dom)
        val primary = RuntimeProcessIds.nextSurfaceId()
        port.registerPrimarySurface(primary)
        val reservation = port.reserve(CapturePortTarget.Surface(primary), CaptureRequest()).reserved()

        val frame = readable.frame()
        val listener = RecordingListener()
        reservation.start(listener, maxFrameBytes = 4096L).started()
        testScheduler.runCurrent()
        assertEquals(listOf("frame"), listener.events)

        // The canvas capture ends not by our stop — the browser releases the element's stream.
        track.endExternally()
        testScheduler.runCurrent()

        assertEquals(
            listOf("frame", "terminated"),
            listener.events,
            "the external end follows the delivered frame",
        )
        assertEquals(
            CapturePortTermination.Outcome(CaptureOutcome.SourceCompleted),
            listener.terminations.single(),
            "the same ruling as HostChoice: the browser revoking the source completes it",
        )
        assertEquals(1, readable.closeCount, "the reader is released exactly once at the external end")
        assertEquals(1, track.stopCount)
        assertEquals(1, track.closeCount)
        assertEquals(1, frame.closeCount)
        // Mutation: mapping the external end to Stopped or Failed fails the termination equality.
    }
}
