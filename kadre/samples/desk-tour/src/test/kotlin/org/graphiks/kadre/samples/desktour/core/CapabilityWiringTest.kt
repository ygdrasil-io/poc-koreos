package org.graphiks.kadre.samples.desktour.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.window.Window
import org.graphiks.kadre.window.WindowRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapabilityWiringTest {
    private class StubGateway(
        private val availability: CapabilityPresentation,
    ) : TourGateway {
        var calls = 0
        override fun lifecycleSummary(): Flow<String> = flowOf("Session")
        override fun observeWindow(window: Window): Flow<DeskTourWindow> = flowOf()
        override fun createNoteAvailability(): CapabilityPresentation = availability
        override suspend fun requestNoteWindow(): KadreResult<WindowRequest> {
            calls++
            return CompletableDeferred<KadreResult<WindowRequest>>().await()
        }
    }

    @Test
    fun `an unsupported create-note capability never reaches the journal and never calls Kadre`() = runTest {
        val store = TourStore()
        val gateway = StubGateway(CapabilityPresentation(false, "Non pris en charge par le host courant."))
        val dispatcher = ActionDispatcher(store, gateway)
        store.publishCreateNoteAvailability(gateway.createNoteAvailability())

        dispatcher.createNote()

        assertTrue(store.state.value.activity.isEmpty(), "a disabled action must not fill the journal")
        assertEquals(0, gateway.calls, "a disabled action must not call Kadre")
    }

    @Test
    fun `the published availability reaches the state so the shell can disable the control`() {
        val store = TourStore()

        store.publishCreateNoteAvailability(CapabilityPresentation(false, "Permission requise."))

        assertFalse(store.state.value.createNote.enabled)
        assertEquals("Permission requise.", store.state.value.createNote.motif)
    }

    @Test
    fun `an available capability lets the intent reach the journal`() = runTest {
        val store = TourStore()
        val gateway = StubGateway(CapabilityPresentation(true))
        val dispatcher = ActionDispatcher(store, gateway)
        store.publishCreateNoteAvailability(gateway.createNoteAvailability())

        assertTrue(store.state.value.createNote.enabled)
        // backgroundScope : l'intention reste en vol jusqu'à un outcome qui n'arrive pas,
        // donc la coroutine ne doit pas bloquer la fin du test.
        backgroundScope.launch { dispatcher.createNote() }
        withTimeout(1_000) { while (store.state.value.activity.isEmpty()) delay(1) }

        assertEquals(ActivityStatus.Pending, store.state.value.activity.single().status)
    }
}
