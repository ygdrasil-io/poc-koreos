package org.graphiks.kadre.internal.linux

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.internal.runtime.GamepadPort
import org.graphiks.kadre.internal.runtime.GamepadPortEffect
import org.graphiks.kadre.internal.runtime.GamepadPortEvent
import org.graphiks.kadre.internal.runtime.GamepadPortGamepad
import org.graphiks.kadre.internal.runtime.GamepadPortRouting

/**
 * Process-wide entry point of the evdev gamepad service (design D3):
 * one broker for the whole process, one view per session. The broker
 * starts with the first view and stops when the last view closes, so a
 * test process or an app without gamepad sessions leaves nothing
 * running. Host backends (Wayland today) feed the returned
 * [GamepadPort] to their session components.
 */
public object EvdevGamepadService {

    private val lock = Any()
    private var broker: EvdevGamepadBroker? = null
    private var liveViews = 0

    /** One session view; closing it detaches the session, not the process. */
    public fun openSessionView(): GamepadPort = synchronized(lock) {
        val active = broker ?: EvdevGamepadBroker(KffiEvdevNativeSource())
            .also { it.start() }
            .also { broker = it }
        liveViews += 1
        ServiceView(active.openSessionView())
    }

    private class ServiceView(
        private val view: EvdevGamepadPort,
    ) : GamepadPort {

        override val inventoryFailure: KadreFailure?
            get() = view.inventoryFailure

        override val gamepads: List<GamepadPortGamepad>
            get() = view.gamepads

        override fun installObserver(observer: (GamepadPortEvent) -> Unit): AutoCloseable =
            view.installObserver(observer)

        override fun updateRouting(routing: GamepadPortRouting) {
            view.updateRouting(routing)
        }

        override fun startEffect(key: Long, effect: GamepadEffect): KadreResult<GamepadPortEffect> =
            view.startEffect(key, effect)

        override fun close() {
            view.close()
            synchronized(lock) {
                liveViews -= 1
                if (liveViews == 0) {
                    broker?.close()
                    broker = null
                }
            }
        }
    }
}
