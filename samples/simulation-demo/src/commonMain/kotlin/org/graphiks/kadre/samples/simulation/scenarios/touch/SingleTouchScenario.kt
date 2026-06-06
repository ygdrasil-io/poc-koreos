package org.graphiks.kadre.samples.simulation.scenarios.touch

import org.graphiks.kadre.core.*
import org.graphiks.kadre.samples.simulation.*

class SingleTouchScenario : TouchScenario(
    id = "touch-single",
    title = "Touch simple",
    description = "Teste les événements tactiles avec un seul doigt. Appuyez, déplacez, relâchez.",
    priority = 100
) {
    override fun start(window: Window, eventLoop: ActiveEventLoop, onEvent: (ScenarioEvent) -> Unit) {
        super.start(window, eventLoop, onEvent)
        onEvent(ScenarioEvent.Message("Touchez l'écran pour voir les événements", MessageSeverity.INFO))
    }

    override fun onTouchEvent(event: WindowEvent.Touch) {
        eventsReceived++

        val action = event.phase?.name?.lowercase() ?: "unknown"
        val pos = "${event.x.toInt()},${event.y.toInt()}"

        onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
            isRunning = true,
            message = "👆 Touch: $action à ($pos)",
            data = mapOf(
                "phase" to action,
                "x" to event.x.toInt(),
                "y" to event.y.toInt()
            )
        )))
    }
}
