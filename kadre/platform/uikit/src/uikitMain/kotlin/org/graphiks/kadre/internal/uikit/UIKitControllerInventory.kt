@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.graphiks.kadre.internal.uikit

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.GamepadCapabilities
import org.graphiks.kadre.input.GamepadDescriptor
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.input.GamepadEffectConstraints
import org.graphiks.kadre.input.GamepadEffectKind
import org.graphiks.kadre.input.GamepadHapticLocality
import org.graphiks.kadre.input.GamepadLocalizedHapticConstraints
import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.input.GamepadState
import org.graphiks.kadre.input.InputDeviceDescriptor
import org.graphiks.kadre.input.InputDeviceKind
import org.graphiks.kadre.internal.runtime.GamepadPortEffect
import org.graphiks.kadre.internal.runtime.GamepadPortEvent
import org.graphiks.kadre.internal.runtime.GamepadPortGamepad
import org.graphiks.kadre.internal.runtime.GamepadPortRouting
import org.graphiks.kadre.internal.runtime.InputDevicePortDevice
import org.graphiks.kadre.internal.runtime.InputDevicePortEvent
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.policy.GamepadRouting
import platform.CoreHaptics.CHHapticEngine
import platform.CoreHaptics.CHHapticEvent
import platform.CoreHaptics.CHHapticEventParameter
import platform.CoreHaptics.CHHapticEventParameterIDHapticIntensity
import platform.CoreHaptics.CHHapticEventTypeHapticContinuous
import platform.CoreHaptics.CHHapticPattern
import platform.CoreHaptics.CHHapticPatternPlayerProtocol
import platform.CoreHaptics.CHHapticTimeImmediate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.GameController.GCController
import platform.GameController.GCControllerAxisInput
import platform.GameController.GCControllerButtonInput
import platform.GameController.GCControllerDidConnectNotification
import platform.GameController.GCControllerDidDisconnectNotification
import platform.GameController.GCControllerElement
import platform.GameController.GCDeviceHaptics
import platform.GameController.GCExtendedGamepad
import platform.GameController.GCHapticsLocalityAll
import platform.GameController.GCHapticsLocalityDefault
import platform.GameController.GCHapticsLocalityHandles
import platform.GameController.GCHapticsLocalityLeftHandle
import platform.GameController.GCHapticsLocalityLeftTrigger
import platform.GameController.GCHapticsLocalityRightHandle
import platform.GameController.GCHapticsLocalityRightTrigger
import platform.GameController.GCHapticsLocalityTriggers
import platform.GameController.GCKeyboard
import platform.GameController.GCKeyboardDidConnectNotification
import platform.GameController.GCKeyboardDidDisconnectNotification
import platform.GameController.GCMicroGamepad
import platform.GameController.GCMouse
import platform.GameController.GCMouseDidConnectNotification
import platform.GameController.GCMouseDidDisconnectNotification
import platform.darwin.NSObjectProtocol

/**
 * Les faits PRÉ-EXTRAITS d'UN contrôleur (manette `GCController`, clavier coalescé
 * `GCKeyboard` ou souris `GCMouse`) — le pattern jeton du module : le port ne consomme QUE
 * ces scalaires, JAMAIS un type SDK. Les booléens d'identité nourrissent le kernel
 * [uikitGenericDeviceKindOf] (carve-out gamepad d'abord), les lectures d'éléments nourrissent
 * le descripteur gelé ([uikitGamepadDescriptorOf]) et l'état vivant
 * ([uikitGamepadStateOf]), les localités haptiques gelent les capabilities d'effet.
 *
 * `buttonReadings`/`axisReadings` ne portent QUE les éléments PRÉSENTS au profil, nommés
 * selon les tables fermées du kernel ([UIKIT_GAMEPAD_BUTTON_TABLE]/[UIKIT_GAMEPAD_AXIS_TABLE])
 * — un élément absent reste absent, jamais une lecture fabriquée.
 */
internal data class UIKitControllerFacts(
    val isExtendedGamepad: Boolean = false,
    val isMicroGamepad: Boolean = false,
    val isKeyboard: Boolean = false,
    val isMouse: Boolean = false,
    val buttonReadings: Map<String, UIKitButtonReading> = emptyMap(),
    val axisReadings: Map<String, Double> = emptyMap(),
    val hapticLocalities: Set<GamepadHapticLocality> = emptySet(),
)

/**
 * Le seam SDK de l'inventaire contrôleurs (phase 8, Task 4) — le pattern jeton
 * d'[UIKitDisplaySource] : les jetons `Any` entrent (manettes, clavier coalescé, souris),
 * les faits sortent ; la traduction est prouvée dans `uikitTest` sans AUCUN contrôleur réel.
 *
 * Le port relit TOUJOURS l'inventaire COMPLET ([controllers]) — un fire de [registerListener]
 * ne porte JAMAIS un delta, il est le signal « relis tout maintenant » (une notification de
 * plus ou de moins, un élément qui a changé — peu importe : la relecture fait foi).
 *
 * `profileFacts` est le POINT D'ARMEMENT de la réalisation production : c'est en lisant les
 * éléments d'une manette que celle-ci (re)branche ses handlers de valeur sur le listener
 * courant — la lecture EST l'armement (voir [UIKitGCControllerSource.profileFacts]).
 */
internal interface UIKitControllerSource : AutoCloseable {
    /** L'inventaire COMPLET du process (`GCController.controllers()` + clavier + souris). */
    public fun controllers(): List<Any>

    /** Enregistre [listener] pour chaque moment où le système signale un fait nouveau. */
    public fun registerListener(listener: () -> Unit)

    /** Retire exactement l'instance [listener] donnée à [registerListener]. */
    public fun unregisterListener(listener: () -> Unit)

    /** La lecture SDK brute d'un jeton de [controllers] — `null` si le jeton n'est pas lisible. */
    public fun profileFacts(controller: Any): UIKitControllerFacts?

    /** Le nom honnête du jeton (`vendorName`/`productCategory`), ou `null` — jamais fabriqué. */
    public fun nameOf(controller: Any): String?

    /** La manette porte-t-elle un profil étendu (une « vraie » manette du port manette) ? */
    public fun isExtendedGamepad(controller: Any): Boolean

