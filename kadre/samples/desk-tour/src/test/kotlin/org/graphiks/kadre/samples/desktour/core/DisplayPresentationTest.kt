package org.graphiks.kadre.samples.desktour.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
class DisplayPresentationTest {
    @Test
    fun `an enumerated inventory lists its screens`() {
        val presentation = DisplayPresentation.Enumerated(
            listOf(
                screenEntryOf("Écran principal", 3440, 1440, 1.0, DisplayModeLabel(3440, 1440, 60.0), isPrimary = true),
            ),
        )

        assertEquals(1, presentation.entries.size)
        assertTrue(presentation.entries.single().isPrimary)
        assertEquals("3440 × 1440 @ 60 Hz", presentation.entries.single().modeLabel)
    }

    @Test
    fun `a screen without a current mode shows no mode rather than an invented one`() {
        val entry = screenEntryOf("Écran", 1920, 1080, 1.0, mode = null, isPrimary = false)
        assertNull(entry.modeLabel)
    }

    @Test
    fun `a screen with no reported name is still listed`() {
        val entry = screenEntryOf(null, 800, 600, 2.0, mode = null, isPrimary = false)
        assertEquals("Écran sans nom", entry.name)
    }

    @Test
    fun `a mode without a refresh rate still describes its size`() {
        val entry = screenEntryOf("Écran", 1920, 1080, 1.0, DisplayModeLabel(1920, 1080, null), isPrimary = false)
        assertEquals("1920 × 1080", entry.modeLabel)
    }

    @Test
    fun `the permission-required state is its own state and never an empty list`() {
        val presentation: DisplayPresentation = DisplayPresentation.NeedsPermission
        assertTrue(presentation !is DisplayPresentation.Enumerated, "un inventaire vide ne remplace jamais cet état")
    }

    @Test
    fun `only the states that can be resolved offer the access action`() {
        assertTrue(DisplayPresentation.NeedsPermission.offersAccess())
        assertTrue(DisplayPresentation.Denied(canRequestAgain = true).offersAccess())
        assertTrue(!DisplayPresentation.Denied(canRequestAgain = false).offersAccess(), "un refus définitif n'offre rien")
        assertTrue(DisplayPresentation.Unavailable("motif", retryable = true).offersAccess())
        assertTrue(!DisplayPresentation.Unavailable("motif", retryable = false).offersAccess())
        assertTrue(!DisplayPresentation.Enumerated(emptyList()).offersAccess(), "un inventaire n'a rien à demander")
    }

    @Test
    fun `a fractional refresh rate is not rounded into an invented value`() {
        val entry = screenEntryOf("Écran", 3008, 1692, 2.0, DisplayModeLabel(3008, 1692, 59.94), isPrimary = false)
        assertEquals("3008 × 1692 @ 59.94 Hz", entry.modeLabel)
    }

    @Test
    fun `a disconnected screen keeps its connection state`() {
        val entry = screenEntryOf("Écran", 800, 600, 1.0, null, isPrimary = false, connected = false)
        assertTrue(!entry.connected)
    }
}
