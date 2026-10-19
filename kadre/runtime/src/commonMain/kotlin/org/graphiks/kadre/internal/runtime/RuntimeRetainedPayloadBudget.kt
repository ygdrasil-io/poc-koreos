package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.diagnostics.*

/** Session-wide reservation SPI; native bridges receive only its reservation callback. */
public fun interface RuntimePayloadBudget {
    public fun retain(bytes: Long): KadreResult<AutoCloseable>
}

internal class RuntimeRetainedPayloadBudget(private val limit: Long) : RuntimePayloadBudget {
    init { require(limit > 0) }
    private val lock = RuntimeLock()
    private var retained = 0L
    val used: Long get() = lock.withLock { retained }
    override fun retain(bytes: Long): KadreResult<AutoCloseable> = lock.withLock {
        if (bytes < 0) return KadreResult.Failure(KadreFailure.InvalidRequest("payloadBytes"))
        if (bytes > limit - retained) return KadreResult.Failure(
            KadreFailure.ResourceLimitExceeded(KadreResourceKind.RetainedPayload, limit))
        retained += bytes
        KadreResult.Success(RuntimePayloadLease { lock.withLock { retained -= bytes } })
    }
}

internal class RuntimePayloadLease(private val release: () -> Unit) : AutoCloseable {
    private val lock = RuntimeLock()
    private var closed = false
    override fun close() {
        val run = lock.withLock { if(closed) false else { closed = true; true } }
        if(run) release()
    }
}

/** One immutable payload can appear in several collector queues without being copied. */
internal class RuntimeSharedPayloadLease(private val lease: AutoCloseable) : AutoCloseable {
    private val lock = RuntimeLock()
    private var references = 1
    private val owner = RuntimePayloadLease(::release)
    fun fork(): AutoCloseable = lock.withLock {
        check(references in 1 until Int.MAX_VALUE)
        references++
        RuntimePayloadLease(::release)
    }
    private fun release() {
        val last = lock.withLock { check(references > 0); --references == 0 }
        if (last) lease.close()
    }
    override fun close() = owner.close()
}
