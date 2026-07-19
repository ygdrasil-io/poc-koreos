@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

/**
 * wasmJs implementation of [WebDomBridge].
 *
 * Provides DOM access based on Wasm JS interop (external interface).
 * DOM listeners are registered via [JsAny] and Wasm [addEventListener],
 * and event fields are extracted via the external interfaces below.
 *
 * This file MAY use Wasm JS interop since it is in wasmJsMain.
 *
 * @since 1.0.0
 */
package org.graphiks.kadre.web

import org.graphiks.kadre.core.Insets

// ---------------------------------------------------------------------------
// External interfaces — wasmJs-side DOM access
// ---------------------------------------------------------------------------

/**
 * External representation of a DOM [EventTarget] (canvas, document, etc.).
 */
@JsName("EventTarget")
external interface JsEventTarget : JsAny {
    fun addEventListener(type: JsString, listener: JsAny)
    fun removeEventListener(type: JsString, listener: JsAny)
}

/**
 * Properties common to all DOM events.
 */
@JsName("Event")
external interface JsDomEvent : JsAny {
    val type: JsString
}

/**
 * Properties of a DOM KeyboardEvent.
 */
@JsName("KeyboardEvent")
external interface JsKeyboardEvent : JsDomEvent {
    val code: JsString
    val key: JsString
    val shiftKey: JsBoolean
    val ctrlKey: JsBoolean
    val altKey: JsBoolean
    val metaKey: JsBoolean
    val repeat: JsBoolean
}

/**
 * Properties of a DOM PointerEvent (includes the MouseEvent fields).
 */
@JsName("PointerEvent")
external interface JsPointerEvent : JsDomEvent {
    val clientX: JsNumber
    val clientY: JsNumber
    val button: JsNumber
    val pointerId: JsNumber
    val pointerType: JsString
    val isPrimary: JsBoolean
}

/**
 * Properties of a DOM WheelEvent.
 */
@JsName("WheelEvent")
external interface JsWheelEvent : JsDomEvent {
    val deltaX: JsNumber
    val deltaY: JsNumber
    val deltaMode: JsNumber
    val ctrlKey: JsBoolean
    val clientX: JsNumber
    val clientY: JsNumber
}

/**
 * Properties of a DOM CompositionEvent.
 */
@JsName("CompositionEvent")
external interface JsCompositionEvent : JsDomEvent {
    val data: JsString?
}

/** One immutable `getBoundingClientRect()` snapshot. */
external interface JsDomRect : JsAny {
    val left: JsNumber
    val top: JsNumber
    val width: JsNumber
    val height: JsNumber
}

// ---------------------------------------------------------------------------
// Wasm JS interop functions — global DOM access
// ---------------------------------------------------------------------------

@JsFun("(id) => document.getElementById(id)")
private external fun getElementById(id: JsString): JsEventTarget?

@JsFun("(target, type, listener) => { target.addEventListener(type, listener); }")
private external fun jsAddEventListener(target: JsEventTarget, type: JsString, listener: JsAny)

@JsFun("(target, type, listener) => { target.removeEventListener(type, listener); }")
private external fun jsRemoveEventListener(target: JsEventTarget, type: JsString, listener: JsAny)

@JsFun("() => document")
private external fun getDocument(): JsEventTarget

@JsFun("() => document.hidden")
private external fun isDocumentHidden(): JsBoolean

@JsFun("""(canvas, callback) => {
    const ro = new ResizeObserver(() => {
        callback();
    });
    ro.observe(canvas);
    return ro;
}""")
private external fun createResizeObserver(canvas: JsEventTarget, callback: JsAny): JsAny

@JsFun("(ro) => { ro.disconnect(); }")
private external fun disconnectResizeObserver(ro: JsAny)

@JsFun("(fn) => fn")
private external fun wrapCallback(fn: () -> Unit): JsAny

@JsFun("() => window")
private external fun getWindow(): JsEventTarget

@JsFun("() => typeof window.PointerEvent !== 'undefined'")
private external fun pointerEventsSupported(): JsBoolean

@JsFun("() => window.devicePixelRatio || 1")
private external fun getDevicePixelRatio(): Double

@JsFun("(canvas) => canvas.getBoundingClientRect()")
private external fun canvasBoundingClientRect(canvas: JsEventTarget): JsDomRect

@JsFun("(canvas) => canvas.clientLeft")
private external fun canvasClientLeft(canvas: JsEventTarget): Double

