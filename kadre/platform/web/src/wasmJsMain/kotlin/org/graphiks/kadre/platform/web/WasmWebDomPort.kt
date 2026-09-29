@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.platform.web

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.internal.runtime.RuntimeSynchronousInteraction
import org.w3c.dom.AddEventListenerOptions
import org.w3c.dom.Document
import org.w3c.dom.HTMLElement
import org.w3c.dom.MutationObserver
import org.w3c.dom.MutationObserverInit
import org.w3c.dom.ShadowRoot
import org.w3c.dom.Window
import org.w3c.dom.events.Event
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toJsArray
import kotlin.js.unsafeCast
import kotlin.math.max

internal class WasmWebDomPort(element: HTMLElement) : WebHostPort {
    private var element: HTMLElement? = element
    private val originDocument: Document = checkNotNull(element.ownerDocument)
    private val originWindow: Window? = originDocument.defaultView
    private var lifecycleObserver: ((WebLifecycleSnapshot) -> Unit)? = null
    private var metricsObserver: ((WebSurfaceMetrics) -> Unit)? = null
    private var inputObserver: WebInputObserver? = null
    private var documentObserver: MutationObserver? = null
    private var shadowRootObserver: MutationObserver? = null
    private var observedShadowRoot: ShadowRoot? = null
    private var resizeObserver: WasmResizeObserver? = null
    private var reconnectAnimationFrame: Int? = null
    private var active: Boolean = false
    private var browsingContextFocused: Boolean = originDocument.hasFocus()
    private var subtreeFocused: Boolean = element.matches(":focus-within")

    /**
     * The identity of the pointer this element holds, or `null` when it holds none.
     *
     * It is the one fact of a capture this port can only know from the browser: `setPointerCapture`
     * names a `pointerId`, and the model's stimulus carries no pointer identity (the runtime keeps a
     * single one per surface, D11). So the identity is recorded when an observation of a delivered kind
     * reports a button pressed here, and forgotten when the browser reports no button of it down any
     * more — never decided, only read: whether a capture may be asked for at all is the surface's rule
     * ([WebPointerOwnership]), and this member only answers *which* pointer a request is about.
     *
     * **Two bookkeepings, one authority.** The identity here and the surface's pressed-button set are
     * derived from the same observations but read different facts — a `pointerId` of the event in hand
     * against the buttons the model recorded — so a contrived sequence can leave them one step apart (a
     * `Released` whose `buttons` is not empty clears the surface's button but not this identity, a
     * `FocusLost` keeps this identity while the surface forgets its pointer). The **surface's ownership
     * is authoritative**: this member never admits anything, and a divergence only shows as the
     * mechanism answering the failure of [applyPointerCapture] where the ownership gate would have said
     * `InteractionRequired(Missing)` — a refusal either way, never a capture taken for a pointer the
     * surface does not hold.
     */
    private var heldPointerId: Int? = null

    /**
     * The motion of the one pointer the runtime keeps per element (D11): every pointer observation is
     * recorded there, so a motion measures from the last position the browser reported, and the exit
     * forgets it so a re-entry measures from its own entry point. The rule itself is shared with the
     * JS port ([WebPointerMotion]); only reading a position is this target's.
     */
    private val pointerMotion: WebPointerMotion = WebPointerMotion()

    /**
     * The interaction dispatcher the surface installed with its session configuration, or `null`
     * before that moment and after [release].
     *
     * The two press listeners invoke it synchronously, inside their own callback and before the
     * ordinary stimulus of the same event is enqueued (the AppKit order, `DESIGN.md:983-989`): the
     * trigger is DOM-free, built with the very mappings the ordinary observation is built with, so
     * the two cannot disagree about the event the element saw.
     */
    private var interactionDispatcher: WebInteractionDispatcher? = null

    /**
     * The primitive emissions whose terminal listeners are still installed on the document.
     *
     * More than one can be live at a time — a second dispatch may emit while the browser has not
     * answered the first — so a list, not a slot: each emission removes itself the moment it
     * settles, and [release] abandons the survivors, whose pendings the surface has already
     * completed with the closed failure.
     */
    private val pendingPrimitiveEmissions: MutableList<WebPrimitiveEmission> = mutableListOf()

    /**
     * The scroll-coalescing frontier of this element ([WebScrollBoundary]) and the animation-frame
     * registration that reports the one fact of its rule no wheel event carries: that the browsing
     * context entered a new frame.
     */
    private val scrollBoundary: WebScrollBoundary = WebScrollBoundary()
    private val scrollFrame: WasmAnimationFrameMarker = WasmAnimationFrameMarker(originWindow) {
        scrollBoundary.frameOpened()
    }

    /**
     * The `wheel` registration is the one listener that declares its options: a passive listener
     * could never suppress the browser's default, and the surface decides later whether it does.
     */
    private val wheelListenerOptions = AddEventListenerOptions(passive = false)

