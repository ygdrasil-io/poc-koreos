package org.graphiks.kadre.platform.web

import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.Window
import org.w3c.dom.mediacapture.MediaStream
import org.w3c.dom.mediacapture.MediaStreamTrack
import kotlin.js.Promise

/**
 * The browser `PermissionStatus` as this realization reads it — exactly the one member the seam
 * copies, declared here because kotlinx-browser 0.5.0 declares no Permissions API at all. The
 * `state` word is the whole answer: `granted`, `denied` or `prompt`, mapped structurally.
 */
private external interface JsPermissionStatus {
    val state: String
}

/** The `getDisplayMedia` constraints dictionary, built shape-only: no member is ever read back. */
private external interface JsDisplayMediaConstraints

/**
 * The capture seam of the browsing context, read through the window this target borrowed.
 *
 * kotlinx-browser 0.5.0 declares `MediaStream`/`MediaStreamTrack` and a `MediaDevices` without
 * `getDisplayMedia`, and nothing for the Permissions API, the `MediaStreamTrackProcessor` or
 * `canvas.captureStream` — the gap this file declares, the way the gamepad realization declared the
 * Gamepad API's gaps. Every presence probe is a `typeof` check through `js()` — a declared member
 * would be *read* unguarded and lie about a browser that lacks it — and every parameter a snippet
 * references stays a free identifier in that snippet (the phase-5 report's pitfall).
 */
internal class JsWebCaptureDom(
    private val element: () -> Any?,
    private val browsingWindow: Window = window,
) : WebCaptureDom {
    private var closed = false
    private var cachedPermission: WebCapturePermissionQueryResult? = null

    override fun isSecureContext(): Boolean = jsCaptureIsSecureContext(browsingWindow)

    override fun queryDisplayCapturePermission(): WebCapturePermissionQueryResult? = cachedPermission

    override fun readDisplayCapturePermission(listener: (WebCapturePermissionQueryResult?) -> Unit) {
        if (closed) {
            listener(null)
            return
        }
        if (!jsHasPermissionsApi(browsingWindow)) {
            listener(null)
            return
        }
        val promise = try {
            jsQueryDisplayCapture(browsingWindow)
        } catch (_: Throwable) {
            // An engine that throws synchronously on an unknown permission name answers the same
            // null a rejected promise does: the readback is unsupported here.
            listener(null)
            return
        }
        promise.then(
            onFulfilled = { status ->
                val answer = jsPermissionAnswer(status)
                cachedPermission = answer
                listener(answer)
                Unit
            },
            onRejected = { _ ->
                cachedPermission = null
                listener(null)
                Unit
            },
        )
    }

    override fun hasDisplayMedia(): Boolean = jsHasDisplayMedia(browsingWindow)

    override suspend fun pickDisplayMedia(cursorHint: String?, frameRateHint: Double?): WebDisplayMediaPick =
        suspendCancellableCoroutine { continuation ->
            val promise = jsPickDisplayMedia(browsingWindow, jsDisplayMediaConstraints(cursorHint, frameRateHint))
            promise.then(
                onFulfilled = { stream ->
                    val track = jsFirstVideoTrack(stream)
                    when {
                        track == null -> {
                            // A display capture without a video track is no source: the granted
                            // stream is released and the browser's own "nothing found" is answered.
                            jsStopEveryTrack(stream)
                            if (continuation.isActive) {
                                continuation.resume(WebDisplayMediaPick.Refused("NotFoundError")) { _, _, _ -> }
                            }
                        }

                        continuation.isActive -> {
                            // The cancellation hook releases what a cancelled resumption would
                            // otherwise strand: the caller never saw this pick, so nobody else
                            // would close it.
                            val picked = WebDisplayMediaPick.Picked(JsDomVideoTrack(stream, track))
                            continuation.resume(picked) { _, _, _ -> picked.track.close() }
                        }

                        else ->
                            // The caller cancelled while the picker was up: what it granted is
                            // released exactly as the discard flow would have released it.
                            JsDomVideoTrack(stream, track).close()
                    }
                    Unit
                },
                onRejected = { failure ->
                    if (continuation.isActive) {
                        continuation.resume(WebDisplayMediaPick.Refused(jsRejectionCode(failure))) { _, _, _ -> }
                    }
                    Unit
                },
            )
        }

    override fun processorFactory(): WebTrackProcessorFactory? =
        if (jsHasTrackProcessor(browsingWindow)) JsTrackProcessorFactory(browsingWindow) else null

    override fun canvasForSurface(): WebDomCanvas? =
        jsCanvasOrNull(element())?.let(::JsDomCanvas)

    override fun close() {
        closed = true
        cachedPermission = null
    }
}

/** One granted track as this realization holds it: the stream it came from, the track itself. */
private class JsDomVideoTrack(private val stream: MediaStream, internal val track: MediaStreamTrack) : WebDomVideoTrack {
    private var stopped = false
    private var closed = false

    override fun stop() {
        if (stopped) return
        stopped = true
        track.stop()
    }

    override fun close() {
        if (closed) return
        closed = true
        if (!stopped) {
            // Never a live capture behind a closed handle: ending the stream's tracks ends the
            // capture the pick granted (display capture grants exactly one video track).
            stopped = true
            jsStopEveryTrack(stream)
        }
    }
}

/** The attach element as this realization read it: a real `HTMLCanvasElement`, kind-checked in JS. */
private class JsDomCanvas(private val canvas: HTMLCanvasElement) : WebDomCanvas {
    override fun captureStream(): WebDomVideoTrack {
        val stream = jsCanvasCaptureStream(canvas)
        val track = jsFirstVideoTrack(stream) ?: error("canvas.captureStream() produced no video track")
        return JsDomVideoTrack(stream, track)
    }
}

