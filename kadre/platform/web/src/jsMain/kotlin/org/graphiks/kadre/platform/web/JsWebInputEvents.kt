package org.graphiks.kadre.platform.web

import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.PenState
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.surface.LogicalPoint
import org.w3c.dom.HTMLElement
import org.w3c.dom.Window
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.WheelEvent
import org.w3c.dom.pointerevents.PointerEvent

/**
 * The DOM-reading half of the JS input port: it reads the fields of a browser event, calls the
 * shared core of `WebInputMapping.kt` and `WebInputTracking.kt`, and hands back an immutable
 * [WebInputStimulus].
 *
 * Nothing here holds a policy, and nothing here decides *how* a browser fact becomes a model value:
 * which usage a `code` means, which kind a `pointerType` is, which delta a `deltaMode` carries, when
 * a scroll may merge and what a motion is all live in the shared core, so the Wasm port — which reads
 * the same fields through its own interop — cannot drift from this one. What is target-specific here
 * is only the reading: the DOM types, the fields they expose, and the two readings a browser event
 * does not carry at all (the surface-relative position of a pointer, and the moment a new animation
 * frame begins).
 *
 * A borrowed event is read within its own callback and never stored, and no returned stimulus names a
 * DOM type.
 */

/**
 * The stimulus of one key event, read from a `keydown` or a `keyup`.
 *
 * [pressed] is the event type's own fact rather than a field of the event, because the DOM has no
 * "key state" member. `repeat` is the browser's flag only on a press: a native auto-repeat reports
 * presses alone, so a release is never a repeat even when the event object says otherwise, and the
 * union's own rule (`a key release cannot repeat`) is preserved without dropping the observation.
 */
internal fun jsKeyStimulus(event: KeyboardEvent, pressed: Boolean): WebInputStimulus.KeyChanged =
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
 * The kind of the pointer that produced one pointer event, or `null` when the event is not a pointer
 * observation of this model at all.
 *
 * The kind comes from the browser's own `pointerType` through the shared core, so the port never
 * substitutes a device for the one the browser named: a pen that reported itself as a pen is
 * delivered as a pen, with the pen state it carries. The `null` of a touch contact is the routing
 * predicate of every delivery path: a contact is not a pointer, so it goes to the touch path
 * ([webTouchPhase], the port's contact table) and never becomes a pointer observation.
 */
internal fun jsPointerKind(event: PointerEvent): PointerKind? = webPointerKind(event.pointerType)

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
internal fun jsPointerPosition(element: HTMLElement, event: PointerEvent): LogicalPoint {
    val box = element.getBoundingClientRect()
    return LogicalPoint(
        event.clientX.toDouble() - box.left - element.clientLeft,
        event.clientY.toDouble() - box.top - element.clientTop,
    )
}

/** The pointer pressure the model can carry, or `null` when this event reports one it cannot. */
internal fun jsPointerPressure(event: PointerEvent): Double? = webPointerPressure(event.pressure.toDouble())

/**
 * The pen state of one pointer observation, read from the browser's four pen members.
 *
 * Only the reading is here: which kinds may carry a pen state, and what happens to a value outside
 * the domains the model documents, are the shared rule of [webPenStateFor], so the Wasm port — which
 * reads those four fields through its own interop — cannot get either wrong.
 */
internal fun jsPointerPenState(kind: PointerKind, event: PointerEvent): PenState? =
    webPenStateFor(
        kind = kind,
        tiltXDegrees = event.tiltX.toDouble(),
        tiltYDegrees = event.tiltY.toDouble(),
        twistDegrees = event.twist.toDouble(),
        tangentialPressure = event.tangentialPressure.toDouble(),
    )

/**
 * The scroll stimulus of one wheel event at [coalescingBoundary], or `null` when the model cannot
 * carry that delta — a page-mode wheel (D9) or a component that is not finite.
 */
internal fun jsScrollStimulus(event: WheelEvent, coalescingBoundary: Long): WebInputStimulus.Scrolled? {
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
 */
internal class JsAnimationFrameMarker(
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
