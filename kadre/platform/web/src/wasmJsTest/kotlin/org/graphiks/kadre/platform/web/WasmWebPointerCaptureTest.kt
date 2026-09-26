@file:OptIn(ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.platform.web

import kotlinx.browser.document
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.w3c.dom.HTMLElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The Wasm port's pointer-capture seam, on a real element of a real page — the twin of
 * `JsWebPointerCaptureTest`, case for case and assertion for assertion.
 *
 * What is proven here is the port's own work and nothing above it: that a capture decision reaches the
 * browser as `setPointerCapture`/`releasePointerCapture` *on the pointer the element observed pressed*,
 * that the browser's own `lostpointercapture` is reported to the surface, and that a browser error is
 * **contained** — returned as the failure the surface reports, never thrown into the callback of the
 * event that led to the decision (`WEB-IMPLEMENTATION-ROADMAP.md` §3.4). It is also the only proof that
 * Kotlin/Wasm can catch a JavaScript error raised by a DOM call at all, which a compile probe could
 * never show.
 *
 * What this suite does *not* prove, and cannot: that a capture succeeds. Chromium refuses
 * `setPointerCapture` for a pointer it does not consider active, and a synthetic `PointerEvent` is
 * never an active pointer — which is why the attempt here is asserted as the browser's refusal. The
 * successful capture, and with it the confinement a consumer observes, is the browser contract
 * scenario of the next task, driven by a real `page.mouse`; a green run of this file is not that proof
 * and must not be read as one.
 */
class WasmWebPointerCaptureTest {
    @Test
    fun theCaptureIsAskedOfTheBrowserForThePointerTheElementObservedPressed() {
        val harness = WasmCaptureHarness()
        try {
            dispatchWasmPointerButton(harness.element, "pointerdown", pointerId = 7, buttons = 1)

            val refused = assertIs<KadreResult.Failure>(
                harness.port.applyPointerCapture(captured = true),
                "the attempt is answered with the browser's own refusal, never with a throw",
            )
            assertEquals(
                expectedRefusal(),
                refused.reason,
                "the refusal is the contained failure of this mechanism, not the browser's error object",
            )
            assertEquals(
                listOf(7),
                harness.captureCalls(),
                "the pointer the port asked about is the one the element observed pressed",
            )

            // The way back is the same seam: a release is asked of the browser for the same pointer.
            assertIs<KadreResult.Failure>(harness.port.applyPointerCapture(captured = false))
            assertEquals(listOf(7), harness.releaseCalls(), "the release names the pointer the capture was asked for")
        } finally {
            harness.close()
        }
    }

    /**
     * The browser's error stays where it was raised.
     *
     * A DOM callback never lets a Kotlin exception escape the port (`WEB-IMPLEMENTATION-ROADMAP.md`
     * §3.4), and the call under test is made from inside the callback of the event that led to the
     * decision — a consumer asking for a capture as it reduces a press. `setPointerCapture` throws for a
     * pointer the browser does not consider active, so the fact this case proves is that the throw is
     * turned into the returned failure: the call of this test returning at all *is* the containment, and
     * no `assertFailsWith` is written here because a throw is exactly what must not happen.
     */
    @Test
    fun aBrowserErrorIsContainedAndReportedRatherThanThrown() {
        val harness = WasmCaptureHarness()
        try {
            dispatchWasmPointerButton(harness.element, "pointerdown", pointerId = 3, buttons = 1)

            val result = harness.port.applyPointerCapture(captured = true)

            assertEquals(
                expectedRefusal(),
                assertIs<KadreResult.Failure>(result).reason,
                "the browser's refusal comes back as a failure the surface can report as a rejected field",
            )
            assertEquals(listOf(3), harness.captureCalls(), "and it was a real attempt, not a silent skip")
        } finally {
            harness.close()
        }
    }

    /**
     * With no pointer observed, the browser is asked nothing and the port still reports a failure.
     *
     * The port cannot act on a pointer it never saw, and it does not invent one: the surface's own
     * ownership rule is what decides whether a capture may be asked for, and a port that quietly did
     * nothing here would answer a request it never performed with a success.
     */
    @Test
    fun nothingIsAskedOfTheBrowserWithoutAPointerTheElementHolds() {
        val harness = WasmCaptureHarness()
        try {
            assertEquals(expectedRefusal(), assertIs<KadreResult.Failure>(harness.port.applyPointerCapture(true)).reason)
            assertTrue(
                harness.captureCalls().isEmpty() && harness.releaseCalls().isEmpty(),
                "no pointer was observed, so the browser was asked for nothing: ${harness.captureCalls()}",
            )

            // A release of nothing is not a failure: there is no capture to end, and the DOM's own
            // `releasePointerCapture` for a pointer that holds none is a no-op rather than an error.
            assertEquals(KadreResult.Success(Unit), harness.port.applyPointerCapture(captured = false))
            assertTrue(harness.releaseCalls().isEmpty())
        } finally {
            harness.close()
        }
    }

    /**
     * The browser's `lostpointercapture` is reported as the browser's own fact.
     *
     * It is not an input observation — no member of the shared union describes it — so the channel is
     * what carries it, and the port reports it for the pointer this element holds and for no other: a
     * capture lost by a pointer this port never asked about is not this surface's to reconcile. A
     * pointer that was released is not held any more either, so its capture cannot be lost.
     */
    @Test
    fun theBrowsersOwnLostCaptureIsReportedForTheHeldPointerOnly() {
        val harness = WasmCaptureHarness()
        try {
            dispatchWasmPointerButton(harness.element, "pointerdown", pointerId = 7, buttons = 1)

            dispatchWasmPointerButton(harness.element, "lostpointercapture", pointerId = 9, buttons = 1)
            assertEquals(
                0,
                harness.captureLosses,
                "a capture lost for a pointer this element does not hold is not reported",
            )

            dispatchWasmPointerButton(harness.element, "lostpointercapture", pointerId = 7, buttons = 1)
            assertEquals(1, harness.captureLosses, "the loss of the held pointer's capture is the surface's fact")

            // The press is released: the pointer is not held any more, and its capture cannot be lost.
            dispatchWasmPointerButton(harness.element, "pointerup", pointerId = 7, buttons = 0)
            dispatchWasmPointerButton(harness.element, "lostpointercapture", pointerId = 7, buttons = 0)
            assertEquals(1, harness.captureLosses, "a pointer that was released is not held, so nothing is reported")
        } finally {
            harness.close()
        }
    }

    /**
     * A capture does not outlive the observation: releasing the port ends the capture it holds.
     *
     * A capture that survived the port would keep routing every event of that pointer to an element
     * Kadre stopped reading, which is a browser effect outliving the decision that asked for it. The
     * release is a mechanism of the port — it reports nothing to anyone, and the failure it cannot raise
     * is contained like every other call of this seam.
     */
    @Test
    fun releasingThePortEndsTheCaptureItHolds() {
        val harness = WasmCaptureHarness()
        try {
            dispatchWasmPointerButton(harness.element, "pointerdown", pointerId = 5, buttons = 1)
            harness.port.applyPointerCapture(captured = true)
            assertEquals(listOf(5), harness.captureCalls())

            harness.close()

            assertEquals(
                listOf(5),
                harness.releaseCalls(),
                "the capture the port holds is released with the element it was taken on",
            )
        } finally {
            harness.close()
        }
    }

    private fun expectedRefusal(): KadreFailure =
        KadreFailure.PlatformFailure(KadrePlatform.Web, "web-host", "pointer-capture-failed")
}

/**
 * One element of the real page, the port that observes it, and a ledger of every capture call that
 * reached the browser.
 *
 * The ledger is the browser's own: the two capture members of the element are patched *before* the port
 * exists, so what a case reads is what `setPointerCapture`/`releasePointerCapture` were really called
 * with — a member the port held privately would prove nothing about the browser. The element carries a
 * synthetic press like any other pointer event of this suite, which is what makes the attempt an
 * attempt and its refusal the browser's. The patch and the dispatch go through `@JsFun`, because
 * Kotlin/Wasm sees neither a JS object nor a Kotlin value from a raw snippet.
 */
private class WasmCaptureHarness {
    val element: HTMLElement = (document.createElement("div") as HTMLElement).also {
        it.style.position = "absolute"
        it.style.left = "80px"
        it.style.top = "60px"
        it.style.width = "320px"
        it.style.height = "180px"
        document.body!!.appendChild(it)
    }

    val port: WasmWebDomPort = WasmWebDomPort(element)

    /** How often the port reported the browser's own lost capture to the channel. */
    var captureLosses: Int = 0
        private set

    private var released: Boolean = false

    init {
        recordPointerCaptureCalls(element)
        port.installLifecycleObserver { }
        port.installInputObserver(
            object : WebInputObserver {
                override fun onObservation(stimulus: WebInputStimulus) = Unit

                override fun onPointerCaptureLost() {
                    captureLosses += 1
                }
            },
        )
    }

    /** The pointer identities the browser was asked to capture, in order. */
    fun captureCalls(): List<Int> = captureLog(wasmCapturedPointers(element))

    /** The pointer identities the browser was asked to release, in order. */
    fun releaseCalls(): List<Int> = captureLog(wasmReleasedPointers(element))

    private fun captureLog(joined: String): List<Int> =
        joined.split(",").filter { it.isNotEmpty() }.map { it.toInt() }

    /** Releases the port and takes the element out of the page. Idempotent, so a `finally` can call it. */
    fun close() {
        if (released) return
        released = true
        port.release()
        element.remove()
    }
}

/**
 * Records every capture call that reaches the browser, with the pointer it named.
 *
 * The patch is installed on the element before the port exists, and it delegates to the browser's own
 * member, so the recorded call is the real one — including its refusal, which is what the callers of
 * this seam are asserted to contain.
 */
@JsFun(
    """(element) => {
         var log = { captured: [], released: [] };
         element.__kadreTestCaptureLog = log;
         var capture = element.setPointerCapture.bind(element);
         var release = element.releasePointerCapture.bind(element);
         element.setPointerCapture = function (pointerId) { log.captured.push(pointerId); return capture(pointerId); };
         element.releasePointerCapture = function (pointerId) { log.released.push(pointerId); return release(pointerId); };
       }""",
)
private external fun recordPointerCaptureCalls(element: JsAny)

/** The pointer identities the browser was asked to capture, comma-separated. */
@JsFun("(element) => element.__kadreTestCaptureLog.captured.join(',')")
private external fun wasmCapturedPointers(element: JsAny): String

/** The pointer identities the browser was asked to release, comma-separated. */
@JsFun("(element) => element.__kadreTestCaptureLog.released.join(',')")
private external fun wasmReleasedPointers(element: JsAny): String

/**
 * Dispatches a real pointer event of [type] carrying [pointerId] and the browser's own button state.
 *
 * The button the model reads is the primary one, and the pointer is a mouse: what this suite observes is
 * the pointer identity a capture names, not a kind, and the primary button is the one a capture is
 * asked on.
 */
@JsFun(
    """(element, type, pointerId, buttons) =>
         element.dispatchEvent(new PointerEvent(type, {
           pointerId: pointerId, pointerType: "mouse", clientX: 100.0, clientY: 100.0,
           button: type === "pointerup" || type === "lostpointercapture" ? -1 : 0, buttons: buttons,
           bubbles: true, cancelable: true }))""",
)
private external fun dispatchWasmPointerButton(element: JsAny, type: String, pointerId: Int, buttons: Int): Unit
