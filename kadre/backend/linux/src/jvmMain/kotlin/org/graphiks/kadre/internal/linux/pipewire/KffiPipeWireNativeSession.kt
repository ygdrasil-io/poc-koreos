package org.graphiks.kadre.internal.linux.pipewire

import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.graphiks.kffi.pipewire.PipeWireBuffers
import org.graphiks.kffi.pipewire.PipeWireContext
import org.graphiks.kffi.pipewire.PipeWireCore
import org.graphiks.kffi.pipewire.PipeWireProperties
import org.graphiks.kffi.pipewire.PipeWireStream
import org.graphiks.kffi.pipewire.PipeWireStreamEvents as KffiStreamEvents
import org.graphiks.kffi.pipewire.PipeWireThreadLoop
import org.graphiks.kffi.pipewire.generated.KFFI_SPA_CHOICE_ENUM
import org.graphiks.kffi.pipewire.generated.KFFI_SPA_CHOICE_RANGE
import org.graphiks.kffi.pipewire.generated.KFFI_PW_STREAM_FLAG_AUTOCONNECT
import org.graphiks.kffi.pipewire.generated.KFFI_PW_STREAM_FLAG_MAP_BUFFERS
import org.graphiks.kffi.pipewire.generated.KFFI_SPA_DATA_MEMPTR
import org.graphiks.kffi.pipewire.generated.KFFI_SPA_FORMAT_MEDIA_SUBTYPE
import org.graphiks.kffi.pipewire.generated.KFFI_SPA_FORMAT_MEDIA_TYPE
import org.graphiks.kffi.pipewire.generated.KFFI_SPA_FORMAT_VIDEO_FORMAT
import org.graphiks.kffi.pipewire.generated.KFFI_SPA_FORMAT_VIDEO_FRAMERATE
import org.graphiks.kffi.pipewire.generated.KFFI_SPA_FORMAT_VIDEO_SIZE
import org.graphiks.kffi.pipewire.generated.KFFI_SPA_MEDIA_SUBTYPE_RAW
import org.graphiks.kffi.pipewire.generated.KFFI_SPA_MEDIA_TYPE_VIDEO
import org.graphiks.kffi.pipewire.generated.KFFI_SPA_PARAM_BUFFERS
import org.graphiks.kffi.pipewire.generated.KFFI_SPA_PARAM_BUFFERS_BLOCKS
import org.graphiks.kffi.pipewire.generated.KFFI_SPA_PARAM_BUFFERS_BUFFERS
import org.graphiks.kffi.pipewire.generated.KFFI_SPA_PARAM_BUFFERS_SIZE
import org.graphiks.kffi.pipewire.generated.KFFI_SPA_PARAM_BUFFERS_STRIDE
import org.graphiks.kffi.pipewire.generated.KFFI_SPA_PARAM_ENUM_FORMAT
import org.graphiks.kffi.pipewire.generated.KFFI_SPA_TYPE_OBJECT_FORMAT
import org.graphiks.kffi.pipewire.generated.KFFI_SPA_TYPE_OBJECT_PARAM_BUFFERS
import org.graphiks.kffi.pipewire.generated.KFFI_SPA_VIDEO_FORMAT_BGRA
import org.graphiks.kffi.pipewire.generated.KFFI_SPA_VIDEO_FORMAT_BGRX
import org.graphiks.kffi.pipewire.generated.KFFI_SPA_VIDEO_FORMAT_RGBA
import org.graphiks.kffi.pipewire.generated.pw_stream_state
import org.graphiks.kffi.pipewire.generated.spa_direction

/**
 * The real [PipeWireNativeSession] over kffi-pipewire (design D9): one
 * thread loop, one context, one fd-connected core (the portal's
 * `OpenPipeWireRemote` fd — never closed here, the reservation owns it) and
 * one consumer stream. Callbacks arrive on the loop thread with the loop
 * lock held (recursive); every plane is copied and the buffer requeued
 * inside [pollFrame] before it returns.
 */
