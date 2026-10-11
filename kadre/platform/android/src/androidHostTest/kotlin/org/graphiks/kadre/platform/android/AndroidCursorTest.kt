package org.graphiks.kadre.platform.android

import android.view.PointerIcon
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.surface.BinaryImage
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorImage
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.ImageFormat
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.RejectedSurfaceField
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceProperty
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceUpdate
import org.graphiks.kadre.surface.SurfaceUpdateOutcome
import org.graphiks.kadre.surface.SurfaceVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Les six comportements normatifs du champ `cursor` committable (phase 4, tâche 2) : le commit
 * `System(CursorIcon)` par le seam du pointeur avec le TYPE passé en readback, le refus champ
 * par champ d'une icône sans équivalent Android (la table est TOTALE — mappée ou nommée
 * non-mappable), le commit `Hidden` comme icône null, le refus de `Custom(image)` (la
 * capability `customCursor` reste Unsupported — aucun chemin image→bitmap en phase 4),
 * l'annonce de la capability à l'installation — exactement l'ensemble mappable, jamais avant
 * l'install, jamais sans seam natif — et la table elle-même : les 23 icônes, chacune mappée
 * vers la constante TYPE_* documentée ou nommée non-mappable avec sa raison.
 *
 * Le seam natif est un recorder : les tests hôtes n'ont ni View ni pointeur — l'implémentation
 * production ([AndroidViewCursorNative]) pose `View.pointerIcon` sur le thread principal, le
 * recorder prouve CE QUE LA SURFACE LUI PASSE, et rien de plus.
 */
class AndroidCursorTest {

    /** Le recorder du seam — chaque type passé (null = Hidden) est retenu dans l'ordre. */
    private class RecordingCursorNative : AndroidCursorNative {
        val calls = mutableListOf<Int?>()

        override fun setPointerIcon(type: Int?) {
            calls += type
        }
    }

    /** La surface sous test : état initial honnête (cursor `System(Default)`), clock inerte. */
    private fun surface(cursorNative: AndroidCursorNative? = null): AndroidHostSurface =
        AndroidHostSurface(
            id = RuntimeProcessIds.nextSurfaceId(),
            initialState = initialSurfaceStateForCursorTest(),
            frameClock = { InertCursorFrameClock },
            cursorNative = cursorNative,
        )

    @Test
    fun systemCursorCommitsWithReadback() = runBlocking {
        val seam = RecordingCursorNative()
        val surface = surface(cursorNative = seam)
        surface.observationInstalled(inputWired = false)

        val outcome = surface.apply(
            SurfaceUpdate(cursor = PropertyChange.Set(CursorStyle.System(CursorIcon.Crosshair))),
        )

        val applied = assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(outcome)
        val result = assertIs<SurfaceUpdateOutcome.Applied>(applied.value)
        assertEquals(
            CursorStyle.System(CursorIcon.Crosshair),
            result.state.cursor,
            "the committed state carries the requested style",
        )
        assertEquals(
            SurfaceRevision(1L),
            result.state.revision,
            "a commit that moved the state bumps the revision once",
        )
        assertEquals(
            listOf<Int?>(PointerIcon.TYPE_CROSSHAIR),
            seam.calls,
            "the seam received exactly the TYPE_* constant of the mapping table",
        )
    }

    @Test
    fun unmappableCursorIsPerFieldRejected() = runBlocking {
        val seam = RecordingCursorNative()
        val surface = surface(cursorNative = seam)
        surface.observationInstalled(inputWired = false)

        // `Progress` (occupé mais responsive) n'a pas d'équivalent Android honnête — la table
        // le nomme non-mappable et l'admission le refuse champ par champ.
        val outcome = surface.apply(
            SurfaceUpdate(cursor = PropertyChange.Set(CursorStyle.System(CursorIcon.Progress))),
        )

        val applied = assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(outcome)
        val partial = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(applied.value)
        assertEquals(
            RejectedSurfaceField(
                SurfaceProperty.Cursor,
                KadreFailure.Unsupported(KadreOperation.UpdateSurface),
            ),
            partial.rejected.single(),
            "an unmappable icon is a per-field refusal, never an outer failure",
        )
        assertEquals(
            CursorStyle.System(CursorIcon.Default),
            partial.state.cursor,
            "the refused field left the committed cursor untouched",
        )
        assertEquals(
            SurfaceRevision(0L),
            partial.state.revision,
            "a refusal alone moves neither state nor revision",
        )
        assertTrue(seam.calls.isEmpty(), "a refused field calls no native primitive")
    }

