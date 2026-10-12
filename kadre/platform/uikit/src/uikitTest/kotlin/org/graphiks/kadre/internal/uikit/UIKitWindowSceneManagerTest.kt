package org.graphiks.kadre.internal.uikit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionId
import org.graphiks.kadre.application.SessionOutcome
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
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.platform.uikit.UIKitWindowSceneProvider
import org.graphiks.kadre.platform.uikit.UIKitWindowSceneRequestResolver
import org.graphiks.kadre.platform.uikit.UIKitWindowSceneRequestSink
import org.graphiks.kadre.window.WindowCancellationOutcome
import org.graphiks.kadre.window.WindowCreationMode
import org.graphiks.kadre.window.WindowManager
import org.graphiks.kadre.window.WindowManagerCapabilities
import org.graphiks.kadre.window.WindowManagerRevision
import org.graphiks.kadre.window.WindowManagerState
import org.graphiks.kadre.window.WindowRequest
import org.graphiks.kadre.window.WindowRequestId
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowRequestState
import org.graphiks.kadre.window.WindowSpec
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * La machine à états des demandes de scènes (Task 1, miroir `WebHostWindowManagerTest`) : chaque
 * refus est un **outcome de la requête admise** — jamais une failure de l'appel `requestWindow` —
 * le pending est réellement asynchrone (FrontBoard, aucun timeout synthétique, roadmap l.240), et
 * la corrélation tardive ne résout jamais une requête terminale une seconde fois.
 *
 * Le noyau [UIKitWindowScenePending] et la porte fermée des failures sont testés directement ; le
 * manager l'est par ses chemins sans objet UIKit (`requestWindow`, `cancel`, `close`, `abandon`,
 * la couture de résolution `claimResolve`/`completeResolve`). Les objets `UIWindowScene`,
 * `UIWindow` et `UIView` ne se construisent pas sans process applicatif : la colle typée UIKit
 * (le `resolve` 5-arg et la construction de `UIKitWindowSceneHost` qu'il alimente) reste une
 * coquille de quelques lignes prouvée par le driver (Task 3) — le même partage kernel/binding que
 * `UIKitSurfaceObservationKernel` en phase 2.
 */
class UIKitWindowSceneManagerTest {

    // -- doubles ---------------------------------------------------------------------------------------------

    /** Le manager runtime délégué — un état figé, un compteur de close ; rien d'autre n'est touché. */
    private class FakeRuntimeManager : WindowManager {
        var closeCalls = 0
        private val mutableState = MutableStateFlow(
            WindowManagerState(
                primary = null,
                windows = emptyList(),
                capabilities = WindowManagerCapabilities(
                    Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RequestWindow)),
                ),
                revision = WindowManagerRevision(0),
            ),
        )

        override val state: StateFlow<WindowManagerState> = mutableState.asStateFlow()

        override suspend fun requestWindow(spec: WindowSpec): KadreResult<WindowRequest> =
            error("the scene manager must not delegate requestWindow")

        fun close() {
            closeCalls += 1
        }
    }

    /** Une session enfant factice — la seule chose qui compte : les demandes d'arrêt enregistrées. */
    private class FakeSession(override val id: SessionId) : KadreSession {
        val stops = mutableListOf<String>()
        private val mutableState = MutableStateFlow<SessionState>(SessionState.Running)

        override val state: StateFlow<SessionState> = mutableState.asStateFlow()

        override fun close() {
            stops += "close"
        }

        override fun requestStop() {
            stops += "requestStop"
        }

        override suspend fun awaitTermination(): SessionOutcome = SessionOutcome.Completed
    }

    /** Un provider double — enregistre chaque `(requestId, spec)` ou jette une exception fixe. */
    private class RecordingProvider(
        private val exception: Throwable? = null,
    ) : UIKitWindowSceneProvider {
        val opened = mutableListOf<Pair<WindowRequestId, WindowSpec>>()

        override fun open(requestId: WindowRequestId, spec: WindowSpec) {
            opened += requestId to spec
            exception?.let { throw it }
        }
    }

    /** Le sink double du resolver — prouve le routage app-side de `abandon`. */
    private class RecordingSink : UIKitWindowSceneRequestSink {
        val abandoned = mutableListOf<Pair<WindowRequestId, String>>()

        override fun resolve(
            requestId: WindowRequestId,
            windowScene: UIWindowScene,
            window: UIWindow,
            surfaceView: UIView,
        ) = error("resolve needs live UIKit objects; it is driver-proven (Task 3)")

        override fun abandon(requestId: WindowRequestId, reason: String) {
            abandoned += requestId to reason
        }
    }

    // -- helpers ---------------------------------------------------------------------------------------------

    private fun policyWith(maxPending: Int): KadrePolicy = KadrePolicies.Default.copy(
        resources = KadrePolicies.Default.resources.copy(maxPendingWindowRequests = maxPending),
    )

    private fun manager(
        provider: UIKitWindowSceneProvider?,
        maxPending: Int = 4,
        childSessionFactory: (UIKitWindowSceneHost) -> KadreResult<KadreSession> = { _ ->
            error("unit tests attach children through completeResolve")
        },
    ): Pair<UIKitWindowSceneManager, FakeRuntimeManager> {
        val runtime = FakeRuntimeManager()
        val scenes = UIKitWindowSceneManager(
            runtimeManager = runtime,
            provider = provider,
            policy = policyWith(maxPending),
            applicationFactory = { error("Task 2 wires the real child factory") },
            failureReporter = {},
            parentScope = CoroutineScope(Dispatchers.Main),
            childSessionFactory = childSessionFactory,
        )
        return scenes to runtime
    }

    private fun fakeChildSession(): FakeSession = FakeSession(SessionId(nextTestId++))

    private fun outcomeOf(request: WindowRequest): WindowRequestOutcome =
        assertIs<WindowRequestState.Terminated>(request.state.value).outcome

    private suspend fun awaitOutcome(request: WindowRequest): WindowRequestOutcome =
        request.state.filterIsInstance<WindowRequestState.Terminated>().first().outcome

    private suspend fun admittedRequest(windows: UIKitWindowSceneManager): WindowRequest =
        assertIs<KadreResult.Success<WindowRequest>>(windows.requestWindow(WindowSpec())).value

    // -- la demande sans provider (tvOS, verbatim) -----------------------------------------------------------

    @Test
    fun requestWindowWithoutProviderIsATerminalRejectedRequestBeforeAnyReservation() = runBlocking {
        // maxPending = 1 : si la première demande réservait un slot, la seconde serait refusée
        // (`ResourceLimitExceeded`) — deux succès prouvent que le chemin null-provider décide
        // AVANT toute réservation (web l.265-266).
        val (windows, _) = manager(provider = null, maxPending = 1)

        val first = assertIs<KadreResult.Success<WindowRequest>>(windows.requestWindow(WindowSpec())).value
        val second = assertIs<KadreResult.Success<WindowRequest>>(windows.requestWindow(WindowSpec())).value

        val outcome = WindowRequestOutcome.Rejected(KadreFailure.Unsupported(KadreOperation.RequestWindow))
        assertEquals(outcome, first.await())
        assertEquals(outcome, outcomeOf(first))
        assertEquals(outcome, second.await())
        assertEquals(WindowCancellationOutcome.AlreadyTerminated(outcome), first.cancel())
        first.close()
        second.close()
    }

    @Test
    fun theWindowManagerCapabilityIsOpenedInNewSessionOnlyWhenAProviderIsConfigured() {
        val (withProvider, _) = manager(provider = RecordingProvider())
        val supported = assertIs<Capability.Supported<Set<WindowCreationMode>>>(
            withProvider.state.value.capabilities.requestWindow,
        )
        assertEquals(setOf(WindowCreationMode.OpenedInNewSession), supported.constraints)
        assertEquals(FeatureAvailability.Available, supported.availability)

        val (withoutProvider, _) = manager(provider = null)
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RequestWindow)),
            withoutProvider.state.value.capabilities.requestWindow,
        )
    }

    @Test
    fun thePublishedStateKeepsTheRuntimePrimaryAndWindows() {
        // La seule substitution est la capability : primaire et fenêtres restent celles du runtime
        // (la primaire ne vit JAMAIS dans ce manager — aucune duplication de reducer).
        val (windows, runtime) = manager(provider = RecordingProvider())
        assertEquals(runtime.state.value.primary, windows.state.value.primary)
        assertEquals(runtime.state.value.windows, windows.state.value.windows)
        assertEquals(runtime.state.value.revision, windows.state.value.revision)
    }

    // -- l'admission et le provider --------------------------------------------------------------------------

    @Test
    fun theProviderReceivesTheRequestIdAndTheSpec() = runBlocking {
        val provider = RecordingProvider()
        val (windows, _) = manager(provider)
        val spec = WindowSpec(title = "scene")

        val request = assertIs<KadreResult.Success<WindowRequest>>(windows.requestWindow(spec)).value

        val (providerRequestId, providerSpec) = provider.opened.single()
        assertEquals(request.id, providerRequestId)
        assertEquals(spec, providerSpec)
        // La demande reste Pending : aucun timeout synthétique, l'outcome viendra à la connexion.
        assertEquals(WindowRequestState.Pending, request.state.value)

        windows.close()
    }

    @Test
    fun aProviderExceptionBecomesTheCallbackExceptionOutcome() = runBlocking {
        val (windows, _) = manager(
            RecordingProvider(exception = IllegalStateException("scene activation blew up")),
        )

        val request = admittedRequest(windows)

        assertEquals(
            WindowRequestOutcome.Rejected(
                KadreFailure.PlatformFailure(KadrePlatform.UIKit, UIKitWindowProviderDomain, "callback-exception"),
            ),
            request.await(),
        )
        // Le slot reste détenu jusqu'au geste du requérant (miroir web) : close() le libère.
        request.close()
        assertIs<KadreResult.Success<WindowRequest>>(windows.requestWindow(WindowSpec()))
        windows.close()
    }

    @Test
    fun aProviderThrowRetainsItsSlotUntilTheRequesterReleasesIt() = runBlocking {
        val (windows, _) = manager(
            RecordingProvider(exception = IllegalStateException("activation refused")),
            maxPending = 1,
        )

        val request = assertIs<KadreResult.Success<WindowRequest>>(windows.requestWindow(WindowSpec())).value
        assertEquals(
            WindowRequestOutcome.Rejected(
                KadreFailure.PlatformFailure(KadrePlatform.UIKit, UIKitWindowProviderDomain, "callback-exception"),
            ),
            request.await(),
        )

        // Rétention web-verbatim : la demande terminée à l'handoff RETIENT son slot tant que le
        // requérant ne l'a pas libéré — c'est ce qui rend la garde du resolve tardif nécessaire.
        assertEquals(
            KadreFailure.ResourceLimitExceeded(KadreResourceKind.WindowRequest, 1L),
            assertIs<KadreResult.Failure>(windows.requestWindow(WindowSpec())).reason,
        )
        request.close()
        assertIs<KadreResult.Success<WindowRequest>>(windows.requestWindow(WindowSpec()))
        windows.close()
    }

    @Test
    fun aResolveAfterProviderThrowNeverAttachesAChildAndNeverOverwritesTheOutcome() = runBlocking {
        val child = fakeChildSession()
        var factoryCalls = 0
        val (windows, _) = manager(
            RecordingProvider(exception = IllegalStateException("activation blew up")),
            childSessionFactory = { _ ->
                factoryCalls += 1
                KadreResult.Success(child)
            },
        )

        val request = assertIs<KadreResult.Success<WindowRequest>>(windows.requestWindow(WindowSpec())).value
        val rejected = WindowRequestOutcome.Rejected(
            KadreFailure.PlatformFailure(KadrePlatform.UIKit, UIKitWindowProviderDomain, "callback-exception"),
        )
        assertEquals(rejected, request.await())

        // Présence dans la carte ≠ état de la requête : l'entrée est retenue, la requête est
        // déjà terminale — la résolution tardive doit être refusée AVANT tout attach d'enfant.
        assertNull(windows.claimResolve(request.id))
        assertEquals(0, factoryCalls)
        assertEquals(rejected, request.await())

        // Le pire cas — un resolve déjà engagé quand la terminaison est tombée (l'entrée
        // réclamée avant le throw) : l'attach éventuel est STOPPÉ, l'outcome reste Rejected.
        val retainedEntry = UIKitWindowScenePending.Entry(
            id = request.id,
            spec = WindowSpec(),
            request = request,
            resolver = UIKitWindowSceneRequestResolver.issue(request.id, RecordingSink()),
        )
        windows.completeResolve(retainedEntry) { KadreResult.Success(child) }

        assertEquals(rejected, request.await())
        assertEquals(listOf("requestStop"), child.stops)
        windows.close()
    }

    @Test
    fun terminateIsAtomicAndTheFirstPublishedOutcomeWins() = runBlocking {
        val (windows, _) = manager(RecordingProvider())
        val request = assertIs<UIKitSceneWindowRequest>(
            assertIs<KadreResult.Success<WindowRequest>>(windows.requestWindow(WindowSpec())).value,
        )

        assertTrue(request.terminate(WindowRequestOutcome.Cancelled))
        // Un second écrivain (hors lock : drain du close, abandon, throw du provider) perd le
        // CAS et ne réécrit plus jamais l'outcome publié — le check-then-act non atomique
        // laissait le dernier écrivain retourner l'outcome (sûr en JS, pas sur Kotlin/Native).
        assertFalse(request.terminate(WindowRequestOutcome.RequesterDetached))
        assertEquals(WindowRequestOutcome.Cancelled, request.await())
        windows.close()
    }

    @Test
    fun pendingBudgetIsEnforcedAndCancellationReleasesTheSlot() = runBlocking {
        val (windows, _) = manager(RecordingProvider(), maxPending = 1)

        val first = admittedRequest(windows)
        assertEquals(
            KadreResult.Failure(KadreFailure.ResourceLimitExceeded(KadreResourceKind.WindowRequest, 1L)),
            windows.requestWindow(WindowSpec()),
        )

        // L'annulation d'une demande pending termine `Cancelled` ET libère le slot (Review Focus 1) :
        // une nouvelle demande redevient possible immédiatement.
        assertEquals(WindowCancellationOutcome.CancelledBeforeCommit, first.cancel())
        assertEquals(WindowRequestOutcome.Cancelled, awaitOutcome(first))
        assertIs<KadreResult.Success<WindowRequest>>(windows.requestWindow(WindowSpec()))
        windows.close()
    }

    @Test
    fun aClosedManagerRefusesClosedHost() = runBlocking {
        val (windows, _) = manager(RecordingProvider())
        windows.close()

        assertEquals(
            KadreFailure.Closed(KadreResourceKind.Host),
            assertIs<KadreResult.Failure>(windows.requestWindow(WindowSpec())).reason,
        )
    }

    // -- la fermeture côté requérant -------------------------------------------------------------------------

    @Test
    fun closingAPendingRequestDetachesItAndReleasesTheSlot() = runBlocking {
        val (windows, _) = manager(RecordingProvider())

        val first = admittedRequest(windows)
        first.close()

        assertEquals(WindowRequestOutcome.RequesterDetached, awaitOutcome(first))
        assertEquals(
            WindowCancellationOutcome.AlreadyTerminated(WindowRequestOutcome.RequesterDetached),
            first.cancel(),
        )
        // Le slot est libéré : la demande suivante est admise.
        assertIs<KadreResult.Success<WindowRequest>>(windows.requestWindow(WindowSpec()))
        windows.close()
    }

    @Test
    fun closingATerminatedRequestOnlyReleasesTheSlot() = runBlocking {
        val (windows, _) = manager(RecordingProvider())
        val child = fakeChildSession()
        val request = admittedRequest(windows)

        // La demande est résolue (terminale) : close() ne touche plus à l'outcome, il libère.
        val entry = assertIs<UIKitWindowScenePending.Entry>(windows.claimResolve(request.id))
        windows.completeResolve(entry) { KadreResult.Success(child) }
        assertEquals(WindowRequestOutcome.OpenedInNewSession(child.id), awaitOutcome(request))

        request.close()
        assertEquals(WindowRequestOutcome.OpenedInNewSession(child.id), outcomeOf(request))
        assertIs<KadreResult.Success<WindowRequest>>(windows.requestWindow(WindowSpec()))
        windows.close()
    }

    // -- la corrélation (resolve) ----------------------------------------------------------------------------

    @Test
    fun resolveAttachesTheChildAndOpensInNewSession() = runBlocking {
        val (windows, _) = manager(RecordingProvider())
        val child = fakeChildSession()

        val request = admittedRequest(windows)
        val entry = assertIs<UIKitWindowScenePending.Entry>(windows.claimResolve(request.id))
        windows.completeResolve(entry) { KadreResult.Success(child) }

        assertEquals(WindowRequestOutcome.OpenedInNewSession(child.id), awaitOutcome(request))
        // L'enfant vivant n'est PAS arrêté — sa session est indépendante (roadmap l.240).
        assertTrue(child.stops.isEmpty())
        // Le slot est libéré par la terminaison : une nouvelle demande est admise.
        assertIs<KadreResult.Success<WindowRequest>>(windows.requestWindow(WindowSpec()))
        windows.close()
    }

    @Test
    fun resolveAfterTerminalIsANoOp() = runBlocking {
        val (windows, _) = manager(RecordingProvider())
        val child = fakeChildSession()

        val request = admittedRequest(windows)
        assertEquals(WindowCancellationOutcome.CancelledBeforeCommit, request.cancel())

        // Connexion tardive après cancellation : `claimResolve` ne rend plus rien — l'attach
        // n'a jamais lieu et l'outcome reste le terminal d'origine (roadmap l.239-240).
        assertNull(windows.claimResolve(request.id))
        assertEquals(WindowRequestOutcome.Cancelled, awaitOutcome(request))
        assertTrue(child.stops.isEmpty())
        windows.close()
    }

    @Test
    fun resolveRacingCancellationDetachesTheJustAttachedChild() = runBlocking {
        val (windows, _) = manager(RecordingProvider())
        val child = fakeChildSession()

        val request = admittedRequest(windows)
        val entry = windows.claimResolve(request.id)
        // La factory est « en vol » quand la cancellation tombe : l'enfant qui vient de s'attacher
        // est une ressource sans owner — il est détaché proprement, l'outcome reste `Cancelled`.
        assertEquals(WindowCancellationOutcome.CancelledBeforeCommit, request.cancel())
        windows.completeResolve(assertIs(entry)) { KadreResult.Success(child) }

        assertEquals(WindowRequestOutcome.Cancelled, awaitOutcome(request))
        assertEquals(listOf("requestStop"), child.stops)
        windows.close()
    }

    @Test
    fun doubleResolveIsANoOpAndTheOutcomeStands() = runBlocking {
        val (windows, _) = manager(RecordingProvider())
        val firstChild = fakeChildSession()
        val secondChild = fakeChildSession()

        val request = admittedRequest(windows)
        val entry = assertIs<UIKitWindowScenePending.Entry>(windows.claimResolve(request.id))
        windows.completeResolve(entry) { KadreResult.Success(firstChild) }
        // Une seconde résolution de la MÊME entrée (double callback du provider) : l'outcome ne
        // bouge pas, l'enfant tardif est détaché sans jamais devenir la réponse.
        windows.completeResolve(entry) { KadreResult.Success(secondChild) }

        assertEquals(WindowRequestOutcome.OpenedInNewSession(firstChild.id), awaitOutcome(request))
        assertTrue(firstChild.stops.isEmpty())
        assertEquals(listOf("requestStop"), secondChild.stops)
        windows.close()
    }

    @Test
    fun aChildAttachFailureInTheClosedSetIsPassedThrough() = runBlocking {
        val (windows, _) = manager(RecordingProvider())

        val request = admittedRequest(windows)
        val entry = assertIs<UIKitWindowScenePending.Entry>(windows.claimResolve(request.id))
        windows.completeResolve(entry) { KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host)) }

        assertEquals(
            WindowRequestOutcome.Rejected(KadreFailure.AlreadyInUse(KadreResourceKind.Host)),
            awaitOutcome(request),
        )
        windows.close()
    }

    @Test
    fun aChildAttachFailureOutOfTheSetBecomesInvalidFailure() = runBlocking {
        val (windows, _) = manager(RecordingProvider())

        val request = admittedRequest(windows)
        val entry = assertIs<UIKitWindowScenePending.Entry>(windows.claimResolve(request.id))
        // `Closed(Surface)` n'est pas dans l'ensemble fermé (`Closed(Host)` l'est) : la porte le
        // refuse et publie le `invalid-failure` de la plateforme, jamais la failure brute.
        windows.completeResolve(entry) { KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface)) }

        assertEquals(
            WindowRequestOutcome.Rejected(
                KadreFailure.PlatformFailure(KadrePlatform.UIKit, UIKitWindowProviderDomain, "invalid-failure"),
            ),
            awaitOutcome(request),
        )
        windows.close()
    }

    @Test
    fun closingTheManagerTerminatesEveryPendingRequesterDetached() = runBlocking {
        val (windows, _) = manager(RecordingProvider())
        val first = admittedRequest(windows)
        val second = admittedRequest(windows)

        windows.close()

        assertEquals(WindowRequestOutcome.RequesterDetached, awaitOutcome(first))
        assertEquals(WindowRequestOutcome.RequesterDetached, awaitOutcome(second))
        assertEquals(
            KadreFailure.Closed(KadreResourceKind.Host),
            assertIs<KadreResult.Failure>(windows.requestWindow(WindowSpec())).reason,
        )
        windows.close() // idempotent
    }

    // -- l'ensemble fermé des failures -----------------------------------------------------------------------

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
            KadreFailure.PlatformFailure(KadrePlatform.UIKit, "host", "anything"),
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

    // -- le resolver public ----------------------------------------------------------------------------------

    @Test
    fun theResolverRoutesAbandonToItsSinkWithItsOwnId() {
        val sink = RecordingSink()
        val resolver = UIKitWindowSceneRequestResolver.issue(WindowRequestId(41L), sink)

        resolver.abandon("scene-never-connected")

        assertEquals(listOf(WindowRequestId(41L) to "scene-never-connected"), sink.abandoned)
    }

    @Test
    fun theManagerAnswersAbandonWithCancelledWhilePendingAndIgnoresItAfterTermination() = runBlocking {
        val (windows, _) = manager(RecordingProvider())

        // Pendante : l'abandon app-side est `Cancelled` — un discard explicite à outcome fermé.
        val pending = admittedRequest(windows)
        windows.abandon(pending.id, "activation-refused")
        assertEquals(WindowRequestOutcome.Cancelled, awaitOutcome(pending))

        // Terminale (ici `OpenedInNewSession`) : l'abandon tardif est un no-op.
        val child = fakeChildSession()
        val resolved = admittedRequest(windows)
        val entry = assertIs<UIKitWindowScenePending.Entry>(windows.claimResolve(resolved.id))
        windows.completeResolve(entry) { KadreResult.Success(child) }
        windows.abandon(resolved.id, "activation-refused")
        assertEquals(WindowRequestOutcome.OpenedInNewSession(child.id), awaitOutcome(resolved))
        windows.close()
    }

    // -- le noyau de pending ---------------------------------------------------------------------------------

    private fun kernel(maxPending: Int): UIKitWindowScenePending = UIKitWindowScenePending(
        maxPendingWindowRequests = maxPending,
        nextRequestId = ::nextTestWindowRequestId,
        issueResolver = { id -> UIKitWindowSceneRequestResolver.issue(id, RecordingSink()) },
    )

    @Test
    fun pendingAdmissionRefusesClosedBeforeTheBound() {
        val pending = kernel(maxPending = 0)
        // Fermé d'abord (web l.221) : même avec une borne de 0, l'état clos est LA réponse.
        pending.drain()
        assertEquals(
            UIKitWindowScenePending.Admission.RefusedClosed,
            pending.admit(WindowSpec(), ::stubEntryRequest),
        )
    }

    @Test
    fun pendingClaimKeepsTheEntryUntilSettleRemovesIt() {
        val pending = kernel(maxPending = 2)
        val admitted = assertIs<UIKitWindowScenePending.Admission.Admitted>(
            pending.admit(WindowSpec(), ::stubEntryRequest),
        )

        // Le claim NE retire pas l'entrée : le budget reste détenu pendant l'attach de l'enfant.
        assertSame(admitted.entry, pending.claim(admitted.entry.id))
        assertSame(admitted.entry, pending.claim(admitted.entry.id))

        // Le settle retire ; un second settle ne trouve plus rien (course cancel/resolve).
        assertSame(admitted.entry, pending.settle(admitted.entry.id))
        assertNull(pending.settle(admitted.entry.id))
        assertNull(pending.claim(admitted.entry.id))

        // remove sur un id absent : null — toutes les routes tardives sont des no-ops.
        assertNull(pending.remove(WindowRequestId(9_999L)))
    }

    @Test
    fun pendingDrainReturnsEverythingAndClosesAdmission() {
        val pending = kernel(maxPending = 3)
        val first = assertIs<UIKitWindowScenePending.Admission.Admitted>(
            pending.admit(WindowSpec(), ::stubEntryRequest),
        ).entry
        val second = assertIs<UIKitWindowScenePending.Admission.Admitted>(
            pending.admit(WindowSpec(), ::stubEntryRequest),
        ).entry

        assertEquals(setOf(first.id, second.id), pending.drain().map(UIKitWindowScenePending.Entry::id).toSet())
        assertEquals(
            UIKitWindowScenePending.Admission.RefusedClosed,
            pending.admit(WindowSpec(), ::stubEntryRequest),
        )
    }

    // -- fixtures --------------------------------------------------------------------------------------------

    private var nextTestId = 100L

    private fun nextTestSessionId(): SessionId = SessionId(nextTestId++)

    private fun nextTestWindowRequestId(): WindowRequestId = WindowRequestId(nextTestId++)

    /** La fabrique d'entrée du noyau dans les tests directs : une requête factice minimale. */
    private fun stubEntryRequest(id: WindowRequestId, resolver: UIKitWindowSceneRequestResolver): WindowRequest =
        StubWindowRequest(id, resolver)

    /** Une requête WindowRequest minimale pour les tests directs du noyau. */
    private class StubWindowRequest(
        override val id: WindowRequestId,
        val resolver: UIKitWindowSceneRequestResolver,
    ) : WindowRequest {
        private val mutableState = MutableStateFlow<WindowRequestState>(WindowRequestState.Pending)

        override val state: StateFlow<WindowRequestState> = mutableState.asStateFlow()

        override fun close() = Unit

        override suspend fun cancel(): WindowCancellationOutcome =
            WindowCancellationOutcome.AlreadyTerminated(WindowRequestOutcome.Cancelled)

        override suspend fun await(): WindowRequestOutcome = WindowRequestOutcome.Cancelled
    }
}

