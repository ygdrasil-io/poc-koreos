package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.ActivationState
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreDiagnostic
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.SurfaceInput
import org.graphiks.kadre.internal.runtime.RawInputPort
import org.graphiks.kadre.internal.runtime.RuntimeDropTransferBudget
import org.graphiks.kadre.internal.runtime.RuntimeEventCollectorAllocator
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeHostController
import org.graphiks.kadre.internal.runtime.RuntimePrimarySurface
import org.graphiks.kadre.internal.runtime.RuntimePrimarySurfaceConfiguration
import org.graphiks.kadre.internal.runtime.RuntimeSessionRevocationHandler
import org.graphiks.kadre.internal.runtime.RuntimeSessionObserver
import org.graphiks.kadre.internal.runtime.RuntimeSurfaceInput
import org.graphiks.kadre.internal.runtime.SurfaceStimulus
import org.graphiks.kadre.internal.runtime.UnsupportedTextInputPort
import org.graphiks.kadre.internal.runtime.admitField
import org.graphiks.kadre.internal.runtime.unsupportedSurfaceCapabilities
import org.graphiks.kadre.policy.ContinuousDelivery
import org.graphiks.kadre.policy.ContinuousOverflowAction
import org.graphiks.kadre.policy.InputDeliveryPolicy
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.policy.WindowDeliveryPolicy
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.RejectedSurfaceField
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceCapabilities
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceEvent
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceProperty
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceUpdate
import org.graphiks.kadre.surface.SurfaceUpdateOutcome
import org.graphiks.kadre.surface.SurfaceVisibility

/** A target-owned animation-frame registration that can be cancelled by the shared surface. */
internal fun interface WebFrameHandle {
    fun cancel()
}

/**
 * The target's input channel into the shared surface: one observer per port, installed once.
 *
 * It carries two members because one browser event carries two different facts. The first is the
 * observation itself — what the element saw — and it is the only member every implementation has to
 * answer; it is the abstract one, so a lambda implements a channel that observes and suppresses
 * nothing, which is what a test double and any port without a suppression seam want.
 *
 * The second is a question about the event *in hand*: may the default action that event would
 * otherwise perform be dropped? A port asks it inside that event's own callback, on that very event,
 * and applies the answer there and nowhere else. The answer belongs to the surface — it reads the
 * `inputDefaultBehavior` in effect and the category of the observation, through the pure rule of
 * `WebInputTranslation.kt` — so the port holds no policy: it applies whatever it is told, and a port
 * that is told nothing (or asks a channel that does not answer) suppresses nothing at all.
 *
 * [suppressDefaultFor] answering `false` by default is that last guarantee: suppression is never
 * implicit (`PUBLIC-API-CATALOG.md:208`), so "no answer" is "no suppression".
 */
internal fun interface WebInputObserver {
    /** Delivers one immutable observation of the element. */
    fun onObservation(stimulus: WebInputStimulus)

    /**
     * Whether the browser default of the event that carried [stimulus] must be dropped.
     *
     * Asked synchronously, within that event's own callback, and answered `false` unless the surface
     * was explicitly told to suppress this category. The observation is always delivered first: this
     * question decides what the browser does *in addition* to Kadre, never whether Kadre delivers.
     */
    fun suppressDefaultFor(stimulus: WebInputStimulus): Boolean = false
}

internal interface WebHostPort {
    val initialSnapshot: WebSurfaceMetrics

    /**
     * Stable target-owned identity used for admission.  Target ports override this with their
     * element identity; the default preserves the existing inert ports until they do so.
     */
    val stableIdentity: Any get() = this

    /** Initial browser facts copied by the target before common admission starts. */
    val initialLifecycleSnapshot: WebLifecycleSnapshot
        get() = WebLifecycleSnapshot(
            connected = true,
            inOriginDocument = true,
            documentVisible = true,
            browsingContextFocused = true,
            subtreeFocused = true,
        )

    /**
     * Installs target-owned observation after ownership has been reserved.
     * [release] removes this observer together with every other target resource.
     */
    fun installLifecycleObserver(observer: (WebLifecycleSnapshot) -> Unit) = Unit

    /**
     * Installs target-owned size and scale observation after ownership has been reserved.
     *
     * Implementations deliver at least one snapshot and may deliver duplicates; the surface
     * deduplicates. [release] removes this observer with every other target resource.
     */
    fun installMetricsObserver(observer: (WebSurfaceMetrics) -> Unit) = Unit

    /**
     * Installs target-owned input observation after ownership has been reserved.
     *
     * The observer receives one immutable, DOM-free [WebInputStimulus] per input fact the target
     * observed; a borrowed browser event never crosses this boundary. Implementations deliver in the
     * order the browser reported the facts, and may deliver nothing at all. [release] removes this
     * observer together with every other target resource.
     *
     * The same channel answers the one question a target cannot answer for itself — whether the
     * default action of the event it is holding must be dropped — so a port suppresses a browser
     * default only where the surface told it to, on the event the surface was just handed
     * ([WebInputObserver.suppressDefaultFor]).
     */
    fun installInputObserver(observer: WebInputObserver) = Unit

