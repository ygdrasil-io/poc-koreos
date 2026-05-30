/**
 * Boucle d'événements Web via requestAnimationFrame.
 *
 * ## Comportement par mode [ControlFlow]
 * - [ControlFlow.Wait]      : la boucle attend un événement DOM avant de déclencher une frame.
 *                             Le prochain RAF est planifié uniquement lorsqu'un événement
 *                             arrive via [WebDomBridge.onWindowEvent].
 * - [ControlFlow.Poll]      : RAF continu — une nouvelle frame est planifiée à chaque tick.
 * - [ControlFlow.WaitUntil] : setTimeout jusqu'à l'instant cible, puis RAF unique.
 *
 * ## Contrainte webMain
 * Ce fichier réside dans `webMain` — AUCUN import DOM n'est autorisé ici.
 * La planification effective des RAF est déléguée aux sous-classes dans
 * `jsMain` ([JsWebEventLoop]) et `wasmJsMain` ([WasmJsWebEventLoop]).
 *
 * ## Cycle de vie
 * ```
 * runApp(handler)
 *   └─► handler.resumed(this)
 *   └─► handler.newEvents(this, StartCause.Init)
 *   └─► handler.aboutToWait(this)
 *   └─► scheduleNextFrame(handler)    ← RAF planifié
 *         └─► tick(handler)           ← rappelé par le navigateur
 *               ├─ handler.newEvents(...)
 *               ├─ dispatch des événements DOM accumulés
 *               ├─ handler.aboutToWait(this)
 *               └─ scheduleNextFrame(handler)  ← RAF suivant (si !isExiting)
 * ```
 *
 * @since 0.1.0
 */
package io.ygdrasil.koreos.web

import io.ygdrasil.koreos.core.ActiveEventLoop
import io.ygdrasil.koreos.core.ApplicationHandler
import io.ygdrasil.koreos.core.ControlFlow
import io.ygdrasil.koreos.core.EventLoopProxy
import io.ygdrasil.koreos.core.StartCause
import io.ygdrasil.koreos.core.Window
import io.ygdrasil.koreos.core.WindowAttributes
import io.ygdrasil.koreos.core.WindowId

/**
 * Boucle d'événements Web partagée entre les cibles JS et wasmJs.
 *
 * Cette classe est abstraite : les sous-classes concrètes ([JsWebEventLoop] et
 * [WasmJsWebEventLoop]) fournissent l'accès à `window.requestAnimationFrame`
 * via les API spécifiques à leur cible.
 *
 * ## Thread safety
 * JavaScript est mono-thread ; les appels depuis `wakeUp()` sont synchrones.
 */
open class WebEventLoop : ActiveEventLoop {

    // -------------------------------------------------------------------------
    // État interne
    // -------------------------------------------------------------------------

    private var _controlFlow: ControlFlow = ControlFlow.Wait
    private var _isExiting = false

    /** Liste des fenêtres actives créées par cette boucle. */
    private val windows = mutableListOf<WebWindow>()

    /** File des événements DOM reçus entre deux frames. */
    private val pendingEvents = mutableListOf<Pair<WindowId, WebWindowEvent>>()

    // -------------------------------------------------------------------------
    // ActiveEventLoop
    // -------------------------------------------------------------------------

    override val controlFlow: ControlFlow get() = _controlFlow

    override fun setControlFlow(controlFlow: ControlFlow) {
        _controlFlow = controlFlow
    }

    override val isExiting: Boolean get() = _isExiting

    override fun exit() {
        _isExiting = true
    }

    /**
     * Crée une fenêtre web et l'attache au DOM via [JsWebDomBridge] ou [WasmJsWebDomBridge].
     *
     * Le pont DOM est instancié par [createDomBridge] (override dans les sous-classes
     * concrètes pour injecter l'implémentation JS ou wasmJs appropriée).
     *
     * @param attributes Paramètres de configuration de la fenêtre.
     * @return La [WebWindow] créée et attachée.
     */
    override fun createWindow(attributes: WindowAttributes): Window {
        val bridge = createDomBridge()
        bridge.onWindowEvent = { event ->
            val win = windows.firstOrNull { it.rawWindowHandle.let { true } }
            // On enfile l'événement pour dispatch lors de la prochaine frame
            pendingEvents.add(Pair(windows.firstOrNull()?.id ?: WindowId(0L), event))
            // En mode Wait, on réveille la boucle immédiatement
            if (_controlFlow is ControlFlow.Wait) {
                scheduleWakeUp()
            }
        }
        val window = WebWindow(attributes, bridge)
        windows.add(window)
        return window
    }

    /**
     * Crée un proxy thread-safe vers cette boucle d'événements.
     *
     * En JavaScript (mono-thread), le proxy appelle simplement [scheduleWakeUp].
     */
    override fun createProxy(): EventLoopProxy = object : EventLoopProxy {
        override fun wakeUp() = scheduleWakeUp()
    }

