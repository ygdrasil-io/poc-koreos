package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.capture.CaptureCadence
import org.graphiks.kadre.capture.CaptureConfiguration
import org.graphiks.kadre.capture.CaptureConfigurationRevision
import org.graphiks.kadre.capture.CaptureDiagnostic
import org.graphiks.kadre.capture.CaptureOrientation
import org.graphiks.kadre.capture.CaptureOutcome
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureStopReason
import org.graphiks.kadre.capture.PixelFormat
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
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

/** The failure domain of the frame pipe: every processor/read/copy refusal of the running stream. */
internal const val STREAM_DOMAIN = "capture-stream"

/**
 * The web capture frame pump (plan decision 6): one [WebFrameReadable] read at a time, each
 * VideoFrame bounded by [start]'s `maxFrameBytes` — the browser's own `allocationSize` is read
 * before any buffer exists — copied once into Kotlin-owned plane bytes, and handed to the listener
 * as a detached [CapturePortFrame].
 *
 * **The effective configuration is derived from the first frame and published before any frame
 * flows**: the browser says nothing honest about size or format until a frame arrives, so [start]
 * reads frame one, derives the complete [CaptureConfiguration] (revision 0), and returns it as the
 * stream's start value; the listener hears no callback until [start] has returned. A later frame
 * whose size or delivered format differs publishes `onReconfigured` — one revision ahead, the
 * runtime's own advance-by-one discipline — before the frame that carries it.
 *
 * **The frame bound is terminal.** A frame whose `allocationSize` exceeds the limit is closed and
 * never copied: the first frame fails the start with `ResourceLimitExceeded`; a running frame
 * terminates the stream through the listener. The never-copy invariant is structural — the copy
 * call sits behind the bound check on every path.
 *
 * **Terminations are exactly once** (decision 7): the app's `requestStop` — the stream's own close
 * included — stops the reader, stops the track, and reports `Stopped(Requested)`; the track's
 * external end (the browser's "Stop sharing", which the DOM signals only as `ended`) reports
 * `SourceCompleted`; a pipe refusal reports `Failed(PlatformFailure(Web, "capture-stream", code))`.
 * Whichever lands first wins; every release path funnels into one idempotent release.
 *
 * **Cancellation releases.** [start] suspends in the first frame read; if that suspension is
 * cancelled, the reservation's stream state — reader and track — is released exactly once before
 * the cancellation surfaces, the SPI's rule for a backend operation cancelled before it can return.
 *
 * A conversion the pump performs (the native word outside the promised three) is recorded as a
 * `BackendFallback` diagnostic **before** the frame it concerns is delivered. The runtime's stream
 * SPI carries no diagnostics channel from backend to session, so the journal is observable here,
 * on the web layer that performed the conversion.
 *
 * The pump serves both web sources — the picked display track ([WebTrackReservation], `HostChoice`)
 * and the primary canvas's own stream ([WebSurfaceReservation], `Surface`). What differs travels as
 * constructor facts: [canvasSourced] turns the Rgba8 alpha answer into `Premultiplied` (decision 6 —
 * the canvas's own compositing, vs the browser's silence for a display frame), and a
 * [CaptureRequest.region] arrives already staged (decision 8 — the readable upstream crops, so the
 * configuration's size is the cropped size and the configuration itself names the region).
 */
