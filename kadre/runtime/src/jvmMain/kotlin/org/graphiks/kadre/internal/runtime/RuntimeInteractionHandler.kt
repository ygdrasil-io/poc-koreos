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
import org.graphiks.kadre.policy.WindowDeliveryPolicy
import org.graphiks.kadre.policy.CollectorOverflowAction
import org.graphiks.kadre.policy.SlowCollectorCancellationException
import org.graphiks.kadre.surface.SurfaceId
import java.util.concurrent.atomic.AtomicLong

/** Owns the single synchronous interaction callback that may be installed for one surface. */
@OptIn(DelicateKadreApi::class)
internal class RuntimeInteractionHandler(
    private val surfaceId: SurfaceId,
    private val advertised: Set<InteractionKind>,
    private val deliveryPolicy: WindowDeliveryPolicy,
    private val eventCollectorGate: RuntimeEventCollectorGate,
    private val failureReporter: RuntimeFailureReporter,
    private val sessionFailureHandler: (KadreFailure) -> Unit,
    private val afterOutcomeSubscriberSnapshot: (() -> Unit)? = null,
) {
    private companion object {
        val nextToken = AtomicLong(0L)
        val activeSurface = ThreadLocal<SurfaceId?>()
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val lock = java.lang.Object()
    private var registration: Registration? = null
    private var nextRequest = 0L
    private var activeCallback: Registration? = null

    @OptIn(DelicateKadreApi::class)
    fun install(handler: InteractionHandler): KadreResult<InteractionRegistration> = synchronized(lock) {
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
        invokeNative: (InteractionAction) -> KadreResult<Unit>,
    ) {
        val active = synchronized(lock) {
            val candidate = registration ?: return
            if (candidate.closed) return
            activeCallback = candidate
            candidate
        }
        val context = CallbackContext(active, event.stamp, supported.intersect(advertised), invokeNative)
        val previousSurface = activeSurface.get()
        activeSurface.set(surfaceId)
        try {
            active.handler.onInteraction(context, event)
        } catch (cause: Exception) {
            safeReport(cause)
            safeFailSession(KadreFailure.ApplicationFailure)
        } catch (cause: LinkageError) {
            safeReport(cause)
            safeFailSession(KadreFailure.ApplicationFailure)
        } finally {
            activeSurface.set(previousSurface)
            context.invalidate()
            context.outcome?.let(active::publish)
            val terminal = synchronized(lock) {
                if (activeCallback === active) {
                    activeCallback = null
                    lock.notifyAll()
                }
                active.terminaliseAfterCallback.takeIf { it }?.let {
                    active.terminaliseLocked() to active.terminalFailure
                }
            }
            terminal?.first?.forEach { it.closeChannel(terminal.second?.let(::KadreException)) }
        }
    }

    fun close() {
        val active = synchronized(lock) {
            registration.also { registration = null }
        }
        active?.closeFromOwner()
    }

    private inner class CallbackContext(
        private val registration: Registration,
        private val stamp: EventStamp,
        private val supported: Set<InteractionKind>,
        private val invokeNative: (InteractionAction) -> KadreResult<Unit>,
    ) : InteractionContext {
        private val tokenValue = InteractionToken(nextToken.getAndIncrement())
        private var valid = true
        private var consumed = false

        override val token: InteractionToken
            get() = tokenValue

        var outcome: InteractionActionOutcome? = null
            private set

        override fun request(action: InteractionAction): KadreResult<InteractionRequestId> {
            val failure = when {
                !valid -> KadreFailure.InteractionRequired(InteractionFailureReason.Expired)
                activeSurface.get() != surfaceId -> KadreFailure.InteractionRequired(InteractionFailureReason.WrongSurface)
                consumed -> KadreFailure.InteractionRequired(InteractionFailureReason.Consumed)
                action.kind() !in supported -> KadreFailure.Unsupported(KadreOperation.Interaction)
                else -> null
            }
            if (failure != null) return KadreResult.Failure(failure)

            val requestId = synchronized(lock) {
                if (registration.closed || this@RuntimeInteractionHandler.registration !== registration) {
                    return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Interaction))
                }
                consumed = true
                InteractionRequestId(nextRequest++)
            }
            val dropOfferId = (action as? InteractionAction.AcceptDrop)?.offerId
            val nativeResult = try {
                invokeNative(action)
            } catch (cause: Exception) {
                safeReport(cause)
                KadreResult.Failure(KadreFailure.ApplicationFailure)
            } catch (cause: LinkageError) {
                safeReport(cause)
                KadreResult.Failure(KadreFailure.ApplicationFailure)
            }
            outcome = when (nativeResult) {
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
            val terminal = synchronized(lock) {
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
                val dispose = synchronized(lock) {
                    subscribers.remove(subscriber)
                    subscriber.deactivate()
                }
                if (dispose) subscriber.closeChannel(null)
                lease.close()
            }
        }

        override fun close() {
            val shouldClose = synchronized(lock) {
                if (closed) return@synchronized false
                closed = true
                if (registration === this@Registration) registration = null
                true
            }
            if (shouldClose) closeFromOwner()
        }

        fun closeFromOwner(failure: KadreFailure? = null) {
            val terminal = synchronized(lock) {
                closed = true
                if (terminalFailure == null && failure != null) terminalFailure = failure
                if (registration === this@Registration) registration = null
                if (activeCallback === this@Registration) {
                    terminaliseAfterCallback = true
                    emptyList<OutcomeSubscriber>() to terminalFailure
                } else {
                    terminaliseLocked() to terminalFailure
                }
            }
            terminal.first.forEach { it.closeChannel(terminal.second?.let(::KadreException)) }
        }

        fun terminaliseLocked(): List<OutcomeSubscriber> {
            check(Thread.holdsLock(lock))
            terminaliseAfterCallback = false
            if (registration === this@Registration) registration = null
            val toClose = subscribers.toList()
            subscribers.clear()
            toClose.forEach(OutcomeSubscriber::deactivate)
            return toClose
        }

        fun publish(value: InteractionActionOutcome) {
            val current = synchronized(lock) { subscribers.toList() }
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
            private val lock = Any()
            private var active = true

            fun offer(value: InteractionActionOutcome): OutcomeOffer {
                return synchronized(lock) {
                    if (!active) return@synchronized OutcomeOffer.Accepted
                    if (channel.trySend(value).isSuccess) return@synchronized OutcomeOffer.Accepted
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

            fun deactivate(): Boolean = synchronized(lock) {
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
        } catch (_: LinkageError) {
            // Failure reporting cannot re-cross a native callback boundary.
        }
    }

    private fun safeFailSession(failure: KadreFailure) {
        try {
            sessionFailureHandler(failure)
        } catch (cause: Exception) {
            safeReport(cause)
        } catch (cause: LinkageError) {
            safeReport(cause)
        }
    }
}
