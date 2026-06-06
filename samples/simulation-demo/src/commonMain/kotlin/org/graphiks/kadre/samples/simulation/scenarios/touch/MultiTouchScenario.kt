package org.graphiks.kadre.samples.simulation.scenarios.touch

import org.graphiks.kadre.core.*
import org.graphiks.kadre.samples.simulation.*

class MultiTouchScenario : TouchScenario(
    id = "touch-multi",
    title = "Multi-touch",
    description = "Teste les événements multi-touch avec plusieurs doigts simultanés.",
    priority = 90
) {
    override val requiredCapabilities: Set<Capability> = setOf(Capability.MULTI_TOUCH)
    private val activeFingers = mutableMapOf<Int, FingerState>()

    data class FingerState(
        val x: Float,
        val y: Float
    )

    override fun start(window: Window, eventLoop: ActiveEventLoop, onEvent: (ScenarioEvent) -> Unit) {
        super.start(window, eventLoop, onEvent)
        activeFingers.clear()
        onEvent(ScenarioEvent.Message("Utilisez plusieurs doigts pour tester le multi-touch", MessageSeverity.INFO))
    }

    override fun onTouchEvent(event: WindowEvent.Touch) {
        eventsReceived++

        when (event.phase) {
            TouchPhase.STARTED -> {
                activeFingers[event.fingerId] = FingerState(event.x, event.y)
            }
            TouchPhase.MOVED -> {
                activeFingers[event.fingerId] = FingerState(event.x, event.y)
            }
            TouchPhase.ENDED -> {
                activeFingers.remove(event.fingerId)
            }
            TouchPhase.CANCELLED -> {
                activeFingers.remove(event.fingerId)
            }
            null -> {}
        }

        onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
            isRunning = true,
            message = "🖐️ Doigts actifs: ${activeFingers.size} | ID: ${event.fingerId} à (${event.x.toInt()}, ${event.y.toInt()})",
            data = mapOf(
                "active_fingers" to activeFingers.size,
                "finger_id" to event.fingerId
            )
        )))
    }
}
