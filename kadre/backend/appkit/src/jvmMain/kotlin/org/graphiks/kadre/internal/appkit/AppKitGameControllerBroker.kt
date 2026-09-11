package org.graphiks.kadre.internal.appkit

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
import org.graphiks.kadre.input.GamepadHapticLocality
import org.graphiks.kadre.input.GamepadLocalizedHapticConstraints
import org.graphiks.kadre.input.GamepadMapping
import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.input.GamepadState
import org.graphiks.kadre.internal.runtime.GamepadPort
import org.graphiks.kadre.internal.runtime.GamepadPortEffect
import org.graphiks.kadre.internal.runtime.GamepadPortEvent
import org.graphiks.kadre.internal.runtime.GamepadPortGamepad
import org.graphiks.kadre.internal.runtime.GamepadPortRouting
import org.graphiks.kadre.policy.GamepadRouting
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration

/** Opens one pointer-free GameController bridge for the process-wide AppKit broker. */
internal fun interface AppKitGameControllerNativeFactory {
    /** Implementations must not invoke [listener] until this function has returned. */
    fun open(listener: (AppKitGameControllerNativeEvent) -> Unit): AppKitGameControllerNative
}

/** Pointer-free GameController bridge used only by the AppKit process broker. */
internal interface AppKitGameControllerNative : AutoCloseable {
    val controllers: List<AppKitGameControllerNativeController>

    fun observeInput(
        key: Long,
        listener: (AppKitGameControllerPhysicalInput) -> Unit,
    ): KadreResult<AutoCloseable>

    fun startEffect(
        key: Long,
        effect: GamepadEffect.LocalizedHaptic,
    ): KadreResult<AppKitGameControllerNativeEffect>

    override fun close()
}

/** A closeable native effect owner that never exposes a native pointer to Kadre. */
internal interface AppKitGameControllerNativeEffect : AutoCloseable {
    fun requestStop(): KadreResult<Unit>

    override fun close()
}

internal data class AppKitGameControllerNativeController(
    val key: Long,
    val name: String?,
    val profile: AppKitGameControllerProfile,
    val initialInputs: List<AppKitGameControllerPhysicalInput>,
    val hapticLocalities: Set<GamepadHapticLocality>,
) {
    init {
        require(key >= 0L) { "controller key must be non-negative" }
    }
}

internal enum class AppKitGameControllerProfile { Standard, Native }

internal sealed interface AppKitGameControllerNativeEvent {
    data class Connected(val controller: AppKitGameControllerNativeController) : AppKitGameControllerNativeEvent
    data class Disconnected(val key: Long) : AppKitGameControllerNativeEvent {
        init {
            require(key >= 0L) { "controller key must be non-negative" }
        }
    }
}

/** Detached physical input copied by the KFFI bridge before it reaches Kadre. */
internal sealed interface AppKitGameControllerPhysicalInput {
    val nativeNames: Set<String>

    data class Button(
        override val nativeNames: Set<String>,
        val value: Double,
        val pressed: Boolean,
    ) : AppKitGameControllerPhysicalInput

    data class Axis(
        override val nativeNames: Set<String>,
        val value: Double,
    ) : AppKitGameControllerPhysicalInput

    data class DirectionPad(
        override val nativeNames: Set<String>,
        val x: Double,
        val y: Double,
    ) : AppKitGameControllerPhysicalInput

    data class Other(
        override val nativeNames: Set<String>,
        val analog: Boolean,
    ) : AppKitGameControllerPhysicalInput
}

internal data class AppKitGameControllerPortDelivery(
    val observer: (GamepadPortEvent) -> Unit,
    val event: GamepadPortEvent,
)

/**
 * Process-wide GameController owner with session-owned [AppKitGameControllerPort] projections.
 *
 * Native input is copied once, then fanned out only to routed sessions. Lifecycle is always
 * fanned out, including to suspended projections.
 */
