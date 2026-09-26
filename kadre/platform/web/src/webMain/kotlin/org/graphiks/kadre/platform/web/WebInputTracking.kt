package org.graphiks.kadre.platform.web

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
