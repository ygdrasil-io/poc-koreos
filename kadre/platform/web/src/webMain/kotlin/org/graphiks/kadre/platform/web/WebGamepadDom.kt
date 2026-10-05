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

    /**
     * The pad's haptic actuator, as the browser reports it; `null` where the browser offers none.
     *
     * Realizations extract the actuator only where the browser's own members exist (`vibrationActuator`
     * with a callable `playEffect` — Chrome, and newer Chromium's `GamepadHapticActuator` itself): a
     * browser without the member yields `null`, and a `null` actuator is an honest `Unsupported`
     * effect capability, never a guessed one.
     */
    val hapticActuator: WebDomHapticActuator?
}

/**
 * One pad's haptic actuator, structurally — the browser's `vibrationActuator` as the seam reads it.
 *
 * [effects] is the browser's own declaration of the effect types the actuator accepts (newer
 * Chromium's `GamepadHapticActuator.effects`, e.g. `["dual-rumble"]`); `null` when the browser
 * declares nothing, in which case the caller probes once. [playEffect] and [reset] answer with the
 * SYNCHRONOUS outcome of the call only — the browser's real answer is a promise, whose rejection
 * nobody can turn back into a synchronous verdict and which is reported, not mapped:
 *
 * - `Accepted` — the call reached the browser and handed back its promise.
 * - `Refused(code)` — the browser refused the call before any promise existed; `code` is the
 *   browser's own name for why (a DOM exception name, or `refused` when it offers none).
 */
internal interface WebDomHapticActuator {
    /** Effect type identifiers the browser reports/accepts, e.g. ["dual-rumble"]; null when undeclared. */
    val effects: List<String>?
    fun playEffect(type: String, durationMs: Int, strongMagnitude: Double, weakMagnitude: Double): WebEffectLaunch
    fun reset(): WebEffectLaunch
}

/** The synchronous result of calling into the browser: call accepted, or refused with a reason code. */
internal sealed interface WebEffectLaunch {
    data object Accepted : WebEffectLaunch
    data class Refused(val code: String) : WebEffectLaunch
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

    /**
     * Whether the browsing context is a secure context (`window.isSecureContext`). The Gamepad API
     * itself works anywhere, but the page's own fact is what the effect capability is probed against:
     * the hub reads this once per pad connection and freezes the verdict into the pad's capabilities.
     */
    val secureContext: Boolean
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
