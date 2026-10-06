package org.graphiks.kadre.platform.web

/**
 * The display-capture permission answer the browser's own readback produced, copied structurally.
 *
 * `Granted`/`Denied`/`NotDetermined` mirror the three `PermissionStatus.state` words the spec
 * defines (`granted`, `denied`, `prompt`); the seam never invents a fourth. The `window` scope has
 * no browser fact of its own — one consent governs whatever the picker offers — so every consumer
 * of this answer mirrors it across both scopes (plan decision 3's documented single-consent limit).
 */
internal sealed interface WebCapturePermissionQueryResult {
    data object Granted : WebCapturePermissionQueryResult

    data object Denied : WebCapturePermissionQueryResult

    data object NotDetermined : WebCapturePermissionQueryResult
}

/**
 * The one answer the browser's consent/picker flow produces. `Picked` carries the video track the
 * browser granted — the caller owns it from the moment the pick returns, and a caller that wanted
 * only the consent verdict stops it immediately (the discard flow of plan decision 4). `Refused`
 * carries the browser's own failure name (`NotAllowedError`, `NotFoundError`, …) or `refused` when
 * the browser offered none; the code is the refusal's whole shape, because the DOM exposes no more.
 */
internal sealed interface WebDisplayMediaPick {
    data class Picked(val track: WebDomVideoTrack) : WebDisplayMediaPick

    data class Refused(val code: String) : WebDisplayMediaPick
}

/**
 * One video track the browser granted, as the seam holds it.
 *
 * [stop] is the browser's own effect: it ends the track and releases the capture it carried. The
 * inherited [close] releases the seam handle, and a realization's close stops the track too if the
 * caller never did — a discarded pick never leaks a live capture. [addEndedListener] hears the
 * track's own end — the browser revoking the source ("Stop sharing"), which the DOM signals only
 * as the `ended` event, never as a failed read.
 */
internal interface WebDomVideoTrack : AutoCloseable {
    /** Performs the browser's own track stop, exactly once per track. */
    fun stop()

    /**
     * Hears the track's own end — the browser revoking the capture, not the caller's [stop] (the
     * DOM never fires `ended` for a stopped track, which is exactly how the two terminations stay
     * distinguishable). Registered before the first read; may fire at most once.
     */
    fun addEndedListener(listener: () -> Unit)
}

/**
 * One VideoFrame the reader delivered, as the seam holds it. [close] performs the browser's own
 * frame release — the handle is the caller's from the moment [WebFrameRead.Frame] lands, and the
 * bytes [copyTo] produces are Kotlin-owned copies, never a view of the frame's own buffers.
 */
internal interface WebVideoFrame : AutoCloseable {
    /** The frame's structural shape, read once as the seam took the handle. */
    val shape: WebVideoFrameShape

    /**
     * The browser's own byte count for a whole-frame copy in [format] — `null` for the frame's own
     * format — computed before any buffer exists, which is what makes the frame bound checkable
     * before a single byte is allocated.
     */
    fun allocationSize(format: String?): Long

    /**
     * Copies every plane of the frame's visible rect into a fresh Kotlin-owned byte array and
     * reports each plane's row stride as the browser wrote it — the tightly-packed stride when the
     * browser reports none, which is what a default copy is by definition. One copy per frame; a
     * browser refusal throws [WebCapturePipeException] with the browser's own error name.
     */
    suspend fun copyTo(format: String?): List<WebPlaneBytes>

    override fun close()
}

/** The structural facts of one [WebVideoFrame], copied out of the browser's own properties. */
internal class WebVideoFrameShape(
    /** The browser's format word (`"RGBA"`, `"I420"`, …) — `null` when the frame says none. */
    val format: String?,
    /** The visible rect's dimensions — the pixels a default copy produces. */
    val width: Int,
    val height: Int,
    /** The presentation timestamp in microseconds; `null` when the frame says none. */
    val timestampUs: Long?,
    /** The frame duration in microseconds; `null` when the browser does not say. */
    val durationUs: Long?,
    /** The frame's color words — `null` when the browser exposes no `colorSpace` at all. */
    val colorSpace: WebColorSpaceShape?,
)

/** The `VideoFrame.colorSpace` words, copied structurally; every member may be the browser's null. */
internal class WebColorSpaceShape(
    val primaries: String?,
    val transfer: String?,
    val matrix: String?,
    val fullRange: Boolean?,
)

/** One copied plane: the browser's own row stride and the fresh Kotlin-owned bytes it wrote. */
internal class WebPlaneBytes(val rowStride: Int, val bytes: ByteArray)

