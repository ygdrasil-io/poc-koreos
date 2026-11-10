package org.graphiks.kadre

import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreRunOptions
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KadreEntryTest {
    @Test
    fun runKadreApplicationReturnsAnExplicitFailureWhenItCannotRun() {
        // L'entrée umbrella réelle (façade par défaut, découverte ServiceLoader) : il n'y a
        // pas de catalogue injectable au niveau umbrella. Les backends sont `runtimeOnly`
        // de platform:desktop et se propagent au classpath runtime du test — selon l'hôte,
        // l'entrée rencontre soit aucun provider disponible (Unsupported(HostAttach)),
        // soit un vrai provider qui refuse un thread de test (InvalidRequest), soit une
        // boucle native manquante (PlatformFailure). Le contrat est le même partout :
        // un échec EXPLICITE dans le domaine des échecs d'hôte — jamais une exception,
        // jamais de crash Main — sur une JVM sans implémentation Main installée
        // (Review Focus 2, ruling Task 5).
        val result = runBlocking {
            runKadreApplication(
                options = KadreRunOptions(),
                application = KadreApplication { },
            )
        }

        val failure = assertIs<KadreResult.Failure>(result)
        assertTrue(
            when (failure.reason) {
                is KadreFailure.Unsupported,
                is KadreFailure.InvalidRequest,
                is KadreFailure.PlatformFailure,
                -> true
                else -> false
            },
            "unexpected failure kind: ${failure.reason}",
        )
    }
}