    private val visibilityListener: (Event) -> Unit = {
        safely { deliverSnapshot() }
    }
    private val windowFocusListener: (Event) -> Unit = {
        safely {
            browsingContextFocused = true
            deliverSnapshot()
        }
    }
    private val windowBlurListener: (Event) -> Unit = {
        safely {
            browsingContextFocused = false
            deliverSnapshot()
        }
    }
    private val subtreeFocusInListener: (Event) -> Unit = {
        safely {
            subtreeFocused = true
            deliverSnapshot()
        }
    }
    private val subtreeFocusOutListener: (Event) -> Unit = {
        safely {
            subtreeFocused = false
            deliverSnapshot()
        }
    }
    private val pagehideListener: (Event) -> Unit = {
        safely { deliverSnapshot(pageHidden = true) }
    }

    /**
     * One `keydown`: the browser's own physical key, logical key, location and modifiers.
     *
     * It is one of the two listeners that dispatch an interaction first — synchronously, inside this
     * very callback, before the observation below is enqueued (the AppKit order,
     * `DESIGN.md:983-989`), which is where the event's transient activation still holds — and one of
     * the two that route through [suppressDefaultFor], because a key press is one of the two events
     * whose page-level default this phase delivers: some keys scroll the document. Which ones, and
     * whether they are suppressed at all, is not decided here — the port hands the observation over
     * and applies the answer it gets.
     */
    private val keyDownListener: (Event) -> Unit = { event ->
        safely {
            wasmKeyboardEventOrNull(event)?.let { keyboard ->
                dispatchInteractionFor(keyboard)
                suppressDefaultFor(event, wasmKeyStimulus(keyboard, pressed = true))
            }
        }
    }

    /** One `keyup`. The port reads no focus here: the lifecycle reduction already owns that. */
    private val keyUpListener: (Event) -> Unit = { event ->
        safely { wasmKeyboardEventOrNull(event)?.let { deliverInput(wasmKeyStimulus(it, pressed = false)) } }
    }

    private val pointerEnterListener: (Event) -> Unit = { event ->
        safely { deliverPointerEntered(wasmPointerEventOrNull(event)) }
    }

    private val pointerMoveListener: (Event) -> Unit = { event ->
        safely { deliverPointerMoved(wasmPointerEventOrNull(event)) }
    }

    /**
     * One `pointerdown`: the interaction dispatch first, synchronously, in this event's own callback
     * and before the ordinary stimulus of it is enqueued (the AppKit order, `DESIGN.md:983-989`) —
     * the frame whose transient activation is the authority a fullscreen or pointer-lock request
     * needs — then the ordinary observation, which continues normally.
     */
    private val pointerDownListener: (Event) -> Unit = { event ->
        safely {
            val pointer = wasmPointerEventOrNull(event)
            dispatchInteractionFor(pointer)
            deliverPointerButton(pointer, PointerButtonState.Pressed)
        }
    }

    private val pointerUpListener: (Event) -> Unit = { event ->
        safely { deliverPointerButton(wasmPointerEventOrNull(event), PointerButtonState.Released) }
    }

    /** One `pointerleave` over the element and its whole subtree. */
    private val pointerLeaveListener: (Event) -> Unit = { event ->
        safely { deliverPointerLeft(wasmPointerEventOrNull(event)) }
    }

    /**
     * One `pointercancel`: the browser revoked the contact, so the pointer is reconciled by dropping
     * it with everything it held, which is what the reducer's pointer exit does.
     */
    private val pointerCancelListener: (Event) -> Unit = { event ->
        safely { deliverPointerLeft(wasmPointerEventOrNull(event)) }
    }

    /**
     * One `lostpointercapture`: the browser says it no longer confines that pointer to this element.
     *
     * It is not an input observation — nothing of the model describes a capture — so it is reported to
     * the channel, which is the one place the surface can hear it: the surface committed the capture,
     * and only the browser knows when it ended. The report names no mode and interprets nothing: the
     * port read the browser's own event and says so.
     *
     * Only the pointer this port holds is reported: a capture lost for a pointer this element never
     * asked about is not a claim this surface ever made.
     */
    private val lostPointerCaptureListener: (Event) -> Unit = { event ->
        safely {
            val pointer = wasmPointerEventOrNull(event) ?: return@safely
            if (wasmPointerKind(pointer) == null) return@safely
            if (pointer.pointerId != heldPointerId) return@safely
            inputObserver?.onPointerCaptureLost()
        }
    }

    /**
     * One `wheel`, observed at the frontier of the element's own scroll history.
     *
     * The wheel is recorded on the shared boundary whether or not its delta is deliverable, so the
     * frontier describes what the browser delivered; the frame registration keeps the boundary able
     * to tell the first wheel of a new frame from the one after it in the same frame.
     *
     * The listener is registered as non-passive because a wheel is the event whose default the surface
     * is asked about, and a passive listener could never drop it. The port itself still decides
     * nothing: [suppressDefaultFor] asks and applies. A wheel Kadre cannot deliver — a page-mode delta
     * (D9), a component that is not finite — never reaches the question at all, and keeps the browser's
     * default: suppression is asked for an event Kadre was handed, never a default Kadre merely
     * noticed.
     */
    private val wheelListener: (Event) -> Unit = { event ->
        safely {
            wasmWheelEventOrNull(event)?.let { wheel ->
                val boundary = scrollBoundary.advance(wheel.deltaMode, wheel.buttons)
                scrollFrame.arm()
                wasmScrollStimulus(wheel, boundary)?.let { stimulus -> suppressDefaultFor(event, stimulus) }
            }
        }
    }