    // -------------------------------------------------------------------------
    // Point d'entrée public
    // -------------------------------------------------------------------------

    /**
     * Démarre la boucle d'événements et notifie le gestionnaire.
     *
     * Appelle [ApplicationHandler.resumed], puis [ApplicationHandler.canCreateSurfaces]
     * (le navigateur autorise la création de surfaces dès le démarrage), puis
     * [ApplicationHandler.newEvents] avec [StartCause.Init], puis
     * [ApplicationHandler.aboutToWait], et enfin planifie la première frame via
     * [scheduleNextFrame].
     *
     * @param handler Gestionnaire du cycle de vie de l'application.
     */
    open fun runApp(handler: ApplicationHandler) {
        handler.resumed(this)
        // Sur le web, le canvas est disponible immédiatement : on autorise tout de
        // suite la création de surfaces (parité avec les boucles desktop AppKit/Win32).
        handler.canCreateSurfaces(this)
        handler.newEvents(this, StartCause.Init)
        handler.aboutToWait(this)
        if (!_isExiting) {
            scheduleNextFrame(handler)
        }
    }

    // -------------------------------------------------------------------------
    // Tick interne — appelé par RAF
    // -------------------------------------------------------------------------

    /**
     * Exécute une itération de la boucle d'événements.
     *
     * Appelé par `requestAnimationFrame` dans les sous-classes concrètes.
     * Distribue les événements DOM accumulés, puis planifie la prochaine frame
     * si la boucle n'est pas en cours d'arrêt.
     *
     * @param handler Gestionnaire du cycle de vie.
     * @param now     Timestamp fourni par requestAnimationFrame (en ms, ignoré ici).
     */
    protected fun tick(handler: ApplicationHandler, now: Double = 0.0) {
        if (_isExiting) return

        // Détermine la cause de démarrage de cette itération
        val cause: StartCause = when (val cf = _controlFlow) {
            is ControlFlow.Poll        -> StartCause.Poll
            is ControlFlow.Wait        -> StartCause.WaitCancelled()
            is ControlFlow.WaitUntil   -> StartCause.ResumeTimeReached(
                requestedResume = cf.instant,
                start = now.toLong()
            )
        }

        handler.newEvents(this, cause)

        // Dispatch des événements DOM accumulés
        val snapshot = pendingEvents.toList()
        pendingEvents.clear()
        for ((windowId, event) in snapshot) {
            handler.windowEvent(this, windowId, event)
        }

        handler.aboutToWait(this)

        // Planifie la frame suivante selon le mode courant
        if (!_isExiting) {
            scheduleNextFrame(handler)
        } else {
            // Notifie le handler de la fin imminente
            handler.suspended(this)
        }
    }

    // -------------------------------------------------------------------------
    // Méthodes extensibles par les sous-classes
    // -------------------------------------------------------------------------

    /**
     * Planifie la prochaine frame selon le [ControlFlow] courant.
     *
     * - [ControlFlow.Poll]      → `requestAnimationFrame` immédiat
     * - [ControlFlow.Wait]      → attend un événement DOM ([scheduleWakeUp] planifiera le RAF)
     * - [ControlFlow.WaitUntil] → `setTimeout` jusqu'à l'instant cible, puis RAF
     *
     * Les sous-classes concrètes doivent fournir `requestAnimationFrame`
     * et `setTimeout` via les mécanismes propres à leur cible (JS ou wasmJs).
     *
     * Cette méthode est `open` pour permettre l'override dans les tests.
     */
    protected open fun scheduleNextFrame(handler: ApplicationHandler) {
        // Stub : l'implémentation concrète est fournie par JsWebEventLoop / WasmJsWebEventLoop
        // via requestAnimationFrame dans jsMain / wasmJsMain.
    }

    /**
     * Planifie un réveil immédiat de la boucle (RAF unique).
     *
     * Utilisé en mode [ControlFlow.Wait] lorsqu'un événement DOM arrive,
     * et par [createProxy] pour réveiller la boucle depuis un autre contexte.
     *
     * Stub — surchargé dans les sous-classes pour appeler `requestAnimationFrame`.
     */
    protected open fun scheduleWakeUp() {
        // Stub : surchargé dans JsWebEventLoop / WasmJsWebEventLoop
    }

    /**
     * Crée le pont DOM approprié à la cible de compilation.
     *
     * Surchargé dans [JsWebEventLoop] pour retourner [JsWebDomBridge],
     * et dans [WasmJsWebEventLoop] pour retourner [WasmJsWebDomBridge].
     *
     * L'implémentation par défaut retourne un pont no-op (utile pour les tests).
     */
    protected open fun createDomBridge(): WebDomBridge = object : WebDomBridge {
        override var onWindowEvent: ((WebWindowEvent) -> Unit)? = null
        override fun attach(targetElementId: String) {}
        override fun detach() {}
    }
}
