package io.ygdrasil.koreos.appkit

import io.ygdrasil.koreos.appkit.bindings.NSApplication
import io.ygdrasil.koreos.core.ActiveEventLoop
import io.ygdrasil.koreos.core.ApplicationHandler
import io.ygdrasil.koreos.core.ControlFlow
import io.ygdrasil.koreos.core.EventLoopProxy
import io.ygdrasil.koreos.core.StartCause
import io.ygdrasil.koreos.core.Window
import io.ygdrasil.koreos.core.WindowAttributes
import io.ygdrasil.koreos.core.WindowId
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests de compilation des classes GRA-125.
 *
 * Aucun test d'exécution réel : l'appel de `NSApp` nécessite le thread
 * principal macOS et est validé par l'application de démonstration M1
 * plutôt que par les tests unitaires.
 */
class KoreosApplicationTest {

    @Test
    fun `KoreosApplication est une sous-classe de NSApplication`() {
        // Vérification au niveau types : KoreosApplication doit hériter
        // de NSApplication (cf. binding kextract).
        val nsAppClass = NSApplication::class.java
        assertTrue(nsAppClass.isAssignableFrom(KoreosApplication::class.java))
    }

    /**
     * Redmine #41 : eventLoop doit être une propriété d'instance (non statique).
     *
     * Vérifie que :
     * - `KoreosApplication` possède un champ `eventLoop` non-statique (backing field JVM).
     * - `KoreosApplication` possède un champ `sharedApp` statique (équivalent de
     *   `NSApp as? KoreosApplication`) — backing field du companion property.
     * - Aucun champ `eventLoop` statique n'existe (la variable statique globale a été supprimée).
     */
    @Test
    fun `eventLoop est une propriete d instance et non une variable statique`() {
        val allFields = KoreosApplication::class.java.declaredFields

        // eventLoop doit exister comme champ d'instance (non statique)
        val eventLoopField = allFields.firstOrNull { it.name == "eventLoop" }
        assertNotNull(
            eventLoopField,
            "eventLoop doit être un champ de KoreosApplication, champs trouvés : ${allFields.map { it.name }}"
        )
        assertTrue(
            !java.lang.reflect.Modifier.isStatic(eventLoopField!!.modifiers),
            "eventLoop doit être non-statique (propriété d'instance)"
        )

        // sharedApp doit être un champ statique (backing field du companion property)
        val sharedAppField = allFields.firstOrNull { it.name == "sharedApp" }
        assertNotNull(
            sharedAppField,
            "sharedApp doit exister dans KoreosApplication pour remplacer NSApp as? KoreosApplication"
        )
        assertTrue(
            java.lang.reflect.Modifier.isStatic(sharedAppField!!.modifiers),
            "sharedApp doit être statique (companion object property)"
        )
    }

    /**
     * Redmine #41 : sharedApp est initialement null avant tout appel à initialize().
     *
     * Garantit qu'il n'y a pas d'initialisation eagerly et que le pattern
     * "NSApp as? KoreosApplication" ne retourne null que si initialize() n'a pas
     * encore été appelé.
     */
    @Test
    fun `sharedApp est null avant initialize`() {
        // Note : ce test suppose que initialize() n'a pas été appelé dans ce processus
        // de test, ce qui est le cas car les tests ne touchent pas au runtime macOS.
        assertNull(
            KoreosApplication.sharedApp,
            "sharedApp doit être null si initialize() n'a jamais été appelé"
        )
    }

    @Test
    fun `KoreosAppDelegate accepte un ApplicationHandler et un ActiveEventLoop`() {
        // Vérifie la signature du constructeur sans instancier l'objet ObjC
        // sous-jacent (qui nécessiterait le thread principal macOS).
        val ctor = KoreosAppDelegate::class.java.constructors.first()
        val paramTypes = ctor.parameterTypes
        assertTrue(paramTypes.any { ApplicationHandler::class.java.isAssignableFrom(it) })
        assertTrue(paramTypes.any { ActiveEventLoop::class.java.isAssignableFrom(it) })
    }

    @Test
    fun `MainThreadCheck est invocable`() {
        // Le type compile et l'objet singleton est accessible.
        val check: MainThreadCheck = MainThreadCheck
        assertNotNull(check)
    }

    @Test
    fun `Callbacks expose des methodes JvmStatic`() {
        // Garantit que les trampolines requis par Linker.upcallStub
        // existent et sont statiques au niveau JVM.
        val didFinish = KoreosAppDelegate.Callbacks::class.java
            .getDeclaredMethod(
                "applicationDidFinishLaunching",
                java.lang.foreign.MemorySegment::class.java,
                java.lang.foreign.MemorySegment::class.java,
                java.lang.foreign.MemorySegment::class.java,
            )
        val shouldTerminate = KoreosAppDelegate.Callbacks::class.java
            .getDeclaredMethod(
                "applicationShouldTerminate",
                java.lang.foreign.MemorySegment::class.java,
                java.lang.foreign.MemorySegment::class.java,
                java.lang.foreign.MemorySegment::class.java,
            )
        assertTrue(java.lang.reflect.Modifier.isStatic(didFinish.modifiers))
        assertTrue(java.lang.reflect.Modifier.isStatic(shouldTerminate.modifiers))
    }

    /** Stub no-op pour valider la signature du constructeur de KoreosAppDelegate. */
    @Suppress("unused")
    private class NoopHandler : ApplicationHandler {
        override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
        override fun windowEvent(eventLoop: ActiveEventLoop, windowId: WindowId, event: Any) = Unit
    }

    /** Stub no-op pour valider la signature du constructeur de KoreosAppDelegate. */
    @Suppress("unused")
    private class NoopEventLoop : ActiveEventLoop {
        override fun createWindow(attributes: WindowAttributes): Window =
            throw UnsupportedOperationException()
        override fun setControlFlow(controlFlow: ControlFlow) = Unit
        override val controlFlow: ControlFlow = ControlFlow.Poll
        override fun exit() = Unit
        override val isExiting: Boolean = false
        override fun createProxy(): EventLoopProxy = throw UnsupportedOperationException()
    }
}
