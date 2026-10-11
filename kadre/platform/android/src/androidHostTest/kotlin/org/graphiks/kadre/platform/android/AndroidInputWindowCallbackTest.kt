package org.graphiks.kadre.platform.android

import android.view.ActionMode
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.SearchEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import org.graphiks.kadre.input.KeyState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Le mécanisme d'observation clavier de la phase 3 : le cœur de décision pur
 * ([AndroidInputWindowCallback]) — les six cas épinglés du plan — puis le wrapper réel
 * `Window.Callback` ([AndroidWindowCallbackForwarder]) sur ce que l'hôte JVM peut prouver :
 * l'installation/la restauration exactes (garde anti-double-install et garde de
 * non-propriété), le forward du focus perdu et la délégation des méthodes sans argument.
 *
 * Le chemin `dispatchKeyEvent(KeyEvent)` du wrapper réel n'est PAS prouvé ici : aucun
 * `KeyEvent` n'est constructible hors émulateur (le stub android.jar lance à la
 * construction, cf. `AndroidViewLeaseTest`) — l'extraction des cinq primitives
 * (`keyCode`, `repeatCount`, `metaState`, `getUnicodeChar()`, `isSystemKey`) est la
 * plomberie SDK mince dont le chemin nominal est prouvé sur émulateur par le scénario
 * contractuel (Task 6), le même partage que le bail de phase 2. Le chaînage de deux
 * wrappers (cas 2) est donc prouvé au niveau des deux cœurs, câblés EXACTEMENT comme les
 * deux wrappers le seraient : le `forwardKey` du supérieur délègue l'événement entier
 * (les mêmes primitives, unicode et isSystemKey compris) dans le `dispatchKeyEvent` de
 * l'inférieur.
 */
class AndroidInputWindowCallbackTest {

    // -----------------------------------------------------------------------
    // Cas 1 — tout est forwardé, la décision de forward est toujours prise
    // -----------------------------------------------------------------------

    @Test
    fun forwardsEverythingAndRestoresTheOriginal() {
        val recorder = CoreRecorder(forwardResult = true)
        val delivered = recorder.core.dispatchKeyEvent(
            nativeKeyCode = KeyEvent.KEYCODE_A,
            repeat = false,
            metaState = KeyEvent.META_SHIFT_ON,
            unicodeChar = 'A'.code,
            isSystemKey = false,
            keyState = KeyState.Pressed,
        )
        assertTrue(delivered, "the forward decision IS the dispatch result")
        assertEquals(1, recorder.forwards.size, "exactly one forward, never two")
        assertEquals(Triple(KeyEvent.KEYCODE_A, false, KeyEvent.META_SHIFT_ON), recorder.forwards.single())
        assertEquals(1, recorder.observations.size, "the key was observed")

        // Le refus du callback wrappé se propage tel quel — le wrapper est transparent.
        val refusing = CoreRecorder(forwardResult = false)
        val refused = refusing.core.dispatchKeyEvent(
            nativeKeyCode = KeyEvent.KEYCODE_B,
            repeat = true,
            metaState = 0,
            unicodeChar = null,
            isSystemKey = false,
            keyState = KeyState.Released,
        )
        assertFalse(refused, "a false forward decision propagates")
        assertEquals(1, refusing.forwards.size)
        assertEquals(Triple(KeyEvent.KEYCODE_B, true, 0), refusing.forwards.single())
    }

    // -----------------------------------------------------------------------
    // Cas 2 — deux wrappers chaînés : chaque événement observé exactement une fois
    // -----------------------------------------------------------------------

    @Test
    fun chainedWrappersDoNotDuplicate() {
        // A wrappe B wrappe l'original — au niveau des cœurs, avec le câblage exact des
        // wrappers : le forwardKey de A dispatche l'événement ENTIER dans B.
        val topObservations = mutableListOf<AndroidInputObservation>()
        val bottomObservations = mutableListOf<AndroidInputObservation>()
        val terminalForwards = mutableListOf<Triple<Int, Boolean, Int>>()
        val bottom = AndroidInputWindowCallback(
            isTargetFocused = { true },
            onObservation = { bottomObservations += it },
            shouldConsumeKey = { false },
            forwardKey = { code, repeat, meta ->
                terminalForwards += Triple(code, repeat, meta)
                true
            },
            forwardFocusLost = {},
        )
        val top = AndroidInputWindowCallback(
            isTargetFocused = { true },
            onObservation = { topObservations += it },
            shouldConsumeKey = { false },
            forwardKey = { code, repeat, meta ->
                bottom.dispatchKeyEvent(
                    nativeKeyCode = code,
                    repeat = repeat,
                    metaState = meta,
                    unicodeChar = 'a'.code,
                    isSystemKey = false,
                    keyState = KeyState.Pressed,
                )
            },
            forwardFocusLost = {},
        )

        val delivered = top.dispatchKeyEvent(
            nativeKeyCode = KeyEvent.KEYCODE_A,
            repeat = false,
            metaState = 0,
            unicodeChar = 'a'.code,
            isSystemKey = false,
            keyState = KeyState.Pressed,
        )

        assertTrue(delivered, "the chain ends in the wrapped original, which accepted")
        assertEquals(1, topObservations.size, "the top wrapper observed the event exactly once")
        assertEquals(1, bottomObservations.size, "the bottom wrapper observed the event exactly once")
        assertEquals(1, terminalForwards.size, "the original received exactly one dispatch")
        assertEquals(Triple(KeyEvent.KEYCODE_A, false, 0), terminalForwards.single())
        assertIs<AndroidInputObservation.Key>(topObservations.single())
        assertIs<AndroidInputObservation.Key>(bottomObservations.single())
    }

