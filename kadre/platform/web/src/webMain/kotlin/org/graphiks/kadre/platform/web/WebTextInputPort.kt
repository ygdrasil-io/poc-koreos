package org.graphiks.kadre.platform.web

import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.TextDocumentRevision
import org.graphiks.kadre.input.TextInputAction
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextRange
import org.graphiks.kadre.internal.runtime.TextInputCursorCommand
import org.graphiks.kadre.internal.runtime.TextInputDocumentCommand
import org.graphiks.kadre.internal.runtime.TextInputObservation
import org.graphiks.kadre.internal.runtime.TextInputOpenCommand
import org.graphiks.kadre.internal.runtime.TextInputOwner
import org.graphiks.kadre.internal.runtime.TextInputPort
import org.graphiks.kadre.surface.LogicalRect

/** The domain of the platform failures the text-input seam reports; `OPERATION-CONTRACTS.md` §7. */
internal const val TEXT_INPUT_SEAM_DOMAIN = "text-input"

/** The code of the one failure a refused write-back produces: the element could not be written. */
internal const val TEXT_INPUT_WRITE_BACK_CODE = "write-back-failed"

/** The code of the one failure a refused installation produces: the listeners could not be added. */
internal const val TEXT_INPUT_INSTALL_CODE = "install-listeners-failed"

/** The element kind of a single-line input, the one the v1 contract addresses besides a textarea. */
internal const val WEB_TEXT_INPUT_ELEMENT = "input"

/** The element kind of a multiline textarea, the other element the v1 contract addresses (D-X3). */
internal const val WEB_TEXT_INPUT_TEXTAREA = "textarea"

/**
 * The DOM-free seam between the text-input port and the attached element.
 *
 * The port's body lives in `webMain`, where no DOM type may appear; the targets implement this
 * interface over the element they hold, and everything that crosses it is a primitive: the element
 * kind the browser reports, the observation listeners a session installs, and the value/selection
 * write of the contract (D-X3). `kind` is the element's own name — `input` or `textarea` for the
 * elements the v1 contract addresses, anything else for one it does not, which is the D-X2 boundary:
 * the host owns the editability, and an element outside the contract's scope simply produces no
 * observations, never a refusal of the structural capability.
 */
internal interface WebTextInputElementAccess {
    /** The element kind the browser reports, or `null` when there is no element to read one from. */
    val kind: String?

    /**
     * Installs the observation listeners of one session on the element.
     *
     * Called once per open, by an addressable owner only; every listener [install] adds goes with the
     * one [withdraw].
     */
    fun install(callbacks: WebTextInputCallbacks)

    /** Removes every listener [install] added. Idempotent, and safe with no installation in hand. */
    fun withdraw()

    /**
     * Applies the document snapshot and the selection to the element: the contract's own write-back
     * (D-X3), the only write Kadre makes to the attached element. Answers whether the element took it.
     */
    fun writeDocument(text: String, selectionStart: Int, selectionEnd: Int): Boolean

    /** The access of a surface whose target lends no element: the port opens and observes nothing. */
    companion object None : WebTextInputElementAccess {
        override val kind: String? = null

        override fun install(callbacks: WebTextInputCallbacks) = Unit

        override fun withdraw() = Unit

        override fun writeDocument(text: String, selectionStart: Int, selectionEnd: Int): Boolean = false
    }
}

/**
 * The observation callbacks one session installs on the element, as the target's glue fills them
 * from the browser events it reads: a `beforeinput` with its `inputType` and the text payload it
 * carries (read from `dataTransfer` for the types that keep it there), a composition start, update
 * or end with the composed text, and a key name for the submission keys.
 */
internal class WebTextInputCallbacks(
    internal val onBeforeInput: (inputType: String?, data: String?) -> Unit,
    internal val onCompositionStart: () -> Unit,
    internal val onCompositionUpdate: (data: String?) -> Unit,
    internal val onCompositionEnd: (data: String?) -> Unit,
    internal val onKeyDown: (key: String?) -> Unit,
)