    override val stableIdentity: Any get() = checkNotNull(element)
    override val leasedElement: Any? get() = element
    override val initialSnapshot: WebSurfaceMetrics = element.surfaceMetrics(originWindow?.devicePixelRatio ?: 1.0)
    override val initialLifecycleSnapshot: WebLifecycleSnapshot = lifecycleSnapshot(element)

    override fun installLifecycleObserver(observer: (WebLifecycleSnapshot) -> Unit) {
        check(lifecycleObserver == null)
        lifecycleObserver = observer
        active = true
        originDocument.addEventListener("visibilitychange", visibilityListener)
        originWindow?.addEventListener("focus", windowFocusListener)
        originWindow?.addEventListener("blur", windowBlurListener)
        originWindow?.addEventListener("pagehide", pagehideListener)
        element?.addEventListener("focusin", subtreeFocusInListener)
        element?.addEventListener("focusout", subtreeFocusOutListener)

        installDocumentObserver()
        val current = checkNotNull(element)
        if (current.isConnected) updateShadowRootObserver(current) else scheduleReconnect()
    }

    override fun installMetricsObserver(observer: (WebSurfaceMetrics) -> Unit) {
        check(metricsObserver == null)
        metricsObserver = observer
        val current = element ?: return
        val installed = createWasmResizeObserver { runCatching { deliverMetrics() } }
        resizeObserver = installed
        installed.observe(current.unsafeCast<JsAny>())
        deliverMetrics()
    }

    /**
     * Installs the input listeners of the element, and nothing on the document or the window.
     *
     * Keyboard, pointer and wheel events are all listened for on the element itself: the host makes
     * it focusable, and a global listener would observe input the surface does not own. A loss of
     * activation needs no listener here either — the lifecycle reduction already observes
     * `focusout`/`blur`/`visibilitychange`/`pagehide`, and the surface publishes the single reset
     * that loss owes, so a second path would produce a second reset for the same loss.
     *
     * The same isolation is what bounds the suppression: a browser default is only ever dropped for an
     * event this element was handed, so nothing here can act on the page as a whole.
     */
    override fun installInputObserver(observer: WebInputObserver) {
        check(inputObserver == null)
        inputObserver = observer
        element?.addEventListener("keydown", keyDownListener)
        element?.addEventListener("keyup", keyUpListener)
        element?.addEventListener("pointerenter", pointerEnterListener)
        element?.addEventListener("pointermove", pointerMoveListener)
        element?.addEventListener("pointerdown", pointerDownListener)
        element?.addEventListener("pointerup", pointerUpListener)
        element?.addEventListener("pointerleave", pointerLeaveListener)
        element?.addEventListener("pointercancel", pointerCancelListener)
        element?.addEventListener("lostpointercapture", lostPointerCaptureListener)
        element?.addEventListener("wheel", wheelListener, wheelListenerOptions)
    }

    /**
     * Performs the one browser effect a capture decision has, containing the browser's own refusal.
     *
     * The port decides nothing here: the surface has already decided that this pointer may be captured —
     * or released — and this member is the mechanism that asks the browser for it. [captured] names the
     * request, and the pointer it is about is the one the element observed pressed ([heldPointerId]),
     * because a capture names a `pointerId` and only this port ever saw one.
     *
     * Three answers, and each is a fact about the mechanism rather than a policy:
     *
     * - a release asked for while no pointer is held is a success: there is no capture to end, and the
     *   DOM's own `releasePointerCapture` for a pointer that holds none is a no-op. Nothing is asked of
     *   the browser, because there is nothing for it to answer;
     * - a capture asked for while no pointer is held is the failure of this mechanism: the browser holds
     *   no pointer this port could ask about, and a request it never performed may not be answered with
     *   a success;
     * - a call the browser refuses — `setPointerCapture` throws for a pointer the browser does not
     *   consider active — is *contained*: it is turned into the same failure and never thrown, because
     *   this call is made from inside the DOM callback of the event that led to the decision
     *   (`WEB-IMPLEMENTATION-ROADMAP.md` §3.4).
     *
     * The failure is a `PlatformFailure` of this platform with the one code of this seam: the call really
     * crosses the browser's DOM API, and the surface reports this failure as a rejected field, which
     * `OPERATION-CONTRACTS.md` §3 admits for it.
     */
    override fun applyPointerCapture(captured: Boolean): KadreResult<Unit> {
        val pointerId = heldPointerId
        if (pointerId == null) {
            return if (captured) {
                KadreResult.Failure(pointerCaptureFailure())
            } else {
                KadreResult.Success(Unit)
            }
        }
        val current = element ?: return KadreResult.Failure(pointerCaptureFailure())
        return if (runCatching { wasmApplyPointerCapture(current, pointerId, captured) }.isSuccess) {
            KadreResult.Success(Unit)
        } else {
            KadreResult.Failure(pointerCaptureFailure())
        }
    }

    /**
     * Keeps the dispatcher the surface installed, for the press listeners to invoke.
     *
     * The surface installs it once, with the session configuration that builds the interaction
     * engine, so the check is the one guarantee that keeps a second installation from silently
     * replacing the channel the first listeners were handed. [release] drops it with the bridges.
     */
    override fun installInteractionDispatcher(dispatcher: WebInteractionDispatcher) {
        check(interactionDispatcher == null) { "this port already installed an interaction dispatcher" }
        interactionDispatcher = dispatcher
    }

