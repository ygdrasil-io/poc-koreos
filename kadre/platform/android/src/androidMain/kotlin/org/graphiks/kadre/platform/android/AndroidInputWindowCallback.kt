package org.graphiks.kadre.platform.android

import android.os.Build
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

/**
 * Les observations copiées de la couche SDK — SDK-free par construction : le wrapper ne
 * livre jamais un type `android.*` à la session, seulement ces valeurs primitives.
 *
 * - [Key] : une touche matérielle vue à [Window.Callback.dispatchKeyEvent], les primitives
 *   telles quelles — `unicodeChar` est `null` quand la plateforme ne rapporte pas de
 *   caractère fiable (l'absence reste une absence, jamais un `'\u0000'` inventé) ;
 *   [Key][Key.keyState] porte le press/release de l'action (`ACTION_UP` → `Released`, toute
 *   autre action — `DOWN`, `MULTIPLE` — → `Pressed`), ajouté par la Task 3 : la livraison au
 *   réducteur ne peut pas inventer l'état qu'un `KeyChanged` exige, et un press éternellement
 *   collé dans l'état du clavier serait un mensonge d'état. Depuis la phase 7, [Key][Key.source]
 *   porte le bitmask `source` de l'événement et [Key][Key.deviceId] l'id du device source — les
 *   deux primitives que le routage gamepad de la delivery lit pour sortir les events
 *   gamepad/joystick du chemin ordinaire ; un event construit sans elles (les observations
 *   des tests, source 0) reste sur le chemin ordinaire, exactement le comportement pré-existant.
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
        val keyState: KeyState,
        /** Le bitmask `source` du `KeyEvent` — les flags gamepad/joystick de la livraison. */
        val source: Int = 0,
        /** L'id du device source — la clé avec laquelle le port gamepad nomme sa manette. */
        val deviceId: Int = 0,
    ) : AndroidInputObservation

    public data object WindowFocusLost : AndroidInputObservation
}

