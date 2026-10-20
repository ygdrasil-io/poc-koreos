package org.graphiks.kadre.internal.linux.pipewire

/**
 * Native seam for one PipeWire capture stream (design
 * LINUX-PIPEWIRE-STREAM-DESIGN D11): the client state machine below drives
 * negotiation and frame discipline entirely against this interface, so the
 * O2 proofs run without libpipewire. The real implementation lives in
 * [KffiPipeWireNativeSession] over kffi-pipewire.
 *
 * Threading contract: [PipeWireStreamEvents] fire on the native loop thread;
 * the other members may be called from any thread. After [close] returns no
 * event fires again and every member is a no-op returning its empty answer.
 */
internal interface PipeWireNativeSession : AutoCloseable {

    /**
     * Waits (bounded) for the stream state to differ from every state this
     * wait already observed; returns the current state. Real waits ride the
     * thread-loop condition — never a sleep loop past the budget.
     */
    fun awaitStateChange(currentState: Int, timeoutMillis: Long): Int

    fun currentState(): Int

    /** Sends the buffer proposal derived from the negotiated format. */
    fun updateBufferParams(count: Int, blocks: Int?, size: Int?, stride: Int?)

    /**
     * Dequeues the next ready buffer, copies its planes into detached
     * arrays and requeues the native buffer before returning — a native
     * buffer is never held across calls. Null when no buffer is ready.
     */
    fun pollFrame(): List<PolledPlane>?

    /** Teardown (stream → core → context → loop), idempotent. */
    override fun close()
}

/** One detached plane copied out of a polled buffer. */
internal data class PolledPlane(
    val dataType: Int,
    val offset: Int,
    val size: Int,
    val stride: Int,
    val bytes: ByteArray,
) {
    init {
        require(bytes.size >= size) { "polled plane bytes must cover the chunk size" }
    }

    override fun equals(other: Any?): Boolean =
        other is PolledPlane && dataType == other.dataType && offset == other.offset &&
            size == other.size && stride == other.stride && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()
}

/** Stream events surfaced from the native loop thread. */
internal interface PipeWireStreamEvents {
    /** [pod] is the raw pod bytes; null clears the param. */
    fun onParamChanged(id: Int, pod: ByteArray?)

    fun onStateChanged(state: Int, error: String?)

    /** A buffer became ready: the client polls exactly once per event. */
    fun onProcess()
}

/** PipeWire stream states (enum pw_stream_state pinned from the headers). */
internal object PipeWireStreamStates {
    const val ERROR: Int = -1
    const val UNCONNECTED: Int = 0
    const val CONNECTING: Int = 1
    const val PAUSED: Int = 2
    const val STREAMING: Int = 3
}
