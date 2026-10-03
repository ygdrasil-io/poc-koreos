package org.graphiks.kadre.platform.web

import kotlinx.browser.document
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.TextDocumentRevision
import org.graphiks.kadre.input.TextInputAction
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextRange
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.internal.runtime.TextInputDocumentCommand
import org.graphiks.kadre.internal.runtime.TextInputObservation
import org.graphiks.kadre.internal.runtime.TextInputOpenCommand
import org.graphiks.kadre.internal.runtime.TextInputOwner
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The JS text-input glue, driven by real browser events on real editable elements.
 *
 * What is proven here is the target's own work and nothing above it: a `beforeinput` or composition
 * event is read, copied into the primitive payload the port computes against its shadow, and delivered
 * as an observation stamped at the configured revision; the write-back sets the element's value and
 * its UTF-16 selection, read back from the element itself; an element the v1 contract does not address
 * gets no listeners and produces nothing; and an owner close withdraws every listener it installed.
 *
 * The channel is `jsTest` and not `webTest` because these cases need the DOM: an event object of the
 * browser's own construction dispatched on an element the browser owns is the thing under test. The
 * shadow, the revision contract and the surface wiring are proven on primitives in `webTest`, which
 * both targets run. Composition is synthetic (the D-X4 charter: real OS IME is the manual charter,
 * and CDP-driven IME is the Playwright phase), the accepted precedent of this repository.
 */