/**
 * Le noyau de bookkeeping en isolation (les tests du manager couvrent déjà les routes ; ceux-ci
 * figent la sémantique fine : borne, ordre closed/bound, limite reportée).
 */
class UIKitWindowScenePendingTest {
    private var nextId = 0L
    private fun nextRequestId(): WindowRequestId = WindowRequestId(nextId++)
    private fun kernel(maxPending: Int): UIKitWindowScenePending = UIKitWindowScenePending(
        maxPendingWindowRequests = maxPending,
        nextRequestId = ::nextRequestId,
        issueResolver = { id -> UIKitWindowSceneRequestResolver.issue(id, RecordingSink()) },
    )
    private fun stubRequest(): WindowRequest = StubPendingRequest()

    @Test
    fun admissionHoldsTheBoundAndReportsItsLimit() {
        val pending = kernel(maxPending = 1)
        assertIs<UIKitWindowScenePending.Admission.Admitted>(pending.admit(WindowSpec()) { _, _ -> stubRequest() })
        assertEquals(
            UIKitWindowScenePending.Admission.RefusedLimit(1),
            pending.admit(WindowSpec()) { _, _ -> stubRequest() },
        )
    }

    private class RecordingSink : UIKitWindowSceneRequestSink {
        override fun resolve(
            requestId: WindowRequestId,
            windowScene: UIWindowScene,
            window: UIWindow,
            surfaceView: UIView,
        ) = Unit

        override fun abandon(requestId: WindowRequestId, reason: String) = Unit
    }

    private class StubPendingRequest : WindowRequest {
        private val mutableState = MutableStateFlow<WindowRequestState>(WindowRequestState.Pending)

        override val id: WindowRequestId = WindowRequestId(9_999L)
        override val state: StateFlow<WindowRequestState> = mutableState.asStateFlow()

        override fun close() = Unit

        override suspend fun cancel(): WindowCancellationOutcome =
            WindowCancellationOutcome.AlreadyTerminated(WindowRequestOutcome.Cancelled)

        override suspend fun await(): WindowRequestOutcome = WindowRequestOutcome.Cancelled
    }
}