/**
 * The Web text-input port: one shadow document, the DOM observations computed against it, and the
 * write-back that keeps the element and the application the same document (plan decision D-X1).
 *
 * The shadow (`WebTextInputShadow`, the precedent of `AppKitTextInputShadow`) is initialised at the
 * open from the config — surrounding text, selection and revision — without writing the element, and
 * it is the only source of offsets this port ever computes from: a `beforeinput` is translated into
 * the edit its `inputType` describes against the shadow's text and selection, a composition is the
 * range the composition replaces plus the text that sits there, and every observation is stamped with
 * the shadow's revision — the application's last accepted one — never a revision of its own. The
 * write-back applies an accepted snapshot to the element and to the shadow together, the write first
 * so an observation the element itself provokes is still computed against the revision the runtime
 * can admit; `updateCursor` is accepted and stored with no browser effect — the browser draws its own
 * caret (recorded limit). The capability is structural (`Capability.Supported`, D-X2): a
 * non-editable element opens a session that observes nothing, because editability is the host's
 * boundary, not a capability of this port's to promise or refuse.
 */
internal class WebTextInputPort(
    private val access: WebTextInputElementAccess,
) : TextInputPort {
    override val capability: Capability<Unit> = Capability.Supported(Unit, FeatureAvailability.Available)

    /** The one owner this port opened. The runtime admits one session per surface before it calls. */
    private var liveOwner: WebTextInputOwner? = null

    override fun open(command: TextInputOpenCommand): KadreResult<TextInputOwner> {
        liveOwner?.let { return KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.TextInputSession)) }
        val owner = WebTextInputOwner(access, command.config, command.onObservation) { closed ->
            if (liveOwner === closed) liveOwner = null
        }
        // The installation is the open's one DOM effect, and like every DOM effect of this seam it is
        // contained: an element that refuses its listeners is the platform failure of the seam, never
        // an exception thrown into the runtime call. The half-installed owner is closed — the
        // withdrawal takes whatever listeners did land, and the close callback is a no-op because the
        // owner was never live.
        val installed = runCatching { owner.install() }
        if (installed.isFailure) {
            owner.close()
            return KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.Web, TEXT_INPUT_SEAM_DOMAIN, TEXT_INPUT_INSTALL_CODE),
            )
        }
        liveOwner = owner
        return KadreResult.Success(owner)
    }

    override suspend fun updateCursor(command: TextInputCursorCommand): KadreResult<Unit> {
        val owner = command.owner as? WebTextInputOwner
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("textInputOwner"))
        if (liveOwner !== owner || owner.isClosed) return textInputClosedFailure()
        return owner.applyCursor(command.rect, command.documentRevision)
    }

    override suspend fun updateDocument(command: TextInputDocumentCommand): KadreResult<Unit> {
        val owner = command.owner as? WebTextInputOwner
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("textInputOwner"))
        if (liveOwner !== owner || owner.isClosed) return textInputClosedFailure()
        return owner.applyDocument(command.text, command.selection, command.documentRevision)
    }

    private fun textInputClosedFailure(): KadreResult.Failure =
        KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession))
}

/**
 * The one live text session this port owns: the shadow, the element listeners and the observation
 * publication of one `open`.
 *
 * Every DOM callback runs contained: an exception a handler throws never escapes into the page, and
 * it closes the owner, because a shadow whose handler failed is a shadow whose offsets can no longer
 * be trusted — the session answers `Closed` from that moment on, the failure the contract promises.
 */
