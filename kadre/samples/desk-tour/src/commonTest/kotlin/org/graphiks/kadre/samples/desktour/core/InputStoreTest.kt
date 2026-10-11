package org.graphiks.kadre.samples.desktour.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InputStoreTest {
    @Test
    fun `no input is observed before the host publishes some`() {
        assertNull(TourStore().state.value.input, "l'entrée non observée n'est pas un état vide affiché")
    }

    @Test
    fun `publishing input replaces the observed presentation`() {
        val store = TourStore()
        val presentation = InputPresentation(
            modifiers = listOf("Maj"),
            pointers = emptyList(),
            pressedKeyCount = 2,
            lastEvent = "Touche — A",
            features = emptyList(),
        )

        store.publishInput(presentation)

        assertEquals(2, store.state.value.input!!.pressedKeyCount)
        assertEquals("Touche — A", store.state.value.input!!.lastEvent)
    }
}
