package org.graphiks.kadre.consumer

import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.PointerIcon
import android.view.WindowInsets
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
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
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.InteractionFailureReason
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.interaction.InteractionAction
import org.graphiks.kadre.interaction.InteractionActionOutcome
import org.graphiks.kadre.interaction.InteractionContext
import org.graphiks.kadre.interaction.InteractionEvent
import org.graphiks.kadre.interaction.InteractionHandler
import org.graphiks.kadre.interaction.InteractionKind
import org.graphiks.kadre.interaction.InteractionRequestId
import org.graphiks.kadre.window.FullscreenMode
import org.graphiks.kadre.window.Window
import org.graphiks.kadre.window.WindowDecorations
import org.graphiks.kadre.window.WindowEvent
import org.graphiks.kadre.window.WindowLevel
import org.graphiks.kadre.window.WindowPhase
import org.graphiks.kadre.window.WindowProperty
import org.graphiks.kadre.window.WindowUpdate
import org.graphiks.kadre.window.WindowUpdateOutcome
import org.graphiks.kadre.platform.android.attachKadre
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.SurfaceProperty
import org.graphiks.kadre.surface.SurfaceUpdate
import org.graphiks.kadre.surface.SurfaceUpdateOutcome
import org.junit.runner.RunWith

/**
 * Les cinq scénarios instrumentés du contrat AND-005 (commandes de fenêtre Activity et
 * interactions transitoires) sur l'API publique `attachKadre` résolue depuis l'umbrella PUBLIÉ.
 * Chaque nom de méthode est un `testName` contractuel du mapping AND-005 — la correspondance :
 *
 * | scenario id (registre)                    | méthode                          | sentinelle gardée                        |
 * |-------------------------------------------|----------------------------------|------------------------------------------|
 * | android-window-commits-with-readback      | androidWindowCommitsWithReadback | -                                        |
 * | android-window-refused-fields-inert       | androidWindowRefusedFieldsInert  | android-window-native-gate               |
 * | android-window-back-host-owned            | androidWindowBackHostOwned       | -                                        |
 * | android-surface-cursor-and-capture        | androidSurfaceCursorAndCapture   | android-capture-listener-confirmation    |
 * | android-interaction-token-authority       | androidInteractionTokenAuthority | android-interaction-unadvertised-refused |
 *
 * TOUTES les formes sont Activity (les commandes de fenêtre le sont — la forme View n'a ni
 * manager ni fenêtre ; la preuve des capacités DISTINCTES est portée par la jambe View de
 * [androidWindowCommitsWithReadback] : `primary == null` et entrée Unsupported).
 *
 * L'injection d'entrée vit à la frontière RÉELLE — `activity.window.callback.dispatchKeyEvent`
 * — la fenêtre de l'Activity, dont le callback EST le wrapper Kadre enchaîné (le miroir exact
 * du contrat AND-004).
 *
 * Les scénarios sont autonomes (ActivityScenario `use{}`, `startActivitySync` own-task pour
 * les Activities secondes — deux ActivityScenario simultanés se détruisent mutuellement) et
 * ferment leurs sessions explicitement. L'observation est un polling borné sur le thread
 * d'instrumentation ; les injections, les lectures SDK et les fermetures de session passent
 * par le thread main.
 */
@RunWith(AndroidJUnit4::class)
class KadreConsumerWindowInteractionContractTest {