internal class WebTextInputOwner(
    private val access: WebTextInputElementAccess,
    private val config: TextInputConfig,
    private val onObservation: (TextInputObservation) -> Boolean,
    private val onClosed: (WebTextInputOwner) -> Unit,
) : TextInputOwner {
    private val shadow = WebTextInputShadow(config)

    /** Whether the element is one the v1 contract addresses; anything else observes nothing (D-X2). */
    private val addressable: Boolean = access.kind == WEB_TEXT_INPUT_ELEMENT || access.kind == WEB_TEXT_INPUT_TEXTAREA

    /** Whether the element is a multiline one, whose submission key is a line break and not an action. */
    private val multilineElement: Boolean = access.kind == WEB_TEXT_INPUT_TEXTAREA

    private var closed = false

    val isClosed: Boolean get() = closed

    /**
     * Installs the observation listeners on the element — and nothing at all on a non-addressable
     * one, where observing nothing is the boundary the capability does not lie about (D-X2).
     */
    fun install() {
        if (!addressable || closed) return
        access.install(
            WebTextInputCallbacks(
                onBeforeInput = { inputType, data -> safely { onBeforeInput(inputType, data) } },
                onCompositionStart = { safely { onCompositionStart() } },
                onCompositionUpdate = { data -> safely { onCompositionUpdate(data) } },
                onCompositionEnd = { data -> safely { onCompositionEnd(data) } },
                onKeyDown = { key -> safely { onKeyDown(key) } },
            ),
        )
    }

    override fun close() {
        if (closed) return
        closed = true
        access.withdraw()
        onClosed(this)
    }

    /** Accepts the cursor rect into the shadow at the exact current revision; no browser effect. */
    fun applyCursor(rect: LogicalRect, revision: TextDocumentRevision): KadreResult<Unit> =
        if (shadow.applyCursor(rect, revision)) {
            KadreResult.Success(Unit)
        } else {
            KadreResult.Failure(
                KadreFailure.StaleRevision(shadow.documentRevision.value, revision.value),
            )
        }

    /**
     * Applies one accepted snapshot: the element first, the shadow second.
     *
     * The write is the contract's own (D-X3) and it comes first so an observation the element itself
     * provokes while it is being written is computed against the revision the runtime can still admit
     * — the shape its non-reconciliable guard exists for. The shadow's own application is the
     * AppKit precedent's; its refusals (`Stale`, `CompositionActive`) are unreachable behind the
     * runtime's own admission, and answered with the failures that name them.
     *
     * An element the v1 contract does not address cannot carry the write-back at all, and the
     * write-back *is* the contract: the session stops here — the owner closes, so no later command
     * and no late observation pretend it still serves — and answers the closed failure, rather than
     * writing a non-contract property onto the host's element (D-X2 permits the open; nothing
     * licenses the write).
     */
    fun applyDocument(
        text: String,
        selection: TextRange,
        revision: TextDocumentRevision,
    ): KadreResult<Unit> {
        if (!addressable) {
            close()
            return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession))
        }
        if (!access.writeDocument(text, selection.startUtf16, selection.endExclusiveUtf16)) {
            return KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.Web, TEXT_INPUT_SEAM_DOMAIN, TEXT_INPUT_WRITE_BACK_CODE),
            )
        }
        return when (shadow.applyDocument(text, selection, revision)) {
            WebTextInputDocumentUpdate.Applied -> KadreResult.Success(Unit)
            WebTextInputDocumentUpdate.Stale -> KadreResult.Failure(
                KadreFailure.StaleRevision(shadow.documentRevision.value, revision.value),
            )

            WebTextInputDocumentUpdate.CompositionActive -> KadreResult.Failure(KadreFailure.InvalidRequest("text"))
        }
    }

    /**
     * One `beforeinput`: the edit its `inputType` describes, computed against the shadow.
     *
     * The types carried here are the ones a shadow can compute honestly: the insertions whose text
     * the event (or its data transfer) names — the range is the selection, the replacement is the
     * payload — the cut, whose range is the whole selection and so needs no boundary of its own, the
     * line breaks a multiline element performs, and the tab the AppKit mapping turns into the next
     * action. The delete types (`deleteContentBackward`, `deleteContentForward`) are **not** mapped:
     * a delete removes a grapheme cluster — backspace over `😀` removes the whole surrogate pair,
     * over a decomposed `é` the base and its combining mark — and the DOM names no cluster boundary
     * without the Segmentation API Kadre does not embed. Computing the one-code-unit edit a shadow
     * can reach would report deleting half a character: the observation would pass the runtime's
     * range check and still be a lie about the document, silently splitting astral characters. They
     * therefore produce nothing, like every other type this mapping cannot compute; the next
     * accepted write-back rewrites the element and restores the agreement.
     *
     * A composition owns its own edits — every `beforeinput` inside one, the `insertCompositionText`
     * of the engines that pair it with the composition events, is the composition's fact and is
     * never an observation of its own.
     */
    private fun onBeforeInput(inputType: String?, data: String?) {
        if (!addressable || closed || shadow.markedRange != null) return
        when (inputType) {
            "insertText", "insertReplacementText", "insertFromPaste", "insertFromDrop" -> {
                val inserted = data ?: return
                val range = shadow.selection
                if (shadow.replaceText(range, inserted)) {
                    publish(TextInputObservation.Replace(range, inserted, shadow.documentRevision))
                }
            }

            "insertLineBreak", "insertParagraph" -> {
                // The element kind is the host's boundary (D-X2): only a multiline element can perform
                // the line break this event describes, so a single-line one reports nothing — an edit
                // the browser cannot have performed is a lie about the document, and the submission
                // action of a single-line element stays with the keydown. On a multiline element the
                // text fact is reported as the Replace it is (the suppression policy is the surface's,
                // and under the default nothing is suppressed), and the AppKit action mapping follows.
                if (!multilineElement) return
                val range = shadow.selection
                if (shadow.replaceText(range, "\n")) {
                    publish(TextInputObservation.Replace(range, "\n", shadow.documentRevision))
                    publish(TextInputObservation.Action(config.action, shadow.documentRevision))
                }
            }

            "insertTab" ->
                // The AppKit mapping: `insertTab:` is the next action. No v1 element the contract
                // addresses performs it, but a browser that delivers one finds the answer here.
                publish(TextInputObservation.Action(TextInputAction.Next, shadow.documentRevision))

            "deleteByCut" -> {
                val selection = shadow.selection
                if (selection.startUtf16 == selection.endExclusiveUtf16) return
                if (shadow.replaceText(selection, "")) {
                    publish(TextInputObservation.Replace(selection, "", shadow.documentRevision))
                }
            }

            else -> return
        }
    }

    /**
     * One `compositionstart`: the composition opens at the shadow's selection, with an empty text.
     *
     * The event carries no data yet, so the opening observation is the empty substitution of the
     * range the composition will fill — the selection the shadow holds — and the selection inside
     * that empty text is the empty one.
     */
    private fun onCompositionStart() {
        if (!addressable || closed || shadow.markedRange != null) return
        val range = shadow.selection
        if (shadow.setMarkedText(range, "", TextRange(0, 0))) {
            publish(TextInputObservation.CompositionChanged(range, "", TextRange(0, 0), shadow.documentRevision))
        }
    }

    /**
     * One `compositionupdate`: the composed text replaced the previous composition.
     *
     * The observation's range is the range in the current document that the new text replaces — the
     * previous composition's span, which is what the application's accepted substitution must
     * replace to reach the new snapshot the runtime rebases to. The selection inside the composed
     * text is its end: the caret the engines put there, and never a fabricated selection of the
     * document (DESIGN §10.3 — a pre-edition is never a `SelectionChanged`).
     */
    private fun onCompositionUpdate(data: String?) {
        if (!addressable || closed) return
        val previous = shadow.markedRange ?: return
        val composed = data ?: return
        val selection = TextRange(composed.length, composed.length)
        if (shadow.setMarkedText(previous, composed, selection)) {
            publish(TextInputObservation.CompositionChanged(previous, composed, selection, shadow.documentRevision))
        }
    }

    /**
     * One `compositionend`: the composition terminates.
     *
     * A final string that differs from the composed text the shadow holds is the browser's own last
     * edit — committed into the element before this event — and it is reported as the `Replace` it
     * is before the terminal observation ends the composition. A final string that is the composed
     * one (the common shape), or no data at all, changes nothing and only terminates.
     */
    private fun onCompositionEnd(data: String?) {
        if (!addressable || closed) return
        val marked = shadow.markedRange ?: return
        val final = data
        if (final != null && final != shadow.composedText) {
            if (shadow.replaceText(marked, final)) {
                publish(TextInputObservation.Replace(marked, final, shadow.documentRevision))
            }
        } else {
            shadow.clearMarkedText()
        }
        publish(TextInputObservation.CompositionChanged(null, "", null, shadow.documentRevision))
    }

    /**
     * One `keydown` the session listens for: the submission keys only, and no text ever (DESIGN
     * `:1219`). Enter on a single-line element is the browser's submission key — the element fires no
     * `beforeinput` for it, so this is the one fact it produces — and on a multiline element it is a
     * line break the `beforeinput` owns, which is why the keydown is silent there.
     */
    private fun onKeyDown(key: String?) {
        if (!addressable || closed || multilineElement) return
        if (key == "Enter") {
            publish(TextInputObservation.Action(config.action, shadow.documentRevision))
        }
    }

    /** Publishes one observation, until the owner closed. */
    private fun publish(observation: TextInputObservation) {
        if (closed) return
        onObservation(observation)
    }

    /** Contains one DOM callback: a throwing handler closes the owner instead of the page. */
    private fun safely(block: () -> Unit) {
        if (closed) return
        try {
            block()
        } catch (cause: Throwable) {
            close()
        }
    }
}

