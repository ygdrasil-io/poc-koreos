package org.graphiks.kadre.diagnostics

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.input.KadrePermission

public interface KadreDiagnostics {
    public val events: Flow<KadreDiagnostic>
    public val counters: StateFlow<DiagnosticCounters>
}

public enum class DiagnosticSeverity { Info, Warning, Error, Fatal }

public enum class KadreSubsystem {
    Application,
    Host,
    Lifecycle,
    Surface,
    Window,
    Display,
    Input,
    Gamepad,
    Capture,
    Policy,
}

public sealed interface KadreDiagnostic {
    public val stamp: EventStamp
    public val severity: DiagnosticSeverity
    public val subsystem: KadreSubsystem

    public data class EventLoss internal constructor(
        public val count: Long,
        public val resource: KadreResourceKind,
        override val subsystem: KadreSubsystem,
        override val stamp: EventStamp,
    ) : KadreDiagnostic {
        init { require(count > 0) }
        override val severity: DiagnosticSeverity get() = DiagnosticSeverity.Warning
    }

    public data class SlowConsumer internal constructor(
        public val resource: KadreResourceKind,
        public val droppedCount: Long,
        override val subsystem: KadreSubsystem,
        override val stamp: EventStamp,
    ) : KadreDiagnostic {
        init { require(droppedCount > 0) }
        override val severity: DiagnosticSeverity get() = DiagnosticSeverity.Warning
    }

    public data class CollectorRejected internal constructor(
        public val perFlow: Boolean,
        public val limit: Long,
        override val subsystem: KadreSubsystem,
        override val stamp: EventStamp,
    ) : KadreDiagnostic {
        init { require(limit > 0) }
        override val severity: DiagnosticSeverity get() = DiagnosticSeverity.Warning
    }

    public data class ResourceLimitHit internal constructor(
        public val resource: KadreResourceKind,
        public val limit: Long,
        override val subsystem: KadreSubsystem,
        override val stamp: EventStamp,
    ) : KadreDiagnostic {
        init { require(limit > 0) }
        override val severity: DiagnosticSeverity get() = DiagnosticSeverity.Warning
    }

    public data class InteractionExpired internal constructor(
        override val subsystem: KadreSubsystem,
        override val stamp: EventStamp,
    ) : KadreDiagnostic {
        override val severity: DiagnosticSeverity get() = DiagnosticSeverity.Info
    }

    public data class PermissionRevoked internal constructor(
        public val permission: KadrePermission,
        override val subsystem: KadreSubsystem,
        override val stamp: EventStamp,
    ) : KadreDiagnostic {
        override val severity: DiagnosticSeverity get() = DiagnosticSeverity.Warning
    }

    public data class CapabilityChanged internal constructor(
        public val resource: KadreResourceKind,
        public val operation: KadreOperation?,
        public val availability: FeatureAvailability,
        override val subsystem: KadreSubsystem,
        override val stamp: EventStamp,
    ) : KadreDiagnostic {
        override val severity: DiagnosticSeverity get() = DiagnosticSeverity.Info
    }

    public data class BackendFallback internal constructor(
        public val operation: KadreOperation,
        public val platform: KadrePlatform,
        override val subsystem: KadreSubsystem,
        override val stamp: EventStamp,
    ) : KadreDiagnostic {
        override val severity: DiagnosticSeverity get() = DiagnosticSeverity.Warning
    }

    public data class PlatformFailureObserved internal constructor(
        public val failure: KadreFailure.PlatformFailure,
        public val operation: KadreOperation,
        override val subsystem: KadreSubsystem,
        override val stamp: EventStamp,
    ) : KadreDiagnostic {
        override val severity: DiagnosticSeverity get() = DiagnosticSeverity.Error
    }

    public data class SessionFailure internal constructor(
        public val failure: KadreFailure,
        override val subsystem: KadreSubsystem,
        override val stamp: EventStamp,
    ) : KadreDiagnostic {
        override val severity: DiagnosticSeverity get() = DiagnosticSeverity.Fatal
    }

    public data class CleanupFailure internal constructor(
        public val failure: KadreFailure,
        override val subsystem: KadreSubsystem,
        override val stamp: EventStamp,
    ) : KadreDiagnostic {
        override val severity: DiagnosticSeverity get() = DiagnosticSeverity.Error
    }
}

public val KadreDiagnostic.message: String
    get() = when (this) {
        is KadreDiagnostic.EventLoss -> "Events were lost for $resource"
        is KadreDiagnostic.SlowConsumer -> "A slow consumer dropped events for $resource"
        is KadreDiagnostic.CollectorRejected -> "An event collector was rejected"
        is KadreDiagnostic.ResourceLimitHit -> "A resource limit was reached for $resource"
        is KadreDiagnostic.InteractionExpired -> "A transient interaction expired"
        is KadreDiagnostic.PermissionRevoked -> "Permission $permission was revoked"
        is KadreDiagnostic.CapabilityChanged -> "A capability changed for $resource"
        is KadreDiagnostic.BackendFallback -> "Backend fallback occurred on $platform"
        is KadreDiagnostic.PlatformFailureObserved -> "A platform failure was observed"
        is KadreDiagnostic.SessionFailure -> "The session failed"
        is KadreDiagnostic.CleanupFailure -> "Cleanup failed"
    }

public data class DiagnosticCounters(
    public val eventLosses: Long,
    public val slowCollectors: Long,
    public val collectorRejections: Long,
    public val resourceLimitHits: Long,
    public val interactionExpirations: Long,
    public val permissionRevocations: Long,
    public val backendFallbacks: Long,
    public val platformFailures: Long,
    public val saturated: Set<DiagnosticCounter>,
) {
    init {
        require(
            eventLosses >= 0 &&
                slowCollectors >= 0 &&
                collectorRejections >= 0 &&
                resourceLimitHits >= 0 &&
                interactionExpirations >= 0 &&
                permissionRevocations >= 0 &&
                backendFallbacks >= 0 &&
                platformFailures >= 0,
        ) { "diagnostic counters must be non-negative" }
    }
}

public enum class DiagnosticCounter {
    EventLosses,
    SlowCollectors,
    CollectorRejections,
    ResourceLimitHits,
    InteractionExpirations,
    PermissionRevocations,
    BackendFallbacks,
    PlatformFailures,
}
