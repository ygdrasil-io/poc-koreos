package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.diagnostics.Capability
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
import org.graphiks.kadre.surface.SurfaceUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The terminal contract of the web surface.
 *
 * However this surface is brought down — a disconnected element, a hidden page, or a redraw
 * overflow — the same order has to hold: the capabilities are unavailable before the detached state
 * is published, the revision is bumped exactly once, the events stream completes, and the port's DOM
 * bridges are released exactly once with no callback admitted afterwards.
 */
class WebSurfaceTeardownTest {
    @Test
    fun detachmentPublishesCapabilitiesBeforeDetachedStateAndReleasesOnce() = runTest {
        val harness = TeardownHarness(this)
        harness.start()
        val port = harness.port
        val surface = harness.surface()

        val observations = mutableListOf<Pair<Capability<Unit>, SurfaceAttachmentState>>()
        val watcher = launch {
            surface.state.collect {
                observations += surface.capabilities.value.platformAccess to it.attachment
            }
        }
        testScheduler.runCurrent()

        port.deliverLifecycle(port.disconnectedSnapshot())
        testScheduler.runCurrent()
        port.deliverLifecycle(port.disconnectedSnapshot())
        testScheduler.runCurrent()

        val detached = observations.withIndex().filter { it.value.second == SurfaceAttachmentState.Detached }
        assertTrue(detached.isNotEmpty(), "the surface must publish one detached state")
        assertTrue(
            detached.all { it.value.first is Capability.Unsupported },
            "capabilities must be unavailable no later than the detached state, never after it",
        )
        assertEquals(1, port.releaseCount, "port resources are released exactly once")
        assertEquals(
            1L,
            surface.state.value.revision.value,
            "the terminal transition publishes exactly one new revision",
        )

        watcher.cancel()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun lateMetricsAfterReleaseAreIgnored() = runTest {
        val harness = TeardownHarness(this)
        harness.start()
        val port = harness.port
        val surface = harness.surface()
        val events = mutableListOf<SurfaceEvent>()
        val collector = launch { surface.events.collect { events += it } }
        testScheduler.runCurrent()

        port.deliverLifecycle(port.disconnectedSnapshot())
        testScheduler.runCurrent()
        val terminalState = surface.state.value
        val terminalEvents = events.size

        port.deliverMetrics(WebSurfaceMetrics(999.0, 999.0, 3.0))
        testScheduler.runCurrent()

        assertEquals(terminalState, surface.state.value, "no readback may mutate a terminated surface")
        assertEquals(terminalEvents, events.size, "no event may follow termination")

        collector.cancel()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun theEventFlowCompletesAtTermination() = runTest {
        val harness = TeardownHarness(this)
        harness.start()
        val port = harness.port
        val surface = harness.surface()

        var completed = false
        val collector = launch {
            try {
                surface.events.collect { }
            } finally {
                completed = true
            }
        }
        testScheduler.runCurrent()

        port.deliverLifecycle(port.disconnectedSnapshot())
        testScheduler.runCurrent()

        assertTrue(completed, "collectors must finish when the surface terminates")

        var lateCollectorCompleted = false
        val lateCollector = launch {
            surface.events.collect { }
            lateCollectorCompleted = true
        }
        testScheduler.runCurrent()

        assertTrue(
            lateCollectorCompleted,
            "a collector that arrives after termination sees a completed stream instead of suspending",
        )

        collector.cancel()
        lateCollector.cancel()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aPendingFrameNeverFiresAfterPageHide() = runTest {
        val harness = TeardownHarness(this)
        harness.start()
        val port = harness.port
        val surface = harness.surface()
        val events = mutableListOf<SurfaceEvent>()
        val collector = launch { surface.events.collect { events += it } }
        testScheduler.runCurrent()

        assertEquals(KadreResult.Success(Unit), surface.requestRedraw())
        port.deliverLifecycle(port.pageHiddenSnapshot())
        testScheduler.runCurrent()
        val terminalEvents = events.size

        port.runFrame()
        testScheduler.runCurrent()

        assertEquals(terminalEvents, events.size, "a pagehide must cancel the pending frame")
        assertEquals(1, port.frameCancellations, "the surface cancels the frame the pagehide interrupted")
        assertEquals(1, port.releaseCount)
        assertEquals(
            SessionOutcome.Stopped(SessionStopReason.HostDetached),
            harness.outcome(),
            "a pagehide terminates without awaiting a cooperative shutdown",
        )
        collector.cancel()
        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * A cooperative stop revokes the session's target resources first: the port is released while the
     * runtime still has to close the surface, so the surface must stop admitting on the revocation —
     * no new callback may start after the owner has let the element go.
     */
    @Test
    fun aFrameRegisteredBeforeACooperativeStopIsNotAdmitted() = runTest {
        val harness = TeardownHarness(this)
        harness.start()
        val port = harness.port
        val surface = harness.surface()
        val events = mutableListOf<SurfaceEvent>()
        val collector = launch { surface.events.collect { events += it } }
        testScheduler.runCurrent()

        assertEquals(KadreResult.Success(Unit), surface.requestRedraw())
        testScheduler.runCurrent()

        // The window under test: the stop releases the port before the runtime closes the surface.
        harness.stop()
        assertEquals(1, port.releaseCount, "a cooperative stop releases the port itself")
        assertEquals(
            SurfaceAttachmentState.Attached,
            surface.state.value.attachment,
            "the surface is still attached when the port goes, so it has to close admission by itself",
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface)),
            surface.requestRedraw(),
            "a revoked surface starts no new callback",
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface)),
            surface.apply(SurfaceUpdate()),
            "the revocation window closes every admission site, not only the redraw one",
        )

        port.runFrame()
        testScheduler.runCurrent()

        assertTrue(
            events.none { it is SurfaceEvent.RedrawRequested },
            "no redraw may be admitted after the owner released the port",
        )
        assertEquals(1, port.frameCancellations, "the revocation cancels the frame the surface registered")
        assertEquals(SurfaceAttachmentState.Detached, surface.state.value.attachment)
        assertEquals(1L, surface.state.value.revision.value)
        assertTrue(collector.isCompleted, "the events flow still completes at the terminal transition")
        assertEquals(1, port.releaseCount, "the terminal transition does not release the port a second time")

        collector.cancel()
        testScheduler.runCurrent()
    }

