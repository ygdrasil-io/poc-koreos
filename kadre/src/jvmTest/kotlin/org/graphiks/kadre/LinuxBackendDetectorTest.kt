/**
 * Unit tests for [LinuxBackendDetector].
 *
 * Strategy: we test the behaviors that do not depend on the classpath
 * or the effective environment variables (env vars are not easily mockable
 * in standard JVM). Covered cases:
 *  - canLoad: existing class → true, non-existent class → false
 *  - The KADRE_LINUX_BACKEND logic is tested indirectly via canLoad-based
 *    detection (the only guaranteeable path without modifying the process env).
 *
 * X11/Wayland detection.
 */
package org.graphiks.kadre

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LinuxBackendDetectorTest {

    @Test
    fun `auto selection falls back from an unusable Wayland session to X11`() {
        val probed = mutableListOf<String>()

        val selection = LinuxBackendDetector.detectBackend(
            environment = mapOf("WAYLAND_DISPLAY" to "stale", "DISPLAY" to ":0")::get,
            loadClass = {},
            probe = { backend ->
                probed += backend
                if (backend == LinuxBackendDetector.WAYLAND_CLASS) {
                    throw IllegalStateException("stale Wayland socket")
                }
            },
        )

        assertEquals(LinuxBackendDetector.X11_CLASS, selection.backendClass)
        assertEquals(LinuxBackendStage.PROBE, selection.stage)
        assertEquals(null, selection.failure)
        assertEquals(
            listOf(LinuxBackendDetector.WAYLAND_CLASS, LinuxBackendDetector.X11_CLASS),
            probed,
        )
    }

    @Test
    fun `forced Wayland selection exposes its probe failure without trying X11`() {
        val probed = mutableListOf<String>()

        val failure = assertFailsWith<IllegalStateException> {
            LinuxBackendDetector.detectBackend(
                environment = mapOf("KADRE_LINUX_BACKEND" to "wayland", "DISPLAY" to ":0")::get,
                loadClass = {},
                probe = { backend ->
                    probed += backend
                    throw IllegalStateException("stale Wayland socket")
                },
            )
        }

        assertContains(failure.message.orEmpty(), LinuxBackendDetector.WAYLAND_CLASS)
        assertContains(failure.message.orEmpty(), "probe")
        assertEquals(listOf(LinuxBackendDetector.WAYLAND_CLASS), probed)
        assertEquals("stale Wayland socket", failure.cause?.message)
    }

    @Test
    fun `selection failure retains the primary and suppressed native causes`() {
        val waylandFailure = IllegalStateException("stale Wayland socket")
        val x11Failure = UnsatisfiedLinkError("XOpenDisplay unavailable")

        val failure = assertFailsWith<IllegalStateException> {
            LinuxBackendDetector.detectBackend(
                environment = mapOf("WAYLAND_DISPLAY" to "stale", "DISPLAY" to ":0")::get,
                loadClass = {},
                probe = { backend ->
                    throw if (backend == LinuxBackendDetector.WAYLAND_CLASS) {
                        waylandFailure
                    } else {
                        x11Failure
                    }
                },
            )
        }

        assertSame(waylandFailure, failure.cause)
        assertEquals(listOf(x11Failure), failure.suppressed.toList())
    }

    @Test
    fun `selection failure quotes only allowlisted Linux environment values`() {
        val failure = assertFailsWith<IllegalStateException> {
            LinuxBackendDetector.detectBackend(
                environment = mapOf(
                    "KADRE_LINUX_BACKEND" to "wayland",
                    "WAYLAND_DISPLAY" to "wayland-1",
                    "DISPLAY" to ":1",
                    "XDG_SESSION_TYPE" to "wayland",
                )::get,
                loadClass = {},
                probe = { throw IllegalStateException("unavailable") },
            )
        }

        val message = failure.message.orEmpty()
        assertContains(message, "KADRE_LINUX_BACKEND=wayland")
        assertContains(message, "WAYLAND_DISPLAY=wayland-1")
        assertContains(message, "DISPLAY=:1")
        assertFalse("XDG_SESSION_TYPE" in message)
    }

    // -------------------------------------------------------------------------
    // canLoad — main logic, OS-independent
    // -------------------------------------------------------------------------

    @Test
    fun `canLoad returns true for an existing class`() {
        // String is always on the classpath
        val result = LinuxBackendDetector.canLoad("java.lang.String")
        assertTrue(result, "java.lang.String must be loadable")
    }

    @Test
    fun `canLoad returns false for a non-existent class`() {
        val result = LinuxBackendDetector.canLoad("org.graphiks.kadre.NonExistentClass999")
        assertFalse(result, "A fictitious class must not be loadable")
    }

    @Test
    fun `canLoad returns false for the X11 backend absent from the classpath`() {
        // kadre-x11 is not a dependency of :kadre — must be absent
        val result = LinuxBackendDetector.canLoad(LinuxBackendDetector.X11_CLASS)
        assertFalse(result, "kadre-x11 must not be on the classpath of :kadre")
    }

    @Test
    fun `canLoad returns false for the Wayland backend absent from the classpath`() {
        // kadre-wayland is not a dependency of :kadre — must be absent
        val result = LinuxBackendDetector.canLoad(LinuxBackendDetector.WAYLAND_CLASS)
        assertFalse(result, "kadre-wayland must not be on the classpath of :kadre")
    }

    @Test
    fun `canLoad with debug=true does not throw an exception`() {
        // Verifies that the debug flag does not introduce a regression
        val result = LinuxBackendDetector.canLoad("org.graphiks.kadre.DoesNotExist", debug = true)
        assertFalse(result)
    }

    // -------------------------------------------------------------------------
    // Constants — verification of the target class names
    // -------------------------------------------------------------------------

    @Test
    fun `X11_CLASS points to the expected x11 package`() {
        assertEquals(
            "org.graphiks.kadre.x11.X11EventLoopKt",
            LinuxBackendDetector.X11_CLASS,
        )
    }

    @Test
    fun `WAYLAND_CLASS points to the expected wayland package`() {
        assertEquals(
            "org.graphiks.kadre.wayland.WaylandEventLoopKt",
            LinuxBackendDetector.WAYLAND_CLASS,
        )
    }
}
