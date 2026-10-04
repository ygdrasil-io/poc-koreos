package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DropItemDescriptor
import org.graphiks.kadre.input.DropItemKind
import org.graphiks.kadre.input.DropItemReadMode
import org.graphiks.kadre.input.DropOfferState
import org.graphiks.kadre.input.DropOfferTerminationReason
import org.graphiks.kadre.input.DropTransfer
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.interaction.InteractionAction
import org.graphiks.kadre.interaction.InteractionContext
import org.graphiks.kadre.interaction.InteractionEvent
import org.graphiks.kadre.interaction.InteractionHandler
import org.graphiks.kadre.interaction.InteractionRegistration
import org.graphiks.kadre.internal.runtime.DropItemSource
import org.graphiks.kadre.internal.runtime.DropTransferSource
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.LogicalPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The drop seam of the web surface: the DOM-free half of the DOM drop pipeline, from the entry the
 * target reports to the transfer the consumer claims.
 *
 * Every case drives the shared [RecordingWebHostPort] and no browser: the channel member the port
 * invokes inside its `dragenter` listener and the observations it delivers from `dragover`,
 * `dragleave` and `drop` are what these tests hand over, so what is proven here is the surface's own
 * contract — the offer a drag entry presents and the `DropEntered` it dispatches synchronously, the
 * accept or reject the handler's single-use token decides, the offer-only activation question the
 * port asks before it may drop a browser default (D-D3), the deliveries that ride the ordinary input
 * reducer, and the teardown that closes what the session owned. The bounded, copied and single-use
 * reads are not re-implemented anywhere in the web seam — the reducer owns them, and the read case
 * pins them as the integration behaviour that seam delivers unchanged.
 */
