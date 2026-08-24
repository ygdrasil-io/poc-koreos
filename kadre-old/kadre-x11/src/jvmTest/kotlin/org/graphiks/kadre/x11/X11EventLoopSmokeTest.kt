/**
 * Smoke tests for X11EventLoop and X11EventLoopProxy.
 *
 * Verifies:
 * - x11Running starts at false.
 * - runApp enables/disables the x11Running flag (handler that quits immediately).
 * - X11EventLoopProxy.wakeUp() delegates exclusively to the shared POSIX fd.
 *
 * X11EventLoop smoke tests.
 */
package org.graphiks.kadre.x11

import org.graphiks.kadre.x11.binding.*
import org.graphiks.kffi.x11.generated.KffiXClientMessageEventStorage
import org.graphiks.kffi.posix.PosixWakeup
import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import java.io.DataInputStream
import java.lang.foreign.Arena
import java.lang.foreign.ValueLayout
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class X11EventLoopSmokeTest {

    @Test
    fun `x11Running starts at false`() {
        // The global flag must be false at startup (or after a finished runApp)
        // Note: if another test left the flag at true, this test will fail —
        // but x11Running is reset to false in runApp's finally block.
        assertFalse(x11Running.get(), "x11Running must be false outside an active loop")
    }

    @Test
    fun `runApp fails explicitly without libX11`() {
        if (libX11 != null) return // Skip on Linux (requires an X server)

        var canCreateSurfacesCalled = false
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
                canCreateSurfacesCalled = true
                eventLoop.exit()
            }
            override fun windowEvent(eventLoop: ActiveEventLoop, windowId: WindowId, event: WindowEvent) {}
        }

        val failure = assertFailsWith<IllegalStateException> { runApp(handler) }

        assertContains(failure.message.orEmpty(), "backend=X11")
        assertContains(failure.message.orEmpty(), "operation=XOpenDisplay")
        assertFalse(canCreateSurfacesCalled,
            "canCreateSurfaces must not be called if libX11 is absent")
        assertFalse(x11Running.get(),
            "x11Running must be false after runApp()")
    }

    @Test
    fun `x11Running is reset to false after missing libX11 failure`() {
        if (libX11 != null) return // Skip on Linux

        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {}
            override fun windowEvent(eventLoop: ActiveEventLoop, windowId: WindowId, event: WindowEvent) {}
        }

        assertFalse(x11Running.get())
        assertFailsWith<IllegalStateException> { runApp(handler) }
        assertFalse(x11Running.get(),
            "x11Running must be false after a failed runApp()")
        assertFailsWith<IllegalStateException> { runApp(handler) }
        assertFalse(x11Running.get(),
            "x11Running must remain false after a second failed runApp()")
    }

    @Test
    fun `runApp throws IllegalStateException if already active`() {
        if (libX11 != null) return // Skip on Linux (non thread-safe flag manipulation in test)

        // Simulate an active loop
        x11Running.set(true)
        try {
            var threw = false
            try {
                runApp(object : ApplicationHandler {
                    override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {}
                    override fun windowEvent(eventLoop: ActiveEventLoop, windowId: WindowId, event: WindowEvent) {}
                })
            } catch (e: IllegalStateException) {
                threw = true
            }
            assertTrue(threw, "runApp must throw IllegalStateException if x11Running is true")
        } finally {
            x11Running.set(false)
        }
    }

    @Test
    fun `X11EventLoopProxy wakeUp works with zero windows`() {
        val wakeup = RecordingWakeup()
        val proxy = X11EventLoopProxy(wakeup)

        proxy.wakeUp()

        assertEquals(1, wakeup.signalCount)
    }

    @Test
    fun `background X11EventLoopProxy wakeUp records no Xlib operation`() {
        val trace = ConcurrentLinkedQueue<String>()
        val wakeup = RecordingWakeup(trace)
        val proxy = X11EventLoopProxy(wakeup)

        val executor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "x11-proxy-caller")
        }
        try {
            executor.submit { proxy.wakeUp() }.get()
        } finally {
            executor.shutdownNow()
        }

        assertEquals(listOf("wake:x11-proxy-caller"), trace.toList())
    }

    @Test
    fun `compiled X11EventLoopProxy depends on PosixWakeup and contains no Xlib or FFM reference`() {
        val constants = classUtf8Constants(X11EventLoopProxy::class.java)
        val required = "org/graphiks/kffi/posix/PosixWakeup"
        val forbidden = listOf(
            "org/graphiks/kadre/ffi/x11",
            "java/lang/foreign/MemorySegment",
            "java/lang/invoke/MethodHandle",
            "XSendEvent",
            "xSendEvent",
        )

        assertTrue(constants.any { required in it }, "compiled proxy must depend on PosixWakeup")
        for (reference in forbidden) {
            assertFalse(
                constants.any { reference in it },
                "compiled proxy contains forbidden reference: $reference",
            )
        }
    }

    @Test
    fun `X11 event window xid uses LP64 offsets per event type`() {
        Arena.ofConfined().use { arena ->
            val event = arena.allocate(96L, 8L)
            event.set(ValueLayout.JAVA_LONG, XANY_WINDOW_OFFSET, 10L)
            event.set(ValueLayout.JAVA_LONG, 40L, 20L)

            assertEquals(10L, x11EventWindowXid(event, FocusIn))
            assertEquals(10L, x11EventWindowXid(event, VisibilityNotify))
            assertEquals(10L, x11EventWindowXid(event, ClientMessage))
            assertEquals(20L, x11EventWindowXid(event, ConfigureNotify))
            assertEquals(20L, x11EventWindowXid(event, DestroyNotify))
        }
    }

    @Test
    fun `X11 client message LP64 offsets are canonical`() {
        Arena.ofConfined().use { arena ->
            val display = arena.allocate(8L, 8L)
            val event = arena.allocate(96L, 8L)
            val clientMessage = KffiXClientMessageEventStorage()
            val clientEvent = KffiXClientMessageEventStorage.Companion.reinterpret(event)
            clientMessage.send_event(clientEvent, 1)
            clientMessage.display(clientEvent, display)
            clientMessage.window(clientEvent, 10L)
            clientMessage.message_type(clientEvent, 20L)
            clientMessage.format(clientEvent, 32)
            clientMessage.data_l0(clientEvent, 30L)

            assertEquals(1, event.get(ValueLayout.JAVA_INT, 16L))
            assertEquals(display.address(), event.get(ValueLayout.ADDRESS, 24L).address())
            assertEquals(10L, event.get(ValueLayout.JAVA_LONG, 32L))
            assertEquals(20L, event.get(ValueLayout.JAVA_LONG, 40L))
            assertEquals(32, event.get(ValueLayout.JAVA_INT, 48L))
            assertEquals(30L, event.get(ValueLayout.JAVA_LONG, 56L))
        }
    }
}

