package org.graphiks.kadre.consumer

import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
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
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.NamedKey
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.platform.android.attachKadre
import org.graphiks.kadre.surface.HostSurface
import org.junit.runner.RunWith

/**
 * Les deux scénarios instrumentés du contrat AND-006 (qualification frontière du canal texte
 * Android) sur l'API publique `attachKadre` résolue depuis l'umbrella PUBLIÉ. Chaque nom de
 * méthode est un `testName` contractuel du mapping AND-006 — la correspondance :
 *
 * | scenario id (registre)                      | méthode                         | sentinelle gardée            |
 * |---------------------------------------------|---------------------------------|------------------------------|
 * | android-text-input-channel-absent           | androidTextInputChannelAbsent   | -                            |
 * | android-text-input-ime-key-deduplication    | androidTextInputImeKeyDeduplication | android-text-input-no-synthesis |
 *
 * LE FAIT CENTRAL DE LA PHASE 5 : kadre ne possède ni ne sous-classe la View de l'hôte — le
 * SDK n'offre à une View ordinaire AUCUN canal d'éditeur (`onCreateInputConnection` répond
 * `null`, `onCheckIsTextEditor` répond `false` — le fait frontière SDK, prouvé ICI à la
 * frontière sur la View attachée du consumer), donc le port texte publie
 * `Unsupported(TextInput)` et le réducteur refuse `openTextInput` on-row. La machinerie IME
 * (Task 1) reste garée derrière la décision normative.
 *
 * LA RÈGLE NORMATIVE ÉPINGLÉE (DESIGN §10.3) : « le texte composé provient uniquement de
 * `TextInputSession` ; kadre ne synthétise pas de texte à partir des événements clavier ».
 * Les touches injectées à la frontière — caractère, `ACTION_MULTIPLE` PORTEUR DE TEXTE,
 * suppression — ne livrent que des `InputEvent.Key` (le porteur de texte, lui, ne livre
 * RIEN — identité `KEYCODE_UNKNOWN` hors table, forwardée sans stimulus inventé) ; le texte
 * que l'événement transporte n'existe pour la session d'aucune façon : zéro
 * `TextInputEvent` (structurellement — la seule voie d'un `TextInputEvent` est une
 * `TextInputSession` ouverte par `openTextInput`, que la surface refuse ; le scénario
 * prouve le refus vivant, APRÈS les injections), capabilities texte inchangées. La mise en
 * garde du wrapper de la phase 3 est écrite ici : un `ACTION_MULTIPLE` est « du texte qui
 * entre, jamais une relève » — et jamais une observation.
 *
 * L'injection est la même frontière RÉELLE que l'AND-004 : `activity.window.callback
 * .dispatchKeyEvent(...)` — la fenêtre de l'Activity, dont le callback EST le wrapper kadre
 * pendant la session. Les gestures ne sont PAS rejouées ici : le scénario multi-touch de
 * l'AND-004 épingle déjà l'absence de gesture claim (les capabilities publient la décision
 * `Unsupported(GestureInput)`), la limite déclarée est enregistrée dans capabilities/android.md.
 *
 * Les scénarios sont autonomes (ActivityScenario `use{}`) et ferment leur session
 * explicitement ; l'observation est un polling borné sur le thread d'instrumentation, les
 * appels View et les injections passent par le thread main.
 */
@RunWith(AndroidJUnit4::class)
class KadreConsumerTextContractTest {