/**
 * Les décisions du wrapper, testées pur : observer puis forwarder, ou consommer sous
 * policy. Aucun type SDK — les cinq primitives d'une touche entrent, une décision sort ;
 * le wrapper réel ([AndroidWindowCallbackForwarder]) n'est que la plomberie
 * `Window.Callback` autour de cette classe.
 *
 * Le contrat complet de [dispatchKeyEvent] — la CONSOMMATION vit sous la MÊME porte de
 * focus que l'observation : une touche destinée à une AUTRE vue focalisée n'est ni
 * observée ni consommée, le host la reçoit entière (l'invariant « delivered then
 * suppressed … reste OBSERVÉE » ne parle que d'une touche dont la vue attachée EST la
 * destinataire focalisée ; consommer hors focus avalerait au host une touche qui ne
 * regarde pas la surface) :
 * 1. la vue attachée PORTE le focus ([isTargetFocused] — `view.hasFocus()` au moment de
 *    l'événement) → la touche est observée ([onObservation] avec
 *    [AndroidInputObservation.Key]) PUIS la consommation décide : une touche système
 *    ([isSystemKey]) n'est JAMAIS consommée — forwardée intacte ; sinon la policy répond
 *    ([shouldConsumeKey] — câblée par la tâche 4 à l'état COMMIS de la surface :
 *    [AndroidInputTranslation.shouldSuppress], exactement les scroll-keys documentaires
 *    sous `SuppressWhenPossible`, rien sous `HostDefault`) : `true` consomme (aucun
 *    forward, l'observation reste livrée — « delivered then suppressed »), `false` laisse
 *    partir (forward) ;
 * 2. la vue attachée N'A PAS le focus → AUCUNE observation, AUCUNE consommation : le
 *    forward SEUL — l'événement est TOUJOURS forwardé quoi qu'il arrive, dans les deux
 *    branches ;
 * 3. le forward est la décision : la valeur de retour de [forwardKey] EST celle rendue au
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
        keyState: KeyState,
        source: Int = 0,
        deviceId: Int = 0,
    ): Boolean {
        // La porte de focus gouverne l'OBSERVATION et la CONSOMMATION ensemble : sans
        // focus, la touche est forwardée seule — jamais avalée au nom d'une policy qui ne
        // concerne pas la vue focalisée d'un autre host.
        val focused = isTargetFocused()
        if (focused) {
            onObservation(
                AndroidInputObservation.Key(
                    nativeKeyCode = nativeKeyCode,
                    repeat = repeat,
                    metaState = metaState,
                    unicodeChar = unicodeChar,
                    isSystemKey = isSystemKey,
                    keyState = keyState,
                    source = source,
                    deviceId = deviceId,
                ),
            )
        }
        if (focused && !isSystemKey && shouldConsumeKey(nativeKeyCode)) return true
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
 * quatre points dévient, tous côté entrée :
 *
 * - [dispatchKeyEvent] : les primitives sont copiées puis remises au cœur
 *   ([AndroidInputWindowCallback]) — l'état press/release est lu sur l'action de l'événement
 *   (`ACTION_UP` → `Released`, toute autre action → `Pressed` ; `ACTION_MULTIPLE` est du
 *   texte qui entre, jamais une relève — sous focus le wrapper publie bien une observation
 *   `Pressed` PRIMITIVE pour elle, que la couche de delivery droppe avant toute dérivation
 *   de stimulus, hors table : `AndroidInputDelivery.kt:209` — jamais un fait texte déduit) ;
 *   le forward réel du `KeyEvent` — celui du callback
 *   wrappé — est le [forwardKey] du cœur, l'événement original voyage entier ;
 * - [onWindowFocusChanged] : la perte passe par le cœur (observation + un seul forward) ;
 * - [dispatchTouchEvent] et [dispatchGenericMotionEvent] : les deux points d'entrée motion —
 *   les primitives de [MotionEvent] sont copiées AVANT le forward (l'événement est recyclé
 *   par le système au retour du dispatch) et remises à [onMotion] avec l'ORIGINE de la View
 *   dans l'espace fenêtre ([viewOffsetInWindow] — `view.getLocationOnScreen()` moins
 *   `decorView.getLocationOnScreen()`, échantillonné AU MOMENT du dispatch par la paire
 *   publique `getLocationOnScreen` ; le dispatch `Window.Callback` livre des coordonnées
 *   fenêtre, les bornes de la View sont parent-relatives, le tracker convertit) ; le
 *   forward, lui, reste intact, tel quel — l'observation n'est jamais une interception ;
 * - [dispatchKeyShortcutEvent], [dispatchTrackballEvent] : forwardés intacts, NON observés —
 *   le trackball est le chemin des périphériques hérités dont le routage de lane (un MOVE
 *   d'outil inconnu est un contact, pas un pointeur) serait une invention ; le chemin
 *   moderne de ces périphériques est le generic, observé lui.
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
    private val onMotion: (AndroidMotionObservation, offsetX: Int, offsetY: Int) -> Unit = { _, _, _ -> },
    /**
     * L'origine de la View DANS l'espace fenêtre, `view.getLocationOnScreen()` moins
     * `decorView.getLocationOnScreen()` — la couture de la conversion view-locale (le tracker
     * soustrait avant ses gates). Le défaut zéro est le cas d'une View collée à l'origine de
     * la fenêtre ; la couture réelle fournit la paire publique.
     */
    private val viewOffsetInWindow: () -> Pair<Int, Int> = { 0 to 0 },
    /**
     * Le canal d'état de la capture (Task 3) : la notification
     * `Window.Callback.onPointerCaptureChanged` du SDK — LA notification d'état que la
     * plateforme offre à une fenêtre (aucun enregistrement de listener de capture n'existe sur
     * `View`, relevé du `android-35`), livrée au seam de la surface
     * ([AndroidViewPointerCaptureNative.dispatchCaptureState] → le listener du seam → le commit
     * en deux temps). Le forward reste intact : l'observation n'est jamais une interception.
     */
    private val onPointerCaptureState: (hasCapture: Boolean) -> Unit = {},
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
                keyState = if (event.action == KeyEvent.ACTION_UP) KeyState.Released else KeyState.Pressed,
                // Les primitives du routage gamepad (phase 7) : le bitmask source et l'id
                // du device — la delivery en sort les events gamepad/joystick du chemin
                // ordinaire vers le port de la session.
                source = event.source,
                deviceId = event.deviceId,
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

    // ---- Les points d'entrée motion : copier les primitives, observer, forwarder intact. ----

    override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean = wrapped.dispatchKeyShortcutEvent(event)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        onMotionWithViewOffset(event)
        return wrapped.dispatchTouchEvent(event)
    }

    override fun dispatchTrackballEvent(event: MotionEvent): Boolean = wrapped.dispatchTrackballEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        onMotionWithViewOffset(event)
        return wrapped.dispatchGenericMotionEvent(event)
    }

    /** L'observation motion avec l'origine de la View échantillonnée AU MOMENT du dispatch. */
    private fun onMotionWithViewOffset(event: MotionEvent) {
        val (offsetX, offsetY) = viewOffsetInWindow()
        onMotion(motionObservation(event), offsetX, offsetY)
    }

    /**
     * La copie des primitives motion AU POINT DE DISPATCH — toutes les lectures SDK ont lieu
     * AVANT que le forward ne rende l'événement au callback wrappé, donc avant que le
     * système ne le recycle : rien de ce qui traverse vers la delivery n'est une référence
     * SDK (le contrat de [AndroidMotionObservation]).
     *
     * La pression est portée telle quelle (le tracker filtre hors domaine — Task 2, F2) ;
     * les axes relatifs (`AXIS_RELATIVE_X/Y`, API 26) sont lus par constante inlinée — un
     * axe inconnu d'une vieille plateforme répond zéro, le zéro honnête.
     *
     * Depuis la phase 7, le bitmask `source` voyage toujours ; les DIX AXES de la table
     * gamepad ([AndroidGamepadMapping.nativeAxes]) ne sont lus que pour une motion source
     * gamepad/joystick — le routage de la delivery les rend au port de la session, et une
     * motion ordinaire (souris, trackpad) ne paie aucune lecture superflue. Les lectures ont
     * lieu ICI, au point de dispatch — l'événement est recyclé au retour.
     */
    private fun motionObservation(event: MotionEvent): AndroidMotionObservation {
        val count = event.pointerCount
        val gamepadSourced = isGamepadSource(event.source)
        return AndroidMotionObservation(
            actionMasked = event.actionMasked,
            actionIndex = event.actionIndex,
            pointerCount = count,
            pointerIds = IntArray(count) { event.getPointerId(it) },
            xs = FloatArray(count) { event.getX(it) },
            ys = FloatArray(count) { event.getY(it) },
            pressures = Array(count) { event.getPressure(it) },
            toolTypes = IntArray(count) { event.getToolType(it) },
            buttons = event.buttonState,
            actionButton = event.actionButton,
            axisHscroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL),
            axisVscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL),
            axisXrelative = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X),
            axisYrelative = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y),
            deviceId = event.deviceId,
            metaState = event.metaState,
            source = event.source,
            gamepadAxes = if (gamepadSourced) {
                AndroidGamepadMapping.nativeAxes.map { axis -> axis to event.getAxisValue(axis) }
            } else {
                emptyList()
            },
        )
    }

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
        // L'observation d'état de la capture (Task 3) : livrée au seam AVANT le forward — le
        // commit en deux temps vit à cette livraison — puis le forward intact. La notification
        // d'état est API 26+ (le minSdk du module est 24) — la convention du module garde
        // chaque primitive au niveau d'API, comme le seam ([AndroidViewPointerCaptureNative]) ;
        // sous 26 la plateforme ne dispatche jamais l'appel, la garde est de cohérence.
        onPointerCaptureState(hasCapture)
        if (Build.VERSION.SDK_INT >= 26) wrapped.onPointerCaptureChanged(hasCapture)
    }
}
