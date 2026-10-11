package org.graphiks.kadre.consumer

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.accessibility.AccessibilityEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.InputStateResetReason
import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.ModifierKey
import org.graphiks.kadre.input.NamedKey
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.input.ScrollDelta
import org.graphiks.kadre.input.SurfaceInputState
import org.graphiks.kadre.input.TouchPhase
import org.graphiks.kadre.platform.android.attachKadre
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.SurfaceUpdate
import org.graphiks.kadre.surface.SurfaceUpdateOutcome
import org.junit.runner.RunWith

/**
 * Les sept scénarios instrumentés du contrat AND-004 (observation et livraison de l'entrée
 * essentielle Android) sur l'API publique `attachKadre` résolue depuis l'umbrella PUBLIÉ.
 * Chaque nom de méthode est un `testName` contractuel du mapping AND-004 — la correspondance :
 *
 * | scenario id (registre)                  | méthode                             | sentinelle gardée                |
 * |-----------------------------------------|-------------------------------------|----------------------------------|
 * | android-input-host-untouched            | androidInputHostUntouched           | -                                |
 * | android-input-keys-mapped               | androidInputKeysMapped              | -                                |
 * | android-input-multi-touch-stable-ids    | androidInputMultiTouchStableIds     | -                                |
 * | android-input-focus-loss-resets         | androidInputFocusLossResets         | android-input-contacts-reset     |
 * | android-input-default-behavior-suppress | androidInputDefaultBehaviorSuppress | android-input-suppression-exact  |
 * | android-input-two-sessions-isolation    | androidInputTwoSessionsIsolation    | android-input-no-double-delivery |
 * | android-input-stylus-mouse-boundary     | androidInputStylusMouseBoundary     | -                                |
 *
 * LA FRONTIÈRE DE L'INJECTION : chaque événement entre par le point de dispatch RÉEL
 * d'Android — `activity.window.callback.dispatchKeyEvent(...)` /
 * `dispatchTouchEvent(MotionEvent.obtain(...))` /
 * `dispatchGenericMotionEvent(MotionEvent.obtain(...))` / `onWindowFocusChanged(false)` — la
 * fenêtre réelle de l'Activity, dont le callback EST le wrapper Kadre enchaîné après
 * l'install. Les événements traversent donc le chemin de production entier : copie des
 * primitives au point de dispatch, observation vers la delivery, forward intact au callback
 * wrappé. Les champs du `KeyEvent` que le wrapper extrait (`getUnicodeChar()`,
 * `repeatCount`…) ne sont observables que sur émulateur — c'est exactement ici que ce chemin
 * est prouvé.
 *
 * Toutes les formes sont Activity (l'observation n'est câblée que là — la forme View n'a pas
 * de `Window.Callback` à enchaîner, ses capabilities d'entrée restent honnêtement
 * Unsupported). Les scénarios sont autonomes (ActivityScenario `use{}` pour l'Activity
 * d'origine, `startActivitySync` pour la seconde — deux ActivityScenario simultanés se
 * détruisent mutuellement, cf. [ConsumerTestSupport.launchConsumerActivity]) et ferment leurs
 * sessions explicitement. L'observation est un polling borné sur le thread d'instrumentation ;
 * les injections et les mutations de View passent par le thread main.
 */
@RunWith(AndroidJUnit4::class)
class KadreConsumerInputContractTest {

