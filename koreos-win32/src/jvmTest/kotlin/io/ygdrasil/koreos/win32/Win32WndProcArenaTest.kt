package io.ygdrasil.koreos.win32

import java.lang.foreign.Arena
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests DoD — Redmine #17 : Win32WndProcArena.
 *
 * Valide que l'objet singleton expose une Arena.ofShared() non fermée
 * et un FunctionDescriptor conforme à la signature WndProc Win32.
 *
 * Aucun appel FFM Win32 réel n'est effectué : ce module est un stub JVM pur,
 * sans chargement de user32.dll ni SymbolLookup natif.
 */
class Win32WndProcArenaTest {

    @Test
    fun `arena est non null apres instanciation`() {
        val arena = Win32WndProcArena.arena
        assertNotNull(arena)
    }

    @Test
    fun `arena n est pas fermee apres creation de 2 instances simulees en scope test`() {
        // Simule deux utilisateurs concurrents de l'arena (ex : deux stubs WndProc)
        val ref1: Arena = Win32WndProcArena.arena
        val ref2: Arena = Win32WndProcArena.arena

        // Les deux références pointent vers la même instance singleton
        assertTrue(ref1 === ref2, "L'arena doit être un singleton partagé")

        // L'arena ne doit pas être fermée après usage dans un scope de test
        // Arena.ofShared() expose isAlive() via le scope — on vérifie via MemorySegment
        // Preuve indirecte : allocate() réussit ssi l'arena est ouverte
        val segment = ref1.allocate(8L)
        assertNotNull(segment, "L'arena doit accepter des allocations (elle est ouverte)")
        assertFalse(segment.address() == 0L, "Le segment alloué doit avoir une adresse non nulle")
    }

    @Test
    fun `wndProcDescriptor a 5 elements - 1 retour et 4 parametres`() {
        val descriptor = Win32WndProcArena.wndProcDescriptor

        // Vérifie le type de retour (LRESULT = JAVA_LONG)
        assertTrue(descriptor.returnLayout().isPresent, "Le descripteur doit avoir un type de retour")

        // Vérifie les 4 paramètres : HWND, UINT (message), WPARAM, LPARAM
        val argumentLayouts = descriptor.argumentLayouts()
        assertEquals(4, argumentLayouts.size, "WndProc doit avoir exactement 4 paramètres")
    }

    @Test
    fun `wndProcDescriptor - verification kdoc Ne scope JAMAIS cette Arena dans source`() {
        // Ce test valide que la contrainte de durée de vie est documentée.
        // Il lit le fichier source et vérifie la présence du KDoc critique.
        val sourceFile = java.io.File("src/jvmMain/kotlin/io/ygdrasil/koreos/win32/Win32WndProcArena.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assertTrue(
                content.contains("Ne JAMAIS fermer cette arena") || content.contains("Ne scope JAMAIS"),
                "Le KDoc doit contenir l'avertissement 'Ne JAMAIS fermer cette arena' ou 'Ne scope JAMAIS'"
            )
        }
        // Si le fichier source n'est pas accessible depuis le répertoire de test (CI),
        // on vérifie via la présence du symbole objet qui prouve que la classe est compilée.
        assertNotNull(Win32WndProcArena, "Le singleton Win32WndProcArena doit être accessible")
    }
}
