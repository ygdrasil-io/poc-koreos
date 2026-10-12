package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Les deux routes pures de la Task 3 qui n'ont pas de miroir web :
 *
 * - **`uikitKeyboardPhysicalKey(raw)`** : la paire HID d'une touche GameController. Le
 *   `GCKeyCode` du handler arrive en `Long?` brut du klib (CFIndex, NS_TYPED_ENUM) ; son
 *   énumération (GCKeyCodes.h, 137 constantes) EST la table HID de la page Keyboard/Keypad
 *   (0x07) — noms pour noms, des touches A..Z (usages 0x04..) aux modificateurs gauche/droite
 *   (0xE0..0xE7), en passant par le keypad (0x53..) et International/LANG — vérifié dans le
 *   SDK iPhoneOS27.0. Nommer la page 0x07 PORTE donc l'invariant documenté de la source, il
 *   n'invente RIEN ; l'usage, lui, est porté tel quel. Un brut hors du domaine 0..65535 (ou
 *   absent) n'est pas une paire HID : `Unidentified(null)`, jamais un `toInt()` tronqué.
 *   Cible de mutation : le `toInt()` silencieux d'un `Long` hors Int (truncation), ou une
 *   page forcée à autre chose que 0x07.
 *
 * - **`uikitHoverDelta(previous, current)`** : la delta d'un mouvement de survol. Le stimulus
 *   `PointerMoved` du runtime EXIGE une `LogicalDelta` non nulle — la delta du survol est
 *   l'ARITHMÉTIQUE de deux observations consécutives (courant − précédent), jamais un fait
 *   inventé. Un mouvement sans précédent (l'entrée a été perdue) n'a pas de delta honnête :
 *   l'observation est perdue (`null`), jamais une zéro fabriquée.
 */
class UIKitKeyboardHoverMappingTest {
    // MARK: - uikitKeyboardPhysicalKey

    @Test
    fun keyboardUsageCarriesTheKeyboardPageAndTheRawUsage() {
        // GCKeyCodeKeyA : usage 0x04 de la page 0x07 (la première touche lettre de GCKeyCodes.h).
        assertEquals(PhysicalCode(page = 0x07, usage = 0x04), uikitKeyboardPhysicalKey(0x04L).asCode())
        // Une flèche (fin de la table principale) et un modificateur (0xE0..0xE7) : même page.
        assertEquals(PhysicalCode(page = 0x07, usage = 0x52), uikitKeyboardPhysicalKey(0x52L).asCode())
        assertEquals(PhysicalCode(page = 0x07, usage = 0xE0), uikitKeyboardPhysicalKey(0xE0L).asCode())
    }

    @Test
    fun keyboardDomainBoundariesAreCodes() {
        assertEquals(PhysicalCode(page = 0x07, usage = 0), uikitKeyboardPhysicalKey(0L).asCode())
        assertEquals(PhysicalCode(page = 0x07, usage = 65_535), uikitKeyboardPhysicalKey(65_535L).asCode())
    }

    @Test
    fun keyboardOutOfRangeRawValuesAreNeverTruncated() {
        // La cible de mutation : un `toInt()` silencieux — 65_536 deviendrait 65_536 ou 0 selon
        // la route ; MIN/MAX tronqueraient. Tout hors-domaine est `Unidentified(null)`.
        assertEquals(uikitKeyboardPhysicalKey(65_536L), unidentifiedPhysical())
        assertEquals(uikitKeyboardPhysicalKey((-1L)), unidentifiedPhysical())
        assertEquals(uikitKeyboardPhysicalKey(Long.MAX_VALUE), unidentifiedPhysical())
        assertEquals(uikitKeyboardPhysicalKey(Long.MIN_VALUE), unidentifiedPhysical())
        assertEquals(uikitKeyboardPhysicalKey(4_294_967_297L), unidentifiedPhysical())
    }

    @Test
    fun keyboardAbsentRawValueIsLost() {
        assertEquals(unidentifiedPhysical(), uikitKeyboardPhysicalKey(null))
    }

    @Test
    fun keyboardPageIsTheKeyboardKeypadPageNeverAnother() {
        val key = uikitKeyboardPhysicalKey(0x2CL) // GCKeyCodeSpacebar
        val code = key.asCode()
        assertEquals(0x07, code?.page, "the GCKeyCode enumeration IS the page 0x07 table")
        assertEquals(0x2C, code?.usage)
    }

    // MARK: - uikitHoverDelta

    @Test
    fun hoverDeltaIsTheArithmeticOfTwoConsecutiveObservations() {
        assertEquals(
            LogicalDelta(3.0, 5.0),
            uikitHoverDelta(previous = LogicalPoint(10.0, 20.0), current = LogicalPoint(13.0, 25.0)),
        )
        assertEquals(
            LogicalDelta(-2.5, -0.5),
            uikitHoverDelta(previous = LogicalPoint(4.0, 2.0), current = LogicalPoint(1.5, 1.5)),
        )
    }

    @Test
    fun hoverDeltaOfAnImmobileHoverIsZeroCarriedNotFabricated() {
        assertEquals(
            LogicalDelta(0.0, 0.0),
            uikitHoverDelta(previous = LogicalPoint(7.0, 9.0), current = LogicalPoint(7.0, 9.0)),
        )
    }

    @Test
    fun hoverDeltaWithoutAPreviousObservationIsLost() {
        // L'entrée (PointerEntered) a été perdue : un Moved sans précédent n'a pas de delta
        // honnête — l'observation est perdue, jamais une zéro fabriquée.
        assertNull(uikitHoverDelta(previous = null, current = LogicalPoint(1.0, 1.0)))
    }

    // MARK: - Aides

    /** Le wrapper de lecture : `PhysicalKey.Code` ou rien (le test ne lit QUE la forme Code). */
    private fun unidentifiedPhysical(): org.graphiks.kadre.input.PhysicalKey =
        org.graphiks.kadre.input.PhysicalKey.Unidentified(null)

    private data class PhysicalCode(val page: Int, val usage: Int)

    private fun org.graphiks.kadre.input.PhysicalKey.asCode(): PhysicalCode? =
        (this as? org.graphiks.kadre.input.PhysicalKey.Code)?.let { PhysicalCode(it.usagePage, it.usageId) }
}
