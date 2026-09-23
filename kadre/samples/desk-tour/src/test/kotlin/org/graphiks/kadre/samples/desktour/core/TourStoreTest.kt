package org.graphiks.kadre.samples.desktour.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TourStoreTest {
    @Test
    fun `admission records a pending entry and returns a fresh correlation id`() {
        val store = TourStore()
        val first = store.admit("Créer une note", "WindowManager.requestWindow")
        val second = store.admit("Créer une note", "WindowManager.requestWindow")

        assertNotEquals(first, second)
        val entries = store.state.value.activity
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.status == ActivityStatus.Pending })
        assertEquals("WindowManager.requestWindow", entries.first().apiDetail)
    }

    @Test
    fun `resolution moves a pending entry to its terminal status with a motif`() {
        val store = TourStore()
        val id = store.admit("Créer une note", "WindowManager.requestWindow")

        store.resolve(id, ActivityStatus.Unavailable, motif = "Non pris en charge par ce host")

        val entry = store.state.value.activity.single()
        assertEquals(ActivityStatus.Unavailable, entry.status)
        assertEquals("Non pris en charge par ce host", entry.motif)
    }

    @Test
    fun `a resolved entry is never resolved twice`() {
        val store = TourStore()
        val id = store.admit("Créer une note", "WindowManager.requestWindow")
        store.resolve(id, ActivityStatus.Succeeded)

        store.resolve(id, ActivityStatus.Rejected, motif = "trop tard")

        val entry = store.state.value.activity.single()
        assertEquals(ActivityStatus.Succeeded, entry.status)
        assertEquals(null, entry.motif)
    }

    @Test
    fun `resolving an unknown id changes nothing`() {
        val store = TourStore()
        store.admit("Créer une note", "WindowManager.requestWindow")

        store.resolve(ActionCorrelationId(9_999), ActivityStatus.Succeeded)

        assertTrue(store.state.value.activity.all { it.status == ActivityStatus.Pending })
    }

    @Test
    fun `the journal defaults to the Activity route and the drawer starts closed`() {
        val state = TourStore().state.value
        assertEquals(TourRoute.Activity, state.route)
        assertEquals(false, state.apiDetailsOpen)
    }
}
