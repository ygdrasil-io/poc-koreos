package org.graphiks.kadre.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class KadrePoliciesTest {
    @Test
    fun defaultProfileMatchesTheClosedSpecification() {
        assertEquals(
            expectedPolicy(
                priority = ExecutionPriority.Balanced,
                shutdownSeconds = 5,
                discreteCapacity = 256,
                discreteIngress = IngressOverflowAction.CloseSource,
                discreteCollector = CollectorOverflowAction.CancelSlowCollector,
                lifecycleCollector = CollectorOverflowAction.CancelSlowCollector,
                continuous = ContinuousDelivery.Coalesced,
                hostSignals = ContinuousDelivery.Latest,
                gamepadChanges = ContinuousDelivery.Latest,
                frames = FrameDelivery.Latest,
                frameBytes = 134_217_728,
                routing = GamepadRouting.ActiveSessionOnly,
                diagnosticCapacity = 256,
                resources = ResourceBudgetPolicy(
                    16, 128, 16, 16, 16, 4, 16, 4, 262_144, 30.seconds,
                    33_554_432, 1_048_576, 4_096, 4_096, 16_777_216,
                ),
            ),
            KadrePolicies.Default,
        )
    }

    @Test
    fun realtimeProfileMatchesTheClosedSpecification() {
        assertEquals(
            expectedPolicy(
                priority = ExecutionPriority.LatencyFirst,
                shutdownSeconds = 2,
                discreteCapacity = 64,
                discreteIngress = IngressOverflowAction.CloseSource,
                discreteCollector = CollectorOverflowAction.CancelSlowCollector,
                lifecycleCollector = CollectorOverflowAction.CancelSlowCollector,
                continuous = ContinuousDelivery.Coalesced,
                hostSignals = ContinuousDelivery.Latest,
                gamepadChanges = ContinuousDelivery.Latest,
                frames = FrameDelivery.Latest,
                frameBytes = 67_108_864,
                routing = GamepadRouting.ActiveSessionOnly,
                diagnosticCapacity = 64,
                resources = ResourceBudgetPolicy(
                    8, 64, 8, 8, 8, 2, 8, 2, 65_536, 5.seconds,
                    8_388_608, 262_144, 2_048, 2_048, 4_194_304,
                ),
            ),
            KadrePolicies.Realtime,
        )
    }

    @Test
    fun recordingProfileMatchesTheClosedSpecification() {
        val recordingContinuous =
            ContinuousDelivery.Buffered(8192, ContinuousOverflowAction.FailSession)
        assertEquals(
            expectedPolicy(
                priority = ExecutionPriority.Throughput,
                shutdownSeconds = 30,
                discreteCapacity = 8192,
                discreteIngress = IngressOverflowAction.FailSession,
                discreteCollector = CollectorOverflowAction.FailSession,
                lifecycleCollector = CollectorOverflowAction.FailSession,
                continuous = recordingContinuous,
                hostSignals = ContinuousDelivery.Buffered(
                    64,
                    ContinuousOverflowAction.DropOldestAndReport,
                ),
                gamepadChanges = recordingContinuous,
                frames = FrameDelivery.Buffered(3, ContinuousOverflowAction.CloseSource),
                frameBytes = 536_870_912,
                routing = GamepadRouting.AllForegroundSessions,
                diagnosticCapacity = 8192,
                resources = ResourceBudgetPolicy(
                    16, 128, 32, 16, 16, 4, 32, 8, 1_048_576, 60.seconds,
                    134_217_728, 4_194_304, 16_384, 16_384, 67_108_864,
                ),
            ),
            KadrePolicies.Recording,
        )
    }

    private fun expectedPolicy(
        priority: ExecutionPriority,
        shutdownSeconds: Int,
        discreteCapacity: Int,
        discreteIngress: IngressOverflowAction,
        discreteCollector: CollectorOverflowAction,
        lifecycleCollector: CollectorOverflowAction,
        continuous: ContinuousDelivery,
        hostSignals: ContinuousDelivery,
        gamepadChanges: ContinuousDelivery,
        frames: FrameDelivery,
        frameBytes: Long,
        routing: GamepadRouting,
        diagnosticCapacity: Int,
        resources: ResourceBudgetPolicy,
    ): KadrePolicy {
        val lifecycleEvents = EventDeliveryPolicy(
            discreteCapacity,
            discreteCapacity,
            IngressOverflowAction.FailSession,
            lifecycleCollector,
        )
        val events = EventDeliveryPolicy(
            discreteCapacity,
            discreteCapacity,
            discreteIngress,
            discreteCollector,
        )
        return KadrePolicy(
            execution = ExecutionPolicy(priority, shutdownSeconds.seconds),
            lifecycleEvents = lifecycleEvents,
            hostSignals = hostSignals,
            window = WindowDeliveryPolicy(events, continuous, if (continuous is ContinuousDelivery.Buffered) continuous else ContinuousDelivery.Latest),
            deviceEvents = events,
            input = InputDeliveryPolicy(events, continuous, continuous, continuous, continuous, gamepadChanges),
            devices = DevicePolicy(routing, DeviceEffectOwnership.ExclusivePerPhysicalDevice),
            capture = CaptureDeliveryPolicy(events, frames, frameBytes),
            diagnostics = DiagnosticPolicy(
                diagnosticCapacity,
                DiagnosticOverflowAction.DropOldestEvent,
                DiagnosticDataExposure.Redacted,
            ),
            resources = resources,
        )
    }
}
