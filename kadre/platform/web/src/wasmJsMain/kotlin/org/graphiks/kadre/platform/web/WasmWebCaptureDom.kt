@file:OptIn(ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.platform.web

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsException
import kotlin.js.JsNumber
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toJsString
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.suspendCancellableCoroutine
import org.graphiks.kadre.surface.PhysicalSize
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
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

// -- the frame pump's pipe: reads, shapes, copies — the same gap the pointer-lock externals fill -----

/** Hears the track's own end — the browser's revocation, never the caller's stop. */
@JsFun("(track, listener) => track.addEventListener('ended', () => listener())")
internal external fun wasmTrackAddEndedListener(track: JsAny, listener: () -> Unit)

/** The reader behind a processor's readable stream. */
@JsFun("(readable) => readable.getReader()")
internal external fun wasmReaderOf(readable: JsAny): JsAny

/** Cancels a reader — the release for a pump that stopped reading; its pending read unwinds. */
@JsFun("(reader) => { reader.cancel(); }")
internal external fun wasmCancelReader(reader: JsAny)

/** One read over the readable: the chunk (done-flag included), or the rejection. */
@JsFun(
    "(reader, onChunk, onFailed) => { " +
        "reader.read().then((result) => onChunk(result), (failure) => onFailed(failure)); }",
)
internal external fun wasmReadableRead(reader: JsAny, onChunk: (JsAny) -> Unit, onFailed: (JsAny) -> Unit)

/** Whether one read chunk was the stream's end rather than a frame. */
@JsFun("(result) => result.done === true")
internal external fun wasmReadIsDone(result: JsAny): Boolean

/** The frame of one read chunk. */
@JsFun("(result) => result.value")
internal external fun wasmReadValue(result: JsAny): JsAny

/** Closes the frame of a chunk whose waiter is gone — the seam's documented discard. */
@JsFun(
    "(result) => { if (result.done !== true && result.value !== null && result.value !== undefined) { result.value.close(); } }",
)
internal external fun wasmDiscardChunk(result: JsAny)

/** The browser's error name for a rejected read, or `refused` when it offers none. */
@JsFun(
    "(failure) => (failure !== null && failure !== undefined && typeof failure.name === 'string' && " +
        "failure.name.length > 0) ? failure.name : 'refused'",
)
internal external fun wasmFailureName(failure: JsAny): JsString

private fun wasmRejectionCode(failure: JsAny): String = wasmFailureName(failure).toString()

/**
 * The browser's own error name behind a JS throw. JavaScript exceptions are signalled to Wasm as
 * [JsException] carrying the thrown value; anything else is a throw the browser did not name.
 */
private fun wasmPipeCode(refused: Throwable): String = when (val value = (refused as? JsException)?.thrownValue) {
    null -> "refused"
    else -> wasmFailureName(value).toString()
}

/** The frame's format word, or `null` when it says none. */
@JsFun("(frame) => (frame.format === undefined || frame.format === null) ? null : frame.format")
internal external fun wasmFrameFormat(frame: JsAny): JsString?

@JsFun("(frame) => frame.visibleWidth")
internal external fun wasmFrameWidth(frame: JsAny): Double

@JsFun("(frame) => frame.visibleHeight")
internal external fun wasmFrameHeight(frame: JsAny): Double

/** The presentation timestamp in microseconds, or `null` when the frame says none. */
@JsFun("(frame) => (frame.timestamp === undefined || frame.timestamp === null) ? null : frame.timestamp")
internal external fun wasmFrameTimestamp(frame: JsAny): JsAny?

/** The frame duration in microseconds, or `null` when the browser does not say. */
@JsFun("(frame) => (frame.duration === undefined || frame.duration === null) ? null : frame.duration")
internal external fun wasmFrameDuration(frame: JsAny): JsAny?

/** The frame's color words object, or `null` when the browser exposes none. */
@JsFun("(frame) => (frame.colorSpace === undefined || frame.colorSpace === null) ? null : frame.colorSpace")
internal external fun wasmFrameColorSpace(frame: JsAny): JsAny?

/** One color word of the frame's `colorSpace`, by name, or `null`. */
@JsFun("(colorSpace, name) => (colorSpace[name] === undefined || colorSpace[name] === null) ? null : colorSpace[name]")
internal external fun wasmColorSpaceWord(colorSpace: JsAny, name: String): JsAny?

/** The frame's full-range flag, or `null` when the browser does not say. */
@JsFun(
    "(colorSpace) => (colorSpace === null || colorSpace === undefined || " +
        "typeof colorSpace.fullRange !== 'boolean') ? null : colorSpace.fullRange",
)
internal external fun wasmColorSpaceFullRange(colorSpace: JsAny): Boolean?

/** The browser's own byte count for a whole-frame copy in [format] — the frame's own when `null`. */
@JsFun("(frame, format) => frame.allocationSize(format === null ? {} : { format: format })")
internal external fun wasmFrameAllocationSize(frame: JsAny, format: JsString?): Double

/**
 * Decision 8's crop: `new VideoFrame(frame, { visibleRect })` — the new independent frame the
 * constructor builds from the source frame and the rect.
 */
@JsFun("(frame, x, y, width, height) => new VideoFrame(frame, { visibleRect: { x: x, y: y, width: width, height: height } })")
internal external fun wasmCropFrame(frame: JsAny, x: Int, y: Int, width: Int, height: Int): JsAny

/**
 * The whole-frame copy: a buffer sized by the browser's own `allocationSize` for the same options,
 * the copy awaited, and the buffer-plus-layout pair handed back for the Kotlin-side slicing.
 */
@JsFun(
    "(frame, format) => { " +
        "const options = format === null ? {} : { format: format }; " +
        "const buffer = new ArrayBuffer(frame.allocationSize(options)); " +
        "return frame.copyTo(buffer, options).then((layout) => ({ buffer: buffer, layout: layout })); }",
)
internal external fun wasmFrameCopy(frame: JsAny, format: JsString?): Promise<JsAny?>

@JsFun("(result) => result.buffer")
internal external fun wasmCopyBuffer(result: JsAny): JsAny

@JsFun("(result) => result.layout")
internal external fun wasmCopyLayout(result: JsAny): JsAny

/** The byte view of one copied buffer, the way its contents are read byte by byte. */
@JsFun("(buffer) => new Int8Array(buffer)")
internal external fun wasmCopyView(buffer: JsAny): Int8Array

@JsFun("(layout) => layout.length")
internal external fun wasmLayoutCount(layout: JsAny): Int

@JsFun("(layout, index) => layout[index].destinationOffset")
internal external fun wasmLayoutOffset(layout: JsAny, index: Int): Double

@JsFun("(layout, index) => layout[index].copyBytes")
internal external fun wasmLayoutBytes(layout: JsAny, index: Int): Double

/** The browser's reported stride of one copied plane, or -1 when the layout entry carries none. */
@JsFun(
    "(layout, index) => (layout[index] !== null && layout[index] !== undefined && " +
        "typeof layout[index].stride === 'number') ? layout[index].stride : -1",
)
internal external fun wasmLayoutStride(layout: JsAny, index: Int): Double

@JsFun("(frame) => frame.close()")
internal external fun wasmFrameClose(frame: JsAny)

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

    override fun addEndedListener(listener: () -> Unit) {
        wasmTrackAddEndedListener(track, listener)
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

/** The reader of the processor's readable: one read at a time, released exactly once. */
private class WasmWebFrameReadable(private val readable: JsAny) : WebFrameReadable {
    private var reader: JsAny? = null
    private var released = false

    override suspend fun read(): WebFrameRead = suspendCancellableCoroutine { continuation ->
        val currentReader = ensureReader()
        wasmReadableRead(
            currentReader,
            onChunk = { result ->
                if (continuation.isActive) {
                    val answer = if (wasmReadIsDone(result)) {
                        WebFrameRead.Ended
                    } else {
                        WebFrameRead.Frame(WasmWebVideoFrame(wasmReadValue(result)))
                    }
                    continuation.resume(answer) { _, _, _ -> }
                } else {
                    // The waiter is gone (stop or cancellation mid-delivery): the frame nobody
                    // will pump is the browser's handle to close, not a Kotlin leak.
                    wasmDiscardChunk(result)
                }
            },
            onFailed = { failure ->
                if (continuation.isActive) {
                    continuation.resume(WebFrameRead.Failed(wasmFailureName(failure).toString())) { _, _, _ -> }
                }
            },
        )
        // The read cancelled from the Kotlin side ends the stream's reader — the browser's own
        // release for a pump that stopped reading mid-frame.
        continuation.invokeOnCancellation { release() }
    }

    override fun close() = release()

    private fun ensureReader(): JsAny {
        if (released) error("the frame reader is released")
        return reader ?: wasmReaderOf(readable).also { reader = it }
    }

    private fun release() {
        if (released) return
        released = true
        reader?.let(::wasmCancelReader)
    }
}

/**
 * One VideoFrame handle: the shape read once as the handle was taken, the browser's own
 * `allocationSize` for the bound check, and one whole-frame `copyTo` into fresh Kotlin-owned
 * planes. A default copy is tightly packed, so a plane the browser reports no stride for is the
 * tightly-packed stride of the copy's own format word; a reported stride is taken as the truth.
 */
private class WasmWebVideoFrame(private val frame: JsAny) : WebVideoFrame {
    override val shape: WebVideoFrameShape = wasmFrameShape(frame)

    override fun cropTo(rect: WebVisibleRect): WebVideoFrame = try {
        WasmWebVideoFrame(wasmCropFrame(frame, rect.x, rect.y, rect.width, rect.height))
    } catch (refused: Throwable) {
        // The seam's contract: a browser refusal crosses as the pipe exception carrying the
        // browser's own error name — never as a raw Throwable.
        throw WebCapturePipeException(wasmPipeCode(refused))
    }

    override fun allocationSize(format: String?): Long = try {
        wasmFrameAllocationSize(frame, format?.toJsString()).toLong()
    } catch (refused: Throwable) {
        // The seam's contract: a browser refusal crosses as the pipe exception carrying the
        // browser's own error name — never as a raw Throwable.
        throw WebCapturePipeException(wasmPipeCode(refused))
    }

    override suspend fun copyTo(format: String?): List<WebPlaneBytes> {
        val result: JsAny = try {
            wasmFrameCopy(frame, format?.toJsString()).await()
        } catch (refused: Throwable) {
            // A rejected `copyTo` promise is the browser's refusal: same normalization as above.
            throw WebCapturePipeException(wasmPipeCode(refused))
        }
        val view = wasmCopyView(wasmCopyBuffer(result))
        val bytes = ByteArray(view.length) { index -> view[index] }
        val layout = wasmCopyLayout(result)
        val copyWord = format ?: shape.format ?: throw WebCapturePipeException("unknown-plane-word")
        return List(wasmLayoutCount(layout)) { index ->
            val offset = wasmLayoutOffset(layout, index).toInt()
            val copyBytes = wasmLayoutBytes(layout, index).toInt()
            val reported = wasmLayoutStride(layout, index)
            WebPlaneBytes(
                rowStride = if (reported >= 0) reported.toInt() else tightRowStride(copyWord, index),
                bytes = bytes.copyOfRange(offset, offset + copyBytes),
            )
        }
    }

    override fun close() {
        wasmFrameClose(frame)
    }

    private fun tightRowStride(copyWord: String, planeIndex: Int): Int {
        val model = WebCaptureMapping.portableFormat(copyWord)
            ?: throw WebCapturePipeException("unknown-plane-word")
        return WebCaptureMapping.planeLayouts(model, PhysicalSize(shape.width, shape.height))[planeIndex].rowStride
    }
}

/** The structural shape of one VideoFrame, copied out of the browser's own properties. */
private fun wasmFrameShape(frame: JsAny): WebVideoFrameShape {
    val colorSpace = wasmFrameColorSpace(frame)
    return WebVideoFrameShape(
        format = wasmFrameFormat(frame)?.toString(),
        width = wasmFrameWidth(frame).toInt(),
        height = wasmFrameHeight(frame).toInt(),
        timestampUs = wasmFrameTimestamp(frame)?.toNumber()?.toLong(),
        durationUs = wasmFrameDuration(frame)?.toNumber()?.toLong(),
        colorSpace = colorSpace?.let {
            WebColorSpaceShape(
                primaries = wasmColorSpaceWord(it, "primaries")?.toString(),
                transfer = wasmColorSpaceWord(it, "transfer")?.toString(),
                matrix = wasmColorSpaceWord(it, "matrix")?.toString(),
                fullRange = wasmColorSpaceFullRange(it),
            )
        },
    )
}

private fun JsAny.toNumber(): Double = unsafeCast<JsNumber>().toDouble()

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