/** The one answer a read produces: a frame handle, the stream's end, or the browser's error name. */
internal sealed interface WebFrameRead {
    data class Frame(val frame: WebVideoFrame) : WebFrameRead

    /** The readable ended — the browser stopped producing frames (the track's own end included). */
    data object Ended : WebFrameRead

    /** The readable failed; the code is the browser's own error name. */
    data class Failed(val code: String) : WebFrameRead
}

/** A frame-pipe refusal carrying the browser's own error name, for the caller to map. */
internal class WebCapturePipeException(val code: String) : Exception()

/**
 * The per-track frame reader the streaming task pumps: one read at a time over the processor's
 * readable, each read answering exactly one [WebFrameRead]. [close] cancels the pending read and
 * releases the reader — exactly once, from any path that reaches it. A read whose waiter is gone
 * (the caller stopped or was cancelled while the browser was already delivering) discards the
 * chunk it would have answered: a frame nobody will pump is the browser's handle to close, not a
 * Kotlin leak — the realization owns that discard.
 */
internal interface WebFrameReadable : AutoCloseable {
    /** Awaits the next frame, the stream's end, or its failure. One read in flight at a time. */
    suspend fun read(): WebFrameRead

    override fun close()
}

/**
 * Builds the frame reader for one granted track. Present iff the browsing context ships the
 * `MediaStreamTrackProcessor` constructor; a browser without it is a browser the capability probe
 * reports as missing that primitive — never one the port guesses about.
 */
internal interface WebTrackProcessorFactory {
    fun processorFor(track: WebDomVideoTrack): WebFrameReadable
}

/**
 * The attach element as a canvas, as the seam reads it. [captureStream] starts the element's own
 * stream — the browser effect the surface-capture path is built on; its refusals (a tainted or
 * zero-sized canvas) are the caller's to contain, since the seam carries no error channel.
 */
internal interface WebDomCanvas {
    fun captureStream(): WebDomVideoTrack
}

/**
 * The capture seam of the browsing context: the readback, the consent flow, the primitive presence
 * facts and the attach element as a canvas. Test doubles implement this; each target has one real
 * realization over its own externals. No DOM type crosses — every member answers in structural
 * shapes, and every browser object stays behind the realization that borrowed it.
 *
 * The probing members ([isSecureContext], [hasDisplayMedia], [processorFactory], [canvasForSurface],
 * [queryDisplayCapturePermission]) are presence probes and readbacks only: none of them can prompt.
 * The one member that reaches the browser's consent machinery is [pickDisplayMedia], and the control
 * plane calls it from the explicit request path alone.
 */
internal interface WebCaptureDom : AutoCloseable {
    /** Whether the browsing context is a secure context — the browser's own word. */
    fun isSecureContext(): Boolean

    /**
     * The settled display-capture readback answer, or `null` when the permissions API or the name is
     * unsupported (or the readback has not settled yet — a promise-based query the realization fires
     * through [readDisplayCapturePermission]; the port republishes when the answer lands).
     */
    fun queryDisplayCapturePermission(): WebCapturePermissionQueryResult?

    /**
     * Fires the browser's display-capture readback once — a `permissions.query` that never prompts —
     * and delivers the answer (the [queryDisplayCapturePermission] value it also caches) to [listener]
     * exactly once: `null` when the API is absent, the name is unknown or the query rejects.
     */
    fun readDisplayCapturePermission(listener: (WebCapturePermissionQueryResult?) -> Unit)

    /** Whether the browsing context ships `getDisplayMedia` (presence only — never a call). */
    fun hasDisplayMedia(): Boolean

    /**
     * Launches the browser consent/picker flow. Suspend; resolves with the picked track or the
     * failure code. The hints are the browser's own picker words (`cursor`, a `frameRate` cap) and
     * are hints alone: the browser may ignore both, and the caller states `null` whenever the
     * request carried no preference.
     */
    suspend fun pickDisplayMedia(cursorHint: String?, frameRateHint: Double?): WebDisplayMediaPick

    /** Present iff the browser ships `MediaStreamTrackProcessor`; `null` otherwise. */
    fun processorFactory(): WebTrackProcessorFactory?

    /** The attach element as a canvas, or `null` when it is none or the port released it. */
    fun canvasForSurface(): WebDomCanvas?
}

/**
 * The per-target realization of [WebCaptureDom] over the session's own browsing context.
 *
 * [element] is the attach element as the session holds it — the same untyped reference the surface
 * lease lends (`WebHostPort.leasedElement`) — read live on every probe, so a port whose session is
 * gone lends nothing. Web code cannot name a DOM type; the one function each target actualizes is
 * how the shared seam reaches the browser.
 */
internal expect fun webCaptureDom(element: () -> Any?): WebCaptureDom