internal class WebCapturePump(
    private val readable: WebFrameReadable,
    private val track: WebDomVideoTrack,
    private val request: CaptureRequest,
    private val loopContext: CoroutineContext = Dispatchers.Default,
    private val canvasSourced: Boolean = false,
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
    private var lastFallback: Pair<PixelFormat?, PixelFormat>? = null
    private val recordedDiagnostics = mutableListOf<CaptureDiagnostic>()
    private var nextStampSequence = 0L

    /** The diagnostics the pump recorded, oldest first — the conversions it performed among them. */
    val diagnostics: List<CaptureDiagnostic>
        get() = lock.withLock { recordedDiagnostics.toList() }

    /**
     * Starts the stream: reads frame one for the effective configuration, then hands the frame to
     * the loop that owns delivery from there. Returns the complete configuration before any frame.
     */
    suspend fun start(
        listener: CapturePortStreamListener,
        maxFrameBytes: Long,
    ): KadreResult<CapturePortStreamStart> {
        require(maxFrameBytes > 0L) { "maxFrameBytes must be positive" }
        val admission = lock.withLock {
            when {
                closed -> failure(KadreFailure.Closed(KadreResourceKind.CaptureSession))
                started -> failure(KadreFailure.AlreadyInUse(KadreResourceKind.CaptureCollector))
                else -> {
                    started = true
                    this.listener = listener
                    this.maxFrameBytes = maxFrameBytes
                    null
                }
            }
        }
        if (admission != null) return admission

        // The browser's revocation rides the track's own end — registered before the first read so
        // an end between reads is heard even while no read is in flight.
        track.addEndedListener(::onTrackEnded)

        val firstFrame = try {
            when (val read = readable.read()) {
                WebFrameRead.Ended -> {
                    release()
                    return failedStart("track-ended")
                }

                is WebFrameRead.Failed -> {
                    release()
                    return failedStart(read.code)
                }

                is WebFrameRead.Frame -> read.frame
            }
        } catch (cancellation: CancellationException) {
            // The SPI's cancellation contract: a suspending operation cancelled before it can
            // return releases the reservation's stream state exactly once.
            release()
            throw cancellation
        } catch (refused: WebCapturePipeException) {
            // Defensive only — through the seam's contract this catch cannot fire: a read delivers
            // its refusal as WebFrameRead.Failed and never throws. It stands so a realization bug
            // cannot breach the port→session error model with a raw Throwable (the runtime session
            // catches only CancellationException around reservation.start).
            release()
            return failedStart(refused.code)
        }

        val shape = firstFrame.shape
        val native = shape.format
        if (native == null || shape.width <= 0 || shape.height <= 0) {
            firstFrame.close()
            release()
            return failedStart(if (native == null) "unknown-frame-format" else "invalid-frame-size")
        }
        val plan = WebCaptureMapping.targetFormat(request.preferredFormats, native)
        val totalBytes = try {
            firstFrame.allocationSize(plan.conversionWordOrNull())
        } catch (refused: WebCapturePipeException) {
            firstFrame.close()
            release()
            return failedStart(refused.code)
        }
        if (totalBytes > maxFrameBytes) {
            // The bound applies before allocating or copying: the frame cannot fit, so it never will.
            firstFrame.close()
            release()
            lock.withLock { terminated = true }
            return KadreResult.Failure(
                KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, maxFrameBytes),
            )
        }
        val initial = configurationFor(shape, plan, revisionValue = 0L)
        lock.withLock { configuration = initial }

        // The loop's scope carries the injected context (the test scheduler in tests, the default
        // dispatcher in production) with its own supervisor job: a terminal frame failure must not
        // cancel anything but the loop itself.
        val pumpScope = CoroutineScope(loopContext + SupervisorJob())
        lock.withLock { scope = pumpScope }
        pumpScope.launch { run(firstFrame, plan) }
        return KadreResult.Success(CapturePortStreamStart(WebCaptureStream(this), initial))
    }

    /** The app's own stop: the stream's resources go, and the listener hears `Stopped(Requested)`. */
    fun requestStop() {
        terminate(CaptureOutcome.Stopped(CaptureStopReason.Requested))
    }

    /** Releases the stream's resources exactly once and seals the pump: a closed pump tells nothing. */
    fun close() {
        lock.withLock {
            closed = true
            terminated = true
        }
        release()
        scope?.cancel()
    }

    // -- the delivery loop --------------------------------------------------------------------------------

    private suspend fun run(firstFrame: WebVideoFrame, firstPlan: WebFormatPlan) {
        var frame: WebVideoFrame? = firstFrame
        try {
            while (true) {
                val current = frame ?: when (val read = readable.read()) {
                    WebFrameRead.Ended -> {
                        terminate(CaptureOutcome.SourceCompleted)
                        return
                    }

                    is WebFrameRead.Failed -> {
                        terminate(CaptureOutcome.Failed(pipeFailure(read.code)))
                        return
                    }

                    is WebFrameRead.Frame -> read.frame
                }
                // The read that was already in flight when the stop landed is released, never
                // delivered: no frame follows the terminal fact, whichever of them won.
                if (lock.withLock { terminated }) {
                    current.close()
                    return
                }

                val shape = current.shape
                val native = shape.format
                if (native == null || shape.width <= 0 || shape.height <= 0) {
                    current.close()
                    terminate(
                        CaptureOutcome.Failed(
                            pipeFailure(if (native == null) "unknown-frame-format" else "invalid-frame-size"),
                        ),
                    )
                    return
                }
                val plan = WebCaptureMapping.targetFormat(request.preferredFormats, native)
                var effective = lock.withLock { configuration } ?: return
                if (
                    shape.width != effective.size.width ||
                    shape.height != effective.size.height ||
                    plan.format != effective.format
                ) {
                    // The complete effective configuration is published — one revision ahead, the
                    // runtime's advance-by-one discipline — before the frame that carries it.
                    val next = configurationFor(shape, plan, revisionValue = nextRevision())
                    lock.withLock { configuration = next }
                    effective = next
                    listener?.onReconfigured(next)
                }
                if (plan is WebFormatPlan.Converted) recordFallback(plan)

                val conversionWord = plan.conversionWordOrNull()
                val totalBytes = try {
                    current.allocationSize(conversionWord)
                } catch (refused: WebCapturePipeException) {
                    current.close()
                    terminate(CaptureOutcome.Failed(pipeFailure(refused.code)))
                    return
                }
                if (totalBytes > maxFrameBytes) {
                    // Bound before copy — the oversized frame is closed, and the copy never ran.
                    current.close()
                    terminate(
                        CaptureOutcome.Failed(
                            KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, maxFrameBytes),
                        ),
                    )
                    return
                }
                val copied = try {
                    current.copyTo(conversionWord)
                } catch (refused: WebCapturePipeException) {
                    current.close()
                    terminate(CaptureOutcome.Failed(pipeFailure(refused.code)))
                    return
                }
                // The browser's handle ends here: the plane bytes are Kotlin-owned copies.
                current.close()
                val portFrame = buildPortFrame(shape, plan, effective, copied)
                if (portFrame == null) {
                    terminate(CaptureOutcome.Failed(pipeFailure("unexpected-plane-layout")))
                    return
                }
                // The stop that lands while this frame was being copied still wins the race.
                if (lock.withLock { terminated }) {
                    return
                }
                listener?.onFrame(portFrame)
                frame = null
            }
        } catch (cancellation: CancellationException) {
            frame?.close()
            release()
            throw cancellation
        } catch (unexpected: Throwable) {
            frame?.close()
            terminate(CaptureOutcome.Failed(pipeFailure("frame-pump-failed")))
        }
    }

    /**
     * Builds the detached frame from the copied planes. The layouts are the tightly-packed ones a
     * default whole-frame copy produces by definition, so the browser's row strides and plane sizes
     * must agree with them exactly — and the SPI's own portable-layout validation runs at
     * construction, so a frame this method returns is a frame the runtime accepts.
     */
    private fun buildPortFrame(
        shape: WebVideoFrameShape,
        plan: WebFormatPlan,
        configuration: CaptureConfiguration,
        copied: List<WebPlaneBytes>,
    ): CapturePortFrame? = try {
        val size = PhysicalSize(shape.width, shape.height)
        val expected = WebCaptureMapping.planeLayouts(plan.format, size)
        if (copied.size != expected.size) {
            null
        } else {
            val planes = copied.mapIndexed { index, plane ->
                val layout = expected[index]
                if (plane.rowStride != layout.rowStride || plane.bytes.size != layout.byteCount) {
                    return null
                }
                CapturePortPlane(layout, plane.bytes)
            }
            CapturePortFrame(
                size = size,
                format = plan.format,
                planes = planes,
                configurationRevision = configuration.revision.value,
                sourceTimestamp = WebCaptureMapping.sourceTimestamp(shape.timestampUs),
                duration = WebCaptureMapping.duration(shape.durationUs),
                discontinuity = null,
                colorEncoding = configuration.colorEncoding,
                alphaMode = configuration.alphaMode,
                orientation = CaptureOrientation.Upright,
            )
        }
    } catch (_: IllegalArgumentException) {
        // The SPI rejected the layout: the frame would have lied to the runtime.
        null
    }

    // -- configuration, diagnostics, terminal and release paths -------------------------------------------

    private fun configurationFor(
        shape: WebVideoFrameShape,
        plan: WebFormatPlan,
        revisionValue: Long,
    ): CaptureConfiguration = CaptureConfiguration(
        revision = CaptureConfigurationRevision(revisionValue),
        size = PhysicalSize(shape.width, shape.height),
        format = plan.format,
        colorEncoding = WebCaptureMapping.colorEncoding(shape),
        alphaMode = WebCaptureMapping.alphaMode(plan.format, canvasSourced),
        orientation = CaptureOrientation.Upright,
        cadence = CaptureCadence.Unknown,
        // The region is the request's own: a Surface's crop (decision 8 — the readable upstream
        // already crops, so the size above is the cropped size), and none for a HostChoice, whose
        // admission refused a region before the picker was ever launched.
        region = request.region,
        cursorMode = request.cursorMode,
    )

    /** Records the conversion diagnostic once per distinct (requested, effective) pair, in order. */
    private fun recordFallback(plan: WebFormatPlan.Converted) {
        val key = plan.requested to plan.format
        val changed = lock.withLock {
            if (lastFallback == key) false else {
                lastFallback = key
                true
            }
        }
        if (!changed) return
        val diagnostic = CaptureDiagnostic.BackendFallback(
            requested = plan.requested,
            effective = plan.format,
            stamp = nextStamp(),
        )
        lock.withLock { recordedDiagnostics += diagnostic }
    }

    private fun nextStamp(): EventStamp = lock.withLock {
        check(nextStampSequence < Long.MAX_VALUE) { "pump diagnostic sequence space exhausted" }
        EventStamp(SessionSequence(nextStampSequence++), SessionInstant(Duration.ZERO), null)
    }

    private fun nextRevision(): Long = lock.withLock {
        check(revision < Long.MAX_VALUE) { "capture configuration revision space exhausted" }
        revision += 1
        revision
    }

    /** The browser revoking the source: the DOM signals it only as the track's own end. */
    private fun onTrackEnded() {
        terminate(CaptureOutcome.SourceCompleted)
    }

    /** The terminal fact, exactly once: release first, then tell the listener, then stop the loop. */
    private fun terminate(outcome: CaptureOutcome) = terminate(CapturePortTermination.Outcome(outcome))

    private fun terminate(termination: CapturePortTermination) {
        val first = lock.withLock { if (terminated) false else { terminated = true; true } }
        if (!first) return
        release()
        listener?.onTerminated(termination)
        scope?.cancel()
    }

    /** The one release: reader, then the browser's own track stop, then the seam handle. Idempotent. */
    private fun release() {
        val targets = lock.withLock {
            if (released) null else {
                released = true
                readable to track
            }
        } ?: return
        targets.first.close()
        targets.second.stop()
        targets.second.close()
    }

    /**
     * Fails the start and seals the pump: the SPI lets a backend invoke listener callbacks only
     * after start returned successfully, so a failed start — and any late browser fact after it —
     * tells the listener nothing.
     */
    private fun failedStart(code: String): KadreResult.Failure {
        lock.withLock { terminated = true }
        return KadreResult.Failure(pipeFailure(code))
    }

    private fun pipeFailure(code: String): KadreFailure =
        KadreFailure.PlatformFailure(KadrePlatform.Web, STREAM_DOMAIN, code)

    private fun failure(failure: KadreFailure): KadreResult.Failure = KadreResult.Failure(failure)

    private fun WebFormatPlan.conversionWordOrNull(): String? =
        (this as? WebFormatPlan.Converted)?.let { WebCaptureMapping.conversionWord(it.format) }
}

