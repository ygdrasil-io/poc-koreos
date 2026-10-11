package org.graphiks.kadre.internal.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
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

/**
 * L'échelle `apply` par champ du port UIKit — le miroir d'`AndroidHostSurface.apply`
 * (l.204-260) en kernel pur, sans UIKit. `Closed(Surface)` et `StaleRevision` restent dans le
 * port (état vivant) : le kernel ne juge que l'échelle des champs contre les capabilities
 * publiées (`uikitSurfaceCapabilities()` — les quatre champs update `Unsupported(UpdateSurface)`).
 */
class UIKitApplyLadderTest {
    private val current = ladderState()

    @Test
    fun allUnchangedAppliesTheCurrentState() {
        val outcome = uikitSurfaceApplyOutcome(
            current = current,
            update = SurfaceUpdate(),
            capabilities = uikitSurfaceCapabilities(),
        )
        // Rien de posé → `Applied(current)` : demander la valeur déjà en effet est admis.
        assertEquals(SurfaceUpdateOutcome.Applied(current), outcome)
    }

    @Test
    fun setFieldsAreRejectedPerFieldWithUnsupportedUpdateSurface() {
        val update = SurfaceUpdate(cursor = PropertyChange.Set(CursorStyle.System(CursorIcon.Default)))
        val outcome = uikitSurfaceApplyOutcome(
            current = current,
            update = update,
            capabilities = uikitSurfaceCapabilities(),
        )
        // Le rejet est PAR CHAMP, dans la liste du succès — jamais une failure externe pour
        // l'update entier (miroir Android l.196-197).
        val partial = outcome as SurfaceUpdateOutcome.PartiallyApplied
        assertEquals(current, partial.state)
        assertEquals(
            listOf(
                RejectedSurfaceField(
                    SurfaceProperty.Cursor,
                    KadreFailure.Unsupported(KadreOperation.UpdateSurface),
                ),
            ),
            partial.rejected,
        )
    }

    @Test
    fun clearIsInvalidRequestNotRejected() {
        // La garde du port (miroir Android l.214-216 + l.496-505) : un Clear est rendu
        // `InvalidRequest(field)` AVANT toute admission — jamais un rejet de capability.
        assertEquals(
            "hitTesting",
            invalidSurfaceClearField(SurfaceUpdate(hitTesting = PropertyChange.Clear)),
        )
        assertNull(
            invalidSurfaceClearField(SurfaceUpdate(hitTesting = PropertyChange.Set(HitTestingMode.Disabled))),
        )
        // Le kernel ne peut pas en voir : un Clear qui lui arriverait est une erreur de
        // programmation criante (le port l'intercepte avant l'échelle), pas un rejet muet.
        assertFailsWith<IllegalStateException> {
            uikitSurfaceApplyOutcome(
                current = current,
                update = SurfaceUpdate(hitTesting = PropertyChange.Clear),
                capabilities = uikitSurfaceCapabilities(),
            )
        }
    }

    @Test
    fun mixedUnchangedAndSetGivesPartiallyAppliedWithOnlyTheSetFields() {
        val update = SurfaceUpdate(
            cursor = PropertyChange.Set(CursorStyle.System(CursorIcon.Pointer)),
            hitTesting = PropertyChange.Set(HitTestingMode.Disabled),
        )
        val outcome = uikitSurfaceApplyOutcome(
            current = current,
            update = update,
            capabilities = uikitSurfaceCapabilities(),
        )
        val partial = outcome as SurfaceUpdateOutcome.PartiallyApplied
        assertEquals(current, partial.state)
        // Seuls les champs posés sont rejetés, dans l'ordre de déclaration de `SurfaceProperty`
        // (Cursor avant HitTesting) — l'ordre où le kernel les admet, une seule passe.
        assertEquals(2, partial.rejected.size)
        assertEquals(
            listOf(SurfaceProperty.Cursor, SurfaceProperty.HitTesting),
            partial.rejected.map { it.field },
        )
        partial.rejected.forEach { rejection ->
            assertEquals(KadreFailure.Unsupported(KadreOperation.UpdateSurface), rejection.failure)
        }
    }
}

/** L'état porteur du test — les mêmes valeurs que le helper Android (base 1×1). */
private fun ladderState(): SurfaceState = SurfaceState(
    attachment = SurfaceAttachmentState.Attached,
    logicalSize = LogicalSize(1.0, 1.0),
    physicalSize = PhysicalSize(1, 1),
    scaleFactor = 1.0,
    safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
    visibility = SurfaceVisibility.Visible,
    occlusion = SurfaceOcclusion.Visible,
    focus = SurfaceFocus.Unfocused,
    appearance = SurfaceAppearance(SurfaceTheme.Unknown, SurfaceContrast.Unknown),
    cursor = CursorStyle.System(CursorIcon.Default),
    pointerCapture = PointerCaptureMode.None,
    hitTesting = HitTestingMode.Enabled,
    inputDefaultBehavior = InputDefaultBehavior.HostDefault,
    revision = SurfaceRevision(0L),
)
