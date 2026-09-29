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
 * promise is spelled out — and `webTest` pins the set against the post-attach snapshot. The four are
 * exactly the actions whose browser primitive and terminal callback can honour the public contract
 * (`DESIGN.md` §9.6, plan decision D3): fullscreen and pointer lock, in both directions. The window
 * actions are not among them — a browser element has no window to move or resize — and `AcceptDrop`
 * and `OpenWindow` belong to later phases and other seams; the admission refuses them before any
 * native call, so they never reach a browser API at all.
 */
internal fun interactionActionsForWeb(): Set<InteractionKind> = setOf(
    InteractionKind.EnterFullscreen,
    InteractionKind.ExitFullscreen,
    InteractionKind.LockPointer,
    InteractionKind.UnlockPointer,
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
 * The one failure a browser refusal of a fullscreen or pointer-lock primitive produces.
 *
 * The DOM exposes no reason — a `fullscreenerror`, a `pointerlockerror` and a rejected promise all
 * arrive unexplained — so the code is the single honest `"refused"` (plan decision D2), named for the
 * [domain] that refused. An emission failure answers with the very same failure: from the outside,
 * "the browser would not have it" and "this port cannot even ask" are the same fact.
 */
internal fun refusalFailure(domain: String): KadreFailure =
    KadreFailure.PlatformFailure(KadrePlatform.Web, domain, "refused")
