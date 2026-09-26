package org.graphiks.kadre.platform.web

import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.PenState
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalPoint
import org.w3c.dom.HTMLElement
import org.w3c.dom.Window
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.WheelEvent
import org.w3c.dom.pointerevents.PointerEvent

/**
 * The DOM-reading half of the JS input port: it reads the fields of a browser event, calls the
 * shared mapping core of `WebInputMapping.kt`, and hands back an immutable [WebInputStimulus].
 *
 * Nothing here holds a policy and nothing here touches a DOM object after it returns: a borrowed
 * event is read within its own callback and never stored, and no returned stimulus names a DOM type.
 * The Wasm port reads the same fields through its own interop and calls the same core, so the two
 * targets cannot disagree on what a `code`, a `deltaMode` or a `pointerType` means — only on how a
 * field is read.
 *
 * The one decision this file makes that the core does not is the boundary of the phase itself: a
 * `pointerType` whose kind the core refuses (`touch`, D12) produces no stimulus at all rather than a
 * stimulus of another kind.
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
 * The kind of the pointer that produced one pointer event, or `null` when this phase delivers no
 * stimulus for it.
 *
 * The kind comes from the browser's own `pointerType` through the shared core, so the port never
 * substitutes a device for the one the browser named: a pen that reported itself as a pen is
 * delivered as a pen, with the pen state it carries, and a touch pointer is refused whole (D12).
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
 */
internal fun jsPointerPosition(element: HTMLElement, event: PointerEvent): LogicalPoint {
    val box = element.getBoundingClientRect()
    return LogicalPoint(
        event.clientX.toDouble() - box.left - element.clientLeft,
        event.clientY.toDouble() - box.top - element.clientTop,
    )
}

/**
 * The motion between the previous pointer observation and [position].
 *
 * The DOM reports no delta for a `pointermove`, so the motion is measured from the last position
 * this port observed on any pointer event, and a motion with nothing before it — the first of a
 * re-entry, or the first after the port was installed — is `(0, 0)`: no motion was observed, and
 * inventing one would be an approximation. Clearing the last position on a leave is what makes the
 * first motion of a re-entry measure from its entry point.
 */
internal fun jsPointerDelta(previous: LogicalPoint?, position: LogicalPoint): LogicalDelta =
    if (previous == null) {
        LogicalDelta(0.0, 0.0)
    } else {
        LogicalDelta(position.x - previous.x, position.y - previous.y)
    }

/** The pointer pressure the model can carry, or `null` when this event reports one it cannot. */
internal fun jsPointerPressure(event: PointerEvent): Double? = webPointerPressure(event.pressure.toDouble())

/**
 * The pen state of one observation, for the kinds that can carry one.
 *
 * Only a pen carries a pen state: `PointerState` and the pointer events themselves require it, and a
 * mouse reports zeros for every pen member, which would otherwise read as a pen lying flat and
 * untwisted. The angles are the browser's own fields, and the core drops the ones outside the
 * domains the model validates instead of clamping them.
 */
internal fun jsPointerPenState(kind: PointerKind, event: PointerEvent): PenState? =
    if (kind == PointerKind.Pen) {
        webPenState(
            tiltXDegrees = event.tiltX.toDouble(),
            tiltYDegrees = event.tiltY.toDouble(),
            twistDegrees = event.twist.toDouble(),
            tangentialPressure = event.tangentialPressure.toDouble(),
        )
    } else {
        null
    }

/**
 * The scroll stimulus of one wheel event at [coalescingBoundary], or `null` when the model cannot
 * carry that delta — a page-mode wheel (D9) or a component that is not finite.
 */
internal fun jsScrollStimulus(event: WheelEvent, coalescingBoundary: Long): WebInputStimulus.Scrolled? {
    val delta = webScrollDelta(event.deltaMode, event.deltaX, event.deltaY) ?: return null
    return WebInputStimulus.Scrolled(delta = delta, coalescingBoundary = coalescingBoundary)
}

/**
 * The scroll-coalescing frontier of the Web port, as the phase's Web rule defines it.
 *
 * The DOM exposes neither the native phase nor the momentum phase an AppKit event carries, so the
 * frontier is the browser's own delivery granularity instead: it opens when the browser's unit of
 * measurement changes (`deltaMode`), when the pointer's button state changes, and for the first wheel
 * every animation frame delivers. Every other wheel shares the frontier of the wheel before it, which
 * is exactly what lets the runtime merge the scrolls of one frame and what keeps the separations the
 * browser did report. No listener is registered here: one animation-frame callback is armed when a
 * wheel event is observed and cancelled when the port releases it.
 */
internal class JsScrollCoalescingFrontier(private val browsingWindow: Window?) {
    private var frontier: Long = 0L
    private var lastDeltaMode: Int? = null
    private var lastButtons: Short? = null
    private var frameIsNew: Boolean = true
    private var frameMarker: Int? = null

    /**
     * Records one observed wheel event and answers the frontier its scroll belongs to.
     *
     * A wheel the port cannot deliver — a page-mode one — is still an observation of the browser, so
     * it moves the frontier like any other: the frontier describes what the browser delivered, not
     * what the model could carry.
     */
    fun frontierFor(event: WheelEvent): Long {
        val opened = frameIsNew || lastDeltaMode != event.deltaMode || lastButtons != event.buttons
        lastDeltaMode = event.deltaMode
        lastButtons = event.buttons
        frameIsNew = false
        armFrameMarker()
        if (opened) frontier += 1L
        return frontier
    }

    /**
     * Cancels the frame registration this frontier owns.
     *
     * A browsing context that cannot schedule frames has none to cancel: the first wheel of a frame
     * cannot be told from the one after it there, so only the unit and button rules separate scrolls.
     */
    fun close() {
        val marker = frameMarker ?: return
        frameMarker = null
        runCatching { browsingWindow?.cancelAnimationFrame(marker) }
    }

    /** Registers the frame callback that opens the next frontier, unless one is already registered. */
    private fun armFrameMarker() {
        if (frameMarker != null) return
        val browserWindow = browsingWindow ?: return
        frameMarker = browserWindow.requestAnimationFrame {
            frameMarker = null
            frameIsNew = true
        }
    }
}
