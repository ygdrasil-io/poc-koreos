package org.graphiks.kadre.internal.win32

import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.GamepadAxis
import org.graphiks.kadre.input.GamepadAxisValue
import org.graphiks.kadre.input.GamepadButton
import org.graphiks.kadre.input.GamepadButtonValue
import org.graphiks.kadre.input.GamepadCapabilities
import org.graphiks.kadre.input.GamepadDescriptor
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.input.GamepadEffectConstraints
import org.graphiks.kadre.input.GamepadEffectKind
import org.graphiks.kadre.input.GamepadMapping
import org.graphiks.kadre.input.GamepadState
import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.policy.GamepadRouting
import org.graphiks.kffi.win32.managed.Win32XInputStateValue
import org.graphiks.kadre.internal.runtime.GamepadPort
import org.graphiks.kadre.internal.runtime.GamepadPortEffect
import org.graphiks.kadre.internal.runtime.GamepadPortEvent
import org.graphiks.kadre.internal.runtime.GamepadPortGamepad
import org.graphiks.kadre.internal.runtime.GamepadPortRouting
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** Standard-mapping buttons in canonical order. */
internal val STANDARD_BUTTONS: List<GamepadButton> = listOf(
    GamepadButton.DpadUp, GamepadButton.DpadDown, GamepadButton.DpadLeft, GamepadButton.DpadRight,
    GamepadButton.Start, GamepadButton.Select,
    GamepadButton.LeftStick, GamepadButton.RightStick,
    GamepadButton.LeftShoulder, GamepadButton.RightShoulder,
    GamepadButton.South, GamepadButton.East, GamepadButton.West, GamepadButton.North,
)

/** Standard-mapping axes in canonical order. */
internal val STANDARD_AXES: List<GamepadAxis> = listOf(
    GamepadAxis.LeftX, GamepadAxis.LeftY, GamepadAxis.RightX, GamepadAxis.RightY,
    GamepadAxis.LeftTrigger, GamepadAxis.RightTrigger,
)

/** XInput button bitmask → pressed Standard buttons. */
internal fun xinputButtonsToStandard(mask: Int): List<GamepadButton> {
    val buttons = ArrayList<GamepadButton>(8)
    if (mask and 0x0001 != 0) buttons += GamepadButton.DpadUp
    if (mask and 0x0002 != 0) buttons += GamepadButton.DpadDown
    if (mask and 0x0004 != 0) buttons += GamepadButton.DpadLeft
    if (mask and 0x0008 != 0) buttons += GamepadButton.DpadRight
    if (mask and 0x0010 != 0) buttons += GamepadButton.Start
    if (mask and 0x0020 != 0) buttons += GamepadButton.Select
    if (mask and 0x0040 != 0) buttons += GamepadButton.LeftStick
    if (mask and 0x0080 != 0) buttons += GamepadButton.RightStick
    if (mask and 0x0100 != 0) buttons += GamepadButton.LeftShoulder
    if (mask and 0x0200 != 0) buttons += GamepadButton.RightShoulder
    if (mask and 0x1000 != 0) buttons += GamepadButton.South
    if (mask and 0x2000 != 0) buttons += GamepadButton.East
    if (mask and 0x4000 != 0) buttons += GamepadButton.West
    if (mask and 0x8000 != 0) buttons += GamepadButton.North
    return buttons
}

/** Canonical state for one poll: pressed Standard buttons + the six axes. */
internal fun standardStateFrom(
    buttons: Int,
    leftTrigger: Int,
    rightTrigger: Int,
    thumbLX: Int,
    thumbLY: Int,
    thumbRX: Int,
    thumbRY: Int,
): GamepadState {
    val pressed = xinputButtonsToStandard(buttons)
    val buttonValues = STANDARD_BUTTONS.map { button ->
        GamepadButtonValue(button, if (button in pressed) 1.0 else 0.0, button in pressed)
    }
    fun thumb(value: Int): Double = if (value >= 0) value / 32767.0 else value / 32768.0
    val axisValues = listOf(
        GamepadAxisValue(GamepadAxis.LeftX, thumb(thumbLX)),
        GamepadAxisValue(GamepadAxis.LeftY, -thumb(thumbLY)),
        GamepadAxisValue(GamepadAxis.RightX, thumb(thumbRX)),
        GamepadAxisValue(GamepadAxis.RightY, -thumb(thumbRY)),
        GamepadAxisValue(GamepadAxis.LeftTrigger, leftTrigger / 255.0),
        GamepadAxisValue(GamepadAxis.RightTrigger, rightTrigger / 255.0),
    )
    return GamepadState(buttonValues, axisValues)
}

internal const val XINPUT_SLOT_COUNT: Int = 4

/**
 * Process-wide XInput gamepad broker. A daemon thread polls the four slots
 * (state every 30 ms) and fans Connected/Disconnected/StateChanged events out
 * to every open port view. The native registration is process-lifetime: the
 * OS refuses removal for this API family, so close only detaches the views.
 */
