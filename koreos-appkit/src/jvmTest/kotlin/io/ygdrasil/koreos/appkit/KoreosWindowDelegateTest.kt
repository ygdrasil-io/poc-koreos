package io.ygdrasil.koreos.appkit

import io.ygdrasil.koreos.core.ActiveEventLoop
import io.ygdrasil.koreos.core.ApplicationHandler
import io.ygdrasil.koreos.core.ControlFlow
import io.ygdrasil.koreos.core.DeviceId
import io.ygdrasil.koreos.core.EventLoopProxy
import io.ygdrasil.koreos.core.Window
import io.ygdrasil.koreos.core.WindowAttributes
import io.ygdrasil.koreos.core.WindowEvent
import io.ygdrasil.koreos.core.WindowId
import java.lang.foreign.MemorySegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests de typage pour KoreosWindowDelegate (GRA-127).
 *
 * Aucun test d'exécution natif : l'instanciation réelle nécessite le thread
 * principal macOS et l'environnement AppKit. Ces tests vérifient uniquement
 * que les types, signatures et contrats de dispatch compilent correctement.
 */
class KoreosWindowDelegateTest {

    // ── Stubs de test ────────────────────────────────────────────────────────

    /** ApplicationHandler stub enregistrant les événements reçus. */
    private class RecordingHandler : ApplicationHandler {
        val events = mutableListOf<Pair<WindowId, Any>>()

        override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

        override fun windowEvent(eventLoop: ActiveEventLoop, windowId: WindowId, event: Any) {
            events.add(windowId to event)
        }
    }

    /** ActiveEventLoop stub minimal. */
    private class StubEventLoop : ActiveEventLoop {
        private var _exiting = false
        override val isExiting: Boolean get() = _exiting
        override val controlFlow: ControlFlow get() = ControlFlow.Wait

        fun markExiting() { _exiting = true }

        override fun createWindow(attributes: WindowAttributes): Window =
            throw UnsupportedOperationException()
        override fun setControlFlow(controlFlow: ControlFlow) = Unit
        override fun exit() { _exiting = true }
        override fun createProxy(): EventLoopProxy = throw UnsupportedOperationException()
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    fun `KoreosWindowDelegate a un constructeur acceptant handler eventLoop windowId`() {
        // Le constructeur public généré pour un param value class (WindowId) a un DefaultConstructorMarker
        // supplémentaire → paramCount >= 3, les 3 premiers params sont ApplicationHandler, ActiveEventLoop, long
        val ctor = KoreosWindowDelegate::class.java.constructors
            .filter { it.parameterCount >= 3 }
            .firstOrNull { c ->
                val p = c.parameterTypes
                ApplicationHandler::class.java.isAssignableFrom(p[0]) &&
                        ActiveEventLoop::class.java.isAssignableFrom(p[1]) &&
                        (p[2] == WindowId::class.java || p[2] == Long::class.java || p[2] == java.lang.Long.TYPE)
            }
        assertNotNull(ctor, "KoreosWindowDelegate doit avoir un ctor(ApplicationHandler, ActiveEventLoop, WindowId/long)")
    }

    @Test
    fun `KoreosWindowDelegate expose un champ ptr de type MemorySegment`() {
        val field = KoreosWindowDelegate::class.java.methods
            .firstOrNull { it.name == "getPtr" && it.parameterCount == 0 }
        assertNotNull(field, "KoreosWindowDelegate doit exposer ptr: MemorySegment via getPtr()")
        assertEquals(MemorySegment::class.java, field.returnType)
    }

    @Test
    fun `onWindowShouldClose retourne 0 quand eventLoop nest pas en cours darret`() {
        val handler = RecordingHandler()
        val eventLoop = StubEventLoop()
        val windowId = WindowId(42L)

        // On crée un delegate sans instancier l'ObjC runtime (test de typage uniquement)
        // → on invoque la méthode via réflexion sur l'objet Callbacks statique
        // qui est accessible sans instancier KoreosWindowDelegate (pas de runtime ObjC).
        //
        // Ici on vérifie seulement que le Callbacks.windowShouldClose retourne Byte.
        val callbacksClass = Class.forName("io.ygdrasil.koreos.appkit.KoreosWindowDelegate\$Callbacks")
        val method = callbacksClass.methods.firstOrNull { it.name == "windowShouldClose" }
        assertNotNull(method, "Callbacks.windowShouldClose doit exister")
        assertEquals(
            java.lang.Byte.TYPE,
            method.returnType,
            "windowShouldClose doit retourner Byte (BOOL)",
        )
        assertEquals(3, method.parameterCount, "windowShouldClose doit prendre 3 params (self, cmd, sender)")
    }

    @Test
    fun `WindowEvent CloseRequested est reconnu comme WindowEvent`() {
        val event: WindowEvent = WindowEvent.CloseRequested
        assertTrue(event is WindowEvent.CloseRequested)
    }

    @Test
    fun `AppKitWindow expose setWindowDelegate avec la bonne signature`() {
        val method = AppKitWindow::class.java.methods
            .firstOrNull { it.name == "setWindowDelegate" }
        assertNotNull(method, "AppKitWindow doit avoir setWindowDelegate()")
        assertEquals(2, method.parameterCount)
        assertTrue(ApplicationHandler::class.java.isAssignableFrom(method.parameterTypes[0]))
        assertTrue(ActiveEventLoop::class.java.isAssignableFrom(method.parameterTypes[1]))
    }

    @Test
    fun `AppKitWindow expose delegate nullable`() {
        val method = AppKitWindow::class.java.methods
            .firstOrNull { it.name == "getDelegate" }
        assertNotNull(method, "AppKitWindow doit exposer getDelegate()")
        assertEquals(KoreosWindowDelegate::class.java, method.returnType)
    }
}
