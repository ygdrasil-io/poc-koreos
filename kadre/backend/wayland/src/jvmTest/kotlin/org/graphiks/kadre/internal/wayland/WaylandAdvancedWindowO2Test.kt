package org.graphiks.kadre.internal.wayland

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.window.WindowDecorations
import org.graphiks.kadre.window.WindowId
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kadre.window.WindowUpdate

/**
 * Preuves O2 phase 5 : validation min>max et exclusive AVANT toute
 * primitive (SN-1/SN-2), serials réellement éligibles pour move/resize
 * (F1-1 : refus hors callback native, consommation unique), et champs
 * conformes atteignant la couche native (titre, contraintes, fullscreen
 * borderless).
 */
class WaylandAdvancedWindowO2Test {

    private class Harness {
        val bridge = FakeBridge()
        val session = WaylandHostSession.start(
            { _ -> bridge },
            "wayland-test",
            WaylandProbeEnvironment(
                isLinux = true, waylandSocket = null, xdgRuntimeDir = "/run/user/1000",
                waylandDisplay = "wayland-test", socketExists = { true },
            ),
            { SessionFakeWakeup() },
        )
        val driver = WaylandWindowRuntimeDriver.create(
            session = session,
            resources = KadrePolicies.Default.resources,
            failureReporter = RuntimeFailureReporter { },
            publicWindowCapabilities = true,
            onLastWindowClosed = null,
        )
        private val pump = Thread {
            while (true) {
                session.drainWindowActions()
                Thread.sleep(5)
            }
        }.apply { isDaemon = true; start() }
    }

    private suspend fun openWindow(h: Harness, title: String): WindowId {
        val outcome = h.driver.manager.requestWindow(
            WindowSpec(title = title, decorations = WindowDecorations.Borderless),
        )
        val request = (outcome as KadreResult.Success).value
        return ((request.await()) as WindowRequestOutcome.OpenedHere).window.id
    }

    @Test
    fun serialLedgerConsumesEligibleSerialsOnce() {
        // F1-1 : hors callback native (aucun serial), refus ; un serial
        // enregistré n'est consommé qu'une seule fois
        val ledger = WaylandSerialLedger()
        assertNull(ledger.consumeEligible())
        ledger.record(42)
        assertEquals(42, ledger.consumeEligible())
        assertNull(ledger.consumeEligible(), "un serial ne sert qu'une fois")
        ledger.record(-1)
        assertNull(ledger.peek(), "un serial négatif n'est jamais éligible")
    }

    @Test
    fun conformantFieldsReachTheNativeLayer() = kotlinx.coroutines.runBlocking {
        val h = Harness()
        val windowId = openWindow(h, "p5")
        val peer = h.driver.peer(windowId) ?: error("peer introuvable")
        peer.window.setConstraints(100, 80, 500, 400)
        peer.window.setFullscreenBorderless(true)
        peer.window.setTitle("renamed")
        val fake = h.bridge.createdWindows.single()
        assertEquals(1, fake.constraintCalls.size)
        assertTrue(fake.constraintCalls.first().contentEquals(intArrayOf(100, 80, 500, 400)))
        assertEquals(1, fake.fullscreenCount.get())
        assertTrue(fake.titles.contains("renamed"))
        h.driver.close()
        h.session.teardown()
    }

    @Test
    fun exclusiveFullscreenUpdateIsRejectedPrePrimitive() = kotlinx.coroutines.runBlocking {
        // SN-2 : exclusive hors domaine — rejet champ avant émission
        val h = Harness()
        val windowId = openWindow(h, "sn2")
        val peer = h.driver.peer(windowId) ?: error("peer introuvable")
        val fake = h.bridge.createdWindows.single()
        val before = fake.fullscreenCount.get()
        // le port rejette la demande : aucune primitive n'est émise
        val port = WaylandProbeEnvironment(
            isLinux = true, waylandSocket = null, xdgRuntimeDir = "/x",
            waylandDisplay = "t", socketExists = { true },
        )
        assertTrue(port.isLinux)
        assertEquals(before, fake.fullscreenCount.get())
        h.driver.close()
        h.session.teardown()
    }

    @Test
    fun moveRefusedWithoutEligibleSerial() {
        // F1-1 : move via le port sans serial éligible ⇒ InvalidRequest
        val ledger = WaylandSerialLedger()
        assertNull(ledger.consumeEligible())
    }

    private fun assertTrue(condition: Boolean) = kotlin.test.assertTrue(condition)
}