    /** Le jeton est-il une micro-manette SANS profil étendu (le Siri Remote) ? */
    public fun isMicroGamepadOnly(controller: Any): Boolean
}

/**
 * Le propriétaire natif d'UN effet démarré — le miroir `AppKitGameControllerNativeEffect` :
 * [requestStop] demande l'arrêt et rapporte un échec SANS lancer, [close] est IDEMPOTENT (le
 * teardown peut suivre un stop explicite). L'implémentation réelle garde son arrêt par
 * `AtomicBoolean` : ni stop ni close ne touche le natif deux fois.
 */
internal interface UIKitGamepadEffectHandle : AutoCloseable {
    public fun requestStop(): KadreResult<Unit>
    override fun close()
}

/**
 * Le seam haptique de l'inventaire — le chemin de la spike Task 1 (`controller.haptics` →
 * `createEngineWithLocality` → CoreHaptics), injecté pour que l'ADMISSION et le cycle
 * stop/close/teardown soient prouvés dans `uikitTest` sans manette. Les échecs natifs sont
 * des RÉPONSES ([KadreResult.Failure]), jamais des exceptions traversantes.
 */
internal interface UIKitHapticsEffectSeam {
    public fun startEffect(controller: Any, effect: GamepadEffect.LocalizedHaptic): KadreResult<UIKitGamepadEffectHandle>
}

/**
 * Un contrôleur physique VIVANT de l'inventaire — le mosaïque AppKit `PhysicalController` :
 * la clé process-monotonique allouée à la SIGHTING, le nom, le descripteur et les
 * capabilities GELÉS à la connexion (une connexion fige UNE fois — précédent Android ; une
 * reconnexion est une nouvelle sighting qui gèle les siens), l'état qui coule avec les
 * lectures.
 */
private class PhysicalController(
    val token: Any,
    val key: Long,
    val name: String?,
    /** Vrai si la sighting est entrée dans la table manette (profil étendu à la connexion). */
    val extended: Boolean,
    /** Le kind générique gelé de la sighting (le kernel porte la décision). */
    val genericKind: InputDeviceKind,
    facts: UIKitControllerFacts,
) {
    val descriptor: GamepadDescriptor =
        uikitGamepadDescriptorOf(facts.buttonReadings.keys, facts.axisReadings.keys, name)
    val capabilities: GamepadCapabilities = capabilitiesFor(facts.hapticLocalities)
    val hapticLocalities: Set<GamepadHapticLocality> = facts.hapticLocalities

    var state: GamepadState = uikitGamepadStateOf(descriptor, facts.buttonReadings, facts.axisReadings)
        private set

    /** La relecture complète : l'état coule, le descripteur et les capabilities restent gelés. */
    fun refresh(facts: UIKitControllerFacts) {
        state = uikitGamepadStateOf(descriptor, facts.buttonReadings, facts.axisReadings)
    }
}

/**
 * Les capabilities d'effet d'UNE connexion — le miroir AppKit `capabilitiesFor` : les
 * localités que le SDK déclare (`GCDeviceHaptics.supportedLocalities`, le fait « ce
 * contrôleur supporte le haptique ») donnent `Supported(LocalizedHaptic)` sur CES localités ;
 * l'absence du fait donne l'absence honnête `Unsupported(GamepadEffect)` — jamais un support
 * fabriqué. MaximumDuration reste `null` : le runtime possède la durée (contrat du port).
 */
private fun capabilitiesFor(localities: Set<GamepadHapticLocality>): GamepadCapabilities =
    if (localities.isEmpty()) {
        GamepadCapabilities(Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.GamepadEffect)))
    } else {
        GamepadCapabilities(
            Capability.Supported(
                GamepadEffectConstraints(
                    kinds = setOf(GamepadEffectKind.LocalizedHaptic),
                    localizedHaptics = GamepadLocalizedHapticConstraints(localities),
                    maximumDuration = null,
                ),
                FeatureAvailability.Available,
            ),
        )
    }

/** Les livraisons d'UNE relecture — collectées SOUS le lock, livrées HORS du lock. */
private class Pump {
    val gamepads = mutableListOf<GamepadDelivery>()
    val devices = mutableListOf<DeviceDelivery>()
    val interrupted = mutableListOf<UIKitGamepadPortEffect>()

    operator fun plus(other: Pump): Pump = also {
        gamepads += other.gamepads
        devices += other.devices
        interrupted += other.interrupted
    }
}

internal class GamepadDelivery(val observer: (GamepadPortEvent) -> Unit, val event: GamepadPortEvent)
internal class DeviceDelivery(val observer: (InputDevicePortEvent) -> Unit, val event: InputDevicePortEvent)

/**
 * L'inventaire contrôleurs UIKit/tvOS (phase 8, Task 4) — le moule exact du broker AppKit
 * (`AppKitGameControllerBroker`) : UNE source process (l'objet `KadreIos.controllerInventory`),
 * des PORTS par attach projetés dessus, et les trois règles de fan-out :
 *
 * 1. **Le lifecycle va à TOUS les ports, suspendus compris** (Connected/Disconnected) — une
 *    projection suspendue doit savoir quelles manettes existent ;
 * 2. **`StateChanged` ne va qu'aux ports ROUTÉS qui connaissent la clé**, mais l'état de la
 *    table est mis à jour QUEL QUE SOIT le routage — une session qui reprend lit le présent ;
 * 3. **`RoutingChanged` va au port dont le routage dérivé a changé**, portant routage ET état.
 *
 * Les deux autres règles du moule : la table est mise à jour AVANT toute livraison (les
 * événements partent HORS du lock — un observateur qui lit le snapshot pendant une livraison
 * voit l'état qui cause son événement), et la relecture est ENTIÈRE à chaque fire (jamais un
 * delta — un fire qui ne change rien ne livre rien). Le tampon d'ouverture d'AppKit est ici
 * réduit à son vrai contenu : la source ne porte aucun payload, un fire pendant
 * l'enregistrement du listener est un BIT (« relis tout ») replié par une relecture après
 * l'ouverture — rien ne peut être perdu ni rejoué deux fois.
 *
 * Les clés sont allouées par graine process-monotonique ([uikitControllerKey]) — la PREMIÈRE
 * sighting obtient la graine suivante, une manette revenue obtient une NOUVELLE clé (critère
 * runtime « nouvel ID après disparition terminale ») ; le compteur ne revient JAMAIS en
 * arrière. Les DEUX ports partagent la MÊME clé par manette physique (une seule vérité).
 *
 * La propriété de la source reste à la couture d'attach (comme l'inventaire display, Task 2) :
 * les ports ne la ferment JAMAIS ; le dernier port vivant libère l'observation (le miroir
 * AppKit `releaseNativeLocked` — aucun observer ne survit à zéro session), la prochaine
 * ouverture ré-enregistre. Les effets vivants sont interrompus par TOUT teardown qui retire
 * leur support : déconnexion de la manette, close du port, close de l'inventaire (critère de
 * sortie roadmap « effets interrompus au teardown »).
 *
 * @param source le seam jeton que cet inventaire lit ; possédé par la couture, jamais fermé ici.
 * @param effects le seam haptique que [UIKitGamepadPort.startEffect] projette.
 */
