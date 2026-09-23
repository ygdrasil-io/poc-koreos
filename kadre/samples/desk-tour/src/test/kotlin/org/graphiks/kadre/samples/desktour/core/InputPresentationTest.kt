package org.graphiks.kadre.samples.desktour.core

import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.input.GestureKind
import org.graphiks.kadre.input.InputCapabilities
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.ModifierKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.input.KadrePermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InputPresentationTest {
    private fun available() = FeatureAvailability.Available

    private fun capabilities(
        keyboard: FeatureAvailability = available(),
        textInput: Capability<Unit> = Capability.Supported(Unit, available()),
        gestures: Capability<Set<GestureKind>> = Capability.Supported(setOf(GestureKind.Pan), available()),
        rawInput: Capability<Unit> = Capability.Supported(Unit, available()),
    ) = InputCapabilities(
        keyboard = keyboard,
        pointer = available(),
        touch = available(),
        gestures = gestures,
        dragAndDrop = available(),
        textInput = textInput,
        rawInput = rawInput,
    )

    @Test
    fun `an unsupported keyboard is disabled and explains why`() {
        val feature = inputFeatures(capabilities(keyboard = FeatureAvailability.Unsupported))
            .single { it.label == "Clavier" }
        assertEquals(false, feature.presentation.enabled)
        assertTrue(feature.presentation.motif!!.isNotBlank())
    }

    @Test
    fun `a host that does not publish the keyboard is not shown as a broken counter`() {
        val feature = inputFeatures(
            capabilities(keyboard = FeatureAvailability.Unavailable(KadreFailure.TemporarilyUnavailable(retryable = true))),
        ).single { it.label == "Clavier" }
        assertEquals(false, feature.presentation.enabled)
        assertTrue(feature.presentation.motif!!.isNotBlank())
    }

    @Test
    fun `partially supported gestures name the supported kinds in plain language`() {
        val feature = inputFeatures(
            capabilities(gestures = Capability.Supported(setOf(GestureKind.Pan, GestureKind.Pinch), available())),
        ).single { it.label == "Gestes" }
        assertTrue(feature.presentation.enabled)
        val motif = feature.presentation.motif!!
        assertTrue(
            motif.contains("glissement") && motif.contains("pincement"),
            "les gestes supportés doivent être nommés en français : $motif",
        )
        assertTrue(!motif.contains("Pan") && !motif.contains("Pinch"), "aucun nom d'énumération à l'écran : $motif")
    }

    @Test
    fun `an enabled gesture capability never loses its reason when it carries one`() {
        val feature = inputFeatures(
            capabilities(
                gestures = Capability.Supported(
                    setOf(GestureKind.Pan),
                    FeatureAvailability.RequiresPermission(KadrePermission.InputMonitoring),
                ),
            ),
        ).single { it.label == "Gestes" }
        assertTrue(!feature.presentation.enabled)
        assertTrue(
            feature.presentation.motif!!.contains("permission"),
            "la raison d'indisponibilité ne doit pas être écrasée par la liste des gestes : ${feature.presentation.motif}",
        )
    }

    @Test
    fun `every input capability is listed even when unsupported`() {
        val labels = inputFeatures(capabilities()).map { it.label }
        assertEquals(
            listOf("Clavier", "Pointeur", "Tactile", "Gestes", "Glisser-déposer", "Saisie de texte", "Entrées brutes"),
            labels,
        )
    }

    @Test
    fun `an unsupported text input is disabled with a motif`() {
        val feature = inputFeatures(
            capabilities(textInput = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.TextInput))),
        ).single { it.label == "Saisie de texte" }
        assertEquals(false, feature.presentation.enabled)
        assertTrue(feature.presentation.motif!!.isNotBlank())
    }

    @Test
    fun `a pointer without a known position has no coordinates rather than invented ones`() {
        val summary = pointerSummaries(
            listOf(PointerSnapshot(PointerKind.Mouse, null, null, emptyList())),
        ).single()
        assertNull(summary.x)
        assertNull(summary.y)
    }

    @Test
    fun `a pointer reports its position and buttons`() {
        val summary = pointerSummaries(
            listOf(PointerSnapshot(PointerKind.Mouse, 12.0, 34.0, listOf(PointerButton.Primary))),
        ).single()
        assertEquals(12.0, summary.x)
        assertEquals(34.0, summary.y)
        assertEquals(1, summary.buttons.size)
    }

    @Test
    fun `no modifier is an empty list and modifiers are named in a stable order`() {
        assertTrue(modifierLabels(KeyboardModifiers(emptySet())).isEmpty())
        assertEquals(
            listOf("Maj", "Contrôle"),
            modifierLabels(KeyboardModifiers(setOf(ModifierKey.Shift, ModifierKey.Control))),
        )
    }
}
