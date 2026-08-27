package org.graphiks.kadre.diagnostics

import org.graphiks.kadre.input.KadrePermission
import org.graphiks.kadre.interaction.InteractionKind

public sealed interface FeatureAvailability {
    public data object Unsupported : FeatureAvailability
    public data object Available : FeatureAvailability
    public data class RequiresPermission(public val permission: KadrePermission) : FeatureAvailability
    public data class RequiresInteraction(public val kind: InteractionKind) : FeatureAvailability

    public data class Unavailable(public val failure: KadreFailure) : FeatureAvailability {
        init {
            require(failure.isAvailabilityFailure()) { "failure is not valid for unavailable capability state" }
        }
    }
}

public sealed interface Capability<out Constraints> {
    public data class Unsupported(public val failure: KadreFailure.Unsupported) : Capability<Nothing>

    public data class Supported<Constraints>(
        public val constraints: Constraints,
        public val availability: FeatureAvailability,
    ) : Capability<Constraints> {
        init {
            require(availability != FeatureAvailability.Unsupported) {
                "a supported capability cannot have Unsupported availability"
            }
            if (constraints is Set<*>) require(constraints.isNotEmpty()) {
                "set constraints must not be empty"
            }
        }
    }
}

private fun KadreFailure.isAvailabilityFailure(): Boolean = when (this) {
    is KadreFailure.PermissionDenied,
    is KadreFailure.TemporarilyUnavailable,
    is KadreFailure.AlreadyInUse,
    is KadreFailure.Closed,
    is KadreFailure.ResourceLimitExceeded,
    is KadreFailure.SourceOverflow,
    is KadreFailure.SourceLost,
    is KadreFailure.PlatformFailure,
    -> true

    else -> false
}
