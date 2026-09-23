package org.graphiks.kadre.samples.desktour.ui

import org.graphiks.kadre.samples.desktour.core.CapabilityPresentation
import org.graphiks.kadre.samples.desktour.core.InputFeature
import org.graphiks.kadre.samples.desktour.core.InputPresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InteractionsLabelsTest {
    private fun presentation(
        lastEvent: String? = null,
        modifiers: List<String> = emptyList(),
    ) = InputPresentation(
        modifiers = modifiers,
        pointers = emptyList(),
        pressedKeyCount = 0,
        lastEvent = lastEvent,
        features = listOf(InputFeature("Clavier", CapabilityPresentation(enabled = true))),
    )

    @Test
    fun `nothing observed yet says so`() {
        assertEquals("Aucune entrée n'a encore été observée.", InteractionsViewLabels.observed(null))
    }

    @Test
    fun `no modifier reads as none rather than an empty string`() {
        val label = InteractionsViewLabels.observed(presentation())!!
        assertTrue(label.contains("Modificateurs : aucun"), "attendu une mention explicite de l'absence : $label")
    }

    @Test
    fun `an observed event is shown as is`() {
        assertTrue(InteractionsViewLabels.observed(presentation(lastEvent = "Touche — A"))!!.contains("Touche — A"))
    }

    @Test
    fun `an unavailable feature shows its motif`() {
        val feature = InputFeature(
            "Saisie de texte",
            CapabilityPresentation(false, "Non pris en charge par le host courant."),
        )
        assertEquals("Non pris en charge par le host courant.", InteractionsViewLabels.featureDetail(feature))
    }

    @Test
    fun `an available feature shows no detail`() {
        assertNull(InteractionsViewLabels.featureDetail(InputFeature("Clavier", CapabilityPresentation(true))))
    }
}
