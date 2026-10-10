package org.graphiks.kadre.platform.web

import kotlinx.coroutines.Dispatchers
import org.graphiks.kadre.capture.CaptureRegion
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.internal.runtime.CapturePortSource
import org.graphiks.kadre.internal.runtime.CapturePortReservation
import kotlin.coroutines.CoroutineContext

/**
 * The `Surface` streaming vertical: the session's primary canvas captured through the element's
 * own `captureStream()` — no picker, no consent, nothing browser-facing beyond the canvas the
 * session itself lent the port. The reservation is the `HostChoice` machinery over a canvas track:
 * the same [WebCapturePump] (decision 6's frame pipeline), the same exactly-once release funnels
 * (decision 7's terminations and the SPI's cancellation contract), with two surface rulings on
 * top — the canvas-sourced flag that answers `Premultiplied` alpha for Rgba8 (decision 6), and
 * decision 8's region crop, staged between the processor's reader and the pump by
 * [WebCroppedFrameReadable] whenever the request carries a [CaptureRequest.region].
 *
 * Admission happened before this class exists: the port refused every id but the session's own
 * registered primary surface, lent the canvas, and started the browser effect exactly once. The
 * capability's own frozen word said the rest — formats `{Rgba8}`, cursor `{Hidden}` (a canvas has
 * no cursor unless the host draws it into the pixels), region `Available`.
 */
internal class WebSurfaceReservation(
    source: CapturePortSource,
    track: WebDomVideoTrack,
    factory: WebTrackProcessorFactory,
    request: CaptureRequest,
    loopContext: CoroutineContext = Dispatchers.Default,
) : WebTrackReservation(
    source = source,
    track = track,
    factory = factory,
    request = request,
    canvasSourced = true,
    loopContext = loopContext,
)

/**
 * Decision 8's crop, staged where the browser's frames enter the pump: every [read] takes the
 * frame the processor delivered and returns the frame `cropTo` produced — `new VideoFrame(frame,
 * { visibleRect })` — so the pump's shape read, bound check and copy all bound and bound-check the
 * cropped frame, and the bytes that cross are exactly the region's.
 *
 * **The two handles never overlap in flight.** The crop constructor clones, so the original handle
 * is released the moment the crop frame exists — before any copy, exactly once, on the success path
 * and on the browser's refusal alike. The crop frame then becomes the pump's own: copied, closed
 * once after its copy, or released by whichever termination or cancellation got there first. A
 * refused crop (a rect outside the frame — the browser's `RangeError`) answers as
 * [WebFrameRead.Failed] carrying the browser's own error name, which the pump maps through its
 * normal pipe-failure path.
 */
internal class WebCroppedFrameReadable(
    private val upstream: WebFrameReadable,
    private val region: CaptureRegion,
) : WebFrameReadable {
    override suspend fun read(): WebFrameRead = when (val read = upstream.read()) {
        is WebFrameRead.Frame -> crop(read.frame)
        WebFrameRead.Ended -> WebFrameRead.Ended
        is WebFrameRead.Failed -> read
    }

    private fun crop(frame: WebVideoFrame): WebFrameRead = try {
        val cropped = frame.cropTo(WebCaptureMapping.visibleRect(region))
        frame.close()
        WebFrameRead.Frame(cropped)
    } catch (refused: WebCapturePipeException) {
        frame.close()
        WebFrameRead.Failed(refused.code)
    }

    override fun close() = upstream.close()
}
