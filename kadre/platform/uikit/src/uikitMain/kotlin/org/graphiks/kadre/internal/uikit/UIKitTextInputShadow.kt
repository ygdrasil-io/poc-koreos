package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.input.TextDocumentRevision
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextRange
import org.graphiks.kadre.surface.LogicalRect

/**
 * La forme PURE de `NSRange` — les champs `location`/`length` d'UIKit (`NSUInteger`, entiers non
 * signés côté klib) ramenés en entiers signés Kotlin. Le port (`UIKitTextInputPort`, Task 2)
 * convertit le `NSRange` du klib en cette valeur AVANT d'interroger l'ombre : le shadow reste
 * pur (zéro UIKit), compilable et testable sans appareil.
 */
internal data class UIKitNsRange(public val location: Long, public val length: Long)

/** Le sentinel UIKit « pas de plage » — le miroir d'`APPKIT_NS_NOT_FOUND` (AppKit, même valeur). */
internal const val UIKIT_NS_NOT_FOUND = Long.MAX_VALUE

/**
 * Immediate Kotlin shadow of the text storage UIKit queries from the receiver.
 *
 * The portable runtime remains authoritative for revisions. This shadow exists so a synchronous
 * UIKit callback always observes the previous insert/marked-text change before the queued
 * observation is reconciled by the runtime. (Miroir verbatim d'`AppKitTextInputShadow` —
 * backend/appkit, `KffiAppKitWindowPort.kt` — adapté aux types foundation ; le document est
 * tenu par le spike du receiver : `UITextField`, les deux familles, preuve driver
 * `KadreUikitTextInputSpikeTests`.)
 */
internal class UIKitTextInputShadow(config: TextInputConfig) {
    var text: String = config.surroundingText
        private set
    var selection: TextRange = config.selection
        private set
    var documentRevision: TextDocumentRevision = config.documentRevision
        private set
    var cursorRect: LogicalRect? = null
        private set
    var markedRange: TextRange? = null
        private set

    fun acceptsCursor(revision: TextDocumentRevision): Boolean = revision == documentRevision

    fun applyCursor(rect: LogicalRect, revision: TextDocumentRevision): Boolean {
        if (!acceptsCursor(revision)) return false
        cursorRect = rect
        return true
    }

    fun applyDocument(
        text: String,
        selection: TextRange,
        revision: TextDocumentRevision,
    ): UIKitTextInputDocumentUpdate {
        if (revision.value < documentRevision.value) return UIKitTextInputDocumentUpdate.Stale
        if (markedRange != null && text != this.text) return UIKitTextInputDocumentUpdate.CompositionActive
        this.text = text
        this.selection = selection
        documentRevision = revision
        return UIKitTextInputDocumentUpdate.Applied
    }

    fun resolveRange(nsRange: UIKitNsRange): TextRange? =
        nsRange.toTextRangeOrNull(text) ?: selection.takeIf { nsRange.location == UIKIT_NS_NOT_FOUND }

    fun replaceText(range: TextRange, replacement: String): Boolean {
        if (!range.isWithin(text)) return false
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
        if (!replacementRange.isWithin(text) || !selectedRange.isWithin(markedText)) return false
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

/** Result of reconciling a runtime snapshot with the synchronous UIKit text shadow. */
internal enum class UIKitTextInputDocumentUpdate {
    Applied,
    Stale,
    CompositionActive,
    Closed,
}

private fun UIKitNsRange.toTextRangeOrNull(text: String): TextRange? {
    if (location == UIKIT_NS_NOT_FOUND || location < 0L || length < 0L) return null
    val end = location + length
    if (end < location || end > text.length.toLong()) return null
    return TextRange(location.toInt(), end.toInt())
}

private fun TextRange.isWithin(text: String): Boolean = endExclusiveUtf16 <= text.length

private fun String.replace(range: TextRange, replacement: String): String =
    substring(0, range.startUtf16) + replacement + substring(range.endExclusiveUtf16)
