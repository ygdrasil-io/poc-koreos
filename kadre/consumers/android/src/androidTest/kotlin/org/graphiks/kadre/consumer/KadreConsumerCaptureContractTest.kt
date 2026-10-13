@file:OptIn(DelicateKadreApi::class)

package org.graphiks.kadre.consumer

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.capture.CaptureManagerState
import org.graphiks.kadre.capture.CaptureOutcome
import org.graphiks.kadre.capture.CapturePermissionScope
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSession
import org.graphiks.kadre.capture.CaptureSessionState
import org.graphiks.kadre.capture.CaptureStopReason
import org.graphiks.kadre.capture.CaptureSources
import org.graphiks.kadre.capture.CaptureTarget
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.KadrePermission
import org.graphiks.kadre.input.PermissionState
import org.graphiks.kadre.platform.android.attachKadre
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.PhysicalSize
import org.junit.After
import org.junit.runner.RunWith

/**
 * Les cinq scénarios instrumentés PUBLICS du contrat AND-009 (capture, consentement réel et
 * frames) sur l'API publique résolue depuis l'umbrella PUBLIÉ — le miroir exact des contrats
 * AND-001..AND-008 (le sixième scénario, `android-capture-source-refused`, nomme un INTERNAL
 * de port et vit dans l'AMI `android-devicetest`, le précédent AND-007). Chaque nom de
 * méthode est un `testName` contractuel du mapping AND-009 :
 *
 * | scenario id (registre)              | méthode                          | sentinelle gardée              |
 * |-------------------------------------|----------------------------------|--------------------------------|
 * | android-capture-surface-stream-bounded | androidCaptureSurfaceStreamBounded | -                           |
 * | android-capture-consent-denied-stateful | androidCaptureConsentDeniedStateful | -                         |
 * | android-capture-token-single-use    | androidCaptureTokenSingleUse     | android-capture-no-prompt-on-readback |
 * | android-capture-fgs-lifecycle       | androidCaptureFgsLifecycle       | android-capture-fgs-stopped-after-terminal |
 * | android-capture-late-attach-consent | androidCaptureLateAttachConsent  | -                              |
 *
 * LE CONSENTEMENT RÉEL : la dialog système MediaProjection est automatisée par l'
 * UiAutomation de l'instrumentation — l'identité shell, la seule qui peut injecter un
 * `input tap` DANS la dialog de systemui. Le mécanisme : `uiautomator dump` (le XML de la
 * hiérarchie réelle) → recherche du bouton par son texte (les libellés API 35 : « Start
 * now » / « Don't allow ») → `input tap` au centre des bounds. Le dump est l'ORACLE : le
 * scénario échoue en citant la hiérarchie courante si le bouton attendu n'existe pas.
 *
 * LES FAITS AND-009, ÉPELÉS PAR SCÉNARIO :
 *
 * 1. SURFACE (android-capture-surface-stream-bounded) — le PixelCopy RÉEL du SurfaceView
 *    attaché (contenu réellement rendu par l'app : un canvas rouge posté dans le holder) :
 *    la configuration complète (révision 0, la taille RÉELLE de la vue) est publiée en
 *    `Streaming` AVANT la première frame ; les frames coulent, chacune aux dimensions de la
 *    configuration et portant LE CONTENU dessiné (le pixel (5,5) est le rouge du canvas) ;
 *    `requestStop` → `Stopped(Requested)` et AUCUNE frame ne suit le terminal.
 * 2. REFUS STATEFUL (android-capture-consent-denied-stateful) — la dialog réelle, bouton
 *    « Don't allow » pressé par l'UiAutomation → `requestPermission` répond un SUCCÈS
 *    stateful (`Denied(canRequestAgain = true)` sur les DEUX scopes, CONTRACTS :64) ; un
 *    `open` ensuite répond le refus de permission SANS nouveau prompt.
 * 3. JETON SINGLE-USE (android-capture-token-single-use) — consentement accordé (dialogue
 *    réel « Start now ») → la session streame ; le readback (`refreshSources`) ne prompt
 *    JAMAIS (la sentinelle `android-capture-no-prompt-on-readback` le garde : une mutation
 *    qui faitlauncher le dialogue au readback tue CE scénario seul) ; un second `open`
 *    sans nouveau consentement → refus de permission (le grant est consommé) ; un NOUVEAU
 *    consentement (dialogue réel encore) → la session 2 vit.
 * 4. FGS (android-capture-fgs-lifecycle) — le service `mediaProjection` DE RESPONSABILITÉ
 *    KADRE : le composant existe dans le manifest mergé, il TOURNE pendant la session
 *    (`ActivityManager.getRunningServices` du propre process — la lecture honnête — et la
 *    notification minimale « Screen capture » postée) et il est ARRÊTÉ au terminal (la
 *    sentinelle `android-capture-fgs-stopped-after-terminal` le garde).
 * 5. ATTACH TARDIF (android-capture-late-attach-consent) — l'activité est RESUMED avant
 *    l'attach ; le consentement via le registre DIRECT (sans LifecycleOwner) fonctionne
 *    sur le registre androidx RÉEL — l'anti-pattern kadre-old
 *    (`ComponentActivity.registerForActivityResult` THROWS après STARTED) est corrigé,
 *    prouvé ICI sur le device, pas seulement en test hôte.
 *
 * Les scénarios sont autonomes, ferment leur session explicitement ; l'observation est un
 * polling borné sur le thread d'instrumentation, les appels View passent par le thread main.
 */
@RunWith(AndroidJUnit4::class)
class KadreConsumerCaptureContractTest {

