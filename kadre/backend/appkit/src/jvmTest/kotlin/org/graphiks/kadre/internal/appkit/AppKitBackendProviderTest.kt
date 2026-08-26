package org.graphiks.kadre.internal.appkit

import kotlinx.coroutines.cancel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.graphiks.kffi.objc.NSApplication
import org.graphiks.kffi.objc.NSNotificationCenter
import org.graphiks.kffi.objc.ObjCRuntime
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreLifecycle
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendProvider
import org.graphiks.kadre.internal.runtime.desktop.DesktopEmbeddedRequest
import org.graphiks.kadre.internal.runtime.desktop.DesktopIntegrationKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopStandaloneRequest
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.window.WindowManager
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import java.lang.foreign.Arena
import java.util.ServiceLoader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppKitBackendProviderTest {
    @Test
    fun discoveryAndAvailabilityDoNotTouchTheNativeBridge() {
        val providers = ServiceLoader.load(DesktopBackendProvider::class.java).toList()
        val provider = providers.single { it.backend == DesktopBackendKind.AppKit }

        assertEquals(setOf(DesktopIntegrationKind.AppKitMainLoop), provider.supportedIntegrations)
        assertEquals(isMacOs(), provider.isAvailable())
    }

    @Test
    fun embeddedAttachRejectsInvalidHostStateBeforeFactoryCreation() {
        var factoryInvoked = false
        val native = RecordingNativeApplication(mainThread = false)
        val provider = AppKitBackendProvider.forTesting(native, AppKitProcessBroker()) { true }
        val factory = KadreApplicationFactory {
            factoryInvoked = true
            KadreApplication { }
        }

        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("options")),
            provider.run(DesktopStandaloneRequest(factory, true, KadrePolicies.Default)),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("options")),
            provider.attach(
                DesktopEmbeddedRequest(
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
                    factory,
                    DesktopIntegrationKind.AppKitMainLoop,
                    KadrePolicies.Default,
                ),
            ),
        )
        assertFalse(factoryInvoked)
        assertEquals(0, native.runCount)
    }

    @Test
    fun embeddedAttachRejectsWrongIntegrationAndInactiveNativeLoopBeforeFactoryCreation() {
        var factoryInvoked = false
        val factory = KadreApplicationFactory {
            factoryInvoked = true
            KadreApplication { }
        }
        val inactiveProvider = AppKitBackendProvider.forTesting(
            RecordingNativeApplication(),
            AppKitProcessBroker(),
        ) { true }
        val inactiveRequest = DesktopEmbeddedRequest(
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
            factory,
            DesktopIntegrationKind.AppKitMainLoop,
            KadrePolicies.Default,
        )

        assertEquals(
            KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true)),
            inactiveProvider.attach(inactiveRequest),
        )
        assertFalse(factoryInvoked)

        val activeProvider = AppKitBackendProvider.forTesting(
            EmbeddedNativeApplication(),
            AppKitProcessBroker(),
        ) { true }
        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("options")),
            activeProvider.attach(
                DesktopEmbeddedRequest(
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
                    factory,
                    DesktopIntegrationKind.AwtEventDispatchThread,
                    KadrePolicies.Default,
                ),
            ),
        )
        assertFalse(factoryInvoked)
    }

    @Test
    fun embeddedAttachBusyClosesItsObservationBeforeFactoryCreation() {
        val broker = AppKitProcessBroker()
        val standalone = assertIs<AppKitProcessBroker.StandaloneLease>(broker.tryAcquireStandalone())
        val native = EmbeddedNativeApplication()
        val provider = AppKitBackendProvider.forTesting(native, broker) { true }
        var factoryInvoked = false
        val parentScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())

        try {
            assertEquals(
                KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host)),
                provider.attach(
                    DesktopEmbeddedRequest(
                        parentScope,
                        KadreApplicationFactory {
                            factoryInvoked = true
                            KadreApplication { }
                        },
                        DesktopIntegrationKind.AppKitMainLoop,
                        KadrePolicies.Default,
                    ),
                ),
            )
            assertEquals(0, native.observerCount)
            assertFalse(factoryInvoked)
        } finally {
            standalone.close()
            parentScope.cancel()
        }
    }

    @Test
    fun embeddedSessionsReceiveLifecycleWithoutOwningTheNativeLoop() = kotlinx.coroutines.runBlocking {
        val native = EmbeddedNativeApplication()
        val provider = AppKitBackendProvider.forTesting(native, AppKitProcessBroker()) { true }
        val firstScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
        val secondScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
        val firstLifecycle = CompletableDeferred<KadreLifecycle>()
        val secondLifecycle = CompletableDeferred<KadreLifecycle>()

        try {
            val first = provider.attach(embeddedRequest(firstScope, firstLifecycle)).requireSession()
            val second = provider.attach(embeddedRequest(secondScope, secondLifecycle)).requireSession()
            val observedFirst = withTimeout(2.seconds) { firstLifecycle.await() }
            val observedSecond = withTimeout(2.seconds) { secondLifecycle.await() }
            assertEquals(2, native.observerCount)

            native.emit(AppKitLifecycleSignal.DidHide)
            assertEquals(org.graphiks.kadre.application.VisibilityState.Background, observedFirst.state.value.visibility)
            assertEquals(org.graphiks.kadre.application.VisibilityState.Background, observedSecond.state.value.visibility)

            first.close()
            assertEquals(
                SessionOutcome.Stopped(SessionStopReason.HostRequested),
                first.awaitTermination(),
            )
            native.awaitObserverCount(1)

            native.emit(AppKitLifecycleSignal.BecameActive)
            assertEquals(org.graphiks.kadre.application.ActivationState.Active, observedSecond.state.value.activation)

            native.emit(AppKitLifecycleSignal.HostTerminated)
            assertEquals(
                SessionOutcome.Stopped(SessionStopReason.HostDetached),
                second.awaitTermination(),
            )
            native.awaitObserverCount(0)
            assertEquals(0, native.runCount)
            assertEquals(0, native.stopCount)
        } finally {
            firstScope.cancel()
            secondScope.cancel()
        }
    }

    @Test
    fun privateWindowDriversDoNotReplaceTheOrdinaryUnsupportedSessionManager() = kotlinx.coroutines.runBlocking {
        val native = EmbeddedNativeApplication()
        val provider = AppKitBackendProvider.forTesting(native, AppKitProcessBroker()) { true }
        val parentScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
        val observedWindows = CompletableDeferred<WindowManager>()
        val first = AppKitWindowRuntimeDriverFactory {
            DeterministicAppKitNativeWindowPort("private-first")
        }.create(KadrePolicies.Default.resources)
        val second = AppKitWindowRuntimeDriverFactory {
            DeterministicAppKitNativeWindowPort("private-second")
        }.create(KadrePolicies.Default.resources)

        try {
            val session = provider.attach(
                DesktopEmbeddedRequest(
                    parentScope,
                    KadreApplicationFactory {
                        KadreApplication {
                            observedWindows.complete(windows)
                            kotlinx.coroutines.awaitCancellation()
                        }
                    },
                    DesktopIntegrationKind.AppKitMainLoop,
                    KadrePolicies.Default,
                ),
            ).requireSession()
            val ordinary = withTimeout(2.seconds) { observedWindows.await() }

            assertNotSame(first.manager, ordinary)
            assertNotSame(second.manager, ordinary)
            assertEquals("UnsupportedWindowManager", ordinary::class.simpleName)
            assertEquals(
                WindowRequestOutcome.Rejected(KadreFailure.Unsupported(KadreOperation.RequestWindow)),
                ordinary.requestWindow(WindowSpec(title = "still-unsupported"))
                    .appKitSuccessValue()
                    .await(),
            )
            assertEquals(emptyList(), first.manager.state.value.windows)
            assertEquals(emptyList(), second.manager.state.value.windows)

            session.close()
            session.awaitTermination()
            Unit
        } finally {
            first.close()
            second.close()
            parentScope.cancel()
        }
    }

    @Test
    fun unavailableProviderDoesNotTouchTheNativeBridgeOrFactory() {
        var factoryInvoked = false
        val native = RecordingNativeApplication()
        val provider = AppKitBackendProvider.forTesting(native, AppKitProcessBroker()) { false }

        val result = provider.run(
            DesktopStandaloneRequest(
                KadreApplicationFactory {
                    factoryInvoked = true
                    KadreApplication { }
                },
                true,
                KadrePolicies.Default,
            ),
        )

        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach)),
            result,
        )
        assertEquals(0, native.mainThreadCheckCount)
        assertEquals(0, native.runCount)
        assertFalse(factoryInvoked)
    }

    @Test
    fun applicationStopStopsTheNativeLoopAndReturnsTheSessionOutcome() {
        val native = StopDrivenNativeApplication()
        val provider = AppKitBackendProvider.forTesting(native, AppKitProcessBroker()) { true }

        val result = provider.run(
            DesktopStandaloneRequest(
                KadreApplicationFactory { KadreApplication { requestStop() } },
                stopWhenLastWindowClosed = true,
                KadrePolicies.Default,
            ),
        )

        assertEquals(
            KadreResult.Success(SessionOutcome.Stopped(SessionStopReason.ApplicationRequested)),
            result,
        )
        assertEquals(2, native.trace.size)
        assertEquals(1, native.trace.count { it == "run" })
        assertEquals(1, native.trace.count { it == "stop" })
        assertEquals(1, native.stopCount)
    }

    @Test
    fun nativeStopFailureBecomesASessionOutcomeAndReleasesOwnership() {
        val broker = AppKitProcessBroker()
        val native = FailingStopNativeApplication()
        val provider = AppKitBackendProvider.forTesting(native, broker) { true }
        val executor = Executors.newSingleThreadExecutor()

        try {
            val result = executor.submit<KadreResult<SessionOutcome>> {
                provider.run(
                    DesktopStandaloneRequest(
                        KadreApplicationFactory { KadreApplication { requestStop() } },
                        stopWhenLastWindowClosed = true,
                        KadrePolicies.Default,
                    ),
                )
            }.get(2, TimeUnit.SECONDS)

            assertEquals(
                KadreResult.Success(
                    SessionOutcome.Failed(
                        KadreFailure.PlatformFailure(
                            KadrePlatform.AppKit,
                            "appkit-host",
                            "stop-exception",
                        ),
                    ),
                ),
                result,
            )
            assertEquals(1, native.stopCount)
            assertEquals(1, native.emergencyStopCount)
            assertTrue(broker.tryAcquireStandalone()?.also { it.close() } != null)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun nativeCancellationAfterAdmissionReturnsHostDetachedAndReleasesOwnership() {
        val cancellation = kotlinx.coroutines.CancellationException("cancelled")
        val applicationStarted = CountDownLatch(1)
        val applicationCancelled = CountDownLatch(1)
        val broker = AppKitProcessBroker()
        val native = CancellationNativeApplication(applicationStarted, cancellation)
        val provider = AppKitBackendProvider.forTesting(native, broker) { true }

        // The native loop waits for this application to start, proving that cancellation occurs
        // after session admission and must therefore be represented by the session outcome.
        val result = provider.run(
            DesktopStandaloneRequest(
                KadreApplicationFactory {
                    KadreApplication {
                        applicationStarted.countDown()
                        try {
                            kotlinx.coroutines.awaitCancellation()
                        } finally {
                            applicationCancelled.countDown()
                        }
                    }
                },
                false,
                KadrePolicies.Default,
            ),
        )

        assertEquals(
            KadreResult.Success(SessionOutcome.Stopped(SessionStopReason.HostDetached)),
            result,
        )
        assertTrue(applicationCancelled.await(2, TimeUnit.SECONDS))
        assertEquals(0, native.stopCount)
        assertTrue(broker.tryAcquireStandalone()?.also { it.close() } != null)
    }

    @Test
    fun nativeFailureAfterAdmissionBecomesASessionOutcome() {
        val nativeFailure = IllegalStateException("native")
        val native = RecordingNativeApplication(runFailure = nativeFailure)
        val provider = AppKitBackendProvider.forTesting(native, AppKitProcessBroker()) { true }

        val result = provider.run(
            DesktopStandaloneRequest(
                KadreApplicationFactory { KadreApplication { kotlinx.coroutines.awaitCancellation() } },
                stopWhenLastWindowClosed = false,
                KadrePolicies.Default,
            ),
        )

        assertEquals(
            KadreResult.Success(
                SessionOutcome.Failed(
                    KadreFailure.PlatformFailure(KadrePlatform.AppKit, "appkit-host", "run-exception"),
                ),
            ),
            result,
        )
        assertEquals(0, native.stopCount)
    }

    @Test
    fun applicationFailureStopsTheNativeLoopAndReturnsTheSessionOutcome() {
        val native = StopDrivenNativeApplication()
        val provider = AppKitBackendProvider.forTesting(native, AppKitProcessBroker()) { true }

        val result = provider.run(
            DesktopStandaloneRequest(
                KadreApplicationFactory {
                    KadreApplication { throw IllegalStateException("application") }
                },
                stopWhenLastWindowClosed = false,
                KadrePolicies.Default,
            ),
        )

        assertEquals(
            KadreResult.Success(SessionOutcome.Failed(KadreFailure.ApplicationFailure)),
            result,
        )
        assertEquals(2, native.trace.size)
        assertEquals(1, native.trace.count { it == "run" })
        assertEquals(1, native.trace.count { it == "stop" })
        assertEquals(1, native.stopCount)
    }

    @Test
    fun standaloneOwnershipReturnsBusyAndCanBeReused() {
        val broker = AppKitProcessBroker()
        val first = broker.tryAcquireStandalone()
        assertIs<AppKitProcessBroker.StandaloneLease>(first)
        assertNull(broker.tryAcquireStandalone())

        val provider = AppKitBackendProvider.forTesting(RecordingNativeApplication(), broker) { true }
        assertEquals(
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host)),
            provider.run(
                DesktopStandaloneRequest(
                    KadreApplicationFactory { KadreApplication { } },
                    true,
                    KadrePolicies.Default,
                ),
            ),
        )

        first.close()
        val second = broker.tryAcquireStandalone()
        assertIs<AppKitProcessBroker.StandaloneLease>(second)
        first.close()
        assertNull(broker.tryAcquireStandalone())
        second.close()
        assertTrue(broker.tryAcquireStandalone()?.also { it.close() } != null)
    }

    @Test
    fun sequentialRunsReleaseAllProcessAndSessionState() {
        val native = RecordingNativeApplication()
        val provider = AppKitBackendProvider.forTesting(native, AppKitProcessBroker()) { true }
        val request = DesktopStandaloneRequest(
            KadreApplicationFactory { KadreApplication { kotlinx.coroutines.awaitCancellation() } },
            true,
            KadrePolicies.Default,
        )

        assertEquals(
            KadreResult.Success(SessionOutcome.Stopped(SessionStopReason.HostDetached)),
            provider.run(request),
        )
        assertEquals(
            KadreResult.Success(SessionOutcome.Stopped(SessionStopReason.HostDetached)),
            provider.run(request),
        )
        assertEquals(2, native.runCount)
    }

    @Test
    fun realKffiStandaloneLoopStartsAndStopsOnMacOs() {
        if (!isMacOs()) {
            assertFalse(AppKitBackendProvider().isAvailable())
            return
        }
        val native = KffiAppKitNativeApplication()
        val provider = AppKitBackendProvider.forTesting(
            native,
            AppKitProcessBroker(),
        ) { true }
        val stopRequestedOffMainThread = AtomicBoolean(false)

        val result = provider.run(
            DesktopStandaloneRequest(
                KadreApplicationFactory {
                    KadreApplication {
                        // Cross the native boundary before requesting stop so this test cannot
                        // accidentally exercise only the pre-run pending-stop handoff.
                        withTimeout(5.seconds) {
                            while (!native.isRunning()) yield()
                        }
                        stopRequestedOffMainThread.set(!native.isMainThread())
                        requestStop()
                    }
                },
                true,
                KadrePolicies.Default,
            ),
        )

        assertEquals(
            KadreResult.Success(SessionOutcome.Stopped(SessionStopReason.ApplicationRequested)),
            result,
        )
        assertTrue(stopRequestedOffMainThread.get())
    }

    @Test
    fun realKffiPendingStopIsConsumedOnMacOs() {
        if (!isMacOs()) return
        val native = KffiAppKitNativeApplication()

        // Request before run() on purpose: this proves the pending-stop handoff separately from
        // the active-loop wakeup scenario above.
        val stop = native.requestStop()
        native.run()

        assertEquals(AppKitStopResult.Accepted, stop.await())
    }

    @Test
    fun realKffiLifecycleSourceStopsDeliveringAfterCloseOnMacOs() {
        if (!isMacOs()) return
        val application = ObjCRuntime.autoreleasePool {
            NSApplication(NSApplication.sharedApplication())
        }
        val center = ObjCRuntime.autoreleasePool {
            NSNotificationCenter(NSNotificationCenter.defaultCenter())
        }
        val observed = AtomicReference<AppKitLifecycleSignal?>(null)
        val observation = KffiAppKitLifecycleSource().start { observed.set(it) }

        try {
            center.postAppKitHideNotification(application)
            assertEquals(AppKitLifecycleSignal.DidHide, observed.get())

            observation.close()
            observed.set(null)
            center.postAppKitHideNotification(application)
            assertNull(observed.get())
        } finally {
            observation.close()
        }
    }

    @Test
    fun realKffiNotificationRoutesThroughAnEmbeddedSessionOnMacOs() = kotlinx.coroutines.runBlocking {
        if (!isMacOs()) return@runBlocking
        val application = ObjCRuntime.autoreleasePool {
            NSApplication(NSApplication.sharedApplication())
        }
        val center = ObjCRuntime.autoreleasePool {
            NSNotificationCenter(NSNotificationCenter.defaultCenter())
        }
        val parentScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
        val lifecycle = CompletableDeferred<KadreLifecycle>()
        val provider = AppKitBackendProvider.forTesting(
            NativeLifecycleApplication(),
            AppKitProcessBroker(),
        ) { true }

        try {
            val session = provider.attach(embeddedRequest(parentScope, lifecycle)).requireSession()
            val observed = withTimeout(2.seconds) { lifecycle.await() }

            center.postAppKitNotification("NSApplicationDidHideNotification", application)
            assertEquals(org.graphiks.kadre.application.VisibilityState.Background, observed.state.value.visibility)

            session.close()
            assertEquals(
                SessionOutcome.Stopped(SessionStopReason.HostRequested),
                session.awaitTermination(),
            )
        } finally {
            parentScope.cancel()
        }
    }
}

