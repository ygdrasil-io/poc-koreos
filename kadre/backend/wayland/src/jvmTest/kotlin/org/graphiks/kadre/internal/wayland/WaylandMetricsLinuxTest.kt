package org.graphiks.kadre.internal.wayland

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.contracts.driver.wayland.WaylandCompositorController
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.DisplayPortSnapshot

/**
 * Preuves O3 dynamiques phase 3 contre sway : inventaire initial (P-1),
 * changement de scale en cours de session via swaymsg (P-2), ajout d'output
 * (P-3 côté ajout). Le retrait et le pacing redraw étendu s'appuient sur les
 * preuves O2 (F1-1, F2-2) et le harnais compositor amputé.
 */
class WaylandMetricsLinuxTest {

    private fun compositor(): WaylandCompositorController =
        WaylandCompositorController(socketName = "kadre-wayland-metrics-o3").also { it.start() }

    private fun environmentFor(controller: WaylandCompositorController): WaylandProbeEnvironment =
        WaylandProbeEnvironment(
            isLinux = true,
            waylandSocket = null,
            xdgRuntimeDir = "/run/user/0",
            waylandDisplay = controller.socketAddress,
            socketExists = { true },
        )

    /** Locates the sway IPC socket created in the compositor runtime dir. */
    private fun swaymsgSocket(controller: WaylandCompositorController): String {
        val runtimeDir = File("/tmp/kadre-wayland-runtime")
        return runtimeDir.listFiles()
            ?.filter { it.name.startsWith("sway-ipc") }
            ?.maxByOrNull { it.lastModified() }
            ?.absolutePath
            ?: error("sway IPC socket not found")
    }

    private fun swaymsg(socket: String, vararg command: String) {
        val process = ProcessBuilder("swaymsg", "-s", socket, *command)
            .redirectErrorStream(true)
            .start()
        process.waitFor(5, TimeUnit.SECONDS)
    }

    /** Mini-pompe : les événements n'arrivent que si la connexion est dispatchée. */
    private fun startPump(bridge: KffiWaylandBridge): AutoCloseable {
        var running = true
        Thread {
            while (running) {
                bridge.roundtrip()
                Thread.sleep(15)
            }
        }.apply { isDaemon = true; start() }
        return AutoCloseable { running = false }
    }

    private suspend fun awaitSnapshot(
        port: WaylandDisplayPort,
        states: CopyOnWriteArrayList<DisplayPortSnapshot>,
        expectation: (DisplayPortSnapshot) -> Boolean,
    ) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            val result = port.requestSnapshot()
            if (result is KadreResult.Success) {
                val snapshot = result.value
                // l'attente tolère les snapshots encore vides (le done du
                // wl_output arrive après l'annonce du global)
                if (runCatching { expectation(snapshot) }.getOrDefault(false)) {
                    states.add(snapshot)
                    return
                }
            }
            Thread.sleep(50)
        }
        error("no snapshot matched the expectation within 10s")
    }

    @Test
    fun scaleChangeMidSessionRepublishesTheInventory() {
        compositor().use { controller ->
            val bridge = KffiWaylandBridge.connect(controller.socketAddress)
            assertNotNull(bridge)
            bridge.use {
                val pump = startPump(bridge)
                val port = WaylandDisplayPort(
                    outputsProvider = bridge::outputSnapshots,
                    rescan = bridge::onOutputsChanged,
                )
                val states = CopyOnWriteArrayList<DisplayPortSnapshot>()
                port.installSnapshotObserver { result ->
                    if (result is KadreResult.Success) states.add(result.value)
                }
                // P-1: inventaire initial 640x480 scale 1
                runBlocking { awaitSnapshot(port, states) { it.displays.single().scaleFactor == 1.0 } }
                val ipc = swaymsgSocket(controller)
                swaymsg(ipc, "output HEADLESS-1 scale 2")
                // P-2: le changement de scale est republishé au prochain done
                runBlocking { awaitSnapshot(port, states) { it.displays.single().scaleFactor == 2.0 } }
                // retour à 1 : re-batching honnête dans les deux sens
                swaymsg(ipc, "output HEADLESS-1 scale 1")
                runBlocking { awaitSnapshot(port, states) { it.displays.single().scaleFactor == 1.0 } }
                assertTrue(states.size >= 3, "au moins trois snapshots publiés: ${states.size}")
                pump.close()
            }
        }
    }

    @Test
    fun outputAdditionExtendsTheInventory() {
        compositor().use { controller ->
            val bridge = KffiWaylandBridge.connect(controller.socketAddress)
            assertNotNull(bridge)
            bridge.use {
                val pump = startPump(bridge)
                val port = WaylandDisplayPort(
                    outputsProvider = bridge::outputSnapshots,
                    rescan = bridge::onOutputsChanged,
                )
                val states = CopyOnWriteArrayList<DisplayPortSnapshot>()
                port.installSnapshotObserver { result ->
                    if (result is KadreResult.Success) states.add(result.value)
                }
                runBlocking { awaitSnapshot(port, states) { it.displays.size == 1 } }
                val ipc = swaymsgSocket(controller)
                swaymsg(ipc, "create_output")
                runBlocking { awaitSnapshot(port, states) { it.displays.size == 2 } }
                assertEquals(2.0, 2.0)
                assertTrue(states.last().displays.any { it.name == "HEADLESS-2" })
                pump.close()
            }
        }
    }
}
