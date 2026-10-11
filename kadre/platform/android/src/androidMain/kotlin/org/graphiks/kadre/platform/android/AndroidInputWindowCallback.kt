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

/**
 * Les observations copiées de la couche SDK — SDK-free par construction : le wrapper ne
 * livre jamais un type `android.*` à la session, seulement ces valeurs primitives.
 *
 * - [Key] : une touche matérielle vue à [Window.Callback.dispatchKeyEvent], les cinq
 *   primitives telles quelles — `unicodeChar` est `null` quand la plateforme ne rapporte
 *   pas de caractère fiable (l'absence reste une absence, jamais un `'\u0000'` inventé).
 * - [WindowFocusLost] : la fenêtre a perdu le focus — l'homologue de l'observation web
 *   `FocusLost`, la seule observée côté fenêtre à cette phase.
 */
internal sealed interface AndroidInputObservation {
    public data class Key(
        val nativeKeyCode: Int,
        val repeat: Boolean,
        val metaState: Int,
        val unicodeChar: Int?,
        val isSystemKey: Boolean,
    ) : AndroidInputObservation

    public data object WindowFocusLost : AndroidInputObservation
}

/**
 * Les décisions du wrapper, testées pur : observer puis forwarder, ou consommer sous
 * policy. Aucun type SDK — les cinq primitives d'une touche entrent, une décision sort ;
 * le wrapper réel ([AndroidWindowCallbackForwarder]) n'est que la plomberie
 * `Window.Callback` autour de cette classe.
 *
 * Le contrat complet de [dispatchKeyEvent] :
 * 1. la touche est observée ([onObservation] avec [AndroidInputObservation.Key]) UNIQUEMENT
 *    si la vue attachée porte le focus ([isTargetFocused] — `view.hasFocus()` au moment de
 *    l'événement) ; l'événement est TOUJOURS forwardé quoi qu'il arrive ;
 * 2. une touche système ([isSystemKey]) n'est JAMAIS consommée — forwardée intacte ;
 * 3. sinon, la policy décide ([shouldConsumeKey] — les scroll-keys, câblé par la tâche 4 ;
 *    le seam de la tâche 1 répond toujours `false`) : `true` consomme (aucun forward),
 *    `false` laisse partir ;
 * 4. le forward est la décision : la valeur de retour de [forwardKey] EST celle rendue au
 *    système.
 *
 * [onWindowFocusChanged] observe la PERTE seule : [onObservation] reçoit
 * [AndroidInputObservation.WindowFocusLost] puis [forwardFocusLost] transmet l'événement.
 * Un gain de focus n'est ni observé ni forwardé par le cœur — le wrapper réel le transmet
 * lui-même, intact.
 */
internal class AndroidInputWindowCallback(
    private val isTargetFocused: () -> Boolean,
    private val onObservation: (AndroidInputObservation) -> Unit,
    private val shouldConsumeKey: (nativeKeyCode: Int) -> Boolean,
    private val forwardKey: (nativeKeyCode: Int, repeat: Boolean, metaState: Int) -> Boolean,
    private val forwardFocusLost: () -> Unit,
) {
    public fun dispatchKeyEvent(
        nativeKeyCode: Int,
        repeat: Boolean,
        metaState: Int,
        unicodeChar: Int?,
        isSystemKey: Boolean,
    ): Boolean {
        if (isTargetFocused()) {
            onObservation(
                AndroidInputObservation.Key(
                    nativeKeyCode = nativeKeyCode,
                    repeat = repeat,
                    metaState = metaState,
                    unicodeChar = unicodeChar,
                    isSystemKey = isSystemKey,
                ),
            )
        }
        if (!isSystemKey && shouldConsumeKey(nativeKeyCode)) return true
        return forwardKey(nativeKeyCode, repeat, metaState)
    }

    public fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) return
        onObservation(AndroidInputObservation.WindowFocusLost)
        forwardFocusLost()
    }
}

