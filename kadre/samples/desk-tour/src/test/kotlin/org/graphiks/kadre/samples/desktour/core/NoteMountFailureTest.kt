package org.graphiks.kadre.samples.desktour.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoteMountFailureTest {
    @Test
    fun `a note whose mount failed is marked as failed and keeps its key`() {
        val store = TourStore()
        val note = DeskTourNote(NoteKey(3L), "Notes", NoteControls(true, true, true, true)).withMountFailure()

        store.publishNote(note)

        val stored = store.state.value.notes.single()
        assertTrue(stored.mountFailed)
        assertEquals(NoteKey(3L), stored.key)
    }

    @Test
    fun `marking a mount failure keeps the note title and controls unchanged`() {
        val store = TourStore()
        val controls = NoteControls(true, false, true, true)
        store.publishNote(DeskTourNote(NoteKey(4L), "Notes", controls))

        store.publishNote(store.state.value.notes.single().withMountFailure())

        val stored = store.state.value.notes.single()
        assertEquals("Notes", stored.title)
        assertEquals(controls, stored.capabilities)
    }
}
