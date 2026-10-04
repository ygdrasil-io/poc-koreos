@file:OptIn(ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.platform.web

import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

/**
 * The DOM half of the Wasm text-input seam: the [WebTextInputElementAccess] the element's glue lends
 * the port, built over the `<input>` and `<textarea>` the host attached.
 *
 * This is the Wasm twin of `JsWebTextInputEvents.kt`, member for member and with the same readings:
 * nothing here holds a policy, and nothing here decides how a browser fact becomes a model value.
 * What is target-specific here is only the way the reading is expressed — a browser event crosses
 * into Kotlin/Wasm as `JsAny`, so the members this seam reads beyond the declared DOM bindings
 * (`inputType`, the nullable `data`, the data-transfer payload of the paste and replacement types)
 * are declared as one `external interface` the borrowed event is cast to, the way the input port
 * declares the events it reads.
 *
 * Every read and every write is contained: an element or a browsing context that refuses is answered
 * with `false` or a `null` payload, never with an exception, because no DOM call may throw into the
 * port or the page.
 */

/** The members of a `beforeinput` this seam reads, beyond what the declared DOM bindings carry. */
internal external interface WasmTextInputEvent : JsAny {
    val inputType: String?
    val data: String?
}

/** The value and selection members of an `<input>` or a `<textarea>` this seam writes. */
internal external interface WasmInputValue : JsAny {
    var value: String
    fun setSelectionRange(start: Int, end: Int)
}

/**
 * The element access of one attached element: kind, listeners, and the value/selection write.
 *
 * The element is captured at construction — the port's own lifetime bounds the access — and every
 * listener is installed on it and removed from it by the session's own open and close, so a page
 * never keeps a listener for a session that closed.
 */
internal class WasmWebTextInputElementAccess(element: HTMLElement) : WebTextInputElementAccess {
    private val target: HTMLElement? = element

    /** The element kinds the v1 contract addresses: the two kinds whose value and selection are the document. */
    override val kind: String?
        get() = target?.let { current -> runCatching { wasmLocalName(current) }.getOrNull() }

    private val listeners: MutableList<Pair<String, (Event) -> Unit>> = mutableListOf()

    override fun install(callbacks: WebTextInputCallbacks) {
        val current = target ?: return
        addListener(current, "beforeinput") { event ->
            val input = runCatching { wasmInputEventOrNull(event) }.getOrNull()
            callbacks.onBeforeInput(
                runCatching { input?.inputType }.getOrNull(),
                runCatching { input?.payload() }.getOrNull(),
            )
        }
        addListener(current, "compositionstart") { _ -> callbacks.onCompositionStart() }
        addListener(current, "compositionupdate") { event ->
            callbacks.onCompositionUpdate(runCatching { wasmEventDataOrNull(event) }.getOrNull())
        }
        addListener(current, "compositionend") { event ->
            callbacks.onCompositionEnd(runCatching { wasmEventDataOrNull(event) }.getOrNull())
        }
        addListener(current, "keydown") { event ->
            callbacks.onKeyDown(runCatching { wasmKeyOfOrNull(event) }.getOrNull())
        }
    }

    override fun withdraw() {
        val current = target ?: return
        listeners.forEach { (type, listener) ->
            runCatching { current.removeEventListener(type, listener) }
        }
        listeners.clear()
    }

    override fun writeDocument(text: String, selectionStart: Int, selectionEnd: Int): Boolean {
        val current = target ?: return false
        return runCatching {
            val writable = wasmWritableElementOrNull(current) ?: return@runCatching false
            if (wasmReadValue(current) != text) wasmWriteValue(current, text)
            if (wasmSelectionStart(current) != selectionStart || wasmSelectionEnd(current) != selectionEnd) {
                writable.setSelectionRange(selectionStart, selectionEnd)
            }
            true
        }.getOrDefault(false)
    }

    private fun addListener(current: HTMLElement, type: String, listener: (Event) -> Unit) {
        current.addEventListener(type, listener)
        listeners += type to listener
    }

    /**
     * The text payload of one `beforeinput`: the event's own `data`, or the plain text its data
     * transfer carries — the paste, drop and replacement types keep their insertion there and carry
     * a null `data`, and the edit they describe is exactly that text.
     */
    private fun WasmTextInputEvent.payload(): String? = data ?: wasmDataTransferText(this)
}

/** The borrowed event as a text input event, or `null` when the read refuses. */
@JsFun("(event) => event instanceof InputEvent ? event : null")
internal external fun wasmInputEventOrNull(event: Event): WasmTextInputEvent?

/** The `data` member of a composition event, the composed text it reports. */
@JsFun("(event) => event.data")
internal external fun wasmEventDataOrNull(event: Event): String?

/** The key name of one keyboard event, or `null` when it is not one. */
@JsFun("(event) => event instanceof KeyboardEvent ? event.key : null")
internal external fun wasmKeyOfOrNull(event: Event): String?

/** The element's own kind name, as the browser reports it. */
@JsFun("(element) => element.localName")
internal external fun wasmLocalName(element: HTMLElement): String

/** The element as the writable value/selection surface, or `null` when it is not one. */
@JsFun(
    "(element) => (element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement) ? element : null",
)
internal external fun wasmWritableElementOrNull(element: HTMLElement): WasmInputValue?

/** Reads the element's `value` back, as the browser holds it. */
@JsFun("(element) => element.value")
internal external fun wasmReadValue(element: HTMLElement): String

/** Assigns the element's `value`, as a page would. */
@JsFun("(element, value) => { element.value = value; }")
internal external fun wasmWriteValue(element: HTMLElement, value: String)

/** The element's `selectionStart`, as the browser holds it. */
@JsFun("(element) => element.selectionStart")
internal external fun wasmSelectionStart(element: HTMLElement): Int

/** The element's `selectionEnd`, as the browser holds it. */
@JsFun("(element) => element.selectionEnd")
internal external fun wasmSelectionEnd(element: HTMLElement): Int

/** The plain text of the event's data transfer, or `null` when it carries none. */
@JsFun(
    """(event) => {
         if (event.dataTransfer === null || event.dataTransfer === undefined) return null;
         try { return event.dataTransfer.getData("text/plain") || null; } catch (e) { return null; }
       }""",
)
internal external fun wasmDataTransferText(event: JsAny): String?
