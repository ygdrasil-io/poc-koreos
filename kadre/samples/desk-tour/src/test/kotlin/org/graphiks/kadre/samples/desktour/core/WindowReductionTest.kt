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
    fun `a rejected request is unavailable and never claims a window was created`() {
        val (store, id) = storeWithPending()

        store.recordWindowRequestOutcome(
            id,
            WindowRequestOutcome.Rejected(KadreFailure.Unsupported(KadreOperation.RequestWindow)),
        )

        val entry = store.state.value.activity.single()
        assertEquals(ActivityStatus.Unavailable, entry.status)
        assertTrue(entry.motif!!.isNotBlank())
    }

    @Test
    fun `a cancelled request resolves as cancelled`() {
        val (store, id) = storeWithPending()
        store.recordWindowRequestOutcome(id, WindowRequestOutcome.Cancelled)
        assertEquals(ActivityStatus.Cancelled, store.state.value.activity.single().status)
    }

    @Test
    fun `a detached requester resolves as cancelled`() {
        val (store, id) = storeWithPending()
        store.recordWindowRequestOutcome(id, WindowRequestOutcome.RequesterDetached)
        assertEquals(ActivityStatus.Cancelled, store.state.value.activity.single().status)
    }

    @Test
    fun `every non-OpenedHere outcome is never reported as succeeded`() {
        val (store, id) = storeWithPending()
        store.recordWindowRequestOutcome(id, WindowRequestOutcome.Cancelled)
        assertTrue(store.state.value.activity.single().status != ActivityStatus.Succeeded)
    }

    @Test
    fun `publishing windows replaces the observed window list`() {
        val (store, _) = storeWithPending()
        store.publishWindows(listOf(DeskTourWindow("Notes", "Focused", 800.0, 600.0, 800, 600)))
        assertEquals(1, store.state.value.windows.size)
        assertEquals("Notes", store.state.value.windows.single().title)
    }
}
