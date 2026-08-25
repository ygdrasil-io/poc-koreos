package org.graphiks.kadre.internal.appkit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.graphiks.kadre.application.ActivationState
import org.graphiks.kadre.application.AttachmentState
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreLifecycle
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.application.VisibilityState
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeHostController
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class AppKitProcessBrokerTest {
    @Test
    fun lifecycleSignalsReachEveryRegisteredEmbeddedHostAndTerminationStaysIsolated() = runBlocking {
        val broker = AppKitProcessBroker()
        val firstRegistration = broker.newEmbeddedHost()
        val secondRegistration = broker.newEmbeddedHost()
        val firstHost = firstRegistration.host.controller
        val secondHost = secondRegistration.host.controller
        val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val firstLifecycle = CompletableDeferred<KadreLifecycle>()
        val secondLifecycle = CompletableDeferred<KadreLifecycle>()

        try {
            val firstSession = firstHost.attachSession(parentScope) { firstLifecycle.complete(it) }
            val secondSession = secondHost.attachSession(parentScope) { secondLifecycle.complete(it) }
            val first = withTimeout(2.seconds) { firstLifecycle.await() }
            val second = withTimeout(2.seconds) { secondLifecycle.await() }

            broker.accept(AppKitLifecycleSignal.DidHide)

            val hidden = LifecycleState(
                AttachmentState.Attached,
                VisibilityState.Background,
                ActivationState.Inactive,
            )
            assertEquals(hidden, first.state.value)
            assertEquals(hidden, second.state.value)

            firstSession.close()
            assertEquals(
                SessionOutcome.Stopped(SessionStopReason.HostRequested),
                firstSession.awaitTermination(),
            )
            firstRegistration.close()
            firstRegistration.close()

            broker.accept(AppKitLifecycleSignal.HostTerminated)

            assertEquals(
                SessionOutcome.Stopped(SessionStopReason.HostDetached),
                secondSession.awaitTermination(),
            )
            assertEquals(
                LifecycleState(
                    AttachmentState.Detached,
                    VisibilityState.Background,
                    ActivationState.Inactive,
                ),
                second.state.value,
            )
            secondRegistration.close()
        } finally {
            parentScope.cancel()
        }
    }

    @Test
    fun lifecycleSignalsRespectTheLifecycleStateInvariants() = runBlocking {
        val broker = AppKitProcessBroker()
        val registration = broker.newEmbeddedHost()
        val host = registration.host.controller
        val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val lifecycle = CompletableDeferred<KadreLifecycle>()

        try {
            val session = host.attachSession(parentScope) { lifecycle.complete(it) }
            val observed = withTimeout(2.seconds) { lifecycle.await() }

            broker.accept(AppKitLifecycleSignal.BecameInactive)
            assertEquals(
                LifecycleState(
                    AttachmentState.Attached,
                    VisibilityState.Foreground,
                    ActivationState.Inactive,
                ),
                observed.state.value,
            )

            broker.accept(AppKitLifecycleSignal.DidHide)
            broker.accept(AppKitLifecycleSignal.DidUnhide)
            assertEquals(
                LifecycleState(
                    AttachmentState.Attached,
                    VisibilityState.Foreground,
                    ActivationState.Inactive,
                ),
                observed.state.value,
            )

            broker.accept(AppKitLifecycleSignal.BecameActive)
            assertEquals(
                LifecycleState(
                    AttachmentState.Attached,
                    VisibilityState.Foreground,
                    ActivationState.Active,
                ),
                observed.state.value,
            )

            session.close()
            session.awaitTermination()
            registration.close()
        } finally {
            parentScope.cancel()
        }
    }

    @Test
    fun standaloneAndEmbeddedOwnershipAreMutuallyExclusiveAndReusable() {
        val broker = AppKitProcessBroker()

        val standalone = assertNotNull(broker.tryAcquireStandalone())
        assertNull(broker.createEmbeddedHost(::RecordingLifecycleTarget))
        standalone.close()
        standalone.close()

        val embedded = assertNotNull(broker.createEmbeddedHost(::RecordingLifecycleTarget))
        assertNull(broker.tryAcquireStandalone())
        embedded.close()
        embedded.close()

        assertNotNull(broker.tryAcquireStandalone()).close()
    }

    @Test
    fun terminatedProcessRejectsAllFurtherOwnership() {
        val broker = AppKitProcessBroker()

        broker.accept(AppKitLifecycleSignal.HostTerminated)

        assertNull(broker.tryAcquireStandalone())
        assertNull(broker.createEmbeddedHost(::RecordingLifecycleTarget))
    }

    @Test
    fun embeddedCreationAndLifecycleDeliveryHaveOneObservableOrder() {
        val broker = AppKitProcessBroker()
        val factoryEntered = CountDownLatch(1)
        val allowFactoryToComplete = CountDownLatch(1)
        val signalStarted = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val registrationFuture = executor.submit<AppKitProcessBroker.EmbeddedRegistration<RecordingLifecycleTarget>?> {
                broker.createEmbeddedHost { initial ->
                    factoryEntered.countDown()
                    check(allowFactoryToComplete.await(2, TimeUnit.SECONDS))
                    RecordingLifecycleTarget(initial)
                }
            }
            assertTrue(factoryEntered.await(2, TimeUnit.SECONDS))

            val hideFuture = executor.submit {
                signalStarted.countDown()
                broker.accept(AppKitLifecycleSignal.DidHide)
            }
            assertTrue(signalStarted.await(2, TimeUnit.SECONDS))

            allowFactoryToComplete.countDown()
            val registration = requireNotNull(registrationFuture.get(2, TimeUnit.SECONDS))
            val host = registration.host
            hideFuture.get(2, TimeUnit.SECONDS)

            assertEquals(
                LifecycleState(
                    AttachmentState.Attached,
                    VisibilityState.Background,
                    ActivationState.Inactive,
                ),
                host.state,
            )
            registration.close()
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun lifecycleDeliveryFinishesBeforeHostTerminationDetachesTheHost() {
        val broker = AppKitProcessBroker()
        val host = BlockingLifecycleTarget()
        val registration = assertNotNull(broker.createEmbeddedHost { host })
        val terminationStarted = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val hideFuture = executor.submit { broker.accept(AppKitLifecycleSignal.DidHide) }
            assertTrue(host.lifecycleUpdateEntered.await(2, TimeUnit.SECONDS))

            val terminationFuture = executor.submit {
                terminationStarted.countDown()
                broker.accept(AppKitLifecycleSignal.HostTerminated)
            }
            assertTrue(terminationStarted.await(2, TimeUnit.SECONDS))
            assertFalse(host.detached.await(100, TimeUnit.MILLISECONDS))

            host.allowLifecycleUpdate.countDown()
            hideFuture.get(2, TimeUnit.SECONDS)
            terminationFuture.get(2, TimeUnit.SECONDS)
            assertTrue(host.detached.await(2, TimeUnit.SECONDS))
        } finally {
            registration.close()
            executor.shutdownNow()
        }
    }
}

