package org.graphiks.kadre.platform.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.internal.runtime.fieldName
import org.graphiks.kadre.internal.runtime.unsupportedSurfaceCapabilities
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
import org.graphiks.kadre.surface.SurfaceEvent
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
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Les cinq comportements normatifs de la surface Android observable (phase 2, tâche 1) :
 * l'identité de l'instance au fil des publications, l'`apply` conforme à sa ligne de contrat
 * (aucun champ supporté en phase 2 — `Applied` à vide, `PartiallyApplied` champ par champ sur
 * champs posés, `StaleRevision` avant toute admission), et la forme on-row de `requestRedraw`
 * telle que Task 3 la remplacera par le scheduling.
 */
class AndroidHostSurfaceTest {

    /** La surface sous test : identité du runtime (friend), état initial honnête, sans View. */
    private fun surface(): AndroidHostSurface = AndroidHostSurface(
        id = RuntimeProcessIds.nextSurfaceId(),
        initialState = initialSurfaceStateForTest(),
    )

    @Test
    fun identityPreservedAcrossPublications() {
        val surface = surface()
        val before = surface
        // La publication interne remplace l'ÉTAT, jamais l'instance : le host tient la même
        // surface tant qu'il demeure (invariant d'identité de la couture P1).
        surface.publishMetrics(update = { it.copy(physicalSize = it.physicalSize.copy(width = 800)) })
        assertSame(before, surface)
        // La publication a bien déplacé l'état publié (révision = marqueur de changement).
        assertEquals(PhysicalSize(800, 1), surface.state.value.physicalSize)
        assertEquals(SurfaceRevision(1L), surface.state.value.revision)
    }

    @Test
    fun applyEmptyUpdateIsAppliedCurrentState() = runBlocking {
        val surface = surface()
        val outcome = surface.apply(SurfaceUpdate()) // aucun champ posé
        val applied = assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(outcome)
        val result = assertIs<SurfaceUpdateOutcome.Applied>(applied.value)
        // Aucun mouvement : l'état rendu est l'instantané courant, révision intacte.
        assertEquals(SurfaceRevision(0L), result.state.revision)
    }

    @Test
    fun applyPosedFieldsArePartiallyAppliedWithUnsupportedPerField() = runBlocking {
        val surface = surface()
        val update = SurfaceUpdate(cursor = PropertyChange.Set(CursorStyle.System(CursorIcon.Default)))
        val outcome = surface.apply(update)
        val applied = assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(outcome)
        val partial = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(applied.value)
        assertEquals(1, partial.rejected.size)
        assertEquals(
            RejectedSurfaceField(
                SurfaceProperty.Cursor,
                KadreFailure.Unsupported(KadreOperation.UpdateSurface),
            ),
            partial.rejected.single(),
        )
        // Les champs non posés ne sont pas rejetés ; la révision ne bouge pas.
        assertEquals(SurfaceRevision(0L), partial.state.revision)
    }

    @Test
    fun staleRevisionIsRejectedBeforeAdmission() = runBlocking {
        val surface = surface()
        val outcome = surface.apply(SurfaceUpdate(expectedRevision = SurfaceRevision(999L)))
        val failure = assertIs<KadreResult.Failure>(outcome)
        assertEquals(KadreFailure.StaleRevision(999L, 0L), failure.reason)
    }

    @Test
    fun clearOnAFieldWithoutUnsetValueIsInvalidRequest() = runBlocking {
        // La garde `Clear` (avant toute admission) : un champ qui n'a pas de valeur « non
        // posée » ne peut pas être effacé — `Invalid(field)` sur le nom du registre §1.1.
        val surface = surface()
        val outcome = surface.apply(SurfaceUpdate(cursor = PropertyChange.Clear))
        val failure = assertIs<KadreResult.Failure>(outcome)
        assertEquals(KadreFailure.InvalidRequest(SurfaceProperty.Cursor.fieldName), failure.reason)
        // Et un champ posé AVANT le champ Clear reste hors de cause : le premier Clear gagne.
        val second = surface.apply(
            SurfaceUpdate(
                cursor = PropertyChange.Set(CursorStyle.System(CursorIcon.Default)),
                pointerCapture = PropertyChange.Clear,
            ),
        )
        val secondFailure = assertIs<KadreResult.Failure>(second)
        assertEquals(
            KadreFailure.InvalidRequest(SurfaceProperty.PointerCapture.fieldName),
            secondFailure.reason,
        )
    }

    @Test
    fun closeRemovesCapabilitiesBeforePublishingTheDetachedState() = runBlocking {
        val surface = surface()
        val detachedSeen = CompletableDeferred<Unit>()
        val collector = async(start = CoroutineStart.UNDISPATCHED) {
            surface.state.collect { state ->
                if (state.attachment == SurfaceAttachmentState.Detached) detachedSeen.complete(Unit)
            }
        }
        repeat(8) { yield() }
        surface.close()
        withTimeout(10_000) { detachedSeen.await() }
        // Au moment où l'état détaché devient visible, les capabilities sont DÉJÀ retirées :
        // elles sont indisponibles au plus tard au détached, ici avant (l'ordre du terminal).
        assertEquals(unsupportedSurfaceCapabilities(), surface.capabilities.value)
        collector.cancel()
    }

    @Test
    fun eventsFlowCompletesAtTerminal() = runBlocking {
        val surface = surface()
        val finished = CompletableDeferred<List<SurfaceEvent>>()
        val collector = async(start = CoroutineStart.UNDISPATCHED) {
            val seen = mutableListOf<SurfaceEvent>()
            surface.events.collect { event -> seen += event }
            finished.complete(seen)
        }
        repeat(8) { yield() }
        surface.close()
        // Le flux d'événements se termine au terminal : le collecteur reçoit la fin (et une
        // collecte arrivant après la fermeture verrait un flux déjà terminé).
        val seen = withTimeout(10_000) { finished.await() }
        assertTrue(seen.isEmpty(), "no event can precede the terminal on a silent surface")
        collector.cancel()
    }

    @Test
    fun requestRedrawBeforeSchedulingAdmitsOrAnswersOnRow() {
        // Task 3 branche le scheduling ; en Task 1 la forme on-row est déjà fixée :
        // `Success` (scheduler branché) ou `Failure(Unsupported(RequestRedraw))` /
        // `Failure(Closed(Surface))` (surface terminale) — jamais rien d'autre.
        val surface = surface()
        val result = surface.requestRedraw()
        assertTrue(
            result is KadreResult.Success ||
                (result is KadreResult.Failure && (result.reason == KadreFailure.Unsupported(KadreOperation.RequestRedraw) || result.reason == KadreFailure.Closed(KadreResourceKind.Surface))),
            "requestRedraw answered off-row: $result",
        )
    }
}

/** L'instantané initial de surface du test — les mêmes valeurs que la couture, base 1×1. */
private fun initialSurfaceStateForTest(): SurfaceState = SurfaceState(
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
