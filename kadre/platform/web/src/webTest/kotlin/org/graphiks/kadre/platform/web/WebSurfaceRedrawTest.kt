package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.ContinuousDelivery
import org.graphiks.kadre.policy.ContinuousOverflowAction
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WebSurfaceRedrawTest {
    @Test
    fun repeatedRequestsInsideOneFrameProduceOneEvent() = runTest {
        val harness = RedrawHarness(KadrePolicies.Default, this)
        harness.start()
        val surface = harness.surface()
        val events = mutableListOf<SurfaceEvent>()
        val collector = launch { surface.events.collect { events += it } }
        testScheduler.runCurrent()

        assertEquals(KadreResult.Success(Unit), surface.requestRedraw())
        assertEquals(KadreResult.Success(Unit), surface.requestRedraw())
        assertEquals(KadreResult.Success(Unit), surface.requestRedraw())
        assertTrue(events.isEmpty(), "no redraw event may be emitted before the scheduled frame")

        harness.runFrame()
        testScheduler.runCurrent()

        assertEquals(1, events.count { it is SurfaceEvent.RedrawRequested }, "one frame, one admission")
        collector.cancel()
        harness.close()
    }

    @Test
    fun aSecondFrameAdmitsAnotherRequest() = runTest {
        val harness = RedrawHarness(KadrePolicies.Default, this)
        harness.start()
        val surface = harness.surface()
        val events = mutableListOf<SurfaceEvent>()
        val collector = launch { surface.events.collect { events += it } }
        testScheduler.runCurrent()

        surface.requestRedraw()
        harness.runFrame()
        testScheduler.runCurrent()
        surface.requestRedraw()
        harness.runFrame()
        testScheduler.runCurrent()

        assertEquals(2, events.count { it is SurfaceEvent.RedrawRequested })

        // Every admitted event is stamped by the session's own sequence source, which hands out the
        // next value per call: a fabricated or reused stamp would not be increasing.
        val sequences = events.filterIsInstance<SurfaceEvent.RedrawRequested>().map { it.stamp.sequence.value }
        assertEquals(sequences.sorted(), sequences, "the collected events carry increasing session sequences")
        assertTrue(
            sequences.zipWithNext().all { (previous, next) -> next == previous + 1L },
            "each admission draws the next value of the session's own source, in order: $sequences",
        )
        collector.cancel()
        harness.close()
    }

    @Test
    fun redrawIsRejectedAfterDetach() = runTest {
        val harness = RedrawHarness(KadrePolicies.Default, this)
        harness.start()
        val surface = harness.surface()
        harness.detachHost()
        testScheduler.runCurrent()

        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface)),
            surface.requestRedraw(),
        )
        harness.close()
    }

    @Test
    fun aPendingFrameIsCancelledAtTermination() = runTest {
        val harness = RedrawHarness(KadrePolicies.Default, this)
        harness.start()
        val surface = harness.surface()
        val events = mutableListOf<SurfaceEvent>()
        val collector = launch { surface.events.collect { events += it } }
        testScheduler.runCurrent()

        surface.requestRedraw()
        collector.cancel()
        harness.close()
        testScheduler.runCurrent()
        harness.runFrame()
        testScheduler.runCurrent()

        assertTrue(events.none { it is SurfaceEvent.RedrawRequested }, "a cancelled frame admits nothing")
        assertEquals(1, harness.frameCancellations, "the surface cancels the frame it registered")
        assertEquals(1, harness.releases, "termination releases the port exactly once")
    }

    @Test
    fun bufferedProfileFailsTheSessionOnOverflow() = runTest {
        val harness = RedrawHarness(KadrePolicies.Recording, this)
        harness.start()
        val surface = harness.surface()
        repeat(harness.bufferedCapacity + 1) { surface.requestRedraw() }
        testScheduler.runCurrent()

        assertEquals(
            SessionOutcome.Failed(KadreFailure.SourceOverflow(KadreResourceKind.Surface)),
            harness.outcome(),
            "exceeding the buffered redraw capacity fails the session",
        )
        harness.close()
    }

    @Test
    fun aDropOverflowPolicyDropsTheExcessAndKeepsCoalescing() = runTest {
        val policy = KadrePolicies.Default.copy(
            window = KadrePolicies.Default.window.copy(
                redrawRequests = ContinuousDelivery.Buffered(
                    capacity = 2,
                    onOverflow = ContinuousOverflowAction.DropOldestAndReport,
                ),
            ),
        )
        val harness = RedrawHarness(policy, this)
        harness.start()
        val surface = harness.surface()
        val events = mutableListOf<SurfaceEvent>()
        val collector = launch { surface.events.collect { events += it } }
        testScheduler.runCurrent()

        repeat(harness.bufferedCapacity + 1) {
            assertEquals(KadreResult.Success(Unit), surface.requestRedraw())
        }
        testScheduler.runCurrent()
        harness.runFrame()
        testScheduler.runCurrent()

        assertEquals(1, events.count { it is SurfaceEvent.RedrawRequested }, "one frame, one admission")
        assertEquals(SessionState.Running, harness.state(), "a drop policy must not fail the session")

        surface.requestRedraw()
        harness.runFrame()
        testScheduler.runCurrent()

        assertEquals(2, events.count { it is SurfaceEvent.RedrawRequested }, "coalescing continues after a drop")
        assertEquals(SessionState.Running, harness.state())
        collector.cancel()
        harness.close()
    }

    @Test
    fun aCloseSourceOverflowClosesTheSurfaceAndKeepsTheSession() = runTest {
        val policy = KadrePolicies.Default.copy(
            window = KadrePolicies.Default.window.copy(
                redrawRequests = ContinuousDelivery.Buffered(
                    capacity = 2,
                    onOverflow = ContinuousOverflowAction.CloseSource,
                ),
            ),
        )
        val harness = RedrawHarness(policy, this)
        harness.start()
        val surface = harness.surface()
        val events = mutableListOf<SurfaceEvent>()
        val collector = launch { surface.events.collect { events += it } }
        testScheduler.runCurrent()

        // The request that crosses the bound is still admitted, and then closes the surface.
        repeat(harness.bufferedCapacity + 1) {
            assertEquals(KadreResult.Success(Unit), surface.requestRedraw())
        }
        testScheduler.runCurrent()
        harness.runFrame()
        testScheduler.runCurrent()

        assertEquals(
            SessionState.Running,
            harness.state(),
            "CloseSource closes only the surface, so the session keeps running",
        )
        assertEquals(SurfaceAttachmentState.Detached, surface.state.value.attachment)
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface)),
            surface.requestRedraw(),
            "a closed surface answers Closed to every operation",
        )
        assertTrue(events.none { it is SurfaceEvent.RedrawRequested }, "a closed surface admits nothing")
        assertTrue(collector.isCompleted, "the events flow completes when the surface closes")
        assertEquals(1, harness.releases, "closing the surface disarms the element's Kadre bridges")
        harness.close()
    }

    @Test
    fun theBufferedRedrawBudgetResetsEveryFrame() = runTest {
        val policy = KadrePolicies.Default.copy(
            window = KadrePolicies.Default.window.copy(
                redrawRequests = ContinuousDelivery.Buffered(
                    capacity = 1,
                    onOverflow = ContinuousOverflowAction.FailSession,
                ),
            ),
        )
        val harness = RedrawHarness(policy, this)
        harness.start()
        val surface = harness.surface()
        val events = mutableListOf<SurfaceEvent>()
        val collector = launch { surface.events.collect { events += it } }
        testScheduler.runCurrent()

        repeat(harness.bufferedCapacity) { assertEquals(KadreResult.Success(Unit), surface.requestRedraw()) }
        harness.runFrame()
        testScheduler.runCurrent()
        assertEquals(1, events.count { it is SurfaceEvent.RedrawRequested }, "the frame admits the request")

        surface.requestRedraw()
        harness.runFrame()
        testScheduler.runCurrent()

        assertEquals(2, events.count { it is SurfaceEvent.RedrawRequested }, "the next frame admits again")
        assertEquals(
            SessionState.Running,
            harness.state(),
            "a session-lifetime counter would have failed the session on the second frame's request",
        )
        collector.cancel()
        harness.close()
    }

    @Test
    fun requestRedrawFromAnEventCollectorIsCoalesced() = runTest {
        val harness = RedrawHarness(KadrePolicies.Default, this)
        harness.start()
        val surface = harness.surface()
        var admissions = 0
        var reentered = false
        val collector = launch {
            surface.events.collect {
                admissions += 1
                if (!reentered) {
                    reentered = true
                    surface.requestRedraw()
                }
            }
        }
        testScheduler.runCurrent()

        surface.requestRedraw()
        harness.runFrame()
        testScheduler.runCurrent()
        val afterFirstFrame = admissions
        harness.runFrame()
        testScheduler.runCurrent()

        assertEquals(afterFirstFrame + 1, admissions, "the re-entrant request admits exactly once, in the next frame")
        collector.cancel()
        harness.close()
    }

    @Test
    fun independentSurfacesScheduleIndependently() = runTest {
        val first = RedrawHarness(KadrePolicies.Default, this)
        val second = RedrawHarness(KadrePolicies.Default, this)
        first.start()
        second.start()
        val firstEvents = mutableListOf<SurfaceEvent>()
        val secondEvents = mutableListOf<SurfaceEvent>()
        val firstCollector = launch { first.surface().events.collect { firstEvents += it } }
        val secondCollector = launch { second.surface().events.collect { secondEvents += it } }
        testScheduler.runCurrent()

        first.surface().requestRedraw()
        second.surface().requestRedraw()
        second.surface().requestRedraw()
        first.runFrame()
        testScheduler.runCurrent()

        assertEquals(1, firstEvents.count { it is SurfaceEvent.RedrawRequested })
        assertEquals(
            0,
            secondEvents.count { it is SurfaceEvent.RedrawRequested },
            "one surface's frame does not admit another's request",
        )

        second.runFrame()
        testScheduler.runCurrent()
        assertEquals(1, secondEvents.count { it is SurfaceEvent.RedrawRequested })

        firstCollector.cancel()
        secondCollector.cancel()
        first.close()
        second.close()
    }
}