    /**
     * The overflow that fails the session reaches termination through the runtime's delivery
     * failure, so the same order has to hold on it as on a detach: only the failing overflow reports
     * the failure, and the surface is already terminal when the session hears about it.
     */
    @Test
    fun aFailedRedrawOverflowTerminatesInTheSameOrder() = runTest {
        val bufferedCapacity = 2
        val policy = KadrePolicies.Default.copy(
            window = KadrePolicies.Default.window.copy(
                redrawRequests = ContinuousDelivery.Buffered(
                    capacity = bufferedCapacity,
                    onOverflow = ContinuousOverflowAction.FailSession,
                ),
            ),
        )
        val harness = TeardownHarness(this, policy)
        harness.start()
        val port = harness.port
        val surface = harness.surface()
        val events = mutableListOf<SurfaceEvent>()
        val collector = launch { surface.events.collect { events += it } }
        testScheduler.runCurrent()

        var attachmentAtRelease: SurfaceAttachmentState? = null
        var platformAccessAtRelease: Capability<Unit>? = null
        port.onRelease = {
            attachmentAtRelease = surface.state.value.attachment
            platformAccessAtRelease = surface.capabilities.value.platformAccess
        }

        // The request that crosses the bound is never admitted: it closes the surface and fails the
        // session instead.
        repeat(bufferedCapacity + 1) { assertEquals(KadreResult.Success(Unit), surface.requestRedraw()) }
        testScheduler.runCurrent()

        assertEquals(
            SessionOutcome.Failed(KadreFailure.SourceOverflow(KadreResourceKind.Surface)),
            harness.outcome(),
            "a buffered overrun fails the session",
        )
        assertEquals(SurfaceAttachmentState.Detached, surface.state.value.attachment)
        assertEquals(
            1L,
            surface.state.value.revision.value,
            "the terminal transition publishes exactly one new revision",
        )
        assertEquals(
            SurfaceAttachmentState.Detached,
            attachmentAtRelease,
            "the surface publishes its terminal state before it lets the element go",
        )
        assertIs<Capability.Unsupported>(
            platformAccessAtRelease,
            "the capabilities are unavailable when the element is let go",
        )
        assertEquals(1, port.releaseCount, "the port is released exactly once on the overflow path")
        assertTrue(events.none { it is SurfaceEvent.RedrawRequested }, "the overrun request is never admitted")
        assertTrue(collector.isCompleted, "the events flow completes with the surface")

        harness.stop()
        testScheduler.runCurrent()
    }
}

/**
 * One attached web session per test, with the initial element every teardown case starts from.
 *
 * The session runs an application that parks until it is torn down, so the surface only ends through
 * the target observation or the redraw policy the test installs.
 */
private class TeardownHarness(scope: TestScope, policy: KadrePolicy = KadrePolicies.Default) {
    val port = RecordingWebHostPort(WebSurfaceMetrics(32.0, 32.0, 1.0))
    private val scopeReady = CompletableDeferred<KadreScope>()
    private val session: KadreSession

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

    /** The surface this session owns, once its application scope exists. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun surface(): HostSurface = scopeReady.getCompleted().primarySurface.value
        ?: error("a web session exposes a primary surface")

    fun stop() = session.requestStop()
}
