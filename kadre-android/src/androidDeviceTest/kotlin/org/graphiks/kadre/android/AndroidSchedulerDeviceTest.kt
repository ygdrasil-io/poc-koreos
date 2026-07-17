package org.graphiks.kadre.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.EventLoopProxy
import org.graphiks.kadre.core.RawWindowHandle
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.Window
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidSchedulerDeviceTest {
    @Test
    fun availableMonitorReportsDefaultDisplayRefreshRate() {
        val ready = CompletableFuture<ActiveEventLoop>()
        val handler = object : GuardedHandler() {
            override fun onCanCreateSurfaces(eventLoop: ActiveEventLoop) {
                eventLoop.createWindow(WindowAttributes())
                ready.complete(eventLoop)
            }
        }

        withActivityScenario(handler) { scenario ->
            val eventLoop = await(ready, handler)
            scenario.onActivity { activity ->
                @Suppress("DEPRECATION")
                val display = (activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                    .defaultDisplay
                val monitor = eventLoop.availableMonitors().single()
                assertEquals(
                    refreshRateMillihertz(display.refreshRate),
                    checkNotNull(monitor.currentVideoMode).refreshRateMilliHz,
                )
            }
        }
    }

    @Test
    fun eventQueuedFromNewEventsIsDeliveredOnlyInNextIterationWithoutEmptyWake() {
        val ready = CompletableFuture<EventLoopProxy>()
        val stabilizationComplete = CompletableFuture<Unit>()
        val completedTrace = CompletableFuture<List<String>>()
        val unexpectedIteration = CountDownLatch(1)
        val trace = CopyOnWriteArrayList<String>()
        val queuedEvent = WindowEvent.Focused(true)
        val exerciseRequested = AtomicBoolean(false)
        val handler = object : GuardedHandler() {
            private var windowId: WindowId? = null
            private var waitCancelledIteration = 0
            private var stabilizationIteration = false
            private var currentWindowEventCount = 0

            override fun onCanCreateSurfaces(eventLoop: ActiveEventLoop) {
                windowId = eventLoop.createWindow(WindowAttributes()).id
            }

            override fun onNewEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                if (startCause !is StartCause.WaitCancelled) return
                if (!exerciseRequested.get()) {
                    stabilizationIteration = true
                    return
                }

                waitCancelledIteration += 1
                currentWindowEventCount = 0
                trace += "newEvents#$waitCancelledIteration"
                when (waitCancelledIteration) {
                    1 -> (eventLoop as AndroidEventLoop).queueWindowEvent(
                        checkNotNull(windowId),
                        queuedEvent,
                    )
                    2 -> Unit
                    else -> Unit
                }
            }

            override fun onWindowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (waitCancelledIteration > 0) {
                    currentWindowEventCount += 1
                }
                if (event === queuedEvent) {
                    trace += "Focused#$waitCancelledIteration"
                }
            }

            override fun onAboutToWait(eventLoop: ActiveEventLoop) {
                if (stabilizationIteration && !exerciseRequested.get()) {
                    stabilizationIteration = false
                    stabilizationComplete.complete(Unit)
                    return
                }
                when (waitCancelledIteration) {
                    0 -> ready.complete(eventLoop.createProxy())
                    1 -> trace += "aboutToWait#1"
                    2 -> {
                        trace += "aboutToWait#2"
                        completedTrace.complete(trace.toList())
                    }

                    else -> {
                        if (currentWindowEventCount == 0) {
                            unexpectedIteration.countDown()
                        }
                    }
                }
            }
        }

        withActivity(handler) {
            val proxy = await(ready, handler)
            proxy.wakeUp()
            await(stabilizationComplete, handler)
            exerciseRequested.set(true)
            proxy.wakeUp()
            assertEquals(
                listOf(
                    "newEvents#1",
                    "aboutToWait#1",
                    "newEvents#2",
                    "Focused#2",
                    "aboutToWait#2",
                ),
                await(completedTrace, handler),
            )
            assertFalse(
                unexpectedIteration.await(750L, TimeUnit.MILLISECONDS),
                "queued event produced an additional empty WaitCancelled iteration",
            )
            handler.rethrowFailure()
        }
    }

    @Test
    fun backgroundCreateWindowCompletesAndReplacesWindowWithDistinctId() {
        val ready = CompletableFuture<Pair<ActiveEventLoop, Window>>()
        val handler = object : GuardedHandler() {
            override fun onCanCreateSurfaces(eventLoop: ActiveEventLoop) {
                ready.complete(eventLoop to eventLoop.createWindow(WindowAttributes()))
            }
        }

        withActivity(handler) {
            val (eventLoop, initialWindow) = await(ready, handler)
            val background = Executors.newSingleThreadExecutor()
            val replacementWindow = try {
                background.submit<Window> {
                    check(Looper.myLooper() != Looper.getMainLooper())
                    eventLoop.createWindow(WindowAttributes())
                }.get(10L, TimeUnit.SECONDS)
            } finally {
                background.shutdownNow()
            }

            assertNotEquals(initialWindow.id, replacementWindow.id)
            assertFailsWith<IllegalStateException> {
                initialWindow.rawWindowHandle
            }
            assertIs<RawWindowHandle.Android>(replacementWindow.rawWindowHandle)
            handler.rethrowFailure()
        }
    }

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

    @Test
    fun closeIsTerminalIdempotentAndPurgesQueuedWindowWork() {
        val closeSnapshot = CompletableFuture<Pair<Boolean, Int>>()
        val postCloseEvents = CopyOnWriteArrayList<WindowEvent>()
        val nonTerminalEvent = CountDownLatch(1)
        lateinit var closedWindow: Window
        lateinit var androidEventLoop: AndroidEventLoop
        val handler = object : GuardedHandler() {
            private var closeStarted = false

            override fun onCanCreateSurfaces(eventLoop: ActiveEventLoop) {
                androidEventLoop = eventLoop as AndroidEventLoop
                closedWindow = eventLoop.createWindow(WindowAttributes())
            }

            override fun onWindowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (!closeStarted || windowId != closedWindow.id) return
                postCloseEvents += event
                if (
                    event is WindowEvent.RedrawRequested ||
                    event is WindowEvent.Focused ||
                    event is WindowEvent.Occluded
                ) {
                    nonTerminalEvent.countDown()
                }
            }

            override fun onAboutToWait(eventLoop: ActiveEventLoop) {
                if (closeStarted) return
                closeStarted = true

                androidEventLoop.queueWindowEvent(closedWindow.id, WindowEvent.Focused(false))
                androidEventLoop.queueWindowEvent(closedWindow.id, WindowEvent.Occluded(true))
                closedWindow.requestRedraw()
                closedWindow.close()
                closedWindow.close()
                closedWindow.requestRedraw()

                val rawHandleInvalid = runCatching { closedWindow.rawWindowHandle }.isFailure
                closeSnapshot.complete(rawHandleInvalid to registeredWindowCount(androidEventLoop))
            }
        }

        SurfaceLifecycleTestActivity.handlerFactory = { handler }
        val scenario = ActivityScenario.launch(SurfaceLifecycleTestActivity::class.java)
        try {
            val (rawHandleInvalid, registeredWindowCount) = await(closeSnapshot, handler)
            assertTrue(rawHandleInvalid, "close must immediately invalidate the raw window handle")
            assertEquals(0, registeredWindowCount, "close must remove the window from the registry")
            assertFalse(
                nonTerminalEvent.await(750L, TimeUnit.MILLISECONDS),
                "redraw, focus, or occlusion was delivered after close",
            )

            scenario.close()
            handler.rethrowFailure()
            assertEquals(
                1,
                postCloseEvents.count { it is WindowEvent.Destroyed },
                "close and Activity destruction must emit exactly one Destroyed",
            )
            assertTrue(
                postCloseEvents.all { it is WindowEvent.Destroyed },
                "only Destroyed may be delivered after close: $postCloseEvents",
            )
        } finally {
            scenario.close()
            SurfaceLifecycleTestActivity.handlerFactory = null
        }
    }

    @Test
    fun concurrentBackgroundCloseCallersWaitForTerminalCompletion() {
        val ready = CompletableFuture<Window>()
        val closeEntered = CountDownLatch(1)
        val releaseClose = CountDownLatch(1)
        val secondReturned = CountDownLatch(1)
        val handler = object : GuardedHandler() {
            override fun onCanCreateSurfaces(eventLoop: ActiveEventLoop) {
                ready.complete(eventLoop.createWindow(WindowAttributes()))
            }

            override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                closeEntered.countDown()
                check(releaseClose.await(5L, TimeUnit.SECONDS))
            }
        }

        withActivity(handler) {
            val window = await(ready, handler)
            val executor = Executors.newFixedThreadPool(2)
            try {
                val first = executor.submit { window.close() }
                assertTrue(closeEntered.await(5L, TimeUnit.SECONDS))
                val second = executor.submit {
                    window.close()
                    secondReturned.countDown()
                }

                assertFalse(
                    secondReturned.await(750L, TimeUnit.MILLISECONDS),
                    "the second close returned while the first close was still in destroySurfaces",
                )
                releaseClose.countDown()
                first.get(5L, TimeUnit.SECONDS)
                second.get(5L, TimeUnit.SECONDS)
                assertFailsWith<IllegalStateException> { window.rawWindowHandle }
                handler.rethrowFailure()
            } finally {
                releaseClose.countDown()
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun createWindowIsRejectedDuringAndAfterTerminalClose() {
        val ready = CompletableFuture<Pair<ActiveEventLoop, Window>>()
        val rejectedAt = CopyOnWriteArrayList<String>()
        val handler = object : GuardedHandler() {
            override fun onCanCreateSurfaces(eventLoop: ActiveEventLoop) {
                ready.complete(eventLoop to eventLoop.createWindow(WindowAttributes()))
            }

            override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                val failure = runCatching { eventLoop.createWindow(WindowAttributes()) }.exceptionOrNull()
                if (failure is IllegalStateException) rejectedAt += "destroySurfaces"
            }

            override fun onWindowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event is WindowEvent.Destroyed) {
                    val failure = runCatching {
                        eventLoop.createWindow(WindowAttributes())
                    }.exceptionOrNull()
                    if (failure is IllegalStateException) rejectedAt += "Destroyed"
                }
            }
        }

        withActivity(handler) {
            val (eventLoop, window) = await(ready, handler)
            window.close()
            val afterCloseFailure = runCatching {
                eventLoop.createWindow(WindowAttributes())
            }.exceptionOrNull()
            if (afterCloseFailure is IllegalStateException) rejectedAt += "afterClose"

            assertEquals(
                setOf("destroySurfaces", "Destroyed", "afterClose"),
                rejectedAt.toSet(),
            )
            assertEquals(3, rejectedAt.size)
            handler.rethrowFailure()
        }
    }

    @Test
    fun destroySurfacesFailureStillInvalidatesDispatchesDestroyedAndFinishes() {
        val ready = CompletableFuture<Pair<AndroidEventLoop, Window>>()
        val expected = IllegalStateException("destroySurfaces failed")
        val throwOnce = AtomicBoolean(true)
        val destroyedCount = AtomicInteger(0)
        val handler = object : GuardedHandler() {
            override fun onCanCreateSurfaces(eventLoop: ActiveEventLoop) {
                ready.complete(eventLoop as AndroidEventLoop to eventLoop.createWindow(WindowAttributes()))
            }

            override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                if (throwOnce.compareAndSet(true, false)) throw expected
            }

            override fun onWindowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event is WindowEvent.Destroyed) destroyedCount.incrementAndGet()
            }
        }

        withActivity(handler) {
            val (eventLoop, window) = await(ready, handler)
            val actual = assertFailsWith<IllegalStateException> { window.close() }
            assertSame(expected, actual)
            assertFailsWith<IllegalStateException> { window.rawWindowHandle }
            assertEquals(0, registeredWindowCount(eventLoop))
            assertEquals(1, destroyedCount.get())
            assertTrue(eventLoop.isExiting)
        }
    }

    @Test
    fun closeFromNewEventsStopsIterationAtDestroyed() {
        val ready = CompletableFuture<EventLoopProxy>()
        val destroyed = CompletableFuture<Unit>()
        val unexpectedCallback = CountDownLatch(1)
        val exercise = AtomicBoolean(false)
        val trace = CopyOnWriteArrayList<String>()
        val handler = object : GuardedHandler() {
            private lateinit var window: Window

            override fun onCanCreateSurfaces(eventLoop: ActiveEventLoop) {
                window = eventLoop.createWindow(WindowAttributes())
            }

            override fun onNewEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                if (!exercise.get()) return
                if (destroyed.isDone) {
                    unexpectedCallback.countDown()
                    return
                }
                trace += "newEvents"
                window.close()
            }

            override fun onWindowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event is WindowEvent.Destroyed) {
                    trace += "Destroyed"
                    destroyed.complete(Unit)
                } else if (destroyed.isDone) {
                    unexpectedCallback.countDown()
                }
            }

            override fun onAboutToWait(eventLoop: ActiveEventLoop) {
                if (!exercise.get()) {
                    ready.complete(eventLoop.createProxy())
                } else if (destroyed.isDone) {
                    trace += "aboutToWait"
                    unexpectedCallback.countDown()
                }
            }
        }

        withActivity(handler) {
            val proxy = await(ready, handler)
            exercise.set(true)
            proxy.wakeUp()
            await(destroyed, handler)
            assertFalse(
                unexpectedCallback.await(750L, TimeUnit.MILLISECONDS),
                "an event-loop callback followed Destroyed after close from newEvents",
            )
            assertEquals(listOf("newEvents", "Destroyed"), trace)
            handler.rethrowFailure()
        }
    }

    @Test
    fun closeFromWindowEventStopsIterationAtDestroyed() {
        val ready = CompletableFuture<Pair<AndroidEventLoop, Window>>()
        val destroyed = CompletableFuture<Unit>()
        val unexpectedCallback = CountDownLatch(1)
        val trace = CopyOnWriteArrayList<String>()
        val handler = object : GuardedHandler() {
            private lateinit var window: Window

            override fun onCanCreateSurfaces(eventLoop: ActiveEventLoop) {
                window = eventLoop.createWindow(WindowAttributes())
            }

            override fun onNewEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                if (destroyed.isDone) unexpectedCallback.countDown()
            }

            override fun onWindowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                when {
                    event is WindowEvent.Focused -> {
                        trace += "Focused"
                        window.close()
                    }
                    event is WindowEvent.Destroyed -> {
                        trace += "Destroyed"
                        destroyed.complete(Unit)
                    }
                    destroyed.isDone -> unexpectedCallback.countDown()
                }
            }

            override fun onAboutToWait(eventLoop: ActiveEventLoop) {
                if (!ready.isDone) {
                    ready.complete(eventLoop as AndroidEventLoop to window)
                } else if (destroyed.isDone) {
                    trace += "aboutToWait"
                    unexpectedCallback.countDown()
                }
            }
        }

        withActivity(handler) {
            val (eventLoop, window) = await(ready, handler)
            eventLoop.queueWindowEvent(window.id, WindowEvent.Focused(false))
            await(destroyed, handler)
            assertFalse(
                unexpectedCallback.await(750L, TimeUnit.MILLISECONDS),
                "an event-loop callback followed Destroyed after close from windowEvent",
            )
            assertEquals(listOf("Focused", "Destroyed"), trace)
            handler.rethrowFailure()
        }
    }

    @Test
    fun closePurgesArmedTimerPendingProxyWakeAndAllSchedulerCallbacks() {
        val waitArmed = CompletableFuture<Triple<AndroidEventLoop, Window, EventLoopProxy>>()
        val destroyed = CompletableFuture<Unit>()
        val unexpectedCallback = CountDownLatch(1)
        val trace = CopyOnWriteArrayList<String>()
        val handler = object : GuardedHandler() {
            private lateinit var eventLoop: AndroidEventLoop
            private lateinit var window: Window
            private var waitRequested = false

            override fun onCanCreateSurfaces(eventLoop: ActiveEventLoop) {
                this.eventLoop = eventLoop as AndroidEventLoop
                window = eventLoop.createWindow(WindowAttributes())
            }

            override fun onNewEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                if (destroyed.isDone) {
                    trace += "newEvents:$startCause"
                    unexpectedCallback.countDown()
                }
            }

            override fun onWindowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event is WindowEvent.Destroyed) {
                    trace += "Destroyed"
                    destroyed.complete(Unit)
                } else if (destroyed.isDone) {
                    trace += event::class.java.simpleName
                    unexpectedCallback.countDown()
                }
            }

            override fun onAboutToWait(eventLoop: ActiveEventLoop) {
                if (!waitRequested) {
                    waitRequested = true
                    eventLoop.setControlFlow(
                        ControlFlow.WaitUntil(System.currentTimeMillis() + 400L),
                    )
                    Handler(Looper.getMainLooper()).post {
                        waitArmed.complete(
                            Triple(this.eventLoop, window, eventLoop.createProxy()),
                        )
                    }
                } else if (destroyed.isDone) {
                    trace += "aboutToWait"
                    unexpectedCallback.countDown()
                }
            }
        }

        withActivityScenario(handler) { scenario ->
            val (eventLoop, window, proxy) = await(waitArmed, handler)
            scenario.onActivity {
                assertTrue(
                    eventLoop.schedulerDiagnostics().hasArmedWait,
                    "WaitUntil timer was not armed",
                )
                val background = Executors.newSingleThreadExecutor()
                try {
                    background.submit { proxy.wakeUp() }.get(5L, TimeUnit.SECONDS)
                    assertTrue(
                        eventLoop.schedulerDiagnostics().hasPendingProxyWake,
                        "proxy wake was not pending",
                    )
                    window.close()
                } finally {
                    background.shutdownNow()
                }

                assertNull(eventLoop.pendingWindow)
                assertEquals(0, registeredWindowCount(eventLoop))
                assertFalse(eventLoop.schedulerDiagnostics().hasArmedWait)
                assertFalse(eventLoop.schedulerDiagnostics().hasPendingProxyWake)
            }

            await(destroyed, handler)
            assertFalse(
                unexpectedCallback.await(750L, TimeUnit.MILLISECONDS),
                "a timer, proxy wake, or scheduler callback survived terminal close: $trace",
            )
            assertEquals(listOf("Destroyed"), trace)
            handler.rethrowFailure()
        }
    }

    private fun registeredWindowCount(eventLoop: AndroidEventLoop): Int {
        return eventLoop.schedulerDiagnostics().registeredWindowCount
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