    /**
     * Emits the fullscreen request of the browser, synchronously, in the frame the action was
     * admitted in.
     *
     * The emission is the synchronous answer ([KadreResult.Success] — the primitive is out, the
     * verdict is not in yet); the verdict is the browser's, and it is collected on the two channels
     * the DOM answers a fullscreen request with, both hooked here and both one-shot through
     * [WebPrimitiveEmission]:
     *
     * - the `fullscreenchange` the browser fires **on the element it took fullscreen** — the
     *   committed answer, and this element's own word only: the gate on the event's target keeps a
     *   change that belongs to another element (or the document-fired one of an exit) from answering
     *   a request this element made;
     * - the `fullscreenerror` — fired on the document or on the failing element, bubbling to the
     *   document listener either way — and the rejected promise the same failure carries; the DOM
     *   exposes no reason, so both deliver the one honest refusal ([refusalFailure]'s code) through
     *   the terminal, and the promise's rejection is caught whatever the terminal already said, so
     *   no rejection of this port is ever left floating.
     *
     * Anything the browser would not even let this port ask — a read or a call that throws — is
     * contained as the same refusal, with the listeners withdrawn, because no terminal will ever
     * fire for a call that never happened.
     */
    override fun requestFullscreen(onTerminal: WebPrimitiveTerminal): KadreResult<Unit> {
        val current = element ?: return KadreResult.Failure(refusalFailure(WEB_FULLSCREEN_DOMAIN))
        val emission = newPrimitiveEmission(onTerminal)
        return try {
            installTerminalListener(emission, WEB_FULLSCREEN_CHANGE_EVENT, committed = true) { event ->
                event.target === current
            }
            installTerminalListener(emission, WEB_FULLSCREEN_ERROR_EVENT, committed = false) { _ -> true }
            current.requestFullscreen().onRejection { emission.settle(false) }
            KadreResult.Success(Unit)
        } catch (cause: Throwable) {
            emission.abandon()
            KadreResult.Failure(refusalFailure(WEB_FULLSCREEN_DOMAIN))
        }
    }

    /**
     * Emits the exit-fullscreen request of the browser, or answers it without one.
     *
     * The exit is only asked where there is a fullscreen to leave: `document.fullscreenElement` is
     * the browser's own state, and reading it null answers the action synchronously with the
     * committed answer — there is nothing to ask the browser for, and a `document.exitFullscreen`
     * call for a document that holds no fullscreen element would be a call with no decision behind
     * it (the zero-call exit the smoke ledger proves). Otherwise the exit is emitted like any
     * request, and the `fullscreenchange` the browser fires on the document — the fullscreen element
     * is already cleared when it fires — is the committed answer, with the rejected promise the
     * refusal's other channel; no promise of this port is ever left floating. A read of the state
     * that throws is the refusal too, contained like every call of this seam.
     */
    override fun exitFullscreen(onTerminal: WebPrimitiveTerminal): KadreResult<Unit> {
        val fullscreenElement = try {
            originDocument.fullscreenElement
        } catch (cause: Throwable) {
            return KadreResult.Failure(refusalFailure(WEB_FULLSCREEN_DOMAIN))
        }
        if (fullscreenElement == null) {
            onTerminal.onTerminal(true)
            return KadreResult.Success(Unit)
        }
        val emission = newPrimitiveEmission(onTerminal)
        return try {
            installTerminalListener(emission, WEB_FULLSCREEN_CHANGE_EVENT, committed = true) { _ -> true }
            originDocument.exitFullscreen().onRejection { emission.settle(false) }
            KadreResult.Success(Unit)
        } catch (cause: Throwable) {
            emission.abandon()
            KadreResult.Failure(refusalFailure(WEB_FULLSCREEN_DOMAIN))
        }
    }

    /**
     * Emits the pointer-lock request of the browser, synchronously, in the frame the action was
     * admitted in.
     *
     * The emission/terminal split is [requestFullscreen]'s, and so are the two channels of the
     * verdict: the `pointerlockchange` and `pointerlockerror` the Pointer Lock API fires **on the
     * document**, plus the promise form of Chromium's own `requestPointerLock` — preferred by this
     * reading, and absorbed as absent when a browser without it answers `undefined`, which leaves
     * the error event the one refusal path. No gate is possible on the change: the event targets
     * the document, so the browser's word arrives as it is, and the one-shot settlement plus the
     * surface's single-request serialisation bound what a second pointer's word could reach.
     */
    override fun requestPointerLock(onTerminal: WebPrimitiveTerminal): KadreResult<Unit> {
        val current = element ?: return KadreResult.Failure(refusalFailure(WEB_POINTER_LOCK_DOMAIN))
        val emission = newPrimitiveEmission(onTerminal)
        return try {
            installTerminalListener(emission, WEB_POINTER_LOCK_CHANGE_EVENT, committed = true) { _ -> true }
            installTerminalListener(emission, WEB_POINTER_LOCK_ERROR_EVENT, committed = false) { _ -> true }
            wasmRequestPointerLock(current.unsafeCast<JsAny>())?.onRejection { emission.settle(false) }
            KadreResult.Success(Unit)
        } catch (cause: Throwable) {
            emission.abandon()
            KadreResult.Failure(refusalFailure(WEB_POINTER_LOCK_DOMAIN))
        }
    }

