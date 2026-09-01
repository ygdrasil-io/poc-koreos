package org.graphiks.kadre.interaction

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DropOfferId
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.TouchId
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.window.FullscreenMode
import org.graphiks.kadre.window.ResizeEdge
import org.graphiks.kadre.window.WindowRequestId
import org.graphiks.kadre.window.WindowSpec
import kotlin.time.Duration

public interface InteractionRegistration : AutoCloseable {
    public val outcomes: Flow<InteractionActionOutcome>
    override fun close()
}

public interface ArmedInteraction : AutoCloseable {
    public val state: StateFlow<ArmedInteractionState>
    override fun close()
    public suspend fun await(): InteractionActionOutcome
}

@DelicateKadreApi
public fun interface InteractionHandler {
    public fun onInteraction(context: InteractionContext, event: InteractionEvent)
}

public interface InteractionContext {
    public val token: InteractionToken
    public fun request(action: InteractionAction): KadreResult<InteractionRequestId>
}

public sealed interface InteractionAction {
    public data class EnterFullscreen(public val mode: FullscreenMode) : InteractionAction
    public data object ExitFullscreen : InteractionAction
    public data class LockPointer(public val mode: PointerCaptureMode) : InteractionAction
    public data object UnlockPointer : InteractionAction
    public data object BeginWindowMove : InteractionAction
    public data class BeginWindowResize(public val edge: ResizeEdge) : InteractionAction
    public data class AcceptDrop(public val offerId: DropOfferId) : InteractionAction
    public data class OpenWindow(public val spec: WindowSpec = WindowSpec()) : InteractionAction
}

public sealed interface InteractionActionOutcome {
    public val requestId: InteractionRequestId
    public val stamp: EventStamp

    public data class Committed(
        override val requestId: InteractionRequestId,
        public val windowRequestId: WindowRequestId?,
        override val stamp: EventStamp,
    ) : InteractionActionOutcome

    public data class Rejected(
        override val requestId: InteractionRequestId,
        public val failure: KadreFailure,
        override val stamp: EventStamp,
    ) : InteractionActionOutcome

    public data class Expired(
        override val requestId: InteractionRequestId,
        override val stamp: EventStamp,
    ) : InteractionActionOutcome

    public data class OwnerClosed(
        override val requestId: InteractionRequestId,
        override val stamp: EventStamp,
    ) : InteractionActionOutcome
}

public sealed interface InteractionTrigger {
    public data object NextEligibleActivation : InteractionTrigger
    public data class PointerPressed(public val button: PointerButton?) : InteractionTrigger
    public data class KeyPressed(public val physicalKey: PhysicalKey?) : InteractionTrigger
    public data object TouchStarted : InteractionTrigger
}

public enum class InteractionTriggerKind { AnyActivation, PointerPress, KeyPress, TouchStart }

public data class ArmedInteractionConstraints(
    public val actions: Set<InteractionKind>,
    public val triggers: Set<InteractionTriggerKind>,
) {
    init {
        require(actions.isNotEmpty()) { "actions must not be empty" }
        require(triggers.isNotEmpty()) { "triggers must not be empty" }
    }
}

public data class InteractionArmOptions(
    public val expiresAfter: Duration,
    public val trigger: InteractionTrigger = InteractionTrigger.NextEligibleActivation,
) {
    init {
        require(expiresAfter.isFinite() && expiresAfter.isPositive()) {
            "expiresAfter must be finite and positive"
        }
    }
}

public sealed interface ArmedInteractionState {
    public data object Armed : ArmedInteractionState
    public data class Terminated(public val outcome: InteractionActionOutcome) : ArmedInteractionState
}

public sealed interface InteractionEvent {
    public val stamp: EventStamp

    public data class PointerPressed(
        public val button: PointerButton,
        public val position: LogicalPoint,
        override val stamp: EventStamp,
    ) : InteractionEvent

    public data class KeyPressed(
        public val physicalKey: PhysicalKey,
        override val stamp: EventStamp,
    ) : InteractionEvent

    public data class TouchStarted(
        public val touchId: TouchId,
        public val position: LogicalPoint,
        override val stamp: EventStamp,
    ) : InteractionEvent
}