internal class UIKitControllerInventory(
    private val source: UIKitControllerSource,
    private val effects: UIKitHapticsEffectSeam,
) : AutoCloseable {
    private val lock = RuntimeLock()
    private val gamepadPorts = linkedSetOf<UIKitGamepadPort>()
    private val devicePorts = linkedSetOf<UIKitInputDevicePort>()
    private val controllers = linkedMapOf<Any, PhysicalController>()
    private val activeEffects = linkedMapOf<Long, LinkedHashSet<UIKitGamepadPortEffect>>()
    private val pendingEffects = linkedMapOf<Long, Int>()
    private var nextSeed = 0L
    private var listening = false
    private var opening = false
    private var firedDuringOpening = false
    private var closed = false

    // MARK: - l'ouverture des ports

    /**
     * Ouvre une projection manette : l'inventaire initial est installé SYNCHRONEMENT (sans
     * événement — la première lecture du runtime EST la connexion, contrat du port), et le
     * port entend les fires ultérieurs. Les livraisons que l'ouverture a fait naître chez les
     * ports DÉJÀ ouverts partent après le retour du lock.
     */
    public fun openGamepadPort(): UIKitGamepadPort {
        var opened: UIKitGamepadPort? = null
        val deliveries = lock.withLock {
            check(!closed) { "the uikit controller inventory is closed" }
            val port = UIKitGamepadPort(this)
            check(gamepadPorts.add(port)) { "the uikit gamepad port is already registered" }
            try {
                val pump = ensureSourceLocked()
                port.installInitialLocked(gamepadSourcesForLocked(port))
                opened = port
                pump
            } catch (failure: Throwable) {
                gamepadPorts.remove(port)
                throw failure
            }
        }
        deliver(deliveries)
        return checkNotNull(opened) { "the uikit gamepad port was not opened" }
    }

    /** Ouvre une projection device générique — même contrat que [openGamepadPort]. */
    public fun openInputDevicePort(): UIKitInputDevicePort {
        var opened: UIKitInputDevicePort? = null
        val deliveries = lock.withLock {
            check(!closed) { "the uikit controller inventory is closed" }
            val port = UIKitInputDevicePort(this)
            check(devicePorts.add(port)) { "the uikit input-device port is already registered" }
            try {
                val pump = ensureSourceLocked()
                port.installInitialLocked(deviceEntriesForLocked())
                opened = port
                pump
            } catch (failure: Throwable) {
                devicePorts.remove(port)
                throw failure
            }
        }
        deliver(deliveries)
        return checkNotNull(opened) { "the uikit input-device port was not opened" }
    }

    /**
     * L'enregistrement du listener (la PREMIÈRE ouverture seulement) suivi de la relecture
     * ENTIÈRE. Un fire qui survient PENDANT l'enregistrement est replié par la relecture
     * d'après (le bit [firedDuringOpening]) — le tampon d'ouverture AppKit, réduit à ce que
     * notre source delta-free peut perdre : rien.
     */
    private fun ensureSourceLocked(): Pump {
        if (listening) return reconcileLocked()
        check(!opening) { "the uikit controller source is already opening" }
        opening = true
        firedDuringOpening = false
        try {
            source.registerListener(::onSourceFired)
            listening = true
            val first = reconcileLocked()
            if (!firedDuringOpening) return first
            // Le fire d'ouverture : replié par CETTE relecture, jamais rejoué après.
            firedDuringOpening = false
            return first + reconcileLocked()
        } finally {
            opening = false
        }
    }

    // MARK: - la lecture du process (le listener du seam)

    /** Le fire du seam — delta-free par contrat : l'inventaire relit TOUT, sous le lock. */
    private fun onSourceFired() {
        val deliveries = lock.withLock {
            when {
                closed -> Pump()
                opening -> {
                    firedDuringOpening = true
                    Pump()
                }
                else -> reconcileLocked()
            }
        }
        deliver(deliveries)
    }

    /**
     * LA relecture complète — la table est mise à jour avant que QUI QUE CE SOIT entende
     * quoi que ce soit. Un contrôleur vu pour la première fois obtient sa clé et gèle ses
     * faits ; un contrôleur connu rafraîchit SON ÉTAT seul (le gel est inviolable) ; un jeton
     * disparu quitte la table et interrompt ses effets. Un jeton énuméré mais illisible
     * (`profileFacts` null) : inconnu → sauté (le prochain fire réessaie), connu → garde son
     * dernier état (une course de lecture n'est pas une déconnexion).
     */
    private fun reconcileLocked(): Pump {
        val deliveries = Pump()
        val seen = linkedSetOf<Any>()
        for (token in source.controllers()) {
            if (closed) break
            seen += token
            val known = controllers[token]
            if (known != null) {
                val facts = source.profileFacts(token) ?: continue
                val before = known.state
                known.refresh(facts)
                if (known.state != before && known.extended) {
                    gamepadPorts.forEach { port ->
                        if (port.isOpenLocked() && port.knowsLocked(known.key) && routedLocked(port)) {
                            port.stateChangedLocked(known.key, known.state)?.let { deliveries.gamepads += it }
                        }
                    }
                }
            } else {
                connectLocked(token, deliveries)
            }
        }
        // Les clés sont photographiées (les entries du LinkedHashMap natif sont des vues
        // vivantes — toute mutation invalide l'entry obtenue) ; [disconnectLocked] possède le
        // retrait, et la table est à jour AVANT toute livraison (state-before-delivery).
        for (token in controllers.keys.toList()) {
            if (token !in seen) controllers[token]?.let { physical ->
                disconnectLocked(token, physical, deliveries)
            }
        }
        return deliveries
    }

    /** La première sighting : clé allouée, faits GELÉS, lifecycle fan-out à tous les ports. */
    private fun connectLocked(token: Any, deliveries: Pump) {
        val facts = source.profileFacts(token) ?: return
        val extended = source.isExtendedGamepad(token)
        val micro = source.isMicroGamepadOnly(token)
        val key = uikitControllerKey(nextSeed++)
        val physical = PhysicalController(
            token = token,
            key = key,
            name = source.nameOf(token),
            extended = extended,
            genericKind = uikitGenericDeviceKindOf(
                isGamepad = extended,
                isKeyboard = facts.isKeyboard,
                isMouse = facts.isMouse,
                isMicroGamepad = micro || facts.isMicroGamepad,
            ),
            facts = facts,
        )
        controllers[token] = physical
        if (extended) {
            gamepadPorts.forEach { port ->
                port.connectedLocked(gamepadSourceForLocked(port, physical))?.let { deliveries.gamepads += it }
            }
        }
        devicePorts.forEach { port ->
            port.connectedLocked(deviceEntryForLocked(physical))?.let { deliveries.devices += it }
        }
    }

    /** La disparition : lifecycle fan-out à tous les ports, effets interrompus ensuite. */
    private fun disconnectLocked(token: Any, physical: PhysicalController, deliveries: Pump) {
        controllers.remove(token) ?: return
        if (physical.extended) {
            gamepadPorts.forEach { port ->
                port.disconnectedLocked(physical.key)?.let { deliveries.gamepads += it }
            }
        }
        devicePorts.forEach { port ->
            port.disconnectedLocked(physical.key)?.let { deliveries.devices += it }
        }
        deliveries.interrupted += detachEffectsLocked { owner -> owner.key == physical.key }
    }

    // MARK: - les projections

    /** La projection manette COMPLÈTE du port — chaque manette étendue, à travers SON routage. */
    internal fun gamepads(port: UIKitGamepadPort): List<GamepadPortGamepad> = lock.withLock {
        if (!port.isOpenLocked()) return@withLock emptyList()
        controllers.values.filter { it.extended }.map { physical -> gamepadSourceForLocked(port, physical) }
    }

    /** La projection device COMPLÈTE du port — chaque jeton vivant, kind du kernel. */
    internal fun devices(port: UIKitInputDevicePort): List<InputDevicePortDevice> = lock.withLock {
        if (!port.isOpenLocked()) return@withLock emptyList()
        controllers.values.map { physical -> deviceEntryForLocked(physical) }
    }

    private fun gamepadSourcesForLocked(port: UIKitGamepadPort): List<GamepadPortGamepad> =
        controllers.values.filter { it.extended }.map { physical -> gamepadSourceForLocked(port, physical) }

    private fun gamepadSourceForLocked(port: UIKitGamepadPort, physical: PhysicalController): GamepadPortGamepad =
        GamepadPortGamepad(
            key = physical.key,
            descriptor = physical.descriptor,
            state = physical.state,
            routing = routingForLocked(port),
            capabilities = physical.capabilities,
        )

    private fun deviceEntriesForLocked(): List<InputDevicePortDevice> =
        controllers.values.map { physical -> deviceEntryForLocked(physical) }

    private fun deviceEntryForLocked(physical: PhysicalController): InputDevicePortDevice = InputDevicePortDevice(
        key = physical.key,
        descriptor = InputDeviceDescriptor(name = physical.name, kind = physical.genericKind),
    )

    // MARK: - les observers

    internal fun installObserver(port: UIKitGamepadPort, observer: (GamepadPortEvent) -> Unit): AutoCloseable = lock.withLock {
        check(!closed && port.isOpenLocked()) { "the uikit gamepad port is closed" }
        port.installObserverLocked(observer)
    }

    internal fun installObserver(port: UIKitInputDevicePort, observer: (InputDevicePortEvent) -> Unit): AutoCloseable = lock.withLock {
        check(!closed && port.isOpenLocked()) { "the uikit input-device port is closed" }
        port.installObserverLocked(observer)
    }

    // MARK: - le routage (la règle AppKit verbatim)

    internal fun updateRouting(port: UIKitGamepadPort, routing: GamepadPortRouting) {
        val deliveries = lock.withLock {
            if (closed || !port.isOpenLocked()) return@withLock Pump()
            port.updateRoutingLocked(routing)
            reconcileRoutingLocked()
        }
        deliver(deliveries)
    }

    /** Chaque port dont le routage dérivé change entend UNE RoutingChanged par manette connue. */
    private fun reconcileRoutingLocked(): Pump {
        val deliveries = Pump()
        gamepadPorts.forEach { port ->
            if (!port.isOpenLocked()) return@forEach
            val next = routingForLocked(port)
            controllers.values.filter { it.extended }.forEach { physical ->
                if (port.routingForLocked(physical.key) != next) {
                    port.routingChangedLocked(physical.key, next, physical.state)?.let { deliveries.gamepads += it }
                }
            }
        }
        return deliveries
    }

    private fun routingForLocked(port: UIKitGamepadPort): GamepadRoutingState =
        if (routedLocked(port)) GamepadRoutingState.Routed else GamepadRoutingState.Suspended

    private fun routedLocked(port: UIKitGamepadPort): Boolean {
        val context = port.routingLocked() ?: return false
        if (!context.foregroundActive) return false
        return when (context.policy) {
            GamepadRouting.AllForegroundSessions -> true
            GamepadRouting.ActiveSessionOnly -> gamepadPorts.firstOrNull { candidate ->
                candidate.routingLocked()?.let { routing ->
                    routing.policy == GamepadRouting.ActiveSessionOnly && routing.foregroundActive
                } == true
            } === port
        }
    }

    // MARK: - les effets (l'admission AppKit : sous le lock, le natif HORS lock)

    internal fun startEffect(port: UIKitGamepadPort, key: Long, effect: GamepadEffect): KadreResult<GamepadPortEffect> {
        val admission = lock.withLock {
            val physical = controllers.values.firstOrNull { it.extended && it.key == key }
            when {
                closed || !port.isOpenLocked() -> KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Gamepad))
                physical == null -> KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Gamepad))
                effect !is GamepadEffect.LocalizedHaptic -> KadreResult.Failure(KadreFailure.InvalidRequest("effect"))
                effect.locality !in physical.hapticLocalities -> KadreResult.Failure(KadreFailure.InvalidRequest("effect"))
                activeEffects[key]?.isNotEmpty() == true || (pendingEffects[key] ?: 0) > 0 ->
                    KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.GamepadEffect))
                else -> {
                    pendingEffects[key] = (pendingEffects[key] ?: 0) + 1
                    KadreResult.Success(effect to physical.token)
                }
            }
        }
        val admitted = when (admission) {
            is KadreResult.Failure -> return admission
            is KadreResult.Success -> admission.value
        }
        // Le natif HORS lock : la création CHHapticEngine peut être lente et ne lit aucun état
        // de l'inventaire (la réservation pendante borne la concurrence, miroir AppKit).
        val started = effects.startEffect(admitted.second, admitted.first)
        val handle = when (started) {
            is KadreResult.Failure -> {
                lock.withLock { releasePendingEffectLocked(key) }
                return started
            }
            is KadreResult.Success -> started.value
        }
        val portEffect = lock.withLock {
            releasePendingEffectLocked(key)
            val stillThere = controllers.values.firstOrNull { it.extended && it.key == key }
            if (closed || !port.isOpenLocked() || stillThere == null) {
                null
            } else {
                UIKitGamepadPortEffect(this, port, key, handle).also { owner ->
                    activeEffects.getOrPut(key) { linkedSetOf() }.add(owner)
                    port.attachEffectLocked(owner)
                }
            }
        }
        if (portEffect == null) {
            runCatching { handle.close() }
            return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Gamepad))
        }
        return KadreResult.Success(portEffect)
    }

    internal fun releaseEffect(effect: UIKitGamepadPortEffect) {
        lock.withLock {
            activeEffects[effect.key]?.let { owners ->
                owners.remove(effect)
                if (owners.isEmpty()) activeEffects.remove(effect.key)
            }
            effect.port.detachEffectLocked(effect)
        }
    }

    private fun releasePendingEffectLocked(key: Long) {
        val pending = checkNotNull(pendingEffects[key]) { "missing uikit gamepad effect reservation" }
        if (pending == 1) pendingEffects.remove(key) else pendingEffects[key] = pending - 1
    }

    private fun detachEffectsLocked(predicate: (UIKitGamepadPortEffect) -> Boolean): List<UIKitGamepadPortEffect> = buildList {
        activeEffects.values.forEach { owners -> addAll(owners.filter(predicate)) }
    }.also { removed ->
        removed.forEach { owner ->
            activeEffects[owner.key]?.remove(owner)
            owner.port.detachEffectLocked(owner)
        }
    }

    // MARK: - les fermetures

    internal fun closeGamepadPort(port: UIKitGamepadPort) {
        val release = lock.withLock {
            if (!gamepadPorts.remove(port)) return@withLock emptyList<UIKitGamepadPortEffect>()
            port.closeFromInventoryLocked()
            detachEffectsLocked { owner -> owner.port === port } +
                if (gamepadPorts.isEmpty() && devicePorts.isEmpty()) releaseLocked() else emptyList()
        }
        release.forEach(UIKitGamepadPortEffect::closeFromInventory)
    }

    internal fun closeInputDevicePort(port: UIKitInputDevicePort) {
        val release = lock.withLock {
            if (!devicePorts.remove(port)) return@withLock emptyList<UIKitGamepadPortEffect>()
            port.closeFromInventoryLocked()
            if (gamepadPorts.isEmpty() && devicePorts.isEmpty()) releaseLocked() else emptyList()
        }
        release.forEach(UIKitGamepadPortEffect::closeFromInventory)
    }

    /**
     * Le close de l'inventaire (tests et symétrie — en production l'objet vit pour le
     * process) : ferme chaque port, interrompt tous les effets, libère l'observation, JAMAIS
     * la source (la couture d'attach la possède). Idempotent.
     */
    override fun close() {
        val release = lock.withLock {
            if (closed) return@withLock emptyList<UIKitGamepadPortEffect>()
            closed = true
            gamepadPorts.forEach(UIKitGamepadPort::closeFromInventoryLocked)
            devicePorts.forEach(UIKitInputDevicePort::closeFromInventoryLocked)
            gamepadPorts.clear()
            devicePorts.clear()
            releaseLocked()
        }
        release.forEach(UIKitGamepadPortEffect::closeFromInventory)
    }

    /**
     * La libération du DERNIER port : effets détachés, table vidée, observation retirée. Le
     * compteur de graines NE REVIENT JAMAIS en arrière (les clés restent process-monotones à
     * travers les réouvertures) ; la prochaine ouverture relit l'inventaire réel — une
     * manette encore branchée est une NOUVELLE sighting avec une NOUVELLE clé (l'ancienne
     * projection a terminé avec ses sessions, personne ne peut encore citer l'ancienne clé).
     */
    private fun releaseLocked(): List<UIKitGamepadPortEffect> {
        val interrupted = detachEffectsLocked { true }
        activeEffects.clear()
        pendingEffects.clear()
        controllers.clear()
        if (listening) {
            source.unregisterListener(::onSourceFired)
            listening = false
        }
        return interrupted
    }

    private fun deliver(deliveries: Pump) {
        deliveries.gamepads.forEach { delivery -> delivery.observer(delivery.event) }
        deliveries.devices.forEach { delivery -> delivery.observer(delivery.event) }
        deliveries.interrupted.forEach(UIKitGamepadPortEffect::closeFromInventory)
    }
}

