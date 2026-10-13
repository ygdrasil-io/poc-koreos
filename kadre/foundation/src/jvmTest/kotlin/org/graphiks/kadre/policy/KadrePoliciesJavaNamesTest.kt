package org.graphiks.kadre.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * La promesse JAVA d'INTEROP-EXPORTS §4 (`:87`) — les helpers de policy sont des fonctions
 * ordinaires du singleton : depuis Java, `KadrePolicies.INSTANCE.defaultPolicy()` existe
 * avec CE nom exact, zéro argument, rendant la lettre promise. Le pin réflexif (JVM seul —
 * `java.lang.reflect` n'existe pas sur les autres cibles) épingle la phrase interop.
 */
class KadrePoliciesJavaNamesTest {

    @Test
    fun helpersAreCallableFromJavaOnTheSingleton() {
        // Le chemin EXACT d'un appel Java : le champ statique `INSTANCE` (invisible du
        // source Kotlin — la réflexion seule y accède), puis la méthode publique.
        val instance = KadrePolicies::class.java.getField("INSTANCE").get(null)
        for (name in listOf("defaultPolicy", "realtimePolicy", "recordingPolicy")) {
            val method = KadrePolicies::class.java.getMethod(name)
            assertEquals(KadrePolicy::class.java, method.returnType, name)
            assertEquals(0, method.parameterCount, name)
        }
        // La lettre rendue est LA constante publique — jamais une copie
        // (`KadrePolicies.INSTANCE.defaultPolicy() === KadrePolicies.Default`).
        assertSame(KadrePolicies.Default, KadrePolicies::class.java.getMethod("defaultPolicy").invoke(instance))
        assertSame(KadrePolicies.Realtime, KadrePolicies::class.java.getMethod("realtimePolicy").invoke(instance))
        assertSame(KadrePolicies.Recording, KadrePolicies::class.java.getMethod("recordingPolicy").invoke(instance))
    }
}
