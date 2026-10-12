package org.graphiks.kadre.consumer

import android.content.ClipData
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View.DragShadowBuilder
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.interaction.InteractionKind
import org.graphiks.kadre.platform.android.attachKadre
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceVisibility
import org.junit.runner.RunWith

/**
 * Le scénario instrumenté PUBLIC de l'AND-007 (qualification frontière du canal drop Android)
 * sur l'API publique `attachKadre` résolue depuis l'umbrella PUBLIÉ — le miroir exact du
 * précédent `android-text-input-channel-absent` (AND-006). Les transferts RÉELS et la
 * sentinelle stream-on-close de l'AND-007 tournent à côté, DANS CE MÊME run connecté : ils
 * exigent la construction runtime-friend (source Task 1 + réducteur Task 2, invisibles des
 * artefacts publiés) et vivent dans l'AAR `android-devicetest` consommé
 * `androidTestImplementation` (module kadre `platform/android-devicetest`) — leurs testcase
 * arrivent dans le XML JUnit de CE run.
 *
 * | scenario id (registre)           | méthode                  | sentinelle gardée              |
 * |----------------------------------|--------------------------|--------------------------------|
 * | android-drop-channel-absent      | androidDropChannelAbsent | android-drop-no-public-channel |
 *
 * LES TROIS FAITS FRONTIÈRES (le canal d'entrée drop n'existe pas sur Android) :
 *
 * 1. L'ÉTAT PUBLIÉ : `capabilities.dragAndDrop` reste `Unsupported` (FeatureAvailability à
 *    deux états — le port texte AND-006 a son `Capability.Unsupported(TextInput)`, le drop
 *    n'a même pas de capability terminale : `dragAndDropAvailable = false`, la décision
 *    normative garée du hook drag, AndroidHostSurface.kt:650).
 * 2. L'ANNONCE P4 : `handlerInteractions` est `Available` et son ensemble annoncé NE contient
 *    PAS `InteractionKind.AcceptDrop` — le moteur d'interactions ne l'annonce jamais ; le
 *    refus des kinds non annoncés (`Unsupported(Interaction)`, sans consommer) est la
 *    mécanique épinglee par l'AND-005 (`android-interaction-token-authority` : `OpenWindow`
 *    refusée exactement ainsi). L'armement direct d'un `AcceptDrop` n'est pas constructible
 *    depuis l'API publique (`DropOfferId` est opaque à constructeur interne — il ne peut
 *    venir QUE d'une offre livrée, et aucune offre ne peut être livrée ici : les deux faits
 *    sont la même absence).
 * 3. LE SILENCE — la preuve par l'absence : un drag RÉEL initié par l'app sur sa propre View
 *    (`View.startDragAndDrop(clip, shadow, null, 0)` — API publique 24+, la session système
 *    vit réellement dans la fenêtre) ne produit AUCUNE observation drop dans la session
 *    kadre — zéro `DropEntered`/`DropMoved`/`DropExited`/`Dropped` dans une fenêtre d'écoute
 *    bornée (échec rapide au premier événement). La réception honnête exige le
 *    `OnDragListener` mono-slot posé par le host (fait SDK §5(e) — décision normative
 *    garée) : tant qu'il n'existe pas sur le chemin kadre, un drag qui traverse la fenêtre
 *    est un silence. Deux faits SDK de fabrication de la session réelle, épelés ici :
 *    (a) le WM exige le transfert du TOUCH FOCUS de la fenêtre vers le canal de drag —
 *    sans toucher vivant, `startDragAndDrop` répond `false` et la session est close
 *    immédiatement (fait `DragDropController.performDrag`, observé sur l'émulateur) ; le
 *    scénario pose donc un toucher système RÉEL (sendPointerSync, la voie INJECT_EVENTS du
 *    harnais) et le laisse posé pendant la fenêtre d'écoute ; (b) le récepteur qui garde la
 *    session vivante est le decor de l'app — JAMAIS la view attachée à kadre (le scénario
 *    regarde la session kadre, pas le decor). Nettoyage : la session de drag est annulée
 *    (`cancelDragAndDrop`, best-effort) et le doigt relevé.
 *
 * Les scénarios sont autonomes (ActivityScenario `use{}`) et ferment leur session
 * explicitement ; l'observation est un polling borné sur le thread d'instrumentation, les
 * appels View passent par le thread main.
 */