    // -----------------------------------------------------------------------
    // Cas 3 — l'observation n'est livrée que si la cible attachée a le focus
    // -----------------------------------------------------------------------

    @Test
    fun keyDeliveredOnlyWhenTargetFocused() {
        // Sans focus sur la cible : AUCUNE observation — mais le forward a quand même lieu.
        val unfocused = CoreRecorder(targetFocused = false)
        val delivered = unfocused.core.dispatchKeyEvent(
            nativeKeyCode = KeyEvent.KEYCODE_K,
            repeat = false,
            metaState = 0,
            unicodeChar = 'k'.code,
            isSystemKey = false,
            keyState = KeyState.Pressed,
        )
        assertTrue(delivered, "the key is forwarded regardless of the target focus")
        assertEquals(0, unfocused.observations.size, "no observation without the target focus")
        assertEquals(1, unfocused.forwards.size)

        // Avec focus : l'observation porte les cinq primitives telles quelles.
        val focused = CoreRecorder(targetFocused = true)
        focused.core.dispatchKeyEvent(
            nativeKeyCode = KeyEvent.KEYCODE_K,
            repeat = true,
            metaState = KeyEvent.META_CTRL_ON,
            unicodeChar = null,
            isSystemKey = false,
            keyState = KeyState.Released,
        )
        val observed = assertIs<AndroidInputObservation.Key>(focused.observations.single())
        assertEquals(KeyEvent.KEYCODE_K, observed.nativeKeyCode)
        assertTrue(observed.repeat)
        assertEquals(KeyEvent.META_CTRL_ON, observed.metaState)
        assertEquals(null, observed.unicodeChar)
        assertFalse(observed.isSystemKey)
        assertEquals(KeyState.Released, observed.keyState, "the observation carries the press/release of the event")
    }

    // -----------------------------------------------------------------------
    // Cas 4 — touche de scroll consommée sous policy : JAMAIS forwardée
    // -----------------------------------------------------------------------

    @Test
    fun scrollKeyConsumedUnderPolicy() {
        val consuming = CoreRecorder(consumeKeys = true)
        val delivered = consuming.core.dispatchKeyEvent(
            nativeKeyCode = KeyEvent.KEYCODE_PAGE_DOWN,
            repeat = false,
            metaState = 0,
            unicodeChar = null,
            isSystemKey = false,
            keyState = KeyState.Pressed,
        )
        assertTrue(delivered, "a consumed key reports true to the system")
        assertEquals(0, consuming.forwards.size, "a consumed key is NEVER forwarded")
        assertEquals(1, consuming.observations.size, "a consumed key is still observed (web : delivered then suppressed)")

        // Le seam par défaut de la tâche 1 — policy toujours fausse — forward tout.
        val passthrough = CoreRecorder(consumeKeys = false)
        assertTrue(passthrough.core.dispatchKeyEvent(KeyEvent.KEYCODE_PAGE_DOWN, false, 0, null, false, KeyState.Pressed))
        assertEquals(1, passthrough.forwards.size)
    }

    // -----------------------------------------------------------------------
    // Cas 5 — la perte de focus fenêtre est une observation
    // -----------------------------------------------------------------------

    @Test
    fun windowFocusLostObserved() {
        val recorder = CoreRecorder()
        recorder.core.onWindowFocusChanged(hasFocus = true)
        assertEquals(0, recorder.observations.size, "focus GAINED is not an observation")
        assertEquals(0, recorder.focusLostCount, "focus gained is not forwarded as a loss")

        recorder.core.onWindowFocusChanged(hasFocus = false)
        assertEquals(1, recorder.observations.size)
        assertIs<AndroidInputObservation.WindowFocusLost>(recorder.observations.single())
        assertEquals(1, recorder.focusLostCount, "the focus loss is forwarded exactly once")
    }