internal class AppKitGameControllerBroker(
    private val nativeFactory: AppKitGameControllerNativeFactory,
) : AutoCloseable {
    private val lock = Any()
    private val ports = linkedSetOf<AppKitGameControllerPort>()
    private val controllers = linkedMapOf<Long, PhysicalController>()
    private val activeEffects = linkedMapOf<Long, LinkedHashSet<AppKitGameControllerPortEffect>>()
    private val pendingEffects = linkedMapOf<Long, Int>()
    private var native: AppKitGameControllerNative? = null
    private var closed = false

    fun openPort(): AppKitGameControllerPort {
        val port = synchronized(lock) {
            check(!closed) { "AppKit GameController broker is closed" }
            AppKitGameControllerPort(this).also { opened ->
                check(ports.add(opened)) { "AppKit GameController port is already registered" }
                try {
                    ensureNativeLocked()
                    opened.installInitialLocked(controllers.values.map { controller -> sourceForLocked(opened, controller) })
                } catch (failure: Throwable) {
                    ports.remove(opened)
                    throw failure
                }
            }
        }
        return port
    }

    override fun close() {
        val release = synchronized(lock) {
            if (closed) return
            closed = true
            ports.forEach(AppKitGameControllerPort::closeFromBrokerLocked)
            ports.clear()
            releaseNativeLocked()
        }
        release.close()
    }

    internal fun gamepads(port: AppKitGameControllerPort): List<GamepadPortGamepad> = synchronized(lock) {
        if (!port.isOpenLocked()) return@synchronized emptyList()
        controllers.values.map { controller -> sourceForLocked(port, controller) }
    }

    internal fun installObserver(
        port: AppKitGameControllerPort,
        observer: (GamepadPortEvent) -> Unit,
    ): AutoCloseable = synchronized(lock) {
        check(port.isOpenLocked()) { "AppKit GameController port is closed" }
        port.installObserverLocked(observer)
    }

    internal fun updateRouting(port: AppKitGameControllerPort, routing: GamepadPortRouting) {
        val deliveries = synchronized(lock) {
            if (!port.isOpenLocked()) return
            port.updateRoutingLocked(routing)
            reconcileRoutingLocked()
        }
        deliver(deliveries)
    }

    internal fun startEffect(
        port: AppKitGameControllerPort,
        key: Long,
        effect: GamepadEffect,
    ): KadreResult<GamepadPortEffect> {
        val admission = synchronized(lock) {
            if (!port.isOpenLocked()) {
                return@synchronized KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Gamepad))
            }
            val controller = controllers[key]
                ?: return@synchronized KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Gamepad))
            val localized = effect as? GamepadEffect.LocalizedHaptic
                ?: return@synchronized KadreResult.Failure(KadreFailure.InvalidRequest("effect"))
            if (localized.locality !in controller.native.hapticLocalities) {
                return@synchronized KadreResult.Failure(KadreFailure.InvalidRequest("effect"))
            }
            if ((activeEffects[key]?.isNotEmpty() == true) || (pendingEffects[key] ?: 0) > 0) {
                return@synchronized KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.GamepadEffect))
            }
            pendingEffects[key] = (pendingEffects[key] ?: 0) + 1
            KadreResult.Success(localized)
        }
        val localized = when (admission) {
            is KadreResult.Failure -> return admission
            is KadreResult.Success -> admission.value
        }
        val started = checkNotNull(native).startEffect(key, localized)
        val owner = when (started) {
            is KadreResult.Failure -> {
                synchronized(lock) { releasePendingEffectLocked(key) }
                return started
            }

            is KadreResult.Success -> started.value
        }
        val portEffect = synchronized(lock) {
            releasePendingEffectLocked(key)
            if (!port.isOpenLocked() || key !in controllers || closed) {
                null
            } else {
                AppKitGameControllerPortEffect(this, port, key, owner).also { effectOwner ->
                    activeEffects.getOrPut(key, ::linkedSetOf).add(effectOwner)
                }
            }
        }
        if (portEffect == null) {
            runCatching(owner::close)
            return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Gamepad))
        }
        return KadreResult.Success(portEffect)
    }

    internal fun closePort(port: AppKitGameControllerPort) {
        val release = synchronized(lock) {
            if (!ports.remove(port)) return
            port.closeFromBrokerLocked()
            val effects = detachEffectsLocked { owner -> owner.port === port }
            if (ports.isEmpty()) effects to releaseNativeLocked() else effects to NativeRelease.None
        }
        release.first.forEach(AppKitGameControllerPortEffect::closeFromBroker)
        release.second.close()
    }

    internal fun releaseEffect(effect: AppKitGameControllerPortEffect) {
        synchronized(lock) {
            activeEffects[effect.key]?.let { owners ->
                owners.remove(effect)
                if (owners.isEmpty()) activeEffects.remove(effect.key)
            }
        }
    }

    private fun acceptNativeEvent(event: AppKitGameControllerNativeEvent) {
        val release = synchronized(lock) {
            if (closed || native == null) return
            when (event) {
                is AppKitGameControllerNativeEvent.Connected -> NativeAcceptance(
                    deliveries = connectLocked(event.controller),
                    effects = emptyList(),
                )

                is AppKitGameControllerNativeEvent.Disconnected -> disconnectLocked(event.key)
            }
        }
        release.effects.forEach(AppKitGameControllerPortEffect::closeFromBroker)
        deliver(release.deliveries)
    }

    private fun acceptInput(key: Long, input: AppKitGameControllerPhysicalInput) {
        val deliveries = synchronized(lock) {
            val controller = controllers[key] ?: return
            controller.update(input)
            ports.filter { port -> routedLocked(port) && port.knowsLocked(key) }
                .mapNotNull { port ->
                    port.stateChangedLocked(key, controller.state())
                }
        }
        deliver(deliveries)
    }

    private fun ensureNativeLocked() {
        if (native != null) return
        val opened = nativeFactory.open(::acceptNativeEvent)
        native = opened
        opened.controllers.forEach(::connectInitialLocked)
    }

    private fun connectInitialLocked(controller: AppKitGameControllerNativeController) {
        if (controllers.containsKey(controller.key)) return
        registerControllerLocked(controller)
    }

    private fun connectLocked(controller: AppKitGameControllerNativeController): List<AppKitGameControllerPortDelivery> {
        if (controllers.containsKey(controller.key)) return emptyList()
        if (!registerControllerLocked(controller)) return emptyList()
        val physical = checkNotNull(controllers[controller.key])
        return ports.mapNotNull { port -> port.connectedLocked(sourceForLocked(port, physical)) }
    }

    private fun registerControllerLocked(controller: AppKitGameControllerNativeController): Boolean {
        val physical = PhysicalController(controller)
        controllers[controller.key] = physical
        val observation = checkNotNull(native).observeInput(controller.key) { input -> acceptInput(controller.key, input) }
        return when (observation) {
            is KadreResult.Success -> {
                physical.observation = observation.value
                true
            }

            is KadreResult.Failure -> {
                controllers.remove(controller.key)
                false
            }
        }
    }

    private fun disconnectLocked(key: Long): NativeAcceptance {
        val controller = controllers.remove(key) ?: return NativeAcceptance(emptyList(), emptyList())
        val effects = detachEffectsLocked { owner -> owner.key == key }
        runCatching { controller.observation?.close() }
        return NativeAcceptance(
            deliveries = ports.mapNotNull { port -> port.disconnectedLocked(key) },
            effects = effects,
        )
    }

    private fun reconcileRoutingLocked(): List<AppKitGameControllerPortDelivery> = buildList {
        ports.forEach { port ->
            controllers.values.forEach { controller ->
                val routing = routingForLocked(port)
                if (port.routingForLocked(controller.native.key) != routing) {
                    port.routingChangedLocked(controller.native.key, routing, controller.state())?.let(::add)
                }
            }
        }
    }

    private fun sourceForLocked(
        port: AppKitGameControllerPort,
        controller: PhysicalController,
    ): GamepadPortGamepad = GamepadPortGamepad(
        key = controller.native.key,
        descriptor = controller.descriptor,
        state = controller.state(),
        routing = routingForLocked(port),
        capabilities = controller.capabilities,
    )

    private fun routingForLocked(port: AppKitGameControllerPort): GamepadRoutingState =
        if (routedLocked(port)) GamepadRoutingState.Routed else GamepadRoutingState.Suspended

    private fun routedLocked(port: AppKitGameControllerPort): Boolean {
        val context = port.routingLocked() ?: return false
        if (!context.foregroundActive) return false
        return when (context.policy) {
            GamepadRouting.AllForegroundSessions -> true
            GamepadRouting.ActiveSessionOnly -> ports.firstOrNull { candidate ->
                candidate.routingLocked()?.let { routing ->
                    routing.policy == GamepadRouting.ActiveSessionOnly && routing.foregroundActive
                } == true
            } === port
        }
    }

    private fun detachEffectsLocked(
        predicate: (AppKitGameControllerPortEffect) -> Boolean,
    ): List<AppKitGameControllerPortEffect> = buildList {
        activeEffects.values.forEach { owners -> addAll(owners.filter(predicate)) }
    }.also { removed ->
        removed.forEach(::releaseEffect)
    }

    private fun releasePendingEffectLocked(key: Long) {
        val pending = checkNotNull(pendingEffects[key]) { "missing gamepad effect reservation" }
        if (pending == 1) pendingEffects.remove(key) else pendingEffects[key] = pending - 1
    }

    private fun releaseNativeLocked(): NativeRelease {
        val effects = detachEffectsLocked { true }
        val observations = controllers.values.mapNotNull(PhysicalController::observation)
        controllers.clear()
        pendingEffects.clear()
        val monitor = native.also { native = null }
        return NativeRelease(effects, observations, monitor)
    }

    private fun deliver(deliveries: List<AppKitGameControllerPortDelivery>) {
        deliveries.forEach { delivery -> delivery.observer(delivery.event) }
    }

    private data class NativeAcceptance(
        val deliveries: List<AppKitGameControllerPortDelivery>,
        val effects: List<AppKitGameControllerPortEffect>,
    )

    private class NativeRelease(
        private val effects: List<AppKitGameControllerPortEffect>,
        private val observations: List<AutoCloseable>,
        private val monitor: AppKitGameControllerNative?,
    ) {
        fun close() {
            effects.forEach(AppKitGameControllerPortEffect::closeFromBroker)
            observations.forEach { observation -> runCatching(observation::close) }
            runCatching { monitor?.close() }
        }

        companion object {
            val None = NativeRelease(emptyList(), emptyList(), null)
        }
    }

    private class PhysicalController(
        val native: AppKitGameControllerNativeController,
    ) {
        private val inputs = linkedMapOf<String, AppKitGameControllerPhysicalInput>()
        var observation: AutoCloseable? = null

        val descriptor: GamepadDescriptor = descriptorFor(native).also { descriptor ->
            native.initialInputs.forEach { input -> inputs[input.key()] = input }
            stateFor(native.profile, descriptor, inputs.values)
        }
        val capabilities: GamepadCapabilities = capabilitiesFor(native.hapticLocalities)

        fun update(input: AppKitGameControllerPhysicalInput) {
            inputs[input.key()] = input
        }

        fun state(): GamepadState = stateFor(native.profile, descriptor, inputs.values)
    }
}

