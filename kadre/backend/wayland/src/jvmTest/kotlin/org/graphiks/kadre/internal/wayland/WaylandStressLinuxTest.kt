package org.graphiks.kadre.internal.wayland

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.contracts.driver.wayland.WaylandCompositorController
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.desktop.DesktopStandaloneRequest
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.window.Window
import org.graphiks.kadre.window.WindowPhase
import org.graphiks.kadre.window.WindowSpec

/**
 * Preuves O3 de stress (design WAYLAND-PHASE-11-CLOSURE-DESIGN D5) :
 * rafales de fenêtres sur les files du driver, interruption brutale du
 * compositor en cours de session, et teardown répété — chaque preuve
 * borne son exécution (jamais de boucle infinie sur le runner) et
 * compare les descripteurs ouverts avant/après (zéro fuite). Tâche
 * dédiée `waylandStressTests` (Linux uniquement), hors gate bloquante.
 */
@OptIn(
    org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
    org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
)
class WaylandStressLinuxTest {

    private fun compositor(): WaylandCompositorController =
        WaylandCompositorController(socketName = "kadre-wayland-stress-o3").also { it.start() }

    private fun environmentFor(controller: WaylandCompositorController): WaylandProbeEnvironment =
        WaylandProbeEnvironment(
            isLinux = true,
            waylandSocket = null,
            xdgRuntimeDir = "/run/user/0",
            waylandDisplay = controller.socketAddress,
            socketExists = { true },
        )

    private fun providerFor(controller: WaylandCompositorController): WaylandBackendProvider =
        WaylandBackendProvider.forTesting(
            availability = { true },
            environment = { environmentFor(controller) },
            bridgeFactory = { name -> KffiWaylandBridge.connect(name) },
        )

    private fun openDescriptorCount(): Int =
        Files.list(Path.of("/proc/self/fd")).use { it.count().toInt() }

    private suspend fun KadreScope.openWindow(title: String): Window =
        when (
            val outcome = windows
                .requestWindow(
                    WindowSpec(
                        title = title,
                        contentSize = LogicalSize(64.0, 48.0),
                        decorations = org.graphiks.kadre.window.WindowDecorations.Borderless,
                    ),
                )
                .let { request ->
                    when (request) {
                        is KadreResult.Success -> request.value.await()
                        is KadreResult.Failure -> org.graphiks.kadre.window.WindowRequestOutcome.Rejected(request.reason)
                    }
                }
        ) {
            is org.graphiks.kadre.window.WindowRequestOutcome.OpenedHere -> outcome.window
            else -> error("window $title was not opened: $outcome")
        }

