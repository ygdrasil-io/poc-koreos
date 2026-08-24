package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.ActivationState
import org.graphiks.kadre.application.AttachmentState
import org.graphiks.kadre.application.HostSignal
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.LifecycleCapabilities
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.MemoryPressureLevel
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.application.VisibilityState
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RuntimeLifecycleTest {
    @Test
    fun lifecyclePublishesSnapshotBeforeEventAndDeduplicates() = runTest {
        val host = RuntimeHostController(KadrePlatform.Fake)
        lateinit var scope: KadreScope
        val session = attach(host) {
            scope = this
            awaitCancellation()
        }
        testScheduler.runCurrent()

        val observations = mutableListOf<Pair<LifecycleState, LifecycleState>>()
        val collector = backgroundScope.launch {
            val event = scope.lifecycle.events.first()
            observations += scope.lifecycle.state.value to event.current
        }
        testScheduler.runCurrent()

        val background = LifecycleState(
            AttachmentState.Attached,
            VisibilityState.Background,
            ActivationState.Inactive,
        )
        host.updateLifecycle(background)
        host.updateLifecycle(background)
        testScheduler.runCurrent()

        assertEquals(listOf(background to background), observations)
        collector.cancel()
        session.close()
        testScheduler.runCurrent()
    }

    @Test
    fun newSessionReceivesCurrentSnapshotWithoutHistoricalEvent() = runTest {
        val host = RuntimeHostController(KadrePlatform.Fake)
        val background = LifecycleState(
            AttachmentState.Attached,
            VisibilityState.Background,
            ActivationState.Inactive,
        )
        host.updateLifecycle(background)

        lateinit var scope: KadreScope
        val session = attach(host) {
            scope = this
            awaitCancellation()
        }
        testScheduler.runCurrent()

        assertEquals(background, scope.lifecycle.state.value)
        session.close()
        testScheduler.runCurrent()
    }

    @Test
    fun memoryPressureRequiresCapabilityAndDetachIsTerminal() = runTest {
        val host = RuntimeHostController(KadrePlatform.Fake)
        lateinit var scope: KadreScope
        val session = attach(host) {
            scope = this
            awaitCancellation()
        }
        testScheduler.runCurrent()

        assertFailsWith<IllegalArgumentException> { host.emitMemoryPressure(MemoryPressureLevel.Moderate) }
        host.updateLifecycleCapabilities(LifecycleCapabilities(FeatureAvailability.Available))
        val signal = backgroundScope.launch {
            assertEquals(
                MemoryPressureLevel.Critical,
                assertIs<HostSignal.MemoryPressure>(scope.lifecycle.signals.first()).level,
            )
        }
        testScheduler.runCurrent()
        host.emitMemoryPressure(MemoryPressureLevel.Critical)
        testScheduler.runCurrent()
        signal.join()

        host.detach()
        testScheduler.runCurrent()
        assertEquals(
            SessionOutcome.Stopped(SessionStopReason.HostDetached),
            session.awaitTermination(),
        )
        assertEquals(AttachmentState.Detached, scope.lifecycle.state.value.attachment)
        assertFailsWith<IllegalArgumentException> {
            host.updateLifecycle(
                LifecycleState(AttachmentState.Attached, VisibilityState.Foreground, ActivationState.Active),
            )
        }
        assertIs<KadreResult.Failure>(
            host.attach(this, KadreApplicationFactory { KadreApplication { } }),
        )
    }

    @Test
    fun controllersDoNotShareLifecycle() = runTest {
        val first = RuntimeHostController(KadrePlatform.Fake)
        val second = RuntimeHostController(KadrePlatform.Fake)
        lateinit var firstScope: KadreScope
        lateinit var secondScope: KadreScope
        val firstSession = attach(first) { firstScope = this; awaitCancellation() }
        val secondSession = attach(second) { secondScope = this; awaitCancellation() }
        testScheduler.runCurrent()

        first.updateLifecycle(
            LifecycleState(AttachmentState.Attached, VisibilityState.Background, ActivationState.Inactive),
        )

        assertEquals(VisibilityState.Background, firstScope.lifecycle.state.value.visibility)
        assertEquals(VisibilityState.Foreground, secondScope.lifecycle.state.value.visibility)
        firstSession.close()
        secondSession.close()
        testScheduler.runCurrent()
    }

    @Test
    fun lifecycleEventsAndSignalsShareAStrictlyIncreasingSessionSequence() = runTest {
        var now = Duration.ZERO
        val host = RuntimeHostController.withClock(
            KadrePlatform.Fake,
            RuntimeClockFactory { RuntimeClock { now } },
        )
        lateinit var scope: KadreScope
        val session = attach(host) { scope = this; awaitCancellation() }
        testScheduler.runCurrent()

        val events = mutableListOf<org.graphiks.kadre.application.LifecycleEvent>()
        val signals = mutableListOf<HostSignal>()
        val eventCollector = backgroundScope.launch { scope.lifecycle.events.take(2).toList(events) }
        val signalCollector = backgroundScope.launch { scope.lifecycle.signals.take(1).toList(signals) }
        testScheduler.runCurrent()

        now = 2.seconds
        host.updateLifecycle(
            LifecycleState(AttachmentState.Attached, VisibilityState.Background, ActivationState.Inactive),
        )
        now = 5.seconds
        host.updateLifecycle(
            LifecycleState(AttachmentState.Attached, VisibilityState.Foreground, ActivationState.Active),
        )
        host.updateLifecycleCapabilities(LifecycleCapabilities(FeatureAvailability.Available))
        now = 8.seconds
        host.emitMemoryPressure(MemoryPressureLevel.Moderate)
        testScheduler.runCurrent()
        eventCollector.join()
        signalCollector.join()

        val sequences = events.map { it.stamp.sequence.value } + signals.map { it.stamp.sequence.value }
        val timestamps = events.map { it.stamp.timestamp.sinceStart } + signals.map { it.stamp.timestamp.sinceStart }
        assertEquals(3, sequences.distinct().size)
        assertTrue(sequences.zipWithNext().all { (previous, next) -> previous < next })
        assertEquals(listOf(2.seconds, 5.seconds, 8.seconds), timestamps)

        session.close()
        testScheduler.runCurrent()
    }

    private fun kotlinx.coroutines.test.TestScope.attach(
        host: RuntimeHostController,
        application: suspend KadreScope.() -> Unit,
    ): KadreSession = assertIs<KadreResult.Success<KadreSession>>(
        host.attach(this, KadreApplicationFactory { KadreApplication(application) }),
    ).value
}
