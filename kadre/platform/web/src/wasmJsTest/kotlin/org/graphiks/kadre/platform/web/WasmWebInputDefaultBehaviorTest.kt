@file:OptIn(ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.platform.web

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlinx.browser.document
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.SurfaceUpdate
import org.graphiks.kadre.surface.SurfaceUpdateOutcome
import org.w3c.dom.HTMLElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * `SurfaceUpdate.inputDefaultBehavior` at the point of dispatch, on a real page and a real session.
 *
 * This is the Wasm twin of `JsWebInputDefaultBehaviorTest`, with the same cases and the same names so
 * the two can be read side by side: an element of the browser's own page, the Wasm DOM port observing
 * it, the shared surface deciding, and the browser itself reporting whether a default was dropped. The
 * instrument is the browser's own `defaultPrevented`, read by a listener this fixture registered
 * *after* the port installed its own, so it runs second and sees the final verdict; `dispatchEvent`'s
 * own answer — `false` when a cancelable event was cancelled — is asserted with it.
 *
 * Only the interop differs from the JS file: the events are built and the verdicts are read through
 * `@JsFun` snippets instead of `js(…)` ones, because Kotlin/Wasm sees neither a JS object nor a Kotlin
 * function value in a raw snippet. Nothing asserted here is Wasm-specific.
 */
class WasmWebInputDefaultBehaviorTest {
    @Test
    fun aWheelAndAScrollKeyKeepTheBrowserDefaultUnderHostDefault() = runTest {
        val harness = WasmDefaultBehaviorHarness(this)
        try {
            val surface = harness.surface()
            val events = mutableListOf<InputEvent>()
            val collector = launch { surface.input.events.collect { events += it } }
            testScheduler.runCurrent()
            assertEquals(
                InputDefaultBehavior.HostDefault,
                surface.state.value.inputDefaultBehavior,
                "a surface starts at the browser's own default, which is the policy under test",
            )

            assertTrue(
                dispatchWheel(harness.element),
                "dispatchEvent answers that the wheel was not cancelled: no listener dropped its default",
            )
            assertTrue(
                dispatchKey(harness.element, type = "keydown", code = "ArrowDown", logicalKey = "ArrowDown"),
                "and neither was the key press whose default scrolls the document",
            )
            testScheduler.runCurrent()

            assertEquals(1, harness.observed("wheel"), "the wheel was dispatched and the fixture observed it")
            assertEquals(
                "kept",
                harness.lastVerdict("wheel"),
                "the browser's own `defaultPrevented` stayed false: Kadre observed the wheel and left the " +
                    "scroll and zoom of the browsing context to the browser",
            )
            assertEquals("kept", harness.lastVerdict("keydown"), "and the document-scroll key keeps its default too")
            assertEquals(
                1,
                events.filterIsInstance<InputEvent.Scrolled>().size,
                "the wheel reached the reducer: the default was kept while the observation was delivered",
            )
            assertEquals(
                setOf<PhysicalKey>(PhysicalKey.Code(usagePage = 0x07, usageId = 0x51)),
                surface.input.state.value.keyboard.pressedKeys,
                "and so did the key: ArrowDown is pressed in the shared snapshot",
            )

            collector.cancel()
        } finally {
            harness.close()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun onlyTheClosedCategoriesLoseTheirDefaultUnderSuppressWhenPossible() = runTest {
        val harness = WasmDefaultBehaviorHarness(this)
        try {
            val surface = harness.surface()
            val events = mutableListOf<InputEvent>()
            val collector = launch { surface.input.events.collect { events += it } }
            testScheduler.runCurrent()

            assertIs<SurfaceUpdateOutcome.Applied>(
                assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                    surface.apply(
                        SurfaceUpdate(
                            inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.SuppressWhenPossible),
                        ),
                    ),
                ).value,
            )

            assertFalse(
                dispatchWheel(harness.element),
                "the wheel is cancelled by the port's own listener: its default scrolls and zooms the page",
            )
            assertEquals("dropped", harness.lastVerdict("wheel"))
            assertFalse(
                dispatchKey(harness.element, type = "keydown", code = "ArrowDown", logicalKey = "ArrowDown"),
                "the document-scroll key is cancelled too: the browser would scroll the document under Kadre",
            )
            assertEquals("dropped", harness.lastVerdict("keydown"))

            // Everything outside the closed set keeps its default, and every one of these still reaches
            // the reducer: suppression is never a way of swallowing input.
            assertTrue(
                dispatchKey(harness.element, type = "keyup", code = "ArrowDown", logicalKey = "ArrowDown"),
                "a release carries no scroll default",
            )
            assertTrue(
                dispatchKey(harness.element, type = "keydown", code = "KeyA", logicalKey = "a"),
                "a key outside the closed list keeps everything it would do",
            )
            assertTrue(
                dispatchKey(harness.element, type = "keydown", code = "Tab", logicalKey = "Tab"),
                "Tab keeps the focus traversal it owns: Kadre does not trap the keyboard in the surface",
            )
            assertTrue(
                dispatchPointer(harness.element, type = "pointerdown", button = 0, buttons = 1),
                "no pointer default is ever dropped by this phase",
            )
            assertTrue(dispatchPointer(harness.element, type = "pointermove", button = -1, buttons = 1))
            assertTrue(
                dispatchWheel(harness.element, deltaMode = 2),
                "a wheel Kadre cannot deliver is not suppressed either: suppression follows an " +
                    "observation Kadre was handed, never a browser default Kadre merely noticed",
            )
            testScheduler.runCurrent()

            assertEquals("kept", harness.lastVerdict("keyup"))
            assertEquals("kept", harness.lastVerdict("pointerdown"))
            assertEquals("kept", harness.lastVerdict("pointermove"))
            assertEquals("kept", harness.lastVerdict("wheel"), "the last wheel observed is the page-mode one")
            assertEquals(
                2,
                harness.observed("wheel"),
                "two wheels were dispatched: the suppressed one and the undeliverable one",
            )
            assertEquals(
                1,
                events.filterIsInstance<InputEvent.Scrolled>().size,
                "only the deliverable wheel became an observation, and it was the suppressed one",
            )
            assertEquals(
                setOf(
                    PhysicalKey.Code(usagePage = 0x07, usageId = 0x04),
                    PhysicalKey.Code(usagePage = 0x07, usageId = 0x2b),
                ),
                surface.input.state.value.keyboard.pressedKeys,
                "the keys that were not suppressed are pressed in the snapshot: they were delivered as usual",
            )

            collector.cancel()
        } finally {
            harness.close()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun restoringHostDefaultStopsSuppressingTheVeryNextEvent() = runTest {
        val harness = WasmDefaultBehaviorHarness(this)
        try {
            val surface = harness.surface()
            surface.apply(
                SurfaceUpdate(inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.SuppressWhenPossible)),
            )
            assertFalse(dispatchWheel(harness.element), "the suppression is in effect")

            assertIs<SurfaceUpdateOutcome.Applied>(
                assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                    surface.apply(
                        SurfaceUpdate(inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.HostDefault)),
                    ),
                ).value,
            )

            assertTrue(
                dispatchWheel(harness.element),
                "the next wheel after the restore runs its default again, without any re-registration",
            )
            assertEquals("kept", harness.lastVerdict("wheel"))
            assertTrue(dispatchKey(harness.element, type = "keydown", code = "ArrowDown", logicalKey = "ArrowDown"))
            assertEquals("kept", harness.lastVerdict("keydown"))
        } finally {
            harness.close()
            testScheduler.runCurrent()
        }
    }
}

