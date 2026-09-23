package org.graphiks.kadre.samples.desktour.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CloseNoteTest {
    private val allControls = NoteControls(true, true, true, true)

    private class Recorder(
        private val controls: NoteControls,
        private val outcome: NoteUpdateOutcome,
    ) : TestTourGateway() {
        var closeCalls = 0
        override fun noteControls(key: NoteKey): NoteControls = controls
        override suspend fun closeNote(key: NoteKey): NoteUpdateOutcome {
            closeCalls++
            return outcome
        }
    }

    @Test
    fun `closing a note journals one entry and removes it from the open list`() = runTest {
        val store = TourStore()
        store.publishNote(DeskTourNote(NoteKey(1L), "Notes", allControls))

        ActionDispatcher(store, Recorder(allControls, NoteUpdateOutcome.Applied)).closeNote(NoteKey(1L))

        assertEquals(ActivityStatus.Succeeded, store.state.value.activity.single().status)
        assertTrue(store.state.value.notes.isEmpty())
    }

    @Test
    fun `two close intents for the same note reach the gateway once`() = runTest {
        val store = TourStore()
        store.publishNote(DeskTourNote(NoteKey(1L), "Notes", allControls))
        val gateway = Recorder(allControls, NoteUpdateOutcome.Applied)
        val dispatcher = ActionDispatcher(store, gateway)

        dispatcher.closeNote(NoteKey(1L))
        dispatcher.closeNote(NoteKey(1L))

        assertEquals(1, gateway.closeCalls, "a note already closing must not be closed twice")
    }

    @Test
    fun `a refused close keeps the note in the open list and is journaled as rejected`() = runTest {
        val store = TourStore()
        store.publishNote(DeskTourNote(NoteKey(1L), "Notes", allControls))
        val gateway = Recorder(allControls, NoteUpdateOutcome.Refused("Cette note n'existe plus."))

        ActionDispatcher(store, gateway).closeNote(NoteKey(1L))

        assertEquals(1, store.state.value.notes.size)
        assertEquals(ActivityStatus.Rejected, store.state.value.activity.single().status)
    }

    @Test
    fun `a note whose close capability is unsupported never reaches the gateway`() = runTest {
        val store = TourStore()
        store.publishNote(DeskTourNote(NoteKey(1L), "Notes", allControls))
        val gateway = Recorder(NoteControls(true, true, true, canClose = false), NoteUpdateOutcome.Applied)

        ActionDispatcher(store, gateway).closeNote(NoteKey(1L))

        assertEquals(0, gateway.closeCalls)
        assertEquals(1, store.state.value.notes.size)
    }
}
