package org.graphiks.kadre.wayland

import org.graphiks.kadre.ffi.posix.PosixWakeup
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WaylandKeyboardLifecycleTest {
    @Test
    fun `format one keeps mapping and locale arena alive through native consumers`() {
        val operations = FakeWaylandKeymapOperations()
        val loader = WaylandKeymapLoader(operations) { "fr_FR.UTF-8" }

        loader.load(format = WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1, fd = 31, size = 64)

        assertEquals(
            listOf(
                "mmap",
                "context",
                "keymap while mapping open",
                "state",
                "compose while locale arena open",
                "munmap",
                "close(31)",
            ),
            operations.trace,
        )
        assertEquals(103L, loader.stateAddress)
        assertEquals(105L, loader.composeStateAddress)
    }

    @Test
    fun `format zero is rejected and its fd is still closed`() {
        val operations = FakeWaylandKeymapOperations()
        val loader = WaylandKeymapLoader(operations)

        assertFailsWith<IllegalArgumentException> {
            loader.load(format = 0, fd = 17, size = 64)
        }

        assertEquals(listOf("close(17)"), operations.trace)
    }

    @Test
    fun `all construction exception paths close fd and only successful mappings are unmapped`() {
        val failurePoints = listOf("mmap", "context", "keymap", "state", "compose", "composeState")

        for (failurePoint in failurePoints) {
            val operations = FakeWaylandKeymapOperations(throwAt = failurePoint)
            val loader = WaylandKeymapLoader(operations)

            assertFailsWith<InjectedKeymapFailure>(failurePoint) {
                loader.load(format = WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1, fd = 23, size = 64)
            }

            assertEquals(1, operations.trace.count { it == "close(23)" }, failurePoint)
            assertEquals(if (failurePoint == "mmap") 0 else 1, operations.trace.count { it == "munmap" }, failurePoint)
        }
    }

    @Test
    fun `replacing a keymap releases every previous resource exactly once`() {
        val operations = FakeWaylandKeymapOperations()
        val loader = WaylandKeymapLoader(operations)
        loader.load(format = WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1, fd = 41, size = 64)
        operations.trace.clear()

        loader.load(format = WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1, fd = 42, size = 64)

        assertEquals(1, operations.trace.count { it == "unref-compose-state(105)" })
        assertEquals(1, operations.trace.count { it == "unref-compose-table(104)" })
        assertEquals(1, operations.trace.count { it == "unref-state(103)" })
        assertEquals(1, operations.trace.count { it == "unref-keymap(102)" })
        assertEquals(1, operations.trace.count { it == "unref-context(101)" })
    }

    @Test
    fun `failed replacement preserves the previously active keymap`() {
        val operations = FakeWaylandKeymapOperations(contextFailureOnCall = 2)
        val loader = WaylandKeymapLoader(operations)
        loader.load(format = WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1, fd = 43, size = 64)
        operations.trace.clear()

        assertFailsWith<InjectedKeymapFailure> {
            loader.load(format = WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1, fd = 44, size = 64)
        }

        assertEquals(103L, loader.stateAddress)
        assertEquals(105L, loader.composeStateAddress)
        assertEquals(0, operations.trace.count { it.startsWith("unref-") })
    }

    @Test
    fun `native keymap callback queues a failure for the Kotlin loop instead of throwing`() {
        val loop = WaylandEventLoop(
            displayPtr = 77L,
            compositorPtr = 0L,
            xdgWmBasePtr = 0L,
            shmPtr = 0L,
            wakeup = StubPosixWakeup,
        )
        val loader = WaylandKeymapLoader(FakeWaylandKeymapOperations(throwAt = "context"))
        val callback = WaylandKeymapCallback(
            loader = loader,
            onLoaded = {},
            onFailure = loop::queueNativeFailure,
        )

        callback.onKeymap(format = WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1, fd = 51, size = 64)

        val failure = assertFailsWith<InjectedKeymapFailure> {
            loop.throwPendingNativeFailure()
        }
        assertEquals("context", failure.message)
    }

    @Test
    fun `seat binding closes its keyboard owner and arena exactly once`() {
        val arena = Arena.ofConfined()
        var keyboardCloses = 0
        val binding = WaylandSeatBinding(arena).apply {
            keyboardOwner = AutoCloseable { keyboardCloses += 1 }
        }

        binding.close()
        binding.close()

        assertEquals(1, keyboardCloses)
        assertFalse(arena.scope().isAlive)
    }
}

private class InjectedKeymapFailure(point: String) : RuntimeException(point)

private class FakeWaylandKeymapOperations(
    private val throwAt: String? = null,
    private val contextFailureOnCall: Int? = null,
) : WaylandKeymapOperations {
    val trace = mutableListOf<String>()
    private var mappingOpen = false
    private var contextCalls = 0

    private fun fail(point: String) {
        if (throwAt == point) throw InjectedKeymapFailure(point)
    }

    override fun mmap(fd: Int, size: Int): MemorySegment? {
        trace += "mmap"
        fail("mmap")
        mappingOpen = true
        return MemorySegment.ofAddress(100L)
    }

    override fun munmap(mapping: MemorySegment, size: Int) {
        assertTrue(mappingOpen, "mapping must still be open when munmap is called")
        mappingOpen = false
        trace += "munmap"
    }

    override fun close(fd: Int) {
        trace += "close($fd)"
    }

    override fun contextNew(): MemorySegment? {
        trace += "context"
        contextCalls += 1
        if (contextCalls == contextFailureOnCall) throw InjectedKeymapFailure("context")
        fail("context")
        return MemorySegment.ofAddress(101L)
    }

    override fun keymapNewFromString(context: MemorySegment, mapping: MemorySegment): MemorySegment? {
        assertTrue(mappingOpen, "keymap parser must run while the mapping is open")
        trace += "keymap while mapping open"
        fail("keymap")
        return MemorySegment.ofAddress(102L)
    }

    override fun stateNew(keymap: MemorySegment): MemorySegment? {
        trace += "state"
        fail("state")
        return MemorySegment.ofAddress(103L)
    }

    override fun composeTableNewFromLocale(context: MemorySegment, locale: MemorySegment): MemorySegment? {
        assertTrue(locale.scope().isAlive, "locale segment must be live during the native call")
        trace += "compose while locale arena open"
        fail("compose")
        return MemorySegment.ofAddress(104L)
    }

    override fun composeStateNew(table: MemorySegment): MemorySegment? {
        fail("composeState")
        return MemorySegment.ofAddress(105L)
    }

    override fun composeStateUnref(state: MemorySegment) {
        trace += "unref-compose-state(${state.address()})"
    }

    override fun composeTableUnref(table: MemorySegment) {
        trace += "unref-compose-table(${table.address()})"
    }

    override fun stateUnref(state: MemorySegment) {
        trace += "unref-state(${state.address()})"
    }

    override fun keymapUnref(keymap: MemorySegment) {
        trace += "unref-keymap(${keymap.address()})"
    }

    override fun contextUnref(context: MemorySegment) {
        trace += "unref-context(${context.address()})"
    }
}

private object StubPosixWakeup : PosixWakeup {
    override val readFd: Int = -1
    override fun signal(): Boolean = true
    override fun drain(): Boolean = true
    override fun close() = Unit
}
