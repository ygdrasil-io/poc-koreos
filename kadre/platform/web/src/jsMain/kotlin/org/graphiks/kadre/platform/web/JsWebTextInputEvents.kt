package org.graphiks.kadre.platform.web

import org.w3c.dom.DataTransfer
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import kotlin.js.unsafeCast

/**
 * The DOM half of the JS text-input seam: the [WebTextInputElementAccess] the element's glue lends
 * the port, built over the `<input>` and `<textarea>` the host attached.
 *
 * This is the JS twin of `WasmWebTextInputEvents.kt`, member for member and with the same readings.
 * Nothing here holds a policy: the listeners it installs forward the browser's own facts as
 * primitives — the `inputType` and the text payload of a `beforeinput`, the `data` of a composition
 * event, the key of a `keydown` — and the write-back is the one write the contract allows, guarded
 * by a comparison that keeps an already-equal element (a composition in flight, most notably) from
 * being disturbed. Every read and every write is contained: an element or a browsing context that
 * refuses is answered with `false`, never with an exception, because no DOM call may throw into the
 * port or the page.
 *
 * The `InputEvent` members this seam reads (`inputType`, a nullable `data`, the `dataTransfer` the
 * paste and replacement types keep their payload in) are declared here, the way the pointer-lock
 * seam declared its own gap in the DOM declarations.
 */

/** The members of a `beforeinput` this seam reads, beyond what the declared `InputEvent` carries. */
private external interface JsTextInputEvent {
    val inputType: String?
    val data: String?
    val dataTransfer: DataTransfer?
}

/**
 * The element access of one attached element: kind, listeners, and the value/selection write.
 *
 * The element is captured at construction — the port's own lifetime bounds the access — and every
 * listener is installed on it and removed from it by the session's own open and close, so a page
 * never keeps a listener for a session that closed.
 */
internal class JsWebTextInputElementAccess(element: HTMLElement) : WebTextInputElementAccess {
    private val target: HTMLElement? = element

    /** The element kinds the v1 contract addresses: the two kinds whose value and selection are the document. */
    override val kind: String?
        get() = target?.let { current -> runCatching { current.localName }.getOrNull() }

    private val listeners: MutableList<Pair<String, (Event) -> Unit>> = mutableListOf()

    override fun install(callbacks: WebTextInputCallbacks) {
        val current = target ?: return
        addListener(current, "beforeinput") { event ->
            val input = event.unsafeCast<JsTextInputEvent?>()
            val inputType = runCatching { input?.inputType }.getOrNull()
            val data = runCatching { input?.eventData() }.getOrNull()
            callbacks.onBeforeInput(inputType, data)
        }
        addListener(current, "compositionstart") { _ -> callbacks.onCompositionStart() }
        addListener(current, "compositionupdate") { event ->
            callbacks.onCompositionUpdate(runCatching { event.unsafeCast<JsTextInputEvent?>()?.data }.getOrNull())
        }
        addListener(current, "compositionend") { event ->
            callbacks.onCompositionEnd(runCatching { event.unsafeCast<JsTextInputEvent?>()?.data }.getOrNull())
        }
        addListener(current, "keydown") { event ->
            callbacks.onKeyDown(runCatching { (event as? KeyboardEvent)?.key }.getOrNull())
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
            val input = current as? HTMLInputValue
            val area = current as? HTMLTextAreaValue
            when {
                input != null -> {
                    if (input.value != text) input.value = text
                    if (input.selectionStart != selectionStart || input.selectionEnd != selectionEnd) {
                        input.setSelectionRange(selectionStart, selectionEnd)
                    }
                }

                area != null -> {
                    if (area.value != text) area.value = text
                    if (area.selectionStart != selectionStart || area.selectionEnd != selectionEnd) {
                        area.setSelectionRange(selectionStart, selectionEnd)
                    }
                }

                else -> return@runCatching false
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
    private fun JsTextInputEvent.eventData(): String? =
        data ?: runCatching { dataTransfer?.getData("text/plain") }.getOrNull()
}

/** The value and selection members of an `<input>` this seam writes, as the DOM declares them. */
private external interface HTMLInputValue {
    var value: String
    val selectionStart: Int?
    val selectionEnd: Int?
    fun setSelectionRange(start: Int, end: Int)
}

/** The same members of a `<textarea>`, the other element the write-back addresses. */
private external interface HTMLTextAreaValue {
    var value: String
    val selectionStart: Int?
    val selectionEnd: Int?
    fun setSelectionRange(start: Int, end: Int)
}
