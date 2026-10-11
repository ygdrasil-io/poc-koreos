package org.graphiks.kadre.platform.android

import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Les six comportements normatifs du champ `pointerCapture` committable (phase 4, tâche 3) — le
 * commit EN DEUX TEMPS : `apply(Set(Confined))` lance la primitive (API 26+), mais l'état n'est
 * committé QUE quand le listener du SDK rapporte la capture (`Window.Callback.
 * onPointerCaptureChanged`) — le
 * retour de la requête n'est JAMAIS la confirmation ; la perte (`listenerChanged(false)`) revient
 * à `None` et publie ; `Set(None)` libère et commit IMMÉDIATEMENT (le release est synchrone côté
 * SDK) ; `Locked` est rejeté champ par champ (D13 — il appartient à
 * `InteractionAction.LockPointer`, tâche 4) ; sous l'API 26 `Confined`/`Locked` sont rejetés
 * champ par champ, zéro appel natif ; et le pont P1 de perte de focus emprunte le même chemin
 * que la perte rapportée par le listener.
 *
 * Le seam natif est un recorder : les tests hôtes n'ont ni View ni pointeur —
 * l'implémentation production ([AndroidViewPointerCaptureNative]) poste
 * `View.requestPointerCapture`/`releasePointerCapture` et pose le listener sur le thread
 * principal ; le recorder prouve CE QUE LA SURFACE LUI DEMANDE, et le listener enregistré est
 * le chemin par lequel le test simule la réponse du SDK.
 */
class AndroidPointerCaptureTest {

    /**
     * Le recorder du seam — chaque demande est retenue dans l'ordre (`request`/`release`), et le
     * listener posé par le seam à sa construction est celui que le test invoque pour simuler la
     * réponse du SDK (`Window.Callback.onPointerCaptureChanged` — le canal d'état du SDK, cf.
     * [AndroidViewPointerCaptureNative]).
     */
    private class RecordingCaptureNative : AndroidPointerCapture.Native {
        val calls = mutableListOf<String>()

        private var recordedListener: ((hasCapture: Boolean) -> Unit)? = null

        /** Le listener posé par le seam à sa construction — le chemin du rapport du SDK. */
        val listener: ((hasCapture: Boolean) -> Unit)? get() = recordedListener

        override fun requestCapture() {
            calls += "request"
        }

        override fun releaseCapture() {
            calls += "release"
        }

        override fun setListener(listener: ((hasCapture: Boolean) -> Unit)?) {
            recordedListener = listener
        }
    }

    /** La surface sous test : état initial honnête (capture `None`), clock inerte. */
    private fun surface(
        apiLevel: Int = 26,
        captureNative: AndroidPointerCapture.Native? = null,
    ): AndroidHostSurface = AndroidHostSurface(
        id = RuntimeProcessIds.nextSurfaceId(),
        initialState = initialSurfaceStateForPointerCaptureTest(),
        frameClock = { InertCaptureFrameClock },
        apiLevel = apiLevel,
        pointerCaptureNative = captureNative,
    )

