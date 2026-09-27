package org.graphiks.kadre.platform.web

import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.NamedKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.PointerCaptureMode

/**
 * The two surface-update fields this phase activates, as rules over the DOM-free observations: the
 * suppression decision of `SurfaceUpdate.inputDefaultBehavior`, and the ownership and the honourable
 * modes of `SurfaceUpdate.pointerCapture`.
 *
 * `PUBLIC-API-CATALOG.md:208` is the normative statement the suppression part implements:
 * `InputDefaultBehavior.SuppressWhenPossible` « demande au backend d'empêcher les actions natives
 * concurrentes associées aux événements livrés par Kadre, par exemple scroll/zoom browser. Il ne
 * signifie jamais que le consumer asynchrone a « handled » l'événement. Un backend incapable de
 * supprimer une catégorie la rejette partiellement avec `Unsupported(UpdateSurface)` et publie la
 * capability correspondante ; aucun changement global de page/application n'est implicite. »
 *
 * Three consequences of that text drive the shape below, and they are the reason the decision is a
 * function of *two* values rather than of a category alone:
 *
 * - **It is about categories, not about events.** Kadre suppresses a *kind* of browser default, only
 *   where it delivers the input that default would act on; what it suppresses is therefore enumerated
 *   once, as a closed set, instead of being decided per event.
 * - **It is never implicit.** Under `HostDefault` the answer is `false` for every category: the
 *   browser keeps its own behaviour until a consumer asks, in so many words, for its inhibition.
 * - **It is a subtraction from the page, so it stays small.** Nothing here can touch the document, the
 *   window or an ancestor: the decision is asked for, and applied to, the event the element itself was
 *   handed — see the two ports' `suppressDefaultFor`.
 *
 * Nothing in this file imports a DOM type, which is what makes both targets share the rule and what
 * lets `webTest` prove it without a browser.
 */

/**
 * The event categories this phase observes, named for the browser default each of them carries.
 *
 * A category is a fact about the *browser*, not about Kadre's model: it is what a browser would do by
 * itself while Kadre is delivering that observation, which is the only thing
 * `SuppressWhenPossible` is allowed to act on. Every observation of this phase belongs to exactly one
 * of these, and no category exists for an event the phase does not deliver — a default Kadre never
 * observes is a default Kadre has no reason, and no way, to touch.
 */
internal enum class WebInputCategory {
    /**
     * One wheel over the element.
     *
     * The reference case the catalog names: the browser's default for a wheel scrolls the nearest
     * scrollable ancestor (the document included) and, with a modifier, zooms the browsing context —
     * both of them page-level acts that would run *while* Kadre delivers the very same wheel as a
     * scroll. Nothing in the page asked for either, so a consumer that is about to move its own
     * content can ask for them to stop.
     */
    Wheel,

    /**
     * One key press of a key whose browser default scrolls the document or an ancestor of it.
     *
     * The same subtraction as [Wheel] for the other input device that moves a page: the arrows, the
     * page keys, `Home`, `End` and the space bar scroll the document when a scrollable ancestor holds
     * the focus or the element does not consume the key. Kadre delivers those presses as key
     * observations, so the two would act twice.
     */
    ScrollingKey,

    /**
     * Every other key observation: a press of a key with no document-scroll default, and every
     * release.
     *
     * Deliberately not suppressible, and each exclusion has a reason of its own:
     *
     * - **Releases.** The document moves on the press alone; a release carries no default to drop.
     * - **`Tab`.** Its default moves the focus. Trapping it would take the keyboard away from the
     *   user, which is a focus-management decision of a later surface (`HitTestingMode`, focus
     *   control) and not a page motion Kadre may silently cancel.
     * - **`Enter`, `Escape`, `Backspace`, the character keys.** Their defaults act inside the page's
     *   own content — submitting a form, dismissing a dialog, inserting text — which is neither a
     *   concurrent page motion nor an act this phase declares any capability over. Text input is
     *   `Unsupported` here, so its default is not Kadre's to take.
     * - **Function keys and the browser-chrome combinations** (`F5`, `F11`, `Ctrl`/`Cmd` + `S`/`P`/`L`).
     *   Their defaults belong to the browser's own user interface, not to the page. Most are not
     *   cancelable at all, and claiming them would be a capability that cannot be honoured.
     */
    Key,

    /**
     * One pointer observation: entry, motion, button transition or exit.
     *
     * Deliberately not suppressible. The defaults of a pointer event are the selection a drag starts,
     * the native drag-and-drop, the context menu a secondary press opens and the focus a press
     * acquires on the element — and the focus acquisition is the very thing D7 relies on: the host
     * makes the element focusable and the reduction observes the focus it gains. Cancelling a
     * `pointerdown` would therefore silence the surface's own focus observation to stop a selection
     * nobody asked to stop. Drag-and-drop, the native drag and the context menu are the capabilities
     * that stay `Unsupported` in this phase, and pointer capture is the one a later task installs from
     * the ownership it already has; none of them is a page motion this field may pre-empt.
     */
    Pointer,