/** One session-owned port into [AppKitGameControllerBroker]. */
internal class AppKitGameControllerPort internal constructor(
    private val broker: AppKitGameControllerBroker,
) : GamepadPort {
    private var closed = false
    private var observer: ((GamepadPortEvent) -> Unit)? = null
    private var routing: GamepadPortRouting? = null
    private val known = linkedSetOf<Long>()
    private val projectedRouting = linkedMapOf<Long, GamepadRoutingState>()

    override val gamepads: List<GamepadPortGamepad>
        get() = broker.gamepads(this)

    override fun installObserver(observer: (GamepadPortEvent) -> Unit): AutoCloseable =
        broker.installObserver(this, observer)

    override fun updateRouting(routing: GamepadPortRouting) {
        broker.updateRouting(this, routing)
    }

    override fun startEffect(key: Long, effect: GamepadEffect): KadreResult<GamepadPortEffect> =
        broker.startEffect(this, key, effect)

    override fun close() {
        broker.closePort(this)
    }

    internal fun isOpenLocked(): Boolean = !closed

    internal fun installInitialLocked(gamepads: List<GamepadPortGamepad>) {
        gamepads.forEach { gamepad ->
            known += gamepad.key
            projectedRouting[gamepad.key] = gamepad.routing
        }
    }

    internal fun installObserverLocked(observer: (GamepadPortEvent) -> Unit): AutoCloseable {
        check(this.observer == null) { "GameController observer is already installed" }
        this.observer = observer
        return AutoCloseable {
            if (this.observer === observer) this.observer = null
        }
    }

    internal fun updateRoutingLocked(routing: GamepadPortRouting) {
        this.routing = routing
    }

    internal fun routingLocked(): GamepadPortRouting? = routing

    internal fun knowsLocked(key: Long): Boolean = key in known

    internal fun routingForLocked(key: Long): GamepadRoutingState? = projectedRouting[key]

    internal fun connectedLocked(source: GamepadPortGamepad): AppKitGameControllerPortDelivery? {
        known += source.key
        projectedRouting[source.key] = source.routing
        return observer?.let { target -> AppKitGameControllerPortDelivery(target, GamepadPortEvent.Connected(source)) }
    }

    internal fun disconnectedLocked(key: Long): AppKitGameControllerPortDelivery? {
        if (!known.remove(key)) return null
        projectedRouting.remove(key)
        return observer?.let { target -> AppKitGameControllerPortDelivery(target, GamepadPortEvent.Disconnected(key)) }
    }

    internal fun stateChangedLocked(key: Long, state: GamepadState): AppKitGameControllerPortDelivery? =
        observer?.let { target -> AppKitGameControllerPortDelivery(target, GamepadPortEvent.StateChanged(key, state)) }

    internal fun routingChangedLocked(
        key: Long,
        routing: GamepadRoutingState,
        state: GamepadState,
    ): AppKitGameControllerPortDelivery? {
        projectedRouting[key] = routing
        return observer?.let { target -> AppKitGameControllerPortDelivery(target, GamepadPortEvent.RoutingChanged(key, routing, state)) }
    }

    internal fun closeFromBrokerLocked() {
        closed = true
        observer = null
        known.clear()
        projectedRouting.clear()
    }
}

