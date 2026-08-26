package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import java.util.concurrent.atomic.AtomicBoolean

/** One session-owned allocator shared by every public event-flow source in that session. */
internal class RuntimeEventCollectorAllocator(internal val sessionLimit: Int) {
    private val lock = Any()
    private val activeByFlow = mutableMapOf<RuntimeEventCollectorGate, Int>()
    private var activeInSession = 0

    init {
        require(sessionLimit > 0) { "sessionLimit must be positive" }
    }

    fun newGate(perFlowLimit: Int): RuntimeEventCollectorGate {
        require(perFlowLimit > 0) { "perFlowLimit must be positive" }
        return RuntimeEventCollectorGate(this, perFlowLimit)
    }

    internal fun tryAcquire(
        gate: RuntimeEventCollectorGate,
        perFlowLimit: Int,
    ): KadreResult<RuntimeEventCollectorLease> = synchronized(lock) {
        val activeInFlow = activeByFlow[gate] ?: 0
        when {
            activeInFlow >= perFlowLimit -> KadreResult.Failure(
                KadreFailure.ResourceLimitExceeded(
                    KadreResourceKind.EventCollector,
                    perFlowLimit.toLong(),
                ),
            )

            activeInSession >= sessionLimit -> KadreResult.Failure(
                KadreFailure.ResourceLimitExceeded(
                    KadreResourceKind.EventCollector,
                    sessionLimit.toLong(),
                ),
            )

            else -> {
                activeByFlow[gate] = activeInFlow + 1
                activeInSession += 1
                KadreResult.Success(RuntimeEventCollectorLease { release(gate) })
            }
        }
    }

    private fun release(gate: RuntimeEventCollectorGate) {
        synchronized(lock) {
            val activeInFlow = checkNotNull(activeByFlow[gate]) {
                "event collector gate was released without an active lease"
            }
            if (activeInFlow == 1) {
                activeByFlow.remove(gate)
            } else {
                activeByFlow[gate] = activeInFlow - 1
            }
            activeInSession -= 1
            check(activeInSession >= 0) { "event collector allocator underflow" }
        }
    }
}

internal class RuntimeEventCollectorGate internal constructor(
    private val allocator: RuntimeEventCollectorAllocator,
    private val perFlowLimit: Int,
) {
    fun tryAcquire(): KadreResult<RuntimeEventCollectorLease> = allocator.tryAcquire(this, perFlowLimit)
}

internal class RuntimeEventCollectorLease internal constructor(
    private val releaseAction: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) releaseAction()
    }
}

internal fun <T> Flow<T>.withEventCollectorAdmission(gate: RuntimeEventCollectorGate): Flow<T> = flow {
    val lease = when (val admission = gate.tryAcquire()) {
        is KadreResult.Success -> admission.value
        is KadreResult.Failure -> throw KadreException(admission.reason)
    }
    try {
        this@withEventCollectorAdmission.collect { value -> emit(value) }
    } finally {
        lease.close()
    }
}
