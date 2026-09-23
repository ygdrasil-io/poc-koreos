package org.graphiks.kadre.samples.desktour.core

import org.graphiks.kadre.surface.SurfaceFocus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WindowPresentationTest {
    @Test
    fun `both focus states reach the screen in french, never as an enum name`() {
        val labels = SurfaceFocus.entries.map(::windowFocusLabel)

        assertEquals(listOf("au premier plan", "en arrière-plan"), labels)
        labels.forEach { label ->
            SurfaceFocus.entries.forEach { focus -> assertFalse(label.contains(focus.name), label) }
        }
    }
}