/**
 * The running stream of one [WebCapturePump]: closing it is the app's own stop — non-blocking,
 * idempotent, and the exact path decision 7 maps to `Stopped(Requested)`.
 */
private class WebCaptureStream(private val pump: WebCapturePump) : CapturePortStream {
    private val lock = RuntimeLock()
    private var closed = false

    override fun close() {
        val first = lock.withLock { if (closed) false else { closed = true; true } }
        if (first) pump.requestStop()
    }
}

/**
 * The one reservation machinery over a granted track, whichever web source granted it: the picked
 * display track of the host picker (`HostChoice`) or the primary canvas's own `captureStream`
 * track (`Surface`, [WebSurfaceReservation]). Reserving started no frame production — [start]
 * builds the processor's reader, stages the request's region crop when one is carried (decision
 * 8's `Surface` ruling; a `HostChoice` request never carries a region, its admission refused one
 * before the picker), and launches the pump; [close] releases the stream state exactly once
 * however far the reservation got, and a never-started reservation never leaks a live capture.
 *
 * [canvasSourced] is the surface's flag alone: it turns the pump's Rgba8 alpha answer into
 * `Premultiplied` (decision 6 — the canvas's own compositing). The class is open only for that
 * subclass, which fixes the flag and names the surface vertical; the machinery is shared, never
 * duplicated.
 */
