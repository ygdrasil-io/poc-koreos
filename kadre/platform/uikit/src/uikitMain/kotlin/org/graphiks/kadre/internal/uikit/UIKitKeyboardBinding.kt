@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.graphiks.kadre.internal.uikit

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.SurfaceStimulus
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.surface.SurfaceId
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.GameController.GCKeyboard
import platform.GameController.GCKeyboardDidConnectNotification
import platform.GameController.GCKeyboardDidDisconnectNotification

/**
 * Le branchement du clavier physique (phase 5, Task 3) : la route GameController du plan
 * (ruling pré-vol — additive, notification-driven, SANS responder chain ni `pressesBegan`).
 * Le klib ne connaît pas de `GCKey` porteur de `usagePage`/`usageCode`/`characters` (le SDK
 * iPhoneOS27.0 n'a pas de tel protocole — vérifié header par header) ; la source honnête est la
 * closure `GCKeyboardInput.keyChangedHandler`, dont la signature klib vérifiée au compile est
 * `(GCKeyboardInput?, GCControllerButtonInput?, GCKeyCode?, Boolean)` — le CODE brut est le
 * troisième paramètre, l'appui le quatrième.
 *
 * VIE (la vérité du SDK, GCKeyboard.h) : tous les claviers connectés sont COALESCÉS en un seul
 * objet `GCKeyboard` (`coalescedKeyboard`), et les notifications ne sont livrées qu'UNE fois —
 * connect au premier clavier, disconnect au DERNIER. La question multi-claviers du brief se
 * répond donc d'elle-même : la source EST déjà coalescée, le binding pose le handler sur LE
 * clavier coalescé et l'annonce `keyboardInstalled` est le booléen unique « une source clavier
 * est attachée ». Le scan initial est `GCKeyboard.coalescedKeyboard` — PAS
 * `GCController.controllers()`, qui ne contient pas les claviers (ce sont des `GCKeyboard`, pas
 * des `GCController` ; écart au brief, dicté par les headers).
 *
 * HONNÊTETÉ DU PAYLOAD (deux limites documentées, jamais masquées) :
 * - **Clé logique.** `GCControllerButtonInput` ne rapporte AUCUN `characters` (la chaîne
 *   produite est une notion d'UIKeyInput, pas de GameController — le header de `GCKeyboardInput`
 *   renvoie d'ailleurs explicitement à UIKit/AppKit pour le texte). La clé logique est donc
 *   `uikitLogicalKey(null)` → `Unidentified(null)` : la clé physique (paire HID de la page 0x07,
 *   cf. [uikitKeyboardPhysicalKey]) est porte TELLE QUELLE, les caractères attendent la route
 *   text-input de la phase 6 — jamais inventés.
 * - **Modificateurs.** GCKeyboard n'a PAS d'équivalent FlagsChanged : les touches modificatrices
 *   arrivent comme des touches ordinaires (`GCKeyCodeLeftShift`…), sans traceur d'état. Le
 *   suivi des modificateurs est DIFFÉRÉ : l'ensemble publié est vide
 *   ([uikitKeyboardModifiers] sur six faux — exactement ce que la source rapporte comme
 *   modificateurs TRACÉS : rien), jamais un ensemble par défaut inventé.
 *
 * ANNONCE : le binding écrit SON fait dans l'agrégat du port ([UIKitInputAnnouncement]) et
 * publie le snapshot complet — au scan initial trouvé (keyboard=true), à la notification de
 * connexion (true) et de déconnexion (false), au teardown (false). APRÈS [teardown], rien
 * n'agit : les observers sont retirés, un handler déjà posé est remis à `null`, et toute
 * livraison déjà en file est rejetée par la garde [emit] (pattern Stale). Idempotent, file
 * principale uniquement (l'install du port EST sur le main ; les notifications arrivent sur la
 * main queue — la closure GameController est appelée sur la file de la session, qui est la
 * principale pour un app UIKit).
 */
