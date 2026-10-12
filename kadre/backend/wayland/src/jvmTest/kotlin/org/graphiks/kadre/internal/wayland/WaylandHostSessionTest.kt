package org.graphiks.kadre.internal.wayland

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.graphiks.kffi.posix.PosixWakeup

class WaylandHostSessionTest {

    private fun environment(waylandSocket: String? = null): WaylandProbeEnvironment =
        WaylandProbeEnvironment(
            isLinux = true,
            waylandSocket = waylandSocket,
            xdgRuntimeDir = "/run/user/1000",
            waylandDisplay = "wayland-test",
            socketExists = { true },
        )

    @Test
    fun waylandSocketFailsBeforeAnyResourceIsCreated() {
        var wakeOpened = false
        var bridgeCreated = false
        val failure = runCatching {
            WaylandHostSession.start(
                bridgeFactory = { _ -> bridgeCreated = true; FakeBridge() },
                socketName = null,
                env = environment(waylandSocket = "7"),
                wakeFactory = { wakeOpened = true; SessionFakeWakeup() },
            )
        }.exceptionOrNull()
        assertTrue(failure is WaylandSessionStartException)
        assertEquals("wayland-socket-unsupported", failure.code)
        assertFalse(wakeOpened)
        assertFalse(bridgeCreated)
    }

    @Test
    fun bridgeFailureClosesTheWake() {
        val wake = SessionFakeWakeup()
        val failure = runCatching {
            WaylandHostSession.start(
                bridgeFactory = { _ -> throw WaylandConnectException("display-connect-failed") },
                socketName = null,
                env = environment(),
                wakeFactory = { wake },
            )
        }.exceptionOrNull()
        assertTrue(failure is WaylandSessionStartException)
        assertEquals("display-connect-failed", failure.code)
        assertTrue(wake.closed)
    }

    @Test
    fun negotiationFailureClosesBridgeAndWake() {
        val wake = SessionFakeWakeup()
        val bridge = FakeBridge(globals = listOf(WaylandGlobal("wl_compositor", 6)))
        val failure = runCatching {
            WaylandHostSession.start(
                bridgeFactory = { _ -> bridge },
                socketName = null,
                env = environment(),
                wakeFactory = { wake },
            )
        }.exceptionOrNull()
        assertTrue(failure is WaylandSessionStartException)
        assertEquals("missing-xdg_wm_base", failure.code)
        assertTrue(bridge.closed)
        assertTrue(wake.closed)
    }

    @Test
    fun successfulStartWiresBridgeWakeAndPump() {
        val bridge = FakeBridge()
        val session = WaylandHostSession.start(
            bridgeFactory = { _ -> bridge },
            socketName = null,
            env = environment(),
            wakeFactory = { SessionFakeWakeup() },
        )
        assertEquals(bridge, session.bridge)
        assertEquals(PollOutcome.DisplayReady, session.bridge.pollEvents(1, false))
        session.teardown()
        assertTrue(bridge.closed)
        assertEquals(1, bridge.flushCount, "flush final avant fermeture (F3)")
    }
}
