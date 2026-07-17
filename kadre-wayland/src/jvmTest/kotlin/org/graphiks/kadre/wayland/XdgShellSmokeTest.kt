package org.graphiks.kadre.wayland

import org.graphiks.kadre.ffi.wayland.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class XdgShellSmokeTest {
    @Test
    fun `xdg close cleanup failure is queued and never crosses the upcall`() {
        val failure = IllegalStateException("surface cleanup failed")
        var captured: Throwable? = null
        val callbacks = XdgUpcallCallbacks(
            onResized = { _, _, _ -> },
            onStateConfigured = {},
            onClose = { throw failure },
            onFailure = { captured = it },
        )

        callbacks.close()

        assertSame(failure, captured)
    }

    @Test
    fun `xdg listener arena releases only after every proxy destroy succeeds`() {
        var listenerClosed = false
        val lifetime = WaylandNativeListenerLifetime()
        val owner = XdgListenerLifetime.register(
            binding = AutoCloseable { listenerClosed = true },
            nativeListenerLifetime = lifetime,
            hasDecoration = false,
        )

        owner.markToplevelDestroyed()
        assertFalse(listenerClosed)
        owner.markSurfaceDestroyed()
        assertTrue(listenerClosed)
    }

    @Test
    fun `xdg listener arena is deferred until disconnect after proxy destroy failure`() {
        var listenerClosed = false
        val lifetime = WaylandNativeListenerLifetime()
        val owner = XdgListenerLifetime.register(
            binding = AutoCloseable { listenerClosed = true },
            nativeListenerLifetime = lifetime,
            hasDecoration = true,
        )

        owner.markDecorationDestroyed()
        owner.markSurfaceDestroyed()
        assertFalse(listenerClosed)
        lifetime.closeAfterDisplayDisconnect()
        assertTrue(listenerClosed)
    }

    @Test
    fun `xdg acquisition rollback covers every partially acquired stage`() {
        fun rollback(decoration: Long, toplevel: Long, surface: Long): List<String> {
            val trace = mutableListOf<String>()
            rollbackXdgAcquisition(
                primary = IllegalStateException("setup"),
                decorationPtr = decoration,
                toplevelPtr = toplevel,
                surfacePtr = surface,
                destroyDecoration = { trace += "decoration-$it" },
                destroyToplevel = { trace += "toplevel-$it" },
                destroySurface = { trace += "surface-$it" },
                closeArena = { trace += "arena" },
            )
            return trace
        }

        assertEquals(listOf("arena"), rollback(0L, 0L, 0L))
        assertEquals(listOf("surface-1", "arena"), rollback(0L, 0L, 1L))
        assertEquals(
            listOf("toplevel-2", "surface-1", "arena"),
            rollback(0L, 2L, 1L),
        )
        assertEquals(
            listOf("decoration-3", "toplevel-2", "surface-1", "arena"),
            rollback(3L, 2L, 1L),
        )
    }

    @Test
    fun `xdg acquisition rollback preserves primary and suppresses every cleanup failure`() {
        val primary = IllegalStateException("listener install")
        val decorationFailure = IllegalArgumentException("decoration")
        val surfaceFailure = UnsupportedOperationException("surface")

        rollbackXdgAcquisition(
            primary = primary,
            decorationPtr = 3L,
            toplevelPtr = 2L,
            surfacePtr = 1L,
            destroyDecoration = { throw decorationFailure },
            destroyToplevel = {},
            destroySurface = { throw surfaceFailure },
            closeArena = {},
        )

        assertEquals(listOf(decorationFailure, surfaceFailure), primary.suppressed.toList())
    }

    @Test
    fun `xdg registration rollback defers listener after any proxy destroy failure`() {
        val registrationFailure = IllegalStateException("listener lifetime closed")
        val destroyFailure = IllegalArgumentException("toplevel destroy")
        val lifetime = WaylandNativeListenerLifetime()
        var listenerClosed = false

        rollbackXdgAcquisition(
            primary = registrationFailure,
            decorationPtr = 3L,
            toplevelPtr = 2L,
            surfacePtr = 1L,
            destroyDecoration = {},
            destroyToplevel = { throw destroyFailure },
            destroySurface = {},
            closeArena = { error("must use retained binding") },
            listenerBinding = AutoCloseable { listenerClosed = true },
            nativeListenerLifetime = lifetime,
        )

        assertFalse(listenerClosed)
        assertEquals(listOf(destroyFailure), registrationFailure.suppressed.toList())
        lifetime.closeAfterDisplayDisconnect()
        assertTrue(listenerClosed)
    }

    @Test
    fun `xdg surface configure rejects missing ack and failed flush`() {
        val missingAck = kotlin.test.assertFailsWith<IllegalStateException> {
            performXdgSurfaceConfigure(ackConfigure = null, flushDisplay = { 0 })
        }
        val failedFlush = kotlin.test.assertFailsWith<IllegalStateException> {
            performXdgSurfaceConfigure(ackConfigure = {}, flushDisplay = { -1 })
        }
        val missingFlush = kotlin.test.assertFailsWith<IllegalStateException> {
            performXdgSurfaceConfigure(ackConfigure = {}, flushDisplay = null)
        }

        assertTrue(missingAck.message.orEmpty().contains("ack_configure"))
        assertTrue(failedFlush.message.orEmpty().contains("wl_display_flush"))
        assertTrue(missingFlush.message.orEmpty().contains("wl_display_flush"))
    }

    @Test
    fun `xdg_shell constants are defined`() {
        // Validate key opcode values from Wayland xdg-shell protocol XML
        assertEquals(2, XDG_WM_BASE_GET_XDG_SURFACE)
        assertEquals(1, XDG_SURFACE_GET_TOPLEVEL)
        assertEquals(2, XDG_TOPLEVEL_SET_TITLE)
        assertEquals(4, XDG_TOPLEVEL_SHOW_WINDOW_MENU)
        assertEquals(5, XDG_TOPLEVEL_MOVE)
        assertEquals(6, XDG_TOPLEVEL_RESIZE)
        assertEquals(3, XDG_TOPLEVEL_SET_APP_ID)
    }

    @Test
    fun `wl_seat request opcodes match protocol order`() {
        assertEquals(0, WL_SEAT_GET_POINTER)
        assertEquals(1, WL_SEAT_GET_KEYBOARD)
        assertEquals(2, WL_SEAT_GET_TOUCH)
    }
}