    /**
     * Emits the unlock-pointer request of the browser, or answers it without one.
     *
     * The exit is only asked where this element is what holds the lock: `document.pointerLockElement`
     * is the browser's own state, and reading it anything but this element — another element's lock,
     * or no lock at all — answers the action synchronously with the committed answer and zero browser
     * calls. Otherwise the exit is emitted, and the `pointerlockchange` the browser fires on the
     * document is the committed answer: the Pointer Lock API produces no promise and no error event
     * for an unlock, so the change is the one channel the verdict arrives on. A read of the state
     * that throws is the refusal too, contained like every call of this seam.
     */
    override fun exitPointerLock(onTerminal: WebPrimitiveTerminal): KadreResult<Unit> {
        val lockedElement = try {
            wasmPointerLockElement(originDocument.unsafeCast<JsAny>())
        } catch (cause: Throwable) {
            return KadreResult.Failure(refusalFailure(WEB_POINTER_LOCK_DOMAIN))
        }
        if (lockedElement == null || lockedElement !== element) {
            onTerminal.onTerminal(true)
            return KadreResult.Success(Unit)
        }
        val emission = newPrimitiveEmission(onTerminal)
        return try {
            installTerminalListener(emission, WEB_POINTER_LOCK_CHANGE_EVENT, committed = true) { _ -> true }
            wasmExitPointerLock(originDocument.unsafeCast<JsAny>())
            KadreResult.Success(Unit)
        } catch (cause: Throwable) {
            emission.abandon()
            KadreResult.Failure(refusalFailure(WEB_POINTER_LOCK_DOMAIN))
        }
    }

    /** Frames belong to the element's browsing context, which may not carry this module's global. */
    override fun scheduleFrame(callback: () -> Unit): WebFrameHandle {
        val browserWindow = originWindow ?: return WebFrameHandle { }
        return wasmScheduleFrame(browserWindow, callback)
    }

    override fun release() {
        if (!active && element == null) return
        active = false
        runCatching { originDocument.removeEventListener("visibilitychange", visibilityListener) }
        runCatching { originWindow?.removeEventListener("focus", windowFocusListener) }
        runCatching { originWindow?.removeEventListener("blur", windowBlurListener) }
        runCatching { originWindow?.removeEventListener("pagehide", pagehideListener) }
        runCatching { element?.removeEventListener("focusin", subtreeFocusInListener) }
        runCatching { element?.removeEventListener("focusout", subtreeFocusOutListener) }
        runCatching { element?.removeEventListener("keydown", keyDownListener) }
        runCatching { element?.removeEventListener("keyup", keyUpListener) }
        runCatching { element?.removeEventListener("pointerenter", pointerEnterListener) }
        runCatching { element?.removeEventListener("pointermove", pointerMoveListener) }
        runCatching { element?.removeEventListener("pointerdown", pointerDownListener) }
        runCatching { element?.removeEventListener("pointerup", pointerUpListener) }
        runCatching { element?.removeEventListener("pointerleave", pointerLeaveListener) }
        runCatching { element?.removeEventListener("pointercancel", pointerCancelListener) }
        runCatching { element?.removeEventListener("lostpointercapture", lostPointerCaptureListener) }
        runCatching { element?.removeEventListener("wheel", wheelListener, wheelListenerOptions) }
        // The terminal listeners of the primitives still awaiting the browser's answer are the last
        // bridges this port holds into the browsing context, and they go with the rest of them: a
        // late fullscreenchange or pointerlockchange must not answer an emission nobody is waiting
        // on, and the surface has already abandoned their pendings with the closed failure.
        runCatching { pendingPrimitiveEmissions.toList().forEach { it.abandon() } }
        pendingPrimitiveEmissions.clear()
        // The capture this port may hold is ended with the element it was taken on: one that outlived
        // the port would keep routing that pointer's events to an element Kadre stopped reading, which
        // is a browser effect outliving the decision that asked for it. Contained like every call of
        // this seam — there is nobody left to report a refusal to.
        element?.let { current -> heldPointerId?.let { id -> runCatching { wasmApplyPointerCapture(current, id, false) } } }
        heldPointerId = null
        runCatching { documentObserver?.disconnect() }
        runCatching { shadowRootObserver?.disconnect() }
        runCatching { resizeObserver?.disconnect() }
        // The scroll frontier owns a frame registration of its own, so it is cancelled with the other
        // per-element resources rather than left to fire for an element the port no longer holds, and
        // both shared trackers forget what they observed of this element.
        runCatching { scrollFrame.close() }
        scrollBoundary.clear()
        pointerMotion.clear()
        reconnectAnimationFrame?.let { animationFrame ->
            runCatching { originWindow?.cancelAnimationFrame(animationFrame) }
        }
        reconnectAnimationFrame = null
        documentObserver = null
        observedShadowRoot = null
        shadowRootObserver = null
        resizeObserver = null
        interactionDispatcher = null
        lifecycleObserver = null
        metricsObserver = null
        inputObserver = null
        element = null
    }

