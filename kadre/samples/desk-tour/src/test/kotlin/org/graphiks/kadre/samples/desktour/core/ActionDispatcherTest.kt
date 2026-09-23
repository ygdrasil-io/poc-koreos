package org.graphiks.kadre.samples.desktour.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActionDispatcherTest {
    private class ScriptedGateway(
        private val gate: CompletableDeferred<NoteOpenOutcome> = CompletableDeferred(),
        private val availability: CapabilityPresentation = CapabilityPresentation(enabled = true),
    ) : TestTourGateway() {
        var calls = 0
        override fun createNoteAvailability(): CapabilityPresentation = availability
        override suspend fun openNote(): NoteOpenOutcome {
            calls++
            return gate.await()
        }
    }

    @Test
    fun `an admitted action stays pending until its public outcome arrives`() = runTest {
        val store = TourStore()
        val gateway = ScriptedGateway()
        val dispatcher = ActionDispatcher(store, gateway)

        val job = launch { dispatcher.createNote() }
        withTimeout(1_000) { while (store.state.value.activity.isEmpty()) delay(1) }

        assertEquals(ActivityStatus.Pending, store.state.value.activity.single().status)
        job.cancelAndJoin()
    }

    @Test
    fun `a refused open leaves the journal rejected and never succeeded`() = runTest {
        val store = TourStore()
        val gateway = ScriptedGateway(CompletableDeferred(NoteOpenOutcome.Refused("Non pris en charge par le host courant.")))
        ActionDispatcher(store, gateway).createNote()

        val entry = store.state.value.activity.single()
        assertEquals(ActivityStatus.Rejected, entry.status)
        assertTrue(entry.motif!!.isNotBlank())
    }

    @Test
    fun `an opened note is journaled as succeeded and added to the open list`() = runTest {
        val store = TourStore()
        val note = DeskTourNote(NoteKey(1L), "Notes", NoteControls(true, true, true, true))
        val gateway = ScriptedGateway(CompletableDeferred(NoteOpenOutcome.Opened(note)))
        ActionDispatcher(store, gateway).createNote()

        assertEquals(ActivityStatus.Succeeded, store.state.value.activity.single().status)
        assertEquals(
            listOf(note),
            store.state.value.notes,
            "une note que l'interface ne peut pas voir est une note qui n'existe pas",
        )
    }

    @Test
    fun `an in-flight action whose coroutine is cancelled resolves as cancelled`() = runTest {
        val store = TourStore()
        val gateway = ScriptedGateway()
        val dispatcher = ActionDispatcher(store, gateway)

        val job: Job = launch { dispatcher.createNote() }
        withTimeout(1_000) { while (store.state.value.activity.isEmpty()) delay(1) }
        job.cancelAndJoin()

        assertEquals(ActivityStatus.Cancelled, store.state.value.activity.single().status)
    }

    @Test
    fun `a second intent while one is in flight is ignored and reaches Kadre only once`() = runTest {
        val store = TourStore()
        val gateway = ScriptedGateway()
        val dispatcher = ActionDispatcher(store, gateway)

        val first = launch { dispatcher.createNote() }
        withTimeout(1_000) { while (store.state.value.activity.isEmpty()) delay(1) }
        val second = launch { dispatcher.createNote() }
        delay(100)

        assertEquals(1, store.state.value.activity.size, "a double click must not double the journal")
        assertEquals(1, gateway.calls, "a double click must not open a second Kadre window")

        first.cancelAndJoin()
        second.cancelAndJoin()
    }
}