@OptIn(ExperimentalCoroutinesApi::class, DelicateKadreApi::class)
class WebDropSurfaceTest {
    @Test
    fun aDragEnterPresentsAnOfferAndDispatchesDropEnteredSynchronously() = runTest {
        val harness = DropHarness(this)
        harness.start()
        val source = RecordedDropTransferSource()
        val entered = mutableListOf<InteractionEvent.DropEntered>()
        val statesDuringCallback = mutableListOf<DropOfferState>()
        harness.installHandler { _, event ->
            if (event is InteractionEvent.DropEntered) {
                entered += event
                statesDuringCallback += event.offer.state.value
            }
        }
        val events = mutableListOf<InputEvent>()
        harness.collectInput(events)
        testScheduler.runCurrent()

        harness.port.deliverDropEntered(source, DROP_POSITION)
        testScheduler.runCurrent()

        assertEquals(1, entered.size, "the drag entry dispatches one DropEntered to the installed handler")
        val offer = entered[0].offer
        assertEquals(DROP_POSITION, entered[0].position, "the interaction carries the position the port reported")
        val dropEntered = assertIs<InputEvent.DropEntered>(events[0])
        assertEquals(offer.id, dropEntered.offer.id, "the offer the input event names is the one the handler received")
        assertEquals(DROP_POSITION, dropEntered.position)
        assertEquals(
            listOf<DropOfferState>(DropOfferState.Presented),
            statesDuringCallback.toList(),
            "the offer is presented while the handler decides, inside the frame the entry arrived in",
        )
        assertEquals(
            DropOfferTerminationReason.Rejected,
            (offer.state.value as? DropOfferState.Terminated)?.reason,
            "an entry the handler did not answer is rejected before the dispatch returns",
        )
        assertFalse(harness.port.holdsActiveDropOffer(), "an offer the handler has not answered is not an accepted one")

        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aHandlerThatAcceptsYieldsAnAcceptedOfferWhoseDropIsClaimableByASingleWinner() = runTest {
        val harness = DropHarness(this)
        harness.start()
        var claimed: KadreResult<DropTransfer>? = null
        var secondClaim: KadreResult<DropTransfer>? = null
        harness.installHandler { context, event ->
            if (event is InteractionEvent.DropEntered) context.request(InteractionAction.AcceptDrop(event.offer.id))
        }
        val events = mutableListOf<InputEvent>()
        harness.collectInput(events)
        testScheduler.runCurrent()

        val source = RecordedDropTransferSource()
        harness.port.deliverDropEntered(source, DROP_POSITION)
        testScheduler.runCurrent()

        val entered = assertIs<InputEvent.DropEntered>(events[0])
        val offer = entered.offer
        assertEquals(DropOfferState.Accepted, offer.state.value, "the handler's AcceptDrop committed the offer")
        assertTrue(harness.port.holdsActiveDropOffer(), "the surface holds the accepted offer the port may activate for")

        harness.port.deliverInput(WebInputStimulus.DropPerformed(DROP_POSITION))
        testScheduler.runCurrent()

        val dropped = assertIs<InputEvent.Dropped>(events[1])
        assertEquals(offer.id, dropped.offer.id, "the drop the consumer observes is the offer that was accepted")
        assertEquals(DROP_POSITION, dropped.position)
        launch {
            claimed = offer.claimTransfer()
            secondClaim = offer.claimTransfer()
        }
        testScheduler.runCurrent()

        assertIs<KadreResult.Success<DropTransfer>>(claimed, "the first claim wins and takes the transfer")
        assertEquals(
            KadreFailure.AlreadyInUse(KadreResourceKind.DropTransfer),
            assertIs<KadreResult.Failure>(secondClaim).reason,
            "the single-winner claim is the reducer's own rule, unchanged by the web seam",
        )

        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun theClaimedTransferReadsBoundedCopiedAndSingleUseThroughTheWebSeam() = runTest {
        val harness = DropHarness(this)
        harness.start()
        harness.installHandler { context, event ->
            if (event is InteractionEvent.DropEntered) context.request(InteractionAction.AcceptDrop(event.offer.id))
        }
        val events = mutableListOf<InputEvent>()
        harness.collectInput(events)
        testScheduler.runCurrent()

        val replayableChunks = listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5))
        val singleUseChunks = listOf(byteArrayOf(9, 8))
        val source = RecordedDropTransferSource(
            RecordedDropItem(chunks = replayableChunks, readMode = DropItemReadMode.Replayable),
            RecordedDropItem(chunks = singleUseChunks, readMode = DropItemReadMode.SingleUse),
        )
        harness.port.deliverDropEntered(source, DROP_POSITION)
        harness.port.deliverInput(WebInputStimulus.DropPerformed(DROP_POSITION))
        testScheduler.runCurrent()
        val dropped = assertIs<InputEvent.Dropped>(events[1])

        var overBounded: KadreResult<Unit>? = null
        var firstRead: List<List<Byte>>? = null
        var replayedRead: List<List<Byte>>? = null
        var consumedRead: List<List<Byte>>? = null
        var singleUseAgain: KadreResult<Unit>? = null
        launch {
            val claimed = assertIs<KadreResult.Success<DropTransfer>>(dropped.offer.claimTransfer()).value
            val replayable = claimed.items[0]
            val singleUse = claimed.items[1]
            overBounded = replayable.collectBytes(3L) { }
            val received = mutableListOf<ByteArray>()
            firstRead = mutableListOf<List<Byte>>().also { values ->
                replayable.collectBytes(64L) { chunk ->
                    values += chunk.toList()
                    received += chunk
                }
            }
            // Everything the reads delivered is zeroed in place: what the replay read then delivers
            // tells whether the collector received the source's own chunks or copies of them.
            received.forEach { it.fill(0) }
            replayedRead = mutableListOf<List<Byte>>().also { values ->
                replayable.collectBytes(64L) { chunk -> values += chunk.toList() }
            }
            consumedRead = mutableListOf<List<Byte>>().also { values ->
                singleUse.collectBytes(64L) { chunk -> values += chunk.toList() }
            }
            singleUseAgain = singleUse.collectBytes(64L) { }
        }
        testScheduler.runCurrent()

        assertEquals(
            KadreFailure.ResourceLimitExceeded(KadreResourceKind.DropItem, 3L),
            assertIs<KadreResult.Failure>(overBounded).reason,
            "the total bound is the reducer's rule the web transfer is read through",
        )
        assertEquals(replayableChunks.map { it.toList() }, firstRead)
        assertEquals(
            replayableChunks.map { it.toList() },
            replayedRead,
            "the chunks the collector mutated were copies: the replay read the originals, unchanged",
        )
        assertEquals(singleUseChunks.map { it.toList() }, consumedRead!!.map { it.toList() })
        assertEquals(
            KadreFailure.Closed(KadreResourceKind.DropItem),
            assertIs<KadreResult.Failure>(singleUseAgain).reason,
            "a SingleUse item is consumed by its one read, exactly as the reducer promised",
        )
        assertTrue(
            source.readsWereBoundedBy(KadrePolicies.Default.resources.maxDropChunkBytes),
            "the reducer asks the source through its own chunk budget — the reader's bound is the total it enforces above, and the web seam adds no read path of its own",
        )

        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aHandlerAbsentDropIsRejectedAndTheBrowserKeepsItsDefault() = runTest {
        val harness = DropHarness(this)
        harness.start()
        val events = mutableListOf<InputEvent>()
        harness.collectInput(events)
        testScheduler.runCurrent()

        val source = RecordedDropTransferSource()
        harness.port.deliverDropEntered(source, DROP_POSITION)
        testScheduler.runCurrent()

        assertEquals(1, events.size, "only the entry itself is published when no handler answered it")
        val offer = assertIs<InputEvent.DropEntered>(events[0]).offer
        assertEquals(
            DropOfferTerminationReason.Rejected,
            (offer.state.value as? DropOfferState.Terminated)?.reason,
            "an offer without a handler to accept it is terminal and rejected",
        )
        assertTrue(source.closed, "the source of a rejected offer is closed by the offer that rejected it")
        assertFalse(harness.port.holdsActiveDropOffer(), "a rejected offer activates no drop target: the browser keeps its default")

        harness.port.deliverInput(WebInputStimulus.DropPerformed(DROP_POSITION))
        testScheduler.runCurrent()
        assertEquals(1, events.size, "a drop over a rejected offer is nothing the reducer could reduce")

        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aHandlerThatConsumesItsTokenOnAnotherActionAlsoRejectsTheOffer() = runTest {
        val harness = DropHarness(this)
        harness.start()
        val events = mutableListOf<InputEvent>()
        harness.collectInput(events)
        harness.installHandler { context, event ->
            if (event is InteractionEvent.DropEntered) context.request(InteractionAction.UnlockPointer)
        }
        testScheduler.runCurrent()

        val source = RecordedDropTransferSource()
        harness.port.deliverDropEntered(source, DROP_POSITION)
        testScheduler.runCurrent()

        val offer = assertIs<InputEvent.DropEntered>(events[0]).offer
        assertEquals(
            DropOfferTerminationReason.Rejected,
            (offer.state.value as? DropOfferState.Terminated)?.reason,
            "an offer the handler did not accept is terminal, whatever else its token did",
        )
        assertTrue(source.closed)

        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aSecondDragEnterTerminatesThePreviousOfferAsLeftSurface() = runTest {
        val harness = DropHarness(this)
        harness.start()
        harness.installHandler { context, event ->
            if (event is InteractionEvent.DropEntered) context.request(InteractionAction.AcceptDrop(event.offer.id))
        }
        val events = mutableListOf<InputEvent>()
        harness.collectInput(events)
        testScheduler.runCurrent()

        val first = RecordedDropTransferSource()
        harness.port.deliverDropEntered(first, LogicalPoint(1.0, 1.0))
        testScheduler.runCurrent()
        assertTrue(harness.port.holdsActiveDropOffer())

        val second = RecordedDropTransferSource()
        harness.port.deliverDropEntered(second, LogicalPoint(2.0, 2.0))
        testScheduler.runCurrent()

        val firstOffer = assertIs<InputEvent.DropEntered>(events[0]).offer
        assertEquals(
            DropOfferTerminationReason.LeftSurface,
            (firstOffer.state.value as? DropOfferState.Terminated)?.reason,
            "the one active offer of the reducer ends the offer it replaces",
        )
        assertTrue(first.closed, "the replaced source is closed with its offer")
        assertTrue(harness.port.holdsActiveDropOffer(), "the surface now holds the offer that replaced the first")

        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aDragOverDeliversDropMovedOnlyWhileTheOfferIsActive() = runTest {
        val harness = DropHarness(this)
        harness.start()
        harness.installHandler { context, event ->
            if (event is InteractionEvent.DropEntered) context.request(InteractionAction.AcceptDrop(event.offer.id))
        }
        val events = mutableListOf<InputEvent>()
        harness.collectInput(events)
        testScheduler.runCurrent()

        harness.port.deliverInput(WebInputStimulus.DropMoved(LogicalPoint(4.0, 5.0)))
        testScheduler.runCurrent()
        assertTrue(events.isEmpty(), "a drag over an element without an offer is nothing to reduce")
        assertFalse(harness.port.holdsActiveDropOffer(), "without an offer the browser default is not dropped")

        val source = RecordedDropTransferSource()
        harness.port.deliverDropEntered(source, DROP_POSITION)
        harness.port.deliverInput(WebInputStimulus.DropMoved(LogicalPoint(6.0, 7.0)))
        testScheduler.runCurrent()

        val moved = assertIs<InputEvent.DropMoved>(events[1])
        assertEquals(assertIs<InputEvent.DropEntered>(events[0]).offer.id, moved.offerId, "the drag over rides the offer the entry presented")
        assertEquals(LogicalPoint(6.0, 7.0), moved.position)
        assertTrue(harness.port.holdsActiveDropOffer(), "the accepted offer is what activates the drop target")

        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aDragLeaveDeliversDropExitedAndTerminatesTheOffer() = runTest {
        val harness = DropHarness(this)
        harness.start()
        harness.installHandler { context, event ->
            if (event is InteractionEvent.DropEntered) context.request(InteractionAction.AcceptDrop(event.offer.id))
        }
        val events = mutableListOf<InputEvent>()
        harness.collectInput(events)
        testScheduler.runCurrent()

        val source = RecordedDropTransferSource()
        harness.port.deliverDropEntered(source, DROP_POSITION)
        harness.port.deliverInput(WebInputStimulus.DropExited)
        testScheduler.runCurrent()

        val exited = assertIs<InputEvent.DropExited>(events[1])
        assertEquals(assertIs<InputEvent.DropEntered>(events[0]).offer.id, exited.offerId)
        val offer = assertIs<InputEvent.DropEntered>(events[0]).offer
        assertEquals(
            DropOfferTerminationReason.LeftSurface,
            (offer.state.value as? DropOfferState.Terminated)?.reason,
            "leaving the element ends the offer the drag carried",
        )
        assertTrue(source.closed)
        assertFalse(harness.port.holdsActiveDropOffer(), "an offer the drag took away activates nothing")

        harness.port.deliverInput(WebInputStimulus.DropPerformed(DROP_POSITION))
        testScheduler.runCurrent()
        assertEquals(2, events.size, "a drop after the exit is nothing the reducer could reduce")

        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aSessionCloseTerminatesTheOfferItStillHoldsAsOwnerClosed() = runTest {
        val harness = DropHarness(this)
        harness.start()
        harness.installHandler { context, event ->
            if (event is InteractionEvent.DropEntered) context.request(InteractionAction.AcceptDrop(event.offer.id))
        }
        val events = mutableListOf<InputEvent>()
        harness.collectInput(events)
        testScheduler.runCurrent()

        val source = RecordedDropTransferSource()
        harness.port.deliverDropEntered(source, DROP_POSITION)
        testScheduler.runCurrent()
        val offer = assertIs<InputEvent.DropEntered>(events[0]).offer

        harness.stop()
        testScheduler.runCurrent()

        assertEquals(
            DropOfferTerminationReason.OwnerClosed,
            (offer.state.value as? DropOfferState.Terminated)?.reason,
            "the session that owned the offer closed it before the drag completed",
        )
        assertTrue(source.closed, "the offer's source is closed with the offer")
        var claim: KadreResult<DropTransfer>? = null
        launch { claim = offer.claimTransfer() }
        testScheduler.runCurrent()
        assertEquals(
            KadreFailure.Closed(KadreResourceKind.DropTransfer),
            assertIs<KadreResult.Failure>(claim).reason,
            "an offer the owner closed claims nothing",
        )
    }

    @Test
    fun aSessionCloseClosesTheTransfersItKeptAndTheSourcesBehindThem() = runTest {
        val harness = DropHarness(this)
        harness.start()
        harness.installHandler { context, event ->
            if (event is InteractionEvent.DropEntered) context.request(InteractionAction.AcceptDrop(event.offer.id))
        }
        val events = mutableListOf<InputEvent>()
        harness.collectInput(events)
        testScheduler.runCurrent()

        val source = RecordedDropTransferSource()
        harness.port.deliverDropEntered(source, DROP_POSITION)
        harness.port.deliverInput(WebInputStimulus.DropPerformed(DROP_POSITION))
        testScheduler.runCurrent()
        val offer = assertIs<InputEvent.DropEntered>(events[0]).offer

        var claimed: KadreResult<DropTransfer>? = null
        launch { claimed = offer.claimTransfer() }
        testScheduler.runCurrent()
        val transfer = assertIs<KadreResult.Success<DropTransfer>>(claimed).value
        assertEquals(
            DropOfferState.Claimed,
            offer.state.value,
            "the offer was claimed before the close: the close has no termination to add to a won claim",
        )

        harness.stop()
        testScheduler.runCurrent()

        assertTrue(source.closed, "the session close reaches the source behind the claimed transfer")
        var read: KadreResult<Unit>? = null
        launch { read = transfer.items.single().collectBytes(64L) { } }
        testScheduler.runCurrent()
        assertEquals(
            KadreFailure.Closed(KadreResourceKind.DropTransfer),
            assertIs<KadreResult.Failure>(read).reason,
            "a transfer the session closed reads nothing",
        )
    }

    @Test
    fun dragAndDropIsDeclaredAvailableStructurallyFromTheFirstInputState() = runTest {
        val harness = DropHarness(this)
        harness.start()
        val state = harness.surface().input.state.value

        assertEquals(
            FeatureAvailability.Available,
            state.capabilities.dragAndDrop,
            "the capability is the structural snapshot of the installation the drag listeners belong to",
        )
        harness.port.deliverInput(WebInputStimulus.DropExited)
        testScheduler.runCurrent()
        assertEquals(
            FeatureAvailability.Available,
            harness.surface().input.state.value.capabilities.dragAndDrop,
            "no observation of this phase degrades the capability that was never conditioned on one",
        )

        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aDropEntryBeforeTheSessionConfigurationPresentsNothingAndClosesItsSource() = runTest {
        val port = RecordingWebHostPort(WebSurfaceMetrics(48.0, 48.0, 1.0))
        val source = RecordedDropTransferSource()
        val session = assertIs<KadreResult.Success<KadreSession>>(
            WebHostSession(
                port = port,
                registry = WebHostRegistry(),
            ).attach(
                parentScope = this,
                applicationFactory = KadreApplicationFactory {
                    KadreApplication { awaitCancellation() }
                },
                policy = KadrePolicies.Default,
            ),
        ).value
        // The port's observer exists from its installation; the session configuration does not yet:
        // nothing has run the application the configuration belongs to.
        port.deliverDropEntered(source, DROP_POSITION)

        assertTrue(source.closed, "an entry nobody could present closes the source it carried")
        assertFalse(port.holdsActiveDropOffer())
        session.requestStop()
        testScheduler.runCurrent()
    }

    @Test
    fun theSurfaceKeepsAnsweringNoOfferAfterItStoppedAdmitting() = runTest {
        val harness = DropHarness(this)
        harness.start()
        harness.installHandler { context, event ->
            if (event is InteractionEvent.DropEntered) context.request(InteractionAction.AcceptDrop(event.offer.id))
        }

        val source = RecordedDropTransferSource()
        harness.port.deliverDropEntered(source, DROP_POSITION)
        testScheduler.runCurrent()
        assertTrue(harness.port.holdsActiveDropOffer())

        harness.stop()
        testScheduler.runCurrent()
        assertFalse(
            harness.port.holdsActiveDropOffer(),
            "a surface that stopped admitting owns nothing, least of all a drop target it may activate",
        )
    }

    private companion object {
        val DROP_POSITION = LogicalPoint(10.0, 12.0)
    }
}

/**
 * The drop harness: the same one-port session the input suite drives, with the input collection the
 * drop cases read.
 */
@OptIn(ExperimentalCoroutinesApi::class, DelicateKadreApi::class)
private class DropHarness(
    scope: TestScope,
) {
    private val testScope: TestScope = scope
    val port = RecordingWebHostPort(WebSurfaceMetrics(48.0, 48.0, 1.0))

    /** Every cause the session's failure reporter was handed; a drop seam failure is reported, never thrown. */
    val reportedFailures: MutableList<Throwable> = mutableListOf()

    private val scopeReady = CompletableDeferred<KadreScope>()
    private val session: KadreSession

    init {
        session = assertIs<KadreResult.Success<KadreSession>>(
            WebHostSession(
                port = port,
                registry = WebHostRegistry(),
                failureReporter = RuntimeFailureReporter { cause -> reportedFailures += cause },
            ).attach(
                parentScope = scope,
                applicationFactory = KadreApplicationFactory {
                    KadreApplication {
                        scopeReady.complete(this)
                        awaitCancellation()
                    }
                },
                policy = KadrePolicies.Default,
            ),
        ).value
    }

    /** Pumping the scheduler until the application scope has been handed to the test. */
    suspend fun start() = scopeReady.await()

    /** The surface this session owns, once its application scope exists. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun surface(): HostSurface = scopeReady.getCompleted().primarySurface.value
        ?: error("a web session exposes a primary surface")

    /** Installs the interaction handler the case drives, and answers with its registration. */
    fun installHandler(
        handler: (context: InteractionContext, event: InteractionEvent) -> Unit,
    ): InteractionRegistration = assertIs<KadreResult.Success<InteractionRegistration>>(
        surface().installInteractionHandler(InteractionHandler(handler)),
    ).value

    /** Collects the surface's input events from now on, into [events], in delivery order. */
    fun collectInput(events: MutableList<InputEvent>): Job =
        testScope.async(UnconfinedTestDispatcher(testScope.testScheduler), start = CoroutineStart.UNDISPATCHED) {
            surface().input.events.collect { event -> events += event }
        }

    fun stop() = session.requestStop()
}

/** One item the recorded source carries: its snapshot descriptor, its read mode and its chunks. */
private class RecordedDropItem(
    val chunks: List<ByteArray>,
    val readMode: DropItemReadMode,
    val descriptor: DropItemDescriptor = DropItemDescriptor(
        displayName = "item",
        sizeBytes = 4L,
        mimeTypes = listOf("text/plain"),
        kind = DropItemKind.Text,
    ),
)

/**
 * The drop source the cases present: it records its close, the chunk bounds its readers asked for
 * and every chunk its reads delivered, which is how a case reads back what the seam did with the
 * source it handed over. Its chunks are handed to the collector *un-copied* on purpose — the copies
 * the collector receives are the reducer's own work, which is exactly what the read case pins. No
 * handle of any target crosses this class — only Kotlin values, exactly as the real glue's source
 * promises.
 */
private class RecordedDropTransferSource(
    private val itemSpecs: List<RecordedDropItem>,
) : DropTransferSource {
    constructor() : this(listOf(RecordedDropItem(chunks = listOf(byteArrayOf(1, 2, 3, 4)), readMode = DropItemReadMode.Replayable)))

    constructor(first: RecordedDropItem, second: RecordedDropItem) : this(listOf(first, second))

    private val requestedChunkBounds: MutableList<Int> = mutableListOf()

    override val items: List<DropItemSource> = itemSpecs.map { spec ->
        object : DropItemSource {
            override val descriptor = spec.descriptor
            override val readMode = spec.readMode

            override suspend fun collectBytes(
                maxChunkBytes: Int,
                collector: suspend (ByteArray) -> Unit,
            ): KadreResult<Unit> {
                requestedChunkBounds += maxChunkBytes
                for (chunk in spec.chunks) collector(chunk)
                return KadreResult.Success(Unit)
            }
        }
    }

    /** Whether every read was asked through the one chunk bound the reducer asks its sources with. */
    fun readsWereBoundedBy(maxChunkBytes: Int): Boolean =
        requestedChunkBounds.isNotEmpty() && requestedChunkBounds.all { it == maxChunkBytes }

    var closed = false
        private set

    override fun close() {
        closed = true
    }
}
