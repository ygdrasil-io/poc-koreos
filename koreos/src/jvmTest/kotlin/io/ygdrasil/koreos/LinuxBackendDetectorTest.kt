/**
 * Tests unitaires pour [LinuxBackendDetector].
 *
 * Stratégie : on teste les comportements qui ne dépendent pas du classpath
 * ou des variables d'environnement effectives (env vars non mockables
 * facilement en JVM standard). Les cas couverts :
 *  - canLoad : classe existante → true, classe inexistante → false
 *  - La logique KOREOS_LINUX_BACKEND est testée indirectement via la détection
 *    par canLoad (seul chemin garantissable sans modifier le process env).
 *
 * Redmine #68 : détection X11/Wayland.
 */
package io.ygdrasil.koreos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxBackendDetectorTest {

    // -------------------------------------------------------------------------
    // canLoad — logique principale, indépendante de l'OS
    // -------------------------------------------------------------------------

    @Test
    fun `canLoad retourne true pour une classe existante`() {
        // String est toujours sur le classpath
        val result = LinuxBackendDetector.canLoad("java.lang.String")
        assertTrue(result, "java.lang.String doit être chargeable")
    }

    @Test
    fun `canLoad retourne false pour une classe inexistante`() {
        val result = LinuxBackendDetector.canLoad("io.ygdrasil.koreos.NonExistentClass999")
        assertFalse(result, "Une classe fictive ne doit pas être chargeable")
    }

    @Test
    fun `canLoad retourne false pour le backend X11 absent du classpath`() {
        // koreos-x11 n'est pas une dépendance de :koreos — doit être absent
        val result = LinuxBackendDetector.canLoad(LinuxBackendDetector.X11_CLASS)
        assertFalse(result, "koreos-x11 ne doit pas être sur le classpath de :koreos")
    }

    @Test
    fun `canLoad retourne false pour le backend Wayland absent du classpath`() {
        // koreos-wayland n'est pas une dépendance de :koreos — doit être absent
        val result = LinuxBackendDetector.canLoad(LinuxBackendDetector.WAYLAND_CLASS)
        assertFalse(result, "koreos-wayland ne doit pas être sur le classpath de :koreos")
    }

    @Test
    fun `canLoad avec debug=true ne lance pas d exception`() {
        // Vérifie que le flag debug n'introduit pas de régression
        val result = LinuxBackendDetector.canLoad("io.ygdrasil.koreos.DoesNotExist", debug = true)
        assertFalse(result)
    }

    // -------------------------------------------------------------------------
    // Constantes — vérification des noms de classes cibles
    // -------------------------------------------------------------------------

    @Test
    fun `X11_CLASS pointe vers le package x11 attendu`() {
        assertEquals(
            "io.ygdrasil.koreos.x11.X11EventLoopKt",
            LinuxBackendDetector.X11_CLASS,
        )
    }

    @Test
    fun `WAYLAND_CLASS pointe vers le package wayland attendu`() {
        assertEquals(
            "io.ygdrasil.koreos.wayland.WaylandEventLoopKt",
            LinuxBackendDetector.WAYLAND_CLASS,
        )
    }
}