/**
 * Le wrapper réel `Window.Callback` : l'observation en CHAÎNE sur le callback existant de
 * la fenêtre — jamais `setOnTouchListener` (slot unique = remplacement silencieux),
 * jamais de réflexion, uniquement la paire publique `getCallback`/`setCallback`.
 *
 * Chaque méthode de [Window.Callback] (y compris les méthodes par défaut) est déléguée
 * TELLE QUELLE au callback wrappé — le wrapper est transparent par construction. Seuls
 * cinq points dévient, tous côté entrée :
 *
 * - [dispatchKeyEvent] : les cinq primitives sont copiées puis remises au cœur
 *   ([AndroidInputWindowCallback]) ; le forward réel du `KeyEvent` — celui du callback
 *   wrappé — est le [forwardKey] du cœur, l'événement original voyage entier ;
 * - [onWindowFocusChanged] : la perte passe par le cœur (observation + un seul forward) ;
 * - [dispatchKeyShortcutEvent], [dispatchTouchEvent], [dispatchTrackballEvent],
 *   [dispatchGenericMotionEvent] : les quatre autres points d'entrée d'événements,
 *   forwardés intacts — le touch est le sujet de la tâche 2, qui étendra
 *   l'interception sans changer ce contrat.
 *
 * L'événement en cours est retenu dans [pendingKeyEvent] le temps du dispatch : le
 * `forwardKey` du cœur ne transporte que des primitives, le `KeyEvent` original est donc
 * rendu au callback wrappé par le fil d'exécution qui dispatche — le fil UI, là où toute
 * la fenêtre est déjà mono-thread.
 *
 * L'installation enchaîne sur le callback existant ([install] pose `this` par-dessus
 * [wrapped]) ; la restauration ([restore]) remet l'instance originale EXACTE. Deux gardes
 * refusent de mentir : un double-install, et une restauration alors que le callback de la
 * fenêtre n'est plus CE wrapper (quelqu'un d'autre l'a remplacé — on n'écrase jamais un
 * callback étranger).
 *
 * La couture [getWindowCallback]/[setWindowCallback] est la paire publique de la fenêtre
 * — en production `window.callback` / `window.callback = it`, testée ici sur une cellule.
 *
 * Le chemin nominal de l'extraction SDK (`keyCode`, `repeatCount`, `metaState`,
 * `getUnicodeChar()`, `isSystem()`) est prouvé sur émulateur par le scénario contractuel
 * (Task 6) — aucun `KeyEvent` n'est constructible hors émulateur, cf. `AndroidViewLeaseTest`.
 */
