package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreLaunchContext
import org.graphiks.kadre.application.KadreLaunchReason
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.InteractionFailureReason
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadrePolicyComponent
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.KadrePermission
import org.graphiks.kadre.internal.runtime.KadreLaunchInfo
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.surface.BinaryImage
import org.graphiks.kadre.surface.ImageFormat
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.window.WindowCancellationOutcome
import org.graphiks.kadre.window.WindowCreationMode
import org.graphiks.kadre.window.WindowRequest
import org.graphiks.kadre.window.WindowRequestId
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowRequestState
import org.graphiks.kadre.window.WindowSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The closed failure set of `WebWindowProvider.open` and the validation ladder of the host it
 * returns, exactly as `OPERATION-CONTRACTS.md` §4 spells them out: every rejection a `requestWindow`
 * caller can observe is one row of that table, produced as an **outcome of the admitted request** —
 * the outer call answers `Success(WindowRequest)` — and never as a failure of the call itself.
 *
 * The provider and the host probe arrive as doubles — the decisions under test are the manager's and
 * must be the same on both targets — while the child sessions are real [WebHostSession] attachments
 * through the ordinary attach path (plan decision D6), so the outcomes name sessions that really
 * exist and really run.
 */
class WebHostWindowManagerTest {
    private val hostFactory = RecordingApplicationFactory()

    // -- doubles ---------------------------------------------------------------------------------------------

    /** A provider double answering every `open` with one fixed result, or throwing one fixed exception. */
    private class FixedProvider(
        private val result: KadreResult<WebHostWindowOffer>? = null,
        private val exception: Throwable? = null,
    ) : WebHostWindowProvider {
        val requests = mutableListOf<Pair<WindowRequestId, WindowSpec>>()

        override fun open(requestId: WindowRequestId, spec: WindowSpec): KadreResult<WebHostWindowOffer> {
            requests += requestId to spec
            exception?.let { throw it }
            return checkNotNull(result) { "this double was built to throw" }
        }
    }

    /** The probe double answering every offer with one fixed set of checks. */
    private class FixedProbe(private val checks: WebWindowHostChecks) : WebWindowHostProbe {
        val offers = mutableListOf<WebHostWindowOffer>()

        override fun probe(offer: WebHostWindowOffer): WebWindowHostChecks {
            offers += offer
            return checks
        }
    }

    /**
     * The child session factory double in the production shape (D6): it attaches a **real**
     * [WebHostSession] through the ordinary attach path, on the offer's own scope, with the launch
     * identity of the request that caused it, on a port whose stable identity is the offer's
     * element — the identity the registry will reserve.
     *
     * The registry is injectable because ownership is a property of the registry a host pair
     * shares: in production every attach goes through [WebHostRegistry.shared], and the test that
     * proves a busy element must hand the owner session and this factory the same instance.
     */
    private inner class ProductionChildFactory(
        private val registry: WebHostRegistry = WebHostRegistry(),
    ) : WebChildSessionFactory {
        val opened = mutableListOf<Pair<WebHostWindowOffer, WindowRequestId>>()
        val sessions = mutableListOf<KadreSession>()

        override fun open(offer: WebHostWindowOffer, requestId: WindowRequestId): KadreResult<KadreSession> {
            opened += offer to requestId
            return when (val attached = WebHostSession(ChildPort(offer.element), registry).attach(
                offer.parentScope,
                hostFactory,
                KadrePolicies.Default,
                offer.attachmentPolicy,
                launch = KadreLaunchInfo(KadreLaunchReason.AdditionalHostRequested, requestId),
            )) {
                is KadreResult.Success -> {
                    sessions += attached.value
                    attached
                }

                is KadreResult.Failure -> attached
            }
        }
    }

    /** A bare port whose stable identity is the element a provider handed over. */
    private class ChildPort(private val identity: Any) : WebHostPort {
        override val initialSnapshot: WebSurfaceMetrics = WebSurfaceMetrics(1.0, 1.0, 1.0)
        override val stableIdentity: Any = identity

        override fun release() {}
    }

