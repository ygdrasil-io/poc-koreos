package org.graphiks.kadre.internal.appkit

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.window.WindowSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class AppKitWindowSessionIntegrationTest {
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
