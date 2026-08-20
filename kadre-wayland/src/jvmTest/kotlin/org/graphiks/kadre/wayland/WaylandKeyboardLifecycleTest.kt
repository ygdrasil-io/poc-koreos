package org.graphiks.kadre.wayland

import org.graphiks.kadre.ffi.posix.PosixWakeup
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
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
    fun `negative fd is rejected without attempting native close`() {
        val operations = FakeWaylandKeymapOperations()
        val loader = WaylandKeymapLoader(operations)

        val failure = assertFailsWith<IllegalArgumentException> {
            loader.load(format = WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1, fd = -1, size = 64)
        }

        assertEquals("invalid Wayland keymap fd: -1", failure.message)
        assertEquals(emptyList(), operations.trace)
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
    fun `null and MAP_FAILED mappings are rejected without munmap but still close fd`() {
        val cases = listOf(
            FakeWaylandKeymapOperations(nullMapping = true),
            FakeWaylandKeymapOperations(mapFailed = true),
        )

        for (operations in cases) {
            assertFailsWith<IllegalStateException> {
                WaylandKeymapLoader(operations).load(
                    format = WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1,
                    fd = 24,
                    size = 64,
                )
            }
            assertEquals(listOf("mmap", "close(24)"), operations.trace)
        }
    }

    @Test
    fun `negative munmap result fails load closes fd and releases complete pending keymap`() {
        val operations = FakeWaylandKeymapOperations(munmapResult = -1)

        val failure = assertFailsWith<IllegalStateException> {
            WaylandKeymapLoader(operations).load(
                format = WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1,
                fd = 25,
                size = 64,
            )
        }

        assertEquals("munmap failed with return code -1", failure.message)
        assertEquals(
            listOf(
                "mmap", "context", "keymap while mapping open", "state",
                "compose while locale arena open", "munmap", "close(25)",
                "unref-compose-state(105)", "unref-compose-table(104)",
                "unref-state(103)", "unref-keymap(102)", "unref-context(101)",
            ),
            operations.trace,
        )
    }

    @Test
    fun `negative close result fails load and releases pending keymap`() {
        val operations = FakeWaylandKeymapOperations(closeResult = -1)

        val failure = assertFailsWith<IllegalStateException> {
            WaylandKeymapLoader(operations).load(
                format = WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1,
                fd = 26,
                size = 64,
            )
        }

        assertEquals("close failed with return code -1", failure.message)
        assertEquals(1, operations.trace.count { it == "close(26)" })
        assertEquals(5, operations.trace.count { it.startsWith("unref-") })
    }

    @Test
    fun `construction failure stays primary while negative munmap and close are suppressed`() {
        val operations = FakeWaylandKeymapOperations(
            throwAt = "state",
            munmapResult = -1,
            closeResult = -1,
        )

        val failure = assertFailsWith<InjectedKeymapFailure> {
            WaylandKeymapLoader(operations).load(
                format = WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1,
                fd = 27,
                size = 64,
            )
        }

        assertEquals("state", failure.message)
        assertEquals(
            listOf("munmap failed with return code -1", "close failed with return code -1"),
            failure.suppressed.map { it.message },
        )
        assertEquals(
            listOf(
                "mmap", "context", "keymap while mapping open", "state",
                "munmap", "close(27)", "unref-keymap(102)", "unref-context(101)",
            ),
            operations.trace,
        )
    }

    @Test
    fun `same throwable reemitted by every cleanup remains the unsuppressed primary`() {
        val sharedFailure = InjectedKeymapFailure("shared")
        val operations = FakeWaylandKeymapOperations(
            sharedFailure = sharedFailure,
            sharedFailurePoints = setOf(
                "state",
                "munmap",
                "close",
                "unref-keymap",
                "unref-context",
            ),
        )

        val failure = assertFailsWith<InjectedKeymapFailure> {
            WaylandKeymapLoader(operations).load(
                format = WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1,
                fd = 28,
                size = 64,
            )
        }

        assertSame(sharedFailure, failure)
        assertEquals(emptyList(), failure.suppressed.toList())
        assertEquals(
            listOf(
                "mmap", "context", "keymap while mapping open", "state",
                "munmap", "close(28)", "unref-keymap(102)", "unref-context(101)",
            ),
            operations.trace,
        )
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
    fun `failed munmap during replacement keeps old keymap and releases only pending resources`() {
        val operations = FakeWaylandKeymapOperations()
        val loader = WaylandKeymapLoader(operations)
        loader.load(format = WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1, fd = 46, size = 64)
        operations.trace.clear()
        operations.munmapResult = -1

        assertFailsWith<IllegalStateException> {
            loader.load(format = WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1, fd = 47, size = 64)
        }

        assertEquals(103L, loader.stateAddress)
        assertEquals(105L, loader.composeStateAddress)
        assertEquals(5, operations.trace.count { it.startsWith("unref-") })
    }

    @Test
    fun `all unrefs are attempted once and later failures are suppressed`() {
        val operations = FakeWaylandKeymapOperations(
            throwUnrefs = setOf("unref-compose-state", "unref-state", "unref-context"),
        )
        val loader = WaylandKeymapLoader(operations)
        loader.load(format = WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1, fd = 45, size = 64)
        operations.trace.clear()

        val failure = assertFailsWith<InjectedKeymapFailure> { loader.close() }
        loader.close()

        assertEquals("unref-compose-state", failure.message)
        assertEquals(
            listOf("unref-state", "unref-context"),
            failure.suppressed.map { it.message },
        )
        assertEquals(
            listOf(
                "unref-compose-state(105)", "unref-compose-table(104)",
                "unref-state(103)", "unref-keymap(102)", "unref-context(101)",
            ),
            operations.trace,
        )
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

    @Test
    fun `seat binding keeps keyboard failure primary suppresses arena failure and stays idempotent`() {
        val arena = Arena.ofConfined()
        val trace = mutableListOf<String>()
        val keyboardFailure = IllegalStateException("keyboard")
        val arenaFailure = IllegalArgumentException("arena")
        val binding = WaylandSeatBinding(
            arena = arena,
            closeArena = {
                trace += "arena"
                throw arenaFailure
            },
        ).apply {
            keyboardOwner = AutoCloseable {
                trace += "keyboard"
                throw keyboardFailure
            }
        }

        try {
            val failure = assertFailsWith<IllegalStateException> { binding.close() }
            binding.close()

            assertSame(keyboardFailure, failure)
            assertEquals(listOf("keyboard", "arena"), trace)
            assertEquals(listOf(arenaFailure), failure.suppressed.toList())
        } finally {
            arena.close()
        }
    }

    @Test
    fun `strict seat child listener invoke failures propagate and roll back every proxy`() {
        val kinds = listOf("wl_keyboard", "wl_pointer", "wl_touch", "wl_data_device")

        for ((index, kind) in kinds.withIndex()) {
            val proxy = 100L + index
            val expected = InjectedListenerFailure("$kind invoke")
            val destroyed = mutableListOf<Long>()

            val actual = assertFailsWith<InjectedListenerFailure>(kind) {
                installOwnedWaylandListener(
                    kind = kind,
                    proxyPtr = proxy,
                    failOnNativeError = true,
                    destroyProxy = { destroyed += it },
                ) { throw expected }
            }

            assertSame(expected, actual, kind)
            assertEquals(listOf(proxy), destroyed, kind)
        }
    }

    @Test
    fun `listener construction failures before native install roll back every child proxy exactly once`() {
        val kinds = listOf("wl_keyboard", "wl_pointer", "wl_touch", "wl_data_device")

        for ((index, kind) in kinds.withIndex()) {
            val proxy = 150L + index
            val expected = InjectedListenerFailure("$kind construct")
            val destroyed = mutableListOf<Long>()
            var nativeInstallReached = false

            val actual = assertFailsWith<InjectedListenerFailure>(kind) {
                constructOwnedWaylandChildListener(
                    kind = kind,
                    proxyPtr = proxy,
                    failOnNativeError = true,
                    destroyProxy = { destroyed += it },
                ) {
                    throw expected
                    @Suppress("UNREACHABLE_CODE")
                    nativeInstallReached = true
                }
            }

            assertSame(expected, actual, kind)
            assertFalse(nativeInstallReached, kind)
            assertEquals(listOf(proxy), destroyed, kind)
        }
    }

    @Test
    fun `strict seat child listener nonzero results fail and roll back every proxy`() {
        val kinds = listOf("wl_keyboard", "wl_pointer", "wl_touch", "wl_data_device")

        for ((index, kind) in kinds.withIndex()) {
            val proxy = 200L + index
            val destroyed = mutableListOf<Long>()

            val failure = assertFailsWith<IllegalStateException>(kind) {
                installOwnedWaylandListener(
                    kind = kind,
                    proxyPtr = proxy,
                    failOnNativeError = true,
                    destroyProxy = { destroyed += it },
                ) { -7 }
            }

            assertEquals("$kind listener installation failed: -7", failure.message, kind)
            assertEquals(listOf(proxy), destroyed, kind)
        }
    }

    @Test
    fun `listener failure remains primary when proxy rollback also fails`() {
        val expected = InjectedListenerFailure("listener")
        val rollback = IllegalStateException("rollback")

        val actual = assertFailsWith<InjectedListenerFailure> {
            installOwnedWaylandListener(
                kind = "wl_pointer",
                proxyPtr = 301L,
                failOnNativeError = true,
                destroyProxy = { throw rollback },
            ) { throw expected }
        }

        assertSame(expected, actual)
        assertEquals(listOf(rollback), actual.suppressed.toList())
    }

    @Test
    fun `construction failure remains primary when child proxy rollback also fails`() {
        val expected = InjectedListenerFailure("construct")
        val rollback = IllegalStateException("rollback construction")

        val actual = assertFailsWith<InjectedListenerFailure> {
            constructOwnedWaylandChildListener<Unit>(
                kind = "wl_keyboard",
                proxyPtr = 302L,
                failOnNativeError = true,
                destroyProxy = { throw rollback },
            ) { throw expected }
        }

        assertSame(expected, actual)
        assertEquals(listOf(rollback), actual.suppressed.toList())
    }
}

private class InjectedKeymapFailure(point: String) : RuntimeException(point)
private class InjectedListenerFailure(point: String) : RuntimeException(point)

private class FakeWaylandKeymapOperations(
    private val throwAt: String? = null,
    private val contextFailureOnCall: Int? = null,
    private val nullMapping: Boolean = false,
    private val mapFailed: Boolean = false,
    var munmapResult: Int = 0,
    var closeResult: Int = 0,
    private val throwUnrefs: Set<String> = emptySet(),
    private val sharedFailure: InjectedKeymapFailure? = null,
    private val sharedFailurePoints: Set<String> = emptySet(),
) : WaylandKeymapOperations {
    val trace = mutableListOf<String>()
    private var mappingOpen = false
    private var contextCalls = 0

    private fun fail(point: String) {
        if (point in sharedFailurePoints) throw checkNotNull(sharedFailure)
        if (throwAt == point) throw InjectedKeymapFailure(point)
    }

    override fun mmap(fd: Int, size: Int): MemorySegment? {
        trace += "mmap"
        fail("mmap")
        if (nullMapping) return null
        if (mapFailed) return MemorySegment.ofAddress(-1L)
        mappingOpen = true
        return MemorySegment.ofAddress(100L)
    }

    override fun munmap(mapping: MemorySegment, size: Int): Int {
        assertTrue(mappingOpen, "mapping must still be open when munmap is called")
        mappingOpen = false
        trace += "munmap"
        fail("munmap")
        return munmapResult
    }

    override fun close(fd: Int): Int {
        trace += "close($fd)"
        fail("close")
        return closeResult
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
        failUnref("unref-compose-state")
    }

    override fun composeTableUnref(table: MemorySegment) {
        trace += "unref-compose-table(${table.address()})"
        failUnref("unref-compose-table")
    }

    override fun stateUnref(state: MemorySegment) {
        trace += "unref-state(${state.address()})"
        failUnref("unref-state")
    }

    override fun keymapUnref(keymap: MemorySegment) {
        trace += "unref-keymap(${keymap.address()})"
        failUnref("unref-keymap")
    }

    override fun contextUnref(context: MemorySegment) {
        trace += "unref-context(${context.address()})"
        failUnref("unref-context")
    }

    private fun failUnref(operation: String) {
        fail(operation)
        if (operation in throwUnrefs) throw InjectedKeymapFailure(operation)
    }
}

private object StubPosixWakeup : PosixWakeup {
    override val readFd: Int = -1
    override fun signal(): Boolean = true
    override fun drain(): Boolean = true
    override fun close() = Unit
}
