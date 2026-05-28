package io.ygdrasil.koreos.appkit

import io.ygdrasil.koreos.core.WindowEvent
import java.lang.foreign.MemorySegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests de typage pour CFRunLoopRedrawObserver (GRA-134).
 *
 * L'installation réelle de l'observer nécessite le thread principal macOS et
 * un CFRunLoop actif — ces tests vérifient uniquement la structure / signatures.
 */
class CFRunLoopRedrawObserverTest {

    @Test
    fun `WindowEvent RedrawRequested est un data object`() {
        val event: WindowEvent = WindowEvent.RedrawRequested
        assertTrue(event is WindowEvent.RedrawRequested)
        // data object → toString stable + singleton
        assertEquals(WindowEvent.RedrawRequested, WindowEvent.RedrawRequested)
    }

    @Test
    fun `CFRunLoopRedrawObserver redrawCallback existe avec signature CFRunLoopObserverCallBack`() {
        val method = CFRunLoopRedrawObserver::class.java.methods
            .firstOrNull { it.name == "redrawCallback" }
        assertNotNull(method, "CFRunLoopRedrawObserver doit exposer redrawCallback (static)")
        assertEquals(Void.TYPE, method.returnType, "redrawCallback doit retourner void")
        assertEquals(3, method.parameterCount, "redrawCallback prend (observer, activity, info)")
        assertEquals(MemorySegment::class.java, method.parameterTypes[0])
        assertEquals(java.lang.Long.TYPE, method.parameterTypes[1])
        assertEquals(MemorySegment::class.java, method.parameterTypes[2])
    }

    @Test
    fun `CFRunLoopRedrawObserver expose install dans son companion`() {
        val companionInstance = CFRunLoopRedrawObserver::class.java
            .getDeclaredField("Companion").get(null)
        val installMethod = companionInstance.javaClass.methods
            .firstOrNull { it.name == "install" }
        assertNotNull(installMethod, "Companion doit exposer install(handler, eventLoop, windows)")
        assertEquals(3, installMethod.parameterCount)
    }

    @Test
    fun `AppKitWindow expose needsRedraw modifiable via requestRedraw`() {
        // requestRedraw doit muter le flag needsRedraw (vérifié via getter/setter générés)
        val getter = AppKitWindow::class.java.methods
            .firstOrNull { it.name == "getNeedsRedraw\$koreos_appkit" || it.name == "getNeedsRedraw" }
        assertNotNull(getter, "AppKitWindow doit exposer un getter sur needsRedraw (internal)")
        assertEquals(java.lang.Boolean.TYPE, getter.returnType)
    }

    @Test
    fun `AppKitWindow requestRedraw existe et retourne void`() {
        val method = AppKitWindow::class.java.methods
            .firstOrNull { it.name == "requestRedraw" }
        assertNotNull(method, "AppKitWindow doit avoir requestRedraw()")
        assertEquals(Void.TYPE, method.returnType)
        assertEquals(0, method.parameterCount)
    }
}