private fun AppKitProcessBroker.newEmbeddedHost(): AppKitProcessBroker.EmbeddedRegistration<AppKitRuntimeHost> =
    assertNotNull(
        createEmbeddedHost { initial ->
            AppKitRuntimeHost(
                RuntimeHostController(
                    platform = KadrePlatform.AppKit,
                    initialLifecycleState = initial,
                ),
            )
        },
    )

private class RecordingLifecycleTarget(
    initial: LifecycleState,
) : AppKitLifecycleTarget {
    var state: LifecycleState = initial
        private set

    override fun updateLifecycle(state: LifecycleState) {
        this.state = state
    }

    override fun detach() = Unit
}

private class BlockingLifecycleTarget : AppKitLifecycleTarget {
    val lifecycleUpdateEntered = CountDownLatch(1)
    val allowLifecycleUpdate = CountDownLatch(1)
    val detached = CountDownLatch(1)

    override fun updateLifecycle(state: LifecycleState) {
        lifecycleUpdateEntered.countDown()
        check(allowLifecycleUpdate.await(2, TimeUnit.SECONDS))
        check(detached.count == 1L) { "Lifecycle delivery raced host detachment" }
    }

    override fun detach() {
        detached.countDown()
    }
}

private fun RuntimeHostController.attachSession(
    parentScope: CoroutineScope,
    captureLifecycle: (KadreLifecycle) -> Unit,
): KadreSession = assertNotNull(
    (attach(
        parentScope,
        KadreApplicationFactory {
            KadreApplication {
                captureLifecycle(lifecycle)
                awaitCancellation()
            }
        },
    ) as? KadreResult.Success<KadreSession>)?.value,
)