    private fun handleMutationBatch() {
        val current = element ?: return
        deliverSnapshot()
        if (!active || current.ownerDocument !== originDocument) return
        if (current.isConnected) {
            cancelReconnect()
            updateShadowRootObserver(current)
        } else {
            disconnectShadowRootObserver()
            scheduleReconnect()
        }
    }

    private fun installDocumentObserver() {
        check(documentObserver == null)
        val next = MutationObserver { _, _ -> safely { handleMutationBatch() } }
        documentObserver = next
        next.observe(
            originDocument,
            MutationObserverInit(
                childList = true,
                attributes = true,
                subtree = true,
                attributeFilter = emptyList<JsString>().toJsArray(),
            ),
        )
    }

    private fun updateShadowRootObserver(current: HTMLElement) {
        val shadowRoot = current.getRootNode() as? ShadowRoot
        if (observedShadowRoot === shadowRoot) return
        disconnectShadowRootObserver()
        if (shadowRoot == null) return
        val next = MutationObserver { _, _ -> safely { handleMutationBatch() } }
        shadowRootObserver = next
        observedShadowRoot = shadowRoot
        next.observe(
            shadowRoot,
            MutationObserverInit(
                childList = true,
                attributes = true,
                subtree = true,
                attributeFilter = emptyList<JsString>().toJsArray(),
            ),
        )
    }

    private fun disconnectShadowRootObserver() {
        shadowRootObserver?.disconnect()
        shadowRootObserver = null
        observedShadowRoot = null
    }

    private fun scheduleReconnect() {
        if (!active || reconnectAnimationFrame != null) return
        val browserWindow = originWindow ?: return
        reconnectAnimationFrame = browserWindow.requestAnimationFrame {
            reconnectAnimationFrame = null
            safely {
                val current = element ?: return@safely
                deliverSnapshot()
                if (!active || current.ownerDocument !== originDocument) return@safely
                if (current.isConnected) updateShadowRootObserver(current) else scheduleReconnect()
            }
        }
    }

    private fun cancelReconnect() {
        val animationFrame = reconnectAnimationFrame ?: return
        reconnectAnimationFrame = null
        runCatching { originWindow?.cancelAnimationFrame(animationFrame) }
    }

    private fun deliverSnapshot(pageHidden: Boolean = false) {
        val current = element ?: return
        subtreeFocused = current.matches(":focus-within")
        lifecycleObserver?.invoke(lifecycleSnapshot(current, pageHidden))
    }

    /** Reads the element back; self-guarding, so a delivery after [release] is a no-op. */
    private fun deliverMetrics() {
        val current = element ?: return
        val observer = metricsObserver ?: return
        observer(current.surfaceMetrics(originWindow?.devicePixelRatio ?: 1.0))
    }

    /**
     * One pointer entry: the first observation of a pointer over the element's subtree.
     *
     * The entry records the position on the shared motion, so the first motion of the pointer
     * measures from where it entered.
     */
    private fun deliverPointerEntered(pointer: WasmPointerEvent?) {
        if (pointer == null) return
        val kind = wasmPointerKind(pointer) ?: return
        val current = element ?: return
        val position = wasmPointerPosition(current, pointer)
        pointerMotion.record(position)
        deliverInput(WebInputStimulus.PointerEntered(position = position, kind = kind))
    }

    /**
     * One pointer motion, with the motion it made since the previous pointer observation.
     *
     * The DOM reports no delta for a `pointermove`, so the motion comes from the shared rule that
     * measures one observation against the last ([WebPointerMotion]); the runtime coalesces motions
     * by summing deltas, which is why an incremental delta is the only one it can carry.
     */
    private fun deliverPointerMoved(pointer: WasmPointerEvent?) {
        if (pointer == null) return
        val kind = wasmPointerKind(pointer) ?: return
        val current = element ?: return
        val position = wasmPointerPosition(current, pointer)
        val delta = pointerMotion.advance(position)
        deliverInput(
            WebInputStimulus.PointerMoved(
                position = position,
                delta = delta,
                pressure = wasmPointerPressure(pointer),
                kind = kind,
                pen = wasmPointerPenState(kind, pointer),
            ),
        )
    }

    /**
     * One pointer-button transition, copied with its position, its pressure and its own kind.
     *
     * The transition moves the shared motion too, so a motion that follows it measures from the
     * position the browser reported with it instead of repeating movement already reported.
     */
    private fun deliverPointerButton(pointer: WasmPointerEvent?, buttonState: PointerButtonState) {
        if (pointer == null) return
        val kind = wasmPointerKind(pointer) ?: return
        val current = element ?: return
        val position = wasmPointerPosition(current, pointer)
        pointerMotion.record(position)
        // The pointer the element holds: the one the browser just reported pressed here, forgotten the
        // moment it reports no button of it down any more. The reading is the browser's own — the
        // model's stimulus carries no pointer identity — and it is what a capture request names.
        when (buttonState) {
            PointerButtonState.Pressed -> heldPointerId = pointer.pointerId
            PointerButtonState.Released -> if (pointer.buttons == 0) heldPointerId = null
        }
        deliverInput(
            WebInputStimulus.PointerButtonChanged(
                button = webPointerButton(pointer.button),
                buttonState = buttonState,
                position = position,
                pressure = wasmPointerPressure(pointer),
                kind = kind,
                pen = wasmPointerPenState(kind, pointer),
            ),
        )
    }

