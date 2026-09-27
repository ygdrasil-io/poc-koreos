package org.graphiks.kadre.internal.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.InteractionFailureReason
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.input.KadrePermission
import org.graphiks.kadre.interaction.InteractionKind
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.RejectedSurfaceField
import org.graphiks.kadre.surface.SurfaceProperty

class SurfaceAdmissionTest {
    @Test
    fun admitFieldRejectsAValueOutsideTheAdmittedConstraints() {
        val rejected = mutableListOf<RejectedSurfaceField>()

        val admitted = admitField(
            change = PropertyChange.Set(PointerCaptureMode.Locked),
            property = SurfaceProperty.PointerCapture,
            capability = Capability.Supported<Set<PointerCaptureMode>>(
                setOf(PointerCaptureMode.None),
                FeatureAvailability.Available,
            ),
            rejected = rejected,
        )

        assertSame(PropertyChange.Unchanged, admitted)
        assertEquals(
            listOf(
                RejectedSurfaceField(
                    SurfaceProperty.PointerCapture,
                    KadreFailure.Unsupported(KadreOperation.UpdateSurface),
                ),
            ),
            rejected,
        )
    }

    @Test
    fun admitFieldAdmitsAValueInsideTheAdmittedConstraints() {
        val rejected = mutableListOf<RejectedSurfaceField>()
        val change = PropertyChange.Set(PointerCaptureMode.Confined)

        val admitted = admitField(
            change = change,
            property = SurfaceProperty.PointerCapture,
            capability = Capability.Supported<Set<PointerCaptureMode>>(
                setOf(PointerCaptureMode.None, PointerCaptureMode.Confined),
                FeatureAvailability.Available,
            ),
            rejected = rejected,
        )

        assertSame(change, admitted)
        assertEquals(emptyList(), rejected)
    }

    @Test
    fun capabilityFailureCarriesTheFailureOfAnUnsupportedCapability() {
        val failure = KadreFailure.Unsupported(KadreOperation.TextInput)

        assertSame(failure, capabilityFailure(Capability.Unsupported(failure)))
    }

    @Test
    fun capabilityFailureMapsRequiresInteractionToInteractionRequiredMissing() {
        val capability: Capability<Set<PointerCaptureMode>> = Capability.Supported(
            setOf(PointerCaptureMode.None),
            FeatureAvailability.RequiresInteraction(InteractionKind.LockPointer),
        )

        assertEquals(
            KadreFailure.InteractionRequired(InteractionFailureReason.Missing),
            capabilityFailure(capability),
        )
    }

    @Test
    fun capabilityFailureMapsRequiresPermissionToUnsupportedUpdateSurface() {
        val capability: Capability<Set<PointerCaptureMode>> = Capability.Supported(
            setOf(PointerCaptureMode.None),
            FeatureAvailability.RequiresPermission(KadrePermission.DisplayEnumeration),
        )

        assertEquals(
            KadreFailure.Unsupported(KadreOperation.UpdateSurface),
            capabilityFailure(capability),
        )
    }

    @Test
    fun capabilityFailureReportsNothingForAnAvailableCapability() {
        val capability: Capability<Set<PointerCaptureMode>> = Capability.Supported(
            setOf(PointerCaptureMode.None),
            FeatureAvailability.Available,
        )

        assertNull(capabilityFailure(capability))
    }
}