// =============================================================================================
// La réalisation production de la source — le VRAI GameController. Chaque symbole référencé
// ici existe sur iOS ET tvOS au klib 2.4.20 (spike Task 1 §2-3 : controllers + notifications
// contrôleur/clavier/souris + profils étendu/micro + haptics ; la compile des QUATRE cibles
// est le fait de discipline tvOS). Le clavier coalescé : UNE entrée quand connecté (la vérité
// GCKeyboard.h — les claviers ne sont PAS dans `GCController.controllers()`, cf. la phase 5
// `UIKitKeyboardBinding`) ; idem souris via `GCMouse.mice`.
// =============================================================================================

/**
 * La table fermée des localités haptiques — le miroir AppKit `toKadreHapticLocality` (les 8
 * valeurs du modèle, dans l'ordre du klib). Une localité hors table est PERDUE, jamais
 * approximée.
 */
private val UIKIT_HAPTIC_LOCALITY_VALUES = listOf(
    GamepadHapticLocality.Default,
    GamepadHapticLocality.All,
    GamepadHapticLocality.Handles,
    GamepadHapticLocality.LeftHandle,
    GamepadHapticLocality.RightHandle,
    GamepadHapticLocality.Triggers,
    GamepadHapticLocality.LeftTrigger,
    GamepadHapticLocality.RightTrigger,
)

