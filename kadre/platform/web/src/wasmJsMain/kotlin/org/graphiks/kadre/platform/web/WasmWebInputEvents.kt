@file:OptIn(ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.platform.web

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.PenState
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.surface.LogicalPoint
import org.w3c.dom.HTMLElement
import org.w3c.dom.Window
import org.w3c.dom.events.Event

/**
 * The DOM-reading half of the Wasm input port: it reads the fields of a browser event, calls the
 * shared core of `WebInputMapping.kt` and `WebInputTracking.kt`, and hands back an immutable
 * [WebInputStimulus].
 *
 * This is the Wasm twin of `JsWebInputEvents.kt`, function for function and with the same readings:
 * nothing here holds a policy, and nothing here decides *how* a browser fact becomes a model value.
 * What is target-specific here is only the reading — and, in Wasm, the way the reading is expressed:
 * a browser event crosses into Kotlin/Wasm as `JsAny`, so the typed event is declared as its own
 * `external interface` carrying exactly the members this port reads, and the type of a borrowed event
 * is established by a JavaScript `instanceof` through `@JsFun` — the one place where Kotlin/Wasm and
 * JavaScript meet. A raw `js(…)` snippet cannot do it, exactly as it could not provide the
 * `ResizeObserver` callback of Phase 2: it sees neither a JS object nor a Kotlin function value.
 *
 * A borrowed event is read within its own callback and never stored, and no returned stimulus names a
 * DOM type.
 */

/**
 * One `KeyboardEvent` as this port reads it: the physical key, the logical key, the location, the
 * auto-repeat flag, the four flags and the two modifier toggles.
 *
 * The members are exactly the ones read, so a member this phase does not consume cannot be read by
 * accident, and the toggles keep `getModifierState` — the only way the DOM reports CapsLock and
 * NumLock — instead of a substitute: the method answers for the key it is asked about, and a browser
 * that does not know the key answers false.
 */
internal external interface WasmKeyboardEvent : JsAny {
    val code: String
    val key: String
    val location: Int
    val repeat: Boolean
    val shiftKey: Boolean
    val ctrlKey: Boolean
    val altKey: Boolean
    val metaKey: Boolean
    fun getModifierState(key: String): Boolean
}

/**
 * One `PointerEvent` as this port reads it: its device, its position, its button and its measurements.
 *
 * `pointerId` and `buttons` are the two members the capture seam needs and no stimulus carries: the
 * identity a `setPointerCapture` names, and the browser's own answer to whether any button of that
 * pointer is still down. Both are read where the port records what the element holds, and neither
 * reaches the input model — the runtime keeps one pointer identity per surface (D11).
 *
 * Four members are read as `Double` where the JS target's own DOM declarations
 * (`kotlin-dom-api-compat`) type `clientX`/`clientY` as `Int` and `pressure`/`tangentialPressure` as
 * `Float`. This is deliberate and was *measured*, not assumed: an event whose four reads are
 * overridden to carry fractional values (`JsWebInputPrecisionTest` in `jsTest` and
 * `WasmWebInputPrecisionTest` in `wasmJsTest`, the same event and the same assertions on both targets)
 * reaches `WebInputStimulus` as `LogicalPoint(x=10.5, y=20.25)`, `pressure=0.1` and
 * `tangentialPressure=0.3` on *both* targets — a JS external read of a narrow declared type does not
 * narrow the runtime value, so the declared types are nominal. Reading them as `Double` here keeps the
 * two targets agreeing on every delivered value; declaring `Int`/`Float` instead would truncate and
 * round *only* on Wasm, which is exactly the drift this phase forbids. `tiltX`/`tiltY`/`twist` and
 * `button` are integers in both declarations and are read as `Int`.
 */
internal external interface WasmPointerEvent : JsAny {
    val pointerId: Int
    val pointerType: String
    val clientX: Double
    val clientY: Double
    val button: Int
    val buttons: Int
    val pressure: Double
    val tiltX: Int
    val tiltY: Int
    val twist: Int
    val tangentialPressure: Double
}

/** One `WheelEvent` as this port reads it: the browser's own unit and the pointer's button state. */
internal external interface WasmWheelEvent : JsAny {
    val deltaMode: Int
    val deltaX: Double
    val deltaY: Double
    val buttons: Int
}

/**
 * The borrowed event as a keyboard event, or `null` when it is not one.
 *
 * This is the Wasm form of the JS port's `event as? KeyboardEvent`: Kotlin/Wasm erases the type of a
 * borrowed DOM value, so the check lives in the JavaScript alone, next to the value it checks. A
 * listener is registered for its own event type, so the answer is the browser's own contract; the
 * check is kept because dropping a mismatched event beats reading fields off one.
 */
@JsFun("(event) => event instanceof KeyboardEvent ? event : null")
internal external fun wasmKeyboardEventOrNull(event: Event): WasmKeyboardEvent?

/** The borrowed event as a pointer event, or `null` when it is not one. */
@JsFun("(event) => event instanceof PointerEvent ? event : null")
internal external fun wasmPointerEventOrNull(event: Event): WasmPointerEvent?

/** The borrowed event as a wheel event, or `null` when it is not one. */
@JsFun("(event) => event instanceof WheelEvent ? event : null")
internal external fun wasmWheelEventOrNull(event: Event): WasmWheelEvent?

/**
 * The stimulus of one key event, read from a `keydown` or a `keyup`.
 *
 * [pressed] is the event type's own fact rather than a field of the event, because the DOM has no
 * "key state" member. `repeat` is the browser's flag only on a press: a native auto-repeat reports
 * presses alone, so a release is never a repeat even when the event object says otherwise, and the
 * union's own rule (`a key release cannot repeat`) is preserved without dropping the observation.
 */
internal fun wasmKeyStimulus(event: WasmKeyboardEvent, pressed: Boolean): WebInputStimulus.KeyChanged =
    WebInputStimulus.KeyChanged(
        physicalKey = webPhysicalKey(event.code),
        logicalKey = webLogicalKey(event.key),
        location = webKeyLocation(event.location),
        keyState = if (pressed) KeyState.Pressed else KeyState.Released,
        repeat = pressed && event.repeat,
        modifiers = webKeyboardModifiers(
            shift = event.shiftKey,
            control = event.ctrlKey,
            alt = event.altKey,
            meta = event.metaKey,
            capsLock = event.getModifierState("CapsLock"),
            numLock = event.getModifierState("NumLock"),
        ),
    )

/**
 * The kind of the pointer that produced one pointer event, or `null` when this phase delivers no
 * stimulus for it.
 *
 * The kind comes from the browser's own `pointerType` through the shared core, so the port never
 * substitutes a device for the one the browser named: a pen that reported itself as a pen is
 * delivered as a pen, with the pen state it carries, and a touch pointer is refused whole (D12).
 */
internal fun wasmPointerKind(event: WasmPointerEvent): PointerKind? = webPointerKind(event.pointerType)

/**
 * The position of one pointer observation, in the surface's own logical space.
 *
 * `DESIGN.md` fixes input coordinates to the surface, whose logical box is the element's
 * `clientWidth`/`clientHeight` — the padding box this very port reads as its size. So the position
 * is the client coordinate of the event minus the element's own origin: its border box
 * (`getBoundingClientRect`, which is viewport-relative like `clientX`) moved in by its border
 * (`clientLeft`/`clientTop`) and out by any ancestor scroll the rect already reflects.
 *
 * The reading is target-specific because it is a measurement of this element in this page; the motion
 * a port derives from two such positions is not, and lives in [WebPointerMotion].
 */
internal fun wasmPointerPosition(element: HTMLElement, event: WasmPointerEvent): LogicalPoint {
    val box = element.getBoundingClientRect()
    return LogicalPoint(
        event.clientX - box.left - element.clientLeft.toDouble(),
        event.clientY - box.top - element.clientTop.toDouble(),
    )
}

/** The pointer pressure the model can carry, or `null` when this event reports one it cannot. */
internal fun wasmPointerPressure(event: WasmPointerEvent): Double? = webPointerPressure(event.pressure)

/**
 * The pen state of one pointer observation, read from the browser's four pen members.
 *
 * Only the reading is here: which kinds may carry a pen state, and what happens to a value outside
 * the domains the model documents, are the shared rule of [webPenStateFor], so this port — which
 * reads those four fields through its own interop — cannot get either wrong.
 */
internal fun wasmPointerPenState(kind: PointerKind, event: WasmPointerEvent): PenState? =
    webPenStateFor(
        kind = kind,
        tiltXDegrees = event.tiltX.toDouble(),
        tiltYDegrees = event.tiltY.toDouble(),
        twistDegrees = event.twist.toDouble(),
        tangentialPressure = event.tangentialPressure,
    )

/**
 * The scroll stimulus of one wheel event at [coalescingBoundary], or `null` when the model cannot
 * carry that delta — a page-mode wheel (D9) or a component that is not finite.
 */
internal fun wasmScrollStimulus(event: WasmWheelEvent, coalescingBoundary: Long): WebInputStimulus.Scrolled? {
    val delta = webScrollDelta(event.deltaMode, event.deltaX, event.deltaY) ?: return null
    return WebInputStimulus.Scrolled(delta = delta, coalescingBoundary = coalescingBoundary)
}

/**
 * The DOM-side source of the "this wheel is the first one a new animation frame delivers" fact that
 * [WebScrollBoundary] rules on.
 *
 * The DOM exposes no phase and no frame counter, so the fact is observed by asking the browsing
 * context for the next frame: while that callback is still pending, every wheel belongs to the frame
 * the browser is already in; when it fires, [onFrame] hears that a new frame began. One registration
 * at a time is armed, and only when a wheel is observed, so a port that never scrolls never asks the
 * browser for a frame; [close] cancels a pending one, because a registration that outlives the port
 * it was armed for is a leak. A browsing context that cannot schedule frames reports no frame at all,
 * which leaves the other two rules of the boundary to separate the scrolls it did deliver.
 *
 * The registration is the same DOM member the JS port uses (`Window.requestAnimationFrame`) with the
 * same Kotlin callback, which crosses the boundary like the port's other listeners.
 */
internal class WasmAnimationFrameMarker(
    private val browsingWindow: Window?,
    private val onFrame: () -> Unit,
) {
    private var marker: Int? = null

    /** Arms the frame registration, unless one is already pending for the frame already begun. */
    fun arm() {
        if (marker != null) return
        val browserWindow = browsingWindow ?: return
        marker = browserWindow.requestAnimationFrame {
            marker = null
            onFrame()
        }
    }

    /** Cancels the pending registration, if any. */
    fun close() {
        val pending = marker ?: return
        marker = null
        runCatching { browsingWindow?.cancelAnimationFrame(pending) }
    }
}