@JsFun("(canvas) => canvas.clientTop")
private external fun canvasClientTop(canvas: JsEventTarget): Double

@JsFun("(canvas) => canvas.clientWidth")
private external fun canvasClientWidth(canvas: JsEventTarget): Double

@JsFun("(canvas) => canvas.clientHeight")
private external fun canvasClientHeight(canvas: JsEventTarget): Double

// --- DnD (Drag & Drop) helpers ---

@JsFun("(e) => { e.preventDefault(); }")
private external fun domPreventDefault(e: JsAny)

@JsFun("(e) => e.clientX")
private external fun dragClientX(e: JsAny): Double

@JsFun("(e) => e.clientY")
private external fun dragClientY(e: JsAny): Double

@JsFun("(e) => e.dataTransfer.items.length")
private external fun dragItemCount(e: JsAny): Int

@JsFun("(e, i) => e.dataTransfer.items[i].type")
private external fun dragItemType(e: JsAny, i: Int): String

@JsFun("(e) => e.dataTransfer.files.length")
private external fun dragFileCount(e: JsAny): Int

@JsFun("(e, i) => e.dataTransfer.files[i].name")
private external fun dragFileName(e: JsAny, i: Int): String

/** Wraps a Kotlin `(Double) -> Unit` into a JS-callable closure (see [wrapCallback]). */
@JsFun("(fn) => fn")
private external fun wrapDoubleCallback(fn: (Double) -> Unit): JsAny

/** Wraps a Kotlin `() -> Boolean` into a JS-callable closure. */
@JsFun("(fn) => fn")
private external fun wrapBoolSupplier(fn: () -> Boolean): JsAny

// ── R5-IME: hidden input overlay ──────────────────────────────────────────

/**
 * Creates a hidden <input> element for IME composition with all the
 * required styling and attribute configuration.
 */
@JsFun("""() => {
    const input = document.createElement('input');
    input.style.position = 'absolute';
    input.style.opacity = '0';
    input.style.height = '0px';
    input.style.width = '0px';
    input.style.pointerEvents = 'none';
    input.style.left = '0px';
    input.style.top = '0px';
    input.style.zIndex = '-1';
    input.autocapitalize = 'off';
    input.autocomplete = 'off';
    input.autocorrect = 'off';
    input.spellcheck = false;
    return input;
}""")
private external fun createImeInputElement(): JsEventTarget

@JsFun("(parent, child) => { parent.appendChild(child); }")
private external fun jsAppendChild(parent: JsEventTarget, child: JsEventTarget)

@JsFun("(el) => el.parentElement")
private external fun jsGetParentElement(el: JsEventTarget): JsEventTarget?

@JsFun("(el) => { el.focus(); }")
private external fun jsFocusElement(el: JsEventTarget)

@JsFun("(el) => { el.blur(); }")
private external fun jsBlurElement(el: JsEventTarget)

@JsFun("(el, value) => { el.inputMode = value; }")
private external fun jsSetInputMode(el: JsEventTarget, value: String)

@JsFun("(el, l, t, w, h) => { el.style.left = l + 'px'; el.style.top = t + 'px'; el.style.width = w + 'px'; el.style.height = h + 'px'; }")
private external fun jsSetInputPosition(el: JsEventTarget, left: Int, top: Int, width: Int, height: Int)

@JsFun("(el, value) => { el.value = value; }")
private external fun jsSetInputValue(el: JsEventTarget, value: String)

@JsFun("(el) => { el.remove(); }")
private external fun jsRemoveElement(el: JsEventTarget)

// ── R5-CustomCursor ───────────────────────────────────────────────────────

/**
 * Creates a data URL from RGBA pixel data via an off-screen canvas.
 *
 * Creates a `<canvas>`, paints the RGBA pixels via `putImageData`,
 * and returns `canvas.toDataURL("image/png")`.
 *
 * The pixel data is passed as a hex-encoded string because wasmJs `@JsFun`
 * interop does not support array types (`IntArray` / `ByteArray`).
 */
@JsFun("""(hex, width, height, hx, hy) => {
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    const imageData = ctx.createImageData(width, height);
    const data = imageData.data;
    const len = width * height * 4;
    for (let i = 0; i < len; i++) {
        data[i] = parseInt(hex.substring(i * 2, i * 2 + 2), 16);
    }
    ctx.putImageData(imageData, 0, 0);
    return canvas.toDataURL('image/png');
}""")
private external fun createCursorDataUrlJs(hex: String, width: Int, height: Int, hx: Int, hy: Int): String

