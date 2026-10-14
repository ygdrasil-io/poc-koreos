package org.graphiks.kadre.internal.uikit

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.input.GamepadState
import org.graphiks.kadre.internal.runtime.GamepadPort
import org.graphiks.kadre.internal.runtime.GamepadPortEffect
import org.graphiks.kadre.internal.runtime.GamepadPortEvent
import org.graphiks.kadre.internal.runtime.GamepadPortGamepad
import org.graphiks.kadre.internal.runtime.GamepadPortRouting

/**
 * Le port manette d'UNE session, projection de l'[UIKitControllerInventory] process — la
 * forme AppKit/web (`AppKitGameControllerPort`/`WebGamepadPort`) : rien n'est stocké ici que
 * le contexte de la session (son observer, son routage, le routage dont chaque observer a
 * déjà entendu parler, les effets qu'il a lancés) ; chaque snapshot dérive VIVANT de la table
 * de l'inventaire à travers le routage de CE port, et l'inventaire met sa table à jour avant
 * de livrer quoi que ce soit — un snapshot ne peut jamais contredire les événements à côté
 * de lui.
 *
 * `startEffect` est routé vers l'inventaire, qui applique l'admission AppKit (port ouvert,
 * manette étendue connue, localité gelée annoncée, UN effet vivant par manette physique) puis
 * démarre le natif HORS lock et enregistre le propriétaire ici — un close pendant qu'un effet
 * court le révoque : un effet ne survit jamais à la projection de session qui l'a demandé.
 *
 * Le port NE ferme JAMAIS la source de l'inventaire (la couture d'attach la possède — le
 * close des composants ferme le port, l'inventaire process vit à travers les attaches).
 */
internal class UIKitGamepadPort internal constructor(
    private val inventory: UIKitControllerInventory,
) : GamepadPort {
    override val inventoryFailure get() = inventory.inventoryFailure
    private var closed: Boolean = false
    private var observer: ((GamepadPortEvent) -> Unit)? = null
    private var routing: GamepadPortRouting? = null
    private val known = linkedSetOf<Long>()
    private val projectedRouting = linkedMapOf<Long, GamepadRoutingState>()
    private val activeEffects = linkedSetOf<UIKitGamepadPortEffect>()

    override val gamepads: List<GamepadPortGamepad>
        get() = inventory.gamepads(this)

    override fun installObserver(observer: (GamepadPortEvent) -> Unit): AutoCloseable =
        inventory.installObserver(this, observer)

    override fun updateRouting(routing: GamepadPortRouting) {
        inventory.updateRouting(this, routing)
    }

    override fun startEffect(key: Long, effect: GamepadEffect): KadreResult<GamepadPortEffect> =
        inventory.startEffect(this, key, effect)

    override fun close() {
        inventory.closeGamepadPort(this)
    }

    // MARK: - le contexte de session, muté par l'inventaire SOUS son lock (le suffixe Locked)

    internal fun isOpenLocked(): Boolean = !closed

    /** L'inventaire initial de l'OUVERTURE — sans événement : la première lecture est la connexion. */
    internal fun installInitialLocked(gamepads: List<GamepadPortGamepad>) {
        gamepads.forEach { gamepad ->
            known += gamepad.key
            projectedRouting[gamepad.key] = gamepad.routing
        }
    }

    internal fun installObserverLocked(observer: (GamepadPortEvent) -> Unit): AutoCloseable {
        check(this.observer == null) { "the uikit gamepad port observer is already installed" }
        this.observer = observer
        return AutoCloseable {
            if (this.observer === observer) this.observer = null
        }
    }

    internal fun observerLocked(): ((GamepadPortEvent) -> Unit)? = observer

    internal fun updateRoutingLocked(routing: GamepadPortRouting) {
        this.routing = routing
    }

    internal fun routingLocked(): GamepadPortRouting? = routing

    internal fun knowsLocked(key: Long): Boolean = key in known

    internal fun routingForLocked(key: Long): GamepadRoutingState? = projectedRouting[key]

    internal fun connectedLocked(source: GamepadPortGamepad): GamepadDelivery? {
        known += source.key
        projectedRouting[source.key] = source.routing
        return observer?.let { target -> GamepadDelivery(target, GamepadPortEvent.Connected(source)) }
    }

    internal fun disconnectedLocked(key: Long): GamepadDelivery? {
        if (!known.remove(key)) return null
        projectedRouting.remove(key)
        return observer?.let { target -> GamepadDelivery(target, GamepadPortEvent.Disconnected(key)) }
    }

    internal fun stateChangedLocked(key: Long, state: GamepadState): GamepadDelivery? =
        observer?.let { target -> GamepadDelivery(target, GamepadPortEvent.StateChanged(key, state)) }

    internal fun routingChangedLocked(key: Long, routing: GamepadRoutingState, state: GamepadState): GamepadDelivery? {
        projectedRouting[key] = routing
        return observer?.let { target -> GamepadDelivery(target, GamepadPortEvent.RoutingChanged(key, routing, state)) }
    }

    /** Enregistre un propriétaire lancé par CE port, sous le lock de l'inventaire. */
    internal fun attachEffectLocked(effect: UIKitGamepadPortEffect) {
        activeEffects.add(effect)
    }

    internal fun detachEffectLocked(effect: UIKitGamepadPortEffect) {
        activeEffects.remove(effect)
    }

    internal fun closeFromInventoryLocked() {
        closed = true
        observer = null
        known.clear()
        projectedRouting.clear()
        activeEffects.clear()
    }
}

/**
 * Le propriétaire d'effet retourné à travers le seam — le miroir exact d'`AppKitGameControllerPortEffect`
 * : [requestStop] délègue au handle natif tant que le propriétaire est vivant (le handle est
 * gardé — UN seul stop natif), [close] est idempotent par `AtomicBoolean` et rend le
 * propriétaire à l'inventaire. Un effet révoqué (teardown) reste appelable sans effet.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class UIKitGamepadPortEffect(
    private val inventory: UIKitControllerInventory,
    internal val port: UIKitGamepadPort,
    internal val key: Long,
    private val native: UIKitGamepadEffectHandle,
) : GamepadPortEffect {
    private val closed = AtomicBoolean(false)

    override fun requestStop(): KadreResult<Unit> = if (closed.load()) {
        KadreResult.Success(Unit)
    } else {
        native.requestStop()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                native.close()
            } finally {
                inventory.releaseEffect(this)
            }
        }
    }

    /** Le close de l'inventaire (teardown) — le même chemin idempotent que le close public. */
    internal fun closeFromInventory() = close()
}