internal class AppKitGameControllerPortEffect(
    private val broker: AppKitGameControllerBroker,
    val port: AppKitGameControllerPort,
    val key: Long,
    private val native: AppKitGameControllerNativeEffect,
) : GamepadPortEffect {
    private val closed = AtomicBoolean()

    override fun requestStop(): KadreResult<Unit> = if (closed.get()) {
        KadreResult.Success(Unit)
    } else {
        native.requestStop()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                native.close()
            } finally {
                broker.releaseEffect(this)
            }
        }
    }

    fun closeFromBroker() = close()
}

private fun descriptorFor(native: AppKitGameControllerNativeController): GamepadDescriptor {
    val mappings = native.initialInputs.flatMap { input -> controlsFor(native.profile, input) }
    val buttons = mappings.mapNotNull(ControlMapping::button).distinct().sortedButtons(native.profile)
    val axes = mappings.mapNotNull(ControlMapping::axis).distinct().sortedAxes(native.profile)
    return GamepadDescriptor(
        name = native.name,
        mapping = when (native.profile) {
            AppKitGameControllerProfile.Standard -> GamepadMapping.Standard
            AppKitGameControllerProfile.Native -> GamepadMapping.Native
        },
        buttons = buttons,
        axes = axes,
    )
}

private fun stateFor(
    profile: AppKitGameControllerProfile,
    descriptor: GamepadDescriptor,
    inputs: Collection<AppKitGameControllerPhysicalInput>,
): GamepadState {
    val buttons = linkedMapOf<GamepadButton, GamepadButtonValue>()
    val axes = linkedMapOf<GamepadAxis, GamepadAxisValue>()
    inputs.forEach { input ->
        controlsFor(profile, input).forEach { mapping ->
            mapping.button?.let { button ->
                val value = input as? AppKitGameControllerPhysicalInput.Button
                if (value != null) buttons[button] = GamepadButtonValue(button, value.value, value.pressed)
            }
            mapping.axis?.let { axis ->
                val value = when (input) {
                    is AppKitGameControllerPhysicalInput.Axis -> input.value
                    is AppKitGameControllerPhysicalInput.DirectionPad -> when (mapping.component) {
                        Component.X -> input.x
                        Component.Y -> input.y
                        null -> null
                    }

                    else -> null
                }
                if (value != null) axes[axis] = GamepadAxisValue(axis, value)
            }
        }
    }
    return GamepadState(
        buttons = descriptor.buttons.map { button -> buttons[button] ?: GamepadButtonValue(button, 0.0, false) },
        axes = descriptor.axes.map { axis -> axes[axis] ?: GamepadAxisValue(axis, 0.0) },
    )
}

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

