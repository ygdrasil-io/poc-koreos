package org.graphiks.kadre.platform.android

import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeHostSurface
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.internal.runtime.UnsupportedWindowManager
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.window.RejectedWindowField
import org.graphiks.kadre.window.WindowCloseDecision
import org.graphiks.kadre.window.WindowProperty
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowRequestState
import org.graphiks.kadre.window.WindowRevision
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kadre.window.WindowUpdate
import org.graphiks.kadre.window.WindowUpdateOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Les deux topologies de fenêtres Android (phase 1) :
 *
 * - la forme **Activity** installe [AndroidActivityWindowManager] : une fenêtre primaire unique
 *   dont la `surface` est LA MÊME INSTANCE que la surface primaire, et dont `requestWindow` est
 *   rejeté synchrone `Unsupported(RequestWindow)` ;
 * - la forme **View** n'installe PAS ce port : le manager par défaut du runtime
 *   ([UnsupportedWindowManager], celui que `RuntimeHostController.withPrimarySurface` installe
 *   depuis la phase 0) laisse `primary == null` et la liste de fenêtres vide.
 */
class AndroidWindowTopologyTest {

    @Test
    fun activityPrimaryWindowSharesSurfaceIdentity() {
        val surface = primarySurface()
        val manager = AndroidActivityWindowManager(
            primarySurface = surface,
            nextRequestId = RuntimeProcessIds::nextWindowRequestId,
        )
        val state = manager.state.value
        // La fenêtre primaire désigne la MÊME instance de surface que la surface primaire.
        assertSame(surface, state.primary?.surface)
        // Fenêtre unique, et primary est cette fenêtre (invariant `WindowManagerState`).
        assertSame(state.primary, state.windows.single())
    }

    @Test
    fun viewFormHasNullPrimaryAndEmptyWindows() {
        // La forme View ne construit rien : le manager par défaut du runtime — la même
        // construction que `UnsupportedRuntimeSessionComponentsFactory` — est son état.
        val manager = UnsupportedWindowManager(RuntimeProcessIds::nextWindowRequestId)
        assertNull(manager.state.value.primary)
        assertTrue(manager.state.value.windows.isEmpty())
    }

    @Test
    fun requestWindowIsImmediatelyRejectedUnsupported() = runBlocking {
        val manager = AndroidActivityWindowManager(
            primarySurface = primarySurface(),
            nextRequestId = RuntimeProcessIds::nextWindowRequestId,
        )
        val result = manager.requestWindow(WindowSpec())
        val request = when (result) {
            is KadreResult.Success -> result.value
            is KadreResult.Failure ->
                throw AssertionError("requestWindow must admit the request, got $result")
        }
        // Rejet synchrone, sans primitive native : l'outcome est déjà terminal avant que
        // l'appelant n'ait jamais vu la requête (le degenerate shape du modèle web).
        val terminated = request.state.value as WindowRequestState.Terminated
        assertEquals(
            KadreFailure.Unsupported(KadreOperation.RequestWindow),
            (terminated.outcome as WindowRequestOutcome.Rejected).failure,
        )
        // `await()` ne suspend pas et rend le même rejet.
        assertEquals(
            KadreFailure.Unsupported(KadreOperation.RequestWindow),
            (request.await() as WindowRequestOutcome.Rejected).failure,
        )
    }

    @Test
    fun windowCommandsAnswerOnContractRowShapes() = runBlocking {
        val manager = AndroidActivityWindowManager(
            primarySurface = primarySurface(),
            nextRequestId = RuntimeProcessIds::nextWindowRequestId,
        )
        val window = manager.state.value.windows.single()

        // `apply` (contrat §4) : un champ valide mais non supporté se rejette CHAMP PAR CHAMP
        // dans le SUCCÈS `PartiallyApplied` — jamais une failure externe. Chaque champ demandé
        // revient `RejectedWindowField(Unsupported(UpdateWindow))`, aucun champ n'est appliqué :
        // l'état rendu est l'instantané courant, révision intacte, aucun événement.
        val applied = window.apply(WindowUpdate(title = PropertyChange.Set("ignored")))
        val partially = when (applied) {
            is KadreResult.Success -> applied.value as WindowUpdateOutcome.PartiallyApplied
            is KadreResult.Failure ->
                throw AssertionError("apply must answer PartiallyApplied, got $applied")
        }
        assertEquals(
            listOf(
                RejectedWindowField(
                    WindowProperty.Title,
                    KadreFailure.Unsupported(KadreOperation.UpdateWindow),
                ),
            ),
            partially.rejected,
        )
        assertSame(window.state.value, partially.state)
        assertEquals(WindowRevision(0), partially.state.revision)
        // Le champ non demandé n'est pas rejeté ; la garde de révision reste honnête (`Stale`).
        val noop = window.apply(WindowUpdate())
        assertTrue(noop is KadreResult.Success && noop.value is WindowUpdateOutcome.Applied)
        assertEquals(
            KadreFailure.StaleRevision(7L, 0L),
            (window.apply(WindowUpdate(expectedRevision = WindowRevision(7))) as KadreResult.Failure).reason,
        )

        // `close` : ruling provisoire on-row — la fenêtre d'host Activity est structurellement
        // non fermable, la ligne n'admets que `Temporary`/`Platform` ; `Temporary` non
        // retryable dit « sans espoir de succès ici » sans réviser le contrat (décision
        // normative parkée phase 4).
        assertEquals(
            KadreFailure.TemporarilyUnavailable(retryable = false),
            (window.close() as KadreResult.Failure).reason,
        )

        // `respondToCloseRequest` : `events` est vide — aucun `CloseRequested` n'est jamais
        // publié — donc aucun `requestId` ne peut être valide : `Invalid("requestId")`, le
        // field exact du registre `InvalidRequest`.
        assertEquals(
            KadreFailure.InvalidRequest("requestId"),
            (
                window.respondToCloseRequest(
                    RuntimeProcessIds.nextWindowCloseRequestId(),
                    WindowCloseDecision.Accept,
                ) as KadreResult.Failure
                ).reason,
        )
    }

    /** La surface primaire du test : le `RuntimeHostSurface` minimal de la phase 0, sans View. */
    private fun primarySurface(): RuntimeHostSurface =
        RuntimeHostSurface(RuntimeProcessIds.nextSurfaceId(), initialSurfaceState())

    /**
     * L'instantané initial de surface, les mêmes valeurs que `initialSurfaceState` de la phase 0
     * bornées à leur minimum valide (base 1x1) — aucun type Android requis.
     */
    private fun initialSurfaceState(): SurfaceState = SurfaceState(
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
}
