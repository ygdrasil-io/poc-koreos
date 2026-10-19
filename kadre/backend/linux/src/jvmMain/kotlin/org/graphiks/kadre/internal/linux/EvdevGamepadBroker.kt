package org.graphiks.kadre.internal.linux

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.GamepadCapabilities
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.input.GamepadEffectConstraints
import org.graphiks.kadre.input.GamepadEffectKind
import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.input.KadrePermission
import org.graphiks.kadre.internal.runtime.GamepadPort
import org.graphiks.kadre.internal.runtime.GamepadPortEffect
import org.graphiks.kadre.internal.runtime.GamepadPortEvent
import org.graphiks.kadre.internal.runtime.GamepadPortGamepad
import org.graphiks.kadre.internal.runtime.GamepadPortRouting
import org.graphiks.kadre.policy.DeviceEffectOwnership

/**
 * Process-wide evdev gamepad service (design D3): one worker thread owns
 * the device fds and the udev drain — host threads never block on input.
 * Each session opens one [EvdevGamepadPort] view; routing arbitrates who
 * receives state. Fakes drive the [EvdevNativeSource] seam in the O2
 * suite; the uinput oracle drives it for O3.
 */
internal class EvdevGamepadBroker(
    private val source: EvdevNativeSource,
) : AutoCloseable {

    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private val gamepads = LinkedHashMap<Long, EvdevGamepad>()
    private val nodes = HashMap<Long, EvdevNativeGamepad>()
    private val keysByDevNode = HashMap<String, Long>()

    // identity → stable base key; twins of one identity get +ordinal (D2)
    private val baseKeysByIdentity = HashMap<String, Long>()
    private val nextBaseKey = AtomicInteger(1)

    private val views = LinkedHashMap<Int, EvdevGamepadPort>()
    private val routing = HashMap<Int, GamepadPortRouting>()
    private val nextViewId = AtomicInteger(1)

    // one live effect per device while ownership is exclusive (D4)
    private val activeEffects = HashMap<Long, EvdevPortEffect>()
    private var worker: Thread? = null

    /**
     * Starts the service. [spawnWorker] false keeps everything
     * synchronous — the O2 suite drives [pumpOnce] by hand.
     */
    fun start(spawnWorker: Boolean = true) {
        if (closed.get()) return
        synchronized(lock) { reconcileInventoryLocked() }
        if (!spawnWorker) return
        worker = Thread(::run, "kadre-evdev-broker").apply {
            isDaemon = true
            start()
        }
    }

    /** One session view; closing it removes the view, not the broker. */
    fun openSessionView(): EvdevGamepadPort = synchronized(lock) {
        val id = nextViewId.getAndIncrement()
        val view = EvdevGamepadPort(id, this)
        views[id] = view
        view
    }

    internal fun updateRouting(viewId: Int, value: GamepadPortRouting) {
        val resumed = synchronized(lock) {
            val previous = routing.put(viewId, value)
            val becameEligible = value.isEligible && (previous?.isEligible != true)
            views[viewId]?.takeIf { becameEligible }?.let { view ->
                view to gamepads.values.map { gamepad ->
                    GamepadPortEvent.RoutingChanged(gamepad.key, GamepadRoutingState.Routed, gamepad.snapshotState())
                }
            }
        }
        // delivered without the broker lock; the view serializes internally
        resumed?.let { (view, events) -> events.forEach(view::offer) }
    }

    internal fun removeView(viewId: Int) {
        synchronized(lock) {
            views.remove(viewId)
            routing.remove(viewId)
        }
    }

    internal fun snapshotGamepads(viewId: Int): List<GamepadPortGamepad> = synchronized(lock) {
        val routingState = routingStateFor(viewId)
        gamepads.values.map(::projectionFor).map { it(routingState) }
    }

    internal fun inventoryFailure(): KadreFailure? {
        val failure = source.enumerationFailure ?: return null
        return if (failure.permissionDenied) {
            KadreFailure.PermissionDenied(KadrePermission.InputMonitoring)
        } else {
            KadreFailure.PlatformFailure(KadrePlatform.Linux, "gamepad", "enumeration-unavailable")
        }
    }

    /** Uploads and plays one effect; the owner stops and releases it (D4). */
    internal fun startEffect(key: Long, effect: GamepadEffect): KadreResult<GamepadPortEffect> {
        val rumble = effect as? GamepadEffect.DualRumble
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("effect"))
        val node: EvdevNativeGamepad
        synchronized(lock) {
            when {
                closed.get() -> return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Gamepad))
                !gamepads.containsKey(key) -> return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Gamepad))
                else -> {
                    val exclusive = routing.values.any { it.effectOwnership == DeviceEffectOwnership.ExclusivePerPhysicalDevice }
                    if (exclusive && activeEffects.containsKey(key)) {
                        return KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Gamepad))
                    }
                    node = nodes[key] ?: return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Gamepad))
                }
            }
        }
        val uploaded = runCatching {
            node.uploadRumble(
                strong = rumble.strong,
                weak = rumble.weak,
                durationMillis = rumble.duration.inWholeMilliseconds.coerceAtLeast(1L).toInt(),
            )
        }.getOrNull()
            ?: return KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.Linux, "gamepad-effect", "upload-refused"),
            )
        val exclusive = synchronized(lock) {
            routing.values.any { it.effectOwnership == DeviceEffectOwnership.ExclusivePerPhysicalDevice }
        }
        val owner = EvdevPortEffect(uploaded)
        if (!uploaded.play()) {
            owner.close()
            return KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.Linux, "gamepad-effect", "play-refused"),
            )
        }
        if (exclusive) {
            synchronized(lock) {
                activeEffects[key] = owner
                owner.onReleased = {
                    synchronized(lock) {
                        if (activeEffects[key] === owner) activeEffects.remove(key)
                    }
                }
            }
        }
        return KadreResult.Success(owner)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        worker?.let { thread -> runCatching { thread.join(BROKER_JOIN_TIMEOUT_MILLIS) } }
        val effects: List<EvdevPortEffect>
        val nodeList: List<EvdevNativeGamepad>
        synchronized(lock) {
            effects = activeEffects.values.toList()
            activeEffects.clear()
            views.values.forEach(EvdevGamepadPort::markClosed)
            views.clear()
            nodeList = nodes.values.toList()
            nodes.clear()
            gamepads.clear()
            keysByDevNode.clear()
        }
        effects.forEach { owner -> runCatching(owner::close) }
        nodeList.forEach { node -> runCatching(node::close) }
        runCatching(source::close)
    }

    // ------------------------------------------------------------- worker

    /** One drain+poll pass; true when anything happened. */
    internal fun pumpOnce(): Boolean = drainHotplug() or pollGamepads()

    private fun run() {
        var lastReconcile = 0L
        while (!closed.get()) {
            val worked = pumpOnce()
            val now = System.currentTimeMillis()
            if (now - lastReconcile >= RECONCILE_MILLIS) {
                lastReconcile = now
                // missed-hotplug safety net: cheap to re-read, idempotent
                synchronized(lock) { reconcileInventoryLocked() }
            }
            if (!worked) runCatching { Thread.sleep(POLL_IDLE_MILLIS) }
        }
    }

    private fun drainHotplug(): Boolean {
        var worked = false
        while (true) {
            val hotplug = runCatching { source.pollHotplug() }.getOrNull() ?: break
            worked = true
            when (hotplug.action) {
                "add" -> addDevice(hotplug.devNode)
                "remove" -> removeDevice(hotplug.devNode)
            }
        }
        return worked
    }

    /** Cold-plug reconciliation: the enumerated list is truth for presence. */
    private fun reconcileInventoryLocked() {
        val listed = source.listDevNodes().toSet()
        listed.forEach(::addDeviceLocked)
        keysByDevNode.keys.toList().filter { it !in listed }.forEach(::removeDeviceLocked)
    }

    private fun addDevice(devNode: String) {
        synchronized(lock) { addDeviceLocked(devNode) }
    }

    private fun addDeviceLocked(devNode: String) {
        if (closed.get() || keysByDevNode.containsKey(devNode)) return
        val node = runCatching { source.openGamepad(devNode) }.getOrNull() ?: return
        val base = baseKeysByIdentity.getOrPut(node.identity) { nextBaseKey.getAndIncrement().toLong() }
        // lowest free slot of the identity: a replug reclaims its key (D2),
        // simultaneous twins take consecutive slots
        var key = base * IDENTITY_KEY_STRIDE
        while (gamepads.containsKey(key)) key += 1
        val gamepad = EvdevGamepad(
            key = key,
            identity = node.identity,
            name = node.name,
            buttonCodes = node.buttonCodes,
            axisCodes = node.axisCodes,
            axisBounds = buildMap { node.axisCodes.forEach { axis -> node.axisBounds(axis)?.let { put(axis, it) } } },
            syntheticButtonCodes = node.syntheticButtonCodes,
            forceFeedback = node.forceFeedback,
        )
        gamepads[key] = gamepad
        nodes[key] = node
        keysByDevNode[devNode] = key
        broadcastLocked { view ->
            listOf(
                GamepadPortEvent.Connected(projectionFor(gamepad)(routingStateFor(view.viewId))),
                GamepadPortEvent.InventoryChanged,
            )
        }
    }

    private fun removeDevice(devNode: String) {
        synchronized(lock) { removeDeviceLocked(devNode) }
    }

    private fun removeDeviceLocked(devNode: String) {
        val key = keysByDevNode.remove(devNode) ?: return
        val gamepad = gamepads.remove(key)
        val node = nodes.remove(key)
        activeEffects.remove(key)?.let { owner -> runCatching(owner::close) }
        if (gamepad != null) {
            broadcastLocked { _ ->
                listOf(
                    GamepadPortEvent.Disconnected(key),
                    GamepadPortEvent.InventoryChanged,
                )
            }
        }
        node?.let { runCatching(it::close) }
    }

    private fun pollGamepads(): Boolean {
        val snapshot: List<Pair<EvdevGamepad, EvdevNativeGamepad>> = synchronized(lock) {
            gamepads.values.mapNotNull { gamepad -> nodes[gamepad.key]?.let { gamepad to it } }
        }
        var worked = false
        val retired = ArrayList<Long>()
        snapshot.forEach { (gamepad, node) ->
            if (node.isDisconnected) {
                // the kernel reports the device gone (unplug/uinput destroy):
                // retire it even though its node file may still be listed
                retired.add(gamepad.key)
                worked = true
                return@forEach
            }
            val events = runCatching { node.readEvents() }.getOrDefault(emptyList())
            if (events.isEmpty()) return@forEach
            worked = true
            var changed = false
            events.forEach { event ->
                if (gamepad.apply(event.type, event.code, event.value)) changed = true
            }
            if (changed) publishState(gamepad)
        }
        if (retired.isNotEmpty()) retireDevices(retired)
        return worked
    }

    private fun retireDevices(keys: List<Long>) {
        synchronized(lock) {
            keys.forEach { key ->
                val devNode = keysByDevNode.entries.firstOrNull { it.value == key }?.key
                if (devNode != null) {
                    removeDeviceLocked(devNode)
                } else {
                    gamepads.remove(key)
                    val node = nodes.remove(key)
                    activeEffects.remove(key)?.let { owner -> runCatching(owner::close) }
                    node?.let { runCatching(it::close) }
                }
            }
        }
    }

    private fun publishState(gamepad: EvdevGamepad) {
        val targets: List<Pair<EvdevGamepadPort, GamepadPortEvent>> = synchronized(lock) {
            val state = gamepad.snapshotState()
            views.values
                .filter { view -> routingStateFor(view.viewId) == GamepadRoutingState.Routed }
                .map { view -> view to GamepadPortEvent.StateChanged(gamepad.key, state) }
        }
        targets.forEach { (view, event) -> view.offer(event) }
    }

    private fun broadcastLocked(events: (EvdevGamepadPort) -> List<GamepadPortEvent>) {
        views.values.toList().forEach { view -> events(view).forEach(view::offer) }
    }

    /** Builds the projection with the view's routing substituted at call time. */
    private fun projectionFor(gamepad: EvdevGamepad): (GamepadRoutingState) -> GamepadPortGamepad = { routingState ->
        GamepadPortGamepad(
            key = gamepad.key,
            descriptor = gamepad.descriptor,
            state = gamepad.snapshotState(),
            routing = routingState,
            capabilities = capabilitiesFor(gamepad),
        )
    }

    private fun routingStateFor(viewId: Int): GamepadRoutingState {
        val value = routing[viewId]
        return if (value?.isEligible ?: DEFAULT_ELIGIBLE) GamepadRoutingState.Routed else GamepadRoutingState.Suspended
    }

    private fun capabilitiesFor(gamepad: EvdevGamepad): GamepadCapabilities = if (gamepad.forceFeedback) {
        GamepadCapabilities(
            effects = Capability.Supported(
                constraints = GamepadEffectConstraints(
                    kinds = setOf(GamepadEffectKind.DualRumble),
                    localizedHaptics = null,
                    maximumDuration = null,
                ),
                availability = FeatureAvailability.Available,
            ),
        )
    } else {
        GamepadCapabilities(
            effects = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.GamepadEffect)),
        )
    }

    private val GamepadPortRouting.isEligible: Boolean
        get() = foregroundActive

    private companion object {
        const val POLL_IDLE_MILLIS = 10L
        const val RECONCILE_MILLIS = 1_000L
        const val BROKER_JOIN_TIMEOUT_MILLIS = 2_000L
        const val IDENTITY_KEY_STRIDE = 64L
        const val DEFAULT_ELIGIBLE = true
    }
}

