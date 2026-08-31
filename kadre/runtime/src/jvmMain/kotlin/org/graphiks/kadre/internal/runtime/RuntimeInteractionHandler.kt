package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.InteractionFailureReason
import org.graphiks.kadre.diagnostics.KadreFailure
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

/** Owns the single synchronous interaction callback that may be installed for one surface. */
@OptIn(DelicateKadreApi::class)
internal class RuntimeInteractionHandler(
    private val deliveryPolicy: WindowDeliveryPolicy,
    private val failureReporter: RuntimeFailureReporter,
    private val sessionFailureHandler: (KadreFailure) -> Unit,
) {
    private val lock = Any()
    private var registration: Registration? = null
    private var nextToken = 0L
    private var nextRequest = 0L

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
        val active = synchronized(lock) { registration } ?: return
        val context = CallbackContext(active, event.stamp, supported, invokeNative)
        try {
            active.handler.onInteraction(context, event)
        } catch (cause: Exception) {
            safeReport(cause)
            safeFailSession(KadreFailure.ApplicationFailure)
        } catch (cause: LinkageError) {
            safeReport(cause)
            safeFailSession(KadreFailure.ApplicationFailure)
        } finally {
            context.invalidate()
            context.outcome?.let(active::publish)
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
        private val tokenValue = synchronized(lock) {
            InteractionToken(nextToken++)
        }
        private var valid = true
        private var consumed = false

        override val token: InteractionToken
            get() = tokenValue

        var outcome: InteractionActionOutcome? = null
            private set

        override fun request(action: InteractionAction): KadreResult<InteractionRequestId> {
            val failure = when {
                !valid -> KadreFailure.InteractionRequired(InteractionFailureReason.Expired)
                consumed -> KadreFailure.InteractionRequired(InteractionFailureReason.Consumed)
                action.kind() !in supported -> KadreFailure.Unsupported(KadreOperation.Interaction)
                else -> null
            }
            if (failure != null) return KadreResult.Failure(failure)

            consumed = true
            val requestId = synchronized(lock) { InteractionRequestId(nextRequest++) }
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
                is KadreResult.Success -> InteractionActionOutcome.Committed(requestId, null, stamp)
                is KadreResult.Failure -> InteractionActionOutcome.Rejected(requestId, nativeResult.reason, stamp)
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
        private val outcomesFlow = MutableSharedFlow<InteractionActionOutcome>(
            replay = 0,
            extraBufferCapacity = deliveryPolicy.discreteEvents.ingressCapacity,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        private var closed = false

        override val outcomes: Flow<InteractionActionOutcome> = outcomesFlow.asSharedFlow()

        override fun close() {
            val shouldClose = synchronized(lock) {
                if (closed) return@synchronized false
                closed = true
                if (registration === this@Registration) registration = null
                true
            }
            if (shouldClose) closeFromOwner()
        }

        fun closeFromOwner() {
            synchronized(lock) { closed = true }
        }

        fun publish(value: InteractionActionOutcome) {
            if (!outcomesFlow.tryEmit(value)) {
                safeReport(IllegalStateException("interaction outcome flow rejected a bounded publication"))
            }
        }
    }

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
