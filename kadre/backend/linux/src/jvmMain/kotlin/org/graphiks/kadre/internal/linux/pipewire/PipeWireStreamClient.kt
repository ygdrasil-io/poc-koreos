package org.graphiks.kadre.internal.linux.pipewire

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.graphiks.kadre.capture.AlphaMode
import org.graphiks.kadre.capture.CaptureCadence
import org.graphiks.kadre.capture.CaptureConfiguration
import org.graphiks.kadre.capture.CaptureConfigurationRevision
import org.graphiks.kadre.capture.CaptureCursorMode
import org.graphiks.kadre.capture.CaptureOrientation
import org.graphiks.kadre.capture.CaptureOutcome
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
import org.graphiks.kadre.internal.runtime.CapturePortPlane
import org.graphiks.kadre.internal.runtime.CapturePortStream
import org.graphiks.kadre.internal.runtime.CapturePortStreamListener
import org.graphiks.kadre.internal.runtime.CapturePortStreamStart
import org.graphiks.kadre.internal.runtime.CapturePortTermination
import org.graphiks.kadre.surface.PhysicalSize
import kotlin.time.Duration.Companion.seconds

/**
 * Consumer-side capture stream over one [PipeWireNativeSession] (design
 * LINUX-PIPEWIRE-STREAM-DESIGN D9/D12). Negotiation is driven purely by
 * param/state events: the Format pod fixes the delivered format (refused
 * unless it maps to a portable [PixelFormat] with a known cadence), the
 * client answers with a buffer proposal, and process events detach frames
 * through [PipeWireNativeSession.pollFrame] — native buffers are never held
 * past a poll. Frames flow to the runtime listener through a bounded queue
 * drained by a dedicated delivery thread: a full queue drops the frame
 * (counted), never blocks the PipeWire loop thread.
 */
