package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.graphiks.kadre.application.ActivationState
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.LifecycleCapabilities
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.MemoryPressureLevel
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionPhase
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.application.SystemSignal
import org.graphiks.kadre.application.VisibilityState
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RuntimeLifecycleTest {
    @Test
    fun memoryPressureDoesNotPublishAfterSessionStopHasBeenAdmitted() = runProcessTest {
        val stopEntered = CountDownLatch(1)
        val releaseStop = CountDownLatch(1)
        val controller = RuntimeProcessController.withComponents(
            platform = KadrePlatform.Fake,
            componentsFactory = { _, _ ->
                RuntimeSessionComponents(windows = UnsupportedWindowManager(RuntimeProcessIds::nextWindowRequestId))
            },
            initialLifecycleCapabilities = LifecycleCapabilities(FeatureAvailability.Available),
            sessionStopHandler = RuntimeSessionStopHandler {
                stopEntered.countDown()
                check(releaseStop.await(2, TimeUnit.SECONDS)) { "test did not release session stop" }
                null
            },
        )
        lateinit var scope: KadreScope
        val session = boot(controller) {
            scope = this
            awaitCancellation()
        }
        testScheduler.runCurrent()
        val signals = mutableListOf<SystemSignal.MemoryPressure>()
        val collector = backgroundScope.launch {
            scope.lifecycle.signals.filterIsInstance<SystemSignal.MemoryPressure>().collect { signal ->
                signals += signal
            }
        }
        testScheduler.runCurrent()
        val closeReturned = CountDownLatch(1)
        Thread.ofPlatform().start {
            try {
                session.close()
            } finally {
                closeReturned.countDown()
            }
        }
        assertTrue(stopEntered.await(2, TimeUnit.SECONDS))

        controller.emitMemoryPressure(MemoryPressureLevel.Critical)
        testScheduler.runCurrent()
        assertEquals(emptyList(), signals)

        releaseStop.countDown()
        assertTrue(
            withContext(Dispatchers.Default) { closeReturned.await(2, TimeUnit.SECONDS) },
            "session close did not complete after its stop handler was released",
        )
        testScheduler.advanceUntilIdle()
        assertEquals(SessionOutcome.Stopped(SessionStopReason.SystemTermination), session.awaitTermination())
        collector.cancel()
    }

    @Test
    fun lifecyclePublishesSnapshotBeforeEventAndDeduplicates() = runTest {
        val lifecycle = lifecycle()
        val background = LifecycleState(
            SessionPhase.Running,
            VisibilityState.Background,
            ActivationState.Inactive,
        )

        val observations = mutableListOf<Pair<LifecycleState, LifecycleState>>()
        val collector = launch {
            val event = lifecycle.events.first()
            observations += lifecycle.state.value to event.current
        }
        testScheduler.runCurrent()

        lifecycle.updateState(background)
        lifecycle.updateState(background)
        testScheduler.runCurrent()

        assertEquals(listOf(background to background), observations)
        collector.cancel()
    }

    @Test
    fun bootedSessionReceivesTheBootInitialSnapshot() = runProcessTest {
        val background = LifecycleState(
            SessionPhase.Running,
            VisibilityState.Background,
            ActivationState.Inactive,
        )
        val controller = RuntimeProcessController.withComponents(
            platform = KadrePlatform.Fake,
            componentsFactory = { _, _ ->
                RuntimeSessionComponents(windows = UnsupportedWindowManager(RuntimeProcessIds::nextWindowRequestId))
            },
            initialLifecycleState = background,
        )
        lateinit var scope: KadreScope
        val session = boot(controller) {
            scope = this
            awaitCancellation()
        }
        testScheduler.runCurrent()

        assertEquals(background, scope.lifecycle.state.value)
        session.close()
        testScheduler.runCurrent()
    }

    @Test
    fun memoryPressureRequiresCapabilityAndTerminatedStateIsFrozen() = runTest {
        val lifecycle = lifecycle()
        testScheduler.runCurrent()

        assertFailsWith<IllegalArgumentException> { lifecycle.emitMemoryPressure(MemoryPressureLevel.Moderate) }
        lifecycle.updateCapabilities(LifecycleCapabilities(FeatureAvailability.Available))
        val signal = launch {
            assertEquals(
                MemoryPressureLevel.Critical,
                assertIs<SystemSignal.MemoryPressure>(lifecycle.signals.first()).level,
            )
        }
        testScheduler.runCurrent()
        lifecycle.emitMemoryPressure(MemoryPressureLevel.Critical)
        testScheduler.runCurrent()
        signal.join()

        val terminated = LifecycleState(
            SessionPhase.Terminated,
            VisibilityState.Background,
            ActivationState.Inactive,
        )
        assertEquals(terminated, lifecycle.updateState(terminated))
        assertEquals(
            terminated,
            lifecycle.updateState(
                LifecycleState(SessionPhase.Running, VisibilityState.Foreground, ActivationState.Active),
            ),
        )
        assertFailsWith<IllegalArgumentException> { lifecycle.emitMemoryPressure(MemoryPressureLevel.Moderate) }
    }

    @Test
    fun lifecycleEventsAndSignalsShareAStrictlyIncreasingSessionSequence() = runTest {
        var now = Duration.ZERO
        val nextSequence = AtomicLong(0L)
        val lifecycle = lifecycle(
            nextStamp = {
                EventStamp(
                    SessionSequence(nextSequence.incrementAndGet()),
                    SessionInstant(now),
                    null,
                )
            },
        )
        testScheduler.runCurrent()

        val events = mutableListOf<org.graphiks.kadre.application.LifecycleEvent>()
        val signals = mutableListOf<SystemSignal>()
        val eventCollector = launch { lifecycle.events.take(2).toList(events) }
        val signalCollector = launch { lifecycle.signals.take(1).toList(signals) }
        testScheduler.runCurrent()

        now = 2.seconds
        lifecycle.updateState(LifecycleState(SessionPhase.Running, VisibilityState.Background, ActivationState.Inactive))
        now = 5.seconds
        lifecycle.updateState(LifecycleState(SessionPhase.Running, VisibilityState.Foreground, ActivationState.Active))
        lifecycle.updateCapabilities(LifecycleCapabilities(FeatureAvailability.Available))
        now = 8.seconds
        lifecycle.emitMemoryPressure(MemoryPressureLevel.Moderate)
        testScheduler.runCurrent()
        eventCollector.join()
        signalCollector.join()

        val sequences = events.map { it.stamp.sequence.value } + signals.map { it.stamp.sequence.value }
        val timestamps = events.map { it.stamp.timestamp.sinceStart } + signals.map { it.stamp.timestamp.sinceStart }
        assertEquals(3, sequences.distinct().size)
        assertTrue(sequences.zipWithNext().all { (previous, next) -> previous < next })
        assertEquals(listOf(2.seconds, 5.seconds, 8.seconds), timestamps)
    }

    private fun lifecycle(
        nextStamp: () -> EventStamp = {
            EventStamp(SessionSequence(0L), SessionInstant(Duration.ZERO), null)
        },
    ): RuntimeLifecycle = RuntimeLifecycle(
        initialState = LifecycleState(SessionPhase.Running, VisibilityState.Foreground, ActivationState.Active),
        initialCapabilities = LifecycleCapabilities(FeatureAvailability.Unsupported),
        nextStamp = nextStamp,
        collectorAllocator = RuntimeEventCollectorAllocator(Int.MAX_VALUE),
        maxCollectorsPerFlow = Int.MAX_VALUE,
    )

    private suspend fun kotlinx.coroutines.test.TestScope.boot(
        controller: RuntimeProcessController,
        application: suspend KadreScope.() -> Unit,
    ): KadreSession = assertIs<KadreResult.Success<KadreSession>>(
        controller.boot(
            RuntimeBootRequest(
                platform = KadrePlatform.Fake,
                applicationFactory = KadreApplicationFactory { KadreApplication(application) },
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
