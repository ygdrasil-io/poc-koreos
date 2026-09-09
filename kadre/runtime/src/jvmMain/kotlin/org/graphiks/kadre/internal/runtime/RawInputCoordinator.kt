package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.InteractionFailureReason
import org.graphiks.kadre.diagnostics.KadreDiagnostic
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.diagnostics.KadreSubsystem
import org.graphiks.kadre.input.RawInputAccess
import org.graphiks.kadre.input.RawInputEvent
import org.graphiks.kadre.input.RawInputState
import org.graphiks.kadre.policy.RawInputDeliveryPolicy
import org.graphiks.kadre.policy.RawInputOverflowAction
import org.graphiks.kadre.surface.SurfaceId

@OptIn(DelicateKadreApi::class)
internal class RawInputCoordinator(
    private val port: RawInputPort,
    private val rawPolicy: RawInputDeliveryPolicy,
    private val maxConcurrentRawInputAccesses: Int,
    private val scope: CoroutineScope,
    private val diagnostics: (KadreDiagnostic) -> Unit = {},
    private val onAvailability: (FeatureAvailability) -> Unit = {},
    private val diagnosticStampSource: () -> EventStamp,
    private val collectorAllocator: RuntimeEventCollectorAllocator,
    private val maxCollectorsPerFlow: Int,
) : AutoCloseable {
    init {
        require(maxConcurrentRawInputAccesses > 0) { "maxConcurrentRawInputAccesses must be positive" }
        require(maxCollectorsPerFlow > 0) { "maxCollectorsPerFlow must be positive" }
    }

    private val lock = Any()
    private val accesses = linkedMapOf<RuntimeRawInputAccess, SurfaceId?>()
    private val closedOwners = mutableSetOf<SurfaceId>()
    private var reserved = 0
    private var closed = false

    /**
     * Reserves the session budget before asking the backend to evaluate permission or create a
     * native registration. Cancellation before handoff releases only this caller's reservation.
     */
    suspend fun requestAccess(owner: SurfaceId? = null): KadreResult<RawInputAccess> {
        val admission = synchronized(lock) {
            when {
                closed -> RawInputAdmission.Closed
                owner != null && owner in closedOwners -> RawInputAdmission.Closed
                reserved >= maxConcurrentRawInputAccesses -> RawInputAdmission.Limit
                else -> {
                    reserved += 1
                    RawInputAdmission.Admitted
                }
            }
        }
        when (admission) {
            RawInputAdmission.Closed -> return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.InputSource))
            RawInputAdmission.Limit -> {
                report(
                    KadreDiagnostic.ResourceLimitHit(
                        KadreResourceKind.RawInputAccess,
                        maxConcurrentRawInputAccesses.toLong(),
                        KadreSubsystem.Input,
                        diagnosticStampSource(),
                    ),
                )
                return KadreResult.Failure(
                    KadreFailure.ResourceLimitExceeded(
                        KadreResourceKind.RawInputAccess,
                        maxConcurrentRawInputAccesses.toLong(),
                    ),
                )
            }

            RawInputAdmission.Admitted -> Unit
        }

        var handedOff = false
        var unownedLease: RawInputPortLease? = null
        try {
            val lease = when (val result = port.requestAccess()) {
                is KadreResult.Failure -> return result
                is KadreResult.Success -> result.value
            }
            unownedLease = lease
            currentCoroutineContext().ensureActive()
            val access = RuntimeRawInputAccess(
                lease = lease,
                scope = scope,
                policy = rawPolicy,
                eventCollectorGate = collectorAllocator.newGate(maxCollectorsPerFlow),
                diagnostics = ::report,
                onAvailability = ::publishAvailability,
                onClosed = ::release,
            )
            val installed = synchronized(lock) {
                if (closed || (owner != null && owner in closedOwners)) {
                    false
                } else {
                    check(accesses.put(access, owner) == null)
                    true
                }
            }
            if (!installed) {
                return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.InputSource))
            }
            handedOff = true
            unownedLease = null
            access.start()
            return KadreResult.Success(access)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            if (!handedOff) {
                try {
                    unownedLease?.close()
                } finally {
                    releaseReservation()
                }
            }
        }
    }

    override fun close() {
        val descendants = synchronized(lock) {
            if (closed) return
            closed = true
            accesses.keys.toList()
        }
        descendants.forEach(RuntimeRawInputAccess::close)
        try {
            port.close()
        } finally {
            synchronized(lock) {
                check(accesses.isEmpty()) { "raw-input coordinator retained an access after close" }
                check(reserved == 0) { "raw-input coordinator retained a reservation after close" }
            }
        }
    }

    /** Closes the raw-input accesses owned by one detached surface without affecting its siblings. */
    fun closeOwner(owner: SurfaceId) {
        val descendants = synchronized(lock) {
            closedOwners += owner
            accesses.filterValues { it == owner }.keys.toList()
        }
        descendants.forEach(RuntimeRawInputAccess::close)
    }

    private fun release(access: RuntimeRawInputAccess) {
        val released = synchronized(lock) {
            if (!accesses.containsKey(access)) {
                false
            } else {
                accesses.remove(access)
                check(reserved > 0) { "raw-input reservation underflow" }
                reserved -= 1
                true
            }
        }
        check(released) { "raw-input access was released without ownership" }
    }

    private fun releaseReservation() = synchronized(lock) {
        check(reserved > 0) { "raw-input reservation underflow" }
        reserved -= 1
    }

    private fun report(diagnostic: KadreDiagnostic) {
        runCatching { diagnostics(diagnostic) }
    }

    private fun publishAvailability(availability: FeatureAvailability) {
        runCatching { onAvailability(availability) }
    }

    private enum class RawInputAdmission { Admitted, Limit, Closed }
}