// --- Gesture field extraction ---

@JsFun("(e) => e.scale")
private external fun gestureScale(e: JsAny): Float

@JsFun("(e) => e.rotation")
private external fun gestureRotation(e: JsAny): Float

// --- Touch field extraction (changedTouches is array-like) ---

@JsFun("(e) => e.changedTouches.length")
private external fun touchCount(e: JsAny): Int

@JsFun("(e, i) => e.changedTouches[i].clientX")
private external fun touchClientX(e: JsAny, i: Int): Double

@JsFun("(e, i) => e.changedTouches[i].clientY")
private external fun touchClientY(e: JsAny, i: Int): Double

@JsFun("(e, i) => e.changedTouches[i].identifier")
private external fun touchIdentifier(e: JsAny, i: Int): Double

@JsFun("(e) => { e.preventDefault(); }")
private external fun touchPreventDefault(e: JsAny)

/**
 * Observes `window.devicePixelRatio` via a re-arming `matchMedia` listener.
 *
 * `cb` receives the new ratio; `isAttached` lets the chain stop after detach.
 */
@JsFun(
    """(cb, isAttached) => {
        function arm() {
            var dpr = window.devicePixelRatio || 1;
            var mq = window.matchMedia('(resolution: ' + dpr + 'dppx)');
            var handler = function() {
                mq.removeEventListener('change', handler);
                if (!isAttached()) return;
                cb(window.devicePixelRatio || 1);
                arm();
            };
            mq.addEventListener('change', handler);
        }
        arm();
    }"""
)
private external fun observeDevicePixelRatioJs(cb: JsAny, isAttached: JsAny)

/**
 * Observes `prefers-color-scheme` via `matchMedia` and calls `cb(dark: Boolean)`
 * when the user toggles between dark and light mode.
 */
@JsFun(
    """(cb) => {
        var mq = window.matchMedia('(prefers-color-scheme: dark)');
        mq.addEventListener('change', function() { cb(mq.matches); });
    }"""
)
private external fun observeColorSchemeJs(cb: JsAny)

/**
 * Wraps a Kotlin `(Boolean) -> Unit` lambda into a JS function callable from
 * `addEventListener`.
 */
@JsFun("(fn) => fn")
private external fun wrapBoolCallback(fn: (Boolean) -> Unit): JsAny

/**
 * Wraps a Kotlin `(JsAny) -> Unit` lambda into a JS function callable from
 * `addEventListener`. The `(fn) => fn` of the `@JsFun` triggers the conversion by the
 * Kotlin/Wasm compiler, which produces a real JS closure — equivalent to the pattern
 * used for the `ResizeObserver` above.
 *
 * Without this wrapper, `handler.toJsReference()` produced an opaque reference not
 * directly callable by the DOM (the listeners were registered but never
 * invoked → keyboard/pointer/wheel inert on the wasmJs side).
 */
@JsFun("(fn) => fn")
private external fun wrapEventHandler(fn: (JsAny) -> Unit): JsAny

/** Calls requestFullscreen on the given element (handles vendor prefixes). */
@JsFun("(el) => { if (el.requestFullscreen) el.requestFullscreen(); else if (el.webkitRequestFullscreen) el.webkitRequestFullscreen(); }")
private external fun jsRequestFullscreen(el: JsEventTarget)

/** Calls document.exitFullscreen (handles vendor prefixes). */
@JsFun("() => { if (document.exitFullscreen) document.exitFullscreen(); else if (document.webkitExitFullscreen) document.webkitExitFullscreen(); }")
private external fun jsExitFullscreen()

// ── R3: cursor, pointer lock, hit-test ─────────────────────────────────────

/** Sets the CSS cursor style on the given element. */
@JsFun("(el, value) => { el.style.cursor = value; }")
private external fun jsSetCssCursor(el: JsEventTarget, value: String)

/** Sets the CSS pointer-events style on the given element. */
@JsFun("(el, value) => { el.style.pointerEvents = value; }")
private external fun jsSetPointerEvents(el: JsEventTarget, value: String)

/** Calls requestPointerLock on the given element (handles vendor prefixes). */
@JsFun("(el) => { if (el.requestPointerLock) el.requestPointerLock(); else if (el.webkitRequestPointerLock) el.webkitRequestPointerLock(); else if (el.mozRequestPointerLock) el.mozRequestPointerLock(); }")
private external fun jsRequestPointerLock(el: JsEventTarget)

