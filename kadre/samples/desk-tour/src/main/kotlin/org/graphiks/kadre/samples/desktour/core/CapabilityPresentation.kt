package org.graphiks.kadre.samples.desktour.core

import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.input.KadrePermission

internal data class CapabilityPresentation(val enabled: Boolean, val motif: String? = null)

internal fun present(capability: Capability<*>): CapabilityPresentation = when (capability) {
    is Capability.Unsupported -> CapabilityPresentation(false, capability.failure.userMotif())
    is Capability.Supported -> present(capability.availability)
}

internal fun present(availability: FeatureAvailability): CapabilityPresentation = when (availability) {
    FeatureAvailability.Available -> CapabilityPresentation(true)
    FeatureAvailability.Unsupported -> CapabilityPresentation(false, "Non pris en charge par le host courant.")
    is FeatureAvailability.RequiresPermission ->
        CapabilityPresentation(false, "Cette action nécessite une permission (${availability.permission.readableName()}).")
    is FeatureAvailability.RequiresInteraction ->
        CapabilityPresentation(false, "Cette action nécessite une interaction préalable non disponible.")
    is FeatureAvailability.Unavailable -> CapabilityPresentation(false, availability.failure.userMotif())
}

private fun KadrePermission.readableName(): String = when (this) {
    KadrePermission.DisplayEnumeration -> "inventaire des écrans"
    KadrePermission.InputMonitoring -> "surveillance des entrées"
    KadrePermission.RawInput -> "entrées brutes"
    KadrePermission.CaptureScreen -> "capture d'écran"
    KadrePermission.CaptureWindow -> "capture de fenêtre"
}
