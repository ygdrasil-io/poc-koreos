package org.graphiks.kadre.wayland

import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kffi.posix.PosixWakeup
import java.lang.foreign.MemorySegment
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WaylandNativeUpcallTest {
    private val loop = WaylandEventLoop(
        displayPtr = 77L,
        compositorPtr = 0L,
        xdgWmBasePtr = 0L,
        shmPtr = 0L,
        wakeup = NativeUpcallStubWakeup,
    )

    @AfterTest
    fun resetGlobalState() {
        WaylandTextInput.resetForTest()
        WaylandFocusState.clear(KEYBOARD_SURFACE)
    }

    @Test
    fun `text input sink failure returns from upcall and rethrows on Kotlin loop`() {
        val listener = TextInputListener(
            onEvent = throwingWindowSink("text-input"),
            onNativeFailure = loop::queueNativeFailure,
        )

        listener.onEnter(NULL, NULL, MemorySegment.ofAddress(TEXT_INPUT_SURFACE))

        assertQueuedFailure("text-input")
    }

    @Test
    fun `keyboard sink failure returns from upcall and rethrows on Kotlin loop`() {
        val listener = WlKeyboardListener(
            onEvent = throwingWindowSink("keyboard"),
            onDeviceEvent = {},
            seatPtr = 91L,
            deviceFilter = WaylandDeviceFilter(),
            onNativeFailure = loop::queueNativeFailure,
        )

        listener.onEnter(NULL, NULL, 1, MemorySegment.ofAddress(KEYBOARD_SURFACE), NULL)

        assertQueuedFailure("keyboard")
        listener.close()
    }

    @Test
    fun `pointer sink failure returns from upcall and rethrows on Kotlin loop`() {
        val listener = WlPointerListener(
            onEvent = throwingWindowSink("pointer"),
            seatPtr = 92L,
            onNativeFailure = loop::queueNativeFailure,
        )

        listener.onMotion(NULL, NULL, 1, 256, 512)

        assertQueuedFailure("pointer")
    }

    @Test
    fun `touch sink failure returns from upcall and rethrows on Kotlin loop`() {
        val listener = WlTouchListener(
            onEvent = throwingWindowSink("touch"),
            onNativeFailure = loop::queueNativeFailure,
        )

        listener.onDown(NULL, NULL, 1, 2, MemorySegment.ofAddress(444L), 3, 256, 512)

        assertQueuedFailure("touch")
    }

    @Test
    fun `data device sink failure returns from upcall and rethrows on Kotlin loop`() {
        val dnd = WaylandDragAndDrop(
            dataDevicePtr = 101L,
            displayPtr = 102L,
            onEvent = throwingWindowSink("data-device"),
        )
        val listener = WlDataDeviceListener(dnd, loop::queueNativeFailure)

        listener.onEnter(NULL, NULL, 1, MemorySegment.ofAddress(445L), 256, 512, NULL)

        assertQueuedFailure("data-device")
    }

    @Test
    fun `failure router failure never escapes the native upcall`() {
        val listener = WlPointerListener(
            onEvent = throwingWindowSink("sink"),
            seatPtr = 93L,
            onNativeFailure = { throw InjectedUpcallFailure("router") },
        )

        listener.onMotion(NULL, NULL, 1, 256, 512)
    }

    private fun throwingWindowSink(message: String): (Long, WindowEvent) -> Unit = { _, _ ->
        throw InjectedUpcallFailure(message)
    }

    private fun assertQueuedFailure(message: String) {
        val failure = assertFailsWith<InjectedUpcallFailure> {
            loop.throwPendingNativeFailure()
        }
        assertEquals(message, failure.message)
    }

    private companion object {
        val NULL: MemorySegment = MemorySegment.NULL
        const val TEXT_INPUT_SURFACE = 440L
        const val KEYBOARD_SURFACE = 441L
    }
}

private class InjectedUpcallFailure(message: String) : RuntimeException(message)

private object NativeUpcallStubWakeup : PosixWakeup {
    override val readFd: Int = -1
    override fun signal(): Boolean = true
    override fun drain(): Boolean = true
    override fun close() = Unit
}