@DelicateKadreApi
private class RuntimeRawInputAccess(
    private val lease: RawInputPortLease,
    private val scope: CoroutineScope,
    private val policy: RawInputDeliveryPolicy,
    private val eventCollectorGate: RuntimeEventCollectorGate,
    private val diagnostics: (KadreDiagnostic) -> Unit,
    private val onAvailability: (FeatureAvailability) -> Unit,
    private val onClosed: (RuntimeRawInputAccess) -> Unit,
) : RawInputAccess {
    private val lock = Any()
    private val mutableState = MutableStateFlow<RawInputState>(RawInputState.Active)
    private val subscribers = linkedSetOf<RawInputSubscriber>()
    private var terminal: RawInputTerminal? = null
    private var collection: Job? = null

    override val state: StateFlow<RawInputState> = mutableState.asStateFlow()
    override val events: Flow<RawInputEvent> = flow {
        val subscriber = RawInputSubscriber(policy)
        when (val registration = register(subscriber)) {
            RawInputSubscription.Closed -> return@flow
            is RawInputSubscription.Failed -> throw KadreException(registration.failure)
            RawInputSubscription.Registered -> Unit
        }
        try {
            while (true) {
                val event = subscriber.next() ?: break
                emit(event)
            }
        } finally {
            unregister(subscriber)
        }
    }.withEventCollectorAdmission(eventCollectorGate)

    fun start() {
        check(collection == null) { "raw-input access was started twice" }
        collection = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            lease.events.collect(::accept)
        }
    }

    override fun close() {
        terminate(failure = null)
    }

    private fun accept(event: RawInputPortLeaseEvent) {
        when (event) {
            is RawInputPortLeaseEvent.Input -> publish(event.event)
            is RawInputPortLeaseEvent.Availability -> acceptAvailability(event.availability)
            is RawInputPortLeaseEvent.Terminal -> terminate(event.failure)
        }
    }

    private fun publish(event: RawInputEvent) {
        val targets = synchronized(lock) {
            if (terminal != null || mutableState.value != RawInputState.Active) emptyList() else subscribers.toList()
        }
        var shouldClose = false
        targets.forEach { subscriber ->
            when (subscriber.offer(event)) {
                RawInputSubscriberOffer.Accepted -> Unit
                RawInputSubscriberOffer.Dropped -> reportLoss(event)
                RawInputSubscriberOffer.CloseAccess -> shouldClose = true
            }
        }
        if (shouldClose) terminate(KadreFailure.SourceOverflow(KadreResourceKind.RawInputAccess))
    }

    private fun acceptAvailability(availability: FeatureAvailability) {
        onAvailability(availability)
        if (availability == FeatureAvailability.Unsupported) {
            terminate(KadreFailure.Unsupported(KadreOperation.RawInputAccess))
            return
        }
        val next = availability.toRawInputState() ?: return
        synchronized(lock) {
            if (terminal != null || mutableState.value == next) return
            mutableState.value = next
        }
    }

    private fun terminate(failure: KadreFailure?) {
        val cleanup = synchronized(lock) {
            if (terminal != null) return
            terminal = failure?.let(RawInputTerminal::Failed) ?: RawInputTerminal.Closed
            mutableState.value = RawInputState.Closed
            val activeSubscribers = subscribers.toList()
            subscribers.clear()
            RawInputCleanup(
                subscribers = activeSubscribers,
                collection = collection,
                lease = lease,
                failure = failure,
            )
        }
        cleanup.close()
        onClosed(this)
    }

    private fun register(subscriber: RawInputSubscriber): RawInputSubscription = synchronized(lock) {
        when (val terminalSnapshot = terminal) {
            null -> {
                check(subscribers.add(subscriber))
                RawInputSubscription.Registered
            }

            RawInputTerminal.Closed -> RawInputSubscription.Closed
            is RawInputTerminal.Failed -> RawInputSubscription.Failed(terminalSnapshot.failure)
        }
    }

    private fun unregister(subscriber: RawInputSubscriber) {
        synchronized(lock) { subscribers.remove(subscriber) }
        subscriber.dispose()
    }

    private fun reportLoss(event: RawInputEvent) {
        runCatching {
            diagnostics(
                KadreDiagnostic.EventLoss(
                    count = 1,
                    resource = KadreResourceKind.RawInputAccess,
                    subsystem = KadreSubsystem.Input,
                    stamp = event.stamp,
                ),
            )
        }
    }
}