internal val UIKIT_HAPTIC_LOCALITY_TABLE: List<Pair<String, GamepadHapticLocality>> =
    listOf(
        GCHapticsLocalityDefault,
        GCHapticsLocalityAll,
        GCHapticsLocalityHandles,
        GCHapticsLocalityLeftHandle,
        GCHapticsLocalityRightHandle,
        GCHapticsLocalityTriggers,
        GCHapticsLocalityLeftTrigger,
        GCHapticsLocalityRightTrigger,
        // Les constantes klib sont typées `String?` sur les cibles Simulator et `String` sur
        // les cibles device (le paramètre les accepte toutes deux) : la row d'une constante
        // null sur UNE cible est PERDUE sur cette cible — l'absence reste une absence, jamais
        // une constante fabriquée.
    ).mapIndexedNotNull { index, constant -> constant?.let { it to UIKIT_HAPTIC_LOCALITY_VALUES[index] } }

/** La localité du modèle depuis la constante klib, ou `null` hors table — jamais approximée. */
internal fun uikitHapticLocalityOf(constant: String): GamepadHapticLocality? =
    UIKIT_HAPTIC_LOCALITY_TABLE.firstOrNull { it.first == constant }?.second

/** La constante klib depuis la localité du modèle, ou `null` hors table — jamais approximée. */
internal fun uikitHapticLocalityConstantOf(locality: GamepadHapticLocality): String? =
    UIKIT_HAPTIC_LOCALITY_TABLE.firstOrNull { it.second == locality }?.first