    /**
     * Registers [callback] for the next animation frame of the browsing context that owns the
     * element.
     *
     * A single registration admits exactly one callback, delivered asynchronously; a registration
     * that [WebFrameHandle.cancel] has consumed admits none. The default preserves the inert ports
     * that never schedule a frame.
     */
    fun scheduleFrame(callback: () -> Unit): WebFrameHandle = WebFrameHandle { }

    /**
     * The host element as an untyped reference, or null once the port released it.
     *
     * The reference is valid only while [WebElementLeasePort.lease] runs its block; the target port
     * never hands out a DOM type through this member.
     */
    val leasedElement: Any? get() = null

    fun release()
}

/**
 * DOM-free lease core for [HostSurface.withWebElement].
 *
 * The callback runs on the host context that lends the element. A lease is not reentrant: a lease
 * started while another is in flight fails with [KadreFailure.TemporarilyUnavailable] instead of
 * waiting, because a renderer re-entering its own surface is a programming error, not back
 * pressure. Once the surface stopped admitting, every later lease reports [KadreFailure.Closed],
 * with or without one in flight — a closed surface never becomes leasable again, so answering
 * `TemporarilyUnavailable(retryable = true)` there would misdescribe the retry.
 *
 * The block may suspend — the facade's callback does not — and the element reference handed to
 * [lease] is valid only until the block ends, whether it returns, throws or is cancelled.
 */
internal interface WebElementLeasePort {
    /** Runs [block] with the leased element, or fails without invoking it. */
    suspend fun <R> lease(block: suspend (Any) -> R): KadreResult<R>
}

internal class WebHostSession(
    private val port: WebHostPort,
    private val registry: WebHostRegistry = WebHostRegistry.shared,
    private val failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { },
) {
    fun attach(
        parentScope: CoroutineScope,
        applicationFactory: KadreApplicationFactory,
        policy: KadrePolicy,
        attachmentPolicy: WebAttachmentPolicy = WebAttachmentPolicy.StopWhenDetached,
    ): KadreResult<KadreSession> {
        val reducer = WebLifecycleReducer(attachmentPolicy)
        val initialLifecycle = when (val reduction = reducer.reduce(port.initialLifecycleSnapshot)) {
            is WebLifecycleReduction.Update -> reduction.state
            WebLifecycleReduction.Terminate -> return KadreResult.Failure(KadreFailure.InvalidRequest("element"))
        }
        val parentJob = parentScope.coroutineContext[Job]
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("parentScope"))
        if (!parentJob.isActive) return KadreResult.Failure(KadreFailure.ParentScopeCancelled)

        val reservation = when (val result = registry.reserve(port.stableIdentity)) {
            is KadreResult.Success -> result.value
            is KadreResult.Failure -> return result
        }
        val ownership = WebHostOwnership(port, reservation)
        var surface: WebHostSurface? = null
        // Input the target reports before this session built its surface waits here, in the order it
        // was reported: the observer is installed before the runtime creates the surface, so that
        // window is real. The stimuli are handed to the surface as soon as it exists, where they wait
        // again — still in order — for the session configuration that builds the reducer.
        val pendingInput = ArrayDeque<WebInputStimulus>()

        fun deliverInput(stimulus: WebInputStimulus) {
            val current = surface
            if (current == null) pendingInput.addLast(stimulus) else current.acceptInput(stimulus)
        }

        // The one channel of this session: the target hands its observations over through it and asks
        // it about the default of the event each observation came from. Both answers are given here,
        // where the surface lives — the target never learns what a category is, which value the policy
        // holds, or how either is decided.
        val inputChannel = object : WebInputObserver {
            override fun onObservation(stimulus: WebInputStimulus) = deliverInput(stimulus)

            override fun suppressDefaultFor(stimulus: WebInputStimulus): Boolean =
                // Input observed before the runtime built the surface waits in `pendingInput`, and the
                // question is answered `false` for it: the behaviour the decision reads is the
                // surface's own state, and a surface that does not exist has stated none. The same
                // holds for every stimulus the session derives itself (a focus loss), which no browser
                // event is waiting on.
                surface?.suppressDefaultFor(stimulus) ?: false
        }

        val controller = createController(initialLifecycle, ownership) { created ->
            surface = created
            pendingInput.forEach(created::acceptInput)
            pendingInput.clear()
        }
        // The reduced lifecycle is where this session learns activation, so it is also where a loss of
        // it neutralises the surface's input snapshot: once per transition that leaves Active, never
        // twice for the same loss, and never on a transition that does not lose it.
        var activationWasActive: Boolean = initialLifecycle.activation == ActivationState.Active
        val installed = runCatching {
            port.installLifecycleObserver { snapshot ->
                when (val reduction = reducer.reduce(snapshot)) {
                    is WebLifecycleReduction.Update -> {
                        val wasActive = activationWasActive
                        activationWasActive = reduction.state.activation == ActivationState.Active
                        controller.updateLifecycle(reduction.state)
                        if (wasActive && !activationWasActive) deliverInput(WebInputStimulus.FocusLost)
                    }

                    WebLifecycleReduction.Terminate -> {
                        // A terminating transition closes the input lane, it never resets it: the
                        // reference surface sends its own teardown straight to the terminal path
                        // (`MinimalWindowSurface.kt:326`) and only the terminal publication closes the
                        // input (`:760-766`), because after a detach or a native revocation every late
                        // stimulus is ignored and the input flow is closed. Losing activation — the one
                        // transition that neutralises the snapshot — is the branch above, once per loss.
                        if (snapshot.pageHidden) controller.detachImmediately() else controller.detach()
                    }
                }
            }
        }
        if (installed.isFailure) {
            ownership.releaseAfterAttachFailure()
            return KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.Web, "web-host", "lifecycle-install-failed"),
            )
        }
        val metricsInstalled = runCatching {
            port.installMetricsObserver { metrics -> surface?.applyMetrics(metrics) }
        }
        if (metricsInstalled.isFailure) {
            ownership.releaseAfterAttachFailure()
            return KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.Web, "web-host", "metrics-install-failed"),
            )
        }
        val inputInstalled = runCatching { port.installInputObserver(inputChannel) }
        if (inputInstalled.isFailure) {
            ownership.releaseAfterAttachFailure()
            return KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.Web, "web-host", "input-install-failed"),
            )
        }

        val attached = controller.attach(parentScope, applicationFactory, policy)
        if (attached is KadreResult.Failure) ownership.releaseAfterAttachFailure()
        return attached
    }

    private fun createController(
        initialLifecycle: LifecycleState,
        ownership: WebHostOwnership,
        onSurfaceCreated: (WebHostSurface) -> Unit,
    ): RuntimeHostController = RuntimeHostController.withPrimarySurface(
        platform = KadrePlatform.Web,
        initialLifecycleState = initialLifecycle,
        sessionRevocationHandler = RuntimeSessionRevocationHandler { ownership.releasePort() },
        sessionObserver = RuntimeSessionObserver { _, _ -> ownership.releaseReservation() },
        failureReporter = failureReporter,
        primarySurfaceFactory = { id ->
            val surface = WebHostSurface(id, port, ownership, failureReporter)
            // The ownership releases the target's bridges before the runtime closes the surface, so
            // it has to be able to stop the surface from admitting anything new in between.
            ownership.observeSurface(surface::onOwnershipRevoked)
            onSurfaceCreated(surface)
            RuntimePrimarySurface(surface, surface::detach)
        },
    )
}