internal class PipeWireStreamClient(
    private val session: PipeWireNativeSession,
) : AutoCloseable {

    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private val delivering = AtomicBoolean(false)

    private var negotiatedFormat: NegotiatedFormat? = null
    private var bufferBlocks: Int? = null
    private var bufferStride: Int? = null
    private val configurationRevision = AtomicLong(0)
    private var frameBudgetBytes: Long = Long.MAX_VALUE
    private var listener: CapturePortStreamListener? = null

    private val pending = ArrayDeque<CapturePortFrame>()
    private val pendingSignal = Object()
    private var droppedFrames = AtomicLong(0)
    private var termination: CapturePortTermination? = null
    private var deliveryThread: Thread? = null

    /** Stream events wired into the native session; loop thread only. */
    val events: PipeWireStreamEvents = object : PipeWireStreamEvents {
        override fun onParamChanged(id: Int, pod: ByteArray?) {
            if (closed.get()) return
            handleParamChanged(id, pod)
        }

        override fun onStateChanged(state: Int, error: String?) {
            if (closed.get()) return
            handleStateChanged(state, error)
        }

        override fun onProcess() {
            if (closed.get() || !delivering.get()) return
            handleProcess()
        }
    }

    /**
     * Waits for the negotiation to reach STREAMING (or a terminal state) and
     * returns the effective configuration before any frame is delivered.
     */
    fun start(
        listener: CapturePortStreamListener,
        maxFrameBytes: Long,
        timeoutMillis: Long,
    ): KadreResult<CapturePortStreamStart> {
        if (closed.get()) {
            return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.CaptureSession))
        }
        synchronized(lock) {
            this.listener = listener
            this.frameBudgetBytes = maxFrameBytes
        }
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (true) {
            synchronized(lock) { termination }?.let { return KadreResult.Failure(terminalFailure(it)) }
            val state = session.currentState()
            if (state == PipeWireStreamStates.STREAMING) break
            if (state == PipeWireStreamStates.ERROR) {
                return KadreResult.Failure(streamFailure("stream-error"))
            }
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) {
                return KadreResult.Failure(streamFailure("negotiation-timeout"))
            }
            session.awaitStateChange(state, remaining)
        }
        val format = synchronized(lock) { negotiatedFormat }
            ?: return KadreResult.Failure(streamFailure("format-absent"))
        if (!delivering.compareAndSet(false, true)) {
            return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.CaptureSession))
        }
        val thread = Thread({ drainLoop() }, "kadre-pipewire-delivery").apply { isDaemon = true }
        synchronized(lock) { deliveryThread = thread }
        thread.start()
        return KadreResult.Success(
            CapturePortStreamStart(
                stream = StreamHandle(),
                configuration = format.configuration(configurationRevision.get()),
            ),
        )
    }

    /** Frames dropped because the delivery queue was full (observability). */
    fun droppedFrameCount(): Long = droppedFrames.get()

    private fun handleParamChanged(id: Int, pod: ByteArray?) {
        if (pod == null) return
        when (id) {
            PARAM_FORMAT -> applyFormat(pod)
            PARAM_BUFFERS -> applyBuffers(pod)
        }
    }

    private fun applyFormat(pod: ByteArray) {
        val objectPod = decodeObject(pod) ?: return
        var mediaType = -1
        var mediaSubtype = -1
        var formatId = -1
        var width = -1
        var height = -1
        var framerateNum = -1
        var framerateDen = -1
        objectPod.properties.forEach { prop ->
            when (prop.key) {
                KEY_MEDIA_TYPE -> (prop.value as? SpaValue.IdValue)?.let { mediaType = it.value }
                KEY_MEDIA_SUBTYPE -> (prop.value as? SpaValue.IdValue)?.let { mediaSubtype = it.value }
                KEY_VIDEO_FORMAT -> choiceDefault(prop.value)?.let { formatId = it }
                KEY_VIDEO_SIZE -> (prop.value as? SpaValue.RectangleValue)?.let {
                    width = it.width
                    height = it.height
                }
                KEY_VIDEO_FRAMERATE -> fractionDefault(prop.value)?.let { (num, den) ->
                    framerateNum = num
                    framerateDen = den
                }
            }
        }
        if (mediaType != MEDIA_TYPE_VIDEO || mediaSubtype != MEDIA_SUBTYPE_RAW) {
            terminateFailure("format-not-raw-video")
            return
        }
        val mapped = deliverableFormat(formatId)
        val cadence = fixedCadence(framerateNum, framerateDen)
        if (mapped == null || width <= 0 || height <= 0 || cadence == null) {
            terminateFailure("format-unsupported")
            return
        }
        val reconfigured = synchronized(lock) {
            val previous = negotiatedFormat
            val next = NegotiatedFormat(formatId, mapped, width, height, cadence)
            negotiatedFormat = next
            previous != null && previous != next && delivering.get()
        }
        if (reconfigured) {
            val revision = configurationRevision.incrementAndGet()
            val configuration = synchronized(lock) { negotiatedFormat!!.configuration(revision) }
            listener?.onReconfigured(configuration)
        }
        proposeBuffers(formatId, width, height)
    }

    /** Chooses the buffer proposal; planar layouts stay with the allocator. */
    private fun proposeBuffers(formatId: Int, width: Int, height: Int) {
        when (formatId) {
            SPA_VIDEO_FORMAT_BGRX, SPA_VIDEO_FORMAT_RGBA, SPA_VIDEO_FORMAT_BGRA -> {
                val stride = width * 4
                session.updateBufferParams(count = 2, blocks = 1, size = stride * height, stride = stride)
            }
            else -> session.updateBufferParams(count = 2, blocks = null, size = null, stride = null)
        }
    }

    private fun applyBuffers(pod: ByteArray) {
        val objectPod = decodeObject(pod) ?: return
        var blocks: Int? = null
        var stride: Int? = null
        objectPod.properties.forEach { prop ->
            when (prop.key) {
                KEY_BUFFERS_BLOCKS -> (prop.value as? SpaValue.IntValue)?.let { blocks = it.value }
                KEY_BUFFERS_STRIDE -> (prop.value as? SpaValue.IntValue)?.let { stride = it.value }
            }
        }
        synchronized(lock) {
            blocks?.let { bufferBlocks = it }
            stride?.let { bufferStride = it }
        }
    }

    private fun handleStateChanged(state: Int, error: String?) {
        if (state == PipeWireStreamStates.ERROR) {
            terminateFailure(if (error.isNullOrEmpty()) "stream-error" else "stream-error: $error")
            return
        }
        val streaming = delivering.get()
        if (streaming && state == PipeWireStreamStates.UNCONNECTED) {
            terminate(CapturePortTermination.SourceLost)
        }
    }

    private fun handleProcess() {
        val planes = session.pollFrame() ?: return
        val format = synchronized(lock) { negotiatedFormat } ?: return
        if (planes.sumOf { it.size.toLong() } > frameBudgetBytes) {
            terminate(
                CapturePortTermination.Outcome(
                    CaptureOutcome.Failed(
                        KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, frameBudgetBytes),
                    ),
                ),
            )
            return
        }
        val blocks = synchronized(lock) { bufferBlocks }
        val strideHint = synchronized(lock) { bufferStride }
        val frame = buildFrame(format, planes, blocks, strideHint) ?: run {
            terminateFailure("frame-layout-invalid")
            return
        }
        var sink: CapturePortStreamListener? = null
        var queued = false
        synchronized(pendingSignal) {
            if (termination == null && pending.size < DELIVERY_CAPACITY) {
                pending.addLast(frame)
                queued = true
                pendingSignal.notifyAll()
                sink = listener
            }
        }
        if (!queued) droppedFrames.incrementAndGet()
        if (sink == null && queued) {
            // no listener installed yet: the frame will drain after start
        }
    }

    /** Dedicated delivery thread: isolates the loop from slow consumers. */
    private fun drainLoop() {
        while (true) {
            val (frame, terminal, sink) = synchronized(pendingSignal) {
                while (pending.isEmpty() && termination == null && !closed.get()) {
                    pendingSignal.wait()
                }
                Triple(pending.removeFirstOrNull(), termination, listener)
            }
            if (frame != null) {
                runCatching { sink?.onFrame(frame) }
                continue
            }
            if (terminal != null || closed.get()) {
                runCatching { sink?.onTerminated(terminal ?: CapturePortTermination.SourceLost) }
                return
            }
        }
    }

    private fun buildFrame(
        format: NegotiatedFormat,
        planes: List<PolledPlane>,
        blocks: Int?,
        strideHint: Int?,
    ): CapturePortFrame? {
        val expectedBlocks = blocks ?: format.expectedPlaneCount()
        if (planes.size != expectedBlocks) return null
        val stride = (strideHint ?: format.defaultStride()).takeIf { it > 0 } ?: return null
        val layouts = format.planeLayouts(stride) ?: return null
        val portPlanes = planes.mapIndexedNotNull { index, plane ->
            val layout = layouts.getOrNull(index) ?: return@mapIndexedNotNull null
            // the chunk must cover the layout: byteCount rows at rowStride
            if (plane.size < layout.byteCount) return@mapIndexedNotNull null
            CapturePortPlane(layout = layout, bytes = plane.bytes.copyOf(layout.byteCount))
        }
        if (portPlanes.size != layouts.size) return null
        return CapturePortFrame(
            size = PhysicalSize(format.width, format.height),
            format = format.pixelFormat,
            planes = portPlanes,
            configurationRevision = configurationRevision.get(),
            sourceTimestamp = null,
            duration = null,
            discontinuity = null,
            colorEncoding = SrgbEncoding,
            alphaMode = format.alphaMode,
            orientation = CaptureOrientation.Upright,
        )
    }

    private fun terminate(termination: CapturePortTermination) {
        var wakeDrainer = false
        synchronized(pendingSignal) {
            if (this.termination == null) {
                this.termination = termination
                delivering.set(false)
                wakeDrainer = true
            }
        }
        if (wakeDrainer) synchronized(pendingSignal) { pendingSignal.notifyAll() }
    }

    private fun streamFailure(code: String): KadreFailure =
        KadreFailure.PlatformFailure(KadrePlatform.Linux, "pipewire-stream", code)

    private fun terminateFailure(code: String) {
        terminate(CapturePortTermination.Outcome(CaptureOutcome.Failed(streamFailure(code))))
    }

    private fun terminalFailure(termination: CapturePortTermination): KadreFailure =
        (termination as? CapturePortTermination.Outcome)
            ?.outcome
            ?.let { it as? CaptureOutcome.Failed }
            ?.failure
            ?: streamFailure("terminated")

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            delivering.set(false)
            synchronized(pendingSignal) { pendingSignal.notifyAll() }
            session.close()
        }
    }

    /** The runtime-facing stream handle: non-blocking, idempotent close. */
    private inner class StreamHandle : CapturePortStream {
        override fun close() {
            this@PipeWireStreamClient.close()
        }
    }

    private fun decodeObject(pod: ByteArray): SpaValue.ObjectValue? =
        runCatching { SpaPodReader.read(java.lang.foreign.MemorySegment.ofArray(pod)) }.getOrNull()
            as? SpaValue.ObjectValue

    private fun choiceDefault(value: SpaValue): Int? = when (value) {
        is SpaValue.IdValue -> value.value
        is SpaValue.ChoiceValue -> (value.values.firstOrNull() as? SpaValue.IdValue)?.value
        else -> null
    }

    private fun fractionDefault(value: SpaValue): Pair<Int, Int>? = when (value) {
        is SpaValue.FractionValue -> value.numerator to value.denominator
        is SpaValue.ChoiceValue -> (value.values.firstOrNull() as? SpaValue.FractionValue)
            ?.let { it.numerator to it.denominator }
        else -> null
    }

    /** Everything the negotiated Format pod fixed. */
    private data class NegotiatedFormat(
        val formatId: Int,
        val pixelFormat: PixelFormat,
        val width: Int,
        val height: Int,
        val cadence: CaptureCadence,
    ) {
        val alphaMode: AlphaMode = when (pixelFormat) {
            PixelFormat.Bgrx8 -> AlphaMode.Opaque
            else -> AlphaMode.Premultiplied
        }

        fun configuration(revision: Long): CaptureConfiguration = CaptureConfiguration(
            revision = CaptureConfigurationRevision(revision),
            size = PhysicalSize(width, height),
            format = pixelFormat,
            colorEncoding = SrgbEncoding,
            alphaMode = alphaMode,
            orientation = CaptureOrientation.Upright,
            cadence = cadence,
            region = null,
            cursorMode = CaptureCursorMode.Hidden,
        )

        fun expectedPlaneCount(): Int = when (pixelFormat) {
            PixelFormat.Rgba8, PixelFormat.Bgra8, PixelFormat.Bgrx8 -> 1
            PixelFormat.Nv12 -> 2
            PixelFormat.I420 -> 3
            is PixelFormat.Opaque -> 0
        }

        fun defaultStride(): Int = when (pixelFormat) {
            PixelFormat.Rgba8, PixelFormat.Bgra8, PixelFormat.Bgrx8 -> width * 4
            PixelFormat.Nv12, PixelFormat.I420 -> width
            is PixelFormat.Opaque -> 0
        }

        fun planeLayouts(stride: Int): List<PixelPlaneLayout>? = when (pixelFormat) {
            PixelFormat.Rgba8, PixelFormat.Bgra8, PixelFormat.Bgrx8 ->
                listOf(plane(width, height, stride, 4, 1, 1))
            PixelFormat.Nv12 -> listOf(
                plane(width, height, stride, 1, 1, 1),
                plane(width.ceilHalf(), height.ceilHalf(), stride, 2, 2, 2),
            )
            PixelFormat.I420 -> listOf(
                plane(width, height, stride, 1, 1, 1),
                plane(width.ceilHalf(), height.ceilHalf(), stride, 1, 2, 2),
                plane(width.ceilHalf(), height.ceilHalf(), stride, 1, 2, 2),
            )
            is PixelFormat.Opaque -> null
        }

        private fun plane(
            width: Int,
            height: Int,
            rowStride: Int,
            pixelStride: Int,
            horizontalSubsampling: Int,
            verticalSubsampling: Int,
        ) = PixelPlaneLayout(
            width = width,
            height = height,
            rowStride = rowStride,
            pixelStride = pixelStride,
            byteCount = rowStride * height,
            horizontalSubsampling = horizontalSubsampling,
            verticalSubsampling = verticalSubsampling,
        )

        private fun Int.ceilHalf(): Int = this / 2 + this % 2
    }

    companion object {
        private const val DELIVERY_CAPACITY = 3

        // SPA param ids and prop keys pinned from the headers (kffi-pipewire)
        private const val PARAM_FORMAT = 4
        private const val PARAM_BUFFERS = 5
        private const val KEY_MEDIA_TYPE = 1
        private const val KEY_MEDIA_SUBTYPE = 2
        private const val KEY_VIDEO_FORMAT = 131073
        private const val KEY_VIDEO_SIZE = 131075
        private const val KEY_VIDEO_FRAMERATE = 131076
        private const val KEY_BUFFERS_BLOCKS = 2
        private const val KEY_BUFFERS_STRIDE = 4
        private const val MEDIA_TYPE_VIDEO = 2
        private const val MEDIA_SUBTYPE_RAW = 1

        private const val SPA_VIDEO_FORMAT_I420 = 2
        private const val SPA_VIDEO_FORMAT_BGRX = 8
        private const val SPA_VIDEO_FORMAT_RGBA = 11
        private const val SPA_VIDEO_FORMAT_BGRA = 12
        private const val SPA_VIDEO_FORMAT_NV12 = 23

        /** D12: only formats with a portable PixelFormat mapping deliver. */
        private fun deliverableFormat(spaFormatId: Int): PixelFormat? = when (spaFormatId) {
            SPA_VIDEO_FORMAT_BGRX -> PixelFormat.Bgrx8
            SPA_VIDEO_FORMAT_RGBA -> PixelFormat.Rgba8
            SPA_VIDEO_FORMAT_BGRA -> PixelFormat.Bgra8
            SPA_VIDEO_FORMAT_NV12 -> PixelFormat.Nv12
            SPA_VIDEO_FORMAT_I420 -> PixelFormat.I420
            else -> null
        }

        /** A known positive framerate is required to describe the cadence. */
        private fun fixedCadence(numerator: Int, denominator: Int): CaptureCadence.Fixed? {
            if (numerator <= 0 || denominator <= 0) return null
            val interval = (denominator.toDouble() / numerator.toDouble()).seconds
            return interval.takeIf { it.isPositive() && it.isFinite() }?.let(CaptureCadence::Fixed)
        }

        /** 8-bit sRGB full range — what BGRx/BGRA videotest sources produce. */
        private val SrgbEncoding = ColorEncoding(
            primaries = ColorPrimaries.Bt709,
            transfer = TransferFunction.Srgb,
            matrix = MatrixCoefficients.Identity,
            range = ColorRange.Full,
            hdr = HdrMetadata.None,
        )
    }
}