internal class AndroidWindowCallbackForwarder(
    /** Le callback enchaîné — l'instance exacte que [restore] remet en place. */
    public val wrapped: Window.Callback,
    private val getWindowCallback: () -> Window.Callback?,
    private val setWindowCallback: (Window.Callback?) -> Unit,
    isTargetFocused: () -> Boolean,
    onObservation: (AndroidInputObservation) -> Unit,
    shouldConsumeKey: (nativeKeyCode: Int) -> Boolean,
) : Window.Callback {
    private var installed: Boolean = false
    private var pendingKeyEvent: KeyEvent? = null

    private val core = AndroidInputWindowCallback(
        isTargetFocused = isTargetFocused,
        onObservation = onObservation,
        shouldConsumeKey = shouldConsumeKey,
        forwardKey = { _, _, _ -> forwardPendingKeyEvent() },
        forwardFocusLost = { wrapped.onWindowFocusChanged(false) },
    )

    /** Pose le wrapper PAR-DESSUS le callback existant. Refuse un double-install. */
    public fun install() {
        check(!installed) { "AndroidWindowCallbackForwarder is already installed" }
        check(getWindowCallback() !== this) { "AndroidWindowCallbackForwarder is already the window callback" }
        setWindowCallback(this)
        installed = true
    }

    /** Remet le callback d'origine EXACT. Refuse de toucher à un callback non propriétaire. */
    public fun restore() {
        check(installed) { "AndroidWindowCallbackForwarder is not installed" }
        check(getWindowCallback() === this) {
            "refusing to restore: the window callback is no longer this wrapper — a foreign callback is never overwritten"
        }
        setWindowCallback(wrapped)
        installed = false
    }

    private fun forwardPendingKeyEvent(): Boolean {
        val event = pendingKeyEvent ?: return false
        return wrapped.dispatchKeyEvent(event)
    }

    // ---- Le point d'entrée observé : copier les primitives, décider, forwarder. ----

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val previous = pendingKeyEvent
        pendingKeyEvent = event
        try {
            return core.dispatchKeyEvent(
                nativeKeyCode = event.keyCode,
                repeat = event.repeatCount > 0,
                metaState = event.metaState,
                // `getUnicodeChar()` ne rapporte un caractère que s'il y en a un : le zéro
                // est l'absence de caractère, portée comme `null` et jamais comme '\u0000'.
                unicodeChar = event.getUnicodeChar().takeIf { it != 0 },
                isSystemKey = event.isSystem,
            )
        } finally {
            pendingKeyEvent = previous
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            // Un gain de focus est forwardé intact — le cœur n'observe que la perte.
            wrapped.onWindowFocusChanged(true)
        } else {
            // La perte : le cœur observe puis appelle forwardFocusLost — qui EST le forward
            // de cet appel au callback wrappé. Exactement un appel, jamais deux.
            core.onWindowFocusChanged(false)
        }
    }

    // ---- Les quatre autres points d'entrée d'événements, forwardés intacts. ----

    override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean = wrapped.dispatchKeyShortcutEvent(event)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean = wrapped.dispatchTouchEvent(event)

    override fun dispatchTrackballEvent(event: MotionEvent): Boolean = wrapped.dispatchTrackballEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean = wrapped.dispatchGenericMotionEvent(event)

    // ---- ActionMode du panneau, forwardé intact. ----

    override fun onActionModeStarted(mode: ActionMode?) {
        wrapped.onActionModeStarted(mode)
    }

    override fun onActionModeFinished(mode: ActionMode?) {
        wrapped.onActionModeFinished(mode)
    }

    // ---- Tout le reste de Window.Callback, délégué tel quel (transparence totale). ----

    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent?): Boolean =
        wrapped.dispatchPopulateAccessibilityEvent(event)

    override fun onCreatePanelView(featureId: Int): View? = wrapped.onCreatePanelView(featureId)

    override fun onCreatePanelMenu(featureId: Int, menu: Menu): Boolean = wrapped.onCreatePanelMenu(featureId, menu)

    override fun onPreparePanel(featureId: Int, view: View?, menu: Menu): Boolean =
        wrapped.onPreparePanel(featureId, view, menu)

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean = wrapped.onMenuOpened(featureId, menu)

    override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean =
        wrapped.onMenuItemSelected(featureId, item)

    override fun onWindowAttributesChanged(attrs: WindowManager.LayoutParams?) {
        wrapped.onWindowAttributesChanged(attrs)
    }

    override fun onContentChanged() {
        wrapped.onContentChanged()
    }

    override fun onAttachedToWindow() {
        wrapped.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        wrapped.onDetachedFromWindow()
    }

    override fun onPanelClosed(featureId: Int, menu: Menu) {
        wrapped.onPanelClosed(featureId, menu)
    }

    override fun onSearchRequested(): Boolean = wrapped.onSearchRequested()

    override fun onSearchRequested(searchEvent: SearchEvent?): Boolean = wrapped.onSearchRequested(searchEvent)

    override fun onWindowStartingActionMode(callback: ActionMode.Callback?): ActionMode? =
        wrapped.onWindowStartingActionMode(callback)

    override fun onWindowStartingActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? =
        wrapped.onWindowStartingActionMode(callback, type)

    override fun onProvideKeyboardShortcuts(groups: MutableList<KeyboardShortcutGroup>?, menu: Menu?, deviceId: Int) {
        wrapped.onProvideKeyboardShortcuts(groups, menu, deviceId)
    }

    override fun onPointerCaptureChanged(hasCapture: Boolean) {
        wrapped.onPointerCaptureChanged(hasCapture)
    }
}