    /**
     * android-window-commits-with-readback — les trois champs supportés de la fenêtre primaire
     * Activity COMMITTENT par le seam natif sur l'Activity réelle, et chaque commit est lu en
     * RETOUR sur la plateforme :
     *
     * - `title` → `Activity.setTitle` ; l'état de la fenêtre porte le titre lu (`getTitle`),
     *   l'Activity porte le même ;
     * - `contentProtection` → `FLAG_SECURE` ajouté puis retiré ; le readback des layout flags
     *   de la fenêtre confirme les DEUX directions ;
     * - `fullscreen` `Borderless` → les barres système se cachent (le readback des insets est
     *   la vérité publiée : tant qu'elles se lisent visibles, la réponse reste
     *   `PartiallyApplied(TemporarilyUnavailable(retryable = true))` et rien ne se commite —
     *   le scénario rejoue la demande jusqu'au commit effectif, le mot on-row de la tâche 1) ;
     *   le retour `Windowed` n'est PAS asserté — sur l'émulateur headless de la preuve,
     *   `InsetsController.show` ne reverse jamais l'état visible dans les insets dispatchés,
     *   la fenêtre y répond `Temporary` toute la durée du test (nettoyage SDK best-effort) ;
     * - chaque commit publie UN `PropertiesChanged` portant les champs qui ont réellement
     *   bougé, corrélé à l'état publié.
     *
     * La jambe « capacités distinctes » : la forme View attachée sur une seconde Activity
     * n'a NI manager (`primary == null`) NI entrée câblée (capabilities clavier/pointeur/
     * tactile Unsupported) — les commandes de fenêtre sont de la forme Activity seule.
     */
    @Test
    fun androidWindowCommitsWithReadback() = runBlocking {
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
            val scope = ConsumerTestSupport.notNull(captured)
            awaitPrimarySurface(captured)
            val window = awaitPrimaryWindow(scope)
            val windowEvents = WindowEventRecorder(window.events, this)
            settle()

            // --- La jambe View : capacités distinctes, pas de manager, pas d'entrée. ---
            val activityB = launchOwnTaskConsumerActivity()
            try {
                ConsumerTestSupport.awaitResumed(activityB)
                val capturedB = AtomicReference<KadreScope?>(null)
                val sessionB = AtomicReference<KadreSession?>(null)
                ConsumerTestSupport.onMain {
                    sessionB.set(
                        ConsumerTestSupport.success(
                            activityB.consumerView.attachKadre(
                                lifecycleOwner = activityB,
                                applicationFactory = scopeCapturingFactory(capturedB),
                            ),
                        ),
                    )
                }
                val surfaceB = awaitPrimarySurface(capturedB)
                assertEquals(
                    null,
                    ConsumerTestSupport.notNull(capturedB).windows.state.value.primary,
                    "the View form has no window manager primary",
                )
                val inputCaps = surfaceB.input.state.value.capabilities
                assertEquals(
                    FeatureAvailability.Unsupported,
                    inputCaps.keyboard,
                    "the View form has no wired keyboard observation",
                )
                assertEquals(
                    FeatureAvailability.Unsupported,
                    inputCaps.pointer,
                    "the View form has no wired pointer observation",
                )
                assertEquals(
                    FeatureAvailability.Unsupported,
                    inputCaps.touch,
                    "the View form has no wired touch observation",
                )
                closeOnMain(sessionB)
            } finally {
                ConsumerTestSupport.onMain { activityB.finish() }
            }

            // --- title : commit + readback des deux côtés du seam. ---
            val titled = assertIs<KadreResult.Success<WindowUpdateOutcome>>(
                window.apply(WindowUpdate(title = PropertyChange.Set("Kadre Window"))),
            )
            assertIs<WindowUpdateOutcome.Applied>(titled.value)
            assertEquals("Kadre Window", window.state.value.title, "the committed title is the readback")
            assertEquals(
                "Kadre Window",
                titleOnMain(ConsumerTestSupport.notNull(activity)),
                "the Activity title moved with the commit",
            )

            // --- contentProtection : FLAG_SECURE ajouté puis retiré, lu en retour. ---
            val shielded = assertIs<KadreResult.Success<WindowUpdateOutcome>>(
                window.apply(WindowUpdate(contentProtection = PropertyChange.Set(true))),
            )
            assertIs<WindowUpdateOutcome.Applied>(shielded.value)
            assertTrue(window.state.value.contentProtection, "the committed protection is the readback")
            assertTrue(
                secureOnMain(ConsumerTestSupport.notNull(activity)),
                "FLAG_SECURE is really set on the Activity window",
            )
            val unshielded = assertIs<KadreResult.Success<WindowUpdateOutcome>>(
                window.apply(WindowUpdate(contentProtection = PropertyChange.Set(false))),
            )
            assertIs<WindowUpdateOutcome.Applied>(unshielded.value)
            assertFalse(window.state.value.contentProtection, "the cleared protection is the readback")
            assertFalse(
                secureOnMain(ConsumerTestSupport.notNull(activity)),
                "FLAG_SECURE is really cleared on the Activity window",
            )

            // --- fullscreen Borderless : le readback décide — des barres encore visibles
            //     ne sont pas un commit ; la demande est rejouée jusqu'au commit effectif.
            //     (Sur cet émulateur headless, `InsetsController.show` ne reverse JAMAIS l'état
            //     visible des barres dans les insets dispatchés — le fenêtre y répond, toute la
            //     durée de la boucle, `PartiallyApplied(TemporarilyUnavailable(retryable=true))`
            //     : le mot on-row qui refuse de mentir sur un commit qui n'a pas eu lieu. Le
            //     retour Windowed n'est donc PAS asserté ici — la restauration est un nettoyage
            //     SDK best-effort, hors contrat.) ---
            val borderless = applyFullscreenUntilCommitted(
                ConsumerTestSupport.notNull(activity),
                window,
                FullscreenMode.Borderless,
            )
            assertEquals(FullscreenMode.Borderless, window.state.value.fullscreen)
            assertEquals(
                FullscreenMode.Borderless,
                borderless.state.fullscreen,
                "the outcome carries the committed state",
            )
            awaitSystemBars(ConsumerTestSupport.notNull(activity), visible = false)
            // Nettoyage best-effort : rendre les barres au host par le SDK public, sans
            // assertion — la réponse on-row de la fenêtre à cette demande d'environnement
            // est déjà couverte par la boucle ci-dessus.
            ConsumerTestSupport.onMain {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ConsumerTestSupport.notNull(activity).window.insetsController
                        ?.show(WindowInsets.Type.systemBars())
                }
            }

