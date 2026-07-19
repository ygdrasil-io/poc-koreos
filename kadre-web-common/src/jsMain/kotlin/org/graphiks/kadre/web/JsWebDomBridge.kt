/**
 * JS implementation of [WebDomBridge].
 *
 * Attaches all the DOM listeners required by the target canvas and removes them
 * on detach. Target callbacks pass their attachment token to
 * [WebBridgeEventAdapter], which owns lifecycle validation and positional mapping.
 *
 * This file MAY use kotlinx.browser and org.w3c.dom.* since it is in jsMain.
 *
 * @since 1.0.0
 */
package org.graphiks.kadre.web

import kotlinx.browser.document
import kotlinx.browser.window
import org.graphiks.kadre.core.Insets
import org.w3c.dom.Element
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventListener
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.WheelEvent

/**
 * JS DOM bridge to the Kadre engine.
 *
 * Listens to and dispatches the following DOM events:
 * - Keyboard: `keydown` / `keyup` → [WebWindowEvent.KeyInput]
 * - Pointer: `pointermove` → [WebWindowEvent.PointerMoved]
 * - Pointer: `pointerdown` / `pointerup` → [WebWindowEvent.PointerButton]
 * - Pointer: `pointerenter` / `pointerleave` → [WebWindowEvent.PointerEntered] / [WebWindowEvent.PointerLeft]
 * - Wheel: `wheel` → [WebWindowEvent.MouseWheel]
 * - Touch: `touchstart` / `touchmove` / `touchend` / `touchcancel` → [WebWindowEvent.Touch]
 * - Resize: `ResizeObserver` on the canvas → [WebWindowEvent.Resized]
 * - Visibility: `visibilitychange` → [WebWindowEvent.Focused]
 * - Scale: re-arming `matchMedia` on `devicePixelRatio` → [WebWindowEvent.ScaleFactorChanged]
 * - Unload: `beforeunload` → [WebWindowEvent.CloseRequested], `pagehide` → [WebWindowEvent.Destroyed]
 */
class JsWebDomBridge : WebDomBridge {

    override var onWindowEvent: ((WebWindowEvent) -> Unit)? = null

    override var onThemeChange: ((Boolean) -> Unit)? = null

    override var preventDefaultEnabled: Boolean = true

    override fun readDevicePixelRatio(): Double = normalizedDevicePixelRatio(window.devicePixelRatio)

    override fun readCanvasPhysicalSize(canvasId: String): Pair<Int, Int> {
        val canvas = document.getElementById(canvasId) ?: return Pair(0, 0)
        val size = readCanvasMetrics(canvas).physicalSize()
        return Pair(size.width, size.height)
    }

    override fun ensureCanvas(attrs: WebWindowAttributes): String {
        val id = attrs.effectiveCanvasId
        val existing = document.getElementById(id)
        if (existing != null) return id
        if (!attrs.appendToBody) {
            println("[JsWebDomBridge] Canvas '$id' not found (appendToBody=false → no creation)")
            return id
        }
        val canvas = document.createElement("canvas").asDynamic()
        canvas.id = id
        canvas.width = attrs.width
        canvas.height = attrs.height
        // tabIndex to make the canvas focusable (without it, keydown/keyup do not fire).
        canvas.tabIndex = 0
        val parent = attrs.parentElementId?.let { document.getElementById(it) }
            ?: document.body
        if (parent == null) {
            println("[JsWebDomBridge] Aucun parent disponible (parentElementId='${attrs.parentElementId}', body absent)")
            return id
        }
        parent.appendChild(canvas)
        println("[JsWebDomBridge] Canvas '$id' (${attrs.width}×${attrs.height}) created and appended")
        return id
    }

    private var targetElement: Element? = null
    private var canvasElement: Element? = null
    private val canvasListeners = mutableListOf<Pair<String, (Event) -> Unit>>()
    private val documentListeners = mutableListOf<Pair<String, (Event) -> Unit>>()
    private val windowListeners = mutableListOf<Pair<String, (Event) -> Unit>>()
    private var resizeObserver: dynamic = null
    private val eventAdapter = WebBridgeEventAdapter(
        metricsProvider = ::readCurrentCanvasMetrics,
        eventSink = { event -> onWindowEvent?.invoke(event) },
        metricsSink = ::dispatchMetrics,
    )

    /** Hidden <input> used for IME composition events. */
    private var imeInput: HTMLInputElement? = null

