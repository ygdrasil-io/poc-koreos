package org.graphiks.kadre.appkit

import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import java.lang.foreign.MemorySegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppKitRegistryLifecycleTest {

    @Test
    fun `terminal window close removes redraw state`() {
        val eventLoop = AppKitEventLoop(NoopHandler)
        val state = AppKitLoopState { 0L }
        val owner = CFRunLoopOwner.install(
            api = WakeRecordingCFRunLoopApi(),
            state = state,
            onAfterWaiting = {},
            onBeforeWaiting = { org.graphiks.kadre.core.ControlFlow.Wait },
        )
        eventLoop.installRunLoopOwner(owner)
        val windowId = WindowId(0x8A9BACL)
        state.requestRedraw(windowId)
        eventLoop.registerWindowCloseActions(windowId, {}, {})
        try {
            eventLoop.closeWindow(windowId)

            assertEquals(emptyList(), state.takeRedraws())
            assertFalse(state.requestRedraw(windowId))
        } finally {
            eventLoop.clearRunLoopOwner(owner)
            owner.close()
        }
    }

    @Test
    fun `runApp rethrows callback failure only after complete cleanup`() {
        val trace = mutableListOf<String>()
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                trace += "destroySurfaces"
                error("destroy boom")
            }

            override fun suspended(eventLoop: ActiveEventLoop) {
                trace += "suspended"
            }

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event === WindowEvent.Destroyed) trace += "Destroyed"
            }
        }

        val failure = assertFailsWith<IllegalStateException> {
            runApp(handler, operationsFactory = { eventLoop ->
                FakeRunAppOperations(
                    eventLoop = eventLoop,
                    appDelegate = MemorySegment.ofAddress(0x8101L),
                    windowDelegate = MemorySegment.ofAddress(0x8201L),
                    imeView = MemorySegment.ofAddress(0x8301L),
                    trace = trace,
                    run = 9,
                )
            })
        }

        assertTrue(failure.message.orEmpty().contains("applicationWillTerminate"))
        assertEquals("destroy boom", failure.cause?.message)
        assertTrue(trace.indexOf("destroySurfaces") < trace.indexOf("run9:unregisterWindowCallbacks"))
        assertTrue(trace.indexOf("run9:unregisterWindowCallbacks") < trace.indexOf("run9:closeOwner"))
        assertEquals("run9:clearReferences", trace.last())
        assertEquals(0, KadreAppDelegate.registeredDelegateCount())
        assertEquals(0, KadreWindowDelegate.registeredDelegateCount())
        assertEquals(0, AppKitImeTextInputClient.registeredViewCount())
        assertFalse(appKitRunning.get())
    }

    @Test
    fun `IME callback exceptions are queued wake the loop and rethrow with context`() {
        val eventLoop = AppKitEventLoop(NoopHandler)
        val api = WakeRecordingCFRunLoopApi()
        val owner = CFRunLoopOwner.install(
            api = api,
            state = AppKitLoopState { 0L },
            onAfterWaiting = {},
            onBeforeWaiting = { org.graphiks.kadre.core.ControlFlow.Wait },
        )
        eventLoop.installRunLoopOwner(owner)
        val view = MemorySegment.ofAddress(0x7A8B9CL)
        val throwingHandler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                error("IME boom")
            }
        }
        AppKitImeTextInputClient.registerView(
            view,
            ImeViewRecord(throwingHandler, eventLoop, WindowId(view.address()), MemorySegment.NULL),
        )
        try {
            AppKitImeTextInputClient.Callbacks.unmarkText(view, MemorySegment.NULL)

            assertEquals(1, api.wakeCount)
            val failure = assertFailsWith<IllegalStateException> {
                eventLoop.throwPendingCallbackFailure()
            }
            assertTrue(failure.message.orEmpty().contains("unmarkText"))
            assertEquals("IME boom", failure.cause?.message)
        } finally {
            AppKitImeTextInputClient.unregisterView(view)
            eventLoop.clearRunLoopOwner(owner)
            owner.close()
        }
    }

    @Test
    fun `window delegate callback exceptions are queued wake the loop and rethrow with context`() {
        val eventLoop = AppKitEventLoop(NoopHandler)
        val api = WakeRecordingCFRunLoopApi()
        val owner = CFRunLoopOwner.install(
            api = api,
            state = AppKitLoopState { 0L },
            onAfterWaiting = {},
            onBeforeWaiting = { org.graphiks.kadre.core.ControlFlow.Wait },
        )
        eventLoop.installRunLoopOwner(owner)
        val self = MemorySegment.ofAddress(0x6A7B8CL)
        KadreWindowDelegate.registerDelegateRoute(
            self.address(),
            object : AppKitWindowDelegateCallbacks {
                override fun onWindowWillClose() {
                    error("delegate boom")
                }

                override fun captureCallbackFailure(context: String, failure: Throwable) {
                    eventLoop.recordCallbackFailure(context, failure)
                }
            },
        )
        try {
            KadreWindowDelegate.Callbacks.windowWillClose(
                self,
                MemorySegment.NULL,
                MemorySegment.NULL,
            )

            assertEquals(1, api.wakeCount)
            val failure = assertFailsWith<IllegalStateException> {
                eventLoop.throwPendingCallbackFailure()
            }
            assertTrue(failure.message.orEmpty().contains("windowWillClose"))
            assertEquals("delegate boom", failure.cause?.message)
        } finally {
            KadreWindowDelegate.unregisterDelegate(self.address())
            eventLoop.clearRunLoopOwner(owner)
            owner.close()
        }
    }

    @Test
    fun `two complete fake runApp calls leave empty tables and ignore stale callbacks`() {
        val allEvents = mutableListOf<String>()
        var staleDelegate = MemorySegment.NULL
        var staleWindowDelegate = MemorySegment.NULL
        var staleImeView = MemorySegment.NULL

        repeat(2) { index ->
            val run = index + 1
            val handler = object : ApplicationHandler {
                override fun resumed(eventLoop: ActiveEventLoop) {
                    allEvents += "run$run:resumed"
                }

                override fun newEvents(
                    eventLoop: ActiveEventLoop,
                    startCause: org.graphiks.kadre.core.StartCause,
                ) {
                    allEvents += "run$run:newEvents"
                }

                override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
                    allEvents += "run$run:canCreateSurfaces"
                }

                override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                    allEvents += "run$run:destroySurfaces"
                }

                override fun suspended(eventLoop: ActiveEventLoop) {
                    allEvents += "run$run:suspended"
                }

                override fun windowEvent(
                    eventLoop: ActiveEventLoop,
                    windowId: WindowId,
                    event: WindowEvent,
                ) {
                    if (event === WindowEvent.Destroyed) allEvents += "run$run:Destroyed"
                }
            }
            val appDelegate = MemorySegment.ofAddress(0x1000L + run)
            val windowDelegate = MemorySegment.ofAddress(0x2000L + run)
            val imeView = MemorySegment.ofAddress(0x3000L + run)

            runApp(handler, operationsFactory = { eventLoop ->
                FakeRunAppOperations(
                    eventLoop = eventLoop,
                    appDelegate = appDelegate,
                    windowDelegate = windowDelegate,
                    imeView = imeView,
                    trace = allEvents,
                    run = run,
                )
            })

            assertEquals(0, KadreAppDelegate.registeredDelegateCount())
            assertEquals(0, KadreWindowDelegate.registeredDelegateCount())
            assertEquals(0, AppKitImeTextInputClient.registeredViewCount())
            assertFalse(appKitRunning.get())

            if (index == 0) {
                staleDelegate = appDelegate
                staleWindowDelegate = windowDelegate
                staleImeView = imeView
            } else {
                val beforeStaleCallbacks = allEvents.toList()
                KadreAppDelegate.Callbacks.applicationDidBecomeActive(
                    staleDelegate,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                )
                KadreWindowDelegate.Callbacks.windowWillClose(
                    staleWindowDelegate,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                )
                AppKitImeTextInputClient.Callbacks.unmarkText(staleImeView, MemorySegment.NULL)
                assertEquals(beforeStaleCallbacks, allEvents)
            }
        }

        assertEquals(
            listOf(
                "run1:initialize", "run1:attachDelegate", "run1:installOwner", "run1:run",
                "run1:resumed", "run1:newEvents", "run1:canCreateSurfaces",
                "run1:destroySurfaces", "run1:unregisterWindowCallbacks",
                "run1:Destroyed", "run1:nativeClose", "run1:suspended",
                "run1:closeOwner", "run1:detachDelegate", "run1:releaseDelegate", "run1:clearReferences",
                "run2:initialize", "run2:attachDelegate", "run2:installOwner", "run2:run",
                "run2:resumed", "run2:newEvents", "run2:canCreateSurfaces",
                "run2:destroySurfaces", "run2:unregisterWindowCallbacks",
                "run2:Destroyed", "run2:nativeClose", "run2:suspended",
                "run2:closeOwner", "run2:detachDelegate", "run2:releaseDelegate", "run2:clearReferences",
            ),
            allEvents,
        )
    }

    @Test
    fun `window callback cleanup detaches unregisters delegate and IME then releases`() {
        val trace = mutableListOf<String>()
        val delegateAddress = 0x5A6B7CL
        val view = MemorySegment.ofAddress(delegateAddress + 1)
        val eventLoop = AppKitEventLoop(NoopHandler)
        KadreWindowDelegate.registerDelegateRoute(delegateAddress, object : AppKitWindowDelegateCallbacks {})
        AppKitImeTextInputClient.registerView(
            view,
            ImeViewRecord(NoopHandler, eventLoop, WindowId(delegateAddress), MemorySegment.NULL),
        )

        appKitUnregisterWindowCallbacks(
            delegateAddress = delegateAddress,
            textInputView = view,
            detachNativeDelegate = { trace += "detachDelegate" },
            releaseDelegate = {
                assertEquals(0, KadreWindowDelegate.registeredDelegateCount())
                assertEquals(0, AppKitImeTextInputClient.registeredViewCount())
                trace += "releaseDelegate"
            },
        )

        assertEquals(listOf("detachDelegate", "releaseDelegate"), trace)
        assertEquals(0, KadreWindowDelegate.registeredDelegateCount())
        assertEquals(0, AppKitImeTextInputClient.registeredViewCount())
    }

    @Test
    fun `AppKit window close routes through event-loop terminal cleanup`() {
        val trace = mutableListOf<String>()
        val windowId = WindowId(0x4A5B6CL)
        val eventLoop = AppKitEventLoop(object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event === WindowEvent.Destroyed) trace += "Destroyed"
            }
        })
        eventLoop.registerWindowCloseActions(
            windowId,
            unregisterCallbacks = { trace += "unregister" },
            closeNative = { trace += "nativeClose" },
        )

        appKitCloseWindow(eventLoop, windowId) { trace += "directNativeClose" }

        assertEquals(listOf("unregister", "Destroyed", "nativeClose"), trace)
    }

    @Test
    fun `window close unregisters every route before native close and is terminal`() {
        val trace = mutableListOf<String>()
        val windowId = WindowId(0x3A4B5CL)
        val delegateAddress = windowId.value + 1
        val view = MemorySegment.ofAddress(windowId.value + 2)
        val eventLoop = AppKitEventLoop(object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event === WindowEvent.Destroyed) trace += "Destroyed"
            }
        })
        KadreWindowDelegate.registerDelegateRoute(delegateAddress, object : AppKitWindowDelegateCallbacks {})
        AppKitImeTextInputClient.registerView(
            view,
            ImeViewRecord(NoopHandler, eventLoop, windowId, MemorySegment.NULL),
        )
        eventLoop.registerWindowCloseActions(
            windowId = windowId,
            unregisterCallbacks = {
                KadreWindowDelegate.unregisterDelegate(delegateAddress)
                AppKitImeTextInputClient.unregisterView(view)
                trace += "unregisterCallbacks"
            },
            closeNative = {
                assertFalse(eventLoop.hasRegisteredWindow(windowId))
                assertEquals(0, KadreWindowDelegate.registeredDelegateCount())
                assertEquals(0, AppKitImeTextInputClient.registeredViewCount())
                trace += "nativeClose"
            },
        )

        eventLoop.closeWindow(windowId)
        eventLoop.closeWindow(windowId)

        assertEquals(listOf("unregisterCallbacks", "Destroyed", "nativeClose"), trace)
    }

    @Test
    fun `unregistered window delegate ignores stale native callbacks`() {
        val address = 0x2A3B4CL
        val self = MemorySegment.ofAddress(address)
        var callbackCount = 0
        val route = object : AppKitWindowDelegateCallbacks {
            override fun onWindowWillClose() {
                callbackCount++
            }
        }

        KadreWindowDelegate.registerDelegateRoute(address, route)
        assertEquals(1, KadreWindowDelegate.registeredDelegateCount())

        KadreWindowDelegate.unregisterDelegate(address)
        KadreWindowDelegate.Callbacks.windowWillClose(
            self,
            MemorySegment.NULL,
            MemorySegment.NULL,
        )

        assertEquals(0, KadreWindowDelegate.registeredDelegateCount())
        assertEquals(0, callbackCount)
    }

    @Test
    fun `unregistered IME view ignores stale native callbacks`() {
        val address = 0x1A2B3CL
        val view = MemorySegment.ofAddress(address)
        var callbackCount = 0
        val eventLoop = AppKitEventLoop(NoopHandler)
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                callbackCount++
            }
        }

        AppKitImeTextInputClient.registerView(
            view,
            ImeViewRecord(handler, eventLoop, WindowId(address), MemorySegment.NULL),
        )
        assertEquals(1, AppKitImeTextInputClient.registeredViewCount())

        AppKitImeTextInputClient.unregisterView(view)
        AppKitImeTextInputClient.Callbacks.unmarkText(view, MemorySegment.NULL)

        assertEquals(0, AppKitImeTextInputClient.registeredViewCount())
        assertEquals(0, callbackCount)
    }

    private object NoopHandler : ApplicationHandler {
        override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

        override fun windowEvent(
            eventLoop: ActiveEventLoop,
            windowId: WindowId,
            event: WindowEvent,
        ) = Unit
    }

    private class FakeRunAppOperations(
        private val eventLoop: AppKitEventLoop,
        private val appDelegate: MemorySegment,
        private val windowDelegate: MemorySegment,
        private val imeView: MemorySegment,
        private val trace: MutableList<String>,
        private val run: Int,
    ) : AppKitRunAppOperations {
        override fun requireMainThread() = Unit

        override fun initialize() {
            trace += "run$run:initialize"
        }

        override fun attachApplicationDelegate() {
            trace += "run$run:attachDelegate"
            KadreAppDelegate.registerDelegateRoute(
                appDelegate.address(),
                object : AppKitApplicationDelegateCallbacks {
                    override fun onDidFinishLaunching() = eventLoop.didLaunch()
                    override fun onDidBecomeActive() = eventLoop.didBecomeActive()
                    override fun onWillResignActive() = eventLoop.willResignActive()
                    override fun onWillTerminate() = eventLoop.willTerminate()
                    override fun captureCallbackFailure(context: String, failure: Throwable) {
                        eventLoop.recordCallbackFailure(context, failure)
                    }
                },
            )
            KadreWindowDelegate.registerDelegateRoute(
                windowDelegate.address(),
                object : AppKitWindowDelegateCallbacks {},
            )
            AppKitImeTextInputClient.registerView(
                imeView,
                ImeViewRecord(NoopHandler, eventLoop, WindowId(imeView.address()), MemorySegment.NULL),
            )
            eventLoop.registerWindowCloseActions(
                WindowId(windowDelegate.address()),
                unregisterCallbacks = {
                    KadreWindowDelegate.unregisterDelegate(windowDelegate.address())
                    AppKitImeTextInputClient.unregisterView(imeView)
                    trace += "run$run:unregisterWindowCallbacks"
                },
                closeNative = { trace += "run$run:nativeClose" },
            )
        }

        override fun installRunLoopOwner() {
            trace += "run$run:installOwner"
        }

        override fun run() {
            trace += "run$run:run"
            KadreAppDelegate.Callbacks.applicationDidFinishLaunching(
                appDelegate,
                MemorySegment.NULL,
                MemorySegment.NULL,
            )
            KadreAppDelegate.Callbacks.applicationWillTerminate(
                appDelegate,
                MemorySegment.NULL,
                MemorySegment.NULL,
            )
        }

        override fun throwPendingCallbackFailure() {
            eventLoop.throwPendingCallbackFailure()
        }

        override fun suppressPendingCallbackFailureOnto(primary: Throwable) {
            eventLoop.suppressPendingCallbackFailureOnto(primary)
        }

        override fun closeRunLoopOwner() {
            trace += "run$run:closeOwner"
        }

        override fun detachApplicationDelegate() {
            trace += "run$run:detachDelegate"
        }

        override fun releaseApplicationDelegate() {
            KadreAppDelegate.unregisterDelegate(appDelegate.address())
            trace += "run$run:releaseDelegate"
        }

        override fun clearApplicationReferences() {
            trace += "run$run:clearReferences"
        }
    }

    private class WakeRecordingCFRunLoopApi : CFRunLoopApi {
        var wakeCount = 0

        override fun createObserver(activities: Long): Long = 1L
        override fun addObserver(observer: Long) = Unit
        override fun removeObserver(observer: Long) = Unit
        override fun invalidateObserver(observer: Long) = Unit
        override fun createTimer(deadlineEpochMillis: Long): Long = 2L
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