class JsWebTextInputTest {
    @Test
    fun theElementsOwnLengthsCountUtf16CodeUnitsOnBothSidesOfTheSeam() {
        val harness = JsTextHarness("input")
        try {
            harness.input.value = "a😀b"
            assertEquals(
                4,
                harness.input.value.length,
                "the element's value is four UTF-16 code units — a, the surrogate pair, b — exactly the " +
                    "count the Kotlin side's String gives, which is what makes the offsets shared",
            )
            assertEquals(
                4,
                "a😀b".length,
                "the Kotlin string of the same content counts the same code units",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun anInsertTextOnARealElementReportsTheReplaceAtUtf16Offsets() {
        val harness = JsTextHarness("input", surroundingText = "a😀b", selection = TextRange(4, 4))
        try {
            harness.open()
            harness.input.value = "a😀b"
            dispatchBeforeInput(harness.element, inputType = "insertText", data = "é")

            assertEquals(
                listOf<TextInputObservation>(
                    TextInputObservation.Replace(TextRange(4, 4), "é", TextDocumentRevision(7)),
                ),
                harness.observed,
                "the insertion at the caret after the emoji is reported at offset 4 — the UTF-16 code " +
                    "unit both sides count — never the three code points",
            )
            assertTrue(
                harness.observed.none { it is TextInputObservation.SelectionChanged },
                "no selection is fabricated from the edit",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun theWriteBackSetsTheValueAndTheUtf16SelectionOfTheRealElement() = runTest {
        val harness = JsTextHarness("input")
        try {
            val owner = harness.open()

            val written = harness.port.updateDocument(
                TextInputDocumentCommand(owner, "a😀bé", TextRange(5, 5), TextDocumentRevision(8)),
            )
            assertIs<KadreResult.Success<Unit>>(written)
            assertEquals("a😀bé", harness.input.value, "the snapshot is the element's value")
            assertEquals(5, harness.input.value.length, "the value crosses as the same UTF-16 code units")
            assertEquals(5, harness.input.selectionStart, "the caret after the emoji is offset 5, not code point 4")
            assertEquals(5, harness.input.selectionEnd)

            val spanning = harness.port.updateDocument(
                TextInputDocumentCommand(owner, "a😀b", TextRange(1, 3), TextDocumentRevision(9)),
            )
            assertIs<KadreResult.Success<Unit>>(spanning)
            assertEquals(1, harness.input.selectionStart, "the selection starts on the first code unit of the pair")
            assertEquals(3, harness.input.selectionEnd, "and ends after the second — the pair is two offsets")
        } finally {
            harness.close()
        }
    }

    @Test
    fun aRealCompositionSequenceReportsTheLifecycleWithoutFabricatedSelections() {
        val harness = JsTextHarness("input", surroundingText = "abc", selection = TextRange(3, 3))
        try {
            harness.open()
            dispatchComposition(harness.element, "compositionstart", data = null)
            dispatchBeforeInput(harness.element, inputType = "insertCompositionText", data = "か")
            dispatchComposition(harness.element, "compositionupdate", data = "か")
            dispatchComposition(harness.element, "compositionupdate", data = "かんじ")
            dispatchComposition(harness.element, "compositionend", data = "かんじ")

            val revision = TextDocumentRevision(7)
            assertEquals(
                listOf<TextInputObservation>(
                    TextInputObservation.CompositionChanged(TextRange(3, 3), "", TextRange(0, 0), revision),
                    TextInputObservation.CompositionChanged(TextRange(3, 3), "か", TextRange(1, 1), revision),
                    TextInputObservation.CompositionChanged(TextRange(3, 4), "かんじ", TextRange(3, 3), revision),
                    TextInputObservation.CompositionChanged(null, "", null, revision),
                ),
                harness.observed,
                "start opens at the selection, the paired insertCompositionText is the composition's own " +
                    "edit and is never an observation, each update replaces the span the previous one " +
                    "composed, and an end whose final string is the composed one terminates without a " +
                    "correction",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun aRealEnterKeydownOnAnInputPublishesTheConfiguredActionAndNoText() {
        val harness = JsTextHarness("input", surroundingText = "abc", selection = TextRange(3, 3), action = TextInputAction.Go)
        try {
            harness.open()
            dispatchKey(harness.element, "keydown", key = "Enter")
            dispatchKey(harness.element, "keydown", key = "a")

            assertEquals(
                listOf<TextInputObservation>(TextInputObservation.Action(TextInputAction.Go, TextDocumentRevision(7))),
                harness.observed,
                "Enter is the browser's submission key on a single-line element: the config's action, " +
                    "and no letter keydown becomes an observation",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun aTextareaEnterIsALineBreakOnceFromTheBeforeinputAndSilentFromTheKeydown() {
        val harness = JsTextHarness("textarea", surroundingText = "abc", selection = TextRange(3, 3), action = TextInputAction.Send)
        try {
            harness.open()
            dispatchKey(harness.element, "keydown", key = "Enter")
            dispatchBeforeInput(harness.element, inputType = "insertLineBreak")

            assertEquals(
                listOf<TextInputObservation>(
                    TextInputObservation.Replace(TextRange(3, 3), "\n", TextDocumentRevision(7)),
                    TextInputObservation.Action(TextInputAction.Send, TextDocumentRevision(7)),
                ),
                harness.observed,
                "the multiline element's Enter is the line the browser performs plus the AppKit action " +
                    "mapping, delivered once — the keydown contributes nothing",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun anElementThatIsNotAnInputOrTextareaInstallsNothingAndObservesNothing() {
        val contentEditable = harnessElement("div").also { it.setAttribute("contenteditable", "true") }
        recordListenerRegistrations(contentEditable)

        val plain = JsTextHarness("div")
        recordListenerRegistrations(plain.element)
        val editable = JsTextHarness(element = contentEditable)
        try {
            assertIs<KadreResult.Success<TextInputOwner>>(plain.portOpen())
            assertIs<KadreResult.Success<TextInputOwner>>(editable.portOpen())

            assertEquals(
                "",
                registeredListenerTypes(plain.element),
                "a plain div gets no text listeners at all",
            )
            assertEquals(
                "",
                registeredListenerTypes(editable.element),
                "a contenteditable div is out of the v1 scope and gets none either",
            )

            dispatchBeforeInput(editable.element, inputType = "insertText", data = "x")
            dispatchComposition(editable.element, "compositionstart")
            dispatchKey(editable.element, "keydown", key = "Enter")
            assertTrue(
                editable.observed.isEmpty(),
                "no observation of any kind is produced for an element the v1 contract does not address (D-X2)",
            )
        } finally {
            plain.close()
            editable.close()
        }
    }

    @Test
    fun anOpenInstallsExactlyTheTextListenersAndTheOwnerCloseWithdrawsThem() {
        val harness = JsTextHarness("input")
        try {
            recordListenerRegistrations(harness.element)
            val owner = assertIs<KadreResult.Success<TextInputOwner>>(harness.portOpen()).value

            assertEquals(
                listOf("beforeinput", "compositionstart", "compositionupdate", "compositionend", "keydown"),
                registeredListenerTypes(harness.element).split(","),
                "the observation set of the seam, installed once at the open",
            )

            owner.close()
            assertEquals(
                "",
                listenersWithoutRemoval(harness.element),
                "the owner close withdraws every listener the open installed",
            )

            dispatchBeforeInput(harness.element, inputType = "insertText", data = "late")
            assertTrue(
                harness.observed.isEmpty(),
                "a late event after the close is nothing: the listeners are gone",
            )
        } finally {
            harness.close()
        }
    }
}

/**
 * The one element a text case drives: a real editable element attached to the page, the real DOM
 * port's element access, and the real [WebTextInputPort] over it.
 */
private class JsTextHarness(
    val element: HTMLElement,
    surroundingText: String = "hello",
    selection: TextRange = TextRange(5, 5),
    action: TextInputAction = TextInputAction.Default,
) {
    constructor(
        kind: String,
        surroundingText: String = "hello",
        selection: TextRange = TextRange(5, 5),
        action: TextInputAction = TextInputAction.Default,
    ) : this(harnessElement(kind), surroundingText, selection, action)

    /** The element as the input element the value/selection cases read; an input case only. */
    val input: HTMLInputElement get() = element as HTMLInputElement

    val observed: MutableList<TextInputObservation> = mutableListOf()

    val port: WebTextInputPort = WebTextInputPort(
        checkNotNull(JsWebDomPort(element).textInputElementAccess),
    )

    private val config = TextInputConfig(
        action = action,
        surroundingText = surroundingText,
        selection = selection,
        documentRevision = TextDocumentRevision(7),
    )

    private var released = false

    /** Opens one session on the port, recording every observation it publishes. */
    fun portOpen(): KadreResult<TextInputOwner> = port.open(
        TextInputOpenCommand(
            surfaceId = RuntimeProcessIds.nextSurfaceId(),
            config = config,
            onObservation = { observation ->
                observed += observation
                true
            },
        ),
    )

    fun open(): TextInputOwner = assertIs<KadreResult.Success<TextInputOwner>>(portOpen()).value

    fun close() {
        if (released) return
        released = true
        element.remove()
    }
}

private fun harnessElement(kind: String): HTMLElement = (document.createElement(kind) as HTMLElement).also { element ->
    element.style.position = "absolute"
    element.style.left = "80px"
    element.style.top = "60px"
    document.body!!.appendChild(element)
}

/** Dispatches a real `beforeinput` with the input type and data a browser would carry. */
private fun dispatchBeforeInput(
    element: HTMLElement,
    inputType: String,
    data: String? = null,
): Unit = js(
    """element.dispatchEvent(new InputEvent("beforeinput", {
         inputType: inputType, data: data, bubbles: true, cancelable: true }))""",
)

/** Dispatches a real composition event of [type], with the data a browser would carry. */
private fun dispatchComposition(
    element: HTMLElement,
    type: String,
    data: String? = null,
): Unit = js(
    """element.dispatchEvent(new CompositionEvent(type, { data: data, bubbles: true, cancelable: true }))""",
)

/** Dispatches a real keyboard event of [type] on [element], as a browser delivers one. */
private fun dispatchKey(
    element: HTMLElement,
    type: String,
    key: String,
): Unit = js(
    """element.dispatchEvent(new KeyboardEvent(type, { key: key, bubbles: true, cancelable: true }))""",
)

/** Records every listener registration of [element], added and removed references alike. */
private fun recordListenerRegistrations(element: HTMLElement): Unit = js(
    """(function () {
         var log = { added: [], removed: [] };
         element.__kadreTestListenerLog = log;
         var add = element.addEventListener.bind(element);
         var remove = element.removeEventListener.bind(element);
         element.addEventListener = function (type, listener, options) {
           log.added.push([type, listener]);
           return add(type, listener, options);
         };
         element.removeEventListener = function (type, listener, options) {
           log.removed.push([type, listener]);
           return remove(type, listener, options);
         };
       }())""",
)

/** The event types a listener was registered for on [element], as a comma-joined string. */
private fun registeredListenerTypes(element: HTMLElement): String = js(
    """element.__kadreTestListenerLog.added.map(function (entry) { return entry[0]; }).join(",")""",
)

/** The registered listeners [element] was never asked to remove, as a comma-joined string of types. */
private fun listenersWithoutRemoval(element: HTMLElement): String = js(
    """(function () {
         var log = element.__kadreTestListenerLog;
         return log.added
           .filter(function (entry) {
             return !log.removed.some(function (removal) {
               return removal[0] === entry[0] && removal[1] === entry[1];
             });
           })
           .map(function (entry) { return entry[0]; })
           .join(",");
       }())""",
)
