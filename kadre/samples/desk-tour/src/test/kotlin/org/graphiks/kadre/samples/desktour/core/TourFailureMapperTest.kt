package org.graphiks.kadre.samples.desktour.core

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TourFailureMapperTest {
    @Test
    fun `a refused note request becomes a refusal with a user motif`() {
        val outcome = noteOpenOutcomeFor(KadreFailure.Unsupported(KadreOperation.RequestWindow))
        assertTrue(outcome is NoteOpenOutcome.Refused)
        assertEquals("Non pris en charge par le host courant.", outcome.motif)
    }

    @Test
    fun `a window budget exhaustion is a refusal and never an opened note`() {
        val outcome = noteOpenOutcomeFor(KadreFailure.ResourceLimitExceeded(KadreResourceKind.Window, 16L))
        assertTrue(outcome is NoteOpenOutcome.Refused)
        assertTrue((outcome as NoteOpenOutcome.Refused).motif.isNotBlank())
    }

    @Test
    fun `a refusal never carries the developer message`() {
        val failure = KadreFailure.StaleRevision(3L, 7L)
        val outcome = noteOpenOutcomeFor(failure)
        assertTrue(outcome is NoteOpenOutcome.Refused)
        assertTrue((outcome as NoteOpenOutcome.Refused).motif != failure.message)
    }
}
