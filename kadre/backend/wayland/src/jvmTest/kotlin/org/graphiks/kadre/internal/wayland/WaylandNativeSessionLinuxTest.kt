package org.graphiks.kadre.internal.wayland

import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.contracts.driver.wayland.WaylandCompositorController
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.desktop.DesktopStandaloneRequest
import org.graphiks.kadre.policy.KadrePolicies

/**
 * Scénarios O3 frontière externe (roadmap §7) contre un compositor réel.
 * Exécutés par la tâche `waylandNativeTests` (Linux uniquement) ; hors Linux
 * la tâche est désactivée — sur Linux, un compositor requis indisponible
 * fait ÉCHOUER le gate, jamais un skip silencieux.
 */
class WaylandNativeSessionLinuxTest {

    private fun compositor(): WaylandCompositorController =
        WaylandCompositorController(socketName = "kadre-wayland-o3").also { it.start() }

    private fun environmentFor(controller: WaylandCompositorController): WaylandProbeEnvironment =
        WaylandProbeEnvironment(
            isLinux = true,
            waylandSocket = null,
            xdgRuntimeDir = "/run/user/0",
            waylandDisplay = controller.socketAddress,
            socketExists = { true },
        )

    private fun providerFor(
        controller: WaylandCompositorController,
        loopStarted: ((WaylandEventPump) -> Unit)? = null,
    ): WaylandBackendProvider =
        WaylandBackendProvider.forTesting(
            availability = { true },
            environment = { environmentFor(controller) },
            bridgeFactory = { name -> KffiWaylandBridge.connect(name) },
            loopStarted = loopStarted,
        )

    private fun aliveApplication(): DesktopStandaloneRequest =
        DesktopStandaloneRequest(
            applicationFactory = KadreApplicationFactory {
                KadreApplication { awaitCancellation() }
            },
            stopWhenLastWindowClosed = true,
            policy = KadrePolicies.Default,
        )

    private fun completingRequest(): DesktopStandaloneRequest =
        DesktopStandaloneRequest(
            applicationFactory = KadreApplicationFactory { KadreApplication { } },
            stopWhenLastWindowClosed = true,
            policy = KadrePolicies.Default,
        )

    private fun openDescriptorCount(): Int =
        Files.list(Path.of("/proc/self/fd")).use { it.count().toInt() }

    @Test
    fun threeCyclesLeaveNoFdBehind() {
        compositor().use { controller ->
            val provider = providerFor(controller)
            val before = openDescriptorCount()
            repeat(3) {
                val result = provider.run(completingRequest())
                assertEquals(KadreResult.Success(SessionOutcome.Completed), result)
            }
            val after = openDescriptorCount()
            assertTrue(after <= before, "fd leak across cycles: before=$before after=$after")
        }
    }

    @Test
    fun repeatedWakeKeepsThePumpResponsive() {
        compositor().use { controller ->
            // la session traverse plusieurs cycles de poll silencieux
            // (compositor sans événement) avant l'arrêt demandé
            val provider = providerFor(controller) { pump ->
                Thread {
                    Thread.sleep(500)
                    pump.requestStop()
                }.start()
            }
            val outcome = provider.run(aliveApplication())
            assertEquals(KadreResult.Success(SessionOutcome.Stopped(SessionStopReason.HostRequested)), outcome)
        }
    }

    @Test
    fun stopFromAnotherThreadDuringPoll() {
        compositor().use { controller ->
            val provider = providerFor(controller) { pump ->
                Thread { pump.requestStop() }.start()
            }
            val outcome = provider.run(aliveApplication())
            assertEquals(KadreResult.Success(SessionOutcome.Stopped(SessionStopReason.HostRequested)), outcome)
        }
    }

    @Test
    fun absentServerFailsBeforeSession() {
        val absentDirectory = Files.createTempDirectory("kadre-wayland-absent")
        val provider = WaylandBackendProvider.forTesting(
            availability = { true },
            environment = {
                WaylandProbeEnvironment(
                    isLinux = true,
                    waylandSocket = null,
                    xdgRuntimeDir = absentDirectory.toString(),
                    waylandDisplay = "no-such-socket",
                    socketExists = { true },
                )
            },
            bridgeFactory = { name -> KffiWaylandBridge.connect(name) },
        )
        val result = provider.run(completingRequest())
        assertEquals(
            KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.Wayland, "start", "display-connect-failed"),
            ),
            result,
        )
    }

    @Test
    fun realWaylandSocketFdFailsUntilKffi001() {
        compositor().use { controller ->
            // un fd réel, jamais consommé ni par le probe ni par le run (R3/F1-4)
            val stream = FileInputStream("/dev/null")
            val fdField = java.io.FileDescriptor::class.java.getDeclaredField("fd").apply { isAccessible = true }
            val fdNumber = fdField.getInt(stream.fd)
            assertTrue(fdNumber >= 0)
            val provider = WaylandBackendProvider.forTesting(
                availability = { true },
                environment = { environmentFor(controller).copy(waylandSocket = fdNumber.toString()) },
                bridgeFactory = { name -> KffiWaylandBridge.connect(name) },
            )
            val result = provider.run(completingRequest())
            assertEquals(
                KadreResult.Failure(
                    KadreFailure.PlatformFailure(KadrePlatform.Wayland, "start", "wayland-socket-unsupported"),
                ),
                result,
            )
            // le fd n'a pas été consommé : le canal reste utilisable
            stream.read()
            stream.close()
        }
    }
}