/** Calls document.exitPointerLock (handles vendor prefixes). */
@JsFun("() => { if (document.exitPointerLock) document.exitPointerLock(); else if (document.webkitExitPointerLock) document.webkitExitPointerLock(); else if (document.mozExitPointerLock) document.mozExitPointerLock(); }")
private external fun jsExitPointerLock()

/** Returns true if the given element currently holds Pointer Lock. */
@JsFun("(canvas) => document.pointerLockElement === canvas")
private external fun jsIsPointerLocked(canvas: JsEventTarget?): Boolean

// ── Task 14: safeArea insets + ownedDisplayHandle ──────────────────────────

@JsFun("""() => {
    const div = document.createElement('div');
    div.style.paddingTop = 'env(safe-area-inset-top, 0px)';
    div.style.paddingBottom = 'env(safe-area-inset-bottom, 0px)';
    div.style.paddingLeft = 'env(safe-area-inset-left, 0px)';
    div.style.paddingRight = 'env(safe-area-inset-right, 0px)';
    document.body.appendChild(div);
    const cs = getComputedStyle(div);
    const r = (parseInt(cs.paddingTop) || 0) + ',' +
              (parseInt(cs.paddingBottom) || 0) + ',' +
              (parseInt(cs.paddingLeft) || 0) + ',' +
              (parseInt(cs.paddingRight) || 0);
    document.body.removeChild(div);
    return r;
}""")
private external fun measureSafeAreaInsetsJs(): String

@JsFun("() => window.screen.availWidth")
private external fun screenAvailWidth(): Int

@JsFun("() => window.screen.availHeight")
private external fun screenAvailHeight(): Int

/**
 * Creates a canvas (id + dimensions) and appends it to the parent (parentId or body).
 * If a canvas with that id already exists, returns it as is without recreating.
 * Returns `true` if the canvas now exists in the DOM (created or pre-existing).
 */
@JsFun("""(id, width, height, parentId) => {
    let canvas = document.getElementById(id);
    if (canvas) return true;
    canvas = document.createElement('canvas');
    canvas.id = id;
    canvas.width = width;
    canvas.height = height;
    canvas.tabIndex = 0;
    const parent = parentId ? document.getElementById(parentId) : document.body;
    if (!parent) return false;
    parent.appendChild(canvas);
    return true;
}""")
private external fun ensureCanvasInDom(
    id: JsString,
    width: Int,
    height: Int,
    parentId: JsString?,
): JsBoolean

// ---------------------------------------------------------------------------
// Implementation
// ---------------------------------------------------------------------------

internal actual fun metricsConnectionOf(bridge: WebDomBridge): WebMetricsConnection? =
    when (bridge) {
        is WasmJsWebDomBridge -> bridge.metricsConnection
        is WebMetricsConnectionOwner -> bridge.metricsConnection
        else -> null
    }

internal actual fun bindMetricsConnection(
    bridge: WebDomBridge,
    connection: WebMetricsConnection?,
) {
    when (bridge) {
        is WasmJsWebDomBridge -> bridge.metricsConnection = connection
        is WebMetricsConnectionOwner -> bridge.metricsConnection = connection
    }
}

/**
 * wasmJs DOM bridge to the Kadre engine.
 *
 * Uses Wasm JS interop via [JsFun] to access DOM APIs not directly
 * available in the Kotlin/Wasm wasmJs runtime.
 */
class WasmJsWebDomBridge : WebDomBridge {

    override var onWindowEvent: ((WebWindowEvent) -> Unit)? = null

    override var onThemeChange: ((Boolean) -> Unit)? = null

    override var preventDefaultEnabled: Boolean = true

    override fun readDevicePixelRatio(): Double = normalizedDevicePixelRatio(getDevicePixelRatio())

    override fun readCanvasPhysicalSize(canvasId: String): Pair<Int, Int> {
        val canvas = getElementById(canvasId.toJsString()) ?: return Pair(0, 0)
        val size = readCanvasMetrics(canvas).physicalSize()
        return Pair(size.width, size.height)
    }