private data class ControlMapping(
    val button: GamepadButton? = null,
    val axis: GamepadAxis? = null,
    val component: Component? = null,
)

private enum class Component { X, Y }

private fun controlsFor(
    profile: AppKitGameControllerProfile,
    input: AppKitGameControllerPhysicalInput,
): List<ControlMapping> = when (profile) {
    AppKitGameControllerProfile.Standard -> standardControlsFor(input)
    AppKitGameControllerProfile.Native -> nativeControlsFor(input)
}

private fun standardControlsFor(input: AppKitGameControllerPhysicalInput): List<ControlMapping> = when (input) {
    is AppKitGameControllerPhysicalInput.Button -> standardButton(input.nativeNames)
        ?.let { button -> listOf(ControlMapping(button = button)) }
        ?: nativeButton(input)?.let { button -> listOf(ControlMapping(button = button)) }.orEmpty()

    is AppKitGameControllerPhysicalInput.Axis -> standardAxis(input.nativeNames)
        ?.let { axis -> listOf(ControlMapping(axis = axis)) }
        ?: nativeAxis(input)?.let { axis -> listOf(ControlMapping(axis = axis)) }.orEmpty()

    is AppKitGameControllerPhysicalInput.DirectionPad -> standardDirectionPad(input.nativeNames)
        ?.let { (x, y) -> listOf(ControlMapping(axis = x, component = Component.X), ControlMapping(axis = y, component = Component.Y)) }
        ?: nativeDirectionPad(input)

    is AppKitGameControllerPhysicalInput.Other -> emptyList()
}

