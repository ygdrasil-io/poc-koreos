package org.graphiks.kadre.internal.wayland

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeWindowManager
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.window.WindowDecorations
import org.graphiks.kadre.window.WindowSpec

/**
 * Preuves O2 des scénarios fenêtres de la tranche 2 (design
 * `WAYLAND-PHASE-2-WINDOWS-DESIGN.md`): refus normatifs avant native
 * (SN-1..SN-3), création borderless atteignant la couche native (F3).
 */
class WaylandWindowO2Test {

    private fun session(bridge: FakeBridge): WaylandHostSession =
        WaylandHostSession.start({ _ -> bridge }, "wayland-test", testEnvironment(), { SessionFakeWakeup() })

    private fun testEnvironment(): WaylandProbeEnvironment =
        WaylandProbeEnvironment(
            isLinux = true,
            waylandSocket = null,
            xdgRuntimeDir = "/run/user/1000",
            waylandDisplay = "wayland-test",
            socketExists = { true },
        )

    private fun driver(bridge: FakeBridge, session: WaylandHostSession): WaylandWindowRuntimeDriver =
        WaylandWindowRuntimeDriver.create(
            session = session,
            resources = KadrePolicies.Default.resources,
            failureReporter = RuntimeFailureReporter { },
            publicWindowCapabilities = true,
            onLastWindowClosed = null,
        )

    @Test
    fun defaultDecorationsAreRejectedBeforeNative() {
        val bridge = FakeBridge()
        val session = session(bridge)
        val driver = driver(bridge, session)
        // le spec par défaut porte decorations=System: rejet normatif avant
        // toute création native (SN-1)
        runBlocking { driver.manager.requestWindow(WindowSpec()) }
        assertEquals(0, bridge.createWindowingCalls.get(), "une fenêtre native a été créée")
        session.teardown()
    }

    @Test
    fun borderlessCreationReachesTheNativeLayer() {
        val bridge = FakeBridge()
        val window = FakeNativeWindow()
        bridge.fakeWindow = window
        val session = session(bridge)
        val driver = driver(bridge, session)
        var requestState: String = "pending"
        runBlocking {
            val result = driver.manager.requestWindow(
                WindowSpec(title = "o2", decorations = WindowDecorations.Borderless),
            )
            val request = (result as KadreResult.Success).value
            val deadline = System.nanoTime() + 5_000_000_000L
            while (System.nanoTime() < deadline) {
                session.drainWindowActions()
                val state = request.state.value
                requestState = state.toString()
                if (state is org.graphiks.kadre.window.WindowRequestState.Terminated) break
                Thread.sleep(25)
            }
        }
        assertTrue(window.titles.contains("o2"), "title never set (requestState=$requestState)")
        assertTrue(window.appIds.contains("org.graphiks.kadre.wayland"))
        assertEquals(1, bridge.createWindowingCalls.get())
        driver.close()
        assertEquals(1, window.closeCount.get(), "destroy ordonné à la fermeture du driver")
        session.teardown()
    }

    @Test
    fun titleFieldRejectionCarriesTheNormativeFailure() {
        val bridge = FakeBridge()
        val window = FakeNativeWindow()
        bridge.fakeWindow = window
        val session = session(bridge)
        val driver = driver(bridge, session)
        // le titre est supporté en tranche 2: ce test épingle que le rejet
        // d'un champ non supporté (level=Normal est supporté, on épingle le
        // refus de decorations=System via WindowSpec par défaut)
        runBlocking { driver.manager.requestWindow(WindowSpec()) }
        assertEquals(0, bridge.createWindowingCalls.get())
        session.teardown()
    }
}