    /** La capture `Confined` demandée PUIS confirmée par le listener — l'état la porte. */
    private suspend fun holdConfined(surface: AndroidHostSurface, seam: RecordingCaptureNative) {
        surface.observationInstalled(inputWired = true)
        val outcome = surface.apply(
            SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined)),
        )
        val applied = assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(outcome)
        assertIs<SurfaceUpdateOutcome.Applied>(applied.value)
        seam.listener?.invoke(true)
        assertEquals(
            PointerCaptureMode.Confined,
            surface.state.value.pointerCapture,
            "the setup must leave the capture committed",
        )
    }

    @Test
    fun confinedCommitWaitsForListenerConfirmation() = runBlocking {
        val seam = RecordingCaptureNative()
        val surface = surface(captureNative = seam)

        // Pré-install : rien n'est annoncé, même avec le seam câblé — la discipline « rien de
        // revendiqué avant l'installation » (le miroir du curseur, tâche 2).
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface)),
            surface.capabilities.value.pointerCapture,
            "pre-install the pointerCapture capability keeps its refusal",
        )
        val pre = surface.apply(
            SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined)),
        )
        assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(pre).value,
            "pre-install a posed pointerCapture field is refused per-field",
        )
        assertTrue(seam.calls.isEmpty(), "pre-install no native primitive is claimed")

        // Installé SANS le canal d'état (`inputWired = false` — la notification
        // `Window.Callback` du SDK, le même fait que l'observation d'entrée) : la confirmation
        // du commit en deux temps ne peut jamais arriver — le refus tient, même le seam câblé.
        surface.observationInstalled(inputWired = false)
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface)),
            surface.capabilities.value.pointerCapture,
            "without the state channel the refusal holds after install too",
        )

        // Post-install avec le seam ET le canal d'état : exactement les DEUX modes que le SDK
        // peut honorer, disponibles — `Locked` est DEHORS (D13, le miroir web).
        surface.observationInstalled(inputWired = true)
        assertEquals(
            Capability.Supported(
                setOf(PointerCaptureMode.None, PointerCaptureMode.Confined),
                FeatureAvailability.Available,
            ),
            surface.capabilities.value.pointerCapture,
            "post-install the capability announces {None, Confined}, available",
        )

        // La demande : la primitive est lancée, mais l'état N'EST PAS committé — le retour de
        // la requête SDK n'est pas la confirmation de la capture.
        val outcome = surface.apply(
            SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined)),
        )
        val applied = assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(outcome)
        val result = assertIs<SurfaceUpdateOutcome.Applied>(applied.value)
        assertEquals(
            PointerCaptureMode.None,
            result.state.pointerCapture,
            "the outcome reports the state as published — the attempt, not the capture",
        )
        assertEquals(
            SurfaceRevision(0L),
            result.state.revision,
            "nothing is committed before the listener confirms",
        )
        assertEquals(
            listOf("request"),
            seam.calls,
            "exactly one requestCapture was asked of the seam",
        )

        // La confirmation du listener : SEUL moment du commit — révision nouvelle, état publié.
        seam.listener?.invoke(true)
        assertEquals(
            PointerCaptureMode.Confined,
            surface.state.value.pointerCapture,
            "the committed state carries the confirmed capture",
        )
        assertEquals(
            SurfaceRevision(1L),
            surface.state.value.revision,
            "the confirmation commits with one new revision",
        )
        assertEquals(
            listOf("request"),
            seam.calls,
            "the confirmation path asks nothing more of the seam",
        )
    }

    @Test
    fun lostListenerRevertsToNone() = runBlocking {
        val seam = RecordingCaptureNative()
        val surface = surface(captureNative = seam)
        holdConfined(surface, seam)
        assertEquals(SurfaceRevision(1L), surface.state.value.revision)

        // Le SDK a perdu la capture (perte de focus, événement incapturable...) et le listener
        // le rapporte : l'état revient à `None` et publie — la publication EST la révérence.
        seam.listener?.invoke(false)

        assertEquals(
            PointerCaptureMode.None,
            surface.state.value.pointerCapture,
            "a capture the SDK no longer holds cannot stay published",
        )
        assertEquals(
            SurfaceRevision(2L),
            surface.state.value.revision,
            "the revert is a committed state change — one new revision",
        )
        assertEquals(
            listOf("request"),
            seam.calls,
            "the loss path asks nothing of the seam — there is nothing left to release",
        )
    }

    @Test
    fun noneReleasesAndCommitsImmediately() = runBlocking {
        val seam = RecordingCaptureNative()
        val surface = surface(captureNative = seam)
        holdConfined(surface, seam)
        assertEquals(SurfaceRevision(1L), surface.state.value.revision)

        // `Set(None)` alors que `Confined` : le release est SYNCHRONE côté SDK — le commit est
        // immédiat, porté par l'outcome lui-même (une révision nouvelle, le miroir du commit
        // en une passe).
        val outcome = surface.apply(
            SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.None)),
        )
        val applied = assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(outcome)
        val result = assertIs<SurfaceUpdateOutcome.Applied>(applied.value)
        assertEquals(
            PointerCaptureMode.None,
            result.state.pointerCapture,
            "the release commits in the same apply — the outcome carries it",
        )
        assertEquals(
            SurfaceRevision(2L),
            result.state.revision,
            "the immediate commit moves the revision once",
        )
        assertEquals(
            listOf("request", "release"),
            seam.calls,
            "the release was asked of the seam",
        )

        // Le rapport de perte que le SDK peut encore délivrer APRÈS le release synchrone est
        // absorbé : l'état dit déjà `None`, rien ne bouge davantage.
        seam.listener?.invoke(false)
        assertEquals(
            SurfaceRevision(2L),
            surface.state.value.revision,
            "a loss reported after the synchronous release moves nothing",
        )
    }

    @Test
    fun lockedIsPerFieldRejected() = runBlocking {
        val seam = RecordingCaptureNative()
        val surface = surface(captureNative = seam)
        surface.observationInstalled(inputWired = true)

        // D13 : `Locked` appartient à `InteractionAction.LockPointer` (transient activation,
        // phase 4, tâche 4) — rejet champ par champ, zéro appel natif, tout niveau d'API.
        val outcome = surface.apply(
            SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Locked)),
        )
        val applied = assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(outcome)
        val partial = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(applied.value)
        assertEquals(
            RejectedSurfaceField(
                SurfaceProperty.PointerCapture,
                KadreFailure.Unsupported(KadreOperation.UpdateSurface),
            ),
            partial.rejected.single(),
            "Locked is a per-field refusal — never an outer failure",
        )
        assertEquals(
            PointerCaptureMode.None,
            partial.state.pointerCapture,
            "the refused field left the committed capture untouched",
        )
        assertEquals(
            SurfaceRevision(0L),
            partial.state.revision,
            "a refusal alone moves neither state nor revision",
        )
        assertTrue(seam.calls.isEmpty(), "a refused field calls no native primitive")
    }

    @Test
    fun belowApi26IsPerFieldRejectedWithReason() = runBlocking {
        val seam = RecordingCaptureNative()
        val surface = surface(apiLevel = 24, captureNative = seam)
        // La capability annonce les modes que la plateforme PORTE, indisponibles avec la raison
        // d'API — `Unavailable` porte la raison, l'admission refuse champ par champ.
        surface.observationInstalled(inputWired = true)
        assertEquals(
            Capability.Supported(
                setOf(PointerCaptureMode.None, PointerCaptureMode.Confined),
                FeatureAvailability.Unavailable(
                    KadreFailure.PlatformFailure(KadrePlatform.Android, "pointer-capture", "api-26-required"),
                ),
            ),
            surface.capabilities.value.pointerCapture,
            "below API 26 the modes are announced with the API reason as their unavailability",
        )

        // API 24 : aucun `Confined` ni `Locked` — rejet champ par champ `Unsupported`, zéro appel.
        val confined = surface.apply(
            SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined)),
        )
        val confinedPartial = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(confined).value,
            "below API 26 a Confined request is refused per-field",
        )
        assertEquals(
            RejectedSurfaceField(
                SurfaceProperty.PointerCapture,
                KadreFailure.Unsupported(KadreOperation.UpdateSurface),
            ),
            confinedPartial.rejected.single(),
        )
        val locked = surface.apply(
            SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Locked)),
        )
        val lockedPartial = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(locked).value,
            "below API 26 a Locked request is refused per-field",
        )
        assertEquals(
            RejectedSurfaceField(
                SurfaceProperty.PointerCapture,
                KadreFailure.Unsupported(KadreOperation.UpdateSurface),
            ),
            lockedPartial.rejected.single(),
        )

        // Et un `Set(None)` sous l'API 26 consulte la capability — l'indisponibilité porte la
        // raison d'API (le mot de la PUBLICATION, face au mot de l'ADMISSION).
        val none = surface.apply(
            SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.None)),
        )
        val nonePartial = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(none).value,
        )
        assertEquals(
            RejectedSurfaceField(
                SurfaceProperty.PointerCapture,
                KadreFailure.PlatformFailure(KadrePlatform.Android, "pointer-capture", "api-26-required"),
            ),
            nonePartial.rejected.single(),
            "below API 26 the Unavailable capability answers with the API reason",
        )
        assertTrue(seam.calls.isEmpty(), "below API 26 no native primitive is ever claimed")
    }

    @Test
    fun focusLossBridgesToLost() = runBlocking {
        val seam = RecordingCaptureNative()
        val surface = surface(captureNative = seam)
        holdConfined(surface, seam)
        assertEquals(SurfaceRevision(1L), surface.state.value.revision)

        // Le pont P1 : la perte de focus fenêtre que la couture observe déjà (phase 3,
        // `WindowFocusLost`) emprunte le MÊME chemin que `listenerChanged(false)` — le SDK
        // libère la capture de lui-même à la perte de focus, le pont couvre la cohérence du
        // seam propre.
        surface.onInputObservation(AndroidInputObservation.WindowFocusLost)

        assertEquals(
            PointerCaptureMode.None,
            surface.state.value.pointerCapture,
            "the focus-loss bridge reverts the capture through the lost path",
        )
        assertEquals(
            SurfaceRevision(2L),
            surface.state.value.revision,
            "the bridged revert is a committed state change — one new revision",
        )
        assertEquals(
            listOf("request"),
            seam.calls,
            "the bridge asks nothing of the seam — same as the listener-reported loss",
        )
    }
}

// ----------------------------------------------------------------------- aides du fichier

/** L'instantané initial de surface du test — les mêmes valeurs que la couture, base 1×1. */
private fun initialSurfaceStateForPointerCaptureTest(): SurfaceState = SurfaceState(
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
private object InertCaptureFrameClock : AndroidRedrawScheduler.FrameClock {
    override fun schedule(frame: () -> Unit) = Unit
    override fun cancel() = Unit
}
