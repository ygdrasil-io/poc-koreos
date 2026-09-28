package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.InteractionFailureReason
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.interaction.InteractionAction
import org.graphiks.kadre.interaction.InteractionActionOutcome
import org.graphiks.kadre.interaction.InteractionContext
import org.graphiks.kadre.interaction.InteractionEvent
import org.graphiks.kadre.interaction.InteractionHandler
import org.graphiks.kadre.interaction.InteractionKind
import org.graphiks.kadre.interaction.InteractionRegistration
import org.graphiks.kadre.interaction.InteractionRequestId
import org.graphiks.kadre.interaction.InteractionToken
import org.graphiks.kadre.input.DropOfferId
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.WindowDeliveryPolicy
import org.graphiks.kadre.policy.CollectorOverflowAction
import org.graphiks.kadre.policy.SlowCollectorCancellationException
import org.graphiks.kadre.surface.SurfaceId

/**
 * Owns the single synchronous interaction callback that may be installed for one surface.
 *
 * The handler is platform-neutral: the call frame ([InteractionCallFrame]) and the native outcome
 * ([NativeInteractionOutcome]) are its only platform seams. A `Now` outcome keeps the pre-lift
 * behaviour — published in the dispatch `finally` — while a `Deferred` outcome registers a
 * pending request that occupies [maxPendingInteractionRequests] until its terminal callback
 * completes it, or until terminalisation abandons it with `Closed(KadreResourceKind.Interaction)`.
 */