private data class RawInputCleanup(
    val subscribers: List<RawInputSubscriber>,
    val collection: Job?,
    val lease: RawInputPortLease,
    val failure: KadreFailure?,
) {
    fun close() {
        subscribers.forEach { subscriber -> subscriber.terminate(failure) }
        collection?.cancel()
        lease.close()
    }
}

private sealed interface RawInputSubscription {
    data object Registered : RawInputSubscription
    data object Closed : RawInputSubscription
    data class Failed(val failure: KadreFailure) : RawInputSubscription
}

private sealed interface RawInputTerminal {
    data object Closed : RawInputTerminal
    data class Failed(val failure: KadreFailure) : RawInputTerminal
}

private sealed interface RawInputSubscriberOffer {
    data object Accepted : RawInputSubscriberOffer
    data object Dropped : RawInputSubscriberOffer
    data object CloseAccess : RawInputSubscriberOffer
}

private class RawInputSubscriber(
    private val policy: RawInputDeliveryPolicy,
) {
    private val lock = Any()
    private val signal = Channel<Unit>(capacity = 1)
    private val entries = ArrayDeque<RawInputEvent>()
    private var terminal: RawInputTerminal? = null

    fun offer(event: RawInputEvent): RawInputSubscriberOffer {
        val outcome = synchronized(lock) {
            if (terminal != null) return RawInputSubscriberOffer.Accepted
            if (entries.size < policy.capacity) {
                entries += event
                RawInputSubscriberOffer.Accepted
            } else {
                when (policy.onOverflow) {
                    RawInputOverflowAction.DropOldestAndReport -> {
                        entries.removeFirst()
                        entries += event
                        RawInputSubscriberOffer.Dropped
                    }

                    RawInputOverflowAction.DropLatestAndReport -> RawInputSubscriberOffer.Dropped
                    RawInputOverflowAction.CloseAccess -> RawInputSubscriberOffer.CloseAccess
                }
            }
        }
        signal.trySend(Unit)
        return outcome
    }

    suspend fun next(): RawInputEvent? {
        while (true) {
            val terminalSnapshot = synchronized(lock) {
                entries.removeFirstOrNull()?.let { return it }
                terminal
            }
            when (terminalSnapshot) {
                RawInputTerminal.Closed -> return null
                is RawInputTerminal.Failed -> throw KadreException(terminalSnapshot.failure)
                null -> signal.receive()
            }
        }
    }

    fun terminate(failure: KadreFailure?) {
        synchronized(lock) {
            if (terminal != null) return
            entries.clear()
            terminal = failure?.let(RawInputTerminal::Failed) ?: RawInputTerminal.Closed
        }
        signal.trySend(Unit)
    }

    fun dispose() {
        synchronized(lock) { entries.clear() }
        signal.cancel()
    }
}

private fun FeatureAvailability.toRawInputState(): RawInputState? = when (this) {
    FeatureAvailability.Available -> RawInputState.Active
    FeatureAvailability.Unsupported -> null
    is FeatureAvailability.Unavailable -> RawInputState.Suspended(failure)
    is FeatureAvailability.RequiresPermission -> RawInputState.Suspended(KadreFailure.PermissionDenied(permission))
    is FeatureAvailability.RequiresInteraction -> RawInputState.Suspended(
        KadreFailure.InteractionRequired(InteractionFailureReason.Missing),
    )
}
