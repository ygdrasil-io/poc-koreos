package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.input.TextDocumentRevision
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextRange
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.LogicalRect
import org.graphiks.kadre.surface.LogicalSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le shadow document du texte (`UIKitTextInputShadow`), prouvé SANS appareil : le miroir exact
 * d'`AppKitTextInputShadow`/`WebTextInputShadow` adapté aux types foundation — le runtime reste
 * l'AUTORITÉ des révisions, l'ombre n'existe que pour qu'un callback UIKit synchrone observe le
 * document du receiver (texte/sélection/révision acceptées) avant que l'observation filetée ne
 * soit réconciliée.
 *
 * Cibles de mutation : la comparaison de révision d'`applyDocument` (`<` — un `!=` refuserait
 * les snapshots de révision ÉGALE, un absence de garde admettrait un document périmé) ; la
 * garde `CompositionActive` (un changement de texte pendant un marqué — l'ombre mentirait au
 * callback synchrone si elle admettait un texte qui contredit la composition vivante) ; les
 * bornes de `replaceText`/`setMarkedText` (un remplacement hors bornes écrirait hors du
 * document) ; la sélection d'`replaceText` (fin d'insertion — la caret quitterait le texte
 * fraîchement écrit) ; le marquage d'`setMarkedText` (la plage marquée DÉBUTE à
 * `replacementRange.startUtf16`, la sélection y est RELATIVE — l'IME perderait sa sélection) ;
 * le repli `UIKIT_NS_NOT_FOUND` de `resolveRange` (les « pas de plage » UIKit deviendraient des
 * plages fantômes) ; `clearMarkedText` qui doit rapporter FALSE quand rien n'est marqué.
 */
class UIKitTextInputShadowTest {

    private fun shadow(
        text: String = "hello",
        selection: TextRange = TextRange(text.length, text.length),
        revision: Long = 7,
    ) = UIKitTextInputShadow(
        TextInputConfig(
            surroundingText = text,
            selection = selection,
            documentRevision = TextDocumentRevision(revision),
        ),
    )

    private fun rect(x: Double, y: Double) = LogicalRect(LogicalPoint(x, y), LogicalSize(10.0, 4.0))

    // MARK: - État initial

    @Test
    fun initialStateMirrorsTheConfigWithNoCursorAndNoMarkedRange() {
        val shadow = shadow(text = "hello", selection = TextRange(2, 4), revision = 7)
        assertEquals("hello", shadow.text)
        assertEquals(TextRange(2, 4), shadow.selection)
        assertEquals(TextDocumentRevision(7), shadow.documentRevision)
        assertNull(shadow.cursorRect, "no cursor geometry until the application accepts one")
        assertNull(shadow.markedRange, "no composition at the open")
    }

    // MARK: - applyCursor

    @Test
    fun applyCursorAcceptsTheExactRevisionAndStoresTheRect() {
        val shadow = shadow(revision = 7)
        assertTrue(shadow.acceptsCursor(TextDocumentRevision(7)))
        assertTrue(shadow.applyCursor(rect(1.0, 2.0), TextDocumentRevision(7)))
        assertEquals(rect(1.0, 2.0), shadow.cursorRect)
    }

    @Test
    fun applyCursorRefusesAnyOtherRevisionWithoutTouchingTheStoredRect() {
        val shadow = shadow(revision = 7)
        assertTrue(shadow.applyCursor(rect(1.0, 2.0), TextDocumentRevision(7)))
        assertFalse(shadow.acceptsCursor(TextDocumentRevision(8)))
        assertFalse(shadow.applyCursor(rect(9.0, 9.0), TextDocumentRevision(6)),
            "a revision from the past (stale) and from the future (unaccepted) are both refused")
        assertEquals(rect(1.0, 2.0), shadow.cursorRect, "the refused call must not overwrite")
    }

    // MARK: - applyDocument

    @Test
    fun applyDocumentAppliesANewerSnapshot() {
        val shadow = shadow(text = "hello", revision = 7)
        val update = shadow.applyDocument("hello!", TextRange(6, 6), TextDocumentRevision(8))
        assertEquals(UIKitTextInputDocumentUpdate.Applied, update)
        assertEquals("hello!", shadow.text)
        assertEquals(TextRange(6, 6), shadow.selection)
        assertEquals(TextDocumentRevision(8), shadow.documentRevision)
    }

    @Test
    fun applyDocumentAcceptsTheSameRevisionAsAnIdempotentRestate() {
        val shadow = shadow(text = "hello", revision = 7)
        val update = shadow.applyDocument("hello", TextRange(2, 2), TextDocumentRevision(7))
        assertEquals(UIKitTextInputDocumentUpdate.Applied, update,
            "an equal revision is the runtime restating its last accepted snapshot")
        assertEquals(TextRange(2, 2), shadow.selection)
        assertEquals(TextDocumentRevision(7), shadow.documentRevision)
    }