internal class Win32GamepadBroker(
    private val native: Win32XInputNative,
    private val statePollMillis: Long = 30,
) : AutoCloseable {
    private val lock = Any()
    private val ports = CopyOnWriteArrayList<Win32GamepadPort>()
    private val connected = LinkedHashMap<Long, GamepadState>()
    @Volatile internal var closed: Boolean = false
        private set
    private val worker = Thread({ pollLoop() }, "kadre-win32-xinput-poll").apply {
        isDaemon = true
        start()
    }

    internal fun openSessionView(): Win32GamepadPort = synchronized(lock) {
        check(!closed) { "Win32 gamepad broker is closed" }
        Win32GamepadPort(this).also { ports += it }
    }

    internal fun pollOnce() {
        for (slot in 0 until XINPUT_SLOT_COUNT) {
            val key = slot.toLong()
            val state: Win32XInputStateValue? = runCatching { native.getState(slot) }.getOrNull()
            synchronized(lock) {
                val previous = connected[key]
                if (state == null) {
                    if (previous != null) {
                        connected.remove(key)
                        publish(GamepadPortEvent.Disconnected(key))
                    }
                } else {
                    val canonical = standardStateFrom(
                        state.buttons, state.leftTrigger, state.rightTrigger,
                        state.thumbLX, state.thumbLY, state.thumbRX, state.thumbRY,
                    )
                    if (previous == null) {
                        connected[key] = canonical
                        publish(GamepadPortEvent.Connected(gamepadFor(key, canonical)))
                    } else if (previous != canonical) {
                        connected[key] = canonical
                        publish(GamepadPortEvent.StateChanged(key, canonical))
                    }
                }
            }
        }
    }

    internal fun gamepadFor(key: Long, state: GamepadState): GamepadPortGamepad =
        GamepadPortGamepad(
            key = key,
            descriptor = GamepadDescriptor(
                name = "XInput controller $key",
                mapping = GamepadMapping.Standard,
                buttons = STANDARD_BUTTONS,
                axes = STANDARD_AXES,
            ),
            state = state,
            routing = GamepadRoutingState.Routed,
            capabilities = GamepadCapabilities(
                effects = Capability.Supported(
                    GamepadEffectConstraints(
                        kinds = setOf(GamepadEffectKind.DualRumble),
                        localizedHaptics = null,
                        maximumDuration = null,
                    ),
                    FeatureAvailability.Available,
                ),
            ),
        )

    internal fun startRumble(slot: Long, strong: Int, weak: Int): Boolean =
        native.setVibration(slot.toInt(), strong, weak)

    internal fun stopRumble(slot: Long) {
        native.setVibration(slot.toInt(), 0, 0)
    }

    internal fun snapshotGamepads(): List<GamepadPortGamepad> = synchronized(lock) {
        connected.map { (key, state) -> gamepadFor(key, state) }
    }

    internal fun publish(event: GamepadPortEvent) {
        ports.forEach { it.receive(event) }
    }

    private fun pollLoop() {
        while (!closed) {
            try {
                pollOnce()
            } catch (ignored: Throwable) {
                // A failed poll keeps the previous inventory; the next tick retries.
            }
            Thread.sleep(statePollMillis)
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            connected.clear()
            ports.toList()
        }.forEach { it.detachedFromBroker() }
    }
}

/**
 * One session view over the process broker. The runtime closes it, which
 * detaches the view without stopping the broker (its native registration is
 * process-lifetime by OS constraint).
 */
internal class Win32GamepadPort internal constructor(
    private val broker: Win32GamepadBroker,
) : GamepadPort {
    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private var observer: ((GamepadPortEvent) -> Unit)? = null

    override val inventoryFailure: KadreFailure? = null

    override val gamepads: List<GamepadPortGamepad>
        get() = broker.snapshotGamepads()

    override fun installObserver(observer: (GamepadPortEvent) -> Unit): AutoCloseable {
        synchronized(lock) {
            check(this.observer == null) { "gamepad observer is already installed" }
            this.observer = observer
        }
        return AutoCloseable {
            synchronized(lock) {
                if (this.observer === observer) this.observer = null
            }
        }
    }

    internal fun receive(event: GamepadPortEvent) {
        observer?.invoke(event)
    }

    override fun updateRouting(routing: GamepadPortRouting) {
        // The XInput broker fans events to every view; per-policy suspension is
        // enforced by the runtime manager (controls neutralized while suspended).
    }

    override fun startEffect(key: Long, effect: GamepadEffect): KadreResult<GamepadPortEffect> {
        if (closed.get()) return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Gamepad))
        val rumble = effect as? GamepadEffect.DualRumble
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("effect"))
        val strong = (rumble.strong * 65535).toInt().coerceIn(0, 0xFFFF)
        val weak = (rumble.weak * 65535).toInt().coerceIn(0, 0xFFFF)
        if (!broker.startRumble(key, strong, weak)) {
            return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Gamepad))
        }
        val portEffect = Win32XInputPortEffect(key, broker::stopRumble)
        return KadreResult.Success(portEffect)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        observer = null
    }

    internal fun detachedFromBroker() {
        close()
    }
}

/** Dual-rumble effect backed by XInputSetState; stop zeroes both motors. */
internal class Win32XInputPortEffect internal constructor(
    private val slot: Long,
    private val stopMotors: (Long) -> Unit,
) : GamepadPortEffect {
    private val closed = AtomicBoolean(false)

    override fun requestStop(): KadreResult<Unit> {
        if (closed.compareAndSet(false, true)) {
            stopMotors(slot)
        }
        return KadreResult.Success(Unit)
    }

    override fun close() {
        requestStop()
    }
}
