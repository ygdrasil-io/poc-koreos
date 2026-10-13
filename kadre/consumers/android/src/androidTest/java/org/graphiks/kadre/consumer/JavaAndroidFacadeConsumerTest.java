package org.graphiks.kadre.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.widget.FrameLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import kotlin.Unit;
import kotlinx.coroutines.CancellableContinuationKt;
import org.graphiks.kadre.application.KadreApplication;
import org.graphiks.kadre.application.KadreApplicationFactory;
import org.graphiks.kadre.application.SessionOutcome;
import org.graphiks.kadre.application.SessionStopReason;
import org.graphiks.kadre.diagnostics.KadreException;
import org.graphiks.kadre.diagnostics.KadreFailure;
import org.graphiks.kadre.host.android.KadreAndroid;
import org.graphiks.kadre.host.android.KadreSessionHandle;
import org.graphiks.kadre.host.android.KadreSessionPhase;
import org.graphiks.kadre.host.android.KadreSessionSnapshot;
import org.graphiks.kadre.policy.KadrePolicies;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * La ligne « Java Android » de §9 (INTEROP-EXPORTS) : attach, observer state, request stop,
 * await {@code CompletionStage}, inspect failure — en JAVA PUR contre l'umbrella PUBLIÉ
 * ({@code org.graphiks.kadre:kadre}, repository contractuel). Aucun type de coroutine ne
 * traverse la surface : la seule coroutine du consumer est l'application elle-même, le
 * SAM {@code KadreApplication} dont la méthode suspend se suspend à jamais avec le
 * marqueur stdlib {@code COROUTINE_SUSPENDED} (l'équivalent Java exact du
 * {@code awaitCancellation()} des tests Kotlin — jamais un {@code Continuation} de la
 * façade).
 *
 * Contrat de thread : l'attach et l'arrêt passent par le thread principal (les formes
 * Activity le dressent déjà côté admission) ; l'attente bloquante de terminaison vit sur
 * le thread d'instrumentation — le looper main reste libre de publier (le même parti pris
 * que {@code ConsumerTestSupport}).
 */
@RunWith(AndroidJUnit4.class)
public class JavaAndroidFacadeConsumerTest {

    /**
     * L'application minimale, suspendue pour toujours : le lambda Java du fun interface
     * {@code KadreApplication} — la signature érasée emporte le paramètre
     * {@code Continuation} du compilateur Kotlin, le corps s'attache à
     * {@code suspendCancellableCoroutine} sans jamais reprendre : l'équivalent Java exact
     * du {@code awaitCancellation()} des tests Kotlin — CANCELLABLE (l'arrêt du host
     * reprend la suspension en annulation, d'où le terminal {@code Stopped}), aucune API
     * kadre de coroutine.
     */
    private static final KadreApplicationFactory SUSPENDED_APPLICATION = context ->
            (scope, completion) ->
                    CancellableContinuationKt.suspendCancellableCoroutine(
                            cont -> Unit.INSTANCE,
                            completion);

