package org.graphiks.kadre.platform.web

import kotlinx.browser.document
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.internal.runtime.RuntimeSynchronousInteraction
import org.graphiks.kadre.surface.LogicalPoint
import org.w3c.dom.HTMLElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The JS port's interaction seam, on a real element of a real page: the four fullscreen and
 * pointer-lock primitives, and the synchronous interaction dispatch of its own listeners.
 *
 * What is proven here is the port's own work and nothing above it: a primitive member *issues* the
 * browser call (the ledger records what reached the patched browser member) and answers the emission
 * synchronously, the browser's terminal answer arrives through the terminal callback — one shot,
 * from the event the browser fires — an exit asked for with no fullscreen/pointer-lock state
 * completes synchronously with **zero** browser calls, and a released port hears no terminal event
 * at all. The dispatch cases prove the order the AppKit contract fixes (`DESIGN.md:983-989`): the
 * interaction trigger leaves the listener *before* the ordinary stimulus of the same event, carrying
 * exactly the values the ordinary path computes.
 *
 * The browser members are patched rather than borrowed because the real ones answer to a transient
 * activation synthetic events never carry — what this suite proves is that the port asked and that
 * the port completed on the answer, not what Chromium answers without an activation; the primitive's
 * real behaviour is the smoke scenario's (a real click) and the manual charter's. The dispatch of a
 * terminal event is a synthetic `Event` with the bubbles the DOM's own firings carry, which is what
 * makes the document-level terminal listeners the same listeners a real `fullscreenchange`
 * (fired on the element) and a real `pointerlockchange` (fired on the document) both reach.
 */
class JsWebInteractionTest {
    // ---------------------------------------------------------------- fullscreen

