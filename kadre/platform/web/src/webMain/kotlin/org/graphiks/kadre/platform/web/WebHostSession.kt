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
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.InteractionFailureReason
import org.graphiks.kadre.diagnostics.KadreDiagnostic
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.SurfaceInput
import org.graphiks.kadre.interaction.InteractionAction
import org.graphiks.kadre.interaction.InteractionContext
import org.graphiks.kadre.interaction.InteractionEvent
import org.graphiks.kadre.interaction.InteractionHandler
import org.graphiks.kadre.interaction.InteractionKind
import org.graphiks.kadre.interaction.InteractionRegistration
import org.graphiks.kadre.interaction.InteractionRequestId
import org.graphiks.kadre.interaction.InteractionToken
import org.graphiks.kadre.internal.runtime.NativeInteractionOutcome
import org.graphiks.kadre.internal.runtime.RawInputPort
import org.graphiks.kadre.internal.runtime.RuntimeDropTransferBudget
import org.graphiks.kadre.internal.runtime.RuntimeEventCollectorAllocator
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeHostController
import org.graphiks.kadre.internal.runtime.RuntimeInteractionHandler
import org.graphiks.kadre.internal.runtime.RuntimePrimarySurface
import org.graphiks.kadre.internal.runtime.RuntimePrimarySurfaceConfiguration
import org.graphiks.kadre.internal.runtime.RuntimeSessionRevocationHandler
import org.graphiks.kadre.internal.runtime.RuntimeSessionObserver
import org.graphiks.kadre.internal.runtime.RuntimeSurfaceInput
import org.graphiks.kadre.internal.runtime.RuntimeSynchronousInteraction
import org.graphiks.kadre.internal.runtime.SurfaceStimulus
import org.graphiks.kadre.internal.runtime.UnsupportedTextInputPort
import org.graphiks.kadre.internal.runtime.admitField
import org.graphiks.kadre.internal.runtime.fieldName
import org.graphiks.kadre.internal.runtime.normaliseFieldFailure
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
 * The one channel the surface registers with its target for the synchronous interaction dispatch.
 *
 * A real DOM port keeps it and invokes [dispatch] inside its own `pointerdown`/`keydown` listeners,
 * synchronously and **before** the ordinary stimulus of the same event is enqueued — the AppKit order
 * (`DESIGN.md:983-989`): the interaction first, the regular input continuing normally afterwards.
 * The trigger is DOM-free by construction — the port translates the event into a
 * [RuntimeSynchronousInteraction] with the mapping utilities of this package (`WebInputMapping.kt`),
 * carrying the event's own pressure rather than narrowing it — and the surface decides everything
 * else: whether a handler is installed, which actions the token may take, and what the primitives are.
 */
internal fun interface WebInteractionDispatcher {
    /** Dispatches one trigger the target observed, within that event's own callback. */
    fun dispatch(trigger: RuntimeSynchronousInteraction)
}

/**
 * The terminal answer of one browser primitive, delivered when the browsing context has decided.
 *
 * `true` is the confirmation a `fullscreenchange`/`pointerlockchange` carries; `false` is the refusal
 * a `fullscreenerror`/`pointerlockerror` or a rejected promise carries. The port decides nothing and
 * explains nothing — the DOM exposes no reason, and the one honest code the refusal produces is
 * [refusalFailure]'s. A member that answers synchronously may invoke this before it returns (the
 * exiting defaults do exactly that); a member that emitted the primitive invokes it later, from the
 * terminal event's own callback.
 */
internal fun interface WebPrimitiveTerminal {
    /** Reports the browser's terminal answer for the primitive that was emitted. */
    fun onTerminal(committed: Boolean)
}

