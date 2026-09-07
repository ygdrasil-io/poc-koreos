package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DropItemReadMode
import org.graphiks.kadre.input.DropItemDescriptor
import org.graphiks.kadre.input.DropOffer
import org.graphiks.kadre.input.DropOfferId
import org.graphiks.kadre.input.DropOfferState
import org.graphiks.kadre.input.DropOfferTerminationReason
import org.graphiks.kadre.input.DropTransfer
import org.graphiks.kadre.input.DroppedItem
import org.graphiks.kadre.policy.ResourceBudgetPolicy

/** Session-scoped, atomic accounting for transfers from admission through close. */
internal class RuntimeDropTransferBudget(
    private val limit: Int,
) {
    private val lock = Any()
    private var reserved = 0

    fun tryReserve(): RuntimeDropTransferReservation? = synchronized(lock) {
        if (reserved >= limit) return@synchronized null
        reserved += 1
        RuntimeDropTransferReservation(this)
    }

    private fun release() = synchronized(lock) {
        check(reserved > 0) { "drop transfer reservation underflow" }
        reserved -= 1
    }

    internal class RuntimeDropTransferReservation(
        private val budget: RuntimeDropTransferBudget,
    ) : AutoCloseable {
        private var closed = false

        override fun close() {
            val release = synchronized(this) {
                if (closed) false else {
                    closed = true
                    true
                }
            }
            if (release) budget.release()
        }
    }
}

internal data class RuntimeDropItemSource(
    val source: DropItemSource,
    val descriptor: DropItemDescriptor,
    val readMode: DropItemReadMode,
)

