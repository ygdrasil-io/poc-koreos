package org.graphiks.kadre.platform.web

import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.surface.LogicalPoint
import org.w3c.dom.AddEventListenerOptions
import org.w3c.dom.Document
import org.w3c.dom.HTMLElement
import org.w3c.dom.MutationObserver
import org.w3c.dom.MutationObserverInit
import org.w3c.dom.ShadowRoot
import org.w3c.dom.Window
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.WheelEvent
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.unsafeCast
import kotlin.math.max

internal class JsWebDomPort(element: HTMLElement) : WebHostPort {
    private var element: HTMLElement? = element
    private val originDocument: Document = checkNotNull(element.ownerDocument)
    private val originWindow: Window? = originDocument.defaultView
    private var lifecycleObserver: ((WebLifecycleSnapshot) -> Unit)? = null
    private var metricsObserver: ((WebSurfaceMetrics) -> Unit)? = null
    private var inputObserver: ((WebInputStimulus) -> Unit)? = null
    private var documentObserver: MutationObserver? = null
    private var shadowRootObserver: MutationObserver? = null
    private var observedShadowRoot: ShadowRoot? = null
    private var resizeObserver: ResizeObserver? = null
    private var reconnectAnimationFrame: Int? = null
    private var active: Boolean = false
    private var browsingContextFocused: Boolean = originDocument.hasFocus()
    private var subtreeFocused: Boolean = element.matches(":focus-within")

    /**
     * The position of the last pointer observation, which is what makes the next motion's delta a
     * measured fact: the DOM reports no delta for a `pointermove`. A leave clears it, so the first
     * motion of a re-entry measures from its entry point rather than from wherever the pointer was
     * when it left.
     */
    private var lastPointerPosition: LogicalPoint? = null

    /** The scroll-coalescing frontier of this element, which owns one animation-frame registration. */
    private val scrollFrontier: JsScrollCoalescingFrontier = JsScrollCoalescingFrontier(originWindow)

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

    /** One `keydown`: the browser's own physical key, logical key, location and modifiers. */
    private val keyDownListener: (Event) -> Unit = { event ->
        safely { (event as? KeyboardEvent)?.let { deliverInput(jsKeyStimulus(it, pressed = true)) } }
    }

    /** One `keyup`. The port reads no focus here: the lifecycle reduction already owns that. */
    private val keyUpListener: (Event) -> Unit = { event ->
        safely { (event as? KeyboardEvent)?.let { deliverInput(jsKeyStimulus(it, pressed = false)) } }
    }

    private val pointerEnterListener: (Event) -> Unit = { event ->
        safely { deliverPointerEntered(event as? PointerEvent) }
    }

    private val pointerMoveListener: (Event) -> Unit = { event ->
        safely { deliverPointerMoved(event as? PointerEvent) }
    }

    private val pointerDownListener: (Event) -> Unit = { event ->
        safely { deliverPointerButton(event as? PointerEvent, PointerButtonState.Pressed) }
    }

    private val pointerUpListener: (Event) -> Unit = { event ->
        safely { deliverPointerButton(event as? PointerEvent, PointerButtonState.Released) }
    }

    /**
     * One `pointerleave`, over the element and its whole subtree.
     *
     * It carries the kind the browser reported, so a pen leaving is not delivered as a mouse leaving,
     * and it clears the motion baseline the way the next entry expects.
     */
    private val pointerLeaveListener: (Event) -> Unit = { event ->
        safely { deliverPointerLeft(event as? PointerEvent) }
    }

    /**
     * One `pointercancel`: the browser revoked the contact, so the pointer is reconciled by dropping
     * it with everything it held, which is what the reducer's pointer exit does.
     */
    private val pointerCancelListener: (Event) -> Unit = { event ->
        safely { deliverPointerLeft(event as? PointerEvent) }
    }

    /**
     * One `wheel`, read at the frontier the element's own scroll history is on.
     *
     * The listener is registered as non-passive because the surface decides later whether the
     * browser's default is suppressed, and a passive listener could never suppress it. The port
     * itself never decides: it calls no `preventDefault`, and nothing here holds that policy.
     */
    private val wheelListener: (Event) -> Unit = { event ->
        safely {
            (event as? WheelEvent)?.let { wheel ->
                jsScrollStimulus(wheel, scrollFrontier.frontierFor(wheel))?.let(::deliverInput)
            }
        }
    }

    override val stableIdentity: Any get() = checkNotNull(element)
    override val leasedElement: Any? get() = element
    override val initialSnapshot: WebSurfaceMetrics =
        element.surfaceMetrics(element.ownerDocument?.defaultView?.deviceScaleFactor() ?: 1.0)
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
        val installed = ResizeObserver { _, _ -> runCatching { deliverMetrics() } }
        resizeObserver = installed
        installed.observe(current)
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
     */
    override fun installInputObserver(observer: (WebInputStimulus) -> Unit) {
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
        element?.addEventListener("wheel", wheelListener, wheelListenerOptions)
    }

