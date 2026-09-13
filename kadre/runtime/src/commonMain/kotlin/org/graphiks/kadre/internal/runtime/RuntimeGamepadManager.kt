package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DeviceConnectionState
import org.graphiks.kadre.input.DeviceId
import org.graphiks.kadre.input.DeviceInventory
import org.graphiks.kadre.input.DeviceLifecycleEvent
import org.graphiks.kadre.input.DeviceManager
import org.graphiks.kadre.input.DeviceManagerRevision
import org.graphiks.kadre.input.DeviceManagerState
import org.graphiks.kadre.input.Gamepad
import org.graphiks.kadre.input.GamepadAxisValue
import org.graphiks.kadre.input.GamepadButtonValue
import org.graphiks.kadre.input.GamepadDescriptor
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.input.GamepadEffectSession
import org.graphiks.kadre.input.GamepadEvent
import org.graphiks.kadre.input.GamepadId
import org.graphiks.kadre.input.GamepadRevision
import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.input.GamepadSnapshot
import org.graphiks.kadre.input.GamepadState

/** Session-owned public gamepad projection backed by one [GamepadPort]. */
internal class RuntimeGamepadManager(
    private val port: GamepadPort,
    private val eventStampSource: () -> EventStamp,
    private val collectorAllocator: RuntimeEventCollectorAllocator,
    private val maxCollectorsPerFlow: Int,
) : DeviceManager, AutoCloseable {
    private val lock = RuntimeLock()
    private val gamepadsByKey = linkedMapOf<Long, RuntimeGamepad>()
    private var closed = false
    private var observation: AutoCloseable? = null
    private val eventGate = collectorAllocator.newGate(maxCollectorsPerFlow)
    private val mutableState = MutableStateFlow(
        DeviceManagerState(
            inventory = DeviceInventory.Enumerated(emptyList(), emptyList()),
            revision = DeviceManagerRevision(0),
        ),
    )
    private val mutableEvents = MutableSharedFlow<DeviceLifecycleEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)

    override val state: StateFlow<DeviceManagerState> = mutableState.asStateFlow()
    override val events: Flow<DeviceLifecycleEvent> = mutableEvents.asSharedFlow().withEventCollectorAdmission(eventGate)

    init {
        observation = port.installObserver(::accept)
        lock.withLock {
            if (!closed) {
                port.gamepads.forEach(::connectInitialLocked)
                publishInventoryLocked(incrementRevision = false)
            }
        }
    }

    override fun device(id: DeviceId) = null

    override fun gamepad(id: GamepadId): Gamepad? = lock.withLock {
        gamepadsByKey.values.firstOrNull { it.id == id }
    }

    override fun close() {
        val toClose = lock.withLock {
            if (closed) return
            closed = true
            observation.also { observation = null }
        }
        toClose?.close()
        lock.withLock {
            gamepadsByKey.values.forEach(RuntimeGamepad::disconnect)
            gamepadsByKey.clear()
        }
    }

    private fun accept(event: GamepadPortEvent) {
        val publications = lock.withLock {
            if (closed) return
            when (event) {
                is GamepadPortEvent.Connected -> connectLocked(event.gamepad)
                is GamepadPortEvent.Disconnected -> disconnectLocked(event.key)
                is GamepadPortEvent.StateChanged -> gamepadsByKey[event.key]
                    ?.stateChanged(event.state, eventStampSource)
                    .orEmpty()

                is GamepadPortEvent.RoutingChanged -> gamepadsByKey[event.key]
                    ?.routingChanged(event.routing, event.state, eventStampSource)
                    .orEmpty()
            }
        }
        publications.forEach { publication ->
            when (publication) {
                is DevicePublication -> mutableEvents.tryEmit(publication.event)
                is GamepadPublication -> publication.gamepad.publish(publication.event)
            }
        }
    }

    private fun connectInitialLocked(source: GamepadPortGamepad) {
        if (gamepadsByKey.containsKey(source.key)) return
        gamepadsByKey[source.key] = newGamepad(source)
    }

    private fun connectLocked(source: GamepadPortGamepad): List<Publication> {
        if (gamepadsByKey.containsKey(source.key)) return emptyList()
        val gamepad = newGamepad(source)
        gamepadsByKey[source.key] = gamepad
        val next = publishInventoryLocked(incrementRevision = true)
        return listOf(
            DevicePublication(
                DeviceLifecycleEvent.GamepadAdded(gamepad, next.revision, eventStampSource()),
            ),
        )
    }

    private fun disconnectLocked(key: Long): List<Publication> {
        val gamepad = gamepadsByKey.remove(key) ?: return emptyList()
        gamepad.disconnect()
        val next = publishInventoryLocked(incrementRevision = true)
        return listOf(
            DevicePublication(
                DeviceLifecycleEvent.GamepadRemoved(gamepad.id, next.revision, eventStampSource()),
            ),
        )
    }

    private fun newGamepad(source: GamepadPortGamepad): RuntimeGamepad {
        validate(source)
        return RuntimeGamepad(
            id = RuntimeProcessIds.nextGamepadId(),
            source = source,
            eventGate = collectorAllocator.newGate(maxCollectorsPerFlow),
        )
    }

    private fun publishInventoryLocked(incrementRevision: Boolean): DeviceManagerState {
        val previous = mutableState.value
        val revision = if (incrementRevision) nextRevision(previous.revision) else previous.revision
        return DeviceManagerState(
            inventory = DeviceInventory.Enumerated(
                devices = emptyList(),
                gamepads = gamepadsByKey.values.toList(),
            ),
            revision = revision,
        ).also { mutableState.value = it }
    }

    private fun validate(source: GamepadPortGamepad) {
        val descriptor = source.descriptor
        val controls = source.state
        require(controls.buttons.map(GamepadButtonValue::button) == descriptor.buttons) {
            "gamepad button state must exactly match its descriptor"
        }
        require(controls.axes.map(GamepadAxisValue::axis) == descriptor.axes) {
            "gamepad axis state must exactly match its descriptor"
        }
    }

    private fun nextRevision(current: DeviceManagerRevision): DeviceManagerRevision {
        check(current.value < Long.MAX_VALUE) { "device manager revision space exhausted" }
        return DeviceManagerRevision(current.value + 1L)
    }

    private sealed interface Publication
    private data class DevicePublication(val event: DeviceLifecycleEvent) : Publication
    private data class GamepadPublication(
        val gamepad: RuntimeGamepad,
        val event: GamepadEvent,
    ) : Publication

    private inner class RuntimeGamepad(
        override val id: GamepadId,
        source: GamepadPortGamepad,
        eventGate: RuntimeEventCollectorGate,
    ) : Gamepad {
        private val mutableState = MutableStateFlow(
            GamepadSnapshot(
                descriptor = source.descriptor,
                connection = DeviceConnectionState.Connected,
                routing = source.routing,
                controls = if (source.routing == GamepadRoutingState.Suspended) {
                    neutralControls(source.descriptor)
                } else {
                    source.state
                },
                capabilities = source.capabilities,
                revision = GamepadRevision(0),
            ),
        )
        private val mutableEvents = MutableSharedFlow<GamepadEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)

        override val state: StateFlow<GamepadSnapshot> = mutableState.asStateFlow()
        override val events: Flow<GamepadEvent> = mutableEvents.asSharedFlow().withEventCollectorAdmission(eventGate)

        override suspend fun playEffect(effect: GamepadEffect): KadreResult<GamepadEffectSession> =
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.GamepadEffect))

        override suspend fun stopEffects(): KadreResult<Unit> = KadreResult.Success(Unit)

        fun stateChanged(nextControls: GamepadState, stampSource: () -> EventStamp): List<Publication> {
            val previous = mutableState.value
            if (previous.connection != DeviceConnectionState.Connected || previous.routing != GamepadRoutingState.Routed) {
                return emptyList()
            }
            validateControls(nextControls, previous)
            if (nextControls == previous.controls) return emptyList()
            val next = previous.copy(controls = nextControls, revision = nextRevision(previous.revision))
            mutableState.value = next
            val stamp = stampSource()
            return buildList {
                nextControls.buttons.zip(previous.controls.buttons).forEach { (nextValue, previousValue) ->
                    if (nextValue != previousValue) {
                        add(GamepadPublication(this@RuntimeGamepad, GamepadEvent.ButtonChanged(nextValue, next.revision, stamp)))
                    }
                }
                nextControls.axes.zip(previous.controls.axes).forEach { (nextValue, previousValue) ->
                    if (nextValue != previousValue) {
                        add(GamepadPublication(this@RuntimeGamepad, GamepadEvent.AxisChanged(nextValue, next.revision, stamp)))
                    }
                }
            }
        }

        fun routingChanged(
            routing: GamepadRoutingState,
            controls: GamepadState,
            stampSource: () -> EventStamp,
        ): List<Publication> {
            val previous = mutableState.value
            if (previous.connection != DeviceConnectionState.Connected || routing == previous.routing) return emptyList()
            val nextControls = if (routing == GamepadRoutingState.Suspended) neutralControls(previous) else controls
            validateControls(nextControls, previous)
            val next = previous.copy(routing = routing, controls = nextControls, revision = nextRevision(previous.revision))
            mutableState.value = next
            val event = when (routing) {
                GamepadRoutingState.Routed -> GamepadEvent.RoutingResumed(next.revision, stampSource())
                GamepadRoutingState.Suspended -> GamepadEvent.RoutingSuspended(next.revision, stampSource())
            }
            return listOf(GamepadPublication(this, event))
        }

        fun disconnect() {
            val previous = mutableState.value
            if (previous.connection == DeviceConnectionState.Disconnected) return
            mutableState.value = previous.copy(
                connection = DeviceConnectionState.Disconnected,
                routing = GamepadRoutingState.Suspended,
                controls = neutralControls(previous),
                revision = nextRevision(previous.revision),
            )
        }

        fun publish(event: GamepadEvent) {
            mutableEvents.tryEmit(event)
        }

        private fun validateControls(controls: GamepadState, current: GamepadSnapshot) {
            require(controls.buttons.map(GamepadButtonValue::button) == current.descriptor.buttons) {
                "gamepad button state must exactly match its descriptor"
            }
            require(controls.axes.map(GamepadAxisValue::axis) == current.descriptor.axes) {
                "gamepad axis state must exactly match its descriptor"
            }
        }

        private fun neutralControls(current: GamepadSnapshot): GamepadState = neutralControls(current.descriptor)

        private fun neutralControls(descriptor: GamepadDescriptor): GamepadState = GamepadState(
            buttons = descriptor.buttons.map { button -> GamepadButtonValue(button, 0.0, false) },
            axes = descriptor.axes.map { axis -> GamepadAxisValue(axis, 0.0) },
        )

        private fun nextRevision(current: GamepadRevision): GamepadRevision {
            check(current.value < Long.MAX_VALUE) { "gamepad revision space exhausted" }
            return GamepadRevision(current.value + 1L)
        }
    }

    private companion object {
        const val EVENT_BUFFER_CAPACITY = 32
    }
}
