import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.platform.desktop.DesktopHostOptions
import org.graphiks.kadre.platform.desktop.runKadreApplication
import org.graphiks.kadre.policy.KadrePolicies

/**
 * Exécutable publication proof (design WAYLAND-PHASE-11-CLOSURE-DESIGN D4) :
 * le consumer — compilé contre l'artefact PUBLIÉ, jamais les projets — mène
 * une session standalone headless jusqu'à son terminaison réelle contre le
 * compositor de l'environnement. Linux + WAYLAND_DISPLAY requis : c'est le
 * job d'acceptation `kadre-wayland-compositor-matrix` (famille consumers)
 * qui l'exécute ; l'absence explicite est un skip documenté, pas un faux
 * succès.
 */
class ConsumerSessionTest {

    @Test
    fun headlessSessionCompletesAgainstThePublishedArtifact() {
        val runtime = System.getenv("XDG_RUNTIME_DIR")
        val display = System.getenv("WAYLAND_DISPLAY")
        org.junit.jupiter.api.Assumptions.assumeTrue(
            display != null && runtime != null && Files.exists(Path.of(runtime, display)),
            "no live compositor — this proof runs under the compositor matrix job",
        )
        val outcome = runKadreApplication(
            DesktopHostOptions.Standalone(),
            KadrePolicies.Default,
        ) { KadreApplication { } }
        assertEquals(SessionOutcome.Completed, outcome)
    }
}