    override fun ensureCanvas(attrs: WebWindowAttributes): String {
        val id = attrs.effectiveCanvasId
        val existing = getElementById(id.toJsString())
        if (existing != null) return id
        if (!attrs.appendToBody) {
            println("[WasmJsWebDomBridge] Canvas '$id' not found (appendToBody=false → no creation)")
            return id
        }
        val ok = ensureCanvasInDom(
            id.toJsString(),
            attrs.width,
            attrs.height,
            attrs.parentElementId?.toJsString(),
        ).toBoolean()
        if (!ok) {
            println("[WasmJsWebDomBridge] Aucun parent disponible (parentElementId='${attrs.parentElementId}', body absent)")
        } else {
            println("[WasmJsWebDomBridge] Canvas '$id' (${attrs.width}×${attrs.height}) created and appended")
        }
        return id
    }

    private var targetElement: JsEventTarget? = null
    private val listenerRefs = mutableListOf<Triple<JsEventTarget, String, JsAny>>()
    private var resizeObserverRef: JsAny? = null
    private val eventAdapter = WebBridgeEventAdapter(
        metricsProvider = ::readCurrentCanvasMetrics,
        eventSink = { event -> onWindowEvent?.invoke(event) },
        metricsSink = ::dispatchMetrics,
    )

    /** Hidden <input> used for IME composition events. */
    private var imeInput: JsEventTarget? = null

    private var attachmentToken: WebAttachmentToken? = null
    internal var metricsConnection: WebMetricsConnection? = null

