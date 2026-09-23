package org.graphiks.kadre.samples.desktour.core

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.message
import org.graphiks.kadre.input.KadrePermission
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FailureMotifTest {
    @Test
    fun `every failure motif is a user sentence and never the developer message`() {
        val failures = listOf(
            KadreFailure.Unsupported(KadreOperation.RequestWindow),
            KadreFailure.PermissionDenied(KadrePermission.DisplayEnumeration),
            KadreFailure.UserCancelled(KadreOperation.RequestWindow),
            KadreFailure.TemporarilyUnavailable(retryable = true),
            KadreFailure.TemporarilyUnavailable(retryable = false),
            KadreFailure.InvalidRequest("sizeConstraints"),
            KadreFailure.InvalidRequest(null),
            KadreFailure.StaleRevision(3, 7),
            KadreFailure.ParentScopeCancelled,
            KadreFailure.ApplicationFailure,
            KadreFailure.PlatformFailure(KadrePlatform.AppKit, "desk-tour.compose", "render"),
        )

        failures.forEach { failure ->
            val motif = failure.userMotif()
            assertTrue(motif.isNotBlank(), "blank motif for $failure")
            assertNotEquals(failure.message, motif, "developer message leaked for $failure")
        }
    }
}
