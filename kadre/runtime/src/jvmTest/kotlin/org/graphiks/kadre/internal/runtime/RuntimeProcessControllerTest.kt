package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The session semantics of the ownership-first controller, merged here from the deleted
 * `RuntimeHostControllerTest`: every scenario that still applies to a booted session, driven
 * through [RuntimeProcessController.boot]. Attach-specific races (parent-scope validation and
 * cancellation, host detachment) died with the attach contract; the embedded seams keep their
 * own transitional suite in `RuntimeEmbeddedHostControllerTest`.
 */
class RuntimeProcessControllerTest {
    @Test
    fun normalApplicationObservesRunningThenCompletes() = runProcessTest {
        lateinit var session: KadreSession
        var observedState: SessionState? = null

        session = boot {
            observedState = session.state.value
        }

        assertEquals(SessionState.Starting, session.state.value)
        testScheduler.runCurrent()

        assertEquals(SessionState.Running, observedState)
        assertEquals(SessionState.Terminated(SessionOutcome.Completed), session.state.value)
        assertEquals(SessionOutcome.Completed, session.awaitTermination())
        assertEquals(SessionOutcome.Completed, session.awaitTermination())
    }

    @Test
    fun applicationAndSystemStopsKeepTheirDistinctReasons() = runProcessTest {
        val controller = controller()
        val applicationStop = boot(controller) { requestStop() }
        testScheduler.runCurrent()
        assertEquals(
            SessionOutcome.Stopped(SessionStopReason.ApplicationRequested),
            applicationStop.awaitTermination(),
        )

        var invoked = false
        val systemStop = boot(controller) { invoked = true }
        systemStop.requestStop()
        systemStop.close()
        systemStop.requestStop()
        testScheduler.runCurrent()
        assertFalse(invoked)
        assertEquals(
            SessionOutcome.Stopped(SessionStopReason.SystemTermination),
            systemStop.awaitTermination(),
        )
    }

    @Test
    fun directApplicationJobCancellationHasItsOwnReason() = runProcessTest {
        val session = boot {
            coroutineContext[Job]!!.cancel()
        }

        testScheduler.runCurrent()

        assertEquals(
            SessionOutcome.Stopped(SessionStopReason.ApplicationCancelled),
            session.awaitTermination(),
        )
    }

    @Test
    fun applicationFailureIsStableAndOriginalCauseIsReported() = runProcessTest {
        val failure = IllegalStateException("boom")
        val reported = mutableListOf<Throwable>()
        val session = boot(controller(failureReporter = RuntimeFailureReporter(reported::add))) { throw failure }

        testScheduler.runCurrent()

        assertEquals(SessionOutcome.Failed(KadreFailure.ApplicationFailure), session.awaitTermination())
        assertEquals(listOf<Throwable>(failure), reported)
    }

    @Test
    fun factoryFailureUsesTheSameStableOutcome() = runProcessTest {
        val failure = IllegalArgumentException("factory")
        val reported = mutableListOf<Throwable>()
        val controller = controller(failureReporter = RuntimeFailureReporter(reported::add))
        val session = assertIs<KadreResult.Success<KadreSession>>(
            controller.boot(
                RuntimeBootRequest(
                    platform = KadrePlatform.Fake,
                    applicationFactory = KadreApplicationFactory { throw failure },
                ),
            ),
        ).value

        testScheduler.runCurrent()

        assertEquals(SessionOutcome.Failed(KadreFailure.ApplicationFailure), session.awaitTermination())
        assertEquals(listOf<Throwable>(failure), reported)
    }

    @Test
    fun applicationReturnWaitsForExistingChildrenAndClosesFurtherAdmission() = runProcessTest {
        var childCompleted = false
        lateinit var capturedScope: org.graphiks.kadre.application.KadreScope
        val session = boot {
            capturedScope = this
            launch {
                delay(1.seconds)
                childCompleted = true
            }
        }

        testScheduler.runCurrent()
        assertEquals(SessionState.Running, session.state.value)
        assertFalse(childCompleted)

        testScheduler.advanceTimeBy(1.seconds)
        testScheduler.runCurrent()
        assertTrue(childCompleted)
        assertEquals(SessionOutcome.Completed, session.awaitTermination())

        val lateChild = capturedScope.launch { error("must not run") }
        assertTrue(lateChild.isCancelled)
    }

    @Test
    fun sessionChildCannotAwaitItsOwnTermination() = runProcessTest {
        lateinit var session: KadreSession
        var rejected = false
        session = boot {
            rejected = runCatching { session.awaitTermination() }.exceptionOrNull() is IllegalStateException
            requestStop()
        }

        testScheduler.runCurrent()

        assertTrue(rejected)
        assertEquals(
            SessionOutcome.Stopped(SessionStopReason.ApplicationRequested),
            session.awaitTermination(),
        )
    }

