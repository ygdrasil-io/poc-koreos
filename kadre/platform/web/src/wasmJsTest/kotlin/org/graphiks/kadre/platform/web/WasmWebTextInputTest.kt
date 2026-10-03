package org.graphiks.kadre.platform.web

import kotlinx.browser.document
import kotlinx.coroutines.test.runTest
import kotlin.js.JsAny
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The Wasm text-input glue, driven by real browser events on real editable elements — the twin of
 * `JsWebTextInputTest`, with the same cases and the same readings, because the two targets must
 * agree on every value the seam delivers.
 *
 * What is proven here is the target's own work and nothing above it: a `beforeinput` or composition
 * event is read, copied into the primitive payload the port computes against its shadow, and delivered
 * as an observation stamped at the configured revision; the write-back sets the element's value and
 * its UTF-16 selection, read back from the element itself; an element the v1 contract does not address
 * gets no listeners and produces nothing; and an owner close withdraws every listener it installed.
 *
 * The events are built and the listeners are watched through `@JsFun` because Kotlin/Wasm sees neither
 * a JS object nor a Kotlin function value from a raw snippet. Composition is synthetic (the D-X4
 * charter: real OS IME is the manual charter), the accepted precedent of this repository.
 */
class WasmWebTextInputTest {
    @Test
    fun theElementsOwnLengthsCountUtf16CodeUnitsOnBothSidesOfTheSeam() {
        val harness = WasmTextHarness("input")
        try {
            wasmSetValue(harness.element, "a😀b")
            assertEquals(
                4,
                wasmValueLength(harness.element),
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
        val harness = WasmTextHarness("input", surroundingText = "a😀b", selection = TextRange(4, 4))
        try {
            harness.open()
            wasmSetValue(harness.element, "a😀b")
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
        val harness = WasmTextHarness("input")
        try {
            val owner = harness.open()

            val written = harness.port.updateDocument(
                TextInputDocumentCommand(owner, "a😀bé", TextRange(5, 5), TextDocumentRevision(8)),
            )
            assertIs<KadreResult.Success<Unit>>(written)
            assertEquals("a😀bé", wasmReadValue(harness.element), "the snapshot is the element's value")
            assertEquals(5, wasmValueLength(harness.element), "the value crosses as the same UTF-16 code units")
            assertEquals(5, wasmSelectionStart(harness.element), "the caret after the emoji is offset 5, not code point 4")
            assertEquals(5, wasmSelectionEnd(harness.element))

            val spanning = harness.port.updateDocument(
                TextInputDocumentCommand(owner, "a😀b", TextRange(1, 3), TextDocumentRevision(9)),
            )
            assertIs<KadreResult.Success<Unit>>(spanning)
            assertEquals(1, wasmSelectionStart(harness.element), "the selection starts on the first code unit of the pair")
            assertEquals(3, wasmSelectionEnd(harness.element), "and ends after the second — the pair is two offsets")
        } finally {
            harness.close()
        }
    }

    @Test
    fun aRealCompositionSequenceReportsTheLifecycleWithoutFabricatedSelections() {
        val harness = WasmTextHarness("input", surroundingText = "abc", selection = TextRange(3, 3))
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
        val harness = WasmTextHarness("input", surroundingText = "abc", selection = TextRange(3, 3), action = TextInputAction.Go)
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
        val harness = WasmTextHarness("textarea", surroundingText = "abc", selection = TextRange(3, 3), action = TextInputAction.Send)
        try {
            harness.open()
            dispatchKey(harness.element, "keydown", key = "Enter")
            dispatchBeforeInput(harness.element, inputType = "insertLineBreak", data = null)

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

        val plain = WasmTextHarness("div")
        recordListenerRegistrations(plain.element)
        val editable = WasmTextHarness(contentEditable)
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
            dispatchComposition(editable.element, "compositionstart", data = null)
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
    fun theWriteBackSetsTheValueAndTheSelectionOfARealTextarea() = runTest {
        val harness = WasmTextHarness("textarea")
        try {
            val owner = harness.open()

            val written = harness.port.updateDocument(
                TextInputDocumentCommand(owner, "a😀bé", TextRange(5, 5), TextDocumentRevision(8)),
            )
            assertIs<KadreResult.Success<Unit>>(written)
            assertEquals("a😀bé", wasmReadValue(harness.element), "the multiline element takes its own branch of the probe")
            assertEquals(5, wasmSelectionStart(harness.element), "the caret after the emoji is offset 5, not code point 4")
            assertEquals(5, wasmSelectionEnd(harness.element))
        } finally {
            harness.close()
        }
    }

    @Test
    fun theWriteBackOnAnElementThatCannotCarryItIsRefusedAndTheElementIsUntouched() = runTest {
        val harness = WasmTextHarness("div")
        try {
            val owner = assertIs<KadreResult.Success<TextInputOwner>>(harness.portOpen()).value
            val attributesBefore = attributeCount(harness.element)

            val refused = harness.port.updateDocument(
                TextInputDocumentCommand(owner, "x", TextRange(0, 0), TextDocumentRevision(8)),
            )
            assertEquals(
                KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession)),
                refused,
                "the write-back is the contract and a plain div cannot carry it: the closed failure",
            )
            assertTrue(
                noValueExpando(harness.element),
                "the div carries no expando value property: the host element is never written " +
                    "outside the contract (D-X3)",
            )
            assertEquals(
                attributesBefore,
                attributeCount(harness.element),
                "the element's attributes are exactly what they were before the refused write",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun anOpenInstallsExactlyTheTextListenersAndTheOwnerCloseWithdrawsThem() {
        val harness = WasmTextHarness("input")
        try {
            recordListenerRegistrations(harness.element)
            val owner = assertIs<KadreResult.Success<TextInputOwner>>(harness.portOpen()).value

            assertEquals(
                "beforeinput,compositionstart,compositionupdate,compositionend,keydown",
                registeredListenerTypes(harness.element),
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

    @Test
    fun aRealCancelledCompositionReportsTheRemovalAndTheTerminalEndWithoutACommit() {
        val harness = WasmTextHarness("input", surroundingText = "abc", selection = TextRange(3, 3))
        try {
            harness.open()
            dispatchComposition(harness.element, "compositionstart", data = null)
            dispatchComposition(harness.element, "compositionupdate", data = "かん")
            // Échap: the browser cancelled — no commit ever happened, and the end event carries the
            // empty final string of the withdrawn composition. A real CompositionEvent cannot carry
            // a null `data` at all: Web IDL stringifies `null` to "null", so the empty string is the
            // only honest shape a real DOM event can give a cancellation that changed nothing else.
            dispatchComposition(harness.element, "compositionend", data = "")

            val revision = TextDocumentRevision(7)
            assertEquals(
                listOf<TextInputObservation>(
                    TextInputObservation.CompositionChanged(TextRange(3, 3), "", TextRange(0, 0), revision),
                    TextInputObservation.CompositionChanged(TextRange(3, 3), "かん", TextRange(2, 2), revision),
                    TextInputObservation.Replace(TextRange(3, 5), "", revision),
                    TextInputObservation.CompositionChanged(null, "", null, revision),
                ),
                harness.observed,
                "a cancellation is the browser's own removal of the composed text, reported as the " +
                    "Replace it is, then the terminal observation ends the composition — and the " +
                    "runtime that never accepted the composition refuses the removal by its own " +
                    "range check, so the session keeps the document it always had",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun aTeardownDuringCompositionWithdrawsTheRealListenersAndTheLateCompositionIsNothing() {
        val harness = WasmTextHarness("input", surroundingText = "abc", selection = TextRange(3, 3))
        try {
            recordListenerRegistrations(harness.element)
            val owner = harness.open()
            dispatchComposition(harness.element, "compositionstart", data = null)
            dispatchComposition(harness.element, "compositionupdate", data = "かん")
            assertEquals(2, harness.observed.size, "the composition is in flight when the teardown arrives")

            owner.close()
            assertEquals(
                "",
                listenersWithoutRemoval(harness.element),
                "the teardown withdraws every listener, with the composition mid-flight",
            )

            dispatchComposition(harness.element, "compositionupdate", data = "かんじ")
            dispatchComposition(harness.element, "compositionend", data = "かんじ")
            assertEquals(
                2,
                harness.observed.size,
                "the late composition is nothing: the listeners are gone from the real element",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun aLineBreakBeforeinputOnARealSingleLineInputProducesNothing() {
        val harness = WasmTextHarness("input", surroundingText = "abc", selection = TextRange(3, 3))
        try {
            harness.open()
            dispatchBeforeInput(harness.element, inputType = "insertLineBreak", data = null)
            dispatchBeforeInput(harness.element, inputType = "insertParagraph", data = null)

            assertEquals(
                emptyList<TextInputObservation>(),
                harness.observed,
                "a single-line input cannot perform the line break the event describes: the element " +
                    "kind is the host's boundary, and the port never reports an edit the browser did " +
                    "not perform",
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
private class WasmTextHarness(
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

    val observed: MutableList<TextInputObservation> = mutableListOf()

    val port: WebTextInputPort = WebTextInputPort(
        checkNotNull(WasmWebDomPort(element).textInputElementAccess),
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
@JsFun(
    """(element, inputType, data) => element.dispatchEvent(new InputEvent("beforeinput", {
         inputType: inputType, data: data, bubbles: true, cancelable: true }))""",
)
private external fun dispatchBeforeInput(element: HTMLElement, inputType: String, data: String?)

/** Dispatches a real composition event of [type], with the data a browser would carry. */
@JsFun(
    """(element, type, data) => element.dispatchEvent(new CompositionEvent(type, {
         data: data, bubbles: true, cancelable: true }))""",
)
private external fun dispatchComposition(element: HTMLElement, type: String, data: String?)

/** Dispatches a real keyboard event of [type] on [element], as a browser delivers one. */
@JsFun(
    """(element, type, key) => element.dispatchEvent(new KeyboardEvent(type, {
         key: key, bubbles: true, cancelable: true }))""",
)
private external fun dispatchKey(element: HTMLElement, type: String, key: String)

/** Records every listener registration of [element], added and removed references alike. */
@JsFun(
    """(target) => {
         var log = { added: [], removed: [] };
         target.__kadreTestListenerLog = log;
         var add = target.addEventListener.bind(target);
         var remove = target.removeEventListener.bind(target);
         target.addEventListener = function (type, listener, options) {
           log.added.push([type, listener]);
           return add(type, listener, options);
         };
         target.removeEventListener = function (type, listener, options) {
           log.removed.push([type, listener]);
           return remove(type, listener, options);
         };
       }""",
)
private external fun recordListenerRegistrations(target: JsAny)

/** The event types a listener was registered for on [element], as a comma-joined string. */
@JsFun(
    """(target) => target.__kadreTestListenerLog.added
         .map(function (entry) { return entry[0]; })
         .join(",")""",
)
private external fun registeredListenerTypes(target: HTMLElement): String

/** The registered listeners [element] was never asked to remove, as a comma-joined string of types. */
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
private external fun listenersWithoutRemoval(target: HTMLElement): String

/** Whether [element] took no expando `value` property: the untouched-div proof. */
@JsFun("(element) => Object.getOwnPropertyNames(element).indexOf(\"value\") === -1")
private external fun noValueExpando(element: HTMLElement): Boolean

/** How many attributes [element] carries, so a refused write is provable against its own before. */
@JsFun("(element) => element.attributes.length")
private external fun attributeCount(element: HTMLElement): Int

/** Reads the element's `value` back, as the browser holds it. */
@JsFun("(element) => element.value")
private external fun wasmReadValue(element: JsAny): String

/** Assigns the element's `value`, as a page would. */
@JsFun("(element, value) => { element.value = value; }")
private external fun wasmSetValue(element: JsAny, value: String)

/** The `length` of the element's value, in the code units the browser counts. */
@JsFun("(element) => element.value.length")
private external fun wasmValueLength(element: JsAny): Int

/** The element's `selectionStart`, as the browser holds it. */
@JsFun("(element) => element.selectionStart")
private external fun wasmSelectionStart(element: JsAny): Int

/** The element's `selectionEnd`, as the browser holds it. */
@JsFun("(element) => element.selectionEnd")
private external fun wasmSelectionEnd(element: JsAny): Int