    @Test
    fun requestFullscreenIssuesThePrimitiveAndCompletesOnTheChangeCallback() {
        val harness = JsInteractionHarness()
        try {
            harness.patchBrowserMembers()
            val terminals = mutableListOf<Boolean>()

            val issued = harness.port.requestFullscreen(WebPrimitiveTerminal { terminals += it })

            assertIs<KadreResult.Success<Unit>>(
                issued,
                "the issue is the synchronous answer: the primitive was emitted, the verdict is not in yet",
            )
            assertEquals(
                1,
                harness.fullscreenRequests(),
                "the primitive crossed the browser's own requestFullscreen member",
            )
            assertEquals(
                emptyList(),
                terminals,
                "the browser has not answered: no terminal callback before its event",
            )

            // The confirmation is the event the browser fires on the element it took fullscreen.
            harness.dispatchEvent(harness.element, "fullscreenchange", bubbles = true)
            assertEquals(listOf(true), terminals, "the browser's change event is the committed answer")

            // One-shot: the listeners went with the first terminal, a later event is nothing at all.
            harness.dispatchEvent(harness.element, "fullscreenchange", bubbles = true)
            assertEquals(
                listOf(true),
                terminals,
                "the terminal listeners are one-shot: a second change completes nothing",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun aFullscreenChangeForAnotherTargetDoesNotCompleteARequest() {
        val harness = JsInteractionHarness()
        try {
            harness.patchBrowserMembers()
            val terminals = mutableListOf<Boolean>()
            assertIs<KadreResult.Success<Unit>>(
                harness.port.requestFullscreen(WebPrimitiveTerminal { terminals += it }),
            )

            // An exit fires its change on the document, and so would a change this element has
            // nothing to do with: the request's confirmation is this element's own word only.
            harness.dispatchEvent(document, "fullscreenchange")
            assertEquals(
                emptyList(),
                terminals,
                "a change whose target is not the element is not the answer to this element's request",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun aBrowserRefusalCompletesAsNotCommitted() = runTest {
        val harness = JsInteractionHarness()
        try {
            harness.patchBrowserMembers()

            // The promise path: Chromium rejects the requestFullscreen promise, and a rejection the
            // port never caught would stay floating — awaiting here is the proof it was caught.
            harness.elementRefusesFullscreen()
            val promiseAnswer = CompletableDeferred<Boolean>()
            assertIs<KadreResult.Success<Unit>>(
                harness.port.requestFullscreen(WebPrimitiveTerminal { promiseAnswer.complete(it) }),
            )
            assertEquals(
                false,
                promiseAnswer.await(),
                "the rejected promise is the refusal: not committed, and caught rather than floating",
            )

            // The event path: a `fullscreenerror` the browser fires is the same refusal, for an
            // emission whose promise has nothing to reject.
            val eventAnswer = mutableListOf<Boolean>()
            assertIs<KadreResult.Success<Unit>>(
                harness.port.requestFullscreen(WebPrimitiveTerminal { eventAnswer += it }),
            )
            harness.dispatchEvent(harness.element, "fullscreenerror", bubbles = true)
            assertEquals(
                listOf(false),
                eventAnswer,
                "the browser's error event is the refusal: the one honest not-committed answer",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun exitFullscreenWithoutFullscreenElementCompletesSynchronouslyWithoutCallingTheBrowser() {
        val harness = JsInteractionHarness()
        try {
            harness.patchBrowserMembers()
            val terminals = mutableListOf<Boolean>()

            val issued = harness.port.exitFullscreen(WebPrimitiveTerminal { terminals += it })

            assertIs<KadreResult.Success<Unit>>(issued)
            assertEquals(
                listOf(true),
                terminals,
                "nothing is fullscreen: the committed answer is already there, synchronously",
            )
            assertEquals(
                0,
                harness.exitFullscreenCalls(),
                "zero browser calls for an exit with no fullscreen element to leave",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun exitFullscreenWithAFullscreenElementAsksTheBrowserAndCompletesOnTheChangeCallback() {
        val harness = JsInteractionHarness()
        try {
            harness.patchBrowserMembers()
            harness.forceDocumentFullscreenElement()
            val terminals = mutableListOf<Boolean>()

            val issued = harness.port.exitFullscreen(WebPrimitiveTerminal { terminals += it })

            assertIs<KadreResult.Success<Unit>>(issued)
            assertEquals(1, harness.exitFullscreenCalls(), "the exit crossed the browser's own member")
            assertEquals(emptyList(), terminals, "the browser has not answered: no terminal before its event")

            // The exit's change fires on the document — the fullscreen element is already cleared
            // when the browser fires it — and the document listener is the one that hears it.
            harness.dispatchEvent(document, "fullscreenchange")
            assertEquals(listOf(true), terminals, "the browser's change event is the committed answer")
        } finally {
            harness.close()
        }
    }

    // ------------------------------------------------------------- pointer lock

    @Test
    fun requestPointerLockIssuesThePrimitiveAndCompletesOnTheChangeCallback() {
        val harness = JsInteractionHarness()
        try {
            harness.patchBrowserMembers()
            val terminals = mutableListOf<Boolean>()

            val issued = harness.port.requestPointerLock(WebPrimitiveTerminal { terminals += it })

            assertIs<KadreResult.Success<Unit>>(
                issued,
                "the issue is the synchronous answer: the primitive was emitted, the verdict is not in yet",
            )
            assertEquals(
                1,
                harness.requestPointerLockCalls(),
                "the primitive crossed the browser's own requestPointerLock member",
            )
            assertEquals(emptyList(), terminals, "the browser has not answered: no terminal before its event")

            harness.dispatchEvent(harness.element, "pointerlockchange", bubbles = true)
            assertEquals(listOf(true), terminals, "the browser's change event is the committed answer")

            harness.dispatchEvent(harness.element, "pointerlockchange", bubbles = true)
            assertEquals(
                listOf(true),
                terminals,
                "the terminal listeners are one-shot: a second change completes nothing",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun aPointerLockRefusalCompletesAsNotCommitted() = runTest {
        val harness = JsInteractionHarness()
        try {
            harness.patchBrowserMembers()

            // The promise path: Chromium rejects the requestPointerLock promise it returns.
            harness.elementRefusesPointerLock()
            val promiseAnswer = CompletableDeferred<Boolean>()
            assertIs<KadreResult.Success<Unit>>(
                harness.port.requestPointerLock(WebPrimitiveTerminal { promiseAnswer.complete(it) }),
            )
            assertEquals(
                false,
                promiseAnswer.await(),
                "the rejected promise is the refusal: not committed, and caught rather than floating",
            )

            // The event path: a `pointerlockerror` is the same refusal for a promise-less emission.
            val eventAnswer = mutableListOf<Boolean>()
            assertIs<KadreResult.Success<Unit>>(
                harness.port.requestPointerLock(WebPrimitiveTerminal { eventAnswer += it }),
            )
            harness.dispatchEvent(harness.element, "pointerlockerror", bubbles = true)
            assertEquals(
                listOf(false),
                eventAnswer,
                "the browser's error event is the refusal: the one honest not-committed answer",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun exitPointerLockWithoutAPointerLockElementCompletesSynchronouslyWithoutCallingTheBrowser() {
        val harness = JsInteractionHarness()
        try {
            harness.patchBrowserMembers()
            val terminals = mutableListOf<Boolean>()

            val issued = harness.port.exitPointerLock(WebPrimitiveTerminal { terminals += it })

            assertIs<KadreResult.Success<Unit>>(issued)
            assertEquals(
                listOf(true),
                terminals,
                "nothing is pointer-locked: the committed answer is already there, synchronously",
            )
            assertEquals(
                0,
                harness.exitPointerLockCalls(),
                "zero browser calls for an exit with no pointer-lock element to unlock",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun exitPointerLockWithTheElementLockedAsksTheBrowserAndCompletesOnTheChangeCallback() {
        val harness = JsInteractionHarness()
        try {
            harness.patchBrowserMembers()
            harness.forceDocumentPointerLockElement()
            val terminals = mutableListOf<Boolean>()

            val issued = harness.port.exitPointerLock(WebPrimitiveTerminal { terminals += it })

            assertIs<KadreResult.Success<Unit>>(issued)
            assertEquals(1, harness.exitPointerLockCalls(), "the exit crossed the browser's own member")
            assertEquals(emptyList(), terminals, "the browser has not answered: no terminal before its event")

            harness.dispatchEvent(document, "pointerlockchange")
            assertEquals(listOf(true), terminals, "the browser's change event is the committed answer")
        } finally {
            harness.close()
        }
    }

    // ----------------------------------------------------------------- teardown

    @Test
    fun lateTerminalEventsAfterReleaseAreIgnored() {
        val harness = JsInteractionHarness()
        try {
            harness.patchBrowserMembers()
            val terminals = mutableListOf<Boolean>()
            assertIs<KadreResult.Success<Unit>>(
                harness.port.requestFullscreen(WebPrimitiveTerminal { terminals += it }),
            )
            assertIs<KadreResult.Success<Unit>>(
                harness.port.requestPointerLock(WebPrimitiveTerminal { terminals += it }),
            )

            harness.close()

            harness.dispatchEvent(harness.element, "fullscreenchange", bubbles = true)
            harness.dispatchEvent(harness.element, "fullscreenerror", bubbles = true)
            harness.dispatchEvent(harness.element, "pointerlockchange", bubbles = true)
            harness.dispatchEvent(harness.element, "pointerlockerror", bubbles = true)
            harness.dispatchPointer(clientX = 100.0, clientY = 100.0, button = 0, buttons = 1)

            assertTrue(
                terminals.isEmpty(),
                "a released port removed its terminal listeners: no late event answers anything",
            )
            assertTrue(
                harness.triggers.isEmpty() && harness.observations.isEmpty(),
                "and nothing of the element's input reaches it either",
            )
        } finally {
            harness.close()
        }
    }

    // -------------------------------------------------------- dispatcher wiring

    @Test
    fun aPointerPressDispatchesTheInteractionBeforeTheOrdinaryStimulus() {
        val harness = JsInteractionHarness()
        try {
            harness.installDispatcher()
            val origin = harness.elementOrigin()
            harness.dispatchPointer(
                origin.x + 35.0,
                origin.y + 44.0,
                button = 0,
                buttons = 1,
                pressure = 0.5,
            )

            assertEquals(
                listOf("interaction", "observation"),
                harness.journal,
                "the listener dispatches the interaction before it delivers the ordinary stimulus",
            )
            val trigger = assertIs<RuntimeSynchronousInteraction.PointerPressed>(harness.triggers.single())
            val observed = assertIs<WebInputStimulus.PointerButtonChanged>(harness.observations.single())
            assertEquals(PointerButton.Primary, trigger.button)
            assertEquals(LogicalPoint(35.0, 44.0), trigger.position)
            assertEquals(
                0.5,
                trigger.pressure,
                "the event's own pressure travels into the trigger, never narrowed",
            )
            assertEquals(trigger.button, observed.button, "the trigger reads what the ordinary path reads")
            assertEquals(trigger.position, observed.position, "the same position mapping, once")
            assertEquals(trigger.pressure, observed.pressure, "the same pressure mapping, once")
        } finally {
            harness.close()
        }
    }

    @Test
    fun aKeyPressDispatchesTheInteractionBeforeTheOrdinaryStimulus() {
        val harness = JsInteractionHarness()
        try {
            harness.installDispatcher()
            harness.dispatchKey(type = "keydown", code = "KeyA", key = "a")

            assertEquals(
                listOf("interaction", "observation"),
                harness.journal,
                "the listener dispatches the interaction before it delivers the ordinary stimulus",
            )
            val trigger = assertIs<RuntimeSynchronousInteraction.KeyPressed>(harness.triggers.single())
            assertEquals(
                PhysicalKey.Code(usagePage = 0x07, usageId = 0x04),
                trigger.physicalKey,
                "the physical key is the one the ordinary stimulus carries",
            )
            val observed = assertIs<WebInputStimulus.KeyChanged>(harness.observations.single())
            assertEquals(trigger.physicalKey, observed.physicalKey, "the same key mapping, once")
        } finally {
            harness.close()
        }
    }

    @Test
    fun aTouchPressDispatchesNoInteractionAtAll() {
        val harness = JsInteractionHarness()
        try {
            harness.installDispatcher()
            val origin = harness.elementOrigin()
            harness.dispatchPointer(
                origin.x + 7.0,
                origin.y + 7.0,
                button = 0,
                buttons = 1,
                pressure = 0.5,
                pointerType = "touch",
            )

            assertTrue(
                harness.triggers.isEmpty() && harness.observations.isEmpty(),
                "a pointer kind the phase refuses delivers no observation and no interaction: " +
                    "${harness.triggers} / ${harness.observations}",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun aSecondDispatcherWhileAttachedIsRefusedAndReleasedPortsAnswerNothing() {
        val harness = JsInteractionHarness()
        try {
            harness.installDispatcher()
            // One channel, installed once: a second installation while the port is attached is a
            // programming error, not a replacement the listeners never asked for.
            harness.installDispatcherSecondTimeFails()

            harness.close()

            harness.dispatchPointer(clientX = 100.0, clientY = 100.0, button = 0, buttons = 1)
            harness.dispatchKey(type = "keydown", code = "KeyA", key = "a")

            assertTrue(
                harness.triggers.isEmpty() && harness.observations.isEmpty(),
                "a released port holds no dispatcher and no listener: nothing answers",
            )
        } finally {
            harness.close()
        }
    }
}

/**
 * One element of the real page, the port that observes it, and every fact the port produced.
 *
 * The browser members the primitives cross are patched only when a case asks for it
 * ([patchBrowserMembers]), and the document members the patch touches are restored on [close], so
 * the page the next case starts from is the browser's own.
 */
private class JsInteractionHarness {
    val element: HTMLElement = (document.createElement("div") as HTMLElement).also {
        it.style.position = "absolute"
        it.style.left = "80px"
        it.style.top = "60px"
        it.style.width = "320px"
        it.style.height = "180px"
        document.body!!.appendChild(it)
    }

    val port: JsWebDomPort = JsWebDomPort(element)

    /** Every interaction trigger the dispatcher received, in dispatch order. */
    val triggers = mutableListOf<RuntimeSynchronousInteraction>()

    /** The order the port acted in: the interaction dispatch, then the ordinary observation. */
    val journal = mutableListOf<String>()

    /** Every ordinary stimulus the port delivered, in delivery order. */
    val observations = mutableListOf<WebInputStimulus>()

    private var released = false

    init {
        port.installLifecycleObserver { }
        port.installInputObserver { stimulus ->
            journal += "observation"
            observations += stimulus
        }
    }

    /** Installs the recording dispatcher the surface installs with its session configuration. */
    fun installDispatcher() {
        port.installInteractionDispatcher(WebInteractionDispatcher { trigger ->
            journal += "interaction"
            triggers += trigger
        })
    }

    /** A second dispatcher on the same port is a programming error, not a replacement. */
    fun installDispatcherSecondTimeFails() {
        val failure = runCatching { installDispatcher() }.exceptionOrNull()
        assertIs<IllegalStateException>(failure, "the dispatcher is installed once, and only once")
    }

    /** Patches the four browser members the primitives cross, with the ledger of what reached them. */
    fun patchBrowserMembers() = patchInteractionMembers(element)

    /** How often the browser's own `requestFullscreen` member was called. */
    fun fullscreenRequests(): Int = primitiveCalls(element, "requestFullscreen")

    /** How often the browser's own `exitFullscreen` member was called. */
    fun exitFullscreenCalls(): Int = primitiveCalls(element, "exitFullscreen")

    /** How often the browser's own `requestPointerLock` member was called. */
    fun requestPointerLockCalls(): Int = primitiveCalls(element, "requestPointerLock")

    /** How often the browser's own `exitPointerLock` member was called. */
    fun exitPointerLockCalls(): Int = primitiveCalls(element, "exitPointerLock")

    /** The next `requestFullscreen` answers with a rejected promise, as a refusing browser does. */
    fun elementRefusesFullscreen() = rejectNextRequestFullscreen(element)

    /** The next `requestPointerLock` answers with a rejected promise, as a refusing browser does. */
    fun elementRefusesPointerLock() = rejectNextRequestPointerLock(element)

    /** The document reports this element as its fullscreen element, as a browser in it would. */
    fun forceDocumentFullscreenElement() = documentFullscreenElementIs(element)

    /** The document reports this element as its pointer-lock element, as a browser in it would. */
    fun forceDocumentPointerLockElement() = documentPointerLockElementIs(element)

    /** Dispatches a state event of the primitives, as the browser fires one. */
    fun dispatchEvent(target: Any, type: String, bubbles: Boolean = false) {
        dispatchStateEvent(target, type, bubbles)
    }

    /** The viewport position of the element's own origin, read back from the browser. */
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

    /** Dispatches a real pointer event of [type] on the element. */
    fun dispatchPointer(
        clientX: Double,
        clientY: Double,
        button: Int,
        buttons: Int,
        pressure: Double = 0.0,
        pointerType: String = "mouse",
    ) {
        dispatchPointerEvent(element, type = "pointerdown", pointerType, clientX, clientY, button, buttons, pressure)
    }

    /** Dispatches a real keyboard event of [type] on the element. */
    fun dispatchKey(type: String, code: String, key: String) {
        dispatchKeyEvent(element, type, code, key)
    }

    /** Releases the port, restores the document's own members, and takes the element out. Idempotent. */
    fun close() {
        if (released) return
        released = true
        port.release()
        restoreDocumentMembers()
        element.remove()
    }
}

/** Patches the four primitive members the port calls, counting every call that reaches them. */
private fun patchInteractionMembers(element: HTMLElement): Unit = js(
    """(function () {
         var log = { requestFullscreen: 0, exitFullscreen: 0, requestPointerLock: 0, exitPointerLock: 0 };
         element.__kadreTestPrimitiveLog = log;
         element.requestFullscreen = function () {
           log.requestFullscreen += 1;
           var result = element.__kadreTestFullscreenResult;
           return result === undefined ? Promise.resolve() : result;
         };
         element.requestPointerLock = function () {
           log.requestPointerLock += 1;
           var result = element.__kadreTestPointerLockResult;
           return result === undefined ? Promise.resolve() : result;
         };
         document.exitFullscreen = function () {
           log.exitFullscreen += 1;
           var result = document.__kadreTestExitResult;
           return result === undefined ? Promise.resolve() : result;
         };
         document.exitPointerLock = function () { log.exitPointerLock += 1; };
       }())""",
)

/** One side of the ledger, as a count. */
private fun primitiveCalls(element: HTMLElement, member: String): Int {
    val count: Int = js("element.__kadreTestPrimitiveLog[member]")
    return count
}

/** The next `requestFullscreen` rejects its promise, the way a browser without the activation does. */
private fun rejectNextRequestFullscreen(element: HTMLElement): Unit = js(
    "element.__kadreTestFullscreenResult = Promise.reject(new Error('fullscreen refused by the test'))",
)

/** The next `requestPointerLock` rejects its promise, the way a refusing browser does. */
private fun rejectNextRequestPointerLock(element: HTMLElement): Unit = js(
    "element.__kadreTestPointerLockResult = Promise.reject(new Error('pointer lock refused by the test'))",
)

/** The document's `fullscreenElement` reads as [element], the way a browser in fullscreen does. */
private fun documentFullscreenElementIs(element: HTMLElement): Unit = js(
    """Object.defineProperty(document, 'fullscreenElement',
         { configurable: true, get: function () { return element; } })""",
)

/** The document's `pointerLockElement` reads as [element], the way a pointer-locked browser does. */
private fun documentPointerLockElementIs(element: HTMLElement): Unit = js(
    """Object.defineProperty(document, 'pointerLockElement',
         { configurable: true, get: function () { return element; } })""",
)

/** Gives the document its own members back; a member that was never patched deletes as a no-op. */
private fun restoreDocumentMembers(): Unit = js(
    """delete document.exitFullscreen;
       delete document.exitPointerLock;
       delete document.fullscreenElement;
       delete document.pointerLockElement""",
)

/** Dispatches a state event of the primitives on [target], with the bubbling the browser's carries. */
private fun dispatchStateEvent(target: Any, type: String, bubbles: Boolean): Unit = js(
    "target.dispatchEvent(new Event(type, { bubbles: bubbles }))",
)

/** Dispatches a real pointer event of [type] on [element], as a browser delivers one. */
private fun dispatchPointerEvent(
    element: HTMLElement,
    type: String,
    pointerType: String,
    clientX: Double,
    clientY: Double,
    button: Int,
    buttons: Int,
    pressure: Double,
): Unit = js(
    """element.dispatchEvent(new PointerEvent(type, {
         pointerId: 1, pointerType: pointerType, clientX: clientX, clientY: clientY,
         button: button, buttons: buttons, pressure: pressure, bubbles: true, cancelable: true }))""",
)

/** Dispatches a real keyboard event of [type] on [element], as a browser delivers one. */
private fun dispatchKeyEvent(element: HTMLElement, type: String, code: String, key: String): Unit = js(
    """element.dispatchEvent(new KeyboardEvent(type, {
         code: code, key: key, bubbles: true, cancelable: true }))""",
)