    /**
     * L'après-scénario : le flux consenti amène l'app projetée (la caméra) DE L'AVANT — les
     * scénarios suivants (les autres classes du gate, focus-sensibles) doivent trouver un
     * appareil PROPRE : le HOME global rend le launcher, jamais une app étrangère devant.
     */
    @After
    fun leaveTheDeviceClean() {
        runCatching {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
        }
        Thread.sleep(300)
    }

    // -- (1) android-capture-surface-stream-bounded ------------------------------------------------------

    /**
     * Le PixelCopy réel du SurfaceView attaché : contenu rendu par l'app, configuration
     * avant frame, frames bornées, `Stopped(Requested)` au stop, zéro frame après.
     */
    @Test
    fun androidCaptureSurfaceStreamBounded() = runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            val surfaceViewRef = AtomicReference<SurfaceView?>(null)
            scenario.onActivity { activity ->
                // La SurfaceView RÉELLE, 320×200, ajoutée au host view ; le contenu réel est
                // dessiné dans SON buffer (le canvas rouge) dès que la surface existe.
                val surfaceView = SurfaceView(activity)
                surfaceView.layoutParams = FrameLayout.LayoutParams(320, 200, Gravity.CENTER)
                activity.consumerView.addView(surfaceView)
                drawRedWhenSurfaceReady(surfaceView)
                surfaceViewRef.set(surfaceView)
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadre(surfaceView, scopeCapturingFactory(captured)),
                    ),
                )
            }
            val scope = ConsumerTestSupport.awaitKadreScope(captured)
            val surfaceView = assertNotNull(surfaceViewRef.get())
            val surface = awaitPrimarySurface(captured)
            // La vue doit être mesurée et son buffer composé AVANT l'open — la première
            // copie lit la taille RÉELLE de la surface (une vue non encore layoutée répond
            // `surface-size-unavailable`, le refus honnête du flux).
            awaitUntil("the SurfaceView to be laid out with a valid surface") {
                surfaceView.width > 0 && surfaceView.height > 0 && surfaceView.holder.surface?.isValid == true
            }

            val manager = scope.capture
            // La capability gelée annonce le bras Surface (API 35 >= 26).
            assertTrue(
                manager.state.value.capabilities.surface
                    is org.graphiks.kadre.diagnostics.Capability.Supported<*>,
                "la capability surface est Supported sur l'API ${android.os.Build.VERSION.SDK_INT}",
            )

            val opened = manager.open(CaptureRequest(target = CaptureTarget.Surface(surface.id)))
            val capture = assertIs<KadreResult.Success<CaptureSession>>(
                opened,
                "le open Surface répond un succès sur le device réel",
            ).value
            val recorder = FrameRecorder(capture)
            try {
                // LE COLLECTEUR D'ABORD : le runtime ne démarre le flux que dans
                // collectFrames — l'état `Streaming` (la configuration complète, révision 0)
                // est posé par le StreamStart du port, avant toute frame au collector.
                recorder.start()
                val streaming = awaitStreaming(capture)
                val expectedSize = PhysicalSize(surfaceView.width, surfaceView.height)
                assertEquals(
                    expectedSize,
                    streaming.configuration.size,
                    "la configuration dérivée de la PREMIÈRE copie porte la taille RÉELLE de la vue",
                )
                assertEquals(0L, streaming.configuration.revision.value, "révision 0")
                assertEquals(
                    org.graphiks.kadre.capture.PixelFormat.Rgba8,
                    streaming.configuration.format,
                )

                awaitFrames(recorder, minimum = 3)

                // LE CONTENU RÉEL : le pixel (5,5) de la première frame est le rouge du
                // canvas dessiné dans le holder — la capture ne fabrique rien.
                val first = recorder.snapshot().first()
                assertEquals(0L, first.revision)
                assertEquals(expectedSize, first.size)
                val bytes = assertNotNull(first.bytes, "la première frame porte ses octets copiés")
                val pixel = (5 * expectedSize.width + 5) * 4
                assertTrue(
                    bytes[pixel].toInt() and 0xFF > 100 &&
                        bytes[pixel + 1].toInt() and 0xFF < 100 &&
                        bytes[pixel + 2].toInt() and 0xFF < 100,
                    "le pixel (5,5) est le rouge dessiné : rgb=" +
                        "${bytes[pixel].toInt() and 0xFF},${bytes[pixel + 1].toInt() and 0xFF},${bytes[pixel + 2].toInt() and 0xFF}",
                )

                // L'arrêt propre : `Stopped(Requested)`, et AUCUNE frame ne suit le
                // terminal OBSERVÉ (le runtime draine son canal d'entrée — le contrat du
                // port est que la pompe est scellée au terminal, pas que le canal déjà
                // rempli se vide en silence).
                capture.requestStop()
                val outcome = capture.awaitTermination()
                assertEquals(
                    CaptureOutcome.Stopped(CaptureStopReason.Requested),
                    outcome,
                    "l'arrêt propre de l'app est l'arrêt demandé",
                )
                // Le collecteur du runtime DRAINE son canal d'entrée au terminal (les
                // frames déjà livrées dans le canal le suivent) : le settle borne la
                // livraison en vol, PUIS la fenêtre du silence prouve que la pompe scellée
                // n'en produit plus AUCUNE.
                Thread.sleep(SILENCE_WINDOW_MILLIS)
                val framesAtTerminal = recorder.snapshot().size
                awaitFrameSilence(recorder, framesAtTerminal)
            } finally {
                recorder.close()
            }
            ConsumerTestSupport.onMain { ConsumerTestSupport.notNull(session).close() }
        }
    }

    // -- (2) android-capture-consent-denied-stateful -----------------------------------------------------

    /**
     * La dialog réelle, « Don't allow » pressé par l'UiAutomation : le refus utilisateur est
     * un SUCCÈS stateful (jamais une failure), publié sur les deux scopes, et un `open`
     * ensuite répond le refus sans nouveau prompt.
     */
    @Test
    fun androidCaptureConsentDeniedStateful() = runBlocking {
        val scenario = ActivityScenario.launch(ConsumerActivity::class.java)
        try {
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            val activityRef = AtomicReference<ConsumerActivity?>(null)
            scenario.onActivity { activity ->
                activityRef.set(activity)
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadre(activity.consumerView, scopeCapturingFactory(captured)),
                    ),
                )
            }
            val scope = ConsumerTestSupport.awaitKadreScope(captured)
            awaitPrimarySurface(captured)

            val state = requestPermissionWithDialog(scope, allow = false)
            assertEquals(
                CapturePermissionStateDenied(),
                state.permissions,
                "le refus utilisateur est un état Denied(canRequestAgain=true), sur les DEUX scopes",
            )

            // Le refus stateful : l'open suivant est refusé en permission — jamais un second
            // prompt (la réservation ne relance jamais le dialogue).
            val opened = scope.capture.open(CaptureRequest(target = CaptureTarget.HostChoice))
            assertEquals(
                KadreResult.Failure(KadreFailure.PermissionDenied(KadrePermission.CaptureScreen)),
                opened,
                "sans grant, l'open HostChoice est refusé en permission",
            )
            ConsumerTestSupport.onMain { ConsumerTestSupport.notNull(session).close() }
        } finally {
            // Le flux consenti peut amener l'app projetée DE L'AVANT — l'activité du test
            // passe STOPPED et le close d'ActivityScenario perd alors son état (l'NPE
            // « Current state was null » du runner androidx) : le close du scénario est
            // RÉSILIENT ici, les ressources CONTRACTUELLES (la session capture, le recueil)
            // étant fermées explicitement par le corps.
            runCatching { scenario.close() }
        }
    }
    // -- (3) android-capture-token-single-use ------------------------------------------------------------

    /**
     * Consentement accordé (dialogue réel) → la session streame ; le readback ne prompt
     * jamais ; un second open sans nouveau consentement est refusé ; un NOUVEAU
     * consentement (dialogue réel encore) rend la session 2 vivante.
     */
    @Test
    fun androidCaptureTokenSingleUse() = runBlocking {
        val scenario = ActivityScenario.launch(ConsumerActivity::class.java)
        try {
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            val activityRef = AtomicReference<ConsumerActivity?>(null)
            scenario.onActivity { activity ->
                activityRef.set(activity)
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadre(activity.consumerView, scopeCapturingFactory(captured)),
                    ),
                )
            }
            val scope = ConsumerTestSupport.awaitKadreScope(captured)
            awaitPrimarySurface(captured)
            val manager = scope.capture

            // Consentement 1 — le dialogue réel, « Start now » pressé par l'UiAutomation.
            val granted = requestPermissionWithDialog(scope, allow = true)
            assertEquals(
                CapturePermissionStateGranted(),
                granted.permissions,
                "le consentement accordé est l'état Granted sur les DEUX scopes",
            )

            // LE READBACK — la sentinelle `android-capture-no-prompt-on-readback` garde ce
            // point : lire l'état ne lance JAMAIS le dialogue (une mutation qui prompte au
            // readback empile un second dialogue en vol et tue CE scénario seul : la
            // demande suivante répondrait l'interaction consommée au lieu du grant frais).
            val readback = manager.refreshSources()
            val readState = assertIs<KadreResult.Success<CaptureManagerState>>(readback).value
            assertEquals(
                CapturePermissionStateGranted(),
                readState.permissions,
                "le readback porte l'état courant, sans rien changer",
            )
            assertEquals(
                CaptureSources.HostPickerOnly,
                readState.sources,
                "l'inventaire reste le picker, jamais un inventaire inventé",
            )

            // La session 1 streame : le grant consommé produit un flux réel.
            val firstOpened = manager.open(CaptureRequest(target = CaptureTarget.HostChoice))
            val first = when (firstOpened) {
                is KadreResult.Success<CaptureSession> -> firstOpened.value
                is KadreResult.Failure -> error("le open #1 a échoué : ${firstOpened.reason}")
            }
            val recorder = FrameRecorder(first)
            try {
                recorder.start()
                awaitStreaming(first)
                awaitFrames(recorder, minimum = 1)
                // L'arrêt propre PENDANT la fenêtre d'observation : le terminal de l'APP
                // (`Stopped(Requested)`) — fermer le recueil AVANT l'arrêt livrerait le
                // terminal du COLLECTEUR annulé (`CollectorCancelled`), un autre fait.
                first.requestStop()
                assertEquals(
                    CaptureOutcome.Stopped(CaptureStopReason.Requested),
                    first.awaitTermination(),
                )
            } finally {
                recorder.close()
            }

            // Le jeton est CONSOMMÉ : un second open sans nouveau consentement → refus de
            // permission, jamais le grant stocké re-servi, jamais un prompt.
            assertEquals(
                KadreResult.Failure(KadreFailure.PermissionDenied(KadrePermission.CaptureScreen)),
                manager.open(CaptureRequest(target = CaptureTarget.HostChoice)),
                "un second open sans nouveau consentement est refusé en permission",
            )

            // Un NOUVEAU consentement (dialogue réel encore) → la session 2 vit.
            val grantedAgain = requestPermissionWithDialog(scope, allow = true)
            assertEquals(
                CapturePermissionStateGranted(),
                grantedAgain.permissions,
                "après une session terminée, une nouvelle demande est légale",
            )
            val secondOpened = manager.open(CaptureRequest(target = CaptureTarget.HostChoice))
            val second = when (secondOpened) {
                is KadreResult.Success<CaptureSession> -> secondOpened.value
                is KadreResult.Failure -> error("le open #2 a échoué : ${secondOpened.reason}")
            }
            second.requestStop()
            assertEquals(
                CaptureOutcome.Stopped(CaptureStopReason.Requested),
                second.awaitTermination(),
            )
            ConsumerTestSupport.onMain { ConsumerTestSupport.notNull(session).close() }
        } finally {
            // Le flux consenti peut amener l'app projetée DE L'AVANT — l'activité du test
            // passe STOPPED et le close d'ActivityScenario perd alors son état (l'NPE
            // « Current state was null » du runner androidx) : le close du scénario est
            // RÉSILIENT ici, les ressources CONTRACTUELLES (la session capture, le recueil)
            // étant fermées explicitement par le corps.
            runCatching { scenario.close() }
        }
    }
    // -- (4) android-capture-fgs-lifecycle ---------------------------------------------------------------

    /**
     * Le service `mediaProjection` de responsabilité Kadre : le composant existe dans le
     * manifest mergé, il tourne pendant la session (service + notification minimale), il
     * est arrêté au terminal.
     */
    @Test
    fun androidCaptureFgsLifecycle() = runBlocking {
        val scenario = ActivityScenario.launch(ConsumerActivity::class.java)
        try {
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            val activityRef = AtomicReference<ConsumerActivity?>(null)
            scenario.onActivity { activity ->
                activityRef.set(activity)
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadre(activity.consumerView, scopeCapturingFactory(captured)),
                    ),
                )
            }
            val scope = ConsumerTestSupport.awaitKadreScope(captured)
            awaitPrimarySurface(captured)
            val activity = ConsumerTestSupport.notNull(activityRef)

            // Le manifest mergé déclare LE service de la bibliothèque (le host n'a rien
            // déclaré) — le fait de responsabilité du ruling P8-0.
            val info = activity.packageManager.getServiceInfo(
                ComponentName(activity, CAPTURE_SERVICE_CLASS),
                0,
            )
            assertTrue(info.isEnabled, "le service capture de la bibliothèque est dans le manifest mergé")

            // La notification du FGS n'existe QUE si POST_NOTIFICATIONS est accordée
            // (API 33+ : sans elle le service tourne, la notification est silencieuse — la
            // conséquence documentée du ruling P8-0). L'identité shell de l'UiAutomation
            // l'accorde pour CE scénario, qui épingle la notification documentée.
            val packageName = activity.packageName
            shellCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")

            // Consentement réel (dialogue), puis session vivante. LE START DU FLUX VIT
            // DANS collectFrames (le runtime) — c'est LÀ que le FGS démarre, et qu'un
            // démarrage refusé (`Temporary(retryable=true)`, l'app projetée étant au
            // premier plan) se voit. La row CONTRACTS dit RETRYABLE : un NOUVEAU
            // consentement réel (le grant du premier open fut consommé par sa réservation),
            // une NOUVELLE session, UNE fois.
            requestPermissionWithDialog(scope, allow = true)
            var capture = assertIs<KadreResult.Success<CaptureSession>>(
                scope.capture.open(CaptureRequest(target = CaptureTarget.HostChoice)),
            ).value
            var recorder = FrameRecorder(capture).also { it.start() }
            var attempts = 0
            var streamed = false
            while (!streamed) {
                if (capture.state.value is CaptureSessionState.Streaming) {
                    streamed = true
                    break
                }
                if (recorder.result.isCompleted) {
                    val failure = (recorder.result.getCompleted() as? KadreResult.Failure)?.reason
                    if (failure !is KadreFailure.TemporarilyUnavailable || ++attempts > 2) {
                        error("the capture stream never started: $failure")
                    }
                    recorder.close()
                    capture.close()
                    requestPermissionWithDialog(scope, allow = true)
                    capture = assertIs<KadreResult.Success<CaptureSession>>(
                        scope.capture.open(CaptureRequest(target = CaptureTarget.HostChoice)),
                    ).value
                    recorder = FrameRecorder(capture).also { it.start() }
                }
                Thread.sleep(POLL_INTERVAL_MILLIS)
            }

            // LE FAIT DU CONTRAT : le service tourne PENDANT la session — `Streaming` est la
            // preuve que la pompe a démarré, et l'ordre P8-0 (startForeground AVANT la
            // première frame) garantit le FGS UP À CE MOMENT : la vérification est
            // immédiate, elle gagne la course à n'importe quel terminal tardif.
            assertTrue(
                captureServiceRunning(activity),
                "le FGS mediaProjection tourne pendant la session (l'ordre P8-0 : startForeground précède la première frame)",
            )
            awaitUntil("the capture notification to be posted") { captureNotificationPosted(activity) }
            // Le terminal : le service est ARRÊTÉ (la sentinelle
            // `android-capture-fgs-stopped-after-terminal` garde ce point — une mutation
            // qui saute le stop du terminal laisse le FGS sous les scénarios suivants).
            capture.requestStop()
            awaitUntil("the session to terminate") {
                capture.state.value is CaptureSessionState.Terminated
            }
            recorder.close()
            awaitUntil("the capture FGS to be stopped after the terminal") { !captureServiceRunning(activity) }
            awaitUntil("the capture notification to be withdrawn") { !captureNotificationPosted(activity) }
            ConsumerTestSupport.onMain { ConsumerTestSupport.notNull(session).close() }
        } finally {
            // Le flux consenti peut amener l'app projetée DE L'AVANT — l'activité du test
            // passe STOPPED et le close d'ActivityScenario perd alors son état (l'NPE
            // « Current state was null » du runner androidx) : le close du scénario est
            // RÉSILIENT ici, les ressources CONTRACTUELLES (la session capture, le recueil)
            // étant fermées explicitement par le corps.
            runCatching { scenario.close() }
        }
    }
    // -- (5) android-capture-late-attach-consent ---------------------------------------------------------

    /**
     * L'activité est RESUMED AVANT l'attach ; le consentement via le registre DIRECT
     * (sans LifecycleOwner) fonctionne sur le registre androidx RÉEL — l'anti-pattern
     * kadre-old est corrigé sur le device, pas seulement en test hôte.
     */
    @Test
    fun androidCaptureLateAttachConsent() = runBlocking {
        // Lancée à la main : l'attach n'arrive qu'APRÈS le RESUMED explicite.
        val activity = ConsumerTestSupport.launchConsumerActivity()
        ConsumerTestSupport.awaitResumed(activity)
        val captured = AtomicReference<KadreScope?>(null)
        val session = AtomicReference<KadreSession?>(null)
        ConsumerTestSupport.onMain {
            session.set(
                ConsumerTestSupport.success(
                    activity.attachKadre(activity.consumerView, scopeCapturingFactory(captured)),
                ),
            )
        }
        val scope = ConsumerTestSupport.awaitKadreScope(captured)
        try {
            awaitPrimarySurface(captured)
            val state = requestPermissionWithDialog(scope, allow = true)
            assertEquals(
                CapturePermissionStateGranted(),
                state.permissions,
                "l'attach tardif (RESUMED) consent par le registre direct — sur le registre androidx réel",
            )
        } finally {
            session.get()?.let { current -> ConsumerTestSupport.onMain { current.close() } }
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

    /** L'état `Streaming` de la session capture — publié par le looper main, attendu par polling. */
    private suspend fun awaitStreaming(session: CaptureSession): CaptureSessionState.Streaming {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        var last = session.state.value
        while (System.currentTimeMillis() < deadline) {
            (session.state.value as? CaptureSessionState.Streaming)?.let { return it }
            last = session.state.value
            kotlinx.coroutines.delay(POLL_INTERVAL_MILLIS)
        }
        error("the capture session never streamed within ${DEFAULT_TIMEOUT_MILLIS}ms; last=$last")
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

    private fun CapturePermissionStateGranted(): org.graphiks.kadre.capture.CapturePermissionState =
        org.graphiks.kadre.capture.CapturePermissionState(
            PermissionState.Granted,
            PermissionState.Granted,
        )

    private fun CapturePermissionStateDenied(): org.graphiks.kadre.capture.CapturePermissionState =
        org.graphiks.kadre.capture.CapturePermissionState(
            PermissionState.Denied(canRequestAgain = true),
            PermissionState.Denied(canRequestAgain = true),
        )

    // -- le consentement automatisé : dump + tap, l'identité shell ---------------------------------------

    /**
     * La demande de consentement AVEC le dialogue système réel automatisé : la demande part
     * sur le scope main (le `launch` du launcher est un appel d'activité), l'UiAutomation
     * presse le bouton voulu, le résultat est attendu borné. Le refus utilisateur est un
     * SUCCÈS stateful (jamais une failure) — la valeur rendue est l'état publié.
     */
    private fun requestPermissionWithDialog(scope: KadreScope, allow: Boolean): CaptureManagerState {
        fun attempt(): CaptureManagerState {
            val result = CompletableDeferred<KadreResult<CaptureManagerState>>()
            val requestJob = ConsumerTestSupport.independentScope().launch {
                result.complete(scope.capture.requestPermission(CapturePermissionScope.Screen))
            }
            try {
                val matched = awaitConsentDialogAndTap(result, allow)
                val completed = runBlocking {
                    withTimeout(RESULT_TIMEOUT_MILLIS) { result.await() }
                }
                val state = assertIs<KadreResult.Success<CaptureManagerState>>(
                    completed,
                    "le refus utilisateur est un succès stateful (jamais une failure) — bouton pressé : $matched",
                ).value
                return state
            } finally {
                requestJob.cancel()
            }
        }

        val state = attempt()
        if (allow && state.permissions.screen is PermissionState.Denied) {
            // Le clic a pu rater SOUS CHARGE (le tap frappe un nœud déplacé) : la demande
            // suivante est légale après un refus (canRequestAgain=true) — UNE tentative de
            // plus. Une vraie difficulté d'automation échouera sur l'assert Granted du
            // scénario, jamais masquée.
            return attempt()
        }
        return state
    }

    /**
     * Attend la dialog de consentement et presse le bouton voulu.
     *
     * LA VOIE PRIMAIRE est l'accessibilité : l'[UiAutomation] de l'instrumentation lit les
     * fenêtres INTERACTIVES (le drapeau `FLAG_RETRIEVE_INTERACTIVE_WINDOWS` — la dialog de
     * systemui est une fenêtre d'un autre process) et pose `ACTION_CLICK` sur le nœud du
     * bouton — LES NŒUDS CLIQUABLES D'ABORD (le TITRE de la dialog API 35, « Start recording
     * with … », contient « start » : cliquer un titre ne fait rien). Le PROGRÈS est vérifié
     * après chaque clic — le résultat arrive ou le bouton disparaît ; sinon la voie de recul
     * presse le MÊME bouton par `input tap` aux bounds ÉCRAN du nœud (l'identité shell est
     * la seule qui injecte un toucher DANS la dialog de systemui). Les libellés essayés et
     * la hiérarchie courante voyagent dans le diagnostic d'échec.
     */
    private fun awaitConsentDialogAndTap(
        result: CompletableDeferred<KadreResult<CaptureManagerState>>,
        allow: Boolean,
    ): String {
        val deadline = System.currentTimeMillis() + DIALOG_TIMEOUT_MILLIS
        val attempted = mutableListOf<String>()
        while (System.currentTimeMillis() < deadline) {
            val candidates = consentButtonNodes(allow)
            for (candidate in candidates) {
                val label = labelOf(candidate.node)
                val at = "allow=$allow '" + label.take(60) + "' clickable=" + candidate.node.isClickable
                attempted += at
                // La LIGNE du sélecteur expose son libellé sur un TextView non cliquable —
                // le clic porte sur l'ANCÊTRE cliquable (la rangée), sinon il ne fait rien.
                val target = clickableAncestorOf(candidate.node)
                target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                if (awaitConsentProgress(result, label) && result.isCompleted) {
                    return "pressed $at"
                }
                // Le bouton a disparu SANS résultat : le flux a avancé à l'ÉCRAN SUIVANT
                // (l'API 35 enchaîne le sélecteur d'app) — la boucle continue.
                if (result.isCompleted) return "pressed $at"
                // Le clic n'a rien fait (un titre, un nœud décoratif) : le recul presse le
                // MÊME bouton par injection shell à ses bounds écran.
                val bounds = android.graphics.Rect().also(candidate.node::getBoundsInScreen)
                if (!bounds.isEmpty) {
                    shellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}")
                    attempted += "shell tap (${bounds.centerX()},${bounds.centerY()})"
                    if (result.isCompleted) return "pressed $at + shell tap"
                }
            }
            // La demande a pu échouer AVANT tout dialogue : l'échec réel prime l'attente.
            if (result.isCompleted) {
                val completed = runBlocking { result.await() }
                error(
                    "the consent request completed without a clickable dialog button (allow=$allow): " +
                        "$completed; attempted: $attempted",
                )
            }
            Thread.sleep(DIALOG_POLL_MILLIS)
        }
        val hierarchy = StringBuilder()
        for (window in interactiveWindows()) {
            window.root?.let { root -> describeTree(root, hierarchy) }
        }
        error(
            "the consent dialog button (allow=$allow) was never pressed within " +
                "${DIALOG_TIMEOUT_MILLIS}ms; attempted: $attempted; hierarchy: " +
                hierarchy.takeLast(DUMP_DIAGNOSTIC_CHARS),
        )
    }

    /**
     * Le progrès du clic : le résultat arrive (le bouton était le bon) ou le nœud disparaît
     * (la dialog a fermé) — borné court, jamais une attente de plus.
     */
    private fun awaitConsentProgress(
        result: CompletableDeferred<KadreResult<CaptureManagerState>>,
        label: String,
    ): Boolean {
        val deadline = System.currentTimeMillis() + CLICK_SETTLE_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (result.isCompleted) return true
            // Le nœud local d'un bouton disparu reste « vivant » : la fenêtre est
            // RECHERCHEE à chaque tour, pas le nœud relu.
            if (consentButtonNodes { candidate -> candidate == label }.isEmpty()) return true
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        return result.isCompleted
    }

    private class ConsentCandidate(
        val node: android.view.accessibility.AccessibilityNodeInfo,
    )

    /** Le premier nœud dont le libellé satisfait le prédicat, toutes fenêtres interactives. */
    private fun findByLabel(predicate: (String) -> Boolean): android.view.accessibility.AccessibilityNodeInfo? {
        for (window in interactiveWindows()) {
            window.root?.let { root ->
                findByLabelInTree(root, predicate)?.let { return it }
            }
        }
        return null
    }

    private fun findByLabelInTree(
        node: android.view.accessibility.AccessibilityNodeInfo,
        predicate: (String) -> Boolean,
    ): android.view.accessibility.AccessibilityNodeInfo? {
        val label = labelOf(node)
        if (label.isNotBlank() && predicate(label)) return node
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child -> findByLabelInTree(child, predicate)?.let { return it } }
        }
        return null
    }

    /**
     * Les nœuds candidats du bouton voulu, LES CLIQUABLES D'ABORD (le titre d'une dialog
     * peut matcher le libellé sans jamais répondre à un clic).
     */
    private fun consentButtonNodes(allow: Boolean): List<ConsentCandidate> {
        val matches = when (allow) {
            true -> ::matchesAllow
            false -> ::matchesDeny
        }
        return consentButtonNodes { label -> matches(label) }
    }

    private fun consentButtonNodes(matches: (String) -> Boolean): List<ConsentCandidate> {
        val clickable = mutableListOf<ConsentCandidate>()
        for (window in interactiveWindows()) {
            window.root?.let { root ->
                collectButtonInTree(root, matches, clickableOnly = true, found = clickable)
            }
        }
        if (clickable.isNotEmpty()) return clickable
        val any = mutableListOf<ConsentCandidate>()
        for (window in interactiveWindows()) {
            window.root?.let { root ->
                collectButtonInTree(root, matches, clickableOnly = false, found = any)
            }
        }
        return any
    }

    private fun collectButtonInTree(
        node: android.view.accessibility.AccessibilityNodeInfo,
        matches: (String) -> Boolean,
        clickableOnly: Boolean,
        found: MutableList<ConsentCandidate>,
    ) {
        val label = labelOf(node)
        if (label.isNotBlank() && matches(label)) {
            if (node.isClickable || (!clickableOnly && node.isEnabled)) {
                found += ConsentCandidate(node)
            }
        }
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child -> collectButtonInTree(child, matches, clickableOnly, found) }
        }
    }

    /** Les fenêtres interactives courantes — vide si le canal d'accessibilité est indisponible. */
    private fun interactiveWindows(): List<android.view.accessibility.AccessibilityWindowInfo> {
        enableInteractiveWindows()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        return try {
            automation.windows
        } catch (ignored: IllegalStateException) {
            emptyList()
        }
    }

    /**
     * Le drapeau `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`, posé une fois : sans lui,
     * `UiAutomation.windows` ne rend pas les fenêtres des AUTRES process (la dialog de
     * systemui en est une).
     */
    private fun enableInteractiveWindows() {
        if (interactiveWindowsEnabled) return
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = android.accessibilityservice.AccessibilityServiceInfo().apply {
            eventTypes = android.view.accessibility.AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        automation.serviceInfo = info
        interactiveWindowsEnabled = true
    }

    /** Le libellé plat d'un nœud — texte et content-desc. */
    private fun labelOf(node: android.view.accessibility.AccessibilityNodeInfo): String =
        (node.text?.toString().orEmpty() + " " + node.contentDescription?.toString().orEmpty()).trim()

    /** Le nœud cliquable le plus proche (le nœud lui-même ou son ancêtre) — borné à 6 niveaux. */
    private fun clickableAncestorOf(
        node: android.view.accessibility.AccessibilityNodeInfo,
    ): android.view.accessibility.AccessibilityNodeInfo {
        var current: android.view.accessibility.AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 6) {
            if (current.isClickable) return current
            current = current.parent
            depth += 1
        }
        return node
    }

    /** La hiérarchie plate des libellés — le diagnostic d'échec, jamais une assertion. */
    private fun describeTree(
        node: android.view.accessibility.AccessibilityNodeInfo,
        into: StringBuilder,
    ) {
        val label = labelOf(node)
        if (label.isNotBlank()) into.append("['").append(label.take(60)).append("'] ")
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child -> describeTree(child, into) }
        }
    }

    /**
     * LES boutons ACCORDER du flux API 35 — TROIS nœuds, pas tous sur le même écran : le
     * bouton « Start » de la dialog (android:id/button1, le libellé API 35 — « Start now »
     * sur les API plus anciennes), l'option « Entire screen » du mode spinner là où elle
     * existe, et — le mode livré par défaut sur l'émulateur étant « A single app » — LA
     * LIGNE DE CETTE APP dans le `MediaProjectionAppSelectorActivity` (« Select app »), le
     * dernier écran du flux consenti. JAMAIS un libellé qui contient « Don't allow » ni
     * « Cancel » (l'anti-motif est exclu AVANT le motif positif) ; le TITRE de la dialog
     * (« Start recording or casting with kadre-android-consumer? ») matche le motif positif
     * mais n'est pas cliquable — les candidats CLIQUABLES passent d'abord, un clic sur un
     * titre ne fait rien et la boucle passe au suivant.
     */
    private fun matchesAllow(label: String): Boolean {
        val lower = label.lowercase()
        if (lower.contains("don't") || lower.contains("dont") || lower.contains("not allow") || lower.contains("cancel")) {
            return false
        }
        // L'app projetée : la caméra (SELECTED_APP_LABEL) — sa tâche est indépendante et son
        // viewfinder se redessine en continu. JAMAIS la ligne de CETTE app : se projeter
        // soi-même relance SA propre activité DANS la tâche du sélecteur, ce qui annule le
        // flux (RESULT_CANCELED) au lieu de le commettre.
        if (lower == SELECTED_APP_LABEL.lowercase()) return true
        return lower.contains("start") || lower.contains("entire screen") || lower.contains("allow")
    }

    /** Le bouton REFUSER : « Don't allow » (ou « Cancel » — le back-port des dialogues anciens). */
    private fun matchesDeny(label: String): Boolean {
        val lower = label.lowercase()
        return lower.contains("don't allow") || lower.contains("dont allow") ||
            lower.contains("not allow") || lower.contains("deny") || lower == "cancel"
    }

    /**
     * La commande shell du device — l'[UiAutomation] parle avec l'identité shell. Le
     * drainage garde un PLAFOND BORNÉ (le descripteur peut rester ouvert sans jamais EOF
     * sur ce câblage émulateur — le miroir exact du drainage des scénarios mémoire P7).
     */
    private fun shellCommand(command: String): String {
        val descriptor =
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("$command 2>&1")
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

    // -- l'observation du FGS ----------------------------------------------------------------------------

    /**
     * Le service capture tourne — la lecture `dumpsys activity services` par l'identité
     * shell : l'API dépréciée `getRunningServices` ne rend plus la vérité sur toutes les
     * images (le vidage system_server, lui, la rend toujours).
     */
    private fun captureServiceRunning(activity: ConsumerActivity): Boolean {
        // DEUX lectures honnêtes, l'une OU l'autre : `getRunningServices` (le propre
        // process — dépréciée mais fonctionnelle pour SOI) et le vidage `dumpsys activity
        // services` de l'identité shell. Le service existe = l'une des deux le nomme.
        @Suppress("DEPRECATION")
        val services = (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Int.MAX_VALUE)
        if (services.any { it.service.className == CAPTURE_SERVICE_CLASS }) return true
        return shellCommand("dumpsys activity services ${activity.packageName}").contains(CAPTURE_SERVICE_CLASS)
    }

    /** La notification minimale de la bibliothèque est postée (le titre documenté du FGS). */
    private fun captureNotificationPosted(activity: ConsumerActivity): Boolean {
        val manager = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.any { notification ->
            notification.notification.extras.getString(android.app.Notification.EXTRA_TITLE) == CAPTURE_NOTIFICATION_TITLE
        }
    }

    /** L'attente bornée d'un fait device — le message cite ce qui était attendu. */
    private fun awaitUntil(expectation: String, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error("awaited $expectation within ${DEFAULT_TIMEOUT_MILLIS}ms and it never held")
    }

    // -- le recueil de frames ----------------------------------------------------------------------------

    /** Les frames attendues — borné ; chaque Record est une copie (la lease est fermée au retour). */
    private fun awaitFrames(recorder: FrameRecorder, minimum: Int) {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (recorder.snapshot().size >= minimum) return
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error("only ${recorder.snapshot().size} frames arrived within ${DEFAULT_TIMEOUT_MILLIS}ms (wanted $minimum)")
    }

    /** La fenêtre bornée du silence : aucune frame ne suit le terminal. */
    private fun awaitFrameSilence(recorder: FrameRecorder, baseline: Int) {
        val deadline = System.currentTimeMillis() + SILENCE_WINDOW_MILLIS
        while (System.currentTimeMillis() < deadline) {
            assertTrue(
                recorder.snapshot().size == baseline,
                "a frame leaked after the terminal: ${recorder.snapshot().size} > $baseline",
            )
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
    }

    /** Le contenu réel du SurfaceView : un canvas rouge posté dans le holder dès qu'il existe. */
    private fun drawRedWhenSurfaceReady(surfaceView: SurfaceView) {
        val draw = {
            val canvas: Canvas? = surfaceView.holder.lockCanvas()
            if (canvas != null) {
                canvas.drawColor(Color.rgb(180, 30, 40))
                surfaceView.holder.unlockCanvasAndPost(canvas)
            }
        }
        surfaceView.holder.addCallback(
            object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) = draw()

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = draw()

                override fun surfaceDestroyed(holder: SurfaceHolder) {}
            },
        )
    }

    /**
     * Le recueil de frames du test : souscrit sur un DISPATCHER DÉDIÉ (le polling du test
     * reste sur le thread d'instrumentation), chaque frame est COPIÉE dans le collector
     * (la lease est fermée au retour du collector — le contrat §11.1). Le scope est CELUI
     * DU RECUEIL, annulé par le scénario ([close]) — le miroir exact du SignalRecorder P7.
     */
    private class FrameRecorder(private val session: CaptureSession) {
        data class Record(
            val size: PhysicalSize,
            val revision: Long,
            val bytes: ByteArray?,
        )

        private val ownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val collected = ConcurrentLinkedQueue<Record>()

        /** Le verdict du collecteur — le start du flux vit DANS collectFrames (runtime). */
        val result = CompletableDeferred<KadreResult<Unit>>()
        private var job: Job? = null

        fun start() {
            job = ownScope.launch {
                result.complete(
                    session.collectFrames { frame ->
                        collected += Record(
                            size = frame.size,
                            revision = frame.configurationRevision.value,
                            bytes = frame.copyPlanes().firstOrNull()?.bytes,
                        )
                    },
                )
            }
        }

        fun snapshot(): List<Record> = collected.toList()

        /** Termine la fenêtre d'observation : le collecteur est annulé, le recueil figé. */
        fun close() {
            job?.cancel()
            ownScope.cancel()
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
        const val POLL_INTERVAL_MILLIS = 25L

        /** Le drapeau d'accessibilité interactive, posé une fois par process de test. */
        @Volatile
        var interactiveWindowsEnabled = false

        /** La fenêtre bornée du silence : un terminal, aucune frame — court. */
        const val SILENCE_WINDOW_MILLIS = 1_500L

        /** La fenêtre d'apparition du dialogue système — le dump prend 1-2 s par tour. */
        const val DIALOG_TIMEOUT_MILLIS = 30_000L
        const val DIALOG_POLL_MILLIS = 500L

        /** L'attente du résultat après le tap — le deliver du registre est rapide. */
        const val RESULT_TIMEOUT_MILLIS = 15_000L

        /** Le retour au premier plan après un consent accordé — borné court. */
        const val RESUME_TIMEOUT_MILLIS = 10_000L


        /** Le settle d'un clic : le résultat arrive ou le bouton disparaît — court. */
        const val CLICK_SETTLE_MILLIS = 4_000L

        /** Le plafond du drainage d'une commande shell (le miroir P7). */
        const val SHELL_DRAIN_TIMEOUT_MILLIS = 5_000L
        const val SHELL_DRAIN_POLL_MILLIS = 20L

        /** Le tronçon de dump retenu pour le diagnostic d'échec du dialogue. */
        const val DUMP_DIAGNOSTIC_CHARS = 2_000

        /** Le service FGS de la bibliothèque — le composant mergé du ruling P8-0. */
        const val CAPTURE_SERVICE_CLASS = "org.graphiks.kadre.platform.android.AndroidCaptureService"

        /** Le titre de la notification minimale de la bibliothèque. */
        const val CAPTURE_NOTIFICATION_TITLE = "Screen capture"

        /**
         * L'app projetée du sélecteur — la caméra de l'émulateur : sa tâche est indépendante
         * de l'app qui demande (projeter SA PROPRE tâche échoue la session de l'API 35 côté
         * system_server — « failing to set ContentRecordingSession, id=-1 »), et son
         * viewfinder se redessine en continu — les frames du flux coulent sans stimuli.
         */
        const val SELECTED_APP_LABEL = "Camera"


    }
}
