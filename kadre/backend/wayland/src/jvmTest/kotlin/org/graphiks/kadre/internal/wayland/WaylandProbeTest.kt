package org.graphiks.kadre.internal.wayland

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class WaylandProbeTest {

    private fun env(
        isLinux: Boolean = true,
        waylandSocket: String? = null,
        xdgRuntimeDir: String? = null,
        waylandDisplay: String? = null,
        socketExists: (String) -> Boolean = { fail("socketExists must not be called: $it") },
    ): WaylandProbeEnvironment = WaylandProbeEnvironment(
        isLinux = isLinux,
        waylandSocket = waylandSocket,
        xdgRuntimeDir = xdgRuntimeDir,
        waylandDisplay = waylandDisplay,
        socketExists = socketExists,
    )

    @Test
    fun nonLinuxIsUnavailableRegardlessOfEndpoint() {
        val result = WaylandProbe.check(env(isLinux = false, waylandSocket = "7"))
        assertEquals(WaylandProbeResult(available = false, reason = WaylandAvailabilityReason.Platform), result)
    }

    @Test
    fun validWaylandSocketFdIsAvailableWithoutTouchingTheFilesystem() {
        val result = WaylandProbe.check(env(waylandSocket = "7"))
        assertEquals(WaylandProbeResult(available = true, reason = null), result)
    }

    @Test
    fun nonNumericWaylandSocketIsInvalid() {
        val result = WaylandProbe.check(env(waylandSocket = "pas-un-fd"))
        assertEquals(
            WaylandProbeResult(available = false, reason = WaylandAvailabilityReason.WaylandSocketInvalid),
            result,
        )
    }

    @Test
    fun overflowingWaylandSocketIsInvalid() {
        val result = WaylandProbe.check(env(waylandSocket = "99999999999999999999"))
        assertEquals(
            WaylandProbeResult(available = false, reason = WaylandAvailabilityReason.WaylandSocketInvalid),
            result,
        )
    }

    @Test
    fun negativeWaylandSocketIsInvalid() {
        val result = WaylandProbe.check(env(waylandSocket = "-3"))
        assertEquals(
            WaylandProbeResult(available = false, reason = WaylandAvailabilityReason.WaylandSocketInvalid),
            result,
        )
    }

    @Test
    fun emptyWaylandSocketIsTreatedAsAbsent() {
        val result = WaylandProbe.check(
            env(
                waylandSocket = "",
                xdgRuntimeDir = "/run/user/1000",
                socketExists = { it == "/run/user/1000/wayland-0" },
            ),
        )
        assertEquals(WaylandProbeResult(available = true, reason = null), result)
    }

    @Test
    fun absoluteWaylandDisplayNeedsNoRuntimeDir() {
        val result = WaylandProbe.check(
            env(
                xdgRuntimeDir = null,
                waylandDisplay = "/run/user/1000/custom",
                socketExists = { it == "/run/user/1000/custom" },
            ),
        )
        assertEquals(WaylandProbeResult(available = true, reason = null), result)
    }

    @Test
    fun absoluteWaylandDisplayReportsMissingSocket() {
        val result = WaylandProbe.check(
            env(xdgRuntimeDir = null, waylandDisplay = "/absent/sock", socketExists = { false }),
        )
        assertEquals(
            WaylandProbeResult(available = false, reason = WaylandAvailabilityReason.SocketAbsent),
            result,
        )
    }

    @Test
    fun runtimeDirWithExplicitDisplayIsResolved() {
        val result = WaylandProbe.check(
            env(
                xdgRuntimeDir = "/run/user/1000",
                waylandDisplay = "wayland-1",
                socketExists = { it == "/run/user/1000/wayland-1" },
            ),
        )
        assertEquals(WaylandProbeResult(available = true, reason = null), result)
    }

    @Test
    fun runtimeDirWithoutDisplayFallsBackToWayland0() {
        val result = WaylandProbe.check(
            env(xdgRuntimeDir = "/run/user/1000", socketExists = { it == "/run/user/1000/wayland-0" }),
        )
        assertEquals(WaylandProbeResult(available = true, reason = null), result)
    }

    @Test
    fun emptyDisplayFallsBackToWayland0() {
        val result = WaylandProbe.check(
            env(
                xdgRuntimeDir = "/run/user/1000",
                waylandDisplay = "",
                socketExists = { it == "/run/user/1000/wayland-0" },
            ),
        )
        assertEquals(WaylandProbeResult(available = true, reason = null), result)
    }

    @Test
    fun absentRuntimeDirWithoutAbsolutePathIsUnavailable() {
        val result = WaylandProbe.check(env(xdgRuntimeDir = null, socketExists = { fail("no path to check") }))
        assertEquals(
            WaylandProbeResult(available = false, reason = WaylandAvailabilityReason.RuntimeDirAbsent),
            result,
        )
    }

    @Test
    fun missingNamedSocketIsUnavailable() {
        val result = WaylandProbe.check(
            env(xdgRuntimeDir = "/run/user/1000", socketExists = { false }),
        )
        assertEquals(
            WaylandProbeResult(available = false, reason = WaylandAvailabilityReason.SocketAbsent),
            result,
        )
    }
}
