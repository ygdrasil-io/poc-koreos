package org.graphiks.kadre.samples.desktour.core

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.window.WindowRequestOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowReductionTest {
    private fun storeWithPending() = TourStore().let { it to it.admit("Créer une note", "WindowManager.requestWindow") }

    @Test
    fun `every outcome other than OpenedHere keeps its own terminal status`() {
        // OpenedInNewSession is not constructible in the sample (internal SessionId); the
        // exhaustive `when` in WindowReduction fails to compile if that branch is dropped.
        val expected = listOf(
            WindowRequestOutcome.Rejected(KadreFailure.Unsupported(KadreOperation.RequestWindow))
                to ActivityStatus.Rejected,
            WindowRequestOutcome.Cancelled to ActivityStatus.Cancelled,
            WindowRequestOutcome.RequesterDetached to ActivityStatus.Cancelled,
        )

        expected.forEach { (outcome, status) ->
            val (store, id) = storeWithPending()
            store.recordWindowRequestOutcome(id, outcome)
            assertEquals(status, store.state.value.activity.single().status, "for $outcome")
        }
    }

    @Test
    fun `a rejected request carries a motif and is never reported as succeeded`() {
        val (store, id) = storeWithPending()

        store.recordWindowRequestOutcome(
            id,
            WindowRequestOutcome.Rejected(KadreFailure.Unsupported(KadreOperation.RequestWindow)),
        )

        val entry = store.state.value.activity.single()
        assertTrue(entry.motif!!.isNotBlank())
        assertTrue(entry.status != ActivityStatus.Succeeded)
    }

    @Test
    fun `publishing windows replaces the observed window list`() {
        val (store, _) = storeWithPending()
        store.publishWindows(listOf(DeskTourWindow("Notes", "Focused", 800.0, 600.0, 800, 600)))
        assertEquals(1, store.state.value.windows.size)
        assertEquals("Notes", store.state.value.windows.single().title)
    }
}
