package org.graphiks.kadre.policy

import kotlin.time.Duration.Companion.seconds

public object KadrePolicies {
    public val Default: KadrePolicy = profile(
        execution = ExecutionPolicy(ExecutionPriority.Balanced, 5.seconds),
        discreteCapacity = 256,
        eventIngress = IngressOverflowAction.CloseSource,
        eventCollector = CollectorOverflowAction.CancelSlowCollector,
        lifecycleCollector = CollectorOverflowAction.CancelSlowCollector,
        hostSignals = ContinuousDelivery.Latest,
        geometry = ContinuousDelivery.Coalesced,
        redraw = ContinuousDelivery.Latest,
        pointer = ContinuousDelivery.Coalesced,
        touch = ContinuousDelivery.Coalesced,
        scroll = ContinuousDelivery.Coalesced,
        gestures = ContinuousDelivery.Coalesced,
        gamepad = ContinuousDelivery.Latest,
        routing = GamepadRouting.ActiveSessionOnly,
        frames = FrameDelivery.Latest,
        frameBytes = 134_217_728,
        diagnosticCapacity = 256,
        resources = ResourceBudgetPolicy(
            16, 128, 16, 16, 16, 4, 16, 4, 262_144, 30.seconds,
            33_554_432, 1_048_576, 4_096, 4_096, 16_777_216,
        ),
    )

    public val Realtime: KadrePolicy = profile(
        execution = ExecutionPolicy(ExecutionPriority.LatencyFirst, 2.seconds),
        discreteCapacity = 64,
        eventIngress = IngressOverflowAction.CloseSource,
        eventCollector = CollectorOverflowAction.CancelSlowCollector,
        lifecycleCollector = CollectorOverflowAction.CancelSlowCollector,
        hostSignals = ContinuousDelivery.Latest,
        geometry = ContinuousDelivery.Coalesced,
        redraw = ContinuousDelivery.Latest,
        pointer = ContinuousDelivery.Coalesced,
        touch = ContinuousDelivery.Coalesced,
        scroll = ContinuousDelivery.Coalesced,
        gestures = ContinuousDelivery.Coalesced,
        gamepad = ContinuousDelivery.Latest,
        routing = GamepadRouting.ActiveSessionOnly,
        frames = FrameDelivery.Latest,
        frameBytes = 67_108_864,
        diagnosticCapacity = 64,
        resources = ResourceBudgetPolicy(
            8, 64, 8, 8, 8, 2, 8, 2, 65_536, 5.seconds,
            8_388_608, 262_144, 2_048, 2_048, 4_194_304,
        ),
    )

    public val Recording: KadrePolicy = profile(
        execution = ExecutionPolicy(ExecutionPriority.Throughput, 30.seconds),
        discreteCapacity = 8192,
        eventIngress = IngressOverflowAction.FailSession,
        eventCollector = CollectorOverflowAction.FailSession,
        lifecycleCollector = CollectorOverflowAction.FailSession,
        hostSignals = ContinuousDelivery.Buffered(64, ContinuousOverflowAction.DropOldestAndReport),
        geometry = ContinuousDelivery.Buffered(8192, ContinuousOverflowAction.FailSession),
        redraw = ContinuousDelivery.Buffered(8192, ContinuousOverflowAction.FailSession),
        pointer = ContinuousDelivery.Buffered(8192, ContinuousOverflowAction.FailSession),
        touch = ContinuousDelivery.Buffered(8192, ContinuousOverflowAction.FailSession),
        scroll = ContinuousDelivery.Buffered(8192, ContinuousOverflowAction.FailSession),
        gestures = ContinuousDelivery.Buffered(8192, ContinuousOverflowAction.FailSession),
        gamepad = ContinuousDelivery.Buffered(8192, ContinuousOverflowAction.FailSession),
        routing = GamepadRouting.AllForegroundSessions,
        frames = FrameDelivery.Buffered(3, ContinuousOverflowAction.CloseSource),
        frameBytes = 536_870_912,
        diagnosticCapacity = 8192,
        resources = ResourceBudgetPolicy(
            16, 128, 32, 16, 16, 4, 32, 8, 1_048_576, 60.seconds,
            134_217_728, 4_194_304, 16_384, 16_384, 67_108_864,
        ),
    )

    private fun profile(
        execution: ExecutionPolicy,
        discreteCapacity: Int,
        eventIngress: IngressOverflowAction,
        eventCollector: CollectorOverflowAction,
        lifecycleCollector: CollectorOverflowAction,
        hostSignals: ContinuousDelivery,
        geometry: ContinuousDelivery,
        redraw: ContinuousDelivery,
        pointer: ContinuousDelivery,
        touch: ContinuousDelivery,
        scroll: ContinuousDelivery,
        gestures: ContinuousDelivery,
        gamepad: ContinuousDelivery,
        routing: GamepadRouting,
        frames: FrameDelivery,
        frameBytes: Long,
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
            eventIngress,
            eventCollector,
        )
        return KadrePolicy(
            execution = execution,
            lifecycleEvents = lifecycleEvents,
            hostSignals = hostSignals,
            window = WindowDeliveryPolicy(events, geometry, redraw),
            deviceEvents = events,
            input = InputDeliveryPolicy(events, pointer, touch, scroll, gestures, gamepad),
            devices = DevicePolicy(
                gamepadRouting = routing,
                effectOwnership = DeviceEffectOwnership.ExclusivePerPhysicalDevice,
            ),
            capture = CaptureDeliveryPolicy(events, frames, frameBytes),
            diagnostics = DiagnosticPolicy(
                eventBufferCapacity = diagnosticCapacity,
                eventOverflow = DiagnosticOverflowAction.DropOldestEvent,
                dataExposure = DiagnosticDataExposure.Redacted,
            ),
            resources = resources,
        )
    }
}
