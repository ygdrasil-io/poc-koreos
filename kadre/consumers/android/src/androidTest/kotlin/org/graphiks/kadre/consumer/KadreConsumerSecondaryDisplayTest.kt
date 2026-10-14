package org.graphiks.kadre.consumer

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.platform.android.attachKadre
import org.graphiks.kadre.platform.android.attachKadreSecondaryDisplay
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.window.WindowRequestOutcome
import org.junit.runner.RunWith

/**
 * Les huit scénarios instrumentés du contrat AND-011 (session présentation sur écran
 * secondaire) sur l'API publique résolue depuis l'umbrella PUBLIÉ. Chaque nom de méthode est
 * un `testName` contractuel du mapping AND-011 — la correspondance :
 *
 * | evidence id (registre)                              | méthode                                   | kind      |
 * |-----------------------------------------------------|-------------------------------------------|-----------|
 * | android-secondary-display-opens-session             | androidSecondaryDisplayOpensSession       | scenario  |
 * | android-secondary-display-no-window-claim           | androidSecondaryDisplayNoWindowClaim      | sentinel  |
 * | android-secondary-display-input-via-presentation    | androidSecondaryDisplayInputViaPresentation | scenario |
 * | android-secondary-display-display-removal-terminal  | androidSecondaryDisplayDisplayRemovalTerminal | scenario |
 * | android-secondary-display-attached-view-denied      | androidSecondaryDisplayAttachedViewDenied | scenario  |
 * | android-secondary-display-same-display-twice-denied | androidSecondaryDisplaySameDisplayTwiceDenied | scenario |
 * | android-secondary-display-coexists-with-main-session | androidSecondaryDisplayCoexistsWithMainSession | scenario |
 * | android-secondary-display-activity-stop-hides       | androidSecondaryDisplayActivityStopHides  | scenario  |
 *
 * L'ÉCRAN SIMULÉ, ÉPELÉ AVANT TOUT : le provisioning (le script de gate et le job CI, pas le
 * test) pose `settings put global overlay_display_devices 1280x720/160` AVANT la passe et le
 * retire APRÈS (cleanup trap) ; le display overlay naît de façon asynchrone (~1 s) et C'EST
 * LE TEST QUI POLLE l'inventaire DisplayManager (≤ 10 s, jamais un sleep). Un tel display de
 * simulation porte FLAG_PRESENTATION → classé Virtual par la règle Physical/Virtual du
 * DisplayPort (limite documentée P7) : les assertions du contrat portent sur les MÉTRIQUES
 * et l'IDENTITÉ de l'écran, jamais sur `DisplayType.Physical`.
 *
 * LE CANAL D'ENTRÉE (ruling du contrôleur, scénario input-via-presentation) :
 * `Instrumentation.sendKeyDownUpSync` ne sait PAS cibler un écran secondaire (l'injection
 * atterrit sur le display par défaut). Le canal réel qui cible l'écran 2 sur l'API 35 est la
 * commande shell `input -d <displayId> tap` (l'identité shell de l'UiAutomation, le
 * InputDispatcher route vers les fenêtres DU display nommé — ici la fenêtre du média). Le
 * scénario l'utilise : tap shell → dispatchTouchEvent de la fenêtre présentation → la chaîne
 * `Window.Callback` de la Task 3 → LA session présentation. Le routage PAR SESSION (l'input
 * du média ne nourrit JAMAIS la session principale) est prouvé côté hôte par
 * `AndroidPresentationInputTest` (la composition conduite par un fake, sans android.jar) —
 * l'honnêteté du contrat bat le canal d'injection : si le shell ne savait pas cibler l'écran
 * 2, le scénario référencerait la preuve hôte et testerait attach+coexistence sur device.
 *
 * LE CANAL LIFECYCLE (ruling du contrôleur, scénario activity-stop-hides) : le canal honnête
 * du module — `ActivityScenario.moveToState` (les moves d'état du lifecycle réels de
 * l'Activity, jamais un `onStop()` appelé à la main) : RESUMED → STARTED cache le média,
 * STARTED → RESUMED le remontre — hide/show SANS terminal, le média survit aux cycles.
 *
 * LA LIBÉRATION DU REGISTRE : la table d'ownership process-wide est interne au module
 * platform (prouvée vide côté hôte, `AndroidSecondaryDisplayAttachTest`) ; sur device, le
 * proxy honnête est l'ADMISSION SUIVANTE — un re-attach qui réussit après la terminaison
 * prouve que la clé (contentView identité OU displayId valeur) a bien été libérée (un
 * registre qui fuirait répondrait `AlreadyInUse(Host)`).
 */