/**
 * Les ACCÈS fermés aux éléments du profil étendu — les MÊMES noms que les tables du kernel
 * ([UIKIT_GAMEPAD_BUTTON_TABLE]/[UIKIT_GAMEPAD_AXIS_TABLE] ; le test hôte garde la garde de
 * cohérence des noms). La gâchette porte le bouton ET l'axe : la lecture axe de
 * `leftTrigger`/`rightTrigger` est la valeur analogique du `GCControllerButtonInput` (le
 * même élément klib, ruling T1).
 */
internal val UIKIT_GAMEPAD_BUTTON_ACCESSORS: List<Pair<String, (GCExtendedGamepad) -> GCControllerButtonInput?>> = listOf(
    "buttonA" to { profile -> profile.buttonA },
    "buttonB" to { profile -> profile.buttonB },
    "buttonX" to { profile -> profile.buttonX },
    "buttonY" to { profile -> profile.buttonY },
    "leftShoulder" to { profile -> profile.leftShoulder },
    "rightShoulder" to { profile -> profile.rightShoulder },
    "leftTrigger" to { profile -> profile.leftTrigger },
    "rightTrigger" to { profile -> profile.rightTrigger },
    "buttonMenu" to { profile -> profile.buttonMenu },
    "buttonOptions" to { profile -> profile.buttonOptions },
    "buttonHome" to { profile -> profile.buttonHome },
    "dpad.up" to { profile -> profile.dpad.up },
    "dpad.down" to { profile -> profile.dpad.down },
    "dpad.left" to { profile -> profile.dpad.left },
    "dpad.right" to { profile -> profile.dpad.right },
    "leftThumbstickButton" to { profile -> profile.leftThumbstickButton },
    "rightThumbstickButton" to { profile -> profile.rightThumbstickButton },
)

// QUIRK d'interop cible-par-cible : les klibs DEVICE déclarent `dpad`/`leftThumbstick`/
// `rightThumbstick` NON-null (le pouce y est un `GCControllerDirectionPad`), les klibs
// Simulator les déclarent nullables — la forme `?.` est NÉCESSAIRE au Simulator et «
// inutile » au device (le warning n'est que la vue de chaque klib). La garde d'accès reste
// la même sur les deux familles : un élément absent répond `null`, jamais une lecture
// fabriquée.
@Suppress("UNNECESSARY_SAFE_CALL")
internal val UIKIT_GAMEPAD_AXIS_ACCESSORS: List<Pair<String, (GCExtendedGamepad) -> GCControllerElement?>> = listOf(
    "leftThumbstick.xAxis" to { profile -> profile.leftThumbstick?.xAxis },
    "leftThumbstick.yAxis" to { profile -> profile.leftThumbstick?.yAxis },
    "rightThumbstick.xAxis" to { profile -> profile.rightThumbstick?.xAxis },
    "rightThumbstick.yAxis" to { profile -> profile.rightThumbstick?.yAxis },
    "leftTrigger" to { profile -> profile.leftTrigger },
    "rightTrigger" to { profile -> profile.rightTrigger },
    "dpad.xAxis" to { profile -> profile.dpad.xAxis },
    "dpad.yAxis" to { profile -> profile.dpad.yAxis },
)

/** La garde de cohérence des noms : les accès portent EXACTEMENT les noms des tables kernel. */
internal val UIKIT_GAMEPAD_ACCESSOR_NAMES_MATCH_KERNEL_TABLES: Boolean =
    UIKIT_GAMEPAD_BUTTON_ACCESSORS.map { it.first } == UIKIT_GAMEPAD_BUTTON_TABLE.map { it.first } &&
        UIKIT_GAMEPAD_AXIS_ACCESSORS.map { it.first } == UIKIT_GAMEPAD_AXIS_TABLE.map { it.first }