/**
 * The target's input channel into the shared surface: one observer per port, installed once.
 *
 * It carries three members because three different facts reach the surface through it, and none of them
 * can be answered by the target alone. The first is the observation itself — what the element saw — and
 * it is the only member every implementation has to answer; it is the abstract one, so a lambda
 * implements a channel that observes and suppresses nothing, which is what a test double and any port
 * without a suppression seam want.
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
 *
 * The third is the browser's word that it ended the element's pointer capture, which is a fact about
 * the surface's own state rather than an input observation: nothing of the input model describes it, so
 * it is not a [WebInputStimulus] and the shared reducer has no transition for it. It is reported, like
 * the question above, and what it means for the committed capture is decided where the state lives.
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

    /**
     * Reports the browser's own `lostpointercapture`: the element no longer confines that pointer.
     *
     * The port reports the browser's fact and decides nothing — whether the loss moves the committed
     * capture is the surface's rule — and it reports it for the pointer it holds, with no payload to
     * interpret: the port never learns what a mode is, and the surface never learns what a DOM event is.
     * A channel that does not answer loses nothing: a surface that is never told a capture ended keeps
     * the one its consumer asked for, which is the conservative reading of a report nobody made.
     */
    fun onPointerCaptureLost() = Unit
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
     * Performs the one browser effect a capture decision has, and reports the browser's own answer.
     *
     * [captured] takes the capture of the pointer this port observed pressed on the element, and `false`
     * releases it. The member is a mechanism and nothing else: the port never decides whether a capture
     * is allowed — it is asked to perform one and either performs it or reports why the browser would
     * not.
     *
     * **The default inverts the usual one.** Every other member of this interface defaults to the inert
     * behaviour a port that does not implement it should have, which is a *success*: nothing observed,
     * nothing scheduled, nothing installed. This one cannot, because the surface commits `Confined` only
     * on a `Success`: a default that answered `Success(Unit)` would let a port that performs no capture
     * at all make the surface publish a confinement the browser never took — the fictitious success this
     * whole admission path exists to prevent. The default is therefore a **failing** one, and it is the
     * failure the capability would have carried had it been honest: `Unsupported(UpdateSurface)`. A port
     * that cannot capture is refused field by field instead of being believed; a port that can must
     * override this member, perform the DOM's own capture effect, and answer with the browser's answer
     * (containing its refusal, see below).
     *
     * The browser refuses a capture for a pointer it does not consider active (`setPointerCapture`
     * throws), and this call is made inside the callback of the event that led to the decision — a
     * consumer asking for a capture as it reduces a press — so the refusal is *contained here*: it is
     * returned as a failure the surface reports as a rejected field, and never thrown into the callback
     * (`WEB-IMPLEMENTATION-ROADMAP.md` §3.4, "un événement DOM … ne laisse pas échapper d'exception
     * Kotlin"). The failure is a [KadreFailure.PlatformFailure] of this platform because the call really
     * crosses the browser's own DOM API, and `OPERATION-CONTRACTS.md` §3 admits it on the
     * rejected-field row of `HostSurface.apply`, which is where the surface puts it — after the shared
     * normaliser has checked it against that closed set
     * ([normaliseFieldFailure]), so a member of that row is required and nothing else is accepted.
     */
    fun applyPointerCapture(captured: Boolean): KadreResult<Unit> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.UpdateSurface))

    /**
     * Registers the channel the surface dispatches its interactions through.
     *
     * The default preserves the inert ports: a target without interaction listeners simply never
     * invokes what it is given, exactly as a port that installs no observation delivers nothing. The
     * real DOM ports override this member to keep the dispatcher and invoke it from their own
     * `pointerdown`/`keydown` listeners (the task that wires the primitives); the surface installs
     * the dispatcher once, with the session configuration that builds the interaction engine.
     */
    fun installInteractionDispatcher(dispatcher: WebInteractionDispatcher) = Unit

    /**
     * Emits the one browser effect the fullscreen action has, and reports the browser's answer.
     *
     * The member is a mechanism and nothing else — the surface has already admitted the action inside
     * a live interaction callback, and this is asked to perform it while the event's transient
     * activation still holds. It answers whether the primitive *was emitted* ([KadreResult.Success])
     * or could not be at all (a failure); whether the browser then honours it is not this answer's
     * business — that verdict arrives through [WebPrimitiveTerminal.onTerminal], when the browsing
     * context confirms or refuses the primitive. The distinction is the deferred outcome's whole
     * shape: an emission failure is synchronous, a browser refusal is a terminal callback.
     *
     * **The default inverts the usual one, exactly as `applyPointerCapture` does.** A port that does
     * not implement the member cannot emit the primitive, and a default that answered `Success` would
     * let the surface promise a fullscreen nobody asked the browser for — the fictitious success the
     * interaction contract exists to prevent. The default is therefore a **failing** one, carrying
     * the refusal the capability's own domain names (`refusalFailure(WEB_FULLSCREEN_DOMAIN)`): the
     * request is refused synchronously, no terminal callback will ever fire for it, and the outcome
     * the surface publishes is the rejection. A port that can emit must override this member.
     */
    fun requestFullscreen(onTerminal: WebPrimitiveTerminal): KadreResult<Unit> =
        KadreResult.Failure(refusalFailure(WEB_FULLSCREEN_DOMAIN))

    /**
     * Emits the one browser effect the exit-fullscreen action has, and reports the browser's answer.
     *
     * The emission/terminal split is [requestFullscreen]'s; this member differs only in its default,
     * which is an **inert success that fires its terminal synchronously**: there is no fullscreen to
     * leave for a port that never enters one, so "already there" *is* the committed answer, and a
     * consumer's exit action completes committed instead of hanging on a callback that would never
     * fire. A real port overrides it to ask the browser to leave.
     */
    fun exitFullscreen(onTerminal: WebPrimitiveTerminal): KadreResult<Unit> {
        onTerminal.onTerminal(true)
        return KadreResult.Success(Unit)
    }

    /**
     * Emits the one browser effect the pointer-lock action has, and reports the browser's answer.
     *
     * The emission/terminal split is [requestFullscreen]'s, and so is the refusing default: a port
     * that does not implement the member cannot lock a pointer, and answering anything but a
     * synchronous refusal would let the surface publish a commitment no browser made. The refusal
     * names the pointer-lock domain (`refusalFailure(WEB_POINTER_LOCK_DOMAIN)`).
     */
    fun requestPointerLock(onTerminal: WebPrimitiveTerminal): KadreResult<Unit> =
        KadreResult.Failure(refusalFailure(WEB_POINTER_LOCK_DOMAIN))

    /**
     * Emits the one browser effect the unlock-pointer action has, and reports the browser's answer.
     *
     * The emission/terminal split is [requestFullscreen]'s, and the default is [exitFullscreen]'s:
     * nothing is locked, so nothing needs unlocking, and the terminal fires synchronously with the
     * committed answer. A real port overrides it to ask the browser to release the pointer.
     */
    fun exitPointerLock(onTerminal: WebPrimitiveTerminal): KadreResult<Unit> {
        onTerminal.onTerminal(true)
        return KadreResult.Success(Unit)
    }

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

        // The one channel of this session: the target hands its observations over through it, asks it
        // about the default of the event each observation came from, and reports the browser's own
        // lost capture through it. Every answer is given here, where the surface lives — the target
        // never learns what a category is, which value the policy holds, what a capture mode is, or how
        // any of them is decided.
        val inputChannel = object : WebInputObserver {
            override fun onObservation(stimulus: WebInputStimulus) = deliverInput(stimulus)

            override fun suppressDefaultFor(stimulus: WebInputStimulus): Boolean =
                // Input observed before the runtime built the surface waits in `pendingInput`, and the
                // question is answered `false` for it: the behaviour the decision reads is the
                // surface's own state, and a surface that does not exist has stated none. The same
                // holds for every stimulus the session derives itself (a focus loss), which no browser
                // event is waiting on.
                surface?.suppressDefaultFor(stimulus) ?: false

            override fun onPointerCaptureLost() {
                // The port reports the browser's fact as soon as it observes it, which is after the
                // surface exists — the listeners are installed once the runtime has built it. A report
                // that arrives with no surface yet is dropped like every other fact of that window:
                // there is no committed capture to reconcile, because no consumer could have asked for
                // one.
                surface?.onPointerCaptureLost()
            }
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

    /**
     * The pointer this surface holds, which is the one thing a `Confined` capture is admitted on.
     *
     * It is the surface's own record of what the element observed, kept by the shared rule of
     * [WebPointerOwnership] and fed by the very stimuli [acceptInput] admits, so the two targets cannot
     * derive it differently. It is not a field of `SurfaceState`: the public model has no such member,
     * and what the element observed of a pointer is not a promise about the browser the way the
     * committed state is.
     */
    private val pointerOwnership: WebPointerOwnership = WebPointerOwnership()
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

    /**
     * The common interaction engine of this surface, built with the session configuration and never
     * before it.
     *
     * It is the runtime's own token engine — the same class the reference surface builds, not a second
     * machine (`plan` decision D1) — so its registration, its token and its pending budget are the
     * runtime's, and the capabilities claim the four web actions only once this exists. `null` is the
     * honest pre-install state: an interaction handler cannot be admitted on a surface whose session
     * has not configured it, and [installInteractionHandler] answers `Unsupported` for exactly that.
     */
    private var interactionHandler: RuntimeInteractionHandler? = null

    /** The actions the engine advertises, as the dispatch's supported set reads them. */
    private var advertisedInteractions: Set<InteractionKind> = emptySet()

    /**
     * The primitive emission of the request currently being admitted, if it emitted a primitive whose
     * terminal answer has not been routed yet.
     *
     * One request runs at a time on this target — the browser is mono-threaded and the token is
     * single-use — so a single slot is exact: [invokeNative] sets it when a primitive was emitted for
     * the request in flight, and the context the surface wraps around the consumer's handler reads it
     * the moment `request` returns, binding the emission to the request id the runtime allocated (or
     * dropping it, when that request failed after the emission and no pending exists to complete).
     */
    private var interactionEmissionInFlight: WebInteractionEmission? = null
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
    private val mutableCapabilities = MutableStateFlow(preInstallSurfaceCapabilities())
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
        // The interaction engine is built from the same configuration the ordinary reducer is — the
        // same delivery policy, collector gate and session failure handling — mirroring the reference
        // surface's own construction, so the web target owns no token machinery of its own (plan
        // decision D1). The dispatcher goes to the port at the same moment: from here on, the
        // target's listeners can dispatch an interaction into it.
        val interactions = interactionActionsForWeb()
        interactionHandler = RuntimeInteractionHandler(
            surfaceId = id,
            advertised = interactions,
            deliveryPolicy = deliveryPolicy,
            eventCollectorGate = sessionAllocator.newGate(maxCollectorsPerFlow),
            failureReporter = failureReporter,
            sessionFailureHandler = sessionFailureHandler,
            maxPendingInteractionRequests = resources.maxPendingInteractionRequests,
        )
        advertisedInteractions = interactions
        port.installInteractionDispatcher(WebInteractionDispatcher { trigger -> dispatchInteraction(trigger) })
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
        // The same structural moment publishes the interaction capability, with the exact set the
        // engine just started advertising — never before: an installation that has not happened is a
        // promise nobody could honour, which is what the pre-install snapshot says instead. The arm
        // path stays unsupported on every platform; the handler's token is the only interaction
        // authority this target exposes.
        mutableCapabilities.value = webSurfaceCapabilities(
            Capability.Supported(interactions, FeatureAvailability.Available),
        )
    }

    /**
     * Admits the one synchronous interaction handler this surface carries, through the common engine.
     *
     * The delegation is the whole story — the runtime owns the token, the serialisation and the
     * single-registration rule (`plan` decision D1) — except for one wrapping: the handler is
     * installed behind [EmissionBindingContext], which observes each `request` result so that a
     * primitive emitted inside the request is bound to the request id the runtime allocated. Without
     * that binding, a browser primitive whose terminal callback fires later would have no request id
     * to complete — the engine allocates the id *before* `invokeNative` runs, but the native call's
     * signature receives only the action, so the id never reaches the emission and only the caller
     * of `request` ever sees it.
     *
     * A surface that stopped admitting answers [KadreFailure.Closed] like every other admission site,
     * a surface without its engine (the session configuration has not installed) answers
     * `Unsupported(InstallInteractionHandler)` — the failure its pre-install capability describes —
     * and a second handler while one lives is the engine's own `AlreadyInUse`.
     */
    @OptIn(DelicateKadreApi::class)
    override fun installInteractionHandler(
        handler: InteractionHandler,
    ): KadreResult<InteractionRegistration> {
        admissionFailure()?.let { return it }
        val interaction = interactionHandler
            ?: return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.InstallInteractionHandler))
        return interaction.install(InteractionHandler { context, event ->
            handler.onInteraction(EmissionBindingContext(context), event)
        })
    }

    /**
     * The dispatcher's half of the seam: one trigger the target observed, dispatched synchronously.
     *
     * This runs inside the DOM callback the port invoked it from, before that event's ordinary
     * stimulus is enqueued (the AppKit order, `DESIGN.md:983-989`), so the callback may request an
     * action while the event's transient activation still holds — which is the entire authority the
     * interaction model preserves. Nothing here suspends or escapes: an exception out of the
     * consumer's handler is captured by the engine, reported, and fails the session, never the page.
     *
     * A surface that stopped admitting dispatches nothing: its listeners are on their way out with
     * the port's bridges, and an interaction on a closed surface is an authority nobody can honour.
     * Touch is deliberately absent: no port of this target builds a `TouchStarted` trigger (the plan's
     * recorded limits), so the branch exists to be refused rather than to classify a member that
     * cannot arrive.
     */
    private fun dispatchInteraction(trigger: RuntimeSynchronousInteraction) {
        if (admissionClosed) return
        val interaction = interactionHandler ?: return
        val active = configuration ?: return
        val event = when (trigger) {
            is RuntimeSynchronousInteraction.PointerPressed -> InteractionEvent.PointerPressed(
                trigger.button,
                trigger.position,
                active.stampSource(),
            )

            is RuntimeSynchronousInteraction.KeyPressed -> InteractionEvent.KeyPressed(
                trigger.physicalKey,
                active.stampSource(),
            )

            is RuntimeSynchronousInteraction.TouchStarted -> return
        }
        interaction.dispatch(event, advertisedInteractions, ::invokeNative)
    }

    /**
     * The one native step an admitted action takes, asked by the engine inside its callback frame.
     *
     * Unreachable members first, because they are the guarantee the admission ordering is made of: an
     * action outside the advertised set is refused by the engine *before* this is called, so no
     * browser API is ever asked for an action the surface does not support. The branch remains,
     * exhaustive, as the closed answer that keeps the function total.
     *
     * The four web actions validate what the admission cannot see — a `LockPointer` mode this target
     * does not take is refused here, still before any primitive call — and then emit their primitive
     * through the port. What comes back decides the outcome's shape: an emission failure is
     * synchronous ([NativeInteractionOutcome.Now]), an emitted primitive is deferred to the browser's
     * terminal answer ([NativeInteractionOutcome.Deferred]), whose callback completes the pending
     * with [refusalFailure] when the browser refused — a `committed = false` without a failure would
     * be a rejection nobody could name.
     */
    private fun invokeNative(action: InteractionAction): NativeInteractionOutcome = when (action) {
        is InteractionAction.EnterFullscreen -> emitWebPrimitive(WEB_FULLSCREEN_DOMAIN) { terminal ->
            port.requestFullscreen(terminal)
        }

        is InteractionAction.ExitFullscreen -> emitWebPrimitive(WEB_FULLSCREEN_DOMAIN) { terminal ->
            port.exitFullscreen(terminal)
        }

        is InteractionAction.LockPointer -> when (val mode = normaliseLockPointerMode(action.mode)) {
            is KadreResult.Failure -> NativeInteractionOutcome.Now(mode)
            is KadreResult.Success -> emitWebPrimitive(WEB_POINTER_LOCK_DOMAIN) { terminal ->
                port.requestPointerLock(terminal)
            }
        }

        is InteractionAction.UnlockPointer -> emitWebPrimitive(WEB_POINTER_LOCK_DOMAIN) { terminal ->
            port.exitPointerLock(terminal)
        }

        InteractionAction.BeginWindowMove,
        is InteractionAction.BeginWindowResize,
        is InteractionAction.AcceptDrop,
        is InteractionAction.OpenWindow,
        -> NativeInteractionOutcome.Now(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.Interaction)),
        )
    }

    /**
     * Emits one primitive through the port and answers the deferred outcome for it.
     *
     * The terminal callback closes over the emission record, not over a request id — the engine
     * allocates the id before `invokeNative` runs, but it is not passed to the native call, whose
     * only parameter is the action. A terminal that fires after the
     * request returned finds the record bound and completes the pending through it; a terminal that
     * fires synchronously inside the primitive call (the exiting defaults) records its answer on the
     * record instead, and the binding below completes the pending the moment the request id exists.
     * A port that could not emit at all answers synchronously and the outcome is a `Now` rejection:
     * no terminal will ever fire for a call that never happened.
     */
    private fun emitWebPrimitive(
        domain: String,
        emit: (WebPrimitiveTerminal) -> KadreResult<Unit>,
    ): NativeInteractionOutcome {
        val emission = WebInteractionEmission()
        val terminal = WebPrimitiveTerminal { committed ->
            onPrimitiveTerminal(emission, committed, if (committed) null else refusalFailure(domain))
        }
        val emitted = emit(terminal)
        if (emitted is KadreResult.Failure) {
            return NativeInteractionOutcome.Now(emitted)
        }
        // The primitive is out, its answer pending: the request in flight owns this emission, and the
        // wrapped context binds it to the id the engine allocates before returning.
        interactionEmissionInFlight = emission
        return NativeInteractionOutcome.Deferred { _, _ ->
            // The engine's terminal notification, delivered after the outcome was published. Nothing
            // to release in this phase: the terminal listeners a real DOM port hooks onto the
            // browser's events are the overriding member's own, and both completion paths route
            // through `onPrimitiveTerminal` above.
        }
    }

    /** Routes one primitive terminal to its pending, binding it first if the request just returned. */
    private fun onPrimitiveTerminal(
        emission: WebInteractionEmission,
        committed: Boolean,
        failure: KadreFailure?,
    ) {
        val requestId = emission.requestId
        if (requestId != null) {
            interactionHandler?.completePending(requestId, committed, failure)
        } else {
            // The terminal fired before the runtime returned the request id — a synchronous answer,
            // like the exiting defaults give: the binding completes it instead.
            emission.terminal = committed to failure
        }
    }

    /**
     * The wrapped context's half: binds the emission of the request that just returned, or drops it.
     *
     * A failed result after the primitive was emitted — the pending budget refused it, or the
     * registration closed during the native call — has no pending behind it: the engine admits no
     * pending for a refused request, so the emission's terminal would complete nothing and is dropped
     * with the record. A successful result binds the id; a terminal that already fired completes the
     * fresh pending right here, which is what makes a synchronously-answering port commit instead of
     * hanging.
     */
    private fun bindEmission(result: KadreResult<InteractionRequestId>) {
        val emission = interactionEmissionInFlight ?: return
        interactionEmissionInFlight = null
        val requestId = (result as? KadreResult.Success)?.value ?: return
        emission.requestId = requestId
        emission.terminal?.let { (committed, failure) ->
            interactionHandler?.completePending(requestId, committed, failure)
        }
        emission.terminal = null
    }

    /**
     * The context the consumer's handler actually receives, which differs from the engine's own by
     * exactly one behaviour: each `request` result is observed, so the primitive emitted inside that
     * request is bound to — or dropped with — the id the engine allocated for it. Everything else is
     * forwarded verbatim: the token, and the engine's own admission rules.
     */
    private inner class EmissionBindingContext(
        private val delegate: InteractionContext,
    ) : InteractionContext {
        override val token: InteractionToken get() = delegate.token

        override fun request(action: InteractionAction): KadreResult<InteractionRequestId> {
            val result = delegate.request(action)
            bindEmission(result)
            return result
        }
    }

    /**
     * Admits one observed input stimulus, from the target's observer or from the lifecycle reduction.
     *
     * Order is arrival order: a stimulus was either reported by the target in the order its callbacks
     * arrived, or derived from the lifecycle snapshot this session just reduced.
     *
     * The ownership the surface holds is updated before the stimulus is reduced, so the consumer that
     * reacts to the event the reduce publishes already sees the ownership that event describes — a
     * release that ends the pointer ends it for the capture too, not one event later.
     */
    fun acceptInput(stimulus: WebInputStimulus) {
        if (admissionClosed) return
        if (!this::surfaceInput.isInitialized) {
            pendingInputStimuli.addLast(stimulus)
            return
        }
        pointerOwnership.observe(stimulus)
        when (stimulus) {
            // The reducer owns the neutral snapshot and the one reset it publishes, and it is the one
            // transition that is not an input packet of its own.
            WebInputStimulus.FocusLost -> surfaceInput.focusLost()
            else -> surfaceInput.accept(stimulus.toSurfaceStimulus(id))
        }
        // The other half of the ownership rule: a pointer the surface no longer holds cannot carry a
        // capture, and the browser ends one with it on every arm but the activation-loss one (see
        // [reconcilePointerCapture] for that divergence).
        if (!pointerOwnership.isOwned) reconcilePointerCapture()
    }

    /**
     * The surface's half of [WebInputObserver.onPointerCaptureLost]: the browser ended the capture.
     *
     * The port reports the browser's own `lostpointercapture` and this reconciles what the surface had
     * committed. Nothing of the input snapshot is touched — a lost capture is not a released button, and
     * the pointer may well still be down on the element — which is why this is a report of its own
     * rather than a stimulus: it moves the surface's claim about the browser, and only that.
     *
     * A surface that stopped admitting reconciles nothing, like every other site of this surface: its
     * capabilities are already unavailable and its state is the terminal one.
     */
    fun onPointerCaptureLost() {
        if (admissionClosed) return
        reconcilePointerCapture()
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
     * fields of the same update that can be. Two fields of the four are supported
     * (`inputDefaultBehavior` and `pointerCapture`, this phase's subjects) and the other two are refused
     * by their own blanket `Unsupported(UpdateSurface)` capability, which is the outcome the previous
     * phase already promised and which no part of this change weakens.
     *
     * The web surface is its own backend for both fields it supports: there is no port below it that
     * could report a different effective value, so an admitted field is committed here and now, with
     * the one new revision a state change owns, and the outcome carries the state the surface
     * publishes. `pointerCapture` is the one field whose commit is not this surface's alone — the
     * browser is what confines a pointer, so the effect is asked of the port and the state is committed
     * only once the browser honoured it (see [admitCaptureAttempt] and [commitPointerCapture]).
     * `CursorStyle.Custom` is the only field whose admission the reference splits between two
     * capabilities (`cursor` for the system icons, `customCursor` for an image); on this target both are
     * `Unsupported(UpdateSurface)`, so consulting `cursor` through the same helper gives the same
     * rejection for every value the field can carry.
     *
     * The two requests the reference refuses before admission are refused here with the same failures,
     * and for the same reason — they are what makes a request the surface did not honour impossible to
     * mistake for one it did: an `expectedRevision` that is not the current one is a `StaleRevision`,
     * and a `Clear` on a field that has no "unset" value is an `InvalidRequest(field)`
     * (`OPERATION-CONTRACTS.md` §1.1 registers exactly these four field names for
     * `HostSurface.apply`). Both run before admission, and in the reference's order — the revision
     * first (`MinimalWindowSurface.kt:269-278`) — so a request that is both stale and malformed is
     * answered as stale rather than as malformed.
     *
     * Every admitted field is then committed or reported, one at a time: what [admitField] answers
     * `Unchanged` for was either not requested or refused by its capability, and a field that reaches
     * the commit with a change the surface has no path for fails loudly instead of disappearing
     * (see [requireRefused]).
     */
    override suspend fun apply(update: SurfaceUpdate): KadreResult<SurfaceUpdateOutcome> {
        admissionFailure()?.let { return it }
        update.expectedRevision?.let { expected ->
            val currentRevision = mutableState.value.revision
            if (expected != currentRevision) {
                return KadreResult.Failure(
                    KadreFailure.StaleRevision(expected.value, currentRevision.value),
                )
            }
        }
        invalidClearField(update)?.let { field ->
            return KadreResult.Failure(KadreFailure.InvalidRequest(field))
        }
        val rejected = mutableListOf<RejectedSurfaceField>()
        val capabilities = mutableCapabilities.value
        // Every field is admitted, including the three that cannot be honoured: a field the surface
        // cannot honour has to be *reported*, never silently dropped, and admitting it is what records
        // the reference's own `RejectedSurfaceField(property, Unsupported(UpdateSurface))` in the list
        // below.
        val cursor = admitField(update.cursor, SurfaceProperty.Cursor, capabilities.cursor, rejected)
        // The capture is the one field whose admission has a second rule of this surface's own, and it
        // is applied here rather than at the commit so that the rejected list reads in the order of the
        // update's fields, exactly as the reference's does.
        val pointerCapture = admitCaptureAttempt(
            admitField(
                update.pointerCapture,
                SurfaceProperty.PointerCapture,
                capabilities.pointerCapture,
                rejected,
            ),
            rejected,
        )
        val hitTesting = admitField(update.hitTesting, SurfaceProperty.HitTesting, capabilities.hitTesting, rejected)
        val inputDefaultBehavior = admitField(
            update.inputDefaultBehavior,
            SurfaceProperty.InputDefaultBehavior,
            capabilities.inputDefaultBehavior,
            rejected,
        )
        // The two fields with no commit path are guarded rather than assumed away: their capabilities
        // refuse every value today, but the day one of them becomes `Supported` the change would land
        // here uncommitted and be answered with an `Applied` for a state that never took it.
        cursor.requireRefused(SurfaceProperty.Cursor)
        hitTesting.requireRefused(SurfaceProperty.HitTesting)
        val current = mutableState.value
        var committed = current
        // `pointerCapture` is committed only once the browser has honoured it, so the effect is asked
        // for first and the state follows it — never the other way round.
        commitPointerCapture(pointerCapture, current, rejected)?.let { capture ->
            committed = committed.copy(pointerCapture = capture)
        }
        if (inputDefaultBehavior is PropertyChange.Set) {
            committed = committed.copy(inputDefaultBehavior = inputDefaultBehavior.value)
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
        // The rejections are merged before they are reported: this surface refuses some fields at
        // admission and the capture at commit, so the two passes are put back into the order the update
        // writes its fields in — the reference's own order, since it walks all four fields in one commit
        // pass (`MinimalWindowSurface.commitUpdateLocked`). A consumer that reads a `PartiallyApplied`
        // therefore sees the same order whatever refused which field.
        val reported = rejected.inSurfaceUpdateOrder()
        return KadreResult.Success(
            if (reported.isEmpty()) {
                SurfaceUpdateOutcome.Applied(state)
            } else {
                SurfaceUpdateOutcome.PartiallyApplied(state, reported)
            },
        )
    }

    /**
     * The capture change this surface may attempt, or [PropertyChange.Unchanged] when the field is
     * refused before any browser call is made.
     *
     * Two rules, and both of them are this surface's rather than the browser's:
     *
     * - a mode this backend cannot honour — `Locked`, the only one [webPointerCaptureIsHonourable] says
     *   no to — is reported `Unsupported(UpdateSurface)`, the same failure the shared admission helper
     *   produces for it. It is unreachable while the capability refuses the mode, and it is stated here
     *   rather than assumed away: the day the capability changed, the change would otherwise land as an
     *   `Applied` for a capture no browser path could take;
     * - a `Confined` capture needs a pointer this surface owns (D13). Without one there is nothing to
     *   confine, and the field is reported `InteractionRequired(Missing)` — the failure
     *   `OPERATION-CONTRACTS.md` §3 admits for a rejected field — so no effect is ever asked of the
     *   browser for a field the surface refused.
     *
     * The refusals are recorded here, which is where the update's fields are walked, so the rejected
     * list keeps the order the caller wrote its fields in.
     */
    private fun admitCaptureAttempt(
        change: PropertyChange<PointerCaptureMode>,
        rejected: MutableList<RejectedSurfaceField>,
    ): PropertyChange<PointerCaptureMode> {
        if (change !is PropertyChange.Set) return change
        if (!webPointerCaptureIsHonourable(change.value)) {
            rejected += RejectedSurfaceField(
                SurfaceProperty.PointerCapture,
                KadreFailure.Unsupported(KadreOperation.UpdateSurface),
            )
            return PropertyChange.Unchanged
        }
        if (change.value == PointerCaptureMode.Confined && !pointerOwnership.isOwned) {
            rejected += RejectedSurfaceField(
                SurfaceProperty.PointerCapture,
                KadreFailure.InteractionRequired(InteractionFailureReason.Missing),
            )
            return PropertyChange.Unchanged
        }
        return change
    }

    /**
     * Performs one admitted capture change through the browser and answers the value to commit, or
     * `null` when this surface commits nothing.
     *
     * The value already in effect is the first answer: there is nothing to ask the browser — the state
     * already says the capture is where the request wants it, and the browser was told so when it was
     * committed — and nothing moves, so the revision stays where it is.
     *
     * Otherwise the port performs the one browser effect and reports the browser's own answer. A success
     * commits the mode; a refusal is reported as a rejected field carrying the failure the port
     * returned, and the state stays where it was — answering `Applied` for a capture the browser never
     * took is the fictitious success this whole path exists to prevent.
     *
     * The failure is *not* trusted: it goes through the runtime's own [normaliseFieldFailure] for this
     * platform and this property, exactly as the reference surface normalises a port's field outcome
     * (`MinimalWindowSurface.commitField` → `normaliseFieldFailure`). A failure the closed set of
     * `OPERATION-CONTRACTS.md` §3 does not admit — a buggy or hostile port answering `Closed(Surface)`,
     * say — is replaced by `PlatformFailure(Web, "surface-command-port", "invalid-field-failure")`, and
     * that adapter failure is reported rather than returned, because what the caller receives must be a
     * failure its operation admits. A port failure that *is* in the set (this task's own
     * `PlatformFailure(Web, "web-host", "pointer-capture-failed")`, or the default member's
     * `Unsupported(UpdateSurface)`) passes through unchanged.
     */
    private fun commitPointerCapture(
        change: PropertyChange<PointerCaptureMode>,
        current: SurfaceState,
        rejected: MutableList<RejectedSurfaceField>,
    ): PointerCaptureMode? {
        if (change !is PropertyChange.Set) return null
        if (change.value == current.pointerCapture) return null
        return when (val honoured = port.applyPointerCapture(change.value != PointerCaptureMode.None)) {
            is KadreResult.Success -> change.value
            is KadreResult.Failure -> {
                val normalised = normaliseFieldFailure(
                    KadrePlatform.Web,
                    SurfaceProperty.PointerCapture,
                    honoured.reason,
                )
                rejected += RejectedSurfaceField(SurfaceProperty.PointerCapture, normalised.failure)
                normalised.adapterFailure?.let { adapterFailure ->
                    // Reported, never returned (`MinimalWindowSurface.reportAdapterFailure`), and the
                    // report itself may not destabilise the command boundary of this surface.
                    runCatching { failureReporter.report(KadreException(adapterFailure)) }
                }
                null
            }
        }
    }

    /**
     * Returns the committed capture to `None`: the browser no longer confines the pointer it named.
     *
     * A `Confined` capture is a claim about the browser — the element holds that pointer — so a claim
     * the browser has ended cannot stay published. It is reached from both halves of the reconciliation:
     * the report the port makes of the browser's own `lostpointercapture`, and the loss of the pointer
     * this surface held (a release, a cancellation, a leave, a loss of activation), because a capture
     * and the pointer it belongs to end together.
     *
     * Nothing is asked of the browser here: the fact being reconciled is that there is no capture left to
     * end, and a `releasePointerCapture` for a pointer that holds none would be a call with no decision
     * behind it. The revision moves as it does for any committed state change, and only when there is a
     * capture to reconcile — a surface that holds none is already at `None`.
     *
     * **Known divergence — the activation-loss arm only.** On every other arm the browser ends the
     * capture implicitly with the pointer it belonged to, so the DOM and the published state agree: a
     * button release, a `pointerleave` and a `pointercancel` all release a capture implicitly. A loss of
     * activation does not: Chromium keeps a mouse pointer capture across a window blur, so on that arm
     * the surface publishes `pointerCapture = None` while the DOM still confines the pointer to the
     * element, and no `releasePointerCapture` is sent to end it. The divergence is one-directional and
     * safe — the surface claims *less* than the browser does, the confinement it stops announcing cannot
     * be re-taken without a new press (the ownership went with the activation), and no operation is
     * authorised by the difference — and it is recorded in this form so the capability documentation of
     * this adapter can state it verbatim: *after a loss of activation the web surface publishes
     * `SurfaceState.pointerCapture = None` and does not call `releasePointerCapture`; Chromium may still
     * hold the mouse capture until the next press, so `None` must be read as "this surface claims no
     * capture", never as "the browser holds none".*
     */
    private fun reconcilePointerCapture() {
        val current = mutableState.value
        if (current.pointerCapture == PointerCaptureMode.None) return
        mutableState.value = current.copy(
            pointerCapture = PointerCaptureMode.None,
            revision = SurfaceRevision(current.revision.value + 1L),
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
     * The names come from `SurfaceProperty.fieldName`, which is the one owner of those paths — the same
     * property an update is admitted with, and the same string `OPERATION-CONTRACTS.md` §1.1 registers
     * for `HostSurface.apply`. The check runs before admission, so a `Clear` is refused whole rather
     * than answered with a success for a state that did not move.
     */
    private fun invalidClearField(update: SurfaceUpdate): String? = when {
        update.cursor is PropertyChange.Clear -> SurfaceProperty.Cursor.fieldName
        update.pointerCapture is PropertyChange.Clear -> SurfaceProperty.PointerCapture.fieldName
        update.hitTesting is PropertyChange.Clear -> SurfaceProperty.HitTesting.fieldName
        update.inputDefaultBehavior is PropertyChange.Clear -> SurfaceProperty.InputDefaultBehavior.fieldName
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
     *
     * The capabilities go with the admission, and they go here rather than only at [terminate]: the
     * window a revocation leaves open is exactly the one in which a consumer could still read a
     * promise while the surface already refuses every call, and a capability that survives it would be
     * a claim this task's field is the first to make about something the surface can no longer do. The
     * state stays `Attached` — a revoked surface is still attached until the runtime closes it — but
     * nothing is announced as available any more, which is the "capabilities unavailable no later than
     * the detached state" order the teardown suite pins.
     */
    fun onOwnershipRevoked() {
        if (revoked) return
        revoked = true
        closeAdmission()
        mutableCapabilities.value = unsupportedSurfaceCapabilities()
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
        // The interaction engine is drained with the admission it belongs to, before the terminal
        // state is published: every deferred pending — a primitive the browser never answered — is
        // abandoned with the closed failure and its budget slot released, and the registration is
        // closed so no later dispatch can revive it. Subscribers observe one `Rejected` per pending
        // and then the flow's end, the same sequence the reference surface's own close produces.
        interactionHandler?.let { interaction ->
            interaction.abandonPendingRequests(KadreFailure.Closed(KadreResourceKind.Interaction))
            interaction.close()
        }
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
        // The surface stops admitting, so it holds nothing: ownership is what a later capture would be
        // admitted on, and a closed surface answers `Closed` to that call anyway. The capture it
        // committed stays where it is — the terminal state is what the runtime publishes next, and the
        // port releases the browser effect it holds with the element.
        pointerOwnership.clear()
    }
}

/**
 * One primitive emission awaiting its terminal answer and, until the request that carried it returns,
 * its request id.
 *
 * The record exists because the id the engine allocates — *before* `invokeNative` runs — is not
 * passed to the native call: the terminal callback cannot close over an id it never receives, so it
 * closes over this record, and the wrapped context binds the two the instant the caller of `request`
 * sees the id. [terminal] holds the answer of a primitive whose browser replied synchronously, inside
 * the emission call itself.
 */
private class WebInteractionEmission {
    var requestId: InteractionRequestId? = null
    var terminal: Pair<Boolean, KadreFailure?>? = null
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
 * Two surface-update fields are activated by this phase.
 *
 * `inputDefaultBehavior`: the two members of its enum are named one by one rather than derived from the
 * enum — the capability is this backend's promise, and a promise is written out — and `webTest` pins
 * that set against `InputDefaultBehavior.entries` in both directions, so a member the port cannot
 * honour cannot appear in the enum without failing a test. Honouring `SuppressWhenPossible` is what
 * makes the capability honest: the port asks this surface about every event it dispatches and drops the
 * default of the closed set of categories (`WebInputTranslation.kt`), so the promise is a behaviour and
 * not a label.
 *
 * `pointerCapture`: only `None` and `Confined` are promised, because they are the two the DOM can be
 * asked for — and `Locked` is deliberately outside, since the Pointer Lock API needs a transient user
 * activation and belongs to `InteractionAction.LockPointer` (`DESIGN.md` §9.6). The set
 * is written out here as the promise and stated once more as the rule
 * ([webPointerCaptureIsHonourable]) the commit reads; `webTest` pins the two against each other and
 * against `PointerCaptureMode.entries`, so `Locked` is provably outside both. No member of this target
 * can lock a pointer at all — the port's one capture effect is `setPointerCapture`, asked only for a
 * pointer the surface owns.
 *
 * `cursor`, `customCursor` and `hitTesting` stay `Unsupported(UpdateSurface)`, exactly as they were
 * before these fields were activated: the phase activates these two, and no part of this change claims
 * another one.
 *
 * `handlerInteractions` is not a field of this function's own choosing: the caller names it, because
 * it is the one capability that is a *transition* rather than a promise — the pre-install snapshot
 * ([preInstallSurfaceCapabilities]) claims nothing, and the session configuration publishes the four
 * web actions at the same structural moment it builds the engine that honours them, mirroring how
 * keyboard and pointer are declared by the reducer's own structural observation. `armedInteractions`
 * stays `Unsupported(ArmInteraction)` in both snapshots: no platform implements the arm path, and the
 * handler's token is the only interaction authority this target exposes.
 *
 * This is the snapshot of an attached surface only; [terminate] publishes the all-unsupported one the
 * reference publishes at its own terminal transition, so no field is ever claimed by a surface that
 * stopped admitting.
 */
private fun webSurfaceCapabilities(handlerInteractions: Capability<Set<InteractionKind>>): SurfaceCapabilities =
    SurfaceCapabilities(
        cursor = unsupportedSurfaceCapability(KadreOperation.UpdateSurface),
        customCursor = unsupportedSurfaceCapability(KadreOperation.UpdateSurface),
        pointerCapture = Capability.Supported(
            setOf(PointerCaptureMode.None, PointerCaptureMode.Confined),
            FeatureAvailability.Available,
        ),
        hitTesting = unsupportedSurfaceCapability(KadreOperation.UpdateSurface),
        inputDefaultBehavior = Capability.Supported(
            setOf(InputDefaultBehavior.HostDefault, InputDefaultBehavior.SuppressWhenPossible),
            FeatureAvailability.Available,
        ),
        handlerInteractions = handlerInteractions,
        armedInteractions = unsupportedSurfaceCapability(KadreOperation.ArmInteraction),
        platformAccess = Capability.Supported(Unit, FeatureAvailability.Available),
    )

/**
 * The capability snapshot before the session configuration installed: nothing about the interaction
 * seam is claimed, because nothing of it exists yet — the engine that would honour a handler is built
 * by that installation and never before it.
 */
internal fun preInstallSurfaceCapabilities(): SurfaceCapabilities =
    webSurfaceCapabilities(unsupportedSurfaceCapability(KadreOperation.InstallInteractionHandler))

private fun <T> unsupportedSurfaceCapability(operation: KadreOperation): Capability<T> =
    Capability.Unsupported(KadreFailure.Unsupported(operation))

/**
 * The fields of `SurfaceUpdate`, in the order the update writes them.
 *
 * `SurfaceProperty`'s declaration order *is* that order — `SurfaceUpdate`:104-110 declares `cursor`,
 * `pointerCapture`, `hitTesting`, `inputDefaultBehavior`, and `OPERATION-CONTRACTS.md` §1.1 registers
 * the same four fields in the same order — so the enum is the one owner of it and no second list is
 * written here.
 */
private val SURFACE_UPDATE_FIELDS: List<SurfaceProperty> = SurfaceProperty.entries.toList()

/**
 * The rejections of one update, in the order of the update's fields.
 *
 * The order is not decoration: `SurfaceUpdateOutcome.PartiallyApplied.rejected` names the fields a
 * caller wrote, and the reference emits them in the order it walks them. This surface refuses fields in
 * *two* passes — the capability and the ownership at admission, the browser at commit — so a rejection
 * produced by the second pass would otherwise be appended after every rejection of the first and leave
 * the list out of order (a `Cursor` refused by its capability followed by a `HitTesting` refused the
 * same way, with the `PointerCapture` the browser refused at the end). Sorting by the field order makes
 * the two passes read as one, whatever refused which field.
 */
private fun List<RejectedSurfaceField>.inSurfaceUpdateOrder(): List<RejectedSurfaceField> =
    sortedBy { rejection -> SURFACE_UPDATE_FIELDS.indexOf(rejection.field) }

/**
 * Asserts that a field the web surface has no commit path for was refused by its own capability.
 *
 * [WebHostSurface.apply] commits the fields it supports and *reports* the others: a field that arrives
 * there admitted is a change the surface would neither commit nor reject, and answering `Applied` for an
 * update no state took is exactly the fictitious success the `Clear` guard exists to prevent. Today the
 * adapter makes that unreachable — `cursor` and `hitTesting` carry a blanket `Unsupported(UpdateSurface)`
 * capability, so [admitField] answers `Unchanged` for every value — but "unreachable as long as the
 * capability stays unsupported" is an assumption about a future phase, so it is checked where the change
 * would land instead of being trusted. The day a commit path is owed, this fires with the field in hand,
 * rather than after a consumer was told its update had been applied.
 *
 * `pointerCapture` is not guarded here because it has rules of its own that refuse a value it cannot
 * commit — the honourable modes and the ownership a `Confined` capture needs
 * ([WebHostSurface.admitCaptureAttempt]) — so nothing about it is trusted to a capability; and
 * `inputDefaultBehavior` has had a commit path since it was activated.
 */
private fun PropertyChange<*>.requireRefused(property: SurfaceProperty) {
    check(this is PropertyChange.Unchanged) {
        "${property.fieldName} arrived admitted, but the web surface has no commit for it"
    }
}