internal class KffiPipeWireNativeSession(
    fd: Int,
    private val targetNodeId: Int,
    private val listener: PipeWireStreamEvents,
) : PipeWireNativeSession {

    private val closed = AtomicBoolean(false)
    private val stateLock = ReentrantLock()
    private val stateChanged = stateLock.newCondition()

    private val loop: PipeWireThreadLoop
    private val context: PipeWireContext
    private val core: PipeWireCore
    private val stream: PipeWireStream
    private var lastObservedState: Int = PipeWireStreamStates.UNCONNECTED

    init {
        loop = PipeWireThreadLoop.create("kadre-pipewire", null)
        check(loop.start() == 0) { "pw_thread_loop_start failed" }
        context = java.lang.foreign.Arena.ofConfined().use { arena ->
            PipeWireProperties.fromString(arena, STREAM_PROPERTIES).use { properties ->
                PipeWireContext.create(loop, properties)
            }
        }
        core = context.connectFd(fd, null)
        stream = java.lang.foreign.Arena.ofConfined().use { arena ->
            PipeWireStream.create(
                core,
                "kadre-capture",
                PipeWireProperties.fromString(arena, STREAM_PROPERTIES),
            object : KffiStreamEvents {
                override fun onStreamDestroyed() = Unit

                override fun onStateChanged(
                    old: pw_stream_state,
                    state: pw_stream_state,
                    error: String?,
                ) {
                    stateLock.withLock {
                        lastObservedState = state.value.toInt()
                        stateChanged.signalAll()
                    }
                    listener.onStateChanged(state.value.toInt(), error)
                }

                override fun onParamChanged(id: Int, param: MemorySegment?) {
                    val bytes = param?.let(::copyPod)
                    listener.onParamChanged(id, bytes)
                }

                override fun onBufferAdded(buffer: MemorySegment) = Unit

                override fun onBufferRemoved(buffer: MemorySegment) = Unit

                override fun onProcess() {
                    listener.onProcess()
                }

                override fun onError(throwable: Throwable) {
                    listener.onStateChanged(PipeWireStreamStates.ERROR, throwable.message)
                }
            },
            )
        }
        withPods(listOf(enumFormatPod())) { pods ->
            loop.withLock {
                stream.connect(
                    direction = spa_direction.SPA_DIRECTION_INPUT.value.toInt(),
                    targetId = targetNodeId,
                    flagsMask = KFFI_PW_STREAM_FLAG_AUTOCONNECT() or KFFI_PW_STREAM_FLAG_MAP_BUFFERS(),
                    pods = pods,
                )
            }
        }
    }

    override fun awaitStateChange(currentState: Int, timeoutMillis: Long): Int {
        val deadline = System.currentTimeMillis() + timeoutMillis
        stateLock.withLock {
            while (lastObservedState == currentState) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return lastObservedState
                stateChanged.await(remaining, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
            return lastObservedState
        }
    }

    override fun currentState(): Int {
        if (closed.get()) return PipeWireStreamStates.UNCONNECTED
        val state = loop.withLock { stream.state() }
        val value = state.value.toInt()
        stateLock.withLock {
            lastObservedState = value
            stateChanged.signalAll()
        }
        return value
    }

    override fun updateBufferParams(count: Int, blocks: Int?, size: Int?, stride: Int?) {
        if (closed.get()) return
        withPods(listOf(buffersProposal(count, blocks, size, stride))) { pods ->
            loop.withLock {
                stream.updateParams(pods)
            }
        }
    }

    override fun pollFrame(): List<PolledPlane>? {
        if (closed.get()) return null
        return loop.withLock {
            val buffer = stream.dequeueBuffer()
            if (buffer == MemorySegment.NULL) return@withLock null
            try {
                val planeCount = PipeWireBuffers.planeCount(buffer)
                if (planeCount <= 0) return@withLock emptyList()
                val planes = ArrayList<PolledPlane>(planeCount)
                for (index in 0 until planeCount) {
                    val view = PipeWireBuffers.plane(buffer, index)
                    if (view.type != KFFI_SPA_DATA_MEMPTR() || view.data == MemorySegment.NULL) {
                        return@withLock emptyList()
                    }
                    // copy the valid chunk only, then let the source reuse it
                    val source = view.data.reinterpret((view.offset + view.size).toLong())
                    val bytes = ByteArray(view.size)
                    MemorySegment.copy(source, ValueLayout.JAVA_BYTE, view.offset.toLong(), bytes, 0, view.size)
                    planes += PolledPlane(view.type, view.offset, view.size, view.stride, bytes)
                }
                planes
            } finally {
                stream.queueBuffer(buffer)
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { stream.close() }
        runCatching { core.close() }
        runCatching { context.close() }
        runCatching { loop.close() }
        stateLock.withLock {
            lastObservedState = PipeWireStreamStates.UNCONNECTED
            stateChanged.signalAll()
        }
    }

    /** Copies a native pod into bytes: size first from the header, then all. */
    private fun copyPod(param: MemorySegment): ByteArray {
        val header = param.reinterpret(8)
        val bodySize = header.get(ValueLayout.JAVA_INT_UNALIGNED, 0)
        val total = 8 + bodySize
        val bytes = ByteArray(total)
        MemorySegment.copy(param.reinterpret(total.toLong()), ValueLayout.JAVA_BYTE, 0, bytes, 0, total)
        return bytes
    }

    /** Pods are serialized synchronously by the pw call: heap segments suffice. */
    private inline fun <T> withPods(pods: List<ByteArray>, block: (List<MemorySegment>) -> T): T =
        block(pods.map(MemorySegment::ofArray))

    private fun enumFormatPod(): ByteArray = SpaPodWriter.toByteArray(
        SpaValue.ObjectValue(
            objectType = KFFI_SPA_TYPE_OBJECT_FORMAT(),
            id = KFFI_SPA_PARAM_ENUM_FORMAT(),
            properties = listOf(
                SpaValue.PropValue(KFFI_SPA_FORMAT_MEDIA_TYPE(), 0, SpaValue.IdValue(KFFI_SPA_MEDIA_TYPE_VIDEO())),
                SpaValue.PropValue(KFFI_SPA_FORMAT_MEDIA_SUBTYPE(), 0, SpaValue.IdValue(KFFI_SPA_MEDIA_SUBTYPE_RAW())),
                SpaValue.PropValue(
                    KFFI_SPA_FORMAT_VIDEO_FORMAT(),
                    0,
                    SpaValue.ChoiceValue(
                        choiceType = KFFI_SPA_CHOICE_ENUM(),
                        flags = 0,
                        values = listOf(
                            SpaValue.IdValue(KFFI_SPA_VIDEO_FORMAT_BGRX()),
                            SpaValue.IdValue(KFFI_SPA_VIDEO_FORMAT_BGRA()),
                            SpaValue.IdValue(KFFI_SPA_VIDEO_FORMAT_RGBA()),
                        ),
                    ),
                ),
                SpaValue.PropValue(
                    KFFI_SPA_FORMAT_VIDEO_SIZE(),
                    0,
                    SpaValue.ChoiceValue(
                        choiceType = KFFI_SPA_CHOICE_RANGE(),
                        flags = 0,
                        values = listOf(
                            SpaValue.RectangleValue(1024, 768),
                            SpaValue.RectangleValue(1, 1),
                            SpaValue.RectangleValue(16384, 16384),
                        ),
                    ),
                ),
                SpaValue.PropValue(
                    KFFI_SPA_FORMAT_VIDEO_FRAMERATE(),
                    0,
                    SpaValue.ChoiceValue(
                        choiceType = KFFI_SPA_CHOICE_RANGE(),
                        flags = 0,
                        values = listOf(
                            SpaValue.FractionValue(0, 1),
                            SpaValue.FractionValue(0, 1),
                            SpaValue.FractionValue(1000, 1),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun buffersProposal(count: Int, blocks: Int?, size: Int?, stride: Int?): ByteArray {
        val props = ArrayList<SpaValue.PropValue>()
        props += SpaValue.PropValue(
            KFFI_SPA_PARAM_BUFFERS_BUFFERS(),
            0,
            SpaValue.ChoiceValue(
                KFFI_SPA_CHOICE_RANGE(),
                0,
                listOf(SpaValue.IntValue(count), SpaValue.IntValue(1), SpaValue.IntValue(8)),
            ),
        )
        blocks?.let { props += SpaValue.PropValue(KFFI_SPA_PARAM_BUFFERS_BLOCKS(), 0, SpaValue.IntValue(it)) }
        size?.let { props += SpaValue.PropValue(KFFI_SPA_PARAM_BUFFERS_SIZE(), 0, SpaValue.IntValue(it)) }
        stride?.let { props += SpaValue.PropValue(KFFI_SPA_PARAM_BUFFERS_STRIDE(), 0, SpaValue.IntValue(it)) }
        return SpaPodWriter.toByteArray(
            SpaValue.ObjectValue(
                objectType = KFFI_SPA_TYPE_OBJECT_PARAM_BUFFERS(),
                id = KFFI_SPA_PARAM_BUFFERS(),
                properties = props,
            ),
        )
    }

    private companion object {
        private const val STREAM_PROPERTIES =
            "media.type=Video media.category=Source media.role=Screen"
    }
}
