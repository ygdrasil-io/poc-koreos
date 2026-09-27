package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.InteractionFailureReason
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.RejectedSurfaceField
import org.graphiks.kadre.surface.SurfaceCapabilities
import org.graphiks.kadre.surface.SurfaceProperty

internal val SURFACE_UPDATE_LIMIT_RESOURCES = setOf(
    KadreResourceKind.ImageResource,
    KadreResourceKind.RetainedPayload,
)

internal fun unsupportedSurfaceCapabilities(): SurfaceCapabilities = SurfaceCapabilities(
    cursor = unsupported(KadreOperation.UpdateSurface),
    customCursor = unsupported(KadreOperation.UpdateSurface),
    pointerCapture = unsupported(KadreOperation.UpdateSurface),
    hitTesting = unsupported(KadreOperation.UpdateSurface),
    inputDefaultBehavior = unsupported(KadreOperation.UpdateSurface),
    handlerInteractions = unsupported(KadreOperation.InstallInteractionHandler),
    armedInteractions = unsupported(KadreOperation.ArmInteraction),
    platformAccess = unsupported(KadreOperation.PlatformSurfaceAccess),
)

internal fun capabilityFailure(capability: Capability<*>): KadreFailure? = when (capability) {
    is Capability.Unsupported -> capability.failure
    is Capability.Supported -> when (val availability = capability.availability) {
        FeatureAvailability.Available -> null
        FeatureAvailability.Unsupported -> KadreFailure.Unsupported(KadreOperation.UpdateSurface)
        is FeatureAvailability.Unavailable -> availability.failure
        is FeatureAvailability.RequiresInteraction ->
            KadreFailure.InteractionRequired(InteractionFailureReason.Missing)

        is FeatureAvailability.RequiresPermission -> KadreFailure.Unsupported(KadreOperation.UpdateSurface)
    }
}

internal fun <T> admitField(
    change: PropertyChange<T>,
    property: SurfaceProperty,
    capability: Capability<Set<T>>,
    rejected: MutableList<RejectedSurfaceField>,
): PropertyChange<T> {
    if (change is PropertyChange.Unchanged) return change
    val failure = capabilityFailure(capability)
    if (failure != null) {
        rejected += RejectedSurfaceField(property, failure)
        return PropertyChange.Unchanged
    }
    if (
        change is PropertyChange.Set &&
        capability is Capability.Supported &&
        change.value !in capability.constraints
    ) {
        rejected += RejectedSurfaceField(property, KadreFailure.Unsupported(KadreOperation.UpdateSurface))
        return PropertyChange.Unchanged
    }
    return change
}

internal fun <T> unsupported(operation: KadreOperation): Capability<T> =
    Capability.Unsupported(KadreFailure.Unsupported(operation))

internal val SurfaceProperty.fieldName: String
    get() = when (this) {
        SurfaceProperty.Cursor -> "cursor"
        SurfaceProperty.PointerCapture -> "pointerCapture"
        SurfaceProperty.HitTesting -> "hitTesting"
        SurfaceProperty.InputDefaultBehavior -> "inputDefaultBehavior"
    }

internal data class NormalisedPortFailure(
    val failure: KadreFailure,
    val adapterFailure: KadreFailure.PlatformFailure? = null,
)

internal fun normaliseFieldFailure(
    platform: KadrePlatform,
    property: SurfaceProperty,
    failure: KadreFailure,
): NormalisedPortFailure =
    if (
        failure == KadreFailure.Unsupported(KadreOperation.UpdateSurface) ||
        failure is KadreFailure.InteractionRequired ||
        failure is KadreFailure.InvalidRequest && failure.field == property.fieldName ||
        failure is KadreFailure.ResourceLimitExceeded &&
        failure.resource in SURFACE_UPDATE_LIMIT_RESOURCES ||
        failure is KadreFailure.TemporarilyUnavailable ||
        failure is KadreFailure.PlatformFailure
    ) {
        NormalisedPortFailure(failure)
    } else {
        invalidPortFailure(platform, "invalid-field-failure")
    }

internal fun invalidPortFailure(platform: KadrePlatform, code: String): NormalisedPortFailure {
    val failure = KadreFailure.PlatformFailure(platform, "surface-command-port", code)
    return NormalisedPortFailure(failure, failure)
}