private fun nativeControlsFor(input: AppKitGameControllerPhysicalInput): List<ControlMapping> = when (input) {
    is AppKitGameControllerPhysicalInput.Button -> nativeButton(input)?.let { button -> listOf(ControlMapping(button = button)) }.orEmpty()
    is AppKitGameControllerPhysicalInput.Axis -> nativeAxis(input)?.let { axis -> listOf(ControlMapping(axis = axis)) }.orEmpty()
    is AppKitGameControllerPhysicalInput.DirectionPad -> nativeDirectionPad(input)
    is AppKitGameControllerPhysicalInput.Other -> emptyList()
}

private fun standardButton(names: Set<String>): GamepadButton? = when {
    names.any { it in setOf("Button A", "GCInputButtonA") } -> GamepadButton.South
    names.any { it in setOf("Button B", "GCInputButtonB") } -> GamepadButton.East
    names.any { it in setOf("Button X", "GCInputButtonX") } -> GamepadButton.West
    names.any { it in setOf("Button Y", "GCInputButtonY") } -> GamepadButton.North
    names.any { it in setOf("Left Shoulder", "GCInputLeftShoulder") } -> GamepadButton.LeftShoulder
    names.any { it in setOf("Right Shoulder", "GCInputRightShoulder") } -> GamepadButton.RightShoulder
    names.any { it in setOf("Left Trigger", "GCInputLeftTrigger") } -> GamepadButton.LeftTrigger
    names.any { it in setOf("Right Trigger", "GCInputRightTrigger") } -> GamepadButton.RightTrigger
    names.any { it in setOf("Options Button", "GCInputButtonOptions") } -> GamepadButton.Select
    names.any { it in setOf("Menu Button", "GCInputButtonMenu") } -> GamepadButton.Start
    names.any { it in setOf("Home Button", "GCInputButtonHome") } -> GamepadButton.Mode
    names.any { it in setOf("Left Thumbstick Button", "GCInputLeftThumbstickButton") } -> GamepadButton.LeftStick
    names.any { it in setOf("Right Thumbstick Button", "GCInputRightThumbstickButton") } -> GamepadButton.RightStick
    else -> null
}