            // --- Les événements corrélés : un PropertiesChanged par commit, les champs
            //     qui ont réellement bougé. ---
            val events = awaitWindowEvents(windowEvents, "the commits' PropertiesChanged events") { published ->
                val properties = published.filterIsInstance<WindowEvent.PropertiesChanged>().flatMap { it.changed }
                WindowProperty.Title in properties &&
                    WindowProperty.ContentProtection in properties &&
                    WindowProperty.Fullscreen in properties
            }
            val propertyEvents = events.filterIsInstance<WindowEvent.PropertiesChanged>()
            assertTrue(
                propertyEvents.any { WindowProperty.Title in it.changed },
                "the title commit published its PropertiesChanged",
            )
            assertTrue(
                propertyEvents.any { WindowProperty.ContentProtection in it.changed },
                "the contentProtection commits published their PropertiesChanged",
            )
            assertTrue(
                propertyEvents.any { WindowProperty.Fullscreen in it.changed },
                "the fullscreen commits published their PropertiesChanged",
            )
            assertTrue(
                events.none { it is WindowEvent.CloseRequested },
                "no close request was ever published by the window",
            )
            closeOnMain(session)
        }
    }

    /**
     * android-window-refused-fields-inert — un update portant trois champs SANS primitive
     * (`resizable`, `level`, `decorations`) répond `PartiallyApplied`, chaque champ rejeté
     * `Unsupported(UpdateWindow)` — et RIEN ne bouge côté natif : l'Activity (titre,
     * FLAG_SECURE, barres) est inchangée, l'état de la fenêtre est inchangé (révision
     * comprise), et AUCUN événement n'est publié (un update entièrement refusé n'est ni un
     * commit ni un mouvement d'état).
     *
     * Sentinelle gardée : `android-window-native-gate` (un champ refusé qui appellerait quand
     * même une primitive du seam déplacerait l'Activity ou la révision — lui seul, car aucun
     * autre scénario ne pose des champs refusés).
     */
    @Test
    fun androidWindowRefusedFieldsInert() = runBlocking {
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
            val window = awaitPrimaryWindow(ConsumerTestSupport.notNull(captured))
            val windowEvents = WindowEventRecorder(window.events, this)
            settle()

            val nativeBefore = snapshotOnMain(ConsumerTestSupport.notNull(activity))
            val stateBefore = window.state.value

            val refused = assertIs<KadreResult.Success<WindowUpdateOutcome>>(
                window.apply(
                    WindowUpdate(
                        resizable = PropertyChange.Set(true),
                        level = PropertyChange.Set(WindowLevel.Floating),
                        decorations = PropertyChange.Set(WindowDecorations.Borderless),
                    ),
                ),
            )
            val partial = assertIs<WindowUpdateOutcome.PartiallyApplied>(refused.value)
            assertEquals(
                setOf(WindowProperty.Resizable, WindowProperty.Level, WindowProperty.Decorations),
                partial.rejected.map { it.field }.toSet(),
                "exactly the three refused fields are reported",
            )
            assertTrue(
                partial.rejected.all { it.failure == KadreFailure.Unsupported(KadreOperation.UpdateWindow) },
                "each refusal is the on-row field-by-field Unsupported(UpdateWindow)",
            )

            // L'inertie native : l'Activity n'a pas bougé, l'état non plus, aucun événement.
            assertEquals(
                nativeBefore,
                snapshotOnMain(ConsumerTestSupport.notNull(activity)),
                "the Activity is untouched by the refused update",
            )
            assertEquals(
                stateBefore,
                window.state.value,
                "the window state (revision included) is untouched by the refused update",
            )
            delay(EVENT_SETTLE_MILLIS)
            assertTrue(
                windowEvents.snapshot().isEmpty(),
                "a fully refused update publishes no event, got ${windowEvents.snapshot()}",
            )
            closeOnMain(session)
        }
    }

    /**
     * android-window-back-host-owned — un Back injecté à la frontière fenêtre
     * (`KEYCODE_BACK` down/up par `window.callback`) atteint la gestion PROPRE du host :
     * l'Activity se termine (`onBackPressed` → finish), pendant que Kadre n'observe RIEN
     * (Back est hors table de mapping — aucun stimulus, aucune interaction) et ne publie
     * jamais de `CloseRequested` ; la session ne s'arrête que PAR la mort du host
     * (`Stopped(HostDetached)`), jamais par une décision Kadre. Puis, sur une Activity
     * vivante : `window.close()` répond `Temporary(retryable = false)` — la seule réponse
     * on-row d'une fenêtre host-owned — et `session.close()` s'arrête
     * (`Stopped(HostRequested)`) en LAISSANT l'Activity vivante (jamais de `finish()`).
     */
    @Test
    fun androidWindowBackHostOwned() = runBlocking {
        // --- Jambe 1 : le Back appartient au host. ---
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
            val window = awaitPrimaryWindow(ConsumerTestSupport.notNull(captured))
            val surface = awaitPrimarySurface(captured)
            val inputEvents = InputEventRecorder(surface.input.events, this)
            val windowEvents = WindowEventRecorder(window.events, this)
            settle()
            ConsumerTestSupport.awaitRunning(ConsumerTestSupport.notNull(session))

            // Le down démarre le tracking Android, le up déclenche onBackPressed → finish.
            dispatchKey(ConsumerTestSupport.notNull(activity), keyDown(KeyEvent.KEYCODE_BACK))
            dispatchKey(ConsumerTestSupport.notNull(activity), keyUp(KeyEvent.KEYCODE_BACK))
            awaitCondition("the host activity finished itself (onBackPressed)") {
                isFinishingOnMain(ConsumerTestSupport.notNull(activity))
            }

            // Kadre n'a rien vu, rien intercepté, rien demandé — l'Activity a répondu seule.
            assertTrue(
                inputEvents.snapshot().isEmpty(),
                "Back is outside the mapping table — no stimulus, got ${inputEvents.snapshot()}",
            )
            assertTrue(
                windowEvents.snapshot().none { it is WindowEvent.CloseRequested },
                "the window never asked Kadre about Back",
            )
            assertEquals(
                WindowPhase.Open,
                window.state.value.phase,
                "the window stays open — Kadre never closes the host's window",
            )

            // La session ne meurt que PAR le host : le binding terminal propose
            // `Stopped(HostDetached)` quand l'Activity finie se détruit.
            ConsumerTestSupport.awaitTerminal(
                ConsumerTestSupport.notNull(session),
                SessionOutcome.Stopped(SessionStopReason.HostDetached),
            )
        }

        // --- Jambe 2 : close et session.close laissent le host intact. ---
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
            val window = awaitPrimaryWindow(ConsumerTestSupport.notNull(captured))
            settle()

            val closed = window.close()
            val failure = assertIs<KadreResult.Failure>(closed, "close() is refused, never successful")
            val temporary = assertIs<KadreFailure.TemporarilyUnavailable>(failure.reason)
            assertFalse(temporary.retryable, "the host-owned window is permanently non-closable by Kadre")

            closeOnMain(session)
            ConsumerTestSupport.awaitTerminal(
                ConsumerTestSupport.notNull(session),
                SessionOutcome.Stopped(SessionStopReason.HostRequested),
            )
            assertFalse(
                isFinishingOnMain(ConsumerTestSupport.notNull(activity)),
                "session.close() never finishes the host activity",
            )
        }
    }

    /**
     * android-surface-cursor-and-capture — les deux champs de surface de la phase 4, committés
     * pour de vrai :
     *
     * - `cursor` : `System(Text)` pose l'icône système TYPE_TEXT sur la View du host — le
     *   readback EST la View (`view.pointerIcon` devient exactement l'icône que la table
     *   Text→TYPE_TEXT promet) — puis le retour `System(Default)` rend la flèche ;
     * - `pointerCapture` : le commit est EN DEUX TEMPS — la réponse à `apply(Confined)` porte
     *   un état dont la capture vaut ENCORE `None` (la tentative lancée n'est pas la
     *   confirmation), et c'est la notification RÉELLE du SDK
     *   (`Window.Callback.onPointerCaptureChanged`, enchaînée par le wrapper de cette
     *   Activity) qui publie `Confined` à une révision nouvelle ; `Locked` reste refusé champ
     *   par champ (D13 — zéro effet) ; `None` rend la main et l'état revient.
     *
     * Sentinelle gardée : `android-capture-listener-confirmation` (un commit immédiat au
     * retour de la requête SDK, sans le listener, ferait porter `Confined` par l'état de la
     * RÉPONSE — l'assertion des deux temps, lui seul car aucun autre scénario n'applique le
     * champ `pointerCapture`).
     */
    @Test
    fun androidSurfaceCursorAndCapture() = runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val activity = activityReference(scenario, prepareKeyFocus = true)
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
            settle()

            // --- Le curseur : l'icône effective de la View est le readback. ---
            val text = assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(SurfaceUpdate(cursor = PropertyChange.Set(CursorStyle.System(CursorIcon.Text)))),
            )
            assertIs<SurfaceUpdateOutcome.Applied>(text.value)
            assertEquals(
                CursorStyle.System(CursorIcon.Text),
                surface.state.value.cursor,
                "the committed cursor style is the requested one",
            )
            awaitPointerIcon(
                ConsumerTestSupport.notNull(activity),
                PointerIcon.TYPE_TEXT,
                "the Text commit reached the View's pointer icon",
            )
            val restored = assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(SurfaceUpdate(cursor = PropertyChange.Set(CursorStyle.System(CursorIcon.Default)))),
            )
            assertIs<SurfaceUpdateOutcome.Applied>(restored.value)
            awaitPointerIcon(
                ConsumerTestSupport.notNull(activity),
                PointerIcon.TYPE_DEFAULT,
                "the Default restore reached the View's pointer icon",
            )

            // --- La capture : la tentative n'est pas la confirmation — l'état porté par la
            //     RÉPONSE vaut encore None ; le listener du SDK confirme ensuite. ---
            val confined = assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined))),
            )
            val confinedOutcome = assertIs<SurfaceUpdateOutcome.Applied>(confined.value)
            assertEquals(
                PointerCaptureMode.None,
                confinedOutcome.state.pointerCapture,
                "the apply response is not the capture confirmation — the attempt was launched, not granted",
            )
            awaitSurfaceCapture(surface, PointerCaptureMode.Confined, "the real SDK listener confirmed the confinement")
            assertTrue(
                surface.state.value.revision.value > confinedOutcome.state.revision.value,
                "the confirmation published at its own, later revision",
            )

            // --- Locked : refus champ par champ, zéro effet. ---
            val locked = assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Locked))),
            )
            val lockedPartial = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(locked.value)
            assertEquals(
                listOf(SurfaceProperty.PointerCapture),
                lockedPartial.rejected.map { it.field },
                "Locked is refused field by field (D13)",
            )
            assertEquals(
                PointerCaptureMode.Confined,
                surface.state.value.pointerCapture,
                "the refused Locked changed nothing",
            )

            // --- None : la main rendue, l'état revient. ---
            val released = assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.None))),
            )
            val releasedOutcome = assertIs<SurfaceUpdateOutcome.Applied>(released.value)
            assertEquals(
                PointerCaptureMode.None,
                releasedOutcome.state.pointerCapture,
                "the synchronous release commits in the same pass",
            )
            assertEquals(
                PointerCaptureMode.None,
                surface.state.value.pointerCapture,
                "the published state is back to None",
            )
            closeOnMain(session)
        }
    }

    /**
     * android-interaction-token-authority — l'autorité d'interaction, de bout en bout sur la
     * frontière réelle :
     *
     * - la capability annonce EXACTEMENT l'ensemble construit ({EnterFullscreen,
     *   ExitFullscreen, LockPointer, UnlockPointer} — seams fenêtre + capture câblés, API 35 ≥
     *   26), `Available` ;
     * - un handler installé reçoit les triggers dérivés des touches injectées à la frontière
     *   fenêtre — `KeyPressed` AVANT le stimulus ordinaire du même down (l'ordre AppKit,
     *   observé ici : au moment du handler, la touche n'est pas encore dans l'état d'entrée
     *   publié, et son stamp d'horloge précède celui de la collecte du `KeyChanged`) ;
     * - le token est USUEL UNE FOIS : `OpenWindow` (non annoncée) est refusée
     *   `Unsupported(Interaction)` sans consommer, `LockPointer(Locked)` est admis (outcome
     *   `Committed`, puis la capture CONFIRMÉE par le listener publie `Confined`), et un
     *   second appel sur le MÊME token répond `InteractionRequired(Consumed)` ;
     * - une pression suivante (`b`) requiert `UnlockPointer` — admis, la capture est rendue et
     *   l'état revient à `None` par le même listener ;
     * - un second handler est refusé `AlreadyInUse(Interaction)`.
     *
     * Sentinelle gardée : `android-interaction-unadvertised-refused` (annoncer `OpenWindow`
     * ferait accepter la requête non annoncée — lui seul, car aucun autre scénario ne requiert
     * d'action d'interaction).
     */
    @OptIn(DelicateKadreApi::class)
    @Test
    fun androidInteractionTokenAuthority() = runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val activity = activityReference(scenario, prepareKeyFocus = true)
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
            settle()

            // L'annonce exacte — une décision de construction, épelée kind par kind.
            val advertised = surface.capabilities.value.handlerInteractions
            val supported = assertIs<Capability.Supported<Set<InteractionKind>>>(advertised)
            assertEquals(
                setOf(
                    InteractionKind.EnterFullscreen,
                    InteractionKind.ExitFullscreen,
                    InteractionKind.LockPointer,
                    InteractionKind.UnlockPointer,
                ),
                supported.constraints,
                "the advertised set is exactly the construction's promise",
            )
            assertEquals(FeatureAvailability.Available, supported.availability)

            // Le handler scénarisé : chaque KeyPressed déclenche des requêtes, chaque résultat
            // est ENREGISTRÉ (jamais asserté dans le callback — un lancement dans le handler
            // est un échec de session, pas une assertion).
            val clock = AtomicLong(0L)
            val handler = ScriptedInteractionHandler(clock) { surface.input.state.value.keyboard.pressedKeys.size }
            val installed = surface.installInteractionHandler(handler)
            val registration = assertIs<KadreResult.Success<org.graphiks.kadre.interaction.InteractionRegistration>>(installed).value
            val second = surface.installInteractionHandler(handler)
            val already = assertIs<KadreResult.Failure>(second)
            assertEquals(
                KadreFailure.AlreadyInUse(KadreResourceKind.Interaction),
                already.reason,
                "a second handler is refused — one per surface",
            )
            val inputEvents = InputEventRecorder(surface.input.events, this, clock)
            val outcomes = OutcomeRecorder(registration.outcomes, this)
            settle()

            // Pression « a » : le trigger KeyPressed part du dispatch RÉEL, AVANT le stimulus
            // ordinaire du même down.
            dispatchKey(ConsumerTestSupport.notNull(activity), keyDown(KeyEvent.KEYCODE_A))
            val firstPress = awaitInputEvents(inputEvents, "the ordinary KeyChanged(a press)") { stamped ->
                stamped.any { (it.second as? InputEvent.Key)?.keyState == KeyState.Pressed }
            }
            assertInteractionBeforeStimulus(handler, firstPress, index = 0, LogicalKey.Character("a"), "the 'a' press")

            // Le script de la pression « a » : refus non-annoncé, verrou admis, token usuel.
            val requests = handler.requestRecords()
            assertEquals(
                listOf("OpenWindow", "LockPointer", "UnlockPointer(consumed)"),
                requests.map { it.action },
                "the first trigger ran the whole script, in order",
            )
            assertFalse(requests[0].accepted, "the unadvertised OpenWindow is refused")
            assertEquals(
                KadreFailure.Unsupported(KadreOperation.Interaction),
                requests[0].failure,
                "the unadvertised refusal is Unsupported(Interaction)",
            )
            assertTrue(requests[1].accepted, "the advertised LockPointer is admitted")
            val lockRequestId = requireNotNull(requests[1].requestId) { "the admitted LockPointer returned no requestId" }
            assertFalse(requests[2].accepted, "the second request on the SAME token is refused")
            assertEquals(
                KadreFailure.InteractionRequired(InteractionFailureReason.Consumed),
                requests[2].failure,
                "the token was consumed once",
            )

            // L'outcome du verrou : Committed au requestId rendu ; les refus hors-token
            // (Unsupported avant allocation, Consumed) n'en publient aucun.
            val committed = awaitOutcomes(outcomes, "the LockPointer Committed outcome") { published ->
                published.any { it is InteractionActionOutcome.Committed && it.requestId == lockRequestId }
            }
            assertEquals(
                1,
                committed.size,
                "exactly one outcome was published — the refusals published none, got $committed",
            )
            assertIs<InteractionActionOutcome.Committed>(committed.single())

            // La confirmation du listener : la capture publiée passe à Confined.
            awaitSurfaceCapture(surface, PointerCaptureMode.Confined, "the LockPointer commit confirmed by the listener")

            // La relève de « a » (rien ne déclenche), puis pression « b » : le nouveau token
            // requiert UnlockPointer — admis, la capture est rendue.
            dispatchKey(ConsumerTestSupport.notNull(activity), keyUp(KeyEvent.KEYCODE_A))
            awaitInputEvents(inputEvents, "the 'a' release") { stamped ->
                stamped.any { (it.second as? InputEvent.Key)?.keyState == KeyState.Released }
            }
            dispatchKey(ConsumerTestSupport.notNull(activity), keyDown(KeyEvent.KEYCODE_B))
            val secondPress = awaitInputEvents(inputEvents, "the ordinary KeyChanged(b press)") { stamped ->
                stamped.count { (it.second as? InputEvent.Key)?.keyState == KeyState.Pressed } == 2
            }
            assertInteractionBeforeStimulus(handler, secondPress, index = 1, LogicalKey.Character("b"), "the 'b' press")
            assertEquals(
                listOf("UnlockPointer"),
                handler.requestRecords().drop(3).map { it.action },
                "the second trigger requested exactly the unlock",
            )
            assertTrue(handler.requestRecords()[3].accepted, "the unlock on a fresh token is admitted")

            // La capture rendue : le listener du SDK rapporte la perte, l'état revient à None.
            awaitSurfaceCapture(surface, PointerCaptureMode.None, "the UnlockPointer release reported by the listener")

            // Le bilan du handler : exactement les deux triggers, chacun avec sa sonde à zéro.
            assertEquals(
                2,
                handler.triggerRecords().size,
                "exactly the two key triggers fired, got ${handler.triggerRecords().map { it.description }}",
            )
            assertEquals(
                4,
                handler.requestRecords().size,
                "exactly the script's four requests, got ${handler.requestRecords().map { it.action }}",
            )

            closeOnMain(session)
        }
    }

    // ------------------------------------------------------------------
    // Aides du fichier — l'observation bornée et la frontière d'injection.
    // ------------------------------------------------------------------

    /** La factory qui capture le [KadreScope] de la session pour l'observation du test. */
    private fun scopeCapturingFactory(captured: AtomicReference<KadreScope?>): KadreApplicationFactory =
        KadreApplicationFactory {
            KadreApplication {
                captured.set(this)
                kotlinx.coroutines.awaitCancellation()
            }
        }

    /** L'Activity du scénario, capturée par référence atomique depuis le thread main. */
    private fun activityReference(
        scenario: ActivityScenario<ConsumerActivity>,
        prepareKeyFocus: Boolean = false,
    ): AtomicReference<ConsumerActivity?> {
        val reference = AtomicReference<ConsumerActivity?>(null)
        scenario.onActivity { activity ->
            reference.set(activity)
            if (prepareKeyFocus) {
                val view = activity.consumerView
                view.isFocusable = true
                view.isFocusableInTouchMode = true
                view.requestFocus()
            }
        }
        return reference
    }

    /** La surface primaire de la session — publiée par le looper main, attendue par polling. */
    private suspend fun awaitPrimarySurface(captured: AtomicReference<KadreScope?>): HostSurface {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            captured.get()?.primarySurface?.value?.let { return it }
            delay(POLL_INTERVAL_MILLIS)
        }
        error("the primary surface was never published within ${DEFAULT_TIMEOUT_MILLIS}ms")
    }

    /** La fenêtre primaire de la forme Activity — le manager la publie à l'attach. */
    private suspend fun awaitPrimaryWindow(scope: KadreScope): Window {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            scope.windows.state.value.primary?.let { return it }
            delay(POLL_INTERVAL_MILLIS)
        }
        error("the primary window was never published within ${DEFAULT_TIMEOUT_MILLIS}ms")
    }

    /** La fermeture explicite d'une session, sur le thread main (le contrat de thread du port). */
    private fun closeOnMain(session: AtomicReference<KadreSession?>) {
        ConsumerTestSupport.onMain { ConsumerTestSupport.notNull(session).close() }
    }

    /** Le launch d'une [ConsumerActivity] dans SA PROPRE tâche (le miroir du contrat AND-004). */
    private fun launchOwnTaskConsumerActivity(): ConsumerActivity {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = android.content.Intent(instrumentation.targetContext, ConsumerActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        @Suppress("UNCHECKED_CAST")
        return instrumentation.startActivitySync(intent) as ConsumerActivity
    }

    /** Le drain de souscription : le miroir des tests hôte (channelFlow → SharedFlow). */
    private suspend fun settle() {
        repeat(8) { yield() }
    }

    private fun titleOnMain(activity: ConsumerActivity): String {
        val holder = AtomicReference<String?>(null)
        ConsumerTestSupport.onMain { holder.set(activity.title?.toString().orEmpty()) }
        return ConsumerTestSupport.notNull(holder)
    }

    /** Le FLAG_SECURE réel des layout flags de la fenêtre — lu sur le thread main. */
    private fun secureOnMain(activity: ConsumerActivity): Boolean {
        val holder = AtomicReference<Boolean?>(null)
        ConsumerTestSupport.onMain {
            val flags = activity.window.attributes.flags
            holder.set(flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
        return ConsumerTestSupport.notNull(holder)
    }

    /** La visibilité courante des barres système — le readback des insets du decorView. */
    private fun systemBarsVisibleOnMain(activity: ConsumerActivity): Boolean {
        val holder = AtomicReference<Boolean?>(null)
        ConsumerTestSupport.onMain {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val insets = activity.window.decorView.rootWindowInsets
                holder.set(insets?.isVisible(WindowInsets.Type.systemBars()) ?: true)
            } else {
                holder.set(true)
            }
        }
        return ConsumerTestSupport.notNull(holder)
    }

    /** L'instantané natif d'une Activity — titre, FLAG_SECURE, visibilité des barres. */
    private data class ActivitySnapshot(val title: String, val secure: Boolean, val systemBarsVisible: Boolean)

    /**
     * L'instantané est lu DANS UN SEUL block main — les lectures helpers *OnMain imbriquent
     * `runOnMainSync`, interdit depuis le thread main lui-même.
     */
    private fun snapshotOnMain(activity: ConsumerActivity): ActivitySnapshot {
        val holder = AtomicReference<ActivitySnapshot?>(null)
        ConsumerTestSupport.onMain {
            val flags = activity.window.attributes.flags
            val insets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.window.decorView.rootWindowInsets
            } else {
                null
            }
            holder.set(
                ActivitySnapshot(
                    title = activity.title?.toString().orEmpty(),
                    secure = flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0,
                    systemBarsVisible = insets?.isVisible(WindowInsets.Type.systemBars()) ?: true,
                ),
            )
        }
        return ConsumerTestSupport.notNull(holder)
    }

    private fun isFinishingOnMain(activity: ConsumerActivity): Boolean {
        val holder = AtomicReference<Boolean?>(null)
        ConsumerTestSupport.onMain { holder.set(activity.isFinishing) }
        return ConsumerTestSupport.notNull(holder)
    }

    /** La demande plein écran rejouée jusqu'au commit que le readback confirme. */
    private suspend fun applyFullscreenUntilCommitted(
        activity: ConsumerActivity,
        window: Window,
        target: FullscreenMode,
    ): WindowUpdateOutcome.Applied {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        var attempts = 0
        var lastReadback = "?"
        while (System.currentTimeMillis() < deadline) {
            attempts += 1
            val result = window.apply(WindowUpdate(fullscreen = PropertyChange.Set(target)))
            val success = assertIs<KadreResult.Success<WindowUpdateOutcome>>(
                result,
                "the fullscreen request is always a Success — refusals ride inside the outcome",
            )
            when (val outcome = success.value) {
                is WindowUpdateOutcome.Applied -> return outcome
                is WindowUpdateOutcome.PartiallyApplied -> {
                    val allTemporary = outcome.rejected.all { rejection ->
                        val failure = rejection.failure
                        failure is KadreFailure.TemporarilyUnavailable && failure.retryable
                    }
                    assertTrue(
                        allTemporary,
                        "the readback-driven refusal is TemporarilyUnavailable(retryable = true), got ${outcome.rejected}",
                    )
                }

                else -> error("unexpected window outcome: $outcome")
            }
            lastReadback = "${window.state.value.fullscreen}; barsVisible=${systemBarsVisibleOnMain(activity)}"
            delay(POLL_INTERVAL_MILLIS)
        }
        error(
            "the fullscreen commit ($target) was never confirmed by the readback after $attempts attempts " +
                "(last: $lastReadback)",
        )
    }

    /** Les barres système atteignent la visibilité demandée — le readback des insets. */
    private suspend fun awaitSystemBars(activity: ConsumerActivity, visible: Boolean) {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        var last = systemBarsVisibleOnMain(activity)
        while (System.currentTimeMillis() < deadline) {
            last = systemBarsVisibleOnMain(activity)
            if (last == visible) return
            delay(POLL_INTERVAL_MILLIS)
        }
        error(
            "the system bars never became ${if (visible) "visible" else "hidden"} " +
                "within ${DEFAULT_TIMEOUT_MILLIS}ms; last=$last",
        )
    }

    /** L'icône effective de la View atteint le TYPE_* attendu — le readback du commit. */
    private suspend fun awaitPointerIcon(activity: ConsumerActivity, type: Int, description: String) {
        val expected = AtomicReference<PointerIcon?>(null)
        ConsumerTestSupport.onMain {
            expected.set(PointerIcon.getSystemIcon(activity.consumerView.context, type))
        }
        val wanted = ConsumerTestSupport.notNull(expected)
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        var last: PointerIcon? = null
        while (System.currentTimeMillis() < deadline) {
            ConsumerTestSupport.onMain { last = activity.consumerView.pointerIcon }
            if (last === wanted) return
            delay(POLL_INTERVAL_MILLIS)
        }
        error("the pointer icon never became type $type within ${DEFAULT_TIMEOUT_MILLIS}ms ($description)")
    }

    /** La capture publiée atteint le mode attendu — le canal du listener du SDK. */
    private suspend fun awaitSurfaceCapture(surface: HostSurface, mode: PointerCaptureMode, description: String) {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        var last = surface.state.value.pointerCapture
        while (System.currentTimeMillis() < deadline) {
            last = surface.state.value.pointerCapture
            if (last == mode) return
            delay(POLL_INTERVAL_MILLIS)
        }
        error("the pointer capture never became $mode within ${DEFAULT_TIMEOUT_MILLIS}ms ($description); last=$last")
    }

    /** L'INJECTION clavier par la frontière Android — le dispatch réel du callback fenêtre. */
    private fun dispatchKey(activity: ConsumerActivity, event: KeyEvent): Boolean {
        val done = CountDownLatch(1)
        val holder = AtomicReference(false)
        ConsumerTestSupport.onMain {
            holder.set(requireNotNull(activity.window.callback).dispatchKeyEvent(event))
            done.countDown()
        }
        check(done.await(INJECTION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) { "the key dispatch never returned" }
        return holder.get()
    }

    private fun keyDown(keyCode: Int, metaState: Int = 0, repeat: Int = 0): KeyEvent = KeyEvent(
        SystemClock.uptimeMillis(),
        SystemClock.uptimeMillis(),
        KeyEvent.ACTION_DOWN,
        keyCode,
        repeat,
        metaState,
    )

    private fun keyUp(keyCode: Int, metaState: Int = 0): KeyEvent = KeyEvent(
        SystemClock.uptimeMillis(),
        SystemClock.uptimeMillis(),
        KeyEvent.ACTION_UP,
        keyCode,
        0,
        metaState,
    )

    /** Une attente générique bornée sur prédicat, avec description nommée à l'échec. */
    private suspend fun awaitCondition(description: String, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            delay(POLL_INTERVAL_MILLIS)
        }
        error("the condition was never met within ${DEFAULT_TIMEOUT_MILLIS}ms ($description)")
    }

    /**
     * L'observation bornée des événements de fenêtre — au timeout, le scénario nomme ce qu'il
     * a vu.
     */
    private suspend fun awaitWindowEvents(
        recorder: WindowEventRecorder,
        description: String,
        predicate: (List<WindowEvent>) -> Boolean,
    ): List<WindowEvent> {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        var last = recorder.snapshot()
        while (System.currentTimeMillis() < deadline) {
            last = recorder.snapshot()
            if (predicate(last)) return last
            delay(POLL_INTERVAL_MILLIS)
        }
        error(
            "window events never satisfied the predicate within ${DEFAULT_TIMEOUT_MILLIS}ms ($description); " +
                "last=${last.joinToString(separator = ", ") { it::class.simpleName ?: "?" }}",
        )
    }

    /** L'observation bornée des événements d'entrée stampés (horloge partagée du scénario). */
    private suspend fun awaitInputEvents(
        recorder: InputEventRecorder,
        description: String,
        predicate: (List<Pair<Long, InputEvent>>) -> Boolean,
    ): List<Pair<Long, InputEvent>> {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        var last = recorder.snapshot()
        while (System.currentTimeMillis() < deadline) {
            last = recorder.snapshot()
            if (predicate(last)) return last
            delay(POLL_INTERVAL_MILLIS)
        }
        error(
            "input events never satisfied the predicate within ${DEFAULT_TIMEOUT_MILLIS}ms ($description); " +
                "last=${last.joinToString(separator = ", ") { it.second::class.simpleName ?: "?" }}",
        )
    }

    /** L'observation bornée des outcomes d'interaction. */
    private suspend fun awaitOutcomes(
        recorder: OutcomeRecorder,
        description: String,
        predicate: (List<InteractionActionOutcome>) -> Boolean,
    ): List<InteractionActionOutcome> {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        var last = recorder.snapshot()
        while (System.currentTimeMillis() < deadline) {
            last = recorder.snapshot()
            if (predicate(last)) return last
            delay(POLL_INTERVAL_MILLIS)
        }
        error(
            "interaction outcomes never satisfied the predicate within ${DEFAULT_TIMEOUT_MILLIS}ms ($description); " +
                "last=$last",
        )
    }

    /**
     * L'ordre interaction→stimulus, observé sur le scénario : le trigger attendu porte un
     * stamp d'horloge ANTÉRIEUR à celui de la collecte du `KeyChanged` du même down, et sa
     * sonde d'état (le nombre de touches publiées au moment du handler) vaut zéro — le handler
     * et l'accept du stimulus vivent sur le même thread main, dans cet ordre. [index] est la
     * position du trigger dans l'ordre de dispatch (0 : « a », 1 : « b ») — les deux appels
     * n'exigent PAS que le scénario n'en ait qu'un.
     */
    private fun assertInteractionBeforeStimulus(
        handler: ScriptedInteractionHandler,
        events: List<Pair<Long, InputEvent>>,
        index: Int,
        expectedKey: LogicalKey,
        description: String,
    ) {
        val records = handler.triggerRecords()
        assertTrue(
            index < records.size,
            "the $description trigger was recorded, got ${records.map { it.description }}",
        )
        val record = records[index]
        assertEquals(
            0,
            record.pressedKeysAtHandler,
            "at handler time the pressed key was not yet in the published input state ($description)",
        )
        val keyStamp = events.last { event ->
            (event.second as? InputEvent.Key)?.logicalKey == expectedKey &&
                (event.second as? InputEvent.Key)?.keyState == KeyState.Pressed
        }.first
        assertTrue(
            record.sequence < keyStamp,
            "the interaction trigger (${record.sequence}) preceded the ordinary stimulus ($keyStamp) for $description",
        )
    }

    /**
     * Le collecteur d'événements de fenêtre : souscrit en UNDISPATCHED, accumule sous verrou —
     * les publications arrivent du thread des commits (le runBlocking lui-même ou le main).
     */
    private class WindowEventRecorder(flow: Flow<WindowEvent>, scope: CoroutineScope) {
        private val lock = Any()
        private val events = mutableListOf<WindowEvent>()
        private val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            flow.collect { event -> synchronized(lock) { events += event } }
        }

        fun snapshot(): List<WindowEvent> = synchronized(lock) { events.toList() }
    }

    /**
     * Le collecteur d'outcomes d'interaction : souscrit en UNDISPATCHED, accumule sous verrou.
     */
    private class OutcomeRecorder(flow: Flow<InteractionActionOutcome>, scope: CoroutineScope) {
        private val lock = Any()
        private val outcomes = mutableListOf<InteractionActionOutcome>()
        private val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            flow.collect { outcome -> synchronized(lock) { outcomes += outcome } }
        }

        fun snapshot(): List<InteractionActionOutcome> = synchronized(lock) { outcomes.toList() }
    }

    /**
     * Le collecteur d'événements d'entrée : souscrit en UNDISPATCHED, chaque événement est
     * daté de l'horloge PARTAGÉE du scénario au moment de la collecte — l'ordre
     * interaction→stimulus se lit en comparant ces stamps à ceux du handler (le stamp du
     * handler est posé pendant le dispatch, sur le main, AVANT que l'accept du stimulus
     * ordinaire n'ait pu émettre quoi que ce soit : sa collecte est nécessairement postérieure).
     */
    private class InputEventRecorder(
        flow: Flow<InputEvent>,
        scope: CoroutineScope,
        private val clock: AtomicLong = AtomicLong(0L),
    ) {
        private val lock = Any()
        private val stamped = mutableListOf<Pair<Long, InputEvent>>()
        private val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            flow.collect { event -> synchronized(lock) { stamped += clock.incrementAndGet() to event } }
        }

        fun snapshot(): List<Pair<Long, InputEvent>> = synchronized(lock) { stamped.toList() }
    }

    /**
     * Le handler scénarisé du scénario token-authority — il ENREGISTRE (jamais asserté dans le
     * callback : un lancement dans le handler est un échec de session) et joue le script :
     *
     * - premier trigger : requiert `OpenWindow` (non annoncée — refus attendu, token NON
     *   consommé), puis `LockPointer(Locked)` (admis — le token est consommé), puis
     *   `UnlockPointer` (refus attendu `InteractionRequired(Consumed)` — le même token ne
     *   passe pas deux fois) ;
     * - second trigger : requiert `UnlockPointer` (admis — le nouveau token libère la capture).
     *
     * Chaque événement est daté de l'horloge partagée et sonde l'état d'entrée publié SUR
     * L'INSTANT (le prédicat de l'ordre interaction→stimulus, déterministe : le handler et
     * l'accept du stimulus vivent sur le même thread main, dans cet ordre).
     */
    @OptIn(DelicateKadreApi::class)
    private class ScriptedInteractionHandler(
        private val clock: AtomicLong,
        private val pressedProbe: () -> Int,
    ) : InteractionHandler {

        /** Un trigger reçu et sa sonde : description, stamp d'horloge, touches publiées à l'instant. */
        class Trigger(val description: String, val sequence: Long, val pressedKeysAtHandler: Int)

        /** Une requête scénarisée et son résultat exact : acceptée (requestId) ou refusée. */
        class Request(
            val action: String,
            val accepted: Boolean,
            val requestId: InteractionRequestId?,
            val failure: KadreFailure?,
        )

        private val lock = Any()
        private val triggers = mutableListOf<Trigger>()
        private val requests = mutableListOf<Request>()
        private var triggerCount = 0

        /** Les triggers reçus, en ordre de dispatch. */
        fun triggerRecords(): List<Trigger> = synchronized(lock) { triggers.toList() }

        fun requestRecords(): List<Request> = synchronized(lock) { requests.toList() }

        override fun onInteraction(context: InteractionContext, event: InteractionEvent) {
            val pressed = pressedProbe()
            synchronized(lock) {
                triggers += Trigger(
                    when (event) {
                        is InteractionEvent.KeyPressed -> "KeyPressed(${event.physicalKey})"
                        is InteractionEvent.TouchStarted -> "TouchStarted(${event.touchId})"
                        is InteractionEvent.PointerPressed -> "PointerPressed(${event.button})"
                        is InteractionEvent.DropEntered -> "DropEntered"
                    },
                    clock.incrementAndGet(),
                    pressed,
                )
                triggerCount += 1
            }
            fun record(action: String, result: KadreResult<InteractionRequestId>) {
                synchronized(lock) {
                    requests += when (result) {
                        is KadreResult.Success -> Request(action, true, result.value, null)
                        is KadreResult.Failure -> Request(action, false, null, result.reason)
                    }
                }
            }
            val index = synchronized(lock) { triggerCount }
            when (index) {
                1 -> {
                    record("OpenWindow", context.request(InteractionAction.OpenWindow()))
                    record("LockPointer", context.request(InteractionAction.LockPointer(PointerCaptureMode.Locked)))
                    record("UnlockPointer(consumed)", context.request(InteractionAction.UnlockPointer))
                }

                2 -> record("UnlockPointer", context.request(InteractionAction.UnlockPointer))
            }
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
        const val POLL_INTERVAL_MILLIS = 25L
        const val INJECTION_TIMEOUT_MILLIS = 10_000L
        const val EVENT_SETTLE_MILLIS = 250L
    }
}