    /**
     * android-text-input-channel-absent — sur la View du consumer attachée (forme Activity),
     * les DEUX méthodes publiques du canal éditeur répondent leur fait SDK : la View
     * ordinaire n'est pas un éditeur (`onCreateInputConnection(EditorInfo())` == **null** —
     * l'implémentation de base de `View`, le fait frontière prouvé à la frontière ;
     * `onCheckIsTextEditor()` == false). La surface publiée est honnête en retour :
     * `capabilities.textInput` == `Unsupported(TextInput)`, et `openTextInput` répond le
     * refus on-row `Failure(Unsupported(TextInput))` — le réducteur consulte la capability
     * avant tout appel du port, le port n'est jamais ouvert par la voie publique.
     */
    @Test
    fun androidTextInputChannelAbsent() = runBlocking {
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
            settle()
            val view = ConsumerTestSupport.notNull(activity).consumerView

            // Le fait frontière SDK, prouvé sur la View attachée RÉELLE : la View ordinaire
            // n'offre AUCUN canal éditeur — les deux méthodes publiques répondent leur
            // absence, et l'IME n'a rien à quoi se connecter.
            ConsumerTestSupport.onMain {
                assertNull(
                    view.onCreateInputConnection(EditorInfo()),
                    "a plain host View offers NO editor channel — onCreateInputConnection answers null",
                )
                assertEquals(
                    false,
                    view.onCheckIsTextEditor(),
                    "a plain host View is not a text editor — onCheckIsTextEditor answers false",
                )
            }

            // La surface publiée dit la même chose : le port texte est Unsupported, et le
            // réducteur refuse l'ouverture ON-ROW — la failure porte l'opération exacte.
            val textInputCapability = surface.input.state.value.capabilities.textInput
            assertEquals(
                Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.TextInput)),
                textInputCapability,
                "the published text input capability is Unsupported(TextInput)",
            )
            val refusal = assertIs<KadreResult.Failure>(
                surface.input.openTextInput(TextInputConfig()),
                "openTextInput is refused on-row, the port is never opened by the public path",
            )
            assertEquals(
                KadreFailure.Unsupported(KadreOperation.TextInput),
                refusal.reason,
                "the refusal names Unsupported(TextInput), the port capability",
            )
            closeOnMain(session)
        }
    }

    /**
     * android-text-input-ime-key-deduplication — trois familles de touches injectées à la
     * frontière réelle (`window.callback.dispatchKeyEvent`) : une touche caractère A
     * (down/up — `getUnicodeChar()` fourni), un `ACTION_MULTIPLE` PORTEUR de texte
     * («xyz» — le constructeur public qui transporte `characters`, qui impose avec lui le
     * `KEYCODE_UNKNOWN` de la forme IME), et la suppression IME-like KEYCODE_DEL (down/up).
     * La livraison est EXACTEMENT des `InputEvent.Key`, dans l'ordre : le caractère de la
     * touche A vient de `getUnicodeChar()` («a» — le fait touche, jamais une observation
     * texte) ; l'`ACTION_MULTIPLE` porteur de texte ne livre RIEN (une identité native hors
     * table — `KEYCODE_UNKNOWN` — est forwardée sans AUCUN stimulus inventé, le texte qui
     * entre n'existe pour la session d'aucune façon) ; la suppression est la touche nommée
     * Backspace, jamais une édition texte. ZÉRO `TextInputEvent` : la seule voie d'un
     * événement texte est une `TextInputSession` ouverte — la surface refuse toujours
     * `openTextInput` APRÈS les injections (le zéro est structurel, la preuve est vivante) —
     * et les capabilities texte sont INCHANGÉES.
     *
     * Sentinelle gardée : `android-text-input-no-synthesis` (une livraison qui déduirait une
     * observation du texte porté — la chaîne publiée à la place du silence, ou l'identité
     * touche inventée depuis elle — est la classe de mutation que l'assertion d'ordre exact
     * avale : elle seule, car aucun autre scénario n'injecte d'`ACTION_MULTIPLE` porteur de
     * texte).
     */
    @Test
    fun androidTextInputImeKeyDeduplication() = runBlocking {
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

            // La famille caractère, puis le porteur de texte, puis la suppression : l'ordre
            // de l'attente qui suit EST la preuve du silence — la position 3 est la DEL, rien
            // du «xyz» n'a pu se glisser entre les deux.
            dispatchKey(target, keyDown(KeyEvent.KEYCODE_A))
            dispatchKey(target, keyUp(KeyEvent.KEYCODE_A))
            dispatchKey(target, multipleCarryingText("xyz"))
            dispatchKey(target, keyDown(KeyEvent.KEYCODE_DEL))
            awaitRecorder(recorder, "the two character keys then the delete press, nothing from the text carrier") { events ->
                keys(events).size >= 3
            }
            dispatchKey(target, keyUp(KeyEvent.KEYCODE_DEL))

            val usageA = PhysicalKey.Code(usagePage = 0x07, usageId = 0x04)
            val usageBackspace = PhysicalKey.Code(usagePage = 0x07, usageId = 0x2A)
            val observed = awaitRecorder(recorder, "the four key observations of the three families") { events ->
                keys(events).size >= 4
            }

            // EXACTEMENT des InputEvent.Key : chaque événement publié par les trois familles
            // est une observation touche — rien d'autre n'existe sur ce chemin.
            assertTrue(
                observed.all { it is InputEvent.Key },
                "every observation of the key injections is an InputEvent.Key, got " +
                    observed.joinToString(separator = ", ", transform = ::describe),
            )
            assertEquals(
                listOf(
                    KeyExpected(usageA, LogicalKey.Character("a"), KeyLocation.Standard, KeyState.Pressed),
                    KeyExpected(usageA, LogicalKey.Character("a"), KeyLocation.Standard, KeyState.Released),
                    // Le ACTION_MULTIPLE porteur de «xyz» : AUCUNE observation — l'identité
                    // native KEYCODE_UNKNOWN est hors table, l'événement est forwardé sans
                    // stimulus inventé, le texte porté n'est jamais devenu quoi que ce soit.
                    KeyExpected(usageBackspace, LogicalKey.Named(NamedKey.Backspace), KeyLocation.Standard, KeyState.Pressed),
                    KeyExpected(usageBackspace, LogicalKey.Named(NamedKey.Backspace), KeyLocation.Standard, KeyState.Released),
                ),
                keys(observed).take(4).map {
                    KeyExpected(it.physicalKey, it.logicalKey, it.location, it.keyState, it.repeat, it.modifiers)
                },
                "the key lane delivers exactly the unicodeChar KEY facts — the carried text and " +
                    "the delete keycode are never an observation of their own",
            )

            // ZÉRO TextInputEvent — structurel et vivant : la seule voie d'un événement
            // texte est une TextInputSession, la surface refuse toujours, et les
            // capabilities texte n'ont pas bougé d'un poil pendant les injections.
            assertEquals(
                Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.TextInput)),
                surface.input.state.value.capabilities.textInput,
                "the text input capability is unchanged after the key injections",
            )
            val refusal = assertIs<KadreResult.Failure>(
                surface.input.openTextInput(TextInputConfig()),
                "openTextInput stays refused after the injections — no text session ever existed",
            )
            assertEquals(
                KadreFailure.Unsupported(KadreOperation.TextInput),
                refusal.reason,
                "the post-injection refusal is still the port capability Unsupported(TextInput)",
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
     * L'INJECTION par la frontière Android : le dispatch réel du callback fenêtre — le
     * wrapper kadre pendant une session — sur le thread main. La valeur rendue EST celle du
     * système.
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

    /**
     * Un `ACTION_MULTIPLE` PORTEUR de texte — le constructeur public qui transporte la
     * chaîne `characters`, la forme des IME qui committent (le domaine de
     * `KeyCharacterMap.getEvents`). Le SDK impose avec elle la forme canonique de l'IME :
     * `ACTION_MULTIPLE` + `KEYCODE_UNKNOWN` + source clavier.
     */
    private fun multipleCarryingText(text: String): KeyEvent = KeyEvent(
        SystemClock.uptimeMillis(),
        text,
        android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,
        0,
    )

    /** Le drain de souscription : le miroir des tests hôte (channelFlow → SharedFlow). */
    private suspend fun settle() {
        repeat(8) { yield() }
    }

    /**
     * L'observation bornée des événements d'entrée — au timeout, le scénario nomme ce qu'il a
     * vu. La pause est `delay` (JAMAIS `Thread.sleep`) : le collecteur du flux vit sur la
     * boucle du runBlocking appelant.
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

    private fun keys(events: List<InputEvent>): List<InputEvent.Key> = events.filterIsInstance<InputEvent.Key>()

    private fun describe(event: InputEvent): String = when (event) {
        is InputEvent.Key -> "Key(${event.physicalKey}, ${event.logicalKey}, ${event.keyState})"
        else -> event.toString()
    }

    /** La forme attendue d'une touche livrée — la comparaison du scénario de déduplication. */
    private data class KeyExpected(
        val physicalKey: PhysicalKey,
        val logicalKey: LogicalKey,
        val location: KeyLocation,
        val keyState: KeyState,
        val repeat: Boolean = false,
        val modifiers: org.graphiks.kadre.input.KeyboardModifiers =
            org.graphiks.kadre.input.KeyboardModifiers(emptySet()),
    )

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
