package org.graphiks.kadre.platform.web

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.DropTransferSource
import org.graphiks.kadre.internal.runtime.RuntimeSynchronousInteraction
import org.graphiks.kadre.surface.LogicalPoint

/**
 * The one host-port double every web surface test drives.
 *
 * It hands the surface a fixed initial readback and lets a test push later readbacks on demand
 * through the observer the port was given. A single class is shared so the surface tests of every
 * phase observe the same target-side behaviour; members are added as the surface starts consuming
 * more of the port.
 */
internal class RecordingWebHostPort(
    initial: WebSurfaceMetrics,
    element: Any? = null,
) : WebHostPort {
    override val initialSnapshot: WebSurfaceMetrics = initial
    override val stableIdentity: Any = Any()
    private var metricsObserver: ((WebSurfaceMetrics) -> Unit)? = null
    private var lifecycleObserver: ((WebLifecycleSnapshot) -> Unit)? = null
    private var inputObserver: WebInputObserver? = null
    private var frame: (() -> Unit)? = null
    private var released: Boolean = false

    /**
     * The stimuli the target observed before Kadre was listening, delivered in order the instant the
     * observer is installed.
     *
     * This is the window a real port works in: it installs its observation before the session
     * configuration exists, so input the element already reported is handed over before anything can
     * reduce it. A test sets this before attaching to drive exactly that window.
     */
    var preInstallInput: List<WebInputStimulus> = emptyList()

    /**
     * The host element this port was built around, as the untyped reference the surface lends.
     *
     * [release] drops it, the way the target ports drop the element they hold, so a lease on a
     * surface whose port is gone finds nothing to lend.
     */
    var element: Any? = element
        private set

    override val leasedElement: Any? get() = element

    /** How often the surface released this port; release is terminal and happens exactly once. */
    var releaseCount: Int = 0
        private set

    /** How often the surface cancelled a frame it had registered; a run frame is not a cancel. */
    var frameCancellations: Int = 0
        private set

    /**
     * Every capture effect the surface asked this port to perform, in the order it asked: `true` is a
     * capture taken, `false` a capture released.
     *
     * This is the port's own record of what it was asked for — the observable a case reads when it has
     * to prove that a decision *reached the browser*, or that a field the surface refused reached it not
     * at all. Nothing else about the port is observable from above it, which is the point: the surface
     * decides and this records, exactly as the target ports perform and decide nothing.
     */
    val pointerCaptureRequests: MutableList<Boolean> = mutableListOf()

    /**
     * What the next capture request answers, or `null` when the browser accepts it.
     *
     * It is the contained refusal of a real browser (a pointer it does not consider active), so a case
     * can drive the path where the effect did not happen without a browser: the port reports the failure
     * and the surface rejects the field with it.
     */
    var pointerCaptureFailure: KadreFailure? = null

    /**
     * Whether this double answers the capture member at all.
     *
     * A target port either implements the mechanism or inherits the interface's own default; with this
     * off the double becomes the second kind, which is how a case pins that the default cannot make the
     * surface commit a capture nobody performed.
     */
    var captureImplemented: Boolean = true

    /**
     * The interaction dispatcher the surface installed, or `null` before that structural install.
     *
     * A real DOM port keeps the dispatcher to invoke it inside its own `pointerdown`/`keydown`
     * listeners; this double keeps it so the tests can drive the very same seam, and records the
     * installation so a case can pin that it happens exactly once, with the session configuration.
     */
    var interactionDispatcher: WebInteractionDispatcher? = null
        private set

    /** How often the surface installed an interaction dispatcher; the structural install happens once. */
    var interactionDispatcherInstallations: Int = 0
        private set

    /**
     * Every primitive call the surface asked of this port, in the order it asked: the four fullscreen
     * and pointer-lock members of this phase, named as the member that was called.
     *
     * This is the browser-effect journal of the interaction seam, the counterpart of
     * [pointerCaptureRequests]: an action refused before the native call — an unsupported kind, a
     * `LockPointer` mode the surface rejects — must leave it untouched, which is how the admission
     * ordering is proven rather than asserted.
     */
    val primitiveCalls: MutableList<String> = mutableListOf()

    /** The terminal callbacks the surface handed the port with its primitive calls, in call order. */
    val handedTerminals: MutableList<WebPrimitiveTerminal> = mutableListOf()

    /**
     * What the next [requestFullscreen] answers, or `null` for the port's own refusing default.
     *
     * An effect receives the terminal and answers the emission result: returning a success without
     * firing the terminal is a deferred emission the browser has not answered yet, firing the terminal
     * before returning is a synchronous answer — exactly the two shapes a real DOM port produces.
     */
    var requestFullscreenEffect: ((WebPrimitiveTerminal) -> KadreResult<Unit>)? = null

    /** What the next [exitFullscreen] answers, or `null` for the port's own exiting default. */
    var exitFullscreenEffect: ((WebPrimitiveTerminal) -> KadreResult<Unit>)? = null

    /** What the next [requestPointerLock] answers, or `null` for the port's own refusing default. */
    var requestPointerLockEffect: ((WebPrimitiveTerminal) -> KadreResult<Unit>)? = null

    /** What the next [exitPointerLock] answers, or `null` for the port's own exiting default. */
    var exitPointerLockEffect: ((WebPrimitiveTerminal) -> KadreResult<Unit>)? = null

    /**
     * The order the target acted in, as its own listener would: the interaction dispatch first, the
     * ordinary observation second. The journal is what an ordering case reads alongside the surface's
     * own revisions to prove that the dispatcher ran before the stimulus was admitted.
     */
    val deliveryJournal: MutableList<String> = mutableListOf()

    override fun applyPointerCapture(captured: Boolean): KadreResult<Unit> {
        pointerCaptureRequests += captured
        if (!captureImplemented) return super.applyPointerCapture(captured)
        val failure = pointerCaptureFailure
        return if (failure == null) KadreResult.Success(Unit) else KadreResult.Failure(failure)
    }

    override fun installInteractionDispatcher(dispatcher: WebInteractionDispatcher) {
        check(interactionDispatcher == null) { "this port already installed an interaction dispatcher" }
        interactionDispatcher = dispatcher
        interactionDispatcherInstallations += 1
    }

    /** Records the call, then answers with the effect a test set, or with the port's own default. */
    override fun requestFullscreen(onTerminal: WebPrimitiveTerminal): KadreResult<Unit> {
        primitiveCalls += "requestFullscreen"
        handedTerminals += onTerminal
        return requestFullscreenEffect?.invoke(onTerminal) ?: super.requestFullscreen(onTerminal)
    }

    /** Records the call, then answers with the effect a test set, or with the port's own default. */
    override fun exitFullscreen(onTerminal: WebPrimitiveTerminal): KadreResult<Unit> {
        primitiveCalls += "exitFullscreen"
        handedTerminals += onTerminal
        return exitFullscreenEffect?.invoke(onTerminal) ?: super.exitFullscreen(onTerminal)
    }

    /** Records the call, then answers with the effect a test set, or with the port's own default. */
    override fun requestPointerLock(onTerminal: WebPrimitiveTerminal): KadreResult<Unit> {
        primitiveCalls += "requestPointerLock"
        handedTerminals += onTerminal
        return requestPointerLockEffect?.invoke(onTerminal) ?: super.requestPointerLock(onTerminal)
    }

    /** Records the call, then answers with the effect a test set, or with the port's own default. */
    override fun exitPointerLock(onTerminal: WebPrimitiveTerminal): KadreResult<Unit> {
        primitiveCalls += "exitPointerLock"
        handedTerminals += onTerminal
        return exitPointerLockEffect?.invoke(onTerminal) ?: super.exitPointerLock(onTerminal)
    }

    /**
     * Fires the terminal the surface handed the port with its most recent primitive call, as the
     * browser fires its own terminal event later: the committed answer a `fullscreenchange` carries,
     * or the refusal a `fullscreenerror` carries.
     */
    fun fireLatestTerminal(committed: Boolean) {
        handedTerminals.last().onTerminal(committed)
    }

    /**
     * Acts as the target's own listener acts: the interaction dispatcher first, synchronously, then
     * the ordinary observation — the order the DOM ports of the next task wire into `pointerdown`
     * and `keydown`.
     */
    fun deliverInteractionThenInput(trigger: RuntimeSynchronousInteraction, stimulus: WebInputStimulus) {
        val dispatcher = checkNotNull(interactionDispatcher) { "this port has no interaction dispatcher" }
        deliveryJournal += "interaction"
        dispatcher.dispatch(trigger)
        deliveryJournal += "observation"
        deliverInput(stimulus)
    }

    /** Dispatches one interaction trigger, as a listener whose event carries no ordinary stimulus would. */
    fun deliverInteraction(trigger: RuntimeSynchronousInteraction) {
        val dispatcher = checkNotNull(interactionDispatcher) { "this port has no interaction dispatcher" }
        dispatcher.dispatch(trigger)
    }

    /**
     * Invoked by the first [release], before the port drops the target resources it holds.
     *
     * A test reads it back to observe what the surface had published at the moment Kadre let the
     * element go — an ordering the surface's own terminal transition is otherwise silent about.
     */
    var onRelease: (() -> Unit)? = null

    override fun installLifecycleObserver(observer: (WebLifecycleSnapshot) -> Unit) {
        lifecycleObserver = observer
    }

    override fun installMetricsObserver(observer: (WebSurfaceMetrics) -> Unit) {
        metricsObserver = observer
    }

    /** Pushes [metrics] as if the target had just read the element back; inert once released. */
    fun deliverMetrics(metrics: WebSurfaceMetrics) {
        metricsObserver?.invoke(metrics)
    }

    /** Pushes [snapshot] as if the target had just observed the browsing context. */
    fun deliverLifecycle(snapshot: WebLifecycleSnapshot) {
        lifecycleObserver?.invoke(snapshot)
    }

    override fun installInputObserver(observer: WebInputObserver) {
        check(inputObserver == null) { "this port already installed an input observer" }
        inputObserver = observer
        val observed = preInstallInput
        preInstallInput = emptyList()
        observed.forEach(observer::onObservation)
    }

    /** Pushes [stimulus] as if the target had just observed it; inert once released. */
    fun deliverInput(stimulus: WebInputStimulus) {
        inputObserver?.onObservation(stimulus)
    }

    /**
     * Pushes [stimulus] as the target would, and answers what the channel said about the default of
     * the event that carried it — the question a real port asks inside that event's own callback.
     *
     * Asking here is what lets the surface's answer be proven on both targets without a browser: a
     * real port computes the stimulus, hands it over and applies the answer to the event it is
     * holding, which is this call plus the `preventDefault` the target tests observe on the event.
     */
    fun deliverInputAndAskSuppression(stimulus: WebInputStimulus): Boolean {
        val observer = inputObserver ?: return false
        observer.onObservation(stimulus)
        return observer.suppressDefaultFor(stimulus)
    }

    /**
     * Pushes the browser's own `lostpointercapture`, as the target would report it from its listener.
     *
     * It is a report and not an observation: nothing of the input model describes it, so the channel's
     * third member is what carries it and no stimulus is delivered.
     */
    fun deliverPointerCaptureLost() {
        inputObserver?.onPointerCaptureLost()
    }

    /**
     * Reports a drag entry the element just observed, as a real port does from its `dragenter`
     * listener: the source the target snapshotted, and the position the event carried. The drop
     * dispatch that follows is the surface's, synchronously inside this call.
     */
    fun deliverDropEntered(source: DropTransferSource, position: LogicalPoint) {
        inputObserver?.onDropEntered(source, position)
    }

    /**
     * Asks the channel the question a real port asks inside its `dragover`/`drop` listeners before
     * it may drop their browser default: does the surface hold an active drop offer? The answer is
     * what the journal of preventDefault calls a real target keeps would record.
     */
    fun holdsActiveDropOffer(): Boolean = inputObserver?.holdsActiveDropOffer() ?: false

    /** The browsing context is gone, as a detached or removed document reports it. */
    fun disconnectedSnapshot(): WebLifecycleSnapshot = WebLifecycleSnapshot(
        connected = false,
        inOriginDocument = true,
        documentVisible = true,
        browsingContextFocused = true,
        subtreeFocused = true,
    )

    /** The document is being hidden, as `pagehide` reports it. */
    fun pageHiddenSnapshot(): WebLifecycleSnapshot = disconnectedSnapshot().copy(pageHidden = true)

    override fun scheduleFrame(callback: () -> Unit): WebFrameHandle {
        frame = callback
        return WebFrameHandle {
            frameCancellations += 1
            frame = null
        }
    }

    /**
     * Runs the frame the surface has registered, as the browsing context would.
     *
     * A [release] does not take that registration away: the browser owns the queued callback and the
     * port only drops what it installed itself, so a frame the surface has not cancelled still fires
     * in the window a cooperative stop leaves between the release and the runtime's close.
     */
    fun runFrame() {
        val scheduled = frame ?: return
        frame = null
        scheduled()
    }

    /**
     * Drops the target's own bridges, as a released DOM port does: the readbacks stop here.
     *
     * The registered frame survives it, because only the surface cancels the frame it registered.
     */
    override fun release() {
        releaseCount += 1
        if (released) return
        released = true
        onRelease?.invoke()
        metricsObserver = null
        lifecycleObserver = null
        inputObserver = null
        interactionDispatcher = null
        element = null
    }
}
