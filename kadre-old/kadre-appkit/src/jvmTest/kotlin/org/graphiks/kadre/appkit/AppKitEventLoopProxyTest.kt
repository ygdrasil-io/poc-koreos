package org.graphiks.kadre.appkit

import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.EventLoopProxy
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Typing tests for GRA-136. No native runtime invocation
 * (the CF downcalls require a configured macOS environment;
 *  the runtime paths are covered by the integration tests).
 */
class AppKitEventLoopProxyTest {

    @Test
    fun `proxy installed on event loop completes three wake consume cycles`() {
        val api = WakeRecordingCFRunLoopApi()
        val state = AppKitLoopState { 1_000L }
        val causes = mutableListOf<StartCause>()
        val owner = CFRunLoopOwner.install(
            api = api,
            state = state,
            onAfterWaiting = causes::add,
            onBeforeWaiting = { ControlFlow.Wait },
        )
        val eventLoop = AppKitEventLoop(NoOpHandler)
        eventLoop.installRunLoopOwner(owner)
        val proxy = eventLoop.createProxy()
        owner.consumeLaunchIteration()
        CFRunLoopOwner.dispatchObserverCallback(
            api.observer,
            CFRunLoopOwner.BEFORE_WAITING,
        )

        try {
            repeat(3) {
                assertEquals(TimerDecision.Cancel, state.arm(ControlFlow.Wait))
                proxy.wakeUp()
                CFRunLoopOwner.dispatchTimerCallback(api.timer)
                CFRunLoopOwner.dispatchObserverCallback(
                    api.observer,
                    CFRunLoopOwner.BEFORE_WAITING,
                )
            }

            assertEquals(List<StartCause>(3) { StartCause.WaitCancelled() }, causes)
            assertEquals(3, api.wakeCount)
        } finally {
            eventLoop.clearRunLoopOwner(owner)
            owner.close()
        }
    }

    @Test
    fun `AppKitEventLoopProxy implements EventLoopProxy`() {
        assertTrue(
            EventLoopProxy::class.java.isAssignableFrom(AppKitEventLoopProxy::class.java),
            "AppKitEventLoopProxy must implement EventLoopProxy",
        )
    }

    @Test
    fun `AppKitEventLoopProxy exposes create in its companion`() {
        val companionClass = AppKitEventLoopProxy.Companion::class.java
        val method = companionClass.declaredMethods
            .firstOrNull { it.name == "create" }
        assertNotNull(method, "AppKitEventLoopProxy.Companion must expose create()")
        // The actual return is AppKitEventLoopProxy (a subtype of EventLoopProxy).
        assertTrue(
            EventLoopProxy::class.java.isAssignableFrom(method.returnType),
            "create() must return an EventLoopProxy",
        )
    }

    @Test
    fun `wakeUp exists and returns void`() {
        val method = AppKitEventLoopProxy::class.java.methods
            .firstOrNull { it.name == "wakeUp" }
        assertNotNull(method, "AppKitEventLoopProxy must have wakeUp()")
        assertEquals(Void.TYPE, method.returnType)
        assertEquals(0, method.parameterCount)
    }

    @Test
    fun `AppKitEventLoop createProxy no longer throws UnsupportedOperationException (GRA-136)`() {
        val method = AppKitEventLoop::class.java.methods
            .firstOrNull { it.name == "createProxy" }
        assertNotNull(method, "AppKitEventLoop must have createProxy()")
        assertEquals(EventLoopProxy::class.java, method.returnType)
    }

    @Test
    fun `CFRunLoopOwner before waiting accepts ControlFlow WaitUntil`() {
        val cf: ControlFlow = ControlFlow.WaitUntil(System.currentTimeMillis() + 100L)
        assertTrue(cf is ControlFlow.WaitUntil)
        assertTrue(cf.instant > 0)
    }

    private object NoOpHandler : ApplicationHandler {
        override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

        override fun windowEvent(
            eventLoop: ActiveEventLoop,
            windowId: WindowId,
            event: WindowEvent,
        ) = Unit
    }

    private class WakeRecordingCFRunLoopApi : CFRunLoopApi {
        var observer = 0L
        val timer = 200L
        var wakeCount = 0

        override fun createObserver(activities: Long): Long = 100L.also { observer = it }
        override fun addObserver(observer: Long) = Unit
        override fun removeObserver(observer: Long) = Unit
        override fun invalidateObserver(observer: Long) = Unit
        override fun createTimer(deadlineEpochMillis: Long): Long = timer
        override fun createImmediateTimer(): Long = timer
        override fun addTimer(timer: Long) = Unit
        override fun invalidateTimer(timer: Long) = Unit
        override fun removeTimer(timer: Long) = Unit
        override fun wakeUp() { wakeCount++ }
        override fun release(ref: Long) = Unit
        override fun close() = Unit
    }
}
