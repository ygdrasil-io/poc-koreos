package org.graphiks.kadre.host.android

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult

/**
 * Les deux pins hôtes de la façade Java (INTEROP-EXPORTS §4) : le mapping d'attach
 * (`Failure` → `KadreException`, jamais null) et le scan réflexif zéro-fuite des
 * signatures publiques du package.
 *
 * Les View Android ne sont pas constructibles hors émulateur (le stub android.jar lance à
 * la construction, cf. `AndroidViewLeaseTest`) : le chemin PUBLIC des deux statiques
 * (`attachKadre` → mapping) est prouvé sur émulateur par les consumers contractuels
 * (AND-010, Task 2). Ici, la couture interne `attachAndHandle` — LE code exact que les
 * deux statiques publics appellent autour de `attachKadre` — est pilotée avec le faux
 * session (le miroir des fakes P1 de `AndroidHostSessionResultTest`).
 */
class KadreAndroidFacadeTest {

    @Test
    fun attachDelegatesAndFailureThrowsKadreException() {
        // La denial d'attach — la couture commune traduit le `KadreResult.Failure` en
        // `KadreException` (la failure est INTACTE, le mapping de `getOrThrow`), jamais null.
        val thrown = assertFailsWith<KadreException> {
            attachAndHandle(testDispatcher()) { KadreResult.Failure(KadreFailure.InvalidRequest("view")) }
        }
        assertEquals(KadreFailure.InvalidRequest("view"), thrown.failure)

        // L'attach valide : un handle non-null, avec l'identifiant opaque alloué par la
        // façade (pas le `SessionId` Kotlin rédigé — le modèle de l'interop web).
        val handle = attachAndHandle(testDispatcher()) { KadreResult.Success(FakeKadreSession()) }
        assertNotNull(handle)
        assertTrue(handle.id().isNotEmpty())
        assertTrue(handle.id() != "SessionId(<redacted>)", "the facade allocates its own opaque id")
        handle.close()
    }

    @Test
    fun javaSurfaceMatchesInterop() {
        // La surface §4 vue de JAVA : deux statiques × (avec et sans policy — @JvmOverloads).
        val statics = KadreAndroid::class.java.declaredMethods
            .filter { Modifier.isStatic(it.modifiers) && !it.isSynthetic && !it.isBridge }
            .associateBy { it.parameterTypes.toList() }
        val activityForm = statics[
            listOf(
                androidx.activity.ComponentActivity::class.java,
                android.view.View::class.java,
                org.graphiks.kadre.application.KadreApplicationFactory::class.java,
                org.graphiks.kadre.policy.KadrePolicy::class.java,
            ),
        ]
        assertNotNull(activityForm, "attach(ComponentActivity, View, factory, policy) must exist")
        assertEquals(KadreSessionHandle::class.java, activityForm.returnType)
        val viewForm = statics[
            listOf(
                android.view.View::class.java,
                androidx.lifecycle.LifecycleOwner::class.java,
                org.graphiks.kadre.application.KadreApplicationFactory::class.java,
                org.graphiks.kadre.policy.KadrePolicy::class.java,
            ),
        ]
        assertNotNull(viewForm, "attach(View, LifecycleOwner, factory, policy) must exist")
        assertEquals(KadreSessionHandle::class.java, viewForm.returnType)
        // Les deux déclinaisons sans policy (le défaut Kotlin `KadrePolicies.Default`).
        assertNotNull(statics[statics.keys.first { it.size == 3 && it[0] == androidx.activity.ComponentActivity::class.java }])
        assertNotNull(statics[statics.keys.first { it.size == 3 && it[0] == android.view.View::class.java }])

        // Le billet §4, verbatim : id/state/observeState/termination/requestStop/close.
        assertEquals(String::class.java, KadreSessionHandle::class.java.getMethod("id").returnType)
        assertEquals(KadreSessionSnapshot::class.java, KadreSessionHandle::class.java.getMethod("state").returnType)
        val observe = KadreSessionHandle::class.java.getMethod("observeState", java.util.function.Consumer::class.java)
        assertEquals(AutoCloseable::class.java, observe.returnType)
        assertEquals(java.util.concurrent.CompletionStage::class.java, KadreSessionHandle::class.java.getMethod("termination").returnType)
        assertEquals(Void.TYPE, KadreSessionHandle::class.java.getMethod("requestStop").returnType)
        assertEquals(Void.TYPE, KadreSessionHandle::class.java.getMethod("close").returnType)
    }

    @Test
    fun noLeakedAsyncTypes() {
        // Le scan réflexif du package `host.android` (precedent P4) : AUCUN paramètre ni
        // retour public `StateFlow`/`Flow`/`Continuation`/`CoroutineScope`. La surface
        // promise est celle des classes TOP-LEVEL du package (les `$`-classes internes —
        // lambdas, companions, inner — sont le glue du compilateur, hors surface §2).
        // Les méthodes publiques non synthétiques et les constructeurs publics y sont
        // couverts, types bruts ET génériques.
        val forbidden = Regex("StateFlow|Flow|Continuation|CoroutineScope")
        val classNames = hostAndroidClassNames().filter { !it.contains('$') }
        assertTrue(classNames.isNotEmpty(), "the host.android package must be scannable from the test classpath")
        for (className in classNames) {
            val clazz = Class.forName(className, false, KadreSessionHandle::class.java.classLoader)
            for (method in clazz.declaredMethods) {
                if (!Modifier.isPublic(method.modifiers) || method.isSynthetic || method.isBridge) continue
                assertNoLeak(forbidden, "${clazz.simpleName}.${method.name} return", method.genericReturnType)
                method.genericParameterTypes.forEachIndexed { index, type ->
                    assertNoLeak(forbidden, "${clazz.simpleName}.${method.name} parameter #$index", type)
                }
            }
            for (constructor in clazz.declaredConstructors) {
                if (!Modifier.isPublic(constructor.modifiers)) continue
                constructor.genericParameterTypes.forEachIndexed { index, type ->
                    assertNoLeak(forbidden, "${clazz.simpleName} constructor parameter #$index", type)
                }
            }
        }
    }

    /** Le dispatcher de test injecté dans la couture — jamais `Dispatchers.Main` hors device. */
    private fun testDispatcher() = CoroutineContextHandleDispatcher(kotlinx.coroutines.test.TestScope().coroutineContext)

    private fun assertNoLeak(forbidden: Regex, what: String, type: java.lang.reflect.Type) {
        assertTrue(
            !forbidden.containsMatchIn(type.typeName),
            "$what leaks an async type: ${type.typeName}",
        )
    }

    /** Chaque classe du package dans la code source du module (répertoire ou jar). */
    private fun hostAndroidClassNames(): List<String> {
        val codeSource = KadreSessionHandle::class.java.protectionDomain.codeSource
            ?: error("no code source for the host.android package")
        val location = codeSource.location
        val prefix = "org/graphiks/kadre/host/android/"
        val file = java.io.File(location.toURI())
        return if (file.isDirectory) {
            val dir = file.resolve(prefix)
            dir.listFiles { entry -> entry.isFile && entry.name.endsWith(".class") }
                .orEmpty()
                .map { entry -> "org.graphiks.kadre.host.android." + entry.name.removeSuffix(".class") }
        } else {
            java.util.jar.JarFile(file).use { jar ->
                jar.entries().asSequence()
                    .map { it.name }
                    .filter { it.startsWith(prefix) && it.endsWith(".class") }
                    .map { "org.graphiks.kadre.host.android." + it.removePrefix(prefix).removeSuffix(".class").replace('/', '.') }
                    .toList()
            }
        }
    }
}