private class RecordingNativeApplication(
    private val mainThread: Boolean = true,
    private val runFailure: RuntimeException? = null,
) : AppKitNativeApplication {
    var mainThreadCheckCount: Int = 0
        private set
    var runCount: Int = 0
        private set
    var stopCount: Int = 0
        private set

    override fun isMainThread(): Boolean {
        mainThreadCheckCount += 1
        return mainThread
    }

    override fun isRunning(): Boolean = false

    override fun startLifecycleObservation(listener: (AppKitLifecycleSignal) -> Unit): AutoCloseable =
        AutoCloseable { }

    override fun run() {
        runCount += 1
        runFailure?.let { throw it }
    }

    override fun requestStop(): AppKitStopRequest {
        stopCount += 1
        return AppKitStopRequest { AppKitStopResult.Accepted }
    }

    override fun emergencyStop() = Unit
}

private class EmbeddedNativeApplication : AppKitNativeApplication {
    private val observers = mutableListOf<(AppKitLifecycleSignal) -> Unit>()

    val observerCount: Int
        get() = observers.size
    var runCount: Int = 0
        private set
    var stopCount: Int = 0
        private set

    override fun isMainThread(): Boolean = true

    override fun isRunning(): Boolean = true

    override fun startLifecycleObservation(listener: (AppKitLifecycleSignal) -> Unit): AutoCloseable =
        observe(listener)