private class WebHostOwnership(
    private val port: WebHostPort,
    private val reservation: WebHostReservation,
) {
    private var portReleased: Boolean = false
    private var reservationReleased: Boolean = false
    private var revokeAdmission: (() -> Unit)? = null

    /**
     * Registers the surface this ownership closes the target's bridges for.
     *
     * The surface is built inside the controller's factory, after this ownership exists, so it hands
     * its revocation entry point here instead of being a constructor dependency.
     */
    fun observeSurface(revokeAdmission: () -> Unit) {
        this.revokeAdmission = revokeAdmission
    }

    fun releaseAfterAttachFailure() {
        releasePort()
        releaseReservation()
    }

    fun releasePort() {
        if (portReleased) return
        portReleased = true
        // The port is about to drop every bridge the target could call back through, so the surface
        // stops admitting first: a cooperative stop releases the port while the runtime still has to
        // close the surface, and a frame registered before that must not be able to fire in between.
        runCatching { revokeAdmission?.invoke() }
        runCatching { port.release() }
    }

    fun releaseReservation() {
        if (reservationReleased) return
        reservationReleased = true
        reservation.release()
    }
}

private class WebHostSurface(
    override val id: SurfaceId,
    private val port: WebHostPort,
    private val ownership: WebHostOwnership,
    private val failureReporter: RuntimeFailureReporter,
) : HostSurface, RuntimePrimarySurfaceConfiguration, WebElementLeasePort {
    private var detached: Boolean = false
    private var terminated: Boolean = false

    /** Set once the owner revoked this surface, which happens before the port's bridges go. */
    private var revoked: Boolean = false

    /** True while nothing new may be admitted, by the owner's revocation or by this surface. */
    private val admissionClosed: Boolean get() = revoked || detached || terminated

    /** True between the admission of a lease and the end of the callback it admitted. */
    private var leaseHeld: Boolean = false
    private var configuration: WebSurfaceConfiguration? = null
    private val pendingStimuli = ArrayDeque<WebSurfaceStimulus>()

    /**
     * The input stimuli the target reported before the session configuration built the reducer.
     *
     * They are replayed in this order the moment it exists, so an input the element reported before
     * Kadre was ready is reduced rather than dropped.
     */
    private val pendingInputStimuli = ArrayDeque<WebInputStimulus>()

    /**
     * The shared ordinary-input reducer of this surface, built once from the session configuration.
     *
     * Nothing before that configuration is this surface's to invent: the delivery policy, the stamp
     * source, the collector allocator and the failure reporter all belong to the session. It is only
     * unreachable before the runtime installs them, because application code can only obtain this
     * surface from the session that configured it.
     */
    private lateinit var surfaceInput: RuntimeSurfaceInput
    private var pendingRedraw: Boolean = false

    private var bufferedRedraws: Int = 0
    private var frameHandle: WebFrameHandle? = null
    private val mutableState = MutableStateFlow(
        SurfaceState(
            attachment = SurfaceAttachmentState.Attached,
            logicalSize = port.initialSnapshot.logicalSize,
            physicalSize = port.initialSnapshot.physicalSize,
            scaleFactor = port.initialSnapshot.scaleFactor,
            safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
            visibility = SurfaceVisibility.Visible,
            occlusion = SurfaceOcclusion.Visible,
            focus = SurfaceFocus.Focused,
            appearance = SurfaceAppearance(SurfaceTheme.Unknown, SurfaceContrast.Unknown),
            cursor = CursorStyle.System(CursorIcon.Default),
            pointerCapture = PointerCaptureMode.None,
            hitTesting = HitTestingMode.Enabled,
            inputDefaultBehavior = InputDefaultBehavior.HostDefault,
            revision = SurfaceRevision(0L),
        ),
    )
    private val mutableCapabilities = MutableStateFlow(webSurfaceCapabilities())
    private val mutableEvents = MutableSharedFlow<SurfaceEvent>(replay = 0, extraBufferCapacity = 16)
    private val terminal = CompletableDeferred<Unit>()

    override val state: StateFlow<SurfaceState> = mutableState.asStateFlow()
    override val capabilities: StateFlow<SurfaceCapabilities> = mutableCapabilities.asStateFlow()

    /**
     * The observation stream of this surface, which completes at [terminate].
     *
     * A shared flow carries no completion, so the broadcast is paired with [terminal]: a collector
     * receives the observations the surface publishes until it closes, and a collector that arrives
     * after that sees an already completed stream, the way a closed surface answers `Closed` to
     * every call.
     */
    override val events: Flow<SurfaceEvent> = channelFlow {
        val forwarding = launch { mutableEvents.collect { send(it) } }
        terminal.await()
        forwarding.cancel()
    }

    /**
     * The shared ordinary-input reducer of this surface, which is its one input.
     *
     * It is built from the session configuration — never from a default of this surface's own — and
     * closed by the terminal transition, so its `events` stream ends with the surface and every later
     * stimulus is refused.
     */
    override val input: SurfaceInput get() = surfaceInput

    /**
     * Installs the session-owned configuration this surface publishes through.
     *
     * A configuration that arrives after the surface stopped admitting is dropped with the stimuli
     * that were waiting for it: [closeAdmission] cleared both, and assigning one here would leave a
     * live configuration on a dead surface — the invariant every admission site relies on.
     *
     * Every parameter is stored verbatim, and the input configuration is what this surface builds the
     * shared ordinary-input reducer from: the same delivery policy, stamp source, collector gates and
     * failure handling the components-side window manager builds its own from. The stimuli the target
     * reported earlier are reduced first, in order, and the structural observation is published last.
     */
    override fun installSessionConfiguration(
        deliveryPolicy: WindowDeliveryPolicy,
        inputDeliveryPolicy: InputDeliveryPolicy,
        source: () -> EventStamp,
        sessionFailureHandler: (KadreFailure) -> Unit,
        collectorAllocator: Any,
        maxCollectorsPerFlow: Int,
        resources: ResourceBudgetPolicy,
        dropTransferScope: CoroutineScope?,
        diagnostics: (KadreDiagnostic) -> Unit,
        rawInputPort: RawInputPort?,
    ) {
        if (admissionClosed) return
        // One configuration builds one reducer: installing a second one would leave the first
        // reducer's event stream unclosed and its stimuli unaccounted for.
        check(!this::surfaceInput.isInitialized) { "the session input configuration was already installed" }
        val active = WebSurfaceConfiguration(
            deliveryPolicy = deliveryPolicy,
            inputDeliveryPolicy = inputDeliveryPolicy,
            stampSource = source,
            sessionFailureHandler = sessionFailureHandler,
            collectorAllocator = collectorAllocator,
            maxCollectorsPerFlow = maxCollectorsPerFlow,
            resources = resources,
            dropTransferScope = dropTransferScope,
            diagnostics = diagnostics,
            rawInputPort = rawInputPort,
        )
        configuration = active
        val sessionAllocator = sessionCollectorAllocator(collectorAllocator)
        surfaceInput = RuntimeSurfaceInput(
            surfaceId = id,
            deliveryPolicy = inputDeliveryPolicy,
            eventStampSource = source,
            eventCollectorGate = sessionAllocator.newGate(maxCollectorsPerFlow),
            textInputPort = UnsupportedTextInputPort,
            // Raw input is not activated in this phase, so the session's own port is not wired here;
            // the capability says so structurally instead of leaving the omission implicit.
            rawInputCoordinator = null,
            rawInputCapability = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RawInputAccess)),
            dragAndDropAvailable = false,
            resources = resources,
            dropTransferBudget = RuntimeDropTransferBudget(resources.maxConcurrentDropTransfers),
            // The scope is received deliberately and unused until drag-and-drop is activated in
            // Phase 5.
            dropTransferScope = null,
            textInputEventCollectorGate = sessionAllocator.newGate(maxCollectorsPerFlow),
            // The reporter of diagnostics that are not session failures is the session's own failure
            // reporter, the one this host was built with; the session diagnostic channel feeds the
            // raw-input coordinator of a later phase, not this one.
            failureReporter = { cause -> failureReporter.report(cause) },
            sessionFailureHandler = sessionFailureHandler,
        )
        val pendingInput = pendingInputStimuli.toList()
        pendingInputStimuli.clear()
        // The target's pre-configuration observations happened before the structural capability
        // observation, so replaying them first preserves causality: the revision they move comes
        // before the one that declares the installation they were already feeding.
        pendingInput.forEach(::acceptInput)
        val pending = pendingStimuli.toList()
        pendingStimuli.clear()
        pending.forEach { publish(it, active) }
        // The installation is structural and complete: keyboard and pointer observation exist from
        // here on, and the capabilities may say so. Nothing of the kind is claimed earlier, and touch
        // and gestures stay unsupported until a phase installs their observers.
        surfaceInput.accept(
            SurfaceStimulus.InputObservationChanged(
                surfaceId = id,
                keyboardInstalled = true,
                pointerInstalled = true,
                touchInstalled = false,
                gestureKinds = emptySet(),
            ),
        )
    }

    /**
     * Admits one observed input stimulus, from the target's observer or from the lifecycle reduction.
     *
     * Order is arrival order: a stimulus was either reported by the target in the order its callbacks
     * arrived, or derived from the lifecycle snapshot this session just reduced.
     */
    fun acceptInput(stimulus: WebInputStimulus) {
        if (admissionClosed) return
        if (!this::surfaceInput.isInitialized) {
            pendingInputStimuli.addLast(stimulus)
            return
        }
        when (stimulus) {
            // The reducer owns the neutral snapshot and the one reset it publishes, and it is the one
            // transition that is not an input packet of its own.
            WebInputStimulus.FocusLost -> surfaceInput.focusLost()
            else -> surfaceInput.accept(stimulus.toSurfaceStimulus(id))
        }
    }

    /** Target-owned metrics observation; ignored once the surface is terminated. */
    fun applyMetrics(metrics: WebSurfaceMetrics) {
        if (admissionClosed) return
        enqueue(WebSurfaceStimulus.Metrics(metrics))
    }

    private fun enqueue(stimulus: WebSurfaceStimulus) {
        val active = configuration
        if (active == null) pendingStimuli.addLast(stimulus) else publish(stimulus, active)
    }

    private fun publish(stimulus: WebSurfaceStimulus, active: WebSurfaceConfiguration) {
        when (stimulus) {
            is WebSurfaceStimulus.Metrics -> publishMetrics(stimulus.metrics, active)
            WebSurfaceStimulus.Redraw -> publishRedraw(active)
        }
    }

    private fun publishMetrics(metrics: WebSurfaceMetrics, active: WebSurfaceConfiguration) {
        if (admissionClosed) return
        val current = mutableState.value
        if (
            current.logicalSize == metrics.logicalSize &&
            current.physicalSize == metrics.physicalSize &&
            current.scaleFactor == metrics.scaleFactor
        ) {
            return
        }
        val next = current.copy(
            logicalSize = metrics.logicalSize,
            physicalSize = metrics.physicalSize,
            scaleFactor = metrics.scaleFactor,
            revision = SurfaceRevision(current.revision.value + 1L),
        )
        mutableState.value = next
        mutableEvents.tryEmit(SurfaceEvent.MetricsChanged(next, active.stampSource()))
    }

    /**
     * Coalesces one request per scheduled frame under the session's redraw policy.
     *
     * `Latest` and `Coalesced` both keep at most one pending request and never reorder; they differ
     * only in how a slow collector is served, which the runtime's event machinery owns. A buffered
     * policy bounds the requests awaiting their frame instead and applies its declared overflow
     * action once the bound is crossed.
     */
    private fun publishRedraw(active: WebSurfaceConfiguration) {
        if (admissionClosed) return
        when (val delivery = active.deliveryPolicy.redrawRequests) {
            is ContinuousDelivery.Latest, is ContinuousDelivery.Coalesced -> pendingRedraw = true
            is ContinuousDelivery.Buffered -> {
                bufferedRedraws += 1
                if (bufferedRedraws > delivery.capacity) {
                    when (delivery.onOverflow) {
                        // DropLatestAndReport degenerates to DropOldestAndReport for a coalescing
                        // admission flag: the excess request carries no payload beyond the revision
                        // the frame reads when it admits, so either action drops it and lets the
                        // requests already awaiting their frame admit once, as usual. Neither action
                        // reports: a redraw request dropped under a documented drop policy is not a
                        // failure this surface announces.
                        ContinuousOverflowAction.DropOldestAndReport,
                        ContinuousOverflowAction.DropLatestAndReport,
                        -> bufferedRedraws = delivery.capacity

                        // RedrawRequested is owned by the surface itself (`DESIGN.md` §15.3), so
                        // CloseSource closes this surface and leaves the session running. FailSession
                        // additionally reports the overflow, which terminates the session.
                        ContinuousOverflowAction.CloseSource -> {
                            terminate(KadreFailure.SourceOverflow(KadreResourceKind.Surface))
                            return
                        }

                        ContinuousOverflowAction.FailSession -> {
                            terminate(KadreFailure.SourceOverflow(KadreResourceKind.Surface))
                            active.sessionFailureHandler(
                                KadreFailure.SourceOverflow(KadreResourceKind.Surface),
                            )
                            return
                        }
                    }
                }
            }
        }
        scheduleFrameIfAbsent(active)
    }

    /** Registers the one frame that admits the pending requests; later stimuli join it. */
    private fun scheduleFrameIfAbsent(active: WebSurfaceConfiguration) {
        if (frameHandle != null) return
        frameHandle = port.scheduleFrame {
            frameHandle = null
            if (admissionClosed) return@scheduleFrame
            val admitted = pendingRedraw || bufferedRedraws > 0
            pendingRedraw = false
            bufferedRedraws = 0
            if (!admitted) return@scheduleFrame
            mutableEvents.tryEmit(
                SurfaceEvent.RedrawRequested(mutableState.value.revision, active.stampSource()),
            )
        }
    }

    /**
     * The one admission gate of this surface: `null` while it admits, its refusal otherwise.
     *
     * Every operation a caller can admit through — [apply], [requestRedraw] and [lease] — opens with
     * this, so a surface that stopped admitting (revoked, detached or terminated) answers the same
     * [KadreFailure.Closed] everywhere instead of drifting apart per site.
     */
    private fun admissionFailure(): KadreResult.Failure? =
        if (admissionClosed) KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface)) else null

    override fun requestRedraw(): KadreResult<Unit> {
        admissionFailure()?.let { return it }
        enqueue(WebSurfaceStimulus.Redraw)
        return KadreResult.Success(Unit)
    }

    /**
     * Admits the surface-update fields this backend can honour, one field at a time.
     *
     * Admission is the shared one — `admitField` over the capability this surface publishes, exactly
     * as the reference surface admits the same fields — so a field that cannot be honoured is rejected
     * with the same [RejectedSurfaceField] the reference produces, and a rejection never blocks the
     * fields of the same update that can be. Today one field of the four is supported
     * (`inputDefaultBehavior`, this task's subject) and the other three are refused by their own
     * blanket `Unsupported(UpdateSurface)` capability, which is the outcome the previous phase already
     * promised and which no part of this change weakens.
     *
     * The web surface is its own backend for the field it supports: there is no port below it that
     * could report a different effective value, so an admitted field is committed here and now, with
     * the one new revision a state change owns, and the outcome carries the state the surface
     * publishes. `CursorStyle.Custom` is the only field whose admission the reference splits between
     * two capabilities (`cursor` for the system icons, `customCursor` for an image); on this target
     * both are `Unsupported(UpdateSurface)`, so consulting `cursor` through the same helper gives the
     * same rejection for every value the field can carry.
     *
     * The two requests the reference refuses before admission are refused here with the same failures,
     * and for the same reason — they are what makes a request the surface did not honour impossible to
     * mistake for one it did: a `Clear` on a field that has no "unset" value is an
     * `InvalidRequest(field)` (`OPERATION-CONTRACTS.md` §1.1 registers exactly these four field names
     * for `HostSurface.apply`), and an `expectedRevision` that is not the current one is a
     * `StaleRevision`, since a caller doing optimistic concurrency must not be told `Applied` for an
     * update computed against a state that no longer exists.
     */
    override suspend fun apply(update: SurfaceUpdate): KadreResult<SurfaceUpdateOutcome> {
        admissionFailure()?.let { return it }
        invalidClearField(update)?.let { field ->
            return KadreResult.Failure(KadreFailure.InvalidRequest(field))
        }
        update.expectedRevision?.let { expected ->
            val currentRevision = mutableState.value.revision
            if (expected != currentRevision) {
                return KadreResult.Failure(
                    KadreFailure.StaleRevision(expected.value, currentRevision.value),
                )
            }
        }
        val rejected = mutableListOf<RejectedSurfaceField>()
        val capabilities = mutableCapabilities.value
        // Every field is admitted, including the three that cannot be honoured: a field the surface
        // cannot honour has to be *reported*, never silently dropped, and admitting it is what records
        // the reference's own `RejectedSurfaceField(property, Unsupported(UpdateSurface))` in the list
        // below. Those three answer `Unchanged` for every value they can carry, so their answer is not
        // bound to anything.
        admitField(update.cursor, SurfaceProperty.Cursor, capabilities.cursor, rejected)
        admitField(update.pointerCapture, SurfaceProperty.PointerCapture, capabilities.pointerCapture, rejected)
        admitField(update.hitTesting, SurfaceProperty.HitTesting, capabilities.hitTesting, rejected)
        val inputDefaultBehavior = admitField(
            update.inputDefaultBehavior,
            SurfaceProperty.InputDefaultBehavior,
            capabilities.inputDefaultBehavior,
            rejected,
        )
        val current = mutableState.value
        val committed = if (inputDefaultBehavior is PropertyChange.Set) {
            current.copy(inputDefaultBehavior = inputDefaultBehavior.value)
        } else {
            // `cursor`, `pointerCapture` and `hitTesting` are admitted as `Unchanged` — either because
            // the update did not request them or because their capability refused them — so no field is
            // left to commit here. This is also the path of an update that requests nothing at all.
            current
        }
        // One commit, one revision, and only for a state that actually moved: `next != current` is the
        // reference's own rule (`commitUpdateLocked`), and it is what makes the revision the marker of a
        // state change rather than of a call. Requesting the value already in effect is admitted and
        // answered `Applied` with the state, and therefore the revision, unchanged.
        val next = if (committed != current) {
            committed.copy(revision = SurfaceRevision(current.revision.value + 1L))
        } else {
            current
        }
        if (next != current) mutableState.value = next
        val state = mutableState.value
        return KadreResult.Success(
            if (rejected.isEmpty()) {
                SurfaceUpdateOutcome.Applied(state)
            } else {
                SurfaceUpdateOutcome.PartiallyApplied(state, rejected)
            },
        )
    }

    /**
     * Whether the browser default of the event that carried [stimulus] must be dropped.
     *
     * This is the surface's half of [WebInputObserver.suppressDefaultFor]: the target computes the
     * observation, the category is derived from it (`WebInputTranslation.kt`), and the effective
     * behaviour is read from the state [apply] commits. So the suppression follows the committed value
     * and nothing else, immediately — the next event of a suppressing category is answered with the
     * new policy, without any re-registration.
     *
     * A surface that stopped admitting answers `false`: a revoked, detached or terminated surface owns
     * nothing, and least of all a subtraction from the behaviour of the page it no longer holds.
     */
    fun suppressDefaultFor(stimulus: WebInputStimulus): Boolean {
        if (admissionClosed) return false
        return shouldSuppress(webInputCategory(stimulus), mutableState.value.inputDefaultBehavior)
    }

    /**
     * The field of a `Clear` update, or `null` when no field was cleared.
     *
     * The names are the field paths `OPERATION-CONTRACTS.md` §1.1 registers for `HostSurface.apply`,
     * and they are the reference's own: the check runs before admission, so a `Clear` is refused whole
     * rather than answered with a success for a state that did not move.
     */
    private fun invalidClearField(update: SurfaceUpdate): String? = when {
        update.cursor is PropertyChange.Clear -> "cursor"
        update.pointerCapture is PropertyChange.Clear -> "pointerCapture"
        update.hitTesting is PropertyChange.Clear -> "hitTesting"
        update.inputDefaultBehavior is PropertyChange.Clear -> "inputDefaultBehavior"
        else -> null
    }

    /**
     * Lends the element its port holds, for the duration of [block] and no longer.
     *
     * The element is read from the port on every lease rather than captured with the surface, so a
     * surface whose port is gone lends nothing even while a stale reference to it survives.
     *
     * A surface that stopped admitting reports [KadreFailure.Closed], the same gate every other
     * admission site uses, and it wins over the reentrancy guard: a lease started after the close
     * cannot be retried, so it must not be described as [KadreFailure.TemporarilyUnavailable]. Only a
     * live surface with a lease in flight answers that, which is the one case where retrying later is
     * the right thing for a caller to do.
     *
     * The block starts without a suspension point between admission and its first instruction, so a
     * waiter cancelled before this call invokes nothing. Once the block has started, a cancellation
     * ends it and the lease is released in the `finally`, which is also what an exception out of the
     * block does.
     */
    override suspend fun <R> lease(block: suspend (Any) -> R): KadreResult<R> {
        admissionFailure()?.let { return it }
        if (leaseHeld) return KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true))
        val element = port.leasedElement
            ?: return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface))
        leaseHeld = true
        try {
            return KadreResult.Success(block(element))
        } finally {
            leaseHeld = false
        }
    }

    /**
     * The owner's revocation of this surface, which runs before the target's bridges are released.
     *
     * Admission stops here — the pending request, the registered frame and the buffered stimuli are
     * dropped — while everything already published stays as it is: the terminal state, the end of the
     * events flow and the release of the port remain [terminate]'s work, so an observer sees the same
     * sequence whether the owner revoked first or the surface reached its own terminal transition.
     */
    fun onOwnershipRevoked() {
        if (revoked) return
        revoked = true
        closeAdmission()
    }

    /** The runtime's teardown of this surface: it stops admitting and releases the port. */
    fun detach() {
        terminate(failure = null)
    }

    /**
     * The one terminal transition of this surface.
     *
     * It is reached both by the host detaching ([detach]), by the owner revoking it, and by a redraw
     * overflow under a `CloseSource` or `FailSession` policy, so the paths cannot drift: the surface
     * admits nothing, owns nothing and reports itself detached whichever one led here. Only a failing
     * overflow additionally reports the failure to the session, which is why the caller owns that
     * call, and only [failure] closes the input stream as failed.
     */
    private fun terminate(failure: KadreFailure?) {
        if (terminated) return
        terminated = true
        detached = true
        closeAdmission()
        // The detachment makes the capabilities unavailable before the detached state is published,
        // in the reference's own words and its own snapshot (`DESIGN.md:703`: « Le détachement rend
        // d'abord les capabilities indisponibles, publie ensuite `SurfaceState.Detached` »). Every
        // field is unavailable from here on, `inputDefaultBehavior` included even though it is
        // `Supported`/`Available` while attached: a surface that stopped admitting cannot drop any
        // browser default any more, and [suppressDefaultFor] answers `false` for exactly that reason,
        // so a capability that still claimed it would be a promise no operation could honour.
        mutableCapabilities.value = unsupportedSurfaceCapabilities()
        try {
            val current = mutableState.value
            mutableState.value = current.copy(
                attachment = SurfaceAttachmentState.Detached,
                revision = SurfaceRevision(current.revision.value + 1L),
            )
        } finally {
            // The reducer goes last, so the terminal state it can no longer publish is already out:
            // closing it ends the events stream and refuses every later stimulus, once and once only.
            if (this::surfaceInput.isInitialized) surfaceInput.close(failure)
            terminal.complete(Unit)
            ownership.releasePort()
        }
    }

    /** Stops this surface from admitting anything new; the first step of every terminal transition. */
    private fun closeAdmission() {
        pendingRedraw = false
        bufferedRedraws = 0
        // A scheduled frame must admit nothing after this, so it is cancelled with the admission.
        frameHandle?.cancel()
        frameHandle = null
        pendingStimuli.clear()
        pendingInputStimuli.clear()
        configuration = null
    }
}

