package org.graphiks.kadre.consumer

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.platform.android.attachKadre
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceVisibility
import org.junit.runner.RunWith

/**
 * Les six scénarios instrumentés du contrat AND-002 (admission, lifecycle, isolation) sur
 * l'API publique `attachKadre` résolue depuis l'umbrella PUBLIÉ. Chaque nom de méthode est
 * un `testName` contractuel du mapping AND-002 — la correspondance scénario ↔ méthode :
 *
 * | scenario id (registre)                | méthode                        | sentinelle gardée                       |
 * |---------------------------------------|--------------------------------|-----------------------------------------|
 * | android-attach-decor-mismatch         | androidAttachDecorMismatch     | -                                       |
 * | android-attach-cross-form-conflict    | androidAttachCrossFormConflict | -                                       |
 * | android-attach-parent-cancelled       | androidAttachParentCancelled   | android-attach-rollback-restores-state  |
 * | android-attach-surviving-scope        | androidAttachSurvivingScope    | -                                       |
 * | android-attach-detach-reparent        | androidAttachDetachReparent    | -                                       |
 * | android-attach-recreation-isolation   | androidAttachRecreationIsolation | android-attach-isolation-per-identity |
 *
 * Chaque scénario est autonome (ActivityScenario `use{}` → la destruction du host termine
 * toute session vivante, tous chemins) et nettoie en plus ses sessions explicitement.
 * Les états sont attendus par observation bornée sur le thread d'instrumentation — le
 * looper main publie (Starting → Running, lifecycle de la surface, terminaux).
 */
@RunWith(AndroidJUnit4::class)
class KadreConsumerAttachContractTest {