private fun standardAxis(names: Set<String>): GamepadAxis? = when {
    names.any { it in setOf("Left Thumbstick X-Axis", "GCInputLeftThumbstickX") } -> GamepadAxis.LeftX
    names.any { it in setOf("Left Thumbstick Y-Axis", "GCInputLeftThumbstickY") } -> GamepadAxis.LeftY
    names.any { it in setOf("Right Thumbstick X-Axis", "GCInputRightThumbstickX") } -> GamepadAxis.RightX
    names.any { it in setOf("Right Thumbstick Y-Axis", "GCInputRightThumbstickY") } -> GamepadAxis.RightY
    names.any { it in setOf("Left Trigger", "GCInputLeftTrigger") } -> GamepadAxis.LeftTrigger
    names.any { it in setOf("Right Trigger", "GCInputRightTrigger") } -> GamepadAxis.RightTrigger
    else -> null
}

private fun standardDirectionPad(names: Set<String>): Pair<GamepadAxis, GamepadAxis>? = when {
    names.any { it in setOf("Left Thumbstick", "GCInputLeftThumbstick") } -> GamepadAxis.LeftX to GamepadAxis.LeftY
    names.any { it in setOf("Right Thumbstick", "GCInputRightThumbstick") } -> GamepadAxis.RightX to GamepadAxis.RightY
    names.any { it in setOf("Direction Pad", "GCInputDirectionPad") } -> GamepadAxis.DpadX to GamepadAxis.DpadY
    else -> null
}

private fun nativeButton(input: AppKitGameControllerPhysicalInput): GamepadButton.Other? =
    input.nativeCode()?.let(GamepadButton::Other)

private fun nativeAxis(input: AppKitGameControllerPhysicalInput): GamepadAxis.Other? =
    input.nativeCode()?.let(GamepadAxis::Other)

private fun nativeDirectionPad(input: AppKitGameControllerPhysicalInput): List<ControlMapping> {
    val code = input.nativeCode() ?: return emptyList()
    return listOf(
        ControlMapping(axis = GamepadAxis.Other("$code-x"), component = Component.X),
        ControlMapping(axis = GamepadAxis.Other("$code-y"), component = Component.Y),
    )
}

private fun AppKitGameControllerPhysicalInput.key(): String = nativeNames.sorted().joinToString(separator = "\u0000")

private fun AppKitGameControllerPhysicalInput.nativeCode(): String? = nativeNames.minOrNull()?.let { name ->
    buildString {
        append("native-")
        name.encodeToByteArray().forEach { byte -> append(byte.toUByte().toString(16).padStart(2, '0')) }
    }
}

private fun List<GamepadButton>.sortedButtons(profile: AppKitGameControllerProfile): List<GamepadButton> = when (profile) {
    AppKitGameControllerProfile.Standard -> sortedWith(compareBy { button -> standardButtons.indexOf(button).takeIf { it >= 0 } ?: Int.MAX_VALUE })
    AppKitGameControllerProfile.Native -> sortedBy { button -> (button as GamepadButton.Other).nativeCode }
}

private fun List<GamepadAxis>.sortedAxes(profile: AppKitGameControllerProfile): List<GamepadAxis> = when (profile) {
    AppKitGameControllerProfile.Standard -> sortedWith(compareBy { axis -> standardAxes.indexOf(axis).takeIf { it >= 0 } ?: Int.MAX_VALUE })
    AppKitGameControllerProfile.Native -> sortedBy { axis -> (axis as GamepadAxis.Other).nativeCode }
}

private val standardButtons = listOf(
    GamepadButton.South,
    GamepadButton.East,
    GamepadButton.West,
    GamepadButton.North,
    GamepadButton.LeftShoulder,
    GamepadButton.RightShoulder,
    GamepadButton.LeftTrigger,
    GamepadButton.RightTrigger,
    GamepadButton.Select,
    GamepadButton.Start,
    GamepadButton.Mode,
    GamepadButton.LeftStick,
    GamepadButton.RightStick,
)

private val standardAxes = listOf(
    GamepadAxis.LeftX,
    GamepadAxis.LeftY,
    GamepadAxis.RightX,
    GamepadAxis.RightY,
    GamepadAxis.LeftTrigger,
    GamepadAxis.RightTrigger,
    GamepadAxis.DpadX,
    GamepadAxis.DpadY,
)