/** Per-session view over the broker (one [GamepadPort] per session). */
internal class EvdevGamepadPort(
    internal val viewId: Int,
    private val broker: EvdevGamepadBroker,
) : GamepadPort {

    private val closed = AtomicBoolean(false)
    private val observers = ArrayList<(GamepadPortEvent) -> Unit>()
    private val observerLock = Any()

    override val inventoryFailure: KadreFailure?
        get() = if (closed.get()) null else broker.inventoryFailure()

    override val gamepads: List<GamepadPortGamepad>
        get() = if (closed.get()) emptyList() else broker.snapshotGamepads(viewId)

    override fun installObserver(observer: (GamepadPortEvent) -> Unit): AutoCloseable {
        if (closed.get()) return AutoCloseable { }
        synchronized(observerLock) { observers.add(observer) }
        return AutoCloseable { synchronized(observerLock) { observers.remove(observer) } }
    }

    override fun updateRouting(value: GamepadPortRouting) {
        if (closed.get()) return
        broker.updateRouting(viewId, value)
    }

    override fun startEffect(key: Long, effect: GamepadEffect): KadreResult<GamepadPortEffect> {
        if (closed.get()) return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Gamepad))
        return broker.startEffect(key, effect)
    }

    internal fun offer(event: GamepadPortEvent) {
        if (closed.get()) return
        val targets: List<(GamepadPortEvent) -> Unit>
        synchronized(observerLock) { targets = observers.toList() }
        targets.forEach { observer -> runCatching { observer(event) } }
    }

    internal fun markClosed() {
        closed.set(true)
        synchronized(observerLock) { observers.clear() }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(observerLock) { observers.clear() }
        broker.removeView(viewId)
    }
}

/** One uploaded rumble: stop and erase are idempotent (D4). */
internal class EvdevPortEffect(
    private val native: EvdevNativeEffect,
) : GamepadPortEffect {
    private val closed = AtomicBoolean(false)

    /** Broker hook clearing the exclusive slot; set under the broker lock. */
    internal var onReleased: (() -> Unit)? = null

    override fun requestStop(): KadreResult<Unit> =
        if (closed.get()) {
            KadreResult.Success(Unit)
        } else if (native.stop()) {
            KadreResult.Success(Unit)
        } else {
            KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.Linux, "gamepad-effect", "stop-refused"),
            )
        }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching(native::release)
        runCatching { onReleased?.invoke() }
    }
}
