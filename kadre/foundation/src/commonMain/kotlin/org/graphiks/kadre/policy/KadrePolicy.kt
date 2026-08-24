package org.graphiks.kadre.policy

import kotlin.time.Duration

public data class KadrePolicy(
    public val execution: ExecutionPolicy,
    public val lifecycleEvents: EventDeliveryPolicy,
    public val hostSignals: ContinuousDelivery,
    public val window: WindowDeliveryPolicy,
    public val deviceEvents: EventDeliveryPolicy,
    public val input: InputDeliveryPolicy,
    public val devices: DevicePolicy,
    public val capture: CaptureDeliveryPolicy,
    public val diagnostics: DiagnosticPolicy,
    public val resources: ResourceBudgetPolicy,
)

public data class ResourceBudgetPolicy(
    public val maxEventCollectorsPerFlow: Int,
    public val maxEventCollectorsPerSession: Int,
    public val maxWindowsPerSession: Int,
    public val maxPendingWindowRequests: Int,
    public val maxPendingInteractionRequests: Int,
    public val maxConcurrentCaptureSessions: Int,
    public val maxConcurrentGamepadEffects: Int,
    public val maxConcurrentDropTransfers: Int,
    public val maxDropChunkBytes: Int,
    public val dropTransferClaimTimeout: Duration,
    public val maxRetainedPayloadBytesPerSession: Long,
    public val maxTextCodeUnitsPerValue: Int,
    public val maxMetadataCodeUnitsPerValue: Int,
    public val maxCollectionElementsPerValue: Int,
    public val maxImageBytesPerResource: Long,
) {
    init {
        require(
            listOf(
                maxEventCollectorsPerFlow,
                maxEventCollectorsPerSession,
                maxWindowsPerSession,
                maxPendingWindowRequests,
                maxPendingInteractionRequests,
                maxConcurrentCaptureSessions,
                maxConcurrentGamepadEffects,
                maxConcurrentDropTransfers,
                maxDropChunkBytes,
                maxTextCodeUnitsPerValue,
                maxMetadataCodeUnitsPerValue,
                maxCollectionElementsPerValue,
            ).all { it > 0 },
        ) { "resource Int limits must be positive" }
        require(maxRetainedPayloadBytesPerSession > 0 && maxImageBytesPerResource > 0) {
            "resource Long budgets must be positive"
        }
        require(maxEventCollectorsPerFlow <= maxEventCollectorsPerSession) {
            "per-flow collectors cannot exceed per-session collectors"
        }
        require(maxDropChunkBytes.toLong() <= maxRetainedPayloadBytesPerSession) {
            "drop chunk budget cannot exceed retained payload budget"
        }
        require(maxImageBytesPerResource <= maxRetainedPayloadBytesPerSession) {
            "image budget cannot exceed retained payload budget"
        }
        requireFinitePositive(dropTransferClaimTimeout, "dropTransferClaimTimeout")
    }
}

public data class DevicePolicy(
    public val gamepadRouting: GamepadRouting,
    public val effectOwnership: DeviceEffectOwnership,
)

public enum class GamepadRouting { ActiveSessionOnly, AllForegroundSessions }

public enum class DeviceEffectOwnership { ExclusivePerPhysicalDevice, SharedWhenSupported }

public data class DiagnosticPolicy(
    public val eventBufferCapacity: Int,
    public val eventOverflow: DiagnosticOverflowAction,
    public val dataExposure: DiagnosticDataExposure,
) {
    init {
        require(eventBufferCapacity > 0) { "eventBufferCapacity must be positive" }
    }
}

public enum class DiagnosticDataExposure { Redacted, IncludePublicMetadata }

public enum class DiagnosticOverflowAction { DropOldestEvent, DropLatestEvent }
