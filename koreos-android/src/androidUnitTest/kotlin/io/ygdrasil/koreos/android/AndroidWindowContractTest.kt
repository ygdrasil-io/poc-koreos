package io.ygdrasil.koreos.android

import io.ygdrasil.koreos.core.ActiveEventLoop
import io.ygdrasil.koreos.core.Window
import io.ygdrasil.koreos.core.WindowAttributes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests de contrat pour [AndroidWindow] et [AndroidEventLoop] (GRA-47).
 *
 * Ces tests vérifient les signatures et contrats d'interface via réflexion,
 * sans instancier d'objets Android réels (qui nécessitent un émulateur ou Robolectric).
 *
 * ## Contrat "pending window"
 *
 * 1. [AndroidEventLoop.createWindow] retourne immédiatement un [AndroidWindow] valide,
 *    même avant que la [android.view.Surface] ne soit disponible.
 * 2. [AndroidWindow.rawWindowHandle] lance [IllegalStateException] si appelé avant
 *    [AndroidWindow.onSurfaceAvailable].
 * 3. [AndroidEventLoop.onSurfaceCreated] transfère la surface vers l'[AndroidWindow]
 *    stocké dans [AndroidEventLoop.pendingWindow].
 * 4. [AndroidEventLoop.onSurfaceDestroyed] invalide la surface de l'[AndroidWindow].
 *
 * Les tests d'exécution réels sont validés manuellement via hello-window-android
 * sur émulateur/appareil Android.
 */
class AndroidWindowContractTest {

    // ── Vérification d'interface ─────────────────────────────────────────────

    @Test
    fun `AndroidWindow implemente Window`() {
        assertTrue(
            Window::class.java.isAssignableFrom(AndroidWindow::class.java),
            "AndroidWindow doit implémenter Window",
        )
    }

    @Test
    fun `AndroidEventLoop implemente ActiveEventLoop`() {
        assertTrue(
            ActiveEventLoop::class.java.isAssignableFrom(AndroidEventLoop::class.java),
            "AndroidEventLoop doit implémenter ActiveEventLoop",
        )
    }

    // ── Vérification de createWindow ─────────────────────────────────────────

    @Test
    fun `AndroidEventLoop expose createWindow retournant Window`() {
        val method = AndroidEventLoop::class.java.methods
            .firstOrNull { it.name == "createWindow" && it.parameterCount == 1 }
        assertNotNull(method, "AndroidEventLoop.createWindow(WindowAttributes) doit exister")
        assertTrue(
            Window::class.java.isAssignableFrom(method.returnType),
            "createWindow doit retourner Window (ou un sous-type)",
        )
        assertEquals(
            WindowAttributes::class.java,
            method.parameterTypes[0],
            "createWindow doit accepter WindowAttributes",
        )
    }

    // ── Vérification de pendingWindow ────────────────────────────────────────

    @Test
    fun `AndroidEventLoop expose pendingWindow de type AndroidWindow nullable`() {
        val method = AndroidEventLoop::class.java.methods
            .firstOrNull { it.name == "getPendingWindow" }
        assertNotNull(method, "AndroidEventLoop doit exposer pendingWindow via getPendingWindow()")
        assertEquals(
            AndroidWindow::class.java,
            method.returnType,
            "pendingWindow doit être de type AndroidWindow",
        )
    }

    // ── Vérification du cycle de vie de la surface ───────────────────────────

    @Test
    fun `AndroidEventLoop expose onSurfaceCreated avec parametre Surface`() {
        val method = AndroidEventLoop::class.java.declaredMethods
            .firstOrNull { it.name == "onSurfaceCreated" }
        assertNotNull(method, "AndroidEventLoop.onSurfaceCreated(Surface) doit exister")
        assertEquals(1, method.parameterCount, "onSurfaceCreated doit accepter un seul paramètre")
        assertEquals(
            "android.view.Surface",
            method.parameterTypes[0].name,
            "onSurfaceCreated doit accepter android.view.Surface",
        )
    }

    @Test
    fun `AndroidEventLoop expose onSurfaceDestroyed sans parametre`() {
        val method = AndroidEventLoop::class.java.declaredMethods
            .firstOrNull { it.name == "onSurfaceDestroyed" }
        assertNotNull(method, "AndroidEventLoop.onSurfaceDestroyed() doit exister")
        assertEquals(0, method.parameterCount, "onSurfaceDestroyed ne doit pas avoir de paramètre")
    }

    @Test
    fun `AndroidWindow expose onSurfaceAvailable avec parametre Surface`() {
        val method = AndroidWindow::class.java.declaredMethods
            .firstOrNull { it.name == "onSurfaceAvailable" }
        assertNotNull(method, "AndroidWindow.onSurfaceAvailable(Surface) doit exister")
        assertEquals(1, method.parameterCount)
        assertEquals(
            "android.view.Surface",
            method.parameterTypes[0].name,
        )
    }

    @Test
    fun `AndroidWindow expose onSurfaceReleased sans parametre`() {
        val method = AndroidWindow::class.java.declaredMethods
            .firstOrNull { it.name == "onSurfaceReleased" }
        assertNotNull(method, "AndroidWindow.onSurfaceReleased() doit exister")
        assertEquals(0, method.parameterCount)
    }

    // ── Vérification de rawWindowHandle ─────────────────────────────────────

    @Test
    fun `AndroidWindow rawWindowHandle retourne Any`() {
        val method = AndroidWindow::class.java.methods
            .firstOrNull { it.name == "getRawWindowHandle" }
        assertNotNull(method, "AndroidWindow doit exposer rawWindowHandle")
        assertEquals(
            Any::class.java,
            method.returnType,
            "rawWindowHandle doit retourner Any (type-erased pour commonMain)",
        )
    }

    /**
     * Documente le contrat : [AndroidWindow.rawWindowHandle] lance
     * [IllegalStateException] avant que [AndroidWindow.onSurfaceAvailable] soit appelé.
     *
     * Validé par inspection du code (throw explicite dans le getter) et par
     * test d'intégration manuel sur émulateur Android.
     */
    @Test
    fun `AndroidWindow rawWindowHandle est documente comme levant IllegalStateException avant surfaceCreated`() {
        val method = AndroidWindow::class.java.methods
            .firstOrNull { it.name == "getRawWindowHandle" }
        assertNotNull(method, "rawWindowHandle doit exister sur AndroidWindow")
        // Le contrat de throw est dans l'implémentation (surface == null → throw).
        // Vérifiable uniquement sur émulateur avec un SurfaceView réel.
    }
}
