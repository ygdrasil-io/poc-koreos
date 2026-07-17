package org.graphiks.kadre.android

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.EventLoopProxy
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.Window
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidSchedulerDeviceTest {
    @Test
    fun redrawRequestedFromAboutToWaitStartsOneOrderedIteration() {
        val trace = CopyOnWriteArrayList<String>()
        val completedTrace = CompletableFuture<List<String>>()
        val handler = object : GuardedHandler() {
            private lateinit var window: Window
            private var redrawRequested = false

            override fun onCanCreateSurfaces(eventLoop: ActiveEventLoop) {
                window = eventLoop.createWindow(WindowAttributes())
            }

            override fun onNewEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                if (redrawRequested) {
                    trace += "newEvents:$startCause"
                }
            }

            override fun onWindowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (redrawRequested && event is WindowEvent.RedrawRequested) {
                    trace += "RedrawRequested"
                }
            }

            override fun onAboutToWait(eventLoop: ActiveEventLoop) {
                if (!redrawRequested) {
                    trace += "aboutToWait:idle"
                    redrawRequested = true
                    window.requestRedraw()
                } else {
                    trace += "aboutToWait"
                    completedTrace.complete(trace.toList())
                }
            }
        }

        withActivity(handler) {
            assertEquals(
                listOf(
                    "aboutToWait:idle",
                    "newEvents:${StartCause.WaitCancelled()}",
                    "RedrawRequested",
                    "aboutToWait",
                ),
                await(completedTrace, handler),
            )
        }
    }

    @Test
    fun tenRedrawsBeforeFrameCoalesceAndReplacementUsesUniqueWindowIds() {
        val redraws = CopyOnWriteArrayList<WindowId>()
        val result = CompletableFuture<Pair<WindowId, WindowId>>()
        val handler = object : GuardedHandler() {
            private var replacedId: WindowId? = null
            private var currentId: WindowId? = null

            override fun onCanCreateSurfaces(eventLoop: ActiveEventLoop) {
                val replacedWindow = eventLoop.createWindow(WindowAttributes())
                replacedId = replacedWindow.id
                replacedWindow.requestRedraw()

                val currentWindow = eventLoop.createWindow(WindowAttributes())
                currentId = currentWindow.id
                repeat(10) {
                    currentWindow.requestRedraw()
                }
            }

            override fun onWindowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event is WindowEvent.RedrawRequested) {
                    redraws += windowId
                }
            }

            override fun onAboutToWait(eventLoop: ActiveEventLoop) {
                if (!result.isDone) {
                    result.complete(checkNotNull(replacedId) to checkNotNull(currentId))
                }
            }
        }

        withActivity(handler) {
            val (replacedId, currentId) = await(result, handler)
            assertNotEquals(replacedId, currentId)
            assertEquals(listOf(currentId), redraws)
        }
    }

    @Test
    fun threeBackgroundProxyWakesStartThreeDistinctWaitCancelledIterations() {
        val ready = CompletableFuture<EventLoopProxy>()
        val completedCycles = LinkedBlockingQueue<StartCause.WaitCancelled>()
        val trace = CopyOnWriteArrayList<String>()
        val handler = object : GuardedHandler() {
            private var pendingCause: StartCause.WaitCancelled? = null
            private var cycle = 0

            override fun onCanCreateSurfaces(eventLoop: ActiveEventLoop) {
                eventLoop.createWindow(WindowAttributes())
            }

            override fun onNewEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                if (startCause is StartCause.WaitCancelled) {
                    cycle += 1
                    pendingCause = startCause
                    trace += "newEvents#$cycle:$startCause"
                }
            }

            override fun onAboutToWait(eventLoop: ActiveEventLoop) {
                val cause = pendingCause
                if (cause != null) {
                    trace += "aboutToWait#$cycle"
                    pendingCause = null
                    completedCycles.offer(cause)
                } else if (!ready.isDone) {
                    ready.complete(eventLoop.createProxy())
                }
            }
        }

        withActivity(handler) {
            val proxy = await(ready, handler)
            val background = Executors.newSingleThreadExecutor()
            try {
                repeat(3) { index ->
                    background.submit {
                        check(Looper.myLooper() != Looper.getMainLooper())
                        proxy.wakeUp()
                    }.get(5, TimeUnit.SECONDS)

                    val cause = completedCycles.poll(5, TimeUnit.SECONDS)
                    handler.rethrowFailure()
                    assertEquals(StartCause.WaitCancelled(), cause, "wake cycle ${index + 1}")
                }
            } finally {
                background.shutdownNow()
            }

            assertEquals(
                listOf(
                    "newEvents#1:${StartCause.WaitCancelled()}",
                    "aboutToWait#1",
                    "newEvents#2:${StartCause.WaitCancelled()}",
                    "aboutToWait#2",
                    "newEvents#3:${StartCause.WaitCancelled()}",
                    "aboutToWait#3",
                ),
                trace,
            )
        }
    }

    @Test
    fun waitUntilOneHundredMillisecondsResumesAtOrAfterItsDeadline() {
        val requestedResume = AtomicLong(0L)
        val resumed = CompletableFuture<StartCause.ResumeTimeReached>()
        val handler = object : GuardedHandler() {
            override fun onCanCreateSurfaces(eventLoop: ActiveEventLoop) {
                eventLoop.createWindow(WindowAttributes())
            }

            override fun onNewEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                if (startCause is StartCause.ResumeTimeReached) {
                    resumed.complete(startCause)
                }
            }

            override fun onAboutToWait(eventLoop: ActiveEventLoop) {
                if (requestedResume.get() == 0L) {
                    val deadline = System.currentTimeMillis() + 100L
                    requestedResume.set(deadline)
                    eventLoop.setControlFlow(ControlFlow.WaitUntil(deadline))
                }
            }
        }

        withActivity(handler) {
            val cause = await(resumed, handler)
            assertEquals(requestedResume.get(), cause.requestedResume)
            assertTrue(cause.start >= cause.requestedResume)
        }
    }

    @Test
    fun redrawBeforeOneSecondDeadlineCancelsWaitAndInvalidatesDeadlineTimer() {
        val requestedResume = AtomicLong(0L)
        val armingRequested = AtomicBoolean(false)
        val schedulerReady = CompletableFuture<EventLoopProxy>()
        val armedWaitWindow = CompletableFuture<Window>()
        val waitCancelled = CompletableFuture<StartCause.WaitCancelled>()
        val orderedTrace = CompletableFuture<List<String>>()
        val staleDeadline = CountDownLatch(1)
        val trace = CopyOnWriteArrayList<String>()
        val handler = object : GuardedHandler() {
            private lateinit var window: Window
            private var armAfterIteration = false

            override fun onCanCreateSurfaces(eventLoop: ActiveEventLoop) {
                window = eventLoop.createWindow(WindowAttributes())
            }

            override fun onNewEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                if (
                    requestedResume.get() == 0L &&
                    armingRequested.get() &&
                    startCause is StartCause.WaitCancelled
                ) {
                    armAfterIteration = true
                    return
                }
                when (startCause) {
                    is StartCause.WaitCancelled -> {
                        if (startCause.requestedResume == requestedResume.get()) {
                            trace += "newEvents:$startCause"
                            eventLoop.setControlFlow(ControlFlow.Wait)
                            waitCancelled.complete(startCause)
                        }
                    }

                    is StartCause.ResumeTimeReached -> {
                        if (startCause.requestedResume == requestedResume.get()) {
                            staleDeadline.countDown()
                        }
                    }

                    else -> Unit
                }
            }

            override fun onWindowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event is WindowEvent.RedrawRequested && waitCancelled.isDone) {
                    trace += "RedrawRequested"
                }
            }

            override fun onAboutToWait(eventLoop: ActiveEventLoop) {
                if (requestedResume.get() == 0L) {
                    if (armAfterIteration) {
                        val deadline = System.currentTimeMillis() + 1_000L
                        requestedResume.set(deadline)
                        eventLoop.setControlFlow(ControlFlow.WaitUntil(deadline))
                        Handler(Looper.getMainLooper()).post {
                            armedWaitWindow.complete(window)
                        }
                    } else if (!schedulerReady.isDone) {
                        schedulerReady.complete(eventLoop.createProxy())
                    }
                } else if (waitCancelled.isDone && !orderedTrace.isDone) {
                    trace += "aboutToWait"
                    orderedTrace.complete(trace.toList())
                }
            }
        }

        withActivity(handler) {
            val proxy = await(schedulerReady, handler)
            armingRequested.set(true)
            proxy.wakeUp()
            val window = await(armedWaitWindow, handler)
            check(Looper.myLooper() != Looper.getMainLooper())
            window.requestRedraw()

            val cause = assertIs<StartCause.WaitCancelled>(await(waitCancelled, handler))
            assertEquals(requestedResume.get(), cause.requestedResume)
            assertEquals(
                listOf(
                    "newEvents:$cause",
                    "RedrawRequested",
                    "aboutToWait",
                ),
                await(orderedTrace, handler),
            )
            assertFalse(
                staleDeadline.await(1_200L, TimeUnit.MILLISECONDS),
                "cancelled WaitUntil timer fired after its generation was invalidated",
            )
            handler.rethrowFailure()
        }
    }

    @Test
    fun keyEventAfterArmedDeadlineCancelsWaitBeforeOrderedDelivery() {
        val requestedResume = AtomicLong(0L)
        val armingRequested = AtomicBoolean(false)
        val schedulerReady = CompletableFuture<EventLoopProxy>()
        val waitArmed = CompletableFuture<Unit>()
        val waitCancelled = CompletableFuture<StartCause.WaitCancelled>()
        val orderedTrace = CompletableFuture<List<String>>()
        val staleDeadline = CountDownLatch(1)
        val trace = CopyOnWriteArrayList<String>()
        val handler = object : GuardedHandler() {
            private var armAfterIteration = false

            override fun onCanCreateSurfaces(eventLoop: ActiveEventLoop) {
                eventLoop.createWindow(WindowAttributes())
            }

            override fun onNewEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                if (
                    requestedResume.get() == 0L &&
                    armingRequested.get() &&
                    startCause is StartCause.WaitCancelled
                ) {
                    armAfterIteration = true
                    return
                }
                when (startCause) {
                    is StartCause.WaitCancelled -> {
                        if (startCause.requestedResume == requestedResume.get()) {
                            trace += "newEvents:$startCause"
                            eventLoop.setControlFlow(ControlFlow.Wait)
                            waitCancelled.complete(startCause)
                        }
                    }

                    is StartCause.ResumeTimeReached -> {
                        if (startCause.requestedResume == requestedResume.get()) {
                            staleDeadline.countDown()
                        }
                    }

                    else -> Unit
                }
            }

            override fun onWindowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event is WindowEvent.KeyInput) {
                    trace += "KeyInput"
                }
            }

            override fun onAboutToWait(eventLoop: ActiveEventLoop) {
                if (requestedResume.get() == 0L) {
                    if (armAfterIteration) {
                        val deadline = System.currentTimeMillis() + 1_000L
                        requestedResume.set(deadline)
                        eventLoop.setControlFlow(ControlFlow.WaitUntil(deadline))
                        Handler(Looper.getMainLooper()).post {
                            waitArmed.complete(Unit)
                        }
                    } else if (!schedulerReady.isDone) {
                        schedulerReady.complete(eventLoop.createProxy())
                    }
                } else if (waitCancelled.isDone && !orderedTrace.isDone) {
                    trace += "aboutToWait"
                    orderedTrace.complete(trace.toList())
                }
            }
        }

        withActivityScenario(handler) { scenario ->
            val proxy = await(schedulerReady, handler)
            armingRequested.set(true)
            proxy.wakeUp()
            await(waitArmed, handler)
            scenario.onActivity { activity ->
                val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)
                assertTrue(activity.onKeyDown(KeyEvent.KEYCODE_A, event))
            }

            val cause = await(waitCancelled, handler)
            assertEquals(requestedResume.get(), cause.requestedResume)
            assertEquals(
                listOf(
                    "newEvents:$cause",
                    "KeyInput",
                    "aboutToWait",
                ),
                await(orderedTrace, handler),
            )
            assertFalse(
                staleDeadline.await(1_200L, TimeUnit.MILLISECONDS),
                "key-cancelled WaitUntil timer fired after its generation was invalidated",
            )
            handler.rethrowFailure()
        }
    }

    @Test
    fun twoWakesQueuedBeforeInitDoNotCreateAnEmptyIterationAfterInit() {
        val initComplete = CompletableFuture<Unit>()
        val unexpectedEmptyIteration = CountDownLatch(1)
        val trace = CopyOnWriteArrayList<String>()
        val handler = object : GuardedHandler() {
            private var currentCause: StartCause? = null
            private var currentWindowEventCount = 0

            override fun onCanCreateSurfaces(eventLoop: ActiveEventLoop) {
                eventLoop.createWindow(WindowAttributes())
                val proxy = eventLoop.createProxy()
                val wakesPosted = CountDownLatch(1)
                val background = Executors.newSingleThreadExecutor()
                try {
                    background.execute {
                        check(Looper.myLooper() != Looper.getMainLooper())
                        proxy.wakeUp()
                        proxy.wakeUp()
                        wakesPosted.countDown()
                    }
                    check(wakesPosted.await(5, TimeUnit.SECONDS))
                } finally {
                    background.shutdownNow()
                }
            }

            override fun onNewEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                currentCause = startCause
                currentWindowEventCount = 0
                if (startCause == StartCause.Init) {
                    trace += "newEvents:Init"
                }
            }

            override fun onWindowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                currentWindowEventCount += 1
            }

            override fun onAboutToWait(eventLoop: ActiveEventLoop) {
                when (currentCause) {
                    StartCause.Init -> {
                        trace += "aboutToWait:Init"
                        initComplete.complete(Unit)
                    }

                    is StartCause.WaitCancelled -> {
                        if (currentWindowEventCount == 0) {
                            unexpectedEmptyIteration.countDown()
                        }
                    }

                    else -> Unit
                }
                currentCause = null
            }
        }

        withActivity(handler) {
            await(initComplete, handler)
            assertFalse(
                unexpectedEmptyIteration.await(750L, TimeUnit.MILLISECONDS),
                "two wakes queued before Init produced a later empty WaitCancelled iteration",
            )
            assertEquals(listOf("newEvents:Init", "aboutToWait:Init"), trace)
            handler.rethrowFailure()
        }
    }

    private fun withActivity(handler: GuardedHandler, assertions: () -> Unit) {
        withActivityScenario(handler) {
            assertions()
        }
    }

    private fun withActivityScenario(
        handler: GuardedHandler,
        assertions: (ActivityScenario<SurfaceLifecycleTestActivity>) -> Unit,
    ) {
        SurfaceLifecycleTestActivity.handlerFactory = { handler }
        val scenario = ActivityScenario.launch(SurfaceLifecycleTestActivity::class.java)
        try {
            assertions(scenario)
            handler.rethrowFailure()
        } finally {
            scenario.close()
            SurfaceLifecycleTestActivity.handlerFactory = null
        }
    }

    private fun <T> await(result: CompletableFuture<T>, handler: GuardedHandler): T {
        return try {
            result.get(5, TimeUnit.SECONDS)
        } catch (failure: Throwable) {
            handler.rethrowFailure()
            throw failure
        }
    }

    private abstract class GuardedHandler : ApplicationHandler {
        private val callbackFailure = CompletableFuture<Throwable>()

        final override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
            guard { onCanCreateSurfaces(eventLoop) }
        }

        final override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
            guard { onNewEvents(eventLoop, startCause) }
        }

        final override fun windowEvent(
            eventLoop: ActiveEventLoop,
            windowId: WindowId,
            event: WindowEvent,
        ) {
            guard { onWindowEvent(eventLoop, windowId, event) }
        }

        final override fun aboutToWait(eventLoop: ActiveEventLoop) {
            guard { onAboutToWait(eventLoop) }
        }

        protected open fun onCanCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

        protected open fun onNewEvents(eventLoop: ActiveEventLoop, startCause: StartCause) = Unit

        protected open fun onWindowEvent(
            eventLoop: ActiveEventLoop,
            windowId: WindowId,
            event: WindowEvent,
        ) = Unit

        protected open fun onAboutToWait(eventLoop: ActiveEventLoop) = Unit

        fun rethrowFailure() {
            if (callbackFailure.isDone) {
                throw callbackFailure.get(1, TimeUnit.SECONDS)
            }
        }

        private fun guard(callback: () -> Unit) {
            if (callbackFailure.isDone) return
            try {
                callback()
            } catch (failure: Throwable) {
                callbackFailure.complete(failure)
            }
        }
    }
}