/** The runtime-owned lifecycle around one backend-retained drop source. */
internal class RuntimeDropOffer(
    override val id: DropOfferId,
    private val source: DropTransferSource,
    private val itemSources: List<RuntimeDropItemSource>,
    private val resources: ResourceBudgetPolicy,
    private val transferBudget: RuntimeDropTransferBudget,
) : DropOffer {
    private val lock = Any()
    private val mutableState = MutableStateFlow<DropOfferState>(DropOfferState.Presented)
    private var transfer: RuntimeDropTransfer? = null
    private var claimed = false
    private var reservation: RuntimeDropTransferBudget.RuntimeDropTransferReservation? = null
    private var claimTimeout: Job? = null

    override val items: List<DropItemDescriptor> = itemSources.map(RuntimeDropItemSource::descriptor)
    override val state = mutableState.asStateFlow()

    fun accept(): KadreResult<Unit> = synchronized(lock) {
        if (mutableState.value != DropOfferState.Presented) {
            return@synchronized KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.DropTransfer))
        }
        val acquired = transferBudget.tryReserve()
            ?: return@synchronized KadreResult.Failure(
                KadreFailure.ResourceLimitExceeded(
                    KadreResourceKind.DropTransfer,
                    resources.maxConcurrentDropTransfers.toLong(),
                ),
            )
        reservation = acquired
        mutableState.value = DropOfferState.Accepted
        KadreResult.Success(Unit)
    }

    fun perform(
        scope: CoroutineScope?,
        onTransferClosed: (RuntimeDropTransfer) -> Unit,
    ): RuntimeDropTransfer? {
        val created = synchronized(lock) {
            if (mutableState.value != DropOfferState.Accepted) return@synchronized null
            val next = RuntimeDropTransfer(
                source = source,
                itemSources = itemSources,
                resources = resources,
                reservation = checkNotNull(reservation),
                onClosed = onTransferClosed,
            )
            reservation = null
            transfer = next
            mutableState.value = DropOfferState.TransferAvailable
            next
        } ?: return null
        val timeout = scope?.let { timerScope ->
            timerScope.launch {
                delay(resources.dropTransferClaimTimeout)
                timeoutClaim(created)
            }
        }
        if (timeout != null) {
            val cancel = synchronized(lock) {
                if (mutableState.value == DropOfferState.TransferAvailable && transfer === created) {
                    claimTimeout = timeout
                    false
                } else {
                    true
                }
            }
            if (cancel) timeout.cancel()
        }
        return created
    }

    fun terminate(reason: DropOfferTerminationReason): Boolean {
        val cleanup = synchronized(lock) {
            when (mutableState.value) {
                DropOfferState.Claimed,
                is DropOfferState.Terminated,
                -> return false

                else -> {
                    mutableState.value = DropOfferState.Terminated(reason)
                    DropOfferCleanup(
                        timeout = claimTimeout.also { claimTimeout = null },
                        transfer = transfer,
                        source = source.takeIf { transfer == null },
                        reservation = reservation.also { reservation = null },
                    )
                }
            }
        }
        cleanup.close()
        return true
    }

    override suspend fun claimTransfer(): KadreResult<DropTransfer> {
        while (true) {
            var timeoutToCancel: Job? = null
            val immediate = synchronized(lock) {
                when (val current = mutableState.value) {
                    DropOfferState.TransferAvailable -> {
                        if (claimed) {
                            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.DropTransfer))
                        } else {
                            claimed = true
                            mutableState.value = DropOfferState.Claimed
                            timeoutToCancel = claimTimeout
                            claimTimeout = null
                            KadreResult.Success(checkNotNull(transfer))
                        }
                    }

                    DropOfferState.Claimed -> KadreResult.Failure(
                        KadreFailure.AlreadyInUse(KadreResourceKind.DropTransfer),
                    )

                    is DropOfferState.Terminated -> KadreResult.Failure(
                        current.reason.toClaimFailure(),
                    )

                    DropOfferState.Presented,
                    DropOfferState.Accepted,
                    -> null
                }
            }
            timeoutToCancel?.cancel()
            if (immediate != null) return immediate
            state.first { it !in setOf(DropOfferState.Presented, DropOfferState.Accepted) }
        }
    }

    private fun timeoutClaim(expected: RuntimeDropTransfer) {
        val cleanup = synchronized(lock) {
            if (mutableState.value != DropOfferState.TransferAvailable || transfer !== expected) return
            mutableState.value = DropOfferState.Terminated(DropOfferTerminationReason.ClaimTimedOut)
            DropOfferCleanup(
                timeout = null,
                transfer = expected,
                source = null,
                reservation = null,
            )
        }
        cleanup.close()
    }
}

private data class DropOfferCleanup(
    val timeout: Job?,
    val transfer: RuntimeDropTransfer?,
    val source: DropTransferSource?,
    val reservation: RuntimeDropTransferBudget.RuntimeDropTransferReservation?,
) {
    fun close() {
        timeout?.cancel()
        if (transfer != null) {
            transfer.close()
        } else {
            try {
                source?.close()
            } finally {
                reservation?.close()
            }
        }
    }
}

private fun DropOfferTerminationReason.toClaimFailure(): KadreFailure = when (this) {
    DropOfferTerminationReason.Rejected,
    DropOfferTerminationReason.LeftSurface,
    DropOfferTerminationReason.OfferExpired,
    DropOfferTerminationReason.ClaimTimedOut,
    DropOfferTerminationReason.OwnerClosed,
    -> KadreFailure.Closed(KadreResourceKind.DropTransfer)

    is DropOfferTerminationReason.Failed -> failure
}

