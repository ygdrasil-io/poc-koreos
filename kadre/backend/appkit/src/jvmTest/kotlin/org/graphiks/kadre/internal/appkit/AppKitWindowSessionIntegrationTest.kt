package org.graphiks.kadre.internal.appkit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.internal.runtime.SurfaceMetrics
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kadre.window.WindowRequestOutcome
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class AppKitWindowSessionIntegrationTest {
    @Test
    fun surfaceStimuliStayWithTheDriverThatOpenedTheirPeer() = runBlocking {
        val firstPort = DeterministicAppKitNativeWindowPort(
            "first-surface-session",
            initialSurfaceSnapshot = deterministicSurfaceSnapshot(LogicalSize(200.0, 100.0), 1.0),
        )
        val secondPort = DeterministicAppKitNativeWindowPort(
            "second-surface-session",
            initialSurfaceSnapshot = deterministicSurfaceSnapshot(LogicalSize(300.0, 150.0), 2.0),
        )
        val first = AppKitWindowRuntimeDriverFactory { firstPort }.create(KadrePolicies.Default.resources)
        val second = AppKitWindowRuntimeDriverFactory { secondPort }.create(KadrePolicies.Default.resources)

        try {
            val firstWindow = assertNotNull(
                first.manager.requestWindow(WindowSpec(title = "first-surface"))
                    .appKitSuccessValue()
                    .await() as? WindowRequestOutcome.OpenedHere,
            ).window
            val secondWindow = assertNotNull(
                second.manager.requestWindow(WindowSpec(title = "second-surface"))
                    .appKitSuccessValue()
                    .await() as? WindowRequestOutcome.OpenedHere,
            ).window
            val firstResize = deterministicMetrics(LogicalSize(640.0, 360.0), 2.0)
            val secondResize = deterministicMetrics(LogicalSize(800.0, 450.0), 1.0)

            firstPort.emitSurfaceMetrics("first-surface", firstResize)
            secondPort.emitSurfaceMetrics("second-surface", secondResize)
            firstPort.emitSurfaceRedrawConsumed("first-surface", 7L)
            secondPort.emitSurfaceRedrawConsumed("second-surface", 11L)

            withTimeout(2.seconds) {
                firstWindow.surface.state.first { it.logicalSize == LogicalSize(640.0, 360.0) }
                secondWindow.surface.state.first { it.logicalSize == LogicalSize(800.0, 450.0) }
            }
            assertEquals(firstResize, firstWindow.surface.state.value.metrics())
            assertEquals(secondResize, secondWindow.surface.state.value.metrics())
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun closingAWindowRejectsEveryLateSurfaceStimulus() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort("late-surface")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(KadrePolicies.Default.resources)
        val resizeBeforeClose = deterministicMetrics(LogicalSize(640.0, 360.0), 2.0)
        val resizeAfterClose = deterministicMetrics(LogicalSize(800.0, 450.0), 1.0)

        try {
            val window = assertNotNull(
                driver.manager.requestWindow(WindowSpec(title = "late-surface"))
                    .appKitSuccessValue()
                    .await() as? WindowRequestOutcome.OpenedHere,
            ).window
            port.emitSurfaceMetrics("late-surface", resizeBeforeClose)
            withTimeout(2.seconds) {
                window.surface.state.first { it.logicalSize == LogicalSize(640.0, 360.0) }
            }

            port.emitNativeClosed("late-surface")
            withTimeout(2.seconds) {
                driver.manager.state.first { it.windows.isEmpty() }
            }
            port.forceLateSurfaceMetrics("late-surface", resizeAfterClose)
            yield()

            assertEquals(resizeBeforeClose, window.surface.state.value.metrics())
        } finally {
            driver.close()
        }
    }

    @Test
    fun nativeSurfaceCallbackDoesNotWaitForTheRuntimeOrAppKitOwner() = runBlocking {
        val port = OwnerThreadAppKitNativeWindowPort("callback-isolation")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(KadrePolicies.Default.resources)

        try {
            val window = assertNotNull(
                driver.manager.requestWindow(WindowSpec(title = "callback-isolation"))
                    .appKitSuccessValue()
                    .await() as? WindowRequestOutcome.OpenedHere,
            ).window
            val callback = port.submitOnOwnerThread {
                port.emitSurfaceMetrics(
                    "callback-isolation",
                    deterministicMetrics(LogicalSize(640.0, 360.0), 2.0),
                )
            }

            callback.get(2, TimeUnit.SECONDS)
            withTimeout(2.seconds) {
                window.surface.state.first { it.logicalSize == LogicalSize(640.0, 360.0) }
            }
            Unit
        } finally {
            driver.close()
            port.close()
        }
    }

    @Test
    fun inFlightRedrawAcknowledgementDoesNotDelayDriverTeardown() = runBlocking {
        val port = OwnerThreadAppKitNativeWindowPort("redraw-teardown")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(KadrePolicies.Default.resources)

        try {
            driver.manager.requestWindow(WindowSpec(title = "redraw-teardown"))
                .appKitSuccessValue()
                .await()
            val acknowledgement = port.submitOnOwnerThread {
                port.emitSurfaceRedrawConsumed("redraw-teardown", 17L)
            }
            val close = async(Dispatchers.Default) { driver.close() }

            acknowledgement.get(2, TimeUnit.SECONDS)
            withTimeout(2.seconds) { close.await() }
            assertEquals(emptyList(), driver.manager.state.value.windows)
        } finally {
            driver.close()
            port.close()
        }
    }

    @Test
    fun embeddedDriversSharingOneBrokerKeepPeersStimuliAndTeardownSessionLocal() = runBlocking {
        val broker = AppKitProcessBroker()
        val firstRegistration = assertNotNull(broker.createEmbeddedHost(::PassiveLifecycleTarget))
        val secondRegistration = assertNotNull(broker.createEmbeddedHost(::PassiveLifecycleTarget))
        val firstPort = DeterministicAppKitNativeWindowPort("first-session")
        val secondPort = DeterministicAppKitNativeWindowPort("second-session")
        val first = AppKitWindowRuntimeDriverFactory { firstPort }.create(KadrePolicies.Default.resources)
        val second = AppKitWindowRuntimeDriverFactory { secondPort }.create(KadrePolicies.Default.resources)

        try {
            first.manager.requestWindow(WindowSpec(title = "first-window")).appKitSuccessValue().await()
            second.manager.requestWindow(WindowSpec(title = "second-window")).appKitSuccessValue().await()

            assertEquals(listOf(AppKitWindowPeerId(0L)), firstPort.createdPeerIds)
            assertEquals(listOf(AppKitWindowPeerId(0L)), secondPort.createdPeerIds)

            assertFalse(firstPort.requestNativeClose("first-window"))
            assertEquals(1, first.manager.state.value.windows.size)
            assertEquals(1, second.manager.state.value.windows.size)

            firstPort.emitNativeClosed("first-window")

            withTimeout(2.seconds) {
                first.manager.state.first { state -> state.windows.isEmpty() }
                while (firstPort.closedWindowTitles != listOf("first-window")) yield()
            }

            assertEquals(emptyList(), first.manager.state.value.windows)
            assertEquals(1, second.manager.state.value.windows.size)
            assertEquals(listOf("first-window"), firstPort.closedWindowTitles)
            assertEquals(emptyList(), secondPort.closedWindowTitles)

            first.close()
            firstRegistration.close()

            assertEquals(1, second.manager.state.value.windows.size)
            assertEquals(emptyList(), secondPort.closedWindowTitles)
        } finally {
            first.close()
            firstRegistration.close()
            second.close()
            secondRegistration.close()
        }
    }

    @Test
    fun standaloneDriverDoesNotAcquireOrReleaseTheProcessLease() = runBlocking {
        val broker = AppKitProcessBroker()
        val lease = assertNotNull(broker.tryAcquireStandalone())
        val firstPort = DeterministicAppKitNativeWindowPort("standalone-first")
        val first = AppKitWindowRuntimeDriverFactory { firstPort }.create(KadrePolicies.Default.resources)

        first.manager.requestWindow(WindowSpec(title = "standalone-window")).appKitSuccessValue().await()
        first.close()

        assertEquals(listOf("standalone-window"), firstPort.closedWindowTitles)
        assertNull(broker.tryAcquireStandalone())

        lease.close()
        val replacementLease = assertNotNull(broker.tryAcquireStandalone())
        val secondPort = DeterministicAppKitNativeWindowPort("standalone-second")
        val second = AppKitWindowRuntimeDriverFactory { secondPort }.create(KadrePolicies.Default.resources)
        try {
            second.manager.requestWindow(WindowSpec(title = "replacement-window"))
                .appKitSuccessValue()
                .await()
            firstPort.emitNativeClosed("standalone-window")

            assertEquals(listOf(AppKitWindowPeerId(0L)), firstPort.createdPeerIds)
            assertEquals(listOf(AppKitWindowPeerId(0L)), secondPort.createdPeerIds)
            assertEquals(1, second.manager.state.value.windows.size)
            assertEquals(emptyList(), secondPort.closedWindowTitles)
        } finally {
            second.close()
            replacementLease.close()
        }
    }
}

private fun deterministicMetrics(logicalSize: LogicalSize, scaleFactor: Double): SurfaceMetrics =
    deterministicSurfaceSnapshot(logicalSize, scaleFactor).metrics

private fun org.graphiks.kadre.surface.SurfaceState.metrics(): SurfaceMetrics = SurfaceMetrics(
    logicalSize = logicalSize,
    physicalSize = physicalSize,
    scaleFactor = scaleFactor,
    safeAreaInsets = safeAreaInsets,
)

private class PassiveLifecycleTarget(
    initialState: LifecycleState,
) : AppKitLifecycleTarget {
    var state: LifecycleState = initialState
        private set

    override fun updateLifecycle(state: LifecycleState) {
        this.state = state
    }

    override fun detach() = Unit
}