    /**
     * The loss of activation the surface reduces from the lifecycle observation rather than from a
     * browser event.
     *
     * It is a category of its own so that the union has no unclassified member, and it is not
     * suppressible: the event behind it (`focusout`, `blur`, `visibilitychange`, `pagehide`) is not one
     * Kadre delivers as input, and none of its defaults is a concurrent page action.
     */
    Focus,
}

/**
 * The closed set of browser defaults `SuppressWhenPossible` drops: the documented answer to "which
 * category acts on the page while Kadre delivers it?".
 *
 * It is written out member by member rather than derived from an enum, and `webTest` pins it against
 * the whole category enum in both directions: extending it is a deliberate act that fails a test, and
 * a category added to the enum without a classification fails one too. That pinning is the point — a
 * suppression that grows by accident is a page behaving differently for no contract.
 *
 * Both members are the same *kind* of default (a page motion the browser performs on its own: the
 * scroll and zoom of a wheel, the document scroll of a key), and both are delivered by Kadre as input
 * of their own, which is what makes the suppression a way of not acting twice rather than a way of
 * hiding an event. Nothing else in this phase qualifies: see the exclusions documented on
 * [WebInputCategory.Key], [WebInputCategory.Pointer] and [WebInputCategory.Focus].
 */
internal val SUPPRESSED_INPUT_DEFAULTS: Set<WebInputCategory> = setOf(
    WebInputCategory.Wheel,
    WebInputCategory.ScrollingKey,
)

/**
 * The keys whose browser default scrolls the document, and the whole of the
 * [WebInputCategory.ScrollingKey] category.
 *
 * This is the closed list the plan requires, and it is deliberately short: the four arrows, the two
 * page keys, `Home`, `End` and the space bar are the keys whose page-level default is the motion of the
 * document itself. The lookup runs on the logical key rather than on the physical one, because the
 * browser's default is defined by the key the layout produced, not by where the key sits: the same
 * physical key is not a document-scroll key on every layout, and the logical key is what the browser
 * itself acts on.
 *
 * The trade is explicit and it is the consumer's to make: where the focus sits on a control that
 * consumes the key, the browser routes it there instead of scrolling, and dropping the default then
 * drops that activation too (`Space` on a focused button is the common case, and the arrow keys on a
 * focused slider). That is why the set stops at the keys whose *page-level* default is a scroll and
 * does not extend to "every key a page could act on": a consumer asks for `SuppressWhenPossible`
 * precisely so that its own handling of a delivered key is not doubled by the browser's, and it asks
 * for it for these keys and no others.
 *
 * Every other named key is excluded, and the reason is per key rather than per list: `Enter`, `Tab`,
 * `Escape`, `Backspace`, `Delete`, `Insert`, the function keys, the toggles (`CapsLock`, `NumLock`), the
 * media and volume keys and the context-menu key all have defaults that act inside the page's content,
 * inside the browser's own user interface, or on the system — none of them moves the document, so none
 * of them is a concurrent action Kadre would be doubling. `webTest` walks `NamedKey.entries` and fails
 * if this list ever disagrees with the category it derives.
 */
private val WEB_DOCUMENT_SCROLL_KEYS: Set<NamedKey> = setOf(
    NamedKey.ArrowUp,
    NamedKey.ArrowDown,
    NamedKey.ArrowLeft,
    NamedKey.ArrowRight,
    NamedKey.PageUp,
    NamedKey.PageDown,
    NamedKey.Home,
    NamedKey.End,
    NamedKey.Space,
)

/**
 * The category of one observation, which is what the decision reads.
 *
 * The derivation belongs next to the decision rather than in a port: it is a rule about Kadre's model
 * (which of its own observations carries a suppressible browser default), it is the same on both
 * targets, and keeping it here is what lets the two ports ask a single question — "may I drop this
 * event's default?" — without either of them knowing anything about categories, keys or behaviours.
 *
 * A wheel is [WebInputCategory.Wheel] whatever it carries; only a *press* of a document-scroll key is
 * [WebInputCategory.ScrollingKey], since a release moves nothing; every other key observation is
 * [WebInputCategory.Key]; every pointer observation is [WebInputCategory.Pointer]; and the loss of
 * activation is [WebInputCategory.Focus].
 */
internal fun webInputCategory(stimulus: WebInputStimulus): WebInputCategory = when (stimulus) {
    is WebInputStimulus.Scrolled -> WebInputCategory.Wheel

    is WebInputStimulus.KeyChanged -> if (
        stimulus.keyState == KeyState.Pressed &&
        stimulus.logicalKey is LogicalKey.Named &&
        stimulus.logicalKey.value in WEB_DOCUMENT_SCROLL_KEYS
    ) {
        WebInputCategory.ScrollingKey
    } else {
        WebInputCategory.Key
    }

    is WebInputStimulus.PointerEntered,
    is WebInputStimulus.PointerMoved,
    is WebInputStimulus.PointerButtonChanged,
    is WebInputStimulus.PointerLeft,
    -> WebInputCategory.Pointer

    WebInputStimulus.FocusLost -> WebInputCategory.Focus
}