    // -----------------------------------------------------------------------
    // Cas 6 — les touches système sont forwardées intactes, jamais consommées
    // -----------------------------------------------------------------------

    @Test
    fun systemKeysForwardedUntouched() {
        // Même quand la policy demande une consommation, une touche système passe.
        val recorder = CoreRecorder(consumeKeys = true, forwardResult = true)
        val delivered = recorder.core.dispatchKeyEvent(
            nativeKeyCode = KeyEvent.KEYCODE_BACK,
            repeat = false,
            metaState = 0,
            unicodeChar = null,
            isSystemKey = true,
            keyState = KeyState.Pressed,
        )
        assertTrue(delivered, "a system key is forwarded (its acceptance decides)")
        assertEquals(1, recorder.forwards.size, "a system key is NEVER consumed")
        val observed = assertIs<AndroidInputObservation.Key>(recorder.observations.single())
        assertTrue(observed.isSystemKey, "the observation carries the system flag")
    }

    // -----------------------------------------------------------------------
    // Le wrapper réel — installation, restauration exactes et leurs gardes
    // -----------------------------------------------------------------------

    @Test
    fun restorePutsBackTheOriginalCallbackExactly() {
        val slot = CallbackSlot()
        val wrapped = RecordingCallback()
        val forwarder = forwarder(wrapped, slot)
        assertSame(null, slot.current, "nothing is installed before install()")

        forwarder.install()
        assertSame(forwarder, slot.current, "the wrapper IS the window callback while installed")

        forwarder.restore()
        assertSame(wrapped, slot.current, "teardown restores the ORIGINAL callback instance")
        assertSame(wrapped, forwarder.wrapped)
    }

    @Test
    fun doubleInstallIsRefused() {
        val slot = CallbackSlot()
        val forwarder = forwarder(RecordingCallback(), slot)
        forwarder.install()
        assertFailsWith<IllegalStateException>("a second install without restore is refused") {
            forwarder.install()
        }
        assertSame(forwarder, slot.current, "the refused install left the first wrapper in place")
    }

    @Test
    fun restoreOfAForeignCallbackIsRefused() {
        val slot = CallbackSlot()
        val forwarder = forwarder(RecordingCallback(), slot)
        forwarder.install()
        // Quelqu'un d'autre a remplacé le callback pendant la session — le wrapper n'est
        // plus propriétaire : il refuse et ne touche à rien.
        val foreign = RecordingCallback()
        slot.current = foreign
        assertFailsWith<IllegalStateException>("restoring a non-owned callback is refused") {
            forwarder.restore()
        }
        assertSame(foreign, slot.current, "the foreign callback is never overwritten")
    }

    @Test
    fun restoreWithoutInstallIsRefused() {
        val slot = CallbackSlot()
        val forwarder = forwarder(RecordingCallback(), slot)
        assertFailsWith<IllegalStateException> { forwarder.restore() }
        assertSame(null, slot.current)
    }

    @Test
    fun focusLostIsForwardedExactlyOnceToTheWrappedCallback() {
        val slot = CallbackSlot()
        val wrapped = RecordingCallback()
        val observations = mutableListOf<AndroidInputObservation>()
        val forwarder = forwarder(wrapped, slot, observations = observations)
        forwarder.install()

        forwarder.onWindowFocusChanged(hasFocus = false)
        assertEquals(listOf(false), wrapped.focusChanges, "exactly one focus call reached the wrapped callback")
        assertIs<AndroidInputObservation.WindowFocusLost>(observations.single())

        forwarder.onWindowFocusChanged(hasFocus = true)
        assertEquals(listOf(false, true), wrapped.focusChanges, "focus gained is forwarded untouched too")
        assertEquals(1, observations.size, "focus gained is still not an observation")
    }

    @Test
    fun nonKeyCallbacksAreForwardedUntouched() {
        val slot = CallbackSlot()
        val wrapped = RecordingCallback()
        val forwarder = forwarder(wrapped, slot)
        forwarder.install()

        forwarder.onContentChanged()
        forwarder.onAttachedToWindow()
        forwarder.onDetachedFromWindow()

        assertEquals(listOf("onContentChanged", "onAttachedToWindow", "onDetachedFromWindow"), wrapped.calls)
    }

    // -----------------------------------------------------------------------
    // Outils de test — aucun type SDK constructible n'est instancié
    // -----------------------------------------------------------------------