private class RecordingWakeup(
    private val trace: ConcurrentLinkedQueue<String>? = null,
) : PosixWakeup {
    override val readFd: Int = 73
    var signalCount: Int = 0
        private set

    override fun signal(): Boolean {
        signalCount += 1
        trace?.add("wake:${Thread.currentThread().name}")
        return true
    }

    override fun drain(): Boolean = true

    override fun close() = Unit
}

private fun classUtf8Constants(type: Class<*>): Set<String> {
    val resource = "/${type.name.replace('.', '/')}.class"
    val stream = checkNotNull(type.getResourceAsStream(resource)) {
        "compiled class resource is unavailable: $resource"
    }
    return DataInputStream(stream.buffered()).use { input ->
        check(input.readInt() == 0xCAFEBABE.toInt()) { "invalid class-file magic: $resource" }
        input.readUnsignedShort() // minor version
        input.readUnsignedShort() // major version
        val constantPoolCount = input.readUnsignedShort()
        val utf8Constants = linkedSetOf<String>()
        var index = 1
        while (index < constantPoolCount) {
            when (val tag = input.readUnsignedByte()) {
                1 -> utf8Constants += input.readUTF()
                3, 4 -> input.skipNBytes(4)
                5, 6 -> {
                    input.skipNBytes(8)
                    index += 1 // Long and Double consume two constant-pool slots.
                }
                7, 8, 16, 19, 20 -> input.skipNBytes(2)
                9, 10, 11, 12, 17, 18 -> input.skipNBytes(4)
                15 -> input.skipNBytes(3)
                else -> error("unsupported class-file constant-pool tag $tag at index $index")
            }
            index += 1
        }
        utf8Constants
    }
}
