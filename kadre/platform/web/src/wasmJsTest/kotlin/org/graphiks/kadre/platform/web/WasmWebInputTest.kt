@file:OptIn(ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.platform.web

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.ModifierKey
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.input.ScrollDelta
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalPoint
import org.w3c.dom.HTMLElement
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Wasm DOM port's input listeners, driven by real browser events on a real element.
 *
 * This is the Wasm twin of `JsWebInputTest`, with the same cases and the same names so the two can be
 * read side by side: a browser event is read, copied through the shared mapping core of
 * `WebInputMapping.kt`, and delivered as an immutable, DOM-free [WebInputStimulus]; a pointer kind this
 * phase does not deliver produces no stimulus at all; and `release` leaves neither a delivering
 * listener nor an observed resource behind.
 *
 * Only the interop of this file differs from the JS one — the events are built and the listeners are
 * watched through `@JsFun` snippets instead of `js(…)` ones, because Kotlin/Wasm sees neither a Kotlin
 * function value nor a JS object in a raw snippet. Nothing asserted here is Wasm-specific except
 * [theModifierTogglesAndThePenMembersAreReadThroughWasmInterop], which exists because a recipe that
 * *compiles* proves nothing about Wasm interop: that case makes the two reads a compile probe cannot
 * prove — `getModifierState` answering true and the four pen members carrying their values — a
 * browser-observed fact.
 */
class WasmWebInputTest {
    @Test
    fun aConformingKeyboardEventBecomesTheKeyObservationItDescribes() {
        val harness = WasmInputHarness()
        try {
            dispatchKey(harness.element, type = "keydown", code = "KeyA", key = "a", location = 1, shiftKey = true, ctrlKey = true)

            assertEquals(
                listOf<WebInputStimulus>(
                    WebInputStimulus.KeyChanged(
                        physicalKey = PhysicalKey.Code(usagePage = 0x07, usageId = 0x04),
                        logicalKey = LogicalKey.Character("a"),
                        location = KeyLocation.Left,
                        keyState = KeyState.Pressed,
                        repeat = false,
                        modifiers = KeyboardModifiers(setOf(ModifierKey.Shift, ModifierKey.Control)),
                    ),
                ),
                harness.delivered.toList(),
                "the physical key, the logical key, the location, the state and the modifiers are the event's own",
            )

            // The union refuses a repeating release — a native auto-repeat only ever reports presses —
            // so the port preserves that invariant instead of dropping the observation: the release is
            // delivered with its repeat flag cleared, and a repeating press keeps its own.
            dispatchKey(harness.element, type = "keyup", code = "KeyA", key = "a", location = 1, repeat = true)
            dispatchKey(harness.element, type = "keydown", code = "KeyA", key = "a", repeat = true)

            assertEquals(
                WebInputStimulus.KeyChanged(
                    physicalKey = PhysicalKey.Code(usagePage = 0x07, usageId = 0x04),
                    logicalKey = LogicalKey.Character("a"),
                    location = KeyLocation.Left,
                    keyState = KeyState.Released,
                    repeat = false,
                    modifiers = KeyboardModifiers(emptySet()),
                ),
                harness.delivered[1],
                "a release is never a repeat, and its modifiers are the ones that event reported",
            )
            assertEquals(
                true,
                (harness.delivered[2] as WebInputStimulus.KeyChanged).repeat,
                "the auto-repeat the browser reported on a press is preserved",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun aMousePointerBecomesEnterMoveButtonAndExitObservationsOfItsOwnKind() {
        val harness = WasmInputHarness()
        try {
            val origin = harness.elementOrigin()
            dispatchPointer(harness.element, "pointerenter", "mouse", clientX = origin.x + 30.0, clientY = origin.y + 40.0)
            dispatchPointer(
                harness.element,
                "pointermove",
                "mouse",
                clientX = origin.x + 35.0,
                clientY = origin.y + 44.0,
                pressure = 0.5,
            )
            dispatchPointer(
                harness.element,
                "pointerdown",
                "mouse",
                clientX = origin.x + 35.0,
                clientY = origin.y + 44.0,
                button = 0,
                buttons = 1,
                pressure = 0.5,
            )
            dispatchPointer(
                harness.element,
                "pointerup",
                "mouse",
                clientX = origin.x + 36.0,
                clientY = origin.y + 45.0,
                button = 0,
                buttons = 0,
            )
            dispatchPointer(harness.element, "pointerleave", "mouse", clientX = origin.x + 36.0, clientY = origin.y + 45.0)

            assertEquals(
                listOf<WebInputStimulus>(
                    WebInputStimulus.PointerEntered(position = LogicalPoint(30.0, 40.0), kind = PointerKind.Mouse),
                    WebInputStimulus.PointerMoved(
                        position = LogicalPoint(35.0, 44.0),
                        delta = LogicalDelta(5.0, 4.0),
                        pressure = 0.5,
                        kind = PointerKind.Mouse,
                        pen = null,
                    ),
                    WebInputStimulus.PointerButtonChanged(
                        button = PointerButton.Primary,
                        buttonState = PointerButtonState.Pressed,
                        position = LogicalPoint(35.0, 44.0),
                        pressure = 0.5,
                        kind = PointerKind.Mouse,
                        pen = null,
                    ),
                    WebInputStimulus.PointerButtonChanged(
                        button = PointerButton.Primary,
                        buttonState = PointerButtonState.Released,
                        position = LogicalPoint(36.0, 45.0),
                        pressure = 0.0,
                        kind = PointerKind.Mouse,
                        pen = null,
                    ),
                    WebInputStimulus.PointerLeft(kind = PointerKind.Mouse),
                ),
                harness.delivered.toList(),
                "the positions are the surface's own, the motion is the one between two observations, and " +
                    "a mouse carries no pen state",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun aPenPointerIsDeliveredAsAPenWithThePenStateTheBrowserReported() {
        val harness = WasmInputHarness()
        try {
            val origin = harness.elementOrigin()
            dispatchPointer(harness.element, "pointerenter", "pen", clientX = origin.x + 10.0, clientY = origin.y + 12.0)
            dispatchPointer(
                harness.element,
                "pointermove",
                "pen",
                clientX = origin.x + 14.0,
                clientY = origin.y + 16.0,
                pressure = 0.5,
                tiltX = 12,
                tiltY = -30,
                twist = 90,
                tangentialPressure = 0.25,
            )

            assertEquals(
                WebInputStimulus.PointerEntered(position = LogicalPoint(10.0, 12.0), kind = PointerKind.Pen),
                harness.delivered[0],
                "a pen is a pen from its first observation, never a mouse",
            )
            val moved = harness.delivered[1] as WebInputStimulus.PointerMoved
            assertEquals(PointerKind.Pen, moved.kind)
            assertEquals(LogicalPoint(14.0, 16.0), moved.position)
            assertEquals(LogicalDelta(4.0, 4.0), moved.delta)
            assertEquals(0.5, moved.pressure, "the pointer pressure travels with the pointer, as for any kind")
            val pen = moved.pen ?: error("a pen observation carries the pen state the browser reported")
            assertEquals(12.0, pen.tiltXDegrees)
            assertEquals(-30.0, pen.tiltYDegrees)
            assertEquals(PI / 2.0, pen.twistRadians ?: 0.0, 1e-12, "the twist is carried in radians")
            assertEquals(0.25, pen.tangentialPressure)
            // The state the model accepts is the proof the domains hold: `PenState` validates them on
            // construction, so a carried state is inside ±90 degrees, `[0, 2π)` and `[-1, 1]`.
            assertTrue(pen.tiltXDegrees!! in -90.0..90.0)
            assertTrue(pen.tiltYDegrees!! in -90.0..90.0)
            assertTrue(pen.twistRadians!! >= 0.0 && pen.twistRadians!! < 2.0 * PI)
            assertTrue(pen.tangentialPressure!! in -1.0..1.0)
        } finally {
            harness.close()
        }
    }

    @Test
    fun aTouchPointerProducesNoStimulusAtAll() {
        val harness = WasmInputHarness()
        try {
            val origin = harness.elementOrigin()
            listOf("pointerenter", "pointermove", "pointerdown", "pointerup", "pointerleave", "pointercancel")
                .forEach { type ->
                    dispatchPointer(
                        harness.element,
                        type,
                        "touch",
                        clientX = origin.x + 7.0,
                        clientY = origin.y + 7.0,
                        button = 0,
                        buttons = 1,
                        pressure = 0.5,
                    )
                }

            assertTrue(
                harness.delivered.isEmpty(),
                "touch is deferred to the phase that installs its observers (D12), so nothing may be " +
                    "delivered for it, not even a pointer of another kind: ${harness.delivered}",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun aPixelWheelIsDeliveredWithTheSignTheBrowserAndKadreBothUse() {
        val harness = WasmInputHarness()
        try {
            dispatchWheel(harness.element, deltaMode = 0, deltaX = 12.5, deltaY = -40.0)

            assertEquals(
                listOf<ScrollDelta>(ScrollDelta.Logical(12.5, -40.0)),
                harness.delivered.filterIsInstance<WebInputStimulus.Scrolled>().map { it.delta },
                "a wheel reports positive down and right, which is Kadre's own orientation: no sign is flipped",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun aLineWheelIsDeliveredInLines() {
        val harness = WasmInputHarness()
        try {
            dispatchWheel(harness.element, deltaMode = 1, deltaX = 0.0, deltaY = 3.0)

            assertEquals(
                listOf<ScrollDelta>(ScrollDelta.Lines(0.0, 3.0)),
                harness.delivered.filterIsInstance<WebInputStimulus.Scrolled>().map { it.delta },
                "the browser's own unit is carried, never converted to pixels",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun aPageWheelProducesNoStimulus() {
        val harness = WasmInputHarness()
        try {
            dispatchWheel(harness.element, deltaMode = 2, deltaX = 0.0, deltaY = 1.0)

            assertTrue(
                harness.delivered.isEmpty(),
                "DOM_DELTA_PAGE is a declared non-normalisable variant: it is dropped, never converted " +
                    "with a page size Kadre does not own (D9): ${harness.delivered}",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun theScrollFrontierSeparatesFramesUnitsAndButtonStates() = runTest {
        val harness = WasmInputHarness()
        try {
            // The rule itself is proven without a browser in `WebInputTrackingTest`, over the shared
            // [WebScrollBoundary]; what this case proves is the reading this target feeds it: the
            // wheels of one real frame share a frontier, and a real animation frame — observed by the
            // port's own registration — is what makes the next wheel open one.
            dispatchWheel(harness.element, deltaMode = 0, deltaX = 0.0, deltaY = 1.0)
            dispatchWheel(harness.element, deltaMode = 0, deltaX = 0.0, deltaY = 2.0)
            dispatchWheel(harness.element, deltaMode = 0, deltaX = 0.0, deltaY = 3.0, buttons = 1)
            dispatchWheel(harness.element, deltaMode = 1, deltaX = 0.0, deltaY = 1.0, buttons = 1)
            val withinOneFrame = harness.frontiers()
            awaitAnimationFrame()
            dispatchWheel(harness.element, deltaMode = 0, deltaX = 0.0, deltaY = 4.0)
            val afterANewFrame = harness.frontiers()

            assertEquals(5, afterANewFrame.size)
            assertEquals(
                withinOneFrame[0],
                withinOneFrame[1],
                "two wheels the browser delivered in one frame may merge: they share their frontier",
            )
            assertTrue(
                withinOneFrame[2] > withinOneFrame[1],
                "a change of the pointer's button state opens a frontier of its own",
            )
            assertTrue(
                withinOneFrame[3] > withinOneFrame[2],
                "a change of the browser's unit opens a frontier of its own",
            )
            assertTrue(
                afterANewFrame[4] > withinOneFrame[3],
                "the first wheel a new animation frame delivers opens a frontier of its own",
            )
        } finally {
            harness.close()
        }
    }

    /**
     * A refused observation is refused whole: it may produce no stimulus *and* may leave nothing of
     * the pointer the port does deliver behind.
     *
     * A touch contact reaches the element like any other pointer, so its exit is dispatched to the
     * same listener a mouse's exit is. The mouse is mid-motion here on purpose: had the refusal
     * happened after the shared motion forgot its position, the mouse's next motion would report no
     * movement at all, which is a stimulus built from another pointer's event.
     */
    @Test
    fun aRefusedTouchExitLeavesTheMouseMotionItDoesNotBelongTo() {
        val harness = WasmInputHarness()
        try {
            val origin = harness.elementOrigin()
            dispatchPointer(harness.element, "pointerenter", "mouse", clientX = origin.x + 10.0, clientY = origin.y + 10.0)
            dispatchPointer(harness.element, "pointermove", "mouse", clientX = origin.x + 12.0, clientY = origin.y + 14.0)
            dispatchPointer(harness.element, "pointerleave", "touch", clientX = origin.x + 99.0, clientY = origin.y + 99.0)
            dispatchPointer(harness.element, "pointermove", "mouse", clientX = origin.x + 15.0, clientY = origin.y + 18.0)

            assertEquals(
                listOf<WebInputStimulus>(
                    WebInputStimulus.PointerEntered(position = LogicalPoint(10.0, 10.0), kind = PointerKind.Mouse),
                    WebInputStimulus.PointerMoved(
                        position = LogicalPoint(12.0, 14.0),
                        delta = LogicalDelta(2.0, 4.0),
                        pressure = 0.0,
                        kind = PointerKind.Mouse,
                        pen = null,
                    ),
                    WebInputStimulus.PointerMoved(
                        position = LogicalPoint(15.0, 18.0),
                        delta = LogicalDelta(3.0, 4.0),
                        pressure = 0.0,
                        kind = PointerKind.Mouse,
                        pen = null,
                    ),
                ),
                harness.delivered.toList(),
                "a touch exit delivers nothing and must not move the mouse's own motion: the last mouse " +
                    "motion measures from the mouse's previous position",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun aCancelledPointerIsReconciledLikeAPointerThatLeft() {
        val harness = WasmInputHarness()
        try {
            val origin = harness.elementOrigin()
            dispatchPointer(harness.element, "pointerenter", "mouse", clientX = origin.x + 5.0, clientY = origin.y + 6.0)
            dispatchPointer(
                harness.element,
                "pointercancel",
                "mouse",
                clientX = origin.x + 5.0,
                clientY = origin.y + 6.0,
                button = 0,
                buttons = 1,
            )

            assertEquals(
                listOf<WebInputStimulus>(
                    WebInputStimulus.PointerEntered(position = LogicalPoint(5.0, 6.0), kind = PointerKind.Mouse),
                    WebInputStimulus.PointerLeft(kind = PointerKind.Mouse),
                ),
                harness.delivered.toList(),
                "a cancelled contact is dropped with everything it held, exactly like one that left",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun releaseRemovesEveryListenerItRegisteredAndNothingIsDeliveredAfterwards() {
        val harness = WasmInputHarness(observeRegistrations = true)
        try {
            val origin = harness.elementOrigin()
            dispatchKey(harness.element, type = "keydown", code = "KeyA", key = "a")
            dispatchPointer(harness.element, "pointerenter", "mouse", clientX = origin.x + 1.0, clientY = origin.y + 1.0)
            dispatchPointer(harness.element, "pointermove", "mouse", clientX = origin.x + 2.0, clientY = origin.y + 2.0)
            dispatchPointer(harness.element, "pointerdown", "mouse", clientX = origin.x + 2.0, clientY = origin.y + 2.0, button = 0, buttons = 1)
            dispatchPointer(harness.element, "pointerup", "mouse", clientX = origin.x + 2.0, clientY = origin.y + 2.0, button = 0)
            dispatchPointer(harness.element, "pointerleave", "mouse", clientX = origin.x + 3.0, clientY = origin.y + 3.0)
            dispatchPointer(harness.element, "pointercancel", "mouse", clientX = origin.x + 3.0, clientY = origin.y + 3.0)
            dispatchWheel(harness.element, deltaMode = 0, deltaX = 0.0, deltaY = 1.0)
            dispatchKey(harness.element, type = "keyup", code = "KeyA", key = "a")
            val observedWhileAttached = harness.delivered.toList()
            assertTrue(observedWhileAttached.isNotEmpty(), "every listener under test delivered something")

            harness.close()

            dispatchKey(harness.element, type = "keydown", code = "KeyA", key = "a")
            dispatchPointer(harness.element, "pointermove", "mouse", clientX = origin.x + 9.0, clientY = origin.y + 9.0)
            dispatchWheel(harness.element, deltaMode = 0, deltaX = 0.0, deltaY = 9.0)
            dispatchPointer(harness.element, "pointercancel", "mouse", clientX = origin.x + 9.0, clientY = origin.y + 9.0)

            assertEquals(
                observedWhileAttached,
                harness.delivered.toList(),
                "a released port delivers nothing, whatever the browser dispatches afterwards",
            )
            assertEquals(
                emptyList(),
                listenerTypes(harness.element, withoutRemoval = true),
                "every listener that was registered was removed with the same reference",
            )
            assertEquals(
                setOf(
                    // The input observer's own listeners, and nothing on the document or the window.
                    // `lostpointercapture` is the one the capture seam needs: it is what tells the
                    // surface a committed capture has ended, and it is registered and removed with the
                    // other input listeners.
                    "keydown",
                    "keyup",
                    "pointerenter",
                    "pointermove",
                    "pointerdown",
                    "pointerup",
                    "pointerleave",
                    "pointercancel",
                    "lostpointercapture",
                    "wheel",
                    // The lifecycle observer's element listeners are the only other registrations, and
                    // a focus loss adds none: the surface publishes the one reset that loss owes.
                    "focusin",
                    "focusout",
                ),
                listenerTypes(harness.element).toSet(),
                "the port registers exactly the element listeners of the phase",
            )
            assertTrue(
                wheelListenerIsNonPassive(harness.element),
                "wheel is registered with an explicit `passive: false`: a passive listener could never " +
                    "suppress the browser's default, which is what the surface decides later",
            )
            // Stricter than the JS ledger, which watches the element alone: the browsing context and
            // the document carry no delivering listener at all, and what the port did register on them
            // — the lifecycle listeners — was removed by the same reference too.
            assertEquals(
                emptyList(),
                listenerTypes(window, withoutRemoval = true),
                "every listener the port registered on the browsing context was removed",
            )
            assertEquals(
                emptyList(),
                listenerTypes(document, withoutRemoval = true),
                "every listener the port registered on the document was removed",
            )
            assertEquals(
                emptyList(),
                (listenerTypes(window) + listenerTypes(document)).filter { it in inputEventTypes },
                "the port registers no input listener on the browsing context or the document: a global " +
                    "listener would observe input the surface does not own",
            )
        } finally {
            harness.close()
        }
    }

    /**
     * The two reads a compile probe cannot prove are reached by this target's interop.
     *
     * Phase 2 of this project proved that a recipe which *compiles* proves nothing about Wasm interop:
     * `JsReference.get()` is a compiler intrinsic rather than a JavaScript member, so a port can
     * compile and still fail at runtime. The same trap sits under the two reads this case forces:
     * `getModifierState` — which every keyboard case exercises, but always with the same answer, so a
     * read that silently reported `false` would be invisible — and the four pen members of a
     * `PointerEvent`, whose values a silently unreadable member would leave at their defaults.
     *
     * So both are read back where the port's own delivery is what is observed: a keyboard event whose
     * init dictionary reports the two toggles on, and a pen motion whose four members carry values that
     * no default would produce. The pen half is asserted by
     * [aPenPointerIsDeliveredAsAPenWithThePenStateTheBrowserReported] as well; it is repeated here so
     * that the two interop-trapped reads are in one place to read.
     */
    @Test
    fun theModifierTogglesAndThePenMembersAreReadThroughWasmInterop() {
        val harness = WasmInputHarness()
        try {
            val origin = harness.elementOrigin()
            dispatchKey(
                harness.element,
                type = "keydown",
                code = "CapsLock",
                key = "CapsLock",
                capsLock = true,
                numLock = true,
            )
            dispatchPointer(
                harness.element,
                "pointerenter",
                "pen",
                clientX = origin.x + 20.0,
                clientY = origin.y + 21.0,
            )
            dispatchPointer(
                harness.element,
                "pointermove",
                "pen",
                clientX = origin.x + 22.0,
                clientY = origin.y + 23.0,
                tiltX = -45,
                tiltY = 60,
                twist = 270,
                tangentialPressure = -0.5,
            )

            assertEquals(
                KeyboardModifiers(setOf(ModifierKey.CapsLock, ModifierKey.NumLock)),
                (harness.delivered[0] as WebInputStimulus.KeyChanged).modifiers,
                "getModifierState answered true for both toggles this event reported: a read that could " +
                    "not be reached from Wasm would have thrown inside the port's own guard and dropped " +
                    "the observation entirely",
            )
            val pen = (harness.delivered[2] as WebInputStimulus.PointerMoved).pen
                ?: error("a pen motion carries the pen state its four members describe")
            assertEquals(-45.0, pen.tiltXDegrees)
            assertEquals(60.0, pen.tiltYDegrees)
            assertEquals(3.0 * PI / 2.0, pen.twistRadians ?: 0.0, 1e-12, "the twist is carried in radians")
            assertEquals(-0.5, pen.tangentialPressure)
        } finally {
            harness.close()
        }
    }

    /**
     * Waits for the browser's next animation frame.
     *
     * The frame the port observes for the scroll frontier is the rendering step of the real event
     * loop, so the wait has to leave the virtual clock of [runTest] behind.
     */
    private suspend fun awaitAnimationFrame() {
        withContext(Dispatchers.Default) {
            val frame = CompletableDeferred<Unit>()
            window.requestAnimationFrame { frame.complete(Unit) }
            frame.await()
        }
    }
}

/**
 * One element of the real page and the port that observes it, with every stimulus the port delivered.
 *
 * The element is laid out by the browser rather than attached to nothing, so the coordinates a case
 * dispatches are viewport coordinates while the positions the port delivers are the element's own:
 * that difference is what makes the surface-relative position a proven fact instead of an assertion
 * about numbers that would hold either way.
 */
private class WasmInputHarness(observeRegistrations: Boolean = false) {
    val element: HTMLElement = (document.createElement("div") as HTMLElement).also {
        it.style.position = "absolute"
        it.style.left = "80px"
        it.style.top = "60px"
        it.style.width = "320px"
        it.style.height = "180px"
        document.body!!.appendChild(it)
    }

    /** Every stimulus the port delivered, in the order the browser reported the events. */
    val delivered: MutableList<WebInputStimulus> = mutableListOf()

    private val port: WasmWebDomPort = WasmWebDomPort(element)
    private var released: Boolean = false

    init {
        if (observeRegistrations) {
            // The ledger watches the three targets the port can register a listener on: the element,
            // and the browsing context and document the lifecycle observer uses. Watching the two
            // globals is what makes "no delivering listener outside the element" a proven fact.
            recordListenerRegistrations(element)
            recordListenerRegistrations(window)
            recordListenerRegistrations(document)
        }
        port.installLifecycleObserver { }
        port.installInputObserver { delivered += it }
    }

    /** Releases the port and takes the element out of the page. Idempotent, so a `finally` can call it. */
    fun close() {
        if (released) return
        released = true
        port.release()
        element.remove()
    }

    /** The viewport position the element's own origin sits at, read back from the browser. */
    fun elementOrigin(): LogicalPoint {
        val box = element.getBoundingClientRect()
        val origin = LogicalPoint(box.left + element.clientLeft, box.top + element.clientTop)
        assertTrue(
            origin.x != 0.0 && origin.y != 0.0,
            "the element must sit away from the viewport origin for a surface-relative position to be " +
                "distinguishable from a viewport one: $origin",
        )
        return origin
    }

    /** The coalescing frontier of every delivered scroll, in delivery order. */
    fun frontiers(): List<Long> = delivered.filterIsInstance<WebInputStimulus.Scrolled>().map { it.coalescingBoundary }
}

/** The event types the input observer owns: a listener of one of them outside the element would be one. */
private val inputEventTypes: List<String> = listOf(
    "keydown",
    "keyup",
    "pointerenter",
    "pointermove",
    "pointerdown",
    "pointerup",
    "pointerleave",
    "pointercancel",
    "wheel",
)

/** Dispatches a real keyboard event of [type] on [element], as a browser delivers one. */
private fun dispatchKey(
    element: HTMLElement,
    type: String,
    code: String,
    key: String,
    location: Int = 0,
    repeat: Boolean = false,
    shiftKey: Boolean = false,
    ctrlKey: Boolean = false,
    altKey: Boolean = false,
    metaKey: Boolean = false,
    capsLock: Boolean = false,
    numLock: Boolean = false,
): Unit = dispatchWasmKey(
    element = element,
    type = type,
    code = code,
    key = key,
    location = location,
    repeat = repeat,
    shiftKey = shiftKey,
    ctrlKey = ctrlKey,
    altKey = altKey,
    metaKey = metaKey,
    capsLock = capsLock,
    numLock = numLock,
)

/** Dispatches a real pointer event of [type] on [element], as a browser delivers one. */
private fun dispatchPointer(
    element: HTMLElement,
    type: String,
    pointerType: String,
    clientX: Double,
    clientY: Double,
    button: Int = -1,
    buttons: Int = 0,
    pressure: Double = 0.0,
    tiltX: Int = 0,
    tiltY: Int = 0,
    twist: Int = 0,
    tangentialPressure: Double = 0.0,
): Unit = dispatchWasmPointer(
    element = element,
    type = type,
    pointerType = pointerType,
    clientX = clientX,
    clientY = clientY,
    button = button,
    buttons = buttons,
    pressure = pressure,
    tiltX = tiltX,
    tiltY = tiltY,
    twist = twist,
    tangentialPressure = tangentialPressure,
)

/** Dispatches a real wheel event on [element], as a browser delivers one. */
private fun dispatchWheel(
    element: HTMLElement,
    deltaMode: Int,
    deltaX: Double,
    deltaY: Double,
    buttons: Int = 0,
): Unit = dispatchWasmWheel(element, deltaMode, deltaX, deltaY, buttons)

/**
 * The event types a listener was registered for on [target], or — when [withoutRemoval] — the
 * registered listeners [target] was never asked to remove, as their event types.
 */
private fun listenerTypes(target: JsAny, withoutRemoval: Boolean = false): List<String> {
    val joined = if (withoutRemoval) {
        wasmListenersWithoutRemoval(target)
    } else {
        wasmRegisteredListenerTypes(target)
    }
    return joined.split(",").filter { it.isNotEmpty() }
}

/**
 * Records every listener registration of [target], with the reference that was registered and the
 * `passive` option it was registered with.
 *
 * A listener that survives `release` is invisible from the outside — a removed listener and a leaked
 * one both keep quiet once the port stopped delivering — so the only honest proof of removal is to
 * watch the registration calls themselves. The patch is installed before the port exists.
 *
 * The events are built and the listeners are watched through `@JsFun` because Kotlin/Wasm sees
 * neither a JS object nor a Kotlin function value from a raw snippet: the snippet is the only place
 * the two interop languages meet.
 */
@JsFun(
    """(target) => {
         var log = { added: [], removed: [] };
         target.__kadreTestListenerLog = log;
         var add = target.addEventListener.bind(target);
         var remove = target.removeEventListener.bind(target);
         target.addEventListener = function (type, listener, options) {
           log.added.push([type, listener, options === undefined || options === null ? undefined : options.passive]);
           return add(type, listener, options);
         };
         target.removeEventListener = function (type, listener, options) {
           log.removed.push([type, listener]);
           return remove(type, listener, options);
         };
       }""",
)
private external fun recordListenerRegistrations(target: JsAny)

/** The event types a listener was registered for on [target], comma-separated. */
@JsFun(
    """(target) => target.__kadreTestListenerLog.added
         .map(function (entry) { return entry[0]; })
         .join(",")""",
)
private external fun wasmRegisteredListenerTypes(target: JsAny): String

/** The registered listeners [target] was never asked to remove, as a comma-separated list of types. */
@JsFun(
    """(target) => {
         var log = target.__kadreTestListenerLog;
         return log.added
           .filter(function (entry) {
             return !log.removed.some(function (removal) {
               return removal[0] === entry[0] && removal[1] === entry[1];
             });
           })
           .map(function (entry) { return entry[0]; })
           .join(",");
       }""",
)
private external fun wasmListenersWithoutRemoval(target: JsAny): String

/** Whether the `wheel` listener of [target] was registered with an explicit `passive: false`. */
@JsFun(
    """(target) => {
         var entry = target.__kadreTestListenerLog.added.filter(function (added) {
           return added[0] === "wheel";
         })[0];
         return entry !== undefined && entry[2] === false;
       }""",
)
private external fun wheelListenerIsNonPassive(target: JsAny): Boolean

/** Dispatches a real keyboard event on [element], as a browser delivers one. */
@JsFun(
    """(element, type, code, key, location, repeat, shiftKey, ctrlKey, altKey, metaKey, capsLock, numLock) =>
         element.dispatchEvent(new KeyboardEvent(type, {
           code: code, key: key, location: location, repeat: repeat, shiftKey: shiftKey, ctrlKey: ctrlKey,
           altKey: altKey, metaKey: metaKey, modifierCapsLock: capsLock, modifierNumLock: numLock,
           bubbles: true, cancelable: true }))""",
)
private external fun dispatchWasmKey(
    element: JsAny,
    type: String,
    code: String,
    key: String,
    location: Int,
    repeat: Boolean,
    shiftKey: Boolean,
    ctrlKey: Boolean,
    altKey: Boolean,
    metaKey: Boolean,
    capsLock: Boolean,
    numLock: Boolean,
): Unit

/** Dispatches a real pointer event on [element], as a browser delivers one. */
@JsFun(
    """(element, type, pointerType, clientX, clientY, button, buttons, pressure, tiltX, tiltY, twist, tangentialPressure) =>
         element.dispatchEvent(new PointerEvent(type, {
           pointerType: pointerType, clientX: clientX, clientY: clientY, button: button, buttons: buttons,
           pressure: pressure, tiltX: tiltX, tiltY: tiltY, twist: twist, tangentialPressure: tangentialPressure,
           bubbles: true, cancelable: true }))""",
)
private external fun dispatchWasmPointer(
    element: JsAny,
    type: String,
    pointerType: String,
    clientX: Double,
    clientY: Double,
    button: Int,
    buttons: Int,
    pressure: Double,
    tiltX: Int,
    tiltY: Int,
    twist: Int,
    tangentialPressure: Double,
): Unit

/** Dispatches a real wheel event on [element], as a browser delivers one. */
@JsFun(
    """(element, deltaMode, deltaX, deltaY, buttons) =>
         element.dispatchEvent(new WheelEvent("wheel", {
           deltaMode: deltaMode, deltaX: deltaX, deltaY: deltaY, buttons: buttons,
           bubbles: true, cancelable: true }))""",
)
private external fun dispatchWasmWheel(
    element: JsAny,
    deltaMode: Int,
    deltaX: Double,
    deltaY: Double,
    buttons: Int,
): Unit