    private var attachmentToken: WebAttachmentToken? = null

    override fun attach(targetElementId: String) {
        val canvas = document.getElementById(targetElementId) ?: return
        targetElement = canvas
        canvasElement = canvas
        val token = eventAdapter.attach()
        attachmentToken = token
        val generation = token.generation

        // --- Keyboard ---
        addListener(canvas, "keydown", token) { e ->
            val ke = e as KeyboardEvent
            val mods = domModifiers(ke.shiftKey, ke.ctrlKey, ke.altKey, ke.metaKey)
            dispatch(token,
                WebWindowEvent.KeyInput(
                    domKeyEvent(
                        code = ke.code,
                        key = ke.key,
                        eventType = "keydown",
                        shiftKey = ke.shiftKey,
                        ctrlKey = ke.ctrlKey,
                        altKey = ke.altKey,
                        metaKey = ke.metaKey,
                        repeat = ke.repeat,
                    ),
                ),
            )
            // R4: emit ModifiersChanged when a modifier key is pressed
            if (ke.key in setOf("Shift", "Control", "Alt", "Meta")) {
                dispatch(token, WebWindowEvent.ModifiersChanged(mods))
            }
        }

        addListener(canvas, "keyup", token) { e ->
            val ke = e as KeyboardEvent
            val mods = domModifiers(ke.shiftKey, ke.ctrlKey, ke.altKey, ke.metaKey)
            dispatch(token,
                WebWindowEvent.KeyInput(
                    domKeyEvent(
                        code = ke.code,
                        key = ke.key,
                        eventType = "keyup",
                        shiftKey = ke.shiftKey,
                        ctrlKey = ke.ctrlKey,
                        altKey = ke.altKey,
                        metaKey = ke.metaKey,
                        repeat = false,
                    ),
                ),
            )
            // R4: emit ModifiersChanged when a modifier key is released
            if (ke.key in setOf("Shift", "Control", "Alt", "Meta")) {
                dispatch(token, WebWindowEvent.ModifiersChanged(mods))
            }
        }

        // Pointer Events are authoritative when available. Legacy Touch Events
        // are registered only as a feature-detected fallback, never alongside them.
        val inputRegistration = selectWebInputRegistration(pointerEventsSupported())
        when (inputRegistration.family) {
            WebInputFamily.PointerEvents -> inputRegistration.eventTypes.forEach { type ->
                addListener(canvas, type, token) { event -> dispatchPointerEvent(token, event) }
            }
            WebInputFamily.LegacyTouchEvents -> inputRegistration.eventTypes.forEach { type ->
                addListener(canvas, type, token) { event -> dispatchTouches(token, event) }
            }
        }

        // --- Wheel ---
        addListener(canvas, "wheel", token) { e ->
            val we = e as WheelEvent
            eventAdapter.wheel(
                token = token,
                deltaX = we.deltaX,
                deltaY = we.deltaY,
                deltaMode = we.deltaMode,
                ctrlKey = we.ctrlKey,
                clientX = we.clientX.toDouble(),
                clientY = we.clientY.toDouble(),
            )
        }

        // --- DnD ---
        addListener(canvas, "dragenter", token) { e ->
            e.preventDefault()
            val pe = e.unsafeCast<PointerEventData>()
            val dt = e.asDynamic().dataTransfer
            val files = if (dt != null) {
                val items = dt.items
                if (items != null && items.length > 0) {
                    (0 until items.length).mapNotNull { i ->
                        items[i].asDynamic().type as? String
                    }
                } else emptyList()
            } else emptyList()
            eventAdapter.dragEntered(token, pe.clientX, pe.clientY, files)
        }

        addListener(canvas, "dragover", token) { e ->
            e.preventDefault()
            val pe = e.unsafeCast<PointerEventData>()
            eventAdapter.dragMoved(token, pe.clientX, pe.clientY)
        }

        addListener(canvas, "drop", token) { e ->
            e.preventDefault()
            val pe = e.unsafeCast<PointerEventData>()
            val dt = e.asDynamic().dataTransfer
            val files = if (dt != null && dt.files != null) {
                (0 until dt.files.length).mapNotNull { i ->
                    dt.files[i].asDynamic().name as? String
                }
            } else emptyList()
            eventAdapter.dragDropped(token, pe.clientX, pe.clientY, files)
        }

        addListener(canvas, "dragleave", token) { _ ->
            dispatch(token, WebWindowEvent.DragLeft)
        }

        // --- Gesture (Safari trackpad: gesturestart/change/end) ---
        addListener(canvas, "gesturestart", token) { e ->
            e.preventDefault()
            dispatch(token,
                WebWindowEvent.WebGestureStart(
                    scale = e.asDynamic().scale as? Float ?: 1.0f,
                    rotation = e.asDynamic().rotation as? Float ?: 0.0f,
                )
            )
        }
        addListener(canvas, "gesturechange", token) { e ->
            e.preventDefault()
            dispatch(token,
                WebWindowEvent.WebGestureChange(
                    scale = e.asDynamic().scale as? Float ?: 1.0f,
                    rotation = e.asDynamic().rotation as? Float ?: 0.0f,
                )
            )
        }
        addListener(canvas, "gestureend", token) { e ->
            e.preventDefault()
            dispatch(token,
                WebWindowEvent.WebGestureEnd(
                    scale = e.asDynamic().scale as? Float ?: 1.0f,
                    rotation = e.asDynamic().rotation as? Float ?: 0.0f,
                )
            )
        }

        // --- Resize via ResizeObserver ---
        resizeObserver = js("new ResizeObserver(function(entries) { return entries; })")
        val self = this
        resizeObserver = js("""(function(callback) {
            return new ResizeObserver(function() {
                callback();
            });
        })(function() { self.dispatchResizedForAttachment(generation); })""")
        resizeObserver.observe(canvas)

        // --- Page visibility → Focused + Occluded ---
        addDocumentListener("visibilitychange", token) { _ ->
            val hidden: Boolean = js("document.hidden")
            dispatch(token, WebWindowEvent.Focused(gained = !hidden))
            dispatch(token, WebWindowEvent.WebOccluded(hidden))
        }

        // --- Unload: beforeunload → CloseRequested, pagehide → Destroyed ---
        addWindowListener("beforeunload", token) { _ ->
            dispatch(token, WebWindowEvent.CloseRequested)
        }
        addWindowListener("pagehide", token) { _ ->
            dispatch(token, WebWindowEvent.Destroyed)
        }

        // --- devicePixelRatio changes → ScaleFactorChanged ---
        observeDevicePixelRatio(generation)

        // --- prefers-color-scheme changes → ThemeChanged ---
        observeColorScheme(generation)
    }