    override fun run() {
        runCount += 1
    }

    override fun requestStop(): AppKitStopRequest {
        stopCount += 1
        return AppKitStopRequest { AppKitStopResult.Accepted }
    }

    override fun emergencyStop() = Unit

    fun emit(signal: AppKitLifecycleSignal) {
        observers.toList().forEach { it(signal) }
    }

    fun observe(listener: (AppKitLifecycleSignal) -> Unit): AutoCloseable {
        observers += listener
        return AutoCloseable { observers -= listener }
    }
}

private class NativeLifecycleApplication : AppKitNativeApplication {
    private val lifecycleSource = KffiAppKitLifecycleSource()

    override fun isMainThread(): Boolean = true

    override fun isRunning(): Boolean = true

    override fun startLifecycleObservation(listener: (AppKitLifecycleSignal) -> Unit): AutoCloseable =
        lifecycleSource.start(listener)

    override fun run(): Nothing = error("embedded test host must not run an AppKit loop")

    override fun requestStop(): AppKitStopRequest =
        error("embedded test host must not request AppKit stop")

    override fun emergencyStop(): Nothing = error("embedded test host must not stop AppKit")
}

private class StopDrivenNativeApplication : AppKitNativeApplication {
    val trace = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val stop = CountDownLatch(1)
    var stopCount: Int = 0
        private set

