package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.window.WindowManager
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class RuntimeHostControllerTest {
    @Test
    fun publicConstructorKeepsWindowsUnsupported() = runTest {
        val host = RuntimeHostController(KadrePlatform.Fake)
        lateinit var observed: WindowManager

        val session = attach(host) {
            observed = windows
            requestStop()
        }
        testScheduler.runCurrent()

        assertIs<UnsupportedWindowManager>(observed)
        assertEquals(SessionOutcome.Stopped(SessionStopReason.ApplicationRequested), session.awaitTermination())
    }

    @Test
    fun eventCollectorBudgetIsSharedAcrossLifecycleAndDiagnosticsWithinOneSession() = runTest {
        val host = RuntimeHostController(KadrePlatform.Fake)
        val policy = KadrePolicies.Default.copy(
            resources = KadrePolicies.Default.resources.copy(
                maxEventCollectorsPerFlow = 1,
                maxEventCollectorsPerSession = 1,
            ),
        )
        lateinit var applicationScope: KadreScope
        val session = attach(host, policy = policy) {
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

    @Test
    fun attachRejectsMissingOrInactiveParentJob() {
        val host = RuntimeHostController(KadrePlatform.Fake)
        val application = KadreApplicationFactory { KadreApplication { } }

        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("parentScope")),
            host.attach(
                object : CoroutineScope {
                    override val coroutineContext = EmptyCoroutineContext
                },
                application,
            ),
        )

        val cancelledScope = CoroutineScope(Job().also(Job::cancel))
        assertEquals(
            KadreResult.Failure(KadreFailure.ParentScopeCancelled),
            host.attach(cancelledScope, application),
        )
    }

    @Test
    fun normalApplicationObservesRunningThenCompletes() = runTest {
        val host = RuntimeHostController(KadrePlatform.Fake)
        lateinit var session: KadreSession
        var observedState: SessionState? = null

        session = assertIs<KadreResult.Success<KadreSession>>(
            host.attach(this, KadreApplicationFactory {
                KadreApplication { observedState = session.state.value }
            }),
        ).value

        assertEquals(SessionState.Starting, session.state.value)
        testScheduler.runCurrent()

        assertEquals(SessionState.Running, observedState)
        assertEquals(SessionState.Terminated(SessionOutcome.Completed), session.state.value)
        assertEquals(SessionOutcome.Completed, session.awaitTermination())
        assertEquals(SessionOutcome.Completed, session.awaitTermination())
    }

    @Test
    fun applicationAndHostStopsKeepTheirDistinctReasons() = runTest {
        val host = RuntimeHostController(KadrePlatform.Fake)
        val applicationStop = attach(host) { requestStop() }
        testScheduler.runCurrent()
        assertEquals(
            SessionOutcome.Stopped(SessionStopReason.ApplicationRequested),
            applicationStop.awaitTermination(),
        )

        var invoked = false
        val hostStop = attach(host) { invoked = true }
        hostStop.requestStop()
        hostStop.close()
        hostStop.requestStop()
        testScheduler.runCurrent()
        assertFalse(invoked)
        assertEquals(
            SessionOutcome.Stopped(SessionStopReason.HostRequested),
            hostStop.awaitTermination(),
        )
    }

    @Test
    fun directApplicationJobCancellationHasItsOwnReason() = runTest {
        val host = RuntimeHostController(KadrePlatform.Fake)
        val session = attach(host) {
            coroutineContext[Job]!!.cancel()
        }

        testScheduler.runCurrent()

        assertEquals(
            SessionOutcome.Stopped(SessionStopReason.ApplicationCancelled),
            session.awaitTermination(),
        )
    }

    @Test
    fun parentCancellationTerminatesSessionWithoutReverseCancellation() = runTest {
        val parentJob = SupervisorJob()
        val parentScope = CoroutineScope(parentJob + StandardTestDispatcher(testScheduler))
        val host = RuntimeHostController(KadrePlatform.Fake)
        val session = assertIs<KadreResult.Success<KadreSession>>(
            host.attach(parentScope, KadreApplicationFactory { KadreApplication { kotlinx.coroutines.awaitCancellation() } }),
        ).value

        parentScope.cancel()
        testScheduler.runCurrent()

        assertEquals(
            SessionOutcome.Stopped(SessionStopReason.ParentCancelled),
            session.awaitTermination(),
        )

        val independentParent = SupervisorJob()
        val independentScope = CoroutineScope(independentParent + StandardTestDispatcher(testScheduler))
        val independentSession = attach(host, independentScope) { error("boom") }
        testScheduler.runCurrent()
        assertTrue(independentParent.isActive)
        assertIs<SessionOutcome.Failed>(independentSession.awaitTermination())
        independentParent.cancel()
    }

    @Test
    fun applicationFailureIsStableAndOriginalCauseIsReported() = runTest {
        val failure = IllegalStateException("boom")
        val reported = mutableListOf<Throwable>()
        val host = RuntimeHostController(KadrePlatform.Fake, failureReporter = reported::add)
        val session = attach(host) { throw failure }

        testScheduler.runCurrent()

        assertEquals(SessionOutcome.Failed(KadreFailure.ApplicationFailure), session.awaitTermination())
        assertEquals(listOf<Throwable>(failure), reported)
    }

    @Test
    fun factoryFailureUsesTheSameStableOutcome() = runTest {
        val failure = IllegalArgumentException("factory")
        val reported = mutableListOf<Throwable>()
        val host = RuntimeHostController(KadrePlatform.Fake, failureReporter = RuntimeFailureReporter(reported::add))
        val session = assertIs<KadreResult.Success<KadreSession>>(
            host.attach(this, KadreApplicationFactory { throw failure }),
        ).value

        testScheduler.runCurrent()

        assertEquals(SessionOutcome.Failed(KadreFailure.ApplicationFailure), session.awaitTermination())
        assertEquals(listOf<Throwable>(failure), reported)
    }

    @Test
    fun applicationReturnWaitsForExistingChildrenAndClosesFurtherAdmission() = runTest {
        val host = RuntimeHostController(KadrePlatform.Fake)
        var childCompleted = false
        lateinit var capturedScope: org.graphiks.kadre.application.KadreScope
        val session = attach(host) {
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
    fun sessionChildCannotAwaitItsOwnTermination() = runTest {
        val host = RuntimeHostController(KadrePlatform.Fake)
        lateinit var session: KadreSession
        var rejected = false
        session = attach(host) {
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
    fun sessionsAreIsolatedAndReceiveProcessUniqueIds() = runTest {
        val firstHost = RuntimeHostController(KadrePlatform.Fake)
        val secondHost = RuntimeHostController(KadrePlatform.Fake)
        lateinit var firstJob: Job
        lateinit var secondJob: Job

        val first = attach(firstHost) {
            firstJob = coroutineContext[Job]!!
            awaitCancellation()
        }
        val second = attach(secondHost) {
            secondJob = coroutineContext[Job]!!
            awaitCancellation()
        }
        testScheduler.runCurrent()

        assertFalse(first.id == second.id)
        assertFalse(firstJob === secondJob)

        first.close()
        testScheduler.runCurrent()
        assertEquals(SessionOutcome.Stopped(SessionStopReason.HostRequested), first.awaitTermination())
        assertTrue(secondJob.isActive)

        second.close()
        testScheduler.runCurrent()
    }

    @Test
    fun applicationFailureDuringCancellationOverridesAStopReason() = runTest {
        val failure = IllegalStateException("cleanup")
        val reported = mutableListOf<Throwable>()
        val host = RuntimeHostController(KadrePlatform.Fake, failureReporter = reported::add)
        val session = attach(host) {
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
    fun shutdownTimeoutTerminatesLogicalSessionWhenApplicationDoesNotCooperate() = runTest {
        val blocker = CompletableDeferred<Unit>()
        val timeout = 1.seconds
        val policy = KadrePolicies.Default.copy(
            execution = KadrePolicies.Default.execution.copy(shutdownTimeout = timeout),
        )
        val host = RuntimeHostController(KadrePlatform.Fake)
        val session = attach(host, policy = policy) {
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
    fun platformFailureTerminatesTheSessionAndNotifiesItsObserverOnce() = runTest {
        val observed = mutableListOf<Pair<org.graphiks.kadre.application.SessionId, SessionOutcome>>()
        val host = RuntimeHostController(
            KadrePlatform.AppKit,
            sessionObserver = RuntimeSessionObserver { id, outcome -> observed += id to outcome },
        )
        val session = attach(host) { awaitCancellation() }
        testScheduler.runCurrent()
        val failure = KadreFailure.PlatformFailure(KadrePlatform.AppKit, "appkit-host", "run-failed")

        host.fail(failure)
        host.fail(failure)
        testScheduler.runCurrent()

        val outcome: SessionOutcome = SessionOutcome.Failed(failure)
        assertEquals(outcome, session.awaitTermination())
        assertEquals(listOf(session.id to outcome), observed)
    }

    @Test
    fun sessionStopHandlerRunsOnceBeforeTerminationAndCanPromoteTheOutcome() = runTest {
        val events = mutableListOf<String>()
        val stopFailure = KadreFailure.PlatformFailure(
            KadrePlatform.AppKit,
            "appkit-host",
            "stop-exception",
        )
        val host = RuntimeHostController(
            KadrePlatform.AppKit,
            sessionStopHandler = RuntimeSessionStopHandler { id ->
                events += "stopping:$id"
                stopFailure
            },
            sessionObserver = RuntimeSessionObserver { id, outcome ->
                events += "terminated:$id:$outcome"
            },
        )
        val session = attach(host) { requestStop() }

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
    fun sessionObserverFailureIsReportedWithoutChangingTheOutcome() = runTest {
        val observerFailure = IllegalStateException("observer")
        val reported = mutableListOf<Throwable>()
        val host = RuntimeHostController(
            KadrePlatform.Fake,
            failureReporter = reported::add,
            sessionObserver = RuntimeSessionObserver { _, _ -> throw observerFailure },
        )
        val session = attach(host) { }

        testScheduler.runCurrent()

        assertEquals(SessionOutcome.Completed, session.awaitTermination())
        assertEquals(listOf<Throwable>(observerFailure), reported)
    }

    private fun kotlinx.coroutines.test.TestScope.attach(
        host: RuntimeHostController,
        policy: KadrePolicy = KadrePolicies.Default,
        application: suspend org.graphiks.kadre.application.KadreScope.() -> Unit,
    ): KadreSession = attach(host, this, policy, application)

    private fun attach(
        host: RuntimeHostController,
        scope: CoroutineScope,
        policy: KadrePolicy = KadrePolicies.Default,
        application: suspend org.graphiks.kadre.application.KadreScope.() -> Unit,
    ): KadreSession = assertIs<KadreResult.Success<KadreSession>>(
        host.attach(scope, KadreApplicationFactory { KadreApplication(application) }, policy),
    ).value
}
