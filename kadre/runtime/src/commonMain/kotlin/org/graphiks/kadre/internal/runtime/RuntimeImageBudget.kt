package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.surface.*

/** Counts immutable image identities once across pending/current window and cursor leases. */
internal class RuntimeImageBudget(private val resources: ResourceBudgetPolicy,
    private var payloadBudget: RuntimePayloadBudget = RuntimeRetainedPayloadBudget(resources.maxRetainedPayloadBytesPerSession)) {
    private class Entry(val lease: AutoCloseable, var references: Int)
    private val lock = RuntimeLock()
    private val entries = IdentityKeyedMap<Entry>()
    private var identities = 0
    fun installPayloadBudget(budget: RuntimePayloadBudget) = lock.withLock {
        check(identities == 0) { "payload budget must be installed before image admission" }
        payloadBudget = budget
    }
    fun retain(image: BinaryImage?): KadreResult<RuntimeImageLease> = lock.withLock {
        if(image == null) return KadreResult.Success(RuntimeImageLease {})
        val bytes = image.byteCount.toLong()
        if(bytes > resources.maxImageBytesPerResource) return KadreResult.Failure(
            KadreFailure.ResourceLimitExceeded(KadreResourceKind.ImageResource, resources.maxImageBytesPerResource))
        val existing = entries[image]
        if(existing == null) {
            val lease = when (val reserved = payloadBudget.retain(bytes)) {
                is KadreResult.Failure -> return reserved
                is KadreResult.Success -> reserved.value
            }
            entries[image] = Entry(lease, 1); identities++
        } else existing.references++
        KadreResult.Success(RuntimeImageLease { lock.withLock {
            val entry = checkNotNull(entries[image])
            if(--entry.references == 0) { entries.remove(image); identities--; entry.lease.close() }
        } })
    }
}

internal typealias RuntimeImageLease = RuntimePayloadLease

internal fun imageConstraintFailure(image: BinaryImage, constraints: ImageConstraints?, field: String): KadreFailure? {
    val size = image.pixelSize
    return if(constraints == null || image.format !in constraints.formats || size == null ||
        size.width > constraints.maximumSize.width || size.height > constraints.maximumSize.height)
        KadreFailure.InvalidRequest(field) else null
}
