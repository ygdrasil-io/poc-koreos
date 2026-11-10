package org.graphiks.kadre

import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreRunOptions
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KadreEntryTest {
    @Test
    fun runKadreApplicationReturnsATypedResultAndNeverThrows() {
        // L'entrée umbrella réelle (façade par défaut, découverte ServiceLoader) : il n'y a
        // pas de catalogue injectable au niveau umbrella. Les backends sont `runtimeOnly`
        // de platform:desktop et se propagent au classpath runtime du test — selon l'hôte,
        // l'entrée rencontre soit aucun provider disponible (Unsupported(HostAttach)),
        // soit un vrai provider qui refuse un thread de test (InvalidRequest), soit une
        // boucle native manquante (PlatformFailure) — soit, sur un hôte où un backend
        // tourne depuis un thread de test (ex. Wayland), une vraie session qui termine
        // avec un SessionOutcome typé. Le contrat sous test est le même partout (ruling
        // Task 5) : un résultat typé — échec EXPLICITE du domaine des échecs d'hôte ou
        // SessionOutcome — jamais une exception, jamais de crash Main, sur une JVM sans
        // implémentation Main installée.
        val result = runBlocking {
            runKadreApplication(
                options = KadreRunOptions(),
                application = KadreApplication { },
            )
        }

        when (result) {
            is KadreResult.Success -> assertIs<SessionOutcome>(result.value)
            is KadreResult.Failure -> assertTrue(
                when (result.reason) {
                    is KadreFailure.Unsupported,
                    is KadreFailure.InvalidRequest,
                    is KadreFailure.PlatformFailure,
                    -> true
                    else -> false
                },
                "unexpected failure kind: ${result.reason}",
            )
        }
    }
}
