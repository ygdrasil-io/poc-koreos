package org.graphiks.kadre.samples.simulation.scenarios.window

import org.graphiks.kadre.core.*
import org.graphiks.kadre.samples.simulation.*

class ResizeScenario : WindowScenario(
    id = "window-resize",
    title = "Redimensionnement",
    description = "Teste les événements de redimensionnement de fenêtre. Redimensionnez la fenêtre et observez les changements de taille.",
    priority = 100
) {
    override fun start(window: Window, eventLoop: ActiveEventLoop, onEvent: (ScenarioEvent) -> Unit) {
        super.start(window, eventLoop, onEvent)
        val size = window.attributes
        onEvent(ScenarioEvent.Message(
            "Redimensionnez la fenêtre. Taille initiale: ${size.width}x${size.height}",
            MessageSeverity.INFO
        ))
    }

    override fun onKeyEvent(event: WindowEvent.KeyInput) {
        super.onKeyEvent(event)
        // Press R to reset to default size
        if (event.pressed && event.key == Key.R) {
            window?.update(WindowAttributes(width = 1200, height = 800))
            onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
                isRunning = true,
                message = "🔄 Taille réinitialisée à 1200x800",
                data = mapOf("width" to 1200, "height" to 800)
            )))
        }
    }
}