/**
 * The session's collector allocator, handed over as the untyped value of the SPI.
 *
 * The allocator is runtime-internal, so the SPI carries it as [Any]; a surface must hand it back to
 * the runtime unchanged rather than interpret it, and an implementation that cannot is a bug, not a
 * policy the surface may substitute one of its own for.
 */
private fun sessionCollectorAllocator(allocator: Any): RuntimeEventCollectorAllocator =
    allocator as? RuntimeEventCollectorAllocator
        ?: error("runtime session collector allocator has an invalid type")

/**
 * One target input observation as the shared reducer's own stimulus.
 *
 * The target's union carries no `surfaceId` because a stimulus describes what the element observed;
 * adding the identity is the surface's own step, exactly as the components-side manager adds it. A
 * focus loss has no branch here: it is not an input packet at all, and the surface reduces it through
 * the reducer's `focusLost`.
 */
private fun WebInputStimulus.toSurfaceStimulus(surfaceId: SurfaceId): SurfaceStimulus = when (this) {
    is WebInputStimulus.KeyChanged -> SurfaceStimulus.KeyChanged(
        surfaceId = surfaceId,
        physicalKey = physicalKey,
        logicalKey = logicalKey,
        location = location,
        keyState = keyState,
        repeat = repeat,
        modifiers = modifiers,
    )

    is WebInputStimulus.PointerEntered -> SurfaceStimulus.PointerEntered(
        surfaceId = surfaceId,
        kind = kind,
        position = position,
    )

    is WebInputStimulus.PointerMoved -> SurfaceStimulus.PointerMoved(
        surfaceId = surfaceId,
        kind = kind,
        position = position,
        delta = delta,
        pressure = pressure,
        pen = pen,
    )

    is WebInputStimulus.PointerButtonChanged -> SurfaceStimulus.PointerButtonChanged(
        surfaceId = surfaceId,
        kind = kind,
        button = button,
        buttonState = buttonState,
        position = position,
        pressure = pressure,
        pen = pen,
    )

    is WebInputStimulus.PointerLeft -> SurfaceStimulus.PointerLeft(
        surfaceId = surfaceId,
        kind = kind,
    )

    is WebInputStimulus.Scrolled -> SurfaceStimulus.Scroll(
        surfaceId = surfaceId,
        delta = delta,
        coalescingBoundary = coalescingBoundary,
    )

    WebInputStimulus.FocusLost -> error("a focus loss is reduced by the reducer, not as a stimulus")
}

