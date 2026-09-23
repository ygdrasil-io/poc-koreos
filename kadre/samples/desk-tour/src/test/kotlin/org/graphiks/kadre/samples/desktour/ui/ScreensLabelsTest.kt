package org.graphiks.kadre.samples.desktour.ui

import org.graphiks.kadre.samples.desktour.core.DisplayPresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreensLabelsTest {
    @Test
    fun `an unobserved inventory says so instead of showing an empty list`() {
        assertEquals("Les écrans n'ont pas encore été observés.", ScreensViewLabels.forInventory(null))
    }

    @Test
    fun `a permission-required state invites the user to grant access`() {
        val label = ScreensViewLabels.forInventory(DisplayPresentation.NeedsPermission)
        assertTrue(label.contains("permission"), "l'état doit nommer la permission : $label")
    }

    @Test
    fun `a denial that cannot be retried does not invite a retry`() {
        val label = ScreensViewLabels.forInventory(DisplayPresentation.Denied(canRequestAgain = false))
        assertTrue(
            label.contains("nouvelle tentative"),
            "le refus définitif doit dire qu'il n'y a pas de réessai : $label",
        )
    }
}