/**
 * One element of the real page, one attached session over it, and this fixture's own verdict on every
 * event dispatched at it.
 *
 * The listeners that record the verdicts are installed in [recordVerdicts], which runs after the
 * session exists: the port installed its own listeners while attaching, so the browser calls this
 * fixture's listener *after* the port's and the `defaultPrevented` it reads is the final one.
 */
private class WasmDefaultBehaviorHarness(scope: TestScope) {
    val element: HTMLElement = (document.createElement("div") as HTMLElement).also {
        it.style.position = "absolute"
        it.style.left = "80px"
        it.style.top = "60px"
        it.style.width = "320px"
        it.style.height = "180px"
        document.body!!.appendChild(it)
    }

    private val scopeReady = CompletableDeferred<KadreScope>()
    private val session: KadreSession

    init {
        session = assertIs<KadreResult.Success<KadreSession>>(
            element.attachKadre(
                parentScope = scope,
                applicationFactory = KadreApplicationFactory {
                    KadreApplication {
                        scopeReady.complete(this)
                        awaitCancellation()
                    }
                },
            ),
        ).value
        recordVerdicts(element)
    }

    /** The primary surface of the attached session, once the application scope exists. */
    suspend fun surface(): HostSurface = scopeReady.await().primarySurface.value
        ?: error("an attached web session exposes a primary surface")