private class RedrawHarness(private val policy: KadrePolicy, scope: CoroutineScope) {
    private val port = RecordingWebHostPort(WebSurfaceMetrics(64.0, 64.0, 1.0))
    private val scopeReady = CompletableDeferred<KadreScope>()
    private val session: KadreSession

    /** The redraw capacity of the buffered profile under test, read from the policy itself. */
    val bufferedCapacity: Int
        get() = (policy.window.redrawRequests as? ContinuousDelivery.Buffered)?.capacity
            ?: error("the policy under test does not buffer redraw requests")

    init {
        session = assertIs<KadreResult.Success<KadreSession>>(
            WebHostSession(port = port, registry = WebHostRegistry()).attach(
                parentScope = scope,
                applicationFactory = KadreApplicationFactory {
                    KadreApplication {
                        scopeReady.complete(this)
                        awaitCancellation()
                    }
                },
                policy = policy,
            ),
        ).value
    }

    /** Pumping the scheduler until the application scope has been handed to the test. */
    suspend fun start() = scopeReady.await()

    /** The terminal outcome the runtime published for this session. */
    suspend fun outcome(): SessionOutcome = session.awaitTermination()

    /** The runtime's published session state; a dropped overflow must leave it running. */
    fun state(): SessionState = session.state.value

    @OptIn(ExperimentalCoroutinesApi::class)
    fun surface(): HostSurface = scopeReady.getCompleted().primarySurface.value
        ?: error("a web session exposes a primary surface")

    fun runFrame() = port.runFrame()

    fun detachHost() = port.deliverLifecycle(
        WebLifecycleSnapshot(
            connected = false,
            inOriginDocument = true,
            documentVisible = true,
            browsingContextFocused = true,
            subtreeFocused = true,
        ),
    )

    /** How often the surface itself cancelled a registered frame. */
    val frameCancellations: Int get() = port.frameCancellations

    /** How often the port was released. */
    val releases: Int get() = port.releaseCount

    fun close() = session.close()
}