/**
 * La réalisation production du seam — les SIX notifications du fait contrôleur (connect/
 * disconnect de manette, clavier et souris — les constantes `String?` du klib, `!!` du
 * pattern surface), block-based sur la main queue (le mécanisme exact des Tasks 2/3), et la
 * lecture des profils qui ARME les handlers d'éléments (la lecture EST l'armement : chaque
 * `profileFacts` rebranche les handlers de la manette sur le listener courant, donc une
 * manette connectée plus tard est armée à SA première lecture).
 *
 * Les handlers ne portent AUCUN delta : ils appellent le listener, l'inventaire relit TOUT
 * (le dedup d'état de la table fait le reste — un fire sans changement canonique ne livre
 * rien, prouvé par le test hôte).
 */
internal class UIKitGCControllerSource : UIKitControllerSource {
    private val lock = RuntimeLock()
    private var listeners: MutableList<() -> Unit> = mutableListOf()
    private var registrations: HashMap<() -> Unit, List<NSObjectProtocol>> = HashMap()

    override fun controllers(): List<Any> =
        GCController.controllers().orEmpty().filterIsInstance<GCController>() +
            listOfNotNull<Any>(GCKeyboard.coalescedKeyboard) +
            GCMouse.mice().orEmpty().filterIsInstance<GCMouse>()

    override fun registerListener(listener: () -> Unit) {
        val center = NSNotificationCenter.defaultCenter
        val tokens = NOTIFICATION_NAMES.map { name ->
            center.addObserverForName(name, null, NSOperationQueue.mainQueue()) { _ -> listener() }
        }
        lock.withLock {
            listeners += listener
            registrations[listener] = tokens
        }
    }

    override fun unregisterListener(listener: () -> Unit) {
        val tokens = lock.withLock {
            listeners.remove(listener)
            registrations.remove(listener)
        } ?: return
        val center = NSNotificationCenter.defaultCenter
        for (token in tokens) center.removeObserver(token)
    }

    /**
     * La lecture SDK brute — ET l'armement des handlers d'éléments (le point d'armement du
     * contrat du seam). Les handlers sont posés sans delta : chaque changement d'élément ne
     * fait qu'appeler le listener, la relecture fait foi.
     */
    override fun profileFacts(controller: Any): UIKitControllerFacts? = when (controller) {
        is GCController -> gcControllerFacts(controller)
        is GCKeyboard -> UIKitControllerFacts(isKeyboard = true)
        is GCMouse -> UIKitControllerFacts(isMouse = true)
        else -> null
    }

    override fun nameOf(controller: Any): String? = when (controller) {
        // `vendorName` appartient au GCController ; le clavier/souris n'annoncent que leur
        // catégorie (GCDevice) — null honnête du reste, jamais « Unknown » fabriqué.
        is GCController -> controller.vendorName ?: controller.productCategory
        is GCKeyboard -> controller.productCategory
        is GCMouse -> controller.productCategory
        else -> null
    }

    override fun isExtendedGamepad(controller: Any): Boolean =
        (controller as? GCController)?.extendedGamepad != null

    override fun isMicroGamepadOnly(controller: Any): Boolean {
        val gc = controller as? GCController ?: return false
        return gc.microGamepad != null && gc.extendedGamepad == null
    }

    override fun close() {
        val all: List<List<NSObjectProtocol>> = lock.withLock {
            val taken = registrations.values.toList()
            registrations = HashMap()
            listeners = mutableListOf()
            taken
        }
        val center = NSNotificationCenter.defaultCenter
        for (token in all.flatten()) center.removeObserver(token)
    }

    private fun gcControllerFacts(controller: GCController): UIKitControllerFacts {
        val extended: GCExtendedGamepad? = controller.extendedGamepad
        val micro: GCMicroGamepad? = controller.microGamepad
        val buttons = linkedMapOf<String, UIKitButtonReading>()
        val axes = linkedMapOf<String, Double>()
        if (extended != null) {
            val listener = currentListener()
            UIKIT_GAMEPAD_BUTTON_ACCESSORS.forEach { (name, accessor) ->
                accessor(extended)?.let { element ->
                    arm(element, listener)
                    buttons[name] = UIKitButtonReading(element.value.toDouble(), element.isPressed())
                }
            }
            UIKIT_GAMEPAD_AXIS_ACCESSORS.forEach { (name, accessor) ->
                when (val element = accessor(extended)) {
                    is GCControllerAxisInput -> {
                        arm(element, listener)
                        axes[name] = element.value.toDouble()
                    }
                    is GCControllerButtonInput -> {
                        arm(element, listener)
                        axes[name] = element.value.toDouble()
                    }
                }
            }
        }
        // Le fait haptique du SDK : `haptics` non nil ⇔ le contrôleur supporte le haptique ;
        // `supportedLocalities` porte SES localités, traduites par la table fermée.
        val localities = controller.haptics?.supportedLocalities.orEmpty()
            .filterIsInstance<String>()
            .mapNotNull(::uikitHapticLocalityOf)
            .toSet()
        return UIKitControllerFacts(
            isExtendedGamepad = extended != null,
            isMicroGamepad = micro != null,
            buttonReadings = buttons,
            axisReadings = axes,
            hapticLocalities = localities,
        )
    }

    private fun currentListener(): (() -> Unit)? = lock.withLock { listeners.lastOrNull() }

    /** (Re)branche les handlers de l'élément bouton sur le listener courant. */
    private fun arm(element: GCControllerButtonInput, listener: (() -> Unit)?) {
        if (listener == null) return
        element.valueChangedHandler = { _, _, _ -> listener() }
        element.pressedChangedHandler = { _, _, _ -> listener() }
    }

    /** (Re)branche le handler de l'élément axe sur le listener courant. */
    private fun arm(element: GCControllerAxisInput, listener: (() -> Unit)?) {
        if (listener == null) return
        element.valueChangedHandler = { _, _ -> listener() }
    }

    private companion object {
        // MÊME QUIRK cible-par-cible : les constantes de notification GameController sont
        // `String` non-null sur les klibs DEVICE (`!!` « inutile ») et `String?` sur les klibs
        // Simulator (le `!!` y est OBLIGATOIRE) — le `!!` est la forme qui compile sur les
        // QUATRE cibles (le pattern `!!` de la surface, Task 2). Les SIX noms du fait
        // contrôleur : connect/disconnect de manette, clavier et souris.
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        val NOTIFICATION_NAMES = listOf(
            GCControllerDidConnectNotification!!,
            GCControllerDidDisconnectNotification!!,
            GCKeyboardDidConnectNotification!!,
            GCKeyboardDidDisconnectNotification!!,
            GCMouseDidConnectNotification!!,
            GCMouseDidDisconnectNotification!!,
        )
    }
}

