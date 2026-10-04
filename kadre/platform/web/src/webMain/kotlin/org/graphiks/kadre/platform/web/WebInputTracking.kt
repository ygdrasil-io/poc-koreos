package org.graphiks.kadre.platform.web

import org.graphiks.kadre.input.TouchId
import org.graphiks.kadre.internal.runtime.RuntimeSynchronousInteraction
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalPoint

/**
 * Tracks the scroll-coalescing frontier of one surface.
 *
 * `APPKIT-PHASE-4-INPUT-DESIGN.md:93-100` fixes the frontier to the native phase and the momentum
 * phase: the runtime merges scroll stimuli that share a boundary and never merges two phases, or a
 * momentum scroll with a non-momentum one, which is how a separation the browser reported is never
 * silently lost. The DOM exposes neither phase, so the Web rule of this phase substitutes the
 * browser's own delivery granularity — the animation frame — for them: the frontier opens when the
 * browser's unit of measurement changes, when the pointer's button state changes, and for the first
 * wheel a new frame delivers; every other wheel reuses the frontier of the wheel before it.
 *
 * This lives with the mapping core rather than with a target because it is a *rule*, not a reading: a
 * port of either target reads `deltaMode` and `buttons` from its own event and calls [advance], so
 * the two targets cannot drift on when a scroll may merge. The frame is the one fact a port cannot
 * take from the wheel event, so a port reports it through [frameOpened] and the rule stays here.
 *
 * The boundaries strictly increase and are never reused, so a consumer can only ever merge the
 * scrolls this rule allows.
 */
internal class WebScrollBoundary {
    private var deltaMode: Int? = null
    private var buttons: Int? = null
    private var frameIsNew: Boolean = true
    private var boundary: Long = 0L

    /**
     * Records one observed wheel event and answers the frontier its scroll belongs to.
     *
     * A wheel whose delta the model cannot carry is still an observation of the browser — only its
     * delta is unusable — so a port may call this for every wheel it observes, which keeps the
     * frontier a description of what the browser delivered rather than of what Kadre could use.
     */
    fun advance(deltaMode: Int, buttons: Int): Long {
        val opens = frameIsNew || this.deltaMode != deltaMode || this.buttons != buttons
        this.deltaMode = deltaMode
        this.buttons = buttons
        frameIsNew = false
        if (opens) boundary += 1L
        return boundary
    }

    /** Reports that the browsing context entered a new animation frame: the next wheel opens one. */
    fun frameOpened() {
        frameIsNew = true
    }

    /**
     * Forgets what was observed, so the next [advance] opens a frontier as the first one did.
     *
     * The counter is not reset: a boundary that was already published is never handed out again,
     * whatever the port did in between.
     */
    fun clear() {
        deltaMode = null
        buttons = null
        frameIsNew = true
    }
}

/**
 * Tracks the last position observed for one pointer, and derives the motion between two observations.
 *
 * The DOM has no motion member for a `pointermove` — `movementX`/`movementY` belong to `MouseEvent`
 * and are not part of `PointerEvent` — so the motion the model carries is measured here, as the
 * difference between this observation's position and the previous one's. Measuring it rather than
 * carrying a browser number is also what makes it usable: the runtime coalesces motions by *summing*
 * deltas (`RuntimeSurfaceInput.kt:1301-1312`), so a cumulative difference could not be carried.
 *
 * A port records every observation it delivers. An entry opens the motion, so the first motion of a
 * pointer measures from where it entered; a button transition moves it, so a motion that follows one
 * does not repeat movement the transition already reported; and [clear] forgets it, which a leave
 * does, so a re-entry measures from its own entry point. A motion with nothing before it — the first
 * observation a port ever made — is `(0, 0)`: no motion was observed, and inventing one would be an
 * approximation. The runtime keeps one pointer identity per surface (D11), so one instance tracks
 * exactly that one pointer.
 */
internal class WebPointerMotion {
    private var previous: LogicalPoint? = null

    /** Records one observation and answers the motion since the previous one, `(0, 0)` if none. */
    fun advance(position: LogicalPoint): LogicalDelta {
        val motion = previous?.let { LogicalDelta(position.x - it.x, position.y - it.y) }
            ?: LogicalDelta(0.0, 0.0)
        record(position)
        return motion
    }

