package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.InputStateRevision
import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.ModifierKey
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.SurfaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.nanoseconds

class RuntimeSurfaceInputConfigurationTest {
    @Test
    fun minimalConfigurationAdvertisesEveryInputCapabilityAsUnsupported() {
        val input = minimalInput()

        val capabilities = input.state.value.capabilities
        assertEquals(FeatureAvailability.Unsupported, capabilities.keyboard)
        assertEquals(FeatureAvailability.Unsupported, capabilities.pointer)
        assertEquals(FeatureAvailability.Unsupported, capabilities.touch)
        assertIs<Capability.Unsupported>(capabilities.gestures)
        assertEquals(FeatureAvailability.Unsupported, capabilities.dragAndDrop)
        assertEquals(
            KadreFailure.Unsupported(KadreOperation.TextInput),
            assertIs<Capability.Unsupported>(capabilities.textInput).failure,
        )
        assertEquals(
            KadreFailure.Unsupported(KadreOperation.RawInputAccess),
            assertIs<Capability.Unsupported>(capabilities.rawInput).failure,
        )
    }

    @Test
    fun minimalConfigurationStartsWithAnEmptyNeutralSnapshot() {
        val input = minimalInput()

        val state = input.state.value
        assertEquals(InputStateRevision(0L), state.revision)
        assertEquals(KeyboardModifiers(emptySet()), state.modifiers)
        assertTrue(state.keyboard.pressedKeys.isEmpty())
        assertTrue(state.pointers.isEmpty())
        assertTrue(state.touches.isEmpty())
    }

    @Test
    fun openTextInputIsUnsupportedWithoutAPort() = runTest {
        val input = minimalInput()

        val failure = assertIs<KadreResult.Failure>(input.openTextInput(TextInputConfig()))
        assertEquals(KadreFailure.Unsupported(KadreOperation.TextInput), failure.reason)
    }

    @OptIn(DelicateKadreApi::class)
    @Test
    fun requestRawInputIsUnsupportedWithoutACoordinator() = runTest {
        val input = minimalInput()

        val failure = assertIs<KadreResult.Failure>(input.requestRawInput())
        assertEquals(KadreFailure.Unsupported(KadreOperation.RawInputAccess), failure.reason)
    }

    @Test
    fun admittedKeyChangeProducesACoherentSnapshot() = runTest {
        val stamp = EventStamp(SessionSequence(0L), SessionInstant(0L.nanoseconds), null)
        val input = minimalInput(stampSource = { stamp })
        val physicalKey = PhysicalKey.Unidentified("native-key")
        val logicalKey = LogicalKey.Unidentified("logical-key")
        val modifiers = KeyboardModifiers(setOf(ModifierKey.Shift))
        val event = async(start = CoroutineStart.UNDISPATCHED) { input.events.first() }

        assertTrue(
            input.accept(
                SurfaceStimulus.KeyChanged(
                    surfaceId = SURFACE_ID,
                    physicalKey = physicalKey,
                    logicalKey = logicalKey,
                    location = KeyLocation.Standard,
                    keyState = KeyState.Pressed,
                    repeat = false,
                    modifiers = modifiers,
                ),
            ),
        )

        val state = input.state.value
        assertEquals(InputStateRevision(1L), state.revision)
        assertEquals(setOf(physicalKey), state.keyboard.pressedKeys)
        assertEquals(modifiers, state.modifiers)
        val published = assertIs<InputEvent.Key>(event.await())
        assertEquals(physicalKey, published.physicalKey)
        assertEquals(logicalKey, published.logicalKey)
        assertEquals(stamp, published.stamp)
        assertEquals(state.revision, published.stateRevision)
    }

    private fun minimalInput(
        stampSource: () -> EventStamp = {
            EventStamp(SessionSequence(0L), SessionInstant(0L.nanoseconds), null)
        },
    ): RuntimeSurfaceInput = RuntimeSurfaceInput(
        surfaceId = SURFACE_ID,
        deliveryPolicy = KadrePolicies.Default.input,
        eventStampSource = stampSource,
        eventCollectorGate = eventCollectorGate(),
        textInputPort = UnsupportedTextInputPort,
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

    private companion object {
        val SURFACE_ID = SurfaceId(1L)
    }
}