    private suspend fun awaitClosed(window: Window) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (window.state.value.phase != WindowPhase.Closed) {
            if (System.nanoTime() > deadline) {
                error("window never closed: ${window.state.value.phase}")
            }
            kotlinx.coroutines.delay(10)
        }
    }

    @Test
    fun windowChurnBurstsKeepTheDriverQueuesDrained() {
        compositor().use { controller ->
            val provider = providerFor(controller)
            val before = openDescriptorCount()
            // 6 rafales × 8 fenêtres ouvertes puis fermées : la file du
            // driver encaisse les créations/destroys sans retarder le
            // terminaison ni fuir
            val request = DesktopStandaloneRequest(
                applicationFactory = KadreApplicationFactory {
                    KadreApplication {
                        repeat(6) { burst ->
                            val windows = (0 until 8).map { openWindow("churn-$burst-$it") }
                            windows.forEach { window ->
                                run { window.close(); awaitClosed(window) }
                            }
                        }
                    }
                },
                stopWhenLastWindowClosed = false,
                policy = KadrePolicies.Default,
            )
            val result = provider.run(request)
            val outcome = (result as? KadreResult.Success<SessionOutcome>)?.value
                ?: error("churn session failed: $result")
            assertTrue(
                outcome == SessionOutcome.Completed ||
                    outcome == org.graphiks.kadre.application.SessionStopReason.HostDetached.let(SessionOutcome::Stopped),
                "unexpected session outcome: $outcome",
            )
            val after = openDescriptorCount()
            assertTrue(after <= before, "fd leak across churn: before=$before after=$after")
        }
    }

    @Test
    fun compositorInterruptionSurfacesConnectionLostWithoutLeaking() {
        // le test possède SON compositor : en mode externalisé (gate sway)
        // le contrôleur partagé ne peut pas être tué — on lance un sway
        // dédié sur un runtime privé et on le détruit sous la session
        val runtime = Files.createTempDirectory("kadre-stress-interrupt")
        val process = ProcessBuilder("sway")
            .apply {
                environment()["XDG_RUNTIME_DIR"] = runtime.toString()
                environment()["WLR_BACKENDS"] = "headless"
                environment()["WLR_RENDERER"] = "pixman"
                environment()["WLR_LIBINPUT_NO_DEVICES"] = "1"
                redirectErrorStream(true)
                redirectOutput(ProcessBuilder.Redirect.DISCARD)
            }
            .start()
        try {
            val socket = waitForSocket(runtime)
            // libwayland accepte un chemin absolu comme nom de socket : le
            // compositor dédié vit dans un runtime privé, indépendant du
            // XDG_RUNTIME_DIR du processus (gate sway partagé)
            val socketPath = runtime.resolve(socket).toString()
            val provider = WaylandBackendProvider.forTesting(
                availability = { true },
                environment = {
                    WaylandProbeEnvironment(
                        isLinux = true,
                        waylandSocket = null,
                        xdgRuntimeDir = runtime.toString(),
                        waylandDisplay = socketPath,
                        socketExists = { true },
                    )
                },
                bridgeFactory = { name -> KffiWaylandBridge.connect(name) },
            )
            val before = openDescriptorCount()
            val runner = Thread {
                val result = provider.run(
                    DesktopStandaloneRequest(
                        applicationFactory = KadreApplicationFactory {
                            KadreApplication { awaitCancellation() }
                        },
                        stopWhenLastWindowClosed = true,
                        policy = KadrePolicies.Default,
                    ),
                )
                lastResult.set(result)
            }.apply { isDaemon = true; start() }
            Thread.sleep(1_500)
            // interruption brutale (SIGKILL) : la connexion tombe, pas le JVM
            process.destroyForcibly()
            process.waitFor()
            runner.join(30_000)
            assertTrue(!runner.isAlive, "session must terminate after the compositor dies")
            // deux issues honnêtes : la déconnexion observée en session
            // (Success(Failed(connection-lost))) ou, si le kill survient
            // pendant le démarrage, l'échec typé AVANT session — jamais un
            // crash ni un succès
            val result = lastResult.get()
            val failure = when (result) {
                is KadreResult.Success -> (result.value as? SessionOutcome.Failed)?.failure
                is KadreResult.Failure -> result.reason
                null -> null
            }
            assertTrue(
                failure is KadreFailure.PlatformFailure,
                "expected a typed platform failure, got $result",
            )
            val after = openDescriptorCount()
            assertTrue(after <= before + 2, "fd leak after interruption: before=$before after=$after")
        } finally {
            process.destroyForcibly()
            runtime.toFile().deleteRecursively()
        }
    }

    /** Dernier résultat de session observé par le thread runner. */
    private val lastResult = java.util.concurrent.atomic.AtomicReference<KadreResult<SessionOutcome>?>()

    private fun waitForSocket(runtime: Path): String {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            val found = Files.list(runtime).use { stream ->
                stream.filter { it.fileName.toString().startsWith("wayland-") }
                    .findFirst()
                    .orElse(null)
            }
            if (found != null) return found.fileName.toString()
            Thread.sleep(100)
        }
        error("dedicated sway did not publish a socket in $runtime")
    }

    /** Sentinelle : sans compositor, aucune session n'est fabriquée —
     *  échec typé AVANT session, jamais un succès inventé ni un crash ;
     *  l'application ATTENDANTE garantit que seul un mensonge terminerait. */
    @Test
    fun absentCompositorRefusesSessionsWithTypedFailure() {
        val runtime = Files.createTempDirectory("kadre-stress-absent")
        val provider = WaylandBackendProvider.forTesting(
            availability = { true },
            environment = {
                WaylandProbeEnvironment(
                    isLinux = true,
                    waylandSocket = null,
                    xdgRuntimeDir = runtime.toString(),
                    waylandDisplay = "no-such-compositor",
                    socketExists = { true },
                )
            },
            bridgeFactory = { name -> KffiWaylandBridge.connect(name) },
        )
        val before = openDescriptorCount()
        val result = provider.run(
            DesktopStandaloneRequest(
                applicationFactory = KadreApplicationFactory {
                    KadreApplication { awaitCancellation() }
                },
                stopWhenLastWindowClosed = true,
                policy = KadrePolicies.Default,
            ),
        )
        val failure = (result as? KadreResult.Failure)?.reason
        assertTrue(
            failure is KadreFailure.PlatformFailure,
            "expected a typed platform failure, got $result",
        )
        val after = openDescriptorCount()
        assertTrue(after <= before, "fd leak on absent compositor: before=$before after=$after")
        runtime.toFile().deleteRecursively()
    }

    @Test
    fun repeatedTeardownStaysBoundedAndLeakFree() {
        compositor().use { controller ->
            val provider = providerFor(controller)
            val before = openDescriptorCount()
            val began = System.nanoTime()
            val cycles = 12
            repeat(cycles) {
                val result = provider.run(
                    DesktopStandaloneRequest(
                        applicationFactory = KadreApplicationFactory { KadreApplication { } },
                        stopWhenLastWindowClosed = true,
                        policy = KadrePolicies.Default,
                    ),
                )
                check(result is KadreResult.Success) { "teardown cycle failed: $result" }
            }
            val elapsedSeconds = (System.nanoTime() - began) / 1_000_000_000.0
            assertTrue(elapsedSeconds < 90.0, "teardown cycles too slow: ${elapsedSeconds}s for $cycles cycles")
            val after = openDescriptorCount()
            assertTrue(after <= before, "fd leak across teardown: before=$before after=$after")
        }
    }
}
