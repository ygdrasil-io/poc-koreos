package org.graphiks.kadre.samples.desktour.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DisplayAccessIntentTest {
    private class RecordingDisplayGateway(
        private val canRequestAccess: Boolean = true,
        private val accessResult: DisplayPresentation = DisplayPresentation.NeedsPermission,
    ) : TestTourGateway() {
        var accessRequested = false
        override fun displayAccessAvailability(): CapabilityPresentation =
            CapabilityPresentation(canRequestAccess, "Non pris en charge par le host courant.")
        override suspend fun requestDisplayAccess(): DisplayPresentation {
            accessRequested = true
            return accessResult
        }
    }

    @Test
    fun `the screens route shows nothing until an inventory is observed`() {
        assertNull(TourStore().state.value.displays, "un inventaire non observé n'est pas une liste vide affichée")
    }

    @Test
    fun `an unsupported enumeration capability disables the access control`() {
        val store = TourStore()
        store.publishDisplayAccessAvailability(
            CapabilityPresentation(false, "Non pris en charge par le host courant."),
        )

        assertEquals(false, store.state.value.displayAccess.enabled)
        assertTrue(store.state.value.displayAccess.motif!!.isNotBlank())
    }

    @Test
    fun `requesting access while it is unsupported never reaches the gateway`() = runTest {
        val store = TourStore()
        val gateway = RecordingDisplayGateway(canRequestAccess = false)
        store.publishDisplayAccessAvailability(gateway.displayAccessAvailability())

        ActionDispatcher(store, gateway).requestDisplayAccess()

        assertEquals(false, gateway.accessRequested, "une capacité absente ne doit pas atteindre le gateway")
        assertTrue(store.state.value.activity.isEmpty(), "une capacité absente ne remplit pas le journal")
    }

    @Test
    fun `a granted access is journaled and the new inventory replaces the old one`() = runTest {
        val store = TourStore()
        val gateway = RecordingDisplayGateway(
            accessResult = DisplayPresentation.Enumerated(
                listOf(screenEntryOf("Écran", 800, 600, 1.0, null, true)),
            ),
        )
        store.publishDisplayAccessAvailability(gateway.displayAccessAvailability())

        ActionDispatcher(store, gateway).requestDisplayAccess()

        assertEquals(ActivityStatus.Succeeded, store.state.value.activity.single().status)
        assertTrue(store.state.value.displays is DisplayPresentation.Enumerated)
    }
}
