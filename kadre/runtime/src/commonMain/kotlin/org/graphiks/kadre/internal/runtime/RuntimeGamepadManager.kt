package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.ActivationState
import org.graphiks.kadre.application.VisibilityState
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
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
import org.graphiks.kadre.input.GamepadEffectKind
import org.graphiks.kadre.input.GamepadEffectOutcome
import org.graphiks.kadre.input.GamepadEffectSession
import org.graphiks.kadre.input.GamepadEffectState
import org.graphiks.kadre.input.GamepadEffectStopReason
import org.graphiks.kadre.input.GamepadEvent
import org.graphiks.kadre.input.GamepadId
import org.graphiks.kadre.input.GamepadRevision
import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.input.GamepadSnapshot
import org.graphiks.kadre.input.GamepadState
import org.graphiks.kadre.policy.GamepadRouting

/** Session-owned public gamepad projection backed by one [GamepadPort]. */
internal class RuntimeGamepadManager(
    private val port: GamepadPort,
    private val eventStampSource: () -> EventStamp,
    private val collectorAllocator: RuntimeEventCollectorAllocator,
    private val maxCollectorsPerFlow: Int,
    private val effectScope: CoroutineScope,
    private val maxConcurrentEffects: Int,
    private val gamepadRouting: GamepadRouting,
    initialLifecycleState: LifecycleState,
) : DeviceManager, AutoCloseable {
    private val lock = RuntimeLock()
    private val gamepadsByKey = linkedMapOf<Long, RuntimeGamepad>()
    private var closed = false
    private var observation: AutoCloseable? = null
    private var routing: GamepadPortRouting? = null
    private var pendingEffects = 0
    private val activeEffects = linkedSetOf<RuntimeGamepadEffectSession>()
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
        require(maxConcurrentEffects > 0) { "maxConcurrentEffects must be positive" }
        observation = port.installObserver(::accept)
        updateRouting(routingFor(initialLifecycleState))
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

    fun updateLifecycle(state: LifecycleState) {
        updateRouting(routingFor(state))
    }

    override fun close() {
        val release = lock.withLock {
            if (closed) return
            closed = true
            val observation = observation.also { observation = null }
            val effects = activeEffects.toList()
            activeEffects.clear()
            gamepadsByKey.values.forEach(RuntimeGamepad::disconnect)
            gamepadsByKey.clear()
            Release(observation, effects)
        }
        release.observation?.close()
        release.effects.forEach { effect -> effect.terminate(GamepadEffectOutcome.Stopped(GamepadEffectStopReason.ParentSessionStopping)) }
    }

    private fun accept(event: GamepadPortEvent) {
        val accepted = lock.withLock {
            if (closed) return
            when (event) {
                is GamepadPortEvent.Connected -> AcceptedPortEvent(connectLocked(event.gamepad))
                is GamepadPortEvent.Disconnected -> disconnectLocked(event.key)
                is GamepadPortEvent.StateChanged -> AcceptedPortEvent(
                    gamepadsByKey[event.key]?.stateChanged(event.state, eventStampSource).orEmpty(),
                )

                is GamepadPortEvent.RoutingChanged -> AcceptedPortEvent(
                    gamepadsByKey[event.key]
                        ?.routingChanged(event.routing, event.state, eventStampSource)
                        .orEmpty(),
                )
            }
        }
        accepted.effects.forEach { effect ->
            effect.terminate(GamepadEffectOutcome.Stopped(GamepadEffectStopReason.DeviceDisconnected))
        }
        accepted.publications.forEach { publication ->
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

    private fun disconnectLocked(key: Long): AcceptedPortEvent {
        val gamepad = gamepadsByKey.remove(key) ?: return AcceptedPortEvent(emptyList())
        val effects = detachEffectsLocked(gamepad)
        gamepad.disconnect()
        val next = publishInventoryLocked(incrementRevision = true)
        return AcceptedPortEvent(
            publications = listOf(
                DevicePublication(
                    DeviceLifecycleEvent.GamepadRemoved(gamepad.id, next.revision, eventStampSource()),
                ),
            ),
            effects = effects,
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

    private fun routingFor(state: LifecycleState): GamepadPortRouting = GamepadPortRouting(
        policy = gamepadRouting,
        foregroundActive = state.visibility == VisibilityState.Foreground && state.activation == ActivationState.Active,
    )

    private fun updateRouting(next: GamepadPortRouting) {
        val shouldUpdate = lock.withLock {
            if (closed || routing == next) {
                false
            } else {
                routing = next
                true
            }
        }
        if (shouldUpdate) port.updateRouting(next)
    }

    private fun startEffect(
        gamepad: RuntimeGamepad,
        effect: GamepadEffect,
    ): KadreResult<GamepadEffectSession> {
        val admission = lock.withLock {
            if (closed || !gamepad.isConnected()) {
                return@withLock KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Gamepad))
            }
            gamepad.effectAdmissionFailure(effect)?.let { failure ->
                return@withLock KadreResult.Failure(failure)
            }
            if (activeEffects.size + pendingEffects >= maxConcurrentEffects) {
                return@withLock KadreResult.Failure(
                    KadreFailure.ResourceLimitExceeded(
                        KadreResourceKind.GamepadEffect,
                        maxConcurrentEffects.toLong(),
                    ),
                )
            }
            pendingEffects += 1
            KadreResult.Success(Unit)
        }
        if (admission is KadreResult.Failure) return admission

        val started = port.startEffect(gamepad.key, effect)
        val owner = when (started) {
            is KadreResult.Failure -> {
                lock.withLock { pendingEffects -= 1 }
                return started
            }

            is KadreResult.Success -> started.value
        }
        val session = lock.withLock {
            pendingEffects -= 1
            if (closed || !gamepad.isConnected()) {
                null
            } else {
                RuntimeGamepadEffectSession(
                    owner = owner,
                    effect = effect,
                    scope = effectScope,
                    gamepadKey = gamepad.key,
                    onTerminated = ::effectTerminated,
                ).also(activeEffects::add)
            }
        }
        if (session == null) {
            runCatching(owner::close)
            return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Gamepad))
        }
        session.start()
        return KadreResult.Success(session)
    }

    private fun stopEffects(gamepad: RuntimeGamepad): KadreResult<Unit> {
        val sessions = lock.withLock {
            if (closed || !gamepad.isConnected()) {
                return@withLock null
            }
            activeEffects.filter { it.gamepadKey == gamepad.key }
        } ?: return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Gamepad))
        sessions.forEach(RuntimeGamepadEffectSession::requestStop)
        return KadreResult.Success(Unit)
    }

    private fun detachEffectsLocked(gamepad: RuntimeGamepad): List<RuntimeGamepadEffectSession> =
        activeEffects.filter { effect -> effect.gamepadKey == gamepad.key }
            .also(activeEffects::removeAll)

    private fun effectTerminated(session: RuntimeGamepadEffectSession) {
        lock.withLock { activeEffects.remove(session) }
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
    private data class AcceptedPortEvent(
        val publications: List<Publication>,
        val effects: List<RuntimeGamepadEffectSession> = emptyList(),
    )
    private data class Release(
        val observation: AutoCloseable?,
        val effects: List<RuntimeGamepadEffectSession>,
    )

    private class RuntimeGamepadEffectSession(
        private val owner: GamepadPortEffect,
        private val effect: GamepadEffect,
        private val scope: CoroutineScope,
        val gamepadKey: Long,
        private val onTerminated: (RuntimeGamepadEffectSession) -> Unit,
    ) : GamepadEffectSession {
        private val lock = RuntimeLock()
        private val terminal = CompletableDeferred<GamepadEffectOutcome>()
        private val mutableState = MutableStateFlow<GamepadEffectState>(GamepadEffectState.Playing)
        private var completion: Job? = null
        private var finished = false

        override val state: StateFlow<GamepadEffectState> = mutableState.asStateFlow()

        fun start() {
            val shouldStart = lock.withLock {
                if (finished || completion != null) false else true
            }
            if (!shouldStart) return
            val job = scope.launch {
                delay(effect.duration)
                terminate(GamepadEffectOutcome.Completed)
            }
            lock.withLock {
                if (finished) job.cancel() else completion = job
            }
        }

        override fun requestStop() {
            terminate(GamepadEffectOutcome.Stopped(GamepadEffectStopReason.Requested))
        }

        override fun close() = requestStop()

        override suspend fun awaitTermination(): GamepadEffectOutcome = terminal.await()

        fun terminate(outcome: GamepadEffectOutcome) {
            val shouldTerminate = lock.withLock {
                if (finished) {
                    false
                } else {
                    finished = true
                    completion?.cancel()
                    if (outcome != GamepadEffectOutcome.Completed) {
                        mutableState.value = GamepadEffectState.Stopping
                    }
                    true
                }
            }
            if (!shouldTerminate) return
            val terminalOutcome = if (outcome == GamepadEffectOutcome.Completed) {
                outcome
            } else {
                when (val stopped = owner.requestStop()) {
                    is KadreResult.Success -> outcome
                    is KadreResult.Failure -> GamepadEffectOutcome.Failed(stopped.reason)
                }
            }
            lock.withLock {
                mutableState.value = GamepadEffectState.Terminated(terminalOutcome)
                check(terminal.complete(terminalOutcome)) { "gamepad effect terminal outcome was already completed" }
            }
            runCatching(owner::close)
            onTerminated(this)
        }
    }

    private inner class RuntimeGamepad(
        override val id: GamepadId,
        source: GamepadPortGamepad,
        eventGate: RuntimeEventCollectorGate,
    ) : Gamepad {
        val key: Long = source.key
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
            this@RuntimeGamepadManager.startEffect(this, effect)

        override suspend fun stopEffects(): KadreResult<Unit> = this@RuntimeGamepadManager.stopEffects(this)

        fun isConnected(): Boolean = mutableState.value.connection == DeviceConnectionState.Connected

        fun effectAdmissionFailure(effect: GamepadEffect): KadreFailure? {
            val capability = mutableState.value.capabilities.effects
            val constraints = when (capability) {
                is Capability.Unsupported -> return capability.failure
                is Capability.Supported -> when (val availability = capability.availability) {
                    FeatureAvailability.Available -> capability.constraints
                    is FeatureAvailability.Unavailable -> return availability.failure
                    else -> return KadreFailure.TemporarilyUnavailable(retryable = true)
                }
            }
            val kind = when (effect) {
                is GamepadEffect.DualRumble -> GamepadEffectKind.DualRumble
                is GamepadEffect.TriggerRumble -> GamepadEffectKind.TriggerRumble
                is GamepadEffect.LocalizedHaptic -> GamepadEffectKind.LocalizedHaptic
            }
            if (kind !in constraints.kinds) return KadreFailure.InvalidRequest("effect")
            if (constraints.maximumDuration?.let { maximum -> effect.duration > maximum } == true) {
                return KadreFailure.InvalidRequest("effect.duration")
            }
            if (effect is GamepadEffect.LocalizedHaptic && effect.locality !in checkNotNull(constraints.localizedHaptics).localities) {
                return KadreFailure.InvalidRequest("effect")
            }
            return null
        }

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
