package org.graphiks.kadre

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertSame

class LinuxBackendLaunchTest {

    @Test
    fun `launch surfaces the native cause instead of its reflection wrapper`() {
        val failure = assertFails {
            invokeBackendRunApp(FailingBackend::class.java.name, handler, platform = "macOS")
        }

        assertSame(nativeFailure, failure)
        assertFalse(failure is InvocationTargetException)
        assertIs<IllegalStateException>(failure)
        assertContains(
            failure.suppressed.single().message.orEmpty(),
            "macOS backend",
        )
        assertContains(
            failure.suppressed.single().message.orEmpty(),
            FailingBackend::class.java.name,
        )
    }

    @Test
    fun `Linux facade probes before callbacks and never falls back after launch failure`() {
        callbackTrace.clear()

        val failure = assertFails {
            runLinuxBackend(
                handler = callbackHandler,
                environment = mapOf("WAYLAND_DISPLAY" to "wayland-0", "DISPLAY" to ":0")::get,
                loadClass = {},
                probe = { backend -> callbackTrace += "probe:$backend" },
                launch = { _, appHandler ->
                    invokeBackendRunApp(
                        CallbackFailingBackend::class.java.name,
                        appHandler,
                        platform = "Wayland",
                    )
                },
            )
        }

        assertSame(handlerFailure, failure)
        assertEquals(
            listOf("probe:${LinuxBackendDetector.WAYLAND_CLASS}", "callback"),
            callbackTrace,
        )
    }

    private object FailingBackend {
        @JvmStatic
        fun runApp(handler: ApplicationHandler) {
            throw nativeFailure
        }
    }

    private object CallbackFailingBackend {
        @JvmStatic
        fun runApp(handler: ApplicationHandler) {
            handler.resumed(testEventLoop)
            throw handlerFailure
        }
    }

    private companion object {
        val nativeFailure = IllegalStateException("native display failure")
        val handlerFailure = IllegalStateException("handler failure after startup")
        val callbackTrace = mutableListOf<String>()

        val testEventLoop: ActiveEventLoop = Proxy.newProxyInstance(
            ActiveEventLoop::class.java.classLoader,
            arrayOf(ActiveEventLoop::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "toString" -> "test event loop"
                "hashCode" -> 0
                "equals" -> false
                else -> error("Unexpected ActiveEventLoop call: ${method.name}")
            }
        } as ActiveEventLoop

        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) = Unit
        }

        val callbackHandler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) = Unit

            override fun resumed(eventLoop: ActiveEventLoop) {
                callbackTrace += "callback"
            }
        }
    }
}
