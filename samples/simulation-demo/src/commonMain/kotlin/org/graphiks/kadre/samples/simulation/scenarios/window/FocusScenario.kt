package org.graphiks.kadre.samples.simulation.scenarios.window

import org.graphiks.kadre.core.*
import org.graphiks.kadre.samples.simulation.*

class FocusScenario : WindowScenario(
    id = "window-focus",
    title = "Focus et événements de fenêtre",
    description = "Teste les événements de focus, déplacement, minimisation et restauration de la fenêtre.",
    priority = 70
) {
    private var focusCount = 0

    override fun start(window: Window, eventLoop: ActiveEventLoop, onEvent: (ScenarioEvent) -> Unit) {
        super.start(window, eventLoop, onEvent)
        onEvent(ScenarioEvent.Message("Déplacez la fenêtre, changez de focus, minimisez-la", MessageSeverity.INFO))
    }

    override fun onKeyEvent(event: WindowEvent.KeyInput) {
        super.onKeyEvent(event)
        if (event.pressed) {
            when (event.key) {
                Key.M -> {
                    window?.update(WindowAttributes(minimized = true))
                    onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
                        isRunning = true,
                        message = "🗕️ Fenêtre minimisée"
                    )))
                }
                Key.P -> {
                    window?.focus()
                    onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
                        isRunning = true,
                        message = "🔲 Focus réclamé"
                    )))
                }
                else -> {}
            }
        }
    }
}
