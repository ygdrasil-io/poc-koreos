package org.graphiks.kadre.appkit

import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppKitRedrawWakeTest {
    @Test
    fun `idle redraw wakes owner and emits one redraw without proxy wake`() =
        withHarness { harness ->
            harness.window.requestRedraw()

            assertEquals(1, harness.api.wakeCount)
            assertEquals(1, harness.api.createdTimers.size)
            assertFalse(harness.window.needsRedraw)

            harness.deliverPendingIteration()

            assertEquals(listOf<StartCause>(StartCause.WaitCancelled()), harness.causes)
            assertEquals(listOf(harness.windowId), harness.redraws)
        }

    @Test
    fun `two redraw requests before consumption coalesce wake and redraw`() =
        withHarness { harness ->
            harness.window.requestRedraw()
            harness.window.requestRedraw()

            assertEquals(1, harness.api.wakeCount)
            assertEquals(1, harness.api.createdTimers.size)
            assertFalse(harness.window.needsRedraw)

            harness.deliverPendingIteration()

            assertEquals(listOf(harness.windowId), harness.redraws)
        }

    @Test
    fun `redraw requested after consumption wakes a new batch`() =
        withHarness { harness ->
            harness.window.requestRedraw()
            harness.deliverPendingIteration()

            harness.window.requestRedraw()

            assertEquals(2, harness.api.wakeCount)
            assertEquals(2, harness.api.createdTimers.distinct().size)
            harness.deliverPendingIteration()
            assertEquals(listOf(harness.windowId, harness.windowId), harness.redraws)
        }

    @Test
    fun `redraw requested while delivering an iteration stays in that iteration`() =
        withHarness { harness ->
            harness.wakeOwner()
            harness.requestRedrawOnNextCause = true

            harness.deliverPendingIteration()

            assertEquals(1, harness.api.wakeCount)
            assertEquals(1, harness.api.createdTimers.size)
            assertEquals(listOf<StartCause>(StartCause.WaitCancelled()), harness.causes)
            assertEquals(listOf(harness.windowId), harness.redraws)
        }

    @Test
    fun `redraw requested after the before-waiting snapshot persists a new iteration`() =
        withHarness { harness ->
            harness.wakeOwner()
            harness.requestRedrawDuringNextAboutToWait = true

            harness.deliverPendingIteration()

            assertEquals(2, harness.api.wakeCount)
            assertEquals(2, harness.api.createdTimers.size)
            assertEquals(listOf<StartCause>(StartCause.WaitCancelled()), harness.causes)
            assertTrue(harness.redraws.isEmpty())

            harness.deliverPendingIteration()

            assertEquals(List<StartCause>(2) { StartCause.WaitCancelled() }, harness.causes)
            assertEquals(listOf(harness.windowId), harness.redraws)
        }

    @Test
    fun `redraw after close neither wakes nor emits`() =
        withHarness { harness ->
            harness.closeWindow()
            val wakeCountAfterClose = harness.api.wakeCount
            val timerCountAfterClose = harness.api.createdTimers.size

            harness.window.requestRedraw()

            assertEquals(wakeCountAfterClose, harness.api.wakeCount)
            assertEquals(timerCountAfterClose, harness.api.createdTimers.size)
            assertTrue(harness.redraws.isEmpty())
        }

    @Test
    fun `closed owner rejection retains the window redraw fallback`() =
        withHarness { harness ->
            harness.closeOwnerKeepingAttachment()

            harness.window.requestRedraw()

            assertEquals(0, harness.api.wakeCount)
            assertTrue(harness.window.needsRedraw)
        }

    private inline fun withHarness(block: (RedrawHarness) -> Unit) {
        RedrawHarness().use(block)
    }

    private class RedrawHarness : AutoCloseable {
        val windowId = WindowId(7L)
        val causes = mutableListOf<StartCause>()
        val redraws = mutableListOf<WindowId>()
        val api = RedrawRecordingCFRunLoopApi()
        private val state = AppKitLoopState { 1_000L }
        private val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) = Unit
        }
        private val eventLoop = AppKitEventLoop(handler)
        var requestRedrawOnNextCause = false
        var requestRedrawDuringNextAboutToWait = false
        private val owner = CFRunLoopOwner.install(
            api = api,
            state = state,
            onAfterWaiting = { cause ->
                causes += cause
                if (requestRedrawOnNextCause) {
                    requestRedrawOnNextCause = false
                    window.requestRedraw()
                }
            },
            onBeforeWaiting = {
                redraws += state.takeRedraws()
                if (requestRedrawDuringNextAboutToWait) {
                    requestRedrawDuringNextAboutToWait = false
                    window.requestRedraw()
                }
                ControlFlow.Wait
            },
        )
        val window: AppKitWindow = allocateWindowWithoutAppKit().also { allocated ->
            setField(allocated, "id", windowId.value)
            setField(allocated, "_eventLoop", eventLoop)
        }

        init {
            owner.consumeLaunchIteration()
            eventLoop.installRunLoopOwner(owner)
            CFRunLoopOwner.dispatchObserverCallback(
                api.createdObserver,
                CFRunLoopOwner.BEFORE_WAITING,
            )
            eventLoop.registerWindowCloseActions(
                windowId = windowId,
                unregisterCallbacks = { setField(window, "_eventLoop", null) },
                closeNative = {},
            )
        }

        fun deliverPendingIteration() {
            val timer = api.createdTimers.last()
            CFRunLoopOwner.dispatchTimerCallback(timer)
            CFRunLoopOwner.dispatchObserverCallback(
                api.createdObserver,
                CFRunLoopOwner.BEFORE_WAITING,
            )
        }

        fun wakeOwner() {
            owner.wakeUp()
        }

        fun closeWindow() {
            eventLoop.closeWindow(windowId)
        }

        fun closeOwnerKeepingAttachment() {
            owner.close()
        }

        override fun close() {
            eventLoop.clearRunLoopOwner(owner)
            owner.close()
        }

        private fun allocateWindowWithoutAppKit(): AppKitWindow {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val unsafeField = unsafeClass.getDeclaredField("theUnsafe").apply {
                isAccessible = true
            }
            val unsafe = unsafeField.get(null)
            return unsafeClass
                .getMethod("allocateInstance", Class::class.java)
                .invoke(unsafe, AppKitWindow::class.java) as AppKitWindow
        }

        private fun setField(target: AppKitWindow, name: String, value: Any?) {
            AppKitWindow::class.java.getDeclaredField(name).apply {
                isAccessible = true
                set(target, value)
            }
        }
    }

    private class RedrawRecordingCFRunLoopApi : CFRunLoopApi {
        var createdObserver = 0L
        val createdTimers = mutableListOf<Long>()
        var wakeCount = 0
        private var nextRef = 700L

        override fun createObserver(activities: Long): Long = nextRef++.also {
            createdObserver = it
        }

        override fun addObserver(observer: Long) = Unit
        override fun removeObserver(observer: Long) = Unit
        override fun invalidateObserver(observer: Long) = Unit
        override fun createTimer(deadlineEpochMillis: Long): Long = nextRef++.also(createdTimers::add)
        override fun createImmediateTimer(): Long = nextRef++.also(createdTimers::add)
        override fun addTimer(timer: Long) = Unit
        override fun invalidateTimer(timer: Long) = Unit
        override fun removeTimer(timer: Long) = Unit
        override fun wakeUp() {
            wakeCount++
        }

        override fun release(ref: Long) = Unit
        override fun close() = Unit
    }
}