    @Test
    fun hiddenCommitsAsNullIcon() = runBlocking {
        val seam = RecordingCursorNative()
        val surface = surface(cursorNative = seam)
        surface.observationInstalled(inputWired = false)

        val outcome = surface.apply(
            SurfaceUpdate(cursor = PropertyChange.Set(CursorStyle.Hidden)),
        )

        val applied = assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(outcome)
        val result = assertIs<SurfaceUpdateOutcome.Applied>(applied.value)
        assertEquals(CursorStyle.Hidden, result.state.cursor, "Hidden is committed as the state")
        assertEquals(SurfaceRevision(1L), result.state.revision)
        assertEquals(
            listOf<Int?>(null),
            seam.calls,
            "Hidden reaches the seam as a null type — the transparent icon contract",
        )
    }

    @Test
    fun customCursorIsPerFieldRejected() = runBlocking {
        val seam = RecordingCursorNative()
        val surface = surface(cursorNative = seam)
        surface.observationInstalled(inputWired = false)

        val outcome = surface.apply(
            SurfaceUpdate(cursor = PropertyChange.Set(CursorStyle.Custom(testCursorImage()))),
        )

        val applied = assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(outcome)
        val partial = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(applied.value)
        assertEquals(
            RejectedSurfaceField(
                SurfaceProperty.Cursor,
                KadreFailure.Unsupported(KadreOperation.UpdateSurface),
            ),
            partial.rejected.single(),
            "Custom(image) consults the customCursor capability and is refused by it",
        )
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface)),
            surface.capabilities.value.customCursor,
            "the customCursor capability stays Unsupported — no image-to-bitmap path in phase 4",
        )
        assertEquals(CursorStyle.System(CursorIcon.Default), partial.state.cursor)
        assertEquals(SurfaceRevision(0L), partial.state.revision)
        assertTrue(seam.calls.isEmpty(), "a refused field calls no native primitive")
    }

    @Test
    fun cursorCapabilityAnnouncesTheMappableSet() = runBlocking {
        val seam = RecordingCursorNative()
        val surface = surface(cursorNative = seam)

        // Pré-install : rien n'est annoncé, même avec le seam câblé — la discipline « rien de
        // revendiqué avant l'installation » de `inputDefaultBehavior` (phase 3, tâche 4).
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface)),
            surface.capabilities.value.cursor,
            "pre-install the cursor capability keeps its refusal",
        )
        val pre = surface.apply(
            SurfaceUpdate(cursor = PropertyChange.Set(CursorStyle.System(CursorIcon.Crosshair))),
        )
        assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(pre).value,
            "pre-install a posed cursor field is refused per-field",
        )
        assertTrue(seam.calls.isEmpty(), "pre-install no native primitive is claimed")

        // Post-install avec seam : exactement l'ensemble mappable, disponible.
        surface.observationInstalled(inputWired = false)
        assertEquals(
            Capability.Supported(AndroidCursorIcons.committableIcons, FeatureAvailability.Available),
            surface.capabilities.value.cursor,
            "post-install the capability announces exactly the mappable set, available",
        )

        // Post-install SANS seam : aucun claim — un attach sans pointeur natif ne promet pas
        // un commit qui ne peut jamais arriver (la sémantique C, le miroir `inputWired`).
        val seamLess = surface(cursorNative = null)
        seamLess.observationInstalled(inputWired = false)
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface)),
            seamLess.capabilities.value.cursor,
            "without the native seam the refusal holds after install too",
        )
    }

    @Test
    fun cursorIconTableIsTotalWithDocumentedDiagonals() {
        // La table totale : exactement les VINGT icônes mappées, les TROIS autres nommées
        // non-mappables avec leur raison (voir la KDoc d'[AndroidCursorIcons.systemIconType]).
        assertEquals(
            setOf(
                CursorIcon.Default,
                CursorIcon.Pointer,
                CursorIcon.Text,
                CursorIcon.Crosshair,
                CursorIcon.Move,
                CursorIcon.Grab,
                CursorIcon.Grabbing,
                CursorIcon.NotAllowed,
                CursorIcon.Wait,
                CursorIcon.Help,
                CursorIcon.ResizeHorizontal,
                CursorIcon.ResizeVertical,
                CursorIcon.ResizeDiagonalNorthWestSouthEast,
                CursorIcon.ResizeDiagonalNorthEastSouthWest,
                CursorIcon.ZoomIn,
                CursorIcon.ZoomOut,
                CursorIcon.ContextMenu,
                CursorIcon.Copy,
                CursorIcon.Alias,
                CursorIcon.Cell,
            ),
            AndroidCursorIcons.committableIcons,
            "the committable set is exactly the mapped icons",
        )
        assertEquals(
            setOf(CursorIcon.Progress, CursorIcon.ResizeColumn, CursorIcon.ResizeRow),
            CursorIcon.entries.toSet() - AndroidCursorIcons.committableIcons,
            "the unmappable icons are exactly the three named in the table",
        )
        // Les diagonales sont directionnelles : NW↔SE est la flèche ancrée en haut à gauche,
        // NE↔SW celle ancrée en haut à droite (les KDoc TYPE_* du SDK).
        assertEquals(
            PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW,
            AndroidCursorIcons.systemIconType(CursorIcon.ResizeDiagonalNorthWestSouthEast),
        )
        assertEquals(
            PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW,
            AndroidCursorIcons.systemIconType(CursorIcon.ResizeDiagonalNorthEastSouthWest),
        )
        assertNull(
            AndroidCursorIcons.systemIconType(CursorIcon.Progress),
            "Progress is named-unmappable: no arrow-plus-busy composite exists",
        )
        assertNull(
            AndroidCursorIcons.systemIconType(CursorIcon.ResizeColumn),
            "ResizeColumn is named-unmappable: no split-bar resize glyph exists",
        )
        assertNull(
            AndroidCursorIcons.systemIconType(CursorIcon.ResizeRow),
            "ResizeRow is named-unmappable: no split-bar resize glyph exists",
        )
    }
}