    override fun attach(targetElementId: String) {
        val canvas = getElementById(targetElementId.toJsString()) ?: return
        targetElement = canvas
        val token = eventAdapter.attach()
        attachmentToken = token

        // --- Keyboard ---
        addDomListener(canvas, "keydown", token) { e ->
            val ke = e.unsafeCast<JsKeyboardEvent>()
            val mods = domModifiers(
                shiftKey = ke.shiftKey.toBoolean(),
                ctrlKey  = ke.ctrlKey.toBoolean(),
                altKey   = ke.altKey.toBoolean(),
                metaKey  = ke.metaKey.toBoolean(),
            )
            val keyStr = ke.key.toString()
            dispatch(token,
                WebWindowEvent.KeyInput(
                    domKeyEvent(
                        code = ke.code.toString(),
                        key = ke.key.toString(),
                        eventType = "keydown",
                        shiftKey = ke.shiftKey.toBoolean(),
                        ctrlKey = ke.ctrlKey.toBoolean(),
                        altKey = ke.altKey.toBoolean(),
                        metaKey = ke.metaKey.toBoolean(),
                        repeat = ke.repeat.toBoolean(),
                    ),
                ),
            )
            // R4: emit ModifiersChanged on modifier key press
            if (keyStr in setOf("Shift", "Control", "Alt", "Meta")) {
                dispatch(token, WebWindowEvent.ModifiersChanged(mods))
            }
        }

        addDomListener(canvas, "keyup", token) { e ->
            val ke = e.unsafeCast<JsKeyboardEvent>()
            val mods = domModifiers(
                shiftKey = ke.shiftKey.toBoolean(),
                ctrlKey  = ke.ctrlKey.toBoolean(),
                altKey   = ke.altKey.toBoolean(),
                metaKey  = ke.metaKey.toBoolean(),
            )
            val keyStr = ke.key.toString()
            dispatch(token,
                WebWindowEvent.KeyInput(
                    domKeyEvent(
                        code = ke.code.toString(),
                        key = ke.key.toString(),
                        eventType = "keyup",
                        shiftKey = ke.shiftKey.toBoolean(),
                        ctrlKey = ke.ctrlKey.toBoolean(),
                        altKey = ke.altKey.toBoolean(),
                        metaKey = ke.metaKey.toBoolean(),
                        repeat = false,
                    ),
                ),
            )
            // R4: emit ModifiersChanged on modifier key release
            if (keyStr in setOf("Shift", "Control", "Alt", "Meta")) {
                dispatch(token, WebWindowEvent.ModifiersChanged(mods))
            }
        }

        // Pointer Events are authoritative when available. Legacy Touch Events
        // are registered only as a feature-detected fallback, never alongside them.
        val inputRegistration = selectWebInputRegistration(pointerEventsSupported().toBoolean())
        when (inputRegistration.family) {
            WebInputFamily.PointerEvents -> inputRegistration.eventTypes.forEach { type ->
                addDomListener(canvas, type, token) { event -> dispatchPointerEvent(token, event) }
            }
            WebInputFamily.LegacyTouchEvents -> inputRegistration.eventTypes.forEach { type ->
                addDomListener(canvas, type, token) { event -> dispatchTouches(token, event) }
            }
        }

        // --- Wheel ---
        addDomListener(canvas, "wheel", token) { e ->
            val we = e.unsafeCast<JsWheelEvent>()
            eventAdapter.wheel(
                token = token,
                deltaX = we.deltaX.toDouble(),
                deltaY = we.deltaY.toDouble(),
                deltaMode = we.deltaMode.toDouble().toInt(),
                ctrlKey = we.ctrlKey.toBoolean(),
                clientX = we.clientX.toDouble(),
                clientY = we.clientY.toDouble(),
            )
        }

        // --- DnD ---
        addDomListener(canvas, "dragenter", token) { e ->
            domPreventDefault(e)
            val x = dragClientX(e)
            val y = dragClientY(e)
            val count = dragItemCount(e)
            val files = (0 until count).map { dragItemType(e, it) }
            eventAdapter.dragEntered(token, x, y, files)
        }

        addDomListener(canvas, "dragover", token) { e ->
            domPreventDefault(e)
            eventAdapter.dragMoved(token, dragClientX(e), dragClientY(e))
        }

        addDomListener(canvas, "drop", token) { e ->
            domPreventDefault(e)
            val x = dragClientX(e)
            val y = dragClientY(e)
            val count = dragFileCount(e)
            val files = (0 until count).map { dragFileName(e, it) }
            eventAdapter.dragDropped(token, x, y, files)
        }

        addDomListener(canvas, "dragleave", token) { _ ->
            dispatch(token, WebWindowEvent.DragLeft)
        }

        // --- Gesture (Safari trackpad: gesturestart/change/end) ---
        addDomListener(canvas, "gesturestart", token) { e ->
            domPreventDefault(e)
            dispatch(token,
                WebWindowEvent.WebGestureStart(
                    scale = gestureScale(e),
                    rotation = gestureRotation(e),
                )
            )
        }
        addDomListener(canvas, "gesturechange", token) { e ->
            domPreventDefault(e)
            dispatch(token,
                WebWindowEvent.WebGestureChange(
                    scale = gestureScale(e),
                    rotation = gestureRotation(e),
                )
            )
        }
        addDomListener(canvas, "gestureend", token) { e ->
            domPreventDefault(e)
            dispatch(token,
                WebWindowEvent.WebGestureEnd(
                    scale = gestureScale(e),
                    rotation = gestureRotation(e),
                )
            )
        }

        // --- ResizeObserver ---
        resizeObserverRef = createResizeObserver(
            canvas,
            wrapCallback {
                eventAdapter.resized(token)
            },
        )

        // --- Visibility → Focused + Occluded ---
        val doc = getDocument()
        addDomListener(doc, "visibilitychange", token) { _ ->
            val hidden = isDocumentHidden().toBoolean()
            dispatch(token, WebWindowEvent.Focused(gained = !hidden))
            dispatch(token, WebWindowEvent.WebOccluded(hidden))
        }

        // --- Unload: beforeunload → CloseRequested, pagehide → Destroyed ---
        val win = getWindow()
        addDomListener(win, "beforeunload", token) { _ ->
            dispatch(token, WebWindowEvent.CloseRequested)
        }
        addDomListener(win, "pagehide", token) { _ ->
            dispatch(token, WebWindowEvent.Destroyed)
        }

        // --- devicePixelRatio changes → ScaleFactorChanged ---
        observeDevicePixelRatioJs(
            cb = wrapDoubleCallback { _ ->
                eventAdapter.devicePixelRatioChanged(token)
            },
            isAttached = wrapBoolSupplier { eventAdapter.isCurrent(token) },
        )

        // --- prefers-color-scheme changes → ThemeChanged ---
        observeColorSchemeJs(
            cb = wrapBoolCallback { dark ->
                eventAdapter.runIfCurrent(token) { onThemeChange?.invoke(dark) }
            },
        )

        metricsConnection
            ?.takeIf { it.bridge === this }
            ?.let(WebMetricsTransactions::reactivate)
    }

    /**
     * Dispatches a [WebWindowEvent.Touch] for each contact in `event.changedTouches`.
     *
     * `preventDefault()` stops the browser from also synthesizing mouse events
     * and page scrolling for the contacts.
     */
    private fun dispatchTouches(token: WebAttachmentToken, e: JsAny) {
        val phase = domTouchTypeToPhase(e.unsafeCast<JsDomEvent>().type.toString())
        val count = touchCount(e)
        val contacts = (0 until count).map { i ->
            WebTouchContact(
                id = touchIdentifier(e, i).toLong(),
                clientX = touchClientX(e, i),
                clientY = touchClientY(e, i),
            )
        }
        eventAdapter.touches(token, phase, contacts)
        if (preventDefaultEnabled) touchPreventDefault(e)
    }

