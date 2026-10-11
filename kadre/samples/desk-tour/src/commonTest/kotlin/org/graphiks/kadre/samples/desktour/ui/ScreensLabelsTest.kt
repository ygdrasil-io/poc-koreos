package org.graphiks.kadre.samples.desktour.ui

import org.graphiks.kadre.samples.desktour.core.DisplayPresentation
import org.graphiks.kadre.samples.desktour.core.screenEntryOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreensLabelsTest {
    @Test
    fun `an unobserved inventory says so instead of showing an empty list`() {
        assertEquals("Les écrans n'ont pas encore été observés.", ScreensViewLabels.forInventory(null))
    }

    @Test
    fun `an enumerated non-empty inventory adds no message`() {
        val enumerated = DisplayPresentation.Enumerated(
            listOf(screenEntryOf("Écran", 800, 600, 1.0, null, isPrimary = true)),
        )
        assertNull(ScreensViewLabels.forInventory(enumerated))
    }

    @Test
    fun `an empty enumerated inventory says the host reports none`() {
        assertEquals(
            "Aucun écran n'est rapporté par le host.",
            ScreensViewLabels.forInventory(DisplayPresentation.Enumerated(emptyList())),
        )
    }

    @Test
    fun `a permission-required state names the permission`() {
        val label = ScreensViewLabels.forInventory(DisplayPresentation.NeedsPermission)
        assertTrue(label!!.contains("permission"), "l'état doit nommer la permission : $label")
    }

    @Test
    fun `a definitive denial states that no retry is possible`() {
        val label = ScreensViewLabels.forInventory(DisplayPresentation.Denied(canRequestAgain = false))!!
        assertTrue(label.contains("Aucune nouvelle tentative"), "attendu une phrase qui exclut le réessai : $label")
    }

    @Test
    fun `a denial that can be retried says a retry is possible`() {
        val label = ScreensViewLabels.forInventory(DisplayPresentation.Denied(canRequestAgain = true))!!
        assertTrue(label.contains("nouvelle tentative est possible"), "attendu une phrase qui ouvre le réessai : $label")
    }

    @Test
    fun `a retryable unavailability does not read as a failure`() {
        val label = ScreensViewLabels.forInventory(
            DisplayPresentation.Unavailable("Action temporairement indisponible, réessayable.", retryable = true),
        )!!
        assertTrue(label.contains("demandez l'accès"), "l'état doit dire quoi faire : $label")
        assertTrue(!label.contains("indisponible"), "il ne doit pas se lire comme un échec : $label")
    }

    @Test
    fun `a definitive unavailability keeps the host motif`() {
        val label = ScreensViewLabels.forInventory(DisplayPresentation.Unavailable("Non pris en charge.", retryable = false))
        assertEquals("Non pris en charge.", label)
    }
}