internal class UIKitKeyboardBinding(
    private val surfaceId: SurfaceId,
    private val announcement: UIKitInputAnnouncement,
    private val publish: (SurfaceStimulus) -> Boolean,
) {
    private val lock = RuntimeLock()
    private var tornDown: Boolean = false
    private var connectToken: Any? = null
    private var disconnectToken: Any? = null
    private var sourceAttached: Boolean = false

    /**
     * Pose les DEUX observers de connexion (D'ABORD — une connexion qui courrait l'install ne
     * doit pas être manquée), puis scanne le clavier coalesqué. Ordre de garde (miroir
     * touch-binding, revue M1) : la garde `tornDown` et le tripwire de double-installation
     * passent avant toute inscription ; un teardown glissé entre l'inscription et son
     * enregistrement est absorbé par la re-lecture (les observers fraîchement posés sont
     * retirés dans la foulée). Toute exception remonte à l'appelant (le port enveloppe dans
     * `runCatching` → rapport, sans jamais casser l'attach).
     */
    fun install() {
        lock.withLock {
            if (tornDown) return
            check(connectToken == null) { "duplicate UIKit keyboard binding install" }
        }
        val connect = NSNotificationCenter.defaultCenter.addObserverForName(
            name = GCKeyboardDidConnectNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue(),
        ) { _ -> onConnect() }
        val disconnect = NSNotificationCenter.defaultCenter.addObserverForName(
            name = GCKeyboardDidDisconnectNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue(),
        ) { _ -> onDisconnect() }
        val live = lock.withLock {
            if (tornDown) {
                false
            } else {
                connectToken = connect
                disconnectToken = disconnect
                true
            }
        }
        if (!live) {
            // Un teardown a passé le sien entre l'inscription et l'enregistrement : son propre
            // retrait est tombé sur des tokens pas encore enregistrés (no-op), le nôtre achève.
            NSNotificationCenter.defaultCenter.removeObserver(connect)
            NSNotificationCenter.defaultCenter.removeObserver(disconnect)
            return
        }
        UIKitKeyboardCount.recordInstall()
        if (attachToConnectedKeyboard()) {
            UIKitKeyboardCount.recordAnnouncement()
            publish(announcement.keyboard(true))
        }
    }

    /**
     * Retrait : observers retirés, handler remis à `nil` si une source était attachée, annonce
     * de révocation (le snapshot complet, fait clavier à faux). Idempotent.
     */
    fun teardown() {
        val stale = lock.withLock {
            if (tornDown) {
                null
            } else {
                tornDown = true
                Triple(connectToken, disconnectToken, sourceAttached).also {
                    connectToken = null
                    disconnectToken = null
                    sourceAttached = false
                }
            }
        } ?: return
        stale.first?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        stale.second?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        if (stale.third) {
            detachHandler()
        }
        UIKitKeyboardCount.recordTeardown()
        UIKitKeyboardCount.recordAnnouncement()
        publish(announcement.keyboard(false))
    }

    /** Connexion : attacher le handler au clavier coalesqué et l'annoncer. */
    private fun onConnect() {
        if (lock.withLock { tornDown }) return
        if (!attachToConnectedKeyboard()) return
        UIKitKeyboardCount.recordConnect()
        UIKitKeyboardCount.recordAnnouncement()
        publish(announcement.keyboard(true))
    }

    /** Déconnexion : la source coalescée n'est plus — révoquer le fait. */
    private fun onDisconnect() {
        val wasAttached = lock.withLock {
            if (tornDown) {
                null
            } else {
                val was = sourceAttached
                sourceAttached = false
                was
            }
        } ?: return
        if (!wasAttached) return
        detachHandler()
        UIKitKeyboardCount.recordAnnouncement()
        publish(announcement.keyboard(false))
    }

    /**
     * La pose du handler sur LE clavier coalesqué : `true` quand une source est attachée, faux
     * quand aucun clavier n'est connecté (le handler sera posé par la notification de
     * connexion) ou quand un teardown a gagné la course (le handler fraîchement posé est remis
     * à `nil` dans la foulée — miroir du re-attach du touch-binding).
     */
    private fun attachToConnectedKeyboard(): Boolean {
        val input = GCKeyboard.coalescedKeyboard?.keyboardInput ?: return false
        input.keyChangedHandler = { _, _, keyCode, pressed -> emit(keyCodeRaw = keyCode, pressed = pressed) }
        val live = lock.withLock {
            if (tornDown) {
                false
            } else {
                sourceAttached = true
                true
            }
        }
        if (!live) {
            input.keyChangedHandler = null
            return false
        }
        return true
    }

    /** Le handler off (le teardown du handler posé — no-op s'il n'y a plus d'objet source). */
    private fun detachHandler() {
        GCKeyboard.coalescedKeyboard?.keyboardInput?.keyChangedHandler = null
    }

    /**
     * La route de publication d'une touche : la garde de teardown d'abord (une livraison déjà
     * en file au moment du retrait est rejetée ICI aussi — double garde avec la remise à `nil`
     * du handler), puis le kernel pur ([uikitKeyboardPhysicalKey] pour la paire HID,
     * [uikitKeyState] pour l'état), puis le sink.
     */
    private fun emit(keyCodeRaw: Long?, pressed: Boolean) {
        if (lock.withLock { tornDown }) return
        UIKitKeyboardCount.recordEvent()
        val stimulus = SurfaceStimulus.KeyChanged(
            surfaceId = surfaceId,
            physicalKey = uikitKeyboardPhysicalKey(keyCodeRaw),
            // La clé logique honnête : AUCUN characters n'existe côté GameController (KDoc de
            // classe) — Unidentified(null), les caractères attendent la phase 6.
            logicalKey = uikitLogicalKey(null),
            location = KeyLocation.Standard,
            keyState = uikitKeyState(pressed),
            // GCKeyboard ne rapporte pas de répétition : chaque livraison est un fait brut
            // appui/relâchement, `repeat=false` est la vérité de la source.
            repeat = false,
            // Aucun traceur de modificateurs côté GCKeyboard (KDoc de classe) : ensemble VIDE,
            // le suivi est différé — jamais un ensemble par défaut.
            modifiers = uikitKeyboardModifiers(
                shift = false,
                control = false,
                alt = false,
                meta = false,
                capsLock = false,
                numLock = false,
            ),
            deviceId = null,
        )
        UIKitKeyboardCount.recordStimulus()
        publish(stimulus)
    }
}