    @Test
    public void javaFacadeAttachObserveStopAwait() throws Exception {
        try (ActivityScenario<ConsumerActivity> scenario =
                ActivityScenario.launch(ConsumerActivity.class)) {
            final KadreSessionHandle[] handleBox = new KadreSessionHandle[1];
            final List<KadreSessionSnapshot> events = new ArrayList<>();
            scenario.onActivity(activity -> {
                // §4 lettre exacte : l'attach nominal ici est la forme complète À QUATRE
                // arguments, policy explicite (le helper Java du letter :
                // KadrePolicies.INSTANCE.getDefault()).
                KadreSessionHandle handle = KadreAndroid.attach(
                        activity,
                        activity.getConsumerView(),
                        SUSPENDED_APPLICATION,
                        KadrePolicies.INSTANCE.getDefault()
                );
                handleBox[0] = handle;

                // L'identifiant opaque alloué par la façade — jamais le SessionId rédigé.
                assertNotNull(handle.id());
                assertTrue(!handle.id().isEmpty());
                assertNotEquals("SessionId(<redacted>)", handle.id());

                // LE stage stable de §4 — le même objet à chaque appel.
                assertSame(handle.termination(), handle.termination());

                // observeState : le snapshot COURANT part à l'émission immédiate, au
                // retour de l'appel — prouvé ICI, sur le thread appelant, avant toute
                // publication du looper.
                KadreSessionPhase before = handle.state().getPhase();
                assertTrue(
                        "a fresh session is Starting or Running, got " + before,
                        before == KadreSessionPhase.Starting || before == KadreSessionPhase.Running);
                handle.observeState(events::add);
                assertEquals("the current snapshot is delivered immediately", 1, events.size());
                assertEquals(before, events.get(0).getPhase());
                assertEquals(handle.id(), events.get(0).getId());
                assertNull(
                        "the outcome is only present on the Terminated phase",
                        events.get(0).getOutcome());
            });

            // La session vive atteint Running — vu du snapshot Java-safe, polling borné
            // sur le thread d'instrumentation, le looper main reste libre.
            KadreSessionHandle handle = notNull(handleBox);
            awaitPhase(handle, KadreSessionPhase.Running);

            // request stop sur le thread principal (le contrat de thread de l'attach).
            scenario.onActivity(activity -> notNull(handleBox).requestStop());

            // await CompletionStage : le stage stable se complète avec l'outcome terminal
            // réel — Stopped(HostRequested) après requestStop.
            SessionOutcome outcome = notNull(handleBox)
                    .termination()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertEquals(
                    new SessionOutcome.Stopped(SessionStopReason.HostRequested),
                    outcome);

            // Les livraisons : une par changement, séquentielles, jamais régressives,
            // toutes estampillées de l'identifiant de CE billet, la dernière portant
            // l'outcome terminal exact.
            assertTrue("at least the immediate delivery must have happened", !events.isEmpty());
            KadreSessionPhase previous = null;
            for (KadreSessionSnapshot snapshot : events) {
                assertEquals(notNull(handleBox).id(), snapshot.getId());
                if (previous != null) {
                    assertTrue(
                            "phases must not regress, got " + previous + " -> " + snapshot.getPhase(),
                            snapshot.getPhase().ordinal() >= previous.ordinal());
                }
                previous = snapshot.getPhase();
            }
            KadreSessionSnapshot last = events.get(events.size() - 1);
            assertEquals(KadreSessionPhase.Terminated, last.getPhase());
            assertEquals(
                    new SessionOutcome.Stopped(SessionStopReason.HostRequested),
                    last.getOutcome());
        }
    }

    @Test
    public void javaFacadeAttachOnDetachedViewThrowsKadreException() {
        try (ActivityScenario<ConsumerActivity> scenario =
                ActivityScenario.launch(ConsumerActivity.class)) {
            scenario.onActivity(activity -> {
                // Une View HORS du decor (jamais attachée à la fenêtre) : l'admission
                // refuse — la façade traduit le KadreResult.Failure en KadreException,
                // la failure est INTACTE (le mapping getOrThrow, jamais null).
                FrameLayout outsideDecor = new FrameLayout(activity);
                try {
                    // La déclinaison TROIS arguments (@JvmOverloads — policy par défaut).
                    KadreAndroid.attach(activity, outsideDecor, SUSPENDED_APPLICATION);
                    fail("attach on a view outside the decor must throw KadreException");
                } catch (KadreException exception) {
                    // Inspect failure : la denial nomme le field fautif de la forme
                    // Activity — InvalidRequest("surfaceView"), l'échelle d'admission view.
                    assertEquals(
                            new KadreFailure.InvalidRequest("surfaceView"),
                            exception.getFailure());
                }

                // La denial n'a rien réservé : la View nominale de la même Activity est
                // immédiatement attachable (la forme trois arguments réussit).
                KadreSessionHandle handle = KadreAndroid.attach(
                        activity,
                        activity.getConsumerView(),
                        SUSPENDED_APPLICATION
                );
                assertNotNull(handle);
                handle.close();
            });
        }
    }

    /** Le polling borné d'une phase du snapshot — le looper main publie en parallèle. */
    private static void awaitPhase(KadreSessionHandle handle, KadreSessionPhase expected)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000L;
        KadreSessionPhase current = handle.state().getPhase();
        while (System.currentTimeMillis() < deadline) {
            current = handle.state().getPhase();
            if (current == expected) {
                return;
            }
            Thread.sleep(25L);
        }
        fail("the session never reached " + expected + " within 10000ms; last=" + current);
    }

    /** La valeur capturée depuis le thread main — absente : le scénario est en erreur. */
    private static KadreSessionHandle notNull(KadreSessionHandle[] box) {
        assertNotNull("the handle was never set", box[0]);
        return box[0];
    }
}
