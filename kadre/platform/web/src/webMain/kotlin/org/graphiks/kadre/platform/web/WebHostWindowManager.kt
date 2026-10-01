package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.surface.BinaryImage
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

/**
 * The domain every provider-related failure carries, as `OPERATION-CONTRACTS.md` §4 names it: the
 * callback's own exceptions and the failures it returned out of the closed set are both failures of
 * the provider, and both land in this domain.
 */
internal const val WEB_WINDOW_PROVIDER_DOMAIN: String = "WebWindowProvider"

/**
 * One window host a provider offered, as the web target's own opaque value.
 *
 * The element is the DOM node the host already created — Kadre never creates or looks up one — but
 * this type cannot say so: `webMain` is DOM-free, and the element is unpacked only by the per-target
 * child session factory ([WebChildSessionFactory], plan decision D5). Everything the validation
 * ladder needs to know about the element and its scope travels as data, computed by the per-target
 * probe ([WebWindowHostChecks]), never as a DOM type.
 */
internal class WebHostWindowOffer(
    internal val element: Any,
    internal val parentScope: CoroutineScope,
    internal val attachmentPolicy: WebAttachmentPolicy,
)

/**
 * The webMain abstraction of the public, per-target [WebWindowProvider]: the same callback, carrying
 * the same closed-failure obligation, but over the opaque offer instead of a DOM-typed host.
 */
internal fun interface WebHostWindowProvider {
    fun open(requestId: WindowRequestId, spec: WindowSpec): KadreResult<WebHostWindowOffer>
}

/**
 * The DOM facts about one offered host, read by target and decided by [validateWindowHostChecks].
 *
 * `distinctDefaultView` folds the two ownerDocument readings the contract refuses — no browsing
 * context at all, and the origin's own browsing context — into one boolean, because both produce the
 * same `InvalidRequest("element.ownerDocument")` answer.
 */
internal data class WebWindowHostChecks(
    internal val elementConnected: Boolean,
    internal val distinctDefaultView: Boolean,
    internal val scopeHasJob: Boolean,
    internal val scopeActive: Boolean,
)

/**
 * Reads the DOM facts of one offered host. Per target by construction — only the target knows what
 * the element's document and browsing context are — and the manager consumes the answer as data.
 */
internal fun interface WebWindowHostProbe {
    fun probe(offer: WebHostWindowOffer): WebWindowHostChecks
}

/**
 * Opens the child session one validated offer becomes (plan decision D6).
 *
 * The per-target implementation unpacks [WebHostWindowOffer.element], builds the target's DOM port
 * and attaches a new session through the **ordinary** attach path — the same [WebHostSession.attach],
 * the same global ownership registry, one [KadreLaunchInfo] carrying
 * [org.graphiks.kadre.application.KadreLaunchReason.AdditionalHostRequested] and the request that
 * caused it. There is no second lifecycle, no second registration mechanism.
 */
internal fun interface WebChildSessionFactory {
    fun open(offer: WebHostWindowOffer, requestId: WindowRequestId): KadreResult<KadreSession>
}

/**
 * The validation ladder of a successfully offered host, in the order `OPERATION-CONTRACTS.md` §4
 * writes it: the first failing rung is the only answer.
 *
 * - a disconnected element under [WebAttachmentPolicy.StopWhenDetached] is `InvalidRequest("element")`
 *   — under [WebAttachmentPolicy.Manual] a detached host is admissible by definition, and the rung
 *   does not apply;
 * - an element without a distinct browsing context (folded by the probe into
 *   [WebWindowHostChecks.distinctDefaultView]) is `InvalidRequest("element.ownerDocument")`;
 * - a scope without a `Job` is `InvalidRequest("parentScope")`;
 * - an inactive scope is `ParentScopeCancelled` — describing only ever the new host's scope, never
 *   the requester's.
 *
 * `null` means the offer is admitted: the child session may open.
 */
internal fun validateWindowHostChecks(
    checks: WebWindowHostChecks,
    stopWhenDetached: Boolean,
): KadreFailure? = when {
    stopWhenDetached && !checks.elementConnected -> KadreFailure.InvalidRequest("element")
    !checks.distinctDefaultView -> KadreFailure.InvalidRequest("element.ownerDocument")
    !checks.scopeHasJob -> KadreFailure.InvalidRequest("parentScope")
    !checks.scopeActive -> KadreFailure.ParentScopeCancelled
    else -> null
}

/**
 * Whether [failure] is one the closed set of `WindowRequestOutcome.Rejected.failure` admits
 * (`OPERATION-CONTRACTS.md` §4), where the contract's `Busy(Host)` is `AlreadyInUse(Host)`,
 * `Limit(Window)` is `ResourceLimitExceeded(Window)`, `Temporary` is `TemporarilyUnavailable` and
 * `Platform` is any [KadreFailure.PlatformFailure]. A failure whose *kind* is not in the set — or
 * whose kind names another resource than the set admits — is refused, and the manager reports it as
 * `invalid-failure` rather than leaking it.
 */