    private fun dispatchPointerEvent(token: WebAttachmentToken, e: JsAny) {
        val pe = e.unsafeCast<JsPointerEvent>()
        eventAdapter.pointer(
            token = token,
            eventType = pe.type.toString(),
            clientX = pe.clientX.toDouble(),
            clientY = pe.clientY.toDouble(),
            pointerId = pe.pointerId.toDouble().toLong(),
            pointerType = pe.pointerType.toString(),
            domPrimary = pe.isPrimary.toBoolean(),
            button = pe.button.toDouble().toInt(),
        )
    }

    // ── Task 14: safeArea insets + ownedDisplayHandle ─────────────────────────

    override fun getSafeAreaInsets(): Insets<Int> {
        val parts = measureSafeAreaInsetsJs().split(',')
        return Insets(
            top = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            bottom = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            left = parts.getOrNull(2)?.toIntOrNull() ?: 0,
            right = parts.getOrNull(3)?.toIntOrNull() ?: 0,
        )
    }

    override fun getDisplayHandle(): Long {
        return (screenAvailWidth().toLong() shl 32) or screenAvailHeight().toLong()
    }

    override fun getCanvasElement(): Any? = targetElement

    // ── R5-IME: hidden input overlay ─────────────────────────────────────────

    override fun setImeAllowed(allowed: Boolean) {
        if (allowed) {
            val token = attachmentToken ?: return
            val input = imeInput ?: createImeInputBox(token).also { imeInput = it }
            jsFocusElement(input)
        } else {
            imeInput?.let { jsBlurElement(it) }
        }
    }

    override fun setImePurpose(purpose: String) {
        imeInput?.let { jsSetInputMode(it, purpose) }
    }

    override fun setImeCursorArea(x: Int, y: Int, width: Int, height: Int) {
        imeInput?.let { jsSetInputPosition(it, x, y, width, height) }
    }

    /**
     * Creates the hidden <input> element and wires IME composition event
     * listeners. Appends it to the canvas parent (or document as fallback).
     */
    private fun createImeInputBox(token: WebAttachmentToken): JsEventTarget {
        val input = createImeInputElement()
        val parent = targetElement?.let { jsGetParentElement(it) } ?: getDocument()
        jsAppendChild(parent, input)

        addDomListener(input, "compositionstart", token) { _ ->
            dispatch(token, WebWindowEvent.Ime(WebImeEvent.Enabled))
        }

        addDomListener(input, "compositionupdate", token) { e ->
            val ce = e.unsafeCast<JsCompositionEvent>()
            val text = ce.data?.toString() ?: ""
            dispatch(token, WebWindowEvent.Ime(WebImeEvent.Preedit(text = text, cursorRange = null)))
        }

        addDomListener(input, "compositionend", token) { e ->
            val ce = e.unsafeCast<JsCompositionEvent>()
            val text = ce.data?.toString() ?: ""
            dispatch(token, WebWindowEvent.Ime(WebImeEvent.Commit(text = text)))
            dispatch(token, WebWindowEvent.Ime(WebImeEvent.Disabled))
            jsSetInputValue(input, "")
        }

        return input
    }

