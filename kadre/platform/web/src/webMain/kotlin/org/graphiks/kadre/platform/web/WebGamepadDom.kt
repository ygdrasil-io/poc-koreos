package org.graphiks.kadre.platform.web

/**
 * One gamepad as the browser's Gamepad API reports it, copied structurally.
 *
 * This is the DOM-free shape everything above the seam consumes: the hub diffs it, the mapping
 * dresses it, and no browser object ever crosses. `index` is the DOM gamepad index — stable per
 * connection, and the key every Kadre-side identity of this pad derives from. `mapping` is the
 * browser's own word (`"standard"`, an empty string, or a vendor string; `null` when the browser
 * omitted it) — the mapping layer reads it, nothing else does. `buttonValues`/`axisValues` are the
 * raw DOM readings in DOM order, copied at read time so a stored observation never changes behind
 * the hub's back.
 */
internal interface WebDomGamepad {
    val index: Int
    val domId: String
    val connected: Boolean
    val mapping: String?
    val buttonValues: List<Double>
    val axisValues: List<Double>
}

/**
 * The Gamepad API seam of the browsing context: a complete poll, and the moments a pad may have
 * appeared or disappeared. Test doubles implement this; each target has one real realization.
 *
 * [getGamepads] returns the browser's own array shape — one entry per DOM gamepad index, with `null`
 * for the absent ones (the holes Chromium reports) — so a poll that cannot reach the API reports
 * nothing rather than guessing. The two listeners exist because the DOM announces connections as
 * events: they carry no pad data and mean one thing only — poll again now. A realization registers
 * them wherever the browser fires them (every engine fires at the window; some also fire at a
 * navigator that implements EventTarget, which Chromium's does not) and never lets a target that
 * accepts no listener become a failure — a missed hint costs one frame of latency, not the poll.
 */
internal interface WebGamepadDom : AutoCloseable {
    /** Complete poll result; entries may be null (holes) — indices are the DOM gamepad indices. */
    fun getGamepads(): List<WebDomGamepad?>

    /** Connect notifications (window + navigator listeners). Returning a pad triggers an immediate poll. */
    fun onGamepadAppeared(listener: () -> Unit): AutoCloseable

    /** Disconnection notifications, registered like [onGamepadAppeared]'s. */
    fun onGamepadDisappeared(listener: () -> Unit): AutoCloseable
}

/**
 * One animation-frame registration of the browsing context the hub polls in.
 *
 * A single registration admits exactly one callback, delivered asynchronously — one per animation
 * frame until cancelled. The hub re-arms the one registration it holds for as long as it polls, so
 * the browser's frame cadence is the poll cadence and a hidden page (which fires no frames) simply
 * polls not at all.
 */
internal fun interface WebFrameScheduler {
    /** One callback per animation frame until cancelled; per-target rAF realization. */
    fun schedule(frame: () -> Unit): AutoCloseable
}

/**
 * The Gamepad API seam of the page the session runs in.
 *
 * Web code cannot name a DOM type — this seam is structural — so the one function each target
 * actualizes is how the shared hub obtains the target's realization over the browsing context.
 */
internal expect fun webGamepadDom(): WebGamepadDom

/**
 * The animation-frame scheduler of the page the session runs in, per target.
 *
 * The realizations reuse the one rAF path the target already owns (the redraw coalescer's
 * registration helper) rather than adding a second one.
 */
internal expect fun webFrameScheduler(): WebFrameScheduler
