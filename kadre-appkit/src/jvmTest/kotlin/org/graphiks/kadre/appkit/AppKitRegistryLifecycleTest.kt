package org.graphiks.kadre.appkit

import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowId
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppKitRegistryLifecycleTest {

    @Test
    fun `public exit requests termination before ordered best-effort lifecycle cleanup`() {
        val trace = mutableListOf<String>()
        val destroyFailure = IllegalStateException("destroy failure")
        val closeFailure = IllegalArgumentException("close failure")
        val eventLoop = AppKitEventLoop(
            handler = object : ApplicationHandler {
                override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

                override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                    trace += "destroySurfaces"
                    throw destroyFailure
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
            },
            terminateApplication = { trace += "terminate" },
        )
        val windowId = WindowId(0x901L)
        eventLoop.registerWindowCloseActions(
            windowId,
            unregisterCallbacks = { trace += "unregister" },
            closeNative = {
                trace += "closeNative"
                throw closeFailure
            },
        )
        eventLoop.didLaunch()

        eventLoop.exit()

        assertEquals(listOf("terminate"), trace)
        val failure = assertFailsWith<IllegalStateException> {
            eventLoop.willTerminate()
        }
        assertSame(destroyFailure, failure)
        assertSame(closeFailure, failure.suppressed.single())
        assertEquals(
            listOf("terminate", "destroySurfaces", "unregister", "Destroyed", "closeNative", "suspended"),
            trace,
        )
    }

    @Test
    fun `owned native window disables release-on-close and releases each owned object once`() {
        val trace = mutableListOf<String>()
        val released = java.util.concurrent.atomic.AtomicBoolean(false)

        appKitOwnNativeWindow { releasedWhenClosed ->
            trace += "releasedWhenClosed=$releasedWhenClosed"
        }
        repeat(2) {
            appKitReleaseNativeWindowResources(
                released = released,
                sendNativeClose = true,
                closeWindow = { trace += "closeWindow" },
                releaseWindow = { trace += "releaseWindow" },
                releaseLayer = { trace += "releaseLayer" },
                releaseView = { trace += "releaseView" },
            )
        }

        assertEquals(
            listOf(
                "releasedWhenClosed=false",
                "closeWindow",
                "releaseWindow",
                "releaseLayer",
                "releaseView",
            ),
            trace,
        )
    }

    @Test
    fun `sendEvent and IME query trampolines contain every failure and return safe defaults`() {
        val eventLoop = AppKitEventLoop(NoopHandler)
        val view = MemorySegment.ofAddress(0x902L)
        AppKitImeTextInputClient.registerView(
            view,
            ImeViewRecord(NoopHandler, eventLoop, WindowId(view.address()), MemorySegment.NULL),
        )
        val superFailure = IllegalStateException("super send failure")
        val handlerFailure = IllegalArgumentException("handler failure")
        val ffmFailure = UnsupportedOperationException("FFM failure")
        val queryFailure = IndexOutOfBoundsException("range failure")
        try {
            appKitInvokeSendEventSafely(eventLoop) { throw superFailure }
            appKitInvokeSendEventSafely(eventLoop) { throw handlerFailure }
            appKitInvokeSendEventSafely(eventLoop) { throw ffmFailure }

            val safeQuery = AppKitImeTextInputClient.Callbacks.querySafely(
                self = view,
                context = "selectedRange",
                defaultValue = MemorySegment.NULL,
            ) { throw queryFailure }
            assertEquals(MemorySegment.NULL, safeQuery)

            AppKitImeTextInputClient.Callbacks.firstRectForCharacterRange_actualRange(
                MemorySegment.NULL,
                view,
                MemorySegment.NULL,
                MemorySegment.NULL,
                MemorySegment.NULL,
            )

            val queued = assertFailsWith<IllegalStateException> {
                eventLoop.throwPendingCallbackFailure()
            }
            assertSame(superFailure, queued.cause)
            assertSame(handlerFailure, queued.suppressed[0].cause)
            assertSame(ffmFailure, queued.suppressed[1].cause)
            assertSame(queryFailure, queued.suppressed[2].cause)
            assertTrue(queued.suppressed[3].message.orEmpty().contains("firstRectForCharacterRange"))

            appKitInvokeSendEventSafely(null) { error("no route") }
            val staleDefault = AppKitImeTextInputClient.Callbacks.querySafely(
                self = MemorySegment.ofAddress(0x903L),
                context = "validAttributesForMarkedText",
                defaultValue = MemorySegment.NULL,
            ) { error("stale route") }
            assertEquals(MemorySegment.NULL, staleDefault)
        } finally {
            AppKitImeTextInputClient.unregisterView(view)
        }
    }

    @Test
    fun `IME rect query resets actual range to NSNotFound after range read failure`() {
        val eventLoop = AppKitEventLoop(NoopHandler)
        val view = MemorySegment.ofAddress(0x923L)
        AppKitImeTextInputClient.registerView(
            view,
            ImeViewRecord(NoopHandler, eventLoop, WindowId(view.address()), MemorySegment.NULL),
        )
        try {
            Arena.ofConfined().use { arena ->
                val returnRect = arena.allocate(32L, 8L)
                val actualRange = arena.allocate(AppKitImeTextInputClient.NS_RANGE_LAYOUT)

                AppKitImeTextInputClient.Callbacks.firstRectForCharacterRange_actualRange(
                    returnRect,
                    view,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    actualRange,
                )

                assertEquals(Long.MAX_VALUE, actualRange.getAtIndex(ValueLayout.JAVA_LONG, 0))
                assertEquals(0L, actualRange.getAtIndex(ValueLayout.JAVA_LONG, 1))
                repeat(4) { index ->
                    assertEquals(0.0, returnRect.getAtIndex(ValueLayout.JAVA_DOUBLE, index.toLong()))
                }
            }
            val queued = assertFailsWith<IllegalStateException> {
                eventLoop.throwPendingCallbackFailure()
            }
            assertTrue(queued.message.orEmpty().contains("firstRectForCharacterRange"))
        } finally {
            AppKitImeTextInputClient.unregisterView(view)
        }
    }

    @Test
    fun `first callback failure terminates a blocking run and final cleanup failure is drained`() {
        val trace = java.util.Collections.synchronizedList(mutableListOf<String>())
        val callbackFailure = IllegalStateException("callback failure")
        val cleanupCallbackFailure = IllegalArgumentException("cleanup callback failure")
        val runStarted = CountDownLatch(1)
        val callbackRecorded = CountDownLatch(1)
        val allowCallbackReturn = CountDownLatch(1)
        val callbackToken = AtomicReference<AppKitNativeCallbackToken>()
        val callbackRoute = AtomicReference<AppKitApplicationDelegateCallbacks>()
        val runFailure = AtomicReference<Throwable?>()
        val wakeApi = BlockingWakeCFRunLoopApi()

        val runThread = thread(name = "blocking-appkit-run") {
            runFailure.set(runCatching {
                runApp(NoopHandler) { eventLoop ->
                object : AppKitRunAppOperations {
                    private var owner: CFRunLoopOwner? = null

                    override fun requestTermination() {
                        trace += "requestTermination"
                        wakeApi.terminationRequested.countDown()
                    }

                    override fun requireMainThread() = Unit
                    override fun initialize() = Unit
                    override fun attachApplicationDelegate() {
                        val callbacks = object : AppKitApplicationDelegateCallbacks {
                            override fun onDidBecomeActive() = throw callbackFailure
                            override fun captureCallbackFailure(context: String, failure: Throwable) {
                                eventLoop.recordCallbackFailure(context, failure)
                                callbackRecorded.countDown()
                                check(allowCallbackReturn.await(5, TimeUnit.SECONDS))
                            }
                        }
                        callbackRoute.set(callbacks)
                        callbackToken.set(KadreAppDelegate.registerDelegateRoute(0x970L, callbacks))
                    }

                    override fun installRunLoopOwner() {
                        val installedOwner = CFRunLoopOwner.install(
                            api = wakeApi,
                            state = AppKitLoopState { 0L },
                            onAfterWaiting = { eventLoop.drainDeferredNativeCallbackCleanup() },
                            onBeforeWaiting = { org.graphiks.kadre.core.ControlFlow.Wait },
                        )
                        owner = installedOwner
                        eventLoop.installRunLoopOwner(installedOwner)
                    }

                    override fun run() {
                        trace += "run"
                        runStarted.countDown()
                        check(wakeApi.woken.await(5, TimeUnit.SECONDS))
                        CFRunLoopOwner.dispatchObserverCallback(
                            wakeApi.observer,
                            CFRunLoopOwner.AFTER_WAITING,
                        )
                        check(wakeApi.terminationRequested.await(5, TimeUnit.SECONDS))
                        trace += "runReturned"
                    }

                    override fun throwPendingCallbackFailure() = eventLoop.throwPendingCallbackFailure()

                    override fun suppressPendingCallbackFailureOnto(primary: Throwable) =
                        eventLoop.suppressPendingCallbackFailureOnto(primary)

                    override fun closeRunLoopOwner() {
                        owner?.let(eventLoop::clearRunLoopOwner)
                        owner?.close()
                        owner = null
                        trace += "closeOwner"
                    }

                    override fun detachApplicationDelegate() {
                        trace += "detachDelegate"
                        eventLoop.recordCallbackFailure("cleanupCallback", cleanupCallbackFailure)
                    }

                    override fun releaseApplicationDelegate() {
                        KadreAppDelegate.unregisterDelegate(callbackToken.get(), callbackRoute.get())
                        trace += "releaseDelegate"
                    }

                    override fun clearApplicationReferences() {
                        trace += "clearReferences"
                    }
                }
                }
            }.exceptionOrNull())
        }

        assertTrue(runStarted.await(5, TimeUnit.SECONDS))
        val callbackThread = thread(name = "controlled-native-callback") {
            trace += "callback"
            KadreAppDelegate.Callbacks.applicationDidBecomeActiveForToken(callbackToken.get())
            trace += "callbackReturn"
        }
        assertTrue(callbackRecorded.await(5, TimeUnit.SECONDS))
        assertFalse(wakeApi.terminationRequested.await(100, TimeUnit.MILLISECONDS))
        allowCallbackReturn.countDown()
        callbackThread.join(5_000)
        runThread.join(5_000)

        assertFalse(callbackThread.isAlive)
        assertFalse(runThread.isAlive)
        val failure = runFailure.get() as? IllegalStateException
            ?: throw AssertionError("expected callback failure after blocking run cleanup", runFailure.get())

        assertSame(callbackFailure, failure.cause)
        val cleanupQueued = failure.suppressed.single()
        assertTrue(cleanupQueued.message.orEmpty().contains("cleanupCallback"))
        assertSame(cleanupCallbackFailure, cleanupQueued.cause)
        assertEquals(
            listOf(
                "run",
                "callback",
                "callbackReturn",
                "requestTermination",
                "runReturned",
                "closeOwner",
                "detachDelegate",
                "releaseDelegate",
                "clearReferences",
            ),
            trace,
        )
    }

    @Test
    fun `native window close defers every owned release past callback return`() {
        val trace = mutableListOf<String>()
        val windowId = WindowId(0x904L)
        val delegate = MemorySegment.ofAddress(0x905L)
        val eventLoop = AppKitEventLoop(NoopHandler)
        var callbackInFlight = false
        var resourcesReleased = false
        var delegateReleased = false
        KadreWindowDelegate.registerDelegateRoute(
            delegate.address(),
            object : AppKitWindowDelegateCallbacks {
                override fun onWindowWillClose() {
                    callbackInFlight = true
                    trace += "callback"
                    eventLoop.confirmWindowClosed(windowId)
                    assertFalse(resourcesReleased)
                    assertFalse(delegateReleased)
                    trace += "callbackReturn"
                    callbackInFlight = false
                }
            },
        )
        eventLoop.registerWindowCloseActions(
            windowId = windowId,
            unregisterCallbacks = {
                KadreWindowDelegate.unregisterDelegate(delegate.address())
                trace += "unregister"
            },
            sendNativeClose = { trace += "sendNativeClose" },
            releaseNativeResources = {
                assertFalse(callbackInFlight)
                resourcesReleased = true
                trace += "releaseWindowLayerView"
            },
            releaseDelegate = {
                assertFalse(callbackInFlight)
                delegateReleased = true
                trace += "releaseDelegate"
            },
        )

        KadreWindowDelegate.Callbacks.windowWillClose(
            delegate,
            MemorySegment.NULL,
            MemorySegment.NULL,
        )

        assertEquals(
            listOf("callback", "unregister", "callbackReturn"),
            trace,
        )
        assertFalse(resourcesReleased)
        assertFalse(delegateReleased)
        eventLoop.drainDeferredNativeCallbackCleanup()
        assertTrue(delegateReleased)
        assertEquals(
            listOf(
                "callback",
                "unregister",
                "callbackReturn",
                "releaseWindowLayerView",
                "releaseDelegate",
            ),
            trace,
        )
    }

    @Test
    fun `termination rejects reentrant windows and drains close actions to stability`() {
        val trace = mutableListOf<String>()
        lateinit var eventLoop: AppKitEventLoop
        var attemptedFromDestroyed = false
        eventLoop = AppKitEventLoop(object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                val failure = assertFailsWith<IllegalStateException> {
                    eventLoop.createWindow(WindowAttributes())
                }
                assertTrue(failure.message.orEmpty().contains("terminating"))
                trace += "destroyRejected"
            }

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event === WindowEvent.Destroyed && !attemptedFromDestroyed) {
                    attemptedFromDestroyed = true
                    val failure = assertFailsWith<IllegalStateException> {
                        eventLoop.createWindow(WindowAttributes())
                    }
                    assertTrue(failure.message.orEmpty().contains("terminating"))
                    trace += "destroyedRejected"
                }
            }
        })
        val first = WindowId(0x906L)
        val reentrant = WindowId(0x907L)
        eventLoop.registerWindowCloseActions(
            first,
            unregisterCallbacks = {
                trace += "closeFirst"
                eventLoop.registerWindowCloseActions(
                    reentrant,
                    unregisterCallbacks = { trace += "closeReentrant" },
                    closeNative = {},
                )
            },
            closeNative = {},
        )

        eventLoop.willTerminate()

        assertEquals(
            listOf("destroyRejected", "closeFirst", "destroyedRejected", "closeReentrant"),
            trace,
        )
        assertFalse(eventLoop.hasRegisteredWindow(first))
        assertFalse(eventLoop.hasRegisteredWindow(reentrant))
    }

    @Test
    fun `setDelegate failure unregisters partial route and preserves cleanup identity`() {
        val address = 0x908L
        val callbacks = object : AppKitWindowDelegateCallbacks {}
        val released = java.util.concurrent.atomic.AtomicBoolean(false)
        val setDelegateFailure = IllegalStateException("setDelegate failure")
        val releaseFailure = IllegalArgumentException("delegate release failure")
        KadreWindowDelegate.registerDelegateRoute(address, callbacks)

        val failure = assertFailsWith<IllegalStateException> {
            appKitReleaseFailedWindowDelegate(setDelegateFailure) {
                appKitReleaseWindowDelegateNative(
                    released = released,
                    address = address,
                    callbacks = callbacks,
                    releaseNative = { throw releaseFailure },
                )
            }
        }

        assertSame(setDelegateFailure, failure)
        assertSame(releaseFailure, failure.suppressed.single())
        assertEquals(0, KadreWindowDelegate.registeredDelegateCount())
        appKitReleaseWindowDelegateNative(
            released = released,
            address = address,
            callbacks = callbacks,
            releaseNative = { error("released twice") },
        )
    }

    @Test
    fun `terminal cleanup clears Kotlin references after detach or release failure`() {
        listOf("detach", "release").forEachIndexed { index, failingStep ->
            val windowId = WindowId(0x910L + index)
            val delegateAddress = windowId.value + 0x10L
            val view = MemorySegment.ofAddress(windowId.value + 0x20L)
            val callbacks = object : AppKitWindowDelegateCallbacks {}
            val failure = IllegalStateException("$failingStep failure")
            var delegateReference: Any? = Any()
            var handlerReference: Any? = Any()
            var eventLoopReference: Any? = Any()
            val eventLoop = AppKitEventLoop(NoopHandler)
            KadreWindowDelegate.registerDelegateRoute(delegateAddress, callbacks)
            AppKitImeTextInputClient.registerView(
                view,
                ImeViewRecord(NoopHandler, eventLoop, windowId, MemorySegment.NULL),
            )
            eventLoop.registerWindowCloseActions(
                windowId = windowId,
                unregisterCallbacks = {
                    appKitClearWindowCallbackReferences(
                        cleanupCallbacks = {
                            appKitUnregisterWindowCallbacks(
                                delegateAddress = delegateAddress,
                                textInputView = view,
                                detachNativeDelegate = {
                                    if (failingStep == "detach") throw failure
                                },
                                releaseDelegate = {},
                            )
                        },
                        clearReferences = {
                            delegateReference = null
                            handlerReference = null
                            eventLoopReference = null
                        },
                    )
                },
                sendNativeClose = {},
                releaseNativeResources = {},
                releaseDelegate = {
                    if (failingStep == "release") throw failure
                },
            )

            val thrown = assertFailsWith<IllegalStateException> {
                eventLoop.closeWindow(windowId)
            }

            assertSame(failure, thrown)
            assertEquals(null, delegateReference)
            assertEquals(null, handlerReference)
            assertEquals(null, eventLoopReference)
            assertEquals(0, KadreWindowDelegate.registeredDelegateCount())
            assertEquals(0, AppKitImeTextInputClient.registeredViewCount())
        }
    }

    @Test
    fun `identity-aware routes isolate reused addresses and in-flight callbacks across runs`() {
        val address = 0x920L
        val self = MemorySegment.ofAddress(address)

        val appEvents = mutableListOf<String>()
        val appEntered = CountDownLatch(1)
        val appRelease = CountDownLatch(1)
        val appRun1 = object : AppKitApplicationDelegateCallbacks {
            override fun onDidBecomeActive() {
                appEntered.countDown()
                check(appRelease.await(5, TimeUnit.SECONDS))
                appEvents += "run1"
            }
        }
        val appRun2 = object : AppKitApplicationDelegateCallbacks {
            override fun onDidBecomeActive() {
                appEvents += "run2"
            }
        }
        KadreAppDelegate.registerDelegateRoute(address, appRun1)
        val appThread = thread(name = "app-route-run1") {
            KadreAppDelegate.Callbacks.applicationDidBecomeActive(
                self,
                MemorySegment.NULL,
                MemorySegment.NULL,
            )
        }
        assertTrue(appEntered.await(5, TimeUnit.SECONDS))
        KadreAppDelegate.registerDelegateRoute(address, appRun2)
        KadreAppDelegate.unregisterDelegate(address, appRun1)
        appRelease.countDown()
        appThread.join(5_000)
        assertFalse(appThread.isAlive)
        KadreAppDelegate.Callbacks.applicationDidBecomeActive(
            self,
            MemorySegment.NULL,
            MemorySegment.NULL,
        )
        assertEquals(listOf("run1", "run2"), appEvents)
        assertEquals(1, KadreAppDelegate.registeredDelegateCount())

        val windowEvents = mutableListOf<String>()
        val windowEntered = CountDownLatch(1)
        val windowRelease = CountDownLatch(1)
        val windowRun1 = object : AppKitWindowDelegateCallbacks {
            override fun onWindowWillClose() {
                windowEntered.countDown()
                check(windowRelease.await(5, TimeUnit.SECONDS))
                windowEvents += "run1"
            }
        }
        val windowRun2 = object : AppKitWindowDelegateCallbacks {
            override fun onWindowWillClose() {
                windowEvents += "run2"
            }
        }
        KadreWindowDelegate.registerDelegateRoute(address, windowRun1)
        val windowThread = thread(name = "window-route-run1") {
            KadreWindowDelegate.Callbacks.windowWillClose(
                self,
                MemorySegment.NULL,
                MemorySegment.NULL,
            )
        }
        assertTrue(windowEntered.await(5, TimeUnit.SECONDS))
        KadreWindowDelegate.registerDelegateRoute(address, windowRun2)
        KadreWindowDelegate.unregisterDelegate(address, windowRun1)
        windowRelease.countDown()
        windowThread.join(5_000)
        assertFalse(windowThread.isAlive)
        KadreWindowDelegate.Callbacks.windowWillClose(
            self,
            MemorySegment.NULL,
            MemorySegment.NULL,
        )
        assertEquals(listOf("run1", "run2"), windowEvents)
        assertEquals(1, KadreWindowDelegate.registeredDelegateCount())

        val imeEvents = mutableListOf<String>()
        val imeEntered = CountDownLatch(1)
        val imeRelease = CountDownLatch(1)
        val eventLoop = AppKitEventLoop(NoopHandler)
        fun imeRecord(run: String, entered: CountDownLatch? = null, release: CountDownLatch? = null) =
            ImeViewRecord(
                handler = object : ApplicationHandler {
                    override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

                    override fun windowEvent(
                        eventLoop: ActiveEventLoop,
                        windowId: WindowId,
                        event: WindowEvent,
                    ) {
                        entered?.countDown()
                        release?.let { check(it.await(5, TimeUnit.SECONDS)) }
                        imeEvents += run
                    }
                },
                eventLoop = eventLoop,
                windowId = WindowId(address),
                imeCursorScreenRect = MemorySegment.NULL,
            )
        val imeRun1 = imeRecord("run1", imeEntered, imeRelease)
        val imeRun2 = imeRecord("run2")
        AppKitImeTextInputClient.registerView(self, imeRun1)
        val imeThread = thread(name = "ime-route-run1") {
            AppKitImeTextInputClient.Callbacks.unmarkText(self, MemorySegment.NULL)
        }
        assertTrue(imeEntered.await(5, TimeUnit.SECONDS))
        AppKitImeTextInputClient.registerView(self, imeRun2)
        AppKitImeTextInputClient.unregisterView(self, imeRun1)
        imeRelease.countDown()
        imeThread.join(5_000)
        assertFalse(imeThread.isAlive)
        AppKitImeTextInputClient.Callbacks.unmarkText(self, MemorySegment.NULL)
        assertEquals(listOf("run1", "run2"), imeEvents)
        assertEquals(1, AppKitImeTextInputClient.registeredViewCount())

        KadreAppDelegate.unregisterDelegate(address, appRun2)
        KadreWindowDelegate.unregisterDelegate(address, windowRun2)
        AppKitImeTextInputClient.unregisterView(self, imeRun2)
        assertEquals(0, KadreAppDelegate.registeredDelegateCount())
        assertEquals(0, KadreWindowDelegate.registeredDelegateCount())
        assertEquals(0, AppKitImeTextInputClient.registeredViewCount())
    }

    @Test
    fun `native generation tokens reject late callbacks after address reuse for every route`() {
        val address = 0x921L
        val self = MemorySegment.ofAddress(address)

        val appEvents = mutableListOf<String>()
        val appRun1 = object : AppKitApplicationDelegateCallbacks {
            override fun onDidBecomeActive() {
                appEvents += "run1"
            }
        }
        val appRun2 = object : AppKitApplicationDelegateCallbacks {
            override fun onDidBecomeActive() {
                appEvents += "run2"
            }
        }
        val appToken1 = KadreAppDelegate.registerDelegateRoute(address, appRun1)
        val appToken2 = KadreAppDelegate.registerDelegateRoute(address, appRun2)
        KadreAppDelegate.unregisterDelegate(appToken1, appRun1)
        KadreAppDelegate.Callbacks.applicationDidBecomeActiveForToken(appToken1)
        KadreAppDelegate.Callbacks.applicationDidBecomeActiveForToken(appToken2)
        assertEquals(listOf("run2"), appEvents)

        val windowEvents = mutableListOf<String>()
        val windowRun1 = object : AppKitWindowDelegateCallbacks {
            override fun onWindowWillClose() {
                windowEvents += "run1"
            }
        }
        val windowRun2 = object : AppKitWindowDelegateCallbacks {
            override fun onWindowWillClose() {
                windowEvents += "run2"
            }
        }
        val windowToken1 = KadreWindowDelegate.registerDelegateRoute(address, windowRun1)
        val windowToken2 = KadreWindowDelegate.registerDelegateRoute(address, windowRun2)
        KadreWindowDelegate.unregisterDelegate(windowToken1, windowRun1)
        KadreWindowDelegate.Callbacks.windowWillCloseForToken(windowToken1)
        KadreWindowDelegate.Callbacks.windowWillCloseForToken(windowToken2)
        assertEquals(listOf("run2"), windowEvents)

        val imeEvents = mutableListOf<String>()
        val eventLoop = AppKitEventLoop(NoopHandler)
        fun imeRecord(run: String) = ImeViewRecord(
            handler = object : ApplicationHandler {
                override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

                override fun windowEvent(
                    eventLoop: ActiveEventLoop,
                    windowId: WindowId,
                    event: WindowEvent,
                ) {
                    imeEvents += run
                }
            },
            eventLoop = eventLoop,
            windowId = WindowId(address),
            imeCursorScreenRect = MemorySegment.NULL,
        )
        val imeRun1 = imeRecord("run1")
        val imeRun2 = imeRecord("run2")
        val imeToken1 = AppKitImeTextInputClient.registerView(self, imeRun1)
        val imeToken2 = AppKitImeTextInputClient.registerView(self, imeRun2)
        AppKitImeTextInputClient.unregisterView(imeToken1, imeRun1)
        AppKitImeTextInputClient.Callbacks.unmarkTextForToken(imeToken1)
        AppKitImeTextInputClient.Callbacks.unmarkTextForToken(imeToken2)
        assertEquals(listOf("run2"), imeEvents)

        KadreAppDelegate.unregisterDelegate(appToken2, appRun2)
        KadreWindowDelegate.unregisterDelegate(windowToken2, windowRun2)
        AppKitImeTextInputClient.unregisterView(imeToken2, imeRun2)
        assertEquals(0, KadreAppDelegate.registeredDelegateCount())
        assertEquals(0, KadreWindowDelegate.registeredDelegateCount())
        assertEquals(0, AppKitImeTextInputClient.registeredViewCount())
    }

    @Test
    fun `native callback quiescence blocks run turnover until in-flight callback returns`() {
        val address = 0x922L
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val quiescent = CountDownLatch(1)
        val callbacks = object : AppKitApplicationDelegateCallbacks {
            override fun onDidBecomeActive() {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        }
        val token = KadreAppDelegate.registerDelegateRoute(address, callbacks)
        val callbackThread = thread(name = "native-callback-in-flight") {
            KadreAppDelegate.Callbacks.applicationDidBecomeActiveForToken(token)
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))

        val turnoverThread = thread(name = "run-turnover") {
            AppKitNativeCallbackBoundary.awaitQuiescence()
            quiescent.countDown()
        }
        assertFalse(quiescent.await(100, TimeUnit.MILLISECONDS))
        release.countDown()
        callbackThread.join(5_000)
        turnoverThread.join(5_000)

        assertFalse(callbackThread.isAlive)
        assertFalse(turnoverThread.isAlive)
        assertEquals(0L, quiescent.count)
        KadreAppDelegate.unregisterDelegate(token, callbacks)
    }

    @Test
    fun `reentrant callback termination defers every native release past upcall return`() {
        listOf("resize", "ime", "windowShouldClose").forEachIndexed { index, selector ->
            val trace = mutableListOf<String>()
            lateinit var eventLoop: AppKitEventLoop
            eventLoop = AppKitEventLoop(
                handler = NoopHandler,
                terminateApplication = {
                    trace += "terminate"
                    eventLoop.willTerminate()
                },
            )
            val windowId = WindowId(0x930L + index)
            eventLoop.registerWindowCloseActions(
                windowId = windowId,
                unregisterCallbacks = { trace += "unregister" },
                sendNativeClose = { trace += "closeWindow" },
                releaseNativeResources = { trace += "releaseWindowViewLayer" },
                releaseDelegate = { trace += "releaseDelegate" },
            )

            val self = MemorySegment.ofAddress(0x940L + index)
            when (selector) {
                "resize" -> {
                    val callbacks = object : AppKitWindowDelegateCallbacks {
                        override fun onWindowDidResize() = error("resize failure")
                        override fun captureCallbackFailure(context: String, failure: Throwable) {
                            eventLoop.recordCallbackFailure(context, failure)
                        }
                    }
                    val token = KadreWindowDelegate.registerDelegateRoute(self.address(), callbacks)
                    trace += "callback"
                    KadreWindowDelegate.Callbacks.windowDidResize(
                        self,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                    )
                    trace += "callbackReturn"
                    KadreWindowDelegate.unregisterDelegate(token, callbacks)
                }
                "ime" -> {
                    val record = ImeViewRecord(
                        handler = object : ApplicationHandler {
                            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
                            override fun windowEvent(
                                eventLoop: ActiveEventLoop,
                                windowId: WindowId,
                                event: WindowEvent,
                            ) = error("IME failure")
                        },
                        eventLoop = eventLoop,
                        windowId = windowId,
                        imeCursorScreenRect = MemorySegment.NULL,
                    )
                    val token = AppKitImeTextInputClient.registerView(self, record)
                    trace += "callback"
                    AppKitImeTextInputClient.Callbacks.unmarkText(self, MemorySegment.NULL)
                    trace += "callbackReturn"
                    AppKitImeTextInputClient.unregisterView(token, record)
                }
                else -> {
                    val callbacks = object : AppKitWindowDelegateCallbacks {
                        override fun onWindowShouldClose(): Byte = error("close query failure")
                        override fun captureCallbackFailure(context: String, failure: Throwable) {
                            eventLoop.recordCallbackFailure(context, failure)
                        }
                    }
                    val token = KadreWindowDelegate.registerDelegateRoute(self.address(), callbacks)
                    trace += "callback"
                    KadreWindowDelegate.Callbacks.windowShouldClose(
                        self,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                    )
                    trace += "callbackReturn"
                    KadreWindowDelegate.unregisterDelegate(token, callbacks)
                }
            }

            eventLoop.drainDeferredNativeCallbackCleanup()

            assertEquals(
                listOf(
                    "callback",
                    "callbackReturn",
                    "terminate",
                    "unregister",
                    "closeWindow",
                    "releaseWindowViewLayer",
                    "releaseDelegate",
                ),
                trace,
                selector,
            )
        }
    }

    @Test
    fun `sendEvent exit defers termination and native releases past upcall return`() {
        val trace = mutableListOf<String>()
        lateinit var eventLoop: AppKitEventLoop
        eventLoop = AppKitEventLoop(
            handler = NoopHandler,
            terminateApplication = {
                trace += "terminate"
                eventLoop.willTerminate()
            },
        )
        eventLoop.registerWindowCloseActions(
            windowId = WindowId(0x949L),
            unregisterCallbacks = { trace += "unregister" },
            sendNativeClose = { trace += "closeWindow" },
            releaseNativeResources = { trace += "releaseWindowViewLayer" },
            releaseDelegate = { trace += "releaseDelegate" },
        )

        trace += "callback"
        appKitInvokeSendEventSafely(eventLoop) {
            trace += "handler"
            eventLoop.exit()
        }
        trace += "callbackReturn"

        assertEquals(listOf("callback", "handler", "callbackReturn"), trace)
        eventLoop.drainDeferredNativeCallbackCleanup()
        assertEquals(
            listOf(
                "callback",
                "handler",
                "callbackReturn",
                "terminate",
                "unregister",
                "closeWindow",
                "releaseWindowViewLayer",
                "releaseDelegate",
            ),
            trace,
        )
    }

    @Test
    fun `serialized terminal state preserves close ordering across reentrant exit and inactivity`() {
        listOf("destroySurfaces", "Destroyed").forEachIndexed { index, reentrantPhase ->
            val trace = mutableListOf<String>()
            lateinit var eventLoop: AppKitEventLoop
            eventLoop = AppKitEventLoop(
                handler = object : ApplicationHandler {
                    override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
                    override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                        trace += "destroySurfaces"
                        if (reentrantPhase == "destroySurfaces") eventLoop.exit()
                    }
                    override fun suspended(eventLoop: ActiveEventLoop) {
                        trace += "suspended"
                    }
                    override fun windowEvent(
                        eventLoop: ActiveEventLoop,
                        windowId: WindowId,
                        event: WindowEvent,
                    ) {
                        if (event === WindowEvent.Destroyed) {
                            trace += "Destroyed"
                            if (reentrantPhase == "Destroyed") eventLoop.exit()
                        }
                    }
                },
                terminateApplication = {
                    trace += "terminate"
                    eventLoop.willTerminate()
                },
            )
            eventLoop.registerWindowCloseActions(
                windowId = WindowId(0x950L + index),
                unregisterCallbacks = { trace += "unregister" },
                sendNativeClose = { trace += "closeWindow" },
                releaseNativeResources = {},
                releaseDelegate = {},
            )

            eventLoop.exit()

            assertEquals(
                listOf(
                    "terminate",
                    "destroySurfaces",
                    "unregister",
                    "Destroyed",
                    "closeWindow",
                    "suspended",
                ),
                trace,
                reentrantPhase,
            )
        }

        val inactiveTrace = mutableListOf<String>()
        val inactiveLoop = AppKitEventLoop(object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
            override fun resumed(eventLoop: ActiveEventLoop) {
                inactiveTrace += "resumed"
            }
            override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                inactiveTrace += "destroySurfaces"
            }
            override fun suspended(eventLoop: ActiveEventLoop) {
                inactiveTrace += "suspended"
            }
            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                if (event === WindowEvent.Destroyed) inactiveTrace += "Destroyed"
            }
        })
        inactiveLoop.didLaunch()
        inactiveLoop.willResignActive()
        inactiveTrace.clear()
        inactiveLoop.registerWindowCloseActions(
            windowId = WindowId(0x952L),
            unregisterCallbacks = {},
            sendNativeClose = { inactiveTrace += "closeWindow" },
            releaseNativeResources = {},
            releaseDelegate = {},
        )

        inactiveLoop.willTerminate()

        assertEquals(
            listOf("destroySurfaces", "Destroyed", "closeWindow", "suspended"),
            inactiveTrace,
        )
    }

    @Test
    fun `every drag trampoline returns rejection for lookup FFM and handler failures`() {
        val eventLoop = AppKitEventLoop(NoopHandler)
        val record = ImeViewRecord(
            handler = object : ApplicationHandler {
                override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
                override fun windowEvent(
                    eventLoop: ActiveEventLoop,
                    windowId: WindowId,
                    event: WindowEvent,
                ) = error("handler failure")
            },
            eventLoop = eventLoop,
            windowId = WindowId(0x960L),
            imeCursorScreenRect = MemorySegment.NULL,
        )

        fun assertLongDefaults(
            invoke: (() -> ImeViewRecord?, (ImeViewRecord) -> Long) -> Long,
        ) {
            assertEquals(0L, invoke({ error("lookup failure") }, { 4L }))
            assertEquals(0L, invoke({ record }, { error("FFM failure") }))
            assertEquals(0L, invoke({ record }) {
                it.handler.windowEvent(it.eventLoop, it.windowId, WindowEvent.DragLeft)
                4L
            })
        }

        assertLongDefaults(AppKitImeTextInputClient.Callbacks::draggingEnteredSafely)
        assertLongDefaults(AppKitImeTextInputClient.Callbacks::draggingUpdatedSafely)
        assertEquals(
            0,
            AppKitImeTextInputClient.Callbacks.performDragOperationSafely(
                recordLookup = { error("lookup failure") },
                operation = { 1 },
            ),
        )
        assertEquals(
            0,
            AppKitImeTextInputClient.Callbacks.performDragOperationSafely(
                recordLookup = { record },
                operation = { error("FFM failure") },
            ),
        )
        assertEquals(
            0,
            AppKitImeTextInputClient.Callbacks.performDragOperationSafely(
                recordLookup = { record },
                operation = {
                    it.handler.windowEvent(it.eventLoop, it.windowId, WindowEvent.DragLeft)
                    1
                },
            ),
        )
    }

    @Test
    fun `delegate installation failure rolls back delegate window layer and view ownership`() {
        val released = java.util.concurrent.atomic.AtomicBoolean(false)
        val setDelegateFailure = IllegalStateException("setDelegate failure")
        val delegateFailure = IllegalArgumentException("delegate release failure")
        val windowFailure = UnsupportedOperationException("window release failure")
        val layerFailure = IllegalMonitorStateException("layer release failure")
        val viewFailure = NoSuchElementException("view release failure")
        var delegateReleases = 0
        var windowReleases = 0
        var layerReleases = 0
        var viewReleases = 0

        val thrown = assertFailsWith<IllegalStateException> {
            appKitRollbackFailedWindowCreation(
                setDelegateFailure = setDelegateFailure,
                resourcesReleased = released,
                releaseDelegate = {
                    delegateReleases += 1
                    throw delegateFailure
                },
                releaseWindow = {
                    windowReleases += 1
                    throw windowFailure
                },
                releaseLayer = {
                    layerReleases += 1
                    throw layerFailure
                },
                releaseView = {
                    viewReleases += 1
                    throw viewFailure
                },
            )
        }

        assertSame(setDelegateFailure, thrown)
        assertEquals(1, delegateReleases)
        assertEquals(1, windowReleases)
        assertEquals(1, layerReleases)
        assertEquals(1, viewReleases)
        assertEquals(
            listOf(delegateFailure, windowFailure, layerFailure, viewFailure),
            thrown.suppressed.toList(),
        )
    }

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

    private class BlockingWakeCFRunLoopApi : CFRunLoopApi {
        val observer = 0x971L
        val woken = CountDownLatch(1)
        val terminationRequested = CountDownLatch(1)

        override fun createObserver(activities: Long): Long = observer
        override fun addObserver(observer: Long) = Unit
        override fun removeObserver(observer: Long) = Unit
        override fun invalidateObserver(observer: Long) = Unit
        override fun createTimer(deadlineEpochMillis: Long): Long = 0x972L
        override fun addTimer(timer: Long) = Unit
        override fun invalidateTimer(timer: Long) = Unit
        override fun removeTimer(timer: Long) = Unit
        override fun wakeUp() {
            woken.countDown()
        }
        override fun release(ref: Long) = Unit
        override fun close() = Unit
    }
}