    /**
     * android-input-host-untouched — un listener de l'application hôte (clé ET tactile, posés
     * sur la View attachée AVANT l'attach) reçoit toujours ses événements après l'install du
     * wrapper : le forwarding n'enlève rien au host — pour les événements qui atteignent la
     * View — et chaque événement y arrive EXACTEMENT UNE FOIS (le chaînage ne duplique pas).
     * La session observe les mêmes événements en parallèle : l'observation n'est pas une
     * interception.
     */
    @Test
    fun androidInputHostUntouched() = runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val activity = activityReference(scenario, prepareKeyFocus = true)
            val keyLog = HostKeyLog()
            val touchLog = HostTouchLog()
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity2 ->
                // La couture consumer : les listeners du host existent AVANT l'attach.
                keyLog.attach(activity2.consumerView)
                touchLog.attach(activity2.consumerView)
                session.set(
                    ConsumerTestSupport.success(
                        activity2.attachKadre(activity2.consumerView, scopeCapturingFactory(captured)),
                    ),
                )
            }
            val surface = awaitPrimarySurface(captured)
            val recorder = InputEventRecorder(surface.input.events, this)
            settle()
            val size = awaitViewSized(ConsumerTestSupport.notNull(activity))

            // Une touche press/release : le host la reçoit exactement une fois par action,
            // et la session l'observe en parallèle.
            dispatchKey(ConsumerTestSupport.notNull(activity), keyDown(KeyEvent.KEYCODE_A))
            dispatchKey(ConsumerTestSupport.notNull(activity), keyUp(KeyEvent.KEYCODE_A))
            awaitRecorder(recorder, "the session observed the 'a' press and release") { events ->
                keys(events).size >= 2
            }
            assertEquals(
                1,
                keyLog.count(KeyEvent.KEYCODE_A, KeyEvent.ACTION_DOWN),
                "the host key listener saw the DOWN exactly once",
            )
            assertEquals(
                1,
                keyLog.count(KeyEvent.KEYCODE_A, KeyEvent.ACTION_UP),
                "the host key listener saw the UP exactly once",
            )

            // Un contact tactile down/up au centre de la View : le listener du host reçoit
            // les deux actions, la session les observe aussi (Started puis Ended).
            val center = size.width / 2f to size.height / 2f
            dispatchTouch(
                ConsumerTestSupport.notNull(activity),
                touchEvent(MotionEvent.ACTION_DOWN, fingers(0 to center)),
            )
            dispatchTouch(
                ConsumerTestSupport.notNull(activity),
                touchEvent(MotionEvent.ACTION_UP, fingers(0 to center)),
            )
            awaitRecorder(recorder, "the session observed the touch start and end") { events ->
                touches(events).map { it.phase } == listOf(TouchPhase.Started, TouchPhase.Ended)
            }
            assertEquals(
                listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP),
                touchLog.snapshot(),
                "the host touch listener received every action exactly once, in order",
            )
            closeOnMain(session)
        }
    }

    /**
     * android-input-keys-mapped — l'injection KEYCODE_A par la frontière fenêtre livre au
     * réducteur le `InputEvent.Key` complet du modèle : `PhysicalKey.Code(0x07, 0x04)` (l'usage
     * HID Keyboard « a »), `LogicalKey.Character("a")` — avec la CASSE portée par
     * `getUnicodeChar()` sous Shift (`"A"` + le modificateur Shift) —, l'état press/release,
     * et le repeat d'un down répété. Les quatre dispatchs arrivent dans l'ordre.
     */
    @Test
    fun androidInputKeysMapped() = runBlocking {
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
            val recorder = InputEventRecorder(surface.input.events, this)
            settle()
            val target = ConsumerTestSupport.notNull(activity)

            dispatchKey(target, keyDown(KeyEvent.KEYCODE_A))
            dispatchKey(target, keyUp(KeyEvent.KEYCODE_A))
            dispatchKey(target, keyDown(KeyEvent.KEYCODE_A, repeat = 1))
            dispatchKey(target, keyDown(KeyEvent.KEYCODE_A, metaState = KeyEvent.META_SHIFT_ON))

            val observed = awaitRecorder(recorder, "the four mapped key events") { events ->
                keys(events).size >= 4
            }
            val usageA = PhysicalKey.Code(usagePage = 0x07, usageId = 0x04)
            assertEquals(
                listOf(
                    KeyExpected(usageA, LogicalKey.Character("a"), KeyLocation.Standard, KeyState.Pressed, repeat = false),
                    KeyExpected(usageA, LogicalKey.Character("a"), KeyLocation.Standard, KeyState.Released, repeat = false),
                    KeyExpected(usageA, LogicalKey.Character("a"), KeyLocation.Standard, KeyState.Pressed, repeat = true),
                    KeyExpected(
                        usageA,
                        LogicalKey.Character("A"),
                        KeyLocation.Standard,
                        KeyState.Pressed,
                        repeat = false,
                        modifiers = KeyboardModifiers(setOf(ModifierKey.Shift)),
                    ),
                ),
                keys(observed).take(4).map {
                    KeyExpected(it.physicalKey, it.logicalKey, it.location, it.keyState, it.repeat, it.modifiers)
                },
                "KEYCODE_A maps to HID usage 0x04 with case, repeat and modifiers from the platform event",
            )
            closeOnMain(session)
        }
    }

    /**
     * android-input-multi-touch-stable-ids — deux contacts injectés (DOWN, POINTER_DOWN,
     * MOVE, POINTER_UP, UP) livrent des `TouchChanged` dont l'identité est STABLE par doigt
     * (le même `touchId` du Started au Ended de chaque doigt, deux ids distincts entre les
     * doigts) et dont les phases sont ordonnées : Started, Started, Moved, Moved, Ended, Ended.
     */
    @Test
    fun androidInputMultiTouchStableIds() = runBlocking {
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
            val size = awaitViewSized(ConsumerTestSupport.notNull(activity))
            val target = ConsumerTestSupport.notNull(activity)

            val leftX = size.width * 0.25f
            val rightX = size.width * 0.75f
            val centerY = size.height / 2f
            dispatchTouch(target, touchEvent(MotionEvent.ACTION_DOWN, fingers(0 to (leftX to centerY))))
            dispatchTouch(
                target,
                touchEvent(
                    MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    fingers(0 to (leftX + 10f to centerY), 1 to (rightX to centerY)),
                ),
            )
            dispatchTouch(
                target,
                touchEvent(
                    MotionEvent.ACTION_MOVE,
                    fingers(0 to (leftX + 20f to centerY + 10f), 1 to (rightX + 20f to centerY + 10f)),
                ),
            )
            dispatchTouch(
                target,
                touchEvent(
                    MotionEvent.ACTION_POINTER_UP or (0 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    fingers(0 to (leftX + 20f to centerY + 10f), 1 to (rightX + 20f to centerY + 10f)),
                ),
            )
            dispatchTouch(
                target,
                touchEvent(MotionEvent.ACTION_UP, fingers(1 to (rightX + 20f to centerY + 10f))),
            )

            val orderedPhases = listOf(
                TouchPhase.Started,
                TouchPhase.Started,
                TouchPhase.Moved,
                TouchPhase.Moved,
                TouchPhase.Ended,
                TouchPhase.Ended,
            )
            val observed = awaitRecorder(recorder, "the six ordered touch phases") { events ->
                touches(events).map { it.phase } == orderedPhases
            }
            val phases = touches(observed)
            val firstContact = listOf(phases[0].touchId, phases[2].touchId, phases[4].touchId).distinct()
            val secondContact = listOf(phases[1].touchId, phases[3].touchId, phases[5].touchId).distinct()
            assertEquals(
                listOf(1, 1),
                listOf(firstContact.size, secondContact.size),
                "each finger keeps ONE stable touch id from Started through Ended, got $phases",
            )
            assertFalse(
                phases[0].touchId == phases[1].touchId,
                "the two simultaneous contacts have distinct identities",
            )
            closeOnMain(session)
        }
    }

    /**
     * android-input-focus-loss-resets — des contacts actifs puis la perte de focus fenêtre
     * simulée au callback (`onWindowFocusChanged(false)`) : l'événement `StateReset(FocusLost)`
     * arrive et l'état d'entrée redevient neutre (aucun contact résiduel) ; le contact suivant
     * est re-minté FRAIS — un contact de la vie d'avant le reset ne peut pas l'avaler.
     *
     * Sentinelle gardée : `android-input-contacts-reset` (un tracker qui ne se vide plus au
     * reset garde le contact sous l'id natif du doigt : le DOWN d'après reste muet — lui seul,
     * car aucun autre scénario ne rejoue un contact après une perte de focus).
     */
    @Test
    fun androidInputFocusLossResets() = runBlocking {
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
            val size = awaitViewSized(ConsumerTestSupport.notNull(activity))
            val target = ConsumerTestSupport.notNull(activity)
            val center = size.width / 2f to size.height / 2f

            // Un contact actif, visible dans l'état publié.
            dispatchTouch(target, touchEvent(MotionEvent.ACTION_DOWN, fingers(0 to center)))
            awaitRecorder(recorder, "the first Started") { events -> touches(events).size == 1 }
            awaitSurfaceTouches(surface, expectedSize = 1)

            // La perte de focus fenêtre, au callback RÉEL : StateReset(FocusLost) + état neutre.
            ConsumerTestSupport.onMain {
                requireNotNull(target.window.callback).onWindowFocusChanged(false)
            }
            awaitRecorder(recorder, "the StateReset(FocusLost)") { events ->
                events.any { it is InputEvent.StateReset && it.reason == InputStateResetReason.FocusLost }
            }
            awaitSurfaceTouches(surface, expectedSize = 0)

            // Le contact d'après est FRAIS : un down au même id natif re-mint une nouvelle
            // identité — la preuve que les contacts d'avant le reset ne subsistent nulle part.
            dispatchTouch(target, touchEvent(MotionEvent.ACTION_DOWN, fingers(0 to center)))
            val afterReset = awaitRecorder(recorder, "the fresh Started after the reset") { events ->
                touches(events).size == 2
            }
            assertFalse(
                touches(afterReset)[0].touchId == touches(afterReset)[1].touchId,
                "the post-reset contact is a fresh identity, not the pre-reset stuck one",
            )
            closeOnMain(session)
        }
    }

    /**
     * android-input-default-behavior-suppress — sous `inputDefaultBehavior=SuppressWhenPossible`
     * commis, une scroll-key documentaire (DPAD_DOWN) est CONSOMMÉE : la View du host ne la
     * reçoit plus, mais l'observation reste livrée (ArrowDown press/release au réducteur — le
     * précédent web « delivered then suppressed ») ; une touche ordinaire (A) n'est PAS
     * consommée — la suppression exacte, ni plus ni moins ; le retour à `HostDefault` rend
     * toute la consommation.
     *
     * Sentinelle gardée : `android-input-suppression-exact` (une policy qui consomme TOUTE
     * touche sous SuppressWhenPossible avale le A du host — lui seul, car aucun autre
     * scénario ne pose SuppressWhenPossible).
     */
    @Test
    fun androidInputDefaultBehaviorSuppress() = runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val activity = activityReference(scenario, prepareKeyFocus = true)
            val keyLog = HostKeyLog()
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity2 ->
                keyLog.attach(activity2.consumerView)
                session.set(
                    ConsumerTestSupport.success(
                        activity2.attachKadre(activity2.consumerView, scopeCapturingFactory(captured)),
                    ),
                )
            }
            val surface = awaitPrimarySurface(captured)
            val recorder = InputEventRecorder(surface.input.events, this)
            settle()
            val target = ConsumerTestSupport.notNull(activity)

            // Le commit de la policy : Applied, l'état publié porte la valeur.
            val applied = assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(
                    SurfaceUpdate(
                        inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.SuppressWhenPossible),
                    ),
                ),
            )
            assertIs<SurfaceUpdateOutcome.Applied>(applied.value)
            assertEquals(
                InputDefaultBehavior.SuppressWhenPossible,
                surface.state.value.inputDefaultBehavior,
                "the committed behaviour is authoritative",
            )

            // La scroll-key consommée : le host n'en voit RIEN, la session l'observe quand même.
            assertTrue(
                dispatchKey(target, keyDown(KeyEvent.KEYCODE_DPAD_DOWN)),
                "a consumed scroll key reports true to the system",
            )
            assertTrue(dispatchKey(target, keyUp(KeyEvent.KEYCODE_DPAD_DOWN)), "the release is consumed too")
            val suppressed = awaitRecorder(recorder, "the suppressed ArrowDown press and release") { events ->
                keys(events).map { it.logicalKey to it.keyState } ==
                    listOf(
                        LogicalKey.Named(NamedKey.ArrowDown) to KeyState.Pressed,
                        LogicalKey.Named(NamedKey.ArrowDown) to KeyState.Released,
                    )
            }
            assertEquals(2, keys(suppressed).size, "the consumed key is still observed (delivered then suppressed)")
            assertEquals(
                0,
                keyLog.count(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN),
                "the host view never receives the suppressed scroll key",
            )

            // La touche ordinaire : PAS consommée — le host la reçoit exactement une fois.
            assertTrue(dispatchKey(target, keyDown(KeyEvent.KEYCODE_A)), "the ordinary key is forwarded and accepted")
            awaitRecorder(recorder, "the forwarded 'a'") { events -> keys(events).size == 3 }
            assertEquals(
                1,
                keyLog.count(KeyEvent.KEYCODE_A, KeyEvent.ACTION_DOWN),
                "SuppressWhenPossible consumes exactly the scroll keys, nothing else",
            )

            // Le retour à HostDefault : plus AUCUNE consommation.
            val restored = assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(
                    SurfaceUpdate(inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.HostDefault)),
                ),
            )
            assertIs<SurfaceUpdateOutcome.Applied>(restored.value)
            assertTrue(
                dispatchKey(target, keyDown(KeyEvent.KEYCODE_DPAD_DOWN)),
                "the key is forwarded and accepted again",
            )
            awaitRecorder(recorder, "the forwarded dpad down under HostDefault") { events -> keys(events).size == 4 }
            assertEquals(
                1,
                keyLog.count(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN),
                "HostDefault never consumes — the host keeps its default behaviour",
            )
            closeOnMain(session)
        }
    }

    /**
     * android-input-two-sessions-isolation — deux Activities attachées : un événement de la
     * fenêtre A n'apparaît JAMAIS dans la session B (et l'inverse), chaque événement livré
     * exactement UNE FOIS par session. Le scénario exerce en plus l'identité de possession du
     * wrapper, la propriété que le host interférent met à l'épreuve : un callback ÉTRANGER de
     * l'application hôte posé par-dessus le wrapper reste dans la chaîne — la fermeture de la
     * session ne l'écrase jamais (le wrapper refuse de restaurer un callback non propriétaire)
     * — et la session ré-attachée ensuite voit chaque événement exactement une fois.
     *
     * Sentinelle gardée : `android-input-no-double-delivery` (un wrapper qui ne reconnaît plus
     * son propre callback écrase le callback étranger à la fermeture : le host perd ses
     * événements après le ré-attach — lui seul, car aucun autre scénario ne remplace le
     * callback fenêtre puis ne referme sa session).
     */
    @Test
    fun androidInputTwoSessionsIsolation() = runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val activityA = activityReference(scenario, prepareKeyFocus = true)
            val capturedA = AtomicReference<KadreScope?>(null)
            val sessionA = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                sessionA.set(
                    ConsumerTestSupport.success(
                        activity.attachKadre(activity.consumerView, scopeCapturingFactory(capturedA)),
                    ),
                )
            }
            val surfaceA = awaitPrimarySurface(capturedA)
            val recorderA = InputEventRecorder(surfaceA.input.events, this)
            settle()
            val targetA = ConsumerTestSupport.notNull(activityA)

            // K1 à la fenêtre A : exactement une livraison à la session A.
            dispatchKey(targetA, keyDown(KeyEvent.KEYCODE_A))
            awaitRecorder(recorderA, "the first key of session A") { events -> keys(events).size == 1 }
            // La seconde Activity — dans SA PROPRE tâche : un launch du MÊME composant que la
            // racine de la tâche courante ferme l'instance racine (la transition CLOSE
            // « FILLS_TASK » d'Android 15) et détruirait la session A ; MULTIPLE_TASK isole
            // les deux sessions l'une de l'autre dès la fenêtre.
            val activityB = launchOwnTaskConsumerActivity()
            try {
                ConsumerTestSupport.awaitResumed(activityB)
                val capturedB = AtomicReference<KadreScope?>(null)
                val sessionB = AtomicReference<KadreSession?>(null)
                ConsumerTestSupport.onMain {
                    val view = activityB.consumerView
                    view.isFocusable = true
                    view.isFocusableInTouchMode = true
                    view.requestFocus()
                    sessionB.set(
                        ConsumerTestSupport.success(
                            activityB.attachKadre(activityB.consumerView, scopeCapturingFactory(capturedB)),
                        ),
                    )
                }
                val surfaceB = awaitPrimarySurface(capturedB)
                val recorderB = InputEventRecorder(surfaceB.input.events, this)
                settle()
                ConsumerTestSupport.awaitRunning(ConsumerTestSupport.notNull(sessionB))

                // K2 à la fenêtre B : arrive à B seulement.
                dispatchKey(activityB, keyDown(KeyEvent.KEYCODE_B))
                awaitRecorder(recorderB, "the first key of session B") { events -> keys(events).size == 1 }
                assertEquals(1, keys(recorderA.snapshot()).size, "a window B event never crosses into session A")

                // K3 à la fenêtre A (désormais en arrière-plan) : arrive à A seulement.
                ConsumerTestSupport.onMain { targetA.consumerView.requestFocus() }
                dispatchKey(targetA, keyDown(KeyEvent.KEYCODE_C))
                awaitRecorder(recorderA, "the second key of session A") { events -> keys(events).size == 2 }
                assertEquals(1, keys(recorderB.snapshot()).size, "a window A event never appears in session B")

                // Un callback ÉTRANGER de l'application hôte se pose par-dessus le wrapper —
                // l'host a tout droit sur sa fenêtre ; le chaînage doit continuer de livrer.
                val foreign = AtomicReference<ForeignChainingCallback?>(null)
                ConsumerTestSupport.onMain {
                    foreign.set(ForeignChainingCallback(requireNotNull(targetA.window.callback)))
                    targetA.window.callback = foreign.get()
                }
                dispatchKey(targetA, keyDown(KeyEvent.KEYCODE_D))
                awaitRecorder(recorderA, "the key through the foreign callback") { events -> keys(events).size == 3 }
                assertEquals(
                    1,
                    ConsumerTestSupport.notNull(foreign).keyDispatches.get(),
                    "the foreign host callback sees each event exactly once",
                )

                // Fermeture de la session A, puis ré-attach sur la MÊME Activity : le
                // ré-attach voit chaque événement exactement une fois, et le callback étranger
                // est TOUJOURS dans la chaîne — la fermeture ne l'a jamais écrasé.
                closeOnMain(sessionA)
                ConsumerTestSupport.awaitTerminal(
                    ConsumerTestSupport.notNull(sessionA),
                    SessionOutcome.Stopped(SessionStopReason.HostRequested),
                )
                val capturedA2 = AtomicReference<KadreScope?>(null)
                val sessionA2 = AtomicReference<KadreSession?>(null)
                ConsumerTestSupport.onMain {
                    sessionA2.set(
                        ConsumerTestSupport.success(
                            targetA.attachKadre(targetA.consumerView, scopeCapturingFactory(capturedA2)),
                        ),
                    )
                }
                val surfaceA2 = awaitPrimarySurface(capturedA2)
                val recorderA2 = InputEventRecorder(surfaceA2.input.events, this)
                settle()
                ConsumerTestSupport.awaitRunning(ConsumerTestSupport.notNull(sessionA2))

                dispatchKey(targetA, keyDown(KeyEvent.KEYCODE_E))
                awaitRecorder(recorderA2, "exactly one key after the re-attach") { events -> keys(events).size == 1 }
                assertEquals(1, keys(recorderA2.snapshot()).size, "no double delivery after the re-attach")
                assertEquals(
                    2,
                    ConsumerTestSupport.notNull(foreign).keyDispatches.get(),
                    "the foreign callback survived the session close untouched",
                )
                assertEquals(1, keys(recorderB.snapshot()).size, "session B stayed untouched throughout")

                closeOnMain(sessionA2)
                closeOnMain(sessionB)
            } finally {
                ConsumerTestSupport.onMain { activityB.finish() }
            }
        }
    }

    /**
     * android-input-stylus-mouse-boundary — le stylet et la souris traversent la MÊME
     * frontière réelle que les doigts (le dispatch `window.callback`) et suivent la LANE de
     * leur outil, pas leur famille d'action (D11/D12) :
     *
     * - un contact STYLUS (`TOOL_TYPE_STYLUS` : DOWN/MOVE/UP injectés par
     *   `dispatchTouchEvent`) livre la pression `Primary` du pointeur unique —
     *   `PointerButtonChanged(Pen, Pressed)` portant l'état de stylet, puis
     *   `PointerMoved(Pen)`, puis la release — et l'état publié garde `touches` VIDE : le
     *   contact du stylet n'est pas un contact tactile ;
     * - la souris en hover (`ACTION_HOVER_ENTER`/`ACTION_HOVER_MOVE` injectés par
     *   `dispatchGenericMotionEvent`) livre `PointerEntered(Mouse)` puis `PointerMoved(Mouse)`
     *   — le hover des coordinates fenêtre, aux bornes de la View — et une molette
     *   `ACTION_SCROLL` à axes relatifs livre `Scrolled(Logical)` dans le delta relatif.
     *
     * L'injection est en coordonnées FENÊTRE (l'espace du dispatch `Window.Callback`) : le
     * scénario calcule l'origine de la View dans la fenêtre — la même paire publique
     * `getLocationOnScreen` que la couture lit — et injecte au centre VIEW-LOCAL ; les
     * positions livrées sont assertées view-loCALES (physique / scale publié), la preuve que
     * les coordonnées traversent la conversion de la couture.
     */
    @Test
    fun androidInputStylusMouseBoundary() = runBlocking {
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
            val size = awaitViewSized(ConsumerTestSupport.notNull(activity))
            val offset = awaitViewOffsetInWindow(ConsumerTestSupport.notNull(activity))
            val target = ConsumerTestSupport.notNull(activity)
            val scale = surface.state.value.scaleFactor
            // Le centre VIEW-LOCAL de la View, porté en coordonnées fenêtre pour l'injection.
            val center = (offset.first + size.width / 2f) to (offset.second + size.height / 2f)

            // --- La jambe stylet : un contact TOOL_TYPE_STYLUS par la frontière tactile. ---
            dispatchTouch(target, touchEvent(MotionEvent.ACTION_DOWN, pens(0 to center)))
            awaitRecorder(recorder, "the stylus Primary press") { events ->
                pointerButtonChanges(events).size >= 1
            }
            // L'état publié : la pression du stylo vit dans le pointeur unique, JAMAIS dans
            // les touches.
            val penState = awaitSurfaceInputState(surface, "the pressed pen pointer in state") { state ->
                state.pointers.any { pointer ->
                    pointer.kind == PointerKind.Pen &&
                        pointer.pen != null &&
                        PointerButton.Primary in pointer.pressedButtons
                }
            }
            assertEquals(0, penState.touches.size, "the stylus contact is NOT a touch state")

            dispatchTouch(
                target,
                touchEvent(MotionEvent.ACTION_MOVE, pens(0 to (center.first + 20f to center.second))),
            )
            dispatchTouch(target, touchEvent(MotionEvent.ACTION_UP, pens(0 to (center.first + 20f to center.second))))
            val stylus = awaitRecorder(recorder, "the stylus move and release") { events ->
                pointerMoves(events).isNotEmpty() && pointerButtonChanges(events).size >= 2
            }
            val pressed = pointerButtonChanges(stylus).first()
            val released = pointerButtonChanges(stylus).last()
            val stylusMove = pointerMoves(stylus).single()
            assertEquals(PointerKind.Pen, pressed.kind, "the stylus rides the pointer lane as a pen")
            assertEquals(PointerButton.Primary, pressed.button)
            assertEquals(PointerButtonState.Pressed, pressed.buttonState)
            assertTrue(pressed.pen != null, "the stylus press carries a pen state payload")
            assertEquals(PointerKind.Pen, stylusMove.kind, "the stylus move carries the pen kind")
            assertTrue(stylusMove.pen != null, "the stylus move carries a pen state payload")
            assertEquals(
                LogicalPoint(size.width / 2.0 / scale, size.height / 2.0 / scale),
                pressed.position,
                "the press position is the view-local center, divided by the published scale",
            )
            assertEquals(PointerButtonState.Released, released.buttonState, "the stylus release closes the press")
            assertTrue(
                touches(stylus).isEmpty(),
                "no TouchChanged ever rides the stylus contact",
            )
            awaitSurfaceInputState(surface, "the neutral touches after the stylus contact") { state ->
                state.touches.isEmpty()
            }

            // --- La jambe souris : hover aux bornes, molette à axes relatifs. ---
            dispatchGenericMotion(
                target,
                genericMotionEvent(
                    MotionEvent.ACTION_HOVER_ENTER,
                    mousePointers(0 to center),
                    InputDevice.SOURCE_MOUSE,
                ),
            )
            dispatchGenericMotion(
                target,
                genericMotionEvent(
                    MotionEvent.ACTION_HOVER_MOVE,
                    mousePointers(0 to (center.first + 10f to center.second)),
                    InputDevice.SOURCE_MOUSE,
                ),
            )
            val hover = awaitRecorder(recorder, "the mouse hover enter and move") { events ->
                pointerEnters(events).isNotEmpty() && pointerMoves(events).size >= 2
            }
            val entered = pointerEnters(hover).single()
            assertEquals(PointerKind.Mouse, entered.kind, "the hover rides the pointer lane as a mouse")
            assertEquals(
                LogicalPoint(size.width / 2.0 / scale, size.height / 2.0 / scale),
                entered.position,
                "the hover enter position is the view-local center",
            )
            val hoverMove = pointerMoves(hover).last()
            assertEquals(PointerKind.Mouse, hoverMove.kind)
            assertEquals(
                LogicalPoint((size.width / 2f + 10f) / scale, size.height / 2.0 / scale),
                hoverMove.position,
                "the hover move position is view-local",
            )

            // La molette : les axes RELATIFS de l'ACTION_SCROLL livrent un Scrolled(Logical)
            // dans le delta exact de l'axe — l'observation passive, jamais consommée.
            dispatchGenericMotion(
                target,
                scrollMotionEvent(
                    at = center.first + 10f to center.second,
                    relativeX = 3f,
                    relativeY = -2f,
                ),
            )
            val scrolled = awaitRecorder(recorder, "the relative-axis wheel scroll") { events ->
                scrolls(events).isNotEmpty()
            }
            val scroll = scrolls(scrolled).single()
            assertEquals(
                ScrollDelta.Logical(3.0, -2.0),
                scroll.delta,
                "the wheel scroll carries the relative axes as the logical delta",
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
    private fun awaitPrimarySurface(captured: AtomicReference<KadreScope?>): HostSurface {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            captured.get()?.primarySurface?.value?.let { return it }
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error("the primary surface was never published within ${DEFAULT_TIMEOUT_MILLIS}ms")
    }

    /** La fermeture explicite d'une session, sur le thread main (le contrat de thread du port). */
    private fun closeOnMain(session: AtomicReference<KadreSession?>) {
        ConsumerTestSupport.onMain { ConsumerTestSupport.notNull(session).close() }
    }

    /**
     * Le launch d'une [ConsumerActivity] dans SA PROPRE tâche (`MULTIPLE_TASK`) : deux
     * sessions vivantes du scénario two-sessions exigent deux Activities qui coexistent —
     * un relaunch du même composant dans la tâche courante ferme l'instance racine (la
     * transition CLOSE « FILLS_TASK » observée sur l'API 35) et détruirait la première
     * session. Contrairement à [ConsumerTestSupport.launchConsumerActivity], l'Activity
     * lancée vit DANS CE test : le scénario la termine explicitement.
     */
    private fun launchOwnTaskConsumerActivity(): ConsumerActivity {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, ConsumerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        @Suppress("UNCHECKED_CAST")
        return instrumentation.startActivitySync(intent) as ConsumerActivity
    }

    /** Les dimensions réelles de la View — l'injection tactile a besoin des bornes physiques. */
    private fun awaitViewSized(activity: ConsumerActivity): ViewSize {
        val holder = AtomicReference<ViewSize?>(null)
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            ConsumerTestSupport.onMain {
                val view = activity.consumerView
                if (view.width > 0 && view.height > 0) holder.set(ViewSize(view.width, view.height))
            }
            holder.get()?.let { return it }
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error("the consumer view never got laid out within ${DEFAULT_TIMEOUT_MILLIS}ms")
    }

    /**
     * L'INJECTION par la frontière Android : le dispatch réel du callback fenêtre — le
     * wrapper Kadre pendant une session — sur le thread main. La valeur rendue EST celle du
     * système (une touche consommée répond `true` sans forward).
     */
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

    /** La même frontière pour le tactile. */
    private fun dispatchTouch(activity: ConsumerActivity, event: MotionEvent): Boolean {
        val done = CountDownLatch(1)
        val holder = AtomicReference(false)
        ConsumerTestSupport.onMain {
            holder.set(requireNotNull(activity.window.callback).dispatchTouchEvent(event))
            done.countDown()
        }
        check(done.await(INJECTION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) { "the touch dispatch never returned" }
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

    /** Les propriétés d'un pointeur doigt — le lane touch (jamais le stylet ni le hover). */
    private fun finger(id: Int): MotionEvent.PointerProperties =
        MotionEvent.PointerProperties().apply {
            this.id = id
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }

    /** Les propriétés d'un pointeur stylet — le lane pointeur en CONTACT (jamais un touch). */
    private fun pen(id: Int): MotionEvent.PointerProperties =
        MotionEvent.PointerProperties().apply {
            this.id = id
            toolType = MotionEvent.TOOL_TYPE_STYLUS
        }

    /** Les propriétés d'un pointeur souris — le lane pointeur en HOVER. */
    private fun mouse(id: Int): MotionEvent.PointerProperties =
        MotionEvent.PointerProperties().apply {
            this.id = id
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }

    private fun pens(vararg pointers: Pair<Int, Pair<Float, Float>>): Pair<
        Array<MotionEvent.PointerProperties>,
        Array<MotionEvent.PointerCoords>,
        > = Pair(
        Array(pointers.size) { index -> pen(pointers[index].first) },
        Array(pointers.size) { index -> at(pointers[index].second.first, pointers[index].second.second) },
    )

    private fun mousePointers(vararg pointers: Pair<Int, Pair<Float, Float>>): Pair<
        Array<MotionEvent.PointerProperties>,
        Array<MotionEvent.PointerCoords>,
        > = Pair(
        Array(pointers.size) { index -> mouse(pointers[index].first) },
        Array(pointers.size) { index -> at(pointers[index].second.first, pointers[index].second.second) },
    )

    /** Un événement motion générique (hover) de la source déclarée de son outil. */
    private fun genericMotionEvent(
        action: Int,
        pointers: Pair<Array<MotionEvent.PointerProperties>, Array<MotionEvent.PointerCoords>>,
        source: Int,
    ): MotionEvent = MotionEvent.obtain(
        SystemClock.uptimeMillis(),
        SystemClock.uptimeMillis(),
        action,
        pointers.first.size,
        pointers.first,
        pointers.second,
        0,
        0,
        1f,
        1f,
        0,
        0,
        source,
        0,
    )

    /**
     * Une molette `ACTION_SCROLL` : les axes RELATIFS vivent dans les coords du pointeur —
     * c'est là que `getAxisValue(AXIS_RELATIVE_*)` du wrapper les lit.
     */
    private fun scrollMotionEvent(at: Pair<Float, Float>, relativeX: Float, relativeY: Float): MotionEvent {
        val coords = at(at.first, at.second).apply {
            setAxisValue(MotionEvent.AXIS_RELATIVE_X, relativeX)
            setAxisValue(MotionEvent.AXIS_RELATIVE_Y, relativeY)
        }
        return MotionEvent.obtain(
            SystemClock.uptimeMillis(),
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_SCROLL,
            1,
            arrayOf(mouse(0)),
            arrayOf(coords),
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_MOUSE,
            0,
        )
    }

    private fun at(x: Float, y: Float): MotionEvent.PointerCoords =
        MotionEvent.PointerCoords().apply {
            clear()
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        }

    private fun fingers(vararg pointers: Pair<Int, Pair<Float, Float>>): Pair<
        Array<MotionEvent.PointerProperties>,
        Array<MotionEvent.PointerCoords>,
        > = Pair(
        Array(pointers.size) { index -> finger(pointers[index].first) },
        Array(pointers.size) { index -> at(pointers[index].second.first, pointers[index].second.second) },
    )

    private fun touchEvent(
        action: Int,
        pointers: Pair<Array<MotionEvent.PointerProperties>, Array<MotionEvent.PointerCoords>>,
    ): MotionEvent = MotionEvent.obtain(
        SystemClock.uptimeMillis(),
        SystemClock.uptimeMillis(),
        action,
        pointers.first.size,
        pointers.first,
        pointers.second,
        0,
        0,
        1f,
        1f,
        0,
        0,
        InputDevice.SOURCE_TOUCHSCREEN,
        0,
    )

    /** La même frontière pour la motion générique — hover, boutons, molette. */
    private fun dispatchGenericMotion(activity: ConsumerActivity, event: MotionEvent): Boolean {
        val done = CountDownLatch(1)
        val holder = AtomicReference(false)
        ConsumerTestSupport.onMain {
            holder.set(requireNotNull(activity.window.callback).dispatchGenericMotionEvent(event))
            done.countDown()
        }
        check(done.await(INJECTION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) { "the generic motion dispatch never returned" }
        return holder.get()
    }

    /**
     * L'origine de la View DANS l'espace fenêtre — la même paire publique que la couture lit
     * (`view.getLocationOnScreen()` moins `decorView.getLocationOnScreen()`) : l'injection se
     * fait en coordonnées fenêtre, les assertions portent des positions view-loCALES.
     */
    private fun awaitViewOffsetInWindow(activity: ConsumerActivity): Pair<Int, Int> {
        val holder = AtomicReference<Pair<Int, Int>?>(null)
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline && holder.get() == null) {
            ConsumerTestSupport.onMain {
                val view = activity.consumerView
                if (view.isAttachedToWindow) {
                    val viewLocation = IntArray(2)
                    view.getLocationOnScreen(viewLocation)
                    val decorLocation = IntArray(2)
                    activity.window.decorView.getLocationOnScreen(decorLocation)
                    holder.set((viewLocation[0] - decorLocation[0]) to (viewLocation[1] - decorLocation[1]))
                }
            }
            if (holder.get() == null) Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        return holder.get() ?: error("the consumer view was never attached within ${DEFAULT_TIMEOUT_MILLIS}ms")
    }

    /** L'état d'entrée publié — attendu par polling borné sur le prédicat du scénario. */
    private suspend fun awaitSurfaceInputState(
        surface: HostSurface,
        description: String,
        predicate: (SurfaceInputState) -> Boolean,
    ): SurfaceInputState {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        var last = surface.input.state.value
        while (System.currentTimeMillis() < deadline) {
            last = surface.input.state.value
            if (predicate(last)) return last
            delay(POLL_INTERVAL_MILLIS)
        }
        error("the input state never satisfied the predicate within ${DEFAULT_TIMEOUT_MILLIS}ms ($description); last=$last")
    }

    /** Le drain de souscription : le miroir des tests hôte (channelFlow → SharedFlow). */
    private suspend fun settle() {
        repeat(8) { yield() }
    }

    /**
     * L'observation bornée des événements d'entrée — au timeout, le scénario nomme ce qu'il a
     * vu. La pause est `delay` (JAMAIS `Thread.sleep`) : le collecteur du flux vit sur la
     * boucle du runBlocking appelant — un blocage de thread empêcherait sa reprise, et la
     * fenêtre passerait à côté d'événements pourtant publiés.
     */
    private suspend fun awaitRecorder(
        recorder: InputEventRecorder,
        description: String,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        predicate: (List<InputEvent>) -> Boolean,
    ): List<InputEvent> {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var last = recorder.snapshot()
        while (System.currentTimeMillis() < deadline) {
            last = recorder.snapshot()
            if (predicate(last)) return last
            delay(POLL_INTERVAL_MILLIS)
        }
        error(
            "input events never satisfied the predicate within ${timeoutMillis}ms ($description); " +
                "last=${last.joinToString(separator = ", ", transform = ::describe)}",
        )
    }

    /** Les contacts publiés par l'état d'entrée — attendus par polling borné. */
    private suspend fun awaitSurfaceTouches(surface: HostSurface, expectedSize: Int) {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        var last = surface.input.state.value.touches.size
        while (System.currentTimeMillis() < deadline) {
            last = surface.input.state.value.touches.size
            if (last == expectedSize) return
            delay(POLL_INTERVAL_MILLIS)
        }
        error("the input state never held exactly $expectedSize touch(es); last=$last")
    }

    private fun keys(events: List<InputEvent>): List<InputEvent.Key> = events.filterIsInstance<InputEvent.Key>()

    private fun touches(events: List<InputEvent>): List<InputEvent.TouchChanged> =
        events.filterIsInstance<InputEvent.TouchChanged>()

    private fun pointerMoves(events: List<InputEvent>): List<InputEvent.PointerMoved> =
        events.filterIsInstance<InputEvent.PointerMoved>()

    private fun pointerButtonChanges(events: List<InputEvent>): List<InputEvent.PointerButtonChanged> =
        events.filterIsInstance<InputEvent.PointerButtonChanged>()

    private fun pointerEnters(events: List<InputEvent>): List<InputEvent.PointerEntered> =
        events.filterIsInstance<InputEvent.PointerEntered>()

    private fun scrolls(events: List<InputEvent>): List<InputEvent.Scrolled> =
        events.filterIsInstance<InputEvent.Scrolled>()

    private fun describe(event: InputEvent): String = when (event) {
        is InputEvent.Key -> "Key(${event.physicalKey}, ${event.logicalKey}, ${event.keyState})"
        is InputEvent.TouchChanged -> "Touch(${event.phase}, ${event.touchId})"
        is InputEvent.StateReset -> "Reset(${event.reason})"
        is InputEvent.PointerMoved -> "PointerMoved(${event.kind}, ${event.position})"
        is InputEvent.PointerButtonChanged -> "PointerButton(${event.kind}, ${event.button}, ${event.buttonState})"
        is InputEvent.PointerEntered -> "PointerEntered(${event.kind}, ${event.position})"
        is InputEvent.PointerLeft -> "PointerLeft(${event.kind})"
        is InputEvent.Scrolled -> "Scrolled(${event.delta})"
        else -> event.toString()
    }

    /** La taille physique de la View, lue sur le thread main. */
    private class ViewSize(val width: Int, val height: Int)

    /** La forme attendue d'une touche livrée — la comparaison du scénario keys-mapped. */
    private data class KeyExpected(
        val physicalKey: PhysicalKey,
        val logicalKey: LogicalKey,
        val location: KeyLocation,
        val keyState: KeyState,
        val repeat: Boolean,
        val modifiers: KeyboardModifiers = KeyboardModifiers(emptySet()),
    )

    /**
     * Le journal du listener clavier du host — posé AVANT l'attach, compté par (action, code).
     * (`onKey` nomme le KEYCODE en second paramètre ; l'action se lit sur l'événement.)
     */
    private class HostKeyLog {
        private val events = mutableListOf<String>()

        fun attach(view: View) {
            view.setOnKeyListener { _, keyCode, event ->
                synchronized(this) { events += "${event.action}:$keyCode" }
                true
            }
        }

        fun count(keyCode: Int, action: Int): Int =
            synchronized(this) { events.count { it == "$action:$keyCode" } }
    }

    /** Le journal du listener tactile du host — les actions masquées, dans l'ordre. */
    private class HostTouchLog {
        private val actions = mutableListOf<Int>()

        fun attach(view: View) {
            view.setOnTouchListener { _, event ->
                synchronized(this) { actions += event.actionMasked }
                true
            }
        }

        fun snapshot(): List<Int> = synchronized(this) { actions.toList() }
    }

    /**
     * Le callback ÉTRANGER de l'application hôte : un vrai wrapper Window.Callback chaîné sur
     * ce qu'il a remplacé — exactement ce qu'un host observateur pose. Il compte ses
     * dispatchs clavier : la preuve qu'il reste dans la chaîne après la fermeture de session
     * (le wrapper Kadre ne peut jamais écraser un callback qu'il ne possède plus).
     */
    private class ForeignChainingCallback(private val wrapped: Window.Callback) : Window.Callback {
        val keyDispatches = AtomicInteger(0)

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            keyDispatches.incrementAndGet()
            return wrapped.dispatchKeyEvent(event)
        }

        override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean = wrapped.dispatchKeyShortcutEvent(event)

        override fun dispatchTouchEvent(event: MotionEvent): Boolean = wrapped.dispatchTouchEvent(event)

        override fun dispatchTrackballEvent(event: MotionEvent): Boolean = wrapped.dispatchTrackballEvent(event)

        override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
            wrapped.dispatchGenericMotionEvent(event)

        override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent?): Boolean =
            wrapped.dispatchPopulateAccessibilityEvent(event)

        override fun onCreatePanelView(featureId: Int): View? = wrapped.onCreatePanelView(featureId)

        override fun onCreatePanelMenu(featureId: Int, menu: android.view.Menu): Boolean =
            wrapped.onCreatePanelMenu(featureId, menu)

        override fun onPreparePanel(featureId: Int, view: View?, menu: android.view.Menu): Boolean =
            wrapped.onPreparePanel(featureId, view, menu)

        override fun onMenuOpened(featureId: Int, menu: android.view.Menu): Boolean =
            wrapped.onMenuOpened(featureId, menu)

        override fun onMenuItemSelected(featureId: Int, item: android.view.MenuItem): Boolean =
            wrapped.onMenuItemSelected(featureId, item)

        override fun onWindowAttributesChanged(attrs: android.view.WindowManager.LayoutParams?) {
            wrapped.onWindowAttributesChanged(attrs)
        }

        override fun onContentChanged() {
            wrapped.onContentChanged()
        }

        override fun onWindowFocusChanged(hasFocus: Boolean) {
            wrapped.onWindowFocusChanged(hasFocus)
        }

        override fun onAttachedToWindow() {
            wrapped.onAttachedToWindow()
        }

        override fun onDetachedFromWindow() {
            wrapped.onDetachedFromWindow()
        }

        override fun onPanelClosed(featureId: Int, menu: android.view.Menu) {
            wrapped.onPanelClosed(featureId, menu)
        }

        override fun onSearchRequested(): Boolean = wrapped.onSearchRequested()

        override fun onSearchRequested(searchEvent: android.view.SearchEvent?): Boolean =
            wrapped.onSearchRequested(searchEvent)

        override fun onWindowStartingActionMode(callback: android.view.ActionMode.Callback?): android.view.ActionMode? =
            wrapped.onWindowStartingActionMode(callback)

        override fun onWindowStartingActionMode(
            callback: android.view.ActionMode.Callback?,
            type: Int,
        ): android.view.ActionMode? = wrapped.onWindowStartingActionMode(callback, type)

        override fun onActionModeStarted(mode: android.view.ActionMode?) {
            wrapped.onActionModeStarted(mode)
        }

        override fun onActionModeFinished(mode: android.view.ActionMode?) {
            wrapped.onActionModeFinished(mode)
        }

        override fun onProvideKeyboardShortcuts(
            groups: MutableList<android.view.KeyboardShortcutGroup>?,
            menu: android.view.Menu?,
            deviceId: Int,
        ) {
            wrapped.onProvideKeyboardShortcuts(groups, menu, deviceId)
        }

        override fun onPointerCaptureChanged(hasCapture: Boolean) {
            wrapped.onPointerCaptureChanged(hasCapture)
        }
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
        const val INJECTION_TIMEOUT_MILLIS = 10_000L
    }
}
