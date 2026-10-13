package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.input.TextInputAction
import org.graphiks.kadre.input.TextInputPurpose
import org.graphiks.kadre.input.TextCapitalization
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * La table de mapping config → UIKit (`UIKitTextInputConfigMapping`), prouvée PURE : les valeurs
 * attendues sont les ENTIERS BRUTS des headers du SDK (`UITextInputTraits.h`, iPhoneSimulator27.0.sdk),
 * écrits littéralement dans le test (jamais relus depuis la plateforme) — le test serait rouGED si
 * une constante de mapping divergeait du header ou si une future SDK renumérotait.
 *
 * Cibles de mutation : chaque branche de chaque `when` (une valeur changée est tuée par son test),
 * le booleen d'entrée sécurisée (Password seul — un `==` élargi à Number produirait un clavier
 * masqué faux), et la direction d'[uikitAutocorrectionTypeRaw] (true→Yes/false→No — une inversion
 * activerait l'autocorrection sur les champs qui la refusent).
 */
class UIKitTextInputConfigMappingTest {

    // MARK: - purpose → UIKeyboardType (UITextInputTraits.h : Default=0, NumbersAndPunctuation=2,
    // URL=3, PhonePad=5, NamePhonePad=6, EmailAddress=7, DecimalPad=9)

    @Test
    fun textPurposeMapsTheDefaultKeyboard() {
        assertEquals(0L, uikitKeyboardTypeRaw(TextInputPurpose.Text))
    }

    @Test
    fun namePurposeMapsTheNamePhonePadKeyboard() {
        assertEquals(6L, uikitKeyboardTypeRaw(TextInputPurpose.Name))
    }

    @Test
    fun emailPurposeMapsTheEmailAddressKeyboard() {
        assertEquals(7L, uikitKeyboardTypeRaw(TextInputPurpose.Email))
    }

    @Test
    fun urlPurposeMapsTheUrlKeyboard() {
        assertEquals(3L, uikitKeyboardTypeRaw(TextInputPurpose.Url))
    }

    @Test
    fun telephonePurposeMapsThePhonePadKeyboard() {
        assertEquals(5L, uikitKeyboardTypeRaw(TextInputPurpose.Telephone))
    }

    @Test
    fun numberPurposeMapsNumbersAndPunctuation() {
        assertEquals(2L, uikitKeyboardTypeRaw(TextInputPurpose.Number))
    }

    @Test
    fun decimalPurposeMapsTheDecimalPad() {
        assertEquals(9L, uikitKeyboardTypeRaw(TextInputPurpose.Decimal))
    }

    @Test
    fun passwordPurposeMapsTheDefaultKeyboard() {
        assertEquals(0L, uikitKeyboardTypeRaw(TextInputPurpose.Password))
    }

    // MARK: - purpose → secure entry

    @Test
    fun onlyThePasswordPurposeIsASecureEntry() {
        assertTrue(uikitIsSecureEntry(TextInputPurpose.Password), "Password alone is a secure entry")
        assertFalse(uikitIsSecureEntry(TextInputPurpose.Text))
        assertFalse(uikitIsSecureEntry(TextInputPurpose.Name))
        assertFalse(uikitIsSecureEntry(TextInputPurpose.Email))
        assertFalse(uikitIsSecureEntry(TextInputPurpose.Url))
        assertFalse(uikitIsSecureEntry(TextInputPurpose.Telephone))
        assertFalse(uikitIsSecureEntry(TextInputPurpose.Number))
        assertFalse(uikitIsSecureEntry(TextInputPurpose.Decimal))
    }

    // MARK: - action → UIReturnKeyType (UITextInputTraits.h : Default=0, Go=1, Next=4, Search=6,
    // Send=7, Done=9 — le Google=2/Join=3/Route=5/Yahoo=8 du header n'a PAS de valeur Kadre)

    @Test
    fun defaultActionMapsTheDefaultReturnKey() {
        assertEquals(0L, uikitReturnKeyTypeRaw(TextInputAction.Default))
    }

    @Test
    fun doneActionMapsTheDoneReturnKey() {
        assertEquals(9L, uikitReturnKeyTypeRaw(TextInputAction.Done))
    }

    @Test
    fun goActionMapsTheGoReturnKey() {
        assertEquals(1L, uikitReturnKeyTypeRaw(TextInputAction.Go))
    }

    @Test
    fun nextActionMapsTheNextReturnKey() {
        assertEquals(4L, uikitReturnKeyTypeRaw(TextInputAction.Next))
    }

    @Test
    fun searchActionMapsTheSearchReturnKey() {
        assertEquals(6L, uikitReturnKeyTypeRaw(TextInputAction.Search))
    }

    @Test
    fun sendActionMapsTheSendReturnKey() {
        assertEquals(7L, uikitReturnKeyTypeRaw(TextInputAction.Send))
    }

    // MARK: - capitalization → UITextAutocapitalizationType (UITextInputTraits.h :
    // None=0, Words=1, Sentences=2, AllCharacters=3)

    @Test
    fun capitalizationNoneMapsZero() {
        assertEquals(0L, uikitAutocapitalizationTypeRaw(TextCapitalization.None))
    }

    @Test
    fun capitalizationWordsMapsOne() {
        assertEquals(1L, uikitAutocapitalizationTypeRaw(TextCapitalization.Words))
    }

    @Test
    fun capitalizationSentencesMapsTwo() {
        assertEquals(2L, uikitAutocapitalizationTypeRaw(TextCapitalization.Sentences))
    }

    @Test
    fun capitalizationCharactersMapsThree() {
        assertEquals(3L, uikitAutocapitalizationTypeRaw(TextCapitalization.Characters))
    }

    // MARK: - autocorrect → UITextAutocorrectionType (UITextInputTraits.h : Default=0, No=1, Yes=2)

    @Test
    fun autocorrectEnabledMapsYes() {
        assertEquals(2L, uikitAutocorrectionTypeRaw(true),
            "the app's explicit request maps to Yes, not to the system Default")
    }

    @Test
    fun autocorrectDisabledMapsNo() {
        assertEquals(1L, uikitAutocorrectionTypeRaw(false))
    }
}
