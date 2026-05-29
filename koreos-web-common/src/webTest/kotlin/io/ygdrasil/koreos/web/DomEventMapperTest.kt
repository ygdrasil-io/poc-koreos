/**
 * Tests des fonctions pures de DomEventMapper.
 *
 * Ces tests vivent dans webTest (source set intermédiaire) : ils s'exécutent
 * aussi bien sur la cible js que sur wasmJs. Aucune dépendance DOM n'est requise
 * car les fonctions testées sont 100 % Kotlin pur.
 */
package io.ygdrasil.koreos.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DomEventMapperTest {

    // -----------------------------------------------------------------------
    // domCodeToKey — lettres
    // -----------------------------------------------------------------------

    @Test
    fun `domCodeToKey mappe les lettres`() {
        assertEquals(WebKey.A, domCodeToKey("KeyA"))
        assertEquals(WebKey.Z, domCodeToKey("KeyZ"))
        assertEquals(WebKey.M, domCodeToKey("KeyM"))
    }

    // -----------------------------------------------------------------------
    // domCodeToKey — chiffres
    // -----------------------------------------------------------------------

    @Test
    fun `domCodeToKey mappe les chiffres`() {
        assertEquals(WebKey.Digit0, domCodeToKey("Digit0"))
        assertEquals(WebKey.Digit9, domCodeToKey("Digit9"))
        assertEquals(WebKey.Digit5, domCodeToKey("Digit5"))
    }

    // -----------------------------------------------------------------------
    // domCodeToKey — touches de fonction
    // -----------------------------------------------------------------------

    @Test
    fun `domCodeToKey mappe les touches de fonction`() {
        assertEquals(WebKey.F1,  domCodeToKey("F1"))
        assertEquals(WebKey.F12, domCodeToKey("F12"))
        assertEquals(WebKey.F6,  domCodeToKey("F6"))
    }

    // -----------------------------------------------------------------------
    // domCodeToKey — touches spéciales
    // -----------------------------------------------------------------------

    @Test
    fun `domCodeToKey mappe les touches spéciales`() {
        assertEquals(WebKey.Space,     domCodeToKey("Space"))
        assertEquals(WebKey.Enter,     domCodeToKey("Enter"))
        assertEquals(WebKey.Escape,    domCodeToKey("Escape"))
        assertEquals(WebKey.Backspace, domCodeToKey("Backspace"))
        assertEquals(WebKey.Tab,       domCodeToKey("Tab"))
    }

    // -----------------------------------------------------------------------
    // domCodeToKey — navigation
    // -----------------------------------------------------------------------

    @Test
    fun `domCodeToKey mappe les touches de navigation`() {
        assertEquals(WebKey.ArrowUp,    domCodeToKey("ArrowUp"))
        assertEquals(WebKey.ArrowDown,  domCodeToKey("ArrowDown"))
        assertEquals(WebKey.ArrowLeft,  domCodeToKey("ArrowLeft"))
        assertEquals(WebKey.ArrowRight, domCodeToKey("ArrowRight"))
    }

    // -----------------------------------------------------------------------
    // domCodeToKey — modificateurs
    // -----------------------------------------------------------------------

    @Test
    fun `domCodeToKey mappe les modificateurs`() {
        assertEquals(WebKey.ShiftLeft,    domCodeToKey("ShiftLeft"))
        assertEquals(WebKey.ShiftRight,   domCodeToKey("ShiftRight"))
        assertEquals(WebKey.ControlLeft,  domCodeToKey("ControlLeft"))
        assertEquals(WebKey.ControlRight, domCodeToKey("ControlRight"))
        assertEquals(WebKey.AltLeft,      domCodeToKey("AltLeft"))
        assertEquals(WebKey.AltRight,     domCodeToKey("AltRight"))
        assertEquals(WebKey.MetaLeft,     domCodeToKey("MetaLeft"))
        assertEquals(WebKey.MetaRight,    domCodeToKey("MetaRight"))
    }

    // -----------------------------------------------------------------------
    // domCodeToKey — inconnu
    // -----------------------------------------------------------------------

    @Test
    fun `domCodeToKey retourne Unknown pour un code non reconnu`() {
        assertEquals(WebKey.Unknown, domCodeToKey(""))
        assertEquals(WebKey.Unknown, domCodeToKey("NumpadAdd"))
        assertEquals(WebKey.Unknown, domCodeToKey("BrowserBack"))
    }

    // -----------------------------------------------------------------------
    // domModifiers
    // -----------------------------------------------------------------------

    @Test
    fun `domModifiers retourne NONE quand tous les drapeaux sont faux`() {
        val mods = domModifiers(shiftKey = false, ctrlKey = false, altKey = false, metaKey = false)
        assertEquals(WebModifiers.NONE, mods)
    }

    @Test
    fun `domModifiers retourne SHIFT quand shiftKey est vrai`() {
        val mods = domModifiers(shiftKey = true, ctrlKey = false, altKey = false, metaKey = false)
        assertTrue(mods.shift)
        assertFalse(mods.ctrl)
        assertFalse(mods.alt)
        assertFalse(mods.meta)
    }

    @Test
    fun `domModifiers retourne CTRL quand ctrlKey est vrai`() {
        val mods = domModifiers(shiftKey = false, ctrlKey = true, altKey = false, metaKey = false)
        assertFalse(mods.shift)
        assertTrue(mods.ctrl)
    }

    @Test
    fun `domModifiers retourne ALT quand altKey est vrai`() {
        val mods = domModifiers(shiftKey = false, ctrlKey = false, altKey = true, metaKey = false)
        assertTrue(mods.alt)
    }

    @Test
    fun `domModifiers retourne META quand metaKey est vrai`() {
        val mods = domModifiers(shiftKey = false, ctrlKey = false, altKey = false, metaKey = true)
        assertTrue(mods.meta)
    }

    @Test
    fun `domModifiers combine correctement plusieurs modificateurs`() {
        val mods = domModifiers(shiftKey = true, ctrlKey = true, altKey = false, metaKey = false)
        assertTrue(mods.shift)
        assertTrue(mods.ctrl)
        assertFalse(mods.alt)
        assertFalse(mods.meta)
        assertTrue(mods.contains(WebModifiers.SHIFT + WebModifiers.CTRL))
    }

    @Test
    fun `domModifiers tous les modificateurs actifs`() {
        val mods = domModifiers(shiftKey = true, ctrlKey = true, altKey = true, metaKey = true)
        assertTrue(mods.shift)
        assertTrue(mods.ctrl)
        assertTrue(mods.alt)
        assertTrue(mods.meta)
    }

    // -----------------------------------------------------------------------
    // domButtonToMouseButton
    // -----------------------------------------------------------------------

    @Test
    fun `domButtonToMouseButton mappe le bouton gauche`() {
        assertEquals(WebMouseButton.Left, domButtonToMouseButton(0))
    }

    @Test
    fun `domButtonToMouseButton mappe le bouton milieu`() {
        assertEquals(WebMouseButton.Middle, domButtonToMouseButton(1))
    }

    @Test
    fun `domButtonToMouseButton mappe le bouton droit`() {
        assertEquals(WebMouseButton.Right, domButtonToMouseButton(2))
    }

    @Test
    fun `domButtonToMouseButton mappe les boutons supplémentaires en Other`() {
        assertEquals(WebMouseButton.Other(3), domButtonToMouseButton(3))
        assertEquals(WebMouseButton.Other(4), domButtonToMouseButton(4))
        assertEquals(WebMouseButton.Other(10), domButtonToMouseButton(10))
    }
}
