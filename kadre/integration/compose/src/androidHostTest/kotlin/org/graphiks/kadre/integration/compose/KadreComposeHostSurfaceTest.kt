package org.graphiks.kadre.integration.compose

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Le pin de surface du composable (le miroir du scan `noLeakedAsyncTypes` de la façade
 * Java) : la signature de [KadreComposeHost] ne transporte que la surface publique — la
 * callback d'attach du host (`(ComponentActivity, FrameLayout) -> View`), l'usine, la
 * policy, le modificateur et le slot content recevant la [org.graphiks.kadre.application.
 * KadreSession] publique. AUCUN type interne kadre (`org.graphiks.kadre.internal.*`) ni
 * type async (Flow/CoroutineScope/Continuation) n'y paraît. Les classes sont chargées par
 * nom depuis le classpath host (le stub android.jar) — aucune View n'est construite.
 */
class KadreComposeHostSurfaceTest {

    @Test
    fun composableSignatureCarriesOnlyThePublicSurface() {
        val composable = Class.forName("org.graphiks.kadre.integration.compose.KadreComposeHostKt")
            .declaredMethods
            .single { it.name == "KadreComposeHost" && Modifier.isPublic(it.modifiers) }

        val signature = (composable.genericParameterTypes + composable.genericReturnType)
            .joinToString(" ") { it.typeName }

        // Les types promis — l'attach cible du host est bien une vue QU'IL crée.
        for (promised in listOf(
            "androidx.activity.ComponentActivity",
            "android.widget.FrameLayout",
            "android.view.View",
            "org.graphiks.kadre.application.KadreApplicationFactory",
            "org.graphiks.kadre.policy.KadrePolicy",
            "androidx.compose.ui.Modifier",
            "org.graphiks.kadre.application.KadreSession",
        )) {
            assertTrue(promised in signature, "the signature must carry $promised — got: $signature")
        }

        // Aucun type interne kadre ni async — le ruling du binding compose.
        val forbidden = Regex("org\\.graphiks\\.kadre\\.internal|StateFlow|Flow|Continuation|CoroutineScope")
        assertTrue(!forbidden.containsMatchIn(signature), "leaked types in signature: $signature")
    }
}