internal class RuntimeDropTransfer(
    private val source: DropTransferSource,
    private val itemSources: List<RuntimeDropItemSource>,
    private val resources: ResourceBudgetPolicy,
    private val reservation: RuntimeDropTransferBudget.RuntimeDropTransferReservation,
    private val onClosed: (RuntimeDropTransfer) -> Unit,
) : DropTransfer {
    private val lock = Any()
    private var closed = false
    private var reading = false

    override val items: List<DroppedItem> = itemSources.map { item ->
        RuntimeDroppedItem(this, item)
    }

    override fun close() {
        val closeSource = synchronized(lock) {
            if (closed) false else {
                closed = true
                true
            }
        }
        if (closeSource) {
            try {
                source.close()
            } finally {
                try {
                    reservation.close()
                } finally {
                    onClosed(this)
                }
            }
        }
    }

    private fun isClosed(): Boolean = synchronized(lock) { closed }

    suspend fun collect(
        source: RuntimeDropItemSource,
        singleUse: RuntimeDroppedItem,
        maxBytes: Long,
        collector: suspend (ByteArray) -> Unit,
    ): KadreResult<Unit> {
        val admission = synchronized(lock) {
            when {
                closed -> DropReadAdmission.Closed
                reading -> DropReadAdmission.AlreadyInUse
                maxBytes <= 0L -> DropReadAdmission.InvalidMaximum
                source.descriptor.sizeBytes?.let { it > maxBytes } == true -> DropReadAdmission.KnownLimitExceeded
                source.readMode == DropItemReadMode.SingleUse && !singleUse.consume() -> DropReadAdmission.ItemConsumed
                else -> {
                    reading = true
                    DropReadAdmission.Admitted
                }
            }
        }
        when (admission) {
            DropReadAdmission.Closed -> return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.DropTransfer))
            DropReadAdmission.AlreadyInUse -> return KadreResult.Failure(
                KadreFailure.AlreadyInUse(KadreResourceKind.DropTransfer),
            )

            DropReadAdmission.InvalidMaximum -> return KadreResult.Failure(KadreFailure.InvalidRequest("maxBytes"))
            DropReadAdmission.KnownLimitExceeded -> return KadreResult.Failure(
                KadreFailure.ResourceLimitExceeded(KadreResourceKind.DropItem, maxBytes),
            )

            DropReadAdmission.ItemConsumed -> return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.DropItem))
            DropReadAdmission.Admitted -> Unit
        }
        var delivered = 0L
        return try {
            val sourceResult = source.source.collectBytes(resources.maxDropChunkBytes) { bytes ->
                if (isClosed()) throw DropTransferClosed
                if (bytes.size > resources.maxDropChunkBytes) throw DropChunkLimitExceeded
                val next = try {
                    Math.addExact(delivered, bytes.size.toLong())
                } catch (_: ArithmeticException) {
                    throw DropReadLimitExceeded
                }
                if (next > maxBytes) throw DropReadLimitExceeded
                delivered = next
                collector(bytes.copyOf())
            }
            if (isClosed()) throw DropTransferClosed
            sourceResult
        } catch (_: DropReadLimitExceeded) {
            KadreResult.Failure(KadreFailure.ResourceLimitExceeded(KadreResourceKind.DropItem, maxBytes))
        } catch (_: DropChunkLimitExceeded) {
            KadreResult.Failure(
                KadreFailure.ResourceLimitExceeded(KadreResourceKind.DropItem, resources.maxDropChunkBytes.toLong()),
            )
        } catch (_: DropTransferClosed) {
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.DropTransfer))
        } finally {
            synchronized(lock) { reading = false }
        }
    }

    private enum class DropReadAdmission {
        Admitted,
        Closed,
        AlreadyInUse,
        InvalidMaximum,
        KnownLimitExceeded,
        ItemConsumed,
    }
}

internal class RuntimeDroppedItem(
    private val transfer: RuntimeDropTransfer,
    private val source: RuntimeDropItemSource,
) : DroppedItem {
    private var consumed = false

    override val descriptor = source.descriptor
    override val readMode = source.readMode

    override suspend fun collectBytes(
        maxBytes: Long,
        collector: suspend (ByteArray) -> Unit,
    ): KadreResult<Unit> = transfer.collect(source, this, maxBytes, collector)

    fun consume(): Boolean {
        if (consumed) return false
        consumed = true
        return true
    }
}

private data object DropReadLimitExceeded : RuntimeException()
private data object DropChunkLimitExceeded : RuntimeException()
private data object DropTransferClosed : RuntimeException()
