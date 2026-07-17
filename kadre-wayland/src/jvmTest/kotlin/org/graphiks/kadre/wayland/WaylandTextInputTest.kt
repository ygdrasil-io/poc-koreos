package org.graphiks.kadre.wayland

import org.graphiks.kadre.core.ImePurpose
import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.ffi.wayland.zwpTextInputV3Interface
import java.lang.foreign.ValueLayout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class WaylandTextInputTest {
    @AfterTest
    fun resetTextInputState() {
        WaylandTextInput.resetForTest()
    }

    @Test
    fun `text input v3 request opcodes and interface metadata match the protocol`() {
        val pointerSize = ValueLayout.ADDRESS.byteSize()

        assertEquals(0, ZWP_TEXT_INPUT_V3_DESTROY)
        assertEquals(1, ZWP_TEXT_INPUT_V3_ENABLE)
        assertEquals(2, ZWP_TEXT_INPUT_V3_DISABLE)
        assertEquals(5, ZWP_TEXT_INPUT_V3_SET_CONTENT_TYPE)
        assertEquals(6, ZWP_TEXT_INPUT_V3_SET_CURSOR_RECTANGLE)
        assertEquals(7, ZWP_TEXT_INPUT_V3_COMMIT)
        assertEquals(8, zwpTextInputV3Interface.get(ValueLayout.JAVA_INT, pointerSize + 4L))
    }

    @Test
    fun `every text input mutation commits then flushes with exact arguments`() {
        val operations = RecordingTextInputRequestOperations()
        WaylandTextInput.textInputPtr = 101L
        WaylandTextInput.displayPtr = 202L
        WaylandTextInput.version = 1

        waylandTextInputEnable(operations)
        waylandTextInputDisable(operations)
        waylandTextInputSetPurpose(ImePurpose.Password, operations)
        waylandTextInputSetCursorRectangle(
            position = PhysicalPosition(20, 10),
            size = PhysicalSize(8, 6),
            scale = 2.0,
            operations = operations,
        )

        assertEquals(
            listOf(
                "void:101:1:1:0",
                "void:101:7:1:0",
                "flush:202",
                "void:101:2:1:0",
                "void:101:7:1:0",
                "flush:202",
                "two-uint:101:5:1:0:0:8",
                "void:101:7:1:0",
                "flush:202",
                "four-int:101:6:1:0:10:5:4:3",
                "void:101:7:1:0",
                "flush:202",
            ),
            operations.trace,
        )
    }

    @Test
    fun `text input mutation is a no-op without an owned proxy`() {
        val operations = RecordingTextInputRequestOperations()

        waylandTextInputEnable(operations)
        assertEquals(false, WaylandTextInput.imeEnabled)
        waylandTextInputDisable(operations)
        waylandTextInputSetPurpose(ImePurpose.Terminal, operations)
        waylandTextInputSetCursorRectangle(
            PhysicalPosition(1, 2),
            PhysicalSize(3, 4),
            scale = 1.0,
            operations = operations,
        )

        assertEquals(emptyList(), operations.trace)
        assertEquals(false, WaylandTextInput.imeEnabled)
    }

    @Test
    fun `text input owner retains listener and destroys proxy before releasing it`() {
        val operations = RecordingTextInputCreationOperations()
        val lifetime = WaylandNativeListenerLifetime()
        val onEvent: (Long, org.graphiks.kadre.core.WindowEvent) -> Unit = { _, _ -> }

        val owner = createTextInput(
            managerPtr = 10L,
            seatPtr = 20L,
            display = 30L,
            onEvent = onEvent,
            nativeListenerLifetime = lifetime,
            failOnNativeError = true,
            operations = operations,
        )

        assertNotNull(owner)
        assertEquals(101L, WaylandTextInput.textInputPtr)
        assertEquals(30L, WaylandTextInput.displayPtr)
        assertEquals(4, WaylandTextInput.version)
        assertSame(onEvent, WaylandTextInput.onImeEvent)
        assertEquals(false, operations.listener.closed)

        owner.close()

        assertEquals(0L, WaylandTextInput.textInputPtr)
        assertEquals(0L, WaylandTextInput.displayPtr)
        assertEquals(1, WaylandTextInput.version)
        assertEquals(0L, WaylandTextInput.focusedSurfacePtr)
        assertEquals(false, WaylandTextInput.imeEnabled)
        assertNull(WaylandTextInput.onImeEvent)
        assertEquals(
            listOf(
                "create:10:20",
                "version:101",
                "listener:create",
                "listener:install:101",
                "destroy:101:0:4:1:state=0",
                "listener:close",
            ),
            operations.trace,
        )
    }

    @Test
    fun `listener installation failure rolls back proxy and listener without publishing state`() {
        val expected = IllegalStateException("listener invoke failed")
        val operations = RecordingTextInputCreationOperations(listenerFailure = expected)
        val lifetime = WaylandNativeListenerLifetime()

        val actual = assertFailsWith<IllegalStateException> {
            createTextInput(
                managerPtr = 10L,
                seatPtr = 20L,
                display = 30L,
                onEvent = { _, _ -> },
                nativeListenerLifetime = lifetime,
                failOnNativeError = true,
                operations = operations,
            )
        }

        assertSame(expected, actual)
        assertEquals(0L, WaylandTextInput.textInputPtr)
        assertNull(WaylandTextInput.onImeEvent)
        assertEquals(1, operations.destroyCount)
        assertEquals(true, operations.listener.closed)
    }

    @Test
    fun `nonzero listener result rolls back transaction`() {
        val operations = RecordingTextInputCreationOperations(listenerResult = -9)
        val lifetime = WaylandNativeListenerLifetime()

        val failure = assertFailsWith<IllegalStateException> {
            createTextInput(
                managerPtr = 10L,
                seatPtr = 20L,
                display = 30L,
                onEvent = { _, _ -> },
                nativeListenerLifetime = lifetime,
                failOnNativeError = true,
                operations = operations,
            )
        }

        assertEquals("text input listener installation failed: -9", failure.message)
        assertEquals(1, operations.destroyCount)
        assertEquals(true, operations.listener.closed)
        assertEquals(0L, WaylandTextInput.textInputPtr)
    }

    @Test
    fun `destroy failure resets singleton and retains listener until display disconnect`() {
        val destroyFailure = IllegalStateException("destroy failed")
        val operations = RecordingTextInputCreationOperations(destroyFailure = destroyFailure)
        val lifetime = WaylandNativeListenerLifetime()
        val owner = assertNotNull(
            createTextInput(
                managerPtr = 10L,
                seatPtr = 20L,
                display = 30L,
                onEvent = { _, _ -> },
                nativeListenerLifetime = lifetime,
                failOnNativeError = true,
                operations = operations,
            ),
        )

        val actual = assertFailsWith<IllegalStateException> { owner.close() }

        assertSame(destroyFailure, actual)
        assertEquals(0L, WaylandTextInput.textInputPtr)
        assertEquals(false, operations.listener.closed)

        lifetime.closeAfterDisplayDisconnect()

        assertEquals(true, operations.listener.closed)
    }

    @Test
    fun `every text input acquisition failure rolls back all acquired resources`() {
        val cases = listOf(
            TextInputFailureCase("create", expectedDestroyCount = 0, expectedListenerClosed = false),
            TextInputFailureCase("null", expectedDestroyCount = 0, expectedListenerClosed = false),
            TextInputFailureCase("version", expectedDestroyCount = 1, expectedListenerClosed = false),
            TextInputFailureCase("listener", expectedDestroyCount = 1, expectedListenerClosed = false),
        )

        for (case in cases) {
            seedTextInputState()
            val operations = RecordingTextInputCreationOperations(
                createResult = if (case.point == "null") 0L else 101L,
                failureAt = case.point.takeUnless { it == "null" },
            )
            val lifetime = WaylandNativeListenerLifetime()

            assertFailsWith<IllegalStateException>(case.point) {
                createTextInput(
                    managerPtr = 10L,
                    seatPtr = 20L,
                    display = 30L,
                    onEvent = { _, _ -> },
                    nativeListenerLifetime = lifetime,
                    failOnNativeError = true,
                    operations = operations,
                )
            }

            assertTextInputStateReset(case.point)
            assertEquals(case.expectedDestroyCount, operations.destroyCount, case.point)
            assertEquals(case.expectedListenerClosed, operations.listener.closed, case.point)
        }
    }

    @Test
    fun `listener lifetime registration failure destroys proxy and closes listener`() {
        val registrationFailure = IllegalStateException("registration failed")
        val operations = RecordingTextInputCreationOperations()
        val lifetime = object : WaylandNativeListenerLifetime() {
            override fun register(binding: AutoCloseable): WaylandNativeListenerLease =
                throw registrationFailure
        }

        val actual = assertFailsWith<IllegalStateException> {
            createTextInput(
                managerPtr = 10L,
                seatPtr = 20L,
                display = 30L,
                onEvent = { _, _ -> },
                nativeListenerLifetime = lifetime,
                failOnNativeError = true,
                operations = operations,
            )
        }

        assertSame(registrationFailure, actual)
        assertEquals(1, operations.destroyCount)
        assertEquals(true, operations.listener.closed)
        assertTextInputStateReset("registration")
    }

    @Test
    fun `rollback destroy failure is suppressed and listener remains until disconnect`() {
        val listenerFailure = IllegalStateException("listener failed")
        val destroyFailure = IllegalArgumentException("destroy failed")
        val operations = RecordingTextInputCreationOperations(
            listenerFailure = listenerFailure,
            destroyFailure = destroyFailure,
        )
        val lifetime = WaylandNativeListenerLifetime()

        val actual = assertFailsWith<IllegalStateException> {
            createTextInput(
                managerPtr = 10L,
                seatPtr = 20L,
                display = 30L,
                onEvent = { _, _ -> },
                nativeListenerLifetime = lifetime,
                failOnNativeError = true,
                operations = operations,
            )
        }

        assertSame(listenerFailure, actual)
        assertEquals(listOf(destroyFailure), actual.suppressed.toList())
        assertEquals(false, operations.listener.closed)
        assertTextInputStateReset("destroy rollback")

        lifetime.closeAfterDisplayDisconnect()
        assertEquals(true, operations.listener.closed)
    }

    private fun seedTextInputState() {
        WaylandTextInput.textInputPtr = 901L
        WaylandTextInput.displayPtr = 902L
        WaylandTextInput.version = 9
        WaylandTextInput.focusedSurfacePtr = 903L
        WaylandTextInput.imeEnabled = true
        WaylandTextInput.onImeEvent = { _, _ -> }
    }

    private fun assertTextInputStateReset(label: String) {
        assertEquals(0L, WaylandTextInput.textInputPtr, label)
        assertEquals(0L, WaylandTextInput.displayPtr, label)
        assertEquals(1, WaylandTextInput.version, label)
        assertEquals(0L, WaylandTextInput.focusedSurfacePtr, label)
        assertEquals(false, WaylandTextInput.imeEnabled, label)
        assertNull(WaylandTextInput.onImeEvent, label)
    }

    private data class TextInputFailureCase(
        val point: String,
        val expectedDestroyCount: Int,
        val expectedListenerClosed: Boolean,
    )

    private class RecordingTextInputRequestOperations : WaylandTextInputRequestOperations {
        val trace = mutableListOf<String>()

        override fun marshalVoid(proxyPtr: Long, opcode: Int, version: Int, flags: Int) {
            trace += "void:$proxyPtr:$opcode:$version:$flags"
        }

        override fun marshalTwoUint(
            proxyPtr: Long,
            opcode: Int,
            version: Int,
            flags: Int,
            first: Int,
            second: Int,
        ) {
            trace += "two-uint:$proxyPtr:$opcode:$version:$flags:$first:$second"
        }

        override fun marshalFourInt(
            proxyPtr: Long,
            opcode: Int,
            version: Int,
            flags: Int,
            first: Int,
            second: Int,
            third: Int,
            fourth: Int,
        ) {
            trace += "four-int:$proxyPtr:$opcode:$version:$flags:$first:$second:$third:$fourth"
        }

        override fun flush(displayPtr: Long): Int {
            trace += "flush:$displayPtr"
            return 0
        }
    }

    private class RecordingTextInputCreationOperations(
        private val listenerResult: Int = 0,
        private val listenerFailure: Throwable? = null,
        private val destroyFailure: Throwable? = null,
        private val createResult: Long = 101L,
        private val failureAt: String? = null,
    ) : WaylandTextInputCreationOperations {
        val trace = mutableListOf<String>()
        val listener = RecordingTextInputListenerRegistration(trace, listenerResult, listenerFailure)
        var destroyCount = 0

        override fun createTextInput(managerPtr: Long, seatPtr: Long): Long {
            trace += "create:$managerPtr:$seatPtr"
            if (failureAt == "create") throw IllegalStateException("create failed")
            return createResult
        }

        override fun getVersion(proxyPtr: Long): Int {
            trace += "version:$proxyPtr"
            if (failureAt == "version") throw IllegalStateException("version failed")
            return 4
        }

        override fun createListenerRegistration(
            onEvent: (Long, org.graphiks.kadre.core.WindowEvent) -> Unit,
        ): WaylandTextInputListenerRegistration {
            trace += "listener:create"
            if (failureAt == "listener") throw IllegalStateException("listener failed")
            return listener
        }

        override fun marshalVoid(proxyPtr: Long, opcode: Int, version: Int, flags: Int) {
            if (opcode == ZWP_TEXT_INPUT_V3_DESTROY) {
                destroyCount += 1
                trace += "destroy:$proxyPtr:$opcode:$version:$flags:state=${WaylandTextInput.textInputPtr}"
                destroyFailure?.let { throw it }
            }
        }

        override fun marshalTwoUint(
            proxyPtr: Long,
            opcode: Int,
            version: Int,
            flags: Int,
            first: Int,
            second: Int,
        ) = Unit

        override fun marshalFourInt(
            proxyPtr: Long,
            opcode: Int,
            version: Int,
            flags: Int,
            first: Int,
            second: Int,
            third: Int,
            fourth: Int,
        ) = Unit

        override fun flush(displayPtr: Long): Int = 0
    }

    private class RecordingTextInputListenerRegistration(
        private val trace: MutableList<String>,
        private val result: Int,
        private val failure: Throwable?,
    ) : WaylandTextInputListenerRegistration {
        var closed = false

        override fun install(proxyPtr: Long): Int {
            trace += "listener:install:$proxyPtr"
            failure?.let { throw it }
            return result
        }

        override fun close() {
            closed = true
            trace += "listener:close"
        }
    }
}
