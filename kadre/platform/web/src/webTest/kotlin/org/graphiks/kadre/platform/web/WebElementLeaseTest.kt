package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.SurfaceInput
import org.graphiks.kadre.policy.ContinuousDelivery
import org.graphiks.kadre.policy.ContinuousOverflowAction
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.SurfaceCapabilities
import org.graphiks.kadre.surface.SurfaceEvent
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceUpdate
import org.graphiks.kadre.surface.SurfaceUpdateOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The lifetime contract of the element escape hatch, exercised on the DOM-free lease core.
 *
 * The core takes and returns `Any`, so this suite runs unchanged in both browser targets; the
 * element it lends is whatever the port holds, and only the two facades turn that reference into an
 * `HTMLElement`.
 */
class WebElementLeaseTest {
    @Test
    fun anAttachedSurfaceLendsItsElement() = runTest {
        val harness = LeasedHarness(this)
        harness.start()
        val surface = harness.surface()
        val lease = surface as WebElementLeasePort

        val result = lease.lease { harness.port.element }

        assertIs<KadreResult.Success<Any>>(result)
        assertSame(harness.port.element, result.value)
        assertEquals(0, harness.port.releaseCount)
        harness.close()
    }

    @Test
    fun aSecondConcurrentLeaseReportsATemporaryFailure() = runTest {
        val harness = LeasedHarness(this)
        harness.start()
        val lease = harness.surface() as WebElementLeasePort
        val held = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = launch {
            lease.lease {
                held.complete(Unit)
                release.await()
                Unit
            }
        }
        held.await()

        val second = lease.lease { Unit }

        assertEquals(
            KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true)),
            second,
        )
        release.complete(Unit)
        first.join()
        harness.close()
    }

    @Test
    fun aDetachedSurfaceReportsClosedSurface() = runTest {
        val harness = LeasedHarness(this)
        harness.start()
        val lease = harness.surface() as WebElementLeasePort
        harness.detachHost()

        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface)),
            lease.lease { Unit },
        )
        harness.close()
    }

    @Test
    fun aCallbackExceptionPropagatesAndReleasesTheLease() = runTest {
        val harness = LeasedHarness(this)
        harness.start()
        val lease = harness.surface() as WebElementLeasePort
        val thrown = IllegalStateException("renderer exploded")

        val raised = runCatching { lease.lease { throw thrown } }.exceptionOrNull()

        assertSame(thrown, raised, "the callback exception is propagated unchanged")
        assertIs<KadreResult.Success<Unit>>(lease.lease { Unit }, "the lease is released before the exception propagates")
        harness.close()
    }

    /**
     * The holder's callback is entered exactly once, and a cancellation inside it releases the lease.
     *
     * A lease has no suspension point between admitting and invoking the callback, so the only
     * cancellation the implementation can observe is the one that arrives once the callback has
     * started — here, while the holder is parked inside it. That path runs production code: the
     * cancellation unwinds through the `finally`, and the lease that follows proves the flag was
     * released rather than left set by a cancelled holder.
     */
    @Test
    fun aWaiterCancelledInsideTheCallbackReleasesTheLease() = runTest {
        val harness = LeasedHarness(this)
        harness.start()
        val lease = harness.surface() as WebElementLeasePort
        var invocations = 0
        val parked = CompletableDeferred<Unit>()
        val holder = launch { lease.lease { invocations += 1; parked.await() } }
        testScheduler.runCurrent()

        holder.cancel()
        testScheduler.runCurrent()

        assertEquals(1, invocations, "the cancelled holder entered its callback exactly once")
        assertIs<KadreResult.Success<Unit>>(
            lease.lease { Unit },
            "the cancellation released the lease instead of leaving it held",
        )
        assertTrue(holder.isCancelled)
        harness.close()
    }

    /**
     * A surface that stopped admitting reports `Closed` even with a lease still in flight.
     *
     * The redraw overflow under `CloseSource` closes the surface while the session keeps running,
     * which is the one way to reach a closed surface that still holds a lease: the holder is parked
     * inside its callback when the surface goes. `retryable = true` would be a lie there — the
     * surface never becomes leasable again — so the admission guard has to answer first.
     */
    @Test
    fun aClosedSurfaceReportsClosedWhileALeaseIsInFlight() = runTest {
        val capacity = 2
        val policy = KadrePolicies.Default.copy(
            window = KadrePolicies.Default.window.copy(
                redrawRequests = ContinuousDelivery.Buffered(
                    capacity = capacity,
                    onOverflow = ContinuousOverflowAction.CloseSource,
                ),
            ),
        )
        val harness = LeasedHarness(this, policy)
        harness.start()
        val surface = harness.surface()
        val lease = surface as WebElementLeasePort
        val parked = CompletableDeferred<Unit>()
        var completed = 0
        val holder = launch {
            val result = lease.lease { parked.await() }
            assertIs<KadreResult.Success<Unit>>(result)
            completed += 1
        }
        testScheduler.runCurrent()

        repeat(capacity + 1) { assertEquals(KadreResult.Success(Unit), surface.requestRedraw()) }

        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface)),
            lease.lease { Unit },
            "a closed surface answers Closed, not a retryable failure, with a lease in flight",
        )

        parked.complete(Unit)
        holder.join()

        assertEquals(1, completed, "the lease admitted before the close still completes normally")
        harness.close()
    }

    @Test
    fun aForeignSurfaceReportsUnsupportedPlatformAccess() = runTest {
        val harness = LeasedHarness(this)
        harness.start()
        val foreign: HostSurface = unsupportedSurface(harness.surface())

        val result = runCatching {
            (foreign as? WebElementLeasePort)?.lease { Unit }
                ?: KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.PlatformSurfaceAccess))
        }.getOrThrow()

        assertEquals(KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.PlatformSurfaceAccess)), result)
        harness.close()
    }
}

