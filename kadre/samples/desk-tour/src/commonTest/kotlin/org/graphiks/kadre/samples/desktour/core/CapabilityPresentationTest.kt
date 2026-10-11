package org.graphiks.kadre.samples.desktour.core

import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.input.KadrePermission
import org.graphiks.kadre.interaction.InteractionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CapabilityPresentationTest {
    @Test
    fun `an unsupported capability is disabled and carries a motif`() {
        val presented = present(Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateWindow)))
        assertFalse(presented.enabled)
        assertNotNull(presented.motif)
    }

    @Test
    fun `an available capability is enabled without a motif`() {
        val presented = present(Capability.Supported(Unit, FeatureAvailability.Available))
        assertTrue(presented.enabled)
        assertEquals(null, presented.motif)
    }

    @Test
    fun `a capability requiring a permission is disabled and names the permission`() {
        val presented = present(
            Capability.Supported(Unit, FeatureAvailability.RequiresPermission(KadrePermission.CaptureScreen)),
        )
        assertFalse(presented.enabled)
        assertTrue(presented.motif!!.contains("permission"))
    }

    @Test
    fun `an interaction-gated availability is disabled and explains the interaction`() {
        val presented = present(FeatureAvailability.RequiresInteraction(InteractionKind.BeginWindowMove))
        assertFalse(presented.enabled)
        assertTrue(presented.motif!!.isNotBlank())
    }

    @Test
    fun `a failure availability is disabled and reuses the shared user motif`() {
        val failure = KadreFailure.PermissionDenied(KadrePermission.DisplayEnumeration)
        val presented = present(FeatureAvailability.Unavailable(failure))
        assertFalse(presented.enabled)
        assertEquals(failure.userMotif(), presented.motif)
    }

    @Test
    fun `no presentation ever enables an unsupported feature`() {
        val candidates = listOf<FeatureAvailability>(
            FeatureAvailability.Unsupported,
            FeatureAvailability.RequiresPermission(KadrePermission.RawInput),
            FeatureAvailability.Unavailable(KadreFailure.TemporarilyUnavailable(retryable = false)),
        )
        candidates.forEach { assertFalse(present(it).enabled, "must stay disabled: $it") }
    }
}