@RunWith(AndroidJUnit4::class)
class KadreConsumerDropContractTest {

    /**
     * android-drop-channel-absent — les trois faits ci-dessus sur l'attach RÉEL du consumer :
     * la capability publiée refuse avant, l'annonce d'interaction n'inclut jamais l'accept,
     * et le drag RÉEL de la propre View de l'app traverse la fenêtre sans qu'aucune
     * observation n'atteigne jamais la session. La capability est ré-assertée APRÈS le drag
     * (le silence n'a rien changé à l'état publié).
     */
    @Test
    fun androidDropChannelAbsent() = runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val activity = activityReference(scenario)
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity2 ->
                session.set(
                    ConsumerTestSupport.success(
                        activity2.attachKadre(activity2.consumerView, scopeCapturingFactory(captured)),
                    ),
                )
            }
            val surface = awaitPrimarySurface(captured)
            val recorder = InputEventRecorder(surface.input.events, this)
            settle()
            val view = ConsumerTestSupport.notNull(activity).consumerView

            // La fenêtre de l'app est VISIBLE et FOCUSée avant le drag réel — un démarrage
            // de drag sur une fenêtre pas encore posée est refusé par le window manager
            // (flake d'émulateur froid), pas un fait du canal.
            ConsumerTestSupport.awaitSurfaceState(captured) { state ->
                state.visibility == SurfaceVisibility.Visible && state.focus == SurfaceFocus.Focused
            }

            // Fait 1 — l'état publié : pas de capability drop, jamais.
            assertEquals(
                FeatureAvailability.Unsupported,
                surface.input.state.value.capabilities.dragAndDrop,
                "the published dragAndDrop capability is Unsupported — there is no drop channel to promise",
            )

            // Fait 2 — l'annonce P4 : le moteur d'interactions n'annonce jamais AcceptDrop.
            val advertised = surface.capabilities.value.handlerInteractions
            val supported = assertIs<Capability.Supported<Set<InteractionKind>>>(advertised)
            assertEquals(
                FeatureAvailability.Available,
                supported.availability,
                "the interaction engine itself is available (the AND-005 construction)",
            )
            assertFalse(
                InteractionKind.AcceptDrop in supported.constraints,
                "AcceptDrop is never advertised — the drop accept cannot even be armed through the engine",
            )

            // Fait 3 — LE SILENCE : un drag RÉEL, initié par l'app sur sa propre View. Le
            // récepteur qui garde la session système vivante est le decor View de l'app (un
            // listener posé par le HOST sur SA propre view — la coopération §5(e), au
            // bénéfice du test ; JAMAIS la consumerView attachée à kadre : le scénario
            // regarde la session kadre, pas le decor).
            var started = false
            // La session système exige le TRANSFERT du touch focus de la fenêtre vers le
            // canal de drag (DragDropController : sans focus transféré, le drag est clos
            // immédiatement et startDragAndDrop répond false) — le toucher vit DANS la
            // fenêtre au moment du start. Un toucher système RÉEL est injecté (la voie
            // INJECT_EVENTS de l'instrumentation — sendPointerSync, l'injection borne du
            // harnais), le doigt reste POSÉ pendant la fenêtre d'écoute : le drag traverse
            // réellement la fenêtre, comme un vrai geste. Le récepteur qui garde la session
            // vivante est le decor de l'app (un listener posé par le HOST sur SA propre
            // view — JAMAIS la consumerView attachée à kadre : le scénario regarde la
            // session kadre, pas le decor).
            val anchorX = view.width.toFloat() / 2f
            val anchorY = view.height.toFloat() / 2f
            val now = SystemClock.uptimeMillis()
            InstrumentationRegistry.getInstrumentation().sendPointerSync(
                MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, anchorX, anchorY, 0),
            )
            ConsumerTestSupport.onMain {
                ConsumerTestSupport.notNull(activity).window.decorView.setOnDragListener { _, _ -> true }
                started = view.startDragAndDrop(
                    ClipData.newPlainText("kadre-drop-probe", "absent-channel"),
                    DragShadowBuilder(view),
                    null,
                    0,
                )
            }
            assertTrue(started, "the drag session started for real — the silence is a real session's silence")
            val deadline = System.currentTimeMillis() + SILENCE_WINDOW_MILLIS
            while (System.currentTimeMillis() < deadline) {
                val drops = recorder.snapshot().filter(::isDropObservation)
                assertTrue(
                    drops.isEmpty(),
                    "the absent channel leaked a drop observation: $drops",
                )
                delay(POLL_INTERVAL_MILLIS)
            }

            // Nettoyage : la session de drag de l'app est annulée (best-effort — la session
            // peut déjà s'être terminée seule) et le doigt injecté est relevé.
            ConsumerTestSupport.onMain {
                runCatching { view.cancelDragAndDrop() }
                runCatching { ConsumerTestSupport.notNull(activity).window.decorView.setOnDragListener(null) }
            }
            runCatching {
                val upAt = SystemClock.uptimeMillis()
                InstrumentationRegistry.getInstrumentation().sendPointerSync(
                    MotionEvent.obtain(upAt, upAt, MotionEvent.ACTION_UP, anchorX, anchorY, 0),
                )
            }

            // Le silence n'a rien changé : l'état publié reste le refus structurel.
            assertEquals(
                FeatureAvailability.Unsupported,
                surface.input.state.value.capabilities.dragAndDrop,
                "the published capability is unchanged after the real drag",
            )
            assertFalse(
                recorder.snapshot().any(::isDropObservation),
                "still zero drop observations over the whole scenario",
            )
            ConsumerTestSupport.onMain { ConsumerTestSupport.notNull(session).close() }
        }
    }

    // ------------------------------------------------------------------
    // Aides du fichier — le miroir des tests AND-004/005/006.
    // ------------------------------------------------------------------

    private fun isDropObservation(event: InputEvent): Boolean = when (event) {
        is InputEvent.DropEntered, is InputEvent.DropMoved, is InputEvent.DropExited, is InputEvent.Dropped -> true
        else -> false
    }

    /** La factory qui capture le [KadreScope] de la session pour l'observation du test. */
    private fun scopeCapturingFactory(captured: AtomicReference<KadreScope?>): KadreApplicationFactory =
        KadreApplicationFactory {
            KadreApplication {
                captured.set(this)
                kotlinx.coroutines.awaitCancellation()
            }
        }

    /** L'Activity du scénario, capturée par référence atomique depuis le thread main. */
    private fun activityReference(scenario: ActivityScenario<ConsumerActivity>): AtomicReference<ConsumerActivity?> {
        val reference = AtomicReference<ConsumerActivity?>(null)
        scenario.onActivity { activity -> reference.set(activity) }
        return reference
    }

    /** La surface primaire de la session — publiée par le looper main, attendue par polling. */
    private fun awaitPrimarySurface(captured: AtomicReference<KadreScope?>): HostSurface {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            captured.get()?.primarySurface?.value?.let { return it }
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error("the primary surface was never published within ${DEFAULT_TIMEOUT_MILLIS}ms")
    }

    /** Le drain de souscription : le miroir des tests hôte (channelFlow → SharedFlow). */
    private suspend fun settle() {
        repeat(8) { yield() }
    }

    /**
     * Le collecteur d'événements d'entrée du test : souscrit en UNDISPATCHED (la
     * souscription est engagée au retour), accumule sous verrou — les publications arrivent
     * du looper main via le réducteur runtime.
     */
    private class InputEventRecorder(flow: Flow<InputEvent>, scope: CoroutineScope) {
        private val lock = Any()
        private val events = mutableListOf<InputEvent>()
        private val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            flow.collect { event -> synchronized(lock) { events += event } }
        }

        fun snapshot(): List<InputEvent> = synchronized(lock) { events.toList() }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
        const val POLL_INTERVAL_MILLIS = 25L

        /** La fenêtre bornée du silence : un drag vivant, aucun événement drop — court. */
        const val SILENCE_WINDOW_MILLIS = 2_000L
    }
}