    /**
     * android-attach-decor-mismatch — une View posée HORS du decorView de l'Activity
     * (jamais attachée à la fenêtre) : `Failure(InvalidRequest("surfaceView"))`, aucune
     * session, et la denial n'a consommé aucune identité (l'attach nominal suivant réussit).
     */
    @Test
    fun androidAttachDecorMismatch() {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val outsideDecor = FrameLayout(activity)
                val result = activity.attachKadre(
                    outsideDecor,
                    ConsumerTestSupport.applicationFactory,
                )
                assertTrue(result is KadreResult.Failure, "expected a Failure, got $result")
                assertEquals(
                    KadreFailure.InvalidRequest("surfaceView"),
                    (result as KadreResult.Failure).reason,
                )
                // Aucune session n'a existé : la View nominale de la même Activity est
                // immédiatement attachable (la denial n'a rien réservé, rien terminé).
                val session = ConsumerTestSupport.success(
                    activity.attachKadre(
                        activity.consumerView,
                        ConsumerTestSupport.applicationFactory,
                    ),
                )
                session.close()
            }
        }
    }

    /**
     * android-attach-cross-form-conflict — attach forme Activity sur la View du decor :
     * succès ; second attach forme View sur la MÊME View : `Failure(AlreadyInUse(Host))`
     * sans exception ; la première session reste vivante.
     */
    @Test
    fun androidAttachCrossFormConflict() {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val activityForm = ConsumerTestSupport.success(
                    activity.attachKadre(
                        activity.consumerView,
                        ConsumerTestSupport.applicationFactory,
                    ),
                )
                try {
                    val viewForm = activity.consumerView.attachKadre(
                        lifecycleOwner = activity,
                        applicationFactory = ConsumerTestSupport.applicationFactory,
                    )
                    assertTrue(viewForm is KadreResult.Failure, "expected a Failure, got $viewForm")
                    assertEquals(
                        KadreFailure.AlreadyInUse(KadreResourceKind.Host),
                        (viewForm as KadreResult.Failure).reason,
                    )
                    // Le conflit n'a rien terminé : la première session reste vivante.
                    assertTrue(
                        activityForm.state.value in setOf(SessionState.Starting, SessionState.Running),
                        "the first session must stay alive, got ${activityForm.state.value}",
                    )
                } finally {
                    activityForm.close()
                }
            }
        }
    }

    /**
     * android-attach-parent-cancelled — scope annulée : `Failure(ParentScopeCancelled)` ;
     * scope sans Job : `Failure(InvalidRequest("parentScope"))` ; puis attach valide sur la
     * MÊME View : succès — la preuve que les denials laissent l'ownership exactement intact.
     *
     * Sentinelle gardée : `android-attach-rollback-restores-state` (une fuite d'ownership
     * sur un attach échoué rend le dernier attach `AlreadyInUse(Host)` — lui seul, car
     * aucun autre scénario ne ré-attache après des denials).
     */
    @Test
    fun androidAttachParentCancelled() {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val cancelled = CoroutineScope(Job().apply { cancel() })
                // Un scope VRAIMENT sans Job : la factory `CoroutineScope(context)` de
                // kotlinx-coroutines ajoute un Job quand le contexte n'en a pas (1.7+),
                // donc la forme object est la seule qui produise `coroutineContext[Job] == null`.
                val jobless = object : CoroutineScope {
                    override val coroutineContext: CoroutineContext = Dispatchers.Main
                }
                val cancelledResult = activity.attachKadre(
                    activity.consumerView,
                    ConsumerTestSupport.applicationFactory,
                    parentScope = cancelled,
                )
                assertTrue(
                    cancelledResult is KadreResult.Failure,
                    "expected a Failure, got $cancelledResult",
                )
                assertEquals(
                    KadreFailure.ParentScopeCancelled,
                    (cancelledResult as KadreResult.Failure).reason,
                )

                val joblessResult = activity.attachKadre(
                    activity.consumerView,
                    ConsumerTestSupport.applicationFactory,
                    parentScope = jobless,
                )
                assertTrue(
                    joblessResult is KadreResult.Failure,
                    "expected a Failure, got $joblessResult",
                )
                assertEquals(
                    KadreFailure.InvalidRequest("parentScope"),
                    (joblessResult as KadreResult.Failure).reason,
                )

                // Rollback intégral des denials : la même View est attachable et la session vit.
                val session = ConsumerTestSupport.success(
                    activity.attachKadre(
                        activity.consumerView,
                        ConsumerTestSupport.applicationFactory,
                    ),
                )
                session.close()
            }
        }
    }

    /**
     * android-attach-surviving-scope — attach avec un scope applicatif indépendant, puis
     * destruction du lifecycle owner : les publications lifecycle (Paused/Stopped) sont
     * OBSERVABLES sur la surface primaire (focus/visibility), la destruction produit le
     * terminal `Terminated(Stopped(HostDetached))`, et le scope survit à l'Activity.
     */
    @Test
    fun androidAttachSurvivingScope() {
        val applicationScope = ConsumerTestSupport.independentScope()
        val captured = AtomicReference<KadreScope?>(null)
        try {
            ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
                val session = AtomicReference<KadreSession?>(null)
                scenario.onActivity { activity ->
                    val factory = KadreApplicationFactory {
                        KadreApplication {
                            captured.set(this)
                            awaitCancellation()
                        }
                    }
                    session.set(
                        ConsumerTestSupport.success(
                            activity.consumerView.attachKadre(
                                lifecycleOwner = activity,
                                applicationFactory = factory,
                                parentScope = applicationScope,
                            ),
                        ),
                    )
                }
                // Activity RESUMED : la publication du host rend la surface Visible + Focused.
                ConsumerTestSupport.awaitSurfaceState(captured) { state ->
                    state.visibility == SurfaceVisibility.Visible &&
                        state.focus == SurfaceFocus.Focused &&
                        state.attachment == SurfaceAttachmentState.Attached
                }
                // Owner PAUSED puis STOPPED (CREATED) : les publications arrivent à la session
                // — Unfocused puis Hidden — SANS terminer la session (stop n'est pas terminal).
                scenario.moveToState(Lifecycle.State.CREATED)
                ConsumerTestSupport.awaitSurfaceState(captured) { state ->
                    state.visibility == SurfaceVisibility.Hidden &&
                        state.focus == SurfaceFocus.Unfocused &&
                        state.attachment == SurfaceAttachmentState.Attached
                }
                // Destruction du owner : le terminal prioritaire `Stopped(HostDetached)`.
                scenario.moveToState(Lifecycle.State.DESTROYED)
                ConsumerTestSupport.awaitTerminal(
                    ConsumerTestSupport.notNull(session),
                    SessionOutcome.Stopped(SessionStopReason.HostDetached),
                )
                // Le scope applicatif indépendant est toujours vivant : la mort du host
                // n'annule que la session, pas le scope qui l'a lancée.
                assertTrue(applicationScope.isActive, "the independent scope must survive the owner")
            }
        } finally {
            applicationScope.cancel()
        }
    }

    /**
     * android-attach-detach-reparent — détacher la View du decor d'origine : terminal
     * `Stopped(HostDetached)` et ownership libéré ; ré-attacher la MÊME View dans le decor
     * d'une autre Activity : une NOUVELLE session (identité différente) vit.
     *
     * La seconde Activity est lancée SANS ActivityScenario (deux scenarios simultanés se
     * détruisent mutuellement — cf. [ConsumerTestSupport.launchConsumerActivity]).
     */
    @Test
    fun androidAttachDetachReparent() {
        ActivityScenario.launch(ConsumerActivity::class.java).use { origin ->
            val originActivity = AtomicReference<ConsumerActivity?>(null)
            val session = AtomicReference<KadreSession?>(null)
            origin.onActivity { activity ->
                originActivity.set(activity)
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadre(
                            activity.consumerView,
                            ConsumerTestSupport.applicationFactory,
                        ),
                    ),
                )
            }
            // Détachement : la View quitte le decor d'origine → terminal prioritaire.
            origin.onActivity { _ ->
                val view = ConsumerTestSupport.notNull(originActivity).consumerView
                (view.parent as ViewGroup).removeView(view)
            }
            ConsumerTestSupport.awaitTerminal(
                ConsumerTestSupport.notNull(session),
                SessionOutcome.Stopped(SessionStopReason.HostDetached),
            )

            // Ré-attach de la même View dans le decor d'une autre Activity.
            val destination = ConsumerTestSupport.launchConsumerActivity()
            try {
                ConsumerTestSupport.awaitResumed(destination)
                val reattached = AtomicReference<KadreSession?>(null)
                ConsumerTestSupport.onMain {
                    val view = ConsumerTestSupport.notNull(originActivity).consumerView
                    (destination.findViewById(android.R.id.content) as ViewGroup).addView(view)
                    reattached.set(
                        ConsumerTestSupport.success(
                            destination.attachKadre(
                                view,
                                ConsumerTestSupport.applicationFactory,
                            ),
                        ),
                    )
                }
                val next = ConsumerTestSupport.notNull(reattached)
                try {
                    assertNotEquals(ConsumerTestSupport.notNull(session).id, next.id)
                    ConsumerTestSupport.awaitRunning(next)
                } finally {
                    ConsumerTestSupport.onMain { next.close() }
                }
            } finally {
                ConsumerTestSupport.onMain { destination.finish() }
            }
        }
    }

    /**
     * android-attach-recreation-isolation — deux Activities, deux sessions : identités
     * (`SessionId`) et lifecycles indépendants ; `recreate()` de l'une termine SA session
     * (`Stopped(HostDetached)`) et laisse l'autre exactement intacte.
     *
     * La seconde Activity est lancée SANS ActivityScenario (deux scenarios simultanés se
     * détruisent mutuellement — cf. [ConsumerTestSupport.launchConsumerActivity]) et c'est
     * ELLE qui est recréée : `recreate()` exige une Activity RESUMED (elle est au premier
     * plan) ; la session de l'autre Activity — passée en arrière-plan mais vivante — doit
     * rester intacte.
     *
     * Sentinelle gardée : `android-attach-isolation-per-identity` (un registre à clé
     * globale unique refuse toute seconde session — lui seul, car aucun autre scénario
     * ne porte deux sessions vivantes en parallèle).
     */
    @Test
    fun androidAttachRecreationIsolation() {
        ActivityScenario.launch(ConsumerActivity::class.java).use { first ->
            val firstActivity = AtomicReference<ConsumerActivity?>(null)
            val firstSession = AtomicReference<KadreSession?>(null)
            first.onActivity { activity ->
                firstActivity.set(activity)
                firstSession.set(
                    ConsumerTestSupport.success(
                        activity.attachKadre(
                            activity.consumerView,
                            ConsumerTestSupport.applicationFactory,
                        ),
                    ),
                )
            }
            val secondActivity = ConsumerTestSupport.launchConsumerActivity()
            try {
                ConsumerTestSupport.awaitResumed(secondActivity)
                val secondSession = AtomicReference<KadreSession?>(null)
                ConsumerTestSupport.onMain {
                    secondSession.set(
                        ConsumerTestSupport.success(
                            secondActivity.attachKadre(
                                secondActivity.consumerView,
                                ConsumerTestSupport.applicationFactory,
                            ),
                        ),
                    )
                }
                val sessionA = ConsumerTestSupport.notNull(firstSession)
                val sessionB = ConsumerTestSupport.notNull(secondSession)
                try {
                    // Identités indépendantes, deux lifecycles vivants en parallèle
                    // (l'Activity A est passée en arrière-plan : sa session reste vivante).
                    assertNotEquals(sessionA.id, sessionB.id)
                    ConsumerTestSupport.awaitRunning(sessionA)
                    ConsumerTestSupport.awaitRunning(sessionB)

                    // recreate() de l'Activity de devant termine SA session...
                    ConsumerTestSupport.onMain { secondActivity.recreate() }
                    ConsumerTestSupport.awaitTerminal(
                        sessionB,
                        SessionOutcome.Stopped(SessionStopReason.HostDetached),
                    )

                    // ...et laisse l'autre exactement intacte (même identité, même état vivant).
                    assertEquals(sessionA.id, ConsumerTestSupport.notNull(firstSession).id)
                    assertEquals(SessionState.Running, ConsumerTestSupport.awaitRunning(sessionA))
                } finally {
                    ConsumerTestSupport.onMain { sessionA.close() }
                }
            } finally {
                // L'instance d'ORIGINE est déjà détruite par recreate() (finish = no-op) ;
                // l'instance recréée, sans session, est finie par le launch du scénario suivant.
                ConsumerTestSupport.onMain { secondActivity.finish() }
            }
        }
    }
}