    override fun detach() {
        val ownerConnection = metricsConnection?.takeIf { it.bridge === this }
        if (ownerConnection == null) {
            WebMetricsTransactions.suspendActive(this)
        } else {
            WebMetricsTransactions.suspend(ownerConnection)
        }
        eventAdapter.detach()
        attachmentToken = null

        for ((target, type, ref) in listenerRefs) {
            jsRemoveEventListener(target, type.toJsString(), ref)
        }
        listenerRefs.clear()

        resizeObserverRef?.let { disconnectResizeObserver(it) }
        resizeObserverRef = null

        imeInput?.let { jsRemoveElement(it) }
        imeInput = null

        targetElement = null
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private fun readCurrentCanvasMetrics(): CanvasMetrics =
        readCanvasMetrics(targetElement ?: error("Web bridge is not attached to a canvas"))

    private fun readCanvasMetrics(canvas: JsEventTarget): CanvasMetrics {
        val rect = canvasBoundingClientRect(canvas)
        return CanvasMetrics(
            leftCss = rect.left.toDouble() + canvasClientLeft(canvas),
            topCss = rect.top.toDouble() + canvasClientTop(canvas),
            widthCss = canvasClientWidth(canvas),
            heightCss = canvasClientHeight(canvas),
            devicePixelRatio = getDevicePixelRatio(),
        )
    }

    private fun addDomListener(
        target: JsEventTarget,
        type: String,
        token: WebAttachmentToken,
        handler: (JsAny) -> Unit,
    ) {
        // `wrapEventHandler` triggers the JS wrapping that makes the Kotlin lambda
        // callable from `addEventListener` (see the wrapper doc above).
        val ref = wrapEventHandler { event ->
            eventAdapter.runIfCurrent(token) { handler(event) }
        }
        jsAddEventListener(target, type.toJsString(), ref)
        listenerRefs.add(Triple(target, type, ref))
    }

    private fun dispatch(token: WebAttachmentToken, event: WebWindowEvent) =
        eventAdapter.emit(token, event)

    private fun dispatchMetrics(token: WebAttachmentToken, transaction: WebMetricsTransaction) {
        if (WebMetricsTransactions.dispatch(this, transaction)) return
        dispatch(token, WebWindowEvent.ScaleFactorChanged(transaction.scaleFactor))
        dispatch(token,
            WebWindowEvent.Resized(
                transaction.physicalSize.width,
                transaction.physicalSize.height,
            ),
        )
    }

    // ── R2: Fullscreen API ────────────────────────────────────────────────────

    override fun requestFullscreen(canvasId: String) {
        try {
            val el = getElementById(canvasId.toJsString()) ?: targetElement ?: return
            jsRequestFullscreen(el)
        } catch (_: Throwable) {}
    }

    override fun exitFullscreen() {
        try {
            jsExitFullscreen()
        } catch (_: Throwable) {}
    }

    // ── R3: Cursor and Pointer Lock ──────────────────────────────────────────

    /**
     * Sets `style.cursor` on the canvas element identified by [canvasId].
     */
    override fun setCssCursor(canvasId: String, cssCursorValue: String) {
        try {
            val el = getElementById(canvasId.toJsString()) ?: targetElement ?: return
            jsSetCssCursor(el, cssCursorValue)
        } catch (_: Throwable) {}
    }

    override fun setPointerEvents(canvasId: String, pointerEventsValue: String) {
        try {
            val el = getElementById(canvasId.toJsString()) ?: targetElement ?: return
            jsSetPointerEvents(el, pointerEventsValue)
        } catch (_: Throwable) {}
    }

    /**
     * Requests Pointer Lock on the canvas element (handles vendor prefixes).
     *
     * The browser may require a user gesture; the lock is granted asynchronously
     * via a `pointerlockchange` event.
     */
    override fun requestPointerLock(canvasId: String) {
        try {
            val el = getElementById(canvasId.toJsString()) ?: targetElement ?: return
            jsRequestPointerLock(el)
        } catch (_: Throwable) {}
    }

    /**
     * Calls `document.exitPointerLock()` (handles vendor prefixes).
     */
    override fun exitPointerLock() {
        try {
            jsExitPointerLock()
        } catch (_: Throwable) {}
    }

    /**
     * Returns true if the canvas element currently holds Pointer Lock.
     */
    override fun isPointerLocked(): Boolean = jsIsPointerLocked(targetElement)

    /**
     * Toggles `style.pointerEvents` on the canvas to enable/disable hit-testing.
     */
    @Deprecated("Use setPointerEvents")
    override fun setCursorHittest(canvasId: String, hittest: Boolean) {
        setPointerEvents(canvasId, if (hittest) "auto" else "none")
    }

    // ── R5-CustomCursor ─────────────────────────────────────────────────────────

    /**
     * Creates a data URL from RGBA pixel data via an off-screen canvas.
     *
     * Encodes bytes as hex and delegates to [createCursorDataUrlJs] which
     * creates a `<canvas>`, paints the pixels via `putImageData`,
     * and returns `canvas.toDataURL("image/png")`.
     */
    override fun createCursorDataUrl(rgba: ByteArray, width: Int, height: Int, hotspotX: Int, hotspotY: Int): String {
        try {
            val hex = buildString(rgba.size * 2) {
                for (b in rgba) {
                    val v = b.toInt() and 0xFF
                    append("0123456789abcdef"[v shr 4])
                    append("0123456789abcdef"[v and 0xF])
                }
            }
            return createCursorDataUrlJs(hex, width, height, hotspotX, hotspotY)
        } catch (_: Throwable) { return "" }
    }
}
