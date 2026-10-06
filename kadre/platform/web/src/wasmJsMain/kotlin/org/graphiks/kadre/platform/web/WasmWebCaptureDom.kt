@file:OptIn(ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.platform.web

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.HTMLElement
import org.w3c.dom.Window

/**
 * kotlinx-browser 0.5.0 declares a `MediaDevices` without `getDisplayMedia` and nothing for the
 * Permissions API, the `MediaStreamTrackProcessor` or `canvas.captureStream` — the gap these
 * `@JsFun` helpers read, the way `wasmRequestPointerLock` reads the pointer-lock members the same
 * bindings do not declare. Every presence probe is a `typeof` check inside its snippet: a browser
 * without the member answers `false`/`null`, and an absent primitive is an honest Unsupported
 * capability, never a guessed one.
 */

/** `getDisplayMedia` presence, probed where it lives: `navigator.mediaDevices`, member by member. */
@JsFun(
    "(navigator) => (typeof navigator.mediaDevices !== 'undefined' && navigator.mediaDevices !== null && " +
        "typeof navigator.mediaDevices.getDisplayMedia === 'function')",
)
internal external fun wasmHasDisplayMedia(navigator: JsAny): Boolean

/** Permissions API presence — the API is absent outright on insecure contexts. */
@JsFun(
    "(navigator) => (typeof navigator.permissions !== 'undefined' && navigator.permissions !== null && " +
        "typeof navigator.permissions.query === 'function')",
)
internal external fun wasmHasPermissionsApi(navigator: JsAny): Boolean

/** The display-capture readback query. Never prompts; a synchronous throw is the caller's `null`. */
@JsFun("(navigator) => navigator.permissions.query({ name: 'display-capture' })")
internal external fun wasmQueryDisplayCapture(navigator: JsAny): JsAny?

/**
 * Awaits the readback promise, handing the settled `state` word — or `null` for a rejection — to
 * [onAnswer] exactly once. The raw word crosses and Kotlin maps it, so both targets answer the
 * same structurally-mapped seam value.
 */
@JsFun("(promise, onAnswer) => { promise.then((status) => onAnswer(status.state), () => onAnswer(null)); }")
internal external fun wasmAwaitDisplayCapturePermission(promise: JsAny, onAnswer: (JsString?) -> Unit)

/**
 * The picker constraints: the video track only, each hint present exactly where its presence flag
 * says the caller stated one — an omitted hint is literally absent from the dictionary, never a
 * zero masquerading as a stated preference.
 */
@JsFun(
    "(hasCursor, cursor, hasFrameRate, frameRate) => " +
        "({ video: { ...(hasCursor ? { cursor: cursor } : {}), ...(hasFrameRate ? { frameRate: { max: frameRate } } : {}) } })",
)
internal external fun wasmDisplayMediaConstraints(
    hasCursorHint: Boolean,
    cursorHint: String,
    hasFrameRateHint: Boolean,
    frameRateHint: Double,
): JsAny

/**
 * The consent/picker call itself — the one browser effect this seam can start. The granted stream
 * crosses to [onPicked]; a rejection crosses to [onRefused] as the browser's own error name (or
 * `refused` when it offers none).
 */
@JsFun(
    "(navigator, constraints, onPicked, onRefused) => { " +
        "navigator.mediaDevices.getDisplayMedia(constraints).then(" +
        "(stream) => onPicked(stream), " +
        "(error) => onRefused((error !== null && error !== undefined && typeof error.name === 'string' && error.name.length > 0) ? error.name : 'refused')); }",
)
internal external fun wasmPickDisplayMedia(
    navigator: JsAny,
    constraints: JsAny,
    onPicked: (JsAny) -> Unit,
    onRefused: (JsString) -> Unit,
)

/** The first video track of a granted stream; display capture answers exactly one. */
@JsFun("(stream) => { const tracks = stream.getVideoTracks(); return tracks.length > 0 ? tracks[0] : null; }")
internal external fun wasmFirstVideoTrack(stream: JsAny): JsAny?

/** Ends one track — the browser's own release effect. */
@JsFun("(track) => track.stop()")
internal external fun wasmStopTrack(track: JsAny)

/** Ends every track of a stream — the whole release a discarded pick owes the browser. */
@JsFun("(stream) => stream.getTracks().forEach((track) => track.stop())")
internal external fun wasmStopEveryTrack(stream: JsAny)

/** The `MediaStreamTrackProcessor` constructor's presence, window-scoped as the spec exposes it. */
@JsFun("(context) => typeof context.MediaStreamTrackProcessor === 'function'")
internal external fun wasmHasTrackProcessor(context: Window): Boolean

/**
 * Builds the processor for one track: the spec's dictionary form first, the early-Chromium
 * positional form behind it — a browser answering either is a browser the pump can read, and one
 * answering neither answers `null`.
 */
@JsFun(
    "(context, track) => { " +
        "try { return new context.MediaStreamTrackProcessor({ track: track }); } " +
        "catch (firstFailure) { try { return new context.MediaStreamTrackProcessor(track); } catch (secondFailure) { return null; } } }",
)
internal external fun wasmMakeTrackProcessor(context: Window, track: JsAny): JsAny?

@JsFun("(processor) => processor.readable")
internal external fun wasmProcessorReadable(processor: JsAny): JsAny

/**
 * The attach element as a canvas, kind-checked by the JavaScript's own `instanceof` inside the
 * snippet — the one place where Kotlin/Wasm and JavaScript meet — so a Kotlin test double or any
 * non-canvas element simply answers `null` instead of lying about its kind.
 */
@JsFun("(element) => (element !== null && element !== undefined && element instanceof HTMLCanvasElement) ? element : null")
internal external fun wasmCanvasElementOrNull(element: HTMLElement): JsAny?

@JsFun("(canvas) => canvas.captureStream()")
internal external fun wasmCanvasCaptureStream(canvas: JsAny): JsAny?

/** One granted track as this realization holds it: the stream it came from, the track itself. */
private class WasmDomVideoTrack(private val stream: JsAny, internal val track: JsAny) : WebDomVideoTrack {
    private var stopped = false
    private var closed = false

    override fun stop() {
        if (stopped) return
        stopped = true
        wasmStopTrack(track)
    }

    override fun close() {
        if (closed) return
        closed = true
        if (!stopped) {
            // Never a live capture behind a closed handle: ending the stream's tracks ends the
            // capture the pick granted (display capture grants exactly one video track).
            stopped = true
            wasmStopEveryTrack(stream)
        }
    }
}

/** The attach element as this realization read it: a real canvas, kind-checked in the snippet. */
private class WasmDomCanvas(private val canvas: JsAny) : WebDomCanvas {
    override fun captureStream(): WebDomVideoTrack {
        val stream = wasmCanvasCaptureStream(canvas)
            ?: error("the browser refused canvas.captureStream()")
        val track = wasmFirstVideoTrack(stream)
            ?: error("canvas.captureStream() produced no video track")
        return WasmDomVideoTrack(stream, track)
    }
}

/** Builds the per-track reader the pump consumes; shaped for the streaming task on this wrapper. */
private class WasmTrackProcessorFactory(private val context: Window) : WebTrackProcessorFactory {
    override fun processorFor(track: WebDomVideoTrack): WebFrameReadable {
        val dom = track as? WasmDomVideoTrack ?: error("the processor builds for this target's own tracks only")
        val processor = wasmMakeTrackProcessor(context, dom.track)
            ?: error("the browser refused to build a MediaStreamTrackProcessor for this track")
        return WasmWebFrameReadable(wasmProcessorReadable(processor))
    }
}

/** The reader the streaming task pumps; it wraps the processor's readable until then. */
private class WasmWebFrameReadable(internal val readable: JsAny) : WebFrameReadable

/**
 * The capture seam of the browsing context, read through the window this target borrowed. The
 * navigator crosses as [JsAny] because the borrowed object needs no type of its own here.
 */
internal class WasmWebCaptureDom(
    private val element: () -> Any?,
    private val browsingWindow: Window = window,
) : WebCaptureDom {
    private val navigator: JsAny = browsingWindow.navigator
    private var closed = false
    private var cachedPermission: WebCapturePermissionQueryResult? = null

    override fun isSecureContext(): Boolean = wasmIsSecureContext(browsingWindow)

    override fun queryDisplayCapturePermission(): WebCapturePermissionQueryResult? = cachedPermission

    override fun readDisplayCapturePermission(listener: (WebCapturePermissionQueryResult?) -> Unit) {
        if (closed) {
            listener(null)
            return
        }
        if (!wasmHasPermissionsApi(navigator)) {
            listener(null)
            return
        }
        val promise = try {
            wasmQueryDisplayCapture(navigator)
        } catch (_: Throwable) {
            // An engine that throws synchronously on an unknown permission name answers the same
            // null a rejected promise does: the readback is unsupported here.
            listener(null)
            return
        }
        if (promise == null) {
            listener(null)
            return
        }
        wasmAwaitDisplayCapturePermission(promise) { state ->
            val answer = when (state?.toString()) {
                "granted" -> WebCapturePermissionQueryResult.Granted
                "denied" -> WebCapturePermissionQueryResult.Denied
                "prompt" -> WebCapturePermissionQueryResult.NotDetermined
                else -> null
            }
            cachedPermission = answer
            listener(answer)
        }
    }

    override fun hasDisplayMedia(): Boolean = wasmHasDisplayMedia(navigator)

    override suspend fun pickDisplayMedia(cursorHint: String?, frameRateHint: Double?): WebDisplayMediaPick =
        suspendCancellableCoroutine { continuation ->
            val constraints = wasmDisplayMediaConstraints(
                hasCursorHint = cursorHint != null,
                cursorHint = cursorHint ?: "",
                hasFrameRateHint = frameRateHint != null,
                frameRateHint = frameRateHint ?: 0.0,
            )
            wasmPickDisplayMedia(
                navigator,
                constraints,
                onPicked = { stream ->
                    val track = wasmFirstVideoTrack(stream)
                    when {
                        track == null -> {
                            // A display capture without a video track is no source: the granted
                            // stream is released and the browser's own "nothing found" is answered.
                            wasmStopEveryTrack(stream)
                            if (continuation.isActive) {
                                continuation.resume(WebDisplayMediaPick.Refused("NotFoundError")) { _, _, _ -> }
                            }
                        }

                        continuation.isActive -> {
                            // The cancellation hook releases what a cancelled resumption would
                            // otherwise strand: the caller never saw this pick, so nobody else
                            // would close it.
                            val picked = WebDisplayMediaPick.Picked(WasmDomVideoTrack(stream, track))
                            continuation.resume(picked) { _, _, _ -> picked.track.close() }
                        }

                        else ->
                            // The caller cancelled while the picker was up: what it granted is
                            // released exactly as the discard flow would have released it.
                            WasmDomVideoTrack(stream, track).close()
                    }
                },
                onRefused = { code ->
                    if (continuation.isActive) {
                        continuation.resume(WebDisplayMediaPick.Refused(code.toString())) { _, _, _ -> }
                    }
                },
            )
        }

    override fun processorFactory(): WebTrackProcessorFactory? =
        if (wasmHasTrackProcessor(browsingWindow)) WasmTrackProcessorFactory(browsingWindow) else null

    override fun canvasForSurface(): WebDomCanvas? {
        // The same borrow the element lease performs: the session's untyped reference, cast at this
        // target's boundary to the DOM type only this target's externals can read.
        val candidate = element() as? HTMLElement ?: return null
        return wasmCanvasElementOrNull(candidate)?.let(::WasmDomCanvas)
    }

    override fun close() {
        closed = true
        cachedPermission = null
    }
}

internal actual fun webCaptureDom(element: () -> Any?): WebCaptureDom = WasmWebCaptureDom(element)