@RunWith(AndroidJUnit4::class)
class KadreConsumerSecondaryDisplayTest {

    /**
     * android-secondary-display-opens-session — attach présentation sur l'écran simulé :
     * session Running, surface aux métriques RÉELLES de l'écran (1280×720 du provisioning,
     * recollées à la lecture SDK indépendante) et premier show LEVEL-TRIGGERED (l'Activity
     * est RESUMED à l'attach — le média est Visible sans aucun move de lifecycle).
     */
    @Test
    fun androidSecondaryDisplayOpensSession() {
        runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val display = awaitOverlayDisplay()
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadreSecondaryDisplay(
                            presentationDisplay = display,
                            contentView = FrameLayout(activity),
                            applicationFactory = scopeCapturingFactory(captured),
                            parentScope = MainScope(),
                        ),
                    ),
                )
            }
            val handle = ConsumerTestSupport.notNull(session)
            ConsumerTestSupport.awaitRunning(handle)

            // Les métriques RÉELLES de l'écran simulé — la lecture SDK indépendante du
            // scénario est l'oracle (jamais la valeur du provisioning prise sur parole).
            val real = Point().also { display.getRealSize(it) }
            val surface = awaitPrimarySurface(captured)
            assertEquals(PhysicalSize(real.x, real.y), surface.state.value.physicalSize)
            assertEquals(
                PhysicalSize(OVERLAY_WIDTH, OVERLAY_HEIGHT),
                surface.state.value.physicalSize,
                "the simulated screen's provisioning contract is 1280x720",
            )
            assertEquals(1.0, surface.state.value.scaleFactor, 1e-9)
            assertEquals(
                org.graphiks.kadre.surface.LogicalSize(OVERLAY_WIDTH.toDouble(), OVERLAY_HEIGHT.toDouble()),
                surface.state.value.logicalSize,
            )

            // Premier show level-triggered : l'hôte RESUMED à l'attach montre le média.
            awaitSurfaceVisibility(captured, SurfaceVisibility.Visible)
            ConsumerTestSupport.onMain { handle.close() }
            ConsumerTestSupport.awaitTerminal(handle, SessionOutcome.Stopped(SessionStopReason.HostRequested))
        }
        }
    }

    /**
     * android-secondary-display-no-window-claim — SENTINELLE : la session présentation ne
     * revendique AUCUN champ fenêtre en v1. La capability publiée est le refus
     * `Unsupported(RequestWindow)` et l'appel `requestWindow()` termine bien
     * `Rejected(Unsupported(RequestWindow))` — la table honnête du manager SANS seam natif
     * (`native = null`), jamais une promesse de fenêtre qu'aucune primitive ne servirait.
     */
    @Test
    fun androidSecondaryDisplayNoWindowClaim() {
        runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val display = awaitOverlayDisplay()
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadreSecondaryDisplay(
                            presentationDisplay = display,
                            contentView = FrameLayout(activity),
                            applicationFactory = scopeCapturingFactory(captured),
                            parentScope = MainScope(),
                        ),
                    ),
                )
            }
            val handle = ConsumerTestSupport.notNull(session)
            ConsumerTestSupport.awaitRunning(handle)
            val scope = ConsumerTestSupport.awaitKadreScope(captured)

            // La capability publiée refuse AVANT l'appel — sur SA propre valeur exacte.
            assertEquals(
                Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RequestWindow)),
                scope.windows.state.value.capabilities.requestWindow,
                "the presentation session never claims a window-creation capability",
            )

            // L'appel : la requête est admise (dégénérée) mais son outcome est terminal
            // `Rejected(Unsupported(RequestWindow))` avant que l'appelant ne la voie.
            val request = assertIs<KadreResult.Success<org.graphiks.kadre.window.WindowRequest>>(
                scope.windows.requestWindow(),
            ).value
            assertEquals(
                WindowRequestOutcome.Rejected(KadreFailure.Unsupported(KadreOperation.RequestWindow)),
                request.await(),
                "requestWindow terminates Rejected(Unsupported(RequestWindow)) — never a fake window",
            )
            ConsumerTestSupport.onMain { handle.close() }
            ConsumerTestSupport.awaitTerminal(handle, SessionOutcome.Stopped(SessionStopReason.HostRequested))
        }
        }
    }

    /**
     * android-secondary-display-input-via-presentation — la chaîne d'entrée du média, de bout
     * en bout sur le canal RÉEL : `input -d <displayId> tap` (l'identité shell de
     * l'UiAutomation cible l'écran simulé, le seul canal d'injection qui sache le faire —
     * `Instrumentation.sendKeyDownUpSync` atterrit sur le display par défaut, ruling du
     * contrôleur) → dispatchTouchEvent de la fenêtre présentation → la chaîne
     * `Window.Callback` posée au premier show → LA session présentation publie un toucher.
     * Le routage PAR SESSION (jamais l'input du média vers la session principale) est prouvé
     * côté hôte par `AndroidPresentationInputTest` (composition pilotable, sans android.jar)
     * — ce scénario le épingle sur la frontière réelle : si le shell ne savait pas cibler
     * l'écran 2, l'attente bornée échouerait (le scénario référencerait alors la preuve hôte
     * et ne testerait que attach+coexistence ; l'honnêteté du contrat bat le canal).
     */
    @Test
    fun androidSecondaryDisplayInputViaPresentation() {
        runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val display = awaitOverlayDisplay()
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            val contentView = AtomicReference<android.view.View?>(null)
            scenario.onActivity { activity ->
                val content = FrameLayout(activity)
                contentView.set(content)
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadreSecondaryDisplay(
                            presentationDisplay = display,
                            contentView = content,
                            applicationFactory = scopeCapturingFactory(captured),
                            parentScope = MainScope(),
                        ),
                    ),
                )
            }
            val handle = ConsumerTestSupport.notNull(session)
            ConsumerTestSupport.awaitRunning(handle)
            awaitSurfaceVisibility(captured, SurfaceVisibility.Visible)
            val surface = awaitPrimarySurface(captured)

            // Le canal réel ciblé : le tap shell SUR l'écran du média, au centre du 1280x720 —
            // UNE FOIS la fenêtre du média réellement montée ET layoutée (le show SDK rend
            // la main avant l'enregistrement asynchrone de la fenêtre et de sa région
            // touchable auprès du WindowManagerService : un tap trop tôt tomberait à l'eau).
            awaitContentViewLaidOut(contentView)
            val recorder = InputEventRecorder(surface.input.events)
            shellTapUntilTouch(
                displayId = display.displayId,
                x = OVERLAY_WIDTH / 2,
                y = OVERLAY_HEIGHT / 2,
                recorder = recorder,
                context = "the shell tap on the overlay display",
            )
            recorder.close()
            ConsumerTestSupport.onMain { handle.close() }
            ConsumerTestSupport.awaitTerminal(handle, SessionOutcome.Stopped(SessionStopReason.HostRequested))
        }
        }
    }

    /**
     * android-secondary-display-display-removal-terminal — le retrait réel de l'écran simulé
     * (`settings delete global overlay_display_devices`, l'identité shell) : la session passe
     * terminal `Stopped(HostDetached)` en observation bornée ≤ 10 s, sans résurrection — et
     * le registre est libéré : le MÊME contentView ré-attaché sur un écran re-provisionné
     * réussit (une clé contentView encore tenue répondrait `AlreadyInUse(Host)`). Le
     * scénario RE-PROVISIONNE l'écran après le retrait (le provisioning de la gate ne
     * repasse pas entre deux méthodes — les scénarios suivants doivent retrouver un écran).
     */
    @Test
    fun androidSecondaryDisplayDisplayRemovalTerminal() {
        runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val display = awaitOverlayDisplay()
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            val contentView = AtomicReference<android.view.View?>(null)
            scenario.onActivity { activity ->
                val content = FrameLayout(activity)
                contentView.set(content)
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadreSecondaryDisplay(
                            presentationDisplay = display,
                            contentView = content,
                            applicationFactory = scopeCapturingFactory(captured),
                            parentScope = MainScope(),
                        ),
                    ),
                )
            }
            val handle = ConsumerTestSupport.notNull(session)
            ConsumerTestSupport.awaitRunning(handle)

            // Le retrait réel : l'écran simulé disparaît de l'inventaire du système.
            shellCommand("settings delete global overlay_display_devices")
            ConsumerTestSupport.awaitState(handle, DEFAULT_TIMEOUT_MILLIS) { state ->
                state is org.graphiks.kadre.application.SessionState.Terminated &&
                    state.outcome == SessionOutcome.Stopped(SessionStopReason.HostDetached)
            }

            // La libération du registre, par l'admission suivante : le MÊME contentView sur
            // un écran re-provisionné (l'inventaire est pollé, jamais slept). FAIT SDK
            // ÉPELÉ : le dismiss d'une Presentation GARDE le contentView enfant de sa
            // PhoneWindow morte — le nettoyage côté app (removeView du parent) précède la
            // réutilisation, comme le ferait toute application ; la PREUVE du test porte sur
            // l'admission (une identité contentView encore tenue y répondrait AlreadyInUse),
            // pas sur le parentage SDK.
            shellCommand("settings put global overlay_display_devices $OVERLAY_SPEC")
            val replacement = awaitOverlayDisplay()
            val recaptured = AtomicReference<KadreScope?>(null)
            val second = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                val content = ConsumerTestSupport.notNull(contentView)
                (content.parent as? android.view.ViewGroup)?.removeView(content)
                second.set(
                    ConsumerTestSupport.success(
                        activity.attachKadreSecondaryDisplay(
                            presentationDisplay = replacement,
                            contentView = content,
                            applicationFactory = scopeCapturingFactory(recaptured),
                            parentScope = MainScope(),
                        ),
                    ),
                )
            }
            val secondHandle = ConsumerTestSupport.notNull(second)
            ConsumerTestSupport.awaitRunning(secondHandle)
            ConsumerTestSupport.onMain { secondHandle.close() }
            ConsumerTestSupport.awaitTerminal(secondHandle, SessionOutcome.Stopped(SessionStopReason.HostRequested))

            // Pas de résurrection : le terminal du premier handle tient après le cycle complet.
            assertTrue(
                handle.state.value is org.graphiks.kadre.application.SessionState.Terminated,
                "a removed display's session never comes back",
            )
        }
        }
    }

    /**
     * android-secondary-display-attached-view-denied — un contentView DÉJÀ attaché à la
     * fenêtre principale est refusé `InvalidRequest("contentView")` (Kadre monte la vue dans
     * la présentation — un contentView déjà monté serait volé à sa fenêtre), SANS réserver :
     * l'attach valide qui suit, sur le MÊME écran, réussit (le refus n'a rien laissé tenir).
     */
    @Test
    fun androidSecondaryDisplayAttachedViewDenied() {
        runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val display = awaitOverlayDisplay()
            val attempt = AtomicReference<KadreResult<KadreSession>?>(null)
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                attempt.set(
                    activity.attachKadreSecondaryDisplay(
                        presentationDisplay = display,
                        contentView = activity.consumerView, // attaché à la fenêtre principale
                        applicationFactory = scopeCapturingFactory(captured),
                        parentScope = MainScope(),
                    ),
                )
                val refusal = assertIs<KadreResult.Failure>(ConsumerTestSupport.notNull(attempt))
                assertEquals(KadreFailure.InvalidRequest("contentView"), refusal.reason)

                // Le refus n'a RIEN réservé : l'attach valide sur le même écran réussit.
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadreSecondaryDisplay(
                            presentationDisplay = display,
                            contentView = FrameLayout(activity),
                            applicationFactory = scopeCapturingFactory(captured),
                            parentScope = MainScope(),
                        ),
                    ),
                )
            }
            val handle = ConsumerTestSupport.notNull(session)
            ConsumerTestSupport.awaitRunning(handle)
            ConsumerTestSupport.onMain { handle.close() }
            ConsumerTestSupport.awaitTerminal(handle, SessionOutcome.Stopped(SessionStopReason.HostRequested))
        }
        }
    }

    /**
     * android-secondary-display-same-display-twice-denied — la réservation displayId PAR
     * VALEUR : un second attach sur le MÊME écran est refusé `AlreadyInUse(Host)` pendant
     * que la première session reste Running ; à sa terminaison la clé est libérée — le
     * SECOND CYCLE de réservation sur le même displayId réussit (c'est l'assertion que la
     * mutation « displayId jamais libéré » tue, sur sa propre admission).
     */
    @Test
    fun androidSecondaryDisplaySameDisplayTwiceDenied() {
        runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val display = awaitOverlayDisplay()
            val firstCaptured = AtomicReference<KadreScope?>(null)
            val first = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                first.set(
                    ConsumerTestSupport.success(
                        activity.attachKadreSecondaryDisplay(
                            presentationDisplay = display,
                            contentView = FrameLayout(activity),
                            applicationFactory = scopeCapturingFactory(firstCaptured),
                            parentScope = MainScope(),
                        ),
                    ),
                )
            }
            val firstHandle = ConsumerTestSupport.notNull(first)
            ConsumerTestSupport.awaitRunning(firstHandle)

            // Le second attach sur le MÊME displayId : refusé, la première session intacte.
            val secondAttempt = AtomicReference<KadreResult<KadreSession>?>(null)
            scenario.onActivity { activity ->
                secondAttempt.set(
                    activity.attachKadreSecondaryDisplay(
                        presentationDisplay = display,
                        contentView = FrameLayout(activity),
                        applicationFactory = scopeCapturingFactory(AtomicReference()),
                        parentScope = MainScope(),
                    ),
                )
            }
            val refusal = assertIs<KadreResult.Failure>(ConsumerTestSupport.notNull(secondAttempt))
            assertEquals(KadreFailure.AlreadyInUse(KadreResourceKind.Host), refusal.reason)
            assertEquals(org.graphiks.kadre.application.SessionState.Running, firstHandle.state.value)

            // Terminaison coopérative, puis SECOND CYCLE : le displayId est libre.
            ConsumerTestSupport.onMain { firstHandle.close() }
            ConsumerTestSupport.awaitTerminal(firstHandle, SessionOutcome.Stopped(SessionStopReason.HostRequested))
            val secondCaptured = AtomicReference<KadreScope?>(null)
            val second = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                second.set(
                    ConsumerTestSupport.success(
                        activity.attachKadreSecondaryDisplay(
                            presentationDisplay = display,
                            contentView = FrameLayout(activity),
                            applicationFactory = scopeCapturingFactory(secondCaptured),
                            parentScope = MainScope(),
                        ),
                    ),
                )
            }
            val secondHandle = ConsumerTestSupport.notNull(second)
            ConsumerTestSupport.awaitRunning(secondHandle)
            ConsumerTestSupport.onMain { secondHandle.close() }
            ConsumerTestSupport.awaitTerminal(secondHandle, SessionOutcome.Stopped(SessionStopReason.HostRequested))
        }
        }
    }

    /**
     * android-secondary-display-coexists-with-main-session — la forme Activity ET la forme
     * présentation vivent sur la MÊME Activity (la forme présentation ne réserve JAMAIS
     * l'identité de l'Activity) : les deux sessions Running, l'input du main intact — un tap
     * shell sur l'écran principal nourrit LA session principale seule, un tap ciblé sur
     * l'écran simulé nourrit LA session présentation seule (l'isolation croisée observée
     * dans les DEUX sens sur les canaux réels), et la session principale survit à la
     * terminaison de la présentation.
     */
    @Test
    fun androidSecondaryDisplayCoexistsWithMainSession() {
        runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            val display = awaitOverlayDisplay()
            val mainCaptured = AtomicReference<KadreScope?>(null)
            val main = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                main.set(
                    ConsumerTestSupport.success(
                        activity.attachKadre(
                            surfaceView = activity.consumerView,
                            applicationFactory = scopeCapturingFactory(mainCaptured),
                            parentScope = MainScope(),
                        ),
                    ),
                )
            }
            val mainHandle = ConsumerTestSupport.notNull(main)
            ConsumerTestSupport.awaitRunning(mainHandle)
            val mainSurface = awaitPrimarySurface(mainCaptured)

            // La session présentation sur la MÊME Activity : Running, Visible.
            val castCaptured = AtomicReference<KadreScope?>(null)
            val cast = AtomicReference<KadreSession?>(null)
            val castContent = AtomicReference<android.view.View?>(null)
            scenario.onActivity { activity ->
                val content = FrameLayout(activity)
                castContent.set(content)
                cast.set(
                    ConsumerTestSupport.success(
                        activity.attachKadreSecondaryDisplay(
                            presentationDisplay = display,
                            contentView = content,
                            applicationFactory = scopeCapturingFactory(castCaptured),
                            parentScope = MainScope(),
                        ),
                    ),
                )
            }
            val castHandle = ConsumerTestSupport.notNull(cast)
            ConsumerTestSupport.awaitRunning(castHandle)
            awaitSurfaceVisibility(castCaptured, SurfaceVisibility.Visible)
            val castSurface = awaitPrimarySurface(castCaptured)
            assertEquals(org.graphiks.kadre.application.SessionState.Running, mainHandle.state.value)
            // La fenêtre du média RÉELLEMENT montée ET layoutée (région touchable live).
            awaitContentViewLaidOut(castContent)

            // L'isolation croisée, sur les canaux réels des deux écrans.
            val mainRecorder = InputEventRecorder(mainSurface.input.events)
            val castRecorder = InputEventRecorder(castSurface.input.events)

            // (1) le tap ciblé sur l'écran simulé : LA session présentation seule.
            shellTapUntilTouch(
                displayId = display.displayId,
                x = OVERLAY_WIDTH / 2,
                y = OVERLAY_HEIGHT / 2,
                recorder = castRecorder,
                context = "the presentation session receives its display's tap",
            )
            awaitNoInput(mainRecorder, "the main session stays untouched by the overlay display's tap")

            // (2) le tap sur l'écran principal : LA session principale seule — l'input du
            //     main est intact malgré la fenêtre présentation (le centre de l'écran
            //     1080x2400 de l'émulateur, dans les bornes de la consumerView).
            val mainSize = Point().also { realDisplay().getRealSize(it) }
            shellTapUntilTouch(
                displayId = android.view.Display.DEFAULT_DISPLAY,
                x = mainSize.x / 2,
                y = mainSize.y / 2,
                recorder = mainRecorder,
                context = "the main session still receives its own display's tap",
            )
            awaitNoInput(castRecorder, "the presentation session is untouched by the main display's tap")

            mainRecorder.close()
            castRecorder.close()

            // La présentation termine ; la session principale SURVIT.
            assertEquals(org.graphiks.kadre.application.SessionState.Running, mainHandle.state.value)
            ConsumerTestSupport.onMain { castHandle.close() }
            ConsumerTestSupport.awaitTerminal(castHandle, SessionOutcome.Stopped(SessionStopReason.HostRequested))
            assertEquals(
                org.graphiks.kadre.application.SessionState.Running,
                mainHandle.state.value,
                "the main session survives the presentation session's termination",
            )
            ConsumerTestSupport.onMain { mainHandle.close() }
            ConsumerTestSupport.awaitTerminal(mainHandle, SessionOutcome.Stopped(SessionStopReason.HostRequested))
        }
        }
    }

    /**
     * android-secondary-display-activity-stop-hides — le canal lifecycle honnête du module
     * (`ActivityScenario.moveToState`, jamais un `onStop()` manuel) : STOP CACHE le média,
     * START le REMONTE — deux cycles complets hide/show, la session JAMAIS terminale (elle
     * vit pendant que le média est caché), jamais deux présentations. LE CANAL DU STOP :
     * `moveToState(CREATED)` — le move RESUMED→STARTED de l'ActivityScenario ne livre QUE
     * onPause (relevé par sonde sur l'émulateur API 35 : aucun onStop alors que l'état
     * rapporté est STARTED), l'idiom du module (`android-attach-surviving-scope`, AND-002)
     * passe par CREATED, qui traverse PAUSED PUIS STOPPED — le canal honnête du lifecycle
     * réel, DESTROYED restant le seul terminal.
     */
    @Test
    fun androidSecondaryDisplayActivityStopHides() {
        runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            val display = awaitOverlayDisplay()
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadreSecondaryDisplay(
                            presentationDisplay = display,
                            contentView = FrameLayout(activity),
                            applicationFactory = scopeCapturingFactory(captured),
                            parentScope = MainScope(),
                        ),
                    ),
                )
            }
            val handle = ConsumerTestSupport.notNull(session)
            ConsumerTestSupport.awaitRunning(handle)
            awaitSurfaceVisibility(captured, SurfaceVisibility.Visible)

            // Deux cycles complets : STOP cache (sans terminal), START remonte.
            repeat(HIDE_SHOW_CYCLES) {
                scenario.moveToState(Lifecycle.State.CREATED)
                awaitSurfaceVisibility(captured, SurfaceVisibility.Hidden)
                assertFalse(
                    handle.state.value is org.graphiks.kadre.application.SessionState.Terminated,
                    "a STOP hides the media but never terminates the session",
                )
                scenario.moveToState(Lifecycle.State.RESUMED)
                awaitSurfaceVisibility(captured, SurfaceVisibility.Visible)
                assertFalse(handle.state.value is org.graphiks.kadre.application.SessionState.Terminated)
            }
            ConsumerTestSupport.onMain { handle.close() }
            ConsumerTestSupport.awaitTerminal(handle, SessionOutcome.Stopped(SessionStopReason.HostRequested))
        }
        }
    }

    // ------------------------------------------------------------------
    // Aides du fichier — le miroir des tests AND-002..AND-008.
    // ------------------------------------------------------------------

    /** La factory qui capture le [KadreScope] de la session pour l'observation du test. */
    private fun scopeCapturingFactory(captured: AtomicReference<KadreScope?>): KadreApplicationFactory =
        KadreApplicationFactory {
            KadreApplication {
                captured.set(this)
                kotlinx.coroutines.awaitCancellation()
            }
        }

    /**
     * L'écran simulé de la gate — poll de l'inventaire DisplayManager (≤ 10 s, 25 ms
     * d'intervalle) : le display overlay naît de façon asynchrone après le `settings put`
     * du provisioning. L'écran retenu porte FLAG_PRESENTATION (le fait du display de
     * simulation — la règle Physical/Virtual du DisplayPort le classe Virtual, les
     * assertions du contrat portent sur les métriques et l'identité, jamais le type).
     */
    private fun awaitOverlayDisplay(): Display {
        val manager = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        var last = "no secondary display"
        while (System.currentTimeMillis() < deadline) {
            val overlay = manager.getDisplays().firstOrNull { display ->
                display.displayId != Display.DEFAULT_DISPLAY &&
                    display.flags and Display.FLAG_PRESENTATION != 0
            }
            if (overlay != null) return overlay
            last = "displayIds=${manager.getDisplays().map(Display::getDisplayId)}"
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error(
            "no FLAG_PRESENTATION secondary display within ${DEFAULT_TIMEOUT_MILLIS}ms ($last) — " +
                "the gate must provision the overlay display (settings put global overlay_display_devices $OVERLAY_SPEC)",
        )
    }

    /** Le display par défaut réel de l'émulateur — l'écran principal des taps du scénario coexistence. */
    private fun realDisplay(): Display {
        val manager = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return assertNotNull(manager.getDisplay(Display.DEFAULT_DISPLAY))
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

    /** La visibilité de la surface présentation — le canal public du show/hide du média. */
    private fun awaitSurfaceVisibility(
        captured: AtomicReference<KadreScope?>,
        expected: SurfaceVisibility,
    ) {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        var last = "no primary surface yet"
        while (System.currentTimeMillis() < deadline) {
            val state = captured.get()?.primarySurface?.value?.state?.value
            if (state != null) {
                if (state.visibility == expected) return
                last = "$state"
            }
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error("surface visibility never became $expected within ${DEFAULT_TIMEOUT_MILLIS}ms; last=$last")
    }

    /**
     * Le tap shell RÉEL, ré-injecté de façon bornée : l'enregistrement de la fenêtre du média
     * auprès de l'InputDispatcher est asynchrone au show SDK (binder + premier passage), et
     * un tap injecté dans cette fenêtre système tombe à l'eau SANS erreur ni livraison — le
     * canal est réel, la course l'est aussi (relevé device : le premier tap se perd une fois
     * sur deux juste après le show). La ré-injection est BORNÉE par le MÊME budget total de
     * 10 s : le scénario ne réussit que sur la livraison RÉELLE d'un tap RÉEL — jamais un
     * événement synthétique — et l'échec au budget reste l'échec du canal.
     */
    private fun shellTapUntilTouch(
        displayId: Int,
        x: Int,
        y: Int,
        recorder: InputEventRecorder,
        context: String,
    ): InputEvent.TouchChanged {
        var attempts = 0
        var lastOutput = ""
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            attempts += 1
            lastOutput = shellCommand("input -d $displayId tap $x $y")
            val attemptDeadline = System.currentTimeMillis() + TAP_ATTEMPT_WINDOW_MILLIS
            while (System.currentTimeMillis() < attemptDeadline) {
                recorder.snapshot().filterIsInstance<InputEvent.TouchChanged>().firstOrNull()?.let { return it }
                Thread.sleep(POLL_INTERVAL_MILLIS)
            }
        }
        error(
            "no touch observed within ${DEFAULT_TIMEOUT_MILLIS}ms after $context " +
                "($attempts real taps injected, last output '$lastOutput'); observed=${recorder.snapshot()}",
        )
    }

    /**
     * La fenêtre bornée de l'isolation : aucun ÉVÉNEMENT D'INPUT n'atteint l'AUTRE session.
     * Le `StateReset(FocusLost)` y est admis : montrer la fenêtre du média déplace le focus
     * fenêtre du système, et la session principale observe honnêtement cette perte — ce
     * n'est pas de l'input cross-talk, c'est le host.
     */
    private fun awaitNoInput(recorder: InputEventRecorder, context: String) {
        fun inputEventCount() = recorder.snapshot().count { it !is InputEvent.StateReset }
        val baseline = inputEventCount()
        val deadline = System.currentTimeMillis() + QUIET_WINDOW_MILLIS
        while (System.currentTimeMillis() < deadline) {
            assertEquals(
                baseline,
                inputEventCount(),
                "$context: an input event leaked into the other session: ${recorder.snapshot()}",
            )
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
    }

    /**
     * Le montage RÉEL de la fenêtre du média — le fait observable qui précède tout tap : le
     * show SDK (`Presentation.show()`) rend la main AVANT que le WindowManagerService
     * n'enregistre la fenêtre auprès de l'InputDispatcher (binder asynchrone) ; un tap
     * injecté trop tôt trouve un display sans fenêtre touchable et tombe à l'eau. Le
     * contentView (POSÉ par Kadre dans la présentation) ne porte son attachement fenêtre
     * qu'au premier passage de layout du ViewRootImpl, et sa RÉGION TOUCHABLE n'existe
     * qu'après le premier layout effectif — l'attachement ET des dimensions non nulles
     * sont la lecture honnête de ce montage (relevé device : le tap immédiat est une
     * course perdue une fois sur deux).
     */
    private fun awaitContentViewLaidOut(contentView: AtomicReference<android.view.View?>) {
        val content = ConsumerTestSupport.notNull(contentView)
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (content.isAttachedToWindow && content.width > 0 && content.height > 0) return
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error(
            "the presentation contentView was never laid out within ${DEFAULT_TIMEOUT_MILLIS}ms " +
                "(attached=${content.isAttachedToWindow}, size=${content.width}x${content.height})",
        )
    }

    /**
     * La commande shell du device — l'identité shell de l'UiAutomation (le seul canal qui
     * cible un écran par `input -d`, qui provisionne et retire l'écran simulé par
     * `settings`). Le drainage borné est le miroir du test AND-008. AUCUNE redirection
     * `2>&1` ici : l'exécution passe par le tokenizer Runtime.exec de l'UiAutomation (PAS
     * un shell) — un `2>&1` y devient un ARGUMENT littéral de la commande (`settings
     * delete … 2>&1` ne supprime alors RIEN, `input … 2>&1` refuse le tap — relevé par
     * sonde sur l'émulateur API 35) ; les commandes de CE fichier n'ont pas de stderr
     * décisionnelle.
     */
    private fun shellCommand(command: String): String {
        val descriptor =
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        descriptor.use { fd ->
            FileInputStream(fd.fileDescriptor).use { stream ->
                val output = StringBuilder()
                val buffer = ByteArray(8192)
                val deadline = System.currentTimeMillis() + SHELL_DRAIN_TIMEOUT_MILLIS
                while (true) {
                    val available = stream.available()
                    if (available > 0) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        output.append(String(buffer, 0, read, Charsets.UTF_8))
                        continue
                    }
                    if (System.currentTimeMillis() > deadline) break
                    Thread.sleep(SHELL_DRAIN_POLL_MILLIS)
                }
                return output.toString()
            }
        }
    }

    /**
     * Le recueil d'événements d'input du test — le miroir du `SignalRecorder` du test
     * AND-008 : souscrit UNDISPATCHED sur un dispatcher dédié, le polling reste sur le
     * thread d'instrumentation, le scope du recueil est annulé par le scénario.
     */
    private class InputEventRecorder(flow: Flow<InputEvent>) {
        private val lock = Any()
        private val events = mutableListOf<InputEvent>()
        private val ownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        init {
            ownScope.launch(start = CoroutineStart.UNDISPATCHED) {
                flow.collect { event -> synchronized(lock) { events += event } }
            }
        }

        fun snapshot(): List<InputEvent> = synchronized(lock) { events.toList() }

        /** Termine la fenêtre d'observation : le collecteur est annulé, le recueil figé. */
        fun close() {
            ownScope.cancel()
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
        const val POLL_INTERVAL_MILLIS = 25L

        /** La fenêtre bornée de l'isolation : un tap, aucun événement dans l'autre session. */
        const val QUIET_WINDOW_MILLIS = 1_500L

        /**
         * Le plafond du drainage d'une commande shell : les commandes de CE fichier
         * (`settings`, `input`) ne portent AUCUNE sortie décisionnelle — le drainage ne
         * collecte que le diagnostic d'échec, et l'EFFET de la commande (le provisioning,
         * l'injection) est observé par les polls bornés, jamais par la sortie. Le plafond
         * court borne une commande muette sans jamais couper un effet (relevé device : le
         * drain de 5 s du miroir AND-008 coûterait 5 s PAR commande sans retour).
         */
        const val SHELL_DRAIN_TIMEOUT_MILLIS = 400L
        const val SHELL_DRAIN_POLL_MILLIS = 20L

        /** Le provisioning de la gate : l'écran simulé 1280x720 à la densité 160. */
        const val OVERLAY_WIDTH = 1280
        const val OVERLAY_HEIGHT = 720
        const val OVERLAY_SPEC = "1280x720/160"

        /** Les cycles hide/show du scénario lifecycle (STOP cache, START remonte). */
        const val HIDE_SHOW_CYCLES = 2

        /** La fenêtre d'écoute d'UN tap réellement injecté avant ré-injection bornée. */
        const val TAP_ATTEMPT_WINDOW_MILLIS = 1_000L
    }
}