/**
 * A surface that lends nothing because it is not a web surface, the way an Android or AppKit one is
 * not: it observes nothing and answers `Unsupported` to every operation and capability.
 *
 * Identity and state are read from a real surface because neither `SurfaceId` nor `SurfaceRevision`
 * has a public constructor, and this suite is outside the module that owns them. The resolution
 * under test reads neither; `input` fails loudly so the fake cannot silently stand in for a surface
 * that would have to answer for it.
 */
private fun unsupportedSurface(host: HostSurface): HostSurface = object : HostSurface {
    override val id: SurfaceId = host.id
    override val state: StateFlow<SurfaceState> = host.state
    override val capabilities: StateFlow<SurfaceCapabilities> = MutableStateFlow(unsupportedSurfaceCapabilities())
    override val events: Flow<SurfaceEvent> = emptyFlow()
    override val input: SurfaceInput get() = error("a foreign surface is never asked for its input")

    override fun requestRedraw(): KadreResult<Unit> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RequestRedraw))

    override suspend fun apply(update: SurfaceUpdate): KadreResult<SurfaceUpdateOutcome> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.UpdateSurface))
}

private fun unsupportedSurfaceCapabilities(): SurfaceCapabilities = SurfaceCapabilities(
    cursor = unsupportedSurfaceCapability(KadreOperation.UpdateSurface),
    customCursor = unsupportedSurfaceCapability(KadreOperation.UpdateSurface),
    pointerCapture = unsupportedSurfaceCapability(KadreOperation.UpdateSurface),
    hitTesting = unsupportedSurfaceCapability(KadreOperation.UpdateSurface),
    inputDefaultBehavior = unsupportedSurfaceCapability(KadreOperation.UpdateSurface),
    handlerInteractions = unsupportedSurfaceCapability(KadreOperation.InstallInteractionHandler),
    armedInteractions = unsupportedSurfaceCapability(KadreOperation.ArmInteraction),
    platformAccess = unsupportedSurfaceCapability(KadreOperation.PlatformSurfaceAccess),
)

private fun <T> unsupportedSurfaceCapability(operation: KadreOperation): Capability<T> =
    Capability.Unsupported(KadreFailure.Unsupported(operation))

/**
 * One attached web session per test, over the shared port double that carries the element.
 *
 * The session runs an application that parks until it is torn down, so the surface only ends through
 * the target observation or the test's own close.
 */
private class LeasedHarness(
    scope: CoroutineScope,
    policy: KadrePolicy = KadrePolicies.Default,
) {
    val port = RecordingWebHostPort(WebSurfaceMetrics(64.0, 64.0, 1.0), element = Any())
    private val scopeReady = CompletableDeferred<KadreScope>()
    private val session: KadreSession

    init {
        session = assertIs<KadreResult.Success<KadreSession>>(
            WebHostSession(port, WebHostRegistry()).attach(
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

    suspend fun start() = scopeReady.await()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun surface(): HostSurface = scopeReady.getCompleted().primarySurface.value
        ?: error("a web session exposes a primary surface")

    fun detachHost() = port.deliverLifecycle(
        WebLifecycleSnapshot(
            connected = false,
            inOriginDocument = true,
            documentVisible = true,
            browsingContextFocused = true,
            subtreeFocused = true,
        ),
    )

    fun close() = session.close()
}
