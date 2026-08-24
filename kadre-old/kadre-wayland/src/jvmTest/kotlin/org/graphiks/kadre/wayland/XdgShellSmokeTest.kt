package org.graphiks.kadre.wayland

import org.graphiks.kffi.wayland.*
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
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
    fun `real xdg factory rolls back every acquired proxy at every failing stage`() {
        val expectedRollback = mapOf(
            "get xdg_surface" to emptyList(),
            "get xdg_toplevel" to listOf("destroy-surface-11"),
            "get xdg decoration" to listOf("destroy-toplevel-12", "destroy-surface-11"),
            "install xdg decoration listener" to listOf(
                "destroy-decoration-13",
                "destroy-toplevel-12",
                "destroy-surface-11",
            ),
            "install xdg_surface listener" to listOf(
                "destroy-decoration-13",
                "destroy-toplevel-12",
                "destroy-surface-11",
            ),
            "install xdg_toplevel listener" to listOf(
                "destroy-decoration-13",
                "destroy-toplevel-12",
                "destroy-surface-11",
            ),
            "initial xdg commit" to listOf(
                "destroy-decoration-13",
                "destroy-toplevel-12",
                "destroy-surface-11",
            ),
            "initial xdg roundtrip" to listOf(
                "destroy-decoration-13",
                "destroy-toplevel-12",
                "destroy-surface-11",
            ),
        )

        expectedRollback.forEach { (stage, expected) ->
            val operations = RecordingXdgCreateOperations(failingStage = stage)

            val failure = assertFailsWith<IllegalStateException>(stage) {
                createXdgForTest(operations)
            }

            assertTrue(failure.message.orEmpty().contains(stage), failure.message)
            assertEquals(expected, operations.trace.filter { it.startsWith("destroy-") }, stage)
        }
    }

    @Test
    fun `real xdg factory requires successful configure ack after roundtrip`() {
        val missingConfigure = RecordingXdgCreateOperations(sendInitialConfigure = false)
        val missingFailure = assertFailsWith<IllegalStateException> {
            createXdgForTest(missingConfigure)
        }

        assertTrue(missingFailure.message.orEmpty().contains("initial xdg configure"))
        assertEquals(
            listOf("destroy-decoration-13", "destroy-toplevel-12", "destroy-surface-11"),
            missingConfigure.trace.filter { it.startsWith("destroy-") },
        )

        val configureFailure = IllegalArgumentException("flush configure")
        val failedConfigure = RecordingXdgCreateOperations(configureFailure = configureFailure)
        val callbackFailures = mutableListOf<Throwable>()
        val failed = assertFailsWith<IllegalStateException> {
            createXdgForTest(failedConfigure, onFailure = callbackFailures::add)
        }

        assertSame(configureFailure, callbackFailures.single())
        assertSame(configureFailure, failed.cause)
        assertEquals(1, failedConfigure.ackCalls)
    }

    @Test
    fun `real xdg factory returns only after configure ack and releases listener lifetime on destroy`() {
        val operations = RecordingXdgCreateOperations()
        val bridge = assertNotNull(createXdgForTest(operations))

        assertTrue(bridge.hasReceivedInitialConfigure())
        assertEquals(1, operations.ackCalls)
        bridge.destroy()
        assertEquals(
            listOf("destroy-decoration-13", "destroy-toplevel-12", "destroy-surface-11"),
            operations.trace.filter { it.startsWith("destroy-") },
        )
    }

    @Test
    fun `initial xdg commit uses actual wl_surface proxy version`() {
        val operations = RecordingXdgCreateOperations()

        assertNotNull(createXdgForTest(operations))

        assertEquals(3, operations.commitVersion)
    }

    @Test
    fun `real xdg factory rolls back every proxy when listener lifetime rejects registration`() {
        val operations = RecordingXdgCreateOperations()
        val registrationFailure = IllegalStateException("listener lifetime closed")
        val lifetime = object : WaylandNativeListenerLifetime() {
            override fun register(binding: AutoCloseable): WaylandNativeListenerLease =
                throw registrationFailure
        }

        val failure = assertFailsWith<IllegalStateException> {
            XdgToplevel.create(
                displayPtr = 10L,
                wmBasePtr = 20L,
                surfacePtr = 30L,
                onResized = { _, _, _ -> },
                onClose = {},
                onFailure = {},
                nativeListenerLifetime = lifetime,
                decorationManagerPtr = 40L,
                operations = operations,
            )
        }

        assertSame(registrationFailure, failure.cause)
        assertEquals(
            listOf("destroy-decoration-13", "destroy-toplevel-12", "destroy-surface-11"),
            operations.trace.filter { it.startsWith("destroy-") },
        )
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

    private fun createXdgForTest(
        operations: XdgCreateOperations,
        onFailure: (Throwable) -> Unit = {},
    ): XdgToplevel? = XdgToplevel.create(
        displayPtr = 10L,
        wmBasePtr = 20L,
        surfacePtr = 30L,
        onResized = { _, _, _ -> },
        onClose = {},
        onFailure = onFailure,
        nativeListenerLifetime = WaylandNativeListenerLifetime(),
        decorationManagerPtr = 40L,
        operations = operations,
    )

    private class RecordingXdgCreateOperations(
        private val failingStage: String? = null,
        private val sendInitialConfigure: Boolean = true,
        private val configureFailure: Throwable? = null,
    ) : XdgCreateOperations {
        val trace = mutableListOf<String>()
        var ackCalls = 0
        var commitVersion: Int? = null

        override val available: Boolean = true

        private fun stage(name: String) {
            trace += name
            if (failingStage == name) throw IllegalStateException("failed $name")
        }

        private fun listenerResult(name: String): Int {
            trace += name
            return if (failingStage == name) -7 else 0
        }

        override fun getVersion(proxyPtr: Long): Int = if (proxyPtr == 30L) 3 else 5

        override fun getXdgSurface(wmBasePtr: Long, surfacePtr: Long, version: Int): Long {
            stage("get xdg_surface")
            return 11L
        }

        override fun getToplevel(xdgSurfacePtr: Long, version: Int): Long {
            stage("get xdg_toplevel")
            return 12L
        }

        override fun getDecoration(managerPtr: Long, toplevelPtr: Long, version: Int): Long {
            stage("get xdg decoration")
            return 13L
        }

        override fun installDecorationListener(
            decorationPtr: Long,
            bridge: XdgToplevel,
            arena: Arena,
        ): Int {
            return listenerResult("install xdg decoration listener")
        }

        override fun installSurfaceListener(
            xdgSurfacePtr: Long,
            bridge: XdgToplevel,
            arena: Arena,
        ): Int {
            return listenerResult("install xdg_surface listener")
        }

        override fun installToplevelListener(
            xdgToplevelPtr: Long,
            bridge: XdgToplevel,
            arena: Arena,
        ): Int {
            return listenerResult("install xdg_toplevel listener")
        }

        override fun setDecorationMode(
            decorationPtr: Long,
            decorationVersion: Int,
            decorated: Boolean,
        ) = Unit

        override fun commit(surfacePtr: Long, version: Int) {
            commitVersion = version
            stage("initial xdg commit")
        }

        override fun roundtrip(displayPtr: Long, bridge: XdgToplevel): Int {
            stage("initial xdg roundtrip")
            if (sendInitialConfigure) {
                bridge.onSurfaceConfigure(MemorySegment.NULL, MemorySegment.NULL, 41)
            }
            return 0
        }

        override fun ackConfigure(xdgSurfacePtr: Long, version: Int, serial: Int) {
            ackCalls += 1
        }

        override fun flush(displayPtr: Long): Int {
            configureFailure?.let { throw it }
            return 0
        }

        override fun destroyDecoration(decorationPtr: Long, version: Int) {
            trace += "destroy-decoration-$decorationPtr"
        }

        override fun destroyToplevel(toplevelPtr: Long, version: Int) {
            trace += "destroy-toplevel-$toplevelPtr"
        }

        override fun destroySurface(surfacePtr: Long, version: Int) {
            trace += "destroy-surface-$surfacePtr"
        }
    }
}