    /**
     * Dispatches a [WebWindowEvent.Touch] for each contact in `event.changedTouches`.
     *
     * Reads the DOM `TouchEvent` dynamically (the Kotlin/JS stdlib type for
     * `TouchEvent` is incomplete in IR). `preventDefault()` stops the browser
     * from also synthesizing mouse events and page scrolling for the contacts.
     */
    private fun dispatchTouches(token: WebAttachmentToken, e: Event) {
        val phase = domTouchTypeToPhase(e.type)
        val touches = e.asDynamic().changedTouches
        val count = (touches.length as Number).toInt()
        val contacts = (0 until count).map { i ->
            val t = touches[i]
            WebTouchContact(
                id = (t.identifier as Number).toDouble().toLong(),
                clientX = (t.clientX as Number).toDouble(),
                clientY = (t.clientY as Number).toDouble(),
            )
        }
        eventAdapter.touches(token, phase, contacts)
        if (preventDefaultEnabled) e.preventDefault()
    }

    private fun dispatchPointerEvent(token: WebAttachmentToken, e: Event) {
        val pe = e.unsafeCast<PointerEventData>()
        eventAdapter.pointer(
            token = token,
            eventType = e.type,
            clientX = pe.clientX,
            clientY = pe.clientY,
            pointerId = pe.pointerId.toLong(),
            pointerType = pe.pointerType,
            domPrimary = pe.isPrimary,
            button = pe.button.toInt(),
        )
    }

    /**
     * Observes `window.devicePixelRatio` via a re-arming `matchMedia` listener.
     *
     * A `(resolution: <dpr>dppx)` media query only fires once when the ratio
     * leaves the current value, so the handler re-arms a fresh query each time.
     * The chain stops when the captured attachment token becomes stale (see [detach]).
     */
    private fun observeDevicePixelRatio(generation: Int) {
        val self = this
        js(
            """(function(cb, isAttached) {
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
            })(function(f) { self.dispatchScaleFactorForAttachment(generation); },
               function() { return self.isAttachmentCurrent(generation); })"""
        )
    }

