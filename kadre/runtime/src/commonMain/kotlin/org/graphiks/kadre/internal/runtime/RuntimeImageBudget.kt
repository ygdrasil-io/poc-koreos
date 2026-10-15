package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.surface.*

/** Counts immutable image identities once across pending/current window and cursor leases. */
internal class RuntimeImageBudget(private val resources: ResourceBudgetPolicy) {
    private class Entry(val bytes: Long, var references: Int)
    private val lock = RuntimeLock()
    private val entries = IdentityKeyedMap<Entry>()
    private var retained = 0L
    fun retain(image: BinaryImage?): KadreResult<RuntimeImageLease> = lock.withLock {
        if(image == null) return KadreResult.Success(RuntimeImageLease {})
        val bytes = image.byteCount.toLong()
        if(bytes > resources.maxImageBytesPerResource) return KadreResult.Failure(
            KadreFailure.ResourceLimitExceeded(KadreResourceKind.ImageResource, resources.maxImageBytesPerResource))
        val existing = entries[image]
        if(existing == null) {
            if(bytes > resources.maxRetainedPayloadBytesPerSession - retained) return KadreResult.Failure(
                KadreFailure.ResourceLimitExceeded(KadreResourceKind.RetainedPayload, resources.maxRetainedPayloadBytesPerSession))
            entries[image] = Entry(bytes, 1); retained += bytes
        } else existing.references++
        KadreResult.Success(RuntimeImageLease { lock.withLock {
            val entry = checkNotNull(entries[image])
            if(--entry.references == 0) { retained -= entry.bytes; entries.remove(image) }
        } })
    }
}

internal class RuntimeImageLease(private val release: () -> Unit) : AutoCloseable {
    private val lock = RuntimeLock()
    private var closed = false
    override fun close() { val run = lock.withLock { if(closed) false else { closed = true; true } }; if(run) release() }
}

internal fun imageConstraintFailure(image: BinaryImage, constraints: ImageConstraints?, field: String): KadreFailure? {
    val size = image.pixelSize
    return if(constraints == null || image.format !in constraints.formats || size == null ||
        size.width > constraints.maximumSize.width || size.height > constraints.maximumSize.height)
        KadreFailure.InvalidRequest(field) else null
}