    /**
     * One pointer exit: a leave, or a cancellation the browser reported.
     *
     * A cancellation is not a button release but a revocation of the contact, so it is delivered as
     * the exit the reducer reconciles the pointer with — the same member a leave uses, with the kind
     * the browser reported for the pointer that went away. A touch pointer is refused here as it is
     * everywhere else, because nothing of it was ever delivered and the surface declares touch
     * unsupported, and the refusal comes first: a pointer this port does not deliver must not even
     * disturb the motion of the one it does, which a touch exit reaching the same listener otherwise
     * would by forgetting where the pointer was.
     */
    private fun deliverPointerLeft(pointer: WasmPointerEvent?) {
        if (pointer == null) return
        val kind = wasmPointerKind(pointer) ?: return
        pointerMotion.clear()
        // The pointer is gone from this element, cancelled or left, so there is no capture to ask for on
        // it any more — the browser ends one implicitly in both cases.
        heldPointerId = null
        deliverInput(WebInputStimulus.PointerLeft(kind = kind))
    }

    /**
     * The interaction trigger of one pointer press, dispatched synchronously, or nothing at all.
     *
     * The trigger is read with the very mappings the ordinary stimulus of the same event is read
     * with — kind, position, pressure, button — so the interaction and the observation cannot
     * disagree about the event the element saw, and the pressure reaches the interaction exactly as
     * the model carries it, never narrowed. A pointer kind this phase refuses delivers no
     * observation, so it dispatches no interaction either: the kind is the ordinary path's own gate.
     * With no dispatcher installed — before the session configuration, or after [release] — there is
     * nothing to invoke, and the ordinary stimulus continues as it always has.
     */
    private fun dispatchInteractionFor(pointer: WasmPointerEvent?) {
        val dispatcher = interactionDispatcher ?: return
        if (pointer == null) return
        wasmPointerKind(pointer) ?: return
        val current = element ?: return
        dispatcher.dispatch(
            RuntimeSynchronousInteraction.PointerPressed(
                button = webPointerButton(pointer.button),
                position = wasmPointerPosition(current, pointer),
                pressure = wasmPointerPressure(pointer),
            ),
        )
    }

    /**
     * The interaction trigger of one key press: the physical key the ordinary stimulus of the same
     * event carries, dispatched synchronously before that stimulus is enqueued.
     */
    private fun dispatchInteractionFor(keyboard: WasmKeyboardEvent) {
        interactionDispatcher?.dispatch(
            RuntimeSynchronousInteraction.KeyPressed(physicalKey = webPhysicalKey(keyboard.code)),
        )
    }

    /**
     * Hands one observation over and answers what the channel said about the default of the event that
     * carried it.
     *
     * The answer is `false` when no observer is installed or when the channel does not answer at all,
     * so a port that is not attached suppresses nothing — and asking is always part of delivering, so
     * a stimulus is never held back by the question.
     */
    private fun deliverInput(stimulus: WebInputStimulus): Boolean {
        val observer = inputObserver ?: return false
        observer.onObservation(stimulus)
        return observer.suppressDefaultFor(stimulus)
    }

    /**
     * The one place this port can drop a browser default, and the only one.
     *
     * The port holds no policy: it hands the observation over and asks the same channel whether the
     * default action of the event that just carried it must be dropped, then applies that answer to
     * that very event, inside that event's own callback. Under `HostDefault` — and for every category
     * Kadre does not suppress — the answer is `false`, so nothing is dropped at all; only a surface
     * that was explicitly told to suppress a category the observation belongs to gets a
     * `preventDefault` out of this port.
     *
     * Only the two listeners whose event has a page-level default route through it: the wheel, whose
     * default scrolls or zooms the browsing context, and the key press, whose scroll keys move the
     * document. Every other listener delivers through [deliverInput] and ignores the answer. No
     * listener of the document, the window or an ancestor calls this, and this file contains no other
     * `preventDefault` anywhere.
     */
    private fun suppressDefaultFor(event: Event, stimulus: WebInputStimulus) {
        if (deliverInput(stimulus)) event.preventDefault()
    }

    /**
     * Creates the one-shot settlement of one primitive emission, tracked until it settles.
     *
     * The tracking is what teardown reads: a port released while the browser has not answered
     * withdraws its live emissions ([release]), whose listeners are the only bridges it still holds
     * into the browsing context. The emission removes itself from the list the moment it settles,
     * first answer or withdrawal, so the list holds only what is still listening.
     */
    private fun newPrimitiveEmission(onTerminal: WebPrimitiveTerminal): WebPrimitiveEmission =
        WebPrimitiveEmission(onTerminal).also { emission ->
            pendingPrimitiveEmissions += emission
            emission.addRemoval { pendingPrimitiveEmissions.remove(emission) }
        }