// ----------------------------------------------------------------------- aides du fichier

/** Une image de curseur 1×1 RGBA, hotspot au coin — le minimum valide pour `Custom`. */
private fun testCursorImage() = CursorImage(
    image = BinaryImage(
        bytes = ByteArray(4),
        format = ImageFormat.Rgba8,
        pixelSize = PhysicalSize(1, 1),
    ),
    hotspot = PhysicalPoint(0, 0),
)

/** L'instantané initial de surface du test — les mêmes valeurs que la couture, base 1×1. */
private fun initialSurfaceStateForCursorTest(): SurfaceState = SurfaceState(
    attachment = SurfaceAttachmentState.Attached,
    logicalSize = LogicalSize(1.0, 1.0),
    physicalSize = PhysicalSize(1, 1),
    scaleFactor = 1.0,
    safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
    visibility = SurfaceVisibility.Visible,
    occlusion = SurfaceOcclusion.Visible,
    focus = SurfaceFocus.Focused,
    appearance = SurfaceAppearance(SurfaceTheme.Unknown, SurfaceContrast.Unknown),
    cursor = CursorStyle.System(CursorIcon.Default),
    pointerCapture = PointerCaptureMode.None,
    hitTesting = HitTestingMode.Enabled,
    inputDefaultBehavior = InputDefaultBehavior.HostDefault,
    revision = SurfaceRevision(0L),
)

/** La clock inerte du test : les frames y sont posées et n'y courent jamais (aucun SDK). */
private object InertCursorFrameClock : AndroidRedrawScheduler.FrameClock {
    override fun schedule(frame: () -> Unit) = Unit
    override fun cancel() = Unit
}