    /**
     * Records one observation whose motion no stimulus carries: an entry states a position, not a
     * movement, and a button transition's movement belongs to the transition itself.
     */
    fun record(position: LogicalPoint) {
        previous = position
    }

    /** Forgets the previous position: the next observation has nothing to measure from. */
    fun clear() {
        previous = null
    }
}

/**
 * The contacts this port currently holds, keyed by the browser's own `pointerId`.
 *
 * A touch contact's stimulus carries a `nativeIdentity` the ordinary-input reducer keys by
 * reference (`IdentityKeyedMap` — native identities, never value types), while the only identity
 * the browser gives a contact is its `pointerId`, an integer value repeated on every event of the
 * gesture. The table is the bridge between the two: it mints one stable identity object per contact
 * at its `pointerdown` and hands the *same* reference back for every later event of that
 * `pointerId`, so the reducer reads one contact, not one contact per event.
 *
 * The whole rule rests on one fact of the DOM, cited here where it is relied on: **a
 * `pointerId` is stable for the lifetime of its contact** for a touch pointer — the browser
 * guarantees it, and the table would otherwise re-key the gesture on every event. The table also
 * mirrors the reducer's own contact lifetime: a contact begun twice under one `pointerId` is a
 * duplicated `down` and answers nothing, a move of a contact never begun answers nothing, and a
 * `pointerup`/`pointercancel` retires the identity, after which later events of that `pointerId`
 * answer nothing — exactly the reductions the shared reducer performs for the same shapes.
 *
 * One instance per port, like [WebPointerMotion]: the element is the whole contact surface this
 * table describes, and `clear` goes with the port's other per-element resets.
 */
internal class WebTouchContacts {
    private val identitiesByPointerId = mutableMapOf<Int, Any>()

    /**
     * Opens a contact and answers its stable identity, or `null` when one is already active under
     * this `pointerId` — a second `down` of a live contact is not a second contact.
     */
    fun begin(pointerId: Int): Any? {
        if (identitiesByPointerId.containsKey(pointerId)) return null
        val identity = Any()
        identitiesByPointerId[pointerId] = identity
        return identity
    }

    /** The identity of the active contact under [pointerId], or `null` when none is. */
    fun identity(pointerId: Int): Any? = identitiesByPointerId[pointerId]

    /**
     * Closes the contact under [pointerId] and answers the identity it held, or `null` when no
     * contact is active under it. The identity is never handed out again: the contact is over.
     */
    fun retire(pointerId: Int): Any? = identitiesByPointerId.remove(pointerId)

    /** Forgets every contact: the port stopped reading the element, so none can be continued. */
    fun clear() {
        identitiesByPointerId.clear()
    }
}

/**
 * The touch identity of one interaction trigger.
 *
 * A touch `pointerdown` dispatches `RuntimeSynchronousInteraction.TouchStarted` to the installed
 * handler, and the trigger carries a `TouchId` — an opaque value the Web target may not mint by
 * inventing a number, but one it must supply, because the interaction is dispatched before the
 * ordinary stimulus of the same event (the AppKit order, `DESIGN.md:983-989`) and nothing else has
 * allocated an identity yet. This class allocates them one per dispatched trigger, monotonically,
 * the way the reducer allocates its own contact identities for the ordinary path.
 *
 * The trigger's identity and the reducer's are two allocations of the same opaque type, made in two
 * lanes, and nothing here claims they are the same value: the handler reads the trigger's identity
 * from the interaction event it receives, the consumer of the input stream reads the reducer's from
 * `InputEvent.TouchChanged`. What is promised is uniqueness within this surface's interaction lane —
 * one trigger, one identity, never reused — which is the whole contract a trigger payload has.
 */
internal class WebTouchInteractions {
    private var nextTouchId = 0L

    /** Builds the `TouchStarted` trigger of one touch `pointerdown` at [position]. */
    fun started(position: LogicalPoint): RuntimeSynchronousInteraction.TouchStarted {
        check(nextTouchId != Long.MAX_VALUE) { "touch interaction identity space exhausted" }
        val touchId = TouchId(nextTouchId)
        nextTouchId += 1L
        return RuntimeSynchronousInteraction.TouchStarted(touchId = touchId, position = position)
    }
}
