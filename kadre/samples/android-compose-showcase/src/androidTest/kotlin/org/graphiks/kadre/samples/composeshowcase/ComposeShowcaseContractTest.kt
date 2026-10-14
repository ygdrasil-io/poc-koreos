package org.graphiks.kadre.samples.composeshowcase

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.DexFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.application.SessionStopReason
import org.junit.runner.RunWith

/**
 * Les deux scénarios instrumentés du contrat AND-010 portés par le sample compose-showcase :
 *
 * | scenario id (registre)            | méthode                          | sentinelle gardée        |
 * |-----------------------------------|----------------------------------|--------------------------|
 * | android-compose-showcase-single-session | androidComposeShowcaseSingleSession | android-compose-no-double-attach |
 * | android-legacy-types-absent       | androidLegacyTypesAbsent         | -                        |
 *
 * Règle de thread : les bascules d'état compose passent par le thread principal
 * (`ActivityScenario.onActivity`), les attentes vivent sur le thread d'instrumentation —
 * le looper main reste libre de composer et de publier.
 */
@RunWith(AndroidJUnit4::class)
class ComposeShowcaseContractTest {

    /**
     * La démo EST le scénario : recomposition ×3 → UNE session ; sortie de composition →
     * fermée ; re-compose → recréée (la machine create/keep/close/recreate de la liaison
     * publiée, prouvée sur l'activity réelle du sample).
     */
    @Test
    fun androidComposeShowcaseSingleSession() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // — create : la première composition, le conteneur rejoint la fenêtre, UNE session —
            val first = awaitOnActivity(scenario, "the first live session") { it.liveSession }

            // — keep : recomposition ×3 (le tick est lu DANS le slot content) — chaque
            //   recomposition ré-enregistre LA MÊME session, jamais une nouvelle.
            for (i in 1..3) {
                scenario.onActivity { it.tick = i }
                awaitOnActivity(scenario, "recomposition #$i to be recorded") {
                    it.seenSessions.size > i
                }
            }
            scenario.onActivity { activity ->
                assertEquals(
                    1,
                    activity.seenSessions.toSet().size,
                    "recomposition must not create a session, saw ${activity.seenSessions}",
                )
                assertSame(first, activity.liveSession, "the live session must be the first one")
            }

            // — close : sortie de composition → la session est fermée —
            scenario.onActivity { it.showHost = false }
            // Sur une fenêtre RÉELLE, la sortie de composition retire la vue (le détachement
            // précède l'onDispose dans l'application de Compose) : le terminal est l'arrêt
            // host du runtime — `Stopped(HostDetached)` (le chemin dispose pur reste
            // `Stopped(HostRequested)`, épinglé par les tests hôtes de la liaison). Les DEUX
            // raisons sont les deux chemins d'arrêt documentés de la machine ; le contrat du
            // scénario est la TERMINATION UNIQUE et la recréation.
            val disposedOutcome = awaitTerminal(first)
            assertTrue(
                disposedOutcome is SessionOutcome.Stopped &&
                    (
                        disposedOutcome.reason == SessionStopReason.HostRequested ||
                            disposedOutcome.reason == SessionStopReason.HostDetached
                        ),
                "the disposed session must stop as a host stop, got $disposedOutcome",
            )

            // — recreate : le host compose à nouveau → une session NOUVELLE sur une nouvelle
            //   vue (les captures de création de la première composition sont mortes avec
            //   elle ; la nouvelle composition apporte les siennes, review T3 M-2) —
            scenario.onActivity { it.showHost = true }
            val second = awaitOnActivity(scenario, "the recreated session") { activity ->
                activity.liveSession?.takeIf { it !== first }
            }
            awaitRunning(second)
            assertNotSame(first, second, "the recomposed host must own a NEW session")
        }
    }

    /**
     * Le consumer négatif : le bytecode réellement exécuté — l'APK du sample, où les classes
     * de l'AAR `platform-android` PUBLIÉ sont fusionnées — ne contient NI
     * `AndroidKadreRuntime` NI `EventLoop` (les types kadre-old). Ils n'ont jamais été dans
     * le nouveau build ; le scan du dex le prouve depuis l'artefact réel, pas depuis une
     * intention. Deux gardes encadrent l'assertion négative : le scan couvre bien le code
     * platform-android (les paquets `org.graphiks.kadre.platform.android` / `.host.android`
     * sont présents), et les FQN cherchés sont les FQN exacts kadre-old — l'`EventLoop` de
     * kotlinx-coroutines (présent, légitime) n'est pas un faux positif.
     */
    @Test
    fun androidLegacyTypesAbsent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val apkPath = requireNotNull(context.applicationInfo.sourceDir) {
            "the sample APK path must be resolvable"
        }
        // Le stub android.jar ne déclare PAS Closeable/AutoCloseable sur DexFile : le close
        // passe par un try/finally explicite (l'entries() n'alloue rien de durable).
        @Suppress("DEPRECATION")
        val dex = DexFile(apkPath)
        try {
            val classNames = dex.entries().asSequence().toList()
            assertTrue(classNames.isNotEmpty(), "the dex scan must enumerate classes")

            // Garde de couverture : le scan porte sur l'APK qui embarque le code platform-android.
            assertTrue(
                classNames.any { it.startsWith("org.graphiks.kadre.platform.android.") },
                "the scan must cover the real platform-android classes",
            )
            assertTrue(
                classNames.any { it.startsWith("org.graphiks.kadre.host.android.") },
                "the scan must cover the real host facade classes",
            )

            // Les types kadre-old exacts : `org.graphiks.kadre.EventLoop` (kadre-old/kadre),
            // `org.graphiks.kadre.core.EventLoop` (kadre-old/kadre-core) et
            // `org.graphiks.kadre.android.AndroidKadreRuntime` (kadre-old/kadre-android).
            val legacy = classNames.filter { name ->
                name == "org.graphiks.kadre.EventLoop" ||
                    name.startsWith("org.graphiks.kadre.EventLoop$") ||
                    name == "org.graphiks.kadre.core.EventLoop" ||
                    name.startsWith("org.graphiks.kadre.core.EventLoop$") ||
                    name.contains("AndroidKadreRuntime")
            }
            assertTrue(
                legacy.isEmpty(),
                "kadre-old legacy types present in the executed dex: $legacy",
            )
        } finally {
            @Suppress("DEPRECATION")
            dex.close()
        }
    }

    /** La lecture d'un état main-confined, attendue par observation bornée (10 s). */
    private fun <T> awaitOnActivity(
        scenario: ActivityScenario<MainActivity>,
        description: String,
        read: (MainActivity) -> T?,
    ): T {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        var last: T? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity -> last = read(activity) }
            if (last != null) {
                @Suppress("UNCHECKED_CAST")
                return last as T
            }
            Thread.sleep(25L)
        }
        fail("the sample never exposed $description within ${TIMEOUT_MS}ms")
    }

    /** La terminaison de la session — polling du StateFlow depuis le thread d'instrumentation. */
    private fun awaitTerminal(session: KadreSession): SessionOutcome {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            val state = session.state.value
            if (state is SessionState.Terminated) return state.outcome
            Thread.sleep(25L)
        }
        fail("the session never terminated within ${TIMEOUT_MS}ms; last=${session.state.value}")
    }

    /** L'état Running de la session recréée — le même polling borné. */
    private fun awaitRunning(session: KadreSession) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (session.state.value == SessionState.Running) return
            Thread.sleep(25L)
        }
        fail("the recreated session never reached Running within ${TIMEOUT_MS}ms; last=${session.state.value}")
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