    /**
     * Observes `prefers-color-scheme` via `matchMedia` and calls [onThemeChange]
     * when the user toggles between dark and light mode.
     */
    private fun observeColorScheme(generation: Int) {
        val self = this
        js(
            """(function(cb) {
                var mq = window.matchMedia('(prefers-color-scheme: dark)');
                var handler = function() {
                    cb(mq.matches);
                };
                mq.addEventListener('change', handler);
            })(function(dark) { self.dispatchThemeChangedForAttachment(generation, dark); })"""
        )
    }

    /** Called from the matchMedia handler when prefers-color-scheme toggles. */
    @JsName("dispatchThemeChanged")
    fun dispatchThemeChanged(dark: Boolean) {
        attachmentToken?.let { token ->
            eventAdapter.runIfCurrent(token) { onThemeChange?.invoke(dark) }
        }
    }

    @JsName("dispatchThemeChangedForAttachment")
    internal fun dispatchThemeChangedForAttachment(generation: Int, dark: Boolean) {
        eventAdapter.runIfCurrent(WebAttachmentToken(generation)) { onThemeChange?.invoke(dark) }
    }

    /** Called from the re-arming matchMedia handler with the new devicePixelRatio. */
    @JsName("dispatchScaleFactor")
    @Suppress("UNUSED_PARAMETER")
    fun dispatchScaleFactor(factor: Double) {
        attachmentToken?.let(eventAdapter::devicePixelRatioChanged)
    }

    @JsName("dispatchScaleFactorForAttachment")
    internal fun dispatchScaleFactorForAttachment(generation: Int) {
        eventAdapter.devicePixelRatioChanged(WebAttachmentToken(generation))
    }

    /** Predicate exposed to JS so the re-arming DPR chain stops after [detach]. */
    @JsName("isAttached")
    fun isAttached(): Boolean = attachmentToken?.let(eventAdapter::isCurrent) == true

    @JsName("isAttachmentCurrent")
    internal fun isAttachmentCurrent(generation: Int): Boolean =
        eventAdapter.isCurrent(WebAttachmentToken(generation))

    /**
     * Called from the JS ResizeObserver with the new dimensions.
     */
    @JsName("dispatchResized")
    @Suppress("UNUSED_PARAMETER")
    fun dispatchResized(width: Int, height: Int) {
        attachmentToken?.let(eventAdapter::resized)
    }

    @JsName("dispatchResizedForAttachment")
    internal fun dispatchResizedForAttachment(generation: Int) {
        eventAdapter.resized(WebAttachmentToken(generation))
    }

    // ── R5-IME: hidden input overlay ─────────────────────────────────────────

    override fun setImeAllowed(allowed: Boolean) {
        if (allowed) {
            val token = attachmentToken ?: return
            val input = imeInput ?: createImeInputBox(token).also { imeInput = it }
            input.focus()
        } else {
            imeInput?.blur()
        }
    }

    override fun setImePurpose(purpose: String) {
        imeInput?.let { it.asDynamic().inputMode = purpose }
    }

    override fun setImeCursorArea(x: Int, y: Int, width: Int, height: Int) {
        val input = imeInput ?: return
        val s = input.style
        s.left = "${x}px"
        s.top = "${y}px"
        s.width = "${width}px"
        s.height = "${height}px"
    }

    /**
     * Creates the hidden <input> element and wires IME composition event
     * listeners. Appends it to the canvas parent (or document.body as fallback).
     */
    private fun createImeInputBox(token: WebAttachmentToken): HTMLInputElement {
        val input = document.createElement("input").unsafeCast<HTMLInputElement>().apply {
            style.position = "absolute"
            style.opacity = "0"
            style.height = "0px"
            style.width = "0px"
            style.left = "0px"
            style.top = "0px"
            style.zIndex = "-1"
        }
        input.style.asDynamic().pointerEvents = "none"
        // Suppress browser auto-correction / auto-fill on the hidden input
        input.asDynamic().autocapitalize = "off"
        input.asDynamic().autocomplete = "off"
        input.asDynamic().autocorrect = "off"
        input.asDynamic().spellcheck = false

        canvasElement?.let { it.parentElement?.appendChild(input) }
            ?: document.body?.appendChild(input)

        input.addEventListener("compositionstart", EventListener {
            dispatch(token, WebWindowEvent.Ime(WebImeEvent.Enabled))
        })

        input.addEventListener("compositionupdate", EventListener { event ->
            val data = event.asDynamic().data as? String ?: ""
            dispatch(token, WebWindowEvent.Ime(WebImeEvent.Preedit(text = data, cursorRange = null)))
        })

        input.addEventListener("compositionend", EventListener { event ->
            val data = event.asDynamic().data as? String ?: ""
            dispatch(token, WebWindowEvent.Ime(WebImeEvent.Commit(text = data)))
            dispatch(token, WebWindowEvent.Ime(WebImeEvent.Disabled))
            input.value = ""
        })

        return input
    }

