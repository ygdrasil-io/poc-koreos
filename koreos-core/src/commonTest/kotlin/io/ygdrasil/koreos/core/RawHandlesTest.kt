/**
 * Tests d'exhaustivité des sealed interfaces [RawWindowHandle] et [RawDisplayHandle].
 *
 * Chaque branche `when` est écrite SANS clause `else` afin que le compilateur
 * Kotlin signale une erreur à la compilation si un variant venait à être ajouté
 * sans mise à jour de ces tests (garantie d'exhaustivité).
 */
package io.ygdrasil.koreos.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RawHandlesTest {

    // -------------------------------------------------------------------------
    // RawWindowHandle — exhaustivité
    // -------------------------------------------------------------------------

    /**
     * Vérifie que le `when` sur [RawWindowHandle] couvre tous les variants sans `else`.
     */
    @Test
    fun `when sur RawWindowHandle est exhaustif`() {
        val handles: List<RawWindowHandle> = listOf(
            RawWindowHandle.AppKit(nsView = 1L, nsWindow = 2L),
            RawWindowHandle.UiKit(uiView = 3L, uiViewController = 4L),
            RawWindowHandle.UiKit(uiView = 5L, uiViewController = null),
            RawWindowHandle.Android(surface = Any()),
        )

        for (handle in handles) {
            // Sans clause `else` — le compilateur garantit l'exhaustivité.
            val nom: String = when (handle) {
                is RawWindowHandle.AppKit   -> "AppKit"
                is RawWindowHandle.UiKit    -> "UiKit"
                is RawWindowHandle.Android  -> "Android"
            }
            assertNotNull(nom)
        }
    }

    @Test
    fun `AppKit expose nsView et nsWindow`() {
        val handle = RawWindowHandle.AppKit(nsView = 0xDEADBEEFL, nsWindow = 0xCAFEBABEL)
        assertEquals(0xDEADBEEFL, handle.nsView)
        assertEquals(0xCAFEBABEL, handle.nsWindow)
    }

    @Test
    fun `UiKit expose uiView et uiViewController nullable`() {
        val avecControleur = RawWindowHandle.UiKit(uiView = 10L, uiViewController = 20L)
        assertEquals(10L, avecControleur.uiView)
        assertEquals(20L, avecControleur.uiViewController)

        val sansControleur = RawWindowHandle.UiKit(uiView = 10L, uiViewController = null)
        assertEquals(10L, sansControleur.uiView)
        assertNull(sansControleur.uiViewController)
    }

    @Test
    fun `Android encapsule la surface comme Any`() {
        val surfaceMock = object {}
        val handle = RawWindowHandle.Android(surface = surfaceMock)
        assertTrue(handle.surface === surfaceMock)
    }

    // -------------------------------------------------------------------------
    // RawDisplayHandle — exhaustivité
    // -------------------------------------------------------------------------

    /**
     * Vérifie que le `when` sur [RawDisplayHandle] couvre tous les variants sans `else`.
     */
    @Test
    fun `when sur RawDisplayHandle est exhaustif`() {
        val handles: List<RawDisplayHandle> = listOf(
            RawDisplayHandle.AppKit,
            RawDisplayHandle.UiKit,
            RawDisplayHandle.Android,
        )

        for (handle in handles) {
            // Sans clause `else` — le compilateur garantit l'exhaustivité.
            val nom: String = when (handle) {
                RawDisplayHandle.AppKit   -> "AppKit"
                RawDisplayHandle.UiKit    -> "UiKit"
                RawDisplayHandle.Android  -> "Android"
            }
            assertNotNull(nom)
        }
    }

    @Test
    fun `RawDisplayHandle AppKit est un singleton`() {
        assertTrue(RawDisplayHandle.AppKit === RawDisplayHandle.AppKit)
    }

    @Test
    fun `RawDisplayHandle UiKit est un singleton`() {
        assertTrue(RawDisplayHandle.UiKit === RawDisplayHandle.UiKit)
    }

    @Test
    fun `RawDisplayHandle Android est un singleton`() {
        assertTrue(RawDisplayHandle.Android === RawDisplayHandle.Android)
    }
}