    override fun isMainThread(): Boolean = true

    override fun isRunning(): Boolean = false

    override fun startLifecycleObservation(listener: (AppKitLifecycleSignal) -> Unit): AutoCloseable =
        AutoCloseable { }

    override fun run() {
        trace += "run"
        stop.await()
    }

    override fun requestStop(): AppKitStopRequest {
        trace += "stop"
        stopCount += 1
        stop.countDown()
        return AppKitStopRequest { AppKitStopResult.Accepted }
    }

    override fun emergencyStop() {
        stop.countDown()
    }
}

private class CancellationNativeApplication(
    private val applicationStarted: CountDownLatch,
    private val cancellation: kotlinx.coroutines.CancellationException,
) : AppKitNativeApplication {
    var stopCount: Int = 0
        private set

    override fun isMainThread(): Boolean = true

    override fun isRunning(): Boolean = false

    override fun startLifecycleObservation(listener: (AppKitLifecycleSignal) -> Unit): AutoCloseable =
        AutoCloseable { }

    override fun run() {
        check(applicationStarted.await(2, TimeUnit.SECONDS)) { "application did not start" }
        throw cancellation
    }

    override fun requestStop(): AppKitStopRequest {
        stopCount += 1
        return AppKitStopRequest { AppKitStopResult.Accepted }
    }

    override fun emergencyStop() = Unit
}