    @Test
    fun applyDocumentAdoptsTheFieldsCommittedViewAtTheSameRevision() {
        // La licence de la resynchronisation du commit IME (fix round 1) : le champ a committé
        // un texte que l'ombre ne porte pas (la composition n'est jamais entrée dans l'ombre en
        // v1) ; à révision ÉGALE et texte DIFFÉRENT, l'ombre adopte la vue committée — la plage
        // honnête du prochain appui se résoudra contre le document que le champ montre vraiment.
        val shadow = shadow(text = "he", selection = TextRange(2, 2), revision = 7)
        val update = shadow.applyDocument("helloé", TextRange(6, 6), TextDocumentRevision(7))
        assertEquals(UIKitTextInputDocumentUpdate.Applied, update,
            "same revision, different text: the field's committed view is adopted, not refused")
        assertEquals("helloé", shadow.text)
        assertEquals(TextRange(6, 6), shadow.selection)
        assertEquals(TextDocumentRevision(7), shadow.documentRevision,
            "the shadow NEVER mints a revision of its own — the runtime stays the authority")
        assertNull(shadow.markedRange, "no composition is created by the reconciliation")
    }

    @Test
    fun applyDocumentRefusesAStaleSnapshotLeavingTheShadowUntouched() {
        val shadow = shadow(text = "hello", revision = 7)
        assertEquals(UIKitTextInputDocumentUpdate.Applied,
            shadow.applyDocument("hello!", TextRange(6, 6), TextDocumentRevision(9)))
        val update = shadow.applyDocument("stale", TextRange(1, 1), TextDocumentRevision(8))
        assertEquals(UIKitTextInputDocumentUpdate.Stale, update)
        assertEquals("hello!", shadow.text, "the stale text must not overwrite")
        assertEquals(TextRange(6, 6), shadow.selection)
        assertEquals(TextDocumentRevision(9), shadow.documentRevision)
    }

    @Test
    fun applyDocumentRefusesATextChangeWhileACompositionIsActive() {
        val shadow = shadow(text = "hello", revision = 7)
        assertTrue(shadow.setMarkedText(TextRange(5, 5), "é", TextRange(1, 1)))
        assertEquals("helloé", shadow.text)
        val update = shadow.applyDocument("application text", TextRange(3, 3), TextDocumentRevision(8))
        assertEquals(UIKitTextInputDocumentUpdate.CompositionActive, update,
            "a text change during a live composition must wait for unmarkText")
        assertEquals("helloé", shadow.text, "the composition's view must survive the refused call")
        assertEquals(TextDocumentRevision(7), shadow.documentRevision)
        assertEquals(TextRange(5, 6), shadow.markedRange)
    }

    @Test
    fun applyDocumentAcceptsASameTextSnapshotWhileACompositionIsActive() {
        val shadow = shadow(text = "hello", revision = 7)
        assertTrue(shadow.setMarkedText(TextRange(5, 5), "é", TextRange(1, 1)))
        val update = shadow.applyDocument("helloé", TextRange(6, 6), TextDocumentRevision(8))
        assertEquals(UIKitTextInputDocumentUpdate.Applied, update,
            "the runtime restating the composition's own view only advances the revision")
        assertEquals(TextDocumentRevision(8), shadow.documentRevision)
        assertEquals(TextRange(5, 6), shadow.markedRange, "the composition is still live")
    }

    // MARK: - replaceText

    @Test
    fun replaceTextReplacesSelectsTheInsertionEndAndClearsTheMarkedRange() {
        val shadow = shadow(text = "hello", revision = 1)
        assertTrue(shadow.setMarkedText(TextRange(5, 5), "é", TextRange(1, 1)),
            "precondition: a composition is live")
        assertTrue(shadow.replaceText(TextRange(2, 4), "X!"))
        assertEquals("heX!oé", shadow.text,
            "the composed text is part of the shadow's text; the replacement only clears the RANGE")
        assertEquals(TextRange(4, 4), shadow.selection,
            "the selection lands at the insertion end (2 + length of 'X!')")
        assertNull(shadow.markedRange, "a replacement ends any composition")
    }

    @Test
    fun replaceTextAtTheEndOfTheDocumentInserts() {
        val shadow = shadow(text = "hello", revision = 1)
        assertTrue(shadow.replaceText(TextRange(5, 5), "!"))
        assertEquals("hello!", shadow.text)
        assertEquals(TextRange(6, 6), shadow.selection)
    }

    @Test
    fun replaceTextRefusesAnOutOfRangeRangeLeavingTheShadowUntouched() {
        val shadow = shadow(text = "hello", revision = 1)
        assertTrue(shadow.setMarkedText(TextRange(0, 0), "É", TextRange(1, 1)),
            "precondition: a composition is live — the text is now 'Éhello' (6 units)")
        assertFalse(shadow.replaceText(TextRange(4, 8), "x"), "end beyond the text length is refused")
        assertFalse(shadow.replaceText(TextRange(9, 9), "x"), "start beyond the text length is refused")
        assertEquals("Éhello", shadow.text)
        assertEquals(TextRange(1, 1), shadow.selection)
        assertEquals(TextRange(0, 1), shadow.markedRange, "a refused replacement keeps the composition")
    }

