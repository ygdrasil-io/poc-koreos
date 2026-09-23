package org.graphiks.kadre.samples.desktour.core

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.message
import org.graphiks.kadre.input.KadrePermission
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DisplayObservationTest {
    @Test
    fun `a permission denial becomes its own denial state and keeps a retry`() {
        val translated = displayPresentationFor(KadreFailure.PermissionDenied(KadrePermission.DisplayEnumeration))
        assertTrue(translated is DisplayPresentation.Denied, "un refus a son propre état, pas celui d'une panne")
        assertTrue(translated.canRequestAgain)
    }

    @Test
    fun `a platform failure carries a user motif and never the developer message`() {
        val failure = KadreFailure.PlatformFailure(KadrePlatform.AppKit, "display", "enumerate")
        val translated = displayPresentationFor(failure)
        assertTrue(translated is DisplayPresentation.Unavailable)
        assertNotEquals(failure.message, translated.motif)
    }

    @Test
    fun `a temporary failure is retryable and says so`() {
        val translated = displayPresentationFor(KadreFailure.TemporarilyUnavailable(retryable = true))
        assertTrue(translated is DisplayPresentation.Unavailable)
        assertTrue(translated.motif.contains("réessayable"))
        assertTrue(translated.retryable, "un échec réessayable doit offrir le réessai")
    }

    @Test
    fun `a definitive failure is not offered for retry`() {
        val translated = displayPresentationFor(KadreFailure.Unsupported(KadreOperation.DisplayAccess))
        assertTrue(translated is DisplayPresentation.Unavailable)
        assertTrue(!translated.retryable, "un refus définitif ne doit pas proposer de réessai")
    }
}