internal fun isAdmittedWindowRequestFailure(failure: KadreFailure): Boolean = when (failure) {
    is KadreFailure.Unsupported -> failure.operation == KadreOperation.RequestWindow
    is KadreFailure.PermissionDenied -> false
    is KadreFailure.UserCancelled -> false
    is KadreFailure.TemporarilyUnavailable -> true
    is KadreFailure.InvalidRequest -> true
    is KadreFailure.AlreadyInUse -> failure.resource == KadreResourceKind.Host
    is KadreFailure.Closed -> failure.resource == KadreResourceKind.Host
    is KadreFailure.ResourceLimitExceeded -> failure.resource == KadreResourceKind.Window
    is KadreFailure.SourceOverflow -> false
    is KadreFailure.StaleRevision -> false
    is KadreFailure.InteractionRequired -> true
    is KadreFailure.UnsupportedPolicy -> false
    KadreFailure.ParentScopeCancelled -> true
    is KadreFailure.ShutdownTimedOut -> false
    is KadreFailure.SourceLost -> false
    KadreFailure.ApplicationFailure -> false
    is KadreFailure.PlatformFailure -> true
}

/**
 * The window manager of a web host session that carries a [WebWindowProvider].
 *
 * It is written on the model of the common [WindowManager] — capabilities, admitted requests,
 * terminal outcomes — but it is this target's own small machine, not a fork of the reference
 * `RuntimeWindowManager` (plan decision D5): Web exercises no `OpenedHere` path and no window
 * commit, so the machine it needs is the provider ladder and nothing else.
 *
 * The flow of one admitted request, all synchronous, all arriving as **outcomes of the request**
 * and never as failures of the outer call:
 *
 * 1. admission — a closed manager refuses with the direct failure `Closed(Host)`; a manager whose
 *    outstanding-request budget is exhausted refuses with the direct failure
 *    `ResourceLimitExceeded(WindowRequest)`. The admitted request holds its budget slot until its
 *    requester releases it ([WindowRequest.close]), which is what makes the budget a real bound on
 *    the requests callers own;
 * 2. without a provider the request terminates exactly as the unsupported manager's does —
 *    `Rejected(Unsupported(RequestWindow))` — and nothing else in this file runs;
 * 3. the provider is consulted synchronously with a copy of the spec ([WindowSpec.dtoCopyForProvider],
 *    decision D7) inside a `runCatching`: a throw becomes
 *    `PlatformFailure(Web, "WebWindowProvider", "callback-exception")` and a returned failure out of
 *    the closed set becomes `PlatformFailure(Web, "WebWindowProvider", "invalid-failure")`;
 * 4. the offered host is probed per target and validated by [validateWindowHostChecks];
 * 5. a validated host becomes a child session through [WebChildSessionFactory] — the ordinary attach
 *    path — and an attach failure the closed set admits (an element already owned by a live session,
 *    say, reported as `AlreadyInUse(Host)`) is the request's rejection.
 *
 * The request semantics are the reference's, degenerate because the outcome is already known when
 * the caller first sees the request: `cancel()` answers
 * [WindowCancellationOutcome.AlreadyTerminated] like the unsupported request does, `await()` returns
 * the terminal outcome, and `close()` is a non-blocking release that leaves the outcome alone. When
 * the session terminates, the manager closes and refuses every later admission with `Closed(Host)`.
 */