/** The result of reconciling a runtime snapshot with the shadow, the AppKit precedent's own. */
internal enum class WebTextInputDocumentUpdate {
    Applied,
    Stale,
    CompositionActive,
}

/**
 * The immediate Kotlin shadow of the document the element holds, computed from the config at the
 * open and advanced by the edits this port observes and the snapshots the application accepts.
 *
 * The portable runtime remains authoritative for revisions: `documentRevision` is the application's
 * last accepted one, and every observation is stamped with it. The text is the view the element
 * shows — the application's document with the composition, when one is active, applied — and the
 * offsets of every observation are UTF-16 code units of this text, the only source this port
 * computes from (D-X1). A caret the user moves without editing is not tracked: the shadow follows
 * the config, the edits and the accepted snapshots, and the next write-back restores the agreement.
 */
internal class WebTextInputShadow(config: TextInputConfig) {
    var text: String = config.surroundingText
        private set

    var selection: TextRange = config.selection
        private set

    var documentRevision: TextDocumentRevision = config.documentRevision
        private set

    /** The cursor rect the application last accepted, stored with no browser effect (recorded limit). */
    var cursorRect: LogicalRect? = null
        private set

    /** The composition's span in the text, or `null` when no composition is active. */
    var markedRange: TextRange? = null
        private set