@OptIn(DelicateKadreApi::class)
internal class RuntimeInteractionHandler(
    private val surfaceId: SurfaceId,
    private val advertised: Set<InteractionKind>,
    private val deliveryPolicy: WindowDeliveryPolicy,
    private val eventCollectorGate: RuntimeEventCollectorGate,
    private val failureReporter: RuntimeFailureReporter,
    private val sessionFailureHandler: (KadreFailure) -> Unit,
    private val maxPendingInteractionRequests: Int = KadrePolicies.Default.resources.maxPendingInteractionRequests,
    private val afterOutcomeSubscriberSnapshot: (() -> Unit)? = null,
) {
    private companion object {
        /**
         * The interaction token counter is global to the process, as the JVM `AtomicLong` of the
         * pre-lift handler was: concurrent dispatches on different surfaces do not serialise each
         * other, so the increment runs under its own [RuntimeLock] instead of a plain var. The
         * lock only has to exclude the increment — never held across a callback.
         *
         * `kotlin.concurrent.atomics.AtomicLong` was probed on Kotlin 2.4.0 and is still
         * `ExperimentalAtomicApi`, so the counter stays a plain var under [RuntimeLock.withLock]
         * until that API stabilises.
         */
        val nextTokenLock = RuntimeLock()
        var nextToken = 0L
    }

    private val lock = RuntimeLock()
    private var registration: Registration? = null
    private var nextRequest = 0L
    private var activeCallback: Registration? = null
    private val pendingRequests = mutableMapOf<InteractionRequestId, PendingInteractionRequest>()
    private val callFrame = InteractionCallFrame()

    init {
        require(maxPendingInteractionRequests > 0) { "maxPendingInteractionRequests must be positive" }
    }

    @OptIn(DelicateKadreApi::class)
    fun install(handler: InteractionHandler): KadreResult<InteractionRegistration> = lock.withLock {
        if (registration != null) {
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Interaction))
        } else {
            val installed = Registration(handler)
            registration = installed
            KadreResult.Success(installed)
        }
    }

    fun dispatch(
        event: InteractionEvent,
        supported: Set<InteractionKind>,
        invokeNative: (InteractionAction) -> NativeInteractionOutcome,
    ) {
        val active = lock.withLock {
            val candidate = registration ?: return
            if (candidate.closed) return
            activeCallback = candidate
            candidate
        }
        val context = CallbackContext(active, event.stamp, supported.intersect(advertised), invokeNative)
        val previousSurface = callFrame.current()
        callFrame.set(surfaceId)
        try {
            active.handler.onInteraction(context, event)
        } catch (cause: Exception) {
            safeReport(cause)
            safeFailSession(KadreFailure.ApplicationFailure)
        } catch (cause: Throwable) {
            if (cause.isLinkageFailure()) {
                safeReport(cause)
                safeFailSession(KadreFailure.ApplicationFailure)
            } else {
                throw cause
            }
        } finally {
            if (previousSurface == null) callFrame.clear() else callFrame.set(previousSurface)
            context.invalidate()
            context.outcome?.let(active::publish)
            val terminal = lock.withLock {
                if (activeCallback === active) {
                    activeCallback = null
                }
                active.terminaliseAfterCallback.takeIf { it }?.let {
                    active.terminaliseLocked() to active.terminalFailure
                }
            }
            terminal?.first?.forEach { it.closeChannel(terminal.second?.let(::KadreException)) }
        }
    }

    fun close() {
        val active = lock.withLock {
            registration.also { registration = null }
        }
        active?.closeFromOwner()
    }

    /**
     * Terminalises one deferred request: publishes its `Committed`/`Rejected` outcome through the
     * registration that made it, releases its pending budget slot and notifies the backend's
     * `complete` callback exactly once. Unknown request IDs — or IDs already abandoned by
     * terminalisation — are ignored, because a terminal callback may race an abandonment.
     *
     * When [committed] is `false`, [failure] carries the refusal the backend observed; the
     * fallback is [KadreFailure.ApplicationFailure] because an outcome needs a closed failure.
     */
    fun completePending(requestId: InteractionRequestId, committed: Boolean, failure: KadreFailure?) {
        val pending = lock.withLock { pendingRequests.remove(requestId) } ?: return
        val outcome = if (committed) {
            InteractionActionOutcome.Committed(requestId, null, pending.stamp, pending.dropOfferId)
        } else {
            InteractionActionOutcome.Rejected(
                requestId,
                failure ?: KadreFailure.ApplicationFailure,
                pending.stamp,
                pending.dropOfferId,
            )
        }
        pending.registration.publish(outcome)
        notifyComplete(pending, committed, failure)
    }

    /**
     * Terminalises every pending deferred request with [failure]: each publishes
     * `Rejected(failure)` — its budget slot released with it — and each backend `complete`
     * callback fires once with `false`. Surface terminalisation abandons with
     * `Closed(KadreResourceKind.Interaction)`.
     */
    fun abandonPendingRequests(failure: KadreFailure) {
        var abandoned: List<PendingInteractionRequest> = emptyList()
        lock.withLock {
            abandoned = pendingRequests.values.toList().also { pendingRequests.clear() }
            abandoned.forEach { pending ->
                pending.registration.publish(
                    InteractionActionOutcome.Rejected(pending.requestId, failure, pending.stamp, pending.dropOfferId),
                )
            }
        }
        abandoned.forEach { notifyComplete(it, committed = false, failure) }
    }

    private fun notifyComplete(
        pending: PendingInteractionRequest,
        committed: Boolean,
        failure: KadreFailure?,
    ) {
        try {
            pending.complete(committed, failure)
        } catch (cause: Exception) {
            safeReport(cause)
        } catch (cause: Throwable) {
            if (cause.isLinkageFailure()) {
                safeReport(cause)
            } else {
                throw cause
            }
        }
    }

    private inner class CallbackContext(
        private val registration: Registration,
        private val stamp: EventStamp,
        private val supported: Set<InteractionKind>,
        private val invokeNative: (InteractionAction) -> NativeInteractionOutcome,
    ) : InteractionContext {
        private val tokenValue = InteractionToken(nextTokenLock.withLock { nextToken++ })
        private var valid = true
        private var consumed = false

        override val token: InteractionToken
            get() = tokenValue

        var outcome: InteractionActionOutcome? = null
            private set

        override fun request(action: InteractionAction): KadreResult<InteractionRequestId> {
            val failure = when {
                !valid -> KadreFailure.InteractionRequired(InteractionFailureReason.Expired)
                callFrame.current() != surfaceId -> KadreFailure.InteractionRequired(InteractionFailureReason.WrongSurface)
                consumed -> KadreFailure.InteractionRequired(InteractionFailureReason.Consumed)
                action.kind() !in supported -> KadreFailure.Unsupported(KadreOperation.Interaction)
                else -> null
            }
            if (failure != null) return KadreResult.Failure(failure)

            val requestId = lock.withLock {
                if (registration.closed || this@RuntimeInteractionHandler.registration !== registration) {
                    return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Interaction))
                }
                consumed = true
                InteractionRequestId(nextRequest++)
            }
            val dropOfferId = (action as? InteractionAction.AcceptDrop)?.offerId
            val nativeOutcome = try {
                invokeNative(action)
            } catch (cause: Exception) {
                safeReport(cause)
                NativeInteractionOutcome.Now(KadreResult.Failure(KadreFailure.ApplicationFailure))
            } catch (cause: Throwable) {
                if (cause.isLinkageFailure()) {
                    safeReport(cause)
                    NativeInteractionOutcome.Now(KadreResult.Failure(KadreFailure.ApplicationFailure))
                } else {
                    throw cause
                }
            }
            when (nativeOutcome) {
                is NativeInteractionOutcome.Now -> {
                    outcome = when (val nativeResult = nativeOutcome.result) {
                        is KadreResult.Success -> InteractionActionOutcome.Committed(
                            requestId,
                            null,
                            stamp,
                            dropOfferId,
                        )

                        is KadreResult.Failure -> InteractionActionOutcome.Rejected(
                            requestId,
                            nativeResult.reason,
                            stamp,
                            dropOfferId,
                        )
                    }
                }

                is NativeInteractionOutcome.Deferred -> {
                    // The pending budget exists only at this registration moment: the request was
                    // already consumed, so the refusal happens after the native primitive was
                    // emitted and the backend observes the refusal through this failure.
                    val admitted = lock.withLock {
                        if (pendingRequests.size >= maxPendingInteractionRequests) {
                            false
                        } else {
                            pendingRequests[requestId] = PendingInteractionRequest(
                                requestId = requestId,
                                registration = registration,
                                stamp = stamp,
                                dropOfferId = dropOfferId,
                                complete = nativeOutcome.complete,
                            )
                            true
                        }
                    }
                    if (!admitted) {
                        return KadreResult.Failure(
                            KadreFailure.ResourceLimitExceeded(
                                KadreResourceKind.Interaction,
                                maxPendingInteractionRequests.toLong(),
                            ),
                        )
                    }
                }
            }
            return KadreResult.Success(requestId)
        }

        fun invalidate() {
            valid = false
        }
    }

    private inner class Registration(
        val handler: InteractionHandler,
    ) : InteractionRegistration {
        internal var closed = false
        var terminaliseAfterCallback = false
        var terminalFailure: KadreFailure? = null
        private val subscribers = linkedSetOf<OutcomeSubscriber>()

        override val outcomes: Flow<InteractionActionOutcome> = flow {
            val lease = when (val admission = eventCollectorGate.tryAcquire()) {
                is KadreResult.Success -> admission.value
                is KadreResult.Failure -> throw KadreException(admission.reason)
            }
            val subscriber = OutcomeSubscriber()
            val terminal = lock.withLock {
                when {
                    terminalFailure != null -> OutcomeSubscription.Failed(terminalFailure!!)
                    closed -> OutcomeSubscription.Closed
                    else -> {
                    subscribers.add(subscriber)
                        OutcomeSubscription.Accepted
                    }
                }
            }
            when (terminal) {
                OutcomeSubscription.Accepted -> Unit
                OutcomeSubscription.Closed -> {
                    lease.close()
                    return@flow
                }

                is OutcomeSubscription.Failed -> {
                    lease.close()
                    throw KadreException(terminal.failure)
                }
            }
            try {
                for (outcome in subscriber.channel) emit(outcome)
            } finally {
                val dispose = lock.withLock {
                    subscribers.remove(subscriber)
                    subscriber.deactivate()
                }
                if (dispose) subscriber.closeChannel(null)
                lease.close()
            }
        }

        override fun close() {
            val shouldClose = lock.withLock {
                if (closed) return@withLock false
                closed = true
                if (registration === this@Registration) registration = null
                true
            }
            if (shouldClose) closeFromOwner()
        }

        fun closeFromOwner(failure: KadreFailure? = null) {
            var abandoned: List<PendingInteractionRequest> = emptyList()
            val terminal = lock.withLock {
                closed = true
                if (terminalFailure == null && failure != null) terminalFailure = failure
                if (registration === this@Registration) registration = null
                // The sweep is atomic with `closed`: a pending registered after this point is
                // impossible. The abandoned outcomes publish before terminalisation so live
                // subscribers still observe them.
                abandoned = pendingRequests.values.toList().also { pendingRequests.clear() }
                abandoned.forEach { pending ->
                    pending.registration.publish(
                        InteractionActionOutcome.Rejected(
                            pending.requestId,
                            terminalFailure ?: KadreFailure.Closed(KadreResourceKind.Interaction),
                            pending.stamp,
                            pending.dropOfferId,
                        ),
                    )
                }
                if (activeCallback === this@Registration) {
                    terminaliseAfterCallback = true
                    emptyList<OutcomeSubscriber>() to terminalFailure
                } else {
                    terminaliseLocked() to terminalFailure
                }
            }
            abandoned.forEach {
                notifyComplete(it, committed = false, terminal.second ?: KadreFailure.Closed(KadreResourceKind.Interaction))
            }
            terminal.first.forEach { it.closeChannel(terminal.second?.let(::KadreException)) }
        }

        fun terminaliseLocked(): List<OutcomeSubscriber> {
            check(lock.isHeldByCurrentThread())
            terminaliseAfterCallback = false
            if (registration === this@Registration) registration = null
            val toClose = subscribers.toList()
            subscribers.clear()
            toClose.forEach(OutcomeSubscriber::deactivate)
            return toClose
        }

        fun publish(value: InteractionActionOutcome) {
            val current = lock.withLock { subscribers.toList() }
            afterOutcomeSubscriberSnapshot?.invoke()
            var closeSource = false
            var failSession = false
            current.forEach { subscriber ->
                when (subscriber.offer(value)) {
                    OutcomeOffer.Accepted -> Unit
                    OutcomeOffer.CancelSlow -> subscriber.closeChannel(
                        SlowCollectorCancellationException("interaction outcome collector exceeded capacity"),
                    )
                    OutcomeOffer.CloseSource -> closeSource = true
                    OutcomeOffer.FailSession -> failSession = true
                }
            }
            if (closeSource || failSession) {
                val failure = KadreFailure.SourceOverflow(KadreResourceKind.Interaction)
                closeFromOwner(failure)
                if (failSession) safeFailSession(failure)
            }
        }

        inner class OutcomeSubscriber {
            val channel = Channel<InteractionActionOutcome>(deliveryPolicy.discreteEvents.collectorCapacity)
            private val lock = RuntimeLock()
            private var active = true

            fun offer(value: InteractionActionOutcome): OutcomeOffer {
                return lock.withLock {
                    if (!active) return@withLock OutcomeOffer.Accepted
                    if (channel.trySend(value).isSuccess) return@withLock OutcomeOffer.Accepted
                    when (deliveryPolicy.discreteEvents.collectorOverflow) {
                        CollectorOverflowAction.CancelSlowCollector -> {
                            active = false
                            OutcomeOffer.CancelSlow
                        }

                        CollectorOverflowAction.CloseSource -> OutcomeOffer.CloseSource
                        CollectorOverflowAction.FailSession -> OutcomeOffer.FailSession
                    }
                }
            }

            fun deactivate(): Boolean = lock.withLock {
                if (!active) false else {
                    active = false
                    true
                }
            }

            fun closeChannel(cause: Throwable?) {
                channel.close(cause)
            }
        }
    }

    private inner class PendingInteractionRequest(
        val requestId: InteractionRequestId,
        val registration: Registration,
        val stamp: EventStamp,
        val dropOfferId: DropOfferId?,
        val complete: (committed: Boolean, failure: KadreFailure?) -> Unit,
    )

    private sealed interface OutcomeSubscription {
        data object Accepted : OutcomeSubscription
        data object Closed : OutcomeSubscription
        data class Failed(val failure: KadreFailure) : OutcomeSubscription
    }

    private enum class OutcomeOffer { Accepted, CancelSlow, CloseSource, FailSession }

    private fun InteractionAction.kind(): InteractionKind = when (this) {
        is InteractionAction.EnterFullscreen -> InteractionKind.EnterFullscreen
        InteractionAction.ExitFullscreen -> InteractionKind.ExitFullscreen
        is InteractionAction.LockPointer -> InteractionKind.LockPointer
        InteractionAction.UnlockPointer -> InteractionKind.UnlockPointer
        InteractionAction.BeginWindowMove -> InteractionKind.BeginWindowMove
        is InteractionAction.BeginWindowResize -> InteractionKind.BeginWindowResize
        is InteractionAction.AcceptDrop -> InteractionKind.AcceptDrop
        is InteractionAction.OpenWindow -> InteractionKind.OpenWindow
    }

    private fun safeReport(cause: Throwable) {
        try {
            failureReporter.report(cause)
        } catch (_: Exception) {
            // Failure reporting cannot re-cross a native callback boundary.
        } catch (throwable: Throwable) {
            if (throwable.isLinkageFailure()) {
                // Failure reporting cannot re-cross a native callback boundary.
            } else {
                throw throwable
            }
        }
    }

    private fun safeFailSession(failure: KadreFailure) {
        try {
            sessionFailureHandler(failure)
        } catch (cause: Exception) {
            safeReport(cause)
        } catch (cause: Throwable) {
            if (cause.isLinkageFailure()) {
                safeReport(cause)
            } else {
                throw cause
            }
        }
    }
}