/**
 * Le compteur process-wide du clavier (le proxy du driver, pattern [UIKitTouchCount]) : les
 * installations/retraits du binding, les connexions de source réellement acceptées
 * ([recordConnect] — le handler posé sur le clavier coalesqué), les livraisons de touches
 * ACCEPTÉES ([recordEvent]), les stimuli `KeyChanged` remis au sink ([recordStimulus]) et les
 * annonces ([recordAnnouncement]). La lecture du driver passe par la sonde — Task 4 prouvera la
 * vraie touche par l'incrémentation du compteur d'events sur le charter manuel.
 */
internal object UIKitKeyboardCount {
    private val lock = RuntimeLock()
    private var installs: Long = 0L
    private var teardowns: Long = 0L
    private var connects: Long = 0L
    private var events: Long = 0L
    private var stimuli: Long = 0L
    private var announcements: Long = 0L

    fun recordInstall() {
        lock.withLock { installs += 1L }
    }

    fun recordTeardown() {
        lock.withLock { teardowns += 1L }
    }

    fun recordConnect() {
        lock.withLock { connects += 1L }
    }

    fun recordEvent() {
        lock.withLock { events += 1L }
    }

    fun recordStimulus() {
        lock.withLock { stimuli += 1L }
    }

    /** Une publication `InputObservationChanged` déclenchée par CE binding. */
    fun recordAnnouncement() {
        lock.withLock { announcements += 1L }
    }

    fun totalInstalls(): Long = lock.withLock { installs }

    fun totalTeardowns(): Long = lock.withLock { teardowns }

    fun totalConnects(): Long = lock.withLock { connects }

    fun totalEvents(): Long = lock.withLock { events }

    fun totalStimuli(): Long = lock.withLock { stimuli }

    fun totalAnnouncements(): Long = lock.withLock { announcements }
}
