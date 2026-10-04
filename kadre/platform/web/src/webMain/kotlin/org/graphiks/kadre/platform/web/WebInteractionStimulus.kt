package org.graphiks.kadre.platform.web

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.interaction.InteractionKind
import org.graphiks.kadre.surface.PointerCaptureMode

/**
 * The pure interaction mapping of the web target: which actions this backend advertises, which
 * `LockPointer` mode it takes, and the one refusal code a browser primitive that says no carries.
 *
 * Nothing here reads the DOM — these are decisions about Kadre's own model, the same on both targets,
 * which is what lets the surface decide without a browser and `webTest` prove it without one. The
 * per-target ports consume the same values when they translate a DOM event into a trigger and when
 * they override the primitive members of [WebHostPort], so the two targets cannot drift.
 */

/**
 * The interaction actions the web surface advertises once its session configuration installed.
 *
 * Written out member by member rather than derived from the enum — a capability is a promise, and a
 * promise is spelled out — and `webTest` pins the set against the post-attach snapshot. The five are
 * exactly the actions whose browser primitive and terminal callback (the four of `DESIGN.md` §9.6,
 * plan decision D3) or drop seam (D-D2) can honour the public contract: fullscreen and pointer lock,
 * in both directions, and the `AcceptDrop` a handler's token spends on the offer a drag entry
 * presented — a `Now` action that never asks the browser for a primitive, whose whole effect is the
 * reducer's own `acceptDrop`. The window actions are not among them — a browser element has no
 * window to move or resize — and `OpenWindow` belongs to a later phase and another seam; the
 * admission refuses it before any native call, so it never reaches a browser API at all.
 */
internal fun interactionActionsForWeb(): Set<InteractionKind> = setOf(
    InteractionKind.EnterFullscreen,
    InteractionKind.ExitFullscreen,
    InteractionKind.LockPointer,
    InteractionKind.UnlockPointer,
    InteractionKind.AcceptDrop,
)

/**
 * Whether this target may perform [mode] as the pointer-lock action, or the failure it is refused
 * with.
 *
 * `Locked` is the one mode the Pointer Lock API offers — the pointer disappears and its movements
 * drive the surface directly — and it is the only mode the action takes. Every other mode is refused
 * with `InvalidRequest("action.mode")`, the field name `OPERATION-CONTRACTS.md` §1.1 registers, and
 * the refusal happens before any primitive call, so a refused mode has no browser effect at all.
 * `Confined` stays where phase 2 put it: the element's own pointer capture, taken through
 * `SurfaceUpdate.pointerCapture` for a pointer the surface observed pressed — an entirely different
 * mechanism, which is exactly why the two must not be conflated (plan decision D3,
 * `DESIGN.md:1943`).
 */
internal fun normaliseLockPointerMode(mode: PointerCaptureMode): KadreResult<Unit> = when (mode) {
    PointerCaptureMode.Locked -> KadreResult.Success(Unit)
    PointerCaptureMode.None, PointerCaptureMode.Confined ->
        KadreResult.Failure(KadreFailure.InvalidRequest("action.mode"))
}

/** The domain of the fullscreen primitives, as the port's failure and the refusal outcome name it. */
internal const val WEB_FULLSCREEN_DOMAIN: String = "fullscreen"

/** The domain of the pointer-lock primitives, as the port's failure and the refusal outcome name it. */
internal const val WEB_POINTER_LOCK_DOMAIN: String = "pointer-lock"

/**
 * The four terminal events of the browser primitives, as the DOM names them.
 *
 * They are plain `addEventListener` names — no binding declares them, and none is needed — but they
 * are the interaction seam's own vocabulary and both targets must speak it identically, which is why
 * they are written once here rather than as literals in each port. The change of each primitive is
 * the browser's committed answer; the error of each is its refusal, the one honest not-committed
 * answer the DOM exposes no reason for (plan decision D2).
 */

/** Fired when the browsing context's fullscreen element changed — on the element that entered, bubbling to the document, or on the document for an exit. */
internal const val WEB_FULLSCREEN_CHANGE_EVENT: String = "fullscreenchange"

/** Fired when a fullscreen request failed, on the document or on the failing element. */
internal const val WEB_FULLSCREEN_ERROR_EVENT: String = "fullscreenerror"

/** Fired when the document's pointer-lock element changed; the Pointer Lock API fires it on the document. */
internal const val WEB_POINTER_LOCK_CHANGE_EVENT: String = "pointerlockchange"

/** Fired when a pointer-lock request failed, on the document. */
internal const val WEB_POINTER_LOCK_ERROR_EVENT: String = "pointerlockerror"

/**
 * The one failure a browser refusal of a fullscreen or pointer-lock primitive produces.
 *
 * The DOM exposes no reason — a `fullscreenerror`, a `pointerlockerror` and a rejected promise all
 * arrive unexplained — so the code is the single honest `"refused"` (plan decision D2), named for the
 * [domain] that refused. An emission failure answers with the very same failure: from the outside,
 * "the browser would not have it" and "this port cannot even ask" are the same fact.
 */
internal fun refusalFailure(domain: String): KadreFailure =
    KadreFailure.PlatformFailure(KadrePlatform.Web, domain, "refused")

/**
 * One primitive emission awaiting the browser's terminal answer, settled exactly once.
 *
 * The DOM ports hook their terminal listeners onto the browser's own events, and the browser answers
 * each primitive on more than one channel at once — a refused `requestFullscreen` both fires a
 * `fullscreenerror` and rejects its promise — so the emission is the one-shot rule those channels
 * need: the first terminal answer wins, the listeners it took its answer from go with it, and every
 * later answer of the same primitive is nothing at all. A member that could not ask the browser at
 * all withdraws its emission instead ([abandon]): the listeners go, and no terminal fires, because
 * the surface completes the request synchronously with the failure and no pending exists behind it.
 *
 * The rule is DOM-free and shared by both ports, which is what keeps them from drifting on the one
 * behaviour a floating second answer would corrupt; the listeners themselves, and the teardown of
 * whatever they hooked onto the document, are each target's own.
 */
internal class WebPrimitiveEmission(
    private val terminal: WebPrimitiveTerminal,
) {
    private var settled: Boolean = false
    private val removals: MutableList<() -> Unit> = mutableListOf()

    /**
     * Registers the teardown of one terminal listener the emission installed, or runs it at once for
     * an emission that already settled — an answer that raced the installation removes itself.
     */
    fun addRemoval(removal: () -> Unit) {
        if (settled) {
            removal()
        } else {
            removals += removal
        }
    }

    /** The browser's terminal answer: the first one wins, and the terminal fires exactly once. */
    fun settle(committed: Boolean) {
        if (settled) return
        settled = true
        removeListeners()
        terminal.onTerminal(committed)
    }

    /** Withdraws the emission without an answer: the primitive was never asked of the browser. */
    fun abandon() {
        if (settled) return
        settled = true
        removeListeners()
    }

    private fun removeListeners() {
        removals.forEach { removal -> runCatching { removal() } }
        removals.clear()
    }
}