    @Test
    fun sequentialBootsReceiveProcessUniqueSessionIds() = runProcessTest {
        val controller = controller()
        val first = boot(controller) { awaitCancellation() }
        first.requestStop()
        testScheduler.advanceUntilIdle()
        val second = boot(controller) { awaitCancellation() }

        assertFalse(first.id == second.id)

        second.requestStop()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun applicationFailureDuringCancellationOverridesAStopReason() = runProcessTest {
        val failure = IllegalStateException("cleanup")
        val reported = mutableListOf<Throwable>()
        val session = boot(controller(failureReporter = RuntimeFailureReporter(reported::add))) {
            try {
                awaitCancellation()
            } finally {
                throw failure
            }
        }
        testScheduler.runCurrent()

        session.close()
        testScheduler.runCurrent()

        assertEquals(SessionOutcome.Failed(KadreFailure.ApplicationFailure), session.awaitTermination())
        assertEquals(listOf<Throwable>(failure), reported)
    }

    @Test
    fun shutdownTimeoutTerminatesLogicalSessionWhenApplicationDoesNotCooperate() = runProcessTest {
        val blocker = CompletableDeferred<Unit>()
        val timeout = 1.seconds
        val policy = KadrePolicies.Default.copy(
            execution = KadrePolicies.Default.execution.copy(shutdownTimeout = timeout),
        )
        val session = boot(controller(), policy = policy) {
            withContext(NonCancellable) { blocker.await() }
        }
        testScheduler.runCurrent()

        session.close()
        testScheduler.advanceTimeBy(timeout)
        testScheduler.runCurrent()

        assertEquals(
            SessionOutcome.Failed(KadreFailure.ShutdownTimedOut(timeout)),
            session.awaitTermination(),
        )

        blocker.complete(Unit)
        testScheduler.runCurrent()
    }

    @Test
    fun sessionStopHandlerRunsOnceBeforeTerminationAndCanPromoteTheOutcome() = runProcessTest {
        val events = mutableListOf<String>()
        val stopFailure = KadreFailure.PlatformFailure(
            KadrePlatform.AppKit,
            "appkit-host",
            "stop-exception",
        )
        val controller = controller(
            sessionStopHandler = RuntimeSessionStopHandler { id ->
                events += "stopping:$id"
                stopFailure
            },
            sessionObserver = RuntimeSessionObserver { id, outcome ->
                events += "terminated:$id:$outcome"
            },
        )
        val session = boot(controller) { requestStop() }

        testScheduler.runCurrent()

        val outcome: SessionOutcome = SessionOutcome.Failed(stopFailure)
        assertEquals(outcome, session.awaitTermination())
        assertEquals(
            listOf(
                "stopping:${session.id}",
                "terminated:${session.id}:$outcome",
            ),
            events,
        )
    }

    @Test
    fun sessionObserverFailureIsReportedWithoutChangingTheOutcome() = runProcessTest {
        val observerFailure = IllegalStateException("observer")
        val reported = mutableListOf<Throwable>()
        val controller = controller(
            failureReporter = RuntimeFailureReporter(reported::add),
            sessionObserver = RuntimeSessionObserver { _, _ -> throw observerFailure },
        )
        val session = boot(controller) { }

        testScheduler.runCurrent()

        assertEquals(SessionOutcome.Completed, session.awaitTermination())
        assertEquals(listOf<Throwable>(observerFailure), reported)
    }

    @Test
    fun eventCollectorBudgetIsSharedAcrossLifecycleAndDiagnosticsWithinOneSession() = runProcessTest {
        val policy = KadrePolicies.Default.copy(
            resources = KadrePolicies.Default.resources.copy(
                maxEventCollectorsPerFlow = 1,
                maxEventCollectorsPerSession = 1,
            ),
        )
        lateinit var applicationScope: org.graphiks.kadre.application.KadreScope
        val session = boot(controller(), policy = policy) {
            applicationScope = this
            awaitCancellation()
        }
        testScheduler.runCurrent()

        val lifecycleCollector = applicationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            applicationScope.lifecycle.events.collect()
        }
        val diagnosticsCollector = applicationScope.async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { applicationScope.diagnostics.events.collect() }.exceptionOrNull()
        }
        testScheduler.runCurrent()

        try {
            assertTrue(diagnosticsCollector.isCompleted)
            val rejection = assertIs<KadreException>(diagnosticsCollector.await())
            assertEquals(
                KadreFailure.ResourceLimitExceeded(KadreResourceKind.EventCollector, 1),
                rejection.failure,
            )
            lifecycleCollector.cancelAndJoin()
            val admittedAfterRelease = applicationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                applicationScope.diagnostics.events.collect()
            }
            try {
                testScheduler.runCurrent()
                assertTrue(admittedAfterRelease.isActive)
            } finally {
                admittedAfterRelease.cancelAndJoin()
            }
        } finally {
            diagnosticsCollector.cancelAndJoin()
            lifecycleCollector.cancelAndJoin()
            session.close()
            testScheduler.runCurrent()
        }
    }

    private fun controller(
        failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { },
        sessionStopHandler: RuntimeSessionStopHandler = RuntimeSessionStopHandler { null },
        sessionObserver: RuntimeSessionObserver = RuntimeSessionObserver { _, _ -> },
    ): RuntimeProcessController = RuntimeProcessController.withComponents(
        platform = KadrePlatform.Fake,
        componentsFactory = { _, _ ->
            RuntimeSessionComponents(windows = UnsupportedWindowManager(RuntimeProcessIds::nextWindowRequestId))
        },
        failureReporter = failureReporter,
        sessionStopHandler = sessionStopHandler,
        sessionObserver = sessionObserver,
    )

    private suspend fun kotlinx.coroutines.test.TestScope.boot(
        application: suspend org.graphiks.kadre.application.KadreScope.() -> Unit,
    ): KadreSession = boot(controller(), KadrePolicies.Default, application)

    private suspend fun kotlinx.coroutines.test.TestScope.boot(
        controller: RuntimeProcessController,
        policy: KadrePolicy = KadrePolicies.Default,
        application: suspend org.graphiks.kadre.application.KadreScope.() -> Unit,
    ): KadreSession = assertIs<KadreResult.Success<KadreSession>>(
        controller.boot(
            RuntimeBootRequest(
                platform = KadrePlatform.Fake,
                applicationFactory = KadreApplicationFactory { KadreApplication(application) },
                policy = policy,
            ),
        ),
    ).value

    /**
     * The controller's owned root scope lives on `Dispatchers.Main.immediate`; a process test
     * installs the test scheduler as Main so every owned coroutine is driven by virtual time.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun runProcessTest(body: suspend TestScope.() -> Unit): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            body()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
