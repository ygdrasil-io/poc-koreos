package org.graphiks.kadre.internal.appkit.manual

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.graphiks.kadre.input.Gamepad
import org.graphiks.kadre.input.GamepadEvent
import org.graphiks.kadre.input.GamepadId
import org.graphiks.kadre.input.GamepadSnapshot

/** Owns the two public flows observed for each gamepad by the Phase 10 manual harness. */
internal class Phase10GamepadObservationSet(
    private val scope: CoroutineScope,
    private val onSnapshot: (Gamepad, GamepadSnapshot) -> Unit,
    private val onEvent: (Gamepad, GamepadEvent) -> Unit,
) : AutoCloseable {
    private val lock = Any()
    private val observations = linkedMapOf<GamepadId, Observation>()

    fun observe(gamepad: Gamepad) {
        val owner = SupervisorJob(scope.coroutineContext[Job])
        val installed = synchronized(lock) {
            if (observations.containsKey(gamepad.id)) {
                false
            } else {
                observations[gamepad.id] = Observation(gamepad, owner)
                true
            }
        }
        if (!installed) {
            owner.cancel()
            return
        }
        scope.launch(owner, start = CoroutineStart.UNDISPATCHED) {
            gamepad.state.collect { snapshot -> onSnapshot(gamepad, snapshot) }
        }
        scope.launch(owner, start = CoroutineStart.UNDISPATCHED) {
            gamepad.events.collect { event -> onEvent(gamepad, event) }
        }
    }

    /** Records the terminal public snapshot before detaching observers for this identity. */
    fun remove(id: GamepadId) {
        val observation = synchronized(lock) { observations.remove(id) } ?: return
        onSnapshot(observation.gamepad, observation.gamepad.state.value)
        observation.owner.cancel()
    }

    override fun close() {
        val owners = synchronized(lock) {
            observations.values.map(Observation::owner).also { observations.clear() }
        }
        owners.forEach(Job::cancel)
    }

    private data class Observation(
        val gamepad: Gamepad,
        val owner: Job,
    )
}
