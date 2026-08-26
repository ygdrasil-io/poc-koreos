package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.window.WindowManager
import org.graphiks.kadre.window.WindowManagerCapabilities
import org.graphiks.kadre.window.WindowManagerRevision
import org.graphiks.kadre.window.WindowManagerState
import org.graphiks.kadre.window.WindowRequest
import org.graphiks.kadre.window.WindowSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertIs
import kotlin.test.assertTrue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RuntimeSessionComponentsTest {
    @Test
    fun injectedWindowManagerIsVisibleThroughItsSessionScope() = runTest {
        val manager = RecordingWindowManager()
        var factoryScopeJob: Job? = null
        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.Fake,
            componentsFactory = RuntimeSessionComponentsFactory { _, rootScope ->
                factoryScopeJob = rootScope.coroutineContext[Job]
                RuntimeSessionComponents(manager)
            },
        )
        lateinit var observed: WindowManager

        val session = attach(host) {
            observed = windows
            awaitCancellation()
        }
        testScheduler.runCurrent()

        assertSame(manager, observed)
        assertTrue(factoryScopeJob!!.isActive)
        assertFalse(factoryScopeJob === coroutineContext[Job])

        session.close()
        testScheduler.runCurrent()
    }

    @Test
    fun sessionComponentsAreIsolatedAndCloseOnlyTheirSessionExactlyOnce() = runTest {
        val components = mutableListOf<RecordingComponent>()
        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.Fake,
            componentsFactory = RuntimeSessionComponentsFactory { _, _ ->
                RecordingComponent().also(components::add).asRuntimeComponent()
            },
        )
        lateinit var firstWindows: WindowManager
        lateinit var secondWindows: WindowManager

        val first = attach(host) {
            firstWindows = windows
            awaitCancellation()
        }
        val second = attach(host) {
            secondWindows = windows
            awaitCancellation()
        }
        testScheduler.runCurrent()

        assertEquals(2, components.size)
        assertSame(components[0].manager, firstWindows)
        assertSame(components[1].manager, secondWindows)
        assertFalse(firstWindows === secondWindows)

        first.close()
        first.close()
        testScheduler.runCurrent()

        assertEquals(1, components[0].closeCount)
        assertEquals(0, components[1].closeCount)

        second.close()
        testScheduler.runCurrent()

        assertEquals(1, components[0].closeCount)
        assertEquals(1, components[1].closeCount)
    }

    @Test
    fun sessionComponentsCloseBeforeHostTerminationIsObserved() = runTest {
        val events = mutableListOf<String>()
        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.Fake,
            componentsFactory = RuntimeSessionComponentsFactory { _, _ ->
                RuntimeSessionComponents(RecordingWindowManager()) { events += "components-closed" }
            },
            sessionObserver = RuntimeSessionObserver { _, _ -> events += "host-observed-termination" },
        )

        attach(host) { requestStop() }
        testScheduler.runCurrent()

        assertEquals(listOf("components-closed", "host-observed-termination"), events)
    }

    @Test
    fun terminationStateAndAwaitBecomeVisibleOnlyAfterComponentsClose() = runTest {
        val events = mutableListOf<String>()
        lateinit var session: KadreSession
        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.Fake,
            componentsFactory = RuntimeSessionComponentsFactory { _, _ ->
                RuntimeSessionComponents(RecordingWindowManager()) {
                    assertEquals(SessionState.Stopping, session.state.value)
                    events += "components-closed"
                }
            },
        )

        session = attach(host) { awaitCancellation() }
        testScheduler.runCurrent()
        val waiter = async {
            session.awaitTermination().also { events += "await-returned" }
        }
        testScheduler.runCurrent()

        session.close()
        testScheduler.runCurrent()

        assertIs<SessionState.Terminated>(session.state.value)
        assertEquals(SessionOutcome.Stopped(org.graphiks.kadre.application.SessionStopReason.HostRequested), waiter.await())
        assertEquals(listOf("components-closed", "await-returned"), events)
    }

    @Test
    fun componentFactoryFailureIsReportedAndDoesNotEscapeAttach() = runTest {
        val failure = IllegalStateException("components")
        val reported = mutableListOf<Throwable>()
        val componentScopeJobs = mutableListOf<Job>()
        var observedTerminations = 0
        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.AppKit,
            componentsFactory = RuntimeSessionComponentsFactory { _, rootScope ->
                componentScopeJobs += checkNotNull(rootScope.coroutineContext[Job])
                throw failure
            },
            failureReporter = RuntimeFailureReporter(reported::add),
            sessionObserver = RuntimeSessionObserver { _, _ -> observedTerminations += 1 },
        )

        val result = host.attach(this, KadreApplicationFactory { KadreApplication { } })

        assertEquals(
            KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.AppKit, "runtime-session-components", "create-failed"),
            ),
            result,
        )
        assertEquals(listOf<Throwable>(failure), reported)
        assertEquals(1, componentScopeJobs.size)
        assertFalse(componentScopeJobs.single().isActive)

        host.detach()
        assertEquals(0, observedTerminations)
    }

    @Test
    fun attachRaceWithHostDetachmentDisposesUnstartedComponentsWithoutNotification() {
        val factoryStarted = java.util.concurrent.CountDownLatch(1)
        val allowFactoryToComplete = java.util.concurrent.CountDownLatch(1)
        val componentScopeJobs = mutableListOf<Job>()
        var closedComponents = 0
        var observedTerminations = 0
        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.Fake,
            componentsFactory = RuntimeSessionComponentsFactory { _, rootScope ->
                componentScopeJobs += checkNotNull(rootScope.coroutineContext[Job])
                factoryStarted.countDown()
                check(allowFactoryToComplete.await(2, TimeUnit.SECONDS))
                RuntimeSessionComponents(RecordingWindowManager()) { closedComponents += 1 }
            },
            sessionObserver = RuntimeSessionObserver { _, _ -> observedTerminations += 1 },
        )
        val parentScope = CoroutineScope(SupervisorJob())
        val executor = Executors.newFixedThreadPool(2)

        try {
            val attach = executor.submit<KadreResult<KadreSession>> {
                host.attach(parentScope, KadreApplicationFactory { KadreApplication { } })
            }
            assertTrue(factoryStarted.await(2, TimeUnit.SECONDS))

            val detach = executor.submit { host.detach() }
            detach.get(2, TimeUnit.SECONDS)
            allowFactoryToComplete.countDown()

            assertEquals(
                KadreResult.Failure(KadreFailure.Closed(org.graphiks.kadre.diagnostics.KadreResourceKind.Host)),
                attach.get(2, TimeUnit.SECONDS),
            )
            assertEquals(1, closedComponents)
            assertFalse(componentScopeJobs.single().isActive)
            assertEquals(0, observedTerminations)
        } finally {
            allowFactoryToComplete.countDown()
            parentScope.coroutineContext[Job]?.cancel()
            executor.shutdownNow()
        }
    }

    private fun kotlinx.coroutines.test.TestScope.attach(
        host: RuntimeHostController,
        application: suspend KadreScope.() -> Unit,
    ): KadreSession = assertIsSuccess(host.attach(this, KadreApplicationFactory { KadreApplication(application) }))

    private fun assertIsSuccess(result: KadreResult<KadreSession>): KadreSession = when (result) {
        is KadreResult.Success -> result.value
        is KadreResult.Failure -> error("expected attached session, got ${result.reason}")
    }

    private class RecordingComponent {
        val manager = RecordingWindowManager()
        var closeCount = 0

        fun asRuntimeComponent(): RuntimeSessionComponents =
            RuntimeSessionComponents(manager) { closeCount += 1 }
    }

    private class RecordingWindowManager : WindowManager {
        private val mutableState = MutableStateFlow(
            WindowManagerState(
                primary = null,
                windows = emptyList(),
                capabilities = WindowManagerCapabilities(
                    Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RequestWindow)),
                ),
                revision = WindowManagerRevision(0),
            ),
        )

        override val state: StateFlow<WindowManagerState> = mutableState

        override suspend fun requestWindow(spec: WindowSpec): KadreResult<WindowRequest> =
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RequestWindow))
    }
}
