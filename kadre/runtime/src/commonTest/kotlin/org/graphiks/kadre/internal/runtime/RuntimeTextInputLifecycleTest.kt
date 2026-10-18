package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.*
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.input.*
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.InputDeliveryPolicy
import org.graphiks.kadre.policy.CollectorOverflowAction
import org.graphiks.kadre.policy.SlowCollectorCancellationException
import org.graphiks.kadre.surface.SurfaceId
import kotlin.test.*
import kotlin.time.Duration.Companion.nanoseconds

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeTextInputLifecycleTest {
    @Test fun openActivationCanPublishWithoutLosingTheFirstObservation() = runTest {
        val port = Port()
        val budget = RuntimeRetainedPayloadBudget(64)
        var accepted = false
        port.onActivate = { command -> accepted = command.onObservation(TextInputObservation.CompositionChanged(
            TextRange(1, 1), "😀", TextRange(2, 2), TextDocumentRevision(0))) }
        val session = assertIs<KadreResult.Success<TextInputSession>>(input(port, budget).openTextInput(config)).value
        assertTrue(accepted)
        assertEquals(TextRange(1, 1), assertIs<TextInputState.Active>(session.state.value).composingRange)
        assertEquals(1, port.owners.single().activated)
        session.close(); assertEquals(0L, budget.used)
    }
    @Test fun terminationDuringOpenClosesOnlyThatOwner() = runTest {
        val port = Port(); val failure = KadreFailure.PlatformFailure(KadrePlatform.Fake, "text", "open")
        port.onOpen = { it.onTerminated(failure) }
        val source = input(port)
        assertEquals(KadreResult.Failure(failure), source.openTextInput(config))
        assertEquals(1, port.owners.single().closed)
        assertEquals(0, port.owners.single().activated)
        assertEquals(FeatureAvailability.Unavailable(failure), assertIs<Capability.Supported<Unit>>(source.state.value.capabilities.textInput).availability)
    }
    @Test fun anOldTerminationCannotCloseTheReplacementSession() = runTest {
        val port = Port(); val source = input(port)
        val first = assertIs<KadreResult.Success<TextInputSession>>(source.openTextInput(config)).value
        first.close()
        val second = assertIs<KadreResult.Success<TextInputSession>>(source.openTextInput(config)).value
        port.commands.first().onTerminated(KadreFailure.PlatformFailure(KadrePlatform.Fake, "text", "late"))
        assertIs<TextInputState.Active>(second.state.value)
        assertEquals(0, port.owners.last().closed)
        assertEquals(FeatureAvailability.Available, assertIs<Capability.Supported<Unit>>(source.state.value.capabilities.textInput).availability)
        second.close()
    }
    @Test fun unavailableTextCapabilityDoesNotOpenANativeOwner() = runTest {
        val port = Port(); val source = input(port)
        val failure = KadreFailure.TemporarilyUnavailable(true)
        assertTrue(source.accept(SurfaceStimulus.InputObservationChanged(SurfaceId(1), true, true,
            textInput=Capability.Supported(Unit, FeatureAvailability.Unavailable(failure)))))
        assertEquals(KadreResult.Failure(failure), source.openTextInput(config))
        assertTrue(port.owners.isEmpty())
    }
    @Test fun cancelledQueuedTextReturnsItsLease() = runTest {
        val budget = RuntimeRetainedPayloadBudget(64); val port = Port()
        val session = assertIs<KadreResult.Success<TextInputSession>>(input(port, budget).openTextInput(config)).value
        val baseline = budget.used
        val stalled = CompletableDeferred<Unit>()
        val collector = launch(start=CoroutineStart.UNDISPATCHED) { session.events.collect { stalled.await() } }
        assertTrue(port.commands.single().onObservation(TextInputObservation.Replace(TextRange(0, 0), "1234", TextDocumentRevision(0))))
        runCurrent()
        assertTrue(port.commands.single().onObservation(TextInputObservation.Replace(TextRange(0, 0), "5678", TextDocumentRevision(0))))
        assertTrue(budget.used > baseline)
        collector.cancelAndJoin()
        assertEquals(baseline, budget.used)
        session.close(); assertEquals(0L, budget.used)
    }
    @Test fun anActivationFailureClosesAndReleasesTheUnpublishedSession() = runTest {
        val budget = RuntimeRetainedPayloadBudget(64); val port = Port().also { it.onActivate = { error("activation") } }
        assertIs<KadreResult.Failure>(input(port, budget).openTextInput(config))
        assertEquals(1, port.owners.single().closed)
        assertEquals(0L, budget.used)
    }
    @Test fun aNewRevisionIsAcknowledgedAfterTheRuntimeAcceptedSnapshot() = runTest {
        val port = Port(); var accepted = false
        port.onApplied = { command, revision -> accepted = command.onObservation(
            TextInputObservation.CompositionChanged(TextRange(1, 1), "x", TextRange(1, 1), revision)) }
        val session = assertIs<KadreResult.Success<TextInputSession>>(input(port).openTextInput(config)).value
        assertIs<KadreResult.Success<Unit>>(session.updateSurroundingText("acb", TextRange(2, 2), TextDocumentRevision(1)))
        assertTrue(accepted)
        assertEquals(TextDocumentRevision(1), assertIs<TextInputState.Active>(session.state.value).documentRevision)
        session.close()
    }
    @Test fun textCollectorsRespectTheDiscretePolicyForZeroPayloadActions() = runTest {
        val port = Port(); val initial = KadrePolicies.Default.input
        val policy = initial.copy(discreteEvents=initial.discreteEvents.copy(collectorCapacity=1,
            collectorOverflow=CollectorOverflowAction.CancelSlowCollector))
        val session = assertIs<KadreResult.Success<TextInputSession>>(input(port, policy=policy).openTextInput(config)).value
        val stalled = CompletableDeferred<Unit>()
        val outcome = async(start=CoroutineStart.UNDISPATCHED) { runCatching { session.events.collect { stalled.await() } } }
        try {
            fun action() = port.commands.single().onObservation(TextInputObservation.Action(TextInputAction.Done, TextDocumentRevision(0)))
            assertTrue(action()); runCurrent()
            assertTrue(action()); assertTrue(action())
            stalled.complete(Unit); runCurrent()
            assertTrue(outcome.isCompleted, "The configured collector capacity must bound zero-payload events too")
            assertIs<SlowCollectorCancellationException>(outcome.await().exceptionOrNull())
            assertIs<TextInputState.Active>(session.state.value)
        } finally { outcome.cancelAndJoin(); session.close() }
    }
    @Test fun normalCloseDrainsAdmittedTextAndThenReleasesItsPayload() = runTest {
        val port = Port(); val budget = RuntimeRetainedPayloadBudget(64)
        val session = assertIs<KadreResult.Success<TextInputSession>>(input(port, budget).openTextInput(config)).value
        val stalled = CompletableDeferred<Unit>(); var delivered = 0
        val collector = async(start=CoroutineStart.UNDISPATCHED) { session.events.collect { delivered++; stalled.await() } }
        try {
            assertTrue(port.commands.single().onObservation(TextInputObservation.Replace(TextRange(0, 0), "1234", TextDocumentRevision(0))))
            runCurrent()
            assertTrue(port.commands.single().onObservation(TextInputObservation.Replace(TextRange(0, 0), "5678", TextDocumentRevision(0))))
            session.close()
            assertTrue(budget.used > 0, "An active collector still owns admitted payload while draining")
            stalled.complete(Unit); collector.await()
            assertEquals(2, delivered); assertEquals(0L, budget.used)
        } finally { collector.cancelAndJoin(); session.close() }
    }
    @Test fun theInitialDocumentReservesItsBytesBeforeOpeningTheNativeOwner() = runTest {
        val port = Port(); val budget = RuntimeRetainedPayloadBudget(3)
        assertEquals(KadreResult.Failure(KadreFailure.ResourceLimitExceeded(KadreResourceKind.RetainedPayload, 3)),
            input(port, budget).openTextInput(config))
        assertTrue(port.owners.isEmpty()); assertEquals(0L, budget.used)
    }
    @Test fun cancellationBeforeHandoffClosesTheOwnerAndReturnsItsInitialLease() = runTest {
        val port = Port(); val budget = RuntimeRetainedPayloadBudget(64); val source = input(port, budget)
        try {
            val attempt = async(start=CoroutineStart.UNDISPATCHED) {
                val openingJob = checkNotNull(currentCoroutineContext()[Job])
                port.onOpen = { openingJob.cancel() }
                source.openTextInput(config)
            }
            attempt.join()
            assertTrue(attempt.isCancelled)
            assertEquals(1, port.owners.single().closed)
            assertEquals(0, port.owners.single().activated)
            assertEquals(0L, budget.used)
            port.onOpen = {}
            assertIs<KadreResult.Success<TextInputSession>>(source.openTextInput(config)).value.close()
        } finally { source.close(null) }
    }
    @Test fun withdrawingTextObservationTerminatesOnlyItsSession() = runTest {
        val port = Port(); val source = input(port)
        val session = assertIs<KadreResult.Success<TextInputSession>>(source.openTextInput(config)).value
        val failure = KadreFailure.PlatformFailure(KadrePlatform.Fake, "text", "withdrawn")
        source.accept(SurfaceStimulus.InputObservationChanged(SurfaceId(1), true, true,
            textInput=Capability.Supported(Unit, FeatureAvailability.Unavailable(failure))))
        assertEquals(TextInputState.Closed, session.state.value)
        assertEquals(1, port.owners.single().closed)
        assertEquals(FeatureAvailability.Available, source.state.value.capabilities.keyboard)
        assertIs<KadreException>(runCatching { session.events.toList() }.exceptionOrNull())
    }
    @Test fun terminationWithdrawsAvailabilityBeforeReportingItsFailure() = runTest {
        val port = Port(); lateinit var source: RuntimeSurfaceInput
        val failure = KadreFailure.PlatformFailure(KadrePlatform.Fake, "text", "terminated")
        var reports = 0
        source = input(port, reporter = {
            reports++
            assertEquals(FeatureAvailability.Unavailable(failure),
                assertIs<Capability.Supported<Unit>>(source.state.value.capabilities.textInput).availability)
        })
        val session = assertIs<KadreResult.Success<TextInputSession>>(source.openTextInput(config)).value
        port.commands.single().onTerminated(failure)
        assertEquals(TextInputState.Closed, session.state.value)
        assertEquals(1, reports)
    }
    private fun input(port: Port, budget: RuntimePayloadBudget = RuntimeRetainedPayloadBudget(1024),
        policy: InputDeliveryPolicy = KadrePolicies.Default.input, reporter: (Throwable) -> Unit = {}) = RuntimeSurfaceInput(
        surfaceId=SurfaceId(1), deliveryPolicy=policy,
        eventStampSource={ EventStamp(SessionSequence(0), SessionInstant(0.nanoseconds), null) },
        eventCollectorGate=gate(), textInputPort=port, rawInputCoordinator=null,
        rawInputCapability=unsupported(KadreOperation.RawInputAccess), dragAndDropAvailable=false,
        resources=KadrePolicies.Default.resources, dropTransferBudget=RuntimeDropTransferBudget(1), dropTransferScope=null,
        textInputEventCollectorGate=gate(), failureReporter=reporter, sessionFailureHandler={}, payloadBudget=budget,
    )
    private fun gate() = RuntimeEventCollectorAllocator(8).newGate(4)
    private class Port : TextInputPort {
        override val capability = Capability.Supported(Unit, FeatureAvailability.Available)
        val owners = mutableListOf<Owner>(); val commands = mutableListOf<TextInputOpenCommand>()
        var onOpen: (TextInputOpenCommand) -> Unit = {}
        var onActivate: (TextInputOpenCommand) -> Unit = {}
        var onApplied: (TextInputOpenCommand, TextDocumentRevision) -> Unit = { _, _ -> }
        override fun open(command: TextInputOpenCommand): KadreResult<TextInputOwner> {
            val owner = Owner({ onActivate(command) }, { revision -> onApplied(command, revision) }); owners += owner; commands += command
            onOpen(command); return KadreResult.Success(owner)
        }
        override suspend fun updateCursor(command: TextInputCursorCommand) = KadreResult.Success(Unit)
        override suspend fun updateDocument(command: TextInputDocumentCommand) = KadreResult.Success(Unit)
    }
    private class Owner(private val activate: () -> Unit, private val applied: (TextDocumentRevision) -> Unit) : TextInputOwner {
        var activated = 0; var closed = 0
        override fun activate() { activated++; activate.invoke() }
        override fun documentApplied(documentRevision: TextDocumentRevision) = applied(documentRevision)
        override fun close() { closed++ }
    }
    companion object { private val config = TextInputConfig(surroundingText="ab", selection=TextRange(1, 1)) }
}