internal class WebHostWindowManager(
    policy: KadrePolicy,
    private val nextRequestId: () -> WindowRequestId,
    private val provider: WebHostWindowProvider?,
    private val childSessionFactory: WebChildSessionFactory,
    private val probe: WebWindowHostProbe,
) : WindowManager {
    private val lock = RuntimeLock()
    private var closed: Boolean = false
    private val outstanding = linkedMapOf<WindowRequestId, WebHostWindowRequest>()
    private val maxPendingWindowRequests = policy.resources.maxPendingWindowRequests

    private val mutableState = MutableStateFlow(
        WindowManagerState(
            primary = null,
            windows = emptyList(),
            capabilities = WindowManagerCapabilities(initialCapabilities(provider)),
            revision = WindowManagerRevision(0),
        ),
    )

    /** The window capability this manager publishes, decided once at construction by the provider. */
    private fun initialCapabilities(provider: WebHostWindowProvider?): Capability<Set<WindowCreationMode>> =
        when (provider) {
            null -> Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RequestWindow))
            else -> Capability.Supported(
                setOf(WindowCreationMode.OpenedInNewSession),
                FeatureAvailability.Available,
            )
        }

    override val state: StateFlow<WindowManagerState> = mutableState.asStateFlow()

    override suspend fun requestWindow(spec: WindowSpec): KadreResult<WindowRequest> {
        val request = lock.withLock {
            when {
                closed -> return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host))
                outstanding.size >= maxPendingWindowRequests -> return KadreResult.Failure(
                    KadreFailure.ResourceLimitExceeded(
                        KadreResourceKind.WindowRequest,
                        maxPendingWindowRequests.toLong(),
                    ),
                )

                else -> {
                    val created = WebHostWindowRequest(nextRequestId())
                    outstanding[created.id] = created
                    created
                }
            }
        }

        // The outcome is computed and published before the caller ever sees the request: a web
        // provider is synchronous, so there is no handoff window in which the caller could cancel
        // the request out of a pending state — the very shape the unsupported request already has.
        val outcome = resolveOutcome(request.id, spec)
        request.terminate(outcome)
        return KadreResult.Success(request)
    }

    /**
     * The session terminated: every later admission is refused, and requests the callers still own
     * stay exactly as terminal as they already were.
     */
    fun close() {
        val released = lock.withLock {
            if (closed) return
            closed = true
            outstanding.keys.toList()
        }
        released.forEach { requestId -> release(requestId) }
    }

    /** Releases the budget slot of one request; the requester's own `close()` arrives here. */
    private fun release(requestId: WindowRequestId) {
        lock.withLock { outstanding.remove(requestId) }
    }

    /** The one ladder of an admitted request, from the provider call to the child session. */
    private fun resolveOutcome(requestId: WindowRequestId, spec: WindowSpec): WindowRequestOutcome {
        val localProvider = provider
            ?: return WindowRequestOutcome.Rejected(KadreFailure.Unsupported(KadreOperation.RequestWindow))

        val offered = try {
            when (val result = localProvider.open(requestId, spec.dtoCopyForProvider())) {
                is KadreResult.Success -> result.value
                is KadreResult.Failure ->
                    return WindowRequestOutcome.Rejected(providerFailure(result.reason))
            }
        } catch (cause: Throwable) {
            // The provider is host code invoked synchronously from this call: its exceptions are
            // captured here and become the failure the contract promises, never an escape.
            return WindowRequestOutcome.Rejected(callbackExceptionFailure())
        }

        val checks = probe.probe(offered)
        val validation = validateWindowHostChecks(checks, offered.attachmentPolicy == WebAttachmentPolicy.StopWhenDetached)
        if (validation != null) return WindowRequestOutcome.Rejected(validation)

        return when (val attached = childSessionFactory.open(offered, requestId)) {
            is KadreResult.Success -> WindowRequestOutcome.OpenedInNewSession(attached.value.id)
            is KadreResult.Failure -> WindowRequestOutcome.Rejected(providerFailure(attached.reason))
        }
    }

    /** The closed-set gate: an admitted failure passes through, any other is reported over. */
    private fun providerFailure(failure: KadreFailure): KadreFailure =
        if (isAdmittedWindowRequestFailure(failure)) {
            failure
        } else {
            KadreFailure.PlatformFailure(KadrePlatform.Web, WEB_WINDOW_PROVIDER_DOMAIN, "invalid-failure")
        }

    private fun callbackExceptionFailure(): KadreFailure =
        KadreFailure.PlatformFailure(KadrePlatform.Web, WEB_WINDOW_PROVIDER_DOMAIN, "callback-exception")

    /**
     * The request a caller owns. Its outcome is published before [WebHostWindowManager.requestWindow]
     * returns, so the reference's cancel/close/await semantics degenerate honestly: cancellation is
     * always `AlreadyTerminated`, awaiting never suspends, and closing only releases what the caller
     * held.
     */
    private inner class WebHostWindowRequest(
        override val id: WindowRequestId,
    ) : WindowRequest {
        private val mutableState = MutableStateFlow<WindowRequestState>(WindowRequestState.Pending)

        override val state: StateFlow<WindowRequestState> = mutableState.asStateFlow()

        override fun close() {
            release(id)
        }

        override suspend fun cancel(): WindowCancellationOutcome {
            val outcome = terminalOutcome()
            if (outcome != null) return WindowCancellationOutcome.AlreadyTerminated(outcome)
            // Unreachable with the synchronous outcome computation, but total: a request nobody
            // terminated is cancelled pre-handoff, exactly as the reference closes one the caller
            // abandoned before the handoff.
            val cancelled = WindowRequestOutcome.Cancelled
            terminate(cancelled)
            return WindowCancellationOutcome.CancelledBeforeCommit
        }

        override suspend fun await(): WindowRequestOutcome =
            state.filterIsInstance<WindowRequestState.Terminated>().first().outcome

        fun terminate(outcome: WindowRequestOutcome) {
            val current = mutableState.value
            if (current is WindowRequestState.Terminated) return
            mutableState.value = WindowRequestState.Terminated(outcome)
        }

        private fun terminalOutcome(): WindowRequestOutcome? =
            (mutableState.value as? WindowRequestState.Terminated)?.outcome
    }
}

/**
 * The copy of a spec the provider receives (decision D7).
 *
 * `WindowSpec` is immutable, so a `copy` carries every field safely; the icon is rewrapped because
 * it is the one field holding a byte array, and the provider must receive bytes nobody else can
 * correlate with the requester's own image.
 */
internal fun WindowSpec.dtoCopyForProvider(): WindowSpec = copy(
    icon = icon?.let { BinaryImage(it.bytes, it.format, it.pixelSize) },
)