    /** Un cœur prêt à l'emploi avec journalisation des trois chemins injectés. */
    private class CoreRecorder(
        private val targetFocused: Boolean = true,
        private val consumeKeys: Boolean = false,
        private val forwardResult: Boolean = true,
    ) {
        val observations = mutableListOf<AndroidInputObservation>()
        val forwards = mutableListOf<Triple<Int, Boolean, Int>>()
        var focusLostCount: Int = 0

        val core = AndroidInputWindowCallback(
            isTargetFocused = { targetFocused },
            onObservation = { observations += it },
            shouldConsumeKey = { consumeKeys },
            forwardKey = { code, repeat, meta ->
                forwards += Triple(code, repeat, meta)
                forwardResult
            },
            forwardFocusLost = { focusLostCount++ },
        )
    }

    /** La cellule `window.callback` — la même paire publique get/set que la fenêtre réelle. */
    private class CallbackSlot {
        var current: Window.Callback? = null
        val get: () -> Window.Callback? = { current }
        val set: (Window.Callback?) -> Unit = { current = it }
    }

    /** Un forwarder câblé sur la cellule de test. */
    private fun forwarder(
        wrapped: Window.Callback,
        slot: CallbackSlot,
        targetFocused: () -> Boolean = { true },
        consume: (Int) -> Boolean = { false },
        observations: MutableList<AndroidInputObservation> = mutableListOf(),
    ): AndroidWindowCallbackForwarder = AndroidWindowCallbackForwarder(
        wrapped = wrapped,
        getWindowCallback = slot.get,
        setWindowCallback = slot.set,
        isTargetFocused = targetFocused,
        onObservation = { observations += it },
        shouldConsumeKey = consume,
    )

    /** Un callback fenêtre d'enregistrement : chaque méthode notifie, aucune ne touche le SDK. */
    @Suppress("DEPRECATION", "OverridingDeprecatedMember")
    private class RecordingCallback : Window.Callback {
        val calls = mutableListOf<String>()
        val focusChanges = mutableListOf<Boolean>()
        var dispatchKeyEventResult: Boolean = true

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            calls += "dispatchKeyEvent"
            return dispatchKeyEventResult
        }

        override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean {
            calls += "dispatchKeyShortcutEvent"
            return true
        }

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            calls += "dispatchTouchEvent"
            return true
        }

        override fun dispatchTrackballEvent(event: MotionEvent): Boolean {
            calls += "dispatchTrackballEvent"
            return true
        }

        override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
            calls += "dispatchGenericMotionEvent"
            return true
        }

        override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent?): Boolean {
            calls += "dispatchPopulateAccessibilityEvent"
            return true
        }

        override fun onActionModeStarted(mode: ActionMode?) {
            calls += "onActionModeStarted"
        }

        override fun onActionModeFinished(mode: ActionMode?) {
            calls += "onActionModeFinished"
        }

        override fun onCreatePanelView(featureId: Int): View? {
            calls += "onCreatePanelView"
            return null
        }

        override fun onCreatePanelMenu(featureId: Int, menu: Menu): Boolean {
            calls += "onCreatePanelMenu"
            return true
        }

        override fun onPreparePanel(featureId: Int, view: View?, menu: Menu): Boolean {
            calls += "onPreparePanel"
            return true
        }

        override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
            calls += "onMenuOpened"
            return true
        }

        override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean {
            calls += "onMenuItemSelected"
            return true
        }

        override fun onWindowAttributesChanged(attrs: WindowManager.LayoutParams?) {
            calls += "onWindowAttributesChanged"
        }

        override fun onContentChanged() {
            calls += "onContentChanged"
        }

        override fun onWindowFocusChanged(hasFocus: Boolean) {
            calls += "onWindowFocusChanged"
            focusChanges += hasFocus
        }

        override fun onAttachedToWindow() {
            calls += "onAttachedToWindow"
        }

        override fun onDetachedFromWindow() {
            calls += "onDetachedFromWindow"
        }

        override fun onPanelClosed(featureId: Int, menu: Menu) {
            calls += "onPanelClosed"
        }

        override fun onSearchRequested(): Boolean {
            calls += "onSearchRequested"
            return false
        }

        override fun onSearchRequested(searchEvent: SearchEvent?): Boolean {
            calls += "onSearchRequested(SearchEvent)"
            return false
        }

        override fun onWindowStartingActionMode(callback: ActionMode.Callback?): ActionMode? {
            calls += "onWindowStartingActionMode"
            return null
        }

        override fun onWindowStartingActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
            calls += "onWindowStartingActionMode(type)"
            return null
        }

        override fun onProvideKeyboardShortcuts(
            groups: MutableList<KeyboardShortcutGroup>?,
            menu: Menu?,
            deviceId: Int,
        ) {
            calls += "onProvideKeyboardShortcuts"
        }

        override fun onPointerCaptureChanged(hasCapture: Boolean) {
            calls += "onPointerCaptureChanged"
        }
    }
}