/**
 * The field capabilities of one attached web surface.
 *
 * `inputDefaultBehavior` is the one surface-update field this phase activates: the two members of its
 * enum are named one by one rather than derived from the enum — the capability is this backend's
 * promise, and a promise is written out — and `webTest` pins that set against
 * `InputDefaultBehavior.entries` in both directions, so a member the port cannot honour cannot appear
 * in the enum without failing a test. Honouring `SuppressWhenPossible` is what makes the capability
 * honest: the port asks this surface about every event it dispatches and drops the default of the
 * closed set of categories (`WebInputTranslation.kt`), so the promise is a behaviour and not a label.
 *
 * `cursor`, `customCursor` and `hitTesting` stay `Unsupported(UpdateSurface)`, exactly as they were
 * before this field was activated: the phase activates one field, and no part of this change claims
 * another one.
 *
 * This is the snapshot of an attached surface only; [terminate] publishes the all-unsupported one the
 * reference publishes at its own terminal transition, so no field is ever claimed by a surface that
 * stopped admitting.
 */
private fun webSurfaceCapabilities(): SurfaceCapabilities = SurfaceCapabilities(
    cursor = unsupportedSurfaceCapability(KadreOperation.UpdateSurface),
    customCursor = unsupportedSurfaceCapability(KadreOperation.UpdateSurface),
    pointerCapture = unsupportedSurfaceCapability(KadreOperation.UpdateSurface),
    hitTesting = unsupportedSurfaceCapability(KadreOperation.UpdateSurface),
    inputDefaultBehavior = Capability.Supported(
        setOf(InputDefaultBehavior.HostDefault, InputDefaultBehavior.SuppressWhenPossible),
        FeatureAvailability.Available,
    ),
    handlerInteractions = unsupportedSurfaceCapability(KadreOperation.InstallInteractionHandler),
    armedInteractions = unsupportedSurfaceCapability(KadreOperation.ArmInteraction),
    platformAccess = Capability.Supported(Unit, FeatureAvailability.Available),
)

private fun <T> unsupportedSurfaceCapability(operation: KadreOperation): Capability<T> =
    Capability.Unsupported(KadreFailure.Unsupported(operation))