    /** A factory double recording every launch context the applications observe. */
    private class RecordingApplicationFactory : KadreApplicationFactory {
        val contexts = mutableListOf<KadreLaunchContext>()

        override fun create(context: KadreLaunchContext): KadreApplication {
            contexts += context
            return KadreApplication { awaitCancellation() }
        }
    }

    // -- helpers ---------------------------------------------------------------------------------------------

    private fun healthyChecks() = WebWindowHostChecks(
        elementConnected = true,
        distinctDefaultView = true,
        scopeHasJob = true,
        scopeActive = true,
    )

    private fun offerFor(scope: CoroutineScope): WebHostWindowOffer =
        WebHostWindowOffer(Any(), scope, WebAttachmentPolicy.Manual)

    private fun policyWith(maxPending: Int): KadrePolicy = KadrePolicies.Default.copy(
        resources = KadrePolicies.Default.resources.copy(maxPendingWindowRequests = maxPending),
    )

    private fun manager(
        provider: WebHostWindowProvider?,
        probe: WebWindowHostProbe = FixedProbe(healthyChecks()),
        childSessionFactory: WebChildSessionFactory = ProductionChildFactory(),
        maxPending: Int = 4,
    ): WebHostWindowManager = WebHostWindowManager(
        policy = policyWith(maxPending),
        nextRequestId = RuntimeProcessIds::nextWindowRequestId,
        provider = provider,
        childSessionFactory = childSessionFactory,
        probe = probe,
    )

    private fun specWithIcon(): WindowSpec = WindowSpec(
        title = "child",
        contentSize = LogicalSize(320.0, 180.0),
        icon = BinaryImage(byteArrayOf(1, 2, 3), ImageFormat.Png, PhysicalSize(1, 1)),
    )

    private fun outcomeOf(request: WindowRequest): WindowRequestOutcome =
        assertIs<WindowRequestState.Terminated>(request.state.value).outcome

    private suspend fun admittedRequest(windows: WebHostWindowManager): WindowRequest =
        assertIs<KadreResult.Success<WindowRequest>>(windows.requestWindow(specWithIcon())).value

    // -- the failure table -----------------------------------------------------------------------------------

    @Test
    fun requestWindowWithoutProviderIsATerminalRejectedRequest() = runTest {
        val windows = manager(provider = null)

        // The outer call succeeds; the absence of multi-window is the request's own terminal
        // outcome — byte for byte the behaviour of the unsupported manager this session carried
        // until now (`UnsupportedWindowRequest`).
        val request = admittedRequest(windows)

        val outcome = request.await()
        assertEquals(
            WindowRequestOutcome.Rejected(KadreFailure.Unsupported(KadreOperation.RequestWindow)),
            outcome,
        )
        assertEquals(outcome, outcomeOf(request))
        assertEquals(WindowCancellationOutcome.AlreadyTerminated(outcome), request.cancel())
        request.close()
    }