private class FailingStopNativeApplication : AppKitNativeApplication {
    private val stop = CountDownLatch(1)
    var stopCount: Int = 0
        private set
    var emergencyStopCount: Int = 0
        private set

    override fun isMainThread(): Boolean = true

    override fun isRunning(): Boolean = false

    override fun startLifecycleObservation(listener: (AppKitLifecycleSignal) -> Unit): AutoCloseable =
        AutoCloseable { }

    override fun run() {
        stop.await()
    }

    override fun requestStop(): AppKitStopRequest {
        stopCount += 1
        throw IllegalStateException("native stop")
    }

    override fun emergencyStop() {
        emergencyStopCount += 1
        stop.countDown()
    }
}

private fun isMacOs(): Boolean = System.getProperty("os.name", "").let { name ->
    name.contains("Mac", ignoreCase = true) || name.contains("Darwin", ignoreCase = true)
}

private fun embeddedRequest(
    parentScope: kotlinx.coroutines.CoroutineScope,
    captureLifecycle: CompletableDeferred<KadreLifecycle>,
): DesktopEmbeddedRequest =
    DesktopEmbeddedRequest(
        parentScope,
        KadreApplicationFactory {
            KadreApplication {
                captureLifecycle.complete(lifecycle)
                kotlinx.coroutines.awaitCancellation()
            }
        },
        DesktopIntegrationKind.AppKitMainLoop,
        KadrePolicies.Default,
    )

private fun KadreResult<KadreSession>.requireSession(): KadreSession =
    (this as? KadreResult.Success)?.value ?: error("Expected a Kadre session, got $this")

private suspend fun EmbeddedNativeApplication.awaitObserverCount(expected: Int) {
    withTimeout(2.seconds) {
        while (observerCount != expected) yield()
    }
}

private fun NSNotificationCenter.postAppKitHideNotification(application: NSApplication) {
    postAppKitNotification("NSApplicationDidHideNotification", application)
}

private fun NSNotificationCenter.postAppKitNotification(name: String, application: NSApplication) {
    ObjCRuntime.autoreleasePool {
        postNotificationName_object(
            ObjCRuntime.newNSString(Arena.global(), name),
            application.ptr,
        )
    }
}
