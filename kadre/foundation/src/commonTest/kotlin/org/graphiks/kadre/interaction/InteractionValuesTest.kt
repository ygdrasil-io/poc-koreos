package org.graphiks.kadre.interaction

import kotlinx.coroutines.flow.MutableStateFlow
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DropItemDescriptor
import org.graphiks.kadre.input.DropItemKind
import org.graphiks.kadre.input.DropOffer
import org.graphiks.kadre.input.DropOfferId
import org.graphiks.kadre.input.DropOfferState
import org.graphiks.kadre.input.DropTransfer
import org.graphiks.kadre.surface.LogicalPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class InteractionValuesTest {
    @Test
    fun armedConstraintsMustHaveActionsAndTriggers() {
        assertFailsWith<IllegalArgumentException> {
            ArmedInteractionConstraints(emptySet(), setOf(InteractionTriggerKind.AnyActivation))
        }
        assertFailsWith<IllegalArgumentException> {
            ArmedInteractionConstraints(setOf(InteractionKind.OpenWindow), emptySet())
        }
    }

    @Test
    fun expirationMustBeFiniteAndPositive() {
        assertFailsWith<IllegalArgumentException> {
            InteractionArmOptions(Duration.ZERO)
        }
        assertFailsWith<IllegalArgumentException> {
            InteractionArmOptions(Duration.INFINITE)
        }
        InteractionArmOptions(1.seconds)
    }

    @Test
    fun dropInteractionCarriesTheOfferAndItsImmutableOutcomeCorrelation() {
        val offer = TestDropOffer(DropOfferId(0))
        val stamp = EventStamp(SessionSequence(0), SessionInstant(Duration.ZERO), null)

        val event = InteractionEvent.DropEntered(offer, LogicalPoint(12.0, 8.0), stamp)
        val outcome = InteractionActionOutcome.Committed(
            requestId = InteractionRequestId(0),
            windowRequestId = null,
            stamp = stamp,
            dropOfferId = offer.id,
        )

        assertEquals(offer, event.offer)
        assertEquals(offer.id, outcome.dropOfferId)
    }
}

private class TestDropOffer(
    override val id: DropOfferId,
) : DropOffer {
    override val items: List<DropItemDescriptor> = emptyList()
    override val state = MutableStateFlow<DropOfferState>(DropOfferState.Presented)

    override suspend fun claimTransfer(): KadreResult<DropTransfer> = error("not used by this value test")
}
