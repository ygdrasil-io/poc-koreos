package org.graphiks.kadre.samples.desktour.core

import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.input.KadrePermission

/**
 * Ce qu'un contrôle doit montrer. `enabled` décrit l'affordance ; `requestable` dit si une
 * action publique permet d'obtenir la capacité — les deux diffèrent : une permission requise
 * désactive le contrôle tout en restant demandable (spec §5 ligne 116).
 */
internal data class CapabilityPresentation(
    val enabled: Boolean,
    val motif: String? = null,
    val requestable: Boolean = false,
)

internal fun present(capability: Capability<*>): CapabilityPresentation = when (capability) {
    is Capability.Unsupported -> CapabilityPresentation(false, capability.failure.userMotif())
    is Capability.Supported -> present(capability.availability)
}

internal fun present(availability: FeatureAvailability): CapabilityPresentation = when (availability) {
    FeatureAvailability.Available -> CapabilityPresentation(true, requestable = true)
    FeatureAvailability.Unsupported -> CapabilityPresentation(false, "Non pris en charge par le host courant.")
    is FeatureAvailability.RequiresPermission -> CapabilityPresentation(
        false,
        "Cette action nécessite une permission (${availability.permission.readableName()}).",
        requestable = true,
    )
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
