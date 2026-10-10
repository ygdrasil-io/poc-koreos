package org.graphiks.kadre.samples.desktour.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoteStoreTest {
    private val controls = NoteControls(
        canRename = true, canRequestAttention = true, canChangeDecorations = false, canClose = true,
    )

    @Test
    fun `a published note appears in the state`() {
        val store = TourStore()
        store.publishNote(DeskTourNote(NoteKey(1L), "Notes", controls))
        assertEquals(1, store.state.value.notes.size)
        assertEquals("Notes", store.state.value.notes.single().title)
    }

    @Test
    fun `publishing the same key again updates the note instead of duplicating it`() {
        val store = TourStore()
        store.publishNote(DeskTourNote(NoteKey(1L), "Notes", controls))
        store.publishNote(DeskTourNote(NoteKey(1L), "Notes renommée", controls))

        assertEquals(1, store.state.value.notes.size)
        assertEquals("Notes renommée", store.state.value.notes.single().title)
    }

    @Test
    fun `removing a note drops it and leaves the others`() {
        val store = TourStore()
        store.publishNote(DeskTourNote(NoteKey(1L), "A", controls))
        store.publishNote(DeskTourNote(NoteKey(2L), "B", controls))

        store.removeNote(NoteKey(1L))

        assertEquals(listOf("B"), store.state.value.notes.map { it.title })
    }

    @Test
    fun `a disabled control is visible as disabled and not inferred from another note`() {
        val store = TourStore()
        store.publishNote(DeskTourNote(NoteKey(1L), "A", controls.copy(canChangeDecorations = false)))
        store.publishNote(DeskTourNote(NoteKey(2L), "B", controls.copy(canChangeDecorations = true)))

        assertTrue(store.state.value.notes.none { it.key == NoteKey(1L) && it.capabilities.canChangeDecorations })
        assertTrue(store.state.value.notes.any { it.key == NoteKey(2L) && it.capabilities.canChangeDecorations })
    }
}
