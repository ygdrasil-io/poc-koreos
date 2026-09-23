package org.graphiks.kadre.samples.desktour.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InputEventSummaryTest {
    @Test
    fun `a summarised event renders its kind and detail`() {
        assertEquals("Touche — A", renderEventSummary(InputEventSummary("Touche", "A")))
    }

    @Test
    fun `an event without a detail renders its kind alone`() {
        assertEquals("Touche", renderEventSummary(InputEventSummary("Touche", null)))
    }

    @Test
    fun `a blank detail is treated as no detail`() {
        assertEquals("Touche", renderEventSummary(InputEventSummary("Touche", "   ")))
    }

    @Test
    fun `an event the demo cannot name still renders something readable`() {
        val rendered = renderEventSummary(InputEventSummary("Événement non identifié", null))
        assertTrue(rendered.isNotBlank())
        assertEquals("Événement non identifié", rendered)
    }
}