    /**
     * Hooks one terminal listener of [type] onto the document, settled by [emission] only.
     *
     * The listener lives on the document because that is where the browser fires every terminal of
     * these primitives that is not fired on the element itself — and the element-fired ones bubble
     * to it — so one listener site hears the browser's word whichever way the DOM delivers it.
     * [gate] answers whether the event is the answer to *this* emission's question: the request's
     * change is this element's own word, and every other terminal is ungated. The removal is the
     * emission's, so the first terminal takes its listener down with it.
     */
    private fun installTerminalListener(
        emission: WebPrimitiveEmission,
        type: String,
        committed: Boolean,
        gate: (Event) -> Boolean,
    ) {
        val listener: (Event) -> Unit = { event -> if (gate(event)) emission.settle(committed) }
        originDocument.addEventListener(type, listener)
        emission.addRemoval { originDocument.removeEventListener(type, listener) }
    }

    private fun lifecycleSnapshot(
        current: HTMLElement,
        pageHidden: Boolean = false,
    ): WebLifecycleSnapshot = WebLifecycleSnapshot(
        connected = current.isConnected,
        inOriginDocument = current.ownerDocument === originDocument,
        documentVisible = originDocument.unsafeCast<WasmDocumentVisibility>().visibilityState.toString() == "visible",
        browsingContextFocused = browsingContextFocused,
        subtreeFocused = subtreeFocused,
        pageHidden = pageHidden,
    )

    private fun safely(block: () -> Unit) {
        if (!active) return
        runCatching(block)
    }
}

/**
 * The one readback of the attached element: CSS pixels of the border box and the device pixel
 * ratio of the browsing context that owns it.
 *
 * A collapsed element still publishes a positive size, and a browsing context that reports no
 * usable ratio falls back to the unscaled one, so the result always satisfies the portable model.
 */
private fun HTMLElement.surfaceMetrics(scaleFactor: Double): WebSurfaceMetrics = WebSurfaceMetrics(
    logicalWidth = max(clientWidth.toDouble(), 1.0),
    logicalHeight = max(clientHeight.toDouble(), 1.0),
    scaleFactor = if (scaleFactor.isFinite() && scaleFactor > 0.0) scaleFactor else 1.0,
)

/** The port's own readback, exposed to the target tests so they exercise it instead of a copy. */
internal fun HTMLElement.readSurfaceMetricsForTest(scaleFactor: Double): WebSurfaceMetrics =
    surfaceMetrics(scaleFactor)

/**
 * The one capture effect this target can ask the browser for, and the only DOM call of the seam.
 *
 * No Kotlin/Wasm interop declares the capture members of `Element`, so the call is written in
 * JavaScript through `@JsFun` — the one place where Kotlin/Wasm and JavaScript meet, as for every other
 * DOM gap of this target — and it is written here rather than in the port so that the seam and its one
 * effect are read together. The call is the DOM's own: `releasePointerCapture` for a pointer that holds
 * no capture is a no-op, and `setPointerCapture` for a pointer the browser does not consider active
 * throws, which the caller contains.
 */
@JsFun(
    """(element, pointerId, captured) => {
         if (captured) { element.setPointerCapture(pointerId); } else { element.releasePointerCapture(pointerId); }
       }""",
)
private external fun wasmApplyPointerCapture(element: JsAny, pointerId: Int, captured: Boolean): Unit

/**
 * The one failure of this seam: the browser could not be made to perform the capture asked for.
 *
 * It is a `PlatformFailure` of this platform because the call really crosses the browser's DOM API
 * (`OPERATION-CONTRACTS.md` §1.5), and it carries one stable code whatever the browser's own error
 * said: what the surface reports as a rejected field is that this mechanism did not perform the effect,
 * and the shape of the browser's error object is not a fact of Kadre's model.
 */
private fun pointerCaptureFailure(): KadreFailure =
    KadreFailure.PlatformFailure(KadrePlatform.Web, "web-host", "pointer-capture-failed")

/**
 * The pointer-lock members of the element and of the document, absent de kotlinx-browser 0.5.0 —
 * the gap this port reads through `@JsFun` instead, the way `wasmApplyPointerCapture` reads the
 * capture members the same bindings do not declare.
 *
 * `requestPointerLock` is read in the promise form Chromium answers with (and rejected promises are
 * the refusals the terminal is taken from); a browser without that form answers `undefined`, which
 * the nullable read absorbs and the `pointerlockerror` listener remains the refusal path.
 * `pointerLockElement` is the browser's own state an unlock is read against, and `exitPointerLock`
 * the one effect an unlock has — a void call with no promise, whose only terminal is the change.
 */
@JsFun("(element) => element.requestPointerLock()")
private external fun wasmRequestPointerLock(element: JsAny): Promise<JsAny?>?

@JsFun("(doc) => doc.exitPointerLock()")
private external fun wasmExitPointerLock(doc: JsAny)

@JsFun("(doc) => doc.pointerLockElement")
private external fun wasmPointerLockElement(doc: JsAny): JsAny?

/**
 * Takes [rejected] as what this promise's refusal means for the primitive that made it, and answers
 * a promise nobody reads.
 *
 * Kotlin/Wasm's own `catch` hands the rejection reason over as a `JsAny` and expects one back, so
 * the handler returns `null` — a rejection the port handled produces no value anyone uses, and the
 * primitive's verdict travels through the emission's terminal instead.
 */
private fun Promise<JsAny?>.onRejection(rejected: () -> Unit): Promise<JsAny?> =
    catch { _ ->
        rejected()
        null
    }

private external interface WasmDocumentVisibility : JsAny {
    val visibilityState: JsString
}
