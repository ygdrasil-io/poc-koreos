package org.graphiks.kadre.internal.wayland

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.contracts.driver.wayland.WaylandCompositorController
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.desktop.DesktopStandaloneRequest
import org.graphiks.kadre.platform.desktop.DesktopNativeWindowHandle
import org.graphiks.kadre.platform.desktop.withDesktopHandle
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.window.Window
import org.graphiks.kadre.window.WindowDecorations
import org.graphiks.kadre.window.WindowPhase
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kadre.surface.LogicalSize

/**
 * Preuves O3 fenêtres contre un compositor réel (design
 * `WAYLAND-PHASE-2-WINDOWS-DESIGN.md`) : deux fenêtres mappées et
 * indépendantes avec succession primary (P-1), propriétés initiales
 * conformes sur configure non-imposé (P-2/F1-4), quiescence après destroy
 * (SN-5) et course de fermeture (SN-6). Exécutées par la tâche
 * `waylandNativeTests` (Linux uniquement) ; hors Linux la tâche est
 * désactivée — sur Linux, un compositor requis indisponible fait ÉCHOUER le
 * gate, jamais un skip silencieux.
 */
@OptIn(
    org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
    org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
)
class WaylandWindowsLinuxTest {

    private fun compositor(): WaylandCompositorController =
        WaylandCompositorController(socketName = "kadre-wayland-windows-o3").also { it.start() }

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

    private suspend fun KadreScope.openWindow(title: String, width: Double, height: Double): Window =
        when (val outcome = windows
            .requestWindow(
                WindowSpec(
                    title = title,
                    contentSize = LogicalSize(width, height),
                    decorations = WindowDecorations.Borderless,
                ),
            )
            .let { request ->
                when (request) {
                    is KadreResult.Success -> request.value.await()
                    is KadreResult.Failure -> WindowRequestOutcome.Rejected(request.reason)
                }
            }
        ) {
            is WindowRequestOutcome.OpenedHere -> outcome.window
            else -> error("window $title was not opened: $outcome")
        }

    private fun <T> KadreResult<T>.orThrow(): T = when (this) {
        is KadreResult.Success -> value
        is KadreResult.Failure -> error("expected success, got $reason")
    }

    @Test
    fun twoWindowsAreMappedAndIndependentWithDeterministicSuccession() {
        compositor().use { controller ->
            val provider = providerFor(controller)
            val request = DesktopStandaloneRequest(
                applicationFactory = KadreApplicationFactory {
                    KadreApplication {
                        val a = openWindow("p1-a", 96.0, 64.0)
                        val b = openWindow("p1-b", 64.0, 64.0)
                        assertEquals(WindowPhase.Open, a.state.value.phase)
                        assertEquals(WindowPhase.Open, b.state.value.phase)
                        assertEquals(org.graphiks.kadre.window.WindowCloseOutcome.Closed, a.close().orThrow())
                        // SN-5 : après le destroy de A, la pompe continue de
                        // tourner et B reste parfaitement saine
                        delay(300)
                        assertEquals(WindowPhase.Open, b.state.value.phase)
                        // lease des adresses natives sur la fenêtre survivante
                        val handle = b.withDesktopHandle { it }.orThrow()
                        val wayland = assertIs<DesktopNativeWindowHandle.Wayland>(handle)
                        assertTrue(wayland.displayAddress != 0uL, "adresse wl_display nulle")
                        assertTrue(wayland.surfaceAddress != 0uL, "adresse wl_surface nulle")
                        assertEquals(org.graphiks.kadre.window.WindowCloseOutcome.Closed, b.close().orThrow())
                    }
                },
                stopWhenLastWindowClosed = true,
                policy = KadrePolicies.Default,
            )
            val result = provider.run(request)
            val outcome = assertIs<KadreResult.Success<SessionOutcome>>(result).value
            assertTrue(
                outcome == SessionOutcome.Completed ||
                    outcome == SessionOutcome.Stopped(SessionStopReason.HostDetached),
                "unexpected session outcome: $outcome",
            )
        }
    }

    @Test
    fun initialPropertiesArePreservedOnANonImposingConfigure() {
        compositor().use { controller ->
            val provider = providerFor(controller)
            val request = DesktopStandaloneRequest(
                applicationFactory = KadreApplicationFactory {
                    KadreApplication {
                        // P-2/F1-4 : le configure non-imposé de Weston ne doit
                        // ni écraser le titre ni la taille demandée
                        val window = openWindow("p2", 100.0, 50.0)
                        val state = window.state.value
                        assertEquals(WindowPhase.Open, state.phase)
                        assertEquals("p2", state.title)
                        assertEquals(LogicalSize(100.0, 50.0), state.contentSize)
                        assertEquals(WindowDecorations.Borderless, state.decorations)
                        assertEquals(org.graphiks.kadre.window.WindowCloseOutcome.Closed, window.close().orThrow())
                    }
                },
                stopWhenLastWindowClosed = true,
                policy = KadrePolicies.Default,
            )
            assertIs<KadreResult.Success<SessionOutcome>>(provider.run(request))
        }
    }

    @Test
    fun secondCloseAfterTerminalCloseKeepsTheSessionHealthy() {
        compositor().use { controller ->
            val provider = providerFor(controller)
            val request = DesktopStandaloneRequest(
                applicationFactory = KadreApplicationFactory {
                    KadreApplication {
                        val window = openWindow("sn6", 64.0, 64.0)
                        assertEquals(org.graphiks.kadre.window.WindowCloseOutcome.Closed, window.close().orThrow())
                        assertEquals(WindowPhase.Closed, window.state.value.phase)
                        // SN-6 : une seconde fermeture après la terminaison ne
                        // casse rien et préserve l'état terminal
                        window.close()
                        assertEquals(WindowPhase.Closed, window.state.value.phase)
                    }
                },
                stopWhenLastWindowClosed = true,
                policy = KadrePolicies.Default,
            )
            assertIs<KadreResult.Success<SessionOutcome>>(provider.run(request))
        }
    }
}
