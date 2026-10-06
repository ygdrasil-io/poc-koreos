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
 * caller never did — a discarded pick never leaks a live capture. The track's own end notification
 * (the browser revoking the source) rides a listener the streaming task adds to this seam; the
 * control plane of this task needs only the release.
 */
internal interface WebDomVideoTrack : AutoCloseable {
    /** Performs the browser's own track stop, exactly once per track. */
    fun stop()
}

/**
 * The per-track frame reader the streaming task pumps. Presence-carrier only at this phase: the
 * processor constructor's existence is what the capability probe reads, and the pump surface the
 * streaming task consumes is shaped there, on top of the readable this object wraps.
 */
internal interface WebFrameReadable

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