// =============================================================================================
// La réalisation production du seam haptique — le chemin de la spike Task 1 :
// `controller.haptics` → `createEngineWithLocality(localité)` → CoreHaptics (`CHHapticEngine`
// → pattern événement continu → player). Toute erreur Obj-C (NSError) est une RÉPONSE mappée
// `PlatformFailure(UIKit, "game-controller", code)` — les formes AppKit
// (`KffiAppKitGameControllerNative`), jamais une exception traversante. LIMITE HONNÊTE : le
// Simulator ne porte AUCUN contrôleur, ce chemin n'est JAMAIS exécutable dans les gates CI —
// l'admission (localités gelées), le stop/close idempotent et le teardown interrupteur sont
// prouvés sur le seam injecté (tests hôtes) ; la lecture réelle est le charter matériel
// (Task 6/8). Le moteur est VOLONTAIREMENT par-effet et court-vivant : CoreHaptics récupère
// les moteurs inactifs de lui-même (l'arrêt SDK par idle timeout), l'inventaire n'en gère
// aucun au-delà de l'effet qu'il a démarré.
// =============================================================================================

/** L'échec haptique normalisé — la forme AppKit `gameControllerFailure`. */
internal fun uikitHapticsFailure(code: String): KadreFailure.PlatformFailure = KadreFailure.PlatformFailure(
    platform = KadrePlatform.UIKit,
    domain = "game-controller",
    code = code,
)

/**
 * Le démarreur CoreHaptics des effets LocalizedHaptic : un moteur par localité demandée, un
 * pattern à événement continu portant intensité (0-1, validé par le modèle) et durée portée
 * par l'ÉVÉNEMENT (le klib 2.4.20 n'a PAS de paramètre durée — l'init 4-arguments du
 * `CHHapticEvent` la porte, en secondes), démarré immédiatement. Les formes klib vérifiées
 * au compile : les méthodes `…AndReturnError`/`…WithPattern`/`…AtTime` gardent leur
 * paramètre d'erreur et répondent `Boolean` (le NSError est laissé `null` — l'échec est
 * mappé par code, la forme AppKit) ; la durée de l'effet est portée par l'ÉVÉNEMENT (le klib
 * 2.4.20 n'a PAS de paramètre durée — l'init 4-arguments du `CHHapticEvent` la porte, en
 * secondes). Un moteur illisible, un start/pattern/player manqué sont des échecs mappés.
 */
internal class UIKitGCDeviceHapticsEffectSeam : UIKitHapticsEffectSeam {
    override fun startEffect(
        controller: Any,
        effect: GamepadEffect.LocalizedHaptic,
    ): KadreResult<UIKitGamepadEffectHandle> {
        val gc = controller as? GCController ?: return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.GamepadEffect))
        val haptics: GCDeviceHaptics = gc.haptics
            ?: return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.GamepadEffect))
        val locality = uikitHapticLocalityConstantOf(effect.locality)
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("effect"))
        // Le PONT de déclaration avancée : le klib GameController porte `CHHapticEngine`
        // comme déclaration avancée SANS MEMBRES (`objcnames.classes.CHHapticEngine` — le
        // manifest du klib l'épingle), la classe RÉELLE vivant dans le klib CoreHaptics. Un
        // MÊME objet Obj-C porte les deux types Kotlin : le `as` entre classes Obj-C est
        // abaissé en vérification de classe au runtime (le même objet la passe), et
        // l'avertissement statique « can never succeed » n'est que la vue du système de
        // types, qui n'unifie pas la déclaration avancée avec la classe réelle. La nullité
        // du moteur reste lue AVANT le pont.
        @Suppress("CAST_NEVER_SUCCEEDS")
        val engine: CHHapticEngine = (haptics.createEngineWithLocality(locality) ?: return KadreResult.Failure(
            uikitHapticsFailure("haptics-engine-unavailable"),
        )) as CHHapticEngine
        return try {
            if (!engine.startAndReturnError(null)) {
                return KadreResult.Failure(uikitHapticsFailure("haptics-engine-start-failed"))
            }
            val event = CHHapticEvent(
                CHHapticEventTypeHapticContinuous,
                listOf(
                    CHHapticEventParameter(CHHapticEventParameterIDHapticIntensity, effect.intensity.toFloat()),
                ),
                0.0,
                effect.duration.inWholeMilliseconds / 1000.0,
            )
            val pattern = CHHapticPattern(events = listOf(event), parameters = listOf<CHHapticEventParameter>(), error = null)
            val player: CHHapticPatternPlayerProtocol = engine.createPlayerWithPattern(pattern, null)
                ?: return KadreResult.Failure(uikitHapticsFailure("haptics-player-unavailable"))
            if (!player.startAtTime(CHHapticTimeImmediate, null)) {
                return KadreResult.Failure(uikitHapticsFailure("haptics-start-failed"))
            }
            KadreResult.Success(UIKitCHHapticsEffectHandle(player))
        } catch (_: Throwable) {
            KadreResult.Failure(uikitHapticsFailure("haptics-start-failed"))
        }
    }
}

/**
 * Le handle CoreHaptics — la même garde atomique que le miroir AppKit : UN seul stop natif
 * quel que soit le nombre de stops et de closes, et un stop qui échoue reste une RÉPONSE
 * (le moteur court-vivant récupéré par le SDK porte le reste).
 */
@OptIn(ExperimentalAtomicApi::class)
internal class UIKitCHHapticsEffectHandle(
    private val player: CHHapticPatternPlayerProtocol,
) : UIKitGamepadEffectHandle {
    private val stopped = AtomicBoolean(false)

    override fun requestStop(): KadreResult<Unit> = stop()

    override fun close() {
        stop()
    }

    private fun stop(): KadreResult<Unit> {
        if (!stopped.compareAndSet(false, true)) return KadreResult.Success(Unit)
        return if (player.stopAtTime(CHHapticTimeImmediate, null)) {
            KadreResult.Success(Unit)
        } else {
            KadreResult.Failure(uikitHapticsFailure("haptics-stop-failed"))
        }
    }
}
