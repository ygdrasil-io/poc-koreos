package org.graphiks.kadre.platform.android

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.awaitCancellation
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.host.android.KadreAndroid
import org.graphiks.kadre.host.android.KadreSessionHandle
import org.graphiks.kadre.host.android.KadreSessionPhase
import org.graphiks.kadre.internal.runtime.DisplayPortSnapshot
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LEAK CYCLES — le scénario de MESURE du nightly (TEST-STRATEGY §11.1 :369 : « détection de
 * leaks d'owners, handles, buffers et subscriptions natives »). Il n'invente AUCUN contrat ni
 * ligne de registre (aucun AND-xxx) : il RÉPÈTE les cycles de vie réels d'une surface attachée
 * sur l'émulateur et affirme, À CHAQUE CYCLE, que le système revient EXACTEMENT à son état de
 * repos — session terminée proprement (outcome exact), AUCUNE résurrection (le terminal tient
 * après le churn lifecycle suivant), et les registres internes bornés.
 *
 * Ce module est l'AMI des internals (le câblage P6, cf. build.gradle.kts) : les classes de
 * scénarios voyagent dans l'APK androidTest du CONSUMER et tournent sous SON runner connecté ;
 * l'Activity hôte est l'activité LAUNCHER du consumer — résolue PAR NOM, le module ami ne
 * compile JAMAIS contre le consumer (le consumer dépend de CET AAR, pas l'inverse).
 *
 * | cycle                          | volume | ce qui est mesuré par cycle |
 * |--------------------------------|--------|------------------------------|
 * | attach/stop (Kotlin puis façade Java `KadreAndroid`) | 10 (5+5) | `Running` publié, outcome terminal exact `Stopped(HostRequested)`, ré-attach de LA MÊME View au cycle suivant (l'ownership `AndroidViewOwnership` est rendu à la terminaison — une réservation qui fuite rend ce ré-attach `AlreadyInUse(Host)`), terminal tenu après churn CREATED→RESUMED, journal de diagnostic inchangé et ≤ capacité |
 * | recreate (`ActivityScenario.recreate`) | 5 | l'ancienne session termine `Stopped(HostDetached)` et RESTE terminée pendant que la nouvelle instance RESUMED vit, la nouvelle instance accepte un attach frais, journal inchangé |
 * | background/foreground (`moveToState`) | 4 allers-retours | la session RESTE vivante (aucune mort induite), les publications lifecycle de la surface suivent chaque transition, journal inchangé ; puis close propre, outcome exact et terminal tenu après churn |
 * | service lifecycle (FGS capture) | 4 | ZÉRO service foreground `AndroidCaptureService` au repos (la lecture comportementale du refcount `AndroidCaptureForegroundOwnership` : le service ne s'arrête qu'à la DERNIÈRE relâche — un hold qui fuite le garderait vivant), le registre d'armement `AndroidCaptureForegroundRegistry` n'accumule aucune entrée (le jeton oublié rend une attente NEUVE), le contrôle de production sans `start` ne relâche rien ni ne lance |
 * | ports P7 display / devices / gamepad | 4 chacun | les cycles install/close des ports sur les SEAMS SDK RÉELS (DisplayManager/InputManager enveloppés-compteurs, délégation TOTALE — aucune couture inventée) : exactement UNE registration à l'install, ZÉRO au close, le seam possédé fermé exactement une fois, l'inventaire gamepad déterministe d'un cycle à l'autre |
 *
 * Ce qui est mesuré ICI : les comptes SDK-observables et comportementaux ci-dessus. Ce qui
 * EXIGE LeakCanary (heap) et reste à la décision sample/debug différée (TEST-STRATEGY :369) :
 * la survie d'instances côté tas (Activity/View capturées, coroutines orphelines, buffers de
 * delivery), et la lecture DIRECTE du compteur privé `holders` du FGS — ce test en mesure la
 * conséquence observable (le service ne court plus au repos) : `acquire` ne suit qu'un `start`
 * réussi et la DERNIÈRE relâche seule envoie le `stopService`, donc « service absent au repos »
 * est la conséquence exacte de « refcount à zéro ». Le refus de la forme « FGS sans projection
 * sur API 34+ » (SecurityException DANS le service, crash process) est la raison structurelle
 * pour laquelle ce test ne démarre JAMAIS le FGS : les cycles `start` réels vivent dans les
 * scénarios AND-009 CONSENTIS du gate, pas dans une mesure de repos.
 *
 * Les attentes sont BORNÉES (poll 25 ms, deadline 10 s — le style des scénarios consommateurs)
 * ; les soaks d'absence (résurrection) sont bornés aussi : on affirme qu'un état NE bouge PAS
 * pendant une fenêtre, jamais un `sleep` nu comme preuve.
 */
public class AndroidLeakCyclesTest {

    // -----------------------------------------------------------------------
    // 10× attach/stop — 5 Kotlin `attachKadre`, 5 façade Java `KadreAndroid`
    // -----------------------------------------------------------------------

    /**
     * Le cycle attach/stop répété sur LA MÊME View restée attachée à la fenêtre : chaque
     * terminaison doit RENDRE l'identité (le ré-attach du cycle suivant réussit — la sentinelle
     * fuite-ownership est « le cycle N+1 répond `AlreadyInUse(Host)` »), chaque close doit
     * livrer l'outcome EXACT `Stopped(HostRequested)`, et le churn lifecycle post-terminal ne
     * doit ressusciter RIEN.
     */
    @Test
    public fun attachStopCyclesReturnToAtRest(): Unit = runOnInstrumentation {
        ActivityScenario.launch(consumerActivityClass()).use { scenario ->
            val view = AtomicReference<View?>(null)
            scenario.onActivity { activity -> view.set(hostViewIn(activity)) }
            val journalBaseline = AndroidAttachDiagnostics.failures().size

            repeat(ATTACH_STOP_CYCLES) { cycle ->
                val useFacade = cycle >= KOTLIN_ATTACH_CYCLES
                val session = AtomicReference<KadreSession?>(null)
                val handle = AtomicReference<KadreSessionHandle?>(null)
                scenario.onActivity { activity ->
                    if (useFacade) {
                        handle.set(
                            KadreAndroid.attach(
                                checkNotNull(view.get()),
                                activity,
                                applicationFactory(),
                            ),
                        )
                    } else {
                        session.set(
                            attachSuccess(
                                checkNotNull(view.get()).attachKadre(
                                    lifecycleOwner = activity,
                                    applicationFactory = applicationFactory(),
                                ),
                            ),
                        )
                    }
                }

                if (useFacade) {
                    val live = checkNotNull(handle.get()) { "cycle $cycle: the facade attach never produced a handle" }
                    awaitHandlePhase(live, KadreSessionPhase.Running, "cycle $cycle")
                    runOnMain { live.close() }
                    assertEquals(
                        "cycle $cycle: the facade close delivers the exact host-requested outcome",
                        SessionOutcome.Stopped(SessionStopReason.HostRequested),
                        live.termination().toCompletableFuture().get(AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
                    )
                    assertEquals(
                        "cycle $cycle: the handle phase is terminal",
                        KadreSessionPhase.Terminated,
                        live.state().phase,
                    )
                    soakHeldHandle(scenario, live, "cycle $cycle")
                } else {
                    val live = checkNotNull(session.get()) { "cycle $cycle: the kotlin attach never produced a session" }
                    awaitState(live) { it == SessionState.Running }
                    runOnMain { live.close() }
                    assertEquals(
                        "cycle $cycle: the close terminates with the exact host-requested outcome",
                        SessionOutcome.Stopped(SessionStopReason.HostRequested),
                        awaitOutcome(live),
                    )
                    soakHeldSession(scenario, live, "cycle $cycle")
                }

                assertJournalAtRest(journalBaseline, "cycle $cycle")
            }

            // La preuve de repos FINALE : l'identité est rendue après le DERNIER cycle aussi.
            scenario.onActivity { activity ->
                attachSuccess(
                    checkNotNull(view.get()).attachKadre(activity, applicationFactory()),
                ).close()
            }
        }
    }

    // -----------------------------------------------------------------------
    // 5× recreate — `ActivityScenario.recreate`
    // -----------------------------------------------------------------------

    /**
     * Le cycle recreate : le recreate détruit l'instance (et SA session — `Stopped
     * (HostDetached)`), la nouvelle instance RESUMED vit pendant que l'ancienne RESTE terminée
     * (aucune résurrection par le binding détruit), et accepte un attach frais (le registre
     * process-wide ne verrouille rien au-delà des identités vivantes).
     */
    @Test
    public fun recreateCyclesTerminateDetachedAndStayTerminated(): Unit = runOnInstrumentation {
        ActivityScenario.launch(consumerActivityClass()).use { scenario ->
            val journalBaseline = AndroidAttachDiagnostics.failures().size
            val previous = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                previous.set(attachSuccess(hostViewIn(activity).attachKadre(activity, applicationFactory())))
            }
            awaitState(checkNotNull(previous.get())) { it == SessionState.Running }

            repeat(RECREATE_CYCLES) { cycle ->
                // ActivityScenario.recreate() bloque le thread de test jusqu'à la nouvelle
                // instance RESUMED — jamais sur le main (il y attendrait ses propres transitions).
                scenario.recreate()
                val old = checkNotNull(previous.get()) { "cycle $cycle: no session to terminate" }
                assertEquals(
                    "cycle $cycle: the recreate terminates the instance's session exactly",
                    SessionOutcome.Stopped(SessionStopReason.HostDetached),
                    awaitOutcome(old),
                )
                soakHeldSession(scenario, old, "recreate cycle $cycle")
                assertJournalAtRest(journalBaseline, "recreate cycle $cycle")

                // La nouvelle instance accepte un attach frais — l'isolation par identité.
                val next = AtomicReference<KadreSession?>(null)
                scenario.onActivity { activity ->
                    next.set(attachSuccess(hostViewIn(activity).attachKadre(activity, applicationFactory())))
                }
                awaitState(checkNotNull(next.get())) { it == SessionState.Running }
                previous.set(next.get())
            }

            // La session survivante se ferme proprement — le dernier cycle ne laisse rien.
            val last = checkNotNull(previous.get())
            runOnMain { last.close() }
            assertEquals(
                "the surviving session closes with the exact host-requested outcome",
                SessionOutcome.Stopped(SessionStopReason.HostRequested),
                awaitOutcome(last),
            )
            assertJournalAtRest(journalBaseline, "after the last recreate cycle")
        }
    }

    // -----------------------------------------------------------------------
    // transitions background/foreground — `moveToState`
    // -----------------------------------------------------------------------

    /**
     * Les allers-retours arrière-plan/avant-plan : la session RESTE vivante (aucune mort
     * induite par le churn), les publications lifecycle de la surface suivent chaque transition
     * (le canal observable du binding), le journal reste au repos ; puis le close final livre
     * l'outcome exact et le churn post-terminal ne ressuscite rien.
     */
    @Test
    public fun backgroundTransitionsKeepTheSessionAliveAndBounded(): Unit = runOnInstrumentation {
        ActivityScenario.launch(consumerActivityClass()).use { scenario ->
            val captured = AtomicReference<KadreScope?>(null)
            val journalBaseline = AndroidAttachDiagnostics.failures().size
            val session = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                session.set(
                    attachSuccess(
                        hostViewIn(activity).attachKadre(
                            lifecycleOwner = activity,
                            applicationFactory = applicationFactory(captured),
                        ),
                    ),
                )
            }
            val live = checkNotNull(session.get())
            awaitState(live) { it == SessionState.Running }
            awaitSurfaceState(captured) { state ->
                state.attachment == SurfaceAttachmentState.Attached &&
                    state.visibility == SurfaceVisibility.Visible &&
                    state.focus == SurfaceFocus.Focused
            }

            repeat(BACKGROUND_ROUND_TRIPS) { cycle ->
                scenario.moveToState(Lifecycle.State.CREATED)
                awaitSurfaceState(captured) { state ->
                    state.attachment == SurfaceAttachmentState.Attached &&
                        state.visibility == SurfaceVisibility.Hidden &&
                        state.focus == SurfaceFocus.Unfocused
                }
                scenario.moveToState(Lifecycle.State.RESUMED)
                awaitSurfaceState(captured) { state ->
                    state.visibility == SurfaceVisibility.Visible &&
                        state.focus == SurfaceFocus.Focused
                }
                assertEquals(
                    "cycle $cycle: the background churn must not kill the session",
                    SessionState.Running,
                    live.state.value,
                )
                assertJournalAtRest(journalBaseline, "background cycle $cycle")
            }

            runOnMain { live.close() }
            assertEquals(
                "the session survives the transitions AND closes with the exact outcome",
                SessionOutcome.Stopped(SessionStopReason.HostRequested),
                awaitOutcome(live),
            )
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            soakHeldSession(scenario, live, "after the background cycles")
            assertJournalAtRest(journalBaseline, "after the background cycles")
        }
    }

    // -----------------------------------------------------------------------
    // 4× service lifecycle — le FGS de capture au repos
    // -----------------------------------------------------------------------

    /**
     * Les cycles du service foreground de capture, CÔTÉ REPOS : le test ne démarre JAMAIS le
     * FGS (sans projection réelle, le `startForeground(type=mediaProjection)` du service jette
     * DANS le process sur API 34+ — les cycles `start` vivent dans les scénarios AND-009
     * consentis). Mesuré par cycle :
     *
     * 1. ZÉRO `AndroidCaptureService` en cours d'exécution — la lecture comportementale du
     *    refcount `AndroidCaptureForegroundOwnership` (privé, hors de portée du device — cf.
     *    la KDoc de classe) : le `stopService` ne part qu'à la DERNIÈRE relâche, un hold qui
     *    fuite laisserait le service vivant.
     * 2. Le registre d'armement n'accumule RIEN : `arm → forget → arm` rend une attente NEUVE
     *    (l'entrée oubliée a bien disparu) ; la sonde se nettoie (`forget` final).
     * 3. Le contrôle de production sans `start` réussi ne relâche rien ni ne lance —
     *    `close()` (== `stop()`) sur un contrôle jamais détenteur est un no-op sûr, et le
     *    service reste absent après lui.
     */
    @Test
    public fun serviceLifecycleCyclesLeaveNoForegroundResidue(): Unit = runOnInstrumentation {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val journalBaseline = AndroidAttachDiagnostics.failures().size

        repeat(SERVICE_CYCLES) { cycle ->
            assertNoCaptureServiceRunning(context)

            val token = "kadre-leak-cycle-$cycle"
            val armed = AndroidCaptureForegroundRegistry.arm(token)
            AndroidCaptureForegroundRegistry.forget(token)
            val rearmed = AndroidCaptureForegroundRegistry.arm(token)
            assertTrue(
                "cycle $cycle: a forgotten token's wait slot is really released (fresh wait, not the stale one)",
                armed !== rearmed,
            )
            AndroidCaptureForegroundRegistry.forget(token)

            val control = AndroidServiceCaptureForeground(context)
            control.close()
            assertNoCaptureServiceRunning(context)
            assertJournalAtRest(journalBaseline, "service cycle $cycle")
        }
    }

    // -----------------------------------------------------------------------
    // 4× cycles install/close des ports P7 sur les SEAMS SDK RÉELS
    // -----------------------------------------------------------------------

    /**
     * Le port display, en cycles, sur le DisplayManager RÉEL enveloppé-compteur (délégation
     * totale — chaque cycle pose et retire un VRAI `DisplayListener`) : l'install publie
     * l'inventaire initial synchronement et porte exactement UNE registration ; le close
     * désenregistre TOUT et ferme le seam possédé exactement une fois. Une fuite de listener
     * rendrait le compte final non nul — CE cycle échoue là, seul.
     */
    @Test
    public fun displayPortCyclesReleaseEveryListenerRegistration(): Unit = runOnInstrumentation {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        repeat(PORT_CYCLES) { cycle ->
            val counting = CountingDisplaySource(AndroidDisplayManagerSource(context))
            val port = AndroidDisplayPort(counting)
            val published = AtomicReference<KadreResult<DisplayPortSnapshot>?>(null)
            port.installSnapshotObserver { snapshot -> published.set(snapshot) }

            val snapshot = checkNotNull(published.get()) {
                "cycle $cycle: the install never published the initial inventory"
            }
            assertTrue(
                "cycle $cycle: the initial publication is a success (the emulator has a default display)",
                snapshot is KadreResult.Success,
            )
            assertTrue(
                "cycle $cycle: the initial inventory is non-empty",
                (snapshot as KadreResult.Success<DisplayPortSnapshot>).value.displays.isNotEmpty(),
            )
            assertEquals(
                "cycle $cycle: the install registered exactly one SDK listener",
                1,
                counting.registrations.get(),
            )

            port.close()
            assertEquals(
                "cycle $cycle: the close unregistered every SDK listener",
                0,
                counting.registrations.get(),
            )
            assertEquals(
                "cycle $cycle: the port closed its owned seam exactly once",
                1,
                counting.sourceCloses.get(),
            )
        }
    }

    /**
     * Le port devices génériques, même discipline sur l'InputManager RÉEL : exactement une
     * registration à l'install, zéro au close, et l'inventaire vivant reste borné (la table
     * est RE-BÂTIE du seam à chaque lecture — jamais un delta mémorisé qui grossirait).
     */
    @Test
    public fun inputDevicePortCyclesReleaseEveryListenerRegistration(): Unit = runOnInstrumentation {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        repeat(PORT_CYCLES) { cycle ->
            val counting = CountingInputSource(AndroidInputManagerSource(context))
            val port = AndroidInputDevicePort(counting)

            assertTrue(
                "cycle $cycle: the live device inventory stays bounded",
                port.devices.size <= MAX_INVENTORY_SIZE,
            )
            port.installObserver { }
            assertEquals(
                "cycle $cycle: the install registered exactly one SDK listener",
                1,
                counting.registrations.get(),
            )

            port.close()
            assertEquals(
                "cycle $cycle: the close unregistered every SDK listener",
                0,
                counting.registrations.get(),
            )
        }
    }

    /**
     * Le port gamepad, même discipline, plus l'INVENTAIRE : la table des manettes est
     * déterministe d'un cycle à l'autre sur le même device (une fuite de la table `pads`
     * grossirait l'inventaire au fil des cycles — CE cycle échoue là) ; le close désenregistre
     * tout (le seam partagé, lui, n'est PAS fermé par le port — la couture d'attach le ferme ;
     * le compteur de fermes de seam reste donc à zéro ici).
     */
    @Test
    public fun gamepadPortCyclesKeepTheInventoryBounded(): Unit = runOnInstrumentation {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var baselineInventory = -1
        repeat(PORT_CYCLES) { cycle ->
            val counting = CountingInputSource(AndroidInputManagerSource(context))
            val port = AndroidGamepadPort(counting, Build.VERSION.SDK_INT)

            port.installObserver { }
            assertEquals(
                "cycle $cycle: the install registered exactly one SDK listener",
                1,
                counting.registrations.get(),
            )
            val inventory = port.gamepads
            assertTrue(
                "cycle $cycle: the gamepad inventory stays bounded",
                inventory.size <= MAX_INVENTORY_SIZE,
            )
            if (baselineInventory < 0) {
                baselineInventory = inventory.size
            } else {
                assertEquals(
                    "cycle $cycle: the gamepad inventory is deterministic across cycles (a leaked pads table would grow)",
                    baselineInventory,
                    inventory.size,
                )
            }

            port.close()
            assertEquals(
                "cycle $cycle: the close unregistered every SDK listener",
                0,
                counting.registrations.get(),
            )
            assertEquals(
                "cycle $cycle: the shared seam is NOT closed by the port (the attach seam owns it)",
                0,
                counting.sourceCloses.get(),
            )
        }
    }

    // -----------------------------------------------------------------------
    // Harnais — l'activité hôte, l'attach nominal, les attentes bornées.
    // -----------------------------------------------------------------------

    /**
     * L'activité LAUNCHER du consumer, résolue PAR NOM : le module ami ne compile JAMAIS
     * contre le consumer (le consumer dépend de CET AAR) — la classe existe au RUNTIME dans
     * l'APK androidTest qui porte ces scénarios, son absence y est une erreur de câblage nommée.
     */
    @Suppress("UNCHECKED_CAST")
    private fun consumerActivityClass(): Class<ComponentActivity> = try {
        Class.forName(CONSUMER_ACTIVITY) as Class<ComponentActivity>
    } catch (missing: ClassNotFoundException) {
        throw IllegalStateException(
            "the consumer launcher activity is missing from the androidTest APK: $CONSUMER_ACTIVITY",
            missing,
        )
    }

    /** Une View de test POSÉE dans le decor de l'activité — l'identité attachable du cycle. */
    private fun hostViewIn(activity: ComponentActivity): View {
        val host = FrameLayout(activity)
        (activity.findViewById(android.R.id.content) as ViewGroup).addView(host)
        return host
    }

    /** L'application minimale : suspendue pour toujours, zéro effet observable. */
    private fun applicationFactory(
        captured: AtomicReference<KadreScope?>? = null,
    ): KadreApplicationFactory = KadreApplicationFactory {
        KadreApplication {
            captured?.set(this)
            awaitCancellation()
        }
    }

    private fun attachSuccess(result: KadreResult<KadreSession>): KadreSession = when (result) {
        is KadreResult.Success -> result.value
        is KadreResult.Failure -> error("expected attach success, got ${result.reason}")
    }

    /** Le block sur le thread d'instrumentation (JUnit4 y court déjà — la forme documente le thread). */
    private fun runOnInstrumentation(block: () -> Unit) = block()

    private fun runOnMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    /** L'état attendu, par polling borné sur le thread d'instrumentation — jamais une course. */
    private fun awaitState(
        session: KadreSession,
        predicate: (SessionState) -> Boolean,
    ): SessionState {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MILLIS
        var current = session.state.value
        while (System.currentTimeMillis() < deadline) {
            current = session.state.value
            if (predicate(current)) return current
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error("session state never satisfied the predicate within ${AWAIT_TIMEOUT_MILLIS}ms; last=$current")
    }

    /** L'outcome terminal exact, par polling borné. */
    private fun awaitOutcome(session: KadreSession): SessionOutcome {
        val state = awaitState(session) { it is SessionState.Terminated }
        return (state as SessionState.Terminated).outcome
    }

    private fun awaitHandlePhase(handle: KadreSessionHandle, expected: KadreSessionPhase, label: String) {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (handle.state().phase == expected) return
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error("$label: the handle never reached $expected within ${AWAIT_TIMEOUT_MILLIS}ms; last=${handle.state()}")
    }

    /** La surface primaire via le [KadreScope] capturé — le canal public des publications. */
    private fun awaitSurfaceState(
        captured: AtomicReference<KadreScope?>,
        predicate: (SurfaceState) -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MILLIS
        var last = "no primary surface yet"
        while (System.currentTimeMillis() < deadline) {
            val surface = captured.get()?.primarySurface?.value
            if (surface != null) {
                val state = surface.state.value
                if (predicate(state)) return
                last = "$state"
            }
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error("surface state never satisfied the predicate within ${AWAIT_TIMEOUT_MILLIS}ms; last=$last")
    }

    /**
     * LE SOAK d'absence : le churn lifecycle est le stimulus de résurrection, puis l'état est
     * relu pendant toute la fenêtre bornée et CHAQUE lecture doit porter le même outcome
     * exact. Une résurrection (binding qui fuite et repart) sortirait du terminal pendant la
     * fenêtre — CE cycle échoue là.
     */
    private fun soakHeldSession(scenario: ActivityScenario<*>, session: KadreSession, label: String) {
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        runOnMain { /* un tour de looper main synchronisé avant la fenêtre */ }
        val expected = (session.state.value as? SessionState.Terminated)?.outcome
        assertNotNull("$label: the soak precondition is a terminated session", expected)
        val deadline = System.currentTimeMillis() + SOAK_WINDOW_MILLIS
        while (System.currentTimeMillis() < deadline) {
            val current = session.state.value
            assertTrue(
                "$label: the terminal did not hold under the lifecycle churn — last=$current",
                current is SessionState.Terminated && current.outcome == expected,
            )
            Thread.sleep(SOAK_STEP_MILLIS)
        }
    }

    /** Le miroir handle de [soakHeldSession] — la phase terminale et l'outcome du billet. */
    private fun soakHeldHandle(scenario: ActivityScenario<*>, handle: KadreSessionHandle, label: String) {
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        runOnMain { }
        val expected = handle.state().outcome
        val deadline = System.currentTimeMillis() + SOAK_WINDOW_MILLIS
        while (System.currentTimeMillis() < deadline) {
            val current = handle.state()
            assertTrue(
                "$label: the handle terminal did not hold under the churn — last=$current",
                current.phase == KadreSessionPhase.Terminated && current.outcome == expected,
            )
            Thread.sleep(SOAK_STEP_MILLIS)
        }
    }

    /**
     * Le journal de diagnostic au repos : un cycle PROPRE ne reporte RIEN (la taille ne bouge
     * pas) et le journal reste sous sa capacité process-wide — un cycle qui fuiterait par une
     * terminaison-sale ou un callback lançant passerait ICI.
     */
    private fun assertJournalAtRest(baseline: Int, label: String) {
        val failures = AndroidAttachDiagnostics.failures()
        assertEquals(
            "$label: a clean cycle must not report any diagnostic",
            baseline,
            failures.size,
        )
        assertTrue(
            "$label: the diagnostic journal stays bounded by its capacity",
            failures.size <= JOURNAL_CAPACITY,
        )
    }

    /**
     * ZÉRO FGS de capture au repos, par polling borné — `getRunningServices` est déprécié
     * depuis l'API 26 MAIS reste fonctionnel pour les services du PROPRE app (exactement ce
     * que la mesure lit) ; c'est la conséquence observable du refcount à zéro.
     */
    private fun assertNoCaptureServiceRunning(context: Context) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            val running = manager.getRunningServices(Int.MAX_VALUE).none { service ->
                service.service.className == CAPTURE_SERVICE_CLASS
            }
            if (running) return
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error("the capture foreground service is still running at rest — a foreground hold leaked")
    }

    /**
     * Le compteur du seam display — délégation TOTALE au seam de production donné, il ne fait
     * qu'observer les registrations réelles posées au DisplayManager.
     */
    private class CountingDisplaySource(
        private val delegate: AndroidDisplayManagerSource,
    ) : AndroidDisplaySource {
        val registrations = AtomicInteger()
        val sourceCloses = AtomicInteger()

        override fun displays(): List<Any> = delegate.displays()

        override fun displayInfo(display: Any): AndroidDisplayInfo = delegate.displayInfo(display)

        override fun registerListener(listener: (Any) -> Unit) {
            registrations.incrementAndGet()
            delegate.registerListener(listener)
        }

        override fun unregisterListener(listener: (Any) -> Unit) {
            registrations.decrementAndGet()
            delegate.unregisterListener(listener)
        }

        override fun close() {
            sourceCloses.incrementAndGet()
            delegate.close()
        }
    }

    /** Le compteur du seam input — la même délégation totale sur l'InputManager réel. */
    private class CountingInputSource(
        private val delegate: AndroidInputManagerSource,
    ) : AndroidInputDeviceSource {
        val registrations = AtomicInteger()
        val sourceCloses = AtomicInteger()

        override fun deviceIds(): List<Int> = delegate.deviceIds()

        override fun deviceInfo(deviceId: Int): AndroidInputDeviceInfo? = delegate.deviceInfo(deviceId)

        override fun registerListener(listener: (Int) -> Unit) {
            registrations.incrementAndGet()
            delegate.registerListener(listener)
        }

        override fun unregisterListener(listener: (Int) -> Unit) {
            registrations.decrementAndGet()
            delegate.unregisterListener(listener)
        }

        override fun startManagedRumble(deviceId: Int, durationMs: Long, strongMagnitude: Float, weakMagnitude: Float): Boolean =
            delegate.startManagedRumble(deviceId, durationMs, strongMagnitude, weakMagnitude)

        override fun startOneShot(deviceId: Int, durationMs: Long, amplitude: Float): Boolean =
            delegate.startOneShot(deviceId, durationMs, amplitude)

        override fun cancelVibration(deviceId: Int) = delegate.cancelVibration(deviceId)

        override fun close() {
            sourceCloses.incrementAndGet()
            delegate.close()
        }
    }

    private companion object {
        /** L'activité LAUNCHER du consumer — résolue par nom (cf. [consumerActivityClass]). */
        const val CONSUMER_ACTIVITY = "org.graphiks.kadre.consumer.ConsumerActivity"

        /** Le FGS de la bibliothèque — la classe lue dans `getRunningServices`. */
        const val CAPTURE_SERVICE_CLASS = "org.graphiks.kadre.platform.android.AndroidCaptureService"

        /** 10 cycles attach/stop : les 5 premiers Kotlin, les 5 suivants par la façade Java. */
        const val ATTACH_STOP_CYCLES = 10
        const val KOTLIN_ATTACH_CYCLES = 5
        const val RECREATE_CYCLES = 5
        const val BACKGROUND_ROUND_TRIPS = 4
        const val SERVICE_CYCLES = 4
        const val PORT_CYCLES = 4

        /** La borne d'inventaire de la mesure (un device réel dépasse rarement quelques entrées). */
        const val MAX_INVENTORY_SIZE = 64

        /** La capacité du journal `AndroidAttachDiagnostics` (JOURNAL_CAPACITY, privé côté production). */
        const val JOURNAL_CAPACITY = 16

        const val AWAIT_TIMEOUT_MILLIS = 10_000L
        const val SOAK_WINDOW_MILLIS = 500L
        const val POLL_INTERVAL_MILLIS = 25L
        const val SOAK_STEP_MILLIS = 50L
    }
}