    /** The text the composition currently holds, or `null` when none is active. */
    val composedText: String?
        get() = markedRange?.let { range -> text.substring(range.startUtf16, range.endExclusiveUtf16) }

    fun applyCursor(rect: LogicalRect, revision: TextDocumentRevision): Boolean {
        if (revision != documentRevision) return false
        cursorRect = rect
        return true
    }

    fun applyDocument(
        text: String,
        selection: TextRange,
        revision: TextDocumentRevision,
    ): WebTextInputDocumentUpdate {
        if (revision.value < documentRevision.value) return WebTextInputDocumentUpdate.Stale
        if (markedRange != null && text != this.text) return WebTextInputDocumentUpdate.CompositionActive
        this.text = text
        this.selection = selection
        documentRevision = revision
        return WebTextInputDocumentUpdate.Applied
    }

    fun replaceText(range: TextRange, replacement: String): Boolean {
        if (range.endExclusiveUtf16 > text.length) return false
        text = text.replace(range, replacement)
        val insertion = range.startUtf16 + replacement.length
        selection = TextRange(insertion, insertion)
        markedRange = null
        return true
    }

    fun setMarkedText(
        replacementRange: TextRange,
        markedText: String,
        selectedRange: TextRange,
    ): Boolean {
        if (replacementRange.endExclusiveUtf16 > text.length) return false
        if (selectedRange.endExclusiveUtf16 > markedText.length) return false
        text = text.replace(replacementRange, markedText)
        val markedStart = replacementRange.startUtf16
        markedRange = TextRange(markedStart, markedStart + markedText.length)
        selection = TextRange(
            markedStart + selectedRange.startUtf16,
            markedStart + selectedRange.endExclusiveUtf16,
        )
        return true
    }

    fun clearMarkedText(): Boolean {
        if (markedRange == null) return false
        markedRange = null
        return true
    }
}

/** Replaces [range] with [replacement] in this string, by UTF-16 offsets. */
private fun String.replace(range: TextRange, replacement: String): String =
    substring(0, range.startUtf16) + replacement + substring(range.endExclusiveUtf16)
