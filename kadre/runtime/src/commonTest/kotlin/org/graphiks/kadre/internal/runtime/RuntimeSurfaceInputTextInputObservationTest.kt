package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.TextDocumentRevision
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextInputSession
import org.graphiks.kadre.input.TextRange
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.SurfaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Pins the ownership of a text-input observation callback.
 *
 * The callback admitted by `openTextInput` belongs to the session that call opened, not to
 * the surface: once that session is closed, the callback must reject every observation, even
 * when a later session now owns the surface and would accept an equivalent observation on its
 * own document. A port may deliver an observation arbitrarily late (the AppKit queued port
 * defers every observation through its queue), so routing by surface-wide state would let a
 * closed session's observation mutate the live one.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RuntimeSurfaceInputTextInputObservationTest {
    @Test
    fun anObservationFromAClosedSessionIsRejectedAndLeavesTheCurrentSessionUntouched() = runTest {
        val port = RecordingTextInputPort()
        val input = input(port)
        val config = TextInputConfig(surroundingText = "ab", selection = TextRange(2, 2))

        val first = assertIs<KadreResult.Success<TextInputSession>>(input.openTextInput(config)).value
        first.close()
        val second = assertIs<KadreResult.Success<TextInputSession>>(input.openTextInput(config)).value
        val stateBefore = second.state.value
        val events = async(start = CoroutineStart.UNDISPATCHED) { second.events.toList() }

        // The same config opened both sessions, so this observation would be admissible for
        // `second`: only its ownership by the closed session can reject it.
        val accepted = port.observation(0)(
            TextInputObservation.Replace(
                range = TextRange(1, 2),
                text = "x",
                baseRevision = TextDocumentRevision(0),
            ),
        )

        assertFalse(accepted)
        assertEquals(stateBefore, second.state.value)

        second.close()
        assertTrue(events.await().isEmpty())
    }

    private fun input(port: TextInputPort): RuntimeSurfaceInput = RuntimeSurfaceInput(
        surfaceId = SURFACE_ID,
        deliveryPolicy = KadrePolicies.Default.input,
        eventStampSource = {
            EventStamp(SessionSequence(0L), SessionInstant(0L.nanoseconds), null)
        },
        eventCollectorGate = eventCollectorGate(),
        textInputPort = port,
        rawInputCoordinator = null,
        rawInputCapability = unsupported(KadreOperation.RawInputAccess),
        dragAndDropAvailable = false,
        resources = KadrePolicies.Default.resources,
        dropTransferBudget = RuntimeDropTransferBudget(
            KadrePolicies.Default.resources.maxConcurrentDropTransfers,
        ),
        dropTransferScope = null,
        textInputEventCollectorGate = eventCollectorGate(),
        failureReporter = { },
        sessionFailureHandler = { },
    )

    private fun eventCollectorGate(): RuntimeEventCollectorGate = RuntimeEventCollectorAllocator(
        KadrePolicies.Default.resources.maxEventCollectorsPerSession,
    ).newGate(KadrePolicies.Default.resources.maxEventCollectorsPerFlow)

    private class RecordingTextInputPort : TextInputPort {
        override val capability: Capability<Unit> = Capability.Supported(Unit, FeatureAvailability.Available)
        private val opened = mutableListOf<TextInputOpenCommand>()

        override fun open(command: TextInputOpenCommand): KadreResult<TextInputOwner> {
            opened += command
            return KadreResult.Success(RecordingTextInputOwner())
        }

        override suspend fun updateCursor(command: TextInputCursorCommand): KadreResult<Unit> =
            KadreResult.Success(Unit)

        override suspend fun updateDocument(command: TextInputDocumentCommand): KadreResult<Unit> =
            KadreResult.Success(Unit)

        fun observation(index: Int): (TextInputObservation) -> Boolean = opened[index].onObservation
    }

    private class RecordingTextInputOwner : TextInputOwner {
        override fun close() = Unit
    }

    private companion object {
        val SURFACE_ID = SurfaceId(1L)
    }
}
