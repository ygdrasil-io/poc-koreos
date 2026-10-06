package org.graphiks.kadre.platform.web

import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.internal.runtime.GamepadPort
import org.graphiks.kadre.internal.runtime.GamepadPortEffect
import org.graphiks.kadre.internal.runtime.GamepadPortEvent
import org.graphiks.kadre.internal.runtime.GamepadPortGamepad
import org.graphiks.kadre.internal.runtime.GamepadPortRouting

/**
 * One session's projection of the page's gamepads, through [WebGamepadHub] — the web form of the
 * AppKit broker's port. Nothing is stored here but the session's own context (its observer, its
 * routing, the routing each observer has already been told about, and the effect owners it has
 * launched): every snapshot derives live from the hub's table through this port's routing, so a
 * snapshot can never disagree with the events beside it — the hub updates its table before it
 * delivers anything.
 *
 * `startEffect` forwards to the hub, which applies `WebGamepadEffects`'s rules onto the pad's own
 * actuator and records the launched owner here, so a close while the effect runs revokes it: an
 * effect cannot outlive the session projection that asked for it.
 */
internal class WebGamepadPort(private val hub: WebGamepadHub) : GamepadPort {
    private var closed: Boolean = false
    private var observer: ((GamepadPortEvent) -> Unit)? = null
    private var routing: GamepadPortRouting? = null
    private var projectedRouting: GamepadRoutingState? = null
    private val activeEffects = linkedSetOf<WebGamepadEffect>()

    override val gamepads: List<GamepadPortGamepad>
        get() = hub.gamepads(this)

    override fun installObserver(observer: (GamepadPortEvent) -> Unit): AutoCloseable =
        hub.installObserver(this, observer)

    override fun updateRouting(routing: GamepadPortRouting) {
        hub.updateRouting(this, routing)
    }

    override fun startEffect(key: Long, effect: GamepadEffect): KadreResult<GamepadPortEffect> =
        hub.startEffect(this, key, effect)

    override fun close() {
        hub.closePort(this)
    }

    internal fun isOpenLocked(): Boolean = !closed

    /** Records one launched owner against this port, under the hub's lock, for the close revocation. */
    internal fun attachEffectLocked(effect: WebGamepadEffect) {
        activeEffects.add(effect)
    }

    /** Revokes every owner this port still holds: one stop each, exactly once, verdicts unread. */
    internal fun revokeEffectsLocked() {
        val owners = activeEffects.toList()
        activeEffects.clear()
        owners.forEach(WebGamepadEffect::revoke)
    }

    internal fun installObserverLocked(observer: (GamepadPortEvent) -> Unit): AutoCloseable {
        check(this.observer == null) { "the web gamepad port observer is already installed" }
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

    internal fun projectedRoutingLocked(): GamepadRoutingState? = projectedRouting

    internal fun setProjectedRoutingLocked(routing: GamepadRoutingState) {
        projectedRouting = routing
    }

    /** The hub's own close of this port; a later `close` of the runtime's is a no-op. */
    internal fun closeFromHubLocked() {
        closed = true
        observer = null
    }
}