    /** Frames belong to the element's browsing context, which may not carry this module's global. */
    override fun scheduleFrame(callback: () -> Unit): WebFrameHandle {
        val browserWindow = originWindow ?: return WebFrameHandle { }
        return jsScheduleFrame(browserWindow, callback)
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
        runCatching { element?.removeEventListener("wheel", wheelListener, wheelListenerOptions) }
        runCatching { documentObserver?.disconnect() }
        runCatching { shadowRootObserver?.disconnect() }
        runCatching { resizeObserver?.disconnect() }
        // The frontier owns a frame registration of its own, so it is cancelled with the other
        // per-element resources rather than left to fire for an element the port no longer holds.
        runCatching { scrollFrontier.close() }
        reconnectAnimationFrame?.let { animationFrame ->
            runCatching { originWindow?.cancelAnimationFrame(animationFrame) }
        }
        reconnectAnimationFrame = null
        documentObserver = null
        observedShadowRoot = null
        shadowRootObserver = null
        resizeObserver = null
        lifecycleObserver = null
        metricsObserver = null
        inputObserver = null
        lastPointerPosition = null
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
        next.observe(originDocument, MutationObserverInit(childList = true, subtree = true))
    }

    private fun updateShadowRootObserver(current: HTMLElement) {
        val shadowRoot = current.getRootNode() as? ShadowRoot
        if (observedShadowRoot === shadowRoot) return
        disconnectShadowRootObserver()
        if (shadowRoot == null) return
        val next = MutationObserver { _, _ -> safely { handleMutationBatch() } }
        shadowRootObserver = next
        observedShadowRoot = shadowRoot
        next.observe(shadowRoot, MutationObserverInit(childList = true, subtree = true))
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
        observer(current.surfaceMetrics(current.ownerDocument?.defaultView?.deviceScaleFactor() ?: 1.0))
    }

    /**
     * One pointer entry: the first observation of a pointer over the element's subtree.
     *
     * The entry also opens the motion baseline, so the first motion of the pointer measures from
     * where it entered rather than from nothing.
     */
    private fun deliverPointerEntered(pointer: PointerEvent?) {
        if (pointer == null) return
        val current = element ?: return
        val kind = jsPointerKind(pointer) ?: return
        val position = jsPointerPosition(current, pointer)
        lastPointerPosition = position
        deliverInput(WebInputStimulus.PointerEntered(position = position, kind = kind))
    }

    /**
     * One pointer motion, with the motion it made since the last pointer observation of the element.
     *
     * The DOM reports no delta for a `pointermove`: the delta is the measured difference from the
     * position of the previous observation, whichever pointer event carried it. The runtime coalesces
     * motions by summing deltas, which is why the delta of one motion is the only one it can carry.
     */
    private fun deliverPointerMoved(pointer: PointerEvent?) {
        if (pointer == null) return
        val current = element ?: return
        val kind = jsPointerKind(pointer) ?: return
        val position = jsPointerPosition(current, pointer)
        val delta = jsPointerDelta(lastPointerPosition, position)
        lastPointerPosition = position
        deliverInput(
            WebInputStimulus.PointerMoved(
                position = position,
                delta = delta,
                pressure = jsPointerPressure(pointer),
                kind = kind,
                pen = jsPointerPenState(kind, pointer),
            ),
        )
    }

    /**
     * One pointer-button transition, copied with its position, its pressure and its own kind.
     *
     * The transition also moves the motion baseline, so a motion that follows it measures from the
     * position the browser reported with the transition rather than skipping that movement.
     */
    private fun deliverPointerButton(pointer: PointerEvent?, buttonState: PointerButtonState) {
        if (pointer == null) return
        val current = element ?: return
        val kind = jsPointerKind(pointer) ?: return
        val position = jsPointerPosition(current, pointer)
        lastPointerPosition = position
        deliverInput(
            WebInputStimulus.PointerButtonChanged(
                button = webPointerButton(pointer.button.toInt()),
                buttonState = buttonState,
                position = position,
                pressure = jsPointerPressure(pointer),
                kind = kind,
                pen = jsPointerPenState(kind, pointer),
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
     * unsupported.
     */
    private fun deliverPointerLeft(pointer: PointerEvent?) {
        if (pointer == null) return
        lastPointerPosition = null
        deliverInput(WebInputStimulus.PointerLeft(kind = jsPointerKind(pointer) ?: return))
    }

    /** Hands one immutable observation to the observer, if one is still installed. */
    private fun deliverInput(stimulus: WebInputStimulus) {
        inputObserver?.invoke(stimulus)
    }

    private fun lifecycleSnapshot(
        current: HTMLElement,
        pageHidden: Boolean = false,
    ): WebLifecycleSnapshot = WebLifecycleSnapshot(
        connected = current.isConnected,
        inOriginDocument = current.ownerDocument === originDocument,
        documentVisible = originDocument.unsafeCast<JsDocumentVisibility>().visibilityState == "visible",
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

private external interface JsDocumentVisibility {
    val visibilityState: String
}
