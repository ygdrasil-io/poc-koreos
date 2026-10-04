package org.graphiks.kadre.platform.web

import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.PenState
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.input.ScrollDelta
import org.graphiks.kadre.input.TouchPhase
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalPoint

/**
 * One immutable input observation of the attached element, copied by the target before it is handed
 * over.
 *
 * This is the input twin of [WebSurfaceStimulus]: a target port maps the browser's own event objects
 * into these values and hands them to the shared surface, which is the only layer that owns
 * identities, stamps, revisions and events. No DOM type appears here, and the union carries no
 * `surfaceId` either: a stimulus describes what the element observed, and the surface it belongs to
 * is the one that received it.
 *
 * The union covers what this phase activates — keyboard, the mouse and pen pointers, touch contacts,
 * scroll, the drop observations of a drag over the element, and the loss of focus. The touch
 * contacts and the pointers stay disjoint, as the model keeps them: a contact is a [TouchChanged]
 * and never a pointer member, and a pointer is never a contact. Gestures stay out of it, because
 * this target publishes no gesture capability — no recognizer exists on the Web (D-T2), and a
 * stimulus nothing can reduce is a fiction. The entry of a drag stays out of it too, for the same
 * reason the runtime has no `DropEntered` stimulus: an offer is presented by a host call, and the
 * entry travels the drop seam of [WebInputObserver] instead.
 *
 * The reference design is AppKit's `AppKitInput`, whose members these mirror, with the same
 * immutability rule: a borrowed native event never crosses the boundary.
 */
internal sealed interface WebInputStimulus {
    /**
     * One key observation. A release can never repeat, because a native auto-repeat only reports
     * presses.
     */
    data class KeyChanged(
        val physicalKey: PhysicalKey,
        val logicalKey: LogicalKey,
        val location: KeyLocation,
        val keyState: KeyState,
        val repeat: Boolean,
        val modifiers: KeyboardModifiers,
    ) : WebInputStimulus {
        init {
            require(keyState == KeyState.Pressed || !repeat) { "a key release cannot repeat" }
        }
    }

    /**
     * One pointer entry over the element's subtree.
     *
     * [kind] is the kind the browser reported for the pointer that entered, and it is carried rather
     * than assumed: a `pointerType` of `pen` delivered as a mouse would be an approximation of a fact
     * the browser stated, which the phase's exit gate forbids. A touch contact produces no pointer
     * stimulus of any kind — it is not a pointer — so a contact's `pointerenter` delivers nothing at
     * all: the entry a contact has no fact for, since a contact has no hover.
     */
    data class PointerEntered(
        val position: LogicalPoint,
        val kind: PointerKind,
    ) : WebInputStimulus

    /**
     * One pointer motion. [pressure] is the pointer's own pressure, or null when it reports none, and
     * [pen] is the pen state of a pointer that reported one — never of a mouse, which reports zeros.
     */
    data class PointerMoved(
        val position: LogicalPoint,
        val delta: LogicalDelta,
        val pressure: Double?,
        val kind: PointerKind,
        val pen: PenState?,
    ) : WebInputStimulus

    /** One pointer-button transition at its own position, of its own kind. */
    data class PointerButtonChanged(
        val button: PointerButton,
        val buttonState: PointerButtonState,
        val position: LogicalPoint,
        val pressure: Double?,
        val kind: PointerKind,
        val pen: PenState?,
    ) : WebInputStimulus

    /**
     * One touch contact observation, in the phase its own pointer event described.
     *
     * [nativeIdentity] is the identity of the contact the browser reported: one stable reference per
     * contact, held by the port's contact table for the gesture's duration, which is what the shared
     * reducer keys its own contact identities by. A contact is never a pointer — this member exists
     * so the two stay disjoint — and its pressure is the one the event reported, dropped to `null`
     * when it reported none the model can carry.
     *
     * A `pointercancel` the browser fires under a native scroll arrives here as
     * `TouchPhase.Cancelled`, delivered as reported: the host owns `touch-action`, and a contact it
     * let the browser revoke is not compensated.
     */
    data class TouchChanged(
        val nativeIdentity: Any,
        val phase: TouchPhase,
        val position: LogicalPoint,
        val pressure: Double?,
    ) : WebInputStimulus

    /**
     * The pointer left the element's subtree, or the browser cancelled it.
     *
     * A cancellation is the same fact for the reducer: the runtime drops the pointer of that kind
     * with everything the pointer held, which is what reconciles a lost contact or a revoked capture.
     */
    data class PointerLeft(val kind: PointerKind) : WebInputStimulus

    /**
     * One scroll observation.
     *
     * [coalescingBoundary] is the target's own coalescing frontier: equal boundaries may merge, and a
     * target advances it exactly at the frontier its own platform can report. A native target
     * advances it when the browser's phase or momentum phase changes — the rule of AppKit's
     * `AppKitScrollBoundary`; a Web target has neither, so it advances it at the frontier its platform
     * does expose, one browser delivery frame (the rule of `WebScrollBoundary` in `webMain`). The
     * boundary is a coalescing fact, never public input state.
     */
    data class Scrolled(
        val delta: ScrollDelta,
        val coalescingBoundary: Long,
    ) : WebInputStimulus {
        init {
            require(coalescingBoundary >= 0L) { "coalescingBoundary must be non-negative" }
        }
    }

    /**
     * One drag over the element, while a drag it presented an offer for is in flight.
     *
     * The offer it rides is the surface's own record — the one it presented and a handler accepted —
     * so the reducer can tie the motion to the offer it still holds; a drag over an element with no
     * active offer is delivered all the same and reduced by nothing, which is exactly what the model
     * does with input no offer answers. This is not a `SurfaceStimulus.DropEntered` substitute: the
     * entry itself travels the drop seam ([WebInputObserver.onDropEntered]) and never appears in
     * this union, because the model presents an offer by a host call, not by a stimulus.
     */
    data class DropMoved(val position: LogicalPoint) : WebInputStimulus

    /**
     * The drag left the element's subtree, taking the offer it carried with it.
     *
     * The surface ties it to the offer it still holds and the reducer ends that offer — the drag
     * that left can claim nothing.
     */
    data object DropExited : WebInputStimulus

    /**
     * The drag was dropped on the element.
     *
     * The position is the drop's own, and the surface ties the observation to the offer it holds:
     * the reducer makes that offer's transfer claimable, which is the one moment the payload a read
     * resolves is the browser's to give. A drop over an element with no active offer is delivered
     * all the same and reduced by nothing.
     */
    data class DropPerformed(val position: LogicalPoint) : WebInputStimulus

    /**
     * The element's subtree, its browsing context, or the whole document stopped being active.
     *
     * The surface derives it from the lifecycle reduction rather than from a browser event: the
     * lifecycle observer is what sees `focusout`, `blur` and `visibilitychange`/`pagehide`, and a
     * loss of activation is the one transition that neutralises the input snapshot.
     */
    data object FocusLost : WebInputStimulus
}