/** Builds the per-track reader the pump consumes; shaped for the streaming task on this wrapper. */
private class JsTrackProcessorFactory(private val context: Window) : WebTrackProcessorFactory {
    override fun processorFor(track: WebDomVideoTrack): WebFrameReadable {
        val dom = track as? JsDomVideoTrack ?: error("the processor builds for this target's own tracks only")
        return JsWebFrameReadable(jsProcessorReadable(jsMakeTrackProcessor(context, dom.track)))
    }
}

/** The reader the streaming task pumps; it wraps the processor's readable until then. */
private class JsWebFrameReadable(val readable: dynamic) : WebFrameReadable

/** Whether the browsing context is a secure context — the browser's own word. */
private fun jsCaptureIsSecureContext(context: Window): Boolean =
    js("context.isSecureContext === true").unsafeCast<Boolean>()

/** `getDisplayMedia` presence, probed where it lives: `navigator.mediaDevices`, guarded member by member. */
private fun jsHasDisplayMedia(context: Window): Boolean =
    js(
        "typeof context.navigator.mediaDevices !== 'undefined' && context.navigator.mediaDevices !== null && " +
            "typeof context.navigator.mediaDevices.getDisplayMedia === 'function'",
    ).unsafeCast<Boolean>()

/** Permissions API presence — the API is absent outright on insecure contexts. */
private fun jsHasPermissionsApi(context: Window): Boolean =
    js(
        "typeof context.navigator.permissions !== 'undefined' && context.navigator.permissions !== null && " +
            "typeof context.navigator.permissions.query === 'function'",
    ).unsafeCast<Boolean>()

/** The display-capture readback query. Never prompts; a rejection is answered by the caller's handlers. */
private fun jsQueryDisplayCapture(context: Window): Promise<JsPermissionStatus> =
    js("context.navigator.permissions.query({ name: 'display-capture' })")

/** The readback word mapped structurally; a state the spec does not define is an unsupported answer. */
private fun jsPermissionAnswer(status: JsPermissionStatus): WebCapturePermissionQueryResult? = when (status.state) {
    "granted" -> WebCapturePermissionQueryResult.Granted
    "denied" -> WebCapturePermissionQueryResult.Denied
    "prompt" -> WebCapturePermissionQueryResult.NotDetermined
    else -> null
}

/**
 * The picker constraints: the video track only, each hint present exactly where the caller stated
 * one — an omitted hint is literally absent from the dictionary, never a zero masquerading as a
 * stated preference.
 */
private fun jsDisplayMediaConstraints(cursorHint: String?, frameRateHint: Double?): JsDisplayMediaConstraints = js(
    """({
         video: {
           ...(cursorHint == null ? { } : { cursor: cursorHint }),
           ...(frameRateHint == null ? { } : { frameRate: { max: frameRateHint } })
         }
       })""",
)

/** The consent/picker call itself — the one browser effect this seam can start. */
private fun jsPickDisplayMedia(context: Window, constraints: JsDisplayMediaConstraints): Promise<MediaStream> =
    js("context.navigator.mediaDevices.getDisplayMedia(constraints)")

/** The first video track of a granted stream; display capture answers exactly one. */
private fun jsFirstVideoTrack(stream: MediaStream): MediaStreamTrack? {
    val tracks = stream.getVideoTracks()
    return if (tracks.isNotEmpty()) tracks[0] else null
}

/** Ends every track of a stream — the whole release a discarded pick owes the browser. */
private fun jsStopEveryTrack(stream: MediaStream) {
    stream.getTracks().forEach { it.stop() }
}

/** The browser's own name for a rejection, as far as one is offered; `refused` otherwise. */
private fun jsRejectionCode(failure: Throwable): String {
    val candidate = failure.asDynamic().name as? String ?: return "refused"
    return if (candidate.isNotEmpty() && candidate.all { it.code in 0x21..0x7e }) candidate else "refused"
}

/** The `MediaStreamTrackProcessor` constructor's presence, window-scoped as the spec exposes it. */
private fun jsHasTrackProcessor(context: Window): Boolean =
    js("typeof context.MediaStreamTrackProcessor === 'function'").unsafeCast<Boolean>()

/**
 * Builds the processor for one track: the spec's dictionary form first, the early-Chromium
 * positional form behind it — a browser answering either is a browser the pump can read.
 */
private fun jsMakeTrackProcessor(context: Window, track: MediaStreamTrack): dynamic = js(
    """(function () {
         try { return new context.MediaStreamTrackProcessor({ track: track }); }
         catch (firstFailure) { return new context.MediaStreamTrackProcessor(track); }
       }())""",
)

private fun jsProcessorReadable(processor: dynamic): dynamic = js("processor.readable")

/**
 * The attach element as a canvas, kind-checked by the JavaScript's own `instanceof` — never a
 * Kotlin cast, which this target reduces to a null check (the phase-5 lesson) — so a Kotlin test
 * double or any non-canvas element simply answers `null`.
 */
private fun jsCanvasOrNull(element: Any?): HTMLCanvasElement? {
    if (element == null) return null
    return js("element instanceof HTMLCanvasElement ? element : null").unsafeCast<HTMLCanvasElement?>()
}

private fun jsCanvasCaptureStream(canvas: HTMLCanvasElement): MediaStream = js("canvas.captureStream()")

internal actual fun webCaptureDom(element: () -> Any?): WebCaptureDom = JsWebCaptureDom(element)