    override fun getCanvasElement(): Any? = canvasElement

    override fun detach() {
        WebMetricsTransactions.disconnectActive(this)
        eventAdapter.detach()
        attachmentToken = null

        val canvas = targetElement

        if (canvas != null) {
            for ((type, handler) in canvasListeners) {
                canvas.removeEventListener(type, handler)
            }
        }
        canvasListeners.clear()

        val docDynamic = document.asDynamic()
        for ((type, handler) in documentListeners) {
            docDynamic.removeEventListener(type, handler)
        }
        documentListeners.clear()

        val winDynamic = window.asDynamic()
        for ((type, handler) in windowListeners) {
            winDynamic.removeEventListener(type, handler)
        }
        windowListeners.clear()

        resizeObserver?.disconnect()
        resizeObserver = null

        imeInput?.remove()
        imeInput = null

        targetElement = null
        canvasElement = null
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private fun readCurrentCanvasMetrics(): CanvasMetrics =
        readCanvasMetrics(canvasElement ?: error("Web bridge is not attached to a canvas"))

    private fun readCanvasMetrics(canvas: Element): CanvasMetrics {
        val rect = canvas.asDynamic().getBoundingClientRect()
        return CanvasMetrics(
            leftCss = (rect.left as Number).toDouble(),
            topCss = (rect.top as Number).toDouble(),
            widthCss = (rect.width as Number).toDouble(),
            heightCss = (rect.height as Number).toDouble(),
            devicePixelRatio = window.devicePixelRatio,
        )
    }

    private fun addListener(
        target: Element,
        type: String,
        token: WebAttachmentToken,
        handler: (Event) -> Unit,
    ) {
        val guarded: (Event) -> Unit = { event ->
            eventAdapter.runIfCurrent(token) { handler(event) }
        }
        target.addEventListener(type, guarded)
        canvasListeners.add(Pair(type, guarded))
    }

    private fun addDocumentListener(
        type: String,
        token: WebAttachmentToken,
        handler: (Event) -> Unit,
    ) {
        val guarded: (Event) -> Unit = { event ->
            eventAdapter.runIfCurrent(token) { handler(event) }
        }
        document.asDynamic().addEventListener(type, guarded)
        documentListeners.add(Pair(type, guarded))
    }

    private fun addWindowListener(
        type: String,
        token: WebAttachmentToken,
        handler: (Event) -> Unit,
    ) {
        val guarded: (Event) -> Unit = { event ->
            eventAdapter.runIfCurrent(token) { handler(event) }
        }
        window.asDynamic().addEventListener(type, guarded)
        windowListeners.add(Pair(type, guarded))
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

    // ── Task 14: safeArea insets + ownedDisplayHandle ─────────────────────────

    override fun getSafeAreaInsets(): Insets<Int> {
        val body = document.body ?: return Insets(0, 0, 0, 0)
        val div = document.createElement("div").asDynamic()
        body.asDynamic().appendChild(div)
        div.style.setProperty("padding-top", "env(safe-area-inset-top, 0px)")
        div.style.setProperty("padding-bottom", "env(safe-area-inset-bottom, 0px)")
        div.style.setProperty("padding-left", "env(safe-area-inset-left, 0px)")
        div.style.setProperty("padding-right", "env(safe-area-inset-right, 0px)")
        val cs = window.asDynamic().getComputedStyle(div)
        fun parsePx(v: Any?): Int {
            val s = v as? String ?: "0px"
            return if (s.endsWith("px")) s.removeSuffix("px").trim().toIntOrNull() ?: 0 else 0
        }
        val insets = Insets(
            top = parsePx(cs.paddingTop),
            bottom = parsePx(cs.paddingBottom),
            left = parsePx(cs.paddingLeft),
            right = parsePx(cs.paddingRight),
        )
        body.asDynamic().removeChild(div)
        return insets
    }

    override fun getDisplayHandle(): Long {
        val screen = window.asDynamic().screen
        return ((screen.availWidth as Int).toLong() shl 32) or (screen.availHeight as Int).toLong()
    }

    // ── R2: Fullscreen API ────────────────────────────────────────────────────

    /**
     * Calls `element.requestFullscreen()` on the target canvas.
     * Uses the dynamic API to handle browser prefixes gracefully.
     */
    override fun requestFullscreen(canvasId: String) {
        val el = document.getElementById(canvasId) ?: targetElement ?: return
        try {
            val d = el.asDynamic()
            when {
                d.requestFullscreen != null -> d.requestFullscreen()
                d.webkitRequestFullscreen != null -> d.webkitRequestFullscreen()
                d.mozRequestFullScreen != null -> d.mozRequestFullScreen()
            }
        } catch (_: Throwable) {}
    }

    // ── R3: cursor, pointer lock, hit-test ───────────────────────────────────

    /**
     * Sets `style.cursor` on the canvas element identified by [canvasId].
     */
    override fun setCssCursor(canvasId: String, cssCursorValue: String) {
        val el = document.getElementById(canvasId) ?: canvasElement ?: return
        el.asDynamic().style.cursor = cssCursorValue
    }

    override fun setPointerEvents(canvasId: String, pointerEventsValue: String) {
        val el = document.getElementById(canvasId) ?: canvasElement ?: return
        el.asDynamic().style.pointerEvents = pointerEventsValue
    }

    /**
     * Requests Pointer Lock on the canvas element (handles vendor prefixes).
     *
     * The browser may require a user gesture; the lock is granted asynchronously
     * via a `pointerlockchange` event.
     */
    override fun requestPointerLock(canvasId: String) {
        val el = document.getElementById(canvasId) ?: canvasElement ?: return
        try {
            val d = el.asDynamic()
            when {
                d.requestPointerLock != null -> d.requestPointerLock()
                d.webkitRequestPointerLock != null -> d.webkitRequestPointerLock()
                d.mozRequestPointerLock != null -> d.mozRequestPointerLock()
            }
        } catch (_: Throwable) {}
    }

    /**
     * Calls `document.exitPointerLock()` (handles vendor prefixes).
     */
    override fun exitPointerLock() {
        try {
            val d = document.asDynamic()
            when {
                d.exitPointerLock != null -> d.exitPointerLock()
                d.webkitExitPointerLock != null -> d.webkitExitPointerLock()
                d.mozExitPointerLock != null -> d.mozExitPointerLock()
            }
        } catch (_: Throwable) {}
    }

    /**
     * Returns true if the canvas element currently holds Pointer Lock.
     */
    override fun isPointerLocked(): Boolean = document.asDynamic().pointerLockElement == canvasElement

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
     * Creates a `<canvas>`, paints the RGBA pixels via `putImageData`,
     * and returns `canvas.toDataURL("image/png")`.
     */
    override fun createCursorDataUrl(rgba: ByteArray, width: Int, height: Int, hotspotX: Int, hotspotY: Int): String {
        try {
            val canvas = document.createElement("canvas").asDynamic()
            canvas.width = width
            canvas.height = height
            val ctx = canvas.getContext("2d").asDynamic()
            val imageData = ctx.createImageData(width, height).asDynamic()
            val data = imageData.data
            for (i in rgba.indices) {
                data[i] = rgba[i]
            }
            ctx.putImageData(imageData, 0.0, 0.0)
            val url: String = canvas.toDataURL("image/png") as String
            return url
        } catch (_: Throwable) { return "" }
    }

    /**
     * Calls `document.exitFullscreen()`.
     */
    override fun exitFullscreen() {
        try {
            val d = document.asDynamic()
            when {
                d.exitFullscreen != null -> d.exitFullscreen()
                d.webkitExitFullscreen != null -> d.webkitExitFullscreen()
                d.mozCancelFullScreen != null -> d.mozCancelFullScreen()
            }
        } catch (_: Throwable) {}
    }
}

/**
 * External interface describing the fields of a DOM PointerEvent accessible from JS.
 *
 * Used to access `clientX`, `clientY` and `button` on `pointerdown`,
 * `pointerup` and `pointermove` events, which inherit from MouseEvent
 * but whose direct cast via `as MouseEvent` is not always stable in IR.
 */
private external interface PointerEventData {
    val clientX: Double
    val clientY: Double
    val button: Short
    val pointerId: Double
    val pointerType: String
    val isPrimary: Boolean
}

private fun pointerEventsSupported(): Boolean =
    js("typeof window.PointerEvent !== 'undefined'") as Boolean