/**
 * The one decision: may the browser default of an event of [category] be dropped under [behaviour]?
 *
 * Total by construction. `HostDefault` answers `false` for every category — that is the phase's exit
 * gate (« le navigateur conserve son comportement par défaut tant que la policy ne demande pas
 * explicitement son inhibition »), and it is written as its own branch rather than as a negation so
 * that a third behaviour would have to be classified here before the file compiled. `SuppressWhenPossible`
 * answers `true` for the closed set and `false` for everything else, so a category Kadre does not
 * suppress behaves exactly as it does under the default.
 *
 * The function reads nothing but its two arguments: the caller supplies the category of the event in
 * hand and the behaviour currently in effect on the surface, which keeps the decision pure, provable
 * without a browser, and independent of when it is asked.
 */
internal fun shouldSuppress(category: WebInputCategory, behaviour: InputDefaultBehavior): Boolean =
    when (behaviour) {
        InputDefaultBehavior.HostDefault -> false
        InputDefaultBehavior.SuppressWhenPossible -> category in SUPPRESSED_INPUT_DEFAULTS
    }

/**
 * The modes of `SurfaceUpdate.pointerCapture` this backend can honour, written out as the rule it is.
 *
 * `None` and `Confined` are the two the DOM can be asked for: a capture the element holds, and no
 * capture at all. `Locked` is deliberately outside, and it is not a gap of this phase but its scope —
 * the Pointer Lock API needs a transient user activation, it belongs to `InteractionAction.LockPointer`
 * (`DESIGN.md` §9.6), and no member of this target asks the browser to lock a pointer.
 *
 * The rule is a function rather than a set held next to the capability because two places have to agree
 * on it: the capability `webSurfaceCapabilities()` publishes, and the commit of [WebHostSurface.apply],
 * which answers a mode it cannot honour with the same `Unsupported(UpdateSurface)` the shared admission
 * helper produces. Written as an exhaustive `when` over the enum, a fourth mode cannot be added without
 * classifying it here, and `webTest` pins this rule and the capability's constraint set against
 * `PointerCaptureMode.entries` in both directions.
 */
internal fun webPointerCaptureIsHonourable(mode: PointerCaptureMode): Boolean = when (mode) {
    PointerCaptureMode.None, PointerCaptureMode.Confined -> true
    PointerCaptureMode.Locked -> false
}

/**
 * Tracks the pointer the surface currently owns: one the element observed pressed and has not seen
 * released.
 *
 * Ownership is the whole of the rule D13 gives `pointerCapture`: a capture may be taken only for a
 * pointer the surface holds, so the state this class keeps is what a `Confined` request is admitted on
 * and what a browser call is guarded by. It is not part of `SurfaceState` — the public model has no
 * such field and this is not a claim about the browser, it is what the surface observed of it.
 *
 * The rule is stated over the *buttons*, not over the pointer, because the DOM reports a transition per
 * button: a release of one button among several does not release the pointer, which is why a set of
 * pressed buttons is kept and ownership ends with the last of them. Every other ending of a pointer is
 * one of the two remaining stimuli: the exit the ports deliver for a `pointerleave` and for a
 * `pointercancel` (`WebInputStimulus.PointerLeft`), and the loss of activation the surface derives from
 * its lifecycle reduction (`WebInputStimulus.FocusLost`), both of which reconcile the pointer — and its
 * buttons — away, exactly as the shared reducer does.
 *
 * The reading lives in `webMain` on the DOM-free union, like every other rule of this file, so the two
 * target ports cannot derive ownership differently: a port hands its observations over, and this is what
 * the surface makes of them.
 */
internal class WebPointerOwnership {
    private val pressedButtons: MutableSet<PointerButton> = mutableSetOf()

    /** Whether the surface holds a pointer it may take a capture for. */
    val isOwned: Boolean get() = pressedButtons.isNotEmpty()

    /** Records one observation, which is the only thing that can begin or end the ownership. */
    fun observe(stimulus: WebInputStimulus) {
        when (stimulus) {
            is WebInputStimulus.PointerButtonChanged -> when (stimulus.buttonState) {
                PointerButtonState.Pressed -> pressedButtons.add(stimulus.button)
                PointerButtonState.Released -> pressedButtons.remove(stimulus.button)
            }

            // The pointer is gone from the element, cancelled or left, so nothing of it is held any more.
            is WebInputStimulus.PointerLeft -> clear()

            // A loss of activation neutralises the input snapshot, pointer and buttons included: a
            // pointer the model no longer has cannot be one this surface holds.
            WebInputStimulus.FocusLost -> clear()

            // An entry, a motion and a scroll state nothing about a button: they move a pointer the
            // surface already holds, or none at all, and neither begins nor ends the ownership.
            is WebInputStimulus.PointerEntered,
            is WebInputStimulus.PointerMoved,
            is WebInputStimulus.Scrolled,
            is WebInputStimulus.KeyChanged,
            -> Unit
        }
    }

    /** Forgets the pointer: the surface stopped admitting, or lost the one it held. */
    fun clear() {
        pressedButtons.clear()
    }
}