open internal class WebTrackReservation(
    override val source: CapturePortSource,
    private val track: WebDomVideoTrack,
    private val factory: WebTrackProcessorFactory,
    private val request: CaptureRequest,
    private val canvasSourced: Boolean = false,
    private val loopContext: CoroutineContext = Dispatchers.Default,
) : CapturePortReservation {
    private val lock = RuntimeLock()
    private var closed = false
    private var started = false
    private var pump: WebCapturePump? = null

    override suspend fun start(
        listener: CapturePortStreamListener,
        maxFrameBytes: Long,
    ): KadreResult<CapturePortStreamStart> {
        val admission = lock.withLock {
            when {
                closed -> failure(KadreFailure.Closed(KadreResourceKind.CaptureSession))
                started -> failure(KadreFailure.AlreadyInUse(KadreResourceKind.CaptureCollector))
                else -> {
                    started = true
                    null
                }
            }
        }
        if (admission != null) return admission

        val upstream = try {
            factory.processorFor(track)
        } catch (_: Throwable) {
            // The browser refused the processor for this track: the pipe was never built.
            return KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.Web, STREAM_DOMAIN, "track-processor-refused"),
            )
        }
        // Decision 8: the crop is staged between the reader and the pump, so every frame the pump
        // sees is already the visible-rect frame and the bound bounds the cropped frame.
        val readable = request.region?.let { WebCroppedFrameReadable(upstream, it) } ?: upstream
        val ownedPump = WebCapturePump(readable, track, request, loopContext, canvasSourced)
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
            // Never a live capture behind a closed handle that never started.
            track.stop()
            track.close()
        }
    }

    private fun failure(failure: KadreFailure): KadreResult.Failure = KadreResult.Failure(failure)
}