    @Test
    fun theWindowManagerCapabilityIsOpenedInNewSessionOnlyWhenAProviderIsConfigured() {
        val withProvider = manager(provider = FixedProvider(KadreResult.Success(offerFor(CoroutineScope(SupervisorJob())))))
        val capability = withProvider.state.value.capabilities.requestWindow
        val supported = assertIs<Capability.Supported<Set<WindowCreationMode>>>(capability)
        assertEquals(setOf(WindowCreationMode.OpenedInNewSession), supported.constraints)
        assertEquals(FeatureAvailability.Available, supported.availability)

        val withoutProvider = manager(provider = null)
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RequestWindow)),
            withoutProvider.state.value.capabilities.requestWindow,
        )
    }

    @Test
    fun theProviderReceivesARequestAndACopiedSpec() = runTest {
        val childScope = childScope()
        val provider = FixedProvider(KadreResult.Success(offerFor(childScope)))
        val windows = manager(provider)
        val spec = specWithIcon()

        val request = admittedRequest(windows)
        val (firstRequestId, received) = provider.requests.single()

        // The provider never receives the caller's own spec: a copy travels, the icon with it.
        assertFalse(spec === received, "the provider must receive a copy, not the caller's spec")
        assertEquals(spec, received)
        val originalIcon = checkNotNull(spec.icon)
        val receivedIcon = checkNotNull(received.icon)
        assertFalse(originalIcon === receivedIcon, "the icon must be rewrapped, not shared")
        assertEquals(originalIcon, receivedIcon)
        // The id the provider saw is the id of the request the caller owns.
        assertEquals(request.id, firstRequestId)

        request.close()
        childScope.cancel()
        testScheduler.runCurrent()
    }

    @Test
    fun anElementOwnedByALiveSessionIsRejectedBusyHost() = runTest {
        val childScope = childScope()
        // One registry, the production shape: owner and child factory both reserve through the same
        // instance, exactly as every attach goes through `WebHostRegistry.shared`.
        val registry = WebHostRegistry()
        // A live session owns the element the provider hands back — the requester's own element,
        // the one case the contract admits `Busy(Host)` for.
        val ownedPort = ChildPort(Any())
        val owner = assertIs<KadreResult.Success<KadreSession>>(
            WebHostSession(ownedPort, registry).attach(
                childScope,
                hostFactory,
                KadrePolicies.Default,
            ),
        ).value
        testScheduler.runCurrent()

        val provider = FixedProvider(
            KadreResult.Success(WebHostWindowOffer(ownedPort.stableIdentity, childScope, WebAttachmentPolicy.Manual)),
        )
        val windows = manager(provider, childSessionFactory = ProductionChildFactory(registry))

        val request = admittedRequest(windows)
        assertEquals(
            WindowRequestOutcome.Rejected(KadreFailure.AlreadyInUse(KadreResourceKind.Host)),
            request.await(),
        )

        owner.requestStop()
        testScheduler.runCurrent()
        childScope.cancel()
    }

    @Test
    fun aSameContextElementIsRejectedInvalidOwnerDocument() = runTest {
        val childScope = childScope()
        val probe = FixedProbe(healthyChecks().copy(distinctDefaultView = false))
        val windows = manager(FixedProvider(KadreResult.Success(offerFor(childScope))), probe)

        val request = admittedRequest(windows)

        assertEquals(
            WindowRequestOutcome.Rejected(KadreFailure.InvalidRequest("element.ownerDocument")),
            request.await(),
        )
        childScope.cancel()
    }

    @Test
    fun aNullDefaultViewIsTheSameCode() = runTest {
        // The per-target probe folds "no browsing context" and "the origin's browsing context" into
        // one boolean, so the manager answers both with the one code the contract names.
        val childScope = childScope()
        val probe = FixedProbe(healthyChecks().copy(distinctDefaultView = false))
        val windows = manager(FixedProvider(KadreResult.Success(offerFor(childScope))), probe)

        val request = admittedRequest(windows)

        assertEquals(
            WindowRequestOutcome.Rejected(KadreFailure.InvalidRequest("element.ownerDocument")),
            request.await(),
        )
        childScope.cancel()
    }

    @Test
    fun aDisconnectedElementUnderStopWhenDetachedIsRejectedInvalidElement() = runTest {
        val childScope = childScope()
        val probe = FixedProbe(healthyChecks().copy(elementConnected = false))
        val offer = WebHostWindowOffer(Any(), childScope, WebAttachmentPolicy.StopWhenDetached)
        val windows = manager(FixedProvider(KadreResult.Success(offer)), probe)

        val request = admittedRequest(windows)

        assertEquals(
            WindowRequestOutcome.Rejected(KadreFailure.InvalidRequest("element")),
            request.await(),
        )
        childScope.cancel()
    }

    @Test
    fun aDisconnectedElementUnderManualIsNotTheElementRung() = runTest {
        // `Manual` hosts may arrive detached: the element rung is gated by the attachment policy,
        // and a disconnected element under `Manual` reaches the later rungs — and the child
        // session — unharmed.
        val childScope = childScope()
        val probe = FixedProbe(healthyChecks().copy(elementConnected = false))
        val offer = WebHostWindowOffer(Any(), childScope, WebAttachmentPolicy.Manual)
        val childFactory = ProductionChildFactory()
        val windows = manager(FixedProvider(KadreResult.Success(offer)), probe, childFactory)

        val request = admittedRequest(windows)

        assertIs<WindowRequestOutcome.OpenedInNewSession>(request.await())
        childScope.cancel()
        testScheduler.runCurrent()
    }

    @Test
    fun aScopeWithoutJobIsInvalidParentScope() = runTest {
        // A scope without a Job cannot be a coroutine parent — the offer's own scope is what the
        // ladder reads, never the caller's.
        val joblessScope = CoroutineScope(kotlin.coroutines.EmptyCoroutineContext)
        val probe = FixedProbe(healthyChecks().copy(scopeHasJob = false))
        val windows = manager(FixedProvider(KadreResult.Success(offerFor(joblessScope))), probe)

        val request = admittedRequest(windows)

        assertEquals(
            WindowRequestOutcome.Rejected(KadreFailure.InvalidRequest("parentScope")),
            request.await(),
        )
    }

    @Test
    fun anInactiveScopeIsParentScopeCancelled() = runTest {
        // The caller runs, so its own scope is alive: the scope the probe reported inactive is the
        // new host's — the only scope `ParentScopeCancelled` may describe (`OPERATION-CONTRACTS.md`
        // §4: it never describes the requester's scope).
        val inactiveScope = CoroutineScope(SupervisorJob())
        inactiveScope.cancel()
        val probe = FixedProbe(healthyChecks().copy(scopeActive = false))
        val windows = manager(FixedProvider(KadreResult.Success(offerFor(inactiveScope))), probe)

        val request = admittedRequest(windows)

        assertEquals(
            WindowRequestOutcome.Rejected(KadreFailure.ParentScopeCancelled),
            request.await(),
        )
    }

    @Test
    fun theValidationLadderReportsTheFirstRungThatFails() = runTest {
        val childScope = childScope()
        val allFailing = WebWindowHostChecks(
            elementConnected = false,
            distinctDefaultView = false,
            scopeHasJob = false,
            scopeActive = false,
        )
        val offer = WebHostWindowOffer(Any(), childScope, WebAttachmentPolicy.StopWhenDetached)
        val windows = manager(FixedProvider(KadreResult.Success(offer)), FixedProbe(allFailing))

        val request = admittedRequest(windows)

        // The ladder runs in the contract's order, and the first failing rung is the only answer:
        // `element` before `element.ownerDocument` before `parentScope` before the cancelled scope.
        assertEquals(
            WindowRequestOutcome.Rejected(KadreFailure.InvalidRequest("element")),
            request.await(),
        )
        childScope.cancel()
    }

    @Test
    fun aProviderExceptionBecomesTheCallbackExceptionOutcome() = runTest {
        val windows = manager(FixedProvider(exception = IllegalStateException("host callback blew up")))

        val request = admittedRequest(windows)

        assertEquals(
            WindowRequestOutcome.Rejected(
                KadreFailure.PlatformFailure(KadrePlatform.Web, "WebWindowProvider", "callback-exception"),
            ),
            request.await(),
        )
    }

    @Test
    fun anOutOfSetFailureBecomesInvalidFailure() = runTest {
        val childScope = childScope()
        // `Closed(Surface)` is not in the closed set of `WindowRequestOutcome.Rejected.failure`
        // (`Closed(Host)` is), so the manager must not pass it through unchanged.
        val windows = manager(
            FixedProvider(KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface))),
        )

        val request = admittedRequest(windows)

        assertEquals(
            WindowRequestOutcome.Rejected(
                KadreFailure.PlatformFailure(KadrePlatform.Web, "WebWindowProvider", "invalid-failure"),
            ),
            request.await(),
        )
        childScope.cancel()
    }

    @Test
    fun theProviderFailureGateAdmitsExactlyTheRejectedSet() {
        val admitted = listOf(
            KadreFailure.Unsupported(KadreOperation.RequestWindow),
            KadreFailure.InvalidRequest("element"),
            KadreFailure.InvalidRequest(null),
            KadreFailure.InteractionRequired(InteractionFailureReason.Missing),
            KadreFailure.AlreadyInUse(KadreResourceKind.Host),
            KadreFailure.Closed(KadreResourceKind.Host),
            KadreFailure.ParentScopeCancelled,
            KadreFailure.ResourceLimitExceeded(KadreResourceKind.Window, 1L),
            KadreFailure.TemporarilyUnavailable(retryable = true),
            KadreFailure.TemporarilyUnavailable(retryable = false),
            KadreFailure.PlatformFailure(KadrePlatform.Web, "host", "anything"),
        )
        admitted.forEach { failure ->
            assertTrue(isAdmittedWindowRequestFailure(failure), "expected admitted: $failure")
        }

        val refused = listOf(
            KadreFailure.Unsupported(KadreOperation.DisplayAccess),
            KadreFailure.PermissionDenied(KadrePermission.DisplayEnumeration),
            KadreFailure.UserCancelled(KadreOperation.RequestWindow),
            KadreFailure.AlreadyInUse(KadreResourceKind.Surface),
            KadreFailure.Closed(KadreResourceKind.Surface),
            KadreFailure.ResourceLimitExceeded(KadreResourceKind.WindowRequest, 1L),
            KadreFailure.ResourceLimitExceeded(KadreResourceKind.ImageResource, 1L),
            KadreFailure.SourceOverflow(KadreResourceKind.Surface),
            KadreFailure.StaleRevision(2L, 3L),
            KadreFailure.UnsupportedPolicy(KadrePolicyComponent.WindowEvents),
            KadreFailure.ShutdownTimedOut(1.seconds),
            KadreFailure.ApplicationFailure,
        )
        refused.forEach { failure ->
            assertFalse(isAdmittedWindowRequestFailure(failure), "expected refused: $failure")
        }
    }

    @Test
    fun anAdmittedSetFailureIsPassedThrough() = runTest {
        val temporary = KadreFailure.TemporarilyUnavailable(retryable = true)
        val windowLimit = KadreFailure.ResourceLimitExceeded(KadreResourceKind.Window, 2L)
        listOf(temporary, windowLimit).forEach { failure ->
            val childScope = childScope()
            val windows = manager(FixedProvider(KadreResult.Failure(failure)))

            val request = admittedRequest(windows)

            assertEquals(
                WindowRequestOutcome.Rejected(failure),
                request.await(),
                "expected pass-through of $failure",
            )
            request.close()
            childScope.cancel()
        }
    }

    @Test
    fun successProducesOpenedInNewSessionAndTheChildSessionRunsTheFactory() = runTest {
        val childScope = childScope()
        val offer = offerFor(childScope)
        val childFactory = ProductionChildFactory()
        val windows = manager(FixedProvider(KadreResult.Success(offer)), childSessionFactory = childFactory)

        val request = admittedRequest(windows)
        val outcome = request.await()

        // Exactly the new-session outcome, never an `OpenedHere`: this target has no window of its
        // own to open anything in.
        val opened = assertIs<WindowRequestOutcome.OpenedInNewSession>(outcome)
        val (factoryOffer, factoryRequestId) = childFactory.opened.single()
        assertSame(offer, factoryOffer, "the factory must receive the very offer the provider returned")
        assertEquals(request.id, factoryRequestId)
        val child = childFactory.sessions.single { it.id == opened.sessionId }
        // The startup coroutine runs on the child scope's own dispatcher: let it start before the
        // application is expected to have observed its launch context.
        testScheduler.runCurrent()
        // The child application was created by the shared factory and learned why it launched.
        val childContext = hostFactory.contexts.last()
        assertEquals(KadreLaunchReason.AdditionalHostRequested, childContext.reason)
        assertEquals(request.id, childContext.originatingRequestId)
        assertEquals(child.id, childContext.sessionId)
        testScheduler.runCurrent()
        assertEquals(SessionState.Running, child.state.value)

        child.requestStop()
        childScope.cancel()
        testScheduler.runCurrent()
    }

    @Test
    fun closingTheRequesterDoesNotCloseTheChildSession() = runTest {
        // Full stack: a real host session whose application requests a window through its own
        // manager, the child attaching on a scope the host session does not own (D6).
        val childScope = childScope()
        val hostScopeReady = CompletableDeferred<KadreScope>()
        val outcomeReady = CompletableDeferred<WindowRequestOutcome>()
        val childFactory = ProductionChildFactory()

        val host = assertIs<KadreResult.Success<KadreSession>>(
            WebHostSession(ChildPort(Any()), WebHostRegistry()).attach(
                this,
                KadreApplicationFactory {
                    KadreApplication {
                        hostScopeReady.complete(this)
                        val request = assertIs<KadreResult.Success<WindowRequest>>(
                            windows.requestWindow(specWithIcon()),
                        ).value
                        outcomeReady.complete(request.await())
                        awaitCancellation()
                    }
                },
                KadrePolicies.Default,
                windowProvider = FixedProvider(KadreResult.Success(offerFor(childScope))),
                childSessionFactory = childFactory,
                windowHostProbe = FixedProbe(healthyChecks()),
            ),
        ).value
        testScheduler.runCurrent()
        hostScopeReady.await()
        val opened = assertIs<WindowRequestOutcome.OpenedInNewSession>(outcomeReady.await())
        val child = childFactory.sessions.single { it.id == opened.sessionId }
        testScheduler.runCurrent()
        assertEquals(SessionState.Running, child.state.value)

        // The requester stops; the child session keeps its own scope and keeps running.
        host.requestStop()
        testScheduler.runCurrent()
        assertEquals(SessionState.Running, child.state.value)

        child.requestStop()
        childScope.cancel()
        testScheduler.runCurrent()
    }

    @Test
    fun sessionTerminationClosesTheWindowManager() = runTest {
        val childScope = childScope()
        val managerSeen = CompletableDeferred<WebHostWindowManager>()

        val host = assertIs<KadreResult.Success<KadreSession>>(
            WebHostSession(ChildPort(Any()), WebHostRegistry()).attach(
                this,
                KadreApplicationFactory {
                    KadreApplication {
                        managerSeen.complete(windows as WebHostWindowManager)
                        awaitCancellation()
                    }
                },
                KadrePolicies.Default,
                windowProvider = FixedProvider(KadreResult.Success(offerFor(childScope))),
                childSessionFactory = ProductionChildFactory(),
                windowHostProbe = FixedProbe(healthyChecks()),
            ),
        ).value
        testScheduler.runCurrent()
        val windows = managerSeen.await()

        host.requestStop()
        testScheduler.runCurrent()

        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host)),
            windows.requestWindow(specWithIcon()),
        )
        childScope.cancel()
        testScheduler.runCurrent()
    }

    @Test
    fun aClosedManagerRefusesClosedHost() = runTest {
        val windows = manager(provider = FixedProvider(KadreResult.Success(offerFor(CoroutineScope(SupervisorJob())))))
        windows.close()

        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host)),
            windows.requestWindow(specWithIcon()),
        )
    }

    @Test
    fun pendingBudgetIsEnforced() = runTest {
        val childScope = childScope()
        val windows = manager(
            FixedProvider(KadreResult.Success(offerFor(childScope))),
            maxPending = 1,
        )

        val first = admittedRequest(windows)
        assertEquals(
            KadreResult.Failure(
                KadreFailure.ResourceLimitExceeded(KadreResourceKind.WindowRequest, 1L),
            ),
            windows.requestWindow(specWithIcon()),
        )

        // The caller owns the request it was handed: releasing it frees the budget slot it held.
        first.close()
        assertIs<KadreResult.Success<WindowRequest>>(windows.requestWindow(specWithIcon()))
        first.close() // idempotent

        childScope.cancel()
        testScheduler.runCurrent()
    }

    @Test
    fun theManagerStateHasNoWindowOfItsOwn() {
        val windows = manager(provider = FixedProvider(KadreResult.Success(offerFor(CoroutineScope(SupervisorJob())))))
        val state = windows.state.value

        assertNull(state.primary)
        assertTrue(state.windows.isEmpty())
        assertEquals(0L, state.revision.value)
    }

    // -- fixtures --------------------------------------------------------------------------------------------

    private fun TestScope.childScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
}
