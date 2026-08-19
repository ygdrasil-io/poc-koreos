package org.graphiks.kadre

import java.lang.reflect.InvocationTargetException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertSame

class LinuxBackendLaunchTest {

    @Test
    fun `launch surfaces the native cause instead of its reflection wrapper`() {
        val failure = assertFails {
            invokeBackendRunApp(FailingBackend::class.java.name, handler)
        }

        assertSame(nativeFailure, failure)
        assertFalse(failure is InvocationTargetException)
        assertIs<IllegalStateException>(failure)
        assertContains(
            failure.suppressed.single().message.orEmpty(),
            FailingBackend::class.java.name,
        )
    }

    private object FailingBackend {
        @JvmStatic
        fun runApp(handler: ApplicationHandler) {
            throw nativeFailure
        }
    }

    private companion object {
        val nativeFailure = IllegalStateException("native display failure")

        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) = Unit
        }
    }
}
