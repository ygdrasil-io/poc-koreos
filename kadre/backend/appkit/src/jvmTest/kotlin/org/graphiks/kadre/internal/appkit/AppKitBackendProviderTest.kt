package org.graphiks.kadre.internal.appkit

import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
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
import java.util.ServiceLoader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppKitBackendProviderTest {
    @Test
    fun discoveryAndAvailabilityDoNotTouchTheNativeBridge() {
        val providers = ServiceLoader.load(DesktopBackendProvider::class.java).toList()
        val provider = providers.single { it.backend == DesktopBackendKind.AppKit }

        assertEquals(emptySet(), provider.supportedIntegrations)
        assertEquals(isMacOs(), provider.isAvailable())
    }

    @Test
    fun offMainThreadAndEmbeddedAreRejectedBeforeFactoryCreation() {
        var factoryInvoked = false
        val native = RecordingNativeApplication(mainThread = false)
        val provider = AppKitBackendProvider.forTesting(native, AppKitStandaloneOwnership()) { true }
        val factory = KadreApplicationFactory {
            factoryInvoked = true
            KadreApplication { }
        }

        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("options")),
            provider.run(DesktopStandaloneRequest(factory, true, KadrePolicies.Default)),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach)),
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
    fun unavailableProviderDoesNotTouchTheNativeBridgeOrFactory() {
        var factoryInvoked = false
        val native = RecordingNativeApplication()
        val provider = AppKitBackendProvider.forTesting(native, AppKitStandaloneOwnership()) { false }

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
        val provider = AppKitBackendProvider.forTesting(native, AppKitStandaloneOwnership()) { true }

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
        val ownership = AppKitStandaloneOwnership()
        val native = FailingStopNativeApplication()
        val provider = AppKitBackendProvider.forTesting(native, ownership) { true }
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
            assertTrue(ownership.tryAcquire()?.also { it.close() } != null)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun nativeCancellationAfterAdmissionReturnsHostDetachedAndReleasesOwnership() {
        val cancellation = kotlinx.coroutines.CancellationException("cancelled")
        val applicationStarted = CountDownLatch(1)
        val applicationCancelled = CountDownLatch(1)
        val ownership = AppKitStandaloneOwnership()
        val native = CancellationNativeApplication(applicationStarted, cancellation)
        val provider = AppKitBackendProvider.forTesting(native, ownership) { true }

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
        assertTrue(ownership.tryAcquire()?.also { it.close() } != null)
    }

    @Test
    fun nativeFailureAfterAdmissionBecomesASessionOutcome() {
        val nativeFailure = IllegalStateException("native")
        val native = RecordingNativeApplication(runFailure = nativeFailure)
        val provider = AppKitBackendProvider.forTesting(native, AppKitStandaloneOwnership()) { true }

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
        val provider = AppKitBackendProvider.forTesting(native, AppKitStandaloneOwnership()) { true }

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
        val ownership = AppKitStandaloneOwnership()
        val first = ownership.tryAcquire()
        assertIs<AppKitStandaloneOwnership.Lease>(first)
        assertNull(ownership.tryAcquire())

        val provider = AppKitBackendProvider.forTesting(RecordingNativeApplication(), ownership) { true }
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
        val second = ownership.tryAcquire()
        assertIs<AppKitStandaloneOwnership.Lease>(second)
        first.close()
        assertNull(ownership.tryAcquire())
        second.close()
        assertTrue(ownership.tryAcquire()?.also { it.close() } != null)
    }

    @Test
    fun sequentialRunsReleaseAllProcessAndSessionState() {
        val native = RecordingNativeApplication()
        val provider = AppKitBackendProvider.forTesting(native, AppKitStandaloneOwnership()) { true }
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
            AppKitStandaloneOwnership(),
        ) { true }
        val stopRequestedOffMainThread = AtomicBoolean(false)

        repeat(2) {
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
        }
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

private class StopDrivenNativeApplication : AppKitNativeApplication {
    val trace = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val stop = CountDownLatch(1)
    var stopCount: Int = 0
        private set

    override fun isMainThread(): Boolean = true

    override fun isRunning(): Boolean = false

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
