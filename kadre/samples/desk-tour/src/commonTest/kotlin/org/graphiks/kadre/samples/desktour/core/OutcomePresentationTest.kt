package org.graphiks.kadre.samples.desktour.core

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.window.RejectedWindowField
import org.graphiks.kadre.window.WindowProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutcomePresentationTest {
    @Test
    fun `a partially applied outcome separates applied and rejected fields`() {
        val presented = presentWindowUpdate(
            applied = listOf(WindowProperty.Title),
            rejected = listOf(
                RejectedWindowField(WindowProperty.Blur, KadreFailure.Unsupported(KadreOperation.UpdateWindow)),
            ),
        )

        assertEquals(listOf(WindowProperty.Title.readableName()), presented.applied)
        assertEquals(1, presented.rejected.size)
        assertEquals(WindowProperty.Blur.readableName(), presented.rejected.single().fieldLabel)
        assertTrue(presented.rejected.single().motif.isNotBlank())
    }

    @Test
    fun `an applied outcome reports no rejected field`() {
        val presented = presentWindowUpdate(applied = listOf(WindowProperty.Title), rejected = emptyList())
        assertEquals(listOf("Titre"), presented.applied)
        assertTrue(presented.rejected.isEmpty())
    }

    @Test
    fun `every window property has a non-blank readable name`() {
        WindowProperty.entries.forEach {
            assertTrue(it.readableName().isNotBlank(), "missing label for $it")
        }
    }
}