    // MARK: - setMarkedText

    @Test
    fun setMarkedTextMarksTheSpanAndSelectsWithinIt() {
        val shadow = shadow(text = "hello", revision = 1)
        assertTrue(shadow.setMarkedText(TextRange(1, 3), "ÉÉ", TextRange(1, 2)))
        assertEquals("hÉÉlo", shadow.text)
        assertEquals(TextRange(1, 3), shadow.markedRange,
            "the marked range starts at the replacement start and spans the marked text")
        assertEquals(TextRange(2, 3), shadow.selection,
            "the selection is the requested range mapped INTO the marked span")
    }

    @Test
    fun setMarkedTextAtTheEndOfTheDocumentStartsAComposition() {
        val shadow = shadow(text = "hello", revision = 1)
        assertTrue(shadow.setMarkedText(TextRange(5, 5), "é", TextRange(1, 1)))
        assertEquals("helloé", shadow.text)
        assertEquals(TextRange(5, 6), shadow.markedRange)
        assertEquals(TextRange(6, 6), shadow.selection)
    }

    @Test
    fun setMarkedTextRefusesAnOutOfRangeReplacementLeavingTheShadowUntouched() {
        val shadow = shadow(text = "hello", revision = 1)
        assertFalse(shadow.setMarkedText(TextRange(4, 6), "é", TextRange(0, 0)),
            "replacement beyond the text length is refused")
        assertFalse(shadow.setMarkedText(TextRange(9, 9), "é", TextRange(0, 0)),
            "replacement start beyond the text length is refused")
        assertEquals("hello", shadow.text)
        assertEquals(TextRange(5, 5), shadow.selection)
        assertNull(shadow.markedRange, "a refused marking must not half-apply")
    }

    @Test
    fun setMarkedTextRefusesASelectionBeyondTheMarkedText() {
        val shadow = shadow(text = "hello", revision = 1)
        assertFalse(shadow.setMarkedText(TextRange(0, 0), "a", TextRange(0, 2)),
            "the requested selection must fit within the marked text")
        assertEquals("hello", shadow.text)
        assertNull(shadow.markedRange)
    }

    // MARK: - clearMarkedText

    @Test
    fun clearMarkedTextReportsFalseWhenNothingIsMarked() {
        val shadow = shadow(text = "hello", revision = 1)
        assertFalse(shadow.clearMarkedText(), "no composition, nothing to clear")
        assertNull(shadow.markedRange)
    }

    @Test
    fun clearMarkedTextClearsTheMarkedRangeAndReportsTrueLeavingTheTextCommitted() {
        val shadow = shadow(text = "hello", revision = 1)
        assertTrue(shadow.setMarkedText(TextRange(5, 5), "é", TextRange(1, 1)))
        assertTrue(shadow.clearMarkedText())
        assertNull(shadow.markedRange)
        assertEquals("helloé", shadow.text,
            "unmarkText commits the composed text — the shadow keeps it until reconciled")
        assertEquals(TextRange(6, 6), shadow.selection)
        assertFalse(shadow.clearMarkedText(), "a second clear has nothing left to clear")
    }

    // MARK: - resolveRange

    @Test
    fun resolveRangeMapsARangeWithinTheText() {
        val shadow = shadow(text = "hello", selection = TextRange(1, 1), revision = 1)
        assertEquals(TextRange(0, 3), shadow.resolveRange(UIKitNsRange(0, 3)))
        assertEquals(TextRange(5, 5), shadow.resolveRange(UIKitNsRange(5, 0)))
    }

    @Test
    fun resolveRangeFallsBackToTheSelectionOnNotFound() {
        val shadow = shadow(text = "hello", selection = TextRange(1, 1), revision = 1)
        assertEquals(TextRange(1, 1),
            shadow.resolveRange(UIKitNsRange(UIKIT_NS_NOT_FOUND, 0)),
            "UIKit's 'no range' sentinel means 'the current selection'")
        assertEquals(TextRange(1, 1),
            shadow.resolveRange(UIKitNsRange(UIKIT_NS_NOT_FOUND, 12)),
            "the sentinel wins even with a nonzero length — no phantom range")
    }

    @Test
    fun resolveRangeReturnsNullForOutOfRangeOrInvalidRanges() {
        val shadow = shadow(text = "hello", selection = TextRange(1, 1), revision = 1)
        assertNull(shadow.resolveRange(UIKitNsRange(3, 9)), "end beyond the text")
        assertNull(shadow.resolveRange(UIKitNsRange(9, 0)), "location beyond the text")
        assertNull(shadow.resolveRange(UIKitNsRange(-1, 2)), "negative location is not a range")
        assertNull(shadow.resolveRange(UIKitNsRange(-5, -1)), "negative length is not a range")
    }
}