    /** How many events of [type] this fixture observed at all. */
    fun observed(type: String): Int = wasmVerdictCount(element, type)

    /**
     * The browser's verdict on the last event of [type] this fixture observed: `"dropped"` when its
     * default had been cancelled, `"kept"` when it had not, and `"none"` when the fixture saw no such
     * event — which is how a case tells "nothing was dispatched" from "nothing was dropped".
     */
    fun lastVerdict(type: String): String = wasmLastVerdict(element, type)

    fun close() {
        session.requestStop()
        element.remove()
    }
}

/**
 * Records one verdict per dispatched event of the types this phase listens for: whether the browser had
 * already been told to drop that event's default by the time this listener ran.
 *
 * The recorder reads the browser's own flag and never the port's internals, and it is registered with
 * an explicit `passive: false` like the port's own wheel listener, so the reading is possible at all.
 */
@JsFun(
    """(element) => {
         element.__kadreDefaultVerdicts = [];
         ["wheel", "keydown", "keyup", "pointerdown", "pointermove", "pointerup"].forEach(function (type) {
           element.addEventListener(type, function (event) {
             element.__kadreDefaultVerdicts.push([event.type, event.defaultPrevented]);
           }, { passive: false });
         });
       }""",
)
private external fun recordVerdicts(element: JsAny)

/** How many events of [type] the fixture observed at all. */
@JsFun(
    """(element, type) => element.__kadreDefaultVerdicts
         .filter(function (verdict) { return verdict[0] === type; }).length""",
)
private external fun wasmVerdictCount(element: JsAny, type: String): Int

/** The browser's verdict on the last event of [type] the fixture observed. */
@JsFun(
    """(element, type) => {
         var seen = element.__kadreDefaultVerdicts.filter(function (verdict) { return verdict[0] === type; });
         if (seen.length === 0) return "none";
         return seen[seen.length - 1][1] === true ? "dropped" : "kept";
       }""",
)
private external fun wasmLastVerdict(element: JsAny, type: String): String

/**
 * Dispatches a real wheel event on [element] and answers whether the browser did *not* cancel it.
 *
 * The return value of `dispatchEvent` is false when a cancelable event was cancelled by one of its
 * listeners, which is the same fact `defaultPrevented` carries, asserted here a second time.
 */
@JsFun(
    """(element, deltaMode, deltaY) =>
         element.dispatchEvent(new WheelEvent("wheel", {
           deltaMode: deltaMode, deltaX: 0.0, deltaY: deltaY,
           bubbles: true, cancelable: true }))""",
)
private external fun dispatchWasmDefaultWheel(element: JsAny, deltaMode: Int, deltaY: Double): Boolean

/** Dispatches a real keyboard event of [type] on [element] and answers whether it was not cancelled. */
@JsFun(
    """(element, type, code, key) =>
         element.dispatchEvent(new KeyboardEvent(type, {
           code: code, key: key, location: 0, repeat: false,
           shiftKey: false, ctrlKey: false, altKey: false, metaKey: false,
           bubbles: true, cancelable: true }))""",
)
private external fun dispatchWasmDefaultKey(element: JsAny, type: String, code: String, key: String): Boolean

/** Dispatches a real pointer event of [type] on [element] and answers whether it was not cancelled. */
@JsFun(
    """(element, type, button, buttons) =>
         element.dispatchEvent(new PointerEvent(type, {
           pointerType: "mouse", clientX: 100.0, clientY: 100.0, button: button, buttons: buttons,
           bubbles: true, cancelable: true }))""",
)
private external fun dispatchPointer(element: JsAny, type: String, button: Int, buttons: Int): Boolean

/** The one case, over the four `@JsFun` externals, so both targets call the same shape. */
private fun dispatchKey(element: HTMLElement, type: String, code: String, logicalKey: String): Boolean =
    dispatchWasmDefaultKey(element, type, code, logicalKey)

/** The wheel, over its own `@JsFun` external, with the same defaults as the JS target's helper. */
private fun dispatchWheel(element: HTMLElement, deltaMode: Int = 1, deltaY: Double = 40.0): Boolean =
    dispatchWasmDefaultWheel(element, deltaMode, deltaY)
